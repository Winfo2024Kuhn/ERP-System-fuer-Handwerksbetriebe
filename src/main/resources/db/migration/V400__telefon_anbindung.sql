-- Telefon-Anbindung (FRITZ!Box): Anrufliste, Sprachnachrichten, gemerkte Rufnummern
-- und das Abteilungs-Recht "Anrufe & Anrufbeantworter".
-- Unterdrückte Nummern werden als '' gespeichert (nicht NULL), damit der
-- Unique-Index auch für sie Doppelte verhindert.

CREATE TABLE IF NOT EXISTS telefon_anruf (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    zeitpunkt DATETIME NOT NULL,
    art ENUM('ANGENOMMEN','ANRUFBEANTWORTER','VERPASST','AUSGEHEND','ABGEWIESEN') NOT NULL,
    anrufbeantworter INT NULL,
    nummer_roh VARCHAR(40) NOT NULL DEFAULT '',
    nummer_normalisiert VARCHAR(40) NULL,
    eigene_nummer VARCHAR(40) NOT NULL,
    dauer_minuten INT NOT NULL DEFAULT 0,
    name_fritzbox VARCHAR(200) NULL,
    kunde_id BIGINT NULL,
    lieferant_id BIGINT NULL,
    zuordnung ENUM('AUTOMATISCH','MANUELL','KEINE') NOT NULL DEFAULT 'KEINE',
    angelegt_am DATETIME NOT NULL,
    CONSTRAINT uk_telefon_anruf UNIQUE (zeitpunkt, art, eigene_nummer, nummer_roh),
    CONSTRAINT fk_telefon_anruf_kunde FOREIGN KEY (kunde_id) REFERENCES kunde(id) ON DELETE SET NULL,
    CONSTRAINT fk_telefon_anruf_lieferant FOREIGN KEY (lieferant_id) REFERENCES lieferanten(id) ON DELETE SET NULL,
    INDEX idx_telefon_anruf_nummer (nummer_normalisiert),
    INDEX idx_telefon_anruf_zeitpunkt (zeitpunkt)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS sprachnachricht (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    anrufbeantworter INT NOT NULL,
    zeitpunkt DATETIME NOT NULL,
    nummer_roh VARCHAR(40) NOT NULL DEFAULT '',
    nummer_normalisiert VARCHAR(40) NULL,
    dauer_sekunden INT NOT NULL DEFAULT 0,
    datei_name VARCHAR(100) NOT NULL,
    abgehoert_am DATETIME NULL,
    abgehoert_von BIGINT NULL,
    anruf_id BIGINT NULL,
    kunde_id BIGINT NULL,
    lieferant_id BIGINT NULL,
    zuordnung ENUM('AUTOMATISCH','MANUELL','KEINE') NOT NULL DEFAULT 'KEINE',
    angelegt_am DATETIME NOT NULL,
    CONSTRAINT uk_sprachnachricht UNIQUE (anrufbeantworter, zeitpunkt, nummer_roh),
    CONSTRAINT fk_sprachnachricht_abgehoert_von FOREIGN KEY (abgehoert_von) REFERENCES frontend_user_profile(id) ON DELETE SET NULL,
    CONSTRAINT fk_sprachnachricht_anruf FOREIGN KEY (anruf_id) REFERENCES telefon_anruf(id) ON DELETE SET NULL,
    CONSTRAINT fk_sprachnachricht_kunde FOREIGN KEY (kunde_id) REFERENCES kunde(id) ON DELETE SET NULL,
    CONSTRAINT fk_sprachnachricht_lieferant FOREIGN KEY (lieferant_id) REFERENCES lieferanten(id) ON DELETE SET NULL,
    INDEX idx_sprachnachricht_nummer (nummer_normalisiert),
    INDEX idx_sprachnachricht_zeitpunkt (zeitpunkt)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS kontakt_rufnummer (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    kunde_id BIGINT NULL,
    lieferant_id BIGINT NULL,
    nummer_roh VARCHAR(40) NOT NULL,
    nummer_normalisiert VARCHAR(40) NOT NULL,
    angelegt_am DATETIME NOT NULL,
    CONSTRAINT fk_kontakt_rufnummer_kunde FOREIGN KEY (kunde_id) REFERENCES kunde(id) ON DELETE CASCADE,
    CONSTRAINT fk_kontakt_rufnummer_lieferant FOREIGN KEY (lieferant_id) REFERENCES lieferanten(id) ON DELETE CASCADE,
    CONSTRAINT ck_kontakt_rufnummer_genau_ein_kontakt CHECK (
        (kunde_id IS NOT NULL AND lieferant_id IS NULL) OR (kunde_id IS NULL AND lieferant_id IS NOT NULL)),
    INDEX idx_kontakt_rufnummer_nummer (nummer_normalisiert)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET @col_exists := (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'abteilung' AND column_name = 'darf_telefon_sehen');
SET @sql := IF(@col_exists = 0, 'ALTER TABLE abteilung ADD COLUMN darf_telefon_sehen BOOLEAN NOT NULL DEFAULT FALSE', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
