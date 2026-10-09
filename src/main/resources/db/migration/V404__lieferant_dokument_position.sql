-- Positionen eines Lieferanten-Dokuments (Angebot, AB, Lieferschein, Rechnung,
-- Gutschrift). Die KI-Dokumentanalyse bzw. ZUGFeRD/XRechnung liefert sie mit.
--
-- Wofür:
--  * Dokumentenkette: Fehlt die Angebotsnummer auf der AB, helfen gleiche
--    Positionen beim Zuordnen.
--  * Projektaufteilung: Statt Prozent oder Betrag kann der Nutzer jede Position
--    einem Projekt oder einer Kostenstelle zuordnen (projekt_id/kostenstelle_id).
--    Daraus entstehen wie bisher Zeilen in lieferant_dokument_projekt_anteil.
--
-- positions_art: WARE wird zugeordnet, NEBENKOSTEN (Fracht, Verpackung, Maut,
-- Zuschläge) und RABATT werden anteilig nach Warenwert verteilt.

CREATE TABLE IF NOT EXISTS lieferant_dokument_position (
    id BIGINT NOT NULL AUTO_INCREMENT,
    geschaeftsdokument_id BIGINT NOT NULL,
    position_nr INT NOT NULL,
    positions_art ENUM('WARE','NEBENKOSTEN','RABATT') NOT NULL DEFAULT 'WARE',
    externe_artikelnummer VARCHAR(64) NULL,
    bezeichnung VARCHAR(500) NOT NULL,
    menge DECIMAL(15,3) NULL,
    mengeneinheit VARCHAR(20) NULL,
    einzelpreis DECIMAL(15,4) NULL,
    preiseinheit VARCHAR(20) NULL,
    gesamtpreis_netto DECIMAL(15,2) NULL,
    projekt_id BIGINT NULL,
    kostenstelle_id BIGINT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_ld_position_geschaeftsdokument FOREIGN KEY (geschaeftsdokument_id)
        REFERENCES lieferant_geschaeftsdokument(id) ON DELETE CASCADE,
    CONSTRAINT fk_ld_position_projekt FOREIGN KEY (projekt_id)
        REFERENCES projekt(id) ON DELETE SET NULL,
    CONSTRAINT fk_ld_position_kostenstelle FOREIGN KEY (kostenstelle_id)
        REFERENCES firma_kostenstelle(id) ON DELETE SET NULL,
    INDEX idx_ld_position_geschaeftsdokument (geschaeftsdokument_id, position_nr)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
