-- ═══════════════════════════════════════════════════════════════
-- Feature B Teil 2: Preis-Historie pro Lieferanten-Artikel
-- ═══════════════════════════════════════════════════════════════
-- Loggt jede Preisaenderung an einem Lieferanten-Artikel (Rechnung,
-- Angebot, Katalog, Manuell, Vorschlag) — unabhaengig von der
-- Verrechnungseinheit (KG, Meter, Stueck, Quadratmeter).
--
-- Die Einheit wird mitgeschrieben, damit der Preis semantisch korrekt
-- bleibt (Stahltraeger €/kg, Stahlrohre €/m, Schrauben €/Stk, ...).
-- Der gewichtete Durchschnittspreis in artikel.durchschnittspreis_netto
-- wird nur fuer Rechnungs-Eintraege mit einheit='KILOGRAMM' aktualisiert.

CREATE TABLE IF NOT EXISTS artikel_preis_historie (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    artikel_id          BIGINT        NOT NULL,
    lieferant_id        BIGINT        NULL,
    preis               DECIMAL(12,4) NOT NULL,
    menge               DECIMAL(18,3) NULL,
    einheit             VARCHAR(32)   NOT NULL,
    quelle              VARCHAR(32)   NOT NULL,
    externe_nummer      VARCHAR(255)  NULL,
    beleg_referenz      VARCHAR(255)  NULL,
    erfasst_am          DATETIME(6)   NOT NULL,
    bemerkung           VARCHAR(500)  NULL,
    CONSTRAINT fk_aph_artikel    FOREIGN KEY (artikel_id)   REFERENCES artikel(id)     ON DELETE CASCADE,
    CONSTRAINT fk_aph_lieferant  FOREIGN KEY (lieferant_id) REFERENCES lieferanten(id) ON DELETE SET NULL,
    INDEX idx_aph_artikel_erfasst (artikel_id, erfasst_am),
    INDEX idx_aph_quelle          (quelle),
    INDEX idx_aph_einheit         (einheit)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
