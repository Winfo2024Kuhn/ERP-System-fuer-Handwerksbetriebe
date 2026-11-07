import { act, renderHook } from '@testing-library/react';
import type { DragEvent } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { useAblage } from './useAblage';
import type { ZiehObjekt } from './typen';

/** Schlankes Zieh-Ereignis für den Hook – nur was er liest. */
function ereignis(optionen: { types?: string[]; files?: File[]; relatedTarget?: Node | null; enthaelt?: boolean } = {}) {
    const dataTransfer = { types: optionen.types ?? [], files: optionen.files ?? [], dropEffect: '' };
    return {
        dataTransfer,
        preventDefault: vi.fn(),
        relatedTarget: optionen.relatedTarget ?? null,
        currentTarget: { contains: () => optionen.enthaelt ?? false },
    } as unknown as DragEvent<HTMLDivElement> & { dataTransfer: typeof dataTransfer; preventDefault: ReturnType<typeof vi.fn> };
}

function zeige(optionen: { ziehen: ZiehObjekt | null; erlaubt: boolean; bilderErlaubt: boolean }) {
    const onAblegen = vi.fn();
    const onBild = vi.fn();
    const hook = renderHook(() => useAblage({ ...optionen, onAblegen, onBild }));
    return { ...hook, onAblegen, onBild };
}

describe('useAblage', () => {
    it('erlaubtes Ziel: zeigt „drüber“ und legt beim Loslassen ab', () => {
        const { result, onAblegen } = zeige({ ziehen: { art: 'ordner', id: 4 }, erlaubt: true, bilderErlaubt: true });
        const ueber = ereignis();
        act(() => result.current.handler.onDragOver(ueber));
        expect(ueber.preventDefault).toHaveBeenCalled();
        expect(ueber.dataTransfer.dropEffect).toBe('move');
        expect(result.current.drueber).toBe('objekt');
        act(() => result.current.handler.onDrop(ereignis()));
        expect(onAblegen).toHaveBeenCalledTimes(1);
        expect(result.current.drueber).toBeNull();
    });

    it('verbotenes Ziel: nimmt nichts an', () => {
        const { result, onAblegen } = zeige({ ziehen: { art: 'ordner', id: 4 }, erlaubt: false, bilderErlaubt: true });
        const ueber = ereignis();
        act(() => result.current.handler.onDragOver(ueber));
        expect(ueber.preventDefault).not.toHaveBeenCalled();
        expect(ueber.dataTransfer.dropEffect).toBe('none');
        expect(result.current.drueber).toBeNull();
        act(() => result.current.handler.onDrop(ereignis()));
        expect(onAblegen).not.toHaveBeenCalled();
    });

    it('Bilddatei aus dem Finder wird zum Ordnerbild', () => {
        const { result, onBild } = zeige({ ziehen: null, erlaubt: false, bilderErlaubt: true });
        act(() => result.current.handler.onDragOver(ereignis({ types: ['Files'] })));
        expect(result.current.drueber).toBe('bild');
        const datei = new File(['x'], 'tor.png', { type: 'image/png' });
        act(() => result.current.handler.onDrop(ereignis({ files: [datei] })));
        expect(onBild).toHaveBeenCalledWith(datei);
    });

    it('ohne Bilder-Erlaubnis bleibt eine Datei wirkungslos', () => {
        const { result } = zeige({ ziehen: null, erlaubt: false, bilderErlaubt: false });
        const ueber = ereignis({ types: ['Files'] });
        act(() => result.current.handler.onDragOver(ueber));
        expect(ueber.preventDefault).not.toHaveBeenCalled();
        expect(result.current.drueber).toBeNull();
    });

    it('Verlassen in ein Kind-Element behält die Markierung, echtes Verlassen löscht sie', () => {
        const { result } = zeige({ ziehen: { art: 'leistung', id: 11 }, erlaubt: true, bilderErlaubt: false });
        act(() => result.current.handler.onDragOver(ereignis()));
        act(() => result.current.handler.onDragLeave(ereignis({ enthaelt: true })));
        expect(result.current.drueber).toBe('objekt');
        act(() => result.current.handler.onDragLeave(ereignis({ enthaelt: false })));
        expect(result.current.drueber).toBeNull();
    });
});
