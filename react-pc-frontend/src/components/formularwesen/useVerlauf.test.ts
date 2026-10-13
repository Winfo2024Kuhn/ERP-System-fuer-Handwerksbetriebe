import { describe, expect, it } from 'vitest';
import { act, renderHook } from '@testing-library/react';
import { useVerlauf } from './useVerlauf';

describe('useVerlauf', () => {
    it('macht Änderungen rückgängig und wiederholt sie', () => {
        const { result } = renderHook(() => useVerlauf(1));
        act(() => result.current.aendern(2));
        act(() => result.current.aendern(v => v + 1));
        expect(result.current).toMatchObject({ stand: 3, kannRueckgaengig: true, kannWiederholen: false });
        act(() => result.current.rueckgaengig());
        expect(result.current.stand).toBe(2);
        act(() => result.current.rueckgaengig());
        expect(result.current).toMatchObject({ stand: 1, kannRueckgaengig: false, kannWiederholen: true });
        act(() => result.current.rueckgaengig());
        expect(result.current.stand).toBe(1);
        act(() => result.current.wiederholen());
        act(() => result.current.wiederholen());
        act(() => result.current.wiederholen());
        expect(result.current).toMatchObject({ stand: 3, kannWiederholen: false });
    });

    it('fasst Änderungen derselben Gruppe zu einem Schritt zusammen (ein Zug mit der Maus)', () => {
        const { result } = renderHook(() => useVerlauf(0));
        act(() => {
            result.current.aendern(1, 'zug-1');
            result.current.aendern(2, 'zug-1');
            result.current.aendern(3, 'zug-1');
        });
        act(() => result.current.aendern(4, 'zug-2'));
        act(() => result.current.rueckgaengig());
        expect(result.current.stand).toBe(3);
        act(() => result.current.rueckgaengig());
        expect(result.current.stand).toBe(0);
    });

    it('nach Rückgängig beginnt dieselbe Gruppe einen neuen Schritt, neue Änderung löscht Wiederholen', () => {
        const { result } = renderHook(() => useVerlauf(0));
        act(() => result.current.aendern(1, 'tippen'));
        act(() => result.current.rueckgaengig());
        act(() => result.current.aendern(5, 'tippen'));
        expect(result.current).toMatchObject({ stand: 5, kannRueckgaengig: true, kannWiederholen: false });
    });

    it('ignoriert Änderungen ohne Wirkung und setzt beim Zurücksetzen den Verlauf leer', () => {
        const { result } = renderHook(() => useVerlauf(7));
        act(() => result.current.aendern(v => v));
        expect(result.current.kannRueckgaengig).toBe(false);
        act(() => result.current.aendern(8));
        act(() => result.current.zuruecksetzen(1));
        expect(result.current).toMatchObject({ stand: 1, kannRueckgaengig: false, kannWiederholen: false });
    });

    it('merkt sich höchstens 50 Schritte', () => {
        const { result } = renderHook(() => useVerlauf(0));
        for (let i = 1; i <= 60; i++) act(() => result.current.aendern(i));
        for (let i = 0; i < 60; i++) act(() => result.current.rueckgaengig());
        expect(result.current.stand).toBe(10);
    });
});
