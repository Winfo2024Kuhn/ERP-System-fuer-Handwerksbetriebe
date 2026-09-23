CREATE TABLE IF NOT EXISTS einkauf_lieferung (
 id BIGINT NOT NULL AUTO_INCREMENT, bestellung_id BIGINT NOT NULL, revision_id BIGINT NOT NULL,
 lieferschein_id BIGINT NOT NULL, eingang TIMESTAMP(6) NOT NULL, idempotenz_key VARCHAR(36) NOT NULL,
 akteur_id BIGINT NOT NULL, angelegt_am TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), PRIMARY KEY(id),
 CONSTRAINT uk_einkauf_lieferung_idempotenz UNIQUE(idempotenz_key),
 CONSTRAINT fk_einkauf_lieferung_bestellung FOREIGN KEY(bestellung_id) REFERENCES einkauf_bestellung(id),
 CONSTRAINT fk_einkauf_lieferung_revision FOREIGN KEY(revision_id) REFERENCES einkauf_bestellung_revision(id),
 CONSTRAINT fk_einkauf_lieferung_lieferschein FOREIGN KEY(lieferschein_id) REFERENCES lieferant_dokument(id),
 INDEX idx_einkauf_lieferung_bestellung(bestellung_id,eingang)
);
CREATE TABLE IF NOT EXISTS einkauf_lieferung_position (
 id BIGINT NOT NULL AUTO_INCREMENT, lieferung_id BIGINT NOT NULL, bestell_position_id BIGINT NOT NULL,
 menge DECIMAL(19,6) NOT NULL, charge VARCHAR(120) NULL, schmelznummer VARCHAR(120) NULL,
 projekt_anteile JSON NOT NULL, PRIMARY KEY(id),
 CONSTRAINT fk_einkauf_lieferung_position_lieferung FOREIGN KEY(lieferung_id) REFERENCES einkauf_lieferung(id),
 CONSTRAINT fk_einkauf_lieferung_position_bestellung FOREIGN KEY(bestell_position_id) REFERENCES einkauf_bestellung_position(id),
 INDEX idx_einkauf_lieferung_position(bestell_position_id)
);
CREATE TABLE IF NOT EXISTS einkauf_charge (
 id BIGINT NOT NULL AUTO_INCREMENT, lieferung_position_id BIGINT NOT NULL, kennung VARCHAR(120) NOT NULL,
 schmelznummer VARCHAR(120) NULL, erfasst_am TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), PRIMARY KEY(id),
 CONSTRAINT fk_einkauf_charge_lieferung_position FOREIGN KEY(lieferung_position_id) REFERENCES einkauf_lieferung_position(id),
 INDEX idx_einkauf_charge_kennung(kennung)
);
CREATE TABLE IF NOT EXISTS einkauf_bestellbestaetigung (
 id BIGINT NOT NULL AUTO_INCREMENT, bestellung_id BIGINT NOT NULL, dokument_id BIGINT NOT NULL,
 datum DATE NOT NULL, liefertermin DATE NULL, positionen_snapshot JSON NOT NULL, abweichung BOOLEAN NOT NULL,
 akteur_id BIGINT NOT NULL, erfasst_am TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), PRIMARY KEY(id),
 CONSTRAINT fk_einkauf_ab_bestellung FOREIGN KEY(bestellung_id) REFERENCES einkauf_bestellung(id),
 CONSTRAINT fk_einkauf_ab_dokument FOREIGN KEY(dokument_id) REFERENCES lieferant_dokument(id),
 INDEX idx_einkauf_ab_bestellung(bestellung_id,datum)
);
ALTER TABLE lieferant_dokument ADD COLUMN einkauf_bestellung_id BIGINT NULL;
CREATE INDEX idx_lieferant_dokument_bestellung ON lieferant_dokument(einkauf_bestellung_id);
