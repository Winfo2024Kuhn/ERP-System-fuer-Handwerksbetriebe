# 🧪 Testing & Endpoint-Security

## Test-Anforderungen
- **Service-Schicht:** JUnit 5 + Mockito (`@ExtendWith(MockitoExtension.class)`). Ziel: ≥ 80 % Branch-Coverage je neuer Klasse. `./mvnw test` schreibt den JaCoCo-Bericht nach `target/site/jacoco/`. Eine projektweite Schwelle gibt es bewusst nicht.
- **Controller-Schicht:** MockMvc (`@WebMvcTest`), Abhängigkeiten per `@MockitoBean` (`org.springframework.test.context.bean.override.mockito.MockitoBean`). `@MockBean` gibt es seit Spring Boot 4 nicht mehr. Test-Annotationen liegen in den Boot-4-Modulen, z. B. `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest` und `org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest`. Ziel: Alle Endpoints (Happy-Path + Fehlerfall).
- **Repository-Schicht:** H2 In-Memory (`@DataJpaTest`, Konfiguration `src/test/resources/application.properties`, Flyway aus).
- **Gegen echtes PostgreSQL:** Das prüfen `repository/DatenbankKompatibilitaet*Test` und `service/*PostgresTest`. Sie laufen nur mit System-Properties (`-Dkompat.db.url=…`, Befehl siehe `BACKEND_ARCH.md`), sonst werden sie übersprungen. Testcontainers gibt es nicht; lokal einen Wegwerf-Container `postgres:16` starten. In der CI laufen sie im Job „Datenbank-Kompatibilitaet (PostgreSQL)“, der auch prüft, dass sie wirklich liefen.
- **Frontend Unit/Komponenten-Tests:** Vitest + Testing Library + jsdom (neben Quell-Datei als `*.test.tsx`/`*.test.ts`). Befehle: `npm test`, Coverage mit `npm run test:coverage`.
- **End-to-End-Tests (Playwright - Pflicht whenever missing):** Immer wenn End-to-End-Tests fehlen, müssen für neue Features, Abläufe, Seiten und Dialoge Playwright-E2E-Tests geschrieben bzw. ergänzt werden (`react-pc-frontend/e2e/` und `react-zeiterfassung/e2e/`).
  - Start mit `npm run test:e2e`. Laufen mehrere Agenten parallel, bekommt jeder einen eigenen Port über `E2E_PORT`.
  - Es läuft kein Backend: Jede Spec stubbt ihre `/api`-Routen selbst (PC: Hilfen in `e2e/hilfen/api.ts`).
  - `test`/`expect` immer aus `./hilfen/test` importieren, nicht aus `@playwright/test`. Diese Datei sperrt fremde Netzwerkzugriffe, auch in Popups.
  - Die PC-Specs laufen in drei Größen: `pc-14zoll` 1440×900, `pc-uebergang` 1536×960 und `pc-monitor` 1920×1080. Die Handy-App läuft im Projekt `handy` (iPhone 15). PC zusätzlich: `npm run typecheck:e2e`.
- **Daten:** Immer Dummy-Daten nutzen (DSGVO!, z. B. `Max Mustermann`). Utils erfordern 100% Coverage.
- **CI:** `.github/workflows/pr-ci.yml` muss vor dem Merge grün sein („PR Quality Gate“). Geprüft werden Backend-Tests, Datenbank-Kompatibilität, Lint, Unit-Tests, Build und E2E beider Frontends sowie die Syntax der OpenFile-Launcher-Skripte (PowerShell-Parser, `bash -n`).

## Sicherheit
Die Sicherheitsregeln stehen dort, wo gebaut wird, getrennt nach Seite:
- **Backend (Java):** `BACKEND_ARCH.md` → „Sicherheit im Backend“ (Endpoint-Checkliste, ReDoS, Path-Traversal, CSRF, `HtmlKlartext`).
- **Frontend (React):** `FRONTEND_UI.md` → „Sicherheit im Frontend“ (`sanitizeHtml()`, `stripHtmlTags()`, `unescapeHtmlEntities()`, `toSafeResourceUrl()`, `parseHtmlInert()`).

Tests prüfen die Regeln der jeweiligen Seite: Backend-Tests die Endpoint-Checkliste (SQL-Injection-, XSS-String-, ID- und Limit-Fälle), Frontend-Tests die Bereinigung (`<img src=x onerror=…>`, `<script>`).
