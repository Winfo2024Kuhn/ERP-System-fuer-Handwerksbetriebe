import { describe, it, expect } from 'vitest';
import { validateNumberDrafts } from './numberDrafts';
describe('Finanzentwürfe', () => {
    it('prüft die ganze Feldgruppe ohne Teilübernahme', () => {
        expect(validateNumberDrafts({ betrag: '12,50', jahr: '2026,' }, { betrag: { label: 'Betrag', required: true }, jahr: { label: 'Jahr', required: true, integer: true } }).valid).toBe(false);
    });
    it('wandelt Komma und optional leer nach vollständiger Prüfung um', () => {
        expect(validateNumberDrafts({ betrag: '12,50', verbrauch: '' }, { betrag: { label: 'Betrag', required: true }, verbrauch: { label: 'Verbrauch' } })).toEqual({ valid: true, values: { betrag: 12.5, verbrauch: null } });
    });
    it('weist leere Pflichtzahl und verletzte Grenze ab', () => {
        for (const jahr of ['', '0', '12,5'])
            expect(validateNumberDrafts({ jahr }, { jahr: { label: 'Jahr', required: true, integer: true, min: 1 } }).valid).toBe(false);
    });
});
it('prüft die Nachkommastellen ohne Rundung und erlaubt nachlaufende Nullen', () => {
    expect(validateNumberDrafts({ betrag: '12,501' }, { betrag: { label: 'Betrag', required: true, maxDecimalPlaces: 2 } }).valid).toBe(false);
    expect(validateNumberDrafts({ betrag: '12,5000' }, { betrag: { label: 'Betrag', required: true, maxDecimalPlaces: 2 } })).toEqual({ valid: true, values: { betrag: 12.5 } });
});
