import { describe, expect, it } from 'vitest';
import {
    ALLE_DOKUMENTARTEN,
    OHNE_ZUORDNUNG,
    dokumentartBezeichnung,
    dokumentartOptionen,
    durchsuchbarMachen,
    filtereTextbausteine,
    gueltigeDokumentart,
    zaehleDokumentarten,
} from './textbausteinFilter';
import { TEST_GRUPPEN, testBaustein } from './__tests__/testdaten';

const LISTE = [
    testBaustein({ id: 1, name: 'Anschreiben Angebot', html: '<p>Guten Tag {{Kundenname}}</p>', dokumenttypen: ['Angebot', 'Nachtragsangebot'] }),
    testBaustein({ id: 2, name: 'Zahlungshinweis', html: '<p>Zahlbar bis {{ZAHLUNGSZIEL}} ohne Abzug.</p>', dokumenttypen: ['Rechnung', 'Angebot'] }),
    testBaustein({ id: 3, name: 'Gruß', html: '<p>Mit freundlichen <b>Grüßen</b></p>', dokumenttypen: [] }),
];
const DURCHSUCHBAR = durchsuchbarMachen(LISTE);
const ids = (suche: string, dokumentart = ALLE_DOKUMENTARTEN) =>
    filtereTextbausteine(DURCHSUCHBAR, { suche, dokumentart }).map((b) => b.id);

describe('filtereTextbausteine', () => {
    it('sucht in Name und Text, Groß-/Kleinschreibung egal, Wörter in beliebiger Reihenfolge', () => {
        expect(ids('')).toEqual([1, 2, 3]);
        expect(ids('anschreiben')).toEqual([1]);
        expect(ids('ABZUG')).toEqual([2]);
        expect(ids('freundlichen grüßen')).toEqual([3]);
        expect(ids('grüßen freundlichen')).toEqual([3]);
        expect(ids('nichts dergleichen')).toEqual([]);
    });

    it('filtert nach Dokumentart und „Ohne Zuordnung“, auch zusammen mit der Suche', () => {
        expect(ids('', 'Angebot')).toEqual([1, 2]);
        expect(ids('', 'Rechnung')).toEqual([2]);
        expect(ids('', '1. Mahnung')).toEqual([]);
        expect(ids('', OHNE_ZUORDNUNG)).toEqual([3]);
        expect(ids('zahlbar', 'Angebot')).toEqual([2]);
    });
});

describe('zaehleDokumentarten und dokumentartOptionen', () => {
    it('zählt je Dokumentart, ohne Zuordnung und gesamt', () => {
        const zaehler = zaehleDokumentarten(LISTE);
        expect(zaehler.alle).toBe(3);
        expect(zaehler.ohneZuordnung).toBe(1);
        expect(zaehler.proDokumentart.get('Angebot')).toBe(2);
        expect(zaehler.proDokumentart.get('Rechnung')).toBe(1);
    });

    it('baut gruppierte Optionen mit Anzahl, „Alle“ zuerst und „Ohne Zuordnung“ zuletzt', () => {
        const optionen = dokumentartOptionen(TEST_GRUPPEN, zaehleDokumentarten(LISTE));
        expect(optionen[0]).toEqual({ value: ALLE_DOKUMENTARTEN, label: 'Alle Dokumentarten (3)' });
        expect(optionen.find((o) => o.value === 'Angebot')).toEqual({ value: 'Angebot', label: 'Angebot (2)', gruppe: 'Angebote & Aufträge' });
        expect(optionen.find((o) => o.value === '2. Mahnung')?.label).toBe('2. Mahnung (0)');
        expect(optionen.at(-1)).toEqual({ value: OHNE_ZUORDNUNG, label: 'Ohne Zuordnung (1)', gruppe: 'Weitere' });
        expect(optionen).toHaveLength(1 + 12 + 1);
    });

    it('beschriftet Filterwerte lesbar', () => {
        expect(dokumentartBezeichnung(OHNE_ZUORDNUNG)).toBe('Ohne Zuordnung');
        expect(dokumentartBezeichnung('Rechnung')).toBe('Rechnung');
    });

    it('übernimmt nur bekannte Dokumentarten aus der URL, sonst „alle“', () => {
        expect(gueltigeDokumentart('Rechnung', TEST_GRUPPEN)).toBe('Rechnung');
        expect(gueltigeDokumentart(OHNE_ZUORDNUNG, TEST_GRUPPEN)).toBe(OHNE_ZUORDNUNG);
        expect(gueltigeDokumentart(ALLE_DOKUMENTARTEN, TEST_GRUPPEN)).toBe(ALLE_DOKUMENTARTEN);
        expect(gueltigeDokumentart('Lieferschein', TEST_GRUPPEN)).toBe(ALLE_DOKUMENTARTEN);
    });
});
