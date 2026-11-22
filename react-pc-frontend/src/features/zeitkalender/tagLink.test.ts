import { describe, expect, it } from 'vitest';
import { leseTagLink, ohneTagLink, tagLinkSchluessel } from './tagLink';

const params = (query: string) => new URLSearchParams(query);

describe('leseTagLink', () => {
    it('liest Tag, markierte Buchungen und Anlass', () => {
        expect(leseTagLink(params('mitarbeiterId=1&jahr=2026&monat=11&tag=2026-11-18&anlass=ueberschneidung&markiert=12,13'))).toEqual({
            tag: '2026-11-18',
            markiert: [12, 13],
            anlass: 'ueberschneidung',
        });
    });

    it('kennt den Anlass „ohne-feierabend“ und lässt einen unbekannten Anlass weg', () => {
        expect(leseTagLink(params('tag=2026-11-18&anlass=ohne-feierabend'))?.anlass).toBe('ohne-feierabend');
        expect(leseTagLink(params('tag=2026-11-18&anlass=<script>'))?.anlass).toBeNull();
        expect(leseTagLink(params('tag=2026-11-18'))?.anlass).toBeNull();
    });

    it('liefert ohne Tag oder mit kaputtem Tag nichts (Altbestand bleibt wie bisher)', () => {
        expect(leseTagLink(params('mitarbeiterId=1&jahr=2026&monat=11'))).toBeNull();
        expect(leseTagLink(params('tag='))).toBeNull();
        expect(leseTagLink(params('tag=18.11.2026'))).toBeNull();
        expect(leseTagLink(params('tag=2026-02-30'))).toBeNull();
        expect(leseTagLink(params('tag=2026-13-01'))).toBeNull();
        expect(leseTagLink(params('tag=2026-11-18T00:00'))).toBeNull();
    });

    it('nimmt nur positive Ganzzahlen, ohne Doppelte, und höchstens zehn', () => {
        expect(leseTagLink(params('tag=2026-11-18&markiert=3,abc,-4,0,3, 5 ,1.5,1e3,9007199254740993'))?.markiert).toEqual([3, 5]);
        const viele = Array.from({ length: 14 }, (_, i) => i + 1).join(',');
        expect(leseTagLink(params(`tag=2026-11-18&markiert=${viele}`))?.markiert).toEqual([1, 2, 3, 4, 5, 6, 7, 8, 9, 10]);
        expect(leseTagLink(params('tag=2026-11-18'))?.markiert).toEqual([]);
        expect(leseTagLink(params('tag=2026-11-18&markiert='))?.markiert).toEqual([]);
    });
});

describe('ohneTagLink', () => {
    it('entfernt tag, markiert und anlass, behält Mitarbeiter und Monat', () => {
        const ergebnis = ohneTagLink(params('mitarbeiterId=1&jahr=2026&monat=11&tag=2026-11-18&markiert=1&anlass=ueberschneidung'));
        expect(ergebnis.toString()).toBe('mitarbeiterId=1&jahr=2026&monat=11');
    });
});

describe('tagLinkSchluessel', () => {
    it('unterscheidet Links nach Mitarbeiter, Tag, Markierung und Anlass', () => {
        const link = { tag: '2026-11-18', markiert: [1, 2], anlass: 'ueberschneidung' as const };
        expect(tagLinkSchluessel(1, link)).toBe(tagLinkSchluessel(1, { ...link }));
        expect(tagLinkSchluessel(2, link)).not.toBe(tagLinkSchluessel(1, link));
        expect(tagLinkSchluessel(1, { ...link, markiert: [1] })).not.toBe(tagLinkSchluessel(1, link));
        expect(tagLinkSchluessel(1, { ...link, anlass: null })).not.toBe(tagLinkSchluessel(1, link));
    });
});
