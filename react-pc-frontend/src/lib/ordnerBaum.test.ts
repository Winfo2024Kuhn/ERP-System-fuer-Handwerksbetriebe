import { describe, expect, it } from 'vitest';
import {
    alleInReihenfolge, baueBaum, hatUnterordner, kinderVon, nachfahren, nachName, ordnerKette, passtZurSuche, pfadText,
    sichtbareZeilen, type BaumOrdner,
} from './ordnerBaum';

interface TestOrdner extends BaumOrdner {
    extra: string;
}

const o = (id: number, name: string, parentId: number | null): TestOrdner => ({ id, name, parentId, extra: `x${id}` });

// Prüfungen › Tore › Halle 2, Prüfungen › Leitern, Arbeitsschutz, verwaist (Eltern 99 fehlt)
const ORDNER = [
    o(3, 'Tore', 1),
    o(1, 'Prüfungen', null),
    o(4, 'leitern', 1),
    o(2, 'Arbeitsschutz', null),
    o(5, 'Halle 2', 3),
    o(6, 'Verwaist', 99),
];

describe('baueBaum', () => {
    it('sortiert Kinder nach Name ohne Groß/Klein und behält eigene Felder', () => {
        const baum = baueBaum(ORDNER);
        expect(kinderVon(baum, null).map((x) => x.name)).toEqual(['Arbeitsschutz', 'Prüfungen', 'Verwaist']);
        expect(kinderVon(baum, 1).map((x) => x.name)).toEqual(['leitern', 'Tore']);
        expect(baum.byId.get(5)?.extra).toBe('x5');
    });

    it('zeigt Ordner mit unbekanntem Eltern-Ordner oben', () => {
        const baum = baueBaum(ORDNER);
        expect(kinderVon(baum, null).some((x) => x.id === 6)).toBe(true);
        expect(kinderVon(baum, 99)).toEqual([]);
    });

    it('kommt mit einer leeren Liste aus', () => {
        const baum = baueBaum([]);
        expect(kinderVon(baum, null)).toEqual([]);
        expect(sichtbareZeilen(baum, new Set())).toEqual([]);
    });
});

describe('Beziehungen', () => {
    const baum = baueBaum(ORDNER);

    it('erkennt Unterordner', () => {
        expect(hatUnterordner(baum, 1)).toBe(true);
        expect(hatUnterordner(baum, 4)).toBe(false);
    });

    it('findet alle Nachfahren, ohne den Ordner selbst', () => {
        expect([...nachfahren(baum, 1)].sort()).toEqual([3, 4, 5]);
        expect(nachfahren(baum, 5).size).toBe(0);
    });

    it('übersteht Zyklen in kaputten Daten', () => {
        const kaputt = baueBaum([o(1, 'A', 2), o(2, 'B', 1)]);
        // Beide haben einen bekannten Eltern-Ordner – keiner ist oben, aber nichts hängt sich auf.
        expect([...nachfahren(kaputt, 1)].sort()).toEqual([2]);
        expect(ordnerKette(kaputt, 1).map((x) => x.id)).toEqual([2, 1]);
    });

    it('liefert Kette und Pfad vom Hauptordner bis zum Ordner', () => {
        expect(ordnerKette(baum, 5).map((x) => x.name)).toEqual(['Prüfungen', 'Tore', 'Halle 2']);
        expect(pfadText(baum, 5)).toBe('Prüfungen › Tore › Halle 2');
        expect(pfadText(baum, 5, ' / ')).toBe('Prüfungen / Tore / Halle 2');
        expect(pfadText(baum, 404)).toBe('');
    });

    it('sortiert per nachName wie im deutschen Alphabet', () => {
        expect([{ name: 'Ö' }, { name: 'a' }, { name: 'Z' }].sort(nachName).map((x) => x.name)).toEqual(['a', 'Ö', 'Z']);
    });
});

describe('sichtbareZeilen', () => {
    const baum = baueBaum(ORDNER);

    it('zeigt nur aufgeklappte Äste mit Ebene und Kinder-Hinweis', () => {
        const zeilen = sichtbareZeilen(baum, new Set([1]));
        expect(zeilen.map((z) => [z.ordner.name, z.ebene, z.hatKinder, z.aufgeklappt])).toEqual([
            ['Arbeitsschutz', 0, false, false],
            ['Prüfungen', 0, true, true],
            ['leitern', 1, false, false],
            ['Tore', 1, true, false],
            ['Verwaist', 0, false, false],
        ]);
    });

    it('beachtet einen Filter auch bei den Kindern', () => {
        const zeilen = sichtbareZeilen(baum, new Set([1, 3]), new Set([1, 3]));
        expect(zeilen.map((z) => [z.ordner.name, z.hatKinder, z.aufgeklappt])).toEqual([
            ['Prüfungen', true, true],
            ['Tore', false, false],
        ]);
    });

    it('zeigt jeden Ordner höchstens einmal', () => {
        const doppelt = baueBaum([o(1, 'A', null), o(1, 'A', null)]);
        expect(sichtbareZeilen(doppelt, new Set())).toHaveLength(1);
    });

    it('listet alle Ordner in Baum-Reihenfolge mit Einrückung', () => {
        expect(alleInReihenfolge(baum).map((z) => [z.ordner.name, z.ebene])).toEqual([
            ['Arbeitsschutz', 0], ['Prüfungen', 0], ['leitern', 1], ['Tore', 1], ['Halle 2', 2], ['Verwaist', 0],
        ]);
    });
});

describe('passtZurSuche', () => {
    it('vergleicht ohne Groß/Klein und ignoriert Leerzeichen am Rand', () => {
        expect(passtZurSuche('Torprüfung Halle 2', '  TOR ')).toBe(true);
        expect(passtZurSuche('Torprüfung', 'Leiter')).toBe(false);
    });
});
