# Graphify Knowledge Graph Richtlinien

## OBERSTE REGEL: GRAPHIFY VOR JEDER CODE-SUCHE

**Bevor du Grep/Glob für Architektur- oder Code-Fragen nutzt, rufe ZUERST graphify auf.**

Der Projekt-Graph enthält die gesamte Symbolik, Abhängigkeiten und Komponentenstruktur des ERPs.

### Wrapper im Projektroot:
- macOS/Linux: `./graphify <befehl>`
- Windows: `.\graphify.cmd <befehl>`

### Befehlsübersicht:
| Frage-Typ | Befehl |
| --- | --- |
| "Wo ist X?" / "Was ruft X auf?" | `./graphify query "wo wird X verwendet"` |
| "Wie hängen A und B zusammen?" | `./graphify path "A" "B"` |
| "Was ist Konzept Y?" | `./graphify explain "Y"` |
| "Was bricht, wenn ich X ändere?" | `./graphify affected "X"` |
| Breiter Architektur-Überblick | `graphify-out/wiki/index.md` lesen |
| Sehr breite Review | `graphify-out/GRAPH_REPORT.md` lesen |

### Aktualisierung:
Nach Änderungen am Code einmalig am Ende der Aufgabe `./graphify update .` ausführen.
