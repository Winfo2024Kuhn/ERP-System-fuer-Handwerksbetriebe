-- ═══════════════════════════════════════════════════════════════
-- Preisanfrage: Einkaufs-Anfrage an mehrere Lieferanten parallel
-- ═══════════════════════════════════════════════════════════════
-- Ermöglicht Handwerkern, für dieselben Bedarfspositionen Angebote von
-- mehreren Lieferanten einzuholen und die günstigsten Preise zu vergleichen.
-- Jeder Lieferant erhält einen eindeutigen Token im PDF/Betreff, über den
-- die Antwort automatisch zugeordnet wird (via In-Reply-To oder Token-Regex).

-- Haupt-Tabelle: Eine Preisanfrage umfasst mehrere Lieferanten und Positionen.
CREATE TABLE IF NOT EXISTS preisanfrage (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nummer VARCHAR(20) NOT NULL UNIQUE COMMENT 'Format PA-YYYY-NNN, z. B. PA-2026-041',
    bauvorhaben VARCHAR(255),
    projekt_id BIGINT NULL,
    erstellt_am DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    antwort_frist DATE NULL COMMENT 'Rueckmeldefrist fuer Lieferanten (Soft-Deadline)',
    status VARCHAR(40) NOT NULL DEFAULT 'OFFEN'
        COMMENT 'OFFEN | TEILWEISE_BEANTWORTET | VOLLSTAENDIG | VERGEBEN | ABGEBROCHEN',
    notiz TEXT,
    vergeben_an_preisanfrage_lieferant_id BIGINT NULL
        COMMENT 'Nach Entscheidung: welcher Lieferant bekam den Auftrag',
    CONSTRAINT fk_preisanfrage_projekt FOREIGN KEY (projekt_id) REFERENCES projekt(id)
        ON DELETE SET NULL,
    INDEX idx_preisanfrage_status (status),
    INDEX idx_preisanfrage_projekt (projekt_id),
    INDEX idx_preisanfrage_erstellt (erstellt_am)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_german2_ci;

-- Pro Lieferant ein eindeutiger Token + Versand-Historie.
-- outgoing_message_id ist die Message-ID der ausgehenden Preisanfrage-Mail;
-- eingehende Antworten werden ueber diesen Wert (parentEmail.messageId) zugeordnet.
CREATE TABLE IF NOT EXISTS preisanfrage_lieferant (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    preisanfrage_id BIGINT NOT NULL,
    lieferant_id BIGINT NOT NULL,
    token VARCHAR(40) NOT NULL UNIQUE COMMENT 'PA-YYYY-NNN-XXXXX, eindeutig',
    versendet_an VARCHAR(255) COMMENT 'Tatsaechliche Empfaenger-E-Mail',
    versendet_am DATETIME(6) NULL,
    outgoing_email_id BIGINT NULL COMMENT 'FK auf email.id der ausgehenden Preisanfrage-Mail',
    outgoing_message_id VARCHAR(512) NULL COMMENT 'Message-ID der ausgehenden Mail (Match via parentEmail)',
    antwort_erhalten_am DATETIME(6) NULL,
    antwort_email_id BIGINT NULL COMMENT 'FK auf email.id der eingegangenen Antwort',
    status VARCHAR(40) NOT NULL DEFAULT 'VORBEREITET'
        COMMENT 'VORBEREITET | VERSENDET | BEANTWORTET | ABGELEHNT',
    CONSTRAINT fk_pal_preisanfrage FOREIGN KEY (preisanfrage_id) REFERENCES preisanfrage(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_pal_lieferant FOREIGN KEY (lieferant_id) REFERENCES lieferanten(id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_pal_outgoing_email FOREIGN KEY (outgoing_email_id) REFERENCES email(id)
        ON DELETE SET NULL,
    CONSTRAINT fk_pal_antwort_email FOREIGN KEY (antwort_email_id) REFERENCES email(id)
        ON DELETE SET NULL,
    INDEX idx_pal_token (token),
    INDEX idx_pal_outgoing_mid (outgoing_message_id),
    INDEX idx_pal_status (status),
    UNIQUE KEY uk_pal_preisanfrage_lieferant (preisanfrage_id, lieferant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_german2_ci;

-- Positionen der Preisanfrage: Kopie der relevanten Bedarfsposition zum Zeitpunkt
-- der Anfrage-Erstellung. artikel_in_projekt_id ist optionaler Rueckverweis auf
-- die Original-Bedarfsposition (fuer spaetere Vergabe).
CREATE TABLE IF NOT EXISTS preisanfrage_position (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    preisanfrage_id BIGINT NOT NULL,
    artikel_in_projekt_id BIGINT NULL COMMENT 'Quelle: offene Bedarfs-Position (ArtikelInProjekt)',
    artikel_id BIGINT NULL,
    externe_artikelnummer VARCHAR(100),
    produktname VARCHAR(255),
    produkttext TEXT,
    werkstoff_name VARCHAR(100),
    menge DECIMAL(12,3),
    einheit VARCHAR(20),
    kommentar TEXT,
    reihenfolge INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_pap_preisanfrage FOREIGN KEY (preisanfrage_id) REFERENCES preisanfrage(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_pap_artikel_in_projekt FOREIGN KEY (artikel_in_projekt_id) REFERENCES artikel_in_projekt(id)
        ON DELETE SET NULL,
    INDEX idx_pap_preisanfrage (preisanfrage_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_german2_ci;

-- Angebot eines Lieferanten fuer eine bestimmte Position (manuelle Preiserfassung V1).
CREATE TABLE IF NOT EXISTS preisanfrage_angebot (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    preisanfrage_lieferant_id BIGINT NOT NULL,
    preisanfrage_position_id BIGINT NOT NULL,
    einzelpreis DECIMAL(12,4) NULL COMMENT 'Pro Einheit, netto',
    gesamtpreis DECIMAL(14,2) NULL COMMENT 'Optional: Position gesamt, netto',
    mwst_prozent DECIMAL(5,2) NULL,
    lieferzeit_tage INT NULL,
    gueltig_bis DATE NULL,
    bemerkung TEXT,
    erfasst_am DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    erfasst_durch VARCHAR(100) COMMENT 'manuell | ki-extraktion (V2)',
    CONSTRAINT fk_paa_pal FOREIGN KEY (preisanfrage_lieferant_id) REFERENCES preisanfrage_lieferant(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_paa_position FOREIGN KEY (preisanfrage_position_id) REFERENCES preisanfrage_position(id)
        ON DELETE CASCADE,
    UNIQUE KEY uk_paa_pal_position (preisanfrage_lieferant_id, preisanfrage_position_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_german2_ci;

-- FK auf preisanfrage_lieferant erst jetzt hinzufuegen, da zirkulaere Abhaengigkeit.
-- Idempotent: Constraint nur anlegen wenn noch nicht vorhanden.
SET @fk_exists := (
    SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE()
      AND TABLE_NAME = 'preisanfrage'
      AND CONSTRAINT_NAME = 'fk_preisanfrage_vergeben_pal'
);
SET @sql := IF(@fk_exists = 0,
    'ALTER TABLE preisanfrage ADD CONSTRAINT fk_preisanfrage_vergeben_pal FOREIGN KEY (vergeben_an_preisanfrage_lieferant_id) REFERENCES preisanfrage_lieferant(id) ON DELETE SET NULL',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
