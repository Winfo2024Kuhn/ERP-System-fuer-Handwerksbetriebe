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

```
materialnummer;nettopreis;produktname;produktlinie;werkstoff;preiseinheit;packgroesse;produkttext
```

| Spalte | Pflicht | Typ | Max. | Inhalt |
| --- | --- | --- | --- | --- |
| `materialnummer` | **ja** | Text | 255 | Feldmanns Artikelnummer, **1:1 wie auf der Seite**. Keine Präfixe, keine Umformatierung. Dient als Schlüssel — ohne sie wird die Zeile verworfen. |
| `nettopreis` | **ja** | Dezimalzahl | — | Netto-**Stückpreis** in Euro. Ohne Währungszeichen, ohne Tausendertrenner. Komma oder Punkt als Dezimalzeichen. `18,50` oder `18.50`. **`1.250,00` ist falsch** → `1250,00`. Ohne gültigen Preis wird die Zeile verworfen. |
| `produktname` | ja | Text | 255 | Bezeichnung wie im Shop, z.B. `Handlaufhalter gerade M8`. Das ist der Name, den der Handwerker in der Suche sieht. |
| `produktlinie` | nein | Text | 255 | Feldmann-Serie oder Produktgruppe, z.B. `Glasklemmen` oder `Serie 2000`. Nutzen wir zum Gruppieren. |
| `werkstoff` | nein | Text | 255 | **Nur aus der Liste unter Punkt 4.** Steht auf der Seite ein anderer Werkstoff oder gar keiner → **Feld leer lassen**. |
| `preiseinheit` | nein | Text | 255 | Immer **`Stk`**. Niemals eine Zahl eintragen — eine Zahl wird als Divisor auf den Preis angewendet. |
| `packgroesse` | nein | Ganzzahl | — | Verpackungseinheit als **reine Zahl**, z.B. `10` für "VE 10 Stück". Kein Text drumherum (siehe Punkt 5). Unbekannt → leer. |
| `produkttext` | nein | Text | 255 | Kurze technische Beschreibung, einzeilig, ohne HTML. Bei längeren Shop-Texten sinnvoll auf 255 Zeichen kürzen — **nicht** hart abschneiden. |

**Zusatzspalten sind erlaubt** und werden vom Import ignoriert (nach Namen gelesen, nicht nach
Position). Nützlich für die spätere Pflege — hänge sie **hinten** an:
`oberflaeche;bild_url;datenblatt_url;quelle_url`

---

## 3. Was du NICHT lieferst

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

## 4. Werkstoff — kontrollierte Liste

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

## 5. Fallstricke, die wir dir vorab abnehmen

**Verpackungseinheit:** Aus dem Feld werden alle Nicht-Ziffern entfernt und der Rest als eine
Zahl gelesen. `1/2 Zoll` wird damit zu `12`, `VE 10 (à 2 Stk)` zu `102`. Deshalb: **nur die
reine Zahl** oder leer.

**Preiseinheit:** Steht dort eine Zahl, wird der Preis dadurch geteilt. `100` macht aus 62,00 €
still und leise 0,62 €. Deshalb ausschließlich `Stk`.

**Artikelnummern-Kollision:** Der Import sucht bestehende Artikel nur über die externe
Artikelnummer — **lieferantenübergreifend**. Nutzt ein anderer Lieferant zufällig dieselbe
Nummer, wird dessen Artikel überschrieben statt ein neuer angelegt. Liefere die Nummern
deshalb exakt so, wie Feldmann sie führt, und **melde Duplikate innerhalb deiner eigenen
Datei** — jede `materialnummer` darf in der CSV nur **einmal** vorkommen.

**Textlängen:** Die Textspalten fassen 255 Zeichen. Ein längerer Wert lässt den Import mit einem
Datenbankfehler abbrechen — und weil alles in einer Transaktion läuft, ist dann die **komplette
Datei** nicht importiert. Kürze konsequent.

**Preis fehlt / „auf Anfrage":** Zeile weglassen und in den Report schreiben. Kein `0`, kein
Platzhalter.

---

## 6. Beispiel

```csv
materialnummer;nettopreis;produktname;produktlinie;werkstoff;preiseinheit;packgroesse;produkttext
12345;18,50;Handlaufhalter gerade M8;Handlaufhalter;1.4301;Stk;10;Wandhalter mit Gewindestift, geschliffen K240
12346;62,00;Glasklemme 45x45 flach;Glasklemmen;1.4301;Stk;1;Für Glasstärke 8-10 mm, inkl. Gummieinlagen
12347;3,20;Rohrkappe flach 42,4x2,0;Endkappen;1.4301;Stk;25;Einschlagkappe, geschliffen
12348;1250,00;Pfostenset komplett 4-teilig;Pfosten;1.4571;Stk;1;Seewasserfest, Staffel ab 5 Stk auf Anfrage
12349;7,90;Rosette Kunststoff schwarz;Zubehör;;Stk;50;Kunststoff, kein Werkstoff aus der Stammliste
```

Zeile 5 zeigt den Normalfall für Nicht-Stammwerkstoffe: `werkstoff` bleibt leer.

---

## 7. Selbstcheck vor Abgabe

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

**Report mitliefern:** Anzahl Artikel, Anzahl übersprungener Seiten mit Grund (kein Preis, keine
Artikelnummer, Variantenseite), Preisspanne min/max, Liste der Werkstoffbezeichnungen, die du
verworfen hast.

---

## 8. Hinweis für uns (nicht für den Scraping-Agenten)

`ArtikelImportService.normalizePreis()` erzwingt einen Preis zwischen **0,50 € und 10,00 €** —
gebaut für Stahl-Kilopreise. Auf Feldmann-Stückpreise angewendet heißt das:

| Echter Preis | Ergebnis im ERP |
| --- | --- |
| 3,20 € | 3,20 € — korrekt |
| 18,50 € | Zeile wird kommentarlos übersprungen |
| 62,00 € | **0,62 €** — still verfälscht |
| 1.250,00 € | **1,25 €** — still verfälscht |

Betroffene Codestelle: `ArtikelImportService.java:316-345`, aufgerufen in Zeile 203.
Die Normalisierung muss abgeschaltet oder an den Lieferanten/die Kategorie gekoppelt werden,
**bevor** eine Feldmann-CSV eingelesen wird. Die CSV selbst kann das nicht kompensieren —
gegenzurechnen wäre schlimmer, weil die Umrechnung von der Preishöhe abhängt.

Zweiter Punkt: Der Import matcht über `findByExterneArtikelnummer` (global) statt über
`findByExterneArtikelnummerAndLieferantId`, obwohl es die Methode gibt
(`ArtikelRepository.java:17`). Bei Nummernkollisionen zwischen Lieferanten überschreibt der
Import fremde Artikel.
