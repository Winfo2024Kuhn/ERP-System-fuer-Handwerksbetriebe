-- Bleche fuer Stahl, Edelstahl, Aluminium und verzinktes Stahlblech.
--
-- Hintergrund: Die Kategorie "Blech" war komplett leer - kein einziger Artikel
-- in keinem Werkstoff. Fuer einen Metallbaubetrieb ist das die groesste Luecke:
-- Abdeckungen, Podeste, Stufen, Verkleidungen und Anschweissplatten laufen alle
-- ueber Blech.
--
-- Bleche werden nach Quadratmeter abgerechnet. Das Flaechengewicht ergibt sich
-- unmittelbar aus Dicke und Dichte: 1 mm Stahl wiegt 7,85 kg je Quadratmeter.
--
-- Riffelblech (Traenenblech) wiegt mehr als glattes Blech gleicher Dicke, weil
-- die aufgewalzten Noppen zusaetzliches Material sind. Der Aufschlag ist ueber
-- alle Dicken nahezu konstant und entspricht rund 0,29 mm zusaetzlicher
-- Materialdicke - bei Stahl also etwa 2,3 kg je Quadratmeter.
--
-- Idempotent: Bereits vorhandene Artikelnummern werden uebersprungen.

DROP TEMPORARY TABLE IF EXISTS tmp_auftrag;
CREATE TEMPORARY TABLE tmp_auftrag (
    lfd INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    form VARCHAR(32), kat INT, norm VARCHAR(64),
    d DECIMAL(10,2), h DECIMAL(10,2), b DECIMAL(10,2), t DECIMAL(10,2)
);

SET @kat_glatt  := (SELECT id FROM kategorie WHERE beschreibung = 'Glattblech'  AND parent_kategorie_id = 7 LIMIT 1);
SET @kat_riffel := (SELECT id FROM kategorie WHERE beschreibung = 'Riffelblech' AND parent_kategorie_id = 7 LIMIT 1);

-- ==========================================================================
-- Glattbleche
-- ==========================================================================
DROP TEMPORARY TABLE IF EXISTS tmp_dicke;
CREATE TEMPORARY TABLE tmp_dicke (wert DECIMAL(10,2) PRIMARY KEY);
INSERT INTO tmp_dicke (wert) VALUES
 (0.75),(1),(1.25),(1.5),(2),(2.5),(3),(4),(5),(6),(8),(10),(12),(15),(20),(25),(30);

INSERT INTO tmp_auftrag (form, kat, norm, t)
SELECT 'BLECH', @kat_glatt, 'EN 10029', wert FROM tmp_dicke;

DROP PROCEDURE IF EXISTS lade_bleche;
DELIMITER $$
CREATE PROCEDURE lade_bleche(
    IN p_werkstoff VARCHAR(64),
    IN p_norm VARCHAR(64),
    IN p_herstellverfahren VARCHAR(32),
    IN p_fertigungszustand VARCHAR(32),
    IN p_kategorie INT,
    IN p_form VARCHAR(32),
    IN p_max_dicke DECIMAL(10,2))
BEGIN
    DECLARE v_fertig INT DEFAULT 0;
    DECLARE v_t DECIMAL(10,2);
    DECLARE cur CURSOR FOR SELECT t FROM tmp_auftrag WHERE t <= p_max_dicke ORDER BY lfd;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_fertig = 1;

    OPEN cur;
    schleife: LOOP
        FETCH cur INTO v_t;
        IF v_fertig = 1 THEN
            LEAVE schleife;
        END IF;
        CALL anlege_halbzeug(p_werkstoff, p_kategorie, p_form, p_norm,
                             p_herstellverfahren, p_fertigungszustand,
                             NULL, NULL, NULL, v_t);
    END LOOP;
    CLOSE cur;
END$$
DELIMITER ;

-- Baustahl: duenne Bleche kaltgewalzt (EN 10130), dicke warmgewalzt (EN 10029).
CALL lade_bleche('S235JR',     'EN 10029',  'GEWALZT', 'WARMGEWALZT', @kat_glatt, 'BLECH', 30);
-- Edelstahl: kaltgewalzt, Oberflaeche 2B - der Normalfall im Sichtbereich.
CALL lade_bleche('1.4301',     'EN 10088-2','GEWALZT', 'KALTGEWALZT', @kat_glatt, 'BLECH', 15);
-- Aluminium: EN AW-5754 ist die uebliche Blechlegierung, gut umformbar.
CALL lade_bleche('EN AW-5754', 'EN 485-2',  'GEWALZT', 'KALTGEWALZT', @kat_glatt, 'BLECH', 20);
-- Bandverzinktes Stahlblech: bereits verzinkt, kein zweites Verzinken noetig.
CALL lade_bleche('DX51D+Z',    'EN 10346',  'GEWALZT', 'KALTGEWALZT', @kat_glatt, 'BLECH', 3);

-- ==========================================================================
-- Riffelbleche (Traenenbleche)
-- ==========================================================================
DELETE FROM tmp_auftrag;
INSERT INTO tmp_auftrag (form, kat, norm, t) VALUES
 ('RIFFELBLECH', @kat_riffel, 'EN 10029', 2),
 ('RIFFELBLECH', @kat_riffel, 'EN 10029', 3),
 ('RIFFELBLECH', @kat_riffel, 'EN 10029', 4),
 ('RIFFELBLECH', @kat_riffel, 'EN 10029', 5),
 ('RIFFELBLECH', @kat_riffel, 'EN 10029', 6),
 ('RIFFELBLECH', @kat_riffel, 'EN 10029', 8),
 ('RIFFELBLECH', @kat_riffel, 'EN 10029', 10);

CALL lade_bleche('S235JR',     'EN 10029',  'GEWALZT', 'WARMGEWALZT', @kat_riffel, 'RIFFELBLECH', 10);
CALL lade_bleche('1.4301',     'EN 10088-2','GEWALZT', 'KALTGEWALZT', @kat_riffel, 'RIFFELBLECH', 6);
CALL lade_bleche('EN AW-5754', 'EN 485-2',  'GEWALZT', 'KALTGEWALZT', @kat_riffel, 'RIFFELBLECH', 6);

-- Noppenzuschlag: rund 0,29 mm zusaetzliche Materialdicke. Nur einmal anwenden,
-- deshalb die Begrenzung auf noch unkorrigierte Datensaetze.
UPDATE artikel a
JOIN artikel_werkstoffe aw ON aw.id = a.id
JOIN werkstoff w ON w.id = a.werkstoff_id
SET aw.masse_pro_qm = ROUND((aw.wandstaerke + 0.29) * w.dichte, 4)
WHERE a.profilform = 'RIFFELBLECH'
  AND a.system_stammdaten = b'1'
  AND aw.wandstaerke IS NOT NULL
  AND w.dichte IS NOT NULL
  AND aw.masse_pro_qm = ROUND(aw.wandstaerke * w.dichte, 4);

-- Riffelblech ist einseitig gemustert - die abzuwickelnde Flaeche bleibt 2 m2
-- je Quadratmeter (Ober- und Unterseite), das setzt bereits anlege_halbzeug.

DROP PROCEDURE IF EXISTS lade_bleche;
DROP TEMPORARY TABLE IF EXISTS tmp_dicke;
DROP TEMPORARY TABLE IF EXISTS tmp_auftrag;
