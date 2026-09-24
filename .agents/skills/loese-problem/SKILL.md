---
name: loese-problem
description: "Startet die komplette Multi-Agent-Pipeline (Brainstorming → Spec → Issue → paralleles Coden → PR → Merge) für GRÖSSERE, mehrteilige Probleme/Features im ERP. NICHT für kleine Bugfixes, Ein-Zeilen-Änderungen oder Aufgaben, bei denen der Weg schon klar ist — dafür reicht die normale Umsetzung oder /bugfix, sonst kostet die Pipeline nur unnötig Zeit. Trigger: der Nutzer beschreibt ein neues, größeres Feature oder ein komplexes, mehrteiliges Problem und will den kompletten Ablauf bis zum fertigen, gemergten Pull Request automatisiert haben. Auch explizit aufrufbar über \"/loese-problem\"."
---

# loese-problem — Multi-Agent-Pipeline

Siehe Design-Entscheidungen: `docs/superpowers/specs/2026-08-28-loese-problem-pipeline-design.md`.

**Announce:** "Ich nutze den loese-problem-Skill für dieses Problem."

Du (Hauptagent) bist der **Orchestrator** über die komplette Pipeline. Du
bleibst von Anfang bis Ende aktiv — auch für die finale Zielprüfung in
Schritt 7 brauchst du dich nicht neu einlesen, weil du das Brainstorming
selbst geführt hast.

## Tokenbudget und Abschnittsschnitt (Nutzervorgabe 23.09.2026)

Ziel ist **weniger Tokens für denselben vollständig geprüften Funktionsumfang**.
Ein Coding-Paket umfasst zusammengehörige Arbeitsschritte mit einer klaren
Datei-Ownership; ein Agent setzt interne Abhängigkeiten nacheinander im selben
Worktree um. Nicht jede Klasse, Schicht oder abhängige Kleinaufgabe braucht einen
neuen Agenten und Abschnitt.

- Plane möglichst wenige baubare Reviewblöcke. Unabhängige Pakete parallelisieren;
  abhängige Schritte oder gemeinsame Schreibdateien demselben Paket zuordnen.
- Verfügbare Agentenslots begrenzen gleichzeitige Arbeit, **nicht** die Paketanzahl
  eines Reviewblocks. Bei mehr unabhängigen Paketen in mehreren Coding-Wellen
  arbeiten; dazwischen keinen Review starten. Zusätzliche Abschnittsgrenzen mit
  konkreten Abhängigkeiten oder Integrationsrisiken begründen.
- **Alle Coding-Pakete fertig → zusammenführen → ein Code-Review des Blocks.**
  Bei Frontend zusätzlich ein Designreview für den gesamten betroffenen Block.
  Das gilt auch für Nachbesserungen und den Start von `review-and-ship`;
  keine Reviews halbfertiger Nachbar-Worktrees.
- Übergabe: Paket-ID, Auftragspfad, Basiscommit, Worktree, Ownership, benötigte
  Verträge und offene Punkte. Vorhandene Spec/Planabschnitte referenzieren; nicht
  mehrfach kopieren, neu recherchieren oder die volle Unterhaltung vererben.
- Ergebnis: Commit, Testnachweise, konkrete Blocker/Folgeabhängigkeiten. Logs und
  große Diffs in Dateien lassen; nur relevante Ausschnitte/Ergebnisse laden.
- Testnachweise an geprüften Quell-/Test-/Abhängigkeitsstand und Konfiguration
  binden. Gültige Nachweise unveränderter Bereiche gemeinsam nutzen; geänderte
  Bereiche und integrierte Wechselwirkungen erneut prüfen. Vorgeschriebene
  vollständige Tests, E2E, Lint und Builds bleiben Pflicht; Fehler nie ignorieren.
- Pro neuer Implementierungsrunde (Abschnitt mit gemeinsamem Reviewblock) neue
  Review-Agenten starten: einen Code-/ERP-Reviewer und bei Frontend zusätzlich
  einen Design-Reviewer. Reviewer nicht in den nächsten Abschnitt übernehmen;
  die Zuordnung gilt pro Runde, nicht pro einzelner Task.
- Bei Nachbesserungen innerhalb derselben Implementierungsrunde verbessert der
  ursprüngliche Coding-Agent sein Paket. Anschließend prüfen dieselben Reviewer
  dieser Runde die integrierten Korrekturen; dafür keine neuen Agenten starten.
  Nur Befunde plus Änderungen übergeben. Keine zusätzliche Reviewrolle ohne
  eigene Prüfverantwortung. Modellvorgaben des Nutzers gelten vor den Tabellen unten.

## Deutsch und Umlaute — auch im Code

Neue frei wählbare Bezeichner im Code (Variablen, Funktionen, Klassen und
Testnamen), Kommentare, Dokumentation, Reviewberichte, Kommunikation und
sichtbare UI-Texte ausschließlich deutsch schreiben. Echte Umlaute (ä, ö, ü,
Ä, Ö, Ü) und ß verwenden, soweit die jeweilige Programmiersprache sie an der
Stelle erlaubt; nicht durch ae, oe, ue oder ss ersetzen.

Vorgegebene Sprachsyntax, Bibliotheks- und externe API-Namen sowie bestehende
Schnittstellenverträge unverändert verwenden. Keine inkompatiblen Umbenennungen
bestehender Bezeichner oder Pfade allein zur sprachlichen Vereinheitlichung.
Diese Vorgabe an Coding- und Review-Agenten weitergeben und im Review prüfen.

## Sparsame Agentenkommunikation (Nutzervorgabe 23.09.2026)

Agenten arbeiten nach einem vollständigen Startauftrag autonom bis zur fertigen
Übergabe. Nachrichten zwischen Orchestrator und Agenten sind auf drei Anlässe
beschränkt: **echte Blocker, notwendige Entscheidungen/Ownership-Absprachen und
fertige Übergaben einschließlich Reviewbefunden**.

- Keine regelmäßigen Statusabfragen, Meilensteinmeldungen, Bestätigungen oder
  Erinnerungen an bereits erteilte Aufträge; auch nicht „beim nächsten Meilenstein“.
- Fortschritt über Abschluss-/Fehlerereignisse und wartende Werkzeuge verfolgen,
  ohne dafür neue Agentenantworten anzufordern. Unveränderten Wartestatus nicht
  wiederholt kommentieren.
- Notwendige Rückfragen/Befunde bündeln; Antworten knapp mit Entscheidung und
  relevanten Datei-/Logpfaden. Keine erneute Zusammenfassung bekannter Verträge.
- Tests, Logs oder Recherchen niemals nur für eine Statusmeldung wiederholen.

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
wenige Reviewblöcke mit disjunkten Coding-Paketen und einer
Worktree-/Branch-Zuordnung pro Paket; zusammengehörige Schritte bündeln. Lege außerdem die Kontext-Log-Datei an
(`references/kontext-log-format.md`) und den Feature-Branch für das gesamte
Vorhaben.

## Schritt 5: Abschnitte abarbeiten (Schleife)

Für jeden Abschnitt der Reihe nach:

1. **Coding-Agenten parallel starten** — die Pakete des Abschnitts entsprechend
   den verfügbaren Slots gemeinsam starten. Jeder bekommt
   den Agenten `loese-problem-coding` (Sonnet) mit: seinem Paketabschnitt aus
   dem Plan, den relevanten Global Constraints, dem Feature-Branch-Namen (Basis für
   seinen eigenen Task-Branch), dem Pfad zur Kontext-Log-Datei.
2. **Warten**, bis alle Coding-Pakete des Abschnitts vollständig fertig sind,
   auch die einer später gestarteten Coding-Welle.
3. **Fertige Paketbranches zusammenführen**, dann für diesen Abschnitt einen
   **neuen** `loese-problem-review` starten. Bei Frontend zusätzlich einen
   **neuen** `loese-problem-design-review`; keine Reviewer eines vorherigen
   Abschnitts weiterverwenden. Beide prüfen den gesamten integrierten Abschnitt
   mit getrennter Prüfverantwortung. Erst danach gilt er als abgenommen.
4. **🔴 und noch keine 2 Nachbesserungen versucht:** Befunde an die ursprünglichen
   Coding-Agenten der betroffenen Pakete zurückgeben (neuer Auftrag, nur Befund
   und Änderungen). Nach Abschluss aller Korrekturen erneut zusammenführen und
   **denselben Review-Agenten dieses Abschnitts** zur Nachprüfung vorlegen.
   Schritt 3 startet nur beim ersten Review eines neuen Abschnitts neue Agenten.
   **🔴 nach der 2. erfolglosen Nachbesserung:** Pipeline stoppen, verbleibende
   🔴-Befunde dem Nutzer vorlegen. **ENDE.**
5. **🟢/🟡:** Abschnitt abgenommen, weiter zum nächsten Abschnitt. Keine
   offenen Abschnitte mehr → weiter zu Schritt 6.

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
- `references/kriterien.md` — Performance-/Observability-/API-Design-Kriterien,
  gemeinsame Quelle für Coding- und Review-Agent (Coding-Agent liest sie
  vorher, damit der Review-Agent möglichst wenig findet).
