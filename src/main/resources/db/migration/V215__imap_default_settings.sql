-- IMAP-Standardwerte für die System-Einstellungen.
-- Bisher wurden IMAP-Zugangsdaten nur aus Umgebungsvariablen gelesen
-- (IMAP_USER/IMAP_PASSWORD/IMAP_HOST). Ab jetzt liegen alle Werte in der
-- DB-Tabelle `system_setting` und sind über die UI änderbar.
--
-- Diese Migration legt nur die Default-Host/Port-Einträge an. Benutzername
-- und Passwort bleiben leer und müssen in der UI hinterlegt werden – fällt
-- automatisch auf den SMTP-Benutzer zurück, falls leer (siehe
-- SystemSettingsService#getImapUsername / #getImapPassword).
--
-- Idempotent: INSERT IGNORE überspringt bereits vorhandene Schlüssel.

INSERT IGNORE INTO system_setting (setting_key, setting_value, beschreibung)
VALUES
    ('imap.host', 'secureimap.t-online.de', 'IMAP Mail-Server Hostname'),
    ('imap.port', '993', 'IMAP Port (993 = SSL)'),
    ('imap.username', '', 'IMAP Benutzername / E-Mail-Adresse'),
    ('imap.password', '', 'IMAP Passwort');
