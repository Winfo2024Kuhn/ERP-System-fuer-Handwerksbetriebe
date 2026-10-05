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

    it('hides everything after an Outlook header, because Outlook always top-posts', () => {
        const header = '<b>Von:</b> test@example.com<br><b>Gesendet:</b> 08.09.2026<br><b>An:</b> mail@example.com<br><b>Betreff:</b> Termin';
        for (const attributes of ['id="divRplyFwdMsg"', 'id="x_divRplyFwdMsg"', 'style="border-top:1px solid gray"']) {
            const html = `<p>Neue Antwort</p><div ${attributes}>${header}</div><div>Vorherige Nachricht<p>Alte Signatur</p></div><p>Noch ältere Nachricht</p>`;
            expect(getThreadPreview(html)).toBe('Neue Antwort');
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

    describe('real-world reply formats (structure from the mailbox, dummy data)', () => {
        const signature = '<p>Mit freundlichen Grüßen</p><p>Max Mustermann</p><p>Musterfirma GmbH</p>';
        const outlookHeader = (tag = 'div') => `<${tag}><b>Von:</b> Musterfirma &lt;info@example.com&gt;<br>
            <b>Gesendet:</b> Dienstag, 23. Juni 2026 17:23<br><b>An:</b> kunde@example.org<br>
            <b>Betreff:</b> AW: Angebot</${tag}>`;

        it('new Outlook / OWA: hr + header div + loose sibling history (multi-level replies)', () => {
            const html = `<div>Hallo Erika,</div><div>erste grobe Zeitschiene steht.</div><div id="Signature">${signature}</div>
                <hr style="display: inline-block; width: 98%;">${outlookHeader()}<div><br></div>
                <div>Hallo Erika,</div><div>anbei der Auftrag.</div><div id="x_Signature">${signature}</div>
                <hr style="display: inline-block; width: 98%;"><div id="x_divRplyFwdMsg">${outlookHeader()}</div>
                <div>Sehr geehrter Herr Mustermann, im Anhang das Angebot.</div><table><tbody><tr><td>Alte Signatur</td></tr></tbody></table>`;
            expect(getThreadPreview(html)).toBe('Hallo Erika, erste grobe Zeitschiene steht. Mit freundlichen Grüßen Max Mustermann Musterfirma GmbH');
            const { doc, button } = collapse(html);
            expect(doc.querySelectorAll('[data-quote-btn]')).toHaveLength(1);
            expect(button.previousElementSibling?.id).toBe('Signature');
            const hidden = button.getAttribute('aria-controls')!.split(' ').map(id => doc.getElementById(id)!);
            expect(hidden.every(element => element.hidden)).toBe(true);
            expect(hidden.map(element => element.textContent).join(' ')).toContain('anbei der Auftrag');
            expect(hidden.map(element => element.textContent).join(' ')).toContain('Alte Signatur');
        });

        it('classic Outlook: header inside a border-top box within WordSection1', () => {
            const html = `<div class="WordSection1"><p class="MsoNormal">Passt so.</p>
                <div style="border:none;border-top:solid #E1E1E1 1.0pt;padding:3.0pt 0cm 0cm 0cm">
                <p class="MsoNormal"><b>Von:</b> Max Mustermann &lt;max@example.com&gt;<br><b>Gesendet:</b> Montag, 7. September 2026 09:12<br>
                <b>An:</b> info@example.com<br><b>Betreff:</b> Termin</p></div><p class="MsoNormal">Alter Text</p></div>`;
            expect(getThreadPreview(html)).toBe('Passt so.');
        });

        it('Outlook mobile: border-style solid none none with only three header lines', () => {
            const html = `<div>Danke!</div><div style="padding: 3pt 0in 0in; border-width: 1pt medium medium; border-style: solid none none;">
                <div class="ms-outlook-mobile-reference-message"><b>Von: </b>max@example.com<br><b>Gesendet: </b>Freitag, 4. September 2026 11:30<br>
                <b>Betreff: </b>Treppe</div></div><div>Alter Text</div>`;
            expect(getThreadPreview(html)).toBe('Danke!');
        });

        it('Samsung / Android: "-------- Ursprüngliche Nachricht --------" separator', () => {
            const html = `<div dir="auto">Vielen Dank für die prompte Erledigung.</div><div id="composer_signature">Von meinem Smartphone gesendet</div>
                <div align="left"><div>-------- Ursprüngliche Nachricht --------</div><div>Von: Musterfirma &lt;info@example.com&gt;</div>
                <div>Datum: 04.09.26 11:30 (GMT+01:00)</div><div>An: max@example.com</div><div>Betreff: Treppe</div><div><br></div>
                <div>Alter Text</div></div>`;
            expect(getThreadPreview(html)).toBe('Vielen Dank für die prompte Erledigung. Von meinem Smartphone gesendet');
        });

        it('Telekom webmail: separate <p> lines after "-----Original-Nachricht-----"', () => {
            const html = `<p>Hallo, passt.</p><p>&nbsp;</p><p>-----Original-Nachricht-----</p><p>Betreff: Neues Balkongeländer</p>
                <p>Datum: 2026-08-25T11:38:01+0200</p><p>Von: "Musterfirma" &lt;info@example.com&gt;</p><p>An: max@example.com</p><p>Alter Text</p>`;
            expect(getThreadPreview(html)).toBe('Hallo, passt.');
        });

        it('web.de / GMX: sub-body-container with Gesendet/Von/An/Betreff', () => {
            const html = `<div>Gerne, bis Montag.</div><div id="sub-body-container" style="margin: 10px 5px 5px 10px; border-left: 2px solid rgb(195, 217, 229);">
                <div style="margin: 0px 0px 10px;"><div><strong>Gesendet: </strong>Dienstag, 6. Januar 2026 um 11:44</div>
                <div><strong>Von: </strong>info@example.com</div><div><strong>An: </strong>max@example.com</div>
                <div><strong>Betreff: </strong>Angebot</div></div><div>Alter Text</div></div>`;
            expect(getThreadPreview(html)).toBe('Gerne, bis Montag.');
        });

        it('plain text: Outlook underscore line and header lines', () => {
            const text = 'Passt.\n\nGruß Max\n\n________________________________\nVon: info@example.com\nGesendet: Montag, 7. September 2026 09:12\nBetreff: Termin\n\nAlter Text';
            expect(getThreadPreview(text)).toBe('Passt. Gruß Max');
            const { doc, button } = collapse(`<pre>${text}</pre>`);
            expect(button).not.toBeNull();
            expect(doc.body.textContent).toContain('Gruß Max');
        });

        it('keeps forwarded content visible', () => {
            const thunderbird = '<p>Bitte nochmal ausdrucken</p><div class="moz-forward-container">-------- Weitergeleitete Nachricht --------<table><tr><th>Betreff:</th><td>Rechnung</td></tr><tr><th>Datum:</th><td>Mon, 3 Aug 2026</td></tr><tr><th>Von:</th><td>info@example.com</td></tr><tr><th>An:</th><td>max@example.com</td></tr></table><p>Rechnungstext</p></div>';
            expect(getThreadPreview(thunderbird)).toContain('Rechnungstext');
            const gmail = '<div>Zur Info</div><div class="gmail_quote"><div class="gmail_attr">---------- Forwarded message ---------<br>Von: info@example.com</div><div>Weitergeleiteter Text</div></div>';
            expect(getThreadPreview(gmail)).toContain('Weitergeleiteter Text');
            const outlookForward = `<p>Bitte prüfen</p>${outlookHeader()}<p>Weitergeleiteter Text</p>`;
            expect(getThreadPreview(outlookForward, { keepForwardedContent: true })).toContain('Weitergeleiteter Text');
            expect(getThreadPreview(outlookForward)).toBe('Bitte prüfen');
        });

        it('never collapses a header without new text before it (e.g. contact forms, forwards without comment)', () => {
            const form = '<p>Von: Max Mustermann</p><p>Datum: 05.10.2026</p><p>An: info@example.com</p><p>Betreff: Anfrage Geländer</p><p>Nachricht: Bitte um Angebot</p>';
            expect(getThreadPreview(form)).toContain('Bitte um Angebot');
            expect(collapse(form).doc.querySelector('[data-quote-btn]')).toBeNull();
        });

        it('keeps a contact form readable when only the intro precedes an incomplete header', () => {
            const html = '<p>Neue Nachricht über das Kontaktformular:</p><p>Von: Max Mustermann</p><p>Betreff: Geländer</p><p>Nachricht: Bitte um Angebot</p>';
            expect(getThreadPreview(html)).toContain('Bitte um Angebot');
        });

        it('needs a separator when the header has only three lines', () => {
            const html = '<p>Neu</p><p>Von: max@example.com</p><p>Datum: heute</p><p>Betreff: Frage</p><p>Weiter im Text</p>';
            expect(getThreadPreview(html)).toBe('Neu Von: max@example.com Datum: heute Betreff: Frage Weiter im Text');
        });

        it('hides table rows in place instead of wrapping them in an invalid div', () => {
            const html = `<table><tbody><tr><td>Neue Antwort</td></tr><tr><td>${outlookHeader('p')}</td></tr><tr><td>Alter Text</td></tr></tbody></table>`;
            const { doc, button, resize } = collapse(html);
            const rows = doc.querySelectorAll('tr');
            expect(rows[0].hidden).toBe(false);
            expect(rows[2].hidden).toBe(true);
            expect(doc.querySelector('tbody > div, tbody > button')).toBeNull();
            expect(button.closest('td')).not.toBeNull();
            button.click();
            expect(rows[2].hidden).toBe(false);
            expect(rows[2].style.display).toBe('');
            expect(resize).toHaveBeenCalledOnce();
        });

        it('keeps quotes before the Outlook history and drops quotes inside it', () => {
            const html = `<p>Antwort</p><blockquote type="cite">Zitat oben</blockquote><p>Nachtrag</p>${outlookHeader()}<blockquote type="cite">Zitat im Verlauf</blockquote>`;
            const { doc } = collapse(html);
            expect(doc.querySelectorAll('[data-quote-btn]')).toHaveLength(2);
            expect(getThreadPreview(html)).toBe('Antwort Nachtrag');
        });
    });
});
