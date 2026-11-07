import { baueBaum, leistungsZaehler } from './ordnerLogik';
import type { Leistung, Ordner } from './typen';

/** Gemeinsame Dummy-Daten für die Hook-Tests der Seite „Leistungen & Kategorien“. */

export function testOrdner(id: number, name: string, parentId: number | null, extra: Partial<Ordner> = {}): Ordner {
    return {
        id, name, parentId, bildUrl: null, beschreibung: '', einheit: 'STUECK', einheitAnzeige: 'Stück',
        projektAnzahl: 0, ...extra,
    };
}

export function testLeistung(id: number, name: string, ordnerId: number | null): Leistung {
    return { id, name, beschreibung: '', preis: 10, einheit: 'STUECK', ordnerId };
}

/** Metallbau › Geländer (2 Leistungen) ; Holzbau (leer). */
export const TEST_ORDNER = [
    testOrdner(1, 'Metallbau', null),
    testOrdner(2, 'Geländer', 1),
    testOrdner(4, 'Holzbau', null),
];
export const TEST_LEISTUNGEN = [testLeistung(10, 'Geländer verzinkt', 2), testLeistung(11, 'Glasbrüstung', 2)];

export function testBaum(ordner = TEST_ORDNER, leistungen = TEST_LEISTUNGEN) {
    const baum = baueBaum(ordner);
    return { baum, zaehler: leistungsZaehler(baum, leistungen), leistungen };
}
