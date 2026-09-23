ALTER TABLE lieferanten_artikel_preise
    ADD COLUMN scope ENUM('STANDARD','PROJEKT','MENGENSTAFFEL') NOT NULL DEFAULT 'STANDARD',
    ADD COLUMN projekt_id BIGINT NULL,
    ADD COLUMN ab_menge DECIMAL(19,6) NULL,
    ADD COLUMN bis_menge DECIMAL(19,6) NULL,
    ADD COLUMN gueltig_ab DATE NULL,
    ADD COLUMN gueltig_bis DATE NULL,
    ADD COLUMN waehrung VARCHAR(3) NOT NULL DEFAULT 'EUR',
    ADD COLUMN einheit VARCHAR(24) NULL,
    ADD COLUMN preisbasis_menge DECIMAL(19,6) NULL,
    ADD COLUMN angebotsversion_id BIGINT NULL,
    ADD COLUMN angebotsposition_id BIGINT NULL,
    ADD COLUMN idempotenz_key VARCHAR(36) NULL,
    ADD COLUMN komponenten_hash VARCHAR(64) NULL;

CREATE INDEX ix_lap_scope_current
    ON lieferanten_artikel_preise (artikel_id, lieferant_id, scope, projekt_id, aktuell);
CREATE UNIQUE INDEX uk_lap_idempotenz_key
    ON lieferanten_artikel_preise (idempotenz_key);
