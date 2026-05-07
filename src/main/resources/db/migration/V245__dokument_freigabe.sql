-- Digitale Freigabe von Geschäftsdokumenten (Angebot, Auftragsbestätigung) durch den Kunden.
-- Der Kunde bekommt per E-Mail einen Link mit einer eindeutigen UUID auf eine öffentliche
-- Seite (Astro auf bauschlosserei-kuhn.de), akzeptiert dort das Dokument digital und wir
-- speichern einen unveränderbaren SHA-256-Fingerabdruck als Beweis.
--
-- Snapshot-Pattern: Die für die öffentliche Anzeige nötigen Daten werden beim Versand
-- in diese Tabelle kopiert. Damit bleibt die Freigabe nachvollziehbar, auch wenn das
-- Originaldokument im ERP später gelöscht oder geändert wird.
CREATE TABLE IF NOT EXISTS dokument_freigabe (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL,

    -- Quelle: aus welcher Tabelle stammt das Originaldokument
    quell_typ VARCHAR(20) NOT NULL,
    quell_dokument_id BIGINT NOT NULL,

    -- Snapshot-Felder (kopiert beim Versand)
    dokument_nummer VARCHAR(100) NOT NULL,
    dokument_art VARCHAR(50) NOT NULL,
    dokument_betrag DECIMAL(12,2) NULL,
    dokument_datei VARCHAR(255) NULL,
    bauvorhaben VARCHAR(500) NULL,
    kunde_name VARCHAR(255) NULL,
    kunde_email VARCHAR(255) NULL,

    -- Lifecycle
    erstellt_am DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    ablauf_datum DATETIME(6) NOT NULL,
    hash_original VARCHAR(128) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',

    -- Annahme-Beweis
    akzeptiert_am DATETIME(6) NULL,
    akzeptiert_ip VARCHAR(45) NULL,
    akzeptiert_user_agent VARCHAR(500) NULL,
    akzeptiert_email VARCHAR(255) NULL,
    hash_acceptance VARCHAR(128) NULL,

    CONSTRAINT uk_dokument_freigabe_uuid UNIQUE (uuid),
    INDEX idx_dokument_freigabe_quelle (quell_typ, quell_dokument_id),
    INDEX idx_dokument_freigabe_status_ablauf (status, ablauf_datum)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
