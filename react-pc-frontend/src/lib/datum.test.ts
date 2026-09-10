import { describe, it, expect, afterEach, vi } from 'vitest';
import { heuteIso, isoDatum } from './datum';

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
