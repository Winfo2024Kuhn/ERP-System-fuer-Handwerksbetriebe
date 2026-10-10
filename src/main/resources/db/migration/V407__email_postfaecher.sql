-- E-Mail-Postfaecher: Absender-Adressen werden zu echten Postfaechern mit eigenem Zugang
--
-- WARUM
-- Bisher gab es genau ein IMAP-/SMTP-Konto (System-Einstellungen) plus ein optionales
-- Versandkonto fuer Rechnungen und Mahnungen. Die Tabelle email_absender kannte nur
-- Absender-Adressen ohne Zugang. Jetzt bekommt jede Adresse optional einen eigenen Zugang
-- (Abruf + Versand), ein Postfach wird Hauptpostfach (z. B. info@) und hoechstens eins
-- verschickt Geschaeftsdokumente. Jede Mail merkt sich, in welchen Postfaechern sie liegt.
--
-- WAS
--   1. email_absender: Zugangs-Spalten, Haken hauptpostfach / fuer_geschaeftsdokumente,
--      Abruf-Status. Das Passwort steht nur verschluesselt (MailSecretService) in der DB.
--   2. email_draft: gewaehltes Absender-Postfach und Einzelversand.
--   3. Neue Tabelle email_postfach_zuordnung (Mail <-> Postfach, n:m mit IMAP-Ordner/UID).
--
-- WAS NICHT PASSIERT
-- Die bestehenden Konten aus den System-Einstellungen werden hier NICHT umgezogen: das
-- Passwort muss verschluesselt werden, das kann nur der Java-Code (PostfachUmzugRunner,
-- laeuft bei jedem Start idempotent). Die Alt-Einstellungen bleiben als Rueckfall stehen.
--
-- ROBUSTHEIT
-- Jede Spalte, jeder Index und jede Tabelle wird nur angelegt, wenn sie fehlt
-- (information_schema + PREPARE/EXECUTE wie in V400). Mehrfaches Ausfuehren aendert nichts.

SET @col := (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'email_absender' AND column_name = 'benutzername');
SET @sql := IF(@col = 0, 'ALTER TABLE email_absender ADD COLUMN benutzername VARCHAR(255) NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'email_absender' AND column_name = 'passwort_verschluesselt');
SET @sql := IF(@col = 0, 'ALTER TABLE email_absender ADD COLUMN passwort_verschluesselt TEXT NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'email_absender' AND column_name = 'smtp_host');
SET @sql := IF(@col = 0, 'ALTER TABLE email_absender ADD COLUMN smtp_host VARCHAR(255) NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'email_absender' AND column_name = 'smtp_port');
SET @sql := IF(@col = 0, 'ALTER TABLE email_absender ADD COLUMN smtp_port INT NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'email_absender' AND column_name = 'imap_host');
SET @sql := IF(@col = 0, 'ALTER TABLE email_absender ADD COLUMN imap_host VARCHAR(255) NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'email_absender' AND column_name = 'imap_port');
SET @sql := IF(@col = 0, 'ALTER TABLE email_absender ADD COLUMN imap_port INT NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'email_absender' AND column_name = 'hauptpostfach');
SET @sql := IF(@col = 0, 'ALTER TABLE email_absender ADD COLUMN hauptpostfach BOOLEAN NOT NULL DEFAULT FALSE', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'email_absender' AND column_name = 'fuer_geschaeftsdokumente');
SET @sql := IF(@col = 0, 'ALTER TABLE email_absender ADD COLUMN fuer_geschaeftsdokumente BOOLEAN NOT NULL DEFAULT FALSE', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'email_absender' AND column_name = 'letzter_abruf_am');
SET @sql := IF(@col = 0, 'ALTER TABLE email_absender ADD COLUMN letzter_abruf_am DATETIME NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'email_absender' AND column_name = 'letzter_abruf_fehler');
SET @sql := IF(@col = 0, 'ALTER TABLE email_absender ADD COLUMN letzter_abruf_fehler VARCHAR(500) NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Entwuerfe merken sich das gewaehlte Absender-Postfach und den Einzelversand.
SET @col := (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'email_draft' AND column_name = 'postfach_id');
SET @sql := IF(@col = 0, 'ALTER TABLE email_draft ADD COLUMN postfach_id BIGINT NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'email_draft' AND column_name = 'einzelversand');
SET @sql := IF(@col = 0, 'ALTER TABLE email_draft ADD COLUMN einzelversand BOOLEAN NOT NULL DEFAULT FALSE', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS email_postfach_zuordnung (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    email_id BIGINT NOT NULL,
    postfach_id BIGINT NOT NULL,
    imap_ordner VARCHAR(255) NULL,
    imap_uid BIGINT NULL,
    CONSTRAINT uk_email_postfach_zuordnung UNIQUE (email_id, postfach_id),
    CONSTRAINT fk_email_postfach_zuordnung_email FOREIGN KEY (email_id) REFERENCES email (id) ON DELETE CASCADE,
    CONSTRAINT fk_email_postfach_zuordnung_postfach FOREIGN KEY (postfach_id) REFERENCES email_absender (id),
    INDEX idx_email_postfach_zuordnung_postfach (postfach_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
