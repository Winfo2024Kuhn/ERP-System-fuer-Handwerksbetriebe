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

ALTER TABLE einkaufsanfrage_lieferant
    ADD COLUMN versand_annahmeereignis VARCHAR(36) NULL,
    ADD INDEX idx_anfrage_lieferant_annahme (versand_annahmeereignis);
