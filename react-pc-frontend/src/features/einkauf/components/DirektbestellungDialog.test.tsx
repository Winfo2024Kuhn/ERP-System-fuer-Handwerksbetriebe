import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { expect, it, vi } from 'vitest';
import { ToastProvider } from '../../../components/ui/toast';
import { DirektbestellungDialog } from './DirektbestellungDialog';

it('legt eine Direktbestellung mit geprüftem Preisnachweis an, ohne Angebots- oder PA-Nummer', async () => {
  let order: Record<string, unknown> | null = null;
  global.fetch = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input);
    if (url.includes('/api/einkauf/bedarf?')) return { ok: true, json: async () => ({ content: [{ id: 6, version: 2, position: { art: 'ARTIKEL', artikelId: 9, interneReferenz: 'MAT-9', bezeichnung: 'Profil', basis: { menge: 2, einheit: 'METER' } }, mengen: { disponierbar: 2 }, liefergruppe: {} }] }) } as Response;
    if (url.includes('/api/lieferanten?')) return { ok: true, json: async () => ({ lieferanten: [{ id: 8, lieferantenname: 'Musterstahl' }] }) } as Response;
    if (url === '/api/lieferanten/8') return { ok: true, json: async () => ({ id: 8, eigeneKundennummer: 'K-8' }) } as Response;
    if (url.includes('/api/einkauf-kontakte?')) return { ok: true, json: async () => [{ id: 4, version: 1, name: 'Einkauf', email: 'einkauf@example.test', standardBestellung: true, aktiv: true }] } as Response;
    order = JSON.parse(String(init?.body)); return { ok: true, status: 201, json: async () => ({ id: 22 }) } as Response;
  });
  render(<ToastProvider><DirektbestellungDialog onClose={vi.fn()} onCreated={vi.fn()} /></ToastProvider>);
  fireEvent.click(await screen.findByLabelText('Bedarf MAT-9 auswählen'));
  fireEvent.click(screen.getByRole('button', { name: 'Lieferant wählen' }));
  fireEvent.click(await screen.findByText('Musterstahl'));
  fireEvent.change(await screen.findByLabelText('Preis MAT-9'), { target: { value: '5,50' } });
  fireEvent.click(screen.getByLabelText('Preis bestätigt am MAT-9'));
  fireEvent.click(within(screen.getByRole('dialog', { name: 'Preis bestätigt am MAT-9 auswählen' })).getByRole('button', { name: 'Heute' }));
  fireEvent.change(screen.getByLabelText('Preisquelle MAT-9'), { target: { value: 'Angebot ANG-8, PDF' } });
  fireEvent.click(screen.getByRole('button', { name: 'Direktbestellung als Entwurf anlegen' }));
  await waitFor(() => expect(order).not.toBeNull());
  expect(order).toMatchObject({ lieferantId: 8, paket: [{ bedarfId: 6, version: 2, menge: 2 }], preise: [{ bedarfId: 6, preis: 5.5, einheit: 'METER', basisMenge: 1, preisHistorieId: null, bestaetigtAm: new Date().toLocaleDateString('sv-SE'), bestaetigungsbeleg: 'Angebot ANG-8, PDF' }] });
});

it('übernimmt ausgewählte Bedarfsanteile und deren Teilmengen in den Direktbestelldialog', async () => {
  global.fetch = vi.fn(async (eingabe: RequestInfo | URL) => String(eingabe).includes('/api/einkauf/bedarf?')
    ? ({ ok: true, json: async () => ({ content: [{ id: 6, version: 2, position: { art: 'ARTIKEL', artikelId: 9, interneReferenz: 'MAT-9', bezeichnung: 'Profil', basis: { menge: 8, einheit: 'METER' } }, mengen: { disponierbar: 8 }, liefergruppe: {} }] }) } as Response)
    : ({ ok: true, json: async () => [] } as Response));
  render(<ToastProvider><DirektbestellungDialog initialeTeilmengen={[{ bedarfId: 6, menge: 1.25 }]} onClose={vi.fn()} onCreated={vi.fn()} /></ToastProvider>);
  expect(await screen.findByLabelText('Bedarf MAT-9 auswählen')).toBeChecked();
  expect(screen.getByLabelText('Menge MAT-9')).toHaveValue('1,25');
});
