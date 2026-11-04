import { act, renderHook, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { EmailThread } from '../../../components/EmailThreadView';
import { useEmailsTabThread } from './useEmailsTabThread';

/** Dummy-Daten (DSGVO): Max Mustermann, max@example.com. */
function verlauf(id: number, anzahl = 1): EmailThread {
    return {
        rootEmailId: id,
        focusedEmailId: id,
        emails: Array.from({ length: anzahl }, (_, i) => ({
            id: id + i, subject: 'Anfrage Geländer', fromAddress: 'max@example.com', direction: 'IN' as const, attachments: [],
        })),
    };
}

function antwort(body: unknown, ok = true): Response {
    return { ok, json: () => Promise.resolve(body) } as Response;
}

afterEach(() => vi.unstubAllGlobals());

describe('useEmailsTabThread', () => {
    it('lädt nichts, solange keine Mail gewählt ist', () => {
        const fetchMock = vi.fn();
        vi.stubGlobal('fetch', fetchMock);
        const { result } = renderHook(() => useEmailsTabThread(null));
        expect(fetchMock).not.toHaveBeenCalled();
        expect(result.current.thread).toBeNull();
        expect(result.current.threadLoading).toBe(false);
    });

    it('lädt den Verlauf der gewählten Mail', async () => {
        const fetchMock = vi.fn().mockResolvedValue(antwort(verlauf(7)));
        vi.stubGlobal('fetch', fetchMock);
        const { result } = renderHook(() => useEmailsTabThread(7));
        expect(result.current.threadLoading).toBe(true);
        await waitFor(() => expect(result.current.threadLoading).toBe(false));
        expect(fetchMock).toHaveBeenCalledWith('/api/emails/7/thread');
        expect(result.current.thread?.rootEmailId).toBe(7);
    });

    it('setzt den Verlauf auf leer, wenn der Server ablehnt oder das Netz ausfällt', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue(antwort(null, false)));
        const abgelehnt = renderHook(() => useEmailsTabThread(7));
        await waitFor(() => expect(abgelehnt.result.current.threadLoading).toBe(false));
        expect(abgelehnt.result.current.thread).toBeNull();

        vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('offline')));
        const offline = renderHook(() => useEmailsTabThread(8));
        await waitFor(() => expect(offline.result.current.threadLoading).toBe(false));
        expect(offline.result.current.thread).toBeNull();
    });

    it('verwirft eine späte Antwort und leert den Verlauf, wenn die Auswahl wechselt', async () => {
        let spaet: (r: Response) => void = () => {};
        const fetchMock = vi.fn()
            .mockReturnValueOnce(new Promise<Response>(resolve => { spaet = resolve; }))
            .mockResolvedValueOnce(antwort(verlauf(9)));
        vi.stubGlobal('fetch', fetchMock);
        const { result, rerender } = renderHook(({ id }) => useEmailsTabThread(id), { initialProps: { id: 7 as number | null } });
        rerender({ id: 9 });
        await waitFor(() => expect(result.current.thread?.rootEmailId).toBe(9));
        await act(async () => { spaet(antwort(verlauf(7))); });
        expect(result.current.thread?.rootEmailId).toBe(9);

        rerender({ id: null });
        expect(result.current.thread).toBeNull();
    });

    it('lädt den Verlauf auf Wunsch neu', async () => {
        const fetchMock = vi.fn()
            .mockResolvedValueOnce(antwort(verlauf(7, 1)))
            .mockResolvedValueOnce(antwort(verlauf(7, 2)));
        vi.stubGlobal('fetch', fetchMock);
        const { result } = renderHook(() => useEmailsTabThread(7));
        await waitFor(() => expect(result.current.thread?.emails).toHaveLength(1));
        act(() => result.current.reloadThread());
        expect(result.current.threadLoading).toBe(true);
        await waitFor(() => expect(result.current.threadLoading).toBe(false));
        expect(result.current.thread?.emails).toHaveLength(2);
        expect(fetchMock).toHaveBeenLastCalledWith('/api/emails/7/thread');
    });

    it('leert beim Neuladen den Verlauf, wenn der Server ablehnt, und behält ihn bei Netzausfall', async () => {
        const fetchMock = vi.fn()
            .mockResolvedValueOnce(antwort(verlauf(7)))
            .mockRejectedValueOnce(new Error('offline'))
            .mockResolvedValueOnce(antwort(null, false));
        vi.stubGlobal('fetch', fetchMock);
        const { result } = renderHook(() => useEmailsTabThread(7));
        await waitFor(() => expect(result.current.thread?.rootEmailId).toBe(7));

        act(() => result.current.reloadThread());
        await waitFor(() => expect(result.current.threadLoading).toBe(false));
        expect(result.current.thread?.rootEmailId).toBe(7);

        act(() => result.current.reloadThread());
        await waitFor(() => expect(result.current.threadLoading).toBe(false));
        expect(result.current.thread).toBeNull();
    });

    it('lädt ohne gewählte Mail auch auf Wunsch nichts', () => {
        const fetchMock = vi.fn();
        vi.stubGlobal('fetch', fetchMock);
        const { result } = renderHook(() => useEmailsTabThread(null));
        act(() => result.current.reloadThread());
        expect(fetchMock).not.toHaveBeenCalled();
        expect(result.current.threadLoading).toBe(false);
    });
});
