import { describe, it, expect } from 'vitest';
import { formatDecimalInput, validateDecimalInput } from './numberInput';
const rules = { label: 'Stunden', required: true };
describe('Deutsche Zahleneingabe', () => {
    it.each(['', ' ', '12,', '1.5', '12abc', 'NaN', 'Infinity', '1e3', '1,2,3'])('lehnt unvollständigen Wert %s ab', value => {
        expect(validateDecimalInput(value, rules).valid).toBe(false);
    });
    it('wandelt vollständige Kommazahlen ohne Teilpräfixe um', () => {
        expect(validateDecimalInput('12,50', rules)).toEqual({ valid: true, value: 12.5 });
        expect(validateDecimalInput('-0,25', rules)).toEqual({ valid: true, value: -0.25 });
        expect(validateDecimalInput('0', rules)).toEqual({ valid: true, value: 0 });
        expect(validateDecimalInput('', { label: 'Optional' })).toEqual({ valid: true, value: null });
    });
    it('prüft Grenzen und Ganzzahlen', () => {
        expect(validateDecimalInput('-1', { ...rules, min: 0 }).valid).toBe(false);
        expect(validateDecimalInput('24,1', { ...rules, max: 24 }).valid).toBe(false);
        expect(validateDecimalInput('1,5', { ...rules, integer: true }).valid).toBe(false);
        expect(validateDecimalInput('24', { ...rules, min: 0, max: 24, integer: true }).valid).toBe(true);
    });
    it('formatiert ohne Rundungsverlust und ohne Tausenderpunkte', () => {
        expect(formatDecimalInput(1234.56789)).toBe('1234,56789');
        expect(formatDecimalInput(0)).toBe('0');
        expect(formatDecimalInput(1e-7)).toBe('0,0000001');
        expect(() => formatDecimalInput(Infinity)).toThrow();
    });
});

it('weist zu große Zahlen mit konkreter Meldung zurück', () => {
    expect(validateDecimalInput('9'.repeat(400), { label: 'Betrag' })).toEqual({ valid: false, message: 'Betrag: Die Zahl ist zu groß.' });
});
