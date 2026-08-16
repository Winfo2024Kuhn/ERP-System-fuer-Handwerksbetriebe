-- Preisspalte auf vier Nachkommastellen.
--
-- Hintergrund: Gespeichert wird immer der Preis je EINER Verrechnungseinheit -
-- je Stueck, je Kilogramm, je Meter. Lieferanten geben Kleinteile aber je 100
-- Stueck an: Schrauben, Muttern, Unterlegscheiben, Nieten. Aus "1,83 EUR je 100
-- Stueck" wird beim Teilen 0,0183 EUR je Stueck - mit nur zwei Nachkommastellen
-- rundet die Datenbank das auf 0,02 EUR. Das sind 9 % Aufschlag auf jede
-- Kalkulation, in der solche Teile vorkommen. Bei noch guenstigeren Artikeln
-- faellt der Preis ganz auf 0,00 EUR und ist damit gar nicht mehr darstellbar.
--
-- Vier Stellen decken den gesamten realistischen Bereich ab: Der guenstigste
-- Kleinteil-Preis liegt bei rund 0,40 EUR je 100 Stueck, also 0,0040 EUR je
-- Stueck. Mehr Stellen braucht es nicht, weniger reichen nicht.
--
-- Die Java-Seite zieht mit: LieferantenArtikelPreise.preis steht ab jetzt auf
-- @Column(scale = 4), sonst scheitert ddl-auto=validate beim Start.
--
-- Die eigentliche Bestandskorrektur (Preise, die noch auf je-100-Niveau stehen)
-- macht V360. Sie muss NACH dieser Migration laufen, sonst geht beim Teilen
-- genau die Genauigkeit verloren, um die es hier geht.
--
-- Idempotent: Mehrfach-Ausfuehrung unschaedlich - liegt die Skala schon bei 4
-- oder hoeher, passiert nichts.
--
-- Betriebshinweis: Eine geaenderte DECIMAL-Praezision kann MySQL nicht in-place
-- umbauen, es faellt auf ALGORITHM=COPY zurueck. Die Tabelle wird also unter
-- einem Metadata-Lock vollstaendig neu geschrieben und ist waehrenddessen fuer
-- Schreibzugriffe gesperrt; die Anwendung startet erst, wenn Flyway durch ist.
-- Bei lieferanten_artikel_preise (rund 10.000 gueltige Staende plus Historie)
-- sind das Sekunden. Auf einem deutlich groesseren Bestand gehoert dafuer ein
-- Wartungsfenster eingeplant.

-- Die Spalte steht im Index ix_lap_artikel_aktuell (artikel_id, aktuell, preis).
-- MySQL baut den Index beim MODIFY selbst neu auf; ein Drop/Create von Hand
-- waere ueberfluessig und wuerde nur ein Zeitfenster ohne Index oeffnen.

SET @skala := (SELECT numeric_scale FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'lieferanten_artikel_preise'
      AND column_name = 'preis');

-- Die bisherige Nullbarkeit wird uebernommen: MODIFY COLUMN ohne ausdrueckliches
-- NOT NULL wuerde eine bestehende NOT-NULL-Bedingung stillschweigend loesen.
SET @nullbar := (SELECT is_nullable FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'lieferanten_artikel_preise'
      AND column_name = 'preis');

SET @sql := IF(@skala IS NOT NULL AND @skala < 4,
    CONCAT('ALTER TABLE lieferanten_artikel_preise MODIFY COLUMN preis DECIMAL(19,4) ',
           IF(@nullbar = 'NO', 'NOT NULL', 'NULL')),
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
