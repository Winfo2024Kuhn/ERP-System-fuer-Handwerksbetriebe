-- V152: Neue Spalten und FK-Beziehungen an bestehenden Tabellen.
-- Idempotent: Prüft ob Spalten/FKs schon existieren via SET/PREPARE/EXECUTE (kein DELIMITER nötig).

-- ============================================================
-- 1. email: Neue FK steuerberater_id
-- ============================================================
SET @col1 = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'email' AND COLUMN_NAME = 'steuerberater_id');
SET @sql1 = IF(@col1 = 0, 'ALTER TABLE email ADD COLUMN steuerberater_id BIGINT NULL', 'SELECT 1');
PREPARE s1 FROM @sql1;
EXECUTE s1;
DEALLOCATE PREPARE s1;

SET @fk1 = (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'email' AND CONSTRAINT_NAME = 'fk_email_steuerberater');
SET @sql2 = IF(@fk1 = 0, 'ALTER TABLE email ADD CONSTRAINT fk_email_steuerberater FOREIGN KEY (steuerberater_id) REFERENCES steuerberater_kontakt(id)', 'SELECT 1');
PREPARE s2 FROM @sql2;
EXECUTE s2;
DEALLOCATE PREPARE s2;

-- ============================================================
-- 2. projekt: Neues Enum-Feld projekt_art
-- ============================================================
SET @col2 = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'projekt' AND COLUMN_NAME = 'projekt_art');
SET @sql3 = IF(@col2 = 0, 'ALTER TABLE projekt ADD COLUMN projekt_art VARCHAR(255) NOT NULL DEFAULT ''PAUSCHAL''', 'SELECT 1');
PREPARE s3 FROM @sql3;
EXECUTE s3;
DEALLOCATE PREPARE s3;

-- ============================================================
-- 3. lieferant_dokument_projekt_anteil: Änderungen + Neue Spalten
-- ============================================================

-- 3a. projekt_id: nullable machen (war vorher NOT NULL)
SET @is_nullable = (SELECT IS_NULLABLE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lieferant_dokument_projekt_anteil' AND COLUMN_NAME = 'projekt_id');
SET @sql4 = IF(@is_nullable = 'NO', 'ALTER TABLE lieferant_dokument_projekt_anteil MODIFY COLUMN projekt_id BIGINT NULL', 'SELECT 1');
PREPARE s4 FROM @sql4;
EXECUTE s4;
DEALLOCATE PREPARE s4;

-- 3b. prozent: nullable machen (war vorher NOT NULL)
SET @is_nullable2 = (SELECT IS_NULLABLE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lieferant_dokument_projekt_anteil' AND COLUMN_NAME = 'prozent');
SET @sql5 = IF(@is_nullable2 = 'NO', 'ALTER TABLE lieferant_dokument_projekt_anteil MODIFY COLUMN prozent INT NULL', 'SELECT 1');
PREPARE s5 FROM @sql5;
EXECUTE s5;
DEALLOCATE PREPARE s5;

-- 3c. Neue Spalte: kostenstelle_id
SET @col3 = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lieferant_dokument_projekt_anteil' AND COLUMN_NAME = 'kostenstelle_id');
SET @sql6 = IF(@col3 = 0, 'ALTER TABLE lieferant_dokument_projekt_anteil ADD COLUMN kostenstelle_id BIGINT NULL', 'SELECT 1');
PREPARE s6 FROM @sql6;
EXECUTE s6;
DEALLOCATE PREPARE s6;

-- 3d. Neue Spalte: absoluter_betrag
SET @col4 = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lieferant_dokument_projekt_anteil' AND COLUMN_NAME = 'absoluter_betrag');
SET @sql7 = IF(@col4 = 0, 'ALTER TABLE lieferant_dokument_projekt_anteil ADD COLUMN absoluter_betrag DECIMAL(12,2) NULL', 'SELECT 1');
PREPARE s7 FROM @sql7;
EXECUTE s7;
DEALLOCATE PREPARE s7;

-- 3e. Neue Spalte: zugeordnet_am
SET @col5 = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lieferant_dokument_projekt_anteil' AND COLUMN_NAME = 'zugeordnet_am');
SET @sql8 = IF(@col5 = 0, 'ALTER TABLE lieferant_dokument_projekt_anteil ADD COLUMN zugeordnet_am DATETIME NULL', 'SELECT 1');
PREPARE s8 FROM @sql8;
EXECUTE s8;
DEALLOCATE PREPARE s8;

-- 3f. Neue Spalte: streckung_jahre
SET @col6 = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lieferant_dokument_projekt_anteil' AND COLUMN_NAME = 'streckung_jahre');
SET @sql9 = IF(@col6 = 0, 'ALTER TABLE lieferant_dokument_projekt_anteil ADD COLUMN streckung_jahre INT NOT NULL DEFAULT 1', 'SELECT 1');
PREPARE s9 FROM @sql9;
EXECUTE s9;
DEALLOCATE PREPARE s9;

-- 3g. Neue Spalte: streckung_start_jahr
SET @col7 = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lieferant_dokument_projekt_anteil' AND COLUMN_NAME = 'streckung_start_jahr');
SET @sql10 = IF(@col7 = 0, 'ALTER TABLE lieferant_dokument_projekt_anteil ADD COLUMN streckung_start_jahr INT NULL', 'SELECT 1');
PREPARE s10 FROM @sql10;
EXECUTE s10;
DEALLOCATE PREPARE s10;

-- 3h. FK für kostenstelle_id
SET @fk2 = (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lieferant_dokument_projekt_anteil' AND CONSTRAINT_NAME = 'fk_ldpa_kostenstelle');
SET @sql11 = IF(@fk2 = 0, 'ALTER TABLE lieferant_dokument_projekt_anteil ADD CONSTRAINT fk_ldpa_kostenstelle FOREIGN KEY (kostenstelle_id) REFERENCES firma_kostenstelle(id)', 'SELECT 1');
PREPARE s11 FROM @sql11;
EXECUTE s11;
DEALLOCATE PREPARE s11;
