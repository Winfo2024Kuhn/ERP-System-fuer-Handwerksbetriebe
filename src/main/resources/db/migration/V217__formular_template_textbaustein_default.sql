-- Speichert pro Formular-Vorlage und Dokumenttyp die Default-Textbausteine
-- die beim Anlegen oder Umwandeln eines Dokuments automatisch
-- vor (position=VOR) oder nach (position=NACH) den Leistungen eingefuegt werden.
-- Mehrere Bausteine pro (template,dokumenttyp,position) sind erlaubt; sort_order steuert die Reihenfolge.
--
-- Idempotent: Tabelle, FK und Indizes werden nur erstellt, wenn sie noch nicht existieren.

-- 1) Tabelle anlegen (CREATE TABLE IF NOT EXISTS ist in MySQL 8.0 unterstuetzt)
CREATE TABLE IF NOT EXISTS formular_template_textbaustein_default (
    id BIGINT NOT NULL AUTO_INCREMENT,
    template_name VARCHAR(150) NOT NULL,
    dokumenttyp VARCHAR(40) NOT NULL,
    position VARCHAR(8) NOT NULL,
    textbaustein_id BIGINT NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2) Foreign Key zu textbaustein nur anlegen, falls nicht vorhanden
SET @fk_exists = (
    SELECT COUNT(*)
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE()
      AND TABLE_NAME = 'formular_template_textbaustein_default'
      AND CONSTRAINT_NAME = 'fk_fttd_textbaustein'
      AND CONSTRAINT_TYPE = 'FOREIGN KEY'
);
SET @add_fk = IF(@fk_exists = 0,
    'ALTER TABLE formular_template_textbaustein_default
        ADD CONSTRAINT fk_fttd_textbaustein
        FOREIGN KEY (textbaustein_id) REFERENCES textbaustein(id) ON DELETE CASCADE',
    'SELECT 1'
);
PREPARE stmt_fk FROM @add_fk;
EXECUTE stmt_fk;
DEALLOCATE PREPARE stmt_fk;

-- 3) Lookup-Index nur anlegen, falls nicht vorhanden
SET @idx_lookup_exists = (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'formular_template_textbaustein_default'
      AND INDEX_NAME = 'idx_fttd_lookup'
);
SET @add_idx_lookup = IF(@idx_lookup_exists = 0,
    'CREATE INDEX idx_fttd_lookup
        ON formular_template_textbaustein_default (template_name, dokumenttyp, position, sort_order)',
    'SELECT 1'
);
PREPARE stmt_idx_lookup FROM @add_idx_lookup;
EXECUTE stmt_idx_lookup;
DEALLOCATE PREPARE stmt_idx_lookup;

-- 4) Textbaustein-Index nur anlegen, falls nicht vorhanden
SET @idx_tb_exists = (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'formular_template_textbaustein_default'
      AND INDEX_NAME = 'idx_fttd_textbaustein'
);
SET @add_idx_tb = IF(@idx_tb_exists = 0,
    'CREATE INDEX idx_fttd_textbaustein
        ON formular_template_textbaustein_default (textbaustein_id)',
    'SELECT 1'
);
PREPARE stmt_idx_tb FROM @add_idx_tb;
EXECUTE stmt_idx_tb;
DEALLOCATE PREPARE stmt_idx_tb;
