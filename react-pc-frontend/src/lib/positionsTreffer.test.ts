import { describe, expect, it } from 'vitest';
import {
    MAX_SUCHWOERTER,
    MIN_WORTLAENGE,
    MIN_ZEICHEN_POSITIONSSUCHE,
    hervorhebungsStuecke,
    normalisiere,
    positionsSucheAktiv,
    suchwoerter,
    trefferNachDokument,
    vereinigeTreffer,
    weitereTrefferText,
    type PositionsTreffer,
} from './positionsTreffer';

// DSGVO: nur Fantasie-Werte (Musterstahl GmbH, Dummy-Chargen)

describe('positionsSucheAktiv', () => {
    it('startet erst ab zwei Zeichen ohne Leerraum am Rand', () => {
        expect(MIN_ZEICHEN_POSITIONSSUCHE).toBe(2);
        expect(positionsSucheAktiv('')).toBe(false);
        expect(positionsSucheAktiv(null)).toBe(false);
        expect(positionsSucheAktiv(undefined)).toBe(false);
        expect(positionsSucheAktiv(' F ')).toBe(false);
        expect(positionsSucheAktiv('Fl')).toBe(true);
        expect(positionsSucheAktiv('  S235JR  ')).toBe(true);
    });
});

describe('suchwoerter', () => {
    it('trennt an Leerraum, schreibt klein und entfernt Doppelte', () => {
        expect(suchwoerter('  Flachstahl   S235JR flachstahl ')).toEqual(['flachstahl', 's235jr']);
    });

    it('nimmt höchstens fünf Wörter wie das Backend', () => {
        expect(MAX_SUCHWOERTER).toBe(5);
        expect(suchwoerter('aa bb cc dd ee ff gg')).toEqual(['aa', 'bb', 'cc', 'dd', 'ee']);
    });

    it('lässt Wörter unter zwei Zeichen weg', () => {
        expect(MIN_WORTLAENGE).toBe(2);
        expect(suchwoerter('a Flachstahl b')).toEqual(['flachstahl']);
    });

    it('normalisiert Maße wie das Backend', () => {
        expect(suchwoerter('Flachstahl 50 x 5')).toEqual(['flachstahl', '50x5']);
        expect(suchwoerter('50 × 5')).toEqual(['50x5']);
        expect(suchwoerter('50*5')).toEqual(['50x5']);
    });

    it('liefert für leere Eingaben nichts', () => {
        expect(suchwoerter('')).toEqual([]);
        expect(suchwoerter(null)).toEqual([]);
        expect(suchwoerter(undefined)).toEqual([]);
        expect(suchwoerter('   ')).toEqual([]);
    });
});

describe('normalisiere', () => {
    it('folgt den Regeln des Backends', () => {
        expect(normalisiere('Flachstahl 50 × 5')).toBe('flachstahl 50x5');
        expect(normalisiere('Blech 2000 x 1000 x 3')).toBe('blech 2000x1000x3');
        expect(normalisiere('Rohr 33,7*2,6')).toBe('rohr 33,7x2,6');
        expect(normalisiere('  Winkel\t\u00a0 50  X  50  ')).toBe('winkel 50x50');
        // „x“ ohne Ziffern auf beiden Seiten bleibt mit Leerraum stehen
        expect(normalisiere('Box x 5')).toBe('box x 5');
        expect(normalisiere('5 x Box')).toBe('5 x box');
        expect(normalisiere('x 5')).toBe('x 5');
        expect(normalisiere('5 x')).toBe('5 x');
    });

    it('kommt mit leeren und besonderen Eingaben zurecht', () => {
        expect(normalisiere('')).toBe('');
        expect(normalisiere(null)).toBe('');
        expect(normalisiere(undefined)).toBe('');
        expect(normalisiere('   ')).toBe('');
        // Längenändernde Kleinschreibung: einfacher Ersatz ohne Maßregeln
        expect(normalisiere(' İnox ')).toBe('i̇nox');
    });
});

describe('trefferNachDokument', () => {
    it('baut eine Tabelle je Dokument-ID', () => {
        const tabelle = trefferNachDokument([
            { dokumentId: 1, trefferText: 'Flachstahl 50x5 · S235JR · Charge 123456', weitereTreffer: 2 },
            { dokumentId: 2, trefferText: 'Rundrohr 33,7', weitereTreffer: 0 },
        ]);
        expect(tabelle.size).toBe(2);
        expect(tabelle.get(1)).toEqual({ dokumentId: 1, trefferText: 'Flachstahl 50x5 · S235JR · Charge 123456', weitereTreffer: 2 });
        expect(tabelle.get(2)?.weitereTreffer).toBe(0);
    });

    it('ist bei Unsinn aus dem Netz robust', () => {
        expect(trefferNachDokument(null).size).toBe(0);
        expect(trefferNachDokument({ dokumentId: 1 }).size).toBe(0);
        const tabelle = trefferNachDokument([
            null,
            'kaputt',
            { dokumentId: '3', trefferText: 'Text' },
            { dokumentId: Number.NaN, trefferText: 'Text' },
            { dokumentId: 4, trefferText: '   ' },
            { dokumentId: 5, trefferText: 42 },
            { dokumentId: 6, trefferText: 'Winkel 50x50x5' },
            { dokumentId: 7, trefferText: 'Blech 3 mm', weitereTreffer: -4 },
            { dokumentId: 8, trefferText: 'Blech 4 mm', weitereTreffer: 2.7 },
            { dokumentId: 9, trefferText: 'Blech 5 mm', weitereTreffer: '3' },
        ]);
        expect([...tabelle.keys()]).toEqual([6, 7, 8, 9]);
        expect(tabelle.get(6)?.weitereTreffer).toBe(0);
        expect(tabelle.get(7)?.weitereTreffer).toBe(0);
        expect(tabelle.get(8)?.weitereTreffer).toBe(2);
        expect(tabelle.get(9)?.weitereTreffer).toBe(0);
    });

    it('behält bei doppelter Dokument-ID den ersten Treffer', () => {
        const tabelle = trefferNachDokument([
            { dokumentId: 1, trefferText: 'erster', weitereTreffer: 0 },
            { dokumentId: 1, trefferText: 'zweiter', weitereTreffer: 0 },
        ]);
        expect(tabelle.get(1)?.trefferText).toBe('erster');
    });
});

describe('vereinigeTreffer', () => {
    const alle = [{ id: 1, nr: 'LS-1' }, { id: 2, nr: 'WZ-2' }, { id: 3, nr: 'RE-3' }, { id: 4, nr: 'AB-4' }];
    const treffer = (ids: number[]) => new Map<number, PositionsTreffer>(ids.map(id => [id, { dokumentId: id, trefferText: 'x', weitereTreffer: 0 }]));

    it('vereinigt Browser- und Positionstreffer ohne Doppelte, in der Reihenfolge der Liste', () => {
        const ergebnis = vereinigeTreffer(alle, d => d.nr.startsWith('RE') || d.nr.startsWith('LS'), treffer([2, 3]));
        expect(ergebnis.map(d => d.id)).toEqual([1, 2, 3]);
    });

    it('liefert nur Positionstreffer, wenn der Browser nichts findet', () => {
        expect(vereinigeTreffer(alle, () => false, treffer([4])).map(d => d.id)).toEqual([4]);
    });

    it('ignoriert Positionstreffer zu Dokumenten, die nicht in der Liste stehen (z. B. anderer Typ-Filter)', () => {
        expect(vereinigeTreffer(alle.slice(0, 2), () => false, treffer([3, 99]))).toEqual([]);
    });
});

describe('weitereTrefferText', () => {
    it('beschriftet weitere Treffer', () => {
        expect(weitereTrefferText(1)).toBe('und 1 weitere');
        expect(weitereTrefferText(3)).toBe('und 3 weitere');
        expect(weitereTrefferText(2.9)).toBe('und 2 weitere');
    });

    it('bleibt ohne weitere Treffer leer', () => {
        expect(weitereTrefferText(0)).toBe('');
        expect(weitereTrefferText(-1)).toBe('');
        expect(weitereTrefferText(null)).toBe('');
        expect(weitereTrefferText(undefined)).toBe('');
        expect(weitereTrefferText(Number.POSITIVE_INFINITY)).toBe('');
    });
});

describe('hervorhebungsStuecke', () => {
    it('markiert den Suchbegriff unabhängig von Groß- und Kleinschreibung', () => {
        expect(hervorhebungsStuecke('Flachstahl 50x5 · S235JR', 'flachSTAHL')).toEqual([
            { text: 'Flachstahl', treffer: true },
            { text: ' 50x5 · S235JR', treffer: false },
        ]);
    });

    it('markiert mehrere Wörter und mehrere Fundstellen', () => {
        expect(hervorhebungsStuecke('Stahl S235JR, Stahl S355', 'stahl s355')).toEqual([
            { text: 'Stahl', treffer: true },
            { text: ' S235JR, ', treffer: false },
            { text: 'Stahl', treffer: true },
            { text: ' ', treffer: false },
            { text: 'S355', treffer: true },
        ]);
    });

    it('legt überlappende und aneinanderstoßende Fundstellen zusammen', () => {
        expect(hervorhebungsStuecke('Charge 123456', '1234 3456')).toEqual([
            { text: 'Charge ', treffer: false },
            { text: '123456', treffer: true },
        ]);
        expect(hervorhebungsStuecke('abcdef', 'abc def')).toEqual([{ text: 'abcdef', treffer: true }]);
        // Ein Wort, das ganz in einem anderen steckt, verlängert nichts
        expect(hervorhebungsStuecke('Flachstahl', 'flachstahl stahl')).toEqual([{ text: 'Flachstahl', treffer: true }]);
        // Gleicher Anfang, verschieden lang: das längere Wort gewinnt
        expect(hervorhebungsStuecke('Stahlträger', 'stahl sta')).toEqual([
            { text: 'Stahl', treffer: true },
            { text: 'träger', treffer: false },
        ]);
    });

    it('lässt den Text ohne Fundstelle oder Suchbegriff unverändert', () => {
        expect(hervorhebungsStuecke('Rundrohr', 'Flachstahl')).toEqual([{ text: 'Rundrohr', treffer: false }]);
        expect(hervorhebungsStuecke('Rundrohr', '')).toEqual([{ text: 'Rundrohr', treffer: false }]);
        expect(hervorhebungsStuecke('Rundrohr', null)).toEqual([{ text: 'Rundrohr', treffer: false }]);
    });

    it('markiert Maße unabhängig von Schreibweise und Leerraum', () => {
        expect(hervorhebungsStuecke('Flachstahl 50 × 5 · S235JR', '50x5')).toEqual([
            { text: 'Flachstahl ', treffer: false },
            { text: '50 × 5', treffer: true },
            { text: ' · S235JR', treffer: false },
        ]);
        expect(hervorhebungsStuecke('Flachstahl 50 x 5', '50 X 5')).toEqual([
            { text: 'Flachstahl ', treffer: false },
            { text: '50 x 5', treffer: true },
        ]);
        expect(hervorhebungsStuecke('Flachstahl 50x5', '50 × 5')).toEqual([
            { text: 'Flachstahl ', treffer: false },
            { text: '50x5', treffer: true },
        ]);
    });

    it('lässt Leerraum am Rand und doppelten Leerraum stehen', () => {
        expect(hervorhebungsStuecke('  Flach   stahl  ', 'flach stahl')).toEqual([
            { text: '  ', treffer: false },
            { text: 'Flach', treffer: true },
            { text: '   ', treffer: false },
            { text: 'stahl', treffer: true },
            { text: '  ', treffer: false },
        ]);
    });

    it('liefert für leeren Text keine Stücke', () => {
        expect(hervorhebungsStuecke('', 'stahl')).toEqual([]);
    });

    it('markiert nichts, wenn Kleinschreiben die Länge ändert', () => {
        // „İ“ wird klein zu zwei Zeichen – die Positionen passten nicht mehr
        expect(hervorhebungsStuecke('İnox Stahl', 'stahl')).toEqual([{ text: 'İnox Stahl', treffer: false }]);
    });

    it('übernimmt Sonderzeichen wörtlich (kein Regex, kein HTML)', () => {
        expect(hervorhebungsStuecke('<b>50*5</b>', '50*5')).toEqual([
            { text: '<b>', treffer: false },
            { text: '50*5', treffer: true },
            { text: '</b>', treffer: false },
        ]);
    });
});
