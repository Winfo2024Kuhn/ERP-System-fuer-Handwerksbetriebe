# Admin-Endpoints

Diese Datei listet alle Endpoints, die nur fuer administrative Zwecke gedacht
sind (Batch-Jobs, Backfills, Datenkorrekturen). Sie sind ueber die bestehende
Session-Authentifizierung geschuetzt — jeder angemeldete Nutzer kann sie aktuell
ausfuehren. Eine feinere Rollentrennung (Rolle `ADMIN`) kommt mit dem
Berechtigungs-Feature.

**Konvention:**
- Pfad-Prefix: `/api/admin/...`
- HTTP-Verb **POST** fuer Aktionen / Batch-Jobs (nicht PUT — PUT = Ressource ersetzen)
- Antwort: JSON mit aussagekraeftigem Ergebnis-Objekt (Anzahl verarbeitet,
  uebersprungen, Fehlerdetails)

---

## Uebersicht

| Endpoint | Verb | Zweck | Eingefuehrt |
|---|---|---|---|
| `/api/admin/vendor-invoices/...` | diverse | Lieferanten-Rechnungs-Verwaltung | vor 2026-04 |
| `/api/admin/artikel/durchschnittspreis/backfill` | POST | Initialer/erneuter Backfill der Artikel-Durchschnittspreise | 2026-04-21 |

---

## Details

### POST `/api/admin/artikel/durchschnittspreis/backfill`

**Zweck:** Berechnet fuer jeden Artikel den Durchschnittspreis neu auf Basis
aller aktuellen `LieferantenArtikelPreise.preis`-Eintraege. Gedacht fuer:
- **Erstinbetriebnahme** des Feature-B-Durchschnittspreises (damit Artikel
  sofort realistische Werte haben).
- **Korrektur-Lauf**, wenn der Durchschnitt aus irgendeinem Grund verdreckt ist.

**Achtung:** Dies ueberschreibt den aktuellen `durchschnittspreis_netto` und
setzt `durchschnittspreis_menge` auf `0`, weil historische Mengen nicht
vorliegen. Danach wachsen beide Werte mit jeder neuen Rechnung korrekt
weiter.

**Request:**
```http
POST /api/admin/artikel/durchschnittspreis/backfill
Cookie: JSESSIONID=...
```

**Response 200:**
```json
{
  "verarbeitet": 1243,
  "uebersprungen": 17,
  "dauerMs": 582
}
```

- `verarbeitet` — Anzahl Artikel, deren Durchschnitt gesetzt wurde.
- `uebersprungen` — Anzahl Artikel ohne irgendeinen Lieferantenpreis
  (bleibt `null`).
- `dauerMs` — Laufzeit des Backfills.

**Auf der Kommandozeile (zum Testen):**
```bash
curl -X POST http://localhost:8080/api/admin/artikel/durchschnittspreis/backfill \
     --cookie "JSESSIONID=..."
```

**Wann ausfuehren?**
- Einmalig nach Feature-B-Deployment.
- Wenn ein Test-Lauf / Datenmigration die Werte verdreckt hat.
- Nicht regelmaessig — im Normalbetrieb wachsen die Werte durch den
  Matching-Agent automatisch mit.
