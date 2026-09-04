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
