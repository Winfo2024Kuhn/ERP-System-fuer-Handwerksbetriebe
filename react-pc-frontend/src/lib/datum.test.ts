import { describe, it, expect, afterEach, vi } from 'vitest';
import { formatDatum, heuteIso, isoDatum, parseDeutschesDatum } from './datum';

describe('Datumsumwandlung', () => {
    afterEach(() => { vi.useRealTimers(); });

    it('formatiert ein Datum in lokaler Zeit, nicht nach UTC', () => {
        // 1. Januar 00:30 Ortszeit: toISOString() haette in Deutschland
        // den 31.12. des Vorjahres geliefert.
        expect(isoDatum(new Date(2026, 0, 1, 0, 30))).toBe('2026-01-01');
        expect(isoDatum(new Date(2026, 8, 9, 23, 45))).toBe('2026-09-09');
    });

    it('liefert bei jedem Aufruf das aktuelle Datum statt eines eingefrorenen Werts', () => {
        vi.useFakeTimers();
        vi.setSystemTime(new Date(2026, 8, 9, 12, 0));
        expect(heuteIso()).toBe('2026-09-09');
        vi.setSystemTime(new Date(2026, 8, 10, 0, 5));
        expect(heuteIso()).toBe('2026-09-10');
    });
});

describe('Deutsche Datumseingabe', () => {
    it('akzeptiert die üblichen Schreibweisen', () => {
        expect(parseDeutschesDatum('9.9.1967')).toBe('1967-09-09');
        expect(parseDeutschesDatum('09.09.1967')).toBe('1967-09-09');
        expect(parseDeutschesDatum(' 09091967 ')).toBe('1967-09-09');
        expect(parseDeutschesDatum('1.12.2026')).toBe('2026-12-01');
    });
    it('lehnt unvollständige, erfundene und zweistellige Jahre ab', () => {
        expect(parseDeutschesDatum('31.02.2026')).toBeNull();
        expect(parseDeutschesDatum('9.9.67')).toBeNull();
        expect(parseDeutschesDatum('9.1967')).toBeNull();
        expect(parseDeutschesDatum('')).toBeNull();
    });
});

describe('formatDatum', () => {
    it('zeigt Datum und Zeitpunkt deutsch, leer bei fehlendem oder kaputtem Wert', () => {
        expect(formatDatum('2026-03-02')).toBe('02.03.2026');
        expect(formatDatum('2026-03-02T10:00:00')).toBe('02.03.2026');
        expect(formatDatum(null)).toBe('');
        expect(formatDatum(undefined)).toBe('');
        expect(formatDatum('gestern')).toBe('');
    });
});
