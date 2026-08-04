import { describe, expect, it } from 'vitest';
import { euro, monatLabel, naechsterOffenerMonat } from './kassenbuchMonate';

/**
 * Tests für die Monatslogik des Kassenbuch-Abschlusses.
 *
 * Der wunde Punkt ist die Jahresgrenze: Wer im Dezember abschließt, muss als
 * Nächstes den Januar des Folgejahres angeboten bekommen — sonst schlägt der
 * Server den Abschluss mit "die Monate müssen lückenlos aufeinander folgen"
 * ab und niemand versteht warum.
 */
describe('naechsterOffenerMonat', () => {
    it('schlägt den Monat nach dem letzten Abschluss vor', () => {
        expect(naechsterOffenerMonat('2026-03')).toEqual({ jahr: 2026, monat: 4 });
        expect(naechsterOffenerMonat('2026-01')).toEqual({ jahr: 2026, monat: 2 });
    });

    it('springt über die Jahresgrenze', () => {
        expect(naechsterOffenerMonat('2026-12')).toEqual({ jahr: 2027, monat: 1 });
    });

    it('nimmt ohne bisherigen Abschluss den Vormonat', () => {
        expect(naechsterOffenerMonat(null, new Date(2026, 6, 15)))
            .toEqual({ jahr: 2026, monat: 6 });
    });

    it('rechnet auch über den Jahreswechsel zurück', () => {
        expect(naechsterOffenerMonat(null, new Date(2026, 0, 5)))
            .toEqual({ jahr: 2025, monat: 12 });
    });

    it('fällt bei unlesbarem Serverwert auf den Vormonat zurück', () => {
        expect(naechsterOffenerMonat('kaputt', new Date(2026, 6, 15)))
            .toEqual({ jahr: 2026, monat: 6 });
        expect(naechsterOffenerMonat('2026-13', new Date(2026, 6, 15)))
            .toEqual({ jahr: 2026, monat: 6 });
    });
});

describe('monatLabel', () => {
    it('schreibt den Monat aus', () => {
        expect(monatLabel(2026, 1)).toBe('Januar 2026');
        expect(monatLabel(2026, 3)).toBe('März 2026');
        expect(monatLabel(2026, 12)).toBe('Dezember 2026');
    });
});

describe('euro', () => {
    it('formatiert deutsch mit zwei Nachkommastellen', () => {
        expect(euro(1234.5)).toBe('1.234,50');
        expect(euro(0)).toBe('0,00');
        expect(euro(-20)).toBe('-20,00');
    });
});
