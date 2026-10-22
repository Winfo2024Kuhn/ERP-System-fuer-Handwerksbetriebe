-- Recht „Aufschläge ändern“ je Abteilung.
--
-- WARUM
-- Der Aufschlag auf den Einkaufspreis ist eine Preisregel des Betriebs. Nicht jeder Benutzer soll sie ändern:
-- In Einstellungen → Berechtigungen wird das pro Abteilung festgelegt, wie schon „Monate abschließen“ oder „Anrufe sehen“.
-- Administratoren dürfen es immer (Logik im Backend).
--
-- WAS
--   darf_aufschlaege_aendern  TRUE = Mitarbeiter dieser Abteilung dürfen die Aufschläge einstellen.
--
-- ROBUSTHEIT
-- DEFAULT FALSE: Nach dem Update darf es zunächst nur der Administrator; er hakt die Abteilungen danach gezielt an.
-- ADD COLUMN IF NOT EXISTS - ein zweiter Lauf ändert nichts.

ALTER TABLE abteilung ADD COLUMN IF NOT EXISTS darf_aufschlaege_aendern BOOLEAN NOT NULL DEFAULT FALSE;
