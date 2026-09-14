import { describe, expect, it, vi } from 'vitest';
import { collapseThreadQuotes, getThreadPreview } from './threadQuotes';

const appleHeader = 'Am 8. September 2026 16:18:29 MESZ schrieb Max Mustermann &lt;test@example.com&gt;:';

function collapse(html: string) {
    const doc = new DOMParser().parseFromString(html, 'text/html');
    const resize = vi.fn();
    collapseThreadQuotes(doc, resize);
    return { doc, resize, button: doc.querySelector('button')! };
}

describe('thread quote display', () => {
    it('collapses Apple attribution, previous text and signature together while retaining the current signature', () => {
        const html = `<p>Passt, danke.</p><p>Viele Grüße<br>Aktuelle Signatur</p><div>${appleHeader}</div><br><blockquote type="cite">Alte Antwort<p>Vorherige Signatur</p></blockquote><p>Nachtrag: Bitte morgen.</p>`;
        const { doc, button, resize } = collapse(html);
        expect(getThreadPreview(html)).toBe('Passt, danke. Viele Grüße Aktuelle Signatur Nachtrag: Bitte morgen.');
        expect(button.getAttribute('aria-expanded')).toBe('false');
        const original = doc.getElementById(button.getAttribute('aria-controls')!)!;
        expect(original.hidden).toBe(true);
        expect(original.textContent).toContain('Am 8. September');
        expect(original.textContent).toContain('Vorherige Signatur');
        button.click();
        expect(original.hidden).toBe(false);
        expect(button.getAttribute('aria-expanded')).toBe('true');
        expect(resize).toHaveBeenCalledOnce();
        button.click();
        expect(original.hidden).toBe(true);
        collapseThreadQuotes(doc, resize);
        expect(doc.querySelectorAll('button')).toHaveLength(1);
    });

    it('retains short current text before a cite and unrelated blockquotes', () => {
        expect(getThreadPreview('<p>Ja!</p><blockquote type="cite">Alter Text</blockquote>')).toBe('Ja!');
        expect(getThreadPreview('<p>Bitte so einbauen:</p><blockquote>Abstand 12 cm</blockquote>'))
            .toBe('Bitte so einbauen: Abstand 12 cm');
    });

    it('preserves inline replies inside Gmail wrappers outside their explicit quote', () => {
        const html = '<p>Aktueller Text</p><div class="gmail_quote"><div>On Sep 8, 2026, test@example.com wrote:</div><blockquote>Alt</blockquote><p>Meine Antwort danach</p></div>';
        expect(getThreadPreview(html)).toBe('Aktueller Text Meine Antwort danach');
    });

    it('collapses standalone Gmail, Yahoo and own quote wrappers including nested quotes', () => {
        for (const className of ['gmail_quote', 'yahoo_quoted', 'email-quote']) {
            const html = `<p>Neu</p><div class="${className}">Alt<div class="email-quote">Noch älter</div></div>`;
            expect(getThreadPreview(html)).toBe('Neu');
            expect(collapse(html).doc.querySelectorAll('button')).toHaveLength(1);
        }
    });

    it('preserves a bottom-posted answer after the Outlook body container', () => {
        const header = '<b>Von:</b> test@example.com<br><b>Gesendet:</b> 08.09.2026<br><b>An:</b> mail@example.com<br><b>Betreff:</b> Termin';
        for (const attributes of ['id="divRplyFwdMsg"', 'style="border-top:1px solid gray"']) {
            const html = `<p>Neue Antwort</p><div ${attributes}>${header}</div><div>Vorherige Nachricht<p>Alte Signatur</p></div><p>Neue Ergänzung</p>`;
            expect(getThreadPreview(html)).toBe('Neue Antwort Neue Ergänzung');
        }
    });

    it('does not classify ordinary separators or unstructured Outlook tails as quotes', () => {
        expect(getThreadPreview('<div style="border-top:1px solid gray">Von: Max</div><p>Neu</p>')).toBe('Von: Max Neu');
        expect(getThreadPreview('<div id="divRplyFwdMsg">Von: Max</div><p>Neu</p>')).toBe('Von: Max Neu');
    });

    it('cleans prefixed plain-text replies while preserving text between and after quoted lines', () => {
        const text = 'Passt, danke.\nAktuelle Signatur\nAm 8. September 2026 schrieb test@example.com:\n> Alter Text\n> Alte Signatur\nMeine Antwort\n> Zweite Frage\nZweite Antwort';
        expect(getThreadPreview(text)).toBe('Passt, danke. Aktuelle Signatur Meine Antwort Zweite Antwort');
    });

    it('does not guess that unmarked content after an attribution is old', () => {
        const text = 'Neu\nOn Sep 8, 2026, test@example.com wrote:\nEine neue Erklärung ohne Zitatmarkierung';
        expect(getThreadPreview(text)).toContain('Eine neue Erklärung ohne Zitatmarkierung');
        expect(getThreadPreview('Vorgabe:\n> 5 cm Abstand')).toBe('Vorgabe: > 5 cm Abstand');
    });

    it('supports a blank line after the attribution and HTML-escaped plain-text quotes', () => {
        expect(getThreadPreview('Neu\nAm 8. September 2026 schrieb test@example.com:\n\n> Alt\nAntwort'))
            .toBe('Neu Antwort');
        expect(getThreadPreview('<p>Neu</p><div>On Sep 8, 2026, test@example.com wrote:</div><br>&gt; Alt'))
            .toBe('Neu');
    });

    it('keeps literal special characters and omits active markup from previews', () => {
        expect(getThreadPreview('<p>Maße &lt; 12 cm &amp; 3 Stück</p><script>unsafe()</script><style>body{color:red}</style>'))
            .toBe('Maße < 12 cm & 3 Stück');
        expect(getThreadPreview('')).toBe('');
    });
});
