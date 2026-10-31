/**
 * Rechenhilfen für den Warenkorb aus dem Lieferanten-Shop.
 * DSGVO: nur Dummy-Artikel, keine Personendaten.
 */
import { describe, expect, it } from 'vitest';
import type { IdsPosition } from './api';
import {
    addiereStammErgebnisse,
    artikelVorbelegungAus,
    artikelstammAuftraegeJeBesuch,
    SHOP_ANLEGEN_HINWEIS,
    verrechnungseinheitAus,
    artikelstammErgebnisText,
    aufschlagQuelleText,
    baueEinzelBloecke,
    baueSammelBlock,
    berechneSummen,
    entferneZeile,
    formatMenge,
    haengeZeilenAn,
    gueltigeZeilen,
    kundentextEinzeln,
    pruefeZeilen,
    sammelListeText,
    stammAktionFuer,
    stelleZeileWiederHer,
    verkaufspreis,
    weitereArtikelText,
    zahlAusEntwurf,
    zeilenEntwurfAus,
    zeilenSchluessel,
    zuEntwurf,
    type WarenkorbZeile,
    type ZeilenEntwurf,
} from './warenkorbPositionen';

const position = (teil: Partial<IdsPosition> = {}): IdsPosition => ({
    index: 0,
    artikelnummer: '0892 100',
    bezeichnung: 'Silikon-Kartusche',
    langtext: null,
    menge: 5,
    einheit: 'Stk',
    einkaufspreis: 4.2,
    hinweis: null,
    aufschlagProzent: 25,
    aufschlagQuelle: 'ARTIKEL',
    stamm: null,
    ...teil,
});

const entwurf = (teil: Partial<ZeilenEntwurf> = {}): ZeilenEntwurf => ({
    vorgangId: 42,
    index: 0,
    artikelnummer: '0892 100',
    bezeichnung: 'Silikon-Kartusche',
    menge: '5',
    einheit: 'Stk',
    einkaufspreis: '4,2',
    aufschlag: '25',
    artikelId: null,
    langtext: null,
    ...teil,
});

const zeile = (teil: Partial<WarenkorbZeile> = {}): WarenkorbZeile => ({
    vorgangId: 42,
    index: 0,
    artikelnummer: '0892 100',
    bezeichnung: 'Silikon-Kartusche',
    menge: 5,
    einheit: 'Stk',
    einkaufspreis: 4.2,
    aufschlagProzent: 25,
    artikelId: null,
    langtext: null,
    ...teil,
});

const ids = () => {
    let n = 0;
    return () => `id-${++n}`;
};

describe('verkaufspreis', () => {
    it('rechnet VK = EK × (1 + Aufschlag/100)', () => {
        expect(verkaufspreis(4.2, 25)).toBe(5.25);
        expect(verkaufspreis(10, 0)).toBe(10);
        expect(verkaufspreis(3.33, 33.3)).toBe(4.44);
        expect(verkaufspreis(10, -10)).toBe(9);
        // kaufmännisch: 0,804 × 1,25 = 1,005 → 1,01
        expect(verkaufspreis(0.804, 25)).toBe(1.01);
    });
});

describe('formatMenge / aufschlagQuelleText / zuEntwurf', () => {
    it('schreibt Mengen deutsch ohne unnötige Nullen', () => {
        expect(formatMenge(5)).toBe('5');
        expect(formatMenge(2.5)).toBe('2,5');
        expect(formatMenge(1000)).toBe('1.000');
        expect(formatMenge(0.3333)).toBe('0,333');
    });

    it('benennt die Herkunft des Aufschlags', () => {
        expect(aufschlagQuelleText('ARTIKEL')).toBe('aus Artikelstamm');
        expect(aufschlagQuelleText('KATEGORIE')).toBe('aus Kategorie');
        expect(aufschlagQuelleText('ALLGEMEIN')).toBe('Standard');
        expect(aufschlagQuelleText('KEINER')).toBe('kein Aufschlag hinterlegt');
    });

    it('macht aus Zahlen Eingabe-Entwürfe', () => {
        expect(zuEntwurf(12.5)).toBe('12,5');
        expect(zuEntwurf(null)).toBe('');
        expect(zuEntwurf(undefined)).toBe('');
        expect(zuEntwurf(Number.NaN)).toBe('');
    });
});

describe('zeilenEntwurfAus / zahlAusEntwurf', () => {
    it('übernimmt die Werte des Servers', () => {
        expect(zeilenEntwurfAus(position({ stamm: { artikelId: 9, bezeichnung: 'Silikon', aktuellerPreis: 4 } }), 42)).toEqual({
            vorgangId: 42, index: 0, artikelnummer: '0892 100', bezeichnung: 'Silikon-Kartusche', menge: '5', einheit: 'Stk',
            einkaufspreis: '4,2', aufschlag: '25', artikelId: 9, langtext: null,
        });
    });

    it('lässt einen fehlenden Preis leer und setzt einen fehlenden Aufschlag auf 0', () => {
        const z = zeilenEntwurfAus(position({ einkaufspreis: null, aufschlagProzent: null }), 42);
        expect(z.einkaufspreis).toBe('');
        expect(z.aufschlag).toBe('0');
        expect(z.artikelId).toBeNull();
    });

    it('liest nur vollständige Zahlen', () => {
        expect(zahlAusEntwurf('12,5')).toBe(12.5);
        expect(zahlAusEntwurf('')).toBeNull();
        expect(zahlAusEntwurf('12,')).toBeNull();
    });
});

describe('pruefeZeilen', () => {
    it('liefert geprüfte Zahlen samt Herkunft für alle Zeilen der Liste', () => {
        const ergebnis = pruefeZeilen([
            entwurf({ bezeichnung: '  Silikon  ', artikelId: 3 }),
            entwurf({ vorgangId: 43, index: 0 }),
        ]);
        expect(ergebnis).toEqual({ gueltig: true, zeilen: [zeile({ bezeichnung: 'Silikon', artikelId: 3 }), zeile({ vorgangId: 43 })] });
    });

    it('verlangt mindestens eine Zeile in der Liste', () => {
        expect(pruefeZeilen([])).toEqual({
            gueltig: false, meldung: 'Keine Artikel in der Liste – bitte erst Artikel aus dem Shop holen.',
        });
    });

    it('meldet fehlende Bezeichnung, Menge, Preis und Aufschlag verständlich', () => {
        expect(pruefeZeilen([entwurf({ bezeichnung: ' ' })])).toEqual({ gueltig: false, meldung: 'Bitte für Artikel 0892 100 eine Bezeichnung eintragen.' });
        expect(pruefeZeilen([entwurf({ menge: '' })])).toEqual({ gueltig: false, meldung: 'Bitte die Menge bei „Silikon-Kartusche“ eingeben.' });
        expect(pruefeZeilen([entwurf({ menge: '0' })])).toEqual({ gueltig: false, meldung: 'Die Menge bei „Silikon-Kartusche“ muss größer als 0 sein.' });
        expect(pruefeZeilen([entwurf({ einkaufspreis: '' })])).toEqual({ gueltig: false, meldung: 'Bitte den Einkaufspreis bei „Silikon-Kartusche“ eingeben.' });
        expect(pruefeZeilen([entwurf({ einkaufspreis: '-1' })]).gueltig).toBe(false);
        expect(pruefeZeilen([entwurf({ aufschlag: 'x' })]).gueltig).toBe(false);
    });
});

describe('gueltigeZeilen / berechneSummen / sammelListeText', () => {
    it('nimmt für Live-Anzeigen nur vollständige Zeilen', () => {
        const zeilen = gueltigeZeilen([
            entwurf(),
            entwurf({ index: 2, einkaufspreis: '' }),
            entwurf({ index: 3, menge: '' }),
            entwurf({ index: 4, aufschlag: '' }),
        ]);
        expect(zeilen).toEqual([zeile()]);
    });

    it('summiert Einkauf, Verkauf und Gewinn auf Cent', () => {
        expect(berechneSummen([zeile(), zeile({ index: 1, menge: 2, einkaufspreis: 10, aufschlagProzent: 10 })]))
            .toEqual({ einkauf: 41, verkauf: 48.25, gewinn: 7.25 });
        expect(berechneSummen([])).toEqual({ einkauf: 0, verkauf: 0, gewinn: 0 });
    });

    it('baut die Kurzliste mit deutschem Mengenformat', () => {
        expect(sammelListeText([
            zeile(),
            zeile({ menge: 2.5, bezeichnung: 'Injektionsmörtel' }),
            zeile({ menge: 100, bezeichnung: 'Schraube 6x100' }),
        ])).toBe('5× Silikon-Kartusche, 2,5× Injektionsmörtel, 100× Schraube 6x100');
    });
});

describe('baueEinzelBloecke / kundentextEinzeln', () => {
    it('baut je Artikel eine Position – Kundentext ohne Artikelnummer und Lieferant', () => {
        const bloecke = baueEinzelBloecke([
            zeile({ artikelId: 7, bezeichnung: 'Sechskantmutter M2,5 Edelstahl' }),
            zeile({ index: 1, einheit: 'm', langtext: 'Sechskantmutter DIN 934\nEdelstahl A2' }),
        ], ids());
        expect(bloecke).toEqual([
            {
                id: 'id-1', type: 'SERVICE', title: 'Sechskantmutter M2,5 Edelstahl', description: '<p>Sechskantmutter M2,5 Edelstahl</p>',
                quantity: 5, unit: 'Stk', price: 5.25, fontSize: 10, fett: false, optional: false, artikelId: 7,
            },
            {
                id: 'id-2', type: 'SERVICE', title: 'Silikon-Kartusche',
                description: '<p>Silikon-Kartusche</p><p>Sechskantmutter DIN 934<br>Edelstahl A2</p>',
                quantity: 5, unit: 'm', price: 5.25, fontSize: 10, fett: false, optional: false,
            },
        ]);
        for (const b of bloecke) expect(b.description).not.toContain('0892 100');
    });

    it('escaped Bezeichnung und Langtext, Zeilenumbrüche werden zu <br>', () => {
        expect(kundentextEinzeln({ bezeichnung: '<script>alert(1)</script>', langtext: '<img src=x onerror=alert(1)>\r\nZeile 2' }))
            .toBe('<p>&lt;script&gt;alert(1)&lt;/script&gt;</p><p>&lt;img src=x onerror=alert(1)&gt;<br>Zeile 2</p>');
        expect(kundentextEinzeln({ bezeichnung: 'Schraube', langtext: '   ' })).toBe('<p>Schraube</p>');
    });

    it('vergibt ohne eigenen Erzeuger echte IDs', () => {
        const [a, b] = baueEinzelBloecke([zeile(), zeile({ index: 1 })]);
        expect(a.id).toBeTruthy();
        expect(a.id).not.toBe(b.id);
    });
});

describe('baueSammelBlock', () => {
    it('fasst alles in einer Pauschal-Position zusammen', () => {
        const block = baueSammelBlock([
            zeile(),
            zeile({ index: 1, menge: 2, bezeichnung: 'Injektionsmörtel', einkaufspreis: 10, aufschlagProzent: 10 }),
        ], ' Material Bad ', ids());
        expect(block).toEqual({
            id: 'id-1', type: 'SERVICE', title: 'Material Bad', description: '<p>5× Silikon-Kartusche, 2× Injektionsmörtel</p>',
            quantity: 1, unit: 'pauschal', price: 48.25, fontSize: 10, fett: false, optional: false,
        });
        expect(block.artikelId).toBeUndefined();
        expect(block.description).not.toContain('0892 100');
    });

    it('nimmt „Material“, wenn die Überschrift leer ist, und escaped die Liste', () => {
        const block = baueSammelBlock([zeile({ bezeichnung: '<script>x</script>' })], '  ');
        expect(block.title).toBe('Material');
        expect(block.description).toBe('<p>5× &lt;script&gt;x&lt;/script&gt;</p>');
    });
});

describe('artikelstammErgebnisText / stammAktionFuer', () => {
    it('fasst das Ergebnis in einem Satz zusammen', () => {
        expect(artikelstammErgebnisText({ aktualisiert: 2, angelegt: 1 })).toBe('2 Preise aktualisiert, 1 Artikel angelegt.');
        expect(artikelstammErgebnisText({ aktualisiert: 1, angelegt: 0 })).toBe('1 Preis aktualisiert.');
        expect(artikelstammErgebnisText({ aktualisiert: 0, angelegt: 3 })).toBe('3 Artikel angelegt.');
        expect(artikelstammErgebnisText({ aktualisiert: 0, angelegt: 0 })).toBe('Im Artikelstamm hat sich nichts geändert.');
    });

    it('entscheidet zwischen neu, aktualisieren und unverändert', () => {
        expect(stammAktionFuer(null, 4.2)).toBe('neu');
        expect(stammAktionFuer({ aktuellerPreis: 4.2 }, 4.2)).toBe('unveraendert');
        expect(stammAktionFuer({ aktuellerPreis: 0.0043 }, 0.0045)).toBe('aktualisieren');
        expect(stammAktionFuer({ aktuellerPreis: 4 }, 4.2)).toBe('aktualisieren');
        expect(stammAktionFuer({ aktuellerPreis: null }, 4.2)).toBe('aktualisieren');
    });
});

describe('zeilenSchluessel / entferneZeile / stelleZeileWiederHer', () => {
    const a = entwurf({ index: 0 });
    const b = entwurf({ index: 1, bezeichnung: 'Injektionsmörtel' });
    const c = entwurf({ vorgangId: 43, index: 0, bezeichnung: 'Schraube 6x100' });

    it('unterscheidet gleiche Indizes aus verschiedenen Shop-Besuchen', () => {
        expect(zeilenSchluessel(a)).toBe('42:0');
        expect(zeilenSchluessel(c)).toBe('43:0');
    });

    it('nimmt eine Zeile heraus und merkt sich die Stelle', () => {
        const { zeilen, entfernt } = entferneZeile([a, b, c], '42:1');
        expect(zeilen).toEqual([a, c]);
        expect(entfernt).toEqual({ zeile: b, stelle: 1 });
    });

    it('lässt die Liste unverändert, wenn es die Zeile nicht gibt', () => {
        const liste = [a];
        expect(entferneZeile(liste, '99:0')).toEqual({ zeilen: liste, entfernt: null });
    });

    it('legt eine Zeile an alter Stelle zurück, sonst ans Ende, aber nie doppelt', () => {
        expect(stelleZeileWiederHer([a, c], { zeile: b, stelle: 1 })).toEqual([a, b, c]);
        expect(stelleZeileWiederHer([a], { zeile: c, stelle: 5 })).toEqual([a, c]);
        expect(stelleZeileWiederHer([a], { zeile: b, stelle: -1 })).toEqual([b, a]);
        const liste = [a, b];
        expect(stelleZeileWiederHer(liste, { zeile: b, stelle: 0 })).toBe(liste);
    });
});

describe('haengeZeilenAn / weitereArtikelText', () => {
    it('hängt neue Zeilen unten an, behält Änderungen und führt gleiche Artikel nicht zusammen', () => {
        const geaendert = entwurf({ bezeichnung: 'Silikon weiß', menge: '7' });
        const gleicherArtikel = entwurf({ vorgangId: 43, index: 0 });
        expect(haengeZeilenAn([geaendert], [gleicherArtikel])).toEqual([geaendert, gleicherArtikel]);
    });

    it('hängt Zeilen eines schon übernommenen Besuchs nicht doppelt an', () => {
        const a = entwurf();
        expect(haengeZeilenAn([a], [entwurf()])).toEqual([a]);
    });

    it('sagt, wie viele Artikel dazugekommen sind', () => {
        expect(weitereArtikelText(3)).toBe('3 weitere Artikel aus dem Shop übernommen.');
        expect(weitereArtikelText(1)).toBe('1 weiterer Artikel aus dem Shop übernommen.');
        expect(weitereArtikelText(0)).toBe('Im Shop-Warenkorb war diesmal nichts – die Liste bleibt, wie sie ist.');
    });
});

describe('artikelstammAuftraegeJeBesuch / addiereStammErgebnisse', () => {
    it('teilt die Preis-Aktualisierungen nach Shop-Besuch auf – neu und unverändert gehen nicht mit', () => {
        expect(artikelstammAuftraegeJeBesuch([
            { zeile: zeile({ vorgangId: 42, index: 0, einkaufspreis: 4 }), aktion: 'aktualisieren' },
            { zeile: zeile({ vorgangId: 43, index: 0, bezeichnung: 'Schraube', einkaufspreis: 0.2 }), aktion: 'neu' },
            { zeile: zeile({ vorgangId: 42, index: 2, bezeichnung: 'Mörtel', einkaufspreis: 10 }), aktion: 'aktualisieren' },
            { zeile: zeile({ vorgangId: 44, index: 1 }), aktion: 'unveraendert' },
            { zeile: zeile({ vorgangId: 43, index: 1, einkaufspreis: 0.1 }), aktion: 'aktualisieren' },
        ])).toEqual([
            { vorgangId: 42, auftrag: {
                aktualisieren: [{ index: 0, einkaufspreis: 4 }, { index: 2, einkaufspreis: 10 }],
                neuAnlegen: [],
            } },
            { vorgangId: 43, auftrag: { aktualisieren: [{ index: 1, einkaufspreis: 0.1 }], neuAnlegen: [] } },
        ]);
        expect(artikelstammAuftraegeJeBesuch([])).toEqual([]);
    });

    it('zählt die Ergebnisse zusammen', () => {
        expect(addiereStammErgebnisse([{ aktualisiert: 1, angelegt: 0 }, { aktualisiert: 1, angelegt: 1 }])).toEqual({ aktualisiert: 2, angelegt: 1 });
        expect(addiereStammErgebnisse([])).toEqual({ aktualisiert: 0, angelegt: 0 });
    });
});

describe('verrechnungseinheitAus / artikelVorbelegungAus', () => {
    it('ordnet die Shop-Einheiten den Einheiten im Artikelstamm zu', () => {
        expect(verrechnungseinheitAus('Stk')).toBe('STUECK');
        expect(verrechnungseinheitAus(' stk. ')).toBe('STUECK');
        expect(verrechnungseinheitAus('Stück')).toBe('STUECK');
        expect(verrechnungseinheitAus('m')).toBe('LAUFENDE_METER');
        expect(verrechnungseinheitAus('m²')).toBe('QUADRATMETER');
        expect(verrechnungseinheitAus('m2')).toBe('QUADRATMETER');
        expect(verrechnungseinheitAus('qm')).toBe('QUADRATMETER');
        expect(verrechnungseinheitAus('kg')).toBe('KILOGRAMM');
        expect(verrechnungseinheitAus('l')).toBe('LITER');
        expect(verrechnungseinheitAus('Pak')).toBeUndefined();
        expect(verrechnungseinheitAus('')).toBeUndefined();
    });

    const LIEFERANT = { id: 7, name: 'Muster Großhandel' };

    it('belegt Name, Lieferanten-Nr., Lieferant, Preis und Einheit vor – Preis aus dem Shop', () => {
        expect(artikelVorbelegungAus(zeile({ bezeichnung: 'Neutralsilikon betongrau', einkaufspreis: 4.2 }), 4.2, LIEFERANT)).toEqual({
            produktname: 'Neutralsilikon betongrau',
            externeArtikelnummer: '0892 100',
            lieferant: LIEFERANT,
            preis: 4.2,
            verrechnungseinheit: 'STUECK',
            preiseinheit: 'Stk',
            preisQuelle: 'IDS_SHOP',
            hinweis: SHOP_ANLEGEN_HINWEIS,
        });
    });

    it('nennt den Preis „von Hand“, wenn er geändert wurde oder der Shop keinen geschickt hat', () => {
        expect(artikelVorbelegungAus(zeile({ einkaufspreis: 5 }), 4.2, LIEFERANT).preisQuelle).toBe('MANUELL');
        expect(artikelVorbelegungAus(zeile({ einkaufspreis: 5 }), null, LIEFERANT).preisQuelle).toBe('MANUELL');
        expect(artikelVorbelegungAus(zeile({ einheit: 'Pak' }), 4.2, LIEFERANT).verrechnungseinheit).toBeUndefined();
    });
});
