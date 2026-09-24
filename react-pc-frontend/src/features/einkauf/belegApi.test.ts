import { afterEach, expect, it, vi } from 'vitest';
import { ladeBestellBelege, registriereBestellBeleg, ladeBestellBelegHoch, ladeNeueBestellDatei } from './belegApi';

afterEach(() => vi.unstubAllGlobals());

it('lädt fehlende historische Belege mit ihrer echten Verfügbarkeit', async () => {
  const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify([
    { lieferantDokumentId: 41, typ: 'RECHNUNG', dateiname: 'Rechnung-41.pdf', verfuegbar: false },
    { lieferantDokumentId: 42, typ: 'RECHNUNG', dateiname: 'Rechnung-42.pdf', verfuegbar: true },
  ]), { status: 200 }));
  vi.stubGlobal('fetch', fetchMock);

  const belege = await ladeBestellBelege(73);

  expect(fetchMock).toHaveBeenCalledWith('/api/einkauf/bestellungen/73/belege', expect.objectContaining({ method: 'GET' }));
  expect(belege.map(b => [b.lieferantDokumentId, b.verfuegbar])).toEqual([[41, false], [42, true]]);
});

it('registriert einen vorhandenen Lieferantenbeleg und liefert beide unterschiedlichen Datei-IDs', async () => {
  const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({
    dateiId: 501, lieferantDokumentId: 41, typ: 'ZEUGNIS_3_1', dateiname: 'zeugnis.pdf', verfuegbar: true,
  }), { status: 200 }));
  vi.stubGlobal('fetch', fetchMock);

  const datei = await registriereBestellBeleg(73, 41);

  expect(fetchMock).toHaveBeenCalledWith('/api/einkauf/bestellungen/73/belege/41/datei', expect.objectContaining({ method: 'POST' }));
  expect(datei.dateiId).toBe(501);
  expect(datei.lieferantDokumentId).toBe(41);
});

it('liefert den Downloadpfad nur für den Lieferantenbeleg', () => {
  expect(ladeBestellBelegHoch(41)).toBe('/api/lieferant-dokumente/41/download');
});

it('lädt eine PDF ohne selbst gesetzten Multipart-Header als passende Belegart hoch', async () => {
  const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({
    dateiId: 502, lieferantDokumentId: 43, typ: 'RECHNUNG', dateiname: 'Teilrechnung.pdf', verfuegbar: true,
  }), { status: 201 }));
  vi.stubGlobal('fetch', fetchMock);
  const pdf = new File(['%PDF-1.7'], 'Teilrechnung.pdf', { type: 'application/pdf' });

  const result = await ladeNeueBestellDatei(73, 'RECHNUNG', pdf);

  expect(fetchMock).toHaveBeenCalledWith('/api/einkauf/bestellungen/73/belege?typ=RECHNUNG', expect.objectContaining({ method: 'POST' }));
  expect(fetchMock.mock.calls[0][1]?.body).toBeInstanceOf(FormData);
  expect((fetchMock.mock.calls[0][1]?.body as FormData).get('datei')).toBe(pdf);
  expect(result.dateiId).toBe(502);
});
