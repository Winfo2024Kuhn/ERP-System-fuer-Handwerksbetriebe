-- Kunden-Gutschrift mindert den Offenen Posten der Rechnung (Issue #223).
--
-- WARUM
-- Eine Gutschrift (z. B. Preisnachlass nach einer Reklamation) ist ein Folgedokument einer
-- Rechnung. Der Kunde schuldet danach nur noch den Rest. Offene Posten kennen aber nur
-- „bezahlt“ oder „offen“ über den vollen Rechnungsbetrag.
--
-- WAS
--   projekt_geschaeftsdokument.gutgeschrieben  Brutto-Summe der gebuchten Gutschriften zu
--                                              dieser Rechnung; 0 = keine Gutschrift
--
-- ROBUSTHEIT
-- ADD COLUMN IF NOT EXISTS mit Standardwert, ein zweiter Lauf ändert nichts.

ALTER TABLE projekt_geschaeftsdokument ADD COLUMN IF NOT EXISTS gutgeschrieben NUMERIC(38, 2) NOT NULL DEFAULT 0;
