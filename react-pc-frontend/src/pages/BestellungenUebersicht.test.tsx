import { render, screen } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import { ToastProvider } from '../components/ui/toast';
import BestellungenUebersicht from './BestellungenUebersicht';
afterEach(() => vi.unstubAllGlobals());
it('zeigt Original-Bestellketten des Lieferanten mit Dokumentnummer', async () => {
  vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify({ offeneAnfragen: [], laufendeBestellungen: [{ id: 'demo', lieferantId: 8, lieferantName: 'Musterlieferant', dokumente: [{ id: 73, typ: 'AUFTRAGSBESTAETIGUNG', dokumentNummer: 'DEMO-AB-73', dokumentDatum: '2026-09-24', betragBrutto: 119, betragNetto: 100, pdfUrl: null }] }], abgeschlossen: [], zugeordnet: [] }), { status: 200 })));
  render(<ToastProvider><BestellungenUebersicht /></ToastProvider>);
  expect(await screen.findByText('Musterlieferant')).toBeInTheDocument();
  expect(screen.getByText('DEMO-AB-73')).toBeInTheDocument();
  expect(fetch).toHaveBeenCalledWith('/api/bestellungen-uebersicht');
});
