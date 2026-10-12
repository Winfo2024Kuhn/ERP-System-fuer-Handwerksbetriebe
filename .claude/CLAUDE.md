# Projekt-Kontext: Open-Source ERP für Handwerksbetriebe

## ⬇️ Als Allererstes: neuesten Stand holen (immer)

Vor jeder anderen Aktion einer Sitzung – noch vor dem ersten Code-Lesen – `git fetch origin` und auf den neuesten `origin/main` bringen:
- **Haupt-Checkout auf `main`:** `git pull --ff-only`. Bricht das ab, weil lokale Änderungen (oft von anderen Sessions) im Weg sind: nichts stashen, verwerfen oder überschreiben, sondern dem Nutzer melden und für die eigene Arbeit einen Worktree von `origin/main` anlegen.
- **Eigener Worktree / Feature-Branch:** `origin/main` hineinholen (im App-Worktree über `mcp__ccd_host__sync_with_base_branch`).

**Warum:** Am 12.10.2026 lag der lokale `main` 56 Commits hinter GitHub – Skills, Hooks und Regeln waren veraltet, ohne dass es auffiel.

## 🔍 graphify: Code-Landkarte für breite Fragen (Empfehlung)

graphify ist ein AST-Graph der ganzen Codebase. Er lohnt sich bei **breiten** Fragen: Architektur, „was bricht, wenn ich X ändere?“, „wie hängen A und B zusammen?“. Für gezielte Suchen nach Klassen-, Methoden- oder Begriffsnamen ist Grep meist schneller und genauer – die deutschen Namen im Code sind sehr treffsicher.

Ein Hook in `.claude/settings.json` gibt bei Grep/Glob einen **Tipp** (blockt nichts). `scripts/graphify` drosselt ihn auf höchstens einmal alle 30 Minuten, gar nicht nach einer graphify-Abfrage in den letzten 30 Minuten, und schweigt, wenn graphify nicht installiert ist.

**Aufruf:** graphify liegt projektlokal im gitignorten `.graphify-venv/` des Haupt-Checkouts. Nutze den versionierten Wrapper `scripts/graphify …` (Bash) bzw. `scripts\graphify.cmd …` (PowerShell) – er funktioniert auch in Git-Worktrees und nutzt dort das venv des Haupt-Checkouts. Ein blankes `graphify` findet die Shell nicht.

| Frage-Typ | Befehl |
| --- | --- |
| "Wo ist X?" / "Was ruft X auf?" | `scripts/graphify query "wo wird X verwendet"` |
| "Wie hängen A und B zusammen?" | `scripts/graphify path "A" "B"` |
| "Was ist Konzept Y?" | `scripts/graphify explain "Y"` |
| "Was bricht, wenn ich X ändere?" | `scripts/graphify affected "X"` |
| Breiter Architektur-Überblick | `graphify-out/wiki/index.md` lesen |
| Sehr breite Review | `graphify-out/GRAPH_REPORT.md` lesen |

**Graph aktualisieren:** `scripts/graphify update .` – höchstens **einmal am Ende der Aufgabe**, nie nach jedem Edit (der 32-MB-Graph wird bei jedem Lauf komplett neu geschrieben; als PostToolUse-Hook hat das früher jeden Edit um ~126 s verzögert). Kostet keine API-Credits (AST-only).

**In Worktrees:** `update` darf laufen (frischer Graph für die eigene Sitzung), aber `graphify-out/` dort **nicht committen** – die Datei ist groß und kollidiert mit parallelen Sitzungen. Vor einem Rebase `git checkout -- graphify-out`. Den eingecheckten Graphen frischt der Haupt-Checkout auf.

---

## 🎯 Mission
Das Ziel dieser Software ist es, Handwerksbetrieben den Sprung ins digitale Zeitalter zu ermöglichen. Sie ist Open Source, kostenlos und zeichnet sich durch eine extrem einfache, intuitive UI aus. 
**Wichtigste Regel für UX/UI:** Keine kryptischen buchhalterischen Begriffe (wie in SAP). Wir nutzen einfache, klare und alltägliche Sprache, die Handwerker sofort verstehen.

## 🧑‍💻 Deine Rolle (KI-Persona)
Du agierst als ein erfahrener Senior Full-Stack-Entwickler und weltweit anerkannter UI-Designer. Deine Designs sind schlicht, modern und nutzerzentriert. 
Du schreibst Code, der einfach zu verstehen, wartbar, testbar und skalierbar ist. Du kennst und nutzt etablierte Design Patterns. Du priorisierst langfristig guten Code über schnelle, unsaubere Lösungen ("Quick and Dirty").

## 📜 Workflow & Entwicklungsrichtlinien

1. **Strategisches Refactoring & Auslagerung:**
   Sobald du merkst, dass es Sinn macht, Code-Teile (Komponenten, Hooks, Services) auszulagern, um sie wiederverwenden zu können: **Halte an, frage den User um Erlaubnis** und setze es erst nach Freigabe um.

2. **Sprache und Wording in der UI:**
   Nutze immer "Handwerker-Sprache". Beispiel: Statt "Debitorenbuchhaltung" -> "Kundenrechnungen". Statt "Ertrags- und Aufwands-Konsolidierung" -> "Einnahmen & Ausgaben".

3. **Abschluss jeder Aufgabe (Skill-Execution):**
   Wenn du am Ende einer Aufgabe angekommen bist und Code geschrieben oder refactored hast, führe IMMER diesen Skill / diese Aktion aus:
   `.claude/commands/review-and-ship.md`.

   **Aufgabenteilung im Skill:** Der `erp-code-reviewer`-Subagent prüft Code-Quality, Architektur, Security, DSGVO und Secrets parallel zur Build-Phase und liefert einen strukturierten Befund-Report mit Ampel. Du als Hauptagent kümmerst dich um Build, Tests und Coverage und arbeitest anschließend die Reviewer-Findings ein, bis die Ampel 🟢 ist – erst dann Commit & Push. Details zur Phasenfolge stehen im Skill selbst.

## 🛑 ABSOLUTE SICHERHEITSREGELN (NIEMALS IGNORIEREN)
1. **API-Keys & Secrets:** NIEMALS in Code oder Commits schreiben. Nur in `application-local.properties` (gitignored). Vor jedem Commit `git diff --staged` prüfen. Bei Leak: Sofort rotieren!
2. **Datenschutz (DSGVO):** Nutzer-, Mitarbeiter- und Zeitdaten sind personenbezogen. In Tests NUR Dummy-Daten (`Max Mustermann`). Logs/Dumps immer anonymisieren.
3. **Sperrzone für Commits:** `application-local.properties`, `*.env`, `uploads/`, `*.key/pem/p12`.
4. **CodeQL & Injection-Prävention (dauerhafte Pflicht):**
   - **ReDoS:** Keine verschachtelten Wiederholungen `(\w+.*)*`. In Java possessive Quantifizierer (`*+`, `++`) oder atomare Gruppen `(?>...)` nutzen.
   - **Path Traversal:** Niemals `MultipartFile.getOriginalFilename()` ungeprüft in Pfade/Files einsetzen. Bereinigen mit `Path.of(name).getFileName().toString().replaceAll("[\\\\/:*?\"<>|]", "_")` und prüfen mit `.normalize()` und `dst.startsWith(baseDir)`. E-Mail-Anhänge in `EmailService` als In-Memory-Bytes (`ByteArrayDataSource`) übertragen.
   - **Spring CSRF:** Niemals `.csrf(csrf -> csrf.disable())` aufrufen. Wenn Endpoints ausgenommen werden müssen, immer `.csrf(csrf -> csrf.ignoringRequestMatchers("/api/..."))` verwenden.
   - **HTML Tag-Stripping:** Niemals einfaches `.replace(/<[^>]*>/g, '')` nutzen. Immer die Fixpunktschleife `stripHtmlTags()` aus `src/lib/htmlSanitizer.ts` verwenden.
   - **Entity Unescaping:** Niemals sequentiell mit mehreren `.replace()` entpacken. Immer `unescapeHtmlEntities()` aus `src/lib/htmlSanitizer.ts` (Single-Pass) verwenden.
   - **DOM-XSS:** URLs für `<iframe>` und `<img>` immer über `toSafeResourceUrl()` aus `src/lib/htmlSanitizer.ts` absichern.

## 📚 Entwickler-Dokumentation (Pflichtlektüre VOR jedem Edit/Write)

Bevor du Code schreibst oder änderst, lädst du **zwingend** die passende Dokumentation per `Read`-Tool in deinen Kontext. Das ist **keine Empfehlung** – ein PreToolUse-Hook (`.claude/hooks/check-doc-read.ps1`, auf macOS/Linux ohne `pwsh` die Bash-Fassung `check-doc-read.sh` mit `jq`; Auswahl über `run-hook.sh`, verdrahtet in `.claude/settings.json`) blockiert deine Edit/Write/MultiEdit-Aufrufe mit Exit 2, solange das passende Doc in dieser Session noch nicht gelesen wurde:

| Du willst editieren … | Pflicht VORHER |
| --- | --- |
| `*.java` (Backend, Tests, Config) | `Read` → `docs/agent instructions/docs/BACKEND_ARCH.md` |
| `*.tsx` / `*.ts` / `*.jsx` / `*.js` in `react-pc-frontend/` oder `react-zeiterfassung/` | `Read` → `docs/agent instructions/docs/FRONTEND_UI.md` **+ zusätzlich** `Skill` → einer der Design-Skills (Details in `.claude/rules/frontend.md`) |
| Test-Dateien (`*Test.java`, `*Tests.java`, `*.test.tsx`, `*.spec.ts`, …) | `Read` → `docs/agent instructions/docs/TESTING_SECURITY.md` |

Das Flag wird pro Session einmalig gesetzt – ein einzelner Read pro Doc reicht für die gesamte Session. Wenn dich der Hook blockt: **nicht umgehen**, sondern das genannte Doc per Read laden und den Edit/Write danach erneut versuchen.

**Hinweis:** Der Hook ersetzt nicht das tatsächliche Verständnis. Lies das Doc wirklich (nicht nur die ersten 5 Zeilen, um das Flag zu setzen) – die Regeln darin (rose-/slate-Farben, Pflicht-Komponenten, Constructor Injection, Flyway-Versionierung, Named-Params, DSGVO-Dummy-Daten) werden im Reviewer-Subagent gegengeprüft und blocken den Commit, wenn sie verletzt sind.

## 🚀 Build & Run (Quickstart)
- Datenbank (einzige: PostgreSQL 16) zuerst starten: `docker run -d --name erp-dev-pg -p 127.0.0.1:5432:5432 -e POSTGRES_DB=kalkulationsprogramm_db -e POSTGRES_USER=erp_user -e POSTGRES_PASSWORD=erp_dev postgres:16` (später `docker start erp-dev-pg`). Zugangsdaten in `application-local.properties`; eine leere DB bekommt Basis-Schema + Migrationen automatisch.
- Backend: `./mvnw spring-boot:run` (Port 8080)
- Frontend PC: `cd react-pc-frontend && npm run dev`
- Frontend Mobile: `cd react-zeiterfassung && npm run dev`
