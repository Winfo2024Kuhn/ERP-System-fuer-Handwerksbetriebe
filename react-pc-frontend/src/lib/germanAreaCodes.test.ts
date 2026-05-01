import { describe, it, expect } from 'vitest';
import { lookupAreaCode } from './germanAreaCodes';

describe('lookupAreaCode', () => {
    it('liefert Vorwahl für Berlin per PLZ', () => {
        expect(lookupAreaCode('10115')).toBe('030');
        expect(lookupAreaCode('12345')).toBe('030');
    });

    it('liefert Vorwahl für Hamburg per PLZ', () => {
        expect(lookupAreaCode('20095')).toBe('040');
    });

    it('liefert Vorwahl für Hannover per PLZ', () => {
        expect(lookupAreaCode('30163')).toBe('0511');
    });

    it('liefert Vorwahl für München per PLZ', () => {
        expect(lookupAreaCode('80331')).toBe('089');
    });

    it('liefert Vorwahl per Ort wenn PLZ unbekannt', () => {
        expect(lookupAreaCode('', 'Berlin')).toBe('030');
        expect(lookupAreaCode('', 'Hamburg')).toBe('040');
    });

    it('akzeptiert deutsche Umlaute im Ort', () => {
        expect(lookupAreaCode('', 'München')).toBe('089');
        expect(lookupAreaCode('', 'Köln')).toBe('0221');
        expect(lookupAreaCode('', 'Würzburg')).toBe('0931');
    });

    it('liefert null für unbekannte PLZ und Ort', () => {
        expect(lookupAreaCode('00000', 'Nirgendwoland')).toBeNull();
    });

    it('liefert null für leere Eingaben', () => {
        expect(lookupAreaCode('')).toBeNull();
    });

    it('priorisiert PLZ vor Ort', () => {
        expect(lookupAreaCode('80331', 'Berlin')).toBe('089');
    });

    it('ignoriert Whitespace im Ort', () => {
        expect(lookupAreaCode('', '  Hannover  ')).toBe('0511');
    });
});
