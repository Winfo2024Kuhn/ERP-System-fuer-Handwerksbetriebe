# Fallstricke aus echten Läufen

**Diese Datei wächst.** Jeder Lauf, der auf ein Problem stößt, das der Skill
hätte verhindern können, hängt hier eine Regel an — kurz, konkret, mit dem
Fehlerbild, an dem man es wiedererkennt. Kein Tagebuch: nur Dinge, die beim
nächsten Mal Zeit oder eine Nachbesserungsrunde sparen.

Gelesen wird sie vom **Orchestrator vor Schritt 4** und vom **Review-Agenten**.
Die für Coding-Agenten relevanten Punkte gehören in deren Auftragstext — sie
lesen diese Datei nicht von selbst.

---

## Orchestrator

### Worktrees vorab anlegen, nie von den Agenten

Drei Agenten, die gleichzeitig `git worktree add` auf demselben Repository
ausführen, kommen sich am `.git`-Verzeichnis ins Gehege. Der Orchestrator legt
**alle** Worktrees eines Abschnitts an, **bevor** er die Agenten startet, und
nennt jedem nur noch seinen fertigen Pfad.

### Gitignorte Unterlagen liegen nicht im Worktree

Ist `docs/superpowers/` (oder wo Spec/Plan/Log liegen) in `.gitignore`, dann
sind diese Dateien in einem frischen Worktree **nicht vorhanden**. Jedem
Agenten die **absoluten Pfade zum Haupt-Checkout** mitgeben, sonst sucht er
vergeblich und rät sich den Auftrag zusammen.

### `node_modules` in Frontend-Worktrees symlinken

Ein `npm ci` pro Worktree kostet Minuten und Hunderte MB — und scheitert,
wenn die Registry aus der Lockfile per Egress-Policy geblockt ist. Stattdessen:

```
ln -s <haupt-checkout>/<frontend>/node_modules <worktree>/<frontend>/node_modules
```

Geprüft: `vitest` und `tsc` lösen über den Symlink sauber auf.

### Baseline messen, bevor der erste Task startet

Vor dem ersten Coding-Agenten **einmal** Backend- und Frontend-Tests **plus
Lint** auf dem unveränderten Feature-Branch laufen lassen und die exakten
Zahlen samt Namen vorbestehender Fehler ins Kontext-Log schreiben, mit
expliziter Abnahmeregel („grün = genau diese N bekannten Fehler, der N+1. ist
neu").

Ohne das diskutieren Coding- und Review-Agent über Fehler, die schon vorher
da waren — und im schlimmsten Fall „repariert" jemand einen fremden Test.

### Exit-Codes: Pipes verschlucken Fehlschläge

`./mvnw test | tail -20` liefert den Exit-Code von `tail`, nicht von Maven.
Ein fehlgeschlagener Build sieht dann wie ein erfolgreicher aus. Immer
`set -o pipefail`, `${PIPESTATUS[0]}` oder erst in eine Datei umleiten und
danach auswerten. **Das ist zweimal passiert und hat beide Male eine falsche
Erfolgsmeldung erzeugt.**

### Löschungen macht der Orchestrator

Der Permission-Classifier blockt in Coding-Sessions `git rm`, `rm` und `mv`
gleichermaßen („Blocked by classifier"), in der Orchestrator-Session nicht.
Tasks mit „(löschen)" in der Files-Liste: dem Agenten sagen, er soll die Datei
stehen lassen und es im Report vermerken — der Orchestrator löscht danach.

### Nach jedem Abschnitt sichern, nicht erst am Ende

Abgenommenen Abschnitt sofort in den Feature-Branch mergen und **pushen**. Der
Container kann eingesammelt werden, und ein Kontolimit kann die Pipeline
jederzeit mitten in der Arbeit abreißen. Was nicht auf `origin` liegt, ist weg.

### Review-Agent meldet „läuft noch" statt einer Ampel

Ein Reviewer startete `npm run test` im Hintergrund und beendete seine Runde
mit „ich melde mich, wenn die Suite fertig ist". Als Subagent bekommt er die
Benachrichtigung nie — die Ampel wäre ausgeblieben. Das ist keine
Fertigmeldung: per Nachricht an denselben Agenten weitermachen lassen
(synchron im Vordergrund, hohes Timeout), nicht neu starten und nicht als
abgenommen werten. Der Satz „Testläufe synchron im Vordergrund" gehört
trotzdem in jeden Reviewer-Auftrag, nicht nur in die Agenten-Definition.

### Nach jedem Agenten-Abbruch: Halbzustand prüfen

Stirbt ein Agent mitten in der Arbeit (Kontolimit, Timeout), ist der Worktree
in einem beliebigen Zwischenzustand. **Immer nachsehen**, ob er etwas
Unvollständiges committet hat:

```
git -C <worktree> log --oneline <feature-branch>..HEAD
git -C <worktree> status --short
```

Realer Fall: Die `@Version`-Felder waren committet, die zugehörige
Spaltenmigration lag nur unversioniert daneben — ein Stand, mit dem die
Anwendung wegen `ddl-auto=validate` nicht mehr gestartet wäre.

Zweiter realer Fall (05.09.2026, Kontolimit mitten in Abschnitt 2): Fix an
zwei Komponenten schon im Worktree, aber keine Spec, kein Commit, sechs
`debug_probe*.mjs` daneben. Nicht neu starten — einen abgebrochenen Agenten
per Nachricht (SendMessage an dieselbe Agent-ID) **wieder aufnehmen**: er
kennt seinen Stand, das spart das komplette Neu-Einlesen. Im Auftrag zur
Wiederaufnahme den vorgefundenen Stand benennen und sagen, wie er
testgetrieben nachholt (Fix als Patch sichern, Dateien zurücksetzen, rote
Spec, Patch wieder anwenden) — und ausdrücklich **kein `git stash`**, der
Stash ist mit anderen Sitzungen geteilt.

---

## Für die Aufträge an Coding-Agenten

Diese Punkte in **jeden** passenden Auftragstext schreiben — die Agenten
kennen sie sonst nicht.

### Lint ist ein Abnahme-Gate, nicht optional

Ein Task lieferte Build und Tests grün ab, riss aber das vorher grüne
Lint-Gate ein — Kosten: zwei Nachbesserungsrunden. Frontend-Aufträge immer
mit **`lint`, `test` und `build`** als Pflicht formulieren, Backend analog mit
dem, was das Projekt kennt.

### Generierte Build-Artefakte gehören nicht in den Commit

Schreibt der Frontend-Build in ein **versioniertes** Ausgabeverzeichnis
(hier: `src/main/resources/static/` mit `emptyOutDir: false`), erzeugt jeder
Lauf ein neues gehashtes Bundle und schreibt die Script-Zeile in `index.html`
um. Bauen mehrere Tasks parallel, kollidieren sie beim Abschnitts-Merge auf
genau dieser Zeile — ein reiner, unnötiger Konflikt plus je ein totes Bundle.

Regel: Build ist ein Fail-Fast-Check, die Artefakte werden vor dem Commit
verworfen. Der echte Build passiert **einmal koordiniert am Ende**.

### Zusammengehörige Änderungen in einer Auslieferung

Manche Änderungen ergeben nur **gemeinsam** einen lauffähigen Stand, und die
Testsuite kann das nicht sehen. Zweimal aufgetreten:

- `@Version`-Feld am Entity **und** die Spalte in der Migration.
- `DROP TABLE x` in einer Migration **und** das Entfernen des Entities, das
  `x` noch mappt.

Beides scheitert erst beim echten Start (`ddl-auto=validate`), nie im Test —
weil im Testprofil Flyway aus ist und das Schema aus den Entities entsteht.
Wenn ein Task nur die eine Hälfte umsetzt, muss die andere im **selben**
Abschnitt liegen.

### Der Auftrag ersetzt nicht den Plan-Block

Ein Auftragstext, der den Task zusammenfasst, verliert zwangsläufig Details.
Real passiert: Das Briefing beschrieb zwei Zustände einer Komponente, der
Plan-Block nannte vier — die beiden fehlenden (Deaktiviert-Zustand und
Verbindungswarnung) wären stillschweigend unter den Tisch gefallen.

Deshalb **immer** den Agenten ausdrücklich auf seinen `### Task N`-Block im
Plan als **maßgebliche Quelle** verweisen und dazusagen, dass der Auftragstext
nur eine Zusammenfassung mit Schwerpunkten ist. Weicht der Plan vom Auftrag
ab, gilt der Plan — und der Agent soll die Abweichung melden.

### Skill-Namen ohne Namespace-Präfix aufrufen

`ui-ux-pro-max:ui-ux-pro-max` schlägt fehl (`Unknown skill`), `ui-ux-pro-max`
funktioniert. Steht in `FRONTEND_UI.md` falsch — im Auftrag korrekt vorgeben,
sonst verliert jeder Frontend-Agent Zeit damit.

---

## Für den Review-Agenten

### Lint selbst fahren, auch wenn nicht danach gefragt wurde

Siehe oben — genau so wurde der Befund gefunden, der sonst durchgerutscht wäre.

### Neue Regressionstests mutationsprüfen

Wird ein Fehler mit Regressionstests geschlossen: den Fehler **testweise
wieder einbauen** und nachsehen, ob genau diese Tests umfallen. Ein Test, der
die Lücke nur scheinbar schließt, ist hier besonders teuer — die ursprüngliche
Testlücke hat den Fehler ja bereits durchgelassen.

Dabei sauber arbeiten: Sonden und Mutationen danach restlos entfernen und
prüfen, dass der Baum wieder byte-identisch ist.

### Jeder Abschnitt muss für sich baubar sein

Ein Plan, der einen Compilerbruch in Kauf nimmt („Task 2 löscht, Task 3
repariert"), macht den Abschnitts-Review zwangsläufig rot, wenn die beiden in
verschiedenen Abschnitten liegen. Solche Paare gehören in denselben Abschnitt
— oder die Löschung wird verschoben.

### Verhalten messen statt argumentieren

Bei Verdacht auf eine Regression: kleine Wegwerf-Sonde gegen den Stand **vor**
der Änderung und gegen den danach, und die Werte vergleichen. Ein Messwert
(„vorher `null`, nachher `12`, zwei Minuten später immer noch `12`") beendet
die Diskussion, eine Vermutung nicht.

---

## Bekannte Testlücken-Muster

- **Hooks mit veränderlichen Optionen:** Tests, die Optionen nur als
  Startwert setzen und nie per `rerender` ändern, übersehen genau die Fehler
  beim Neu-Armieren. Mindestens ein `rerender`-Test nach einer
  Zwischen-Zeitspanne gehört dazu.
- **Zurücksetzen ohne Neustart:** Ein Test, der nur „ist wieder `null`" prüft,
  ist grün für eine Lösung, die zurücksetzt und danach nichts mehr plant.
  Immer zusätzlich prüfen, dass der neue Zyklus wirklich anläuft.
