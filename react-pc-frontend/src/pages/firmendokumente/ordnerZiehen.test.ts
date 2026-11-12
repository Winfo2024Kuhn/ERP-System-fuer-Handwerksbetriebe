import { describe, expect, it } from 'vitest';
import { baueBaum } from '../../lib/ordnerBaum';
import { DOKUMENT_DATENTYP, schreibeGezogenesDokument } from './firmendokumenteLogik';
import { ablageArt, gezogeneDokumentHerkunft, gezogeneOrdnerId, ordnerDarfNach, schreibeGezogenenOrdner, ziehtOrdner } from './ordnerZiehen';
import type { FirmenOrdner } from './typen';

const o = (id: number, name: string, parentId: number | null, extra: Partial<FirmenOrdner> = {}): FirmenOrdner => ({
    id, name, parentId, anzahlDokumente: 0, anzahlUeberfaellig: 0, anzahlBaldFaellig: 0, darfAendern: true, eingeschraenkt: false, ...extra,
});

// Fotos Baustelle › Montag › Vormittag, Prüfungen, Geschäftsleitung (nur ansehen)
const BAUM = baueBaum([
    o(1, 'Fotos Baustelle Mustermann', null),
    o(2, 'Montag', 1),
    o(3, 'Vormittag', 2),
    o(4, 'Prüfungen', null),
    o(6, 'Geschäftsleitung', null, { darfAendern: false, eingeschraenkt: true }),
    o(7, 'Verträge', 6, { darfAendern: false, eingeschraenkt: true }),
]);

/** dataTransfer, wie ihn ein gezogener Ordner während des Ziehens zeigt (nur Typen lesbar). */
function gezogen(id: number): string[] {
    const typen: string[] = [];
    schreibeGezogenenOrdner({ setData: (typ) => { typen.push(typ); } }, { id, name: 'egal' });
    return typen;
}

describe('ordnerZiehen', () => {
    it('legt den Ordner mit eigenem Datentyp in den dataTransfer – die ID ist schon beim Ziehen lesbar', () => {
        const daten = new Map<string, string>();
        schreibeGezogenenOrdner({ setData: (typ, wert) => { daten.set(typ, wert); } }, { id: 2, name: 'Montag' });
        expect(daten.get('application/x-firmendokument-ordner')).toBe('2');
        expect(daten.get('text/plain')).toBe('Montag');
        const typen = [...daten.keys()];
        expect(ziehtOrdner({ dataTransfer: { types: typen } })).toBe(true);
        expect(gezogeneOrdnerId(typen)).toBe(2);
    });

    it('erkennt fremde oder kaputte Zieh-Daten nicht als Ordner', () => {
        expect(ziehtOrdner({ dataTransfer: { types: ['Files'] } })).toBe(false);
        expect(ziehtOrdner({ dataTransfer: { types: [DOKUMENT_DATENTYP] } })).toBe(false);
        expect(ziehtOrdner({ dataTransfer: null })).toBe(false);
        expect(gezogeneOrdnerId(['application/x-firmendokument-ordner-id-abc'])).toBeNull();
        expect(gezogeneOrdnerId(['application/x-firmendokument-ordner-id-1.5'])).toBeNull();
        expect(gezogeneOrdnerId(['text/plain'])).toBeNull();
    });

    it('lässt einen Ordner in einen anderen oder nach ganz oben, aber nicht in sich selbst, seine Unterordner oder dorthin, wo er schon liegt', () => {
        expect(ordnerDarfNach(BAUM, 2, 4)).toBe(true);
        expect(ordnerDarfNach(BAUM, 2, null)).toBe(true);
        expect(ordnerDarfNach(BAUM, 2, 2)).toBe(false);
        expect(ordnerDarfNach(BAUM, 2, 3)).toBe(false);
        expect(ordnerDarfNach(BAUM, 2, 1)).toBe(false);
        expect(ordnerDarfNach(BAUM, 1, null)).toBe(false);
        expect(ordnerDarfNach(BAUM, 99, 4)).toBe(false);
    });

    it('verlangt „Ändern“ am gezogenen Ordner und am Ziel', () => {
        expect(ordnerDarfNach(BAUM, 2, 6)).toBe(false);
        expect(ordnerDarfNach(BAUM, 7, 4)).toBe(false);
        expect(ordnerDarfNach(BAUM, 7, null)).toBe(false);
    });

    it('sagt, was ein Ziel gerade annimmt: Dateien, Dokumente oder Ordner – die Kopfzeile nur Ordner', () => {
        expect(ablageArt(BAUM, 4, ['Files'])).toBe('dateien');
        expect(ablageArt(BAUM, 4, [DOKUMENT_DATENTYP, 'text/plain'])).toBe('dokument');
        expect(ablageArt(BAUM, 4, gezogen(2))).toBe('ordner');
        expect(ablageArt(BAUM, 3, gezogen(2))).toBeNull();
        expect(ablageArt(BAUM, 6, ['Files'])).toBeNull();
        expect(ablageArt(BAUM, 6, [DOKUMENT_DATENTYP])).toBeNull();
        expect(ablageArt(BAUM, 4, ['text/plain'])).toBeNull();
        expect(ablageArt(BAUM, null, gezogen(2))).toBe('ordner');
        expect(ablageArt(BAUM, null, gezogen(1))).toBeNull();
        expect(ablageArt(BAUM, null, ['Files'])).toBeNull();
        expect(ablageArt(BAUM, null, [DOKUMENT_DATENTYP])).toBeNull();
    });

    it('verrät beim Ziehen eines Dokuments seinen Ordner – der eigene Ordner nimmt es gar nicht erst an', () => {
        const typen: string[] = [];
        schreibeGezogenesDokument({ setData: (typ) => { typen.push(typ); } }, { id: 10, ordnerId: 2, name: 'Foto', faelligAm: null });
        expect(gezogeneDokumentHerkunft(typen)).toBe(2);
        expect(gezogeneOrdnerId(typen)).toBeNull();
        expect(ziehtOrdner({ dataTransfer: { types: typen } })).toBe(false);
        expect(ablageArt(BAUM, 2, typen)).toBeNull();
        expect(ablageArt(BAUM, 4, typen)).toBe('dokument');
        expect(ablageArt(BAUM, 1, typen)).toBe('dokument');
        expect(ablageArt(BAUM, 6, typen)).toBeNull();
        expect(gezogeneDokumentHerkunft(['application/x-firmendokument-aus-ordner-x'])).toBeNull();
    });
});
