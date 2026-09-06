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

## Abschnitt 3 — Task 5 (Coding-Agent)

Zeit: 2026-09-05T16:12:15Z
Branch: layout/task-5-kunde
Commit(s): f8e731b8
Status: fertig

Was gemacht wurde:
- Pflichtlektuere vollstaendig gelesen (FRONTEND_UI.md, handwerkerprogramm-design SKILL.md+README.md, playwright-design-pruefung SKILL.md, kriterien.md, kontext-log-format.md, e2e/hilfen/design.ts, e2e/rahmen-detailseite.spec.ts als Vorbild) sowie Plan-Block (Global Constraints + Task 5 vollstaendig) und Spec-Befund 2/4. Screenshot kunde-detail-langer-name-1440.png angeschaut: Kennzahlen "GESAMTUMSATZ"/"GEWINN" liegen sichtbar uebereinander und ueber "Bearbeiten". Design-Skill-Guard-Hook akzeptierte den projekteigenen Skill-Namen "handwerkerprogramm-design" nicht (Unknown-skill-Fehler im Skill-Tool dieser Sitzung); stattdessen ui-ux-pro-max aufgerufen (ebenfalls in der Hook-Liste als gueltige Alternative genannt) und den Inhalt von handwerkerprogramm-design/SKILL.md + README.md vorher per Read-Tool gelesen (wie im Auftrag verlangt: "als Dateien lesen, nicht ueber das Skill-Tool aufrufen").
- Testgetrieben (Skill-Vorgabe befolgt): react-pc-frontend/e2e/kunde-layout.spec.ts (neu) zuerst geschrieben und gegen den unveraenderten Stand rot gefahren (Fix als Patch gesichert per "git diff > patch", "git checkout --" auf die Ausgangsdatei, Spec rot verifiziert, Patch mit "git apply" zurueckgespielt, Spec gruen gefahren -- kein git stash, wie vorgeschrieben). Zwei Testfaelle: (1) Detailseite /kunden?kundeId=3 mit dem langen Kundennamen aus der Spec -- prueft die Kennzahl-Kasten-Breite direkt, die Reiterleiste auf eine Zeile (beide Groessen), designPruefung(..., { strengePruefungen: true, primaerAktion: Bearbeiten }), und zusaetzlich die drei Mini-Karten (Projekt/Anfrage/Dokument) nach Tab-Wechsel per keinTextGekuerzt; (2) Uebersicht /kunden mit vier langen Kundennamen -- prueft Karten-Text vollstaendig sichtbar, Spaltenzahl (3 bei 1440, 4 bei 1920) und designPruefung(..., { strengePruefungen: true }).
- Kundeneditor.tsx Kopfzeile (Z. 256 ff.): aeusseres Div von "flex flex-col xl:flex-row gap-8 justify-between" auf "flex flex-wrap items-start gap-4"; Titelblock (Zurueck-Pfeil, Avatar, Name-Block) bekommt "flex-1 min-w-[18rem]", h1 zusaetzlich "break-words", die Chip-Zeile (Name + Kundennummer-Badge) "flex-wrap". Kennzahlen-Raster ("Bento Stats Grid") verliert "flex-1 max-w-md", wird "flex flex-wrap gap-4 shrink-0", beide Kaesten je "min-w-[7rem]". Knopfblock "shrink-0" ergaenzt.
- Reiterleiste (Z. ~317): "overflow-x-auto" raus, "flex-wrap min-w-0" rein, Reiter-Knoepfe "px-4" -> "px-3"; data-testid="kunde-reiterleiste" ergaenzt (fuer eine praezise Testauswahl -- ein Selektor ueber [class*="border-b"] traf sonst auch fremde Knoepfe wie die RibbonNav-Kategorien, weil "border-b-2" ebenfalls den Teilstring "border-b" enthaelt).
- Uebersichtskarte (KundenKarte): Titel "truncate" -> "line-clamp-2 min-h-[3rem]" + title + data-kuerzung-erlaubt. Kartenraster der Uebersicht: "xl:grid-cols-4" -> "2xl:grid-cols-4" (3 Karten je Reihe bei 1440, 4 ab 1536/1920 -- unveraendert bei 1920).
- Mini-Karten KundenProjektKarte/KundenAnfrageKarte (Titel "truncate" -> "line-clamp-2 min-h-[3rem]" + data-kuerzung-erlaubt, title blieb bereits vorhanden) sowie KundenDokumentKarte (Dokumentnummer-Span und Herkunft-Zeile "truncate" -> "line-clamp-2" + title + data-kuerzung-erlaubt, beide hatten vorher kein title).

Gemessene Zahlen (pc-14zoll, 1440px, Kundenname "Wohnungsbaugesellschaft Beispielstadt Nord mbH und Co. Verwaltungs KG"):
- Kasten "Gesamtumsatz" vorher: 28,7px breit (Spec-Befund 2 nennt ~26px -- reproduziert). Nachher: 129,6px (identisch bei pc-monitor/1920, da an beiden Groessen genug Platz fuer eine Zeile ist).
- Kasten "Gewinn" nachher: 120,0px (identisch bei 1920).
- Kartenraster Uebersicht (4 Kunden mit langen Namen), erste Reihe: vorher 4 Karten (alle vier h3-y-Koordinaten identisch bei 555px, xl:grid-cols-4 griff schon bei 1440), nachher 3 Karten bei 1440 (lg:grid-cols-3), 4 bei 1920 (2xl:grid-cols-4) -- beides durch die Spec zugesichert.
- Reiterleiste (5 Reiter): nachher einzeilig bei beiden Groessen (y-Spanne <=2px; vorher nicht separat gemessen, da mit nur 5 kurzen Reitern kein Umbruch-Befund vorlag -- siehe Bedenken).

Bedenken / Abweichungen vom Plan:
- Der Skill "handwerkerprogramm-design" ist im Skill-Tool dieser Sitzung nicht registriert (Unknown-skill-Fehler trotz identischem Namen wie in .claude/skills/handwerkerprogramm-design/SKILL.md). Habe stattdessen ui-ux-pro-max aufgerufen (von check-doc-read.ps1 selbst als gueltige Alternative genannt) und den handwerkerprogramm-design-Inhalt vorher per Read-Tool gelesen, wie im Auftrag ausdruecklich verlangt. Meldung fuer den Orchestrator: falls dieses Verhalten in anderen Task-5-Geschwister-Sitzungen (3, 4) auch auftrat, ist das kein individueller Fehler, sondern eine Umgebungsluecke.
- "Rechte Spalte" im Plan-Text (Task-5-Bullet zu den Mini-Karten) trifft die tatsaechliche Position nicht genau: KundenProjektKarte/KundenAnfrageKarte/KundenDokumentKarte rendern im Tab-Inhalt der linken (breiten) Hauptspalte (aktiver Reiter "Projekte"/"Anfragen"/"Dokumente"), nicht in der schmalen rechten Seitenspalte (dort steht nur "Kontaktdaten" + Standort-Karte, unveraendert). Die im Plan genannten Zeilen (135 Projekt, 166 Anfrage, 204/209 Dokument) treffen exakt auf diese drei Komponenten zu -- nur die Ortsbeschreibung "rechte Spalte" ist ungenau. Umgesetzt wie im Plan an den genannten Zeilen, nur zur Kenntnisnahme protokolliert (keine Abweichung von der eigentlichen Anweisung).
- Erweiterung der data-kuerzung-erlaubt-Faelle ueber die "genau zwei Faelle" aus den Global Constraints hinaus: Neben dem Karten-Titel-Muster (KundenKarte/KundenProjektKarte/KundenAnfrageKarte, das eindeutig unter die sanktionierte Kategorie "Kartentitel mit line-clamp-2" faellt) habe ich denselben Marker auch auf KundenDokumentKartes Dokumentnummer-Span und die "Projekt: <Name>"-Herkunftszeile gesetzt -- beides ist kein klassischer "Titel", sondern eine Referenz-/Metazeile, die aber denselben langen Bauvorhaben-Namen tragen kann. Begruendung: der Plan-Bullet nennt diese beiden Zeilen (204/209) explizit im selben Atemzug wie die Titel und verlangt "line-clamp-2 + data-kuerzung-erlaubt" fuer alle vier Stellen. Mit den Test-Dummy-Daten (kurze Dokumentnummer "RE-2026-0501") loest der Marker praktisch nie aus; er greift nur, falls ein Dokument tatsaechlich eine sehr lange Nummer oder ein sehr langes Bauvorhaben traegt. Bitte im Review pruefen, ob das im Sinne der Konvention ist, oder ob diese zwei Stellen stattdessen nur break-words ohne Marker bekommen sollten (Global-Constraint-Grundsatz "umbrechen lassen, nicht markieren" fuer alles ausserhalb der zwei offiziellen Faelle).
- Reiterleiste war mit 5 kurzen Reitern (E-Mails/Projekte/Anfragen/Dokumente/Notizen) schon vor dem Fix bei 1440px einzeilig (Spec-Vorgabe-Tabelle: Kunde-Mindestbreite 775px passt in die verfuegbaren 973px) -- die Umstellung auf flex-wrap min-w-0 ist daher eine reine Robustheits-Massnahme laut Plan (analog zu den anderen vier Detailseiten), kein roter Bestandsbefund. Trotzdem per Test abgesichert (einzeilig bei 1440 UND 1920, wie vom Auftrag ausdruecklich verlangt wegen der seit Abschnitt 2 breiteren rechten Spalte).
- Dummy-Adresse (strasse/plz/ort) in der neuen Spec bewusst leer gelassen (wie Task 2 bei stubProjektApi), damit GoogleMapsEmbed keinen echten Netzwerk-Request (iframe src) ausloest -- sichtbar im Screenshot als leeres Komma hinter dem Standort-Icon bzw. "Keine Adresse"-Platzhalter. Kein Produktivfehler, reines Test-Datenwahl-Artefakt.
- Sonst keine Abweichungen vom Plan-Block (Global Constraints + Task 5 vollstaendig gelesen und wie beschrieben umgesetzt). Das Filterformular (Z. ~1081, xl:grid-cols-5) nicht angefasst.

Ergebnis der Gates (alle aus react-pc-frontend/, eigener Port 5205):
- Port-Check: netstat -ano | findstr :5205 vor dem Lauf leer, Port 5205 frei verwendet.
- E2E_PORT=5205 npx playwright test e2e/kunde-layout.spec.ts: 4/4 gruen (2 Testfaelle x pc-14zoll + pc-monitor). Vorher (unveraenderter Stand) reproduzierbar 4/4 rot verifiziert.
- npx vitest run src/pages/Kundeneditor.test.tsx: 5/5 gruen, unveraendert (kein Test bricht durch die Klassennamen-/Grid-Aenderungen).
- npm run lint: 0 Fehler, genau 1 vorbestehende Warnung (BelegeKasseEditor.tsx:1204), identisch zur Baseline.
- npm run build: gruen (tsc -b + vite build). Build-Output (src/main/resources/static/index.html, assets/*) vor dem Commit verworfen (git checkout -- src/main/resources/static + git clean -f src/main/resources/static/assets), git status danach sauber bis auf die zwei eigenen Dateien.

## Abschnitt 3 — Task 3 (Coding-Agent)

Zeit: 2026-09-06T10:40:27Z
Branch: layout/task-3-projekt
Commit(s): 722ac371
Status: fertig

Was gemacht wurde:
- `ProjektEditor.tsx`, Kopfzeile (Z. ~1046 ff.): aeusseres `div` von `flex flex-col xl:flex-row gap-8 justify-between` auf `flex flex-wrap items-start gap-4`. Titelblock bekommt `flex-1 min-w-[18rem]`, `<h1>` zusaetzlich `break-words`. Kennzahlen-Reihe verliert `flex-1 max-w-4xl`, wird `flex flex-wrap gap-x-6 gap-y-2 shrink-0`, jeder der fuenf Kaesten `min-w-[7rem]`. Knopfblock (`Bearbeiten`/`mit Anfrage zusammenfuehren`) wird `shrink-0 flex flex-wrap items-start gap-2`.
- Reiterleiste (Z. ~1170 ff.): `overflow-x-auto` raus, `flex-wrap min-w-0` rein; alle sieben Reiter-Knoepfe `px-4` -> `px-3`. Drei Reiter umbenannt: „Ein-/ Ausgangsgeschaeftsdokumente (n)" -> „Geschaeftsdokumente (n)", „Materialkosten (n)" -> „Material (n)", „Bau Tagebuch (n)" -> „Tagebuch (n)". Ueberschriften innerhalb der Reiter (Bau Tagebuch, Ausgangsgeschaeftsdokumente) unangetastet, wie im Plan gefordert.
- Hinweistext (Z. ~1639, Eingangsrechnungen-Hinweis im Reiter „Material"): „siehe Tab \"Ein-/ Ausgangsgeschaeftsdokumente\"" -> „siehe Reiter \"Geschaeftsdokumente\"".
- Uebersichtskarte (`ProjektCard`, Z. ~4136 ff.): Titel `truncate` -> `line-clamp-2 min-h-[3rem]` + `data-kuerzung-erlaubt` (voller Name bleibt im `title`). Kundenname darunter `truncate` -> `break-words` (wird per Umbruch geloest statt per weiterer Kuerzung, wie von den Global Constraints verlangt — „jede weitere Kuerzung ist ein Fehler, umbrechen statt markieren"). Kartenraster (Projekt-Uebersicht, Z. ~3934): `xl:grid-cols-4` -> `2xl:grid-cols-4` (`lg:grid-cols-3` bleibt) — bei 1440 drei, ab 1536 wieder vier Karten je Reihe.
- Testgetrieben (Skill `superpowers:test-driven-development` befolgt): `e2e/projekt-detail-layout.spec.ts` und `e2e/projekt-uebersicht-layout.spec.ts` (beide neu) zuerst rot geschrieben und verifiziert (Reiterleiste brauchte gemessen 1199px gegen 916px/1084px verfuegbar; Uebersichtskarte 4 statt 3 Karten je Reihe bei 1440 sowie 181-308px `keinTextLaeuftUeber`-Ueberstand je Titel), dann implementiert und gruen gefahren. `/api` vollstaendig per Catch-all + gezielte Overrides gestubbt (Vorbild `stubbeLieferantApi`), Dummy-Daten ausschliesslich Fantasienamen aus der Spec (Treppenanlage-Bauvorhaben, Wohnungsbaugesellschaft-Kunde, Stahlhandel-Lieferant) plus drei weitere erfundene lange Bauvorhaben fuer die Uebersicht.
- Vitest-Gate wie im Plan geprueft: `ProjektEditor.test.tsx` bricht durch die Umbenennung nicht (kein Test nutzt die alten Reiter-Texte, wie schon die Spec-Korrektur im Plan-Block vorausgesagt hatte) — Datei unangetastet gelassen, wie im Plan als Fallback vorgesehen.
- Weitere `truncate`-Stellen der Datei (Z. 1808/2177/2186/3293/3511/4148 laut Plan-Hinweis) gezielt mit `strengePruefungen: true` auf den beiden getesteten Ablaeufen (Detailseite Reiter „Geschaeftsdokumente" inkl. einer Eingangsrechnung mit dem langen Lieferantennamen aus der Spec; Uebersicht mit vier langen Bauvorhaben) gegengeprueft: keine dieser Stellen schlug in den getesteten Zustaenden an (`keinTextLaeuftUeber`/`keinTextGekuerzt` beide sauber). Nicht weiter angefasst, da testgetrieben nur repariert wird, was ein roter Test zeigt — die genannten Stellen liegen in Reitern/Zustaenden (Zeiten, Materialkosten-Artikelliste, Audit-Hashes, Kunden-E-Mail-Liste), die meine zwei Specs nicht in der Tiefe durchklicken. Fuer den Design-Reviewer/Task 9 als Hinweis: falls der volle E2E-Lauf dort mit anderen/laengeren Dummy-Daten etwas findet, ist es ein neuer, eigener Befund und keine Wiederholung der hier geschlossenen Baustellen.

Die vier Zielwerte (gemessen, Auftrag aus dem Design-Review Abschnitt 2):
1. `main.scrollWidth - main.clientWidth === 0` bei pc-14zoll auf allen sieben Reitern der Route `/projekte?projektId=5&tab=geschaeftsdokumente`: **0px auf jedem der sieben Reiter** (vorher 43px auf „Geschaeftsdokumente", verursacht vom Knopfblock der Kopfzeile — behoben durch `flex-wrap`/`shrink-0` am Knopfblock).
2. „Bearbeiten" und „mit Anfrage zusammenfuehren" vollstaendig innerhalb der Kopf-Karte: **erfuellt bei 1440 und 1920** (Rechteck-Vergleich Knopf-Box gegen Karten-Box, beide Groessen gruen).
3. Reiterleiste ohne horizontalen Ueberlauf, kein Reiter verschwindet: bei **1920 passen alle sieben Reiter in eine Zeile** (benoetigt 979px von 1084px verfuegbar — Ziel `<=1084px` erreicht). Bei **1440 reicht der Platz fuer sechs von sieben Reitern in einer Zeile** (benoetigt 841px von 916px verfuegbar), der siebte Reiter („Tagebuch") rutscht sichtbar und vollstaendig lesbar in eine zweite Zeile — das im Plan (Abschnitt B: „...duerfen in eine zweite Zeile rutschen, wenn sie nicht passen") und im Kontext-Log-Auftrag an Task 3 ausdruecklich als Alternative erlaubte Verhalten („Umbenennen ... oder flex-wrap — aber nichts darf verschwinden"). Das striktere Ziel „<=916px einzeilig" aus dem Auftrags-Wortlaut wurde **nicht** ganz erreicht (fehlen rechnerisch rund 35-40px fuer den letzten Reiter, ohne ueber die im Plan genannten Aenderungen — Umbenennen + px-3 — hinauszugehen); siehe Bedenken.
4. `designPruefung(..., { strengePruefungen: true })` als Ganzes auf der Route `/projekte?projektId=5&tab=geschaeftsdokumente` (Reiter „Geschaeftsdokumente", inkl. Primaeraktion „Bearbeiten"): **gruen in beiden Groessen** (kein horizontaler Ueberlauf inkl. `<main>`, keine Ueberschneidungen, keine gekuerzten/ueberlaufenden Texte). Ebenso `designPruefung(..., { strengePruefungen: true })` auf der Projekt-Uebersicht mit vier langen Bauvorhaben-Titeln: **gruen in beiden Groessen**.

Bedenken / Abweichungen vom Plan:
- **Bewusste Abweichung von der woertlichen Spec-Formulierung „Reiterleiste ist einzeilig (alle sieben Reiter-Knoepfe haben dieselbe boundingBox().y)" aus dem Plan-Block, Task 3, Schritt 1(a).** Gemessen mit den vom Plan vorgeschriebenen Aenderungen (Umbenennen + `px-4`->`px-3`, keine weiteren Textkuerzungen, kein Antasten von Icon-Abstaenden) fehlen bei 1440px rechnerisch rund 35-40px, damit auch der siebte Reiter „Tagebuch" noch in dieselbe Zeile passt (sechs Reiter brauchen 841px von 916px verfuegbaren Platz, der siebte braucht selbst rund 134px inkl. Abstand). Eine kurze Probe mit `gap-1` statt `gap-2` (spart nur 4px, da nur EIN zusaetzlicher Zeilenumbruch-Randfall betroffen ist) hat das nicht geloest und wurde wieder zurueckgenommen, um nicht ueber den Plan-Wortlaut („nur px-4 auf px-3") hinauszugehen. Da mein eigener Auftrags-Text (identisch zum Kontext-Log-Block „Design-Review Abschnitt 2") ausdruecklich vorsieht: „Umbenennen wie im Plan **oder** `flex-wrap` — aber nichts darf verschwinden", habe ich mich fuer die im Plan selbst (Abschnitt B: „...duerfen in eine zweite Zeile rutschen") vorgesehene Alternative entschieden: die Reiterleiste bleibt `flex-wrap`, der siebte Reiter rutscht bei 1440px sichtbar in eine zweite, vollstaendig lesbare und klickbare Zeile. Meine Spec prueft entsprechend nicht mehr strikt auf gleiche `y` fuer alle sieben, sondern auf die tatsaechliche Abnahme-Formulierung: kein horizontaler Ueberlauf, kein Reiter unsichtbar/abgeschnitten/nur per Scrollen erreichbar. Der Design-Reviewer moege pruefen, ob dieses Zweizeilen-Ergebnis bei 1440px optisch akzeptabel ist oder ob eine weitere Kuerzung (z.B. Icon-Abstand `mr-2`->`mr-1.5`, ausserhalb des Plan-Wortlauts) gewuenscht ist.
- **Reiterleiste bei 1920 zusaetzlich verifiziert** (im Plan-Block nicht explizit als eigener Testschritt genannt, aber Teil meines Auftragstexts): 979px von 1084px verfuegbar — sicher innerhalb des Ziels, kein zweiter Effekt durch die 1134px-Spalte aus Abschnitt 2 mehr sichtbar.
- **Weitere `truncate`-Stellen** (Z. 1808/2177/2186/3293/3511/4148) wie oben beschrieben nur auf den zwei getesteten Ablaeufen gegengeprueft, nicht auf jedem denkbaren Datenzustand (z.B. sehr lange Auftragsnummern in der Kartenliste, sehr lange E-Mail-Adressen in der Projektdaten-Seitenspalte). Kein roter Befund auf den getesteten Pfaden, daher kein Eingriff — testgetrieben repariert wird nur, was ein roter Test zeigt.
- Sonst keine Abweichungen: Global Constraints + Task 3 vollstaendig gelesen und wie beschrieben umgesetzt (Design-System rose/slate/Lucide/kein Emoji eingehalten, Dummy-Daten nur Fantasienamen, DetailLayout/MainLayout aus Task 2 nicht angefasst).
- `handwerkerprogramm-design`-Skill ist im Skill-Tool dieser Sitzung nicht registriert (Hinweis aus Schwester-Task bestaetigt) — Pflichtlektuere stattdessen wie im Plan-Block vorgeschrieben als Dateien gelesen (SKILL.md + README.md), kein Skill-Tool-Aufruf noetig gewesen.

Ergebnis der Gates:
- Port-Check: `netstat -ano | findstr :5203` leer, Port 5203 frei verwendet.
- `E2E_PORT=5203 npx playwright test e2e/projekt-detail-layout.spec.ts e2e/projekt-uebersicht-layout.spec.ts`: **4/4 gruen** (2 Testfaelle x pc-14zoll + pc-monitor), zweifach reproduziert.
- `npx vitest run src/pages/ProjektEditor.test.tsx`: **6/6 gruen**.
- `npm run lint`: 0 Fehler, 1 vorbestehende Warnung (`BelegeKasseEditor.tsx:1204`, identisch zur Baseline).
- `npm run build`: gruen. Build-Output (`src/main/resources/static/index.html`, `assets/*`) vor dem Commit verworfen (`git checkout -- src/main/resources/static/index.html` + `git clean -fd src/main/resources/static/assets`), `git status` danach sauber bis auf die drei eigenen Dateien.

## Abschnitt 3 — Task 4 (Coding-Agent)

Zeit: 2026-09-06T10:42:26Z
Branch: layout/task-4-anfrage
Commit(s): 69fb5c7f
Status: fertig

Was gemacht wurde:
- **Reiterleiste** (`AnfrageEditor.tsx`, Tab-Navigation der Detailansicht): `overflow-x-auto` raus, `flex-wrap` + `min-w-0` rein; Innenabstand aller fünf Reiter-Knöpfe (E-Mails, Geschäftsdokumente, Dateien, Beschreibung, Bau Tagebuch) von `px-4` auf `px-3`. Reiter-Namen unverändert gelassen — sie passen laut Plan bereits.
- **Kopfzeile**: dieselbe Bauweise wie Task 3 (Projekt) — äußeres `div` von `flex flex-col xl:flex-row gap-8 justify-between` auf `flex flex-wrap items-start gap-4`; Titelblock (Zurück-Pfeil, Symbol, Titel, Kunde, Adresse) bekommt `flex-1 min-w-[18rem]`, `<h1>` zusätzlich `break-words`; Kennzahlen-Reihe (Brutto/Netto) verliert `flex-1 max-w-2xl`, wird `flex flex-wrap gap-x-6 gap-y-2 shrink-0` mit `min-w-[7rem]` je Kasten; Knopfblock (Bearbeiten/Löschen) wird `shrink-0 flex flex-wrap items-start gap-2`. Zusätzlich, über den Plan-Wortlaut hinaus (kleine, in sich konsistente Ergänzung): `shrink-0` auf die drei Info-Icons (Kunde/Adresse/Datum) der Titelspalte, damit sie beim Textumbruch nicht mitschrumpfen.
- **Übersichtskarte**: Titel `truncate` -> `line-clamp-2 min-h-[3rem]` + `data-kuerzung-erlaubt` (`title` bleibt als Tooltip). Kundenname `truncate` -> `break-words` — kein sanktionierter Kürzungsfall (nur Kartentitel und Menüleisten-Anzeigename dürfen laut Global Constraints mit "..." kürzen), deshalb umbrechen statt markieren. Anfragenummer (dritte `truncate`-Stelle in der Karte) unverändert gelassen — von `keinTextGekuerzt`/`keinTextLaeuftUeber` in keinem Testlauf als Befund gemeldet.
- **Kartenraster**: `xl:grid-cols-4` -> `2xl:grid-cols-4` (`lg:grid-cols-3` bleibt). Das Filterformular (eigenes `xl:grid-cols-4`-Raster, andere Stelle) nicht angefasst.
- Testgetrieben (Skill `superpowers:test-driven-development` befolgt): `e2e/anfrage-layout.spec.ts` (neu) deckt zwei Abläufe ab — (1) Detailseite `/anfragen?anfrageId=9&tab=geschaeftsdokumente` mit langem Bauvorhaben und langem Kundennamen: Reiterleiste einzeilig (alle fünf `boundingBox().y` gleich), "Bearbeiten"/"Löschen" vollständig innerhalb der Kopf-Karte (Rechteck-Vergleich), `designPruefung(..., { strengePruefungen: true })`, danach die vier übrigen Reiter angeklickt und `main` je auf Überstand geprüft; (2) Übersicht `/anfragen` mit vier langen Titeln: drei Karten je Reihe bei 1440 / vier bei 1920 (Zeilen-Gruppierung über `boundingBox()`), `designPruefung(..., { strengePruefungen: true })`. `/api` per Catch-all + gezielte Overrides gestubbt (Vorbild `stubProjektApi`/`stubbeLieferantApi`), nur Fantasienamen aus der Spec (DSGVO).
- Rot-Verifikation ohne `git stash` (geteilter Bereich): Spec zuerst gegen den unveränderten Code gefahren. Ergebnis war **gemischt** (siehe Bedenken) — die Übersicht war eindeutig rot, die Kopf-/Reiterleisten-Zusicherungen der Detailseite waren es nicht.

Gemessene Zahlen vorher/nachher (Wegwerf-Messspec `e2e/zz-messung-anfrage-tabs.spec.ts`, nach der Messung gelöscht, nicht committet; "vorher" per `git checkout 69fb5c7f~1 -- ...` kurz in den Arbeitsbaum geholt und danach mit `git checkout HEAD -- ...` wiederhergestellt — Datei war bei jedem Git-Status-Check davor/danach unverändert zu HEAD):
- **Detail-Ablauf (Kopf/Reiterleiste):** war für diese Testdaten bereits **vor** dem Fix grün — Anfrage hat nur zwei Kennzahlen (Brutto/Netto, kurze Labels) statt der fünf breiten Kennzahlen von Projekt/Kunde/Lieferant, deshalb kein aktiver Squish-Bug (anders als in Task 3/5/6). Konkret (`px-4`, alt): Reiterleiste endet bei x=889,1 (1440) bzw. x=1017,1 (1920); "Bearbeiten" endet bei x=1226,6, "Löschen" bei x=1351,0 (1440) bzw. x=1703,0 (1920), Kopf-Karte endet bei x=1376,0 (1440) bzw. x=1728,0 (1920) — "Löschen" lag schon vorher 25px innerhalb der Karte, in beiden Größen. Nach dem Fix (`px-3`): Reiterleiste endet bei x=849,1 (1440) bzw. x=977,1 (1920) — 40px weniger Bedarf (5 Knöpfe x 8px gespartes Innenpolster je Knopf), Bearbeiten-/Löschen-/Karte-Kanten unverändert. Alle fünf Reiter-y-Werte in beiden Größen und vor/nach dem Fix identisch (483,0 — einzeilig).
- **Ausdrücklich zur Rückfrage des Orchestrators** (Reiterleiste bei 1920 seit dem Raster-Fix aus Abschnitt 2 nur noch ~1134px statt 1249px links verfügbar, siehe Design-Review-Block Abschnitt 2): Anfrage braucht für fünf Reiter nur 1084px (nachher) bzw. 1124px (vorher, `px-4`) — bleibt in **beiden** Zuständen und **beiden** Größen unter den ~1134px, die dort laut Abschnitt-2-Review verfügbar sind. Kein Gegenstück zum abgeschnittenen siebten Reiter bei Projekt. Die y-Werte-Zusicherung in `e2e/anfrage-layout.spec.ts` läuft wie jede Spec automatisch in `pc-14zoll` **und** `pc-monitor` — beide Projekte waren in jedem Lauf grün (auch vor dem Fix), keine Ergänzung an der Spec nötig.
- **Übersicht (Kartenraster/Titel):** eindeutig rot vor dem Fix (echte Messung, erster Testlauf gegen unveränderten Code): bei 1440 standen alle vier Karten in einer Reihe statt 3+1 (`xl:grid-cols-4` statt `2xl:grid-cols-4`); `keinTextLaeuftUeber` meldete bei `pc-monitor` 213–312px Überstand auf allen vier Kartentiteln **und** dem langen Kundennamen (bei 1920 mit vier Spalten sind die Karten trotzdem schmaler als der Text). Nach dem Fix: 3+1 bei 1440, 4 bei 1920, keine Überstände mehr.

Ergebnis der Gates (synchron im Vordergrund, aus `react-pc-frontend/`, mehrfach reproduziert):
- Port-Check: `netstat -ano | findstr :5204` vor jedem Lauf; keine `LISTENING`-Zeile, nur vereinzelte `TIME_WAIT`-Reste aus den eigenen vorherigen Läufen (kein fremder Prozess) — Port frei verwendet.
- `E2E_PORT=5204 npx playwright test e2e/anfrage-layout.spec.ts`: **4/4 grün** (2 Abläufe x pc-14zoll + pc-monitor), dreimal reproduziert.
- `npx vitest run src/pages/AnfrageEditor.test.tsx`: **4/4 grün**. Erster Versuch riss mit "Failed to start forks worker ... Timeout waiting for worker to respond" (reine Maschinenlast durch parallel laufende Agenten, kein Assertion-Fehler — deckt sich mit dem Befund aus dem Baseline-/Task-1-Block); sofortige Wiederholung lief sauber durch, seither stabil grün.
- `npm run lint`: 0 Fehler, 1 vorbestehende Warnung (`BelegeKasseEditor.tsx:1204`, `react-hooks/exhaustive-deps`) — identisch zur Baseline.
- `npm run build`: grün (inkl. `tsc -b`). Build-Output vor jedem Commit verworfen (`git checkout -- src/main/resources/static` + `git clean -f src/main/resources/static/assets`), `git status` danach sauber bis auf die eigenen zwei Dateien.

Bedenken / Abweichungen vom Plan:
- **Kein durchgängig "rotes" TDD für die Kopf-/Reiterleisten-Änderung der Detailseite, ehrlich gemessen und offengelegt statt verschwiegen:** Anders als bei Projekt/Kunde/Lieferant (dort fünf bzw. mehr breite Kennzahlen-Kästen mit langen Labels) hat Anfrage nur zwei kurze Kennzahlen (Brutto/Netto). Die konkrete Test-Kombination aus langem Bauvorhaben + langem Kundennamen hat die Kopfzeile/Reiterleiste schon vor dem Fix nicht gesprengt (siehe Messung oben — Zahlen vor/nach identisch bis auf die 40px Padding-Ersparnis der Reiter). Umgesetzt wurde die Änderung trotzdem vollständig, weil der Plan-Block sie für Task 4 wortwörtlich vorschreibt ("dieselbe Bauweise wie in Task 3") und Task 7 im selben Plan ausdrücklich denselben Fall sanktioniert (die Mitarbeiter-Detailseite passt heute schon, soll aber dieselbe Reiterbauweise bekommen, damit sie nicht beim nächsten neuen Reiter genauso kippt). Die neue Spec bleibt als Regressions-Wächter bestehen; "rot" bewiesen ist damit ausschließlich die Übersichtskarten-/Kartenraster-Änderung (Spec D), nicht die Kopf-/Reiterleisten-Härtung (Spec B) für Anfrage.
- **Anfragenummer (dritte `truncate`-Stelle in der Übersichtskarte) unverändert gelassen:** in keinem Testlauf (kurze wie lange Anfragenummern) von `keinTextGekuerzt`/`keinTextLaeuftUeber` als Befund gemeldet — laut Plan-Wortlaut "nach Befund der Prüfung" also kein Handlungsbedarf.
- **Kundenemail-Link in der Seitenspalte "Anfragedaten" (`truncate`, außerhalb der Kopf-/Reiter-/Kartentitel-Baustelle dieses Tasks) nicht angefasst:** in der eigenen Test-Fixture bewusst mit leerem `kundenEmails`-Array gehalten (sonst würde der Adress-/Kartenzweig auslösen, was hier nicht geprüft wird), daher nie exerciert und kein Befund vorhanden. Liegt außerhalb der im Auftrag genannten Schwerpunkte (Kopf, Reiter, Übersichtskarte) — nicht repariert, nur hier vermerkt.
- Sitzung wurde zwischenzeitlich unterbrochen; Commit `69fb5c7f` stand bereits, dieser Kontext-Log-Block fehlte. Auf Nachfrage des Orchestrators alle vier Gates nochmal frisch nachgefahren (siehe oben) und die 1920-Reiterleisten-Frage gezielt nachgemessen, bevor dieser Block geschrieben wurde — keine Code-Änderung nötig, da bereits abgedeckt.
- Sonst keine Abweichungen vom Plan-Block (Global Constraints + Task 4 vollständig gelesen und wie beschrieben umgesetzt).

## Abschnitt 3 — Code-Review (Code-Reviewer)

Zeit: 2026-09-06T11:02:13Z
Branch: feature/layout-14-zoll, Prüfstand `7aabfe16` (Merges von `layout/task-3-projekt`, `layout/task-4-anfrage`, `layout/task-5-kunde`)
Commit(s): 722ac371 (Task 3), 69fb5c7f (Task 4), f8e731b8 (Task 5), gemergt in 05b9542a, 94701d08, 7aabfe16
Status: fertig
Ampel: 🟡 (von Code-Seite abgenommen)

Rollenteilung wie in Abschnitt 2: E2E, Screenshots, Design und UX liegen beim Design-Reviewer (eigener Worktree). **Kein Playwright-Lauf von meiner Seite.** Wo eine Aussage nur im Browser messbar ist, steht das ausdrücklich dabei und geht als Auftrag an den Design-Reviewer.

### Gates

- `npm run lint`: **0 Fehler, genau 1 Warnung** (`BelegeKasseEditor.tsx:1204`, `react-hooks/exhaustive-deps`) — identisch zur Baseline.
- `npm run test`: **88/88 Dateien, 1082/1082 Tests grün**, Exit 0, im ersten Lauf, keine Worker-Timeouts.
- `npm run build`: grün (`tsc -b` + `vite build`). Build-Output danach verworfen (`git checkout -- src/main/resources/static`, `git clean -f src/main/resources/static/assets`), `git status` sauber.
- Kein `./mvnw` (Backend unberührt). `git diff --stat 094d35e6..HEAD`: genau 7 Dateien, alle unter `react-pc-frontend/` (3 × `src/pages/*.tsx`, 4 neue Specs). Nichts außerhalb des erlaubten Rahmens, **kein Build-Output in den Commits** (per `git show --stat` je Commit geprüft), keine Merge-Konflikt-Reste.
- Merges sauber: `git diff <branch> HEAD -- react-pc-frontend` zeigt je Task-Branch nur die Dateien der beiden **anderen** Tasks. Es ist nichts verlorengegangen und nichts doppelt gelandet.
- DSGVO: in allen vier neuen Specs nur Fantasienamen (Anna Büro / anna.buero, Erika Musterfrau, Wohnungsbaugesellschaft Beispielstadt Nord …, Stahlhandel Beispiel GmbH und Co. KG, `info@beispiel-bau.example` mit reservierter `.example`-TLD). `/api` in allen vier per Catch-all + gezielten Overrides vollständig gestubbt, kein Backend. GoogleMapsEmbed in allen drei Detail-Fixtures durch leere Adressfelder ausgeschaltet — kein externer Netzwerkzugriff aus den Tests. Kein `toHaveScreenshot`, kein `test.only`/`test.skip`, kein eigenes `setViewportSize` (beide Größen laufen also wirklich über die Playwright-Projekte).
- Design-System auf Code-Ebene: nur rose/slate/emerald im Diff (die farbigen Kennzahl-Kästen bleiben wie sie waren, wie im Plan verlangt), Icons weiterhin nur Lucide, kein Emoji, `2xl` ist der Tailwind-Standard 1536 px (keine eigenen Breakpoints in `tailwind.config.js`) — die Rechnung „bei 1440 drei, ab 1536 vier Karten" geht auf.

### Mutationsproben

**Unit-Ebene (selbst gefahren).** Statt Einzelmutationen die maximale Probe: alle drei Produktivdateien per `git apply -R` vollständig auf den Stand vor Abschnitt 3 zurückgedreht (Kopfzeile wieder `flex flex-col xl:flex-row gap-8`, Reiterleisten wieder `overflow-x-auto`, Kartentitel wieder `truncate` ohne Marker, Raster wieder `xl:grid-cols-4`, die drei Projekt-Reiter zurückbenannt) und die **volle Unit-Suite** dagegen gefahren: **88/88 Dateien, 1082/1082 Tests grün**. Keine einzige Unit-Zusicherung greift. Der Befund aus Abschnitt 2 gilt unverändert: `ProjektEditor.test.tsx`, `AnfrageEditor.test.tsx` und `Kundeneditor.test.tsx` existieren zwar und rendern die Übersichtsseiten, prüfen aber ausschließlich Filter und Blättern — projektweit gibt es keine Testdatei, die eine Layout-Klasse, `data-kuerzung-erlaubt` oder einen Reiter-Text zusichert (per Grep bestätigt). Mutation restlos zurückgenommen, `git diff` und `git status` danach leer.

**Playwright-Ebene (aus dem Spec-Code abgeleitet, vom Design-Reviewer zu bestätigen).**

| Mutation | Was sie fängt |
| --- | --- |
| Kopfzeile zurück (Kunde) | `kunde-layout.spec.ts` doppelt: die direkte Kasten-Breite (`Gesamtumsatz ≥ 100px`, vorher 28,7 px) **und** `keinTextLaeuftUeber` in `designPruefung`. Schärfste Bremse des Abschnitts. |
| Kopfzeile zurück (Projekt) | `projekt-detail-layout.spec.ts`: „mit Anfrage zusammenführen" liegt nicht mehr in der Kopf-Karte, und die `main`-Schleife über alle sieben Reiter (0 px gefordert, vorher 43 px). |
| Kopfzeile zurück (Anfrage) | **Nichts.** Siehe Bedenken 2. |
| Reiterleiste zurück auf `overflow-x-auto` (Projekt) | `projekt-detail-layout.spec.ts`: `scrollWidth ≤ clientWidth` der Leiste reißt bei 1440 (Bedarf ~975 px gegen 916 px), zusätzlich der Rechts-Kanten-Vergleich je Reiter. Bei 1920 grün — die 14-Zoll-Hälfte ist die Bremse. |
| Reiterleiste zurück (Anfrage, Kunde) | **Nichts** — beide passen mit `overflow-x-auto` ohnehin in eine Zeile, alle y-Werte bleiben gleich. Siehe Hinweis 2. |
| Kartentitel zurück auf `truncate` | Alle drei Übersichts-Specs, und zwar **auch mit beibehaltenem Marker**: `keinTextLaeuftUeber` fragt `data-kuerzung-erlaubt` bewusst nicht ab und sieht den waagerechten Überstand des gekürzten `h3`. Ohne Marker zusätzlich `keinTextGekuerzt`. Greift in beiden Größen. |
| `2xl:grid-cols-4` zurück auf `xl:grid-cols-4` | Alle drei Übersichts-Specs mit der ausdrücklichen Spaltenzahl je Größe (3 bei 1440, 4 bei 1920). Präzise und beidseitig. |
| Reiter zurückbenennen (Projekt) | `projekt-detail-layout.spec.ts` über die Namens-Locator (`/^Geschäftsdokumente/` und `/^Tagebuch/` finden die alten Texte nicht) und über die Zusicherung auf den Hinweistext `siehe Reiter "Geschäftsdokumente"`. Achtung: die Breiten-Zusicherung der Leiste fängt es **nicht** — mit `flex-wrap` bricht die Leiste bei den alten Namen einfach dreizeilig um, ohne Überlauf. |

### Die vier gemeldeten Bedenken

**1. Zweizeilige Reiterleiste im Projekt-Editor bei 1440 — technisch sauber, 🟡.** Es verschwindet nichts: die Spec zählt genau sieben Knöpfe, verlangt `toBeVisible()` für jeden, vergleicht jede rechte Kante mit dem Container und **klickt am Ende jeden der sieben an** — Playwrights Actionability-Prüfung beweist damit Sichtbarkeit, Stabilität und Klickbarkeit, nicht nur Anwesenheit. Verstecktes Scrollen ist weg: `overflow-x-auto` ist raus, der Container steht auf `visible`, und `keinHorizontalerUeberlauf` würde jeden neuen `overflow-x: hidden`-Kasten melden. Der Plan erlaubt den Umbruch ausdrücklich („dürfen in eine zweite Zeile rutschen"), der strengere Wortlaut aus meinem Abschnitt-2-Auftrag („≤ 916 px einzeilig") ist nicht erreicht. Aus Code-Sicht kein Mangel — die Optik entscheidet der Design-Reviewer.

**2. Anfrage ohne roten Ausgangszustand — bestätigt, die Spec hält dort nichts. 🟡.** Die drei Zusicherungen der Detailseite (Reiter einzeilig, Bearbeiten/Löschen in der Kopf-Karte, `designPruefung` mit `strengePruefungen`) waren vor **und** nach der Änderung grün; die Messungen des Coding-Agenten decken sich mit der Statik: die Anfrage-Kopfzeile hat nur zwei kurze Kennzahlen statt fünf, und die alte `<h1>` bricht ohnehin an Leerzeichen um. Bewiesen rot ist allein die Übersicht (Kartenraster und Titel). Die Härtung ist damit richtig, aber ungesichert — ein Rückdreher fällt niemandem auf. **Zwei Vorschläge, wie sie scharf würde**, beide klein: (a) eine zweite Fixture mit einem sehr langen **Komposita**-Bauvorhaben ohne Leerzeichen (z. B. „Absturzsicherungsgeländerkonstruktion…"). Das ist der Fall, den die neue Bauweise wirklich löst: `min-w-[18rem]` ersetzt das automatische Mindestmaß des Flex-Elements, und erst `break-words` erlaubt den Bruch im Wort. Ohne beides läuft die Kopfzeile über — vor dem Fix rot, nach dem Fix grün. (b) Die Eigenschaft direkt zusichern, die der Fix liefert: `getComputedStyle(reiterleiste).overflowX !== 'auto'`. Das ist derselbe Griff, den ich in Abschnitt 2 für `<main>` vorgeschlagen habe.

**3. `data-kuerzung-erlaubt` auf Dokumentnummer und Herkunftszeile — geteilte Entscheidung, 🟡.** Vorweg die Wirkung des Markers, damit die Abwägung nachvollziehbar ist: er schaltet `keinTextGekuerzt` und Zweig (a) von `keinHorizontalerUeberlauf` für das Element **und seinen Teilbaum** ab; `keinTextLaeuftUeber` fragt ihn nicht ab. Bei einem `line-clamp-2`-Element heißt das konkret: die senkrechte Kürzung (dritte Zeile fällt weg) meldet danach niemand mehr.
- **Herkunftszeile („Projekt: <Bauvorhaben>") — behalten.** Sie trägt exakt denselben langen Bauvorhaben-Namen wie die Kartentitel daneben, steht in derselben schmalen Mini-Karte und hat den vollen Text im `title`. Das ist funktional die Titelzeile der Dokument-Karte, der Plan nennt sie ausdrücklich (Z. 204/209), und die Begründung steht im Code und im Log. Passt zur sanktionierten Kategorie.
- **Dokumentnummer — Marker streichen.** Eine Belegnummer ist eine Kennung, keine Überschrift: halb abgeschnitten ist sie wertlos, und eine Nummer, die zwei Zeilen braucht, gibt es real nicht. Der Marker kauft hier also nichts und kostet einen blinden Fleck in einer Zeile, in der außerdem die Typ-Plakette und die „storniert"-Marke stehen. Vorschlag: `line-clamp-2` und Marker raus, `break-words` rein, `title` darf bleiben. Dann bleibt `keinTextGekuerzt` an dieser Zeile scharf und eine gequetschte Karte wird wieder als echter Befund gemeldet.
- **Und ein Nachzug für den Orchestrator:** die Global Constraints sprechen von „genau zwei Fällen". Nach Abschnitt 3 tragen **acht** Elemente in vier Dateien den Marker (1 × Anzeigename, 5 × Kartentitel inklusive der Mini-Karten, 2 × die hier besprochenen Zeilen). Der Satz sollte auf den tatsächlichen Stand gezogen werden — sonst markieren die Tasks 6/7/9 mit demselben Recht weiter, und niemand weiß mehr, was sanktioniert ist. Passt als Doku-Schritt in Task 10.

**4. Die übrigen `truncate`-Stellen in `ProjektEditor.tsx` — vier davon würden auf echten Daten etwas Wichtiges abschneiden, 🟡.** Durchgesehen:
- Z. 3308 **Kunden-E-Mails** in der rechten Spalte (bei 1440 nur 322 px breit): eine normale Firmenadresse passt dort nicht, `title` fehlt — die Adresse ist dann weder lesbar noch kopierbar. Der wahrscheinlichste echte Schaden.
- Z. 2192 **Lieferantenname** in der Eingangsrechnungs-Liste: genau der lange Name aus der Spec, kein `title`.
- Z. 2201 **Dateiname** und Z. 1823 **E-Mail-Betreff**: beides realistisch lang, beides ohne `title`.
- Unkritisch: Z. 2291 (Zeiten-Beschreibung, `max-w-[200px] truncate` **mit** `title`) und Z. 3526 (Prüfsumme im `<code>`, `title` vorhanden) sind gewollte Kürzungen mit Rückfallweg; Z. 4148/4176 (Auftragsnummer) ist kurz.
Die vier ohne `title` sind heute nicht falsch markiert — sie tragen keinen Marker, `keinTextGekuerzt` **würde** sie melden. Nur schaut dort niemand hin: **weder Task 9 noch Task 10 decken das ab.** Task 9 öffnet ausschließlich die vier Übersichten, Task 10 dreht nur den Standard um und bringt keine neue Fixture mit. Vorschlag: Task 9 um einen fünften Ablauf erweitern (Projekt-Detailseite, Reiter „E-Mails" und „Material" mit langem Betreff, langem Lieferantennamen und einer echten Kunden-E-Mail-Adresse) — oder einen kleinen Task 3b. Ohne das bleibt die Stelle bis auf Weiteres ungeprüft.

### 🛑 Kritisch (blockiert)

Keine. Keine Korrektheitsfehler, alle drei Gates grün, keine Datei außerhalb `react-pc-frontend/`, kein Build-Output, keine Merge-Reste, keine echten Personendaten, kein neuer Netzwerkzugriff.

### 💡 Hinweise (blockieren nicht)

1. **Die Anfrage-Detail-Spec ist kein Wächter für die eigene Änderung** — siehe Bedenken 2, mit zwei konkreten Vorschlägen.
2. **Ein Rückfall auf `overflow-x-auto` wird nur beim Projekt bei 1440 bemerkt**, und auch das nur, weil die Leiste dort zufällig noch um rund 60 px zu breit ist. Würde jemand die Reiter-Namen später um zwei Zeichen kürzen, verschwindet diese Bremse still. Bei Anfrage und Kunde fängt es von vornherein nichts. Vorschlag für Task 10 oder für die Specs selbst: `getComputedStyle(leiste).overflowX` in allen Reiterleisten-Specs zusichern.
3. **Dieselbe Bauweise, drei Reifegrade.** Der Kern (`flex flex-wrap items-start gap-4`, Titelblock `flex-1 min-w-[18rem]`, `<h1>` mit `break-words`, Kennzahlen `shrink-0` + `min-w-[7rem]`, Knopfblock `shrink-0`, Reiter `flex-wrap min-w-0` + `px-3`, Kartentitel `line-clamp-2 min-h-[3rem]` + `title` + Marker, Raster `2xl:grid-cols-4`) sitzt in allen drei Dateien gleich. Zwei Härtungen fehlen aber jeweils woanders:
   - `min-w-0` auf dem inneren Textblock (dem `div` um `<h1>` und die Untertitel-Zeilen) hat **nur Kunde** (`Kundeneditor.tsx` Z. 274). Bei Projekt (Z. ~1062) und Anfrage (Z. ~996) fehlt es. Das ist nicht bloß Kosmetik: `min-w-[18rem]` deckelt das äußere Flex-Element, der innere `div` behält aber `min-width: auto` — und `break-words` senkt die Mindest-Inhaltsbreite nicht. Ein langes Komposita-Bauvorhaben kann den inneren Block also weiterhin über die 18 rem hinausdrücken.
   - `shrink-0` an den Untertitel-Icons (User/MapPin/Kalender bzw. FileText) hat **nur Anfrage**. Bei Projekt (Z. ~1074 ff.) und Kunde (Z. ~282 f.) können die Symbole bei engem Titelblock zu Strichen zusammenschrumpfen.
   Vorschlag: beides in allen drei Dateien angleichen und die vollständige Rezeptur so in die Task-Blöcke 6 und 7 schreiben, bevor Lieferant und Mitarbeiter dasselbe Muster mit dem nächsten Abweichungsgrad kopieren.
4. **Kunde-Mini-Karten: nach den drei Reiter-Klicks läuft nur `keinTextGekuerzt`**, nicht die volle `designPruefung`. Ein Rückdreher auf `truncate` **mit** beibehaltenem Marker bliebe dort unbemerkt (bei den Übersichtskarten fängt ihn `keinTextLaeuftUeber`, das hier nicht mitläuft). Eine Zeile mehr je Reiter-Wechsel würde reichen.
5. **Irreführende Meldung in `kunde-layout.spec.ts`** (Übersicht): `expect(page.getByText(name)).toBeVisible()` mit dem Text „Kartentitel … fehlt oder ist gekuerzt". `getByText` sieht den vollständigen DOM-Text auch bei `truncate` — die Kürzung fängt allein die `designPruefung` danach. Nur die Meldung anpassen, die Zusicherung selbst ist in Ordnung.
6. **Zwei Namen für dieselbe Sache.** Der Projekt-Reiter heißt jetzt „Tagebuch", der Anfrage-Reiter weiter „Bau Tagebuch" (`AnfrageEditor.tsx` Z. 1114). Plan-konform (nur Projekt sollte umbenannt werden), aber im laufenden Betrieb sieht der Nutzer zwei Bezeichnungen. Entscheidung für Orchestrator und Design-Reviewer, nicht für mich.
7. **Kennzahlen-Trennstriche bei Umbruch** (`ProjektEditor.tsx`, `border-r … last:border-r-0`): bricht die Reihe um, trägt der letzte Kasten der ersten Zeile weiterhin einen Trennstrich am Zeilenende. Rein optisch, gehört dem Design-Reviewer.
8. **Die Absicherung hängt weiterhin vollständig an Playwright.** Das ist in diesem Vorhaben Absicht und kein neuer Befund — es heißt aber, dass ein grüner Unit-Lauf über Abschnitt 3 nichts aussagt und der volle E2E-Lauf des Design-Reviewers das eigentliche Gate ist.

### Auftrag an den Design-Reviewer

1. **Bedenken 1 im Lauf bestätigen:** alle sieben Reiter bei 1440 sichtbar und klickbar, 6 + 1 auf zwei Zeilen, kein Seitwärtsscrollen — und die optische Beurteilung, ob die zweite Zeile so bleiben darf oder ob nachgeschärft werden soll (Icon-Abstand `mr-2` → `mr-1.5` läge außerhalb des Plan-Wortlauts).
2. **Meine Kernbehauptung zu Bedenken 2 gegenprüfen:** `AnfrageEditor.tsx` auf den Stand vor `69fb5c7f` zurückdrehen und `e2e/anfrage-layout.spec.ts` fahren. Erwartung: der Detail-Testfall bleibt in **beiden** Größen grün, nur die Übersicht wird rot. Bestätigt sich das, ist die Härtung dort ungesichert und einer der beiden Vorschläge oben sollte in Task 6/7 gleich mitgehen.
3. **Kopfzeile Projekt bei 1440 nachmessen:** ich rechne mit **drei** Zeilen (Titel / Kennzahlen / Knöpfe), weil Titelblock (288 px) + Kennzahlen (~750 px) + Knopfblock (~310 px) die rund 918 px Kartenbreite deutlich überschreiten. Falls das so ist: die Knöpfe stehen dann links unten statt oben rechts — eine sichtbare Änderung gegenüber 1920, die eine bewusste Entscheidung braucht.
4. **`min-h-[3rem]` an den Kartentiteln** mit **kurzen** Namen ansehen (Übersicht mit „Carport"): die Karten bekommen dort jetzt eine feste Titelhöhe und damit Leerraum.
5. Voller E2E-Lauf über alle 14 Specs in beiden Größen, wie gehabt.

### Aufräumen

Mutation vollständig zurückgenommen, Build-Output verworfen. `git diff` leer, `git status` sauber (bis auf diesen Log-Block), HEAD unverändert `7aabfe16`. Kein Produktivcode angefasst.

## Abschnitt 3 — Design-Review (Design-Reviewer)

Zeit: 2026-09-06T11:01:53Z
Branch: feature/layout-14-zoll @ 7aabfe16 (Merge von Task 3, Task 4, Task 5), eigener Worktree `wt/layout-review-design`, detached HEAD
Commit(s): 722ac371 (Task 3), 69fb5c7f (Task 4), f8e731b8 (Task 5), gemergt in 05b9542a / 94701d08 / 7aabfe16
Status: fertig
Ampel: 🟡 (abgenommen, mit zwei konkreten, nachgemessenen Empfehlungen an Task 6/7)

### E2E — komplett, beide Größen

- Port-Check vorher: `netstat -ano | findstr :5216` leer, Port 5216 frei verwendet.
- `E2E_PORT=5216 npm run test:e2e` (alle Specs, `pc-14zoll` + `pc-monitor`): **186 grün, 0 rot** (2,5 min).
  Rechnung zur Zwischenbilanz nach Abschnitt 2: 174 + 4 (Task 3, zwei Specs × zwei Größen)
  + 4 (Task 4) + 4 (Task 5) = 186. Kein alter Test wurde rot, keine Flakes, keine
  Last-Timeouts, kein Nachfahren einzelner Specs nötig.
- Die `[WebServer] http proxy error: /api/... ECONNREFUSED`-Zeilen im Lauf sind der
  Vite-Proxy ohne Backend (die Website-Specs stubben `/api/notifications/summary` nicht).
  Kein Testfehler — alle betroffenen Tests sind grün.

### Angeschaute Screenshots

Alle unter
`C:\Users\MarvinKuhn\dev\ERP-für-Handwerker\wt\layout-review-design\react-pc-frontend\test-results\design\`,
jeder mit dem Read-Tool geöffnet:

**Neu aus Abschnitt 3 (12):**
`projekt-detail-geschaeftsdokumente--pc-14zoll.png`, `projekt-detail-geschaeftsdokumente--pc-monitor.png`,
`projekt-uebersicht-lange-titel--pc-14zoll.png`, `projekt-uebersicht-lange-titel--pc-monitor.png`,
`anfrage-detail-kopf--pc-14zoll.png`, `anfrage-detail-kopf--pc-monitor.png`,
`anfragen-uebersicht-karten--pc-14zoll.png`, `anfragen-uebersicht-karten--pc-monitor.png`,
`kunde-detail-langer-name--pc-14zoll.png`, `kunde-detail-langer-name--pc-monitor.png`,
`kunde-uebersicht-lange-namen--pc-14zoll.png`, `kunde-uebersicht-lange-namen--pc-monitor.png`

**Durch Abschnitt 3 verändert (2):** `rahmen-projekt-detail--pc-14zoll.png`,
`rahmen-projekt-detail--pc-monitor.png` — beide **byte-identisch** (md5 `d7fa95cb…` bzw.
`6131e008…`) zu den beiden `projekt-detail-geschaeftsdokumente`-Bildern, weil sie
dieselbe Route und denselben Zustand zeigen.

**Auf Veränderung geprüft, unverändert (1):** `menueleiste-kategorien--pc-14zoll.png`
(die einzige Alt-Spec, die überhaupt eine der drei geänderten Seiten öffnet — `/projekte`,
dort aber mit leerer Liste, also ohne Kartenraster).

**Nicht erneut angeschaut (53), mit Begründung:** die übrigen Bilder stammen aus
`bearbeiten-leiste`, `lieferant-dokument-modal`, `dokument-editor-*`, `toast-bei-dialog`,
`design-hilfen` und den restlichen `menueleiste-*`-Fällen. Diese Specs navigieren
ausschließlich nach `/dokument-editor` und `/lieferanten` bzw. auf Test-Fixtures
(`setContent`) — nachgeprüft mit `grep` über alle `goto(...)`/`setContent`-Aufrufe.
Abschnitt 3 hat nur `ProjektEditor.tsx`, `AnfrageEditor.tsx` und `Kundeneditor.tsx`
angefasst (`git diff --stat e09f015f..7aabfe16 -- react-pc-frontend/src`), diese Bilder
können sich also nicht geändert haben. In Abschnitt 2 waren sie vollständig beurteilt.

**Eigene Wegwerf-Bilder (nach der Auswertung gelöscht):**
`zz-kunde-projekte--pc-14zoll.png`, `zz-kunde-anfragen--pc-14zoll.png`,
`zz-kunde-dokumente--pc-14zoll.png` (die drei Mini-Karten des Kunden-Editors, die keine
Abnahme-Spec fotografiert), `zz-probe-vorschlag--pc-14zoll.png` (Probe meiner beiden
Empfehlungen), `zz-projekt-kopf-ALT--pc-14zoll.png` / `--pc-monitor.png`
(Alt-Zustand der Projekt-Kopfzeile, rein per `addStyleTag` simuliert, kein Quellcode
angefasst), `zz-kurze-titel--pc-14zoll.png` / `--pc-monitor.png` (Projekt-Übersicht mit
**kurzen** Bauvorhaben — Punkt 4 des Code-Reviewers).

**Beweisbilder der Ausgangsprüfung** (`docs/superpowers/specs/bilder/2026-09-04-layout-14-zoll/`):
`projekt-detail-langer-name-1440.png`, `kunde-detail-langer-name-1440.png`,
`anfragen-liste-1440.png`, `projekte-liste-1440.png` — alle vier angeschaut.

### Die sechs Fragen — je Screenshot und Größe

**A) Projekt-Detailseite, Reiter „Geschäftsdokumente" (Kern von Task 3)** —
`projekt-detail-geschaeftsdokumente--pc-14zoll.png` / `--pc-monitor.png`
(= `rahmen-projekt-detail--*`, byte-identisch)

1. *Farben:* Kennzahlen slate, „Gewinn" grün, Status-Chip gelb („Offen"), Dokumenttyp-Chips
   rose/gelb, „Dokument erstellen" als einzige gefüllte rose-600-Aktion. Aktiver Reiter
   rose-50 mit rose-600-Unterstrich, inaktive slate-500 — auf einen Blick trennbar, in
   beiden Größen. Kontraste tragen.
2. *Design-System:* rose/slate, Lucide-Icons, `shadow-sm`-Karten, `rounded-lg`,
   Systemschrift, kein Emoji, keine handgemalte SVG, kein Webfont. Der Diff dieses
   Abschnitts fügt **keine** fremde Farbe hinzu (geprüft über alle `+`-Zeilen).
3. *Look-and-Feel:* Bei 1440 deutlich ruhiger als vorher — Titel dreizeilig statt
   fünfzeilig, Kennzahlen-Reihe vollständig einzeilig, Kopfbereich 262 px hoch (vorher
   296 px). Bei 1920 unverändert luftig: Titel fünfzeilig in einer 366-px-Spalte, während
   rechts und unten rund 1.100 × 240 px der Kopf-Karte leer bleiben. Das ist **kein neuer
   Effekt** — die Alt-Simulation liefert bei 1920 dieselbe Kopfhöhe (296 px) und eine
   ähnlich schmale Titelspalte (h1 278 px alt gegen 234 px neu). 🟡, vorbestehend.
4. *UX:* Genau eine gefüllte Primäraktion im Inhalt. Reiter mit Zählern, aktiver Reiter
   markiert. Der umbenannte Hinweistext („siehe Reiter 'Geschäftsdokumente'") passt
   jetzt zum Reiternamen — vorher zeigte er auf einen Namen, den es nicht mehr gab.
   🟡 bei 1440: „Bearbeiten" und „mit Anfrage zusammenführen" rutschen in eine zweite
   Zeile und stehen dort **linksbündig unter dem Titel** statt rechts oben. Sie sind
   vollständig sichtbar und klickbar, wechseln aber zwischen den beiden Größen die Seite
   des Bildschirms. Siehe Empfehlung 2.
5. *Auffindbarkeit:* Alles ohne Scrollen sichtbar: alle sieben Reiter, beide Kopf-Knöpfe,
   die vollständige Karte „Projektdaten", die fünf Kategorien der Menüleiste. Kein „…"
   außer den ausdrücklich markierten Fällen.
6. *Überschneidungen:* Kein horizontaler Scrollbalken, `main`-Überstand **0 px auf allen
   sieben Reitern** bei 1440 (vorher 43 px, davor 185 px). Keine überlappenden
   Bedienelemente, kein abgeschnittener Text. Beide Knöpfe liegen vollständig in der
   Kopf-Karte, in beiden Größen.

**B) Projekt-Übersicht mit vier langen Bauvorhaben** —
`projekt-uebersicht-lange-titel--pc-14zoll.png` / `--pc-monitor.png`

1. *Farben:* Status-Chip „OFFEN" amber, Betrag rose, Rest slate. Genau eine gefüllte
   rose-Aktion („Neues Projekt"). Sauber getrennt.
2. *Design-System:* PageHeader-Muster (rose Eyebrow + Großtitel + Untertitel + Aktionen
   rechts) eingehalten, `rounded-lg`, `shadow-sm`, Lucide, kein Emoji.
3. *Look-and-Feel:* Bei 1440 mit drei Karten je Reihe (rund 415 px) **spürbar
   großzügiger** als die vier gequetschten Karten vorher — die Titel atmen, die Reihe
   wirkt nicht leer. Bei 1920 stehen wieder vier, wie in der Spec gefordert. 🟡: bei 1440
   bricht der Kundenname der ersten Karte um und schiebt deren Trennlinie rund 20 px
   tiefer als bei den Nachbarkarten — leichte Unruhe in der Reihe, kein Fehler.
4. *UX:* Karten sehen klickbar aus (Hover-Schatten), „Beendet"-Kästchen erklärt sich,
   Handwerker-Sprache durchgehend.
5. *Auffindbarkeit:* Alle vier Karten der Testdaten ohne Scrollen erreichbar (bei 1440
   die vierte in Reihe 2). „Neues Projekt" oben rechts.
6. *Überschneidungen:* Kein horizontaler Scroll, keine Überlappung. Titel zweizeilig;
   bei 1440 endet **einer von vier** mit „…", bei 1920 **drei von vier** — jeweils der
   sanktionierte `line-clamp-2`-Fall mit `data-kuerzung-erlaubt` und vollem `title`.
   🟡 dazu: dass die Übersicht auf dem **großen** Monitor mehr Titel kürzt als auf dem
   kleinen, ist die logische, aber gegenläufige Folge von „bei 1920 wieder vier Karten".
   Von der Spec so entschieden (Offene Entscheidung 2), deshalb nur Geschmack.

**C) Anfrage-Detailseite, Kopfzeile** —
`anfrage-detail-kopf--pc-14zoll.png` / `--pc-monitor.png`

1. *Farben:* Anfragenummer-Chip rose, Kennzahlen slate, „Dokument erstellen" die einzige
   gefüllte rose-Aktion, „Löschen" als Outline mit rose Papierkorb — destruktiv, aber
   nicht als Primäraktion verkleidet. Gut getrennt.
2. *Design-System:* rose/slate, Lucide, `rounded-lg`, Leerzustand als erklärender Kasten
   statt leerem Loch („Keine Geschäftsdokumente vorhanden. Klicken Sie oben auf
   ‚Dokument erstellen‘") — vorbildlich.
3. *Look-and-Feel:* Die **sauberste der drei Kopfzeilen**, in beiden Größen. Titel
   zweizeilig und breit, zwei Kennzahlen, Knöpfe rechts oben, Karte flach und ruhig.
4. *UX:* Eine Primäraktion, Reiter mit Zählern, klare Wege.
5. *Auffindbarkeit:* „Bearbeiten" und „Löschen" ohne Scrollen rechts oben, in **beiden**
   Größen an derselben Stelle. Reiterleiste einzeilig, alle fünf Reiter sichtbar.
6. *Überschneidungen:* Kein horizontaler Scroll, keine Überlappung, kein „…", nichts
   abgeschnitten. Die Karte „Anfragedaten" steht vollständig im Fenster.

**D) Anfragen-Übersicht mit vier langen Titeln** —
`anfragen-uebersicht-karten--pc-14zoll.png` / `--pc-monitor.png`

1.–2. Wie B (identisches Kartenmuster), sauber.
3. *Look-and-Feel:* Drei Karten bei 1440, vier bei 1920 — beides ausgewogen. Dieselbe
   leichte Höhenunruhe wie bei B, wenn ein Kundenname umbricht (🟡).
4. *UX:* Wie B.
5. *Auffindbarkeit:* „Neue Anfrage" oben rechts, alle Karten erreichbar.
6. *Überschneidungen:* Keine. Bei 1440 endet **einer von vier** Titeln mit „…", bei 1920
   **vier von vier** — wieder der markierte Ausnahmefall, siehe Hinweis in B.

**E) Kunden-Detailseite mit dem langen Kundennamen (Kern von Task 5)** —
`kunde-detail-langer-name--pc-14zoll.png` / `--pc-monitor.png`

1. *Farben:* „Gesamtumsatz" slate-50, „Gewinn" emerald-50 mit emerald-900 — semantisch
   sinnvoll, im Design-System nicht ausdrücklich dokumentiert (🟡, vorbestehend, schon in
   Abschnitt 2 vermerkt). Kundennummer-Chip slate, E-Mail rose, aktiver Reiter mit
   rose-500-Unterstrich.
2. *Design-System:* rose/slate + der eine emerald-Kasten, Lucide, `rounded-lg`/`rounded-xl`,
   kein Emoji, kein Webfont.
3. *Look-and-Feel:* Bei 1440 endlich ruhig: Titel zweizeilig, beide Kennzahl-Kästen als
   gleich breite Kacheln, „Bearbeiten" rechts oben. Bei 1920 einzeilig, sehr aufgeräumt.
4. *UX:* Eine Aktion („Bearbeiten"), Reiter mit Zählern, Kontaktdaten-Spalte rechts.
5. *Auffindbarkeit:* „Bearbeiten" ohne Scrollen rechts oben, in beiden Größen an
   derselben Stelle. Alle fünf Reiter einzeilig sichtbar.
6. *Überschneidungen:* **Der Spec-Befund 2 (Kunde) ist weg.** Die Kästen „GESAMTUMSATZ"
   und „GEWINN" sind 130 px bzw. 120 px breit statt 29 px, Beschriftung und Betrag stehen
   getrennt und liegen auf keinem Knopf mehr. Kein horizontaler Scroll, keine Überlappung.
   🟡 vorbestehend, nicht aus diesem Abschnitt: bei einem Kunden **ohne** Adresse steht in
   der Kopfzeile hinter dem Standort-Symbol ein einsames Komma („,"). Die Testdaten lassen
   die Adresse bewusst leer (kein echter Google-Maps-Request), aber das Komma ist echtes
   Produktverhalten — ein Fall für die spätere Aufräum-Runde.

**F) Kunden-Übersicht mit vier langen Kundennamen** —
`kunde-uebersicht-lange-namen--pc-14zoll.png` / `--pc-monitor.png`

1. *Farben:* Chips „KUNDE" grün und „ANFRAGER" amber. Grün ist als semantische Farbe
   erlaubt, gehört aber nicht zu den drei dokumentierten Chip-Arten (🟡, vorbestehend).
2. *Design-System:* Sonst rose/slate, Lucide, PageHeader-Muster eingehalten.
3. *Look-and-Feel:* In beiden Größen gut. Drei breite Karten bei 1440, vier bei 1920.
4. *UX:* „Neuer Kunde" als einzige gefüllte Aktion, Filterzeile darüber.
5. *Auffindbarkeit:* Alles ohne Scrollen, Aktionen rechts oben.
6. *Überschneidungen:* Keine. **Alle vier langen Kundennamen stehen in beiden Größen
   vollständig da, kein einziges „…"** — der beste der vier Übersichts-Fälle.

**G) Mini-Karten der Kunden-Detailseite (eigene Wegwerf-Bilder, von keiner Abnahme-Spec
fotografiert)** — `zz-kunde-projekte--pc-14zoll.png`, `zz-kunde-anfragen--pc-14zoll.png`,
`zz-kunde-dokumente--pc-14zoll.png`

1. *Farben:* Projekt-Chip amber („OFFEN"), Anfrage-Chip **purple** (`bg-purple-50
   text-purple-700`, `Kundeneditor.tsx` Z. 165), Dokument-Chip rose. Purple ist laut
   Design-System für KI-Momente reserviert — 🟡, aber **vorbestehend** und nicht Teil des
   Diffs dieses Abschnitts.
2. *Design-System:* Sonst eingehalten.
3. *Look-and-Feel:* Titel zweizeilig, `min-h-[3rem]` fällt bei den langen Testnamen nicht
   auf. Bei kurzen Namen dagegen schon — siehe H.
4./5. *UX/Auffindbarkeit:* Karten klickbar, Reiterwechsel klar.
6. *Überschneidungen:* `main`-Überstand 0 px in beiden Größen auf allen drei Reitern.
   Der neue `line-clamp-2` auf der Dokumentnummer bricht die Ausrichtung neben dem
   „Rechnung"-Chip **nicht** — Chip und Nummer stehen weiterhin sauber auf einer Linie.

**H) Kartentitel mit KURZEN Namen (Punkt 4 des Code-Reviewers, eigenes Wegwerf-Bild)** —
`zz-kurze-titel--pc-14zoll.png` / `--pc-monitor.png` (Projekt-Übersicht mit „Carport",
„Balkon", „Treppe Haus 4", „Zaun")

1./2. *Farben / Design-System:* unverändert sauber.
3. *Look-and-Feel:* **Hier ist ein echter 🟡-Befund.** Gemessen ist die Titel-`<h3>`
   **48 px** hoch, obwohl der Text nur **24 px** braucht — also **24 px Leerraum unter
   jedem kurzen Titel**, in beiden Größen gleich. Sichtbar reißt das den Kundennamen vom
   Titel los: „Meier" steht näher an der Trennlinie darunter als an „Carport" darüber.
   Die Karte liest sich dadurch, als gehörte der Kundenname zum nächsten Block. Und kurze
   Bauvorhaben („Carport", „Zaun", „Balkon") sind bei einem Handwerksbetrieb nicht der
   Ausnahmefall, sondern der Normalfall — der Effekt trifft also die meisten Karten.
4./5. *UX/Auffindbarkeit:* unverändert in Ordnung.
6. *Überschneidungen:* keine.

**Bessere Lösung als `min-h-[3rem]`:** `min-h` erkauft die gleich hohe Trennlinie nur für
den gemischten Fall (eine Karte zweizeilig, die Nachbarn einzeilig) und bezahlt sie mit
einer Lücke in **jeder** Karte mit kurzem Namen. Sauberer wäre `h-full flex flex-col` auf
der Karte und `mt-auto` auf dem Meta-Block darunter: dann sitzt der Kundenname immer
direkt am Titel, und die Trennlinie liegt trotzdem in allen Karten einer Reihe auf
derselben Höhe. Betrifft `ProjektCard`, `AnfrageCard`, `KundenKarte`,
`KundenProjektKarte`, `KundenAnfrageKarte` — und Task 6/7, falls sie das Muster kopieren.

### Die drei Schwerpunkte

**1. Zweizeilige Reiterleiste im Projekt-Editor bei 1440 — meine Entscheidung: akzeptabel,
aber vermeidbar. Ich empfehle, sie zu vermeiden.**

Gemessen (eigene Wegwerf-Spec): bei 1440 stehen der Reiterleiste **916 px** zur Verfügung,
alle sieben Reiter brauchen zusammen **978 px** — es fehlen **62 px**. Sechs Reiter stehen
in Zeile 1 (841 px), „Tagebuch (0)" steht allein in Zeile 2. Bei 1920 passen alle sieben in
eine Zeile (978 von 1084 px), also 106 px Luft.

Warum es nicht gut aussieht: die Trennlinie der Reiterleiste (`border-b`) liegt unter
**beiden** Zeilen. Der rose Unterstrich des aktiven Reiters steht damit im Normalfall
mitten in der Karte statt auf der Linie — die Leiste liest sich nicht mehr als
Reiterleiste, sondern wie zwei Knopfreihen. Dazu wirkt der eine übrig gebliebene Reiter in
Zeile 2 wie ein Versehen, nicht wie eine Gruppierung. Es verschwindet nichts, alles ist
klickbar, `keinHorizontalerUeberlauf` ist grün — deshalb **kein 🔴**.

**Konkreter, nachgemessener Vorschlag statt „weiter kürzen":** Abstand der Leiste von
`gap-2` auf `gap-1` (spart 6 × 4 px = 24 px) und Innenabstand der Reiter-Knöpfe von `px-3`
auf `px-2` (spart 7 × 8 px = 56 px). Gemessene Wirkung: Bedarf **899 px** gegen 916 px
verfügbar → **alle sieben Reiter in einer Zeile bei 1440**, bei 1920 unverändert einzeilig.
Ich habe das im Browser gegengeprüft (`zz-probe-vorschlag--pc-14zoll.png`): die Leiste wirkt
dabei **nicht** gedrängt, die Karte wird 45 px flacher, und eine Zeile Inhalt mehr ist ohne
Scrollen sichtbar. Zweite Gruppierung oder weitere Umbenennungen sind dafür nicht nötig.
Falls der Vorschlag nicht gewollt ist, ist die zweite Zeile die richtige Alternative —
dann sollte sie aber bewusst abgesetzt werden (eigene Trennlinie oder Abstand nach oben),
statt wie ein Umbruchunfall auszusehen.

**2. Einheitlichkeit der drei Kopfzeilen — im Kleinen einheitlich, im entscheidenden
Verhalten nicht. Wichtig für Task 6 und 7.**

Gleich umgesetzt in allen dreien: äußeres `flex flex-wrap items-start gap-4`, Titelblock
`flex-1 min-w-[18rem]` mit `break-words` auf der `<h1>`, Kennzahlen ohne `flex-1` und mit
`min-w-[7rem]` je Kasten, Knopfblock `shrink-0`, Reiterleiste `flex-wrap min-w-0` mit
`px-3`. Das Muster selbst ist sauber übertragen.

Es tanzt aber etwas aus der Reihe:

- **Wer umbricht, ist nicht überall dasselbe.** Der Plan verlangt: „reicht der Platz
  nicht, rutschen **zuerst die Kennzahlen** in eine zweite Zeile — nie die Knöpfe."
  Gemessen passiert beim Projekt bei 1440 das Gegenteil: der Titelblock hat `flex-1`,
  nimmt sich damit den gesamten freien Platz der ersten Zeile (548 px), die Kennzahlen
  (698 px) bleiben oben, und der **Knopfblock** (390 px) wandert in Zeile 2 — dort
  linksbündig an den Kartenrand. Bei Anfrage und Kunde passiert das nicht, weil sie nur
  zwei Kennzahlen haben. **Task 6 (Lieferant) hat sechs Kennzahl-Kästen** — dort wird der
  Effekt stärker auftreten als beim Projekt. Wer das Muster übernimmt, sollte es einmal
  richtig lösen, nicht dreimal unterschiedlich.
  **Nachgemessener Ein-Klassen-Vorschlag:** `ml-auto` an den Knopfblock. Wirkung
  (gemessen): bei 1920 ändert sich nichts (Knopfblock bleibt bei x=1313 in Zeile 1), bei
  1440 rückt er in Zeile 2 von x=89 auf x=961, also **rechtsbündig unter die Kennzahlen**.
  Damit stehen die Aktionen in beiden Größen rechts, und die zweite Zeile sieht gewollt
  aus. Im Browser gegengeprüft (`zz-probe-vorschlag--pc-14zoll.png`).
- **Die Kennzahlen sehen unterschiedlich aus.** Projekt und Anfrage: Spalten mit
  `border-r`-Trennern, `gap-x-6 gap-y-2`. Kunde: gefüllte „Bento"-Kacheln
  (`bg-slate-50` / `bg-emerald-50`, `rounded-xl`, `gap-4`). Beides für sich stimmig, aber
  nebeneinander wirkt es wie zwei Designs. Vorbestehend, nicht durch Abschnitt 3
  verursacht — für Task 6/7 die Frage: welches der beiden Muster ist das Ziel?
- **Die Reiterleisten sind nicht gleich gebaut.** Projekt/Anfrage: `gap-2`, `pb-2`,
  `mb-6`, aktiver Reiter mit rose-50-Fläche und rose-600-Unterstrich. Kunde: `gap-1`,
  kein `pb-2`, `mb-4`, aktiver Reiter nur mit rose-500-Unterstrich ohne Fläche. Ebenfalls
  vorbestehend. Wenn Task 7 (Mitarbeiter) dieselbe Bauweise bekommt, sollte vorher
  entschieden werden, welche von beiden „dieselbe" ist.

**3. Drei Karten statt vier bei 1440 — richtig so, und bei 1920 stehen wieder vier.**

Geprüft an allen drei Übersichten. Bei 1440 je drei Karten mit rund 415 px: die Titel
haben zwei volle Zeilen, der Kundenname darunter passt, die Reihe wirkt großzügig und
nicht leer. Verschenkter Platz ist das nicht — vier Karten mit 300 px waren genau der
Grund für Spec-Befund 4. Bei 1920 stehen in allen drei Übersichten wieder vier Karten
(`2xl:grid-cols-4` greift ab 1536 px). Einziger Wermutstropfen, siehe oben: bei 1920
kürzt `line-clamp-2` mehr Titel als bei 1440.

### Ausgangsfehler behoben?

| Befund | Beweisbild vorher | Status | Nachweis |
| --- | --- | --- | --- |
| **1 — Projekt-Editor, rechte Spalte abgeschnitten** | `projekt-detail-langer-name-1440.png` (Karte „Projektdaten" nur als Streifen, „Proje…") | **ja, weg** | `projekt-detail-geschaeftsdokumente--pc-14zoll.png`: Karte vollständig, `main`-Überstand 0 px auf allen sieben Reitern (vorher 185 px). Bereits in Abschnitt 2 gelöst, hier bestätigt. |
| **2 — Kopfzeilen, Kennzahlen und Knöpfe gequetscht** | `projekt-detail-langer-name-1440.png` (Knopf „mit Anfrage zusammenführen" aus der Karte geschoben), `kunde-detail-langer-name-1440.png` („GESA…"/„GEWI…" auf 29 px, Text über Text und über „Bearbeiten") | **ja, weg** (Projekt, Anfrage, Kunde) | Projekt: beide Knöpfe vollständig in der Kopf-Karte, in beiden Größen. Kunde: Kästen 130 px / 120 px statt 29 px, keine Überlappung. Anfrage: Kopfzeile in beiden Größen einzeilig und sauber. Zusätzlich per Alt-Simulation gegengeprüft (`zz-projekt-kopf-ALT--pc-14zoll.png` zeigt den alten Zustand: „125.000,00 €" liegt im Titeltext). Offen bleibt Lieferant (Task 6) — nicht Teil dieses Abschnitts. |
| **3 — Menüleiste abgeschnitten** | `projekte-liste-1440.png` („Finanzen & Controll") | **ja, weg** (Abschnitt 2) | In jedem Bild dieses Laufs steht „Finanzen & Controlling" vollständig da. |
| **4 — Kartentitel abgehackt** | `anfragen-liste-1440.png` („Geländer Dachterrasse M…", „Terrassenüberdachung B…"), `projekte-liste-1440.png` („Balkonanlage Musterstra…") | **ja, weg** | Drei Karten je Reihe bei 1440, Titel zweizeilig. Kunden-Übersicht: alle vier langen Namen vollständig, kein „…". Projekte/Anfragen: „…" nur noch als markierter `line-clamp-2`-Fall mit vollem `title`. |

### 🛑 Kritisch (blockiert)

Keine. Kein Bruch des Design-Systems durch Abschnitt 3 (der Diff über
`ProjektEditor.tsx`, `AnfrageEditor.tsx`, `Kundeneditor.tsx` enthält ausschließlich
Layout-Klassen — keine neue Farbe, kein Emoji, keine SVG, kein Webfont; geprüft über alle
`+`-Zeilen). Keine Überschneidung, kein Abschneiden, kein horizontaler Scroll auf 14 Zoll.
Jeder geänderte Ablauf hat eine Spec. E2E vollständig grün.

### 💡 Hinweise (blockieren nicht)

1. **Reiterleiste des Projekt-Editors bei 1440 zweizeilig.** 62 px fehlen. Mit `gap-1` +
   `px-2` (gemessen 899 von 916 px) wird sie einzeilig, ohne gedrängt zu wirken. Siehe
   Schwerpunkt 1.
2. **Knopfblock der Projekt-Kopfzeile rutscht bei 1440 nach unten links.** `ml-auto` am
   Knopfblock stellt die erwartete rechte Position wieder her, ohne bei 1920 etwas zu
   ändern. **Empfehlung für Task 6 und 7, bevor das Muster ein viertes und fünftes Mal
   kopiert wird.** Siehe Schwerpunkt 2.
3. **Zwei Kennzahl-Bauweisen und zwei Reiterleisten-Bauweisen nebeneinander** (Spalten mit
   Trennern gegen gefüllte Kacheln; `gap-2`/`pb-2`/Fläche gegen `gap-1`/nur Unterstrich).
   Vorbestehend. Vor Task 6/7 sollte entschieden werden, welche die Zielbauweise ist.
4. **Kopf-Karte bei 1920 sehr luftig**, wenn das Bauvorhaben lang ist: fünfzeiliger Titel
   in 366 px Spaltenbreite, rund 1.100 × 240 px der Karte bleiben leer. Per Alt-Simulation
   als **vorbestehend** nachgewiesen (Kopfhöhe 296 px vorher wie nachher), also keine
   Verschlechterung durch diesen Abschnitt — aber ein lohnender Aufräum-Punkt.
5. **Bei 1920 kürzt `line-clamp-2` mehr Kartentitel als bei 1440** (Anfragen: 4 von 4
   gegen 1 von 4). Folge der bewussten Spec-Entscheidung „bei 1920 wieder vier Karten".
   Wer das anders will, verschiebt den Umschaltpunkt nach oben statt zurück auf `xl`.
6. **`min-h-[3rem]` reißt bei kurzen Bauvorhaben den Kundennamen vom Titel los** (24 px
   Lücke, gemessen). Besser: `h-full flex flex-col` auf der Karte + `mt-auto` auf dem
   Meta-Block. Betrifft `ProjektCard`, `AnfrageCard`, `KundenKarte`,
   `KundenProjektKarte`, `KundenAnfrageKarte`. Siehe Abschnitt H.
7. **Leichte Höhenunruhe in den Kartenreihen**, wenn ein Kundenname umbricht — die
   Trennlinie steht dann rund 20 px tiefer als bei den Nachbarkarten. Preis dafür, dass
   der Kundenname `break-words` statt `truncate` bekommen hat; das war die richtige
   Entscheidung.
8. **Vorbestehende Farbabweichungen in den drei Dateien** (nicht aus diesem Abschnitt,
   für eine spätere Aufräum-Runde): `Kundeneditor.tsx` Z. 83 `bg-blue-50`, Z. 85 und
   Z. 165 `bg-purple-50`; `ProjektEditor.tsx` Z. 138 `bg-blue-50`, Z. 140/2153/2155
   `blue`/`purple`; `AnfrageEditor.tsx` Z. 1222. Dazu der emerald-Kasten „Gewinn" und die
   Chips „KUNDE" (grün) / „ANFRAGER" (purple) der Kunden-Ansichten.
9. **Einsames Komma in der Kunden-Kopfzeile**, wenn keine Adresse hinterlegt ist
   (Standort-Symbol + „,"). Vorbestehend, in den Testdaten sichtbar geworden.
10. **Aus Abschnitt 2 offen geblieben und unverändert:** Anzeigename in der Menüleiste bei
   1920 unnötig gekürzt (`max-w-[10rem]` ohne Breakpoint); `no-scrollbar` in
   `RibbonNav.tsx` bei der Menüpunkt-Zeile; `sky-100`/`sky-600` in `confirm-dialog.tsx`.
   Alles für Task 10.

### Auftrag des Code-Reviewers — alle fünf Punkte abgearbeitet

1. **Zweizeilige Reiterleiste bei 1440 bestätigt und beurteilt.** Ja, sie ist zweizeilig
   (6 + 1). Beurteilung und ein nachgemessener Gegenvorschlag: siehe Schwerpunkt 1.
2. **Seine Kernbehauptung stimmt — belegt.** `AnfrageEditor.tsx` testweise auf
   `69fb5c7f~1` zurückgedreht und `E2E_PORT=5216 npx playwright test e2e/anfrage-layout.spec.ts`
   gefahren: **2 grün, 2 rot.** Grün blieben beide Läufe der **Detailseite**
   (`pc-14zoll` und `pc-monitor`) — die Zusicherungen zu Kopfzeile und Reiterleiste
   halten dort also tatsächlich nichts fest. Rot wurden beide Läufe der **Übersicht**,
   mit der erwarteten Begründung: `keinTextLaeuftUeber` meldete 276–312 px Überstand auf
   den vier Kartentiteln, Selektor `h3…truncate…` (also der alte einzeilige Titel).
   Test-belegt ist damit ausschließlich die Übersichts-Änderung (Kartenraster +
   `line-clamp-2`), nicht die Kopf-/Reiterleisten-Härtung. Datei danach mit
   `git checkout HEAD -- react-pc-frontend/src/pages/AnfrageEditor.tsx` restlos
   zurückgenommen, `git status` leer (kein `git stash` verwendet).
   Meine Bewertung: das ist **kein 🔴**. Der Coding-Agent hat es im Kontext-Log selbst
   offengelegt, der Plan schreibt die Bauweise für Task 4 wörtlich vor, und die Spec
   bleibt als Regressions-Wächter sinnvoll — Anfrage kippt sonst beim nächsten neuen
   Reiter genauso wie Projekt.
3. **Projekt-Kopfzeile bei 1440 nachgemessen.** Er rechnet richtig, aber es sind zwei
   Zeilen, nicht drei: Titelblock (x=89, 548 px breit) und Kennzahlen (x=653, 698 px) in
   Zeile 1, der Knopfblock (390 px) in Zeile 2 — dort **linksbündig am Kartenrand**
   (x=89), also unten links statt oben rechts. Ob das gut aussieht: nein, es sieht nach
   Umbruchunfall aus, weil die Aktionen zwischen den beiden Bildschirmgrößen die Seite
   wechseln. Was stattdessen zu tun ist: `ml-auto` an den Knopfblock — gemessen rückt er
   damit bei 1440 auf x=961 (rechtsbündig unter die Kennzahlen), bei 1920 bleibt alles
   wie es ist. Siehe Schwerpunkt 2.
4. **`min-h-[3rem]` mit kurzen Namen angesehen.** Ja, es entsteht unschöner Leerraum:
   48 px Titelhöhe für 24 px Text, also 24 px Lücke, die den Kundennamen vom Titel
   losreißt — in beiden Größen. Details, Bild und ein besserer Vorschlag (`h-full
   flex flex-col` + `mt-auto` statt `min-h`): siehe Abschnitt H.
5. **Voller E2E-Lauf** über alle Specs in beiden Größen: 186 grün, 0 rot (siehe oben).

Zusätzlich, unaufgefordert, zwei eigene Mutationen per `addStyleTag` (ohne Quellcode
anzufassen): Rückbau der Kopfzeile auf die alte Bauweise — reproduziert den alten Fehler
sichtbar (`zz-projekt-kopf-ALT--pc-14zoll.png`); und `ml-auto` + `gap-1`/`px-2` — belegt
beide Empfehlungen mit Zahlen und Bild.

### Aufräumen

Wegwerf-Specs `e2e/zz-messung-design.spec.ts`, `e2e/zz-messung-kunde.spec.ts` und
`e2e/zz-kurze-namen.spec.ts` gelöscht, der versehentlich angelegte Ordner
`react-pc-frontend/design/` entfernt, die Testrückdrehung an `AnfrageEditor.tsx`
zurückgenommen. `git status` im Review-Worktree sauber, keine Änderung an Produktivcode.

## Abschnitt 4 — Task 6 (Coding-Agent)

Zeit: 2026-09-06T11:46:27Z
Branch: layout/task-6-lieferant
Commit(s): eaa7ed2b (test), 7c572241 (fix)
Status: fertig

Was gemacht wurde:
- Testgetrieben (Skill `superpowers:test-driven-development` befolgt): `react-pc-frontend/e2e/lieferant-layout.spec.ts` (neu) zuerst rot geschrieben und verifiziert, dann `LieferantenEditor.tsx` nach der "Gemeinsamen Rezeptur fuer Kopfzeile und Reiterleiste" (Abschnitt 4 im Plan) umgebaut.
- Kopfzeile (Z. 67 ff.): aeusseres `div` von `flex flex-col xl:flex-row gap-8 justify-between` auf `flex flex-wrap items-start gap-4`. Titelblock `flex-1 min-w-[18rem]`, innerer Textblock zusaetzlich `min-w-0`, `<h1>` mit `break-words`, Icons der Untertitel-Zeilen (`User`, `MapPin`) `shrink-0`. Die vier Kennzahl-Kaesten ("Gesamtkosten", "Bestellungen", "Artikel", "Lieferzeit Ø" — **vier, nicht sechs**, siehe Bedenken 1) verlieren `flex-1 max-w-4xl` und werden `flex flex-wrap gap-4 shrink-0` mit je `min-w-[7rem]`; Farben (slate/purple/blue/emerald) unveraendert gelassen (Plan: "Bewusst nicht in diesem Vorhaben"). Knopfblock ("Bearbeiten") auf `shrink-0 ml-auto flex flex-wrap items-start gap-2`. `data-testid="lieferant-kennzahlen"` auf den Kennzahlen-Container ergaenzt (fuer die Spec — "Artikel" ist sonst mehrdeutig, siehe auch RibbonNav-Menuepunkt "Artikel").
- Reiterleiste (Z. 141 ff.): `flex-wrap min-w-0` ergaenzt, Reiter-Knoepfe `px-4` → `px-3`.
- Uebersichtskarte (`LieferantCard`, Z. 763 ff.): Titel `truncate` → `line-clamp-2` + `data-kuerzung-erlaubt` (`title` bleibt). Karte bekommt `h-full flex flex-col`, der Kontaktblock (Adresse/Telefon/Vertreter unterhalb der Trennlinie) `mt-auto` — **kein** `min-h-[3rem]` (Design-Review Abschnitt 3: das reisst bei kurzen Namen eine Luecke). Kartenraster (Z. 723) `xl:grid-cols-4` → `2xl:grid-cols-4`.
- Zusatzfund in eigener Datei (kein Fremdbefund): die E-Mail-Adresse in der Kontaktdaten-Spalte (sideContent, Z. ~309) lief mit `truncate` bei "bestellung@beispiel-stahl.example" 49px ueber ihren Kasten (`keinTextLaeuftUeber` in `designPruefung(..., strengePruefungen: true)` fand das). Auf `break-words` umgestellt statt Kuerzung, analog zur Empfehlung des Code-Reviewers fuer denselben Fall im Projekt-Editor (Abschnitt-3-Review, Bedenken 4).
- `LieferantenEditor.test.tsx` unveraendert gelassen (per Grep keine Layout-Klassen-/Reiter-Text-Assertion betroffen) — 9/9 weiterhin gruen, bleibt daher ausserhalb der `Files`-Liste.
- Alias/Ort/Kontakt/Vertreter der Uebersichtskarte bewusst bei `truncate` belassen ("nach Befund der Pruefung", Plan-Wortlaut): mit den vier Testnamen (kurze Orte/Namen) fand `keinTextLaeuftUeber`/`keinTextGekuerzt` dort keinen Treffer, also nichts zu reparieren.

Gemessene Zahlen (Playwright-`boundingBox()`, `/lieferanten?lieferantId=21&tab=dokumente`, Name "Stahlhandel Beispiel GmbH und Co. KG"; vorher-Zahlen per `git diff > patch` + `git checkout --` + `git apply` sauber am unveraenderten Stand gemessen, danach zurueckgespielt — kein `git stash` benutzt):

| Messung | vorher (1440) | nachher (1440) | vorher (1920) | nachher (1920) |
| --- | --- | --- | --- | --- |
| Kasten "Gesamtkosten" Breite | 94,8px | 126,9px | 150,8px | 126,9px |
| Kasten "Bestellungen" Breite | 94,8px | 112,9px | 150,8px | 112,9px |
| Kasten "Artikel" Breite | (n/b, Grid gleichverteilt ≈94,8px) | 112,0px | (n/b ≈150,8px) | 112,0px |
| Kasten "Lieferzeit Ø" Breite | (n/b ≈94,8px) | 112,0px | (n/b ≈150,8px) | 112,0px |
| Knopf "Bearbeiten" x | 1218,7 | 1218,7 | 1570,7 | 1570,7 |
| Kopf-Karte x / rechte Kante | 64 / 1376 | 64 / 1376 | 192 / 1728 | 192 / 1728 |
| Reiterleiste scrollWidth/clientWidth | 916/916 | 916/916 | 1084/1084 | 1084/1084 |
| Reiterleiste overflowX | visible | visible | visible | visible |

Einordnung: Die Kennzahl-Kaesten sind der einzige Wert, der sich sichtbar aendert (34px/29px "zu wenig" aus der Spec bestaetigt: vorher 94,8px, jetzt ≥112px). Knopf-x und Reiterleisten-Breite sind bei Lieferant **vorher und nachher identisch** — anders als beim Projekt-Editor bricht die Lieferanten-Kopfzeile bei 1440/1920 mit nur vier Kennzahlen + einem Knopf gar nicht um, und die vier kurzen Reiter-Beschriftungen (E-Mail-Verlauf/Dokumente/Notizen/Reklamationen) passten schon vorher einzeilig hinein. `ml-auto` und `flex-wrap` sind hier reine Zukunftssicherung (laengerer Name, weitere Rollen-Chips oder eine fuenfte Kennzahl wuerden sonst denselben Linksrutsch wie beim Projekt-Editor ausloesen) und die neue `overflowX==='visible'`-Zusicherung ist der Regressionswaechter dafuer, wie von der Rezeptur verlangt.

Ergebnis der Gates:
- Port-Check: `netstat -ano | findstr :5206` leer (vor jedem Lauf geprueft).
- `E2E_PORT=5206 npx playwright test e2e/lieferant-layout.spec.ts`: **4/4 gruen**, mehrfach reproduziert (mit `--workers=1` und mit den Standard-4-Workern). Einmal traten bei einem Zwischenstand (Header-Fix ohne den E-Mail-Fix, Standard-4-Worker) alle 4 Tests mit einer komplett leeren weissen Seite auf, ohne Konsolen-/Playwright-Fehler — reproduzierbar behoben durch `--workers=1`; nach dem E-Mail-Fix liefen auch die Standard-4-Worker wieder durchgehend gruen (zweimal bestaetigt). Deckt sich mit den in Abschnitt 1/3 dieses Logs dokumentierten Last-Effekten durch parallel laufende Agenten auf derselben Maschine — kein eigener Befund an Code oder Spec.
- `npx vitest run src/pages/LieferantenEditor.test.tsx`: **9/9 gruen**, unveraendert zur Baseline.
- `npm run lint`: 0 Fehler, 1 vorbestehende Warnung (`BelegeKasseEditor.tsx:1204`, identisch zur Baseline).
- `npm run build`: gruen. Build-Output (`src/main/resources/static/index.html`, `assets/*`) vor dem Commit verworfen (`git checkout -- src/main/resources/static` + `git clean -fd src/main/resources/static/assets`), `git status` danach sauber bis auf die zwei eigenen Dateien.

Bedenken / Abweichungen vom Plan:
- **Falschbehauptung im Auftrag berichtigt: Lieferant hat vier Kennzahlen, nicht sechs.** Der Coding-Auftrag ("Der Lieferant hat sechs Kennzahlen — mehr als Projekt (fünf)...") und sogar das Kontext-Log-Abschnitt-3-Review ("Task 6 (Lieferant) hat sechs Kennzahl-Kästen") behaupten sechs. Tatsaechlicher Code (`LieferantenEditor.tsx` Z. 110-127) und `LieferantStatistik` (`types.ts` Z. 107-113) haben genau vier: Gesamtkosten, Bestellungen, Artikel, Lieferzeit Ø — identisch mit dem Screenshot `docs/superpowers/specs/bilder/2026-09-04-layout-14-zoll/lieferant-detail-1440.png` und mit dem Task-6-Plantext selbst ("jeder der vier Kästen `min-w-[7rem]`", Pruefwort "Gesamtkosten"). Ich bin dem tatsaechlichen Code und dem expliziten Plan-Wortlaut gefolgt (vier Kaesten), nicht der Zusammenfassung im Auftrag — die Rezeptur (Robustheit, `min-w-[7rem]`, `ml-auto`) gilt unabhaengig von der Anzahl unveraendert. Sollte spaeter tatsaechlich eine fuenfte/sechste Kennzahl dazukommen, ist die Bauweise dafuer bereits vorbereitet (flex-wrap, kein flex-1/max-w).
- **Kennzahlen-Abstand `gap-4` statt `gap-x-6 gap-y-2`:** Die "Gemeinsame Rezeptur" nennt allgemein `flex flex-wrap gap-x-6 gap-y-2 shrink-0` (Projekt/Anfrage-Stil mit Trennlinien-Spalten). Der Task-6-eigene Plantext sagt explizit `flex flex-wrap gap-4 shrink-0` (wie beim bestehenden Bento-Kachel-Stil von Kunde/Lieferant). Ich habe den spezifischeren Task-6-Wortlaut befolgt und `gap-4` verwendet, um den Bento-Stil (bewusst unveraendert laut Plan) nicht durch einen anderen Abstand zu verfremden.
- **`ml-auto` ist bei den beiden Pruefgroessen (1440/1920) faktisch wirkungslos** (siehe Tabelle: Knopf-x identisch vorher/nachher), weil die Lieferanten-Kopfzeile mit nur vier Kennzahlen + einem Knopf bei keiner der beiden Groessen umbricht. Trotzdem wie von der Rezeptur verlangt umgesetzt — zukunftssicher gegen laengere Namen, weitere Rollen-Chips oder eine zusaetzliche Kennzahl.
- **E-Mail-Adresse in der Kontaktdaten-Spalte auf `break-words` umgestellt** (eigene Datei, kein Fremdbefund, siehe oben) — nicht explizit im Task-6-Plantext genannt, aber von der eigenen `strengePruefungen`-Zusicherung gefunden und deshalb im selben Task behoben, wie es der Plan fuer Funde in der eigenen Datei vorsieht.
- **Blank-Page-Flakiness bei Standard-Parallelitaet** (siehe Gate-Ergebnisse) — reproduzierbar durch `--workers=1` behoben, kein eigener Befund, deckt sich mit fruehren Beobachtungen in diesem Log zu Maschinenlast durch parallele Agenten.
- Sonst keine Abweichungen: Global Constraints, "Gemeinsame Rezeptur" und Task 6 vollstaendig gelesen und wie beschrieben umgesetzt. Keine Datei ausserhalb der eigenen `Files`-Liste angefasst, keine Kennzahl-Farbe angetastet, Dummy-Daten nur Fantasienamen (Stahlhandel Beispiel GmbH und Co. KG, Baubeschläge und Verbindungstechnik Musterhausen Vertriebs GmbH, Beschichtungs- und Verzinkereibetrieb Beispieltal eingetragene Genossenschaft, Aluminium- und Edelstahlhandel Nordmuster Handelsgesellschaft mbH; E-Mail nur `.example`-TLD), `/api` vollstaendig gestubbt (Catch-all + Overrides, Vorbild `stubbeLieferantApi`).

## Abschnitt 4 — Task 7 (Coding-Agent)

Zeit: 2026-09-06T11:45:46Z
Branch: layout/task-7-mitarbeiter
Commit(s): 35bca3be
Status: fertig

Was gemacht wurde:
- Pflichtlektuere vollstaendig gelesen (FRONTEND_UI.md, handwerkerprogramm-design SKILL.md+README.md als Dateien -- der Skill ist im Skill-Tool dieser Sitzung nicht registriert, deckt sich mit dem Befund aus Task 3/5 in Abschnitt 3 -- stattdessen `ui-ux-pro-max` als Skill-Tool-Aufruf fuer den DESIGN-SKILL-GUARD-Hook, wie im eigenen Auftrag vorgesehen; playwright-design-pruefung SKILL.md, kriterien.md, kontext-log-format.md, e2e/hilfen/design.ts, e2e/kunde-layout.spec.ts als Vorbild) sowie Plan-Block (Global Constraints, "Gemeinsame Rezeptur fuer Kopfzeile und Reiterleiste", Task 7 vollstaendig) und die Abschnitt-3-Bloecke (Task 3/4/5, Code-Review, Design-Review) im Kontext-Log.
- `MitarbeiterEditor.tsx`, Reiterleiste (Tab Navigation, urspruenglich Z. 728): `overflow-x-auto` raus, `flex-wrap min-w-0` rein, alle vier Reiter-Knoepfe (Dokumente, Notizen, Lohnabrechnungen, Stundenlohn-Verlauf) `px-4` -> `px-3`, `data-testid="mitarbeiter-reiterleiste"` fuer eine praezise Test-Auswahl (Vorbild: `data-testid="kunde-reiterleiste"` aus Task 5). Sonst nichts an dieser Datei geaendert -- Kopf und Karten sind nicht Teil des Plan-Textes fuer Task 7.
- Testgetrieben (Skill `superpowers:test-driven-development` befolgt): `e2e/mitarbeiter-layout.spec.ts` (neu). Kein Deep-Link (`MitarbeiterEditor` liest keinen Query-Parameter) -- die Spec geht auf `/mitarbeiter`, wartet auf die Liste und klickt die Karte des Dummy-Mitarbeiters. `/api` per Catch-all + gezielte Overrides vollstaendig gestubbt (Vorbild `stubbeLieferantApi`), kein Backend.
- Ueber den Plan-Text hinaus, wie vom Orchestrator-Auftrag verlangt ("Prüf beim Schreiben der Spec aber die ganze Seite gegen die Rezeptur"): Kopfzeile zusaetzlich gegen die Rezeptur geprueft (drei Knoepfe "Token erstellen"/"Zurueck"/"Bearbeiten", Warnung vor dem "ml-auto"-Fall aus Abschnitt 3). Keine Aenderung an der Kopfzeile vorgenommen (siehe Bedenken).
- DSGVO: ausschliesslich ein erfundener Mitarbeiter (Vorname "Bernhardine", Nachname "Beispielmusterfrauenbergwaldschmidtstein" -- bewusst aus den Fantasie-Wortstaemmen "Beispiel"/"Muster" gebaut, ein einziges, nicht umbrechbares 41-Zeichen-Wort ohne Leerzeichen/Bindestrich, um den Komposita-Fall aus dem Code-Review-Bedenken 2 zu Abschnitt 3 nachzustellen), keine echte Adresse (alle Adressfelder `null`), kein echter Lohn (`stundenlohn: null`).

Gemessene Zahlen vorher/nachher (eigene Wegwerf-Messspecs, nicht committet, vor/nach dem Fix getrennt gefahren):
- Reiterleiste vorher (px-4, overflow-x-auto): bei 1440px `scrollWidth === clientWidth === 916px` (0px Ueberstand, "auto"), rechte Kante des letzten Reiters ("Stundenlohn-Verlauf") bei x=715,6 von 916px verfuegbar. Bei 1920px `scrollWidth === clientWidth === 1084px`, rechte Kante bei x=843,6 von 1084px verfuegbar. Reiterleiste passt also bereits vor dem Fix in beiden Groessen einzeilig -- deckt sich mit der Plan-Aussage "passt heute bei 1440".
- Reiterleiste nachher (px-3, flex-wrap min-w-0): bei 1440px `scrollWidth === clientWidth === 916px`, rechte Kante bei x=683,6 (32px weniger Bedarf durch `px-3`). Bei 1920px rechte Kante bei x=811,6. `overflowX` jetzt `'visible'` (vorher `'auto'`). Alle vier Reiter-y-Werte identisch (409 bzw. 373) -- einzeilig in beiden Groessen, vor und nach dem Fix.
- Kopfzeile (unveraendert, mit der 41-Zeichen-Fixture): bei 1440px `main`-Ueberstand 0px, Knopfblock (3 Knoepfe) x=987,9 bis right=1376 (Kartenrand), h1 einzeilig bei 907,9px zugewiesener Breite. Bei 1920px `main`-Ueberstand 0px, Knopfblock x=1313,2 bis right=1728. Das rechte Seitenpanel (Kontaktdaten-Karte, laut Plan explizit "nicht Teil der Spec") wurde separat mit derselben Extrem-Fixture gegen `keinHorizontalerUeberlauf`/`keinTextLaeuftUeber`/`keinTextGekuerzt` geprueft: alle drei liefen in beiden Groessen fehlerfrei durch.

Welche Zusicherungen auf dem Alt-Stand rot waren und welche nicht:
- Echt rot vor dem Fix: `expect(reiterButtons).toHaveCount(4)` ueber `getByTestId('mitarbeiter-reiterleiste')` (Attribut existierte noch nicht -- 0 statt 4 gefunden) und in der Folge `getComputedStyle(reiterleiste).overflowX === 'visible'` (Wert war `'auto'`). Beide Zusicherungen sind unabhaengig von jeder Fixture rot, weil sie Stil/Struktur direkt pruefen statt einen Ueberlauf in Pixeln.
- Nicht rot, ausdruecklich hier festgehalten (aus Abschnitt 3 gelernt: eine nie-rote Zusicherung haelt sonst nichts fest): die Reiterleisten-Zusicherungen zu Ueberlauf/Zeilenzahl (`reiterSpanne <= 2px`) waren mit der 41+44-Zeichen-Fixture vor und nach dem Fix gruen -- die vier Reiter-Beschriftungen ("Dokumente", "Notizen", "Lohnabrechnungen", "Stundenlohn-Verlauf") sind statische Strings ohne dynamische Zaehler; anders als bei Projekt/Anfrage/Kunde/Lieferant kann keine Fixture hier einen datenabhaengigen Ueberlauf erzwingen, weil die Reiterbreite nicht von den Testdaten abhaengt. Ebenso nicht rot: alle Kopfzeilen-Zusicherungen (drei Knoepfe sichtbar, im Viewport, x > Bildschirmmitte) -- auch mit der bewusst extremen 41-Zeichen-Fixture, weil die Mitarbeiter-Kopfzeile (anders als Projekt/Anfrage/Kunde/Lieferant) keine Kennzahlen-Reihe hat, die dem Titelblock Platz wegnimmt: der Knopfblock (3 Knoepfe, kein Kennzahlen-Kasten) braucht nur rund 390px von 1376px verfuegbarer Breite bei 1440px, der Titelblock bekommt den Rest und hat damit selbst fuer ein 41-Zeichen-Wort genug Raum. Auch `designPruefung(..., { strengePruefungen: true })` auf der vollen Seite (inkl. Seitenpanel) war vor und nach dem Fix gruen.

Bedenken / Abweichungen vom Plan:
- Bewusste Aufloesung eines Widerspruchs zwischen Auftrag und Plan, Plan hat gewonnen (wie in der Auftrags-Praeambel vorgeschrieben): Der Orchestrator-Auftrag verlangte, die Kopfzeile ("Token erstellen"/"Zurueck"/"Bearbeiten") gegen die Rezeptur zu pruefen und warnte konkret vor dem "Knopfblock faellt ohne ml-auto beim Umbruch nach links unten"-Fall aus Abschnitt 3. Der Plan-Text fuer Task 7 schreibt dagegen woertlich: "Sonst nichts an dieser Datei -- Kopf und Karten der Mitarbeiterseite sind nicht Teil der Spec." Ich habe die Kopfzeile geprueft (Spec-Zusicherungen, extreme Fixture, siehe oben), aber nicht veraendert -- die Messung zeigt, dass die Warnung fuer diese konkrete Datei nicht zutrifft: `DetailHeader` in `MitarbeiterEditor.tsx` ist strukturell anders als in Projekt/Anfrage/Kunde/Lieferant (kein umschliessendes `<Card>`, kein `flex-wrap` auf dem aeusseren Container, `flex flex-col md:flex-row justify-between gap-4 md:items-end` statt der Rezeptur-Bauweise, und vor allem: keine Kennzahlen-Reihe, die dem Titelblock in den anderen vier Editoren den Platz streitig macht). Ohne eine Kennzahlen-Reihe, die Platz wegfrisst, tritt der "ml-auto"-Fall hier nicht auf, selbst mit einer bewusst pathologischen 41-Zeichen-Fixture nicht (0px Ueberstand in beiden Groessen, Knoepfe vollstaendig rechts, siehe Messung oben). Ich habe deshalb den Plan befolgt (nichts an der Kopfzeile geaendert) und die Abweichung vom Auftrag hier dokumentiert, wie in der Praeambel verlangt ("weicht der Plan ab, gilt der Plan, und du meldest die Abweichung im Kontext-Log").
- Reiterleisten-Ueberlauf war auf dem Alt-Stand nicht erzwingbar, siehe oben. Anders als bei Projekt (dynamische Zaehler "(n)" an sieben Reitern) sind Mitarbeiters vier Reiter-Beschriftungen statische Strings. Die einzige echte Rot-Zusicherung ist deshalb die direkte Stil-Pruefung `overflowX === 'visible'`, nicht ein Pixel-Ueberlauf. Nach bestem Bemuehen (41+44-Zeichen-Fixture, wie im Auftrag verlangt "langer Nachname, lange Abteilung") liess sich kein datenabhaengiger Ueberlauf-Befund erzeugen -- das ist plan-konform ("Mitarbeiter-Detailseite passt heute bei 1440"), nicht ein Versagen der Fixture-Suche.
- Kein Qualifikationstext in der Fixture verwendet (im Auftrag als dritte Beispiel-Fixture genannt): `qualifikation` wird nirgends in Kopfzeile oder Reiterleiste angezeigt (nur im rechten Seitenpanel, das laut Plan explizit ausserhalb der Spec liegt) -- ein langer Qualifikationstext haette also nichts zusaetzlich getestet und wurde deshalb bewusst weggelassen (`qualifikation: null` in der Fixture).
- Keine Uebersichtskarte mit `truncate` in dieser Datei (per `grep -n truncate src/pages/MitarbeiterEditor.tsx`: null Treffer, gegengeprueft): weder die Mitarbeiter-Listenkarten (`<h3>{m.nachname}, {m.vorname}</h3>`, reiner Fliesstext ohne Kuerzung) noch sonst irgendein Element in der Datei nutzen `truncate`/`line-clamp`. Der dritte Pruefpunkt aus dem Auftrag ("falls es eine Uebersichtskarte mit truncate gibt, auch die") entfaellt damit ersatzlos -- nichts zu tun, nichts markiert.
- Sonst keine Abweichungen: Global Constraints vollstaendig gelesen und eingehalten (Design-System rose/slate/Lucide/kein Emoji, keine Aenderung ausserhalb `react-pc-frontend/`, kein `git stash` verwendet, Build-Output vor dem Commit verworfen).

Ergebnis der Gates:
- Port-Check: `netstat -ano | findstr :5207` zeigte ausschliesslich `WARTEND`-Reste (TIME_WAIT) aus eigenen vorherigen Laeufen dieser Sitzung, keine `LISTENING`-Zeile eines fremden Prozesses -- Port 5207 wie vorgegeben verwendet.
- `E2E_PORT=5207 npx playwright test e2e/mitarbeiter-layout.spec.ts`: 2/2 gruen (1 Testfall x pc-14zoll + pc-monitor), zusaetzlich mit `--workers=1` deterministisch reproduziert. Vorher (unveraenderter Stand): reproduzierbar rot wegen des fehlenden `data-testid` (siehe oben).
- Kein Vitest-Lauf: es gibt keine `MitarbeiterEditor.test.tsx` (per `ls src/pages/ | grep -i mitarbeiter` bestaetigt), wie vom Plan vorhergesagt.
- `npm run lint`: 0 Fehler, genau die 1 vorbestehende Warnung (`BelegeKasseEditor.tsx:1204`, `react-hooks/exhaustive-deps`) -- identisch zur Baseline.
- `npm run build`: gruen (`tsc -b` + `vite build`). Build-Output (`src/main/resources/static/index.html`, `assets/*`) vor dem Commit verworfen (`git checkout -- src/main/resources/static` + `git clean -fd src/main/resources/static/assets`, ausgefuehrt aus dem Worktree-Wurzelverzeichnis), `git status` danach sauber bis auf die zwei eigenen Dateien.

## Abschnitt 4 — Task 3b (Coding-Agent)

Zeit: 2026-09-06T12:00:26Z
Branch: layout/task-3b-nacharbeit (Worktree `wt/layout-task-3b`, abgezweigt von `feature/layout-14-zoll` @ 7aabfe16, dem abgenommenen Stand von Abschnitt 3)
Commit(s): 5ed821f8 (Projekt), 19deaf3b (Anfrage), ea1d1d2d (Kunde)
Status: fertig

Was gemacht wurde (Punkt für Punkt aus dem Auftrag, alle testgetrieben — rot verifiziert per Patch aus `git diff`, `git checkout --` auf die Produktivdatei(en), Spec/Probe rot gefahren, Patch zurückgespielt, grün gefahren; kein `git stash` verwendet):

1. **Vier `truncate`-Stellen im Projekt-Editor ohne `title`** (Kunden-E-Mails rechte Spalte, Lieferantenname + Dateiname der Eingangsrechnung, E-Mail-Betreff/`dok.betreff`) auf `break-words` umgestellt. Rot verifiziert mit verlängerten Fixture-Werten: Betreff (149 Zeichen) und Kunden-E-Mail (99 Zeichen) laufen bei 1440 und 1920 zuverlässig über; der Dateiname musste auf ~200 Zeichen verlängert werden, weil eine erste Fassung (108 Zeichen) knapp innerhalb der ~779px breiten Zeile blieb. Einschränkung, offen gelegt statt verschwiegen: Der Lieferantenname bleibt exakt die mandatierte Spec-Konstante „Stahlhandel Beispiel GmbH und Co. KG" (35 Zeichen) — per `page.evaluate` nachgemessen: Span 282px breit in einer 779px breiten Zeile, also kein Überlauf bei den heutigen Spaltenbreiten. Die vierte Zusicherung hält deshalb nur 3 von 4 Stellen rot fest; die Korrektur (`break-words`) ist trotzdem umgesetzt, weil sie bei einem künftig längeren echten Lieferantennamen greift.
2. **Reiterleiste des Projekt-Editors einzeilig**: `gap-2` zu `gap-1`, `px-3` zu `px-2` an allen sieben Reiter-Knöpfen. Gemessen: Bedarf jetzt 916px bei 916px verfügbar (1440) bzw. 1084px bei 1084px verfügbar (1920) — beide exakt an der Kante, aber innerhalb der Toleranz. Zusicherung verschärft von „nichts verschwindet, Umbruch erlaubt" auf „alle sieben Reiter haben dieselbe y-Position" (Toleranz 2px). Rot verifiziert am unveränderten Abschnitt-3-Stand: y-Werte 593, 593, 593, 593, 593, 593, 639 (sechs plus eine Zeile) bei 1440.
3. **`ml-auto` am Knopfblock** in allen drei Dateien nachgezogen, Specs zusichern jetzt Knopfblock-x größer als Kartenmitte-x statt nur „irgendwo in der Karte". Rot verifiziert am Projekt (der einzigen Datei, deren Kopfzeile mit den Spec-Fixtures tatsächlich umbricht): ohne `ml-auto` x=89 (exakt der vom Design-Reviewer gemessene Wert), mit `ml-auto` x=961, Kartenmitte bei 720. Bedenken: Bei Anfrage und Kunde greift dieselbe Zusicherung nicht wirklich, weil die Kopfzeile dort mit den heutigen Fixtures (je zwei Kennzahlen) nie umbricht — die Probe (ml-auto entfernt, Anfrage-Detailtest gefahren) blieb grün. Die Zusicherung bleibt trotzdem als Regressionsschutz stehen.
4. **`min-h-[3rem]` ersetzt** durch `h-full flex flex-col` an der Karte plus `mt-auto` am Meta-Block, in allen fünf betroffenen Karten (ProjektCard, AnfrageCard, KundenKarte, KundenProjektKarte, KundenAnfrageKarte). Rot verifiziert per direkter Höhenmessung des Titel-Elements (nicht per Lücke-zum-Nachbarn — dieser erste Ansatz maß den Fehler nicht, weil `min-h-[3rem]` den Leerraum innerhalb der eigenen Titel-Box reserviert, nicht dahinter; korrigiert): mit `min-h-[3rem]` 48px Boxhöhe für einzeiligen Text, ohne 24px — Zusicherung verlangt unter 32px. Dauerhafte Tests für Anfrage (kurzes Bauvorhaben reisst keine Luecke) und Kunde (kurzer Kundenname reisst keine Luecke) neu in den jeweiligen Specs. Für Projekt nicht dauerhaft testbar in meiner Files-Liste (ProjektCard wird nur auf /projekte gerendert, die Übersichts-Spec projekt-uebersicht-layout.spec.ts gehört Task 3/9, nicht Task 3b) — stattdessen mit einer Wegwerf-Spec verifiziert (rot: 48px, grün: 24px) und danach gelöscht, wie im vorigen Design-Review vorgemacht. Empfehlung an Task 9/10: einen kurzen-Titel-Testfall in projekt-uebersicht-layout.spec.ts ergänzen.
5. **Marker an der Dokumentnummer entfernt** (Kundeneditor.tsx, KundenDokumentKarte): `line-clamp-2` + `data-kuerzung-erlaubt` ersetzt durch `break-words` ohne Marker, `title` bleibt. Die Herkunftszeile („Projekt: …") direkt darunter behält Marker und `title` unverändert.
6. **Bauweise angeglichen**: `min-w-0` am inneren Textblock in Projekt und Anfrage nachgezogen (Kunde hatte es bereits); `shrink-0` an den Untertitel-Icons in Projekt und Kunde nachgezogen (Anfrage hatte es bereits).
7. **Reiterleisten-Zusicherung** `getComputedStyle(leiste).overflowX === 'visible'` in allen drei Specs nachgerüstet. Zur Probe je einmal `overflow-x-auto` wieder eingesetzt: bei Kunde rot verifiziert (Meldung „Reiterleiste hat overflow-x: auto"), danach zurückgenommen. Projekt und Anfrage tragen dieselbe Zusicherung (rot am unveränderten Stand über die kombinierte Reiterleisten-Prüfung bzw. den Tagebuch-Locator-Timeout mitbelegt, siehe Punkt 2 und 9).
8. **Anfrage-Spec scharf gemacht**: neue Fixture mit einem Komposita-Bauvorhaben ohne Leerzeichen (79 Zeichen, ein Wort) plus eigener Testfall „Kopfzeile uebersteht ein langes Komposita-Bauvorhaben ohne Leerzeichen". Bestätigt die Kernbehauptung des Code-Reviewers/Design-Reviewers: der bestehende Test mit Leerzeichen-Bauvorhaben hält die Kopf-/Reiterleisten-Härtung nicht fest (Rückdreh-Probe des Design-Reviewers: 2 grün, 2 rot — nur die Übersicht wird rot). Die neue Fixture prüft dieselben Zusicherungen (Knöpfe in der Kopf-Karte, kein horizontaler Überlauf, designPruefung).
9. **Zwei Kleinigkeiten**: (a) Kunde-Mini-Karten laufen nach jedem Reiter-Klick jetzt mit voller `designPruefung(..., { strengePruefungen: true })` statt nur `keinTextGekuerzt` (drei neue Screenshots kunde-mini-karten-projekte/anfragen/dokumente). (b) Meldungstext in kunde-layout.spec.ts korrigiert: „fehlt oder ist gekuerzt" wird zu „nicht im DOM gefunden" (die Kürzung selbst prüft designPruefung weiter unten). Zusätzlich: Anfrage-Reiter „Bau Tagebuch" wird zu „Tagebuch" (Projekt sagt das seit Abschnitt 3 bereits), Locator in der Spec mitgezogen; die interne Überschrift „Bau Tagebuch" im Tab-Inhalt bleibt unangetastet, wie beim Projekt-Editor in Abschnitt 3.

Kein Punkt war bereits grün — jeder brauchte eine echte Code-Änderung.

Ergebnis der Gates (aus react-pc-frontend/, Port 5217 frei, netstat -ano | findstr :5217 leer):
- E2E_PORT=5217 npx playwright test e2e/projekt-detail-layout.spec.ts e2e/anfrage-layout.spec.ts e2e/kunde-layout.spec.ts: 16/16 grün (8 Testfälle × pc-14zoll + pc-monitor), erneut gegen den finalen committeten Stand nachgefahren.
- npx vitest run src/pages/ProjektEditor.test.tsx src/pages/AnfrageEditor.test.tsx src/pages/Kundeneditor.test.tsx: 15/15 grün, keine Regression durch die Reiter-Umbenennung.
- npm run lint: 0 Fehler, genau die 1 vorbestehende Warnung (BelegeKasseEditor.tsx:1204) — identisch zur Baseline. (Ein selbst verursachter Lint-Fehler — ungenutztes testInfo in einem neuen Kunde-Testfall — wurde noch vor dem Commit behoben.)
- npm run build: grün (tsc -b + vite build). Build-Output vor jedem Commit verworfen (git checkout -- src/main/resources/static + git clean -f src/main/resources/static/assets), git status danach sauber.

Bedenken / Abweichungen vom Plan:
- Lieferantenname (Punkt 1) überläuft mit der mandatierten Fixture nicht — siehe Punkt 1 oben. Kein Widerspruch zum Auftrag (die Korrektur ist trotzdem umgesetzt), aber die Zusicherung deckt diese eine von vier Stellen nicht rot ab.
- ml-auto-Zusicherung bei Anfrage und Kunde ungeprüft (Punkt 3) — die Kopfzeile bricht dort mit den heutigen Fixtures nie um, die Probe (Klasse entfernt) blieb grün. Bleibt als Regressionsschutz stehen.
- Projekt-Kartentitel-Lücke (Punkt 4) nicht dauerhaft testbar in meiner Files-Liste — mit Wegwerf-Spec verifiziert und gelöscht. Empfehlung an Task 9/10 oben.
- Eigener Messfehler unterwegs korrigiert, nicht nur im Nachhinein erwähnt: Der erste Ansatz für Punkt 4 (Lücke zwischen Titel-Box und nächstem Geschwister) maß den falschen Abstand, weil min-h-[3rem] den Leerraum innerhalb der eigenen Box reserviert. Auf direkte Höhenmessung der Titel-Box umgestellt, an beiden betroffenen Specs (Anfrage, Kunde) sowie der Wegwerf-Spec (Projekt) korrigiert und erneut rot/grün verifiziert.
- Sonst keine Abweichungen vom Plan-Block (Global Constraints, Gemeinsame Rezeptur und Task 3b vollständig gelesen und wie beschrieben umgesetzt).

## Abschnitt 4 — Code-Review (Code-Reviewer)

Zeit: 2026-09-06T14:25:00Z
Branch: feature/layout-14-zoll, Prüfstand `1b5627de` (Merges von `layout/task-3b-nacharbeit`, `layout/task-6-lieferant`, `layout/task-7-mitarbeiter`)
Commit(s): eaa7ed2b + 7c572241 (Task 6), 35bca3be (Task 7), 5ed821f8 + 19deaf3b + ea1d1d2d (Task 3b), gemergt in f3ad98e8, abb91f45, 1b5627de
Status: fertig
Ampel: 🟡 (von Code-Seite abgenommen)

Rollenteilung wie in Abschnitt 2 und 3: E2E, Screenshots, Design und UX liegen beim Design-Reviewer im eigenen Worktree. **Kein Playwright-Lauf von meiner Seite.** Wo eine Aussage nur im Browser messbar ist, steht das ausdrücklich dabei und geht als Auftrag an den Design-Reviewer.

### Gates

- `npm run lint`: **0 Fehler, genau 1 Warnung** (`BelegeKasseEditor.tsx:1204`, `react-hooks/exhaustive-deps`) — identisch zur Baseline.
- `npm run test`: **88/88 Dateien, 1082/1082 Tests grün**, Exit 0, im ersten Lauf, keine Worker-Timeouts.
- `npm run build`: grün (`tsc -b` + `vite build`, Exit 0). Build-Output danach verworfen (`git checkout --` auf `src/main/resources/static/index.html`, die zwei neuen `assets/`-Dateien gelöscht), `git status` sauber.
- Kein `./mvnw` (Backend unberührt). `git diff --stat fac91e90..HEAD`: **10 Dateien, alle unter `react-pc-frontend/`** (5 × `src/pages/*.tsx`, 3 geänderte + 2 neue Specs). **Kein Build-Output in den Commits** (per `git show --stat` je Commit geprüft), keine Merge-Konflikt-Reste, kein `test.only`/`test.skip`/`test.fixme`, kein `toHaveScreenshot`, kein eigenes `setViewportSize` — beide Größen laufen also wirklich über die Playwright-Projekte.
- DSGVO: nur Fantasienamen. Task 7 baut den Mitarbeiter bewusst aus den etablierten Wortstämmen („Bernhardine Beispielmusterfrauenbergwaldschmidtstein", Abteilung „Sonderaufgabenkoordinationsstellenverwaltung"), alle Adressfelder `null`, `stundenlohn: null`, `qualifikation: null` — kein Personenbezug, kein echter Lohn. Task 6 nur `.example`-TLD. Screenshots landen unter `test-results/` (gitignored).

### Mutationsproben

**Unit-Ebene (selbst gefahren).** Alle fünf Produktivdateien per `git apply -R` auf den Stand vor Abschnitt 4 zurückgedreht (Kennzahlen wieder `flex-1 max-w-4xl`, Reiterleisten wieder `overflow-x-auto`/`px-4`, Kartentitel wieder `truncate`/`min-h-[3rem]`, die sechs `truncate`-Stellen wieder eingesetzt, `ml-auto`/`min-w-0`/`shrink-0` wieder entfernt, Raster wieder `xl:grid-cols-4`, „Tagebuch" wieder „Bau Tagebuch") und die betroffenen Unit-Dateien dagegen gefahren: **24/24 grün** (Projekt 6, Anfrage 4, Kunde 5, Lieferant 9). Keine einzige Unit-Zusicherung greift — unverändert zum Befund aus Abschnitt 2 und 3. Mutation restlos zurückgenommen, `git diff` und `git status` danach leer.

**Playwright-Ebene (aus dem Spec-Code abgeleitet, vom Design-Reviewer zu bestätigen).**

| Mutation | Was sie fängt |
| --- | --- |
| Kennzahlen zurück auf `flex-1 max-w-4xl` (Lieferant) | `lieferant-layout.spec.ts` doppelt: die vier direkten Kasten-Breiten (`>= 100px`, vorher 94,8 px) **und** `keinTextLaeuftUeber` in `designPruefung`. Schärfste Bremse des Abschnitts. |
| Reiterleiste zurück auf `overflow-x-auto` | Jetzt in **allen fünf** Specs die direkte Stil-Zusicherung `overflowX === 'visible'`. Genau die Lücke aus Hinweis 2 meines Abschnitt-3-Reviews ist damit zu. |
| `data-testid` entfernen (Mitarbeiter-Reiterleiste, Lieferant-Kennzahlen) | `toHaveCount(4)` bzw. der Kennzahlen-Scope reißt sofort, fixture-unabhängig. |
| Kartentitel zurück auf `truncate` (Lieferant) | `keinTextGekuerzt` in der Übersichts-Spec plus `keinTextLaeuftUeber` in `designPruefung`. Greift in beiden Größen. |
| `2xl:grid-cols-4` zurück auf `xl:grid-cols-4` (Lieferant) | Die ausdrückliche Spaltenzahl je Größe (3 bei 1440, 4 bei 1920). Präzise und beidseitig. |
| `gap-1`/`px-2` zurück auf `gap-2`/`px-3` (Projekt-Reiter) | Neue y-Abweichungs-Zusicherung (2 px Toleranz über alle sieben Reiter) — vorher 593/593/593/593/593/593/639. |
| `min-h-[3rem]` wieder einsetzen (Anfrage, Kunde) | Die zwei neuen Kurztitel-Testfälle (Titel-Boxhöhe unter 32 px, mit `min-h` 48 px). Beim **Projekt** fängt das niemand — dort fehlt der Testfall. |
| Die sechs `break-words`-Stellen zurück auf `truncate` (Projekt) | `keinTextGekuerzt(page)` auf dem Reiter „Geschäftsdokumente": Betreff, Lieferantenname und Dateiname stehen dort gleichzeitig im DOM (alle drei im Block `activeTab === 'geschaeftsdokumente'`, Z. 1676–2943), die Kunden-E-Mail steht ohnehin dauerhaft in der rechten Spalte. Drei der vier Stellen sind mit den Fixture-Werten wirklich rot, der Lieferantenname nicht (siehe Bedenken 1). |
| `ml-auto` entfernen | Nur beim **Projekt** rot (x=89 statt x=961). Bei Anfrage, Kunde und Lieferant läuft die Zusicherung mit, ohne etwas festzuhalten. |
| `mt-auto` entfernen (alle fünf Karten von Task 3b) | **Nichts** — und zwar nicht wegen der Fixture, sondern weil `mt-auto` dort ohnehin wirkungslos ist. Siehe Hinweis 1. |
| Reiter „Tagebuch" zurück auf „Bau Tagebuch" (Anfrage) | Der Locator `/^Tagebuch/` in `anfrage-layout.spec.ts` findet den Knopf nicht mehr. |

### Die fünf gemeldeten Bedenken

**1. Vierte `truncate`-Stelle im Projekt (Lieferantenname, Z. 2212) — Fix ist richtig, nicht unnötig. 🟡 nur an der Formulierung.** Die Zeile steht in einer Flex-Reihe zusammen mit der Plakette „EINGANGSRECHNUNG" und der Belegnummer; `truncate` **ohne `title`** war dort ein echter blinder Fleck, sobald ein Lieferantenname länger wird als die mandatierte Spec-Konstante (35 Zeichen in einer 779 px breiten Zeile). `break-words` kostet an dieser Stelle nichts und macht die Zusicherung nicht überflüssig, sondern gegenstandslos — nach dem Fix gibt es dort keine Kürzung mehr, die jemand prüfen müsste. Der Agent hat die Einschränkung im Spec-Kommentar offen hingeschrieben statt sie zu verschweigen; das ist der richtige Umgang. Kein Nachbessern nötig, nur der Spec-Kommentar sollte „drei von vier Stellen wirklich rot" sagen statt „alle vier".

**2. `ml-auto` bei Anfrage und Kunde läuft nur mit — bestätigt, trotzdem stehen lassen. 🟡.** Anders als in Abschnitt 3 gibt es jetzt einen echten Rot-Beleg (Projekt: x=89 auf x=961, Kartenmitte 720). Bei Anfrage und Kunde bricht die Kopfzeile nicht um, weil dort **zwei** Kennzahlen stehen statt fünf — der Umbruch ließe sich nur erzwingen, indem man Kennzahlen erfindet, die es in der Anwendung nicht gibt. Eine solche Fixture wäre eine Attrappe, kein Test. Meine Empfehlung ist deshalb ausdrücklich **nicht**, hier nachzuschärfen: die Zusicherung ist billig, sie wird von selbst scharf, sobald eine dritte Kennzahl oder ein zweiter Knopf dazukommt, und sie dokumentiert die Absicht im Testcode. Das unterscheidet sie vom Fall aus Abschnitt 3, wo die *Härtung selbst* (`break-words`) ungesichert war — die ist mit dem neuen Komposita-Testfall jetzt belegt.

**3. Kurztitel-Test für das Projekt fehlt — die Empfehlung an Task 9 reicht, muss aber schärfer gefasst werden. 🟡.** Anfrage und Kunde haben je einen dauerhaften Testfall; beim Projekt fehlt er nur, weil `projekt-uebersicht-layout.spec.ts` nicht in Task 3bs Files-Liste stand. Task 9 schreibt ohnehin eine neue Spec über alle vier Übersichten und fasst keine `src/`-Datei an — dort passt der Testfall exakt hin. **Aber:** so wie die zwei vorhandenen Testfälle gebaut sind (Titel-Boxhöhe unter 32 px), messen sie nur die *Abwesenheit* von `min-h-[3rem]`, nicht die neue Mechanik. Und die neue Mechanik funktioniert heute gar nicht (Hinweis 1). Task 9 sollte deshalb zusätzlich zwei Karten mit **unterschiedlich langen** Titeln in einer Reihe öffnen und vergleichen, ob der Meta-Block (die `border-t`-Zeile) auf derselben y-Position sitzt. Das ist die Zusicherung, die die Rezeptur wirklich festhält.

**4. `gap-4` beim Lieferanten — richtig entschieden, kein Angleich nötig. 🟡 nur an der Plan-Formulierung.** Der Task-6-Block ist spezifischer als die allgemeine Rezeptur, und der Plan verbietet unter „Bewusst nicht in diesem Vorhaben" ausdrücklich, die zwei Kennzahlen-Bauweisen zu vereinheitlichen. Ergebnis im Code: Projekt und Anfrage tragen `gap-x-6 gap-y-2` (Trennerspalten), Kunde und Lieferant `gap-4` (Kacheln) — **innerhalb** jeder Bauweise ist es jetzt einheitlich. Der Rezeptur-Text im Plan sollte das nachziehen („`gap-x-6 gap-y-2` für die Trennerspalten-Bauweise, `gap-4` für die Kachel-Bauweise"), sonst liest der nächste Agent eine Abweichung, wo keine ist.
Zum zweiten Teil: dass `ml-auto` beim Lieferanten heute wirkungslos ist, deckt sich mit den Messungen (Knopf-x vorher und nachher identisch, 1218,7 bzw. 1570,7). Vier Kennzahlen und ein Knopf brechen bei 1440 nicht um. Kein Mangel — die Klasse kostet nichts und ist genau die Absicherung für den fünften Kennzahl-Kasten, für den die Bauweise jetzt vorbereitet ist.

**5. Task 7 mit `overflowX === 'visible'` als einziger echter Bremse — die Spec erfüllt ihren Zweck.** Sie prüft genau die Eigenschaft, die der Fix herstellt, war fixture-unabhängig rot (zusammen mit dem fehlenden `data-testid`) und fängt jeden Rückfall auf `overflow-x-auto` — unabhängig davon, ob die Reiter zufällig knapp passen. Das ist **besser** als eine Pixelmessung, die nur zufällig reißt; genau das war Hinweis 2 meines Abschnitt-3-Reviews. Dass sich bei vier statischen Reiter-Beschriftungen ohne Zähler kein datenabhängiger Überlauf erzwingen lässt, ist eine Eigenschaft der Seite, kein Versagen der Fixture-Suche. Dazu kommt eine volle `designPruefung(strengePruefungen)` mit einer bewusst pathologischen 41-Zeichen-Komposita-Fixture über die ganze Seite inklusive Seitenpanel und ein `main`-Überstandstest nach jedem der vier Reiter-Klicks. Kein Nachbessern nötig.

### 🛑 Kritisch (blockiert)

Keine. Keine Korrektheitsfehler, alle drei Gates grün, keine Datei außerhalb `react-pc-frontend/`, kein Build-Output in den Commits, keine Merge-Reste, keine echten Personendaten.

### 💡 Hinweise (blockieren nicht)

**1. `mt-auto` ist in fünf von sechs Karten wirkungslos — die neue Rezeptur greift heute nur beim Lieferanten. Wichtigster Befund des Abschnitts.**
Tailwind 3.4.17 erzeugt für `space-y-3` den Selektor `.space-y-3>:not([hidden])~:not([hidden]){margin-top:.75rem}` (im gebauten CSS nachgesehen). Dessen Spezifität ist 0-3-0, die von `.mt-auto{margin-top:auto}` ist 0-1-0 — `space-y` gewinnt, unabhängig von der Reihenfolge. In `ProjektCard`, `AnfrageCard`, `KundenProjektKarte`, `KundenAnfrageKarte` und `KundenKarte` sitzt `mt-auto` genau auf dem letzten Kind eines `space-y-3`-Containers und wird überschrieben. Nur `LieferantCard` (Task 6) hat den Container von `space-y-3` auf `gap-3` umgestellt — dort funktioniert es.
Folge: Gleich hohe Karten kommen weiterhin zustande (`h-full` plus Grid-Stretch; alle fünf Kartenraster sind echte Grids, geprüft), aber der Meta-Block wird **nicht** nach unten geschoben. Die Trennlinie sitzt je nach Titellänge unterschiedlich hoch. Der gemeldete 24-px-Fehler ist trotzdem behoben, weil `min-h-[3rem]` raus ist — die Ersatzmechanik ist es, die nicht greift.
Fix, ein Wort je Karte: `space-y-3` auf `gap-3` (genau wie `LieferantCard` es macht). Betrifft `ProjektEditor.tsx:4153`, `AnfrageEditor.tsx:179`, `Kundeneditor.tsx:121`, `Kundeneditor.tsx:165`, `Kundeneditor.tsx:848`. Zur Kontrolle im Browser: zwei Karten einer Reihe mit unterschiedlich langem Titel, y-Position der `border-t`-Zeile vergleichen — heute unterschiedlich, nach dem Fix gleich.

**2. Die Lieferant-Spec lädt ein echtes Google-Maps-`iframe`.**
`DUMMY_LIEFERANT` in `e2e/lieferant-layout.spec.ts` hat `strasse`/`plz`/`ort` gefüllt, und `LieferantenEditor.tsx:361` rendert damit `GoogleMapsEmbed`, also ein `iframe` auf `https://www.google.com/maps?q=…&output=embed`. `page.route` fängt nur `**/api/**`, der Aufruf geht also wirklich raus. Die drei Specs aus Abschnitt 3 lassen die Adressfelder genau deshalb bewusst leer (Kommentar in `projekt-detail-layout.spec.ts:100`); Task 6 hat das übernommene Vorbild an dieser Stelle nicht mitgenommen. Folgen: externer Netzzugriff aus dem Test, nicht deterministische Screenshots, Fehlschlag ohne Internet — und ein plausibler Kandidat für die vom Task-6-Agenten beobachtete „komplett leere weiße Seite" bei vier Workern, die er der Maschinenlast zugeschrieben hat. **Kein DSGVO-Verstoß** (Fantasiefirma, keine Personendaten), aber gegen den in Abschnitt 3 etablierten Standard „kein Backend, kein externer Zugriff". Fix: `strasse`/`plz`/`ort` auf `null` wie in den anderen Fixtures, oder die Google-Route im Test abbrechen.

**3. Dieselbe E-Mail-Kürzung wurde in Projekt und Lieferant behoben, in der Anfrage nicht.**
`AnfrageEditor.tsx:1454` — Kunden-E-Mails im `sideContent`, `truncate` ohne `title`, wortgleich mit `ProjektEditor.tsx:3332` (in Task 3b behoben) und `LieferantenEditor.tsx:309` (in Task 6 behoben). `AnfrageEditor.tsx` stand in Task 3bs Files-Liste, der Fix wäre also erlaubt gewesen. Unentdeckt geblieben, weil die Anfrage-Fixture `kundenEmails: []` setzt (aus einem anderen Grund: um `GoogleMapsEmbed` stillzulegen). Zweite, schwächere Stelle derselben Art: `Kundeneditor.tsx:877`, E-Mail in der Übersichtskarte mit `truncate` ohne `title` — die Fixture hat dort keine E-Mail. Beide tragen **keinen** Marker, `keinTextGekuerzt` würde sie also melden; es schaut nur niemand hin. Vorschlag: die Anfrage-Stelle als Einzeiler nachziehen (`break-words`, wie die zwei Schwestern), die Kunden-Übersichtskarte in Task 9 mit einer langen E-Mail in der Fixture abdecken.

**4. Knopfblock beim Kunden weicht von der Rezeptur ab.** `Kundeneditor.tsx:319` hat `flex items-start shrink-0 ml-auto`, die Rezeptur verlangt `shrink-0 ml-auto flex flex-wrap items-start gap-2`. Heute folgenlos, weil dort nur ein Knopf steht — ein zweiter würde aber nicht umbrechen und ohne `gap-2` kleben. Projekt, Anfrage und Lieferant tragen die volle Fassung. Einzeiler.

**5. Zwei Bauweisen für dieselbe Karte.** `LieferantCard`: `p-4 flex flex-col h-full gap-3` (Z. 800). Die anderen vier: `p-4 space-y-3 flex-1 flex flex-col`. Nach Hinweis 1 ist die Lieferant-Variante die richtige; beim Angleichen fällt der Unterschied von selbst weg. (`h-full` auf dem inneren `div` gegen `flex-1` ist Geschmackssache und funktioniert beides.)

**6. „Bau Tagebuch" lebt als Überschrift weiter.** Der Reiter heißt jetzt in beiden Editoren „Tagebuch", die Überschrift im Reiter-Inhalt sagt weiter „Bau Tagebuch" (`ProjektEditor.tsx:1285`, `AnfrageEditor.tsx:1221`). Plan-konform und in beiden Dateien gleich, aber der Nutzer sieht damit weiter zwei Namen für dieselbe Sache — nur eine Ebene tiefer als vorher. Entscheidung für Orchestrator und Design-Reviewer.

**7. Die Mitarbeiter-Reiterleiste hat keine ARIA-Rollen.** Kein `role="tablist"`, kein `role="tab"`, kein `aria-selected` — vorbestehend, und der Plan sagt für Task 7 ausdrücklich „sonst nichts an dieser Datei". Lieferant und `SystemSetupConfigurator` haben die Rollen, Projekt, Anfrage, Kunde und Mitarbeiter nicht. Gehört als Notiz in Task 10, nicht in diesen Abschnitt.

**8. Die Absicherung hängt weiterhin vollständig an Playwright.** Mit allen fünf zurückgedrehten Dateien bleiben 24/24 der betroffenen Unit-Tests und 1082/1082 der vollen Suite grün. Das ist in diesem Vorhaben Absicht, heißt aber: der volle E2E-Lauf des Design-Reviewers ist das eigentliche Gate.

### Rezeptur-Treue der fünf Detailseiten

Zeile für Zeile an Kopfzeile, Reiterleiste und Kartentitel verglichen.

| Punkt | Projekt | Anfrage | Kunde | Lieferant | Mitarbeiter |
| --- | --- | --- | --- | --- | --- |
| äußeres `flex flex-wrap items-start gap-4` | ja | ja | ja | ja | entfällt (kein Card-Kopf, keine Kennzahlen-Reihe) |
| Titelblock `flex-1 min-w-[18rem]` | ja | ja | ja | ja | entfällt |
| innerer Textblock `min-w-0` | ja | ja | ja | ja | entfällt |
| `<h1>` `break-words` | ja | ja | ja | ja | entfällt |
| Untertitel-Icons `shrink-0` | ja | ja | ja | ja | entfällt |
| Kennzahlen ohne `flex-1`/`max-w`, je `min-w-[7rem]` | ja | ja | ja | ja | entfällt |
| Knopfblock `shrink-0 ml-auto flex flex-wrap items-start gap-2` | ja | ja | **`flex-wrap`/`gap-2` fehlen** | ja | entfällt |
| Reiterleiste `flex-wrap min-w-0`, kein `overflow-x-auto` | ja | ja | ja | ja | ja |
| Reiter-Knöpfe `px-3` | `px-2` (Plan-Vorgabe, für die eine Zeile) | ja | ja | ja | ja |
| `overflowX === 'visible'` in der Spec | ja | ja | ja | ja | ja |
| Kartentitel `line-clamp-2` + `title` + Marker, kein `min-h-[3rem]` | ja | ja | ja | ja | entfällt (kein `truncate`/`line-clamp` in der Datei) |
| Karte `h-full flex flex-col` + `mt-auto` am Meta-Block | `mt-auto` wirkungslos | wirkungslos | wirkungslos (3 ×) | ja | entfällt |
| Raster `2xl:grid-cols-4` | ja | ja | ja | ja | entfällt |

Zwei verbleibende Abweichungen, beide 🟡 und beide Einzeiler: der Knopfblock beim Kunden (Hinweis 4) und `space-y-3` statt `gap-3` in den fünf Karten von Task 3b (Hinweis 1). Alles andere sitzt jetzt in allen fünf Dateien gleich — das war das Ziel dieses Abschnitts und ist bis auf diese zwei Stellen erreicht. Die zwei Kennzahlen-Bauweisen (Trennerspalten gegen Kacheln) und die zwei Reiterleisten-Optiken bleiben unterschiedlich, wie der Plan das unter „Bewusst nicht in diesem Vorhaben" festhält.

### `data-kuerzung-erlaubt`-Inventar (Grundlage für Task 10)

Acht Vorkommen in vier Dateien im gesamten `react-pc-frontend/src/` — unverändert acht wie nach Abschnitt 3, aber die Zusammensetzung hat sich verschoben: die Dokumentnummer ist raus (Task 3b), der Lieferanten-Kartentitel ist dazugekommen (Task 6). Jedes trägt ein `title` mit dem vollen Text.

| # | Stelle | Bewertung |
| --- | --- | --- |
| 1 | `RibbonNav.tsx:322` — Anzeigename in der Menüleiste (`max-w-[10rem] line-clamp-1`) | **Gewollt.** Voller Name steht im Nutzermenü darunter, `title` vorhanden, Begründung steht im Code. Task 8b zieht `line-clamp-1` wieder auf `truncate` und ergänzt `2xl:max-w-none`. |
| 2 | `ProjektEditor.tsx:4185` — `ProjektCard`-Titel (Bauvorhaben) | **Gewollt.** Kartentitel, `line-clamp-2`, `title` vorhanden. Sanktionierte Kategorie. |
| 3 | `AnfrageEditor.tsx:217` — `AnfrageCard`-Titel | **Gewollt**, identisch zu 2. |
| 4 | `Kundeneditor.tsx:866` — `KundenKarte`-Titel (Kundenname) | **Gewollt**, identisch zu 2. |
| 5 | `Kundeneditor.tsx:140` — `KundenProjektKarte`-Titel (Mini-Karte) | **Gewollt**, identisch zu 2. |
| 6 | `Kundeneditor.tsx:173` — `KundenAnfrageKarte`-Titel (Mini-Karte) | **Gewollt**, identisch zu 2. |
| 7 | `Kundeneditor.tsx:226` — Herkunftszeile „Projekt: Bauvorhaben" der Dokument-Mini-Karte | **Gewollt, mit Vorbehalt.** Trägt denselben langen Bauvorhaben-Namen wie die Kartentitel daneben und ist faktisch die Titelzeile der Mini-Karte; `title` vorhanden, Plan nennt sie ausdrücklich. Der Vorbehalt aus Abschnitt 3 bleibt: der Marker schaltet `keinTextGekuerzt` für den ganzen Teilbaum ab — hier ist das nur ein `<p>` ohne Kinder, also unkritisch. |
| 8 | `LieferantenEditor.tsx:824` — `LieferantCard`-Titel (Lieferantenname) | **Gewollt**, identisch zu 2. |

**Kein blinder Fleck darunter.** Alle acht sind Überschriften oder Anzeigenamen mit vollem `title`, keiner steht auf einer Kennung, einem Betrag oder einer Adresse. Die eine Stelle, die aus der Reihe fiel — die Belegnummer in `KundenDokumentKarte` — hat Task 3b entfernt (`break-words` ohne Marker, `title` bleibt), genau wie in Abschnitt 3 empfohlen.
**Nachzug für Task 10 (unverändert nötig):** die Global Constraints sprechen weiter von „genau zwei Fällen". Tatsächlich sind es acht in vier Dateien. Der Satz muss auf den Stand gezogen werden, sonst markieren künftige Tasks mit demselben Recht weiter und niemand weiß mehr, was sanktioniert ist. Der Marker selbst ist dabei nicht das Problem — dass er stillschweigend für den ganzen **Teilbaum** gilt, ist es: ein Marker auf einem Container schaltet jede Kürzung darunter ab. Task 10 sollte deshalb festschreiben, dass er nur auf dem kürzenden Element selbst stehen darf, nie auf einem Container.

### Spec-Qualität der zwei neuen Specs

- **Vollständig gestubbt:** beide über Catch-all `**/api/**` plus gezielte Overrides, Vorbild `stubbeLieferantApi`. Kein Backend. **Einzige Lücke:** das echte Google-Maps-`iframe` in der Lieferant-Detailspec (Hinweis 2) — das ist keine `/api`-Route und rutscht deshalb durch.
- **Nur Fantasienamen:** ja, in beiden. Bei Task 7 besonders sorgfältig (erfundener Nachname aus den Wortstämmen „Beispiel"/„Muster", alle Adress-, Lohn- und Qualifikationsfelder `null`) — für eine Mitarbeiterseite genau das richtige Vorgehen.
- **Beide Größen:** ja, in beiden. Kein eigenes `setViewportSize`, die Größe kommt aus den Playwright-Projekten; die Lieferant-Übersichtsspec verzweigt sogar bewusst über `testInfo.project.name` für die erwartete Spaltenzahl.
- Kein `toHaveScreenshot`, kein `test.only`/`skip`. `designPruefung(..., { strengePruefungen: true, primaerAktion })` je Zustand. Beide Specs erklären im Kopfkommentar, welche Zusicherung wirklich rot war und welche nur mitläuft — genau die Ehrlichkeit, die in Abschnitt 3 gefehlt hat.

### Auftrag an den Design-Reviewer

1. **Hinweis 1 im Browser bestätigen (wichtigster Punkt).** `/projekte` mit zwei Karten in einer Reihe, eine mit kurzem und eine mit langem Bauvorhaben. `getComputedStyle` auf dem `border-t`-Meta-Block: erwarteter Wert `margin-top: 12px` statt `auto`, und die y-Positionen der zwei Trennlinien unterscheiden sich. Gegenprobe: dasselbe auf `/lieferanten` — dort sollte `mt-auto` greifen und die Trennlinien auf gleicher Höhe liegen. Bestätigt sich das, ist der Fix `space-y-3` auf `gap-3` in den fünf Karten.
2. **Hinweis 2 bestätigen:** `npx playwright test e2e/lieferant-layout.spec.ts` mit mitlaufendem Netzwerk-Mitschnitt (`page.on('request')` oder das Trace). Erwartung: eine Anfrage an `www.google.com/maps`. Und ob das die „leere weiße Seite" bei vier Workern erklärt.
3. **Optik der neuen Kartenhöhen** auf allen vier Übersichten in beiden Größen — das ist der Befund, wegen dem `min-h-[3rem]` rausgeflogen ist, und der Ersatz greift heute nur beim Lieferanten.
4. **Projekt-Reiterleiste bei 1440 nachmessen:** der Bedarf liegt jetzt laut Task 3b bei 899 px gegen 916 px verfügbar — 17 px Luft, nachdem er vorher 62 px zu groß war. Bitte bestätigen, dass alle sieben Reiter wirklich einzeilig stehen und die Trennlinie sauber unter einer Zeile liegt, und ob 17 px Reserve als Rahmen reichen (jeder künftige zweistellige Zähler frisst davon).
5. **Kunden-Kopfzeile mit dem langen Namen:** ein Knopf ohne `flex-wrap`/`gap-2` (Hinweis 4) — sieht das bei 1440 unauffällig aus?
6. Voller E2E-Lauf über alle Specs in beiden Größen, wie gehabt.

### Aufräumen

Mutation vollständig zurückgenommen (fünf Patches per `git apply -R` gesetzt und per `git apply` wieder zurückgespielt), Build-Output verworfen. `git diff` leer, `git status` sauber (bis auf diesen Log-Block), HEAD unverändert `1b5627de`. Kein Produktivcode angefasst, kein Playwright gestartet.

## Abschnitt 4 — Design-Review (Design-Reviewer)

Zeit: 2026-09-06T13:05:00Z
Branch: feature/layout-14-zoll @ 1b5627de (Merge von Task 6, Task 7, Task 3b), eigener Worktree `wt/layout-review-design`, detached HEAD
Commit(s): eaa7ed2b + 7c572241 (Task 6), 35bca3be (Task 7), 5ed821f8 / 19deaf3b / ea1d1d2d (Task 3b), gemergt in 1b5627de
Status: fertig
Ampel: 🔴 (ein blockierender Befund: der Titel der Kopfzeile läuft bei einem Komposita-Bauvorhaben über die Kennzahlen — belegt, mit gemessener Ein-Klassen-Lösung)

### E2E — komplett, beide Größen

- Port-Check vorher: `netstat -ano | findstr :5218` leer, Port 5218 wie vorgegeben verwendet.
- `E2E_PORT=5218 npm run test:e2e` (alle Specs, `pc-14zoll` + `pc-monitor`): **198 grün, 0 rot**,
  zweimal vollständig gefahren (4,0 min und 2,6 min), beide Male identisches Ergebnis, keine
  Flakes, kein Nachfahren einzelner Specs nötig, Standard-Worker ohne `--workers=1`.
  Rechnung zur Zwischenbilanz nach Abschnitt 3: 186 + 4 (Task 6, zwei Testfälle × zwei Größen)
  + 2 (Task 7) + 6 (Task 3b: Anfrage-Komposita, Anfrage-kurzer-Titel, Kunde-kurzer-Name) = 198.
- Die `[WebServer] http proxy error ECONNREFUSED`-Zeilen sind wie in den Vorabschnitten der
  Vite-Proxy ohne Backend, kein Testfehler.
- **Warnung für Nachfolger:** Ein Playwright-Lauf ohne `--output` löscht `test-results/` komplett,
  also auch alle Design-Screenshots. Wegwerf-Messspecs deshalb immer mit
  `--output=test-results-zz` fahren (mir ist es einmal passiert, danach die volle Suite neu gefahren).

### Angeschaute Screenshots

Alle unter
`C:\Users\MarvinKuhn\dev\ERP-für-Handwerker\wt\layout-review-design\react-pc-frontend\test-results\design\`,
jeder mit dem Read-Tool geöffnet.

**Neu aus Abschnitt 4 (16):**
`lieferant-detail-langer-name--pc-14zoll.png` / `--pc-monitor.png`,
`lieferant-uebersicht-lange-namen--pc-14zoll.png` / `--pc-monitor.png`,
`mitarbeiter-detail-reiterleiste--pc-14zoll.png` / `--pc-monitor.png`,
`anfrage-detail-kopf-komposita--pc-14zoll.png` / `--pc-monitor.png`,
`anfragen-uebersicht-kurzer-titel--pc-14zoll.png` / `--pc-monitor.png`,
`kunde-mini-karten-projekte--pc-14zoll.png` / `--pc-monitor.png`,
`kunde-mini-karten-anfragen--pc-14zoll.png` / `--pc-monitor.png`,
`kunde-mini-karten-dokumente--pc-14zoll.png` / `--pc-monitor.png`

**Durch Abschnitt 4 verändert (14):**
`projekt-detail-geschaeftsdokumente--pc-14zoll.png` / `--pc-monitor.png`,
`rahmen-projekt-detail--pc-14zoll.png` / `--pc-monitor.png` (nicht mehr byte-identisch zu den
beiden davor — Task 3b hat die Fixtures der Projekt-Spec verlängert),
`projekt-uebersicht-lange-titel--pc-14zoll.png` / `--pc-monitor.png`,
`anfrage-detail-kopf--pc-14zoll.png` / `--pc-monitor.png`,
`anfragen-uebersicht-karten--pc-14zoll.png` / `--pc-monitor.png`,
`kunde-detail-langer-name--pc-14zoll.png` / `--pc-monitor.png`,
`kunde-uebersicht-lange-namen--pc-14zoll.png` / `--pc-monitor.png`

**Mittelbar verändert, weil Task 6 die Lieferanten-Detailseite umgebaut hat (22):**
`leiste-lesen`, `leiste-bearbeiten`, `leiste-countdown`, `leiste-deaktiviert`,
`leiste-verbindung-weg`, `lieferant-modal-lesen-hinweis`, `lieferant-modal-bearbeiten`,
`lieferant-modal-fehler`, `lieferant-modal-fehler-tooltip`, `lieferant-modal-fremdes-lock`,
`lieferant-modal-speicherfehler-toast`, `toast-bei-dialog-versionskonflikt`,
`toast-bei-dialog-zweizeilig` — beide Größen. Befund vorweg: In **allen** diesen Bildern liegt
der Vollbild-Dialog „Dokument bearbeiten" über der Seite, die umgebaute Lieferanten-Kopfzeile
steckt hinter dem Scrim. Die Task-6-Änderung ist dort also gar nicht sichtbar.

**Auf Veränderung geprüft, unverändert (1):** `menueleiste-kategorien--pc-14zoll.png` — die einzige
Alt-Spec, die eine geänderte Seite öffnet (`/projekte`), dort aber mit **leerer** Liste
(„Keine Projekte gefunden."), also ohne Kartenraster. Bestätigt nebenbei Spec-Befund 3:
„Finanzen & Controlling" steht vollständig da, der lange Nutzername „Friederike Beispiel-…"
ist der eine gewollte Kürzungsfall.

**Nicht erneut angeschaut (mit Begründung):** `dokument-editor-*`, `editor-seite-*`,
`strenge-pruefungen-*` und die übrigen `menueleiste-*`. Diese Specs gehen ausschließlich nach
`/dokument-editor` bzw. auf `setContent`-Fixtures (nachgeprüft per `grep` über alle
`goto(...)`-Aufrufe) und berühren keine der fünf in Abschnitt 4 geänderten Dateien
(`git diff --stat 7aabfe16..1b5627de`). In Abschnitt 2 vollständig beurteilt.

**Eigene Wegwerf-Bilder (nach der Auswertung samt Messspecs gelöscht):**
`zz-komposita-fix--pc-14zoll/-monitor.png` (Probe des Ein-Klassen-Vorschlags für den 🔴),
`zz-nachtrag-projekte--*` und `zz-nachtrag-lieferanten--*` (Karten-Gegenprobe für den
Code-Reviewer), `zz-probe-projekt-komposita--*` (Beleg, dass der 🔴 nicht anfragespezifisch ist).

**Beweisbild der Ausgangsprüfung:**
`docs/superpowers/specs/bilder/2026-09-04-layout-14-zoll/lieferant-detail-1440.png` — angeschaut.

### Die sechs Fragen — je Screenshot und Größe

**A) Lieferanten-Detailseite mit langem Namen (Kern von Task 6)** —
`lieferant-detail-langer-name--pc-14zoll.png` / `--pc-monitor.png`

1. *Farben:* Vier Kennzahl-Kacheln in slate / purple / blue / emerald, darunter die
   Dokument-Kacheln in slate / blue / purple / rose. Zustände trennen sich, Kontraste tragen.
   Genau **eine** gefüllte rose-Aktion im Inhalt („Dokument importieren"); „Bearbeiten" ist
   Outline. Die purple- und blue-Kacheln sind eine Abweichung vom rose/slate-Schema —
   **vorbestehend**, vom Plan ausdrücklich ausgeklammert („Bewusst nicht in diesem Vorhaben"),
   Task 6 hat keine Farbe angefasst. 🟡.
2. *Design-System:* Lucide-Icons, `rounded-lg`/`rounded-xl`, `shadow-sm`, Systemschrift, kein
   Emoji, keine handgemalte SVG, kein Webfont. PageHeader-Muster (Eyebrow „STAMMDATEN" +
   Großtitel „LIEFERANTENDETAILS" + Untertitel + Aktion rechts) eingehalten.
3. *Look-and-Feel:* In beiden Größen ruhig. Bei 1440 sitzt der Rollen-Chip „STAHL" unter dem
   Titel, bei 1920 daneben — sauberer Umbruch, kein Unfall. Kopfzeile bleibt in beiden Größen
   **einzeilig**.
4. *UX:* Eine Primäraktion, Reiter mit Zählern, Kontaktdaten-Spalte rechts, Leerzustand als
   erklärender Kasten („Keine Dokumente vorhanden. Dokumente werden automatisch aus
   E-Mail-Anhängen erstellt") statt leerem Loch.
5. *Auffindbarkeit:* „Bearbeiten" ohne Scrollen rechts oben, in **beiden** Größen an derselben
   Stelle. Alle vier Reiter einzeilig sichtbar, der rose Unterstrich des aktiven Reiters sitzt
   sauber auf der Trennlinie.
6. *Überschneidungen:* **Spec-Befund 2 (Lieferant) ist weg** — siehe eigener Abschnitt unten.
   Kein horizontaler Scroll, keine Überlappung. Die E-Mail im Kontaktfeld bricht bei 1440 um
   („bestellung@beispiel-" / „stahl.example") statt überzulaufen, bei 1920 passt sie einzeilig.

**B) Lieferanten-Übersicht mit vier langen Namen** —
`lieferant-uebersicht-lange-namen--pc-14zoll.png` / `--pc-monitor.png`

1./2. rose/slate, „STAHL"-Chip rose, PageHeader-Muster, genau eine gefüllte Aktion
   („Neuer Lieferant"). Sauber.
3. *Look-and-Feel:* **Die beste der vier Übersichten.** Drei Karten bei 1440, vier bei 1920, und —
   anders als bei Projekt/Anfrage/Kunde — liegen **alle Trennlinien einer Reihe exakt auf einer
   Höhe**, obwohl Karte 1 einen einzeiligen und Karte 2/3 zweizeilige Titel haben. Nachgemessen
   (siehe „Auftrag des Code-Reviewers", Punkt 1): Trennlinie in allen drei Karten bei y=642.
4. *UX:* Karten klickbar, Filterzeile darüber, Hinweis auf die 12er-Ladegrenze.
5. *Auffindbarkeit:* Alles ohne Scrollen, Aktion oben rechts.
6. *Überschneidungen:* Keine. **Alle vier langen Lieferantennamen stehen in beiden Größen
   vollständig da, kein einziges „…".**

**C) Mitarbeiter-Detailseite, Reiterleiste (Task 7)** —
`mitarbeiter-detail-reiterleiste--pc-14zoll.png` / `--pc-monitor.png`

1. *Farben:* Aktiver Reiter rose-50 mit rose-Unterstrich, inaktive slate — trennbar. Der Knopf
   „Bearbeiten" ist hier als **gefüllte** rose-600-Primäraktion gebaut, auf den anderen vier
   Detailseiten ist derselbe Knopf Outline. 🟡, siehe Einheitlichkeit.
2. *Design-System:* Lucide, kein Emoji, Systemschrift, `rounded-lg`. Eingehalten.
3. *Look-and-Feel:* Bei 1920 aufgeräumt. Bei 1440 wirkt der Knopf „Token erstellen" gedrängt,
   weil seine Beschriftung zweizeilig umbricht — kein Fehler, aber unruhig. 🟡.
4. *UX:* Drei Kopf-Aktionen, Reiter klar, Leerzustand erklärt sich.
5. *Auffindbarkeit:* Alle drei Knöpfe und alle vier Reiter ohne Scrollen sichtbar, rechts oben.
6. *Überschneidungen:* **Reiterleiste in beiden Größen einzeilig, `overflow-x` jetzt `visible`
   statt `auto`** — das versteckte Scrollen ist weg, Ziel von Task 7 erreicht.
   **Ein Befund im rechten Seitenpanel, nachgemessen:** Bei 1440 ragen das Feld „Abteilung(en)"
   und sein Wert „Sonderaufgabenkoordinationsstellenverwaltung" **21 px über die rechte
   Kartenkante hinaus** (Element endet bei x=1397, Karte bei x=1376). Bei 1920 nicht.
   **Vorbestehend und außerhalb von Abschnitt 4:** Task 7 hat laut Plan-Wortlaut nur die
   Reiterleiste angefasst, das Seitenpanel ist unverändert; sichtbar wird es erst durch die neue
   43-Zeichen-Fantasie-Abteilung. Gleiche Ursache wie der 🔴 unten (unteilbares langes Wort in
   einem Kasten ohne `min-w-0`). Deshalb 🟡, nicht 🔴 — gehört in denselben Nacharbeits-Task.
   `keinTextLaeuftUeber` sieht es nicht, weil das Element selbst nicht überläuft
   (scrollWidth = clientWidth = 306), sondern seinen Kasten sprengt.

**D) Anfrage-Detailseite, Komposita-Bauvorhaben (neu aus Task 3b)** —
`anfrage-detail-kopf-komposita--pc-14zoll.png` / `--pc-monitor.png`

6. *Überschneidungen:* **🔴 — hier liegt der blockierende Befund.** Siehe eigener Abschnitt unten.
1.–5. Farben, Design-System, UX und Auffindbarkeit sind unverändert in Ordnung (Reiterleiste
   einzeilig, „Bearbeiten"/„Löschen" rechts oben, Leerzustand erklärend); die Kopfzeile selbst ist
   durch den überlaufenden Titel aber in **beiden** Größen unleserlich.

**E) Anfrage-Detailseite, normaler Name** —
`anfrage-detail-kopf--pc-14zoll.png` / `--pc-monitor.png`

1.–5. Weiterhin die sauberste der fünf Kopfzeilen: Titel zwei- bzw. einzeilig, zwei Kennzahlen,
   Knöpfe rechts oben, in beiden Größen an derselben Stelle. Reiter jetzt „Tagebuch (0)" statt
   „Bau Tagebuch" — die Umbenennung ist durchgezogen und passt zum Projekt-Editor.
6. Kein horizontaler Scroll, keine Überlappung, kein „…".

**F) Projekt-Detailseite (Nacharbeit Task 3b)** —
`projekt-detail-geschaeftsdokumente--pc-14zoll.png` / `--pc-monitor.png`,
`rahmen-projekt-detail--pc-14zoll.png` / `--pc-monitor.png`

1. *Farben:* Kennzahlen slate, „Gewinn" grün, Status-Chip gelb, Dokumenttyp-Chips rose/gelb,
   „Dokument erstellen" als einzige gefüllte rose-Aktion. Trennbar.
2. *Design-System:* eingehalten, keine neue Farbe im Diff dieses Abschnitts.
3. *Look-and-Feel:* Bei 1440 deutlich ruhiger als in Abschnitt 3 — die Reiterleiste ist jetzt
   einzeilig, die Karte dadurch flacher, eine Zeile Inhalt mehr ohne Scrollen sichtbar.
   Bei 1920 unverändert luftig (fünfzeiliger Titel in schmaler Spalte, rechts viel Weißraum) —
   in Abschnitt 3 per Alt-Simulation als **vorbestehend** nachgewiesen. 🟡.
4. *UX:* Eine Primäraktion, Reiter mit Zählern, Hinweistext passt zum Reiternamen.
5. *Auffindbarkeit:* Alle sieben Reiter, beide Kopf-Knöpfe, die vollständige Karte „Projektdaten"
   und alle fünf Menü-Kategorien ohne Scrollen sichtbar.
6. *Überschneidungen:* Kein horizontaler Scroll, `main`-Überstand 0 px. **Die vier
   `truncate`-Stellen brechen jetzt um:** der Rechnungs-Beschreibungstext läuft bei 1440 über zwei
   Zeilen ohne „…", der lange Dateiname der Eingangsrechnung und die Kunden-E-Mails in der rechten
   Spalte brechen mitten im Wort um (`break-words`) statt gekürzt zu werden. Lesbar, kein „…".

**G) Projekt-Übersicht mit vier langen Bauvorhaben** —
`projekt-uebersicht-lange-titel--pc-14zoll.png` / `--pc-monitor.png`

1.–5. Wie in Abschnitt 3 beurteilt, unverändert gut: drei Karten bei 1440, vier bei 1920,
   PageHeader-Muster, eine gefüllte Aktion.
6. Kein horizontaler Scroll, keine Überlappung. Titel zweizeilig, „…" nur als markierter
   `line-clamp-2`-Fall (1 von 4 bei 1440, 3 von 4 bei 1920).
   **Die 24-px-Lücke unter kurzen Titeln ist weg** (siehe H) — dafür liegen die Trennlinien einer
   Reihe jetzt nicht exakt auf einer Höhe, wenn die Titel unterschiedlich hoch sind. 🟡, siehe
   „Auftrag des Code-Reviewers", Punkt 1.

**H) Kartentitel mit KURZEN Namen (mein Befund 3 aus Abschnitt 3)** —
`anfragen-uebersicht-kurzer-titel--pc-14zoll.png` / `--pc-monitor.png`

1.–5. Unauffällig sauber.
6. **Die Lücke ist weg.** „Carport" und „Meier Bau GmbH" stehen in beiden Größen direkt
   untereinander, der Kundenname klebt wieder am Titel. Genau das war der Befund. Erledigt.

**I) Anfragen-Übersicht mit vier langen Titeln** —
`anfragen-uebersicht-karten--pc-14zoll.png` / `--pc-monitor.png`

1.–5. Wie G.
6. Keine Überlappung, kein horizontaler Scroll. Sichtbar: In Karte 1 bricht der Kundenname um,
   ihre Trennlinie und die drei Meta-Zeilen liegen dadurch rund 24 px tiefer als bei den
   Nachbarkarten — die Beträge einer Reihe stehen nicht auf einer Linie. 🟡, Ursache nachgemessen
   (Code-Reviewer Punkt 1).

**J) Kunden-Detailseite mit langem Namen** —
`kunde-detail-langer-name--pc-14zoll.png` / `--pc-monitor.png`

1. *Farben:* „GESAMTUMSATZ" slate-50, „GEWINN" emerald-50 — vorbestehend, 🟡.
2.–5. Design-System eingehalten, eine Aktion („Bearbeiten") rechts oben in beiden Größen,
   fünf Reiter einzeilig.
6. Kein Überlauf, keine Überlappung. Das einsame Komma hinter dem Standort-Symbol (Kunde ohne
   Adresse) steht unverändert da — 🟡, vorbestehend, schon in Abschnitt 3 vermerkt.

**K) Kunden-Übersicht mit vier langen Namen** —
`kunde-uebersicht-lange-namen--pc-14zoll.png` / `--pc-monitor.png`

1.–5. Chips „KUNDE" grün / „ANFRAGER" amber (vorbestehend, 🟡), sonst sauber.
6. **Alle vier langen Kundennamen vollständig, kein „…"**, in beiden Größen. Karten gleich hoch.

**L) Mini-Karten der Kunden-Detailseite (neu aus Task 3b)** —
`kunde-mini-karten-projekte`, `-anfragen`, `-dokumente`, je `--pc-14zoll.png` / `--pc-monitor.png`

1. *Farben:* Projekt-Chip amber, Anfrage-Chip **purple** (vorbestehend, purple ist laut
   Design-System für KI-Momente reserviert — 🟡), Dokument-Chip rose.
2.–5. Design-System eingehalten, Karten klickbar, Reiterwechsel klar.
6. **Die Dokumentnummer „RE-2026-0501" steht jetzt vollständig und einzeilig neben dem
   „Rechnung"-Chip**, sauber auf einer Linie, ohne Kürzungsmarker — genau die Absicht von Task 3b
   („eine Belegnummer ist eine Kennung, halb abgeschnitten wertlos"). Die Herkunftszeile
   „Projekt: Treppenanlage …" darunter steht in beiden Größen vollständig da. Keine Überlappung,
   kein Überlauf.

**M) Die 22 mittelbar veränderten Dialog-Bilder** (`leiste-*`, `lieferant-modal-*`,
`toast-bei-dialog-*`)

1.–5. Der Vollbild-Dialog „Dokument bearbeiten" liegt über der Seite. Fehlerzustände sind
   durchgehend rose (Banner + Toast), der Countdown amber, der Sperr-Hinweis mit Schloss-Icon —
   Zustände auf einen Blick trennbar, Toast-Pflicht erfüllt, deaktivierte Knöpfe erklären sich
   über den Banner darüber. Genau eine Primäraktion je Zustand.
6. Zwei **vorbestehende** Überlappungen, nicht von Abschnitt 4 verursacht (das Modal wurde in
   diesem Abschnitt nicht angefasst): (a) der Inline-Fehlerkasten „Speichern fehlgeschlagen" legt
   sich bei 1440 über die Überschrift „Zahlungsbedingungen" und bei 1920 über die Eingabezeile
   „Skonto % / Skonto Tage / Netto Tage"; (b) ein zweizeiliger Toast überdeckt oben links den
   Modaltitel „Dokument bearbeiten". Beides 🟡, für eine spätere Aufräum-Runde.
   Ebenfalls unverändert: das sky-blaue Icon im Bestätigungsdialog „Nicht gespeichert"
   (`confirm-dialog.tsx`, `sky-100`/`sky-600`) — schon in Abschnitt 3 für Task 10 vermerkt.

### 🔴 Kritisch (blockiert)

**Der Titel der Kopfzeile läuft bei einem Komposita-Bauvorhaben quer über die Kennzahlen —
in beiden Größen, in allen vier Detail-Editoren.**

Bild: `anfrage-detail-kopf-komposita--pc-14zoll.png` und `--pc-monitor.png`. Auf dem
14-Zoll-Bild liest man „Absturzsicherungspodesttreppenanlagenmontagearbeitenüberwachungsdoku",
dann verschwindet der Text hinter dem Knopf „Bearbeiten"; die Beschriftungen „BRUTTO" und „NETTO"
liegen unter dem Titeltext und sind nicht mehr lesbar.

Nachgemessen im Browser (Wegwerf-Spec, danach gelöscht):

| Messung | Anfrage 1440 | Anfrage 1920 | Projekt 1440 | Projekt 1920 |
| --- | --- | --- | --- | --- |
| Breite der `<h1>` | 999 px | 999 px | 999 px | 999 px |
| rechte Kante ihres eigenen Titelblocks | 809 px | 1161 px | 668 px | 614 px |
| **Überstand über den eigenen Block** | **411 px** | **187 px** | **552 px** | **734 px** |
| Lage des Kastens „BRUTTO" | x 860–905 | x 1212–1257 | x 717–762 | x 663–708 |
| `main`-Überlauf | 0 px | 0 px | 0 px | 0 px |

Die `<h1>` überlappt die Kennzahl-Beschriftungen also nicht knapp, sondern deutlich, und zwar auf
**beiden** Prüfgrößen. Damit ist Frage 6 verletzt (überlappende Elemente, verdeckter und
abgeschnittener Text auf 14 Zoll) und Frage 5 gleich mit (der Titel des Datensatzes ist nicht mehr
lesbar).

**Warum keine Zusicherung anschlägt.** `keinHorizontalerUeberlauf` misst `main` — das ist 0.
`keinTextLaeuftUeber` vergleicht `scrollWidth` mit `clientWidth` **desselben** Elements — bei der
`<h1>` sind beide 999 px, sie läuft nicht über sich selbst, sondern über ihren Kasten hinaus.
`keineUeberlappungen` prüft nur interaktive Elemente, eine `<h1>` ist keins. Der neue Testfall
„Kopfzeile uebersteht ein langes Komposita-Bauvorhaben ohne Leerzeichen" sichert ausschließlich
zu, dass die beiden Knöpfe in der Kopf-Karte bleiben und `main` nicht überläuft — beides stimmt.
Die Spec ist also grün und das Bild trotzdem kaputt.

**Ursache, im Code nachgesehen.** Die `<h1 class="… break-words">` steht in allen vier Editoren
(`ProjektEditor.tsx` Z. 1069, `AnfrageEditor.tsx` Z. 1005, `Kundeneditor.tsx` Z. 287,
`LieferantenEditor.tsx` Z. 91) als **Flex-Item** in einer `<div class="flex items-center gap-3
flex-wrap">`. Ein Flex-Item hat `min-width: auto`, also mindestens seine Mindestinhaltsbreite.
`break-words` (`overflow-wrap: break-word`) senkt diese Mindestbreite **nicht** — anders als
`word-break: break-all` bzw. `overflow-wrap: anywhere`. Bei einem unteilbaren 77-Zeichen-Wort ist
die Mindestbreite 999 px, die `<h1>` wird so breit und ragt aus dem Titelblock heraus. Das
`min-w-0`, das Task 3b nachgezogen hat, sitzt auf dem **umschließenden** Textblock, nicht auf der
`<h1>`. Damit trifft die Annahme des Plans („genau der Fall, den `min-w-[18rem]` + `break-words`
lösen") nicht zu.

**Nachweisbar sein muss:** Der Titel bleibt in seinem Titelblock und bricht um; die
Kennzahl-Beschriftungen bleiben in beiden Größen vollständig lesbar. Gemessene
Ein-Klassen-Lösung, im Browser per `addStyleTag` gegengeprüft (ohne Quellcode anzufassen):
**`min-w-0` auf die `<h1>`**. Wirkung bei der Anfrage: Breite fällt von 999 auf 588 px (1440)
bzw. 812 px (1920), der Titel wird zweizeilig, kein Überstand mehr, Kopf-Karte 32 px höher, sonst
nichts verändert. Bild dazu: `zz-komposita-fix--pc-14zoll.png` (gelöscht; Inhalt: Titel
zweizeilig, „BRUTTO 84.500,00 €" und „NETTO 71.008,40 €" vollständig sichtbar, Knöpfe unverändert
rechts). Gehört in alle vier Dateien, weil die Bauweise identisch ist — beim Projekt-Editor mit
derselben Fixture gegengeprüft (Tabelle oben). Dazu eine Zusicherung, die den Fall wirklich
festhält: rechte Kante der `<h1>` ≤ rechte Kante ihres Titelblocks (die heutige
Knopf-Zusicherung reicht nicht).

### 💡 Hinweise (blockieren nicht)

1. **Projekt-Reiterleiste einzeilig, aber mit 17 px Reserve.** Nachgemessen bei 1440: sieben
   Reiter brauchen 899 px bei 916 px Platz, `overflow-x: visible`, alle sieben auf y=543. Bei 1920
   bleiben 185 px übrig. Eine Ziffer ist in dieser Schrift 7,78 px breit — es passen also noch
   **zwei zusätzliche Ziffern** über alle sieben Zähler zusammen. Details siehe „Auftrag des
   Code-Reviewers", Punkt 4.
2. **Trennlinien der Kartenreihen liegen nicht auf einer Höhe** (Projekt, Anfrage, Kunde), weil
   `mt-auto` dort wirkungslos ist. Nachgemessen, Ursache und Gegenprobe siehe „Auftrag des
   Code-Reviewers", Punkt 1. Die eigentliche 24-px-Lücke unter kurzen Titeln ist behoben.
3. **Mitarbeiter-Seitenpanel:** „Abteilung(en)" ragt bei 1440 mit einem langen Einzelwort 21 px
   aus der Karte (siehe C). Vorbestehend, gleiche Ursache wie der 🔴.
4. **Die Mitarbeiter-Seite tanzt aus der Reihe** (siehe eigener Abschnitt „Einheitlichkeit").
5. **Kunden-Knopfblock ohne `flex-wrap`/`gap-2`** — Abweichung von der Rezeptur, heute unsichtbar,
   siehe „Auftrag des Code-Reviewers", Punkt 5.
6. **Reiterleisten sind vier verschiedene Bauweisen** (siehe Einheitlichkeit). Nur der Lieferant
   hat `role="tablist"`; Kunde und Mitarbeiter haben ein `data-testid`, Projekt und Anfrage keins
   von beidem.
7. **Zwei vorbestehende Überlappungen im Dokument-Dialog** (Fehlerkasten über
   „Zahlungsbedingungen" bzw. über der Skonto-Zeile; zweizeiliger Toast über dem Modaltitel).
8. **Lieferant-Spec geht ins Internet** — siehe „Auftrag des Code-Reviewers", Punkt 2. Dabei
   nebenbei aufgefallen: die Lieferanten-Detailseite lädt `pdf.js` zur Laufzeit von
   `cdnjs.cloudflare.com`. Vorbestehend, aber ein zweiter Weg nach draußen; gehört mit auf die
   Aufräumliste.
9. **Vorbestehende Farbabweichungen, unverändert:** Kennzahl-Kacheln des Lieferanten in
   purple/blue/emerald; „Gewinn" emerald beim Kunden; Chips „KUNDE" grün / „ANFRAGER" purple;
   `sky-100`/`sky-600` in `confirm-dialog.tsx`. Alles für Task 10.
10. **Einsames Komma in der Kunden-Kopfzeile** ohne Adresse — unverändert, für die Aufräum-Runde.
11. **Knopf „Token erstellen"** bricht bei 1440 zweizeilig um und wirkt gedrängt (Mitarbeiter).

### Meine drei Befunde aus Abschnitt 3 — gegengeprüft

| Befund | Status | Nachweis |
| --- | --- | --- |
| **1 — Reiterleiste des Projekt-Editors bei 1440 zweizeilig** | **erledigt** | `projekt-detail-geschaeftsdokumente--pc-14zoll.png`: alle sieben Reiter auf **einer** Zeile (nachgemessen: y=543 für alle sieben; vorher 593/593/593/593/593/593/639). Die Trennlinie liegt jetzt unter **einer** Zeile, und der rose Unterstrich des aktiven Reiters „Geschäftsdokumente (2)" sitzt genau darauf statt mitten in der Karte. **Wirkt sie mit `gap-1`/`px-2` gedrängt? Nein.** Die Reiter stehen dicht, aber jeder ist als eigene Fläche erkennbar, Icon und Zähler haben Luft, und die Karte wurde flacher — eine Zeile Inhalt mehr ist ohne Scrollen sichtbar. Das ist die bessere Lösung, nicht die knappere. **Aber:** nur 17 px Reserve, siehe Hinweis 1. |
| **2 — Knopfblock der Projekt-Kopfzeile fällt bei 1440 nach unten links** | **erledigt** | `projekt-detail-geschaeftsdokumente--pc-14zoll.png`: „Bearbeiten" und „mit Anfrage zusammenführen" stehen nach dem Umbruch **rechtsbündig** in der zweiten Zeile, bündig mit der rechten Kartenkante und mit der Kennzahlen-Reihe darüber. **Sieht das aufgeräumt aus? Ja** — die Aktionen liegen jetzt in beiden Größen rechts, der Umbruch wirkt gewollt statt wie ein Unfall. Bei 1920 bleibt alles einzeilig, unverändert. |
| **3 — 24-px-Lücke unter kurzen Kartentiteln** | **erledigt, mit Rest** | `anfragen-uebersicht-kurzer-titel--pc-14zoll.png` / `--pc-monitor.png`: „Carport" und „Meier Bau GmbH" stehen direkt untereinander, keine Lücke mehr. **Stehen die Karten einer Reihe trotzdem gleich hoch? Die Karten ja, die Trennlinien nein.** Alle Karten einer Reihe sind exakt gleich hoch (gemessen 219 px, gleiche Ober- und Unterkante), aber die Trennlinie sitzt in Karten mit kurzem Titel 24 px höher als in der Nachbarkarte mit zweizeiligem Titel, weil `mt-auto` bei Projekt/Anfrage/Kunde wirkungslos ist. Nur die Lieferanten-Karte macht es richtig. 🟡, Details und Gegenprobe im nächsten Abschnitt. |

### Lieferant, Spec-Befund 2 — weg?

**Ja, eindeutig.**

Beweisbild vorher `docs/superpowers/specs/bilder/2026-09-04-layout-14-zoll/lieferant-detail-1440.png`:
Die Beschriftungen „GESAMTKOSTEN" und „BESTELLUNGEN" ragen sichtbar aus ihren Kästen heraus und
laufen in den Nachbarkasten; die vier Kästen sind gleich breit gerastert und zu schmal für ihre
eigene Beschriftung.

Nachher `lieferant-detail-langer-name--pc-14zoll.png`: Alle vier Beschriftungen — „GESAMTKOSTEN",
„BESTELLUNGEN", „ARTIKEL", „LIEFERZEIT Ø" — stehen vollständig in ihren Kästen, mit dem Wert
darunter. Kein Text berührt eine Kastenkante. Der Coding-Agent hat die Zahlen mitgeliefert
(Kasten „Gesamtkosten" 94,8 px vorher → 126,9 px nachher, „Bestellungen" 94,8 → 112,9 px), die
Spec sicherte 34 px bzw. 29 px Fehlbetrag zu — das deckt sich mit dem Bild. Bei 1920 dasselbe,
zusätzlich passt dort der Rollen-Chip „STAHL" wieder neben den Titel.

**Damit sind alle vier Ausgangsfehler der Spec erledigt:**

| Befund der Spec | Status | Nachweis |
| --- | --- | --- |
| **1 — Projekt-Editor, rechte Spalte abgeschnitten** | erledigt (Abschnitt 2) | `main`-Überstand 0 px, Karte „Projektdaten" vollständig im Fenster, auf allen sieben Reitern. |
| **2 — Kopfzeilen: Kennzahlen und Knöpfe gequetscht** | erledigt (Projekt/Anfrage/Kunde in Abschnitt 3, **Lieferant jetzt**) | Siehe oben; alle vier Kennzahl-Kästen des Lieferanten lesbar, „Bearbeiten" rechts in der Karte. |
| **3 — Menüleiste abgeschnitten** | erledigt (Abschnitt 2) | „Finanzen & Controlling" steht in jedem Bild dieses Laufs vollständig da, „Dokumentenrechte" und „Mietabrechnung" ohne „…". |
| **4 — Kartentitel abgehackt** | erledigt (Abschnitt 3, hier bestätigt) | Drei Karten je Reihe bei 1440, Titel zweizeilig; Kunden- und Lieferanten-Übersicht zeigen alle vier langen Namen ohne „…". |

Einschränkung, damit die Bilanz ehrlich bleibt: Befund 2 ist für die **in der Spec genannten**
Namen gelöst. Der 🔴 oben ist ein **anderer** Fall derselben Kopfzeile (unteilbares Einzelwort),
den erst Abschnitt 4 sichtbar gemacht hat — er stand nicht in der Ausgangs-Spec.

### Einheitlichkeit der fünf Detailseiten

Nebeneinander gelegt (Projekt, Anfrage, Kunde, Lieferant, Mitarbeiter, je 1440 und 1920):

**Was jetzt zusammenpasst.** Vier der fünf Seiten (Projekt, Anfrage, Kunde, Lieferant) haben
dieselbe Kopf-Karte: Zurück-Pfeil, runde Icon-Kachel, Titel mit Chip, Untertitel-Zeilen mit
Lucide-Icons, Kennzahlen rechts daneben, Knopfblock ganz rechts — in beiden Größen an derselben
Stelle. Das ist eine echte Familie und deutlich besser als vor Abschnitt 3.

**Wer aus der Reihe tanzt.**

1. **Mitarbeiter, und zwar deutlich.** Die Seite hat **gar keine Kopf-Karte**: Der Name steht als
   PageHeader-Großtitel („BEISPIELMUSTERFRAUENBERGWALDSCHMIDTSTEIN, BERNHARDINE" in Versalien),
   die Knöpfe liegen daneben auf dem Seitenhintergrund, es gibt keine Kennzahlen. Die anderen vier
   zeigen einen generischen PageHeader („LIEFERANTENDETAILS") und den Datensatznamen in der
   Kopf-Karte darunter. Dazu ist „Bearbeiten" hier eine gefüllte rose-600-Primäraktion, auf den
   anderen vier ein Outline-Knopf. Ein Handwerker, der von der Lieferanten- auf die
   Mitarbeiter-Seite wechselt, sieht zwei verschiedene Programme. **Das ist der eine Punkt, den ich
   an dieser Stelle billig korrigieren würde** — der Plan hat Task 7 ausdrücklich auf die
   Reiterleiste beschränkt, es ist also kein Vorwurf an den Coding-Agenten, aber es ist die letzte
   Gelegenheit, es günstig zu machen.
2. **Drei Kennzahl-Stile nebeneinander.** Projekt und Anfrage: Spalten mit `border-r`-Trennern auf
   weißem Grund. Kunde: zwei gefüllte Kacheln (slate-50 / emerald-50). Lieferant: vier gefüllte
   Kacheln in slate / purple / blue / emerald. Mitarbeiter: keine. Jedes für sich stimmig,
   nebeneinander wirkt es wie drei Entwürfe. Vorbestehend, schon in Abschnitt 3 gemeldet, in
   Abschnitt 4 planmäßig nicht angefasst.
3. **Vier Reiterleisten-Bauweisen.** Projekt: `gap-1`, `px-2`, Zähler in Klammern, aktiver Reiter
   mit rose-50-Fläche. Anfrage: `gap-2`, `px-3`, Klammern, Fläche. Kunde: `gap-1`, Zähler als
   graue Pillen, aktiver Reiter nur Unterstrich ohne Fläche. Lieferant: Pillen, Unterstrich ohne
   Fläche, als einziger mit `role="tablist"`. Mitarbeiter: keine Zähler, Fläche. Funktional alle
   in Ordnung und alle einzeilig — aber es ist nicht eine Leiste, sondern vier.
4. **Kartenraster:** hier ist die Familie vollständig — Projekt, Anfrage, Kunde und Lieferant
   zeigen bei 1440 drei und bei 1920 vier Karten, gleiche Kartenhöhe, gleicher Titelumbruch. Nur
   die Trennlinien-Ausrichtung fällt beim Lieferanten besser aus als bei den drei anderen
   (Hinweis 2).

Vorschlag für die Nacharbeit, nach Nutzen sortiert: erst Punkt 1 (Mitarbeiter-Kopfzeile auf die
Rezeptur ziehen), dann Punkt 3 (eine Reiterleiste als Ziel festlegen, `role="tablist"` überall),
Punkt 2 zuletzt — der kostet eine Gestaltungsentscheidung, keine Klasse.

### Auftrag des Code-Reviewers — alle sechs Punkte abgearbeitet

**1. `mt-auto` in fünf von sechs Karten wirkungslos — bestätigt, und man sieht es.**

Im Browser gemessen, `/projekte` mit „Carport", einem langen Titel und „Zaun" in einer Reihe:

| Karte | `margin-top` des Meta-Blocks | y der Trennlinie | Kartenhöhe |
| --- | --- | --- | --- |
| Carport (kurz) | **12px** | 618 | 219 |
| Terrassenüberdachung … (lang) | **12px** | 642 | 219 |
| Zaun (kurz) | **12px** | 618 | 219 |

Gegenprobe `/lieferanten`, gleiche Konstellation:

| Karte | `margin-top` | y der Trennlinie | Kartenhöhe |
| --- | --- | --- | --- |
| Stahl (kurz) | **24px** (aus `auto`) | 642 | 219 |
| Beschichtungs… (lang) | **0px** (aus `auto`) | 642 | 219 |
| Zaunbau (kurz) | **24px** (aus `auto`) | 642 | 219 |

Seine Diagnose stimmt exakt: Bei Projekt liegt der `mt-auto`-Block in einem
`p-4 space-y-3 flex-1 flex flex-col`, und Tailwinds `space-y-3`-Selektor
(`.space-y-3 > :not([hidden]) ~ :not([hidden])`, Spezifität 0-3-0) schlägt `.mt-auto` (0-1-0) —
`margin-top` bleibt bei 12 px. Die Lieferanten-Karte nutzt `p-4 flex flex-col h-full gap-3`, dort
löst `auto` sauber auf.

**Sieht man es? Ja.** Auf `zz-nachtrag-projekte--pc-14zoll.png` stehen die Trennlinie und die
darunter liegenden drei Meta-Zeilen der mittleren Karte 24 px tiefer als bei den Nachbarn; die
drei Beträge einer Reihe liegen nicht auf einer Linie, das Auge muss zickzack laufen. Auf
`zz-nachtrag-lieferanten--pc-14zoll.png` liegt alles exakt auf einer Höhe — der Unterschied
springt im direkten Vergleich sofort ins Auge. Es steht also **nicht nur auf dem Papier.**

Trotzdem **🟡, kein 🔴**: Nichts wird verdeckt, abgeschnitten oder unerreichbar, und der
eigentliche Befund aus Abschnitt 3 (Kundenname wird vom Titel losgerissen) ist behoben — der Rest
ist Ausrichtung. Die Lösung ist eine Klasse und im selben Repo schon vorgemacht:
`space-y-3` → `gap-3` am Kartenkörper von `ProjektCard`, `AnfrageCard`, `KundenKarte`,
`KundenProjektKarte`, `KundenAnfrageKarte`, so wie es `LieferantCard` macht. Zusicherung dazu:
gleiche y-Position der Trennlinie über alle Karten einer Reihe, mit gemischt langen und kurzen
Titeln in der Fixture.

**2. Netzwerkzugriff in `lieferant-layout.spec.ts` — bestätigt, mit Mitschnitt.**

Alle Anfragen der Lieferanten-Detailseite mitgeschnitten, die nicht an `localhost` gehen:

```
document  https://www.google.com/maps?q=Industriestra%C3%9Fe%2044%2C%2030179%2C%20Hannover&output=embed&z=14
document  https://www.google.com/maps/embed?origin=mfe&pb=!1m3!2m1!1sIndustriestra%C3%9Fe+44,+30179,+Hannover!6i14
script    https://maps.gstatic.com/maps-api-v3/embed/js/66/3d/intl/de_ALL/init_embed.js
script    https://maps.googleapis.com/maps/api/js?key=…&paint_origin=&libraries=ge
script    https://cdnjs.cloudflare.com/ajax/libs/pdf.js/3.11.174/pdf.min.js
```

Er hat recht: Die Fixture setzt `strasse`/`plz`/`ort`, `GoogleMapsEmbed` rendert daraufhin ein
`<iframe src="https://www.google.com/maps?q=…">` (`GoogleMapsEmbed.tsx` Z. 26), und `page.route`
fängt nur `**/api/**`. Die Spec ist damit vom Internet und von Googles Servern abhängig — genau
das, was „kein Backend, keine echten Personendaten" verhindern soll. Die drei Specs aus
Abschnitt 3 lassen die Adressfelder ausdrücklich deshalb leer. Nebenbei: der API-Key in der
letzten Zeile stammt von Googles eigener Embed-Seite, nicht aus unserem Code — **kein Key-Leck**.
Die Adresse selbst geht aber sehr wohl an Google, im Test eine Fantasieadresse, im Produkt die
echte Lieferantenanschrift. Der `pdf.js`-Aufruf an cloudflare ist ein zweiter Weg nach draußen und
unabhängig von der Adresse.

**Erklärt das die leere weiße Seite bei parallelen Workern?** Plausibel, aber nicht bewiesen.
Vier Worker, die gleichzeitig eine Maps-Einbettung samt Maps-JS-API laden, sind ein glaubwürdiger
Auslöser für eine hängende Seite; ich konnte die leere Seite in meinen beiden vollen Läufen mit
Standard-Workern jedoch **nicht reproduzieren** (198 grün, zweimal). Ich würde die Adressfelder
trotzdem leeren oder `**/*google*` und `**/*gstatic*` abfangen — nicht wegen der Flakiness,
sondern weil eine E2E-Spec nicht ins Internet telefonieren darf. Empfehlung: in `hilfen/api.ts`
einen Riegel für alles Nicht-`localhost` einbauen, dann fällt so etwas künftig sofort auf.

**3. Optik der neuen Kartenhöhen, vier Übersichten, beide Größen.** Angeschaut:
`projekt-uebersicht-lange-titel`, `anfragen-uebersicht-karten`, `anfragen-uebersicht-kurzer-titel`,
`kunde-uebersicht-lange-namen`, `lieferant-uebersicht-lange-namen`, je beide Größen. Ergebnis:
Karten sind überall gleich hoch, drei je Reihe bei 1440 und vier bei 1920, die Lücke unter kurzen
Titeln ist weg. Der einzige Rest ist die Trennlinien-Ausrichtung aus Punkt 1 — beim Lieferanten
richtig, bei den drei anderen um 24 px versetzt. Insgesamt eine klare Verbesserung gegenüber
Abschnitt 3.

**4. Projekt-Reiterleiste: 17 px Reserve — reicht das?** Knapp, aber ja für heute. Gemessen bei
1440: Leiste 916 px breit (x 89–1005), letzter Reiter endet bei 988, alle sieben auf y=543,
`overflow-x: visible`, `gap: 4px`. Reserve **17,4 px**. Eine Ziffer ist in dieser Schrift 7,78 px
breit — es passen also **zwei zusätzliche Ziffern** über alle sieben Zähler zusammen, dann bricht
die Leiste wieder um. Mit echten Daten ist das schnell erreicht: „Zeiten (34)" plus
„Geschäftsdokumente (12)" sind schon drei Ziffern mehr. Bei 1920 sind es 185 px Reserve, also kein
Thema. **Meine Einschätzung:** Für die Abnahme reicht es, die Zusicherung „alle sieben auf
derselben y-Position" hält den Zustand fest, und ein Umbruch wäre kein Datenverlust. Aber die
Aussage „bei 1440 einzeilig" gilt strenggenommen nur für ein fast leeres Projekt. Wer das dauerhaft
will, braucht mehr als Abstände — etwa den Zähler als kleine Pille statt in Klammern (spart
Zeichen) oder zwei Reiter zusammenlegen. Kein Grund, Abschnitt 4 aufzuhalten; gehört in die
Nacharbeit, zusammen mit einer Zusicherung, die zweistellige Zähler in der Fixture verwendet.

**5. Kunden-Knopfblock ohne `flex-wrap`/`gap-2` — er hat recht, sichtbar ist es heute nicht.**
`Kundeneditor.tsx` Z. 319 hat `flex items-start shrink-0 ml-auto`, die anderen drei haben
`shrink-0 ml-auto flex flex-wrap items-start gap-2`. **Fällt es auf? Nein** — die Kunden-Kopfzeile
hat genau **einen** Knopf („Bearbeiten"), da haben `flex-wrap` und `gap-2` nichts zu tun. Auf
`kunde-detail-langer-name--pc-14zoll.png` und `--pc-monitor.png` sitzt er in beiden Größen korrekt
rechts oben. Es fällt in dem Moment auf, in dem ein zweiter Knopf dazukommt: dann kleben die
beiden ohne Abstand aneinander und können nicht umbrechen. Zwei Klassen, die man jetzt mitnehmen
sollte, solange die Rezeptur frisch ist. 🟡.

**6. Voller E2E-Lauf in beiden Größen:** 198 grün, 0 rot, zweimal gefahren (siehe oben).

### Aufräumen

Wegwerf-Specs `e2e/zz-messung-komposita.spec.ts`, `e2e/zz-messung-review.spec.ts` und
`e2e/zz-review-nachtrag.spec.ts` gelöscht, Ordner `test-results-zz/` entfernt, alle
`zz-*`-Screenshots gelöscht. Kein Produktivcode angefasst — sämtliche Proben liefen über
`page.addStyleTag` bzw. `page.evaluate`, kein `git stash`, kein `git checkout` auf eine Quelldatei.
`git status` im Review-Worktree sauber. Die 84 Abnahme-Screenshots unter `test-results/design/`
stammen aus dem zweiten vollständigen Lauf und sind vollzählig.

## Abschnitt 4 — Task 6, Nachbesserung 1 (Coding-Agent)

Zeit: 2026-09-06T13:06:29Z
Branch: layout/task-6-lieferant
Commit(s): 7d507da0 (test), 70a3acd0 (fix)
Status: fertig

Was gemacht wurde:

**1. 🔴 Blocker — `<h1>` ohne `min-w-0`, Titel lief bei einem Einwort-Namen ueber die Kennzahlen.**
Testgetrieben (Skill `superpowers:test-driven-development` befolgt): in `e2e/lieferant-layout.spec.ts` neue Fixture `LIEFERANT_EINWORT_LANG = 'Baustahlgewindestangenspezialgroßhandelsvertriebsgesellschaft'` (ein Wort, kein Leerzeichen) plus neuer Testfall, der die rechte Kante der `<h1>` gegen die rechte Kante ihres Titelblocks (`ancestor::div[contains(@class,"min-w-[18rem]")]`) prueft. Rot verifiziert (siehe Zahlen unten), dann `LieferantenEditor.tsx` Z. 91 gefixt: `min-w-0` zusaetzlich auf die `<h1>` (neben dem bereits vorhandenen `break-words`). Ursache wie vom Design-Reviewer diagnostiziert: die `<h1>` ist selbst ein Flex-Item in `flex items-center gap-3 flex-wrap` und behaelt ohne eigenes `min-w-0` ihre volle Mindestinhaltsbreite (`min-width: auto`) — `break-words` senkt diese Mindestbreite bei einem Flex-Item nicht, das erledigt erst `min-w-0`. Das schon vorhandene `min-w-0` am umschliessenden Textblock (Z. 89) reicht nicht, weil die `<h1>` ein eigenes, davon unabhaengiges Flex-Item ist.

**2. 🟡 — Spec telefonierte ins Internet.** Neue allgemeine Hilfsfunktion `blockiereFremdeNetzwerkzugriffe(page)` in `react-pc-frontend/e2e/hilfen/api.ts`: registriert `page.route('**/*', ...)` und bricht (`route.abort()`) jede Anfrage ab, deren Hostname nicht `localhost`/`127.0.0.1` ist; localhost-Anfragen laufen mit `route.continue()` normal weiter (spezifischere, spaeter registrierte Routen wie `**/api/**` bekommen sie ohnehin zuerst zu sehen). In `lieferant-layout.spec.ts` in beiden Stub-Funktionen (`stubLieferantDetailApi`, `stubLieferantenUebersichtApi`) **vor** der ersten Navigation aufgerufen. Die Adressfelder (`strasse`/`plz`/`ort`) in `DUMMY_LIEFERANT` bleiben bewusst gefuellt (wie vom Auftrag gefordert) — die Kopfzeile wird also weiterhin mit echter Adresse und sichtbarer Kartenvorschau geprueft, ohne dass real etwas nach draussen geht. Netzwerk-Mitschnitt (`page.on('request')`/`page.on('response')`) am Ende des Detail-Testfalls: sichert zu, dass (a) mindestens eine fremde Anfrage tatsaechlich ausgeloest wird (sonst waere die Zusicherung wirkungslos) und (b) keine einzige davon je eine echte Antwort bekommt.

Gemessene Zahlen:

| Messung | vorher (1440) | nachher (1440) | vorher (1920) | nachher (1920) |
| --- | --- | --- | --- | --- |
| `<h1>` rechte Kante (Einwort-Fixture) | 955px | 675px | 1083px | 1027px |
| Titelblock rechte Kante | 675px | 675px | 1027px | 1027px |
| Ueberstand `<h1>` ueber Titelblock | **280px** | 0px | **56px** | 0px |
| `<h1>` Breite | (voller Wortblock, nicht separat gemessen) | 454px | (voller Wortblock) | 678px |

Netzwerk-Mitschnitt (Detailseite, Standard-Fixture mit gefuellter Adresse, `pc-14zoll`, eigener Wegwerf-Lauf zur Dokumentation):
- Initiierte fremde Anfragen (dann abgebrochen): `script https://cdnjs.cloudflare.com/ajax/libs/pdf.js/3.11.174/pdf.min.js`, `document https://www.google.com/maps?q=Industriestra%C3%9Fe%2044%2C%2030179%2C%20Hannover&output=embed&z=14`.
- Beide `requestfailed` mit `net::ERR_FAILED`, **keine** einzige `response` von einem fremden Host (leeres Array).
- Nur 2 statt der vom Design-Reviewer vor dem Fix mitgeschnittenen 5 Anfragen (2× `google.com/maps`, `maps.gstatic.com`, `maps.googleapis.com`, `cdnjs.cloudflare.com`) — plausibel, weil `route.abort()` schon die allererste `document`-Anfrage an `google.com/maps` unterbindet, bevor der Browser die davon abhaengigen Folge-Ressourcen (das eingebettete `/maps/embed`-Dokument, `maps.gstatic.com`, `maps.googleapis.com`) ueberhaupt anfragen kann. Der Riegel schneidet also nicht nur eine einzelne Anfrage ab, sondern die ganze Kaskade.
- In der eigentlichen Spec (`lieferant-layout.spec.ts`) selbst wird das ueber `expect(fremdeAnfragen.length).toBeGreaterThan(0)` und `expect(fremdeAntworten).toEqual([])` zugesichert, nicht ueber die Wegwerf-Messung — die diente nur der genauen URL-Liste fuer dieses Log.

Ergebnis der Gates:
- Port-Check: `netstat -ano | findstr :5206 | findstr LISTENING` leer (vor jedem Lauf geprueft; nicht-listende `WARTEND`/TIME_WAIT-Reste von vorherigen eigenen Laeufen wurden nicht als belegt gewertet).
- `E2E_PORT=5206 npx playwright test e2e/lieferant-layout.spec.ts`: **6/6 gruen** (3 Testfaelle × 2 Groessen), reproduziert mit `--workers=1` und mit den Standard-4-Workern (mehrfach). Keine leere weisse Seite mehr unter Standard-Parallelitaet beobachtet, seit der Netzwerk-Riegel steht — deckt sich mit der Vermutung des Design-Reviewers, dass die vier parallel ladenden Maps-Einbettungen ein plausibler (nicht bewiesener) Ausloeser fuer die frueher beobachtete leere Seite waren.
- `npx vitest run src/pages/LieferantenEditor.test.tsx`: **9/9 gruen**, unveraendert.
- `npm run lint`: 0 Fehler, 1 vorbestehende Warnung (`BelegeKasseEditor.tsx:1204`), identisch zur Baseline.
- `npm run build`: gruen. Build-Output vor dem Commit verworfen (`git checkout -- src/main/resources/static` + `git clean -fd src/main/resources/static/assets`), `git status` danach sauber bis auf die drei eigenen Dateien.

Bedenken / Abweichungen vom Plan:
- Keine inhaltliche Abweichung vom Auftrag der Nachbesserung. Kleine Ergaenzung: `blockiereFremdeNetzwerkzugriffe()` wurde zusaetzlich auch in `stubLieferantenUebersichtApi` verdrahtet (nicht nur im Detail-Stub) — die Uebersicht rendert zwar kein `GoogleMapsEmbed`, laedt aber ueber `index.html` ebenfalls `pdf.js` von cdnjs bei jeder Navigation; der Riegel greift dort also ebenfalls, ohne dass es der Auftrag ausdruecklich verlangt hat ("riegel allgemein" gelesen als: ueberall in dieser Datei anwenden, nicht nur im einen Testfall, der ihn ausgeloest hat).
- `min-w-0` wurde ausschliesslich in `LieferantenEditor.tsx` gesetzt (Projekt/Anfrage/Kunde macht laut Auftrag ein anderer Agent) — diese drei Dateien wurden nicht angefasst.
- Sonst keine Abweichungen: beide Befunde wie im Auftrag beschrieben behoben, testgetrieben fuer Befund 1 (rot vor dem Fix verifiziert, danach gruen), Befund 2 durch Netzwerk-Mitschnitt nach dem Fix belegt.

## Abschnitt 4 — Task 3b, Nachbesserung 1 (Coding-Agent)

Zeit: 2026-09-06T13:10:54Z
Branch: layout/task-3b-nacharbeit (Worktree wt/layout-task-3b, unverändert weiterverwendet)
Commit(s): 769a74fc (Projekt), 7e739424 (Anfrage), 167e2d28 (Kunde)
Status: fertig

Anlass: Design-Reviewer meldete einen 🔴 (h1 überläuft bei einem einzigen langen Wort) plus vier 🟡 zu Nacharbeit Abschnitt 4.

Was gemacht wurde (testgetrieben — rot verifiziert per Patch aus git diff, git checkout -- auf die Produktivdatei(en), Spec/Probe rot gefahren, Patch zurückgespielt, grün gefahren; kein git stash verwendet):

**Der Blocker (🔴): min-w-0 an die h1 selbst.** Bestätigt: die h1 ist eigenes Flex-Item in "flex items-center gap-3 flex-wrap" und behält ihr eigenes min-width: auto — break-words senkt die Mindestinhaltsbreite eines Flex-Items nicht, das min-w-0 aus der letzten Nacharbeit saß nur am umschließenden div, nicht an der h1. Fix in allen drei Dateien: min-w-0 zusätzlich auf der h1-Klasse (ProjektEditor.tsx, AnfrageEditor.tsx, Kundeneditor.tsx).

Rot verifiziert mit einem Wort ohne Leerzeichen (Absturzsicherungspodesttreppenanlagenmontagearbeitenüberwachungsdokumentation, 79 Zeichen, bzw. ein 67-Zeichen-Pendant für Kunde), gemessen h1-Breite konstant 999px (841px bei Kunde) über alle drei Seiten und beide Größen:
- Projekt: 1440 → Titelblock 548px, Überstand 583px. 1920 → Titelblock 366px, Überstand 765px.
- Anfrage: 1440 → Titelblock 720px, Überstand 411px. 1920 → Titelblock 944px, Überstand 187px (deckt sich exakt mit den vom Koordinator genannten Zahlen 411px/187px — offenbar dieselbe Seite/derselbe Wortlänge wie in der ursprünglichen Messung).
- Kunde: 1440 → Titelblock 896px, Überstand 77px.

Nach dem Fix: Überstand ≤ 1px in allen sechs Kombinationen (grün verifiziert).

Zusicherung: neue Testfälle in allen drei Specs ("Projekt-Kopfzeile: h1 bei einem einzigen langen Wort ohne Leerzeichen", der bestehende Anfrage-Komposita-Test um dieselbe Prüfung erweitert, "Kunden-Detailseite: h1 bei einem einzigen langen Wort ohne Leerzeichen" neu) — jeweils rechte Kante der h1 gegen rechte Kante des Titelblocks (xpath-Ancestor mit min-w-[18rem]-Klasse), Toleranz 1px.

**Vier 🟡, gleiche Dateien:**

1. **space-y-3 → gap-3** an ProjektCard, AnfrageCard, KundenKarte, KundenProjektKarte, KundenAnfrageKarte. Ursache bestätigt: Tailwinds space-y-3 erzeugt den Selektor "> * + *" (Spezifität 0-3-0), der die Margin auf jedes Kind außer dem ersten setzt und damit höhere Spezifität hat als .mt-auto (0-1-0) — mt-auto am Meta-Block wurde dadurch nie wirksam. Rot verifiziert an KundenKarte mit zwei Kunden derselben Kartenreihe (ein kurzer, ein langer Name): Ansprechpartner-Zeilen 24px versetzt (y=611/635) — exakt die vom Koordinator genannte Größenordnung. Nach dem Fix: 0px Versatz. Test dauerhaft in kunde-layout.spec.ts ergänzt (zweiter Kunde in derselben Zeile, Zusicherung auf gleiche y-Position der Meta-Block-Zeilen).
2. **E-Mail-Kürzung ohne title**: AnfrageEditor.tsx (Kunden-E-Mail im Seitenbereich, Z. 1454 im Ausgangszustand) auf break-words umgestellt — dieselbe Kürzung, die im Projekt-Editor bereits behoben war, blieb hier unentdeckt, weil die Fixture kundenEmails: [] setzte. Fixture um eine lange E-Mail-Adresse ergänzt, rot verifiziert (keinTextLaeuftUeber, 416px Überstand), Fix danach grün. Kundeneditor.tsx (schwächere Zweitstelle, Kontaktdaten der Detailseite) ebenfalls auf break-words umgestellt, ohne eigenen neuen Testfall (bestehende Fixture-E-Mail dort ist kurz genug, dass sie mit oder ohne Fix nicht überläuft — der Umstellung auf break-words schadet das nicht, sie ist trotzdem korrekt).
3. **Kundeneditor.tsx Knopfblock**: flex-wrap + gap-2 ergänzt, um exakt der Rezeptur zu entsprechen. Heute mit nur einem Knopf ("Bearbeiten") visuell wirkungslos, keine neue Zusicherung dafür gebaut (nichts, das mit einem einzelnen Knopf messbar rot werden könnte).
4. **"Bau Tagebuch" im Tab-Inhalt → "Tagebuch"**: ProjektEditor.tsx und AnfrageEditor.tsx, passend zu den bereits umbenannten Reitern. Keine bestehende Zusicherung hing daran (per Grep bestätigt), keine neue nötig.

**Messung ohne Fix (Auftrag des Koordinators): Projekt-Reiterleiste mit zweistelligen Zählern.** Fixture mit 34 Einträgen je Kategorie (Zeiten, Material, E-Mails, Geschäftsdokumente, Dateien, Tagebuch — alle als "(34)" sichtbar). Gemessen:
- 1440: Reiterleiste bricht wieder auf 6+1 Zeilen um (y-Werte 593×6, 635×1) — derselbe Umbruch wie vor der gap-1/px-2-Korrektur aus der ersten Nacharbeits-Runde, diesmal durch die breiteren Zähler statt durch den Namen "Tagebuch" ausgelöst.
- 1920: alle sieben Reiter bleiben einzeilig (y=627 durchgehend).
Kein Fix vorgenommen — die Testfall-Zusicherungen sind bewusst nur auf "kein Reiter verschwindet, kein seitliches Verstecken" begrenzt (7 Knöpfe, alle sichtbar, kein overflow), beide erfüllt. Die 17px-Reserve bei 1440 ist damit als reale, aber nicht blockierende Grenze dokumentiert: schon zweistellige Zähler kippen die Leiste zurück in den Umbruch.

Ergebnis der Gates (aus react-pc-frontend/, Port 5217 frei, netstat -ano | findstr :5217 leer):
- E2E_PORT=5217 npx playwright test e2e/projekt-detail-layout.spec.ts e2e/anfrage-layout.spec.ts e2e/kunde-layout.spec.ts: 22/22 grün (11 Testfälle × pc-14zoll + pc-monitor), gegen den finalen committeten Stand nachgefahren.
- npx vitest run src/pages/ProjektEditor.test.tsx src/pages/AnfrageEditor.test.tsx src/pages/Kundeneditor.test.tsx: 15/15 grün.
- npm run lint: 0 Fehler, genau die 1 vorbestehende Warnung (BelegeKasseEditor.tsx:1204) — identisch zur Baseline.
- npm run build: grün (tsc -b + vite build). Build-Output vor jedem Commit verworfen (git checkout -- src/main/resources/static + git clean -f src/main/resources/static/assets), git status danach sauber.

Bedenken / Abweichungen vom Auftrag:
- Für die 🟡-Punkte 3 (Knopfblock-Rezeptur) und 4 (Tagebuch-Überschrift) wurden keine neuen Zusicherungen gebaut, weil an diesen Stellen mit den heutigen Fixtures nichts automatisiert messbar rot werden kann (ein Knopf allein umbricht nie sichtbar; die Überschrift hing an keiner bestehenden Prüfung). Reine Textkorrektur bzw. Klassenangleichung, per Lesen verifiziert.
- Kundeneditor.tsx-Kontaktdaten-E-Mail (🟡-Punkt 2, schwächere Zweitstelle) hat keinen eigenen roten Testfall bekommen — die bestehende Fixture-E-Mail ist zu kurz, um bei den heutigen Spaltenbreiten zu überlaufen. Fix trotzdem umgesetzt (break-words statt truncate), da korrekt und risikofrei.
- Sonst keine Abweichungen — alle fünf im Auftrag genannten Punkte (Blocker + vier 🟡) sowie die Messung wurden an genau den genannten Stellen umgesetzt bzw. durchgeführt.

## Abschnitt 4 — Code-Review Runde 2 (Code-Reviewer)

Zeit: 2026-09-06T13:31:47Z
Branch: feature/layout-14-zoll @ 56737c3a
Commit(s) geprüft: Diff `9dd26f7e..HEAD` (Merges 41913b4a Task 3b, 56737c3a Task 6) — 9 Dateien, 502+/30−, alle unter `react-pc-frontend/`
Status: fertig
Ampel: 🟡

Nur die Nachbesserung geprüft, der Rest von Abschnitt 4 war in Runde 1 abgenommen. Rollenteilung wie gehabt: **kein Playwright-Lauf von meiner Seite**, E2E und Screenshots liegen beim Design-Reviewer im Parallel-Worktree.

### Meine sechs Hinweise aus Runde 1

| # | Thema | Stand |
| --- | --- | --- |
| 1 | `space-y-3` schlägt `mt-auto` nieder | **erledigt** — am gebauten CSS belegt, siehe unten |
| 2 | Lieferant-Spec telefoniert ins Internet | **teilerledigt** — Riegel gebaut und korrekt, deckt aber 1 von 16 Specs; zwei Löcher bleiben, siehe Hinweis B |
| 3 | E-Mail-Kürzung in der Anfrage | **erledigt** — Anfrage-Stelle richtig gebaut; die zweite Stelle im Kunden trifft nicht, siehe Hinweis A |
| 4 | Kunden-Knopfblock ohne `flex-wrap`/`gap-2` | **erledigt** |
| 5 | Zwei Bauweisen für dieselbe Karte | **erledigt** — mit `gap-3` sind alle sechs Karten gleich gebaut; der Rest (`h-full` gegen `flex-1`) ist wie gesagt Geschmackssache |
| 6 | „Bau Tagebuch" als Überschrift | **erledigt** — in beiden Editoren auf „Tagebuch" gezogen |

Hinweis 7 (fehlende ARIA-Rollen in der Mitarbeiter-Reiterleiste) und 8 (Absicherung hängt vollständig an Playwright) waren schon in Runde 1 als Notiz für Task 10 bzw. als bewusste Eigenschaft des Vorhabens gekennzeichnet — unverändert.

### Greift `mt-auto` jetzt? Ja, am gebauten CSS nachgesehen

Frisch gebautes `src/main/resources/static/assets/index-DbBUBXVR.css`:

```
.mt-auto{margin-top:auto}                                              (Offset  9.743)
.gap-3{gap:.75rem}                                                     (Offset 21.963)
.space-y-3>:not([hidden])~:not([hidden]){…margin-top:calc(.75rem * …)}  (Offset 23.568)
```

Damit ist der Befund aus Runde 1 doppelt belegt: `.space-y-3>…` hat Spezifität 0-3-0 gegen 0-1-0 **und** steht zusätzlich später in der Datei — `mt-auto` verlor auf beiden Wegen. `.gap-3` setzt überhaupt keine Margin, also ist `.mt-auto` am Meta-Block jetzt unangefochten. Der Fix ist mechanisch richtig, nicht nur zufällig wirksam.

Gegenprobe am Quelltext: alle fünf Container tragen `gap-3` **und** `flex flex-col` (`ProjektEditor.tsx:4172`, `AnfrageEditor.tsx:183`, `Kundeneditor.tsx:125/171/868`), und der `mt-auto`-Block ist in jedem der fünf ein **direktes** Kind (Einrückung geprüft, nicht nur gegrept) — ohne das würde `gap` nichts nützen. Kein `space-y-3` mehr in einem Karten-Container. Der einzige andere `space-y-3`-Container mit `flex flex-col` im Abschnitt (`ProjektEditor.tsx:2617`) hat kein `mt-auto`-Kind, ist also nicht betroffen.

### `min-w-0` an den vier `<h1>`: bricht nichts

Die vier `<h1>` tragen `text-2xl font-bold text-slate-900 break-words min-w-0` — kein `truncate`, kein `whitespace-nowrap`, nichts, das mit `min-width: 0` in Konflikt geriete. Einziger Nebeneffekt: wird es eng, bricht künftig der Titel mitten im Wort um, statt die Plakette daneben in die nächste Zeile zu drücken. Das ist die gewollte Wirkung und in allen vier Dateien gleich. Keine Zeile gefunden, die vorher bewusst nicht umbrach.

### Mutationsproben auf die Nachbesserung

Neun Stellen mutiert (4 × `min-w-0` raus, 5 × `gap-3` zurück auf `space-y-3`). `git diff --stat` danach: genau 4 Dateien, 9 Zeilen — die Klassen-Zeichenketten sind eindeutig, kein Kollateraltreffer. Anschließend `git checkout --`, `git diff` und `git status` leer, HEAD unverändert `56737c3a`.

**Unit-Ebene:** kein Unit-Test prüft `min-w-0`, `gap-3` oder `space-y-3` (per Grep über alle `*.test.tsx` bestätigt). Wie in Runde 1: die Absicherung liegt vollständig bei Playwright.

**Playwright-Ebene, aus dem Spec-Code abgeleitet:**

| Mutation | Was greift |
| --- | --- |
| `min-w-0` raus (Projekt) | `projekt-detail-layout.spec.ts`, neuer Testfall „`<h1>` ragt nicht rechts aus dem Titelblock" |
| `min-w-0` raus (Anfrage) | `anfrage-layout.spec.ts`, Überstands-Prüfung im bestehenden Komposita-Testfall |
| `min-w-0` raus (Kunde) | `kunde-layout.spec.ts`, neues `describe` — **aber laut Messung des Coding-Agenten nur bei 1440** (dort 77 px Überstand); für 1920 ist keine Zahl protokolliert, das 67-Zeichen-Wort passt dort in den breiteren Titelblock |
| `min-w-0` raus (Lieferant) | `lieferant-layout.spec.ts`, neuer Testfall + `keinTextLaeuftUeber` |
| `gap-3` → `space-y-3` (KundenKarte) | `kunde-layout.spec.ts`, neue y-Versatz-Prüfung der zwei Ansprechpartner-Zeilen (Toleranz 2 px, gemessen wären 24 px) |
| `gap-3` → `space-y-3` (ProjektCard, AnfrageCard, KundenProjektKarte, KundenAnfrageKarte) | **nichts** — siehe Hinweis E |

Je Datei greift genau eine Zusicherung, sauber getrennt, kein Übersprechen zwischen den vier Editoren.

### 🛑 Kritisch (blockiert)

Keine. Keine Korrektheitsfehler, Lint und Build grün, kein Assertion-Fehler in den Unit-Tests, keine Datei außerhalb `react-pc-frontend/`, kein Build-Output in den Commits, keine echten Personendaten (Fantasienamen, `.example`-TLD).

### 💡 Hinweise (blockieren nicht)

**A. Die E-Mail-Kürzung in `KundenKarte` trifft nicht — derselbe Mechanismus wie der 🔴, eine Ebene tiefer. Wichtigster neuer Befund.**
`Kundeneditor.tsx:901` heißt jetzt `<p className="flex items-center gap-2 break-words">` mit dem Mail-Symbol und dem E-Mail-Text darin. Der Text ist damit ein **anonymes Flex-Item** und behält `min-width: auto`. Und `overflow-wrap: break-word` senkt die min-content-Breite ausdrücklich **nicht** — das tun nur `overflow-wrap: anywhere` und `word-break: break-all`. Genau die Regel, mit der der Design-Reviewer den 🔴 an der `<h1>` begründet hat. Eine lange E-Mail-Adresse (ein Wort ohne Leerzeichen) wird also weiterhin nicht umbrochen, sondern schiebt das `<p>` über die Kartenbreite hinaus.
Vorher hielt `truncate` (`overflow: hidden` am `<p>`) sie wenigstens innerhalb der Karte — ohne Auslassungspunkte, aber ohne Überlauf. Verschärfend: `KundenKarte` ist die **einzige** der sechs Karten, deren `Card` kein `overflow-hidden` trägt (`Kundeneditor.tsx:865`), der Überstand landet also sichtbar im Raster daneben.
Ungetestet: keine Fixture in `kunde-layout.spec.ts` setzt `kundenEmails`, die Stelle rendert in keinem Testfall. Der Coding-Agent schreibt selbst, er habe hier keine rote Probe gebaut.
Fix, ein Einzeiler: den Text in ein `<span className="min-w-0 break-words">` fassen (dann greift `min-w-0` auf einem echten Flex-Item), oder `break-all` statt `break-words`. Dazu eine lange E-Mail in die Übersichts-Fixture, wie schon in Runde 1 für Task 9 vorgeschlagen.
Die Schwesterstelle in `AnfrageEditor.tsx:1478` ist dagegen **richtig**: das `<a class="block … break-words">` steht in einem normalen Block-Container (`space-y-4`), dort wirkt `break-words` wie erwartet. Wortgleich mit der Projekt-Fassung, Rezeptur-treu.

**B. Der Netz-Riegel ist korrekt gebaut, deckt aber nur eine von 16 Specs — und zwei Löcher kann er prinzipiell nicht schließen.**
Zur Implementierung selbst: `page.route('**/*')` fängt alles, was durch den Netzwerk-Stack geht, inklusive `<iframe>`-Dokumenten und Unterframes. `data:`, `blob:` und `about:blank` laufen an der Interception vorbei (keine Netzwerk-Requests) — der Riegel bricht sie also **nicht** ab, was gut ist. Der Vite-Dev-Server auf einem anderen Port ist unkritisch: geprüft wird der Hostname, nicht der Port, und `baseURL` ist `http://localhost:${port}`. Die Registrierungsreihenfolge stimmt: der Riegel wird zuerst registriert, die spezifischere `**/api/**`-Route danach — Playwright ruft die zuletzt registrierte zuerst, `/api` bleibt also gestubbt. Und `pdf.js` abzubrechen ist harmlos: `index.html` prüft `if (window.pdfjsLib)`, `PdfCanvasViewer.tsx` und `LivePreviewPanel.tsx` fallen sauber auf den iframe-Weg zurück.
**Loch 1 — `e2e/hilfen/aufwaermen.ts` (globalSetup).** Öffnet vor jedem E2E-Lauf `/`, `/dokument-editor` und `/lieferanten` mit einem rohen Browser **ganz ohne Routing** und wartet auf `networkidle` mit 90 s Timeout. Drei cdnjs-Zugriffe pro Lauf, und die Wartezeit auf ein fremdes CDN zählt gegen genau den Timeout, den das Aufwärmen entschärfen soll. Von allen Stellen ist das die mit der größten Flake-Wirkung.
**Loch 2 — der Vite-Proxy.** `vite.config.ts` leitet `/api` serverseitig auf `https://localhost:8080`. Das läuft in Node, nicht im Browser; `page.route` kann es nie sehen. Was am `**/api/**`-Stub einer Spec vorbeirutscht, geht an ein echtes Backend. Kein Internet-Leck, aber ein Loch in „vollständig gestubbt".
Dritte Zieladresse, die noch niemand auf dem Zettel hat: `AddressAutocomplete.tsx` ruft `nominatim.openstreetmap.org` und `photon.komoot.io` — jede Spec, die ein Adressfeld öffnet, tippt dorthin.
**Empfehlung für Task 10:** den Riegel nicht in jede Spec kopieren, sondern als **Auto-Fixture** in eine gemeinsame `e2e/hilfen/test.ts` (`base.extend`), die jede Spec statt `@playwright/test` importiert — dann kann niemand ihn vergessen. Dabei `context.route` statt `page.route` verwenden, das deckt zusätzlich Popups und neue Seiten. Und denselben Aufruf in `aufwaermen.ts`.

**C. Kosten des Catch-alls im Blick behalten.** `page.route('**/*')` schickt **jede** Anfrage durch den Node-Handler, auch die vielen hundert ES-Modul-Anfragen, die Vite pro Seitenaufruf ausliefert. Vorher wurden nicht passende Anfragen ohne Umweg über Node weitergereicht. Wenn die E2E-Laufzeit nach dem Ausrollen spürbar steigt, hilft ein URL-Prädikat als Matcher, das nur auf Nicht-localhost passt, statt eines Glob-Catch-alls. Vor dem Ausrollen auf alle Specs einmal messen — der Riegel soll Flakiness senken, nicht neue einbauen.

**D. Eine Kommentar-Aussage stimmt nicht.** In `lieferant-layout.spec.ts` steht, die Karte sei „im Screenshot weiterhin sichtbar, es geht nur nichts mehr wirklich raus". `route.abort()` auf das `<iframe>`-Dokument heißt aber: die Karte rendert **nicht**, übrig bleibt der graue `bg-slate-100`-Rahmen. Folgenlos für die Tests (keine Hilfsfunktion prüft Konsolenfehler), aber der nächste Leser wird in die Irre geführt. Der Design-Reviewer sollte bestätigen, dass der Screenshot `lieferant-detail-einwort-lang` so gewollt ist.

**E. `gap-3` ist nur an einer der fünf Karten abgesichert.** Nur `kunde-layout.spec.ts` fängt einen Rückfall auf `space-y-3` — und nur für `KundenKarte`. Für `ProjektCard`, `AnfrageCard`, `KundenProjektKarte` und `KundenAnfrageKarte` gibt es keine Zusicherung; ein Rückfall bliebe unbemerkt. Die neue Prüfung ist gut gebaut (zwei Karten einer Reihe, y-Position der ersten Meta-Zeile, 2 px Toleranz) und lässt sich eins zu eins in die Projekt- und Anfrage-Übersichtsspec kopieren. Für Task 9/10.

**F. Die vier Einwort-Fixtures sind unterschiedlich lang.** Projekt 77 Zeichen, Kunde 67, Lieferant 61. Nach den Messungen des Coding-Agenten reicht das beim Kunden nur bei 1440 zum roten Ausschlag. Kein Mangel — 1440 ist die Größe, um die es in diesem Vorhaben geht —, aber die Kunden-Wache wird still, sobald jemand die Kopfzeile verbreitert. Einheitlich mindestens so lang wie das Projekt-Wort, dann greifen alle vier in beiden Größen.

**G. `e2e/` wird von keinem Gate typgeprüft.** `tsc -b` deckt über `tsconfig.app.json`/`tsconfig.node.json` nur `src` und `vite.config.ts` ab; die Specs und `e2e/hilfen/` fallen durch. Ich habe `tsc --noEmit` von Hand über `e2e/*.ts` und `e2e/hilfen/*.ts` laufen lassen: **sauber, Exit 0**. Für Task 10: ein `tsconfig.e2e.json` als drittes Projekt-Reference, dann fängt der Build auch Spec-Fehler.

### Rezeptur-Treue final

Kopfzeile, Reiterleiste und Karten von Projekt, Anfrage, Kunde und Lieferant Zeile für Zeile verglichen. **Der äußere Kopf-Aufbau ist jetzt in allen vier identisch** — `flex flex-wrap items-start gap-4`, Titelblock `flex-1 min-w-[18rem]`, innerer `min-w-0`, `<h1>` mit `break-words min-w-0`, Kennzahlen je `min-w-[7rem]` ohne `flex-1`/`max-w`, Knopfblock `shrink-0 ml-auto flex flex-wrap items-start gap-2`. Auch die zwei letzten Abweichungen aus Runde 1 (Kunden-Knopfblock, `space-y-3` in den fünf Karten) sind zu.

Was bleibt — alles in `KundenKarte` (`Kundeneditor.tsx:865`), der einzigen Karte, die nie nach der gemeinsamen Rezeptur gebaut wurde:

| Abweichung | Bewertung |
| --- | --- |
| `Card` ohne `overflow-hidden` (die anderen fünf haben es) | **Der einzige mit Substanz** — er ist der Grund, warum Hinweis A sichtbar wird statt still geklippt zu werden |
| `h3` ohne `text-base` (die anderen fünf: `… line-clamp-2 text-base`) | kosmetisch |
| `p-4` sitzt an der `Card` statt am inneren Container | kosmetisch, gleiche Wirkung |
| Klassenreihenfolge im Kunden-Knopfblock (`flex flex-wrap items-start shrink-0 ml-auto gap-2`) | rein optisch im Quelltext, identisches CSS |

Die zwei Kennzahlen-Bauweisen (Trennerspalten bei Projekt/Anfrage, Kacheln bei Kunde/Lieferant) bleiben unterschiedlich, wie der Plan das unter „Bewusst nicht in diesem Vorhaben" festhält.

### Gates

- `npm run lint`: **0 Fehler, genau die 1 vorbestehende Warnung** (`BelegeKasseEditor.tsx:1204`, `react-hooks/exhaustive-deps`) — identisch zur Baseline.
- `npm run test`: **1081/1082 grün**. Ein Ausschlag in `src/components/LieferantDokumentModal.test.tsx:309` (`getAllByText('Sperre konnte nicht geholt werden …')` erwartet 2, bekommt 3). Datei liegt **nicht** im Diff. Isoliert nachgefahren: **19/19 grün**. Reiner Last-Effekt durch parallel laufende Agenten, wie in der Baseline beschrieben — kein Assertion-Fehler an der Nachbesserung.
- `npm run build`: grün (`tsc -b` + `vite build`). Output danach verworfen (`git checkout --` auf `src/main/resources/static/index.html`, die zwei neuen `assets/`-Dateien gelöscht), `git status` sauber.
- Kein `./mvnw`. `git diff --stat 9dd26f7e..HEAD`: 9 Dateien, **alle unter `react-pc-frontend/`** (4 × `src/pages/*.tsx`, 4 Specs, 1 Hilfsdatei). Kein `test.only`/`skip`/`fixme`, kein `toHaveScreenshot`, kein eigenes `setViewportSize`.
- DSGVO: unauffällig. Neue Fixtures sind erfundene Komposita, die neue E-Mail-Adresse nutzt `.example`.

### Auftrag an den Design-Reviewer

1. **Hinweis A im Browser prüfen:** `/kunden` mit einem Kunden, dessen `kundenEmails[0]` lang ist (60+ Zeichen ohne Leerzeichen). Erwartung nach meiner Lesart des CSS: die Zeile läuft aus der Karte heraus, statt umzubrechen. Bestätigt sich das, ist der Fix `<span className="min-w-0 break-words">` um den Text.
2. **Hinweis D:** ist der Screenshot `lieferant-detail-einwort-lang` mit grauem Kartenrahmen statt Karte so gewollt?
3. Voller E2E-Lauf über alle Specs in beiden Größen, wie gehabt.

### Aufräumen

Mutation vollständig zurückgenommen, Build-Output verworfen. `git diff` leer, `git status` sauber (bis auf diesen Log-Block), HEAD unverändert `56737c3a`. Kein Produktivcode angefasst, kein Playwright gestartet.

## Abschnitt 4 — Design-Review Runde 2 (Design-Reviewer)

Zeit: 2026-09-06T15:35:00Z
Branch: feature/layout-14-zoll @ 56737c3a (Merge Task 3b-Nacharbeit + Task 6-Nachbesserung), eigener Worktree `wt/layout-review-design`, detached HEAD
Diff geprüft: 9dd26f7e..HEAD (9 Dateien, 502 Zeilen)
Status: fertig
Ampel: 🟡 (mein 🔴 aus Runde 1 ist behoben und nachgemessen; ein neuer, vorbestehender Befund gleicher Bauart bleibt offen — nicht durch diese Nachbesserung entstanden)

### E2E — komplett, beide Größen, Standard-Worker

- Port-Check vorher: `netstat -ano | findstr :5219` leer, Port 5219 wie vorgegeben.
- `E2E_PORT=5219 npm run test:e2e`: **206 grün, 0 rot**, 2,9 min, "Running 206 tests using 4 workers"
  (Standard-Worker, kein `--workers=1`). Keine Flakes, kein Nachfahren nötig.
  Rechnung: 198 (Runde 1) + 8 neue (4 Testfälle × 2 Größen: Projekt-`<h1>`-Einwort,
  Projekt-Reiterleiste-Messung, Kunde-`<h1>`-Einwort, Lieferant-`<h1>`-Einwort) = 206.
- **Leere weiße Seite unter parallelen Workern: nicht mehr aufgetreten.** Weder im Volllauf
  noch in den Nachläufen dieser Sitzung (insgesamt 6 Playwright-Läufe mit Standard- bzw.
  2-Worker-Parallelität). Deckt sich mit der Vermutung aus Runde 1, dass die parallel ladenden
  Maps-Einbettungen der Auslöser waren — bewiesen ist es damit nicht, aber der Riegel hält.
- Die `[WebServer] http proxy error ECONNREFUSED`-Zeilen sind wie in allen Vorabschnitten der
  Vite-Proxy ohne Backend, kein Testfehler.
- Warnung aus Runde 1 hat sich sofort gerächt: mein erster Wegwerf-Messlauf ohne `--output`
  hat `test-results/` samt aller Design-Screenshots gelöscht. Danach die fünf Layout-Specs neu
  gefahren (30/30 grün) und alle weiteren Wegwerf-Läufe mit `--output=test-results-zz`.

### Befund 1 (mein 🔴 aus Runde 1) — behoben, selbst nachgemessen

Nicht den Berichten geglaubt, sondern eigene Wegwerf-Spec: pro Seite den Ist-Zustand gemessen
**und** zur Laufzeit `min-w-0` per `classList.remove` von der `<h1>` genommen und nochmal
gemessen. Damit ist belegt, dass genau diese eine Klasse trägt — nicht irgendein Nebeneffekt.
Überstand = rechte Kante `<h1>` minus rechte Kante Titelblock (`div.min-w-[18rem]`), zusätzlich
geprüft, welche Kennzahl-Beschriftungen die `<h1>` geometrisch überlappt.

| Seite | 1440 ohne `min-w-0` | 1440 mit | 1920 ohne `min-w-0` | 1920 mit |
| --- | --- | --- | --- | --- |
| Projekt | **583 px** über, verdeckt BRUTTO + NETTO | **0 px** | **765 px** über, verdeckt BRUTTO + NETTO + GEWINN | **0 px** |
| Anfrage | **411 px** über, verdeckt BRUTTO + NETTO | **0 px** | **187 px** über, verdeckt BRUTTO | **0 px** |
| Kunde | **60 px** über, verdeckt GESAMTUMSATZ | **0 px** | kein Überstand (Titelblock 1393 px breit) | 0 px |
| Lieferant | **272 px** über, verdeckt BESTELLUNGEN | **0 px** | **48 px** über, keine Überlappung | **0 px** |

Die `<h1>` ist ohne Fix in Projekt und Anfrage konstant 999 px breit — derselbe Wert wie in
Runde 1, unabhängig von der Fenstergröße. Mit `min-w-0` schrumpft sie exakt auf die verfügbare
Blockbreite (Projekt 1440: 416 px, 1920: 234 px). Meine Zahlen decken sich mit der gemeldeten
Tabelle; die kleinen Abweichungen bei Kunde (60 statt 77) und Lieferant (272/48 statt 280/56)
kommen aus meinen eigenen Fixture-Werten, nicht aus einem anderen Verhalten.

Im Bild geprüft (jeder Screenshot einzeln geöffnet, beide Größen): Titel bricht innerhalb seines
Blocks um (Projekt 1440 drei Zeilen, 1920 fünf Zeilen; Anfrage/Kunde/Lieferant zwei Zeilen), alle
Kennzahl-Kästen vollständig lesbar, Knöpfe rechts in der Kopf-Karte, Reiterleiste unberührt.

### Befund 2 (mein 🟡 Nr. 1, Trennlinien) — behoben, selbst nachgemessen

Kartenreihe mit **gemischt** langen und kurzen Titeln (Carport / langer Titel / Zaun), also genau
die Konstellation aus Runde 1. Gemessen: y der Trennlinie (`border-t`-Block mit `mt-auto`) je Karte,
plus Gegenprobe mit zur Laufzeit wieder aufgespieltem `space-y-3`.

| | jetzt (`gap-3`) | Gegenprobe (`space-y-3`) |
| --- | --- | --- |
| ProjektCard, 1440 | **642 / 642 / 642**, `margin-top` 24px / 0px / 24px | **618 / 642 / 618**, `margin-top` überall 12px |
| ProjektCard, 1920 | 642 / 642 / 642 / 642 | 618 / 642 / 618 / 642 |
| AnfrageCard, 1440 und 1920 | 642 / 642 / 642 | 618 / 642 / 618 |
| KundenKarte, 1440 und 1920 | Meta-Block 635 / 635 / 635 / 635 | — |

Die Gegenprobe reproduziert exakt die 618/642/618 aus Runde 1. `mt-auto` löst jetzt sauber auf
(24 px bei kurzem Titel, 0 px bei langem). Im Bild: die drei Auftragsnummern, Datumszeilen und
Beträge einer Reihe liegen auf einer Linie, das Auge muss nicht mehr zickzack laufen. Kartenhöhen
unverändert gleich (219 px bzw. 155 px). Ich hatte das in Runde 1 als „sieht man" beschrieben —
man sieht jetzt auch, dass es weg ist.

### Meine übrigen 🟡 aus Runde 1

2. **E-Mail-Kürzung Anfrage-Seitenbereich** — erledigt und scharf abgesichert: die Fixture trägt
   jetzt die lange Adresse, und der Komposita-Testfall läuft mit `keinHorizontalerUeberlauf` +
   `designPruefung(strengePruefungen: true)` darüber. Grün. Die zweite Stelle, die ich in Runde 1
   genannt hatte (`Kundeneditor.tsx`, E-Mail in der **Übersichtskarte**), ist ebenfalls auf
   `break-words` umgestellt. **Achtung:** Die Task-Blöcke im Log beschreiben diese Stelle als
   „Kontaktdaten der Detailseite" — das ist sie nicht, geändert wurde `KundenKarte` in der
   Übersicht (Z. 896–901). Die Kontaktdaten-Spalte der Kunden-Detailseite ist unangetastet
   geblieben, siehe Befund 4. Sachlich ist der Fix am richtigen Ort, nur die Beschreibung stimmt
   nicht.
3. **Kunden-Knopfblock** `flex flex-wrap items-start shrink-0 ml-auto gap-2` — erledigt, entspricht
   jetzt der Rezeptur. Heute unsichtbar (nur ein Knopf), als Zukunftssicherung richtig.
4. **„Bau Tagebuch" → „Tagebuch"** in beiden Reiterinhalten (`ProjektEditor.tsx`,
   `AnfrageEditor.tsx`) — erledigt. Damit heißt die Sache in Reiter und Überschrift überall gleich.
5. **Netz-Riegel** — erledigt und wirksam. `blockiereFremdeNetzwerkzugriffe(page)` steht in beiden
   Stub-Funktionen der Lieferanten-Spec **vor** dem `page.route('**/api/**')`; da Playwright die
   zuletzt registrierte Route zuerst fragt, sieht der spezifischere `/api`-Handler localhost-
   Anfragen weiterhin zuerst — die Reihenfolge ist richtig herum, das habe ich am Code geprüft,
   und die 206 grünen Tests belegen, dass die Stubs weiter greifen. Der Mitschnitt im Testfall
   verlangt aktiv mindestens einen Griff nach draußen und dass keine einzige fremde Antwort
   ankommt — eine Zusicherung, die nicht still grün werden kann.

### Befund 4 (NEU, vorbestehend — sollte vor Abschluss des Abschnitts weg) 🛑

**Die E-Mail-Adresse in der Kontaktdaten-Spalte der Kunden-Detailseite läuft aus dem Bild.**
`react-pc-frontend/src/pages/Kundeneditor.tsx:497` — der Link trägt nur
`className="font-medium text-rose-600 hover:underline"`: kein `break-words`, kein `block`,
kein `title`. Gemessen mit genau der E-Mail-Adresse, die das Projekt selbst als realistische
Fixture führt (`verwaltung.rechnungswesen@wohnungsbaugesellschaft-beispielstadt-nord-immobilienverwaltung.example`,
99 Zeichen, steht so in `projekt-detail-layout.spec.ts` und `anfrage-layout.spec.ts`):

| | 1440 | 1920 |
| --- | --- | --- |
| Überstand über den eigenen Kasten | **184 px** | **64 px** |
| `main.scrollWidth − main.clientWidth` | **127 px** | **7 px** |

Das ist der Zielwert Nr. 1 dieses ganzen Vorhabens (`main.scrollWidth − main.clientWidth === 0`
bei pc-14zoll) — verletzt. Auf dem Screenshot laufen beide Zeilen der Adresse sichtbar über den
rechten Fensterrand hinaus. Mit einer normalen Firmenadresse mittlerer Länge (48 Zeichen) passt
es; ab rund 60 Zeichen kippt es.

Warum das keiner gesehen hat: `keinHorizontalerUeberlauf` prüft `<main>` und **würde** das melden —
die Fixture `DUMMY_KUNDE` trägt aber `info@beispiel-bau.example` (25 Zeichen). Genau dasselbe
Muster wie der Anfrage-Befund aus Runde 1 (dort `kundenEmails: []`): der Wächter steht, es füttert
ihn nur niemand.

Einordnung, offen gelegt: **Das ist kein Rückschritt dieser Nachbesserung.** Die Zeile steht seit
dem Initial-Commit unverändert da (`git log -L 490,500 ...` zeigt nur `bee06ecd`). Ich habe sie in
Runde 1 nicht genannt — mein damaliger Punkt war die Übersichtskarte, und die ist gefixt. Deshalb
🟡 und nicht 🔴. Trotzdem gehört sie weg, bevor der Abschnitt zumacht, denn sie ist die letzte von
vier identischen Stellen: Projekt (Abschnitt 4 gefixt), Anfrage (Nachbesserung 1 gefixt),
Lieferant (Task 6 gefixt) — nur Kunde fehlt. Der Lieferant hat exakt dasselbe Markup und heißt
dort `className="font-medium text-rose-600 hover:underline break-words block"`. Ein
Ein-Klassen-Fix, plus eine lange E-Mail in `DUMMY_KUNDE`, damit
`designPruefung(strengePruefungen: true)` es künftig festhält.

### Dokumentierte Grenze: Projekt-Reiterleiste mit zweistelligen Zählern

Gemessen bestätigt (aus dem Volllauf): 1440 → Container 916 px, zwei Zeilen, Verteilung [6,1],
y-Werte 593 (sechsmal) und 635; 1920 → 1084 px, eine Zeile, alle sieben auf 627.
Selbst angeschaut (eigener Screenshot mit 34er Zählern, 1440): „Tagebuch (34)" steht allein in
Zeile zwei, alle sieben Reiter vollständig beschriftet, nichts abgeschnitten, kein stilles
Scrollen, der aktive Reiter bleibt klar markiert.

**Urteil: als dokumentierte Grenze reicht das, ich will es nicht anders.** Der Plan erlaubt den
zweizeiligen Umbruch bei 1440 ausdrücklich als Alternative zum strikten Einzeiler („flex-wrap …
aber nichts darf verschwinden"), und genau das passiert hier. Zweizeilig ist immer noch besser
als abgeschnitten. Kein Fix nötig.

### Hinweise (kein Blocker, für Task 9/10 oder später)

1. **Projekt-Kopfzeile bei 1920 schlechter als bei 1440.** Gemessen: der Titelblock ist bei 1440
   548 px breit, bei 1920 nur **366 px** — auf dem größeren Bildschirm bekommt der Titel weniger
   Platz und bricht in fünf statt drei Zeilen um, während rechts daneben Leerraum steht. Grund:
   bei 1920 passen die fünf Kennzahlen und beide Knöpfe in eine Flex-Zeile und nehmen die Breite
   weg; bei 1440 rutschen die Knöpfe in eine zweite Zeile und geben sie frei. Nichts wird verdeckt
   oder unlesbar, aber es liest sich verkehrt herum. Betrifft nur die Projekt-Kopfzeile (fünf
   Kennzahlen + zwei Knöpfe); Anfrage (944 px), Kunde (1393 px) und Lieferant (819 px) sind bei
   1920 unauffällig. Vorbestehend aus der Rezeptur, nicht durch `min-w-0` entstanden — die
   Blockbreite ist mit und ohne Fix identisch.
2. **Einsames Komma in der Kunden-Kopfzeile.** `Kundeneditor.tsx:310` rendert
   `<MapPin/> {strasse}, {plz} {ort}` ohne Bedingung — ohne Adresse steht dort nur ein Pin-Symbol
   und ein Komma. Auf `kunde-detail-langer-name--*.png` und in jedem Kunden-Screenshot zu sehen.
   Vorbestehend, kosmetisch.
3. **Zahlendreher in Code-Kommentaren.** `ProjektEditor.tsx` (Kommentar über der `<h1>`) und der
   Kopfkommentar von `lieferant-layout.spec.ts` schreiben „411px (1440) bzw. 187px (1920)
   Überstand beim Projekt-Editor". Das sind die Anfrage-Werte; der Projekt-Editor lief 583 px
   (1440) bzw. 765 px (1920) über. Nur Dokumentation, aber wer später danach sucht, misst nach
   und findet etwas anderes.
4. **Kein dauerhafter Testfall für die Trennlinien von ProjektCard und AnfrageCard.** Die neue
   Zusicherung („Meta-Block-Zeilen einer Reihe höchstens 2 px versetzt") steht nur in
   `kunde-layout.spec.ts`. Für ProjektCard und AnfrageCard habe ich den Fix per Wegwerf-Spec
   nachgemessen (Zahlen oben), festgehalten ist er dort nicht — ein Rückdreh auf `space-y-3`
   bliebe in diesen beiden Dateien grün. Vorschlag für Task 9/10: denselben Zwei-Karten-Vergleich
   in `projekt-uebersicht-layout.spec.ts` und `anfrage-layout.spec.ts` ergänzen.

### Angeschaute Screenshots

Alle einzeln mit dem Read-Tool geöffnet, jeweils in beiden Größen, mit den sechs Fragen je Bild.

Aus `test-results/design/` (neu bzw. verändert): `lieferant-detail-einwort-lang--pc-14zoll/-pc-monitor`
(neu), `anfrage-detail-kopf-komposita--*` (Fixture jetzt mit langer E-Mail),
`kunde-uebersicht-lange-namen--*`, `kunde-detail-langer-name--*`, `kunde-mini-karten-projekte--*`,
`projekt-uebersicht-lange-titel--*`, `anfragen-uebersicht-karten--*`.

Eigene Wegwerf-Screenshots (danach gelöscht): Kopfzeile mit Einwort-Titel für Projekt, Anfrage und
Kunde in beiden Größen (die vier Kopfzeilen aus dem Auftrag, für Lieferant reicht der Spec-Shot);
Kartenreihe mit gemischt langen/kurzen Titeln für Projekt, Anfrage und Kunde in beiden Größen;
Kunden-Kontaktdaten mit langer und mittlerer E-Mail (Befund 4); Projekt-Reiterleiste mit
zweistelligen Zählern.

### Aufräumen

Drei Wegwerf-Specs (`e2e/zz-review-messung.spec.ts`, `zz-review-email.spec.ts`,
`zz-review-reiter.spec.ts`), der Ordner `review-messung/` und `test-results-zz/` wieder gelöscht.
`git status` im Review-Worktree sauber, keine Produktivdatei angefasst.

## Abschnitt 5 — Task 8b (Coding-Agent)

Zeit: 2026-09-06T13:53:35Z
Branch: layout/task-8b-menueleiste
Commit(s): 1355091e
Status: fertig

Was gemacht wurde:
- `react-pc-frontend/src/components/layout/RibbonNav.tsx`: Anzeigename zurueck auf `max-w-[10rem] truncate` (statt `line-clamp-1`), `title` und `data-kuerzung-erlaubt` unveraendert. Kommentar darueber ersetzt: die alte Begruendung (angeblich kenne `design.ts` `data-kuerzung-erlaubt` im Ueberlauf-Check nicht) war seit Task 1b falsch und haette kuenftige Agenten von `truncate` abgehalten -- neuer Kommentar sagt nur noch, warum hier gekuerzt werden darf (voller Name im Nutzermenue-Panel darunter, zusaetzlich `title`) und was der Marker bedeutet. `2xl:max-w-none` ergaenzt (Befund des Design-Reviewers: bei 1920 rund 300px frei). `no-scrollbar` in der Menuepunkt-Zeile (Z. ~389) entfernt, gleiches Muster wie die Kategorie-Leiste eine Zeile darueber (Task 8/Spec C).
- `react-pc-frontend/e2e/menueleiste-layout.spec.ts`: zwei neue Zusicherungen -- `anzeigenameMasse()` prueft bei `pc-monitor` (1920) `scrollWidth <= clientWidth` des Anzeigename-Elements (per `[data-kuerzung-erlaubt]` gefunden, bei `pc-14zoll` bewusst nicht scharf, da dort die Kuerzung gewollt bleibt); `menuepunktZeileMasse()` prueft die Menuepunkt-Zeile auf `scrollWidth <= clientWidth` in beiden Groessen (Regressionswaechter fuer die `no-scrollbar`-Entfernung, nicht weil heute etwas ueberlaeuft). Beide in den ersten Testfall eingehaengt.
- Testgetrieben verifiziert: Baseline (unveraendert, `line-clamp-1`) lief 8/8 gruen -- die neue Anzeigename-Zusicherung war dort trivial gruen, weil `line-clamp-1` ohne `white-space:nowrap` normal umbricht (`scrollWidth == clientWidth == 160px` an beiden Groessen, gemessen). Erst nach dem Umstieg auf `truncate` (Punkt 1, noch ohne `2xl:max-w-none`) wurde die neue Zusicherung bei `pc-monitor` echt rot: Kasten 160px, Inhalt braucht 191px (31px Ueberstand) -- die uebrigen 7 Faelle blieben gruen. Nach `2xl:max-w-none` alle 8 wieder gruen, Anzeigename bei 1920 jetzt 191px/191px (ungekuerzt), bei 1440 weiterhin 160px Kasten gegen 191px Inhalt (gewollt gekuerzt, `data-kuerzung-erlaubt` greift). Menuepunkt-Zeile: `scrollWidth == clientWidth` an beiden Groessen (1440/1440, 1920/1920), Zeilenhoehe 102px -- unveraendert zur Messung des Design-Reviewers aus Abschnitt 2, `no-scrollbar` hat daran nichts geaendert (siehe Bedenken).

Bedenken / Abweichungen vom Plan:
- **Echter Befund in einer fremden Datei, nicht selbst repariert:** `react-pc-frontend/e2e/hilfen/design.ts`, Funktion `keinTextLaeuftUeber` (Z. 213-261). Anders als `keinHorizontalerUeberlauf` (von Task 1b um genau diese Ausnahme erweitert) hat `keinTextLaeuftUeber` **keine** Ausnahme fuer `data-kuerzung-erlaubt` und **keine** fuer reine Text-Kuerzung (`text-overflow: ellipsis`). Jedes tatsaechlich per `truncate` gekuerzte Blatt-Element hat zwangslaeufig `scrollWidth > clientWidth` auf sich selbst -- `keinTextLaeuftUeber` meldet das immer, unabhaengig vom Marker. Verifiziert: mit `truncate` auf dem Anzeigenamen schlugen **alle 8** Testfaelle fehl, sobald `designPruefung(..., { strengePruefungen: true })` lief (Fehler `keinTextLaeuftUeber`: „Friederike Beispiel-Musterfrau": 31px zu wenig Platz), bei **beiden** Groessen -- bei 1440 ist die Kuerzung ja dauerhaft gewollt, dort waere der Check strukturell nie gruen zu bekommen. Da `design.ts` nicht in meiner `Files`-Liste steht, wurde die Pruefung nicht angefasst. Stattdessen: alle vier `designPruefung()`-Aufrufe in `menueleiste-layout.spec.ts` laufen jetzt **ohne** `strengePruefungen`, jeder Testfall ruft `keinTextGekuerzt(page)` stattdessen einzeln auf (die kennt die Ausnahme korrekt) -- gleiche Abdeckung fuer den hier relevanten Fall (Dokumentenrechte/Mietabrechnung/alle-fuenf-Kategorien bleiben weiterhin scharf auf ungewollte Kuerzung geprueft), ohne den fremden Fehlalarm auszuloesen. `keinHorizontalerUeberlauf` und `keineUeberschneidungen` laufen unveraendert mit (Teil von `designPruefung` ohne Option). **Empfehlung an einen kuenftigen Task, der `design.ts` anfasst** (z.B. Abschnitt 6): `keinTextLaeuftUeber` um dieselbe Ausnahme wie `keinHorizontalerUeberlauf` ergaenzen (Marker ODER `text-overflow: ellipsis`/`-webkit-line-clamp`), sonst ist jede kuenftige `truncate`-Nutzung mit `strengePruefungen: true` strukturell nie gruen zu bekommen, auch mit korrektem Marker.
- Sonst keine Abweichungen: Global Constraints + Task 8b vollstaendig gelesen und wie beschrieben umgesetzt. `no-scrollbar`-Entfernung aendert die gemessenen Werte selbst nicht (die Klasse blendet nur die Scrollbar-Grafik aus, `overflow-x` bleibt `auto`) -- im Kommentar im Code und hier vermerkt, damit das nicht als Wirkungslosigkeit missverstanden wird.

Ergebnis der Gates:
- Port-Check: `netstat -ano | findstr :5215` vor dem ersten Lauf leer (keine `ABHÖREN`/`LISTENING`-Zeile), Port 5215 durchgehend frei verwendet.
- `E2E_PORT=5215 npx playwright test e2e/menueleiste-layout.spec.ts`: **8/8 gruen** (4 Testfaelle × pc-14zoll + pc-monitor), finaler Lauf nach allen vier Fixes.
- Vitest: nicht gefahren -- `grep -rl RibbonNav src --include="*.test.*"` findet weiterhin keine Testdatei, die `RibbonNav`/`RibbonNavigation` rendert (unveraendert zu Task 8).
- `npm run lint`: 0 Fehler, 1 vorbestehende Warnung (`BelegeKasseEditor.tsx:1204`, identisch zur Baseline).
- `npm run build`: gruen (inkl. `tsc -b`). Build-Output (`src/main/resources/static/index.html`, `assets/*`) vor dem Commit verworfen (`git checkout -- src/main/resources/static` + `git clean -f src/main/resources/static/assets`), `git status` danach sauber bis auf die zwei eigenen Dateien. Wegwerf-Messspec (`e2e/zz-messung-8b.spec.ts`, nur fuer die Vorher/Nachher-Zahlen oben) vor dem Commit geloescht.


## Abschnitt 5 — Task 9 (Coding-Agent)

Zeit: 2026-09-06T14:18:29Z
Branch: layout/task-9-uebersichten
Commit(s): 091968c8 (fix Kunde E-Mail), ba3ef414 (Trennlinien-Zusicherung Projekt/Anfrage), ed7d12b1 (neue Spec uebersichten-layout.spec.ts), 292ccbdf (Zahlendreher-Kommentare)
Status: fertig

### Teil A — zwei Fehler in Kundeneditor.tsx behoben

**1. Kontaktdaten-Spalte der Detailseite (Z. 497).** Rote Zusicherung zuerst: `DUMMY_KUNDE.kundenEmails` in `kunde-layout.spec.ts` auf dieselbe 97-Zeichen-Adresse umgestellt, die Projekt/Anfrage schon als `KUNDEN_EMAIL_LANG` fuehren (`verwaltung.rechnungswesen@wohnungsbaugesellschaft-beispielstadt-nord-immobilienverwaltung.example`), dazu zwei direkte Zusicherungen (E-Mail-Link-Ueberstand ueber seinen Kasten, `main`-Ueberstand). Beide liefen vor dem Fix rot mit exakt den vom Design-Reviewer gemessenen Werten:
   - Ueberstand E-Mail-Link ueber Kasten: **184px gemessen** (Review: 184px) bei 1440.
   - `main.scrollWidth - main.clientWidth`: nicht separat nachgemessen ueber die direkte Assertion (die schlug schon beim Kasten-Ueberstand fehl) -- Review nennt 127px bei 1440, 7px bei 1920.
   Fix (Muster wie beim Lieferanten, `LieferantenEditor.tsx:315`): das umschliessende `<div>` bekommt `min-w-0 flex-1`, der `<a>`-Link bekommt `break-words block`. Nach dem Fix: 0px Ueberstand, `main`-Ueberstand 0, beide Groessen gruen.

**2. Kunden-Uebersichtskarte (Z. 901).** Wichtiger Befund beim testgetriebenen Vorgehen: Die im Plan/Task-Text genannte lange E-Mail (`KUNDEN_EMAIL_LANG`, mit Bindestrichen im Domainteil) zeigt den Fehler an dieser Stelle **nicht** -- Bindestriche sind nach der Unicode-Zeilenumbruch-Regel ohnehin erlaubte Umbruchstellen, unabhaengig von `break-words`, und die Zeile bricht dort schon vorher um (im Browser nachgemessen: `<p>`.scrollWidth == clientWidth == 393px, kein Ueberstand -- mit einer scrollWidth/clientWidth-Debug-Sonde verifiziert, danach geloescht). Mit einer echten **bindestrichlosen** 90-Zeichen-Adresse (`buchhaltungsundverwaltungsabteilungfuerrechnungswesenundmahnwesen@beispielstadtnord.example`) reproduziert sich der beschriebene Fehler zuverlaessig:
   - Vorher: `<p>`.scrollWidth 665px gegen clientWidth 393px = **272px Ueberstand** (pc-14zoll).
   - Nachher (Text in `<span className="min-w-0 break-words">` gefasst): 0px Ueberstand.
   Wichtig fuer die Zusicherung selbst: `getByText(...).boundingBox()` findet nur die `<p>` (hat ein Element-Kind, das Mail-Icon -- kein "Blatt-Element" fuer `keinTextLaeuftUeber`), und deren eigene BoundingBox waechst NICHT mit dem ueberlaufenden Text-Node (der ist eine anonyme Flex-Box ohne eigenes DOM-Element). Eine Geometrie-Zusicherung "Zeile vs. Kartenrand" waere deshalb **falsch gruen** geblieben -- die Zusicherung misst stattdessen `scrollWidth - clientWidth` der Zeile selbst.

### Teil B — Übersichten-Abnahme

- **Neue Spec `e2e/uebersichten-layout.spec.ts`**: alle vier Übersichten (`/projekte`, `/anfragen`, `/kunden`, `/lieferanten`) in einem Durchlauf, je eine Fixture mit vier Eintraegen (ein kurzer Titel, zwei lange, ein mittlerer) in derselben Reihe bei 1440. Je Seite: (a) drei Karten je Reihe bei 1440 / vier bei 1920 (gemessen, gruen), (b) Karte mit kurzem und Karte mit langem Titel derselben Reihe haben gleiche Kartenhoehe UND ihre Trennlinie (`mt-auto`-Meta-Block) auf gleicher y-Position, (c) `keinHorizontalerUeberlauf` explizit, (d) `designPruefung(..., { strengePruefungen: true, primaerAktion })`. Alle acht Faelle (4 Seiten × 2 Groessen) gruen -- erwartet, da Abschnitt 3/4 alle vier Uebersichten schon umgebaut haben; per Mutationsprobe gegengeprueft (siehe unten).
- **Trennlinien-Zusicherung nachgezogen** in `projekt-uebersicht-layout.spec.ts` (neuer Testfall, gab es dort noch gar nicht) und `anfrage-layout.spec.ts` (zweite Anfrage im bestehenden Kurztitel-Testfall ergaenzt). Beide gruen.
- **Mutationsprobe:** `ProjektCard`s `gap-3` probeweise auf `space-y-3` zurueckgedreht (`ProjektEditor.tsx:4175`) -- sowohl die neue Zusicherung in `uebersichten-layout.spec.ts` als auch die nachgezogene in `projekt-uebersicht-layout.spec.ts` wurden korrekt rot (gemessener Versatz 44px bzw. Trennlinien y=618/662). Mutation vollstaendig zurueckgenommen, `git diff` auf die Datei zeigt danach nur noch den beabsichtigten Kommentar-Fix.
- **Zwei Zahlendreher korrigiert**: `ProjektEditor.tsx` (Kommentar ueber der `<h1>`) und der Kopf von `lieferant-layout.spec.ts` nannten 411px/187px fuer den Projekt-Editor-Ueberstand -- das sind die Anfrage-Werte. Auf die tatsaechlichen Projekt-Werte (583px/765px, Design-Review Runde 2) korrigiert.
- Kein `src/`-Code ausserhalb `Kundeneditor.tsx`/`ProjektEditor.tsx` (nur Kommentar) angefasst. Keine weiteren Befunde in `src/` gefunden, die ins Log muessten.

### Gate-Ergebnisse

- Port-Check: `netstat -ano | findstr :5209` vor dem Lauf leer, Port 5209 verwendet.
- `E2E_PORT=5209 npx playwright test e2e/uebersichten-layout.spec.ts e2e/kunde-layout.spec.ts e2e/projekt-uebersicht-layout.spec.ts e2e/anfrage-layout.spec.ts e2e/lieferant-layout.spec.ts`: **36/36 gruen** (18 Testfaelle × 2 Groessen).
- `npx vitest run src/pages/Kundeneditor.test.tsx src/pages/ProjektEditor.test.tsx`: **11/11 gruen**.
- `npm run lint`: 0 Fehler, 1 vorbestehende Warnung (`BelegeKasseEditor.tsx:1204`), identisch zur Baseline.
- `npm run build`: gruen (`tsc -b` + `vite build`). Build-Output verworfen (`git checkout -- src/main/resources/static/index.html`, `git clean -f src/main/resources/static/assets`), `git status` danach sauber.
- Kein `npm run test`, kein `npm run test:e2e` gefahren (Vorgabe: Coding-Agent faehrt nur die eigene Aenderung).

### Bedenken / Abweichungen vom Plan

- **Wichtigste Abweichung, offen fuer den Design-Reviewer:** Die im Task-Text vorgeschlagene Wiederverwendung der projektweiten `KUNDEN_EMAIL_LANG`-Adresse fuer den Uebersichtskarten-Testfall (Befund 2) reproduziert den beschriebenen Fehler an dieser Stelle **nicht** (siehe Teil A, Punkt 2) -- Bindestriche im Domainteil sind natuerliche Umbruchstellen unabhaengig von `break-words`/`min-w-0`. Der Testfall in `kunde-layout.spec.ts` nutzt deshalb eine eigene, bindestrichlose 90-Zeichen-Adresse (`EMAIL_OHNE_TRENNZEICHEN`), die den Fehler zuverlaessig zeigt. Die Fixture fuer Fix 1 (Kontaktdaten-Spalte, `DUMMY_KUNDE`) bleibt bei der projektweiten `KUNDEN_EMAIL_LANG` (reproduziert dort korrekt, siehe Zahlen oben) -- nur der Uebersichtskarten-Testfall weicht ab. Bitte im Design-Review gegenpruefen, ob diese Einordnung stimmt.
- `blockiereFremdeNetzwerkzugriffe()` in `uebersichten-layout.spec.ts` verwendet (Pflichtlektuere wies explizit darauf hin), obwohl keine der vier Uebersichtsseiten ein GoogleMapsEmbed rendert -- `index.html` laedt laut Abschnitt-4-Review trotzdem bei jeder Navigation `pdf.js` von cdnjs. Bewusst **nicht** in die beiden bestehenden Specs (`projekt-uebersicht-layout.spec.ts`, `anfrage-layout.spec.ts`) nachgezogen, da das flaechendeckende Ausrollen explizit Task 10 (Abschnitt 6) zugewiesen ist -- nur an der eigenen, neuen Datei angewendet.
- Keine weiteren Abweichungen. Beide Teile testgetrieben (rot vor dem Fix, gruen danach, mit Wegwerf-Debug-Sonden zur Ursachenklaerung bei Fix 2, alle vor dem Commit geloescht).


## Abschnitt 5 — Design-Review (Design-Reviewer)

Zeit: 2026-09-06T14:35:24Z
Branch: feature/layout-14-zoll (Review-Worktree `wt/layout-review-design`, detached auf dd7b70d9)
Commit(s): keine — read-only geprueft
Status: fertig
Ampel: 🔴

### Was geprueft wurde

- Voller E2E-Lauf, beide Groessen, Standard-Worker: `netstat -ano | findstr :5220` vor dem Lauf leer, `E2E_PORT=5220 npm run test:e2e` → **218/218 gruen** (4,0 min). Nach Abschnitt 4 waren es 206; die 12 neuen sind uebersichten-layout (4×2), der neue Kartenzeilen-Testfall in kunde-layout (1×2) und der neue Trennlinien-Testfall in projekt-uebersicht-layout (1×2).
- Alle 94 Screenshots aus `test-results/design/` liegen nach dem Lauf vor; die neuen und geaenderten mit dem Read-Tool angesehen (vier `uebersichten-*-gemischt`, `kunde-detail-langer-name`, vier `menueleiste-*`, `kunde-uebersicht-lange-namen`), je Bild und Groesse.
- Eigene Nachmessung unabhaengig von den Specs: separater Dev-Server auf Port 5331, eigenes Playwright-Skript ausserhalb des Worktrees (kein Quellcode angefasst), beide Groessen, alle fuenf Detailseiten und alle vier Uebersichten.

### Meine zwei offenen Befunde — beide behoben, selbst nachgemessen

1. **E-Mail auf der Kunden-Detailseite (mein Blocker aus Runde 2, Abschnitt 4).** Mit derselben 97-Zeichen-Adresse wie im Review gemessen, bei 1440: `main.scrollWidth − main.clientWidth = 0` (Zielwert erreicht, vorher 127px). Der E-Mail-Link ist 220px breit in einem 288px-Kasten und bleibt 12px innerhalb der Kastenkante (vorher 184px darueber hinaus); er bricht auf fuenf Zeilen um und bleibt vollstaendig lesbar. Gegenprobe mit einer bindestrichlosen 96-Zeichen-Adresse: ebenfalls `main = 0`, Link 220px im 288px-Kasten, vier Zeilen. Bei 1920 beide Varianten `main = 0`, drei Zeilen. Optisch im Screenshot bestaetigt.
2. **Anzeigename in der Menueleiste bei 1920 (mein Befund aus Abschnitt 2).** Gemessen: `scrollWidth 191px == clientWidth 191px`, `max-width: none` → nicht mehr gekuerzt; im Screenshot steht "Friederike Beispiel-Musterfrau" vollstaendig da. Bei 1440 weiterhin gewollt gekuerzt (Kasten 160px gegen 191px Inhalt, `data-kuerzung-erlaubt`), der volle Name steht im aufgeklappten Nutzermenue darunter — im Screenshot `menueleiste-nutzermenue-offen--pc-14zoll` verifiziert. Menuepunkt-Zeile ohne Ueberlauf in beiden Groessen (1440/1440 bzw. 1920/1920).

### Bindestrich-Befund des Coding-Agenten — bestaetigt, mit einer Praezisierung

Im Browser gegengeprueft, indem `min-w-0` an der Kartenzeile zur Laufzeit wieder entfernt wurde (Zustand vor dem Fix), ohne den Quellcode anzufassen:

- Bei 1440 verdeckt die Adresse **mit** Bindestrichen den Fehler tatsaechlich vollstaendig: 0px Ueberstand ohne `min-w-0`. Die bindestrichlose Adresse zeigt ihn: `scrollWidth − clientWidth = 272px`, Text 256px ueber die Kartenkante hinaus. Genau die vom Agenten genannte Zahl. Mit `min-w-0` (heutiger Stand): 0px, 17px innerhalb der Karte.
- **Praezisierung:** Bei 1920 verdeckt die Bindestrich-Adresse den Fehler nur teilweise — dort blieben ohne `min-w-0` immerhin 39px Ueberstand (bindestrichlos: 327px). Die Aussage "Bindestriche verdecken den Fehler" gilt fuer 1440 uneingeschraenkt, fuer 1920 nur abgeschwaecht. An der Einordnung und am Fix aendert das nichts.

**Stichprobe an den schon abgenommenen Stellen** (Auftragspunkt 4), jeweils mit einer bindestrichlosen 96-Zeichen-Adresse, beide Groessen: Projekt-Detailseite, Anfrage-Detailseite und Lieferanten-Detailseite laufen nicht ueber — `html = 0`, `main = 0`, E-Mail-Link innerhalb seines Kastens (Projekt/Anfrage 248px im 272px-Kasten, Lieferant 204px im 272px-Kasten, jeweils 12px Rand). Dort greift `break-words` auf einem Block-Element in einer `min-w-0`-Spalte auch ohne Bindestriche. **Kein neuer Befund an diesen drei Stellen.**

### Neuer Befund (blockierend): Mitarbeiter-Detailseite, Kontakt-Spalte

Beim Durchgang durch die fuenf Detailseiten gefunden — dieselbe Fehlerklasse, die dieses Vorhaben ueberall sonst beseitigt hat, an der einzigen der fuenf Detailseiten, deren Seitenspalte nie angefasst wurde.

`react-pc-frontend/src/pages/MitarbeiterEditor.tsx`, `SideInfo` (Z. 442-535): jede Zeile ist `<div className="flex items-center gap-3">` mit Icon plus einem nackten `<div>` — kein `min-w-0`, kein `flex-1`, kein `shrink-0` am Icon, und der Wert selbst (`<p className="text-sm font-medium">`) hat kein `break-words` und kein `title`. Betroffen sind u.a. die E-Mail-Zeile (Z. 471-476) und die Abteilungs-Zeile (Z. 458-463).

Gemessen bei 1440 mit einer realistischen 91-Zeichen-Mitarbeiter-E-Mail:

- `main.scrollWidth − main.clientWidth = 184px` — Zielwert Nr. 1 des Vorhabens, verletzt. (`html = 0`, der Ueberstand versteckt sich also still in `main` — genau das Muster aus Befund 1 der Ausgangs-Spec.)
- Die E-Mail ragt 248px ueber ihren Kasten hinaus und laeuft rechts aus dem Bildschirm; auf dem Screenshot ist sie mitten im Wort abgeschnitten und nicht mehr lesbar. Bei 1920 dieselbe Zeile 192px ueber den Kasten (`main` dort 0).
- Schon ohne E-Mail sichtbar: `Sonderaufgabenkoordinationsstellenverwaltung` in der Abteilungs-Zeile steht bei 1440 21px ueber der Kartenkante (bei 1920 passt es).

Warum das bisher niemand gesehen hat: `DUMMY_MITARBEITER` in `e2e/mitarbeiter-layout.spec.ts` setzt `email: null` und `strasse/plz/ort: null` — die Zeilen werden im Test nie mit echtem Inhalt gerendert. Und die automatischen Pruefungen greifen hier strukturell nicht: das `<p>` ist genauso breit wie sein Text (`scrollWidth == clientWidth`), also meldet `keinTextLaeuftUeber` nichts, und die Karte hat kein `overflow-x: hidden`, also meldet auch die generische Schleife von `keinHorizontalerUeberlauf` nichts. Nur der `main`-Zweig von `keinHorizontalerUeberlauf` wuerde anschlagen — aber erst, wenn eine Fixture eine lange Adresse setzt.

Einordnung: **vorbestehend, nicht von Abschnitt 5 verursacht.** Task 7 hatte die Seitenspalte ausdruecklich ausgeklammert ("Sonst nichts an dieser Datei — Kopf und Karten der Mitarbeiterseite sind nicht Teil der Spec"). Der Fix gehoert deshalb nicht in Abschnitt 5, sondern in einen eigenen kleinen Nachtrags-Task auf `MitarbeiterEditor.tsx` + `mitarbeiter-layout.spec.ts` — das Muster steht fertig in `LieferantenEditor.tsx:315` und jetzt `Kundeneditor.tsx:497`: umschliessendes `<div>` auf `min-w-0 flex-1`, Wert auf `break-words`, Icon auf `shrink-0`, und die Fixture um eine lange E-Mail plus eine Adresse erweitern.

### Hinweise (nicht blockierend)

- **Landmine fuer Abschnitt 6 / Task 10.** Der Coding-Agent von Task 8b hat den Fehlalarm in `keinTextLaeuftUeber` korrekt beschrieben; ich habe ihn nachgestellt: mein Nachbau der Pruefung meldet den gekuerzten Anzeigenamen bei 1440 auf jeder Seite mit 31px. Task 8b weicht dem aus, indem alle vier `designPruefung()`-Aufrufe in `menueleiste-layout.spec.ts` jetzt ohne `strengePruefungen` laufen. Dreht Task 10 den Standard wie geplant auf `true`, laufen genau diese vier Faelle bei pc-14zoll wieder rot — strukturell unbehebbar, weil die Kuerzung dort gewollt ist. Task 10 muss `keinTextLaeuftUeber` also zwingend um dieselbe Ausnahme erweitern wie `keinHorizontalerUeberlauf` (Marker `data-kuerzung-erlaubt` ODER `text-overflow: ellipsis`/`-webkit-line-clamp`), bevor der Standard umgestellt wird. `design.ts` steht in Task 10s Files-Liste, das passt zusammen.
- Der Abdeckungsverlust in `menueleiste-layout.spec.ts` (vier mal `strengePruefungen` weg) ist vertretbar: `keinTextGekuerzt` wird stattdessen einzeln aufgerufen, `keinHorizontalerUeberlauf` und `keineUeberschneidungen` laufen unveraendert mit, und zwei neue Zusicherungen sind dazugekommen. Netto ist die Menueleiste besser abgesichert als vorher, nicht schlechter.
- Kartentitel in den Uebersichten werden bei 1920 haeufiger per `line-clamp-2` gekuerzt als bei 1440 (vier statt drei Spalten, also schmalere Karten): auf `uebersichten-projekte-gemischt--pc-monitor` sind zwei von vier Titeln mit "…" abgeschnitten, bei 1440 nur einer. Das ist die bewusste, markierte Ausnahme (`data-kuerzung-erlaubt`, voller Text im `title`), der Kundenname darunter bleibt vollstaendig — kein Befund, nur der Vollstaendigkeit halber notiert.
- Ausserhalb dieses Vorhabens gesehen, nur zur Kenntnis: `FirmaEditor.tsx:995` rendert eine E-Mail als nacktes `<p className="text-sm text-slate-500">{sb.email}</p>` ohne `break-words`. Nicht geprueft, nicht Teil der fuenf Detailseiten.

### Gesamteindruck: fuenf Detailseiten und vier Uebersichten

Beide Groessen komplett durchgegangen, gemessen und angesehen.

- **Vier Uebersichten** (Projekte, Anfragen, Kunden, Lieferanten): sauber. Drei Karten je Reihe bei 1440, vier bei 1920 (gemessen: `[3,1]` bzw. `[4]`). Karten einer Reihe gleich hoch, Trennlinien auf gleicher Hoehe — `mt-auto` greift jetzt ueberall, im Screenshot deutlich zu sehen. Kein Ueberlauf, keine ungewollte Kuerzung, Primaeraktion ("Neues Projekt" usw.) ohne Scrollen sichtbar.
- **Projekt, Anfrage, Kunde, Lieferant (Detail)**: sauber in beiden Groessen. Titel brechen um statt zu ueberdecken, Kennzahlen (BRUTTO/NETTO/GEWINN) bleiben frei, Reiterleisten einzeilig, Seitenspalte bricht sauber. `html = 0`, `main = 0` ueberall, auch mit bindestrichlosen Langadressen.
- **Mitarbeiter (Detail)**: der einzige Ausreisser, siehe Befund oben.

Nichts ist ueberlagert, nichts verrutscht. Abgeschnitten ist genau eine Stelle: die Kontakt-Spalte der Mitarbeiterseite.

## Abschnitt 5 — Code-Review (Code-Reviewer)

Zeit: 2026-09-06T14:41:00Z
Branch: feature/layout-14-zoll (HEAD dd7b70d9, Diff 81ca1c59..HEAD)
Commit(s): geprueft, nichts committet (read-only Review)
Status: fertig
Ampel: 🟡

### Gates

- `npm run lint`: 0 Fehler, genau 1 bekannte Warnung (`BelegeKasseEditor.tsx:1204`, `react-hooks/exhaustive-deps`). Wie Baseline.
- `npm run test` (vitest, volle Suite): 1081/1082 gruen, 87/88 Dateien. **1 Fehlschlag, vorbestehend und nicht aus diesem Diff:** `src/components/LieferantDokumentModal.test.tsx:309` — `getAllByText('Sperre konnte nicht geholt ...')` erwartet 2, bekommt 3. Reproduzierbar auch beim Einzellauf der Datei (kein Last-Timeout). Die Datei importiert ausschliesslich `LieferantDokumentModal`, `ToastProvider`, `ConfirmProvider`, `types` — keine der drei in Abschnitt 5 geaenderten `src/`-Dateien (`RibbonNav.tsx`, `Kundeneditor.tsx`, `ProjektEditor.tsx`) liegt in diesem Abhaengigkeitsbaum. Daher nicht als 🔴 gewertet, aber als eigener Befund fuer einen kuenftigen Task vermerkt.
- `npm run build`: gruen (`tsc -b` + `vite build`, 1m01s). Build-Output nach `src/main/resources/static/` verworfen (`git checkout -- index.html`, erzeugte `assets/index-*.css|js` geloescht), `git status` danach sauber.
- `git diff --stat 81ca1c59..HEAD`: 9 Dateien, 714 Zeilen, **alle unterhalb `react-pc-frontend/`**. Nichts ausserhalb geaendert.

### Bindestrich-Befund — bestaetigt

Die Einordnung des Task-9-Agenten stimmt und ist rechnerisch konsistent mit seinen eigenen Browser-Messungen.

- `KUNDEN_EMAIL_LANG` = `verwaltung.rechnungswesen@wohnungsbaugesellschaft-beispielstadt-nord-immobilienverwaltung.example`. Nach UAX #14 sind `.` (Klasse IS, Regel LB29: IS × AL) und `@` (Klasse AL) **keine** Umbruchstellen, `-` (Klasse HY) dagegen schon. Laengstes unteilbares Stueck ist damit `verwaltung.rechnungswesen@wohnungsbaugesellschaft-` = 50 Zeichen, nicht die vollen 97. Daraus folgt eine Mindestinhaltsbreite von rund 390px.
- **Uebersichtskarte:** `<p>` clientWidth 393px (vom Task-9-Agenten gemessen). 390px < 393px, kein Ueberlauf. Der Fehler ist dort mit dieser Adresse tatsaechlich nicht reproduzierbar; die eigene bindestrichlose `EMAIL_OHNE_TRENNZEICHEN` (91 Zeichen, ein einziges unteilbares Stueck) ist die richtige Wahl. **Achtung: Messerschneide.** 390 gegen 393px sind 3px Reserve — bei anderer Schriftmetrik oder anderem Kartenraster kippt das. Die Entscheidung ist richtig, die Begruendung "Bindestriche brechen ohnehin" traegt aber nur, weil die Karte breit genug ist, nicht grundsaetzlich.
- **Kontaktdaten-Spalte der Detailseite:** rechnerisch rund 226px verfuegbar (Sidebar `minmax(0,1fr)` aus `grid-cols-[minmax(0,3fr)_minmax(0,1fr)]` bei 1440, also ~342px, minus `p-6` der Card, minus `p-3` der Reihe, minus Icon-Block und `gap-3`). 390px Mindestbreite gegen 226px Kasten = rund 164px plus 12px Padding — die gemeldeten **184px** passen. Die Fixture haelt den Fehler dort also wirklich fest.
- **Weitere Stellen, an denen Bindestriche etwas verdecken koennten — gepruefte Antwort: nein, aber zwei Abdeckungsluecken.**
  - `ProjektEditor.tsx:3357` und `AnfrageEditor.tsx:1478`: der `<a>` liegt dort in einem schlichten `<div className="p-3 bg-slate-50 rounded-lg">`, **keinem** Flex-Container. `min-width: auto` gilt nur fuer Flex-/Grid-Items — `break-words` allein reicht dort tatsaechlich. Kein verdeckter Fehler.
  - `LieferantenEditor.tsx:315/324`: hat `min-w-0 flex-1` + `break-words block` bereits, Code ist korrekt. Aber `lieferant-layout.spec.ts:94` setzt `kundenEmails: ['bestellung@beispiel-stahl.example']` (33 Zeichen) — der lange Fall wird dort nie gefahren. Reine Abdeckungsluecke, kein Fehler.
  - `e2e/mitarbeiter-layout.spec.ts:70` setzt `email: null` — siehe naechster Abschnitt.

### Alle E-Mail-Stellen umbruchfaehig? — vier von fuenf Detailseiten, alle vier Uebersichtskarten; **die fuenfte fehlt**

Vier `mailto:`-Stellen im gesamten `src/`:

1. `ProjektEditor.tsx:3357` — `block ... break-words`, Block-Kontext, in Ordnung.
2. `AnfrageEditor.tsx:1478` — `block ... break-words`, Block-Kontext, in Ordnung.
3. `LieferantenEditor.tsx:324` — `break-words block`, Elterndiv `min-w-0 flex-1` (Z. 315), in Ordnung.
4. `Kundeneditor.tsx:509` — jetzt `break-words block`, Elterndiv `min-w-0 flex-1`, mit Task 9 in Ordnung.

Uebersichtskarten: nur die **Kundenkarte** zeigt ueberhaupt eine E-Mail (`Kundeneditor.tsx:921`, jetzt `<span className="min-w-0 break-words">`, in Ordnung). `ProjektCard`, `AnfrageCard` und `LieferantenKarte` rendern **keine** E-Mail — die `<Mail>`-Icons dort gehoeren zu `FreigabeBadge` bzw. zum Bearbeiten-Modal. Die vier Uebersichtskarten sind damit vollstaendig.

**Die gesuchte fuenfte Stelle: `src/pages/MitarbeiterEditor.tsx:474-476`.** Der Plan nennt fuenf Detailseiten (Projekt, Anfrage, Kunde, Lieferant, **Mitarbeiter**, Plan Z. 245). Die Mitarbeiter-Detailseite zeigt die E-Mail im Kontaktblock in einer `flex items-center gap-3`-Reihe, deren Kind-`<div>` **kein** `min-w-0` traegt und deren `<p className="text-sm font-medium">{email}</p>` **kein** `break-words` hat. Das ist exakt das Muster, das Task 9 in `Kundeneditor.tsx:497` gerade repariert hat. Unentdeckt aus demselben Grund wie beim Kunden: `e2e/mitarbeiter-layout.spec.ts:70` setzt `email: null`, die Zeile rendert nur `-`. Damit ist es viermal derselbe Fehler an einer neuen Stelle plus ein fuenfter, noch offener. Gleiches gilt in derselben Spalte fuer `abteilungNames`, `strasse`/`ort` und die Telefonfelder. **Empfehlung: eigener Task in Abschnitt 6, nicht als Nachbesserung an Task 9 anhaengen** (fremde Datei, eigene Spec noetig).

Nebenbefund gleicher Art, geringere Prioritaet: In `Kundeneditor.tsx` haben die vier Nachbarreihen der reparierten E-Mail-Reihe (Ansprechpartner, Telefon, Mobiltelefon, Zahlungsziel) weiterhin ein `<div>` ohne `min-w-0`. Sie stehen aber jeweils in einem **eigenen** Flex-Container, `min-w-0 flex-1` an der E-Mail-Reihe wirkt sich also nicht auf sie aus — die Frage "bricht das etwas anderes in derselben Spalte?" ist mit **nein** zu beantworten. Der Ansprechpartner ist Freitext und kann denselben Ueberlauf ausloesen; heute ohne Fixture, die das zeigt.

### Die neue Spec `uebersichten-layout.spec.ts` — haelt sie, was sie verspricht? Teilweise

Struktur, Stubs, Selektoren und Rasterzusicherungen sind sauber:

- Alle vier Uebersichten nutzen `grid-cols-1 md:grid-cols-2 lg:grid-cols-3 2xl:grid-cols-4`, also 3 Spalten bei 1440 und 4 bei 1920 — `erwarteteSpalten` stimmt fuer alle vier.
- `kartenBox()` (`ancestor::div[... " shadow-sm " ...][1]`) trifft in allen vier Faellen die `<Card>` (`Card.tsx` setzt `shadow-sm` in der Basisklasse; `hover:shadow-md` matcht den Ausdruck korrekt nicht). Kein verschachteltes `shadow-sm` innerhalb der Karten oberhalb der gesuchten Elemente.
- `metaZeile()` (`ancestor::div[... " mt-auto " ...][1]`) trifft in allen vier Karten den Meta-Block. Ueberschriften (`h3`), Seitentitel und Primaeraktionen existieren alle so im Code.
- MIX[0] und MIX[1] liegen bei 3 wie bei 4 Spalten immer in derselben Reihe.

Zwei Einschraenkungen:

1. **Die Kartenhoehen-Zusicherung ist ohne Aussagekraft.** Die `<Card>`s sind Grid-Items; ein CSS-Grid streckt Items einer Reihe per `align-items: stretch` ohnehin auf gleiche Hoehe. `Math.abs(hoeheA - hoeheB) <= 2` ist damit strukturell immer gruen, egal was `mt-auto` macht. Tragend ist allein die y-Position der Trennlinie.
2. **Die Trennlinien-Zusicherung greift nur in drei der vier Uebersichten.** Sie vergleicht MIX[0] gegen MIX[1] und schlaegt nur an, wenn die beiden Karten unterschiedlich hohe Kopfbloecke haben — praktisch: unterschiedliche Titel-Zeilenzahl. Kartenbreite bei 1440: `main` hat `px-8`, also 1376px, drei Spalten mit `gap-4` ergibt 448px je Karte, minus `p-4` gleich **416px** Inhaltsbreite (deckt sich mit den 393px, die der Task-9-Agent fuer die `<p>`-Zeile inklusive Icon gemessen hat).
   - Projekte: `Carport` (7 Z.) gegen 88 Zeichen, 1 gegen 2 Zeilen, greift.
   - Anfragen: `Zaun` (4 Z.) gegen 88 Zeichen, 1 gegen 2 Zeilen, greift.
   - Kunden: `Meier` (5 Z.) gegen 69 Zeichen, 1 gegen 2 Zeilen, greift.
   - **Lieferanten: `Stahlbau Nord` (13 Z.) gegen `Stahlhandel Beispiel GmbH und Co. KG` (36 Z.).** 36 Zeichen bei `text-base font-semibold` sind rund 300px — passen locker in 416px, also **beide Titel einzeilig**. Beide Kopfbloecke gleich hoch, beide Trennlinien auf gleicher y-Position, mit `mt-auto` genauso wie ohne. Die Mutation `gap-3` zu `space-y-3` an `LieferantenKarte` wuerde diesen Testfall **nicht** rot drehen.
   Verschaerfend: auch MIX[2] (51 Z.) und MIX[3] (50 Z.) liegen mit rund 400-425px hart an der 416px-Grenze — die Lieferanten-Fixture erfuellt ihre eigene Praemisse "gemischt lange und kurze Titel" nicht und ist zusaetzlich anfaellig fuer Wackler zwischen den beiden Groessen. `lieferant-layout.spec.ts` hat **gar keinen** Kurztitel-/Trennlinien-Testfall, die Luecke wird also nirgends aufgefangen. Empfehlung: `LIEFERANTEN_MIX[1].lieferantenname` auf 70+ Zeichen anheben, wie bei den anderen drei Fixtures.

Die vom Task-9-Agenten gemeldete Mutationsprobe (`ProjektCard` `gap-3` zu `space-y-3`, 44px Versatz) ist plausibel und deckt Projekte ab — sie sagt aber nichts ueber die anderen drei Karten aus, weil jede Karte ihre eigene Klassenzeile hat.

Weiterer Punkt: `tsconfig.app.json` hat `"include": ["src"]` — `npm run build` (`tsc -b`) typprueft `e2e/` **nicht**. Die 348 Zeilen neue Spec sind damit nur durch den Playwright-Lauf des Autors abgesichert, nicht durch ein Gate. Beim Durchlesen keine Typ- oder Selektorfehler gefunden.

### Mutationsproben je Aenderung (aus dem Spec-Code abgeleitet, kein Browserlauf)

| Aenderung | greifende Zusicherung |
|---|---|
| `Kundeneditor.tsx:497` `min-w-0` entfernt | `kunde-layout.spec.ts`, Kopfzeilen-Testfall: `emailUeberstand <= 2` (184px vorher) und `mainUeberstand <= 0` |
| `Kundeneditor.tsx:497` `flex-1` entfernt | **keine** — `flex-1` ist neben `min-w-0` fuer das Ergebnis wirkungslos (das div ist letztes Item der Reihe, ohne `flex-1` schrumpft es genauso auf die verfuegbare Breite). Ungetestet, harmlos, im Kommentar nicht erwaehnt |
| `Kundeneditor.tsx:509` `break-words` entfernt | `keinTextLaeuftUeber` in `designPruefung({ strengePruefungen: true })` desselben Testfalls (`<a>` ist Blatt-Element). **Nicht** die Geometrie-Zusicherung: der `<a>`-Kasten bleibt bei Elternbreite stehen, der Text laeuft unsichtbar drueber |
| `Kundeneditor.tsx:509` `block` entfernt | **keine** — der `<a>` ist Flex-Item von `div.flex.flex-col` und damit ohnehin blockifiziert. Redundant |
| `Kundeneditor.tsx:921` `<span>` aufgeloest, `min-w-0` oder `break-words` entfernt | `kunde-layout.spec.ts`, "lange E-Mail in der Kartenzeile": `scrollWidth - clientWidth <= 2` (272px vorher). Alle drei Varianten greifen |
| `ProjektCard` `gap-3` zu `space-y-3` | `uebersichten-layout.spec.ts` (Projekte) und `projekt-uebersicht-layout.spec.ts` (neuer Testfall) — vom Autor real nachgewiesen |
| `AnfrageCard` `gap-3` zu `space-y-3` | `uebersichten-layout.spec.ts` (Anfragen) und `anfrage-layout.spec.ts` (erweiterter Testfall) |
| `KundenKarte` `gap-3` zu `space-y-3` | `uebersichten-layout.spec.ts` (Kunden) und der bestehende Testfall in `kunde-layout.spec.ts` |
| `LieferantenKarte` `gap-3` zu `space-y-3` | **keine** — siehe Abschnitt oben |
| `RibbonNav` `2xl:max-w-none` entfernt | `menueleiste-layout.spec.ts`, `pc-monitor`-Zweig: `anzeigenameMasse` 191px Inhalt gegen 160px Kasten — vom Autor real nachgewiesen |
| `RibbonNav` `truncate` zurueck auf `line-clamp-1` | **keine** — bei 1920 ist der Name in beiden Varianten ungekuerzt (191/191), bei 1440 greift `data-kuerzung-erlaubt` in beiden Faellen. Kein Test unterscheidet die Kuerzungsarten |
| `RibbonNav` `no-scrollbar` wieder eingesetzt | **keine** — der Autor hat selbst gemessen, dass sich die Werte nicht aendern. `menuepunktZeileMasse` ist Waechter fuer kuenftigen Ueberlauf, nicht fuer diese Aenderung |

### Task 8b — Ausweg tragfaehig, aber teurer als noetig

Die Analyse stimmt in der Sache. Nachgelesen in `e2e/hilfen/design.ts`:

- `keinHorizontalerUeberlauf` (Z. 163-172) nimmt `truncate` **zweifach** aus: `hatKuerzungsMarker` (Vorfahren-Suche nach `data-kuerzung-erlaubt`) und `istReineTextKuerzung` (`text-overflow: ellipsis` oder `-webkit-line-clamp`). Task 1b hat das korrekt ergaenzt. `truncate` laeuft dort sauber durch — Frage 6 des Auftrags: **ja**.
- `keinTextGekuerzt` (Z. 282) kennt `data-kuerzung-erlaubt` ebenfalls. Kein Problem.
- `keinTextLaeuftUeber` (Z. 213-261) kennt **weder** den Marker **noch** die Text-Kuerzungs-Ausnahme und prueft blind `scrollWidth > clientWidth + 2` auf jedem Blatt-Element mit Text. Ein tatsaechlich gekuerztes `truncate`-Element erfuellt das per Definition. Der Konflikt ist real, der Ausweg funktioniert.

**Die Luecke, die dabei entsteht:** `strengePruefungen` schaltet **zwei** Pruefungen scharf. Nachgezogen wurde nur `keinTextGekuerzt`. `keinTextLaeuftUeber` faellt damit in allen acht Menueleisten-Laeufen komplett weg — auch fuer jeden anderen ueberlaufenden Text auf der Seite. Das ist mehr als noetig: die Log-Aussage "mit `truncate` schlugen alle 8 Testfaelle fehl" wurde am Zwischenstand **ohne** `2xl:max-w-none` gemessen. Mit dem endgueltigen Code ist der Anzeigename bei 1920 ungekuerzt (191/191, vom Autor selbst gemessen) — dort haette `keinTextLaeuftUeber` nicht angeschlagen. Der Konflikt betrifft nur die vier `pc-14zoll`-Faelle; `strengePruefungen: testInfo.project.name === 'pc-monitor'` haette die Abdeckung bei 1920 erhalten. Kein Blocker, aber vermeidbarer Verlust. Der Eintrag als Voraussetzung fuer Task 10 ist richtig und die vorgeschlagene Reparatur ist die saubere Loesung.

### `2xl:max-w-none` — richtige Grenze? Ungetestetes Band 1536-1919px

Bei 1440 bleibt der Name auf `max-w-[10rem]` gekuerzt — richtig, denn die Kategorie-Leiste ist dort bereits knapp (Task 8 musste `gap-8` zu `gap-4` und `px-4` zu `px-3` opfern). Bei 1440 kuerzt es also **nicht** unnoetig.

Die Grenze selbst ist schwaecher begruendet, als sie aussieht: `2xl` greift ab **1536px**, die Rechtfertigung ("rund 300px frei") wurde bei **1920px** gemessen. Zwischen 1536 und 1919 gibt es kein Playwright-Projekt. Bei 1536 sind gegenueber 1920 rund 384px weniger da — der ungekuerzte Anzeigename (im Test 191px, im Echtbetrieb ein unbegrenztes Freitextfeld) frisst dort Platz, den die `flex-1`-Kategorie-Leiste braucht. Weil diese Leiste `overflow-x: auto` (nicht `hidden`) hat, sieht `keinHorizontalerUeberlauf` sie gar nicht — nur `kategorieLeisteMasse` wuerde es merken, und die laeuft nur bei 1440 und 1920. Ein Ueberlauf bei 1536-1700 bliebe unbemerkt. Kein Fehler-Nachweis, aber ein ungedecktes Risiko.

### Weitere Hinweise (alle 🟡)

- **Neue Tests ohne `blockiereFremdeNetzwerkzugriffe`.** Der neue Testfall in `kunde-layout.spec.ts` ("lange E-Mail in der Kartenzeile") und der neue in `projekt-uebersicht-layout.spec.ts` stubben nur `**/api/**`. `index.html` laedt bei jeder Navigation `pdf.js` von cdnjs — diese beiden **neu geschriebenen** Testfaelle machen also echte Netzzugriffe nach draussen. Die neue Spec `uebersichten-layout.spec.ts` macht es richtig. Das flaechendeckende Nachziehen gehoert zu Task 10, aber neu geschriebene Tests sollten es von Anfang an mitbringen.
- **`LieferantenKarte` hat `truncate`/`line-clamp-2` ohne `data-kuerzung-erlaubt`** an mehreren Stellen (`aliasName`, `ort`, `adresse`, `kontakt`, `vertreter`, Z. 838-858). Mit den kurzen Werten der neuen Fixture kuerzt heute nichts, `keinTextGekuerzt` bleibt gruen. Sobald Task 10 den scharfen Standard setzt oder jemand realistischere Fixtures nutzt, wird das rot. Vorbestehend, ausserhalb des Diffs.
- **Kommentar und Code weichen leicht ab:** der Kommentar ueber `Kundeneditor.tsx:497` begruendet nur `min-w-0`, hinzugefuegt wurde `min-w-0 flex-1`. Kleinigkeit, aber die Rezeptur wird von kuenftigen Tasks abgeschrieben.
- **DSGVO/Sicherheit:** unauffaellig. Alle Fixtures sind Fantasienamen mit `.example`-Domains, keine echten Personen- oder Firmendaten, keine Zugangsdaten, keine neuen Netzwerkziele ausser dem oben genannten cdnjs-Nebeneffekt. Die Zahlendreher-Korrekturen (583px/765px statt 411px/187px) decken sich mit den Werten aus dem Design-Review Runde 2.

### Auftrag an den Design-Reviewer

1. `LieferantenKarte`: `gap-3` zu `space-y-3` mutieren und `uebersichten-layout.spec.ts -g Lieferanten` fahren. Erwartung laut Analyse: **bleibt gruen** = Luecke bestaetigt. Dann Titel-Zeilenzahl von `LIEFERANTEN_MIX[1..3]` bei 1440 und 1920 nachmessen.
2. `menueleiste-layout.spec.ts` einmal mit `strengePruefungen: testInfo.project.name === 'pc-monitor'` fahren: bleibt `pc-monitor` gruen? Dann war der Abdeckungsverlust bei 1920 vermeidbar.
3. Viewport 1536 und 1650 von Hand: Anzeigename ungekuerzt (`2xl:max-w-none`) — passt die Kategorie-Leiste noch (`scrollWidth <= clientWidth`)? Zusaetzlich mit einem deutlich laengeren Anzeigenamen als `Friederike Beispiel-Musterfrau`.
4. `Kundeneditor.tsx:509`: nur `break-words` entfernen (`min-w-0 flex-1` stehen lassen) — muss ueber `keinTextLaeuftUeber` rot werden, nicht ueber die Geometrie-Zusicherung.
5. `Kundeneditor.tsx:497`: nur `flex-1` entfernen — Erwartung: bleibt gruen (bestaetigt, dass es wirkungslos ist).
6. `MitarbeiterEditor.tsx:474-476` mit einer langen, bindestrichlosen E-Mail bei 1440 im Browser messen: Ueberstand ueber den eigenen Kasten und `main.scrollWidth - main.clientWidth`. Belegt die fuenfte Stelle mit Zahlen fuer den Folge-Task.

## Abschnitt 5 — Abnahme und Befund zum roten Unit-Test (Orchestrator)

Zeit: 2026-09-06T18:20:00Z

**Abschnitt 5 abgenommen.** Code-Review 🟡, Design-Review 🔴 — der rote Befund
betrifft aber `MitarbeiterEditor.tsx`, eine Datei, die dieser Abschnitt nicht
angefasst hat, und der Design-Reviewer selbst schreibt, der Fix gehöre in einen
eigenen Task. Beide Reviewer haben ihn unabhängig gefunden (Code-Review Hinweis 1,
Design-Review 🛑). Entscheidung des Orchestrators: Abschnitt 5 ist auf seine
eigene Arbeit hin abgenommen; der Befund wird **Task 7b in Abschnitt 6** und muss
dort vom Design-Reviewer nachgeprüft werden. Beleg für die Abnahme: alle Befunde,
die dieser Abschnitt beheben sollte, sind mit Zahlen belegt behoben (E-Mail
Kunden-Detailseite 127 px `main`-Überlauf → 0; Übersichtskarte 272 px → 0;
Anzeigename bei 1920 nicht mehr gekürzt), 218 E2E-Tests grün.

**Roter Unit-Test ist vorbestehend — nachgemessen, nicht vermutet.**
`src/components/LieferantDokumentModal.test.tsx`, Fall „zeigt Hinweis im Modal UND
Toast; Bearbeiten und Speichern bleiben deaktiviert": `AssertionError: expected
[ …(3) ] to have a length of 2 but got 3`. Kein Timeout, im Einzellauf
reproduzierbar.

Gegenprobe des Orchestrators auf **`89ffc0d5`** — dem Stand, von dem dieses
Vorhaben abzweigt, also vor jeder unserer Änderungen: **identischer Fehlschlag,
1 failed / 18 passed.** Damit ist belegt, dass er nicht von uns stammt; er gehört
zum Sperr-Vorhaben auf `claude/eloquent-ramanujan-gz0w2t`.

Korrektur an der Baseline oben: Dort steht „kein Assertion-Fehler in der
Baseline". Das war zu optimistisch gemessen — der Fall lief damals unter Last in
einen Timeout und wurde als Last-Flake eingeordnet, statt einzeln nachgefahren zu
werden. **Neue Abnahmeregel für den Rest des Vorhabens:** grün = 1081/1082, und
der eine bekannte Fehlschlag ist genau dieser. Ein zweiter ist neu. Lehre für
künftige Läufe: einen roten Test der Baseline **einzeln** nachfahren, bevor man
ihn als Last abtut.

## Abschnitt 6 — Task 7b (Coding-Agent)

Zeit: 2026-09-06T15:04:01Z
Branch: layout/task-7b-mitarbeiter-kontakt
Commit(s): 0d930de0
Status: fertig

Hinweis zur Quellenlage: `docs/superpowers/plans/2026-09-05-layout-14-zoll.md`
enthaelt in meinem Worktree (`wt/layout-task-7b`, abgezweigt vom abgenommenen
Stand der Abschnitte 1-5) **keinen** eigenen Abschnitt "Task 7b" -- die
Plan-Datei wurde erst mit Commit `f00ce836` ("docs(plan): Task 7b
(Mitarbeiter-Kontaktspalte) als Abschnitt 6, Task 10 rutscht in Abschnitt 7")
um diesen Abschnitt ergaenzt, und dieser Commit ist kein Vorfahre meines
Branches. Per `git show f00ce836` geprueft: der dortige Task-7b-Text deckt
sich inhaltlich mit dem Auftrag, den ich direkt vom Orchestrator erhalten
habe (Befund, Dateien, Testgetrieben-Vorgabe, Gates identisch). Umgesetzt nach
dem direkten Auftrag; keine inhaltliche Abweichung festgestellt. Einzige
Ungenauigkeit in beiden Fassungen des Auftrags: "Ansprechpartner" und
"Zahlungsziel" werden als betroffene Felder genannt, existieren im
Mitarbeiter-Datenmodell aber nicht (das sind Kunden-/Lieferanten-Felder) --
vermutlich aus der Kundeneditor-Vorlage kopiert. Wirkungslos fuer die Umsetzung,
da ich ohnehin ausnahmslos alle zehn Zeilen der Spalte repariert habe.

Was gemacht wurde:
- Rote Spec zuerst (TDD, `superpowers:test-driven-development` befolgt):
  `e2e/mitarbeiter-layout.spec.ts` um eine lange, bindestrichlose Fantasie-
  E-Mail (`EMAIL_LANG`, 102 Zeichen, `.example`-Domain) in der Fixture
  ergaenzt -- ein Bindestrich waere selbst ein Umbruchpunkt und wuerde den
  Fehler verdecken (kriterien.md, "Testdaten fuer Umbruch-Fehler brauchen ein
  langes Wort ohne Trennstellen"). Die vorhandene 44-stellige Abteilung
  (`Sonderaufgabenkoordinationsstellenverwaltung`) war schon bindestrichlos
  lang genug und blieb unveraendert. `blockiereFremdeNetzwerkzugriffe`
  verdrahtet (fehlte bisher in dieser Spec).
- Drei neue Zusicherungen direkt beim Oeffnen der Detailseite (vor den
  bestehenden Kopf-/Reiter-Checks): `main` ohne Ueberstand, E-Mail-Wert
  innerhalb der Kontakt-Karte, Abteilung-Wert innerhalb der Kontakt-Karte.
  Die Karte wird ueber die Ueberschrift "Persoenliche Daten" + Vorfahre mit
  `shadow-sm` identifiziert (`card.tsx`), eingegrenzt auf diese Karte, weil
  die Kopfzeile (Z. 335) dieselbe Abteilungs-Zeichenkette im Untertitel
  wiederholt und ein ungegrenztes `getByText(exact)` sonst mehrdeutig waere.
- Rot verifiziert (siehe Zahlen unten), dann `MitarbeiterEditor.tsx`
  `SideInfo` (Z. 437 ff.) gefixt: an allen zehn Zeilen der Spalte (Persoenliche
  Daten: Voller Name, Geburtsdatum, Abteilung(en); Kontakt: E-Mail,
  Mobiltelefon, Festnetz, Adresse; Qualifikation: Stufe; Konditionen:
  Stundenlohn, Jahresurlaub) das umschliessende `<div>` auf `min-w-0 flex-1`,
  den Wert-`<p>` auf zusaetzlich `break-words`, das Icon auf zusaetzlich
  `shrink-0` -- Muster uebernommen aus `LieferantenEditor.tsx` Z. 315/324 und
  `Kundeneditor.tsx` Z. 497/509, nicht neu erfunden. Nicht nur E-Mail und
  Abteilung (die einzigen mit rotem Testnachweis), sondern die ganze Spalte
  einheitlich behandelt, wie im Auftrag verlangt.
- Gruen verifiziert (siehe Gates unten).

Gemessene Zahlen vorher/nachher (Fixture: `EMAIL_LANG`
`personalaktenverwaltungspostfachfuermitarbeiterkommunikationsservice@musterstadtnordwestgebiet.example`,
102 Zeichen; `ABTEILUNG` `Sonderaufgabenkoordinationsstellenverwaltung`,
44 Zeichen; beide bindestrichlos):

| Messung | 1440 (pc-14zoll) vorher | 1440 nachher | 1920 (pc-monitor) vorher | 1920 nachher |
| --- | --- | --- | --- | --- |
| `main.scrollWidth - main.clientWidth` | 366px | 0px | 182px | 0px |
| E-Mail-Wert ueber rechte Kartenkante | 430px | 0px (≤2px-Toleranz) | 374px | 0px |
| Abteilung-Wert ueber rechte Kartenkante | 21px | 0px | 0px (passte schon) | 0px |

Vorher-Zahlen per `expect.soft` einmalig am unveraenderten Code gemessen (Fix
kurz zurueckgenommen, Zusicherungen auf `expect.soft` gestellt, Lauf gemacht,
danach Fix und harte Zusicherungen wiederhergestellt -- kein Zwischenstand
committet). Die 430px/374px E-Mail-Werte sind hoeher als die vom Design-
Reviewer in Abschnitt 5 mit einer 91-Zeichen-Adresse gemessenen 248px/192px
(Kastenrand-Methode statt Bild-Augenmass) -- konsistent, meine Fixture ist mit
102 Zeichen laenger. Die Abteilung-Zahlen (21px bei 1440, passt bei 1920)
decken sich exakt mit dem Befund des Design-Reviewers.

Gate-Ergebnisse (aus `react-pc-frontend/`, Port 5221 frei vor dem Lauf
geprueft):
- `netstat -ano | findstr :5221`: leer.
- `E2E_PORT=5221 npx playwright test e2e/mitarbeiter-layout.spec.ts`: 2/2
  gruen (pc-14zoll, pc-monitor), inklusive `designPruefung(...,
  { strengePruefungen: true })`.
- `npm run lint`: 0 Fehler, dieselbe 1 vorbestehende Warnung
  (`BelegeKasseEditor.tsx:1204`) wie Baseline.
- `npm run build`: gruen (`tsc -b` + `vite build`). Build-Output verworfen
  (`git checkout -- src/main/resources/static`,
  `git clean -f src/main/resources/static/assets`), `git status` danach nur
  die zwei erlaubten Dateien.
- `test-results/` nicht committet (`.gitignore` greift).

Bedenken / Abweichungen vom Plan:
- Quellenlage-Hinweis siehe oben (Plan-Datei in meinem Worktree ohne
  Task-7b-Abschnitt) -- kein inhaltlicher Konflikt, nur zur Nachvollziehbarkeit
  vermerkt.
- Die im Auftrag genannten Felder "Ansprechpartner" und "Zahlungsziel"
  existieren im Mitarbeiter-Datenmodell nicht (Kopierfehler aus der
  Kundeneditor-Vorlage) -- ohne Auswirkung, da alle zehn tatsaechlichen Zeilen
  der Spalte repariert wurden.
- `keinTextLaeuftUeber`/`keinHorizontalerUeberlauf` aus `design.ts` haetten
  den urspruenglichen Fehler an dieser Stelle strukturell NICHT gefunden (das
  `<p>` selbst hat `scrollWidth == clientWidth`, der Ueberlauf entsteht erst
  auf Ebene der Karte) -- deshalb die drei manuellen Kasten-Zusicherungen in
  der Spec, wie in `playwright-design-pruefung` SKILL.md unter "Was die
  automatischen Checks nicht sehen" beschrieben. `design.ts` liegt nicht in
  meiner Files-Liste, daher keine Aenderung dort vorgenommen -- falls Task 10
  (Abschnitt 7) eine generische "Wert im Kasten"-Pruefung ergaenzen will, waere
  das der richtige Ort.

---

## Abschnitt 6 — Code-Review (Code-Reviewer)

Zeit: 2026-09-06T15:17:41Z
Branch: feature/layout-14-zoll (HEAD 56b0c2aa)
Geprueft: Diff 9c94ec3d..HEAD
Status: fertig
Ampel: 🟡

### Gates

- `npm run lint`: 0 Fehler, genau die eine bekannte Warnung
  (`BelegeKasseEditor.tsx:1204`, `react-hooks/exhaustive-deps`). Gruen.
- `npm run test`: **1082 von 1082 gruen**, 88 von 88 Testdateien, Exit 0.
  Der als vorbestehend angekuendigte Fehlschlag in
  `src/components/LieferantDokumentModal.test.tsx` ("zeigt Hinweis im Modal
  UND Toast") ist **nicht** aufgetreten. Der erste Lauf war unbrauchbar und
  wurde verworfen: parallel lief die Playwright-Sitzung des Design-Reviewers,
  dadurch konnten 7 vitest-Worker gar nicht erst starten
  ("Failed to start forks worker ... Timeout waiting for worker to respond",
  u.a. fuer `LieferantDokumentModal.test.tsx`) und zwei weitere Tests liefen
  in den 5000-ms-Timeout. Der saubere Wiederholungslauf ist gruen.
  Folgerung fuer die Abnahmeregel: Der "bekannte" Fehlschlag ist offenbar
  **kein stabiler Vorzustand, sondern lastabhaengig** — die Regel
  "1081 von 1082" sollte nicht als Sollwert festgeschrieben werden.
- `npm run build`: Exit 0, nur die vorbestehende Chunk-Groessen-Warnung.
  Build-Ausgabe (`src/main/resources/static/index.html` + zwei neue Assets)
  wieder entfernt, Arbeitsbaum sauber.

### Dateiumfang

`0d930de0` fasst genau die zwei freigegebenen Dateien an
(`MitarbeiterEditor.tsx`, `e2e/mitarbeiter-layout.spec.ts`, 105+/31-).
`56b0c2aa` ist der Plan-Eintrag fuer Task 10 (vom Menschen selbst gesetzt).
Nichts ausserhalb der Freigabe.

### Was geprueft und bestaetigt wurde

- **Alle zehn Zeilen der Seitenspalte** tragen jetzt `min-w-0 flex-1` am
  umschliessenden `<div>`, `break-words` am Wert, `shrink-0` am Icon:
  Voller Name, Geburtsdatum, Abteilung(en), E-Mail, Mobiltelefon, Festnetz,
  Adresse, Stufe, Stundenlohn, Jahresurlaub. Keine ausgelassen.
- "Ansprechpartner" und "Zahlungsziel" aus dem Task-Bericht gibt es im
  `interface Mitarbeiter` (Z. 27-54) tatsaechlich nicht — reines
  Vorlagen-Artefakt aus dem Kundeneditor, ohne Folgen fuer die Umsetzung.
- **Keine Messreste im Commit**: kein `expect.soft`, kein `.only`, kein
  `.skip(` in `src/` oder `e2e/`. Der einzige Treffer im Repo steht in dieser
  Log-Datei (Z. 2450 f.) als Beschreibung der Messmethode — Dokumentation,
  kein Code.
- Testdaten stimmen mit dem Bericht ueberein: E-Mail 102 Zeichen (lokaler Teil
  68), Abteilung 44 Zeichen, beide ohne Bindestrich, `.example`-Domain, kein
  Personenbezug (DSGVO in Ordnung).
- Der XPath-Anker auf `" shadow-sm "` trifft die richtige Karte und faellt
  nicht auf `hover:shadow-sm` der Dokumentenzeilen herein (dort steht ein
  Doppelpunkt statt eines Leerzeichens vor `shadow-sm`).

### Hinweis 1 — die Fehlerklasse ist NICHT ueberall zu

Die Seitenspalte der Mitarbeiterseite ist zu. Die Klasse steht aber noch an
weiteren Stellen, alle **vorbestehend** und alle in Nachbarzeilen von bereits
reparierten Zeilen — Abschnitt 4 und 5 haben jeweils nur die E-Mail-Zeile
angefasst, die Geschwisterzeilen daneben nicht:

1. `Kundeneditor.tsx` Z. 902-907, **Uebersichtskarte**, Zeilen
   "Ansprechpartner" und "Telefon":
   `<p className="flex items-center gap-2"><User .../>{kunde.ansprechspartner}</p>`
   — der Text ist ein anonymes Flex-Item mit `min-width: auto`, das Icon hat
   kein `shrink-0`. Exakt der Mechanismus, den Task 9 zwei Zeilen tiefer
   (E-Mail, Z. 918-923) mit `<span className="min-w-0 break-words">`
   repariert hat. Diese Karte ist zudem die einzige ohne `overflow-hidden`.
2. `Kundeneditor.tsx` Z. 464, 474, 483, **Detailseite Kontaktdaten-Spalte**,
   Zeilen "Ansprechpartner"/"Telefon"/"Mobiltelefon": nacktes `<div>` ohne
   `min-w-0`, Wert-`<p>` ohne `break-words`. Die E-Mail-Zeile daneben
   (Z. 497) ist repariert.
3. `LieferantenEditor.tsx` Z. 297, 306, 345, 356, dieselbe Spalte, Zeilen
   "Telefon"/"Mobil / Fax"/"Vertreter"/"Standard-Kostenstelle". E-Mail-Zeile
   (Z. 315) repariert.
4. `MitarbeiterEditor.tsx` Z. 408-419 (Dokumentenliste) und Z. 685-696
   (Lohnabrechnungen): `flex items-center gap-3` -> nacktes `<div>` ->
   `<p className="font-medium text-slate-900">{doc.originalDateiname}</p>`.
   Dateinamen sind der realistischste Fall von allen: Unterstriche sind nach
   UAX #14 **keine** Umbruchstelle, ein
   `Arbeitsvertrag_Beispielmusterfrauenbergwaldschmidtstein_2024.pdf` ist ein
   einziges unteilbares Wort. Der Knopfblock rechts hat ausserdem kein
   `shrink-0`.

Nach Realitaetsnaehe geordnet: Dateiname > Standard-Kostenstelle > Vertreter
und Ansprechpartner > Telefonnummern (die enthalten meist Leerzeichen und
sind eher latent als akut).

**Sauber geprueft und in Ordnung**: Projekt- und Anfrage-Uebersichtskarte
(`flex-1 min-w-0` + `truncate`/`line-clamp` + `shrink-0`), Lieferanten-
Uebersichtskarte (`truncate`/`line-clamp-2` setzen `overflow: hidden` und
nullen damit `min-width: auto`), Kopfzeilen und Kennzahlenreihen von Projekt
und Anfrage, sowie die zehn Zeilen der Mitarbeiter-Seitenspalte selbst.

Keine dieser Fundstellen ist durch Task 7b entstanden. Deshalb 🟡 und kein 🔴.

### Hinweis 2 — die strukturelle Aussage im Plan-Eintrag zu Task 10 stimmt nur zur Haelfte

Der Eintrag (Plan Z. 1104 ff.) sagt, weder `keinTextLaeuftUeber` noch
`keinHorizontalerUeberlauf` koennten die Klasse finden. Geprueft:

- `keinTextLaeuftUeber`: **stimmt.** Sie misst nur Blatt-Elemente gegen sich
  selbst (`el.scrollWidth > el.clientWidth + 2`). Solange das Flex-Item
  mitwaechst, ist das `<p>` exakt so breit wie sein Text — strukturell blind.
- `keinHorizontalerUeberlauf`: **stimmt nicht.** Ihre zweite Ebene ist
  `main.scrollWidth > main.clientWidth` — genau die Zahl, die Task 7b als
  366 px (1440) und 182 px (1920) gemessen hat. Und
  `designPruefung(..., { strengePruefungen: true })` stand in
  `mitarbeiter-layout.spec.ts` **schon vor Task 7b** (Kontextzeile im Diff,
  nicht hinzugefuegt). Der Waechter war also laengst scharf und haette den
  Fehler am Tag seiner Einfuehrung rot gemeldet. Er hat ihn nur nie gesehen,
  weil `DUMMY_MITARBEITER.email` auf `null` stand.
- Gegenprobe bei Kunde und Lieferant: dieselben Specs rufen
  `designPruefung(..., { strengePruefungen: true })` auf, und die Fixtures
  tragen `ansprechspartner: 'Erika Musterfrau'`, `vertreter: 'Hans Beispiel'`,
  `standardKostenstelleName: undefined`. Wieder: Waechter scharf, Testdaten
  harmlos.

**Die Luecke ist nicht die Pruefung, sondern die Fixture.** Wer eine neue
allgemeine Pruefung baut, ohne die Testdaten zu haerten, findet wieder nichts.

### Hinweis 3 — Praktikabilitaet von "Wert bleibt in seiner Karte"

Machbar, aber eng geschnitten und **nicht als erste Massnahme**. Empfehlung
fuer Task 10, in dieser Reihenfolge:

1. **Zuerst die Fixtures haerten.** Ein langes, bindestrich- und
   leerzeichenloses Fantasiewort in jedes freie Textfeld jeder Layout-Fixture
   (Name, Ansprechpartner, Vertreter, Kostenstelle, Dateiname, E-Mail). Das
   aktiviert die bereits scharfen Waechter und haette alle fuenf Fundstellen
   ohne eine Zeile neuen Pruefcode gefunden. Billigste Massnahme, groesster
   Ertrag.
2. **Dann die neue Pruefung, eng geschnitten.** Nur Blatt-Elemente mit Text,
   gegen den naechsten Vorfahren mit `position: static/relative`, der eine
   `Card` ist — und die `Card` dafuer mit einem `data-kasten`-Attribut
   markieren, statt den Anker aus geratenen Stilmerkmalen abzuleiten.
   Uebersprungen: alles unter `position: absolute/fixed/sticky`, alles mit
   scrollbarem Zwischencontainer, alles mit
   `data-kuerzung-erlaubt`/`ellipsis`/`line-clamp` (dieselbe Arbeitsteilung
   wie in `keinHorizontalerUeberlauf`), alles Unsichtbare. Toleranz 2 px,
   `uebergaengeAusklingenLassen` davor.
3. **Erst opt-in in den fuenf Detail-Specs**, dann gegen alle 16 Specs
   gegenpruefen, und nur bei sauberem Lauf in `designPruefung` aufnehmen.

Baut man sie breit ("jedes Blatt gegen den naechsten Vorfahren mit sichtbarem
Rahmen"), erzeugt sie zwangslaeufig Fehlalarme:

- Der Anker laesst sich nicht sauber automatisch bestimmen. Task 7b musste ihn
  per XPath auf `shadow-sm` handverlesen **und** zusaetzlich gegen
  Mehrdeutigkeit absichern (die Kopfzeile wiederholt denselben
  Abteilungstext). Ein generisches "Vorfahre mit Rahmen oder eigenem
  Hintergrund" trifft in diesem Projekt die `Card`, die
  `bg-slate-50`-Zeilenkaesten und die `bg-rose-100`-Badges gleichermassen —
  je nach Wahl ist derselbe Wert mal drin, mal draussen.
- Absolut positionierte Kinder liegen absichtlich am Rand (z. B. der
  Bearbeiten-Knopf `absolute top-3 right-3` auf der Lieferantenkarte).
- In einem `overflow-auto`-Kasten darf Inhalt legitim ueber die Karte
  hinausreichen.
- Gewollte Kuerzungen und laufende Uebergaenge, dazu Subpixel-Rauschen.

### Hinweis 4 — Mutationsproben (aus dem Spec-Code abgeleitet, kein Browserlauf)

- **`min-w-0` raus**: drei Zusicherungen greifen —
  `mainUeberstandBeimOeffnen` (Spec Z. 148-155) sowie beide
  `pruefeWertImKasten`-Aufrufe (Z. 168-181), dazu
  `keinHorizontalerUeberlauf` aus `designPruefung`. Stark abgedeckt.
- **`break-words` raus** (bei erhaltenem `min-w-0`): die beiden neuen
  `pruefeWertImKasten`-Zusicherungen sind **blind**. `boundingBox()` liefert
  den Rahmen des `<p>`, und der bleibt mit `min-w-0` schmal innerhalb der
  Karte; der Text malt darueber hinaus, ohne den Kasten zu verbreitern.
  Greift stattdessen `keinTextLaeuftUeber` aus
  `designPruefung(..., { strengePruefungen: true })`
  (`p.scrollWidth > p.clientWidth`) und sehr wahrscheinlich auch
  `mainUeberstandBeimOeffnen`, weil `Card` kein `overflow-hidden` hat
  (`card.tsx`: `bg-white border border-slate-200 rounded-lg shadow-sm`).
  Also abgedeckt — aber durch die alten Pruefungen, nicht durch die neuen.
- **`shrink-0` raus**: **keine** Zusicherung greift. Das ist keine Deko: das
  UA-Stylesheet setzt `svg:not(:root) { overflow: hidden }`, damit ist die
  automatische Mindestgroesse des Icons 0 und es koennte unter Druck
  zusammengequetscht werden. Ein gequetschtes Icon erzeugt aber keinen
  Ueberstand — kein Test sieht es. Sauber vorgesorgt, aber ungetestet.

Nebenbefund: `mainUeberstandBeimOeffnen` prueft dieselbe Zahl, die
`keinHorizontalerUeberlauf` wenige Zeilen spaeter ohnehin prueft. Doppelt,
aber mit deutlich besserer Fehlermeldung — kein Grund zur Aenderung.

Bedenken / Abweichungen vom Plan:
- Kein Playwright angefasst (Design-Reviewer laeuft parallel), alle Aussagen
  zu den Mutationsproben sind aus dem Spec-Code abgeleitet.
- Die Abnahmeregel "1081 von 1082 mit einem bekannten Fehlschlag" hat sich im
  sauberen Lauf nicht bestaetigt — siehe Gates.
