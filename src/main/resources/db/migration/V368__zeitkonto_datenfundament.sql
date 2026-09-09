-- Datenfundament: keine Änderung bestehender Saldozahlen, keine automatischen Abschlüsse.
-- Idempotentes information_schema/PREPARE-Muster wie V367.

SET @col_exists := (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'mitarbeiter' AND column_name = 'art');
SET @sql := IF(@col_exists = 0, 'ALTER TABLE mitarbeiter ADD COLUMN art ENUM(''MENSCH'',''SYSTEM'') NOT NULL DEFAULT ''MENSCH''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'mitarbeiter' AND column_name = 'fuehrt_zeitkonto');
SET @sql := IF(@col_exists = 0, 'ALTER TABLE mitarbeiter ADD COLUMN fuehrt_zeitkonto BOOLEAN NOT NULL DEFAULT TRUE', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'abteilung' AND column_name = 'darf_monat_abschliessen');
SET @sql := IF(@col_exists = 0, 'ALTER TABLE abteilung ADD COLUMN darf_monat_abschliessen BOOLEAN NOT NULL DEFAULT FALSE', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'monats_saldo' AND column_name = 'festgeschrieben');
SET @sql := IF(@col_exists = 0, 'ALTER TABLE monats_saldo ADD COLUMN festgeschrieben BOOLEAN NOT NULL DEFAULT FALSE', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'monats_saldo' AND column_name = 'festgeschrieben_am');
SET @sql := IF(@col_exists = 0, 'ALTER TABLE monats_saldo ADD COLUMN festgeschrieben_am DATETIME(6) NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'monats_saldo' AND column_name = 'festgeschrieben_von_mitarbeiter_id');
SET @sql := IF(@col_exists = 0, 'ALTER TABLE monats_saldo ADD COLUMN festgeschrieben_von_mitarbeiter_id BIGINT NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'monats_saldo' AND column_name = 'version');
SET @sql := IF(@col_exists = 0, 'ALTER TABLE monats_saldo ADD COLUMN version BIGINT NOT NULL DEFAULT 0', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @fk := (SELECT COUNT(*) FROM information_schema.table_constraints
    WHERE table_schema = DATABASE() AND table_name = 'monats_saldo'
      AND constraint_name = 'fk_monats_saldo_festgeschrieben_von');
SET @sql := IF(@fk = 0,
    'ALTER TABLE monats_saldo ADD CONSTRAINT fk_monats_saldo_festgeschrieben_von
     FOREIGN KEY (festgeschrieben_von_mitarbeiter_id) REFERENCES mitarbeiter(id)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS zeitkontenmodell (
    id BIGINT NOT NULL AUTO_INCREMENT,
    version BIGINT NOT NULL DEFAULT 0,
    bezeichnung VARCHAR(255) NOT NULL,
    montag_stunden DECIMAL(4,2) NULL,
    dienstag_stunden DECIMAL(4,2) NULL,
    mittwoch_stunden DECIMAL(4,2) NULL,
    donnerstag_stunden DECIMAL(4,2) NULL,
    freitag_stunden DECIMAL(4,2) NULL,
    samstag_stunden DECIMAL(4,2) NULL,
    sonntag_stunden DECIMAL(4,2) NULL,
    buchung_start_zeit TIME(6) NULL,
    buchung_ende_zeit TIME(6) NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS zeitkonto_version (
    id BIGINT NOT NULL AUTO_INCREMENT,
    version BIGINT NOT NULL DEFAULT 0,
    mitarbeiter_id BIGINT NOT NULL,
    gueltig_von DATE NOT NULL,
    gueltig_bis DATE NULL,
    vorlage_id BIGINT NULL,
    montag_stunden DECIMAL(4,2) NULL,
    dienstag_stunden DECIMAL(4,2) NULL,
    mittwoch_stunden DECIMAL(4,2) NULL,
    donnerstag_stunden DECIMAL(4,2) NULL,
    freitag_stunden DECIMAL(4,2) NULL,
    samstag_stunden DECIMAL(4,2) NULL,
    sonntag_stunden DECIMAL(4,2) NULL,
    buchung_start_zeit TIME(6) NULL,
    buchung_ende_zeit TIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_zeitkonto_version_mitarbeiter_von UNIQUE (mitarbeiter_id, gueltig_von),
    CONSTRAINT fk_zeitkonto_version_mitarbeiter FOREIGN KEY (mitarbeiter_id) REFERENCES mitarbeiter(id),
    CONSTRAINT fk_zeitkonto_version_vorlage FOREIGN KEY (vorlage_id) REFERENCES zeitkontenmodell(id),
    CONSTRAINT chk_zeitkonto_version_zeitraum CHECK (gueltig_bis IS NULL OR gueltig_bis >= gueltig_von)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS monatsabschluss_audit (
    id BIGINT NOT NULL AUTO_INCREMENT,
    mitarbeiter_id BIGINT NOT NULL,
    jahr INT NOT NULL,
    monat INT NOT NULL,
    aktion ENUM('ABSCHLIESSEN','OEFFNEN') NOT NULL,
    akteur_id BIGINT NOT NULL,
    zeitpunkt DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_monatsabschluss_audit_mitarbeiter FOREIGN KEY (mitarbeiter_id) REFERENCES mitarbeiter(id),
    CONSTRAINT fk_monatsabschluss_audit_akteur FOREIGN KEY (akteur_id) REFERENCES mitarbeiter(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET @idx := (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE()
    AND table_name = 'monatsabschluss_audit' AND index_name = 'idx_monatsabschluss_audit_monat');
SET @sql := IF(@idx = 0,
    'CREATE INDEX idx_monatsabschluss_audit_monat ON monatsabschluss_audit (mitarbeiter_id, jahr, monat, zeitpunkt, id)',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
