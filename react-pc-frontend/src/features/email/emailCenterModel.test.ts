import { describe, expect, it } from 'vitest';
import {
    buildForwardSubject, buildReplyAddressing, buildReplySubject, getDisplayName, groupEmailThreads, hasFurtherRecipients,
    type EmailItem,
} from './emailCenterModel';

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
