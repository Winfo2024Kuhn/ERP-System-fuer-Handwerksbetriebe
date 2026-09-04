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

## Grüne Gates (nicht verhandelbar)

Ein Task ist erst fertig, wenn **alle** Prüfungen grün sind, die das Projekt
kennt — nicht nur die, die im Auftrag namentlich stehen. Im Frontend sind das
`lint`, `test`, `build` **und `test:e2e`** (Playwright, siehe unten); im Backend
der volle Testlauf.

**Playwright-Pflicht bei Frontend-Änderungen** (Vorgabe des Nutzers vom
04.09.2026): Was der Nutzer sieht und klickt, wird end-to-end geprüft. Jeder
Task, der `.tsx`/`.ts` unter `react-pc-frontend/src/` ändert, liefert eine
Spec unter `react-pc-frontend/e2e/` für genau den geänderten Ablauf — mit
gestubbten `/api`-Routen über `e2e/hilfen/api.ts`, ohne Backend, ohne echte
Personendaten. Laufen mehrere Agenten gleichzeitig, bekommt jeder einen
eigenen Port: `E2E_PORT=5174 npm run test:e2e`. Der Review-Agent fährt
`npm run test:e2e` nach dem Merge selbst, auf dem Standard-Port.

`lint` wird dabei am häufigsten übersehen: ein Task lieferte Build und Tests
grün ab und riss trotzdem ein vorher grünes Lint-Gate ein — Kosten: zwei
Nachbesserungsrunden. Der Review-Agent fährt Lint deshalb auch dann selbst,
wenn im Auftrag nur von Tests die Rede war.

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
