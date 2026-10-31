#!/usr/bin/env python3
"""Übungs-Shop für die IDS-Shop-Anbindung – nur zum lokalen Testen, kommt nie ins Programm.

Start:  python3 -I scripts/ids-testshop/testshop.py   (lauscht nur auf 127.0.0.1:9000)
Im ERP als Shop-Adresse eintragen:  http://localhost:9000/ids

Der Shop nimmt das Anmeldeformular des ERP an (multipart oder url-kodiert), zeigt, welche Felder
angekommen sind (das Passwort nur als „gesetzt“/„leer“), lässt Beispielartikel anklicken und schickt
den Warenkorb als IDS-Connect-2.5-XML im Feld „warenkorb“ (multipart) per Browser an die hookurl zurück.
"""
import html
import json
import sys
from email.parser import BytesParser
from email.policy import HTTP
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 9000

ARTIKEL = [
    # Artikelnummer, Kurztext, Einheit (IDS-Code), Netto-Preis je Preisbasis, Preisbasis, Hinweis.
    # Zurückgeschickt wird wie bei Würth: Preisbasis 1 → NetPrice = Preis der ganzen Zeile (Stückpreis × Menge),
    # Preisbasis > 1 → NetPrice = Preis je Basis (z. B. je 100 Stück).
    ("0892 161 1", "Silikon transparent 310 ml", "KRT", "8.40", "1", ""),
    ("0903 450 300", "Injektionsmörtel WIT-VM 250 420 ml", "KRT", "21.90", "1", ""),
    ("0157 106 100", "Holzbauschraube 6x100 Senkkopf", "PCE", "12.50", "100", ""),
    ("0912 800 12", "Gewindestange M12 1 m verzinkt", "MTR", "3.10", "1", ""),
    ("0700 116 000", "Schweißdraht SG2 1,0 mm 15 kg", "KGM", "", "1", "Preis auf Anfrage"),
]

SEITE = """<!doctype html><html lang="de"><head><meta charset="utf-8"><title>Übungs-Shop (IDS)</title>
<style>body{{font-family:system-ui,sans-serif;max-width:900px;margin:2rem auto;color:#1e293b}}
table{{border-collapse:collapse;width:100%}}td,th{{border-bottom:1px solid #e2e8f0;padding:.4rem;text-align:left}}
.box{{background:#f8fafc;border:1px solid #e2e8f0;border-radius:8px;padding:1rem;margin:1rem 0}}
button{{background:#e11d48;color:#fff;border:0;border-radius:6px;padding:.5rem 1rem;margin:.25rem;cursor:pointer}}
button.grau{{background:#64748b}}input[type=number]{{width:5rem}}</style></head><body>
<h1>Übungs-Shop (IDS-Connect)</h1>
<div class="box"><b>Angekommen vom ERP</b> ({enctype}):<br>{felder}</div>
{warnung}
<form id="f" method="post" enctype="multipart/form-data" action="{hookurl}">
<input type="hidden" name="warenkorb" id="warenkorb">
<table><tr><th>Art.-Nr.</th><th>Artikel</th><th>Preis netto</th><th>Menge</th></tr>{zeilen}</table>
<p><button type="submit" data-art="normal">Warenkorb übernehmen</button>
<button type="submit" class="grau" data-art="bestellt">… mit Bestellung</button>
<button type="submit" class="grau" data-art="kaputt">Kaputter Warenkorb</button>
<button type="submit" class="grau" data-art="leer">Leerer Warenkorb</button></p></form>
<script>
const artikel = {artikel_json};
const esc = s => s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
// Wie Würth: ohne Preisbasis (1) ist NetPrice der Preis der ganzen Zeile.
const netPrice = (a, menge) => a[4] === '1' ? (Number(a[3]) * Number(menge)).toFixed(2) : a[3];
document.querySelectorAll('button[data-art]').forEach(b => b.addEventListener('click', () => {{
  const art = b.dataset.art;
  if (art === 'kaputt') {{ document.getElementById('warenkorb').value = '<kein xml'; return; }}
  if (art === 'leer') {{ document.getElementById('warenkorb').value = ''; return; }}
  let items = '';
  artikel.forEach((a, i) => {{
    const menge = document.getElementById('m' + i).value;
    if (!menge || Number(menge) <= 0) return;
    items += '<OrderItem><ArtNo>' + esc(a[0]) + '</ArtNo><Qty>' + Number(menge).toFixed(2) + '</Qty><QU>' + a[2] + '</QU>'
      + '<Kurztext>' + esc(a[1]) + '</Kurztext>' + (a[3] ? '<NetPrice>' + netPrice(a, menge) + '</NetPrice><PriceBasis>' + a[4] + '</PriceBasis>' : '')
      + (a[5] ? '<Hinweis>' + esc(a[5]) + '</Hinweis>' : '') + '</OrderItem>';
  }});
  const kz = art === 'bestellt' ? 'Warenkorbrückgabe mit Bestellung' : 'Warenkorbrückgabe';
  document.getElementById('warenkorb').value = '<?xml version="1.0" encoding="UTF-8"?><Warenkorb><WarenkorbInfo>'
    + '<Date>' + new Date().toISOString().slice(0,10) + '</Date><Time>12:00:00</Time><RueckgabeKZ>' + kz
    + '</RueckgabeKZ><Version>2.5</Version></WarenkorbInfo><Order><OrderInfo><Cur>EUR</Cur></OrderInfo>' + items + '</Order></Warenkorb>';
}}));
</script></body></html>"""


def lies_formular(handler):
    laenge = int(handler.headers.get("Content-Length") or 0)
    if laenge > 1_000_000:
        return {}
    body = handler.rfile.read(laenge)
    typ = handler.headers.get("Content-Type", "")
    if typ.startswith("multipart/"):
        msg = BytesParser(policy=HTTP).parsebytes(b"Content-Type: " + typ.encode() + b"\r\n\r\n" + body)
        felder = {}
        for teil in msg.iter_parts():
            name = teil.get_param("name", header="content-disposition")
            if name:
                felder[name] = teil.get_payload(decode=True).decode("utf-8", "replace")
        return felder
    return {k: v[0] for k, v in parse_qs(body.decode("utf-8", "replace")).items()}


class Shop(BaseHTTPRequestHandler):
    def do_GET(self):
        self.antwort(200, "<p>Übungs-Shop läuft. Im ERP als Shop-Adresse <code>http://localhost:%d/ids</code> eintragen.</p>" % PORT)

    def do_POST(self):
        if urlparse(self.path).path != "/ids":
            return self.antwort(404, "unbekannt")
        felder = lies_formular(self)
        hook = felder.get("hookurl", "")
        if urlparse(hook).scheme not in ("http", "https"):
            return self.antwort(400, "<p>Keine gültige hookurl angekommen.</p>")
        anzeige = []
        for name, wert in felder.items():
            if name == "pw_kunde":
                wert = "gesetzt" if wert else "leer"
            elif name == "hookurl":
                wert = wert[: wert.rfind("/") + 1] + "…"  # Schlüssel nicht anzeigen
            anzeige.append("<code>%s</code> = %s" % (html.escape(name), html.escape(wert)))
        warnung = ""
        if "action" not in felder:
            warnung = '<div class="box">Ohne <code>action</code> angemeldet (Variante „Nur Zugangsfelder“).</div>'
        zeilen = "".join(
            "<tr><td>%s</td><td>%s</td><td>%s</td><td><input type=number min=0 step=1 id=m%d value=%d></td></tr>"
            % (html.escape(a[0]), html.escape(a[1]),
               (("%s € je Stück (geht als Zeilenpreis zurück)" % a[3]) if a[4] == "1" else ("%s € je %s Stück" % (a[3], a[4]))) if a[3] else html.escape(a[5]), i, 5 if i == 0 else 0)
            for i, a in enumerate(ARTIKEL))
        seite = SEITE.format(enctype=html.escape(self.headers.get("Content-Type", "").split(";")[0]),
                             felder="<br>".join(anzeige), warnung=warnung, hookurl=html.escape(hook, quote=True),
                             zeilen=zeilen, artikel_json=json.dumps(ARTIKEL))
        self.antwort(200, seite)

    def antwort(self, code, inhalt):
        daten = inhalt.encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Content-Length", str(len(daten)))
        self.end_headers()
        self.wfile.write(daten)

    def log_message(self, fmt, *args):
        # Keine Formularinhalte loggen (Passwort).
        sys.stderr.write("[testshop] %s %s\n" % (self.command, self.path))


if __name__ == "__main__":
    print("Übungs-Shop auf http://localhost:%d/ids" % PORT)
    ThreadingHTTPServer(("127.0.0.1", PORT), Shop).serve_forever()
