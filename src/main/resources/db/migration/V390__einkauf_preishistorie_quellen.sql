-- Jede Ergänzung ist einzeln abgesichert, damit auch ein teilweise angewandter Stand reparierbar bleibt.
SET @einkauf_ddl = IF(
    (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'lieferanten_artikel_preise' AND column_name = 'scope') = 0,
    'ALTER TABLE lieferanten_artikel_preise ADD COLUMN scope ENUM(''STANDARD'',''PROJEKT'',''MENGENSTAFFEL'') NOT NULL DEFAULT ''STANDARD''',
    'SELECT 1');
PREPARE einkauf_stmt FROM @einkauf_ddl;
EXECUTE einkauf_stmt;
DEALLOCATE PREPARE einkauf_stmt;
SET @einkauf_ddl = IF(
    (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'lieferanten_artikel_preise' AND column_name = 'projekt_id') = 0,
    'ALTER TABLE lieferanten_artikel_preise ADD COLUMN projekt_id BIGINT NULL',
    'SELECT 1');
PREPARE einkauf_stmt FROM @einkauf_ddl;
EXECUTE einkauf_stmt;
DEALLOCATE PREPARE einkauf_stmt;
SET @einkauf_ddl = IF(
    (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'lieferanten_artikel_preise' AND column_name = 'ab_menge') = 0,
    'ALTER TABLE lieferanten_artikel_preise ADD COLUMN ab_menge DECIMAL(19,6) NULL',
    'SELECT 1');
PREPARE einkauf_stmt FROM @einkauf_ddl;
EXECUTE einkauf_stmt;
DEALLOCATE PREPARE einkauf_stmt;
SET @einkauf_ddl = IF(
    (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'lieferanten_artikel_preise' AND column_name = 'bis_menge') = 0,
    'ALTER TABLE lieferanten_artikel_preise ADD COLUMN bis_menge DECIMAL(19,6) NULL',
    'SELECT 1');
PREPARE einkauf_stmt FROM @einkauf_ddl;
EXECUTE einkauf_stmt;
DEALLOCATE PREPARE einkauf_stmt;
SET @einkauf_ddl = IF(
    (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'lieferanten_artikel_preise' AND column_name = 'gueltig_ab') = 0,
    'ALTER TABLE lieferanten_artikel_preise ADD COLUMN gueltig_ab DATE NULL',
    'SELECT 1');
PREPARE einkauf_stmt FROM @einkauf_ddl;
EXECUTE einkauf_stmt;
DEALLOCATE PREPARE einkauf_stmt;
SET @einkauf_ddl = IF(
    (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'lieferanten_artikel_preise' AND column_name = 'gueltig_bis') = 0,
    'ALTER TABLE lieferanten_artikel_preise ADD COLUMN gueltig_bis DATE NULL',
    'SELECT 1');
PREPARE einkauf_stmt FROM @einkauf_ddl;
EXECUTE einkauf_stmt;
DEALLOCATE PREPARE einkauf_stmt;
SET @einkauf_ddl = IF(
    (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'lieferanten_artikel_preise' AND column_name = 'waehrung') = 0,
    'ALTER TABLE lieferanten_artikel_preise ADD COLUMN waehrung VARCHAR(3) NOT NULL DEFAULT ''EUR''',
    'SELECT 1');
PREPARE einkauf_stmt FROM @einkauf_ddl;
EXECUTE einkauf_stmt;
DEALLOCATE PREPARE einkauf_stmt;
SET @einkauf_ddl = IF(
    (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'lieferanten_artikel_preise' AND column_name = 'einheit') = 0,
    'ALTER TABLE lieferanten_artikel_preise ADD COLUMN einheit VARCHAR(24) NULL',
    'SELECT 1');
PREPARE einkauf_stmt FROM @einkauf_ddl;
EXECUTE einkauf_stmt;
DEALLOCATE PREPARE einkauf_stmt;
SET @einkauf_ddl = IF(
    (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'lieferanten_artikel_preise' AND column_name = 'preisbasis_menge') = 0,
    'ALTER TABLE lieferanten_artikel_preise ADD COLUMN preisbasis_menge DECIMAL(19,6) NULL',
    'SELECT 1');
PREPARE einkauf_stmt FROM @einkauf_ddl;
EXECUTE einkauf_stmt;
DEALLOCATE PREPARE einkauf_stmt;
SET @einkauf_ddl = IF(
    (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'lieferanten_artikel_preise' AND column_name = 'angebotsversion_id') = 0,
    'ALTER TABLE lieferanten_artikel_preise ADD COLUMN angebotsversion_id BIGINT NULL',
    'SELECT 1');
PREPARE einkauf_stmt FROM @einkauf_ddl;
EXECUTE einkauf_stmt;
DEALLOCATE PREPARE einkauf_stmt;
SET @einkauf_ddl = IF(
    (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'lieferanten_artikel_preise' AND column_name = 'angebotsposition_id') = 0,
    'ALTER TABLE lieferanten_artikel_preise ADD COLUMN angebotsposition_id BIGINT NULL',
    'SELECT 1');
PREPARE einkauf_stmt FROM @einkauf_ddl;
EXECUTE einkauf_stmt;
DEALLOCATE PREPARE einkauf_stmt;
SET @einkauf_ddl = IF(
    (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'lieferanten_artikel_preise' AND column_name = 'idempotenz_key') = 0,
    'ALTER TABLE lieferanten_artikel_preise ADD COLUMN idempotenz_key VARCHAR(36) NULL',
    'SELECT 1');
PREPARE einkauf_stmt FROM @einkauf_ddl;
EXECUTE einkauf_stmt;
DEALLOCATE PREPARE einkauf_stmt;
SET @einkauf_ddl = IF(
    (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'lieferanten_artikel_preise' AND column_name = 'komponenten_hash') = 0,
    'ALTER TABLE lieferanten_artikel_preise ADD COLUMN komponenten_hash VARCHAR(64) NULL',
    'SELECT 1');
PREPARE einkauf_stmt FROM @einkauf_ddl;
EXECUTE einkauf_stmt;
DEALLOCATE PREPARE einkauf_stmt;
SET @einkauf_ddl = IF(
    (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'lieferanten_artikel_preise' AND index_name = 'ix_lap_scope_current') = 0,
    'CREATE INDEX ix_lap_scope_current ON lieferanten_artikel_preise (artikel_id, lieferant_id, scope, projekt_id, aktuell)',
    'SELECT 1');
PREPARE einkauf_stmt FROM @einkauf_ddl;
EXECUTE einkauf_stmt;
DEALLOCATE PREPARE einkauf_stmt;
SET @einkauf_ddl = IF(
    (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'lieferanten_artikel_preise' AND index_name = 'uk_lap_idempotenz_key') = 0,
    'CREATE UNIQUE INDEX uk_lap_idempotenz_key ON lieferanten_artikel_preise (idempotenz_key)',
    'SELECT 1');
PREPARE einkauf_stmt FROM @einkauf_ddl;
EXECUTE einkauf_stmt;
DEALLOCATE PREPARE einkauf_stmt;
