-- Verallgemeinertes Soft-Lock fuer sperrbare Datensaetze -- nicht mehr nur
-- Geschaeftsdokumente wie zuvor in dokument_lock (V292). Ersetzt diese Tabelle.
--
-- Strategie unveraendert (Heartbeat-Lock, siehe V292):
--   * Beim Oeffnen wird ein Eintrag mit acquired_at + last_heartbeat_at = NOW() angelegt.
--   * Frontend pingt regelmaessig einen Heartbeat-Endpoint, der last_heartbeat_at aktualisiert.
--   * Beim Schliessen des Tabs wird der Eintrag aktiv geloescht.
--   * Faellt das Schliessen aus (Browser-Crash, Netzabbruch), darf ein anderer
--     Benutzer das Lock uebernehmen, sobald last_heartbeat_at > 90s alt ist.
--
-- Warum DROP + CREATE statt RENAME/ALTER: Lock-Eintraege sind reine
-- Laufzeit-Zustaende ohne fachlichen Wert und ohne Historienbedarf.
-- Schlimmstenfalls muss beim Deploy einmal neu auf "Bearbeiten" geklickt
-- werden, weil ein laufendes Lock verloren geht -- unkritisch, und guenstiger
-- als eine Rename-Migration samt Spaltenumbenennung mitzuschleppen.
--
-- (entitaet_typ, entitaet_id) ist weiterhin der zusammengesetzte Schluessel
-- (vormals dokument_typ/dokument_id), weil IDs zwischen den sperrbaren Typen
-- ueberlappen (z.B. AUSGANG/1 und EINGANG/1 sind unabhaengige Datensaetze).
--
-- entitaet_typ ist VARCHAR(32) und bewusst KEIN natives MySQL-ENUM: dieses
-- Projekt stellt in application.properties
-- (hibernate.type.preferred_enum_jdbc_type=VARCHAR) den Hibernate-Default
-- projektweit auf VARCHAR um -- ein natives ENUM wuerde ddl-auto=validate
-- beim Start brechen. Das Java-Feld ist @Enumerated(EnumType.STRING) auf
-- SperrbarerTyp (aktuell AUSGANG, EINGANG).

DROP TABLE IF EXISTS dokument_lock;

CREATE TABLE IF NOT EXISTS datensatz_lock (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    entitaet_typ       VARCHAR(32)  NOT NULL,
    entitaet_id        BIGINT       NOT NULL,
    user_id            BIGINT       NOT NULL,
    user_display_name  VARCHAR(255) NOT NULL,
    acquired_at        DATETIME     NOT NULL,
    last_heartbeat_at  DATETIME     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_datensatz_lock_target UNIQUE (entitaet_typ, entitaet_id),
    INDEX idx_datensatz_lock_heartbeat (last_heartbeat_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
