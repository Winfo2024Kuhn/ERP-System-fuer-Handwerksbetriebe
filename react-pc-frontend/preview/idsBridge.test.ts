import { afterEach, describe, expect, it } from 'vitest';
import { createServer, type Server } from 'node:http';
import { createIdsBridge } from './idsBridge';
import { parseIdsCart, serializeIdsCart } from './idsCart';
import { getProcurementPreviewState, receiveIdsCart, resetProcurementMock } from './procurementMock';

const xml = (flag = 'Warenkorbrückgabe', qty = '2.5', extra = '') => `<?xml version="1.0"?><Warenkorb xmlns="urn:ids:test"><WarenkorbInfo><RueckgabeKZ>${flag}</RueckgabeKZ></WarenkorbInfo><Order><OrderInfo><OrderConfNo>DEMO-42</OrderConfNo></OrderInfo><OrderItem><ArtNo>DEMO-001</ArtNo><Qty>${qty}</Qty><QU>PCE</QU><Kurztext>Dummy Schraube</Kurztext><NetPrice>85.00</NetPrice><PriceBasis>100</PriceBasis>${extra}</OrderItem></Order></Warenkorb>`;
const servers: Server[] = [];
afterEach(async () => { await Promise.all(servers.splice(0).map(server => new Promise<void>(resolve => server.close(() => resolve())))); resetProcurementMock(); });
async function setup(enabled = true, supplierId = 203) {
  const server = createServer((req, res) => { void handle(req, res, new URL(req.url!, 'http://localhost')); });
  servers.push(server);
  await new Promise<void>(resolve => server.listen(0, '127.0.0.1', resolve));
  const address = server.address() as { port: number };
  const origin = `http://127.0.0.1:${address.port}`;
  const handle = createIdsBridge(origin, () => enabled ? { customer: 'dummy', username: 'dummy', password: 'dummy-only' } : null, receiveIdsCart, undefined, undefined, supplierId);
  const start = () => fetch(`${origin}/api/ids/punchout/203/start`, { method: 'POST', headers: { Origin: origin } });
  return { origin, start };
}

describe('Würth IDS local connection', () => {
  it('uses the real configured supplier ID instead of the former preview ID', async () => {
    const { origin } = await setup(true, 1);
    expect(await (await fetch(`${origin}/api/ids/lieferanten`)).json()).toEqual([{ id: 1, name: 'Würth' }]);
    const response = await fetch(`${origin}/api/ids/punchout/1/start`, { method: 'POST', headers: { Origin: origin } });
    expect(response.status).toBe(200);
    const form = await response.json();
    expect(new URL(form.fields.hookurl).pathname).toBe('/api/ids/punchout/1/return');
    expect((await fetch(`${origin}/api/ids/punchout/203/start`, { method: 'POST', headers: { Origin: origin } })).status).toBe(405);
  });
  it('provides multipart fields, no-store and a single-use callback that imports quantities and prices', async () => {
    const { origin, start } = await setup();
    expect(await (await fetch(`${origin}/api/ids/lieferanten`)).json()).toEqual([{ id: 203, name: 'Würth' }]);
    const response = await start();
    expect(response.headers.get('cache-control')).toBe('no-store');
    const form = await response.json();
    expect(form.enctype).toBe('multipart/form-data');
    expect(new URL(form.action).origin).toBe('https://eshop.wuerth.de');
    expect(Object.keys(form.fields)).toEqual(['action', 'kndnr', 'name_kunde', 'pw_kunde', 'hookurl']);
    expect(form.fields.action).toBe('WKE');
    const body = new FormData(); body.set('warenkorb', xml());
    const returned = await fetch(form.fields.hookurl, { method: 'POST', body, redirect: 'manual' });
    expect(returned.status).toBe(303);
    const row = getProcurementPreviewState().bestellungen.find(row => row.externeArtikelnummer === 'DEMO-001');
    // NetPrice ist bei Würth der Netto-Betrag der ganzen Position: 85,00 für 2,5 Stück = 34,00 je Stück.
    expect(row).toMatchObject({ menge: 2.5, preisProEinheit: 34, lieferantId: 203, bestellt: false, projektId: null });
    expect((await fetch(form.fields.hookurl, { method: 'POST', body, redirect: 'manual' })).status).toBe(403);
  });
  it('rejects foreign or missing origins and forged tokens', async () => {
    const { origin } = await setup();
    for (const headers of [{}, { Origin: 'https://example.test' }]) {
      expect((await fetch(`${origin}/api/ids/punchout/203/start`, { method: 'POST', headers })).status).toBe(403);
    }
    expect((await fetch(`${origin}/api/ids/punchout/203/start`)).status).toBe(405);
    expect((await fetch(`${origin}/api/ids/punchout/203/return?token=fake`, { method: 'POST' })).status).toBe(403);
  });
  it('rejects invalid carts without creating rows and does not echo XML', async () => {
    const { start } = await setup();
    const form = await (await start()).json();
    const body = new URLSearchParams({ warenkorb: xml('Warenkorbrückgabe', '-1') });
    const response = await fetch(form.fields.hookurl, { method: 'POST', body });
    expect(response.status).toBe(400);
    expect(await response.text()).not.toContain('DEMO-001');
    expect(getProcurementPreviewState().bestellungen).toHaveLength(4);
  });
  it('does not advertise incomplete credentials', async () => {
    const { origin, start } = await setup(false);
    expect(await (await fetch(`${origin}/api/ids/lieferanten`)).json()).toEqual([]);
    expect((await start()).status).toBe(409);
  });
  it('handles already-ordered returns without exposing them as open need', () => {
    receiveIdsCart(parseIdsCart(xml('Warenkorbrückgabe mit Bestellung')));
    expect(getProcurementPreviewState().bestellungen.at(-1)).toMatchObject({ bestellt: true, idsPriceBasis: 100 });
  });
  it('accepts missing prices, rejects entities, empty carts, malformed numbers and unknown return flags', () => {
    expect(parseIdsCart(xml().replace('<NetPrice>85.00</NetPrice>', '')).items[0].netPrice).toBeNull();
    for (const invalid of [xml('unknown'), xml('Warenkorbrückgabe', '1,2'), xml().replace('<PriceBasis>100</PriceBasis>', '<PriceBasis>0</PriceBasis>'), '<Warenkorb/>', '<!DOCTYPE Warenkorb [<!ENTITY x SYSTEM "file:///etc/passwd">]>' + xml().replace('<?xml version="1.0"?>', ''), xml().replace('</Warenkorb>', '')]) {
      expect(() => parseIdsCart(invalid)).toThrow();
    }
  });
});


describe('IDS review and send-back', () => {
  it('retains details and emits a send cart without a return or order flag', () => {
    const cart = parseIdsCart(xml('Warenkorbrückgabe', '2.5', '<Langtext>Langer Text &amp; Zubehör</Langtext><EAN>0012345678901</EAN><VAT>19</VAT><RefItems><Customer>01</Customer><Supplier>02</Supplier></RefItems>'));
    expect(cart.items[0]).toMatchObject({ description: 'Langer Text & Zubehör', ean: '0012345678901', vat: 19, unitCode: 'PCE', customerReference: '01', supplierReference: '02' });
    const sent = serializeIdsCart(cart, 'B-2026-000001');
    expect(sent).toContain('<PartNo>B-2026-000001</PartNo>');
    expect(sent).toContain('<Qty>2.5</Qty>');
    expect(sent).toContain('<Customer>01</Customer>');
    expect(sent).toContain('Langer Text &amp; Zubehör');
    expect(sent).not.toContain('RueckgabeKZ');
  });
  it('redirects to review, sends WKS and replaces the same cart on confirmed return', async () => {
    const { origin, start } = await setup();
    const initial = await (await start()).json();
    const returned = await fetch(initial.fields.hookurl, { method: 'POST', body: new URLSearchParams({ warenkorb: xml() }), redirect: 'manual' });
    const location = returned.headers.get('location')!;
    expect(location).toMatch(/^\/bestellungen\/ids\/[^/]+$/);
    const id = location.split('/').at(-1);
    const reviewed = await (await fetch(`${origin}/api/ids/warenkoerbe/${id}`)).json();
    expect(reviewed.items).toHaveLength(1);
    const outbound = await (await fetch(`${origin}/api/ids/warenkoerbe/${id}/senden`, { method: 'POST', headers: { Origin: origin } })).json();
    expect(outbound.fields.action).toBe('WKS');
    expect(outbound.fields.warenkorb).toContain(`<PartNo>${reviewed.number}</PartNo>`);
    const confirmed = await fetch(outbound.fields.hookurl, { method: 'POST', body: new URLSearchParams({ warenkorb: xml('Warenkorbrückgabe mit Bestellung') }), redirect: 'manual' });
    expect(confirmed.headers.get('location')).toBe(location);
    expect(getProcurementPreviewState().bestellungen.filter(row => row.externeArtikelnummer === 'DEMO-001')).toHaveLength(1);
    expect((await (await fetch(`${origin}/api/ids/warenkoerbe/${id}`)).json()).ordered).toBe(true);
    expect((await fetch(`${origin}/api/ids/warenkoerbe/${id}/senden`, { method: 'POST', headers: { Origin: origin } })).status).toBe(409);
  });
});

describe('Warenkorb am Projekt parken', () => {
  const startMit = (origin: string, body: string) => fetch(`${origin}/api/ids/punchout/203/start`, { method: 'POST', headers: { Origin: origin, 'Content-Type': 'application/json' }, body });
  it('merkt sich das Projekt beim Start, speichert es beim Rücksprung und leitet zurück auf den Projektbedarf', async () => {
    const { origin } = await setup();
    const form = await (await startMit(origin, JSON.stringify({ projektId: 7, projektName: '  Musterhaus Max Mustermann  ' }))).json();
    expect(Object.keys(form.fields)).toEqual(['action', 'kndnr', 'name_kunde', 'pw_kunde', 'hookurl']);
    const returned = await fetch(form.fields.hookurl, { method: 'POST', body: new URLSearchParams({ warenkorb: xml() }), redirect: 'manual' });
    expect(returned.status).toBe(303);
    const location = returned.headers.get('location')!;
    const ziel = new URL(location, origin);
    expect(ziel.pathname).toBe('/bestellungen/bedarf/projekt/7');
    const id = ziel.searchParams.get('warenkorb')!;
    const gespeichert = await (await fetch(`${origin}/api/ids/warenkoerbe/${id}`)).json();
    expect(gespeichert).toMatchObject({ projektId: 7, projektName: 'Musterhaus Max Mustermann' });
    expect(await (await fetch(`${origin}/api/ids/warenkoerbe?projektId=7`)).json()).toHaveLength(1);
    expect(await (await fetch(`${origin}/api/ids/warenkoerbe?projektId=8`)).json()).toEqual([]);

    // Erneutes Senden (WKS) und Rückgabe mit Bestellung behält die Zuordnung.
    const outbound = await (await fetch(`${origin}/api/ids/warenkoerbe/${id}/senden`, { method: 'POST', headers: { Origin: origin } })).json();
    expect(outbound.fields.action).toBe('WKS');
    const bestaetigt = await fetch(outbound.fields.hookurl, { method: 'POST', body: new URLSearchParams({ warenkorb: xml('Warenkorbrückgabe mit Bestellung') }), redirect: 'manual' });
    expect(bestaetigt.headers.get('location')).toBe(location);
    expect(await (await fetch(`${origin}/api/ids/warenkoerbe/${id}`)).json()).toMatchObject({ ordered: true, projektId: 7, projektName: 'Musterhaus Max Mustermann' });
  });
  it('lehnt ungültige Projektangaben mit 400 ab', async () => {
    const { origin } = await setup();
    const ungueltig = [
      JSON.stringify({ projektId: 0 }), JSON.stringify({ projektId: -3 }), JSON.stringify({ projektId: 1.5 }),
      JSON.stringify({ projektId: '7' }), JSON.stringify({ projektId: Number.MAX_SAFE_INTEGER + 1 }),
      JSON.stringify({ projektId: 7, projektName: 'x'.repeat(201) }), JSON.stringify({ projektId: 7, projektName: 42 }),
      JSON.stringify({ projektName: 'ohne Projekt' }), JSON.stringify([7]), '{kaputt', JSON.stringify({ projektId: 7, projektName: 'y'.repeat(3000) }),
    ];
    for (const body of ungueltig) expect((await startMit(origin, body)).status, body.slice(0, 40)).toBe(400);
    for (const query of ['0', '-1', 'abc', '1.5', "7'; DROP TABLE x; --", '99999999999999999']) {
      expect((await fetch(`${origin}/api/ids/warenkoerbe?projektId=${encodeURIComponent(query)}`)).status).toBe(400);
    }
  });
  it('ohne Projekt bleibt alles wie bisher', async () => {
    const { origin, start } = await setup();
    const form = await (await start()).json();
    const returned = await fetch(form.fields.hookurl, { method: 'POST', body: new URLSearchParams({ warenkorb: xml() }), redirect: 'manual' });
    expect(returned.headers.get('location')).toMatch(/^\/bestellungen\/ids\/[^/?]+$/);
    const alle = await (await fetch(`${origin}/api/ids/warenkoerbe`)).json();
    expect(alle).toHaveLength(1);
    expect(alle[0].projektId).toBeUndefined();
  });
});
