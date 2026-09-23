CREATE TABLE IF NOT EXISTS einkauf_angebot (
    id BIGINT NOT NULL AUTO_INCREMENT,
    version BIGINT NOT NULL DEFAULT 0,
    beteiligung_id BIGINT NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'ERFASST',
    PRIMARY KEY (id),
    CONSTRAINT uk_einkauf_angebot_beteiligung UNIQUE (beteiligung_id),
    CONSTRAINT fk_einkauf_angebot_beteiligung FOREIGN KEY (beteiligung_id) REFERENCES einkaufsanfrage_lieferant(id)
);

CREATE TABLE IF NOT EXISTS einkauf_angebot_version (
    id BIGINT NOT NULL AUTO_INCREMENT,
    version BIGINT NOT NULL DEFAULT 0,
    angebot_id BIGINT NOT NULL,
    anfrage_revision_id BIGINT NOT NULL,
    nummer INT NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'ERFASST',
    angebotsnummer VARCHAR(120) NULL,
    datum DATE NULL,
    gueltig_bis DATE NULL,
    waehrung CHAR(3) NOT NULL,
    zahlungsbedingungen VARCHAR(2000) NULL,
    skonto_prozent DECIMAL(9,6) NULL,
    skonto_tage INT NULL,
    email_id BIGINT NULL,
    original_datei_id BIGINT NULL,
    bestaetigt_von BIGINT NULL,
    bestaetigt_am TIMESTAMP(6) NULL,
    abweichung_bestaetigt_von BIGINT NULL,
    abweichung_bestaetigt_am TIMESTAMP(6) NULL,
    abweichung_bestaetigung VARCHAR(1000) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_angebot_version_nummer UNIQUE (angebot_id, nummer),
    CONSTRAINT fk_angebot_version_angebot FOREIGN KEY (angebot_id) REFERENCES einkauf_angebot(id),
    CONSTRAINT fk_angebot_version_anfrage_revision FOREIGN KEY (anfrage_revision_id) REFERENCES einkaufsanfrage_revision(id),
    CONSTRAINT fk_angebot_version_email FOREIGN KEY (email_id) REFERENCES email(id),
    CONSTRAINT fk_angebot_version_datei FOREIGN KEY (original_datei_id) REFERENCES einkauf_datei(id),
    INDEX idx_angebot_version_anfrage (anfrage_revision_id)
);

CREATE TABLE IF NOT EXISTS einkauf_angebot_position (
    id BIGINT NOT NULL AUTO_INCREMENT,
    version_id BIGINT NOT NULL,
    anfrage_position_id BIGINT NOT NULL,
    original_nummer VARCHAR(120) NULL,
    original_text VARCHAR(2000) NULL,
    angebotene_menge JSON NULL,
    mindestmenge DECIMAL(19,6) NULL,
    verpackungseinheit DECIMAL(19,6) NULL,
    liefertermin DATE NULL,
    abweichungen JSON NOT NULL,
    zeugnisse JSON NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_angebot_position_request UNIQUE (version_id, anfrage_position_id),
    CONSTRAINT fk_angebot_position_version FOREIGN KEY (version_id) REFERENCES einkauf_angebot_version(id),
    CONSTRAINT fk_angebot_position_request FOREIGN KEY (anfrage_position_id) REFERENCES einkaufsanfrage_position(id),
    INDEX idx_angebot_position_version (version_id)
);

CREATE TABLE IF NOT EXISTS einkauf_angebot_kosten (
    id BIGINT NOT NULL AUTO_INCREMENT,
    version_id BIGINT NOT NULL,
    position_id BIGINT NULL,
    schluessel VARCHAR(80) NOT NULL,
    art VARCHAR(24) NOT NULL,
    betrag DECIMAL(19,6) NULL,
    basis VARCHAR(24) NULL,
    basis_menge DECIMAL(19,6) NULL,
    prozent_basis_schluessel VARCHAR(80) NULL,
    enthalten BOOLEAN NOT NULL DEFAULT FALSE,
    variabel BOOLEAN NOT NULL DEFAULT FALSE,
    quelle VARCHAR(500) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_angebot_kosten_version FOREIGN KEY (version_id) REFERENCES einkauf_angebot_version(id),
    CONSTRAINT fk_angebot_kosten_position FOREIGN KEY (position_id) REFERENCES einkauf_angebot_position(id),
    INDEX idx_angebot_kosten_version (version_id, position_id, schluessel)
);
