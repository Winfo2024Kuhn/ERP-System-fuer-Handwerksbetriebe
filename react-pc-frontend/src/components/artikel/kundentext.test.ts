import { describe, expect, it } from 'vitest';

import { baueKundentext, hatKundentext, kundentextFuerPosition } from './kundentext';
import type { Artikel } from '../../types';

/** Nur die Felder setzen, um die es im jeweiligen Fall geht. */
const artikel = (felder: Partial<Artikel>): Artikel =>
    ({ id: 1, produktname: '', ...felder }) as Artikel;

const merkmal = (name: string, anzeigename: string) => ({ name, anzeigename });

describe('hatKundentext', () => {
    it('erkennt einen echten Text', () => {
        expect(hatKundentext('<p>Rundrohr 42,4 x 2 mm</p>')).toBe(true);
    });

    it.each([undefined, null, '', '<p></p>', '<p>&nbsp;</p>', '   '])(
        'wertet %s als leer',
        (wert) => {
            expect(hatKundentext(wert)).toBe(false);
        },
    );
});

describe('baueKundentext — Regelfall Halbzeug', () => {
    it('setzt Profilform, Abmessung und Werkstoff zu einem Satz zusammen', () => {
        const text = baueKundentext(artikel({
            produktname: 'RO 42,4X2,0',
            profilform: merkmal('RUNDROHR', 'Rundrohr'),
            abmessung: '42,4 x 2',
            werkstoffName: '1.4301',
        }));

        expect(text).toBe('<p>Rundrohr 42,4 x 2 mm aus Edelstahl 1.4301</p>');
    });

    it('haengt nahtlos und geschliffen an, weil sie sonst gleiche Positionen ununterscheidbar machen', () => {
        const text = baueKundentext(artikel({
            produktname: 'RO 42,4X2,0',
            profilform: merkmal('RUNDROHR', 'Rundrohr'),
            abmessung: '42,4 x 2',
            werkstoffName: '1.4301',
            herstellverfahren: merkmal('NAHTLOS', 'nahtlos'),
            fertigungszustand: merkmal('GESCHLIFFEN', 'geschliffen'),
        }));

        expect(text).toBe('<p>Rundrohr 42,4 x 2 mm aus Edelstahl 1.4301, nahtlos, geschliffen</p>');
    });

    it('laesst den Normalfall gewalzt weg', () => {
        const text = baueKundentext(artikel({
            produktname: 'VR 40X40X2',
            profilform: merkmal('VIERKANTROHR', 'Vierkantrohr'),
            abmessung: '40 x 40 x 2',
            herstellverfahren: merkmal('GESCHWEISST', 'geschweißt'),
        }));

        expect(text).toBe('<p>Vierkantrohr 40 x 40 x 2 mm</p>');
    });

    it('haengt beim Blech kein zweites mm an', () => {
        const text = baueKundentext(artikel({
            produktname: 'BL 2,0',
            profilform: merkmal('BLECH', 'Blech'),
            abmessung: '2 mm',
            werkstoffName: 'DX51D+Z',
        }));

        expect(text).toBe('<p>Blech 2 mm aus verzinktem Stahl (DX51D+Z)</p>');
    });

    it('nennt einen Winkel nur Winkel — gleich- oder ungleichschenklig sagt die Abmessung', () => {
        const text = baueKundentext(artikel({
            produktname: 'WI 40X40X4',
            profilform: merkmal('WINKEL_GLEICHSCHENKLIG', 'Winkel gleichschenklig'),
            abmessung: '40 x 40 x 4',
        }));

        expect(text).toBe('<p>Winkel 40 x 40 x 4 mm</p>');
    });

    it('nimmt den internen Sammelwert SONSTIGES nicht aufs Kundendokument', () => {
        const text = baueKundentext(artikel({
            produktname: 'Sonderteil XY',
            profilform: merkmal('SONSTIGES', 'Sonstiges'),
            abmessung: '30 x 10',
        }));

        // Faellt auf den Produktnamen zurueck statt "Sonstiges 30 x 10 mm" zu drucken.
        expect(text).toBe('<p>Sonderteil XY, 30 x 10 mm</p>');
    });

    it('behaelt unbekannte Werkstoffcodes unveraendert', () => {
        const text = baueKundentext(artikel({
            produktname: 'RO 20X2',
            profilform: merkmal('RUNDROHR', 'Rundrohr'),
            abmessung: '20 x 2',
            werkstoffName: 'X5CrNi18-10',
        }));

        expect(text).toBe('<p>Rundrohr 20 x 2 mm aus X5CrNi18-10</p>');
    });
});

describe('baueKundentext — Rueckfallebene ueber den Produktnamen', () => {
    it('laesst den Produktnamen den Text tragen, wenn er mit einem echten Wort beginnt', () => {
        const text = baueKundentext(artikel({
            produktname: 'Türschließer TS 3000 silber',
            werkstoffName: 'Stahl',
        }));

        expect(text).toBe('<p>Türschließer TS 3000 silber aus Baustahl</p>');
    });

    it('wiederholt den Werkstoff nicht, wenn er schon im Produktnamen steht', () => {
        const text = baueKundentext(artikel({
            produktname: 'Allzweckdübel Kunststoff',
            werkstoffName: 'Kunststoff',
        }));

        expect(text).toBe('<p>Allzweckdübel Kunststoff</p>');
    });

    it('haengt die Abmessung nur an, wenn sie nicht schon im Namen steckt', () => {
        // "42,4x2" und "42.4 x 2" sind dieselbe Angabe - Komma/Punkt und
        // Leerzeichen duerfen den Vergleich nicht auseinanderreissen.
        expect(baueKundentext(artikel({ produktname: 'Edelstahlrohr 42,4x2', abmessung: '42.4 x 2' })))
            .toBe('<p>Edelstahlrohr 42,4x2</p>');
        expect(baueKundentext(artikel({ produktname: 'Edelstahlrohr', abmessung: '42,4 x 2' })))
            .toBe('<p>Edelstahlrohr, 42,4 x 2 mm</p>');
    });

    it('maskiert spitze Klammern aus den Stammdaten', () => {
        expect(baueKundentext(artikel({ produktname: 'Halter <klein> & fein' })))
            .toBe('<p>Halter &lt;klein&gt; &amp; fein</p>');
    });
});

describe('baueKundentext — wann bewusst gar kein Text entsteht', () => {
    it('liefert nichts fuer ein kryptisches Lieferantenkuerzel ohne Profilform', () => {
        // Genau der Fall, den auch das Backfill-Skript aussortiert: "RO 42,4X2,0"
        // ist keine Sprache, die ein Kunde auf einem Angebot lesen sollte.
        expect(baueKundentext(artikel({ produktname: 'RO 42,4X2,0' }))).toBe('');
    });

    it('liefert nichts fuer eine nackte Masskette', () => {
        expect(baueKundentext(artikel({ produktname: '42.4x2' }))).toBe('');
    });

    it('liefert nichts ohne jede Angabe', () => {
        expect(baueKundentext(artikel({ produktname: '' }))).toBe('');
    });

    it('liefert nichts, wenn nur die Profilform ohne Abmessung da ist und der Name kryptisch bleibt', () => {
        expect(baueKundentext(artikel({
            produktname: 'RO 42,4X2,0',
            profilform: merkmal('RUNDROHR', 'Rundrohr'),
        }))).toBe('');
    });
});

describe('kundentextFuerPosition', () => {
    it('nimmt den gepflegten Text und erzeugt nichts dazu', () => {
        const text = kundentextFuerPosition(artikel({
            produktname: 'RO 42,4X2,0',
            profilform: merkmal('RUNDROHR', 'Rundrohr'),
            abmessung: '42,4 x 2',
            beschreibung: '<p>Handgeschriebener Kundentext</p>',
        }));

        expect(text).toBe('<p>Handgeschriebener Kundentext</p>');
    });

    it('erzeugt einen Text, wenn am Artikel nur ein leerer Rich-Text-Rest steht', () => {
        const text = kundentextFuerPosition(artikel({
            produktname: 'RO 42,4X2,0',
            profilform: merkmal('RUNDROHR', 'Rundrohr'),
            abmessung: '42,4 x 2',
            beschreibung: '<p></p>',
        }));

        expect(text).toBe('<p>Rundrohr 42,4 x 2 mm</p>');
    });

    it('bleibt leer, wenn sich nichts Kundentaugliches ableiten laesst', () => {
        expect(kundentextFuerPosition(artikel({ produktname: 'RO 42,4X2,0' }))).toBe('');
    });
});
