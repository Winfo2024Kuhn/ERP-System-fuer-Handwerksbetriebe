# Firmendokumente – API-Vertrag

Ablage für Firmen-Dokumente (Torprüfung, Gefährdungsbeurteilung, Versicherungen …) in einer
Ordner-Baumstruktur. **Alle Dateiformate** (PDF, Fotos, Word, Excel, Zeichnungen …) außer
Programmdateien. Für **jeden angemeldeten PC-Nutzer** (Session + CSRF wie alle `/api/**`-Endpunkte),
soweit die **Ordnerrechte** es erlauben (siehe unten). Nicht in der Handy-App.

Alle Fehler kommen als `ApiError` `{ status, message, fields }`; `message` ist ein deutscher Satz
für die Nutzer.

## Ordnerrechte je Abteilung

Rechte gibt es nur am **obersten Ordner**; Unterordner und Dokumente erben sie.

- `eingeschraenkt = false` (Standard, wie bisher): alle angemeldeten Benutzer sehen und ändern den Zweig.
- `eingeschraenkt = true`: nur die eingetragenen Abteilungen, je Abteilung `ANSEHEN` oder `AENDERN`.
  Ohne Eintrag (oder `KEINE`) sieht die Abteilung den Zweig gar nicht. Keine Abteilung eingetragen = nur Admins.
- **Admins** sehen und ändern immer alles. Ist jemand in mehreren Abteilungen, gilt die **höchste** Stufe.
- Die Abteilungen eines Benutzers kommen wie bei den übrigen Abteilungsrechten über sein aktives Profil →
  aktiver Mitarbeiter (Art Mensch) → dessen Abteilungen. Benutzer ohne verknüpften Mitarbeiter oder ohne
  Abteilung sehen nur die nicht eingeschränkten Zweige.
- **Ansehen** = Ordner und Dokumente sehen, Dateien öffnen/herunterladen. **Ändern** = zusätzlich hochladen,
  Dokumente bearbeiten/verschieben/löschen und Unterordner anlegen/umbenennen/verschieben/löschen.
- Nicht sichtbare Ordner und Dokumente verhalten sich wie „gibt es nicht“ (404 bzw. bei Zielordnern 400
  „… gibt es nicht mehr“). Wer nur ansehen darf und etwas ändern will, bekommt
  **403 „Du darfst in diesem Ordner nichts ändern.“**
- **Verschieben aus einem eingeschränkten Zweig:** Liegt ein Ordner oder Dokument in einem eingeschränkten Zweig
  und käme es durch das Verschieben unter einen **anderen** obersten Ordner, darf das nur ein Admin
  (sonst **403 „Aus einem eingeschränkten Ordner kann nur ein Admin in einen anderen Hauptordner verschieben.“**).
  So kann niemand mit „Ändern“-Recht die Einschränkung aufheben, indem er den Ordner in einen offenen Zweig legt.
  Für alle mit „Ändern“ erlaubt bleiben: Verschieben innerhalb desselben Zweigs und einen Unterordner nach ganz
  oben holen (er übernimmt die Rechte, es wird also nichts gelockert).
- **Bekannte Grenze:** Beim Anlegen oder Umbenennen eines obersten Ordners meldet „Hier gibt es schon einen
  Ordner …“ auch einen gleichnamigen Ordner, den man selbst nicht sieht. Den Namen eines versteckten obersten
  Ordners kann man so erraten (nicht seinen Inhalt). Das nehmen wir bewusst hin.
- Gespeichert in `firmendokument_ordner.rechte_eingeschraenkt` und `firmendokument_ordner_recht`
  (Migration V446). Wird ein Ordner oder eine Abteilung gelöscht, verschwinden ihre Einträge mit.

## Ordner

### `GET /api/firmendokumente/ordner`
Alle **sichtbaren** Ordner (höchstens 2000 werden geladen), sortiert nach Name.

```json
[
  { "id": 1, "name": "Prüfungen", "parentId": null,
    "anzahlDokumente": 3, "anzahlUeberfaellig": 1, "anzahlBaldFaellig": 0,
    "darfAendern": true, "eingeschraenkt": false }
]
```
- `anzahlDokumente`, `anzahlUeberfaellig`, `anzahlBaldFaellig` zählen nur Dokumente **direkt** in
  diesem Ordner (Unterordner nicht mitgezählt; das Frontend summiert bei Bedarf).
- überfällig = `faelligAm` < heute; bald fällig = heute ≤ `faelligAm` ≤ heute + 30 Tage.
- `darfAendern`: Dokumente hochladen/ändern/löschen, Unterordner anlegen/umbenennen/verschieben/löschen.
- `eingeschraenkt`: `true`, wenn der oberste Ordner dieses Zweigs eingeschränkt ist (Schloss-Symbol;
  Admins sehen es auch).
- Neue Installationen und bestehende ohne Ordner bekommen fünf Startordner:
  „Arbeitsschutz“, „Prüfungen“, „Versicherungen“, „Verträge“, „Zertifikate & Nachweise“.

### `POST /api/firmendokumente/ordner` → 201 + Ordner
Body `{ "name": "Tore", "parentId": 1 }` (`parentId` `null` = oberste Ebene).
- Oberste Ebene: darf jeder; der neue Ordner ist nicht eingeschränkt.
- 400 „Es gibt schon 2000 Ordner. Bitte zuerst leere Ordner löschen.“ – mehr Ordner lädt der Baum nicht.
- Unterordner: braucht `darfAendern` am Elternordner (sonst 403).
- 400 „Bitte einen Namen für den Ordner eingeben.“ (leer), „Der Name darf höchstens 200 Zeichen lang sein.“
- 400 „Den übergeordneten Ordner gibt es nicht mehr.“ (auch, wenn er nicht sichtbar ist)
- 400 „Hier gibt es schon einen Ordner „Tore“.“ (Name ohne Groß/Klein gleich)

### `PUT /api/firmendokumente/ordner/{id}` → 200 + Ordner
Body `{ "name": "Tore & Türen" }` – umbenennen, braucht `darfAendern`. Fehler wie oben,
404 „Den Ordner gibt es nicht mehr.“ wenn Ordner fehlt oder nicht sichtbar, 403 ohne Ändern-Recht.

### `PUT /api/firmendokumente/ordner/{id}/verschieben` → 200 + Ordner
Body `{ "parentId": 5 }` oder `{ "parentId": null }`. Braucht `darfAendern` am Ordner **und** am Ziel
(Ziel `null` = oberste Ebene ist immer erlaubt).
- Wird ein Unterordner auf die oberste Ebene geholt, übernimmt er Schalter und Einträge seines bisherigen
  obersten Ordners (wer vorher nichts sah, sieht auch danach nichts).
- Wird ein oberster Ordner in einen anderen verschoben, gelten danach die Rechte des neuen obersten Ordners;
  seine eigenen Einträge werden gelöscht. Ist er eingeschränkt, darf das nur ein Admin (siehe oben).
- 403 „Aus einem eingeschränkten Ordner kann nur ein Admin in einen anderen Hauptordner verschieben.“
  (Nicht-Admin legt einen Ordner aus einem eingeschränkten Zweig unter einen anderen obersten Ordner)
- 400 „Ein Ordner kann nicht in sich selbst oder einen seiner Unterordner verschoben werden.“
- 400 „Den Zielordner gibt es nicht mehr.“ (auch, wenn er nicht sichtbar ist), 400 Namensdoppel wie oben.
- 404 wenn Ordner fehlt oder nicht sichtbar, 403 ohne Ändern-Recht an Ordner oder Ziel.

### `DELETE /api/firmendokumente/ordner/{id}` → 204
Braucht `darfAendern`; die Rechte-Einträge des Ordners werden mitgelöscht.
- 400 „Der Ordner ist nicht leer. Bitte zuerst die Dokumente und Unterordner verschieben oder löschen.“
- 404 wenn Ordner fehlt oder nicht sichtbar, 403 ohne Ändern-Recht.

### `GET /api/firmendokumente/ordner/{id}/rechte` *(nur Admin, sonst 403)*
```json
{ "ordnerId": 3, "ordnerName": "Versicherungen", "eingeschraenkt": true,
  "abteilungen": [
    { "abteilungId": 10, "name": "Büro", "stufe": "AENDERN" },
    { "abteilungId": 12, "name": "Montage", "stufe": "KEINE" },
    { "abteilungId": 11, "name": "Werkstatt", "stufe": "ANSEHEN" }
  ] }
```
- `abteilungen`: **alle** Abteilungen (höchstens 500), nach Name; ohne Eintrag `KEINE`.
- 404 „Den Ordner gibt es nicht mehr.“
- 400 „Rechte werden am obersten Ordner eingestellt. Sie gelten auch für alle Unterordner.“ wenn `{id}`
  kein oberster Ordner ist.

### `PUT /api/firmendokumente/ordner/{id}/rechte` *(nur Admin, sonst 403)* → 200 wie GET
Body `{ "eingeschraenkt": true, "abteilungen": [{ "abteilungId": 10, "stufe": "AENDERN" }] }`
- Fehlende Abteilungen im Body und `KEINE` = kein Eintrag (wird gelöscht).
- Bei `eingeschraenkt=false` werden die Einträge trotzdem gespeichert (bleiben für späteres Einschalten
  erhalten), wirken aber nicht.
- 404 / 400 „oberster Ordner“ wie bei GET.
- 400 „Bitte angeben, ob der Ordner eingeschränkt ist.“ (`eingeschraenkt` fehlt)
- 400 „Die Abteilung gibt es nicht mehr.“ (unbekannte oder fehlende `abteilungId`)
- 400 „Bitte für jede Abteilung „Kein Zugriff“, „Ansehen“ oder „Ändern“ wählen.“ (Stufe fehlt oder unbekannt)
- 400 „Jede Abteilung darf nur einmal vorkommen.“, 400 bei mehr als 500 Einträgen.
- 409 „Jemand anders hat die Rechte gerade gespeichert. Bitte neu laden.“ (zwei Admins speichern gleichzeitig)

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
  "url": "/api/firmendokumente/7/datei",
  "dateiart": "PDF",
  "darfAendern": true
}
```
- `hochgeladenVon` und `faelligAm` können `null` sein.
- `dateiart` aus der Endung des Originalnamens: `PDF` (pdf), `BILD` (png, jpg, jpeg, gif, webp, bmp),
  sonst `SONSTIGE` (auch svg und html – die können Skripte enthalten). Dokumente aus der Zeit vor allen
  Formaten (gespeichert als `<UUID>.pdf`) sind immer `PDF`.
- `darfAendern`: darf der Angemeldete das Dokument bearbeiten, verschieben und löschen.

Seiten-JSON (Listen):
```json
{ "eintraege": [ /* Dokumente */ ], "gesamt": 120, "seite": 0, "seitenGroesse": 50 }
```
`page` ab 0 (negativ → 0), `size` Standard 50, höchstens 50.

### `GET /api/firmendokumente?ordnerId=1&page=0&size=50`
Dokumente direkt in einem Ordner, sortiert nach Name (ohne Groß/Klein), dann id.
`ordnerId` Pflicht; 404 „Den Ordner gibt es nicht mehr.“ wenn Ordner fehlt oder nicht sichtbar.

### `GET /api/firmendokumente/suche?q=tor&page=0&size=50`
Sucht in Name und Original-Dateiname über alle **sichtbaren** Ordner (ohne Groß/Klein), sortiert nach Name.
- 400 „Bitte mindestens 2 Zeichen eingeben.“ / „Der Suchbegriff darf höchstens 100 Zeichen lang sein.“

### `GET /api/firmendokumente/faellig?page=0&size=50`
Überfällige und bald fällige Dokumente (`faelligAm` ≤ heute + 30 Tage) aus **sichtbaren** Ordnern,
sortiert nach `faelligAm` aufsteigend, dann Name.

### `POST /api/firmendokumente` (multipart) → 201 + Dokument
Felder: `datei` (Pflicht, jedes Format bis 50 MB außer Programmdateien), `ordnerId` (Pflicht),
`name` (Pflicht, ≤ 200), `faelligAm` (optional, `yyyy-MM-dd`). Braucht `darfAendern` am Zielordner.
- 400 „Bitte eine Datei auswählen.“ (leer)
- 400 „Die Datei ist größer als 50 MB. Bitte eine kleinere Datei wählen.“
- 400 „Programmdateien (z. B. .exe oder .bat) können aus Sicherheitsgründen nicht abgelegt werden.“ –
  gesperrt sind die Endungen exe, msi, msc, msp, mst, bat, cmd, com, scr, pif, ps1, ps1xml, psm1, psd1, vbs,
  vbe, js, jse, wsf, wsh, hta, jar, dll, cpl, lnk, reg, scf, url, website, chm, inf, gadget, application,
  appref-ms, settingcontent-ms, library-ms, xbap, iso, img, vhd, vhdx, sh, command, app, pkg, dmg.
  Es zählt die letzte Endung des Originalnamens (Groß/Klein egal, auch „rechnung.pdf.exe“; Punkte/Leerzeichen
  am Ende werden ignoriert; eine Endung sind bis zu 20 Buchstaben/Ziffern, auch mit Bindestrich wie
  „appref-ms“). Der Inhalt wird nicht geprüft. **Office-Dateien mit Makros (docm, xlsm, pptm) bleiben
  erlaubt:** Handwerksbetriebe arbeiten mit Kalkulations-xlsm, und Office warnt beim Öffnen selbst vor Makros.
- 400 „Der Name darf nicht auf eine Programmendung wie .exe enden.“ (Dokumentname, z. B. „Rechnung.exe“ –
  sonst käme die Datei als .exe zum Download; gilt auch beim Ändern)
- 400 „Bitte einen Namen für das Dokument eingeben.“ / Länge wie oben
- 400 „Den Ordner gibt es nicht mehr.“ (fehlt oder nicht sichtbar)
- 403 „Du darfst in diesem Ordner nichts ändern.“ (nur Ansehen-Recht)
- Ablage: `uploads/firmendokumente/<UUID>.bin` – die echte Endung steht nur im Originalnamen in der
  Datenbank, nie im Dateisystem.

### `PUT /api/firmendokumente/{id}` → 200 + Dokument
Body `{ "name": "…", "ordnerId": 3, "faelligAm": "2027-10-31" }` – alle drei Felder immer mitschicken
(`faelligAm: null` löscht das Datum). Umbenennen, Verschieben und Datum in einem. Braucht `darfAendern`
am bisherigen und (beim Verschieben) am neuen Ordner.
Fehler wie beim Hochladen (auch der Name mit Programmendung), 404 wenn Dokument fehlt oder nicht sichtbar,
403 ohne Ändern-Recht, 403 „Aus einem eingeschränkten Ordner kann nur ein Admin in einen anderen Hauptordner
verschieben.“ beim Verschieben aus einem eingeschränkten Zweig unter einen anderen obersten Ordner.

### `DELETE /api/firmendokumente/{id}` → 204
Löscht Eintrag und Datei endgültig (braucht `darfAendern`). 404 wenn Dokument fehlt oder nicht sichtbar,
403 ohne Ändern-Recht.

### `GET /api/firmendokumente/{id}/datei[?download=true]`
- `PDF` → `application/pdf`, `BILD` → passendes `image/*`; ohne `download` `Content-Disposition: inline`,
  mit `download=true` `attachment`.
- `SONSTIGE` → immer `application/octet-stream` + `attachment` (auch ohne `download=true`), damit der
  Browser z. B. eine HTML-Datei nie selbst öffnet.
- Immer mit `X-Content-Type-Options: nosniff` und `Content-Security-Policy: sandbox; default-src 'none'`.
- Dateiname = `name` + Endung des Originalnamens, wenn sie nicht schon dransteht (z. B. „Haftpflicht 2026.docx“);
  ohne Endung kein Anhängsel. Endet der Name selbst auf eine Programmendung (alte Daten), wird deren Punkt
  zu „_“ („Rechnung.exe“ → „Rechnung_exe.pdf“).
- „Ansehen“ reicht. 404 wenn Dokument oder Datei fehlt oder das Dokument nicht sichtbar ist.
