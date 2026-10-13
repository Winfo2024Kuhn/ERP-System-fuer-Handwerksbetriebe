import { afterEach, describe, expect, it, vi } from 'vitest';
import { act, renderHook } from '@testing-library/react';
import { einpassZoom, MAX_ZOOM, MIN_ZOOM, useSeitenZoom } from './useSeitenZoom';

describe('einpassZoom', () => {
    it('passt die ganze Seite mit Rand in den Bereich', () => {
        expect(einpassZoom({ breite: 1000, hoehe: 890 }, { breite: 595, hoehe: 842 })).toBe(1);
        expect(einpassZoom({ breite: 1000, hoehe: 469 }, { breite: 595, hoehe: 842 })).toBe(0.5);
    });

    it('bleibt in den Grenzen und kommt mit leerem Bereich klar', () => {
        expect(einpassZoom({ breite: 100, hoehe: 100 }, { breite: 595, hoehe: 842 })).toBe(MIN_ZOOM);
        expect(einpassZoom({ breite: 9000, hoehe: 9000 }, { breite: 595, hoehe: 842 })).toBe(MAX_ZOOM);
        expect(einpassZoom({ breite: 0, hoehe: 0 }, { breite: 595, hoehe: 842 })).toBe(1);
    });
});

describe('useSeitenZoom', () => {
    it('startet eingepasst, zoomt in 10er-Schritten und kehrt zum Einpassen zurück', () => {
        const ref = { current: null };
        const { result } = renderHook(() => useSeitenZoom(ref, { breite: 595, hoehe: 842 }));
        expect(result.current.eingepasst).toBe(true);
        expect(result.current.zoom).toBe(1);
        act(() => result.current.vergroessern());
        expect(result.current).toMatchObject({ zoom: 1.1, eingepasst: false });
        act(() => result.current.verkleinern());
        act(() => result.current.verkleinern());
        expect(result.current.zoom).toBe(0.9);
        act(() => result.current.einpassen());
        expect(result.current).toMatchObject({ zoom: 1, eingepasst: true });
    });

    const echterBeobachter = window.ResizeObserver;
    afterEach(() => { window.ResizeObserver = echterBeobachter; });

    it('misst den Bereich und folgt seiner Größe', () => {
        let melden = () => {};
        const beobachten = vi.fn();
        const trennen = vi.fn();
        window.ResizeObserver = class {
            constructor(rueckruf: () => void) { melden = rueckruf; }
            observe = beobachten;
            unobserve = vi.fn();
            disconnect = trennen;
        } as unknown as typeof ResizeObserver;
        const bereich = document.createElement('div');
        let hoehe = 890;
        Object.defineProperty(bereich, 'clientWidth', { get: () => 1000 });
        Object.defineProperty(bereich, 'clientHeight', { get: () => hoehe });
        const { result, unmount } = renderHook(() => useSeitenZoom({ current: bereich }, { breite: 595, hoehe: 842 }));
        expect(result.current.zoom).toBe(1);
        expect(beobachten).toHaveBeenCalledWith(bereich);
        hoehe = 469;
        act(() => melden());
        expect(result.current.zoom).toBe(0.5);
        unmount();
        expect(trennen).toHaveBeenCalled();
    });

    it('misst ohne ResizeObserver einmal beim Start', () => {
        window.ResizeObserver = undefined as unknown as typeof ResizeObserver;
        const bereich = document.createElement('div');
        Object.defineProperty(bereich, 'clientWidth', { get: () => 1000 });
        Object.defineProperty(bereich, 'clientHeight', { get: () => 469 });
        const { result } = renderHook(() => useSeitenZoom({ current: bereich }, { breite: 595, hoehe: 842 }));
        expect(result.current.zoom).toBe(0.5);
    });
});
