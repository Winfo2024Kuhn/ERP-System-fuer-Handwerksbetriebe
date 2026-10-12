# ZUGFeRD & E-Invoicing – Elektronische Rechnungsstellung

## Übersicht

Dieses Dokument beschreibt die Implementierung der elektronischen Rechnungsstellung (E-Invoicing) im Kalkulationsprogramm nach dem ZUGFeRD-Standard. Das System unterstützt sowohl das **Erstellen** von ZUGFeRD-Hybrid-PDFs (PDF mit eingebettetem XML) als auch das **Lesen** von ZUGFeRD-PDFs und XML-E-Rechnungen (XRechnung, ZUGFeRD-XML).

**Kurz für die Praxis:**
- E-Rechnung verschicken: im Dokument-Editor auf **„E-Mail“** oder **„PDF“** klicken und **„ZUGFeRD PDF“** wählen („PDF mit maschinenlesbaren Rechnungsdaten (EN 16931)“).
- Vorher müssen die Firmendaten vollständig sein (Vorlagen & Stammdaten → Administration → **Firma** → Reiter „Firmendaten“): Firmenname, Straße, Postleitzahl, Ort und Umsatzsteuer-ID oder Steuernummer. Fehlt etwas, meldet das Programm z. B. „Für die E-Rechnung fehlen Angaben zu Ihrem Betrieb: Postleitzahl. Bitte unter „Firma“ ergänzen.“ – und die Rechnung wird **nicht** gebucht.
- E-Rechnungen von Lieferanten (ZUGFeRD-PDF oder XML) werden beim Eingang automatisch ausgelesen – ganz ohne KI.

---

## 1. Ausgehende Rechnungen (`ZugferdErstellService`)

### 1.1 Zweck

Erstellt ZUGFeRD-konforme Hybrid-PDFs, die sowohl menschenlesbares PDF als auch maschinenlesbares XML enthalten.

### 1.2 Technologie

- **Mustang-Bibliothek** (`org.mustangproject:library` 2.18.0)
- Klasse: `ZUGFeRDExporterFromA3`
- Profil: Das Profil wird im Code nicht ausdrücklich gesetzt; es gilt die Vorgabe der Mustang-Bibliothek: **EN 16931 (COMFORT)**, ZUGFeRD 2 / Factur-X
- Ergebnis: PDF/A-3 mit eingebettetem XML

### 1.3 Ablauf

```
Dokument-Editor: "E-Mail" oder "PDF" → Format "ZUGFeRD PDF"
    │
    ▼
GET /api/dokument-generator/zugferd-pruefung
    │  → Firmendaten vollständig? (204 = ok, 422 = Meldung mit fehlenden Angaben)
    │
    ▼
Rechnung buchen & sperren (nur Rechnungstypen)
    │
    ▼
POST /api/dokument-generator/zugferd-pdf
    │  1. Standard-PDF erzeugen (RechnungPdfService, OpenPDF)
    │  2. ZugferdDaten aus den Dokumentdaten ableiten
    │  3. ZugferdErstellService.erzeuge(pdfPfad, zugferdDaten)
    │     → bettet ZUGFeRD-XML ein, erzeugt temporäre Datei
    │
    ▼
ZUGFeRD-Hybrid-PDF (PDF/A-3 + eingebettetes XML) als Download bzw. E-Mail-Anhang
```

### 1.4 Methode

```java
Path erzeuge(String originalPdfPath, ZugferdDaten daten)
```

**Parameter:**
- `originalPdfPath` – Pfad zum bereits generierten PDF
- `daten` – `ZugferdDaten`-DTO mit den Rechnungsinformationen

**Rückgabe:** Pfad zur temporären Datei mit dem ZUGFeRD-Hybrid-PDF

Zusätzlich prüft `pruefeVerkaeuferdaten()` vorab nur die Firmendaten, ohne etwas zu erzeugen.

### 1.5 Inhalt des eingebetteten XML

| Bereich | Quelle / Regel |
|---|---|
| Rechnungsnummer | Dokumentnummer (z. B. `RE-2026/10/00042`) |
| Rechnungsdatum | Dokumentdatum (fehlt es: heute) |
| Fälligkeitsdatum | Rechnungsdatum **+ 14 Tage**; wird nur bei Dokumentart „Rechnung“ eingetragen |
| Verkäufer | Firmendaten: Name, Straße, PLZ, Ort, Land `DE`, USt-IdNr. und/oder Steuernummer (ohne USt-IdNr. dient die Steuernummer zugleich als Kennung), Kontakt (Geschäftsführer bzw. Firmenname, Telefon, E-Mail), Bankverbindung (IBAN, BIC), falls hinterlegt |
| Käufer | Kundenname (fehlt er: „Kunde“) und Kundennummer |
| Positionen | **Eine Sammelposition** mit dem Nettobetrag, bezeichnet nach der Dokumentart |
| Nettobetrag | Summe der nicht-optionalen Leistungen inkl. Positionsrabatt, abzüglich Pauschalrabatt; bei Abschlagsrechnungen der Abschlagsbetrag; bei Schlussrechnungen abzüglich der bereits gestellten Rechnungen |
| Steuersatz | 19 % |
| Bruttobetrag | Netto × 1,19 |

**Prüfung der Firmendaten (`baueVerkaeufer`)** – fehlt etwas, wird mit `FirmenstammdatenUnvollstaendigException` (HTTP 422) abgebrochen:
- Firmenname, Straße, Postleitzahl, Ort
- Umsatzsteuer-ID **oder** Steuernummer
- Eine angegebene USt-IdNr. muss mit dem Ländercode beginnen (Format `DE123456789`)

---

## 2. Eingehende Rechnungen (`ZugferdExtractorService`)

### 2.1 Zweck

Extrahiert strukturierte Rechnungsdaten aus ZUGFeRD-PDFs (eingebettetes XML).

### 2.2 Methode

```java
ZugferdDaten extract(String pdfPath, String originalFilename)
```

Ist das PDF keine ZUGFeRD-Datei, wird ein fast leeres `ZugferdDaten` zurückgegeben (nur der aus dem Dateinamen geratene Dokumenttyp) – es gibt keine Exception.

### 2.3 Extrahierte Felder

| Feld | ZUGFeRD-Quelle | Beschreibung |
|---|---|---|
| `rechnungsnummer` | Invoice ID | Rechnungsnummer |
| `rechnungsdatum` | Issue Date | Rechnungsdatum |
| `faelligkeitsdatum` | Due Date | Fälligkeitsdatum (falls vorhanden) |
| `betrag` | Grand Total Amount | Bruttobetrag |
| `bereitsGezahlt` | Total Prepaid Amount | `true`, wenn der bereits gezahlte Betrag den Bruttobetrag deckt (z. B. Vorauskasse) |
| `kundenName` | Buyer Name | Name des Rechnungsempfängers (Umlaute werden repariert) |
| `kundennummer` | Buyer ID | Kundennummer |
| `bestellnummer` | Buyer Order Reference / Buyer Reference | Unsere Bestellnummer beim Lieferanten |
| `referenzNummer` | Seller Order Reference, Contract Reference, Seller Reference, Project Reference | Referenz des Lieferanten (AB-Nummer, Vertrag, Projekt) |
| `skontoProzent` | Payment Discount Terms – Percent | Skonto-Prozentsatz |
| `skontoTage` | Payment Discount Terms – Basis Period | Skontotage |
| `typeCode` | TypeCode (UNTDID 1001) | Roher Dokumenttyp-Code |
| `geschaeftsdokumentart` | aus TypeCode, sonst Dateiname | z. B. Rechnung, Gutschrift, Auftragsbestätigung, Lieferschein, Angebot |
| `artikelPositionen` | Line Items (CII und UBL) | Einzelne Rechnungspositionen |
| `betragNetto` | **berechnet** | Brutto ÷ 1,19 |
| `mwstSatz` | **fest** | 0,19 |
| `nettoTage` | **berechnet** | Tage zwischen Rechnungs- und Fälligkeitsdatum |

### 2.4 Artikelpositionen (`ZugferdArtikelPosition`)

Jede Rechnungsposition enthält:

| Feld | Beschreibung |
|---|---|
| `externeArtikelnummer` | Artikelnummer des Lieferanten (bzw. des Käufers) |
| `bezeichnung` | Artikelbezeichnung |
| `menge` | Menge |
| `mengeneinheit` | Einheit (Stück, kg, m etc.) |
| `einzelpreis` | Preis pro Einheit |
| `preiseinheit` | Preiseinheit (z. B. t, 100 kg) |
| `gesamtpreisNetto` | Positionsbetrag netto |

Die Positionen fließen in die Preisübernahme für den Artikelstamm – aber nur, wenn der TypeCode der Datei das Dokument wirklich als Rechnung ausweist.

---

## 3. Konvertierung (`ZugferdConverterService`)

### 3.1 Zweck

Baut ein `ZugferdDaten`-Objekt aus einem Projekt-Offenen-Posten. **Hinweis:** Der Service wird derzeit von keiner anderen Klasse aufgerufen; ausgehende E-Rechnungen entstehen über `DokumentGeneratorController` (siehe Abschnitt 1.3).

### 3.2 Methode

```java
ZugferdDaten convertRechnung(Projekt projekt, ProjektGeschaeftsdokument rechnung)
```

### 3.3 Mapping

| Interne Daten | ZUGFeRD-Feld |
|---|---|
| `rechnung.getDokumentid()` | `rechnungsnummer` |
| `rechnung.getRechnungsdatum()` | `rechnungsdatum` |
| `rechnung.getFaelligkeitsdatum()` | `faelligkeitsdatum` |
| `rechnung.getBruttoBetrag()` | `betrag` |
| `rechnung.getGeschaeftsdokumentart()` | `geschaeftsdokumentart` |
| `projekt.getKunde()` | `kundenName` |
| `projekt.getKundennummer()` | `kundennummer` |

---

## 4. Auslese-Kette bei Eingangsrechnungen

### 4.1 Priorisierte Extraktionsmethoden (`GeminiDokumentAnalyseService`)

```
[1] PDF mit eingebettetem ZUGFeRD-XML?
     │
     ├── JA → ZugferdExtractorService.extract()
     │         datenquelle = "ZUGFERD"
     │         verifiziert = true, aiConfidence = 1.0
     │         (Höchste Zuverlässigkeit)
     │
     └── NEIN ↓

[2] XML-Datei (XRechnung oder ZUGFeRD-XML)?
     │
     ├── JA → XML-Parsing
     │         datenquelle = "XML", aiConfidence = 1.0
     │
     └── NEIN ↓

[3] KI-Analyse (Google Gemini) – für PDF, JPG, PNG
     │
     └── verifiziert = false
         aiConfidence = 0.0–1.0 (von der KI geliefert)
         manuellePruefungErforderlich = true, wenn Pflichtfelder fehlen
         (vorher zweiter Versuch mit dem Pro-Modell, falls ein eigenes konfiguriert ist)
```

Der Dateiname wird nur genutzt, um den Dokumenttyp zu erraten – er ist keine eigene Auslese-Stufe. Die KI braucht einen hinterlegten Gemini-Schlüssel (Einstellungen → Reiter „KI-Funktionen“).

### 4.2 Vergleich der Methoden

| Methode | Zuverlässigkeit | Auto-Verifiziert | Geschwindigkeit |
|---|---|---|---|
| ZUGFeRD | ★★★★★ | ✅ Ja | ⚡ Schnell |
| XML | ★★★★☆ | ❌ Nein (Datenquelle „XML“) | ⚡ Schnell |
| KI (Gemini) | ★★★☆☆ | ❌ Nein | 🐢 Langsam (API-Call) |

### 4.3 Wo E-Rechnungen ankommen

- **E-Mail-Anhänge** von Lieferanten (PDF oder XML) werden automatisch als Lieferanten-Dokument abgelegt und ausgelesen
- **Lieferanten** → Dokumente hochladen
- **Rechnungen** (Rechnungsübersicht) → „Rechnung/Gutschrift manuell importieren“ (PDF, XML, JPG, PNG; max. 25 MB)

---

## 5. Feldmapping

### 5.1 Ausgehend (Intern → ZUGFeRD)

| Internes Feld | ZUGFeRD-Feld |
|---|---|
| Dokumentnummer | Invoice Number |
| Dokumentdatum | Issue Date |
| Dokumentdatum + 14 Tage | Due Date (nur Dokumentart „Rechnung“) |
| Nettobetrag (Sammelposition) | Line Item / Tax Basis |
| 19 % | Tax Percent |
| Kundenname | Buyer Name |
| Kundennummer | Buyer ID |
| Firmendaten | Seller (Name, Anschrift, USt-IdNr./Steuernummer, Kontakt, Bank) |

### 5.2 Eingehend (ZUGFeRD → `LieferantGeschaeftsdokument`)

| ZUGFeRD-Feld | Internes Feld |
|---|---|
| Invoice Number | `dokumentNummer` |
| Issue Date | `dokumentDatum` |
| Due Date | `zahlungsziel` |
| Grand Total Amount | `betragBrutto` |
| (Brutto ÷ 1,19) | `betragNetto` |
| (fest 0,19) | `mwstSatz` |
| Buyer Order Reference | `bestellnummer` |
| Seller Order / Contract / Seller Reference | `referenzNummer` |
| Cash Discount Percent | `skontoProzent` |
| Cash Discount Days | `skontoTage` |
| (Fälligkeit − Rechnungsdatum) | `nettoTage` |
| Total Prepaid Amount ≥ Brutto | `bereitsGezahlt = true`, `bezahlt = true`, `bezahltAm = Rechnungsdatum` |
| TypeCode | erkannter Dokumenttyp |
| Line Items | ausgelesene Positionen |

---

## 6. Gesetzliche Anforderungen

### 6.1 E-Rechnungspflicht

| Zeitraum | Pflicht | Bereich |
|---|---|---|
| Seit 27.11.2020 | E-Rechnung an Bundesbehörden (Länder mit eigenen Fristen) | **B2G** (Business-to-Government) |
| Ab 01.01.2025 | Empfangspflicht | **B2B** (alle Unternehmen müssen E-Rechnungen empfangen können) |
| Ab 01.01.2027 | Versandpflicht | **B2B** (Unternehmen mit mehr als 800.000 € Vorjahresumsatz) |
| Ab 01.01.2028 | Versandpflicht | **B2B** (alle Unternehmen) |

Rechnungen an Privatkunden (B2C) sind nicht betroffen. Fristen und Ausnahmen (z. B. Kleinbetragsrechnungen) im Zweifel mit dem Steuerberater klären.

### 6.2 Unterstützte Formate

| Format | Beschreibung | Status |
|---|---|---|
| **ZUGFeRD 2 (Profil EN 16931)** | Hybrid-PDF (PDF/A-3 + XML) | ✅ Erstellen und Lesen |
| **Factur-X** | Französisch-deutsches Pendant zu ZUGFeRD | ✅ Lesen (gleiche Basis) |
| **XRechnung** | Reines XML-Format (kein PDF) | ⚠️ Nur Lesen (CII und UBL); Erstellen nicht möglich |

### 6.3 Anforderungen an E-Rechnungen

- **Strukturierte Daten**: Maschinenlesbares XML mit Pflichtfeldern
- **Integrität**: PDF/A-3 Standard für Langzeitarchivierung
- **Vollständigkeit**: Rechnungsnummer, Datum, Beträge, MwSt, Empfänger
- **Normenkonformität**: EN 16931 (europäische Norm für elektronische Rechnungen)

### 6.4 Bekannte Einschränkungen der erzeugten E-Rechnung

- Alle Leistungen stehen als **eine Sammelposition** im XML; die Einzelpositionen sieht der Empfänger nur im PDF.
- Der Steuersatz ist fest **19 %**.
- Das Fälligkeitsdatum ist fest **Rechnungsdatum + 14 Tage** – unabhängig vom Zahlungsziel des Dokuments.
- Vom Käufer werden nur Name und Kundennummer übergeben, keine Anschrift.

---

## 7. Datenmodell (`ZugferdDaten`)

### 7.1 Hauptfelder

| Feld | Typ | Beschreibung |
|---|---|---|
| `geschaeftsdokumentart` | `String` | Dokumenttyp (Rechnung, Gutschrift, Auftragsbestätigung etc.) |
| `typeCode` | `String` | Roher UNTDID-1001-Code aus der XML |
| `rechnungsnummer` | `String` | Rechnungsnummer |
| `rechnungsdatum` | `LocalDate` | Rechnungsdatum |
| `faelligkeitsdatum` | `LocalDate` | Fälligkeitsdatum |
| `betrag` | `BigDecimal` | Bruttobetrag |
| `betragNetto` | `BigDecimal` | Nettobetrag |
| `mwstSatz` | `BigDecimal` | MwSt-Satz (Extractor: Anteil 0.19, Dokument-Editor: Prozent 19 – der Erstell-Service versteht beides) |
| `kundenName` | `String` | Kundenname |
| `kundennummer` | `String` | Kundennummer |
| `email`, `anrede` | `String` | Kontaktangaben |
| `bestellnummer` | `String` | Eigene Bestellnummer |
| `referenzNummer` | `String` | Referenz des Lieferanten |
| `bereitsGezahlt` | `Boolean` | Laut XML bereits bezahlt |
| `skontoProzent` | `BigDecimal` | Skonto-Prozentsatz |
| `skontoTage` | `Integer` | Skontotage |
| `nettoTage` | `Integer` | Netto-Zahlungsziel |
| `referenzDokumentId`, `mahnstufe` | `Long`, `String` | Für hochgeladene Mahnungen |
| `artikelPositionen` | `List<ZugferdArtikelPosition>` | Einzelpositionen |

---

## 8. API-Referenz

| Methode | Pfad | Beschreibung |
|---|---|---|
| `GET` | `/api/dokument-generator/zugferd-pruefung` | Firmendaten für E-Rechnung prüfen (204 oder 422) |
| `POST` | `/api/dokument-generator/zugferd-pdf` | ZUGFeRD-PDF aus dem Dokument-Editor erzeugen |
| `POST` | `/api/anfragen/zugferd/extract` | ZUGFeRD-Daten aus PDF extrahieren |
| `POST` | `/api/anfragen/zugferd/extract-ai` | KI-basierte Extraktion aus PDF |
| `POST` | `/api/anfragen/{anfrageID}/zugferd` | ZUGFeRD-PDF aus hochgeladenem PDF + Daten an einer Anfrage ablegen |
| `POST` | `/api/projekte/zugferd/extract` | ZUGFeRD-Daten aus PDF extrahieren (Projekt-Kontext) |
| `POST` | `/api/projekte/zugferd/extract-ai` | KI-basierte Extraktion (Projekt-Kontext) |
| `POST` | `/api/projekte/{projektID}/zugferd` | ZUGFeRD-PDF aus hochgeladenem PDF + Daten an einem Projekt ablegen |

Die Endpunkte unter `/api/anfragen` und `/api/projekte` stammen aus dem früheren Upload-Weg und werden von der PC-Oberfläche derzeit nicht aufgerufen. Die frühere Basis `/api/angebote` gibt es nicht mehr.

---

## 9. Services-Übersicht

| Service | Verantwortung |
|---|---|
| `ZugferdErstellService` | ZUGFeRD-Hybrid-PDF erstellen (Mustang-Bibliothek), Firmendaten prüfen |
| `ZugferdExtractorService` | ZUGFeRD-Daten aus eingehenden PDFs extrahieren |
| `ZugferdConverterService` | `ZugferdDaten` aus einem Projekt-Offenen-Posten bauen (derzeit ohne Aufrufer) |
| `GeminiDokumentAnalyseService` | Auslese-Kette ZUGFeRD → XML → KI für Lieferanten-Dokumente |
| `PdfAiExtractorService` | PDF-Inhaltsextraktion per KI (Gemini, Ausweichlösung Ollama) |
| `RechnungPdfService` | Erzeugt das sichtbare PDF, in das das XML eingebettet wird |
