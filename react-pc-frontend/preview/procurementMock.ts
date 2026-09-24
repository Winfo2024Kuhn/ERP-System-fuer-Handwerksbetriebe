import type { IncomingMessage, ServerResponse } from 'node:http';
import type { IdsCart } from './idsCart';
import { WUERTH_ID } from './idsBridge';

/** Isolated original-branch preview. All business data is dummy and lives only in this process. */
type Data = Record<string, unknown>;
export interface PreviewBestellung extends Data {
  id: number; projektId: number | null; lieferantId: number | null;
  produktname: string; menge: number; einheit: string; bestellt: boolean; exportiertAm: string | null;
}
interface InquirySupplier extends Data { id: number; lieferantId: number; lieferantenname: string; token: string; status: string }
interface Inquiry extends Data { id: number; nummer: string; status: string; lieferanten: InquirySupplier[]; positionen: (Data & { id: number })[] }
const projects = [
  { id: 101, bauvorhaben: 'Werkhalle Musterstadt', auftragsnummer: 'P-2026-101', kunde: 'Max Mustermann', abgeschlossen: false, excKlasse: 'EXC_2' },
  { id: 102, bauvorhaben: 'Treppengeländer Musterhof', auftragsnummer: 'P-2026-102', kunde: 'Max Mustermann', abgeschlossen: false, excKlasse: 'EXC_2' },
];
const categories = [
  { id: 1, beschreibung: 'Stahl', parentId: null },
  { id: 302, beschreibung: 'Hohlprofile', parentId: 1 },
  { id: 303, beschreibung: 'Bleche und Platten', parentId: 1 },
  { id: 304, beschreibung: 'Befestigung', parentId: null },
];
const supplierSeed = [
  { id: 201, lieferantenname: 'Musterstahl Handel', lieferantenTyp: 'Stahlhandel', istAktiv: true, ort: 'Musterstadt', plz: '12345', strasse: 'Musterweg 1', kundenEmails: ['stahl@example.test'] },
  { id: 202, lieferantenname: 'Musterbefestigung', lieferantenTyp: 'Fachhandel', istAktiv: true, ort: 'Musterhof', plz: '12345', strasse: 'Musterweg 2', kundenEmails: ['handel@example.test'] },
];
const articles = [
  { id: 401, produktname: 'Rechteckrohr 60 × 40 × 3', produkttext: 'Hohlprofil für die Werkhalle', produktlinie: 'Stahlprofile', werkstoffName: 'S235JR', abmessung: '60 × 40 × 3 mm', artikelnummer: 'DEMO-401', externeArtikelnummer: 'MS-60403', kategorieId: 302, rootKategorieId: 1, kategoriePfad: 'Stahl > Hohlprofile', lieferantId: 201, lieferantenname: 'Musterstahl Handel', preis: 12.5, preisDatum: '2026-09-24', kgProMeter: 4.25, verpackungseinheit: 6, verrechnungseinheit: { name: 'METER', anzeigename: 'm' }, fixmassMm: 6000 },
  { id: 402, produktname: 'Fußplatte 200 × 200 × 10', produkttext: 'Stahlplatte für Stützenfuß', produktlinie: 'Bleche', werkstoffName: 'S235JR', abmessung: '200 × 200 × 10 mm', artikelnummer: 'DEMO-402', externeArtikelnummer: 'MS-20010', kategorieId: 303, rootKategorieId: 1, kategoriePfad: 'Stahl > Bleche und Platten', lieferantId: 201, lieferantenname: 'Musterstahl Handel', preis: null, preisDatum: null, kgProMeter: null, verpackungseinheit: 1, verrechnungseinheit: { name: 'STUECK', anzeigename: 'Stück' }, fixmassMm: null },
  { id: 403, produktname: 'Sechskantschraube M12', produkttext: 'Schraube M12 × 40', produktlinie: 'Schrauben', werkstoffName: 'Stahl verzinkt', abmessung: 'M12 × 40', artikelnummer: 'DEMO-403', externeArtikelnummer: 'MB-M1240', kategorieId: 304, rootKategorieId: 304, kategoriePfad: 'Befestigung', lieferantId: 202, lieferantenname: 'Musterbefestigung', preis: 0.85, preisDatum: '2026-09-24', kgProMeter: null, verpackungseinheit: 1, verrechnungseinheit: { name: 'STUECK', anzeigename: 'Stück' }, fixmassMm: null },
];
const wuerth = { id: WUERTH_ID, lieferantenname: 'Würth', lieferantenTyp: 'SONSTIGER', istAktiv: true, ort: '', plz: '', strasse: '', kundenEmails: [] as string[] };
let suppliers = structuredClone([...supplierSeed, wuerth]);
let rows: PreviewBestellung[] = [];
let inquiries: Inquiry[] = [];
let offers: Data[] = [];
let emails: Data[] = [];
let assignments: Data[] = [];
let nextId = 2000;
const now = () => new Date().toISOString();
function fail(message: string, status = 400): never { throw Object.assign(new Error(message), { status }); }
function required<T>(value: T | undefined, label: string): T { return value ?? fail(`${label} nicht gefunden.`, 404); }
function object(value: unknown): Data {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return fail('JSON-Objekt erwartet.');
  return value as Data;
}
function list(value: unknown): Data[] { return Array.isArray(value) ? value.map(object) : []; }
function positive(value: unknown): number {
  if (typeof value !== 'number' || !Number.isFinite(value) || value <= 0) return fail('Menge muss größer als 0 sein.');
  return value;
}
function refId(value: unknown, values: readonly { id: number }[], label: string): number | null {
  if (value == null) return null;
  if (typeof value !== 'number' || !values.some(item => item.id === value)) return fail(`${label} ist ungültig.`);
  return value;
}
function makeRow(input: Data, id = nextId++): PreviewBestellung {
  const projektId = refId(input.projektId, projects, 'Projekt');
  const lieferantId = refId(input.lieferantId, suppliers, 'Lieferant');
  const artikelId = refId(input.artikelId, articles, 'Artikel');
  const article = articles.find(item => item.id === artikelId);
  const kategorieId = refId(input.kategorieId ?? article?.kategorieId, categories, 'Kategorie');
  const category = categories.find(item => item.id === kategorieId);
  const project = projects.find(item => item.id === projektId);
  const supplier = suppliers.find(item => item.id === lieferantId);
  const produktname = String(input.produktname ?? article?.produktname ?? '').trim();
  if (!produktname) fail('Produktname fehlt.');
  const menge = positive(input.menge);
  const pieces = input.einheit === 'm' ? menge * 1000 / Number(input.fixmassMm ?? 0) : menge;
  const stueckzahl = artikelId && Number.isInteger(pieces) && pieces > 0 ? pieces : 0;
  return {
    id, artikelId, externeArtikelnummer: article?.externeArtikelnummer ?? null,
    produktname, produkttext: input.produkttext ?? article?.produkttext ?? null, werkstoffName: article?.werkstoffName ?? null,
    kategorieId, kategorieName: category?.beschreibung ?? null, rootKategorieId: category?.parentId ?? category?.id ?? null,
    rootKategorieName: categories.find(item => item.id === (category?.parentId ?? category?.id))?.beschreibung ?? null,
    stueckzahl, menge, einheit: String(input.einheit ?? 'Stück'), projektId,
    projektName: project?.bauvorhaben ?? null, projektNummer: project?.auftragsnummer ?? null, kundenName: project?.kunde ?? null,
    lieferantId, lieferantName: supplier?.lieferantenname ?? null, bestellt: Boolean(input.bestellt), bestelltAm: input.bestelltAm ?? null,
    exportiertAm: typeof input.exportiertAm === 'string' ? input.exportiertAm : null,
    kommentar: input.kommentar ?? null, kilogramm: article?.kgProMeter ? menge * article.kgProMeter : null,
    gesamtKilogramm: article?.kgProMeter ? menge * article.kgProMeter : null,
    verpackungseinheit: article?.verpackungseinheit ?? null, fixmassMm: input.fixmassMm ?? null, schnittbildId: input.schnittbildId ?? null,
    schnittbildBildUrl: input.schnittbildId ? '/api/preview/schnitt.svg' : null, schnittAchseBildUrl: null,
    anschnittbildStegUrl: null, anschnittbildFlanschUrl: null, anschnittStegText: null, anschnittFlanschText: null,
    anschnittWinkelLinks: input.anschnittWinkelLinks ?? null, anschnittWinkelRechts: input.anschnittWinkelRechts ?? null,
    zeugnisAnforderung: input.zeugnisAnforderung ?? null, excKlasse: project?.excKlasse ?? null, freiePosition: !artikelId,
  };
}
export function resetProcurementMock(): void {
  suppliers = structuredClone([...supplierSeed, wuerth]); nextId = 2000; inquiries = []; offers = []; emails = []; assignments = [];
  rows = [
    makeRow({ projektId: 101, lieferantId: 201, artikelId: 401, menge: 12, einheit: 'm', fixmassMm: 6000, zeugnisAnforderung: 'APZ_3_1' }, 1001),
    makeRow({ projektId: 101, lieferantId: 201, artikelId: 402, menge: 4, einheit: 'Stück', zeugnisAnforderung: 'APZ_3_1' }, 1002),
    makeRow({ projektId: 102, lieferantId: 202, artikelId: 403, menge: 24, einheit: 'Stück' }, 1003),
    makeRow({ projektId: null, lieferantId: 201, produktname: 'Werkstattmaterial', menge: 5, einheit: 'Stück', kommentar: 'Dummy-Bedarf ohne Projekt' }, 1004),
  ];
  inquiries = [{
    id: 901, nummer: 'PA-2026-0001', projektId: 101, bauvorhaben: projects[0].bauvorhaben,
    erstelltAm: '2026-09-24T09:00:00', antwortFrist: '2026-10-01', notiz: 'Dummy-Preisanfrage zur Werkhalle', status: 'OFFEN',
    lieferanten: supplierSeed.map((supplier, index) => ({ id: 911 + index, lieferantId: supplier.id, lieferantenname: supplier.lieferantenname, token: `preview-only-${911 + index}`, status: 'VORBEREITET', versendetAn: supplier.kundenEmails[0], versendetAm: null, antwortErhaltenAm: null })),
    positionen: rows.filter(row => row.projektId === 101).map((row, index) => ({ id: 921 + index, artikelInProjektId: row.id, artikelId: row.artikelId, externeArtikelnummer: row.externeArtikelnummer, produktname: row.produktname, produkttext: row.produkttext, werkstoffName: row.werkstoffName, menge: row.menge, einheit: row.einheit, kommentar: row.kommentar, reihenfolge: index })),
  }];
}
resetProcurementMock();
export function getProcurementPreviewState() {
  return structuredClone({ projekte: projects, lieferanten: suppliers, artikel: articles, kategorien: categories, bestellungen: rows, preisanfragen: inquiries, angebote: offers, emails, zuordnungen: assignments });
}

/** A real IDS return is kept in this local preview only, never sent as an order. */
/** Am Projekt geparkte Warenkörbe tragen ihre projektId mit – sonst erschienen sie im Mock als Vorrat. */
export function receiveIdsCart(cart: IdsCart & { projektId?: number }, id?: string): void {
  if (!suppliers.some(supplier => supplier.id === WUERTH_ID)) suppliers.push({
    id: WUERTH_ID, lieferantenname: 'Würth', lieferantenTyp: 'Fachhandel', istAktiv: true,
    ort: '', plz: '', strasse: '', kundenEmails: [],
  });
  if (id) rows = rows.filter(row => row.idsCartId !== id);
  // Unbekannte Projekte (z. B. aus einer älteren privaten Ablage) bleiben Vorrat, statt den Import scheitern zu lassen.
  const projektId = cart.projektId != null && projects.some(project => project.id === cart.projektId) ? cart.projektId : null;
  const imported = cart.items.map(item => ({
    ...makeRow({ produktname: item.name, produkttext: item.description, menge: item.quantity, einheit: item.unit, lieferantId: WUERTH_ID, projektId,
      bestellt: cart.ordered, bestelltAm: cart.ordered ? now().slice(0, 10) : null,
      kommentar: `Aus Würth übernommen${cart.ordered ? ' – bereits im Shop bestellt' : ''}${cart.reference ? ` (${cart.reference})` : ''}` }),
    externeArtikelnummer: item.article, preisProEinheit: item.netPrice == null ? null : item.netPrice / item.quantity,
    idsCartId: id, idsNetPrice: item.netPrice, idsPriceBasis: item.priceBasis, idsEan: item.ean, idsVat: item.vat,
  }));
  rows.push(...imported);
}
async function bytes(req: IncomingMessage): Promise<Buffer> {
  const chunks: Buffer[] = []; let size = 0;
  for await (const chunk of req) {
    const buffer = Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk);
    size += buffer.length;
    if (size > 20 * 1024 * 1024) fail('Vorschaudatei ist zu groß.', 413);
    chunks.push(buffer);
  }
  return Buffer.concat(chunks);
}
async function body(req: IncomingMessage): Promise<Data> {
  const buffer = await bytes(req);
  if (!buffer.length) return {};
  try { return object(JSON.parse(buffer.toString('utf8'))); } catch { return fail('Ungültiger JSON-Inhalt.'); }
}
function json(res: ServerResponse, value: unknown, status = 200): true {
  res.statusCode = status; res.setHeader('Content-Type', 'application/json; charset=utf-8');
  res.setHeader('Cache-Control', 'no-store'); res.end(JSON.stringify(value)); return true;
}
function search<T>(items: T[], query: string | null): T[] {
  const term = (query ?? '').trim().toLocaleLowerCase('de-DE');
  return term ? items.filter(item => JSON.stringify(item).toLocaleLowerCase('de-DE').includes(term)) : items;
}
function paginate<T>(items: T[], url: URL): T[] {
  const size = Math.max(1, Math.min(500, Number(url.searchParams.get('size')) || 20));
  const page = Math.max(0, Number(url.searchParams.get('page')) || 0);
  return items.slice(page * size, (page + 1) * size);
}
function comparison(inquiry: Inquiry) {
  return {
    preisanfrageId: inquiry.id, nummer: inquiry.nummer, bauvorhaben: inquiry.bauvorhaben,
    lieferanten: inquiry.lieferanten.map(supplier => ({ ...supplier, preisanfrageLieferantId: supplier.id })),
    positionen: inquiry.positionen.map(position => {
      const priced = offers.filter(offer => offer.preisanfragePositionId === position.id && typeof offer.einzelpreis === 'number');
      const cheapest = priced.length ? Math.min(...priced.map(offer => Number(offer.einzelpreis))) : null;
      const cells = inquiry.lieferanten.map(supplier => {
        const offer = priced.find(item => item.preisanfrageLieferantId === supplier.id);
        return { preisanfrageLieferantId: supplier.id, einzelpreis: offer?.einzelpreis ?? null,
          gesamtpreis: offer ? Number(offer.einzelpreis) * Number(position.menge) : null,
          mwstProzent: offer?.mwstProzent ?? null, lieferzeitTage: offer?.lieferzeitTage ?? null, bemerkung: offer?.bemerkung ?? null,
          guenstigster: !!offer && offer.einzelpreis === cheapest };
      });
      return { ...position, preisanfragePositionId: position.id, guenstigsterPreisanfrageLieferantId: cells.find(cell => cell.guenstigster)?.preisanfrageLieferantId ?? null, zellen: cells };
    }),
  };
}

/** Original Java PDF services run in a separate, strictly loopback-only preview bridge. */
async function pdf(res: ServerResponse, payload: Data): Promise<true> {
  try {
    const response = await fetch('http://127.0.0.1:8097/render', {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload), signal: AbortSignal.timeout(20_000),
    });
    if (!response.ok) return json(res, { message: 'Original-PDF konnte nicht erzeugt werden.' }, 502);
    res.statusCode = 200; res.setHeader('Content-Type', 'application/pdf'); res.setHeader('Content-Disposition', 'inline; filename="vorschau.pdf"');
    res.setHeader('Cache-Control', 'no-store'); res.end(Buffer.from(await response.arrayBuffer())); return true;
  } catch { return json(res, { message: 'Lokale Original-PDF-Vorschau ist noch nicht bereit.' }, 503); }
}

export async function handleProcurementMock(req: IncomingMessage, res: ServerResponse, url: URL): Promise<boolean> {
  const path = url.pathname; const method = req.method ?? 'GET';
  try {
    if (method === 'GET') {
      if (path === '/api/bestellungen/offen') return json(res, rows.filter(row => !row.bestellt));
      if (path === '/api/projekte/simple') return json(res, paginate(search(projects, url.searchParams.get('q')), url));
      if (path === '/api/artikel/kategorien/alle') return json(res, categories.map(item => ({ id: item.id, bezeichnung: item.beschreibung, parentId: item.parentId })));
      if (path === '/api/kategorien') return json(res, categories);
      if (path === '/api/lieferanten') {
        const found = search(suppliers, url.searchParams.get('q')); return json(res, { lieferanten: paginate(found, url), gesamt: found.length });
      }
      if (path === '/api/artikel/werkstoffe') return json(res, [...new Set(articles.map(article => article.werkstoffName))]);
      if (path === '/api/artikel') {
        let found = search(articles, url.searchParams.get('q'));
        const category = Number(url.searchParams.get('kategorieId'));
        if (category) found = found.filter(article => article.kategorieId === category || article.rootKategorieId === category);
        for (const [query, field] of [['lieferant', 'lieferantenname'], ['produktlinie', 'produktlinie'], ['werkstoff', 'werkstoffName']] as const) {
          const value = url.searchParams.get(query); if (value) found = found.filter(article => article[field] === value);
        }
        return json(res, { artikel: paginate(found, url), gesamt: found.length });
      }
      if (path === '/api/bestellungen/zeugnis-default') return json(res, { zeugnisTyp: url.searchParams.get('excKlasse') ? 'APZ_3_1' : null });
      if (path === '/api/schnitt-achsen') return json(res, [{ id: 501, bildUrl: '/api/preview/schnitt.svg', kategorieId: 302 }]);
      if (path === '/api/schnittbilder') return json(res, [{ id: 601, bildUrlSchnittbild: '/api/preview/schnitt.svg', schnittAchseId: 501, schnittAchseBildUrl: '/api/preview/schnitt.svg' }]);
      if (path === '/api/preview/schnitt.svg') {
        res.setHeader('Content-Type', 'image/svg+xml'); res.end('<svg xmlns="http://www.w3.org/2000/svg" width="180" height="90" viewBox="0 0 180 90"><rect width="180" height="90" fill="#f8fafc"/><path d="M25 65L50 25H155L130 65Z" stroke="#334155" stroke-width="3" fill="none"/><path d="M10 45H170" stroke="#e11d48" stroke-dasharray="5 5"/></svg>'); return true;
      }
      if (path === '/api/ids/lieferanten') return json(res, []);
      if (path === '/api/email/from-addresses') return json(res, ['bestellung@example.test']);
      if (path === '/api/email/signatures/default') return json(res, { html: '<p>Mit freundlichen Grüßen<br>Max Mustermann · Musterbetrieb</p>' });
      if (path === '/api/preisanfragen') return json(res, inquiries.filter(item => !url.searchParams.get('status') || item.status === url.searchParams.get('status')));
      const supplier = path.match(/^\/api\/lieferanten\/(\d+)$/);
      if (supplier) return json(res, required(suppliers.find(item => item.id === Number(supplier[1])), 'Lieferant'));
      const projectPdf = path.match(/^\/api\/bestellungen\/projekt\/(\d+)\/(bedarfsliste-pdf|pdf)$/);
      if (projectPdf) return pdf(res, { art: projectPdf[2] === 'bedarfsliste-pdf' ? 'bedarf' : 'bestellung', variante: 'projekt', id: Number(projectPdf[1]), bestellungen: rows.filter(row => row.projektId === Number(projectPdf[1]) && !row.bestellt) });
      const supplierPdf = path.match(/^\/api\/bestellungen\/lieferant\/(\d+)\/pdf$/);
      if (supplierPdf) return pdf(res, { art: 'bestellung', id: Number(supplierPdf[1]), bestellungen: rows.filter(row => row.lieferantId === Number(supplierPdf[1]) && !row.bestellt) });
      const inquiryPdf = path.match(/^\/api\/preisanfragen\/lieferant\/(\d+)\/pdf$/);
      if (inquiryPdf) {
        const inquiry = required(inquiries.find(item => item.lieferanten.some(supplier => supplier.id === Number(inquiryPdf[1]))), 'Preisanfrage');
        const supplier = inquiry.lieferanten.find(item => item.id === Number(inquiryPdf[1]))!;
        return pdf(res, { art: 'preisanfrage', id: supplier.id, bestellungen: rows, preisanfrage: { ...inquiry, token: supplier.token }, positionen: inquiry.positionen });
      }
      const compare = path.match(/^\/api\/preisanfragen\/(\d+)\/vergleich$/);
      if (compare) return json(res, comparison(required(inquiries.find(item => item.id === Number(compare[1])), 'Preisanfrage')));
      if (path === '/api/bestellungen-uebersicht/kostenstellen') return json(res, [{ id: 701, bezeichnung: 'Werkstatt', nummer: 'K-701', beschreibung: 'Allgemeines Werkstattmaterial' }]);
      if (path === '/api/bestellungen-uebersicht') return json(res, overview());
      const document = path.match(/^\/api\/bestellungen-uebersicht\/geschaeftsdaten\/(\d+)$/);
      if (document) return json(res, { id: Number(document[1]), dokumentNummer: 'DEMO-RE-2026-001', dokumentDatum: '2026-09-24', betragNetto: 100, betragBrutto: 119, mwstSatz: 19, liefertermin: null, bestellnummer: 'DEMO-B-001', lieferantId: 201, lieferantName: 'Musterstahl Handel' });
    }
    if (method === 'POST' && path === '/api/bestellungen/manuell') {
      const row = makeRow(await body(req)); rows.push(row); return json(res, row);
    }
    const rowPath = path.match(/^\/api\/bestellungen\/(\d+)(\/freitext|\/aus-lager)?$/);
    if (rowPath && ['PUT', 'PATCH', 'DELETE', 'POST'].includes(method)) {
      const row = required(rows.find(item => item.id === Number(rowPath[1])), 'Position');
      if (method === 'PATCH') { row.bestellt = url.searchParams.get('bestellt') === 'true'; row.bestelltAm = row.bestellt ? now().slice(0, 10) : null; return json(res, row); }
      if (rowPath[2] === '/aus-lager') { row.bestellt = method === 'POST'; return json(res, row); }
      if (row.exportiertAm) fail('Bereits exportierte Position kann nicht geändert werden.', 409);
      if (method === 'DELETE') { rows = rows.filter(item => item !== row); return json(res, {}); }
      if (method === 'PUT') { const updated = makeRow({ ...row, ...await body(req) }, row.id); rows[rows.indexOf(row)] = updated; return json(res, updated); }
    }
    const exported = path.match(/^\/api\/bestellungen\/lieferant\/(\d+)\/markiere-exportiert$/);
    if (exported && method === 'POST') { rows.filter(row => row.lieferantId === Number(exported[1]) && !row.bestellt).forEach(row => { row.exportiertAm = now().slice(0, 19); }); return json(res, {}); }
    const supplierEmail = path.match(/^\/api\/lieferanten\/(\d+)\/emails$/);
    if (supplierEmail && method === 'POST') {
      const supplier = required(suppliers.find(item => item.id === Number(supplierEmail[1])), 'Lieferant');
      const { email } = await body(req); if (typeof email !== 'string' || !email.includes('@')) fail('E-Mail-Adresse fehlt.');
      if (!supplier.kundenEmails.includes(String(email))) supplier.kundenEmails.push(String(email)); return json(res, supplier);
    }
    if (path === '/api/email/beautify' && method === 'POST') { const input = await body(req); return json(res, { suggestion: input.text ?? '' }); }
    if (path === '/api/emails/send' && method === 'POST') {
      const form = await new Response(new Uint8Array(await bytes(req)), { headers: { 'content-type': req.headers['content-type'] ?? '' } }).formData();
      const part = form.get('dto');
      const dto = object(JSON.parse(typeof part === 'string' ? part : await (part as Blob).text()));
      emails.push({ ...dto, id: nextId++, simulated: true, erstelltAm: now() }); return json(res, { simulated: true });
    }
    if (path === '/api/preisanfragen' && method === 'POST') {
      const input = await body(req);
      const selected = Array.isArray(input.lieferantIds) ? input.lieferantIds : [];
      if (!selected.length || !list(input.positionen).length) fail('Lieferanten und Positionen erforderlich.');
      const recipients = input.empfaengerProLieferant ? object(input.empfaengerProLieferant) : {};
      const inquiry: Inquiry = { id: nextId++, nummer: `PA-2026-${String(inquiries.length + 1).padStart(4, '0')}`, projektId: refId(input.projektId, projects, 'Projekt'), bauvorhaben: input.bauvorhaben ?? null, erstelltAm: now(), antwortFrist: input.antwortFrist ?? null, notiz: input.notiz ?? null, status: 'OFFEN',
        lieferanten: selected.map(value => {
          const supplier = required(suppliers.find(item => item.id === value), 'Lieferant'); const id = nextId++;
          return { id, lieferantId: supplier.id, lieferantenname: supplier.lieferantenname, token: `preview-only-${id}`, status: 'VORBEREITET', versendetAn: recipients[String(supplier.id)] ?? supplier.kundenEmails[0], versendetAm: null, antwortErhaltenAm: null };
        }), positionen: list(input.positionen).map(position => ({ ...position, id: nextId++, menge: positive(position.menge) })) };
      inquiries.push(inquiry); return json(res, inquiry);
    }
    if (path === '/api/preisanfragen/angebote' && method === 'POST') {
      const input = await body(req);
      const inquiry = required(inquiries.find(item => item.lieferanten.some(supplier => supplier.id === input.preisanfrageLieferantId)), 'Preisanfrage');
      required(inquiry.positionen.find(item => item.id === input.preisanfragePositionId), 'Position');
      if (typeof input.einzelpreis !== 'number' || !Number.isFinite(input.einzelpreis) || input.einzelpreis < 0) fail('Preis ist ungültig.');
      offers = offers.filter(offer => offer.preisanfrageLieferantId !== input.preisanfrageLieferantId || offer.preisanfragePositionId !== input.preisanfragePositionId); offers.push(input);
      const supplier = inquiry.lieferanten.find(item => item.id === input.preisanfrageLieferantId)!; supplier.status = 'BEANTWORTET'; supplier.antwortErhaltenAm = now();
      inquiry.status = inquiry.lieferanten.every(item => item.status === 'BEANTWORTET') ? 'VOLLSTAENDIG' : 'TEILWEISE_BEANTWORTET'; return json(res, input);
    }
    const inquiryPath = path.match(/^\/api\/preisanfragen\/(\d+)(?:\/(versenden|vergleich|angebote\/extrahieren|vergeben\/\d+))?$/);
    if (inquiryPath && ['POST', 'DELETE'].includes(method)) {
      const inquiry = required(inquiries.find(item => item.id === Number(inquiryPath[1])), 'Preisanfrage');
      if (method === 'DELETE') { inquiry.status = 'ABGEBROCHEN'; return json(res, inquiry); }
      if (inquiryPath[2] === 'versenden') { inquiry.lieferanten.forEach(supplier => { supplier.status = 'VERSENDET'; supplier.versendetAm = now(); }); return json(res, inquiry); }
      if (inquiryPath[2] === 'angebote/extrahieren') return json(res, { verarbeiteteLieferanten: 0, extrahierteAngebote: 0, fehler: 0 });
      if (inquiryPath[2]?.startsWith('vergeben/')) {
        const supplier = required(inquiry.lieferanten.find(item => item.id === Number(inquiryPath[2]?.split('/')[1])), 'Lieferant');
        inquiry.status = 'VERGEBEN'; inquiry.vergebenAnPreisanfrageLieferantId = supplier.id;
        inquiry.positionen.forEach(position => {
          const row = rows.find(item => item.id === position.artikelInProjektId);
          if (row) { row.lieferantId = supplier.lieferantId; row.lieferantName = supplier.lieferantenname; row.bestellt = false; }
          else rows.push(makeRow({ ...position, projektId: inquiry.projektId, lieferantId: supplier.lieferantId }));
        }); return json(res, inquiry);
      }
    }
    if (path === '/api/bestellungen-uebersicht/zuordnen' && method === 'POST') { assignments.push(await body(req)); return json(res, {}); }
    if (path.startsWith('/api/bestellungen/import/hicad/') && method === 'POST') return hicad(req, res, path);
    return false;
  } catch (error) {
    const status = error instanceof Error && 'status' in error ? Number(error.status) : 400;
    const message = error instanceof Error ? error.message : 'Vorschauanfrage fehlgeschlagen.';
    // Headers are ASCII-only; the JSON response retains the complete German explanation.
    res.setHeader('X-Error-Reason', message.replace(/[^\x20-\x7E]/g, '?'));
    return json(res, { message }, status);
  }
}
function overview() {
  const chain = { id: 'demo-rechnung-801', lieferantId: 201, lieferantName: 'Musterstahl Handel', dokumente: [{ id: 801, typ: 'RECHNUNG', dokumentNummer: 'DEMO-RE-2026-001', dokumentDatum: '2026-09-24', betragBrutto: 119, betragNetto: 100, liefertermin: null, dateiname: 'Demo-Rechnung.pdf', pdfUrl: null }] };
  return { offeneAnfragen: [], laufendeBestellungen: [], abgeschlossen: assignments.some(item => item.geschaeftsdokumentId === 801) ? [] : [chain], zugeordnet: assignments.some(item => item.geschaeftsdokumentId === 801) ? [chain] : [] };
}
async function hicad(req: IncomingMessage, res: ServerResponse, path: string): Promise<boolean> {
  if (path.endsWith('/preview')) {
    await bytes(req);
    return json(res, { zeichnungsnr: 'DEMO-Z-101', auftragsnummer: 'P-2026-101', auftragstext: 'Werkhalle Musterstadt', kunde: 'Max Mustermann', ersteller: 'Max Mustermann', erstelltAm: '2026-09-24', erkannteProjektId: 101, erkannteProjektName: 'Werkhalle Musterstadt', gruppen: [{ groupKey: 'demo-rohr', bezeichnung: 'Rechteckrohr 60 × 40 × 3', werkstoff: 'S235JR', artikelId: 401, artikelProduktname: articles[0].produktname, verpackungseinheitM: 6, defaultAggregieren: true, summeMeter: 6, summeStueck: 3, berechneteStaebe: 1, zeilen: [{ posNr: '1', anzahl: 3, bezeichnung: 'Rechteckrohr 60 × 40 × 3', laengeMm: 2000, werkstoff: 'S235JR', gewichtProStueckKg: 8.5, gesamtGewichtKg: 25.5 }] }] });
  }
  const input = await body(req);
  if (path.endsWith('/confirm')) {
    const created = list(input.gruppen).map(group => {
      const preview = list(input.preview).find(item => item.groupKey === group.groupKey);
      return makeRow({ projektId: group.projektId ?? input.projektId, lieferantId: group.lieferantId, artikelId: group.artikelId, kategorieId: group.kategorieId, produktname: preview?.bezeichnung ?? 'HiCAD-Vorschauprofil', menge: group.aggregieren ? Number(preview?.summeMeter ?? 6) : Number(preview?.summeStueck ?? 3), einheit: group.aggregieren ? 'm' : 'Stück', kommentar: input.kommentarPrefix ?? 'HiCAD-Dummy-Import', fixmassMm: Number(group.stangenlaengeM ?? 6) * 1000 });
    });
    rows.push(...created); return json(res, { angelegtePositionen: created.length });
  }
  if (path.endsWith('/optimiere')) {
    const length = positive(input.stangenlaengeM) * 1000;
    const cuts = list(input.zeilen).flatMap(row => Array.from({ length: Math.min(10000, Math.max(0, Number(row.anzahl) || 0)) }, () => Number(row.laengeMm) || 0)).sort((a, b) => b - a);
    const bins: number[] = []; let tooLong = 0;
    for (const cut of cuts) {
      if (cut > length) { tooLong++; continue; }
      const index = bins.findIndex(remaining => remaining >= cut);
      if (index < 0) bins.push(length - cut); else bins[index] -= cut;
    }
    return json(res, { anzahlStangen: bins.length, verschnittMm: bins.reduce((sum, rest) => sum + rest, 0), ueberlange: tooLong });
  }
  return false;
}
