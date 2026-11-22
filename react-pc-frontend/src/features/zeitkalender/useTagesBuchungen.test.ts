import { act, renderHook } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import type { Buchung } from './kalenderTypen';
import { useTagesBuchungen } from './useTagesBuchungen';

const b = (id: number, start: string, ende: string, typ: Buchung['typ'] = 'ARBEIT'): Buchung => ({
    id, projektId: typ === 'PAUSE' ? -1 : 1, arbeitsgangId: 1, projektName: '', arbeitsgangName: '', startZeit: start, endeZeit: ende,
    dauerMinuten: null, dauerFormatiert: null, notiz: null, typ,
});

describe('useTagesBuchungen', () => {
    it('verschiebt beim Ändern des Pausenendes die Anschlussbuchung und merkt sie zum Speichern vor', () => {
        const { result } = renderHook(() => useTagesBuchungen([b(1, '08:00', '12:00'), b(2, '12:00', '12:30', 'PAUSE'), b(3, '12:30', '16:00')]));
        act(() => result.current.aendern(2, { endeZeit: '13:00' }));

        const anschluss = result.current.buchungen.find(x => x.id === 3)!;
        expect(result.current.zeitEntwurf(anschluss, 'startZeit')).toBe('13:00');
        expect([...result.current.geaenderteIds].sort()).toEqual([2, 3]);
    });

    it('teilt beim Hinzufügen einer Pause die laufende Arbeitszeit', () => {
        const { result } = renderHook(() => useTagesBuchungen([b(1, '08:00', '16:00')]));
        act(() => result.current.neuePause());
        expect(result.current.buchungen.map(x => result.current.zeitEntwurf(x, 'startZeit'))).toEqual(['08:00', '12:00', '12:30']);
        expect(result.current.ungespeichert()).toBe(true);
    });

    it('vergisst gespeicherte Änderungen', () => {
        const { result } = renderHook(() => useTagesBuchungen([b(1, '08:00', '16:00')]));
        act(() => result.current.aendern(1, { startZeit: '09:00' }));
        expect(result.current.ungespeichert()).toBe(true);
        act(() => result.current.alsGespeichertMerken([1]));
        expect(result.current.ungespeichert()).toBe(false);
    });
});
