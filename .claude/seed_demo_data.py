#!/usr/bin/env python3
"""
Demo-Daten Seed Script für ERP-System Handwerksbetriebe
========================================================
DSGVO-konform: Ausschliesslich fiktive Musterdaten.
Keine echten Personen, Adressen oder E-Mail-Adressen.

Preis-Modell fuer lineare Regression:
  Edelstahlgelaender:   800 + menge * 175  EUR/m
  Stahlgelaender:       500 + menge * 115  EUR/m
  Schiebetor:          1500 + anzahl * 2200 EUR/Stk
  Drehtuer Stahl:       800 + anzahl * 1100 EUR/Stk
  Stahlzaun:            400 + menge * 90   EUR/m
  Stahltreppe Aussen:  1200 + anzahl * 2800 EUR/Stk

Aufruf: python seed_demo_data.py
"""

import urllib.request
import urllib.error
import json
import ssl
import sys
import time
import uuid
from datetime import date, timedelta

BASE_URL = "https://marvins-laptop.tail7cd296.ts.net"

# SSL-Verifikation fuer self-signed Zertifikat deaktivieren
ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE


# ─────────────────────────────────────────────────────────────
# HTTP-Helpers
# ─────────────────────────────────────────────────────────────

def api_json(method, path, data=None):
    url = BASE_URL + path
    body = json.dumps(data, ensure_ascii=False).encode("utf-8") if data is not None else None
    req = urllib.request.Request(url, data=body, method=method)
    req.add_header("Content-Type", "application/json; charset=utf-8")
    req.add_header("Accept", "application/json")
    try:
        with urllib.request.urlopen(req, context=ctx, timeout=20) as resp:
            text = resp.read().decode("utf-8")
            return json.loads(text) if text else {}
    except urllib.error.HTTPError as e:
        msg = e.read().decode("utf-8")
        print(f"  X HTTP {e.code} {method} {path}: {msg[:300]}")
        return None
    except Exception as e:
        print(f"  X Fehler {method} {path}: {e}")
        return None


def make_multipart(fields):
    """Baut einen multipart/form-data Body ohne externe Bibliotheken."""
    boundary = uuid.uuid4().hex
    parts = []
    for name, value in fields.items():
        if value is None:
            continue
        parts.append(
            f"--{boundary}\r\n"
            f'Content-Disposition: form-data; name="{name}"\r\n'
            f"\r\n"
            f"{value}\r\n"
        )
    parts.append(f"--{boundary}--\r\n")
    body = "".join(parts).encode("utf-8")
    content_type = f"multipart/form-data; boundary={boundary}"
    return body, content_type


def api_multipart(path, fields):
    url = BASE_URL + path
    body, content_type = make_multipart(fields)
    req = urllib.request.Request(url, data=body, method="POST")
    req.add_header("Content-Type", content_type)
    req.add_header("Accept", "application/json")
    try:
        with urllib.request.urlopen(req, context=ctx, timeout=20) as resp:
            text = resp.read().decode("utf-8")
            return json.loads(text) if text else {}
    except urllib.error.HTTPError as e:
        msg = e.read().decode("utf-8")
        print(f"  X HTTP {e.code} POST {path}: {msg[:300]}")
        return None
    except Exception as e:
        print(f"  X Fehler POST {path}: {e}")
        return None


def post(path, data):
    return api_json("POST", path, data)


def get(path):
    return api_json("GET", path)


def section(title):
    print(f"\n{'='*60}")
    print(f"  {title}")
    print(f"{'='*60}")


def ok(msg):
    print(f"  + {msg}")


def warn(msg):
    print(f"  ! {msg}")


# ─────────────────────────────────────────────────────────────
# Preisberechnung (deterministisch, leichte Variation fuer
# realistische Regressionsdaten)
# ─────────────────────────────────────────────────────────────

def preis(edelstahl=0, stahl=0, schiebetor=0, drehtuer=0, zaun=0, treppe=0, faktor=1.0):
    p = (
        (800 if edelstahl > 0 else 0) + edelstahl * 175 +
        (500 if stahl     > 0 else 0) + stahl     * 115 +
        (1500 if schiebetor > 0 else 0) + schiebetor * 2200 +
        (800 if drehtuer  > 0 else 0) + drehtuer  * 1100 +
        (400 if zaun      > 0 else 0) + zaun      * 90 +
        (1200 if treppe   > 0 else 0) + treppe    * 2800
    )
    return round(p * faktor, 2)


# ─────────────────────────────────────────────────────────────
# 1. PRODUKTKATEGORIEN
# ─────────────────────────────────────────────────────────────

def create_produktkategorien():
    section("1/5  Produktkategorien anlegen")

    def kat(bezeichnung, einheit, parent_id=None, beschreibung=""):
        fields = {
            "bezeichnung": bezeichnung,
            "verrechnungseinheit": einheit,
            "beschreibung": beschreibung,
        }
        if parent_id is not None:
            fields["parentId"] = str(parent_id)
        result = api_multipart("/api/produktkategorien", fields)
        if result:
            ok(f"{'  ' if parent_id else ''}'{bezeichnung}' ->  ID {result['id']}")
            return result["id"]
        else:
            warn(f"Kategorie '{bezeichnung}' konnte nicht angelegt werden")
            sys.exit(1)

    # Elternkategorien
    g_id  = kat("Gelaender",        "LAUFENDE_METER", beschreibung="Gelaaenderarbeiten inkl. Montage")
    t_id  = kat("Tore und Tueren",  "STUECK",         beschreibung="Tore, Drehtuerenund Absperrungen")
    e_id  = kat("Einfriedungen",    "LAUFENDE_METER", beschreibung="Zaeune und Einfriedungsanlagen")
    tr_id = kat("Treppen",          "STUECK",         beschreibung="Stahltreppen fuer Innen- und Aussenbereich")

    # Leaf-Kategorien (fuer Regression)
    edelstahl_id  = kat("Edelstahlgelaender",    "LAUFENDE_METER", g_id,
                        "Hochwertige Edelstahlgelaender V2A/V4A, inkl. Handlauf und Montage")
    stahl_id      = kat("Stahlgelaender lackiert","LAUFENDE_METER", g_id,
                        "Stahlgelaender, pulverbeschichtet oder lackiert")
    schiebetor_id = kat("Schiebetor motorisiert", "STUECK",         t_id,
                        "Elektrisch betriebenes Schiebetor inkl. Antrieb und Fernbedienung")
    drehtuer_id   = kat("Drehtuer Stahl",         "STUECK",         t_id,
                        "Stahldrehtuer fuer Gewerbe- und Industriebereiche")
    zaun_id       = kat("Stahlzaun",              "LAUFENDE_METER", e_id,
                        "Stabmattenzaun Stahl, verzinkt oder pulverbeschichtet")
    treppe_id     = kat("Stahltreppe Aussen",     "STUECK",         tr_id,
                        "Aussentreppe aus feuerverzinktem Stahl, inkl. Gelaender")

    return {
        "edelstahl":  edelstahl_id,
        "stahl":      stahl_id,
        "schiebetor": schiebetor_id,
        "drehtuer":   drehtuer_id,
        "zaun":       zaun_id,
        "treppe":     treppe_id,
    }


# ─────────────────────────────────────────────────────────────
# 2. KUNDEN
# ─────────────────────────────────────────────────────────────

def create_kunden():
    section("2/5  Kunden anlegen (DSGVO-konform)")

    kunden = [
        {"name": "Mustermann Verwaltungs GmbH",  "anrede": "FIRMA",
         "ansprechspartner": "Max Mustermann",
         "strasse": "Musterstrasse 1",   "plz": "12345", "ort": "Musterstadt",
         "telefon": "0800 0000001", "kundenEmails": ["verwaltung@musterfirma.example.com"]},

        {"name": "Beispiel Bautraeger AG",        "anrede": "FIRMA",
         "ansprechspartner": "Erika Beispiel",
         "strasse": "Beispielweg 10",    "plz": "54321", "ort": "Beispielhausen",
         "telefon": "0800 0000002", "kundenEmails": ["buero@beispiel-bau.example.com"]},

        {"name": "Max Mustermann",                "anrede": "HERR",
         "ansprechspartner": "Max Mustermann",
         "strasse": "Musterallee 5",     "plz": "12345", "ort": "Musterstadt",
         "telefon": "0800 0000003", "kundenEmails": ["max.mustermann@example.com"]},

        {"name": "Erika Musterfrau",              "anrede": "FRAU",
         "ansprechspartner": "Erika Musterfrau",
         "strasse": "Teststrasse 3",     "plz": "67890", "ort": "Testdorf",
         "telefon": "0800 0000004", "kundenEmails": ["erika.musterfrau@example.com"]},

        {"name": "Familie Testmann",              "anrede": "FAMILIE",
         "ansprechspartner": "Hans Testmann",
         "strasse": "Demoweg 7",         "plz": "11111", "ort": "Demostadt",
         "telefon": "0800 0000005", "kundenEmails": []},

        {"name": "Demo Industrie GmbH",           "anrede": "FIRMA",
         "ansprechspartner": "Anna Demo",
         "strasse": "Industriestrasse 100","plz": "22222", "ort": "Demoburg",
         "telefon": "0800 0000006", "kundenEmails": ["einkauf@demo-industrie.example.com"]},

        {"name": "Hans Beispiel",                 "anrede": "HERR",
         "ansprechspartner": "Hans Beispiel",
         "strasse": "Hauptstrasse 42",   "plz": "33333", "ort": "Beispielort",
         "telefon": "0800 0000007", "kundenEmails": []},

        {"name": "Muster Immobilien GmbH",        "anrede": "FIRMA",
         "ansprechspartner": "Maria Muster",
         "strasse": "Gartenweg 8",       "plz": "44444", "ort": "Musterbach",
         "telefon": "0800 0000008", "kundenEmails": ["info@muster-immobilien.example.com"]},
    ]

    def find_existing_kunde(name):
        encoded = urllib.parse.quote(name)
        r = get(f"/api/kunden?name={encoded}&size=5")
        if r and r.get("kunden"):
            return r["kunden"][0]["id"]
        return None

    ids = []
    for kd in kunden:
        r = post("/api/kunden", kd)
        if r and r.get("id"):
            ids.append(r["id"])
            ok(f"'{kd['name']}' ->  ID {r['id']} (neu)")
        else:
            # Bereits vorhanden: ID per Suche ermitteln
            existing_id = find_existing_kunde(kd["name"])
            if existing_id:
                ids.append(existing_id)
                ok(f"'{kd['name']}' ->  ID {existing_id} (bereits vorhanden)")
            else:
                warn(f"Kunde '{kd['name']}' nicht gefunden")
                ids.append(None)
    return ids


# ─────────────────────────────────────────────────────────────
# 3. LIEFERANTEN
# ─────────────────────────────────────────────────────────────

def create_lieferanten():
    section("3/5  Lieferanten anlegen (DSGVO-konform)")

    lieferanten = [
        {"lieferantenname": "Muster Stahlhandel GmbH",
         "lieferantenTyp": "Stahlhandel", "vertreter": "Herr Mustermann",
         "strasse": "Stahlstrasse 1",  "plz": "10000", "ort": "Stahldorf",
         "telefon": "0800 1000001", "startZusammenarbeit": "2020-01-15",
         "kundenEmails": ["bestellung@muster-stahl.example.com"]},

        {"lieferantenname": "Demo Beschlaege AG",
         "lieferantenTyp": "Beschlaege und Zubehoer", "vertreter": "Frau Beispiel",
         "strasse": "Beschlagweg 5",   "plz": "20000", "ort": "Beispielburg",
         "telefon": "0800 1000002", "startZusammenarbeit": "2021-03-01",
         "kundenEmails": ["vertrieb@demo-beschlaege.example.com"]},

        {"lieferantenname": "Beispiel Edelstahl GmbH",
         "lieferantenTyp": "Edelstahllieferant", "vertreter": "Herr Demo",
         "strasse": "Edelstahlallee 20","plz": "30000", "ort": "Teststadt",
         "telefon": "0800 1000003", "startZusammenarbeit": "2019-06-10",
         "kundenEmails": ["anfrage@beispiel-edelstahl.example.com"]},
    ]

    for ld in lieferanten:
        r = post("/api/lieferanten", ld)
        if r:
            ok(f"'{ld['lieferantenname']}' ->  ID {r['id']}")
        else:
            warn(f"Lieferant '{ld['lieferantenname']}' uebersprungen")


# ─────────────────────────────────────────────────────────────
# 4. PROJEKTE  (32 Stueck, davon 3 offen)
#
# Jede Leaf-Kategorie erscheint in >= 10 Projekten.
# Preise folgen dem linearen Modell (mit leichter Variation),
# damit die Regression eine klare Gerade zeigt.
#
# Zaehlstand Leaf-Kategorien nach diesem Block:
#   edelstahl : 10
#   stahl     : 10
#   schiebetor: 10
#   drehtuer  : 10
#   zaun      : 12
#   treppe    : 10
# ─────────────────────────────────────────────────────────────

def create_projekte(k, kat):
    section("4/5  Projekte + ProjektProduktkategorien anlegen")

    # k[i] = kunden_ids[i]  (None wenn Anlage gescheitert)
    PROJEKTE = [
        # ── 29 ABGESCHLOSSENE PROJEKTE ──────────────────────────────────

        # Edelstahlgelaender (10x)
        ("Gelaender Einfamilienhaus Musterstrasse",       k[2], "Musterallee 5",        "12345","Musterstadt",    "2023-03-15", True,  preis(edelstahl=8,           faktor=1.00), "PAUSCHAL", [("edelstahl",8)]),
        ("Balkongelaender Edelstahl Beispielort",         k[6], "Hauptstrasse 42",      "33333","Beispielort",    "2023-09-20", True,  preis(edelstahl=5,           faktor=1.10), "PAUSCHAL", [("edelstahl",5)]),
        ("Wohnkomplex Edelstahlgelaender",                k[7], "Gartenweg 8",          "44444","Musterbach",     "2024-03-10", True,  preis(edelstahl=35,          faktor=1.07), "PAUSCHAL", [("edelstahl",35)]),
        ("Luxusvilla Edelstahl und Aussentreppe",         k[2], "Musterallee 5",        "12345","Musterstadt",    "2024-07-30", True,  preis(edelstahl=50,treppe=1, faktor=1.12), "PAUSCHAL", [("edelstahl",50),("treppe",1)]),
        ("Gelaender Neubau Beispiel AG",                  k[1], "Beispielweg 10",       "54321","Beispielhausen", "2023-06-01", True,  preis(edelstahl=20,drehtuer=2,faktor=1.08),"PAUSCHAL", [("edelstahl",20),("drehtuer",2)]),
        ("Dachterrasse Edelstahl und Treppe",             k[7], "Gartenweg 8",          "44444","Musterbach",     "2024-12-03", True,  preis(edelstahl=22,treppe=1, faktor=1.06), "PAUSCHAL", [("edelstahl",22),("treppe",1)]),
        ("Reihenhaus Doppelgelaender",                    k[4], "Demoweg 7",            "11111","Demostadt",      "2024-11-12", True,  preis(stahl=4,edelstahl=4,   faktor=0.98), "REGIE",    [("stahl",4),   ("edelstahl",4)]),
        ("Seniorenheim Barrierefreiheit komplett",        k[7], "Gartenweg 8",          "44444","Musterbach",     "2025-04-10", True,  preis(edelstahl=45,treppe=3,drehtuer=2,faktor=1.07),"PAUSCHAL",[("edelstahl",45),("treppe",3),("drehtuer",2)]),
        ("Stadtpark Gelaender und Einfriedung",           k[1], "Beispielweg 10",       "54321","Beispielhausen", "2025-11-01", False, preis(edelstahl=15,zaun=80,  faktor=1.08), "PAUSCHAL", [("edelstahl",15),("zaun",80)]),   # OFFEN
        ("Mehrfamilienhaus Musterbach Gelaender",         k[7], "Gartenweg 8",          "44444","Musterbach",     "2025-10-15", False, preis(stahl=24,edelstahl=12, faktor=1.05), "PAUSCHAL", [("stahl",24),  ("edelstahl",12)]),# OFFEN

        # Stahlgelaender (10x) – incl. Ueberschneidungen oben (reihenhaus, mehrfamilien)
        ("Treppengelaender Reparatur Testdorf",           k[3], "Teststrasse 3",        "67890","Testdorf",       "2023-05-10", True,  preis(stahl=12,              faktor=0.95), "REGIE",    [("stahl",12)]),
        ("Veranda Gelaender Stahl Testdorf",              k[3], "Teststrasse 3",        "67890","Testdorf",       "2024-01-18", True,  preis(stahl=6,               faktor=1.00), "REGIE",    [("stahl",6)]),
        ("Aussentreppe und Gelaender Gewerbe",            k[5], "Industriestrasse 100", "22222","Demoburg",       "2024-05-07", True,  preis(treppe=2,stahl=8,      faktor=1.05), "PAUSCHAL", [("treppe",2),  ("stahl",8)]),
        ("Buerogebaeude Eingang Drehtuer Gelaender",      k[0], "Musterstrasse 1",      "12345","Musterstadt",    "2024-09-05", True,  preis(drehtuer=4,stahl=10,   faktor=1.03), "PAUSCHAL", [("drehtuer",4),("stahl",10)]),
        ("Feuerwehrzufahrt Absicherung",                  k[0], "Musterstrasse 1",      "12345","Musterstadt",    "2025-02-14", True,  preis(schiebetor=2,stahl=15, faktor=1.04), "PAUSCHAL", [("schiebetor",2),("stahl",15)]),
        ("Schulgebaeude Aussentreppen und Gelaender",     k[1], "Beispielweg 10",       "54321","Beispielhausen", "2025-03-01", True,  preis(stahl=30,treppe=4,     faktor=1.09), "PAUSCHAL", [("stahl",30),  ("treppe",4)]),
        ("Altstadtbereich Gelaender und Treppen",         k[2], "Musterallee 5",        "12345","Musterstadt",    "2025-06-01", True,  preis(stahl=16,drehtuer=2,treppe=3,faktor=1.03),"PAUSCHAL",[("stahl",16),("drehtuer",2),("treppe",3)]),

        # Schiebetor (10x) – incl. Ueberschneidungen
        ("Einfahrtstor und Zaunanlage Musterfirma",       k[0], "Musterstrasse 1",      "12345","Musterstadt",    "2023-04-20", True,  preis(schiebetor=1,zaun=15,  faktor=1.05), "PAUSCHAL", [("schiebetor",1),("zaun",15)]),
        ("Schiebetor und Zaunanlage Betriebsgelaende",    k[5], "Industriestrasse 100", "22222","Demoburg",       "2023-10-03", True,  preis(schiebetor=2,zaun=25,  faktor=1.03), "PAUSCHAL", [("schiebetor",2),("zaun",25)]),
        ("Tiefgarage Absicherung",                        k[0], "Musterstrasse 1",      "12345","Musterstadt",    "2024-02-28", True,  preis(schiebetor=1,drehtuer=2,faktor=1.04),"PAUSCHAL", [("schiebetor",1),("drehtuer",2)]),
        ("Grundstueckseinfriedung und Schiebetor",        k[1], "Beispielweg 10",       "54321","Beispielhausen", "2024-04-22", True,  preis(zaun=60,schiebetor=1,  faktor=1.01), "PAUSCHAL", [("zaun",60),   ("schiebetor",1)]),
        ("Industriezaun und Schiebetor Logistik",         k[5], "Industriestrasse 100", "22222","Demoburg",       "2024-10-20", True,  preis(zaun=100,schiebetor=3, faktor=1.08), "PAUSCHAL", [("zaun",100),  ("schiebetor",3)]),
        ("Logistikzentrum Einfahrt komplett",             k[5], "Industriestrasse 100", "22222","Demoburg",       "2025-01-08", True,  preis(schiebetor=4,zaun=35,  faktor=1.10), "PAUSCHAL", [("schiebetor",4),("zaun",35)]),
        ("Sportplatz Einfriedung",                        k[5], "Industriestrasse 100", "22222","Demoburg",       "2025-03-20", True,  preis(zaun=120,schiebetor=2, faktor=1.02), "PAUSCHAL", [("zaun",120),  ("schiebetor",2)]),
        ("Parkhaus Absicherung und Erschliessung",        k[0], "Musterstrasse 1",      "12345","Musterstadt",    "2025-05-15", True,  preis(schiebetor=3,drehtuer=5,treppe=2,faktor=1.05),"PAUSCHAL",[("schiebetor",3),("drehtuer",5),("treppe",2)]),
        ("Firmeneinfahrt Demo GmbH komplett",             k[5], "Industriestrasse 100", "22222","Demoburg",       "2025-09-01", False, preis(schiebetor=1,drehtuer=3,zaun=45,faktor=1.10),"PAUSCHAL",[("schiebetor",1),("drehtuer",3),("zaun",45)]),# OFFEN

        # Drehtuer (10x) – incl. Ueberschneidungen oben
        ("Gartenzaun Einfamilienhaus mit Drehtuer",       k[7], "Gartenweg 8",          "44444","Musterbach",     "2023-12-01", True,  preis(zaun=40,drehtuer=1,    faktor=0.98), "PAUSCHAL", [("zaun",40),   ("drehtuer",1)]),
        ("Hofabsperrung Zaun und Drehtuer",               k[4], "Demoweg 7",            "11111","Demostadt",      "2024-06-15", True,  preis(zaun=20,drehtuer=1,    faktor=0.96), "PAUSCHAL", [("zaun",20),   ("drehtuer",1)]),
        ("Kleingarten Einfriedung mit Drehtuer",          k[6], "Hauptstrasse 42",      "33333","Beispielort",    "2024-08-14", True,  preis(zaun=50,drehtuer=1,    faktor=1.00), "PAUSCHAL", [("zaun",50),   ("drehtuer",1)]),

        # Zaun (12x) – bereits alle erfasst durch Ueberschneidungen oben
        # Treppe (10x) – incl. Ueberschneidungen oben
        ("Aussentreppe Wohnhaus Demostadt",               k[4], "Demoweg 7",            "11111","Demostadt",      "2023-08-05", True,  preis(treppe=1,              faktor=0.97), "PAUSCHAL", [("treppe",1)]),
        ("Treppenkomplex mit Gelaender",                  k[1], "Beispielweg 10",       "54321","Beispielhausen", "2023-11-15", True,  preis(treppe=3,stahl=18,     faktor=1.06), "PAUSCHAL", [("treppe",3),  ("stahl",18)]),
        ("Zaunanlage und Aussentreppe Gewerbe",           k[5], "Industriestrasse 100", "22222","Demoburg",       "2023-07-14", True,  preis(zaun=30,treppe=1,      faktor=1.02), "PAUSCHAL", [("zaun",30),   ("treppe",1)]),
    ]

    created = 0
    for row in PROJEKTE:
        (bauvorhaben, kunden_id, strasse, plz, ort,
         anlegedatum, abgeschlossen, brutto, art, kategorien) = row

        # Naechste Auftragsnummer vom Backend holen
        nr_resp = get("/api/projekte/naechste-auftragsnummer")
        auftragsnummer = nr_resp.get("auftragsnummer") if nr_resp else None
        if not auftragsnummer:
            warn(f"Konnte keine Auftragsnummer ermitteln fuer '{bauvorhaben}'")
            continue

        prod_kats = [
            {"produktkategorieID": kat[key], "menge": float(menge)}
            for key, menge in kategorien
        ]

        projekt_data = {
            "bauvorhaben":    bauvorhaben,
            "auftragsnummer": auftragsnummer,
            "kundenId":       kunden_id,
            "strasse":        strasse,
            "plz":            plz,
            "ort":            ort,
            "anlegedatum":    anlegedatum,
            "abgeschlossen":  abgeschlossen,
            "bezahlt":        abgeschlossen,
            "bruttoPreis":    brutto,
            "projektArt":     art,
            "produktkategorien": prod_kats,
            "kurzbeschreibung": (
                "Musterprojekt fuer Demo-Praesentation. "
                "Alle Daten sind DSGVO-konform (fiktive Musterdaten)."
            ),
        }

        if abgeschlossen:
            start_dt = date.fromisoformat(anlegedatum)
            projekt_data["abschlussdatum"] = (start_dt + timedelta(days=55)).isoformat()

        r = post("/api/projekte", projekt_data)
        if r:
            status_str = "abgeschlossen" if abgeschlossen else "OFFEN     "
            ok(f"[{status_str}] {auftragsnummer}  {bauvorhaben[:42]:<42}  {brutto:>9.0f} EUR")
            created += 1
        else:
            warn(f"Projekt '{bauvorhaben}' FEHLER")

        time.sleep(0.05)   # Server schonen

    print(f"\n  Gesamt: {created}/{len(PROJEKTE)} Projekte angelegt")


# ─────────────────────────────────────────────────────────────
# 5. ANFRAGEN
# ─────────────────────────────────────────────────────────────

def create_anfragen(k):
    section("5/5  Anfragen anlegen")

    anfragen = [
        # Offene Anfragen
        {"bauvorhaben": "Anfrage Balkongelaender Edelstahl ca. 6m",
         "kundenId": k[2], "anlegedatum": "2025-11-01",
         "projektStrasse": "Musterallee 5", "projektPlz": "12345", "projektOrt": "Musterstadt",
         "betrag": 1850.0, "abgeschlossen": False,
         "kurzbeschreibung": "Interessent moechte Kostenvoranschlag fuer Balkongelaender"},

        {"bauvorhaben": "Anfrage Einfahrtstor elektrisch Hauptstrasse",
         "kundenId": k[6], "anlegedatum": "2025-11-10",
         "projektStrasse": "Hauptstrasse 42", "projektPlz": "33333", "projektOrt": "Beispielort",
         "betrag": 4200.0, "abgeschlossen": False,
         "kurzbeschreibung": "Schiebetor elektrisch, ca. 4m Breite"},

        {"bauvorhaben": "Anfrage Zaunanlage Grundstueck Testdorf",
         "kundenId": k[3], "anlegedatum": "2025-11-20",
         "projektStrasse": "Teststrasse 3", "projektPlz": "67890", "projektOrt": "Testdorf",
         "betrag": 3500.0, "abgeschlossen": False,
         "kurzbeschreibung": "Stabmattenzaun anthrazit ca. 35m"},

        {"bauvorhaben": "Anfrage Aussentreppe zur Dachterrasse",
         "kundenId": k[4], "anlegedatum": "2025-12-01",
         "projektStrasse": "Demoweg 7", "projektPlz": "11111", "projektOrt": "Demostadt",
         "betrag": 4800.0, "abgeschlossen": False,
         "kurzbeschreibung": "Stahltreppe Aussenseite ca. 3m Hoehe"},

        {"bauvorhaben": "Anfrage Gelaender Pflegeeinrichtung 40m Edelstahl",
         "kundenId": k[7], "anlegedatum": "2025-12-10",
         "projektStrasse": "Gartenweg 8", "projektPlz": "44444", "projektOrt": "Musterbach",
         "betrag": 12000.0, "abgeschlossen": False,
         "kurzbeschreibung": "Barrierefreie Gelaender V4A, komplette Einrichtung"},

        # Abgeschlossene / abgelehnte Anfragen
        {"bauvorhaben": "Anfrage Terrassengelaender (nicht weitergefuehrt)",
         "kundenId": k[2], "anlegedatum": "2024-08-01",
         "projektStrasse": "Musterallee 5", "projektPlz": "12345", "projektOrt": "Musterstadt",
         "betrag": 950.0, "abgeschlossen": True,
         "kurzbeschreibung": "Anfrage zurueckgezogen"},

        {"bauvorhaben": "Anfrage Hoftor Muster GmbH (Auftrag anderweitig vergeben)",
         "kundenId": k[0], "anlegedatum": "2024-09-15",
         "projektStrasse": "Musterstrasse 1", "projektPlz": "12345", "projektOrt": "Musterstadt",
         "betrag": 3200.0, "abgeschlossen": True,
         "kurzbeschreibung": "Kunde hat Mitbewerber beauftragt"},
    ]

    for ad in anfragen:
        r = post("/api/anfragen", ad)
        if r:
            status_str = "abgeschlossen" if ad["abgeschlossen"] else "offen"
            ok(f"[{status_str}] '{ad['bauvorhaben'][:55]}'")
        else:
            warn(f"Anfrage '{ad['bauvorhaben']}' FEHLER")


# ─────────────────────────────────────────────────────────────
# MAIN
# ─────────────────────────────────────────────────────────────

def main():
    print("\n" + "=" * 60)
    print("  ERP Demo-Daten Seed Script")
    print("  DSGVO-konform: Nur fiktive Musterdaten")
    print(f"  Ziel: {BASE_URL}")
    print("=" * 60)

    # Verbindungstest
    print("\nVerbindungstest ...")
    test = get("/api/produktkategorien")
    if test is None:
        print(f"\nX Backend nicht erreichbar unter {BASE_URL}")
        print("  Bitte Backend starten und Tailscale-Verbindung pruefen.")
        sys.exit(1)
    print(f"  Verbindung OK  ({len(test) if isinstance(test, list) else '?'} Kategorien vorhanden)")

    if isinstance(test, list) and len(test) > 0:
        print(f"\n  HINWEIS: Es existieren bereits {len(test)} Produktkategorien.")
        antwort = input("  Trotzdem fortfahren und weitere Daten anlegen? (j/N): ").strip().lower()
        if antwort != "j":
            print("  Abgebrochen.")
            sys.exit(0)

    kat      = create_produktkategorien()
    k        = create_kunden()
    create_lieferanten()
    create_projekte(k, kat)
    create_anfragen(k)

    print("\n" + "=" * 60)
    print("  FERTIG: Demo-Daten erfolgreich angelegt!")
    print()
    print("  Kategorie-Abdeckung (>= 10 je Leaf):")
    print("    Edelstahlgelaender  : 10")
    print("    Stahlgelaender      : 10")
    print("    Schiebetor          : 10")
    print("    Drehtuer Stahl      : 10")
    print("    Stahlzaun           : 12")
    print("    Stahltreppe Aussen  : 10")
    print()
    print("  Projekte: 29 abgeschlossen | 3 offen")
    print("  Anfragen:  5 offen         | 2 abgeschlossen")
    print("=" * 60 + "\n")


if __name__ == "__main__":
    main()
