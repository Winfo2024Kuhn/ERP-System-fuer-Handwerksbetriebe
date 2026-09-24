import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { expect, it, vi } from 'vitest';
import { ToastProvider } from './ui/toast';
import { CreateReklamationModal } from './CreateReklamationModal';

it('sendet initiale Bestell-, Positions- und Rechnungsbezüge als echte Reklamationsreferenzen', async () => {
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    if (String(input) === '/api/reklamationen/lieferant/8' && init?.method === 'POST') return new Response(JSON.stringify({ id: 31, bilder: [] }), { status: 201 });
    return new Response('[]', { status: 200 });
  });
  global.fetch = fetchMock as typeof fetch;
  const onSuccess = vi.fn();
  render(<ToastProvider><CreateReklamationModal isOpen lieferantId={8} onClose={vi.fn()} onSuccess={onSuccess}
    initialBezug={{ bestellungId: 73, bestellPositionId: 9, rechnungId: 60, lieferscheinId: 41, beschreibung: 'Abweichende Teilrechnung prüfen.' }} /></ToastProvider>);

  expect(screen.getByText(/Bestellung 73 · Position 9 · Rechnung 60/)).toBeInTheDocument();
  expect(screen.getByLabelText('Beschreibung / Grund der Reklamation')).toHaveValue('Abweichende Teilrechnung prüfen.');
  await userEvent.click(screen.getByRole('button', { name: 'Reklamation erstellen' }));

  await waitFor(() => expect(onSuccess).toHaveBeenCalledOnce());
  const request = fetchMock.mock.calls.find(call => call[0] === '/api/reklamationen/lieferant/8');
  expect(JSON.parse(request![1].body as string)).toEqual({
    beschreibung: 'Abweichende Teilrechnung prüfen.', bestellungId: 73, bestellPositionId: 9, rechnungId: 60, lieferscheinId: 41,
  });
});
