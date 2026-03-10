# Lieferanten-Dokumente – Eingangsrechnungen, KI-Erkennung, Freigabeprozess

## Übersicht

Dieses Dokument beschreibt den vollständigen Prozess der Lieferanten-Dokumentenverwaltung – vom Eingang über die automatische Datenextraktion (ZUGFeRD, XML, KI) bis zur Genehmigung und Zahlungsverfolgung.

---

## 1. Datei-Handling

### 1.1 Kernprinzip: Keine Datei-Duplikation

Lieferanten-Dokumente können aus zwei Quellen stammen:

| Quelle | Handling |
|---|---|
| **E-Mail-Anhang** | `LieferantDokument` referenziert `EmailAttachment` über FK (`attachment_id`) – die Datei wird **nicht** kopiert |
| **Manueller Upload** | Dateien werden im `uploads/`-Verzeichnis mit UUID-basiertem Namen gespeichert (`gespeicherterDateiname`) |

### 1.2 Effektiver Dateiname

Die Methode `getEffektiverDateiname()` priorisiert:
1. Anhang vom E-Mail-Attachment (`attachment.getDateiname()`)
2. Fallback auf `originalDateiname`

### 1.3 Entity `LieferantDokument`

| Feld | Typ | Beschreibung |
|---|---|---|
| `id` | `Long` | Primärschlüssel |
| `lieferant` | `Lieferant` (FK) | Zugehöriger Lieferant |
| `attachment` | `EmailAttachment` (FK) | Optionaler E-Mail-Anhang (keine Datei-Duplikation) |
| `typ` | `LieferantDokumentTyp` (Enum) | Art des Dokuments |
| `originalDateiname` | `String` | Originaler Dateiname |
| `gespeicherterDateiname` | `String` | UUID-basierter gespeicherter Dateiname |
| `uploadDatum` | `LocalDateTime` | Datum und Uhrzeit des Uploads |
| `uploadedBy` | `Mitarbeiter` (FK) | Hochladender Mitarbeiter |
| `geschaeftsdaten` | `LieferantGeschaeftsdokument` (1:1) | Extrahierte Geschäftsdaten |
| `projektAnteile` | `Set<LieferantDokumentProjektAnteil>` (1:N) | Prozentuale Projekt-Zuordnungen |
| `verknuepfteDokumente` | `Set<LieferantDokument>` (M:N) | Verknüpfte Dokumente |

---

## 2. Lieferant-Dokumenttypen (`LieferantDokumentTyp`)

| Typ | Beschreibung |
|---|---|
| `ANGEBOT` | Angebot vom Lieferanten |
| `AUFTRAGSBESTAETIGUNG` | Auftragsbestätigung des Lieferanten |
| `LIEFERSCHEIN` | Lieferschein zur Warenlieferung |
| `RECHNUNG` | Eingangsrechnung des Lieferanten |
| `GUTSCHRIFT` | Gutschrift vom Lieferanten |
| `SONSTIG` | Nicht-Geschäftsdokumente (Katalog, Info etc.) |

---

## 3. KI-Extraktion und Datenquellen

### 3.1 Extraktions-Kette (Fallback-Strategie)

```
PDF-Eingang
    │
    ├─[1] ZUGFeRD-XML vorhanden? ──► ZugferdExtractorService
    │                                 datenquelle = "ZUGFERD"
    │                                 verifiziert = true (strukturierte Daten)
    │
    ├─[2] XML-Metadaten vorhanden? ──► XML-Parsing
    │                                  datenquelle = "XML"
    │                                  verifiziert = true
    │
    ├─[3] Dateiname-Parsing ──────► Regex auf Dateinamen
    │
    └─[4] KI-Analyse (Gemini AI) ──► GeminiDokumentAnalyseService
                                      datenquelle = "AI"
                                      verifiziert = false
                                      aiConfidence = 0.0–1.0
                                      manuellePruefungErforderlich = true/false
```

### 3.2 Datenquelle-Enum

| Datenquelle | Beschreibung | Auto-Verifiziert? |
|---|---|---|
| `ZUGFERD` | Strukturierte ZUGFeRD-XML-Daten aus dem PDF | ✅ Ja |
| `XML` | Externe XML-Metadaten | ✅ Ja |
| `AI` | KI-basierte OCR-Extraktion (Gemini AI) | ❌ Nein – manuelle Prüfung |

### 3.3 KI-Analyse (Gemini AI)

Der `GeminiDokumentAnalyseService` extrahiert:
- Dokumentnummer, Datum, Beträge (Netto/Brutto)
- MwSt-Satz
- Bestellnummer und Referenznummer
- Liefertermin
- Zahlungsbedingungen (Nettotage, Skontotage, Skontoprozent)

Die Ergebnisse werden mit einem **Confidence-Score** (0.0–1.0) versehen. Das Roh-JSON der KI-Antwort wird im Feld `aiRawJson` gespeichert.

---

## 4. Entity `LieferantGeschaeftsdokument`

### 4.1 Felder

| Feld | Typ | Beschreibung |
|---|---|---|
| `id` | `Long` | Primärschlüssel |
| `dokument` | `LieferantDokument` (FK, 1:1) | Zugehöriges Dokument |
| `dokumentNummer` | `String` | Rechnungs-/Dokumentnummer |
| `dokumentDatum` | `LocalDate` | Datum des Dokuments |
| `betragNetto` | `BigDecimal` | Nettobetrag |
| `betragBrutto` | `BigDecimal` | Bruttobetrag |
| `mwstSatz` | `BigDecimal` | MwSt-Satz |
| `liefertermin` | `LocalDate` | Liefertermin |
| `bestellnummer` | `String` | Eigene Bestellnummer (Referenz) |
| `referenzNummer` | `String` | Externe Referenznummer |

**KI-Felder:**

| Feld | Typ | Beschreibung |
|---|---|---|
| `aiRawJson` | `String` (LONGTEXT) | Roh-JSON der KI-Analyse |
| `aiConfidence` | `Double` | Confidence-Score (0.0–1.0) |
| `analysiertAm` | `LocalDateTime` | Zeitpunkt der Analyse |
| `datenquelle` | `String` | `ZUGFERD`, `XML` oder `AI` |

**Status-Flags:**

| Feld | Typ | Beschreibung |
|---|---|---|
| `verifiziert` | `Boolean` | Manuelle Bestätigung der extrahierten Daten |
| `genehmigt` | `Boolean` | Freigabe durch Abt. 3 (Büro) |
| `lagerbestellung` | `Boolean` | Lagerbestellung (kein Projekt erforderlich) |

**Zahlungsfelder:**

| Feld | Typ | Beschreibung |
|---|---|---|
| `bezahlt` | `Boolean` | Vollständig bezahlt |
| `bezahltAm` | `LocalDate` | Datum der Zahlung |
| `tatsaechlichGezahlt` | `BigDecimal` | Gezahlter Betrag (nach Skonto) |
| `mitSkonto` | `Boolean` | Skonto genutzt |
| `bereitsGezahlt` | `Boolean` | Von KI als bereits bezahlt erkannt |
| `zahlungsziel` | `LocalDate` | Fälligkeitsdatum |
| `skontoTage` | `Integer` | Tage für Skonto-Abzug |
| `skontoProzent` | `BigDecimal` | Skonto-Prozentsatz |
| `nettoTage` | `Integer` | Zahlungsziel in Tagen |

---

## 5. Verifizierungs-Flag

### 5.1 Zweck

Das `verifiziert`-Flag zeigt an, ob die extrahierten Daten manuell bestätigt wurden.

### 5.2 Automatische Verifizierung

| Datenquelle | Auto-Verifiziert? | Begründung |
|---|---|---|
| `ZUGFERD` | ✅ Ja | Strukturierte, maschinenlesbare Daten |
| `XML` | ✅ Ja | Strukturierte Daten |
| `AI` | ❌ Nein | KI-Ergebnisse erfordern manuelle Prüfung |

### 5.3 Manuelle Verifizierung

Bei KI-extrahierten Daten muss ein Mitarbeiter die Daten prüfen und das `verifiziert`-Flag manuell setzen.

---

## 6. Genehmigungsprozess

### 6.1 Ablauf

```
Dokument hochgeladen / E-Mail empfangen
   │
   ▼
KI-Analyse / ZUGFeRD-Extraktion
   │
   ├─ verifiziert = false (bei AI)
   │  └─► Manuelle Prüfung und Korrektur der Daten
   │       └─► verifiziert = true
   │
   ├─ verifiziert = true (bei ZUGFERD/XML)
   │
   ▼
Genehmigung durch Büro (Abt. 3)
   │
   └─► genehmigt = true
        → Sichtbar für Buchhaltung (Abt. 2)
        → Zahlungsverfolgung aktiv
```

### 6.2 Abteilungsbasierte Sichtbarkeit

| Abteilung | Sichtbarkeit |
|---|---|
| **Abt. 3 (Büro)** | Alle Dokumente – kann genehmigen |
| **Abt. 2 (Buchhaltung)** | Nur genehmigte Dokumente |

---

## 7. Projekt-Aufteilung

### 7.1 Entity `LieferantDokumentProjektAnteil`

Jedes Lieferanten-Dokument kann **mehreren Projekten oder Kostenstellen** prozentual zugeordnet werden.

| Feld | Typ | Beschreibung |
|---|---|---|
| `id` | `Long` | Primärschlüssel |
| `dokument` | `LieferantDokument` (FK) | Zugehöriges Dokument |
| `projekt` | `Projekt` (FK, optional) | Zugeordnetes Projekt |
| `kostenstelle` | `Kostenstelle` (FK, optional) | Zugeordnete Kostenstelle |
| `prozent` | `BigDecimal` | Prozentsatz (0–100) |
| `absoluterBetrag` | `BigDecimal` | Optionaler absoluter Betrag |
| `berechneterBetrag` | `BigDecimal` | Berechneter Anteil |
| `beschreibung` | `String` | Beschreibung der Zuordnung |
| `zugeordnetAm` | `LocalDateTime` | Zeitpunkt der Zuordnung |
| `streckungJahre` | `Integer` | Verteilung über mehrere Jahre (Default: 1) |
| `streckungStartJahr` | `Integer` | Startjahr der Kostenstreckung |

### 7.2 Berechnungslogik

Die Methode `berechneAnteil()`:
- Wenn `absoluterBetrag` gesetzt → direkt verwenden
- Sonst: `gesamtBetrag × prozent / 100`

### 7.3 Kostenstreckung

Für periodische Kosten (z.B. Zertifizierung alle 4 Jahre):

```
Zertifizierung: 4.000 € alle 4 Jahre
  streckungJahre = 4
  streckungStartJahr = 2026

  getJahresanteil()                    → 1.000 € pro Jahr
  isStreckungAktivFuerJahr(2027)       → true
  isStreckungAktivFuerJahr(2030)       → false
```

### 7.4 Beispiel

```
Lieferantenrechnung (Brutto: 10.000 €)
   ├── Projekt A: 60% → 6.000 €
   ├── Projekt B: 30% → 3.000 €
   └── Kostenstelle "Lager": 10% → 1.000 €
```

---

## 8. Rekursive Verknüpfung

### 8.1 M:M-Verknüpfung

Lieferanten-Dokumente verwenden eine **Many-to-Many-Verknüpfung** über die Join-Tabelle `lieferant_dokument_verknuepfung`:

```java
@ManyToMany verknuepfteDokumente    // → Verknüpfte Dokumente
@ManyToMany verknuepftVon           // ← Dokumente, die dieses verknüpft haben
```

### 8.2 Typische Verknüpfungen

```
Lieferanten-Rechnung ←──► Lieferschein ←──► Auftragsbestätigung ←──► Angebot
```

Dies ermöglicht die vollständige Nachverfolgung der Lieferanten-Dokumentenkette.

---

## 9. Zahlungsbedingungen

| Feld | Beschreibung | Beispiel |
|---|---|---|
| `nettoTage` | Zahlungsziel in Tagen | 30 Tage |
| `skontoTage` | Tage für Skonto-Abzug | 10 Tage |
| `skontoProzent` | Skonto-Prozentsatz | 2.00% |
| `liefertermin` | Erwarteter Liefertermin | 2026-04-15 |
| `zahlungsziel` | Fälligkeitsdatum | 2026-04-30 |

---

## 10. API-Referenz

### 10.1 Basis-URL

```
/api/lieferant-dokumente
```

### 10.2 Endpoints

| Methode | Pfad | Beschreibung |
|---|---|---|
| `POST` | `/lieferant/{lieferantId}/reanalyze` | KI-Reanalyse für einen Lieferanten |
| `POST` | `/{dokumentId}/reanalyze` | KI-Reanalyse eines einzelnen Dokuments |
| `POST` | `/relink-all` | Alle E-Mail-Anhänge neu verknüpfen |
| `POST` | `/lieferant/{lieferantId}/relink` | E-Mail-Anhänge für einen Lieferanten neu verknüpfen |
| `POST` | `/process-assigned-emails` | Zugewiesene E-Mails verarbeiten |
| `POST` | `/process-email/{emailId}` | Einzelne E-Mail verarbeiten |
| `POST` | `/lieferant/{lieferantId}/process-emails` | E-Mails eines Lieferanten verarbeiten |
| `GET` | `/duplicates` | Duplikate finden |
| `GET` | `/{dokumentId}/download` | Dokument herunterladen |

---

## 11. Entitäten-Übersicht

| Entity | Zweck |
|---|---|
| `LieferantDokument` | Dateicontainer für Lieferanten-Dokumente |
| `LieferantGeschaeftsdokument` | Extrahierte Metadaten (1:1 mit LieferantDokument) |
| `LieferantDokumentProjektAnteil` | Prozentuale Projekt-/Kostenstellen-Zuordnung |
| `LieferantDokumentTyp` (Enum) | Dokumenttypen: Angebot, AB, Lieferschein, Rechnung, Gutschrift, Sonstig |

| Service | Verantwortung |
|---|---|
| `LieferantDokumentService` | Upload, Berechtigungen, KI-Analyse-Integration |
| `GeminiDokumentAnalyseService` | KI-basierte Dokumentenanalyse (Gemini AI) |
| `PdfAiExtractorService` | PDF-Inhaltsextraktion via KI |
| `ZugferdExtractorService` | ZUGFeRD-Datenextraktion aus PDFs |
