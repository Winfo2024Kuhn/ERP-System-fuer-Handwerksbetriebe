-- E-Mail-Signaturen: Firmen-Vorlagen.
--
-- WARUM
-- Der Admin legt das Design der Signaturen fest (Aufbau, Farbe, Logo, Firmenangaben), die
-- Mitarbeiter bauen darauf ihre eigene Signatur mit ihren persoenlichen Angaben. Die bisherige
-- Zuweisung einer fertigen Signatur in der Benutzerverwaltung entfaellt dafuer.
--
-- WAS
--   firmen_vorlage     TRUE = diese Signatur ist eine Firmen-Vorlage, alle Benutzer sehen sie
--                      im Signatur-Studio als Ausgangspunkt.
--   firmen_vorlage_id  Firmen-Vorlage, aus der die Signatur gebaut wurde. NULL = freies Design
--                      (alle bestehenden Signaturen bleiben so, wie sie sind).
--                      Wird die Vorlage geloescht, bleibt die Signatur ohne Verweis stehen.
--
-- ROBUSTHEIT
-- Nur ADD COLUMN IF NOT EXISTS, Fremdschluessel und Index nur, wenn sie fehlen – ein zweiter
-- Lauf aendert nichts.

ALTER TABLE email_signature ADD COLUMN IF NOT EXISTS firmen_vorlage BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE email_signature ADD COLUMN IF NOT EXISTS firmen_vorlage_id BIGINT;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_email_signature_firmen_vorlage') THEN
        ALTER TABLE email_signature
            ADD CONSTRAINT fk_email_signature_firmen_vorlage FOREIGN KEY (firmen_vorlage_id)
            REFERENCES email_signature (id) ON DELETE SET NULL;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_email_signature_firmen_vorlage_id ON email_signature (firmen_vorlage_id);
