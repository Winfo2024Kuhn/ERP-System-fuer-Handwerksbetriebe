import { createIdsDraftStore } from './idsDraftStore';
import { randomBytes } from 'node:crypto';
import type { IncomingMessage, ServerResponse } from 'node:http';
import { parseIdsCart, serializeIdsCart, type IdsCart } from './idsCart';
import type { IdsProjektZuordnung } from '../src/types/ids';

import { createIdsConfigStore, WUERTH_SHOP as SHOP, type IdsSettings } from './idsConfig';
export const WUERTH_ID = 203;
export interface IdsCredentials { customer: string; username: string; password: string }

export const idsConfig = createIdsConfigStore(process.env.IDS_PREVIEW_CONFIG_PATH);
export function loadIdsCredentials(): IdsCredentials | null {
  const config = idsConfig.load();
  return config.aktiviert && config.kundennummer && config.loginName && config.passwort
    ? { customer: config.kundennummer, username: config.loginName, password: config.passwort } : null;
}

const PROJEKT_BODY_LIMIT = 2048;
const PROJEKT_NAME_MAX = 200;

/**
 * Optionaler JSON-Body beim Shop-Start aus dem Projektbedarf: { projektId, projektName }.
 * Leerer Body = Warenkorb ohne Projekt. Alles Ungültige wirft (→ 400).
 */
async function leseProjektZuordnung(req: IncomingMessage): Promise<IdsProjektZuordnung | undefined> {
  const chunks: Buffer[] = []; let size = 0;
  for await (const chunk of req) {
    const data = Buffer.from(chunk); size += data.length;
    if (size > PROJEKT_BODY_LIMIT) throw new Error('Eingabe zu groß.');
    chunks.push(data);
  }
  const text = Buffer.concat(chunks).toString('utf8').trim();
  if (!text) return undefined;
  const value: unknown = JSON.parse(text);
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('Ungültige Projektangabe.');
  const { projektId, projektName } = value as Record<string, unknown>;
  if (projektId == null) {
    if (projektName != null) throw new Error('Projektname ohne Projekt.');
    return undefined;
  }
  if (typeof projektId !== 'number' || !Number.isSafeInteger(projektId) || projektId <= 0) throw new Error('Ungültige Projektnummer.');
  if (projektName != null && typeof projektName !== 'string') throw new Error('Ungültiger Projektname.');
  const name = projektName?.trim() ?? '';
  // eslint-disable-next-line no-control-regex
  if (name.length > PROJEKT_NAME_MAX || /[\u0000-\u001f\u007f]/.test(name)) throw new Error('Ungültiger Projektname.');
  return name ? { projektId, projektName: name } : { projektId };
}

export function createIdsBridge(origin: string, credentials: () => IdsCredentials | null, receive: (cart: IdsCart, id?: string) => void, store = idsConfig, drafts = createIdsDraftStore(), supplierId: number | null = WUERTH_ID) {
  const tokens = new Map<string, { expires: number; cartId?: string; projekt?: IdsProjektZuordnung }>();
  const send = (res: ServerResponse, value: unknown, status = 200) => {
    res.statusCode = status;
    res.setHeader('Cache-Control', 'no-store');
    res.setHeader('Referrer-Policy', 'no-referrer');
    res.setHeader('X-Content-Type-Options', 'nosniff');
    res.setHeader('Content-Type', 'application/json; charset=utf-8');
    res.end(JSON.stringify(value)); return true;
  };
  return async (req: IncomingMessage, res: ServerResponse, url: URL): Promise<boolean> => {
    const settings = url.pathname === `/api/admin/lieferanten/${supplierId}/ids-konfig`;
    if (!url.pathname.startsWith('/api/ids/') && url.pathname !== '/api/admin/ids/lieferanten' && !settings) return false;
    if (req.headers.host !== new URL(origin).host) return send(res, { message: 'Nur lokal verfügbar.' }, 403);
    if (url.pathname === '/api/admin/ids/lieferanten' && req.method === 'GET') return send(res, supplierId === null ? [] : [{ id: supplierId, name: 'Würth' }]);
    if (settings) {
      if (req.method === 'GET') return send(res, { ...store.load(), passwort: store.load().passwort ? '********' : '' });
      if (req.method !== 'PUT') return send(res, { message: 'Aufruf nicht verfügbar.' }, 405);
      if (req.headers.origin !== origin) return send(res, { message: 'Bitte in den Einstellungen speichern.' }, 403);
      try {
        const chunks: Buffer[] = []; let size = 0;
        for await (const chunk of req) { const data = Buffer.from(chunk); size += data.length; if (size > 8192) return send(res, { message: 'Eingabe zu groß.' }, 413); chunks.push(data); }
        const value = store.save(JSON.parse(Buffer.concat(chunks).toString('utf8')) as IdsSettings);
        return send(res, { ...value, passwort: value.passwort ? '********' : '' });
      } catch { return send(res, { message: 'Bitte Würth-URL, IDS-Protokoll und Zugangsdaten prüfen. Aktivierte Schnittstellen benötigen Kundennummer, Login und Passwort.' }, 400); }
    }
    const draftMatch = url.pathname.match(/^\/api\/ids\/warenkoerbe\/([a-zA-Z0-9-]+)(\/senden)?$/);
    if (req.method === 'GET' && url.pathname === '/api/ids/warenkoerbe') {
      const filter = url.searchParams.get('projektId');
      if (filter === null) return send(res, drafts.all());
      const projektId = /^[1-9]\d{0,15}$/.test(filter) ? Number(filter) : NaN;
      if (!Number.isSafeInteger(projektId)) return send(res, { message: 'Ungültige Projektnummer.' }, 400);
      return send(res, drafts.byProjekt(projektId));
    }
    const draft = draftMatch ? drafts.get(draftMatch[1]) : undefined;
    if (draftMatch && !draft) return send(res, { message: 'Warenkorb nicht gefunden.' }, 404);
    if (draftMatch && !draftMatch[2] && req.method === 'GET') return send(res, draft);
    const outbound = !!draftMatch?.[2];
    const start = url.pathname === `/api/ids/punchout/${supplierId}/start`;
    const callback = url.pathname === `/api/ids/punchout/${supplierId}/return`;
    if (url.pathname === '/api/ids/lieferanten' && req.method === 'GET') {
      return send(res, credentials() && supplierId !== null ? [{ id: supplierId, name: 'Würth' }] : []);
    }
    if (req.method !== 'POST' || (!start && !outbound && !callback)) return send(res, { message: 'IDS-Aufruf nicht verfügbar.' }, 405);
    if (start || outbound) {
      // This preview has no login: only its own loopback page may request credentials.
      if (req.headers.origin !== origin) return send(res, { message: 'Bitte den Shop aus der Anwendung öffnen.' }, 403);
      // Nur der Erst-Start aus dem Projektbedarf trägt ein Projekt; beim erneuten Senden behält der Warenkorb seins.
      let projekt: IdsProjektZuordnung | undefined;
      if (start) {
        try { projekt = await leseProjektZuordnung(req); }
        catch { return send(res, { message: 'Projektangabe für den Warenkorb ist ungültig.' }, 400); }
      }
      if (supplierId === null) return send(res, { message: 'Würth muss zuerst einem Lieferanten aus der Datenbank zugeordnet werden.' }, 409);
      if (draft?.ordered) return send(res, { message: 'Dieser Warenkorb wurde bereits bestellt.' }, 409);
      const config = credentials();
      if (!config) return send(res, { message: 'Würth ist noch nicht eingerichtet.' }, 409);
      for (const [token, session] of tokens) if (session.expires < Date.now()) tokens.delete(token);
      if (tokens.size >= 100) return send(res, { message: 'Zu viele offene Shop-Aufrufe.' }, 429);
      const token = randomBytes(32).toString('hex');
      tokens.set(token, { expires: Date.now() + 60 * 60 * 1000, cartId: draft?.id, projekt });
      return send(res, { action: SHOP, enctype: 'multipart/form-data', fields: {
        action: outbound ? 'WKS' : 'WKE',
        ...(draft ? { warenkorb: serializeIdsCart(draft, draft.number) } : {}),
        kndnr: config.customer, name_kunde: config.username, pw_kunde: config.password,
        hookurl: `${origin}/api/ids/punchout/${supplierId}/return?token=${token}`,
      } });
    }
    const token = url.searchParams.get('token') ?? '';
    const session = tokens.get(token);
    if (!session || session.expires < Date.now()) return send(res, { message: 'Shop-Aufruf abgelaufen. Bitte erneut aus der Anwendung öffnen.' }, 403);
    // Reserve before reading the stream: simultaneous replay cannot import twice.
    tokens.delete(token);
    try {
      const chunks: Buffer[] = []; let size = 0;
      for await (const chunk of req) {
        const data = Buffer.from(chunk); size += data.length;
        if (size > 2 * 1024 * 1024) return send(res, { message: 'Warenkorb ist zu groß.' }, 413);
        chunks.push(data);
      }
      const form = await new Response(new Uint8Array(Buffer.concat(chunks)), { headers: { 'content-type': req.headers['content-type'] ?? '' } }).formData();
      const xml = form.get('warenkorb');
      if (!xml) throw new Error('Warenkorb fehlt.');
      const cart = parseIdsCart(typeof xml === 'string' ? xml : await xml.text());
      const saved = drafts.save(cart, session.cartId, session.projekt);
      receive(saved, saved.id);
      res.statusCode = 303;
      res.setHeader('Cache-Control', 'no-store');
      res.setHeader('Referrer-Policy', 'no-referrer');
      // Aus dem Projektbedarf gestartete Warenkörbe werden am Projekt geparkt und später von dort bestellt.
      res.setHeader('Location', saved.projektId != null
        ? `/bestellungen/bedarf/projekt/${saved.projektId}?warenkorb=${encodeURIComponent(saved.id)}`
        : `/bestellungen/ids/${saved.id}`);
      res.end(); return true;
    } catch {
      return send(res, { message: 'Warenkorb konnte nicht übernommen werden. Bitte den Shop erneut öffnen und den Warenkorb zurückgeben.' }, 400);
    }
  };
}
