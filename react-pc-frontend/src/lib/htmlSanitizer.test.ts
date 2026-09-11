import { describe, it, expect } from 'vitest';
import { stripHtmlTags, unescapeHtmlEntities, toSafeResourceUrl } from './htmlSanitizer';

describe('htmlSanitizer', () => {
    describe('stripHtmlTags', () => {
        it('entfernt einfache HTML-Tags', () => {
            expect(stripHtmlTags('<p>Hallo <b>Welt</b></p>')).toBe('Hallo Welt');
        });

        it('beseitigt geschachtelte Tags vollständig (Fixpunkt gegen Multicharacter-Sanitization)', () => {
            expect(stripHtmlTags('<<script>script>alert(1)</script>')).toBe('alert(1)');
            expect(stripHtmlTags('<scr<script>ipt>bad</scr</script>ipt>')).toBe('bad');
        });

        it('akzeptiert Ersatzzeichen wie Leerzeichen', () => {
            expect(stripHtmlTags('<h1>Titel</h1><p>Absatz</p>', ' ').replace(/\s+/g, ' ').trim()).toBe('Titel Absatz');
        });

        it('behandelt leere Eingaben sauber', () => {
            expect(stripHtmlTags('')).toBe('');
        });
    });

    describe('unescapeHtmlEntities', () => {
        it('wandelt Standard-HTML-Entities um', () => {
            expect(unescapeHtmlEntities('&lt;div&gt; &amp; &#39;Test&#39; &quot;Name&quot;&nbsp;Hier')).toBe(
                '<div> & \'Test\' "Name" Hier'
            );
        });

        it('verhindert Double-Unescaping (&amp;lt; bleibt &lt; und wird nicht zu <)', () => {
            expect(unescapeHtmlEntities('&amp;lt;')).toBe('&lt;');
            expect(unescapeHtmlEntities('&amp;gt;')).toBe('&gt;');
            expect(unescapeHtmlEntities('&amp;amp;')).toBe('&amp;');
        });
    });

    describe('toSafeResourceUrl', () => {
        it('erlaubt sichere blob:-URLs', () => {
            const blobUrl = 'blob:http://localhost:5173/00000000-0000-0000-0000-000000000000';
            expect(toSafeResourceUrl(blobUrl)).toBe(blobUrl);
        });

        it('erlaubt sichere absolute HTTP(S)-URLs', () => {
            expect(toSafeResourceUrl('https://example.com/test.pdf')).toBe('https://example.com/test.pdf');
            expect(toSafeResourceUrl('http://example.com/test.png')).toBe('http://example.com/test.png');
        });

        it('erlaubt relative Pfade', () => {
            expect(toSafeResourceUrl('/api/dokumente/123')).toBe('/api/dokumente/123');
        });

        it('blockiert bösartige Schemata wie javascript: oder data:', () => {
            expect(toSafeResourceUrl('javascript:alert(1)')).toBe('');
            expect(toSafeResourceUrl('JAVASCRIPT:alert(1)')).toBe('');
            expect(toSafeResourceUrl('data:text/html,<script>alert(1)</script>')).toBe('');
            expect(toSafeResourceUrl('vbscript:msgbox(1)')).toBe('');
        });

        it('blockiert protocol-relative URLs (//evil.com)', () => {
            expect(toSafeResourceUrl('//evil.com/phishing')).toBe('');
        });

        it('behandelt leere oder ungültige Werte sicher', () => {
            expect(toSafeResourceUrl('')).toBe('');
            expect(toSafeResourceUrl(null)).toBe('');
            expect(toSafeResourceUrl(undefined)).toBe('');
            expect(toSafeResourceUrl('   ')).toBe('');
        });
    });
});
