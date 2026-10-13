import { describe, expect, it } from 'vitest';
import { formatUrlaubstage, formatUrlaubstageMitEinheit } from './urlaubstage';

describe('formatUrlaubstage', () => {
    it('zeigt ganze Tage ohne Nachkomma', () => {
        expect(formatUrlaubstage(12)).toBe('12');
        expect(formatUrlaubstage(0)).toBe('0');
    });

    it('zeigt halbe Tage mit Komma', () => {
        expect(formatUrlaubstage(12.5)).toBe('12,5');
        expect(formatUrlaubstage(0.5)).toBe('0,5');
    });

    it('rundet nicht auf ganze Tage, wenn der Server feiner liefert', () => {
        expect(formatUrlaubstage(1.25)).toBe('1,25');
    });

    it('fällt bei fehlenden oder ungültigen Werten auf 0 zurück', () => {
        expect(formatUrlaubstage(null)).toBe('0');
        expect(formatUrlaubstage(undefined)).toBe('0');
        expect(formatUrlaubstage(Number.NaN)).toBe('0');
    });
});

describe('formatUrlaubstageMitEinheit', () => {
    it('hängt die passende Einheit an', () => {
        expect(formatUrlaubstageMitEinheit(1)).toBe('1 Tag');
        expect(formatUrlaubstageMitEinheit(12.5)).toBe('12,5 Tage');
        expect(formatUrlaubstageMitEinheit(0)).toBe('0 Tage');
    });
});
