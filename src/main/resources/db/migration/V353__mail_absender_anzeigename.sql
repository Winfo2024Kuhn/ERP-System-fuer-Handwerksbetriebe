-- Anzeigename des Absenders ("Bauschlosserei Kuhn <rechnungen@...>").
--
-- Bisher ging bei jeder vom System versendeten Mail nur die nackte Adresse
-- raus. Im Posteingang des Kunden steht dann "rechnungen@..." statt des
-- Firmennamens. Das wirkt anonym, der Kunde erkennt den Absender schlechter,
-- und Spamfilter bewerten Absender ohne Anzeigenamen etwas schlechter.
--
-- Zwei Werte, weil es zwei Postfaecher gibt:
--   mail.absender-name           -> Standard-Postfach (Schriftverkehr,
--                                   Auftragsbestaetigungen ohne eigenes Konto)
--   mail.dokumente.absender-name -> Postfach fuer Ausgangsgeschaeftsdokumente
--
-- Beide bleiben leer: Ohne Eintrag verhaelt sich der Versand exakt wie bisher
-- (nur Adresse, kein Name). Gepflegt wird ueber die System-Einstellungen.
--
-- Hinweis: An den einzelnen Absender-Adressen (Tabelle email_absender) gibt es
-- bereits eine Spalte `anzeigename`. Die wurde bisher nur angezeigt, aber nie
-- beim Versand verwendet; ab jetzt schlaegt sie fuer die jeweilige Adresse den
-- hier gepflegten Standardwert.
--
-- Idempotent: INSERT IGNORE ueberspringt bereits vorhandene Schluessel.

INSERT IGNORE INTO system_setting (setting_key, setting_value, beschreibung)
VALUES
    ('mail.absender-name', '',
     'Anzeigename des Absenders fuer Mails ueber das Standard-Postfach (leer = nur Adresse)'),
    ('mail.dokumente.absender-name', '',
     'Anzeigename des Absenders fuer Ausgangsgeschaeftsdokumente (leer = nur Adresse)');
