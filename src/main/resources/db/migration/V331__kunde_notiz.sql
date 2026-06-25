-- Notizen je Kunde (Feature "Kunden-Notizen", analog lieferant_notiz).
--
-- Eine freie Textnotiz, die ein Mitarbeiter zu einem Kunden hinterlegen kann
-- (Telefonnotizen, Hinweise, Absprachen). Nur Schema, keine personenbezogenen Daten.
-- Idempotent: Mehrfach-Ausfuehrung unschaedlich (CREATE TABLE nur wenn nicht vorhanden).

SET @tbl_exists := (SELECT COUNT(*) FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'kunde_notiz');
SET @sql := IF(@tbl_exists = 0,
    'CREATE TABLE kunde_notiz (
        id BIGINT NOT NULL AUTO_INCREMENT,
        kunde_id BIGINT NOT NULL,
        text TEXT NOT NULL,
        erstellt_am DATETIME(6) NOT NULL,
        CONSTRAINT pk_kunde_notiz PRIMARY KEY (id),
        CONSTRAINT fk_kunde_notiz_kunde FOREIGN KEY (kunde_id) REFERENCES kunde(id) ON DELETE CASCADE
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Index fuer das haeufige "Notizen eines Kunden, neueste zuerst".
SET @idx_exists := (SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'kunde_notiz'
      AND index_name = 'idx_kunde_notiz_kunde');
SET @sql := IF(@idx_exists = 0,
    'CREATE INDEX idx_kunde_notiz_kunde ON kunde_notiz (kunde_id, erstellt_am)',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
