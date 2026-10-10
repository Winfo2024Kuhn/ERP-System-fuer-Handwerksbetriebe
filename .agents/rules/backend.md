# Backend & Architektur-Richtlinien

## Package-Struktur
`org.example.kalkulationsprogramm/`
- `controller/`: REST-Endpoints (Keine Business-Logik hier!)
- `service/`: Business-Logik
- `repository/`: Spring Data JPA
- `domain/`: JPA-Entities + Enums
- `dto/`: Data-Transfer-Objects (Entities NIE direkt exponieren!)
- `mapper/`: DTO ↔ Entity Mapper
- `config/`: Spring-Konfiguration
- `org.example.email/`: E-Mail-System (IMAP/SMTP)

## Coding-Regeln
- **Injection:** Constructor Injection (`@RequiredArgsConstructor` oder explizit). Keine `@Autowired` Field Injection.
- **SQL:** Ausschließlich parametrisierte Queries (`@Query` mit `:param`), kein String-Concat.
- **Datenbank:** Nur PostgreSQL 16 (H2 nur im Speicher in den Unit-Tests). Kein MySQL/MariaDB mehr.
- **Flyway:** Eine neue Migration ist eine Datei unter `src/main/resources/db/postgresql/migration/V{N}__{beschreibung}.sql` (N = höchste vorhandene + 1). Bestehende Migrationen NIEMALS ändern! Immer idempotent halten. In `db/migration/` nichts mehr ablegen (`FlywayMigrationenTest` bricht sonst den Build).
- **Java-Enums = `VARCHAR(n)`-Spalte ohne `CHECK` mit Werteliste:** `@Enumerated(EnumType.STRING)` liegt in PostgreSQL als `VARCHAR` (`ddl-auto=validate` prüft den Typ); neue Enum-Werte sollen keine Migration brauchen. Werte exakt wie die Java-Konstanten (UPPERCASE).
- **DTOs:** Entities niemals direkt als Controller-Rückgabe exponieren.

## Architektur-Patterns
- **Audit-Trail:** GoBD-konform (z.B. `ZeitbuchungAudit`, vollständige Snapshots).
- **Dokumentketten:** Angebote → Auftragsbestätigung → Rechnungen (Vorgänger/Nachfolger).
- **MonatsSaldo-Caching:** Vergangene Monate gecacht, aktueller Monat live.
