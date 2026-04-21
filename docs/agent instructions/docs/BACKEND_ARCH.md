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
  - Neue Skripte unter `src/main/resources/db/migration/V{N}__{beschreibung}.sql` (aufsteigend, aktuell V235+).
  - Bestehende Migrationen NIEMALS ändern — auch nicht im selben Branch, sobald sie einmal lokal eingespielt sind (Flyway-Checksumme).
  - Migrationen sollen immer **idempotent** sein (ein erneuter Lauf auf einer bereits migrierten DB darf nicht fehlschlagen).
  - Ziel ist ausschließlich **MySQL 8.0** (Dialekt-spezifisch, keine MariaDB/PostgreSQL-Syntax). In MySQL 8 gilt:
    - `IF NOT EXISTS` funktioniert **nur** bei `CREATE TABLE` und `CREATE DATABASE`.
    - `IF NOT EXISTS` funktioniert **nicht** bei `CREATE INDEX`, `ALTER TABLE ADD COLUMN`, `ADD INDEX`, `ADD CONSTRAINT`, `ADD FOREIGN KEY`, `DROP COLUMN`, `DROP INDEX`.
    - Für alle nicht-unterstützten Fälle ist der `INFORMATION_SCHEMA` + `PREPARE`/`EXECUTE`-Workaround Pflicht.
    - Stored Procedures / `DELIMITER` sind in Flyway-Skripten möglich, aber das `PREPARE`-Pattern bleibt die einfachere, gut lesbare Form und wird durchgängig verwendet.

  **Idempotente Patterns im Projekt (Referenz: vorhandene Migrationen):**

  1. **Neue Tabelle** → direkt mit `IF NOT EXISTS` (siehe [V215__en1090_echeck.sql](src/main/resources/db/migration/V215__en1090_echeck.sql), [V229__artikel_preis_historie.sql](src/main/resources/db/migration/V229__artikel_preis_historie.sql)):
     ```sql
     CREATE TABLE IF NOT EXISTS meine_tabelle (
         id BIGINT AUTO_INCREMENT PRIMARY KEY,
         ...
     ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
     ```

  2. **Neue Spalte** → Prepared-Statement mit `INFORMATION_SCHEMA.COLUMNS`-Check (Pattern aus [V228__artikel_durchschnittspreis.sql](src/main/resources/db/migration/V228__artikel_durchschnittspreis.sql)):
     ```sql
     SET @col = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE()
                   AND TABLE_NAME = 'artikel'
                   AND COLUMN_NAME = 'durchschnittspreis_netto');
     SET @sql = IF(@col = 0,
         'ALTER TABLE artikel ADD COLUMN durchschnittspreis_netto DECIMAL(12,4) NULL',
         'SELECT 1');
     PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
     ```

  3. **Neuer Index** → gleiches Muster gegen `INFORMATION_SCHEMA.STATISTICS`:
     ```sql
     SET @idx = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
                 WHERE TABLE_SCHEMA = DATABASE()
                   AND TABLE_NAME = 'artikel'
                   AND INDEX_NAME = 'idx_artikel_durchschnittspreis_aktualisiert');
     SET @sql = IF(@idx = 0,
         'ALTER TABLE artikel ADD INDEX idx_artikel_durchschnittspreis_aktualisiert (durchschnittspreis_aktualisiert_am)',
         'SELECT 1');
     PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
     ```

  4. **Neuer Foreign Key** → Check gegen `INFORMATION_SCHEMA.TABLE_CONSTRAINTS` (`CONSTRAINT_TYPE = 'FOREIGN KEY'`).

  5. **Spalten-Typ ändern / ENUM-Konvertierung** → `ALTER TABLE ... MODIFY COLUMN ...` ist in MySQL 8 von Natur aus idempotent, wenn der Zielzustand bereits besteht (siehe [V231__artikel_preis_historie_enum_spalten.sql](src/main/resources/db/migration/V231__artikel_preis_historie_enum_spalten.sql), [V232__wps_lage_typ_enum.sql](src/main/resources/db/migration/V232__wps_lage_typ_enum.sql)). Kein Wrapper nötig.

  **Anti-Patterns — bitte vermeiden (brechen auf MySQL 8):**
  - `ALTER TABLE x ADD COLUMN IF NOT EXISTS ...` → MariaDB-Syntax, in MySQL 8 Parse-Error.
  - `ALTER TABLE x ADD INDEX IF NOT EXISTS ...` → MariaDB-Syntax, in MySQL 8 Parse-Error.
  - `CREATE INDEX IF NOT EXISTS ...` → PostgreSQL/MariaDB, in MySQL 8 Parse-Error.
  - `DROP COLUMN IF EXISTS ...` / `DROP INDEX IF EXISTS ...` → in MySQL 8 nicht unterstützt.
  - `ALTER TABLE x ADD COLUMN ...` ohne Existenz-Check → zweiter Lauf wirft `Duplicate column name`.
  - `CREATE INDEX ...` ohne Check → zweiter Lauf wirft `Duplicate key name`.

  **Merkhilfe:** Erstmaliger Lauf muss grün sein, und ein `./mvnw flyway:repair` + erneuter Lauf danach auch.

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