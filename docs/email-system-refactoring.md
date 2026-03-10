# E-Mail System Refactoring - Architektur-Dokumentation

## Erstellt: 2025-12-17
## Status: Analyse & Vorschlag

---

## 1. IST-Zustand: ER-Modell (Entity Relationship)

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           AKTUELLE EMAIL-ENTITIES                                │
└─────────────────────────────────────────────────────────────────────────────────┘

┌──────────────────┐        ┌──────────────────┐        ┌──────────────────┐
│   ProjektEmail   │        │   AngebotEmail   │        │  LieferantEmail  │
├──────────────────┤        ├──────────────────┤        ├──────────────────┤
│ id               │        │ id               │        │ id               │
│ projekt_id (FK)  │        │ angebot_id (FK)  │        │ lieferant_id(FK) │
│ parent_id (FK)   │        │ parent_id (FK)   │        │ recipient        │
│ recipient        │        │ recipient        │        │ cc               │
│ cc               │        │ cc               │        │ fromAddress      │
│ fromAddress      │        │ fromAddress      │        │ subject          │
│ subject          │        │ subject          │        │ htmlBody         │
│ htmlBody         │        │ htmlBody         │        │ body             │
│ body             │        │ body             │        │ rawBody          │
│ rawBody          │        │ rawBody          │        │ sentAt           │
│ sentAt           │        │ sentAt           │        │ direction        │
│ firstViewedAt    │        │ firstViewedAt    │        │ messageId        │
│ direction        │        │ direction        │        │ firstViewedAt    │
│ messageId        │        │ messageId        │        └────────┬─────────┘
└────────┬─────────┘        └────────┬─────────┘                 │
         │                           │                           │
         ▼                           ▼                           ▼
┌──────────────────┐        ┌──────────────────┐        ┌────────────────────────┐
│ ProjektEmailFile │        │ AngebotEmailFile │        │LieferantEmailAttachment│
├──────────────────┤        ├──────────────────┤        ├────────────────────────┤
│ id               │        │ id               │        │ id                     │
│ projektEmail(FK) │        │ angebotEmail(FK) │        │ lieferantEmail_id (FK) │
│ originalFilename │        │ originalFilename │        │ originalFilename       │
│ storedFilename   │        │ storedFilename   │        │ storedFilename         │
│ contentId        │        │ contentId        │        │ contentId              │
│ inlineAttachment │        │ inlineAttachment │        │ inlineAttachment       │
└──────────────────┘        └──────────────────┘        │ processed (KI-Status)  │
                                                        └────────────────────────┘

┌──────────────────────────────────────────────────────────────────────────────────┐
│                           TRACKING & METADATEN                                    │
└──────────────────────────────────────────────────────────────────────────────────┘

┌──────────────────┐        ┌──────────────────────┐        ┌──────────────────┐
│  ProcessedEmail  │        │  ProcessedEmailIndex │        │  EmailFetchState │
├──────────────────┤        ├──────────────────────┤        ├──────────────────┤
│ messageId (PK)   │        │ id                   │        │ folder (PK)      │
│ processedAt      │        │ messageId            │        │ lastUid          │
│ senderDomain ⚠️  │        │ addressHash          │        └──────────────────┘
│ hasAttachments   │        │ folder               │
│ assigned         │        │ uid                  │
│ processedBy(SET) │        │ processedAt          │
└──────────────────┘        └──────────────────────┘

⚠️ senderDomain wird NIE befüllt (BUG!)

┌──────────────────────────────────────────────────────────────────────────────────┐
│                           LIEFERANTEN-DOKUMENTE                                   │
└──────────────────────────────────────────────────────────────────────────────────┘

┌────────────────────────┐        ┌─────────────────────────────┐
│   LieferantDokument    │◄───────│ LieferantGeschaeftsdokument │
├────────────────────────┤        ├─────────────────────────────┤
│ id                     │        │ id                          │
│ lieferant_id (FK)      │        │ dokument_id (FK, 1:1)       │
│ attachment_id (FK) ────┼───────►│ dokumentNummer              │
│ typ (ENUM)             │        │ dokumentDatum               │
│ originalDateiname      │        │ betragNetto/Brutto          │
│ gespeicherterDateiname │        │ mwstSatz                    │
│ uploadDatum            │        │ liefertermin                │
│ uploadedBy_id (FK)     │        │ zahlungsziel                │
└────────────────────────┘        │ skonto*, netto*             │
         ▲                        │ aiConfidence                │
         │                        │ aiRawJson                   │
         │                        └─────────────────────────────┘
         │
    Referenz auf LieferantEmailAttachment
    (kein Kopieren von Dateien)
```

---

## 2. IST-Zustand: Sequenzdiagramm (E-Mail-Verarbeitung)

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                    AKTUELLE EMAIL-VERARBEITUNG (CHAOTISCH)                       │
└─────────────────────────────────────────────────────────────────────────────────┘

┌─────────┐     ┌────────────────┐     ┌───────────────────────┐     ┌─────────────────────────┐
│  IMAP   │     │GlobalEmailWatch│     │ProjektEmailImportSvc  │     │LieferantEmailPipelineSvc│
│ Server  │     │      er        │     │                       │     │                         │
└────┬────┘     └───────┬────────┘     └───────────┬───────────┘     └────────────┬────────────┘
     │                  │                          │                              │
     │   @Scheduled     │                          │                              │
     │   (30 Sek.)      │                          │                              │
     │◄─────────────────┤                          │                              │
     │                  │                          │                              │
     │  fetchMessages() │                          │                              │
     │─────────────────►│                          │                              │
     │                  │   fetchMessages(         │                              │
     │                  │   allSearchTerms)        │                              │
     │                  │─────────────────────────►│                              │
     │                  │                          │                              │
     │                  │   ┌──────────────────────┤                              │
     │                  │   │ Suche INBOX, SENT,   │                              │
     │                  │   │ Archive-Ordner       │                              │
     │                  │   │ LIMIT: 50 Mails!     │                              │
     │                  │   └──────────────────────┤                              │
     │                  │                          │                              │
     │                  │   List<Message>          │                              │
     │                  │◄─────────────────────────┤                              │
     │                  │                          │                              │
     │                  │   ┌──────────────────────┐                              │
     │                  │   │ FOR EACH Message:    │                              │
     │                  │   │ processMessage()     │                              │
     │                  │   └──────────────────────┘                              │
     │                  │                          │                              │
     │                  │   mapAndPersist(msg)     │                              │
     │                  │─────────────────────────►│                              │
     │                  │                          │                              │
     │                  │                          │ ┌────────────────────────────┐
     │                  │                          │ │1. E-Mail als ProjektEmail  │
     │                  │                          │ │   speichern (IMMER!)       │
     │                  │                          │ │                            │
     │                  │                          │ │2. LieferantEmailResolver   │
     │                  │                          │ │   prüft ob Lieferant       │
     │                  │                          │ │                            │
     │                  │                          │ │3. Falls ja: LieferantEmail │
     │                  │                          │ │   separat erstellen        │
     │                  │                          │ │   (Duplikat der Daten!)    │
     │                  │                          │ │                            │
     │                  │                          │ │4. KI-Analyse für PDFs      │
     │                  │                          │ └────────────────────────────┘
     │                  │                          │                              │
     │                  │                          │  ⚠️ ProcessedEmail wird      │
     │                  │                          │     OHNE senderDomain        │
     │                  │                          │     gespeichert!             │
     │                  │                          │                              │
```

---

## 3. PROBLEME der aktuellen Architektur

### 🔴 Problem 1: Daten-Duplikation
```
Email kommt rein ──► ProjektEmail (IMMER gespeichert)
                  └─► LieferantEmail (KOPIE wenn Lieferant erkannt)
                  └─► AngebotEmail (KOPIE wenn Angebot erkannt)
                  
⚠️ Dieselbe E-Mail kann 3x gespeichert werden!
⚠️ Body, Subject, Attachments werden kopiert statt referenziert
```

### 🔴 Problem 2: Fehlende Domain-Speicherung
```java
// LieferantEmailPipelineService.processIncomingEmail() - WIRD NIE AUFGERUFEN!
ProcessedEmail processed = new ProcessedEmail(messageId, LocalDateTime.now());
processed.setSenderDomain(domain);  // ✅ Korrekt, aber...

// ProjektEmailImportService.mapAndPersist() - WIRD TATSÄCHLICH AUFGERUFEN
// HIER WIRD senderDomain NIE GESETZT! ❌
```

### 🔴 Problem 3: Batch-Limit ohne Queue
```java
// GlobalEmailWatcher.checkEmails()
int batchSize = 50;
if (totalFound > batchSize) {
    messages = messages.subList(0, batchSize);  // REST GEHT VERLOREN BIS NÄCHSTER LAUF!
}
// ⚠️ Keine persistente Queue = Verarbeitungslücken
```

### 🔴 Problem 4: Inkonsistente Zuordnung
```
E-Mail kommt rein:
├── projekt.kundenEmails enthält Absender? → ProjektEmail
├── angebot.kundenEmails enthält Absender? → AngebotEmail  
├── lieferant.kundenEmails enthält Domain? → LieferantEmail
└── Sonst? → ProjektEmail mit projekt=null (Orphan!)
```

---

## 4. SOLL-Zustand: Neue Architektur

### Prinzipien:
1. **Single Source of Truth**: Eine zentrale `Email`-Entity statt 3 separate
2. **Referenz statt Kopie**: Zuordnungen über Join-Tabellen
3. **Persistente Queue**: E-Mails werden in Queue gespeichert, dann verarbeitet
4. **Domain-basierte Erkennung**: Automatische Zuordnung über E-Mail-Domains

---

## 5. NEUES ER-Modell (Vorschlag)

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           NEUE EMAIL-ARCHITEKTUR                                 │
└─────────────────────────────────────────────────────────────────────────────────┘

                              ┌──────────────────────┐
                              │     Email (NEU)      │
                              ├──────────────────────┤
                              │ id                   │
                              │ messageId (UNIQUE)   │
                              │ fromAddress          │
                              │ senderDomain ◄───────┼── Automatisch extrahiert!
                              │ recipient            │
                              │ cc                   │
                              │ subject              │
                              │ htmlBody             │
                              │ body                 │
                              │ rawBody              │
                              │ sentAt               │
                              │ direction (IN/OUT)   │
                              │ firstViewedAt        │
                              │ status (ENUM) ◄──────┼── QUEUED, PROCESSING, DONE, ERROR
                              │ processedAt          │
                              └──────────┬───────────┘
                                         │
         ┌───────────────────────────────┼───────────────────────────────┐
         │                               │                               │
         ▼                               ▼                               ▼
┌─────────────────┐           ┌─────────────────┐           ┌─────────────────┐
│  EmailProjekt   │           │  EmailAngebot   │           │ EmailLieferant  │
│  (Join-Tabelle) │           │  (Join-Tabelle) │           │ (Join-Tabelle)  │
├─────────────────┤           ├─────────────────┤           ├─────────────────┤
│ id              │           │ id              │           │ id              │
│ email_id (FK)   │           │ email_id (FK)   │           │ email_id (FK)   │
│ projekt_id (FK) │           │ angebot_id (FK) │           │ lieferant_id(FK)│
│ zugeordnetAm    │           │ zugeordnetAm    │           │ zugeordnetAm    │
└─────────────────┘           └─────────────────┘           └─────────────────┘

                              ┌──────────────────────┐
                              │   EmailAttachment    │
                              ├──────────────────────┤
                              │ id                   │
                              │ email_id (FK)        │
                              │ originalFilename     │
                              │ storedFilename       │
                              │ contentId            │
                              │ inlineAttachment     │
                              │ mimeType             │
                              │ sizeBytes            │
                              │ aiProcessed          │
                              │ aiProcessedAt        │
                              └──────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────────┐
│                           EMAIL PROCESSING QUEUE                                 │
└─────────────────────────────────────────────────────────────────────────────────┘

┌──────────────────────┐
│  EmailProcessingJob  │  ◄── Persistente Queue für die Verarbeitung
├──────────────────────┤
│ id                   │
│ messageId            │
│ imapFolder           │
│ imapUid              │
│ status (ENUM)        │  ── PENDING, IN_PROGRESS, COMPLETED, FAILED, RETRY
│ priority (INT)       │
│ retryCount           │
│ errorMessage         │
│ createdAt            │
│ startedAt            │
│ completedAt          │
│ nextRetryAt          │
└──────────────────────┘
```

---

## 6. NEUES Sequenzdiagramm (Vorschlag)

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                    NEUE EMAIL-VERARBEITUNG (2-PHASEN)                            │
└─────────────────────────────────────────────────────────────────────────────────┘

PHASE 1: EINSAMMELN (Schnell, keine Verarbeitung)
═══════════════════════════════════════════════════

┌─────────┐     ┌────────────────┐     ┌───────────────────────┐
│  IMAP   │     │ EmailCollector │     │ EmailProcessingJob    │
│ Server  │     │    Service     │     │    (Queue/DB)         │
└────┬────┘     └───────┬────────┘     └───────────┬───────────┘
     │                  │                          │
     │   @Scheduled     │                          │
     │   (30 Sek.)      │                          │
     │◄─────────────────┤                          │
     │                  │                          │
     │  Scan INBOX/SENT │                          │
     │─────────────────►│                          │
     │                  │                          │
     │  Neue Message-IDs│                          │
     │◄─────────────────┤                          │
     │                  │                          │
     │                  │  Für jede neue Mail:     │
     │                  │  INSERT INTO Queue       │
     │                  │─────────────────────────►│
     │                  │  (folder, uid, msgId)    │
     │                  │                          │
     │                  │  ✅ KEINE Verarbeitung!  │
     │                  │  ✅ KEINE Content-Lesen! │
     │                  │  ✅ NUR IDs sammeln      │
     │                  │                          │

PHASE 2: VERARBEITEN (Asynchron, Worker-basiert)
══════════════════════════════════════════════════

┌────────────────┐     ┌───────────────────────┐     ┌───────────────────┐
│EmailProcessor  │     │ EmailProcessingJob    │     │    Email (DB)     │
│  Worker        │     │    (Queue)            │     │                   │
└───────┬────────┘     └───────────┬───────────┘     └─────────┬─────────┘
        │                          │                           │
        │   @Scheduled (5 Sek.)    │                           │
        │  oder Async-Worker       │                           │
        │                          │                           │
        │  SELECT * FROM Queue     │                           │
        │  WHERE status=PENDING    │                           │
        │  LIMIT 10                │                           │
        │◄─────────────────────────┤                           │
        │                          │                           │
        │  Für jeden Job:          │                           │
        │  ┌─────────────────────┐ │                           │
        │  │1. IMAP öffnen       │ │                           │
        │  │2. Content laden     │ │                           │
        │  │3. Domain extrahieren│ │                           │
        │  │4. EmailEntity      │ │                           │
        │  │   speichern         │──┼──────────────────────────►│
        │  │5. Zuordnung:        │ │                           │
        │  │   - Projekt?        │ │                           │
        │  │   - Angebot?        │ │                           │
        │  │   - Lieferant?      │ │                           │
        │  │6. Attachments       │ │                           │
        │  │7. KI-Analyse        │ │                           │
        │  └─────────────────────┘ │                           │
        │                          │                           │
        │  UPDATE Queue            │                           │
        │  SET status=COMPLETED    │                           │
        │─────────────────────────►│                           │
        │                          │                           │
```

---

## 7. Status-ENUM für die Queue

```java
public enum EmailJobStatus {
    PENDING,       // Neu in Queue, wartet auf Verarbeitung
    IN_PROGRESS,   // Gerade in Bearbeitung
    COMPLETED,     // Erfolgreich verarbeitet
    FAILED,        // Fehlgeschlagen (nach max Retries)
    RETRY          // Wird erneut versucht
}
```

---

## 8. Migrations-Strategie

### Phase 1: Queue einführen (OHNE bestehende Architektur zu ändern)
1. ✅ Neue Entity `EmailProcessingJob` erstellen
2. ✅ `EmailCollectorService` erstellt nur Queue-Einträge
3. ✅ `EmailProcessorWorker` verarbeitet aus Queue
4. ✅ Bestehende Services bleiben erstmal aktiv

### Phase 2: Domain-Speicherung fixen
1. ✅ `senderDomain` in `ProcessedEmail` korrekt befüllen
2. ✅ Backfill für bestehende Einträge

### Phase 3: Neue Email-Entity (optional, später)
1. 🔄 Zentrale `Email`-Entity einführen
2. 🔄 Join-Tabellen für Zuordnungen
3. 🔄 Migration der bestehenden Daten

---

## 9. Vorteile der neuen Architektur

| Aspekt | IST (Chaotisch) | SOLL (Queue-basiert) |
|--------|-----------------|----------------------|
| **Datenduplikation** | E-Mail 3x gespeichert | E-Mail 1x, Zuordnung via FK |
| **Batch-Overflow** | 50+ Mails = Verlust bis nächster Lauf | Queue = Alle Mails erfasst |
| **Retry bei Fehler** | Keine Wiederholung | Automatischer Retry |
| **Domain-Tracking** | Nie gespeichert | Immer extrahiert |
| **Skalierbarkeit** | Sequentiell im Scheduler | Parallel mit Workern |
| **Monitoring** | Nur Logs | Queue-Statistiken |

---

## 10. Nächste Schritte

1. **Entity erstellen**: `EmailProcessingJob`
2. **Repository erstellen**: `EmailProcessingJobRepository`
3. **Service erstellen**: `EmailCollectorService` (sammelt nur IDs)
4. **Worker erstellen**: `EmailProcessorWorker` (verarbeitet asynchron)
5. **GlobalEmailWatcher refactoren**: Nutzt Collector + Worker
6. **senderDomain-Bug fixen**: In mapAndPersist() Domain speichern
7. **Tests anpassen**

## ✅ IMPLEMENTIERT (2025-12-17)

### Neue Dateien erstellt:

| Datei | Typ | Beschreibung |
|-------|-----|--------------|
| `domain/EmailProcessingJob.java` | Entity | Queue-Tabelle für E-Mail-Jobs |
| `domain/EmailJobStatus.java` | Enum | Status: PENDING, IN_PROGRESS, COMPLETED, FAILED, RETRY |
| `repository/EmailProcessingJobRepository.java` | Repository | Queries für Queue-Verwaltung |
| `service/EmailCollectorService.java` | Service | Phase 1: Sammelt Message-IDs (schnell) |
| `service/EmailProcessorWorker.java` | Service | Phase 2: Verarbeitet Jobs asynchron |
| `service/EmailQueueScheduler.java` | Scheduler | Orchestriert Collector + Processor |
| `controller/EmailQueueController.java` | REST-API | Admin-Monitoring und manuelle Trigger |

### API-Endpunkte:

```
GET  /api/admin/email-queue/stats          → Queue-Statistiken
GET  /api/admin/email-queue/failed         → Fehlgeschlagene Jobs
GET  /api/admin/email-queue/retry          → Retry-Jobs
POST /api/admin/email-queue/collect        → Manuell E-Mails sammeln
POST /api/admin/email-queue/process        → Manuell Jobs verarbeiten
POST /api/admin/email-queue/{id}/retry     → Einzelnen Job zurücksetzen
POST /api/admin/email-queue/toggle         → Queue-Modus an/aus
DELETE /api/admin/email-queue/cleanup      → Alte Jobs löschen
POST /api/admin/email-queue/backfill-domains → senderDomain nachträglich setzen
```

### Komponentendiagramm (Neu):

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        NEUE QUEUE-BASIERTE ARCHITEKTUR                       │
└─────────────────────────────────────────────────────────────────────────────┘

                    ┌─────────────────────────┐
                    │   EmailQueueScheduler   │
                    │   (@Scheduled)          │
                    └───────────┬─────────────┘
                                │
            ┌───────────────────┼───────────────────┐
            │                   │                   │
            ▼                   ▼                   ▼
┌───────────────────┐  ┌────────────────────┐  ┌──────────────────┐
│EmailCollectorSvc  │  │EmailProcessorWorker│  │EmailQueueControl-│
│                   │  │                    │  │ler (REST API)    │
│ - Alle 30 Sek.    │  │ - Alle 5 Sek.      │  │                  │
│ - Sammelt IDs     │  │ - 10 Jobs/Batch    │  │ - Monitoring     │
│ - Kein Content    │  │ - Lädt Content     │  │ - Manuell Trigger│
└─────────┬─────────┘  │ - Delegiert an     │  │ - Retry-Mgmt     │
          │            │   bestehende Svc   │  └──────────────────┘
          │            └─────────┬──────────┘
          │                      │
          ▼                      ▼
┌─────────────────────────────────────────┐
│        EmailProcessingJob (DB)          │
│     (Persistente Queue/Tabelle)         │
├─────────────────────────────────────────┤
│ status: PENDING → IN_PROGRESS           │
│         → COMPLETED / FAILED / RETRY    │
│                                         │
│ senderDomain: Immer extrahiert! ✅      │
└─────────────────────────────────────────┘
          │
          ▼
┌─────────────────────────────────────────┐
│     BESTEHENDE SERVICES (unverändert)   │
├─────────────────────────────────────────┤
│ - ProjektEmailImportService             │
│ - LieferantEmailPipelineService         │
│ - GeminiDokumentAnalyseService          │
│ - etc.                                  │
└─────────────────────────────────────────┘
```

### Nächste Schritte:

1. **Test im Produktivbetrieb**: Queue-Modus aktiviert, beobachten
2. **GlobalEmailWatcher deaktivieren**: Wenn Queue stabil läuft
3. **Backfill ausführen**: `POST /api/admin/email-queue/backfill-domains`
4. **LieferantBackfill testen**: Mit korrekten senderDomains sollte es funktionieren

---

## 11. MIGRATION: Was passiert mit bestehenden Daten?

### ❌ NICHTS LÖSCHEN! ✅

Die bestehenden Daten bleiben **vollständig erhalten**. Die neue Queue arbeitet **parallel**:

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                        BESTEHENDE DATEN (bleiben!)                               │
├─────────────────────────────────────────────────────────────────────────────────┤
│  ✅ ProjektEmail         - Alle E-Mails bleiben erhalten                         │
│  ✅ AngebotEmail         - Alle E-Mails bleiben erhalten                         │
│  ✅ LieferantEmail       - Alle E-Mails bleiben erhalten                         │
│  ✅ ProcessedEmail       - Alle Tracking-Einträge bleiben                        │
│  ✅ LieferantDokument    - Alle Dokumente bleiben erhalten                       │
│  ⚠️ ProcessedEmail.senderDomain - IST MEIST NULL (Bug!) → Kann gefixt werden    │
└─────────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────────┐
│                        NEUE DATEN (werden hinzugefügt)                           │
├─────────────────────────────────────────────────────────────────────────────────┤
│  🆕 EmailProcessingJob   - Neue Queue-Tabelle (wird bei ersten Lauf erstellt)   │
│                          - Enthält nur NEUE E-Mails ab jetzt                     │
│                          - Alte E-Mails werden NICHT nochmal verarbeitet        │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### Migrations-Schritte:

```
SCHRITT 1: Anwendung starten
────────────────────────────
→ Hibernate erstellt automatisch die neue Tabelle "email_processing_job"
→ Queue ist leer, keine Duplikate

SCHRITT 2: senderDomain backfillen (OPTIONAL aber empfohlen)
────────────────────────────────────────────────────────────
POST /api/admin/email-queue/backfill-domains

→ Durchsucht alle ProcessedEmail ohne senderDomain
→ Extrahiert Domain aus ProjektEmail.fromAddress
→ Aktualisiert ProcessedEmail.senderDomain

SCHRITT 3: Lieferanten-Backfill testen
──────────────────────────────────────
→ Öffne einen Lieferanten im Frontend
→ Klicke "E-Mails aktualisieren" oder ähnlich
→ reprocessUnassignedEmails() findet jetzt E-Mails per Domain!

SCHRITT 4: Queue beobachten
───────────────────────────
GET /api/admin/email-queue/stats

→ Zeigt: pending, inProgress, completed, failed, retry
→ Neue E-Mails erscheinen automatisch in Queue
```

### Backfill-Skript für senderDomain (erweiterter Ansatz):

Wenn die bestehenden ProcessedEmail-Einträge keine senderDomain haben, 
können wir sie aus den ProjektEmail-Daten rekonstruieren:

```sql
-- SQL zur manuellen Prüfung (nicht ausführen, nur zur Analyse):
SELECT 
    pe.message_id,
    pe.sender_domain,
    proj.from_address
FROM processed_email pe
LEFT JOIN projekt_email proj ON pe.message_id = proj.message_id
WHERE pe.sender_domain IS NULL
  AND proj.from_address IS NOT NULL
LIMIT 100;
```

---

## 12. DETAILLIERTE ABLAUFDIAGRAMME

### 12.1 Übersicht: Welche E-Mail geht wohin?

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                    E-MAIL EINGANG: WOHIN WIRD ZUGEORDNET?                        │
└─────────────────────────────────────────────────────────────────────────────────┘

                              ┌─────────────────┐
                              │  Neue E-Mail    │
                              │  (IMAP INBOX)   │
                              └────────┬────────┘
                                       │
                                       ▼
                    ┌──────────────────────────────────────┐
                    │      EmailCollectorService           │
                    │      (sammelt Message-ID, Domain)    │
                    └──────────────────┬───────────────────┘
                                       │
                                       ▼
                    ┌──────────────────────────────────────┐
                    │      EmailProcessingJob (Queue)      │
                    │      senderDomain = "wuerth.com"     │
                    └──────────────────┬───────────────────┘
                                       │
                                       ▼
                    ┌──────────────────────────────────────┐
                    │      EmailProcessorWorker            │
                    │      (lädt Content, delegiert)       │
                    └──────────────────┬───────────────────┘
                                       │
                                       ▼
        ┌──────────────────────────────────────────────────────────────┐
        │                ProjektEmailImportService.mapAndPersist()     │
        │                                                              │
        │  Entscheidungslogik:                                         │
        │  ┌────────────────────────────────────────────────────────┐  │
        │  │ 1. Prüfe: Absender/Empfänger in Projekt.kundenEmails?  │  │
        │  │    → JA: EmailAssignmentService.Decision = PROJEKT     │  │
        │  │                                                         │  │
        │  │ 2. Prüfe: Absender/Empfänger in Angebot.kundenEmails?  │  │
        │  │    → JA: EmailAssignmentService.Decision = ANGEBOT     │  │
        │  │                                                         │  │
        │  │ 3. Prüfe: Absender-Domain in Lieferant.kundenEmails?   │  │
        │  │    → JA: LieferantEmailResolver gibt lieferantId       │  │
        │  │                                                         │  │
        │  │ 4. Sonst: ProjektEmail mit projekt=NULL (Orphan)       │  │
        │  └────────────────────────────────────────────────────────┘  │
        └──────────────────────────────────────────────────────────────┘
                                       │
           ┌───────────────────────────┼───────────────────────────┐
           │                           │                           │
           ▼                           ▼                           ▼
┌───────────────────┐      ┌───────────────────┐      ┌───────────────────┐
│   ProjektEmail    │      │   AngebotEmail    │      │  LieferantEmail   │
│   (IMMER!)        │      │   (wenn Angebot)  │      │  (wenn Lieferant) │
└───────────────────┘      └───────────────────┘      └─────────┬─────────┘
                                                                 │
                                                                 ▼
                                                      ┌───────────────────┐
                                                      │LieferantEmail-    │
                                                      │Attachment         │
                                                      │(PDF, XML, etc.)   │
                                                      └─────────┬─────────┘
                                                                │
                                                                ▼
                                                      ┌───────────────────┐
                                                      │LieferantEmail-    │
                                                      │PipelineService    │
                                                      │.processAttachment │
                                                      └─────────┬─────────┘
                                                                │
                                                                ▼
                                                      ┌───────────────────┐
                                                      │LieferantDokument  │
                                                      │+ Geschäftsdokum.  │
                                                      └───────────────────┘
```

### 12.2 ProjektEmail: Detaillierter Ablauf

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                        PROJEKTEMAIL VERARBEITUNG                                 │
└─────────────────────────────────────────────────────────────────────────────────┘

┌────────────────┐
│ Eingehende     │
│ E-Mail         │
└───────┬────────┘
        │
        ▼
┌────────────────────────────────────────┐
│ EmailAssignmentService.resolve()       │
│                                        │
│ Input:                                 │
│ - counterpartAddresses (From/To/CC)    │
│ - subject                              │
│ - body                                 │
│ - sentAt                               │
│ - projekteMap (Email → Projekt)        │
└───────────────────┬────────────────────┘
                    │
                    ▼
        ┌───────────────────────┐
        │ Absender/Empfänger    │────────── NEIN ──────────────────────┐
        │ in Projekt.kunden-    │                                      │
        │ Emails?               │                                      │
        └───────────┬───────────┘                                      │
                    │ JA                                               │
                    ▼                                                  │
        ┌───────────────────────┐                                      │
        │ Decision.Type =       │                                      │
        │ PROJEKT               │                                      │
        │ Decision.id = projekt │                                      │
        └───────────┬───────────┘                                      │
                    │                                                  │
                    ▼                                                  │
┌────────────────────────────────────────┐                             │
│ ProjektEmail erstellen:                │                             │
│                                        │                             │
│ - projekt = zugeordnetes Projekt       │◄────────────────────────────┘
│ - fromAddress                          │   (projekt = NULL)
│ - recipient, cc                        │
│ - subject                              │
│ - htmlBody, body, rawBody              │
│ - sentAt, direction                    │
│ - messageId                            │
└───────────────────┬────────────────────┘
                    │
                    ▼
┌────────────────────────────────────────┐
│ Parent-Email suchen (Thread-Linking)   │
│                                        │
│ Suche nach E-Mail mit gleichem:        │
│ - Subject (ohne Re:/Fwd:)              │
│ - Projekt                              │
│ - Früherem Datum                       │
└───────────────────┬────────────────────┘
                    │
                    ▼
┌────────────────────────────────────────┐
│ Attachments speichern                  │
│                                        │
│ → ProjektEmailFile                     │
│   - originalFilename                   │
│   - storedFilename (UUID)              │
│   - Speicherort: uploads/attachments/  │
└───────────────────┬────────────────────┘
                    │
                    ▼
┌────────────────────────────────────────┐
│ ProcessedEmail erstellen/updaten       │
│                                        │
│ - messageId (PK)                       │
│ - processedAt                          │
│ - senderDomain ← HIER WIRD GEFIXT!     │
│ - hasAttachments                       │
│ - assigned = (projekt != null)         │
└────────────────────────────────────────┘
```

### 12.3 AngebotEmail: Detaillierter Ablauf

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                        ANGEBOTEMAIL VERARBEITUNG                                 │
└─────────────────────────────────────────────────────────────────────────────────┘

┌────────────────┐
│ Eingehende     │
│ E-Mail         │
└───────┬────────┘
        │
        ▼
┌────────────────────────────────────────┐
│ EmailAssignmentService.resolve()       │
│                                        │
│ Prüft:                                 │
│ 1. Projekt-Match (Priorität 1)         │
│ 2. Angebot-Match (Priorität 2)         │
│    - Kunde.kundenEmails                │
│    - Angebot.kundenEmails              │
└───────────────────┬────────────────────┘
                    │
                    ▼
        ┌───────────────────────┐
        │ Decision.Type =       │
        │ ANGEBOT?              │
        └───────────┬───────────┘
                    │ JA
                    ▼
┌────────────────────────────────────────┐
│ AngebotEmailImportService              │
│ .mapAndPersist()                       │
│                                        │
│ → Ähnlich wie ProjektEmail, aber:      │
│   - angebot statt projekt              │
│   - AngebotEmail Entity                │
│   - AngebotEmailFile für Attachments   │
└───────────────────┬────────────────────┘
                    │
                    ▼
┌────────────────────────────────────────┐
│ AngebotEmail erstellen:                │
│                                        │
│ - angebot = zugeordnetes Angebot       │
│ - parent = vorherige Mail im Thread    │
│ - fromAddress, recipient, cc           │
│ - subject, body, htmlBody              │
│ - sentAt, direction                    │
│ - messageId                            │
└────────────────────────────────────────┘

HINWEIS: ProjektEmail wird TROTZDEM erstellt!
         E-Mails werden DUPLIZIERT (Design-Problem)
```

### 12.4 LieferantEmail: Detaillierter Ablauf

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                       LIEFERANTEMAIL VERARBEITUNG                                │
└─────────────────────────────────────────────────────────────────────────────────┘

┌────────────────┐
│ Eingehende     │
│ E-Mail         │
└───────┬────────┘
        │
        ▼
┌────────────────────────────────────────┐
│ LieferantEmailResolver.resolve()       │
│                                        │
│ Input: List<String> counterpartAddr.   │
│                                        │
│ Logik:                                 │
│ 1. Extrahiere Domain(s) aus Adressen   │
│ 2. Suche Lieferant mit passender       │
│    Domain in lieferant.kundenEmails    │
│ 3. Return: Optional<Long> lieferantId  │
└───────────────────┬────────────────────┘
                    │
                    ▼
        ┌───────────────────────┐
        │ lieferantId != null?  │────────── NEIN ──► Keine LieferantEmail
        └───────────┬───────────┘
                    │ JA
                    ▼
┌────────────────────────────────────────┐
│ LieferantEmail erstellen:              │
│                                        │
│ - lieferant = gefundener Lieferant     │
│ - direction = IN/OUT                   │
│ - fromAddress, recipient, cc           │
│ - subject, body, htmlBody, rawBody     │
│ - sentAt, messageId                    │
└───────────────────┬────────────────────┘
                    │
                    ▼
┌────────────────────────────────────────┐
│ Attachments verarbeiten:               │
│ processPartForLieferant()              │
│                                        │
│ Für jeden Anhang:                      │
│ → LieferantEmailAttachment erstellen   │
│   - originalFilename                   │
│   - storedFilename                     │
│   - Speicherort: uploads/attachments/  │
│     lieferanten/{lieferantId}/         │
│   - processed = false (KI pending)     │
└───────────────────┬────────────────────┘
                    │
                    ▼
┌────────────────────────────────────────┐
│ KI-Pipeline triggern:                  │
│ LieferantEmailPipelineService          │
│ .processAttachment()                   │
│                                        │
│ Für jeden Anhang (PDF/XML):            │
│ → Siehe Diagramm 12.5                  │
└────────────────────────────────────────┘
```

### 12.5 LieferantDokument & Geschäftsdokument: KI-Pipeline

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                    LIEFERANTDOKUMENT / GESCHÄFTSDOKUMENT PIPELINE                │
└─────────────────────────────────────────────────────────────────────────────────┘

┌────────────────────────────┐
│ LieferantEmailAttachment   │
│ (PDF oder XML)             │
│ processed = false          │
└────────────┬───────────────┘
             │
             ▼
┌────────────────────────────────────────┐
│ LieferantEmailPipelineService          │
│ .processAttachment(attachment, lief.)  │
└────────────┬───────────────────────────┘
             │
             ▼
┌────────────────────────────────────────┐
│ Ist Dateiendung PDF oder XML?          │
└────────────┬───────────────────────────┘
             │ JA
             ▼
┌────────────────────────────────────────┐
│ Temporäres LieferantDokument erstellen │
│                                        │
│ - lieferant = aus Attachment           │
│ - attachment = Referenz (keine Kopie!) │
│ - typ = SONSTIG (wird überschrieben)   │
│ - uploadDatum = jetzt                  │
└────────────┬───────────────────────────┘
             │
             ▼
┌────────────────────────────────────────┐
│ GeminiDokumentAnalyseService           │
│ .analysiereDokument(dokument)          │
│                                        │
│ Reihenfolge:                           │
│ 1. ZUGFeRD-PDF prüfen                  │
│ 2. XML-Rechnung parsen                 │
│ 3. Fallback: Gemini AI OCR             │
└────────────┬───────────────────────────┘
             │
             ▼
┌────────────────────────────────────────────────────────────────────────────────┐
│                                                                                │
│  ┌─────────────────────┐    ┌─────────────────────┐    ┌─────────────────────┐ │
│  │ ZUGFeRD-Extraktion  │    │   XML-Extraktion    │    │   Gemini AI OCR     │ │
│  ├─────────────────────┤    ├─────────────────────┤    ├─────────────────────┤ │
│  │ ZugferdExtractor-   │    │ Parsing von:        │    │ System-Prompt:      │ │
│  │ Service.extract()   │    │ - XRechnung         │    │ - Dokumenttyp       │ │
│  │                     │    │ - CrossIndustry-    │    │ - Rechnungsnummer   │ │
│  │ Extrahiert:         │    │   Invoice           │    │ - Beträge           │ │
│  │ - Rechnungsnummer   │    │                     │    │ - Skonto            │ │
│  │ - Datum, Betrag     │    │ Extrahiert:         │    │ - Artikelpositionen │ │
│  │ - Skonto, MwSt      │    │ - ID, Betrag        │    │                     │ │
│  │ - Artikelpositionen │    │ - Datum             │    │ confidence: 0.0-1.0 │ │
│  │                     │    │                     │    │                     │ │
│  │ confidence = 1.0    │    │ confidence = 1.0    │    │ confidence = variab │ │
│  └──────────┬──────────┘    └──────────┬──────────┘    └──────────┬──────────┘ │
│             │                          │                          │            │
│             └──────────────────────────┴──────────────────────────┘            │
│                                        │                                       │
└────────────────────────────────────────┼───────────────────────────────────────┘
                                         │
                                         ▼
                      ┌───────────────────────────────────┐
                      │ geschaeftsdaten == null?          │
                      └───────────────────┬───────────────┘
                                          │
                     ┌────────────────────┴────────────────────┐
                     │ JA                                      │ NEIN
                     ▼                                         ▼
        ┌─────────────────────────┐           ┌─────────────────────────────────┐
        │ KEIN Geschäftsdokument  │           │ LieferantGeschaeftsdokument     │
        │                         │           │ erstellen:                      │
        │ → LieferantDokument     │           │                                 │
        │   LÖSCHEN               │           │ - dokument = Referenz           │
        │ → Attachment als        │           │ - dokumentNummer                │
        │   processed markieren   │           │ - dokumentDatum                 │
        └─────────────────────────┘           │ - betragNetto, betragBrutto     │
                                              │ - mwstSatz                      │
                                              │ - liefertermin                  │
                                              │ - zahlungsziel                  │
                                              │ - skontoTage, skontoProzent     │
                                              │ - nettoTage                     │
                                              │ - bestellnummer                 │
                                              │ - referenzNummer                │
                                              │ - aiConfidence                  │
                                              │ - aiRawJson (bei KI)            │
                                              └───────────────┬─────────────────┘
                                                              │
                                                              ▼
                                              ┌─────────────────────────────────┐
                                              │ Dokumenttyp aus Analyse         │
                                              │ übernehmen:                     │
                                              │                                 │
                                              │ - RECHNUNG (Eingangsrechnung)   │
                                              │ - ANGEBOT                       │
                                              │ - AUFTRAGSBESTAETIGUNG          │
                                              │ - LIEFERSCHEIN                  │
                                              │ - SONSTIG → wird gelöscht!      │
                                              └───────────────┬─────────────────┘
                                                              │
                                                              ▼
                                              ┌─────────────────────────────────┐
                                              │ Automatische Verknüpfung        │
                                              │                                 │
                                              │ Bei ReferenzNummer:             │
                                              │ - Suche passendes Dokument      │
                                              │   beim selben Lieferanten       │
                                              │ - Verknüpfe Dokumentenkette     │
                                              │   (z.B. Rechnung → AB → Angebot)│
                                              └───────────────┬─────────────────┘
                                                              │
                                                              ▼
                                              ┌─────────────────────────────────┐
                                              │ LieferantDokument speichern     │
                                              │ + geschaeftsdaten (1:1)         │
                                              │                                 │
                                              │ Attachment als processed=true   │
                                              │ markieren                       │
                                              └─────────────────────────────────┘
```

### 12.6 Gesamtübersicht: Entity-Beziehungen

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                        ENTITY-BEZIEHUNGEN ÜBERSICHT                              │
└─────────────────────────────────────────────────────────────────────────────────┘

┌───────────────┐     ┌───────────────┐     ┌───────────────┐
│    Kunde      │     │   Angebot     │     │   Projekt     │
└───────┬───────┘     └───────┬───────┘     └───────┬───────┘
        │                     │                     │
        │ kundenEmails        │ kundenEmails        │ kundenEmails
        │                     │ (+ Kunde.emails)    │ (vom Kunden)
        │                     │                     │
        └─────────┬───────────┴───────────┬─────────┘
                  │                       │
                  ▼                       ▼
        ┌───────────────────────────────────────────────┐
        │           E-MAIL-ZUORDNUNG                    │
        │                                               │
        │  Eingehende E-Mail wird geprüft gegen:       │
        │  1. Projekt.kundenEmails → ProjektEmail       │
        │  2. Angebot.kundenEmails → AngebotEmail       │
        │  3. Lieferant.kundenEmails → LieferantEmail   │
        └───────────────────────────────────────────────┘
                              │
          ┌───────────────────┼───────────────────┐
          │                   │                   │
          ▼                   ▼                   ▼
┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐
│  ProjektEmail   │  │  AngebotEmail   │  │ LieferantEmail  │
├─────────────────┤  ├─────────────────┤  ├─────────────────┤
│ projekt (FK)    │  │ angebot (FK)    │  │ lieferant (FK)  │
│ attachments ────┤  │ attachments ────┤  │ attachments ────┤
└────────┬────────┘  └────────┬────────┘  └────────┬────────┘
         │                    │                    │
         ▼                    ▼                    ▼
┌─────────────────┐  ┌─────────────────┐  ┌────────────────────────┐
│ProjektEmailFile │  │AngebotEmailFile │  │LieferantEmailAttachment│
└─────────────────┘  └─────────────────┘  └───────────┬────────────┘
                                                      │
                                                      │ (Referenz,
                                                      │  keine Kopie)
                                                      ▼
                                          ┌───────────────────────┐
                                          │  LieferantDokument    │
                                          ├───────────────────────┤
                                          │ lieferant (FK)        │
                                          │ attachment (FK) ──────┼──► Referenz auf
                                          │ typ (ENUM)            │    Attachment
                                          │ projektAnteile        │
                                          │ verknuepfteDokumente  │
                                          └───────────┬───────────┘
                                                      │
                                                      │ 1:1
                                                      ▼
                                          ┌───────────────────────┐
                                          │LieferantGeschaefts-   │
                                          │dokument               │
                                          ├───────────────────────┤
                                          │ dokumentNummer        │
                                          │ betragNetto/Brutto    │
                                          │ skonto*, netto*       │
                                          │ aiConfidence          │
                                          └───────────────────────┘


┌─────────────────────────────────────────────────────────────────────────────────┐
│                        LIEFERANTEN-STRUKTUR                                      │
└─────────────────────────────────────────────────────────────────────────────────┘

┌───────────────────┐
│    Lieferanten    │
├───────────────────┤
│ id                │
│ lieferantenname   │
│ kundenEmails[]    │◄─── Hier werden E-Mail-Adressen hinterlegt!
│                   │     z.B. ["bestellung@wuerth.com", "info@wuerth.com"]
└────────┬──────────┘
         │
         │ 1:N
         │
         ├──────────────────────┬──────────────────────┐
         │                      │                      │
         ▼                      ▼                      ▼
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────────┐
│ LieferantEmail  │    │LieferantDokument│    │LieferantenArtikel-  │
│                 │    │                 │    │Preise               │
│ E-Mail-Korres-  │    │ Angebote,       │    │                     │
│ pondenz         │    │ Rechnungen,     │    │ Artikelpreise aus   │
│                 │    │ ABs, Liefer-    │    │ Rechnungen/Angebote │
│                 │    │ scheine         │    │                     │
└─────────────────┘    └─────────────────┘    └─────────────────────┘
```

---

## 13. FAQ: Häufige Fragen

### ❓ Werden alle alten E-Mails nochmal importiert?
**Nein.** Der EmailCollector prüft `jobRepository.existsByMessageId()` bevor ein neuer Job erstellt wird. Da die alten E-Mails in `ProcessedEmail` gespeichert sind, werden sie übersprungen.

### ❓ Was wenn die Queue voll wird?
Die Queue hat kein Limit. Alle E-Mails werden erfasst. Der Processor verarbeitet 10 Jobs alle 5 Sekunden = 120 Jobs/Minute = 7.200 Jobs/Stunde.

### ❓ Was passiert bei Server-Absturz während Verarbeitung?
Jobs die länger als 30 Minuten `IN_PROGRESS` sind, werden vom Watchdog automatisch auf `RETRY` zurückgesetzt.

### ❓ Wie lösche ich die Queue?
```sql
-- VORSICHT: Nur wenn du weißt was du tust!
DELETE FROM email_processing_job WHERE status = 'COMPLETED';
-- Oder für kompletten Reset:
TRUNCATE TABLE email_processing_job;
```

### ❓ Kann ich den alten GlobalEmailWatcher deaktivieren?
Ja, aber erst wenn die Queue stabil läuft. Setze `@Scheduled` in GlobalEmailWatcher auf `@Disabled` oder entferne die Annotation temporär.
