# Architektur-Übersicht – Technische Architektur und Designentscheidungen

## Übersicht

Dieses Dokument beschreibt die technische Architektur des Handwerkerprogramms – den Tech-Stack, die Modul-Struktur, verwendete Design-Patterns und externe Integrationen.

---

## 1. Tech-Stack

| Schicht | Technologie | Version |
|---|---|---|
| **Backend** | Java, Spring Boot (Web, Security, Data JPA/Hibernate, Actuator), Flyway | Java 25, Spring Boot 4.1 |
| **Datenbank** | PostgreSQL (einzige Datenbank; H2 nur in den automatischen Tests) | PostgreSQL 16 |
| **Desktop-Frontend** | React, TypeScript, Vite, Tailwind CSS | React 19, TypeScript 5.9, Vite 7, Tailwind 3 |
| **Mobile App** | React-PWA (`vite-plugin-pwa`, eigener Service Worker, Offline-Speicher in IndexedDB über `idb`) | React 19, Vite 7, Tailwind 4 |
| **PDF-Generierung** | OpenPDF (iText-Fork), Apache PDFBox, Mustang (ZUGFeRD) | – |
| **KI-Integration (optional)** | Google Gemini API, Ollama | – |
| **E-Mail** | Jakarta Mail (IMAP/SMTP), mehrere Postfächer | – |
| **KI-Hilfe-Suche (RAG)** | Vektorsuche im eigenen Prozess (`LocalRagService`, Embeddings über Gemini) | – |
| **Push & QR** | Web Push mit VAPID (`nl.martijndwars:web-push`), QR-Codes mit ZXing | – |
| **Build & Tests** | Maven Wrapper (Backend, JUnit 5), npm/Vite (Frontends, Vitest, Playwright) | – |
| **Deployment** | Docker-Image aus `ghcr.io` (gebaut in `.github/workflows/docker-image.yml`), Betrieb beim Kunden mit `deployment/docker-server` | – |

---

## 2. Modul-Struktur

### 2.1 Backend-Architektur

```
Controller → Service → Repository → Domain (Entity)
```

Jede Schicht hat eine klar definierte Verantwortung (Anzahlen: Stand 12.10.2026, gezählt nach Annotation bzw. Basis-Interface):

| Schicht | Verantwortung | Anzahl |
|---|---|---|
| **Controller** | REST-Endpoints, Request/Response-Handling | 97 |
| **Service** | Business-Logik, Transaktionen, Validierung | 191 |
| **Repository** | Datenbankzugriff (Spring Data JPA) | ca. 110 |
| **Domain** | JPA-Entities, Enums, Value Objects | 125 Entities |
| **DTO** | Daten-Transfer-Objekte für API-Kommunikation | diverse |
| **Mapper** | DTO ↔ Entity Konvertierung | diverse |

### 2.2 Package-Struktur

```
org.example.kalkulationsprogramm/
├── controller/              # REST-Endpoints
│   ├── advice/              # Exception-Handler (RestExceptionHandler)
│   └── miete/               # Mietabrechnung
├── domain/                  # JPA-Entities und Enums
│   ├── converter/           # JPA Attribute-Converter (LieferantDokumentTypConverter)
│   └── miete/               # Mietabrechnung (Mietobjekt, Kostenstelle, Verteilungsschlüssel …)
├── service/                 # Business-Logik
│   ├── benachrichtigung/    # Benachrichtigungen (Glocke), Meldungen von Sicherung und Nachtupdate
│   ├── mail/                # Postfach-Zugänge, SMTP-Versand, Verschlüsselung der Mail-Passwörter
│   ├── miete/               # Mietabrechnung, Kostenverteilung, PDFs
│   ├── pdf/                 # PDF-Hilfen
│   └── telefon/             # Telefonanlage (FRITZ!Box): Anrufliste, Anrufbeantworter, Live-Anrufe
├── repository/              # Spring Data JPA Repositories
│   └── miete/               # Mietverwaltungs-Repositories
├── dto/                     # Daten-Transfer-Objekte, je Fachbereich ein Unterordner, z. B.
│   ├── AusgangsGeschaeftsDokument/
│   ├── Anfrage/
│   ├── Bestellung/
│   ├── Email/
│   ├── Postfach/
│   ├── Zugferd/
│   ├── Kunde/
│   └── miete/
├── mapper/                  # DTO ↔ Entity Mapper
├── config/                  # Spring-Konfiguration, Security-Filter, Flyway-Startsetup, Backfill-Runner
├── event/                   # Spring-Events (EmailAddressChangedEvent und Listener)
├── exception/               # Fachliche Exceptions (NotFoundException, ForbiddenException …)
├── util/                    # Hilfsklassen (E-Mail-HTML, verständliche DB-Fehlermeldungen, PDF-Schriften)
├── cli/                     # RAG-Datei der KI-Hilfe von der Kommandozeile (StandaloneRagIndexer)
└── tools/                   # Einmal-Werkzeuge (E-Mail-HTML-Backfill)

org.example.email/
├── EmailService.java        # E-Mail-Versand und -Empfang (inkl. Gesendet-Kopie)
├── EingebetteteBilder.java  # Im HTML eingebettete Bilder vor dem Versand als Inline-Anhang
└── ListFolders.java         # IMAP-Ordner-Auflistung
```

---

## 3. Frontend-Architektur

Beide Frontends werden mit Vite gebaut. Die Build-Ausgabe liegt eingecheckt unter `src/main/resources/static` (PC-Oberfläche) bzw. `src/main/resources/static/zeiterfassung` (Handy-App) und wird vom Spring-Boot-Backend ausgeliefert. Das Docker-Image baut beide Frontends beim Erstellen frisch aus dem Quellcode (`Dockerfile`, Stufe 1).

### 3.1 Desktop-Frontend (`react-pc-frontend/`)

- **Technologie:** React 19 + TypeScript + Vite 7 + Tailwind CSS 3
- **Seiten:** 44 Seiten-Komponenten unter `src/pages/`, dazu fachliche Bausteine unter `src/features/` (Stand 12.10.2026)
- **Zweck:** Haupt-UI für die Büroverwaltung, erreichbar unter `/`
- **Bereiche (Menüleiste):** Vorlagen & Stammdaten, Projektmanagement, Zeiterfassung, Kommunikation (E-Mail, Telefon, Website), Finanzen & Controlling; dazu System-Einstellungen und Benachrichtigungen

### 3.2 Mobile Zeiterfassung (`react-zeiterfassung/`)

- **Technologie:** React 19 + TypeScript + Vite 7 + Tailwind CSS 4, PWA mit eigenem Service Worker (`src/sw.ts`)
- **Seiten:** 23 Seiten-Komponenten unter `src/pages/` (Stand 12.10.2026)
- **Zweck:** Zeiterfassung und Baustellen-Alltag für Mitarbeiter im Feld, erreichbar unter `/zeiterfassung/`
- **Features:** Start/Stop-Stempeluhr, Offline-Sync, Urlaub und Abwesenheiten, Saldenübersicht, Kalender mit Erinnerungen (Web Push), Projekt- und Anfrage-Notizen mit Fotos, Belege scannen, Lieferscheine und Reklamationen, Spracheingabe (nur mit KI-Schlüssel), MwSt-Rechner
- **Offline-Fähigkeit:** IndexedDB für lokale Speicherung (`OfflineService`), automatischer Sync bei Reconnect
- **QR-Code:** Anmeldung per QR-Code mit dem persönlichen Mitarbeiter-Token. Die Adresse im QR-Code kommt aus `zeiterfassung.base-url` (Docker: `ZEITERFASSUNG_URL`), z. B. die Tailscale-Adresse des ERP-Rechners

### 3.3 Kommunikation

Beide Frontends kommunizieren über **REST-API** mit dem Spring Boot Backend auf Port 8080 (gleiche Adresse, unter der auch die Oberflächen ausgeliefert werden). Live-Anrufe der Telefonanlage kommen per Server-Sent Events, Kalender-Erinnerungen auf dem Handy per Web Push.

### 3.4 Anmeldung und Zugriffsschutz

- **Desktop:** Sitzung nach Anmeldung mit Benutzername und Passwort, CSRF-Schutz (`SecurityConfig`). HTTP Basic ist deaktiviert.
- **Handy-App:** Mitarbeiter-Token im Header `X-Auth-Token` (`MobileTokenAuthenticationFilter`), erlaubt nur die Methoden und Routen aus `MobileApiPolicy`; Fehlversuche bremst `TokenAttemptLimiter`.
- **Netzgrenze:** `ZeiterfassungSecurityFilter` lässt von außen nur die Handy-App durch und weist im eigenen Netz fremde Adressen ab (Schutz vor DNS-Rebinding, `ErlaubteAdressen`).
- **Website-Schnittstelle:** `/api/internal/**` nur mit gemeinsamem Schlüssel (`WebsiteInternalApiKeyFilter`).

Details: [MOBILE_TOKEN_SECURITY.md](MOBILE_TOKEN_SECURITY.md).

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
- `geaendertVia` (welcher Kanal: MOBILE_APP, DESKTOP, ADMIN_KORREKTUR, IMPORT, SYSTEM)
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

**Warmup:** `MonatsSaldoWarmupService` berechnet nach dem Start (asynchron) alle vergangenen Monate vor.

### 4.4 Datei-Deduplizierung

**Zweck:** Keine doppelte Dateiablage bei E-Mail-Anhängen.

**Prinzip:** `LieferantDokument` referenziert `EmailAttachment` über FK statt die Datei zu kopieren:
```java
@ManyToOne EmailAttachment attachment;  // Nur Referenz, keine Kopie
```

### 4.5 Enum-basiertes State Management

**Zweck:** Typsichere Zustände und Klassifizierungen. In PostgreSQL liegen Enums als `VARCHAR` ohne Werteliste, damit ein neuer Enum-Wert keine Migration braucht.

**Einsatz:**
- `AusgangsGeschaeftsDokumentTyp` – 12 Werte: 9 Dokumenttypen (inkl. Nachtragsangebot) und 3 virtuelle Mahn-Typen für die Anzeige
- `LieferantDokumentTyp` – 8 Lieferanten-Dokumenttypen (inkl. Beleg und Werkstoffzeugnis)
- `Dokumenttyp` – 12 Dokumenttypen (inkl. Mahnungen und Stornorechnung)
- `Mahnstufe` – 3 Mahnstufen
- `Verrechnungseinheit` – 4 Einheiten (kg, lfd. m, m², Stück)
- `AuditAktion` – 3 Aktionen (ERSTELLT, GEAENDERT, STORNIERT)
- `ErfassungsQuelle` – 5 Quellen (MOBILE_APP, DESKTOP, ADMIN_KORREKTUR, IMPORT, SYSTEM)

---

## 5. Externe Integrationen

### 5.1 Google Gemini AI

- **Zweck:** Dokumentenanalyse, OCR, E-Mail-Formulierung, KI-Hilfe, Spracheingabe
- **Optional:** Ohne Schlüssel bleiben die KI-Funktionen aus, alles andere funktioniert. Der Schlüssel wird im ERP unter *System-Einstellungen → KI-Funktionen* gespeichert (Ausweichwert: Property `ai.gemini.api-key`)
- **Service:** `GeminiDokumentAnalyseService`, `KiHilfeController`
- **Modell:** je Funktion konfigurierbar (`ai.gemini.model.*`, `ai.email.model`, `ai.ki-hilfe.model`)
- **Einsatz:** Lieferantenrechnungen ohne ZUGFeRD-Daten werden per KI analysiert

### 5.2 IMAP E-Mail-Import

- **Zweck:** Automatischer E-Mail-Import alle 60 Sekunden, nur mit `email.features.enabled=true` (Standard: aus)
- **Postfächer:** beliebig viele, eingerichtet unter *System-Einstellungen → E-Mail*. Die Passwörter liegen verschlüsselt in der Datenbank (`MailSecretService`, Schlüssel `mail.credentials.encryption-key`)
- **Service:** `EmailImportService`, `EmailService`
- **Features:**
  - Spam-Filter und Thread-Erkennung
  - Automatische Zuordnung zu Kunden/Lieferanten/Projekten
  - Anhänge werden als Lieferantendokumente verarbeitet

### 5.3 ZUGFeRD/Mustang

- **Zweck:** Elektronische Rechnungen erstellen und lesen
- **Bibliothek:** Mustang (Java ZUGFeRD Library)
- **Services:** `ZugferdErstellService`, `ZugferdExtractorService`, `ZugferdConverterService`
- **Standard:** ZUGFeRD 2 / Factur-X / PDF/A-3

### 5.4 KI-Hilfe: Wissen über das Programm (RAG)

- **Zweck:** RAG (Retrieval-Augmented Generation) für Fragen an die KI-Hilfe – Doku und Quellcode als durchsuchbare Abschnitte
- **Services:** `RagIndexer` (`service/kihilfe/`) zerlegt Doku und Quellcode und schreibt die RAG-Datei; `GeminiEinbetter` holt die Embeddings; `LocalRagService` lädt die Datei beim Start und sucht im eigenen Prozess (kein eigener Datenbankdienst)
- **Kundenserver:** Die RAG-Datei entsteht beim Image-Bau in GitHub (`docker-image.yml`, Secret `GEMINI_API_KEY`) und liegt im Image unter `/app/ki-hilfe/rag-cache.json` – mit jedem Nachtupdate kommt die aktuelle
- **Entwicklung:** `LocalRagService` bettet beim Start geänderte Abschnitte selbst ein (`.rag-cache.json`)
- **Konfiguration:** `ai.rag.enabled` (überall an), `ai.rag.datei`, `ai.rag.top-k`, `ai.rag.score-threshold`; Details in `RAG_INDEXING.md`
- **Hinweis:** `QdrantRagService` und der Qdrant-Container in `docker-compose.yml` sind noch vorhanden, werden vom ERP aber nicht genutzt

### 5.5 Telefonanlage (FRITZ!Box)

- **Zweck:** Anrufliste und Anrufbeantworter ins ERP holen, Live-Anrufe anzeigen
- **Services:** `TelefonAbholService` (alle 2 Minuten), `TelefonAnrufmonitorService`, `TelefonLiveService` (Server-Sent Events)
- **Einstellungen:** *System-Einstellungen → Telefon*

### 5.6 Firmen-Website

- **Website → ERP:** Anfrage-Formular, digitale Angebotsfreigabe und Analytics-Snapshot über `/api/internal/**`, geschützt mit `website.internal-api.key`
- **ERP → Website:** Beiträge für den Bereich „Aktuelles“ (`website.beitraege.*`)

---

## 6. Datenbank-Migrationen (Flyway)

### 6.1 Konfiguration

- **Pfad:** `classpath:db/postgresql/migration` (eine Datei je Migration, Anleitung: `src/main/resources/db/postgresql/migration/README.md`)
- **Neuinstallation:** Eine leere Datenbank bekommt zuerst `db/postgresql/basis/V1__basis_schema.sql` (`FlywayStartSetupConfig`, eigene Historientabelle `flyway_basis_history`), danach laufen die Migrationen. Die Basis enthält alles bis V406
- **Aktiviert:** `spring.flyway.enabled=true`, `baseline-on-migrate=true`, `out-of-order=true`
- **Prüfsummen:** `validate-on-migrate=false` (die Historie des eigenen Servers enthält noch Einträge aus der MySQL-Zeit). Dass sich keine Migration nachträglich ändert, prüft `FlywayMigrationenTest`
- **Nur PostgreSQL:** Zeigt die Verbindung auf eine andere Datenbank, bricht der Start ab

### 6.2 Migrationen (Stand 12.10.2026)

| Version | Beschreibung |
|---|---|
| `V407` | E-Mail-Postfächer mit eigenem Zugang |
| `V408` | Sichtbarkeit der Postfächer |
| `V409` | Leere Versionsnummern (`@Version`) auffüllen |
| `V410` | Doppelte Fremdschlüssel entfernen |
| `V411` | Schutzregeln, Kunden-E-Mails mehrfach erlaubt |
| `V412` | DATEV: Soll- oder Ist-Stunden ans Steuerbüro |
| `V413` | Abwesenheitsnotizen pro Postfach |
| `V414` | Erledigte Benachrichtigungen pro Benutzer |
| `V415` | Bestätigungscode per E-Mail für die digitale Freigabe |
| `V416` | E-Mail-Signaturen: Besitzer und Formulardaten |
| `V417` | Reine Ortsvorwahlen in Telefonnummern leeren |
| `V418` | E-Mail-Signaturen: Firmen-Vorlagen |

### 6.3 Namenskonvention

```
V{VERSION}__{beschreibung}.sql
```

Neue Migration: höchste vorhandene Nummer + 1, idempotent geschrieben, Prüfsumme in `FlywayMigrationenTest.CHECKSUMMEN` eintragen. Eine bereits ausgelieferte Migration wird nie mehr geändert.

---

## 7. Konfiguration

### 7.1 Anwendungskonfiguration (`application.properties`)

| Bereich | Konfiguration |
|---|---|
| **Server** | Port 8080 |
| **Datenbank** | PostgreSQL `localhost:5432/kalkulationsprogramm_db` (`APP_DB_URL`, `APP_DB_USER`, `APP_DB_PASS`), UTF-8, `ddl-auto=validate` |
| **Connection Pool** | HikariCP, READ_COMMITTED Isolation |
| **Datei-Upload** | Max. 15 GB (von außen 25 MB), verschiedene Verzeichnisse für Uploads, Bilder, Anhänge |
| **KI** | Gemini-Schlüssel in der Datenbank (optional), Modelle je Funktion, RAG (`ai.rag.enabled`) |
| **E-Mail** | Postfächer (SMTP/IMAP) in der Datenbank, `email.features.enabled` (Abruf, Aufräumen, Lieferanten-Abruf; Standard aus), `mail.credentials.encryption-key` |
| **Sicherheit** | `app.security.allowed-hosts`, `app.cors.allowed-origins`, `zeiterfassung.security.*`, `website.internal-api.key` |
| **Handy-App** | `zeiterfassung.base-url` (QR-Codes), `push.vapid.*` (Web Push) |
| **Betrieb** | `/actuator/health` (nur dieser Endpoint), `erp.betriebsstatus.ordner` (Meldungen von Sicherung und Nachtupdate) |
| **Vendor** | Microsoft Azure, Amazon Business (optional) |

### 7.2 Profile

- `application.properties` – Basiskonfiguration, aktiviert standardmäßig das Profil `local`
- `application-local.properties` – Lokale Überschreibungen (DB-Zugangsdaten, Schlüssel); nicht im Repository, Vorlage `application-local.properties.example`
- `application-docker.properties` – Profil `docker`: Datenbank-Container `postgres`, Uploads unter `/app/uploads`, RAG-Datei aus dem Image (`/app/ki-hilfe/`), E-Mail-Funktionen aus
- Tests: `src/test/resources/application.properties` mit H2 im Speicher

---

## 8. Deployment

### 8.1 Optionen

| Methode | Beschreibung |
|---|---|
| **Docker beim Kunden** | `deployment/docker-server`: PostgreSQL, App-Image (`ghcr.io/…:stable`), Updater (tägliche Sicherung um 2 Uhr, Nachtupdate um 3 Uhr mit Rollback) und Netzsperre |
| **Docker lokal** | `docker-compose.yml` im Projektordner (PostgreSQL, App aus dem lokalen Build, Qdrant); mit Beispieldaten: `scripts/demo/demo-starten.sh` |
| **Handy-Zugang von außen** | Tailscale (App oder Funnel) oder `deployment/mobile-public` (HTTPS-Gateway nur für die Zeiterfassung) |
| **JAR** | Standalone Spring Boot JAR, Datenbank PostgreSQL |

### 8.2 Build-Befehle

```bash
# Frontends bauen (Ausgabe nach src/main/resources/static, wird mitcommittet)
cd react-pc-frontend && npm run build && cd ..
cd react-zeiterfassung && npm run build && cd ..

# Backend bauen (enthält die gebauten Frontends)
./mvnw clean package

# Tests ausführen
./mvnw test
```

Das Docker-Image entsteht in `.github/workflows/docker-image.yml` nach jedem Push auf `main`. Veröffentlicht wird es erst nach einem Starttest (`deployment/docker-server/starttest.sh`) als `:stable` und `:sha-<commit>`. Die Tests laufen vorher im Pull Request (`.github/workflows/pr-ci.yml`).

### 8.3 Deployment-Skripte

Im Verzeichnis `deployment/scripts/` liegen ältere Windows-Skripte für den Betrieb als JAR (aus der Zeit vor `deployment/docker-server`):
- Backup
- Autostart
- Restart
- Update

Für neue Installationen ist `deployment/docker-server` der empfohlene Weg.

Detaillierte Anleitung: [`deployment/README.md`](../deployment/README.md)

---

## 9. Projekt-Kennzahlen

Stand 12.10.2026:

| Metrik | Wert |
|---|---|
| REST-Controller | 97 |
| Business-Services | 191 |
| Domain-Entities | 125 |
| Spring Data Repositories | ca. 110 |
| Seiten-Komponenten (PC) | 44 |
| Seiten-Komponenten (PWA) | 23 |
| Flyway | Basis-Schema (bis V406) + 12 Migrationen (V407–V418) |
| Java-Dateien gesamt | 997 (dazu 449 Test-Dateien) |
