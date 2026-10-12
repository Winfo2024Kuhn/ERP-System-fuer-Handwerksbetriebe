# Graphify Knowledge Graph Richtlinien

## graphify: Code-Landkarte für breite Fragen (Empfehlung)

**Für breite Code-Fragen (Architektur, Abhängigkeiten, „was bricht, wenn …“) ist graphify hilfreich. Gezielte Namenssuchen gehen per Grep meist schneller.**

Der Projekt-Graph enthält die gesamte Symbolik, Abhängigkeiten und Komponentenstruktur des ERPs.

### Wrapper (versioniert, funktioniert auch in Git-Worktrees):
- macOS/Linux: `scripts/graphify <befehl>`
- Windows: `scripts\graphify.cmd <befehl>`

### Befehlsübersicht:
| Frage-Typ | Befehl |
| --- | --- |
| "Wo ist X?" / "Was ruft X auf?" | `scripts/graphify query "wo wird X verwendet"` |
| "Wie hängen A und B zusammen?" | `scripts/graphify path "A" "B"` |
| "Was ist Konzept Y?" | `scripts/graphify explain "Y"` |
| "Was bricht, wenn ich X ändere?" | `scripts/graphify affected "X"` |
| Breiter Architektur-Überblick / sehr breite Review | `graphify-out/GRAPH_REPORT.md` lesen |

Hängt graphify selbst einen `## graphify`-Block an (blankes `graphify`, Update nach jeder Änderung, `wiki/index.md`), gilt er nicht – wieder entfernen.

### Aktualisierung:
Nach Änderungen am Code einmalig am Ende der Aufgabe `scripts/graphify update .` ausführen – nicht nach jedem Edit.
