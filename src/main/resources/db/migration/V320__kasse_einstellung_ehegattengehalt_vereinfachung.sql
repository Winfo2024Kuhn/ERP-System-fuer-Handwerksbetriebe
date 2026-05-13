-- Vereinfachung des Ehegattengehalt-Schedulers: das Buchungskonto ist nun
-- hart "4120 Loehne & Gehaelter" (siehe V307), und Ehegattengehalt ist rein
-- buchhalterisch — keine Kostenstelle. Spalten ehegattengehalt_sachkonto_id
-- und ehegattengehalt_kostenstelle_id werden entfernt.
--
-- Idempotent: prueft via information_schema, ob die FK/Spalten noch existieren.

-- FK Sachkonto droppen (Name aus V319: fk_kasse_sachkonto)
SET @fk_exists := (
    SELECT COUNT(*) FROM information_schema.table_constraints
    WHERE table_schema = DATABASE()
      AND table_name = 'kasse_einstellung'
      AND constraint_name = 'fk_kasse_sachkonto'
      AND constraint_type = 'FOREIGN KEY'
);
SET @sql := IF(@fk_exists > 0,
    'ALTER TABLE kasse_einstellung DROP FOREIGN KEY fk_kasse_sachkonto',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- FK Kostenstelle droppen
SET @fk_exists := (
    SELECT COUNT(*) FROM information_schema.table_constraints
    WHERE table_schema = DATABASE()
      AND table_name = 'kasse_einstellung'
      AND constraint_name = 'fk_kasse_kostenstelle'
      AND constraint_type = 'FOREIGN KEY'
);
SET @sql := IF(@fk_exists > 0,
    'ALTER TABLE kasse_einstellung DROP FOREIGN KEY fk_kasse_kostenstelle',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Spalte ehegattengehalt_sachkonto_id droppen
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'kasse_einstellung'
      AND column_name = 'ehegattengehalt_sachkonto_id'
);
SET @sql := IF(@col_exists > 0,
    'ALTER TABLE kasse_einstellung DROP COLUMN ehegattengehalt_sachkonto_id',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Spalte ehegattengehalt_kostenstelle_id droppen
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'kasse_einstellung'
      AND column_name = 'ehegattengehalt_kostenstelle_id'
);
SET @sql := IF(@col_exists > 0,
    'ALTER TABLE kasse_einstellung DROP COLUMN ehegattengehalt_kostenstelle_id',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
