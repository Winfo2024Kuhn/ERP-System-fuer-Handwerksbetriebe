import { describe, it, expect } from 'vitest';
import {
    canCreateEinfacheRechnung,
    istRechnungAmVorgaengerGesperrt,
    hatAktiveAuftragsbestaetigung,
} from './abrechnungsverlauf';
import type { AbrechnungsverlaufDto, AbrechnungspositionDto, AusgangsGeschaeftsDokumentTyp } from '../types';

const basisVerlauf: Omit<AbrechnungsverlaufDto, 'positionen'> = {
    basisdokumentId: 1,
    basisdokumentNummer: 'AB-2026-0001',
    basisdokumentTyp: 'AUFTRAGSBESTAETIGUNG',
    basisdokumentBetragNetto: 1000,
    bereitsAbgerechnet: 0,
    restbetrag: 1000,
};

const position = (overrides: Partial<AbrechnungspositionDto>): AbrechnungspositionDto => ({
    id: 100,
    dokumentNummer: 'RE-2026-0001',
    typ: 'RECHNUNG',
    datum: '2026-05-01',
    betragNetto: 1000,
    storniert: false,
    ...overrides,
});

describe('canCreateEinfacheRechnung', () => {
    it('liefert false bei null/undefined Verlauf', () => {
        expect(canCreateEinfacheRechnung(null)).toBe(false);
        expect(canCreateEinfacheRechnung(undefined)).toBe(false);
    });

    it('liefert true, wenn noch keine Folgerechnung existiert (Happy Path)', () => {
        const verlauf: AbrechnungsverlaufDto = { ...basisVerlauf, positionen: [] };
        expect(canCreateEinfacheRechnung(verlauf)).toBe(true);
    });

    it('liefert true, wenn die einzige einfache Rechnung storniert wurde (Bug-Szenario)', () => {
        // Regression: Vorher wurde nur length===0 geprueft, daher war "Einfache Rechnung"
        // nach einer Storno-Aktion nicht mehr waehlbar, obwohl der Restbetrag wieder voll
        // verfuegbar ist.
        const verlauf: AbrechnungsverlaufDto = {
            ...basisVerlauf,
            positionen: [position({ storniert: true })],
        };
        expect(canCreateEinfacheRechnung(verlauf)).toBe(true);
    });

    it('liefert true, wenn alle bisherigen Rechnungen (egal welcher Typ) storniert sind', () => {
        const verlauf: AbrechnungsverlaufDto = {
            ...basisVerlauf,
            positionen: [
                position({ id: 10, typ: 'ABSCHLAGSRECHNUNG', storniert: true }),
                position({ id: 11, typ: 'TEILRECHNUNG', storniert: true }),
            ],
        };
        expect(canCreateEinfacheRechnung(verlauf)).toBe(true);
    });

    it('liefert false, sobald mindestens eine aktive Folgerechnung existiert', () => {
        const verlauf: AbrechnungsverlaufDto = {
            ...basisVerlauf,
            positionen: [
                position({ id: 10, typ: 'ABSCHLAGSRECHNUNG', storniert: true }),
                position({ id: 11, typ: 'ABSCHLAGSRECHNUNG', storniert: false }),
            ],
        };
        expect(canCreateEinfacheRechnung(verlauf)).toBe(false);
    });
});

const folge = (typ: AusgangsGeschaeftsDokumentTyp, storniert = false) => ({ typ, storniert });

describe('hatAktiveAuftragsbestaetigung', () => {
    it('erkennt eine aktive Auftragsbestätigung', () => {
        expect(hatAktiveAuftragsbestaetigung([folge('AUFTRAGSBESTAETIGUNG')])).toBe(true);
    });

    it('ignoriert eine stornierte Auftragsbestätigung', () => {
        // Sonst bliebe der Vorgang nach einer Stornierung dauerhaft ohne AB.
        expect(hatAktiveAuftragsbestaetigung([folge('AUFTRAGSBESTAETIGUNG', true)])).toBe(false);
    });

    it('liefert false ohne Folgedokumente und bei anderen Typen', () => {
        expect(hatAktiveAuftragsbestaetigung([])).toBe(false);
        expect(hatAktiveAuftragsbestaetigung([folge('ABSCHLAGSRECHNUNG')])).toBe(false);
    });
});

describe('istRechnungAmVorgaengerGesperrt', () => {

    it('sperrt nicht, solange keine Auftragsbestätigung existiert', () => {
        expect(istRechnungAmVorgaengerGesperrt([])).toBe(false);
        expect(istRechnungAmVorgaengerGesperrt([folge('ABSCHLAGSRECHNUNG')])).toBe(false);
    });

    it('sperrt, sobald eine Auftragsbestätigung darunter hängt', () => {
        expect(istRechnungAmVorgaengerGesperrt([folge('AUFTRAGSBESTAETIGUNG')])).toBe(true);
    });

    it('sperrt nicht, wenn am Vorgänger bereits abgerechnet wurde (Bestandsvorgang)', () => {
        // Der Verlauf der AB kennt die schon am Angebot abgerechneten Beträge nicht.
        // Ein erzwungener Wechsel würde dort den vollen Restbetrag ausweisen.
        expect(istRechnungAmVorgaengerGesperrt([
            folge('ABSCHLAGSRECHNUNG'),
            folge('AUFTRAGSBESTAETIGUNG'),
        ])).toBe(false);
    });

    it('ignoriert eine stornierte Auftragsbestätigung', () => {
        expect(istRechnungAmVorgaengerGesperrt([folge('AUFTRAGSBESTAETIGUNG', true)])).toBe(false);
    });

    it('sperrt trotz stornierter Vorrechnung, wenn eine aktive AB existiert', () => {
        // Die stornierte Rechnung zählt nicht als "hier wurde schon abgerechnet" —
        // ihr Betrag ist wieder frei, der Vorgang gehört an die AB.
        expect(istRechnungAmVorgaengerGesperrt([
            folge('ABSCHLAGSRECHNUNG', true),
            folge('AUFTRAGSBESTAETIGUNG'),
        ])).toBe(true);
    });

    it('behandelt fehlendes storniert-Flag als aktiv', () => {
        expect(istRechnungAmVorgaengerGesperrt([{ typ: 'AUFTRAGSBESTAETIGUNG' }])).toBe(true);
    });
});
