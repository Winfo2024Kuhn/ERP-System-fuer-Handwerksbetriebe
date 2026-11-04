import { afterEach, describe, expect, it, vi } from 'vitest';
import type { GenericEmail } from './emailsTabTypes';
import { buildReplyQuote, buildReplyRecipient, buildReplySubject } from './emailsTabReply';

/** Dummy-Daten (DSGVO): Max Mustermann, max@example.com. */
function mail(overrides: Partial<GenericEmail> = {}): GenericEmail {
    return {
        id: 1,
        subject: 'Anfrage Geländer',
        fromAddress: 'Max Mustermann <max@example.com>',
        direction: 'IN',
        sentAt: '2026-10-09T10:05:00',
        bodyHtml: '<p>Bitte um Angebot</p>',
        ...overrides,
    };
}

afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
});

describe('buildReplyQuote', () => {
    it('liefert nichts, solange keine Mail gewählt ist', () => {
        expect(buildReplyQuote(null)).toBeUndefined();
    });

    it('baut das Zitat mit „Am … schrieb …:“ und dem Text der Mail', () => {
        const quote = buildReplyQuote(mail())!;
        expect(quote).toContain('class="email-quote"');
        expect(quote).toContain('Am 09.10.2026, 10:05 Uhr schrieb Max Mustermann:');
        expect(quote).toContain('<p>Bitte um Angebot</p>');
    });

    it('lässt das Datum leer, wenn die Mail keins hat, und nimmt den Klartext, wenn HTML fehlt', () => {
        const quote = buildReplyQuote(mail({ sentAt: undefined, bodyHtml: undefined, body: 'Nur Text' }))!;
        expect(quote).toContain('Am  schrieb Max Mustermann:');
        expect(quote).toContain('Nur Text');
    });

    it('fällt auf from und sender zurück und kommt ohne Text aus', () => {
        expect(buildReplyQuote(mail({ fromAddress: undefined, from: 'Erika <erika@example.com>', bodyHtml: undefined }))!)
            .toContain('schrieb Erika:');
        expect(buildReplyQuote(mail({ fromAddress: undefined, sender: 'max@example.com' }))!)
            .toContain('schrieb max@example.com:');
    });

    it('entfernt script, style und link und maskiert den Absendernamen', () => {
        const onerror = vi.fn();
        vi.stubGlobal('emailsTabTestOnerror', onerror);
        const quote = buildReplyQuote(mail({
            fromAddress: '"Max & Söhne" <max@example.com>',
            bodyHtml: '<p>Hallo</p><script>window.emailsTabTestOnerror()</script>'
                + '<style>p{color:red}</style><link rel="stylesheet" href="x.css">'
                + '<img src=x onerror="window.emailsTabTestOnerror()">',
        }))!;
        expect(quote).not.toContain('<script');
        expect(quote).not.toContain('<style');
        expect(quote).not.toContain('<link');
        expect(quote).toContain('<p>Hallo</p>');
        expect(quote).toContain('schrieb Max &amp; Söhne:');
        // Das Zitat wird nur geparst, nicht angezeigt: kein Skript und kein onerror läuft dabei.
        expect(onerror).not.toHaveBeenCalled();
    });

    it('übernimmt den Text unverändert, wenn der Parser ausfällt', () => {
        vi.stubGlobal('DOMParser', class { parseFromString() { throw new Error('kaputt'); } });
        expect(buildReplyQuote(mail({ bodyHtml: '<p>roh</p><script>x</script>' }))!)
            .toContain('<p>roh</p><script>x</script>');
    });
});

describe('buildReplyRecipient', () => {
    it('ist leer, solange keine Mail gewählt ist', () => {
        expect(buildReplyRecipient(null)).toBe('');
    });

    it('antwortet bei einer eingegangenen Mail an den Absender', () => {
        expect(buildReplyRecipient(mail())).toContain('max@example.com');
    });

    it('nimmt die Reply-To-Adresse, wenn es eine gibt', () => {
        expect(buildReplyRecipient(mail({ replyToAddress: 'buero@example.com' }))).toBe('buero@example.com');
    });

    it('antwortet auf eine eigene gesendete Mail an deren Empfänger, nicht an uns selbst', () => {
        const to = buildReplyRecipient(mail({
            direction: 'OUT', fromAddress: 'firma@example.com', recipient: 'max@example.com',
        }));
        expect(to).toBe('max@example.com');
        expect(to).not.toContain('firma@example.com');
    });

    it('nutzt to oder die Empfängerliste, wenn recipient fehlt', () => {
        expect(buildReplyRecipient(mail({ direction: 'OUT', fromAddress: 'firma@example.com', to: 'max@example.com' })))
            .toBe('max@example.com');
        expect(buildReplyRecipient(mail({
            direction: 'OUT', fromAddress: undefined, from: undefined, sender: 'firma@example.com',
            recipients: ['max@example.com', 'erika@example.com'],
        }))).toContain('erika@example.com');
    });

    it('geht ohne Richtung von einer eingegangenen Mail aus und kommt ohne Absender aus', () => {
        expect(buildReplyRecipient(mail({ direction: undefined }))).toContain('max@example.com');
        expect(buildReplyRecipient(mail({ fromAddress: undefined }))).toBe('');
    });
});

describe('buildReplySubject', () => {
    it('setzt „AW: “ vor den Betreff', () => {
        expect(buildReplySubject(mail({ subject: 'Anfrage Geländer' }))).toBe('AW: Anfrage Geländer');
    });

    it('lässt einen Betreff mit AW: oder RE: unverändert', () => {
        expect(buildReplySubject(mail({ subject: 'AW: Anfrage' }))).toBe('AW: Anfrage');
        expect(buildReplySubject(mail({ subject: ' re: Anfrage' }))).toBe(' re: Anfrage');
    });

    it('nimmt „AW: “, wenn keine Mail oder kein Betreff da ist', () => {
        expect(buildReplySubject(null)).toBe('AW: ');
        expect(buildReplySubject(mail({ subject: undefined }))).toBe('AW: ');
    });
});
