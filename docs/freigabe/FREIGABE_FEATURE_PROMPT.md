# KI-Prompt: Digitale Freigabe-Seite für Angebote / Auftragsbestätigungen

> **Kontext für die KI:** Diese Datei beschreibt **rein die technische Anbindung** an das ERP-Backend der Bauschlosserei Kuhn. Das **Design (Layout, Farben, Animationen, Fonts, Texte, Rechtshinweise)** wird von einem separaten Design-Agenten geliefert – diese Datei macht **keine** Design-Vorgaben. Bau das Datenmodell, die Routen und die Server-Calls; das Aussehen kommt später drauf.

> Diese Datei liegt zur Referenz auch im ERP-Repo unter `docs/freigabe/FREIGABE_FEATURE_PROMPT.md`. Die produktive Kopie für die KI-Sitzung sollte ins Astro-Repo (`C:\dev\Internetseite\molecular-mercury\FREIGABE_FEATURE_PROMPT.md`) übernommen werden.

---

## 1 · Aufgabe

Lege im Astro-Projekt unter `molecular-mercury/` eine **dynamische SSR-Seite** an:

```
src/pages/freigabe/[uuid].astro
```

Auf dieser Seite kann der Empfänger einer E-Mail (Kunde) ein Angebot oder eine Auftragsbestätigung digital prüfen und verbindlich annehmen. Außerdem brauchen wir einen **PDF-Proxy** und eine **POST-Route**, weil das ERP nicht öffentlich erreichbar ist und nur über Cloudflare-Access-Service-Token spricht.

```
src/pages/api/freigabe/[uuid]/pdf.ts          (GET-Proxy, streamt das PDF)
src/pages/api/freigabe/[uuid]/akzeptieren.ts  (POST: nimmt Bestätigung an, leitet ans ERP weiter)
```

Alle drei Dateien müssen `export const prerender = false;` setzen – die Daten kommen erst zur Request-Zeit aus dem ERP.

---

## 2 · ENV-Variablen (sind bereits gesetzt)

Genau dieselben wie für `src/pages/api/anfrage.ts`:

```
ERP_BASE_URL              = z.B. https://erp.internal.example  (Tunnel-URL des ERP)
CF_ACCESS_CLIENT_ID       = Cloudflare-Service-Token-ID
CF_ACCESS_CLIENT_SECRET   = Cloudflare-Service-Token-Secret
```

Diese drei MÜSSEN bei jedem ERP-Call als Header mitgeschickt werden:

```ts
const erpHeaders = {
  'CF-Access-Client-Id': process.env.CF_ACCESS_CLIENT_ID!,
  'CF-Access-Client-Secret': process.env.CF_ACCESS_CLIENT_SECRET!,
};
```

Wenn eine der Variablen fehlt: **immer 500 zurückgeben** und konsolelogge `[freigabe] ENV-Konfiguration unvollständig`. Niemals den Wert selbst loggen.

---

## 3 · Backend-Endpoints (im ERP, fertig implementiert)

Alle Pfade liegen unter `${ERP_BASE_URL}/api/internal/freigabe/...`. Cloudflare-Access-Header sind Pflicht.

### 3.1 GET `/api/internal/freigabe/{uuid}`

**Zweck:** Daten für die Seitenanzeige holen.

**Antwort 200 — `FreigabeAnsichtDto`:**

```ts
{
  uuid: string;                     // = der UUID-Path-Parameter
  status: "PENDING" | "ACCEPTED" | "EXPIRED" | "REVOKED";
  dokumentNummer: string;           // z.B. "ANG-2026-0123"
  dokumentArt: string;              // "Angebot" | "Auftragsbestätigung"
  dokumentBetrag: number | null;    // Brutto in EUR, kann null sein
  bauvorhaben: string | null;       // Beschreibung des Vorhabens
  kundeName: string | null;
  kundeEmail: string | null;        // E-Mail an die der Link gemailt wurde
  erstelltAm: string;               // ISO-LocalDateTime, "2026-05-05T12:00:00"
  ablaufDatum: string;              // ISO-LocalDateTime
  akzeptiertAm: string | null;      // nur gesetzt wenn status === "ACCEPTED"
  abgelaufen: boolean;              // Convenience-Flag (entspricht status === "EXPIRED")
  pdfPfad: string;                  // "/api/internal/freigabe/{uuid}/pdf" – nicht im Browser nutzen
}
```

**Antwort 404:** UUID ist unbekannt → Astro zeigt eine "Link nicht gefunden"-Seite.

### 3.2 GET `/api/internal/freigabe/{uuid}/pdf`

**Zweck:** Liefert das PDF als binären Stream.

**Antwort 200:** `Content-Type: application/pdf`, `Content-Disposition: inline; filename="..."`. Der Body ist die rohe PDF-Datei.

**Antwort 404:** PDF-Datei nicht auffindbar.
**Antwort 410:** Freigabe ist abgelaufen oder widerrufen → kein PDF mehr.

### 3.3 POST `/api/internal/freigabe/{uuid}/akzeptieren`

**Body — `FreigabeAkzeptierenRequest` (JSON):**

```ts
{
  email: string;             // optionale Bestätigungs-E-Mail vom Kunden
  bestaetigung: boolean;     // MUSS true sein, sonst 400
  clientIp: string;          // echte Client-IP, vom Astro-Layer extrahiert
  userAgent: string;         // User-Agent des Browsers
}
```

**Antwort 200 — `FreigabeAkzeptiertResponse`:**

```ts
{
  uuid: string;
  dokumentNummer: string;
  dokumentArt: string;
  akzeptiertAm: string;      // ISO-LocalDateTime
  hashAcceptance: string;    // 64-Zeichen-Hex-String (SHA-256)
}
```

**Antwort 400:** `bestaetigung !== true` oder ungültiger Body.
**Antwort 404:** UUID unbekannt.
**Antwort 410:** Abgelaufen / widerrufen / bereits in einem nicht-akzeptierbaren Zustand.

> Das Backend erzeugt den `hashAcceptance` aus IP, E-Mail, Zeitstempel, originalem Dokumenten-Hash und einem **serverseitigen Salt**. Der Kunde sieht den Hash als unveränderbare Quittung – Astro muss ihn nur durchreichen.

---

## 4 · Astro-Routen-Implementierung

### 4.1 `src/pages/freigabe/[uuid].astro`

```astro
---
export const prerender = false;

import Layout from '../../layouts/Layout.astro';

const { uuid } = Astro.params;

const erpBaseUrl = process.env.ERP_BASE_URL;
const cfClientId = process.env.CF_ACCESS_CLIENT_ID;
const cfClientSecret = process.env.CF_ACCESS_CLIENT_SECRET;

if (!uuid || !erpBaseUrl || !cfClientId || !cfClientSecret) {
  return new Response('Nicht verfügbar.', { status: 500 });
}

let dto: any = null;
let httpStatus = 0;
try {
  const resp = await fetch(`${erpBaseUrl.replace(/\/$/, '')}/api/internal/freigabe/${encodeURIComponent(uuid)}`, {
    headers: {
      'CF-Access-Client-Id': cfClientId,
      'CF-Access-Client-Secret': cfClientSecret,
    },
  });
  httpStatus = resp.status;
  if (resp.ok) dto = await resp.json();
} catch (e) {
  console.error('[freigabe] ERP-Call fehlgeschlagen:', (e as any)?.message);
  return new Response('Service vorübergehend nicht erreichbar.', { status: 502 });
}

// Status-Branching für die View:
//   httpStatus === 404           -> "Link nicht gefunden"
//   dto?.status === "EXPIRED"    -> "Link ist abgelaufen"  (oder dto.abgelaufen === true)
//   dto?.status === "ACCEPTED"   -> Bestätigungs-Ansicht (akzeptiertAm anzeigen)
//   dto?.status === "PENDING"    -> Annahme-Formular (PDF-Anzeige + Bestätigungs-Checkbox + Submit)
//   dto?.status === "REVOKED"    -> "Link wurde zurückgezogen"
---

<Layout title={`Freigabe ${dto?.dokumentNummer ?? ''}`}>
  {/* Design wird vom Design-Agenten gefüllt. Felder, die zur Verfügung stehen:
       dto.dokumentArt, dto.dokumentNummer, dto.dokumentBetrag, dto.bauvorhaben,
       dto.kundeName, dto.ablaufDatum, dto.akzeptiertAm, dto.status */}
  {/* PDF-Einbindung später per <iframe src={`/api/freigabe/${uuid}/pdf`} /> */}
  {/* Submit-Form schickt POST an /api/freigabe/{uuid}/akzeptieren */}
</Layout>
```

### 4.2 `src/pages/api/freigabe/[uuid]/pdf.ts` (Proxy)

```ts
import type { APIRoute } from 'astro';
export const prerender = false;

export const GET: APIRoute = async ({ params }) => {
  const uuid = params.uuid;
  const erpBaseUrl = process.env.ERP_BASE_URL;
  const cfClientId = process.env.CF_ACCESS_CLIENT_ID;
  const cfClientSecret = process.env.CF_ACCESS_CLIENT_SECRET;
  if (!uuid || !erpBaseUrl || !cfClientId || !cfClientSecret) {
    return new Response('not configured', { status: 500 });
  }

  const upstream = await fetch(
    `${erpBaseUrl.replace(/\/$/, '')}/api/internal/freigabe/${encodeURIComponent(uuid)}/pdf`,
    {
      headers: {
        'CF-Access-Client-Id': cfClientId,
        'CF-Access-Client-Secret': cfClientSecret,
      },
    },
  );
  if (!upstream.ok) {
    return new Response(null, { status: upstream.status });
  }
  return new Response(upstream.body, {
    status: 200,
    headers: {
      'Content-Type': upstream.headers.get('Content-Type') ?? 'application/pdf',
      'Content-Disposition': upstream.headers.get('Content-Disposition') ?? 'inline',
      // Kein Cache: Status kann sich jederzeit ändern (Annahme, Ablauf).
      'Cache-Control': 'no-store',
    },
  });
};
```

Im Astro-Frontend wird die PDF dann via `/api/freigabe/${uuid}/pdf` (ohne `internal`!) eingebunden – das ist die öffentliche Astro-Route, die im Hintergrund den Proxy-Call macht.

### 4.3 `src/pages/api/freigabe/[uuid]/akzeptieren.ts`

```ts
import type { APIRoute } from 'astro';
export const prerender = false;

export const POST: APIRoute = async ({ params, request, clientAddress }) => {
  const uuid = params.uuid;
  const erpBaseUrl = process.env.ERP_BASE_URL;
  const cfClientId = process.env.CF_ACCESS_CLIENT_ID;
  const cfClientSecret = process.env.CF_ACCESS_CLIENT_SECRET;
  if (!uuid || !erpBaseUrl || !cfClientId || !cfClientSecret) {
    return new Response(JSON.stringify({ success: false, message: 'Server nicht konfiguriert.' }), { status: 500 });
  }

  // Body parsen (FormData oder JSON möglich – das überlässt der Design-Agent dem UI)
  let body: any = {};
  try {
    const ct = request.headers.get('content-type') ?? '';
    if (ct.includes('application/json')) {
      body = await request.json();
    } else {
      const fd = await request.formData();
      body = Object.fromEntries(fd.entries());
      body.bestaetigung = body.bestaetigung === 'on' || body.bestaetigung === 'true' || body.bestaetigung === true;
    }
  } catch {
    return new Response(JSON.stringify({ success: false, message: 'Ungültige Anfrage.' }), { status: 400 });
  }

  if (body.bestaetigung !== true) {
    return new Response(
      JSON.stringify({ success: false, message: 'Bitte bestätigen Sie die Annahme.' }),
      { status: 400 },
    );
  }

  const ip = request.headers.get('cf-connecting-ip')
    ?? request.headers.get('x-forwarded-for')?.split(',')[0]?.trim()
    ?? clientAddress
    ?? '';

  const payload = {
    email: typeof body.email === 'string' ? body.email : null,
    bestaetigung: true,
    clientIp: ip.slice(0, 45),
    userAgent: (request.headers.get('user-agent') ?? '').slice(0, 500),
  };

  const resp = await fetch(
    `${erpBaseUrl.replace(/\/$/, '')}/api/internal/freigabe/${encodeURIComponent(uuid)}/akzeptieren`,
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'CF-Access-Client-Id': cfClientId,
        'CF-Access-Client-Secret': cfClientSecret,
      },
      body: JSON.stringify(payload),
    },
  );
  const text = await resp.text();
  return new Response(text, {
    status: resp.status,
    headers: { 'Content-Type': 'application/json; charset=utf-8' },
  });
};
```

---

## 5 · Vorlage zum Anlehnen

`src/pages/api/anfrage.ts` (bereits im Repo) zeigt **exakt dasselbe Muster** für Cloudflare-Access-Header, IP-Extraktion und Forwarding ans ERP. Wenn du unsicher bist, nimm dir diese Datei als Referenz – sie ist produktiv im Einsatz.

---

## 6 · Fachliche Anforderungen die der Design-Agent kommunizieren muss

1. Bestätigungs-Checkbox `bestaetigung` MUSS aktiv angeklickt sein – ohne `true` lehnt das ERP mit 400 ab.
2. IP, User-Agent und Annahme-Zeitpunkt werden gespeichert. Datenschutz-Hinweis Pflicht.
3. Annahme ist rechtsverbindlich – Begriff "verbindlich annehmen" oder "kostenpflichtig beauftragen" auf dem Button.
4. Nach erfolgreicher Annahme `hashAcceptance` und `akzeptiertAm` als Quittung sichtbar machen.
5. PDF-Anzeige via `/api/freigabe/${uuid}/pdf` (Astro-Proxy), nicht direkt ERP.

---

## 7 · Was der Design-Agent NICHT machen soll

- Keine zusätzlichen Backend-Endpoints anfragen – alles Nötige ist oben dokumentiert.
- Kein direktes Aufrufen der ERP-URL aus dem Browser. Browser → Astro → ERP, immer.
- Keine Caching-Header auf `[uuid].astro` oder `pdf.ts`. Status kann sich jederzeit ändern.
- Kein Speichern von `clientIp` oder `userAgent` clientseitig. Astro-Layer extrahiert und schickt sie.

---

## 8 · Test-URL

Wenn das ERP ein Angebot oder eine AB versendet, fügt es automatisch den Link an:

```
https://bauschlosserei-kuhn.de/freigabe/<uuid>
```

Solange `[uuid].astro` fehlt, läuft der Link in eine Astro-404. Sobald die Seite steht, ist sie sofort über die echten ERP-Daten testbar.
