-- ═══════════════════════════════════════════════════════════════
-- V239: artikel_in_projekt — Spalte `lieferant_id` droppen
-- ═══════════════════════════════════════════════════════════════
-- Ausgangslage (aus `SHOW CREATE TABLE artikel_in_projekt`):
--   - KEIN Foreign Key auf `lieferant_id`. FKs existieren nur auf
--     projekt_id, artikel_id, kategorie_id.
--   - Composite-Index `FKrl0duw5r0pksbpj79kdpjabql` auf
--     (artikel_id, lieferant_id). Dieser Index ist aktuell der
--     einzige Index, der `artikel_id` abdeckt — der FK auf
--     `artikel_id` (`FKbff4hugr1a1yb7wpntofif4lj`) braucht ihn
--     deshalb als Backing-Index. Daher schlägt ein direktes
--     DROP INDEX mit Error 1553 fehl ("needed in a foreign key
--     constraint").
--
-- Vorgehen:
--   1) Single-Column-Index auf `artikel_id` anlegen, falls noch keiner
--      existiert. Dann hat der FK auf artikel_id einen eigenen
--      Backing-Index und der Composite-Index darf weg.
--   2) Composite-Index (der `lieferant_id` enthält) droppen.
--   3) Spalte `lieferant_id` droppen.
--
-- Umsetzung via Stored Procedure, damit jeder Schritt idempotent ist
-- und die Migration auch auf Umgebungen läuft, auf denen Teile schon
-- manuell bereinigt wurden.

DROP PROCEDURE IF EXISTS __v239_drop_aip_lieferant;

CREATE PROCEDURE __v239_drop_aip_lieferant()
BEGIN
    DECLARE v_composite_idx VARCHAR(64) DEFAULT NULL;
    DECLARE v_single_idx_exists INT DEFAULT 0;
    DECLARE v_has_col INT DEFAULT 0;

    -- 1) Composite-Index finden, der (artikel_id, lieferant_id) abdeckt.
    SELECT MAX(s1.INDEX_NAME) INTO v_composite_idx
    FROM information_schema.STATISTICS s1
    JOIN information_schema.STATISTICS s2
      ON s1.TABLE_SCHEMA = s2.TABLE_SCHEMA
     AND s1.TABLE_NAME   = s2.TABLE_NAME
     AND s1.INDEX_NAME   = s2.INDEX_NAME
    WHERE s1.TABLE_SCHEMA = DATABASE()
      AND s1.TABLE_NAME   = 'artikel_in_projekt'
      AND s1.COLUMN_NAME  = 'lieferant_id'
      AND s2.COLUMN_NAME  = 'artikel_id'
      AND s1.INDEX_NAME  <> 'PRIMARY';

    -- 2) Prüfen, ob bereits ein reiner Single-Column-Index auf artikel_id existiert.
    --    "Single-Column" = es gibt keinen Eintrag mit SEQ_IN_INDEX > 1 in dem Index.
    SELECT COUNT(*) INTO v_single_idx_exists
    FROM information_schema.STATISTICS s
    WHERE s.TABLE_SCHEMA = DATABASE()
      AND s.TABLE_NAME   = 'artikel_in_projekt'
      AND s.COLUMN_NAME  = 'artikel_id'
      AND s.SEQ_IN_INDEX = 1
      AND s.INDEX_NAME  <> 'PRIMARY'
      AND NOT EXISTS (
            SELECT 1 FROM information_schema.STATISTICS s2
            WHERE s2.TABLE_SCHEMA = s.TABLE_SCHEMA
              AND s2.TABLE_NAME   = s.TABLE_NAME
              AND s2.INDEX_NAME   = s.INDEX_NAME
              AND s2.SEQ_IN_INDEX > 1
      );

    -- 3) Single-Column-Index anlegen, damit der FK auf artikel_id einen
    --    eigenen Backing-Index bekommt.
    IF v_single_idx_exists = 0 THEN
        ALTER TABLE artikel_in_projekt ADD INDEX idx_aip_artikel (artikel_id);
    END IF;

    -- 4) Composite-Index droppen. Handler für 1091 ("already gone") als
    --    Sicherheitsnetz für idempotentes Re-Running.
    IF v_composite_idx IS NOT NULL THEN
        BEGIN
            DECLARE CONTINUE HANDLER FOR 1091 BEGIN END;
            SET @sql = CONCAT('ALTER TABLE artikel_in_projekt DROP INDEX `', v_composite_idx, '`');
            PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
        END;
    END IF;

    -- 5) Spalte droppen.
    SELECT COUNT(*) INTO v_has_col
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME   = 'artikel_in_projekt'
      AND COLUMN_NAME  = 'lieferant_id';

    IF v_has_col > 0 THEN
        ALTER TABLE artikel_in_projekt DROP COLUMN lieferant_id;
    END IF;
END;

CALL __v239_drop_aip_lieferant();
DROP PROCEDURE __v239_drop_aip_lieferant;
