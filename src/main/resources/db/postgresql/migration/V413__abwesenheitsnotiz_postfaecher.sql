-- Abwesenheitsnotizen gelten pro Postfach.
-- PostgreSQL-Zwilling von db/migration/V413__abwesenheitsnotiz_postfaecher.sql (Erklaerung dort).
-- Idempotent ueber IF NOT EXISTS; der Uebertrag fuellt nur Notizen ohne Postfach.

CREATE TABLE IF NOT EXISTS out_of_office_schedule_postfach (
    schedule_id BIGINT NOT NULL,
    postfach_id BIGINT NOT NULL,
    PRIMARY KEY (schedule_id, postfach_id),
    CONSTRAINT fk_ooo_schedule_postfach_schedule FOREIGN KEY (schedule_id) REFERENCES out_of_office_schedule (id) ON DELETE CASCADE,
    CONSTRAINT fk_ooo_schedule_postfach_postfach FOREIGN KEY (postfach_id) REFERENCES email_absender (id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_ooo_schedule_postfach_postfach ON out_of_office_schedule_postfach (postfach_id);

INSERT INTO out_of_office_schedule_postfach (schedule_id, postfach_id)
SELECT s.id, p.id
FROM out_of_office_schedule s
JOIN email_absender p
  ON p.aktiv = TRUE
 AND p.laeuft_aus = FALSE
 AND (p.fuer_geschaeftsdokumente = FALSE OR p.hauptpostfach = TRUE)
WHERE NOT EXISTS (SELECT 1 FROM out_of_office_schedule_postfach z WHERE z.schedule_id = s.id)
  AND NOT EXISTS (SELECT 1 FROM frontend_user_profile u WHERE u.email_absender_id = p.id);
