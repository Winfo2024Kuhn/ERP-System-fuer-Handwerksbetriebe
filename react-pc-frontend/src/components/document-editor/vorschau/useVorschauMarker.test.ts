import { describe, it, expect } from 'vitest';
import { renderHook } from '@testing-library/react';
import { useVorschauMarker } from './useVorschauMarker';

describe('useVorschauMarker', () => {
    it('meldet beim ersten Rendern keine Aenderung', () => {
        const { result } = renderHook(() => useVorschauMarker({ datum: '2026-09-01', balkenAnzeigen: true }));
        expect(result.current.geaendert).toBe(false);
    });

    it('liefert einen neuen Schluessel, sobald sich ein Feld aendert, und sonst denselben', () => {
        const abschlussZeilen = {};
        const { result, rerender } = renderHook(
            (inhalt: Record<string, unknown>) => useVorschauMarker(inhalt),
            { initialProps: { datum: '2026-09-01', abschlussZeilen } },
        );
        const erster = result.current.schluessel;

        rerender({ datum: '2026-09-01', abschlussZeilen });
        expect(result.current.schluessel).toBe(erster);

        rerender({ datum: '2026-09-01', abschlussZeilen: { differenz: { ausgeblendet: true } } });
        expect(result.current.schluessel).not.toBe(erster);
    });

    it('ruft beiAenderung genau einmal je Aenderung', () => {
        let aufrufe = 0;
        const { rerender } = renderHook(
            (inhalt: Record<string, unknown>) => useVorschauMarker(inhalt, () => { aufrufe += 1; }),
            { initialProps: { balkenAnzeigen: true } },
        );
        rerender({ balkenAnzeigen: false });
        rerender({ balkenAnzeigen: false });

        expect(aufrufe).toBe(1);
    });
});
