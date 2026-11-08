import { describe, expect, it } from 'vitest';
import { formatiereBetrag, jahreZusammenfuehren, lohnabrechnungenUrl, meldungAusFehlertext } from './firmaHilfen';

describe('jahreZusammenfuehren', () => {
    it('nimmt das aktuelle Jahr immer mit und sortiert absteigend ohne Doppelte', () => {
        expect(jahreZusammenfuehren(2026, [2024, 2025], [2025, 2023])).toEqual([2026, 2025, 2024, 2023]);
    });

    it('übergeht Antworten, die keine Liste sind', () => {
        expect(jahreZusammenfuehren(2026, null, { jahre: [2020] })).toEqual([2026]);
    });
});

describe('lohnabrechnungenUrl', () => {
    it('fragt ohne Filter alle Abrechnungen des Jahres ab', () => {
        expect(lohnabrechnungenUrl('ALL', 2026)).toBe('/api/lohnabrechnungen/jahr/2026');
    });

    it('fragt mit Filter nur den gewählten Steuerberater ab', () => {
        expect(lohnabrechnungenUrl('3', 2026)).toBe('/api/lohnabrechnungen/steuerberater/3/jahr/2026');
    });
});

describe('formatiereBetrag', () => {
    it('zeigt Beträge mit zwei Nachkommastellen im deutschen Format', () => {
        expect(formatiereBetrag(12345.6)).toBe('12.345,60');
        expect(formatiereBetrag(0)).toBe('0,00');
    });
});

describe('meldungAusFehlertext', () => {
    it('nimmt die Meldung aus der JSON-Antwort', () => {
        expect(meldungAusFehlertext('{"message":"Name fehlt."}', 'Ersatz', () => 'kein JSON')).toBe('Name fehlt.');
    });

    it('nimmt den Ersatztext, wenn das JSON keine Meldung hat', () => {
        expect(meldungAusFehlertext('{}', 'Ersatz', () => 'kein JSON')).toBe('Ersatz');
        expect(meldungAusFehlertext('{"message":""}', 'Ersatz', () => 'kein JSON')).toBe('Ersatz');
    });

    it('nimmt den Text für Nicht-JSON, auch bei null', () => {
        expect(meldungAusFehlertext('Serverfehler', 'Ersatz', text => `Fehler: ${text}`)).toBe('Fehler: Serverfehler');
        expect(meldungAusFehlertext('null', 'Ersatz', () => 'kein JSON')).toBe('kein JSON');
    });
});
