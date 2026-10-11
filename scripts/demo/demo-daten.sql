-- Demo-Daten für eine frische, leere Datenbank: ein erfundener Metallbaubetrieb
-- mit Mitarbeitern, Zeitkonten, Kunden, Lieferanten, Anfragen, Projekten,
-- Zeitbuchungen, Materialkosten und Rechnungen. Alle Namen sind erfunden.
-- Datumswerte hängen an CURRENT_DATE, damit Auswertungen aktuelle Monate zeigen.
--
-- Aufruf: scripts/demo/demo-starten.sh (lädt die Datei nach dem ersten Start)
-- oder von Hand:
--   docker compose -p erp-demo exec -T postgres psql -U erp_user -d kalkulationsprogramm_db < scripts/demo/demo-daten.sql

-- Nummern im Format der App: Auftrag YYYY/MM/NNNNN (fortlaufend je Monat),
-- Rechnung {PREFIX}-YYYY/MM/NNNNN über den gemeinsamen Monatszähler.
CREATE OR REPLACE FUNCTION pg_temp.demo_auftragsnummer(datum date) RETURNS text AS $f$
    SELECT to_char(datum, 'YYYY/MM/')
           || lpad((SELECT count(*) + 1 FROM projekt WHERE auftragsnummer LIKE to_char(datum, 'YYYY/MM/') || '%')::text, 5, '0');
$f$ LANGUAGE sql;

CREATE OR REPLACE FUNCTION pg_temp.demo_rechnungsnummer(praefix text, datum date) RETURNS text AS $f$
    INSERT INTO ausgangs_geschaeftsdokument_counter (monat_key, zaehler)
    VALUES (to_char(datum, 'YYYY/MM'), 1)
    ON CONFLICT (monat_key) DO UPDATE SET zaehler = ausgangs_geschaeftsdokument_counter.zaehler + 1
    RETURNING praefix || '-' || monat_key || '/' || lpad(zaehler::text, 5, '0');
$f$ LANGUAGE sql;

DO $$
DECLARE
    abt_buero bigint; abt_werkstatt bigint; abt_montage bigint;
    ma_max bigint; ma_erika bigint; ma_jonas bigint; ma_lena bigint; ma_paul bigint;
    ag_planung bigint; ag_zuschnitt bigint; ag_schweissen bigint; ag_montage bigint; ag_oberflaeche bigint;
    pk_gelaender bigint; pk_balkon bigint; pk_treppe bigint; pk_vordach bigint; pk_tor bigint; pk_zaun bigint;
    lf_stahl bigint; lf_schrauben bigint; lf_verzinkerei bigint; lf_glas bigint;
    k bigint[];
    p bigint;
    ppk bigint;
    i int; d int; std numeric;
    arbeiter bigint[];
    gaenge bigint[];
    anteile numeric[];
    menge numeric; gesamt numeric; tag date; ist_treppe boolean;
    zk_start date; ma bigint; offen bigint[]; vormittag bigint; nachmittag bigint; neubau_ppk bigint;
    zk_modell bigint; nr text; datum date;
BEGIN
    -- Nur in eine frische Datenbank: Sobald jemand Stammdaten oder Zeiten
    -- angelegt hat, bleibt alles unverändert.
    IF EXISTS (SELECT 1 FROM kunde) OR EXISTS (SELECT 1 FROM projekt) OR EXISTS (SELECT 1 FROM lieferanten)
       OR EXISTS (SELECT 1 FROM abteilung) OR EXISTS (SELECT 1 FROM zeitbuchung)
       OR EXISTS (SELECT 1 FROM mitarbeiter WHERE art = 'MENSCH') THEN
        RAISE NOTICE 'Die Datenbank ist nicht leer, Demo-Daten werden nicht geladen.';
        RETURN;
    END IF;

    -- Firma ------------------------------------------------------------------
    INSERT INTO firmeninformation (id, version, firmenname, strasse, plz, ort, telefon, email, website,
                                   geschaeftsfuehrer, steuernummer, ust_id_nr, bank_name, iban, bic, gewerk_id,
                                   firmenfarbe)
    VALUES (1, 0, 'Mustermann Metallbau GmbH', 'Musterstraße 1', '12345', 'Musterstadt', '01234 567890',
            'info@mustermann-metallbau.example', 'www.mustermann-metallbau.example', 'Max Mustermann',
            '123/456/78901', 'DE123456789', 'Musterbank', 'DE00 1234 5678 9012 3456 78', 'MUSTDEFFXXX',
            (SELECT id FROM gewerk WHERE name LIKE 'Metallbau%' ORDER BY id LIMIT 1), '#e11d48')
    ON CONFLICT (id) DO NOTHING;

    -- Platzhalter für die Ersteinrichtung (Mailserver und KI-Schlüssel), damit die
    -- Demo ohne diesen Schritt startet. Mails gehen an eine ungültige Adresse. Die KI
    -- gilt als eingerichtet, ihre Aufrufe scheitern aber am ungültigen Schlüssel.
    -- Vorhandene Werte bleiben unverändert.
    INSERT INTO system_setting (setting_key, setting_value, beschreibung)
    VALUES ('smtp.host', 'smtp.demo.invalid', 'Demo'), ('smtp.port', '587', 'Demo'),
           ('smtp.username', 'demo@mustermann-metallbau.example', 'Demo'),
           ('smtp.password', 'nur-demo-kein-passwort', 'Demo'),
           ('ai.gemini.api-key', 'nur-demo-kein-schluessel', 'Demo')
    ON CONFLICT (setting_key) DO UPDATE SET setting_value = excluded.setting_value
    WHERE system_setting.setting_value IS NULL OR system_setting.setting_value = '';

    -- Abteilungen und Mitarbeiter ----------------------------------------------
    INSERT INTO abteilung (name, darf_rechnungen_genehmigen, darf_rechnungen_sehen, darf_monat_abschliessen)
    VALUES ('Büro', true, true, true) RETURNING id INTO abt_buero;
    INSERT INTO abteilung (name, darf_rechnungen_genehmigen, darf_rechnungen_sehen)
    VALUES ('Werkstatt', false, false) RETURNING id INTO abt_werkstatt;
    INSERT INTO abteilung (name, darf_rechnungen_genehmigen, darf_rechnungen_sehen)
    VALUES ('Montage', false, false) RETURNING id INTO abt_montage;

    INSERT INTO mitarbeiter (vorname, nachname, aktiv, art, beschaeftigungsart, qualifikation, stundenlohn,
                             jahres_urlaub, eintrittsdatum, fuehrt_zeitkonto, ist_geschaeftsfuehrer, email, version)
    VALUES ('Max', 'Mustermann', true, 'MENSCH', 'GF_SV_FREI', 'MEISTER', 45, 30, DATE '2012-04-01', false, true,
            'max@mustermann-metallbau.example', 0) RETURNING id INTO ma_max;
    INSERT INTO mitarbeiter (vorname, nachname, aktiv, art, beschaeftigungsart, qualifikation, stundenlohn,
                             jahres_urlaub, eintrittsdatum, fuehrt_zeitkonto, version)
    VALUES ('Erika', 'Musterfrau', true, 'MENSCH', 'REGULAER', 'FACHARBEITER', 22, 30, DATE '2016-09-01', false, 0)
    RETURNING id INTO ma_erika;
    INSERT INTO mitarbeiter (vorname, nachname, aktiv, art, beschaeftigungsart, qualifikation, stundenlohn,
                             jahres_urlaub, eintrittsdatum, fuehrt_zeitkonto, version)
    VALUES ('Jonas', 'Beispiel', true, 'MENSCH', 'REGULAER', 'FACHARBEITER', 24, 28, DATE '2018-08-01', true, 0)
    RETURNING id INTO ma_jonas;
    INSERT INTO mitarbeiter (vorname, nachname, aktiv, art, beschaeftigungsart, qualifikation, stundenlohn,
                             jahres_urlaub, eintrittsdatum, fuehrt_zeitkonto, version)
    VALUES ('Lena', 'Probe', true, 'MENSCH', 'REGULAER', 'FACHARBEITER', 23, 28, DATE '2020-02-15', true, 0)
    RETURNING id INTO ma_lena;
    INSERT INTO mitarbeiter (vorname, nachname, aktiv, art, beschaeftigungsart, qualifikation, stundenlohn,
                             jahres_urlaub, eintrittsdatum, fuehrt_zeitkonto, version)
    VALUES ('Paul', 'Platzhalter', true, 'MENSCH', 'REGULAER', 'AUSZUBILDENDER', 14, 28, DATE '2023-09-01', true, 0)
    RETURNING id INTO ma_paul;

    -- Anmelde-Code für die Handy-App (QR-Code in der Mitarbeiterverwaltung)
    UPDATE mitarbeiter SET login_token = gen_random_uuid()::text WHERE art = 'MENSCH' AND login_token IS NULL;

    INSERT INTO mitarbeiter_abteilung (mitarbeiter_id, abteilung_id) VALUES
        (ma_max, abt_buero), (ma_erika, abt_buero), (ma_jonas, abt_werkstatt),
        (ma_lena, abt_werkstatt), (ma_paul, abt_montage), (ma_jonas, abt_montage);

    INSERT INTO mitarbeiter_stundenlohn (mitarbeiter_id, gueltig_ab, stundenlohn)
    SELECT id, make_date(EXTRACT(YEAR FROM CURRENT_DATE)::int - 3, 1, 1), stundenlohn FROM mitarbeiter WHERE id IN (ma_max, ma_erika, ma_jonas, ma_lena, ma_paul);

    -- Zeiterfassung seit Anfang des Vormonats: ältere Monate muss niemand abschließen
    INSERT INTO system_setting (setting_key, setting_value, beschreibung)
    VALUES ('zeiterfassung.eingefuehrt-am', to_char(date_trunc('month', CURRENT_DATE) - interval '1 month', 'YYYY-MM-DD'),
            'Ab diesem Tag nutzt der Betrieb die Zeiterfassung; frühere Monate müssen nicht abgeschlossen werden')
    ON CONFLICT (setting_key) DO NOTHING;

    -- Arbeitsgänge mit Stundensätzen ------------------------------------------
    INSERT INTO arbeitsgang (beschreibung, abteilung_id, version) VALUES ('Planung & Aufmaß', abt_buero, 0) RETURNING id INTO ag_planung;
    INSERT INTO arbeitsgang (beschreibung, abteilung_id, version) VALUES ('Zuschnitt', abt_werkstatt, 0) RETURNING id INTO ag_zuschnitt;
    INSERT INTO arbeitsgang (beschreibung, abteilung_id, version) VALUES ('Schweißen', abt_werkstatt, 0) RETURNING id INTO ag_schweissen;
    INSERT INTO arbeitsgang (beschreibung, abteilung_id, version) VALUES ('Montage', abt_montage, 0) RETURNING id INTO ag_montage;
    INSERT INTO arbeitsgang (beschreibung, abteilung_id, version) VALUES ('Oberfläche', abt_werkstatt, 0) RETURNING id INTO ag_oberflaeche;

    INSERT INTO arbeitsgang_stundensatz (arbeitsgang_id, jahr, satz)
    SELECT a.id, j.jahr, s.satz
    FROM (VALUES (ag_planung, 72), (ag_zuschnitt, 62), (ag_schweissen, 68), (ag_montage, 65), (ag_oberflaeche, 58)) AS s(ag, satz)
    JOIN arbeitsgang a ON a.id = s.ag
    CROSS JOIN generate_series(EXTRACT(YEAR FROM CURRENT_DATE)::int - 3, EXTRACT(YEAR FROM CURRENT_DATE)::int) AS j(jahr);

    -- Produktkategorien und Leistungen ----------------------------------------
    INSERT INTO produktkategorie (bezeichnung, verrechnungseinheit, beschreibung, version)
    VALUES ('Treppengeländer', 'LAUFENDE_METER', 'Geländer innen und außen, Stahl oder Edelstahl', 0) RETURNING id INTO pk_gelaender;
    INSERT INTO produktkategorie (bezeichnung, verrechnungseinheit, beschreibung, version)
    VALUES ('Balkongeländer', 'LAUFENDE_METER', 'Balkon- und Brüstungsgeländer, feuerverzinkt', 0) RETURNING id INTO pk_balkon;
    INSERT INTO produktkategorie (bezeichnung, verrechnungseinheit, beschreibung, version)
    VALUES ('Stahltreppe', 'STUECK', 'Außen- und Wangentreppen', 0) RETURNING id INTO pk_treppe;
    INSERT INTO produktkategorie (bezeichnung, verrechnungseinheit, beschreibung, version)
    VALUES ('Vordach', 'STUECK', 'Vordächer aus Stahl und Glas', 0) RETURNING id INTO pk_vordach;
    INSERT INTO produktkategorie (bezeichnung, verrechnungseinheit, beschreibung, version)
    VALUES ('Hoftor', 'STUECK', 'Dreh- und Schiebetore', 0) RETURNING id INTO pk_tor;
    INSERT INTO produktkategorie (bezeichnung, verrechnungseinheit, beschreibung, version)
    VALUES ('Zaunanlage', 'LAUFENDE_METER', 'Stabgitter- und Designzäune', 0) RETURNING id INTO pk_zaun;

    INSERT INTO leistung (bezeichnung, einheit, preis, kategorie_id, beschreibung) VALUES
        ('Treppengeländer Flachstahl, feuerverzinkt', 'LAUFENDE_METER', 289.00, pk_gelaender, 'Pfosten 40x10, Handlauf Rundrohr 42,4, Füllung Rundstab'),
        ('Edelstahl-Handlauf Ø 42,4 mm', 'LAUFENDE_METER', 145.00, pk_gelaender, 'V2A geschliffen, inkl. Wandhalter'),
        ('Balkongeländer mit Lochblechfüllung', 'LAUFENDE_METER', 410.00, pk_balkon, 'feuerverzinkt und pulverbeschichtet RAL 7016'),
        ('Außentreppe, 12 Stufen, Gitterroststufen', 'STUECK', 6850.00, pk_treppe, 'Wangen U-Profil, inkl. Podest'),
        ('Vordach Stahl/VSG 2,0 x 1,2 m', 'STUECK', 2380.00, pk_vordach, 'Konsolen und Zugstangen, VSG 2x8 mm'),
        ('Drehtor zweiflügelig 3,5 m', 'STUECK', 3950.00, pk_tor, 'inkl. Pfosten, Bodenriegel und Schloss'),
        ('Stabgitterzaun 1,4 m hoch', 'LAUFENDE_METER', 96.00, pk_zaun, 'Doppelstabmatte 8/6/8, RAL 6005');

    -- Lieferanten -------------------------------------------------------------
    INSERT INTO lieferanten (lieferantenname, lieferanten_typ, strasse, plz, ort, telefon, ist_aktiv, start_zusammenarbeit, version)
    VALUES ('Stahlhandel Beispiel KG', 'Lieferant', 'Industriestraße 5', '12345', 'Musterstadt', '01234 1111', true, TIMESTAMP '2015-01-01', 0) RETURNING id INTO lf_stahl;
    INSERT INTO lieferanten (lieferantenname, lieferanten_typ, strasse, plz, ort, telefon, ist_aktiv, start_zusammenarbeit, version)
    VALUES ('Schrauben Muster GmbH', 'Lieferant', 'Am Lager 12', '23456', 'Beispielheim', '01234 2222', true, TIMESTAMP '2017-05-01', 0) RETURNING id INTO lf_schrauben;
    INSERT INTO lieferanten (lieferantenname, lieferanten_typ, strasse, plz, ort, telefon, ist_aktiv, start_zusammenarbeit, version)
    VALUES ('Verzinkerei Probe', 'Dienstleister', 'Zinkweg 3', '34567', 'Probstadt', '01234 3333', true, TIMESTAMP '2014-03-01', 0) RETURNING id INTO lf_verzinkerei;
    INSERT INTO lieferanten (lieferantenname, lieferanten_typ, strasse, plz, ort, telefon, ist_aktiv, start_zusammenarbeit, version)
    VALUES ('Glas Exempel GmbH', 'Lieferant', 'Scheibenallee 8', '45678', 'Exempelburg', '01234 4444', true, TIMESTAMP '2019-07-01', 0) RETURNING id INTO lf_glas;

    -- Kunden ------------------------------------------------------------------
    WITH neu AS (
        INSERT INTO kunde (kundennummer, anrede, name, ansprechspartner, strasse, plz, ort, telefon, zahlungsziel, version)
        VALUES
            ('1001', 'HERR', 'Max Mustermann', NULL, 'Lindenweg 4', '12345', 'Musterstadt', '01234 100100', 14, 0),
            ('1002', 'FRAU', 'Erika Beispiel', NULL, 'Gartenstraße 17', '12347', 'Musterstadt', '01234 100200', 14, 0),
            ('1003', 'FIRMA', 'Wohnbau Exempel GmbH', 'Herr Probe', 'Hafenstraße 2', '23456', 'Beispielheim', '01234 300300', 30, 0),
            ('1004', 'FAMILIE', 'Familie Muster', NULL, 'Am Bach 9', '34567', 'Probstadt', '01234 400400', 14, 0),
            ('1005', 'FIRMA', 'Autohaus Platzhalter', 'Frau Muster', 'Gewerbering 21', '12345', 'Musterstadt', '01234 500500', 21, 0),
            ('1006', 'HERR', 'Jan Probe', NULL, 'Kirchplatz 1', '45678', 'Exempelburg', '01234 600600', 14, 0),
            ('1007', 'FIRMA', 'Gemeinde Musterdorf', 'Bauamt', 'Rathausplatz 1', '56789', 'Musterdorf', '01234 700700', 30, 0),
            ('1008', 'FRAU', 'Anna Exempel', NULL, 'Sonnenhang 5', '12349', 'Musterstadt', '01234 800800', 14, 0)
        RETURNING id, kundennummer
    )
    SELECT array_agg(id ORDER BY kundennummer) INTO k FROM neu;

    INSERT INTO kunden_emails (kunden_id, email)
    SELECT k[n], 'kunde' || n || '@example.com' FROM generate_series(1, 8) AS n;

    UPDATE kunden_zaehler SET naechste_nummer = 1009 WHERE naechste_nummer < 1009;

    -- Anfragen (noch kein Projekt) ---------------------------------------------
    INSERT INTO anfrage (kunde_id, bauvorhaben, kurzbeschreibung, projekt_strasse, projekt_plz, projekt_ort, betrag,
                         anlegedatum, created_at, abgeschlossen, version)
    VALUES
        (k[6], 'Kellertreppe Probe', 'Geländer an der Kellertreppe, ca. 4 m, innen', 'Kirchplatz 1', '45678', 'Exempelburg', 1450, CURRENT_DATE - 3, now() - interval '3 days', false, 0),
        (k[8], 'Vordach Hauseingang', 'Vordach mit Glas über der Haustür', 'Sonnenhang 5', '12349', 'Musterstadt', 2600, CURRENT_DATE - 9, now() - interval '9 days', false, 0),
        (k[7], 'Zaun Spielplatz', 'Stabgitterzaun 60 m mit zwei Toren', 'Am Spielplatz', '56789', 'Musterdorf', 8900, CURRENT_DATE - 16, now() - interval '16 days', false, 0),
        (k[5], 'Schiebetor Werkstatthof', 'Freitragendes Schiebetor 6 m', 'Gewerbering 21', '12345', 'Musterstadt', 7400, CURRENT_DATE - 24, now() - interval '24 days', false, 0);

    -- Projekte ----------------------------------------------------------------
    arbeiter := ARRAY[ma_jonas, ma_lena, ma_paul, ma_max];
    gaenge := ARRAY[ag_planung, ag_zuschnitt, ag_schweissen, ag_montage, ag_oberflaeche];

    FOR i IN 1..8 LOOP
        INSERT INTO projekt (auftragsnummer, bauvorhaben, kurzbeschreibung, kunden_id, strasse, plz, ort, projekt_art,
                             brutto_preis, anlegedatum, abgeschlossen, abschlussdatum, bezahlt, version)
        SELECT pg_temp.demo_auftragsnummer(CURRENT_DATE - v.alter_tage),
               v.bv, v.kb, k[v.kunde], v.str, v.plz, v.ort, v.art, v.preis,
               CURRENT_DATE - v.alter_tage, v.fertig, CASE WHEN v.fertig THEN CURRENT_DATE - v.alter_tage + 45 END,
               v.bezahlt, 0
        FROM (VALUES
            (1, 'Treppengeländer EFH Mustermann', 'Innengeländer Stahl mit Edelstahl-Handlauf', 1, 'Lindenweg 4', '12345', 'Musterstadt', 'PAUSCHAL', 4862.00, 210, true, true),
            (2, 'Balkongeländer Wohnanlage Hafen', '6 Balkone, Lochblech, RAL 7016', 3, 'Hafenstraße 2', '23456', 'Beispielheim', 'PAUSCHAL', 23418.00, 160, true, true),
            (3, 'Außentreppe Familie Muster', 'Fluchttreppe Garten, verzinkt', 4, 'Am Bach 9', '34567', 'Probstadt', 'PAUSCHAL', 8151.50, 120, true, false),
            (4, 'Hoftor Autohaus', 'Zweiflügeliges Drehtor mit Antrieb', 5, 'Gewerbering 21', '12345', 'Musterstadt', 'PAUSCHAL', 6545.00, 90, true, true),
            (5, 'Vordach Beispiel', 'Vordach Stahl/Glas über Hauseingang', 2, 'Gartenstraße 17', '12347', 'Musterstadt', 'PAUSCHAL', 2832.20, 55, false, false),
            (6, 'Zaun Bauhof Musterdorf', '120 m Stabgitterzaun', 7, 'Rathausplatz 1', '56789', 'Musterdorf', 'PAUSCHAL', 15350.00, 35, false, false),
            (7, 'Reparatur Geländer Wohnanlage', 'Regiearbeiten nach Sturmschaden', 3, 'Hafenstraße 2', '23456', 'Beispielheim', 'REGIE', 1890.00, 20, false, false),
            (8, 'Balkongeländer Altbau Exempel', 'Zwei französische Balkone', 8, 'Sonnenhang 5', '12349', 'Musterstadt', 'PAUSCHAL', 3570.00, 8, false, false)
        ) AS v(nr, bv, kb, kunde, str, plz, ort, art, preis, alter_tage, fertig, bezahlt)
        WHERE v.nr = i
        RETURNING id INTO p;

        -- Produktkategorie mit Menge passend zum Projekt
        INSERT INTO projekt_produktkategorie (projekt_id, produktkategorie_id, menge)
        VALUES (p, (ARRAY[pk_gelaender, pk_balkon, pk_treppe, pk_tor, pk_vordach, pk_zaun, pk_gelaender, pk_balkon])[i],
                (ARRAY[14, 48, 1, 1, 1, 120, 6, 4])[i])
        RETURNING id INTO ppk;

        -- Zeitbuchungen der fertigen Projekte; laufende Projekte bekommen weiter unten
        -- lückenlose Arbeitstage, passend zum Zeitkonto
        FOR d IN 0..(CASE WHEN i <= 4 THEN (ARRAY[9, 22, 12, 10])[i] ELSE -1 END) LOOP
            std := 4 + ((i * 7 + d * 3) % 5);
            INSERT INTO zeitbuchung (mitarbeiter_id, projekt_id, projekt_produktkategorie_id, arbeitsgang_id,
                                     arbeitsgang_stundensatz_id, start_zeit, ende_zeit, anzahl_in_stunden, typ,
                                     erfasst_via, erfasst_am, erfasst_von_mitarbeiter_id, version)
            SELECT arbeiter[1 + (d + i) % 4], p, ppk, g.ag, s.id,
                   t.tag + time '07:00', t.tag + time '07:00' + std * interval '1 hour', std, 'ARBEIT',
                   'MOBILE_APP', t.tag + time '07:00', arbeiter[1 + (d + i) % 4], 0
            FROM (SELECT gaenge[1 + (d % 5)] AS ag) g
            CROSS JOIN LATERAL (
                SELECT (CURRENT_DATE - (ARRAY[210, 160, 120, 90, 55, 35, 20, 8])[i] + 7 + d * 2)::timestamp AS tag
            ) t
            JOIN arbeitsgang_stundensatz s ON s.arbeitsgang_id = g.ag AND s.jahr = EXTRACT(YEAR FROM t.tag)::int
            WHERE t.tag::date < CURRENT_DATE;
        END LOOP;

        -- Materialkosten
        INSERT INTO materialkosten (projekt_id, lieferant_id, beschreibung, betrag, rechnungsnummer, monat)
        VALUES (p, lf_stahl, 'Flachstahl, Rohre und Bleche', round((ARRAY[620, 3900, 1450, 980, 310, 2650, 120, 420])[i]::numeric, 2),
                'SB-' || (24000 + i * 17), EXTRACT(MONTH FROM CURRENT_DATE - (ARRAY[210, 160, 120, 90, 55, 35, 20, 8])[i] + 10)::int);
        IF i IN (2, 3, 4, 6) THEN
            INSERT INTO materialkosten (projekt_id, lieferant_id, beschreibung, betrag, rechnungsnummer, monat)
            VALUES (p, lf_verzinkerei, 'Feuerverzinken', (ARRAY[0, 1680, 540, 410, 0, 1320, 0, 0])[i], 'VZ-' || (8100 + i),
                    EXTRACT(MONTH FROM CURRENT_DATE - (ARRAY[210, 160, 120, 90, 55, 35, 20, 8])[i] + 20)::int);
        END IF;
        IF i = 5 THEN
            INSERT INTO materialkosten (projekt_id, lieferant_id, beschreibung, betrag, rechnungsnummer)
            VALUES (p, lf_glas, 'VSG 2x8 mm, 2000 x 1200', 465.80, 'GE-5521');
        END IF;
        INSERT INTO materialkosten (projekt_id, lieferant_id, beschreibung, betrag, rechnungsnummer)
        VALUES (p, lf_schrauben, 'Befestigungsmaterial', 48 + i * 11, 'SM-' || (700 + i));

        -- Rechnungen für die Erfolgsanalyse: Schlussrechnung bei fertigen Projekten,
        -- Abschlagsrechnung beim laufenden Zaun-Projekt. Eine PDF-Datei gibt es dazu nicht.
        IF i <= 4 OR i = 6 THEN
            datum := CURRENT_DATE - (ARRAY[210, 160, 120, 90, 55, 35, 20, 8])[i] + 40;
            nr := pg_temp.demo_rechnungsnummer(CASE WHEN i = 6 THEN 'AR' ELSE 'SR' END, datum);
            WITH dok AS (
                INSERT INTO projekt_dokument (projekt, dokument_gruppe, original_dateiname, gespeicherter_dateiname,
                                              dateityp, upload_datum)
                VALUES (p, 'GESCHAEFTSDOKUMENTE', replace(nr, '/', '-') || '.pdf', 'demo-' || replace(nr, '/', '-') || '.pdf',
                        'pdf', datum)
                RETURNING id, upload_datum
            )
            INSERT INTO projekt_geschaeftsdokument (id, dokumentid, geschaeftsdokumentart, bezahlt, brutto_betrag,
                                                    rechnungsdatum, faelligkeitsdatum, system_generiert)
            SELECT dok.id, nr,
                   CASE WHEN i = 6 THEN 'Abschlagsrechnung' ELSE 'Schlussrechnung' END,
                   i IN (1, 2, 4, 6),
                   CASE WHEN i = 6 THEN 6000.00 ELSE (ARRAY[4862.00, 23418.00, 8151.50, 6545.00])[i] END,
                   dok.upload_datum, dok.upload_datum + 14, true
            FROM dok;
        END IF;

        INSERT INTO projekt_notiz (projekt_id, mitarbeiter_id, notiz, erstellt_am, mobile_sichtbar, nur_fuer_ersteller)
        VALUES (p, ma_max, (ARRAY[
            'Aufmaß erledigt, Handlauf auf Wunsch in Edelstahl.',
            'Lochbleche bei der Verzinkerei angefragt, Liefertermin KW folgt.',
            'Fundamente durch den Kunden, Anker gesetzt.',
            'Antrieb vom Kunden beigestellt.',
            'Glas bestellt, Montage nach Lieferung.',
            'Bauhof will Tore in RAL 6005.',
            'Zwei Pfosten abgerissen, Fotos im Tagebuch.',
            'Aufmaß nächste Woche.'])[i], now() - interval '2 days' * i, true, false);
    END LOOP;


    -- Großes laufendes Projekt mit Abschlagsrechnungen, auf das die Werkstatt
    -- vormittags bucht
    INSERT INTO projekt (auftragsnummer, bauvorhaben, kurzbeschreibung, kunden_id, strasse, plz, ort, projekt_art,
                         brutto_preis, anlegedatum, abgeschlossen, bezahlt, version)
    VALUES (pg_temp.demo_auftragsnummer(CURRENT_DATE - 60), 'Neubau Wohnanlage Hafen, Haus B',
            '160 m Balkongeländer und zwei Außentreppen', k[3], 'Hafenstraße 4', '23456', 'Beispielheim', 'PAUSCHAL',
            78064.00, CURRENT_DATE - 60, false, false, 0)
    RETURNING id INTO p;
    INSERT INTO projekt_produktkategorie (projekt_id, produktkategorie_id, menge) VALUES (p, pk_balkon, 160)
    RETURNING id INTO neubau_ppk;
    INSERT INTO materialkosten (projekt_id, lieferant_id, beschreibung, betrag, rechnungsnummer)
    VALUES (p, lf_stahl, 'Lochbleche, Flachstahl, Rohre', 11840.00, 'SB-24410'),
           (p, lf_verzinkerei, 'Feuerverzinken und Pulvern', 6920.00, 'VZ-8190');
    FOR i IN 1..3 LOOP
        datum := CURRENT_DATE - (ARRAY[35, 12, 2])[i];
        nr := pg_temp.demo_rechnungsnummer('AR', datum);
        WITH dok AS (
            INSERT INTO projekt_dokument (projekt, dokument_gruppe, original_dateiname, gespeicherter_dateiname,
                                          dateityp, upload_datum)
            VALUES (p, 'GESCHAEFTSDOKUMENTE', replace(nr, '/', '-') || '.pdf', 'demo-' || replace(nr, '/', '-') || '.pdf',
                    'pdf', datum)
            RETURNING id, upload_datum
        )
        INSERT INTO projekt_geschaeftsdokument (id, dokumentid, geschaeftsdokumentart, bezahlt, brutto_betrag,
                                                rechnungsdatum, faelligkeitsdatum, system_generiert)
        SELECT dok.id, nr, 'Abschlagsrechnung',
               i < 3, (ARRAY[40000.00, 30000.00, 5000.00])[i], dok.upload_datum, dok.upload_datum + 14, true
        FROM dok;
    END LOOP;

    -- Zeitkonten: 40-Stunden-Woche seit Anfang des Vormonats (wie die Einführung der
    -- Zeiterfassung oben). Danach je Arbeitstag 8,5 h: vormittags auf den Neubau,
    -- nachmittags reihum auf die übrigen laufenden Projekte.
    zk_start := date_trunc('month', CURRENT_DATE - interval '1 month')::date;
    INSERT INTO zeitkontenmodell (bezeichnung, montag_stunden, dienstag_stunden, mittwoch_stunden, donnerstag_stunden,
                                  freitag_stunden, samstag_stunden, sonntag_stunden, version)
    VALUES ('Vollzeit 40 Stunden', 8, 8, 8, 8, 8, 0, 0, 0)
    RETURNING id INTO zk_modell;
    INSERT INTO zeitkonto_version (mitarbeiter_id, gueltig_von, montag_stunden, dienstag_stunden, mittwoch_stunden,
                                   donnerstag_stunden, freitag_stunden, samstag_stunden, sonntag_stunden,
                                   vorlage_id, version)
    SELECT m.id, zk_start, 8, 8, 8, 8, 8, 0, 0, zk_modell, 0
    FROM mitarbeiter m WHERE m.id IN (ma_jonas, ma_lena, ma_paul);
    -- Der Gesamtsaldo zählt ab dem Eintrittsdatum. Ältere Demo-Buchungen ohne Soll
    -- würden sonst als Überstunden erscheinen.
    UPDATE mitarbeiter SET eintrittsdatum = zk_start WHERE id IN (ma_jonas, ma_lena, ma_paul);

    FOR tag IN SELECT g::date FROM generate_series(zk_start, CURRENT_DATE - 1, interval '1 day') AS g LOOP
        CONTINUE WHEN EXTRACT(ISODOW FROM tag) > 5 OR EXISTS (SELECT 1 FROM feiertag f WHERE f.datum = tag);
        SELECT array_agg(pp.id ORDER BY p2.anlegedatum) INTO offen
        FROM projekt p2 JOIN projekt_produktkategorie pp ON pp.projekt_id = p2.id
        WHERE NOT p2.abgeschlossen AND p2.anlegedatum <= tag;
        CONTINUE WHEN offen IS NULL;
        i := 0;
        FOREACH ma IN ARRAY ARRAY[ma_jonas, ma_lena, ma_paul] LOOP
            i := i + 1;
            vormittag := neubau_ppk;
            nachmittag := offen[1 + (EXTRACT(DOY FROM tag)::int + i * 3 + 1) % array_length(offen, 1)];
            INSERT INTO zeitbuchung (mitarbeiter_id, projekt_id, projekt_produktkategorie_id, arbeitsgang_id,
                                     arbeitsgang_stundensatz_id, start_zeit, ende_zeit, anzahl_in_stunden, typ,
                                     erfasst_via, erfasst_am, erfasst_von_mitarbeiter_id, version)
            SELECT ma, pp.projekt_id, pp.id, b.ag, s.id, tag + b.von, tag + b.bis,
                   EXTRACT(EPOCH FROM b.bis - b.von) / 3600, 'ARBEIT', 'MOBILE_APP', tag + b.bis, ma, 0
            FROM (VALUES (vormittag, time '07:00', time '11:30',
                          gaenge[2 + (i + EXTRACT(DOY FROM tag)::int) % 4]),
                         (nachmittag, time '12:00', time '16:00',
                          gaenge[2 + (i + EXTRACT(DOY FROM tag)::int + 1) % 4])
                 ) AS b(ppk_id, von, bis, ag)
            JOIN projekt_produktkategorie pp ON pp.id = b.ppk_id
            JOIN arbeitsgang_stundensatz s ON s.arbeitsgang_id = b.ag AND s.jahr = EXTRACT(YEAR FROM tag)::int;
        END LOOP;
    END LOOP;

    -- Abgeschlossene Geländer-Projekte der letzten zwei Jahre: Datenbasis für die
    -- Nachkalkulation je Produktkategorie (Fixzeit + Zeit je Meter per Regression).
    anteile := ARRAY[0.12, 0.15, 0.30, 0.30, 0.13];
    FOR i IN 1..15 LOOP
        ist_treppe := i <= 9;
        menge := (ARRAY[3, 5, 7, 8, 10, 12, 15, 18, 22, 6, 9, 14, 20, 26, 32])[i];
        gesamt := round(CASE WHEN ist_treppe THEN 3.0 + 2.1 * menge ELSE 6.0 + 1.6 * menge END
                        + ((i * 37) % 7 - 3) * 0.9, 1);
        tag := CURRENT_DATE - 75 - i * 32;

        INSERT INTO projekt (auftragsnummer, bauvorhaben, kurzbeschreibung, kunden_id, strasse, plz, ort, projekt_art,
                             brutto_preis, anlegedatum, abgeschlossen, abschlussdatum, bezahlt, version)
        VALUES (pg_temp.demo_auftragsnummer(tag),
                format('%s %s', CASE WHEN ist_treppe THEN 'Treppengeländer' ELSE 'Balkongeländer' END,
                       (ARRAY['Musterweg', 'Am Hang', 'Lindenhof', 'Schulstraße', 'Mühlbach', 'Bergstraße', 'Talblick', 'Am Markt'])[1 + i % 8]),
                format('%s m, feuerverzinkt', menge), k[1 + i % 8], 'Musterweg ' || i, '12345', 'Musterstadt', 'PAUSCHAL',
                round(menge * CASE WHEN ist_treppe THEN 289 ELSE 410 END * 1.19, 2), tag, true, tag + 30, true, 0)
        RETURNING id INTO p;

        INSERT INTO projekt_produktkategorie (projekt_id, produktkategorie_id, menge)
        VALUES (p, CASE WHEN ist_treppe THEN pk_gelaender ELSE pk_balkon END, menge)
        RETURNING id INTO ppk;

        FOR d IN 1..5 LOOP
            std := round(gesamt * anteile[d], 1);
            INSERT INTO zeitbuchung (mitarbeiter_id, projekt_id, projekt_produktkategorie_id, arbeitsgang_id,
                                     arbeitsgang_stundensatz_id, start_zeit, ende_zeit, anzahl_in_stunden, typ,
                                     erfasst_via, erfasst_am, erfasst_von_mitarbeiter_id, version)
            SELECT arbeiter[1 + (d + i) % 4], p, ppk, gaenge[d], s.id,
                   (tag + d * 2) + time '07:00', (tag + d * 2) + time '07:00' + std * interval '1 hour', std, 'ARBEIT',
                   'MOBILE_APP', (tag + d * 2) + time '07:00', arbeiter[1 + (d + i) % 4], 0
            FROM arbeitsgang_stundensatz s
            WHERE s.arbeitsgang_id = gaenge[d] AND s.jahr = EXTRACT(YEAR FROM tag + d * 2)::int;
        END LOOP;

        INSERT INTO materialkosten (projekt_id, lieferant_id, beschreibung, betrag, rechnungsnummer)
        VALUES (p, lf_stahl, 'Stahl und Verzinkung', round(menge * CASE WHEN ist_treppe THEN 62 ELSE 95 END, 2), 'SB-' || (23000 + i));

        nr := pg_temp.demo_rechnungsnummer('SR', tag + 30);
        WITH dok AS (
            INSERT INTO projekt_dokument (projekt, dokument_gruppe, original_dateiname, gespeicherter_dateiname,
                                          dateityp, upload_datum)
            VALUES (p, 'GESCHAEFTSDOKUMENTE', replace(nr, '/', '-') || '.pdf', 'demo-' || replace(nr, '/', '-') || '.pdf',
                    'pdf', tag + 30)
            RETURNING id, upload_datum
        )
        INSERT INTO projekt_geschaeftsdokument (id, dokumentid, geschaeftsdokumentart, bezahlt, brutto_betrag,
                                                rechnungsdatum, faelligkeitsdatum, system_generiert)
        SELECT dok.id, nr, 'Schlussrechnung', true,
               round(menge * CASE WHEN ist_treppe THEN 289 ELSE 410 END * 1.19, 2),
               dok.upload_datum, dok.upload_datum + 14, true
        FROM dok;
    END LOOP;

    RAISE NOTICE 'Demo-Daten geladen: 5 Mitarbeiter, 8 Kunden, 4 Lieferanten, 4 Anfragen, 24 Projekte.';
END $$;
