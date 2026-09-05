# Kontext-Log — PC-App auf 14 Zoll

Plan: `docs/superpowers/plans/2026-09-05-layout-14-zoll.md`
Spec: `docs/superpowers/specs/2026-09-04-layout-14-zoll.md`

Append-only — niemand ändert oder löscht bestehenden Text, jeder hängt nur
unten einen neuen Block an. Lock-Protokoll siehe
`.claude/skills/loese-problem/references/kontext-log-format.md`.

## Baseline (Orchestrator)

Zeit: 2026-09-05T11:45:00Z
Stand: `feature/layout-14-zoll` @ 1fa8f265 (Basis: `origin/claude/eloquent-ramanujan-gz0w2t` @ 89ffc0d5), unverändert, `react-pc-frontend/`
Umgebung: Node 24, `node_modules` als Junction auf `ERP-System-fuer-Handwerksbetriebe/react-pc-frontend/node_modules` (package.json identisch mit main). Playwright-Browser vorhanden. Backend wird nicht angefasst, Backend-Suite ist für dieses Vorhaben kein Gate — der Code-Reviewer prüft stattdessen per `git diff --stat`, dass außerhalb von `react-pc-frontend/` und `docs/` nichts geändert wurde.

- `npm run lint`: 0 Fehler, **1 vorbestehende Warnung** (`src/pages/BelegeKasseEditor.tsx:1204`, react-hooks/exhaustive-deps).
- `npm run test`: 88 Dateien, 1082 Tests. Im Volllauf 13 rote Tests, alle `Test timed out in 5000ms`, alle in unberührten Dateien (ArtikelEditor, BeitraegeTab, EmailCenter, AnfrageEditor, BildEditorModal, document-editor/index, useDatensatzLock); im isolierten Nachlauf dieser Dateien grün bis auf 2 andere, wieder reine 5-s-Timeouts. Ursache: Last durch parallel laufende Agenten auf der Maschine (derselbe Befund wie im Sperr-Lauf, Log dort). **Kein Assertion-Fehler in der Baseline.**
- `npm run build`: grün. Build-Output in `src/main/resources/static/` danach auf HEAD zurückgesetzt.
- `E2E_PORT=5200 npm run test:e2e`: **110 passed**, 0 failed (beide Größen, 5,4 min inkl. Aufwärmen).

Abnahmeregel: grün = Lint 0 Fehler + genau diese 1 Warnung; Unit-Tests ohne Assertion-Fehler (ein 5-s-Timeout in einer unberührten Datei gilt erst als Fehler, wenn er im isolierten Einzellauf der Datei wiederkommt); Build grün; E2E 110 alte Tests + alle neuen grün in beiden Größen.

Branch-Namen: Task-Branches heißen `layout/task-<N>-<kurz>` (nicht `feature/layout-14-zoll/task-N` — Git kann keinen Zweig `a/b` anlegen, wenn `a` schon ein Zweig ist). Worktrees unter `C:/Users/MarvinKuhn/dev/ERP-für-Handwerker/wt/layout-task-<N>`, legt der Orchestrator an (mit `node_modules`-Junction). Ports 5201–5210 je Task, Design-Reviewer 5210+, andere Läufe auf dieser Maschine nutzen 5173–5190.
