import { describe, expect, it } from 'vitest';
import { trailingQuoteStart } from './threadPlainTextQuotes';

describe('trailingQuoteStart', () => {
    it('finds the closing ">" block and skips trailing blank lines', () => {
        expect(trailingQuoteStart(['Neu', '', '> Alt', '>', '> Alt 2', '', ''])).toBe(2);
    });

    it('takes an attribution-like intro line along, also across blank lines', () => {
        expect(trailingQuoteStart(['Neu', 'max@example.com hat geschrieben:', '', '> Alt', '> Alt 2'])).toBe(1);
    });

    it('leaves an ordinary sentence ending in ":" visible', () => {
        expect(trailingQuoteStart(['Hallo', 'bitte so einbauen:', '> Abstand 12 cm', '> Höhe 90 cm'])).toBe(2);
    });

    it('needs two ">" lines or a recognised attribution', () => {
        expect(trailingQuoteStart(['Hallo', 'die Breite muss', '> 3 m sein'])).toBe(-1);
        expect(trailingQuoteStart(['Neu', 'Am 8. September 2026 schrieb max@example.com:', '> Alt'])).toBe(1);
    });

    it('returns -1 without a closing ">" block', () => {
        expect(trailingQuoteStart(['Neu', '> Alt', 'Antwort'])).toBe(-1);
        expect(trailingQuoteStart(['', ''])).toBe(-1);
        expect(trailingQuoteStart([])).toBe(-1);
    });

    it('starts at the first line when the whole text is quoted', () => {
        expect(trailingQuoteStart(['> Alt', '> Alt 2'])).toBe(0);
    });
});
