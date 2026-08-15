# Auftrag: Feldmann-Artikel scrapen und als Import-CSV liefern

Du bist ein Scraping-Agent. Du liest die Produktseiten des Lieferanten **Feldmann** aus und
schreibst genau **eine CSV-Datei**, die unser ERP ohne Nacharbeit einliest.

Es geht um **Zukaufteile**: fertige Kaufteile (Handlaufhalter, Glasklemmen, Rosetten,
Endkappen, Rohrverbinder …). Diese Artikel haben **keine Massnorm und keine Abmaße** im
technischen Sinn und werden **je Stück** verkauft. Es sind also **keine** Halbzeuge/Profile —
alles, was mit Profilform, Herstellverfahren, Massnorm oder Kilopreis zu tun hat, ist hier
**nicht** zu erfassen.

---

## 1. Harte Formatregeln (Verstoß = Import kaputt)

Der Importer ist ein einfacher Zeilen-Splitter, **kein RFC-4180-CSV-Parser**. Deshalb gilt:

| Regel | Wert | Warum |
| --- | --- | --- |
| Trennzeichen | **Semikolon `;`** | Der Import splittet hart auf `;`. Komma wird beim eigentlichen Import **nicht** erkannt. |
| Anführungszeichen | **verboten** | Es gibt kein Quoting. `"Halter A"` landet inklusive Gänsefüßchen im Produktnamen. |
| Semikolon im Wert | **verboten** | Zerreißt die Zeile. Ersetze es durch ein Komma. |
| Mehrfachwerte in `dokumente`/`dokument_typen` | **Pipe `\|`** | Nur in diesen zwei Spalten erlaubt. Semikolon ist bereits das Spaltentrennzeichen — ein zweites Semikolon im Feld würde die Zeile zerreißen. |
| Zeilenumbruch im Wert | **verboten** | Eine Zeile = ein Artikel. Ersetze Umbrüche durch ` – `. |
| Kodierung | **UTF-8 mit BOM** | Sichert Umlaute (Größe, Öse, Weiß). |
| Zeilenende | `\n` oder `\r\n` | beides wird gelesen |
| Kopfzeile | **Pflicht**, exakt kleingeschrieben | Header werden per Name gematcht, nicht per Position. |
| Spaltenzahl | in jeder Zeile gleich | Fehlende Werte als leeres Feld (`;;`), nie weglassen. |
| Pflichtspalten | ganz **vorne** | Leere Felder am Zeilenende werden beim Splitten abgeschnitten. |

> **Wenn du mit Python schreibst:** kein `csv.QUOTE_MINIMAL`. Nimm
> `csv.writer(f, delimiter=';', quoting=csv.QUOTE_NONE, escapechar=None)` und säubere die
> Werte **vorher** (`value.replace(';', ',').replace('\n', ' – ').replace('"', '')`).
> Bricht der Writer wegen eines Sonderzeichens ab, ist der Wert nicht sauber — repariere den
> Wert, nicht das Quoting.

---

## 2. Spalten

Kopfzeile exakt so, in dieser Reihenfolge:

```text
materialnummer;nettopreis;produktname;produktlinie;werkstoff;preiseinheit;packgroesse;produkttext;vorschaubild;dokumente;dokument_typen
```

| Spalte | Pflicht | Typ | Max. | Inhalt |
| --- | --- | --- | --- | --- |
| `materialnummer` | **ja** | Text | 255 | Feldmanns Artikelnummer, **1:1 wie auf der Seite**. Keine Präfixe, keine Umformatierung. Dient als Schlüssel — ohne sie wird die Zeile verworfen. |
| `nettopreis` | **ja** | Dezimalzahl | — | Netto-**Stückpreis** in Euro. Ohne Währungszeichen, ohne Tausendertrenner. Komma oder Punkt als Dezimalzeichen. `18,50` oder `18.50`. **`1.250,00` ist falsch** → `1250,00`. Ohne gültigen Preis wird die Zeile verworfen. |
| `produktname` | ja | Text | 255 | Bezeichnung wie im Shop, z.B. `Handlaufhalter gerade M8`. Das ist der Name, den der Handwerker in der Suche sieht. |
| `produktlinie` | nein | Text | 255 | Feldmann-Serie oder Produktgruppe, z.B. `Glasklemmen` oder `Serie 2000`. Nutzen wir zum Gruppieren. |
| `werkstoff` | nein | Text | 255 | **Nur aus der Liste unter Punkt 5.** Steht auf der Seite ein anderer Werkstoff oder gar keiner → **Feld leer lassen**. |
| `preiseinheit` | nein | Text | 255 | Immer **`Stk`**. Niemals eine Zahl eintragen — eine Zahl wird als Divisor auf den Preis angewendet. |
| `packgroesse` | nein | Ganzzahl | — | Verpackungseinheit als **reine Zahl**, z.B. `10` für "VE 10 Stück". Kein Text drumherum (siehe Punkt 6). Unbekannt → leer. |
| `produkttext` | nein | Text | 255 | Kurze technische Beschreibung, einzeilig, ohne HTML. Bei längeren Shop-Texten sinnvoll auf 255 Zeichen kürzen — **nicht** hart abschneiden. |
| `vorschaubild` | nein | Text | 255 | Dateiname des Vorschaubilds im Ordner `dateien/` (siehe Abschnitt 3), z.B. `12345_vorschau.jpg`. Kein Pfad, keine URL — nur der Dateiname. **Höchstens ein** Bild je Artikel. Kein Bild vorhanden → Feld leer lassen. |
| `dokumente` | nein | Text | — | Dateinamen weiterer Unterlagen im Ordner `dateien/`, mit Pipe `\|` getrennt, z.B. `12345_zulassung.pdf\|12345_zeichnung.pdf`. **Kein Semikolon** (siehe Abschnitt 1). Keine Unterlagen vorhanden → Feld leer lassen. |
| `dokument_typen` | nein* | Text | — | Ein Typ je Eintrag in `dokumente`, **gleiche Reihenfolge, gleiche Anzahl**, ebenfalls mit `\|` getrennt. Erlaubt: `ZULASSUNG`, `ZEICHNUNG`, `DATENBLATT`, `MONTAGEANLEITUNG`, `SONSTIGES`. |

\* `dokument_typen` ist **Pflicht, sobald `dokumente` gefüllt ist** — sonst lässt sich eine
Datei keinem Typ zuordnen. `vorschaubild` bekommt beim Hochladen automatisch den Typ
`VORSCHAUBILD`; der taucht nicht in `dokument_typen` auf, das ist ausschließlich für die
Einträge in `dokumente` reserviert.

**Zusatzspalten sind erlaubt** und werden vom Import ignoriert (nach Namen gelesen, nicht nach
Position). Nützlich für die spätere Pflege — hänge sie ganz **hinten** an, nach den
Dokumentenspalten: `oberflaeche;quelle_url`. `quelle_url` dient nur der Nachverfolgung, welche
Shop-Seite du gescrapt hast — **nicht** der Bild- oder Dokumentenlieferung. Dafür gelten
ausschließlich `vorschaubild` und `dokumente` (Abschnitt 3), nie ein Link.

---

## 3. Dateien: Vorschaubild und Unterlagen

Neben der CSV lieferst du einen Ordner `dateien/` mit allen heruntergeladenen Bildern und
PDFs, auf die die Spalten `vorschaubild` und `dokumente` verweisen.

**Ablage:** CSV und Ordner nebeneinander:

```text
artikel.csv
dateien/
├── 12345_vorschau.jpg
├── 12345_zulassung.pdf
├── 12345_zeichnung.pdf
├── 12346_vorschau.jpg
├── 12347_datenblatt.pdf
├── 12348_vorschau.jpg
└── 12348_montage.pdf
```

**Dateinamen:** keine Leerzeichen, keine Umlaute, keine Sonderzeichen außer `_`, `-`, `.`.
Präfix immer die `materialnummer`, damit Datei und Zeile eindeutig zuordenbar bleiben, z.B.
`12345_vorschau.jpg`, `12345_zulassung_din.pdf`.

**Erlaubte Formate:** `pdf`, `png`, `jpg`, `jpeg`, `webp`, `gif`. Andere Formate (z.B. `.docx`,
`.zip`, `.tiff`) werden abgelehnt — wandle sie vorher um.

**Maximalgröße:** 10 MB je Datei. Größere Bilder vorher herunterrechnen. Ist ein Dokument
selbst über 10 MB groß, lässt du es weg und vermerkst das im Report (Abschnitt 8).

**Vorschaubild:** höchstens **eines** je Artikel. Lädt das ERP später ein zweites hoch, ersetzt
es automatisch das erste — liefere also gleich das richtige Bild.

**Wie die Dateien im ERP landen:** Lieferung und Verknüpfung sind **zwei getrennte Schritte**.
Du lieferst CSV und `dateien/`-Ordner — das Verknüpfen mit den Artikeln läuft **nicht**
automatisch nebenher, es gibt (Stand heute) bewusst keinen Massen-Import, der beides in einem
Rutsch einliest. Das Verknüpfen ist ein eigener Schritt danach, der **nicht zu deiner
Lieferung gehört**: Für jede Datei wird nach dem CSV-Import der zugehörige Artikel über seine
`materialnummer` (die externe Artikelnummer, siehe Abschnitt 6) gesucht, um die interne
Artikel-`id` zu bekommen — erst damit lässt sich der passende Endpoint aufrufen, über vier
Endpoints unter `/api/artikel`:

| Endpoint | Zweck |
| --- | --- |
| `POST /{id}/dokumente` | Datei hochladen. Multipart-Felder: `datei`, `typ`, optional `beschreibung`. |
| `GET /{id}/dokumente` | Dokumente eines Artikels auflisten. |
| `GET /dokumente/{dokumentId}/datei` | Datei herunterladen. |
| `DELETE /dokumente/{dokumentId}` | Dokument löschen. |

Für dich als Scraping-Agent sind die Endpoints Hintergrundwissen, kein Auftrag. Deine Lieferung
ist abgeschlossen, sobald CSV und `dateien/`-Ordner die Regeln oben erfüllen — Format, Größe,
ein Vorschaubild je Artikel, und jeder Wert in `dokument_typen` exakt einer der erlaubten Typen
aus Abschnitt 2.

**Keine Fremdlinks:** Dateien werden heruntergeladen und mitgeliefert, nie als URL auf den Shop
von Feldmann hinterlegt. Ein Link ist morgen tot oder zeigt hinter ein Login — die Datei im
`dateien/`-Ordner nicht.

---

## 4. Was du NICHT lieferst

Diese Felder setzt der Mensch beim Upload bzw. später im ERP — eine Spalte dafür wird ignoriert
oder richtet Schaden an:

- **Lieferant** — wird im Upload-Dialog gewählt, nicht aus der CSV gelesen.
- **Kategorie** — wird im Upload-Dialog gewählt und nur bei neuen Artikeln gesetzt.
- **Währung** — die Spalte existiert im Mapping, wird aber nirgends ausgewertet. Alles ist EUR.
- **Verkaufspreis / Aufschlag / Marge** — wird im ERP gepflegt, nie gescrapt.
- **Massnorm, Werkstoffnorm, Profilform, Herstellverfahren, Fertigungszustand, Abmaße,
  Gewicht, Dichte** — bei Zukaufteilen nicht anwendbar. Leer lassen, keine Spalte anlegen.
- **Bruttopreise, Staffelpreise, Aktionspreise, UVP** — wir wollen genau **einen** Netto-Stückpreis
  je Artikel. Gibt es nur Staffelpreise, nimm den Preis der **kleinsten** Abnahmemenge und
  vermerke die Staffel in `produkttext`.

---

## 5. Werkstoff — kontrollierte Liste

Der Import legt jeden unbekannten Werkstoffnamen **automatisch neu an**. Schreibst du
`Edelstahl`, `V2A`, `Edelstahl rostfrei` und `INOX`, entstehen vier Karteileichen ohne Dichte
und ohne Norm.

Erlaubt sind ausschließlich diese Schreibweisen:

| Erlaubter Wert | Wenn auf der Seite steht … |
| --- | --- |
| `1.4301` | V2A, A2, AISI 304, Edelstahl (innen/normal) |
| `1.4571` | V4A, A4, AISI 316Ti, seewasserfest |
| `S355J2` | Baustahl, Stahl, Konstruktionsstahl |
| `DX51D+Z` | verzinktes Stahlblech, Sendzimir |
| `EN AW-5754` | Aluminium, Alu-Blech |

**Alles andere** (Messing, Kunststoff, Zinkdruckguss, Gummi, „Edelstahloptik", Materialmix,
keine Angabe) → **Feld leer lassen**. Rate nicht. Ein leeres Feld ist reparierbar, eine
falsch angelegte Werkstoff-Karteileiche nicht.

---

## 6. Fallstricke, die wir dir vorab abnehmen

**Verpackungseinheit:** Aus dem Feld werden alle Nicht-Ziffern entfernt und der Rest als eine
Zahl gelesen. `1/2 Zoll` wird damit zu `12`, `VE 10 (à 2 Stk)` zu `102`. Deshalb: **nur die
reine Zahl** oder leer.

**Preiseinheit:** Steht dort eine Zahl, wird der Preis dadurch geteilt. `100` macht aus 62,00 €
still und leise 0,62 €. Deshalb ausschließlich `Stk`.

**Artikelnummern-Kollision:** Der Import sucht bestehende Artikel nur **innerhalb des beim
Upload gewählten Lieferanten** (Feldmann). Nutzt ein anderer Lieferant zufällig dieselbe
Nummer, bleiben beide Artikel getrennt erhalten. Liefere die Nummern trotzdem exakt so, wie
Feldmann sie führt, und **melde Duplikate innerhalb deiner eigenen Datei** — jede
`materialnummer` darf in der CSV nur **einmal** vorkommen, sonst überschreibt die zweite Zeile
die erste.

**Textlängen:** Die Textspalten fassen 255 Zeichen. Ein längerer Wert lässt den Import mit einem
Datenbankfehler abbrechen — und weil alles in einer Transaktion läuft, ist dann die **komplette
Datei** nicht importiert. Kürze konsequent. Ausgenommen sind `dokumente` und `dokument_typen`:
dort zählt nicht die Zeichenlänge, sondern die Zahl der Pipe-getrennten Einträge (Abschnitt 2).

**Preis fehlt / „auf Anfrage":** Zeile weglassen und in den Report schreiben. Kein `0`, kein
Platzhalter.

---

## 7. Beispiel

```csv
materialnummer;nettopreis;produktname;produktlinie;werkstoff;preiseinheit;packgroesse;produkttext;vorschaubild;dokumente;dokument_typen
12345;18,50;Handlaufhalter gerade M8;Handlaufhalter;1.4301;Stk;10;Wandhalter mit Gewindestift, geschliffen K240;12345_vorschau.jpg;12345_zulassung.pdf|12345_zeichnung.pdf;ZULASSUNG|ZEICHNUNG
12346;62,00;Glasklemme 45x45 flach;Glasklemmen;1.4301;Stk;1;Für Glasstärke 8-10 mm, inkl. Gummieinlagen;12346_vorschau.jpg;;
12347;3,20;Rohrkappe flach 42,4x2,0;Endkappen;1.4301;Stk;25;Einschlagkappe, geschliffen;;12347_datenblatt.pdf;DATENBLATT
12348;1250,00;Pfostenset komplett 4-teilig;Pfosten;1.4571;Stk;1;Seewasserfest, Staffel ab 5 Stk auf Anfrage;12348_vorschau.jpg;12348_montage.pdf;MONTAGEANLEITUNG
12349;7,90;Rosette Kunststoff schwarz;Zubehör;;Stk;50;Kunststoff, kein Werkstoff aus der Stammliste;;;
```

Zeile 5 zeigt den Normalfall für Nicht-Stammwerkstoffe: `werkstoff` bleibt leer. Zeile 2 hat ein
Vorschaubild, aber keine weiteren Unterlagen — `dokumente` und `dokument_typen` bleiben leer.
Zeile 3 hat kein Vorschaubild, aber ein Datenblatt. Der zugehörige `dateien/`-Ordner steht in
Abschnitt 3 — jede hier referenzierte Datei ist dort aufgeführt.

---

## 8. Selbstcheck vor Abgabe

Führe diese Prüfungen aus und liefere das Ergebnis mit:

1. Kopfzeile stimmt **zeichengenau** mit Punkt 2 überein (klein, `;`-getrennt).
2. Jede Zeile hat **gleich viele** `;` wie die Kopfzeile.
3. Kein `"` und kein Zeilenumbruch in irgendeinem Wert.
4. `materialnummer` in jeder Zeile gefüllt und **dateiweit eindeutig**.
5. `nettopreis` in jeder Zeile gefüllt, parst als Dezimalzahl, **kein Tausendertrenner**.
6. `preiseinheit` überall `Stk`.
7. `packgroesse` ist entweder leer oder rein numerisch.
8. `werkstoff` ist entweder leer oder einer von: `1.4301`, `1.4571`, `S355J2`, `DX51D+Z`, `EN AW-5754`.
9. Kein Textfeld länger als 255 Zeichen.
10. Datei ist UTF-8 mit BOM, Umlaute im Klartext lesbar.
11. `dokumente` und `dokument_typen` haben in jeder Zeile **gleich viele** Pipe-getrennte
    Einträge — ein leeres Feld zählt in beiden als 0.
12. Jede in `vorschaubild` und `dokumente` genannte Datei liegt tatsächlich im Ordner `dateien/`.
13. Keine Datei im Ordner `dateien/` größer als 10 MB.
14. Jede Datei hat eine der Endungen `pdf`, `png`, `jpg`, `jpeg`, `webp`, `gif`.
15. Jeder Eintrag in `dokument_typen` ist einer von: `ZULASSUNG`, `ZEICHNUNG`, `DATENBLATT`,
    `MONTAGEANLEITUNG`, `SONSTIGES`.

**Report mitliefern:** Anzahl Artikel, Anzahl übersprungener Seiten mit Grund (kein Preis, keine
Artikelnummer, Variantenseite), Preisspanne min/max, Liste der Werkstoffbezeichnungen, die du
verworfen hast, Liste der wegen Übergröße (> 10 MB) weggelassenen Dokumente mit Artikelnummer
(siehe Abschnitt 3).

---

## 9. Hinweis für uns (nicht für den Scraping-Agenten)

Zwei Dinge, die seit der ersten Fassung dieses Dokuments repariert wurden — hier festgehalten,
damit niemand erneut danach sucht:

**Preis-Normalisierung ist aus.** `ArtikelImportService.normalizePreis()` presste früher jeden
Preis in den Bereich 0,50 € – 10,00 € (gebaut für Stahl-Kilopreise) und verfälschte damit
Feldmann-Stückpreise still. Die Korrektur läuft heute nur noch, wenn der Import sie über einen
API-Parameter ausdrücklich anfordert — die Vorgabe steht auf **aus**. Was in `nettopreis`
steht, landet unverändert in der Datenbank. Verwirft oder korrigiert der Import trotzdem einmal
einen Preis, steht das mit Artikelnummer, Ausgangswert und Ergebnis im Log, statt lautlos zu
verschwinden.

**Einschränkung:** Es gibt dafür bislang **keinen Schalter in der Oberfläche** — die Korrektur
ist ausschließlich über den API-Parameter erreichbar. Für den normalen Weg über die
Upload-Oberfläche heißt das: Preise werden immer unverändert übernommen.

**Artikel-Matching ist lieferantenspezifisch.** Der Import sucht bestehende Artikel nur noch
über `findByExterneArtikelnummerAndLieferantId`, nicht mehr global über
`findByExterneArtikelnummer`. Zwei Lieferanten mit derselben Artikelnummer überschreiben sich
dadurch nicht mehr gegenseitig (siehe auch Abschnitt 6).
