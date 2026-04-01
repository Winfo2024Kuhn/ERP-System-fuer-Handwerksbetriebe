---
name: sync-docs
description: Prüft ob aktuelle Commits neue Patterns, Komponenten oder Regeln eingeführt haben die noch nicht in AGENTS.md, CLAUDE.md oder DEVELOPMENT.md dokumentiert sind – und macht die nötigen Updates.
---

# Dokumentations-Sync

Dieser Skill liest die letzten Commits, vergleicht sie mit der vorhandenen Dokumentation und aktualisiert diese wo nötig.

---

## Schritt 1: Commits analysieren

```bash
# Alle Commits seit dem letzten Merge in main
git log main...HEAD --oneline

# Vollständiger Diff aller geänderten Dateien
git diff main...HEAD -- "*.tsx" "*.ts" "*.java" "*.md"
```

Suche in den Diffs nach:
- Neuen Komponenten in `src/components/` → gehören in CLAUDE.md Pflicht-Tabelle falls wiederverwendbar
- Neuen Patterns (fetch-as-blob, custom hooks, util-Funktionen)
- Neuen Regeln oder Einschränkungen die im Code dokumentiert sind (Kommentare mit „WICHTIG", „REGEL", „NIEMALS")
- Entfernten oder umbenannten Komponenten → Doku-Verweise aktualisieren
- Neuen API-Endpoints oder Backend-Services

---

## Schritt 2: Dokumentation prüfen

Lies die drei Haupt-Dokus:

```
.claude/AGENTS.md        – Agent-Regeln, Patterns, verbotene Aktionen
.claude/CLAUDE.md        – Projekt-Überblick, Coding-Regeln, Design-System
.github/DEVELOPMENT.md   – Vollständige UI-Komponenten-API, Security-Checkliste
```

Für jede Änderung aus Schritt 1 prüfen:
- [ ] Ist die neue Komponente in der Pflicht-Tabelle von CLAUDE.md?
- [ ] Ist das neue Pattern in AGENTS.md erklärt?
- [ ] Hat die neue Komponente einen API-Abschnitt in DEVELOPMENT.md?
- [ ] Sind Ausnahmen/Ausnahmefälle dokumentiert?
- [ ] Sind alte Verweise auf entfernte Komponenten/Funktionen aktualisiert?

---

## Schritt 3: Updates durchführen

Nur dokumentieren was **nicht-offensichtlich** ist oder **zukünftige Fehler verhindern** würde:

**AGENTS.md aktualisieren wenn:**
- Neues Pattern das man falsch implementieren könnte (wie `DocumentPreviewModal`)
- Neue verbotene Aktion oder neue Pflicht-Komponente
- Neue DRY-Ausnahme oder Ausnahmeregel

**CLAUDE.md aktualisieren wenn:**
- Neue Pflicht-Komponente zur Tabelle hinzufügen (Pfad + was sie ersetzt)
- Neue Coding-Regel die für alle gilt

**DEVELOPMENT.md aktualisieren wenn:**
- Neue UI-Komponente mit Props-API
- Geänderter Button-Style oder Farbwert
- Neues Page-Pattern

---

## Schritt 4: Zusammenfassung

Ausgabe am Ende:
- Was wurde dokumentiert (mit Dateipfad + Zeilennummer)
- Was wurde bewusst NICHT dokumentiert (und warum)
- Offene Punkte die menschliche Entscheidung brauchen

---

## Hinweise

- **Keine spekulativen Docs** – nur was tatsächlich im Code existiert
- **Kein Copy-Paste von Implementierungsdetails** – Docs erklären das *Warum*, nicht das *Wie*
- Code-Beispiele in Docs: minimal, zeigen nur die Nutzung (nicht die interne Implementierung)
- Wenn unklar ob etwas dokumentiert werden soll → kurz beim User nachfragen
