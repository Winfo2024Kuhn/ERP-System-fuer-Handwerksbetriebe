import { describe, expect, it } from 'vitest';
import { BESTELLBEDARF_PFAD, istFreigegeben } from './freigaben';

describe('istFreigegeben', () => {
    it('sperrt die Seite „Bedarf“, solange das Bestellwesen nicht fertig ist', () => {
        expect(istFreigegeben(BESTELLBEDARF_PFAD)).toBe(false);
    });

    it('lässt alle übrigen Seiten durch', () => {
        expect(istFreigegeben('/bestellungen')).toBe(true);
        expect(istFreigegeben('/projekte')).toBe(true);
    });
});
