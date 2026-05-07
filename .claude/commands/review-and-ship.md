---
name: review-and-ship
description: Startet den erp-code-reviewer-Subagenten im Hintergrund und kümmert sich parallel um Compile, Tests und fehlende Tests. Loopt Findings → Fix → Re-Check, bis alles grün ist. Erst dann Commit & Push.
---

# Review & Ship (parallelisiert)

Du bist der **Implementations-Agent** im Window des Users. Deine Aufgabe ab jetzt:

1. **Review-Subagent SOFORT im Hintergrund starten** (er reviewt parallel zu dir).
2. **Während er reviewt**, kümmerst DU dich um Build, Tests, Lint und fehlende Test-Coverage.
3. **Wenn der Subagent zurückmeldet**: Findings einarbeiten, dann nochmal Build/Tests prüfen.
4. **Loop** bis alles grün → Commit & Push.

**STOPP-REGEL:** Kein Commit ohne 🟢 vom Reviewer UND ohne grüne Tests/Builds. Bei 🔴 → Fix → erneuter Review-Lauf.

---

## 🚧 SCOPE-REGEL: Nur eigene Änderungen committen

Es laufen oft **mehrere Claude-Instanzen parallel** im selben Repo. Du darfst deshalb **ausschließlich Dateien stagen und committen, die du in dieser Session selbst geändert hast**. Fremde Änderungen (von anderen Agents, vom User, von anderen Branches) bleiben im Working Tree liegen – nicht stagen, nicht reverten, nicht „aufräumen".

**Vorgehen vor jedem `git add`:**

1. Merke dir die Liste der Dateien, die DU geändert/erstellt hast (aus deinen eigenen Edit/Write-Calls).
2. `git status` zeigt evtl. weitere Dateien – die ignorierst du.
3. Stage **nur explizit per Pfad** (`git add <pfad1> <pfad2>`). **NIEMALS** `git add .`, `git add -A` oder `git add -u`.
4. Vor Commit: `git diff --staged --name-only` – jede Datei darin muss aus deiner eigenen Änderungsliste stammen. Sonst unstage (`git restore --staged <pfad>`).

**Ausnahme – Frontend-Build-Artefakte:** Wenn du Frontend-Code geändert und `npm run build` laufen lassen hast, darfst (und sollst) du die daraus entstehenden statischen Assets mit committen, damit der User sie nicht auf einem anderen Rechner neu bauen muss:

- `src/main/resources/static/index.html`
- `src/main/resources/static/assets/index-*.js`
- `src/main/resources/static/assets/index-*.css`
- analoge Dateien aus dem Mobile-Build, falls Mobile betroffen war

Diese gelten als „deine eigenen Änderungen", solange sie aus deinem Build entstanden sind.

---

## Phase 0: Review-Subagent im Hintergrund starten (NICHT WARTEN)

**ZUERST** (bevor du irgendetwas anderes tust):

Rufe das `Agent`-Tool auf mit `run_in_background: true`:

- `subagent_type: "erp-code-reviewer"`
- `description: "ERP Backend+Frontend+Security Review"`
- `run_in_background: true`
- Prompt-Inhalt:
  - Kurze Beschreibung des aktuell implementierten Features/Fixes (aus dem Conversation-Context).
  - Anweisung: „Prüfe den aktuellen Diff (`git diff main...HEAD` + ungestaged) gemäß `docs/agent instructions/docs/BACKEND_ARCH.md`, `docs/agent instructions/docs/FRONTEND_UI.md` und `docs/agent instructions/docs/TESTING_SECURITY.md`. Gib einen strukturierten Report zurück mit: Ampel (🟢/🟡/🔴), kritische Findings (Datei:Zeile + Begründung), nicht-kritische Hinweise. Sei streng aber konkret."
  - Bitte um Ampel-Bewertung am Ende: 🟢 GRÜN / 🟡 GELB / 🔴 ROT.

**Wichtig:** Du wartest NICHT auf das Ergebnis. Du gehst sofort zu Phase 1.

---

## Phase 1: Parallel zum Review – Build, Tests, Coverage (DU)

Während der Subagent reviewt, arbeitest du diese Liste ab. Bei jedem Fehler: Root Cause beheben, dann weiter.

### 1a. Secrets-Scan (KRITISCH – bei Fund sofort abbrechen)

```bash
git diff main...HEAD -- "*.properties" "*.yml" "*.yaml" "*.env"
git diff main...HEAD | grep -iE "(api_key|password=|token=|secret=|passwd)" | grep -v "test@example\|mustermann\|musterstraße"
```

- Credentials, Tokens, echte Personendaten?
- `application-local.properties` / `uploads/` / `*.key|pem|p12` im Diff?
- **Fund → SOFORTIGER ABBRUCH**, Subagent kann weiterlaufen, aber kein Commit.

### 1b. Backend kompilieren + testen

```bash
./mvnw.cmd clean package -DskipTests 2>&1 | tail -20
./mvnw.cmd test 2>&1 | tail -40
```

- Compile-Fehler → fixen.
- Test-Fail → Root Cause analysieren (nicht Test „grün-fummeln").

### 1c. Desktop-Frontend

```bash
cd c:\dev\ERP-System-fuer-Handwerksbetriebe\react-pc-frontend && npm run lint 2>&1 | tail -30
cd c:\dev\ERP-System-fuer-Handwerksbetriebe\react-pc-frontend && npm run build 2>&1 | tail -20
cd c:\dev\ERP-System-fuer-Handwerksbetriebe\react-pc-frontend && npm run test 2>&1 | tail -30
```

### 1d. Mobile-Frontend (nur wenn Mobile-Diff)

```bash
cd c:\dev\ERP-System-fuer-Handwerksbetriebe\react-zeiterfassung && npm run lint 2>&1 | tail -30
cd c:\dev\ERP-System-fuer-Handwerksbetriebe\react-zeiterfassung && npm run build 2>&1 | tail -20
cd c:\dev\ERP-System-fuer-Handwerksbetriebe\react-zeiterfassung && npm run test 2>&1 | tail -30
```

### 1e. Fehlende Tests schreiben

Schau dir `git diff main...HEAD --name-only` an und prüfe:

- Neue Service-Methode ohne Test? → Test schreiben.
- Neuer Controller-Endpoint ohne Happy-Path + Fehlerfall? → beides schreiben.
- Neue React-Komponente mit Logik ohne Vitest? → Smoke-Test minimum.
- Tests mit echten Personendaten → auf `Max Mustermann` etc. umstellen.

Nach jedem hinzugefügten Test: 1b/1c/1d für den betroffenen Bereich erneut laufen lassen.

### 1f. Kurzer Eigen-Gegencheck (während Subagent noch läuft)

- Controller mit Business-Logik?
- Entities direkt in REST-Response statt DTO?
- String-Konkat in JPQL/SQL?
- `System.out.println` / `console.log` / auskommentierter Code / TODO ohne Ticket?
- Frontend: indigo/blue statt rose/slate? `<select>`/`<input type="date">`/`<iframe src=>` statt Pflicht-Komponenten?
- Neue Flyway-Migration: höhere Versionsnummer als alle bestehenden? Bestehende Migration verändert? (→ ABSOLUTES VERBOT)

---

## Phase 2: Subagent-Ergebnis abholen & einarbeiten

Sobald der Hintergrund-Agent fertig meldet:

1. **Report einlesen** (Ampel + Findings).
2. **🔴 ROT** → ALLE kritischen Findings fixen. Danach Phase 1b–1d für die betroffenen Bereiche erneut. Danach **erneut Phase 0** (neuer Hintergrund-Review-Lauf).
3. **🟡 GELB** → Findings dem User zusammengefasst zeigen + fragen ob er trotzdem freigeben will. Ohne Freigabe wie 🔴 behandeln.
4. **🟢 GRÜN** → weiter zu Phase 3.

**Loop-Regel:** Nach jeder Fix-Runde MUSS ein neuer Review-Lauf gestartet werden (Phase 0 erneut, im Hintergrund), während du parallel Phase 1b–1d wiederholst.

### 2a. Architektur & Code-Qualität
Für jede geänderte Java-Datei prüfen:
- Enthält der Controller Business-Logik? (Verletzung der Schichtentrennung)
- Werden Entities direkt in REST-Responses zurückgegeben? (DTOs fehlen)
- Gibt es String-Konkatenation in JPQL/SQL statt parametrisierten Queries?
- Field Injection statt Constructor Injection?
- `System.out.println` oder `console.log` im Commit?
- Auskommentierter Code?
- TODOs ohne Ticket-Referenz?

### 2b. Frontend-Qualität
Für jede geänderte TSX/TS-Datei prüfen:
- `dangerouslySetInnerHTML` ohne Sanitizer?
- URL-Parameter ohne `encodeURIComponent()`?
- Farben: indigo/blue statt rose/slate?
- HTML `<select>` statt `Select`-Komponente?
- `<input type="date">` statt `DatePicker`-Komponente?
- `<iframe src={url}>` für PDFs statt `DocumentPreviewModal`?
- Neue Seite ohne Page-Header-Pattern?

### 2c. Flyway-Migrationen
Falls neue SQL-Dateien im Diff:
```bash
git diff main...HEAD --name-only -- "src/main/resources/db/migration/"
```
- Versionsnummer höher als alle bestehenden?
- Bestehende Migrationsdateien verändert? (ABSOLUTES VERBOT)

### 2d. Test-Coverage-Check
- Neue Service-Methoden ohne Tests?
- Neue Controller-Endpoints ohne Happy-Path und Fehlerfall-Test?
- Mindestens 80% Branch-Coverage im Coverage-Report erreicht?
- 100% Branch-Coverage bei kritischen Stellen!!
- Tests mit echten Personendaten?

### 2e. Dokumentations-Check
- Neue UI-Patterns oder Komponenten ohne Update in `.github/DEVELOPMENT.md`?

---

## Phase 3: Security-Review

Skill `/security-review` ausführen (`Skill`-Tool, `skill: "security-review"`). Kritische Befunde blocken Commit.

---

## Phase 4: Commit & Push

Nur wenn:

- ✅ Reviewer-Ampel 🟢 (oder 🟡 mit User-Freigabe)
- ✅ `/security-review` ohne kritische Findings
- ✅ Backend Build + Tests grün
- ✅ Frontend Lint + Build + Tests grün (Desktop + Mobile falls betroffen)
- ✅ Keine Secrets / DSGVO-Verstöße im Diff

Commit-Nachricht ableiten aus `git diff main...HEAD --stat` und `git log --oneline -5`:

- Typ: `feat` / `fix` / `refactor` / `test` / `docs` / `chore`
- Scope aus Modul ableiten
- Beschreibung: WAS + WARUM

```bash
git status
# NUR eigene Dateien stagen – siehe Scope-Regel oben.
# Kein "git add .", kein "git add -A". Pfade explizit angeben:
git add <pfad/zu/eigener/datei1> <pfad/zu/eigener/datei2> ...
# Falls Frontend gebaut wurde, zusätzlich die Build-Artefakte:
git add src/main/resources/static/index.html src/main/resources/static/assets/index-*.js src/main/resources/static/assets/index-*.css
# Gegencheck: alles im Stage muss aus deiner eigenen Änderungsliste stammen.
git diff --staged --name-only
git commit -m "$(cat <<'EOF'
<typ>(<scope>): <kurze Beschreibung>

<Ursache/Motivation>

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
git push origin HEAD
```

---

## Wenn ein Check FEHLGESCHLAGEN bleibt

**KEIN Commit, KEIN Push.**

```text
❌ REVIEW FEHLGESCHLAGEN – Kein Commit erstellt

Phase: [0/1a/1b/1c/1d/1e/1f/2/3]
Problem: [Genaue Beschreibung]
Datei(en): [Betroffene Dateien mit Zeilennummern]
Reviewer-Ampel: [🟢/🟡/🔴]

Bitte beheben und /review-and-ship erneut ausführen.
```

---

## Phase 5: GitHub-Issues aktualisieren (PFLICHT nach erfolgreichem Push)

Sobald der Push durchgelaufen ist, **MUSST du den `gh-issue-manager`-Agent aufrufen**, damit er die betroffenen GitHub-Issues kommentiert (und auf Rückfrage ggf. schließt).

Nutze dazu das Agent-Tool mit `subagent_type: "gh-issue-manager"` und übergib ihm folgenden Kontext im `prompt`:

- Commit-Hash und Commit-Message des gerade erstellten Commits
- Branch-Name
- Liste der geänderten Dateien (`git diff --name-only HEAD~1`)
- Etwaige Issue-Nummern aus Commit-Message oder Branch-Name (z.B. `#42`)

Beispiel-Prompt an den Agent:

```text
Review-and-Ship ist abgeschlossen. Kommentiere die betroffenen GitHub-Issues mit einer
Zusammenfassung der Änderungen.

Commit: <hash>
Branch: <branch>
Message: <commit-message>
Geänderte Dateien:
<liste>

Referenzierte Issues: <#nummern oder "keine gefunden – bitte via gh issue list suchen">

Schließe Issues NICHT eigenständig – nur kommentieren, ausser der User hat den Abschluss
vorher explizit bestätigt.
```

Erst **nach** dem Agent-Aufruf den Abschlussbericht ausgeben.

---

## Abschlussbericht (nach erfolgreichem Push)

```text
✅ SHIPPED

Commit: <hash>
Branch: <branch>
Review-Runden: <Anzahl Phase-0-Aufrufe>
Geprüfte Checks:
  - erp-code-reviewer Subagent: 🟢
  - /security-review Skill: ✅
  - Backend Build + Tests: ✅
  - Frontend Lint + Build + Tests: ✅
  - Secrets-Scan: ✅
Push: origin/<branch>
Issues aktualisiert: [Liste der kommentierten Issue-Nummern oder "keine gefunden"]
```

---

## Merksätze

- **Phase 0 IMMER mit `run_in_background: true`.** Sonst blockiert der Review die Tests.
- **Du wartest nicht** – während der Reviewer arbeitet, kompilierst und testest du.
- **Jede Fix-Runde startet einen neuen Review-Lauf** – nicht nur einmal reviewen.
- **Tests grün-fummeln ist verboten.** Root Cause finden, dann fixen.
- **Nur eigene Dateien stagen** (parallele Sessions!) – Frontend-Build-Artefakte (`static/index.html`, `static/assets/index-*.js|css`) dürfen mit, damit der User auf anderem Rechner kein `npm run build` mehr braucht.
