import { describe, expect, it } from 'vitest';
import { neueAdresseZumSpeichern, versandHindernis } from './emailVersandPruefung';

const OK = {
    empfaenger: 'max.mustermann@example.com', betreff: 'Angebot', anhaengeZuGross: false,
    zuVieleEmpfaenger: false, pflichtanhangFehlt: false,
};

describe('versandHindernis', () => {
    it('liefert undefined, wenn alles passt', () => {
        expect(versandHindernis(OK)).toBeUndefined();
    });

    it('nennt das erste Hindernis', () => {
        expect(versandHindernis({ ...OK, empfaenger: ' ' })).toBe('Bitte Empfänger angeben.');
        expect(versandHindernis({ ...OK, betreff: '' })).toBe('Bitte Betreff angeben.');
        expect(versandHindernis({ ...OK, anhaengeZuGross: true })).toMatch(/^Anhänge dürfen zusammen höchstens/);
        expect(versandHindernis({ ...OK, zuVieleEmpfaenger: true })).toMatch(/Empfänger/);
        expect(versandHindernis({ ...OK, pflichtanhangFehlt: true, pflichtanhangHinweis: 'Rechnung fehlt.' })).toBe('Rechnung fehlt.');
        expect(versandHindernis({ ...OK, pflichtanhangFehlt: true })).toBeNull();
    });
});

describe('neueAdresseZumSpeichern', () => {
    it('bietet nur eine einzelne, noch unbekannte Adresse an', () => {
        expect(neueAdresseZumSpeichern('"Max" <max.mustermann@example.com>', [])).toBe('max.mustermann@example.com');
        expect(neueAdresseZumSpeichern('max.mustermann@example.com', ['Max <MAX.MUSTERMANN@example.com>'])).toBe('');
        expect(neueAdresseZumSpeichern('a@example.com, b@example.org', [])).toBe('');
    });
});
