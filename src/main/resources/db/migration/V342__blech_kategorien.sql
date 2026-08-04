-- Kategorien fuer Bleche anlegen.
--
-- Hintergrund: Die Kategorie "Blech" (id 7) existiert unter "Werkstoffe", hat
-- aber weder Unterkategorien noch einen einzigen Artikel. Fuer einen
-- Metallbaubetrieb ist das die groesste Luecke im Stammdatenbestand - Bleche
-- fuer Abdeckungen, Podeste, Stufen und Verkleidungen fehlten bisher komplett.
--
-- Bleche werden nach Quadratmeter abgerechnet, nicht nach laufendem Meter.
-- Deshalb wird das Flaechengewicht in masse_pro_qm gefuehrt (siehe V336).
--
-- Idempotent: Mehrfach-Ausfuehrung unschaedlich.

-- Sicherstellen, dass die Oberkategorie vorhanden ist.
INSERT INTO kategorie (id, beschreibung, parent_kategorie_id)
SELECT 7, 'Blech', (SELECT id FROM (SELECT id FROM kategorie WHERE beschreibung = 'Werkstoffe' AND parent_kategorie_id IS NULL LIMIT 1) AS w)
WHERE NOT EXISTS (SELECT 1 FROM (SELECT * FROM kategorie) k WHERE k.id = 7);

-- Glattblech: der Normalfall fuer Abdeckungen und Verkleidungen.
INSERT INTO kategorie (beschreibung, parent_kategorie_id)
SELECT 'Glattblech', 7
WHERE NOT EXISTS (SELECT 1 FROM (SELECT * FROM kategorie) k
                  WHERE k.beschreibung = 'Glattblech' AND k.parent_kategorie_id = 7);

-- Riffelblech (Traenenblech): rutschhemmend, fuer Stufen, Podeste und Rampen.
INSERT INTO kategorie (beschreibung, parent_kategorie_id)
SELECT 'Riffelblech', 7
WHERE NOT EXISTS (SELECT 1 FROM (SELECT * FROM kategorie) k
                  WHERE k.beschreibung = 'Riffelblech' AND k.parent_kategorie_id = 7);

-- Die typischen Liefer-Rollen der Blech-Kategorie entsprechen denen der
-- uebrigen Werkstoffe, damit der Lieferanten-Vorschlag beim Preis-Eintragen
-- greift. Uebernommen wird die Einstellung der Kategorie "Hohlprofile".
INSERT INTO kategorie_rollen (kategorie_id, rolle)
SELECT k.id, kr.rolle
FROM kategorie k
JOIN kategorie_rollen kr ON kr.kategorie_id = 5
WHERE k.parent_kategorie_id = 7
  AND NOT EXISTS (SELECT 1 FROM (SELECT * FROM kategorie_rollen) x
                  WHERE x.kategorie_id = k.id AND x.rolle = kr.rolle);
