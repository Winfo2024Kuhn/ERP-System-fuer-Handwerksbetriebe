-- E-Mail-Postfaecher, Etappe 2: Wer darf welches Postfach sehen?
-- PostgreSQL-Zwilling von db/migration/V408__email_postfach_sichtbarkeit.sql (Erklaerung dort).
-- Idempotent ueber IF NOT EXISTS.

ALTER TABLE email_absender ADD COLUMN IF NOT EXISTS sichtbar_fuer_alle BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE IF NOT EXISTS email_absender_abteilung (
    postfach_id BIGINT NOT NULL,
    abteilung_id BIGINT NOT NULL,
    PRIMARY KEY (postfach_id, abteilung_id),
    CONSTRAINT fk_email_absender_abteilung_postfach FOREIGN KEY (postfach_id) REFERENCES email_absender (id) ON DELETE CASCADE,
    CONSTRAINT fk_email_absender_abteilung_abteilung FOREIGN KEY (abteilung_id) REFERENCES abteilung (id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_email_absender_abteilung_abteilung ON email_absender_abteilung (abteilung_id);

CREATE TABLE IF NOT EXISTS email_absender_benutzer (
    postfach_id BIGINT NOT NULL,
    frontend_user_profile_id BIGINT NOT NULL,
    PRIMARY KEY (postfach_id, frontend_user_profile_id),
    CONSTRAINT fk_email_absender_benutzer_postfach FOREIGN KEY (postfach_id) REFERENCES email_absender (id) ON DELETE CASCADE,
    CONSTRAINT fk_email_absender_benutzer_benutzer FOREIGN KEY (frontend_user_profile_id) REFERENCES frontend_user_profile (id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_email_absender_benutzer_benutzer ON email_absender_benutzer (frontend_user_profile_id);

ALTER TABLE email_absender ADD COLUMN IF NOT EXISTS laeuft_aus BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE email_draft ADD COLUMN IF NOT EXISTS weitergeleitet_von_email_id BIGINT;
