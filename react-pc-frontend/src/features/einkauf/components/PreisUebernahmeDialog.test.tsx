import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { expect, it, vi } from 'vitest';
import { ToastProvider } from '../../../components/ui/toast';
import { PreisUebernahmeDialog } from './PreisUebernahmeDialog';

it('übernimmt einen bestätigten Preis nur nach Auswahl des Scopes und Grund', async () => {
  let payload: Record<string, unknown> | null = null;
  global.fetch = vi.fn(async (_input: RequestInfo | URL, init?: RequestInit) => { payload = JSON.parse(String(init?.body)); return { ok: true, json: async () => ({ id: 19 }) } as Response; });
  render(<ToastProvider><PreisUebernahmeDialog angebotVersionId={8} angebotPositionId={12} artikelId={33} lieferantId={4} preis={24.5} waehrung="EUR" einheit="METER" gueltigBis="2026-10-31" onClose={vi.fn()} onSaved={vi.fn()} /></ToastProvider>);
  fireEvent.change(screen.getByLabelText('Begründung'), { target: { value: 'Preis ausdrücklich geprüft' } });
  fireEvent.click(screen.getByRole('button', { name: 'Preis übernehmen' }));
  await waitFor(() => expect(payload).not.toBeNull());
  expect(payload).toMatchObject({ scope: 'STANDARD', begruendung: 'Preis ausdrücklich geprüft' });
});
