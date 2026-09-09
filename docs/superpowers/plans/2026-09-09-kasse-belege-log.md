# Kontext-Log: Kasse & Belege

Append-only. Absoluter Pfad (Haupt-Checkout, nicht Worktree):
`C:/Users/MarvinKuhn/dev/ERP-für-Handwerker/ERP-System-fuer-Handwerksbetriebe/docs/superpowers/plans/2026-09-09-kasse-belege-log.md`
Lock-Protokoll: siehe `.claude/skills/loese-problem/references/kontext-log-format.md`.

Spec: `docs/superpowers/specs/2026-09-09-kasse-belege.md`
Brainstorming (Recherche + Design, freigegeben): `docs/superpowers/specs/2026-09-09-kasse-belege-brainstorming.md`
Feature-Branch: `feature/kasse-belege` (abgezweigt von `main` @ d0e76688, 09.09.2026)

## Umgebung (Orchestrator, 09.09.2026)

Zeit: 2026-09-09T19:10:00Z
Rolle: Orchestrator

- Werkzeuge im Haupt-Checkout: OpenJDK 23.0.2, Node v24.19.0, npm 11.17.0, Maven 3.9.10 (`./mvnw`, unter PowerShell `.\mvnw.cmd`).
- `node_modules` vorhanden: `react-pc-frontend` (306 Einträge), `react-zeiterfassung` (453 Einträge). **Kein `npm ci` in Worktrees** — NTFS-Junction auf das Haupt-`node_modules` (`mklink /J`), legt der Orchestrator an. Vor `git worktree remove` die Junction mit `rmdir` (ohne /s) lösen, sonst wird das echte `node_modules` gelöscht (siehe fallstricke.md).
- Worktree-Konvention: `C:/Users/MarvinKuhn/dev/ERP-für-Handwerker/wt/<kurzslug>-task-<N>`, Branch `<kurzslug>/task-<N>-<stichwort>` (nicht unter `feature/`).
- Skills im Worktree: `wt/<worktree-name>:handwerkerprogramm-design` (scoped) funktioniert, unscoped nicht. Für den Hook `ui-ux-pro-max` (ohne Namespace).
- `./graphify update .` nur im Haupt-Checkout, einmal am Ende — nicht in Worktrees, nicht je Agent.
- Shell-Werkzeug: Timeout-Parameter ausdrücklich auf 600000 ms setzen; Standard sind 120 s, danach rutscht ein Lauf still in den Hintergrund.
- Maven: mehrere Testklassen mit Komma trennen (`-Dtest=A,B`), nie `+`. Nur ein Maven-Prozess je Worktree.
- `src/main/resources/static/index.html` zeigt in jedem frischen Worktree einen Phantom-Diff (CRLF/LF) — kein Befund, nicht committen.
- Frontend-Build schreibt in das versionierte `src/main/resources/static/` — Artefakte vor dem Commit verwerfen (`git checkout -- src/main/resources/static && git clean -fdq src/main/resources/static`).
- MCP-Server `shadcn` und `magic` sind in dieser Session **nicht** verbunden; Komponenten aus dem bestehenden `components/ui/`-Bestand bauen (Vorbilder nennen), nicht neu erfinden.
- GitHub: kein MCP-Connector, `gh` nicht angemeldet. Issue/PR laufen über Token-Datei im Scratchpad (`references/github-ohne-mcp.md`), nur durch den Orchestrator.
- Upstream-Stand: `main` wurde vor dem Abzweigen per fast-forward auf origin gezogen (56 Commits, Thema Zeitkonto, Migrationen bis V371). Nächste freie Migration: **V372**. An Beleg-/Kasse-Dateien hat sich upstream nur `BelegService.findCaller` (3 Zeilen, `MitarbeiterArt.MENSCH`) geändert.

Baseline (Lint/Test/Build/Maven/E2E auf dem unveränderten Feature-Branch) folgt als eigener Block, sobald der Lauf durch ist.

## Baseline Backend (Orchestrator)

Zeit: 2026-09-09T19:05:00Z
Befehl: `.\mvnw.cmd -B test` auf `feature/kasse-belege` @ d0e76688, unverändert.
Ergebnis: **Tests run: 2747, Failures: 0, Errors: 4, Skipped: 6** (BUILD FAILURE, 171 s).

Vorbestehende Fehler (alle `CannotCreateTransaction: Could not open JPA EntityManager` — brauchen eine echte Datenbank, im Testprofil nicht vorhanden):
- `AuditChainRepairIntegrationTest.rebuildMachtEchteLokaleKetteIntakt`
- `AuditChainRepairIntegrationTest.appendToChainHashIstNachRoundtripReproduzierbar`
- `AuditHashRoundtripDiagnoseTest.getrimmterZeitstempelUeberlebtDbRoundtrip`
- `AuditHashRoundtripDiagnoseTest.rohNanosekundenUeberlebenDbRoundtripNicht`

**Abnahmeregel Backend:** grün = genau diese 4 Errors, 0 Failures. Ein 5. Error oder ein Failure ist neu und ein Befund. Diese vier nicht reparieren, nicht überspringen, nicht deaktivieren.
