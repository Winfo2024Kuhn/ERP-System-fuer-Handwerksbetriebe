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

-- Spalte: durchschnittspreis_netto
SET @col = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'artikel'
              AND COLUMN_NAME = 'durchschnittspreis_netto');
SET @sql = IF(@col = 0,
              'ALTER TABLE artikel ADD COLUMN durchschnittspreis_netto DECIMAL(12,4) NULL COMMENT ''Gewichteter Durchschnittspreis in €/kg (normiert wie Matching-Agent-Preise)''',
              'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Spalte: durchschnittspreis_menge
SET @col = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'artikel'
              AND COLUMN_NAME = 'durchschnittspreis_menge');
SET @sql = IF(@col = 0,
              'ALTER TABLE artikel ADD COLUMN durchschnittspreis_menge DECIMAL(18,3) NULL DEFAULT 0 COMMENT ''Summe aller bisher eingeflossenen Mengen in kg (Gewichtungsbasis)''',
              'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Spalte: durchschnittspreis_aktualisiert_am
SET @col = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'artikel'
              AND COLUMN_NAME = 'durchschnittspreis_aktualisiert_am');
SET @sql = IF(@col = 0,
              'ALTER TABLE artikel ADD COLUMN durchschnittspreis_aktualisiert_am DATETIME(6) NULL COMMENT ''Zeitpunkt des letzten Durchschnittspreis-Updates''',
              'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Index: idx_artikel_durchschnittspreis_aktualisiert
SET @idx = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'artikel'
              AND INDEX_NAME = 'idx_artikel_durchschnittspreis_aktualisiert');
SET @sql = IF(@idx = 0,
              'ALTER TABLE artikel ADD INDEX idx_artikel_durchschnittspreis_aktualisiert (durchschnittspreis_aktualisiert_am)',
              'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
