-- Entscheidung am Urlaubsantrag festhalten (Issue #270, Punkt 3).
--
-- WARUM
-- Mitarbeiter erfahren bisher nur durch Nachsehen, ob ihr Antrag genehmigt oder
-- abgelehnt wurde. Die Handy-App soll die Entscheidung einmal als Hinweis zeigen,
-- bis der Mitarbeiter sie mit „Verstanden" bestätigt. Dafür braucht der Antrag den
-- Zeitpunkt der Entscheidung und eine Markierung, ob sie schon gesehen wurde.
--
-- WAS
--   entschieden_am        Zeitpunkt von Genehmigung oder Ablehnung (NULL = noch offen
--                         oder vor dieser Erweiterung entschieden).
--   entscheidung_gesehen  TRUE = der Mitarbeiter hat den Hinweis bestätigt.
--                         Bestandsdaten gelten als gesehen, damit beim Update nicht
--                         jahrealte Entscheidungen als neu aufpoppen.
--
-- ROBUSTHEIT
-- Nur ADD COLUMN IF NOT EXISTS - ein zweiter Lauf ändert nichts.

ALTER TABLE urlaubsantrag ADD COLUMN IF NOT EXISTS entschieden_am TIMESTAMP;
ALTER TABLE urlaubsantrag ADD COLUMN IF NOT EXISTS entscheidung_gesehen BOOLEAN NOT NULL DEFAULT TRUE;
