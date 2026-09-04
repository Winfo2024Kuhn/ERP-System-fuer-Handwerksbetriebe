---
name: loese-problem
description: Startet die komplette Multi-Agent-Pipeline (Brainstorming → Spec → Issue → paralleles Coden → PR → Merge) für GRÖSSERE, mehrteilige Probleme/Features im ERP. NICHT für kleine Bugfixes, Ein-Zeilen-Änderungen oder Aufgaben, bei denen der Weg schon klar ist — dafür reicht die normale Umsetzung oder /bugfix, sonst kostet die Pipeline nur unnötig Zeit. Trigger: der Nutzer beschreibt ein neues, größeres Feature oder ein komplexes, mehrteiliges Problem und will den kompletten Ablauf bis zum fertigen, gemergten Pull Request automatisiert haben. Auch explizit aufrufbar über "/loese-problem".
---

# loese-problem — Multi-Agent-Pipeline

Siehe Design-Entscheidungen: `docs/superpowers/specs/2026-08-28-loese-problem-pipeline-design.md`.

**Announce:** "Ich nutze den loese-problem-Skill für dieses Problem."

Du (Hauptagent) bist der **Orchestrator** über die komplette Pipeline. Du
bleibst von Anfang bis Ende aktiv — auch für die finale Zielprüfung in
Schritt 7 brauchst du dich nicht neu einlesen, weil du das Brainstorming
selbst geführt hast.

## Wann NICHT starten

- Ein-Datei-Fix, klarer Bug, triviale Änderung → normal umsetzen oder `/bugfix`.
- Du weißt bereits genau, was zu tun ist, und es betrifft nur eine
  Komponente → kein Grund für Issue/Worktrees/PR-Ritual.

Im Zweifel trotzdem starten — Schritt 0 sortiert kleine Aufgaben ohnehin
sofort in den schlanken Weg aus.

## Schritt 0: Brainstorming

Rufe den Skill `superpowers:brainstorming` ganz normal auf. Er klassifiziert
selbst bounded/architectural.

- **bounded:** Setze direkt um (1 Durchgang), dann `.claude/commands/review-and-ship.md`.
  Max. 2 Nachbesserungs-Durchläufe, wenn der Reviewer dort etwas beanstandet.
  **ENDE.** Der Rest dieses Skills gilt nicht.
- **architectural:** Sobald der Nutzer dem Design zustimmt, geht es **ohne
  weiteren Zwischenstopp** automatisch weiter. Das ist der einzige manuelle
  Freigabepunkt der ganzen Pipeline.

## Schritt 1: Spec schreiben

Starte den Agenten `loese-problem-spec` (Sonnet) mit dem Brainstorming-Ergebnis
(Problem, Design, Entscheidungen). Er schreibt
`docs/superpowers/specs/<datum>-<thema>.md`.

## Schritt 2: Issue anlegen

**Voraussetzung prüfen:** Ist der GitHub-MCP-Connector verfügbar (per
`ToolSearch` nach einem GitHub-Tool suchen, z.B. `create_issue`)? Falls nicht
autorisiert: **stoppen**, dem Nutzer sagen, dass er den Connector über die
claude.ai-Einstellungen bzw. `/mcp` autorisieren muss, dort weitermachen wo
aufgehört wurde. Nicht raten oder mit `gh` improvisieren.

Wenn verfügbar: Starte `loese-problem-issue` (Sonnet) mit der fertigen Spec.
Er legt das Issue an und trägt die Issue-Nummer in die Spec ein.

## Schritt 3: Grober Implementierungsplan

Starte `loese-problem-grobplan` (Opus) mit Spec + Issue-Nummer. Schreibt
`docs/superpowers/plans/<datum>-<thema>.md` (grobe Task-Liste, noch ohne
Rundeneinteilung).

## Schritt 4: Parallelität planen

Starte `loese-problem-parallelplan` (Sonnet) mit dem Plan. Er ergänzt den
Plan um die Abschnittseinteilung (siehe `references/plan-format.md`):
maximal 3 Tasks pro Abschnitt, disjunkte Dateien, plus eine
Worktree-/Branch-Zuordnung pro Task. Lege außerdem die Kontext-Log-Datei an
(`references/kontext-log-format.md`) und den Feature-Branch für das gesamte
Vorhaben.

**Danach, bevor der erste Coding-Agent startet** — lies dazu
`references/fallstricke.md`, dort steht das Warum zu jedem Punkt:

1. **Baseline messen.** Backend- und Frontend-Tests **plus Lint** auf dem
   unveränderten Feature-Branch laufen lassen. Exakte Zahlen und die Namen
   vorbestehender Fehler ins Kontext-Log, mit Abnahmeregel („grün = genau
   diese N bekannten Fehler, der N+1. ist neu"). Ohne das streiten Coding- und
   Review-Agent später über Fehler, die schon vorher da waren.
2. **Umgebung prüfen und ins Kontext-Log schreiben:** Build-Werkzeuge
   vorhanden und in der geforderten Version? Abhängigkeiten installierbar
   (Egress-Policy!)? Wenn etwas nur mit einem Workaround geht, gehört der
   Workaround ins Log — sonst scheitert jeder Agent einzeln daran.
3. **Worktrees des ersten Abschnitts selbst anlegen.** Niemals die Agenten
   `git worktree add` machen lassen: parallel kollidieren sie am
   `.git`-Verzeichnis.

## Schritt 5: Abschnitte abarbeiten (Schleife)

Für jeden Abschnitt der Reihe nach:

1. **Coding-Agenten parallel starten** — alle Tasks des Abschnitts in
   **einer einzigen Nachricht**, sonst laufen sie nacheinander. Jeder bekommt
   den Agenten `loese-problem-coding` (Sonnet) mit: seinem Task-Abschnitt aus
   dem Plan, den Global Constraints, dem Feature-Branch-Namen (Basis für
   seinen eigenen Task-Branch), dem Pfad zur Kontext-Log-Datei.
2. **Warten**, bis alle Agenten des Abschnitts zurück sind.
3. **Ein** Review-Agent für den ganzen Abschnitt: `loese-problem-review` (Opus).
   Er merged die Task-Branches in den Feature-Branch, testet selbst, prüft
   die Kriterien und liefert eine Ampel.
4. **🔴 und noch keine 2 Nachbesserungen versucht:** Befund an denselben
   Coding-Agenten zurück (neuer Auftrag, nur der Befund + sein Task), dann
   zurück zu Schritt 3.
   **🔴 nach der 2. erfolglosen Nachbesserung:** Pipeline stoppen, verbleibende
   🔴-Befunde dem Nutzer vorlegen. **ENDE.**
5. **🟢/🟡:** Abschnitt abgenommen. **Sofort in den Feature-Branch mergen und
   pushen** — nicht bis zum Schluss warten. Der Container kann eingesammelt
   werden und ein Kontolimit die Pipeline mitten in der Arbeit abreißen; was
   nicht auf `origin` liegt, ist weg. Danach die Worktrees des nächsten
   Abschnitts anlegen und weiter. Keine offenen Abschnitte mehr → Schritt 6.

**Wenn ein Agent abstürzt** (Kontolimit, Timeout, API-Fehler): nicht einfach
neu starten. Erst nachsehen, was er hinterlassen hat —
`git -C <worktree> log --oneline <feature-branch>..HEAD` und
`git -C <worktree> status --short`. Ein halb fertiger Task kann einen Stand
hinterlassen, der für sich genommen kaputt ist (real passiert: Entity-Feld
committet, zugehörige Migration nur unversioniert daneben — die Anwendung
wäre nicht mehr gestartet). Erst den Stand heilen oder verwerfen, dann neu
starten.

## Schritt 6: Pull Request

Einmal Gesamt-Build/Tests über den fertigen Feature-Branch (wie
`review-and-ship.md`). Dann PR erstellen (GitHub-MCP), verlinkt mit dem Issue.

## Schritt 7: Zielprüfung (du selbst, kein neuer Agent)

Lies den kompletten PR-Diff. Prüfe **nur** gegen die ursprüngliche Absicht aus
Schritt 0 — keine Stildetails, die hat der Abschnitts-Reviewer schon geprüft:

- Löst der PR das ursprünglich beschriebene Problem vollständig?
- Ist etwas Themenfremdes reingerutscht?
- Fehlt ein Teil, der im Design zugesagt war?

**Abweichung, noch keine 2 Versuche:** Befund an einen Coding-Agenten
(`loese-problem-coding`) zur Nachbesserung, danach Schritt 7 wiederholen.
**Abweichung nach dem 2. Versuch:** stoppen, dem Nutzer vorlegen. **ENDE.**
**Passt:** weiter zu Schritt 8.

## Schritt 8: Abschluss

PR mergen (GitHub-MCP), Issue schließen. Kurz berichten: Issue-/PR-Nummer,
Anzahl Abschnitte, Anzahl Nachbesserungs-Runden insgesamt.

## Schritt 9: Skill nachschärfen (Pflicht, nicht optional)

Bist du unterwegs auf ein Problem gestoßen, das dieser Skill hätte verhindern
können, hänge es an `references/fallstricke.md` an — **bevor** du die Aufgabe
als erledigt meldest. Kandidaten sind Dinge, die dich oder einen Agenten Zeit
gekostet haben, ohne zur eigentlichen Aufgabe zu gehören:

- eine Nachbesserungsrunde, die ein Satz im Auftrag verhindert hätte,
- ein Umgebungsproblem, an dem mehrere Agenten nacheinander gescheitert sind,
- ein Fehler, den die Testsuite prinzipbedingt nicht sehen konnte,
- eine Stelle, an der Plan oder Doku nachweislich falsch lagen.

**Nicht** aufnehmen: einmalige Ausrutscher, Geschmacksfragen, alles schon
Dokumentierte. Format: kurze Überschrift, das Fehlerbild zum Wiedererkennen,
die Regel fürs nächste Mal. Lieber drei brauchbare Zeilen als eine Seite
Nacherzählung.

Wenn eine Regel eine Datei betrifft, die ohnehin schon Vorgaben macht
(`plan-format.md`, `kriterien.md`, `kontext-log-format.md`), gehört sie dorthin
statt in die Sammeldatei.

## Rollen & Modelle

| Rolle | Agent-Datei | Modell |
|---|---|---|
| Orchestrator (Brainstorming + Zielprüfung) | — (du selbst) | aktuelle Session |
| Spec-Autor | `loese-problem-spec` | Sonnet |
| Issue-Agent | `loese-problem-issue` | Sonnet |
| Grobplaner | `loese-problem-grobplan` | Opus |
| Parallelitäts-Planer | `loese-problem-parallelplan` | Sonnet |
| Coding-Agent | `loese-problem-coding` | Sonnet |
| Abschnitts-Reviewer | `loese-problem-review` | Opus |

## Drei Schleifen, jede mit fester Obergrenze

1. Review + Nachbesserung je Abschnitt — max. 2 Durchläufe (Schritt 5.4).
2. Abschnitt für Abschnitt bis nichts mehr offen ist — kein Cap nötig, endet
   von selbst.
3. Rücksprung aus der Zielprüfung — max. 2 Durchläufe (Schritt 7).

Wird eine feste Grenze erreicht, wird **nicht weitergelooped** — die
Pipeline stoppt und legt dem Nutzer die verbleibenden Befunde vor.

## Referenzen

- `references/plan-format.md` — erwartetes Format für Plan-Datei inkl.
  Abschnitts-/Worktree-Zuordnung.
- `references/kontext-log-format.md` — Format und Lock-Protokoll für die
  gemeinsame Kontext-Log-Datei.
- `references/fallstricke.md` — **gesammelte Stolperstellen aus echten
  Läufen.** Orchestrator liest sie vor Schritt 4, der Review-Agent bekommt sie
  im Auftrag. Die dort für Coding-Agenten markierten Punkte gehören in deren
  Auftragstext.
- `references/kriterien.md` — Performance-/Observability-/API-Design-Kriterien,
  gemeinsame Quelle für Coding- und Review-Agent (Coding-Agent liest sie
  vorher, damit der Review-Agent möglichst wenig findet).
