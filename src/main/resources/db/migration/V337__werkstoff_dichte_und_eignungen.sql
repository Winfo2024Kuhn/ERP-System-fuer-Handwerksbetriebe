-- Werkstoff-Stammdaten: Dichte und Beschichtungs-Eignungen.
--
-- Hintergrund: Ob ein Bauteil feuerverzinkt oder pulverbeschichtet werden kann,
-- haengt am Werkstoff und nicht am einzelnen Artikel. Edelstahl wird nicht
-- feuerverzinkt, Aluminium auch nicht - beides laesst sich aber pulverbeschichten.
-- Diese Regel hier einmal je Werkstoff zu pflegen ist deutlich weniger fehleranfaellig,
-- als sie an 6784 Artikeln zu wiederholen.
--
-- Der Artikel kann davon abweichen: artikel.verzinkungsgeeignet bzw.
-- artikel.pulverbeschichtungsgeeignet sind NULL, solange der Werkstoff-Standard
-- gilt, und werden nur bei einer Ausnahme ausdruecklich gesetzt (siehe V336).
--
-- Die Dichte wird gebraucht, um Gewicht je Meter und Flaechengewicht aus den
-- Abmessungen auszurechnen und die vorhandenen Werte gegenzupruefen.
--
-- Idempotent: Mehrfach-Ausfuehrung unschaedlich.

-- --------------------------------------------------------------------------
-- 1. Neue Spalten
-- --------------------------------------------------------------------------

SET @c := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE()
    AND table_name = 'werkstoff' AND column_name = 'dichte');
SET @sql := IF(@c = 0,
    'ALTER TABLE werkstoff ADD COLUMN dichte DECIMAL(6,3) NULL COMMENT ''g/cm3, fuer Gewichtsberechnung''',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE()
    AND table_name = 'werkstoff' AND column_name = 'verzinkungsgeeignet');
SET @sql := IF(@c = 0,
    "ALTER TABLE werkstoff ADD COLUMN verzinkungsgeeignet BIT(1) NOT NULL DEFAULT b'0'",
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE()
    AND table_name = 'werkstoff' AND column_name = 'pulverbeschichtungsgeeignet');
SET @sql := IF(@c = 0,
    "ALTER TABLE werkstoff ADD COLUMN pulverbeschichtungsgeeignet BIT(1) NOT NULL DEFAULT b'0'",
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Kurzer, alltagstauglicher Name fuer die Oberflaeche ("Edelstahl" statt "1.4301").
SET @c := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE()
    AND table_name = 'werkstoff' AND column_name = 'anzeigename');
SET @sql := IF(@c = 0,
    'ALTER TABLE werkstoff ADD COLUMN anzeigename VARCHAR(128) NULL',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Hinweis fuer den Anwender, warum etwas nicht geht.
SET @c := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE()
    AND table_name = 'werkstoff' AND column_name = 'beschichtungshinweis');
SET @sql := IF(@c = 0,
    'ALTER TABLE werkstoff ADD COLUMN beschichtungshinweis VARCHAR(255) NULL',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Werkstoffnorm, getrennt von der Massnorm des Artikels.
SET @c := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE()
    AND table_name = 'werkstoff' AND column_name = 'werkstoffnorm');
SET @sql := IF(@c = 0,
    'ALTER TABLE werkstoff ADD COLUMN werkstoffnorm VARCHAR(64) NULL',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- --------------------------------------------------------------------------
-- 2. Bekannte Werkstoffe befuellen
-- --------------------------------------------------------------------------
-- Feuerverzinken heisst: Bauteil ins ca. 450 Grad heisse Zinkbad tauchen.
--   Baustahl  -> ja, der klassische Korrosionsschutz.
--   Edelstahl -> nein, rostet ohnehin nicht und das Zink wuerde die Passivschicht
--                zerstoeren; ausserdem Gefahr von Zinkinduzierter Rissbildung.
--   Aluminium -> nein, Alu schuetzt sich selbst; im Zinkbad droht Kontaktkorrosion.
-- Pulverbeschichten geht bei allen drei Metallen, bei Alu ist es sogar der Standard.

UPDATE werkstoff SET
    anzeigename = 'Baustahl S235JR',
    dichte = 7.850,
    werkstoffnorm = 'EN 10025-2',
    verzinkungsgeeignet = b'1',
    pulverbeschichtungsgeeignet = b'1',
    beschichtungshinweis = 'Feuerverzinken und Pulverbeschichten moeglich, auch kombiniert (Duplex).'
WHERE name = 'S235JR';

UPDATE werkstoff SET
    anzeigename = 'Baustahl',
    dichte = 7.850,
    verzinkungsgeeignet = b'1',
    pulverbeschichtungsgeeignet = b'1',
    beschichtungshinweis = 'Feuerverzinken und Pulverbeschichten moeglich, auch kombiniert (Duplex).'
WHERE name = 'Stahl';

UPDATE werkstoff SET
    anzeigename = 'Edelstahl 1.4301',
    dichte = 7.900,
    werkstoffnorm = 'EN 10088-3',
    verzinkungsgeeignet = b'0',
    pulverbeschichtungsgeeignet = b'1',
    beschichtungshinweis = 'Nicht feuerverzinken - rostet nicht und das Zink wuerde die Schutzschicht zerstoeren. Pulverbeschichten geht.'
WHERE name = '1.4301';

UPDATE werkstoff SET
    anzeigename = 'Edelstahl',
    dichte = 7.900,
    verzinkungsgeeignet = b'0',
    pulverbeschichtungsgeeignet = b'1',
    beschichtungshinweis = 'Nicht feuerverzinken - rostet nicht und das Zink wuerde die Schutzschicht zerstoeren. Pulverbeschichten geht.'
WHERE name = 'Edelstahl';

UPDATE werkstoff SET
    anzeigename = 'Aluminium EN AW-6060',
    dichte = 2.700,
    werkstoffnorm = 'EN 573-3',
    verzinkungsgeeignet = b'0',
    pulverbeschichtungsgeeignet = b'1',
    beschichtungshinweis = 'Nicht feuerverzinken. Pulverbeschichten ist bei Alu der Normalfall, alternativ eloxieren.'
WHERE name = 'EN AW-6060';

UPDATE werkstoff SET
    anzeigename = 'Aluminium',
    dichte = 2.700,
    verzinkungsgeeignet = b'0',
    pulverbeschichtungsgeeignet = b'1',
    beschichtungshinweis = 'Nicht feuerverzinken. Pulverbeschichten ist bei Alu der Normalfall, alternativ eloxieren.'
WHERE name = 'Aluminium';

UPDATE werkstoff SET
    anzeigename = 'Kunststoff',
    verzinkungsgeeignet = b'0',
    pulverbeschichtungsgeeignet = b'0',
    beschichtungshinweis = 'Weder verzinken noch pulverbeschichten - haelt die noetige Temperatur nicht aus.'
WHERE name = 'Kunststoff';

-- Fallback: Werkstoffe ohne Anzeigename bekommen den technischen Namen.
UPDATE werkstoff SET anzeigename = name WHERE anzeigename IS NULL;

-- --------------------------------------------------------------------------
-- 3. Weitere gaengige Werkstoffe anlegen (fuer die neuen Stammdaten)
-- --------------------------------------------------------------------------

INSERT INTO werkstoff (name, anzeigename, dichte, werkstoffnorm, verzinkungsgeeignet,
                       pulverbeschichtungsgeeignet, beschichtungshinweis)
SELECT * FROM (SELECT
    'S355J2' AS name, 'Baustahl S355J2 (hochfest)' AS anzeigename, 7.850 AS dichte,
    'EN 10025-2' AS werkstoffnorm, b'1' AS vz, b'1' AS pb,
    'Feuerverzinken und Pulverbeschichten moeglich, auch kombiniert (Duplex).' AS hinweis) AS neu
WHERE NOT EXISTS (SELECT 1 FROM werkstoff w WHERE w.name = 'S355J2');

INSERT INTO werkstoff (name, anzeigename, dichte, werkstoffnorm, verzinkungsgeeignet,
                       pulverbeschichtungsgeeignet, beschichtungshinweis)
SELECT * FROM (SELECT
    '1.4571' AS name, 'Edelstahl 1.4571 (seewasserfest)' AS anzeigename, 8.000 AS dichte,
    'EN 10088-3' AS werkstoffnorm, b'0' AS vz, b'1' AS pb,
    'Nicht feuerverzinken. Hoehere Bestaendigkeit als 1.4301, fuer Kuesten- und Poolbereich.' AS hinweis) AS neu
WHERE NOT EXISTS (SELECT 1 FROM werkstoff w WHERE w.name = '1.4571');

INSERT INTO werkstoff (name, anzeigename, dichte, werkstoffnorm, verzinkungsgeeignet,
                       pulverbeschichtungsgeeignet, beschichtungshinweis)
SELECT * FROM (SELECT
    'EN AW-5754' AS name, 'Aluminium EN AW-5754 (Blech)' AS anzeigename, 2.660 AS dichte,
    'EN 573-3' AS werkstoffnorm, b'0' AS vz, b'1' AS pb,
    'Nicht feuerverzinken. Gut umformbar und schweissbar - die uebliche Blechlegierung.' AS hinweis) AS neu
WHERE NOT EXISTS (SELECT 1 FROM werkstoff w WHERE w.name = 'EN AW-5754');

INSERT INTO werkstoff (name, anzeigename, dichte, werkstoffnorm, verzinkungsgeeignet,
                       pulverbeschichtungsgeeignet, beschichtungshinweis)
SELECT * FROM (SELECT
    'DX51D+Z' AS name, 'Stahlblech verzinkt (Sendzimir)' AS anzeigename, 7.850 AS dichte,
    'EN 10346' AS werkstoffnorm, b'0' AS vz, b'1' AS pb,
    'Bereits bandverzinkt - kein zweites Verzinken noetig. Pulverbeschichten geht.' AS hinweis) AS neu
WHERE NOT EXISTS (SELECT 1 FROM werkstoff w WHERE w.name = 'DX51D+Z');
