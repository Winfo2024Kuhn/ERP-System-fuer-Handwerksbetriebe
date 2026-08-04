-- Eigenes Mail-Konto fuer den Versand von Ausgangsgeschaeftsdokumenten
-- (Rechnungen, Mahnungen, Auftrags- und Anfragebestaetigungen).
--
-- Hintergrund: Der bisherige Versand laeuft ueber ein t-online-Postfach.
-- Die Empfaenger-Spamfilter bewerten geschaeftliche Post mit PDF-Anhang von
-- einer Privatkunden-Domain deutlich schaerfer, weil SPF und DKIM dort dem
-- Provider gehoeren und nicht dem Absender. Mit einem Postfach auf der
-- eigenen Firmendomain (inkl. eigenem SPF- und DKIM-Eintrag) faellt dieser
-- Nachteil weg.
--
-- Bewusst ein zweites, getrenntes Konto: Der manuelle Schriftverkehr im
-- E-Mail-Center und der IMAP-Abruf bleiben unveraendert auf dem bisherigen
-- Postfach. Nur der Dokumentversand wechselt.
--
-- Alle Werte bleiben leer und werden ueber die System-Einstellungen im
-- Frontend gepflegt (analog smtp.* und imap.*) — niemals im Code oder in
-- einer eingecheckten Properties-Datei. Solange 'smtp.dokumente.aktiv' auf
-- 'false' steht oder Pflichtfelder fehlen, faellt der Versand auf das
-- Standard-Konto zurueck (siehe SystemSettingsService#getDokumentMailKonto).
--
-- Idempotent: INSERT IGNORE ueberspringt bereits vorhandene Schluessel.

INSERT IGNORE INTO system_setting (setting_key, setting_value, beschreibung)
VALUES
    ('smtp.dokumente.aktiv', 'false',
     'Eigenes Mail-Konto fuer Ausgangsgeschaeftsdokumente verwenden (false = Standard-Konto)'),
    ('smtp.dokumente.host', '',
     'SMTP Mail-Server fuer Ausgangsgeschaeftsdokumente'),
    ('smtp.dokumente.port', '465',
     'SMTP Port fuer Ausgangsgeschaeftsdokumente (465 = SSL)'),
    ('smtp.dokumente.username', '',
     'SMTP Benutzername / E-Mail-Adresse fuer Ausgangsgeschaeftsdokumente'),
    ('smtp.dokumente.password', '',
     'SMTP Passwort fuer Ausgangsgeschaeftsdokumente'),
    ('mail.dokumente.from-address', '',
     'Sichtbare Absender-Adresse fuer Ausgangsgeschaeftsdokumente (leer = SMTP-Benutzer)');
