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
git add -p   # interaktiv prüfen, oder gezielt nach Datei
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
```

---

## Merksätze

- **Phase 0 IMMER mit `run_in_background: true`.** Sonst blockiert der Review die Tests.
- **Du wartest nicht** – während der Reviewer arbeitet, kompilierst und testest du.
- **Jede Fix-Runde startet einen neuen Review-Lauf** – nicht nur einmal reviewen.
- **Tests grün-fummeln ist verboten.** Root Cause finden, dann fixen.
