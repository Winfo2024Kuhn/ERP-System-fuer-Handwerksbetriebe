# Kontext-Log: Spracheingabe in der mobilen Zeiterfassung

**Append-only.** Niemand ändert oder löscht bestehenden Text. Jeder hängt
unten einen neuen Block an, gesichert über das Lock-Protokoll.

**Absoluter Pfad dieser Datei** (nicht die Kopie im eigenen Worktree
beschreiben, sonst sieht sie niemand):

    /Users/marvinkuhn/dev/wt/spracheingabe/docs/superpowers/plans/2026-09-17-spracheingabe-zeiterfassung-log.md

Lock:

    LOG="/Users/marvinkuhn/dev/wt/spracheingabe/docs/superpowers/plans/2026-09-17-spracheingabe-zeiterfassung-log.md"
    LOCK="$LOG.lock"
    for i in $(seq 1 20); do mkdir "$LOCK" 2>/dev/null && break || sleep 1; done
    cat >> "$LOG" <<'BLOCK'
    ...dein Block...
    BLOCK
    rmdir "$LOCK"

Das Log committen die Agenten **nicht** — das macht der Orchestrator nach
jedem Abschnitt, sonst kollidiert jeder Merge am Dateiende.

---

## Rahmen

| | |
| --- | --- |
| Issue | #163 |
| Spec | `docs/superpowers/specs/2026-09-17-spracheingabe-zeiterfassung.md` |
| Plan | `docs/superpowers/plans/2026-09-17-spracheingabe-zeiterfassung.md` |
| Feature-Branch | `feature/spracheingabe-zeiterfassung` |
| Orchestrator-Worktree | `/Users/marvinkuhn/dev/wt/spracheingabe` |
| Haupt-Checkout | `/Users/marvinkuhn/dev/ERP-System-fuer-Handwerksbetriebe` (Branch `main`, **nicht anfassen**) |

## Baseline — gemessen am 17.09.2026 auf dem unveränderten Feature-Branch

    Backend   ./mvnw test      554 Testklassen, 3126 Tests
                               0 Failures, 0 Errors, 17 Skipped, Exit 0
    Frontend  npm test         22 Testdateien, 217 Tests, alle grün, Exit 0
    Lint      npm run lint     0 Fehler, Exit 0

**Abnahmeregel: alles grün.** Es gibt keine bekannten Vorschäden. Jeder
Fehler ist neu und gehört dem, der ihn verursacht hat.

## Umgebung — geprüft, gilt für alle Agenten

- Java/Maven über den Wrapper `./mvnw` im Repo-Wurzelverzeichnis. Läuft.
- Node/npm vorhanden. `npm ci` ist **nicht** nötig und soll **nicht**
  laufen — jedes Frontend-Worktree bekommt einen Symlink auf das
  `node_modules` des Haupt-Checkouts, den der Orchestrator anlegt.
- Playwright-E2E-Tests: nur in Abschnitten mit sichtbarer Oberfläche
  (ab Abschnitt 4). Niemals parallel zu einem laufenden Maven-Lauf.
- `src/main/resources/static/index.html` zeigt in jedem frischen Worktree
  einen Phantom-Diff durch Zeilenende-Normalisierung. **Kein Befund.**
  Nicht committen, nicht reparieren, nicht im Report melden.
- Generierte Build-Artefakte unter `src/main/resources/static/zeiterfassung/`
  gehören **nicht** in die Task-Commits. `npm run build` ist Fail-Fast-Prüfung;
  die Artefakte danach verwerfen. Den echten Build macht der Orchestrator
  einmal am Ende.
- Frontend-Edits sind hook-geschützt: vorher `docs/agent instructions/docs/FRONTEND_UI.md`
  per Read laden UND den Skill `handwerkerprogramm-design` aufrufen
  (Skill-Name **ohne** Namespace-Präfix). Backend-Edits brauchen
  `BACKEND_ARCH.md`, Test-Dateien `TESTING_SECURITY.md`.

## Abschnitte

| Abschnitt | Tasks | Design-Review |
| --- | --- | --- |
| 1 | 1, 3, 5, 6 | nein |
| 2 | 2, 4 | nein |
| 3 | 7 | nein |
| 4 | 8, 9, 10 | ja |
| 5 | 11, 12, 13 | ja |

---

## Verlauf

### 17.09.2026 — Orchestrator, vor Abschnitt 1

Vorbereitung abgeschlossen. Spec (`eb202c76`), Issue #163 (`e8f98135`),
Grobplan (`8e3e0aac`) und Abschnittseinteilung liegen auf dem
Feature-Branch und sind nach `origin` gepusht.

`main` wurde in den Feature-Branch geholt, bevor die ersten Worktrees
entstanden. Grund: `main` hatte sich nach dem Abzweigen um zwei Commits
bewegt — PR #162 (Dokumenteditor) und den Merge des iOS-Safe-Area-Fixes.
Letzterer hat `DashboardPage.tsx` geändert, dieselbe Datei, in die Task 10
das Zahnrad setzt. Ohne diesen Schritt hätten die Coding-Agenten auf einem
veralteten Stand gearbeitet.

Abweichung vom Grobplan-Vorschlag: Die dort vorgeschlagene Runde `{2, 4, 7}`
verletzt Randbedingung 5 desselben Kapitels — Task 7 importiert das Modul,
das Task 4 erst anlegt. Task 7 hat deshalb eine eigene Runde bekommen.
Fünf Abschnitte statt vier.
