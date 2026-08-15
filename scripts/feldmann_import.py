"""Importiert das Feldmann-Sortiment: Artikel, Kategorien, Texte, Bilder, Datenblaetter.

Fuenf Phasen, einzeln aufrufbar, jede fuer sich wiederholbar:

  aufbereiten  Liest feldmann_artikel.csv und das Kategorie-Mapping, korrigiert
               die Werkstoffe und erzeugt je Zielkategorie eine Import-CSV.
               Braucht keinen Server.
  kategorien   Legt die im Mapping fehlenden Kategorien an.
  artikel      Spielt die Import-CSVs ein (eine je Kategorie).
  texte        Traegt Kurzbeschreibung (Innensicht) und Beschreibung
               (Kundentext fuers Angebot) nach.
  dateien      Laedt Vorschaubilder und Datenblaetter hoch.

Standard ist immer der Trockenlauf; erst --apply schreibt.

    python scripts/feldmann_import.py aufbereiten --quelle C:\\pfad\\feldmann_artikel.csv \\
        --mapping C:\\pfad\\kategorie_mapping.csv --arbeitsverzeichnis C:\\pfad\\import
    python scripts/feldmann_import.py kategorien --url http://localhost:8080 --benutzer name --apply

Es braucht nur Python 3 (Standardbibliothek).
"""
import argparse
import csv
import getpass
import http.cookiejar
import io
import json
import mimetypes
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
import uuid
from pathlib import Path

ZEITLIMIT_SEKUNDEN = 120
SEITENGROESSE = 200
LIEFERANT = "Feldmann"

# ---------------------------------------------------------------------------
# Werkstoffe
# ---------------------------------------------------------------------------

# Feldmanns "V4A" ist die molybdaenlegierte Gruppe 1.4401/1.4404 - nicht der
# titanstabilisierte 316Ti (1.4571), auf den die Rohdatei sie abbildet. Der
# Report zur Datei weist selbst darauf hin.
WERKSTOFF_KORREKTUR = {"1.4571": "1.4404"}

# Werkstattbegriff im Produktnamen -> Werkstoffnummer. Greift nur, wenn die
# Werkstoff-Spalte leer ist: 456 Artikel tragen V2A/V4A im Namen, haben aber
# kein gefuelltes Werkstoff-Feld.
WERKSTOFF_AUS_NAME = {"V2A": "1.4301", "V4A": "1.4404"}

# Klartext fuers Kundendokument. Deckungsgleich mit WERKSTOFF_KLARTEXT in
# react-pc-frontend/src/components/artikel/kundentext.ts - beide Wege muessen
# denselben Satz erzeugen.
WERKSTOFF_KLARTEXT = {
    "1.4301": "Edelstahl 1.4301",
    "1.4404": "Edelstahl 1.4404",
    "1.4571": "Edelstahl 1.4571",
    "S355J2": "Baustahl S355J2",
    "S235JR": "Baustahl S235JR",
    "EN AW-5754": "Aluminium EN AW-5754",
    "EN AW-6060": "Aluminium EN AW-6060",
}

# Werkstoffnummern, die im Stamm gepflegt sind. Eine unbekannte Nummer wuerde
# der Import stillschweigend als neuen Werkstoff ohne Dichte anlegen.
WERKSTOFFE_IM_STAMM = {"1.4301", "1.4404", "1.4541", "1.4571"}
WERKSTOFFNUMMER = re.compile(r"\b(1\.4[0-9]{3})\b")

MAX_KURZBESCHREIBUNG = 255
MAX_BESCHREIBUNG = 10000


# ---------------------------------------------------------------------------
# Texte
# ---------------------------------------------------------------------------

def escape(text):
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


def werkstoff_fuer_zeile(zeile):
    """Werkstoffnummer einer CSV-Zeile nach Korrektur und Namensauswertung.

    Nur eindeutige Angaben zaehlen. Woerter wie "Aluminium" oder "Stahl" im
    Produkttext bleiben ausdruecklich unberuecksichtigt: bei diesem Sortiment
    bezeichnen sie oft die Oberflaeche und nicht das Material ("Design-Abdeckung
    ..., Aluminium E4/EV1" ist eine Eloxalfarbe). Lieber kein Werkstoff als ein
    falscher.
    """
    roh = (zeile.get("werkstoff") or "").strip()
    name = zeile.get("produktname") or ""
    aus_name = next((n for b, n in WERKSTOFF_AUS_NAME.items() if b in name), None)

    if roh:
        korrigiert = WERKSTOFF_KORREKTUR.get(roh, roh)
        # Die Quelle widerspricht sich bei einigen Artikeln selbst: Der Name
        # sagt V4A, die Werkstoff-Spalte 1.4301 (= V2A). Zwischen den beiden
        # liegen Korrosionsbestaendigkeit und Preis - hier wird nicht geraten,
        # der Artikel geht ohne Werkstoff rein und steht im Protokoll.
        if aus_name and aus_name != korrigiert:
            return ""
        return korrigiert

    if aus_name:
        return aus_name

    # Ausgeschriebene Werkstoffnummer im Text, z.B. "Wandanker 25x25, 1.4301".
    treffer = WERKSTOFFNUMMER.search(f"{name} {zeile.get('produkttext') or ''}")
    if treffer and treffer.group(1) in WERKSTOFFE_IM_STAMM:
        return WERKSTOFF_KORREKTUR.get(treffer.group(1), treffer.group(1))
    return ""


# "V2A" kommt im Produktnamen an jeder erdenklichen Stelle vor: am Ende
# ("Designstab, V2A"), am Anfang ("V2A - Glasklemme Mod. 32"), als Wortpraefix
# ("V2A-Schliessblech") und mittendrin ("Gewindestift M8x35, V2A, DIN 913").
# Fuer den Kundentext muss es ueberall weg, weil dahinter der Klartext kommt.
KUERZEL_MIT_NUMMER = re.compile(r"[,;]?\s*\bV[24]A\b\s*\(\s*1\.4[0-9]{3}\s*\)")
KUERZEL_PRAEFIX = re.compile(r"^\s*V[24]A\s*[-–]\s*")
KUERZEL_WORTPRAEFIX = re.compile(r"\bV[24]A-")
KUERZEL_EINZELN = re.compile(r"[,;]?\s*\bV[24]A\b")
SATZZEICHEN_RESTE = re.compile(r"\s*,\s*,")


def entferne_werkstattkuerzel(name):
    """Nimmt V2A/V4A aus dem Namen und raeumt die Satzzeichen dahinter auf."""
    text = KUERZEL_MIT_NUMMER.sub("", name)
    text = KUERZEL_PRAEFIX.sub("", text)
    text = KUERZEL_WORTPRAEFIX.sub("", text)
    text = KUERZEL_EINZELN.sub("", text)
    text = SATZZEICHEN_RESTE.sub(",", text)
    text = re.sub(r"\s{2,}", " ", text).strip()
    return text.strip(" ,;-–")


def baue_kurzbeschreibung(zeile):
    """Innensicht fuer den Dokument-Editor.

    Der Shop-Produkttext ist dafuer das bessere Feld als der Produktname: Er
    enthaelt die Lieferanten- und Herstellernummern, nach denen intern gesucht
    wird. Auf ein Kundendokument darf er genau deswegen nie.
    """
    text = (zeile.get("produkttext") or "").strip() or (zeile.get("produktname") or "").strip()
    return text[:MAX_KURZBESCHREIBUNG]


def baue_beschreibung(zeile, werkstoff):
    """Kundentext fuers Angebot als schlichtes HTML.

    Der Produktname traegt den Satz - bei diesem Sortiment beginnt er
    durchgaengig mit einem echten Wort ("Glasklemme Mod. 31, ..."). Der
    Werkstattbegriff am Ende ("..., V2A") weicht dem Klartext, den der Kunde
    lesen soll ("aus Edelstahl 1.4301"). Fehlt der Werkstoff, entfaellt der
    Zusatz ersatzlos - erfunden wird nichts.
    """
    name = (zeile.get("produktname") or "").strip()
    if not name:
        return ""

    satz = entferne_werkstattkuerzel(name)
    if not satz:
        return ""

    klartext = WERKSTOFF_KLARTEXT.get(werkstoff)
    # Ein Name, der auf Doppelpunkt endet ("..., bestehend aus:"), kuendigt eine
    # Aufzaehlung an, die in der Quelle fehlt. Da darf kein "aus Edelstahl"
    # hinterher - der Satz endet lieber offen, als schief zu werden.
    if klartext and not satz.endswith(":") and not nennt_werkstoff_schon(satz, klartext):
        satz += f" aus {klartext}"
    return f"<p>{escape(satz)}</p>"[:MAX_BESCHREIBUNG]


def nennt_werkstoff_schon(satz, klartext):
    """Steht das Material bereits im Satz?

    Geprueft wird auf die Wendung "aus Edelstahl" und nicht auf das blosse Wort:
    "Anschlagsblech mit Gummipuffer aus Edelstahl" braucht keinen zweiten
    Werkstoffzusatz, ein "Gegenkasten mit Edelstahlschliessblech" dagegen schon
    - dort ist Edelstahl das Material eines Bauteils, nicht des Artikels.
    """
    klein = satz.lower()
    if klartext.lower() in klein:
        return True
    grundwort = klartext.split()[0].lower()  # Edelstahl, Baustahl, Aluminium
    return f"aus {grundwort}" in klein


# ---------------------------------------------------------------------------
# Phase 1: Aufbereiten
# ---------------------------------------------------------------------------

def sicherer_dateiname(text):
    return "".join(c if c.isalnum() or c in "-_" else "_" for c in text)[:120]


def phase_aufbereiten(args):
    with io.open(args.quelle, encoding="utf-8-sig", newline="") as f:
        artikel = list(csv.DictReader(f, delimiter=";"))
    with io.open(args.mapping, encoding="utf-8-sig", newline="") as f:
        mapping = {z["produktlinie"]: z["zielpfad"] for z in csv.DictReader(f, delimiter=";")}

    arbeit = Path(args.arbeitsverzeichnis)
    (arbeit / "csv").mkdir(parents=True, exist_ok=True)

    nach_kategorie = {}
    texte = []
    ohne_mapping = []
    widersprueche = []
    werkstoff_ergaenzt = 0
    werkstoff_korrigiert = 0

    for zeile in artikel:
        linie = (zeile.get("produktlinie") or "").strip()
        pfad = mapping.get(linie)
        if not pfad:
            ohne_mapping.append(linie)
            continue

        roh = (zeile.get("werkstoff") or "").strip()
        werkstoff = werkstoff_fuer_zeile(zeile)
        if not roh and werkstoff:
            werkstoff_ergaenzt += 1
        elif roh and not werkstoff:
            widersprueche.append((zeile["materialnummer"], zeile["produktname"], roh))
        elif roh and werkstoff != roh:
            werkstoff_korrigiert += 1

        nach_kategorie.setdefault(pfad, []).append({
            "materialnummer": zeile["materialnummer"],
            "nettopreis": zeile["nettopreis"],
            "produktname": zeile["produktname"],
            "produktlinie": linie,
            "werkstoff": werkstoff,
            "preiseinheit": zeile.get("preiseinheit") or "Stk",
            "packgroesse": zeile.get("packgroesse") or "",
            "produkttext": zeile.get("produkttext") or "",
        })
        texte.append({
            "materialnummer": zeile["materialnummer"],
            "kurzbeschreibung": baue_kurzbeschreibung(zeile),
            "beschreibung": baue_beschreibung(zeile, werkstoff),
            "bild_url": zeile.get("bild_url") or "",
            "datenblatt_url": zeile.get("datenblatt_url") or "",
        })

    spalten = ["materialnummer", "nettopreis", "produktname", "produktlinie",
               "werkstoff", "preiseinheit", "packgroesse", "produkttext"]
    verzeichnis = []
    for pfad, zeilen in sorted(nach_kategorie.items()):
        datei = arbeit / "csv" / f"{sicherer_dateiname(pfad)}.csv"
        with io.open(datei, "w", encoding="utf-8-sig", newline="") as f:
            w = csv.DictWriter(f, fieldnames=spalten, delimiter=";")
            w.writeheader()
            w.writerows(zeilen)
        verzeichnis.append({"zielpfad": pfad, "datei": datei.name, "anzahl": len(zeilen)})

    (arbeit / "verzeichnis.json").write_text(
        json.dumps(verzeichnis, ensure_ascii=False, indent=2), encoding="utf-8")
    with io.open(arbeit / "texte.csv", "w", encoding="utf-8-sig", newline="") as f:
        w = csv.DictWriter(f, delimiter=";", fieldnames=[
            "materialnummer", "kurzbeschreibung", "beschreibung", "bild_url", "datenblatt_url"])
        w.writeheader()
        w.writerows(texte)

    print(f"Artikel eingelesen: {len(artikel)}")
    print(f"  auf {len(nach_kategorie)} Kategorien verteilt: {sum(len(v) for v in nach_kategorie.values())}")
    print(f"  Werkstoff aus dem Produktnamen ergaenzt: {werkstoff_ergaenzt}")
    print(f"  Werkstoff korrigiert (1.4571 -> 1.4404):  {werkstoff_korrigiert}")
    if widersprueche:
        print(f"  Werkstoff leer gelassen (Quelle widerspricht sich): {len(widersprueche)}")
        for nummer, name, feld in widersprueche:
            print(f"      {nummer}: Feld '{feld}' vs. Name '{name[:52]}'")
    if ohne_mapping:
        print(f"  OHNE KATEGORIE-MAPPING: {len(ohne_mapping)} "
              f"({sorted(set(ohne_mapping))[:3]}) - diese Artikel bleiben aussen vor")
    print(f"\nGeschrieben nach {arbeit}")
    beispiel = texte[0] if texte else None
    if beispiel:
        print(f"\nBeispieltexte ({beispiel['materialnummer']}):")
        print(f"  kurz:  {beispiel['kurzbeschreibung'][:100]}")
        print(f"  kunde: {beispiel['beschreibung'][:100]}")
    return 0


# ---------------------------------------------------------------------------
# HTTP-Klient
# ---------------------------------------------------------------------------

class ErpClient:
    """Session-Login und CSRF-Token, wie in artikel_dokumenttexte_backfill.py."""

    def __init__(self, basis_url):
        self.basis_url = basis_url.rstrip("/")
        self.cookies = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(
            urllib.request.HTTPCookieProcessor(self.cookies))

    def login(self, benutzer, passwort):
        rumpf = urllib.parse.urlencode(
            {"username": benutzer, "password": passwort}).encode("utf-8")
        anfrage = urllib.request.Request(
            f"{self.basis_url}/api/auth/login", data=rumpf, method="POST",
            headers={"Content-Type": "application/x-www-form-urlencoded"})
        with self.opener.open(anfrage, timeout=ZEITLIMIT_SEKUNDEN) as antwort:
            antwort.read()

    def _xsrf(self):
        for cookie in self.cookies:
            if cookie.name == "XSRF-TOKEN":
                return cookie.value
        return None

    def _kopfzeilen(self, extra=None):
        kopf = {"Accept": "application/json"}
        token = self._xsrf()
        if token:
            kopf["X-XSRF-TOKEN"] = token
        if extra:
            kopf.update(extra)
        return kopf

    def get_json(self, pfad, params=None):
        url = f"{self.basis_url}{pfad}"
        if params:
            url += "?" + urllib.parse.urlencode(params)
        anfrage = urllib.request.Request(url, headers=self._kopfzeilen())
        with self.opener.open(anfrage, timeout=ZEITLIMIT_SEKUNDEN) as antwort:
            return json.load(antwort)

    def json_anfrage(self, pfad, rumpf, methode):
        daten = json.dumps(rumpf).encode("utf-8")
        anfrage = urllib.request.Request(
            f"{self.basis_url}{pfad}", data=daten, method=methode,
            headers=self._kopfzeilen({"Content-Type": "application/json"}))
        with self.opener.open(anfrage, timeout=ZEITLIMIT_SEKUNDEN) as antwort:
            inhalt = antwort.read()
            return json.loads(inhalt) if inhalt else None

    def multipart(self, pfad, felder, datei=None):
        """POST als multipart/form-data - fuer CSV-Import und Datei-Upload."""
        grenze = "----ErpImport" + uuid.uuid4().hex
        teile = []
        for name, wert in felder.items():
            teile.append(f"--{grenze}\r\nContent-Disposition: form-data; name=\"{name}\"\r\n\r\n{wert}\r\n"
                         .encode("utf-8"))
        if datei:
            feldname, dateiname, inhalt = datei
            typ = mimetypes.guess_type(dateiname)[0] or "application/octet-stream"
            teile.append(
                f"--{grenze}\r\nContent-Disposition: form-data; name=\"{feldname}\"; "
                f"filename=\"{dateiname}\"\r\nContent-Type: {typ}\r\n\r\n".encode("utf-8"))
            teile.append(inhalt)
            teile.append(b"\r\n")
        teile.append(f"--{grenze}--\r\n".encode("utf-8"))
        rumpf = b"".join(teile)

        anfrage = urllib.request.Request(
            f"{self.basis_url}{pfad}", data=rumpf, method="POST",
            headers=self._kopfzeilen({
                "Content-Type": f"multipart/form-data; boundary={grenze}"}))
        with self.opener.open(anfrage, timeout=ZEITLIMIT_SEKUNDEN) as antwort:
            inhalt = antwort.read()
            return json.loads(inhalt) if inhalt else None


def fehlertext(e):
    if isinstance(e, urllib.error.HTTPError):
        try:
            return f"HTTP {e.code}: {e.read().decode('utf-8', 'replace')[:200]}"
        except Exception:
            return f"HTTP {e.code}"
    return f"{type(e).__name__}: {e}"


def melde_an(args):
    client = ErpClient(args.url)
    passwort = args.passwort or os.environ.get("ERP_PASSWORT")
    benutzer = args.benutzer or os.environ.get("ERP_BENUTZER")
    if not benutzer:
        print("Kein Benutzer angegeben (--benutzer oder ERP_BENUTZER).", file=sys.stderr)
        return None
    if not passwort:
        passwort = getpass.getpass(f"Passwort fuer {benutzer}: ")
    try:
        client.login(benutzer, passwort)
    except Exception as e:
        print(f"Login fehlgeschlagen: {fehlertext(e)}", file=sys.stderr)
        return None
    return client


# ---------------------------------------------------------------------------
# Phase 2: Kategorien
# ---------------------------------------------------------------------------

def lade_kategoriebaum(client):
    """Alle Kategorien als {pfad_klein: id}."""
    alle = client.get_json("/api/artikel/kategorien/alle")
    nach_id = {k["id"]: k for k in alle}

    def pfad_von(k):
        teile, aktuell, tiefe = [], k, 0
        while aktuell and tiefe < 8:
            teile.insert(0, (aktuell.get("beschreibung") or "").strip())
            eltern_id = aktuell.get("parentKategorieId") or aktuell.get("parentId")
            aktuell = nach_id.get(eltern_id)
            tiefe += 1
        return "/".join(teile)

    return {pfad_von(k).lower(): k["id"] for k in alle}


def phase_kategorien(args):
    client = melde_an(args)
    if client is None:
        return 2

    with io.open(args.mapping, encoding="utf-8-sig", newline="") as f:
        pfade = sorted({z["zielpfad"] for z in csv.DictReader(f, delimiter=";")})

    try:
        baum = lade_kategoriebaum(client)
    except Exception as e:
        print(f"Kategoriebaum nicht lesbar: {fehlertext(e)}", file=sys.stderr)
        return 2

    # Alle Ebenen sammeln, Eltern vor Kindern.
    benoetigt = []
    for pfad in pfade:
        teile = pfad.split("/")
        for i in range(1, len(teile) + 1):
            teilpfad = "/".join(teile[:i])
            if teilpfad not in benoetigt:
                benoetigt.append(teilpfad)
    benoetigt.sort(key=lambda p: (p.count("/"), p))

    angelegt = uebersprungen = fehler = 0
    for pfad in benoetigt:
        if pfad.lower() in baum:
            uebersprungen += 1
            continue
        teile = pfad.split("/")
        name = teile[-1]
        eltern_pfad = "/".join(teile[:-1])
        eltern_id = baum.get(eltern_pfad.lower()) if eltern_pfad else None
        if eltern_pfad and eltern_id is None:
            print(f"  FEHLER {pfad}: Oberkategorie '{eltern_pfad}' fehlt")
            fehler += 1
            continue

        if not args.apply:
            print(f"  geplant: {pfad}")
            baum[pfad.lower()] = -1  # damit Kinder im Trockenlauf nicht meckern
            angelegt += 1
            continue
        try:
            rumpf = {"beschreibung": name}
            if eltern_id is not None:
                rumpf["parentKategorieId"] = eltern_id
            antwort = client.json_anfrage("/api/artikel/kategorien", rumpf, "POST")
            baum[pfad.lower()] = antwort["id"]
            angelegt += 1
            print(f"  angelegt: {pfad} (id {antwort['id']})")
        except Exception as e:
            print(f"  FEHLER {pfad}: {fehlertext(e)}")
            fehler += 1

    art = "angelegt" if args.apply else "geplant"
    print(f"\n{angelegt} {art}, {uebersprungen} schon vorhanden, {fehler} Fehler")
    if not args.apply:
        print("Trockenlauf - mit --apply wird geschrieben.")
    return 1 if fehler else 0


# ---------------------------------------------------------------------------
# Phase 3: Artikel
# ---------------------------------------------------------------------------

def phase_artikel(args):
    client = melde_an(args)
    if client is None:
        return 2

    arbeit = Path(args.arbeitsverzeichnis)
    verzeichnis = json.loads((arbeit / "verzeichnis.json").read_text(encoding="utf-8"))
    try:
        baum = lade_kategoriebaum(client)
    except Exception as e:
        print(f"Kategoriebaum nicht lesbar: {fehlertext(e)}", file=sys.stderr)
        return 2

    fehler = 0
    verarbeitet = 0
    for i, eintrag in enumerate(verzeichnis, 1):
        pfad = eintrag["zielpfad"]
        kategorie_id = baum.get(pfad.lower())
        if kategorie_id is None:
            print(f"  FEHLER {pfad}: Kategorie fehlt - erst Phase 'kategorien' laufen lassen")
            fehler += 1
            continue
        if args.limit and verarbeitet >= args.limit:
            print(f"\nLimit von {args.limit} Kategorien erreicht - Rest nicht angefasst.")
            break
        if not args.apply:
            print(f"  geplant: {eintrag['anzahl']:4d} Artikel -> {pfad} (Kategorie {kategorie_id})")
            verarbeitet += 1
            continue

        datei = arbeit / "csv" / eintrag["datei"]
        try:
            client.multipart("/api/artikel/import", {
                "lieferant": LIEFERANT,
                "kategorieId": str(kategorie_id),
                # Die Kilopreis-Korrektur wuerde jeden Preis ueber 10 EUR
                # verwerfen oder durch 100 teilen. Das hier sind Stueckpreise.
                "preiskorrekturAnwenden": "false",
            }, datei=("file", eintrag["datei"], datei.read_bytes()))
            print(f"  [{i}/{len(verzeichnis)}] {eintrag['anzahl']:4d} Artikel -> {pfad}")
            verarbeitet += 1
        except Exception as e:
            print(f"  FEHLER {pfad}: {fehlertext(e)}")
            fehler += 1

    print(f"\n{verarbeitet} Kategorien verarbeitet, {fehler} Fehler")
    if not args.apply:
        print("Trockenlauf - mit --apply wird geschrieben.")
    return 1 if fehler else 0


# ---------------------------------------------------------------------------
# Phase 4: Texte
# ---------------------------------------------------------------------------

def artikel_index(client):
    """{externe_artikelnummer_klein: artikel_id} fuer den Feldmann-Bestand."""
    index = {}
    seite = 0
    while True:
        daten = client.get_json("/api/artikel", {
            "page": seite, "size": SEITENGROESSE, "lieferant": LIEFERANT, "sort": "produktname"})
        treffer = daten.get("artikel") or []
        if not treffer:
            break
        for a in treffer:
            nummer = (a.get("externeArtikelnummer") or "").strip().lower()
            if nummer:
                index[nummer] = a["id"]
        gesamt = daten.get("gesamt")
        groesse = daten.get("seitenGroesse") or SEITENGROESSE
        if len(treffer) < groesse:
            break
        if isinstance(gesamt, int) and (seite + 1) * groesse >= gesamt:
            break
        seite += 1
    return index


def phase_texte(args):
    client = melde_an(args)
    if client is None:
        return 2

    arbeit = Path(args.arbeitsverzeichnis)
    with io.open(arbeit / "texte.csv", encoding="utf-8-sig", newline="") as f:
        texte = list(csv.DictReader(f, delimiter=";"))

    index = artikel_index(client)
    print(f"{len(index)} Feldmann-Artikel im Bestand gefunden")

    geschrieben = fehlt = fehler = 0
    for zeile in texte:
        if args.limit and geschrieben >= args.limit:
            print(f"\nLimit von {args.limit} erreicht - Rest nicht angefasst.")
            break
        artikel_id = index.get(zeile["materialnummer"].strip().lower())
        if artikel_id is None:
            fehlt += 1
            continue
        rumpf = {"kurzbeschreibung": zeile["kurzbeschreibung"],
                 "beschreibung": zeile["beschreibung"]}
        if not args.apply:
            geschrieben += 1
            continue
        try:
            client.json_anfrage(f"/api/artikel/{artikel_id}/dokumenttexte", rumpf, "PATCH")
            geschrieben += 1
            if geschrieben % 250 == 0:
                print(f"  {geschrieben} geschrieben")
        except Exception as e:
            print(f"  FEHLER {zeile['materialnummer']}: {fehlertext(e)}")
            fehler += 1

    art = "geschrieben" if args.apply else "geplant"
    print(f"\n{geschrieben} {art}, {fehlt} ohne passenden Artikel, {fehler} Fehler")
    if not args.apply:
        print("Trockenlauf - mit --apply wird geschrieben.")
    return 1 if fehler else 0


# ---------------------------------------------------------------------------
# Phase 5: Dateien
# ---------------------------------------------------------------------------

def phase_dateien(args):
    client = melde_an(args)
    if client is None:
        return 2

    arbeit = Path(args.arbeitsverzeichnis)
    with io.open(arbeit / "texte.csv", encoding="utf-8-sig", newline="") as f:
        zeilen = list(csv.DictReader(f, delimiter=";"))

    bilder_dir = Path(args.dateien) / "bilder"
    db_dir = Path(args.dateien) / "datenblaetter"
    index = artikel_index(client)
    print(f"{len(index)} Feldmann-Artikel im Bestand gefunden")

    hoch = fehlt_artikel = fehlt_datei = fehler = 0
    for zeile in zeilen:
        if args.limit and hoch >= args.limit:
            print(f"\nLimit von {args.limit} erreicht - Rest nicht angefasst.")
            break
        nummer = zeile["materialnummer"].strip()
        artikel_id = index.get(nummer.lower())
        if artikel_id is None:
            fehlt_artikel += 1
            continue

        aufgaben = []
        if zeile.get("bild_url"):
            endung = zeile["bild_url"].rsplit(".", 1)[-1].lower()
            endung = endung if endung in ("jpg", "jpeg", "png", "webp", "gif") else "jpg"
            pfad = bilder_dir / f"{''.join(c if c.isalnum() or c in '._-' else '_' for c in nummer)}.{endung}"
            aufgaben.append((pfad, "VORSCHAUBILD", None))
        if zeile.get("datenblatt_url"):
            name = zeile["datenblatt_url"].rsplit("/", 1)[-1]
            name = "".join(c if c.isalnum() or c in "._-" else "_" for c in name)
            if not name.lower().endswith(".pdf"):
                name += ".pdf"
            aufgaben.append((db_dir / name, "DATENBLATT", "Datenblatt"))

        for pfad, typ, beschreibung in aufgaben:
            if not pfad.exists() or pfad.stat().st_size == 0:
                fehlt_datei += 1
                continue
            if not args.apply:
                hoch += 1
                continue
            try:
                felder = {"typ": typ}
                if beschreibung:
                    felder["beschreibung"] = beschreibung
                client.multipart(f"/api/artikel/{artikel_id}/dokumente", felder,
                                 datei=("datei", pfad.name, pfad.read_bytes()))
                hoch += 1
                if hoch % 250 == 0:
                    print(f"  {hoch} hochgeladen")
            except Exception as e:
                print(f"  FEHLER {nummer} ({typ}): {fehlertext(e)}")
                fehler += 1

    art = "hochgeladen" if args.apply else "geplant"
    print(f"\n{hoch} {art}, {fehlt_artikel} ohne Artikel, "
          f"{fehlt_datei} Datei nicht auf der Platte, {fehler} Fehler")
    if not args.apply:
        print("Trockenlauf - mit --apply wird geschrieben.")
    return 1 if fehler else 0


# ---------------------------------------------------------------------------

def main():
    p = argparse.ArgumentParser(description=__doc__,
                                formatter_class=argparse.RawDescriptionHelpFormatter)
    unter = p.add_subparsers(dest="phase", required=True)

    def mit_server(sp):
        sp.add_argument("--url", default="http://localhost:8080")
        sp.add_argument("--benutzer")
        sp.add_argument("--passwort", help="besser weglassen - landet im Shell-Verlauf")
        sp.add_argument("--apply", action="store_true", help="wirklich schreiben")
        sp.add_argument("--limit", type=int, help="nach N Vorgaengen aufhoeren")

    a = unter.add_parser("aufbereiten")
    a.add_argument("--quelle", required=True)
    a.add_argument("--mapping", required=True)
    a.add_argument("--arbeitsverzeichnis", required=True)

    k = unter.add_parser("kategorien")
    k.add_argument("--mapping", required=True)
    mit_server(k)

    art = unter.add_parser("artikel")
    art.add_argument("--arbeitsverzeichnis", required=True)
    mit_server(art)

    t = unter.add_parser("texte")
    t.add_argument("--arbeitsverzeichnis", required=True)
    mit_server(t)

    d = unter.add_parser("dateien")
    d.add_argument("--arbeitsverzeichnis", required=True)
    d.add_argument("--dateien", required=True, help="Ordner mit bilder/ und datenblaetter/")
    mit_server(d)

    args = p.parse_args()
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    return {
        "aufbereiten": phase_aufbereiten,
        "kategorien": phase_kategorien,
        "artikel": phase_artikel,
        "texte": phase_texte,
        "dateien": phase_dateien,
    }[args.phase](args)


if __name__ == "__main__":
    sys.exit(main())
