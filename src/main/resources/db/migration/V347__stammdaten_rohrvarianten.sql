-- Nahtlose und kaltgezogene Rohrvarianten in Baustahl.
--
-- Hintergrund: Bisher war jede Rohrabmessung nur in einer Ausfuehrung
-- hinterlegt. Tatsaechlich gibt es dieselbe Abmessung je nach Herstellung in
-- mehreren Varianten, die sich in Preis, Oberflaeche und Massgenauigkeit
-- deutlich unterscheiden:
--
--   geschweisst, kaltgefertigt  EN 10219-2   Standard im Stahlbau, guenstigste Ware
--   nahtlos, warmgefertigt      EN 10210-2   ohne Laengsnaht, hoeher belastbar, teurer
--   nahtlos, kaltgezogen        EN 10305-1   Praezisionsrohr, sehr massgenau
--   geschweisst, kaltgezogen    EN 10305-2   Praezisionsrohr, guenstiger als nahtlos
--
-- Angelegt werden nur Kombinationen, die es am Markt wirklich gibt: Nahtlose
-- Rohre beginnen praktisch bei 21,3 mm Aussendurchmesser, Praezisionsrohre
-- enden bei rund 120 mm. Ausserhalb dieser Grenzen wuerde die Suche mit
-- Groessen gefuellt, die kein Haendler liefert.
--
-- Die Artikelnummer erhaelt ein Varianten-Kuerzel (NS, NSP, P), damit dieselbe
-- Abmessung mehrfach gefuehrt werden kann. Der Produktname bekommt den Zusatz
-- "nahtlos" bzw. "gezogen", damit die Trefferliste unterscheidbar bleibt.
--
-- Idempotent: Bereits vorhandene Artikelnummern werden uebersprungen.

DROP TEMPORARY TABLE IF EXISTS tmp_maszahl;
CREATE TEMPORARY TABLE tmp_maszahl (wert DECIMAL(10,2) PRIMARY KEY);

DROP TEMPORARY TABLE IF EXISTS tmp_wand;
CREATE TEMPORARY TABLE tmp_wand (wert DECIMAL(10,2) PRIMARY KEY);

DROP TEMPORARY TABLE IF EXISTS tmp_auftrag;
CREATE TEMPORARY TABLE tmp_auftrag (
    lfd INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    form VARCHAR(32), kat INT, norm VARCHAR(64),
    d DECIMAL(10,2), h DECIMAL(10,2), b DECIMAL(10,2), t DECIMAL(10,2)
);

-- Aussendurchmesser der handelsueblichen Rohrreihe (EN 10220).
INSERT INTO tmp_maszahl (wert) VALUES
 (21.3),(26.9),(33.7),(42.4),(48.3),(60.3),(76.1),(88.9),(101.6),(114.3),
 (139.7),(168.3),(193.7),(219.1);
INSERT INTO tmp_wand (wert) VALUES (2),(2.3),(2.6),(2.9),(3.2),(3.6),(4),(4.5),(5),(5.6),(6.3),(8),(10);

-- ==========================================================================
-- Nahtlos warmgefertigt EN 10210-2
-- ==========================================================================
INSERT INTO tmp_auftrag (form, kat, norm, d, t)
SELECT 'RUNDROHR', 8, 'EN 10210-2', m.wert, w.wert
FROM tmp_maszahl m CROSS JOIN tmp_wand w
WHERE w.wert <= m.wert / 8 AND m.wert - 2 * w.wert >= 4;

DROP PROCEDURE IF EXISTS lade_rohrvarianten;
DELIMITER $$
CREATE PROCEDURE lade_rohrvarianten(
    IN p_werkstoff VARCHAR(64),
    IN p_herstellverfahren VARCHAR(32),
    IN p_fertigungszustand VARCHAR(32))
BEGIN
    DECLARE v_fertig INT DEFAULT 0;
    DECLARE v_form VARCHAR(32);
    DECLARE v_kat INT;
    DECLARE v_norm VARCHAR(64);
    DECLARE v_d, v_h, v_b, v_t DECIMAL(10,2);
    DECLARE cur CURSOR FOR SELECT form, kat, norm, d, h, b, t FROM tmp_auftrag ORDER BY lfd;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_fertig = 1;

    OPEN cur;
    schleife: LOOP
        FETCH cur INTO v_form, v_kat, v_norm, v_d, v_h, v_b, v_t;
        IF v_fertig = 1 THEN
            LEAVE schleife;
        END IF;
        CALL anlege_halbzeug(p_werkstoff, v_kat, v_form, v_norm,
                             p_herstellverfahren, p_fertigungszustand,
                             v_d, v_h, v_b, v_t);
    END LOOP;
    CLOSE cur;
END$$
DELIMITER ;

CALL lade_rohrvarianten('S235JR', 'NAHTLOS', 'WARMGEFERTIGT');

-- ==========================================================================
-- Nahtlos kaltgezogen EN 10305-1 (Praezisionsrohr, bis rund 120 mm)
-- ==========================================================================
DELETE FROM tmp_auftrag;
INSERT INTO tmp_auftrag (form, kat, norm, d, t)
SELECT 'RUNDROHR', 8, 'EN 10305-1', m.wert, w.wert
FROM tmp_maszahl m CROSS JOIN tmp_wand w
WHERE m.wert <= 120 AND w.wert <= m.wert / 8 AND w.wert <= 6.3 AND m.wert - 2 * w.wert >= 4;

CALL lade_rohrvarianten('S235JR', 'NAHTLOS', 'KALTGEZOGEN');

-- ==========================================================================
-- Geschweisst kaltgezogen EN 10305-2 (Praezisionsrohr)
-- ==========================================================================
DELETE FROM tmp_auftrag;
INSERT INTO tmp_auftrag (form, kat, norm, d, t)
SELECT 'RUNDROHR', 8, 'EN 10305-2', m.wert, w.wert
FROM tmp_maszahl m CROSS JOIN tmp_wand w
WHERE m.wert <= 120 AND w.wert <= m.wert / 8 AND w.wert <= 5 AND m.wert - 2 * w.wert >= 4;

CALL lade_rohrvarianten('S235JR', 'GESCHWEISST', 'KALTGEZOGEN');

-- ==========================================================================
-- Nahtlose Hohlprofile EN 10210-2, quadratisch und rechteckig
-- ==========================================================================
-- Warmgefertigte Hohlprofile haben groessere Eckradien und sind im tragenden
-- Stahlbau ueblich, wo EN 10219 (kaltgefertigt) nicht zugelassen ist.

DELETE FROM tmp_auftrag;
DELETE FROM tmp_maszahl;
INSERT INTO tmp_maszahl (wert) VALUES
 (40),(50),(60),(70),(80),(90),(100),(120),(140),(150),(160),(180),(200),(250),(300);
DELETE FROM tmp_wand;
INSERT INTO tmp_wand (wert) VALUES (3),(3.6),(4),(5),(6),(6.3),(8),(10),(12.5);

INSERT INTO tmp_auftrag (form, kat, norm, h, b, t)
SELECT 'QUADRATROHR', 10, 'EN 10210-2', m.wert, m.wert, w.wert
FROM tmp_maszahl m CROSS JOIN tmp_wand w
WHERE w.wert <= m.wert / 10 AND m.wert - 2 * w.wert >= 6;

CALL lade_rohrvarianten('S235JR', 'NAHTLOS', 'WARMGEFERTIGT');

DELETE FROM tmp_auftrag;
INSERT INTO tmp_auftrag (form, kat, norm, h, b, t)
SELECT 'RECHTECKROHR', 9, 'EN 10210-2', p.h, p.b, w.wert
FROM (SELECT 50 h, 30 b UNION ALL SELECT 60,40 UNION ALL SELECT 80,40 UNION ALL
      SELECT 90,50 UNION ALL SELECT 100,50 UNION ALL SELECT 100,60 UNION ALL
      SELECT 120,60 UNION ALL SELECT 120,80 UNION ALL SELECT 140,80 UNION ALL
      SELECT 150,100 UNION ALL SELECT 160,80 UNION ALL SELECT 200,100 UNION ALL
      SELECT 200,120 UNION ALL SELECT 250,150 UNION ALL SELECT 300,200) p
CROSS JOIN tmp_wand w
WHERE w.wert <= LEAST(p.h, p.b) / 8 AND LEAST(p.h, p.b) - 2 * w.wert >= 6;

CALL lade_rohrvarianten('S235JR', 'NAHTLOS', 'WARMGEFERTIGT');

DROP PROCEDURE IF EXISTS lade_rohrvarianten;
DROP TEMPORARY TABLE IF EXISTS tmp_maszahl;
DROP TEMPORARY TABLE IF EXISTS tmp_wand;
DROP TEMPORARY TABLE IF EXISTS tmp_auftrag;
