-- Track which frontend user assigned an Eingangsrechnung to a project/Kostenstelle
ALTER TABLE lieferant_dokument_projekt_anteil
    ADD COLUMN zugeordnet_von_user_id BIGINT NULL,
    ADD CONSTRAINT fk_projekt_anteil_zugeordnet_von
        FOREIGN KEY (zugeordnet_von_user_id)
        REFERENCES frontend_user_profile(id)
        ON DELETE SET NULL;
