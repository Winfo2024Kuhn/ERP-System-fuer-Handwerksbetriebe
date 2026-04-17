-- Zeitpunkt des letzten Exports (PDF-Download oder E-Mail-Versand) pro Bestellposition.
-- Solange NULL, ist die Position editierbar. Sobald gesetzt, wird sie als "exportiert" gesperrt.

SET @col = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'artikel_in_projekt'
              AND COLUMN_NAME = 'exportiert_am');
SET @sql = IF(@col = 0,
              'ALTER TABLE artikel_in_projekt ADD COLUMN exportiert_am DATETIME NULL',
              'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
