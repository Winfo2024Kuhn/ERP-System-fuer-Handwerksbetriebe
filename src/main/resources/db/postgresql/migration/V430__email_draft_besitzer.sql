-- E-Mail-Entwürfe bekommen einen Besitzer.
--
-- WARUM
-- Bisher sah jeder Benutzer alle Entwürfe (Issue #271, Punkt 2). Ein angefangener Entwurf
-- ist aber persönlich: Künftig sieht ihn nur, wer ihn angelegt hat. Admins sehen alle.
--
-- WAS
--   email_draft.besitzer_id  Benutzer (frontend_user_profile), der den Entwurf angelegt hat
--
-- ALTE ENTWÜRFE
-- Bestehende Entwürfe bleiben ohne Besitzer und damit weiter für alle sichtbar, damit nichts
-- verschwindet. Wird ein Benutzer gelöscht, verlieren seine Entwürfe nur den Besitzer
-- (ON DELETE SET NULL) und sind dann wieder für alle sichtbar.
--
-- ROBUSTHEIT
-- ADD COLUMN / CREATE INDEX IF NOT EXISTS; der Fremdschlüssel wird nur angelegt, wenn es ihn
-- noch nicht gibt.

ALTER TABLE email_draft ADD COLUMN IF NOT EXISTS besitzer_id BIGINT;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_email_draft_besitzer') THEN
        ALTER TABLE email_draft
            ADD CONSTRAINT fk_email_draft_besitzer
            FOREIGN KEY (besitzer_id) REFERENCES frontend_user_profile (id) ON DELETE SET NULL;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_email_draft_besitzer ON email_draft (besitzer_id);
