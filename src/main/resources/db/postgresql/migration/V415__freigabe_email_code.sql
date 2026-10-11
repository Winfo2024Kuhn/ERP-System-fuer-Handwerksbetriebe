-- Bestätigungscode per E-Mail für die digitale Freigabe.
--
-- WARUM
-- Bisher reichte der Freigabe-Link allein, um ein Angebot verbindlich anzunehmen. Wer den Link
-- in die Hände bekam (weitergeleitete Mail, Chat, Browserverlauf), konnte mit beliebigem Namen
-- freigeben. Jetzt schickt das System beim Klick auf "Freigeben" einen 6-stelligen Einmal-Code
-- an die Adresse, an die das Dokument ging. Erst mit diesem Code gilt die Annahme.
--
-- WAS
--   code_hash               HMAC-SHA256 über UUID und Code - der Code selbst wird nie gespeichert.
--                           NULL = kein gültiger Code offen (nie angefordert, verbraucht, gesperrt).
--   code_gueltig_bis        Ablauf des aktuellen Codes (15 Minuten nach Versand).
--   code_fehlversuche       Falsche Eingaben für den aktuellen Code; nach 5 geht automatisch ein neuer raus.
--   code_fehlversuche_gesamt Falsche Eingaben über alle Codes; nach 15 ist die Online-Freigabe gesperrt
--                           (Schutz gegen Raten über viele Codes). Das Büro verschickt dann neu.
--   code_angefordert_am     Letzter Versand - Grundlage der 30-Sekunden-Sperre.
--   code_fenster_start      Beginn des Stundenfensters für das Versandlimit.
--   code_fenster_anzahl     Versendete Codes im aktuellen Stundenfenster (höchstens 5).
--   code_bestaetigt_am      Beweissicherung: Zeitpunkt der Code-Bestätigung bei der Annahme.
--   code_bestaetigt_email   Beweissicherung: Adresse, an die der bestätigte Code ging.
--   kunde_email_aus_versand TRUE, sobald kunde_email aus dem ersten echten Versand der Angebots-Mail
--                           übernommen wurde. Danach ist der Empfänger fest – eine spätere Weiterleitung
--                           oder neue Mail mit demselben Link ändert ihn nicht mehr.
--
-- ROBUSTHEIT
-- Nur ADD COLUMN IF NOT EXISTS - ein zweiter Lauf ändert nichts. Bestehende Freigaben bekommen
-- Zähler 0 und keinen Code; sie fordern beim nächsten Klick einen an.

ALTER TABLE dokument_freigabe ADD COLUMN IF NOT EXISTS code_hash VARCHAR(64);
ALTER TABLE dokument_freigabe ADD COLUMN IF NOT EXISTS code_gueltig_bis TIMESTAMP(6);
ALTER TABLE dokument_freigabe ADD COLUMN IF NOT EXISTS code_fehlversuche INTEGER NOT NULL DEFAULT 0;
ALTER TABLE dokument_freigabe ADD COLUMN IF NOT EXISTS code_fehlversuche_gesamt INTEGER NOT NULL DEFAULT 0;
ALTER TABLE dokument_freigabe ADD COLUMN IF NOT EXISTS code_angefordert_am TIMESTAMP(6);
ALTER TABLE dokument_freigabe ADD COLUMN IF NOT EXISTS code_fenster_start TIMESTAMP(6);
ALTER TABLE dokument_freigabe ADD COLUMN IF NOT EXISTS code_fenster_anzahl INTEGER NOT NULL DEFAULT 0;
ALTER TABLE dokument_freigabe ADD COLUMN IF NOT EXISTS code_bestaetigt_am TIMESTAMP(6);
ALTER TABLE dokument_freigabe ADD COLUMN IF NOT EXISTS code_bestaetigt_email VARCHAR(255);
ALTER TABLE dokument_freigabe ADD COLUMN IF NOT EXISTS kunde_email_aus_versand BOOLEAN NOT NULL DEFAULT FALSE;
