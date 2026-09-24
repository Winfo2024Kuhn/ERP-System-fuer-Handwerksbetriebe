import { describe, expect, it } from 'vitest';
import type { KassenBewegung } from '../../types';
import { baueJournal } from './journalSaldo';

const bewegungen: KassenBewegung[] = [
    { belegId: 1, datum: '2026-09-01', kategorie: 'PRIVATEINLAGE', beschreibung: 'Startgeld', betrag: 100, saldoNachher: 150, laufendeNummer: 91 },
    { belegId: 2, datum: '2026-09-02', kategorie: 'KASSE_AUSGABE', beschreibung: 'Werkzeug', lieferantName: 'Musterbaustoffe GmbH', betrag: -30, saldoNachher: 120 },
    { belegId: 3, datum: '2026-09-03', kategorie: 'KASSE_EINNAHME', beschreibung: 'Kundenzahlung', betrag: 20, saldoNachher: 140, laufendeNummer: null },
    { belegId: 4, datum: '2026-09-04', kategorie: 'KASSE_EINNAHME', beschreibung: null, lieferantName: null, betrag: 0, saldoNachher: 140 },
];

describe('baueJournal', () => {
    it('erhält feste Nummern und nummeriert offene Zeilen vor der Suche', () => {
        const zeilen = baueJournal(bewegungen, '');
        expect(zeilen.map(z => z.anzeigeNummer)).toEqual([91, null, null, null]);
        expect(zeilen.map(z => z.vorlaeufigeNummer)).toEqual([1, 2, 3, 4]);
        expect(baueJournal(bewegungen, 'Werkzeug')[0].vorlaeufigeNummer).toBe(2);
    });

    it.each([
        ['  WERKZEUG  ', [2]],
        ['musterbaustoffe', [2]],
        ['Eigenes Geld eingelegt', [1]],
        ['Kasse – Einnahme', [3, 4]],
        ['unbekannt', []],
    ])('sucht nach Beschreibung, Lieferant oder Kategorie: %s', (suche, ids) => {
        expect(baueJournal(bewegungen, suche).map(z => z.belegId)).toEqual(ids);
    });

    it('teilt Einnahmen und positive Ausgabebeträge auf und erhält Nullbuchungen', () => {
        expect(baueJournal(bewegungen, '').map(z => [z.einnahme, z.ausgabe]))
            .toEqual([[100, null], [null, 30], [20, null], [0, null]]);
    });

    it('übernimmt den Serverbestand auch bei gefilterten oder unvollständigen Zeilen', () => {
        const zeilen = baueJournal([{ ...bewegungen[1], saldoNachher: 999.25 }], 'Werkzeug');
        expect(zeilen[0].saldoNachher).toBe(999.25);
        expect(bewegungen[1]).not.toHaveProperty('einnahme');
    });

    it('ergibt im vollständigen festen Datensatz aus Anfangsbestand und Bewegungen den Endbestand', () => {
        const saldoEnde = baueJournal(bewegungen, '').reduce(
            (bestand, zeile) => bestand + (zeile.einnahme ?? 0) - (zeile.ausgabe ?? 0), 50);
        expect(saldoEnde).toBe(140);
    });

    it('liefert für einen leeren Zeitraum keine Zeilen', () => {
        expect(baueJournal([], '')).toEqual([]);
    });
});
