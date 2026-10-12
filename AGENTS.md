# Projekt-Kontext: Open-Source ERP für Handwerksbetriebe

## 🔍 graphify: Code-Landkarte für breite Fragen (Empfehlung)

**Für breite Code-Fragen (Architektur, Abhängigkeiten, „was bricht, wenn …“) ist graphify hilfreich. Gezielte Namenssuchen gehen per Grep meist schneller.**

Das ist die zentrale Wissensbasis der Codebase (AST-Graph mit 32+ MB Daten, Symbolen, Abhängigkeiten und Komponenten):

**Aufruf:** graphify ist projektlokal im gitignorten `.graphify-venv/` des Haupt-Checkouts installiert. Nutze den versionierten Wrapper (funktioniert auch in Git-Worktrees und nutzt dort das venv des Haupt-Checkouts):
`scripts/graphify …` (Bash/Zsh auf macOS/Linux) bzw. `scripts\graphify.cmd …` (PowerShell auf Windows).

| Frage-Typ | Befehl |
| --- | --- |
| "Wo ist X?" / "Was ruft X auf?" | `scripts/graphify query "wo wird X verwendet"` |
| "Wie hängen A und B zusammen?" | `scripts/graphify path "A" "B"` |
| "Was ist Konzept Y?" | `scripts/graphify explain "Y"` |
| "Was bricht, wenn ich X ändere?" | `scripts/graphify affected "X"` |
| Breiter Architektur-Überblick / sehr breite Review | `graphify-out/GRAPH_REPORT.md` lesen (ein `graphify-out/wiki/` wird derzeit nicht erzeugt) |

**Grep/Read direkt ist meist besser, wenn:**
- du den exakten Dateipfad kennst → Datei direkt öffnen/lesen.
- du einen Klassen-, Methoden- oder Begriffsnamen suchst (die deutschen Namen im Code sind sehr treffsicher).
- du nach einem konkreten String-Literal suchst (z.B. Property-Keys, Textbausteine), das der Graph nicht als AST-Symbol führt.
- graphify die Frage bereits beantwortet hat und du nur die Zeilennummer brauchst.

**Nach Code-Änderungen:**
- `scripts/graphify update .` höchstens einmal am Ende der Aufgabe ausführen, nie nach jedem Edit: AST-only (keine API-Kosten), schreibt aber jedes Mal den ganzen Graphen (`graphify-out/graph.json`, über 40 MB) neu.
- In Git-Worktrees darf `update` laufen, `graphify-out/` dort aber **nicht committen** (große Datei, kollidiert mit parallelen Sitzungen). Vor einem Rebase `git checkout -- graphify-out`.

---

## 🎯 Mission
Das ERP ermöglicht Handwerksbetrieben den einfachen Sprung ins digitale Zeitalter. Open Source, kostenfrei, intuitive Bedienung.
**Wichtigste UX/UI-Regel:** Keine kryptischen buchhalterischen Begriffe (kein SAP-Jargon). Nutze einfache, klare, alltägliche **Handwerker-Sprache**:
- Statt "Debitorenbuchhaltung" → "Kundenrechnungen"
- Statt "Ertrags- und Aufwands-Konsolidierung" → "Einnahmen & Ausgaben"
- Statt "Fakturierungs-Vollzug" → "Rechnung ausstellen"
- Statt "Valuta-Restanz" → "Noch offen"

## 🧑‍💻 Persona & Engineering Standards
- Rolle: Erfahrener Senior Full-Stack-Entwickler (Java / Spring Boot + React / TypeScript) und UI-Designer.
- Qualität vor Hektik: sauberer, wartbarer, testbarer Code mit etablierten Design Patterns.
- **Auslagern und wiederverwenden (dauerhafte Nutzerfreigabe vom 09.09.2026):** Wiederkehrende UI und Logik in gemeinsame Komponenten, Hooks, Services oder Hilfsfunktionen auslagern und vorhandene Bausteine konsequent wiederverwenden. Keine kopierten Sonderlösungen pro Seite. Das dafür nötige Refactoring ist ausdrücklich autorisiert und benötigt keine erneute Freigabe. Abstraktionen nach gemeinsamer Verantwortung schneiden; fachliche Unterschiede erhalten.

---

## 🛑 Absolute Sicherheitsregeln (Niemals ignorieren)
1. **API-Keys & Secrets:** NIEMALS in Code oder Commits schreiben. Ausschließlich in `application-local.properties` (gitignored). Vor jedem Commit `git diff --staged` prüfen.
2. **Datenschutz (DSGVO):** Nutzer-, Mitarbeiter- und Zeitdaten sind personenbezogen. In Tests NUR Dummy-Daten verwenden (`Max Mustermann`, `test@example.com`). Logs anonymisieren.
3. **Sperrzone für Commits:** `application-local.properties`, `*.env`, `uploads/`, `*.key/pem/p12`.
4. **CodeQL & Injection-Prävention (dauerhafte Pflicht):**
   - **ReDoS:** Keine verschachtelten Wiederholungen `(\w+.*)*`. In Java possessive Quantifizierer (`*+`, `++`) oder atomare Gruppen `(?>...)` nutzen.
   - **Path Traversal:** Niemals `MultipartFile.getOriginalFilename()` ungeprüft in Pfade/Files einsetzen. Bereinigen mit `Path.of(name).getFileName().toString().replaceAll("[\\\\/:*?\"<>|]", "_")` und prüfen mit `.normalize()` und `dst.startsWith(baseDir)`. E-Mail-Anhänge in `EmailService` als In-Memory-Bytes (`ByteArrayDataSource`) übertragen.
   - **Spring CSRF:** Niemals `.csrf(csrf -> csrf.disable())` aufrufen. Wenn Endpoints ausgenommen werden müssen, immer `.csrf(csrf -> csrf.ignoringRequestMatchers("/api/..."))` verwenden.
   - **HTML Tag-Stripping:** Niemals einfaches `.replace(/<[^>]*>/g, '')` nutzen. Immer die Fixpunktschleife `stripHtmlTags()` aus `src/lib/htmlSanitizer.ts` verwenden.
   - **Entity Unescaping:** Niemals sequentiell mit mehreren `.replace()` entpacken. Immer `unescapeHtmlEntities()` aus `src/lib/htmlSanitizer.ts` (Single-Pass) verwenden.
   - **DOM-XSS:** URLs für `<iframe>` und `<img>` immer über `toSafeResourceUrl()` aus `src/lib/htmlSanitizer.ts` absichern.

---

## 📚 Entwickler-Dokumentation (Pflichtlektüre vor Edits)

Bevor du Code schreibst oder änderst, lies die entsprechende Architektur-Dokumentation:

| Bereich | Dokumentation |
| --- | --- |
| Backend (`*.java`, Config, Flyway) | `docs/agent instructions/docs/BACKEND_ARCH.md` |
| Frontend (`react-pc-frontend/`, `react-zeiterfassung/`) | `docs/agent instructions/docs/FRONTEND_UI.md` |
| Tests (`*Test.java`, `*.test.tsx`) | `docs/agent instructions/docs/TESTING_SECURITY.md` |

### Wichtigste Backend-Regeln:
- **Constructor Injection:** `@RequiredArgsConstructor` oder expliziter Konstruktor. Keine Field Injection mit `@Autowired`.
- **SQL:** Ausschließlich parametrisierte Queries (`@Query` mit `:param`), niemals String-Konkatenation.
- **Datenbank:** Nur PostgreSQL 16 (H2 nur im Speicher in den Unit-Tests). Kein MySQL/MariaDB mehr.
- **Flyway:** Eine neue Migration ist eine Datei unter `src/main/resources/db/postgresql/migration/V{N}__{beschreibung}.sql` (N = höchste vorhandene + 1, idempotent). Bestehende Migrationen NIEMALS ändern! Neue Migration in `FlywayMigrationenTest.CHECKSUMMEN` eintragen (der Test schlägt fehl und nennt die Zeile). In `db/migration/` nichts mehr ablegen (`FlywayMigrationenTest` bricht sonst den Build). Details: `src/main/resources/db/postgresql/migration/README.md`.
- **Java-Enums = `VARCHAR(n)`-Spalte ohne `CHECK` mit Werteliste:** `@Enumerated(EnumType.STRING)` liegt in PostgreSQL als `VARCHAR`; neue Enum-Werte sollen keine Migration brauchen. Werte exakt wie die Java-Konstanten (UPPERCASE).
- **DTOs:** Entities niemals direkt über Controller exponieren. Immer DTOs verwenden.

### Wichtigste Frontend-Regeln:
- **Design-System:** Handwerker-Fokus: Schlicht, modern, klar.
- **Farben:** Rose/Rot als Primär- und Akzentfarbe (`#dc2626` / `rose-600`, Palette `rose-50`–`rose-900` und `slate-50`–`slate-900`). Kein generisches Indigo oder Blau für Primärelemente!
- **Pflicht-Komponenten (nicht neu erfinden):**
  - `<Select>` → `src/components/ui/select-custom.tsx`
  - `<DatePicker>` → `src/components/ui/datepicker.tsx`
  - `<ImageViewer>` → `src/components/ui/image-viewer.tsx`
  - `<DetailLayout>` → `src/components/DetailLayout.tsx`
- **Sicherheit:** Kein `dangerouslySetInnerHTML` ohne Sanitizing.
- **Systemeigene Eingaben und Meldungen:** Vorhandene gestaltete UI-Komponenten und Toast-/Bestätigungsdialoge verwenden. Keine sichtbaren Browser-Standard-Picker für Datum/Uhrzeit, Number-Spinner, nativen Select-Popups oder `window.alert`/`window.confirm`. Auch geöffnete Auswahlen und fokussierte Felder müssen dem eigenen Design-System entsprechen.

### Zahlenfelder (dauerhafte Nutzervorgabe vom 09.09.2026)
- **Deutsch formatieren:** Dezimalzahlen mit Komma eingeben und anzeigen (z. B. `12,5`); für formatierte Anzeigen `de-DE` verwenden. DATEV-Dateien folgen zusätzlich ihrer Formatspezifikation.
- **0 bei Fokus leeren:** Eine 0 darf im unberührten Mengen-/Dezimalfeld angezeigt werden. Sobald das Feld per Klick oder Tab den Fokus erhält, einen vorhandenen Nullwert (auch `0,00`) leeren, damit direkt neu getippt werden kann. Andere Werte nicht löschen; Kennnummern sind davon ausgenommen. Eingabewerte während des Bearbeitens als Text halten. Leere Zwischenstände und ein noch unvollständiges Dezimalkomma zulassen; nicht bei jedem Tastendruck mit `Number(value) || 0` wieder eine 0 einsetzen.
- **Zahlpflicht bei Übernahme:** Pflichtfelder beim Speichern/Bestätigen auf eine vollständige, gültige Zahl und fachliche Grenzen prüfen. Leer ist nicht automatisch 0. Ungültige Eingaben mit konkreter deutscher Fehlermeldung anzeigen und nicht speichern; serverseitig ebenfalls validieren.
- **Kennnummern sind keine Mengen:** Personalnummern, Lohnarten und ähnliche Ziffernfolgen als Text behandeln, führende Nullen erhalten, nur erlaubte Ziffern/Längen akzeptieren; kein Dezimalkomma oder Tausenderformat dafür.
### End-to-End-Tests (dauerhafte Nutzervorgabe vom 10.09.2026)
- **Immer wenn End-to-End-Tests fehlen:** Für jedes neue Feature, jeden neuen Benutzer-Workflow, jede neue Seite oder signifikante Verhaltensänderung (Desktop in `react-pc-frontend/e2e/` und Mobile in `react-zeiterfassung/e2e/`) MÜSSEN vollständige Playwright End-to-End-Tests geschrieben bzw. ergänzt werden.
- Vor dem Commit immer die E2E-Tests ausführen (`npm run test:e2e`).

### 🟢 Alle Tests & Linting MÜSSEN immer grün sein (dauerhafte Nutzervorgabe vom 11.09.2026)
- **100% grüne Tests und fehlerfreies Linting:** Alle Tests (Backend: `./mvnw test`, Frontend Unit: `npm test` und E2E: `npm run test:e2e`) sowie Linter (`npm run lint`) MÜSSEN ausnahmslos fehlerfrei durchlaufen.
- Niemals fehlschlagende Tests oder Linter-Fehler überspringen, ignorieren, auskommentieren oder ungelöst lassen. Vor jedem Commit, Push oder PR muss alles vollständig grün sein.
- **Review & Ship Pflicht:** Am Ende jeder Aufgabe immer `/review-and-ship` ausführen.

### 🔒 `main` nur über Pull Requests (dauerhafte Nutzervorgabe vom 11.10.2026)
- **`main` ist Produktion:** Jeder Merge auf `main` wird nach dem Starttest als `:stable` veröffentlicht und läuft nach dem Nachtupdate (03:00) auf dem eigenen Server und bei den Kunden. Getestet wird **nur im PR** – auf `main` läuft „PR Quality Checks“ nicht noch einmal. Das ist sicher, weil das Ruleset PR-Pflicht, den Check „PR Quality Gate“ und „Branch muss vor dem Merge aktuell sein“ verlangt – und abgesichert durch den Prüfschritt in `docker-image.yml`: Ein Merge am Ruleset vorbei (ohne grünes Gate oder mit veraltetem Branch) bekommt kein Image.
- **Nie direkt auf `main` pushen** – ein Ruleset lehnt es ab. Immer: eigener Branch von `origin/main` → Pull Request (vor dem finalen 🟢 des Reviewers **als Entwurf**, `gh pr create --draft`) → bei 🟢 `gh pr ready <nr>`; mergen übernimmt die PR-Automatik. Pflicht-Check ist „PR Quality Gate“ (alle Tests aus `pr-ci.yml`).
- Kein Bypass, kein `--admin`. Scheitert ein Check nur zufällig, den Lauf wiederholen (`gh run rerun <id> --failed`), nicht umgehen.
- **PR-Automatik:** `.github/workflows/pr-automatik.yml` mergt jeden PR dieses Kontos (eigene und Agenten, nie Forks, nie Drafts), sobald „PR Quality Gate“ grün und der Branch aktuell ist, und bringt danach die übrigen PRs selbst auf den Stand von `main` (inkl. neuem Testlauf). Anhalten: Label `kein-automerge` am PR. Bei Merge-Konflikt bleibt es Handarbeit.

---

## 🚀 Build & Run (Quickstart)

- **Datenbank starten (PostgreSQL 16 im Container):** `docker run -d --name erp-dev-pg -p 127.0.0.1:5432:5432 -e POSTGRES_DB=kalkulationsprogramm_db -e POSTGRES_USER=erp_user -e POSTGRES_PASSWORD=erp_dev postgres:16` – Zugangsdaten in `application-local.properties` (`spring.datasource.username`/`password`). Eine leere DB bekommt Basis-Schema und Migrationen automatisch. Später: `docker start erp-dev-pg`.
- **Backend starten:** `./mvnw spring-boot:run` (Port 8080)
- **Backend testen:** `./mvnw test`
- **Frontend PC:**
  ```bash
  cd react-pc-frontend
  npm run dev       # Entwicklungs-Server
  npm run build     # Produktions-Build
  npm run lint      # Linter
  npm test          # Vitest Testsuite
  npm run test:e2e  # Playwright E2E-Tests
  ```
- **Frontend Zeiterfassung (Mobile):**
  ```bash
  cd react-zeiterfassung
  npm run dev
  npm run build
  npm run lint
  npm test
  npm run test:e2e  # Playwright E2E-Tests
  ```
- **Build-Ausgabe ist eingecheckt:** `npm run build` schreibt nach `src/main/resources/static/` (Zeiterfassung nach `static/zeiterfassung/`). Nach Frontend-Änderungen bauen und diese Dateien mitcommitten.

---

## 🏁 Aufgabenabschluss (Review & Ship)
Am Ende jeder Aufgabe:
1. Tests ausführen:
   - Backend: `./mvnw test`
   - Frontend Unit: `npm test` im jeweiligen Frontend-Ordner
   - Frontend E2E: `npm run test:e2e` im jeweiligen Frontend-Ordner
2. Builds prüfen:
   - `npm run build` in betroffenen Frontend-Verzeichnissen
3. Sub-Agent Code-Review:
   - Ein Sub-Agent mit leerem Kontextfenster (`erp-code-reviewer`) prüft die Änderungen und liefert eine Ampel-Bewertung (🟢/🟡/🔴).
4. Diff prüfen:
   - `git status` und `git diff` prüfen.
   - Nur Dateien stagen, die für diese Aufgabe geändert wurden (keine Fremdänderungen).
   - `git diff --staged` auf versehentlich committete Secrets oder Logs prüfen.
5. Graphify synchronisieren:
   - `scripts/graphify update .` (in Worktrees `graphify-out/` nicht mitcommitten)

