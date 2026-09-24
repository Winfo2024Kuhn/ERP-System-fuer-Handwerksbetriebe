import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { expect, it, vi } from 'vitest';
import { ToastProvider } from '../../../components/ui/toast';
import { AngebotEditor } from './AngebotEditor';

it('erfasst ein manuelles Angebot mit editierbaren Positionswerten', async () => {
  let payload: Record<string, unknown> | null = null;
  global.fetch = vi.fn(async (_input: RequestInfo | URL, init?: RequestInit) => { payload = JSON.parse(String(init?.body)); return { ok: true, status: 201, json: async () => ({ id: 17 }) } as Response; });
  render(<ToastProvider><AngebotEditor beteiligungId={8} anfrageRevisionId={3} positionen={[{ id: 4, snapshot: { interneReferenz: 'MAT-4', bezeichnung: 'Träger', werkstoff: 'S235', basis: { menge: 2, einheit: 'METER' } } }]} onSaved={vi.fn()} /></ToastProvider>);
  fireEvent.change(screen.getByLabelText('Angebotspreis MAT-4'), { target: { value: '12,50' } });
  fireEvent.change(screen.getByLabelText('Angebotsnummer'), { target: { value: 'ANG-44' } });
  fireEvent.click(screen.getByRole('button', { name: 'Angebot speichern' }));
  await waitFor(() => expect(payload).not.toBeNull());
  expect(payload).toMatchObject({ anfrageRevisionId: 3, angebotsnummer: 'ANG-44', positionen: [{ anfragePositionId: 4, angeboten: { menge: 2, einheit: 'METER' }, kosten: [{ betrag: 12.5, basis: 'M' }] }] });
});
