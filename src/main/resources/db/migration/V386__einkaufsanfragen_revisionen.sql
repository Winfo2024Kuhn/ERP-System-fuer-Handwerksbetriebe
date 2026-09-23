CREATE TABLE IF NOT EXISTS einkaufsanfrage (
    id BIGINT NOT NULL AUTO_INCREMENT,
    version BIGINT NOT NULL,
    pa_nummer VARCHAR(32) NOT NULL,
    zustaendig_id BIGINT NULL,
    idempotenz_key CHAR(36) NOT NULL,
    payload_hash CHAR(64) NOT NULL,
    aktuelle_revision_id BIGINT NULL,
    angelegt_am TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    geloescht_am TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_einkaufsanfrage_pa (pa_nummer),
    UNIQUE KEY uk_einkaufsanfrage_idempotenz (idempotenz_key)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS einkaufsanfrage_revision (
    id BIGINT NOT NULL AUTO_INCREMENT,
    anfrage_id BIGINT NOT NULL,
    nummer INT NOT NULL,
    antwortfrist DATE NULL,
    liefertermin DATE NULL,
    status VARCHAR(24) NOT NULL,
    idempotenz_key CHAR(36) NOT NULL,
    payload_hash CHAR(64) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_anfrage_revision_nummer (anfrage_id, nummer),
    UNIQUE KEY uk_anfrage_revision_idempotenz (idempotenz_key),
    CONSTRAINT fk_anfrage_revision_kopf FOREIGN KEY (anfrage_id) REFERENCES einkaufsanfrage(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS einkaufsanfrage_position (
    id BIGINT NOT NULL AUTO_INCREMENT,
    revision_id BIGINT NOT NULL,
    position_snapshot JSON NOT NULL,
    menge DECIMAL(19,6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_anfrage_position_revision (revision_id),
    CONSTRAINT fk_anfrage_position_revision FOREIGN KEY (revision_id) REFERENCES einkaufsanfrage_revision(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS einkaufsanfrage_herkunft (
    id BIGINT NOT NULL AUTO_INCREMENT,
    position_id BIGINT NOT NULL,
    bedarf_id BIGINT NOT NULL,
    bedarf_version BIGINT NOT NULL,
    menge DECIMAL(19,6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_anfrage_herkunft_bedarf (bedarf_id),
    CONSTRAINT fk_anfrage_herkunft_position FOREIGN KEY (position_id) REFERENCES einkaufsanfrage_position(id),
    CONSTRAINT fk_anfrage_herkunft_bedarf FOREIGN KEY (bedarf_id) REFERENCES einkauf_bedarf(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS einkaufsanfrage_lieferant (
    id BIGINT NOT NULL AUTO_INCREMENT,
    revision_id BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    kontakt_snapshot JSON NOT NULL,
    rueckmeldecode CHAR(32) NOT NULL,
    status VARCHAR(24) NOT NULL,
    antwort_am TIMESTAMP(6) NULL,
    versandversuche JSON NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_anfrage_reply_code (rueckmeldecode),
    KEY idx_anfrage_lieferant_revision (revision_id),
    CONSTRAINT fk_anfrage_lieferant_revision FOREIGN KEY (revision_id) REFERENCES einkaufsanfrage_revision(id)
) ENGINE=InnoDB;


SET @fk_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.REFERENTIAL_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'einkaufsanfrage'
      AND CONSTRAINT_NAME = 'fk_einkaufsanfrage_aktuelle_revision'
);
SET @sql := IF(@fk_exists = 0,
    'ALTER TABLE einkaufsanfrage ADD CONSTRAINT fk_einkaufsanfrage_aktuelle_revision FOREIGN KEY (aktuelle_revision_id) REFERENCES einkaufsanfrage_revision(id)',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
