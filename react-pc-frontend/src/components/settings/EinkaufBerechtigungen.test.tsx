import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { ToastProvider } from '../ui/toast';
import { EinkaufBerechtigungen } from './EinkaufBerechtigungen';

function mount(fetchMock: typeof fetch) {
    vi.stubGlobal('fetch', fetchMock);
    return render(<ToastProvider><EinkaufBerechtigungen profileId={42} /></ToastProvider>);
}

test('lädt Rechte des gewählten Profils und speichert einzeln geänderte Einkaufsrechte', async () => {
    const fetchMock = vi.fn<typeof fetch>()
        .mockResolvedValueOnce(new Response(JSON.stringify(['LESEN']), { status: 200 }))
        .mockResolvedValueOnce(new Response(JSON.stringify(['LESEN', 'ANFRAGE_SENDEN']), { status: 200 }));
    mount(fetchMock);
    expect(await screen.findByLabelText('Einkauf lesen')).toBeChecked();
    fireEvent.click(screen.getByLabelText('Anfragen senden'));
    fireEvent.click(screen.getByRole('button', { name: 'Rechte speichern' }));
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2));
    expect(fetchMock.mock.calls[0][0]).toBe('/api/settings/einkauf-berechtigungen/42');
    expect(fetchMock.mock.calls[1][1]?.body).toBe(JSON.stringify({ rechte: ['LESEN', 'ANFRAGE_SENDEN'] }));
});

test('zeigt bei fehlender Admin-Berechtigung keine bearbeitbaren Rechtefelder', async () => {
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(new Response('{}', { status: 403 }));
    mount(fetchMock);
    expect(await screen.findByText('Rechte können mit diesem Zugang nicht bearbeitet werden.')).toBeInTheDocument();
    expect(screen.queryByLabelText('Einkauf lesen')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Rechte speichern' })).not.toBeInTheDocument();
});
