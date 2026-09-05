---
name: review-and-ship
description: Startet den erp-code-reviewer-Subagenten im Hintergrund und kümmert sich parallel um Compile, Tests und fehlende Tests. Loopt Findings → Fix → Re-Check, bis alles grün ist. Erst dann Commit & Push.
---

# Review & Ship (parallelisiert)

Du bist der **Implementations-Agent** im Window des Users. Deine Aufgabe ab jetzt:

1. **Review-Subagent SOFORT im Hintergrund starten** – er übernimmt **komplett** Code-Review **inklusive Security** (Secrets, OWASP, DSGVO, Path-Traversal, CORS, …).
2. **Während er reviewt**, kümmerst DU dich **ausschließlich** um Build, Tests, Lint und fehlende Test-Coverage. **Du machst KEIN eigenes Security-Audit, KEINEN Secrets-Scan, KEINEN Eigen-Gegencheck der Reviewer-Themen** – das ist Doppelarbeit. Vertraue auf den Reviewer.
3. **Wenn der Subagent zurückmeldet**: Findings 1:1 umsetzen, dann nochmal Build/Tests prüfen.
4. **Loop** bis alles grün → Commit & Push.

**Rollenteilung – wichtig:**

- **Reviewer-Subagent** = Qualität + Architektur + Security + DSGVO. Findet Probleme.
- **Du (Hauptagent)** = Build, Tests, Coverage, Implementierung der Reviewer-Findings, am Ende Commit, Push, Pull Request und – bei grünen Checks – der Merge. **Findest keine Probleme selbst** – du behebst die, die der Reviewer meldet.

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

## Phase 0: Review-Lauf starten (NICHT WARTEN)

**ZUERST** (bevor du irgendetwas anderes tust), startest du den **Claude-Reviewer im Hintergrund**.

> **Kein Codex.** Der Codex-Zweitreview wurde auf Wunsch des Users entfernt. Rufe `codex` in diesem Workflow **nicht** auf – weder als Zweitmeinung noch als Fallback. Der `erp-code-reviewer`-Subagent ist die alleinige Review-Instanz.

### 0a. Claude-Reviewer (erp-code-reviewer Subagent)

Rufe das `Agent`-Tool auf mit `run_in_background: true`:

- `subagent_type: "erp-code-reviewer"`
- `description: "ERP Backend+Frontend+Security Review"`
- `run_in_background: true`
- Prompt-Inhalt:
  - Kurze Beschreibung des aktuell implementierten Features/Fixes (aus dem Conversation-Context).
  - Anweisung: „Prüfe den aktuellen Diff (`git diff main...HEAD` + ungestaged) gemäß `docs/agent instructions/docs/BACKEND_ARCH.md`, `docs/agent instructions/docs/FRONTEND_UI.md` und `docs/agent instructions/docs/TESTING_SECURITY.md`. Gib einen strukturierten Report zurück mit: Ampel (🟢/🟡/🔴), kritische Findings (Datei:Zeile + Begründung), nicht-kritische Hinweise. Sei streng aber konkret."
  - Bitte um Ampel-Bewertung am Ende: 🟢 GRÜN / 🟡 GELB / 🔴 ROT.

### 0b. Sofort weiter zu Phase 1

**Du wartest NICHT auf den Reviewer.** Er läuft im Hintergrund. Du gehst sofort zu Phase 1 und kompilierst/testest parallel.

---

## Phase 1: Parallel zum Review – Build, Tests, Coverage (DU)

Während der Subagent reviewt, arbeitest du diese Liste ab. Bei jedem Fehler: Root Cause beheben, dann weiter.

> **Reminder:** Secrets-Scan, Security-Audit, DSGVO-Check, Architektur-Gegencheck → macht der Reviewer-Subagent. Du **nicht**. Wenn der Reviewer einen Secret-/Security-Befund meldet, behebst du ihn in Phase 2.

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

> **Kein Eigen-Gegencheck.** Architektur-, UI- und Security-Themen prüft der Reviewer-Subagent. Du wartest seinen Report ab und reagierst dann in Phase 2.

---

## Phase 2: Review-Ergebnis abholen & einarbeiten

Sobald der Hintergrund-Reviewer fertig ist (du bekommst eine Completion-Notification):

1. **Report einlesen:** Output aus dem `Agent`-Tool-Result.
2. **Ampel auswerten:**
   - **🔴 ROT** → ALLE kritischen Findings fixen. Danach Phase 1b–1d für die betroffenen Bereiche erneut. Danach **erneut Phase 0** (Reviewer neu starten).
   - **🟡 GELB** → Findings dem User zeigen + fragen ob er trotzdem freigeben will. Ohne Freigabe wie 🔴 behandeln.
   - **🟢 GRÜN** → weiter zu Phase 3.

**Loop-Regel:** Nach jeder Fix-Runde MUSS der Reviewer neu laufen (Phase 0a erneut, im Hintergrund), während du parallel Phase 1b–1d wiederholst.

**Einspruchs-Regel:** Wenn du ein Finding für sachlich falsch hältst (der Reviewer sieht den Kontext nicht immer vollständig), fixe es **nicht** stillschweigend weg und ignoriere es auch nicht – **leg es dem User kurz vor** mit deiner Begründung. Das deutet oft auf eine echte Architektur-Entscheidung hin.

---

## Phase 3: Commit & Push

Nur wenn:

- ✅ Claude-Reviewer-Ampel 🟢 (oder 🟡 mit User-Freigabe) – deckt Security/DSGVO/Secrets ab
- ✅ Backend Build + Tests grün
- ✅ Frontend Lint + Build + Tests grün (Desktop + Mobile falls betroffen)

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

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
git push origin HEAD
```

---

## Phase 4: Pull Request, Checks abwarten, mergen

Nur wenn Phase 3 durch ist (Commit gepusht).

### 4a. Pull Request anlegen

Ziel ist `main`, es sei denn der User nennt einen anderen Basis-Branch.

```bash
gh pr create --base main --head "$(git branch --show-current)" \
  --title "<gleiche Zeile wie der Commit-Titel>" \
  --body-file <(cat <<'EOF'
Behebt #<issue>.

## Problem
<was war kaputt / was fehlte>

## Lösung
<was die Änderung tut>

## Tests
<welche Suiten, wie viele, lokal gelaufen>

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)
```

**Hängt der Branch an einem gemeinsamen Arbeitsbranch, auf dem parallele Sessions
arbeiten?** Dann NICHT diesen Branch als PR-Quelle nehmen — der PR schleppte die
gesamte fremde, womöglich unfertige Arbeit mit nach `main`. Stattdessen den eigenen
Commit auf einen frischen Branch von `origin/main` cherry-picken und von dort den PR
öffnen. Gegenprobe vor dem Anlegen:

```bash
git rev-list --count origin/main..HEAD   # >1 bei fremder Arbeit im Branch
```

### 4b. Checks abwarten

```bash
gh pr checks <nr> --watch
```

Warte, bis **kein** Check mehr `pending` ist.

> ⚠️ **Dieses Repo hat auf Pull Requests aktuell KEINE Build- oder Test-Checks.**
> `.github/workflows/release.yml` triggert nur auf `v*`-Tags; auf dem PR läuft
> ausschließlich CodeQL (Security-Scanning). „Alle Checks grün" heißt hier also
> **nicht**, dass Tests gelaufen sind. Die Absicherung sind und bleiben deine
> lokalen Läufe aus Phase 1. Sag das im Abschlussbericht ausdrücklich dazu, damit
> niemand ein grünes Häkchen für einen Testlauf hält.

### 4c. Mergen

Merge automatisch, sobald **alle** Bedingungen erfüllt sind:

- ✅ Jeder Check auf dem PR ist `pass` (kein `fail`, kein `pending`)
- ✅ Reviewer-Ampel 🟢 (oder 🟡 mit User-Freigabe)
- ✅ Deine lokalen Builds und Tests aus Phase 1 waren grün
- ✅ `gh pr view <nr> --json mergeable -q .mergeable` liefert `MERGEABLE`

```bash
gh pr merge <nr> --squash --delete-branch
```

**Verboten:**

- Merge bei rotem oder noch laufendem Check.
- `--admin` oder sonst irgendein Weg, der einen Check überspringt.
- Merge, wenn dein eigener Testlauf rot war — auch dann nicht, wenn GitHub grün meldet
  (siehe die Warnung in 4b: GitHub testet hier gar nicht).
- Merge eines Branches, der fremde unfertige Arbeit mitbringt (siehe 4a).

Bleibt ein Check rot: **nicht mergen**, sondern nach dem Muster unten berichten.

---

## Wenn ein Check FEHLGESCHLAGEN bleibt

**KEIN Commit, KEIN Push.**

```text
❌ REVIEW FEHLGESCHLAGEN – Kein Commit erstellt

Phase: [0/1b/1c/1d/1e/2/3]
Problem: [Genaue Beschreibung]
Datei(en): [Betroffene Dateien mit Zeilennummern]
Reviewer-Ampel: [🟢/🟡/🔴]

Bitte beheben und /review-and-ship erneut ausführen.
```

---

## Abschlussbericht (nach erfolgreichem Push)

```text
✅ SHIPPED

Commit: <hash>
Branch: <branch>
Review-Runden: <Anzahl Phase-0-Aufrufe>
Lokal geprüft:
  - erp-code-reviewer Subagent (Claude, inkl. Security/DSGVO/Secrets): 🟢
  - Backend Build + Tests: ✅
  - Frontend Lint + Build + Tests: ✅
Push: origin/<branch>
Pull Request: #<nr>
GitHub-Checks: <Liste mit Ergebnis> — ACHTUNG: nur CodeQL, keine Tests
Merge: <gemergt / offen, weil ...>
```

---

## Merksätze

- **Kein Codex.** Einzige Review-Instanz ist der `erp-code-reviewer`-Subagent. Kein `codex exec`, keine Zweitmeinung über ein Fremdmodell.
- **Phase 0 IMMER mit `run_in_background: true`.** Sonst blockiert der Review die Tests.
- **Du wartest nicht** – während der Reviewer arbeitet, kompilierst und testest du.
- **Jede Fix-Runde startet einen neuen Review-Lauf** – nicht nur einmal reviewen.
- **Tests grün-fummeln ist verboten.** Root Cause finden, dann fixen.
- **Nach dem Push kommt der PR** (Phase 4), und bei grünen Checks wird **automatisch gemergt** – ohne Rückfrage. Rot oder pending: nicht mergen, berichten.
- **Grüne GitHub-Checks sind hier kein Testnachweis.** Auf PRs läuft nur CodeQL. Was zählt, sind deine lokalen Läufe aus Phase 1.
- **PR nie von einem geteilten Arbeitsbranch öffnen** – sonst wandert fremde, unfertige Arbeit nach `main`. Eigenen Commit auf einen frischen Branch von `origin/main` cherry-picken.
- **Nur eigene Dateien stagen** (parallele Sessions!) – Frontend-Build-Artefakte (`static/index.html`, `static/assets/index-*.js|css`) dürfen mit, damit der User auf anderem Rechner kein `npm run build` mehr braucht.
