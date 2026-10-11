import { describe, expect, it } from 'vitest';
import { ohneReineVorwahl, sanitizePhoneInput, toDialablePhone } from './phoneUtils';

describe('toDialablePhone', () => {
    it('entfernt Trennzeichen und behält ein führendes +', () => {
        expect(toDialablePhone('0931 / 123-45 6')).toBe('0931123456');
        expect(toDialablePhone(' +49 (931) 123456 ')).toBe('+49931123456');
    });

    it('liefert für leere Werte einen leeren Text', () => {
        expect(toDialablePhone(null)).toBe('');
        expect(toDialablePhone(undefined)).toBe('');
        expect(toDialablePhone('')).toBe('');
    });
});

describe('sanitizePhoneInput', () => {
    it('lässt nur Ziffern, +, -, /, Klammern und Leerzeichen stehen', () => {
        expect(sanitizePhoneInput('+49 (0931) 12-34/5abc')).toBe('+49 (0931) 12-34/5');
    });
});

describe('ohneReineVorwahl', () => {
    it.each([
        '0931 ',
        '0931',
        ' 089 ',
        '+49 931',
        '0049 931',
        '+49',
        '0931 12',
    ])('leert die reine Vorwahl "%s"', (eingabe) => {
        expect(ohneReineVorwahl(eingabe)).toBe('');
    });

    it.each([
        ['0931 123', '0931 123'],
        ['  0931 123456 ', '0931 123456'],
        ['+49 170 1234567', '+49 170 1234567'],
        ['030/123-4567', '030/123-4567'],
    ])('behält die vollständige Nummer "%s" (nur getrimmt)', (eingabe, erwartet) => {
        expect(ohneReineVorwahl(eingabe)).toBe(erwartet);
    });

    it('behält kurze Ortsnummern ohne Vorwahl – wie das Backend', () => {
        expect(ohneReineVorwahl('12345')).toBe('12345');
    });

    it('liefert für leere Werte einen leeren Text', () => {
        expect(ohneReineVorwahl(null)).toBe('');
        expect(ohneReineVorwahl(undefined)).toBe('');
        expect(ohneReineVorwahl('   ')).toBe('');
    });
});
