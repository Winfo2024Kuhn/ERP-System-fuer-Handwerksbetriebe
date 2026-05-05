-- GoBD-konforme Audit-Tabelle für Ausgangs-Geschäftsdokumente.
-- Speichert einen unveränderlichen Snapshot bei jeder relevanten Aktion
-- (Erstellung, Änderung, Buchung, Versand, Stornierung, Löschung).
-- Pflicht-Begründung bei Löschung und Änderung -> Steuerprüfungs-tauglich.

CREATE TABLE IF NOT EXISTS ausgangs_geschaeftsdokument_audit (
    id BIGINT NOT NULL AUTO_INCREMENT,

    -- Referenz auf das Originaldokument. Bleibt nach Hard-Delete als reine
    -- ID-Referenz erhalten, daher KEIN Foreign-Key-Constraint.
    dokument_id BIGINT NOT NULL,

    aktion VARCHAR(20) NOT NULL,

    -- Snapshot der Stamm-Daten zum Zeitpunkt der Aktion ----------------------
    dokument_nummer VARCHAR(20) NOT NULL,
    typ VARCHAR(30) NOT NULL,
    datum DATE,
    betreff VARCHAR(500),
    betrag_netto DECIMAL(12, 2),
    betrag_brutto DECIMAL(12, 2),
    mwst_satz DECIMAL(5, 4),
    abschlags_nummer INT,
    projekt_id BIGINT,
    anfrage_id BIGINT,
    kunde_id BIGINT,
    vorgaenger_id BIGINT,
    versand_datum DATE,
    gebucht BOOLEAN NOT NULL DEFAULT FALSE,
    gebucht_am DATE,
    storniert BOOLEAN NOT NULL DEFAULT FALSE,
    storniert_am DATE,
    digital_angenommen BOOLEAN NOT NULL DEFAULT FALSE,

    -- Hash des aktuellen HTML-Inhalts (SHA-256, hex). Erlaubt Erkennung von
    -- Manipulationen ohne den vollen HTML-Body in der Audit-Tabelle zu duplizieren.
    inhalt_hash CHAR(64),

    -- Änderungs-Metadaten ---------------------------------------------------
    geaendert_von_id BIGINT,
    geaendert_am DATETIME(6) NOT NULL,
    aenderungsgrund TEXT,
    ip_adresse VARCHAR(45),

    PRIMARY KEY (id),

    INDEX idx_audit_dokument_id (dokument_id),
    INDEX idx_audit_geaendert_am (geaendert_am),
    INDEX idx_audit_aktion (aktion)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
