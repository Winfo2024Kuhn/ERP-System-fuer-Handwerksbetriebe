import { renderHook, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { useDarfAufschlaegeAendern } from './useDarfAufschlaegeAendern';

const antwort = (body: unknown, ok = true) => ({ ok, status: ok ? 200 : 500, json: async () => body });

afterEach(() => { vi.unstubAllGlobals(); });

describe('useDarfAufschlaegeAendern', () => {
    it('ist am Anfang falsch und danach wahr, wenn der Server das Recht bestätigt', async () => {
        const fetcher = vi.fn(async () => antwort({ darfAendern: true }));
        vi.stubGlobal('fetch', fetcher);

        const { result } = renderHook(() => useDarfAufschlaegeAendern());

        expect(result.current).toBe(false);
        await waitFor(() => expect(result.current).toBe(true));
        expect(fetcher).toHaveBeenCalledWith('/api/aufschlaege/berechtigung', expect.anything());
    });

    it('bleibt falsch ohne Recht, bei Serverfehler und wenn der Server nicht erreichbar ist', async () => {
        vi.stubGlobal('fetch', vi.fn(async () => antwort({ darfAendern: false })));
        const ohneRecht = renderHook(() => useDarfAufschlaegeAendern());
        await waitFor(() => expect(vi.mocked(fetch)).toHaveBeenCalled());
        expect(ohneRecht.result.current).toBe(false);

        vi.stubGlobal('fetch', vi.fn(async () => antwort({}, false)));
        const fehler = renderHook(() => useDarfAufschlaegeAendern());
        await waitFor(() => expect(vi.mocked(fetch)).toHaveBeenCalled());
        expect(fehler.result.current).toBe(false);

        vi.stubGlobal('fetch', vi.fn(async () => { throw new Error('Netz weg'); }));
        const offline = renderHook(() => useDarfAufschlaegeAendern());
        await waitFor(() => expect(vi.mocked(fetch)).toHaveBeenCalled());
        expect(offline.result.current).toBe(false);
    });

    it('fragt bei jedem Einhängen neu, damit nach einem Benutzerwechsel kein fremdes Recht stehen bleibt', async () => {
        const fetcher = vi.fn(async () => antwort({ darfAendern: true }));
        vi.stubGlobal('fetch', fetcher);

        const erster = renderHook(() => useDarfAufschlaegeAendern());
        await waitFor(() => expect(erster.result.current).toBe(true));
        erster.unmount();
        fetcher.mockImplementation(async () => antwort({ darfAendern: false }));
        const zweiter = renderHook(() => useDarfAufschlaegeAendern());

        expect(zweiter.result.current).toBe(false);
        await waitFor(() => expect(fetcher).toHaveBeenCalledTimes(2));
        expect(zweiter.result.current).toBe(false);
    });
});
