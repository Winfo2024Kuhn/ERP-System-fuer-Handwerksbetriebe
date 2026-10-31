-- Spam auch auf dem Mail-Server in den Spam-Ordner verschieben.
--
-- WARUM
-- Das ERP erkennt Spam gut (Regeln + Bayes), markiert ihn aber nur im ERP. Auf dem Server blieb
-- die Mail im Posteingang und tauchte am Handy und in Outlook weiter auf. Ein Hintergrund-Abgleich
-- (ServerSpamAbgleichService) verschiebt Spam jetzt auch auf dem Server und holt ihn bei
-- „Kein Spam“ zurück in den Posteingang.
--
-- WAS
--   email_absender.spam_auf_server_verschieben  Schalter je Postfach, Standard an.
--   email_absender.spam_ordner                  von Hand gewählter Spam-Ordner; NULL = automatisch erkennen.
--   email_absender.spam_ordner_erkannt          Ordner, den der letzte Abgleich benutzt hat (Anzeige).
--   email_absender.spam_abgleich_fehler         verständlicher Satz, wenn der letzte Abgleich scheiterte.
--   email_postfach_zuordnung.server_spam_status VERSCHOBEN = vom ERP in den Spam-Ordner verschoben,
--                                               NICHT_GEFUNDEN = auf dem Server nicht (mehr) da, nicht erneut versuchen,
--                                               FEHLGESCHLAGEN = vom Server mehrfach abgelehnt, nicht erneut versuchen.
--                                               Java-Enum ServerSpamStatus, daher VARCHAR ohne CHECK.
--
-- ROBUSTHEIT
-- ADD COLUMN / CREATE INDEX IF NOT EXISTS - ein zweiter Lauf ändert nichts.

ALTER TABLE email_absender ADD COLUMN IF NOT EXISTS spam_auf_server_verschieben BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE email_absender ADD COLUMN IF NOT EXISTS spam_ordner VARCHAR(255);
ALTER TABLE email_absender ADD COLUMN IF NOT EXISTS spam_ordner_erkannt VARCHAR(255);
ALTER TABLE email_absender ADD COLUMN IF NOT EXISTS spam_abgleich_fehler VARCHAR(500);

ALTER TABLE email_postfach_zuordnung ADD COLUMN IF NOT EXISTS server_spam_status VARCHAR(30);

-- Der Abgleich fragt alle zwei Minuten nach offenem Spam und nach zurückzuholenden Mails.
CREATE INDEX IF NOT EXISTS idx_email_spam_offen ON email (id) WHERE is_spam = TRUE AND deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_email_postfach_zuordnung_spam_verschoben
    ON email_postfach_zuordnung (postfach_id) WHERE server_spam_status = 'VERSCHOBEN';
