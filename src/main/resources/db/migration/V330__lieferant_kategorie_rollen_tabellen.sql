-- Rollen-Infrastruktur fuer Lieferanten + Kategorien (Feature "Lieferanten-Rollen").
--
-- Ein Lieferant kann kuenftig MEHRERE Rollen haben (Stahlhandel, Schrauben,
-- Verzinkerei, Lackierer, Fertigteile, Aluminium, Edelstahl, Werkzeug, IT, Sonstiger)
-- statt eines einzelnen lieferanten_typ-Strings. Kategorien koennen "typische Rollen"
-- hinterlegen, damit beim Preis-Eintragen am Artikel die passenden Lieferanten
-- vorgeschlagen werden.
--
-- Diese Migration enthaelt NUR Schema (keine personenbezogenen Daten). Die konkrete
-- Rollen-Zuordnung realer Lieferanten erfolgt ueber ein separates, NICHT versioniertes
-- Ops-Skript (db-ops/), weil es echte Firmen-Datensaetze referenziert und nicht ins
-- Open-Source-Repo gehoert.
--
-- Enum-Spalten als native MySQL-ENUM (Hibernate-6-Regel: @Enumerated(STRING) -> ENUM).
-- Idempotent: Mehrfach-Ausfuehrung unschaedlich.

-- ---------------------------------------------------------------------------
-- lieferanten_rollen: N Rollen je Lieferant
-- ---------------------------------------------------------------------------
SET @tbl_exists := (SELECT COUNT(*) FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'lieferanten_rollen');
SET @sql := IF(@tbl_exists = 0,
    'CREATE TABLE lieferanten_rollen (
        lieferant_id BIGINT NOT NULL,
        rolle ENUM(''STAHLHANDEL'',''SCHRAUBEN_NORMTEILE'',''BESCHICHTUNG_VERZINKEN'',''LACKIERER'',''FERTIGTEILE_ZUKAUF'',''ALUMINIUM_NE'',''EDELSTAHL'',''WERKZEUG_VERBRAUCH'',''IT'',''SONSTIGER'') NOT NULL,
        CONSTRAINT pk_lieferanten_rollen PRIMARY KEY (lieferant_id, rolle),
        CONSTRAINT fk_lieferanten_rollen_lieferant FOREIGN KEY (lieferant_id) REFERENCES lieferanten(id) ON DELETE CASCADE
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------------
-- kategorie_rollen: typische Liefer-Rollen je Kategorie (kategorie.id ist INT)
-- ---------------------------------------------------------------------------
SET @tbl_exists := (SELECT COUNT(*) FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'kategorie_rollen');
SET @sql := IF(@tbl_exists = 0,
    'CREATE TABLE kategorie_rollen (
        kategorie_id INT NOT NULL,
        rolle ENUM(''STAHLHANDEL'',''SCHRAUBEN_NORMTEILE'',''BESCHICHTUNG_VERZINKEN'',''LACKIERER'',''FERTIGTEILE_ZUKAUF'',''ALUMINIUM_NE'',''EDELSTAHL'',''WERKZEUG_VERBRAUCH'',''IT'',''SONSTIGER'') NOT NULL,
        CONSTRAINT pk_kategorie_rollen PRIMARY KEY (kategorie_id, rolle),
        CONSTRAINT fk_kategorie_rollen_kategorie FOREIGN KEY (kategorie_id) REFERENCES kategorie(id) ON DELETE CASCADE
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
