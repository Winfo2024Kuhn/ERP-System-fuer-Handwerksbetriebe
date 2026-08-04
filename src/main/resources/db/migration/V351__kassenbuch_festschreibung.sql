-- Kassenbuch und Belege rechtssicher machen (GoBD / § 146 Abs. 4 AO).
--
-- Ausgangslage: Ein Beleg liess sich nach dem Pruefen beliebig weiter
-- veraendern oder verwerfen, ohne dass davon eine Spur blieb. Fuer
-- Kassenaufzeichnungen ist das unzulaessig -- sie muessen unveraenderbar sein,
-- und wo doch korrigiert wird, muss die urspruengliche Buchung sichtbar
-- bleiben (Storno statt stiller Ueberschreibung).
--
-- Diese Migration legt dieselbe Mechanik an, die es fuer Ausgangsrechnungen
-- schon gibt (siehe V255): eine Hash-Kette ueber alle Aenderungen, deren
-- Bruch mathematisch nachweisbar ist.
--
-- Neue Tabellen:
--   beleg_audit                -- unveraenderlicher Snapshot je Aktion, mit Hash-Kette
--   beleg_audit_chain_state    -- Singleton-Kettenkopf (id=1) + Zaehler der laufenden Nummer
--   kassenzaehlung             -- Kassensturz: gezaehltes Bargeld vs. rechnerischer Bestand
--   kassenbuch_monatsabschluss -- abgeschlossener Monat, ab dann sind die Belege gesperrt
--
-- Neue beleg-Spalten:
--   laufende_nummer            -- dauerhafte, lueckenlose Nummer, vergeben beim Monatsabschluss
--   festgeschrieben*           -- ab hier nur noch Storno statt Aenderung
--   storno_*                   -- Verweise zwischen Original und Stornobuchung
--   datei_hash                 -- SHA-256 des Belegfotos; erkennt ausgetauschte Bilder
--
-- Idempotent: alle Tabellen mit IF NOT EXISTS, alle Spalten und Indizes ueber
-- INFORMATION_SCHEMA abgesichert. Mehrfaches Ausfuehren aendert nichts.

-- =====================================================================
-- 1) Kettenkopf der Beleg-Hash-Kette
-- =====================================================================
-- Bewusst eine eigene Tabelle und nicht eine zweite Zeile in
-- audit_chain_state: dort steht ein CHECK (id = 1), das genau eine Kette
-- erzwingt. Belege bekommen deshalb ihren eigenen Kettenkopf.
--
-- last_laufende_nummer haengt mit im selben Datensatz, weil der
-- Monatsabschluss beides in einem Rutsch braucht: einen Audit-Eintrag
-- anhaengen UND die naechste Belegnummer ziehen. Ein einziger Row-Lock
-- deckt beides ab, damit zwei parallele Abschluesse weder denselben
-- previous_hash noch dieselbe Nummer bekommen koennen.
CREATE TABLE IF NOT EXISTS beleg_audit_chain_state (
    id INT NOT NULL,
    last_chain_index BIGINT NOT NULL DEFAULT -1,
    last_entry_hash CHAR(64) NULL,
    last_laufende_nummer BIGINT NOT NULL DEFAULT 0,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT chk_beleg_audit_chain_state_singleton CHECK (id = 1)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

INSERT INTO beleg_audit_chain_state (id, last_chain_index, last_entry_hash, last_laufende_nummer, updated_at)
VALUES (1, -1, NULL, 0, NOW(6))
ON DUPLICATE KEY UPDATE id = id;

-- =====================================================================
-- 2) Audit-Tabelle
-- =====================================================================
-- Wird ausschliesslich per INSERT befuellt. Kein Foreign Key auf beleg(id):
-- verschwindet ein Beleg jemals aus der Haupttabelle, muss der Pruefer
-- trotzdem noch sehen koennen, welcher Betrag mit welcher Begruendung
-- verschwunden ist.
--
-- Die Snapshot-Spalten der Java-Enums sind bewusst VARCHAR und nicht ENUM:
-- ein Archiv-Eintrag von 2026 muss auch dann noch lesbar bleiben, wenn die
-- Kategorien-Liste im Code spaeter erweitert oder umbenannt wird. Die
-- Entity setzt dafuer columnDefinition explizit, sonst wuerde
-- ddl-auto=validate eine ENUM-Spalte erwarten.
CREATE TABLE IF NOT EXISTS beleg_audit (
    id BIGINT NOT NULL AUTO_INCREMENT,
    chain_index BIGINT NULL,

    beleg_id BIGINT NULL,
    aktion VARCHAR(30) NOT NULL,

    -- Snapshot des Belegs zum Zeitpunkt der Aktion
    laufende_nummer BIGINT NULL,
    beleg_kategorie VARCHAR(40) NULL,
    beleg_status VARCHAR(20) NULL,
    beleg_datum DATE NULL,
    beleg_nummer VARCHAR(100) NULL,
    beschreibung VARCHAR(500) NULL,
    betrag_netto DECIMAL(15,2) NULL,
    betrag_brutto DECIMAL(15,2) NULL,
    mwst_satz DECIMAL(5,2) NULL,
    zahlungsart VARCHAR(40) NULL,
    aufteilungs_modus VARCHAR(20) NULL,
    betrag_firma_netto DECIMAL(15,2) NULL,
    betrag_firma_brutto DECIMAL(15,2) NULL,
    betrag_firma_mwst DECIMAL(15,2) NULL,
    lieferant_id BIGINT NULL,
    sachkonto_id BIGINT NULL,
    sachkonto_nummer VARCHAR(20) NULL,
    kostenstelle_id BIGINT NULL,
    ist_umbuchung TINYINT(1) NOT NULL DEFAULT 0,
    gespeicherter_dateiname VARCHAR(255) NULL,
    datei_hash CHAR(64) NULL,
    storno_fuer_beleg_id BIGINT NULL,
    storniert_durch_beleg_id BIGINT NULL,
    festgeschrieben TINYINT(1) NOT NULL DEFAULT 0,

    -- Bezug fuer Aktionen, die keinen einzelnen Beleg betreffen
    -- (KASSE_GEZAEHLT, MONAT_ABGESCHLOSSEN). bezug_id zeigt dann auf die
    -- kassenzaehlung bzw. den kassenbuch_monatsabschluss.
    bezug_typ VARCHAR(30) NULL,
    bezug_id BIGINT NULL,
    zusatz TEXT NULL,

    -- Hash-Kette
    previous_hash CHAR(64) NULL,
    entry_hash CHAR(64) NULL,

    -- Wer, wann, warum
    geaendert_von_id BIGINT NULL,
    geaendert_am DATETIME(6) NOT NULL,
    aenderungsgrund TEXT NULL,
    ip_adresse VARCHAR(45) NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uq_beleg_audit_chain_index (chain_index),
    KEY idx_beleg_audit_beleg (beleg_id),
    KEY idx_beleg_audit_geaendert_am (geaendert_am)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- =====================================================================
-- 3) Kassenzaehlung (Kassensturz)
-- =====================================================================
-- Insert-only: eine gezaehlte Kasse, die man hinterher aendern kann, ist
-- wertlos. Es gibt daher keinen Update- und keinen Delete-Pfad im Service.
-- Die Stueckelung (wie viele 50er, wie viele 2-Euro-Stuecke) liegt als
-- JSON-Text daneben, damit der Zaehlzettel spaeter reproduzierbar ist.
CREATE TABLE IF NOT EXISTS kassenzaehlung (
    id BIGINT NOT NULL AUTO_INCREMENT,
    gezaehlt_am DATETIME(6) NOT NULL,
    stichtag DATE NOT NULL,
    gezaehlter_bestand DECIMAL(15,2) NOT NULL,
    rechnerischer_bestand DECIMAL(15,2) NOT NULL,
    differenz DECIMAL(15,2) NOT NULL,
    stueckelung_json TEXT NULL,
    bemerkung VARCHAR(1000) NULL,
    -- Beleg, der eine festgestellte Differenz in der Kasse ausgleicht
    -- (Kassenfehlbetrag / Kassenueberschuss). NULL = Differenz war 0 oder
    -- wurde bewusst nur dokumentiert, nicht gebucht.
    ausgleich_beleg_id BIGINT NULL,
    erfasst_von_id BIGINT NULL,
    PRIMARY KEY (id),
    KEY idx_kassenzaehlung_stichtag (stichtag)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- =====================================================================
-- 4) Monatsabschluss
-- =====================================================================
-- Ein abgeschlossener Monat ist das Ereignis, das die Belege festschreibt.
-- Ein Monat kann genau einmal abgeschlossen werden (UNIQUE auf jahr+monat)
-- und wird nie wieder geoeffnet -- ein Wiederaufmachen waere genau die
-- Hintertuer, die § 146 AO ausschliessen soll.
CREATE TABLE IF NOT EXISTS kassenbuch_monatsabschluss (
    id BIGINT NOT NULL AUTO_INCREMENT,
    jahr INT NOT NULL,
    monat INT NOT NULL,
    abgeschlossen_am DATETIME(6) NOT NULL,
    abgeschlossen_von_id BIGINT NULL,
    anfangsbestand DECIMAL(15,2) NOT NULL,
    endbestand DECIMAL(15,2) NOT NULL,
    summe_einnahmen DECIMAL(15,2) NOT NULL,
    summe_ausgaben DECIMAL(15,2) NOT NULL,
    anzahl_belege INT NOT NULL,
    erste_laufende_nummer BIGINT NULL,
    letzte_laufende_nummer BIGINT NULL,
    -- Kettenposition zum Zeitpunkt des Abschlusses. Wer spaeter behauptet,
    -- der Monat habe anders ausgesehen, muss diesen Hash reproduzieren.
    chain_index BIGINT NULL,
    entry_hash CHAR(64) NULL,
    bemerkung VARCHAR(1000) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_monatsabschluss_jahr_monat (jahr, monat)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- =====================================================================
-- 5) Neue Spalten an der beleg-Tabelle
-- =====================================================================

-- laufende_nummer: dauerhafte, lueckenlose Belegnummer. Vergeben beim
-- Monatsabschluss, danach unveraenderlich. NULL = Beleg gehoert noch zu
-- einem offenen Monat.
SET @c = (SELECT COUNT(*) FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'beleg' AND COLUMN_NAME = 'laufende_nummer');
SET @s = IF(@c = 0, 'ALTER TABLE beleg ADD COLUMN laufende_nummer BIGINT NULL', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @c = (SELECT COUNT(*) FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'beleg' AND COLUMN_NAME = 'festgeschrieben');
SET @s = IF(@c = 0, 'ALTER TABLE beleg ADD COLUMN festgeschrieben TINYINT(1) NOT NULL DEFAULT 0', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @c = (SELECT COUNT(*) FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'beleg' AND COLUMN_NAME = 'festgeschrieben_am');
SET @s = IF(@c = 0, 'ALTER TABLE beleg ADD COLUMN festgeschrieben_am DATETIME(6) NULL', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @c = (SELECT COUNT(*) FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'beleg' AND COLUMN_NAME = 'festgeschrieben_von_id');
SET @s = IF(@c = 0, 'ALTER TABLE beleg ADD COLUMN festgeschrieben_von_id BIGINT NULL', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- monatsabschluss_id: an welchem Abschluss haengt dieser Beleg. Macht im
-- Export nachvollziehbar, welcher Beleg in welchem Monatspaket steckt.
SET @c = (SELECT COUNT(*) FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'beleg' AND COLUMN_NAME = 'monatsabschluss_id');
SET @s = IF(@c = 0, 'ALTER TABLE beleg ADD COLUMN monatsabschluss_id BIGINT NULL', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- storno_fuer_beleg_id: dieser Beleg IST die Stornobuchung von jenem.
SET @c = (SELECT COUNT(*) FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'beleg' AND COLUMN_NAME = 'storno_fuer_beleg_id');
SET @s = IF(@c = 0, 'ALTER TABLE beleg ADD COLUMN storno_fuer_beleg_id BIGINT NULL', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- storniert_durch_beleg_id: dieser Beleg WURDE durch jenen storniert.
SET @c = (SELECT COUNT(*) FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'beleg' AND COLUMN_NAME = 'storniert_durch_beleg_id');
SET @s = IF(@c = 0, 'ALTER TABLE beleg ADD COLUMN storniert_durch_beleg_id BIGINT NULL', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @c = (SELECT COUNT(*) FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'beleg' AND COLUMN_NAME = 'storniert_am');
SET @s = IF(@c = 0, 'ALTER TABLE beleg ADD COLUMN storniert_am DATETIME(6) NULL', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @c = (SELECT COUNT(*) FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'beleg' AND COLUMN_NAME = 'storno_grund');
SET @s = IF(@c = 0, 'ALTER TABLE beleg ADD COLUMN storno_grund VARCHAR(500) NULL', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- datei_hash: SHA-256 des hochgeladenen Belegfotos, einmal beim Upload
-- berechnet. Damit ist im Export beweisbar, dass das Bild im Ordner genau
-- das Bild ist, das der Buchhalter geprueft hat.
SET @c = (SELECT COUNT(*) FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'beleg' AND COLUMN_NAME = 'datei_hash');
SET @s = IF(@c = 0, 'ALTER TABLE beleg ADD COLUMN datei_hash CHAR(64) NULL', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- Lueckenlosigkeit auf DB-Ebene absichern: dieselbe laufende Nummer darf
-- es kein zweites Mal geben. NULL-Werte sind in MySQL von UNIQUE
-- ausgenommen, offene Monate bleiben also erlaubt.
SET @c = (SELECT COUNT(*) FROM information_schema.STATISTICS
          WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'beleg' AND INDEX_NAME = 'uq_beleg_laufende_nummer');
SET @s = IF(@c = 0, 'CREATE UNIQUE INDEX uq_beleg_laufende_nummer ON beleg (laufende_nummer)', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @c = (SELECT COUNT(*) FROM information_schema.STATISTICS
          WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'beleg' AND INDEX_NAME = 'idx_beleg_festgeschrieben');
SET @s = IF(@c = 0, 'CREATE INDEX idx_beleg_festgeschrieben ON beleg (festgeschrieben, beleg_datum)', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
