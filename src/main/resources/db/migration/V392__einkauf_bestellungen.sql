CREATE TABLE IF NOT EXISTS einkauf_bestellung (
 id BIGINT NOT NULL AUTO_INCREMENT, version BIGINT NOT NULL DEFAULT 0, nummer VARCHAR(32) NOT NULL,
 lieferant_id BIGINT NOT NULL, angebotsversion_id BIGINT NULL, anfrage_revision_id BIGINT NULL,
 empfaenger_snapshot JSON NOT NULL,
 status ENUM('ENTWURF','BESTELLT','TEILGELIEFERT','GELIEFERT','STORNIERT') NOT NULL DEFAULT 'ENTWURF',
 lieferanten_status ENUM('AUSSTEHEND','BESTAETIGT','ABWEICHUNG') NOT NULL DEFAULT 'AUSSTEHEND',
 idempotenz_key VARCHAR(36) NOT NULL, payload_hash VARCHAR(64) NOT NULL, angelegt_von BIGINT NOT NULL,
 angelegt_am TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), PRIMARY KEY(id),
 CONSTRAINT uk_einkauf_bestellung_nummer UNIQUE(nummer), CONSTRAINT uk_einkauf_bestellung_idempotenz UNIQUE(idempotenz_key),
 CONSTRAINT fk_bestellung_angebot_version FOREIGN KEY(angebotsversion_id) REFERENCES einkauf_angebot_version(id),
 CONSTRAINT fk_bestellung_anfrage_revision FOREIGN KEY(anfrage_revision_id) REFERENCES einkaufsanfrage_revision(id),
 INDEX idx_bestellung_lieferant_status(lieferant_id,status,id)
);
CREATE TABLE IF NOT EXISTS einkauf_bestellung_revision (
 id BIGINT NOT NULL AUTO_INCREMENT, bestellung_id BIGINT NOT NULL, nummer INT NOT NULL, version BIGINT NOT NULL DEFAULT 0,
 snapshot JSON NOT NULL, sha256 VARCHAR(64) NOT NULL, geaendert_von BIGINT NOT NULL,
 geaendert_am TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), versand_id BIGINT NULL,
 PRIMARY KEY(id), CONSTRAINT uk_bestellung_revision_nummer UNIQUE(bestellung_id,nummer),
 CONSTRAINT fk_bestellung_revision_bestellung FOREIGN KEY(bestellung_id) REFERENCES einkauf_bestellung(id),
 INDEX idx_bestellung_revision_versand(versand_id)
);
CREATE TABLE IF NOT EXISTS einkauf_bestellung_position (
 id BIGINT NOT NULL AUTO_INCREMENT, revision_id BIGINT NOT NULL, position_snapshot JSON NOT NULL,
 menge DECIMAL(19,6) NOT NULL, netto_einzelpreis DECIMAL(19,6) NULL, waehrung VARCHAR(3) NOT NULL,
 kosten_snapshot JSON NOT NULL, liefergruppe_snapshot JSON NOT NULL, PRIMARY KEY(id),
 CONSTRAINT fk_bestellung_position_revision FOREIGN KEY(revision_id) REFERENCES einkauf_bestellung_revision(id),
 INDEX idx_bestellung_position_revision(revision_id)
);
CREATE TABLE IF NOT EXISTS einkauf_bestellung_herkunft (
 id BIGINT NOT NULL AUTO_INCREMENT, bestell_position_id BIGINT NOT NULL, bedarf_id BIGINT NOT NULL,
 bedarf_version BIGINT NOT NULL, quell_version BIGINT NOT NULL, reservierungs_version BIGINT NOT NULL, menge DECIMAL(19,6) NOT NULL, PRIMARY KEY(id),
 CONSTRAINT fk_bestellung_herkunft_position FOREIGN KEY(bestell_position_id) REFERENCES einkauf_bestellung_position(id),
 CONSTRAINT fk_bestellung_herkunft_bedarf FOREIGN KEY(bedarf_id) REFERENCES einkauf_bedarf(id),
 INDEX idx_bestellung_herkunft_bedarf(bedarf_id)
);
