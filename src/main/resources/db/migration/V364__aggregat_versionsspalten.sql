-- Fuegt jedem der 14 verifizierten Aggregate-Root-Entities eine version-Spalte
-- hinzu (optimistisches Sperren via @jakarta.persistence.Version). Zweites
-- Sicherheitsnetz gegen paralleles Speichern - insbesondere fuer Schreibpfade
-- ohne eigene Sperr-Oberflaeche (Mobile-App react-zeiterfassung).
--
-- Klasse -> Tabelle (siehe Implementierungsplan, Abschnitt "Aggregate-Roots
-- fuer @Version - verifizierte Liste"):
--   Projekt                     -> projekt
--   Anfrage                     -> anfrage
--   Kunde                       -> kunde
--   Lieferanten                 -> lieferanten
--   Artikel                     -> artikel    (JOINED-Vererbung: Spalte NUR auf
--                                               der Wurzeltabelle, nicht auf
--                                               artikel_werkstoffe/artikel_hilfsstoffe)
--   Mitarbeiter                 -> mitarbeiter
--   AusgangsGeschaeftsDokument  -> ausgangs_geschaeftsdokument
--   LieferantDokument           -> lieferant_dokument
--   Beleg                       -> beleg
--   Textbaustein                -> textbaustein
--   Produktkategorie            -> produktkategorie
--   Arbeitsgang                 -> arbeitsgang
--   Firmeninformation           -> firmeninformation
--   LieferantReklamation        -> lieferant_reklamation
--
-- Eine einzige Sammel-Datei statt 14 Einzelmigrationen: spring.jpa.hibernate.
-- ddl-auto=validate laeuft in Produktion - jeder Zwischenstand "Entity hat
-- @Version, Spalte fehlt noch" waere ein nicht startfaehiger Zustand. Flyway
-- spielt ohnehin alle Migrationen einer Auslieferung in einem Rutsch ein,
-- 14 Dateien haetten also nur 13 nutzlose Zwischenstaende erzeugt. Der Bestand
-- kennt Sammel-Migrationen bereits (z.B. V254 mit mehreren ALTER-TABLE-Bloecken).
--
-- Kind-Entitaeten (Positionen/Bloecke, z.B. ArtikelInProjekt, BelegPosition,
-- LieferantDokumentProjektAnteil) bekommen bewusst KEINE eigene version-Spalte -
-- gespeichert wird immer ueber den Wurzel-Aggregat, dessen Version als Waechter
-- fuer den gesamten Aggregatsbaum reicht.
--
-- NOT NULL DEFAULT 0: bestehende Zeilen bekommen beim ALTER TABLE einen
-- gueltigen Startwert, jede folgende Aenderung ueber den jeweiligen
-- Wurzel-Service zaehlt danach automatisch hoch (Hibernate setzt @Version-
-- Spalten selbst, nie manuell).
--
-- Idempotent: jeder Block prueft per information_schema, ob die Spalte schon
-- existiert, bevor er sie anlegt (Vorlage: V334__projekt_abgeschlossen_manuell.sql).

-- projekt.version
SET @col_exists := (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'projekt' AND column_name = 'version');
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE projekt ADD COLUMN version BIGINT NOT NULL DEFAULT 0',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- anfrage.version
SET @col_exists := (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'anfrage' AND column_name = 'version');
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE anfrage ADD COLUMN version BIGINT NOT NULL DEFAULT 0',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- kunde.version
SET @col_exists := (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'kunde' AND column_name = 'version');
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE kunde ADD COLUMN version BIGINT NOT NULL DEFAULT 0',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- lieferanten.version
SET @col_exists := (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'lieferanten' AND column_name = 'version');
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE lieferanten ADD COLUMN version BIGINT NOT NULL DEFAULT 0',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- artikel.version (JOINED-Wurzeltabelle - NICHT auf artikel_werkstoffe/artikel_hilfsstoffe)
SET @col_exists := (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'artikel' AND column_name = 'version');
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE artikel ADD COLUMN version BIGINT NOT NULL DEFAULT 0',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- mitarbeiter.version
SET @col_exists := (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'mitarbeiter' AND column_name = 'version');
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE mitarbeiter ADD COLUMN version BIGINT NOT NULL DEFAULT 0',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ausgangs_geschaeftsdokument.version
SET @col_exists := (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ausgangs_geschaeftsdokument' AND column_name = 'version');
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE ausgangs_geschaeftsdokument ADD COLUMN version BIGINT NOT NULL DEFAULT 0',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- lieferant_dokument.version
SET @col_exists := (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'lieferant_dokument' AND column_name = 'version');
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE lieferant_dokument ADD COLUMN version BIGINT NOT NULL DEFAULT 0',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- beleg.version
SET @col_exists := (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'beleg' AND column_name = 'version');
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE beleg ADD COLUMN version BIGINT NOT NULL DEFAULT 0',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- textbaustein.version
SET @col_exists := (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'textbaustein' AND column_name = 'version');
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE textbaustein ADD COLUMN version BIGINT NOT NULL DEFAULT 0',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- produktkategorie.version
SET @col_exists := (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'produktkategorie' AND column_name = 'version');
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE produktkategorie ADD COLUMN version BIGINT NOT NULL DEFAULT 0',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- arbeitsgang.version
SET @col_exists := (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'arbeitsgang' AND column_name = 'version');
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE arbeitsgang ADD COLUMN version BIGINT NOT NULL DEFAULT 0',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- firmeninformation.version
SET @col_exists := (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'firmeninformation' AND column_name = 'version');
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE firmeninformation ADD COLUMN version BIGINT NOT NULL DEFAULT 0',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- lieferant_reklamation.version
SET @col_exists := (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'lieferant_reklamation' AND column_name = 'version');
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE lieferant_reklamation ADD COLUMN version BIGINT NOT NULL DEFAULT 0',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
