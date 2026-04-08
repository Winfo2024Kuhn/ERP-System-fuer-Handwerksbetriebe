# Repository Guidelines

## Project Structure & Modules
- `src/main/java/org/example/kalkulationsprogramm/` – Application code
    - `controller/` (REST endpoints), `service/` (business logic), `repository/` (Spring Data), `domain/` (JPA entities), `dto/` (API models)
- `src/main/resources/` – Configuration (e.g., `application.yml/properties`), templates, static assets
- `src/test/java/org/example/kalkulationsprogramm/` – JUnit tests
- `uploads/` – Runtime file storage (ignored by Git)

## Build, Test, and Run
- Build JAR: `./mvnw clean package` – compiles, tests, packages
- Run locally: `./mvnw spring-boot:run` – starts the API on the configured port
- Run tests: `./mvnw test` – executes unit/integration tests
- Windows installer: `./mvnw jpackage:jpackage` – after packaging
- Requires Java 23; Maven wrapper included (no global install needed)

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
| **H2** | In-Memory-Datenbank für Repository-Tests (`@DataJpaTest`) |
| **Awaitility** | Asynchrone Assertions für zeitbasierte Logik |

#### Dateistruktur & Namenskonventionen
- **Ort:** Spiegelpakete unter `src/test/java/org/example/kalkulationsprogramm/`
- **Unit-Tests:** `{ClassName}Test.java` (z.B. `AngebotServiceTest.java`)
- **Integrationstests:** `{ClassName}IT.java` (z.B. `ProjektManagementServiceIT.java`)
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

    @MockBean
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

#### Test-Abdeckungsziele
- **Service-Klassen (Backend):** ≥ 80% Line Coverage
- **Controller (Backend):** Alle Endpoints mit Happy-Path + Fehlerfall
- **UI-Komponenten (Frontend):** Alle Props-Varianten, User-Interaktionen
- **Utility/Helper:** 100% Coverage anstreben

### Sicherheitstests (OWASP Top 10)

Jede neue Funktionalität, die User-Input entgegennimmt, MUSS mit Sicherheitstests abgedeckt werden. Die Tests basieren auf dem **OWASP Top 10** Standard.

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
Alle HTML-Ausgaben müssen sanitisiert werden (vgl. `EmailHtmlSanitizer`).

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

**Regel im Frontend (React):** Kein `dangerouslySetInnerHTML` ohne vorherige Sanitisierung. Wenn HTML-Rendering nötig (z.B. E-Mail-Vorschau), immer serverseitig sanitisierte Daten verwenden.

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

// RICHTIG: Nur sanitisiertes HTML (z.B. für E-Mail-Anzeige)
<div dangerouslySetInnerHTML={{ __html: sanitizedHtml }} />
// → sanitizedHtml kommt vom Server, dort mit EmailHtmlSanitizer bereinigt

// RICHTIG: URL-Parameter escapen
const url = `/api/search?q=${encodeURIComponent(suchbegriff)}`;

// FALSCH: Direkte String-Interpolation
const url = `/api/search?q=${suchbegriff}`;
```

## Commit & Pull Requests
- Commits: clear, imperative subject; concise body with rationale; group related changes
- PRs: include summary, linked issues, reproduction/verification steps, and screenshots for UI changes
- Expect `./mvnw test` to pass; update/add tests when changing behavior

## Security & Configuration

### Allgemeine Regeln
- **Keine Secrets im Code:** Passwörter, API-Keys, Tokens NIEMALS committen. Lokale Overrides in `application-local.properties` (in `.gitignore`).
- `uploads/` ist nicht versioniert – ephemere, nicht-sensitive Dateien.
- App-Konfiguration in `src/main/resources/`; pro Umgebung über Properties-Overrides.

### OWASP Top 10 – Pflicht-Maßnahmen

| # | Risiko | Maßnahme im Projekt |
|---|---|---|
| A01 | **Broken Access Control** | Datei-Pfade immer normalisieren, UUID-Dateinamen, kein direkter Zugriff auf `uploads/` |
| A02 | **Cryptographic Failures** | Keine Secrets im Code, HTTPS in Produktion, sichere Passwort-Hashing |
| A03 | **Injection** | Spring Data parametrisierte Queries, kein String-Concat in JPQL/SQL, HTML-Sanitisierung |
| A04 | **Insecure Design** | DTOs verwenden (nie Entities direkt exponieren), Input-Validierung an Systemgrenzen |
| A05 | **Security Misconfiguration** | CORS restriktiv konfigurieren, keine Stack-Traces in Produktion |
| A06 | **Vulnerable Components** | Dependencies regelmäßig updaten, `./mvnw versions:display-dependency-updates` |
| A07 | **Auth Failures** | (Aktuell Intranet-App) – bei externem Zugang Spring Security einsetzen |
| A08 | **Data Integrity Failures** | DTOs gegen Mass-Assignment, Jackson `FAIL_ON_UNKNOWN_PROPERTIES` |
| A09 | **Logging Failures** | Sicherheitsrelevante Aktionen loggen (Dateiupload, Löschvorgänge) |
| A10 | **SSRF** | Keine User-gesteuerten URLs für Server-Requests – wenn nötig, Allowlist verwenden |

## Frontend Implementation
- Bevorzugt React-Komponenten und -State für neue oder anzupassende UI-Funktionen, anstatt vanilla JS oder DOM-Manipulation.
- Führe immer builds aus von `npm run build`

## Button Design Guidelines

Buttons sollen im gesamten Programm einheitlich gestaltet werden, um eine konsistente Benutzererfahrung zu gewährleisten.

### Primärer Button (Hauptaktionen)
- **CSS-Klassen:** `bg-rose-600 text-white border border-rose-600 hover:bg-rose-700`
- **Verwendung:** Hauptaktionen wie "Neu", "Speichern", "Beschreibung anlegen"
- **Beispiele:**
    - Leistungseditor → "+ Neu" Button für neue Leistung (`src/pages/Leistungseditor.tsx`)
    - Leistungseditor → "+ Beschreibung anlegen" Button (`src/pages/Leistungseditor.tsx`)
    - Textvorlagen-Editor → "Speichern" Button (`src/App.tsx`)

### Sekundärer Button (Outline)
- **CSS-Klassen:** `variant="outline"` oder `border-rose-300 text-rose-700 hover:bg-rose-50`
- **Verwendung:** Abbrechen, Zurück, sekundäre Aktionen
- **Beispiele:**
    - "Abbrechen" Button in allen Formularen
    - "Vorlage laden" Button im Formularwesen

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
Das gesamte Programm verwendet das **rose/rot** Farbschema:
- farbe des firmenlogos #dc2626
- `rose-50` bis `rose-900` für Primärfarben
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
- `options`: Array von `{ value: string; label: string; }` Objekten
- `placeholder`: Optionaler Platzhaltertext
- `className`: Optionale zusätzliche CSS-Klassen

### DatePicker Design

Für alle Datumsfelder im Projekt MUSS die projekt-eigene React-Komponente `DatePicker` aus `src/components/ui/datepicker.tsx` verwendet werden.

- **Import:** `import { DatePicker } from '../components/ui/datepicker';` (Pfad je nach Dateiort anpassen)
- **Verwendung:** Anstelle von nativem HTML `<input type="date">`.
- **Features:** Kalender-Popup, Monats-/Jahresnavigation, "Heute"/"Löschen"-Schnellaktionen, deutsche Lokalisierung.

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

**Besondere Features:**
- `<<` / `>>` Buttons für schnelle Jahresnavigation
- `<` / `>` Buttons für Monatsnavigation
- Klick auf Monats-/Jahres-Titel springt zum aktuellen Monat
- "Heute"-Button setzt das aktuelle Datum
- "Löschen"-Button entfernt das Datum

## Page Header Design Guidelines

Alle React-Seiten müssen das gleiche, standardisierte Header-Pattern verwenden, um eine konsistente Benutzererfahrung zu gewährleisten.

### Header-Struktur
Jede Seite muss folgende Elemente im Header aufweisen:
1. **Kategorie-Label** – Kleine, uppercase Kategorie-Bezeichnung
2. **Seitentitel** – Großes, fettes, uppercase Titel
3. **Beschreibung** – Subtile Beschreibung der Seite
4. **Action Buttons** – Rechts ausgerichtet (optional)

### CSS-Klassen

```tsx
{/* Standard Page Header */}
<div className="flex flex-col md:flex-row justify-between gap-4 md:items-end mb-8">
    <div>
        <p className="text-sm font-semibold text-rose-600 uppercase tracking-wide">
            {/* Kategorie-Label, z.B. "Stammdaten", "Buchhaltung", "Controlling" */}
        </p>
        <h1 className="text-3xl font-bold text-slate-900">
            {/* SEITENTITEL IN UPPERCASE */}
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
| FormularwesenEditor  | Vorlagen-Center     |
| BestellungEditor     | Einkauf             |
| Leistungseditor      | Textverwaltung      |
| ArbeitsgangEditor    | Arbeitsplanung      |
| AngebotEditor        | Angebotsmanagement  |
| ProduktkategorieEditor | Artikelverwaltung  |
| EmailCenter          | Kommunikation      |

### Anwendung
- Neue Seiten MÜSSEN dieses Header-Pattern verwenden
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


## React Templates & DRY Principles

Um das **DRY (Don't Repeat Yourself)** Prinzip zu wahren und ein konsistentes UI zu gewährleisten, sind folgende Templates und Komponenten zu nutzen:

### DetailLayout
- **Pfad:** `src/components/DetailLayout.tsx`
- **Verwendung:** Für alle Detailansichten (Kunde, Lieferant, etc.).
- **Props:**
    - `header`: Bento-Stats Header und Navigations-Buttons.
    - `mainContent`: Hauptinhalt links (z.B. `EmailHistory`).
    - `sideContent`: Seitenleiste rechts (z.B. Kontaktdaten, Map).

### EmailHistory
- **Pfad:** `src/components/EmailHistory.tsx`
- **Verwendung:** Einheitliche Darstellung von E-Mail-Verläufen in Detailansichten.
- **Features:** Suchfunktion, Expandable Items, Anhang-Anzeige, HTML-Vorschau.

### GoogleMapsEmbed
- **Pfad:** `src/components/GoogleMapsEmbed.tsx`
- **Verwendung:** Standardisierte Kartenanzeige für Adressen.

### Anwendung
Neue Detailseiten sollten immer `DetailLayout` verwenden, um das zweispaltige Layout (2/3 Main, 1/3 Side) konsistent zu halten. E-Mail-Listen und Karten sollten niemals inline neu implementiert, sondern importiert werden.

---

## AI Development Workflow & Frontend Architecture

### Modular Frontend Strategy
- **Componentization:** Break down the UI into small, reusable React components. Avoid monolithic files.
- **Structure:**
    - `src/components/ui/`: Generic, reusable atoms (e.g., Inputs, Cards, Modals) that are style-agnostic regarding business logic.
    - `src/features/{featureName}/`: Domain-specific components, hooks, and logic (e.g., `src/features/calculation/`).
- **Separation of Concerns:** Keep business logic (custom hooks, services) separate from presentational code (JSX, Tailwind).

### Documentation-First Protocol (Strict)
- **Single Source of Truth:** This file (`.github/DEVELOPMENT.md`) is the master record for all design and architectural decisions.
- **The "Update Rule":** Whenever you introduce a new UI pattern or reusable component style (e.g., a standardized Input field, a Card layout, or a Badge):
    1. **YOU MUST UPDATE `.github/DEVELOPMENT.md` FIRST.** Create a new section (e.g., "Input Design Guidelines") listing the specific Tailwind classes and usage rules.
    2. **THEN** implement the code.
- **Rationale:** This ensures that future context windows know exactly which styles belong to this project without analyzing the entire codebase.

### Build & Verification Loop
- **Continuous Validation:** Do not wait until the entire task is finished. Run `npm run build` **frequently** (e.g., immediately after creating a new component or refactoring a file).
- **Fail Fast:** Ensure the application compiles without errors before moving to the next implementation step.

### Visual Consistency Check
- **Color Enforcement:** Strictly adhere to the **Rose/Red** (`#dc2626`, `rose-50` to `rose-900`) theme defined in the "Button Design Guidelines".
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

Excel- und HiCAD-Dateien werden automatisch in `uploads/CADdrawings/` gespeichert. Der UNC-Pfad muss genau auf diesen Unterordner zeigen.

#### c) Backend neu starten

Nach Änderung der `application-local.properties` muss das Backend neu gestartet werden.

### 2. Client-PCs einrichten (einmalig, pro Arbeitsplatz)

Auf jedem PC, der Dateien direkt öffnen soll:

1. OpenFileLauncher-Setup herunterladen (in der App unter Einstellungen → OpenFile Launcher)
2. `OpenFileLauncher-Install.bat` als Administrator ausführen
3. Registriert das `openfile://`-Protokoll in der Windows-Registry

Kein Neustart nötig. Ab sofort öffnen sich Excel- und HiCAD-Dateien per Klick direkt im Programm.

### 3. `launcher.ps1` – erlaubte Netzwerkpfade (config.json)

Der Launcher liest die erlaubten UNC-Roots **dynamisch** aus `C:\Program Files\OpenFileLauncher\config.json`. Diese Datei wird von `Setup-Freigabe.ps1` automatisch aus `hicad.network-url` generiert — `launcher.ps1` selbst muss nie manuell bearbeitet werden.

```json
{
  "allowedRoots": ["\\\\<RECHNERNAME>\\ERP-Uploads\\CADdrawings"],
  "rootAliases": {}
}
```

**Pfad ändern:** Nur `hicad.network-url` in `application-local.properties` anpassen, dann `Setup-Freigabe.ps1` als Admin ausführen — `config.json` wird neu generiert und der Launcher automatisch aktualisiert.

### Troubleshooting

| Problem | Ursache | Lösung |
|---------|---------|--------|
| Datei wird heruntergeladen statt geöffnet | `hicad.network-url` nicht gesetzt oder kein UNC-Pfad | `application-local.properties` prüfen (Java-Escaping: `\\\\SERVER\\Share`), Backend neu starten |
| Launcher öffnet sich nicht | `openfile://`-Protokoll nicht registriert | OpenFileLauncher auf dem Client neu installieren (`INSTALL.bat` als Admin) |
| „Pfad nicht gefunden" im Launcher | `config.json` fehlt oder falsche `allowedRoots` | `Setup-Freigabe.ps1` als Admin auf dem Serverrechner ausführen, dann `config.json` auf Client-PCs verteilen |
| Freigabe nicht erreichbar | Windows-Firewall blockiert SMB | Firewall-Regel für Port 445 prüfen oder Freigabe auf dem Server kontrollieren |