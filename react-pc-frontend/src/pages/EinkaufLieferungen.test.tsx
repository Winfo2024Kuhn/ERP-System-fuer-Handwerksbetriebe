import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, expect, it, vi } from 'vitest';
import { ToastProvider } from '../components/ui/toast';
import EinkaufLieferungen from './EinkaufLieferungen';

beforeEach(() => {
  global.fetch = vi.fn(async (input: RequestInfo | URL) => {
    const path = String(input);
    if (path.startsWith('/api/einkauf/bestellungen?')) return { ok: true, json: async () => ({ content: [{ id: 73, nummer: 'B-2026-0073', lieferantId: 8, status: 'TEILGELIEFERT', lieferantenStatus: 'BESTAETIGT', angelegtAm: '2026-09-24' }] }) } as Response;
    if (path === '/api/einkauf/bestellungen/73') return { ok: true, json: async () => ({ id: 73, version: 5, nummer: 'B-2026-0073', lieferantId: 8, angebotsversionId: null, anfrageRevisionId: null, empfaenger: {}, status: 'BESTELLT', lieferantenStatus: 'BESTAETIGT', revisionen: [{ id: 33, nummer: 1, version: 1, snapshot: {}, sha256: 'hash', versandId: 19, verworfen: false, angenommenAm: '2026-09-20T09:00:00Z', externerNachweis: null, positionen: [] }] }) } as Response;
    if (path.endsWith('/73/belege')) return { ok: true, json: async () => [] } as Response;
    if (path.endsWith('/73/mengen')) return { ok: true, json: async () => [{ bedarfId: 15, reserviert: 0, bestellt: 4, geliefert: 2, storniert: 0, offen: 2 }] } as Response;
    if (path.endsWith('/73/lieferungen')) return { ok: true, json: async () => [
      { id: 81, bestellungId: 73, revisionId: 3, lieferscheinId: 40, eingang: '2026-09-20T10:00:00Z', positionen: [{ id: 91, bestellPositionId: 9, menge: 1, charge: 'CH-1', schmelznummer: 'SM-1', projektAnteile: [], chargen: [{ id: 101, kennung: 'CH-1', schmelznummer: 'SM-1' }] }] },
      { id: 82, bestellungId: 73, revisionId: 3, lieferscheinId: 41, eingang: '2026-09-22T10:00:00Z', positionen: [{ id: 92, bestellPositionId: 9, menge: 1, charge: 'CH-2', schmelznummer: 'SM-2', projektAnteile: [], chargen: [{ id: 102, kennung: 'CH-2', schmelznummer: 'SM-2' }] }] },
    ] } as Response;
    if (path.includes('/api/einkauf/zeugnisse?')) return { ok: true, json: async () => [{ id: 111, version: 1, revisionId: 3, bestellPositionId: 9, art: 'ZEUGNIS_3_1', grundlage: 'EN 10204', grundlageVersion: '2026', frist: '2026-10-01', status: 'ANGEFORDERT', dateiIds: [], lieferPositionIds: [], chargeIds: [], materialFreigegeben: false, chargeStaende: [{ chargeId: 101, status: 'ANGEFORDERT', version: 1, materialFreigegeben: false }, { chargeId: 102, status: 'ANGEFORDERT', version: 1, materialFreigegeben: false }] }] } as Response;
    if (path.includes('/api/bestellungen-uebersicht')) return { ok: true, json: async () => [] } as Response;
    return { ok: true, json: async () => ({}) } as Response;
  });
});

it('trennt gelieferte Menge, offene Menge und fehlende Chargenzeugnisse', async () => {
  render(<MemoryRouter><ToastProvider><EinkaufLieferungen /></ToastProvider></MemoryRouter>);
  expect(await screen.findByText('B-2026-0073')).toBeInTheDocument();
  await waitFor(() => expect(screen.getByText(/2 von 4/)).toBeInTheDocument());
  expect(screen.getByText(/2 offen/)).toBeInTheDocument();
  expect(screen.getByText(/Zeugnis 3.1 fehlt/)).toBeInTheDocument();
  expect(screen.getByText(/Material nicht freigegeben/)).toBeInTheDocument();
});

it('öffnet den Wareneingang direkt aus der Lieferübersicht', async () => {
  render(<MemoryRouter><ToastProvider><EinkaufLieferungen /></ToastProvider></MemoryRouter>);

  await userEvent.click(await screen.findByRole('button', { name: 'Lieferung erfassen' }));

  expect(await screen.findByRole('dialog')).toBeInTheDocument();
  expect(screen.getByText(/Zeugnisse werden danach separat zugeordnet und geprüft/)).toBeInTheDocument();
});
