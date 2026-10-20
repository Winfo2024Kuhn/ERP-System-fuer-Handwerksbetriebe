/**
 * Vitest-Suite fuer „Als neue Leistung speichern“: Abweichung einer Position
 * von ihrer Ursprungs-Leistung und Rueckweg der Einheit auf den Enum-Namen.
 *
 * DSGVO: ausschliesslich Dummy-Daten.
 */
import { describe, it, expect } from 'vitest';
import { anzeigeEinheitDerLeistung, einheitZuLeistungsEinheit, weichtVonLeistungAb } from './helpers';
import type { DocBlock, LeistungApiDto } from './types';

const leistung: LeistungApiDto = {
    id: 7,
    name: 'Geländer montieren',
    description: '<p>Montage inkl. <b>Befestigung</b> &amp; Abdichtung</p>',
    price: 120,
    unit: { name: 'LAUFENDE_METER', anzeigename: 'Laufende Meter' },
    folderId: 12,
};

/** So legt der Editor die Position beim Einfuegen an (LeistungPickerModal onSelect). */
const eingefuegt: Pick<DocBlock, 'title' | 'description' | 'price' | 'unit'> = {
    title: 'Geländer montieren',
    description: '<p>Montage inkl. <strong>Befestigung</strong> &amp; Abdichtung</p><p></p>',
    price: 120,
    unit: 'lfm',
};

describe('weichtVonLeistungAb', () => {
    it('meldet keine Abweichung direkt nach dem Einfuegen (auch wenn Tiptap das HTML umschreibt)', () => {
        expect(weichtVonLeistungAb(eingefuegt, leistung)).toBe(false);
    });

    it('ignoriert Leerraum am Rand des Namens', () => {
        expect(weichtVonLeistungAb({ ...eingefuegt, title: '  Geländer montieren ' }, leistung)).toBe(false);
    });

    it('erkennt einen geaenderten Namen', () => {
        expect(weichtVonLeistungAb({ ...eingefuegt, title: 'Geländer montieren, verzinkt' }, leistung)).toBe(true);
    });

    it('erkennt einen geaenderten Text', () => {
        expect(weichtVonLeistungAb({ ...eingefuegt, description: '<p>Montage ohne Befestigung</p>' }, leistung)).toBe(true);
    });

    it('erkennt einen geaenderten Preis, aber keine Rundungsreste unter einem Cent', () => {
        expect(weichtVonLeistungAb({ ...eingefuegt, price: 135.5 }, leistung)).toBe(true);
        expect(weichtVonLeistungAb({ ...eingefuegt, price: 120.001 }, leistung)).toBe(false);
    });

    it('zaehlt fehlenden Preis wie 0', () => {
        const ohnePreis = { ...leistung, price: null as unknown as number };
        expect(weichtVonLeistungAb({ ...eingefuegt, price: 0 }, ohnePreis)).toBe(false);
        expect(weichtVonLeistungAb({ ...eingefuegt, price: undefined }, ohnePreis)).toBe(false);
    });

    it('erkennt eine geaenderte Einheit', () => {
        expect(weichtVonLeistungAb({ ...eingefuegt, unit: 'm²' }, leistung)).toBe(true);
    });

    it('ignoriert frei getippte Einheiten, die sich nicht speichern lassen (sonst Doppel-Leistungen)', () => {
        expect(weichtVonLeistungAb({ ...eingefuegt, unit: 'Psch' }, leistung)).toBe(false);
        expect(weichtVonLeistungAb({ ...eingefuegt, unit: 'm' }, leistung)).toBe(false);
        // Andere Abweichungen zaehlen trotzdem.
        expect(weichtVonLeistungAb({ ...eingefuegt, unit: 'Psch', price: 99 }, leistung)).toBe(true);
    });

    it('nimmt bei fehlender Einheit Stk an – auf beiden Seiten', () => {
        const ohneEinheit = { ...leistung, unit: null };
        expect(weichtVonLeistungAb({ ...eingefuegt, unit: undefined }, ohneEinheit)).toBe(false);
        expect(weichtVonLeistungAb({ ...eingefuegt, unit: 'Stk' }, ohneEinheit)).toBe(false);
    });

    it('meldet nichts, wenn die Ursprungs-Leistung fehlt', () => {
        expect(weichtVonLeistungAb({ ...eingefuegt, title: 'Anders' }, undefined)).toBe(false);
        expect(weichtVonLeistungAb({ ...eingefuegt, title: 'Anders' }, null)).toBe(false);
    });

    it('behandelt leere Texte auf beiden Seiten als gleich', () => {
        const ohneText = { ...leistung, description: '' };
        expect(weichtVonLeistungAb({ ...eingefuegt, description: '<p></p>' }, ohneText)).toBe(false);
        expect(weichtVonLeistungAb({ ...eingefuegt, description: undefined }, ohneText)).toBe(false);
    });
});

describe('einheitZuLeistungsEinheit', () => {
    it('bildet jede Anzeige-Einheit auf ihren Enum-Namen ab', () => {
        expect(einheitZuLeistungsEinheit('lfm')).toBe('LAUFENDE_METER');
        expect(einheitZuLeistungsEinheit('m²')).toBe('QUADRATMETER');
        expect(einheitZuLeistungsEinheit('kg')).toBe('KILOGRAMM');
        expect(einheitZuLeistungsEinheit(' Stk ')).toBe('STUECK');
    });

    it('liefert null fuer frei getippte oder leere Einheiten', () => {
        expect(einheitZuLeistungsEinheit('Psch')).toBeNull();
        expect(einheitZuLeistungsEinheit('')).toBeNull();
        expect(einheitZuLeistungsEinheit(undefined)).toBeNull();
        expect(einheitZuLeistungsEinheit(null)).toBeNull();
    });
});

describe('anzeigeEinheitDerLeistung', () => {
    it('zeigt die Kurzform und faellt auf Stk zurueck', () => {
        expect(anzeigeEinheitDerLeistung(leistung)).toBe('lfm');
        expect(anzeigeEinheitDerLeistung({ unit: null })).toBe('Stk');
        expect(anzeigeEinheitDerLeistung({ unit: { name: 'UNBEKANNT', anzeigename: '?' } })).toBe('Stk');
    });
});
