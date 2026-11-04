import { describe, expect, it } from 'vitest';
import { attributionBefore, isAttribution } from './threadQuoteAttribution';

function bodyOf(html: string): HTMLElement {
    return new DOMParser().parseFromString(html, 'text/html').body;
}

describe('isAttribution', () => {
    it.each([
        ['Apple Mail / Thunderbird', 'Am 8. September 2026 16:18:29 MESZ schrieb Max Mustermann <max@example.com>:'],
        ['English', 'On Sep 8, 2026, max@example.com wrote:'],
        ['Gmail (deutsch, neu)', 'Max Mustermann <max@example.com> schrieb am Do., 9. Okt. 2026 um 10:00:'],
        ['Gmail mit Komma statt "um"', 'Max Mustermann <max@example.com> schrieb am Fr., 10. Okt. 2026, 10:00:'],
        ['Gmail mit zwei Adressen', '"Max Mustermann" <max@example.com> <max@example.com> schrieb am Do., 9. Okt. 2026 um 10:00:'],
        ['Gmail (englisch, Name zuerst)', 'Max Mustermann <max@example.com> wrote on Thu, 9 Oct 2026 at 10:00:'],
        ['Yahoo', 'Am Dienstag, 7. Oktober 2026 um 10:00:00 MESZ hat Max Mustermann <max@example.com> Folgendes geschrieben:'],
        ['AOL', 'Am 9. Okt. 2026, 10:00, hat Max Mustermann <max@example.com> geschrieben:'],
        ['Webmail-Klartext', '09.10.2026 10:00 - max@example.com schrieb:'],
        ['WEB.DE/GMX-App', 'Am 09.10.26, 10:00 schrieb Max Mustermann <max@example.com>:'],
    ])('recognises %s', (_client, line) => {
        expect(isAttribution(line)).toBe(true);
    });

    it('normalises whitespace and ignores case', () => {
        expect(isAttribution('  am 8. September 2026\n schrieb max@example.com:  ')).toBe(true);
    });

    it.each([
        ['ordinary sentence', 'Bitte so einbauen:'],
        ['no digit and no address', 'Am Montag schrieb Max Mustermann:'],
        ['no colon at the end', 'Max Mustermann <max@example.com> schrieb am Do., 9. Okt. 2026 um 10:00'],
        ['Gmail format without address', 'Max Mustermann schrieb am Do., 9. Okt. 2026 um 10:00:'],
        ['plain "schrieb:" without address', 'Am 9.10. hat der Kunde Folgendes gefragt:'],
        ['too long', `Am 9. Oktober 2026 schrieb ${'x'.repeat(500)}:`],
    ])('rejects %s', (_case, line) => {
        expect(isAttribution(line)).toBe(false);
    });

    it('stays fast on long hostile input', () => {
        const hostile = `Am ${' schrieb am @'.repeat(5000)}`;
        const started = performance.now();
        expect(isAttribution(hostile)).toBe(false);
        // Generous limit: CI runners are busy. A regression would cost seconds, not milliseconds.
        expect(performance.now() - started).toBeLessThan(1000);
    });

    it('needs "hat" before "geschrieben", so ordinary sentences stay visible', () => {
        expect(isAttribution('Am 3. Oktober habe ich Ihnen geschrieben:')).toBe(false);
    });
});

describe('attributionBefore', () => {
    it('joins inline siblings (text, link, text) into one attribution line', () => {
        const body = bodyOf('<div>Am 09.10.26, 10:00 schrieb Max Mustermann &lt;<a href="mailto:max@example.com">max@example.com</a>&gt;:<blockquote>Alt</blockquote></div>');
        const nodes = attributionBefore(body.querySelector('blockquote')!);
        expect(nodes).not.toBeNull();
        expect(nodes!.map(node => node.textContent).join('')).toBe('Am 09.10.26, 10:00 schrieb Max Mustermann <max@example.com>:');
        expect(nodes).toHaveLength(3);
    });

    it('takes a preceding block element as the whole line and keeps spacers in the group', () => {
        const body = bodyOf('<div>Am 8. September 2026 schrieb max@example.com:</div><br><blockquote>Alt</blockquote>');
        const nodes = attributionBefore(body.querySelector('blockquote')!);
        expect(nodes?.map(node => node.nodeName)).toEqual(['DIV', 'BR']);
    });

    it('stops at a line break, so earlier lines never join the attribution', () => {
        const body = bodyOf('<div>Neue Antwort<br>Am 09.10.26, 10:00 schrieb <a href="mailto:max@example.com">max@example.com</a>:<blockquote>Alt</blockquote></div>');
        const nodes = attributionBefore(body.querySelector('blockquote')!);
        expect(nodes!.map(node => node.textContent).join('')).toBe('Am 09.10.26, 10:00 schrieb max@example.com:');
    });

    it('returns null when the preceding line is no attribution', () => {
        const body = bodyOf('<p>Bitte so einbauen:</p><blockquote>Abstand 12 cm</blockquote>');
        expect(attributionBefore(body.querySelector('blockquote')!)).toBeNull();
        expect(attributionBefore(bodyOf('<blockquote>Alt</blockquote>').firstChild!)).toBeNull();
    });

    it('gives up on very long inline runs instead of walking them (no quadratic cost)', () => {
        const body = bodyOf(`<div>Am 8. September 2026 schrieb max@example.com:${'<i></i>'.repeat(50)}<blockquote>Alt</blockquote></div>`);
        expect(attributionBefore(body.querySelector('blockquote')!)).toBeNull();
    });

    it('ignores comment text when building the line', () => {
        const body = bodyOf('<div>Am 8. September 2026 schrieb max@example.com:<!-- Notiz --><blockquote>Alt</blockquote></div>');
        expect(attributionBefore(body.querySelector('blockquote')!)).not.toBeNull();
    });
});
