import { describe, expect, it } from 'vitest';
import type { Sachkonto, Zahlungsart } from '../../types';
import {
    buildSachkontoOptions, buildZahlungsartOptions, formatEuro, isoDatum, sicherheitsText,
} from './belegFormat';

describe('formatEuro', () => {
    it('zeigt einen Gedankenstrich fuer null/undefined', () => {
        expect(formatEuro(null)).toBe('–');
        expect(formatEuro(undefined)).toBe('–');
    });

    it('formatiert 0 mit zwei Nachkommastellen', () => {
        expect(formatEuro(0)).toBe('0,00');
    });

    it('formatiert grosse Betraege mit Tausenderpunkt', () => {
        expect(formatEuro(1234.5)).toBe('1.234,50');
    });

    it('formatiert negative Betraege mit Minus', () => {
        expect(formatEuro(-50)).toBe('-50,00');
    });
});

describe('isoDatum', () => {
    it('formatiert den 1. Januar in lokaler Zeit als YYYY-01-01 -- nicht den 31.12. des Vorjahres', () => {
        // new Date(jahr, monat, tag) erzeugt lokale Zeit (keine UTC-Umrechnung
        // wie toISOString()) -- genau der Fall aus dem Kommentar in
        // belegFormat.ts: ohne getFullYear()/getMonth()/getDate() wuerde der
        // Zeitraum-Filter am Jahreswechsel danebengreifen.
        expect(isoDatum(new Date(2026, 0, 1))).toBe('2026-01-01');
    });

    it('fuellt Monat und Tag zweistellig auf', () => {
        expect(isoDatum(new Date(2026, 8, 9))).toBe('2026-09-09');
    });

    it('rechnet ueber den Jahreswechsel am 31. Dezember korrekt', () => {
        expect(isoDatum(new Date(2025, 11, 31))).toBe('2025-12-31');
    });
});

describe('buildSachkontoOptions', () => {
    const sachkonto = (overrides: Partial<Sachkonto>): Sachkonto => ({
        id: 1,
        bezeichnung: 'Beispielkonto',
        kontoTyp: 'AUFWAND',
        aktiv: true,
        sortierung: 0,
        ...overrides,
    });

    it('gruppiert in der Reihenfolge Aufwand -> Ertrag -> Privat -> Neutral, mit fuehrendem Leer-Eintrag', () => {
        // Bewusst NICHT in Zielreihenfolge eingegeben, damit die Funktion die
        // Sortierung wirklich selbst herstellt statt nur die Eingabe durchzureichen.
        const sachkonten: Sachkonto[] = [
            sachkonto({ id: 4, kontoTyp: 'NEUTRAL', bezeichnung: 'Durchlaufposten', sortierung: 1 }),
            sachkonto({ id: 3, kontoTyp: 'PRIVAT', bezeichnung: 'Privatentnahme', sortierung: 1 }),
            sachkonto({ id: 2, kontoTyp: 'ERTRAG', bezeichnung: 'Erloese', sortierung: 1 }),
            sachkonto({ id: 1, kontoTyp: 'AUFWAND', bezeichnung: 'Buerobedarf', sortierung: 1 }),
        ];

        const options = buildSachkontoOptions(sachkonten);

        expect(options[0]).toEqual({ value: '', label: '– kein Konto –' });
        expect(options.map(o => o.value)).toEqual(['', '1', '2', '3', '4']);
        expect(options[1].label).toContain('Aufwand');
        expect(options[2].label).toContain('Ertrag');
        expect(options[3].label).toContain('Privat');
        expect(options[4].label).toContain('Neutral');
    });

    it('sortiert innerhalb einer Gruppe nach `sortierung`', () => {
        const sachkonten: Sachkonto[] = [
            sachkonto({ id: 20, kontoTyp: 'AUFWAND', bezeichnung: 'Zweites Konto', sortierung: 2 }),
            sachkonto({ id: 10, kontoTyp: 'AUFWAND', bezeichnung: 'Erstes Konto', sortierung: 1 }),
        ];

        const options = buildSachkontoOptions(sachkonten);

        expect(options.map(o => o.value)).toEqual(['', '10', '20']);
    });
});

describe('buildZahlungsartOptions', () => {
    const zahlungsarten: Zahlungsart[] = [
        { id: 1, bezeichnung: 'Bar', aktiv: true, sortierung: 2 },
        { id: 2, bezeichnung: 'EC-Karte', aktiv: true, sortierung: 1 },
    ];

    it('fuehrt einen Leer-Eintrag voran', () => {
        const options = buildZahlungsartOptions(zahlungsarten);
        expect(options[0]).toEqual({ value: '', label: '– keine Angabe –' });
    });

    it('haengt einen bestehenden Fremdwert als "(nicht im Stamm)" an', () => {
        const options = buildZahlungsartOptions(zahlungsarten, 'Gutschein');
        const letzte = options[options.length - 1];
        expect(letzte).toEqual({ value: 'Gutschein', label: 'Gutschein (nicht im Stamm)' });
    });

    it('haengt nichts an, wenn der bestehende Wert schon im Stamm steht', () => {
        const options = buildZahlungsartOptions(zahlungsarten, 'Bar');
        expect(options.some(o => o.label.includes('nicht im Stamm'))).toBe(false);
    });
});

describe('sicherheitsText', () => {
    it('meldet "Sicherheit unbekannt" bei null/undefined', () => {
        expect(sicherheitsText(null).text).toBe('Sicherheit unbekannt');
        expect(sicherheitsText(undefined).text).toBe('Sicherheit unbekannt');
    });

    it('ist ab 0.95 "sehr sicher"', () => {
        expect(sicherheitsText(0.95).text).toBe('sehr sicher');
        expect(sicherheitsText(1).text).toBe('sehr sicher');
    });

    it('ist knapp unter 0.95 nur noch "ziemlich sicher"', () => {
        expect(sicherheitsText(0.9499).text).toBe('ziemlich sicher');
    });

    it('ist ab 0.70 "ziemlich sicher"', () => {
        expect(sicherheitsText(0.70).text).toBe('ziemlich sicher');
    });

    it('ist knapp unter 0.70 "eher unsicher"', () => {
        expect(sicherheitsText(0.6999).text).toBe('eher unsicher – bitte prüfen');
    });

    it('ist ab 0.30 "eher unsicher"', () => {
        expect(sicherheitsText(0.30).text).toBe('eher unsicher – bitte prüfen');
    });

    it('ist unter 0.30 "sehr unsicher"', () => {
        expect(sicherheitsText(0.2999).text).toBe('sehr unsicher – bitte prüfen');
        expect(sicherheitsText(0).text).toBe('sehr unsicher – bitte prüfen');
    });
});
