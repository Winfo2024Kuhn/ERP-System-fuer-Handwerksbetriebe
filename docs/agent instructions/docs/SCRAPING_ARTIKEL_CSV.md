# Auftrag: Feldmann-Artikel scrapen und für den Import liefern

Du bist ein Scraping-Agent. Du liest die Produktseiten des Lieferanten **Feldmann** aus und
lieferst **eine CSV-Datei plus zwei Ordner** mit den heruntergeladenen Vorschaubildern und
Datenblättern. Wir spielen beides danach mit `scripts/feldmann_import.py` über die Anwendung
ein (Abschnitt 9). Die Anwendung prüft dabei einiges, aber nicht alles — was hier als Regel
steht, musst du selbst einhalten.

Es geht um **Zukaufteile**: fertige Kaufteile (Handlaufhalter, Glasklemmen, Rosetten,
Endkappen, Rohrverbinder …). Diese Artikel haben **keine Massnorm und keine Abmaße** im
technischen Sinn und werden **je Stück** verkauft. Es sind also **keine** Halbzeuge/Profile —
alles, was mit Profilform, Herstellverfahren, Massnorm oder Kilopreis zu tun hat, ist hier
**nicht** zu erfassen.

---

## 1. Format der CSV

| Regel | Wert | Warum |
| --- | --- | --- |
| Dateiname | `feldmann_artikel.csv` | So heißt die Quelle beim Einspielen. |
| Trennzeichen | **Semikolon `;`** | Deutsche Locale, kollidiert nicht mit dem Komma als Dezimaltrennzeichen in `nettopreis`. |
| `;` im Wert | **verboten** — durch `,` ersetzen | Die Anwendung zerlegt jede Zeile stur an `;` und kennt keine Anführungszeichen. Ein `;` im Wert zerreißt die Zeile, auch wenn er in `"…"` steht. |
| `"` im Wert | **verboten** — weglassen oder durch `''` ersetzen | Ein Wert mit `"` wird beim Aufbereiten in Anführungszeichen gesetzt, und die landen dann wörtlich im Artikel. |
| Zeilenumbruch im Wert | **verboten** — durch ` – ` ersetzen | Die Anwendung liest Zeile für Zeile. |
| Kommas im Wert | erlaubt, ohne Anführungszeichen | `,` ist nicht das Trennzeichen. |
| Kodierung | **UTF-8** | Mit oder ohne BOM. |
| Zeilenende | `\n` oder `\r\n` | beides wird gelesen |
| Kopfzeile | **Pflicht**, exakt kleingeschrieben | Spalten werden per Name gelesen, nicht per Position. Fehlt `materialnummer`, `nettopreis` oder `produktname`, bricht das Aufbereiten mit einem Fehler ab. Fehlt `produktlinie`, bekommt kein Artikel eine Kategorie, und es wird keiner eingespielt. |
| Spaltenzahl | in jeder Zeile gleich | Fehlende Werte als leeres Feld (`;;`), nie weglassen. |

> **Wenn du mit Python schreibst:** `csv.writer(f, delimiter=';')` ist richtig — aber nur,
> weil nach den Regeln oben **kein** Wert mehr `;`, `"` oder einen Zeilenumbruch enthält.
> Bereinige die Werte also **vor** dem Schreiben; verlass dich nicht auf das automatische
> Quoting.

---

## 2. Spalten

Kopfzeile exakt so, in dieser Reihenfolge:

```text
materialnummer;nettopreis;produktname;produktlinie;werkstoff;preiseinheit;packgroesse;produkttext;bild_url;datenblatt_url
```

| Spalte | Pflicht | Typ | Max. | Inhalt |
| --- | --- | --- | --- | --- |
| `materialnummer` | **ja** | Text | 255 | Feldmanns Artikelnummer, **1:1 wie auf der Seite**. Keine Präfixe, keine Umformatierung. Daran erkennt der Import, ob es den Artikel bei Feldmann schon gibt. Ohne sie wird die Zeile übersprungen. |
| `nettopreis` | **ja** | Dezimalzahl | — | Netto-**Stückpreis** in Euro. Ohne Währungszeichen, ohne Tausendertrenner. Komma oder Punkt als Dezimalzeichen: `18,50` oder `18.50`. **`1.250,00` ist falsch** → `1250,00` (sonst überspringt der Import die Zeile). Ohne gültigen Preis ist die Zeile für uns wertlos — weglassen (Abschnitt 6). |
| `produktname` | ja | Text | 255 | Bezeichnung **in der Schreibweise des Shops**, z.B. `Handlaufhalter gerade M8, V2A` — auch Maße bleiben so, wie sie dort stehen. Daraus entsteht der **Kundentext fürs Angebot**: `V2A`/`V4A` werden dort entfernt, und hinten kommt der Werkstoff im Klartext dazu („… aus Edelstahl 1.4301“). Beginne den Namen deshalb mit einem echten Wort, nicht mit einer Nummer. |
| `produktlinie` | **ja** | Text | 255 | Feldmann-Serie oder Produktgruppe, z.B. `Glasklemmen`. **Darüber wird die Kategorie im ERP zugeordnet** (Abschnitt 4). Schreib dieselbe Linie immer exakt gleich. Artikel mit leerer oder unbekannter Linie bleiben beim Einspielen außen vor. |
| `werkstoff` | nein | Text | 255 | **Nur aus der Liste in Abschnitt 5 und nur bei eindeutiger Angabe.** Sonst → **Feld leer lassen**. |
| `preiseinheit` | nein | Text | 255 | Immer **`Stk`** (leer wird zu `Stk`). **Nie eine Zahl** — steht hier eine Zahl, teilt der Import den Preis durch sie. |
| `packgroesse` | nein | Ganzzahl | — | Verpackungseinheit als **reine Zahl**, z.B. `10` für „VE 10 Stück“. Der Import wirft alle Nicht-Ziffern weg — aus `10-25` würde `1025`. Unbekannt oder keine einzelne Zahl → leer. |
| `produkttext` | nein | Text | 255 | Kurze technische Beschreibung, einzeilig, ohne HTML. Wird zur internen Kurzbeschreibung (Innensicht, nie aufs Kundendokument). Bei längeren Shop-Texten sinnvoll auf 255 Zeichen kürzen — **nicht** hart abschneiden. |
| `bild_url` | nein | Text | — | Adresse des Vorschaubilds im Shop. **Höchstens ein** Bild je Artikel. Die Datei selbst lieferst du in `bilder/` (Abschnitt 3); aus der Adresse ergibt sich ihre Endung. Kein Bild → leer. |
| `datenblatt_url` | nein | Text | — | Adresse des Datenblatts (PDF) im Shop. **Höchstens eines** je Artikel. Die Datei lieferst du in `datenblaetter/` (Abschnitt 3); aus der Adresse ergibt sich ihr Dateiname. Kein Datenblatt → leer. |

> **Max. 255 ist hart:** Ein zu langer Wert in `produktname`, `produktlinie`, `produkttext`,
> `werkstoff` oder `preiseinheit` lässt nicht nur diese Zeile, sondern **den Import der ganzen
> Kategorie** scheitern.

**Preishistorie:** Ändert sich der Preis eines schon bekannten Artikels, legt der Import einen
neuen Preisstand an; der alte bleibt im Verlauf. Ein unveränderter Preis erzeugt keinen neuen
Stand.

**Suche:** Die Artikelsuche im ERP zerlegt die Eingabe an Leerzeichen, Kommas und dem „x“
zwischen zwei Zahlen. Jedes Teilwort muss in einem dieser Felder vorkommen: `produktname`,
`produktlinie`, `produkttext`, Werkstoff (Nummer oder Werkstattname wie „V2A“), `materialnummer`
oder Lieferantenname. Die Eingabe „42,4“ wird zu „42“ und „4“ und findet `42.4` auch —
umgekehrt findet „42.4“ ein `42,4` nicht. Steht im `produktname` ein Maß mit Komma, schreib es
deshalb in `produkttext` **zusätzlich mit Punkt** (`42.4`).

**Zusatzspalten sind erlaubt** und werden ignoriert (nach Namen gelesen, nicht nach Position).
Nützlich für die spätere Pflege — hänge sie ganz **hinten** an: `oberflaeche;quelle_url`.
`quelle_url` dient nur der Nachverfolgung, welche Shop-Seite du gescrapt hast.

---

## 3. Dateien: Vorschaubild und Datenblatt

Neben der CSV lieferst du zwei Ordner mit den **heruntergeladenen** Dateien. Wir laden nichts
nach — die Spalten `bild_url` und `datenblatt_url` bestimmen nur, **wie** die Datei heißen muss.

```text
feldmann_artikel.csv
bilder/
├── 12345.jpg
├── 12346.png
└── 12348.jpg
datenblaetter/
├── DB_12345.pdf
├── 12347_datenblatt.pdf
└── Montage_12348.pdf
```

**Vorschaubild → `bilder/<materialnummer>.<endung>`**

- `<materialnummer>`: genau wie in der CSV; jedes Zeichen außer Buchstaben, Ziffern, `.`, `_`
  und `-` wird zu `_`.
- `<endung>`: das, was in `bild_url` nach dem letzten Punkt steht, kleingeschrieben — wenn es
  `jpg`, `jpeg`, `png`, `webp` oder `gif` ist. Sonst (andere Endung, Adresse mit `?…` am Ende)
  heißt die Datei `.jpg`, und dann muss sie auch **wirklich ein JPEG** sein: wandle sie um.
- Beispiel: Artikel `12345`, `bild_url` `https://shop.example/img/12345_gross.PNG` →
  `bilder/12345.png`.

**Datenblatt → `datenblaetter/<name>`**

- `<name>`: der Teil von `datenblatt_url` nach dem letzten `/`, genau so, wie er in der CSV
  steht; jedes Zeichen außer Buchstaben, Ziffern, `.`, `_` und `-` wird zu `_`; endet er nicht
  auf `.pdf`, wird `.pdf` angehängt.
- Beispiele: `https://shop.example/pdf/DB_12345.pdf` → `datenblaetter/DB_12345.pdf`;
  `https://shop.example/pdf/DB%2012345.pdf` → `datenblaetter/DB_2012345.pdf` (das `%` wird zu
  `_`, die `20` bleibt).
- Nur PDF. Einen `?…`-Anhang an der Adresse lässt du weg, solange der Name dadurch eindeutig
  bleibt — sonst wird er unnötig lang (`DB.pdf?dl=1` → `DB.pdf_dl_1.pdf`).

**Eindeutige Namen:** Den Namen gibt die Adresse vor, du kannst ihn nicht frei wählen. Ergeben
zwei **verschiedene** Dateien denselben Namen (z.B. `…/12345/datenblatt.pdf` und
`…/12346/datenblatt.pdf` → beide `datenblatt.pdf`), lass `datenblatt_url` bei **allen**
betroffenen Artikeln leer und melde ihre Artikelnummern im Report — sonst hinge an einem Artikel
still das falsche Datenblatt. Dieselbe Datei für mehrere Artikel ist dagegen erlaubt.

**Grenzen beim Dateinamen:** kein `..` im Namen (die Anwendung lehnt ihn ab) und höchstens 200
Zeichen. Ergibt sich aus der Adresse ein Name, der das verletzt, lass die zugehörige Spalte
(`bild_url` bzw. `datenblatt_url`) leer und melde den Artikel im Report.

**Grenzen, die die Anwendung prüft:** höchstens **10 MB** je Datei, nur die Endungen `pdf`,
`png`, `jpg`, `jpeg`, `webp`, `gif`. Was darüber liegt, lehnt sie ab. Ist eine Datei größer,
rechne Bilder herunter; ein zu großes Datenblatt lässt du weg und vermerkst es im Report
(Abschnitt 8). Leere Dateien (0 Byte) werden übersprungen.

**Andere Unterlagen** (Zulassung, Zeichnung, Montageanleitung …) lädt der Import nicht hoch.
Liefere sie nicht mit; nenne im Report nur, bei wie vielen Artikeln es welche gab.

**Keine Fremdlinks:** Im ERP landet die Datei, nie ein Link auf den Shop von Feldmann. Ein Link
ist morgen tot oder zeigt hinter ein Login — die heruntergeladene Datei nicht.

---

## 4. Was du NICHT lieferst

Diese Felder setzt der Import mit einem festen Wert oder später ein Mensch im ERP — eine
Spalte dafür in der CSV wird ignoriert:

- **Lieferant** — beim Einspielen fest „Feldmann“.
- **Kategorie** — ergibt sich beim Einspielen aus `produktlinie` über eine Zuordnungstabelle,
  die wir pflegen (`kategorie_mapping.csv`), und wird nur bei neuen Artikeln gesetzt. Du
  lieferst dafür im Report die Liste aller Produktlinien (Abschnitt 8).
- **Interne Artikelnummer** (`artikel.artikelnummer`) — vergibt der Import nicht, sie bleibt
  leer. Gefunden wird der Artikel trotzdem über die `materialnummer`.
- **Kundentext und Kurzbeschreibung** — erzeugt das Einspielen selbst aus `produktname`,
  `werkstoff` und `produkttext`.
- **Währung** — es gibt keine Spalte dafür. Alles ist EUR.
- **Verkaufspreis / Aufschlag / Marge** — wird im ERP gepflegt, nie gescrapt.
- **Massnorm, Werkstoffnorm, Profilform, Herstellverfahren, Fertigungszustand,
  Verzinkungsgeeignet, Pulverbeschichtungsgeeignet, `hicad_name`** — bei Zukaufteilen nicht
  anwendbar. Leer lassen, keine Spalte anlegen.
- **Bruttopreise, Staffelpreise, Aktionspreise, UVP** — wir wollen genau **einen**
  Netto-Stückpreis je Artikel. Gibt es nur Staffelpreise, nimm den Preis der **kleinsten**
  Abnahmemenge und vermerke die Staffel in `produkttext`.

---

## 5. Werkstoff — kontrollierte Liste

Der Werkstoff landet im **Kundentext fürs Angebot** („… aus Edelstahl 1.4301“). Ein falscher
Werkstoff steht also schwarz auf weiß beim Kunden — ein leeres Feld dagegen ist harmlos und
reparierbar. Außerdem legt der Import einen Namen, den das ERP nicht kennt, **ungefragt als
neuen Werkstoff** an, ohne Dichte und Eigenschaften.

Trag deshalb **nur eindeutige Angaben** ein, und nur in diesen Schreibweisen:

| Erlaubter Wert | Nur wenn auf der Seite wörtlich steht … |
| --- | --- |
| `1.4301` | V2A, A2, AISI 304 oder 1.4301 |
| `1.4404` | V4A, A4, AISI 316L oder 1.4404 |
| `S355J2` | S355J2 |
| `DX51D+Z` | DX51D |
| `EN AW-5754` | EN AW-5754 oder AlMg3, „5754“ nur als Werkstoffangabe (nicht als Teil einer Nummer) |

**Allgemeine Wörter reichen nicht:** „Edelstahl“, „Stahl“, „Baustahl“, „Aluminium“ oder
„seewasserfest“ → **Feld leer lassen**. Bei Feldmann beschreiben solche Wörter oft die
Oberfläche, nicht das Material („Aluminium E4/EV1“ ist eine Eloxalfarbe).

**1.4571, 316Ti, AISI 316 (ohne L) oder 1.4401** ausdrücklich auf der Seite → Feld leer lassen
und im Report melden. Feldmanns „V4A“ ist die Sorte **1.4404**; ein geliefertes `1.4571`
würde das Einspielen ohne Rückfrage auf `1.4404` umschreiben.

Steht `V2A`/`V4A` nur im Produktnamen und das Feld `werkstoff` ist leer, ergänzt das Einspielen
den Werkstoff selbst. Widersprechen sich Name und Feld (Name „V4A“, Feld `1.4301`), bleibt der
Werkstoff leer — da wird nicht geraten.

**Alles andere** (Messing, Kunststoff, Zinkdruckguss, Gummi, „Edelstahloptik", Materialmix,
keine Angabe) → **Feld leer lassen**. Rate nicht.

---

## 6. Fallstricke, auf die du achten musst

**Artikelnummern-Dubletten:** Der Import erkennt einen Artikel an `materialnummer` zusammen
mit dem Lieferanten — ohne Rücksicht auf Groß-/Kleinschreibung und Leerzeichen am Rand. Kommt
dieselbe Nummer zweimal vor, überschreibt die zweite Zeile die erste; bei abweichendem Preis
entsteht dazu ein falscher Preisstand. **Melde Duplikate** — jede `materialnummer` darf in der
CSV nur **einmal** vorkommen.

**Textlängen:** Die Textspalten sind auf 255 Zeichen begrenzt. Ein längerer Wert lässt den
Import der ganzen Kategorie scheitern (Abschnitt 2). Kürze konsequent.

**Preis fehlt / „auf Anfrage":** Zeile weglassen und in den Report schreiben. Kein `0`, kein
Platzhalter.

---

## 7. Beispiel

```csv
materialnummer;nettopreis;produktname;produktlinie;werkstoff;preiseinheit;packgroesse;produkttext;bild_url;datenblatt_url
12345;18,50;Handlaufhalter gerade M8, V2A;Handlaufhalter;1.4301;Stk;10;Wandhalter mit Gewindestift, für Rundrohr 42.4 mm, geschliffen K240;https://shop.example/img/12345_gross.jpg;https://shop.example/pdf/DB_12345.pdf
12346;62,00;Glasklemme 45x45 flach;Glasklemmen;1.4301;Stk;1;Für Glasstärke 8-10 mm, inkl. Gummieinlagen;https://shop.example/img/12346.png;
12347;3,20;Rohrkappe flach 42,4x2,0;Endkappen;1.4301;Stk;25;Einschlagkappe für Rohr 42.4x2.0, geschliffen;;https://shop.example/pdf/12347_datenblatt.pdf
12348;1250,00;Pfostenset komplett 4-teilig, V4A;Pfosten;1.4404;Stk;1;Staffel ab 5 Stk auf Anfrage;https://shop.example/img/12348.jpg;https://shop.example/pdf/Montage_12348.pdf
12349;7,90;Rosette Kunststoff schwarz;Zubehör;;Stk;50;Kunststoff, kein Werkstoff aus der Stammliste;;
```

Zeile 1 zeigt, wohin ein `;` aus dem Shop-Text gehört: Er wurde zu `,`. Zeile 3 zeigt die
Maß-Regel: Der Name bleibt wie im Shop (`42,4x2,0`), im `produkttext` steht das Maß zusätzlich
mit Punkt. In Zeile 2 und 3 steht `1.4301`, weil die Shop-Seite dort „Werkstoff: V2A“ angibt —
ohne solche Angabe bliebe das Feld leer. Zeile 2 hat ein Vorschaubild, aber kein Datenblatt,
Zeile 3 ein Datenblatt, aber kein Vorschaubild. Zeile 5 zeigt den Normalfall für
Nicht-Stammwerkstoffe: `werkstoff` bleibt leer. Die Ordner dazu stehen in Abschnitt 3.

---

## 8. Selbstcheck vor Abgabe

Führe diese Prüfungen aus und liefere das Ergebnis mit:

1. Die ersten zehn Spalten der Kopfzeile stimmen **zeichengenau** mit Abschnitt 2 überein
   (klein, `;`-getrennt); Zusatzspalten stehen nur dahinter.
2. Jede Zeile hat beim Zerlegen an `;` **exakt so viele Felder** wie die Kopfzeile Spalten hat.
3. Kein Wert enthält `;`, `"` oder einen Zeilenumbruch.
4. `materialnummer` in jeder Zeile gefüllt und **dateiweit eindeutig**, auch ohne Rücksicht
   auf Groß-/Kleinschreibung.
5. `nettopreis` in jeder Zeile gefüllt, parst als Dezimalzahl, **kein Tausendertrenner**.
6. `produktlinie` in jeder Zeile gefüllt.
7. `preiseinheit` überall `Stk`.
8. `packgroesse` ist entweder leer oder rein numerisch.
9. `werkstoff` ist entweder leer oder einer von `1.4301`, `1.4404`, `S355J2`, `DX51D+Z`,
   `EN AW-5754` — und dann jeweils mit eindeutiger Angabe auf der Seite (Abschnitt 5).
10. Kein Textfeld länger als 255 Zeichen.
11. Datei ist UTF-8, Umlaute im Klartext lesbar.
12. Zu jeder gefüllten `bild_url` liegt die Datei unter dem Namen aus Abschnitt 3 in `bilder/`,
    zu jeder gefüllten `datenblatt_url` in `datenblaetter/`.
13. Keine Datei größer als 10 MB, keine leer, jede mit einer der Endungen `pdf`, `png`, `jpg`,
    `jpeg`, `webp`, `gif` — und der Inhalt passt zur Endung.
14. Kein Dateiname steht für zwei verschiedene Dateien; keiner enthält `..` oder ist länger als
    200 Zeichen. Wo das nicht ging, ist die URL-Spalte leer und der Artikel im Report gemeldet.

**Report mitliefern:** Anzahl Artikel, **Liste aller Produktlinien mit Anzahl Artikel** (für
die Kategorie-Zuordnung), Anzahl übersprungener Seiten mit Grund (kein Preis, keine
Artikelnummer, Variantenseite), Preisspanne min/max, Liste der Werkstoffbezeichnungen, die du
verworfen hast (mit Artikelnummer bei 1.4571, 316Ti, AISI 316 und 1.4401), Liste der wegen
Übergröße (> 10 MB) weggelassenen Datenblätter mit Artikelnummer, Fälle mit gleichem
Dateinamen, Anzahl Artikel mit weiteren Unterlagen, die nicht mitgeliefert wurden.

---

## 9. Hinweis für uns (nicht für den Scraping-Agenten)

> **Bekannter Fehler — vor dem Einspielen beheben:** `aufbereiten` schreibt die Import-CSVs in
> `<dir>/csv/` mit BOM (`utf-8-sig`). `ArtikelImportService` entfernt die BOM nicht aus der
> Kopfzeile, erkennt die Spalte `materialnummer` dadurch nicht und überspringt **jede** Zeile —
> Phase 3 meldet trotzdem Erfolg, es kommt aber kein neuer Artikel an, und `texte`/`dateien`
> finden danach keine neuen Artikel. Bis das im Code behoben ist, nach **jedem** `aufbereiten`
> die BOM aus den Dateien in `<dir>/csv/` entfernen (nicht mit Excel speichern — „CSV UTF-8“
> schreibt sie wieder hinein):
>
> ```bash
> python -c "import pathlib,sys; [p.write_bytes(p.read_bytes().removeprefix(b'\xef\xbb\xbf')) for p in pathlib.Path(sys.argv[1]).glob('*.csv')]" <dir>/csv
> ```
>
> Kontrolle nach Phase 3: Ein Trockenlauf von `texte` meldet „N Feldmann-Artikel im Bestand
> gefunden“ — N muss um die eingespielten Artikel gewachsen sein.

Eingespielt wird mit `scripts/feldmann_import.py`, Phase für Phase. `aufbereiten` braucht
keinen Server und schreibt immer. Die Phasen 2–5 sind ohne `--apply` Trockenläufe; sie brauchen
`--benutzer` (oder `ERP_BENUTZER`), das Passwort kommt per Abfrage oder aus `ERP_PASSWORT`,
`--url` ist ohne Angabe `http://localhost:8080`.

1. `aufbereiten --quelle feldmann_artikel.csv --mapping kategorie_mapping.csv --arbeitsverzeichnis <dir>`
   — korrigiert die Werkstoffe (Abschnitt 5) und erzeugt je Zielkategorie eine Import-CSV
   sowie `texte.csv`. `kategorie_mapping.csv` hat die Spalten `produktlinie;zielpfad`
   (`zielpfad` z.B. `Geländerbau/Glasklemmen`); Linien ohne Eintrag bleiben außen vor.
2. `kategorien --mapping kategorie_mapping.csv` — legt fehlende Kategorien an.
3. `artikel --arbeitsverzeichnis <dir>` — `POST /api/artikel/import`, je Kategorie eine Datei.
4. `texte --arbeitsverzeichnis <dir>` — Kurzbeschreibung und Kundentext
   (`PATCH /api/artikel/{id}/dokumenttexte`).
5. `dateien --arbeitsverzeichnis <dir> --dateien <dir mit bilder/ und datenblaetter/>` —
   `POST /api/artikel/{id}/dokumente`. **Nur einmal mit `--apply` laufen lassen:** Jeder Lauf
   hängt die Datenblätter erneut an, nur das Vorschaubild wird ersetzt. Das Skript kann schon
   erledigte Artikel nicht überspringen (`--limit` zählt immer von vorn). Nach einem Abbruch
   daher ein zweites Arbeitsverzeichnis anlegen, dessen `texte.csv` nur noch die fehlenden
   Artikel enthält, und `dateien` damit fortsetzen.

Was dabei in der Anwendung passiert (`ArtikelImportService`, `ArtikelDokumentService`):

- **Preiskorrektur** (`normalizePreis()`, presst Preise auf 0,50 € – 10,00 €): läuft nur mit
  `preiskorrekturAnwenden=true`. Der Endpunkt hat `false` als Vorgabe, und das Skript schickt
  ausdrücklich `false` — Stückpreise bleiben unverändert.
- **Artikel-Abgleich:** je Lieferant über die externe Artikelnummer, ohne Rücksicht auf
  Groß-/Kleinschreibung (`findByExterneArtikelnummerAndLieferantId`), nicht global.
- **Eine Transaktion je Kategorie-Datei:** Scheitert eine Zeile an der Datenbank (z.B. Text
  über 255 Zeichen), wird die ganze Datei zurückgerollt.
- **Werkstoffe:** Ein Name, den der Werkstoff-Stamm nicht kennt, wird ohne Dichte neu angelegt.
  Eine frische Installation kennt `1.4301` noch nicht, und die Anwendung hat keine Maske zum
  Anlegen von Werkstoffen. Deshalb vor dem Einspielen direkt in der Datenbank (ergänzt auch
  einen Eintrag, den ein früherer Import schon ohne Dichte angelegt hat):

  ```sql
  INSERT INTO werkstoff (name, anzeigename, werkstattname, werkstoffnorm, dichte,
                         verzinkungsgeeignet, pulverbeschichtungsgeeignet, beschichtungshinweis)
  VALUES ('1.4301', 'Edelstahl 1.4301', 'V2A', 'EN 10088-2', 7.900, false, true,
          'Nicht feuerverzinken.')
  ON CONFLICT (name) DO UPDATE
     SET anzeigename = EXCLUDED.anzeigename, werkstattname = EXCLUDED.werkstattname,
         werkstoffnorm = EXCLUDED.werkstoffnorm, dichte = EXCLUDED.dichte,
         pulverbeschichtungsgeeignet = EXCLUDED.pulverbeschichtungsgeeignet,
         beschichtungshinweis = EXCLUDED.beschichtungshinweis
   WHERE werkstoff.dichte IS NULL;
  ```

  Ohne Werkstattnamen fände die Suche nach „V2A“ die Artikel nur noch über den Produktnamen.
  Das Skript setzt außerdem `1.4541`, wenn diese Nummer im Namen oder Text steht; auch die fehlt
  im Stamm.
- **Werkstoff trotz leerem Feld:** Das Skript setzt `1.4404` auch dann, wenn `werkstoff` leer
  ist, aber „V4A“ im Namen oder „1.4571“/„1.4404“ in Name oder Text steht. Die Artikel, die der
  Scraping-Agent wegen 1.4571, 316Ti, AISI 316 oder 1.4401 gemeldet hat, nach dem Einspielen
  deshalb gezielt nachprüfen.
- **Dateien** gehen durch die Upload-Prüfungen (10 MB, Endungs-Liste, bereinigter Dateiname,
  kein `..`); ein neues Vorschaubild ersetzt ein vorhandenes samt Datei.
- **Zeilen zerlegen:** Der Import trennt stur an `;` — daher das Verbot in Abschnitt 1.
