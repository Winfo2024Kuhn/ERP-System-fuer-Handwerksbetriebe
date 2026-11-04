import { act, renderHook, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { ReactNode } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ConfirmProvider } from '../../components/ui/confirm-dialog';
import { ToastProvider } from '../../components/ui/toast';
import { useEmailLoeschen } from './useEmailLoeschen';

const wrapper = ({ children }: { children: ReactNode }) => <ConfirmProvider><ToastProvider>{children}</ToastProvider></ConfirmProvider>;
const ok = (body: unknown = {}) => new Response(JSON.stringify(body), { status: 200 });
const fehler = (status: number, message?: string) => new Response(message ? JSON.stringify({ message }) : '', { status });

function starte(ordner: 'inbox' | 'trash', antwort: (url: string, init?: RequestInit) => Response = () => ok()) {
    const fetchMock = vi.fn((url: string, init?: RequestInit) => Promise.resolve(antwort(url, init)));
    vi.stubGlobal('fetch', fetchMock);
    const ausblenden = vi.fn();
    const neuLaden = vi.fn();
    const geaendert = vi.fn();
    const hook = renderHook(() => useEmailLoeschen({ ausblenden, neuLaden, geaendert }), { wrapper });
    const endgueltig = ordner === 'trash';
    const loeschen = (ids: number[], betreff?: string) => hook.result.current.loeschen(ids, { endgueltig, betreff });
    return { ...hook, loeschen, fetchMock, ausblenden, neuLaden, geaendert };
}

const koerper = (init?: RequestInit) => JSON.parse(String(init?.body)) as { ids: number[] };

describe('useEmailLoeschen – Papierkorb mit Rückgängig', () => {
    beforeEach(() => { vi.useFakeTimers({ shouldAdvanceTime: true }); });
    afterEach(() => { vi.useRealTimers(); vi.unstubAllGlobals(); });

    it('legt ohne Rückfrage in den Papierkorb und holt per „Rückgängig“ dieselben Mails zurück', async () => {
        const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
        const { loeschen, fetchMock, ausblenden, neuLaden, geaendert } = starte('inbox', () => ok({ verschoben: 2 }));
        await act(async () => { await loeschen([16, 17], 'Anfrage Terrasse'); });

        expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
        expect(ausblenden).toHaveBeenCalledWith([16, 17]);
        expect(fetchMock).toHaveBeenCalledWith('/api/emails/papierkorb', expect.objectContaining({ method: 'POST' }));
        expect(koerper(fetchMock.mock.calls[0][1]).ids).toEqual([16, 17]);
        expect(geaendert).toHaveBeenCalled();
        expect(screen.getByText('2 E-Mails in den Papierkorb gelegt.')).toBeInTheDocument();

        await user.click(screen.getByRole('button', { name: 'Rückgängig' }));
        expect(fetchMock).toHaveBeenLastCalledWith('/api/emails/papierkorb/zurueckholen', expect.objectContaining({ method: 'POST' }));
        expect(koerper(fetchMock.mock.calls[1][1]).ids).toEqual([16, 17]);
        expect(await screen.findByText('2 E-Mails sind wieder da.')).toBeInTheDocument();
        expect(neuLaden).toHaveBeenCalledWith([16, 17]);
    });

    it('zählt im Toast, was der Server wirklich verschoben hat', async () => {
        const { loeschen } = starte('inbox', () => ok({ verschoben: 1 }));
        await act(async () => { await loeschen([16, 17]); });
        expect(screen.getByText('E-Mail in den Papierkorb gelegt.')).toBeInTheDocument();
    });

    it('meldet ohne „Rückgängig“, wenn nichts verschoben wurde, und zeigt die Mails wieder', async () => {
        const { loeschen, neuLaden, geaendert } = starte('inbox', () => ok({ verschoben: 0 }));
        await act(async () => { await loeschen([16]); });
        expect(screen.getByText('Diese E-Mail liegt schon im Papierkorb.')).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Rückgängig' })).not.toBeInTheDocument();
        expect(neuLaden).toHaveBeenCalledWith([16]);
        expect(geaendert).not.toHaveBeenCalled();
    });

    it('meldet, wenn das Zurückholen scheitert', async () => {
        const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
        const { loeschen, neuLaden } = starte('inbox', url => (url.endsWith('/zurueckholen') ? fehler(500) : ok()));
        await act(async () => { await loeschen([5]); });
        expect(screen.getByText('E-Mail in den Papierkorb gelegt.')).toBeInTheDocument();
        await user.click(screen.getByRole('button', { name: 'Rückgängig' }));
        expect(await screen.findByText('E-Mails konnten nicht zurückgeholt werden.')).toBeInTheDocument();
        expect(neuLaden).not.toHaveBeenCalled();
    });

    it('teilt mehr als 500 Mails in Blöcke', async () => {
        const ids = Array.from({ length: 600 }, (_, i) => i + 1);
        const { loeschen, fetchMock } = starte('inbox');
        await act(async () => { await loeschen(ids); });
        expect(fetchMock.mock.calls.map(([, init]) => koerper(init).ids.length)).toEqual([500, 100]);
        expect(screen.getByText('600 E-Mails in den Papierkorb gelegt.')).toBeInTheDocument();
    });

    it('holt die Mails bei einem Fehler zurück in die Liste und zeigt den Grund vom Server', async () => {
        const { loeschen, neuLaden, geaendert } = starte('inbox', () => fehler(503, 'Der Mailserver ist gerade nicht erreichbar.'));
        await act(async () => { await loeschen([1]); });
        expect(neuLaden).toHaveBeenCalledWith([1]);
        expect(geaendert).not.toHaveBeenCalled();
        expect(screen.getByText('Der Mailserver ist gerade nicht erreichbar.')).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Rückgängig' })).not.toBeInTheDocument();
    });

    it('meldet ohne Verbindung einen eigenen Satz', async () => {
        const fetchMock = vi.fn(() => Promise.reject(new TypeError('Failed to fetch')));
        vi.stubGlobal('fetch', fetchMock);
        const { result } = renderHook(() => useEmailLoeschen({ ausblenden: vi.fn(), neuLaden: vi.fn(), geaendert: vi.fn() }), { wrapper });
        await act(async () => { await result.current.loeschen([1, 2], { endgueltig: false }); });
        expect(screen.getByText('E-Mails konnten nicht in den Papierkorb gelegt werden.')).toBeInTheDocument();
    });
});

describe('useEmailLoeschen – Wiederherstellen aus dem Papierkorb', () => {
    afterEach(() => { vi.unstubAllGlobals(); });

    it('holt die Mails in ihren Ursprungsordner zurück, nicht per Verschieben in den Posteingang', async () => {
        const { result, fetchMock, ausblenden, neuLaden, geaendert } = starte('trash', () => ok({ zurueckgeholt: 3 }));
        await act(async () => { await result.current.wiederherstellen([4, 5, 6]); });
        expect(ausblenden).toHaveBeenCalledWith([4, 5, 6]);
        expect(fetchMock).toHaveBeenCalledTimes(1);
        expect(fetchMock).toHaveBeenCalledWith('/api/emails/papierkorb/zurueckholen', expect.objectContaining({ method: 'POST' }));
        expect(koerper(fetchMock.mock.calls[0][1]).ids).toEqual([4, 5, 6]);
        expect(geaendert).toHaveBeenCalled();
        expect(neuLaden).not.toHaveBeenCalled();
        expect(screen.getByText('3 E-Mails sind wieder da.')).toBeInTheDocument();
    });

    it('meldet, wenn nichts mehr zurückzuholen war', async () => {
        const { result, neuLaden } = starte('trash', () => ok({ zurueckgeholt: 0 }));
        await act(async () => { await result.current.wiederherstellen([4, 5]); });
        expect(screen.getByText('Diese E-Mails liegen nicht mehr im Papierkorb.')).toBeInTheDocument();
        expect(screen.queryByText(/wieder da/)).not.toBeInTheDocument();
        expect(neuLaden).toHaveBeenCalledWith([4, 5]);
    });

    it('teilt große Mengen in Blöcke à 500', async () => {
        const { result, fetchMock } = starte('trash');
        await act(async () => { await result.current.wiederherstellen(Array.from({ length: 501 }, (_, i) => i + 1)); });
        expect(fetchMock.mock.calls.map(([, init]) => koerper(init).ids.length)).toEqual([500, 1]);
        expect(screen.getByText('501 E-Mails sind wieder da.')).toBeInTheDocument();
    });

    it('lässt die Mails bei einem Fehler im Papierkorb und zeigt den Grund', async () => {
        const { result, neuLaden, geaendert } = starte('trash', () => fehler(400, 'Keine E-Mails ausgewählt.'));
        await act(async () => { await result.current.wiederherstellen([7]); });
        expect(neuLaden).toHaveBeenCalledWith([7]);
        expect(geaendert).not.toHaveBeenCalled();
        expect(screen.getByText('Keine E-Mails ausgewählt.')).toBeInTheDocument();
        expect(screen.queryByText('E-Mail ist wieder da.')).not.toBeInTheDocument();
    });
});

describe('useEmailLoeschen – endgültig im Papierkorb', () => {
    afterEach(() => { vi.unstubAllGlobals(); });

    it('fragt mit Betreff nach und löscht erst nach „Endgültig löschen“', async () => {
        const user = userEvent.setup();
        const { loeschen, fetchMock, ausblenden } = starte('trash');
        let fertig!: Promise<void>;
        act(() => { fertig = loeschen([8], 'Angebot Geländer'); });

        const dialog = await screen.findByRole('dialog');
        expect(dialog).toHaveTextContent('Endgültig löschen?');
        expect(dialog).toHaveTextContent('„Angebot Geländer“ wird endgültig gelöscht und auch vom Mailserver entfernt. Das lässt sich nicht rückgängig machen.');
        expect(fetchMock).not.toHaveBeenCalled();
        await user.click(screen.getByRole('button', { name: 'Endgültig löschen' }));
        await act(async () => { await fertig; });

        expect(ausblenden).toHaveBeenCalledWith([8]);
        expect(fetchMock).toHaveBeenCalledWith('/api/emails/8/permanent', { method: 'DELETE' });
        expect(screen.getByText('E-Mail endgültig gelöscht.')).toBeInTheDocument();
    });

    it('lässt bei „Abbrechen“ alles stehen', async () => {
        const user = userEvent.setup();
        const { loeschen, fetchMock, ausblenden } = starte('trash');
        let fertig!: Promise<void>;
        act(() => { fertig = loeschen([8, 9]); });
        expect(await screen.findByRole('dialog')).toHaveTextContent('2 E-Mails werden endgültig gelöscht');
        await user.click(screen.getByRole('button', { name: 'Abbrechen' }));
        await act(async () => { await fertig; });
        expect(fetchMock).not.toHaveBeenCalled();
        expect(ausblenden).not.toHaveBeenCalled();
    });

    it('meldet „n von m“ und holt die übrigen zurück, wenn ein Teil scheitert', async () => {
        const user = userEvent.setup();
        const { loeschen, neuLaden } = starte('trash', url => (url === '/api/emails/9/permanent' ? fehler(500) : ok()));
        let fertig!: Promise<void>;
        act(() => { fertig = loeschen([8, 9]); });
        await user.click(await screen.findByRole('button', { name: 'Endgültig löschen' }));
        await act(async () => { await fertig; });
        expect(screen.getByText('1 von 2 E-Mails konnten nicht gelöscht werden.')).toBeInTheDocument();
        expect(neuLaden).toHaveBeenCalledWith([9]);
    });
});
