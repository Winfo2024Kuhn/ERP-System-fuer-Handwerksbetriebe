# Anfrage-Funnel: Setup-Anleitung

Verbindet die Marketing-Webseite (öffentlicher 3-Schritt-Funnel) mit dem ERP.
Die Webseite leitet eingehende Anfragen Server-zu-Server an das ERP weiter; das
ERP legt automatisch einen Kunden + eine Anfrage + eine Bautagebuch-fähige
Notiz mit den Funnel-Bildern an.

## Architektur (Kurzform)

```
Besucher → Website-Server (Funnel-Form, Validierung, Captcha optional)
              │  POST  https://erp.eure-domain.de/api/internal/anfrage
              │  Header: CF-Access-Client-Id, CF-Access-Client-Secret
              ▼
       Cloudflare Access (validiert Service Token)
              │  durch den Tunnel
              ▼
       Cloudflare Tunnel  →  ERP (LAN, kein offener Port)
              │  Header: Cf-Access-Jwt-Assertion
              ▼
       CloudflareAccessJwtFilter (Defense-in-Depth)
              ▼
       AnfrageFunnelController → AnfrageFunnelService
              ▼
       Kunde + Anfrage + AnfrageNotiz (mit Bildern)
```

Eingang ist nur erreichbar, wenn der Service Token gültig ist. Im LAN ist der
Spring-Port nicht öffentlich – Anfragen kommen ausschließlich durch den Tunnel.

---

## Schritt 1 — Cloudflare Zero Trust einrichten

> Free-Tier reicht (bis 50 User). Dashboard:
> [https://one.dash.cloudflare.com](https://one.dash.cloudflare.com)

### 1a. Tunnel anlegen

1. **Networks → Tunnels → Create a tunnel**
2. Name: `erp-tunnel`
3. `cloudflared` auf einem Rechner im LAN installieren (am ERP-Server selbst
   oder einer kleinen VM daneben).
4. **Public hostname** hinzufügen:
   - Subdomain: `erp`
   - Domain: `eure-domain.de`
   - Service: `HTTP` → `localhost:8080`
5. (Optional) Zweiten Hostname `erp-admin.eure-domain.de` für die Admin-UI
   anlegen, mit eigener Access-Policy (E-Mail-Login eurer Mitarbeiter).

### 1b. Access Application für /api/internal/\*

1. **Access → Applications → Add an application → Self-hosted**
2. Name: `ERP Funnel-Eingang`
3. Application domain: `erp.eure-domain.de`
4. Path: `/api/internal/*`
5. Session duration: `24h` (egal, bei Service Tokens irrelevant)
6. **Identity providers:** Service Token-Login zulassen, alles andere abschalten
7. Speichern → **AUD-Tag** notieren (in `Settings → Overview` der Application)

### 1c. Service Token erstellen

1. **Access → Service Auth → Service Tokens → Create Service Token**
2. Name: `Website-Server → ERP`
3. Duration: ohne Ablauf oder z.B. 1 Jahr (rotierbar)
4. **Client-ID + Client-Secret notieren** (Secret wird nur einmal angezeigt!)

### 1d. Policy auf der Application setzen

1. Zurück zur Application aus Schritt 1b → **Policies → Add a policy**
2. Action: `Service Auth`
3. Include: `Service Token = Website-Server → ERP`
4. Speichern

---

## Schritt 2 — ERP-Server konfigurieren

### Environment-Variablen setzen

Auf dem ERP-Produktionsserver in der Service-/Container-Konfiguration:

```bash
CF_ACCESS_ENABLED=true
CF_ACCESS_TEAM_DOMAIN=<euer-team>.cloudflareaccess.com
CF_ACCESS_APPLICATION_AUD=<AUD-Tag aus Schritt 1b>
```

> **Lokal/Dev:** Variablen weglassen oder `CF_ACCESS_ENABLED=false`. Der Filter
> wird dann komplett umgangen, der Funnel-Endpoint ist intern offen erreichbar
> (z.B. zum Testen mit Postman).

### Datenbank-Migrationen

Beim nächsten Start des ERP laufen automatisch:

- `V221__system_mitarbeiter_webseite.sql` — legt Mitarbeiter „System Webseite"
  an (login_token = `__SYSTEM_FUNNEL__`, aktiv = false → taucht nicht in
  Auswahl-Listen auf).
- `V222__kunden_zaehler.sql` — Singleton-Counter-Tabelle für atomare,
  fortlaufende Kundennummern (verhindert Doppel-Vergabe bei parallelen
  Anfragen).

Beide Skripte sind idempotent.

### Spring-Port nicht direkt exponieren

Der ERP-Port (`8080`) darf **ausschließlich** über den Cloudflare-Tunnel
erreichbar sein. Keine Port-Forwarding-Regel im Router, keine Firewall-Freigabe
nach außen. Im Zweifel mit Port-Scan von außen prüfen.

---

## Schritt 3 — Website-Server konfigurieren

### Geheimnisse hinterlegen

Auf dem Webseiten-Server in `.env` (oder Equivalent), **niemals ins Repo committen**:

```
CF_ACCESS_CLIENT_ID=<Client-ID aus Schritt 1c>
CF_ACCESS_CLIENT_SECRET=<Client-Secret aus Schritt 1c>
ERP_FUNNEL_URL=https://erp.eure-domain.de/api/internal/anfrage
```

### Funnel-Submit weiterleiten

Beim Submit des Funnels (Schritt 3 „Unverbindlich anfragen"):

1. **Auf dem Webseiten-Server** das Formular validieren (Captcha,
   Rate-Limit, Honeypot — was immer ihr da habt).
2. Multipart-Request an `ERP_FUNNEL_URL` schicken:

**Header:**
```
CF-Access-Client-Id: <id>
CF-Access-Client-Secret: <secret>
Content-Type: multipart/form-data
```

**Multipart-Parts:**
- `anfrage` (JSON-String) — siehe Schema unten
- `bilder` (Datei, optional, mehrfach) — JPG/PNG/GIF/WebP, max. 5 MB

**JSON-Schema für `anfrage`:**
```json
{
  "serviceTyp": "Neubau",
  "projektarten": ["Wohnhaus", "Sanierung", "Dachsanierung"],
  "nachricht": "Ich hätte gerne ein Angebot für...",
  "vorname": "Max",
  "nachname": "Mustermann",
  "email": "max@example.de",
  "telefon": "0170 1234567",
  "projektAnschrift": "Kleistraße 11, 97072 Würzburg",
  "rechnungsAnschrift": "Hauptstraße 5, 80331 München",
  "rechnungsAnschriftGleichProjekt": false,
  "datenschutzAkzeptiert": true,
  "consentIp": "1.2.3.4",
  "datenschutzVersion": "2026-01"
}
```

**Pflichtfelder:** `serviceTyp`, `nachricht`, `vorname`, `nachname`, `email`,
`datenschutzAkzeptiert` (muss `true` sein, sonst HTTP 400).

**Adress-Trennung:**
- `projektAnschrift` → landet auf `Anfrage.projektStrasse/Plz/Ort`
  (= „Wo wird gebaut?")
- `rechnungsAnschrift` → landet auf `Kunde.strasse/plz/ort`
  (= „Wohin geht die Rechnung?")
- `rechnungsAnschriftGleichProjekt: true` → `rechnungsAnschrift` wird
  ignoriert, stattdessen wird `projektAnschrift` als Rechnungsadresse
  übernommen.

**Antwort bei Erfolg (HTTP 201):**
```json
{ "success": true, "anfrageId": 1234, "message": "Anfrage erfolgreich angelegt." }
```

**Antwort bei Validierungsfehler (HTTP 400):**
```json
{ "success": false, "message": "<Fehlerbeschreibung>" }
```

---

## Schritt 4 — Verifikation

### 4a. Lokal (ohne Cloudflare)

`CF_ACCESS_ENABLED=false`, dann mit curl:

```bash
curl -X POST http://localhost:8080/api/internal/anfrage \
  -F 'anfrage={"serviceTyp":"Neubau","nachricht":"Test","vorname":"Max",
       "nachname":"Mustermann","email":"max@example.de",
       "datenschutzAkzeptiert":true};type=application/json' \
  -F 'bilder=@/pfad/zu/bild.jpg'
```

Erwartetes Ergebnis: HTTP 201 + `anfrageId`. Im ERP unter
**Anfragen** sollte eine neue Anfrage erscheinen, mit Notiz + Bildern.

### 4b. Produktiv (mit Cloudflare)

Vom Webseiten-Server aus:

```bash
curl -X POST https://erp.eure-domain.de/api/internal/anfrage \
  -H "CF-Access-Client-Id: $CF_ACCESS_CLIENT_ID" \
  -H "CF-Access-Client-Secret: $CF_ACCESS_CLIENT_SECRET" \
  -F 'anfrage={...};type=application/json'
```

Erwartetes Ergebnis: HTTP 201.
Ohne korrekte Header: HTTP 401 von Cloudflare oder vom JWT-Filter.

### 4c. Negativ-Tests

- Falsches Service-Secret → 401 (Cloudflare blockiert)
- Direkter Zugriff aus dem LAN auf `http://erp-server:8080/api/internal/anfrage`
  ohne JWT (CF_ACCESS_ENABLED=true): 401 (CloudflareAccessJwtFilter blockiert)
- `datenschutzAkzeptiert=false`: 400 mit Validierungsfehler

---

## Was im ERP passiert

1. **Kunde:** Wird über E-Mail-Match wiederverwendet, sonst neu angelegt mit
   atomarer Kundennummer (Counter-Tabelle).
2. **Anfrage:** `bauvorhaben` = `<ServiceTyp> - <Projektarten>` (z.B.
   „Neubau - Wohnhaus, Sanierung"), `kurzbeschreibung` enthält den Funnel-Text.
3. **AnfrageNotiz:** Erstellt vom System-Mitarbeiter „System Webseite" mit dem
   vollständigen Funnel-Inhalt + Datenschutz-Consent (Datum + IP + Version).
   Hochgeladene Bilder werden als `AnfrageNotizBild` mit gespeichert.
4. **Beim späteren Anfrage→Projekt-Wandeln** transferiert das ERP die Notiz
   automatisch ins **Bautagebuch** des neuen Projekts (inkl. Bilder + Datum).

---

## Token-Rotation

Service Token rotieren wir im **Cloudflare Zero Trust Dashboard → Service Auth**:

1. Neuen Service Token erstellen.
2. Auf dem Webseiten-Server `CF_ACCESS_CLIENT_ID` + `CF_ACCESS_CLIENT_SECRET`
   updaten und Service neu starten.
3. Sobald sicher: alten Token im Dashboard löschen.

Auf der ERP-Seite ist **keine Aktion** nötig (das Application-AUD bleibt gleich).

---

## Troubleshooting

| Symptom | Ursache | Lösung |
|---|---|---|
| HTTP 401 sofort, kommt nicht im ERP-Log an | Cloudflare blockiert | Service-Token-Header prüfen, AUD-Tag in Application korrekt? |
| HTTP 401 mit `CF-Access-JWT fehlt` im ERP-Log | Tunnel reicht keinen JWT durch | Access Application für den Pfad `/api/internal/*` aktiv? |
| HTTP 401 mit `CF-Access-JWT ungültig` | `team-domain` oder `aud` falsch konfiguriert | ENV-Variablen `CF_ACCESS_TEAM_DOMAIN` / `CF_ACCESS_APPLICATION_AUD` prüfen |
| HTTP 400 `Datenschutz muss akzeptiert werden` | Checkbox nicht durchgereicht | `datenschutzAkzeptiert: true` in JSON setzen |
| HTTP 500, Log: „System-Mitarbeiter 'Webseite' nicht gefunden" | Migration V221 nicht gelaufen | ERP neu starten, Flyway-Status prüfen |
| Bilder fehlen in der Notiz | Falscher Multipart-Part-Name | Part-Name muss exakt `bilder` heißen, MIME-Typ jpg/png/gif/webp |

---

## Code-Referenz

| Datei | Zweck |
|---|---|
| [`AnfrageFunnelController`](../src/main/java/org/example/kalkulationsprogramm/controller/AnfrageFunnelController.java) | REST-Endpoint `/api/internal/anfrage` |
| [`AnfrageFunnelService`](../src/main/java/org/example/kalkulationsprogramm/service/AnfrageFunnelService.java) | Mapping Funnel → Kunde + Anfrage + Notiz |
| [`AnfrageFunnelRequestDto`](../src/main/java/org/example/kalkulationsprogramm/dto/Anfrage/AnfrageFunnelRequestDto.java) | Eingabe-Schema mit `@Valid` |
| [`KundennummerService`](../src/main/java/org/example/kalkulationsprogramm/service/KundennummerService.java) | Atomare Kundennummer-Vergabe |
| [`CloudflareAccessJwtFilter`](../src/main/java/org/example/kalkulationsprogramm/config/CloudflareAccessJwtFilter.java) | Defense-in-Depth JWT-Verifikation |
| [`SecurityConfig`](../src/main/java/org/example/kalkulationsprogramm/config/SecurityConfig.java) | Filter-Chain für `/api/internal/**` |
| [`V221__system_mitarbeiter_webseite.sql`](../src/main/resources/db/migration/V221__system_mitarbeiter_webseite.sql) | Seed System-Mitarbeiter |
| [`V222__kunden_zaehler.sql`](../src/main/resources/db/migration/V222__kunden_zaehler.sql) | Counter-Tabelle für Kundennummern |
