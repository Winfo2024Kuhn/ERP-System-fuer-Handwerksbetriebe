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

### Subagent meldet „läuft noch" statt eines Ergebnisses

Ein Reviewer startete `npm run test` im Hintergrund und beendete seine Runde
mit „ich melde mich, wenn die Suite fertig ist". Als Subagent bekommt er die
Benachrichtigung nie — die Ampel wäre ausgeblieben. Das ist keine
Fertigmeldung: per Nachricht an denselben Agenten weitermachen lassen
(synchron im Vordergrund, hohes Timeout), nicht neu starten und nicht als
abgenommen werten. Der Satz „Testläufe synchron im Vordergrund" gehört
trotzdem in jeden Reviewer-Auftrag, nicht nur in die Agenten-Definition.

**Das trifft Coding-Agenten genauso** (real, 08.09.2026): Ein Task startete
`npm run build` im Hintergrund und beendete seine Runde mit „sobald die
Fertigmeldung eintrifft, mache ich weiter" — ohne Commit, ohne Gates, mit
Build-Artefakten im Arbeitsverzeichnis. Die Meldung kam nie.

Regel für **jeden** Auftragstext, Coding wie Review: *Test- und Buildläufe
ausschließlich synchron im Vordergrund mit hohem Timeout. Niemals im
Hintergrund starten und auf eine Benachrichtigung warten — die erreicht dich
als Subagent nicht.* Kommt trotzdem so eine Meldung zurück: **nicht** als
fertig werten und **nicht** neu starten, sondern denselben Agenten per
`SendMessage` weitermachen lassen; er kennt seinen Stand.

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

### `handwerkerprogramm-design`: unscoped scheitert, worktree-scoped funktioniert

Der Design-Skill des Projekts liegt zwar unter `.claude/skills/`, ist im
Skill-Tool aber **nicht registriert** — ein Aufruf endet mit „Unknown skill",
obwohl der Hook `check-doc-read.ps1` vor jedem Frontend-Edit einen Design-Skill
verlangt. Im Auftrag deshalb immer beides vorgeben: den Inhalt von
`handwerkerprogramm-design/SKILL.md` + `README.md` **als Datei lesen** (das ist
der inhaltliche Maßstab), und für den Hook `ui-ux-pro-max` aufrufen (steht in
der Hook-Liste als gültige Alternative). Sonst verliert jeder Frontend-Agent
Zeit mit dem Fehlschlag.

### Skill-Namen ohne Namespace-Präfix aufrufen

`ui-ux-pro-max:ui-ux-pro-max` schlägt fehl (`Unknown skill`), `ui-ux-pro-max`
funktioniert. Steht in `FRONTEND_UI.md` falsch — im Auftrag korrekt vorgeben,
sonst verliert jeder Frontend-Agent Zeit damit.

**Präzisierung (08.09.2026, gemessen):** Arbeitet der Agent in einem
**Worktree**, registriert Claude Code die Projekt-Skills zusätzlich
**scoped auf diesen Worktree** — dann heißt der Aufruf
`wt/<worktree-name>:handwerkerprogramm-design` und **funktioniert**. Der
unscoped Name `handwerkerprogramm-design` scheitert weiterhin.

Für den Auftragstext heißt das: **den scoped Namen vorgeben**, mit dem
Datei-Weg (`SKILL.md` + `README.md` lesen, `ui-ux-pro-max` für den Hook) nur
als Fallback. Der Skill-Aufruf liefert mehr als die Datei — unter anderem die
beiden pixelgenauen UI-Kits (Desktop und Mobile), die man sonst nicht sieht.

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

- **Text-Zusicherung gegen eine Datei mit Kopfkommentar:** Ein Test, der mit
  `datei.contains("spaltenname")` prüft, ob eine Migration eine Spalte anlegt,
  ist grün, sobald der Name **irgendwo** vorkommt — auch nur im Kommentarkopf,
  der ohnehin beschreibt, was die Datei tut. Real gemessen: beide
  `ALTER TABLE`-Blöcke gelöscht, Test blieb grün, obwohl sein Javadoc ihn als
  „einziges Sicherheitsnetz" auswies. Entweder die Kommentarzeilen vor dem
  Assert wegschneiden oder auf die tatsächliche Anweisung prüfen
  (`ADD COLUMN spaltenname`). Und immer gegenprobieren: die Anweisung testweise
  löschen und sehen, ob der Test **wirklich** rot wird.

- **Hooks mit veränderlichen Optionen:** Tests, die Optionen nur als
  Startwert setzen und nie per `rerender` ändern, übersehen genau die Fehler
  beim Neu-Armieren. Mindestens ein `rerender`-Test nach einer
  Zwischen-Zeitspanne gehört dazu.
- **Zurücksetzen ohne Neustart:** Ein Test, der nur „ist wieder `null`" prüft,
  ist grün für eine Lösung, die zurücksetzt und danach nichts mehr plant.
  Immer zusätzlich prüfen, dass der neue Zyklus wirklich anläuft.

### PC-App für Browser-Prüfungen lokal starten: H2 reicht, aber mit Fallen

Für Playwright-Durchläufe mit echten Daten braucht es kein MySQL und kein
Docker: `./mvnw -B spring-boot:run -Dspring-boot.run.profiles=h2` mit
`-Dspring-boot.run.jvmArguments=-Duser.home=<scratchpad>/erp-home`, sonst
legt H2 `~/ERP-Handwerk/` auf dem Rechner an. Das Backend spricht **http**,
der Vite-Proxy in `vite.config.ts` zeigt aber auf `https://localhost:8080`
und scheitert. Nicht die Datei ändern: ein kleines Node-Skript im Scratchpad
startet `createServer` aus `node_modules/vite` mit der echten `configFile`
und überschriebenem Proxy auf eigenem Port. Erstanmeldung über „Konto
anlegen", danach blockt `/onboarding`, bis SMTP, Gemini-Key und Datei-Ordner
per `PUT /api/settings/...` mit Dummy-Werten gesetzt sind (CSRF-Header
`X-XSRF-TOKEN` aus dem Cookie). H2 kennt kein `DATE_SUB`: Kunden nur mit
manueller Kundennummer anlegen; `mwstSatz` ist ein Bruch (0.19). Details in
der Memory-Datei `erp-lokal-starten-h2`.

### Session muss im Repo-Ordner starten, sonst fehlen die Pipeline-Agenten

Fehlerbild: `Agent type 'loese-problem-spec' not found. Available agents:
claude, general-purpose, Explore, ...` — die Liste enthält nur die
eingebauten Agenten, keinen einzigen `loese-problem-*`.

Ursache: Claude Code liest `.claude/agents/`, `.claude/skills/` und
`.claude/commands/` **einmal beim Session-Start** aus dem Arbeitsordner. Wurde
die Session eine Ebene über dem Repo gestartet (z.B. in
`dev/ERP-für-Handwerker` statt in `dev/ERP-für-Handwerker/ERP-System-fuer-Handwerksbetriebe`),
sind die Projekt-Agenten nicht registriert. Ein Junction/Symlink auf `.claude`
nachträglich anzulegen hilft **nicht mehr für die laufende Session** — die
Registrierung ist schon gelaufen.

Regel: Vor Schritt 1 prüfen, ob die Pipeline-Agenten überhaupt aufrufbar sind.
Sind sie es nicht, **nicht** auf `general-purpose` mit hineinkopierter
Rollenbeschreibung ausweichen — der Nutzer will die Pipeline so, wie sie
definiert ist. Stattdessen stoppen und die Session im Repo-Ordner neu starten
lassen. Zwischenergebnisse vorher aus dem Scratchpad in den Repo-Ordner
retten, denn die neue Session bekommt ein anderes Scratchpad-Verzeichnis.

### Verwaiste Dev-Server sperren `node_modules` im Haupt-Checkout

Fehlerbild: `npm run lint` / `npm run test` im Haupt-Checkout bricht ab mit
„Der Befehl 'eslint' ist entweder falsch geschrieben oder konnte nicht gefunden
werden", obwohl `node_modules/eslint` da ist — `node_modules/.bin` ist **leer**.
Und die naheliegende Reparatur `npm ci` scheitert mit
`EPERM: operation not permitted, unlink … @esbuild\win32-x64\esbuild.exe`.

Ursache: Aus einem **früheren Lauf** liefen noch Vite-Dev-Server in einem
Worktree (`wt/review-design/…`). Weil Worktrees ihr `node_modules` per
Symlink/Junction auf das Haupt-Checkout zeigen (siehe oben), sperrt ein
Dev-Server **im Worktree** die Binaries **im Haupt-Checkout**. Der Zusammenhang
ist von außen nicht zu sehen.

Regeln:

- `npm install` kommt durch, wo `npm ci` scheitert — es löscht die gesperrte
  Datei nicht. (`npm rebuild` läuft zwar durch, lässt `.bin` aber leer.)
- Wer einen Dev-Server startet, beendet ihn am Ende seines Tasks. Das gilt
  besonders für den Design-Reviewer.
- Prozesse zu beenden kann der Permission-Classifier blockieren. Dann nicht
  dagegen anrennen: `npm install` reicht, und der Befund gehört ins Kontext-Log.

### Symlink auf `.claude` hilft — aber nur vor dem Session-Start

Präzisierung zum Punkt „Session muss im Repo-Ordner starten": Ein Symlink
`dev/<projekt>/.claude` → `dev/<projekt>/<repo>/.claude`, der **vor** dem
Session-Start existiert, registriert die Projekt-Agenten korrekt, auch wenn die
Session eine Ebene über dem Repo läuft. Nur das **nachträgliche** Anlegen in
einer laufenden Session hilft nicht mehr.

Trotzdem vor Schritt 1 prüfen, ob die `loese-problem-*`-Agenten in der Liste der
verfügbaren Agenten stehen — der Symlink kann fehlen.

### `-Dtest=A+B` läuft nicht — mehrere Testklassen trennt ein Komma

Fehlerbild: Der Gate-Befehl aus dem Plan findet keine oder nur die halben
Tests. Bei Maven Surefire trennt `+` **Methoden innerhalb einer Klasse**,
mehrere Klassen trennt man mit **Komma**:

```
./mvnw -B test -Dtest=ErsteKlasseTest,ZweiteKlasseTest
```

Gehört in den Grobplan-Auftrag: Gate-Befehle mit Komma schreiben. Sonst
stolpert jeder Coding-Agent einzeln darüber (real: in einem Plan sechsmal
falsch vorgegeben).

### `static/index.html` zeigt in jedem frischen Worktree einen Phantom-Diff

Fehlerbild: `git status` im frisch angelegten Worktree meldet
`M src/main/resources/static/index.html`, obwohl niemand sie angefasst hat —
und das in **jedem** Worktree gleichzeitig.

Ursache: reine Zeilenende-Normalisierung (CRLF im Working Copy, LF im Repo,
keine `.gitattributes`-Regel für die Datei). Der Inhalt ist identisch.

Regel: Das ist **kein** Build-Artefakt und kein Befund. Nicht committen, nicht
"reparieren", nicht im Report als Abweichung führen. Ein Satz im Auftragstext
spart dem Agenten die Irritation.

### Charakterisierungstests brechen an der neuen Abhängigkeit, nicht am Verhalten

Fehlerbild: Ein Task stellt einen Service auf eine **neue** Abhängigkeit um
(Constructor Injection). Der Charakterisierungstest, der sein Verhalten
festnageln soll, wird rot — mit `NullPointerException`, nicht mit einer
falschen Zahl. Ursache: Der Test kennt den neuen Konstruktor-Parameter nicht,
Mockito injiziert `null`. Bei `@WebMvcTest` fehlt analog ein `@MockBean`.

Das ist **kein** Verhaltensfehler und **keine** Regression, sondern eine
Verkabelungslücke — der Test ist schlicht nicht mehr lauffähig.

Regel, die in den Auftrag gehört (real gebraucht am 08.09.2026, sonst wären
sechs Tasks einzeln daran gescheitert):

> An deiner Charakterisierungsdatei darfst du die **Verkabelung** anpassen —
> Mock/`@MockBean` ergänzen, Stubs setzen, Konstruktoraufruf nachziehen.
> **Unverändert bleiben:** jede erwartete Zahl, jede erwartete Exception samt
> Meldung, und die Menge der geprüften Fälle.

Zwei Dinge dazusagen, sonst wird das Netz still entschärft:

- **Pro Fixture-Fall den konkreten Wert stubben**, nicht pauschal `any()` auf
  einen festen Rückgabewert. Sonst ist der Test grün, egal was der Code tut.
- **Gegenprobe verlangen:** einen Stub testweise auf einen falschen Wert
  setzen und nachsehen, ob der Test wirklich rot wird.

**Besser noch — beim Planen vermeiden:** Wer Charakterisierungstests plant,
plant sie gegen die **künftige** Konstruktor-Signatur, oder schreibt in den
Task, dass die Verkabelung später angepasst werden darf. Sonst kollidiert die
Vorgabe „diese Datei ist unantastbar" zwangsläufig mit jeder Umstellung, die
sie absichern soll.

### Zwei Maven-Prozesse auf demselben `target/` erfinden Fehler

Fehlerbild: Ein Vollauf meldet plötzlich Hunderte Errors, typischerweise
`class path resource [.../XyzTest.class] cannot be opened because it does not
exist` und massenhaft `Failed to load ApplicationContext`. Es sieht aus wie ein
katastrophaler Regress, ist aber keiner.

Ursache: Ein zweiter Maven-Lauf (Sonde, Mutationsprobe, Gate) lief **gleichzeitig**
im selben Worktree. Beide schreiben in dasselbe `target/`, einer räumt dem
anderen die gerade geladenen Klassen weg.

Regel: Im selben Worktree immer nur **ein** Maven-Prozess. Wer eine Sonde neben
einem Vollauf braucht, wartet den Vollauf ab. Und wer solche Zahlen sieht:
**erst an einen Selbstverschulden denken, bevor der Befund gemeldet wird** —
an der Fehlerart erkennbar (fehlende `.class`-Dateien statt fachlicher
Assertions).

Real passiert am 08.09.2026: 485 gemeldete Errors, nach sauberer Wiederholung
exakt die vier bekannten. Der Reviewer hat es selbst erkannt und offengelegt —
genau richtig, aber es kostet einen kompletten Suite-Lauf.
