-- Recht „Zeiterfassung im Menü sehen“ je Abteilung.
--
-- WARUM
-- Nicht jeder PC-Benutzer soll den Menü-Bereich „Zeiterfassung“ (Kalender, Urlaubsanträge,
-- Monatsabschluss, Lange Krankheit …) sehen. In Einstellungen → Berechtigungen wird das jetzt
-- pro Abteilung festgelegt, wie schon „Monate abschließen“ oder „Anrufe sehen“.
-- Administratoren und die Geschäftsführung sehen den Bereich immer (Logik im Backend).
--
-- WAS
--   darf_zeiterfassung_sehen  TRUE = Mitarbeiter dieser Abteilung sehen den Bereich.
--
-- ROBUSTHEIT
-- DEFAULT TRUE: Beim Update verliert niemand den Bereich; der Betrieb nimmt den Haken
-- danach gezielt weg. ADD COLUMN IF NOT EXISTS - ein zweiter Lauf ändert nichts.

ALTER TABLE abteilung ADD COLUMN IF NOT EXISTS darf_zeiterfassung_sehen BOOLEAN NOT NULL DEFAULT TRUE;
