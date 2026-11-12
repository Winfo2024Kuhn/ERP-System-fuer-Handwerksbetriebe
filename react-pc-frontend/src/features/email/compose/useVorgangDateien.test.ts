import { act, renderHook, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { useVorgangDateien } from './useVorgangDateien';

const datei = (id: number, name: string) => ({ id, originalDateiname: name, url: `/api/dateien/${id}`, dokumentGruppe: 'DIVERSE_DOKUMENTE' });

function antwort(body: unknown) {
    return new Response(JSON.stringify(body), { status: 200, headers: { 'Content-Type': 'application/json' } });
}

afterEach(() => { vi.unstubAllGlobals(); });

describe('useVorgangDateien', () => {
    it('verwirft die späte Antwort des vorigen Projekts und leert die Liste beim Wechsel', async () => {
        let altFreigeben: (r: Response) => void = () => undefined;
        vi.stubGlobal('fetch', vi.fn((url: string) => {
            if (url === '/api/projekte/1/dokumente') return new Promise<Response>(resolve => { altFreigeben = resolve; });
            if (url === '/api/projekte/2/dokumente') return Promise.resolve(antwort([datei(20, 'plan-neu.pdf')]));
            return Promise.resolve(antwort([]));
        }));
        const onFehler = vi.fn();
        const { result, rerender } = renderHook(({ id }) => useVorgangDateien(id, false, onFehler), { initialProps: { id: 1 } });
        expect(result.current.laedt).toBe(true);

        rerender({ id: 2 });
        await waitFor(() => expect(result.current.dokumente.map(d => d.id)).toEqual([20]));

        await act(async () => { altFreigeben(antwort([datei(10, 'fremd.pdf')])); });
        expect(result.current.dokumente.map(d => d.id)).toEqual([20]);
        expect(result.current.laedt).toBe(false);
        expect(onFehler).not.toHaveBeenCalled();
    });

    it('zeigt beim Wechsel nicht kurz die alte Liste', async () => {
        vi.stubGlobal('fetch', vi.fn((url: string) => (url === '/api/projekte/1/dokumente'
            ? Promise.resolve(antwort([datei(10, 'alt.pdf')]))
            : new Promise<Response>(() => undefined))));
        const { result, rerender } = renderHook(({ id }) => useVorgangDateien(id, false, vi.fn()), { initialProps: { id: 1 } });
        await waitFor(() => expect(result.current.dokumente).toHaveLength(1));
        rerender({ id: 2 });
        expect(result.current.dokumente).toEqual([]);
        expect(result.current.laedt).toBe(true);
    });
});
