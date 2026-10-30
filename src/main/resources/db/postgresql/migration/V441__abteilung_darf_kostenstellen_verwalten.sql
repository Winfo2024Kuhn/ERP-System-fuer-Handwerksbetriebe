-- Recht „Kostenstellen verwalten“ je Abteilung.
--
-- WARUM
-- Kostenstellen anlegen, umbenennen, löschen und wiederherstellen durften bisher nur Administratoren.
-- In Einstellungen → Abteilungen & Rechte wird das jetzt pro Abteilung freigegeben, wie schon „Aufschläge ändern“.
-- Administratoren dürfen es immer (Logik im Backend).
--
-- WAS
--   darf_kostenstellen_verwalten  TRUE = Mitarbeiter dieser Abteilung dürfen Kostenstellen verwalten.
--
-- ROBUSTHEIT
-- DEFAULT FALSE: Nach dem Update darf es zunächst nur der Administrator; er hakt die Abteilungen danach gezielt an.
-- ADD COLUMN IF NOT EXISTS - ein zweiter Lauf ändert nichts.

ALTER TABLE abteilung ADD COLUMN IF NOT EXISTS darf_kostenstellen_verwalten BOOLEAN NOT NULL DEFAULT FALSE;
