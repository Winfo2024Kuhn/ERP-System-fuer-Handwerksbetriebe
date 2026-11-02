import { describe, expect, it } from 'vitest';
import { formatBytes } from './dateiGroesse';

describe('formatBytes', () => {
    it('KB und MB mit Komma', () => {
        expect(formatBytes(86_000)).toBe('84 KB');
        expect(formatBytes(10)).toBe('1 KB');
        expect(formatBytes(482_113)).toBe('471 KB');
        expect(formatBytes(1.5 * 1024 * 1024)).toBe('1,5 MB');
    });
});
