import { describe, expect, it } from 'vitest';
import { anfragePfad, lieferantPfad, projektPfad } from './navigationPfade';

describe('navigationPfade', () => {
    it('baut die Pfade mit dem Query-Parameter, den die Zielseiten auswerten', () => {
        expect(projektPfad(21)).toBe('/projekte?projektId=21');
        expect(anfragePfad(32)).toBe('/anfragen?anfrageId=32');
        expect(lieferantPfad(7)).toBe('/lieferanten?lieferantId=7');
    });

    it('kodiert die ID für die URL', () => {
        // Zur Laufzeit kann eine ungeprüfte ID ankommen – sie darf den Query-String nicht aufbrechen.
        const unsauber = '1&tab=x' as unknown as number;
        expect(projektPfad(unsauber)).toBe('/projekte?projektId=1%26tab%3Dx');
        expect(anfragePfad(unsauber)).toBe('/anfragen?anfrageId=1%26tab%3Dx');
        expect(lieferantPfad(unsauber)).toBe('/lieferanten?lieferantId=1%26tab%3Dx');
    });
});
