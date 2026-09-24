import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { expect, it, vi } from 'vitest';
import { ToastProvider } from '../../../components/ui/toast';
import { AnforderungsVorlagen } from './AnforderungsVorlagen';

it('lädt manuell gepflegte Vorgaben zur Artikel-ID mit Grundlage und Version', async () => {
  const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
    if (String(input) === '/api/einkauf/anforderungsvorlagen?artikelId=15') return new Response(JSON.stringify([
      { art: 'ZEUGNIS_3_1', grundlage: 'EN 10204', grundlageVersion: 'V2', fachlichBestaetigt: true },
    ]), { status: 200 });
    return new Response('{}', { status: 200 });
  });
  global.fetch = fetchMock as typeof fetch;
  render(<ToastProvider><AnforderungsVorlagen /></ToastProvider>);

  await userEvent.type(screen.getByLabelText('Artikelnummer'), '15');
  await userEvent.click(screen.getByRole('button', { name: 'Vorgaben laden' }));

  expect(await screen.findByText(/EN 10204 · V2/)).toBeInTheDocument();
  expect(screen.getByText(/keine EN-1090-Erfüllung automatisch berechnet/)).toBeInTheDocument();
});

it('legt eine bestätigte Grundlage als neue Version an und zeigt die gespeicherte Fassung', async () => {
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    if (String(input) === '/api/einkauf/anforderungsvorlagen' && init?.method === 'POST') return new Response(JSON.stringify({
      id: 82, artikelId: 15, projektId: null, art: 'ZEUGNIS_3_1', grundlage: 'EN 10204', grundlageVersion: 'V3', fachlichBestaetigt: true,
    }), { status: 200 });
    return new Response('[]', { status: 200 });
  });
  global.fetch = fetchMock as typeof fetch;
  render(<ToastProvider><AnforderungsVorlagen /></ToastProvider>);

  await userEvent.type(screen.getByLabelText('Artikelnummer'), '15');
  await userEvent.click(screen.getByRole('button', { name: 'Zeugnis 3.1' }));
  await userEvent.type(screen.getByLabelText('Fachliche Grundlage'), 'EN 10204');
  await userEvent.click(screen.getByRole('button', { name: 'Vorgabe speichern' }));

  await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/einkauf/anforderungsvorlagen', expect.objectContaining({ method: 'POST' })));
  const body = JSON.parse(fetchMock.mock.calls.find(call => call[1]?.method === 'POST')![1]!.body as string);
  expect(body).toEqual({ artikelId: 15, projektId: null, art: 'ZEUGNIS_3_1', grundlage: 'EN 10204' });
  expect(await screen.findAllByText(/Neue Vorgabe V3 gespeichert/)).toHaveLength(2);
});
