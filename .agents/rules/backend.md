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
- **Flyway:** Neue Skripte unter `src/main/resources/db/migration/V{N}__{beschreibung}.sql`. Bestehende Migrationen NIEMALS ändern! Immer idempotent halten.
- **Java-Enums in MySQL = native `ENUM`-Spalte:** In Flyway-Migrationen immer `ENUM('WERT_A','WERT_B',...)` definieren (UPPERCASE), niemals `VARCHAR`, da Hibernate 6.x standardmäßig native ENUMs erwartet (`ddl-auto=validate`).
- **DTOs:** Entities niemals direkt als Controller-Rückgabe exponieren.

## Architektur-Patterns
- **Audit-Trail:** GoBD-konform (z.B. `ZeitbuchungAudit`, vollständige Snapshots).
- **Dokumentketten:** Angebote → Auftragsbestätigung → Rechnungen (Vorgänger/Nachfolger).
- **MonatsSaldo-Caching:** Vergangene Monate gecacht, aktueller Monat live.
