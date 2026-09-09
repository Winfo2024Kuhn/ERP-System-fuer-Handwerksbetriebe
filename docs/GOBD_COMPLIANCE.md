# GoBD-Compliance – Umsetzung im System

## Übersicht

Dieses Dokument beschreibt die Umsetzung der **Grundsätze zur ordnungsmäßigen Führung und Aufbewahrung von Büchern, Aufzeichnungen und Unterlagen in elektronischer Form sowie zum Datenzugriff (GoBD)** im Kalkulationsprogramm.

Rechtsgrundlagen:
- **GoBD** (BMF-Schreiben vom 28.11.2019)
- **§ 257 HGB** – Aufbewahrung von Unterlagen
- **§ 147 AO** – Ordnungsvorschriften für die Aufbewahrung von Unterlagen
- **§ 146 AO** – Ordnungsvorschriften für die Buchführung
- **§§ 140–148 AO** – Aufzeichnungspflichten und Ordnungsvorschriften
- **§ 238 ff. HGB** – Buchführungspflicht

> **Hinweis zum Geltungsbereich der GoBD:** Die GoBD gelten **nicht nur für die Finanzbuchhaltung**, sondern für **alle steuerlich relevanten Daten** – also auch Angebote, Auftragsbestätigungen, Lieferscheine, Stammdatenänderungen, Preislisten, Lagerbewegungen, Audit Trails und E-Mails mit Preisverhandlungen. Maßgeblich ist nicht der Name des Dokuments, sondern seine **steuerliche Relevanz**. Wer hier lückenhaft arbeitet, riskiert die Verwerfung der Buchführung und eine Schätzung nach § 162 AO.

---

## 0. Geltungsbereich & Abgrenzung

Dieses ERP ist **ein Vorsystem für Ausgangsbelege im Lead-to-Cash-Prozess**, kein Komplettsystem mit eigener Finanzbuchhaltung oder Kasse. Die GoBD-Verantwortung ist arbeitsteilig organisiert, damit das System nicht in den Zuständigkeitsbereich zertifizierungspflichtiger Software (Buchhaltung, Kasse) hineingreift.

### 0.1 Was dieses System abdeckt

| Bereich | GoBD-Umsetzung im System |
|---|---|
| **Angebote** | Versionierung, Vorgänger-Nachfolger-Kette, Audit-Trail mit Hash-Kette |
| **Auftragsbestätigungen** | Audit-Trail mit Hash-Kette, Vorgänger-Referenz |
| **Lieferscheine** | Vorgänger-Kette zu Projekt/AB, Versanddatum-Sperre |
| **Ausgangsrechnungen** (alle Typen) | Lückenlose Nummerierung, Buchungssperre, Storno-Verfahren, Hash-Kette, Z3-Export |
| **Mahnungen** | Audit-Trail mit Hash-Kette |
| **Zeitbuchungen & Zeitkonto-Korrekturen** | Versionierte Audit-Snapshots, Begründungspflicht, Erfassungsquelle |
| **Eingangsbelege (Lieferantenrechnungen, Quittungen)** | Belegfoto-Pflicht, KI-gestützte Vorerfassung als **Vorsortierung für den Steuerberater** |
| **Projekt-/Auftragsdaten** | Lückenlose Kette Angebot → AB → Lieferschein → Rechnung |

### 0.2 Was dieses System bewusst NICHT abdeckt

| Bereich | Wo es stattdessen läuft | Begründung |
|---|---|---|
| **Finanzbuchhaltung (DATEV, Bilanz, USt-Voranmeldung)** | Steuerberater mit zertifizierter Software | Trennung von Vorsystem und Buchungssystem ist gewollt – das Vorsystem liefert die Belege, der Steuerberater bucht. |
| **Registrierkasse / POS mit TSE (§ 146a AO)** | Nicht abgebildet — wird auch nicht gebraucht | Das System führt ein *elektronisches Kassenbuch*, kein Aufzeichnungssystem mit Kassenfunktion. Wer eine offene Ladenkasse mit Einzelaufzeichnung führt, braucht keine TSE und muss nichts melden. Die bei einer Bareinnahme erzeugte Quittung ist der Beleg zur Buchung (§ 33 UStDV / § 14 UStG), kein Bon im Sinne von § 146a AO. |
| **Lagerbuchführung / Inventur** | Manueller Prozess bzw. nicht abgebildet | Für reine Auftragsfertigung (Handwerk) nicht zwingend; bei Lagerhaltung über Bagatellgrenze wäre ein separates Modul nötig. |
| **Lohnbuchhaltung** | Steuerberater | Personalstammdaten und Zeitbuchungen werden im ERP geführt, aber die Lohnabrechnung erfolgt extern. |

**Abgrenzung Kasse vs. Registrierkasse:** Die TSE-Pflicht, die
Kassenmeldung ans Finanzamt (ab 2025) und die Belegausgabepflicht gelten
für elektronische Aufzeichnungssysteme *mit Kassenfunktion* – also für
Registrierkassen und POS-Systeme, die einen Verkauf abschließen. Ein
elektronisches Kassenbuch ist dagegen Buchführungssoftware: Es zeichnet
die Bewegung des Bargelds auf, nicht den Verkauf. Wer eine offene
Ladenkasse (Geldkassette) plus Kassenbuch mit Einzelaufzeichnung führt,
braucht deshalb keine TSE und muss nichts melden – genau dieses Modell
ist hier umgesetzt. Konsequenz für die Oberfläche: Das Programm tritt nie
wie eine Registrierkasse auf, es gibt kein „Verkauf abschließen" und
keinen Kassenbon als Verkaufsbeleg. Wie die Kasse im Alltag bedient wird,
steht in [`docs/KASSE_ANLEITUNG.md`](KASSE_ANLEITUNG.md).

### 0.3 Konsequenz für die GoBD-Prüfung

Bei einer Außenprüfung sind drei Datenquellen relevant:

1. **Dieses ERP** liefert per Z3-Export alle Ausgangsbelege inklusive Audit-Trail und Hash-Kette.
2. **Dieses ERP** liefert zusätzlich das Kassenbuch, den DATEV-Buchungsstapel und die Belegbilder als Monatspaket.
3. **Der Steuerberater** liefert die Buchhaltungs-Exporte (DATEV).

Für die **Finanzbuchhaltung** bleibt die ursprüngliche Architekturentscheidung
richtig: Sie verhindert, dass eine selbstgebaute Software in den
zertifizierungspflichtigen Bereich der Buchhaltung hineingreift, ohne die
dafür nötigen Nachweise (Buchhaltungs-Testat) erbringen zu müssen – das
bleibt beim Steuerberater. Für die **Kasse** gilt das dagegen nicht mehr:
Das eingebaute Kassenbuch ist kein zertifizierungspflichtiges
Aufzeichnungssystem (siehe Abgrenzung oben), sondern klassische
Buchführung nach § 146 AO. Dafür braucht es weder eine TSE-Zertifizierung
noch eine externe Software.

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

## 10. Manipulationssichere Hash-Kette für Ausgangs-Dokument-Audit (GoBD Rz. 64)

### 10.1 Zweck

GoBD Rz. 64 fordert, dass nachträgliche Änderungen an Buchungsbelegen erkennbar sein müssen. Die Hash-Kette stellt sicher, dass eine Manipulation eines bereits protokollierten Audit-Eintrags (z. B. direkt in der Datenbank) sofort bei der maschinellen Prüfung auffällt.

### 10.2 Funktionsprinzip

Jeder Eintrag in `ausgangs_geschaeftsdokument_audit` erhält drei zusätzliche Felder:

| Feld | Typ | Bedeutung |
|---|---|---|
| `chain_index` | BIGINT UNIQUE | Monoton wachsende Position in der Kette (0, 1, 2, …) |
| `previous_hash` | CHAR(64) | SHA-256 des unmittelbaren Vorgänger-Eintrags (`NULL` bei erstem Eintrag) |
| `entry_hash` | CHAR(64) | SHA-256 über alle relevanten Felder **plus** `previous_hash` |

Der **entry_hash** wird über folgende Felder gebildet (kanonische Reihenfolge, `|`-separiert):

```
chain_index | previous_hash | dokumentId | dokumentNummer | typ | aktion |
betragNetto | betragBrutto | gebucht | storniert | digitalAngenommen |
geaendertAm (ISO) | geaendertVon (Id) | inhaltHash
```

### 10.3 Atomares Anhängen

Der Kettenkopf liegt in der Singleton-Tabelle `audit_chain_state` (Zeile id=1). Beim Anhängen eines neuen Eintrags wird diese Zeile mit `SELECT … FOR UPDATE` **pessimistisch gelockt**, sodass zwei parallele Aktionen niemals denselben `previous_hash` erhalten.

### 10.4 Backfill

Beim ersten Start nach der V255-Migration führt der `AuditChainBackfillRunner` (Spring `ApplicationRunner`) alle bereits vorhandenen Audit-Einträge in chronologischer Reihenfolge in die Kette ein. Bestehende Einträge ohne `chain_index` werden nachträglich verkettet.

### 10.5 Selbstverifikation

Der Endpoint `GET /api/ausgangs-dokumente/audit/verify` prüft die gesamte Kette und liefert einen Bericht:

```json
{
  "intakt": true,
  "gesamtAnzahl": 1247,
  "letzterChainIndex": 1246,
  "letzterEntryHash": "a3f9...",
  "fehler": []
}
```

Bei Manipulation enthält `fehler` die erste Bruchstelle mit `chainIndex`, `dokumentNummer` und Beschreibung.

### 10.6 GoBD-Z3-Paket (Datenträgerüberlassung)

Der Endpoint `GET /api/ausgangs-dokumente/audit/z3-paket?von=YYYY-MM-DD&bis=YYYY-MM-DD` erzeugt ein ZIP-Paket für die **Datenträgerüberlassung (Z3)** nach GoBD:

| Datei im ZIP | Inhalt |
|---|---|
| `dokumente.csv` | Alle Ausgangs-Geschäftsdokumente im Zeitraum (Stammdaten) |
| `audit.csv` | Vollständiger Audit-Log mit Hash-Kette (chain_index, previous_hash, entry_hash) |
| `INFO.txt` | Erklärung des Formats, Prüfanleitung für Steuerprüfer |
| `manifest.sha256` | SHA-256-Prüfsummen aller Dateien im Paket |

---

## 11. Eingangsbelege & Lieferantendokumente

### 11.1 Erfassung mit Belegfoto-Pflicht

Eingangsbelege (Lieferantenrechnungen, Quittungen, Bons) werden in der Entity `Beleg` geführt. Eine **harte DB-Invariante** stellt sicher, dass jeder Beleg eine Bildkopie hat:

- `gespeicherterDateiname` ist **NOT NULL** für alle echten Belege
- Nur reine Umbuchungen (`istUmbuchung = true`) dürfen ohne Bilddatei existieren
- Original-Dateien werden im konfigurierten Upload-Verzeichnis archiviert

### 11.2 KI-gestützte Vorerfassung (Status: Vorsortierung, nicht Buchung)

Eingangsbelege durchlaufen einen KI-Analyse-Workflow (`BelegKiAnalyseStatus`), der Vorschläge für Lieferant, Kategorie und Aufteilung auf Kostenstellen liefert. **Wichtige Einordnung im Sinne der GoBD:**

- Die KI-Klassifizierung ist eine **Vorsortierung als Komfort-Feature**, keine steuerliche Buchung.
- Die endgültige Kontierung erfolgt beim Steuerberater in zertifizierter Buchhaltungssoftware.
- Der Beleg selbst (Bild + Stammdaten) bleibt unverändert; nur Metadaten werden ergänzt.

### 11.3 Monatsexport für den Steuerberater

Der `BelegeKasseExportPdfService` erzeugt einen Monatsexport aller Eingangsbelege als PDF-Bündel, das an den Steuerberater übergeben wird. Dies entspricht dem Lieferweg „Vorsystem → Buchhaltung" und ist die GoBD-relevante Übergabestelle.

---

## 12. Bekannte Lücken & Roadmap

Dieser Abschnitt dokumentiert offene Punkte transparent, weil GoBD-Compliance nur dann belastbar ist, wenn Lücken benannt statt verschwiegen werden.

### 12.1 Stammdaten-Änderungshistorie (geplant)

**Aktueller Stand:** Stammdaten (Kunde, Lieferant, eigene Firmendaten) werden bei Änderung überschrieben, ohne dass eine Historie der Vorgängerwerte erhalten bleibt. Auf Rechnungen ist die zum Versandzeitpunkt gültige Adresse über das archivierte PDF rekonstruierbar, aber nicht direkt im Datenmodell abgreifbar.

**Geplante Umsetzung:** Eine generische Tabelle `stammdaten_aenderung` mit JSON-Snapshots (Vorher/Nachher), Benutzer, Zeitpunkt und geänderten Feldern. Scope:

| Entity | Audit-relevante Felder |
|---|---|
| `Kunde` | Name, Adresse, USt-ID, Steuernummer |
| `Lieferanten` | Name, Adresse, USt-ID, Bankverbindung |
| `Firma` (eigene Stammdaten) | Name, Adresse, USt-ID, Bankverbindung, Steuernummer |

**Nicht im Scope:** Artikelstammdaten, Sachkonten, Mitarbeiterstammdaten – diese sind entweder nicht direkt steuerrelevant für Ausgangsbelege oder werden beim Steuerberater geführt.

### 12.2 Automatisierte Aufbewahrungsverwaltung

**Aktueller Stand:** Alle Dokumente bleiben dauerhaft erhalten; es gibt keinen Automatismus, der nach Ablauf der 10-Jahres-Frist Belege archiviert oder zur Löschung freigibt. Manuelle Bereinigung nur in Abstimmung mit dem Steuerberater.

**Bewertung:** Dauerhaftes Aufbewahren ist GoBD-konform und in der Praxis bei einem Datenvolumen, wie es ein Handwerksbetrieb erzeugt, unkritisch. Eine Automatisierung ist daher nicht priorisiert.

### 12.3 Verfahrensdokumentation

**Aktueller Stand:** Dieses Dokument (`GOBD_COMPLIANCE.md`) zusammen mit `DOKUMENTEN_LIFECYCLE.md`, `RECHNUNGSWESEN.md` und `KASSE_BUCHHALTUNG.md` ist die technische Verfahrensdokumentation des Vorsystems.

**Empfehlung:** Betriebe sollten zusätzlich eine **betriebliche Verfahrensdokumentation** führen, die beschreibt:

- Welche Belege wo entstehen (ERP / Kassenprogramm / E-Mail-Postfach)
- Wer Belege erfasst und freigibt
- Wann die Übergabe an den Steuerberater erfolgt
- Wie das Backup organisiert ist

Diese betriebliche Verfahrensdokumentation ist nicht Teil der Software, sondern ein organisatorisches Dokument des Betriebs.

### 12.4 Manuelle Backup-Strategie

**Aktueller Stand:** Datenbank-Backups und Belegarchiv liegen in der Verantwortung des Betreibers (siehe `INSTALLATION.md`). Das System protokolliert keine Backup-Läufe.

**Empfehlung:** Tägliches Backup der PostgreSQL-Datenbank **und** des Upload-Verzeichnisses an einen externen Ort. Ohne Backup verlieren die hier beschriebenen GoBD-Maßnahmen ihre Schutzwirkung bei Hardware-Defekt.

---

## 13. Zusammenfassung der technischen Maßnahmen

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
| **Manipulationsschutz Audit** | **SHA-256 Hash-Kette** (entry_hash + previous_hash) | **`AusgangsGeschaeftsDokumentAudit`** |
| **Maschinenlesbare Prüfung** | **Verify-Endpoint + Z3-ZIP-Export** | **`AuditChainVerifier` / `SteuerpruefungZ3ExportService`** |
