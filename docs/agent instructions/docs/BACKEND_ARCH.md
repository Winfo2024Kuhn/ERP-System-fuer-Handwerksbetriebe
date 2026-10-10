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
- **Datenbank: nur PostgreSQL 16** (eigener Server und alle Kunden). MySQL, MariaDB und eine eingebaute H2-Datenbank gibt es nicht mehr; H2 läuft nur noch im Speicher in den automatischen Unit-Tests (`src/test/resources/application.properties`).
- **Flyway:** Eine neue Migration ist **eine** Datei unter `src/main/resources/db/postgresql/migration/V{N}__{beschreibung}.sql` (N = höchste vorhandene Nummer + 1). Bestehende Migrationen NIEMALS ändern. Sollen immer idempotent sein!! (`IF NOT EXISTS`, `ON CONFLICT DO NOTHING`, `DO $$ … $$`). Anleitung: `db/postgresql/migration/README.md`. Der Ordner `db/migration/` ist Geschichte – `FlywayMigrationenTest` lässt den Build scheitern, wenn dort wieder etwas liegt. Die Ordnernamen `db/postgresql/…` bleiben, damit die Flyway-Historie der laufenden Installationen gültig bleibt.
  Neuinstallationen bekommen auf leerer DB zuerst `db/postgresql/basis/V1__basis_schema.sql` (`FlywayStartSetupConfig`, erzeugt mit `scripts/basis-schema/postgres_erzeugen.sh`); `FlywayMigrationenTest` hält die Checksumme jeder Migration fest – eine neue Migration dort eintragen (der Test nennt den Wert); `DatenbankKompatibilitaetMigrationenTest` prüft, dass jede Migration einen zweiten Lauf übersteht.
- **Java-Enums = `VARCHAR(n)`-Spalte, OHNE `CHECK` mit Werteliste:** `@Enumerated(EnumType.STRING)` liegt in PostgreSQL als `VARCHAR`. Kein `CHECK (spalte IN ('A','B'))` – ein neuer Enum-Wert soll keine Migration brauchen (`FlywayMigrationenTest` prüft das). `n` so wählen, dass der längste Wert mit Luft passt. Werte exakt wie die Java-Enum-Konstanten (UPPERCASE).
- **Abfragen:** JPQL ist erste Wahl. Kein `@Lob` auf `String`/`byte[]` (wird in PostgreSQL zu `oid`), sondern `@JdbcTypeCode(SqlTypes.LONG32VARCHAR)` bzw. `SqlTypes.LONG32VARBINARY`; `CHAR(n)`-Spalten zusätzlich `@JdbcTypeCode(SqlTypes.CHAR)`. Optionale Filter `(:param IS NULL OR …)`: bei Datums-Parametern `CAST(:von AS LocalDate) IS NULL` schreiben (bzw. `LocalDateTime`) – PostgreSQL kennt den Typ sonst nicht. Wo natives SQL unvermeidbar ist: Repository-Fragment (`…RepositoryErweiterung` + `…Impl`) in PostgreSQL-Form, Vorbild `SpamTokenCountRepositoryErweiterungImpl`. `DatenbankKompatibilitaetTest` führt jede `@Query` gegen ein echtes PostgreSQL aus (CI-Job „Datenbank-Kompatibilität“; lokal: `./mvnw test "-Dtest=DatenbankKompatibilitaet*Test" -Dkompat.db.url=jdbc:postgresql://localhost:5432/kompat -Dkompat.db.user=kompat -Dkompat.db.pass=kompat` mit einem Wegwerf-Container `postgres:16`).
- **Suchen, Sortierung, Vergleiche (PostgreSQL ist streng):** In Specifications kein `cq.distinct(true)`, sobald sortiert wird – PostgreSQL verlangt bei `SELECT DISTINCT`, dass jeder Sortierausdruck (`lower(name)`, Spalten verbundener Tabellen) ausgewählt ist (Fehler 42P10). Treffer in Sammlungen (E-Mails, Projekte) per `EXISTS`-Unterabfrage – Vorbild `KundenSuche`/`LieferantenSuche`. Frei wählbare Sortierspalten über `PortableSortierung`. Bei fester Sortierung nach Spalten, die `NULL` erlauben, die Lage leerer Werte immer bewusst angeben: in JPQL `ASC NULLS FIRST` bzw. `DESC NULLS LAST` (so erwarten es die Nutzer; PostgreSQL macht es von sich aus umgekehrt; abgeleitete `…OrderBy…`-Methoden können das nicht – dann `@Query`). Textvergleiche auf Eingaben immer mit `lower(...)`/`…IgnoreCase` – PostgreSQL unterscheidet Groß-/Kleinschreibung (und vergleicht Umlaute nicht akzentfrei: „wurth“ findet „Würth“ nicht). Tests: `DatenbankKompatibilitaetSucheTest` (Such-Endpunkte mit allen Filtern und Sortierungen), `DatenbankKompatibilitaetSortierungTest` (jede Sortierung auf `NULL`-fähigen Spalten), `DatenbankKompatibilitaetEntitaetenTest` (jede Entität mit leeren Feldern speichern und laden).

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
- `spring.datasource.*` (PostgreSQL; Standard `jdbc:postgresql://localhost:5432/kalkulationsprogramm_db`, Zugangsdaten über `APP_DB_URL`/`APP_DB_USER`/`APP_DB_PASS` bzw. `application-local.properties`, im Docker-Profil der Dienst `postgres`)