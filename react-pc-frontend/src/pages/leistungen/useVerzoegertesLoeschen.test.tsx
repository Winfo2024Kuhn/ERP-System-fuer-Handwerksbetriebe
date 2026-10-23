import { act, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ToastProvider } from '../../components/ui/toast';
import { RUECKGAENGIG_MS, useVerzoegertesLoeschen } from './useVerzoegertesLoeschen';

const geloescht = vi.fn();

function Probe() {
    const { ausgeblendet, planen } = useVerzoegertesLoeschen();
    return (
        <div>
            <p data-testid="status">{ausgeblendet.has('ordner:4') ? 'ausgeblendet' : 'sichtbar'}</p>
            <button onClick={() => planen({
                schluessel: 'ordner:4', url: '/api/produktkategorien/4', name: 'Holzbau',
                fehlerText: '„Holzbau“ konnte nicht gelöscht werden.', onGeloescht: geloescht,
            })}>Löschen</button>
        </div>
    );
}

const status = () => screen.getByTestId('status').textContent;

describe('Löschen mit „Rückgängig“ (ohne Bezüge)', () => {
    let fetchMock: ReturnType<typeof vi.fn>;
    beforeEach(() => {
        vi.useFakeTimers();
        fetchMock = vi.fn().mockResolvedValue({ ok: true, status: 204, json: () => Promise.resolve({}) });
        vi.stubGlobal('fetch', fetchMock);
    });
    afterEach(() => {
        vi.useRealTimers();
        vi.unstubAllGlobals();
    });

    it('blendet sofort aus und schickt nach Ablauf genau ein DELETE', async () => {
        render(<ToastProvider><Probe /></ToastProvider>);
        act(() => screen.getByText('Löschen').click());
        expect(status()).toBe('ausgeblendet');
        expect(screen.getByText('„Holzbau“ gelöscht.')).toBeInTheDocument();
        expect(fetchMock).not.toHaveBeenCalled();

        await act(async () => { await vi.advanceTimersByTimeAsync(RUECKGAENGIG_MS); });
        expect(fetchMock).toHaveBeenCalledTimes(1);
        expect(fetchMock).toHaveBeenCalledWith('/api/produktkategorien/4', { method: 'DELETE' });
        expect(geloescht).toHaveBeenCalledTimes(1);
        expect(screen.queryByRole('button', { name: 'Rückgängig' })).not.toBeInTheDocument();
    });

    it('„Rückgängig“ blendet wieder ein und sendet nichts', async () => {
        render(<ToastProvider><Probe /></ToastProvider>);
        act(() => screen.getByText('Löschen').click());
        act(() => screen.getByRole('button', { name: 'Rückgängig' }).click());
        expect(status()).toBe('sichtbar');
        expect(screen.getByText('„Holzbau“ ist wieder da.')).toBeInTheDocument();

        await act(async () => { await vi.advanceTimersByTimeAsync(RUECKGAENGIG_MS * 2); });
        expect(fetchMock).not.toHaveBeenCalled();
        expect(geloescht).not.toHaveBeenCalled();
    });

    it('zeigt bei Fehlschlag die Meldung des Servers und blendet wieder ein', async () => {
        fetchMock.mockResolvedValue({ ok: false, status: 409, json: () => Promise.resolve({ message: '„Holzbau“ wird noch verwendet.' }) });
        render(<ToastProvider><Probe /></ToastProvider>);
        act(() => screen.getByText('Löschen').click());
        await act(async () => { await vi.advanceTimersByTimeAsync(RUECKGAENGIG_MS); });
        expect(status()).toBe('sichtbar');
        expect(screen.getByText('„Holzbau“ wird noch verwendet.')).toBeInTheDocument();
        expect(geloescht).not.toHaveBeenCalled();
    });

    it('meldet einen Verbindungsfehler verständlich', async () => {
        fetchMock.mockRejectedValue(new TypeError('Failed to fetch'));
        render(<ToastProvider><Probe /></ToastProvider>);
        act(() => screen.getByText('Löschen').click());
        await act(async () => { await vi.advanceTimersByTimeAsync(RUECKGAENGIG_MS); });
        expect(screen.getByText(/Keine Verbindung zum Server/)).toBeInTheDocument();
    });

    it('plant dasselbe Objekt nicht doppelt', async () => {
        render(<ToastProvider><Probe /></ToastProvider>);
        act(() => { screen.getByText('Löschen').click(); screen.getByText('Löschen').click(); });
        await act(async () => { await vi.advanceTimersByTimeAsync(RUECKGAENGIG_MS); });
        expect(fetchMock).toHaveBeenCalledTimes(1);
    });

    it('schickt ausstehende Löschungen beim Verlassen sofort mit keepalive', () => {
        const { unmount } = render(<ToastProvider><Probe /></ToastProvider>);
        act(() => screen.getByText('Löschen').click());
        unmount();
        expect(fetchMock).toHaveBeenCalledWith('/api/produktkategorien/4', { method: 'DELETE', keepalive: true });
        // Kein zweites DELETE nach Ablauf der Frist
        act(() => { vi.advanceTimersByTime(RUECKGAENGIG_MS); });
        expect(fetchMock).toHaveBeenCalledTimes(1);
    });

    it('schickt ausstehende Löschungen auch beim Schließen des Tabs (pagehide) und schließt die Rückgängig-Meldung', async () => {
        render(<ToastProvider><Probe /></ToastProvider>);
        act(() => screen.getByText('Löschen').click());
        await act(async () => { window.dispatchEvent(new Event('pagehide')); });
        expect(fetchMock).toHaveBeenCalledWith('/api/produktkategorien/4', { method: 'DELETE', keepalive: true });
        expect(screen.queryByRole('button', { name: 'Rückgängig' })).not.toBeInTheDocument();
        // Seite steht noch (z. B. Zurück-Cache): Ergebnis wird übernommen.
        expect(geloescht).toHaveBeenCalledTimes(1);
    });

    it('zeigt nach pagehide den Fehler des Servers, solange die Seite noch steht', async () => {
        fetchMock.mockResolvedValue({ ok: false, status: 409, json: () => Promise.resolve({ message: '„Holzbau“ wird noch verwendet.' }) });
        render(<ToastProvider><Probe /></ToastProvider>);
        act(() => screen.getByText('Löschen').click());
        await act(async () => { window.dispatchEvent(new Event('pagehide')); });
        expect(screen.getByText('„Holzbau“ wird noch verwendet.')).toBeInTheDocument();
        expect(status()).toBe('sichtbar');
        expect(geloescht).not.toHaveBeenCalled();
    });

    it('nach pagehide: Verbindungsfehler mit Ersatztext', async () => {
        fetchMock.mockRejectedValue(new TypeError('Failed to fetch'));
        render(<ToastProvider><Probe /></ToastProvider>);
        act(() => screen.getByText('Löschen').click());
        await act(async () => { window.dispatchEvent(new Event('pagehide')); });
        expect(screen.getByText('„Holzbau“ konnte nicht gelöscht werden.')).toBeInTheDocument();
    });

    it('nach dem Verlassen der Seite wird nichts mehr angezeigt oder übernommen', async () => {
        fetchMock.mockResolvedValue({ ok: false, status: 409, json: () => Promise.resolve({ message: 'Fehler danach' }) });
        function Huelle({ zeigen }: { zeigen: boolean }) { return <ToastProvider>{zeigen && <Probe />}</ToastProvider>; }
        const { rerender } = render(<Huelle zeigen />);
        act(() => screen.getByText('Löschen').click());
        await act(async () => { rerender(<Huelle zeigen={false} />); });
        expect(screen.queryByText('Fehler danach')).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Rückgängig' })).not.toBeInTheDocument();
    });
});
