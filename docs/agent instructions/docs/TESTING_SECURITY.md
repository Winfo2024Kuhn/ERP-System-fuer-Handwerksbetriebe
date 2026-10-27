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

## Sicherheits-Pflichtcheckliste (Für JEDEN neuen Endpoint)
Bevor ein Endpoint als "fertig" markiert wird, müssen folgende Angriffsvektoren im Code/Test abgedeckt sein:
1. **SQL Injection:** `'; DROP TABLE x; --` in allen String-Parametern.
2. **XSS:** `<script>alert(1)</script>` in allen Textfeldern.
3. **Ungültige IDs:** Negative Werte, `Long.MAX_VALUE`, `0`.
4. **Limits:** Leere Pflichtfelder und überlange Eingaben (> 10.000 Zeichen) abfangen.
5. **Datei-Uploads/Downloads:** Path-Traversal verhindern (`../../etc/passwd`), gefährliche Dateitypen (`.exe`, `.bat`, `.js`) blockieren.

## CodeQL & Sicherheits-Vorgaben (dauerhafte Pflicht)
1. **Reguläre Ausdrücke (ReDoS-Prävention):** Keine verschachtelten unbegrenzten Wiederholungen wie `(\w+.*)*` oder `(.*)+`. In Java possessive Quantifizierer (`*+`, `++`) oder atomare Gruppen `(?>...)` nutzen, um Catastrophic Backtracking auszuschließen.
2. **Path Traversal & Datei-Uploads:** Dateinamen aus `MultipartFile.getOriginalFilename()` niemals ungeprüft in `Path.resolve()` oder `new File()` verwenden. Immer mit `Path.of(name).getFileName().toString().replaceAll("[\\\\/:*?\"<>|]", "_")` bereinigen und mit `.normalize()` sowie `dst.startsWith(baseDir)` absichern. Für E-Mail-Anhänge in `EmailService` In-Memory-Bytes (`byte[] data` mit `ByteArrayDataSource`) statt unnötiger temporärer Dateien auf Festplatte nutzen.
3. **Spring CSRF:** Niemals `.csrf(csrf -> csrf.disable())` aufrufen. Wenn Endpoints (z. B. S2S / JWT / Machine-to-Machine) ausgenommen werden müssen, immer `.csrf(csrf -> csrf.ignoringRequestMatchers("/api/..."))` verwenden.
4. **HTML Tag-Stripping & Multi-Character Sanitization:** Niemals ein simples `.replace(/<[^>]*>/g, '')` für Sanitization verwenden (CodeQL schlägt `incomplete-multi-character-sanitization` an). Immer die zentrale Hilfsfunktion `stripHtmlTags()` aus `react-pc-frontend/src/lib/htmlSanitizer.ts` (Fixpunktschleife) verwenden.
5. **Entity-Unescaping & Double Escaping:** HTML-Entitäten niemals sequentiell mit mehreren `.replace()` entpacken. Immer die zentrale Hilfsfunktion `unescapeHtmlEntities()` aus `react-pc-frontend/src/lib/htmlSanitizer.ts` (Single-Pass Regex) verwenden.
6. **DOM-XSS in Vorschauen (iframes & img):** URLs für `<iframe>` und `<img>` immer über `toSafeResourceUrl()` aus `react-pc-frontend/src/lib/htmlSanitizer.ts` validieren (erlaubt nur `blob:`, `http:`, `https:` und relative Pfade `/`; blockiert `javascript:`, `data:` und protokoll-relative `//…`).
7. **HTML-Anzeige & HTML-Parsen (Stored/DOM-XSS):** `dangerouslySetInnerHTML` nur mit `sanitizeHtml()` aus `react-pc-frontend/src/lib/htmlSanitizer.ts` (DOMPurify) (oder eine strengere eigene DOMPurify-Instanz mit Positivliste, Vorbild `bereinigeBeitragsHtml`; nie lockerer), auch bei serverseitig bereinigten Daten. Gleiches gilt für `element.innerHTML = …` und `insertAdjacentHTML` an angezeigten Elementen (z. B. contentEditable-Editoren). `EmailHtmlSanitizer` ist Backend-Java und zählt im Frontend nicht. HTML nur zum Auslesen über `parseHtmlInert()`/`htmlToText()` parsen, nie über `document.createElement('div').innerHTML`. Tests decken `<img src=x onerror=…>` und `<script>` ab.

**Backend (Java):** Für Punkt 4 und 5 gibt es `util/HtmlKlartext` (`entferneTags()` als Fixpunktschleife, `entpackeEntitaeten()` in einem Durchgang, `zuText()` = beides in dieser Reihenfolge). Keine eigenen `replaceAll("<[^>]*>", …)` und keine Ketten aus `.replace("&amp;", "&").replace("&lt;", "<")`, die aus `&amp;lt;` ein `<` machen.

`htmlSanitizer.ts` gibt es nur im PC-Frontend. `react-zeiterfassung` hat kein Gegenstück. Braucht die Handy-App diese Funktionen, das vor dem Bau zur Freigabe stellen (Auslagerung, siehe `.claude/CLAUDE.md`).
