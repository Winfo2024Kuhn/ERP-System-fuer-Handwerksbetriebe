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
- **Flyway:** Neue Skripte unter `src/main/resources/db/migration/V{N}__{beschreibung}.sql`. Bestehende Migrationen NIEMALS ändern. Sollen immer idempotent sein!!
  Neuinstallationen bekommen auf leerer DB zuerst `db/basis/V1__basis_schema.sql` (`FlywayStartSetupConfig`, erzeugt mit `scripts/basis-schema/erzeugen.sh`); `FlywayStartSetupConfigTest` prüft die Checksummen der darin enthaltenen Migrationen.
- **Zwei Datenbanken: MySQL (eigener Server) + PostgreSQL (Kunden).** Jede neue Migration gibt es **zweimal mit derselben Nummer**: `db/migration/V{N}__x.sql` (MySQL) und `db/postgresql/migration/V{N}__x.sql` (PostgreSQL) – `PostgresMigrationslinieTest` lässt den Build sonst scheitern. Übersetzungstabelle MySQL→PostgreSQL in `db/postgresql/migration/README.md`. Die PostgreSQL-Basis (`db/postgresql/basis`) erzeugt `scripts/basis-schema/postgres_erzeugen.sh`.
- **Datenbankneutraler Code:** JPQL statt nativem SQL. Kein `@Lob` auf `String`/`byte[]` (wird in PostgreSQL zu `oid`), sondern `@JdbcTypeCode(SqlTypes.LONG32VARCHAR)` bzw. `SqlTypes.LONG32VARBINARY`; `CHAR(n)`-Spalten zusätzlich `@JdbcTypeCode(SqlTypes.CHAR)`. Nicht portabel: `DATE_SUB`/`DATEDIFF`/`IFNULL`/`CAST AS UNSIGNED`/`ON DUPLICATE KEY`, Optionale Filter `(:param IS NULL OR …)`: bei Datums-Parametern `CAST(:von AS LocalDate) IS NULL` schreiben (bzw. `LocalDateTime`) – PostgreSQL kennt den Typ sonst nicht. `DatenbankKompatibilitaetTest` führt jede `@Query` gegen echte MySQL + PostgreSQL aus (CI-Job „Datenbank-Kompatibilität“). Wo natives SQL unvermeidbar ist: Repository-Fragment (`…RepositoryErweiterung` + `…Impl`) mit beiden Varianten, Auswahl über `DatenbankArt.istPostgres(entityManager)` – Vorbild `SpamTokenCountRepositoryErweiterungImpl`.
- **Java-Enums in MySQL = native `ENUM`-Spalte (NICHT `VARCHAR`!):** Hibernate 6.x mit MySQL-Dialekt mappt `@Enumerated(EnumType.STRING)` standardmäßig auf einen nativen `ENUM`-Spaltentyp. Wenn du in einer Migration eine Spalte für ein Java-Enum anlegst, **immer** als `ENUM('WERT_A','WERT_B',...)` schreiben — sonst schlägt `ddl-auto=validate` beim Startup fehl mit `wrong column type ... found [varchar], but expecting [enum (...)]`. Werte exakt wie die Java-Enum-Konstanten (UPPERCASE). Beispiel siehe `kunde.anrede` und `V291__steuerberater_ansprechpartner_anrede_enum.sql`.

## Architektur-Patterns
- **Audit-Trail:** GoBD-konform (`ZeitbuchungAudit`, vollständige Snapshots).
- **Dokumentketten:** Angebote → Aufträge → Rechnungen (Vorgänger/Nachfolger).
- **MonatsSaldo-Caching:** Vergangene Monate gecacht, aktueller Monat live.
- **Datei-Deduplizierung:** `LieferantDokument` → FK auf `EmailAttachment`.
- **Enum State-Management:** Typsichere Dokumenttypen, Mahnstufen.

## ML Spam-Filter (Naive Bayes)
Supervised Multinomial Naive Bayes in reinem Java.
- **Ensemble:** 40% Regel-Score (`SpamFilterService`) + 60% Bayes-Score (`SpamBayesService`).
- **Daten:** Token-Frequenzen in `spam_token_counts`, In-Memory-Cache (5-Min-Refresh).
- **Feedback:** User trainiert das Modell über `mark-spam`/`mark-not-spam` Endpoints weiter.

## Externe Dienste (Config in properties)
- `ai.gemini.*` (Google Gemini AI)
- `spring.mail.*` (IMAP/SMTP E-Mail)
- `ai.rag.*` (Qdrant Vector DB)
- `spring.datasource.*` (MySQL; Kunden PostgreSQL über Profil `postgres`)