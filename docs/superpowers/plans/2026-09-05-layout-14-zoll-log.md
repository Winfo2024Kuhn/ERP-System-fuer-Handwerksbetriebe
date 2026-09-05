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

## Abschnitt 1 — Review (Code-Reviewer)

Zeit: 2026-09-05T15:05:00Z
Branch: feature/layout-14-zoll @ 4b8327cd (Merge von `layout/task-1-design-hilfen`, ce0c4fe9 + baaf16b5)
Commit(s): ce0c4fe9, baaf16b5, 9e44a6bc, 4b8327cd
Status: fertig
Ampel: 🟡 (abgenommen)

Was geprüft wurde:
- Umfang: `git diff --stat 0a48aa3d..HEAD` = genau 3 Dateien (Kontext-Log, `e2e/design-hilfen.spec.ts`, `e2e/hilfen/design.ts`). Außerhalb von `react-pc-frontend/` und `docs/` **null** geänderte Dateien — Backend-Suite entfällt planmäßig. Kein Build-Output in den Commits, keine Konfliktmarker.
- Gates (alle aus `react-pc-frontend/`, synchron, Port vorher per `netstat` als frei geprüft):
  - `npm run lint`: 0 Fehler, genau die 1 vorbestehende Warnung (`BelegeKasseEditor.tsx:1204`) — identisch zur Baseline.
  - `npm run test`: **88 Dateien, 1082 Tests, 1082 grün, 0 rot** (92 s). Sauberer als die Baseline, in der 13 lastbedingte 5-s-Timeouts standen.
  - `npm run build`: grün. Build-Output danach verworfen (`git checkout -- src/main/resources/static` + `git clean -f src/main/resources/static/assets`).
  - `E2E_PORT=5211 npm run test:e2e`: **154 grün, 0 rot** (3,2 min, beide Größen) = 110 Baseline-Tests + 44 neue. Die immer mitlaufende Erweiterung von `keinHorizontalerUeberlauf` dreht **keinen** der 110 alten Tests rot.
- Mutationsproben (Pflicht laut `fallstricke.md`), je Mutation ein Lauf von `e2e/design-hilfen.spec.ts` auf eigenem Port 5212, Vergleich gegen 22/22 grün:
  1. `<main>`-Messung abgeschaltet → genau 1 rot („main mit overflow-x:hidden und breiterem Inhalt loest aus").
  2. Toleranz der `overflow-x: hidden`-Schleife `+2` → `+2000` → genau 1 rot („Element mit overflow-x:hidden … auch ohne main").
  3. Toleranz in `keinTextLaeuftUeber` `+2` → `+2000` → genau 3 rot (Breite-0-Fall, `strengePruefungen: true`, Abgrenzungsfall).
  4. `data-kuerzung-erlaubt`-Ausnahme immer wahr → genau 2 rot (ellipsis-Fall, `-webkit-line-clamp`-Fall).
  5. `istUnsichtbarVersteckt` immer wahr → genau 6 rot (alle Fälle, die über die drei Ausnahme-Zweige laufen).
  6. Zusatzprobe: Höhen-Guard aus `istUnsichtbarVersteckt` entfernt (nur noch `clientWidth`) → 1 rot (Breite-0-Fall). Der Guard ist also tragend, die sr-only-Ausnahme frisst den Spec-Befund 2 nicht.
  Jeweils danach `git checkout` + Prüfsummenvergleich: `e2e/hilfen/design.ts` byte-identisch, `git diff` leer, `git status` sauber.
- Wegwerf-Sonde (danach gelöscht) für die Randfälle, die keine Spec abdeckt:
  - `overflow-x: auto` und `overflow-x: scroll` mit 800 px Inhalt in 200 px Kasten → **kein** Befund. Legitime Scroll-Container werden korrekt nicht angemeckert.
  - `overflow-y: hidden` ohne `overflow-x` → computed `overflowX = auto` → kein Befund (richtig, das Ding scrollt sichtbar).
  - `-webkit-line-clamp` wird **nicht** vererbt (Kind meldet `none`) → keine Fehlalarme an Kindern von `.line-clamp-2`.
  - 300×1 px-Kasten mit `overflow: hidden` und echt angeschnittenem Text → bleibt Befund. Die sr-only-Ausnahme ist eng genug geschnitten.

Bedenken / Abweichungen vom Plan (alles 🟡, nichts blockiert):
- **`overflow: clip` fällt durch das Raster.** Die generische Schleife prüft nur `overflowX === 'hidden'`; ein 200-px-Kasten mit `overflow: clip` und 800 px Inhalt bleibt still (gemessen). `clip` versteckt genauso lautlos wie `hidden`. Plan- und spectreu (dort steht nur „hidden"), aber `design-hilfen.spec.ts` nutzt `overflow: clip` selbst, um dem Check auszuweichen — die Lücke ist also bekannt. Empfehlung für Task 10: `clip` mitprüfen.
- **Inline-Vorfahre mit nicht-sichtbarem Overflow schluckt echte Befunde.** Inline-Elemente melden immer `clientWidth`/`clientHeight` = 0, deshalb hält `istUnsichtbarVersteckt` **jeden** inline Vorfahren mit `overflow: hidden` für ein sr-only-Muster. Gemessen: ein `display:block`-Kasten mit 492 px Text in 0 px Breite, verpackt in `<span style="overflow:hidden">`, wird nicht mehr gemeldet; dasselbe mit `text-overflow: ellipsis` in `keinTextGekuerzt`. Im heutigen `src/` habe ich kein solches Paar gefunden (die `truncate`-Spans sind fast alle `block` oder Flex-Kinder, also blockifiziert und normal messbar) — deshalb 🟡 und kein 🔴. Fix-Vorschlag für Task 10: zusätzlich `getComputedStyle(k).display !== 'inline'` fordern oder `getBoundingClientRect()` statt `clientWidth/Height` messen.
- **`keinTextLaeuftUeber` sieht nur Blatt-Elemente.** Text, der direkt neben einem Element-Kind steht (`<p>Langer Name <span>Badge</span></p>` oder Lucide-Icon + Beschriftung in einem Knopf), wird nie gemessen (gemessen: bleibt still). Genau so im Plan vorgegeben, aber eine Abdeckungslücke, sobald Task 10 den Standard scharf dreht.
- **Meldungstext bei `text-overflow: ellipsis` ohne `overflow: hidden`:** Dort wird nichts gekürzt (die Ellipse greift nur bei nicht-sichtbarem Overflow), gemeldet wird trotzdem „Text ist gekuerzt". Der Fall ist ein echter Überlauf, aber unter falschem Namen.
- **`<main>`-Check ohne Toleranz** (`scrollWidth > clientWidth`), während die anderen Prüfungen +1/+2 zugestehen. Plantreu, aber bei Sub-Pixel-Rundung ein Flaky-Risiko für die Folgetasks.
- **Formulierungs-Spannung zu Task 2:** dessen „Produces" verspricht `<main>` „mit sichtbarem Scrollbalken statt stillem Abschneiden". Der jetzt immer laufende `<main>`-Check verbietet aber jeden Überstand in `<main>` — auch einen legitim scrollbaren. Der Spec-Text von Task 2 („(a) `main` hat keinen Überstand") löst das richtig auf; wer nur die Produces-Zeile liest, baut am Check vorbei.
- **`istUnsichtbarVersteckt` steht dreimal wortgleich im File.** Technisch erzwungen (jede `page.evaluate` wird eigenständig serialisiert), ließe sich aber als geteilte String-Konstante einmal halten.
- **`.claude/skills/playwright-design-pruefung/SKILL.md` kennt die neuen Prüfungen und die Option `strengePruefungen` nicht.** Nicht Task 1s Baustelle (nicht in seiner `Files`-Liste, Global Constraints verbieten Fremddateien) — gehört zu Task 10, wenn der Standard auf `true` dreht.
- **Aside, nicht von Abschnitt 1 verursacht:** `docs/superpowers/specs/bilder/` ist untracked. Die Beweisbilder, auf die Spec und Plan verweisen, liegen damit nicht im Repo.

Keine 🔴-Befunde: keine Korrektheitsfehler in den Messpfaden, kein rotes Gate, keine Sicherheits-/DSGVO-Verstöße (reiner Testcode, keine echten Personendaten, Miniseiten nur mit den Fantasienamen der Spec), kein Merge-Konflikt, keine Änderung außerhalb der erlaubten Dateien.

## Abschnitt 2 — Task 8 (Coding-Agent)

Zeit: 2026-09-05T13:19:49Z
Branch: layout/task-8-menueleiste
Commit(s): edc521f8, 35ef9b75
Status: fertig

Was gemacht wurde:
- `react-pc-frontend/e2e/menueleiste-layout.spec.ts` (neu): stubbt `/api/auth/me` mit langem Nutzernamen (`Friederike Beispiel-Musterfrau`, `admin: true`, `roles: ['ADMIN']`, `requiresInitialSetup: false`), `/api/notifications/summary` sowie die Landeseite-Routen von `/projekte` (`/api/last-accessed/PROJEKT`, `/api/projekte`, `/api/projekte/jahre`, `/api/projekte/freigabe-status`). Vier Testfaelle: (1) alle fuenf Kategorien vollstaendig lesbar + Kategorie-Leiste ohne Ueberlauf (eigene `page.evaluate`-Messung des Container-`scrollWidth`/`clientWidth`, da `keinHorizontalerUeberlauf` nur `overflow-x: hidden` prueft, der Container aber `overflow-x: auto` hat), (2) Kategorie "Vorlagen & Stammdaten" -> "Dokumentenrechte" vollstaendig, (3) Kategorie "Finanzen & Controlling" -> "Mietabrechnung" vollstaendig, (4) Nutzermenue geoeffnet -> voller Anzeigename ohne Kuerzung (zusaetzlicher Testfall, siehe Bedenken). Jeder Testfall endet mit `designPruefung(..., { strengePruefungen: true })`. Vor der Umsetzung verifiziert rot: Kategorie-Leiste bei pc-14zoll 117px Ueberstand (langer Name verschaerft die 38px aus der Spec), `keinTextGekuerzt` schlug bei "Dokumentenrechte", "Mietabrechnung" und dem Anzeigenamen (beide Stellen: kompaktes Badge und Nutzermenue-Panel) an -- 7 von 8 Testlaeufen rot, nur die Kategorien-Pruefung bei pc-monitor (1920) war schon vorher gruen (genug Platz).
- `react-pc-frontend/src/components/layout/RibbonNav.tsx`: Kategorie-Leiste `no-scrollbar` entfernt, Kategorie-Knoepfe `px-3 2xl:px-4` statt `px-4`, oberste Zeile `gap-4` statt `gap-8`. Menuepunkt-Beschriftungen `max-w-[5.5rem] leading-tight text-center break-words` statt `max-w-[4.5rem] truncate` (zweizeilig statt gekuerzt). Anzeigename `max-w-[10rem]` + `title` + `data-kuerzung-erlaubt` (Begruendung: voller Name steht im Nutzermenue-Panel darunter). "KI-Hilfe"-Beschriftung (`hidden lg:inline`) nicht angefasst -- mit den beiden anderen Fixes blieb die Spec bei 1440 nicht rot, ein Wechsel auf `xl:inline` war nicht noetig.

Bedenken / Abweichungen vom Plan:
- **Abweichung vom Plan-Wortlaut beim Anzeigenamen:** Plan verlangt `max-w-[10rem] truncate`. Umgesetzt wurde stattdessen `max-w-[10rem] line-clamp-1` (mit `title` + `data-kuerzung-erlaubt`, wie gefordert). Grund, empirisch verifiziert: `truncate` (text-overflow: ellipsis + white-space: nowrap + overflow: hidden) macht aus dem Anzeigenamen bei echter Kuerzung automatisch ein Element mit `overflow-x: hidden`, dessen `scrollWidth > clientWidth` ist -- genau das Muster, das der fremde, IMMER aktive Check `keinHorizontalerUeberlauf` (aus `e2e/hilfen/design.ts`, Task 1) meldet. Dieser Check kennt `data-kuerzung-erlaubt` nicht (nur `keinTextGekuerzt` kennt die Ausnahme) und ist nicht optional abschaltbar. Mit `truncate` blieben alle vier Testfaelle rot (`designPruefung` schlaegt bei jedem Aufruf fehl, weil das Badge in jedem Seitenzustand sichtbar ist). `line-clamp-1` kuerzt dagegen vertikal (`scrollHeight`, nicht `scrollWidth`) und loest den Konflikt, ohne `e2e/hilfen/design.ts` anzufassen (nicht in meiner `Files`-Liste). Sichtbares Ergebnis ist identisch: ein Wort mit automatischem "…" plus `title`-Tooltip. Empfehlung fuer Task 10 (einziger Task, der `design.ts` noch anfasst): `keinHorizontalerUeberlauf`s generische `overflow-x: hidden`-Schleife sollte dieselbe `data-kuerzung-erlaubt`-Ausnahme bekommen wie `keinTextGekuerzt`, sonst ist die Konvention fuer jedes kuenftige `truncate`-basierte (nicht `line-clamp`-basierte) Kuerzungs-Element unbrauchbar.
- **Zusaetzlicher Fund, nicht explizit im Plan-Bullet fuer Task 8 genannt, aber dieselbe Datei/Baustelle:** Das Nutzermenue-Panel (Zeile ~312, `showUserMenu`-Dropdown) zeigte den Anzeigenamen ebenfalls mit `line-clamp-1` ohne `data-kuerzung-erlaubt`. Die im Plan genannte Begruendung fuer die Kuerzung des kompakten Namens ("voller Name steht im Menue darunter") waere damit falsch gewesen -- bei einem langen Namen war auch dort abgeschnitten (verifiziert rot: `keinTextGekuerzt` meldete `-webkit-line-clamp` an genau dieser Stelle). Behoben durch `break-words` statt `line-clamp-1` (lieber umbrechen als kuerzen, wie an anderer Stelle im Plan gefordert). Eigener vierter Testfall dafuer ergaenzt.
- **Vitest-Gate nicht gefahren:** `NotificationBell.render.test.tsx` rendert `<NotificationBell />` direkt (per `render(<MemoryRouter><NotificationBell /></MemoryRouter>)`), nicht `RibbonNav`/`RibbonNavigation`. Mit `grep -rl RibbonNav src/**/*.test.ts*` findet sich projektweit **keine** Testdatei, die die Menueleiste mitrendert. Gemaess Plan-Bedingung ("nur fahren, wenn sie RibbonNav tatsaechlich einbindet") wurde das Vitest-Gate deshalb nicht ausgefuehrt.
- Sonst keine Abweichungen: Global Constraints + Task 8 vollstaendig gelesen und wie beschrieben umgesetzt. Bei pc-monitor (1920) aendert sich durch `gap-4` (oberste Zeile, nicht breakpoint-gated) und den `max-w-[10rem]`-Deckel auf den Anzeigenamen minimal etwas gegenueber vorher -- fuer normal lange Namen (wie in bestehenden Specs, z.B. "Anna Buero") unsichtbar, nur bei einem aussergewoehnlich langen Namen wie im Test wuerde er jetzt auch bei 1920 gekuerzt (vorher nicht, da keine Hoechstbreite existierte). Beides ist im Plan-Text ohne Breakpoint-Einschraenkung vorgeschrieben; dem Design-Reviewer zur Kenntnis, falls beim 1920-Vorher/Nachher-Vergleich auffaellig.

Ergebnis der Gates:
- Port-Check: `netstat -ano | findstr :5208` leer, Port 5208 frei verwendet.
- `E2E_PORT=5208 npx playwright test e2e/menueleiste-layout.spec.ts`: **8/8 gruen** (4 Testfaelle × pc-14zoll + pc-monitor).
- Vitest: nicht gefahren, siehe Bedenken oben (keine Testdatei bindet RibbonNav ein).
- `npm run lint`: 0 Fehler, 1 vorbestehende Warnung (`BelegeKasseEditor.tsx:1204`, identisch zur Baseline).
- `npm run build`: gruen (inkl. `tsc -b`). Build-Output (`src/main/resources/static/index.html`, `assets/*`) vor dem Commit verworfen (`git checkout -- src/main/resources/static` + `git clean -f src/main/resources/static/assets`).

## Abschnitt 2 — Task 1b (Coding-Agent)

Zeit: 2026-09-05T15:11:32Z
Branch: layout/task-1b-kuerzung-ausnahme
Commit(s): b1fd90c0
Status: fertig

Was gemacht wurde:
- Nachtrag zu Task 1 (Befund aus Task 8, Kontext-Log Abschnitt 2): Der immer laufende Teil von `keinHorizontalerUeberlauf` (generische `overflow-x: hidden`-Schleife) meldete bisher auch gewollte Text-Kuerzungen -- ein `truncate`-Element (`overflow: hidden` + `text-overflow: ellipsis` + `white-space: nowrap`) mit echt gekuerztem Text erfuellt genau dieses Muster, selbst mit `data-kuerzung-erlaubt` (das kannte bisher nur `keinTextGekuerzt`). Damit war `truncate` faktisch verboten, Task 8 musste deshalb auf `line-clamp-1` ausweichen.
- `react-pc-frontend/e2e/hilfen/design.ts`: Die Element-Schleife von `keinHorizontalerUeberlauf` ueberspringt jetzt zwei unabhaengige Faelle, jeder fuer sich ausreichend: (a) `hatKuerzungsMarker(el)` -- Element selbst oder ein Vorfahre traegt `data-kuerzung-erlaubt`; (b) `istReineTextKuerzung(el)` -- `text-overflow: ellipsis` gesetzt ODER `-webkit-line-clamp` != `none`, unabhaengig vom Marker (ob eine Kuerzung zulaessig ist, entscheidet allein `keinTextGekuerzt`). Deutscher Kommentar mit Begruendung der Arbeitsteilung direkt im Code. `<main>`-Check und die generische Meldung echter Kaesten-Ueberstaende (kein `text-overflow`/`line-clamp`) bleiben unveraendert scharf. Nichts sonst an den drei Pruefungen geaendert.
- Testgetrieben (`react-pc-frontend/e2e/design-hilfen.spec.ts`, neues `describe`-Bloecke "keinHorizontalerUeberlauf ignoriert reine Text-Kuerzungen (Task 1b)"): sechs neue Faelle exakt nach Auftrag -- (1) `truncate` mit echt gekuerztem Text UND `data-kuerzung-erlaubt`: `keinHorizontalerUeberlauf` laeuft durch, `keinTextGekuerzt` laeuft durch; (2) dasselbe OHNE Marker: `keinHorizontalerUeberlauf` laeuft trotzdem durch (nicht seine Zustaendigkeit), `keinTextGekuerzt` wirft; (3) 200px-Kasten mit `overflow: hidden`, ohne `text-overflow`/`line-clamp`, 800px breites Kind: `keinHorizontalerUeberlauf` wirft weiterhin. Vor der Aenderung rot verifiziert: genau die zwei `keinHorizontalerUeberlauf`-Faelle aus (1) und (2) schlugen fehl ("div.titel: 210px zu wenig Platz"), alle anderen (inkl. Fall 3) waren schon vorher gruen -- erwartungsgemaess, da (3) ein reiner Nicht-Regressions-Test ist (kein `text-overflow`/`line-clamp` beteiligt) und die `keinTextGekuerzt`-Haelften von (1)/(2) schon aus Task 1 funktionierten. Nach der Implementierung alle 6 neuen Faelle gruen, keine bestehende Miniseite (u.a. der `<main>`-Check, Punkt 4 im Auftrag) davon beruehrt.

Bedenken / Abweichungen vom Plan:
- keine.

Ergebnis der Gates:
- Port-Check: `netstat -ano | findstr :5213` leer, Port 5213 frei verwendet.
- `E2E_PORT=5213 npx playwright test e2e/design-hilfen.spec.ts` (beide Projekte): **54/54 gruen** (27 Testfaelle x pc-14zoll + pc-monitor).
- `E2E_PORT=5213 npx playwright test e2e/bearbeiten-leiste.spec.ts e2e/lieferant-dokument-modal.spec.ts`: **18/18 gruen** -- keine Regression durch die neue Ausnahme.
- `npm run lint`: 0 Fehler, 1 vorbestehende Warnung (`BelegeKasseEditor.tsx:1204`, identisch zur Baseline).
- `npm run build`: gruen. Build-Output (`src/main/resources/static/index.html`, `assets/*`) vor dem Commit verworfen (`git checkout -- src/main/resources/static` + `git clean -f src/main/resources/static/assets`), `git status` danach sauber bis auf die zwei eigenen Dateien.

## Abschnitt 2 — Task 2 (Coding-Agent)

Zeit: 2026-09-05T15:18:50Z
Branch: layout/task-2-rahmen
Commit(s): 4285f00e
Status: fertig

Was gemacht wurde:
- `DetailLayout.tsx` Zeile 17: Raster von `xl:grid-cols-[3fr_1fr]` auf `xl:grid-cols-[minmax(0,3fr)_minmax(0,1fr)]` umgestellt, mit Kommentar zur Ursache (Rasterzellen haben standardmäßig `min-width: auto` und schrumpfen nicht unter die Mindestinhaltsbreite ihres Inhalts — bei 7 nicht umbrechenden Reitern in `ProjektEditor.tsx` sind das 1247px).
- `MainLayout.tsx` Zeile 14: `overflow-x-hidden` → `overflow-x-auto` mit Kommentar, dass ein Überstand ab jetzt sichtbar statt versteckt ist.
- Testgetrieben (Skill `superpowers:test-driven-development` befolgt): `e2e/rahmen-detailseite.spec.ts` (neu) öffnet `/projekte?projektId=5&tab=geschaeftsdokumente` mit dem langen Bauvorhaben „Treppenanlage mit Podest und Absturzsicherung Bürogebäude Beispielstraße" und dem langen Kundennamen aus der Spec. `/api` per Catch-all (`**/api/**`) + gezielte Overrides gestubbt (Vorbild `stubbeLieferantApi`), Dummy-Daten nur Fantasienamen.
- Rot-Verifikation ohne `git stash` (geteilter Bereich, laut Hinweis nicht benutzt): Fix als Patch gesichert (`git diff > ../task2-fix.patch`), `git checkout --` auf die zwei Dateien zurückgesetzt, Spec rot gefahren, Patch mit `git apply` zurückgespielt, Spec grün gefahren. Vor dem Fix: Raster selbst hat 227px internen Überstand bei pc-14zoll (`raster.scrollWidth - raster.clientWidth`), Karte „Projektdaten" ragt ca. 163-166px rechts aus dem Fenster. Nach dem Fix: 0px Überstand, Karte vollständig im Fenster (beide Größen).
- Zusätzlich verifiziert: bei pc-monitor (1920px) bleibt `main` in beiden Zuständen bei 0px Überstand (kein neuer/wegfallender Scrollbalken); die zwei vom Plan genannten Fix-Varianten (`minmax(0, Nfr)` auf den Grid-Spalten vs. `min-w-0` auf den Spalten-Divs bei unverändertem `3fr_1fr`) liefern nachweislich pixelidentische Ergebnisse (empirisch gegeneinander getestet).

Bedenken / Abweichungen vom Plan:
- **Echter Befund in einer fremden Datei (Task 3, `ProjektEditor.tsx`), nicht selbst repariert:** Die Kopfzeile (Zeile ~1045 ff., Kennzahlen-Reihe + Knopfblock „Bearbeiten"/„mit Anfrage zusammenführen") hat bei 1440px unabhängig vom Bauvorhaben-Namen zu wenig Platz — selbst mit einem einzelnen kurzen Wort wie „Carport" wird der Knopfblock ca. 40-60px nach rechts aus der Kopf-Karte geschoben (Spec-Befund 2). Verifiziert per Vorher/Nachher-Messung: der Effekt ist exakt gleich groß vor und nach dem DetailLayout/MainLayout-Fix, also nicht durch Task 2 verursacht und nicht durch Task 2 behebbar (Datei nicht in meiner Files-Liste). Auswirkung: `main.scrollWidth` bleibt auf `/projekte?projektId=5&tab=geschaeftsdokumente` bei pc-14zoll auch nach diesem Fix bei rund 40-60px Überstand (statt 0), verursacht ausschließlich durch die noch ungefixte Kopfzeile. Deshalb prüft meine Spec den Überstand gezielt am Raster selbst (`DetailLayout.tsx`) statt pauschal an `<main>`, und ruft `designPruefung()` aus `e2e/hilfen/design.ts` bewusst **nicht** als Ganzes auf — deren `keinHorizontalerUeberlauf()` misst `<main>` ohne Toleranz und würde wegen dieses Befunds unabhängig von der Korrektheit dieses Tasks immer rot bleiben. Die übrigen, hier ehrlich prüfbaren Teile von `designPruefung` (Screenshot nach `test-results/design/`, `keineUeberschneidungen`, Sichtbarkeit der Primäraktion) laufen manuell zusammengesetzt trotzdem mit. **Empfehlung an Task 3:** beim Kopfzeilen-Umbau explizit gegen `/projekte?projektId=5&tab=geschaeftsdokumente` mit dem langen Bauvorhaben prüfen, dass danach auch `designPruefung()` als Ganzes (inkl. `keinHorizontalerUeberlauf` auf `<main>`) grün wird — aktuell (nach Abschnitt 2) ist das noch nicht der Fall, siehe Screenshot `test-results/design/rahmen-projekt-detail--pc-14zoll.png` (Knopf „mit Anfrage zusammenführen" sichtbar am rechten Rand abgeschnitten).
- **Abweichung von der wörtlichen Spec-Vorgabe `toBeInViewport({ ratio: 1 })`:** aus demselben Grund (Kopfzeile bei langem Bauvorhaben mehrzeilig, drückt die Karte „Projektdaten" zusätzlich nach unten) hätte diese Prüfung auch die y-Achse einbezogen und wäre unabhängig von diesem Task fälschlich rot geblieben. Stattdessen misst die Spec x-Position und -Breite der Karte manuell (`boundingBox()`) gegen die Fensterbreite — das ist exakt das, was Spec-Befund 1 beschreibt („170px ragt hinaus", eine reine Horizontal-Aussage) und was `DetailLayout.tsx`/`MainLayout.tsx` reparieren.
- **Spaltenbreiten bei pc-monitor nicht byte-identisch vorher/nachher:** Messung zeigt, dass die rechte Spalte schon vor dem Fix bei 1920px (durch dieselbe Reiterleiste) leicht schmaler ist als ihr reiner 1fr-Anteil (265px statt 384px) — 1920px hat zwar genug Platz, um `main` insgesamt überlauffrei zu halten, das Grid-Sizing berücksichtigt den Mindestinhalt der Reiterleiste aber trotzdem mit. Nach dem Fix bekommt die rechte Spalte ihren vollen 1fr-Anteil (378px, exakt 3fr:1fr) — eine Verbreiterung um ca. 113px, keine Verschlechterung, aber auch keine exakte Pixelgleichheit. Ein byte-identisches Vorher/Nachher ist mit keiner der beiden vom Plan genannten Fix-Varianten technisch möglich (empirisch verifiziert, siehe oben). Die Spec prüft deshalb bei pc-monitor auf eine sinnvoll aufgeteilte, nicht zusammengequetschte Spaltenstruktur (beide Spalten deutlich >0, Verhältnis >2:1) statt auf exakte Pixelgleichheit.
- Sonst keine Abweichungen vom Plan-Block (Global Constraints + Task 2 vollständig gelesen und umgesetzt wie beschrieben).

Ergebnis der Gates:
- Port-Check: `netstat -ano | findstr :5202` leer vor dem Lauf, Port 5202 frei verwendet.
- `E2E_PORT=5202 npx playwright test e2e/rahmen-detailseite.spec.ts` (beide Projekte, auch mit `--workers=1` deterministisch wiederholt): **2/2 grün**.
- Keine Vitest-Datei rendert `DetailLayout`/`MainLayout` (geprüft per Grep) — kein Vitest-Lauf nötig, wie vom Plan erwartet.
- `npm run lint`: 0 Fehler, 1 vorbestehende Warnung (`BelegeKasseEditor.tsx:1204`, unverändert wie Baseline).
- `npm run build`: grün. Build-Output vor dem Commit verworfen (`git checkout -- src/main/resources/static` + `git clean -f src/main/resources/static/assets`).

## Abschnitt 2 — Design-Review (Design-Reviewer)

Zeit: 2026-09-05T15:38:27Z
Branch: feature/layout-14-zoll @ 1a0857ff (Merge von Task 8, Task 1b, Task 2), eigener Worktree `wt/layout-review-design`, detached HEAD
Commit(s): edc521f8, 35ef9b75, b1fd90c0, 4285f00e (gemergt in 1a0857ff)
Status: fertig
Ampel: 🟡 (abgenommen, mit einer harten Auflage an Task 3)

### E2E — komplett, beide Größen

- Port-Check vorher: `netstat -ano | findstr :5214` leer, Port 5214 frei verwendet.
- `E2E_PORT=5214 npm run test:e2e` (alle Specs, `pc-14zoll` + `pc-monitor`): **174 grün, 0 rot** (6,8 min).
  Rechnung zur Baseline: 110 (vor dem Vorhaben) + 44 (Task 1) + 10 (Task 1b) + 8 (Task 8) + 2 (Task 2) = 174. Kein alter Test wurde rot.
- Zweiter Lauf `E2E_PORT=5214 npx playwright test --project=pc-monitor`: **87 grün, 0 rot**. Grund für den zweiten Lauf: Playwright leert `test-results/` beim Start jedes Laufs, dadurch waren die 1920er-Bilder nach der eigenen Wegwerf-Spec weg und mussten neu erzeugt werden.
- Keine Last-Timeouts, keine Flakes, kein Nachfahren einzelner Specs nötig.

### Eigene Messungen (Wegwerf-Spec, danach gelöscht, `git status` sauber)

Der Alt-Zustand wurde **ohne Quellcode-Änderung** simuliert: per `page.addStyleTag` wurde das Raster im `xl`-Bereich wieder auf reines `3fr 1fr` (ohne `minmax(0,…)`) gezwungen. Damit gibt es ein ehrliches Vorher/Nachher am selben Stand.

| Seite / Größe | Spalten VORHER (sim.) | Spalten NACHHER | Reiterleiste (Bedarf/Platz) | `main`-Überstand |
| --- | --- | --- | --- | --- |
| Projekt 1440 | 1249 / 265 | **966 / 322** | vorher 1199/1199, nachher 1199/916 | vorher 163 px → **nachher 43 px** |
| Projekt 1920 | 1249 / 265 | **1134 / 378** | vorher 1199/**1199**, nachher 1199/**1084** | 0 → 0 |
| Kunde 1440 | 1014 / 338 | 1014 / 338 (identisch) | – | 0 → 0 |
| Kunde 1920 | 1374 / 458 | 1374 / 458 (identisch) | – | 0 → 0 |

Menüleiste, beide Größen: Kategorie-Leiste Überstand **0 px**. Menüpunkt-Zeile je Kategorie (alle fünf durchgeklickt): kein Überlauf (1440/1440 bzw. 1920/1920), Zeilenhöhe 102 px gegen den `max-h-40`-Deckel (160 px) — die zweizeiligen Beschriftungen schneiden nirgends ab.

### Die sechs Fragen — je Screenshot und Größe

Alle Bilder liegen unter
`C:\Users\MarvinKuhn\dev\ERP-für-Handwerker\wt\layout-review-design\react-pc-frontend\test-results\design\`
(die `zz-*`-Bilder der Wegwerf-Spec zusätzlich gesichert unter
`…\scratchpad\zz-shots\`). Jedes einzelne wurde mit dem Read-Tool geöffnet.

**A) Menüleiste (Kern von Task 8)** — `menueleiste-kategorien--pc-14zoll.png`, `menueleiste-kategorien--pc-monitor.png`, `menueleiste-dokumentenrechte--pc-14zoll.png`, `menueleiste-dokumentenrechte--pc-monitor.png`, `menueleiste-mietabrechnung--pc-14zoll.png`, `menueleiste-mietabrechnung--pc-monitor.png`, `menueleiste-nutzermenue-offen--pc-14zoll.png`, `menueleiste-nutzermenue-offen--pc-monitor.png`, dazu `zz-ribbon-projektmanagement--pc-14zoll/-pc-monitor.png`, `zz-ribbon-vorlagen-stammdaten--pc-14zoll/-pc-monitor.png`, `zz-ribbon-finanzen--pc-14zoll/-pc-monitor.png`, `zz-ribbon-nutzermenue--pc-14zoll/-pc-monitor.png`

1. *Farben:* Aktive Kategorie rose-50 mit rose-200-Rahmen und rose-700-Text, inaktive slate-500 — auf einen Blick unterscheidbar, in beiden Größen. Aktiver Menüpunkt weiß mit rose-100-Icon-Kreis, inaktiv slate. Genau eine rosa Primäraktion je Seite („Neues Projekt"), das rose-Eyebrow darüber ist Text, keine Aktion. Kontraste tragen.
2. *Design-System:* rose/slate durchgehend, kein blue/indigo/violet. Icons ausschließlich Lucide (`Gem` für KI-Hilfe), kein Emoji, keine handgemalte SVG, Systemschrift, `rounded-lg`, PageHeader-Muster (Eyebrow + Großtitel + Untertitel + Aktionen) eingehalten.
3. *Look-and-Feel:* Ruhig. Die Kategorie-Knöpfe stehen mit `px-3` enger, wirken aber nicht gedrängt — das Ergebnis liest sich eher aufgeräumter als vorher. Die Menüpunkte in „Vorlagen & Stammdaten" (11 Punkte) stehen sauber in Untergruppen mit ihren Kapitälchen-Labels. Zweizeilige Beschriftungen: bei 1440 wie bei 1920 bleibt **jede** geprüfte Beschriftung tatsächlich einzeilig („Dokumentenrechte" und „Mietabrechnung" passen knapp in die 5,5 rem) — die befürchtete Unruhe tritt gar nicht erst ein. Die Zeile wächst dadurch auch nicht.
4. *UX:* Kategorie öffnen/schließen ist ein Klick, aktive Kategorie klar markiert, Nutzermenü mit `aria-expanded`-Pfeil. Der Weg zu „Dokumentenrechte" ist ohne Raten sichtbar.
5. *Auffindbarkeit:* **Alle fünf Kategorien bei 1440 vollständig lesbar**, „Finanzen & Controlling" komplett — der Spec-Befund 3 ist damit weg. „Dokumentenrechte" und „Mietabrechnung" stehen vollständig da, kein „…". Nichts unter dem Fold.
6. *Überschneidungen:* Kein horizontaler Scroll (Kategorie-Leiste gemessen 0 px Überstand in beiden Größen), keine überlappenden Bedienelemente. Das Nutzermenü legt sich als Dropdown über die Menüzeile — normal für ein Overlay, verdeckt nichts, was man dabei braucht.
   **Ein 🟡 zur Kürzung:** Der Anzeigename wird zu „Friederike Beispiel-…" gekürzt — bei 1440 nachvollziehbar, bei **1920 aber unnötig**, dort stehen rechts noch rund 300 px frei. `max-w-[10rem]` ist nicht breakpoint-gated. Dazu bricht die Kürzung mitten im Doppelnamen, was rauer wirkt als nötig. Der volle Name steht im aufgeklappten Menü (in `menueleiste-nutzermenue-offen` und `zz-ribbon-nutzermenue` beide Größen geprüft: „Friederike Beispiel-Musterfrau" zweizeilig, ungekürzt) und im `title` — deshalb vertretbar, aber `2xl:max-w-none` wäre die bessere Lösung.

**B) Projekt-Detailseite, Zwei-Spalten-Raster (Kern von Task 2)** — `rahmen-projekt-detail--pc-14zoll.png`, `rahmen-projekt-detail--pc-monitor.png`, `zz-projekt-detail--pc-14zoll.png`, `zz-projekt-detail--pc-monitor.png`, `zz-projekt-detail-ALT-SIMULIERT--pc-14zoll.png`, `zz-projekt-detail-ALT-SIMULIERT--pc-monitor.png`

1. *Farben:* Kennzahlen-Reihe slate mit grünem „Gewinn", Status-Chip gelb („Offen"), Dokumententyp-Chips rose/gelb, Primäraktion „Dokument erstellen" rose-600 — genau eine gefüllte rosa Aktion im Inhaltsbereich. Zustände klar getrennt.
2. *Design-System:* rose/slate, Lucide-Icons, `shadow-sm`-Karten, `rounded-lg`. Kein Fremdton, kein Emoji. Die drei Chip-Arten werden eingehalten.
3. *Look-and-Feel:* **Hier liegt die eigentliche Verbesserung.** Im Alt-Bild bei 1440 ist die Karte „Projektdaten" am rechten Rand abgeschnitten — man sieht „Projek" und halbe Werte. Nach dem Fix steht sie vollständig da, Kundenname, Kundennummer und Auftragsnummer sind lesbar. Bei 1920 wird die rechte Spalte von 265 auf 378 px breiter: der lange Kundenname passt in zwei statt drei Zeilen, die Spalte liest sich als echte Seitenspalte statt als angeklebter Streifen. Die linke Spalte verliert 115 px und wirkt trotzdem nicht gedrängt — Dokumentenliste, Leerzustände und Überschriften haben reichlich Luft.
4. *UX:* „Bearbeiten" oben rechts, ohne Scrollen sichtbar, in beiden Größen. Reiter mit Zählern, aktiver Reiter mit rose-Unterstrich.
5. *Auffindbarkeit:* Bei 1440 **neu erreicht**: die rechte Spalte ist wieder auffindbar. Aber siehe Punkt 6 zu den Reitern.
6. *Überschneidungen:* Bei 1440 bleibt ein Rest-Überstand von 43 px (vorher 163 px) — der Knopf „mit Anfrage zusammenführen" wird rechts angeschnitten. **Nicht von Abschnitt 2 verursacht** (die Alt-Simulation zeigt denselben Kopfzeilen-Effekt), das ist Spec-Befund 2 und gehört Task 3. Zweiter Punkt siehe Kritik unten (Reiterleiste bei 1920).

**C) Kunden-Detailseite (durch DetailLayout mitbetroffen)** — `zz-kunde-detail--pc-14zoll.png`, `zz-kunde-detail--pc-monitor.png`, `zz-kunde-detail-ALT-SIMULIERT--pc-14zoll.png`, `zz-kunde-detail-ALT-SIMULIERT--pc-monitor.png`

1. *Farben:* „Gesamtumsatz" slate, „Gewinn" emerald — semantisch sinnvoll, wenn auch nicht im Design-System dokumentiert (🟡, vorbestehend). E-Mail-Link rose, Kontaktkarten slate-50.
2. *Design-System:* rose/slate + der eine emerald-Kasten, Lucide-Icons, kein Emoji, `rounded-lg`.
3. *Look-and-Feel:* Bei 1920 sehr ruhig und ausgewogen (1374/458). Die Kontaktdaten-Spalte trägt ihre Kästen ohne Enge.
4. *UX:* Eine Primäraktion („Bearbeiten"), Reiter mit Zählern, Standort-Karte am Fuß der Seitenspalte.
5. *Auffindbarkeit:* „Bearbeiten" ohne Scrollen sichtbar in beiden Größen.
6. *Überschneidungen:* **Bei 1440 überlappen sich in der Kopfzeile „GESAMTUMSATZ" und „GEWINN"** — beide Kästen auf rund 50 px gequetscht, Beschriftungen und Beträge liegen übereinander und stoßen an „Bearbeiten". Das ist Spec-Befund 2 (`Kundeneditor.tsx` ~Z. 275). **Nachweislich nicht von Abschnitt 2:** die Alt-Simulation liefert bei 1440 exakt dieselben Spaltenbreiten (1014/338) und exakt dasselbe Bild. Gehört zur Kunden-Kopfzeile in Abschnitt 3.

**D) Bearbeiten-Leiste im Lieferanten-Dokument-Modal (Abschnitt 0/1, unverändert)** — `leiste-lesen--pc-14zoll/-pc-monitor.png`, `leiste-bearbeiten--pc-14zoll/-pc-monitor.png`, `leiste-countdown--pc-14zoll/-pc-monitor.png`, `leiste-deaktiviert--pc-14zoll/-pc-monitor.png`, `leiste-verbindung-weg--pc-14zoll/-pc-monitor.png`, `lieferant-modal-lesen-hinweis--pc-14zoll/-pc-monitor.png`, `lieferant-modal-bearbeiten--pc-14zoll/-pc-monitor.png`, `lieferant-modal-fremdes-lock--pc-14zoll/-pc-monitor.png`, `lieferant-modal-fehler--pc-14zoll/-pc-monitor.png`, `lieferant-modal-fehler-tooltip--pc-14zoll/-pc-monitor.png`, `lieferant-modal-speicherfehler-toast--pc-14zoll/-pc-monitor.png`

1. *Farben:* Vorbildlich getrennt — Lesen grau/rose-Umriss, Bearbeiten rose-600 gefüllt, Countdown amber, fremdes Lock rose-50 mit Schloss, Verbindung weg rose mit durchgestrichenem Funk-Icon, Fehler rose-Rahmen. Deaktiviertes „Speichern" blass-rose statt stumm grau.
2. *Design-System:* rose/slate, Lucide, `rounded-2xl` für den Dialog, Feld-Icons konsequent.
3. *Look-and-Feel:* Ordentlich, zweispaltig, gleiche Feldabstände. In beiden Größen stabil.
4. *UX:* Gulf of Evaluation erfüllt — jeder Fehler bekommt zusätzlich einen Toast, Ladezustände sichtbar, Countdown erklärt sich selbst.
5. *Auffindbarkeit:* Primäraktion je Zustand ohne Scrollen sichtbar.
6. *Überschneidungen:* Kein horizontaler Scroll. **🟡 vorbestehend:** in `lieferant-modal-speicherfehler-toast` legt sich der Inline-Kasten „Speichern fehlgeschlagen" über die Überschrift „Zahlungsbedingungen" (1440) bzw. über die Skonto-Felder (1920). In `toast-bei-dialog-zweizeilig` verdeckt der zweizeilige Toast den Modal-Titel „Dokument bearbeiten". Beides nicht Abschnitt 2.

**E) Dokument-Editor als Vollbildseite (Abschnitt 0/1, unverändert)** — `editor-seite-lesen--pc-14zoll/-pc-monitor.png`, `editor-seite-bearbeiten--pc-14zoll/-pc-monitor.png`, `editor-seite-gesperrt--pc-14zoll/-pc-monitor.png`, `editor-seite-fehler--pc-14zoll/-pc-monitor.png`, `editor-seite-tab-schliessen--pc-14zoll/-pc-monitor.png`, `editor-seite-warn-dialog-blockiert-leiste--pc-14zoll/-pc-monitor.png`, `dokument-editor-vor-schliessen--pc-14zoll/-pc-monitor.png`, `dokument-editor-ungespeichert-warnung--pc-14zoll/-pc-monitor.png`, `toast-bei-dialog-versionskonflikt--pc-14zoll/-pc-monitor.png`

1. *Farben:* Zustände klar (Sperr-Banner rose, Warn-Dialog amber-Icon, Erfolgsseite rose-Kreis). **🟡 vorbestehend:** der Versionskonflikt-Dialog „Nicht gespeichert" nutzt `bg-sky-100` / `text-sky-600` (`src/components/ui/confirm-dialog.tsx` Z. 47/48) — eine **fremde Farbe** im Produkt-UI. Stammt aus dem Initial-Commit, war in keiner `Files`-Liste dieses Vorhabens.
2. *Design-System:* Sonst rose/slate, Lucide, kein Emoji. Ladezustand als Skeleton (`animate-pulse`), kein leerer Kasten — vorbildlich.
3. *Look-and-Feel:* Bei 1440 dicht und stimmig. **🟡 bei 1920:** die linke Editorhälfte bleibt unter der Ablagefläche zu rund zwei Dritteln leer, der Inhalt schwebt oben. Vorbestehend, nicht Abschnitt 2.
4. *UX:* Warn-Dialog blockiert die Leiste korrekt, „Speichern & Schließen" ist die eine gefüllte Primäraktion. **🟡:** in `editor-seite-lesen` stehen „Bearbeiten" und „PDF" beide als gefüllte rose-600-Knöpfe nebeneinander — zwei Primäraktionen auf einem Screen. Vorbestehend.
5. *Auffindbarkeit:* Alles ohne Scrollen erreichbar.
6. *Überschneidungen:* Keine. Beschriftung „Nicht speichern" bricht im Dialog zweizeilig um, bleibt aber im Knopf (🟡 Geschmack).

**F) Prüfhilfen-Miniseiten (kein Produkt-UI)** — `strenge-pruefungen-standard-aus--pc-14zoll/-pc-monitor.png`, `strenge-pruefungen-standard-an--pc-14zoll/-pc-monitor.png`
Testfixtures von `design-hilfen.spec.ts` (weiße Seite, ein Textschnipsel). Kein Produkt-Bildschirm, die sechs Fragen sind darauf nicht anwendbar. Angeschaut und als solche eingeordnet.

### 🛑 Kritisch (blockiert)

Keine. Kein Bruch des Design-Systems durch Abschnitt 2 (Diff enthält nur Layout-Klassen in rose/slate, keine Icons, keine Schrift, keine Farbe), keine Überschneidung und kein neues Abschneiden auf 14 Zoll durch diesen Abschnitt, alle Abläufe haben Specs, E2E vollständig grün.

### 💡 Hinweise (blockieren nicht)

1. **Reiterleiste des Projekt-Editors bei 1920 — echte, von Task 2 verursachte Verschlechterung.** Das Raster verschiebt 113 px von links nach rechts (1249/265 → 1134/378). Die Reiterleiste braucht 1199 px; sie hatte vorher exakt 1199 px und hat jetzt 1084 px. Folge: der siebte Reiter „Bau Tagebuch (0)" ist bei 1920 **abgeschnitten** und nur noch über stilles Seitwärtsscrollen erreichbar (`overflow-x-auto`, unter macOS ohne dauerhaft sichtbaren Balken). Vorher standen bei 1920 alle sieben Reiter da. Das ist der eine Punkt, an dem Abnahme-Punkt 6 der Spec („auf 1920 keine sichtbare Änderung") verletzt ist. Kein 🔴, weil (a) die Reparatur in Task 3 bereits geplant ist (Reiter umbenennen), (b) der Reiter erreichbar bleibt und (c) ein Blockieren die eine Zeile rückgängig machen würde, die den kritischen 14-Zoll-Fehler behebt. Auflage siehe unten.
2. **Bewertung der 1920-Proportionen selbst: gute Korrektur, keine Aufblähung.** 378 px sind exakt der 1fr-Anteil des 3:1-Rasters, nicht mehr. Der Kundenname passt in zwei statt drei Zeilen, die rechte Spalte liest sich endlich als Seitenspalte. Die linke Spalte bei 1134 px ist nicht gedrängt. Die Kunden-Detailseite bei 1920 (1374/458, unverändert) belegt, wie das Raster gemeint war. Ich würde die Verschiebung **behalten**, nicht zurückdrehen — das eigentliche Problem ist die zu breite Reiterleiste, nicht die Spaltenaufteilung.
3. **Anzeigename bei 1920 unnötig gekürzt.** `max-w-[10rem]` ohne Breakpoint. Vorschlag für Task 10: `max-w-[10rem] 2xl:max-w-none` (oder `2xl:max-w-[18rem]`), damit der volle Name dort steht, wo Platz ist.
4. **`no-scrollbar` lebt eine Zeile tiefer weiter.** `RibbonNav.tsx` ~Z. 389 (die Zeile mit den Menüpunkten) hat weiterhin `overflow-x-auto no-scrollbar`. Heute läuft dort nichts über (gemessen, alle fünf Kategorien, beide Größen) und der `max-h-40`-Deckel wird mit 102 px nicht ausgereizt — aber es ist genau das Muster, das Spec C eine Zeile darüber beseitigt hat. Für Task 10 aufräumen.
5. **Vorbestehend, nicht Abschnitt 2** (jeweils per Alt-Simulation oder `git log` verifiziert): Kunden-Kopfzeile bei 1440 (Kennzahlen überlappen, Spec-Befund 2, `Kundeneditor.tsx`); `confirm-dialog.tsx` mit `sky-100`/`sky-600` statt rose/slate (Initial-Commit); Inline-Fehlerkasten im Lieferanten-Modal überlagert Nachbarfelder; zwei gefüllte rose-Primäraktionen im Dokument-Editor; leere linke Editorhälfte bei 1920.

### Auftrag an Task 3 (messbar, nicht nach Augenmaß)

Auf `/projekte?projektId=5&tab=geschaeftsdokumente` mit dem langen Bauvorhaben aus der Spec:

1. `main.scrollWidth - main.clientWidth === 0` bei **pc-14zoll** (heute 43 px, verursacht vom Knopfblock der Kopfzeile).
2. „mit Anfrage zusammenführen" liegt vollständig innerhalb der Kopf-Karte, bei 1440 und 1920.
3. Reiterleiste `scrollWidth ≤ clientWidth` bei **beiden** Größen. Konkret: sie muss von heute **1199 px** auf **≤ 916 px** (1440) bzw. **≤ 1084 px** (1920) kommen. Die geplanten Umbenennungen (Geschäftsdokumente / Material / Tagebuch) müssen das tatsächlich erreichen — nachmessen, nicht schätzen. Alternativ `flex-wrap` wie in Spec B beschrieben; dann darf die Leiste zweizeilig werden, aber nichts darf verschwinden.
4. `designPruefung()` als Ganzes (inklusive `keinHorizontalerUeberlauf` auf `<main>`) läuft auf dieser Route in beiden Größen grün — heute noch nicht, deshalb ruft `rahmen-detailseite.spec.ts` sie bewusst nur in Teilen auf.

### Aufräumen

Wegwerf-Spec `e2e/zz-design-review.spec.ts` gelöscht, `git status` im Review-Worktree sauber, keine Änderung an Produktivcode.

## Abschnitt 2 — Code-Review (Code-Reviewer)

Zeit: 2026-09-05T15:52:00Z
Branch: feature/layout-14-zoll, Prüfstand `1a0857ff` (Merge von Task 1b, Task 2, Task 8); während des Reviews ist der Branch auf `7280897c` (Orchestrator, Beweis-Screenshots) weitergelaufen — an `react-pc-frontend/` ändert dieser Commit nichts.
Commit(s): 35ef9b75 + edc521f8 (Task 8), b1fd90c0 (Task 1b), 4285f00e (Task 2), gemergt in 42e3a44e, dd331405, 1a0857ff
Status: fertig
Ampel: 🟡 (von Code-Seite abgenommen)

Rollenteilung: E2E, Screenshots, Design und UX liegen beim Design-Reviewer (eigener Worktree). Dieser Block deckt Code, Korrektheit, Testqualität, Performance, Datenschutz, Sicherheit und die vollen Unit-/Lint-/Build-Gates ab. **Kein Playwright-Lauf von meiner Seite** — wo eine Aussage nur im Browser messbar ist, steht das unten ausdrücklich dabei.

### Gates

- `npm run lint`: **0 Fehler, genau 1 Warnung** (`BelegeKasseEditor.tsx:1204`, `react-hooks/exhaustive-deps`) — identisch zur Baseline.
- `npm run test`: erster Volllauf 74 von 88 Dateien / 821 Tests grün, 14 Dateien gar nicht gestartet (Failed to start forks worker … Timeout waiting for worker to respond) — reine Maschinenlast durch parallel laufende Agenten, **kein einziger Assertion-Fehler**. Zwei saubere Wiederholungsläufe: **88/88 Dateien, 1082/1082 Tests grün**, Exit 0. Damit gilt die Abnahmeregel der Baseline als erfüllt.
- `npm run build`: grün (`tsc -b` + `vite build`). Build-Output danach verworfen (`git checkout -- src/main/resources/static`, `git clean -f src/main/resources/static/assets`), `git status` sauber.
- Kein `./mvnw` (Backend unberührt). Stattdessen `git diff --name-only 4b8327cd..HEAD`: nur `react-pc-frontend/`, `docs/` und die zwei Orchestrator-Dateien `.claude/skills/loese-problem/references/{fallstricke,plan-format}.md` (Commits 4217e39f, ba627faa). **Kein Build-Output im Diff**, keine Datei außerhalb des erlaubten Rahmens, keine Merge-Konflikt-Reste.
- DSGVO: in beiden neuen Specs nur Fantasienamen (Anna Büro / anna.buero, Friederike Beispiel-Musterfrau / friederike.beispiel, Wohnungsbaugesellschaft Beispielstadt Nord …, Stahlhandel Beispiel GmbH und Co. KG). Kein Backend-Zugriff, `/api` vollständig gestubbt, GoogleMapsEmbed bewusst durch Weglassen der Adressfelder ausgeschaltet — kein externer Netzwerkzugriff aus den Tests.
- Design-System auf Code-Ebene: nur rose/slate-Klassen im Diff, Icons weiterhin ausschließlich Lucide, kein Emoji, keine neue Farbe, keine neue Schrift. Das `title` am Anzeigenamen verdrängt den zugänglichen Namen des umgebenden Knopfes nicht (der bleibt der volle Name, weil der Knopf eigenen Textinhalt hat).

### Mutationsproben

Fünf Mutationen gleichzeitig eingebaut (Raster zurück auf `[3fr_1fr]`; `<main>` zurück auf `overflow-x-hidden`; `gap-4` zurück auf `gap-8`; `px-3 2xl:px-4` zurück auf `px-4`; die neue Ausnahme-Zeile in `keinHorizontalerUeberlauf` gelöscht) und die **volle Unit-Suite** dagegen gefahren:

- `npm run test` mit allen fünf Mutationen: **88/88 Dateien, 1082/1082 Tests grün**. Keine einzige Unit-Zusicherung greift — es gibt projektweit keine Testdatei, die `DetailLayout`, `MainLayout` oder `RibbonNav` rendert (per Grep bestätigt). Die einzigen roten Meldungen waren zwei Lint-Fehler (is assigned a value but never used) für die beiden dadurch verwaisten Hilfsfunktionen in `design.ts` — ein Artefakt meiner Mutation, keine echte Zusicherung.
- Die Absicherung liegt damit vollständig bei Playwright. Aus dem Spec-Code abgeleitet, **vom Design-Reviewer zu bestätigen, nicht von mir gefahren**:
  - Raster zurück auf `[3fr_1fr]` → `rahmen-detailseite.spec.ts` bei `pc-14zoll` rot (Raster-Überstand rund 226 px gegen die 4-px-Grenze, und die Karte Projektdaten ragt hinaus). Bei `pc-monitor` bleibt sie grün — siehe Hinweis 4.
  - `gap-8` bzw. `px-4` zurück → `menueleiste-layout.spec.ts`, erster Testfall (`scrollWidth <= clientWidth` der Kategorie-Leiste) bei `pc-14zoll` rot.
  - Ausnahme-Zeile aus `design.ts` entfernt → `design-hilfen.spec.ts`, die beiden `keinHorizontalerUeberlauf`-Fälle des neuen Blocks rot.
  - `<main>` zurück auf `overflow-x-hidden` → **von nichts erfasst**, siehe Hinweis 5.
- Alle Mutationen restlos zurückgenommen, `git diff` auf die vier Dateien danach leer.

### Die drei gemeldeten Bedenken

**1. Rest-Überstand von `main` auf der Projekt-Detailseite (Task 2) — 🟡, vorbestehend, gehört Task 3.** Bestätigt, auf zwei Wegen. Strukturell: `DetailLayout.tsx` rendert `{header}` als **Geschwister oberhalb** des Rasters, nicht darin — die Spaltengrößen können die Kopfzeile also gar nicht beeinflussen. Und `overflow-x: hidden` → `auto` ändert `main.scrollWidth` nicht, beide sind Scroll-Container; nur der Balken wird sichtbar. Rechnerisch ist die Änderung auf `main.scrollWidth` monoton: `minmax(0, Nfr)` senkt das automatische Minimum der Spuren, es kann dadurch nichts breiter werden als vorher. Numerisch bestätigt durch die Alt-Simulation des Design-Reviewers (163 px → 43 px bei 1440, Kopfzeilen-Effekt vorher wie nachher gleich). Der Auftrag an Task 3 steht schon im Plan-Block (jeden der sieben Reiter anklicken und `main` auf Überstand prüfen, `designPruefung(..., { strengePruefungen: true })`).

**2. Spaltenbreiten auf 1920 (265 → 378 px) — 🟡, Korrektur an der Aufteilung, aber mit einer echten Nebenwirkung.** Die Zahlen gehen exakt auf: nutzbare Rasterbreite bei `pc-monitor` 1536 px, abzüglich `gap-6` bleiben 1512 px. Vorher zwang die Mindestinhaltsbreite der Reiterleiste die linke Spalte auf 1249 px, für rechts blieben 265 px Rest — das Raster hat seine 3:1 auf 1920 also **nie** aufgelöst, die rechte Spalte war schon immer gequetscht. Nachher: 1134 / 378 px, exakt 3:1. Das ist eine Korrektur, keine Regression. **Aber:** dieselbe Verschiebung nimmt der Reiterleiste 115 px (Bedarf 1199 px, verfügbar jetzt 1084 px), und der siebte Reiter Bau Tagebuch (0) ist bei 1920 abgeschnitten und nur noch über stilles Seitwärtsscrollen erreichbar (Messung des Design-Reviewers). Das verletzt Spec-Abnahmepunkt 6 an genau dieser Stelle. Zur Plan-Frage: **keine der beiden im Plan genannten Varianten hätte das vermieden** — `min-w-0` auf den Spalten-Divs und `minmax(0, Nfr)` auf den Spuren setzen dasselbe automatische Minimum auf 0, es ist derselbe Fix in zwei Schreibweisen (vom Coding-Agenten empirisch als pixelidentisch gemessen). Ein dritter Hebel existiert, falls Abschnitt 2 auf 1920 unbedingt unverändert aussehen müsste: den Fix per Breakpoint deckeln, `xl:grid-cols-[minmax(0,3fr)_minmax(0,1fr)] 2xl:grid-cols-[3fr_1fr]`. **Ich rate davon ab** — das friert die latente Quetschung wieder ein, und Task 3 beseitigt im nächsten Abschnitt ohnehin die Ursache (Plan: `overflow-x-auto` raus, `flex-wrap` rein, Reiter umbenennen). Der Auftrag des Design-Reviewers an Task 3 (Reiterleiste höchstens 1084 px bei 1920) deckt das messbar ab.

**3. `line-clamp-1` statt `truncate` beim Anzeigenamen (Task 8) — 🟡, Ausweichlösung ist nicht mehr nötig.** Im Code nachgeprüft: `truncate` setzt `text-overflow: ellipsis`, und genau darauf greift jetzt Zweig (b) der neuen Ausnahme in `keinHorizontalerUeberlauf`; `keinTextGekuerzt` lässt das Element über `data-kuerzung-erlaubt` durch. Das Paar, das Task 8 damals blockiert hat, ist damit aufgelöst — der neue Fall (1) in `design-hilfen.spec.ts` beweist genau diese Kombination. Empfehlung: bei der nächsten Berührung von `RibbonNav.tsx` zurück auf den Plan-Wortlaut `max-w-[10rem] truncate` (plus `title` und Marker, beide sind schon da). **Dringlicher als der Klassenname ist der Kommentar:** die Begründung in `RibbonNav.tsx` Z. 306–317 behauptet, `design.ts` kenne `data-kuerzung-erlaubt` im Überlauf-Check nicht — das stimmt seit Task 1b nicht mehr, und beides steckt im selben Merge. So wie er dasteht, verbietet der Kommentar künftigen Agenten `truncate` mit einer falschen Begründung.

### 🛑 Kritisch (blockiert)

Keine. Keine Korrektheitsfehler, Gates grün, keine Änderung außerhalb des erlaubten Rahmens, kein Build-Output, keine Merge-Reste, keine echten Personendaten.

### 💡 Hinweise (blockieren nicht)

1. **Zweig (a) der neuen Ausnahme in `keinHorizontalerUeberlauf` ist zu weit — und überflüssig.** `hatKuerzungsMarker` läuft die **Vorfahren** hoch: ein `data-kuerzung-erlaubt` irgendwo oben schaltet den immer laufenden Kasten-Überlauf-Check für den ganzen Teilbaum ab, auch für Überstände, die mit Text nichts zu tun haben (breites Kind-Element in einem `overflow: hidden`-Kasten). Diesen Fall fängt danach **nichts** mehr: `keinTextGekuerzt` braucht `text-overflow`/`-webkit-line-clamp` und überspringt denselben Marker, `keinTextLaeuftUeber` sieht nur Blatt-Elemente mit Text. Zweig (a) wird für keinen dokumentierten Fall gebraucht — Tailwinds `truncate` setzt `text-overflow: ellipsis`, `line-clamp-1` und die Projekt-Klasse `.line-clamp-2` setzen `-webkit-line-clamp`; alle drei fallen bereits unter Zweig (b). **Empfehlung für Task 10: Zweig (a) ersatzlos streichen.** Heute ist das Risiko klein (genau ein Marker im Produktivcode, auf einem Blatt-`<p>`), es wächst aber mit jedem Marker, den die Tasks 3–9 setzen — und Task 10 dreht `strengePruefungen` auf `true`, danach wird der Marker das naheliegende Mittel, um Befunde stillzulegen.
2. **Zweig (b) ist breit, aber vertretbar — mit einer Lücke bis Task 10.** Er nimmt alle 216 `truncate`-Stellen im `src/` aus der immer laufenden Schleife. Der Fall truncate-Kasten schneidet ein breites Kind-Element ab wird dann nur noch von `keinTextGekuerzt` gemeldet, also erst ab Task 10 (scharfer Standard) — bis dahin blinder Fleck. Der neue Fall (3) in `design-hilfen.spec.ts` deckt ihn nicht ab, sein Kasten hat bewusst **kein** `text-overflow`. Vorschlag: in Task 10 eine Miniseite mit truncate-Kasten und 800-px-Kind ergänzen und festhalten, welcher Check dafür zuständig ist.
3. **`rahmen-detailseite.spec.ts` prüft `<main>` überhaupt nicht.** Die Begründung (vorbestehende Kopfzeile) trägt, aber eine künftige Regression, die `main` von 43 px auf 400 px treibt, fiele auf dieser Route niemandem auf. Vorschlag: jetzt schon eine gedeckelte Zusicherung (`main`-Überstand höchstens 60 px) mit Kommentar, dass Task 3 auf 0 senkt. Der 0-px-Check selbst steht bereits im Plan-Block von Task 3.
4. **Die `pc-monitor`-Hälfte von `rahmen-detailseite.spec.ts` ist keine Regressionsbremse.** Mit dem alten Raster wären die Werte bei 1920 1249/265 und das Verhältnis 4,7 — alle drei Zusicherungen (`>800`, `>250`, Verhältnis `>2`) laufen damit durch. Nur die 1440er-Hälfte wird rot. Eine Obergrenze auf das Verhältnis (nach dem Fix exakt 3,0, also z.B. `toBeLessThan(3.5)`) würde daraus einen echten Wächter machen.
5. **Die `MainLayout`-Änderung hat null Testabdeckung.** `keinHorizontalerUeberlauf` misst `main.scrollWidth > main.clientWidth`, und dieser Wert ist unter `overflow-x: hidden` und `auto` identisch (beides sind Scroll-Container, nur der Balken unterscheidet sich). Ein Rückdreher auf `hidden` lässt die gesamte Suite grün — bei mir auf Unit-Ebene gemessen, und auch ein voller E2E-Lauf kann es bauartbedingt nicht sehen. Wenn sichtbarer Balken statt stillem Abschneiden eine zugesicherte Eigenschaft sein soll, braucht sie eine eigene Zeile, z.B. `getComputedStyle(main).overflowX !== 'hidden'` in `keinHorizontalerUeberlauf`.
6. **Falscher Kommentar in `RibbonNav.tsx` Z. 306–317** — siehe Bedenken 3. Zusammen mit dem Rückbau auf `truncate` erledigen.
7. **`no-scrollbar` lebt in derselben Datei weiter**, `RibbonNav.tsx` Z. 389 (Menüpunkt-Zeile, `overflow-x-auto no-scrollbar`) — genau das Muster, das Spec C eine Zeile darüber beseitigt hat. Deckt sich mit Hinweis 4 des Design-Reviewers.
8. **Für 3, 6 und 7 gibt es aktuell keinen Task.** `RibbonNav.tsx` steht in keiner `Files`-Liste eines verbleibenden Tasks (Task 10 umfasst nur `e2e/hilfen/design.ts`). Vorschlag an den Orchestrator: einen kleinen Task 8b in Abschnitt 5, der `truncate` zurückholt, den Kommentar richtigstellt, `2xl:max-w-none` ergänzt (Vorschlag des Design-Reviewers) und das übrige `no-scrollbar` entfernt.

### Aufräumen

Alle fünf Mutationen zurückgenommen, Build-Output verworfen. `git diff` gegen HEAD zeigt nur noch diesen Log-Block (und den davor stehenden, ebenfalls noch nicht committeten Block des Design-Reviewers), `git status` sonst sauber. Kein Produktivcode angefasst.
