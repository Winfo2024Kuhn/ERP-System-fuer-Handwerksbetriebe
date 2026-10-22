-- Empfänger (An/CC) und Betreff einer E-Mail ohne Längengrenze speichern.
-- Bisher VARCHAR(1000): Ein Newsletter mit langer Empfängerliste ließ sich nicht
-- importieren ("value too long for type character varying(1000)") und wurde bei
-- jedem Abruf erneut versucht. Auf den Spalten liegt kein Index.
-- Idempotent: TEXT -> TEXT ändert nichts.

ALTER TABLE email ALTER COLUMN recipient TYPE TEXT;
ALTER TABLE email ALTER COLUMN cc TYPE TEXT;
ALTER TABLE email ALTER COLUMN subject TYPE TEXT;
