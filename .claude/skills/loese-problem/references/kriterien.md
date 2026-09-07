# Kriterien für loese-problem (Coding + Review, gemeinsame Quelle)

**Coding-Agenten:** lies das hier VOR dem ersten Edit, zusätzlich zur
normalen Pflichtlektüre (`BACKEND_ARCH.md`/`FRONTEND_UI.md`/
`TESTING_SECURITY.md`/`security-audit.md`). Ziel: die Punkte unten schon
beim Schreiben einhalten, nicht erst hinterher vom Review-Agenten korrigieren
lassen — jede vermeidbare Nachbesserungs-Runde kostet Zeit.

**Review-Agent:** dieselbe Liste ist dein Prüf-Maßstab für die Ampel.

## Sicherheit & DSGVO

Vollständig abgedeckt durch `.claude/commands/security-audit.md` und
`docs/agent instructions/docs/TESTING_SECURITY.md` — hier nicht dupliziert,
einfach die Checklisten dort abarbeiten.

## Grüne Gates (nicht verhandelbar) — und wer welches fährt

Vorgabe des Nutzers vom 04.09.2026: **Coding-Agenten fahren nie die komplette
Testsuite.** Das dauert zu lang (Backend ~10 Minuten, Frontend ~2 Minuten, und
parallel laufende Agenten machen zeitabhängige Tests flaky). Sie testen genau
ihre Änderung — die Review-Agenten fahren alles.

| Wer | Backend | Frontend |
| --- | --- | --- |
| **Coding-Agent** | nur die eigenen Testklassen: `./mvnw -B test -Dtest=MeinTest` | nur die eigenen Testdateien: `npx vitest run <datei>`; dazu `npm run lint` und `npm run build` (beide schnell, und Lint ist das am häufigsten gerissene Gate); die eigene Playwright-Spec auf eigenem Port: `E2E_PORT=<port> npx playwright test e2e/<spec>` |
| **Code-Reviewer** (`loese-problem-review`) | voller Lauf `./mvnw -B test` | `npm run lint`, `npm run test` (alles), `npm run build` |
| **Design-Reviewer** (`loese-problem-design-review`, nur bei Frontend-Änderungen) | — | `npm run test:e2e` (alle Specs, beide Bildschirmgrößen), Screenshots anschauen, die sechs Fragen aus `playwright-design-pruefung` beantworten |

Ein Abschnitt ist erst abgenommen, wenn **jeder beteiligte Reviewer** 🟢 oder 🟡
gemeldet hat. Bei Frontend-Änderungen laufen beide Reviewer **parallel**, jeder
in einem eigenen Worktree — der Code-Reviewer macht Mutationsproben am
Quellcode, und die dürfen dem Design-Reviewer nicht in den laufenden
Dev-Server hineinfunken.

**Playwright-Pflicht bei Frontend-Änderungen** (Vorgabe des Nutzers vom
04.09.2026): Was der Nutzer sieht und klickt, wird end-to-end geprüft. Jeder
Task, der `.tsx`/`.ts` unter `react-pc-frontend/src/` oder
`react-zeiterfassung/src/` ändert, liefert eine Spec unter `e2e/` für genau
den geänderten Ablauf — mit gestubbten `/api`-Routen über `e2e/hilfen/api.ts`,
ohne Backend, ohne echte Personendaten — und fährt **diese eine Spec** selbst.
Die Design-Prüfung (feste Bildschirmgrößen, Screenshots, sechs Fragen zu
Farben, Design-System, Look-and-Feel, UX, Auffindbarkeit, Überschneidungen)
macht der Design-Reviewer; Details im Skill `playwright-design-pruefung`.

`lint` wird am häufigsten übersehen: ein Task lieferte Build und Tests grün ab
und riss trotzdem ein vorher grünes Lint-Gate ein — Kosten: zwei
Nachbesserungsrunden. Deshalb fährt es auch der Coding-Agent, obwohl er die
Testsuite nicht fährt.

Gegenstück: vorbestehende Fehler aus der Baseline (siehe Kontext-Log) sind
**nicht** deine Baustelle. Nicht reparieren, nicht überspringen, nicht
deaktivieren — nur die Abnahmeregel einhalten.

## Performance

- Backend: keine N+1-Queries in Schleifen ohne `JOIN FETCH`; Pagination bei
  potenziell großen Listen (`Pageable`); lange Operationen (E-Mail-Versand,
  PDF-Erzeugung) nicht synchron im Request-Thread ohne Async/Queue.
- Frontend: keine sequenziellen Fetch-Wasserfälle, wo `Promise.all` ginge;
  große Listen ohne unnötige Re-Renders (React-Keys, Memoisierung wo
  sinnvoll); Bilder/Assets nicht unkomprimiert einbinden.
- Mobile (`react-zeiterfassung`): Bundle-Größe im Blick behalten — Handwerker
  nutzen die App unterwegs oft mit schlechtem Netz.

## Observability

- Kritische Aktionen (Rechnung erstellt, Zeitbuchung, Löschung) strukturiert
  loggen: Entität + ID, **keine** Klarnamen/E-Mails/Adressen im Log (DSGVO).
- `catch`-Blöcke loggen die tatsächliche Ursache (Exception, Kontext) —
  niemals eine Exception stillschweigend verschlucken.

## API-/Schnittstellen-Design

- Neue Endpoints folgen dem bestehenden Pfad- und DTO-Namensschema statt
  einen eigenen Stil einzuführen — bei bestehenden vergleichbaren Endpoints
  abschauen, nicht neu erfinden.
- Response-Struktur konsistent mit vergleichbaren bestehenden Endpoints
  (Pagination-Wrapper, Fehler-Format).
- Keine Breaking Changes an bestehenden Endpoints, ohne das ausdrücklich als
  Bedenken im Kontext-Log zu vermerken (dann entscheidet der Nutzer, nicht
  der Agent selbst).

## Layout: sechs Fallen, die kein Test von selbst findet

Aus dem 14-Zoll-Vorhaben (September 2026). Jede einzelne ist erst im Browser
oder am gebauten CSS aufgefallen, keine im Quelltext:

- **`break-words` reicht bei Flex-Items nicht.** Eine Überschrift in einer
  Flex-Zeile behält `min-width: auto` und wird so breit wie ihr längstes Wort —
  bei einem Komposita-Namen quer über die Nachbarspalte. Nur `min-w-0` **am
  Element selbst** senkt die Mindestbreite; am Elternteil wirkt es nicht.
- **`min-w-0` muss auf **jede** Ebene, nicht nur auf die unterste.** Die Falle
  gilt für Flex-**und** Grid-Items gleichermaßen. Ist die äußere Zeile selbst ein
  Grid-Item (`grid gap-2`), nützt `min-w-0` an den inneren Flex-Ebenen nichts —
  die äußere hält weiter ihre automatische Mindestbreite. In Task 11 real
  passiert, zweiter Anlauf nötig.
- **`boundingBox()` ist kein Überlauf-Maß.** Ein Element, das seine Elternbreite
  füllt (normaler Block), behält seine Rechteckbreite, auch wenn der Text
  sichtbar darüber hinausmalt — nur Elemente, die sich am Inhalt ausrichten
  (Flex-/Grid-Item, inline-block, float, `w-fit`), werden selbst breiter. Wer
  einen Überlauf messen will, vergleicht `scrollWidth` gegen `clientWidth`,
  nicht zwei Rechtecke. Real passiert: drei Zusicherungen aus diesem Vorhaben
  wurden rot, wenn man `min-w-0` entfernte, blieben aber grün, wenn man
  `break-words` entfernte — sie prüften also nur die halbe Rezeptur.
- **`space-y-*` schlägt `mt-auto`.** Tailwind erzeugt für `space-y-3` einen
  Selektor der Spezifität 0-3-0, `.mt-auto` hat 0-1-0. Wer den letzten Block einer
  Karte nach unten schieben will, braucht `flex flex-col` + `gap-*` statt
  `space-y-*`. Sonst steht die Klasse da und tut nichts.

Alle fünf gelten sinngemäß für jede künftige Layout-Arbeit: **die Klasse im
Quelltext ist kein Beweis, dass sie wirkt.** Am gebauten CSS oder im Browser
nachmessen — und die Zusicherung so bauen, dass sie beim Entfernen **jeder**
beteiligten Klasse rot wird, nicht nur bei einer. In diesem Vorhaben sind
dreimal hintereinander „Attrappen" entstanden: Klassen, die im Diff richtig
aussahen und nichts taten, weil die Nachbarklasse fehlte.

Sechste Falle, aus derselben Familie: **eine definite `max-width` deckelt die
automatische Mindestbreite eines Flex-Items.** Wer eine solche Deckelung
entfernt, weil sie optisch stört, macht damit ein vorhandenes `break-words`
wirkungslos — dann muss `min-w-0` nachrücken. Real passiert beim Streichen von
`max-w-[200px]`.

- **Testdaten für Umbruch-Fehler brauchen ein langes Wort ohne Trennstellen.**
Bindestriche und Punkte sind selbst Umbruchpunkte — eine Adresse wie
`info@beispiel-stahl.example` bricht ohnehin um und verdeckt den Fehler
vollständig (bei 1440 gemessen: 0 px Überstand mit Bindestrich, 272 px ohne).
Wer eine Umbruch-Zusicherung baut, nimmt eine bindestrichlose Zeichenkette,
sonst ist der Test grün und hält nichts fest. **Aber harte Testdaten allein
reichen nicht:** Steht die überlaufende Stelle in einer breiten Hauptspalte,
schluckt die den Überstand, bevor er `main` erreicht — dann schlägt der
Seiten-Wächter nicht an und es braucht eine Zusicherung „Wert bleibt in seinem
Kasten". Beides kombinieren. Dasselbe gilt für Namen:
„Wohnungsbaugesellschaft Beispielstadt Nord" prüft etwas anderes als ein echtes
Komposita-Wort ohne Leerzeichen.

## Was NICHT zu diesen Kriterien gehört (Anti-Bikeshedding)

Formatierung, for- vs. while-Loop, Naming-Geschmack und ähnliche
Stilfragen sind **keine** Kriterien hier. Die dürfen im Review als 🟡-Hinweis
auftauchen, aber nie eine Nachbesserung auslösen.
