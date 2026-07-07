CREATE TABLE IF NOT EXISTS lagerort (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(80) NOT NULL,
    name VARCHAR(160) NOT NULL,
    regal VARCHAR(80),
    fach VARCHAR(80),
    aktiv BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_lagerort_code UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS lagerbestand (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    artikel_id BIGINT NOT NULL,
    lagerort_id BIGINT NOT NULL,
    menge DECIMAL(19,4) NOT NULL DEFAULT 0,
    mindestbestand DECIMAL(19,4) NOT NULL DEFAULT 0,
    charge VARCHAR(60),
    bemerkung VARCHAR(255),
    aktualisiert_am TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_lagerbestand_artikel_lagerort UNIQUE (artikel_id, lagerort_id),
    CONSTRAINT fk_lagerbestand_artikel FOREIGN KEY (artikel_id) REFERENCES artikel(id),
    CONSTRAINT fk_lagerbestand_lagerort FOREIGN KEY (lagerort_id) REFERENCES lagerort(id)
);

CREATE TABLE IF NOT EXISTS lagerbewegung (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    artikel_id BIGINT NOT NULL,
    von_lagerort_id BIGINT,
    nach_lagerort_id BIGINT,
    typ VARCHAR(30) NOT NULL,
    menge DECIMAL(19,4) NOT NULL,
    grund VARCHAR(255),
    referenz VARCHAR(120),
    verantwortlicher VARCHAR(120),
    erstellt_am TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_lagerbewegung_artikel FOREIGN KEY (artikel_id) REFERENCES artikel(id),
    CONSTRAINT fk_lagerbewegung_von_lagerort FOREIGN KEY (von_lagerort_id) REFERENCES lagerort(id),
    CONSTRAINT fk_lagerbewegung_nach_lagerort FOREIGN KEY (nach_lagerort_id) REFERENCES lagerort(id)
);

CREATE INDEX IF NOT EXISTS idx_lagerbestand_artikel ON lagerbestand(artikel_id);
CREATE INDEX IF NOT EXISTS idx_lagerbewegung_artikel ON lagerbewegung(artikel_id);
CREATE INDEX IF NOT EXISTS idx_lagerbewegung_erstellt_am ON lagerbewegung(erstellt_am);

INSERT INTO lagerort (code, name, regal, fach, aktiv)
SELECT 'HL-DEFAULT', 'Hauptlager', 'HL', 'DEFAULT', TRUE
WHERE NOT EXISTS (SELECT 1 FROM lagerort WHERE code = 'HL-DEFAULT');
