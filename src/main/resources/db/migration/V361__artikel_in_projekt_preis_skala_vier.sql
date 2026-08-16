-- Preisspalte der Projektposition auf vier Nachkommastellen.
--
-- Hintergrund: V359 hat lieferanten_artikel_preise.preis von DECIMAL(19,2) auf
-- DECIMAL(19,4) gehoben, weil Lieferanten Kleinteile je 100 Stueck angeben und
-- aus "1,83 EUR je 100 Stueck" beim Teilen 0,0183 EUR je Stueck wird - mit nur
-- zwei Nachkommastellen rundet die Datenbank das auf 0,02 EUR (9 % Aufschlag).
-- Genau dieser Verlust passiert eine Ebene tiefer erneut: ProjektManagement-
-- Service.fuegeArtikelMaterialkosten schreibt den Preis je Einheit direkt aus
-- determineUnitPrice(...) in artikel_in_projekt.preis_pro_stueck. Solange diese
-- Spalte nur zwei Nachkommastellen fasst, rundet sie den bereits korrigierten
-- Lieferantenpreis beim Uebernehmen in die Projektposition wieder auf 0,02 EUR -
-- bei noch guenstigeren Artikeln bis auf 0,00 EUR, und das Material verschwindet
-- aus der Kalkulation.
--
-- Vier Stellen decken denselben Bereich ab wie V359 und aus demselben Grund:
-- mehr braucht es nicht, weniger reichen nicht.
--
-- Die Java-Seite zieht mit: ArtikelInProjekt.preisProStueck steht ab jetzt auf
-- @Column(scale = 4), sonst scheitert ddl-auto=validate beim Start.
--
-- Bestehende Projektpositionen behalten ihren bereits gerundeten Wert - sie
-- sind fachlich eingefroren (siehe Task-Brief). Diese Migration korrigiert
-- keine Bestandsdaten, nur die Spaltenbreite fuer neue Werte.
--
-- Idempotent: Mehrfach-Ausfuehrung unschaedlich - liegt die Skala schon bei 4
-- oder hoeher, passiert nichts.
--
-- Betriebshinweis: Eine geaenderte DECIMAL-Praezision kann MySQL nicht in-place
-- umbauen, es faellt auf ALGORITHM=COPY zurueck. artikel_in_projekt wird also
-- unter einem Metadata-Lock vollstaendig neu geschrieben und ist waehrenddessen
-- fuer Schreibzugriffe gesperrt; die Anwendung startet erst, wenn Flyway durch
-- ist. Die Tabelle waechst mit jeder Projektposition - je laenger sie im
-- Betrieb ist, desto eher gehoert dafuer ein Wartungsfenster eingeplant.

SET @skala := (SELECT numeric_scale FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'artikel_in_projekt'
      AND column_name = 'preis_pro_stueck');

-- Die bisherige Nullbarkeit wird uebernommen: MODIFY COLUMN ohne ausdrueckliches
-- NOT NULL wuerde eine bestehende NOT-NULL-Bedingung stillschweigend loesen.
SET @nullbar := (SELECT is_nullable FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'artikel_in_projekt'
      AND column_name = 'preis_pro_stueck');

SET @sql := IF(@skala IS NOT NULL AND @skala < 4,
    CONCAT('ALTER TABLE artikel_in_projekt MODIFY COLUMN preis_pro_stueck DECIMAL(19,4) ',
           IF(@nullbar = 'NO', 'NOT NULL', 'NULL')),
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
