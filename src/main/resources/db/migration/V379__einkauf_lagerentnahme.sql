CREATE TABLE IF NOT EXISTS einkauf_lagerentnahme (
    id BIGINT NOT NULL AUTO_INCREMENT,
    projekt_id BIGINT NOT NULL,
    bedarf_id BIGINT NOT NULL,
    bedarf_version BIGINT NOT NULL,
    menge DECIMAL(19,6) NOT NULL,
    einheit ENUM('STUECK','METER','KILOGRAMM','TONNE','QUADRATMETER') NOT NULL,
    preis_je_einheit DECIMAL(19,6) NULL,
    preis_quelle VARCHAR(255) NULL,
    bewerteter_betrag DECIMAL(19,2) NULL,
    offener_bedarf_nach_entnahme DECIMAL(19,6) NOT NULL,
    entnommen_am TIMESTAMP(6) NOT NULL,
    idempotenz_key CHAR(36) NOT NULL,
    payload_hash CHAR(64) NOT NULL,
    akteur_id BIGINT NOT NULL,
    mitarbeiter_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_lagerentnahme_idempotenz UNIQUE (idempotenz_key),
    CONSTRAINT fk_lagerentnahme_projekt FOREIGN KEY (projekt_id) REFERENCES projekt(id),
    CONSTRAINT fk_lagerentnahme_bedarf FOREIGN KEY (bedarf_id) REFERENCES einkauf_bedarf(id),
    CONSTRAINT fk_lagerentnahme_akteur FOREIGN KEY (akteur_id) REFERENCES frontend_user_profile(id),
    CONSTRAINT fk_lagerentnahme_mitarbeiter FOREIGN KEY (mitarbeiter_id) REFERENCES mitarbeiter(id),
    INDEX ix_lagerentnahme_projekt_zeit (projekt_id, entnommen_am, id)
);

CREATE TABLE IF NOT EXISTS einkauf_lagerentnahme_bewertung (
    lagerentnahme_id BIGINT NOT NULL,
    reihenfolge INT NOT NULL,
    preis_je_einheit DECIMAL(19,6) NOT NULL,
    preis_quelle VARCHAR(255) NOT NULL,
    bewertet_am TIMESTAMP(6) NOT NULL,
    akteur_id BIGINT NOT NULL,
    mitarbeiter_id BIGINT NOT NULL,
    PRIMARY KEY (lagerentnahme_id, reihenfolge),
    CONSTRAINT fk_lagerentnahme_bewertung_entnahme FOREIGN KEY (lagerentnahme_id)
        REFERENCES einkauf_lagerentnahme(id),
    CONSTRAINT fk_lagerentnahme_bewertung_akteur FOREIGN KEY (akteur_id)
        REFERENCES frontend_user_profile(id),
    CONSTRAINT fk_lagerentnahme_bewertung_mitarbeiter FOREIGN KEY (mitarbeiter_id)
        REFERENCES mitarbeiter(id)
);
