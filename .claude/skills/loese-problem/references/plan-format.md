# Plan-Format für loese-problem

Planung optimiert auf wenige Reviewblöcke und geringen Kontextverbrauch bei
vollständigem Funktionsumfang. Die Tokenbudget-Regeln aus SKILL.md gelten.

```markdown
# Plan: <Thema>
Issue: #<Nummer>
Feature-Branch: codex/<slug>
Kontext-Log: docs/superpowers/plans/<datum>-<thema>-log.md

## Global Constraints
<Gemeinsame Vorgaben einmalig; in Aufträgen darauf verweisen.>

## Abschnitt 1: <baubares gemeinsames Ergebnis>
Begründung der Grenze: <konkrete Abhängigkeit/Integrationsprüfung>

### Coding-Paket A: <zusammenhängende Verantwortung>
- Arbeitsschritte: <stabile IDs in interner Reihenfolge>
- Branch: codex/<slug>-paket-a
- Worktree: .claude/worktrees/<slug>-paket-a
- Files: <exakte Vereinigungsmenge; ein Eigentümer>
- Vorbild: <bekannter Bestandspfad + Symbol>
- Interfaces:
  - Produces: <konkrete Verträge>
  - Consumes extern: <vor Paketstart integriert und geprüft>
  - Consumes intern: <vor Nutzung fertig implementierter und getesteter Schritt>
- Steps:
  - [ ] <konkrete Änderung + fachlicher Rot/Grün-Test>
- Abschlussnachweise: <geprüfter Stand, Befehle/Ergebnis, Logpfade>

### Coding-Paket B
...

## Abnahme
<Ein Code-Review des fertigen integrierten Blocks, bei Frontend ein Designreview.>
```

## Abschnittsschnitt prüfen

1. Zusammengehörige Schritte mit gemeinsamen Dateien oder enger Abhängigkeit
   einem Paket zuordnen; derselbe Agent setzt sie nacheinander um. Pakete müssen
   fachlich abgrenzbar bleiben. Keine Paketgrenze nur pro Klasse oder Schicht.
2. Datei→Paket und Abhängigkeiten programmatisch prüfen. Parallele Pakete dürfen
   keine Schreibdateien teilen und keine noch unfertigen Ergebnisse voneinander
   benötigen. Jede Voraussetzung liegt in einem früheren abgenommenen Block oder
   einem früheren Schritt desselben Pakets.
3. Möglichst viele unabhängige Pakete in denselben Reviewblock. Verfügbare Slots
   begrenzen nur gleichzeitig laufende Agenten: weitere Pakete in Coding-Wellen
   ohne Zwischenreview bearbeiten. Es gibt kein starres Drei-Pakete-Limit pro Block.
4. Jeder fertige Block ist baubar und startfähig, einschließlich passender
   Entity-/Migrationsänderungen. Zusammengehörige Frontendänderungen bündeln,
   damit ein Designreview den vollständigen Ablauf prüfen kann.
5. Erst **alle Coding-Pakete fertig**, dann integrieren und reviewen. Dieselbe
   Barriere gilt für Korrekturen. Keine unfertigen Änderungen in Folgetasks geben.

## Übergabe und Wiederaufnahme

Ein Agent bekommt den Pfad seines Paketauftrags, Basiscommit, Worktree und offene
Punkte; der Auftrag referenziert gemeinsame Verträge. Keine vollständige Kopie
von Spec, Gesamtplan oder Unterhaltung pro Agent. Abschlüsse nennen Commit,
Prüfnachweise und Blocker; Details bleiben in Logdateien. Bereits gültige
Testnachweise unveränderter Bereiche bleiben mit ihrem geprüften Stand verknüpft.

Jedes Paket besitzt einen Worktree. Paketbranches nicht unter einen bestehenden
Featurebranch hängen: `codex/<slug>/paket-a` kollidiert mit `codex/<slug>` als Git-Ref.
Branch und Worktree nach Abnahme nur entsprechend dem tatsächlichen Zustand
wiederverwenden. Kontextlog append-only; Plan enthält die Struktur und Abnahmefälle.
