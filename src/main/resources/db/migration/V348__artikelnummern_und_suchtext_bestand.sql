-- Interne Artikelnummern und Suchtext fuer den Bestand nachziehen.
--
-- Hintergrund: Die in V345 bis V347 neu angelegten Artikel bringen Nummer und
-- Suchtext mit. Die rund 6800 gewachsenen Bestandsartikel haben beides nicht.
-- Ohne Suchtext wuerde die neue Suche ausgerechnet die vorhandenen Artikel
-- nicht finden - und ohne Nummer gaebe es zwei Klassen von Artikeln.
--
-- Die Nummer folgt demselben sprechenden Schema wie bei den neuen Artikeln
-- (Werkstoff-Form-Masse, z.B. ST-RR-42.4-2). Artikel ohne Abmessungen
-- (Schrauben, Werkzeuge, Betriebsstoffe) erhalten eine fortlaufende Nummer,
-- damit auch sie eindeutig referenzierbar sind.
--
-- Der Suchtext buendelt alle Schreibweisen einer Abmessung: "42.4" und "42,4",
-- mit und ohne Trennzeichen, dazu Synonyme wie "rohr" fuer "rundrohr". Die
-- Suche zerlegt die Eingabe in Woerter und verlangt, dass alle vorkommen -
-- damit trifft "rundrohr 42.4 x2" genau den richtigen Artikel.
--
-- Idempotent: Bereits vergebene Nummern und Suchtexte bleiben unberuehrt.

-- --------------------------------------------------------------------------
-- 1. Sprechende Nummern fuer Halbzeuge
-- --------------------------------------------------------------------------
-- Kollisionen sind moeglich, wenn zwei Bestandsartikel dieselbe Abmessung im
-- selben Werkstoff haben (etwa durch doppelte Pflege). Der Zaehler aus
-- ROW_NUMBER haengt in dem Fall eine laufende Ziffer an, statt die Migration
-- am eindeutigen Index scheitern zu lassen.

DROP TEMPORARY TABLE IF EXISTS tmp_nummern;
CREATE TEMPORARY TABLE tmp_nummern (
    artikel_id BIGINT PRIMARY KEY,
    nummer VARCHAR(64)
);

INSERT INTO tmp_nummern (artikel_id, nummer)
SELECT id,
       CONCAT(basis, IF(lfd = 1, '', CONCAT('-', lfd)))
FROM (
    SELECT a.id,
           CONCAT_WS('-',
               CASE w.name
                   WHEN 'S235JR'     THEN 'ST'
                   WHEN 'S355J2'     THEN 'ST355'
                   WHEN '1.4301'     THEN 'VA'
                   WHEN '1.4571'     THEN 'VA71'
                   WHEN 'EN AW-6060' THEN 'AL'
                   WHEN 'EN AW-5754' THEN 'AL57'
                   WHEN 'DX51D+Z'    THEN 'DXZ'
                   WHEN 'Stahl'      THEN 'ST'
                   WHEN 'Edelstahl'  THEN 'VA'
                   WHEN 'Aluminium'  THEN 'AL'
                   ELSE 'XX' END,
               CASE a.profilform
                   WHEN 'RUNDROHR'                 THEN 'RR'
                   WHEN 'QUADRATROHR'              THEN 'QR'
                   WHEN 'RECHTECKROHR'             THEN 'RE'
                   WHEN 'RUNDSTAB'                 THEN 'RS'
                   WHEN 'VIERKANTSTAB'             THEN 'VK'
                   WHEN 'FLACHSTAB'                THEN 'FL'
                   WHEN 'BREITFLACHSTAHL'          THEN 'BF'
                   WHEN 'WINKEL_GLEICHSCHENKLIG'   THEN 'WG'
                   WHEN 'WINKEL_UNGLEICHSCHENKLIG' THEN 'WU'
                   WHEN 'U_PROFIL'                 THEN 'UP'
                   WHEN 'UPE_PROFIL'               THEN 'UPE'
                   WHEN 'UAP_PROFIL'               THEN 'UAP'
                   WHEN 'IPE_PROFIL'               THEN 'IPE'
                   WHEN 'I_PROFIL'                 THEN 'IP'
                   WHEN 'HEA_PROFIL'               THEN 'HEA'
                   WHEN 'HEB_PROFIL'               THEN 'HEB'
                   WHEN 'HEM_PROFIL'               THEN 'HEM'
                   WHEN 'T_PROFIL'                 THEN 'TP'
                   WHEN 'Z_PROFIL'                 THEN 'ZP'
                   WHEN 'BLECH'                    THEN 'BL'
                   WHEN 'RIFFELBLECH'              THEN 'RB'
                   ELSE 'SO' END,
               NULLIF(CASE
                   WHEN a.profilform IN ('RUNDROHR','RUNDSTAB') THEN zahl_kurz(aw.durchmesser)
                   ELSE CONCAT_WS('x', zahl_kurz(aw.hoehe), zahl_kurz(aw.breite))
               END, ''),
               NULLIF(zahl_kurz(aw.wandstaerke), ''),
               IF(aw.geschliffen = b'1', 'K240', NULL)) AS basis,
           ROW_NUMBER() OVER (PARTITION BY
               w.name, a.profilform, aw.durchmesser, aw.hoehe, aw.breite,
               aw.wandstaerke, aw.geschliffen
               ORDER BY a.id) AS lfd
    FROM artikel a
    JOIN artikel_werkstoffe aw ON aw.id = a.id
    JOIN werkstoff w ON w.id = a.werkstoff_id
    WHERE a.artikelnummer IS NULL
      AND a.profilform IS NOT NULL
) AS berechnet
WHERE basis IS NOT NULL AND basis <> '';

-- Nur vergeben, was noch nicht belegt ist.
UPDATE artikel a
JOIN tmp_nummern t ON t.artikel_id = a.id
SET a.artikelnummer = t.nummer
WHERE a.artikelnummer IS NULL
  AND NOT EXISTS (SELECT 1 FROM (SELECT artikelnummer FROM artikel) x
                  WHERE x.artikelnummer = t.nummer);

DROP TEMPORARY TABLE IF EXISTS tmp_nummern;

-- --------------------------------------------------------------------------
-- 2. Fortlaufende Nummern fuer alles Uebrige
-- --------------------------------------------------------------------------
-- Schrauben, Werkzeuge und Betriebsstoffe haben keine Abmessungen, aus denen
-- sich eine sprechende Nummer bilden liesse.

SET @lfd := (SELECT COALESCE(MAX(CAST(SUBSTRING(artikelnummer, 3) AS UNSIGNED)), 0)
             FROM artikel WHERE artikelnummer REGEXP '^A-[0-9]+$');

UPDATE artikel
SET artikelnummer = CONCAT('A-', LPAD((@lfd := @lfd + 1), 6, '0'))
WHERE artikelnummer IS NULL;

-- --------------------------------------------------------------------------
-- 3. Suchtext aufbauen
-- --------------------------------------------------------------------------

UPDATE artikel a
JOIN werkstoff w ON w.id = a.werkstoff_id
LEFT JOIN artikel_werkstoffe aw ON aw.id = a.id
LEFT JOIN kategorie k ON k.id = a.kategorie_id
SET a.suchtext = LOWER(CONCAT_WS(' ',
        CASE a.profilform
            WHEN 'RUNDROHR'                 THEN 'rundrohr rohr rund'
            WHEN 'QUADRATROHR'              THEN 'quadratrohr vierkantrohr rohr quadrat hohlprofil'
            WHEN 'RECHTECKROHR'             THEN 'rechteckrohr rohr rechteck hohlprofil'
            WHEN 'RUNDSTAB'                 THEN 'rundstab rundstahl rundmaterial stab rund'
            WHEN 'VIERKANTSTAB'             THEN 'vierkantstab vierkant quadratstahl stab'
            WHEN 'FLACHSTAB'                THEN 'flachstahl flach flachmaterial bandstahl'
            WHEN 'BREITFLACHSTAHL'          THEN 'breitflachstahl flach breit'
            WHEN 'WINKEL_GLEICHSCHENKLIG'   THEN 'winkel winkelstahl l-profil gleichschenklig'
            WHEN 'WINKEL_UNGLEICHSCHENKLIG' THEN 'winkel winkelstahl l-profil ungleichschenklig'
            WHEN 'U_PROFIL'                 THEN 'u-profil uprofil u profil'
            WHEN 'UPE_PROFIL'               THEN 'upe-profil upe u profil'
            WHEN 'UAP_PROFIL'               THEN 'uap-profil uap u profil'
            WHEN 'IPE_PROFIL'               THEN 'ipe traeger doppel-t i-profil'
            WHEN 'I_PROFIL'                 THEN 'i-profil traeger doppel-t'
            WHEN 'HEA_PROFIL'               THEN 'hea traeger breitflansch'
            WHEN 'HEB_PROFIL'               THEN 'heb traeger breitflansch'
            WHEN 'HEM_PROFIL'               THEN 'hem traeger breitflansch'
            WHEN 'T_PROFIL'                 THEN 't-profil tprofil t profil'
            WHEN 'Z_PROFIL'                 THEN 'z-profil zprofil z profil'
            WHEN 'BLECH'                    THEN 'blech glattblech tafel platte'
            WHEN 'RIFFELBLECH'              THEN 'riffelblech traenenblech blech rutschhemmend'
            ELSE '' END,
        a.artikelnummer,
        a.produktname,
        REPLACE(a.produktname, '.', ','),
        REPLACE(a.produktname, ',', '.'),
        a.produkttext,
        k.beschreibung,
        zahl_kurz(aw.durchmesser), zahl_kurz(aw.hoehe),
        zahl_kurz(aw.breite), zahl_kurz(aw.wandstaerke),
        REPLACE(zahl_kurz(aw.durchmesser), '.', ','),
        w.name, w.anzeigename,
        a.massnorm, a.produktlinie,
        LOWER(REPLACE(a.herstellverfahren, '_', ' ')),
        LOWER(REPLACE(a.fertigungszustand, '_', ' ')),
        IF(aw.geschliffen = b'1', 'geschliffen k240', NULL)))
WHERE a.suchtext IS NULL OR a.suchtext = '';

-- Artikel ohne Werkstoffbezug (Betriebsstoffe, Werkzeuge) ebenfalls erfassen.
UPDATE artikel a
LEFT JOIN kategorie k ON k.id = a.kategorie_id
SET a.suchtext = LOWER(CONCAT_WS(' ', a.artikelnummer, a.produktname,
                                 a.produkttext, a.produktlinie, k.beschreibung))
WHERE a.suchtext IS NULL OR a.suchtext = '';
