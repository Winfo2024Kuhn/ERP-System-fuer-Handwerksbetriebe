import { afterEach, describe, expect, it, vi } from 'vitest';
import { formatEmailDate, verlaufAnzahl } from './emailsTabFormat';

afterEach(() => vi.restoreAllMocks());

describe('formatEmailDate', () => {
    it('zeigt Datum und Uhrzeit im deutschen Format', () => {
        expect(formatEmailDate({ sentAt: '2026-10-09T10:05:00' })).toBe('09.10.2026, 10:05');
    });

    it('nimmt Empfangs- oder Sendedatum, wenn sentAt fehlt', () => {
        expect(formatEmailDate({ receivedDate: '2026-01-02T08:00:00' })).toBe('02.01.2026, 08:00');
        expect(formatEmailDate({ sentDate: '2026-03-04T09:30:00' })).toBe('04.03.2026, 09:30');
    });

    it('zeigt einen Strich ohne Datum', () => {
        expect(formatEmailDate({})).toBe('-');
    });

    it('zeigt den Rohwert, wenn das Formatieren scheitert', () => {
        vi.spyOn(Date.prototype, 'toLocaleDateString').mockImplementation(() => { throw new RangeError('x'); });
        expect(formatEmailDate({ sentAt: 'irgendwas' })).toBe('irgendwas');
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
