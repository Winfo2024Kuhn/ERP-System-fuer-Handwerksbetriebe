import { describe, expect, it } from 'vitest';
import { EINZELVERSAND_ZU_VIELE } from '../einzelversand';
import { emailComposeTitel, emailComposeUntertitel, sendenSperre, type SendenZustand } from './emailComposeAnzeige';

/** Kopf und Sendeknopf des E-Mail-Formulars. Dummy-Daten (DSGVO): Max Mustermann. */
describe('emailComposeTitel', () => {
    it('nennt eine Antwort „Antwort“, auch wenn der Betreff eine Bestellanfrage ist', () => {
        expect(emailComposeTitel({ antwort: true, weiterleitung: false, betreff: 'AW: Angebot Treppengeländer' })).toBe('Antwort');
        expect(emailComposeTitel({ antwort: true, weiterleitung: false, betreff: 'Bestellanfrage: Musterhandel GmbH' })).toBe('Antwort');
    });

    it('nennt eine Weiterleitung „Weiterleitung“', () => {
        expect(emailComposeTitel({ antwort: false, weiterleitung: true, betreff: 'WG: Angebot Treppengeländer' })).toBe('Weiterleitung');
    });

    it('nennt bei einer Bestellanfrage den Lieferanten', () => {
        expect(emailComposeTitel({ antwort: false, weiterleitung: false, betreff: ' bestellanfrage:  Musterhandel GmbH ' }))
            .toBe('Bestellung an Musterhandel GmbH senden');
    });

    it('bleibt sonst bei „Neue E-Mail“, auch bei einer Bestellanfrage ohne Namen', () => {
        expect(emailComposeTitel({ antwort: false, weiterleitung: false, betreff: '' })).toBe('Neue E-Mail');
        expect(emailComposeTitel({ antwort: false, weiterleitung: false, betreff: 'Bestellanfrage:' })).toBe('Neue E-Mail');
        expect(emailComposeTitel({ antwort: false, weiterleitung: false, betreff: 'Angebot Treppengeländer' })).toBe('Neue E-Mail');
    });
});

describe('emailComposeUntertitel', () => {
    it('nennt den Vorgang, wenn einer feststeht', () => {
        expect(emailComposeUntertitel({ vorgang: { typ: 'Anfrage', name: 'Geländer Mustermann' }, betreff: 'Bestellanfrage: Musterhandel' }))
            .toBe('Anfrage: Geländer Mustermann');
        expect(emailComposeUntertitel({ vorgang: { typ: 'Projekt', name: 'Treppe Mustermann' }, betreff: '' }))
            .toBe('Projekt: Treppe Mustermann');
    });

    it('beschreibt sonst die Bestellanfrage oder das Schreiben', () => {
        expect(emailComposeUntertitel({ betreff: 'Bestellanfrage: Musterhandel GmbH' })).toBe('Bestellanfrage mit E-Mail-Versand vorbereiten');
        expect(emailComposeUntertitel({ vorgang: { typ: 'Projekt', name: '' }, betreff: 'Hallo' })).toBe('E-Mail verfassen und Anhänge verwalten');
    });
});

describe('sendenSperre', () => {
    const frei: SendenZustand = {
        beschaeftigt: false, originalAnhaengeLaden: false, empfaengerFehlt: false, betreffFehlt: false,
        anhaengeZuGross: false, zuVieleEmpfaenger: false, pflichtanhangFehlt: false,
    };

    it('gibt frei, wenn alles da ist', () => {
        expect(sendenSperre(frei)).toEqual({ gesperrt: false, hinweis: undefined });
    });

    it.each([
        ['beschaeftigt'], ['empfaengerFehlt'], ['betreffFehlt'], ['anhaengeZuGross'],
    ] as const)('sperrt bei %s ohne eigenen Hinweis', (feld) => {
        expect(sendenSperre({ ...frei, [feld]: true })).toEqual({ gesperrt: true, hinweis: undefined });
    });

    it('erklärt zu viele Empfänger vor fehlendem Pflichtanhang und ladenden Anhängen', () => {
        const alles = { ...frei, zuVieleEmpfaenger: true, pflichtanhangFehlt: true, originalAnhaengeLaden: true };
        expect(sendenSperre(alles, 'Rechnung fehlt')).toEqual({ gesperrt: true, hinweis: EINZELVERSAND_ZU_VIELE });
        expect(sendenSperre({ ...alles, zuVieleEmpfaenger: false }, 'Rechnung fehlt')).toEqual({ gesperrt: true, hinweis: 'Rechnung fehlt' });
        expect(sendenSperre({ ...frei, originalAnhaengeLaden: true }))
            .toEqual({ gesperrt: true, hinweis: 'Die Anhänge der Original-Mail werden noch geladen.' });
    });
});
