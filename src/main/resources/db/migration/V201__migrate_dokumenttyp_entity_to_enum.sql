-- V201: Migrate Dokumenttyp from JPA entity (table) to Java enum.
-- Columns/table may already exist (created by Hibernate ddl-auto=update),
-- so every DDL statement is guarded to be idempotent.
-- Uses SET/PREPARE/EXECUTE pattern instead of stored procedures (no DELIMITER needed).

-- ============================================================
-- 1. geschaeftsdokument: add dokumenttyp_enum, migrate data
-- ============================================================
SET @col1 = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'geschaeftsdokument' AND COLUMN_NAME = 'dokumenttyp_enum');
SET @sql1 = IF(@col1 = 0, 'ALTER TABLE geschaeftsdokument ADD COLUMN dokumenttyp_enum VARCHAR(30) NULL', 'SELECT 1');
PREPARE s1 FROM @sql1;
EXECUTE s1;
DEALLOCATE PREPARE s1;

UPDATE geschaeftsdokument g
JOIN dokumenttyp d ON g.dokumenttyp_id = d.id
SET g.dokumenttyp_enum = CASE d.name
    WHEN 'Angebot'              THEN 'ANGEBOT'
    WHEN 'Auftragsbestätigung'  THEN 'AUFTRAGSBESTAETIGUNG'
    WHEN 'Teilrechnung'         THEN 'TEILRECHNUNG'
    WHEN 'Abschlagsrechnung'    THEN 'ABSCHLAGSRECHNUNG'
    WHEN 'Schlussrechnung'      THEN 'SCHLUSSRECHNUNG'
    WHEN 'Zahlungserinnerung'   THEN 'ZAHLUNGSERINNERUNG'
    WHEN '1. Mahnung'           THEN 'ERSTE_MAHNUNG'
    WHEN '2. Mahnung'           THEN 'ZWEITE_MAHNUNG'
    WHEN 'Stornorechnung'       THEN 'STORNORECHNUNG'
    WHEN 'Rechnung'             THEN 'SCHLUSSRECHNUNG'
    WHEN 'Mahnung'              THEN 'ERSTE_MAHNUNG'
    WHEN 'Storno'               THEN 'STORNORECHNUNG'
    WHEN 'Gutschrift'           THEN 'STORNORECHNUNG'
    ELSE 'ANGEBOT'
END
WHERE g.dokumenttyp_id IS NOT NULL AND g.dokumenttyp_enum IS NULL;

UPDATE geschaeftsdokument SET dokumenttyp_enum = 'ANGEBOT' WHERE dokumenttyp_enum IS NULL;

ALTER TABLE geschaeftsdokument MODIFY COLUMN dokumenttyp_enum VARCHAR(30) NOT NULL;

-- ============================================================
-- 2. formular_template_assignment: add dokumenttyp_enum, migrate
-- ============================================================
SET @col2 = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'formular_template_assignment' AND COLUMN_NAME = 'dokumenttyp_enum');
SET @sql2 = IF(@col2 = 0, 'ALTER TABLE formular_template_assignment ADD COLUMN dokumenttyp_enum VARCHAR(30) NULL', 'SELECT 1');
PREPARE s2 FROM @sql2;
EXECUTE s2;
DEALLOCATE PREPARE s2;

UPDATE formular_template_assignment fta
JOIN dokumenttyp d ON fta.dokumenttyp_id = d.id
SET fta.dokumenttyp_enum = CASE d.name
    WHEN 'Angebot'              THEN 'ANGEBOT'
    WHEN 'Auftragsbestätigung'  THEN 'AUFTRAGSBESTAETIGUNG'
    WHEN 'Teilrechnung'         THEN 'TEILRECHNUNG'
    WHEN 'Abschlagsrechnung'    THEN 'ABSCHLAGSRECHNUNG'
    WHEN 'Schlussrechnung'      THEN 'SCHLUSSRECHNUNG'
    WHEN 'Zahlungserinnerung'   THEN 'ZAHLUNGSERINNERUNG'
    WHEN '1. Mahnung'           THEN 'ERSTE_MAHNUNG'
    WHEN '2. Mahnung'           THEN 'ZWEITE_MAHNUNG'
    WHEN 'Stornorechnung'       THEN 'STORNORECHNUNG'
    WHEN 'Rechnung'             THEN 'SCHLUSSRECHNUNG'
    WHEN 'Mahnung'              THEN 'ERSTE_MAHNUNG'
    WHEN 'Storno'               THEN 'STORNORECHNUNG'
    WHEN 'Gutschrift'           THEN 'STORNORECHNUNG'
    ELSE 'ANGEBOT'
END
WHERE fta.dokumenttyp_id IS NOT NULL AND fta.dokumenttyp_enum IS NULL;

-- Fix rows that were already migrated with invalid enum values (from previous partial runs)
UPDATE formular_template_assignment SET dokumenttyp_enum = 'SCHLUSSRECHNUNG' WHERE dokumenttyp_enum = 'RECHNUNG';
UPDATE formular_template_assignment SET dokumenttyp_enum = 'ERSTE_MAHNUNG'   WHERE dokumenttyp_enum = 'MAHNUNG';
UPDATE formular_template_assignment SET dokumenttyp_enum = 'STORNORECHNUNG'  WHERE dokumenttyp_enum = 'STORNO';
UPDATE formular_template_assignment SET dokumenttyp_enum = 'STORNORECHNUNG'  WHERE dokumenttyp_enum = 'GUTSCHRIFT';

UPDATE formular_template_assignment SET dokumenttyp_enum = 'ANGEBOT' WHERE dokumenttyp_enum IS NULL;

ALTER TABLE formular_template_assignment MODIFY COLUMN dokumenttyp_enum VARCHAR(30) NOT NULL;

-- ============================================================
-- 3. textbaustein_dokumenttyp_enum: create and migrate from old join table
-- ============================================================
CREATE TABLE IF NOT EXISTS textbaustein_dokumenttyp_enum (
    textbaustein_id BIGINT NOT NULL,
    dokumenttyp     VARCHAR(30) NOT NULL,
    CONSTRAINT fk_tb_doktyp_enum_tb FOREIGN KEY (textbaustein_id) REFERENCES textbaustein(id) ON DELETE CASCADE
);

INSERT INTO textbaustein_dokumenttyp_enum (textbaustein_id, dokumenttyp)
SELECT td.textbaustein_id,
    CASE d.name
        WHEN 'Angebot'              THEN 'ANGEBOT'
        WHEN 'Auftragsbestätigung'  THEN 'AUFTRAGSBESTAETIGUNG'
        WHEN 'Teilrechnung'         THEN 'TEILRECHNUNG'
        WHEN 'Abschlagsrechnung'    THEN 'ABSCHLAGSRECHNUNG'
        WHEN 'Schlussrechnung'      THEN 'SCHLUSSRECHNUNG'
        WHEN 'Zahlungserinnerung'   THEN 'ZAHLUNGSERINNERUNG'
        WHEN '1. Mahnung'           THEN 'ERSTE_MAHNUNG'
        WHEN '2. Mahnung'           THEN 'ZWEITE_MAHNUNG'
        WHEN 'Stornorechnung'       THEN 'STORNORECHNUNG'
        WHEN 'Rechnung'             THEN 'SCHLUSSRECHNUNG'
        WHEN 'Mahnung'              THEN 'ERSTE_MAHNUNG'
        WHEN 'Storno'               THEN 'STORNORECHNUNG'
        WHEN 'Gutschrift'           THEN 'STORNORECHNUNG'
        ELSE 'ANGEBOT'
    END
FROM textbaustein_dokumenttyp td
JOIN dokumenttyp d ON td.dokumenttyp_id = d.id
WHERE NOT EXISTS (SELECT 1 FROM textbaustein_dokumenttyp_enum);

-- Old tables/columns are intentionally NOT dropped for rollback safety.
-- Remove manually after verifying the migration: dokumenttyp table,
-- geschaeftsdokument.dokumenttyp_id, formular_template_assignment.dokumenttyp_id,
-- textbaustein_dokumenttyp table.
