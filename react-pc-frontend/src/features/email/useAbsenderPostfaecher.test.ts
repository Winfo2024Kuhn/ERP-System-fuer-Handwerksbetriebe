import { afterEach, describe, expect, it, vi } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { useAbsenderPostfaecher } from './useAbsenderPostfaecher';

afterEach(() => vi.unstubAllGlobals());

describe('useAbsenderPostfaecher', () => {
    it('lädt die Postfächer', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify([
            { id: 3, emailAdresse: 'info@musterbetrieb.example', anzeigename: null, eigenes: false, hauptpostfach: true },
        ]), { status: 200 })));
        const { result } = renderHook(() => useAbsenderPostfaecher());
        expect(result.current.laedt).toBe(true);
        await waitFor(() => expect(result.current.laedt).toBe(false));
        expect(result.current).toMatchObject({ fehler: false, postfaecher: [{ id: 3 }] });
    });

    it('meldet Fehler', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('{}', { status: 500 })));
        const { result } = renderHook(() => useAbsenderPostfaecher());
        await waitFor(() => expect(result.current.fehler).toBe(true));
        expect(result.current.postfaecher).toEqual([]);
    });

    it('ignoriert Antworten nach dem Schließen', async () => {
        let erfolg!: (r: Response) => void;
        let fehlschlag!: (e: Error) => void;
        vi.stubGlobal('fetch', vi.fn()
            .mockImplementationOnce(() => new Promise<Response>(r => { erfolg = r; }))
            .mockImplementationOnce(() => new Promise<Response>((_, f) => { fehlschlag = f; })));
        const erster = renderHook(() => useAbsenderPostfaecher());
        erster.unmount();
        erfolg(new Response('[]', { status: 200 }));
        const zweiter = renderHook(() => useAbsenderPostfaecher());
        zweiter.unmount();
        fehlschlag(new Error('offline'));
        await new Promise(r => setTimeout(r, 0));
        expect(erster.result.current.laedt).toBe(true);
        expect(zweiter.result.current.laedt).toBe(true);
    });
});
