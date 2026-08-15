"""Spielt das aufbereitete Feldmann-Sortiment direkt in die Datenbank ein.

Gegenstueck zu feldmann_import.py, das denselben Bestand ueber die REST-API
einspielt. Dieser Weg schreibt unmittelbar per SQL und bildet dabei genau das
nach, was ArtikelImportService.importiereCsv und ArtikelDokumentService.ladeHoch
sonst tun - inklusive der Regeln, die man beim Schreiben von Hand leicht
uebersieht:

  * Artikel gelten je Lieferant als bekannt, erkannt an der externen
    Artikelnummer in lieferanten_artikel_preise (nicht global).
  * Preisstaende sind historisiert: aktuell=1 traegt genau eine Zeile je
    Artikel und Lieferant, aeltere werden auf 0 gesetzt statt ueberschrieben.
  * Je Artikel gibt es hoechstens ein VORSCHAUBILD; ein neues ersetzt das alte
    samt Datei.
  * Dateien liegen unter uploads/artikel/<artikelId>/<uuid>_<originalname>.

Voraussetzung: feldmann_import.py aufbereiten ist gelaufen.
Zugangsdaten kommen aus application-local.properties; Prod verlangt zusaetzlich
--ziel prod und dort Schreibrechte (der uebliche Zugang ist nur lesend).

Phasen einzeln aufrufbar und jede fuer sich wiederholbar:

    python scripts/feldmann_db_import.py kategorien --arbeitsverzeichnis <dir>
    python scripts/feldmann_db_import.py artikel    --arbeitsverzeichnis <dir> --apply
    python scripts/feldmann_db_import.py dateien    --arbeitsverzeichnis <dir> \\
        --dateien <dir mit bilder/ und datenblaetter/> --apply

Ohne --apply wird nichts geschrieben.
"""
import argparse
import csv
import io
import json
import re
import shutil
import sys
import uuid
from datetime import datetime
from pathlib import Path

try:
    import pymysql
except ImportError:
    sys.exit("pymysql fehlt. Installieren mit: pip install pymysql")

PROJEKT = Path(__file__).resolve().parent.parent
PROPS = PROJEKT / "src" / "main" / "resources" / "application-local.properties"
# Standardablage der laufenden Anwendung. Fuer einen anderen Server laesst sich
# mit --upload-ziel ein Verzeichnis aufbauen, das anschliessend als Ganzes nach
# uploads/artikel/ auf jenen Server kopiert wird - die Ordnernamen sind die
# Artikel-IDs der jeweiligen Datenbank und unterscheiden sich zwischen den
# Instanzen.
UPLOAD_BASIS = PROJEKT / "uploads" / "artikel"
LIEFERANT = "Feldmann"
PROD_HOST = "100.109.109.64"

BILD_ENDUNGEN = ("jpg", "jpeg", "png", "webp", "gif")


# ---------------------------------------------------------------------------
# Verbindung
# ---------------------------------------------------------------------------

def verbindung(ziel):
    """Baut die Verbindung aus application-local.properties auf.

    Dort steht genau ein aktiver Datenbankblock (lokal); die Prod-URL liegt als
    Kommentar daneben. Beide teilen sich die Zugangsdaten, getauscht wird nur
    der Host.
    """
    werte = {}
    for zeile in PROPS.read_text(encoding="utf-8", errors="replace").splitlines():
        roh = zeile.strip().lstrip("#").strip()
        for schluessel in ("url", "username", "password"):
            praefix = f"spring.datasource.{schluessel}="
            if roh.startswith(praefix) and schluessel not in werte:
                werte[schluessel] = roh[len(praefix):].strip()
    if not werte.get("username") or not werte.get("url"):
        sys.exit("Keine Zugangsdaten in application-local.properties gefunden.")

    treffer = re.match(r"jdbc:mysql://([^:/]+):?(\d*)/([A-Za-z0-9_]+)", werte["url"])
    host = PROD_HOST if ziel == "prod" else treffer.group(1)
    return pymysql.connect(
        host=host, port=int(treffer.group(2) or 3306),
        user=werte["username"], password=werte.get("password", ""),
        database=treffer.group(3), charset="utf8mb4", autocommit=False)


def eine(cur, sql, params=()):
    cur.execute(sql, params)
    zeile = cur.fetchone()
    return zeile[0] if zeile else None


# ---------------------------------------------------------------------------
# Stammdaten
# ---------------------------------------------------------------------------

def lieferant_id(cur, anlegen):
    """Feldmann-Lieferant; legt ihn bei Bedarf an (wie es der Import tut)."""
    vorhanden = eine(cur, "SELECT id FROM lieferanten WHERE lieferantenname = %s", (LIEFERANT,))
    if vorhanden:
        # Der Import-Endpoint filtert die Lieferantenliste auf ist_aktiv = 1;
        # ein Lieferant mit NULL taucht in der Oberflaeche sonst nicht auf.
        if anlegen:
            cur.execute(
                "UPDATE lieferanten SET ist_aktiv = 1 WHERE id = %s AND (ist_aktiv IS NULL OR ist_aktiv = 0)",
                (vorhanden,))
        return vorhanden
    if not anlegen:
        return None
    cur.execute(
        "INSERT INTO lieferanten (lieferantenname, ist_aktiv, start_zusammenarbeit) VALUES (%s, 1, %s)",
        (LIEFERANT, datetime.now()))
    return cur.lastrowid


def werkstoff_index(cur):
    cur.execute("SELECT id, name FROM werkstoff")
    return {name.strip().lower(): kid for kid, name in cur.fetchall() if name}


def kategorie_index(cur):
    """{pfad_klein: id} ueber die gesamte Kategoriekette."""
    cur.execute("SELECT id, beschreibung, parent_kategorie_id FROM kategorie")
    zeilen = {kid: (bez, parent) for kid, bez, parent in cur.fetchall()}
    index = {}
    for kid, (bez, parent) in zeilen.items():
        teile, aktuell, tiefe = [bez or ""], parent, 0
        while aktuell in zeilen and tiefe < 8:
            teile.insert(0, zeilen[aktuell][0] or "")
            aktuell = zeilen[aktuell][1]
            tiefe += 1
        index["/".join(teile).lower()] = kid
    return index


def artikel_index(cur, lief_id):
    """{externe_artikelnummer_klein: artikel_id} fuer diesen Lieferanten."""
    cur.execute(
        "SELECT externe_artikelnummer, artikel_id FROM lieferanten_artikel_preise "
        "WHERE lieferant_id = %s AND externe_artikelnummer IS NOT NULL", (lief_id,))
    return {nr.strip().lower(): aid for nr, aid in cur.fetchall() if nr}


# ---------------------------------------------------------------------------
# Phase: Kategorien
# ---------------------------------------------------------------------------

def phase_kategorien(cur, args):
    verzeichnis = json.loads(
        (Path(args.arbeitsverzeichnis) / "verzeichnis.json").read_text(encoding="utf-8"))
    index = kategorie_index(cur)

    benoetigt = []
    for eintrag in verzeichnis:
        teile = eintrag["zielpfad"].split("/")
        for i in range(1, len(teile) + 1):
            teilpfad = "/".join(teile[:i])
            if teilpfad not in benoetigt:
                benoetigt.append(teilpfad)
    benoetigt.sort(key=lambda p: (p.count("/"), p))

    angelegt = vorhanden = 0
    for pfad in benoetigt:
        if pfad.lower() in index:
            vorhanden += 1
            continue
        teile = pfad.split("/")
        eltern_pfad = "/".join(teile[:-1])
        eltern_id = index.get(eltern_pfad.lower()) if eltern_pfad else None
        if eltern_pfad and eltern_id is None:
            print(f"  FEHLER: Oberkategorie '{eltern_pfad}' fehlt")
            continue
        if not args.apply:
            print(f"  geplant: {pfad}")
            index[pfad.lower()] = -1
            angelegt += 1
            continue
        cur.execute(
            "INSERT INTO kategorie (beschreibung, parent_kategorie_id) VALUES (%s, %s)",
            (teile[-1], eltern_id))
        index[pfad.lower()] = cur.lastrowid
        angelegt += 1

    print(f"\n{angelegt} {'angelegt' if args.apply else 'geplant'}, {vorhanden} schon vorhanden")
    return 0


# ---------------------------------------------------------------------------
# Phase: Artikel, Preise, Texte
# ---------------------------------------------------------------------------

def phase_artikel(cur, args):
    arbeit = Path(args.arbeitsverzeichnis)
    verzeichnis = json.loads((arbeit / "verzeichnis.json").read_text(encoding="utf-8"))
    with io.open(arbeit / "texte.csv", encoding="utf-8-sig", newline="") as f:
        texte = {z["materialnummer"]: z for z in csv.DictReader(f, delimiter=";")}

    lief_id = lieferant_id(cur, args.apply)
    if lief_id is None:
        print("Lieferant Feldmann existiert noch nicht (Trockenlauf legt ihn nicht an).")
        lief_id = -1
    kategorien = kategorie_index(cur)
    werkstoffe = werkstoff_index(cur)
    bekannt = artikel_index(cur, lief_id) if lief_id > 0 else {}

    neu = aktualisiert = preis_neu = fehlende_kategorie = 0
    fehlende_werkstoffe = set()
    jetzt = datetime.now()

    for eintrag in verzeichnis:
        kategorie_id = kategorien.get(eintrag["zielpfad"].lower())
        if kategorie_id is None:
            fehlende_kategorie += 1
            print(f"  FEHLER: Kategorie fehlt: {eintrag['zielpfad']}")
            continue

        with io.open(arbeit / "csv" / eintrag["datei"], encoding="utf-8-sig", newline="") as f:
            for zeile in csv.DictReader(f, delimiter=";"):
                nummer = zeile["materialnummer"].strip()
                werkstoff_name = (zeile.get("werkstoff") or "").strip()
                werkstoff_id = None
                if werkstoff_name:
                    werkstoff_id = werkstoffe.get(werkstoff_name.lower())
                    if werkstoff_id is None:
                        fehlende_werkstoffe.add(werkstoff_name)

                text = texte.get(nummer, {})
                preis = zeile["nettopreis"].replace(",", ".")
                artikel_id = bekannt.get(nummer.lower())

                if artikel_id is None:
                    neu += 1
                    if not args.apply:
                        continue
                    cur.execute(
                        "INSERT INTO artikel (produktlinie, produktname, produkttext, "
                        "preiseinheit, verpackungseinheit, kategorie_id, werkstoff_id, "
                        "kurzbeschreibung, beschreibung, system_stammdaten) "
                        "VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,0)",
                        (zeile["produktlinie"], zeile["produktname"], zeile["produkttext"] or None,
                         zeile["preiseinheit"] or "Stk",
                         int(zeile["packgroesse"]) if (zeile.get("packgroesse") or "").isdigit() else None,
                         kategorie_id, werkstoff_id,
                         text.get("kurzbeschreibung") or None, text.get("beschreibung") or None))
                    artikel_id = cur.lastrowid
                    bekannt[nummer.lower()] = artikel_id
                    cur.execute(
                        "INSERT INTO lieferanten_artikel_preise (externe_artikelnummer, preis, "
                        "preis_aenderungsdatum, artikel_id, lieferant_id, aktuell, quelle, erfasst_am) "
                        "VALUES (%s,%s,%s,%s,%s,1,'CSV_IMPORT',%s)",
                        (nummer, preis, jetzt, artikel_id, lief_id, jetzt))
                    preis_neu += 1
                    continue

                # Bekannter Artikel: Stammdaten und Texte nachziehen.
                aktualisiert += 1
                if not args.apply:
                    continue
                cur.execute(
                    "UPDATE artikel SET produktlinie=%s, produktname=%s, produkttext=%s, "
                    "preiseinheit=%s, kategorie_id=%s, werkstoff_id=%s, "
                    "kurzbeschreibung=%s, beschreibung=%s WHERE id=%s",
                    (zeile["produktlinie"], zeile["produktname"], zeile["produkttext"] or None,
                     zeile["preiseinheit"] or "Stk", kategorie_id, werkstoff_id,
                     text.get("kurzbeschreibung") or None, text.get("beschreibung") or None,
                     artikel_id))

                # Preis nur anfassen, wenn er sich geaendert hat - sonst wuerde
                # jeder Lauf eine ueberfluessige Historienzeile erzeugen.
                alt = eine(cur,
                           "SELECT preis FROM lieferanten_artikel_preise "
                           "WHERE artikel_id=%s AND lieferant_id=%s AND aktuell=1",
                           (artikel_id, lief_id))
                if alt is None or str(alt) != str(round(float(preis), 2)):
                    cur.execute(
                        "UPDATE lieferanten_artikel_preise SET aktuell=0 "
                        "WHERE artikel_id=%s AND lieferant_id=%s AND aktuell=1",
                        (artikel_id, lief_id))
                    cur.execute(
                        "INSERT INTO lieferanten_artikel_preise (externe_artikelnummer, preis, "
                        "preis_aenderungsdatum, artikel_id, lieferant_id, aktuell, quelle, erfasst_am) "
                        "VALUES (%s,%s,%s,%s,%s,1,'CSV_IMPORT',%s)",
                        (nummer, preis, jetzt, artikel_id, lief_id, jetzt))
                    preis_neu += 1

    print(f"\nneue Artikel:            {neu}")
    print(f"bestehende aktualisiert: {aktualisiert}")
    print(f"Preisstaende geschrieben:{preis_neu}")
    if fehlende_kategorie:
        print(f"FEHLENDE KATEGORIEN:     {fehlende_kategorie} (erst Phase 'kategorien' laufen lassen)")
    if fehlende_werkstoffe:
        print(f"UNBEKANNTE WERKSTOFFE:   {sorted(fehlende_werkstoffe)} - Artikel bleiben ohne Werkstoff")
    return 1 if fehlende_kategorie else 0


# ---------------------------------------------------------------------------
# Phase: Dateien
# ---------------------------------------------------------------------------

def sicherer_name(text):
    return "".join(c if c.isalnum() or c in "._-" else "_" for c in text)


def phase_dateien(cur, args):
    arbeit = Path(args.arbeitsverzeichnis)
    with io.open(arbeit / "texte.csv", encoding="utf-8-sig", newline="") as f:
        zeilen = list(csv.DictReader(f, delimiter=";"))

    bilder_dir = Path(args.dateien) / "bilder"
    db_dir = Path(args.dateien) / "datenblaetter"
    upload_basis = Path(args.upload_ziel) if args.upload_ziel else UPLOAD_BASIS
    lief_id = lieferant_id(cur, False)
    if not lief_id:
        sys.exit("Lieferant Feldmann fehlt - erst Phase 'artikel' laufen lassen.")
    bekannt = artikel_index(cur, lief_id)

    hoch = ersetzt = fehlt_artikel = fehlt_datei = zu_gross = 0
    jetzt = datetime.now()

    for zeile in zeilen:
        nummer = zeile["materialnummer"].strip()
        artikel_id = bekannt.get(nummer.lower())
        if artikel_id is None:
            fehlt_artikel += 1
            continue

        aufgaben = []
        if zeile.get("bild_url"):
            endung = zeile["bild_url"].rsplit(".", 1)[-1].lower()
            endung = endung if endung in BILD_ENDUNGEN else "jpg"
            aufgaben.append((bilder_dir / f"{sicherer_name(nummer)}.{endung}",
                             "VORSCHAUBILD", None))
        if zeile.get("datenblatt_url"):
            name = sicherer_name(zeile["datenblatt_url"].rsplit("/", 1)[-1])
            if not name.lower().endswith(".pdf"):
                name += ".pdf"
            aufgaben.append((db_dir / name, "DATENBLATT", "Datenblatt"))

        for quelle, typ, beschreibung in aufgaben:
            if not quelle.exists() or quelle.stat().st_size == 0:
                fehlt_datei += 1
                continue
            groesse = quelle.stat().st_size
            # Dieselbe Grenze, die ArtikelDokumentService durchsetzt.
            if groesse > 10 * 1024 * 1024:
                zu_gross += 1
                continue

            # Schon vorhanden? Datenblaetter nicht doppelt anhaengen.
            if typ == "DATENBLATT":
                da = eine(cur, "SELECT id FROM artikel_dokument WHERE artikel_id=%s AND typ=%s "
                               "AND original_dateiname=%s", (artikel_id, typ, quelle.name))
                if da:
                    continue

            alt = None
            if typ == "VORSCHAUBILD":
                cur.execute("SELECT id, gespeicherter_dateiname FROM artikel_dokument "
                            "WHERE artikel_id=%s AND typ='VORSCHAUBILD'", (artikel_id,))
                alt = cur.fetchall()

            gespeichert = f"{uuid.uuid4()}_{quelle.name}"
            if not args.apply:
                hoch += 1
                continue

            ziel_dir = upload_basis / str(artikel_id)
            ziel_dir.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(quelle, ziel_dir / gespeichert)
            cur.execute(
                "INSERT INTO artikel_dokument (artikel_id, original_dateiname, "
                "gespeicherter_dateiname, typ, beschreibung, erstellt_am, dateigroesse_bytes) "
                "VALUES (%s,%s,%s,%s,%s,%s,%s)",
                (artikel_id, quelle.name, gespeichert, typ, beschreibung, jetzt, groesse))
            hoch += 1

            # Erst nach dem erfolgreichen Schreiben das alte Vorschaubild
            # entfernen - genau die Reihenfolge aus ArtikelDokumentService.
            for alt_id, alt_datei in (alt or []):
                cur.execute("DELETE FROM artikel_dokument WHERE id=%s", (alt_id,))
                (ziel_dir / alt_datei).unlink(missing_ok=True)
                ersetzt += 1

        if args.limit and hoch >= args.limit:
            print(f"\nLimit von {args.limit} erreicht - Rest nicht angefasst.")
            break

    print(f"\nDateien {'geschrieben' if args.apply else 'geplant'}: {hoch}")
    print(f"ersetzte Vorschaubilder: {ersetzt}")
    print(f"ohne passenden Artikel:  {fehlt_artikel}")
    print(f"Datei nicht vorhanden:   {fehlt_datei}")
    print(f"ueber 10 MB uebersprungen:{zu_gross}")
    return 0


# ---------------------------------------------------------------------------

def main():
    p = argparse.ArgumentParser(description=__doc__,
                                formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("phase", choices=["kategorien", "artikel", "dateien"])
    p.add_argument("--arbeitsverzeichnis", required=True)
    p.add_argument("--dateien", help="Ordner mit bilder/ und datenblaetter/ (Phase dateien)")
    p.add_argument("--upload-ziel", dest="upload_ziel",
                   help="Zielordner statt uploads/artikel - fuer einen anderen Server "
                        "vorbereiten und den Ordner danach dorthin kopieren")
    p.add_argument("--ziel", choices=["lokal", "prod"], default="lokal")
    p.add_argument("--apply", action="store_true", help="wirklich schreiben")
    p.add_argument("--limit", type=int)
    args = p.parse_args()
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

    if args.phase == "dateien" and not args.dateien:
        sys.exit("--dateien fehlt.")
    if args.ziel == "prod" and args.apply:
        print("!! Schreibender Zugriff auf die PRODUKTIVDATENBANK !!")

    conn = verbindung(args.ziel)
    try:
        with conn.cursor() as cur:
            code = {"kategorien": phase_kategorien,
                    "artikel": phase_artikel,
                    "dateien": phase_dateien}[args.phase](cur, args)
        if args.apply:
            conn.commit()
            print("gespeichert.")
        else:
            conn.rollback()
            print("Trockenlauf - nichts geschrieben. Mit --apply ausfuehren.")
        return code
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()


if __name__ == "__main__":
    sys.exit(main())
