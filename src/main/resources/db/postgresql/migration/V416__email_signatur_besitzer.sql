-- E-Mail-Signaturen: Besitzer und Formulardaten des Signatur-Studios.
--
-- WARUM
-- Bisher sah jeder angemeldete Benutzer alle Signaturen aller Kollegen. Ab jetzt gehoert jede
-- Signatur einem Benutzer: Benutzer sehen und bearbeiten nur ihre eigenen, Admins sehen alle.
-- Das Signatur-Studio baut die Signatur aus einem Formular (Vorlage, Name, Telefon, Bild-Links
-- ...). Damit man sie spaeter wieder im Formular bearbeiten kann, wird der Formularzustand als
-- JSON mitgespeichert.
--
-- WAS
--   besitzer_id    Benutzer (frontend_user_profile), dem die Signatur gehoert.
--                  NULL = ohne Besitzer – nur Admins sehen sie. Bestehende Signaturen bleiben
--                  bewusst ohne Besitzer, der Admin verteilt sie.
--                  Wird der Benutzer geloescht, bleibt die Signatur ohne Besitzer stehen.
--   vorlage_daten  JSON mit dem Formularzustand des Studios. NULL = von Hand geschriebenes HTML.
--
-- ROBUSTHEIT
-- Nur ADD COLUMN IF NOT EXISTS, Fremdschluessel und Index nur, wenn sie fehlen – ein zweiter
-- Lauf aendert nichts.

ALTER TABLE email_signature ADD COLUMN IF NOT EXISTS besitzer_id BIGINT;
ALTER TABLE email_signature ADD COLUMN IF NOT EXISTS vorlage_daten TEXT;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_email_signature_besitzer') THEN
        ALTER TABLE email_signature
            ADD CONSTRAINT fk_email_signature_besitzer FOREIGN KEY (besitzer_id)
            REFERENCES frontend_user_profile (id) ON DELETE SET NULL;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_email_signature_besitzer ON email_signature (besitzer_id);
