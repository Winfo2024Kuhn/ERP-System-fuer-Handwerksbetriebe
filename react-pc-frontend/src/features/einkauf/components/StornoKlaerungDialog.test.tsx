import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { expect, it, vi } from 'vitest';
import { ToastProvider } from '../../../components/ui/toast';
import { StornoKlaerungDialog } from './StornoKlaerungDialog';

it('erstellt erst eine unveränderliche Vorschau und sendet nur nach ausdrücklicher Bestätigung', async () => {
  const calls: Array<{ url: string; body: unknown }> = [];
  global.fetch = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    calls.push({ url: String(input), body: init?.body ? JSON.parse(String(init.body)) : null });
    return { ok: true, json: async () => String(input).endsWith('/storno-vorschau') ? { version: 4, vorschauId: 'preview-1', vorschauHash: 'hash', subject: 'Storno B-1', htmlBody: '<p>Anfrage</p>', empfaenger: 'test@example.com', revisionId: 8 } : { id: 7, status: 'VORBEREITET' } } as Response;
  });
  render(<ToastProvider><StornoKlaerungDialog bestellungId={2} version={4} anteile={[{ bedarfId: 3, version: 1, menge: 1 }]} onClose={vi.fn()} onSent={vi.fn()} /></ToastProvider>);
  fireEvent.change(screen.getByLabelText('Grund der Stornoanfrage'), { target: { value: 'Liefertermin nicht haltbar' } });
  fireEvent.click(screen.getByRole('button', { name: 'Vorschau erstellen' }));
  await screen.findByText('Storno B-1');
  expect(calls).toHaveLength(1);
  fireEvent.click(screen.getByRole('button', { name: 'Stornoanfrage senden' }));
  await waitFor(() => expect(calls).toHaveLength(2));
  expect(calls[1].body).toMatchObject({ version: 4, vorschauId: 'preview-1', vorschauHash: 'hash' });
});
