-- E-Mail-Postfaecher, Etappe 2: Wer darf welches Postfach sehen?
--
-- WARUM
-- Bisher sieht jeder im E-Mail-Center alle Mails. Jetzt kann der Admin pro Postfach
-- festlegen, wer es sieht: alle im Betrieb (Standard) oder nur bestimmte Abteilungen
-- und/oder Benutzer. Das Hauptpostfach sieht immer jeder, Admins sehen alles – das regelt
-- der Java-Code (PostfachSichtbarkeitService), nicht die Datenbank.
--
-- WAS
--   1. email_absender.sichtbar_fuer_alle (Standard TRUE: bestehende Postfaecher bleiben
--      fuer alle sichtbar, es aendert sich beim Einspielen nichts).
--   2. email_absender_abteilung: Freigabe eines Postfachs fuer Abteilungen.
--   3. email_absender_benutzer: Freigabe eines Postfachs fuer einzelne Benutzer.
--   4. email_draft.weitergeleitet_von_email_id: Ein Weiterleitungs-Entwurf behaelt beim
--      Wiederoeffnen den festen Absender der Original-Mail.
--   5. email_absender.laeuft_aus (Standard FALSE): Ein Postfach, das auslaufen soll (z. B. die
--      alte T-Online-Adresse). Mails kommen weiter an, Antworten gehen aber ueber das
--      Hauptpostfach, und fuer neue Mails ist es nicht mehr waehlbar.
--
-- ROBUSTHEIT
-- Spalten und Tabellen werden nur angelegt, wenn sie fehlen (information_schema +
-- PREPARE/EXECUTE wie in V407). Mehrfaches Ausfuehren aendert nichts. Wird ein Postfach,
-- eine Abteilung oder ein Benutzer geloescht, verschwinden die Freigaben mit (ON DELETE CASCADE).

SET @col := (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'email_absender' AND column_name = 'sichtbar_fuer_alle');
SET @sql := IF(@col = 0, 'ALTER TABLE email_absender ADD COLUMN sichtbar_fuer_alle BOOLEAN NOT NULL DEFAULT TRUE', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS email_absender_abteilung (
    postfach_id BIGINT NOT NULL,
    abteilung_id BIGINT NOT NULL,
    PRIMARY KEY (postfach_id, abteilung_id),
    CONSTRAINT fk_email_absender_abteilung_postfach FOREIGN KEY (postfach_id) REFERENCES email_absender (id) ON DELETE CASCADE,
    CONSTRAINT fk_email_absender_abteilung_abteilung FOREIGN KEY (abteilung_id) REFERENCES abteilung (id) ON DELETE CASCADE,
    INDEX idx_email_absender_abteilung_abteilung (abteilung_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS email_absender_benutzer (
    postfach_id BIGINT NOT NULL,
    frontend_user_profile_id BIGINT NOT NULL,
    PRIMARY KEY (postfach_id, frontend_user_profile_id),
    CONSTRAINT fk_email_absender_benutzer_postfach FOREIGN KEY (postfach_id) REFERENCES email_absender (id) ON DELETE CASCADE,
    CONSTRAINT fk_email_absender_benutzer_benutzer FOREIGN KEY (frontend_user_profile_id) REFERENCES frontend_user_profile (id) ON DELETE CASCADE,
    INDEX idx_email_absender_benutzer_benutzer (frontend_user_profile_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET @col := (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'email_absender' AND column_name = 'laeuft_aus');
SET @sql := IF(@col = 0, 'ALTER TABLE email_absender ADD COLUMN laeuft_aus BOOLEAN NOT NULL DEFAULT FALSE', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'email_draft' AND column_name = 'weitergeleitet_von_email_id');
SET @sql := IF(@col = 0, 'ALTER TABLE email_draft ADD COLUMN weitergeleitet_von_email_id BIGINT NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
