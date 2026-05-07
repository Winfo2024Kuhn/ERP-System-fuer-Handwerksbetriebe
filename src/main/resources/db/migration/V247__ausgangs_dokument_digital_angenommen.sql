-- Flag, ob ein AusgangsGeschaeftsDokument vom Kunden digital angenommen wurde.
-- Wird beim Akzeptieren einer DokumentFreigabe gesetzt und sperrt das Dokument
-- (Angebot wird verbindlich; AB wird sofort als verbindlich eingefroren).
-- Idempotent: nur hinzufügen wenn die Spalte fehlt.
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'ausgangs_geschaeftsdokument'
      AND column_name = 'digital_angenommen'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE ausgangs_geschaeftsdokument ADD COLUMN digital_angenommen BOOLEAN NOT NULL DEFAULT FALSE',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
