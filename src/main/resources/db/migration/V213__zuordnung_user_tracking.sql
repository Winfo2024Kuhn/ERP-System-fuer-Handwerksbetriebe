-- Track which frontend user assigned an Eingangsrechnung to a project/Kostenstelle
-- Add column only if it does not already exist
SET @col_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME   = 'lieferant_dokument_projekt_anteil'
      AND COLUMN_NAME  = 'zugeordnet_von_user_id'
);

SET @add_col = IF(@col_exists = 0,
    'ALTER TABLE lieferant_dokument_projekt_anteil ADD COLUMN zugeordnet_von_user_id BIGINT NULL',
    'SELECT 1'
);
PREPARE stmt FROM @add_col;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Add FK only if it does not already exist
SET @fk_exists = (
    SELECT COUNT(*)
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA    = DATABASE()
      AND TABLE_NAME      = 'lieferant_dokument_projekt_anteil'
      AND CONSTRAINT_NAME = 'fk_projekt_anteil_zugeordnet_von'
      AND CONSTRAINT_TYPE = 'FOREIGN KEY'
);

SET @add_fk = IF(@fk_exists = 0,
    'ALTER TABLE lieferant_dokument_projekt_anteil ADD CONSTRAINT fk_projekt_anteil_zugeordnet_von FOREIGN KEY (zugeordnet_von_user_id) REFERENCES frontend_user_profile(id) ON DELETE SET NULL',
    'SELECT 1'
);
PREPARE stmt2 FROM @add_fk;
EXECUTE stmt2;
DEALLOCATE PREPARE stmt2;
