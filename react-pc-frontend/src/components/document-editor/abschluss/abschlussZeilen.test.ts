/**
 * Eigene Texte und ausgeblendete Zeilen im Abrechnungsstand.
 * DSGVO: nur Dummy-Texte.
 */
import { describe, it, expect } from 'vitest';
import {
    BEZEICHNUNG_MAX,
    UNTERZEILE_MAX,
    aenderungsBezeichnung,
    abschlussZeilenSpeicherwert,
    istTextAngepasst,
    normalisiereAbschlussZeilen,
    normalisiereZeile,
    pdfAbschlussZeilen,
    pdfZeile,
    setzeZeile,
} from './abschlussZeilen';

describe('normalisiereZeile', () => {
    it('kuerzt, ersetzt Zeilenumbrueche und laesst leere Texte weg', () => {
        expect(normalisiereZeile({ bezeichnung: '  Rechnung\nTreppe  ', unterzeile: '   ' }))
            .toEqual({ bezeichnung: 'Rechnung Treppe' });
        expect(normalisiereZeile({ bezeichnung: 'x'.repeat(100), unterzeile: 'y'.repeat(300) }))
            .toEqual({ bezeichnung: `${'x'.repeat(BEZEICHNUNG_MAX - 1)}…`, unterzeile: `${'y'.repeat(UNTERZEILE_MAX - 1)}…` });
    });

    it('macht Steuer- und Formatzeichen zu Leerzeichen wie das Backend', () => {
        expect(normalisiereZeile({ bezeichnung: 'A\tB\u0007C\u200bD\u2028E\u2029F' }))
            .toEqual({ bezeichnung: 'A B C D E F' });
    });

    it('kuerzt nach Zeichen (Codepoints) und haengt … an', () => {
        const lang = '😀'.repeat(100);
        const gekuerzt = normalisiereZeile({ bezeichnung: lang })!.bezeichnung!;
        expect(Array.from(gekuerzt)).toHaveLength(BEZEICHNUNG_MAX);
        expect(gekuerzt.endsWith('😀…')).toBe(true);
        expect(normalisiereZeile({ bezeichnung: 'x'.repeat(BEZEICHNUNG_MAX) })!.bezeichnung).toBe('x'.repeat(BEZEICHNUNG_MAX));
    });

    it('merkt sich nur ein echtes Ausblenden', () => {
        expect(normalisiereZeile({ ausgeblendet: true })).toEqual({ ausgeblendet: true });
        expect(normalisiereZeile({ ausgeblendet: false })).toBeUndefined();
        expect(normalisiereZeile({ ausgeblendet: 'ja' })).toBeUndefined();
    });

    it('verwirft alles, was kein Objekt ist', () => {
        expect(normalisiereZeile(null)).toBeUndefined();
        expect(normalisiereZeile('Text')).toBeUndefined();
        expect(normalisiereZeile({ bezeichnung: 42 })).toBeUndefined();
    });
});

describe('normalisiereAbschlussZeilen (Laden aus positionenJson)', () => {
    it('liefert fuer alte Rechnungen ohne Schluessel ein leeres Objekt', () => {
        expect(normalisiereAbschlussZeilen(undefined)).toEqual({});
        expect(normalisiereAbschlussZeilen(null)).toEqual({});
        expect(normalisiereAbschlussZeilen([1, 2])).toEqual({});
    });

    it('uebernimmt beide Zeilen und ignoriert fremde Schluessel', () => {
        expect(normalisiereAbschlussZeilen({
            dieseRechnung: { bezeichnung: 'Rechnung Treppengeländer' },
            differenz: { unterzeile: 'Glasfüllung statt Lochblech', ausgeblendet: true },
            fremd: { bezeichnung: 'x' },
        })).toEqual({
            dieseRechnung: { bezeichnung: 'Rechnung Treppengeländer' },
            differenz: { unterzeile: 'Glasfüllung statt Lochblech', ausgeblendet: true },
        });
    });
});

describe('abschlussZeilenSpeicherwert', () => {
    it('speichert nichts, solange alles automatisch ist', () => {
        expect(abschlussZeilenSpeicherwert({})).toBeUndefined();
        expect(abschlussZeilenSpeicherwert({ differenz: { bezeichnung: ' ' } })).toBeUndefined();
    });

    it('speichert nur nicht-leere Anpassungen', () => {
        expect(abschlussZeilenSpeicherwert({ dieseRechnung: { ausgeblendet: true }, differenz: {} }))
            .toEqual({ dieseRechnung: { ausgeblendet: true } });
    });
});

describe('pdfZeile', () => {
    it('schickt null, wenn der Nutzer nichts geaendert hat', () => {
        expect(pdfZeile(undefined)).toBeNull();
        expect(pdfZeile({})).toBeNull();
    });

    it('schickt leere Texte als null und ausblenden immer als boolean', () => {
        expect(pdfZeile({ ausgeblendet: true }))
            .toEqual({ bezeichnung: null, unterzeile: null, ausblenden: true });
        expect(pdfZeile({ bezeichnung: 'Mehrleistung', unterzeile: 'Glasfüllung' }))
            .toEqual({ bezeichnung: 'Mehrleistung', unterzeile: 'Glasfüllung', ausblenden: false });
    });
});

describe('pdfAbschlussZeilen', () => {
    const zeilen = { dieseRechnung: { bezeichnung: 'Rechnung Treppe' }, differenz: { ausgeblendet: true } };

    it('schickt die Anpassungen, wenn der Abrechnungsstand sichtbar ist', () => {
        expect(pdfAbschlussZeilen(zeilen, true)).toEqual({
            dieseRechnungZeile: { bezeichnung: 'Rechnung Treppe', unterzeile: null, ausblenden: false },
            differenzZeile: { bezeichnung: null, unterzeile: null, ausblenden: true },
        });
    });

    it('schickt nichts, wenn der Editor den Abrechnungsstand nicht zeigt', () => {
        expect(pdfAbschlussZeilen(zeilen, false)).toEqual({ dieseRechnungZeile: null, differenzZeile: null });
    });
});

describe('setzeZeile', () => {
    it('ersetzt eine Zeile und laesst die andere stehen', () => {
        const vorher = { dieseRechnung: { bezeichnung: 'A' }, differenz: { ausgeblendet: true } };
        expect(setzeZeile(vorher, 'dieseRechnung', { bezeichnung: 'B' }))
            .toEqual({ dieseRechnung: { bezeichnung: 'B' }, differenz: { ausgeblendet: true } });
    });

    it('entfernt eine Zeile, die wieder ganz automatisch ist', () => {
        expect(setzeZeile({ differenz: { ausgeblendet: true } }, 'differenz', { ausgeblendet: false }))
            .toEqual({});
        expect(setzeZeile({ differenz: { bezeichnung: 'x' } }, 'differenz', undefined)).toEqual({});
    });
});

describe('istTextAngepasst', () => {
    it('zaehlt nur eigene Texte, nicht das Ausblenden', () => {
        expect(istTextAngepasst(undefined)).toBe(false);
        expect(istTextAngepasst({ ausgeblendet: true })).toBe(false);
        expect(istTextAngepasst({ unterzeile: 'x' })).toBe(true);
    });
});

describe('aenderungsBezeichnung (Eintrag in Rueckgaengig-Liste)', () => {
    it('benennt Ausblenden, Einblenden und Textaenderung', () => {
        expect(aenderungsBezeichnung(undefined, { ausgeblendet: true })).toBe('Zeile ausgeblendet');
        expect(aenderungsBezeichnung({ ausgeblendet: true }, undefined)).toBe('Zeile eingeblendet');
        expect(aenderungsBezeichnung(undefined, { bezeichnung: 'x' })).toBe('Zeilentext geändert');
        expect(aenderungsBezeichnung({ bezeichnung: 'x' }, undefined)).toBe('Zeilentext geändert');
    });
});
