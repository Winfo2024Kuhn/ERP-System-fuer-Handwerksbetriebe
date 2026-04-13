-- V212: EN 1090 EXC 2 + E-Check (BGV A3) Feature-Erweiterungen
-- Alle Tabellen in einer Migration um Konsistenz zu garantieren

-- ---------------------------------------------------------------------------
-- 1. lieferant_dokument: Wareneingangs-Prüfung (Ware geprüft)
-- ---------------------------------------------------------------------------
ALTER TABLE lieferant_dokument
    ADD COLUMN IF NOT EXISTS ware_geprueft BOOLEAN NOT NULL DEFAULT FALSE;

-- ---------------------------------------------------------------------------
-- 2. schweisser_zertifikat: Schweißer-Qualifikationen (EN ISO 9606-1)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS schweisser_zertifikat (
    id                  BIGINT NOT NULL AUTO_INCREMENT,
    mitarbeiter_id      BIGINT NOT NULL,
    zertifikatsnummer   VARCHAR(100) NOT NULL,
    norm                VARCHAR(100) NOT NULL COMMENT 'z.B. EN ISO 9606-1, EN ISO 14732',
    schweiss_prozess    VARCHAR(50)  NOT NULL COMMENT 'z.B. 111 MMA, 135 MAG, 141 WIG',
    grundwerkstoff      VARCHAR(100)          COMMENT 'z.B. S355, 1.4301',
    pruefstelle         VARCHAR(200)          COMMENT 'Name der Prüfstelle/Überwachungsorg.',
    ausstellungsdatum   DATE NOT NULL,
    ablaufdatum         DATE                  COMMENT 'NULL = unbegrenzt (z.B. Meisterprüfung)',
    gespeicherter_dateiname VARCHAR(500)      COMMENT 'Zertifikat-PDF (UUID-Dateiname)',
    original_dateiname  VARCHAR(500),
    erstellt_am         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    aktualisiert_am     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_schw_zert_mitarbeiter FOREIGN KEY (mitarbeiter_id)
        REFERENCES mitarbeiter (id) ON DELETE RESTRICT,
    INDEX idx_schw_zert_mitarbeiter (mitarbeiter_id),
    INDEX idx_schw_zert_ablauf      (ablaufdatum)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 3. wps: Schweißanweisungen (EN ISO 15614-1)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS wps (
    id                  BIGINT NOT NULL AUTO_INCREMENT,
    wps_nummer          VARCHAR(100) NOT NULL UNIQUE COMMENT 'z.B. WPS-2024-001',
    bezeichnung         VARCHAR(255),
    norm                VARCHAR(100) NOT NULL COMMENT 'z.B. EN ISO 15614-1',
    schweiss_prozess    VARCHAR(50)  NOT NULL COMMENT 'z.B. 135 MAG',
    grundwerkstoff      VARCHAR(100)          COMMENT 'z.B. S235, S355',
    zusatzwerkstoff     VARCHAR(200),
    nahtart             VARCHAR(100)          COMMENT 'z.B. Stumpfnaht, Kehlnaht',
    blechdicke_min      DECIMAL(6,2),
    blechdicke_max      DECIMAL(6,2),
    revisionsdatum      DATE,
    gueltig_bis         DATE,
    gespeicherter_dateiname VARCHAR(500),
    original_dateiname  VARCHAR(500),
    erstellt_am         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    aktualisiert_am     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_wps_nummer (wps_nummer)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- M:N Verknüpfung WPS <-> Projekt
CREATE TABLE IF NOT EXISTS wps_projekt (
    wps_id              BIGINT NOT NULL,
    projekt_id          BIGINT NOT NULL,
    PRIMARY KEY (wps_id, projekt_id),
    CONSTRAINT fk_wps_proj_wps     FOREIGN KEY (wps_id)     REFERENCES wps     (id) ON DELETE CASCADE,
    CONSTRAINT fk_wps_proj_projekt FOREIGN KEY (projekt_id) REFERENCES projekt  (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 4. werkstoffzeugnis: EN 10204 Zeugnisse (3.1 / 3.2)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS werkstoffzeugnis (
    id                  BIGINT NOT NULL AUTO_INCREMENT,
    lieferant_id        BIGINT,
    schmelz_nummer      VARCHAR(100)          COMMENT 'Charge/Schmelze (wird als dokumentNummer verwendet)',
    material_guete      VARCHAR(100)          COMMENT 'z.B. S355J2, 1.4301, St37-2',
    norm_typ            VARCHAR(10)  NOT NULL DEFAULT '3.1'
                            COMMENT 'EN 10204 Zeugnistyp: 2.1, 2.2, 3.1, 3.2',
    pruef_datum         DATE,
    pruefstelle         VARCHAR(200),
    lieferant_dokument_id BIGINT    UNIQUE    COMMENT 'Verknüpfung zum gescannten Dokument',
    gespeicherter_dateiname VARCHAR(500),
    original_dateiname  VARCHAR(500),
    erstellt_am         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    aktualisiert_am     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_werks_lieferant FOREIGN KEY (lieferant_id)
        REFERENCES lieferanten (id) ON DELETE SET NULL,
    CONSTRAINT fk_werks_dokument  FOREIGN KEY (lieferant_dokument_id)
        REFERENCES lieferant_dokument (id) ON DELETE SET NULL,
    INDEX idx_werks_schmelz    (schmelz_nummer),
    INDEX idx_werks_lieferant  (lieferant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- M:N Verknüpfung Werkstoffzeugnis <-> Projekt
CREATE TABLE IF NOT EXISTS werkstoffzeugnis_projekt (
    werkstoffzeugnis_id BIGINT NOT NULL,
    projekt_id          BIGINT NOT NULL,
    PRIMARY KEY (werkstoffzeugnis_id, projekt_id),
    CONSTRAINT fk_wz_proj_wz      FOREIGN KEY (werkstoffzeugnis_id) REFERENCES werkstoffzeugnis (id) ON DELETE CASCADE,
    CONSTRAINT fk_wz_proj_projekt FOREIGN KEY (projekt_id)          REFERENCES projekt          (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 5. betriebsmittel: Elektrische Betriebsmittel für E-Check (BGV A3 / DGUV V3)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS betriebsmittel (
    id                      BIGINT NOT NULL AUTO_INCREMENT,
    bezeichnung             VARCHAR(255) NOT NULL,
    seriennummer            VARCHAR(200) UNIQUE,
    barcode                 VARCHAR(300) UNIQUE      COMMENT 'Barcode/QR-Code für mobile Identifikation',
    hersteller              VARCHAR(200),
    modell                  VARCHAR(200),
    standort                VARCHAR(200)             COMMENT 'z.B. Lager, Baustelle XY',
    bild_dateiname          VARCHAR(500),
    naechstes_pruef_datum   DATE                     COMMENT 'Wird auto. nach Prüfung berechnet',
    pruef_intervall_monate  INT NOT NULL DEFAULT 12  COMMENT 'Prüfintervall nach DGUV V3',
    ausser_betrieb          BOOLEAN NOT NULL DEFAULT FALSE,
    erstellt_am             DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    aktualisiert_am         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_bm_barcode        (barcode),
    INDEX idx_bm_pruef_datum    (naechstes_pruef_datum),
    INDEX idx_bm_seriennummer   (seriennummer)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 6. betriebsmittel_pruefung: Prüfprotokolle E-Check (BGV A3)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS betriebsmittel_pruefung (
    id                  BIGINT NOT NULL AUTO_INCREMENT,
    betriebsmittel_id   BIGINT NOT NULL,
    pruefer_id          BIGINT               COMMENT 'Mitarbeiter der die Prüfung durchführt',
    pruef_datum         DATE NOT NULL,
    naechstes_pruef_datum DATE               COMMENT 'Berechnetes nächstes Prüfdatum',
    bestanden           BOOLEAN NOT NULL DEFAULT TRUE,
    schutzklasse        VARCHAR(20)          COMMENT 'SK I, SK II, SK III',
    messwert_schutzleiter DECIMAL(8,4)       COMMENT 'Schutzleiterwiderstand in Ohm',
    messwert_isolationswiderstand DECIMAL(8,4) COMMENT 'Isolationswiderstand in MΩ',
    messwert_ableitstrom DECIMAL(8,4)        COMMENT 'Berührungsstrom in mA',
    bemerkung           TEXT,
    von_elektriker_verifiziert BOOLEAN NOT NULL DEFAULT FALSE
                            COMMENT 'Nachbearbeitung durch Elektriker auf Desktop',
    erstellt_am         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_bm_pruef_bm      FOREIGN KEY (betriebsmittel_id) REFERENCES betriebsmittel (id) ON DELETE RESTRICT,
    CONSTRAINT fk_bm_pruef_pruefer FOREIGN KEY (pruefer_id)        REFERENCES mitarbeiter     (id) ON DELETE SET NULL,
    INDEX idx_bm_pruef_bm   (betriebsmittel_id),
    INDEX idx_bm_pruef_datum (pruef_datum)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
