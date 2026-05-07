-- ═══════════════════════════════════════════════════════════════
-- V283: artikel_in_projekt — Spalte `lieferant_id` droppen
-- ═══════════════════════════════════════════════════════════════
-- Ausgangslage (aus `SHOW CREATE TABLE artikel_in_projekt`):
--   - Auf manchen Umgebungen existieren historische FKs auf
--     `lieferant_id` (aus alter Hibernate-ddl-auto=update-Zeit):
--       * FKcntoko2hycpioejmu3pjx9wv9 → lieferanten
--       * FKrl0duw5r0pksbpj79kdpjabql → lieferanten_artikel_preise
--         (composite auf artikel_id, lieferant_id)
--   - Auf anderen Umgebungen sind diese FKs schon nicht (mehr) da.
--   - Composite-Index `FKrl0duw5r0pksbpj79kdpjabql` ist auf manchen
--     DBs der einzige Index, der `artikel_id` abdeckt — der FK auf
--     `artikel_id` (`FKbff4hugr1a1yb7wpntofif4lj`) braucht ihn
--     deshalb als Backing-Index. Direktes DROP INDEX schlägt mit
--     Error 1553 fehl ("needed in a foreign key constraint").
--
-- Vorgehen:
--   0) Alle FKs auf artikel_in_projekt droppen, die `lieferant_id`
--      einschließen (Cursor über information_schema.KEY_COLUMN_USAGE).
--   1) Single-Column-Index auf `artikel_id` anlegen, falls noch keiner
--      existiert. Dann hat der FK auf artikel_id einen eigenen
--      Backing-Index und der Composite-Index darf weg.
--   2) Composite-Index (der `lieferant_id` enthält) droppen.
--   3) Spalte `lieferant_id` droppen.
--
-- Umsetzung via Stored Procedure, damit jeder Schritt idempotent ist
-- und die Migration auch auf Umgebungen läuft, auf denen Teile schon
-- manuell bereinigt wurden (Cursor liefert leere Menge → No-Op).

DROP PROCEDURE IF EXISTS __v283_drop_aip_lieferant;

CREATE PROCEDURE __v283_drop_aip_lieferant()
BEGIN
    DECLARE v_composite_idx VARCHAR(64) DEFAULT NULL;
    DECLARE v_single_idx_exists INT DEFAULT 0;
    DECLARE v_has_col INT DEFAULT 0;
    DECLARE v_done INT DEFAULT 0;
    DECLARE v_fk_name VARCHAR(64);
    DECLARE fk_cursor CURSOR FOR
        SELECT DISTINCT CONSTRAINT_NAME
        FROM information_schema.KEY_COLUMN_USAGE
        WHERE TABLE_SCHEMA      = DATABASE()
          AND TABLE_NAME        = 'artikel_in_projekt'
          AND COLUMN_NAME       = 'lieferant_id'
          AND REFERENCED_TABLE_NAME IS NOT NULL;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_done = 1;

    -- 0) Alle Foreign Keys droppen, die `lieferant_id` einschließen (Single-Spalte
    --    auf `lieferanten` oder Composite auf `lieferanten_artikel_preise`).
    --    Diese FKs sind aus alter Hibernate-Zeit (ddl-auto=update) entstanden
    --    und blockieren sowohl das DROP INDEX als auch das DROP COLUMN.
    OPEN fk_cursor;
    fk_loop: LOOP
        FETCH fk_cursor INTO v_fk_name;
        IF v_done THEN LEAVE fk_loop; END IF;
        SET @sql = CONCAT('ALTER TABLE artikel_in_projekt DROP FOREIGN KEY `', v_fk_name, '`');
        PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END LOOP;
    CLOSE fk_cursor;

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

CALL __v283_drop_aip_lieferant();
DROP PROCEDURE __v283_drop_aip_lieferant;
