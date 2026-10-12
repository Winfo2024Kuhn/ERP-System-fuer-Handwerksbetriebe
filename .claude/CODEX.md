# Projekt-Kontext: Open-Source ERP für Handwerksbetriebe

## 🔍 graphify: Code-Landkarte für breite Fragen (Empfehlung)

**Für breite Code-Fragen (Architektur, Abhängigkeiten, „was bricht, wenn …“) ist graphify hilfreich. Gezielte Namenssuchen gehen per Grep meist schneller.**

Ein Hook in `.claude/settings.json` gibt bei Grep/Glob einen Tipp (blockt nichts), höchstens alle 30 Minuten, und ist still, wenn graphify nicht installiert ist.

**Aufruf:** graphify ist bewusst **nicht** systemweit installiert, sondern projektlokal in `.graphify-venv/`. Nutze den versionierten Wrapper (funktioniert auch in Git-Worktrees) — `scripts/graphify …` (Bash) bzw. `scripts\graphify.cmd …` (PowerShell). Ein blankes `graphify` findet die Shell nicht.

| Frage-Typ | Befehl |
| --- | --- |
| "Wo ist X?" / "Was ruft X auf?" | `scripts/graphify query "wo wird X verwendet"` |
| "Wie hängen A und B zusammen?" | `scripts/graphify path "A" "B"` |
| "Was ist Konzept Y?" | `scripts/graphify explain "Y"` |
| "Was bricht, wenn ich X ändere?" | `scripts/graphify affected "X"` |
| Breiter Architektur-Überblick / sehr breite Review | `graphify-out/GRAPH_REPORT.md` lesen |

Hängt graphify selbst einen `## graphify`-Block an (blankes `graphify`, Update nach jeder Änderung, `wiki/index.md`), gilt er nicht – wieder entfernen.

**Ausnahmen** (Grep/Glob direkt erlaubt):

- Du kennst den exakten Dateipfad → `Read` direkt.
- Du suchst nach einem konkreten String-Literal, das der Graph nicht als Symbol führt (z.B. Property-Keys, Werte innerhalb von Migrationen). Die Flyway-Migrationen selbst sind im Graphen — Tabellen und Funktionen findest du per `query`.
- graphify hat die Frage schon beantwortet und du brauchst nur das letzte Detail.

**Nach Code-Änderungen:** `scripts/graphify update .` — **einmal am Ende der Aufgabe**, nicht nach jedem einzelnen Edit (AST-only, gratis; der 32-MB-Graph wird bei jedem Lauf komplett neu geschrieben).

---

## 🎯 Mission
Das Ziel dieser Software ist es, Handwerksbetrieben den Sprung ins digitale Zeitalter zu ermöglichen. Sie ist Open Source, kostenlos und zeichnet sich durch eine extrem einfache, intuitive UI aus. 
**Wichtigste Regel für UX/UI:** Keine kryptischen buchhalterischen Begriffe (wie in SAP). Wir nutzen einfache, klare und alltägliche Sprache, die Handwerker sofort verstehen.

## 🧑‍💻 Deine Rolle (KI-Persona)
Du agierst als ein erfahrener Senior Full-Stack-Entwickler und weltweit anerkannter UI-Designer. Deine Designs sind schlicht, modern und nutzerzentriert. 
Du schreibst Code, der einfach zu verstehen, wartbar, testbar und skalierbar ist. Du kennst und nutzt etablierte Design Patterns. Du priorisierst langfristig guten Code über schnelle, unsaubere Lösungen ("Quick and Dirty").

## 📜 Workflow & Entwicklungsrichtlinien

1. **Auslagern und wiederverwenden (dauerhafte Nutzerfreigabe vom 09.09.2026, bestätigt am 12.10.2026):**
   Wiederkehrende UI und Logik in gemeinsame Komponenten, Hooks, Services oder Hilfsfunktionen auslagern und vorhandene Bausteine konsequent wiederverwenden. Keine kopierten Sonderlösungen pro Seite. Das dafür nötige Refactoring ist ausdrücklich autorisiert und benötigt keine erneute Freigabe. Abstraktionen nach gemeinsamer Verantwortung schneiden; fachliche Unterschiede erhalten.

2. **Sprache und Wording in der UI:**
   Nutze immer "Handwerker-Sprache". Beispiel: Statt "Debitorenbuchhaltung" -> "Kundenrechnungen". Statt "Ertrags- und Aufwands-Konsolidierung" -> "Einnahmen & Ausgaben".

3. **Abschluss jeder Aufgabe (Skill-Execution):**
   Wenn du am Ende einer Aufgabe angekommen bist und Code geschrieben oder refactored hast, führe IMMER diesen Skill / diese Aktion aus:
   `.claude/commands/review-and-ship.md`.

   **Aufgabenteilung im Skill:** Der `erp-code-reviewer`-Subagent prüft Code-Quality, Architektur, Security, DSGVO und Secrets parallel zur Build-Phase und liefert einen strukturierten Befund-Report mit Ampel. Du als Hauptagent kümmerst dich um Build, Tests und Coverage und arbeitest anschließend die Reviewer-Findings ein, bis die Ampel 🟢 ist – erst dann Commit & Push. Details zur Phasenfolge stehen im Skill selbst.

## 🛑 ABSOLUTE SICHERHEITSREGELN (NIEMALS IGNORIEREN)
1. **API-Keys & Secrets:** NIEMALS in Code, Logs oder Commits schreiben. Vor jedem Commit `git diff --staged` prüfen. Bei Leak: Sofort rotieren! Wo sie liegen:
   - **Grundregel (alle übrigen und neuen Secrets):** nur in `application-local.properties` (gitignored) bzw. im Docker-Betrieb in `app.env` (Umgebungsvariablen). Muss ein Secret in die DB, dann nur verschlüsselt über `MailSecretService` – wie das FRITZ!Box-Passwort. Keine weiteren Klartext-Secrets in `system_setting`.
   - **Gemini-Key, SMTP/IMAP-Zugänge:** DB-Tabelle `system_setting` (im Klartext), gepflegt unter System-Einstellungen → KI-Funktionen bzw. → E-Mail; Postfach-Passwörter zusätzlich verschlüsselt in `email_absender`. Die Properties `ai.gemini.api-key`, `smtp.*`, `imap.*` sind nur Rückfall (`application-local.properties` bzw. `app.env`).
   - **FRITZ!Box-Passwort:** `system_setting`, verschlüsselt mit `mail.credentials.encryption-key`.
   - DB-Dumps enthalten deshalb Klartext-Zugänge: nie committen oder weitergeben.
2. **Datenschutz (DSGVO):** Nutzer-, Mitarbeiter- und Zeitdaten sind personenbezogen. In Tests NUR Dummy-Daten (`Max Mustermann`). Logs/Dumps immer anonymisieren.
3. **Sperrzone für Commits:** `application-local.properties`, `*.env`, `uploads/`, `*.key/pem/p12`, DB-Dumps.
4. **CodeQL & Injection-Prävention (dauerhafte Pflicht):**
   - **ReDoS:** Keine verschachtelten Wiederholungen `(\w+.*)*`. In Java possessive Quantifizierer (`*+`, `++`) oder atomare Gruppen `(?>...)` nutzen.
   - **Path Traversal:** Niemals `MultipartFile.getOriginalFilename()` ungeprüft in Pfade/Files einsetzen. Bereinigen mit `Path.of(name).getFileName().toString().replaceAll("[\\\\/:*?\"<>|]", "_")` und prüfen mit `.normalize()` und `dst.startsWith(baseDir)`. E-Mail-Anhänge in `EmailService` als In-Memory-Bytes (`ByteArrayDataSource`) übertragen.
   - **Spring CSRF:** Niemals `.csrf(csrf -> csrf.disable())` aufrufen. Wenn Endpoints ausgenommen werden müssen, immer `.csrf(csrf -> csrf.ignoringRequestMatchers("/api/..."))` verwenden.
   - **HTML Tag-Stripping:** Niemals einfaches `.replace(/<[^>]*>/g, '')` nutzen. Immer die Fixpunktschleife `stripHtmlTags()` aus `react-pc-frontend/src/lib/htmlSanitizer.ts` verwenden.
   - **Entity Unescaping:** Niemals sequentiell mit mehreren `.replace()` entpacken. Immer `unescapeHtmlEntities()` aus `react-pc-frontend/src/lib/htmlSanitizer.ts` (Single-Pass) verwenden.
   - **DOM-XSS:** URLs für `<iframe>` und `<img>` immer über `toSafeResourceUrl()` aus `react-pc-frontend/src/lib/htmlSanitizer.ts` absichern.

## 📚 Entwickler-Dokumentation (Pflichtlektüre VOR jedem Edit/Write)

Bevor du Code schreibst oder änderst, lädst du **zwingend** die passende Dokumentation per `Read`-Tool in deinen Kontext. Das ist **keine Empfehlung** – ein PreToolUse-Hook (`.claude/hooks/check-doc-read.ps1`) blockiert deine Edit/Write/MultiEdit-Aufrufe mit Exit 2, solange das passende Doc in dieser Session noch nicht gelesen wurde:

| Du willst editieren … | Pflicht-Read VORHER |
| --- | --- |
| `*.java` (Backend, Tests, Config) | `docs\agent instructions\docs\BACKEND_ARCH.md` |
| `*.tsx` / `*.ts` / `*.jsx` / `*.js` in `react-pc-frontend/` oder `react-zeiterfassung/` | `docs\agent instructions\docs\FRONTEND_UI.md` **+ zusätzlich** `.claude\skills\handwerkerprogramm-design\README.md` (unser Design-System: Farben, Typografie, Icons, Wording, UI-Kits) |
| Test-Dateien (`*Test.java`, `*Tests.java`, `*.test.tsx`, `*.spec.ts`, …) | `docs\agent instructions\docs\TESTING_SECURITY.md` |

Das Flag wird pro Session einmalig gesetzt – ein einzelner Read pro Doc reicht für die gesamte Session. Wenn dich der Hook blockt: **nicht umgehen**, sondern das genannte Doc per Read laden und den Edit/Write danach erneut versuchen.

**Hinweis:** Der Hook ersetzt nicht das tatsächliche Verständnis. Lies das Doc wirklich (nicht nur die ersten 5 Zeilen, um das Flag zu setzen) – die Regeln darin (rose-/slate-Farben, Pflicht-Komponenten, Constructor Injection, Flyway-Versionierung, Named-Params, DSGVO-Dummy-Daten) werden im Reviewer-Subagent gegengeprüft und blocken den Commit, wenn sie verletzt sind.

## 🚀 Build & Run (Quickstart)
- Backend: `./mvnw spring-boot:run` (Port 8080)
- Frontend PC: `cd react-pc-frontend && npm run dev`
- Frontend Mobile: `cd react-zeiterfassung && npm run dev`
