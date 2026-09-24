import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { ToastProvider } from '../../../components/ui/toast';
import { Rechnungsabgleich } from './Rechnungsabgleich';

const fetchMock = vi.fn();
const order = {
  id: 73, version: 5, nummer: 'B-2026-0073', lieferantId: 8, angebotsversionId: null, anfrageRevisionId: null,
  empfaenger: { lieferantId: 8, kontaktId: null, lieferantenname: 'Testlieferant', email: 'test@example.com', name: null, anrede: null, eigeneKundennummer: null },
  status: 'TEILGELIEFERT', lieferantenStatus: 'BESTAETIGT',
  revisionen: [{ id: 33, nummer: 1, version: 1, snapshot: {}, sha256: 'hash', versandId: 19, verworfen: false, angenommenAm: '2026-09-20T09:00:00Z', externerNachweis: null,
    positionen: [{ id: 9, snapshot: { bezeichnung: 'Stahlträger', basis: { einheit: 'STUECK' }, dokumente: [] }, menge: 4, nettoEinzelpreis: 10, herkuenfte: [{ bedarfId: 15, version: 2, menge: 4 }] }] }],
};
const summary = { bestellungId: 73, positionen: [{ positionId: 9, bezeichnung: 'Stahlträger', einheit: 'STUECK', vereinbart: 4, bestaetigt: 4, geliefert: 2, kumuliertAbgerechnet: 0, offen: 4, abweichungen: [{ positionId: 9, feld: 'menge', vereinbart: 4, abgerechnet: 5, differenz: 1, rechenweg: '5 auf der Rechnung − 4 vereinbart = 1 Stück Abweichung', quellen: [{ typ: 'BELEG', id: 60, bezeichnung: 'R-60', betrag: null }] }], pruefen: true, quellen: [] }], unbelegteDokumentIds: [42] };

beforeEach(() => {
  fetchMock.mockReset();
  fetchMock.mockImplementation(async (input: RequestInfo | URL, init?: RequestInit) => {
    const path = String(input);
    if (path === '/api/einkauf/bestellungen/73/rechnungsabgleich') return new Response(JSON.stringify(summary), { status: 200 });
    if (path === '/api/einkauf/bestellungen/73') return new Response(JSON.stringify(order), { status: 200 });
    if (path.endsWith('/73/belege')) return new Response(JSON.stringify([{ lieferantDokumentId: 42, typ: 'RECHNUNG', dateiname: 'Teilrechnung.pdf', verfuegbar: true }]), { status: 200 });
    if (path.endsWith('/belege/42/datei')) return new Response(JSON.stringify({ dateiId: 502, lieferantDokumentId: 42, typ: 'RECHNUNG', dateiname: 'Teilrechnung.pdf', verfuegbar: true }), { status: 200 });
    if (path === '/api/einkauf/belege/42/zuordnung' && init?.method === 'POST') return new Response(JSON.stringify({ id: 91, dokumentId: 42, bestellungId: 73 }), { status: 200 });
    return new Response('{}', { status: 200 });
  });
  global.fetch = fetchMock as typeof fetch;
});

it('stellt vereinbart, bestätigt, geliefert und abgerechnet samt nachvollziehbarem Rechenweg gegenüber', async () => {
  render(<MemoryRouter><ToastProvider><Rechnungsabgleich bestellungId={73} /></ToastProvider></MemoryRouter>);

  expect(await screen.findByText('Stahlträger')).toBeInTheDocument();
  expect(screen.getByText(/Vereinbart: 4 STUECK/)).toBeInTheDocument();
  expect(screen.getByText(/Bestätigt: 4/)).toBeInTheDocument();
  expect(screen.getByText(/Geliefert: 2/)).toBeInTheDocument();
  expect(screen.getByText(/Abgerechnet: 0/)).toBeInTheDocument();
  expect(screen.getByText(/5 auf der Rechnung − 4 vereinbart = 1 Stück Abweichung/)).toBeInTheDocument();
  expect(screen.getByRole('link', { name: /Rechnung R-60 in den offenen Posten ansehen/ })).toHaveAttribute('href', '/offeneposten?tab=eingang&dokumentId=60');
});

it('erfasst eine Teilrechnung gegen die gewählte Bestellposition und den Beleg', async () => {
  render(<MemoryRouter><ToastProvider><Rechnungsabgleich bestellungId={73} /></ToastProvider></MemoryRouter>);

  await userEvent.click(await screen.findByRole('button', { name: /Teilrechnung\.pdf zuordnen/ }));
  await userEvent.clear(screen.getByLabelText('Rechnungsmenge · Stahlträger'));
  await userEvent.type(screen.getByLabelText('Rechnungsmenge · Stahlträger'), '2');
  await userEvent.clear(screen.getByLabelText('Netto-Einzelpreis · Stahlträger'));
  await userEvent.type(screen.getByLabelText('Netto-Einzelpreis · Stahlträger'), '10');
  await userEvent.click(screen.getByRole('button', { name: 'Rechnung zuordnen' }));

  await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/einkauf/belege/42/zuordnung', expect.objectContaining({ method: 'POST' })));
  const assignment = fetchMock.mock.calls.find(call => call[0] === '/api/einkauf/belege/42/zuordnung');
  expect(JSON.parse(assignment![1].body as string)).toMatchObject({ bestellungId: 73, version: 5, art: 'RECHNUNG', positionen: [{ bestellPositionId: 9, menge: 2, nettoEinzelpreis: 10, preisBasisMenge: 1 }] });
});

it('öffnet die vorhandene Reklamation mit Bestellung, Position und Rechnungsbeleg verknüpft', async () => {
  render(<MemoryRouter><ToastProvider><Rechnungsabgleich bestellungId={73} /></ToastProvider></MemoryRouter>);

  await userEvent.click(await screen.findByRole('button', { name: /Abweichung reklamieren/ }));
  expect(await screen.findByRole('dialog', { name: 'Neue Reklamation erstellen' })).toBeInTheDocument();
  await userEvent.type(screen.getByLabelText('Beschreibung / Grund der Reklamation'), 'Menge auf Teilrechnung stimmt nicht.');
  await userEvent.click(screen.getByRole('button', { name: 'Reklamation erstellen' }));

  await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/reklamationen/lieferant/8', expect.objectContaining({ method: 'POST' })));
  const complaint = fetchMock.mock.calls.find(call => call[0] === '/api/reklamationen/lieferant/8');
  expect(JSON.parse(complaint![1].body as string)).toMatchObject({ bestellungId: 73, bestellPositionId: 9, rechnungId: 60 });
});
