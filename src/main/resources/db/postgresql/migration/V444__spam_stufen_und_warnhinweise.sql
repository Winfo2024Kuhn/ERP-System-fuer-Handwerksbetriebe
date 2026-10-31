-- Spam-Erkennung in drei Stufen, Erkennung gefälschter Absender und Abzocke-Warnungen.
--
-- WARUM
-- Seit V443 wandert Spam auch auf dem Mail-Server in den Spam-Ordner. Ein Fehlalarm kostet damit
-- echte Post. Nur noch sehr sichere Fälle werden Spam; was verdächtig, aber nicht sicher ist,
-- bleibt als „Vielleicht Spam“ im Posteingang, bis jemand entscheidet.
--
-- WAS
--   email.from_name           Anzeigename des Absenders („Sparkasse“ <x@irgendwo.example>), für die
--                             Erkennung gefälschter Absender.
--   email.anbieter_spam_flag  TRUE, wenn der Mail-Anbieter „X-Spam-Flag: YES“ gesetzt hat; NULL = Header fehlte.
--   email.spam_stufe          SPAM / UNSICHER / KEIN_SPAM (Java-Enum SpamStufe, daher VARCHAR ohne CHECK);
--                             NULL = vor dieser Version bewertet.
--   email.spam_gruende        Kürzel der erkannten Signale, kommagetrennt (Anzeige „Warum?“).
--   email.warnhinweis         Abzocke-Art (Java-Enum AbzockeArt), z. B. BRANCHENBUCH; verschiebt nie etwas.
--   email.spam_token_version  Version des Zerlegers, mit dem die Mail trainiert wurde; NULL = Version 1.
--                             Nötig, damit „Spam“/„Kein Spam“ beim Umentscheiden genau die Zähler zurücknimmt,
--                             die beim Training hochgezählt wurden.
--
-- ROBUSTHEIT
-- Nur ADD COLUMN IF NOT EXISTS, keine Rückfüllung: Alte Mails werden bewusst nicht neu bewertet.
-- Ein zweiter Lauf ändert nichts.

ALTER TABLE email ADD COLUMN IF NOT EXISTS from_name VARCHAR(255);
ALTER TABLE email ADD COLUMN IF NOT EXISTS anbieter_spam_flag BOOLEAN;
ALTER TABLE email ADD COLUMN IF NOT EXISTS spam_stufe VARCHAR(20);
ALTER TABLE email ADD COLUMN IF NOT EXISTS spam_gruende VARCHAR(500);
ALTER TABLE email ADD COLUMN IF NOT EXISTS warnhinweis VARCHAR(40);
ALTER TABLE email ADD COLUMN IF NOT EXISTS spam_token_version SMALLINT;
