-- ---------------------------------------------------------------------------
-- Werkstattname am Werkstoff: der Begriff, den der Schlosser benutzt.
--
-- In der Werkstatt heisst 1.4301 schlicht "V2A" und 1.4404 "V4A" - die
-- Werkstoffnummer steht zwar auf dem Lieferschein, wird aber weder gesprochen
-- noch in die Suche getippt. Beim Feldmann-Sortiment steht genau dieser Begriff
-- sogar im Produktnamen ("Glasklemme Mod. 31, Anschluss: 42,4mm, V2A"), waehrend
-- das Werkstoff-Feld die Nummer traegt. Ohne diese Spalte findet eine Suche nach
-- "V4A" die Artikel nur zufaellig ueber den Produktnamen und keinen einzigen,
-- bei dem der Begriff dort fehlt.
--
-- Bewusst eine einzelne Spalte und keine Alias-Tabelle: je Werkstoff genau ein
-- Werkstattbegriff. Mehrere Schreibweisen (A2, 304, St37) waeren eine 1:n-
-- Beziehung und sind hier nicht gefragt.
--
-- Zuordnung nach Stahlgruppe:
--   V2A = chromlegiert          -> 1.4301 (304), 1.4541 (321)
--   V4A = mit Molybdaen         -> 1.4404 (316L), 1.4571 (316Ti)
-- ---------------------------------------------------------------------------

SET @c := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE()
    AND table_name = 'werkstoff' AND column_name = 'werkstattname');
SET @sql := IF(@c = 0,
    'ALTER TABLE werkstoff ADD COLUMN werkstattname VARCHAR(64) NULL',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Edelstaehle
UPDATE werkstoff SET werkstattname = 'V2A' WHERE name IN ('1.4301', '1.4541');
UPDATE werkstoff SET werkstattname = 'V4A' WHERE name IN ('1.4404', '1.4571');
UPDATE werkstoff SET werkstattname = 'Edelstahl' WHERE name = 'Edelstahl';

-- Staehle
UPDATE werkstoff SET werkstattname = 'Baustahl' WHERE name IN ('S235JR', 'S355J2', 'Stahl');
UPDATE werkstoff SET werkstattname = 'Verzinkt' WHERE name = 'DX51D+Z';

-- Aluminium
UPDATE werkstoff SET werkstattname = 'Alu' WHERE name IN ('Aluminium', 'EN AW-5754', 'EN AW-6060');

-- Kunststoff heisst auch in der Werkstatt Kunststoff - der Vollstaendigkeit
-- halber gesetzt, damit die Spalte nicht ohne Grund leer bleibt.
UPDATE werkstoff SET werkstattname = 'Kunststoff' WHERE name = 'Kunststoff';

-- ---------------------------------------------------------------------------
-- 1.4404 sicherstellen. Der Feldmann-Import haengt seine V4A-Artikel hier ein
-- (nicht an 1.4571 - Feldmanns "V4A" ist 1.4401/1.4404, nicht der
-- titanstabilisierte 316Ti). Auf manchen Instanzen gibt es den Werkstoff schon,
-- auf anderen nicht - deshalb erst anlegen, dann ergaenzen.
-- ---------------------------------------------------------------------------
INSERT INTO werkstoff (name, anzeigename, dichte, werkstoffnorm, verzinkungsgeeignet,
                       pulverbeschichtungsgeeignet, beschichtungshinweis, werkstattname)
SELECT * FROM (SELECT
    '1.4404' AS name, 'Edelstahl 1.4404' AS anzeigename, 8.000 AS dichte,
    'EN 10088-2' AS werkstoffnorm, b'0' AS vz, b'1' AS pb,
    'Nicht feuerverzinken. Molybdaenlegiert, bestaendiger als 1.4301 - fuer Aussen- und Poolbereich.' AS hinweis,
    'V4A' AS wn) AS neu
WHERE NOT EXISTS (SELECT 1 FROM werkstoff w WHERE w.name = '1.4404');

UPDATE werkstoff SET
    anzeigename = 'Edelstahl 1.4404',
    dichte = 8.000,
    werkstoffnorm = 'EN 10088-2',
    verzinkungsgeeignet = b'0',
    pulverbeschichtungsgeeignet = b'1',
    beschichtungshinweis = 'Nicht feuerverzinken. Molybdaenlegiert, bestaendiger als 1.4301 - fuer Aussen- und Poolbereich.'
WHERE name = '1.4404' AND (anzeigename IS NULL OR anzeigename = '');

-- 1.4541 gibt es nicht auf jeder Instanz; wo doch, bekommt er seinen
-- Werkstattnamen aus dem Block oben. Hier nichts anlegen - er wird vom
-- Feldmann-Sortiment nicht gebraucht.
