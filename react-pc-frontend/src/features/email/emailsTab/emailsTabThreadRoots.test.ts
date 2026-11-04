import { describe, expect, it } from 'vitest';
import type { GenericEmail } from './emailsTabTypes';
import { buildThreadRoots, filterThreadRoots, type ThreadRootEmail } from './emailsTabThreadRoots';

/** Dummy-Daten (DSGVO): Max Mustermann, max@example.com. */
function mail(id: number, overrides: Partial<GenericEmail> = {}): GenericEmail {
    return { id, subject: `Anfrage ${id}`, fromAddress: 'max@example.com', direction: 'IN', ...overrides };
}

describe('buildThreadRoots', () => {
    it('zeigt Mails ohne Vorgänger als eigene Wurzel ohne Antworten', () => {
        const roots = buildThreadRoots([mail(1), mail(2)]);
        expect(roots.map(r => [r.id, r._replyCount])).toEqual([[1, 0], [2, 0]]);
    });

    it('liefert eine leere Liste, wenn keine Mails übergeben werden', () => {
        expect(buildThreadRoots(undefined as unknown as GenericEmail[])).toEqual([]);
        expect(buildThreadRoots([])).toEqual([]);
    });

    it('zählt Antworten über mehrere Stufen bis zur Wurzel (parentEmailId und parentId)', () => {
        const roots = buildThreadRoots([
            mail(1),
            mail(2, { parentEmailId: 1 }),
            mail(3, { parentId: 2 }),
            mail(4, { parentEmailId: 3 }),
        ]);
        expect(roots).toHaveLength(1);
        expect(roots[0].id).toBe(1);
        expect(roots[0]._replyCount).toBe(3);
    });

    it('macht eine Mail zur Wurzel, deren Vorgänger nicht in der Liste steht', () => {
        const roots = buildThreadRoots([mail(5, { parentEmailId: 99 }), mail(6, { parentEmailId: 5 })]);
        expect(roots.map(r => [r.id, r._replyCount])).toEqual([[5, 1]]);
    });

    it('bevorzugt die Antwortzahl vom Server vor der eigenen Zählung', () => {
        const roots = buildThreadRoots([mail(1, { replyCount: 7 }), mail(2, { parentEmailId: 1 })]);
        expect(roots[0]._replyCount).toBe(7);
    });

    it('zählt selbst, wenn der Server 0 Antworten meldet', () => {
        const roots = buildThreadRoots([mail(1, { replyCount: 0 }), mail(2, { parentEmailId: 1 })]);
        expect(roots[0]._replyCount).toBe(1);
    });

    it('zählt verschachtelte Antworten aus dem Feld replies mit', () => {
        const roots = buildThreadRoots([
            mail(1, { replies: [mail(10, { replies: [mail(11)] }), mail(12, { replies: [] })] }),
        ]);
        expect(roots[0]._replyCount).toBe(3);
    });

    it('hängt sich bei einem Kreis aus Vorgängern nicht auf', () => {
        // 1 → 2 → 3 → 1: keine echte Wurzel, keine Mail wird angezeigt, aber die Schleife endet.
        const roots = buildThreadRoots([
            mail(1, { parentEmailId: 3 }),
            mail(2, { parentEmailId: 1 }),
            mail(3, { parentEmailId: 2 }),
        ]);
        expect(roots).toEqual([]);
    });

    it('hängt sich bei einer Mail, die auf sich selbst zeigt, nicht auf', () => {
        expect(buildThreadRoots([mail(1, { parentEmailId: 1 })])).toEqual([]);
    });

    it('ändert die übergebenen Mails nicht', () => {
        const original = mail(1);
        buildThreadRoots([original]);
        expect(original).not.toHaveProperty('_replyCount');
    });
});

describe('filterThreadRoots', () => {
    const roots: ThreadRootEmail[] = [
        { ...mail(1, { subject: 'Geländer Treppe', direction: 'IN', from: 'Max Mustermann' }), _replyCount: 0 },
        { ...mail(2, { subject: 'Angebot Tor', direction: 'OUT', fromAddress: undefined, sender: 'buero@example.com' }), _replyCount: 0 },
        { ...mail(3, { subject: undefined, direction: 'IN', fromAddress: undefined, bodyPreview: 'Bitte um Rückruf' }), _replyCount: 0 },
        { ...mail(4, { subject: 'Ohne Text', direction: 'IN', fromAddress: undefined }), _replyCount: 0 },
        { ...mail(5, { subject: 'Body', body: 'Stahlträger HEB 200' }), _replyCount: 0 },
        { ...mail(6, { subject: 'Html', bodyHtml: '<p>Zaunfeld</p>' }), _replyCount: 0 },
    ];
    const ids = (list: ThreadRootEmail[]) => list.map(r => r.id);

    it('lässt bei „Alle“ und leerer Suche alles stehen', () => {
        expect(ids(filterThreadRoots(roots, 'all', ''))).toEqual([1, 2, 3, 4, 5, 6]);
        expect(ids(filterThreadRoots(roots, 'all', '   '))).toEqual([1, 2, 3, 4, 5, 6]);
    });

    it('filtert nach Eingang und Ausgang', () => {
        expect(ids(filterThreadRoots(roots, 'in', ''))).toEqual([1, 3, 4, 5, 6]);
        expect(ids(filterThreadRoots(roots, 'out', ''))).toEqual([2]);
    });

    it('sucht ohne Groß-/Kleinschreibung in Betreff, Absender und Text', () => {
        expect(ids(filterThreadRoots(roots, 'all', 'GELÄNDER'))).toEqual([1]);
        expect(ids(filterThreadRoots(roots, 'all', 'mustermann'))).toEqual([1]);
        expect(ids(filterThreadRoots(roots, 'all', 'buero@'))).toEqual([2]);
        expect(ids(filterThreadRoots(roots, 'all', 'rückruf'))).toEqual([3]);
        expect(ids(filterThreadRoots(roots, 'all', 'heb 200'))).toEqual([5]);
        expect(ids(filterThreadRoots(roots, 'all', 'zaunfeld'))).toEqual([6]);
        expect(ids(filterThreadRoots(roots, 'all', 'max@example'))).toEqual([5, 6]);
    });

    it('kombiniert Richtung und Suche', () => {
        expect(ids(filterThreadRoots(roots, 'out', 'geländer'))).toEqual([]);
        expect(ids(filterThreadRoots(roots, 'in', 'nichts passt'))).toEqual([]);
    });
});
