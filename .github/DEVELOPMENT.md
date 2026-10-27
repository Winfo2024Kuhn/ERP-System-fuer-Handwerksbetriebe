# Repository Guidelines

## Project Structure & Modules
- `src/main/java/org/example/kalkulationsprogramm/` – Application code
    - `controller/` (REST endpoints), `service/` (business logic), `repository/` (Spring Data), `domain/` (JPA entities), `dto/` (API models), `mapper/`, `config/` (Spring Security, filters, startup runners)
- `src/main/java/org/example/email/` – Mail sending (`EmailService`)
- `src/main/resources/` – Configuration (`application.properties`, `application-docker.properties`, `application-local.properties.example`), `db/postgresql/` (base schema + Flyway migrations), `fonts/`, `static/` (checked-in build of both frontends)
- `src/test/java/org/example/kalkulationsprogramm/` – JUnit tests
- `react-pc-frontend/` – Desktop UI (React 19, Vite 7, Tailwind 3), `react-zeiterfassung/` – mobile PWA (React 19, Vite 7, Tailwind 4)
- `deployment/docker-server/` – Docker setup for customer servers (postgres, app, updater, netzsperre)
- `uploads/` – Runtime file storage (ignored by Git)

## Build, Test, and Run
- Build JAR: `./mvnw clean package` – compiles, tests, packages
- Database: PostgreSQL 16 is the only database. Start one locally before running the app, e.g. `docker run -d --name erp-dev-pg -p 127.0.0.1:5432:5432 -e POSTGRES_DB=kalkulationsprogramm_db -e POSTGRES_USER=erp_user -e POSTGRES_PASSWORD=erp_dev postgres:16`, and put the credentials into `application-local.properties` (never committed). An empty database gets the base schema and all migrations on first start.
- Run locally: `./mvnw spring-boot:run` – starts the API on port 8080 (`server.port`), profile `local` (reads `application-local.properties`)
- Run tests: `./mvnw test` – executes unit/integration tests (H2 in memory, no database needed); JaCoCo writes a coverage report to `target/site/jacoco`
- Note: Surefire runs only its default patterns (`*Test.java`, `*Tests.java`, `Test*.java`, `*TestCase.java`); no Failsafe plugin is configured. Classes named `*IT.java` (e.g. `ProjektManagementServiceIT`) do **not** run with `./mvnw test` and therefore not in CI either.
- Database tests against real PostgreSQL: `./mvnw test "-Dtest=DatenbankKompatibilitaet*Test" -Dkompat.db.url=jdbc:postgresql://localhost:5432/kompat -Dkompat.db.user=kompat -Dkompat.db.pass=kompat` (throwaway `postgres:16` container)
- Frontends: `npm run dev` / `npm run build` / `npm run lint` / `npm test` / `npm run test:e2e` in `react-pc-frontend/` and `react-zeiterfassung/`. `npm run build` writes into `src/main/resources/static/` – the build output is committed.
- Requires Java 25; Maven wrapper included (no global install needed)

## Coding Style & Naming
- Java conventions: classes `UpperCamelCase`, methods/fields `lowerCamelCase`
- Indentation: 4 spaces; one public class per file
- Prefer constructor injection for Spring components; Lombok allowed (e.g., `@AllArgsConstructor`)
- Package placement must match responsibility (controller/service/repository/domain/dto)
- Use  DRY (Don't Repeat Yourself) principles to avoid code duplication

## Localization & Language
- Frontend copy must use German wording with proper umlauts (ä, ö, ü, ß); avoid fallback spellings like "ae"/"oe"/"ue" unless technical constraints require otherwise.

## Testing Guidelines

### Grundprinzipien
- **Jede neue Funktionalität MUSS mit Tests begleitet werden** – kein Merge ohne Tests.
- **Test-Pyramide einhalten:** Viele Unit-Tests, wenige Integrationstests, minimale E2E-Tests.
- **Testbare Architektur:** Code so schreiben, dass er testbar ist (Dependency Injection, keine statischen Abhängigkeiten, kleine Methoden mit klarer Verantwortung).
- **Keine Tests ohne Assertion:** Jeder `@Test` muss mindestens eine bedeutungsvolle Assertion enthalten.

### Backend-Tests (Java / Spring Boot)

#### Frameworks & Tools
| Tool | Zweck |
|---|---|
| **JUnit 5** | Test-Framework (`@Test`, `@Nested`, `@BeforeEach`, `@ParameterizedTest`) |
| **Mockito** | Mocking (`@Mock`, `@InjectMocks`, `@ExtendWith(MockitoExtension.class)`) |
| **MockMvc** | REST-Controller-Tests (`@WebMvcTest`) |
| **AssertJ** | Fluent Assertions (`.isEqualTo()`, `.containsExactly()`, `.isNotNull()`) |
| **H2** | In-Memory-Datenbank für Repository-Tests (`@DataJpaTest`) – nur in Tests; die Anwendung selbst läuft immer auf PostgreSQL |
| **Awaitility** | Asynchrone Assertions für zeitbasierte Logik |
| **GreenMail** | Test-Mailserver (SMTP/IMAP) für E-Mail-Tests |
| **PostgreSQL 16** | Nur für die `DatenbankKompatibilitaet*Test`-Klassen (laufen nur mit `-Dkompat.db.url`, in der CI im Job „Datenbank-Kompatibilitaet (PostgreSQL)“) |

#### Dateistruktur & Namenskonventionen
- **Ort:** Spiegelpakete unter `src/test/java/org/example/kalkulationsprogramm/`
- **Unit-Tests:** `{ClassName}Test.java` (z.B. `AbwesenheitServiceTest.java`)
- **Integrationstests:** `{ClassName}IT.java` (z.B. `ProjektManagementServiceIT.java`) – Achtung: Diese Klassen laufen mit `./mvnw test` und in der CI derzeit **nicht** mit (siehe oben). Integrationstests, die mitlaufen sollen, wie Unit-Tests auf `Test` enden lassen (z.B. die `DatenbankKompatibilitaet*Test`-Klassen).
- **Test-Methoden:** Beschreibend auf Deutsch oder Englisch, z.B. `gibtLeereListeZurueckWennKeineArtikelVorhanden()`

#### Service-Tests (Unit-Tests)
```java
@ExtendWith(MockitoExtension.class)
class MeinServiceTest {

    @Mock
    private MeinRepository repository;

    @InjectMocks
    private MeinService service;

    @Test
    void gibtLeereListeZurueckWennKeineEintraegeVorhanden() {
        when(repository.findAll()).thenReturn(Collections.emptyList());

        List<MeinDto> result = service.alleAbrufen();

        assertThat(result).isEmpty();
        verify(repository).findAll();
    }
}
```

#### Controller-Tests (REST-Slice-Tests)
```java
@WebMvcTest(MeinController.class)
class MeinControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MeinService service;

    @Test
    void getEndpointGibt200Zurueck() throws Exception {
        when(service.alleAbrufen()).thenReturn(List.of());

        mockMvc.perform(get("/api/mein-endpoint"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
```

#### Repository-Tests (Datenbank-Slice-Tests)
```java
@DataJpaTest
class MeinRepositoryTest {

    @Autowired
    private MeinRepository repository;

    @Test
    void speichertUndFindetEntity() {
        MeineEntity entity = new MeineEntity();
        entity.setName("Test");

        MeineEntity saved = repository.save(entity);

        assertThat(saved.getId()).isNotNull();
        assertThat(repository.findById(saved.getId())).isPresent();
    }
}
```

#### Test-Strukturierung mit @Nested
Für Services mit vielen Methoden, `@Nested`-Klassen verwenden:
```java
@ExtendWith(MockitoExtension.class)
class KomplexerServiceTest {

    @Nested
    class BerechneMethode {
        @Test
        void gibtNullZurueckBeiLeeremInput() { /* ... */ }

        @Test
        void berechnetKorrektMitStandardwerten() { /* ... */ }
    }

    @Nested
    class SpeichereMethode {
        @Test
        void wirftExceptionBeiUngueltigemInput() { /* ... */ }
    }
}
```

#### Was MUSS getestet werden?
| Schicht | Was testen? | Annotation |
|---|---|---|
| **Service** | Geschäftslogik, Berechnungen, Fehlerfälle, Edge-Cases | `@ExtendWith(MockitoExtension.class)` |
| **Controller** | HTTP-Status, Request/Response-Mapping, Validierung | `@WebMvcTest` |
| **Repository** | Custom Queries, Cascading, komplexe Finder-Methoden | `@DataJpaTest` |
| **Mapper** | DTO ↔ Entity Konvertierung | Plain JUnit |
| **Utils** | Hilfsfunktionen, Sanitizer, Parser | Plain JUnit |

### Frontend-Tests (React / TypeScript)

#### Framework-Setup (Vitest + Testing Library)
Das Frontend verwendet **Vitest** als Test-Runner und **@testing-library/react** für Komponententests.

**Test-Script in `package.json`:**
```json
"scripts": {
    "test": "vitest run",
    "test:watch": "vitest",
    "test:coverage": "vitest run --coverage"
}
```

#### Dateistruktur & Namenskonventionen
- **Test-Dateien:** Neben der Quell-Datei, z.B. `Button.test.tsx` neben `Button.tsx`
- **Alternativ:** `__tests__/` Verzeichnis im gleichen Ordner
- **Namensschema:** `{Komponentenname}.test.tsx` oder `{Hook}.test.ts`

#### Komponenten-Test-Pattern
```tsx
import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { MeineKomponente } from './MeineKomponente';

describe('MeineKomponente', () => {
    it('rendert den Titel korrekt', () => {
        render(<MeineKomponente titel="Hallo" />);
        expect(screen.getByText('Hallo')).toBeInTheDocument();
    });

    it('ruft onClick-Handler auf bei Klick', () => {
        const handleClick = vi.fn();
        render(<MeineKomponente onClick={handleClick} />);

        fireEvent.click(screen.getByRole('button'));
        expect(handleClick).toHaveBeenCalledOnce();
    });
});
```

#### Hook-Test-Pattern
```tsx
import { renderHook, act } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { useMeinHook } from './useMeinHook';

describe('useMeinHook', () => {
    it('initialisiert mit Standardwert', () => {
        const { result } = renderHook(() => useMeinHook());
        expect(result.current.wert).toBe(0);
    });
});
```

#### Was MUSS getestet werden (Frontend)?
| Kategorie | Beispiel |
|---|---|
| **UI-Komponenten** | Rendering, Props-Verhalten, Conditional Rendering |
| **Formulare** | Validierung, Submit-Handler, Fehlermeldungen |
| **Custom Hooks** | State-Änderungen, Side-Effects, Return-Werte |
| **Utility-Funktionen** | Formatierung, Berechnungen, Parser |
| **Modale/Dialoge** | Öffnen/Schließen, Bestätigungsfluss |

#### End-to-End-Tests (Playwright)
- **Ort und Start:** `react-pc-frontend/e2e/` und `react-zeiterfassung/e2e/`, jeweils `npm run test:e2e`. In der CI Teil des „PR Quality Gate“.
- **PC-Frontend:** läuft gegen einen frischen Produktions-Build (`vite preview`, Port `E2E_PORT`, Standard 4173) – ohne Spring Boot und ohne Datenbank: Jeder Test stubbt seine `/api`-Routen selbst (`e2e/hilfen/api.ts`), nur mit Dummy-Daten. Jede Spec läuft in drei Fenstergrößen (1440×900, 1536×960, 1920×1080). Mit `E2E_DEVSERVER=1` oder `npm run test:e2e:ui` läuft stattdessen der Dev-Server.
- **Zeiterfassung:** läuft gegen den Vite-Dev-Server mit HTTPS (Port `E2E_PORT`, Standard 5175) im Handy-Format (iPhone 15, Chromium).

#### Test-Abdeckungsziele
- **Service-Klassen (Backend):** ≥ 80% Line Coverage
- **Controller (Backend):** Alle Endpoints mit Happy-Path + Fehlerfall
- **UI-Komponenten (Frontend):** Alle Props-Varianten, User-Interaktionen
- **Utility/Helper:** 100% Coverage anstreben

### Sicherheitstests (OWASP Top 10)

Jede neue Funktionalität, die User-Input entgegennimmt, MUSS mit Sicherheitstests abgedeckt werden. Die Tests basieren auf dem **OWASP Top 10** Standard.

Die Pfade in den Beispielen (z.B. `/api/dateien/upload`, `/api/angebote`) sind Platzhalter – die echten Endpunkte stehen im jeweiligen Controller.

#### 1. SQL Injection (A03:2021 – Injection)
Spring Data JPA mit parametrisierten Queries schützt vor SQL Injection. **Trotzdem testen:**

```java
@Test
void sucheMitSqlInjectionGibtLeereListeZurueck() {
    // Sicherstellen, dass SQL-Injection-Versuche kein Ergebnis liefern
    // und keine Exception werfen
    mockMvc.perform(get("/api/artikel")
            .param("search", "'; DROP TABLE artikel; --"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(0)));
}

@Test
void sucheMitUnionInjectionWirdAbgefangen() {
    mockMvc.perform(get("/api/artikel")
            .param("search", "' UNION SELECT * FROM users --"))
            .andExpect(status().isOk());
}
```

**Regel:** NIEMALS String-Konkatenation in SQL/JPQL verwenden. Immer `@Query` mit benannten Parametern (`:param`) oder Spring Data Derived Queries verwenden.

#### 2. Cross-Site Scripting / XSS (A03:2021 – Injection)
Alle HTML-Ausgaben müssen sanitisiert werden: E-Mail-HTML über `EmailHtmlSanitizer`, anderes gespeichertes HTML per jsoup-`Safelist` (Vorbild `SignaturHtmlPruefung`).

```java
@Test
void xssInNameWirdBereinigt() {
    String maliciousInput = "<script>alert('XSS')</script>";
    String result = sanitizer.sanitize(maliciousInput);

    assertThat(result).doesNotContain("<script>");
    assertThat(result).doesNotContain("alert");
}

@Test
void xssInBeschreibungsfeldWirdEscaped() {
    String input = "<img src=x onerror=alert(1)>";
    String result = sanitizer.sanitize(input);

    assertThat(result).doesNotContain("onerror");
}
```

**Regel im Frontend (React):** Kein `dangerouslySetInnerHTML` ohne `sanitizeHtml()` aus `react-pc-frontend/src/lib/htmlSanitizer.ts` (DOMPurify) (oder eine strengere eigene DOMPurify-Instanz mit Positivliste, Vorbild `bereinigeBeitragsHtml`; nie lockerer), auch wenn das Backend die Daten schon bereinigt hat. Gleiches gilt für `element.innerHTML = …` und `insertAdjacentHTML` an angezeigten Elementen (z. B. contentEditable-Editoren). `EmailHtmlSanitizer` ist eine Backend-Klasse und gilt nur für E-Mails. HTML, das nur ausgelesen wird, über `parseHtmlInert()`/`htmlToText()` parsen, nie über `document.createElement('div').innerHTML`.

#### 3. Path Traversal (A01:2021 – Broken Access Control)
Datei-Upload und -Download-Endpoints müssen gegen Path-Traversal geschützt sein.

```java
@Test
void dateiUploadMitPathTraversalWirdAbgelehnt() {
    mockMvc.perform(multipart("/api/dateien/upload")
            .file(new MockMultipartFile(
                    "datei", "../../etc/passwd", "text/plain", "hack".getBytes())))
            .andExpect(status().isBadRequest());
}

@Test
void dateiDownloadMitPathTraversalWirdAbgelehnt() {
    mockMvc.perform(get("/api/dateien/download")
            .param("pfad", "../../../etc/passwd"))
            .andExpect(status().isBadRequest());
}
```

**Regel:** Dateinamen IMMER normalisieren und validieren. Pfade MÜSSEN innerhalb des `uploads/`-Verzeichnisses bleiben. UUID-basierte Dateinamen bevorzugen (wie bereits im Projekt umgesetzt).

#### 4. Unsichere Deserialisierung & Mass Assignment (A08:2021)
DTOs schützen vor Mass Assignment. Trotzdem testen, dass keine unerwünschten Felder gesetzt werden können.

```java
@Test
void erstelleAngebotIgnoriertUnbekannteFelder() throws Exception {
    String jsonMitExtraFeld = """
        {"titel": "Angebot", "admin": true, "rolle": "ADMIN"}
    """;

    mockMvc.perform(post("/api/angebote")
            .contentType(MediaType.APPLICATION_JSON)
            .content(jsonMitExtraFeld))
            .andExpect(status().isOk());
    // Sicherstellen, dass "admin" und "rolle" ignoriert wurden
}
```

#### 5. Input-Validierung (A03:2021 – Injection)
Alle Eingaben an den Systemgrenzen validieren.

```java
@Test
void erstelleKundeMitLeeremNamenGibt400() throws Exception {
    mockMvc.perform(post("/api/kunden")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\": \"\"}"))
            .andExpect(status().isBadRequest());
}

@Test
void erstelleKundeMitUeberlangemNamenGibt400() throws Exception {
    String langerName = "A".repeat(10000);
    mockMvc.perform(post("/api/kunden")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\": \"" + langerName + "\"}"))
            .andExpect(status().isBadRequest());
}
```

#### 6. Datei-Upload-Sicherheit
```java
@Test
void blockiertGefaehrlicheDateitypen() throws Exception {
    mockMvc.perform(multipart("/api/dateien/upload")
            .file(new MockMultipartFile(
                    "datei", "virus.exe", "application/x-msdownload", "content".getBytes())))
            .andExpect(status().isBadRequest());
}

@Test
void blockiertZuGrosseDateien() throws Exception {
    byte[] grosseDatei = new byte[100 * 1024 * 1024]; // 100 MB
    mockMvc.perform(multipart("/api/dateien/upload")
            .file(new MockMultipartFile("datei", "test.pdf", "application/pdf", grosseDatei)))
            .andExpect(status().is4xxClientError());
}
```

#### Sicherheitstest-Checkliste für neue Endpoints

Bei jedem neuen REST-Endpoint MÜSSEN folgende Tests geschrieben werden:

| Kategorie | Test | Pflicht? |
|---|---|---|
| **SQL Injection** | `'; DROP TABLE x; --` in allen String-Parametern | JA |
| **XSS** | `<script>alert(1)</script>` in allen Textfeldern | JA |
| **Path Traversal** | `../../etc/passwd` bei Dateipfaden | JA, bei Datei-Endpoints |
| **Ungültige IDs** | Negative IDs, Long.MAX_VALUE, 0 | JA |
| **Leere Pflichtfelder** | Leerer Body, fehlende Felder | JA |
| **Überlange Eingaben** | Strings > 10.000 Zeichen | JA |
| **Content-Type-Mismatch** | JSON-Endpoint mit XML aufrufen | JA |
| **Ungültige Datei-Typen** | `.exe`, `.bat`, `.sh` bei Uploads | JA, bei Upload-Endpoints |

#### Sichere Coding-Muster (Backend)

```java
// RICHTIG: Parametrisierte Query
@Query("SELECT a FROM Artikel a WHERE a.name LIKE %:name%")
List<Artikel> findByName(@Param("name") String name);

// FALSCH: String-Konkatenation → SQL Injection!
@Query("SELECT a FROM Artikel a WHERE a.name LIKE '%" + name + "%'")
List<Artikel> findByName(String name);

// RICHTIG: Dateiname normalisieren
String safeName = Paths.get(originalName).getFileName().toString();

// FALSCH: Dateiname direkt verwenden
Path target = uploadDir.resolve(userProvidedFilename);
```

#### Sichere Coding-Muster (Frontend)

```tsx
// RICHTIG: React escaped automatisch
<p>{benutzerEingabe}</p>

// FALSCH: Unsanitisiertes HTML
<div dangerouslySetInnerHTML={{ __html: benutzerEingabe }} />

// RICHTIG: HTML im Frontend bereinigen (lib/htmlSanitizer.ts, DOMPurify)
<div dangerouslySetInnerHTML={{ __html: sanitizeHtml(gespeichertesHtml) }} />

// RICHTIG: HTML nur auslesen, ohne es auszuführen
const text = htmlToText(gespeichertesHtml);

// RICHTIG: URL-Parameter escapen
const url = `/api/search?q=${encodeURIComponent(suchbegriff)}`;

// FALSCH: Direkte String-Interpolation
const url = `/api/search?q=${suchbegriff}`;
```

## Commit & Pull Requests
- Commits: clear, imperative subject; concise body with rationale; group related changes
- PRs: include summary, linked issues, reproduction/verification steps, and screenshots for UI changes
- Expect `./mvnw test` to pass; update/add tests when changing behavior
- `main` only via pull request (a ruleset rejects direct pushes). Required check: "PR Quality Gate" from `.github/workflows/pr-ci.yml` (backend tests, PostgreSQL compatibility, lint, unit tests, build and E2E for both frontends, syntax of the OpenFile Launcher scripts). Green PRs from the owner account are merged by `.github/workflows/pr-automatik.yml` (label `kein-automerge` stops it); every merge to `main` is built by `.github/workflows/docker-image.yml`, start-tested and published as `:stable`, which customer servers install in the nightly update.

## Security & Configuration

### Allgemeine Regeln
- **Keine Secrets im Code:** Passwörter, API-Keys, Tokens NIEMALS committen. Lokale Overrides in `application-local.properties` (in `.gitignore`).
- `uploads/` ist nicht versioniert – Laufzeit-Ablage für Dokumente, Bilder und E-Mail-Anhänge. Enthält Kunden- und Mitarbeiterdaten, daher nie committen.
- App-Konfiguration in `src/main/resources/`; pro Umgebung über Properties-Overrides (Profil `local` → `application-local.properties`, Profil `docker` → `application-docker.properties`). E-Mail-Zugang, KI-Schlüssel und Datei-Ordner werden in der App unter Einstellungen gepflegt und in der Tabelle `system_setting` gespeichert.

### OWASP Top 10 – Pflicht-Maßnahmen

| # | Risiko | Maßnahme im Projekt |
|---|---|---|
| A01 | **Broken Access Control** | Datei-Pfade immer normalisieren, UUID-Dateinamen, kein direkter Zugriff auf `uploads/` |
| A02 | **Cryptographic Failures** | Keine Secrets im Code, HTTPS in Produktion, sichere Passwort-Hashing |
| A03 | **Injection** | Spring Data parametrisierte Queries, kein String-Concat in JPQL/SQL, HTML-Sanitisierung |
| A04 | **Insecure Design** | DTOs verwenden (nie Entities direkt exponieren), Input-Validierung an Systemgrenzen |
| A05 | **Security Misconfiguration** | CORS restriktiv konfigurieren (`app.cors.allowed-origins`, Standard leer), erlaubte Hosts (`app.security.allowed-hosts`), keine Stack-Traces in Produktion |
| A06 | **Vulnerable Components** | Dependencies regelmäßig updaten, `./mvnw versions:display-dependency-updates` |
| A07 | **Auth Failures** | Spring Security (`config/SecurityConfig.java`): Login mit Sitzung und CSRF-Cookie, Passwörter mit BCrypt; Handy-App mit Mitarbeiter-Token und Fehlversuchs-Begrenzung (`docs/MOBILE_TOKEN_SECURITY.md`); `/api/internal` nur mit Website-Schlüssel (`X-ERP-Website-Key`) |
| A08 | **Data Integrity Failures** | DTOs gegen Mass-Assignment – unbekannte JSON-Felder ignoriert Jackson (Spring-Boot-Standard `FAIL_ON_UNKNOWN_PROPERTIES=false`), sie dürfen also nie in Entities durchrutschen |
| A09 | **Logging Failures** | Sicherheitsrelevante Aktionen loggen (Dateiupload, Löschvorgänge) |
| A10 | **SSRF** | Keine User-gesteuerten URLs für Server-Requests – wenn nötig, Allowlist verwenden |

## Frontend Implementation
- Bevorzugt React-Komponenten und -State für neue oder anzupassende UI-Funktionen, anstatt vanilla JS oder DOM-Manipulation.
- Führe immer builds aus von `npm run build`

## Button Design Guidelines

Buttons sollen im gesamten Programm einheitlich gestaltet werden, um eine konsistente Benutzererfahrung zu gewährleisten. Dafür gibt es die Komponente `Button` aus `src/components/ui/button.tsx` mit den Varianten `default`, `outline`, `ghost` und `secondary` und den Größen `default` und `sm`.

### Primärer Button (Hauptaktionen)
- **CSS-Klassen:** `bg-rose-600 text-white border border-rose-600 hover:bg-rose-700` (= `variant="default"`)
- **Verwendung:** Hauptaktionen wie "Neu", "Speichern"
- **Beispiele:**
    - Leistungen & Kategorien → "Neue Leistung" (`src/pages/leistungen/LeistungenReiter.tsx`)
    - E-Mail-Textvorlagen → "Speichern" (`src/pages/EmailTextvorlagenEditor.tsx`)

### Sekundärer Button (Outline)
- **CSS-Klassen:** `variant="outline"` (= `bg-white text-rose-700 border border-rose-200 hover:bg-rose-50`)
- **Verwendung:** Abbrechen, Zurück, sekundäre Aktionen
- **Beispiele:**
    - "Abbrechen" Button in allen Formularen
    - "Ordner prüfen" in den Einstellungen → Dateien

### Ghost Button (Minimal)
- **CSS-Klassen:** `variant="ghost"` mit `text-rose-700 hover:bg-rose-100`
- **Verwendung:** In Toolbars, für Bearbeitungsaktionen in Listen
- **Beispiele:**
    - Toolbar-Buttons im TiptapEditor (`src/components/TiptapEditor.tsx`)
    - "Bearbeiten" und "Löschen" Buttons in Leistungslisten

### Button-Größen
- `size="sm"` – Standard für die meisten Buttons
- Normale Größe – nur für besonders wichtige Hauptaktionen

### Icons in Buttons
- Immer links vom Text platzieren
- Lucide React Icons verwenden (`lucide-react`)
- Größe: `className="w-4 h-4"` für `size="sm"` Buttons

### Farbschema
Das gesamte Programm verwendet das **Rose**-Farbschema:
- Farbe des Firmenlogos/App-Icons: `#dc2626` (`red-600`) – nicht die UI-Primärfarbe
- `rose-50` bis `rose-900` für Primärfarben, Primärfarbe `rose-600` (`#e11d48`)
- `slate-50` bis `slate-900` für neutrale Farben
- Kein indigo, blue oder andere Akzentfarben verwenden

### Dropdown / Select Design

Für alle Dropdowns (Select-Menüs) im Projekt MUSS die projekt-eigene React-Komponente `Select` aus `src/components/ui/select-custom.tsx` verwendet werden.

- **Import:** `import { Select } from '../components/ui/select-custom';` (Pfad je nach Dateiort anpassen)
- **Verwendung:** Anstelle von nativem HTML `<select>`.
- **Styling:** Die Komponente bringt ihr eigenes Styling mit (Ring, Border, etc.) passend zum Rose-Theme.

#### Select-Komponenten-API

```tsx
import { Select } from '../components/ui/select-custom';

<Select
    value={selectedValue}
    onChange={(value) => setSelectedValue(value)}
    options={[
        { value: '', label: 'Bitte auswählen' },
        { value: 'option1', label: 'Option 1' },
        { value: 'option2', label: 'Option 2' }
    ]}
    placeholder="Auswahl..."
    className="w-full" // optional
/>
```

**Props:**
- `value`: Der aktuell ausgewählte Wert (string)
- `onChange`: Callback-Funktion, die den ausgewählten Wert direkt empfängt (NICHT ein Event-Objekt!)
- `options`: Array von `{ value: string; label: string; disabled?: boolean; gruppe?: string }` Objekten (`gruppe` setzt eine nicht anklickbare Überschrift über die Optionen dieser Gruppe)
- `placeholder`: Optionaler Platzhaltertext (Standard: "Bitte wählen...")
- `className`: Optionale zusätzliche CSS-Klassen
- `disabled`, `required`, `error`, `id`, `name`: Optional, wie bei normalen Formularfeldern (`error` zeigt eine Fehlermeldung unter dem Feld)

### DatePicker Design

Für alle Datumsfelder im Projekt MUSS die projekt-eigene React-Komponente `DatePicker` aus `src/components/ui/datepicker.tsx` verwendet werden.

- **Import:** `import { DatePicker } from '../components/ui/datepicker';` (Pfad je nach Dateiort anpassen)
- **Verwendung:** Anstelle von nativem HTML `<input type="date">`.
- **Features:** Kalender-Popup mit Eingabefeld (TT.MM.JJJJ), Monats-/Jahresansicht, "Heute"/"Löschen"/"Schließen", deutsche Lokalisierung.

#### DatePicker-Komponenten-API

```tsx
import { DatePicker } from '../components/ui/datepicker';

<DatePicker
    value={dateValue}  // Format: "YYYY-MM-DD" oder ""
    onChange={(value) => setDateValue(value)}
    placeholder="Datum wählen"
    disabled={false}
    className="w-full" // optional
/>
```

**Props:**
- `value`: Der Datumswert im Format `YYYY-MM-DD` (string) oder leerer String
- `onChange`: Callback-Funktion, die den neuen Datumswert direkt empfängt
- `placeholder`: Optionaler Platzhaltertext (Standard: "Datum wählen")
- `disabled`: Optionales Deaktivieren der Eingabe
- `className`: Optionale zusätzliche CSS-Klassen
- `min` / `max`: Optional, frühestes/spätestes erlaubtes Datum (`YYYY-MM-DD`); Tage außerhalb sind gesperrt
- `required`, `error`, `id`, `name`: Optional, wie bei normalen Formularfeldern

**Besondere Features:**
- Feld "Datum eingeben" oben im Popup: Datum tippen (TT.MM.JJJJ), mit Enter oder "Übernehmen" übernehmen – schnellster Weg zu weit entfernten Daten (z.B. Geburtsdatum)
- `<` / `>` Buttons blättern je nach Ansicht Monate, Jahre oder Jahresseiten
- Klick auf den Titel wechselt von der Tagesansicht in die Monats- und weiter in die Jahresansicht
- "Heute"-Button setzt das aktuelle Datum
- "Löschen"-Button entfernt das Datum
- Tastaturbedienung (Pfeiltasten, Escape)

## Page Header Design Guidelines

Alle React-Seiten müssen das gleiche, standardisierte Header-Pattern verwenden, um eine konsistente Benutzererfahrung zu gewährleisten.

### Header-Struktur
Jede Seite muss folgende Elemente im Header aufweisen:
1. **Kategorie-Label** – Kleine, uppercase Kategorie-Bezeichnung
2. **Seitentitel** – Großes, fettes, uppercase Titel
3. **Beschreibung** – Subtile Beschreibung der Seite
4. **Action Buttons** – Rechts ausgerichtet (optional)

### Umsetzung: `PageLayout`

Das Header-Pattern nicht von Hand nachbauen, sondern `PageLayout` aus `src/components/layout/PageLayout.tsx` verwenden. Es setzt Seitenbreite, Abstände und den Header (Titel automatisch in Uppercase):

```tsx
<PageLayout
    ribbonCategory="Stammdaten"
    title="Lieferanten"
    subtitle="Beschreibungstext"
    actions={<Button size="sm">...</Button>}
>
    {/* Seiteninhalt */}
</PageLayout>
```

Nur für einen Header ohne Seitenrahmen gibt es `PageHeader` aus `src/components/PageHeader.tsx` (Props `category`, `title`, `description`, `actions`).

### CSS-Klassen (zur Referenz, so rendert `PageLayout` den Header)

```tsx
{/* Standard Page Header */}
<div className="flex flex-col md:flex-row justify-between gap-4 md:items-end mb-8">
    <div>
        <p className="text-sm font-semibold text-rose-600 uppercase tracking-wide">
            {/* Kategorie-Label, z.B. "Stammdaten", "Buchhaltung", "Controlling" */}
        </p>
        <h1 className="text-3xl font-bold text-slate-900 uppercase">
            {/* Seitentitel – wird per CSS in Uppercase angezeigt */}
        </h1>
        <p className="text-slate-500 mt-1">
            {/* Beschreibungstext */}
        </p>
    </div>
    <div className="flex gap-2">
        {/* Action Buttons */}
    </div>
</div>
```

### Beispiele für Kategorie-Labels
| Seite                | Kategorie-Label     |
|----------------------|---------------------|
| LieferantenEditor    | Stammdaten          |
| ProjektEditor        | Projektmanagement   |
| ArtikelEditor        | Katalog             |
| OffenePostenEditor   | Buchhaltung         |
| ErfolgsanalyseEditor | Controlling         |
| FormularwesenMain (Formularwesen) | Vorlagen-Center |
| BestellungEditor     | Einkauf             |
| LeistungenUndKategorien | Katalog          |
| ArbeitsgangEditor    | Arbeitsplanung      |
| AnfrageEditor        | Anfragemanagement   |
| TelefonPage, EmailEinstellungenPage | Kommunikation |
| MietabrechnungEditor | Finanzen & Controlling |

### Anwendung
- Neue Seiten MÜSSEN dieses Header-Pattern verwenden (über `PageLayout`)
- Die Kategorie-Label Farbe ist immer `text-rose-600`
- Der Titel ist immer in `UPPERCASE` geschrieben
- Die Beschreibung verwendet `text-slate-500`

## Image Viewer Design Guidelines

Für alle Vollbild-Anzeigen von Bildern MUSS die projekt-eigene React-Komponente `ImageViewer` aus `src/components/ui/image-viewer.tsx` verwendet werden.

- **Import:** `import { ImageViewer } from '../components/ui/image-viewer';`
- **Styling:** "Clean Look" mit dunklem Overlay, zentriertem Bild und schwebendem Schließen-Button.

### ImageViewer-Komponenten-API

```tsx
<ImageViewer
    src={selectedImageUrl} // string | null
    alt="Beschreibung"     // optional
    onClose={() => setSelectedImageUrl(null)}
/>
```

**Props:**
- `src`: Die URL des Bildes (wenn `null`, wird nichts angezeigt)
- `onClose`: Callback zum Schließen (Setzt State auf null)
- `alt`: Alt-Text für das Bild
- `images`: Optional, Liste `{ url: string; name?: string }[]` zum Durchblättern mehrerer Bilder (Galerie)
- `startIndex`: Optional, mit welchem Bild aus `images` die Anzeige startet

Zoomen per Mausrad, Doppelklick oder Knopf; vergrößerte Bilder lassen sich verschieben.


## React Templates & DRY Principles

Um das **DRY (Don't Repeat Yourself)** Prinzip zu wahren und ein konsistentes UI zu gewährleisten, sind folgende Templates und Komponenten zu nutzen:

### DetailLayout
- **Pfad:** `src/components/DetailLayout.tsx`
- **Verwendung:** Für alle Detailansichten (Kunde, Lieferant, Projekt, Anfrage, Mitarbeiter).
- **Props:**
    - `header`: Bento-Stats Header und Navigations-Buttons.
    - `mainContent`: Hauptinhalt links (z.B. Reiter mit `EmailsTab`).
    - `sideContent`: Seitenleiste rechts (z.B. Kontaktdaten, Map).

### EmailsTab
- **Pfad:** `src/components/EmailsTab.tsx`
- **Verwendung:** Einheitliche Darstellung von E-Mail-Verläufen in Detailansichten (Kunde, Projekt, Anfrage, Lieferant). Genau einen Bezug übergeben: `kundeId`, `projektId`, `anfrageId` oder `lieferantId`.
- **Features:** Suche, Verläufe mit Antworten, Anhang-Vorschau, Zustellstatus (unzustellbar), „Neue E-Mail“ und „Antworten“ (abschaltbar über `showComposeButton`/`showReplyButton`).

### GoogleMapsEmbed
- **Pfad:** `src/components/GoogleMapsEmbed.tsx`
- **Verwendung:** Standardisierte Kartenanzeige für Adressen.

### Anwendung
Neue Detailseiten sollten immer `DetailLayout` verwenden, um das zweispaltige Layout (ab `xl` 3/4 Main, 1/4 Side) konsistent zu halten. E-Mail-Listen und Karten sollten niemals inline neu implementiert, sondern importiert werden.

---

## AI Development Workflow & Frontend Architecture

### Modular Frontend Strategy
- **Componentization:** Break down the UI into small, reusable React components. Avoid monolithic files.
- **Structure:**
    - `src/components/ui/`: Generic, reusable atoms (e.g., Inputs, Cards, Modals) that are style-agnostic regarding business logic.
    - `src/features/{featureName}/`: Domain-specific components, hooks, and logic (e.g., `src/features/monatsabschluss/`, `src/features/email/`).
    - `src/pages/`: page components (routes in `src/App.tsx`); `src/lib/`: shared helpers (e.g. `htmlSanitizer.ts`, `numberInput.ts`).
- **Separation of Concerns:** Keep business logic (custom hooks, services) separate from presentational code (JSX, Tailwind).

### Documentation-First Protocol (Strict)
- **Single Source of Truth:** This file (`.github/DEVELOPMENT.md`) is the master record for all design and architectural decisions.
- **The "Update Rule":** Whenever you introduce a new UI pattern or reusable component style (e.g., a standardized Input field, a Card layout, or a Badge):
    1. **YOU MUST UPDATE `.github/DEVELOPMENT.md` FIRST.** Create a new section (e.g., "Input Design Guidelines") listing the specific Tailwind classes and usage rules.
    2. **THEN** implement the code.
- **Rationale:** This ensures that future context windows know exactly which styles belong to this project without analyzing the entire codebase.
- **Related docs:** The rules for AI agents live in `docs/agent instructions/docs/` (`BACKEND_ARCH.md`, `FRONTEND_UI.md`, `TESTING_SECURITY.md`); colours, icons, wording and UI kits are in the design-system skill `.claude/skills/handwerkerprogramm-design/`. Keep them consistent with this file.

### Build & Verification Loop
- **Continuous Validation:** Do not wait until the entire task is finished. Run `npm run build` **frequently** (e.g., immediately after creating a new component or refactoring a file).
- **Fail Fast:** Ensure the application compiles without errors before moving to the next implementation step.

### Visual Consistency Check
- **Color Enforcement:** Strictly adhere to the **Rose** theme (primary `rose-600` = `#e11d48`, `rose-50` to `rose-900`) defined in the "Button Design Guidelines". `#dc2626` is `red-600` (danger/error), not the primary color.
- **No Magic Values:** Do not invent new styles. If a style is missing in `.github/DEVELOPMENT.md`, define it there first.

---

## Datei-Öffnung via OpenFileLauncher (Excel, HiCAD, TENADO)

Dateien vom Typ `.xlsm`, `.xlsx`, `.xls`, `.sza`, `.tcd` werden **nicht heruntergeladen**, sondern direkt auf dem Server geöffnet – ohne Kopie. Dafür sind zwei Dinge nötig: eine Windows-Netzwerkfreigabe auf dem Serverrechner und der OpenFileLauncher auf jedem Client-PC.

### Funktionsweise

```
Browser klickt Datei
  → Frontend ruft openfile://?path=\\SERVER\ERP-Uploads\CADdrawings\datei.xlsm auf
  → Windows leitet an launcher.ps1 weiter
  → Launcher mappt Netzlaufwerk und öffnet Datei direkt in Excel/HiCAD
  → Änderungen werden direkt auf dem Server gespeichert (keine Kopie)
```

### 1. Serverrechner einrichten (einmalig, pro Installation)

#### Ablageort in der App (Einstellungen → Reiter „Dateien“)

Ablageort und Netzwerk-Adresse stellt ein Admin heute direkt in der App ein (Einstellungen → „Dateien“):
- **Ordner auf diesem Rechner** – wohin Excel-, HiCAD- und Tenado-Dateien (`.xls`, `.xlsx`, `.xlsm`, `.sza`, `.tcd`) gespeichert werden („Ordner prüfen“ testet ihn).
- **Netzwerk-Adresse für Kollegen** – UNC-Pfad, unter dem andere Rechner denselben Ordner erreichen. Daraus baut das ERP die `openfile://`-Links.
- **„Oder: Ordner automatisch freigeben“** legt die Windows-Freigabe `ERP-Dateien` für den gespeicherten Ordner an (Windows fragt nach Administrator-Rechten).

Die Werte liegen in der Tabelle `system_setting` und wirken ohne Neustart. Solange dort nichts eingetragen ist, gelten `hicad.local-path` (Standard `uploads/CADdrawings`) und `hicad.network-url` aus den Properties.

Der klassische Weg mit Skript und `application-local.properties` (a–c) funktioniert weiterhin und wird für die `config.json` des Launchers gebraucht (Abschnitt 3).

#### a) Netzwerkfreigabe erstellen

Den `uploads/`-Ordner als Windows-Freigabe freigeben. Entweder über das mitgelieferte Skript (empfohlen) oder manuell:

**Automatisch (empfohlen):**
```
deployment\openfile-launcher\Setup-Freigabe.ps1
→ Rechtsklick → Als Administrator ausführen
```
Das Skript erstellt die Freigabe `ERP-Uploads` und aktualisiert den Launcher automatisch.

**Manuell (PowerShell als Administrator):**
```powershell
New-SmbShare -Name 'ERP-Uploads' `
             -Path 'C:\<Installationspfad>\uploads' `
             -FullAccess $env:USERNAME
```

#### b) `application-local.properties` konfigurieren

```properties
# Netzwerkpfad zur CADdrawings-Freigabe (UNC-Format, doppelte Backslashes)
hicad.network-url=\\\\<RECHNERNAME>\\ERP-Uploads\\CADdrawings
```

`<RECHNERNAME>` = Windows-Computername (`hostname` in CMD ausführen).

Ohne anderen Ordner in den Einstellungen werden Excel- und HiCAD-Dateien in `uploads/CADdrawings/` gespeichert (`hicad.local-path`). Der UNC-Pfad muss genau auf diesen Unterordner zeigen.

#### c) Backend neu starten

Nach Änderung der `application-local.properties` muss das Backend neu gestartet werden (Änderungen in Einstellungen → „Dateien“ brauchen keinen Neustart).

### 2. Client-PCs einrichten (einmalig, pro Arbeitsplatz)

Auf jedem PC, der Dateien direkt öffnen soll:

1. Den Ordner `deployment/openfile-launcher/` auf den PC kopieren (Details in der dortigen `README.md`)
2. `INSTALL.bat` (oder `OpenFileLauncher-Install.bat`) per Rechtsklick als Administrator ausführen
3. Registriert das `openfile://`-Protokoll in der Windows-Registry

Hinweis: Eine Download-Kachel dafür (`src/components/OpenFileLauncherSetup.tsx`, Endpunkt `/api/system/openfile-launcher-setup`) gibt es im Code, sie ist in den Einstellungen aber derzeit nicht eingebunden.

Kein Neustart nötig. Ab sofort öffnen sich Excel- und HiCAD-Dateien per Klick direkt im Programm.

### 3. `launcher.ps1` – erlaubte Netzwerkpfade (config.json)

Der Launcher liest die erlaubten UNC-Roots **dynamisch** aus `C:\Program Files\OpenFileLauncher\config.json`. Diese Datei wird von `Setup-Freigabe.ps1` automatisch aus `hicad.network-url` generiert — `launcher.ps1` selbst muss nie manuell bearbeitet werden.

```json
{
  "allowedRoots": ["\\\\<RECHNERNAME>\\ERP-Uploads\\CADdrawings"],
  "rootAliases": {}
}
```

**Pfad ändern:** `hicad.network-url` in `application-local.properties` anpassen, dann `Setup-Freigabe.ps1` als Admin ausführen — `config.json` wird neu generiert und der Launcher automatisch aktualisiert. Ist in Einstellungen → „Dateien“ eine Netzwerk-Adresse eingetragen, hat sie Vorrang; dort denselben Wert eintragen, sonst lehnt der Launcher die Links ab.

### Troubleshooting

| Problem | Ursache | Lösung |
|---------|---------|--------|
| Datei wird heruntergeladen statt geöffnet | Keine Netzwerk-Adresse gesetzt oder kein UNC-Pfad | Einstellungen → „Dateien“ → „Netzwerk-Adresse für Kollegen“ prüfen; ohne Eintrag dort `hicad.network-url` in `application-local.properties` (Java-Escaping: `\\\\SERVER\\Share`, danach Backend neu starten) |
| Launcher öffnet sich nicht | `openfile://`-Protokoll nicht registriert | OpenFileLauncher auf dem Client neu installieren (`INSTALL.bat` als Admin) |
| „Pfad nicht gefunden" im Launcher | `config.json` fehlt oder falsche `allowedRoots` | `Setup-Freigabe.ps1` als Admin auf dem Serverrechner ausführen, dann `config.json` auf Client-PCs verteilen |
| Freigabe nicht erreichbar | Windows-Firewall blockiert SMB | Firewall-Regel für Port 445 prüfen oder Freigabe auf dem Server kontrollieren |