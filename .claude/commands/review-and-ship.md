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
- **Du (Hauptagent)** = Build, Tests, Coverage, Implementierung der Reviewer-Findings, am Ende Commit & Push. **Findest keine Probleme selbst** – du behebst die, die der Reviewer meldet.

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

## Phase 0: Zwei parallele Review-Läufe starten (NICHT WARTEN)

**ZUERST** (bevor du irgendetwas anderes tust), startest du **zwei unabhängige Reviewer parallel im Hintergrund** – einen Claude-basierten Subagenten und einen Codex-CLI-Run als Zweitmeinung. Codex läuft über den lokalen ChatGPT-Account-Login (`codex login status` → "Logged in using ChatGPT"), wird also über das ChatGPT-Abo abgerechnet, **nicht** über die OpenAI-API. Kein API-Key nötig – und es darf auch keiner gesetzt sein.

### 0a. Claude-Reviewer (erp-code-reviewer Subagent)

Rufe das `Agent`-Tool auf mit `run_in_background: true`:

- `subagent_type: "erp-code-reviewer"`
- `description: "ERP Backend+Frontend+Security Review"`
- `run_in_background: true`
- Prompt-Inhalt:
  - Kurze Beschreibung des aktuell implementierten Features/Fixes (aus dem Conversation-Context).
  - Anweisung: „Prüfe den aktuellen Diff (`git diff main...HEAD` + ungestaged) gemäß `docs/agent instructions/docs/BACKEND_ARCH.md`, `docs/agent instructions/docs/FRONTEND_UI.md` und `docs/agent instructions/docs/TESTING_SECURITY.md`. Gib einen strukturierten Report zurück mit: Ampel (🟢/🟡/🔴), kritische Findings (Datei:Zeile + Begründung), nicht-kritische Hinweise. Sei streng aber konkret."
  - Bitte um Ampel-Bewertung am Ende: 🟢 GRÜN / 🟡 GELB / 🔴 ROT.

### 0b. Codex-Reviewer (ChatGPT-Account, parallel)

Starte den Codex-Review **im Hintergrund** via `PowerShell` mit `run_in_background: true`. Output-Verzeichnis sicherstellen, Diff in Datei schreiben, dann an `codex exec review` pipen:

```powershell
New-Item -ItemType Directory -Force -Path .claude\reviews | Out-Null
$diffMain = git diff main...HEAD
$diffUnstaged = git diff
"$diffMain`n`n---UNSTAGED---`n$diffUnstaged" | Out-File -Encoding utf8 .claude\reviews\diff.patch

Get-Content .claude\reviews\diff.patch | codex exec review `
  -m gpt-5.5 `
  --sandbox read-only `
  "Du bist Senior-Reviewer fuer ein Spring-Boot + React ERP (Handwerker-Software). Pruefe den angehaengten Diff streng gegen die Projekt-Regeln: Constructor Injection (kein @Autowired auf Feldern), Flyway-Versionen V288+, Named-Params in JPA-Queries, rose-/slate-Farben + Pflicht-Komponenten im Frontend, DSGVO-Dummy-Daten 'Max Mustermann' in Tests, keine Secrets in Code, keine SQL-Injection, keine XSS, Handwerker-Sprache statt Buchhalter-Begriffen. Liefere zuerst kritische Findings (Datei:Zeile + Begruendung), dann nicht-kritische Hinweise, am Ende GENAU EINE Zeile: 'AMPEL: GRUEN' ODER 'AMPEL: GELB' ODER 'AMPEL: ROT'. Antworte auf Deutsch." `
  *> .claude\reviews\codex-review.md
```

Wichtig:

- `codex exec review` (nicht `codex exec`) → spezialisierter Review-Modus mit Diff-Verstaendnis
- `-m gpt-5.5` → Modell-Override; falls Codex 5.5 nicht kennt, fällt es auf den Default zurück. Liste der verfügbaren Modelle: `codex features` bzw. `~/.codex/config.toml`.
- `--sandbox read-only` → Codex darf nichts schreiben, nur analysieren
- `*> ...` leitet stdout+stderr in die Report-Datei
- **NIEMALS `OPENAI_API_KEY` env setzen** vor diesem Aufruf, sonst wechselt Codex auf API-Abrechnung statt ChatGPT-Abo. Falls die Variable in dieser Session gesetzt ist: `Remove-Item Env:OPENAI_API_KEY` vorher.

### 0c. Sofort weiter zu Phase 1

**Du wartest auf KEINEN der zwei Reviewer.** Beide laufen im Hintergrund. Du gehst sofort zu Phase 1 und kompilierst/testest parallel.

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

## Phase 2: BEIDE Review-Ergebnisse abholen & einarbeiten

Sobald **beide** Hintergrund-Reviewer (Claude-Subagent UND Codex) fertig sind:

1. **Beide Reports einlesen:**
   - Claude-Subagent: Output aus dem `Agent`-Tool-Result.
   - Codex: `Read` auf `.claude\reviews\codex-review.md`, letzte Zeile ist die `AMPEL: …`.
2. **Effektive Ampel = strengste der zwei** (ROT schlägt GELB schlägt GRÜN). Beispiel: Claude 🟢, Codex 🔴 → effektiv 🔴.
3. **🔴 ROT** (mind. einer rot) → ALLE kritischen Findings BEIDER Reports fixen. Danach Phase 1b–1d für die betroffenen Bereiche erneut. Danach **erneut Phase 0** (beide Reviewer neu starten).
4. **🟡 GELB** (kein rot, mind. einer gelb) → Findings beider Reports zusammengefasst dem User zeigen + fragen ob er trotzdem freigeben will. Ohne Freigabe wie 🔴 behandeln.
5. **🟢 GRÜN** (beide grün) → weiter zu Phase 3.

**Loop-Regel:** Nach jeder Fix-Runde MÜSSEN BEIDE Reviewer neu laufen (Phase 0a + 0b erneut, im Hintergrund), während du parallel Phase 1b–1d wiederholst. Niemals nur einen der zwei neu starten.

**Konflikt-Regel:** Wenn Claude und Codex sich bei einem konkreten Finding widersprechen (z.B. einer markiert einen Spot als kritisch, der andere nicht), gewinnt **nicht** automatisch die strengere Stimme – dann **kurz dem User vorlegen**, weil das oft auf eine echte Architektur-Entscheidung hinweist.

---

## Phase 3: Commit & Push

Nur wenn:

- ✅ Claude-Reviewer-Ampel 🟢 (oder 🟡 mit User-Freigabe) – deckt Security/DSGVO/Secrets ab
- ✅ Codex-Reviewer-Ampel 🟢 (oder 🟡 mit User-Freigabe) – Zweitmeinung über ChatGPT-Abo
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
Geprüfte Checks:
  - erp-code-reviewer Subagent (Claude, inkl. Security/DSGVO/Secrets): 🟢
  - codex exec review (ChatGPT-Abo, Zweitmeinung): 🟢
  - Backend Build + Tests: ✅
  - Frontend Lint + Build + Tests: ✅
Push: origin/<branch>
```

---

## Merksätze

- **Phase 0 IMMER mit `run_in_background: true`.** Sonst blockiert der Review die Tests.
- **Du wartest nicht** – während der Reviewer arbeitet, kompilierst und testest du.
- **Jede Fix-Runde startet einen neuen Review-Lauf** – nicht nur einmal reviewen.
- **Tests grün-fummeln ist verboten.** Root Cause finden, dann fixen.
- **Nur eigene Dateien stagen** (parallele Sessions!) – Frontend-Build-Artefakte (`static/index.html`, `static/assets/index-*.js|css`) dürfen mit, damit der User auf anderem Rechner kein `npm run build` mehr braucht.
