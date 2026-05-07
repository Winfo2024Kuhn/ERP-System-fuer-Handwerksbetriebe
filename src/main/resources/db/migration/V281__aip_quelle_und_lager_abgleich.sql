-- ═══════════════════════════════════════════════════════════════
-- V237: artikel_in_projekt bekommt eigenen Workflow-Zustand
-- ═══════════════════════════════════════════════════════════════
-- Hintergrund (Stufe A2, siehe .claude/plans/a2-aip-kalkulation-only.md):
-- `artikel_in_projekt` wird auf seine eigentliche Rolle als reine
-- Bedarfs-/Kalkulationsposition reduziert. Der Bestellvorgang selbst
-- lebt seit V236 auf `bestellung` / `bestellposition`.
--
-- Diese Migration ist **additiv**: sie fuegt die neuen Workflow-Felder
-- hinzu und backfillt aus den alten Bestell-Flags. Das Droppen der
-- alten Spalten (`bestellt`, `bestellt_am`, `exportiert_am`, `lieferant_id`)
-- passiert in einer spaeteren Migration (V238), wenn alle Konsumenten
-- auf das neue Modell umgestellt sind.
--
-- Neue Felder:
--   - `quelle`                 (Enum: OFFEN|AUS_LAGER|IN_ANFRAGE|BESTELLT)
--   - `lager_abgleich_am`      Zeitpunkt des Lager-Haken durch den Chef
--   - `lager_abgleich_durch`   GoBD-Snapshot des Chef-Namens

-- ───────────────────────────────────────────────────────────────
-- 1) Spalten idempotent anlegen (MySQL 8 kennt kein IF NOT EXISTS
--    fuer ADD COLUMN im <= 8.0.28). Wir pruefen information_schema.
-- ───────────────────────────────────────────────────────────────
SET @dbname = DATABASE();

SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = 'artikel_in_projekt'
       AND COLUMN_NAME = 'quelle') = 0,
    'ALTER TABLE artikel_in_projekt ADD COLUMN quelle VARCHAR(30) NOT NULL DEFAULT ''OFFEN''',
    'SELECT 1'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = 'artikel_in_projekt'
       AND COLUMN_NAME = 'lager_abgleich_am') = 0,
    'ALTER TABLE artikel_in_projekt ADD COLUMN lager_abgleich_am DATETIME NULL',
    'SELECT 1'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = 'artikel_in_projekt'
       AND COLUMN_NAME = 'lager_abgleich_durch') = 0,
    'ALTER TABLE artikel_in_projekt ADD COLUMN lager_abgleich_durch VARCHAR(255) NULL',
    'SELECT 1'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ───────────────────────────────────────────────────────────────
-- 2) Backfill: Workflow-Zustand aus Alt-Flags ableiten.
--    `bestellt=TRUE` → quelle = BESTELLT; sonst OFFEN.
--    Idempotent: nur auf Zeilen, die noch beim Default stehen.
-- ───────────────────────────────────────────────────────────────
UPDATE artikel_in_projekt
   SET quelle = 'BESTELLT'
 WHERE bestellt = TRUE
   AND quelle   = 'OFFEN';
