-- Datenmodell fuer Langzeitkrankmeldungen (Issue #91): buendelt die
-- zeitliche Abfolge aus Lohnfortzahlung, Krankengeld und optionaler
-- Wiedereingliederung eines Mitarbeiters.
--
-- langzeitkrankmeldung ist das Wurzel-Aggregat (traegt version, optimistisches
-- Sperren wie in V364__aggregat_versionsspalten.sql beschrieben).
-- langzeitkrankmeldung_phase ist die Kind-Entitaet (Positionen der zeitlichen
-- Abfolge) und bekommt bewusst KEINE eigene version-Spalte -- gespeichert wird
-- immer ueber die Wurzel, deren Version als Waechter fuer den gesamten
-- Aggregatsbaum reicht (dieselbe Begruendung wie in V364).
--
-- status (langzeitkrankmeldung) und typ (langzeitkrankmeldung_phase) sind
-- native ENUM-Spalten, keine VARCHAR-Spalten: Hibernate 6.x mit MySQL-Dialekt
-- mappt @Enumerated(EnumType.STRING) auf einen nativen ENUM-Spaltentyp, sonst
-- schlaegt ddl-auto=validate beim Start fehl (siehe BACKEND_ARCH.md, Vorbild
-- V366__datensatz_lock_entitaet_typ_enum.sql). Werte exakt wie die
-- Java-Enum-Konstanten (UPPERCASE).
--
-- abwesenheit bekommt zwei neue, nullable FK-Spalten auf beide neuen Tabellen
-- (langzeitkrankmeldung_id, langzeitkrankmeldung_phase_id) -- analog zur
-- bestehenden urlaubsantrag_id-Spalte. ON DELETE SET NULL: eine geloeschte
-- Langzeitkrankmeldung darf die bereits gebuchten Abwesenheitstage nicht mit
-- sich reissen, sie verlieren nur den Bezug (gleiche Ueberlegung wie beim
-- Preisstand-Bezug in V339__artikel_in_projekt_preisbezug.sql).
--
-- Idempotent: CREATE TABLE IF NOT EXISTS fuer beide neuen Tabellen, die
-- ALTER-TABLE- und ADD-CONSTRAINT-Bloecke an abwesenheit pruefen vorher ueber
-- information_schema, ob Spalte/Constraint schon existieren (Muster aus
-- V364, Zeile 47-53, und V339, Abschnitt 4).

CREATE TABLE IF NOT EXISTS langzeitkrankmeldung (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    mitarbeiter_id       BIGINT       NOT NULL,
    beginn               DATE         NOT NULL,
    ende                 DATE         NULL,
    status               ENUM('LAUFEND','BEENDET','ABGEBROCHEN') NOT NULL,
    lohnfortzahlung_bis  DATE         NOT NULL,
    notiz                VARCHAR(500) NULL,
    version              BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_langzeitkrankmeldung_mitarbeiter FOREIGN KEY (mitarbeiter_id)
        REFERENCES mitarbeiter(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET @idx := (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE()
    AND table_name = 'langzeitkrankmeldung' AND index_name = 'idx_langzeitkrankmeldung_mitarbeiter_beginn');
SET @sql := IF(@idx = 0,
    'CREATE INDEX idx_langzeitkrankmeldung_mitarbeiter_beginn ON langzeitkrankmeldung (mitarbeiter_id, beginn)',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS langzeitkrankmeldung_phase (
    id                       BIGINT       NOT NULL AUTO_INCREMENT,
    langzeitkrankmeldung_id  BIGINT       NOT NULL,
    typ                      ENUM('LOHNFORTZAHLUNG','KRANKENGELD','WIEDEREINGLIEDERUNG') NOT NULL,
    von_datum                DATE         NOT NULL,
    bis_datum                DATE         NULL,
    stunden_pro_tag          DECIMAL(4,2) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_langzeitkrankmeldung_phase_meldung FOREIGN KEY (langzeitkrankmeldung_id)
        REFERENCES langzeitkrankmeldung(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET @idx := (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE()
    AND table_name = 'langzeitkrankmeldung_phase' AND index_name = 'idx_langzeitkrankmeldung_phase_meldung_von');
SET @sql := IF(@idx = 0,
    'CREATE INDEX idx_langzeitkrankmeldung_phase_meldung_von ON langzeitkrankmeldung_phase (langzeitkrankmeldung_id, von_datum)',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- abwesenheit.langzeitkrankmeldung_id
SET @col_exists := (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'abwesenheit' AND column_name = 'langzeitkrankmeldung_id');
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE abwesenheit ADD COLUMN langzeitkrankmeldung_id BIGINT NULL',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- abwesenheit.langzeitkrankmeldung_phase_id
SET @col_exists := (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'abwesenheit' AND column_name = 'langzeitkrankmeldung_phase_id');
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE abwesenheit ADD COLUMN langzeitkrankmeldung_phase_id BIGINT NULL',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- fk_abwesenheit_langzeitkrankmeldung
SET @fk := (SELECT COUNT(*) FROM information_schema.table_constraints
    WHERE table_schema = DATABASE() AND table_name = 'abwesenheit'
      AND constraint_name = 'fk_abwesenheit_langzeitkrankmeldung');
SET @sql := IF(@fk = 0,
    'ALTER TABLE abwesenheit ADD CONSTRAINT fk_abwesenheit_langzeitkrankmeldung
       FOREIGN KEY (langzeitkrankmeldung_id) REFERENCES langzeitkrankmeldung (id)
       ON DELETE SET NULL',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- fk_abwesenheit_langzeitkrankmeldung_phase
SET @fk := (SELECT COUNT(*) FROM information_schema.table_constraints
    WHERE table_schema = DATABASE() AND table_name = 'abwesenheit'
      AND constraint_name = 'fk_abwesenheit_langzeitkrankmeldung_phase');
SET @sql := IF(@fk = 0,
    'ALTER TABLE abwesenheit ADD CONSTRAINT fk_abwesenheit_langzeitkrankmeldung_phase
       FOREIGN KEY (langzeitkrankmeldung_phase_id) REFERENCES langzeitkrankmeldung_phase (id)
       ON DELETE SET NULL',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
