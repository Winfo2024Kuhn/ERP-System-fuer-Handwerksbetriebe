import { describe, expect, it } from 'vitest';
import { istGueltigerTab, LIEFERANT_TABS, mitInfoAdresse } from './lieferantHilfen';

describe('istGueltigerTab', () => {
    it('kennt den Reiter Ansprechpartner direkt nach den E-Mails', () => {
        expect(LIEFERANT_TABS.slice(0, 3)).toEqual(['emails', 'ansprechpartner', 'anrufe']);
        expect(istGueltigerTab('ansprechpartner')).toBe(true);
    });

    it('lehnt Unbekanntes und fehlende Werte ab', () => {
        expect(istGueltigerTab('quatsch')).toBe(false);
        expect(istGueltigerTab(null)).toBe(false);
    });
});

describe('mitInfoAdresse', () => {
    it('hängt die info@-Adresse derselben Domain hinter die neue Adresse', () => {
        expect(mitInfoAdresse([], 'bestellung@muster.de')).toEqual(['bestellung@muster.de', 'info@muster.de']);
    });

    it('fügt nichts doppelt ein und ignoriert leere Eingaben', () => {
        expect(mitInfoAdresse(['info@muster.de'], 'INFO@muster.de')).toEqual(['info@muster.de']);
        expect(mitInfoAdresse(['a@muster.de'], '   ')).toEqual(['a@muster.de']);
        expect(mitInfoAdresse(undefined, '')).toEqual([]);
    });
});
