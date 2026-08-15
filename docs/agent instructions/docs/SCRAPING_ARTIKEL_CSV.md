# Auftrag: Feldmann-Artikel scrapen und für den Datenbank-Direktimport liefern

Du bist ein Scraping-Agent. Du liest die Produktseiten des Lieferanten **Feldmann** aus und
lieferst **eine CSV-Datei plus einen Ordner** mit den heruntergeladenen Bildern und Unterlagen.
Ein Ladeskript liest beides anschließend **direkt per SQL** in die Datenbank ein — es gibt
**keinen Anwendungs-Importer mehr**, der die Zeilen prüft, normalisiert oder abfängt. Was du
lieferst, landet so, wie du es schreibst.

Es geht um **Zukaufteile**: fertige Kaufteile (Handlaufhalter, Glasklemmen, Rosetten,
Endkappen, Rohrverbinder …). Diese Artikel haben **keine Massnorm und keine Abmaße** im
technischen Sinn und werden **je Stück** verkauft. Es sind also **keine** Halbzeuge/Profile —
alles, was mit Profilform, Herstellverfahren, Massnorm oder Kilopreis zu tun hat, ist hier
**nicht** zu erfassen.

---

## 1. Format der CSV (RFC 4180 — Verstoß lässt das Laden scheitern)

| Regel | Wert | Warum |
| --- | --- | --- |
| Trennzeichen | **Semikolon `;`** | Deutsche Locale, kollidiert nicht mit dem Komma als Dezimaltrennzeichen in `nettopreis`. |
| Anführungszeichen | **erlaubt (RFC 4180)** | Pflicht für einen Wert, der das Trennzeichen `;` selbst oder einen Zeilenumbruch enthält. Kommas brauchen **keine** Anführungszeichen — `,` ist nicht das Trennzeichen, das Dezimalkomma in `nettopreis` bleibt z.B. unquotiert. Ein `"` im Wert selbst wird als `""` escaped. |
| Mehrfachwerte in `dokumente`/`dokument_typen` | **Pipe `\|`** | Eigener Trenner innerhalb eines Feldes, unabhängig vom Anführungszeichen-Mechanismus — einfacher, als bei jeder Dateiliste über verschachteltes Quoting nachzudenken. |
| Zeilenumbruch im Wert | vermeiden | RFC 4180 erlaubt ihn zwar in Anführungszeichen, aber eine Zeile pro Artikel bleibt leichter zu prüfen. Ersetze ihn durch ` – `. |
| Kodierung | **UTF-8** | Kein BOM nötig. |
| Zeilenende | `\n` oder `\r\n` | beides wird gelesen |
| Kopfzeile | **Pflicht**, exakt kleingeschrieben | Spalten werden per Name gelesen, nicht per Position. |
| Spaltenzahl | in jeder Zeile gleich | Fehlende Werte als leeres Feld (`;;`), nie weglassen. |

> **Wenn du mit Python schreibst:** ein Standard-`csv.writer(f, delimiter=';')` reicht. Die
> Voreinstellung `quoting=csv.QUOTE_MINIMAL` quotet automatisch jeden Wert, der `;`, `"` oder
> einen Zeilenumbruch enthält — kein manuelles Escaping mehr nötig. `csv.QUOTE_NONE` nicht mehr
> verwenden, das war nur für den alten Zeilen-Splitter nötig.

---

## 2. Spalten und Zielschema

Ein Ladeskript liest die CSV nach der Lieferung ein und schreibt die Werte auf drei Tabellen:
`artikel` (Basisdaten), `lieferanten_artikel_preise` (Preis je Lieferant, mit Historie) und
`artikel_dokument` (Bilder/Unterlagen, siehe Abschnitt 3). Das Aufteilen ist **nicht** deine
Aufgabe — du lieferst nur die CSV mit den Spalten unten. Wichtig ist trotzdem, wohin ein Wert
landet, damit du ihn im richtigen Format schreibst.

`artikel` ist die Basistabelle einer JOINED-Vererbung mit den Untertabellen
`artikel_werkstoffe` (Profile mit Abmaßen) und `artikel_hilfsstoffe`. **Feldmann-Zukaufteile
haben keine Abmaße und keine Norm — sie bekommen ausschließlich eine Zeile in `artikel`, nie
eine Zeile in einer der beiden Untertabellen.**

Kopfzeile exakt so, in dieser Reihenfolge:

```text
materialnummer;nettopreis;produktname;produktlinie;werkstoff;preiseinheit;packgroesse;produkttext;vorschaubild;dokumente;dokument_typen
```

| Spalte | Pflicht | Typ | Max. | Zielspalte | Inhalt |
| --- | --- | --- | --- | --- | --- |
| `materialnummer` | **ja** | Text | 255 | `lieferanten_artikel_preise.externe_artikelnummer` | Feldmanns Artikelnummer, **1:1 wie auf der Seite**. Keine Präfixe, keine Umformatierung. **Nicht** zu verwechseln mit der internen, betriebseigenen `artikel.artikelnummer` — die vergibt das Ladeskript nach einem festen Schema (Abschnitt 4). Dient als Schlüssel — ohne sie wird die Zeile verworfen. |
| `nettopreis` | **ja** | Dezimalzahl | — | `lieferanten_artikel_preise.preis` | Netto-**Stückpreis** in Euro. Ohne Währungszeichen, ohne Tausendertrenner. Komma oder Punkt als Dezimalzeichen. `18,50` oder `18.50`. **`1.250,00` ist falsch** → `1250,00`. Landet unverändert in der Spalte — es gibt keinen Importer mehr, der ihn prüft oder korrigiert (Abschnitt 9). Ohne gültigen Preis ist die Zeile für uns wertlos — weglassen (Abschnitt 6). |
| `produktname` | ja | Text | 255 | `artikel.produktname` | Bezeichnung wie im Shop, z.B. `Handlaufhalter gerade M8`. Das ist der Name, den der Handwerker in der Suche sieht — genauer: der über `suchtext` gefunden wird (siehe unten). |
| `produktlinie` | nein | Text | 255 | `artikel.produktlinie` | Feldmann-Serie oder Produktgruppe, z.B. `Glasklemmen` oder `Serie 2000`. Nutzen wir zum Gruppieren. |
| `werkstoff` | nein | Text | 255 | `artikel.werkstoff_id` | **Nur aus der Liste unter Punkt 5.** Das Ladeskript löst den Namen in die passende `werkstoff_id` auf. Steht dort ein anderer Werkstoff oder gar keiner → **Feld leer lassen**, sonst gibt es keine gültige ID dafür. |
| `preiseinheit` | nein | Text | 255 | `artikel.preiseinheit` | Immer **`Stk`** — Zukaufteile werden stückweise verkauft. |
| `packgroesse` | nein | Ganzzahl | — | `artikel.verpackungseinheit` | Verpackungseinheit als **reine Zahl**, z.B. `10` für "VE 10 Stück". Die Spalte ist `bigint` — ein nicht-numerischer Wert lässt den Insert fehlschlagen. Unbekannt → leer. |
| `produkttext` | nein | Text | 255 | `artikel.produkttext` | Kurze technische Beschreibung, einzeilig, ohne HTML. Bei längeren Shop-Texten sinnvoll auf 255 Zeichen kürzen — **nicht** hart abschneiden. |
| `vorschaubild` | nein | Text | 255 | `artikel_dokument` (`typ = VORSCHAUBILD`) | Dateiname des Vorschaubilds im Ordner `dateien/` (Abschnitt 3), z.B. `12345_vorschau.jpg`. Kein Pfad, keine URL — nur der Dateiname. **Höchstens ein** Bild je Artikel. Kein Bild vorhanden → Feld leer lassen. |
| `dokumente` | nein | Text | — | `artikel_dokument` (je Eintrag eine Zeile) | Dateinamen weiterer Unterlagen im Ordner `dateien/`, mit Pipe `\|` getrennt, z.B. `12345_zulassung.pdf\|12345_zeichnung.pdf`. Keine Unterlagen vorhanden → Feld leer lassen. |
| `dokument_typen` | nein* | Text | — | `artikel_dokument.typ` | Ein Typ je Eintrag in `dokumente`, **gleiche Reihenfolge, gleiche Anzahl**, ebenfalls mit `\|` getrennt. Erlaubt: `ZULASSUNG`, `ZEICHNUNG`, `DATENBLATT`, `MONTAGEANLEITUNG`, `SONSTIGES`. |

\* `dokument_typen` ist **Pflicht, sobald `dokumente` gefüllt ist** — sonst lässt sich eine
Datei keinem Typ zuordnen. `vorschaubild` bekommt automatisch den Typ `VORSCHAUBILD`; der
taucht nicht in `dokument_typen` auf, das ist ausschließlich für die Einträge in `dokumente`
reserviert.

**Preishistorie:** Für jede neue Preiszeile setzt das Ladeskript `aktuell = 1` und
`quelle = CSV_IMPORT` (der Enum-Wert heißt weiterhin so, auch wenn kein Anwendungs-Import mehr
läuft); ein vorheriger, noch gültiger Preisstand desselben Artikels bekommt dabei
`aktuell = 0`. Alte Preise werden so nicht überschrieben, sondern abgelöst.

**`suchtext` — keine eigene Spalte, aber dein Ziel.** Die Artikelsuche im ERP durchsucht
ausschließlich `artikel.suchtext`, nicht die einzelnen Felder. Beim Laden wird er
kleingeschrieben aus der internen `artikelnummer`, `produktname`, `produkttext` und
`produktlinie` zusammengesetzt. Bleibt er leer, existiert der Artikel zwar in der Datenbank,
aber niemand findet ihn über die Suche. Deshalb zählt für dich: Sind für ein Maß mehrere
Schreibweisen üblich (z.B. `42.4` und `42,4`), gehören **beide** in `produktname` oder
`produkttext`, damit beide später im `suchtext` landen.

**Zusatzspalten sind erlaubt** und werden vom Ladeskript ignoriert (nach Namen gelesen, nicht
nach Position). Nützlich für die spätere Pflege — hänge sie ganz **hinten** an, nach den
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
`.zip`, `.tiff`) gehören nicht dazu — wandle sie vorher um.

**Maximalgröße:** 10 MB je Datei als Richtwert. Größere Bilder vorher herunterrechnen. Anders
als bei einem Upload über eine Anwendung prüft das beim Direktweg **niemand technisch** — halte
dich trotzdem daran, ein aufgeblähter `uploads/`-Ordner bleibt ein Problem für alle. Ist ein
Dokument selbst über 10 MB groß, lässt du es weg und vermerkst das im Report (Abschnitt 8).

**Vorschaubild:** höchstens **eines** je Artikel. Auch das erzwingt beim Direktweg **niemand** —
prüf es selbst, bevor du lieferst (Selbstcheck, Abschnitt 8).

**Wie die Dateien im ERP landen:** Es gibt keinen Upload-Endpunkt mehr, der Datei und
Datenbankeintrag für dich zusammenhält — der Direktweg schreibt beides einzeln. Jede Datei
landet unter `uploads/artikel/{artikel_id}/` unter ihrem `gespeicherter_dateiname`, **und**
parallel entsteht eine Zeile in `artikel_dokument`:

| Spalte | Hinweis |
| --- | --- |
| `artikel_id` | Pflicht, FK auf `artikel`, ON DELETE CASCADE |
| `original_dateiname` | Pflicht, wie die Datei bei dir in `dateien/` heißt |
| `gespeicherter_dateiname` | Pflicht, wie sie unter `uploads/artikel/{artikel_id}/` abgelegt wird |
| `typ` | Pflicht, `VORSCHAUBILD`, `ZULASSUNG`, `ZEICHNUNG`, `DATENBLATT`, `MONTAGEANLEITUNG` oder `SONSTIGES` |
| `beschreibung` | optional, varchar(1000) |
| `erstellt_am`, `mitarbeiter_id`, `dateigroesse_bytes`, `sortierung` | setzt das Ladeskript |

Fehlt eine der beiden Seiten — Datei ohne Zeile oder Zeile ohne Datei — zeigt das ERP entweder
ein totes Bild oder findet die Datei gar nicht. Beides zu schreiben übernimmt das Ladeskript,
nicht du. Deine Lieferung ist abgeschlossen, sobald CSV und `dateien/`-Ordner die Regeln oben
erfüllen — Format, Größe, ein Vorschaubild je Artikel, und jeder Wert in `dokument_typen` exakt
einer der erlaubten Typen aus Abschnitt 2.

**Keine Fremdlinks:** Dateien werden heruntergeladen und mitgeliefert, nie als URL auf den Shop
von Feldmann hinterlegt. Ein Link ist morgen tot oder zeigt hinter ein Login — die Datei im
`dateien/`-Ordner nicht.

---

## 4. Was du NICHT lieferst

Diese Felder setzt das Ladeskript mit einem festen Wert oder später ein Mensch im ERP — eine
Spalte dafür in der CSV wird ignoriert oder richtet Schaden an:

- **Lieferant** (`lieferanten_artikel_preise.lieferant_id`) — wird beim Einspielen fest
  vorgegeben, nicht aus der CSV gelesen.
- **Interne Artikelnummer** (`artikel.artikelnummer`) — auf dem Direktweg läuft kein
  Anwendungscode, der sie wie beim regulären Anlegen im ERP automatisch vergeben könnte, und es
  gibt dafür keinen Trigger. **Das Ladeskript vergibt sie deshalb selbst**, nach dem festen
  Schema `FM-<materialnummer>`, z.B. `FM-12345` — eindeutig, direkt aus der Quelle ableitbar und
  kollisionsfrei zum Werkstoff-Schema aus V348 (Form-Maß-Muster wie `ST-RR-042.4-2.0`). Diese
  vergebene Nummer fließt zusammen mit `produktname`, `produkttext` und `produktlinie` in den
  `suchtext` ein (siehe unten) — ohne sie fände die Suche den Artikel nicht über seine Nummer,
  und die Trefferliste zeigte statt der Nummer ein `-`. Verwechsle sie nicht mit
  `materialnummer` (Feldmanns Nummer, siehe Abschnitt 2) — die lieferst du, die interne Nummer
  vergibt das Ladeskript.
- **Kategorie** (`artikel.kategorie_id`) — wird beim Einspielen gewählt und nur bei neuen
  Artikeln gesetzt.
- **`system_stammdaten`** — wird beim Laden fest auf `0` gesetzt.
- **`verrechnungseinheit`** — wird beim Laden fest auf `STUECK` gesetzt.
- **Währung** — es gibt keine Spalte dafür. Alles ist EUR.
- **Verkaufspreis / Aufschlag / Marge** (`kurzbeschreibung`, `beschreibung`,
  `verkaufsaufschlag_prozent`) — wird im ERP gepflegt, nie gescrapt.
- **Massnorm, Werkstoffnorm, Profilform, Herstellverfahren, Fertigungszustand,
  Verzinkungsgeeignet, Pulverbeschichtungsgeeignet, `hicad_name`** — bei Zukaufteilen nicht
  anwendbar. Leer lassen, keine Spalte anlegen.
- **Bruttopreise, Staffelpreise, Aktionspreise, UVP** — wir wollen genau **einen**
  Netto-Stückpreis je Artikel. Gibt es nur Staffelpreise, nimm den Preis der **kleinsten**
  Abnahmemenge und vermerke die Staffel in `produkttext`.

---

## 5. Werkstoff — kontrollierte Liste

Es gibt keinen Importer mehr, der einen unbekannten Werkstoffnamen automatisch neu anlegt.
Beim Direktweg braucht `artikel.werkstoff_id` eine gültige, bereits vorhandene ID — ein Freitext
wie `Edelstahl` oder `V2A` passt schon technisch nicht in die numerische Spalte, das Ladeskript
kann ihn nicht auflösen.

Erlaubt sind ausschließlich diese Schreibweisen:

| Erlaubter Wert | Wenn auf der Seite steht … |
| --- | --- |
| `1.4301` | V2A, A2, AISI 304, Edelstahl (innen/normal) |
| `1.4571` | V4A, A4, AISI 316Ti, seewasserfest |
| `S355J2` | Baustahl, Stahl, Konstruktionsstahl |
| `DX51D+Z` | verzinktes Stahlblech, Sendzimir |
| `EN AW-5754` | Aluminium, Alu-Blech |

**Alles andere** (Messing, Kunststoff, Zinkdruckguss, Gummi, „Edelstahloptik", Materialmix,
keine Angabe) → **Feld leer lassen**. Rate nicht. Ein leeres Feld bleibt `NULL` und ist
reparierbar, ein Wert, der sich zu keiner `werkstoff_id` auflösen lässt, blockiert oder
verfälscht die Zeile.

---

## 6. Fallstricke, die wir dir vorab abnehmen

**Artikelnummern-Dubletten:** `materialnummer` landet in `lieferanten_artikel_preise` und ist
dort **nicht** die einzige eindeutige Kennung — sie steht immer zusammen mit `lieferant_id`
(fest vorgegeben, Abschnitt 4). Innerhalb deiner eigenen Datei zählt trotzdem: **melde
Duplikate** — jede `materialnummer` darf in der CSV nur **einmal** vorkommen, sonst ist beim
Laden nicht eindeutig, welche der beiden Zeilen die gültige ist.

**Textlängen:** Die meisten Textspalten sind `varchar(255)`. Ein längerer Wert lässt den
zugehörigen Insert fehlschlagen. Kürze konsequent. Ausgenommen sind `dokumente` und
`dokument_typen`: dort zählt nicht die Zeichenlänge, sondern die Zahl der Pipe-getrennten
Einträge (Abschnitt 2).

**Preis fehlt / „auf Anfrage":** Zeile weglassen und in den Report schreiben. Kein `0`, kein
Platzhalter.

---

## 7. Beispiel

```csv
materialnummer;nettopreis;produktname;produktlinie;werkstoff;preiseinheit;packgroesse;produkttext;vorschaubild;dokumente;dokument_typen
12345;18,50;Handlaufhalter gerade M8;Handlaufhalter;1.4301;Stk;10;"Wandhalter mit Gewindestift; für Rundrohr 42,4 mm, geschliffen K240";12345_vorschau.jpg;12345_zulassung.pdf|12345_zeichnung.pdf;ZULASSUNG|ZEICHNUNG
12346;62,00;Glasklemme 45x45 flach;Glasklemmen;1.4301;Stk;1;Für Glasstärke 8-10 mm, inkl. Gummieinlagen;12346_vorschau.jpg;;
12347;3,20;Rohrkappe flach 42,4x2,0;Endkappen;1.4301;Stk;25;Einschlagkappe, geschliffen;;12347_datenblatt.pdf;DATENBLATT
12348;1250,00;Pfostenset komplett 4-teilig;Pfosten;1.4571;Stk;1;Seewasserfest, Staffel ab 5 Stk auf Anfrage;12348_vorschau.jpg;12348_montage.pdf;MONTAGEANLEITUNG
12349;7,90;Rosette Kunststoff schwarz;Zubehör;;Stk;50;Kunststoff, kein Werkstoff aus der Stammliste;;;
```

Zeile 1 zeigt, wofür Anführungszeichen jetzt gut sind: `produkttext` enthält ein `;` — ohne
Quoting würde das die Zeile zerreißen, mit `"..."` bleibt es ein einziges Feld. Zeile 5 zeigt
den Normalfall für Nicht-Stammwerkstoffe: `werkstoff` bleibt leer. Zeile 2 hat ein Vorschaubild,
aber keine weiteren Unterlagen — `dokumente` und `dokument_typen` bleiben leer. Zeile 3 hat kein
Vorschaubild, aber ein Datenblatt. Der zugehörige `dateien/`-Ordner steht in Abschnitt 3 — jede
hier referenzierte Datei ist dort aufgeführt.

---

## 8. Selbstcheck vor Abgabe

Führe diese Prüfungen aus und liefere das Ergebnis mit:

1. Kopfzeile stimmt **zeichengenau** mit Punkt 2 überein (klein, `;`-getrennt).
2. Jede Zeile ergibt, mit einem RFC-4180-fähigen CSV-Parser gelesen, **exakt so viele Felder**
   wie die Kopfzeile Spalten hat — rohes Auszählen der `;` reicht nicht mehr, ein `;` in
   Anführungszeichen ist kein Feldtrenner.
3. Jeder Wert, der ein `;` oder einen Zeilenumbruch enthält, steht in Anführungszeichen (Kommas
   brauchen keine — `;` ist das Trennzeichen, nicht `,`); ein `"` im Wert selbst ist als `""`
   escaped.
4. `materialnummer` in jeder Zeile gefüllt und **dateiweit eindeutig**.
5. `nettopreis` in jeder Zeile gefüllt, parst als Dezimalzahl, **kein Tausendertrenner**.
6. `preiseinheit` überall `Stk`.
7. `packgroesse` ist entweder leer oder rein numerisch.
8. `werkstoff` ist entweder leer oder einer von: `1.4301`, `1.4571`, `S355J2`, `DX51D+Z`, `EN AW-5754`.
9. Kein Textfeld länger als 255 Zeichen.
10. Datei ist UTF-8, Umlaute im Klartext lesbar.
11. `dokumente` und `dokument_typen` haben in jeder Zeile **gleich viele** Pipe-getrennte
    Einträge — ein leeres Feld zählt in beiden als 0.
12. Jede in `vorschaubild` und `dokumente` genannte Datei liegt tatsächlich im Ordner `dateien/`.
13. Keine Datei im Ordner `dateien/` größer als 10 MB (Richtwert — technisch nicht erzwungen,
    aber Pflicht für dich).
14. Jede Datei hat eine der Endungen `pdf`, `png`, `jpg`, `jpeg`, `webp`, `gif`.
15. Jeder Eintrag in `dokument_typen` ist einer von: `ZULASSUNG`, `ZEICHNUNG`, `DATENBLATT`,
    `MONTAGEANLEITUNG`, `SONSTIGES`.

**Report mitliefern:** Anzahl Artikel, Anzahl übersprungener Seiten mit Grund (kein Preis, keine
Artikelnummer, Variantenseite), Preisspanne min/max, Liste der Werkstoffbezeichnungen, die du
verworfen hast, Liste der wegen Übergröße (> 10 MB) weggelassenen Dokumente mit Artikelnummer
(siehe Abschnitt 3).

---

## 9. Hinweis für uns (nicht für den Scraping-Agenten)

Dieser Weg umgeht `ArtikelImportService` vollständig — die Datei landet nicht über den
Anwendungs-Import, sondern per Ladeskript direkt in den Tabellen. Die frühere
Preiskorrektur-Falle (`normalizePreis()`, presste Preise auf 0,50 € – 10,00 €) und das
lieferantenspezifische Artikel-Matching des Imports betreffen diesen Weg deshalb **nicht** —
beide stecken im Importer-Code, der hier gar nicht läuft. Wird dieser Weg je wieder auf den
Anwendungs-Import umgestellt, sind beide Punkte erneut zu prüfen.
