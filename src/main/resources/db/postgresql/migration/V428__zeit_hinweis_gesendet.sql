-- Verschickte Zeiterfassungs-Erinnerungen festhalten (Push aufs Handy).
--
-- WARUM
-- Ein Prüf-Job schaut alle paar Minuten auf die Zeitbuchungen und schickt Mitarbeitern
-- einen Push ans Handy: an die gesetzliche Pause (Arbeitszeitgesetz § 4: 30 Minuten ab
-- 6 Stunden, 45 Minuten ab 9 Stunden, nie länger als 6 Stunden am Stück) und wenn der
-- Auto-Stop eine Buchung beendet hat, weil kein Feierabend gestempelt wurde. Jede
-- Nachricht soll pro Tag nur einmal kommen, auch wenn der Server neu startet.
--
-- WAS
--   mitarbeiter_id  Empfänger (kein Fremdschlüssel: reine Buchführung, alte Zeilen
--                   räumt der Job nach einer Woche selbst weg)
--   tag             Arbeitstag, für den erinnert wurde
--   art             PAUSE_6_STUNDEN, PAUSE_9_STUNDEN, PAUSE_AM_STUECK oder
--                   AUTO_BEENDET (Java-Enum, ohne CHECK)
--   gesendet_am     Zeitpunkt des Versands
--
-- ROBUSTHEIT
-- Nur CREATE ... IF NOT EXISTS - ein zweiter Lauf ändert nichts.

CREATE TABLE IF NOT EXISTS zeit_hinweis_gesendet (
    id             BIGSERIAL PRIMARY KEY,
    mitarbeiter_id BIGINT      NOT NULL,
    tag            DATE        NOT NULL,
    art            VARCHAR(40) NOT NULL,
    gesendet_am    TIMESTAMP   NOT NULL,
    CONSTRAINT uk_zeit_hinweis_gesendet UNIQUE (mitarbeiter_id, tag, art)
);
