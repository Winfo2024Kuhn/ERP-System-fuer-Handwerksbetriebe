import { describe, it, expect } from 'vitest';
import { ANREDE_AUSWAHL, anredeText, briefanrede } from './briefanrede';

describe('anredeText', () => {
    it.each([
        ['HERR', 'Sehr geehrter Herr'],
        ['herr', 'Sehr geehrter Herr'],
        ['FRAU', 'Sehr geehrte Frau'],
        [' Frau ', 'Sehr geehrte Frau'],
        ['FAMILIE', 'Sehr geehrte Familie'],
        ['FIRMA', 'Sehr geehrte Damen und Herren'],
        ['DAMEN_HERREN', 'Sehr geehrte Damen und Herren'],
        ['damen_herren', 'Sehr geehrte Damen und Herren'],
        ['', 'Sehr geehrte Damen und Herren'],
        [null, 'Sehr geehrte Damen und Herren'],
        [undefined, 'Sehr geehrte Damen und Herren'],
        ['UNBEKANNT', 'Sehr geehrte Damen und Herren'],
    ])('%s ergibt „%s“ und nie den Rohwert', (art, erwartet) => {
        expect(anredeText(art)).toBe(erwartet);
    });
});

describe('briefanrede', () => {
    it('setzt Herr, Frau und Familie mit Namen zusammen', () => {
        expect(briefanrede('HERR', 'Mustermann')).toBe('Sehr geehrter Herr Mustermann');
        expect(briefanrede('frau', 'Musterfrau')).toBe('Sehr geehrte Frau Musterfrau');
        expect(briefanrede('FAMILIE', 'Mustermann')).toBe('Sehr geehrte Familie Mustermann');
    });

    it('trimmt den Namen', () => {
        expect(briefanrede('HERR', '  Max Mustermann  ')).toBe('Sehr geehrter Herr Max Mustermann');
    });

    it('nimmt ohne Namen immer „Damen und Herren“, auch bei Herr und Frau', () => {
        expect(briefanrede('HERR', '')).toBe('Sehr geehrte Damen und Herren');
        expect(briefanrede('FRAU', '   ')).toBe('Sehr geehrte Damen und Herren');
        expect(briefanrede('FAMILIE', null)).toBe('Sehr geehrte Damen und Herren');
        expect(briefanrede('HERR', undefined)).toBe('Sehr geehrte Damen und Herren');
    });

    it('nimmt bei Firma, Damen und Herren, leer oder unbekannt keinen Namen', () => {
        expect(briefanrede('DAMEN_HERREN', 'Landkreis Musterstadt')).toBe('Sehr geehrte Damen und Herren');
        expect(briefanrede('FIRMA', 'Mustermann GmbH')).toBe('Sehr geehrte Damen und Herren');
        expect(briefanrede('', 'Mustermann')).toBe('Sehr geehrte Damen und Herren');
        expect(briefanrede(undefined, 'Mustermann')).toBe('Sehr geehrte Damen und Herren');
        expect(briefanrede('DIVERS', 'Mustermann')).toBe('Sehr geehrte Damen und Herren');
    });

    it('schneidet ein Herr, Frau oder Familie am Namensanfang ab', () => {
        expect(briefanrede('HERR', 'Herr Mustermann')).toBe('Sehr geehrter Herr Mustermann');
        expect(briefanrede('FRAU', 'frau Musterfrau')).toBe('Sehr geehrte Frau Musterfrau');
        expect(briefanrede('FAMILIE', 'FAMILIE  Mustermann')).toBe('Sehr geehrte Familie Mustermann');
    });

    it('nimmt „Damen und Herren“, wenn der Name nur aus Herr, Frau oder Familie besteht', () => {
        expect(briefanrede('HERR', 'Herr')).toBe('Sehr geehrte Damen und Herren');
        expect(briefanrede('FRAU', ' frau ')).toBe('Sehr geehrte Damen und Herren');
        expect(briefanrede('FAMILIE', 'Familie')).toBe('Sehr geehrte Damen und Herren');
    });

    it('lässt Namen stehen, die nur mit diesen Buchstaben beginnen', () => {
        expect(briefanrede('HERR', 'Herrmann')).toBe('Sehr geehrter Herr Herrmann');
        expect(briefanrede('FRAU', 'Frauke Muster')).toBe('Sehr geehrte Frau Frauke Muster');
    });
});

describe('ANREDE_AUSWAHL', () => {
    it('bietet die vier Anreden mit ihrem Anredetext als Beschriftung an', () => {
        expect(ANREDE_AUSWAHL).toEqual([
            { value: 'HERR', label: 'Sehr geehrter Herr' },
            { value: 'FRAU', label: 'Sehr geehrte Frau' },
            { value: 'DAMEN_HERREN', label: 'Sehr geehrte Damen und Herren' },
            { value: 'FAMILIE', label: 'Sehr geehrte Familie' },
        ]);
    });
});
