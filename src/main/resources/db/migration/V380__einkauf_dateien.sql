CREATE TABLE IF NOT EXISTS einkauf_datei (
    id BIGINT NOT NULL AUTO_INCREMENT,
    sha256 CHAR(64) NOT NULL,
    gespeicherter_name CHAR(36) NULL,
    original_name VARCHAR(255) NOT NULL,
    mime_typ VARCHAR(100) NOT NULL,
    byte_anzahl BIGINT NOT NULL,
    email_attachment_id BIGINT NULL,
    lieferant_dokument_id BIGINT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_einkauf_datei_sha256 UNIQUE (sha256),
    KEY idx_einkauf_datei_email_attachment (email_attachment_id),
    KEY idx_einkauf_datei_lieferant_dokument (lieferant_dokument_id),
    CONSTRAINT fk_einkauf_datei_email_attachment FOREIGN KEY (email_attachment_id) REFERENCES email_attachment(id),
    CONSTRAINT fk_einkauf_datei_lieferant_dokument FOREIGN KEY (lieferant_dokument_id) REFERENCES lieferant_dokument(id)
);

CREATE TABLE IF NOT EXISTS einkauf_anlage_version (
    id BIGINT NOT NULL AUTO_INCREMENT,
    bedarf_id BIGINT NOT NULL,
    datei_id BIGINT NOT NULL,
    revision VARCHAR(80) NOT NULL,
    freigegeben BOOLEAN NOT NULL DEFAULT FALSE,
    versendet BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    CONSTRAINT uk_einkauf_anlage_revision UNIQUE (bedarf_id, revision),
    KEY idx_einkauf_anlage_datei (datei_id),
    CONSTRAINT fk_einkauf_anlage_bedarf FOREIGN KEY (bedarf_id) REFERENCES einkauf_bedarf(id),
    CONSTRAINT fk_einkauf_anlage_datei FOREIGN KEY (datei_id) REFERENCES einkauf_datei(id)
);
