# ZUGFeRD & E-Invoicing – Elektronische Rechnungsstellung

## Übersicht

Dieses Dokument beschreibt die Implementierung der elektronischen Rechnungsstellung (E-Invoicing) im Kalkulationsprogramm nach dem ZUGFeRD-Standard. Das System unterstützt sowohl das **Erstellen** als auch das **Lesen** von ZUGFeRD-konformen Hybrid-PDFs.

---

## 1. Ausgehende Rechnungen (`ZugferdErstellService`)

### 1.1 Zweck

Erstellt ZUGFeRD-konforme Hybrid-PDFs, die sowohl menschenlesbares PDF als auch maschinenlesbares XML enthalten.

### 1.2 Technologie

- **Mustang-Bibliothek** (ZUGFeRD Java-Library)
- Klassen: `ZUGFeRDExporterFromA3`
- Profil: ZUGFeRD 2.0 / Factur-X

### 1.3 Ablauf

```
Ausgangsrechnung (AusgangsGeschaeftsDokument)
    │
    ▼
RechnungPdfService.erzeugePdf()
    │  → Erzeugt Standard-PDF (iText)
    │
    ▼
ZugferdErstellService.erzeuge(originalPdfPath, zugferdDaten)
    │  → Bettet ZUGFeRD-XML in das PDF ein
    │  → Erzeugt temporäre Datei mit Hybrid-PDF
    │
    ▼
ZUGFeRD-Hybrid-PDF (PDF/A-3 + eingebettetes XML)
```

### 1.4 Methode

```java
Path erzeuge(Path originalPdfPath, ZugferdDaten daten)
```

**Parameter:**
- `originalPdfPath` – Pfad zum bereits generierten PDF
- `daten` – `ZugferdDaten`-DTO mit allen Rechnungsinformationen

**Rückgabe:** Pfad zur temporären Datei mit dem ZUGFeRD-Hybrid-PDF

---

## 2. Eingehende Rechnungen (`ZugferdExtractorService`)

### 2.1 Zweck

Extrahiert strukturierte Rechnungsdaten aus ZUGFeRD-PDFs (eingebettetes XML).

### 2.2 Methode

```java
ZugferdDaten extract(Path pdfPath, String originalFilename)
```

### 2.3 Extrahierte Felder

| Feld | ZUGFeRD-Mapping | Beschreibung |
|---|---|---|
| `rechnungsnummer` | Invoice Number | Rechnungsnummer |
| `rechnungsdatum` | Issue Date | Rechnungsdatum |
| `faelligkeitsdatum` | Due Date | Fälligkeitsdatum |
| `betrag` | Grand Total Amount | Bruttobetrag |
| `betragNetto` | Tax Basis Total Amount | Nettobetrag |
| `mwstSatz` | Tax Percent | MwSt-Satz |
| `kundenName` | Buyer Name | Kundenname |
| `kundennummer` | Buyer ID | Kundennummer |
| `bestellnummer` | Buyer Order Reference | Eigene Bestellnummer |
| `referenzNummer` | Payment Reference | Referenznummer |
| `skontoProzent` | Cash Discount Percent | Skonto-Prozentsatz |
| `skontoTage` | Cash Discount Days | Skontotage |
| `nettoTage` | Net Payment Days | Netto-Zahlungsziel |
| `geschaeftsdokumentart` | Document Type | Dokumenttyp |
| `artikelPositionen` | Line Items | Einzelne Rechnungspositionen |

### 2.4 Artikelpositionen (`ZugferdArtikelPosition`)

Jede Rechnungsposition enthält:

| Feld | Beschreibung |
|---|---|
| `externeArtikelnummer` | Artikelnummer des Lieferanten |
| `bezeichnung` | Artikelbezeichnung |
| `menge` | Menge |
| `mengeneinheit` | Einheit (Stück, kg, m etc.) |
| `einzelpreis` | Preis pro Einheit |
| `preiseinheit` | Preiseinheit |

---

## 3. Konvertierung (`ZugferdConverterService`)

### 3.1 Zweck

Konvertiert interne Datenstrukturen in das `ZugferdDaten`-Format für den Export.

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
| `projekt.getKunde().getName()` | `kundenName` |
| `projekt.getKunde().getKundennummer()` | `kundennummer` |

---

## 4. Fallback-Kette bei Eingangsrechnungen

### 4.1 Priorisierte Extraktionsmethoden

```
[1] ZUGFeRD-XML in PDF vorhanden?
     │
     ├── JA → ZugferdExtractorService.extract()
     │         datenquelle = "ZUGFERD"
     │         verifiziert = true
     │         (Höchste Zuverlässigkeit)
     │
     └── NEIN ↓

[2] XML-Metadaten vorhanden?
     │
     ├── JA → XML-Parsing
     │         datenquelle = "XML"
     │         verifiziert = true
     │
     └── NEIN ↓

[3] Dateiname-Parsing
     │
     └── Regex auf Dateinamen für Dokumentnummer/Datum
         ↓

[4] KI-Analyse (Gemini AI)
     │
     └── GeminiDokumentAnalyseService
           datenquelle = "AI"
           verifiziert = false
           aiConfidence = 0.0–1.0
           manuellePruefungErforderlich = true/false
```

### 4.2 Vergleich der Methoden

| Methode | Zuverlässigkeit | Auto-Verifiziert | Geschwindigkeit |
|---|---|---|---|
| ZUGFeRD | ★★★★★ | ✅ Ja | ⚡ Schnell |
| XML | ★★★★☆ | ✅ Ja | ⚡ Schnell |
| Dateiname-Parsing | ★★☆☆☆ | ❌ Nein | ⚡ Schnell |
| Gemini AI (OCR) | ★★★☆☆ | ❌ Nein | 🐢 Langsam (API-Call) |

---

## 5. Feldmapping

### 5.1 Ausgehend (Intern → ZUGFeRD)

| Internes Feld | ZUGFeRD-Feld |
|---|---|
| Dokumentnummer | Invoice Number |
| Datum | Issue Date |
| Fälligkeitsdatum | Due Date |
| Bruttobetrag | Grand Total Amount |
| Nettobetrag | Tax Basis Total Amount |
| MwSt-Satz | Tax Percent |
| Kundenname | Buyer Name |
| Kundennummer | Buyer ID |
| Bestellnummer | Buyer Order Reference |

### 5.2 Eingehend (ZUGFeRD → Intern)

| ZUGFeRD-Feld | Internes Feld |
|---|---|
| Invoice Number | `dokumentNummer` |
| Issue Date | `dokumentDatum` |
| Due Date | `zahlungsziel` |
| Grand Total Amount | `betragBrutto` |
| Tax Basis Total Amount | `betragNetto` |
| Tax Percent | `mwstSatz` |
| Buyer Order Reference | `bestellnummer` |
| Payment Reference | `referenzNummer` |
| Cash Discount Percent | `skontoProzent` |
| Cash Discount Days | `skontoTage` |
| Net Payment Days | `nettoTage` |

---

## 6. Gesetzliche Anforderungen

### 6.1 E-Rechnungspflicht

| Zeitraum | Pflicht | Bereich |
|---|---|---|
| Ab 01.01.2025 | E-Rechnungspflicht | **B2G** (Business-to-Government) |
| Ab 01.01.2025 | Empfangspflicht | **B2B** (alle Unternehmen müssen E-Rechnungen empfangen können) |
| Ab 01.01.2027 | Versandpflicht | **B2B** (Unternehmen > 800.000 € Umsatz) |
| Ab 01.01.2028 | Versandpflicht | **B2B** (alle Unternehmen) |

### 6.2 Unterstützte Formate

| Format | Beschreibung | Status |
|---|---|---|
| **ZUGFeRD 2.0** | Hybrid-PDF (PDF/A-3 + XML) | ✅ Unterstützt |
| **Factur-X** | Französisch-deutsches Pendant zu ZUGFeRD | ✅ Unterstützt (gleiche Basis) |
| **XRechnung** | Reines XML-Format (kein PDF) | ⚠️ XML-Parsing unterstützt |

### 6.3 Anforderungen an E-Rechnungen

- **Strukturierte Daten**: Maschinenlesbares XML mit Pflichtfeldern
- **Integrität**: PDF/A-3 Standard für Langzeitarchivierung
- **Vollständigkeit**: Rechnungsnummer, Datum, Beträge, MwSt, Empfänger
- **Normenkonformität**: EN 16931 (europäische Norm für elektronische Rechnungen)

---

## 7. Datenmodell (`ZugferdDaten`)

### 7.1 Hauptfelder

| Feld | Typ | Beschreibung |
|---|---|---|
| `geschaeftsdokumentart` | `String` | Dokumenttyp (Rechnung, Gutschrift etc.) |
| `rechnungsnummer` | `String` | Rechnungsnummer |
| `rechnungsdatum` | `LocalDate` | Rechnungsdatum |
| `faelligkeitsdatum` | `LocalDate` | Fälligkeitsdatum |
| `betrag` | `BigDecimal` | Bruttobetrag |
| `betragNetto` | `BigDecimal` | Nettobetrag |
| `mwstSatz` | `BigDecimal` | MwSt-Satz |
| `kundenName` | `String` | Kundenname |
| `kundennummer` | `String` | Kundennummer |
| `bestellnummer` | `String` | Eigene Bestellnummer |
| `referenzNummer` | `String` | Zahlungsreferenz |
| `skontoProzent` | `BigDecimal` | Skonto-Prozentsatz |
| `skontoTage` | `Integer` | Skontotage |
| `nettoTage` | `Integer` | Netto-Zahlungsziel |
| `artikelPositionen` | `List<ZugferdArtikelPosition>` | Einzelpositionen |

---

## 8. API-Referenz

| Methode | Pfad | Beschreibung |
|---|---|---|
| `POST` | `/api/angebote/zugferd/extract` | ZUGFeRD-Daten aus PDF extrahieren |
| `POST` | `/api/angebote/zugferd/extract-ai` | KI-basierte Extraktion aus PDF |
| `POST` | `/api/angebote/{angebotID}/zugferd` | ZUGFeRD-PDF für Angebot erstellen |
| `POST` | `/api/dokument-generator/zugferd-pdf` | ZUGFeRD-PDF über Dokumentgenerator erstellen |

---

## 9. Services-Übersicht

| Service | Verantwortung |
|---|---|
| `ZugferdExtractorService` | ZUGFeRD-Daten aus eingehenden PDFs extrahieren |
| `ZugferdConverterService` | Interne Daten in ZUGFeRD-Format konvertieren |
| `ZugferdErstellService` | ZUGFeRD-Hybrid-PDF erstellen (Mustang-Bibliothek) |
| `GeminiDokumentAnalyseService` | KI-Fallback für nicht-ZUGFeRD-Dokumente |
| `PdfAiExtractorService` | PDF-Inhaltsextraktion via KI |
