-- Werkstoffzeugnis als eigener Lieferanten-Dokumenttyp + Suchfelder an Positionen
--
-- 1) Neuer Wert WERKSTOFFZEUGNIS in allen Spalten, die LieferantDokumentTyp
--    speichern. Ist die Spalte ein natives ENUM, wird der vorhandene
--    COLUMN_TYPE verlaengert - so bleiben auch Altwerte (z. B. EINGANGSRECHNUNG)
--    erhalten. NULL/NOT NULL und ein vorhandener DEFAULT bleiben, wie sie sind;
--    ein Spalten-COMMENT oder eine abweichende Spalten-Collation wuerde MODIFY
--    nicht uebernehmen - die drei Spalten haben beides nicht (Tabellen-Default).
--    Zu kurze VARCHAR-Spalten werden verbreitert. Fehlt Tabelle oder Spalte,
--    passiert nichts.
-- 2) lieferant_dokument_position bekommt werkstoff, charge, abmessung und
--    suchtext (normalisierte Verkettung fuer die Positionssuche, wird von
--    PositionsSuchtext in Java gepflegt; hier einmalig fuer vorhandene Zeilen).
--
-- Abteilungsrechte fuer den neuen Typ werden bewusst NICHT vorbelegt - der
-- Admin stellt sie unter Einstellungen -> Berechtigungen ein.

-- ---------------------------------------------------------------- 1) Typ-Spalten

-- lieferant_dokument.typ
SET @dt = NULL, @ct = NULL, @nl = NULL, @len = NULL, @def = NULL;
SELECT DATA_TYPE, COLUMN_TYPE, IS_NULLABLE, CHARACTER_MAXIMUM_LENGTH, COLUMN_DEFAULT
  INTO @dt, @ct, @nl, @len, @def
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lieferant_dokument' AND COLUMN_NAME = 'typ'
 LIMIT 1;
SET @sql = CASE
    WHEN @dt = 'enum' AND @ct NOT LIKE '%''WERKSTOFFZEUGNIS''%' THEN CONCAT(
        'ALTER TABLE lieferant_dokument MODIFY COLUMN typ ',
        LEFT(@ct, CHAR_LENGTH(@ct) - 1), ',''WERKSTOFFZEUGNIS'')',
        IF(@nl = 'NO', ' NOT NULL', ' NULL'),
        IF(@def IS NULL, '', CONCAT(' DEFAULT ', QUOTE(@def))))
    WHEN @dt = 'varchar' AND @len < 16 THEN CONCAT(
        'ALTER TABLE lieferant_dokument MODIFY COLUMN typ VARCHAR(30)',
        IF(@nl = 'NO', ' NOT NULL', ' NULL'),
        IF(@def IS NULL, '', CONCAT(' DEFAULT ', QUOTE(@def))))
    ELSE 'SELECT 1'
END;
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- beleg.dokument_typ (natives ENUM aus V304)
SET @dt = NULL, @ct = NULL, @nl = NULL, @len = NULL, @def = NULL;
SELECT DATA_TYPE, COLUMN_TYPE, IS_NULLABLE, CHARACTER_MAXIMUM_LENGTH, COLUMN_DEFAULT
  INTO @dt, @ct, @nl, @len, @def
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'beleg' AND COLUMN_NAME = 'dokument_typ'
 LIMIT 1;
SET @sql = CASE
    WHEN @dt = 'enum' AND @ct NOT LIKE '%''WERKSTOFFZEUGNIS''%' THEN CONCAT(
        'ALTER TABLE beleg MODIFY COLUMN dokument_typ ',
        LEFT(@ct, CHAR_LENGTH(@ct) - 1), ',''WERKSTOFFZEUGNIS'')',
        IF(@nl = 'NO', ' NOT NULL', ' NULL'),
        IF(@def IS NULL, '', CONCAT(' DEFAULT ', QUOTE(@def))))
    WHEN @dt = 'varchar' AND @len < 16 THEN CONCAT(
        'ALTER TABLE beleg MODIFY COLUMN dokument_typ VARCHAR(30)',
        IF(@nl = 'NO', ' NOT NULL', ' NULL'),
        IF(@def IS NULL, '', CONCAT(' DEFAULT ', QUOTE(@def))))
    ELSE 'SELECT 1'
END;
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- abteilung_dokument_berechtigung.dokument_typ
SET @dt = NULL, @ct = NULL, @nl = NULL, @len = NULL, @def = NULL;
SELECT DATA_TYPE, COLUMN_TYPE, IS_NULLABLE, CHARACTER_MAXIMUM_LENGTH, COLUMN_DEFAULT
  INTO @dt, @ct, @nl, @len, @def
  FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'abteilung_dokument_berechtigung'
   AND COLUMN_NAME = 'dokument_typ'
 LIMIT 1;
SET @sql = CASE
    WHEN @dt = 'enum' AND @ct NOT LIKE '%''WERKSTOFFZEUGNIS''%' THEN CONCAT(
        'ALTER TABLE abteilung_dokument_berechtigung MODIFY COLUMN dokument_typ ',
        LEFT(@ct, CHAR_LENGTH(@ct) - 1), ',''WERKSTOFFZEUGNIS'')',
        IF(@nl = 'NO', ' NOT NULL', ' NULL'),
        IF(@def IS NULL, '', CONCAT(' DEFAULT ', QUOTE(@def))))
    WHEN @dt = 'varchar' AND @len < 16 THEN CONCAT(
        'ALTER TABLE abteilung_dokument_berechtigung MODIFY COLUMN dokument_typ VARCHAR(50)',
        IF(@nl = 'NO', ' NOT NULL', ' NULL'),
        IF(@def IS NULL, '', CONCAT(' DEFAULT ', QUOTE(@def))))
    ELSE 'SELECT 1'
END;
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ------------------------------------------------ 2) Suchfelder an den Positionen

SET @col = (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lieferant_dokument_position'
               AND COLUMN_NAME = 'werkstoff');
SET @tab = (SELECT COUNT(*) FROM information_schema.TABLES
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lieferant_dokument_position');
SET @sql = IF(@tab = 1 AND @col = 0,
    'ALTER TABLE lieferant_dokument_position ADD COLUMN werkstoff VARCHAR(100) NULL AFTER bezeichnung',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col = (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lieferant_dokument_position'
               AND COLUMN_NAME = 'charge');
SET @sql = IF(@tab = 1 AND @col = 0,
    'ALTER TABLE lieferant_dokument_position ADD COLUMN charge VARCHAR(100) NULL AFTER werkstoff',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col = (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lieferant_dokument_position'
               AND COLUMN_NAME = 'abmessung');
SET @sql = IF(@tab = 1 AND @col = 0,
    'ALTER TABLE lieferant_dokument_position ADD COLUMN abmessung VARCHAR(100) NULL AFTER charge',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col = (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lieferant_dokument_position'
               AND COLUMN_NAME = 'suchtext');
SET @sql = IF(@tab = 1 AND @col = 0,
    'ALTER TABLE lieferant_dokument_position ADD COLUMN suchtext VARCHAR(1000) NULL',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Suchtext fuer vorhandene Positionen - gleiche Regeln wie PositionsSuchtext:
-- klein, Malzeichen/Stern -> x, kein Leerraum um x zwischen Ziffern
-- ("50 x 5" -> "50x5"), Leerraum zusammengefasst. Possessive Quantifizierer.
SET @sql = IF(@tab = 1,
    'UPDATE lieferant_dokument_position
        SET suchtext = LEFT(TRIM(REGEXP_REPLACE(
                REGEXP_REPLACE(
                    REGEXP_REPLACE(
                        LOWER(CONCAT_WS('' '', bezeichnung, externe_artikelnummer, werkstoff, charge, abmessung)),
                        ''[×*]'', ''x''),
                    ''(?<=[0-9])[[:space:]]*+x[[:space:]]*+(?=[0-9])'', ''x''),
                ''[[:space:]]++'', '' '')), 1000)
      WHERE suchtext IS NULL',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
