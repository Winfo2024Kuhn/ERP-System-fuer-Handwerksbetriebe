import { describe, expect, it } from 'vitest';
import { decodeMimeWord, formatDateLabel, formatSize, formatTime, getDayString, isWiederholtesSignaturLogo } from './threadFormat';

describe('threadFormat', () => {
    it('formatiert Dateigrößen', () => {
        expect(formatSize(undefined)).toBe('');
        expect(formatSize(512)).toBe('512 B');
        expect(formatSize(2048)).toBe('2 KB');
        expect(formatSize(3 * 1024 * 1024)).toBe('3.0 MB');
    });

    it('liefert Tag und Uhrzeit, leer ohne Datum', () => {
        expect(getDayString('2026-11-04T10:05:00')).toBe('2026-11-04');
        expect(getDayString(undefined)).toBe('');
        expect(formatTime('2026-11-04T10:05:00')).toBe('10:05');
        expect(formatTime(undefined)).toBe('');
        expect(formatDateLabel('2026-11-04T10:05:00')).toContain('4. November 2026');
    });

    it('dekodiert RFC-2047-Dateinamen (Q und B)', () => {
        expect(decodeMimeWord('=?iso-8859-1?Q?Gel=E4nder_Max_Mustermann.pdf?=')).toBe('Geländer Max Mustermann.pdf');
        expect(decodeMimeWord('=?utf-8?B?VG9yLnBkZg==?=')).toBe('Tor.pdf');
        expect(decodeMimeWord('normal.pdf')).toBe('normal.pdf');
    });

    it('erkennt nur kleine Bilder als wiederholtes Signatur-Logo', () => {
        expect(isWiederholtesSignaturLogo({ id: 1, originalFilename: 'logo.png', mimeType: 'image/png', sizeBytes: 5000 })).toBe(true);
        expect(isWiederholtesSignaturLogo({ id: 2, originalFilename: 'foto.jpg', mimeType: 'image/jpeg', sizeBytes: 500_000 })).toBe(false);
        expect(isWiederholtesSignaturLogo({ id: 3, originalFilename: 'plan.pdf', mimeType: 'application/pdf', sizeBytes: 5000 })).toBe(false);
        expect(isWiederholtesSignaturLogo({ id: 4, originalFilename: 'logo.png', mimeType: 'image/png' })).toBe(false);
    });
});
