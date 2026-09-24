import { describe, expect, it } from 'vitest';
import {
    aufCent,
    bruttoAusNetto,
    mwstAusBrutto,
    nettoAusBrutto,
    schluesseleAuf,
    zuZahl,
} from './mwst';

/**
 * Tests für das Umsatzsteuer-Rechnen.
 *
 * Utils brauchen laut Projektvorgabe volle Abdeckung — hier besonders, weil
 * jeder Rundungsfehler direkt ins Kassenbuch wandert und nach der
 * Festschreibung nur noch per Storno korrigierbar wäre.
 */
describe('aufCent', () => {
    it('rundet auf zwei Nachkommastellen', () => {
        expect(aufCent(1.234)).toBe(1.23);
        expect(aufCent(1.235)).toBe(1.24);
        expect(aufCent(10)).toBe(10);
    });

    it('rundet die klassischen Fließkomma-Fallen richtig', () => {
        // 1.005 * 100 ergibt in Fließkomma 100.49999999999999 — ohne Korrektur
        // würde hier auf 1.00 abgerundet.
        expect(aufCent(1.005)).toBe(1.01);
        expect(aufCent(8.165)).toBe(8.17);
    });

    it('behandelt negative Beträge symmetrisch', () => {
        expect(aufCent(-19.994)).toBe(-19.99);
    });

    it('gibt bei unlesbaren Werten NaN zurück', () => {
        expect(aufCent(NaN)).toBeNaN();
        expect(aufCent(Infinity)).toBeNaN();
    });
});

describe('nettoAusBrutto', () => {
    it('rechnet 19 Prozent heraus', () => {
        expect(nettoAusBrutto(119, 19)).toBe(100);
        expect(nettoAusBrutto(19.99, 19)).toBe(16.8);
    });

    it('rechnet 7 Prozent heraus', () => {
        expect(nettoAusBrutto(107, 7)).toBe(100);
    });

    it('lässt den Betrag bei 0 Prozent unverändert', () => {
        expect(nettoAusBrutto(50, 0)).toBe(50);
    });

    it('gibt null zurück, solange die Eingaben nicht rechenbar sind', () => {
        expect(nettoAusBrutto(0, 19)).toBeNull();
        expect(nettoAusBrutto(-5, 19)).toBeNull();
        expect(nettoAusBrutto(NaN, 19)).toBeNull();
        expect(nettoAusBrutto(100, NaN)).toBeNull();
        expect(nettoAusBrutto(100, -1)).toBeNull();
    });
});

describe('mwstAusBrutto', () => {
    it('liefert den Steueranteil', () => {
        expect(mwstAusBrutto(119, 19)).toBe(19);
        expect(mwstAusBrutto(107, 7)).toBe(7);
    });

    it('ist bei 0 Prozent auch 0', () => {
        expect(mwstAusBrutto(50, 0)).toBe(0);
    });

    it('geht immer exakt mit dem Netto auf', () => {
        // Der eigentliche Grund für die Differenz-Berechnung: netto + mwst
        // muss den Bruttobetrag exakt treffen, sonst stimmt der Beleg nicht.
        for (const brutto of [19.99, 8.17, 1.01, 33.33, 12.5, 0.05, 999.99]) {
            for (const satz of [19, 7, 0]) {
                const netto = nettoAusBrutto(brutto, satz)!;
                const mwst = mwstAusBrutto(brutto, satz)!;
                expect(aufCent(netto + mwst)).toBe(aufCent(brutto));
            }
        }
    });

    it('gibt null zurück, wenn schon das Netto nicht rechenbar ist', () => {
        expect(mwstAusBrutto(0, 19)).toBeNull();
    });
});

describe('bruttoAusNetto', () => {
    it('schlägt die Steuer auf', () => {
        expect(bruttoAusNetto(100, 19)).toBe(119);
        expect(bruttoAusNetto(100, 7)).toBe(107);
        expect(bruttoAusNetto(100, 0)).toBe(100);
    });

    it('gibt null zurück bei unbrauchbaren Eingaben', () => {
        expect(bruttoAusNetto(0, 19)).toBeNull();
        expect(bruttoAusNetto(100, NaN)).toBeNull();
    });
});

describe('schluesseleAuf', () => {
    it('liefert alle drei Werte plus Satz', () => {
        expect(schluesseleAuf(119, 19)).toEqual({
            brutto: 119,
            netto: 100,
            mwst: 19,
            satzProzent: 19,
        });
    });

    it('gibt null zurück, solange die Eingabe unvollständig ist', () => {
        expect(schluesseleAuf(0, 19)).toBeNull();
        expect(schluesseleAuf(NaN, 19)).toBeNull();
    });
});

describe('zuZahl', () => {
    it('versteht das deutsche Komma', () => {
        expect(zuZahl('19,99')).toBe(19.99);
        expect(zuZahl('1234,5')).toBe(1234.5);
    });

    it('versteht auch den Punkt', () => {
        expect(zuZahl('19.99')).toBe(19.99);
    });

    it('ignoriert Leerzeichen', () => {
        expect(zuZahl('  19,99 ')).toBe(19.99);
        expect(zuZahl('1 234,50')).toBe(1234.5);
    });

    it('reicht Zahlen unverändert durch', () => {
        expect(zuZahl(42)).toBe(42);
        expect(zuZahl(0)).toBe(0);
    });

    it('gibt null zurück bei leeren oder unlesbaren Eingaben', () => {
        expect(zuZahl('')).toBeNull();
        expect(zuZahl('   ')).toBeNull();
        expect(zuZahl(null)).toBeNull();
        expect(zuZahl(undefined)).toBeNull();
        expect(zuZahl('keine Zahl')).toBeNull();
        expect(zuZahl(NaN)).toBeNull();
    });
});
