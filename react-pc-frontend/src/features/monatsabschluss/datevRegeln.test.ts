import { describe, expect, it } from 'vitest';
import {
    DATEV_KATEGORIEN,
    kategorieName,
    mitPersonalnummer,
    mitStundenart,
    normalisiereKonfiguration,
    nummerFehler,
    pruefeKonfiguration,
    zugangEingetragen,
    zumSpeichern,
} from './datevRegeln';
import type { Konfiguration } from './types';

/** Nur Dummy-Daten (DSGVO). */
const basis = (): Konfiguration => normalisiereKonfiguration({
    version: 1, ziel: 'LODAS', beraterNr: '001234', mandantenNr: '0012', stundenart: 'SOLL',
    zuordnungen: [{ kategorie: 'ARBEIT', ausgeschlossen: false, lohnart: '0200' }],
    personalnummern: [{ mitarbeiterId: 1, personalnummer: '00014', stundenart: 'IST' }],
});
const name = (id: number) => (id === 1 ? 'Max Mustermann' : 'Erika Mustermann');

describe('nummerFehler', () => {
    it('lässt leere Felder und gültige Nummern mit führenden Nullen durch', () => {
        expect(nummerFehler('', 4, 7, 'Beraternummer')).toBe('');
        expect(nummerFehler('001234', 4, 7, 'Beraternummer')).toBe('');
    });

    it('lehnt Kommas, zu lange Nummern und reine Nullen ab', () => {
        expect(nummerFehler('12,5', 4, 7, 'Beraternummer')).toBe('Beraternummer: Bitte 4 bis 7 Ziffern und eine Nummer größer als 0 eingeben.');
        expect(nummerFehler('123456', 1, 5, 'Mandantennummer')).toMatch(/1 bis 5 Ziffern/);
        expect(nummerFehler('0000', 1, 4, 'Lohnart')).toMatch(/größer als 0/);
        expect(nummerFehler('12', 3, 3, 'Test')).toBe('Test: Bitte 3 Ziffern und eine Nummer größer als 0 eingeben.');
    });
});

describe('kategorieName', () => {
    it('kennt alle acht Kategorien und sonst nichts', () => {
        expect(DATEV_KATEGORIEN).toHaveLength(8);
        expect(kategorieName('FEIERTAG')).toBe('Feiertage');
        expect(kategorieName('KORREKTUR')).toBeUndefined();
    });
});

describe('normalisiereKonfiguration', () => {
    it('füllt alle Kategorien in fester Reihenfolge und ersetzt null durch leere Texte', () => {
        const c = normalisiereKonfiguration({
            version: null, ziel: 'LODAS', beraterNr: null, mandantenNr: null, stundenart: 'IST',
            zuordnungen: [{ kategorie: 'URLAUB', ausgeschlossen: true, lohnart: null }],
            personalnummern: [{ mitarbeiterId: 2, personalnummer: '7' } as Konfiguration['personalnummern'][number]],
        });
        expect(c.beraterNr).toBe('');
        expect(c.mandantenNr).toBe('');
        expect(c.stundenart).toBe('IST');
        expect(c.zuordnungen.map(z => z.kategorie)).toEqual(DATEV_KATEGORIEN.map(([k]) => k));
        expect(c.zuordnungen.find(z => z.kategorie === 'URLAUB')).toEqual({ kategorie: 'URLAUB', ausgeschlossen: true, lohnart: '' });
        expect(c.zuordnungen.find(z => z.kategorie === 'ARBEIT')).toEqual({ kategorie: 'ARBEIT', ausgeschlossen: false, lohnart: '' });
        expect(c.personalnummern).toEqual([{ mitarbeiterId: 2, personalnummer: '7', stundenart: null }]);
    });

    it('nimmt Soll-Stunden an, wenn der Server keine Stundenart schickt', () => {
        const ohne = { ...basis(), stundenart: undefined } as unknown as Konfiguration;
        expect(normalisiereKonfiguration(ohne).stundenart).toBe('SOLL');
    });
});

describe('Personalnummer und Stundenart je Mitarbeiter', () => {
    it('trägt eine neue Personalnummer ohne eigene Stundenart ein und sortiert nach Mitarbeiter', () => {
        const c = mitPersonalnummer({ ...basis(), personalnummern: [{ mitarbeiterId: 3, personalnummer: '3', stundenart: null }] }, 2, '00016');
        expect(c.personalnummern).toEqual([
            { mitarbeiterId: 2, personalnummer: '00016', stundenart: null },
            { mitarbeiterId: 3, personalnummer: '3', stundenart: null },
        ]);
    });

    it('behält die Stundenart beim Ändern und setzt sie beim Leeren zurück', () => {
        expect(mitPersonalnummer(basis(), 1, '00099').personalnummern[0]).toEqual({ mitarbeiterId: 1, personalnummer: '00099', stundenart: 'IST' });
        expect(mitPersonalnummer(basis(), 1, '').personalnummern[0]).toEqual({ mitarbeiterId: 1, personalnummer: '', stundenart: null });
    });

    it('setzt die Stundenart nur bei Mitarbeitern mit Personalnummer', () => {
        expect(mitStundenart(basis(), 1, 'SOLL').personalnummern[0].stundenart).toBe('SOLL');
        expect(mitStundenart(basis(), 1, null).personalnummern[0].stundenart).toBeNull();
        const ohneNummer = mitPersonalnummer(basis(), 1, '');
        expect(mitStundenart(ohneNummer, 1, 'IST')).toBe(ohneNummer);
        expect(mitStundenart(basis(), 2, 'IST')).toEqual(basis());
    });

    it('ändert nur den gewählten Mitarbeiter', () => {
        const zwei = mitPersonalnummer(basis(), 2, '00016');
        expect(mitStundenart(zwei, 2, 'SOLL').personalnummern).toEqual([
            { mitarbeiterId: 1, personalnummer: '00014', stundenart: 'IST' },
            { mitarbeiterId: 2, personalnummer: '00016', stundenart: 'SOLL' },
        ]);
    });
});

describe('pruefeKonfiguration', () => {
    it('ist bei einer gültigen oder leeren Konfiguration zufrieden', () => {
        expect(pruefeKonfiguration(basis(), name)).toBe('');
        expect(pruefeKonfiguration(normalisiereKonfiguration({ ...basis(), beraterNr: null, mandantenNr: null, zuordnungen: [], personalnummern: [] }), name)).toBe('');
    });

    it('meldet den ersten Fehler in Formular-Reihenfolge', () => {
        expect(pruefeKonfiguration({ ...basis(), beraterNr: '12' }, name)).toMatch(/^Beraternummer/);
        expect(pruefeKonfiguration({ ...basis(), mandantenNr: 'abc' }, name)).toMatch(/^Mandantennummer/);
        const lohnart = { ...basis(), zuordnungen: basis().zuordnungen.map(z => z.kategorie === 'URLAUB' ? { ...z, lohnart: '12345' } : z) };
        expect(pruefeKonfiguration(lohnart, name)).toMatch(/^Lohnart für Urlaub/);
        const unbekannt = { ...basis(), zuordnungen: [{ kategorie: 'SONDER', ausgeschlossen: false, lohnart: 'x' }] };
        expect(pruefeKonfiguration(unbekannt, name)).toMatch(/^Lohnart für SONDER/);
        expect(pruefeKonfiguration(mitPersonalnummer(basis(), 2, '1x'), name)).toMatch(/^Personalnummer für Erika Mustermann/);
    });

    it('erkennt doppelte Personalnummern auch mit unterschiedlichen führenden Nullen', () => {
        expect(pruefeKonfiguration(mitPersonalnummer(basis(), 2, '14'), name)).toMatch(/mehrfach vergeben/);
        expect(pruefeKonfiguration(mitPersonalnummer(mitPersonalnummer(basis(), 2, ''), 3, ''), name)).toBe('');
    });
});

describe('zumSpeichern und zugangEingetragen', () => {
    it('schickt leere Personalnummern nicht mit', () => {
        const c = mitPersonalnummer(basis(), 2, '');
        expect(zumSpeichern(c).personalnummern).toEqual([{ mitarbeiterId: 1, personalnummer: '00014', stundenart: 'IST' }]);
    });

    it('braucht Berater- und Mandantennummer', () => {
        expect(zugangEingetragen(basis())).toBe(true);
        expect(zugangEingetragen({ beraterNr: '', mandantenNr: '1' })).toBe(false);
        expect(zugangEingetragen({ beraterNr: '1234', mandantenNr: null })).toBe(false);
    });
});
