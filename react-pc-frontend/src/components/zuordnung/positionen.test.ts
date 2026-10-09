import { describe, expect, it } from 'vitest';
import { freierFarbIndex, zielFarbe } from './zielFarben';
import {
    ablageId,
    baueAufteilung,
    gezogenePositionen,
    zielAusAblage,
    formatEuro,
    formatMenge,
    formatProzent,
    istWare,
    offeneWarenpositionen,
    zieleAusPositionen,
    zielAusSchluessel,
    zielSchluessel,
    zuweisungenAusPositionen,
    type DokumentPosition,
} from './positionen';

const position = (id: number, art: DokumentPosition['positionsArt'], ziel: Partial<DokumentPosition> = {}): DokumentPosition => ({
    id,
    positionNr: id,
    positionsArt: art,
    externeArtikelnummer: null,
    bezeichnung: `Artikel ${id}`,
    menge: 1,
    mengeneinheit: 'Stk',
    einzelpreis: 10,
    preiseinheit: null,
    gesamtpreisNetto: 10,
    projektId: null,
    projektName: null,
    kostenstelleId: null,
    kostenstelleName: null,
    ...ziel,
});

describe('positionen – Ziel-Schlüssel', () => {
    it('bildet Schlüssel für Projekt, Kostenstelle und kein Ziel', () => {
        expect(zielSchluessel({ projektId: 5 })).toBe('p:5');
        expect(zielSchluessel({ kostenstelleId: 7 })).toBe('k:7');
        expect(zielSchluessel({})).toBe('');
        expect(zielSchluessel({ projektId: null, kostenstelleId: null })).toBe('');
    });

    it('liest Schlüssel zurück und verwirft Unbekanntes', () => {
        expect(zielAusSchluessel('p:5')).toEqual({ projektId: 5 });
        expect(zielAusSchluessel('k:7')).toEqual({ kostenstelleId: 7 });
        expect(zielAusSchluessel('')).toEqual({});
        expect(zielAusSchluessel('x:1')).toEqual({});
        expect(zielAusSchluessel("p:1'; DROP TABLE x; --")).toEqual({});
    });
});

describe('positionen – Zuweisungen', () => {
    const positionen = [
        position(1, 'WARE', { projektId: 10, projektName: 'Halle Mustermann' }),
        position(2, 'WARE', { kostenstelleId: 3, kostenstelleName: 'Werkstatt' }),
        position(3, 'WARE'),
        position(4, 'NEBENKOSTEN', { projektId: 10 }),
        position(5, 'WARE', { projektId: 10, projektName: 'Halle Mustermann' }),
    ];

    it('erkennt Warenpositionen', () => {
        expect(istWare(positionen[0])).toBe(true);
        expect(istWare(positionen[3])).toBe(false);
    });

    it('übernimmt gespeicherte Ziele nur von Warenpositionen', () => {
        expect(zuweisungenAusPositionen(positionen)).toEqual({ 1: 'p:10', 2: 'k:3', 5: 'p:10' });
    });

    it('zählt offene Warenpositionen', () => {
        expect(offeneWarenpositionen(positionen, { 1: 'p:10' }).map(p => p.id)).toEqual([2, 3, 5]);
    });

    it('baut die Aufteilung nur aus Warenpositionen', () => {
        expect(baueAufteilung(positionen, { 1: 'p:10', 2: 'k:3' })).toEqual([
            { positionId: 1, projektId: 10 },
            { positionId: 2, kostenstelleId: 3 },
            { positionId: 3 },
            { positionId: 5 },
        ]);
    });

    it('liefert jedes Ziel der Positionen genau einmal', () => {
        expect(zieleAusPositionen(positionen)).toEqual([
            { projektId: 10, projektName: 'Halle Mustermann' },
            { kostenstelleId: 3, kostenstelleName: 'Werkstatt' },
        ]);
    });

    it('ergänzt fehlende Namen', () => {
        expect(zieleAusPositionen([position(1, 'WARE', { projektId: 4 }), position(2, 'WARE', { kostenstelleId: 9 })])).toEqual([
            { projektId: 4, projektName: 'Projekt 4' },
            { kostenstelleId: 9, kostenstelleName: 'Kostenstelle 9' },
        ]);
    });
});

describe('positionen – Formatierung', () => {
    it('formatiert Euro, Menge und Prozent deutsch', () => {
        expect(formatEuro(1234.5)).toBe('1.234,50');
        expect(formatEuro(null)).toBe('–');
        expect(formatEuro(Number.NaN)).toBe('–');
        expect(formatMenge(2.5)).toBe('2,5');
        expect(formatMenge(1000)).toBe('1.000');
        expect(formatMenge(undefined)).toBe('–');
        expect(formatProzent(33.333)).toBe('33,3');
        expect(formatProzent(null)).toBe('–');
    });
});

describe('positionen – Ziehen und Ablegen', () => {
    const positionen = [position(1, 'WARE'), position(2, 'WARE'), position(3, 'NEBENKOSTEN'), position(4, 'WARE')];

    it('bildet Ablage-IDs und liest sie zurück', () => {
        expect(ablageId('p:5')).toBe('ablage:p:5');
        expect(zielAusAblage(ablageId('p:5'))).toBe('p:5');
        expect(zielAusAblage(ablageId('k:2'))).toBe('k:2');
        expect(zielAusAblage(ablageId(''))).toBe('');
    });

    it('verwirft fremde oder kaputte Ablage-IDs', () => {
        expect(zielAusAblage(null)).toBeNull();
        expect(zielAusAblage(undefined)).toBeNull();
        expect(zielAusAblage(7)).toBeNull();
        expect(zielAusAblage('position:1')).toBeNull();
        expect(zielAusAblage('ablage:x:1')).toBeNull();
        expect(zielAusAblage('ablage:<script>alert(1)</script>')).toBeNull();
    });

    it('zieht markierte Warenpositionen gemeinsam, in Listenreihenfolge', () => {
        expect(gezogenePositionen(4, new Set([4, 1, 3]), positionen)).toEqual([1, 4]);
    });

    it('zieht eine nicht markierte Position allein', () => {
        expect(gezogenePositionen(2, new Set([1, 4]), positionen)).toEqual([2]);
    });

    it('zieht keine Nebenkosten und keine unbekannten Positionen', () => {
        expect(gezogenePositionen(3, new Set(), positionen)).toEqual([]);
        expect(gezogenePositionen(99, new Set(), positionen)).toEqual([]);
    });
});

describe('zielFarben', () => {
    it('gibt jedem der ersten sieben Ziele eine eigene Farbe und wiederholt danach', () => {
        const punkte = Array.from({ length: 7 }, (_, i) => zielFarbe(i).punkt);
        expect(new Set(punkte).size).toBe(7);
        expect(zielFarbe(7)).toEqual(zielFarbe(0));
        expect(zielFarbe(-1)).toEqual(zielFarbe(6));
        // keine Markenfarbe, keine Warnfarbe, nichts Blaues
        expect(punkte.some(p => /rose|amber|blue|indigo|violet|purple/.test(p))).toBe(false);
    });

    it('vergibt die kleinste noch freie Farbe', () => {
        expect(freierFarbIndex([])).toBe(0);
        expect(freierFarbIndex([0, 1, 2])).toBe(3);
        expect(freierFarbIndex([1, 2])).toBe(0);
        expect(freierFarbIndex([0, 2, undefined])).toBe(1);
    });
});
