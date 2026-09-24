import userEvent from '@testing-library/user-event';
import { render, screen, waitFor } from '@testing-library/react';
import { beforeEach, expect, it, vi } from 'vitest';
import { ToastProvider } from '../../../components/ui/toast';
import { BestellfreigabeDialog } from './BestellfreigabeDialog';

beforeEach(() => {
  global.fetch = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const path = String(input);
    if (path === '/api/email-textvorlagen') return { ok: true, json: async () => [{ id: 4, dokumentTyp: 'EINKAUF_BESTELLUNG', aktiv: true, standard: true }] } as Response;
    if (path.includes('/vorschau?') && init?.method === 'POST') return { ok: true, json: async () => ({ version: 5, vorschauHash: 'hash', subject: 'Bestellung B-73', htmlBody: '<p>Profil</p>', empfaenger: 'einkauf@example.test', pdfDateiId: 55, anlageVersionIds: [], revisionsNummer: 2, eigeneKundennummer: '00017', liefertermin: '2026-10-14', antwortfrist: '2026-10-01', anlagen: [] }) } as Response;
    if (path.endsWith('/freigeben') && init?.method === 'POST') return { ok: true, json: async () => ({ id: 88, version: 1, status: 'VORBEREITET' }) } as Response;
    return { ok: true, json: async () => ({}) } as Response;
  });
});

it('zeigt die konkrete Revisionsvorschau und verlangt deren Bestätigung vor der Freigabe', async () => {
  const user = userEvent.setup(); const onReleased = vi.fn();
  render(<ToastProvider><BestellfreigabeDialog bestellungId={73} onClose={vi.fn()} onReleased={onReleased} /></ToastProvider>);
  await user.click(screen.getByRole('button', { name: 'Vorschau laden' }));
  expect(await screen.findByText('Bestellung B-73')).toBeInTheDocument();
  expect(screen.getByText('Fassung 2')).toBeInTheDocument();
  expect(screen.getByText(/Eigene Kundennummer: 00017/)).toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Bestellung freigeben' })).toBeDisabled();
  await user.click(screen.getByRole('checkbox', { name: /PDF und Empfänger geprüft/i }));
  await user.click(screen.getByRole('button', { name: 'Bestellung freigeben' }));
  await waitFor(() => expect(onReleased).toHaveBeenCalled());
  expect(global.fetch).toHaveBeenCalledWith('/api/einkauf/bestellungen/73/freigeben', expect.objectContaining({ method: 'POST' }));
});
