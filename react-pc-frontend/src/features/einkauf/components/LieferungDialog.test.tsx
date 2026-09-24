import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, expect, it, vi } from 'vitest';
import { ToastProvider } from '../../../components/ui/toast';
import { LieferungDialog } from './LieferungDialog';

const fetchMock = vi.fn();
const acceptedOrder = {
  id: 73, version: 5, nummer: 'B-2026-0073', lieferantId: 8, angebotsversionId: null, anfrageRevisionId: null,
  empfaenger: { lieferantId: 8, kontaktId: null, lieferantenname: 'Testlieferant', email: 'test@example.com', name: null, anrede: null, eigeneKundennummer: null },
  status: 'BESTELLT', lieferantenStatus: 'BESTAETIGT',
  revisionen: [{ id: 33, nummer: 1, version: 1, snapshot: {}, sha256: 'hash', versandId: 19, verworfen: false, angenommenAm: '2026-09-20T09:00:00Z', externerNachweis: null,
    positionen: [{ id: 9, snapshot: { bezeichnung: 'Stahlträger', basis: { einheit: 'STUECK' }, dokumente: [] }, menge: 4, nettoEinzelpreis: 10, herkuenfte: [{ bedarfId: 15, version: 2, menge: 4 }] }] }],
};

beforeEach(() => {
  fetchMock.mockReset();
  fetchMock.mockImplementation(async (input: RequestInfo | URL, init?: RequestInit) => {
    const path = String(input);
    if (path === '/api/einkauf/bestellungen/73' && (!init || init.method === 'GET')) return new Response(JSON.stringify(acceptedOrder), { status: 200 });
    if (path.endsWith('/73/lieferungen') && (!init || init.method === 'GET')) return new Response('[]', { status: 200 });
    if (path.endsWith('/73/mengen')) return new Response(JSON.stringify([{ bedarfId: 15, reserviert: 0, bestellt: 4, geliefert: 0, storniert: 0, offen: 4 }]), { status: 200 });
    if (path.endsWith('/73/belege')) return new Response('[]', { status: 200 });
    if (path.endsWith('/73/lieferungen') && init?.method === 'POST') return new Response(JSON.stringify({ id: 81 }), { status: 200 });
    return new Response('{}', { status: 200 });
  });
  global.fetch = fetchMock as typeof fetch;
});

it('erfasst eine Teillieferung mit positiver Menge, Charge und Bestellungsversion', async () => {
  const onSaved = vi.fn();
  render(<ToastProvider><LieferungDialog bestellungId={73} onClose={vi.fn()} onSaved={onSaved} /></ToastProvider>);

  await userEvent.type(await screen.findByLabelText('Menge (STUECK)'), '2');
  await userEvent.type(screen.getByLabelText('Charge'), 'CH-1');
  await userEvent.type(screen.getByLabelText('Schmelznummer'), 'SM-1');
  await userEvent.clear(screen.getByLabelText('Eingangsdatum'));
  await userEvent.type(screen.getByLabelText('Eingangsdatum'), '2026-09-24');
  await userEvent.click(screen.getByRole('button', { name: 'Lieferung erfassen' }));

  await waitFor(() => expect(onSaved).toHaveBeenCalledOnce());
  const post = fetchMock.mock.calls.find(call => String(call[0]).endsWith('/73/lieferungen') && call[1]?.method === 'POST');
  expect(JSON.parse(post![1].body as string)).toMatchObject({ version: 5, positionen: [{ bestellPositionId: 9, menge: 2, charge: 'CH-1', schmelznummer: 'SM-1', projektAnteile: [{ bedarfId: 15, version: 2, menge: 2 }] }] });
});

it('lehnt eine leere Menge vor dem Speichern ab', async () => {
  render(<ToastProvider><LieferungDialog bestellungId={73} onClose={vi.fn()} onSaved={vi.fn()} /></ToastProvider>);

  await screen.findByText(/Stahlträger/);
  await userEvent.click(screen.getByRole('button', { name: 'Lieferung erfassen' }));

  expect(await screen.findByRole('alert')).toHaveTextContent('Bitte geben Sie für mindestens eine Position eine Liefermenge ein.');
  expect(fetchMock).not.toHaveBeenCalledWith('/api/einkauf/bestellungen/73/lieferungen', expect.objectContaining({ method: 'POST' }));
});
