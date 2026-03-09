# Architektur-Übersicht – Technische Architektur und Designentscheidungen

## Übersicht

Dieses Dokument beschreibt die technische Architektur des Handwerkerprogramms – den Tech-Stack, die Modul-Struktur, verwendete Design-Patterns und externe Integrationen.

---

## 1. Tech-Stack

| Schicht | Technologie | Version |
|---|---|---|
| **Backend** | Java, Spring Boot, JPA/Hibernate, Flyway | Java 23, Spring Boot 3.2.5 |
| **Datenbank** | MySQL | MySQL 8 |
| **Desktop-Frontend** | React, TypeScript, Vite, Tailwind CSS | React 18, TypeScript 5 |
| **Mobile App** | React PWA (Offline-fähig via IndexedDB) | React 18 |
| **PDF-Generierung** | OpenPDF (iText), Apache PDFBox, Mustang (ZUGFeRD) | – |
| **KI-Integration** | Google Gemini API, Ollama (optional) | – |
| **E-Mail** | Jakarta Mail (IMAP/SMTP) | – |
| **Vektorsuche** | Qdrant Vector Database | – |
| **Build** | Maven (Backend), Vite (Frontend) | – |
| **Deployment** | jpackage (Windows EXE), Docker (optional) | – |

---

## 2. Modul-Struktur

### 2.1 Backend-Architektur

```
Controller → Service → Repository → Domain (Entity)
```

Jede Schicht hat eine klar definierte Verantwortung:

| Schicht | Verantwortung | Anzahl |
|---|---|---|
| **Controller** | REST-Endpoints, Request/Response-Handling | 53 |
| **Service** | Business-Logik, Transaktionen, Validierung | 84 |
| **Repository** | Datenbankzugriff (Spring Data JPA) | 75+ |
| **Domain** | JPA-Entities, Enums, Value Objects | 109 |
| **DTO** | Daten-Transfer-Objekte für API-Kommunikation | diverse |
| **Mapper** | DTO ↔ Entity Konvertierung | diverse |

### 2.2 Package-Struktur

```
org.example.kalkulationsprogramm/
├── controller/              # REST-Endpoints
│   └── advice/              # Exception-Handler (RestExceptionHandler)
├── domain/                  # JPA-Entities und Enums
│   └── converter/           # JPA Attribute-Converter
├── service/                 # Business-Logik
├── repository/              # Spring Data JPA Repositories
│   └── miete/               # Mietverwaltungs-Repositories
├── dto/                     # Daten-Transfer-Objekte
│   ├── Angebot/
│   ├── Bestellung/
│   ├── Geschaeftsdokument/
│   ├── Zugferd/
│   ├── AngebotEmail/
│   └── Kunde/
├── mapper/                  # DTO ↔ Entity Mapper
└── config/                  # Spring-Konfiguration

org.example.email/
├── EmailService.java        # E-Mail-Versand und -Empfang
├── ImapAppendService.java   # IMAP-Operationen
└── ListFolders.java         # IMAP-Ordner-Auflistung
```

---

## 3. Frontend-Architektur

### 3.1 Desktop-Frontend (`react-pc-frontend/`)

- **Technologie:** React 18 + TypeScript + Vite + Tailwind CSS
- **Seiten:** 31 Desktop-Seiten
- **Zweck:** Haupt-UI für die Büroverwaltung
- **Features:** Editoren, Dashboards, Projektmanagement, Rechnungswesen, E-Mail, Bestellwesen

### 3.2 Mobile Zeiterfassung (`react-zeiterfassung/`)

- **Technologie:** React PWA (Progressive Web App)
- **Seiten:** 18 Mobile-Seiten
- **Zweck:** Zeiterfassung für Mitarbeiter im Feld
- **Features:** Start/Stop-Stempeluhr, Offline-Sync, Urlaub, Saldenübersicht
- **Offline-Fähigkeit:** IndexedDB für lokale Speicherung, automatischer Sync bei Reconnect
- **QR-Code:** Konfigurierbare Basis-URL für Cloudflare-Tunnel-Zugang

### 3.3 Kommunikation

Beide Frontends kommunizieren über **REST-API** mit dem Spring Boot Backend auf Port 8082.

---

## 4. Design-Patterns

### 4.1 Audit-Trail-Pattern (Immutability via Snapshots)

**Zweck:** GoBD-konforme Nachvollziehbarkeit aller Änderungen.

**Prinzip:** Bei jeder Änderung wird ein vollständiger Snapshot des Datensatzes erstellt und als unveränderbare Version gespeichert.

**Einsatz:**
- `ZeitbuchungAudit` – Snapshots für Zeitbuchungs-Änderungen
- `ZeitkontoKorrekturAudit` – Snapshots für Zeitkonto-Korrekturen

**Felder pro Snapshot:**
- Vollständige Fachdaten (Kopie)
- `geaendertVon` (wer)
- `geaendertAm` (wann)
- `geaendertVia` (welcher Kanal: MOBILE_APP, DESKTOP, ADMIN_KORREKTUR, IMPORT)
- `aenderungsgrund` (Pflichtbegründung bei Änderung/Storno)
- `version` (fortlaufend: 1 = Ersterfassung, 2+ = Änderungen)

### 4.2 Dokumentketten-Pattern

**Zweck:** Lückenlose Verknüpfung aller Geschäftsdokumente.

**Prinzip:** Jedes Dokument kann auf einen Vorgänger verweisen und hat eine Liste von Nachfolgern.

**Ausgangs-Dokumente (linear):**
```java
@ManyToOne vorgaenger        // FK auf Vorgänger
@OneToMany nachfolger        // Liste der Nachfolger
```

**Lieferanten-Dokumente (M:M):**
```java
@ManyToMany verknuepfteDokumente    // Flexiblere Verknüpfung
@ManyToMany verknuepftVon           // Rückverknüpfung
```

### 4.3 Performance-Caching mit Smart-Invalidierung

**Zweck:** Schnelle Abfrage berechneter Saldenwerte für vergangene Monate.

**Prinzip:** `MonatsSaldo`-Cache mit `gueltig`-Flag:
- Vergangene Monate + Cache gültig → Cache verwenden
- Vergangene Monate + Cache ungültig → Neuberechnung
- Aktueller/Zukünftiger Monat → Immer live berechnen

**Invalidierung:** Bei Änderung von Quelldaten wird `gueltig = false` gesetzt → Lazy Recalculation bei nächster Abfrage.

**Warmup:** `MonatsSaldoWarmupService` berechnet beim Start alle vergangenen Monate vor.

### 4.4 Datei-Deduplizierung

**Zweck:** Keine doppelte Dateiablage bei E-Mail-Anhängen.

**Prinzip:** `LieferantDokument` referenziert `EmailAttachment` über FK statt die Datei zu kopieren:
```java
@ManyToOne EmailAttachment attachment;  // Nur Referenz, keine Kopie
```

### 4.5 Enum-basiertes State Management

**Zweck:** Typsichere Zustände und Klassifizierungen.

**Einsatz:**
- `AusgangsGeschaeftsDokumentTyp` – 8 Dokumenttypen
- `LieferantDokumentTyp` – 6 Lieferanten-Dokumenttypen
- `Dokumenttyp` – 11 Dokumenttypen (inkl. Mahnungen)
- `Mahnstufe` – 3 Mahnstufen
- `Verrechnungseinheit` – 4 Einheiten (kg, lfd. m, m², Stück)
- `AuditAktion` – 3 Aktionen (ERSTELLT, GEAENDERT, STORNIERT)
- `ErfassungsQuelle` – 4 Quellen (MOBILE_APP, DESKTOP, ADMIN_KORREKTUR, IMPORT)

---

## 5. Externe Integrationen

### 5.1 Google Gemini AI

- **Zweck:** Dokumentenanalyse, OCR, E-Mail-Formulierung
- **Service:** `GeminiDokumentAnalyseService`, `KiHilfeController`
- **Modell:** `gemini-flash-latest` (konfigurierbar)
- **Einsatz:** Lieferantenrechnungen ohne ZUGFeRD-Daten werden per KI analysiert

### 5.2 IMAP E-Mail-Import

- **Zweck:** Automatischer E-Mail-Import alle 60 Sekunden
- **Service:** `EmailService`, `ImapAppendService`
- **Features:**
  - Spam-Filter und Thread-Erkennung
  - Automatische Zuordnung zu Kunden/Lieferanten/Projekten
  - Anhänge werden als Lieferantendokumente verarbeitet

### 5.3 ZUGFeRD/Mustang

- **Zweck:** Elektronische Rechnungen erstellen und lesen
- **Bibliothek:** Mustang (Java ZUGFeRD Library)
- **Services:** `ZugferdErstellService`, `ZugferdExtractorService`, `ZugferdConverterService`
- **Standard:** ZUGFeRD 2.0 / Factur-X / PDF/A-3

### 5.4 Qdrant Vector Database

- **Zweck:** RAG (Retrieval-Augmented Generation) für Code-Q&A und Dokumentensuche
- **Service:** `QdrantRagService`
- **Konfiguration:** `ai.rag.enabled = true`
- **Docker:** `docker-compose.yml` enthält Qdrant-Container

---

## 6. Datenbank-Migrationen (Flyway)

### 6.1 Konfiguration

- **Pfad:** `classpath:db/migration`
- **Aktiviert:** `spring.flyway.enabled=true`
- **Baseline:** Flyway verwaltet alle Datenbank-Schemata

### 6.2 Migrationen (Auswahl)

| Version | Beschreibung |
|---|---|
| `V150` | Umbenennung Kostenstelle → Miete-Kostenstelle |
| `V151` | Neue Tabellen für Mietverwaltung |
| `V152` | Neue Spalten und Foreign Keys |
| `V200` | Datenbereinigung (Orphan-Kostenstellen) |
| `V201` | Migration Dokumenttyp: Entity → Enum |
| `V202` | Alte Dokumenttyp-Tabelle entfernt |
| `V203` | Nicht-Rechnungs-Dokumente entsperrt |
| `V204` | MonatsSaldo-Cache-Tabelle erstellt |
| `V205` | Fälligkeitsdatum-Feld nachgetragen |
| `V206` | Rechnung/Gutschrift zu Dokumenttyp-Enum |
| `V207` | Buchungs-Zeitfenster für Zeitkonto |

### 6.3 Namenskonvention

```
V{VERSION}__{beschreibung}.sql
```

Versionsnummern sind aufsteigend und fortlaufend.

---

## 7. Konfiguration

### 7.1 Anwendungskonfiguration (`application.properties`)

| Bereich | Konfiguration |
|---|---|
| **Server** | Port 8082 |
| **Datenbank** | MySQL localhost:3307, UTF-8MB4, German Collation |
| **Connection Pool** | HikariCP, READ_COMMITTED Isolation |
| **Datei-Upload** | Max. 15 GB, verschiedene Verzeichnisse für Uploads, Bilder, Anhänge |
| **KI** | Gemini API-Key, Modell `gemini-flash-latest`, RAG aktiviert |
| **E-Mail** | SMTP-Konfiguration, IMAP-Import |
| **Vendor** | Microsoft Azure, Amazon Business (optional) |

### 7.2 Profile

- `application.properties` – Basiskonfiguration
- `application-local.properties` – Lokale Überschreibungen (DB-Zugangsdaten)

---

## 8. Deployment

### 8.1 Optionen

| Methode | Beschreibung |
|---|---|
| **jpackage** | Windows-Installer (EXE) mit eingebettetem JRE |
| **Docker** | Optional via `docker-compose.yml` (Qdrant) |
| **JAR** | Standalone Spring Boot JAR |

### 8.2 Build-Befehle

```bash
# Backend bauen
./mvnw clean package

# Frontend bauen
cd react-pc-frontend && npm run build

# Windows-Installer
./mvnw jpackage:jpackage

# Tests ausführen
./mvnw test
```

### 8.3 Deployment-Skripte

Im Verzeichnis `deployment/scripts/` befinden sich Skripte für:
- Backup
- Autostart
- Restart

Detaillierte Anleitung: [`deployment/README.md`](../deployment/README.md)

---

## 9. Projekt-Kennzahlen

| Metrik | Wert |
|---|---|
| REST-Controller | 53 |
| Business-Services | 84 |
| Domain-Entities | 109 |
| Spring Data Repositories | 75+ |
| Desktop-Seiten (PC) | 31 |
| Mobile-Seiten (PWA) | 18 |
| Flyway-Migrationen | 207+ |
| Java-Dateien gesamt | 505 |
