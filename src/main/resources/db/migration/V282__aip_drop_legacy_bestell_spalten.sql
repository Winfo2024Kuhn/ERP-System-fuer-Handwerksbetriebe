-- ═══════════════════════════════════════════════════════════════
-- V238: artikel_in_projekt — Alt-Spalten des Bestellvorgangs droppen
-- ═══════════════════════════════════════════════════════════════
-- Hintergrund (Stufe A2, siehe .claude/plans/a2-aip-kalkulation-only.md):
-- V236 hat das neue Bestellaggregat (`bestellung` / `bestellposition`)
-- eingeführt, V237 hat `artikel_in_projekt` auf den neuen Workflow-
-- Zustand `quelle` umgestellt und die Daten rückbefüllt. Damit sind
-- die alten Bestell-Flags auf AiP überflüssig.
--
-- Diese Migration entfernt die drei Legacy-Spalten:
--   - `bestellt`        (BOOLEAN)
--   - `bestellt_am`     (DATE)
--   - `exportiert_am`   (DATETIME)
--
-- `lieferant_id` und weitere Felder (`schnitt_form`, `zeugnis_anforderung`,
-- `fixmass_mm`, …) werden in einer späteren Migration (V239/β) entfernt,
-- wenn die passenden Konsumenten umgestellt sind.
--
-- MySQL 8 / idempotent: information_schema + PREPARE/EXECUTE.

SET @dbname = DATABASE();

-- ───────────────────────────────────────────────────────────────
-- 1) bestellt
-- ───────────────────────────────────────────────────────────────
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = 'artikel_in_projekt'
       AND COLUMN_NAME = 'bestellt') > 0,
    'ALTER TABLE artikel_in_projekt DROP COLUMN bestellt',
    'SELECT 1'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ───────────────────────────────────────────────────────────────
-- 2) bestellt_am
-- ───────────────────────────────────────────────────────────────
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = 'artikel_in_projekt'
       AND COLUMN_NAME = 'bestellt_am') > 0,
    'ALTER TABLE artikel_in_projekt DROP COLUMN bestellt_am',
    'SELECT 1'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ───────────────────────────────────────────────────────────────
-- 3) exportiert_am
-- ───────────────────────────────────────────────────────────────
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = 'artikel_in_projekt'
       AND COLUMN_NAME = 'exportiert_am') > 0,
    'ALTER TABLE artikel_in_projekt DROP COLUMN exportiert_am',
    'SELECT 1'
));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
