# Dokumenten-Lifecycle – Lebenszyklus aller Geschäftsdokumente

## Übersicht

Dieses Dokument beschreibt den vollständigen Lebenszyklus aller Geschäftsdokumente im System – sowohl ausgehende Dokumente (Rechnungen, Angebote etc.) als auch eingehende Dokumente (Lieferantenrechnungen, Lieferscheine etc.).

---

## 1. Ausgangs-Dokumentenkette

### 1.1 Vollständiger Lifecycle

```
                          ┌─────────────────────────────────────────────┐
                          │            ENTWURF (Draft)                  │
                          │  gebucht=false, versandDatum=null           │
                          │  → Bearbeitbar, löschbar (mit Begründung)  │
                          └──────────────┬──────────────────────────────┘
                                         │
                    ┌────────────────────┼────────────────────┐
                    ▼                    ▼                    ▼
            ┌──────────────┐   ┌──────────────────┐   ┌────────────────┐
            │   ANGEBOT    │   │      direkte      │   │   aus Vorlage  │
            │              │   │    Erstellung      │   │   erstellt     │
            └──────┬───────┘   └────────┬─────────┘   └───────┬────────┘
                   │                    │                      │
                   ▼                    │                      │
            ┌──────────────┐            │                      │
            │     AB       │◄───────────┘                      │
            │ (Auftrags-   │◄──────────────────────────────────┘
            │ bestätigung) │
            └──────┬───────┘
                   │
          ┌────────┼────────┬───────────────────┐
          ▼        ▼        ▼                   ▼
    ┌──────────┐ ┌──────────┐ ┌──────────┐  ┌──────────────┐
    │Abschlags-│ │Abschlags-│ │Abschlags-│  │   RECHNUNG   │
    │rechnung 1│ │rechnung 2│ │rechnung 3│  │  (Einzel)    │
    └──────────┘ └──────────┘ └──────────┘  └──────────────┘
          │            │            │
          └────────────┼────────────┘
                       ▼
                ┌──────────────┐
                │SCHLUSSRECHNUNG│
                │(verrechnet   │
                │ alle Abschläge)│
                └──────────────┘
```

### 1.2 Status-Übergänge

```
ENTWURF ──[Buchen]──────► GEBUCHT
   │                         │
   │                    [E-Mail senden]
   │                         │
   │                         ▼
   │                     VERSENDET
   │                    (gebucht + versandDatum)
   │                         │
   │                    [Stornieren]
   │                         │
   ├──[Löschen]              ▼
   │  (nur Entwürfe)     STORNIERT
   ▼                    (storniert=true)
GELÖSCHT                     │
(aus DB entfernt)            └──► STORNO-Dokument erstellt
                                  (gebucht=true, eigene Nummer)
```

### 1.3 Status-Matrix pro Dokumenttyp

| Typ | Entwurf → Gebucht | Gebucht → Gesperrt? | Stornierbar? | Löschbar (Entwurf)? |
|---|---|---|---|---|
| `ANGEBOT` | ✅ | ❌ (bleibt bearbeitbar) | ❌ | ✅ |
| `AUFTRAGSBESTAETIGUNG` | ✅ | ❌ (bleibt bearbeitbar) | ❌ | ✅ |
| `RECHNUNG` | ✅ | ✅ Gesperrt | ✅ | ✅ |
| `TEILRECHNUNG` | ✅ | ✅ Gesperrt | ✅ | ✅ |
| `ABSCHLAGSRECHNUNG` | ✅ | ✅ Gesperrt | ✅ | ✅ |
| `SCHLUSSRECHNUNG` | ✅ | ✅ Gesperrt | ✅ | ✅ |
| `GUTSCHRIFT` | ✅ | ✅ Gesperrt | ✅ | ✅ |
| `STORNO` | – (sofort gebucht) | ✅ Immer gesperrt | ❌ | ❌ Nie löschbar |

---

## 2. Eingangs-Dokumentenkette (Lieferanten)

### 2.1 Vollständiger Lifecycle

```
┌────────────────────────────────────────┐
│        DOKUMENTQUELLE                  │
│                                        │
│  ┌─────────────┐   ┌───────────────┐  │
│  │ E-Mail-     │   │  Manueller    │  │
│  │ Anhang      │   │  Upload       │  │
│  │(EmailAttach-│   │(Datei direkt) │  │
│  │ ment-FK)    │   │               │  │
│  └──────┬──────┘   └───────┬───────┘  │
│         │                  │          │
└─────────┼──────────────────┼──────────┘
          │                  │
          ▼                  ▼
   ┌──────────────────────────────┐
   │     LieferantDokument        │
   │  (Dateicontainer)            │
   │                              │
   │  ● Lieferant-Zuordnung       │
   │  ● Dokumenttyp               │
   │  ● Dateiname (original +     │
   │    gespeichert)              │
   │  ● Upload-Datum & -Person    │
   └──────────────┬───────────────┘
                  │
                  │ [KI-Analyse / ZUGFeRD-Extraktion]
                  ▼
   ┌──────────────────────────────┐
   │  LieferantGeschaeftsdokument │
   │  (extrahierte Metadaten)     │
   │                              │
   │  ● Dokumentnummer, Datum     │
   │  ● Beträge (Netto/Brutto)   │
   │  ● MwSt-Satz                │
   │  ● Bestellnummer, Referenz   │
   │  ● Liefertermin              │
   │  ● Zahlungsbedingungen       │
   │  ● KI-Confidence-Score       │
   │  ● Datenquelle (ZUGFERD/     │
   │    XML/AI)                   │
   │  ● Verifizierungs-Flag       │
   └──────────────┬───────────────┘
                  │
                  │ [Genehmigung durch Büro]
                  ▼
   ┌──────────────────────────────┐
   │  Genehmigt (genehmigt=true)  │
   │  → Sichtbar für Buchhaltung  │
   └──────────────┬───────────────┘
                  │
                  │ [Zahlungseingang]
                  ▼
   ┌──────────────────────────────┐
   │  Bezahlt (bezahlt=true)      │
   │  bezahltAm, tatsaechlich-    │
   │  Gezahlt (nach Skonto)       │
   └──────────────────────────────┘
```

### 2.2 Lieferant-Dokumenttypen (`LieferantDokumentTyp`)

| Typ | Beschreibung |
|---|---|
| `ANGEBOT` | Angebot vom Lieferanten |
| `AUFTRAGSBESTAETIGUNG` | Auftragsbestätigung des Lieferanten |
| `LIEFERSCHEIN` | Lieferschein zur Warenlieferung |
| `RECHNUNG` | Eingangsrechnung des Lieferanten |
| `GUTSCHRIFT` | Gutschrift vom Lieferanten |
| `SONSTIG` | Nicht-Geschäftsdokumente (Katalog, Info etc.) |

### 2.3 Datenextraktions-Kette

```
PDF-Eingang
    │
    ├─[1] ZUGFeRD-XML vorhanden? ──► ZugferdExtractorService
    │                                 datenquelle = "ZUGFERD"
    │                                 verifiziert = true
    │
    ├─[2] XML-Metadaten vorhanden? ──► XML-Parsing
    │                                  datenquelle = "XML"
    │                                  verifiziert = true
    │
    ├─[3] Dateiname-Parsing ──────► Regex auf Dateinamen
    │
    └─[4] KI-Analyse (Gemini AI) ──► AI-OCR + Extraktion
                                      datenquelle = "AI"
                                      verifiziert = false
                                      aiConfidence = 0.0–1.0
                                      manuellePruefungErforderlich = true/false
```

### 2.4 Datei-Handling

**Kernprinzip: Keine Datei-Duplikation.**

- E-Mail-Anhänge: `LieferantDokument` referenziert `EmailAttachment` über FK (`attachment_id`) – die Datei wird **nicht** kopiert
- Manuelle Uploads: Dateien werden im `uploads/`-Verzeichnis mit UUID-basiertem Namen gespeichert (`gespeicherterDateiname`)
- Effektiver Dateiname: `getEffektiverDateiname()` priorisiert Attachment, dann Fallback auf `originalDateiname`

---

## 3. Projekt-Zuordnung und Kostenverteilung

### 3.1 LieferantDokumentProjektAnteil

Jedes Lieferanten-Dokument kann **mehreren Projekten oder Kostenstellen** prozentual zugeordnet werden:

```
Lieferantenrechnung (Brutto: 10.000 €)
   │
   ├── Projekt A: 60% → 6.000 €
   ├── Projekt B: 30% → 3.000 €
   └── Kostenstelle "Lager": 10% → 1.000 €
```

### 3.2 Zuordnungsmethoden

| Methode | Feld | Beschreibung |
|---|---|---|
| **Prozentual** | `prozent` | Prozentsatz (0–100) vom Gesamtbetrag |
| **Absolutbetrag** | `absoluterBetrag` | Fester Betrag (überschreibt Prozent) |

Die Berechnung erfolgt in `berechneAnteil()`:
- Wenn `absoluterBetrag` gesetzt → direkt verwenden
- Sonst: `gesamtBetrag × prozent / 100`

### 3.3 Kostenstreckung

Für periodische Kosten (z.B. Zertifizierung alle 4 Jahre) unterstützt die Entity eine **Kostenstreckung**:

| Feld | Beschreibung |
|---|---|
| `streckungJahre` | Über wie viele Jahre verteilen (Default: 1) |
| `streckungStartJahr` | Ab welchem Jahr gilt die Streckung |

**Beispiel:** Zertifizierung 4.000 € alle 4 Jahre:
- `streckungJahre = 4`, `streckungStartJahr = 2026`
- `getJahresanteil()` → 1.000 € pro Jahr
- `isStreckungAktivFuerJahr(2027)` → `true`
- `isStreckungAktivFuerJahr(2030)` → `false`

### 3.4 Lagerbestellung-Flag

Dokumente können als `lagerbestellung = true` markiert werden. In diesem Fall ist **keine Projekt-Zuordnung erforderlich**.

---

## 4. Verknüpfung zwischen Dokumenten

### 4.1 Ausgangs-Dokumente: Lineare Kette

Ausgangs-Geschäftsdokumente verwenden eine **lineare Vorgänger/Nachfolger-Kette** über FK:

```java
@ManyToOne vorgaenger        // → Vorgänger-Dokument
@OneToMany nachfolger        // ← Alle Nachfolger
```

### 4.2 Lieferanten-Dokumente: M:M-Verknüpfung

Lieferanten-Dokumente verwenden eine **Many-to-Many-Verknüpfung** für flexiblere Ketten:

```java
@ManyToMany verknuepfteDokumente    // → Verknüpfte Dokumente
@ManyToMany verknuepftVon           // ← Dokumente, die dieses verknüpft haben
```

**Join-Tabelle:** `lieferant_dokument_verknuepfung`

Beispiel:
```
Lieferanten-Rechnung ←──► Lieferschein ←──► Auftragsbestätigung ←──► Angebot
```

### 4.3 Eingangs-Dokumente: Geschaeftsdokument-Kette

Die Entity `Geschaeftsdokument` (im Projekt-Kontext) nutzt ebenfalls eine lineare FK-Kette:

```java
@ManyToOne vorgaengerDokument
@OneToMany nachfolgerDokumente
```

---

## 5. Zahlungsverfolgung

### 5.1 Ausgangs-Dokumente (Offene Posten)

Gebuchte Ausgangsrechnungen erzeugen automatisch einen `ProjektGeschaeftsdokument`-Eintrag:

```
Rechnung gebucht
   └→ ProjektGeschaeftsdokument erstellt
       ├── dokumentid = Dokumentnummer
       ├── bruttoBetrag = Bruttobetrag
       ├── bezahlt = false
       ├── rechnungsdatum = Dokumentdatum
       └── faelligkeitsdatum = datum + zahlungszielTage
```

**Status-Übergänge:**
```
Offen (bezahlt=false) ──[Zahlung]──► Bezahlt (bezahlt=true)
        │
   [Stornierung]
        │
        ▼
Bezahlt (automatisch bei Storno des Ausgangsdokuments)
```

### 5.2 Eingangs-Dokumente (Lieferantenrechnungen)

Lieferantenrechnungen (`LieferantGeschaeftsdokument`) verwalten den Zahlungsstatus direkt:

| Feld | Beschreibung |
|---|---|
| `bezahlt` | `true` wenn bezahlt |
| `bezahltAm` | Datum der Zahlung |
| `tatsaechlichGezahlt` | Gezahlter Betrag (nach Skonto-Abzug) |
| `mitSkonto` | `true` wenn Skonto genutzt wurde |
| `bereitsGezahlt` | Von KI erkannt (z.B. Vorauskasse) |

### 5.3 Zahlungsbedingungen

| Feld | Beschreibung |
|---|---|
| `zahlungsziel` | Fälligkeitsdatum |
| `skontoTage` | Tage für Skonto-Abzug |
| `skontoProzent` | Skonto-Prozentsatz (z.B. 2.00%) |
| `nettoTage` | Zahlungsziel in Tagen (z.B. 30) |

### 5.4 Geschaeftsdokument: Zahlungen und offener Betrag

Die Entity `Geschaeftsdokument` unterstützt **Teilzahlungen** über die `Zahlung`-Entity:

```java
List<Zahlung> zahlungen;

BigDecimal getSummeZahlungen()   // Summe aller Zahlungen
BigDecimal getOffenerBetrag()    // betragBrutto - Summe(Zahlungen)
boolean istBezahlt()             // offenerBetrag <= 0
```

Jede `Zahlung` enthält:
- `zahlungsdatum` – Datum der Zahlung
- `betrag` – Gezahlter Betrag
- `zahlungsart` – Art (Überweisung, Bar, PayPal etc.)
- `verwendungszweck` – Verwendungszweck
- `notiz` – Optionale Notiz

---

## 6. Genehmigungsprozess (Eingangs-Dokumente)

### 6.1 Abteilungs-basiert

| Abteilung | Rechte |
|---|---|
| **Abt. 3 (Büro)** | Sieht alle Dokumente, kann genehmigen (`genehmigt = true`) |
| **Abt. 2 (Buchhaltung)** | Sieht nur genehmigte Dokumente |

### 6.2 Verifizierungs- und Genehmigungsflags

```
Dokument hochgeladen
   │
   ▼
KI-Analyse (verifiziert=false, datenquelle="AI")
   │
   ├─[Daten korrekt]──► Verifiziert (verifiziert=true)
   │
   ├─[Daten unvollständig]──► manuellePruefungErforderlich=true
   │                            → User muss nachbearbeiten
   │
   └─[ZUGFeRD-Daten]──► Automatisch verifiziert (verifiziert=true, datenquelle="ZUGFERD")
   
Verifiziert
   │
   └─[Büro-Genehmigung]──► Genehmigt (genehmigt=true)
                              → Sichtbar für Buchhaltung
                              → Zahlungsverfolgung aktiv
```

---

## 7. Zusammenfassung: Entitäten-Übersicht

### 7.1 Ausgangs-Dokumente

| Entity | Zweck |
|---|---|
| `AusgangsGeschaeftsDokument` | Zentrale Entity für alle ausgehenden Geschäftsdokumente |
| `AusgangsGeschaeftsDokumentTyp` | Enum der Dokumenttypen (8 Typen) |
| `AusgangsGeschaeftsDokumentCounter` | Counter-Tabelle für Nummernvergabe |
| `ProjektGeschaeftsdokument` | Offene-Posten-Eintrag (extends ProjektDokument) |
| `Mahnstufe` | Enum für Mahnwesen (3 Stufen) |

### 7.2 Eingangs-Dokumente

| Entity | Zweck |
|---|---|
| `LieferantDokument` | Dateicontainer für Lieferanten-Dokumente |
| `LieferantGeschaeftsdokument` | Extrahierte Metadaten (1:1 mit LieferantDokument) |
| `LieferantDokumentProjektAnteil` | Prozentuale Projekt-/Kostenstellen-Zuordnung |
| `LieferantDokumentTyp` | Enum der Dokumenttypen (6 Typen) |

### 7.3 Projekt-Kontext

| Entity | Zweck |
|---|---|
| `Dokumenttyp` | Enum mit Labels (10 Typen inkl. Mahnungen) |

### 7.4 Services

| Service | Verantwortung |
|---|---|
| `AusgangsGeschaeftsDokumentService` | CRUD, Buchung, Stornierung, Löschung |
| `RechnungPdfService` | PDF-Generierung mit iText |
| `ZugferdErstellService` | ZUGFeRD-PDF erstellen |
| `ZugferdExtractorService` | ZUGFeRD-Daten aus PDFs extrahieren |
| `ZugferdConverterService` | Daten nach ZugferdDaten konvertieren |
