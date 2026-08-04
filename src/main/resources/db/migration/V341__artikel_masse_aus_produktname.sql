-- Abmessungen aus dem Produktnamen in echte Zahlenfelder ueberfuehren.
--
-- Hintergrund: Die Masse standen bisher ausschliesslich als Text im Produktnamen
-- ("42.4x2", "70x40x5 mm", "15 mm"). Die Spalten hoehe und breite waren zwar
-- vorhanden, aber bei allen 3145 Datensaetzen 0. Damit konnte weder nach
-- Durchmesser oder Wandstaerke gefiltert noch ein Gewicht nachgerechnet werden.
--
-- Wie die Zahlen zu lesen sind, haengt an der Profilform: bei einem Rundrohr ist
-- die erste Zahl der Durchmesser und die zweite die Wandstaerke, bei einem
-- Rechteckrohr sind es Hoehe, Breite und Wandstaerke, bei einem Flachstahl
-- Breite und Dicke. Die Profilform wurde in V340 gesetzt.
--
-- Vorhandene Werte werden nicht ueberschrieben - die Migration fuellt nur Luecken.
--
-- Idempotent: Mehrfach-Ausfuehrung unschaedlich.

-- --------------------------------------------------------------------------
-- 1. Produktnamen in Bestandteile zerlegen
-- --------------------------------------------------------------------------
-- Die Namen folgen im Bestand mehreren Schreibweisen, die alle abgedeckt werden
-- muessen:
--   "42.4x2"            reine Masse
--   "70x40x5 mm"        mit Einheit
--   "Fl 16x8"           mit Kurzzeichen fuer die Form (Fl, L, HR, HQ, RD, BRFL, VKT, UAP)
--   "BRFL240X40 K(240)" mit Kurzzeichen und angehaengtem Schliffgrad
--   "42.4x2 (K240)"     mit Schliffgrad in Klammern
--
-- Bereinigt werden daher der Reihe nach: Dezimalkomma, Leerzeichen, das
-- Malzeichen "*", Klammerzusaetze, die Einheit "mm", das fuehrende Kurzzeichen
-- und ein etwaiger Rest wie "k240" am Ende. Uebrig bleibt z.B. "42.4x2".
--
-- Beim Entfernen von Buchstaben ist das "x" ausgenommen - es ist der Trenner
-- zwischen den Massen und darf nicht mit weggeschnitten werden.

DROP TEMPORARY TABLE IF EXISTS tmp_artikel_masse;

CREATE TEMPORARY TABLE tmp_artikel_masse AS
SELECT
    a.id,
    a.profilform,
    bereinigt.clean,
    -- Anzahl der durch "x" getrennten Bestandteile.
    LENGTH(bereinigt.clean) - LENGTH(REPLACE(bereinigt.clean, 'x', '')) + 1 AS teile,
    SUBSTRING_INDEX(bereinigt.clean, 'x', 1) AS t1,
    SUBSTRING_INDEX(SUBSTRING_INDEX(bereinigt.clean, 'x', 2), 'x', -1) AS t2,
    SUBSTRING_INDEX(SUBSTRING_INDEX(bereinigt.clean, 'x', 3), 'x', -1) AS t3
FROM artikel a
JOIN artikel_werkstoffe aw ON aw.id = a.id
JOIN (
    SELECT a2.id,
           REGEXP_REPLACE(
               REGEXP_REPLACE(
                   REGEXP_REPLACE(
                       REPLACE(REPLACE(REPLACE(LOWER(a2.produktname), ' ', ''), ',', '.'), '*', 'x'),
                       '\\([^)]*\\)|mm|ø', ''),
                   '^[a-z]+', ''),
               '[a-wyz].*$', '') AS clean
    FROM artikel a2
) AS bereinigt ON bereinigt.id = a.id
WHERE a.profilform IS NOT NULL
  AND a.produktname IS NOT NULL;

CREATE INDEX ix_tmp_masse ON tmp_artikel_masse (id);

-- Nur echte Zahlen uebernehmen - alles andere bleibt leer statt falsch gefuellt.
UPDATE tmp_artikel_masse SET t1 = NULL WHERE t1 NOT REGEXP '^[0-9]+(\\.[0-9]+)?$';
UPDATE tmp_artikel_masse SET t2 = NULL WHERE t2 NOT REGEXP '^[0-9]+(\\.[0-9]+)?$';
UPDATE tmp_artikel_masse SET t3 = NULL WHERE t3 NOT REGEXP '^[0-9]+(\\.[0-9]+)?$';

-- --------------------------------------------------------------------------
-- 2. Rundformen: erste Zahl ist der Durchmesser
-- --------------------------------------------------------------------------

-- Rundrohr "42.4x2" -> Aussendurchmesser 42,4 mm, Wandstaerke 2 mm
UPDATE artikel_werkstoffe aw
JOIN tmp_artikel_masse t ON t.id = aw.id
SET aw.durchmesser = CAST(t.t1 AS DECIMAL(10,2)),
    aw.wandstaerke = CAST(t.t2 AS DECIMAL(10,2))
WHERE t.profilform = 'RUNDROHR' AND t.teile = 2
  AND t.t1 IS NOT NULL AND t.t2 IS NOT NULL
  AND aw.durchmesser IS NULL;

-- Rundstab "15" -> Durchmesser 15 mm, kein Hohlraum
UPDATE artikel_werkstoffe aw
JOIN tmp_artikel_masse t ON t.id = aw.id
SET aw.durchmesser = CAST(t.t1 AS DECIMAL(10,2))
WHERE t.profilform = 'RUNDSTAB' AND t.teile = 1
  AND t.t1 IS NOT NULL
  AND aw.durchmesser IS NULL;

-- --------------------------------------------------------------------------
-- 3. Vollmaterial mit eckigem Querschnitt
-- --------------------------------------------------------------------------

-- Vierkantstab "20" -> 20 x 20 mm
UPDATE artikel_werkstoffe aw
JOIN tmp_artikel_masse t ON t.id = aw.id
SET aw.hoehe = CAST(t.t1 AS DECIMAL(10,2)),
    aw.breite = CAST(t.t1 AS DECIMAL(10,2))
WHERE t.profilform = 'VIERKANTSTAB' AND t.teile = 1
  AND t.t1 IS NOT NULL
  AND aw.hoehe IS NULL;

-- Vierkantstab "20x20"
UPDATE artikel_werkstoffe aw
JOIN tmp_artikel_masse t ON t.id = aw.id
SET aw.hoehe = CAST(t.t1 AS DECIMAL(10,2)),
    aw.breite = CAST(t.t2 AS DECIMAL(10,2))
WHERE t.profilform = 'VIERKANTSTAB' AND t.teile = 2
  AND t.t1 IS NOT NULL AND t.t2 IS NOT NULL
  AND aw.hoehe IS NULL;

-- Flachstahl "70x10" -> 70 mm breit, 10 mm dick.
-- Die Dicke wird als Wandstaerke gefuehrt, damit alle Vollmaterialien und
-- Bleche dasselbe Feld fuer "Materialdicke" verwenden.
UPDATE artikel_werkstoffe aw
JOIN tmp_artikel_masse t ON t.id = aw.id
SET aw.breite = CAST(t.t1 AS DECIMAL(10,2)),
    aw.wandstaerke = CAST(t.t2 AS DECIMAL(10,2))
WHERE t.profilform IN ('FLACHSTAB','BREITFLACHSTAHL') AND t.teile = 2
  AND t.t1 IS NOT NULL AND t.t2 IS NOT NULL
  AND aw.breite IS NULL;

-- --------------------------------------------------------------------------
-- 4. Hohlprofile mit eckigem Querschnitt
-- --------------------------------------------------------------------------

-- Quadratrohr "40x3" -> 40 x 40 mm bei 3 mm Wand
UPDATE artikel_werkstoffe aw
JOIN tmp_artikel_masse t ON t.id = aw.id
SET aw.hoehe = CAST(t.t1 AS DECIMAL(10,2)),
    aw.breite = CAST(t.t1 AS DECIMAL(10,2)),
    aw.wandstaerke = CAST(t.t2 AS DECIMAL(10,2))
WHERE t.profilform = 'QUADRATROHR' AND t.teile = 2
  AND t.t1 IS NOT NULL AND t.t2 IS NOT NULL
  AND aw.hoehe IS NULL;

-- Quadratrohr "40x40x3"
UPDATE artikel_werkstoffe aw
JOIN tmp_artikel_masse t ON t.id = aw.id
SET aw.hoehe = CAST(t.t1 AS DECIMAL(10,2)),
    aw.breite = CAST(t.t2 AS DECIMAL(10,2)),
    aw.wandstaerke = CAST(t.t3 AS DECIMAL(10,2))
WHERE t.profilform = 'QUADRATROHR' AND t.teile = 3
  AND t.t1 IS NOT NULL AND t.t2 IS NOT NULL AND t.t3 IS NOT NULL
  AND aw.hoehe IS NULL;

-- Rechteckrohr "60x40x3"
UPDATE artikel_werkstoffe aw
JOIN tmp_artikel_masse t ON t.id = aw.id
SET aw.hoehe = CAST(t.t1 AS DECIMAL(10,2)),
    aw.breite = CAST(t.t2 AS DECIMAL(10,2)),
    aw.wandstaerke = CAST(t.t3 AS DECIMAL(10,2))
WHERE t.profilform = 'RECHTECKROHR' AND t.teile = 3
  AND t.t1 IS NOT NULL AND t.t2 IS NOT NULL AND t.t3 IS NOT NULL
  AND aw.hoehe IS NULL;

-- --------------------------------------------------------------------------
-- 5. Offene Profile
-- --------------------------------------------------------------------------

-- Winkel und U-/T-/Z-Profile "60x40x5" -> Schenkel 60 und 40, Dicke 5
UPDATE artikel_werkstoffe aw
JOIN tmp_artikel_masse t ON t.id = aw.id
SET aw.hoehe = CAST(t.t1 AS DECIMAL(10,2)),
    aw.breite = CAST(t.t2 AS DECIMAL(10,2)),
    aw.wandstaerke = CAST(t.t3 AS DECIMAL(10,2))
WHERE t.profilform IN ('WINKEL_GLEICHSCHENKLIG','WINKEL_UNGLEICHSCHENKLIG',
                       'U_PROFIL','UPE_PROFIL','UAP_PROFIL','T_PROFIL','Z_PROFIL')
  AND t.teile = 3
  AND t.t1 IS NOT NULL AND t.t2 IS NOT NULL AND t.t3 IS NOT NULL
  AND aw.hoehe IS NULL;

-- Gleichschenkliger Winkel "40x4" -> beide Schenkel 40, Dicke 4
UPDATE artikel_werkstoffe aw
JOIN tmp_artikel_masse t ON t.id = aw.id
SET aw.hoehe = CAST(t.t1 AS DECIMAL(10,2)),
    aw.breite = CAST(t.t1 AS DECIMAL(10,2)),
    aw.wandstaerke = CAST(t.t2 AS DECIMAL(10,2))
WHERE t.profilform = 'WINKEL_GLEICHSCHENKLIG' AND t.teile = 2
  AND t.t1 IS NOT NULL AND t.t2 IS NOT NULL
  AND aw.hoehe IS NULL;

-- Traeger und Normprofile werden nur ueber die Nennhoehe bezeichnet
-- ("IPE 200", "U 100"). Steg- und Flanschdicken stehen in den Normtabellen und
-- werden hier bewusst nicht geraten.
UPDATE artikel_werkstoffe aw
JOIN tmp_artikel_masse t ON t.id = aw.id
SET aw.hoehe = CAST(t.t1 AS DECIMAL(10,2))
WHERE t.profilform IN ('IPE_PROFIL','I_PROFIL','HEA_PROFIL','HEB_PROFIL','HEM_PROFIL',
                       'U_PROFIL','UPE_PROFIL','UAP_PROFIL','T_PROFIL','Z_PROFIL')
  AND t.teile = 1
  AND t.t1 IS NOT NULL
  AND aw.hoehe IS NULL;

-- --------------------------------------------------------------------------
-- 6. Uebliche Lieferlaenge
-- --------------------------------------------------------------------------
-- Stabstahl, Rohre und Profile werden im Handel in 6-m-Stangen gefuehrt. Der
-- Wert dient als Ausgangspunkt fuer die spaetere Verschnittrechnung und kann
-- am Artikel abweichend gepflegt werden.

UPDATE artikel_werkstoffe aw
JOIN artikel a ON a.id = aw.id
SET aw.standardlaenge_mm = 6000
WHERE aw.standardlaenge_mm IS NULL
  AND a.profilform IS NOT NULL
  AND a.profilform NOT IN ('BLECH','RIFFELBLECH','LOCHBLECH','SONSTIGES');

DROP TEMPORARY TABLE IF EXISTS tmp_artikel_masse;
