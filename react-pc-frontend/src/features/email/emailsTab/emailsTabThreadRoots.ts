import type { EmailsTabFilter, GenericEmail } from './emailsTabTypes';

/** Wurzel eines Verlaufs mit der Zahl der Antworten darunter. */
export type ThreadRootEmail = GenericEmail & { _replyCount: number };

const parentIdOf = (e: GenericEmail) => e.parentEmailId ?? e.parentId;

/** Zählt die verschachtelten Antworten aus dem Feld `replies`. */
function countReplies(email: GenericEmail): number {
    if (!email.replies || email.replies.length === 0) return 0;
    let count = email.replies.length;
    for (const r of email.replies) {
        count += countReplies(r);
    }
    return count;
}

/** Läuft vom Vorgänger aus bis zur obersten Mail der Liste; ein Kreis bricht ab. */
function findRootId(startId: number, emailMap: Map<number, GenericEmail>): number {
    let rootId = startId;
    const visited = new Set<number>();
    while (emailMap.has(rootId)) {
        const parent = emailMap.get(rootId)!;
        const ppid = parentIdOf(parent);
        if (ppid == null || !emailMap.has(ppid) || visited.has(ppid)) break;
        visited.add(rootId);
        rootId = ppid;
    }
    return rootId;
}

/**
 * Fasst eine flache Mailliste zu Verläufen zusammen: Wurzeln sind Mails ohne Vorgänger
 * oder mit einem Vorgänger, der nicht in der Liste steht.
 */
export function buildThreadRoots(emails: GenericEmail[]): ThreadRootEmail[] {
    if (!emails) return [];
    const emailMap = new Map<number, GenericEmail>();
    for (const e of emails) emailMap.set(e.id, e);

    const replyCounts = new Map<number, number>();
    for (const e of emails) {
        const pid = parentIdOf(e);
        if (pid != null) {
            const rootId = findRootId(pid, emailMap);
            replyCounts.set(rootId, (replyCounts.get(rootId) || 0) + 1);
        }
    }

    const roots = emails.filter(e => {
        const pid = parentIdOf(e);
        return pid == null || !emailMap.has(pid);
    });

    return roots.map(root => ({
        ...root,
        // Prefer server-provided replyCount (counts full thread chain regardless of entity assignment).
        // Fall back to client-side flat-list traversal for older endpoints that don't send replyCount.
        _replyCount: (root.replyCount != null && root.replyCount > 0)
            ? root.replyCount
            : (replyCounts.get(root.id) || 0) + countReplies(root),
    }));
}

/** Filtert die Verläufe nach Richtung und Suchtext (Betreff, Absender, Text). */
export function filterThreadRoots(roots: ThreadRootEmail[], filter: EmailsTabFilter, search: string): ThreadRootEmail[] {
    return roots.filter(email => {
        if (filter === 'in' && email.direction !== 'IN') return false;
        if (filter === 'out' && email.direction !== 'OUT') return false;
        if (search.trim()) {
            const needle = search.toLowerCase();
            const subject = (email.subject || '').toLowerCase();
            const from = (email.from || email.fromAddress || email.sender || '').toLowerCase();
            const body = (email.bodyHtml || email.bodyPreview || email.body || '').toLowerCase();
            if (!subject.includes(needle) && !from.includes(needle) && !body.includes(needle)) {
                return false;
            }
        }
        return true;
    });
}
