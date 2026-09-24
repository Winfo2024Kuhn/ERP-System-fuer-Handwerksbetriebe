import { describe, it, expect } from 'vitest';
import { escapeHtml, isLikelyPlainText } from './emailContentFrameUtils';

describe('isLikelyPlainText', () => {
    it('erkennt t-online/Hetzner Plain-Text-Mail mit \\n und ohne Tags', () => {
        const tonlineMail = [
            'Sehr geehrter Herr Mustermann,',
            '',
            'vielen Dank für Ihre Nachricht.',
            '',
            '> > Vorherige Mail wurde so zitiert',
            '> > über mehrere Zeilen.',
        ].join('\n');
        expect(isLikelyPlainText(tonlineMail)).toBe(true);
    });

    it('lehnt Gmail-HTML mit <div>/<br> ab', () => {
        const gmailHtml = '<div>Hallo<br/>Welt</div>\n<div class="gmail_quote">…</div>';
        expect(isLikelyPlainText(gmailHtml)).toBe(false);
    });

    it('lehnt pretty-printed Tabellen-HTML ab (Newlines zwischen <td>-Tags)', () => {
        const tableMail = '<table>\n<tr>\n<td>Spalte 1</td>\n<td>Spalte 2</td>\n</tr>\n</table>';
        expect(isLikelyPlainText(tableMail)).toBe(false);
    });

    it('lehnt einzeiligen Plain-Text ohne \\n ab', () => {
        expect(isLikelyPlainText('Eine kurze Zeile ohne Umbruch')).toBe(false);
    });

    it('lehnt leere/nullable Eingaben ab', () => {
        expect(isLikelyPlainText('')).toBe(false);
        expect(isLikelyPlainText(null)).toBe(false);
        expect(isLikelyPlainText(undefined)).toBe(false);
    });
});

describe('escapeHtml', () => {
    it('escaped die fünf relevanten HTML-Sonderzeichen', () => {
        expect(escapeHtml('<script>alert("xss")</script>'))
            .toBe('&lt;script&gt;alert(&quot;xss&quot;)&lt;/script&gt;');
    });

    it('escaped & zuerst, damit kein doppeltes Escaping entsteht', () => {
        expect(escapeHtml('Tom & Jerry < 3')).toBe('Tom &amp; Jerry &lt; 3');
    });

    it('lässt harmlosen Text mit Dummy-Daten unverändert', () => {
        expect(escapeHtml('Max Mustermann, 12345 Musterstadt')).toBe('Max Mustermann, 12345 Musterstadt');
    });
});
