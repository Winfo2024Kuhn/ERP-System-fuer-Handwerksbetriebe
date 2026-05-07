-- Fixmaß pro Bestellposition (in Millimetern). Für Werkstoffe/Träger, die auf Länge
-- bestellt werden (z. B. 6000 mm × 3 Stück). Optional, NULL = kein Fixmaß.

-- MySQL 8.0 unterstützt kein "ADD COLUMN IF NOT EXISTS" — Prepared Statement als Workaround.
SET @col = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'artikel_in_projekt'
              AND COLUMN_NAME = 'fixmass_mm');
SET @sql = IF(@col = 0,
              'ALTER TABLE artikel_in_projekt ADD COLUMN fixmass_mm INT NULL',
              'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
