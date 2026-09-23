CREATE TABLE IF NOT EXISTS frontend_user_einkauf_recht (
    profile_id BIGINT NOT NULL,
    recht ENUM('LESEN','BEARBEITEN','ANFRAGE_SENDEN','BESTELLUNG_FREIGEBEN','ZEUGNIS_PRUEFEN') NOT NULL,
    PRIMARY KEY (profile_id, recht),
    CONSTRAINT fk_frontend_user_einkauf_recht_profile
        FOREIGN KEY (profile_id) REFERENCES frontend_user_profile(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS einkauf_audit (
    id BIGINT NOT NULL AUTO_INCREMENT,
    vorgang_typ VARCHAR(80) NOT NULL,
    vorgang_id BIGINT NOT NULL,
    aktion VARCHAR(80) NOT NULL,
    akteur_id BIGINT NOT NULL,
    zeitpunkt TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    vorher_snapshot JSON NULL,
    nachher_snapshot JSON NULL,
    grund VARCHAR(1000) NULL,
    PRIMARY KEY (id),
    KEY ix_einkauf_audit_vorgang (vorgang_typ, vorgang_id, id),
    KEY ix_einkauf_audit_akteur_zeit (akteur_id, zeitpunkt)
);
