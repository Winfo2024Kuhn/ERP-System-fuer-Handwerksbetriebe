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

Dieses ERP ist **ein Vorsystem für Ausgangsbelege im Lead-to-Cash-Prozess** mit einem **elektronischen Kassenbuch** für die Barkasse (siehe 0.2), aber kein Komplettsystem mit eigener Finanzbuchhaltung. Die GoBD-Verantwortung ist arbeitsteilig organisiert, damit das System nicht in den Zuständigkeitsbereich zertifizierungspflichtiger Software (Finanzbuchhaltung, Registrierkassen mit TSE) hineingreift.

### 0.1 Was dieses System abdeckt

| Bereich | GoBD-Umsetzung im System |
|---|---|
| **Angebote / Nachtragsangebote** | Fortlaufende Nummer, Vorgänger-Nachfolger-Kette, Sperre nach digitaler Annahme, Annahme-Nachweis mit Hash (`DokumentFreigabe`), Audit-Eintrag bei digitaler Annahme und bei Löschung |
| **Auftragsbestätigungen** | Vorgänger-Referenz, Sperre nach digitaler Annahme, Audit-Eintrag bei digitaler Annahme und bei Löschung |
| **Ausgangsrechnungen** (Rechnung, Teil-, Abschlags-, Schlussrechnung, Gutschrift, Storno) | Fortlaufende Nummerierung, Buchungssperre, Storno-Verfahren, Audit-Trail mit Hash-Kette, archiviertes PDF, Z3-Export |
| **Mahnungen** | Archiviertes PDF, Versanddatum, Verweis auf die Rechnung (nicht Teil der Hash-Kette) |
| **Zeitbuchungen & Zeitkonto-Korrekturen** | Versionierte Audit-Snapshots, Begründungspflicht, Erfassungsquelle |
| **Eingangsbelege (Lieferantenrechnungen, Lieferscheine, Quittungen)** | Ablage der Originaldatei, Belegfoto-Pflicht im Kassenbuch, KI-gestützte Vorerfassung als **Vorsortierung für den Steuerberater** |
| **Kassenbuch** | Eigene Hash-Kette, Festschreibung per Monatsabschluss (siehe `KASSE_BUCHHALTUNG.md`) |
| **Projekt-/Auftragsdaten** | Lückenlose Kette Angebot → AB → Rechnung → Storno |

Ausgehende Lieferscheine erstellt das System nicht; Lieferscheine kommen nur als Lieferanten-Dokumente herein.

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

**Nie gebuchte Dokumenttypen** (`ANGEBOT`, `NACHTRAGSANGEBOT`, `AUFTRAGSBESTAETIGUNG`): Ein Buchungsaufruf bleibt bei ihnen ohne Wirkung. Sie sind Geschäftsbriefe und bleiben bearbeitbar (Verhandlungsspielraum) – bis der Kunde das Angebot **digital annimmt**. Dann werden Angebot und automatisch erzeugte Auftragsbestätigung mit `digitalAngenommen = true` gesperrt („Verbindlich“), damit der Beweis der Annahme erhalten bleibt.

### 1.2 Bearbeitbarkeits-Prüfung

Die Methode `istBearbeitbar()` auf der Entity prüft:

```
Stornierte Dokumente           →  IMMER gesperrt (Korrekturnachweis)
Digital angenommene Dokumente  →  GESPERRT (Beweis der Annahme)
Gebuchte Rechnungen/Gutschrift/
Storno                         →  GESPERRT (GoBD-Unveränderbarkeit)
Sonst (Entwürfe, Angebote, ABs)→  BEARBEITBAR
```

Der Server lehnt Änderungen an gesperrten Dokumenten ab („Dokument ist gesperrt und kann nicht mehr bearbeitet werden.“).

### 1.3 Buchungszeitpunkte

Ein Rechnungsdokument wird gebucht (`gebucht = true`, `gebuchtAm = heute`) in folgenden Fällen:

1. **Drucken** („Drucken & Buchen“) oder **PDF-Export** im Dokument-Editor – der Editor ruft `POST /api/ausgangs-dokumente/{id}/buchen` auf; das Dokumentdatum wird vorher auf heute gesetzt
2. **E-Mail-Versand** – der Editor bucht vor dem Versand; danach setzt `POST /api/ausgangs-dokumente/{id}/email-versendet` zusätzlich `versandDatum`
3. **Stornierung** – Das Storno-Gegendokument wird sofort als gebucht erstellt

Ein **Entwurfsversand** (Knopf „Entwurf“, PDF mit Wasserzeichen „ENTWURF“) bucht nicht. Das beim Buchen erzeugte PDF wird auf dem Server archiviert (`pdf-speichern`).

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
| `digitalAngenommen = true` | **NEIN** | Vom Kunden verbindlich angenommen (auch wenn der automatische AB-Versand gescheitert ist) |
| `storniert = true` | **NEIN** | Korrekturnachweis muss erhalten bleiben |
| `typ = STORNO` | **NEIN** | Storno-Dokumente sind Korrekturbuchungen |
| Entwurf (alles false) | **JA** | Nur mit Pflicht-Begründung |

### 2.3 Pflicht-Begründung bei Löschung

Jede Löschung eines Entwurfs erfordert eine textuelle Begründung (`begruendung`). Ohne Begründung wird die Löschung abgelehnt („Eine Begründung für das Löschen ist erforderlich.“).

In der Oberfläche (Aktionsmenü → „Löschen“, Dialog „Dokument löschen“) wählt der Benutzer einen Grund aus – Tippfehler / Erfassungsfehler, Versehentlich doppelt erstellt, Falscher Kunde / Empfänger, Falscher Betrag, Falsche Leistungspositionen, Auftrag wurde storniert (Entwurf), Testdaten / Demo-Eintrag oder Sonstiges (dann Freitext Pflicht).

Vor dem endgültigen Löschen schreibt das System einen unveränderlichen Audit-Eintrag `GELOESCHT` mit Snapshot (Nummer, Typ, Beträge), Begründung, Bearbeiter und IP-Adresse in die Hash-Kette – für **alle** Dokumenttypen. Die Nummer des gelöschten Entwurfs bleibt dadurch nachweisbar (`GET /api/ausgangs-dokumente/historie/nummer/{dokumentNummer}`).

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

In der Oberfläche: Projekt → Reiter „Geschäftsdokumente“ → gebuchte Rechnung anklicken → **„Stornieren“** → Bestätigung „Rechnung stornieren“. Der Vorgang kann nicht rückgängig gemacht werden.

```
Original-Rechnung (z.B. RECHNUNG, Nr. RE-2026/03/00001)
       │
       ▼
  [Stornierung auslösen]
       │
       ├── Original markiert: storniert = true, storniertAm = heute
       │
       └── Neues STORNO-Dokument erstellt:
           ├── Eigene Dokumentnummer (z.B. ST-2026/03/00002)
           ├── Betreff: "Stornorechnung ST-2026/03/00002 (zu Rechnung RE-2026/03/00001)"
           ├── Beträge: Netto und Brutto als Minderung (negativ)
           ├── Vorgänger-Referenz auf Original (vorgaenger_id)
           ├── Projekt, Anfrage, Kunde und Rechnungsanschrift vom Original übernommen
           ├── HTML-Inhalt und Positionen-JSON vom Original übernommen
           ├── Sofort gebucht: gebucht = true, gebuchtAm = heute
           └── Offener-Posten-Eintrag des Originals wird als bezahlt markiert
```

### 3.2 Stornierbare Dokumenttypen

Nur gebuchte Rechnungstypen können storniert werden (Oberfläche und Schnittstelle):
- `RECHNUNG`
- `TEILRECHNUNG`
- `ABSCHLAGSRECHNUNG`
- `SCHLUSSRECHNUNG`

Angebote, Nachtragsangebote, Auftragsbestätigungen und Gutschriften können **nicht** storniert werden („Nur Rechnungen können storniert werden.“). Ein bereits storniertes Dokument lässt sich nicht erneut stornieren, eine Stornorechnung selbst nicht stornieren (sonst sänke der Umsatz doppelt). Nur **gebuchte** Rechnungen sind stornierbar; ein Entwurf wird gelöscht („Nur gebuchte Rechnungen können storniert werden. Einen Entwurf bitte löschen.“).

### 3.3 Auswirkungen der Stornierung

1. **Original-Dokument**: `storniert = true`, `storniertAm` gesetzt → dauerhaft gesperrt
2. **Storno-Dokument**: Sofort gebucht und gesperrt → kann nie gelöscht werden
3. **Offene Posten**: Zugehöriger Eintrag wird als bezahlt markiert
4. **Kaskade**: Wird eine Abschlagsrechnung storniert, werden nicht stornierte, gebuchte Schlussrechnungen am selben Vorgänger automatisch mitstorniert; ein Schlussrechnungs-Entwurf bleibt stehen
5. **Projekt-Preis**: Wird automatisch aus verbleibenden Dokumenten neu berechnet
6. **Anfrage-Betrag**: Wird automatisch aktualisiert (falls das Dokument noch an einer Anfrage hängt)
7. **Audit**: `STORNIERT` für das Original (mit Grund; ohne Angabe „Stornierung des Originaldokuments“) und `ERSTELLT` für die Stornorechnung
8. **Beide Dokumente bleiben erhalten** → lückenloser Audit-Trail

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
| `SYSTEM` | Automatische Systemverarbeitung (z. B. automatisches Beenden bei überschrittenem Zeitfenster) |

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

## 7. Fortlaufende Nummerierung

### 7.1 Format

Dokumentnummern folgen dem Format: **`{KÜRZEL}-JJJJ/MM/NNNNN`**

Kürzel: `AG` Angebot, `NA` Nachtragsangebot, `AB` Auftragsbestätigung, `RE` Rechnung, `TR` Teilrechnung, `AR` Abschlagsrechnung, `SR` Schlussrechnung, `GU` Gutschrift, `ST` Stornorechnung.

Beispiel: `RE-2026/03/00001`

### 7.2 Implementierung

- Separate Counter-Tabelle `ausgangs_geschaeftsdokument_counter` mit Key `JJJJ/MM`
- Thread-sichere Nummernvergabe durch **pessimistisches Locking** (`findByMonatKeyForUpdate`)
- **Ein gemeinsamer Zähler pro Monat für alle Dokumenttypen** – das Kürzel unterscheidet nur den Typ
- Die Nummer wird beim Anlegen vergeben und nie wiederverwendet; die Dokumentnummer ist in der Datenbank eindeutig

**Lücken und ihre Erklärung:** Weil alle Typen einen Zähler teilen, sind z. B. Rechnungsnummern allein nicht lückenlos aufeinanderfolgend (dazwischen liegen Angebote, ABs usw.). Wird ein Entwurf gelöscht, fehlt seine Nummer ebenfalls. Jede Nummer lässt sich aber über den Audit-Trail erklären: `dokumente.csv` und `audit.csv` im Z3-Paket enthalten alle Dokumente und alle Löschungen, und `GET /api/ausgangs-dokumente/historie/nummer/{dokumentNummer}` zeigt den Verlauf auch für gelöschte Entwürfe.

### 7.3 Monatliche Zähler-Einheit

| Monat-Key | Zähler | Nächstes Dokument (z. B. Rechnung) |
|---|---|---|
| `2026/01` | 12 | `RE-2026/01/00013` |
| `2026/02` | 5 | `RE-2026/02/00006` |
| `2026/03` | 0 | `RE-2026/03/00001` |

---

## 8. Dokumentenketten und Datenintegrität

### 8.1 Vorgänger–Nachfolger-Kette

Jedes `AusgangsGeschaeftsDokument` kann auf einen Vorgänger verweisen (`vorgaenger_id` FK). Dies sichert die lückenlose Dokumentenkette:

```
Angebot (AG-2026/01/00001)
   └→ AB (AB-2026/01/00002) [vorgaenger_id → Angebot]
       └→ Abschlagsrechnung 1 (AR-2026/02/00001) [vorgaenger_id → AB]
       └→ Abschlagsrechnung 2 (AR-2026/02/00003) [vorgaenger_id → AB]
       └→ Schlussrechnung (SR-2026/03/00001) [vorgaenger_id → AB]
```

### 8.2 Storno-Kette

Bei Stornierung verweist das Storno-Dokument auf das Original:

```
Rechnung (RE-2026/02/00001) [storniert = true]
   └→ STORNO (ST-2026/02/00002) [vorgaenger_id → Rechnung, gebucht = true]
```

---

## 9. Aufbewahrungsfristen

### 9.1 Gesetzliche Fristen (§ 257 HGB / § 147 AO)

| Dokumentart | Frist | Rechtsgrundlage |
|---|---|---|
| Bücher, Aufzeichnungen, Inventare, Jahresabschlüsse | **10 Jahre** | § 147 Abs. 1 Nr. 1, Abs. 3 AO |
| Buchungsbelege, darunter Rechnungen (Eingang & Ausgang) | **8 Jahre** (seit 01.01.2025, vorher 10 Jahre) | § 147 Abs. 1 Nr. 4, Abs. 3 AO; § 14b UStG |
| Geschäftsbriefe (empfangen & gesendet) | **6 Jahre** | § 147 Abs. 1 Nr. 2/3 AO |
| Angebote (angenommene) | **6 Jahre** | § 147 Abs. 1 Nr. 2/3 AO |
| Auftragsbestätigungen | **6 Jahre** | § 147 Abs. 1 Nr. 2/3 AO |

Die Verkürzung auf 8 Jahre stammt aus dem Vierten Bürokratieentlastungsgesetz. Fristen im Einzelfall mit dem Steuerberater abstimmen.

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

Der **entry_hash** ist `SHA-256(kanonische Form | previous_hash)`. Die kanonische Form (`canonicalForm()`) verbindet die Felder in fester Reihenfolge mit `|` (leere Werte als Leerstring, Zeitstempel ISO-8601):

```
chain_index | dokumentId | aktion | dokumentNummer | typ | datum | betreff |
betragNetto | betragBrutto | mwstSatz | abschlagsNummer | projektId | anfrageId |
kundeId | vorgaengerId | versandDatum | gebucht | gebuchtAm | storniert |
storniertAm | digitalAngenommen | inhaltHash | geaendertVon (Id) |
geaendertAm | aenderungsgrund | ipAdresse
```

`inhaltHash` ist der SHA-256 des HTML-Inhalts (`htmlInhalt`) zum Zeitpunkt der Aktion. Der Hash wird über die **gespeicherte** Form des Eintrags berechnet, damit er beim Prüfen exakt reproduzierbar ist.

### 10.3 Atomares Anhängen

Der Kettenkopf liegt in der Singleton-Tabelle `audit_chain_state` (Zeile id=1). Beim Anhängen eines neuen Eintrags wird diese Zeile **pessimistisch gelockt** (`SELECT … FOR UPDATE`), sodass zwei parallele Aktionen niemals denselben `previous_hash` erhalten.

### 10.4 Backfill und Reparatur

- **Backfill:** Beim Start verkettet der `AuditChainBackfillRunner` (Spring `ApplicationRunner`) alle Audit-Einträge, die noch keinen `chain_index` haben, in chronologischer Reihenfolge. Ist die Kette vollständig, tut er nichts.
- **Einmalige Reparatur:** Nur mit dem Start-Schalter `audit.chain.rebuild-on-start=true` baut der `AuditChainRebuildRunner` die Kette über die gespeicherten Werte neu auf (gedacht für Altbestände nach einem früheren Hash-Fehler). Vorher den Audit-CSV-Export sichern und den Schalter danach **wieder entfernen** – sonst würde jede Manipulation beim nächsten Start „geheilt“. Einen HTTP-Endpoint dafür gibt es bewusst nicht.

### 10.5 Selbstverifikation

In der Oberfläche: Vorlagen & Stammdaten → Administration → **Firma** → Reiter **„Steuerprüfung“** → Abschnitt „Hash-Kette verifizieren“ → **„Kette prüfen“**. Ergebnis: „Hash-Kette intakt – … Einträge geprüft“ oder „Hash-Kette gebrochen – Manipulation nachweisbar“.

Technisch prüft der Endpoint `GET /api/ausgangs-dokumente/audit/verify` die gesamte Kette (lückenloser `chain_index` ab 0, `previous_hash`, `entry_hash`) und liefert einen Bericht:

```json
{
  "intakt": true,
  "gesamtAnzahl": 1247,
  "letzterChainIndex": 1246,
  "letzterEntryHash": "a3f9...",
  "fehler": []
}
```

Bei Manipulation enthält `fehler` die gefundene(n) Bruchstelle(n) mit `auditId`, `chainIndex`, `dokumentNummer`, Fehlerart (`typ`) und Beschreibung.

### 10.6 GoBD-Z3-Paket (Datenträgerüberlassung)

In der Oberfläche: Firma → Reiter **„Steuerprüfung“** → „Audit-Trail für Steuerprüfung“: Zeitraum wählen (Schnellauswahl aktuelles Jahr und die zwei Vorjahre oder Von/Bis), dann **„Z3-Prüfungs-Paket (ZIP)“** oder **„Nur CSV“**. Die Dateien enthalten personenbezogene Daten (Bearbeiter-IDs, IP-Adressen) – nur an autorisierte Prüfer weitergeben.

Der Endpoint `GET /api/ausgangs-dokumente/audit/z3-paket?von=JJJJ-MM-TT&bis=JJJJ-MM-TT` erzeugt ein ZIP-Paket für die **Datenträgerüberlassung (Z3)** nach GoBD:

| Datei im ZIP | Inhalt |
|---|---|
| `dokumente.csv` | Alle Ausgangs-Geschäftsdokumente im Zeitraum (Stammdaten) |
| `audit.csv` | Vollständiger Audit-Log mit Hash-Kette (chain_index, previous_hash, entry_hash) |
| `INFO.txt` | Erklärung des Formats, Prüfanleitung für Steuerprüfer |
| `manifest.sha256` | SHA-256-Prüfsummen aller Dateien im Paket |

Weitere Endpunkte: `GET /api/ausgangs-dokumente/audit/export?von=…&bis=…` (nur CSV) und `GET /api/ausgangs-dokumente/audit/anzahl?von=…&bis=…` (Anzahl der Einträge für die Vorschau).

### 10.7 Welche Aktionen protokolliert werden

| Aktion (`AusgangsGeschaeftsDokumentAuditAktion`) | Wann | Für welche Typen |
|---|---|---|
| `ERSTELLT` | Anlegen (auch Stornorechnung) | Rechnungstypen, Gutschrift, Storno |
| `GEBUCHT` | Festschreibung beim Drucken/Export/E-Mail | Rechnungstypen, Gutschrift, Storno |
| `VERSENDET` | E-Mail-Versand | Rechnungstypen, Gutschrift, Storno |
| `STORNIERT` | Stornierung des Originals | Rechnungstypen |
| `GELOESCHT` | Löschen eines Entwurfs (mit Begründung) | alle Typen |
| `DIGITAL_ANGENOMMEN` | Digitale Annahme durch den Kunden | Angebot, Nachtragsangebot, Auto-Auftragsbestätigung |
| `GEAENDERT` | Nachträgliche Korrektur mit Begründung (derzeit nur durch den Admin-Wartungslauf `POST /api/admin/projekte/rabatt-betraege-korrigieren`, der Altbeträge mit Pauschalrabatt nachrechnet – auch bei gebuchten Rechnungen) | betroffene Dokumente |

Bearbeitungen von Entwürfen vor der Buchung werden nicht einzeln protokolliert – sie sind noch nicht festgeschrieben. Den Verlauf eines Dokuments zeigt das Aktionsmenü → **„Verlauf anzeigen“** (`GET /api/ausgangs-dokumente/{id}/historie`).

---

## 11. Eingangsbelege & Lieferantendokumente

### 11.1 Erfassung mit Belegfoto-Pflicht

Belege für Kasse und Buchhaltung (Quittungen, Bons, per Handy gescannte Rechnungen) werden in der Entity `Beleg` geführt. Eine **harte DB-Invariante** (Check-Constraint `chk_beleg_datei_oder_umbuchung`) stellt sicher, dass jeder Beleg eine Bildkopie hat:

- `gespeicherterDateiname` muss für alle echten Belege gesetzt sein
- Nur reine Umbuchungen (`istUmbuchung = true`) dürfen ohne Bilddatei existieren
- Original-Dateien werden im konfigurierten Upload-Verzeichnis archiviert

Lieferantenrechnungen, Lieferscheine usw., die per E-Mail oder Upload eingehen, liegen als `LieferantDokument` mit der Originaldatei vor (siehe `DOKUMENTEN_LIFECYCLE.md`, Abschnitt 2). Wird eine Rechnung am Handy gescannt, verweist das Lieferanten-Dokument auf den zugehörigen `Beleg`.

### 11.2 KI-gestützte Vorerfassung (Status: Vorsortierung, nicht Buchung)

Belege durchlaufen nach dem Upload einen KI-Analyse-Workflow (`BelegKiAnalyseStatus`: PENDING, LAEUFT, DONE, FAILED), der Vorschläge für Lieferant, Kategorie und Aufteilung auf Kostenstellen liefert. Lieferanten-Dokumente werden ebenso vorausgelesen (ZUGFeRD → XML → KI). **Wichtige Einordnung im Sinne der GoBD:**

- Die KI-Klassifizierung ist eine **Vorsortierung als Komfort-Feature**, keine steuerliche Buchung.
- Die endgültige Kontierung erfolgt beim Steuerberater in zertifizierter Buchhaltungssoftware.
- Der Beleg selbst (Bild + Stammdaten) bleibt unverändert; nur Metadaten werden ergänzt.

### 11.3 Monatsexport für den Steuerberater

Der `BelegeKasseExportPdfService` erzeugt das **Monats-Kassenbuch als PDF** (Briefkopf, Festschreibungsstand, fortlaufendes Journal mit laufendem Kassenbestand, Kassenstürze und die Belegbilder – ein Beleg pro Seite mit Nummer und Fingerabdruck der Datei), das an den Steuerberater übergeben wird. Zusammen mit dem DATEV-Export entspricht das dem Lieferweg „Vorsystem → Buchhaltung“ und ist die GoBD-relevante Übergabestelle.

### 11.4 Hash-Kette des Kassenbuchs

Das Kassenbuch hat eine **eigene** Hash-Kette (`BelegAudit`, Kettenkopf `beleg_audit_chain_state`), baugleich zu der der Ausgangsrechnungen, aber bewusst getrennt. Protokolliert werden u. a. Erfassen, Ändern, Prüfen, Verwerfen, Festschreiben, Stornieren, Kassensturz und Monatsabschluss. Details: `KASSE_BUCHHALTUNG.md`.

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

**Aktueller Stand:** Alle Dokumente bleiben dauerhaft erhalten; es gibt keinen Automatismus, der nach Ablauf der Aufbewahrungsfristen Belege archiviert oder zur Löschung freigibt. Manuelle Bereinigung nur in Abstimmung mit dem Steuerberater.

**Bewertung:** Dauerhaftes Aufbewahren ist GoBD-konform und in der Praxis bei einem Datenvolumen, wie es ein Handwerksbetrieb erzeugt, unkritisch. Eine Automatisierung ist daher nicht priorisiert.

### 12.3 Verfahrensdokumentation

**Aktueller Stand:** Dieses Dokument (`GOBD_COMPLIANCE.md`) zusammen mit `DOKUMENTEN_LIFECYCLE.md`, `RECHNUNGSWESEN.md` und `KASSE_BUCHHALTUNG.md` ist die technische Verfahrensdokumentation des Vorsystems.

**Empfehlung:** Betriebe sollten zusätzlich eine **betriebliche Verfahrensdokumentation** führen, die beschreibt:

- Welche Belege wo entstehen (ERP / Kassenprogramm / E-Mail-Postfach)
- Wer Belege erfasst und freigibt
- Wann die Übergabe an den Steuerberater erfolgt
- Wie das Backup organisiert ist

Diese betriebliche Verfahrensdokumentation ist nicht Teil der Software, sondern ein organisatorisches Dokument des Betriebs.

### 12.4 Backup-Strategie

**Aktueller Stand:** Bei der empfohlenen Docker-Installation beim Kunden sichert der Updater jede Nacht (Standard 2 Uhr) die PostgreSQL-Datenbank und spiegelt das Upload-Verzeichnis auf eine externe Festplatte und/oder in einen Cloud-Ordner (siehe `INSTALLATION.md`). Das Ergebnis jedes Laufs meldet er an das ERP: Admins sehen fehlgeschlagene oder nur teilweise gelungene Sicherungen – und den Fall „seit über einem Tag keine Sicherung“ – unter **Benachrichtigungen** (Thema „Sicherung & Updates“). Bei anderen Installationsarten (eigene Docker-Umgebung, JAR) liegt die Sicherung weiterhin beim Betreiber.

**Empfehlung:** Sicherungsziel außer Haus einrichten und die Benachrichtigungen im Blick behalten. Ohne Backup der Datenbank **und** des Upload-Verzeichnisses verlieren die hier beschriebenen GoBD-Maßnahmen ihre Schutzwirkung bei Hardware-Defekt.

---

## 13. Zusammenfassung der technischen Maßnahmen

| GoBD-Anforderung | Technische Umsetzung | Entity/Service |
|---|---|---|
| Unveränderbarkeit | `gebucht`-/`digitalAngenommen`-Flag + `istBearbeitbar()` | `AusgangsGeschaeftsDokument` |
| Löschverbot | Validierung in `loeschen()`, Audit-Eintrag `GELOESCHT` vor jeder Löschung | `AusgangsGeschaeftsDokumentService` |
| Storno statt Löschung | `stornieren()` erstellt Gegendokument | `AusgangsGeschaeftsDokumentService` |
| Fortlaufende Nummerierung | Gemeinsamer Monatszähler mit pessimistischem Lock, Kürzel je Typ | `AusgangsGeschaeftsDokumentCounter` |
| Beweis der Kundenannahme | Snapshot + Hash beim Versand und bei der Annahme, Bestätigungscode | `DokumentFreigabe` |
| Schutz vor gleichzeitigem Überschreiben | Bearbeitungssperre + Versionsnummer (HTTP 409) | `DatensatzLock`, `@Version` |
| Audit-Trail Zeitbuchungen | Immutable Snapshots pro Version | `ZeitbuchungAudit` |
| Audit-Trail Korrekturen | Immutable Snapshots pro Version | `ZeitkontoKorrekturAudit` |
| Nachvollziehbarkeit | `geaendertVon`, `geaendertAm`, `geaendertVia` | Audit-Entities |
| Begründungspflicht | `aenderungsgrund` (Pflicht bei Änderung/Storno) | Audit-Entities |
| Dokumentenkette | `vorgaenger_id` FK + `nachfolger`-Liste | `AusgangsGeschaeftsDokument` |
| Doppelerfassungsschutz | `idempotencyKey` (UUID) | `Zeitbuchung` |
| Offene Posten-Tracking | Automatischer Eintrag bei Buchung | `ProjektGeschaeftsdokument` |
| **Manipulationsschutz Audit** | **SHA-256 Hash-Kette** (entry_hash + previous_hash) | **`AusgangsGeschaeftsDokumentAudit`**, **`BelegAudit`** (Kassenbuch) |
| **Maschinenlesbare Prüfung** | **Verify-Endpoint + Z3-ZIP-Export** (Firma → Steuerprüfung) | **`AuditChainVerifier` / `SteuerpruefungZ3ExportService`** |
| Datensicherung | Nächtliche Sicherung mit Statusmeldung (Docker-Installation) | `BetriebsstatusService` |
