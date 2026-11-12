import { describe, expect, it } from 'vitest';
import { zerlegeInLinks } from './linkErkennung';

const links = (text: string) => zerlegeInLinks(text).filter(s => s.art === 'link');

describe('zerlegeInLinks', () => {
    it('lässt Text ohne Adresse als ein Stück', () => {
        expect(zerlegeInLinks('Guten Tag, Max Mustermann')).toEqual([{ art: 'text', text: 'Guten Tag, Max Mustermann' }]);
        expect(zerlegeInLinks('')).toEqual([]);
    });

    it('erkennt https-Adressen mitten im Text', () => {
        expect(zerlegeInLinks('Freigabe: https://onedrive.example.com/f/c/abc123?e=XyZ bitte prüfen')).toEqual([
            { art: 'text', text: 'Freigabe: ' },
            { art: 'link', text: 'https://onedrive.example.com/f/c/abc123?e=XyZ', href: 'https://onedrive.example.com/f/c/abc123?e=XyZ' },
            { art: 'text', text: ' bitte prüfen' },
        ]);
    });

    it('nimmt Satzzeichen am Ende nicht in den Link', () => {
        for (const zeichen of ['.', ',', ';', ':', '!', '?', '»', '"', "'", ')', ']']) {
            expect(links(`siehe https://example.com/datei${zeichen}`)[0]).toMatchObject({ href: 'https://example.com/datei' });
        }
        expect(links('siehe https://onedrive.example.com/f/c/abc?e=x.')[0].href).toBe('https://onedrive.example.com/f/c/abc?e=x');
        expect(links('Ende: https://example.com/a?!.')[0].href).toBe('https://example.com/a');
    });

    it('behält ausgeglichene Klammern, schneidet die schließende Klammer drumherum ab', () => {
        expect(links('https://de.wikipedia.org/wiki/Stahl_(Werkstoff)')[0].href).toBe('https://de.wikipedia.org/wiki/Stahl_(Werkstoff)');
        expect(zerlegeInLinks('(siehe https://example.com/plan)')).toEqual([
            { art: 'text', text: '(siehe ' },
            { art: 'link', text: 'https://example.com/plan', href: 'https://example.com/plan' },
            { art: 'text', text: ')' },
        ]);
    });

    it('ergänzt bei www. das https://', () => {
        expect(links('Infos unter www.example.com.')[0]).toEqual({
            art: 'link', text: 'www.example.com', href: 'https://www.example.com',
        });
    });

    it('erkennt http und E-Mail-Adressen', () => {
        expect(links('http://example.com')[0].href).toBe('http://example.com');
        expect(links('Schreiben Sie an max.mustermann@example.com.')[0]).toEqual({
            art: 'link', text: 'max.mustermann@example.com', href: 'mailto:max.mustermann@example.com',
        });
    });

    it('verlinkt keine gefährlichen oder halben Adressen', () => {
        expect(links('javascript:alert(1)')).toEqual([]);
        expect(links('data:text/html,<script>alert(1)</script>')).toEqual([]);
        expect(links('https://')).toEqual([]);
        expect(links('www.')).toEqual([]);
        expect(links('foo.www.example.com')).toEqual([]);
        expect(links('ftp://example.com')).toEqual([]);
    });

    it('lässt HTML im Text als Text stehen', () => {
        const stuecke = zerlegeInLinks('<img src=x onerror=alert(1)> https://example.com');
        expect(stuecke[0]).toEqual({ art: 'text', text: '<img src=x onerror=alert(1)> ' });
        expect(stuecke[1]).toMatchObject({ art: 'link', href: 'https://example.com' });
    });

    it('verträgt sehr lange Zeichenketten ohne Leerzeichen', () => {
        const lang = 'a'.repeat(200_000);
        const start = performance.now();
        expect(zerlegeInLinks(lang)).toEqual([{ art: 'text', text: lang }]);
        expect(zerlegeInLinks(`${'@'.repeat(50_000)}${'a'.repeat(50_000)}`)).toHaveLength(1);
        expect(zerlegeInLinks(`https://${'a'.repeat(100_000)}.com`)).toHaveLength(1);
        // Viele Adressen am Stück und ein langer Schwanz aus Klammern bzw. Satzzeichen.
        expect(links(`https://a.example.com/${')'.repeat(2020)} `.repeat(500))).toHaveLength(500);
        expect(links(`https://a.example.com/x${'.)'.repeat(1000)} `.repeat(100))).toHaveLength(100);
        expect(zerlegeInLinks(`https://a.example.com/${'('.repeat(1000)}${')'.repeat(1000)}`).length).toBeGreaterThan(0);
        expect(performance.now() - start).toBeLessThan(500);
    });

    it('bleibt bei vielen abgebrochenen Kandidaten linear', () => {
        for (const text of [':www.'.repeat(200_000), ',http://'.repeat(125_000)]) {
            const start = performance.now();
            zerlegeInLinks(text);
            expect(performance.now() - start).toBeLessThan(400);
        }
    });

    it('verlinkt Adressen über 2048 Zeichen gar nicht statt abgeschnitten', () => {
        const lang = `https://www.example.com/${'a'.repeat(3000)}`;
        expect(zerlegeInLinks(`siehe ${lang} bitte`)).toEqual([{ art: 'text', text: `siehe ${lang} bitte` }]);
        const passend = `https://www.example.com/${'a'.repeat(2000)}`;
        expect(links(`siehe ${passend}.`)[0].href).toBe(passend);
    });
});
