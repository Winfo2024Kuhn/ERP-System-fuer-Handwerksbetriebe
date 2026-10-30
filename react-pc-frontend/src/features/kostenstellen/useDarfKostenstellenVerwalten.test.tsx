import { renderHook, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { useDarfKostenstellenVerwalten } from './useDarfKostenstellenVerwalten';

const antwort = (body: unknown, ok = true) => ({ ok, status: ok ? 200 : 403, json: async () => body });

afterEach(() => { vi.unstubAllGlobals(); });

describe('useDarfKostenstellenVerwalten', () => {
    it('fragt das Recht „Kostenstellen verwalten“ ab und ist danach wahr', async () => {
        const fetcher = vi.fn(async () => antwort({ darfAendern: true }));
        vi.stubGlobal('fetch', fetcher);

        const { result } = renderHook(() => useDarfKostenstellenVerwalten());

        expect(result.current).toBe(false);
        await waitFor(() => expect(result.current).toBe(true));
        expect(fetcher).toHaveBeenCalledWith('/api/firma/kostenstellen/berechtigung', expect.anything());
    });

    it('bleibt falsch ohne Recht, bei Fehlerantwort und wenn der Server nicht erreichbar ist', async () => {
        vi.stubGlobal('fetch', vi.fn(async () => antwort({ darfAendern: false })));
        const ohneRecht = renderHook(() => useDarfKostenstellenVerwalten());
        await waitFor(() => expect(vi.mocked(fetch)).toHaveBeenCalled());
        expect(ohneRecht.result.current).toBe(false);

        vi.stubGlobal('fetch', vi.fn(async () => antwort({ darfAendern: true }, false)));
        const fehler = renderHook(() => useDarfKostenstellenVerwalten());
        await waitFor(() => expect(vi.mocked(fetch)).toHaveBeenCalled());
        expect(fehler.result.current).toBe(false);

        vi.stubGlobal('fetch', vi.fn(async () => { throw new Error('Netz weg'); }));
        const offline = renderHook(() => useDarfKostenstellenVerwalten());
        await waitFor(() => expect(vi.mocked(fetch)).toHaveBeenCalled());
        expect(offline.result.current).toBe(false);
    });
});
