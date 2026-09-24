import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, expect, it, vi } from 'vitest';
import { ToastProvider } from '../../../components/ui/toast';
import { ZeugnisZuordnung } from './ZeugnisZuordnung';

const fetchMock = vi.fn();
const deliveries = [
  { id: 81, bestellungId: 73, revisionId: 33, lieferscheinId: 40, eingang: '2026-09-20T10:00:00Z', positionen: [{ id: 91, bestellPositionId: 9, menge: 1, charge: 'CH-1', schmelznummer: 'SM-1', projektAnteile: [], chargen: [{ id: 101, kennung: 'CH-1', schmelznummer: 'SM-1' }] }] },
  { id: 82, bestellungId: 73, revisionId: 33, lieferscheinId: 41, eingang: '2026-09-22T10:00:00Z', positionen: [{ id: 92, bestellPositionId: 10, menge: 1, charge: 'CH-2', schmelznummer: 'SM-2', projektAnteile: [], chargen: [{ id: 102, kennung: 'CH-2', schmelznummer: 'SM-2' }] }] },
];
const expectations = [
  { id: 111, version: 1, revisionId: 33, bestellPositionId: 9, art: 'ZEUGNIS_3_1', grundlage: 'EN 10204', grundlageVersion: 'V3', frist: null, status: 'ERWARTET', dateiIds: [], lieferPositionIds: [], chargeIds: [], materialFreigegeben: false, chargeStaende: [{ chargeId: 101, status: 'ERWARTET', version: 1, materialFreigegeben: false }] },
  { id: 112, version: 1, revisionId: 33, bestellPositionId: 10, art: 'ZEUGNIS_3_1', grundlage: 'EN 10204', grundlageVersion: 'V3', frist: null, status: 'ERWARTET', dateiIds: [], lieferPositionIds: [], chargeIds: [], materialFreigegeben: false, chargeStaende: [{ chargeId: 102, status: 'ERWARTET', version: 1, materialFreigegeben: false }] },
];

beforeEach(() => {
  fetchMock.mockReset();
  fetchMock.mockImplementation(async (input: RequestInfo | URL, init?: RequestInit) => {
    const path = String(input);
    if (path === '/api/einkauf/berechtigungen') return new Response(JSON.stringify(['LESEN', 'BEARBEITEN']), { status: 200 });
    if (path.endsWith('/73/lieferungen') && init?.method !== 'POST') return new Response(JSON.stringify(deliveries), { status: 200 });
    if (path === '/api/einkauf/zeugnisse?bestellungId=73') return new Response(JSON.stringify(expectations), { status: 200 });
    if (path.endsWith('/73/belege')) return new Response(JSON.stringify([{ lieferantDokumentId: 42, typ: 'SONSTIG', dateiname: 'Mehrere-Chargen.pdf', verfuegbar: true }]), { status: 200 });
    if (path.endsWith('/belege/42/datei')) return new Response(JSON.stringify({ dateiId: 501, lieferantDokumentId: 42, typ: 'SONSTIG', dateiname: 'Mehrere-Chargen.pdf', verfuegbar: true }), { status: 200 });
    if (/\/zeugnisse\/(111|112)\/eingang\/501$/.test(path)) return new Response(JSON.stringify(expectations[0]), { status: 200 });
    if (path === '/api/einkauf/zeugnisse/501/zuordnen') return new Response(JSON.stringify({ erwartungen: expectations, klaerungNoetig: false, chargen: [
      { zuordnungId: 1001, erwartungId: 111, chargeId: 101, status: 'ZUGEORDNET', version: 1, materialFreigegeben: false },
      { zuordnungId: 1002, erwartungId: 112, chargeId: 102, status: 'ZUGEORDNET', version: 1, materialFreigegeben: false },
    ] }), { status: 200 });
    return new Response('{}', { status: 200 });
  });
  global.fetch = fetchMock as typeof fetch;
});

it('ordnet ein PDF in einer Aktion mehreren passenden Lieferpositionen und Chargen zu', async () => {
  render(<ToastProvider><ZeugnisZuordnung bestellungId={73} /></ToastProvider>);

  expect((await screen.findAllByText(/EN 10204/)).length).toBe(2);
  await userEvent.click(await screen.findByRole('button', { name: /Mehrere-Chargen\.pdf zuordnen/ }));
  await userEvent.click(screen.getByRole('button', { name: 'Zeugnis zuordnen' }));

  await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/einkauf/zeugnisse/501/zuordnen', expect.objectContaining({ method: 'POST' })));
  const assignment = fetchMock.mock.calls.find(call => call[0] === '/api/einkauf/zeugnisse/501/zuordnen');
  expect(JSON.parse(assignment![1].body as string)).toMatchObject({ dokumentId: 501, erwartungIds: [111, 112], lieferPositionIds: [91, 92], chargeIds: [101, 102] });
  expect(await screen.findByText(/Zeugnis zugeordnet/)).toBeInTheDocument();
});

it('zeigt keine Freigabeaktion für Nutzer ohne Zeugnis-Prüfrecht', async () => {
  render(<ToastProvider><ZeugnisZuordnung bestellungId={73} /></ToastProvider>);

  await screen.findAllByText(/EN 10204/);
  expect(screen.queryByRole('button', { name: /Zeugnis prüfen/ })).not.toBeInTheDocument();
  expect(screen.getAllByText(/Nur Personen mit Zeugnis-Prüfrecht/)).toHaveLength(1);
});
