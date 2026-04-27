-- Verknüpft jeden Lieferanten optional mit einer Standard-Kostenstelle.
-- Beim Anlegen neuer Bestellungen / Eingangsrechnungen wird diese vorgeschlagen,
-- damit der Nutzer nicht jedes Mal die gleiche Kostenstelle manuell zuweisen muss.

SET @col_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME   = 'lieferanten'
      AND COLUMN_NAME  = 'standard_kostenstelle_id'
);

SET @add_col = IF(@col_exists = 0,
    'ALTER TABLE lieferanten ADD COLUMN standard_kostenstelle_id BIGINT NULL',
    'SELECT 1'
);
PREPARE stmt FROM @add_col;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @fk_exists = (
    SELECT COUNT(*)
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA    = DATABASE()
      AND TABLE_NAME      = 'lieferanten'
      AND CONSTRAINT_NAME = 'fk_lieferanten_standard_kostenstelle'
      AND CONSTRAINT_TYPE = 'FOREIGN KEY'
);

SET @add_fk = IF(@fk_exists = 0,
    'ALTER TABLE lieferanten ADD CONSTRAINT fk_lieferanten_standard_kostenstelle FOREIGN KEY (standard_kostenstelle_id) REFERENCES firma_kostenstelle(id) ON DELETE SET NULL',
    'SELECT 1'
);
PREPARE stmt2 FROM @add_fk;
EXECUTE stmt2;
DEALLOCATE PREPARE stmt2;
