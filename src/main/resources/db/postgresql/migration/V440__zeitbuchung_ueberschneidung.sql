-- Überschneidungen zwischen Handy-Buchungen und Einträgen, die schon im Büro standen.
-- "Handy gewinnt": Kommt eine (oft offline gestempelte) Handy-Buchung an, werden
-- überlappende Büro-Einträge gekürzt, geteilt oder entfernt. Jede Zeile hier ist eine
-- Meldung dazu – für die Glocke am PC und die Nachrichten am Handy des Mitarbeiters.
-- Die Buchungs-IDs sind bewusst ohne Fremdschlüssel: Ein entfernter Büro-Eintrag
-- soll die Meldung nicht mitlöschen (der Stand davor steht in buero_von/buero_bis).
-- Idempotent: IF NOT EXISTS.

CREATE TABLE IF NOT EXISTS zeitbuchung_ueberschneidung (
    id BIGSERIAL PRIMARY KEY,
    mitarbeiter_id BIGINT NOT NULL REFERENCES mitarbeiter (id) ON DELETE CASCADE,
    handy_buchung_id BIGINT,
    handy_von TIMESTAMP NOT NULL,
    handy_bis TIMESTAMP,
    buero_buchung_id BIGINT,
    rest_buchung_id BIGINT,
    buero_von TIMESTAMP NOT NULL,
    buero_bis TIMESTAMP,
    buero_bezeichnung VARCHAR(500),
    ergebnis VARCHAR(30) NOT NULL,
    erstellt_am TIMESTAMP NOT NULL,
    geaendert_am TIMESTAMP NOT NULL,
    mitarbeiter_gesehen_am TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_zb_ueberschneidung_mitarbeiter
    ON zeitbuchung_ueberschneidung (mitarbeiter_id, mitarbeiter_gesehen_am);
CREATE INDEX IF NOT EXISTS idx_zb_ueberschneidung_geaendert
    ON zeitbuchung_ueberschneidung (geaendert_am);
CREATE INDEX IF NOT EXISTS idx_zb_ueberschneidung_buero
    ON zeitbuchung_ueberschneidung (buero_buchung_id);
CREATE INDEX IF NOT EXISTS idx_zb_ueberschneidung_rest
    ON zeitbuchung_ueberschneidung (rest_buchung_id);
CREATE INDEX IF NOT EXISTS idx_zb_ueberschneidung_handy
    ON zeitbuchung_ueberschneidung (handy_buchung_id);
