-- Historische Details nur für neue Abschlüsse. Kein Backfill bestehender Monatswerte.

SET @col_exists := (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'monats_saldo' AND column_name = 'urlaub_stunden');
SET @sql := IF(@col_exists = 0, 'ALTER TABLE monats_saldo ADD COLUMN urlaub_stunden DECIMAL(10,2) NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'monats_saldo' AND column_name = 'krankheit_stunden');
SET @sql := IF(@col_exists = 0, 'ALTER TABLE monats_saldo ADD COLUMN krankheit_stunden DECIMAL(10,2) NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'monats_saldo' AND column_name = 'fortbildung_stunden');
SET @sql := IF(@col_exists = 0, 'ALTER TABLE monats_saldo ADD COLUMN fortbildung_stunden DECIMAL(10,2) NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'monats_saldo' AND column_name = 'zeitausgleich_stunden');
SET @sql := IF(@col_exists = 0, 'ALTER TABLE monats_saldo ADD COLUMN zeitausgleich_stunden DECIMAL(10,2) NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'monats_saldo' AND column_name = 'krankengeld_stunden');
SET @sql := IF(@col_exists = 0, 'ALTER TABLE monats_saldo ADD COLUMN krankengeld_stunden DECIMAL(10,2) NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'monats_saldo' AND column_name = 'wiedereingliederung_stunden');
SET @sql := IF(@col_exists = 0, 'ALTER TABLE monats_saldo ADD COLUMN wiedereingliederung_stunden DECIMAL(10,2) NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
