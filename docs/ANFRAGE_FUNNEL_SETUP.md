# Anfrage-Funnel: Setup-Anleitung

> **Stand:** Oktober 2026

Verbindet die Marketing-Webseite (öffentlicher 3-Schritt-Funnel) mit dem ERP.
Die Webseite leitet eingehende Anfragen Server-zu-Server an das ERP weiter; das
ERP legt automatisch einen Kunden + eine Anfrage + eine Bautagebuch-fähige
Notiz mit den Funnel-Bildern an, schickt dem Interessenten eine
Bestätigungsmail und meldet die neue Anfrage per Push aufs Handy.

Im ERP erscheinen die Anfragen unter **Projektmanagement › Aufträge › Anfragen**
und in der Handy-App auf der Seite „Anfragen“.

## Architektur (Kurzform)

```
Besucher → Website-Server (Funnel-Form, Validierung, Captcha optional)
              │  POST  <ERP-Adresse im Tailscale-Netz>/api/internal/anfrage
              │  Header: X-ERP-Website-Key
              ▼
       Tailscale (privates Netz, kein offener Port)
              ▼
       ERP hinter `tailscale serve`
              │  ZeiterfassungSecurityFilter: /api/internal/** nur aus LAN oder Tailnet
              │  WebsiteInternalApiKeyFilter: Schlüssel prüfen, sonst 401
              ▼
       AnfrageFunnelController → AnfrageFunnelService
              ▼
       Kunde + Anfrage + AnfrageNotiz (mit Bildern)
              ├─ Bestätigungsmail an den Interessenten (nach dem Speichern)
              ├─ Push-Nachricht „Neue Anfrage: <Name>“ aufs Handy
              └─ Spam-Prüfung im Hintergrund (nur Protokoll)
```

Die Schnittstelle `/api/internal/**` ist nur aus dem Firmen-LAN oder dem
Tailscale-Netz erreichbar und verlangt zusätzlich den gemeinsamen
Website-Schlüssel. Anfragen über **Tailscale Funnel** (öffentlich) gelten als
extern und werden abgewiesen – der Website-Server muss also selbst Mitglied im
Tailscale-Netz sein.

> **Cloudflare:** Der frühere Weg über Cloudflare Tunnel + Access ist derzeit
> nicht nutzbar: Die Netzgrenze erkennt die öffentliche Absender-IP und sperrt
> `/api/internal/**`, bevor der JWT geprüft wird. Die Einrichtung steht nur noch
> zur Referenz im [Anhang](#anhang-cloudflare-access-sonderfall).
> Hintergrund und Umstellungsreihenfolge:
> [MOBILE_TOKEN_SECURITY.md](MOBILE_TOKEN_SECURITY.md#website-schnittstelle-apiinternal).

---

## Schritt 1 — Verbindung einrichten (Tailscale + Schlüssel)

### 1a. Gemeinsamen Schlüssel erzeugen

```bash
openssl rand -base64 32
```

Den Wert sicher notieren. Er wird auf beiden Seiten eingetragen und **niemals
ins Repository** committet.

### 1b. Tailscale

1. ERP-Rechner und Website-Server ins selbe Tailscale-Netz (Tailnet) aufnehmen.
2. Das ERP hinter `tailscale serve` betreiben (Port nur auf `127.0.0.1`),
   siehe [deployment/docker-server/README.md](../deployment/docker-server/README.md)
   und [INSTALLATION.md](INSTALLATION.md).
3. Die ERP-Adresse im Tailnet ist die Adresse, an die die Website sendet.

---

## Schritt 2 — ERP-Server konfigurieren

### Umgebungsvariablen setzen

Auf dem ERP-Produktionsserver (Umgebung oder `application-local.properties`):

| Property | Umgebungsvariable | Wert |
|---|---|---|
| `website.internal-api.key` | `WEBSITE_INTERNAL_API_KEY` | der Schlüssel aus Schritt 1a |
| `cloudflare.access.enabled` | `CF_ACCESS_ENABLED` | `false` (Standard) |

- **Fail-closed:** Ist kein Schlüssel gesetzt, steht beim Start eine Warnung im
  Log und `/api/internal/**` antwortet auf jede Anfrage mit 401.
- **Reihenfolge bei der Umstellung:** zuerst die Website mit Schlüssel
  ausrollen, dann das ERP mit demselben Schlüssel starten (Details in
  [MOBILE_TOKEN_SECURITY.md](MOBILE_TOKEN_SECURITY.md#website-schnittstelle-apiinternal)).

### Datenbank

Eine neue Datenbank bekommt alles Nötige automatisch über das Basis-Schema
(`V1__basis_schema.sql`, früher Migrationen V221/V222):

- Mitarbeiter „System Webseite“ (`login_token = __SYSTEM_FUNNEL__`, Art `SYSTEM`)
  – Ersteller der Anfrage-Notizen.
- Tabelle `kunden_zaehler` für atomare, fortlaufende Kundennummern (verhindert
  Doppel-Vergabe bei parallelen Anfragen).
- E-Mail-Vorlage „Webseite — Anfragebestätigung“ (Dokumenttyp
  `WEBSITE_ANFRAGE_BESTAETIGUNG`).

### Bestätigungsmail und Push

- Die Bestätigungsmail nutzt die Vorlage „Webseite — Anfragebestätigung“
  (**Kommunikation › E-Mail › E-Mail Vorlagen**, Kategorie „Webseite &
  Anfragen“). Sie wird nur verschickt, wenn die Vorlage aktiv und der
  E-Mail-Versand eingerichtet ist. Absender ist die Standard-Absenderadresse
  des Betriebs, darunter die System-Signatur. Die Mail wird der Anfrage
  zugeordnet und ist im E-Mail-Center bei der Anfrage zu sehen.
- Die Push-Nachricht bekommen alle Mitarbeiter, deren Abteilung das Recht
  **„Neue Anfrage über Webseite“** hat (Einstellungen › Reiter „Abteilungen & Rechte“,
  „Push-Nachrichten aufs Handy“). Ein Tipp darauf öffnet die Anfrage in der
  Handy-App.

### Spring-Port nicht direkt exponieren

Der ERP-Port (`8080` bzw. `APP_PORT`) darf **nicht** öffentlich erreichbar
sein. Keine Port-Forwarding-Regel im Router, keine Firewall-Freigabe nach
außen. Im Zweifel mit Port-Scan von außen prüfen.

---

## Schritt 3 — Website-Server konfigurieren

### Geheimnisse hinterlegen

Auf dem Webseiten-Server in `.env` (oder Equivalent), **niemals ins Repo committen**:

```
ERP_INTERNAL_API_KEY=<Schlüssel aus Schritt 1a>
```

Dazu die ERP-Adresse im Tailnet. Wie die Variable dafür heißt, legt der Code
der Webseite fest (frühere Fassungen nutzten `ERP_FUNNEL_URL` bzw.
`ERP_BASE_URL`).

### Funnel-Submit weiterleiten

Beim Submit des Funnels (Schritt 3 „Unverbindlich anfragen"):

1. **Auf dem Webseiten-Server** das Formular validieren (Captcha,
   Rate-Limit, Honeypot — was immer ihr da habt).
2. Multipart-Request an `<ERP-Adresse>/api/internal/anfrage` schicken:

**Header:**
```
X-ERP-Website-Key: <Schlüssel>
Content-Type: multipart/form-data
```

**Multipart-Parts:**
- `anfrage` (JSON-String) — siehe Schema unten
- `bilder` (Datei, optional, mehrfach) — JPG/PNG/GIF/WebP, max. 5 MB

Ohne Bilder geht auch ein reiner JSON-Aufruf (`Content-Type: application/json`,
Body = Schema unten).

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
  "projektAnschrift": "Musterstraße 1, 12345 Musterstadt",
  "rechnungsAnschrift": "Musterweg 2, 12345 Musterstadt",
  "rechnungsAnschriftGleichProjekt": false,
  "datenschutzAkzeptiert": true,
  "consentIp": "192.0.2.1",
  "datenschutzVersion": "2026-01"
}
```

**Pflichtfelder:** `serviceTyp`, `nachricht`, `vorname`, `nachname`, `email`
(gültige Adresse), `datenschutzAkzeptiert` (muss `true` sein, sonst HTTP 400).

**Längen:** `serviceTyp` 50, `projektarten` höchstens 20 Einträge à 100,
`nachricht` 5000, `vorname`/`nachname` je 100, `email` 255, `telefon` 50,
Anschriften je 500, `consentIp` und `datenschutzVersion` je 50 Zeichen.

**Adress-Trennung:**
- `projektAnschrift` → landet auf `Anfrage.projektStrasse/Plz/Ort`
  (= „Wo wird gebaut?")
- `rechnungsAnschrift` → landet auf `Kunde.strasse/plz/ort`
  (= „Wohin geht die Rechnung?")
- `rechnungsAnschriftGleichProjekt: true` → `rechnungsAnschrift` wird
  ignoriert, stattdessen wird `projektAnschrift` als Rechnungsadresse
  übernommen.
- Die Anschrift wird bestmöglich in Straße / PLZ / Ort zerlegt
  („Straße Nr, PLZ Ort“); passt das Format nicht, landet alles in der Straße.

**Antwort bei Erfolg (HTTP 201):**
```json
{ "success": true, "anfrageId": 1234, "message": "Anfrage erfolgreich angelegt." }
```

**Antwort bei Validierungsfehler (HTTP 400):**
```json
{ "success": false, "message": "Die Anfrage ist unvollständig oder ungültig.", "felder": ["email", "vorname"] }
```

`felder` steht bei jedem Prüffehler (Pflichtfeld, Länge, E-Mail-Format,
Datenschutz), nicht bei kaputtem oder leerem JSON („Anfrage-Payload ist kein
gültiges JSON.“). Bei fehlender Datenschutz-Zustimmung lautet `message`
„Datenschutz muss akzeptiert werden.“. Das ERP loggt abgelehnte Anfragen mit
den Feldnamen, nie mit den Werten.

**Weitere Antworten:** 401 `{"success":false,"message":"Nicht autorisiert."}`
bei fehlendem oder falschem Schlüssel; 500 `"Interner Fehler."` bei
unerwarteten Fehlern. Den Code `SPAM_ABGELEHNT` (HTTP 422) wertet die Webseite
noch aus, das ERP sendet ihn derzeit aber nicht mehr (die Spam-Prüfung läuft im
Hintergrund).

---

## Schritt 4 — Verifikation

### 4a. Lokal

`CF_ACCESS_ENABLED=false` und `WEBSITE_INTERNAL_API_KEY` gesetzt, dann mit curl:

```bash
curl -X POST http://localhost:8080/api/internal/anfrage \
  -H "X-ERP-Website-Key: $WEBSITE_INTERNAL_API_KEY" \
  -F 'anfrage={"serviceTyp":"Neubau","nachricht":"Test","vorname":"Max",
       "nachname":"Mustermann","email":"max@example.de",
       "datenschutzAkzeptiert":true};type=application/json' \
  -F 'bilder=@/pfad/zu/bild.jpg'
```

Erwartetes Ergebnis: HTTP 201 + `anfrageId`. Im ERP unter
**Projektmanagement › Aufträge › Anfragen** sollte eine neue Anfrage erscheinen,
mit Notiz + Bildern.

### 4b. Produktiv (über Tailscale)

Vom Webseiten-Server aus:

```bash
curl -X POST <ERP-Adresse im Tailnet>/api/internal/anfrage \
  -H "X-ERP-Website-Key: $ERP_INTERNAL_API_KEY" \
  -F 'anfrage={...};type=application/json'
```

Erwartetes Ergebnis: HTTP 201.

### 4c. Negativ-Tests

- Fehlender oder falscher `X-ERP-Website-Key`: 401 vom ERP
- Aufruf aus dem Internet (öffentliche Absender-IP, Tailscale Funnel): 403 von der Netzgrenze
- `datenschutzAkzeptiert=false`: 400 mit Validierungsfehler

---

## Was im ERP passiert

1. **Kunde:** Wird über die E-Mail-Adresse (Groß-/Kleinschreibung egal)
   wiederverwendet – fehlt dort eine Telefonnummer, wird sie ergänzt. Sonst
   wird er neu angelegt mit atomarer Kundennummer (Counter-Tabelle).
2. **Anfrage:** `bauvorhaben` = `<ServiceTyp> - <Projektarten>` (z.B.
   „Neubau - Wohnhaus, Sanierung"), `kurzbeschreibung` enthält Bauvorhaben und
   Funnel-Text (höchstens 1000 Zeichen).
3. **AnfrageNotiz:** Erstellt vom System-Mitarbeiter „System Webseite" mit dem
   vollständigen Funnel-Inhalt + Datenschutz-Consent (Datum + IP + Version).
   Hochgeladene Bilder werden als `AnfrageNotizBild` mit gespeichert.
4. **Bestätigungsmail:** Nach dem Speichern geht im Hintergrund die
   Bestätigung an den Interessenten (siehe Schritt 2). Scheitert sie, bleibt
   die Anfrage trotzdem erhalten.
5. **Push:** „Neue Anfrage: <Vorname Nachname>“ mit dem Bauvorhaben als Text.
6. **Spam-Prüfung:** Ein KI-Filter (`AnfrageFunnelSpamFilterService`,
   Einstellung `anfrage.funnel.spamfilter.provider`, Standard `lokal`) prüft
   im Hintergrund. Erkannter Spam wird nur im Log vermerkt, die Anfrage bleibt
   bestehen und kann von Hand gelöscht werden. Ist der Filter nicht erreichbar,
   wird durchgelassen.
7. **Beim späteren Anfrage→Projekt-Wandeln** überträgt das ERP die Notiz
   automatisch ins **Bautagebuch** des neuen Projekts (inkl. Bilder + Datum).

---

## Schlüssel-Rotation

Das ERP kennt immer genau einen gültigen Schlüssel. Zum Wechseln:

1. Neuen Schlüssel erzeugen (Schritt 1a).
2. Auf dem Webseiten-Server `ERP_INTERNAL_API_KEY` ändern und den Dienst neu starten.
3. Direkt danach im ERP `WEBSITE_INTERNAL_API_KEY` auf denselben Wert setzen und neu starten.

Zwischen Schritt 2 und 3 antwortet das ERP mit 401 – Anfragen aus dieser
kurzen Lücke gehen verloren. Rollback: auf beiden Seiten wieder denselben
alten Wert eintragen.

---

## Troubleshooting

| Symptom | Ursache | Lösung |
|---|---|---|
| HTTP 401 `Nicht autorisiert.` | Schlüssel fehlt, ist falsch oder im ERP nicht gesetzt | `X-ERP-Website-Key` auf der Website und `WEBSITE_INTERNAL_API_KEY` im ERP vergleichen; Warnung „website.internal-api.key ist nicht gesetzt“ im ERP-Log? |
| HTTP 403 | Aufruf kommt nicht aus LAN/Tailnet (z. B. über Tailscale Funnel oder Tunnel) | Website-Server direkt ins Tailnet aufnehmen |
| HTTP 400 `Datenschutz muss akzeptiert werden.` | Checkbox nicht durchgereicht | `datenschutzAkzeptiert: true` in JSON setzen |
| HTTP 400 `Die Anfrage ist unvollständig oder ungültig.` | Pflichtfeld fehlt oder zu lang | Feldnamen stehen in `felder` |
| HTTP 400, Log: „System-Mitarbeiter 'Webseite' nicht gefunden" | Mitarbeiter mit `__SYSTEM_FUNNEL__` fehlt (alte Datenbank) | Flyway-Status prüfen, Basis-Schema bzw. Migration V221 |
| Keine Bestätigungsmail | Vorlage inaktiv, E-Mail-Versand nicht eingerichtet oder SMTP-Fehler | Vorlage „Webseite — Anfragebestätigung“ prüfen, ERP-Log ansehen |
| Bilder fehlen in der Notiz | Falscher Multipart-Part-Name | Part-Name muss exakt `bilder` heißen, MIME-Typ jpg/png/gif/webp |

---

## Anhang: Cloudflare Access (Sonderfall)

Nur zur Referenz – über einen Cloudflare-Tunnel ist `/api/internal/**` derzeit
nicht erreichbar (siehe oben).

- ERP: `CF_ACCESS_ENABLED=true`, `CF_ACCESS_TEAM_DOMAIN=<euer-team>.cloudflareaccess.com`,
  `CF_ACCESS_APPLICATION_AUD=<AUD-Tag der Access-Application>`.
- Ist Cloudflare Access aktiv, zählt **ausschließlich** ein gültiger
  `Cf-Access-Jwt-Assertion` (geprüft von `CloudflareAccessJwtFilter`); der
  Website-Schlüssel allein genügt dann nicht.
- Cloudflare-Seite: Tunnel auf `localhost:8080`, Access-Application für den Pfad
  `/api/internal/*` mit Policy „Service Auth“ und einem Service Token; die
  Website schickt `CF-Access-Client-Id` und `CF-Access-Client-Secret`.
- Fehlerbilder: 401 mit `CF-Access-JWT fehlt` (Access-Application für den Pfad
  nicht aktiv) bzw. `CF-Access-JWT ungültig` (`team-domain` oder `aud` falsch).

---

## Code-Referenz

| Datei | Zweck |
|---|---|
| [`AnfrageFunnelController`](../src/main/java/org/example/kalkulationsprogramm/controller/AnfrageFunnelController.java) | REST-Endpoint `/api/internal/anfrage` (Multipart und JSON) |
| [`AnfrageFunnelService`](../src/main/java/org/example/kalkulationsprogramm/service/AnfrageFunnelService.java) | Mapping Funnel → Kunde + Anfrage + Notiz, Push, Hintergrund-Aufgaben |
| [`AnfrageFunnelRequestDto`](../src/main/java/org/example/kalkulationsprogramm/dto/Anfrage/AnfrageFunnelRequestDto.java) | Eingabe-Schema mit Validierung |
| [`AnfrageBestaetigungVersandService`](../src/main/java/org/example/kalkulationsprogramm/service/AnfrageBestaetigungVersandService.java) | Bestätigungsmail an den Interessenten |
| [`AnfrageFunnelSpamFilterService`](../src/main/java/org/example/kalkulationsprogramm/service/AnfrageFunnelSpamFilterService.java) | Spam-Prüfung im Hintergrund |
| [`KundennummerService`](../src/main/java/org/example/kalkulationsprogramm/service/KundennummerService.java) | Atomare Kundennummer-Vergabe |
| [`WebsiteInternalApiKeyFilter`](../src/main/java/org/example/kalkulationsprogramm/config/WebsiteInternalApiKeyFilter.java) | Prüfung des Website-Schlüssels |
| [`ZeiterfassungSecurityFilter`](../src/main/java/org/example/kalkulationsprogramm/config/ZeiterfassungSecurityFilter.java) | Netzgrenze (nur LAN/Tailnet) |
| [`CloudflareAccessJwtFilter`](../src/main/java/org/example/kalkulationsprogramm/config/CloudflareAccessJwtFilter.java) | JWT-Prüfung im Cloudflare-Sonderfall |
| [`SecurityConfig`](../src/main/java/org/example/kalkulationsprogramm/config/SecurityConfig.java) | Filter-Chain für `/api/internal/**` |
| [`V1__basis_schema.sql`](../src/main/resources/db/postgresql/basis/V1__basis_schema.sql) | Seed System-Mitarbeiter, Counter-Tabelle für Kundennummern und Bestätigungs-Vorlage (früher V221/V222) |
