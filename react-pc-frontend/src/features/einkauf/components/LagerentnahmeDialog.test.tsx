import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { expect, it, vi } from 'vitest';
import { ToastProvider } from '../../../components/ui/toast';
import { LagerentnahmeDialog } from './LagerentnahmeDialog';

it('ändert die offene Menge erst nach bestätigter Entnahme und zeigt den Serverstand', async () => {
  const bestaetigt = vi.fn();
  let requestBody: unknown;
  global.fetch = vi.fn(async (_input: RequestInfo | URL, optionen?: RequestInit) => {
    requestBody = JSON.parse(String(optionen?.body));
    return { ok: true, status: 201, json: async () => ({ id: 7, bedarfId: 6, menge: 3, offenerBedarf: 7 }) } as Response;
  });
  render(<ToastProvider><LagerentnahmeDialog bedarf={{ id: 6, version: 4, bezeichnung: 'Stahlprofil', einheit: 'STUECK', offen: 10 }} schließen={vi.fn()} bestätigt={bestaetigt} /></ToastProvider>);
  expect(global.fetch).not.toHaveBeenCalled();
  fireEvent.change(screen.getByLabelText('Entnommene Menge'), { target: { value: '3' } });
  fireEvent.click(screen.getByRole('button', { name: 'Entnahme bestätigen' }));
  await waitFor(() => expect(bestaetigt).toHaveBeenCalledWith(7));
  expect(requestBody).toMatchObject({ anteil: { bedarfId: 6, version: 4, menge: 3 } });
});


it('zeigt lokale Mengenfehler inline und als Toast, ohne die Entnahme zu buchen', async () => {
  global.fetch = vi.fn();
  render(<ToastProvider><LagerentnahmeDialog bedarf={{ id: 6, version: 4, bezeichnung: 'Stahlprofil', einheit: 'STUECK', offen: 10 }} schließen={vi.fn()} bestätigt={vi.fn()} /></ToastProvider>);
  fireEvent.change(screen.getByLabelText('Entnommene Menge'), { target: { value: '11' } });
  fireEvent.click(screen.getByRole('button', { name: 'Entnahme bestätigen' }));
  expect(await screen.findAllByText('Bitte eine Menge zwischen 0 und 10 Stück eingeben.')).toHaveLength(2);
  expect(screen.getByTestId('toast-container')).toHaveTextContent('Bitte eine Menge zwischen 0 und 10 Stück eingeben.');
  expect(global.fetch).not.toHaveBeenCalled();
});

it('verlangt nach einem Versionskonflikt eine bewusste Übernahme des aktuellen Bedarfs', async () => {
  const aufrufe: RequestInit[] = []; const bestätigt = vi.fn();
  global.fetch = vi.fn(async (_eingabe: RequestInfo | URL, optionen?: RequestInit) => {
    if (optionen?.method === 'POST') {
      aufrufe.push(optionen);
      return aufrufe.length === 1 ? { ok: false, status: 409, json: async () => ({ message: 'Bedarf geändert' }) } as Response
        : { ok: true, status: 201, json: async () => ({ id: 1, offenerBedarf: 3 }) } as Response;
    }
    return { ok: true, json: async () => ({ id: 6, version: 5, position: { bezeichnung: 'Profil', basis: { einheit: 'STUECK' } }, mengen: { disponierbar: 6 } }) } as Response;
  });
  render(<ToastProvider><LagerentnahmeDialog bedarf={{ id: 6, version: 4, bezeichnung: 'Profil', einheit: 'STUECK', offen: 10 }} schließen={vi.fn()} bestätigt={bestätigt} /></ToastProvider>);
  fireEvent.change(screen.getByLabelText('Entnommene Menge'), { target: { value: '3' } });
  fireEvent.click(screen.getByRole('button', { name: 'Entnahme bestätigen' }));
  const abgleich = await screen.findByRole('button', { name: 'Aktuellen Stand übernehmen' });
  expect(screen.getByRole('button', { name: 'Entnahme bestätigen' })).toBeDisabled();
  fireEvent.click(abgleich);
  expect(screen.getByLabelText('Entnommene Menge')).toHaveValue('3');
  fireEvent.click(screen.getByRole('button', { name: 'Entnahme bestätigen' }));
  await waitFor(() => expect(bestätigt).toHaveBeenCalledWith(3));
  expect(JSON.parse(String(aufrufe[1].body)).anteil).toEqual({ bedarfId: 6, version: 5, menge: 3 });
});
