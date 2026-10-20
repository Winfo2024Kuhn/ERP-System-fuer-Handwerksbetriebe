# Dokumenten-Lifecycle – Lebenszyklus aller Geschäftsdokumente

## Übersicht

Dieses Dokument beschreibt den vollständigen Lebenszyklus aller Geschäftsdokumente im System – sowohl ausgehende Dokumente (Angebote, Auftragsbestätigungen, Rechnungen etc.) als auch eingehende Dokumente (Lieferantenrechnungen, Lieferscheine etc.).

---

## 1. Ausgangs-Dokumentenkette

### 1.1 Vollständiger Lifecycle

```
                 ┌──────────────────────────────────────────────┐
                 │               ENTWURF                        │
                 │  gebucht=false, versandDatum=null            │
                 │  → bearbeitbar, löschbar (mit Begründung)    │
                 └──────────────────────┬───────────────────────┘
                                        │
             ┌──────────────────────────┼─────────────────────────────┐
             ▼                          ▼                             ▼
     ┌──────────────┐        ┌────────────────────┐        ┌────────────────────┐
     │   ANGEBOT    │        │  NACHTRAGSANGEBOT  │        │ eigenständige AB   │
     │ (max. 1 pro  │        │ (nur im Projekt,   │        │ oder Rechnung –    │
     │  Vorgang)    │        │  beliebig viele)   │        │ nur als erstes Dok.│
     └──────┬───────┘        └─────────┬──────────┘        └────────────────────┘
            │                          │
            ▼                          ▼
     ┌──────────────┐           ┌──────────────┐
     │     AB       │           │     AB       │      ← höchstens eine aktive AB
     │ (Auftrags-   │           │              │        je Angebot/Nachtragsangebot
     │ bestätigung) │           └──────────────┘
     └──────┬───────┘
            │   Rechnungen hängen am untersten Dokument des Vorgangs
   ┌────────┼────────┬─────────────┬───────────────────┐
   ▼        ▼        ▼             ▼                   ▼
┌──────────┐┌──────────┐┌──────────────┐ ┌──────────────┐
│Abschlags-││Abschlags-││ Teilrechnung │ │   RECHNUNG   │
│rechnung 1││rechnung 2││              │ │ ("Einfache   │
└──────────┘└──────────┘└──────────────┘ │  Rechnung")  │
      │           │            │          └──────────────┘
      └───────────┼────────────┘
                  ▼
          ┌────────────────┐
          │ SCHLUSSRECHNUNG│
          │ (zieht alle    │
          │  vorherigen    │
          │  Rechnungen ab)│
          └────────────────┘
```

### 1.2 Status-Übergänge

**Rechnungstypen** (Rechnung, Teil-, Abschlags-, Schlussrechnung, Gutschrift):

```
ENTWURF ──[Drucken & Buchen / PDF / E-Mail]──► GEBUCHT (gesperrt)
   │                                              │
   │                                         [E-Mail]
   │                                              ▼
   │                                          VERSENDET
   │                                     (gebucht + versandDatum)
   │                                              │
   ├──[Löschen]                              [Stornieren]
   │  (nur Entwürfe, mit Begründung)              │
   ▼                                              ▼
GELÖSCHT                                      STORNIERT (storniert=true)
(aus DB entfernt, Audit-Eintrag bleibt)           │
                                                  └──► STORNO-Dokument erstellt
                                                       (gebucht=true, eigene Nummer)
```

**Angebot, Nachtragsangebot, Auftragsbestätigung:** Diese Dokumente werden **nie gebucht**. Sie bleiben auch nach dem Versand bearbeitbar, bis der Kunde das Angebot **digital annimmt** – dann sind Angebot und die automatisch erzeugte AB **verbindlich** (`digitalAngenommen = true`) und gesperrt.

```
ENTWURF ──[E-Mail]──► VERSENDET (versandDatum gesetzt, weiter bearbeitbar, nicht mehr löschbar)
                          │
                   [digitale Annahme]
                          ▼
                     VERBINDLICH (gesperrt)
```

### 1.3 Status-Matrix pro Dokumenttyp

| Typ | Wird gebucht? | Gesperrt nach … | Stornierbar? | Löschbar? |
|---|---|---|---|---|
| `ANGEBOT` | ❌ nie | digitaler Annahme | ❌ | ✅ solange nicht versendet und nicht digital angenommen |
| `NACHTRAGSANGEBOT` | ❌ nie | digitaler Annahme | ❌ | ✅ solange nicht versendet und nicht digital angenommen |
| `AUFTRAGSBESTAETIGUNG` | ❌ nie | digitaler Annahme (Auto-AB) | ❌ | ✅ solange nicht versendet und nicht digital angenommen |
| `RECHNUNG` | ✅ | Buchung | ✅ (nur gebucht) | ✅ nur als Entwurf |
| `TEILRECHNUNG` | ✅ | Buchung | ✅ (nur gebucht) | ✅ nur als Entwurf |
| `ABSCHLAGSRECHNUNG` | ✅ | Buchung | ✅ (nur gebucht) | ✅ nur als Entwurf |
| `SCHLUSSRECHNUNG` | ✅ | Buchung | ✅ (nur gebucht) | ✅ nur als Entwurf |
| `GUTSCHRIFT` | ✅ | Buchung | ✅ (nur gebucht) | ✅ nur als Entwurf |
| `STORNO` | sofort gebucht | immer | ❌ | ❌ nie |

Stornierte Dokumente sind immer gesperrt und nie löschbar. Eine **Gutschrift** entsteht nur über „Gutschrift erstellen“ an einer gebuchten, nicht stornierten Rechnung; ihr Storno erhöht den offenen Betrag der Rechnung wieder um die Gutschrift. Wird eine **Abschlagsrechnung** storniert, werden zugehörige gebuchte Schlussrechnungen am selben Vorgänger automatisch mitstorniert; ein Schlussrechnungs-Entwurf bleibt stehen. Beides meldet die Oberfläche nach dem Stornieren als Hinweis.

### 1.4 Anzeige in der Oberfläche

Im Reiter **„Geschäftsdokumente“** von Projekt und Anfrage erscheinen die Dokumente als Baum (Basisdokument → Folgedokumente). Jedes Dokument trägt eine Plakette:

| Plakette | Bedeutung |
|---|---|
| **Entwurf** | Noch nicht gebucht, nicht verbindlich – bearbeitbar |
| **Gebucht** | Rechnung ist festgeschrieben (Schloss-Symbol) |
| **Verbindlich** | Vom Kunden digital angenommen (Schloss-Symbol) |
| **Storniert** | Durch eine Stornorechnung aufgehoben |

Zusätzlich zeigen Angebote den Freigabe-Status („Angenommen · Datum“, „Wartet auf Kunde“, „Link abgelaufen“, „Link zurückgezogen“).

**Aktionsmenü eines Dokuments (Klick auf das Dokument):**
- „Öffnen (neuer Tab)“ – öffnet den Dokument-Editor
- „Auftragsbestätigung“ (bei Angebot/Nachtragsangebot, nur im Projekt)
- „Rechnung erstellen“ (bei Angebot/Nachtragsangebot/AB, nur im Projekt)
- „Stornieren“ (bei gebuchten, nicht stornierten Rechnungen) – erzeugt automatisch eine Stornorechnung
- „Verlauf anzeigen“ – Audit-Verlauf für die Steuerprüfung
- „Löschen“ (nur Entwürfe) – Dialog „Dokument löschen“ mit Pflicht-Grund: Tippfehler / Erfassungsfehler, Versehentlich doppelt erstellt, Falscher Kunde / Empfänger, Falscher Betrag, Falsche Leistungspositionen, Auftrag wurde storniert (Entwurf), Testdaten / Demo-Eintrag, Sonstiges (dann Freitext mit mindestens 5 Zeichen Pflicht)

### 1.5 Gleichzeitiges Bearbeiten (Datensatz-Sperre)

Damit zwei Personen nicht gleichzeitig dasselbe Dokument überschreiben:

- Wer ein gespeichertes Dokument im Dokument-Editor öffnet, bekommt automatisch die **Bearbeitungssperre** (`DatensatzLock`, Typ `AUSGANG`), sofern sie frei ist. Kollegen sehen dann: „*Name* bearbeitet das gerade — Sie sehen den aktuellen Stand.“ und lesen nur mit.
- Mit **„Fertig“** geben Sie die Sperre frei, mit **„Bearbeiten“** holen Sie sie wieder. Beim Schließen des Editors (X) wird gespeichert und freigegeben; schließt der Browser den Tab nicht selbst, erscheint ein Hinweis, dass Sie den Tab jetzt schließen können.
- Nach **5 Minuten ohne Aktivität** wird automatisch gespeichert und freigegeben. 60 Sekunden vorher erscheint: „Wird in 60 Sekunden freigegeben — bewegen Sie die Maus, um weiterzuarbeiten.“
- Zusätzlich trägt jedes Dokument eine **Versionsnummer**. Hat inzwischen jemand anderes gespeichert, wird der Speicherversuch abgelehnt (HTTP 409): „Jemand anders hat … gerade gespeichert. Ihre Änderungen wurden nicht übernommen — bitte neu laden.“
- Speichern (`PUT /api/ausgangs-dokumente/{id}`) ist nur mit gehaltener Sperre möglich.

Im Editor lassen sich die letzten bis zu **20 Änderungsschritte** rückgängig machen bzw. wiederholen (Strg+Z / Strg+Y). Gesperrte Dokumente bieten das nicht an.

---

## 2. Eingangs-Dokumentenkette (Lieferanten)

### 2.1 Vollständiger Lifecycle

```
┌──────────────────────────────────────────────────────┐
│                DOKUMENTQUELLE                        │
│                                                      │
│  ┌─────────────┐   ┌───────────────┐  ┌───────────┐  │
│  │ E-Mail-     │   │  Manueller    │  │ Handy-    │  │
│  │ Anhang      │   │  Upload       │  │ Scan      │  │
│  │(EmailAttach-│   │(Datei direkt) │  │ (Beleg)   │  │
│  │ ment-FK)    │   │               │  │           │  │
│  └──────┬──────┘   └───────┬───────┘  └─────┬─────┘  │
│         │                  │                │        │
└─────────┼──────────────────┼────────────────┼────────┘
          │                  │                │
          ▼                  ▼                ▼
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
                  │ [ZUGFeRD / XML / KI-Analyse]
                  ▼
   ┌──────────────────────────────┐
   │  LieferantGeschaeftsdokument │
   │  (extrahierte Metadaten)     │
   │                              │
   │  ● Dokumentnummer, Datum     │
   │  ● Beträge (Netto/Brutto)    │
   │  ● MwSt-Satz                 │
   │  ● Bestellnummer, Referenz   │
   │  ● Liefertermin              │
   │  ● Zahlungsbedingungen       │
   │  ● KI-Confidence-Score       │
   │  ● Datenquelle               │
   │  ● Verifizierungs-Flag       │
   └──────────────┬───────────────┘
                  │
                  │ [Genehmigung]
                  ▼
   ┌──────────────────────────────┐
   │  Genehmigt (genehmigt=true)  │
   │  → sichtbar für alle mit     │
   │    "Genehmigte Rechnungen    │
   │     sehen"                   │
   └──────────────┬───────────────┘
                  │
                  │ [Häkchen "Bezahlt"]
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
| `BELEG` | Buchhaltungs-Belege (Kasse, Privatentnahme, Bank, Einmalbelege) – steuert die Rechte für das Beleg-Modul |
| `WERKSTOFFZEUGNIS` | Abnahmeprüfzeugnis/Werkszeugnis zum Lieferschein (Werkstoff, Charge) |

Welche Abteilung welche Typen sehen bzw. scannen darf, wird unter Einstellungen → **Berechtigungen** festgelegt.

### 2.3 Datenextraktions-Kette

```
Datei-Eingang
    │
    ├─[1] PDF mit ZUGFeRD-XML? ──► ZugferdExtractorService
    │                              datenquelle = "ZUGFERD"
    │                              verifiziert = true, aiConfidence = 1.0
    │
    ├─[2] XML-Datei (XRechnung/ZUGFeRD-XML)? ──► XML-Parsing
    │                              datenquelle = "XML", aiConfidence = 1.0
    │
    └─[3] KI-Analyse (Google Gemini) ──► OCR + Extraktion (PDF, JPG, PNG)
                                   verifiziert = false
                                   aiConfidence = 0.0–1.0 (von der KI geliefert)
                                   manuellePruefungErforderlich = true,
                                     wenn Pflichtfelder fehlen (vorher ein
                                     zweiter Versuch mit dem Pro-Modell, falls
                                     konfiguriert)
                                   bei Fehlern: datenquelle = "AI_FAILED" /
                                     "AI_PARSE_FAILED" / "AI_ERROR"
```

Der Dateiname dient nur als Hinweis auf den Dokumenttyp (z. B. „Lieferschein“ im Namen), nicht als eigene Extraktionsstufe.

### 2.4 Datei-Handling

**Kernprinzip: Keine Datei-Duplikation.**

- E-Mail-Anhänge: `LieferantDokument` referenziert `EmailAttachment` über FK (`attachment_id`) – die Datei wird **nicht** kopiert
- Handy-Scans: `LieferantDokument` verweist auf den zugrunde liegenden `Beleg` (`beleg_id`); Datei und Vorschau kommen aus dem Beleg
- Manuelle Uploads: Dateien werden im Upload-Verzeichnis mit UUID-basiertem Namen gespeichert (`gespeicherterDateiname`)
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
   └── Kostenstelle "Lager": 10% → Anteil vom Nettobetrag
```

### 3.2 Zuordnungsmethoden

| Methode | Feld | Beschreibung |
|---|---|---|
| **Prozentual** | `prozent` | Prozentsatz (0–100) |
| **Absolutbetrag** | `absoluterBetrag` | Fester Betrag (hat Vorrang vor Prozent) |

Die Berechnung erfolgt in `berechneAnteil(netto, brutto)` und wird in `berechneterBetrag` gespeichert:
- Wenn `absoluterBetrag` gesetzt → direkt verwenden
- Projekt-Zuordnung: `Brutto × prozent / 100`
- Kostenstellen-Zuordnung: `Netto × prozent / 100` (fehlt der Nettobetrag, wird Brutto genommen)

### 3.3 Kostenstreckung

Für periodische Kosten (z. B. Zertifizierung alle 4 Jahre) unterstützt die Entity eine **Kostenstreckung**:

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

Details zur Bestellkette: `BESTELLWESEN.md` und `LIEFERANTEN_DOKUMENTEN.md`.

### 4.3 Mahnungen: Verweis auf die Rechnung

Mahnungen (`ProjektGeschaeftsdokument` mit `mahnstufe`) verweisen über `referenzDokument` auf die Original-Rechnung; die Rechnung kennt ihre Mahnungen über `mahnungen`. Im Reiter „Geschäftsdokumente“ werden sie als virtuelle Einträge (Typ `ZAHLUNGSERINNERUNG`, `ERSTE_MAHNUNG`, `ZWEITE_MAHNUNG`) unter der Rechnung angezeigt.

---

## 5. Zahlungsverfolgung

### 5.1 Ausgangs-Dokumente (Offene Posten)

Gebuchte Ausgangsrechnungen (Rechnung, Teil-, Abschlags-, Schlussrechnung) eines Projekts erzeugen automatisch einen `ProjektGeschaeftsdokument`-Eintrag:

```
Rechnung gebucht
   └→ ProjektGeschaeftsdokument erstellt
       ├── dokumentid = Dokumentnummer
       ├── bruttoBetrag = Bruttobetrag
       ├── bezahlt = false
       ├── rechnungsdatum = Dokumentdatum
       ├── faelligkeitsdatum = datum + zahlungszielTage
       └── systemGeneriert = true
```

**Status-Übergänge:**
```
Offen (bezahlt=false) ──[Häkchen "Bezahlt" in Offene Posten]──► Bezahlt (bezahlt=true)
        │
   [Stornierung]
        │
        ▼
Bezahlt (automatisch bei Storno des Ausgangsdokuments)
```

Es gibt **keine Teilzahlungen** für Ausgangsrechnungen: Der Status ist nur „bezahlt“ oder „nicht bezahlt“.

### 5.2 Eingangs-Dokumente (Lieferantenrechnungen)

Lieferantenrechnungen (`LieferantGeschaeftsdokument`) verwalten den Zahlungsstatus direkt:

| Feld | Beschreibung |
|---|---|
| `bezahlt` | `true` wenn bezahlt |
| `bezahltAm` | Datum der Zahlung |
| `tatsaechlichGezahlt` | Gezahlter Betrag (nach Skonto-Abzug) |
| `mitSkonto` | `true` wenn Skonto genutzt wurde |
| `bereitsGezahlt` | Laut Rechnung bereits bezahlt (von KI oder ZUGFeRD erkannt, z. B. Vorauskasse) |
| `zahlungsart` | Z. B. Vorauskasse, SEPA-Lastschrift, Kreditkarte |

### 5.3 Zahlungsbedingungen

| Feld | Beschreibung |
|---|---|
| `zahlungsziel` | Fälligkeitsdatum |
| `skontoTage` | Tage für Skonto-Abzug |
| `skontoProzent` | Skonto-Prozentsatz (z. B. 2.00 %) |
| `nettoTage` | Zahlungsziel in Tagen (z. B. 30) |

Details zu Skonto und Mahnwesen: `ZAHLUNGSVERKEHR.md`.

---

## 6. Genehmigungsprozess (Eingangs-Dokumente)

### 6.1 Rechte pro Abteilung

Die Rechte werden unter Einstellungen → **Berechtigungen** pro Abteilung vergeben:

| Recht | Wirkung |
|---|---|
| **Rechnungen genehmigen** (`darfRechnungenGenehmigen`) | Sieht alle Eingangsrechnungen und kann sie genehmigen (`genehmigt = true`) |
| **Genehmigte Rechnungen sehen** (`darfRechnungenSehen`) | Sieht nur bereits genehmigte Eingangsrechnungen |
| keines von beiden | Sieht keine Eingangsrechnungen in den Offenen Posten |

Typische Aufteilung: Büro genehmigt, Buchhaltung sieht nur Genehmigtes. Noch nicht genehmigte Rechnungen lassen sich im vollwertigen Bearbeitungsdialog öffnen, genehmigte nur noch in der PDF-Vorschau.

### 6.2 Verifizierungs- und Genehmigungsflags

```
Dokument hochgeladen
   │
   ├─[ZUGFeRD-Daten]──► verifiziert=true, datenquelle="ZUGFERD"
   │
   ├─[XML-Daten]──► datenquelle="XML"
   │
   └─[KI-Analyse]──► verifiziert=false
         │
         ├─[Pflichtfelder vollständig]──► manuellePruefungErforderlich=false
         │
         └─[Daten unvollständig]──► manuellePruefungErforderlich=true
                                      → Mitarbeiter muss nachbearbeiten

Unabhängig davon:
   └─[Genehmigung durch berechtigte Abteilung]──► genehmigt=true
                              → sichtbar für "Genehmigte Rechnungen sehen"
```

---

## 7. Zusammenfassung: Entitäten-Übersicht

### 7.1 Ausgangs-Dokumente

| Entity | Zweck |
|---|---|
| `AusgangsGeschaeftsDokument` | Zentrale Entity für alle ausgehenden Geschäftsdokumente |
| `AusgangsGeschaeftsDokumentTyp` | Enum der Dokumenttypen (9 echte Typen + 3 virtuelle Mahn-Typen) |
| `AusgangsGeschaeftsDokumentCounter` | Counter-Tabelle für Nummernvergabe (ein Zähler pro Monat) |
| `AusgangsGeschaeftsDokumentAudit` | Unveränderlicher Audit-Trail mit Hash-Kette |
| `DokumentFreigabe` | Digitale Annahme von Angeboten |
| `DatensatzLock` | Bearbeitungssperre (Typ `AUSGANG` bzw. `EINGANG`) |
| `ProjektGeschaeftsdokument` | Offene-Posten-Eintrag und Mahnungen (extends ProjektDokument) |
| `Mahnstufe` | Enum für Mahnwesen (3 Stufen) |

### 7.2 Eingangs-Dokumente

| Entity | Zweck |
|---|---|
| `LieferantDokument` | Dateicontainer für Lieferanten-Dokumente |
| `LieferantGeschaeftsdokument` | Extrahierte Metadaten (1:1 mit LieferantDokument) |
| `LieferantDokumentProjektAnteil` | Prozentuale Projekt-/Kostenstellen-Zuordnung |
| `LieferantDokumentTyp` | Enum der Dokumenttypen (8 Typen) |

### 7.3 Projekt-Kontext

| Entity | Zweck |
|---|---|
| `Dokumenttyp` | Enum mit Labels (12 Typen inkl. Nachtragsangebot, Mahnungen und Stornorechnung), u. a. für die Zuordnung von Textbausteinen |

### 7.4 Services

| Service | Verantwortung |
|---|---|
| `AusgangsGeschaeftsDokumentService` | Anlegen, Speichern, Buchung, Stornierung, Löschung, Abrechnungsverlauf, Preise |
| `AusgangsGeschaeftsDokumentAuditService` | Audit-Einträge mit Hash-Kette |
| `DokumentFreigabeService` | Digitale Annahme, Auto-Projekt, Auto-Auftragsbestätigung |
| `RechnungPdfService` | PDF-Generierung mit OpenPDF |
| `ZugferdErstellService` | ZUGFeRD-PDF erstellen |
| `ZugferdExtractorService` | ZUGFeRD-Daten aus PDFs extrahieren |
| `GeminiDokumentAnalyseService` | Analyse von Lieferanten-Dokumenten (ZUGFeRD → XML → KI) |
| `AutoMahnVersandService` | Automatische Zahlungserinnerungen und Mahnungen |
| `ZugferdConverterService` | Baut `ZugferdDaten` aus einem Projekt-Offenen-Posten (derzeit ohne Aufrufer) |
