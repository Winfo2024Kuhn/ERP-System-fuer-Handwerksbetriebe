-- Aluminium-Katalog nach EN 755 (EN AW-6060).
--
-- Hintergrund: Der Bestand hatte bei Aluminium deutliche Luecken. Rundrohre gab
-- es nur in einem groben Raster - ausgerechnet die Gelaenderdurchmesser 42,4 und
-- 48,3 fehlten ganz, ebenso duenne Wandstaerken. Rundstangen waren mit 13
-- Abmessungen vertreten (Stahl: 50), U-Profile mit 14.
--
-- Die Abmessungen werden als Kreuzprodukt aus Nennmass und Wandstaerke gebildet
-- und ueber ein Verhaeltnis begrenzt, damit nur Kombinationen entstehen, die es
-- als Strangpressprofil tatsaechlich gibt (eine Wandstaerke von 10 mm bei einem
-- 20er Rohr waere Vollmaterial).
--
-- Gewicht, Oberflaeche, Artikelnummer und Suchtext berechnet die Prozedur
-- anlege_halbzeug aus V343.
--
-- Idempotent: Bereits vorhandene Artikelnummern werden uebersprungen.

DROP TEMPORARY TABLE IF EXISTS tmp_maszahl;
CREATE TEMPORARY TABLE tmp_maszahl (wert DECIMAL(10,2) PRIMARY KEY);

DROP TEMPORARY TABLE IF EXISTS tmp_wand;
CREATE TEMPORARY TABLE tmp_wand (wert DECIMAL(10,2) PRIMARY KEY);

DROP TEMPORARY TABLE IF EXISTS tmp_paar;
CREATE TEMPORARY TABLE tmp_paar (h DECIMAL(10,2), b DECIMAL(10,2), PRIMARY KEY (h, b));

DROP TEMPORARY TABLE IF EXISTS tmp_auftrag;
CREATE TEMPORARY TABLE tmp_auftrag (
    lfd INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    form VARCHAR(32), kat INT, norm VARCHAR(64),
    d DECIMAL(10,2), h DECIMAL(10,2), b DECIMAL(10,2), t DECIMAL(10,2)
);

-- ==========================================================================
-- Rundrohre EN 755-8
-- ==========================================================================
INSERT INTO tmp_maszahl (wert) VALUES
 (6),(8),(10),(12),(14),(15),(16),(18),(20),(22),(25),(28),(30),(32),(35),(38),
 (40),(42),(42.4),(45),(48),(48.3),(50),(55),(60),(63),(65),(70),(75),(80),(85),
 (90),(100),(110),(120),(130),(140),(150),(160),(180),(200);
INSERT INTO tmp_wand (wert) VALUES (1),(1.5),(2),(2.5),(3),(4),(5),(6),(8),(10);

INSERT INTO tmp_auftrag (form, kat, norm, d, t)
SELECT 'RUNDROHR', 8, 'EN 755-8', m.wert, w.wert
FROM tmp_maszahl m CROSS JOIN tmp_wand w
WHERE w.wert <= m.wert / 6 AND m.wert - 2 * w.wert >= 2;

-- ==========================================================================
-- Rundstangen EN 755-3
-- ==========================================================================
-- Eigene Masszahlen: 42,4 und 48,3 sind Rohr-Aussendurchmesser (aus der
-- Zoll-Reihe der Gewinderohre) und kommen als Vollmaterial nicht vor.
DELETE FROM tmp_maszahl;
INSERT INTO tmp_maszahl (wert) VALUES
 (3),(4),(5),(6),(8),(10),(12),(14),(15),(16),(18),(20),(22),(25),(28),(30),
 (32),(35),(38),(40),(45),(50),(55),(60),(65),(70),(80),(90),(100),(110),(120),(150);

INSERT INTO tmp_auftrag (form, kat, norm, d)
SELECT 'RUNDSTAB', 67, 'EN 755-3', wert FROM tmp_maszahl;

-- ==========================================================================
-- Quadratrohre EN 755-8
-- ==========================================================================
DELETE FROM tmp_maszahl;
INSERT INTO tmp_maszahl (wert) VALUES
 (10),(12),(15),(20),(25),(30),(35),(40),(45),(50),(60),(70),(80),(90),(100),(120),(150),(200);

INSERT INTO tmp_auftrag (form, kat, norm, h, b, t)
SELECT 'QUADRATROHR', 10, 'EN 755-8', m.wert, m.wert, w.wert
FROM tmp_maszahl m CROSS JOIN tmp_wand w
WHERE w.wert <= m.wert / 8 AND m.wert - 2 * w.wert >= 2;

-- ==========================================================================
-- Vierkantstangen EN 755-4
-- ==========================================================================
INSERT INTO tmp_auftrag (form, kat, norm, h, b)
SELECT 'VIERKANTSTAB', 68, 'EN 755-4', wert, wert FROM tmp_maszahl WHERE wert <= 100;

-- ==========================================================================
-- Rechteckrohre EN 755-8
-- ==========================================================================
INSERT INTO tmp_paar (h, b) VALUES
 (20,10),(25,15),(30,15),(30,20),(40,20),(40,30),(50,20),(50,25),(50,30),
 (60,20),(60,30),(60,40),(70,40),(80,20),(80,40),(80,50),(80,60),(90,50),
 (100,40),(100,50),(100,60),(100,80),(120,40),(120,60),(120,80),(140,60),
 (150,50),(150,100),(160,80),(180,60),(200,50),(200,100),(200,120);

INSERT INTO tmp_auftrag (form, kat, norm, h, b, t)
SELECT 'RECHTECKROHR', 9, 'EN 755-8', p.h, p.b, w.wert
FROM tmp_paar p CROSS JOIN tmp_wand w
WHERE w.wert <= LEAST(p.h, p.b) / 6 AND LEAST(p.h, p.b) - 2 * w.wert >= 2;

-- ==========================================================================
-- Flachstangen EN 755-5
-- ==========================================================================
DELETE FROM tmp_maszahl;
INSERT INTO tmp_maszahl (wert) VALUES
 (10),(12),(15),(20),(25),(30),(35),(40),(45),(50),(60),(70),(80),(90),(100),(120),(150),(200);
DELETE FROM tmp_wand;
INSERT INTO tmp_wand (wert) VALUES (2),(3),(4),(5),(6),(8),(10),(12),(15),(20);

INSERT INTO tmp_auftrag (form, kat, norm, b, t)
SELECT 'FLACHSTAB', 80, 'EN 755-5', m.wert, w.wert
FROM tmp_maszahl m CROSS JOIN tmp_wand w
WHERE w.wert <= m.wert / 2;

-- ==========================================================================
-- Winkel EN 755-9, gleichschenklig
-- ==========================================================================
DELETE FROM tmp_maszahl;
INSERT INTO tmp_maszahl (wert) VALUES
 (10),(15),(20),(25),(30),(35),(40),(50),(60),(70),(80),(100),(120),(150);
DELETE FROM tmp_wand;
INSERT INTO tmp_wand (wert) VALUES (2),(3),(4),(5),(6),(8),(10);

INSERT INTO tmp_auftrag (form, kat, norm, h, b, t)
SELECT 'WINKEL_GLEICHSCHENKLIG', 101, 'EN 755-9', m.wert, m.wert, w.wert
FROM tmp_maszahl m CROSS JOIN tmp_wand w
WHERE w.wert <= m.wert / 5;

-- ==========================================================================
-- Winkel EN 755-9, ungleichschenklig
-- ==========================================================================
DELETE FROM tmp_paar;
INSERT INTO tmp_paar (h, b) VALUES
 (20,10),(25,15),(30,15),(30,20),(40,20),(40,25),(40,30),(50,25),(50,30),(50,40),
 (60,30),(60,40),(70,40),(80,40),(80,50),(100,50),(100,60),(120,60),(150,75),(200,100);

INSERT INTO tmp_auftrag (form, kat, norm, h, b, t)
SELECT 'WINKEL_UNGLEICHSCHENKLIG', 102, 'EN 755-9', p.h, p.b, w.wert
FROM tmp_paar p CROSS JOIN tmp_wand w
WHERE w.wert <= LEAST(p.h, p.b) / 5;

-- ==========================================================================
-- U-Profile EN 755-9
-- ==========================================================================
DELETE FROM tmp_paar;
INSERT INTO tmp_paar (h, b) VALUES
 (10,10),(15,15),(20,10),(20,15),(20,20),(25,25),(30,15),(30,20),(30,30),
 (40,20),(40,25),(40,40),(50,25),(50,30),(50,50),(60,30),(60,40),(60,60),
 (70,40),(80,40),(80,50),(80,80),(100,40),(100,50),(100,60),(120,50),(120,60),
 (150,75),(200,100);

INSERT INTO tmp_auftrag (form, kat, norm, h, b, t)
SELECT 'U_PROFIL', 91, 'EN 755-9', p.h, p.b, w.wert
FROM tmp_paar p CROSS JOIN tmp_wand w
WHERE w.wert <= LEAST(p.h, p.b) / 5;

-- ==========================================================================
-- T-Profile EN 755-9
-- ==========================================================================
DELETE FROM tmp_paar;
INSERT INTO tmp_paar (h, b) VALUES
 (15,15),(20,20),(25,25),(30,30),(35,35),(40,40),(50,50),(60,60),(80,80),(100,100);

INSERT INTO tmp_auftrag (form, kat, norm, h, b, t)
SELECT 'T_PROFIL', 62, 'EN 755-9', p.h, p.b, w.wert
FROM tmp_paar p CROSS JOIN tmp_wand w
WHERE w.wert <= LEAST(p.h, p.b) / 5;

-- ==========================================================================
-- Z-Profile EN 755-9
-- ==========================================================================
DELETE FROM tmp_paar;
INSERT INTO tmp_paar (h, b) VALUES
 (20,20),(25,25),(30,30),(40,40),(50,50),(60,60),(80,80),(100,100);

INSERT INTO tmp_auftrag (form, kat, norm, h, b, t)
SELECT 'Z_PROFIL', 100, 'EN 755-9', p.h, p.b, w.wert
FROM tmp_paar p CROSS JOIN tmp_wand w
WHERE w.wert <= LEAST(p.h, p.b) / 5;

-- ==========================================================================
-- Auftragsliste abarbeiten
-- ==========================================================================

DROP PROCEDURE IF EXISTS lade_stammdaten_auftraege;
DELIMITER $$
CREATE PROCEDURE lade_stammdaten_auftraege(
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

CALL lade_stammdaten_auftraege('EN AW-6060', 'STRANGGEPRESST', 'KALTGEFERTIGT');

DROP TEMPORARY TABLE IF EXISTS tmp_maszahl;
DROP TEMPORARY TABLE IF EXISTS tmp_wand;
DROP TEMPORARY TABLE IF EXISTS tmp_paar;
DROP TEMPORARY TABLE IF EXISTS tmp_auftrag;
