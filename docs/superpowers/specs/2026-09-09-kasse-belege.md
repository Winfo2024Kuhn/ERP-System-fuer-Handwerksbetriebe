# Spec: Kasse & Belege

Status: Grundlage ist das vom Nutzer am 09.09.2026 abgenommene Brainstorming
(`docs/superpowers/specs/2026-09-09-kasse-belege-brainstorming.md`, Teil A
Recherche, Teil B Ist-Zustand, Teil C Design C1–C13). Diese Spec erfindet
nichts neu, sie überführt das abgenommene Design in eine Umsetzungsgrundlage
für den Grobplan. Die sechs Annahmen am Ende des Brainstormings gelten als
bestätigt. Das Refactoring aus C9 ist vom Nutzer ausdrücklich freigegeben und
fester Bestandteil dieses Umfangs, kein optionaler Vorschlag.

## Ziel

Wer im Betrieb "die Kasse macht" — meist ohne Buchhaltungswissen — kann jede
Bargeld-Bewegung direkt und richtig erfassen: eine echte Bareinnahme, eine
Barausgabe, Geld von/zur Bank, Privateinlage/-entnahme. Heute geht das nur
über vier eng geschnittene Shortcuts (Bank→Kasse, Privateinlage,
Privatentnahme, Ehegattengehalt); eine normale Einnahme oder Ausgabe lässt
sich nicht buchen. Jede Buchung soll ohne Zusatzaufwand rechtssicher sein
(Kassenbuch-Pflichtinhalt, GoBD, Beleg zu jeder Zeile) und automatisch das
liefern, was der Steuerberater braucht — inklusive einer DATEV-Buchungsdatei,
damit er nichts mehr abtippen muss. Zusätzlich behebt dieses Vorhaben fünf
konkrete Fehler/Lücken, die der Nutzer beim Ausprobieren gefunden hat: der am
Handy gewählte Lieferant wird nach der KI-Erkennung überschrieben statt
respektiert; "Rechnung" als Zahlungsart landet ungefragt in den offenen
Posten, ohne dass man das im Prüfen-Dialog ändern kann; Kostenstellen-
Vorschläge kommen nie (stiller Abbruch ohne Grund); das Konto-Dropdown ist zu
schmal und schneidet Text ab; "Wo gezahlt" hat kein KI-Vorschlagsfeld und die
KI schlägt nie ein Konto vor.

Zielgruppe: der Handwerksbetrieb selbst (Kasse führen, ohne Buchhaltung
studiert zu haben) und dessen Steuerberater (bekommt Kassenbuch, DATEV-Datei,
Belegbilder und Zahlstatus statt Rohdaten zum Abtippen).

## Nicht-Ziele

Wörtlich aus dem Brainstorming (C13) übernommen — bewusst nicht Teil dieses
Vorhabens:

- Bankimport
- Kreditorenkonten
- TSE/DSFinV-K
- USt-Voranmeldung
- Anlagenbuchhaltung
- Bearbeiten von Belegen am Handy (Korrektur bleibt am PC, Schnellscan-Prinzip
  am Handy bleibt bestehen)
- Mehrwährung

## Architektur/Ablauf

### 0. Rechtsgrundlagen (Maßstab für Umsetzung und Review)

Diese Regeln sind der fachliche Maßstab, an dem Coding- und Review-Agenten
jede Umsetzungsentscheidung in diesem Vorhaben messen müssen.

**Kassenbuch-Pflicht und Pflichtinhalt (§ 146 AO, § 238 HGB).** Pflicht für
Bilanzierer; wer die EÜR macht, muss zwar kein "Kassenbuch" führen, aber jede
Bareinnahme trotzdem einzeln und täglich aufzeichnen — inhaltlich dieselben
Daten. Pflichtinhalt je Bewegung: laufende Nummer, Datum, Belegnummer,
Buchungstext, Betrag (Einnahme oder Ausgabe), Steuersatz, Bestand danach. Je
Blatt/Monat: Firma, Zeitraum, Anfangs- und Endbestand. Regeln: täglich
erfassen (keine Sammelbuchung, keine Schätzung), Bestand darf nie negativ
werden, jederzeit kassensturzfähig, unveränderbar (Korrektur nur durch
sichtbaren Storno), der Monat wird festgeschrieben. Privateinlagen,
Privatentnahmen und Bank↔Kasse-Transfers sind Barbewegungen und gehören ins
Kassenbuch — Bar-Geschäftsvorfälle über das Privatkonto zu buchen ist
unzulässig. Diese Mechanik (Nummer, Hash, Festschreibung, Storno,
Mindestbestand) existiert bereits (V302–V351) und wird nicht umgebaut, nur
benutzbar gemacht.

**TSE/Kassengesetz gilt nicht (§ 146a AO).** Die TSE-Pflicht, Kassenmeldung
ab 2025 und Belegausgabepflicht gelten für elektronische
Aufzeichnungssysteme mit Kassenfunktion (Registrierkassen, POS-Systeme). Ein
elektronisches Kassenbuch ist Buchführungssoftware — es zeichnet die Bewegung
des Bargelds auf, nicht den Verkauf. Wer eine offene Ladenkasse (Geldkassette)
plus Kassenbuch mit Einzelaufzeichnung führt, braucht keine TSE und muss
nichts melden. Genau dieses Modell wird gebaut. Konsequenz: Das Programm darf
nie wie eine Registrierkasse auftreten (kein "Verkauf abschließen", kein
Kassenbon als Verkaufsbeleg). Die bei einer Bareinnahme erzeugte Quittung ist
der Beleg zur Buchung (§ 33 UStDV Kleinbetragsrechnung bzw. § 14 UStG), kein
Bon im Sinne von § 146a AO — das gehört in die Verfahrensdokumentation
(Endpoint `/kassenbuch/verfahrensdokumentation` existiert bereits und wird um
die Kassen-Regeln ergänzt). `docs/GOBD_COMPLIANCE.md` Abschnitt 0.2 behauptet
heute noch "Barkasse mit TSE → separates Kassenprogramm" — das ist seit
V302–V351 (Kassenbuch liegt im System) überholt und wird im Zuge dieses
Vorhabens korrigiert.

**Belegarten (§ 33 UStDV, § 14 UStG):**

| Belegart | Wann | Pflichtangaben | Vorsteuer? |
|---|---|---|---|
| Fremdbeleg (Bon, Rechnung) ≤ 250 € brutto | Kauf beim Händler | Aussteller, Datum, Menge/Art, Bruttobetrag, Steuersatz | ja |
| Fremdbeleg > 250 € | Rechnung | volle Rechnungsangaben (u.a. Empfänger, Rechnungsnummer, Netto/USt getrennt) | ja |
| Quittung an Kunden (Bareinnahme) | Kunde zahlt bar | wie Kleinbetragsrechnung; über 250 € wie Rechnung | — (Umsatz) |
| Eigenbeleg | kein Fremdbeleg vorhanden (Parkautomat, Trinkgeld, verloren) | Datum, Betrag, Zweck, Empfänger, Zahlungsart, Ersteller/Unterschrift, Grund | nein (Betriebsausgabe ja, Vorsteuer nie) |

Ersetzendes Scannen (Handy-Foto statt Papier) muss vollständig, lesbar,
unveränderbar (SHA-256-Hash am Beleg — bereits vorhanden) und zeitnah sein,
plus Verfahrensdokumentation.

**Was der Steuerberater monatlich braucht:** Kassenbuch als PDF (vorhanden,
`BelegeKasseExportPdfService`); Buchungen als DATEV-Datei
(Buchungsstapel/EXTF-CSV, fehlt — das senkt die Steuerberater-Kosten, weil
nichts mehr abgetippt werden muss); Belegbilder als eigene Dateien, benannt
nach laufender Nummer (heute nur im PDF eingebettet); Liste der
Eingangsrechnungen mit Zahlstatus (heute als HTML-Mail vorhanden, aber ohne
Zahlstatus); Ausgangsrechnungen (vorhanden, Z3-Export) und Bankumsätze (holt
sich der Steuerberater selbst — bewusst außerhalb dieses Vorhabens).

**DATEV-Buchungsstapel (EXTF, Version 700).** Kopfzeile (Positionen 1–22):

```
"EXTF";700;21;"Buchungsstapel";13;<erzeugt am YYYYMMDDHHMMSSfff>;;"HW";"<Ersteller>";;<Beraternummer>;<Mandantennummer>;<WJ-Beginn YYYYMMDD>;4;<Datum von>;<Datum bis>;"Kasse <Monat>";;1;;<Festschreibung 0/1>;"EUR"
```

Spaltenzeile: vollständige Liste nach DATEV-Format (125 Spalten, Referenz:
ledermann/datev-Beispieldatei). Je Buchung wird befüllt:

| Spalte | Wert |
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
| Festschreibung | 1, wenn der Monat abgeschlossen ist |

Kontenrahmen: Die vorhandenen Sachkonten (V303/V307) sind SKR03-Nummern.
Kasse = 1000, Bank = 1200 sind Standard, werden aber einstellbar. Neue
Einstellungen: Beraternummer, Mandantennummer, Beginn des Wirtschaftsjahres
(Monat), Kassenkonto, Bankkonto.

### 1. Kassenbuch: Journal statt T-Konto, plus "Neue Buchung" (C1)

Der Tab "Kassenbuch" zeigt eine Journal-Tabelle: Nr. · Datum · Was · Beleg ·
Einnahme · Ausgabe · Bestand danach. Oben der aktuelle Bestand als große
Zahl, daneben "Kasse zählen" und "Monat abschließen" (beides existiert
bereits, `KassenbuchAbschlussLeiste`). Das heutige T-Konto
(`KassenbuchView.tsx:837`) entfällt in der Oberfläche — das PDF ist ohnehin
schon ein Journal.

Ein Button "Neue Buchung" öffnet eine Auswahl aus sechs Kacheln:

| Kachel | Kategorie | Pflichtfelder | Beleg |
|---|---|---|---|
| Geld eingenommen (Kunde hat bar bezahlt) | KASSE_EINNAHME | Betrag, Datum, Von wem, Wofür (Ertragskonto, Standard "Erlöse 19 %"), MwSt 19/7/0, optional "Zu welcher Rechnung?" (offene Ausgangsrechnung) | System erzeugt eine Quittung als PDF und hängt sie als Belegdatei an. Ist eine Rechnung gewählt, wird sie als bezahlt markiert (bestehender Mechanismus `ProjektGeschaeftsdokument.bezahlt`). |
| Geld ausgegeben (bar bezahlt) | KASSE_AUSGABE | Betrag, Datum, An wen, Wofür (Aufwandskonto), MwSt, optional Baustelle/Bereich | Entweder Foto/PDF anhängen (normaler Beleg, sofort geprüft) oder "Kein Beleg vorhanden" → System erzeugt einen Eigenbeleg als PDF, MwSt wird auf 0 % festgesetzt mit dem Hinweis "Ohne Fremdbeleg gibt es keine Vorsteuer". |
| Geld von der Bank geholt | KASSE_EINNAHME, Gegenkonto Bank | Betrag, Datum | Eigenbeleg (wie heute `bankAbhebung`) |
| Geld zur Bank gebracht *(neu)* | KASSE_AUSGABE, Gegenkonto Bank | Betrag, Datum | Eigenbeleg |
| Eigenes Geld eingelegt | PRIVATEINLAGE | Betrag, Datum | Eigenbeleg (wie heute) |
| Geld privat entnommen | PRIVATENTNAHME | Betrag, Datum | Eigenbeleg (wie heute) |

Alle sechs laufen über einen gemeinsamen Backend-Pfad:
`BelegService.createUmbuchung` (`BelegService.java:708`) wird zu
`createKassenbuchung` erweitert (Sachkonto, MwSt, Empfänger/Zahler, Quelle,
optionale Rechnung). Der Beleg wird sofort `VALIDIERT`, Mindestbestand wird
wie heute geprüft (409 mit Privateinlage-Vorschlag, `KasseSaldoService`). Das
erzeugte PDF (Quittung/Eigenbeleg) landet als Belegdatei — damit bleibt die
Invariante "jeder Beleg hat eine Datei" erhalten und der Steuerberater
bekommt zu jeder Zeile ein Bild. Der Ehegattengehalt-Shortcut bleibt
unverändert unter "Einstellungen" (Singleton-Entität `KasseEinstellung`,
`domain/KasseEinstellung.java`, Tabelle `kasse_einstellung`).

Neue Beleg-Spalten (siehe Datenmodell): `quelle`
(SCAN | QUITTUNG | EIGENBELEG | TRANSFER), `gegenpartei` (Von wem / An wen,
120 Zeichen), `ausgangsrechnung_id` (nullable, FK auf
`projekt_geschaeftsdokument`).

### 2. Beleg prüfen: geführter Dialog (C2)

Der Dialog "Beleg prüfen" (`BelegeKasseEditor.tsx`) wird neu geordnet. Das
Formular bekommt zwei von drei Spalten, die Vorschau eine (heute umgekehrt;
Formular hat aktuell nur 1 von 3 Spalten, `BelegeKasseEditor.tsx:1458-1465`).
Reihenfolge:

1. **Betrag und Datum** — von der KI vorbelegt, mit Badge "von KI gelesen".
2. **Wie wurde bezahlt?** — Select mit den Zahlungsarten aus den Stammdaten
   (`zahlungsart`-Tabelle, V308: Bar, EC-Karte, Überweisung, Lastschrift,
   Kreditkarte, PayPal, Scheck, Rechnung). Darunter automatisch die Folge in
   einem Satz: "Bar → landet im Kassenbuch" / "Überweisung → läuft über die
   Bank, nicht über die Kasse". Die Kategorie (`belegKategorie`) wird daraus
   abgeleitet und nicht mehr separat abgefragt — "Wo gezahlt" verschwindet
   als eigenes Feld. Badge "von KI erkannt", wenn der Wert von der KI stammt.
3. **Ist die Rechnung schon bezahlt?** — nur sichtbar, wenn Zahlungsart nicht
   Bar/EC und Dokumenttyp Rechnung/Gutschrift. Zwei Optionen: "Ja, bezahlt am
   … (Datum)" / "Nein, in die offenen Posten". Bar und EC-Karte gelten
   automatisch als bezahlt. Der Dialog zeigt den Link zur verknüpften
   Eingangsrechnung (fehlt heute komplett).
4. **Wofür war das?** — Sachkonto, gruppiert nach Aufwand/Ertrag/Privat,
   Label "4930 Bürobedarf" (Typ als Gruppenüberschrift statt Präfix). Darüber
   ein Vorschlags-Chip: "KI schlägt vor: 4930 Bürobedarf (sicher) —
   Übernehmen" oder "Beim letzten Mal bei diesem Lieferanten: 3400 Material —
   Übernehmen" oder ehrlich "Kein Vorschlag: <Grund>".
5. **Für welche Baustelle / welchen Bereich?** — Kostenstelle als einfaches
   Select (schreibt intern einen 100-%-Split), mit Vorschlags-Chip (KI,
   Lieferanten-Standardkostenstelle, Historie). "Auf mehrere aufteilen" öffnet
   den bestehenden Split-Editor.
6. **Lieferant** — mit Abweichungs-Hinweis: "Am Handy gewählt: OBI. KI hat
   gelesen: Hornbach. Übernehmen?" (siehe Abschnitt 4).

Alles Weitere (Beleg-Nr., Netto, Notiz) bleibt unter "Mehr Details". Jedes
Feld hat einen Hilfetext in einem Satz, Handwerker-Sprache, kein
Buchhalter-Wort ohne Erklärung.

### 3. Zahlungsstatus und offene Posten (C3)

- `BelegDto.UpdateRequest` bekommt `zahlungsstatus` (BEZAHLT | OFFEN) und
  `bezahltAm`. `BelegService.updateBeleg` (`BelegService.java:308ff.`)
  schreibt das auf die verknüpfte `LieferantGeschaeftsdokument` (`bezahlt`,
  `bezahltAm`) — die Entscheidung im Prüfen-Dialog ist maßgeblich und
  überschreibt KI/Vorauskasse. Heute kennt `UpdateRequest` dieses Feld nicht.
- Beim Anlegen der Eingangsrechnung durch die KI
  (`BelegKiAnalyseService.erstelleEingangsrechnungFallsRechnung`,
  `BelegKiAnalyseService.java:336-405`) wird `bereitsGezahlt` aus der
  Zahlungsart abgeleitet (Bar, EC-Karte, Kreditkarte, PayPal → bezahlt),
  damit eine bar bezahlte Rechnung nicht mehr in den offenen Posten
  auftaucht.
- Das Beleg-DTO liefert zusätzlich `eingangsrechnungBezahlt`,
  `eingangsrechnungBezahltAm`, damit der Dialog den Status zeigen kann.
- `beleg.zahlungsart` wird vereinheitlicht: Die KI-Codes werden beim
  Speichern auf die Stammdaten-Bezeichnung gemappt (z.B. `UEBERWEISUNG` →
  `Überweisung`). Ein Backfill-Skript in der Migration korrigiert Altbestand.
  Die vollständige Code-Liste der KI (`GeminiDokumentAnalyseService.java:170`,
  Prompt-Zeilen 257-268) ist `VORAUSKASSE`, `SEPA_LASTSCHRIFT`, `KREDITKARTE`,
  `PAYPAL`, `AMAZON_PAY`, `UEBERWEISUNG`, `BAR`, `SONSTIGE`; die
  Stammdaten-Tabelle (`V308__zahlungsart_stammdaten.sql`) kennt Bar,
  EC-Karte, Überweisung, Lastschrift, Kreditkarte, PayPal, Scheck, Rechnung.
  Für `VORAUSKASSE`, `AMAZON_PAY` und `SONSTIGE` gibt es keine 1:1
  Entsprechung — siehe Offene Punkte.

### 4. KI-Vorschläge: sichtbar, ehrlich, übernehmbar (C4)

- **Provenienz speichern:** neue Spalten `ki_zahlungsart`, `ki_belegdatum`,
  `ki_betrag_brutto` (Kopie der KI-Lesung). Das UI zeigt "von KI gelesen",
  solange der Wert damit übereinstimmt. `kiVorgeschlagenerLieferant` erhält
  ab jetzt immer die KI-Lesung (Fix in `BelegKiAnalyseService.java:143-144`:
  dort wird das Feld heute mit `beleg.getLieferant()` — also dem
  Nutzer-/DB-Wert, gesetzt in Zeile 137-141 — überschrieben statt mit der
  reinen KI-Lesung; die KI-Lesung geht dadurch spurlos verloren, sobald ein
  Lieferant bereits gesetzt ist). Der PC blendet den Vorschlag zusätzlich bei
  gesetztem Lieferanten aus (`BelegeKasseEditor.tsx:1436`) — auch das wird
  Teil des Fixes: Abweichungen sollen sichtbar bleiben, nicht ausgeblendet
  werden.
- **Grund statt Schweigen:** `ki_kostenkonto_hinweis` (255 Zeichen) hält
  fest, warum kein Konto-Vorschlag kam: "Kein KI-Schlüssel hinterlegt", "Zu
  wenig Text auf dem Beleg", "Kostenstelle war schon gesetzt", "KI unsicher
  (0,4)". Der Dialog zeigt den Satz. Grundlage:
  `BelegKiKostenkontoService.klassifiziereBeleg`
  (`BelegKiKostenkontoService.java:101-171`) bricht heute an vier Stellen
  still ab (Kostenstelle schon gesetzt, kein Gemini-Key, leere Antwort, keine
  `finale_zuordnung` nach 6 Runden) — in allen Fällen bleibt alles `null`,
  ohne Hinweis.
- **Belegbild an den Agenten:** `klassifiziereBeleg` bekommt das Belegbild
  (bzw. die erste PDF-Seite) und die extrahierten Positionen mit, nicht nur
  Textfelder (Prompt enthält heute kein Bild). Der veraltete Kommentar "0.80"
  in `BelegKiAnalyseService.java:184` (die tatsächliche Auto-Übernahme-Schwelle
  ist 0,95) wird korrigiert.
- **Historie als Fallback ohne KI:** neuer `BelegVorschlagService`: für einen
  Lieferanten das häufigste Sachkonto und die häufigste Kostenstelle der
  letzten 20 validierten Belege; dazu die Standardkostenstelle des
  Lieferanten. Läuft immer, auch ohne Gemini-Key. Ergebnis im DTO als
  `vorschlagSachkonto` / `vorschlagKostenstelle` mit `quelle`
  (KI | HISTORIE | LIEFERANT_STANDARD) und `begruendung`.
- **Übernehmen-Buttons** für Sachkonto und Kostenstelle in der
  Vorschlags-Karte (heute nur Text, nicht übernehmbar,
  `KiVorschlagKarte.tsx:1993-1997`).
- Tests für `klassifiziereBeleg` selbst (die vier Abbruchpfade) — fehlen
  heute komplett.

### 5. Mobile: Ergebnis kommt an (C5)

- `BelegScannerPage.tsx` pollt aktuell nicht (`BelegScannerPage.tsx:112-135`,
  die Zeile bleibt auf "KI liest Beleg…" stehen). Sie pollt künftig alle 3 s,
  solange ein eigener Beleg in `PENDING`/`LAEUFT` ist, und hört auf, wenn
  alle fertig sind.
- Die Zeile zeigt nach der Analyse Betrag, Datum und — bei Abweichung — "KI
  hat <Name> gelesen" unter dem gewählten Lieferanten. Kein Bearbeiten am
  Handy; die Korrektur bleibt am PC (bewusst: Schnellscan-Prinzip bleibt,
  siehe Nicht-Ziele).

### 6. Dropdown und Layout (C6)

`select-custom.tsx` wird repariert, nicht ersetzt (wird an sieben Stellen
benutzt). Heutige Mängel: Breite hart = Trigger-Breite
(`select-custom.tsx:29-37`), Optionen `truncate` ohne Tooltip
(`select-custom.tsx:89`), Position wird nur beim Öffnen berechnet (kein
Scroll-/Resize-Listener, kein Hochklappen). Das Formular hat nur 1 von 3
Spalten (`BelegeKasseEditor.tsx:1458-1465`), das Konto-Feld darin nochmal die
Hälfte — rund ein Sechstel der Dialogbreite für Labels wie "Aufwand · 4930
Bürobedarf und Zeitschriften".

- Breite = max(Trigger, Inhalt), gedeckelt auf `min(90vw, 480px)`; Optionen
  brechen um statt abzuschneiden, plus `title`.
- Position wird bei Scroll und Resize neu berechnet; klappt nach oben, wenn
  unten kein Platz ist.
- Optionale Gruppen (`optgroup`-Äquivalent) für die Sachkonto-Typen.
- Playwright-Zusicherung: für jede Option `scrollWidth <= clientWidth`, und
  das Dropdown liegt vollständig im Viewport.
- Dialog: Formular `lg:col-span-2`, Vorschau `lg:col-span-1`; Konto- und
  Kostenstellen-Felder in voller Formularbreite.

### 7. Export für den Steuerberater (C7)

Ein Dialog "Für den Steuerberater" (Monat wählen) erzeugt eine ZIP-Datei:

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

- Neue Einstellungen (auf der bestehenden Singleton-Tabelle `kasse_einstellung`,
  `domain/KasseEinstellung.java`): Beraternummer, Mandantennummer,
  Wirtschaftsjahr-Beginn (Monat), Kassenkonto (Standard 1000), Bankkonto
  (Standard 1200). Fehlen Berater-/Mandantennummer, wird trotzdem exportiert
  (DATEV erlaubt leere Felder), mit Hinweis.
- **Vorprüfung vor dem Export:** Belege ohne Sachkonto oder ohne Zahlungsart
  werden aufgelistet ("3 Belege sind noch nicht fertig geprüft") — Export
  erst, wenn die Liste leer ist oder der Nutzer "trotzdem" wählt (dann Konto
  leer, Buchungstext mit Präfix "PRÜFEN:").
- Der bestehende E-Mail-Versand (`SteuerberaterBelegExportModal`) bekommt die
  ZIP als Anhang statt der HTML-Tabelle; die Mail bleibt kurz.
- Bewusst nicht im DATEV-Stapel: Bank- und Kreditkartenbuchungen. Das System
  kennt keine Bankumsätze; der Steuerberater bucht sie vom Kontoauszug und
  findet Beleg und Zahlstatus in der CSV 03. Das steht so in der LIESMICH.

Neue Backend-Services: `DatevExportService` als reine Funktion (Belege →
CSV-Text), getestet gegen eine Golden-File-Datei; `SteuerberaterExportService`
baut die ZIP. `BuchungssatzAbleitung` (`BuchungssatzAbleitung.java`, heute
nur im Test aufgerufen) wird dafür endlich benutzt (Soll/Haben je Kategorie)
statt still zu liegen — siehe Offene Punkte zur genauen Rolle im
DATEV-Export.

### 8. Einlesen: "So funktioniert die Kasse" (C8)

Ein einklappbarer Kasten oben im Kassenbuch-Tab (Standard: offen, bis der
Nutzer ihn schließt; Zustand im `localStorage`), fünf Sätze:

1. Jede Barbewegung sofort eintragen — auch Bank-Abhebung und eigenes Geld.
2. Zu jeder Zeile gehört ein Beleg. Fehlt einer, macht das Programm einen
   Eigenbeleg (ohne Vorsteuer).
3. Die Kasse darf nie unter null. Vorher eigenes Geld einlegen.
4. Einmal im Monat: Kasse zählen, dann den Monat abschließen. Danach ist er
   fest.
5. Falsch gebucht? Nicht löschen — stornieren. Das Original bleibt sichtbar.

Dazu der Link "Mehr dazu" auf eine kurze Hilfeseite (Markdown in `docs/`,
verlinkt aus der Oberfläche).

### 9. Refactoring — freigegeben (C9)

Damit die Tasks parallel laufen können und die Datei wieder lesbar wird:

- `BelegDetailModal` (640 Zeilen) → `components/kasse/BelegDetailModal.tsx`
- `KassenbuchView` + `TKontoZeile` → ersetzt durch
  `components/kasse/KassenbuchJournal.tsx`
- `KiVorschlagKarte` → `components/kasse/VorschlagsChip.tsx`
- Beleg-Typen (`Beleg`, `Sachkonto`, `Zahlungsart`, `BelegKategorie`, …) →
  `src/types.ts` (ein Ort, wie beim Rest des Frontends)
- Die vier Shortcut-Modals in `KasseShortcuts.tsx` gehen in
  `components/kasse/NeueBuchungDialog.tsx` auf.

Reine Verschiebung zuerst (eigener Task, Verhalten unverändert, Tests grün),
danach die fachlichen Änderungen. Backend: `BelegService.java` (aktuell 1232+
Zeilen) bleibt bestehen, neue Logik kommt in eigene Services
(`KassenbuchungService`, `BelegVorschlagService`, `DatevExportService`,
`SteuerberaterExportService`, `EigenbelegPdfService`).

## Datenmodell

Nächste freie Flyway-Nummer (Stand des Hauptcheckouts nach dem Fast-Forward
auf `origin/main`, Zeitkonto-Vorhaben V368–V371 bereits vergeben):
**V372**. Eine Migration
`V372__kasse_buchungen_und_export.sql`:

- `beleg`: `quelle VARCHAR(20) NOT NULL DEFAULT 'SCAN'`,
  `gegenpartei VARCHAR(120)`, `ausgangsrechnung_id BIGINT NULL` (FK auf
  `projekt_geschaeftsdokument`), `ki_zahlungsart VARCHAR(40)`,
  `ki_belegdatum DATE`, `ki_betrag_brutto DECIMAL(15,2)`,
  `ki_kostenkonto_hinweis VARCHAR(255)`.
- `kasse_einstellung`: `datev_beraternummer VARCHAR(7)`,
  `datev_mandantennummer VARCHAR(5)`,
  `wirtschaftsjahr_beginn_monat TINYINT DEFAULT 1`,
  `kassenkonto_nummer VARCHAR(8) DEFAULT '1000'`,
  `bankkonto_nummer VARCHAR(8) DEFAULT '1200'`.
- Backfill: `beleg.zahlungsart`-Codes → Stammdaten-Bezeichnung (siehe
  Abschnitt 3 für die vollständige Code-Liste und die drei Codes ohne
  eindeutige Zielspalte); `quelle = 'TRANSFER'` für bestehende
  `ist_umbuchung = TRUE`.
- Alles idempotent über `INFORMATION_SCHEMA`, wie in V351.

Bestehende Endpunkte bleiben kompatibel: `POST /api/buchhaltung/umbuchungen`
bleibt als Alias auf den neuen Pfad `POST /kassenbuch/buchungen` erhalten.

`BelegDto.UpdateRequest` (siehe Abschnitt 3) bekommt `zahlungsstatus`
(BEZAHLT | OFFEN) und `bezahltAm`. `BelegDto.Response` bekommt
`eingangsrechnungBezahlt`, `eingangsrechnungBezahltAm`, `vorschlagSachkonto`,
`vorschlagKostenstelle` (jeweils mit `quelle` und `begruendung`).

## Betroffene Bereiche

**Backend** (`src/main/java/org/example/kalkulationsprogramm/`):

- Geändert: `service/BelegService.java` (`createUmbuchung` → erweitert/als
  `createKassenbuchung`, Zeile 708; `updateBeleg`, Zeile 308ff. —
  Zahlungsstatus).
- Geändert: `service/BelegKiAnalyseService.java` (Lieferant-Fix Zeile
  137-144, `belegKategorie`-Ableitung Zeile 146-157,
  `erstelleEingangsrechnungFallsRechnung` Zeile 336-405 —
  `bereitsGezahlt`-Ableitung).
- Geändert: `service/BelegKiKostenkontoService.java`
  (`klassifiziereBeleg`, Zeile 101-171 — Hinweis statt stillem Abbruch,
  Belegbild in den Prompt, Kommentar-Fix Zeile 184).
- Geändert: `service/BuchungssatzAbleitung.java` (wird für den DATEV-Export
  eingebunden, siehe Offene Punkte).
- Geändert: `service/BelegeKasseExportPdfService.java` (Baustein 01 im neuen
  ZIP-Export).
- Geändert: `domain/KasseEinstellung.java`,
  `repository/KasseEinstellungRepository.java` (neue DATEV-Einstellungen).
- Geändert: `dto/BelegDto.java` (neue Felder, siehe Datenmodell).
- Neu: `service/KassenbuchungService.java` (sechs Kacheln, ein gemeinsamer
  Pfad).
- Neu: `service/BelegVorschlagService.java` (Historie/Lieferanten-Standard-
  Fallback).
- Neu: `service/DatevExportService.java` (EXTF-700-CSV, reine Funktion).
- Neu: `service/SteuerberaterExportService.java` (ZIP-Aufbau,
  Vorprüfung, Mail-Anhang).
- Neu: `service/EigenbelegPdfService.java` (Quittungs- und Eigenbeleg-PDFs;
  genaue Aufteilung siehe Offene Punkte).
- Neu: `src/main/resources/db/migration/V372__kasse_buchungen_und_export.sql`.
- Fach-Dokumentation: `docs/GOBD_COMPLIANCE.md` Abschnitt 0.2 (Korrektur:
  Barkasse braucht keine TSE), Endpoint
  `/kassenbuch/verfahrensdokumentation` (Ergänzung um Kassen-Regeln).

**Frontend Desktop** (`react-pc-frontend/`):

- Geändert/aufgeteilt (C9): `src/pages/BelegeKasseEditor.tsx` (2137 Zeilen,
  13 Unterkomponenten — Prüfen-Dialog nach C2, Auslagerung von
  `BelegDetailModal` und `KiVorschlagKarte`).
- Ersetzt: `KassenbuchView` + `TKontoZeile`
  (`src/.../KassenbuchView.tsx:837`) → neu
  `src/components/kasse/KassenbuchJournal.tsx`.
- Aufgeteilt/verschoben: `src/components/kasse/KasseShortcuts.tsx` → neu
  `src/components/kasse/NeueBuchungDialog.tsx` (sechs Kacheln).
- Geändert: `src/components/ui/select-custom.tsx` (Breite, Position,
  Gruppen, Zeilen 29-37 und 89 betroffen).
- Geändert: `SteuerberaterBelegExportModal` (ZIP-Anhang statt HTML-Tabelle,
  Vorprüfung).
- Neu: zentrale Beleg-Typen in `src/types.ts` (C9).

**Frontend Handy** (`react-zeiterfassung/`):

- Geändert: `src/pages/BelegScannerPage.tsx` (Polling alle 3 s statt keinem
  Polling, Zeilen 112-135; Anzeige "KI hat <Name> gelesen").

## Tests

- Backend: `DatevExportServiceTest` (Golden File, S/H-Regeln, BU-Schlüssel,
  TTMM, Umlaute/Anführungszeichen), `KassenbuchungServiceTest` (sechs
  Kacheln, Mindestbestand, Rechnung wird bezahlt markiert, PDF-Datei
  existiert und hat Hash), `BelegVorschlagServiceTest` (Historie,
  Lieferanten-Standard, Reihenfolge KI > Standard > Historie),
  `BelegKiKostenkontoServiceTest` für die vier Abbruchpfade,
  `BelegServiceZahlungsstatusTest` (bezahlt/offen schreibt auf die
  Eingangsrechnung), Migrationstest auf die neuen Spalten.
- Frontend (vitest): Journal-Saldo, Ableitung Zahlungsart → Satz,
  Vorschlags-Chip.
- Playwright (je Task eine Spec, gestubbte API): Neue Buchung (alle sechs
  Kacheln), Prüfen-Dialog in Reihenfolge, Dropdown nicht abgeschnitten,
  Export-Dialog mit Vorprüfung, Mobile-Zeile aktualisiert sich.
- Alle Backend-Tests mit Dummy-Daten (z.B. "Max Mustermann"), keine echten
  Personen-/Kundendaten (DSGVO, Projektregel).

## Offene Punkte für den Grobplan

- **Zahlungsart-Mapping ohne 1:1-Ziel:** Die KI liefert
  `VORAUSKASSE`, `SEPA_LASTSCHRIFT`, `KREDITKARTE`, `PAYPAL`, `AMAZON_PAY`,
  `UEBERWEISUNG`, `BAR`, `SONSTIGE` (`GeminiDokumentAnalyseService.java:170`,
  257-268). Die Stammdaten-Zahlungsarten (`V308__zahlungsart_stammdaten.sql`)
  sind Bar, EC-Karte, Überweisung, Lastschrift, Kreditkarte, PayPal, Scheck,
  Rechnung. Für `VORAUSKASSE`, `AMAZON_PAY` und `SONSTIGE` nennt das
  Brainstorming kein Ziel-Mapping (nur das Beispiel `UEBERWEISUNG` →
  `Überweisung`). Der Grobplan muss festlegen, worauf diese drei Codes im
  Backfill und beim Speichern gemappt werden.
- **Rolle von `BuchungssatzAbleitung` im DATEV-Export:** Die Klasse
  (`BuchungssatzAbleitung.java`) liefert heute Text-Labels ("Kasse", "Bank",
  Sachkonto-Bezeichnung) für die PDF-Darstellung — nicht das
  DATEV-Soll/Haben-Kennzeichen (ein Buchstabe S/H je Zeile, siehe
  Rechtsgrundlagen A5). Der Grobplan muss klären, ob `DatevExportService`
  diese Klasse erweitert (neue Methode, die S/H statt Text liefert) oder das
  S/H-Kennzeichen unabhängig direkt aus `BelegKategorie` ableitet und
  `BuchungssatzAbleitung` nur wie bisher für das PDF benutzt.
- **Eigenbeleg-Unterschrift:** Die Rechtsgrundlage (A3) verlangt auf einem
  Eigenbeleg "Ersteller/Unterschrift". Das Design (C1/C10) sieht dafür keine
  gesonderte Erfassung vor. Der Grobplan muss festlegen, ob der Name des
  angemeldeten, buchenden Nutzers auf dem PDF als Unterschrift-Ersatz reicht,
  oder ob eine zusätzliche Bestätigung/Signatur nötig ist.
- **Aufteilung Quittungs-PDF/Eigenbeleg-PDF:** C9 nennt nur einen neuen
  `EigenbelegPdfService`, C1 verlangt aber zwei fachlich unterschiedliche
  PDF-Typen (Quittung an den Kunden bei "Geld eingenommen"; Eigenbeleg bei
  den übrigen fünf Kacheln ohne Fremdbeleg). Der Grobplan muss entscheiden,
  ob beide Typen im selben Service liegen oder ob ein eigener
  `QuittungPdfService` sinnvoller ist.
- **WJ-Beginn-Berechnung im DATEV-Header:** Die neue Einstellung
  `wirtschaftsjahr_beginn_monat` liefert nur einen Monat. Feld 13 der
  DATEV-Kopfzeile braucht ein volles Datum (`YYYYMMDD`). Bei einem vom
  Kalenderjahr abweichenden Wirtschaftsjahr (z.B. Beginn im Juli) ist nicht
  spezifiziert, wie das zugehörige Jahr aus dem gewählten Exportmonat
  abgeleitet wird.
- **UI-Ort der neuen DATEV-Einstellungen:** Die heutigen Kasse-Einstellungen
  (Mindestbestand, Ehegattengehalt-Automatik) werden direkt in
  `react-pc-frontend/src/pages/BelegeKasseEditor.tsx` editiert; die
  allgemeine Einstellungsseite (`react-pc-frontend/src/pages/EinstellungenEditor.tsx`
  → `src/components/settings/SystemSetupConfigurator.tsx`) hat aktuell nur
  die Reiter E-Mail, Dateien, KI-Funktionen, Zeiterfassung — keinen
  Buchhaltungs-Reiter. Der Grobplan muss festlegen, wo (eigener Reiter im
  `SystemSetupConfigurator`, Dialog innerhalb der Kasse, oder neue Seite) die
  fünf neuen DATEV-Einstellungen aus C7 untergebracht werden.

## Entscheidungen des Orchestrators zu den offenen Punkten (09.09.2026)

1. **KI-Zahlungsart-Codes → Stammdaten:** `BAR`→Bar, `EC`/`EC_KARTE`/`GIROCARD`→EC-Karte,
   `UEBERWEISUNG`→Überweisung, `SEPA_LASTSCHRIFT`/`LASTSCHRIFT`→Lastschrift,
   `KREDITKARTE`→Kreditkarte, `PAYPAL`→PayPal, `AMAZON_PAY`→**Online-Zahlung** (neuer
   Stammdaten-Eintrag in V372, `sortierung` 65, Kategorie BANK), `VORAUSKASSE`→Überweisung
   **plus** `bereitsGezahlt = true` auf der Eingangsrechnung, `RECHNUNG`→Rechnung,
   `SONSTIGE`/unbekannt/leer → Zahlungsart bleibt leer, der Nutzer muss wählen (Pflichtfeld
   beim Prüfen). Das Mapping lebt an einer Stelle (`ZahlungsartMapper`, reine Funktion, mit Test)
   und wird sowohl in der KI-Analyse als auch im Backfill der Migration (SQL-`CASE`) benutzt.
2. **BuchungssatzAbleitung und DATEV:** `BuchungssatzAbleitung` bekommt eine zweite Methode
   `ableitenKonten(Beleg, KasseEinstellung) -> Buchungssatz(sollKontoNr, habenKontoNr)`, die
   Kontonummern statt Labels liefert (Kasse/Bank aus `KasseEinstellung`, Privatkonten aus dem
   Sachkonto bzw. den Standardkonten 1800/1810 laut V303). Der DATEV-Writer schreibt **immer**
   `Soll/Haben-Kennzeichen = S`, `Konto = Soll-Konto`, `Gegenkonto = Haben-Konto`. Das ist
   gültiges DATEV und erspart die Vorzeichen-Logik je Kontotyp.
3. **Eigenbeleg-Unterschrift:** Ein digitaler Eigenbeleg trägt "Erstellt von <Vor- und
   Nachname des angemeldeten Nutzers> am <Datum, Uhrzeit>" und den SHA-256-Hash der Datei.
   Das ersetzt die handschriftliche Unterschrift (GoBD: Ersteller und Zeitpunkt sind
   nachvollziehbar und unveränderbar). Keine Unterschriftszeile zum Ausdrucken.
4. **Ein PDF-Service:** `BelegPdfService` mit zwei Methoden `erzeugeQuittung(...)` und
   `erzeugeEigenbeleg(...)`, gemeinsames Layout (Briefkopf wie `BelegeKasseExportPdfService`,
   gleiche PDF-Bibliothek). Kein zweiter Service.
5. **WJ-Beginn:** `wirtschaftsjahr_beginn_monat` (1–12, Standard 1). Für den Exportmonat M/J gilt:
   WJ-Beginn = `J-<Monat>-01`, wenn M ≥ Beginnmonat, sonst `(J-1)-<Monat>-01`. Reine Funktion
   mit Test (Januar-Beginn, abweichendes WJ, Jahreswechsel).
6. **UI-Ort der DATEV-Einstellungen:** der bestehende Einstellungen-Dialog der Kasse
   (Zahnrad in `KasseShortcuts.tsx`, `GET/PUT /api/buchhaltung/kasse/einstellung`) bekommt einen
   zweiten Abschnitt "Für den Steuerberater" mit Beraternummer, Mandantennummer,
   Wirtschaftsjahr-Beginn, Kassenkonto, Bankkonto. Kein neuer Reiter auf der
   Einstellungsseite. Nach dem Refactoring (C9) liegt dieser Dialog in
   `components/kasse/KasseEinstellungenDialog.tsx`.
