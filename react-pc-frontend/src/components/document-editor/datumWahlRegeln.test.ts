import { describe, it, expect } from 'vitest';
import {
    DATUM_WAHL_START,
    datumWahlFehler,
    gewaehltesDatum,
    mussDatumFragen,
    zahlbarBis,
} from './datumWahlRegeln';

const HEUTE = '2026-11-17';
const ANGELEGT = '2026-11-03';

describe('mussDatumFragen', () => {
    const offen = { gespeichertesDatum: ANGELEGT, heute: HEUTE, gesperrt: false, entwurf: false };

    it('fragt, wenn ein offenes Dokument ein anderes Datum als heute trägt', () => {
        expect(mussDatumFragen(offen)).toBe(true);
    });

    it('fragt nicht, wenn das Datum schon heute ist', () => {
        expect(mussDatumFragen({ ...offen, gespeichertesDatum: HEUTE })).toBe(false);
    });

    it('fragt nicht bei gesperrten Dokumenten (gebucht, angenommen, storniert)', () => {
        expect(mussDatumFragen({ ...offen, gesperrt: true })).toBe(false);
    });

    it('fragt nicht beim Entwurf mit Wasserzeichen', () => {
        expect(mussDatumFragen({ ...offen, entwurf: true })).toBe(false);
    });

    it('fragt nicht, wenn noch gar kein Datum gespeichert ist', () => {
        expect(mussDatumFragen({ ...offen, gespeichertesDatum: '' })).toBe(false);
        expect(mussDatumFragen({ ...offen, gespeichertesDatum: null })).toBe(false);
    });
});

describe('gewaehltesDatum', () => {
    it('liefert heute als Vorauswahl', () => {
        expect(gewaehltesDatum(DATUM_WAHL_START, ANGELEGT, HEUTE)).toBe(HEUTE);
    });

    it('liefert das bisherige Datum', () => {
        expect(gewaehltesDatum({ art: 'bisher', anderesDatum: '' }, ANGELEGT, HEUTE)).toBe(ANGELEGT);
    });

    it('liefert das andere Datum, leer solange keins gewählt ist', () => {
        expect(gewaehltesDatum({ art: 'anders', anderesDatum: '2026-11-10' }, ANGELEGT, HEUTE)).toBe('2026-11-10');
        expect(gewaehltesDatum({ art: 'anders', anderesDatum: '' }, ANGELEGT, HEUTE)).toBe('');
    });
});

describe('datumWahlFehler', () => {
    it('akzeptiert heute und ältere Daten auch bei Rechnungen', () => {
        expect(datumWahlFehler(DATUM_WAHL_START, ANGELEGT, HEUTE, true)).toBeNull();
        expect(datumWahlFehler({ art: 'bisher', anderesDatum: '' }, ANGELEGT, HEUTE, true)).toBeNull();
        expect(datumWahlFehler({ art: 'anders', anderesDatum: '2025-01-31' }, ANGELEGT, HEUTE, true)).toBeNull();
    });

    it('verlangt bei „Anderes Datum“ ein gewähltes Datum', () => {
        expect(datumWahlFehler({ art: 'anders', anderesDatum: '' }, ANGELEGT, HEUTE, false))
            .toBe('Bitte ein Datum auswählen.');
    });

    it('sperrt bei Rechnungen ein Datum in der Zukunft', () => {
        expect(datumWahlFehler({ art: 'anders', anderesDatum: '2026-11-18' }, ANGELEGT, HEUTE, true))
            .toMatch(/Zukunft/);
        expect(datumWahlFehler({ art: 'bisher', anderesDatum: '' }, '2026-12-01', HEUTE, true))
            .toMatch(/Zukunft/);
    });

    it('erlaubt bei Angeboten auch ein Datum in der Zukunft', () => {
        expect(datumWahlFehler({ art: 'anders', anderesDatum: '2026-12-24' }, ANGELEGT, HEUTE, false)).toBeNull();
    });
});

describe('zahlbarBis', () => {
    it('rechnet das Zahlungsziel ab dem gewählten Datum', () => {
        expect(zahlbarBis('2026-11-03', 14)).toBe('17.11.2026');
        expect(zahlbarBis('2026-12-20', 14)).toBe('03.01.2027');
    });

    it('liefert nichts ohne Datum', () => {
        expect(zahlbarBis('', 14)).toBe('');
    });
});
