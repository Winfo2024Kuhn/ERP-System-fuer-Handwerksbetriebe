# Projekt-Kontext: Open-Source ERP für Handwerksbetriebe

## 🔍 OBERSTE REGEL: GRAPHIFY VOR JEDER SUCHE

**Bevor du Grep/Glob/find/ls für Codebase-Fragen nutzt, rufe ZUERST graphify auf.**

Das ist die zentrale Wissensbasis der Codebase (AST-Graph mit 32+ MB Daten, Symbolen, Abhängigkeiten und Komponenten):

**Aufruf:** graphify ist projektlokal in `.graphify-venv/` installiert. Nutze den Wrapper im Projektroot:
`./graphify …` (Bash/Zsh auf macOS/Linux) bzw. `.\graphify.cmd …` (PowerShell auf Windows).

| Frage-Typ | Befehl ZUERST |
| --- | --- |
| "Wo ist X?" / "Was ruft X auf?" | `./graphify query "wo wird X verwendet"` |
| "Wie hängen A und B zusammen?" | `./graphify path "A" "B"` |
| "Was ist Konzept Y?" | `./graphify explain "Y"` |
| "Was bricht, wenn ich X ändere?" | `./graphify affected "X"` |
| Breiter Architektur-Überblick | `graphify-out/wiki/index.md` lesen |
| Sehr breite Review | `graphify-out/GRAPH_REPORT.md` lesen |

**Ausnahmen (Grep/Read direkt erlaubt):**
- Du kennst den exakten Dateipfad → Datei direkt öffnen/lesen.
- Du suchst nach einem konkreten String-Literal (z.B. Property-Keys, Textbausteine), das der Graph nicht als AST-Symbol führt.
- graphify hat die Frage bereits beantwortet und du benötigst nur die Zeilennummer.

**Nach Code-Änderungen:**
- `./graphify update .` einmalig am Ende der Aufgabe ausführen (AST-only, schnell, hält den Graphen synchron).

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
- **Strategisches Refactoring:** Wenn du Code-Teile (Komponenten, Hooks, Services) auslagern möchtest: **Frage den Nutzer vorher um Erlaubnis** und setze es erst nach Freigabe um.

---

## 🛑 Absolute Sicherheitsregeln (Niemals ignorieren)
1. **API-Keys & Secrets:** NIEMALS in Code oder Commits schreiben. Ausschließlich in `application-local.properties` (gitignored). Vor jedem Commit `git diff --staged` prüfen.
2. **Datenschutz (DSGVO):** Nutzer-, Mitarbeiter- und Zeitdaten sind personenbezogen. In Tests NUR Dummy-Daten verwenden (`Max Mustermann`, `test@example.com`). Logs anonymisieren.
3. **Sperrzone für Commits:** `application-local.properties`, `*.env`, `uploads/`, `*.key/pem/p12`.

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
- **Flyway:** Neue Skripte unter `src/main/resources/db/migration/V{N}__{beschreibung}.sql`. Bestehende Migrationen NIEMALS ändern!
- **Java-Enums in MySQL = native `ENUM(...)`-Spalte:** Hibernate 6.x mappt `@Enumerated(EnumType.STRING)` auf native MySQL ENUMs. In Migrationen immer `ENUM('WERT1','WERT2')` (UPPERCASE) definieren, nicht `VARCHAR`.
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

---

## 🚀 Build & Run (Quickstart)

- **Backend starten:** `./mvnw spring-boot:run` (Port 8080)
- **Backend testen:** `./mvnw test`
- **Frontend PC:**
  ```bash
  cd react-pc-frontend
  npm run dev       # Entwicklungs-Server
  npm run build     # Produktions-Build
  npm run lint      # Linter
  npm test          # Vitest Testsuite
  ```
- **Frontend Zeiterfassung (Mobile):**
  ```bash
  cd react-zeiterfassung
  npm run dev
  npm run build
  npm test
  ```

---

## 🏁 Aufgabenabschluss (Review & Ship)
Am Ende jeder Aufgabe:
1. Tests ausführen:
   - Backend: `./mvnw test`
   - Frontend: `npm test` im jeweiligen Frontend-Ordner
2. Builds prüfen:
   - `npm run build` in betroffenen Frontend-Verzeichnissen
3. Diff prüfen:
   - `git status` und `git diff` prüfen.
   - Nur Dateien stagen, die für diese Aufgabe geändert wurden (keine Fremdänderungen).
   - `git diff --staged` auf versehentlich committete Secrets oder Logs prüfen.
4. Graphify synchronisieren:
   - `./graphify update .`
