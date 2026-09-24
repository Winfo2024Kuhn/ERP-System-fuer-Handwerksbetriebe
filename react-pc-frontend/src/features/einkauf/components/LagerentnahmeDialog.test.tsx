import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { expect, it, vi } from 'vitest';
import { ToastProvider } from '../../../components/ui/toast';
import { LagerentnahmeDialog } from './LagerentnahmeDialog';

it('ändert die offene Menge erst nach bestätigter Entnahme und zeigt den Serverstand', async () => {
  const bestaetigt = vi.fn();
  let requestBody: unknown;
  global.fetch = vi.fn(async (_input: RequestInfo | URL, init?: RequestInit) => {
    requestBody = JSON.parse(String(init?.body));
    return { ok: true, status: 201, json: async () => ({ id: 7, bedarfId: 6, menge: 3, offenerBedarf: 7 }) } as Response;
  });
  render(<ToastProvider><LagerentnahmeDialog bedarf={{ id: 6, version: 4, bezeichnung: 'Stahlprofil', einheit: 'STUECK', offen: 10 }} onClose={vi.fn()} onBestaetigt={bestaetigt} /></ToastProvider>);
  expect(global.fetch).not.toHaveBeenCalled();
  fireEvent.change(screen.getByLabelText('Entnommene Menge'), { target: { value: '3' } });
  fireEvent.click(screen.getByRole('button', { name: 'Entnahme bestätigen' }));
  await waitFor(() => expect(bestaetigt).toHaveBeenCalledWith(7));
  expect(requestBody).toMatchObject({ anteil: { bedarfId: 6, version: 4, menge: 3 } });
});
