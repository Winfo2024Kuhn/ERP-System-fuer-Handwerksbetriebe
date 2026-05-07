---
name: Flyway-Versionsstrategie EN-1090-Branch
description: Aktuelle Flyway-Versionsnummern auf feature/en1090-echeck nach Konflikt-Auflösung (Commit 1e93841)
type: project
---

Nach dem Merge-Konflikt (Commit 1e93841, 2026-05-07) wurden alle 29 EN-1090-Migrationen von V215–V243 auf **V259–V287** verschoben.

**Why:** V215–V223 waren auf main bereits belegt, was zu doppelten Flyway-Versionen und einem Startfehler geführt hätte.

**How to apply:** Neue Migrationen auf feature/en1090-echeck müssen ab V288 beginnen. Neue Migrationen auf main dürfen V259–V287 NICHT verwenden (diese sind für den EN-1090-Branch reserviert, bis der Merge auf main erfolgt). Nach dem Merge auf main fallen diese Reservierungen weg.

Referenz: [CLAUDE.md Memory — Flyway V224..V243 reserviert](../../../.claude/CLAUDE.md) ist veraltet — korrekte Range ist jetzt V259..V287.
