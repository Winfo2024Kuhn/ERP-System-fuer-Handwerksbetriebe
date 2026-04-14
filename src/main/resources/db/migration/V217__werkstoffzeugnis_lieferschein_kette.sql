-- V214: Werkstoffzeugnis → Lieferschein Verknüpfung (1 Lieferschein : N Werkstoffzeugnisse)
-- Ein Werkstoffzeugnis enthält oft eine Lieferschein-Nr. oder Bestellnummer
-- über die es dem zugehörigen Lieferschein-Dokument zugeordnet werden kann.

ALTER TABLE werkstoffzeugnis
    ADD COLUMN lieferschein_dokument_id BIGINT NULL
        COMMENT 'Zugehöriger Lieferschein (1:N – ein Lieferschein kann mehrere Werkstoffzeugnisse haben)';

ALTER TABLE werkstoffzeugnis
    ADD CONSTRAINT fk_werks_lieferschein
        FOREIGN KEY (lieferschein_dokument_id) REFERENCES lieferant_dokument (id)
        ON DELETE SET NULL;

CREATE INDEX idx_werks_lieferschein ON werkstoffzeugnis (lieferschein_dokument_id);
