-- Spalte zeitbuchung_audit.geaendert_via als VARCHAR(50) festlegen.
-- Hintergrund: Die Tabelle wurde ursprünglich per hibernate ddl-auto angelegt.
-- Hibernate hatte die Spalte als MySQL ENUM(...) erzeugt (Default für
-- @Enumerated(STRING) ohne columnDefinition). Ergebnis:
--   1) Neue Enum-Werte (z.B. ADMIN_KORREKTUR) lösten "Data truncated" aus.
--   2) Schema-Validation scheiterte nach reiner Längen-Anpassung an ENUM vs VARCHAR.
-- VARCHAR(50) ist robust gegen künftige Enum-Erweiterungen ohne DDL-Migration.
SET @current_type := (
    SELECT COLUMN_TYPE FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'zeitbuchung_audit'
      AND COLUMN_NAME = 'geaendert_via'
);
SET @sql := IF(@current_type IS NULL OR LOWER(@current_type) = 'varchar(50)',
    'SELECT 1',
    'ALTER TABLE zeitbuchung_audit MODIFY COLUMN geaendert_via VARCHAR(50) NOT NULL');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
