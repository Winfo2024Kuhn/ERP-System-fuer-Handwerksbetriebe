CREATE TABLE IF NOT EXISTS lieferant_einkauf_kontakt (
    id BIGINT NOT NULL AUTO_INCREMENT,
    version BIGINT NOT NULL DEFAULT 0,
    lieferant_id BIGINT NOT NULL,
    name VARCHAR(160) NULL,
    anrede VARCHAR(40) NULL,
    email VARCHAR(254) NOT NULL,
    standard_anfrage BOOLEAN NOT NULL DEFAULT FALSE,
    standard_bestellung BOOLEAN NOT NULL DEFAULT FALSE,
    aktiv BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (id),
    KEY idx_lieferant_einkauf_kontakt_aktiv (lieferant_id, aktiv, id),
    CONSTRAINT fk_lieferant_einkauf_kontakt_lieferant
        FOREIGN KEY (lieferant_id) REFERENCES lieferanten (id)
) ENGINE=InnoDB;
