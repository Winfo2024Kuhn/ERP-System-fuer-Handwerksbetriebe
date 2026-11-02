# Firmendokumente – API-Vertrag

Ablage für Firmen-PDFs (Torprüfung, Gefährdungsbeurteilung, Versicherungen …) in einer
Ordner-Baumstruktur. Sichtbar und bearbeitbar für **jeden angemeldeten PC-Nutzer**
(Session + CSRF wie alle `/api/**`-Endpunkte, keine Admin-Prüfung). Nicht in der Handy-App.

Alle Fehler kommen als `ApiError` `{ status, message, fields }`; `message` ist ein deutscher Satz
für die Nutzer.

## Ordner

### `GET /api/firmendokumente/ordner`
Alle Ordner (höchstens 2000), sortiert nach Name.

```json
[
  { "id": 1, "name": "Prüfungen", "parentId": null,
    "anzahlDokumente": 3, "anzahlUeberfaellig": 1, "anzahlBaldFaellig": 0 }
]
```
- `anzahlDokumente`, `anzahlUeberfaellig`, `anzahlBaldFaellig` zählen nur Dokumente **direkt** in
  diesem Ordner (Unterordner nicht mitgezählt; das Frontend summiert bei Bedarf).
- überfällig = `faelligAm` < heute; bald fällig = heute ≤ `faelligAm` ≤ heute + 30 Tage.
- Neue Installationen und bestehende ohne Ordner bekommen fünf Startordner:
  „Arbeitsschutz“, „Prüfungen“, „Versicherungen“, „Verträge“, „Zertifikate & Nachweise“.

### `POST /api/firmendokumente/ordner` → 201 + Ordner
Body `{ "name": "Tore", "parentId": 1 }` (`parentId` `null` = oberste Ebene).
- 400 „Bitte einen Namen für den Ordner eingeben.“ (leer), „Der Name darf höchstens 200 Zeichen lang sein.“
- 400 „Den übergeordneten Ordner gibt es nicht mehr.“
- 400 „Hier gibt es schon einen Ordner „Tore“.“ (Name ohne Groß/Klein gleich)

### `PUT /api/firmendokumente/ordner/{id}` → 200 + Ordner
Body `{ "name": "Tore & Türen" }` – umbenennen. Fehler wie oben, 404 wenn Ordner fehlt.

### `PUT /api/firmendokumente/ordner/{id}/verschieben` → 200 + Ordner
Body `{ "parentId": 5 }` oder `{ "parentId": null }`.
- 400 „Ein Ordner kann nicht in sich selbst oder einen seiner Unterordner verschoben werden.“
- 400 Namensdoppel wie oben, 404 wenn Ordner fehlt.

### `DELETE /api/firmendokumente/ordner/{id}` → 204
- 400 „Der Ordner ist nicht leer. Bitte zuerst die Dokumente und Unterordner verschieben oder löschen.“
- 404 wenn Ordner fehlt.

## Dokumente

Dokument-JSON (überall gleich):
```json
{
  "id": 7,
  "ordnerId": 1,
  "name": "Torprüfung Halle 2 – 2026",
  "originalDateiname": "scan_0042.pdf",
  "groesseBytes": 482113,
  "hochgeladenAm": "2026-11-02T09:14:00",
  "hochgeladenVon": "Max Mustermann",
  "faelligAm": "2027-10-31",
  "url": "/api/firmendokumente/7/datei"
}
```
`hochgeladenVon` und `faelligAm` können `null` sein.

Seiten-JSON (Listen):
```json
{ "eintraege": [ /* Dokumente */ ], "gesamt": 120, "seite": 0, "seitenGroesse": 50 }
```
`page` ab 0 (negativ → 0), `size` Standard 50, höchstens 50.

### `GET /api/firmendokumente?ordnerId=1&page=0&size=50`
Dokumente direkt in einem Ordner, sortiert nach Name (ohne Groß/Klein), dann id.
`ordnerId` Pflicht; 404 wenn Ordner fehlt.

### `GET /api/firmendokumente/suche?q=tor&page=0&size=50`
Sucht in Name und Original-Dateiname über alle Ordner (ohne Groß/Klein), sortiert nach Name.
- 400 „Bitte mindestens 2 Zeichen eingeben.“ / „Der Suchbegriff darf höchstens 100 Zeichen lang sein.“

### `GET /api/firmendokumente/faellig?page=0&size=50`
Überfällige und bald fällige Dokumente (`faelligAm` ≤ heute + 30 Tage), sortiert nach `faelligAm`
aufsteigend, dann Name.

### `POST /api/firmendokumente` (multipart) → 201 + Dokument
Felder: `datei` (PDF, Pflicht), `ordnerId` (Pflicht), `name` (Pflicht, ≤ 200), `faelligAm`
(optional, `yyyy-MM-dd`).
- 400 „Bitte eine Datei auswählen.“ (leer)
- 400 „Es können nur PDF-Dateien abgelegt werden.“ (Inhalt beginnt nicht mit `%PDF-`)
- 400 „Die Datei ist größer als 50 MB. Bitte eine kleinere PDF wählen oder die Datei verkleinern.“
- 400 „Bitte einen Namen für das Dokument eingeben.“ / Länge wie oben
- 400 „Den Ordner gibt es nicht mehr.“

### `PUT /api/firmendokumente/{id}` → 200 + Dokument
Body `{ "name": "…", "ordnerId": 3, "faelligAm": "2027-10-31" }` – alle drei Felder immer mitschicken
(`faelligAm: null` löscht das Datum). Umbenennen, Verschieben und Datum in einem.
Fehler wie beim Hochladen, 404 wenn Dokument fehlt.

### `DELETE /api/firmendokumente/{id}` → 204
Löscht Eintrag und Datei endgültig. 404 wenn Dokument fehlt.

### `GET /api/firmendokumente/{id}/datei[?download=true]`
Die PDF (`application/pdf`). Ohne `download` `Content-Disposition: inline`, mit `download=true`
`attachment`; Dateiname = `name` + `.pdf`. 404 wenn Dokument oder Datei fehlt.
