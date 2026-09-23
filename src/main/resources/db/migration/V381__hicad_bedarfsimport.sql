CREATE TABLE IF NOT EXISTS hicad_import (
    id BIGINT NOT NULL AUTO_INCREMENT,
    version BIGINT NOT NULL DEFAULT 0,
    projekt_id BIGINT NOT NULL,
    datei_hash CHAR(64) NOT NULL,
    import_instanz CHAR(36) NOT NULL,
    akteur_id BIGINT NOT NULL,
    duplikat BOOLEAN NOT NULL DEFAULT FALSE,
    idempotenz_key CHAR(36) NULL,
    payload_hash CHAR(64) NULL,
    result_json LONGTEXT NULL,
    idempotenz_ergebnisse_json LONGTEXT NULL,
    PRIMARY KEY (id),
    KEY idx_hicad_hash_projekt (projekt_id, datei_hash),
    KEY idx_hicad_import_instanz (import_instanz)
);

CREATE TABLE IF NOT EXISTS hicad_import_zeile (
    id BIGINT NOT NULL AUTO_INCREMENT,
    import_id BIGINT NOT NULL,
    zeilennummer INT NOT NULL,
    rohtext VARCHAR(4000) NOT NULL,
    snapshot_json VARCHAR(8000) NULL,
    bild_datei_ids_json VARCHAR(4000) NULL,
    uebernommene_menge DECIMAL(19,6) NOT NULL DEFAULT 0,
    uebernommen BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    CONSTRAINT uk_hicad_import_zeilennummer UNIQUE (import_id, zeilennummer),
    CONSTRAINT fk_hicad_zeile_import FOREIGN KEY (import_id) REFERENCES hicad_import(id)
);
