# Digitale Freigabe: Schnittstelle zwischen Webseite und ERP

> **Stand:** Oktober 2026. Diese Datei war ursprünglich ein Umsetzungs-Auftrag für die Freigabe-Seite der Firmen-Webseite. Das Feature ist im ERP fertig umgesetzt; die Datei beschreibt jetzt den **Ist-Stand der Schnittstelle**, die die Webseite nutzt. Wie der Ablauf im ERP aussieht (Versand, Plaketten, Beweissicherung), steht in [ANGEBOTSWESEN.md](../ANGEBOTSWESEN.md), Abschnitt 4.

---

## 1 · Worum es geht

Kunden nehmen ein **Angebot**, **Nachtragsangebot** oder eine **Auftragsbestätigung** digital an. Das ERP ist nicht öffentlich erreichbar; deshalb zeigt die Firmen-Webseite die Freigabe-Seite an und holt sich alle Daten serverseitig beim ERP.

```
Kunde (Browser)
   │  https://<eure-domain>/freigabe/<uuid>
   ▼
Webseiten-Server  ── Server-zu-Server, Header X-ERP-Website-Key ──►  ERP  /api/internal/freigabe/...
```

**Grundregeln für die Webseite:**
- Der Browser ruft **nie** das ERP direkt auf. Browser → Webseite → ERP, immer.
- Die PDF wird über eine eigene Route der Webseite durchgereicht, nicht über `pdfPfad`.
- Keine Caching-Header auf der Freigabe-Seite und der PDF – der Status kann sich jederzeit ändern.
- IP-Adresse und User-Agent ermittelt der Webseiten-Server aus den Request-Headern und schickt sie mit; im Browser wird davon nichts gespeichert.

---

## 2 · Verbindung und Konfiguration

**Webseite → ERP:** Server-zu-Server über das Tailscale-Netz. Jeder Aufruf trägt den Header `X-ERP-Website-Key` mit dem gemeinsamen Schlüssel (Webseite: `ERP_INTERNAL_API_KEY`, ERP: `website.internal-api.key` bzw. `WEBSITE_INTERNAL_API_KEY`). Ohne gültigen Schlüssel antwortet das ERP mit 401. Aufrufe aus dem Internet (auch über Tailscale Funnel) weist die Netzgrenze des ERP ab. Cloudflare Access ist nur noch ein Sonderfall und derzeit nicht nutzbar. Einrichtung: [ANFRAGE_FUNNEL_SETUP.md](../ANFRAGE_FUNNEL_SETUP.md), Details: [MOBILE_TOKEN_SECURITY.md](../MOBILE_TOKEN_SECURITY.md#website-schnittstelle-apiinternal).

Wenn die Konfiguration auf der Webseite unvollständig ist, antwortet sie selbst mit 500 und loggt nur den Hinweis, nie die Werte.

**ERP-Einstellungen:**

| Property | Zweck |
|---|---|
| `freigabe.public-base-url` | Öffentliche Adresse der Webseite. Der Link in der E-Mail lautet `<public-base-url>/freigabe/<uuid>`. **Ohne Eintrag gehen Angebote und Auftragsbestätigungen ohne Annahme-Link hinaus** (Warnung beim Start). |
| `freigabe.hash.salt` | Geheimer Wert für die Prüf-Hashes und den Code-HMAC. In jeder Installation einen eigenen Wert setzen (nur in `application-local.properties` bzw. der Umgebung). |

---

## 3 · Ablauf auf der Freigabe-Seite

1. Kunde öffnet den Link → Webseite holt `GET /api/internal/freigabe/{uuid}` und zeigt je nach `status` die passende Ansicht.
2. Bei `PENDING`: Dokument anzeigen (PDF über den Proxy der Webseite, dazu die Positionen), Alternativen zur Auswahl anbieten, Vor- und Nachname abfragen, Bestätigungs-Checkbox.
3. Klick auf „Freigeben“ → `POST /api/internal/freigabe/{uuid}/code`: Das ERP schickt einen **6-stelligen Bestätigungscode** an die Adresse, an die das Dokument ging, und meldet die maskierte Adresse zurück.
4. Kunde gibt den Code ein → `POST /api/internal/freigabe/{uuid}/akzeptieren`.
5. Erfolg → Quittung mit `akzeptiertAm`, `unterzeichnerName` und `hashAcceptance` anzeigen.

Nach der Annahme eines Angebots oder Nachtragsangebots sperrt das ERP das Dokument, legt automatisch eine Auftragsbestätigung an (bei einer Anfrage vorher ein Projekt) und schickt sie dem Kunden.

**Schutzregeln für den Code:** 15 Minuten gültig; ein neuer Code macht den alten ungültig; neuer Code frühestens nach 30 Sekunden, höchstens 5 Codes pro Stunde; nach 5 Fehlversuchen geht automatisch ein neuer Code raus; nach insgesamt 15 Fehlversuchen ist die Online-Freigabe gesperrt, das Büro bekommt eine Push-Nachricht. Gespeichert wird nur ein HMAC des Codes.

---

## 4 · Endpunkte im ERP

Alle Pfade liegen unter `<ERP-Adresse>/api/internal/freigabe/...` und verlangen `X-ERP-Website-Key`.

### 4.1 `GET /{uuid}` – Daten für die Seite

**200 — `FreigabeAnsichtDto`:**

```ts
{
  uuid: string;
  status: "PENDING" | "ACCEPTED" | "EXPIRED" | "REVOKED";
  dokumentNummer: string;
  dokumentArt: string;              // z. B. "Angebot", "Auftragsbestätigung"
  dokumentBetrag: number | null;    // Brutto in EUR
  bauvorhaben: string | null;
  kundeName: string | null;
  kundeEmail: string | null;        // Adresse, an die der Link ging
  erstelltAm: string;               // ISO-LocalDateTime
  ablaufDatum: string;              // ISO-LocalDateTime
  akzeptiertAm: string | null;      // nur bei ACCEPTED
  abgelaufen: boolean;
  pdfPfad: string;                  // "/api/internal/freigabe/{uuid}/pdf" – nicht im Browser nutzen
  positionen: FreigabePositionDto[] | null;  // null bei Alt-Freigaben ohne Positionen
  basisNetto: number | null;        // Summe ohne Alternativen (nach Rabatt)
  basisBrutto: number | null;
  mwstProzent: number | null;       // für die Live-Berechnung der Endsumme
  hatAlternativen: boolean;
  globalRabattProzent: number | null;
}
```

`FreigabePositionDto`: `blockId` (für die Auswahl zurückschicken), `typ` (`SERVICE`, `SECTION_HEADER`, `TEXT`, `SUBTOTAL`, `SEPARATOR`, `CLOSURE`), `pos`, `bezeichnung`, `beschreibungHtml` (HTML – sicher rendern), `menge`, `einheit`, `einzelpreisNetto`, `rabattProzent`, `gesamtpreisNetto`, `optional` (Alternative), `alternativGruppe` (gesetzt = genau eine Variante der Gruppe wählen; leer = frei dazubuchbar), `sectionLabel`, `children`.

**404:** UUID unbekannt → „Link nicht gefunden“. Abgelaufene Links stellt das ERP beim Abruf selbst auf `EXPIRED`.

### 4.2 `GET /{uuid}/pdf` – PDF

**200:** `Content-Type: application/pdf`, `Content-Disposition: inline; filename="..."`.
**404:** keine PDF vorhanden. **410:** Freigabe abgelaufen oder zurückgezogen.

### 4.3 `DELETE /{uuid}/pdf` – PDF vom Link lösen

Für abgelaufene Links: Die PDF wird vom Link gelöst und danach nicht mehr ausgeliefert. Die Datei selbst bleibt im ERP, sie ist die PDF des Dokuments.

**204:** erledigt (Freigabe abgelaufen oder zurückgezogen). **409:** Freigabe ist noch offen oder angenommen, nichts geändert. **404:** unbekannte UUID.

### 4.4 `POST /{uuid}/code` – Bestätigungscode anfordern

**200:** `{ success: true, emailMaskiert: string, gueltigBis: string, erneutAnfordernInSekunden: number }`

| Status | Bedeutung |
|---|---|
| 404 | UUID unbekannt |
| 409 | keine E-Mail-Adresse hinterlegt |
| 410 | Freigabe nicht mehr offen |
| 423 | Online-Freigabe gesperrt (zu viele Fehlversuche) |
| 429 | zu früh für einen neuen Code oder Versand läuft gerade (`erneutAnfordernInSekunden`) |
| 502 | Versand der Mail gescheitert |

Fehlerantworten tragen `success: false` und eine `message`, die man dem Kunden zeigen kann. Die Code-Mail geht über das Hauptpostfach raus.

### 4.5 `POST /{uuid}/akzeptieren` – verbindlich annehmen

**Body — `FreigabeAkzeptierenRequest`:**

```ts
{
  vorname: string;                 // Pflicht, 2–80 Zeichen
  nachname: string;                // Pflicht, 2–80 Zeichen
  unterzeichnerName: string;       // Pflicht, 2–160 Zeichen ("Vorname Nachname")
  code: string;                    // Pflicht, Code aus der E-Mail (Leerzeichen/Bindestriche egal)
  bestaetigung: boolean;           // MUSS true sein
  email?: string;                  // optional
  clientIp: string;                // vom Webseiten-Server ermittelt
  userAgent: string;               // vom Webseiten-Server ermittelt (max. 500 Zeichen)
  ausgewaehlteAlternativen?: string[];  // blockIds der gewählten Alternativen
}
```

Den verbindlichen Betrag rechnet das ERP aus der Auswahl selbst neu; Beträge vom Browser werden nicht übernommen.

**200 — `FreigabeAkzeptiertResponse`:** `{ uuid, dokumentNummer, dokumentArt, akzeptiertAm, hashAcceptance, unterzeichnerName }`

| Status | Bedeutung |
|---|---|
| 400 | `bestaetigung` nicht `true` („Bitte bestätigen Sie, dass Sie das Dokument geprüft haben.“) oder Pflichtfeld fehlt/zu kurz |
| 404 | UUID unbekannt |
| 410 | abgelaufen oder zurückgezogen |

Ist die Freigabe schon angenommen (z. B. Doppelklick), antwortet das ERP mit 200 und den Daten der **ersten** Annahme.
| 422 | Code-Fehler: `codeFehler` (`FALSCH`, `ABGELAUFEN`, `NEUER_CODE_VERSCHICKT`, `GESPERRT`, …), `verbleibendeVersuche`, ggf. `erneutAnfordernInSekunden`, `emailMaskiert` |

`hashAcceptance` ist ein SHA-256 über den Hash des versendeten Dokuments, UUID, IP, E-Mail, Zeitpunkt, Unterzeichner, gewählte Alternativen, verbindlichen Betrag, die Code-Bestätigung und den serverseitigen Salt. Der Kunde sieht ihn als unveränderbare Quittung; die Webseite reicht ihn nur durch.

---

## 5 · Was die Seite dem Kunden zeigen muss

1. Bestätigungs-Checkbox (`bestaetigung`) – ohne aktiven Haken lehnt das ERP mit 400 ab.
2. Hinweis, dass IP-Adresse, Browser-Kennung, Name und Zeitpunkt gespeichert werden (Datenschutz).
3. Der Knopf macht klar, dass die Annahme verbindlich ist (z. B. „verbindlich annehmen“).
4. Nach erfolgreicher Annahme `akzeptiertAm`, `unterzeichnerName` und `hashAcceptance` als Quittung.
5. Je Status eine eigene Ansicht: `PENDING` (Annahme), `ACCEPTED` (schon angenommen), `EXPIRED` (abgelaufen), `REVOKED` (zurückgezogen), 404 (Link nicht gefunden).

---

## 6 · Link in der E-Mail

Beim Versand eines Angebots, Nachtragsangebots oder einer Auftragsbestätigung aus dem Dokument-Editor fragt das ERP „Wie lange soll der Annahme-Link gültig sein?“ (Schnellwahl 7, 14, 21 oder 30 Tage oder ein Datum; Standard 14 Tage). Der Link `<freigabe.public-base-url>/freigabe/<uuid>` steht als Knopf mit Klartext-Ersatz in der Mail, darunter das Ablaufdatum.
