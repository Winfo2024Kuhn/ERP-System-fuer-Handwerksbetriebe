import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, expect, it, vi } from 'vitest';
import { ToastProvider } from '../components/ui/toast';
import BestellungenUebersicht from './BestellungenUebersicht';

beforeEach(() => {
  global.fetch = vi.fn(async (input: RequestInfo | URL) => {
    const path = String(input);
    if (path === '/api/einkauf/bestellungen?page=0&size=20') return { ok: true, json: async () => ({ content: [{ id: 73, nummer: 'B-2026-0073', lieferantId: 8, status: 'BESTELLT', lieferantenStatus: 'AUSSTEHEND', angelegtAm: '2026-09-24' }], totalPages: 1, totalElements: 1 }) } as Response;
    if (path.includes('belege-offen')) return { ok: true, json: async () => [] } as Response;
    return { ok: true, json: async () => ({ offeneAnfragen: [], laufendeBestellungen: [], abgeschlossen: [], zugeordnet: [], ausgeblendet: [] }) } as Response;
  });
});

it('zeigt echte Bestellungen getrennt von bisherigen Belegketten und verlinkt das Detail', async () => {
  render(<MemoryRouter><ToastProvider><BestellungenUebersicht /></ToastProvider></MemoryRouter>);
  expect(await screen.findByText('B-2026-0073')).toBeInTheDocument();
  expect(screen.getByRole('link', { name: /B-2026-0073/ })).toHaveAttribute('href', '/bestellungen/73');
  expect(screen.getByText('Bisherige Belege')).toBeInTheDocument();
});
