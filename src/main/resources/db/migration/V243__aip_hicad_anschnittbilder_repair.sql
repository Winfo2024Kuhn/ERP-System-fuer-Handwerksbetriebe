-- ═══════════════════════════════════════════════════════════════
-- V243: Repair fuer V242 — fehlende HiCAD-Anschnitt-Spalten nachziehen
-- ═══════════════════════════════════════════════════════════════
-- V242 hat die vier Spalten direkt per ALTER TABLE angelegt,
-- ohne Idempotenz-Pruefung. Auf manchen Umgebungen ist V242 in
-- flyway_schema_history zwar als applied markiert, die Spalten
-- fehlen aber tatsaechlich (Schema-Validation-Fehler beim Boot:
-- "missing column [anschnitt_flansch_text] in table [artikel_in_projekt]").
--
-- Diese Migration ist idempotent (PREPARE/EXECUTE mit
-- INFORMATION_SCHEMA-Check) und bringt das Schema sauber
-- in den Zielzustand — egal in welchem Vorzustand die DB ist.
-- MySQL 8.

-- 1) anschnittbild_steg_url
SET @col = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'artikel_in_projekt'
              AND COLUMN_NAME = 'anschnittbild_steg_url');
SET @sql = IF(@col = 0,
    'ALTER TABLE artikel_in_projekt ADD COLUMN anschnittbild_steg_url VARCHAR(500) NULL',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2) anschnittbild_flansch_url
SET @col = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'artikel_in_projekt'
              AND COLUMN_NAME = 'anschnittbild_flansch_url');
SET @sql = IF(@col = 0,
    'ALTER TABLE artikel_in_projekt ADD COLUMN anschnittbild_flansch_url VARCHAR(500) NULL',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3) anschnitt_steg_text
SET @col = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'artikel_in_projekt'
              AND COLUMN_NAME = 'anschnitt_steg_text');
SET @sql = IF(@col = 0,
    'ALTER TABLE artikel_in_projekt ADD COLUMN anschnitt_steg_text VARCHAR(80) NULL',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 4) anschnitt_flansch_text
SET @col = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'artikel_in_projekt'
              AND COLUMN_NAME = 'anschnitt_flansch_text');
SET @sql = IF(@col = 0,
    'ALTER TABLE artikel_in_projekt ADD COLUMN anschnitt_flansch_text VARCHAR(80) NULL',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
