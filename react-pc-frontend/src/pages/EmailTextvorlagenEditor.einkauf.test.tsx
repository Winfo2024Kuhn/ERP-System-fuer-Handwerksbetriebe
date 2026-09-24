import { act, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import userEvent from '@testing-library/user-event';
import { ToastProvider } from '../components/ui/toast';
import EmailTextvorlagenEditor from './EmailTextvorlagenEditor';

describe('EmailTextvorlagenEditor Einkauf', () => {
 beforeEach(() => { global.fetch = vi.fn(async (input: RequestInfo | URL) => {
  const url = String(input);
  if (url.endsWith('/api/email-textvorlagen')) return { ok: true, json: async () => [{ id: 8, dokumentTyp: 'EINKAUF_ANFRAGE', kategorie: 'EINKAUF', name: 'Anfrage', subjectTemplate: '{{PA_NUMMER}}', htmlBody: '<p>{{ANREDE}}</p>', aktiv: true }] } as Response;
  if (url.endsWith('/dokumenttypen')) return { ok: true, json: async () => [{ value: 'EINKAUF_ANFRAGE', label: 'Einkauf — Lieferantenanfrage', kategorie: 'EINKAUF', kategorieLabel: 'Einkauf' }] } as Response;
  if (url.endsWith('/placeholders/EINKAUF_ANFRAGE')) return { ok: true, json: async () => [{ token: '{{ANREDE}}', label: 'Neutrale Anrede', pflicht: true, imBetreffErlaubt: false }] } as Response;
  if (url.endsWith('/placeholders')) return { ok: true, json: async () => [] } as Response;
  return { ok: true, json: async () => ({}) } as Response;
 }); });
 it('lädt Einkaufskategorie und Platzhalter passend zum Dokumenttyp', async () => {
   const user = userEvent.setup();
   render(<MemoryRouter><ToastProvider><EmailTextvorlagenEditor /></ToastProvider></MemoryRouter>);
   expect((await screen.findAllByText('Einkauf')).length).toBeGreaterThan(0);
   await waitFor(() => expect(global.fetch).toHaveBeenCalledWith('/api/email-textvorlagen/placeholders/EINKAUF_ANFRAGE'));
   await user.click(screen.getAllByRole('button', { name: /bearbeiten/i })[0]);
   expect(await screen.findByTitle('Neutrale Anrede')).toBeInTheDocument();
 });
 it('behält typgebundene Platzhalter bei verspäteter allgemeiner Metadatenantwort', async () => {
   const originalFetch = global.fetch;
   let complete!: (response: Response) => void;
   const delayed = new Promise<Response>(resolve => { complete = resolve; });
   global.fetch = vi.fn((input: RequestInfo | URL, init?: RequestInit) => String(input).endsWith('/placeholders') ? delayed : originalFetch(input, init));
   render(<MemoryRouter><ToastProvider><EmailTextvorlagenEditor /></ToastProvider></MemoryRouter>);
   await userEvent.click((await screen.findAllByRole('button', { name: /bearbeiten/i }))[0]);
   expect(await screen.findByTitle('Neutrale Anrede')).toBeInTheDocument();
   await act(async () => complete({ ok: true, json: async () => [{ token: '{{ANREDE}}', label: 'Allgemeine Anrede' }] } as Response));
   expect(screen.getByTitle('Neutrale Anrede')).toBeInTheDocument();
 });

});
