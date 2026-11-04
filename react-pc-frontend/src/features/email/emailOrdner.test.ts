import { describe, expect, it } from 'vitest';
import { Briefcase, Calculator, FileText, Inbox, MailQuestionMark, Newspaper, Package, Send, ShieldAlert, Trash2 } from 'lucide-react';
import { POSTFACH_ORDNER, VERSCHIEBE_ZIELE, ZUGEORDNET_ORDNER, ablageOrdner, ordnerSchildFuer } from './emailOrdner';

describe('ordnerSchildFuer', () => {
    it.each([
        ['inbox', 'Posteingang', Inbox, false],
        ['sent', 'Gesendet', Send, false],
        ['trash', 'Papierkorb', Trash2, true],
        ['spam', 'Spam', ShieldAlert, true],
        ['newsletter', 'Newsletter', Newspaper, false],
        ['projects', 'Projekt', Briefcase, false],
        ['offers', 'Anfrage', FileText, false],
        ['suppliers', 'Lieferant', Package, false],
        ['tax-advisors', 'Steuerberater', Calculator, false],
        ['inquiries', 'Mögliche Anfrage', MailQuestionMark, false],
    ] as const)('zeigt für „%s“ das Schild „%s“', (wert, label, icon, warnend) => {
        expect(ordnerSchildFuer(wert)).toEqual({ label, icon, warnend });
    });

    it.each([undefined, null, '', 'drafts', 'starred', 'unassigned', 'irgendwas', 'constructor'])('zeigt für „%s“ kein Schild', (wert) => {
        expect(ordnerSchildFuer(wert)).toBeNull();
    });
});

describe('Ordnerliste', () => {
    it('hält Seitenleiste und Schilder aus einer Quelle: gleiche Icons je Ordner', () => {
        for (const ordner of [...POSTFACH_ORDNER, ...ZUGEORDNET_ORDNER]) {
            const schild = ordnerSchildFuer(ordner.id);
            if (schild) expect(schild.icon).toBe(ordner.icon);
        }
    });

    it('bietet als Verschiebeziele nur die Ordner, auf die man Mails ziehen kann', () => {
        expect(VERSCHIEBE_ZIELE.map(ziel => ziel.id)).toEqual(['inbox', 'newsletter', 'spam', 'trash']);
    });
});

describe('ablageOrdner', () => {
    it('nimmt bei der Suche in allen Ordnern allein den Ordner der Mail', () => {
        expect(ablageOrdner({ folder: 'spam' }, true, 'trash')).toBe('spam');
        expect(ablageOrdner({ folder: 'inquiries' }, true, 'trash')).toBe('inquiries');
        expect(ablageOrdner({ folder: 'trash' }, true, 'inbox')).toBe('trash');
    });

    it('bildet einen fehlenden oder unbekannten Ordner nie auf den geöffneten ab', () => {
        expect(ablageOrdner({}, true, 'trash')).toBeNull();
        expect(ablageOrdner({ folder: 'irgendwo' as never }, true, 'trash')).toBeNull();
    });

    it('nimmt sonst den geöffneten Ordner', () => {
        expect(ablageOrdner({ folder: 'inbox' }, false, 'starred')).toBe('starred');
        expect(ablageOrdner({}, false, 'trash')).toBe('trash');
    });
});
