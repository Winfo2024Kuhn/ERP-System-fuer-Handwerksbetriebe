-- Spalte zum Ausblenden alter Lieferanten-Dokumente in der Bestellübersicht.
-- Wird genutzt, um z.B. uralte Anfragen/Rechnungen, die nicht mehr zugeordnet
-- werden können, aus der Tagesansicht zu nehmen, ohne sie zu löschen.

SET @col_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME   = 'lieferant_dokument'
      AND COLUMN_NAME  = 'ausgeblendet'
);

SET @add_col = IF(@col_exists = 0,
    'ALTER TABLE lieferant_dokument ADD COLUMN ausgeblendet BOOLEAN NOT NULL DEFAULT FALSE',
    'SELECT 1'
);
PREPARE stmt FROM @add_col;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists = (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME   = 'lieferant_dokument'
      AND INDEX_NAME   = 'idx_lieferant_dokument_ausgeblendet'
);

SET @add_idx = IF(@idx_exists = 0,
    'CREATE INDEX idx_lieferant_dokument_ausgeblendet ON lieferant_dokument (ausgeblendet)',
    'SELECT 1'
);
PREPARE stmt2 FROM @add_idx;
EXECUTE stmt2;
DEALLOCATE PREPARE stmt2;
