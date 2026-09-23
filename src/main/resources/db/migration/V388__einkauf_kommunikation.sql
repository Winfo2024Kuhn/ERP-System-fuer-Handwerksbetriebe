CREATE TABLE IF NOT EXISTS einkauf_mail_zuordnung (
    id BIGINT NOT NULL AUTO_INCREMENT,
    email_id BIGINT NOT NULL,
    typ VARCHAR(24) NULL,
    vorgang_id BIGINT NULL,
    beteiligung_id BIGINT NULL,
    revision_id BIGINT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PRUEFEN',
    quelle VARCHAR(40) NOT NULL DEFAULT 'KEIN_EINDEUTIGER_TREFFER',
    bestaetigt BOOLEAN NOT NULL DEFAULT FALSE,
    begruendung VARCHAR(1000) NULL,
    bestaetigt_von BIGINT NULL,
    bestaetigt_am TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_einkauf_mail_email UNIQUE (email_id),
    CONSTRAINT fk_einkauf_mail_email FOREIGN KEY (email_id) REFERENCES email(id),
    INDEX idx_einkauf_mail_vorgang (typ, vorgang_id, id)
);

CREATE TABLE IF NOT EXISTS einkauf_kommunikation_vorschau (
    freigabe_token CHAR(64) NOT NULL,
    anfrage_id BIGINT NOT NULL,
    beteiligung_id BIGINT NOT NULL,
    revision_id BIGINT NOT NULL,
    vorlage_id BIGINT NOT NULL,
    vorlage_version BIGINT NOT NULL,
    subject VARCHAR(998) NOT NULL,
    html_body LONGTEXT NOT NULL,
    empfaenger VARCHAR(1000) NOT NULL,
    anlage_version_ids JSON NOT NULL,
    pdf_datei_id BIGINT NOT NULL,
    pdf_sha256 CHAR(64) NOT NULL,
    inhalt_sha256 CHAR(64) NOT NULL,
    erstellt_am TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    gueltig_bis TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (freigabe_token),
    INDEX idx_einkauf_vorschau_bindung (anfrage_id, beteiligung_id, revision_id),
    INDEX idx_einkauf_vorschau_ablauf (gueltig_bis)
);

SET @einkauf_ddl = IF(
    (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE()
        AND table_name = 'einkaufsanfrage_lieferant' AND column_name = 'versand_annahmeereignis') = 0,
    'ALTER TABLE einkaufsanfrage_lieferant ADD COLUMN versand_annahmeereignis VARCHAR(36) NULL',
    'SELECT 1');
PREPARE einkauf_stmt FROM @einkauf_ddl;
EXECUTE einkauf_stmt;
DEALLOCATE PREPARE einkauf_stmt;

SET @einkauf_ddl = IF(
    (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE()
        AND table_name = 'einkaufsanfrage_lieferant' AND index_name = 'idx_anfrage_lieferant_annahme') = 0,
    'ALTER TABLE einkaufsanfrage_lieferant ADD INDEX idx_anfrage_lieferant_annahme (versand_annahmeereignis)',
    'SELECT 1');
PREPARE einkauf_stmt FROM @einkauf_ddl;
EXECUTE einkauf_stmt;
DEALLOCATE PREPARE einkauf_stmt;

SET @einkauf_ddl = IF(
    (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE()
        AND table_name = 'email' AND index_name = 'idx_email_einkauf_recovery') = 0,
    'CREATE INDEX idx_email_einkauf_recovery ON email (konto_id, direction, id)',
    'SELECT 1');
PREPARE einkauf_stmt FROM @einkauf_ddl;
EXECUTE einkauf_stmt;
DEALLOCATE PREPARE einkauf_stmt;
