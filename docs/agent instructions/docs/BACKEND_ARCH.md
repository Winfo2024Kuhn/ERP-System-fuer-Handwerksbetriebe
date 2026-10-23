# ⚙️ Backend & Architektur-Richtlinien

## Package-Struktur
`org.example.kalkulationsprogramm/`
- `controller/`: REST-Endpoints (Keine Logik hier!); `controller/advice/RestExceptionHandler` = zentrale Fehlerbehandlung
- `service/`: Business-Logik (auch der E-Mail-Import, Threads, Postfächer, Spam: `Email*Service`)
- `repository/`: Spring Data JPA
- `domain/`: JPA-Entities + Enums
- `dto/`: Data-Transfer-Objects (Entities NIE direkt exponieren!)
- `mapper/`: DTO ↔ Entity Mapper
- `config/`: Spring-Konfiguration (u. a. `SecurityConfig`, `FlywayStartSetupConfig`)
- `exception/`: eigene Exceptions (`NotFoundException`, `ForbiddenException`, …)
- `util/`: Hilfsklassen (`EmailHtmlSanitizer`, `ConstraintMessageResolver`, …)
- `org.example.email/`: SMTP-Versand (`EmailService`) plus IMAP-Diagnose (`ListFolders`)

## Coding-Regeln
- **Injection:** Constructor Injection. Standard ist Lombok `@RequiredArgsConstructor` mit `private final`-Feldern; `@AllArgsConstructor` ist erlaubt, ein eigener Konstruktor ebenso (z. B. für `@Value`-Parameter). Keine `@Autowired`-Feldinjektion. Einzige Ausnahme: die Selbstreferenz über `@Setter(onMethod_ = { @Autowired, @Lazy })`, damit `@Transactional` durch den Spring-Proxy läuft (Vorbild `EmailImportService`).
- **JSON (Jackson 3):** Pakete `tools.jackson.*` (nur die Annotationen bleiben `com.fasterxml.jackson.annotation`). Jackson-Ausnahmen sind ungeprüft (`JacksonException`). `JsonNode`-Werte immer mit Ersatzwert lesen – `asString("")`, `asInt(0)`, `asLong(0L)`, `asDouble(0.0)`, `asBoolean(false)`: Ohne Ersatzwert werfen sie bei unpassendem Typ (z. B. Text statt Zahl in einer KI-Antwort) eine Ausnahme – ebenso `decimalValue()`, `intValue()`, `stringValue()` & Co. ohne vorherige Typprüfung (`isNumber()`, `isString()`). Geldbeträge aus KI-Antworten über eine tolerante Lesefunktion (Zahl oder Text wie „1.234,56“). Bevorzugt den Spring-Mapper injizieren; ein eigener entsteht über `JsonMapper.builderWithJackson2Defaults()…build()` (unveränderlich, gleiche Standardwerte wie die Anwendung mit `spring.jackson.use-jackson2-defaults=true`).
- **SQL:** Nur parametrisierte Queries (`@Query` mit `:param`), kein String-Concat.
- **Fehler an den Client:** `ResponseStatusException(HttpStatus.…, "Meldung")` werfen. Die Meldung ist ein verständlicher deutscher Satz, denn sie landet bei den Nutzern. `RestExceptionHandler` macht daraus JSON (`ApiError`: `status`, `message`, `fields`). Verletzte Datenbank-Constraints übersetzt `ConstraintMessageResolver` automatisch. Ein Versionskonflikt (`@Version`, optimistisches Sperren) wird zu HTTP 409 „Jemand anders hat diese Daten gerade gespeichert …“.
- **Datenbank: nur PostgreSQL 16** (eigener Server und alle Kunden). MySQL, MariaDB und eine eingebaute H2-Datenbank gibt es nicht mehr; H2 läuft nur noch im Speicher in den automatischen Unit-Tests (`src/test/resources/application.properties`).
- **Flyway:** Eine neue Migration ist **eine** Datei unter `src/main/resources/db/postgresql/migration/V{N}__{beschreibung}.sql` (N = höchste vorhandene Nummer + 1). Bestehende Migrationen NIEMALS ändern. Sollen immer idempotent sein!! (`IF NOT EXISTS`, `ON CONFLICT DO NOTHING`, `DO $$ … $$`). Anleitung: `db/postgresql/migration/README.md`. Der Ordner `db/migration/` ist Geschichte – `FlywayMigrationenTest` lässt den Build scheitern, wenn dort wieder etwas liegt. Die Ordnernamen `db/postgresql/…` bleiben, damit die Flyway-Historie der laufenden Installationen gültig bleibt.
  Neuinstallationen bekommen auf leerer DB zuerst `db/postgresql/basis/V1__basis_schema.sql` (`FlywayStartSetupConfig`, erzeugt mit `scripts/basis-schema/postgres_erzeugen.sh`); `FlywayMigrationenTest` hält die Checksumme jeder Migration fest – eine neue Migration dort eintragen (der Test nennt den Wert); `DatenbankKompatibilitaetMigrationenTest` prüft, dass jede Migration einen zweiten Lauf übersteht.
- **Java-Enums = `VARCHAR(n)`-Spalte, OHNE `CHECK` mit Werteliste:** `@Enumerated(EnumType.STRING)` liegt in PostgreSQL als `VARCHAR`. Kein `CHECK (spalte IN ('A','B'))` – ein neuer Enum-Wert soll keine Migration brauchen (`FlywayMigrationenTest` prüft das). `n` so wählen, dass der längste Wert mit Luft passt. Werte exakt wie die Java-Enum-Konstanten (UPPERCASE).
- **Abfragen:** JPQL ist erste Wahl. Kein `@Lob` auf `String`/`byte[]` (wird in PostgreSQL zu `oid`), sondern `@JdbcTypeCode(SqlTypes.LONG32VARCHAR)` bzw. `SqlTypes.LONG32VARBINARY`; `CHAR(n)`-Spalten zusätzlich `@JdbcTypeCode(SqlTypes.CHAR)`. Optionale Filter `(:param IS NULL OR …)`: bei Datums-Parametern `CAST(:von AS LocalDate) IS NULL` schreiben (bzw. `LocalDateTime`) – PostgreSQL kennt den Typ sonst nicht. Wo natives SQL unvermeidbar ist: Repository-Fragment (`…RepositoryErweiterung` + `…Impl`) in PostgreSQL-Form, Vorbild `SpamTokenCountRepositoryErweiterungImpl`. `DatenbankKompatibilitaetTest` führt jede `@Query` gegen ein echtes PostgreSQL aus (CI-Job „Datenbank-Kompatibilität“; lokal: `./mvnw test "-Dtest=DatenbankKompatibilitaet*Test" -Dkompat.db.url=jdbc:postgresql://localhost:5432/kompat -Dkompat.db.user=kompat -Dkompat.db.pass=kompat` mit einem Wegwerf-Container `postgres:16`).
- **Suchen, Sortierung, Vergleiche (PostgreSQL ist streng):** In Specifications kein `cq.distinct(true)`, sobald sortiert wird – PostgreSQL verlangt bei `SELECT DISTINCT`, dass jeder Sortierausdruck (`lower(name)`, Spalten verbundener Tabellen) ausgewählt ist (Fehler 42P10). Treffer in Sammlungen (E-Mails, Projekte) per `EXISTS`-Unterabfrage – Vorbild `KundenSuche`/`LieferantenSuche`. Frei wählbare Sortierspalten über `PortableSortierung`. Bei fester Sortierung nach Spalten, die `NULL` erlauben, die Lage leerer Werte immer bewusst angeben: in JPQL `ASC NULLS FIRST` bzw. `DESC NULLS LAST` (so erwarten es die Nutzer; PostgreSQL macht es von sich aus umgekehrt; abgeleitete `…OrderBy…`-Methoden können das nicht – dann `@Query`). Textvergleiche auf Eingaben immer mit `lower(...)`/`…IgnoreCase` – PostgreSQL unterscheidet Groß-/Kleinschreibung (und vergleicht Umlaute nicht akzentfrei: „wurth“ findet „Würth“ nicht). Tests: `DatenbankKompatibilitaetSucheTest` (Such-Endpunkte mit allen Filtern und Sortierungen), `DatenbankKompatibilitaetSortierungTest` (jede Sortierung auf `NULL`-fähigen Spalten), `DatenbankKompatibilitaetEntitaetenTest` (jede Entität mit leeren Feldern speichern und laden).
- **PDFs (OpenPDF, Paket `org.openpdf`):** Writer immer über `util/PdfWriterFabrik` (`writer(…)` für ein neues Dokument, `kopie(…)` zum Zusammenfügen), nie `PdfWriter.getInstance` oder `new PdfCopy` direkt. Die Fabrik setzt PDF 1.7. Ohne sie schreibt OpenPDF 3 `%PDF-2.0`, und die E-Rechnung (PDF/A-3) fällt durch die Prüfung. `PdfWriterFabrikTest` lässt den Build bei einem direkten Aufruf scheitern. Schriften kommen aus `util/PdfSchriften`.

## Architektur-Patterns
- **Audit-Trail:** GoBD-konform mit vollständigen Snapshots (`ZeitbuchungAudit`, `BelegAudit`, `AusgangsGeschaeftsDokumentAudit`). Ausgangsdokumente und Belege hängen ihre Einträge zusätzlich an eine Hash-Kette (`AuditChainState` bzw. `BelegAuditChainState`; Prüfung durch `AuditChainVerifier` bzw. `BelegAuditChainVerifier`).
- **Dokumentketten:** `AusgangsGeschaeftsDokument` mit `vorgaenger`/`nachfolger`. Die Kette: Angebot/Nachtragsangebot → Auftragsbestätigung → Rechnung, Teil-, Abschlags- oder Schlussrechnung, dazu Gutschrift und Storno.
- **MonatsSaldo-Caching:** Vergangene Monate gecacht, aktueller Monat live.
- **Datei-Deduplizierung:** `LieferantDokument` → FK auf `EmailAttachment`.
- **Enum State-Management:** Typsichere Dokumenttypen (`AusgangsGeschaeftsDokumentTyp`), Mahnstufen.
- **Gleichzeitiges Bearbeiten:** `@Version` auf den Haupt-Entitäten (z. B. `Projekt`, `Kunde`, `AusgangsGeschaeftsDokument`). Dazu eine Bearbeiten-Sperre je Datensatz (`DatensatzLockService`, `SperrbarerTyp`; im PC-Frontend `components/lock/`).
- **Security (`config/SecurityConfig`):** fünf Filterketten in fester Reihenfolge (`@Order`):
  1. `/api/internal/**` für die Firmen-Website, mit Schlüssel im Header
  2. `ZEITERFASSUNG_PATHS` für die Handy-App mit Login-Token
  3. `OEFFENTLICHE_PFADE`, nur lesend (`OeffentlichePfadeCsrfTest`)
  4. `/api/**` mit Session + CSRF-Cookie
  5. ein Catch-all

  Neue Desktop-Endpunkte gehören unter `/api/**`. Ist ein Endpunkt nur für Admins, braucht er dort eine eigene Zeile `requestMatchers(…).hasRole("ADMIN")`. Method-Security (`@PreAuthorize`) ist nicht aktiv. Ein Endpunkt für die Handy-App muss zusätzlich in `ZEITERFASSUNG_PATHS` **und** in der abschließenden Liste `MobileApiPolicy` stehen (Test: `MobileApiPolicyTest`). Danach `python3 deployment/mobile-public/sync_routes.py` ausführen (unter Windows `python`), damit das öffentliche Handy-Gateway die Route auch durchlässt (`MobileGatewayRoutenTest` scheitert sonst).

## ML Spam-Filter (Naive Bayes)
Supervised Multinomial Naive Bayes in reinem Java.
- **Ensemble:** 40% Regel-Score (`SpamFilterService`) + 60% Bayes-Score (`SpamBayesService`).
- **Daten:** Token-Frequenzen in `spam_token_count` (Entity `SpamTokenCount`), In-Memory-Cache (5-Min-Refresh).
- **Feedback:** User trainiert das Modell über die Endpoints `POST /api/emails/{emailId}/mark-spam` bzw. `mark-not-spam` (`UnifiedEmailController`) weiter.

## Externe Dienste (Config)
- **Gemini (Google KI):** Der API-Key steht **nicht** in den Properties. Er wird in der Oberfläche gepflegt (System-Einstellungen → KI-Funktionen) und liegt in der Tabelle `system_setting` (`SystemSettingsService`). In den Properties stehen nur die Modelle (`ai.gemini.model.*`).
- **E-Mail (SMTP/IMAP):** Auch die Zugangsdaten stehen in `system_setting` und werden in der Oberfläche gepflegt (System-Einstellungen → E-Mail), nicht über `spring.mail.*`. `email.features.enabled` schaltet Import, Aufräumen und Lieferanten-Abruf als Hintergrundaufgaben an oder aus.
- `ai.rag.*`: Wissen der KI-Hilfe – Hybrid-Suche im Prozess (Vektor + Wortsuche `StichwortIndex`, in `LocalRagService`; Zerlegen/Einbetten/RAG-Datei in `service/kihilfe/RagIndexer`, Embeddings über Gemini). Reicht das Gefundene nicht, schlägt die KI über `KiHilfeWerkzeuge` selbst nach (Function Calling, max. 4 Runden; Gesprächsbausteine für alle KI-Dienste in `GeminiVerlauf`). Im Docker-Image liegt kein Quellcode: Die RAG-Datei entsteht beim Image-Bau und kommt mit dem Nachtupdate (`RAG_INDEXING.md`). **Neue Seite im PC-Frontend → Route in `LocalRagService.ROUTE_TO_FILE_KEYWORDS` eintragen.** Es braucht kein Qdrant; `QdrantRagService` wird nirgends verwendet.
- `ai.ollama.*`: lokales Ollama (optional)
- `spring.datasource.*` (PostgreSQL; Standard `jdbc:postgresql://localhost:5432/kalkulationsprogramm_db`, Zugangsdaten über `APP_DB_URL`/`APP_DB_USER`/`APP_DB_PASS` bzw. `application-local.properties`, im Docker-Profil der Dienst `postgres`)