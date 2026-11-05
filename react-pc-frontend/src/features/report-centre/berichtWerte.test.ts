import { describe, expect, it } from 'vitest';
import type { FilterFeld, FilterWerte, MitarbeiterFeld } from './berichtTypen';
import {
    gewaehlteIds, gewaehlteMitarbeiter, grundwerte, mitarbeiterFeld, mitarbeiterText, pruefeWerte, zeitraumAus, zusammenfassung,
} from './berichtWerte';

const MAX = { id: 1, name: 'Max Mustermann' };
const ERIKA = { id: 2, name: 'Erika Musterfrau' };
const ALLE = [MAX, ERIKA];

const werte = (teil: Partial<FilterWerte> = {}): FilterWerte => ({
    ...grundwerte(new Date(2026, 10, 5), { mitTagesliste: true }), ...teil,
});

const alleFeld: MitarbeiterFeld = { typ: 'mitarbeiter', min: 1, max: 50, alleVorauswaehlen: true };
const keinerFeld: MitarbeiterFeld = { ...alleFeld, alleVorauswaehlen: false };

describe('grundwerte', () => {
    it('nimmt den Vormonat und lässt die Mitarbeiter offen', () => {
        expect(werte()).toEqual({
            jahr: 2026, monat: 10, zeitraumArt: 'monat', von: '2026-10-01', bis: '2026-10-31',
            mitarbeiterIds: null, schalter: { mitTagesliste: true },
        });
    });
});

describe('Mitarbeiter-Auswahl', () => {
    it('findet das Mitarbeiter-Feld', () => {
        expect(mitarbeiterFeld([{ typ: 'monat' }, alleFeld])).toBe(alleFeld);
        expect(mitarbeiterFeld([{ typ: 'monat' }])).toBeUndefined();
    });
    it('wählt vor dem ersten Klick alle oder keinen vor', () => {
        expect(gewaehlteIds(alleFeld, werte(), ALLE)).toEqual([1, 2]);
        expect(gewaehlteIds(keinerFeld, werte(), ALLE)).toEqual([]);
        expect(gewaehlteIds(undefined, werte(), ALLE)).toEqual([]);
    });
    it('nimmt nach einer Änderung die eigene Auswahl', () => {
        expect(gewaehlteIds(alleFeld, werte({ mitarbeiterIds: [2] }), ALLE)).toEqual([2]);
    });
    it('löst IDs in Listenreihenfolge auf und lässt Unbekannte weg', () => {
        expect(gewaehlteMitarbeiter([2, 1, 99], ALLE)).toEqual([MAX, ERIKA]);
    });
    it('fasst lange Namenslisten zusammen', () => {
        expect(mitarbeiterText([])).toBe('keiner');
        expect(mitarbeiterText(ALLE)).toBe('Max Mustermann, Erika Musterfrau');
        const viele = [MAX, ERIKA, { id: 3, name: 'A' }, { id: 4, name: 'B' }];
        expect(mitarbeiterText(viele)).toBe('Max Mustermann, Erika Musterfrau und 2 weitere');
    });
});

describe('zeitraumAus', () => {
    it('rechnet Monat, Jahr und freien Zeitraum', () => {
        expect(zeitraumAus(werte({ zeitraumArt: 'monat', monat: 2, jahr: 2028 }))).toEqual({ von: '2028-02-01', bis: '2028-02-29' });
        expect(zeitraumAus(werte({ zeitraumArt: 'jahr' }))).toEqual({ von: '2026-01-01', bis: '2026-12-31' });
        expect(zeitraumAus(werte({ zeitraumArt: 'bereich', von: '2026-03-01', bis: '2026-03-15' }))).toEqual({ von: '2026-03-01', bis: '2026-03-15' });
    });
});

describe('pruefeWerte', () => {
    const felder: FilterFeld[] = [
        { typ: 'monat' }, { typ: 'zeitraum' }, keinerFeld, { typ: 'schalter', schluessel: 'mitTagesliste', beschriftung: 'Tagesliste' },
    ];
    it('ist zufrieden mit gültigen Werten', () => {
        expect(pruefeWerte(felder, werte(), 1, 2)).toBeNull();
    });
    it('verlangt einen gültigen Monat', () => {
        expect(pruefeWerte(felder, werte({ monat: 0 }), 1, 2)).toBe('Bitte einen Monat wählen.');
    });
    it('prüft den freien Zeitraum', () => {
        expect(pruefeWerte(felder, werte({ zeitraumArt: 'bereich', von: '2026-10-31', bis: '2026-10-01' }), 1, 2))
            .toBe('Das Ende darf nicht vor dem Anfang liegen.');
    });
    it('verlangt mindestens einen und höchstens 50 Mitarbeiter', () => {
        expect(pruefeWerte(felder, werte(), 0, 60)).toBe('Bitte mindestens einen Mitarbeiter wählen.');
        expect(pruefeWerte(felder, werte(), 51, 60)).toBe('Bitte höchstens 50 Mitarbeiter auf einmal wählen.');
        // Ohne „alle erlaubt“ gilt die Grenze auch, wenn alle gewählt sind.
        expect(pruefeWerte(felder, werte(), 60, 60)).toBe('Bitte höchstens 50 Mitarbeiter auf einmal wählen.');
    });
    it('erlaubt beim Steuerberater „alle“ auch über 50, eine Teilauswahl aber nicht', () => {
        const steuerberater: FilterFeld[] = [{ ...alleFeld, alleErlaubt: true }];
        expect(pruefeWerte(steuerberater, werte(), 60, 60)).toBeNull();
        expect(pruefeWerte(steuerberater, werte(), 50, 60)).toBeNull();
        expect(pruefeWerte(steuerberater, werte(), 51, 60)).toBe('Bitte höchstens 50 Mitarbeiter auswählen oder alle.');
    });
});

describe('zusammenfassung', () => {
    it('beschreibt jedes Feld in einer Zeile', () => {
        const felder: FilterFeld[] = [
            { typ: 'monat' }, { typ: 'zeitraum' }, keinerFeld,
            { typ: 'schalter', schluessel: 'mitTagesliste', beschriftung: 'Tagesliste' },
            { typ: 'schalter', schluessel: 'mitUnterschrift', beschriftung: 'Unterschriftszeile' },
        ];
        expect(zusammenfassung(felder, werte(), [MAX])).toEqual([
            ['Monat', 'Oktober 2026'],
            ['Zeitraum', 'Oktober 2026'],
            ['Mitarbeiter (1)', 'Max Mustermann'],
            ['Tagesliste', 'ja'],
            ['Unterschriftszeile', 'nein'],
        ]);
    });
    it('nennt ganzes Jahr und freien Zeitraum', () => {
        expect(zusammenfassung([{ typ: 'zeitraum' }], werte({ zeitraumArt: 'jahr' }), [])).toEqual([['Zeitraum', 'Ganzes Jahr 2026']]);
        expect(zusammenfassung([{ typ: 'zeitraum' }], werte({ zeitraumArt: 'bereich', von: '2026-03-01', bis: '2026-03-15' }), []))
            .toEqual([['Zeitraum', '01.03.2026 bis 15.03.2026']]);
    });
});
