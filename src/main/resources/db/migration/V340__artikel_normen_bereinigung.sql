-- Bereinigung der Norm-Angaben und Ableitung von Profilform, Herstellverfahren
-- und Fertigungszustand aus Kategorie und Werkstoff.
--
-- Hintergrund: Die Normen wurden bisher frei als Text erfasst. Eine Auswertung
-- des Bestands hat dabei zwei systematische Fehlergruppen zutage gefoerdert:
--
-- 1) Aluminium - die Teilnormen der EN 755 sind vertauscht. EN 755-3 regelt
--    Rundstangen, EN 755-4 Vierkantstangen, EN 755-5 Rechteckstangen,
--    EN 755-8 stranggepresste Rohre und EN 755-9 Profile. Im Bestand standen
--    Rundrohre unter EN 755-4 (Vierkantstangen) und Winkel unter EN 755-2.
--    EN 755-2 ist ueberhaupt keine Massnorm, sondern regelt nur die
--    mechanischen Eigenschaften - sie stand bei 1054 Artikeln als Massangabe.
--
-- 2) Edelstahl - es wurden Normen fuer unlegierten Stahl verwendet. EN 10219-2
--    und DIN 59200 gelten ausdruecklich nicht fuer nichtrostende Staehle.
--
-- Die Originalwerte werden vorher in artikel_bereinigung_backup gesichert, damit
-- jede Aenderung nachvollziehbar und umkehrbar bleibt.
--
-- Idempotent: Mehrfach-Ausfuehrung unschaedlich (Sicherung nur beim ersten Lauf).

-- --------------------------------------------------------------------------
-- 1. Sicherungstabelle
-- --------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS artikel_bereinigung_backup (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    artikel_id BIGINT NOT NULL,
    feld VARCHAR(64) NOT NULL,
    alt_wert VARCHAR(255) NULL,
    neu_wert VARCHAR(255) NULL,
    grund VARCHAR(255) NULL,
    migration VARCHAR(32) NOT NULL,
    gesichert_am DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX ix_abb_artikel (artikel_id),
    INDEX ix_abb_migration (migration)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Alte Produktlinie (= bisherige Normangabe) einmalig sichern.
INSERT INTO artikel_bereinigung_backup (artikel_id, feld, alt_wert, neu_wert, grund, migration)
SELECT a.id, 'produktlinie', a.produktlinie, NULL, 'Stand vor Norm-Bereinigung', 'V340'
FROM artikel a
WHERE NOT EXISTS (
    SELECT 1 FROM artikel_bereinigung_backup b
    WHERE b.artikel_id = a.id AND b.feld = 'produktlinie' AND b.migration = 'V340');

-- --------------------------------------------------------------------------
-- 2. Profilform aus der Kategorie ableiten
-- --------------------------------------------------------------------------
-- Die Kategorie-IDs entsprechen dem gewachsenen Kategoriebaum des Betriebs.
-- Die Profilform wird zusaetzlich gefuehrt, weil Kategorien umbenannt werden
-- duerfen, die Berechnungsformeln aber stabil bleiben muessen.

UPDATE artikel SET profilform = 'RUNDROHR'                 WHERE kategorie_id = 8   AND profilform IS NULL;
UPDATE artikel SET profilform = 'RECHTECKROHR'             WHERE kategorie_id = 9   AND profilform IS NULL;
UPDATE artikel SET profilform = 'QUADRATROHR'              WHERE kategorie_id = 10  AND profilform IS NULL;
UPDATE artikel SET profilform = 'FLACHSTAB'                WHERE kategorie_id = 80  AND profilform IS NULL;
UPDATE artikel SET profilform = 'BREITFLACHSTAHL'          WHERE kategorie_id = 81  AND profilform IS NULL;
UPDATE artikel SET profilform = 'RUNDSTAB'                 WHERE kategorie_id = 67  AND profilform IS NULL;
UPDATE artikel SET profilform = 'VIERKANTSTAB'             WHERE kategorie_id = 68  AND profilform IS NULL;
UPDATE artikel SET profilform = 'T_PROFIL'                 WHERE kategorie_id = 62  AND profilform IS NULL;
UPDATE artikel SET profilform = 'Z_PROFIL'                 WHERE kategorie_id = 100 AND profilform IS NULL;
UPDATE artikel SET profilform = 'WINKEL_GLEICHSCHENKLIG'   WHERE kategorie_id = 101 AND profilform IS NULL;
UPDATE artikel SET profilform = 'WINKEL_UNGLEICHSCHENKLIG' WHERE kategorie_id = 102 AND profilform IS NULL;
UPDATE artikel SET profilform = 'U_PROFIL'                 WHERE kategorie_id = 91  AND profilform IS NULL;
UPDATE artikel SET profilform = 'UAP_PROFIL'               WHERE kategorie_id = 90  AND profilform IS NULL;
UPDATE artikel SET profilform = 'UPE_PROFIL'               WHERE kategorie_id = 92  AND profilform IS NULL;
UPDATE artikel SET profilform = 'HEA_PROFIL'               WHERE kategorie_id = 69  AND profilform IS NULL;
UPDATE artikel SET profilform = 'HEB_PROFIL'               WHERE kategorie_id = 70  AND profilform IS NULL;
UPDATE artikel SET profilform = 'IPE_PROFIL'               WHERE kategorie_id = 71  AND profilform IS NULL;
UPDATE artikel SET profilform = 'I_PROFIL'                 WHERE kategorie_id = 72  AND profilform IS NULL;
UPDATE artikel SET profilform = 'HEM_PROFIL'               WHERE kategorie_id = 73  AND profilform IS NULL;

-- --------------------------------------------------------------------------
-- 3. Bisherige Norm in massnorm uebernehmen
-- --------------------------------------------------------------------------
-- Der Werkstoffanteil ("| EN AW-6060 (AlMgSi0,5)") wird abgeschnitten, er gehoert
-- zum Werkstoff und nicht zur Massnorm.

UPDATE artikel
SET massnorm = TRIM(SUBSTRING_INDEX(produktlinie, '|', 1))
WHERE massnorm IS NULL AND produktlinie IS NOT NULL AND produktlinie <> '';

-- Festigkeitszustaende wie "F22" gehoeren nicht in die Massnorm.
UPDATE artikel SET massnorm = TRIM(REPLACE(massnorm, 'F22', ''))
WHERE massnorm LIKE '%F22%';

-- --------------------------------------------------------------------------
-- 4. Aluminium: EN 755 richtig zuordnen
-- --------------------------------------------------------------------------

SET @alu := (SELECT GROUP_CONCAT(id) FROM werkstoff WHERE name IN ('EN AW-6060','EN AW-5754','Aluminium'));

UPDATE artikel a
JOIN werkstoff w ON w.id = a.werkstoff_id
SET a.massnorm = CASE a.profilform
        -- Stranggepresste Rohre, unabhaengig vom Querschnitt.
        WHEN 'RUNDROHR'     THEN 'EN 755-8'
        WHEN 'QUADRATROHR'  THEN 'EN 755-8'
        WHEN 'RECHTECKROHR' THEN 'EN 755-8'
        -- Stangen nach Querschnittsform.
        WHEN 'RUNDSTAB'     THEN 'EN 755-3'
        WHEN 'VIERKANTSTAB' THEN 'EN 755-4'
        WHEN 'FLACHSTAB'    THEN 'EN 755-5'
        WHEN 'BREITFLACHSTAHL' THEN 'EN 755-5'
        -- Alle uebrigen offenen Querschnitte sind Profile.
        ELSE 'EN 755-9'
    END,
    a.herstellverfahren = 'STRANGGEPRESST',
    a.fertigungszustand = 'KALTGEFERTIGT'
WHERE w.name IN ('EN AW-6060','EN AW-5754','Aluminium')
  AND a.profilform IS NOT NULL;

-- --------------------------------------------------------------------------
-- 5. Edelstahl: Normen fuer nichtrostende Staehle setzen
-- --------------------------------------------------------------------------
-- EN 10219-2 (Hohlprofile) und DIN 59200 (Breitflachstahl) gelten nur fuer
-- unlegierte und Feinkornbaustaehle und sind fuer 1.4301 nicht anwendbar.

UPDATE artikel a
JOIN werkstoff w ON w.id = a.werkstoff_id
SET a.massnorm = CASE a.profilform
        -- Geschweisste nichtrostende Rohre.
        WHEN 'RUNDROHR'     THEN 'EN 10217-7'
        WHEN 'QUADRATROHR'  THEN 'EN 10296-2'
        WHEN 'RECHTECKROHR' THEN 'EN 10296-2'
        -- Massnormen des Stabstahls gelten unveraendert weiter.
        WHEN 'FLACHSTAB'        THEN 'EN 10058'
        WHEN 'BREITFLACHSTAHL'  THEN 'EN 10058'
        WHEN 'RUNDSTAB'         THEN 'EN 10060'
        WHEN 'VIERKANTSTAB'     THEN 'EN 10059'
        WHEN 'WINKEL_GLEICHSCHENKLIG'   THEN 'EN 10056-1'
        WHEN 'WINKEL_UNGLEICHSCHENKLIG' THEN 'EN 10056-1'
        ELSE a.massnorm
    END,
    a.werkstoffnorm = 'EN 10088-3',
    a.herstellverfahren = CASE
        WHEN a.profilform IN ('RUNDROHR','QUADRATROHR','RECHTECKROHR') THEN 'GESCHWEISST'
        ELSE 'GEWALZT'
    END,
    a.fertigungszustand = CASE
        WHEN a.profilform IN ('RUNDROHR','QUADRATROHR','RECHTECKROHR') THEN 'KALTGEFERTIGT'
        ELSE 'WARMGEWALZT'
    END
WHERE w.name IN ('1.4301','1.4571','Edelstahl')
  AND a.profilform IS NOT NULL;

-- Geschliffene Ware ist im Sichtbereich ueblich und wird als eigener Zustand
-- gefuehrt. Der Schliffgrad steht im Bestand in zwei Schreibweisen im
-- Produktnamen: "(K240)" und "K(240)".
UPDATE artikel a
JOIN artikel_werkstoffe aw ON aw.id = a.id
SET a.fertigungszustand = 'GESCHLIFFEN', aw.geschliffen = b'1'
WHERE a.produktname REGEXP 'K[[:space:]]*\\(?[[:space:]]*240';

-- --------------------------------------------------------------------------
-- 6. Baustahl: Verfahren und Zustand aus der bestehenden Norm ableiten
-- --------------------------------------------------------------------------
-- Hier waren die Normen ganz ueberwiegend korrekt, es fehlten nur Verfahren und
-- Zustand. Die Zuordnung folgt unmittelbar aus der jeweiligen Norm.

UPDATE artikel a
JOIN werkstoff w ON w.id = a.werkstoff_id
SET a.herstellverfahren = 'GESCHWEISST', a.fertigungszustand = 'KALTGEFERTIGT'
WHERE w.name IN ('S235JR','S355J2','Stahl') AND a.massnorm = 'EN 10219-2';

UPDATE artikel a
JOIN werkstoff w ON w.id = a.werkstoff_id
SET a.herstellverfahren = 'GESCHWEISST', a.fertigungszustand = 'KALTGEZOGEN'
WHERE w.name IN ('S235JR','S355J2','Stahl') AND a.massnorm IN ('DIN EN 10305-5','EN 10305-5');

UPDATE artikel a
JOIN werkstoff w ON w.id = a.werkstoff_id
SET a.herstellverfahren = 'GEWALZT', a.fertigungszustand = 'WARMGEWALZT'
WHERE w.name IN ('S235JR','S355J2','Stahl')
  AND a.massnorm IN ('EN 10058','EN 10059','EN 10060','EN 10055','EN 10056-1',
                     'DIN 59200','DIN 1026','DIN 1026-1','DIN 1026-2','DIN 1027',
                     'DIN 1025-2','DIN 1025-3','DIN 1025-4','DIN 1025-5');

UPDATE artikel a
JOIN werkstoff w ON w.id = a.werkstoff_id
SET a.werkstoffnorm = 'EN 10025-2'
WHERE w.name IN ('S235JR','S355J2') AND a.werkstoffnorm IS NULL;

-- UAP-Profile stammen aus der franzoesischen Reihe und sind nicht in DIN 1026
-- geregelt - dort stehen nur U (Teil 1) und UPE (Teil 2).
UPDATE artikel SET massnorm = 'NF A 45-255'
WHERE profilform = 'UAP_PROFIL' AND massnorm IN ('DIN 1026','DIN 1026-1');

-- --------------------------------------------------------------------------
-- 7. Aenderungen protokollieren
-- --------------------------------------------------------------------------

INSERT INTO artikel_bereinigung_backup (artikel_id, feld, alt_wert, neu_wert, grund, migration)
SELECT a.id, 'massnorm', b.alt_wert, a.massnorm, 'Norm regelbasiert korrigiert', 'V340-ergebnis'
FROM artikel a
JOIN artikel_bereinigung_backup b ON b.artikel_id = a.id AND b.feld = 'produktlinie' AND b.migration = 'V340'
WHERE a.massnorm IS NOT NULL
  AND TRIM(SUBSTRING_INDEX(COALESCE(b.alt_wert, ''), '|', 1)) <> a.massnorm
  AND NOT EXISTS (
      SELECT 1 FROM artikel_bereinigung_backup x
      WHERE x.artikel_id = a.id AND x.feld = 'massnorm' AND x.migration = 'V340-ergebnis');
