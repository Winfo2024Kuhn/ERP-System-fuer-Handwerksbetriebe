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
- **PostgreSQL ist die einzige produktive Datenbank (eigener Server + Kunden). MySQL wird nicht mehr gebraucht**, steckt aber noch im Code (MySQL-Migrationen, MySQL-Varianten nativer Abfragen, Standard ohne Profil), bis es entfernt ist. Neue Abfragen und Migrationen für PostgreSQL schreiben und dort testen (`DatenbankKompatibilitaetTest` mit `-Dkompat.db.url=jdbc:postgresql:…`). Solange die MySQL-Teile da sind, gibt es jede neue Migration noch **zweimal mit derselben Nummer**: `db/migration/V{N}__x.sql` (MySQL) und `db/postgresql/migration/V{N}__x.sql` (PostgreSQL) – `PostgresMigrationslinieTest` lässt den Build sonst scheitern. Übersetzungstabelle MySQL→PostgreSQL in `db/postgresql/migration/README.md`. Die PostgreSQL-Basis (`db/postgresql/basis`) erzeugt `scripts/basis-schema/postgres_erzeugen.sh`.
- **Datenbankneutraler Code:** JPQL statt nativem SQL. Kein `@Lob` auf `String`/`byte[]` (wird in PostgreSQL zu `oid`), sondern `@JdbcTypeCode(SqlTypes.LONG32VARCHAR)` bzw. `SqlTypes.LONG32VARBINARY`; `CHAR(n)`-Spalten zusätzlich `@JdbcTypeCode(SqlTypes.CHAR)`. Nicht portabel: `DATE_SUB`/`DATEDIFF`/`IFNULL`/`CAST AS UNSIGNED`/`ON DUPLICATE KEY`, Optionale Filter `(:param IS NULL OR …)`: bei Datums-Parametern `CAST(:von AS LocalDate) IS NULL` schreiben (bzw. `LocalDateTime`) – PostgreSQL kennt den Typ sonst nicht. `DatenbankKompatibilitaetTest` führt jede `@Query` gegen echte MySQL + PostgreSQL aus (CI-Job „Datenbank-Kompatibilität“). Wo natives SQL unvermeidbar ist: Repository-Fragment (`…RepositoryErweiterung` + `…Impl`) mit beiden Varianten, Auswahl über `DatenbankArt.istPostgres(entityManager)` – Vorbild `SpamTokenCountRepositoryErweiterungImpl`.
- **Suchen, Sortierung, Vergleiche (MySQL ≠ PostgreSQL):** In Specifications kein `cq.distinct(true)`, sobald sortiert wird – PostgreSQL verlangt bei `SELECT DISTINCT`, dass jeder Sortierausdruck (`lower(name)`, Spalten verbundener Tabellen) ausgewählt ist (Fehler 42P10, MySQL 8 teils auch). Treffer in Sammlungen (E-Mails, Projekte) per `EXISTS`-Unterabfrage – Vorbild `KundenSuche`/`LieferantenSuche`. Frei wählbare Sortierspalten über `PortableSortierung`; feste Sortierung nach Spalten, die `NULL` erlauben, in JPQL mit `ASC NULLS FIRST` bzw. `DESC NULLS LAST` (wie bisher auf MySQL; abgeleitete `…OrderBy…`-Methoden können das nicht – dann `@Query`). Textvergleiche auf Eingaben immer mit `lower(...)`/`…IgnoreCase` (MySQL-Kollation ignoriert Groß-/Kleinschreibung, PostgreSQL nicht). Tests: `DatenbankKompatibilitaetSucheTest` (Such-Endpunkte mit allen Filtern und Sortierungen), `DatenbankKompatibilitaetSortierungTest` (jede Sortierung auf `NULL`-fähigen Spalten), `DatenbankKompatibilitaetEntitaetenTest` (jede Entität mit leeren Feldern speichern und laden).
- **Java-Enums im MySQL-Zwilling = native `ENUM`-Spalte (NICHT `VARCHAR`!)** – gilt nur, solange die MySQL-Migrationen noch mitgepflegt werden; in PostgreSQL `VARCHAR(n)` + `CHECK`: Hibernate 6.x mit MySQL-Dialekt mappt `@Enumerated(EnumType.STRING)` standardmäßig auf einen nativen `ENUM`-Spaltentyp. Wenn du in einer Migration eine Spalte für ein Java-Enum anlegst, **immer** als `ENUM('WERT_A','WERT_B',...)` schreiben — sonst schlägt `ddl-auto=validate` beim Startup fehl mit `wrong column type ... found [varchar], but expecting [enum (...)]`. Werte exakt wie die Java-Enum-Konstanten (UPPERCASE). Beispiel siehe `kunde.anrede` und `V291__steuerberater_ansprechpartner_anrede_enum.sql`.

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
- `spring.datasource.*` (PostgreSQL über Profil `postgres` – eigener Server und Kunden; der MySQL-Standard ohne Profil wird nicht mehr gebraucht)