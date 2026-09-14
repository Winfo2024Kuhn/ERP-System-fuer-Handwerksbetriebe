# Brainstorming-Ergebnis: Kasse & Belege — Recherche und Design

Status: Design am 09.09.2026 vom Nutzer freigegeben ("passt"), inklusive der sechs Annahmen und des Refactorings aus C9.
Pfad: architectural (loese-problem-Pipeline, Schritt 0).

## Problem (so hat es der Nutzer beschrieben)

1. Die Kasse kann nur vier Shortcuts (Bank→Kasse, Privateinlage, Privatentnahme,
   Ehegattengehalt). Wer "die Kasse macht", kann keine echte Einnahme oder Ausgabe
   buchen. Das Ergebnis soll aber als Ausgabe für den Steuerberater taugen.
2. Es soll extrem einfach sein — für Leute ohne Buchhaltungswissen. Ein bisschen
   einlesen ist okay, aber alles muss rechtens bleiben.
3. Beleg-Scan: Der am Handy gewählte Lieferant wird nach der KI-Erkennung nicht
   aktualisiert.
4. Beim Prüfen am PC: Zahlungsart "Rechnung" angegeben, der Beleg landet trotzdem
   in den offenen Posten. Man will "bereits gezahlt" oder "in offene Posten" wählen.
5. Kosten zuweisen (Kostenstelle): es kommen keine Vorschläge.
6. Das Konto-Feld ist im Dialog abgedeckt, im Dropdown sind die Einträge länger
   als das Dropdown und werden abgeschnitten.
7. Nachtrag: Bei "Wo gezahlt" fehlt die Beschreibung, die die KI einfügen soll,
   und die KI schlägt nie ein Konto vor.

---

## Teil A — Recherche: Was rechtlich gilt und was der Steuerberater braucht

### A1. Kassenbuch: Wer muss, und was drin stehen muss

- Ein **Kassenbuch** ist Pflicht für Bilanzierer (§ 146 AO, § 238 HGB). Wer die
  EÜR macht, muss kein Kassenbuch führen — aber jede Bareinnahme trotzdem
  **einzeln und täglich** aufzeichnen. Praktisch heißt das: dieselben Daten,
  nur ohne den Namen "Kassenbuch". Wir bauen also eine Lösung, die für beide
  passt.
- Pflichtinhalt je Bewegung: **laufende Nummer, Datum, Belegnummer,
  Buchungstext, Betrag (Einnahme oder Ausgabe), Steuersatz, Bestand danach**.
  Je Blatt/Monat: Firma, Zeitraum, **Anfangs- und Endbestand**.
- Regeln: **täglich** erfassen (keine Sammelbuchung, keine Schätzung),
  **Bestand nie negativ**, jederzeit **kassensturzfähig** (Zählung muss zum
  Buchwert passen), **unveränderbar** — Korrektur nur durch sichtbaren Storno,
  Monat wird **festgeschrieben**. Ein Excel-Kassenbuch gilt deshalb als nicht
  GoBD-konform.
- Privateinlagen, Privatentnahmen und Bank↔Kasse-Transfers sind Barbewegungen
  und gehören ins Kassenbuch. Bar-Geschäftsvorfälle über das Privatkonto zu
  buchen (statt über die Kasse) ist unzulässig.

### A2. TSE, Kassengesetz, Meldepflicht — gilt das für uns?

**Nein.** § 146a AO (TSE-Pflicht, Kassenmeldung ab 2025, Belegausgabepflicht)
gilt für **elektronische Aufzeichnungssysteme mit Kassenfunktion** —
Registrierkassen, POS-Systeme. Ein **elektronisches Kassenbuch** ist
Buchführungssoftware, kein Kassensystem: Es zeichnet nicht den Verkauf auf,
sondern die Bewegung des Bargelds. Wer eine **offene Ladenkasse** (Geldkassette)
plus Kassenbuch mit Einzelaufzeichnung führt, braucht keine TSE und muss nichts
melden. Genau dieses Modell bauen wir.

Konsequenz für das Design: Das Programm darf nie wie eine Registrierkasse
auftreten (kein "Verkauf abschließen", kein Kassenbon als Verkaufsbeleg). Die
Quittung, die wir bei einer Bareinnahme erzeugen, ist der **Beleg zur Buchung**
(§ 33 UStDV Kleinbetragsrechnung bzw. § 14 UStG) — nicht ein Bon im Sinne von
§ 146a AO. Das schreiben wir so in die Verfahrensdokumentation.

Das steht heute anders in `docs/GOBD_COMPLIANCE.md` Abschnitt 0.2 ("Barkasse
mit TSE → separates Kassenprogramm"). Der Text ist überholt, seit V302–V351 das
Kassenbuch im System liegt, und wird im Zuge dieses Vorhabens korrigiert.

### A3. Belege: Was ein Beleg braucht

| Belegart | Wann | Pflichtangaben | Vorsteuer? |
|---|---|---|---|
| **Fremdbeleg** (Bon, Rechnung) ≤ 250 € brutto | Kauf beim Händler | Aussteller, Datum, Menge/Art, Bruttobetrag, Steuersatz (§ 33 UStDV) | ja |
| **Fremdbeleg** > 250 € | Rechnung | volle Rechnungsangaben (§ 14 UStG), u.a. Empfänger, Rechnungsnummer, Netto/USt getrennt | ja |
| **Quittung an Kunden** (Bareinnahme) | Kunde zahlt bar | wie Kleinbetragsrechnung; über 250 € wie Rechnung (dann besser die Ausgangsrechnung bar quittieren) | — (Umsatz) |
| **Eigenbeleg** | kein Fremdbeleg vorhanden (Parkautomat, Trinkgeld, verloren) | Datum, Betrag, Zweck, Empfänger, Zahlungsart, Ersteller/Unterschrift, Grund | **nein** (Betriebsausgabe ja, Vorsteuer nie) |

Ersetzendes Scannen (Handy-Foto statt Papier): vollständig, lesbar,
**unveränderbar** (wir haben den SHA-256-Hash am Beleg), **zeitnah**, plus eine
Verfahrensdokumentation. Der Endpoint `/kassenbuch/verfahrensdokumentation`
existiert und wird um die Kassen-Regeln aus A1/A2 ergänzt.

### A4. Was der Steuerberater monatlich braucht

1. **Kassenbuch** als PDF (Journal mit laufender Nummer, Anfangs-/Endbestand,
   Kassenstürze). — Gibt es (`BelegeKasseExportPdfService`).
2. **Buchungen als DATEV-Datei** (Buchungsstapel, EXTF-CSV) — damit er nichts
   abtippt. Das ist der Teil, der die Steuerberater-Kosten senkt. — **Fehlt.**
3. **Belegbilder** (PDF/JPG), benannt nach laufender Nummer, damit Bild und
   Buchung zusammenfinden. — Nur im Kassenbuch-PDF eingebettet, nicht als Dateien.
4. **Liste der Eingangsrechnungen** mit Zahlstatus (bezahlt wann/womit, offen).
   — Gibt es als HTML-Mail (`SteuerberaterBelegExportModal`), aber ohne Zahlstatus.
5. Ausgangsrechnungen — gibt es (Z3-Export). Bankumsätze holt er sich selbst.

### A5. DATEV-Buchungsstapel (EXTF, Version 700) — was wir füllen

Kopfzeile (Auszug, Positionen 1–22):
`"EXTF";700;21;"Buchungsstapel";13;<erzeugt am YYYYMMDDHHMMSSfff>;;"HW";"<Ersteller>";;<Beraternummer>;<Mandantennummer>;<WJ-Beginn YYYYMMDD>;4;<Datum von>;<Datum bis>;"Kasse <Monat>";;1;;<Festschreibung 0/1>;"EUR"`

Spaltenzeile: die vollständige Liste aus dem DATEV-Format (125 Spalten,
Referenz: ledermann/datev-Beispieldatei). Wir befüllen je Buchung:

| Spalte | Wert bei uns |
|---|---|
| Umsatz (ohne Soll/Haben-Kz) | Bruttobetrag, Komma als Dezimaltrenner |
| Soll/Haben-Kennzeichen | S = Geld geht raus (Aufwand), H = Geld kommt rein |
| WKZ Umsatz | EUR |
| Konto | Sachkonto-Nummer (Aufwand/Ertrag/Privat) |
| Gegenkonto | 1000 Kasse; bei Bank-Transfer 1200 |
| BU-Schlüssel | 9 = 19 % Vorsteuer, 8 = 7 % Vorsteuer, 3 = 19 % Umsatzsteuer, 2 = 7 % USt, leer bei 0 %/Privat/Transfer |
| Belegdatum | TTMM |
| Belegfeld 1 | laufende Kassenbuch-Nummer |
| Buchungstext | Beschreibung, max. 60 Zeichen |
| KOST1 – Kostenstelle | Kostenstellen-Nummer (bei Split: eine Zeile je Anteil) |
| Festschreibung | 1 wenn der Monat abgeschlossen ist |

Kontenrahmen: Die vorhandenen Sachkonten (V303/V307) sind SKR03-Nummern.
Kasse = 1000, Bank = 1200 sind Standard, werden aber einstellbar gemacht.
Nötige neue Einstellungen: Beraternummer, Mandantennummer, Beginn des
Wirtschaftsjahres (Monat), Kassenkonto, Bankkonto.

Quellen (Auswahl): fuer-gruender.de/gobd, onlinebilanz.de/kassenbuch-fuehren-pflicht,
haufe.de (EÜR ohne Kassenbuch), taxandbytes.de (§ 146a und elektronisches
Kassenbuch), lexware.de (Eigenbeleg, ersetzendes Scannen), fuer-gruender.de
(Kleinbetragsrechnung), auditplan.io + ledermann/datev (EXTF-Format),
epago.de (BU-Schlüssel).

---

## Teil B — Ist-Zustand im Code (Kurzfassung mit Fundstellen)

**Schon da und brauchbar:**

- `Beleg` mit Kategorie, Sachkonto, MwSt, Zahlungsart (Freitext!), Kostenstellen-Splits,
  Hash der Datei, Audit-Hash-Kette, laufende Nummer beim Monatsabschluss,
  Festschreibung, Storno, Kassenzählung (Stückelung), Mindestbestand
  (`KasseSaldoService`), Verfahrensdokumentation-Endpoint. (V302–V351)
- Kassenbuch-PDF als Journal mit Belegbildern (`BelegeKasseExportPdfService`).
- Belegfreie Buchung `POST /api/buchhaltung/umbuchungen` (`BelegService.createUmbuchung:707`)
  — sofort validiert, nur Kassen-/Bank-/Privat-Kategorien.
- KI-Analyse liest Nummer, Datum, Beträge, MwSt, Zahlungsart, Dokumenttyp; ein
  Agent (`BelegKiKostenkontoService`) schlägt Kostenstelle + Sachkonto vor.

**Lücken, die die Nutzerpunkte erklären:**

| Nutzerpunkt | Ursache im Code |
|---|---|
| Nur Shortcuts | Die Shortcut-Dialoge (`KasseShortcuts.tsx`) haben kein Sachkonto-Feld; eine Bareinnahme mit Quittung oder eine Barausgabe ohne Foto gibt es nicht. Kasse→Bank fehlt ganz. Die Kassenansicht ist ein T-Konto (`KassenbuchView:837`) — Buchhalter-Optik, nicht Handwerker-Optik. |
| Lieferant vom Handy | `BelegKiAnalyseService.java:137-144`: KI setzt den Lieferanten nur, wenn keiner da ist (richtig), überschreibt aber danach `kiVorgeschlagenerLieferant` mit dem **Nutzerwert** statt mit der KI-Lesung. Der PC blendet den Vorschlag bei gesetztem Lieferanten aus (`BelegeKasseEditor.tsx:1436`). Die KI-Lesung geht spurlos verloren. Zusätzlich pollt `BelegScannerPage.tsx:112-135` nicht — die Zeile bleibt auf "KI liest Beleg…". |
| "Rechnung" → offener Posten | Bei `dokumentTyp ∈ {RECHNUNG, GUTSCHRIFT}` + Lieferant legt die KI **sofort** eine Eingangsrechnung an (`BelegKiAnalyseService.java:336-405`). `bereitsGezahlt` kommt nur aus der KI oder aus Lieferant-Vorauskasse. Am Beleg gibt es **kein** Feld "bezahlt"; `UpdateRequest` kennt es nicht (`BelegService.updateBeleg:307-468`). Der Dialog zeigt die verknüpfte Eingangsrechnung nicht einmal an. |
| Keine Kostenstellen-Vorschläge | `BelegKiKostenkontoService.klassifiziereBeleg:101-171` bricht **still** ab: Kostenstelle schon gesetzt, kein Gemini-Key, leere Antwort, keine `finale_zuordnung` nach 6 Runden — in allen Fällen bleibt alles null, ohne Hinweis. Der Prompt enthält **kein Belegbild**, nur Textfelder. Auto-Übernahme erst ab 0,95 Confidence. Kostenstellen-Vorschlag ist nur Text, nicht übernehmbar (`KiVorschlagKarte:1993-1997`). |
| Konto-Dropdown | Eigenbau `select-custom.tsx`: Breite hart = Trigger-Breite (`:29-37`), Optionen `truncate` ohne Tooltip (`:89`), Position nur beim Öffnen berechnet (kein Scroll-/Resize-Listener, kein Hochklappen). Das Formular hat nur 1 von 3 Spalten (`BelegeKasseEditor.tsx:1458-1465`), das Konto-Feld darin nochmal die Hälfte — rund ein Sechstel der Dialogbreite für Labels wie "Aufwand · 4930 Bürobedarf und Zeitschriften". |
| "Wo gezahlt" ohne KI-Text | "Wo gezahlt" ist `belegKategorie`, von der KI aus der Zahlungsart abgeleitet (`:146-157`) — es gibt **kein** KI-Vorschlagsfeld für Zahlungsart/Kategorie, also nichts, was das UI zeigen könnte. Die Zahlungsart selbst ist hinter "Mehr Details" versteckt. Außerdem schreibt die KI Codes (`UEBERWEISUNG`, `BAR`) in `beleg.zahlungsart`, die Stammdaten (V308) heißen aber `Überweisung`, `Bar` — das Dropdown findet den Wert nicht. |
| KI schlägt nie ein Konto vor | siehe Kostenstellen-Zeile; zusätzlich: Karte rendert nur mit gesetzter `kiVorgeschlagenerSachkontoId` (`:1948`), Sachkonto wird nie automatisch vorbelegt (nur serverseitig ≥ 0,95). Kein Test deckt `klassifiziereBeleg` ab. |
| Steuerberater-Ausgabe | Kein DATEV-Export, keine Belegbilder als Dateien, kein Zahlstatus in der Liste. |

Technische Schulden, die die Arbeit behindern: `BelegeKasseEditor.tsx` hat
2137 Zeilen mit 13 Unterkomponenten; alle Beleg-Typen sind dort lokal
dupliziert statt in `types.ts`; `BuchungssatzAbleitung` wird nirgends
aufgerufen (nur im Test); Kommentar "0.80" in `BelegKiAnalyseService:184` ist
falsch (Schwelle ist 0,95).

---

## Teil C — Design

Leitidee: **Eine Frage nach der anderen, in Handwerker-Sprache, und jede
Buchung erzeugt automatisch das, was der Steuerberater braucht.** Die
Rechtssicherheit (Nummer, Hash, Festschreibung, Storno) ist schon da — wir
bauen sie nicht um, wir machen sie benutzbar.

### C1. Kassenbuch: Journal statt T-Konto, plus "Neue Buchung"

Der Tab "Kassenbuch" zeigt eine **Journal-Tabelle**: Nr. · Datum · Was ·
Beleg · Einnahme · Ausgabe · Bestand danach. Oben der aktuelle Bestand als
große Zahl, daneben "Kasse zählen" und "Monat abschließen" (beides gibt es
schon, `KassenbuchAbschlussLeiste`). Das T-Konto entfällt in der Oberfläche
(das PDF ist ohnehin schon ein Journal).

Ein Button **"Neue Buchung"** öffnet eine Auswahl aus sechs Kacheln:

| Kachel | Kategorie | Pflichtfelder | Beleg |
|---|---|---|---|
| **Geld eingenommen** (Kunde hat bar bezahlt) | KASSE_EINNAHME | Betrag, Datum, Von wem, Wofür (Ertragskonto, Standard "Erlöse 19 %"), MwSt 19/7/0, optional "Zu welcher Rechnung?" (offene Ausgangsrechnung) | System erzeugt eine **Quittung als PDF** und hängt sie als Belegdatei an. Ist eine Rechnung gewählt, wird sie als bezahlt markiert (bestehender Mechanismus `ProjektGeschaeftsdokument.bezahlt`). |
| **Geld ausgegeben** (bar bezahlt) | KASSE_AUSGABE | Betrag, Datum, An wen, Wofür (Aufwandskonto), MwSt, optional Baustelle/Bereich | Entweder Foto/PDF anhängen (dann normaler Beleg, sofort geprüft) oder "Kein Beleg vorhanden" → System erzeugt einen **Eigenbeleg als PDF**, MwSt wird auf 0 % festgesetzt mit dem Hinweis "Ohne Fremdbeleg gibt es keine Vorsteuer". |
| **Geld von der Bank geholt** | KASSE_EINNAHME, Gegenkonto Bank | Betrag, Datum | Eigenbeleg (wie heute `bankAbhebung`) |
| **Geld zur Bank gebracht** *(neu)* | KASSE_AUSGABE, Gegenkonto Bank | Betrag, Datum | Eigenbeleg |
| **Eigenes Geld eingelegt** | PRIVATEINLAGE | Betrag, Datum | Eigenbeleg (wie heute) |
| **Geld privat entnommen** | PRIVATENTNAHME | Betrag, Datum | Eigenbeleg (wie heute) |

Alle sechs laufen über einen gemeinsamen Backend-Pfad
(`BelegService.createUmbuchung` wird zu `createKassenbuchung` erweitert:
Sachkonto, MwSt, Empfänger/Zahler, Quelle, optionale Rechnung). Der Beleg wird
sofort `VALIDIERT`, Mindestbestand wird wie heute geprüft (409 mit
Privateinlage-Vorschlag). Das erzeugte PDF (Quittung/Eigenbeleg) landet als
Belegdatei — damit bleibt die Invariante "jeder Beleg hat eine Datei" erhalten
und der Steuerberater bekommt zu jeder Zeile ein Bild. Der Ehegattengehalt-
Shortcut bleibt unverändert unter "Einstellungen".

Neue Beleg-Spalten: `quelle` (SCAN | QUITTUNG | EIGENBELEG | TRANSFER),
`gegenpartei` (Von wem / An wen, 120 Zeichen), `ausgangsrechnung_id`
(nullable, FK auf `projekt_geschaeftsdokument`).

### C2. Beleg prüfen: fünf Fragen in fester Reihenfolge

Der Dialog "Beleg prüfen" wird neu geordnet. Das Formular bekommt **zwei von
drei Spalten**, die Vorschau eine (heute umgekehrt). Reihenfolge:

1. **Betrag und Datum** — von der KI vorbelegt, mit Badge "von KI gelesen".
2. **Wie wurde bezahlt?** — ein Select mit den Zahlungsarten aus den Stammdaten
   (Bar, EC-Karte, Überweisung, Lastschrift, Kreditkarte, PayPal, Rechnung).
   Darunter erscheint automatisch die Folge in einem Satz:
   "Bar → landet im Kassenbuch" / "Überweisung → läuft über die Bank, nicht
   über die Kasse". Die Kategorie (`belegKategorie`) wird daraus abgeleitet
   und nicht mehr separat abgefragt. "Wo gezahlt" verschwindet als Feld.
   Badge "von KI erkannt", wenn der Wert von der KI stammt.
3. **Ist die Rechnung schon bezahlt?** — nur sichtbar, wenn Zahlungsart nicht
   Bar/EC und Dokumenttyp Rechnung/Gutschrift. Zwei Optionen: "Ja, bezahlt am
   … (Datum)" / "Nein, in die offenen Posten". Bar und EC-Karte gelten
   automatisch als bezahlt. Der Dialog zeigt den Link zur verknüpften
   Eingangsrechnung.
4. **Wofür war das?** — Sachkonto, gruppiert nach Aufwand/Ertrag/Privat,
   Label "4930 Bürobedarf" (Typ als Gruppenüberschrift statt Präfix). Darüber
   ein **Vorschlags-Chip**: "KI schlägt vor: 4930 Bürobedarf (sicher) —
   Übernehmen" oder "Beim letzten Mal bei diesem Lieferanten: 3400 Material
   — Übernehmen" oder ehrlich "Kein Vorschlag: <Grund>".
5. **Für welche Baustelle / welchen Bereich?** — eine Kostenstelle als
   einfaches Select (schreibt intern einen 100 %-Split), mit Vorschlags-Chip
   (KI, Lieferanten-Standardkostenstelle, Historie). "Auf mehrere aufteilen"
   öffnet den bestehenden Split-Editor.
6. **Lieferant** — mit Abweichungs-Hinweis: "Am Handy gewählt: OBI. KI hat
   gelesen: Hornbach. Übernehmen?" (siehe C4).

Alles Weitere (Beleg-Nr., Netto, Notiz) bleibt unter "Mehr Details".
Jedes Feld hat einen Hilfetext in einem Satz, Handwerker-Sprache, kein
Buchhalter-Wort ohne Erklärung.

### C3. Zahlungsstatus und offene Posten (Backend)

- `BelegDto.UpdateRequest` bekommt `zahlungsstatus` (BEZAHLT | OFFEN) und
  `bezahltAm`. `BelegService.updateBeleg` schreibt das auf die verknüpfte
  `LieferantGeschaeftsdokument` (`bezahlt`, `bezahltAm`) — die Entscheidung
  im Prüfen-Dialog ist maßgeblich und überschreibt KI/Vorauskasse.
- Beim Anlegen der Eingangsrechnung durch die KI wird `bereitsGezahlt` aus der
  Zahlungsart abgeleitet (Bar, EC-Karte, Kreditkarte, PayPal → bezahlt), damit
  eine bar bezahlte Rechnung nicht mehr in den offenen Posten auftaucht.
- Das Beleg-DTO liefert `eingangsrechnungBezahlt`, `eingangsrechnungBezahltAm`
  mit, damit der Dialog den Status zeigen kann.
- `beleg.zahlungsart` wird vereinheitlicht: die KI-Codes werden beim Speichern
  auf die Stammdaten-Bezeichnung gemappt (`UEBERWEISUNG` → `Überweisung`).
  Ein Backfill-Skript in der Migration korrigiert Altbestand.

### C4. KI-Vorschläge: sichtbar, ehrlich, übernehmbar

- **Provenienz speichern:** neue Spalten `ki_zahlungsart`, `ki_belegdatum`,
  `ki_betrag_brutto` (Kopie der KI-Lesung). Das UI zeigt "von KI gelesen",
  solange der Wert damit übereinstimmt. `kiVorgeschlagenerLieferant` erhält
  ab jetzt **immer** die KI-Lesung (Fix `BelegKiAnalyseService:143`).
- **Grund statt Schweigen:** `ki_kostenkonto_hinweis` (255 Zeichen) hält fest,
  warum kein Konto-Vorschlag kam: "Kein KI-Schlüssel hinterlegt", "Zu wenig
  Text auf dem Beleg", "Kostenstelle war schon gesetzt", "KI unsicher (0,4)".
  Der Dialog zeigt den Satz.
- **Belegbild an den Agenten:** `klassifiziereBeleg` bekommt das Belegbild
  (bzw. die erste PDF-Seite) und die extrahierten Positionen mit, nicht nur
  Textfelder. Der veraltete Kommentar "0.80" wird korrigiert.
- **Historie als Fallback ohne KI:** neuer `BelegVorschlagService`: für einen
  Lieferanten das häufigste Sachkonto und die häufigste Kostenstelle der
  letzten 20 validierten Belege; dazu die Standardkostenstelle des
  Lieferanten. Läuft immer, auch ohne Gemini-Key. Ergebnis im DTO als
  `vorschlagSachkonto` / `vorschlagKostenstelle` mit `quelle` (KI | HISTORIE |
  LIEFERANT_STANDARD) und `begruendung`.
- **Übernehmen-Buttons** für Sachkonto **und** Kostenstelle in der
  Vorschlags-Karte.
- Tests für `klassifiziereBeleg` selbst (die vier Abbruchpfade) — die fehlen
  heute komplett.

### C5. Mobile: Ergebnis kommt an

- `BelegScannerPage` pollt alle 3 s, solange ein eigener Beleg in
  `PENDING`/`LAEUFT` ist, und hört auf, wenn alle fertig sind.
- Die Zeile zeigt nach der Analyse Betrag, Datum und — bei Abweichung — "KI hat
  <Name> gelesen" unter dem gewählten Lieferanten. Kein Bearbeiten am Handy;
  die Korrektur bleibt am PC (bewusst: Schnellscan-Prinzip bleibt).

### C6. Dropdown und Layout

`select-custom.tsx` wird repariert, nicht ersetzt (wird an sieben Stellen
benutzt):

- Breite = max(Trigger, Inhalt), gedeckelt auf `min(90vw, 480px)`; Optionen
  brechen um statt abzuschneiden, plus `title`.
- Position wird bei Scroll und Resize neu berechnet; klappt nach oben, wenn
  unten kein Platz ist.
- Optionale Gruppen (`optgroup`-Äquivalent) für die Sachkonto-Typen.
- Playwright-Zusicherung: für jede Option `scrollWidth <= clientWidth`, und
  das Dropdown liegt vollständig im Viewport.

Dialog: Formular `lg:col-span-2`, Vorschau `lg:col-span-1`; Konto- und
Kostenstellen-Felder in voller Formularbreite.

### C7. Export für den Steuerberater

Ein Dialog "Für den Steuerberater" (Monat wählen) erzeugt **eine ZIP-Datei**:

```
2026-08_Kasse_Musterbetrieb/
  01_Kassenbuch_2026-08.pdf              (gibt es schon)
  02_Buchungen_DATEV_2026-08.csv         (EXTF 700, alle Kassenbuchungen des Monats)
  03_Eingangsrechnungen_2026-08.csv      (alle validierten Belege: Nr, Datum, Lieferant, Netto, MwSt, Brutto, Konto, Kostenstelle, Zahlungsart, bezahlt am / offen)
  04_Belege/
     0001_2026-08-02_OBI.pdf             (laufende Nummer + Datum + Lieferant)
     ...
  LIESMICH.txt                           (was drin ist, Kontenrahmen, Kassenkonto, Hinweis auf Verfahrensdokumentation)
```

- Neue Einstellungen unter Buchhaltung → Einstellungen: Beraternummer,
  Mandantennummer, Wirtschaftsjahr-Beginn (Monat), Kassenkonto (Standard 1000),
  Bankkonto (Standard 1200). Fehlen Berater-/Mandantennummer, wird trotzdem
  exportiert (DATEV erlaubt leere Felder), mit Hinweis.
- **Vorprüfung vor dem Export:** Belege ohne Sachkonto oder ohne Zahlungsart
  werden aufgelistet ("3 Belege sind noch nicht fertig geprüft") — Export erst,
  wenn die Liste leer ist oder der Nutzer "trotzdem" wählt (dann Konto leer,
  Buchungstext mit Präfix "PRÜFEN:").
- Der bestehende E-Mail-Versand bekommt die ZIP als Anhang statt der
  HTML-Tabelle; die Mail bleibt kurz.
- Bewusst **nicht** im DATEV-Stapel: Bank- und Kreditkartenbuchungen. Das
  System kennt keine Bankumsätze; der Steuerberater bucht sie vom Kontoauszug
  und findet Beleg und Zahlstatus in der CSV 03. Das steht so in der LIESMICH.

Der neue `DatevExportService` ist eine reine Funktion (Belege → CSV-Text),
getestet gegen eine Golden-File-Datei; `SteuerberaterExportService` baut die
ZIP. `BuchungssatzAbleitung` wird dafür endlich benutzt (Soll/Haben je
Kategorie) statt still im Test zu liegen.

### C8. Einlesen: "So funktioniert die Kasse"

Ein einklappbarer Kasten oben im Kassenbuch-Tab (Standard: offen, bis der
Nutzer ihn schließt; Zustand im `localStorage`), fünf Sätze:

1. Jede Barbewegung sofort eintragen — auch Bank-Abhebung und eigenes Geld.
2. Zu jeder Zeile gehört ein Beleg. Fehlt einer, macht das Programm einen
   Eigenbeleg (ohne Vorsteuer).
3. Die Kasse darf nie unter null. Vorher eigenes Geld einlegen.
4. Einmal im Monat: Kasse zählen, dann den Monat abschließen. Danach ist er fest.
5. Falsch gebucht? Nicht löschen — stornieren. Das Original bleibt sichtbar.

Dazu der Link "Mehr dazu" auf eine kurze Hilfeseite (Markdown in `docs/`,
verlinkt aus der Oberfläche).

### C9. Refactoring — braucht ausdrückliche Freigabe

Damit die Tasks parallel laufen können und die Datei wieder lesbar wird:

- `BelegDetailModal` (640 Zeilen) → `components/kasse/BelegDetailModal.tsx`
- `KassenbuchView` + `TKontoZeile` → ersetzt durch `components/kasse/KassenbuchJournal.tsx`
- `KiVorschlagKarte` → `components/kasse/VorschlagsChip.tsx`
- Beleg-Typen (`Beleg`, `Sachkonto`, `Zahlungsart`, `BelegKategorie`, …) →
  `src/types.ts` (ein Ort, wie beim Rest des Frontends)
- Die vier Shortcut-Modals in `KasseShortcuts.tsx` gehen in
  `components/kasse/NeueBuchungDialog.tsx` auf.

Reine Verschiebung zuerst (eigener Task, Verhalten unverändert, Tests grün),
danach die fachlichen Änderungen. Backend: `BelegService` (1232 Zeilen) bleibt,
neue Logik kommt in eigene Services (`KassenbuchungService`,
`BelegVorschlagService`, `DatevExportService`, `SteuerberaterExportService`,
`EigenbelegPdfService`).

### C10. Datenmodell und Migrationen

Eine Migration `V<nächste>__kasse_buchungen_und_export.sql`:

- `beleg`: `quelle VARCHAR(20) NOT NULL DEFAULT 'SCAN'`, `gegenpartei VARCHAR(120)`,
  `ausgangsrechnung_id BIGINT NULL` (FK), `ki_zahlungsart VARCHAR(40)`,
  `ki_belegdatum DATE`, `ki_betrag_brutto DECIMAL(15,2)`,
  `ki_kostenkonto_hinweis VARCHAR(255)`.
- `kasse_einstellung`: `datev_beraternummer VARCHAR(7)`,
  `datev_mandantennummer VARCHAR(5)`, `wirtschaftsjahr_beginn_monat TINYINT DEFAULT 1`,
  `kassenkonto_nummer VARCHAR(8) DEFAULT '1000'`, `bankkonto_nummer VARCHAR(8) DEFAULT '1200'`.
- Backfill: `beleg.zahlungsart` Codes → Stammdaten-Bezeichnung;
  `quelle = 'TRANSFER'` für bestehende `ist_umbuchung = TRUE`.
- Alles idempotent über INFORMATION_SCHEMA, wie in V351.

Bestehende Endpunkte bleiben kompatibel; `POST /umbuchungen` bleibt als Alias
auf den neuen Pfad `POST /kassenbuch/buchungen`.

### C11. Wording (Handwerker-Sprache, wird konsequent benutzt)

| Buchhalter-Wort | In der Oberfläche |
|---|---|
| Sachkonto | Wofür? (Konto) |
| Kostenstelle | Baustelle / Bereich |
| Belegkategorie | (entfällt; ergibt sich aus "Wie bezahlt?") |
| Privateinlage / Privatentnahme | Eigenes Geld eingelegt / Geld privat entnommen |
| Festschreibung | Monat abschließen |
| Kassensturz | Kasse zählen |
| Buchungsstapel | Buchungen für den Steuerberater (DATEV-Datei) |
| Eigenbeleg | Ersatzbeleg (das Programm erstellt ihn) |
| Offener Posten | Noch nicht bezahlt |

### C12. Tests

- Backend: `DatevExportServiceTest` (Golden File, S/H-Regeln, BU-Schlüssel,
  TTMM, Umlaute/Anführungszeichen), `KassenbuchungServiceTest` (sechs
  Kacheln, Mindestbestand, Rechnung wird bezahlt markiert, PDF-Datei
  existiert und hat Hash), `BelegVorschlagServiceTest` (Historie,
  Lieferanten-Standard, Reihenfolge KI > Standard > Historie),
  `BelegKiKostenkontoServiceTest` für die vier Abbruchpfade,
  `BelegServiceZahlungsstatusTest` (bezahlt/offen schreibt auf die
  Eingangsrechnung), Migrationstest auf die neuen Spalten.
- Frontend (vitest): Journal-Saldo, Ableitung Zahlungsart → Satz, Vorschlags-Chip.
- Playwright (je Task eine Spec, gestubbte API): Neue Buchung (alle sechs
  Kacheln), Prüfen-Dialog in Reihenfolge, Dropdown nicht abgeschnitten,
  Export-Dialog mit Vorprüfung, Mobile-Zeile aktualisiert sich.

### C13. Nicht-Ziele (bewusst)

Bankimport, Kreditorenkonten, TSE/DSFinV-K, USt-Voranmeldung, Anlagenbuchhaltung,
Bearbeiten von Belegen am Handy, Mehrwährung.

---

## Offene Entscheidungen für den Nutzer (Annahmen, falls nichts anderes gesagt wird)

1. Journal ersetzt das T-Konto in der Oberfläche. *(Annahme: ja)*
2. Quittung und Eigenbeleg erzeugt das Programm automatisch als PDF. *(Annahme: ja)*
3. DATEV-Stapel enthält nur Kassenbuchungen; Bank/Kreditkarte nur in der Belegliste. *(Annahme: ja)*
4. Refactoring nach C9 ist freigegeben. *(Braucht ausdrückliches Ja — Projektregel)*
5. "Wo gezahlt" verschwindet als eigenes Feld; die eine Frage "Wie bezahlt?" ersetzt es. *(Annahme: ja)*
6. Bar und EC-Karte gelten automatisch als bezahlt; nur Überweisung/Lastschrift/Rechnung fragen nach. *(Annahme: ja)*
