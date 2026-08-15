-- Artikel bekommen Datei-Anhaenge: ein Vorschaubild und beliebig viele
-- Zusatzdokumente (Zulassungen, Zeichnungen, Datenblaetter, Montageanleitungen,
-- Sonstiges).
--
-- Das Vorschaubild ist bewusst kein eigenes Feld an artikel, sondern ein
-- artikel_dokument mit typ = VORSCHAUBILD - dieselbe Ablage-Mechanik wie fuer
-- alle anderen Dokumenttypen. Dass es je Artikel hoechstens eines gibt, prueft
-- der Service beim Speichern; die Datenbank erzwingt hier absichtlich KEINEN
-- Unique-Index auf (artikel_id, typ), nur einen normalen Suchindex.
--
-- typ ist eine native ENUM-Spalte, nicht VARCHAR: Hibernate 6.x mit
-- MySQL-Dialekt mappt @Enumerated(EnumType.STRING) auf ENUM(...) und
-- ddl-auto=validate schlaegt sonst beim Start fehl (analog kunde.anrede,
-- siehe V291).
--
-- Idempotent: CREATE TABLE IF NOT EXISTS, Index ueber information_schema
-- abgesichert.

CREATE TABLE IF NOT EXISTS artikel_dokument (
    id BIGINT NOT NULL AUTO_INCREMENT,
    artikel_id BIGINT NOT NULL,
    original_dateiname VARCHAR(255) NOT NULL,
    gespeicherter_dateiname VARCHAR(255) NOT NULL,
    typ ENUM('VORSCHAUBILD','ZULASSUNG','ZEICHNUNG','DATENBLATT','MONTAGEANLEITUNG','SONSTIGES') NOT NULL,
    beschreibung VARCHAR(1000) NULL,
    erstellt_am DATETIME(6) NOT NULL,
    mitarbeiter_id BIGINT NULL,
    dateigroesse_bytes BIGINT NULL,
    sortierung INT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_artikel_dokument_artikel FOREIGN KEY (artikel_id)
        REFERENCES artikel(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET @idx := (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE()
    AND table_name = 'artikel_dokument' AND index_name = 'idx_artikel_dokument_artikel_typ');
SET @sql := IF(@idx = 0,
    'CREATE INDEX idx_artikel_dokument_artikel_typ ON artikel_dokument (artikel_id, typ)',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
