-- Hilfsprozedur zum Anlegen von Halbzeug-Stammdaten.
--
-- Die folgenden Migrationen legen ueber tausend Artikel an. Statt diese einzeln
-- auszuschreiben, uebernimmt diese Prozedur die immer gleiche Arbeit: Sie
-- rechnet Gewicht je Meter und abzuwickelnde Oberflaeche aus den Abmessungen
-- und der Werkstoffdichte aus, bildet den Produktnamen in der im Bestand
-- ueblichen Schreibweise, vergibt eine sprechende Artikelnummer und baut den
-- Suchtext mit allen Schreibweisen auf.
--
-- Damit steht die Berechnung an genau einer Stelle und ist nachvollziehbar,
-- statt in tausend Zahlenliteralen zu verschwinden.
--
-- Idempotent: Ein bereits vorhandener Artikel (gleiche Artikelnummer) wird
-- uebersprungen, bestehende Preise und manuelle Aenderungen bleiben unberuehrt.

DROP FUNCTION IF EXISTS zahl_kurz;
DROP PROCEDURE IF EXISTS anlege_halbzeug;

DELIMITER $$

-- Zahl ohne ueberfluessige Nachkommastellen: 42.40 -> "42.4", 2.00 -> "2"
CREATE FUNCTION zahl_kurz(p_wert DECIMAL(10,2))
RETURNS VARCHAR(16)
DETERMINISTIC
BEGIN
    IF p_wert IS NULL THEN
        RETURN '';
    END IF;
    IF p_wert = FLOOR(p_wert) THEN
        RETURN CAST(FLOOR(p_wert) AS CHAR);
    END IF;
    RETURN TRIM(TRAILING '0' FROM CAST(p_wert AS CHAR));
END$$

CREATE PROCEDURE anlege_halbzeug(
    IN p_werkstoff        VARCHAR(64),
    IN p_kategorie_id     INT,
    IN p_profilform       VARCHAR(32),
    IN p_massnorm         VARCHAR(64),
    IN p_herstellverfahren VARCHAR(32),
    IN p_fertigungszustand VARCHAR(32),
    IN p_durchmesser      DECIMAL(10,2),
    IN p_hoehe            DECIMAL(10,2),
    IN p_breite           DECIMAL(10,2),
    IN p_wandstaerke      DECIMAL(10,2))
proc: BEGIN
    DECLARE v_werkstoff_id BIGINT;
    DECLARE v_dichte       DECIMAL(6,3);
    DECLARE v_kuerzel      VARCHAR(8);
    DECLARE v_form_kuerzel VARCHAR(4);
    DECLARE v_name         VARCHAR(255);
    DECLARE v_nummer       VARCHAR(64);
    DECLARE v_masse        DECIMAL(12,4);
    DECLARE v_masse_qm     DECIMAL(10,4);
    DECLARE v_mantel       DECIMAL(12,4);
    DECLARE v_quer         DECIMAL(10,3);
    DECLARE v_einheit      VARCHAR(32);
    DECLARE v_suchtext     TEXT;
    DECLARE v_artikel_id   BIGINT;
    DECLARE v_ist_blech    TINYINT DEFAULT 0;
    DECLARE v_variante     VARCHAR(8);

    SELECT id, dichte INTO v_werkstoff_id, v_dichte
    FROM werkstoff WHERE name = p_werkstoff LIMIT 1;

    IF v_werkstoff_id IS NULL OR v_dichte IS NULL THEN
        -- Ohne Werkstoff und Dichte laesst sich nichts berechnen.
        LEAVE proc;
    END IF;

    SET v_ist_blech = IF(p_profilform IN ('BLECH','RIFFELBLECH','LOCHBLECH'), 1, 0);

    -- Werkstoff-Kuerzel fuer die Artikelnummer.
    SET v_kuerzel = CASE p_werkstoff
        WHEN 'S235JR'     THEN 'ST'
        WHEN 'S355J2'     THEN 'ST355'
        WHEN '1.4301'     THEN 'VA'
        WHEN '1.4571'     THEN 'VA71'
        WHEN 'EN AW-6060' THEN 'AL'
        WHEN 'EN AW-5754' THEN 'AL57'
        WHEN 'DX51D+Z'    THEN 'DXZ'
        ELSE 'XX' END;

    SET v_form_kuerzel = CASE p_profilform
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
        WHEN 'T_PROFIL'                 THEN 'TP'
        WHEN 'Z_PROFIL'                 THEN 'ZP'
        WHEN 'BLECH'                    THEN 'BL'
        WHEN 'RIFFELBLECH'              THEN 'RB'
        ELSE 'SO' END;

    -- ------------------------------------------------------------------
    -- Produktname in der im Bestand ueblichen Schreibweise
    -- ------------------------------------------------------------------
    SET v_name = CASE
        WHEN p_profilform = 'RUNDROHR'
            THEN CONCAT(zahl_kurz(p_durchmesser), 'x', zahl_kurz(p_wandstaerke))
        WHEN p_profilform = 'RUNDSTAB'
            THEN CONCAT(zahl_kurz(p_durchmesser), ' mm')
        WHEN v_ist_blech = 1
            THEN CONCAT(zahl_kurz(p_wandstaerke), ' mm')
        WHEN p_profilform IN ('FLACHSTAB','BREITFLACHSTAHL','VIERKANTSTAB')
            THEN CONCAT(zahl_kurz(p_breite), 'x', zahl_kurz(p_wandstaerke), ' mm')
        ELSE CONCAT(zahl_kurz(p_hoehe), 'x', zahl_kurz(p_breite), 'x',
                    zahl_kurz(p_wandstaerke), ' mm')
        END;

    -- Dieselbe Abmessung gibt es in mehreren Ausfuehrungen. Ohne Zusatz stuende
    -- "42.4x2" mehrfach identisch in der Trefferliste. Das Herstellverfahren
    -- steht zusaetzlich als eigenes Feld zur Verfuegung und ist filterbar.
    SET v_name = CONCAT(v_name, CASE
        WHEN p_herstellverfahren = 'NAHTLOS' AND p_fertigungszustand = 'KALTGEZOGEN'   THEN ' nahtlos gezogen'
        WHEN p_herstellverfahren = 'NAHTLOS'                                            THEN ' nahtlos'
        WHEN p_herstellverfahren = 'GESCHWEISST' AND p_fertigungszustand = 'KALTGEZOGEN' THEN ' gezogen'
        WHEN p_fertigungszustand = 'GESCHLIFFEN'                                        THEN ' (K240)'
        ELSE '' END);

    -- Varianten-Kuerzel: Dieselbe Abmessung gibt es je nach Herstellverfahren
    -- mehrfach - ein Rundrohr 42,4x2 als geschweisste Standardware, als
    -- nahtloses und als kaltgezogenes Praezisionsrohr. Ohne Unterscheidung
    -- wuerde die Artikelnummer kollidieren. Die verbreitetste Ausfuehrung
    -- bleibt ohne Zusatz, damit die gaengigen Nummern kurz bleiben.
    SET v_variante = CASE
        WHEN p_herstellverfahren = 'NAHTLOS' AND p_fertigungszustand = 'WARMGEFERTIGT' THEN 'NS'
        WHEN p_herstellverfahren = 'NAHTLOS' AND p_fertigungszustand = 'KALTGEZOGEN'   THEN 'NSP'
        WHEN p_herstellverfahren = 'GESCHWEISST' AND p_fertigungszustand = 'KALTGEZOGEN' THEN 'P'
        WHEN p_fertigungszustand = 'GESCHLIFFEN' THEN 'K240'
        ELSE NULL END;

    -- NULLIF verhindert einen leeren Bestandteil: zahl_kurz liefert bei einem
    -- nicht belegten Mass einen Leerstring, den CONCAT_WS sonst als gueltigen
    -- Wert behandeln und mit einem Trennstrich anhaengen wuerde (z.B.
    -- "AL-RS-42.4-" bei einem Rundstab ohne Wandstaerke).
    SET v_nummer = CONCAT_WS('-', v_kuerzel, v_form_kuerzel,
        NULLIF(CASE
            WHEN p_profilform IN ('RUNDROHR','RUNDSTAB') THEN zahl_kurz(p_durchmesser)
            WHEN v_ist_blech = 1 THEN zahl_kurz(p_wandstaerke)
            ELSE CONCAT_WS('x', zahl_kurz(p_hoehe), zahl_kurz(p_breite))
        END, ''),
        IF(v_ist_blech = 1, NULL, NULLIF(zahl_kurz(p_wandstaerke), '')),
        v_variante);

    -- Schon vorhanden? Dann nichts tun.
    IF EXISTS (SELECT 1 FROM artikel WHERE artikelnummer = v_nummer) THEN
        LEAVE proc;
    END IF;

    -- ------------------------------------------------------------------
    -- Gewicht und abzuwickelnde Oberflaeche
    -- ------------------------------------------------------------------
    -- Bei Hohlprofilen mindern die Eckradien das Gewicht; sie sind mit dem
    -- Term (4 - PI) * t^2 beruecksichtigt. Alle Masse in mm, Dichte in g/cm3,
    -- Ergebnis in kg je laufendem Meter bzw. kg je Quadratmeter.
    SET v_masse = CASE p_profilform
        WHEN 'RUNDROHR'     THEN PI() * (p_durchmesser - p_wandstaerke) * p_wandstaerke * v_dichte / 1000
        WHEN 'RUNDSTAB'     THEN PI() / 4 * POW(p_durchmesser, 2) * v_dichte / 1000
        WHEN 'QUADRATROHR'  THEN (4 * p_wandstaerke * (p_hoehe - p_wandstaerke)
                                  - (4 - PI()) * POW(p_wandstaerke, 2)) * v_dichte / 1000
        WHEN 'RECHTECKROHR' THEN (2 * p_wandstaerke * (p_hoehe + p_breite - 2 * p_wandstaerke)
                                  - (4 - PI()) * POW(p_wandstaerke, 2)) * v_dichte / 1000
        WHEN 'VIERKANTSTAB' THEN p_hoehe * p_breite * v_dichte / 1000
        WHEN 'FLACHSTAB'        THEN p_breite * p_wandstaerke * v_dichte / 1000
        WHEN 'BREITFLACHSTAHL'  THEN p_breite * p_wandstaerke * v_dichte / 1000
        WHEN 'WINKEL_GLEICHSCHENKLIG'   THEN p_wandstaerke * (p_hoehe + p_breite - p_wandstaerke) * v_dichte / 1000
        WHEN 'WINKEL_UNGLEICHSCHENKLIG' THEN p_wandstaerke * (p_hoehe + p_breite - p_wandstaerke) * v_dichte / 1000
        -- U-, T- und Z-Profile: Steg plus zwei Schenkel, ueberlappende Ecken
        -- werden einmal abgezogen.
        WHEN 'U_PROFIL' THEN p_wandstaerke * (p_hoehe + 2 * p_breite - 2 * p_wandstaerke) * v_dichte / 1000
        WHEN 'T_PROFIL' THEN p_wandstaerke * (p_hoehe + p_breite - p_wandstaerke) * v_dichte / 1000
        WHEN 'Z_PROFIL' THEN p_wandstaerke * (p_hoehe + 2 * p_breite - 2 * p_wandstaerke) * v_dichte / 1000
        ELSE NULL END;

    -- Bleche werden nach Flaeche gerechnet: Dicke in mm mal Dichte ergibt kg/m2.
    SET v_masse_qm = IF(v_ist_blech = 1, p_wandstaerke * v_dichte, NULL);

    -- Oberflaeche je laufendem Meter in m2 - Grundlage fuer Beschichtungskosten.
    SET v_mantel = CASE
        WHEN p_profilform IN ('RUNDROHR','RUNDSTAB') THEN PI() * p_durchmesser / 1000
        WHEN p_profilform = 'QUADRATROHR'            THEN 4 * p_hoehe / 1000
        WHEN p_profilform = 'RECHTECKROHR'           THEN 2 * (p_hoehe + p_breite) / 1000
        WHEN p_profilform = 'VIERKANTSTAB'           THEN 2 * (p_hoehe + p_breite) / 1000
        WHEN p_profilform IN ('FLACHSTAB','BREITFLACHSTAHL')
                                                     THEN 2 * (p_breite + p_wandstaerke) / 1000
        WHEN p_profilform IN ('WINKEL_GLEICHSCHENKLIG','WINKEL_UNGLEICHSCHENKLIG','T_PROFIL')
                                                     THEN 2 * (p_hoehe + p_breite) / 1000
        WHEN p_profilform IN ('U_PROFIL','Z_PROFIL')  THEN 2 * (p_hoehe + 2 * p_breite) / 1000
        WHEN v_ist_blech = 1                          THEN 2
        ELSE NULL END;

    SET v_quer = IF(v_dichte > 0 AND v_masse IS NOT NULL, v_masse / v_dichte * 10, NULL);
    SET v_einheit = IF(v_ist_blech = 1, 'QUADRATMETER', 'LAUFENDE_METER');

    -- ------------------------------------------------------------------
    -- Suchtext: alle Schreibweisen, damit "rundrohr 42.4 x2" trifft
    -- ------------------------------------------------------------------
    SET v_suchtext = LOWER(CONCAT_WS(' ',
        CASE p_profilform
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
            WHEN 'T_PROFIL'                 THEN 't-profil tprofil t profil'
            WHEN 'Z_PROFIL'                 THEN 'z-profil zprofil z profil'
            WHEN 'BLECH'                    THEN 'blech glattblech tafel platte'
            WHEN 'RIFFELBLECH'              THEN 'riffelblech traenenblech blech rutschhemmend'
            ELSE '' END,
        v_nummer,
        REPLACE(v_name, ' mm', ''),
        REPLACE(REPLACE(v_name, ' mm', ''), '.', ','),
        zahl_kurz(p_durchmesser), zahl_kurz(p_hoehe), zahl_kurz(p_breite), zahl_kurz(p_wandstaerke),
        REPLACE(zahl_kurz(p_durchmesser), '.', ','),
        p_werkstoff,
        (SELECT anzeigename FROM werkstoff WHERE id = v_werkstoff_id),
        p_massnorm,
        LOWER(p_herstellverfahren),
        LOWER(p_fertigungszustand)));

    -- ------------------------------------------------------------------
    -- Anlegen (JOINED-Vererbung: erst artikel, dann artikel_werkstoffe)
    -- ------------------------------------------------------------------
    INSERT INTO artikel (produktname, produktlinie, kategorie_id, werkstoff_id,
                         verrechnungseinheit, artikelnummer, suchtext, profilform,
                         massnorm, herstellverfahren, fertigungszustand, system_stammdaten)
    VALUES (v_name, p_massnorm, p_kategorie_id, v_werkstoff_id,
            v_einheit, v_nummer, v_suchtext, p_profilform,
            p_massnorm, p_herstellverfahren, p_fertigungszustand, b'1');

    SET v_artikel_id = LAST_INSERT_ID();

    INSERT INTO artikel_werkstoffe (id, masse_pro_meter, masse_pro_qm, mantelflaeche,
                                    geschliffen, durchmesser, hoehe, breite, wandstaerke,
                                    querschnittsflaeche, standardlaenge_mm)
    VALUES (v_artikel_id, v_masse, v_masse_qm, v_mantel,
            b'0', p_durchmesser, p_hoehe, p_breite, p_wandstaerke,
            v_quer, IF(v_ist_blech = 1, NULL, 6000));

END$$

DELIMITER ;
