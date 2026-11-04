import { describe, expect, it, vi } from 'vitest';
import { collapseThreadQuotes, getThreadPreview, type ThreadQuoteOptions } from './threadQuotes';

/**
 * Formats that still showed history in the mailbox analysis (Nov. 2026).
 * Same structure as the real mails, dummy data only.
 */
function collapse(html: string, options: ThreadQuoteOptions = {}) {
    const doc = new DOMParser().parseFromString(html, 'text/html');
    collapseThreadQuotes(doc, vi.fn(), options);
    const buttons = Array.from(doc.querySelectorAll<HTMLButtonElement>('[data-quote-btn]'));
    const hiddenText = buttons
        .flatMap(button => button.getAttribute('aria-controls')!.split(' '))
        .map(id => doc.getElementById(id)!.textContent)
        .join(' ');
    return { doc, buttons, hiddenText };
}

const gmailAttr = 'Max Mustermann &lt;max@example.com&gt; schrieb am Do., 9. Okt. 2026 um 10:00:';

describe('attribution lines of other mail clients', () => {
    it('Gmail single level: attribution and quote disappear together', () => {
        const html = `<div>Neue Antwort</div><div class="gmail_quote gmail_quote_container"><div class="gmail_attr">${gmailAttr}</div><blockquote class="gmail_quote">ALTE Mail</blockquote></div>`;
        expect(getThreadPreview(html)).toBe('Neue Antwort');
        const { buttons, hiddenText } = collapse(html);
        expect(buttons).toHaveLength(1);
        expect(hiddenText).toContain('schrieb am Do., 9. Okt. 2026');
    });

    it('WEB.DE / GMX app: attribution spread over text, link and text before the quote', () => {
        const html = '<div>Neue Antwort</div><div class="mail_android_quote">Am 09.10.26, 10:00 schrieb Max Mustermann &lt;<a href="mailto:max@example.com">max@example.com</a>&gt;:<blockquote class="gmail_quote">ALTE Mail</blockquote></div>';
        expect(getThreadPreview(html)).toBe('Neue Antwort');
        expect(collapse(html).hiddenText).toContain('Am 09.10.26, 10:00 schrieb Max Mustermann');
    });

    it('Yahoo / AOL: "hat … geschrieben:" before an unmarked blockquote', () => {
        for (const attr of [
            'Am Dienstag, 7. Oktober 2026 um 10:00:00 MESZ hat Max Mustermann &lt;max@example.com&gt; Folgendes geschrieben:',
            'Am 9. Okt. 2026, 10:00, hat Max Mustermann &lt;max@example.com&gt; geschrieben:',
        ]) {
            expect(getThreadPreview(`<div>Neue Antwort</div><div>${attr}</div><blockquote>ALTE Mail</blockquote>`)).toBe('Neue Antwort');
        }
    });

    it('webmail plain text: "datum - adresse schrieb:" before prefixed lines, later answers stay', () => {
        const text = 'Neue Antwort\n\n09.10.2026 10:00 - max@example.com schrieb:\n> ALTE Mail\nNachtrag';
        expect(getThreadPreview(text)).toBe('Neue Antwort Nachtrag');
    });
});

describe('Gmail multi-level quotes', () => {
    const html = `<div>Neue Antwort</div><div class="gmail_quote gmail_quote_container"><div class="gmail_attr">${gmailAttr}</div><blockquote class="gmail_quote"><div>ZWEITE Mail</div><div class="gmail_quote"><div class="gmail_attr">Erika Musterfrau &lt;erika@example.com&gt; schrieb am Mi., 8. Okt. 2026 um 09:00:</div><blockquote class="gmail_quote">ERSTE Mail</blockquote></div></blockquote></div>`;

    it('collapses the outer quote as a whole, including all older mails', () => {
        expect(getThreadPreview(html)).toBe('Neue Antwort');
        const { buttons, hiddenText } = collapse(html);
        expect(buttons).toHaveLength(1);
        expect(hiddenText).toContain('ZWEITE Mail');
        expect(hiddenText).toContain('ERSTE Mail');
    });

    it('still keeps inline answers inside a Gmail wrapper that is no blockquote', () => {
        const inline = `<div class="gmail_quote"><div class="gmail_attr">${gmailAttr}</div><blockquote class="gmail_quote">Frage</blockquote><div>Meine Antwort</div></div>`;
        expect(getThreadPreview(`<div>Neu</div>${inline}`)).toBe('Neu Meine Antwort');
    });
});

describe('forwarded mails with older history below', () => {
    const header = (from: string, date: string, to: string, subject: string) =>
        `<div><b>Von:</b> ${from}<br><b>Gesendet:</b> ${date}<br><b>An:</b> ${to}<br><b>Betreff:</b> ${subject}</div>`;
    const outlookForward = `<div>Zur Info</div><hr>${header('Max Mustermann &lt;max@example.com&gt;', 'Montag, 6. Oktober 2026 10:00', 'Erika Musterfrau', 'Angebot')}<div>WEITERGELEITETE Mail</div><hr>${header('Erika Musterfrau &lt;erika@example.com&gt;', 'Freitag, 3. Oktober 2026 09:00', 'Max Mustermann', 'Anfrage')}<div>ALTE Mail</div>`;

    it('Outlook: keeps the forwarded mail with its header and collapses from the next header on', () => {
        const preview = getThreadPreview(outlookForward, { keepForwardedContent: true });
        expect(preview).toContain('Zur Info');
        expect(preview).toContain('Betreff: Angebot');
        expect(preview).toContain('WEITERGELEITETE Mail');
        expect(preview).not.toContain('ALTE Mail');
        expect(preview).not.toContain('Anfrage');
        const { buttons, hiddenText } = collapse(outlookForward, { keepForwardedContent: true });
        expect(buttons).toHaveLength(1);
        expect(hiddenText).toContain('ALTE Mail');
    });

    it('plain text: "-----Ursprüngliche Nachricht-----" or an underscore line as second separator', () => {
        const forwarded = 'Zur Info\n\n________________________________\nVon: Max Mustermann <max@example.com>\nGesendet: Montag, 6. Oktober 2026 10:00\nAn: Erika Musterfrau\nBetreff: Angebot\n\nWEITERGELEITETE Mail\n\n';
        const olderHeader = 'Von: Erika Musterfrau <erika@example.com>\nGesendet: Freitag, 3. Oktober 2026 09:00\nAn: Max Mustermann\nBetreff: Anfrage\n\nALTE Mail';
        for (const separator of ['-----Ursprüngliche Nachricht-----\n', '________________________________\n']) {
            const preview = getThreadPreview(forwarded + separator + olderHeader, { keepForwardedContent: true });
            expect(preview).toContain('WEITERGELEITETE Mail');
            expect(preview).not.toContain('ALTE Mail');
        }
    });

    it('forward marker without flag: forwarded header and text stay, older history is collapsed', () => {
        const html = '<p>Zur Info</p><div class="moz-forward-container">-------- Weitergeleitete Nachricht --------<table><tr><th>Betreff:</th><td>Angebot</td></tr><tr><th>Datum:</th><td>06.10.2026</td></tr><tr><th>Von:</th><td>max@example.com</td></tr><tr><th>An:</th><td>erika@example.com</td></tr></table><p>WEITERGELEITETE Mail</p><p>-----Ursprüngliche Nachricht-----</p><p>Von: erika@example.com</p><p>Gesendet: 03.10.2026</p><p>An: max@example.com</p><p>Betreff: Anfrage</p><p>ALTE Mail</p></div>';
        const preview = getThreadPreview(html);
        expect(preview).toContain('Weitergeleitete Nachricht');
        expect(preview).toContain('WEITERGELEITETE Mail');
        expect(preview).not.toContain('ALTE Mail');
    });

    it('nested forwards: the forwarded customer request inside a forward stays visible', () => {
        const text = 'Bitte an Max\n\n---------- Forwarded message ---------\nVon: Erika Musterfrau <erika@example.com>\nDate: Mo., 6. Okt. 2026 um 10:00\nSubject: Fwd: Anfrage\nTo: chef@example.com\n\nBitte an Max weiter\n\n---------- Forwarded message ---------\nFrom: Max Mustermann <max@example.com>\nDate: Fr., 3. Okt. 2026 um 09:00\nSubject: Anfrage\nTo: erika@example.com\n\nKUNDENANFRAGE Geländer\n\n-----Ursprüngliche Nachricht-----\nVon: info@example.com\nGesendet: 01.10.2026\nAn: max@example.com\nBetreff: Alt\n\nALTE Mail';
        const preview = getThreadPreview(text, { keepForwardedContent: true });
        expect(preview).toContain('Bitte an Max weiter');
        expect(preview).toContain('KUNDENANFRAGE Geländer');
        expect(preview).not.toContain('ALTE Mail');
    });

    it('keeps everything visible when there is no second header', () => {
        const single = `<div>Zur Info</div><hr>${header('max@example.com', 'Montag, 6. Oktober 2026 10:00', 'erika@example.com', 'Angebot')}<div>WEITERGELEITETE Mail</div>`;
        expect(collapse(single, { keepForwardedContent: true }).buttons).toHaveLength(0);
    });

    it('needs forwarded text between the first header and the cut', () => {
        const twoHeaders = `<div>Zur Info</div>${header('max@example.com', '06.10.2026', 'erika@example.com', 'Angebot')}<hr>${header('erika@example.com', '03.10.2026', 'max@example.com', 'Anfrage')}<div>WEITERGELEITETE Mail</div>`;
        expect(collapse(twoHeaders, { keepForwardedContent: true }).buttons).toHaveLength(0);
    });

    it('also handles a forward without comment, header at the very top', () => {
        const noComment = `${header('max@example.com', '06.10.2026', 'erika@example.com', 'Angebot')}<div>WEITERGELEITETE Mail</div><hr>${header('erika@example.com', '03.10.2026', 'max@example.com', 'Anfrage')}<div>ALTE Mail</div>`;
        expect(getThreadPreview(noComment, { keepForwardedContent: true })).not.toContain('ALTE Mail');
        expect(getThreadPreview(noComment)).toContain('ALTE Mail');
    });
});

describe('trailing ">" lines without a recognised attribution', () => {
    it('t-online: collapses a closing block of prefixed lines after new text', () => {
        const html = '<pre>Neue Antwort\n\n&gt; Gesendet: Donnerstag, 09.10.2026 um 10:00 Uhr\n&gt; Von: "Max Mustermann" &lt;max@example.com&gt;\n&gt; Betreff: Anfrage\n&gt;\n&gt; ALTE Mail\n\n</pre>';
        expect(getThreadPreview(html)).toBe('Neue Antwort');
        const { buttons, hiddenText } = collapse(html);
        expect(buttons).toHaveLength(1);
        expect(hiddenText).toContain('ALTE Mail');
    });

    it('takes an unknown attribution-like intro line along', () => {
        expect(getThreadPreview('Neue Antwort\n\nmax@example.com hat geschrieben:\n> ALTE Mail\n> Gruß')).toBe('Neue Antwort');
    });

    it('keeps ">" in running text and ordinary intro sentences visible', () => {
        expect(getThreadPreview('<p>Bitte Angebot für Tor, Breite <b>&gt; 3 m</b></p>')).toBe('Bitte Angebot für Tor, Breite > 3 m');
        expect(getThreadPreview('<p>Maße: Breite <b>&gt; 3 m\n&gt; Höhe 2 m</b></p>')).toBe('Maße: Breite > 3 m > Höhe 2 m');
        expect(getThreadPreview('Hallo,\ndie Breite muss\n> 3 m sein')).toBe('Hallo, die Breite muss > 3 m sein');
        expect(getThreadPreview('Hallo Herr Mustermann,\n\nbitte so einbauen:\n> Abstand 12 cm'))
            .toBe('Hallo Herr Mustermann, bitte so einbauen: > Abstand 12 cm');
        expect(getThreadPreview('Hallo Herr Mustermann,\n\nbitte so einbauen:\n> Abstand 12 cm\n> Höhe 90 cm'))
            .toBe('Hallo Herr Mustermann, bitte so einbauen:');
    });

    it('stays fast with many empty inline elements around ">" text (no quadratic walk)', () => {
        // Unbounded walk costs (">" nodes × empty nodes) = 800 × 10,000, about 15 s;
        // the bounded walk only pays for parsing the ~12,000 nodes.
        const html = `<p>Neu</p><p>${'<i></i>'.repeat(10000)}${'&gt;<b></b>'.repeat(800)}</p>`;
        const started = performance.now();
        getThreadPreview(html);
        // Generous limit for busy CI runners.
        expect(performance.now() - started).toBeLessThan(5000);
    }, 30000);

    it('leaves inline answers and quotes without new text alone', () => {
        expect(getThreadPreview('> Frage 1\nAntwort 1\n> Frage 2')).toBe('> Frage 1 Antwort 1 > Frage 2');
        expect(getThreadPreview('Neu\n> Alt\nAntwort')).toBe('Neu > Alt Antwort');
        expect(getThreadPreview('> Nur ein Zitat')).toBe('> Nur ein Zitat');
        expect(getThreadPreview('<p>&gt; Frage</p><p>Antwort</p>')).toBe('> Frage Antwort');
    });
});

describe('Yahoo with prefixed class names', () => {
    it('recognises "ydp…yahoo_quoted" as a quote wrapper', () => {
        const html = '<div>Neue Antwort</div><div id="ydp1yahoo_quoted_1" class="ydp8a8a0641yahoo_quoted"><div>Am Dienstag, 7. Oktober 2026 um 10:00:00 MESZ hat Max Mustermann &lt;max@example.com&gt; Folgendes geschrieben:</div><div>ALTE Mail</div></div>';
        expect(getThreadPreview(html)).toBe('Neue Antwort');
        expect(collapse(html).buttons).toHaveLength(1);
    });
});

describe('display safety', () => {
    it('parses quoted history inertly', () => {
        const html = '<p>Neu</p><blockquote type="cite"><img src="x" onerror="window.__quoteXss = 1">Alt</blockquote>';
        expect(getThreadPreview(html)).toBe('Neu');
        expect((window as unknown as { __quoteXss?: number }).__quoteXss).toBeUndefined();
    });
});

describe('Gmail reply to a forward', () => {
    it('collapses a quoted forward inside a Gmail blockquote, the forward is old history there', () => {
        const html = `<div>Danke, erledigt.</div><div class="gmail_quote gmail_quote_container"><div class="gmail_attr">${gmailAttr}</div><blockquote class="gmail_quote">---------- Forwarded message ---------<br>Von: erika@example.com<div>WEITERGELEITETE Mail</div></blockquote></div>`;
        expect(getThreadPreview(html)).toBe('Danke, erledigt.');
    });
});
