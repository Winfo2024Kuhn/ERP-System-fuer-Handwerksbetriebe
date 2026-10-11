-- Abwesenheitsnotizen gelten pro Postfach.
--
-- WARUM
-- Bisher gab es Abwesenheitsnotizen nur fuer den ganzen Betrieb: Eine aktive Notiz hat auf
-- jede eingehende Mail geantwortet, egal in welchem Postfach, immer ueber das Hauptpostfach.
-- Jetzt waehlt man pro Notiz die Postfaecher aus (Mitarbeiter: das eigene und allgemeine wie
-- info@; Admins: alle). Geantwortet wird nur auf Mails in diesen Postfaechern – und zwar aus
-- dem Postfach, in dem die Mail ankam.
--
-- WAS
--   1. out_of_office_schedule_postfach: welche Postfaecher eine Notiz abdeckt.
--   2. Bestehende Notizen bekommen alle allgemeinen Postfaecher: eingeschaltet, laeuft nicht
--      aus, kein reines Rechnungs-Postfach (ausser es ist das Hauptpostfach) und keinem
--      Benutzer als eigenes Postfach zugeordnet.
--
-- ROBUSTHEIT
-- Tabelle nur, wenn sie fehlt. Der Uebertrag fuellt nur Notizen, die noch gar kein Postfach
-- haben, und fuegt keine Paare doppelt ein – mehrfaches Ausfuehren aendert nichts. Wird eine
-- Notiz oder ein Postfach geloescht, verschwindet die Zuordnung mit (ON DELETE CASCADE).

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
