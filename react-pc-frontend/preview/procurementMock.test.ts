import { afterAll, beforeAll, beforeEach, describe, expect, it } from 'vitest';
import { createServer, type Server } from 'node:http';
import { handleProcurementMock, resetProcurementMock, getProcurementPreviewState, receiveIdsCart } from './procurementMock';

let server: Server;
let origin: string;
const request = (path: string, method = 'GET', body?: unknown) => fetch(`${origin}${path}`, {
  method, headers: { 'content-type': 'application/json' }, body: body === undefined ? undefined : JSON.stringify(body),
});
beforeAll(async () => {
  server = createServer(async (req, res) => {
    if (!await handleProcurementMock(req, res, new URL(req.url!, 'http://localhost'))) { res.statusCode = 404; res.end('Unhandled'); }
  });
  await new Promise<void>(resolve => server.listen(0, '127.0.0.1', resolve));
  const address = server.address();
  if (!address || typeof address === 'string') throw new Error('Missing test address');
  origin = `http://127.0.0.1:${address.port}`;
});
afterAll(async () => { await new Promise<void>((resolve, reject) => server.close(error => error ? reject(error) : resolve())); });
beforeEach(() => resetProcurementMock());

describe('original procurement preview contract', () => {
  it('imports a parked IDS cart into its project instead of stock', async () => {
    const cart = { ordered: false, reference: '', currency: 'EUR', items: [{ article: 'DEMO-P', name: 'Dummy Projektschraube', quantity: 2, unit: 'Stück', netPrice: 4, priceBasis: 1 }] };
    receiveIdsCart({ ...cart, projektId: 101 }, 'cart-projekt');
    receiveIdsCart({ ...cart, items: [{ ...cart.items[0], article: 'DEMO-V' }] }, 'cart-vorrat');
    receiveIdsCart({ ...cart, items: [{ ...cart.items[0], article: 'DEMO-X' }], projektId: 999 }, 'cart-unbekannt');
    const rows: { externeArtikelnummer: string; projektId: number | null; projektName: string | null; preisProEinheit: number | null }[] = await (await request('/api/bestellungen/offen')).json();
    expect(rows.find(row => row.externeArtikelnummer === 'DEMO-P')).toMatchObject({ projektId: 101, projektName: 'Werkhalle Musterstadt', preisProEinheit: 2 });
    expect(rows.find(row => row.externeArtikelnummer === 'DEMO-V')).toMatchObject({ projektId: null });
    expect(rows.find(row => row.externeArtikelnummer === 'DEMO-X')).toMatchObject({ projektId: null });
  });
  it('serves original category DTO and searches articles by category and supplier', async () => {
    const categories = await (await request('/api/kategorien')).json();
    expect(categories).toContainEqual({ id: 302, beschreibung: 'Hohlprofile', parentId: 1 });
    const result = await (await request('/api/artikel?kategorieId=302&lieferant=Musterstahl%20Handel')).json();
    expect(result.gesamt).toBe(1);
    expect(result.artikel[0]).toMatchObject({ id: 401, kategorieId: 302 });
    expect((await request('/api/not-implemented')).status).toBe(404);
  });
  it('keeps original PDF steel-root and cut-quantity fields consistent', async () => {
    const rows = await (await request('/api/bestellungen/offen')).json();
    expect(rows.find((row: { id: number }) => row.id === 1001)).toMatchObject({ rootKategorieId: 1, menge: 12, stueckzahl: 2, fixmassMm: 6000, excKlasse: 'EXC_2', zeugnisAnforderung: 'APZ_3_1' });
  });
  it('persists manual needs, edits, ordered state and deletion over reload', async () => {
    const created = await (await request('/api/bestellungen/manuell', 'POST', {
      projektId: 101, lieferantId: 201, produktname: 'Zusätzliche Lasche', menge: 7.5, einheit: 'm',
    })).json();
    expect(created).toMatchObject({ projektId: 101, projektName: 'Werkhalle Musterstadt', menge: 7.5 });
    expect((await request(`/api/bestellungen/${created.id}`, 'PUT', { produktname: 'Geänderte Lasche', menge: 8 })).ok).toBe(true);
    let rows = await (await request('/api/bestellungen/offen')).json();
    expect(rows.find((row: { id: number }) => row.id === created.id)).toMatchObject({ produktname: 'Geänderte Lasche', menge: 8 });
    await request(`/api/bestellungen/${created.id}?bestellt=true`, 'PATCH');
    rows = await (await request('/api/bestellungen/offen')).json();
    expect(rows.some((row: { id: number }) => row.id === created.id)).toBe(false);
    await request(`/api/bestellungen/${created.id}?bestellt=false`, 'PATCH');
    expect((await request(`/api/bestellungen/${created.id}/freitext`, 'DELETE')).ok).toBe(true);
    expect(getProcurementPreviewState().bestellungen.some(row => row.id === created.id)).toBe(false);
  });
  it('creates, prices and awards an enquiry using original IDs and preserves the chosen supplier', async () => {
    const enquiry = await (await request('/api/preisanfragen', 'POST', {
      projektId: 101, bauvorhaben: 'Werkhalle Musterstadt', antwortFrist: '2026-10-01',
      lieferantIds: [201, 202], empfaengerProLieferant: { 201: 'test@example.test', 202: 'test@example.test' },
      positionen: [{ artikelInProjektId: 1001, produktname: 'Rechteckrohr', menge: 12, einheit: 'm' }],
    })).json();
    const supplier = enquiry.lieferanten[1];
    await request(`/api/preisanfragen/${enquiry.id}/versenden`, 'POST');
    await request('/api/preisanfragen/angebote', 'POST', { preisanfrageLieferantId: supplier.id, preisanfragePositionId: enquiry.positionen[0].id, einzelpreis: 9.5, mwstProzent: 19 });
    const comparison = await (await request(`/api/preisanfragen/${enquiry.id}/vergleich`)).json();
    expect(comparison.positionen[0].zellen[1]).toMatchObject({ einzelpreis: 9.5, gesamtpreis: 114, guenstigster: true });
    expect((await request(`/api/preisanfragen/${enquiry.id}/vergeben/${supplier.id}`, 'POST')).ok).toBe(true);
    const rows = await (await request('/api/bestellungen/offen')).json();
    expect(rows.find((row: { id: number }) => row.id === 1001)).toMatchObject({ lieferantId: 202 });
    const list = await (await request('/api/preisanfragen?status=VERGEBEN')).json();
    expect(list).toHaveLength(1);
    expect(list[0].vergebenAnPreisanfrageLieferantId).toBe(supplier.id);
  });
  it('rejects invalid references and freezes exported material', async () => {
    expect((await request('/api/bestellungen/manuell', 'POST', { produktname: 'Ungültig', menge: 0 })).status).toBe(400);
    expect((await request('/api/bestellungen/manuell', 'POST', { produktname: 'Unbekannt', menge: 1, projektId: 999 })).status).toBe(400);
    await request('/api/bestellungen/lieferant/201/markiere-exportiert', 'POST');
    expect((await request('/api/bestellungen/1001', 'PUT', { menge: 3 })).status).toBe(409);
    expect((await request('/api/bestellungen/1001/freitext', 'DELETE')).status).toBe(409);
  });
  it('records dummy outgoing mail in memory without delivery', async () => {
    const form = new FormData();
    form.append('dto', new Blob([JSON.stringify({ sender: 'test@example.test', recipients: ['test@example.test'], subject: 'Vorschau', body: 'Nur Dummy', lieferantId: 201 })], { type: 'application/json' }), 'blob');
    const response = await fetch(`${origin}/api/emails/send`, { method: 'POST', body: form });
    expect(response.ok).toBe(true);
    expect(getProcurementPreviewState().emails).toHaveLength(1);
    expect(getProcurementPreviewState().emails[0]).toMatchObject({ subject: 'Vorschau', simulated: true });
  });
  it('retains original invoice allocation over reload', async () => {
    let overview = await (await request('/api/bestellungen-uebersicht')).json();
    expect(overview.abgeschlossen[0].dokumente[0].id).toBe(801);
    await request('/api/bestellungen-uebersicht/zuordnen', 'POST', {
      geschaeftsdokumentId: 801, frontendUserProfileId: 1,
      projektAnteile: [{ projektId: 101, betrag: 100, prozentanteil: 100, beschreibung: 'Dummyzuordnung' }],
    });
    overview = await (await request('/api/bestellungen-uebersicht')).json();
    expect(overview.abgeschlossen).toHaveLength(0);
    expect(overview.zugeordnet[0].dokumente[0].id).toBe(801);
  });
  it('imports the deterministic HiCAD preview and calculates cuts locally', async () => {
    const form = new FormData();
    form.append('file', new Blob(['dummy preview fixture']), 'muster.xlsx');
    const preview = await (await fetch(`${origin}/api/bestellungen/import/hicad/preview`, { method: 'POST', body: form })).json();
    expect(preview.erkannteProjektId).toBe(101);
    const optimized = await (await request('/api/bestellungen/import/hicad/optimiere', 'POST', { stangenlaengeM: 6, zeilen: preview.gruppen[0].zeilen })).json();
    expect(optimized).toEqual({ anzahlStangen: 1, verschnittMm: 0, ueberlange: 0 });
    const imported = await (await request('/api/bestellungen/import/hicad/confirm', 'POST', {
      projektId: 101, preview: preview.gruppen,
      gruppen: [{ groupKey: 'demo-rohr', projektId: 101, lieferantId: 201, artikelId: 401, aggregieren: true, stangenlaengeM: 6 }],
    })).json();
    expect(imported.angelegtePositionen).toBe(1);
    expect(getProcurementPreviewState().bestellungen.at(-1)).toMatchObject({ projektId: 101, artikelId: 401, menge: 6, stueckzahl: 1 });
  });

});
