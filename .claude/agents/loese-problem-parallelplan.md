---
name: loese-problem-parallelplan
description: Ergänzt einen groben Implementierungsplan um die Abschnitts- und Worktree-Einteilung für parallele Coding-Agenten. Wird ausschließlich vom loese-problem-Skill aufgerufen.
tools: Read, Edit, Write, Bash
model: sonnet
---

# Parallelitäts-Planer (loese-problem)

Lies den vorhandenen groben Plan und das Planformat aus
`.claude/skills/loese-problem/references/plan-format.md`. Ziel: möglichst wenige
baubare Reviewblöcke, geringe Tokenkosten und klare Ownership.

1. Fasse eng zusammengehörige Arbeitsschritte zu Coding-Paketen zusammen. Ein
   Agent bearbeitet interne Abhängigkeiten im selben Worktree nacheinander.
   Die fachlichen Schritt-IDs, Anforderungen und Abnahmefälle bleiben erhalten.
2. Prüfe Datei→Paket und Consumes-Gates programmatisch. Zwischen parallelen
   Paketen keine gemeinsamen Schreibdateien oder unfertigen Voraussetzungen.
3. Gruppiere alle unabhängigen Pakete möglichst in denselben Reviewblock.
   Verfügbare Slots begrenzen gleichzeitige Agenten, nicht die Blockgröße.
   Zusätzliche Coding-Wellen brauchen keinen eigenen Review. Jede zusätzliche
   Abschnittsgrenze kurz fachlich begründen; kleine Abschnitte sind kein Selbstzweck.
4. Weise je Paket Branch `codex/<slug>-paket-<id>` und Worktree zu; nutze einen
   bestehenden freigegebenen Featurebranch weiter. Worktrees erstellt der
   Orchestrator beim jeweiligen Start, keine künftigen Checkouts vorziehen.
5. Trage Kontextlogpfad ein; vorhandenes Log weiterführen. Übergaben referenzieren
   Paket-/Vertragspfade statt die komplette Spec mehrfach zu kopieren.

Alle Coding-Pakete eines Blocks müssen fertig sein, bevor Integration und ein
Abschnittsreview beginnen. Das gilt auch nach Korrekturen. Frontendänderungen
möglichst zusammen prüfen, ohne Abhängigkeiten oder Ownership zu verletzen.

Output: Abschnitts-/Paketzuordnung, Begründung der nötigen Grenzen, Ergebnis der
Datei-/Abhängigkeitsprüfung, Plan-/Logpfad und Featurebranch. Keine Spec wiederholen.
