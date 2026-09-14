# 🧪 Testing & Endpoint-Security

## Test-Anforderungen
- **Service-Schicht:** JUnit 5 + Mockito (`@ExtendWith(MockitoExtension.class)`). Ziel: ≥ 80% Coverage.
- **Controller-Schicht:** MockMvc (`@WebMvcTest`). Ziel: Alle Endpoints (Happy-Path + Fehlerfall).
- **Repository-Schicht:** H2 In-Memory (`@DataJpaTest`).
- **Frontend Unit/Komponenten-Tests:** Vitest + Testing Library (neben Quell-Datei als `*.test.tsx`).
- **End-to-End-Tests (Playwright - Pflicht whenever missing):** Immer wenn End-to-End-Tests fehlen, müssen für neue Features, Abläufe, Seiten und Dialoge Playwright-E2E-Tests geschrieben bzw. ergänzt werden (`react-pc-frontend/e2e/` und `react-zeiterfassung/e2e/`).
- **Daten:** Immer Dummy-Daten nutzen (DSGVO!). Utils erfordern 100% Coverage.

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
6. **DOM-XSS in Vorschauen (iframes & img):** URLs für `<iframe>` und `<img>` immer über `toSafeResourceUrl()` aus `react-pc-frontend/src/lib/htmlSanitizer.ts` validieren (erlaubt nur `blob:`, `http:`, `https:` und relative Pfade `/`; blockiert `javascript:` und `data:`).