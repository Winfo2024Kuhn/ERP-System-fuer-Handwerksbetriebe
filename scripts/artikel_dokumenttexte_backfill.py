#!/usr/bin/env python3
"""Fuellt Kurzbeschreibung und Kundenbeschreibung fuer Bestandsartikel.

Liest ueber GET /api/artikel und schreibt ueber
PATCH /api/artikel/{id}/dokumenttexte zurueck.

Warum ueber die API und nicht per SQL: Die Abmessung ist kein Datenbankfeld,
sondern wird in ArtikelWerkstoffe.getAbmessungText() je nach Profilform
unterschiedlich zusammengesetzt ("42,4 x 2" beim Rundrohr, "2 mm" beim Blech,
"OE 42,4" beim Rundstab). Die API liefert sie als "abmessung" fertig mit.
Ein SQL-Skript muesste diese Regel nachbauen und wuerde beim naechsten
Profiltyp auseinanderdriften.

Ohne --apply wird nichts geschrieben. Standard ist ein Trockenlauf, der nur
eine CSV mit allen geplanten Aenderungen erzeugt.

Der Verkaufsaufschlag wird bewusst NIE mitgesendet: Der Endpunkt arbeitet als
echtes Teil-Update (was nicht im Rumpf steht, bleibt unangetastet), und der
Aufschlag ist eine kaufmaennische Entscheidung, die aus Stammdaten nicht
ableitbar ist.
"""

import argparse
import csv
import getpass
import http.cookiejar
import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request

# Der Server deckelt die Seitengroesse hart auf 50 (ArtikelController.MAX_PAGE_SIZE).
# Mehr anzufragen wird stillschweigend gekuerzt - ein Skript, das dann "weniger
# Treffer als angefragt" als "letzte Seite" deutet, saehe nur die erste Seite.
# Deshalb genau die Servergrenze anfragen und zusaetzlich "gesamt" auswerten.
SEITENGROESSE = 50

# Grenzen des PATCH-Endpunkts (ArtikelService.MAX_KURZBESCHREIBUNG/-BESCHREIBUNG).
MAX_KURZ = 255
MAX_BESCHREIBUNG = 10_000

ZEITLIMIT_SEKUNDEN = 30

# Vorhandene Texte stehen zur Kontrolle mit im Protokoll. Eine gepflegte
# Beschreibung kann 10.000 Zeichen lang sein - fuers Durchsehen reicht der
# Anfang, solange erkennbar bleibt, was drinsteht.
CSV_WERT_LIMIT = 300


def fuer_csv(text) -> str:
    """Kuerzt lange Werte fuer das Protokoll, erkennbar am Zusatz."""
    text = (text or "").strip()
    if len(text) <= CSV_WERT_LIMIT:
        return text
    return text[:CSV_WERT_LIMIT] + " ... [gekuerzt]"

# ---------------------------------------------------------------------------
# Textbau-Regeln
# ---------------------------------------------------------------------------
# Drei Grundsaetze:
#   1. Nichts erfinden - fehlt eine Angabe, entfaellt sie ersatzlos.
#   2. Keine halben Saetze - reicht es nicht fuer einen brauchbaren Text,
#      wird der Artikel uebersprungen und protokolliert.
#   3. Handwerker-Sprache - der Kunde liest "Edelstahl 1.4301", nicht nur
#      den Werkstoffcode; Normkuerzel wie "EN 10219-2" bleiben ganz weg.

# Kundenfreundliche Namen fuer die Werkstoffcodes aus den Stammdaten.
# Ausgangspunkt sind die Anzeigenamen aus Migration
# V337__werkstoff_dichte_und_eignungen.sql; die Zuordnung Code ->
# Werkstofffamilie (1.4301 = Edelstahl, S235JR = Baustahl, ...) stammt
# von dort - neu erfunden wird hier nichts. Die Formulierung ist aber
# bewusst NICHT woertlich uebernommen, sondern fuers Kundendokument
# angepasst: Erklaer-Zusaetze wie "(hochfest)", "(seewasserfest)" und
# "(Blech)" sind gekuerzt, und aus "Stahlblech verzinkt (Sendzimir)"
# wird "verzinktem Stahl (DX51D+Z)", damit der Satz
# "... aus <Werkstoff>" lesbar bleibt.
# Unbekannte Werkstoffe behalten unveraendert ihren technischen Namen.
WERKSTOFF_KLARTEXT = {
    "S235JR": "Baustahl S235JR",
    "S355J2": "Baustahl S355J2",
    "Stahl": "Baustahl",
    "1.4301": "Edelstahl 1.4301",
    "1.4571": "Edelstahl 1.4571",
    "Edelstahl": "Edelstahl",
    "EN AW-6060": "Aluminium EN AW-6060",
    "EN AW-5754": "Aluminium EN AW-5754",
    "Aluminium": "Aluminium",
    "DX51D+Z": "verzinktem Stahl (DX51D+Z)",
    "Kunststoff": "Kunststoff",
}

# Abweichungen vom Profilform-Anzeigenamen fuer das Kundendokument:
# - Winkel: ob gleich- oder ungleichschenklig, sagt die Abmessung (40 x 40
#   bzw. 60 x 40) dem Kunden bereits - das Fachwort waere nur Ballast.
# - SONSTIGES ist ein interner Sammelwert und darf nie als "Sonstiges"
#   auf einem Kundendokument stehen.
PROFILFORM_KUNDE = {
    "WINKEL_GLEICHSCHENKLIG": "Winkel",
    "WINKEL_UNGLEICHSCHENKLIG": "Winkel",
    "SONSTIGES": None,
}

# Fertigungsmerkmale, die dem Kunden etwas sagen und zwei sonst identisch
# beschriebene Positionen unterscheiden (nahtloses vs. geschweisstes Rohr,
# geschliffene vs. rohe Oberflaeche). Alle uebrigen Verfahren/Zustaende
# ("gewalzt", "warmgefertigt", ...) sind der Normalfall und bleiben weg.
VERFAHREN_FUER_KUNDEN = {"NAHTLOS"}
ZUSTAND_FUER_KUNDEN = {"KALTGEZOGEN", "GESCHLIFFEN"}


def enum_name(wert):
    """Technischer Name eines Enum-Felds. Die API liefert Enums als Objekt
    {"name": "RUNDROHR", "anzeigename": "Rundrohr", ...}; zur Sicherheit
    werden auch nackte Strings akzeptiert."""
    if isinstance(wert, dict):
        return wert.get("name")
    if isinstance(wert, str):
        return wert
    return None


def enum_anzeigename(wert):
    if isinstance(wert, dict):
        return wert.get("anzeigename") or wert.get("name")
    if isinstance(wert, str):
        return wert
    return None


def normalisiert(text: str) -> str:
    """Vergleichsform einer Abmessung: '42,4 x 2' und '42.4x2.0' sollen sich
    finden. Kleinbuchstaben, Komma zu Punkt, ohne Leerzeichen, 'mm' und
    Durchmesserzeichen entfernt."""
    text = text.lower().replace(",", ".").replace("Ø", "").replace("ø", "")
    text = re.sub(r"\s+", "", text)
    return text.replace("mm", "")


def hat_klartext_wort(text: str) -> bool:
    """Enthaelt der Text ein echtes Wort (mindestens drei Buchstaben am
    Stueck)? '42.4x2' nicht, 'Tuerschliesser TS 3000' schon."""
    return re.search(r"[A-Za-zÄÖÜäöüß]{3,}", text) is not None


def traegt_sich_selbst(produktname: str) -> bool:
    """Kann der Produktname allein als Kundentext dienen? Nur wenn er mit
    einem echten Wort beginnt ('Tuerschliesser TS 3000', 'Edelstahlrohr
    42,4x2'). Reine Masszeichenfolgen ('42.4x2 nahtlos') oder kryptische
    Lieferantenkuerzel ('RO 42,4X2,0') fallen durch."""
    erster_teil = produktname.split()[0] if produktname.split() else ""
    return hat_klartext_wort(erster_teil)


def profilform_fuer_kunden(artikel: dict):
    name = enum_name(artikel.get("profilform"))
    if not name:
        return None
    if name in PROFILFORM_KUNDE:
        return PROFILFORM_KUNDE[name]
    return enum_anzeigename(artikel.get("profilform"))


def abmessung_mit_einheit(artikel: dict):
    """Abmessung mit Einheit. getAbmessungText() haengt beim Blech schon
    'mm' an ('2 mm') - dann kein zweites Mal anhaengen, sonst stuende
    '2 mm mm' auf dem Angebot."""
    abmessung = (artikel.get("abmessung") or "").strip()
    if not abmessung:
        return None
    return abmessung if abmessung.endswith("mm") else f"{abmessung} mm"


def werkstoff_fuer_kunden(artikel: dict):
    name = (artikel.get("werkstoffName") or "").strip()
    if not name:
        return None
    return WERKSTOFF_KLARTEXT.get(name, name)


def fertigungsmerkmale(artikel: dict) -> list:
    merkmale = []
    if enum_name(artikel.get("herstellverfahren")) in VERFAHREN_FUER_KUNDEN:
        merkmale.append(enum_anzeigename(artikel.get("herstellverfahren")))
    if enum_name(artikel.get("fertigungszustand")) in ZUSTAND_FUER_KUNDEN:
        merkmale.append(enum_anzeigename(artikel.get("fertigungszustand")))
    return [m for m in merkmale if m]


def baue_kurzbeschreibung(artikel: dict):
    """Innensicht: knapp und wiederfindbar. Profilform, Produktname,
    Abmessung (nur wenn sie nicht schon im Produktnamen steckt) und der
    technische Werkstoffname - genau die Begriffe, nach denen der Bediener
    im Editor sucht."""
    teile = []

    form_name = enum_name(artikel.get("profilform"))
    if form_name and form_name != "SONSTIGES":
        anzeige = enum_anzeigename(artikel.get("profilform"))
        if anzeige:
            teile.append(anzeige)

    produktname = (artikel.get("produktname") or "").strip()
    if produktname:
        teile.append(produktname)

    abmessung = (artikel.get("abmessung") or "").strip()
    if abmessung and (not produktname or normalisiert(abmessung) not in normalisiert(produktname)):
        teile.append(abmessung)

    werkstoff = (artikel.get("werkstoffName") or "").strip()
    if werkstoff:
        teile.append(werkstoff)

    if not teile:
        return None
    # Ein einzelner Bestandteil reicht nur, wenn er ein sprechender Name ist
    # ("Tuerschliesser TS 3000"), nicht bei einer nackten Masskette ("42.4x2").
    if len(teile) == 1 and not hat_klartext_wort(teile[0]):
        return None
    return " ".join(teile)[:MAX_KURZ]


def baue_beschreibung(artikel: dict):
    """Kundentext als schlichtes HTML, ein Satz ohne Normkuerzel.

    Regelfall (Halbzeug mit Profilform und Abmessung):
        "Rundrohr 42,4 x 2 mm aus Edelstahl 1.4301, nahtlos"
    Rueckfallebene (z.B. Beschlaege): Der Produktname traegt den Text,
        "Tuerschliesser TS 3000 silber aus Baustahl".
    Reicht beides nicht, wird der Artikel uebersprungen - ein fehlender
    Text ist besser als "Rundrohr , mm" auf einem Angebot.
    """
    form = profilform_fuer_kunden(artikel)
    abmessung = abmessung_mit_einheit(artikel)
    werkstoff = werkstoff_fuer_kunden(artikel)

    if form and abmessung:
        satz = f"{form} {abmessung}"
        if werkstoff:
            satz += f" aus {werkstoff}"
        merkmale = fertigungsmerkmale(artikel)
        if merkmale:
            satz += ", " + ", ".join(merkmale)
        return f"<p>{escape(satz)}</p>"

    produktname = (artikel.get("produktname") or "").strip()
    if not produktname or not traegt_sich_selbst(produktname):
        return None
    satz = produktname
    if werkstoff:
        satz += f" aus {werkstoff}"
    if abmessung and normalisiert(abmessung) not in normalisiert(produktname):
        satz += f", {abmessung}"
    beschreibung = f"<p>{escape(satz)}</p>"
    return beschreibung if len(beschreibung) <= MAX_BESCHREIBUNG else None


def escape(text: str) -> str:
    return (text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;"))


# ---------------------------------------------------------------------------
# HTTP-Klient (Session-Login + CSRF, nur Standardbibliothek)
# ---------------------------------------------------------------------------

class ErpClient:
    """Kapselt Session-Cookie und CSRF-Token.

    /api/** verlangt einen angemeldeten Benutzer. Der Login laeuft ueber
    POST /api/auth/login (Formulardaten); das dabei gesetzte Session-Cookie
    haelt der CookieJar. Schreibende Aufrufe brauchen zusaetzlich den Wert
    des XSRF-TOKEN-Cookies als X-XSRF-TOKEN-Header.
    """

    def __init__(self, basis_url: str):
        self.basis_url = basis_url.rstrip("/")
        self.cookies = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(
            urllib.request.HTTPCookieProcessor(self.cookies))

    def login(self, benutzer: str, passwort: str) -> None:
        rumpf = urllib.parse.urlencode(
            {"username": benutzer, "password": passwort}).encode("utf-8")
        anfrage = urllib.request.Request(
            f"{self.basis_url}/api/auth/login", data=rumpf, method="POST",
            headers={"Content-Type": "application/x-www-form-urlencoded"})
        with self.opener.open(anfrage, timeout=ZEITLIMIT_SEKUNDEN) as antwort:
            antwort.read()

    def get_json(self, pfad: str, params: dict | None = None):
        url = f"{self.basis_url}{pfad}"
        if params:
            url += "?" + urllib.parse.urlencode(params)
        anfrage = urllib.request.Request(url, headers={"Accept": "application/json"})
        with self.opener.open(anfrage, timeout=ZEITLIMIT_SEKUNDEN) as antwort:
            return json.load(antwort)

    def patch_json(self, pfad: str, rumpf: dict) -> None:
        daten = json.dumps(rumpf).encode("utf-8")
        headers = {"Content-Type": "application/json"}
        token = self._xsrf_token()
        if token:
            headers["X-XSRF-TOKEN"] = token
        anfrage = urllib.request.Request(
            f"{self.basis_url}{pfad}", data=daten, method="PATCH", headers=headers)
        with self.opener.open(anfrage, timeout=ZEITLIMIT_SEKUNDEN) as antwort:
            antwort.read()

    def _xsrf_token(self):
        for cookie in self.cookies:
            if cookie.name == "XSRF-TOKEN":
                return cookie.value
        return None


def alle_artikel(client: ErpClient):
    """Liefert alle Artikel seitenweise. Ende, wenn eine Seite leer ist,
    weniger als die Seitengroesse liefert oder 'gesamt' erreicht ist."""
    seite = 0
    while True:
        daten = client.get_json("/api/artikel", {
            "page": seite, "size": SEITENGROESSE, "sort": "produktname"})
        treffer = daten.get("artikel") or []
        if not treffer:
            return
        yield from treffer
        gesamt = daten.get("gesamt")
        groesse = daten.get("seitenGroesse") or SEITENGROESSE
        if len(treffer) < groesse:
            return
        if isinstance(gesamt, int) and (seite + 1) * groesse >= gesamt:
            return
        seite += 1


def schreibe(client: ErpClient, artikel_id: int, kurz: str, beschreibung: str) -> None:
    """Schreibt die beiden Texte.

    Der Endpunkt arbeitet als echtes Teil-Update: Was nicht im Rumpf steht,
    bleibt unangetastet. Der Verkaufsaufschlag wird deshalb bewusst NICHT
    mitgesendet - er ist eine kaufmaennische Entscheidung und aus Stammdaten
    nicht ableitbar.
    """
    client.patch_json(f"/api/artikel/{artikel_id}/dokumenttexte", {
        "kurzbeschreibung": kurz,
        "beschreibung": beschreibung,
    })


# ---------------------------------------------------------------------------
# Ablauf
# ---------------------------------------------------------------------------

def main() -> int:
    # Windows-Konsolen laufen teils noch mit cp1252 - Umlaute und das
    # Durchmesserzeichen duerfen die Zusammenfassung nicht abstuerzen lassen.
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(errors="replace")
        sys.stderr.reconfigure(errors="replace")

    parser = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--url", default="http://localhost:8080",
                        help="Basis-URL des ERP (Standard: http://localhost:8080)")
    parser.add_argument("--benutzer", default=os.environ.get("ERP_BENUTZER"),
                        help="Login-Benutzer (oder Umgebungsvariable ERP_BENUTZER)")
    parser.add_argument("--passwort", default=None,
                        help="Login-Passwort. Besser weglassen: dann wird die "
                             "Umgebungsvariable ERP_PASSWORT genutzt oder "
                             "unsichtbar nachgefragt.")
    parser.add_argument("--apply", action="store_true",
                        help="Tatsaechlich schreiben. Ohne dieses Flag nur Trockenlauf.")
    parser.add_argument("--protokoll", default="artikel_backfill.csv",
                        help="Pfad der CSV mit allen geplanten Aenderungen")
    args = parser.parse_args()

    client = ErpClient(args.url)

    if args.benutzer:
        passwort = args.passwort or os.environ.get("ERP_PASSWORT")
        if not passwort:
            if not sys.stdin.isatty():
                print("Kein Passwort: --passwort oder ERP_PASSWORT setzen.", file=sys.stderr)
                return 2
            passwort = getpass.getpass(f"Passwort fuer {args.benutzer}: ")
        try:
            client.login(args.benutzer, passwort)
        except urllib.error.HTTPError as e:
            print(f"Login als '{args.benutzer}' fehlgeschlagen (HTTP {e.code}).", file=sys.stderr)
            return 2
        except urllib.error.URLError as e:
            print(f"ERP unter {args.url} nicht erreichbar: {e.reason}", file=sys.stderr)
            return 2

    gesetzt = uebersprungen_gepflegt = uebersprungen_duerftig = fehler = 0

    with open(args.protokoll, "w", newline="", encoding="utf-8-sig") as datei:
        schreiber = csv.writer(datei, delimiter=";")
        schreiber.writerow(["id", "artikelnummer", "produktname", "profilform",
                            "aktion", "alte_kurzbeschreibung", "alte_beschreibung",
                            "neue_kurzbeschreibung", "neue_beschreibung"])

        try:
            for artikel in alle_artikel(client):
                artikel_id = artikel.get("id")
                nummer = artikel.get("artikelnummer") or ""
                name = artikel.get("produktname") or ""
                form = enum_name(artikel.get("profilform")) or ""
                alte_kurz = fuer_csv(artikel.get("kurzbeschreibung"))
                alte_beschreibung = fuer_csv(artikel.get("beschreibung"))

                # Bereits gepflegte Artikel bleiben unangetastet. Ein von Hand
                # geschriebener Kundentext ist immer besser als ein generierter.
                # Die vorhandenen Werte stehen mit im Protokoll, damit beim
                # Durchsehen erkennbar ist, WAS da schon steht - echter Text
                # oder nur ein Rest wie "<p></p>".
                if alte_kurz or alte_beschreibung:
                    uebersprungen_gepflegt += 1
                    schreiber.writerow([artikel_id, nummer, name, form,
                                        "uebersprungen: schon gepflegt",
                                        alte_kurz, alte_beschreibung, "", ""])
                    continue

                kurz = baue_kurzbeschreibung(artikel)
                beschreibung = baue_beschreibung(artikel)
                if not kurz or not beschreibung:
                    uebersprungen_duerftig += 1
                    schreiber.writerow([artikel_id, nummer, name, form,
                                        "uebersprungen: zu wenig Daten",
                                        alte_kurz, alte_beschreibung, "", ""])
                    continue

                if args.apply:
                    try:
                        schreibe(client, artikel_id, kurz, beschreibung)
                        gesetzt += 1
                        schreiber.writerow([artikel_id, nummer, name, form,
                                            "geschrieben",
                                            alte_kurz, alte_beschreibung,
                                            kurz, beschreibung])
                    except (urllib.error.HTTPError, urllib.error.URLError) as e:
                        fehler += 1
                        grund = f"HTTP {e.code}" if isinstance(e, urllib.error.HTTPError) else str(e.reason)
                        schreiber.writerow([artikel_id, nummer, name, form,
                                            f"fehler: {grund}",
                                            alte_kurz, alte_beschreibung,
                                            kurz, beschreibung])
                        print(f"FEHLER bei Artikel {artikel_id}: {grund}", file=sys.stderr)
                else:
                    gesetzt += 1
                    schreiber.writerow([artikel_id, nummer, name, form,
                                        "geplant",
                                        alte_kurz, alte_beschreibung,
                                        kurz, beschreibung])
        except urllib.error.HTTPError as e:
            if e.code in (401, 403):
                print(f"Zugriff verweigert (HTTP {e.code}). Login noetig: "
                      f"--benutzer angeben (Passwort wird abgefragt).", file=sys.stderr)
            else:
                print(f"Abbruch: HTTP {e.code} beim Lesen von {args.url}.", file=sys.stderr)
            return 2
        except urllib.error.URLError as e:
            print(f"ERP unter {args.url} nicht erreichbar: {e.reason}", file=sys.stderr)
            return 2
        except json.JSONDecodeError:
            print(f"Antwort von {args.url} ist kein JSON - ist das die richtige "
                  f"Basis-URL?", file=sys.stderr)
            return 2

    modus = "geschrieben" if args.apply else "geplant (Trockenlauf, nichts geschrieben)"
    print(f"{gesetzt} {modus}")
    print(f"{uebersprungen_gepflegt} uebersprungen, weil schon gepflegt")
    print(f"{uebersprungen_duerftig} uebersprungen, weil zu wenig Daten")
    if fehler:
        print(f"{fehler} Fehler - siehe Protokoll und Ausgabe oben", file=sys.stderr)
    print(f"Protokoll: {args.protokoll}")
    return 1 if fehler else 0


if __name__ == "__main__":
    raise SystemExit(main())
