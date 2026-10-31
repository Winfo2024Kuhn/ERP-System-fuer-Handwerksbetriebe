import { describe, expect, it } from 'vitest';
import { kuerzelZuChipHtml } from './platzhalter';
import { NEUER_TEXT, entwurfAus, hatAenderungen, kopieVon, neuerEntwurf, speichernDaten } from './textbausteinEntwurf';
import { TEST_KATALOG, testBaustein } from './__tests__/testdaten';

describe('textbausteinEntwurf', () => {
    it('neuer Entwurf: leerer Name, Starttext, FREITEXT, Vorauswahl nur wenn übergeben', () => {
        expect(neuerEntwurf()).toEqual({
            id: null, name: '', html: NEUER_TEXT, dokumenttypen: [], typ: 'FREITEXT', beschreibung: null, sortOrder: null,
        });
        expect(neuerEntwurf('Rechnung').dokumenttypen).toEqual(['Rechnung']);
    });

    it('Kopie: neuer Datensatz mit „Kopie von“, Name auf 150 Zeichen begrenzt', () => {
        const kopie = kopieVon(testBaustein({ id: 4, name: 'Anschreiben', dokumenttypen: ['Angebot'] }));
        expect(kopie).toMatchObject({ id: null, name: 'Kopie von Anschreiben', dokumenttypen: ['Angebot'], typ: 'VORTEXT', sortOrder: null });
        expect(kopieVon(testBaustein({ id: 5, name: 'x'.repeat(150) })).name).toHaveLength(150);
    });

    it('speichernDaten: Chips werden Kürzel, typ/beschreibung/sortOrder bleiben, nur verwendete Kürzel', () => {
        const entwurf = entwurfAus(testBaustein({ id: 7, name: 'Alt', html: '<p>{{Kundenname}} {{Irgendwas}}</p>' }));
        const daten = speichernDaten(entwurf, {
            name: '  Neuer Name  ',
            dokumenttypen: ['Rechnung'],
            chipHtml: kuerzelZuChipHtml(entwurf.html, TEST_KATALOG),
        });
        expect(daten).toEqual({
            name: 'Neuer Name',
            typ: 'VORTEXT',
            beschreibung: 'Einleitung',
            sortOrder: 3,
            html: '<p>{{Kundenname}} {{Irgendwas}}</p>',
            placeholders: ['{{Kundenname}}', '{{Irgendwas}}'],
            dokumenttypen: ['Rechnung'],
        });
    });

    it('hatAenderungen: Name, Dokumentarten (Reihenfolge egal) und Text gegen den Editor-Stand', () => {
        const entwurf = entwurfAus(testBaustein({ id: 7, name: 'Alt', html: '<p>{{Kundenname}}</p>', dokumenttypen: ['Angebot', 'Rechnung'] }));
        const chipHtml = kuerzelZuChipHtml(entwurf.html, TEST_KATALOG);
        const vergleich = '<p>{{Kundenname}}</p>';
        const basis = { name: 'Alt', dokumenttypen: ['Rechnung', 'Angebot'], chipHtml };
        expect(hatAenderungen(entwurf, basis, vergleich)).toBe(false);
        expect(hatAenderungen(entwurf, { ...basis, name: 'Neu' }, vergleich)).toBe(true);
        expect(hatAenderungen(entwurf, { ...basis, dokumenttypen: ['Angebot'] }, vergleich)).toBe(true);
        expect(hatAenderungen(entwurf, { ...basis, dokumenttypen: ['Angebot', 'Gutschrift'] }, vergleich)).toBe(true);
        expect(hatAenderungen(entwurf, { ...basis, chipHtml: '<p>Anders</p>' }, vergleich)).toBe(true);
    });
});
