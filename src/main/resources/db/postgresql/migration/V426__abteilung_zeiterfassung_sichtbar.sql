-- Recht „Zeiterfassung im Menü sehen“ je Abteilung.
--
-- WARUM
-- Nicht jeder PC-Benutzer soll den Menü-Bereich „Zeiterfassung“ (Kalender, Urlaubsanträge,
-- Monatsabschluss, Lange Krankheit …) sehen. Am selben Recht hängt, wer am PC die Krankheit
-- anderer Mitarbeiter sieht (DSGVO Art. 9). In Einstellungen → Berechtigungen wird das pro
-- Abteilung festgelegt, wie schon „Monate abschließen“ oder „Anrufe sehen“.
-- Administratoren und die Geschäftsführung sehen den Bereich immer (Logik im Backend).
--
-- WAS
--   darf_zeiterfassung_sehen  TRUE = Mitarbeiter dieser Abteilung sehen den Bereich.
--
-- ROBUSTHEIT
-- DEFAULT FALSE (Nutzerentscheidung 13.10.2026): Nach dem Update sehen zunächst nur Admin und
-- Geschäftsführung Zeiterfassung und Krankheit; der Admin hakt die Abteilungen danach gezielt an.
-- ADD COLUMN IF NOT EXISTS - ein zweiter Lauf ändert nichts.

ALTER TABLE abteilung ADD COLUMN IF NOT EXISTS darf_zeiterfassung_sehen BOOLEAN NOT NULL DEFAULT FALSE;
