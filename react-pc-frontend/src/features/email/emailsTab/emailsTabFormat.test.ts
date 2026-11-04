import { afterEach, describe, expect, it, vi } from 'vitest';
import { formatEmailDate } from './emailsTabFormat';

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
