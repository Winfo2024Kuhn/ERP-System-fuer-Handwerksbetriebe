# Kontext-Log: Rückgängig & Wiederholen im Dokumenteditor

Append-only. Absoluter Pfad (Haupt-Checkout dieses Vorhabens, nicht dein
eigenes Task-Worktree):
`/Users/marvinkuhn/dev/wt/verlauf-integration/docs/superpowers/plans/2026-09-17-dokumenteditor-rueckgaengig-log.md`
Lock-Protokoll: siehe `.claude/skills/loese-problem/references/kontext-log-format.md`.

**Wichtig:** Immer diesen absoluten Pfad verwenden, nicht den relativen Pfad
aus dem eigenen Worktree — jedes Task-Worktree hat eine eigene Kopie dieser
Datei; ein relativer Schreibzugriff landet dort unsichtbar für alle anderen
Agenten und geht beim Merge verloren.

Issue: #160
Spec: `docs/superpowers/specs/2026-09-17-dokumenteditor-rueckgaengig.md`
Plan: `docs/superpowers/plans/2026-09-17-dokumenteditor-rueckgaengig.md`
Feature-Branch: `feature/dokument-verlauf` (existiert bereits, auch auf
`origin`, mit Spec- und Plan-Commits; abgezweigt von `main` @ 0ff937db,
17.09.2026 — nicht neu anlegen, kein `git checkout -b`)

Abschnitte: 2 (Abschnitt 1 = Task 1–3 parallel, Abschnitt 2 = Task 4).
Branch-/Worktree-Zuordnung je Task steht im Plan-Kopf der Task-Blöcke.

---

## Ausgangslage vor Abschnitt 1 (Orchestrator)

Zeit: 2026-09-17 (vor dem Start des ersten Coding-Agenten)
Stand: `feature/dokument-verlauf` @ 4b9be7c7 (nur Spec- und Plan-Commits, kein Produktivcode)

**Gemessene Baseline (alles grün):**

| Gate | Befehl (in `react-pc-frontend/`) | Ergebnis |
|---|---|---|
| Lint | `npm run lint` | exit 0, keine Meldung |
| Unit-Tests | `npm run test` (vitest run) | 133 Dateien, 1467 Tests, alle grün, ~25 s |
| Typen | `npx tsc -b` | exit 0 |
| E2E | `E2E_PORT=5311 npx playwright test` | 627/627 grün, 2,8 min, drei Größen (`pc-14zoll`, `pc-uebergang`, `pc-monitor`) |

**Abnahmeregel:** Es gibt **keine** vorbestehenden Fehler. Jeder rote Test,
jeder Lint-Fehler und jeder Typfehler ab hier ist neu und gehört zum Vorhaben.

**Backend:** wird nicht angefasst (reines Frontend-Vorhaben). Keine
Maven-Gates, keine Backend-Baseline. Reviewer prüfen per
`git diff --name-only main...HEAD`, dass nichts unter `src/main/java` oder
`src/test/java` liegt.

**Umgebung (macOS, nicht Windows — die Fallstricke-Datei ist windowslastig):**

- Shell: zsh/bash. Kein PowerShell, keine NTFS-Junctions.
- `node_modules` in jedem Worktree als Symlink:
  `ln -s /Users/marvinkuhn/dev/ERP-System-fuer-Handwerksbetriebe/react-pc-frontend/node_modules <worktree>/react-pc-frontend/node_modules`
  (legt der Orchestrator an). Vor `git worktree remove` erst den Symlink mit
  `rm <worktree>/react-pc-frontend/node_modules` lösen.
- Node v22.23.2, npm 10.9.8.
- `graphify` gibt es **nur** im Haupt-Checkout
  (`/Users/marvinkuhn/dev/ERP-System-fuer-Handwerksbetriebe/graphify`), nicht in
  Worktrees. `./graphify update .` läuft einmal am Ende, im Haupt-Checkout,
  durch den Orchestrator.
- Der graphify-Hook-Guard in `.claude/settings.json` zeigt auf Windows-Pfade und
  greift hier nicht.
- Der Skill `superpowers:test-driven-development` ist in dieser Umgebung **nicht
  installiert**. Testgetrieben wird trotzdem gearbeitet: roter Test, Grund des
  Fehlschlags verstehen, umsetzen, grün, committen.
- Build-Gate ohne Artefakte:
  `npx tsc -b && npx vite build --outDir "$(mktemp -d)" --emptyOutDir`.
  `npm run build` schreibt nach `../src/main/resources/static/`
  (`emptyOutDir: false`) und erzeugt Merge-Konflikte.

**Parallele fremde Arbeit (nicht anfassen):**

- Im Haupt-Checkout liegt uncommittete Arbeit des Nutzers (Vorkalkulation:
  `react-pc-frontend/src/features/vorkalkulation/`, `VorkalkulationDummy.tsx`,
  `e2e/vorkalkulation.spec.ts`, Änderung an `App.tsx`).
- In einer anderen Session läuft „Zahlungsziel im Dokumenteditor mitspeichern“
  (Branch `claude/competent-satoshi-5be572`, Worktree
  `.claude/worktrees/competent-satoshi-5be572`). Sie ändert `handleSave` und die
  Ungespeichert-Signatur in derselben `index.tsx` wie Task 4. Deshalb läuft der
  Signatur-Umbau hier über die eine Hilfsfunktion `baueDokumentSignatur`
  (Task 1). Der Orchestrator gleicht vor dem PR mit `main` ab.
