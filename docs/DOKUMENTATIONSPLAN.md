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
├── ZAHLUNGSVERKEHR.md             🔴 Fehlt – Prio 2
├── LIEFERANTEN_DOKUMENTEN.md      🟡 Fehlt – Prio 2
├── ZUGFERD_E_INVOICING.md         🟡 Fehlt – Prio 2
├── BESTELLWESEN.md                🟡 Fehlt – Prio 3
├── ANGEBOTSWESEN.md               🟡 Fehlt – Prio 3
├── MONATS_SALDO_CACHING.md        🟢 Fehlt – Prio 3
├── API_REFERENZ.md                🟢 Fehlt – Prio 3
└── ARCHITEKTUR_UEBERSICHT.md     🟢 Fehlt – Prio 4
```

---

## 🔴 Priorität 1 – GoBD & Rechnungswesen (Pflichtdokumentation)

### 1. RECHNUNGSWESEN.md
> Kompletter Rechnungsprozess von Erstellung bis Versand

**Inhalte:**
- [ ] Dokumenttypen (`AusgangsGeschaeftsDokumentTyp`): ANGEBOT, AUFTRAGSBESTAETIGUNG, RECHNUNG, TEILRECHNUNG, ABSCHLAGSRECHNUNG, SCHLUSSRECHNUNG, GUTSCHRIFT, STORNO
- [ ] Automatische Nummernvergabe: Format `YYYY/MM/NNNNN` (z.B. "2026/03/00001"), Counter-Logik (`AusgangsGeschaeftsDokumentCounter`)
- [ ] Dokumentkette: Angebot → AB → Abschlagsrechnungen (1, 2, 3…) → Schlussrechnung
- [ ] Abschlagsnummer-Logik (automatisches Hochzählen via `abschlagsNummer`)
- [ ] Umwandlung zwischen Dokumenttypen (Angebot → AB → Rechnung), Content-Vererbung
- [ ] MwSt-Berechnung: Netto × (1 + mwstSatz) = Brutto
- [ ] Rechnungsadresse-Override (pro Dokument abweichend vom Kundenstamm)
- [ ] PDF-Generierung (`RechnungPdfService`): Inhalt-Rendering, ZUGFeRD-Einbettung
- [ ] Rechnungsübersicht: Monats-/Jahresgruppierung, Summenberechnung
- [ ] Positionen-JSON: Struktur und Felder der Rechnungspositionen

### 2. GOBD_COMPLIANCE.md
> Grundsätze ordnungsmäßiger Buchführung – Umsetzung im System

**Inhalte:**
- [ ] **Löschverbot**: Keine Geschäftsdokumente dürfen gelöscht werden – stattdessen Storno-Verfahren
- [ ] **Storno-Prozess**: Neues STORNO-Dokument wird erstellt → Original erhält `storniert=true` + `storniertAm` → Beide Dokumente bleiben erhalten → Audit-Trail vollständig
- [ ] **Buchungssperre**: `gebucht=true` macht Dokument unveränderlich (nach Export/Versand)
- [ ] **Unveränderbarkeit**: Gebuchte Rechnungen können nicht bearbeitet werden, nur storniert
- [ ] **Audit-Trail für Zeitbuchungen**:
  - Jede Änderung erzeugt `ZeitbuchungAudit`-Snapshot (vollständige Kopie des Datensatzes)
  - Versionsnummer wird hochgezählt (1 = Ersterfassung, 2+ = Änderungen)
  - `AuditAktion`: ERSTELLT, GEAENDERT, STORNIERT
  - Pflichtfeld `aenderungsgrund` – Begründung für jede Änderung
  - Erfassung von `geaendertVon` (wer), `geaendertAm` (wann), `geaendertVia` (MOBILE_APP, DESKTOP, etc.)
- [ ] **Zeitkonto-Korrekturen**: Eigener Audit-Trail via `ZeitkontoKorrekturAudit`
- [ ] **Idempotenz**: `idempotencyKey` (UUID) bei Zeitbuchungen verhindert Doppelerfassungen bei Offline-Sync
- [ ] **Nachvollziehbarkeit**: Jeder Datensatz kann vollständig rekonstruiert werden (lückenlose Versionshistorie)
- [ ] **Aufbewahrungsfristen**: Empfehlungen (10 Jahre für Rechnungen, 6 Jahre für Geschäftsbriefe nach §257 HGB / §147 AO)
- [ ] **Nummerierung**: Lückenlose, fortlaufende Rechnungsnummern pro Monat
- [ ] **Datenintegrität**: Bezug auf Vorgänger-Dokument via `vorgaengerId` FK sichert Kette

### 3. DOKUMENTEN_LIFECYCLE.md
> Lebenszyklus aller Geschäftsdokumente im System

**Inhalte:**
- [ ] **Ausgangs-Dokumentenkette** (Flowchart):
  ```
  Angebot → Auftragsbestätigung → Abschlagsrechnung(en) → Schlussrechnung
                                                              ↓
                                                         Export (gebucht=true) → gesperrt
                                                              ↓
                                                     [Optional] Storno → Storno-Dokument
  ```
- [ ] **Eingangs-Dokumentenkette** (Lieferant):
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
- [ ] Status-Übergänge pro Dokumenttyp
- [ ] Verknüpfung zwischen Dokumenten (`verknuepfteDokumente` M:M): Rechnung ↔ Lieferschein ↔ AB ↔ Angebot
- [ ] Projekt-Zuordnung und Mehrprojekt-Aufteilung (`LieferantDokumentProjektAnteil`: Prozentsatz pro Projekt)
- [ ] Lagerbestellung-Flag (kein Projekt erforderlich)
- [ ] Vorgänger/Nachfolger-Kette (FK `vorgaengerDokument` + `nachfolgerDokumente`)

---

## 🟡 Priorität 2 – Zahlungsverkehr & Lieferanten

### 4. ZAHLUNGSVERKEHR.md
> Zahlungsverfolgung, Offene Posten, Mahnwesen

**Inhalte:**
- [ ] **Zahlungs-Entity** (`Zahlung`): Datum, Betrag, Zahlungsart (Überweisung, Bar, PayPal, etc.), Verwendungszweck
- [ ] **Teilzahlungen**: Mehrere Zahlungen pro Dokument möglich
- [ ] **Offener Betrag**: `betragBrutto - Summe(Zahlungen)` → Automatische Berechnung
- [ ] **Offene Posten-Übersicht**: Endpoint `GET /api/offene-posten/eingang`
- [ ] **Abteilungs-Berechtigungen**: Abt. 3 (Büro) sieht alle, Abt. 2 (Buchhaltung) nur genehmigte
- [ ] **Mahnwesen** (`Mahnstufe`-Enum):
  - Stufe 1: ZAHLUNGSERINNERUNG (Zahlungserinnerung)
  - Stufe 2: ERSTE_MAHNUNG (1. Mahnung)
  - Stufe 3: ZWEITE_MAHNUNG (2. Mahnung)
- [ ] Eskalationsprozess und Mahnstufen-Tracking auf `ProjektGeschaeftsDokument`
- [ ] Skonto-Verwaltung: `skontoTage`, `skontoProzent`, `mitSkonto`-Flag bei Lieferantenrechnungen

### 5. LIEFERANTEN_DOKUMENTEN.md
> Eingangsrechnungen, KI-Erkennung, Freigabeprozess

**Inhalte:**
- [ ] **Datei-Handling**: Referenz auf `EmailAttachment` (keine Datei-Duplikation) oder manueller Upload
- [ ] **KI-Extraktion**: ZUGFeRD → XML → Gemini-AI-Fallback (mit Confidence-Score)
- [ ] **Datenquelle-Enum**: ZUGFERD, XML, AI – Transparenz über Herkunft der Metadaten
- [ ] **Verifizierungs-Flag**: `verifiziert` – manuelle Bestätigung der KI-Daten
- [ ] **Genehmigung**: `genehmigt`-Flag (Freigabe durch Abt. 3)
- [ ] **Projekt-Aufteilung**: Prozentuale Zuordnung auf mehrere Projekte
- [ ] **Rekursive Verknüpfung**: M:M `verknuepfteDokumente` (Rechnung ↔ Lieferschein ↔ AB)
- [ ] **Zahlungsbedingungen**: Nettotage, Skontotage, Skontoprozent, Liefertermin

### 6. ZUGFERD_E_INVOICING.md
> Elektronische Rechnungsstellung nach EU-Norm

**Inhalte:**
- [ ] **Ausgehend** (`ZugferdErstellService`): Hybrid-PDF mit eingebettetem XML erstellen (Mustang-Bibliothek)
- [ ] **Eingehend** (`ZugferdExtractorService`): XML-Parsing → Bestellnummer, Referenznummer, etc. extrahieren
- [ ] **Konvertierung** (`ZugferdConverterService`): Format-Umwandlung
- [ ] **Fallback-Kette**: ZUGFeRD → XML → Dateiname-Parsing → AI-OCR
- [ ] **Feldmapping**: Dokumentnummer → Invoice Number, Datum → Issue Date, Kundenname → Buyer
- [ ] **Gesetzliche Anforderungen**: E-Rechnungspflicht ab 2025 (B2G), schrittweise B2B

---

## 🟢 Priorität 3 – Geschäftsprozesse

### 7. BESTELLWESEN.md
> Einkauf, Bestellungen, PDF-Generierung

**Inhalte:**
- [ ] **Offene Bestellungen**: Unbestellte Artikel pro Projekt (`bestellt=false`)
- [ ] **Gruppierung**: Nach Lieferant + Projekt
- [ ] **Preisberechnung nach Verrechnungseinheit**: Kilogramm (Preis × kg), Laufende Meter, Quadratmeter, Stück
- [ ] **Bestellvorgang**: `setBestellt()` → Berechnung Endpreis, Markierung als bestellt
- [ ] **Bestellungs-PDF** (`BestellungPdfService`): Generierung gruppiert nach Lieferant/Projekt
- [ ] **Materialkosten-Tracking**: Erfassung und Zuordnung zu Projekten

### 8. ANGEBOTSWESEN.md
> Angebotserstellung, Kundenmanagement, Konversionsrate

**Inhalte:**
- [ ] **Angebot-Entity**: Bauvorhaben, Betrag, Anlegedatum, Bild-URL (Hero-Image für UI-Karten)
- [ ] **Kunden-E-Mails**: ElementCollection für persistente E-Mail-Liste pro Angebot
- [ ] **Notizen & Bilder**: AngebotNotiz mit Bild-Support (AngebotNotizBild)
- [ ] **Abschluss**: `abgeschlossen`-Flag
- [ ] **E-Mail-Backfill**: `EmailAddressChangedEvent` für nachträgliche Zuordnung
- [ ] **Konversion zum Projekt**: Vom Angebot zum Projekt → Auftragsbestätigung → Rechnung

### 9. MONATS_SALDO_CACHING.md
> Performance-Optimierung für monatliche Zeitkonto-Salden

**Inhalte:**
- [ ] **Zweck**: Performance-Cache, kein rechtlich bindendes Dokument
- [ ] **Komponenten**: Ist-Stunden, Soll-Stunden, Abwesenheits-Stunden, Feiertags-Stunden, Korrektur-Stunden
- [ ] **Caching-Strategie**:
  - Aktueller/Zukünftiger Monat → immer live berechnen
  - Vergangener Monat + Cache gültig → Cache verwenden
  - Vergangener Monat + Cache ungültig → Neuberechnung, speichern
- [ ] **Invalidierung**: `gueltig=false` bei Quelldaten-Änderung → automatische Neuberechnung bei nächster Abfrage
- [ ] **Warmup** (`MonatsSaldoWarmupService`): Vorberechnung beim Start

### 10. API_REFERENZ.md
> REST-Endpoint-Übersicht für alle Module

**Inhalte:**
- [ ] **AusgangsGeschaeftsDokument**: CRUD, Typ-Konversion, Storno, Export
- [ ] **Rechnungsübersicht**: Monats-/Jahresgruppierung, PDF-Export
- [ ] **Geschaeftsdokument**: CRUD, Abschluss-Info
- [ ] **Offene Posten**: Eingang mit Abteilungs-Filter
- [ ] **Bestellungen**: Offene Items, Bestellmarkierung
- [ ] **Zeiterfassung**: Buchungen, Korrekturen, Audit-Abfragen
- [ ] **E-Mail**: Import, Zuordnung, Signatur-Verwaltung
- [ ] **Authentifizierung & Berechtigungen**: Abteilungsbasierte Zugriffssteuerung

---

## 🔵 Priorität 4 – Architektur

### 11. ARCHITEKTUR_UEBERSICHT.md
> Technische Architektur und Designentscheidungen

**Inhalte:**
- [ ] **Tech-Stack**: Spring Boot, JPA/Hibernate, MySQL, React (Vite), Tailwind CSS
- [ ] **Modul-Struktur**: Controller → Service → Repository → Domain
- [ ] **Frontend-Architektur**: `react-pc-frontend/` (Haupt-UI), `react-textbausteine/` (Text-Editor), `react-zeiterfassung/` (Zeiterfassung)
- [ ] **Design-Patterns**:
  - Audit-Trail-Pattern (Immutability via Snapshots)
  - Dokumentketten-Pattern (Vorgänger-FK + Nachfolger)
  - Performance-Caching mit Smart-Invalidierung (MonatsSaldo)
  - Datei-Deduplizierung (LieferantDokument → EmailAttachment FK)
  - Enum-basiertes State Management
- [ ] **Externe Integrationen**: Gemini AI, IMAP E-Mail-Import, ZUGFeRD/Mustang
- [ ] **Datenbank-Migrationen**: Flyway-Versionierung

---

## 📅 Empfohlene Reihenfolge

| # | Dokument | Grund | Status |
|---|----------|-------|--------|
| 1 | GOBD_COMPLIANCE.md | **Rechtlich erforderlich** – Nachweis der GoBD-Konformität | ✅ Fertig |
| 2 | RECHNUNGSWESEN.md | Kernprozess, muss dokumentiert sein für Prüfungen | ✅ Fertig |
| 3 | DOKUMENTEN_LIFECYCLE.md | Grundlage für Verständnis aller Dokumentflüsse | ✅ Fertig |
| 4 | ZAHLUNGSVERKEHR.md | Direkt mit Rechnungswesen verknüpft | ⬜ Offen |
| 5 | LIEFERANTEN_DOKUMENTEN.md | Eingangsseite des Rechnungswesens | ⬜ Offen |
| 6 | ZUGFERD_E_INVOICING.md | Gesetzliche Pflicht ab 2025 | ⬜ Offen |
| 7 | BESTELLWESEN.md | Kerngeschäftsprozess | ⬜ Offen |
| 8 | ANGEBOTSWESEN.md | Kerngeschäftsprozess | ⬜ Offen |
| 9 | MONATS_SALDO_CACHING.md | Technische Doku für Wartung | ⬜ Offen |
| 10 | API_REFERENZ.md | Entwickler-Dokumentation | ⬜ Offen |
| 11 | ARCHITEKTUR_UEBERSICHT.md | Onboarding & Wartung | ⬜ Offen |

---

## ✅ Bereits dokumentiert

| Dokument | Inhalt |
|----------|--------|
| BUSINESS_CASES.md | Geschäftsnutzen aller Module (Kalkulation, Controlling, KI-Erkennung, E-Mail) |
| email-system-refactoring.md | E-Mail-System Refactoring-Dokumentation |
| ZEITERFASSUNG_WORKFLOWS.md | Zeiterfassungs-Workflows und -Prozesse |
