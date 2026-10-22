-- EAN/Strichcode am Artikel. Idempotent.
--
-- Nur ein Teil der Artikel hat eine EAN, deshalb ist die Spalte optional. Eindeutig ist sie trotzdem: Beim Scannen muss ein Code zu genau
-- einem Artikel fuehren. PostgreSQL erlaubt mehrere NULL-Werte in einem eindeutigen Index.

ALTER TABLE artikel ADD COLUMN IF NOT EXISTS ean VARCHAR(14) NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_artikel_ean ON artikel (ean);
