import { afterEach, describe, expect, it, vi } from 'vitest';
import { entwurfBezeichnung, formatEmailDate, verlaufAnzahl } from './emailsTabFormat';

afterEach(() => vi.restoreAllMocks());

describe('formatEmailDate', () => {
    it('zeigt Datum und Uhrzeit im deutschen Format', () => {
        expect(formatEmailDate('2026-10-09T10:05:00')).toBe('09.10.2026, 10:05');
    });

    it('zeigt einen Strich ohne Datum', () => {
        expect(formatEmailDate(null)).toBe('-');
        expect(formatEmailDate(undefined)).toBe('-');
    });

    it('zeigt den Rohwert, wenn das Formatieren scheitert', () => {
        vi.spyOn(Date.prototype, 'toLocaleDateString').mockImplementation(() => { throw new RangeError('x'); });
        expect(formatEmailDate('irgendwas')).toBe('irgendwas');
    });
});

describe('verlaufAnzahl', () => {
    const mail = { isDraft: false };
    const entwurf = { isDraft: true };

    it('zählt Nachrichten und Entwürfe getrennt, Einzahl und Mehrzahl', () => {
        expect(verlaufAnzahl([mail])).toBe('1 Nachricht im Verlauf');
        expect(verlaufAnzahl([mail, mail, mail])).toBe('3 Nachrichten im Verlauf');
        expect(verlaufAnzahl([mail, entwurf])).toBe('1 Nachricht im Verlauf, 1 Entwurf');
        expect(verlaufAnzahl([mail, mail, entwurf, entwurf])).toBe('2 Nachrichten im Verlauf, 2 Entwürfe');
        expect(verlaufAnzahl([])).toBe('0 Nachrichten im Verlauf');
    });
});

describe('entwurfBezeichnung', () => {
    it('nennt den Betreff, sonst den Textanfang, sonst nur „Entwurf“', () => {
        expect(entwurfBezeichnung({ subject: '  AW: Geländer  ', snippet: 'Text' })).toBe('Entwurf „AW: Geländer“');
        expect(entwurfBezeichnung({ subject: ' ', snippet: 'Angebot folgt bis Freitag' })).toBe('Entwurf „Angebot folgt bis Freitag“');
        expect(entwurfBezeichnung({ snippet: '[Entwurf]' })).toBe('Entwurf');
        expect(entwurfBezeichnung(undefined)).toBe('Entwurf');
    });

    it('kürzt lange Texte mit echtem Auslassungszeichen', () => {
        const lang = 'Sehr geehrter Herr Mustermann, anbei das Angebot für das Geländer';
        expect(entwurfBezeichnung({ snippet: lang })).toBe('Entwurf „Sehr geehrter Herr Mustermann, anbei das…“');
    });
});
