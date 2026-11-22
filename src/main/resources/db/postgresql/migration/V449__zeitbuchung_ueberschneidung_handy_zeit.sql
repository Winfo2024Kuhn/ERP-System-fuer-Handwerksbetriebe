-- Handy-Zeiten je Überschneidungs-Meldung ("Handy gewinnt").
-- Bisher hielt die Meldung nur eine Spanne fest (handy_von = frühester Start, handy_bis =
-- spätestes Ende aller Handy-Buchungen). Bei Arbeit 11:27–12:00 und Pause 12:00–13:34 stand
-- dann "Handy-Buchung 11:27–13:34" in der Glocke, die es so nie gab. Jetzt steht hier jede
-- Handy-Buchung einzeln, so wie das Handy sie damals geschickt hat (Stand beim Auflösen,
-- nicht live – das Büro kann die Handy-Buchung später ändern).
-- handy_buchung_id bewusst ohne Fremdschlüssel (wie in zeitbuchung_ueberschneidung): Eine
-- gelöschte Buchung soll die Meldung nicht verändern. Mit der Meldung verschwinden ihre Zeilen.
-- handy_von/handy_bis der Meldung bleiben für Altmeldungen und als Rückfall.
-- Idempotent: IF NOT EXISTS.

CREATE TABLE IF NOT EXISTS zeitbuchung_ueberschneidung_handy_zeit (
    id BIGSERIAL PRIMARY KEY,
    ueberschneidung_id BIGINT NOT NULL REFERENCES zeitbuchung_ueberschneidung (id) ON DELETE CASCADE,
    handy_buchung_id BIGINT NOT NULL,
    art VARCHAR(20),
    von TIMESTAMP NOT NULL,
    bis TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_zb_ueberschneidung_handy_zeit
    ON zeitbuchung_ueberschneidung_handy_zeit (ueberschneidung_id, handy_buchung_id);

-- Ende nachtragen, wenn die Handy-Buchung abgeschlossen ist (Suche über die Handy-Buchung).
CREATE INDEX IF NOT EXISTS idx_zb_ueberschneidung_handy_zeit_buchung
    ON zeitbuchung_ueberschneidung_handy_zeit (handy_buchung_id);
