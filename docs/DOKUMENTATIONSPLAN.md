# Dokumentationsplan – Kalkulationsprogramm

## Übersicht

Dieser Plan definiert alle fehlenden Dokumentationen, priorisiert nach Dringlichkeit.
Bestehende Dokumente: `BUSINESS_CASES.md`, `email-system-refactoring.md`, `ZEITERFASSUNG_WORKFLOWS.md`.

---

## 📋 Dokumentationsstruktur (Ziel)

```
docs/
├── DOKUMENTATIONSPLAN.md          ← Dieser Plan (Tracking)
├── BUSINESS_CASES.md              ✅ Vorhanden
├── email-system-refactoring.md    ✅ Vorhanden
├── ZEITERFASSUNG_WORKFLOWS.md     ✅ Vorhanden
│
├── RECHNUNGSWESEN.md              ✅ Vorhanden – Prio 1
├── GOBD_COMPLIANCE.md             ✅ Vorhanden – Prio 1
├── DOKUMENTEN_LIFECYCLE.md         ✅ Vorhanden – Prio 1
├── ZAHLUNGSVERKEHR.md             ✅ Vorhanden – Prio 2
├── LIEFERANTEN_DOKUMENTEN.md      ✅ Vorhanden – Prio 2
├── ZUGFERD_E_INVOICING.md         ✅ Vorhanden – Prio 2
├── BESTELLWESEN.md                ✅ Vorhanden – Prio 3
├── ANGEBOTSWESEN.md               ✅ Vorhanden – Prio 3
├── MONATS_SALDO_CACHING.md        ✅ Vorhanden – Prio 3
├── API_REFERENZ.md                ✅ Vorhanden – Prio 3
└── ARCHITEKTUR_UEBERSICHT.md     ✅ Vorhanden – Prio 4
```

---

## 🔴 Priorität 1 – GoBD & Rechnungswesen (Pflichtdokumentation)

### 1. RECHNUNGSWESEN.md
> Kompletter Rechnungsprozess von Erstellung bis Versand

**Inhalte:**
- [x] Dokumenttypen (`AusgangsGeschaeftsDokumentTyp`): ANGEBOT, AUFTRAGSBESTAETIGUNG, RECHNUNG, TEILRECHNUNG, ABSCHLAGSRECHNUNG, SCHLUSSRECHNUNG, GUTSCHRIFT, STORNO
- [x] Automatische Nummernvergabe: Format `YYYY/MM/NNNNN` (z.B. "2026/03/00001"), Counter-Logik (`AusgangsGeschaeftsDokumentCounter`)
- [x] Dokumentkette: Angebot → AB → Abschlagsrechnungen (1, 2, 3…) → Schlussrechnung
- [x] Abschlagsnummer-Logik (automatisches Hochzählen via `abschlagsNummer`)
- [x] Umwandlung zwischen Dokumenttypen (Angebot → AB → Rechnung), Content-Vererbung
- [x] MwSt-Berechnung: Netto × (1 + mwstSatz) = Brutto
- [x] Rechnungsadresse-Override (pro Dokument abweichend vom Kundenstamm)
- [x] PDF-Generierung (`RechnungPdfService`): Inhalt-Rendering, ZUGFeRD-Einbettung
- [x] Rechnungsübersicht: Monats-/Jahresgruppierung, Summenberechnung
- [x] Positionen-JSON: Struktur und Felder der Rechnungspositionen

### 2. GOBD_COMPLIANCE.md
> Grundsätze ordnungsmäßiger Buchführung – Umsetzung im System

**Inhalte:**
- [x] **Löschverbot**: Keine Geschäftsdokumente dürfen gelöscht werden – stattdessen Storno-Verfahren
- [x] **Storno-Prozess**: Neues STORNO-Dokument wird erstellt → Original erhält `storniert=true` + `storniertAm` → Beide Dokumente bleiben erhalten → Audit-Trail vollständig
- [x] **Buchungssperre**: `gebucht=true` macht Dokument unveränderlich (nach Export/Versand)
- [x] **Unveränderbarkeit**: Gebuchte Rechnungen können nicht bearbeitet werden, nur storniert
- [x] **Audit-Trail für Zeitbuchungen**:
  - Jede Änderung erzeugt `ZeitbuchungAudit`-Snapshot (vollständige Kopie des Datensatzes)
  - Versionsnummer wird hochgezählt (1 = Ersterfassung, 2+ = Änderungen)
  - `AuditAktion`: ERSTELLT, GEAENDERT, STORNIERT
  - Pflichtfeld `aenderungsgrund` – Begründung für jede Änderung
  - Erfassung von `geaendertVon` (wer), `geaendertAm` (wann), `geaendertVia` (MOBILE_APP, DESKTOP, etc.)
- [x] **Zeitkonto-Korrekturen**: Eigener Audit-Trail via `ZeitkontoKorrekturAudit`
- [x] **Idempotenz**: `idempotencyKey` (UUID) bei Zeitbuchungen verhindert Doppelerfassungen bei Offline-Sync
- [x] **Nachvollziehbarkeit**: Jeder Datensatz kann vollständig rekonstruiert werden (lückenlose Versionshistorie)
- [x] **Aufbewahrungsfristen**: Empfehlungen (10 Jahre für Rechnungen, 6 Jahre für Geschäftsbriefe nach §257 HGB / §147 AO)
- [x] **Nummerierung**: Lückenlose, fortlaufende Rechnungsnummern pro Monat
- [x] **Datenintegrität**: Bezug auf Vorgänger-Dokument via `vorgaengerId` FK sichert Kette

### 3. DOKUMENTEN_LIFECYCLE.md
> Lebenszyklus aller Geschäftsdokumente im System

**Inhalte:**
- [x] **Ausgangs-Dokumentenkette** (Flowchart):
  ```
  Angebot → Auftragsbestätigung → Abschlagsrechnung(en) → Schlussrechnung
                                                              ↓
                                                         Export (gebucht=true) → gesperrt
                                                              ↓
                                                     [Optional] Storno → Storno-Dokument
  ```
- [x] **Eingangs-Dokumentenkette** (Lieferant):
  ```
  E-Mail-Anhang / Manueller Upload
       ↓
  LieferantDokument (Dateicontainer)
       ↓
  LieferantGeschaeftsdokument (extrahierte Metadaten)
       ↓
  Geschaeftsdokument (zentrale Buchungseinheit)
       ↓
  Zahlung(en) → Offener Posten bis vollständig bezahlt
  ```
- [x] Status-Übergänge pro Dokumenttyp
- [x] Verknüpfung zwischen Dokumenten (`verknuepfteDokumente` M:M): Rechnung ↔ Lieferschein ↔ AB ↔ Angebot
- [x] Projekt-Zuordnung und Mehrprojekt-Aufteilung (`LieferantDokumentProjektAnteil`: Prozentsatz pro Projekt)
- [x] Lagerbestellung-Flag (kein Projekt erforderlich)
- [x] Vorgänger/Nachfolger-Kette (FK `vorgaengerDokument` + `nachfolgerDokumente`)

---

## 🟡 Priorität 2 – Zahlungsverkehr & Lieferanten

### 4. ZAHLUNGSVERKEHR.md
> Zahlungsverfolgung, Offene Posten, Mahnwesen

**Inhalte:**
- [x] **Zahlungs-Entity** (`Zahlung`): Datum, Betrag, Zahlungsart (Überweisung, Bar, PayPal, etc.), Verwendungszweck
- [x] **Teilzahlungen**: Mehrere Zahlungen pro Dokument möglich
- [x] **Offener Betrag**: `betragBrutto - Summe(Zahlungen)` → Automatische Berechnung
- [x] **Offene Posten-Übersicht**: Endpoint `GET /api/offene-posten/eingang`
- [x] **Abteilungs-Berechtigungen**: Abt. 3 (Büro) sieht alle, Abt. 2 (Buchhaltung) nur genehmigte
- [x] **Mahnwesen** (`Mahnstufe`-Enum):
  - Stufe 1: ZAHLUNGSERINNERUNG (Zahlungserinnerung)
  - Stufe 2: ERSTE_MAHNUNG (1. Mahnung)
  - Stufe 3: ZWEITE_MAHNUNG (2. Mahnung)
- [x] Eskalationsprozess und Mahnstufen-Tracking auf `ProjektGeschaeftsDokument`
- [x] Skonto-Verwaltung: `skontoTage`, `skontoProzent`, `mitSkonto`-Flag bei Lieferantenrechnungen

### 5. LIEFERANTEN_DOKUMENTEN.md
> Eingangsrechnungen, KI-Erkennung, Freigabeprozess

**Inhalte:**
- [x] **Datei-Handling**: Referenz auf `EmailAttachment` (keine Datei-Duplikation) oder manueller Upload
- [x] **KI-Extraktion**: ZUGFeRD → XML → Gemini-AI-Fallback (mit Confidence-Score)
- [x] **Datenquelle-Enum**: ZUGFERD, XML, AI – Transparenz über Herkunft der Metadaten
- [x] **Verifizierungs-Flag**: `verifiziert` – manuelle Bestätigung der KI-Daten
- [x] **Genehmigung**: `genehmigt`-Flag (Freigabe durch Abt. 3)
- [x] **Projekt-Aufteilung**: Prozentuale Zuordnung auf mehrere Projekte
- [x] **Rekursive Verknüpfung**: M:M `verknuepfteDokumente` (Rechnung ↔ Lieferschein ↔ AB)
- [x] **Zahlungsbedingungen**: Nettotage, Skontotage, Skontoprozent, Liefertermin

### 6. ZUGFERD_E_INVOICING.md
> Elektronische Rechnungsstellung nach EU-Norm

**Inhalte:**
- [x] **Ausgehend** (`ZugferdErstellService`): Hybrid-PDF mit eingebettetem XML erstellen (Mustang-Bibliothek)
- [x] **Eingehend** (`ZugferdExtractorService`): XML-Parsing → Bestellnummer, Referenznummer, etc. extrahieren
- [x] **Konvertierung** (`ZugferdConverterService`): Format-Umwandlung
- [x] **Fallback-Kette**: ZUGFeRD → XML → Dateiname-Parsing → AI-OCR
- [x] **Feldmapping**: Dokumentnummer → Invoice Number, Datum → Issue Date, Kundenname → Buyer
- [x] **Gesetzliche Anforderungen**: E-Rechnungspflicht ab 2025 (B2G), schrittweise B2B

---

## 🟢 Priorität 3 – Geschäftsprozesse

### 7. BESTELLWESEN.md
> Einkauf, Bestellungen, PDF-Generierung

**Inhalte:**
- [x] **Offene Bestellungen**: Unbestellte Artikel pro Projekt (`bestellt=false`)
- [x] **Gruppierung**: Nach Lieferant + Projekt
- [x] **Preisberechnung nach Verrechnungseinheit**: Kilogramm (Preis × kg), Laufende Meter, Quadratmeter, Stück
- [x] **Bestellvorgang**: `setBestellt()` → Berechnung Endpreis, Markierung als bestellt
- [x] **Bestellungs-PDF** (`BestellungPdfService`): Generierung gruppiert nach Lieferant/Projekt
- [x] **Materialkosten-Tracking**: Erfassung und Zuordnung zu Projekten

### 8. ANGEBOTSWESEN.md
> Angebotserstellung, Kundenmanagement, Konversionsrate

**Inhalte:**
- [x] **Angebot-Entity**: Bauvorhaben, Betrag, Anlegedatum, Bild-URL (Hero-Image für UI-Karten)
- [x] **Kunden-E-Mails**: ElementCollection für persistente E-Mail-Liste pro Angebot
- [x] **Notizen & Bilder**: AngebotNotiz mit Bild-Support (AngebotNotizBild)
- [x] **Abschluss**: `abgeschlossen`-Flag
- [x] **E-Mail-Backfill**: `EmailAddressChangedEvent` für nachträgliche Zuordnung
- [x] **Konversion zum Projekt**: Vom Angebot zum Projekt → Auftragsbestätigung → Rechnung

### 9. MONATS_SALDO_CACHING.md
> Performance-Optimierung für monatliche Zeitkonto-Salden

**Inhalte:**
- [x] **Zweck**: Performance-Cache, kein rechtlich bindendes Dokument
- [x] **Komponenten**: Ist-Stunden, Soll-Stunden, Abwesenheits-Stunden, Feiertags-Stunden, Korrektur-Stunden
- [x] **Caching-Strategie**:
  - Aktueller/Zukünftiger Monat → immer live berechnen
  - Vergangener Monat + Cache gültig → Cache verwenden
  - Vergangener Monat + Cache ungültig → Neuberechnung, speichern
- [x] **Invalidierung**: `gueltig=false` bei Quelldaten-Änderung → automatische Neuberechnung bei nächster Abfrage
- [x] **Warmup** (`MonatsSaldoWarmupService`): Vorberechnung beim Start

### 10. API_REFERENZ.md
> REST-Endpoint-Übersicht für alle Module

**Inhalte:**
- [x] **AusgangsGeschaeftsDokument**: CRUD, Typ-Konversion, Storno, Export
- [x] **Rechnungsübersicht**: Monats-/Jahresgruppierung, PDF-Export
- [x] **Geschaeftsdokument**: CRUD, Abschluss-Info
- [x] **Offene Posten**: Eingang mit Abteilungs-Filter
- [x] **Bestellungen**: Offene Items, Bestellmarkierung
- [x] **Zeiterfassung**: Buchungen, Korrekturen, Audit-Abfragen
- [x] **E-Mail**: Import, Zuordnung, Signatur-Verwaltung
- [x] **Authentifizierung & Berechtigungen**: Abteilungsbasierte Zugriffssteuerung

---

## 🔵 Priorität 4 – Architektur

### 11. ARCHITEKTUR_UEBERSICHT.md
> Technische Architektur und Designentscheidungen

**Inhalte:**
- [x] **Tech-Stack**: Spring Boot, JPA/Hibernate, MySQL, React (Vite), Tailwind CSS
- [x] **Modul-Struktur**: Controller → Service → Repository → Domain
- [x] **Frontend-Architektur**: `react-pc-frontend/` (Haupt-UI), `react-textbausteine/` (Text-Editor), `react-zeiterfassung/` (Zeiterfassung)
- [x] **Design-Patterns**:
  - Audit-Trail-Pattern (Immutability via Snapshots)
  - Dokumentketten-Pattern (Vorgänger-FK + Nachfolger)
  - Performance-Caching mit Smart-Invalidierung (MonatsSaldo)
  - Datei-Deduplizierung (LieferantDokument → EmailAttachment FK)
  - Enum-basiertes State Management
- [x] **Externe Integrationen**: Gemini AI, IMAP E-Mail-Import, ZUGFeRD/Mustang
- [x] **Datenbank-Migrationen**: Flyway-Versionierung

---

## 📅 Empfohlene Reihenfolge

| # | Dokument | Grund | Status |
|---|----------|-------|--------|
| 1 | GOBD_COMPLIANCE.md | **Rechtlich erforderlich** – Nachweis der GoBD-Konformität | ✅ Fertig |
| 2 | RECHNUNGSWESEN.md | Kernprozess, muss dokumentiert sein für Prüfungen | ✅ Fertig |
| 3 | DOKUMENTEN_LIFECYCLE.md | Grundlage für Verständnis aller Dokumentflüsse | ✅ Fertig |
| 4 | ZAHLUNGSVERKEHR.md | Direkt mit Rechnungswesen verknüpft | ✅ Fertig |
| 5 | LIEFERANTEN_DOKUMENTEN.md | Eingangsseite des Rechnungswesens | ✅ Fertig |
| 6 | ZUGFERD_E_INVOICING.md | Gesetzliche Pflicht ab 2025 | ✅ Fertig |
| 7 | BESTELLWESEN.md | Kerngeschäftsprozess | ✅ Fertig |
| 8 | ANGEBOTSWESEN.md | Kerngeschäftsprozess | ✅ Fertig |
| 9 | MONATS_SALDO_CACHING.md | Technische Doku für Wartung | ✅ Fertig |
| 10 | API_REFERENZ.md | Entwickler-Dokumentation | ✅ Fertig |
| 11 | ARCHITEKTUR_UEBERSICHT.md | Onboarding & Wartung | ✅ Fertig |

---

## ✅ Bereits dokumentiert

| Dokument | Inhalt |
|----------|--------|
| BUSINESS_CASES.md | Geschäftsnutzen aller Module (Kalkulation, Controlling, KI-Erkennung, E-Mail) |
| email-system-refactoring.md | E-Mail-System Refactoring-Dokumentation |
| ZEITERFASSUNG_WORKFLOWS.md | Zeiterfassungs-Workflows und -Prozesse |
| RECHNUNGSWESEN.md | Kompletter Rechnungsprozess von Erstellung bis Versand |
| GOBD_COMPLIANCE.md | GoBD-Konformität: Unveränderbarkeit, Löschverbot, Audit-Trail |
| DOKUMENTEN_LIFECYCLE.md | Lebenszyklus aller Geschäftsdokumente (Eingang & Ausgang) |
| ZAHLUNGSVERKEHR.md | Zahlungsverfolgung, Offene Posten, Mahnwesen |
| LIEFERANTEN_DOKUMENTEN.md | Eingangsrechnungen, KI-Erkennung, Freigabeprozess |
| ZUGFERD_E_INVOICING.md | Elektronische Rechnungsstellung nach EU-Norm |
| BESTELLWESEN.md | Einkauf, Bestellungen, PDF-Generierung |
| ANGEBOTSWESEN.md | Angebotserstellung, Kundenmanagement, Konversionsrate |
| MONATS_SALDO_CACHING.md | Performance-Optimierung für monatliche Zeitkonto-Salden |
| API_REFERENZ.md | REST-Endpoint-Übersicht für alle Module |
| ARCHITEKTUR_UEBERSICHT.md | Technische Architektur und Designentscheidungen |
