import { describe, expect, it } from 'vitest';
import { BESTELLBEDARF_AKTIV, BESTELLBEDARF_PFAD } from './freigaben';
import { KI_HILFE_ROUTEN, kiHilfeLinkZiel } from './kiHilfeRouten';

describe('kiHilfeLinkZiel', () => {
    it('lässt bekannte Seiten zu, auch ohne führenden Schrägstrich', () => {
        expect(kiHilfeLinkZiel('/projekte')).toBe('/projekte');
        expect(kiHilfeLinkZiel('firmendokumente')).toBe('/firmendokumente');
    });

    it('lehnt unbekannte und fremde Ziele ab', () => {
        expect(kiHilfeLinkZiel('/gibt-es-nicht')).toBeNull();
        expect(kiHilfeLinkZiel('//evil.example')).toBeNull();
        expect(kiHilfeLinkZiel('javascript:alert(1)')).toBeNull();
    });

    it('verlinkt nicht freigegebene Seiten nicht', () => {
        expect(KI_HILFE_ROUTEN.has(BESTELLBEDARF_PFAD)).toBe(BESTELLBEDARF_AKTIV);
    });
});
