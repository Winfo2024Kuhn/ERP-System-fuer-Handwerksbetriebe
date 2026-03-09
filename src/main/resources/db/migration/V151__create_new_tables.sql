-- V151: Neue Tabellen erstellen die auf dem aktuellen Branch hinzugekommen sind.
-- Alle mit IF NOT EXISTS, da Hibernate ddl-auto=update sie evtl. schon erstellt hat.

-- ============================================================
-- 1. steuerberater_kontakt (Muss VOR bwa_upload und lohnabrechnung kommen wegen FK)
-- ============================================================
CREATE TABLE IF NOT EXISTS steuerberater_kontakt (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    telefon VARCHAR(255),
    ansprechpartner VARCHAR(255),
    auto_process_emails BIT(1) NOT NULL DEFAULT 1,
    aktiv BIT(1) NOT NULL DEFAULT 1,
    notizen VARCHAR(500),
    gueltig_ab DATE,
    gueltig_bis DATE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Zugehörige ElementCollection-Tabelle
CREATE TABLE IF NOT EXISTS steuerberater_kontakt_emails (
    steuerberater_id BIGINT NOT NULL,
    email VARCHAR(255),
    CONSTRAINT fk_stb_emails_stb FOREIGN KEY (steuerberater_id) REFERENCES steuerberater_kontakt(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 2. firma_kostenstelle (Muss VOR bwa_position kommen wegen FK)
-- ============================================================
CREATE TABLE IF NOT EXISTS firma_kostenstelle (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    typ VARCHAR(255) NOT NULL,
    beschreibung VARCHAR(500),
    ist_fixkosten BIT(1) NOT NULL DEFAULT 0,
    ist_investition BIT(1) NOT NULL DEFAULT 0,
    aktiv BIT(1) NOT NULL DEFAULT 1,
    sortierung INT DEFAULT 0,
    CONSTRAINT uk_firma_kostenstelle_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 3. arbeitszeitart
-- ============================================================
CREATE TABLE IF NOT EXISTS arbeitszeitart (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    bezeichnung VARCHAR(100) NOT NULL,
    beschreibung TEXT,
    stundensatz DECIMAL(10,2) NOT NULL,
    aktiv BIT(1) NOT NULL DEFAULT 1,
    sortierung INT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 4. firmeninformation (Singleton-Tabelle)
-- ============================================================
CREATE TABLE IF NOT EXISTS firmeninformation (
    id BIGINT PRIMARY KEY,
    firmenname VARCHAR(255) NOT NULL,
    strasse VARCHAR(255),
    plz VARCHAR(255),
    ort VARCHAR(255),
    telefon VARCHAR(255),
    fax VARCHAR(255),
    email VARCHAR(255),
    website VARCHAR(255),
    steuernummer VARCHAR(255),
    ust_id_nr VARCHAR(255),
    handelsregister VARCHAR(255),
    handelsregister_nummer VARCHAR(255),
    bank_name VARCHAR(255),
    iban VARCHAR(255),
    bic VARCHAR(255),
    logo_dateiname VARCHAR(255),
    geschaeftsfuehrer VARCHAR(255),
    fusszeile_text VARCHAR(1000)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 5. ausgangs_geschaeftsdokument
-- ============================================================
CREATE TABLE IF NOT EXISTS ausgangs_geschaeftsdokument (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    dokument_nummer VARCHAR(20) NOT NULL,
    typ VARCHAR(30) NOT NULL,
    datum DATE NOT NULL,
    betreff VARCHAR(500),
    betrag_netto DECIMAL(12,2),
    betrag_brutto DECIMAL(12,2),
    mwst_satz DECIMAL(5,4),
    abschlags_nummer INT,
    html_inhalt LONGTEXT,
    positionen_json LONGTEXT,
    gebucht BIT(1) NOT NULL DEFAULT 0,
    gebucht_am DATE,
    storniert BIT(1) NOT NULL DEFAULT 0,
    storniert_am DATE,
    projekt_id BIGINT,
    angebot_id BIGINT,
    kunde_id BIGINT,
    vorgaenger_id BIGINT,
    zahlungsziel_tage INT,
    versand_datum DATE,
    rechnungsadresse_override VARCHAR(500),
    erstellt_von_id BIGINT,
    erstellt_am DATETIME NOT NULL,
    geaendert_am DATETIME,
    CONSTRAINT uk_ausgangs_gd_nummer UNIQUE (dokument_nummer),
    CONSTRAINT fk_ausgangs_gd_projekt FOREIGN KEY (projekt_id) REFERENCES projekt(id),
    CONSTRAINT fk_ausgangs_gd_angebot FOREIGN KEY (angebot_id) REFERENCES angebot(id),
    CONSTRAINT fk_ausgangs_gd_kunde FOREIGN KEY (kunde_id) REFERENCES kunde(id),
    CONSTRAINT fk_ausgangs_gd_vorgaenger FOREIGN KEY (vorgaenger_id) REFERENCES ausgangs_geschaeftsdokument(id),
    CONSTRAINT fk_ausgangs_gd_erstellt_von FOREIGN KEY (erstellt_von_id) REFERENCES frontend_user_profile(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 6. ausgangs_geschaeftsdokument_counter
-- ============================================================
CREATE TABLE IF NOT EXISTS ausgangs_geschaeftsdokument_counter (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    monat_key VARCHAR(10) NOT NULL,
    zaehler BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_ausgangs_gd_counter_monat UNIQUE (monat_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 7. bwa_upload (braucht steuerberater_kontakt FK)
-- ============================================================
CREATE TABLE IF NOT EXISTS bwa_upload (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    typ VARCHAR(255) NOT NULL,
    jahr INT NOT NULL,
    monat INT,
    original_dateiname VARCHAR(255),
    gespeicherter_dateiname VARCHAR(255),
    upload_datum DATETIME NOT NULL,
    analyse_datum DATETIME,
    ai_raw_json TEXT,
    ai_confidence DOUBLE,
    analysiert BIT(1) NOT NULL DEFAULT 0,
    freigegeben BIT(1) NOT NULL DEFAULT 0,
    freigegeben_am DATETIME,
    freigegeben_von_id BIGINT,
    gesamt_gemeinkosten DECIMAL(14,2),
    kosten_aus_rechnungen DECIMAL(14,2),
    kosten_aus_bwa DECIMAL(14,2),
    steuerberater_id BIGINT,
    email_id BIGINT,
    CONSTRAINT fk_bwa_upload_freigegeben_von FOREIGN KEY (freigegeben_von_id) REFERENCES mitarbeiter(id),
    CONSTRAINT fk_bwa_upload_steuerberater FOREIGN KEY (steuerberater_id) REFERENCES steuerberater_kontakt(id),
    CONSTRAINT fk_bwa_upload_email FOREIGN KEY (email_id) REFERENCES email(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 8. bwa_position (braucht bwa_upload und firma_kostenstelle FK)
-- ============================================================
CREATE TABLE IF NOT EXISTS bwa_position (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    bwa_upload_id BIGINT NOT NULL,
    kontonummer VARCHAR(20),
    bezeichnung VARCHAR(255) NOT NULL,
    betrag_monat DECIMAL(14,2) NOT NULL,
    betrag_kumuliert DECIMAL(14,2),
    kategorie VARCHAR(50),
    kostenstelle_id BIGINT,
    in_rechnungen_gefunden BIT(1) NOT NULL DEFAULT 0,
    rechnungssumme DECIMAL(14,2),
    differenz DECIMAL(14,2),
    manuell_korrigiert BIT(1) NOT NULL DEFAULT 0,
    notiz VARCHAR(500),
    CONSTRAINT fk_bwa_position_upload FOREIGN KEY (bwa_upload_id) REFERENCES bwa_upload(id),
    CONSTRAINT fk_bwa_position_kostenstelle FOREIGN KEY (kostenstelle_id) REFERENCES firma_kostenstelle(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 9. lohnabrechnung (braucht mitarbeiter, steuerberater_kontakt, email FK)
-- ============================================================
CREATE TABLE IF NOT EXISTS lohnabrechnung (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    mitarbeiter_id BIGINT NOT NULL,
    steuerberater_id BIGINT,
    jahr INT NOT NULL,
    monat INT NOT NULL,
    original_dateiname VARCHAR(255),
    gespeicherter_dateiname VARCHAR(255) NOT NULL,
    bruttolohn DECIMAL(10,2),
    nettolohn DECIMAL(10,2),
    import_datum DATETIME NOT NULL,
    email_id BIGINT,
    status VARCHAR(255) NOT NULL DEFAULT 'IMPORTIERT',
    ai_raw_json TEXT,
    CONSTRAINT fk_lohnabrechnung_mitarbeiter FOREIGN KEY (mitarbeiter_id) REFERENCES mitarbeiter(id),
    CONSTRAINT fk_lohnabrechnung_steuerberater FOREIGN KEY (steuerberater_id) REFERENCES steuerberater_kontakt(id),
    CONSTRAINT fk_lohnabrechnung_email FOREIGN KEY (email_id) REFERENCES email(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Indices für lohnabrechnung (inline in TABLE definition oder hier da Tabelle frisch erstellt)
-- Wenn Tabelle gerade erstellt wurde, existieren Indices garantiert nicht.
-- Fallback: Prüfung via information_schema ohne DELIMITER/Prozeduren.

SET @idx1 = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lohnabrechnung' AND INDEX_NAME = 'idx_lohnabrechnung_mitarbeiter');
SET @sql1 = IF(@idx1 = 0, 'CREATE INDEX idx_lohnabrechnung_mitarbeiter ON lohnabrechnung(mitarbeiter_id)', 'SELECT 1');
PREPARE stmt1 FROM @sql1;
EXECUTE stmt1;
DEALLOCATE PREPARE stmt1;

SET @idx2 = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lohnabrechnung' AND INDEX_NAME = 'idx_lohnabrechnung_periode');
SET @sql2 = IF(@idx2 = 0, 'CREATE INDEX idx_lohnabrechnung_periode ON lohnabrechnung(jahr, monat)', 'SELECT 1');
PREPARE stmt2 FROM @sql2;
EXECUTE stmt2;
DEALLOCATE PREPARE stmt2;

SET @idx3 = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'lohnabrechnung' AND INDEX_NAME = 'idx_lohnabrechnung_steuerberater');
SET @sql3 = IF(@idx3 = 0, 'CREATE INDEX idx_lohnabrechnung_steuerberater ON lohnabrechnung(steuerberater_id)', 'SELECT 1');
PREPARE stmt3 FROM @sql3;
EXECUTE stmt3;
DEALLOCATE PREPARE stmt3;
