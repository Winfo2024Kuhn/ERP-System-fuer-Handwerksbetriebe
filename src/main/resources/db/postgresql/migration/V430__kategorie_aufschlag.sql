-- Aufschlag je Artikelkategorie. Leer = die Kategorie hat keinen eigenen Aufschlag
-- (dann gilt der der Oberkategorie, sonst der allgemeine). Bestehende Kategorien bleiben leer, deshalb ändert sich nach dem Update nichts.
-- Idempotent.

ALTER TABLE kategorie ADD COLUMN IF NOT EXISTS verkaufsaufschlag_prozent DECIMAL(6,2) NULL;
