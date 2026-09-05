# Plan-Format für loese-problem

Erweitert das normale `writing-plans`-/`parallele-runden`-Format um eine
Worktree-Spalte, weil hier jeder Task zusätzlich zur Datei-Trennung ein
eigenes Worktree bekommt.

```markdown
# Plan: <Thema>

Issue: #<Nummer>
Feature-Branch: feature/<slug>
Kontext-Log: docs/superpowers/plans/<datum>-<thema>-log.md

## Global Constraints

<Regeln, die für alle Tasks gelten — Doku-Pflichtlektüre, Projektregeln, etc.>

## Abschnitt 1 (max. 3 Tasks, disjunkte Dateien)

### Task 1
- Branch: feature/<slug>/task-1
- Worktree: .claude/worktrees/<slug>-task-1
- Files: <Liste der Dateien, die dieser Task anfasst — exakte Pfade>
- Vorbild: <existierender ähnlicher Code als Vorlage, Datei+Zeile — oder "keins" wenn es nichts Vergleichbares gibt>
- Interfaces:
  - Produces: <was dieser Task erzeugt, das andere importieren könnten — mit exakter Signatur>
  - Consumes: <was dieser Task aus anderen Tasks braucht — muss vorher fertig sein>
- Steps:
  - [ ] Schritt 1 (konkret: Datei + Klasse/Funktion + was genau passiert)
  - [ ] Schritt 2

### Task 2
...

## Abschnitt 2
...

## Log

<wird während der Ausführung NICHT hier befüllt — siehe eigene
Kontext-Log-Datei. Dieser Abschnitt bleibt für eine kurze
Abschluss-Zusammenfassung pro Abschnitt durch den Review-Agenten reserviert.>
```

## Regeln für den Abschnittsschnitt

Dieselben zwei Regeln wie in `parallele-runden`:

1. Keine zwei Tasks eines Abschnitts schreiben in dieselbe Datei.
2. Ein Task startet erst, wenn alles fertig und geprüft ist, was er unter
   `Consumes` braucht.
3. **Jeder Abschnitt muss für sich baubar und grün sein.** Ein Schnitt, der
   einen Zwischenstand mit Compilerbruch oder nicht startfähiger Anwendung
   erzeugt („Task A löscht, Task B repariert"), ist falsch — der
   Abschnitts-Review wird zwangsläufig rot. Solche Paare gehören in denselben
   Abschnitt, sonst wird der zerstörende Teil verschoben.

   Das gilt besonders für Änderungen, die nur **gemeinsam** einen lauffähigen
   Stand ergeben und die die Testsuite nicht sehen kann — z.B. ein
   Entity-Feld und seine Spaltenmigration, oder ein `DROP TABLE` und das
   Entfernen des Entities, das die Tabelle noch mappt. Läuft im Testprofil
   kein Flyway, fällt so etwas erst beim echten Start auf.

Zusätzlich hier: jeder Task bekommt ein eigenes Worktree, auch wenn die
Datei-Trennung schon sauber ist — das ist die zweite Sicherheitsebene, falls
ein Task doch mal unerwartet eine gemeinsame Datei anfasst (z.B. eine
generierte Datei). Ein echter Merge-Konflikt beim Zusammenführen der
Task-Branches ist dann ein sichtbarer Fehler statt eines stillen
Datenverlusts.

## Regel für Steps: konkret statt vage

Ein Coding-Agent bekommt später nur seinen eigenen Task, nicht den ganzen
Plan. Jeder Step muss deshalb so konkret sein, dass er direkt umgesetzt
werden kann, ohne vorher selbst breit im Code zu suchen — das kostet sonst
bei jedem Task erneut Zeit und Kontext. Konkret heißt: Datei + Klasse/Funktion
+ was genau passiert, plus ein Vorbild aus dem Bestandscode, wenn es eins
gibt. Der Coding-Agent darf `graphify` trotzdem nutzen, aber nur um einzelne
Punkte aus dem Plan gezielt zu prüfen — nicht um sich einen allgemeinen
Überblick zu verschaffen, den eigentlich schon der Plan liefern sollte.
