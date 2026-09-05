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

## Was NICHT zu diesen Kriterien gehört (Anti-Bikeshedding)

Formatierung, for- vs. while-Loop, Naming-Geschmack und ähnliche
Stilfragen sind **keine** Kriterien hier. Die dürfen im Review als 🟡-Hinweis
auftauchen, aber nie eine Nachbesserung auslösen.
