# GoBD-Compliance – Umsetzung im System

## Übersicht

Dieses Dokument beschreibt die Umsetzung der **Grundsätze zur ordnungsmäßigen Führung und Aufbewahrung von Büchern, Aufzeichnungen und Unterlagen in elektronischer Form sowie zum Datenzugriff (GoBD)** im Kalkulationsprogramm.

Rechtsgrundlagen:
- **GoBD** (BMF-Schreiben vom 28.11.2019)
- **§ 257 HGB** – Aufbewahrung von Unterlagen
- **§ 147 AO** – Ordnungsvorschriften für die Aufbewahrung von Unterlagen
- **§ 146 AO** – Ordnungsvorschriften für die Buchführung

---

## 1. Grundsatz der Unveränderbarkeit (GoBD Rz. 58–59)

### 1.1 Buchungssperre für Rechnungsdokumente

Gebuchte Dokumente werden durch das `gebucht`-Flag in der Entity `AusgangsGeschaeftsDokument` als unveränderbar markiert. Nur **Rechnungstypen** werden nach dem Buchen gesperrt:

**Sperrbare Dokumenttypen** (`SPERRBARE_TYPEN`):
| Typ | Beschreibung |
|---|---|
| `RECHNUNG` | Einzelrechnung |
| `TEILRECHNUNG` | Teilrechnung |
| `ABSCHLAGSRECHNUNG` | Abschlagsrechnung |
| `SCHLUSSRECHNUNG` | Schlussrechnung |
| `GUTSCHRIFT` | Gutschrift |
| `STORNO` | Stornorechnung |

**Nicht-sperrbare Dokumenttypen** (bleiben auch nach Buchung bearbeitbar):
| Typ | Beschreibung |
|---|---|
| `ANGEBOT` | Angebot |
| `AUFTRAGSBESTAETIGUNG` | Auftragsbestätigung |

### 1.2 Bearbeitbarkeits-Prüfung

Die Methode `istBearbeitbar()` auf der Entity prüft:

```
Stornierte Dokumente  →  IMMER gesperrt (Korrekturnachweis)
Gebuchte Rechnungen   →  GESPERRT (GoBD-Unveränderbarkeit)
Gebuchte Angebote/ABs →  BEARBEITBAR (kein Rechnungstyp)
Nicht gebuchte Docs   →  BEARBEITBAR
```

### 1.3 Buchungszeitpunkte

Ein Dokument wird gebucht (`gebucht = true`, `gebuchtAm = heute`) in folgenden Fällen:

1. **Manuelles Buchen** – Über den Endpoint `POST /api/ausgangs-dokumente/{id}/buchen`
2. **E-Mail-Versand** – Über `POST /api/ausgangs-dokumente/{id}/email-versendet`; setzt zusätzlich `versandDatum`
3. **Stornierung** – Das Storno-Gegendokument wird sofort als gebucht erstellt

---

## 2. Löschverbot und GoBD-konforme Löschregeln

### 2.1 Grundprinzip

Geschäftsdokumente dürfen gemäß GoBD grundsätzlich **nicht gelöscht** werden. Stattdessen wird das Storno-Verfahren angewendet.

### 2.2 Löschregeln im Detail (§ 147 AO, GoBD Rz. 58–59)

Die Methode `loeschen()` im `AusgangsGeschaeftsDokumentService` erzwingt folgende Regeln:

| Bedingung | Löschbar? | Begründung |
|---|---|---|
| `gebucht = true` | **NEIN** | Grundsatz der Unveränderbarkeit |
| `versandDatum != null` | **NEIN** | In den Geschäftsverkehr gebracht |
| `storniert = true` | **NEIN** | Korrekturnachweis muss erhalten bleiben |
| `typ = STORNO` | **NEIN** | Storno-Dokumente sind Korrekturbuchungen |
| Entwurf (alles false) | **JA** | Nur mit Pflicht-Begründung |

### 2.3 Pflicht-Begründung bei Löschung

Jede Löschung eines Entwurfs erfordert eine textuelle Begründung (`begruendung`). Ohne Begründung wird die Löschung abgelehnt. Die Begründung wird im Server-Log protokolliert.

### 2.4 Fehlermeldungen

```
"Gebuchte Dokumente dürfen gemäß GoBD nicht gelöscht werden. 
 Bitte erstellen Sie stattdessen eine Stornierung."

"Bereits versandte Dokumente dürfen gemäß GoBD nicht gelöscht werden. 
 Bitte erstellen Sie stattdessen eine Stornierung."

"Stornierte Dokumente dürfen nicht gelöscht werden, 
 da sie als Korrekturnachweis aufbewahrt werden müssen."

"Stornorechnungen dürfen nicht gelöscht werden, 
 da sie als Korrekturbuchung aufbewahrt werden müssen."
```

---

## 3. Storno-Verfahren

### 3.1 Ablauf

```
Original-Rechnung (z.B. RECHNUNG, Nr. 2026/03/00001)
       │
       ▼
  [Stornierung auslösen]
       │
       ├── Original markiert: storniert = true, storniertAm = heute
       │
       └── Neues STORNO-Dokument erstellt:
           ├── Eigene Dokumentnummer (z.B. 2026/03/00002)
           ├── Betreff: "Stornorechnung 2026/03/00002 (zu Rechnung 2026/03/00001)"
           ├── Beträge: Netto und Brutto NEGIERT
           ├── Vorgänger-Referenz auf Original (vorgaenger_id)
           ├── Projekt, Angebot, Kunde vom Original übernommen
           ├── HTML-Inhalt und Positionen-JSON vom Original übernommen
           ├── Sofort gebucht: gebucht = true, gebuchtAm = heute
           └── Offener-Posten-Eintrag des Originals wird als bezahlt markiert
```

### 3.2 Stornierbare Dokumenttypen

Nur Rechnungstypen können storniert werden:
- `RECHNUNG`
- `TEILRECHNUNG`
- `ABSCHLAGSRECHNUNG`
- `SCHLUSSRECHNUNG`

Angebote und Auftragsbestätigungen können **nicht** storniert werden (sie bleiben bearbeitbar).

### 3.3 Auswirkungen der Stornierung

1. **Original-Dokument**: `storniert = true`, `storniertAm` gesetzt → dauerhaft gesperrt
2. **Storno-Dokument**: Sofort gebucht und gesperrt → kann nie gelöscht werden
3. **Offene Posten**: Zugehöriger Eintrag wird als bezahlt markiert
4. **Projekt-Preis**: Wird automatisch aus verbleibenden Dokumenten neuberechnet
5. **Angebot-Preis**: Wird automatisch aktualisiert (falls Angebot verknüpft)
6. **Beide Dokumente bleiben erhalten** → lückenloser Audit-Trail

---

## 4. Audit-Trail für Zeitbuchungen

### 4.1 Architektur

Jede Änderung an einer `Zeitbuchung` erzeugt einen unveränderlichen Snapshot in `ZeitbuchungAudit`. Die Snapshots werden über den `ZeitbuchungAuditService` protokolliert.

### 4.2 Versionierung

| Version | Bedeutung |
|---|---|
| `1` | Initiale Erfassung (Erstanlage) |
| `2` | Erste Änderung |
| `3+` | Weitere Änderungen |

Uniqueness-Constraint: `(zeitbuchung_id, version)` − keine doppelten Versionen möglich.

### 4.3 Audit-Aktionen (`AuditAktion`)

| Aktion | Beschreibung |
|---|---|
| `ERSTELLT` | Initiale Erfassung (Stempelung am Handy oder manuelle Anlage) |
| `GEAENDERT` | Nachträgliche Korrektur (z.B. Endzeit angepasst) |
| `STORNIERT` | Stornierung / Löschung der Buchung |

### 4.4 Snapshot-Felder in `ZeitbuchungAudit`

Jeder Snapshot speichert eine **vollständige Kopie** des Datensatzes zum Zeitpunkt der Änderung:

**Fachdaten (Snapshot):**
- `mitarbeiterId` – Zugehöriger Mitarbeiter
- `projektId` – Zugehöriges Projekt (null bei PAUSE-Buchungen)
- `arbeitsgangId` – Arbeitsgang
- `arbeitsgangStundensatzId` – Stundensatz des Arbeitsgangs
- `projektProduktkategorieId` – Produktkategorie im Projekt
- `startZeit` – Beginn der Zeitbuchung
- `endeZeit` – Ende der Zeitbuchung
- `anzahlInStunden` – Berechnete Stundenzahl
- `notiz` – Freitext-Notiz

**Änderungs-Metadaten:**
- `geaendertVon` (Mitarbeiter-FK) – Wer hat die Änderung durchgeführt?
- `geaendertAm` (LocalDateTime) – Wann wurde die Änderung durchgeführt?
- `geaendertVia` (ErfassungsQuelle) – Über welchen Kanal?
- `aenderungsgrund` (Text) – Begründung (Pflicht bei GEAENDERT/STORNIERT)

### 4.5 Erfassungsquellen (`ErfassungsQuelle`)

| Quelle | Beschreibung |
|---|---|
| `MOBILE_APP` | Stempelung über die Mobile-PWA (Mitarbeiter am Handy) |
| `DESKTOP` | Erfassung/Korrektur über das PC-Frontend im Büro |
| `ADMIN_KORREKTUR` | Administrative Korrektur durch Chef/Buchhaltung |
| `IMPORT` | Import aus externem System |

### 4.6 Rekonstruierbarkeit

Durch die lückenlose Versionskette kann jeder Datensatz **vollständig rekonstruiert** werden:

```
Zeitbuchung #42
  └── Audit Version 1: ERSTELLT (2026-03-01 08:00, MOBILE_APP)
  └── Audit Version 2: GEAENDERT (2026-03-01 18:30, DESKTOP, "Endzeit korrigiert")
  └── Audit Version 3: STORNIERT (2026-03-02 09:00, ADMIN_KORREKTUR, "Doppelbuchung")
```

---

## 5. Audit-Trail für Zeitkonto-Korrekturen

### 5.1 Zweck

Korrekturen am Zeitkonto (z.B. Überstunden-Ausgleich, manuelle Anpassungen) sind **buchhalterisch relevant** und erfordern ebenfalls einen vollständigen Audit-Trail.

### 5.2 Snapshot-Felder in `ZeitkontoKorrekturAudit`

**Fachdaten:**
- `mitarbeiterId` – Betroffener Mitarbeiter
- `datum` – Datum der Korrektur
- `stunden` – Stundenanzahl (positiv oder negativ)
- `grund` – Fachlicher Grund der Korrektur

**Änderungs-Metadaten:** Identisch zu `ZeitbuchungAudit` (wer, wann, via, Begründung).

### 5.3 Uniqueness-Constraint

`(zeitkonto_korrektur_id, version)` – Keine doppelten Versionen pro Korrektur.

---

## 6. Idempotenz bei Zeitbuchungen

### 6.1 Problem

Bei Offline-Sync über die Mobile-PWA können Buchungen durch Netzwerkprobleme mehrfach übertragen werden.

### 6.2 Lösung

Jede Zeitbuchung enthält einen `idempotencyKey` (UUID). Vor dem Speichern wird geprüft, ob bereits eine Buchung mit diesem Key existiert. Falls ja, wird die bestehende Buchung zurückgegeben statt eine Doppelerfassung zu erzeugen.

---

## 7. Lückenlose Nummerierung

### 7.1 Format

Dokumentnummern folgen dem Format: **`YYYY/MM/NNNNN`**

Beispiel: `2026/03/00001`

### 7.2 Implementierung

- Separate Counter-Tabelle `ausgangs_geschaeftsdokument_counter` mit Key `YYYY/MM`
- Thread-sichere Nummernvergabe durch **pessimistisches Locking** (`findByMonatKeyForUpdate`)
- Zähler wird pro Monat hochgezählt
- Innerhalb eines Monats entstehen keine Lücken

### 7.3 Monatliche Zähler-Einheit

| Monat-Key | Zähler | Nächste Nummer |
|---|---|---|
| `2026/01` | 12 | `2026/01/00013` |
| `2026/02` | 5 | `2026/02/00006` |
| `2026/03` | 0 | `2026/03/00001` |

---

## 8. Dokumentenketten und Datenintegrität

### 8.1 Vorgänger–Nachfolger-Kette

Jedes `AusgangsGeschaeftsDokument` kann auf einen Vorgänger verweisen (`vorgaenger_id` FK). Dies sichert die lückenlose Dokumentenkette:

```
Angebot (2026/01/00001)
   └→ AB (2026/01/00002) [vorgaenger_id → Angebot]
       └→ Abschlagsrechnung 1 (2026/02/00001) [vorgaenger_id → AB]
       └→ Abschlagsrechnung 2 (2026/02/00003) [vorgaenger_id → AB]
       └→ Schlussrechnung (2026/03/00001) [vorgaenger_id → AB]
```

### 8.2 Storno-Kette

Bei Stornierung verweist das Storno-Dokument auf das Original:

```
Rechnung (2026/02/00001) [storniert = true]
   └→ STORNO (2026/02/00002) [vorgaenger_id → Rechnung, gebucht = true]
```

---

## 9. Aufbewahrungsfristen

### 9.1 Gesetzliche Fristen (§ 257 HGB / § 147 AO)

| Dokumentart | Frist | Rechtsgrundlage |
|---|---|---|
| Rechnungen (Eingang & Ausgang) | **10 Jahre** | § 147 Abs. 1 Nr. 1 AO |
| Buchungsbelege | **10 Jahre** | § 147 Abs. 1 Nr. 4 AO |
| Geschäftsbriefe (empfangen & gesendet) | **6 Jahre** | § 147 Abs. 1 Nr. 2 AO |
| Angebote (angenommene) | **6 Jahre** | § 147 Abs. 1 Nr. 2 AO |
| Auftragsbestätigungen | **6 Jahre** | § 147 Abs. 1 Nr. 2 AO |

### 9.2 Empfehlung

Das System implementiert aktuell **kein automatisches Löschen** nach Ablauf der Aufbewahrungsfrist. Alle Dokumente bleiben dauerhaft im System erhalten. Eine manuelle Bereinigung nach Ablauf der Fristen ist nur über direkte Datenbankoperationen möglich und sollte nur nach Rücksprache mit dem Steuerberater erfolgen.

---

## 10. Zusammenfassung der technischen Maßnahmen

| GoBD-Anforderung | Technische Umsetzung | Entity/Service |
|---|---|---|
| Unveränderbarkeit | `gebucht`-Flag + `istBearbeitbar()` | `AusgangsGeschaeftsDokument` |
| Löschverbot | Validierung in `loeschen()` | `AusgangsGeschaeftsDokumentService` |
| Storno statt Löschung | `stornieren()` erstellt Gegendokument | `AusgangsGeschaeftsDokumentService` |
| Lückenlose Nummerierung | Counter-Tabelle mit pessimistischem Lock | `AusgangsGeschaeftsDokumentCounter` |
| Audit-Trail Zeitbuchungen | Immutable Snapshots pro Version | `ZeitbuchungAudit` |
| Audit-Trail Korrekturen | Immutable Snapshots pro Version | `ZeitkontoKorrekturAudit` |
| Nachvollziehbarkeit | `geaendertVon`, `geaendertAm`, `geaendertVia` | Audit-Entities |
| Begründungspflicht | `aenderungsgrund` (Pflicht bei Änderung/Storno) | Audit-Entities |
| Dokumentenkette | `vorgaenger_id` FK + `nachfolger`-Liste | `AusgangsGeschaeftsDokument` |
| Doppelerfassungsschutz | `idempotencyKey` (UUID) | `Zeitbuchung` |
| Offene Posten-Tracking | Automatischer Eintrag bei Buchung | `ProjektGeschaeftsdokument` |
