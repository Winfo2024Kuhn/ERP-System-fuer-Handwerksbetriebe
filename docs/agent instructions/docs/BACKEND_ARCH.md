# ⚙️ Backend & Architektur-Richtlinien

## Package-Struktur
`org.example.kalkulationsprogramm/`
- `controller/`: REST-Endpoints (Keine Logik hier!)
- `service/`: Business-Logik
- `repository/`: Spring Data JPA
- `domain/`: JPA-Entities + Enums
- `dto/`: Data-Transfer-Objects (Entities NIE direkt exponieren!)
- `mapper/`: DTO ↔ Entity Mapper
- `config/`: Spring-Konfiguration
- `org.example.email/`: E-Mail-System (IMAP/SMTP)

## Coding-Regeln
- **Injection:** Constructor Injection; Lombok `@AllArgsConstructor` ist erlaubt.
- **SQL:** Nur parametrisierte Queries (`@Query` mit `:param`), kein String-Concat.
- **Flyway & Idempotenz (WICHTIG):**
  - Neue Skripte unter `src/main/resources/db/migration/V{N}__{beschreibung}.sql` (aufsteigend ab V207+).
  - Bestehende Migrationen NIEMALS ändern.
  - Migrationen sollen immer **idempotent** sein (mehrfach ausführbar ohne Fehler).
  - Bitte für **MySQL 8.0**-Syntax auslegen.

## Architektur-Patterns
- **Audit-Trail:** GoBD-konform (`ZeitbuchungAudit`, vollständige Snapshots).
- **Dokumentketten:** Angebote → Aufträge → Rechnungen (Vorgänger/Nachfolger).
- **MonatsSaldo-Caching:** Vergangene Monate gecacht, aktueller Monat live.
- **Datei-Deduplizierung:** `LieferantDokument` → FK auf `EmailAttachment`.
- **Enum State-Management:** Typsichere Dokumenttypen, Mahnstufen, Audit-Aktionen.

## ML Spam-Filter (Naive Bayes)
Supervised Multinomial Naive Bayes in reinem Java.
- **Ensemble:** 40% Regel-Score (`SpamFilterService`) + 60% Bayes-Score (`SpamBayesService`).
- **Daten:** Token-Frequenzen in `spam_token_counts`, In-Memory-Cache (5-Min-Refresh).
- **Feedback:** User trainiert das Modell über `mark-spam`/`mark-not-spam` Endpoints weiter.

## Externe Dienste (Config in properties, NIE im Code)
- `ai.gemini.*` (Google Gemini AI)
- `spring.mail.*` (IMAP/SMTP E-Mail)
- `ai.rag.*` (Qdrant Vector DB)
- `spring.datasource.*` (MySQL)