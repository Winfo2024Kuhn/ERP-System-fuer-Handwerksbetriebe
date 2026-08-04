-- Technische Stammdaten am Artikel: Masse als echte Zahlen, Herstellverfahren,
-- Fertigungszustand, interne Artikelnummer und Suchtext.
--
-- Hintergrund: Bisher standen alle Masse nur als Text im Produktnamen ("42.4x2").
-- Die Spalten hoehe/breite waren zwar vorhanden, aber als INT angelegt und bei
-- allen 3145 Datensaetzen 0 - eine 42,4 passt in ein INT gar nicht hinein.
-- Dadurch konnte die Suche nicht nach Durchmesser oder Wandstaerke filtern und
-- die Kalkulation muesste Masse aus dem Namen raten.
--
-- Zusaetzlich wird das Herstellverfahren (nahtlos/geschweisst/stranggepresst)
-- und der Fertigungszustand (warmgewalzt/kaltgezogen/...) erfasst. Daraus
-- ergibt sich die zutreffende Norm - bisher wurde die frei eingetippt, was zu
-- zahlreichen Fehlzuordnungen gefuehrt hat (siehe V339).
--
-- Genauigkeit: masse_pro_meter und mantelflaeche hatten nur 2 Nachkommastellen.
-- Ein Alu-Flachstab 10x3 wiegt 0,0764 kg/m und stand als "0.08" in der DB - bei
-- groesseren Mengen ein spuerbarer Kalkulationsfehler. Jetzt 4 Nachkommastellen.
--
-- Idempotent: Mehrfach-Ausfuehrung unschaedlich.

-- --------------------------------------------------------------------------
-- Hilfsprozedur: Spalte nur anlegen, wenn sie noch nicht existiert.
-- --------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS add_column_if_missing;
DELIMITER $$
CREATE PROCEDURE add_column_if_missing(
    IN p_table VARCHAR(64),
    IN p_column VARCHAR(64),
    IN p_definition TEXT)
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = DATABASE()
                     AND table_name = p_table
                     AND column_name = p_column) THEN
        SET @ddl := CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN `', p_column, '` ', p_definition);
        PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

-- --------------------------------------------------------------------------
-- 1. Artikel: interne Artikelnummer und Suchtext
-- --------------------------------------------------------------------------

-- Interne, betriebseigene Artikelnummer. Eindeutig, sprechend aufgebaut
-- (z.B. "ST-RR-042.4-2.0" = Stahl Rundrohr 42,4 x 2,0). Wird in V341 vergeben.
CALL add_column_if_missing('artikel', 'artikelnummer', 'VARCHAR(64) NULL');

-- Normalisierter Suchtext mit allen Schreibweisen und Synonymen, damit die
-- Suche "rundrohr 42.4 x2" genauso findet wie "rohr 42,4x2" oder "Ø42.4".
-- Wird von der Suche wortweise (UND-Verknuepfung) durchsucht.
CALL add_column_if_missing('artikel', 'suchtext', 'TEXT NULL');

-- Herstellverfahren: bestimmt zusammen mit dem Fertigungszustand die Norm.
CALL add_column_if_missing('artikel', 'herstellverfahren',
    "ENUM('NAHTLOS','GESCHWEISST','STRANGGEPRESST','GEWALZT','GEZOGEN','GEKANTET') NULL");

-- Fertigungs-/Lieferzustand.
CALL add_column_if_missing('artikel', 'fertigungszustand',
    "ENUM('WARMGEFERTIGT','KALTGEFERTIGT','WARMGEWALZT','KALTGEWALZT','KALTGEZOGEN','BLANK','GEBEIZT','GESCHLIFFEN') NULL");

-- Geometrische Grundform. Bewusst als Enum und nicht nur ueber die Kategorie,
-- weil die Kategorien vom Anwender umbenannt werden koennen, die Formel zur
-- Gewichtsberechnung aber stabil an der Form haengen muss.
CALL add_column_if_missing('artikel', 'profilform',
    "ENUM('RUNDROHR','QUADRATROHR','RECHTECKROHR','RUNDSTAB','VIERKANTSTAB','SECHSKANTSTAB','FLACHSTAB','BREITFLACHSTAHL','WINKEL_GLEICHSCHENKLIG','WINKEL_UNGLEICHSCHENKLIG','U_PROFIL','UPE_PROFIL','UAP_PROFIL','I_PROFIL','IPE_PROFIL','HEA_PROFIL','HEB_PROFIL','HEM_PROFIL','T_PROFIL','Z_PROFIL','BLECH','RIFFELBLECH','LOCHBLECH','SONSTIGES') NULL");

-- Massnorm (z.B. "EN 10219-2"). Getrennt von der Werkstoffnorm, weil beide
-- unterschiedlich sind: EN 755-9 regelt die Masse, EN AW-6060 den Werkstoff.
CALL add_column_if_missing('artikel', 'massnorm', 'VARCHAR(64) NULL');
CALL add_column_if_missing('artikel', 'werkstoffnorm', 'VARCHAR(64) NULL');

-- Eignungs-Kennzeichen. NULL bedeutet bewusst "erbt vom Werkstoff" (siehe V337),
-- nur ein ausdruecklich gesetzter Wert weicht davon ab.
CALL add_column_if_missing('artikel', 'verzinkungsgeeignet', 'BIT(1) NULL');
CALL add_column_if_missing('artikel', 'pulverbeschichtungsgeeignet', 'BIT(1) NULL');

-- Kennzeichnet die vom System mitgelieferten Stammdaten. Schuetzt sie beim
-- CSV-Import vor dem Ueberschreiben und erlaubt spaeter ein sauberes Update.
CALL add_column_if_missing('artikel', 'system_stammdaten', "BIT(1) NOT NULL DEFAULT b'0'");

-- --------------------------------------------------------------------------
-- 2. artikel_werkstoffe: echte Masszahlen
-- --------------------------------------------------------------------------

CALL add_column_if_missing('artikel_werkstoffe', 'durchmesser', 'DECIMAL(10,2) NULL');
CALL add_column_if_missing('artikel_werkstoffe', 'wandstaerke', 'DECIMAL(10,2) NULL');
CALL add_column_if_missing('artikel_werkstoffe', 'stegdicke', 'DECIMAL(10,2) NULL');
CALL add_column_if_missing('artikel_werkstoffe', 'flanschdicke', 'DECIMAL(10,2) NULL');
CALL add_column_if_missing('artikel_werkstoffe', 'querschnittsflaeche', 'DECIMAL(10,3) NULL');
CALL add_column_if_missing('artikel_werkstoffe', 'standardlaenge_mm', 'INT NULL');

-- Flaechengewicht fuer Bleche. masse_pro_meter passt dort nicht, weil ein Blech
-- nach Quadratmeter und nicht nach laufendem Meter abgerechnet wird.
CALL add_column_if_missing('artikel_werkstoffe', 'masse_pro_qm', 'DECIMAL(10,4) NULL');

DROP PROCEDURE IF EXISTS add_column_if_missing;

-- --------------------------------------------------------------------------
-- 3. Bestehende Spalten auf brauchbare Typen bringen
-- --------------------------------------------------------------------------

-- hoehe/breite von INT auf Dezimalzahl. Die Spalten waren bisher durchgaengig 0,
-- es geht dabei also kein Wert verloren. NULL wird erlaubt, damit "nicht erfasst"
-- von "tatsaechlich 0" unterscheidbar bleibt.
SET @t := (SELECT data_type FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'artikel_werkstoffe' AND column_name = 'hoehe');
SET @sql := IF(@t <> 'decimal',
    'ALTER TABLE artikel_werkstoffe MODIFY COLUMN hoehe DECIMAL(10,2) NULL',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @t := (SELECT data_type FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'artikel_werkstoffe' AND column_name = 'breite');
SET @sql := IF(@t <> 'decimal',
    'ALTER TABLE artikel_werkstoffe MODIFY COLUMN breite DECIMAL(10,2) NULL',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Die bisherigen Nullwerte bedeuten "nicht erfasst", nicht "Mass ist 0".
UPDATE artikel_werkstoffe SET hoehe = NULL WHERE hoehe = 0;
UPDATE artikel_werkstoffe SET breite = NULL WHERE breite = 0;

-- Genauigkeit von 2 auf 4 Nachkommastellen erhoehen.
SET @s := (SELECT numeric_scale FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'artikel_werkstoffe' AND column_name = 'masse_pro_meter');
SET @sql := IF(@s < 4,
    'ALTER TABLE artikel_werkstoffe MODIFY COLUMN masse_pro_meter DECIMAL(12,4) NULL',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @s := (SELECT numeric_scale FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'artikel_werkstoffe' AND column_name = 'mantelflaeche');
SET @sql := IF(@s < 4,
    'ALTER TABLE artikel_werkstoffe MODIFY COLUMN mantelflaeche DECIMAL(12,4) NULL',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- geschliffen war NOT NULL ohne Default - beim Anlegen neuer Artikel unpraktisch.
SET @n := (SELECT is_nullable FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'artikel_werkstoffe' AND column_name = 'geschliffen');
SET @sql := IF(@n = 'NO',
    "ALTER TABLE artikel_werkstoffe MODIFY COLUMN geschliffen BIT(1) NOT NULL DEFAULT b'0'",
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- --------------------------------------------------------------------------
-- 4. Indizes fuer die Suche
-- --------------------------------------------------------------------------

SET @idx := (SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'artikel' AND index_name = 'ux_artikel_artikelnummer');
SET @sql := IF(@idx = 0,
    'CREATE UNIQUE INDEX ux_artikel_artikelnummer ON artikel (artikelnummer)',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx := (SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'artikel' AND index_name = 'ix_artikel_profilform');
SET @sql := IF(@idx = 0,
    'CREATE INDEX ix_artikel_profilform ON artikel (profilform)',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Volltextindex auf den Suchtext: MySQL kann damit "rundrohr 42.4 2" im
-- Boolean-Mode als UND-Suche aufloesen, ohne jede Zeile zu lesen.
SET @idx := (SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'artikel' AND index_name = 'ft_artikel_suchtext');
SET @sql := IF(@idx = 0,
    'CREATE FULLTEXT INDEX ft_artikel_suchtext ON artikel (suchtext)',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
