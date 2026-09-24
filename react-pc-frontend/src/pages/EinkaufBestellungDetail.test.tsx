import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, expect, it, vi } from 'vitest';
import { ToastProvider } from '../components/ui/toast';
import EinkaufBestellungDetail from './EinkaufBestellungDetail';

beforeEach(() => {
  global.fetch = vi.fn(async () => ({ ok: true, json: async () => ({
    id: 73, version: 4, nummer: 'B-2026-0073', lieferantId: 8, angebotsversionId: 22,
    anfrageRevisionId: 13, empfaenger: { lieferantenname: 'Musterstahl GmbH', email: 'einkauf@example.test' },
    status: 'ENTWURF', lieferantenStatus: 'AUSSTEHEND', revisionen: [{
      id: 4, nummer: 2, version: 4, snapshot: { liefertermin: '2026-10-14', statusText: 'Entwurf' },
      sha256: 'hash', versandId: null, verworfen: false, angenommenAm: null, externerNachweis: null,
      positionen: [{ id: 9, snapshot: { bezeichnung: 'Träger Revision B', werkstoff: 'S235JR', basis: { menge: 3, einheit: 'METER' } }, menge: 3, nettoEinzelpreis: 12.5, herkuenfte: [] }],
    }],
  }) } as Response));
});

it('zeigt die Bestellnummer und Position aus dem unveränderlichen Revisionssnapshot', async () => {
  render(<MemoryRouter initialEntries={['/bestellungen/73']}><ToastProvider><Routes>
    <Route path="/bestellungen/:id" element={<EinkaufBestellungDetail />} />
  </Routes></ToastProvider></MemoryRouter>);
  expect(await screen.findByText('Träger Revision B')).toBeInTheDocument();
  expect(screen.getAllByText('B-2026-0073').length).toBeGreaterThan(0);
  expect(screen.getByText('Musterstahl GmbH')).toBeInTheDocument();
});

it('erneuert nur sicher fehlgeschlagenen Versand und lässt unklare Zustellung ohne Nachweis gesperrt', async () => {
  const retryBodies: unknown[] = [];
  global.fetch = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const path = String(input);
    if (path.endsWith('/revisionen/4/versandstatus')) return { ok: true, json: async () => [
      { id: 21, version: 2, typ: 'BESTELLUNG', vorgangId: 73, revisionId: 4, status: 'FEHLGESCHLAGEN', fehlerCode: 'SMTP_UNAVAILABLE', erstelltAm: '2026-09-24T08:00:00Z', angenommenAm: null, archiviert: false, messageId: null },
      { id: 22, version: 1, typ: 'BESTELLUNG', vorgangId: 73, revisionId: 4, status: 'UNKLAR', fehlerCode: 'CONNECTION_LOST', erstelltAm: '2026-09-24T08:05:00Z', angenommenAm: null, archiviert: false, messageId: null },
    ] } as Response;
    if (path.endsWith('/belege')) return { ok: true, json: async () => [] } as Response;
    if (path.endsWith('/storno-anfragen')) return { ok: true, json: async () => [] } as Response;
    if (path.endsWith('/mengen')) return { ok: true, json: async () => [] } as Response;
    if (path.endsWith('/erneut')) { retryBodies.push(JSON.parse(String(init?.body))); return { ok: true, json: async () => ({}) } as Response; }
    return { ok: true, json: async () => ({ id: 73, version: 4, nummer: 'B-2026-0073', lieferantId: 8, empfaenger: { lieferantenname: 'Musterstahl' }, status: 'BESTELLT', lieferantenStatus: 'AUSSTEHEND', revisionen: [{ id: 4, nummer: 2, version: 4, snapshot: {}, sha256: 'hash', verworfen: false, angenommenAm: null, externerNachweis: null, positionen: [] }] }) } as Response;
  });
  render(<MemoryRouter initialEntries={['/bestellungen/73']}><ToastProvider><Routes><Route path="/bestellungen/:id" element={<EinkaufBestellungDetail />} /></Routes></ToastProvider></MemoryRouter>);
  expect(await screen.findByText(/SMTP_UNAVAILABLE/)).toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Erneut senden' })).toBeInTheDocument();
  const unclearEntry = screen.getByText(/CONNECTION_LOST/).closest('li');
  expect(unclearEntry).not.toBeNull();
  expect(within(unclearEntry as HTMLElement).queryByRole('button', { name: /Erneut senden/ })).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: 'Erneut senden' }));
  await waitFor(() => expect(retryBodies).toEqual([{ version: 2 }]));
  expect(screen.getByText(/CONNECTION_LOST/)).toBeInTheDocument();
});
