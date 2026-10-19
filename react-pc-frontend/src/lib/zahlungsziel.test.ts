import { describe, expect, it } from 'vitest';
import {
    MAX_ZAHLUNGSZIEL_TAGE,
    MIN_ZAHLUNGSZIEL_TAGE,
    ZAHLUNGSZIEL_MELDUNG,
    pruefeZahlungszielTage,
} from './zahlungsziel';

describe('pruefeZahlungszielTage', () => {
    it.each([
        ['1', 1],
        ['8', 8],
        ['14', 14],
        ['365', 365],
        ['  30 ', 30],
        ['007', 7],
    ])('nimmt %j als %d Tage an', (entwurf, tage) => {
        expect(pruefeZahlungszielTage(entwurf)).toEqual({ gueltig: true, tage });
    });

    it.each(['', '   ', '0', '000', '366', '999', '1000', '-5', '+5', '1,5', '1.5', '14 Tage', 'abc', '1e2'])(
        'lehnt %j ab',
        (entwurf) => {
            expect(pruefeZahlungszielTage(entwurf)).toEqual({ gueltig: false, meldung: ZAHLUNGSZIEL_MELDUNG });
        },
    );

    it('nennt die Grenzen in der Meldung', () => {
        expect(ZAHLUNGSZIEL_MELDUNG).toBe('Das Zahlungsziel muss zwischen 1 und 365 Tagen liegen.');
        expect(MIN_ZAHLUNGSZIEL_TAGE).toBe(1);
        expect(MAX_ZAHLUNGSZIEL_TAGE).toBe(365);
    });
});
