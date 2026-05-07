-- EN 1090 Zeugnisanforderungs-Matrix auf Kategorie (je EXC-Klasse ein Wert)
ALTER TABLE kategorie ADD COLUMN zeugnis_exc1 VARCHAR(30) NULL;
ALTER TABLE kategorie ADD COLUMN zeugnis_exc2 VARCHAR(30) NULL;
ALTER TABLE kategorie ADD COLUMN zeugnis_exc3 VARCHAR(30) NULL;
ALTER TABLE kategorie ADD COLUMN zeugnis_exc4 VARCHAR(30) NULL;

-- artikel_id optional: freie Positionen ohne Stammartikel
ALTER TABLE artikel_in_projekt MODIFY COLUMN artikel_id BIGINT NULL;

-- Zusätzliche Felder für manuelle Bestellpositionen und EN 1090
ALTER TABLE artikel_in_projekt ADD COLUMN zeugnis_anforderung VARCHAR(30)   NULL;
ALTER TABLE artikel_in_projekt ADD COLUMN freitext_produktname VARCHAR(500)  NULL;
ALTER TABLE artikel_in_projekt ADD COLUMN freitext_produkttext TEXT           NULL;
ALTER TABLE artikel_in_projekt ADD COLUMN freitext_einheit     VARCHAR(50)   NULL;
ALTER TABLE artikel_in_projekt ADD COLUMN freitext_menge       NUMERIC(19,3) NULL;
ALTER TABLE artikel_in_projekt ADD COLUMN kategorie_id         INTEGER       NULL;
ALTER TABLE artikel_in_projekt ADD CONSTRAINT fk_aip_kategorie FOREIGN KEY (kategorie_id) REFERENCES kategorie(id);

-- =====================================================================
-- EN 1090 Zeugnis-Defaults nach DIN EN 10204 (Quelle: DIN EN 1090 Wissensdatenbank)
-- Primär nach Materialgüte, nicht nach EXC-Klasse (bis EXC 2 einheitlich).
-- EXC 3/4 erfordert durchgängig APZ 3.1.
-- IDs aus bestehender Kategorie-Tabelle (Stand 2026-04).
-- Kinder erben über ZeugnisService.bestimmeDefault() wenn eigener Wert NULL.
-- =====================================================================

-- ID 1: Werkstoffe (root) → Baustahl S235/S275: EXC 1/2 WZ_2_2, EXC 3/4 APZ_3_1
UPDATE kategorie SET
    zeugnis_exc1 = 'WZ_2_2',
    zeugnis_exc2 = 'WZ_2_2',
    zeugnis_exc3 = 'APZ_3_1',
    zeugnis_exc4 = 'APZ_3_1'
WHERE id = 1;

-- ID 14: Schrauben, ID 27: Nieten → WZ_2_2 (alle EXC)
UPDATE kategorie SET
    zeugnis_exc1 = 'WZ_2_2',
    zeugnis_exc2 = 'WZ_2_2',
    zeugnis_exc3 = 'WZ_2_2',
    zeugnis_exc4 = 'WZ_2_2'
WHERE id IN (14, 27);

-- ID 15: Dübel, ID 25: Kunststoff-Dübel → CE-Kennzeichnung (alle EXC)
UPDATE kategorie SET
    zeugnis_exc1 = 'CE_KONFORMITAET',
    zeugnis_exc2 = 'CE_KONFORMITAET',
    zeugnis_exc3 = 'CE_KONFORMITAET',
    zeugnis_exc4 = 'CE_KONFORMITAET'
WHERE id IN (15, 25);

-- ID 26: Gewindestangen → WZ_2_2 (EXC 1/2), APZ_3_1 (EXC 3/4)
UPDATE kategorie SET
    zeugnis_exc1 = 'WZ_2_2',
    zeugnis_exc2 = 'WZ_2_2',
    zeugnis_exc3 = 'APZ_3_1',
    zeugnis_exc4 = 'APZ_3_1'
WHERE id = 26;
