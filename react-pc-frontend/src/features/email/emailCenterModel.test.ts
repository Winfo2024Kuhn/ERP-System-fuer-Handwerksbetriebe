import { describe, expect, it } from 'vitest';
import {
    buildEmailListUrl, buildForwardSubject, buildReplyAddressing, buildReplySubject, emailListCacheKey, filtereEntwuerfe, fremderEntwurfVon,
    getDisplayName, groupEmailThreads, hasFurtherRecipients, loeschFehlerMeldung, nutztServerFilterUngelesen,
    weiterleitbareAnhaenge, type DraftItem, type EmailItem,
} from './emailCenterModel';

describe('filtereEntwuerfe', () => {
    const entwuerfe: DraftItem[] = [
        {
            id: 1, subject: 'Angebot Treppe', recipient: 'max.mustermann@example.com', fromAddress: 'info@musterbetrieb.example',
            body: '<p>Maße für die</p><p><strong>Treppe</strong> &amp; Geländer</p>',
        },
        { id: 2, subject: 'Rechnung 2026-014', recipient: 'erika.musterfrau@example.com', body: '<p>Anbei die Rechnung.</p>' },
        { id: 3 },
    ];
    const ids = (suchbegriff: string) => filtereEntwuerfe(entwuerfe, suchbegriff).map(e => e.id);

    it('liefert ohne Suchbegriff alle Entwürfe', () => {
        expect(filtereEntwuerfe(entwuerfe, '   ')).toBe(entwuerfe);
    });

    it('findet über Betreff, Empfänger und Absender ohne Groß-/Kleinschreibung', () => {
        expect(ids('RECHNUNG 2026')).toEqual([2]);
        expect(ids('erika.musterfrau')).toEqual([2]);
        expect(ids('musterbetrieb')).toEqual([1]);
    });

    it('sucht im Klartext des HTML-Texts: Wörter über Absätze hinweg und entpackte Sonderzeichen', () => {
        expect(ids('die treppe')).toEqual([1]);
        expect(ids('treppe & geländer')).toEqual([1]);
    });

    it('trifft keine HTML-Tags oder Entitäten', () => {
        expect(ids('strong')).toEqual([]);
        expect(ids('&amp;')).toEqual([]);
    });
});

describe('Ordnerlisten: Filter „Ungelesen" auf dem Server', () => {
    it('filtert nur bei „Ungelesen" und nur in Ordnern mit Lese-Konzept serverseitig', () => {
        expect(nutztServerFilterUngelesen('inbox', 'unread')).toBe(true);
        expect(nutztServerFilterUngelesen('spam', 'unread')).toBe(true);
        expect(nutztServerFilterUngelesen('tax-advisors', 'unread')).toBe(true);
        expect(nutztServerFilterUngelesen('inbox', 'all')).toBe(false);
        expect(nutztServerFilterUngelesen('inbox', 'read')).toBe(false);
        expect(nutztServerFilterUngelesen('sent', 'unread')).toBe(false);
        expect(nutztServerFilterUngelesen('drafts', 'unread')).toBe(false);
    });

    it('hängt nurUngelesen=true nur bei Bedarf an die Ordner-URL', () => {
        expect(buildEmailListUrl('inbox', 0, 50, false)).toBe('/api/emails/inbox?offset=0&limit=50');
        expect(buildEmailListUrl('inbox', 50, 50, true)).toBe('/api/emails/inbox?offset=50&limit=50&nurUngelesen=true');
        expect(buildEmailListUrl('tax-advisors', 0, 120, true)).toBe('/api/emails/tax-advisors?offset=0&limit=120&nurUngelesen=true');
        expect(buildEmailListUrl('unassigned', 0, 50, false)).toBe('/api/emails/unassigned?offset=0&limit=50');
    });

    it('fällt für Entwürfe auf den Posteingang zurück wie bisher', () => {
        expect(buildEmailListUrl('drafts', 0, 50, false)).toBe('/api/emails/inbox?offset=0&limit=50');
    });

    it('legt gefilterte und volle Liste getrennt im Zwischenspeicher ab', () => {
        expect(emailListCacheKey('inbox', false)).toBe('inbox');
        expect(emailListCacheKey('inbox', true)).toBe('inbox|unread');
        expect(emailListCacheKey('inbox', true)).not.toBe(emailListCacheKey('inbox', false));
        expect(emailListCacheKey('spam', true)).not.toBe(emailListCacheKey('inbox', true));
    });
});

const OWN = ['info@musterbetrieb.example', 'buero@musterbetrieb.example'];
const isOwn = (address: string) => OWN.includes(address.toLowerCase());

function mail(id: number, overrides: Partial<EmailItem> = {}): EmailItem {
    return { id, type: 'email', direction: 'IN', isRead: true, sentAt: `2026-09-${String(id).padStart(2, '0')}T10:00:00`, ...overrides };
}

describe('groupEmailThreads', () => {
    it('groups by backend thread root even when the linking reply lives in another folder', () => {
        // Inbox: "Anfrage" (16.) and "Re: Anfrage" (17.). Our own answer in between lies in "Gesendet".
        const anfrage = mail(16, { subject: 'Anfrage', threadRootId: 16 });
        const antwort = mail(17, { subject: 'Re: Anfrage', parentEmailId: 99, threadRootId: 16, isRead: false });
        const rows = groupEmailThreads([antwort, anfrage]);
        expect(rows).toHaveLength(1);
        expect(rows[0].email.id).toBe(17);
        expect(rows[0].members.map(m => m.id)).toEqual([16, 17]);
        expect(rows[0].anyUnread).toBe(true);
    });

    it('falls back to the loaded parent chain for mails without threadRootId', () => {
        const rows = groupEmailThreads([mail(1), mail(2, { parentEmailId: 1 }), mail(3, { parentEmailId: 2 }), mail(4)]);
        expect(rows.map(r => r.members.map(m => m.id))).toEqual([[1, 2, 3], [4]]);
    });

    it('joins a legacy child to the backend root of its loaded parent', () => {
        const rows = groupEmailThreads([mail(5, { threadRootId: 1, parentEmailId: 1 }), mail(6, { parentEmailId: 5 })]);
        expect(rows).toHaveLength(1);
    });

    it('uses the newest activity of the whole thread and survives cycles', () => {
        const rows = groupEmailThreads([
            mail(1, { threadLastActivityAt: '2026-09-20T08:00:00', parentEmailId: 2 }),
            mail(2, { parentEmailId: 1 }),
        ]);
        expect(rows).toHaveLength(1);
        expect(rows[0].latestActivity).toBe(new Date('2026-09-20T08:00:00').getTime());
    });

    it('prefers the higher id as representative when two mails share a timestamp', () => {
        const rows = groupEmailThreads([
            mail(8, { threadRootId: 1, sentAt: '2026-09-01T10:00:00' }),
            mail(9, { threadRootId: 1, sentAt: '2026-09-01T10:00:00' }),
        ]);
        expect(rows[0].email.id).toBe(9);
        expect(groupEmailThreads([])).toEqual([]);
    });
});

describe('buildReplyAddressing', () => {
    const incoming = {
        direction: 'IN' as const,
        fromAddress: 'max.mustermann@example.com',
        recipient: 'info@musterbetrieb.example, erika.musterfrau@example.com',
        cc: 'Architekt Muster <architekt@example.org>, buero@musterbetrieb.example, MAX.MUSTERMANN@example.com',
        kundeName: 'Max Mustermann',
    };

    it('replies only to the sender, using the customer name', () => {
        expect(buildReplyAddressing(incoming, 'reply', isOwn)).toEqual({ to: '"Max Mustermann" <max.mustermann@example.com>', cc: [] });
    });

    it('reply-all keeps other To recipients in To and CC in CC, without own addresses and duplicates', () => {
        expect(buildReplyAddressing(incoming, 'replyAll', isOwn)).toEqual({
            to: '"Max Mustermann" <max.mustermann@example.com>, erika.musterfrau@example.com',
            cc: ['"Architekt Muster" <architekt@example.org>'],
        });
    });

    it('works when we only received the mail in CC', () => {
        const ccOnly = { direction: 'IN' as const, fromAddress: 'max@example.com', recipient: 'erika@example.com', cc: 'info@musterbetrieb.example' };
        expect(buildReplyAddressing(ccOnly, 'reply', isOwn).to).toBe('max@example.com');
        expect(buildReplyAddressing(ccOnly, 'replyAll', isOwn)).toEqual({ to: 'max@example.com, erika@example.com', cc: [] });
    });

    it('honours Reply-To and never uses the customer name for it', () => {
        const withReplyTo = { ...incoming, replyToAddress: 'auftraege@example.com' };
        expect(buildReplyAddressing(withReplyTo, 'reply', isOwn).to).toBe('auftraege@example.com');
    });

    it('answers our own sent mail to its external recipients including CC on reply-all', () => {
        const sent = {
            direction: 'OUT' as const, fromAddress: 'info@musterbetrieb.example',
            recipient: '"Mustermann, Max" <max@example.com>, buero@musterbetrieb.example', cc: 'erika@example.com',
        };
        expect(buildReplyAddressing(sent, 'reply', isOwn)).toEqual({ to: '"Mustermann, Max" <max@example.com>', cc: [] });
        expect(buildReplyAddressing(sent, 'replyAll', isOwn).cc).toEqual(['erika@example.com']);
    });

    it('returns an empty To when every recipient is our own address', () => {
        const toSelf = { direction: 'OUT' as const, fromAddress: 'info@musterbetrieb.example', recipient: 'buero@musterbetrieb.example' };
        expect(buildReplyAddressing(toSelf, 'replyAll', isOwn)).toEqual({ to: '', cc: [] });
        expect(buildReplyAddressing({ direction: 'IN' }, 'reply', isOwn)).toEqual({ to: '', cc: [] });
    });

    it('reports whether reply-all reaches more people', () => {
        expect(hasFurtherRecipients(incoming, isOwn)).toBe(true);
        expect(hasFurtherRecipients({ direction: 'IN', fromAddress: 'max@example.com', recipient: 'info@musterbetrieb.example' }, isOwn)).toBe(false);
    });
});

describe('reply and forward subjects', () => {
    it('does not stack prefixes, whatever the case or language', () => {
        expect(buildReplySubject('AW: Angebot')).toBe('AW: Angebot');
        expect(buildReplySubject('RE: Angebot')).toBe('RE: Angebot');
        expect(buildReplySubject('Angebot')).toBe('Re: Angebot');
        expect(buildReplySubject(undefined)).toBe('Re: ');
        expect(buildForwardSubject('WG: Rechnung')).toBe('WG: Rechnung');
        expect(buildForwardSubject('FW: Rechnung')).toBe('FW: Rechnung');
        expect(buildForwardSubject('Rechnung')).toBe('Fwd: Rechnung');
    });
});

describe('getDisplayName', () => {
    const selbstAntwort = mail(1, {
        fromAddress: 'Musterbetrieb GmbH <info@musterbetrieb.example>',
        recipient: 'Max Mustermann <max.mustermann@example.com>',
    });

    it('zeigt bei Mails von einem eigenen Absender den Empfänger', () => {
        expect(getDisplayName(selbstAntwort, isOwn)).toBe('Max Mustermann');
    });

    it('zeigt den Absender, wenn er kein eigener Absender ist', () => {
        const fremd = mail(2, {
            fromAddress: 'Erika Musterfrau <erika.musterfrau@t-online.de>',
            recipient: 'info@musterbetrieb.example',
        });
        expect(getDisplayName(fremd, isOwn)).toBe('Erika Musterfrau');
    });

    it('hält ohne bekannte eigene Absender keine Adresse für eigene', () => {
        expect(getDisplayName(selbstAntwort)).toBe('Musterbetrieb GmbH');
    });
});

describe('loeschFehlerMeldung', () => {
    it('nennt bei einer Mail den Grund vom Server oder einen eigenen Text', () => {
        expect(loeschFehlerMeldung(1, 1, 'Der Mailserver ist gerade nicht erreichbar.')).toBe('Der Mailserver ist gerade nicht erreichbar.');
        expect(loeschFehlerMeldung(1, 1, '  ')).toBe('Die E-Mail konnte nicht gelöscht werden.');
        expect(loeschFehlerMeldung(1, 1)).toBe('Die E-Mail konnte nicht gelöscht werden.');
    });

    it('zählt bei mehreren Mails „n von m“ und hängt den Grund an', () => {
        expect(loeschFehlerMeldung(2, 5, null)).toBe('2 von 5 E-Mails konnten nicht gelöscht werden.');
        expect(loeschFehlerMeldung(1, 2, 'Keine Berechtigung.')).toBe('1 von 2 E-Mails konnten nicht gelöscht werden. Keine Berechtigung.');
    });
});

describe('weiterleitbareAnhaenge', () => {
    it('lässt eingebettete Bilder weg und behält alles andere', () => {
        expect(weiterleitbareAnhaenge([
            { id: 1, originalFilename: 'Angebot.pdf', inline: false },
            { id: 2, originalFilename: 'Foto.jpg' },
            { id: 3, originalFilename: 'logo.png', inline: true },
        ]).map(a => a.id)).toEqual([1, 2]);
    });

    it('kommt ohne Anhänge mit einer leeren Liste zurück', () => {
        expect(weiterleitbareAnhaenge(undefined)).toEqual([]);
        expect(weiterleitbareAnhaenge(null)).toEqual([]);
    });
});

describe('fremderEntwurfVon', () => {
    it('nennt den Besitzer nur, wenn es nicht der eigene Entwurf ist', () => {
        expect(fremderEntwurfVon({ erstelltVon: 'Erika Musterfrau' }, 'Max Mustermann')).toBe('Erika Musterfrau');
        expect(fremderEntwurfVon({ erstelltVon: 'Max Mustermann' }, 'Max Mustermann')).toBeNull();
        expect(fremderEntwurfVon({ erstelltVon: ' max mustermann ' }, 'Max Mustermann')).toBeNull();
    });

    it('zeigt nichts bei älteren Entwürfen ohne Besitzer oder ohne eigenen Namen', () => {
        expect(fremderEntwurfVon({ erstelltVon: null }, 'Max Mustermann')).toBeNull();
        expect(fremderEntwurfVon({}, 'Max Mustermann')).toBeNull();
        expect(fremderEntwurfVon({ erstelltVon: '  ' }, 'Max Mustermann')).toBeNull();
        expect(fremderEntwurfVon({ erstelltVon: 'Erika Musterfrau' }, null)).toBeNull();
        expect(fremderEntwurfVon({ erstelltVon: 'Erika Musterfrau' }, '')).toBeNull();
    });
});
