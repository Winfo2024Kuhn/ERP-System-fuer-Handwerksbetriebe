import { describe, it, expect } from 'vitest';
import {
    htmlToText,
    parseHtmlInert,
    sanitizeHtml,
    stripHtmlTags,
    toSafeResourceUrl,
    unescapeHtmlEntities,
} from './htmlSanitizer';

describe('htmlSanitizer', () => {
    describe('sanitizeHtml', () => {
        it('entfernt Skripte und Event-Handler', () => {
            const sauber = sanitizeHtml('<p>Hallo</p><img src="x" onerror="alert(1)"><script>alert(2)</script>');
            expect(sauber).toBe('<p>Hallo</p><img src="x">');
        });

        it('entfernt javascript:-Links, behaelt aber den Linktext', () => {
            const sauber = sanitizeHtml('<a href="javascript:alert(1)">Klick</a>');
            expect(sauber).not.toContain('javascript:');
            expect(sauber).toContain('Klick');
        });

        it('entfernt iframes und Formulare', () => {
            const sauber = sanitizeHtml('<iframe src="https://example.com"></iframe><form action="/x"><input></form>Text');
            expect(sauber).not.toMatch(/<iframe|<form/);
            expect(sauber).toContain('Text');
        });

        it('behaelt Richtext, Tabellen, class, style und data-Attribute', () => {
            const html =
                '<p><strong>Fett</strong> <em>kursiv</em></p>' +
                '<ul><li>Punkt</li></ul>' +
                '<table><tbody><tr><td style="color: red;">Zelle</td></tr></tbody></table>' +
                '<span class="chip" data-preview-placeholder="true">{{Kundenname}}</span>' +
                '<div class="email-signature" style="margin-top: 20px;">Max Mustermann</div>';
            expect(sanitizeHtml(html)).toBe(html);
        });

        it('behandelt leere Eingaben sauber', () => {
            expect(sanitizeHtml('')).toBe('');
            expect(sanitizeHtml(null)).toBe('');
            expect(sanitizeHtml(undefined)).toBe('');
        });
    });

    describe('parseHtmlInert', () => {
        it('parst in ein eigenes Dokument statt ins laufende', () => {
            const wrapper = parseHtmlInert('<img src="x" onerror="alert(1)"><p>Text</p>');
            expect(wrapper.ownerDocument).not.toBe(document);
            expect(wrapper.ownerDocument.defaultView).toBeNull();
            expect(wrapper.isConnected).toBe(false);
        });

        it('verhaelt sich wie ein div: querySelector, dataset, innerHTML', () => {
            const wrapper = parseHtmlInert(
                '<meta name="background-image" content="bild.png"><div data-block-type="TEXT" data-x="40">A</div><script>x()</script>',
            );
            // meta bleibt im div (bei einem vollen Dokument-Parse landete es im head)
            expect(wrapper.querySelector('meta[name="background-image"]')?.getAttribute('content')).toBe('bild.png');
            expect((wrapper.querySelector('[data-block-type]') as HTMLElement).dataset.x).toBe('40');
            wrapper.querySelectorAll('script').forEach(n => n.remove());
            expect(wrapper.innerHTML).not.toContain('<script');
        });

        it('entfernt noscript samt Inhalt (anderes Parsing ohne Fenster, mXSS)', () => {
            // Die eigentliche mXSS-Payload (</noscript> in einem Attribut) parst
            // jsdom anders als ein Browser; geprueft wird deshalb das Entfernen.
            const wrapper = parseHtmlInert('<p>vorher</p><noscript><p>x</p></noscript><p>nachher</p>');
            expect(wrapper.querySelector('noscript')).toBeNull();
            expect(wrapper.textContent).toBe('vorhernachher');
        });

        it('liefert bei leerer Eingabe ein leeres div', () => {
            expect(parseHtmlInert(null).innerHTML).toBe('');
            expect(parseHtmlInert(undefined).tagName).toBe('DIV');
        });
    });

    describe('htmlToText', () => {
        it('liefert den reinen Text', () => {
            expect(htmlToText('<p>Hallo <b>Welt</b></p><img src="x" onerror="alert(1)">')).toBe('Hallo Welt');
        });

        it('entpackt Entitaeten genau einmal', () => {
            expect(htmlToText('&lt;b&gt; &amp;lt;')).toBe('<b> &lt;');
        });

        it('behandelt leere Eingaben sauber', () => {
            expect(htmlToText('')).toBe('');
            expect(htmlToText(null)).toBe('');
        });
    });

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

        it('bereinigt auch mehr als 20 rekursiv geschachtelte Tag-Ebenen restlos bis zum Fixpunkt', () => {
            // 25-fach geschachteltes Tag-Konstrukt:
            let nested = 'alert(1)';
            for (let i = 0; i < 25; i++) {
                nested = `<script>${nested}</script>`;
            }
            expect(stripHtmlTags(nested)).toBe('alert(1)');
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
