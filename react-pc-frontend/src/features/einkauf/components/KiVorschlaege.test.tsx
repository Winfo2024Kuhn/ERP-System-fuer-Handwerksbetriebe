import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { expect, it, vi } from 'vitest';
import { ToastProvider } from '../../../components/ui/toast';
import { KiVorschlaege } from './KiVorschlaege';

it('übernimmt nur ausdrücklich ausgewählte KI-Felder und hält Korrekturen fest', async () => {
  let payload: Record<string, unknown> | null = null;
  global.fetch = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input);
    if (url.endsWith('/analysen/6')) return { ok: true, json: async () => ({ id: 6, angebotId: 12, status: 'ABGESCHLOSSEN' }) } as Response;
    if (url.endsWith('/analysen/6/vorschlaege')) return { ok: true, json: async () => [{ feldpfad: 'angeb oten', wert: '12,5', quelle: { emailId: 4, zitat: '12,50 EUR/M', seite: 1 }, confidence: 0.9 }] } as Response;
    if (url.endsWith('/angebote/12')) return { ok: true, json: async () => ({ id: 12, versionen: [{ id: 15, version: 7 }] }) } as Response;
    payload = JSON.parse(String(init?.body)); return { ok: true, json: async () => ({ id: 16 }) } as Response;
  });
  render(<ToastProvider><KiVorschlaege jobId={6} onUebernommen={vi.fn()} /></ToastProvider>);
  fireEvent.click(await screen.findByRole('checkbox', { name: /Vorschlag übernehmen/i }));
  fireEvent.change(screen.getByLabelText('Korrektur für angeb oten'), { target: { value: '13,25' } });
  fireEvent.click(screen.getByRole('button', { name: 'Auswahl übernehmen' }));
  await waitFor(() => expect(payload).not.toBeNull());
  expect(payload).toMatchObject({ erwarteteAngebotVersion: 7, akzeptierteFeldpfade: ['angeb oten'], korrekturen: { 'angeb oten': '13,25' } });
});
