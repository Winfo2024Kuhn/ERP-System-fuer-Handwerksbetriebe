-- Gesperrte Kunden nach Ablauf der Aufbewahrungsfrist anonymisieren.
--
-- WARUM
-- Ein Kunde mit Löschwunsch wird gesperrt (V431), solange seine Unterlagen aufbewahrt werden
-- müssen. Ist die Frist abgelaufen, kann ein Admin ihn per Knopf anonymisieren: Name wird zu
-- „Gelöschter Kunde <Kundennummer>“, Anrede und Anschrift werden geleert. Nichts passiert
-- automatisch, Dokumente werden nicht gelöscht.
--
-- WAS
--   kunde.anonymisiert_am   Zeitpunkt der Anonymisierung; NULL = nicht anonymisiert
--
-- ROBUSTHEIT
-- ADD COLUMN IF NOT EXISTS.

ALTER TABLE kunde ADD COLUMN IF NOT EXISTS anonymisiert_am TIMESTAMP;
