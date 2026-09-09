-- Ausschließlich explizite Zeiträume ohne Konto; keine Änderung vorhandener Salden.
CREATE TABLE IF NOT EXISTS zeitkonto_pause (
    id BIGINT NOT NULL AUTO_INCREMENT,
    version BIGINT NOT NULL DEFAULT 0,
    mitarbeiter_id BIGINT NOT NULL,
    gueltig_von DATE NOT NULL,
    gueltig_bis DATE NULL,
    offene_pause_mitarbeiter_id BIGINT GENERATED ALWAYS AS
        (CASE WHEN gueltig_bis IS NULL THEN mitarbeiter_id ELSE NULL END) STORED,
    PRIMARY KEY (id),
    CONSTRAINT uk_zeitkonto_pause_mitarbeiter_von UNIQUE (mitarbeiter_id, gueltig_von),
    CONSTRAINT uk_zeitkonto_pause_offen UNIQUE (offene_pause_mitarbeiter_id),
    CONSTRAINT fk_zeitkonto_pause_mitarbeiter FOREIGN KEY (mitarbeiter_id) REFERENCES mitarbeiter(id),
    CONSTRAINT ck_zeitkonto_pause_zeitraum CHECK (gueltig_bis IS NULL OR gueltig_bis >= gueltig_von)
);
