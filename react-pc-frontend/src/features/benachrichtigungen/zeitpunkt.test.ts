import { describe, expect, it } from 'vitest';
import { formatZeitpunkt } from './zeitpunkt';

const JETZT = new Date(2026, 9, 11, 9, 30, 0); // 11.10.2026 09:30 Ortszeit

describe('formatZeitpunkt', () => {
    it('heute, gestern, morgen mit Uhrzeit', () => {
        expect(formatZeitpunkt('2026-10-11T02:00:17', JETZT)).toBe('heute, 02:00');
        expect(formatZeitpunkt('2026-10-10T14:12:00', JETZT)).toBe('gestern, 14:12');
        expect(formatZeitpunkt('2026-10-12T09:00:00', JETZT)).toBe('morgen, 09:00');
    });

    it('sonst das volle Datum mit Uhrzeit', () => {
        expect(formatZeitpunkt('2026-10-01T02:00:00', JETZT)).toBe('01.10.2026, 02:00');
        expect(formatZeitpunkt('2025-12-31T23:59:59.123', JETZT)).toBe('31.12.2025, 23:59');
    });

    it('nur ein Datum: keine Uhrzeit', () => {
        expect(formatZeitpunkt('2026-10-11', JETZT)).toBe('heute');
        expect(formatZeitpunkt('2026-10-10', JETZT)).toBe('gestern');
        expect(formatZeitpunkt('2026-08-01', JETZT)).toBe('01.08.2026');
    });

    it('rechnet nicht in UTC um (kurz nach Mitternacht bleibt der Tag)', () => {
        const kurzNachMitternacht = new Date(2026, 9, 11, 0, 10, 0);
        expect(formatZeitpunkt('2026-10-11T00:05:00', kurzNachMitternacht)).toBe('heute, 00:05');
    });

    it('über Monats- und Jahresgrenzen', () => {
        expect(formatZeitpunkt('2026-12-31T22:00:00', new Date(2027, 0, 1, 8, 0, 0))).toBe('gestern, 22:00');
        expect(formatZeitpunkt('2026-10-31', new Date(2026, 10, 1, 8, 0, 0))).toBe('gestern');
    });

    it('Unlesbares ergibt einen leeren Text', () => {
        expect(formatZeitpunkt('', JETZT)).toBe('');
        expect(formatZeitpunkt(null, JETZT)).toBe('');
        expect(formatZeitpunkt(undefined, JETZT)).toBe('');
        expect(formatZeitpunkt('gestern', JETZT)).toBe('');
        expect(formatZeitpunkt('2026-02-31', JETZT)).toBe('');
    });

    it('nimmt ohne Angabe die aktuelle Uhrzeit', () => {
        const jetzt = new Date();
        const zwei = (n: number) => String(n).padStart(2, '0');
        const heute = `${jetzt.getFullYear()}-${zwei(jetzt.getMonth() + 1)}-${zwei(jetzt.getDate())}`;
        expect(formatZeitpunkt(heute)).toBe('heute');
    });
});
