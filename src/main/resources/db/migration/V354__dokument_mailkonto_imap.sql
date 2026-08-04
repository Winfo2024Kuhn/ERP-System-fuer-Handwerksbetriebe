-- Posteingangs-Server (IMAP) des Postfachs fuer Ausgangsgeschaeftsdokumente.
--
-- Hintergrund: Jede versendete Mail wird zusaetzlich im "Gesendet"-Ordner beim
-- Provider abgelegt (SentMailArchiver) — ein vom ERP unabhaengiger Nachweis mit
-- dem Zeitstempel des Providers. Diese Ablage lief bisher immer ueber die
-- IMAP-Zugangsdaten des Standard-Postfachs. Seit Rechnungen und Mahnungen ueber
-- ein eigenes Postfach rausgehen, landete die Kopie damit im falschen Postfach:
-- versendet bei Anbieter A, abgelegt bei Anbieter B.
--
-- Ab jetzt liegt die Kopie in dem Postfach, das die Mail auch verschickt hat.
--
-- Benutzername und Passwort werden bewusst NICHT getrennt gepflegt: Es ist
-- dasselbe Postfach wie beim Versand, die SMTP-Zugangsdaten gelten auch fuer
-- den Posteingang (siehe SystemSettingsService#getDokumentImapZugang).
--
-- Der Host bleibt leer und faellt dann auf den SMTP-Host zurueck — bei den
-- meisten Anbietern bedient derselbe Servername beide Richtungen. Nur wenn ein
-- Anbieter davon abweicht, muss hier etwas eingetragen werden.
--
-- Idempotent: INSERT IGNORE ueberspringt bereits vorhandene Schluessel.

INSERT IGNORE INTO system_setting (setting_key, setting_value, beschreibung)
VALUES
    ('imap.dokumente.host', '',
     'Posteingangs-Server des Dokument-Postfachs (leer = derselbe wie beim Versand)'),
    ('imap.dokumente.port', '993',
     'IMAP Port des Dokument-Postfachs (993 = SSL)');
