-- ═══════════════════════════════════════════════════════════════
-- Feature B: Gleitender Durchschnittspreis pro Artikel
-- ═══════════════════════════════════════════════════════════════
-- Haelt pro Artikel einen gewichteten Durchschnittspreis auf Basis
-- tatsaechlich bezahlter Rechnungspreise. Wird vom Matching-Agent
-- (ArtikelMatchingToolService.updateArtikelPreis) nach erfolgreichem
-- Preis-Update fortlaufend mitgefuehrt.
--
-- Formel (gewichteter Durchschnitt):
--   p_neu_ges = (p_alt * m_alt + p_neu * m_neu) / (m_alt + m_neu)
--   m_neu_ges = m_alt + m_neu
--
-- Initialer Backfill via POST /api/admin/artikel/durchschnittspreis/backfill.

ALTER TABLE artikel
    ADD COLUMN IF NOT EXISTS durchschnittspreis_netto DECIMAL(12,4) NULL
        COMMENT 'Gewichteter Durchschnittspreis in €/kg (normiert wie Matching-Agent-Preise)';

ALTER TABLE artikel
    ADD COLUMN IF NOT EXISTS durchschnittspreis_menge DECIMAL(18,3) NULL DEFAULT 0
        COMMENT 'Summe aller bisher eingeflossenen Mengen in kg (Gewichtungsbasis)';

ALTER TABLE artikel
    ADD COLUMN IF NOT EXISTS durchschnittspreis_aktualisiert_am DATETIME(6) NULL
        COMMENT 'Zeitpunkt des letzten Durchschnittspreis-Updates';

ALTER TABLE artikel
    ADD INDEX IF NOT EXISTS idx_artikel_durchschnittspreis_aktualisiert
        (durchschnittspreis_aktualisiert_am);
