# Original EN1090 PDF mock adapter

Start `./preview/pdf-start.sh` (Java 23 and Maven available on PATH).
The bridge binds **127.0.0.1:8097** only. `GET /health` reports readiness.
It does not start Spring, a database, mail or scheduled jobs. It instantiates the
unchanged original `BestellungPdfService`, mocking only its data providers.
The optional company header contains dummy company details; no real logo or
uploaded files are loaded. Existing classpath PDF appendices remain unchanged.
Generated temporary files are read, returned and deleted.

POST `/render` with JSON:

```json
{
  "art": "bedarf",
  "id": 7,
  "bestellungen": [
    {"projektId":7,"projektName":"Musterhalle","projektNummer":"TEST-7",
     "produktname":"Vierkantrohr","menge":3,"stueckzahl":3,"einheit":"Stk",
     "fixmassMm":1200,"rootKategorieId":1,"lieferantId":21}
  ]
}
```

- `bedarf`: `id` is project ID; calls original `generateBedarfslistePdf`.
- `bestellung`: `id` is supplier ID; calls original `generatePdfForLieferant`.
  Optional `variante: "projekt"` uses project ID and original `generatePdfForProjekt`.
- `preisanfrage`: `id` is inquiry-supplier ID. Also provide
  `preisanfrage: {id, nummer, bauvorhaben, antwortFrist, notiz, token}` and
  `positionen: [{produktname, produkttext, externeArtikelnummer, werkstoffName,
  menge, einheit, kommentar, reihenfolge}]`. Calls original `generatePdfForPreisanfrage`.

All data comes from the current browser mock snapshot; output is PDF bytes.
Dates use ISO YYYY-MM-DD. Original DTO fields are retained; unrecognized frontend
fields are ignored. The preview accepts at most 2 MiB and 5000 positions.
The Vite preview API sends its current mock snapshot directly to `/render` when an original PDF URL is requested.

Verification: `mvn -Dtest=OriginalPdfPreviewTest,BestellungPdfServiceTest test`.
