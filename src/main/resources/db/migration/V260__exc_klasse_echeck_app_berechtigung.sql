-- V213: EXC-Klasse für Projekte (EN 1090 EXC 1-4) + E-Check App-Berechtigung pro Abteilung

-- EXC-Klasse am Projekt (optional, NULL = kein EN 1090 Projekt)
-- MySQL 8.0 doesn't support ADD COLUMN IF NOT EXISTS
SET @col1 = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'projekt' AND COLUMN_NAME = 'exc_klasse');
SET @sql1 = IF(@col1 = 0, 'ALTER TABLE projekt ADD COLUMN exc_klasse VARCHAR(10) NULL', 'SELECT 1');
PREPARE stmt1 FROM @sql1;
EXECUTE stmt1;
DEALLOCATE PREPARE stmt1;

-- E-Check App-Freigabe pro Abteilung
-- Wenn TRUE: Mitarbeiter dieser Abteilung sehen in der Zeiterfassungs-App den E-Check Menüpunkt
SET @col2 = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'abteilung' AND COLUMN_NAME = 'darf_echeck_app');
SET @sql2 = IF(@col2 = 0, 'ALTER TABLE abteilung ADD COLUMN darf_echeck_app BOOLEAN NOT NULL DEFAULT FALSE', 'SELECT 1');
PREPARE stmt2 FROM @sql2;
EXECUTE stmt2;
DEALLOCATE PREPARE stmt2;
