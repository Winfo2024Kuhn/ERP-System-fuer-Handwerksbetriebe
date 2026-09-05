# Kontext-Log — PC-App auf 14 Zoll

Plan: `docs/superpowers/plans/2026-09-05-layout-14-zoll.md`
Spec: `docs/superpowers/specs/2026-09-04-layout-14-zoll.md`

Append-only — niemand ändert oder löscht bestehenden Text, jeder hängt nur
unten einen neuen Block an. Lock-Protokoll siehe
`.claude/skills/loese-problem/references/kontext-log-format.md`.

## Baseline (Orchestrator)

Zeit: 2026-09-05T11:45:00Z
Stand: `feature/layout-14-zoll` @ 1fa8f265 (Basis: `origin/claude/eloquent-ramanujan-gz0w2t` @ 89ffc0d5), unverändert, `react-pc-frontend/`
Umgebung: Node 24, `node_modules` als Junction auf `ERP-System-fuer-Handwerksbetriebe/react-pc-frontend/node_modules` (package.json identisch mit main). Playwright-Browser vorhanden. Backend wird nicht angefasst, Backend-Suite ist für dieses Vorhaben kein Gate — der Code-Reviewer prüft stattdessen per `git diff --stat`, dass außerhalb von `react-pc-frontend/` und `docs/` nichts geändert wurde.

- `npm run lint`: 0 Fehler, **1 vorbestehende Warnung** (`src/pages/BelegeKasseEditor.tsx:1204`, react-hooks/exhaustive-deps).
- `npm run test`: 88 Dateien, 1082 Tests. Im Volllauf 13 rote Tests, alle `Test timed out in 5000ms`, alle in unberührten Dateien (ArtikelEditor, BeitraegeTab, EmailCenter, AnfrageEditor, BildEditorModal, document-editor/index, useDatensatzLock); im isolierten Nachlauf dieser Dateien grün bis auf 2 andere, wieder reine 5-s-Timeouts. Ursache: Last durch parallel laufende Agenten auf der Maschine (derselbe Befund wie im Sperr-Lauf, Log dort). **Kein Assertion-Fehler in der Baseline.**
- `npm run build`: grün. Build-Output in `src/main/resources/static/` danach auf HEAD zurückgesetzt.
- `E2E_PORT=5200 npm run test:e2e`: **110 passed**, 0 failed (beide Größen, 5,4 min inkl. Aufwärmen).

Abnahmeregel: grün = Lint 0 Fehler + genau diese 1 Warnung; Unit-Tests ohne Assertion-Fehler (ein 5-s-Timeout in einer unberührten Datei gilt erst als Fehler, wenn er im isolierten Einzellauf der Datei wiederkommt); Build grün; E2E 110 alte Tests + alle neuen grün in beiden Größen.

Branch-Namen: Task-Branches heißen `layout/task-<N>-<kurz>` (nicht `feature/layout-14-zoll/task-N` — Git kann keinen Zweig `a/b` anlegen, wenn `a` schon ein Zweig ist). Worktrees unter `C:/Users/MarvinKuhn/dev/ERP-für-Handwerker/wt/layout-task-<N>`, legt der Orchestrator an (mit `node_modules`-Junction). Ports 5201–5210 je Task, Design-Reviewer 5210+, andere Läufe auf dieser Maschine nutzen 5173–5190.

## Abschnitt 1 — Task 1 (Coding-Agent)

Zeit: 2026-09-05T12:12:59Z
Branch: layout/task-1-design-hilfen
Commit(s): ce0c4fe9
Status: fertig

Was gemacht wurde:
- `react-pc-frontend/e2e/hilfen/design.ts`: `keinHorizontalerUeberlauf` erweitert um (a) `<main>`-Check (`scrollWidth ≤ clientWidth`) und (b) jedes Element mit `overflow-x: hidden`, dessen Inhalt breiter ist (`scrollWidth > clientWidth + 2`); läuft unverändert immer mit, keine neue Option nötig.
- Zwei neue exportierte Prüfungen: `keinTextLaeuftUeber` (Blatt-Elemente mit Text, `scrollWidth ≤ clientWidth + 2`, Elemente mit Breite 0 werden bewusst NICHT übersprungen, `display:none`/`visibility:hidden`/`opacity:0` bleiben draußen) und `keinTextGekuerzt` (Fall a: `text-overflow: ellipsis` mit `scrollWidth > clientWidth + 1`; Fall b: `-webkit-line-clamp` gesetzt mit `scrollHeight > clientHeight + 1`; Ausnahme `data-kuerzung-erlaubt` auf Element oder Vorfahre). Fehlermeldungen deutsch, mit Tag, Klassen und fehlenden Pixeln bzw. vollständigem Text.
- `DesignPruefungOptionen` bekommt `strengePruefungen?: boolean` (Standard `false`, mit Kommentar zur Begründung — bestehende Specs über den Lieferanten-Kopf laufen heute noch über ungefixte Seiten); bei `true` laufen `keinTextLaeuftUeber` und `keinTextGekuerzt` nach den bestehenden Prüfungen in `designPruefung` mit.
- Testgetrieben: `react-pc-frontend/e2e/design-hilfen.spec.ts` (neu, 17 Testfälle) ruft alle drei Prüfungen direkt auf `page.setContent()`-Miniseiten auf, je Fall eine kaputte und eine heile Seite (u. a. Breite-0-Fall aus der Spec, Unsichtbares wird ignoriert, `-webkit-line-clamp` ohne `text-overflow: ellipsis` nach Spec-Korrektur, `data-kuerzung-erlaubt` am Element und am Vorfahren, `strengePruefungen`-Option per `designPruefung`). Erst rot verifiziert (Import-Fehler bzw. fehlende main-/Element-Prüfung), dann grün.

Bedenken / Abweichungen vom Plan:
- **Echter Befund, Prüfung NICHT entschärft (wie angewiesen):** Die Rauchprobe (`E2E_PORT=5201 npx playwright test e2e/bearbeiten-leiste.spec.ts e2e/lieferant-dokument-modal.spec.ts`, Baseline laut Orchestrator-Log: 110/110 grün) dreht **4 von 18 Tests reproduzierbar rot** (mit `--workers=1` deterministisch, beide Bildschirmgrößen): `bearbeiten-leiste.spec.ts` → „Knopf deaktiviert: fehlgeschlagener Erwerb (500) lässt 'Bearbeiten' deaktiviert stehen" (pc-14zoll + pc-monitor) und `lieferant-dokument-modal.spec.ts` → „Fehlerfall (500) beim Öffnen: … 'Bearbeiten' bleibt deaktiviert" (pc-14zoll + pc-monitor). Ursache in beiden Fällen identisch: `react-pc-frontend/src/components/lock/BearbeitenLeiste.tsx` Zeile 145 rendert im Fehler-/Gesperrt-Zustand `<span id={gesperrtGrundId} className="sr-only">{grund}</span>` mit `grund = LOCK_FEHLER_TEXT = "Sperre konnte nicht geholt werden — bitte neu laden."` (53 Zeichen). Tailwinds `.sr-only`-Klasse setzt `width: 1px; overflow: hidden; white-space: nowrap; clip: rect(0,0,0,0)` — der neue, jetzt immer laufende Element-Check aus Task 1 (jedes Element mit `overflow-x: hidden`, dessen Inhalt breiter ist) meldet das als 379px Überstand. Fachlich ist das **kein Layoutfehler**: Der Text ist absichtlich für Screenreader da und auf keiner Bildschirmgröße jemals sichtbar (1×1-px-Kasten, per `clip` weggeschnitten) — anders als bei `display:none`/`visibility:hidden`/`opacity:0` (die `keinTextLaeuftUeber` bereits ausnimmt) kennt der neue `keinHorizontalerUeberlauf`-Zweig diese Ausnahme nicht, weil der Plan-Text dafür keine Ausnahme vorsieht ("jedes Element, dessen overflowX === 'hidden' ist … " ohne Zusatzklausel). Weisungsgemäß (Auftrag: „werden die rot, ist das ein Befund fürs Kontext-Log, nicht ein Grund, die Prüfung zu entschärfen") habe ich die Prüfung **nicht** angepasst und **nicht** `BearbeitenLeiste.tsx`/`dialog.tsx` angefasst (außerhalb meiner Files-Liste). Projektweit nutzen 9 Dateien die `.sr-only`-Klasse (`grep -rl sr-only src/`), das Risiko weiterer False Positives in späteren Tasks/im vollen E2E-Lauf ist also nicht auf diese zwei Dateien begrenzt. Empfehlung für den Orchestrator/Design-Reviewer: prüfen, ob `keinHorizontalerUeberlauf` eine Ausnahme für das Standard-„visually-hidden-für-Screenreader"-Muster (z. B. `width ≤ 1px` **und** `height ≤ 1px` **und** `overflow: hidden`) bekommen soll, oder ob das bewusst so bleibt und stattdessen `BearbeitenLeiste.tsx`/`dialog.tsx` einen anderen Ausblendungs-Mechanismus bekommen.
- Bei parallelem Lauf (Standard-`--workers`, 4 parallel) zeigten sich zusätzlich 3 weitere, **nicht reproduzierbare** Rot-Ausschläge in `bearbeiten-leiste.spec.ts` (Lesen-Modus, Countdown, Verbindung weg) mit derselben `span.sr-only`-Meldung — bei `--workers=1` verschwinden sie vollständig und deterministisch. Wirkt wie Nebenwirkung paralleler Last auf dieser Maschine (andere Agenten laufen mit), nicht wie ein eigenständiger zweiter Befund; nicht weiter verfolgt, da der Auftrag nur die eigene Änderung testen lässt, keine Ursachenforschung an fremden Timing-Effekten.
- Sonst keine Abweichungen vom Plan-Block (Global Constraints + Task 1 vollständig gelesen und umgesetzt wie beschrieben).

Ergebnis der Gates:
- Port-Check: `netstat -ano | findstr :5201` leer, Port 5201 frei verwendet.
- `E2E_PORT=5201 npx playwright test e2e/design-hilfen.spec.ts` (beide Projekte): **34/34 grün** (17 Testfälle × pc-14zoll + pc-monitor).
- `E2E_PORT=5201 npx playwright test e2e/bearbeiten-leiste.spec.ts e2e/lieferant-dokument-modal.spec.ts`: **14/18 grün, 4 rot** (siehe Bedenken oben) — mit `--workers=1` deterministisch reproduziert.
- `npm run lint`: 0 Fehler, 1 vorbestehende Warnung (`BelegeKasseEditor.tsx:1204`, wie Baseline) — unverändert.
- `npm run build`: grün. Build-Output (`src/main/resources/static/index.html`, `assets/*`) vor dem Commit verworfen (`git checkout -- src/main/resources/static` + `git clean -f src/main/resources/static/assets`).

## Abschnitt 1 — Task 1, Nachbesserung 1 (Coding-Agent)

Zeit: 2026-09-05T12:23:37Z
Branch: layout/task-1-design-hilfen
Commit(s): baaf16b5
Status: fertig

Was gemacht wurde:
- Nachbesserung zum Befund aus dem ersten Task-1-Block (4 rote Rauchproben durch `span.sr-only`): in `react-pc-frontend/e2e/hilfen/design.ts` bekommen `keinHorizontalerUeberlauf` (generische `overflow-x: hidden`-Schleife), `keinTextLaeuftUeber` und `keinTextGekuerzt` je eine neue Ausnahme `istUnsichtbarVersteckt(el)`: Element selbst oder ein Vorfahre ist faktisch 1×1 px (`clientWidth <= 1 && clientHeight <= 1`) **und** nicht `overflow: visible` (oder `clip: rect(0px, 0px, 0px, 0px)` / gesetztes `clip-path`). Trifft das zu, wird das Element/sein Nachfahre von allen drei Prüfungen ignoriert — Tailwinds `.sr-only`-Muster kann auf keiner Bildschirmgröße etwas Sichtbares abschneiden.
- Ausdrücklich NICHT dasselbe wie „Breite 0": ein 0 px breiter, aber hoher und nicht abgeschnittener Kasten (Kennzahl-Kasten aus Spec-Befund 2) bleibt weiterhin ein Befund, weil `clientHeight` dort > 1 ist und nichts geklippt wird.
- Testgetrieben in `react-pc-frontend/e2e/design-hilfen.spec.ts`: vier neue rote-dann-grüne Fälle für das sr-only-Muster (je eine direkte Prüfung pro Funktion, dazu ein Fall mit `<b>` als verschachteltem Kind, um zu belegen, dass die Vorfahren-Suche wirklich gebraucht wird — ohne Ausnahme hätte `<b>` selbst angeschlagen), plus ein Abgrenzungstest (0 px breiter/16 px hoher Text in 26-px-Kasten mit `overflow: visible` bleibt roter Befund). Erst rot verifiziert (4 Fehlschläge, exakt wie erwartet), dann grün nach der Implementierung.

Bedenken / Abweichungen vom Plan:
- keine.

Ergebnis der Gates:
- Port-Check: `netstat -ano | findstr :5201` zeigt nur den eigenen, seit dieser Sitzung laufenden Dev-Server (kein fremder Prozess).
- `E2E_PORT=5201 npx playwright test e2e/design-hilfen.spec.ts` (beide Projekte): **44/44 grün** (22 Testfälle × pc-14zoll + pc-monitor).
- `E2E_PORT=5201 npx playwright test e2e/bearbeiten-leiste.spec.ts e2e/lieferant-dokument-modal.spec.ts --workers=1`: **18/18 grün** — der Befund aus dem ersten Block ist behoben.
- `npm run lint`: 0 Fehler, 1 vorbestehende Warnung (`BelegeKasseEditor.tsx:1204`, unverändert wie Baseline).
- `npm run build`: grün. Build-Output vor dem Commit verworfen (`git checkout -- src/main/resources/static` + `git clean -f src/main/resources/static/assets`).
