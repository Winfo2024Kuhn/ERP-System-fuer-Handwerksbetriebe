import { extractDisplayName, extractEmailAddress, formatRecipient, parseRecipientList } from '../../lib/emailAddress';
import { htmlZuEinzeiler } from '../../lib/htmlSanitizer';
import type { PostfachKurz } from './postfach';

/** Anhang-Daten aus der Backend-API (UnifiedEmailDto.AttachmentDto). */
export interface EmailAttachment {
    id: number;
    originalFilename?: string;
    filename?: string;
    storedFilename?: string;
    mimeType?: string;
    fileSize?: number;
    contentId?: string;
    inline?: boolean;
}

/** Prüft ob ein Dateiname auf ein gängiges Bildformat hindeutet. */
export function isImageAttachment(filename: string): boolean {
    return /\.(jpg|jpeg|png|gif|webp|bmp|svg)$/i.test(filename);
}


export interface EmailItem {
    id: number;
    type: string;
    containerId?: number;
    direction: 'IN' | 'OUT';
    subject?: string;
    sender?: string;
    fromAddress?: string;
    body?: string;
    htmlBody?: string;
    sentAt?: string;
    attachments?: EmailAttachment[];
    replies?: EmailItem[];
    zuordnungTyp?: string;
    projektName?: string;
    /** Auftragsnummer des zugeordneten Projekts, falls vergeben. */
    projektAuftragsnummer?: string | null;
    anfrageName?: string;
    lieferantName?: string;
    kundeName?: string;
    isRead?: boolean;
    recipient?: string;
    cc?: string;
    /** Reply-To-Kopfzeile: Antworten gehen dorthin statt an fromAddress. */
    replyToAddress?: string;
    spamScore?: number;
    // Assignment IDs
    projektId?: number;
    anfrageId?: number;
    lieferantId?: number;
    kundeId?: number;
    // Computed folder from backend
    folder?: FolderType;
    // Thread-Informationen
    parentEmailId?: number;   // null/undefined = Thread-Wurzel
    /** Wurzel des gesamten Verlaufs (Backend) – auch wenn Zwischenglieder nicht geladen sind. */
    threadRootId?: number;
    replyCount?: number;      // Anzahl weiterer Nachrichten im Verlauf
    /** Backend-berechnete jüngste Aktivität im gesamten Thread (für Sortierung). */
    threadLastActivityAt?: string;
    isStarred?: boolean;
    /** Postfächer, in denen die Mail liegt (Backend liefert nie null, ggf. []). */
    postfaecher?: PostfachKurz[];
    /** Postfach, über das Antworten und Weiterleitungen fest rausgehen. */
    antwortPostfach?: PostfachKurz | null;
}

/** Gespeicherter E-Mail-Entwurf aus `/api/emails/drafts`. */
export interface DraftItem {
    id: number;
    recipient?: string;
    cc?: string;
    subject?: string;
    body?: string;
    fromAddress?: string;
    replyEmailId?: number;
    weitergeleitetVonEmailId?: number | null;
    projektId?: number;
    anfrageId?: number;
    updatedAt?: string;
    /** Anzeigename des Benutzers, der den Entwurf angelegt hat; `null` bei älteren Entwürfen ohne Besitzer. */
    erstelltVon?: string | null;
}

/**
 * Name des fremden Besitzers eines Entwurfs – nur wenn er nicht der eigene ist.
 * Normale Benutzer sehen ohnehin nur ihre Entwürfe; Admins sehen alle und
 * erkennen so, wem ein Entwurf gehört. Ohne Besitzer (ältere Entwürfe) oder
 * ohne bekannten eigenen Namen: `null`.
 */
export function fremderEntwurfVon(draft: Pick<DraftItem, 'erstelltVon'>, eigenerName: string | null | undefined): string | null {
    const besitzer = draft.erstelltVon?.trim();
    if (!besitzer) return null;
    const ich = eigenerName?.trim();
    if (!ich) return null;
    return besitzer.toLocaleLowerCase('de-DE') === ich.toLocaleLowerCase('de-DE') ? null : besitzer;
}

/**
 * Filtert Entwürfe nach Betreff, Empfänger, Absender und Text (ohne Groß-/Kleinschreibung).
 * Der Text ist HTML: gesucht wird im Klartext, damit Tags und Entitäten nicht
 * mittreffen und Wörter aus verschiedenen Absätzen getrennt bleiben.
 */
export function filtereEntwuerfe<T extends DraftItem>(drafts: T[], suchbegriff: string): T[] {
    const q = suchbegriff.trim().toLowerCase();
    if (!q) return drafts;
    return drafts.filter(draft =>
        draft.subject?.toLowerCase().includes(q) ||
        draft.recipient?.toLowerCase().includes(q) ||
        draft.fromAddress?.toLowerCase().includes(q) ||
        htmlZuEinzeiler(draft.body).toLowerCase().includes(q)
    );
}

// Folder Types
export type FolderType = 'inbox' | 'sent' | 'drafts' | 'trash' | 'spam' | 'newsletter' | 'starred' | 'projects' | 'offers' | 'suppliers' | 'tax-advisors' | 'unassigned';

/** Lese-Filter über der E-Mail-Liste. */
export type ReadFilter = 'all' | 'unread' | 'read';

/**
 * Filtert die Liste serverseitig auf ungelesene Mails? Nur bei „Ungelesen" und
 * nur in Ordnern mit Lese-Konzept – Gesendet und Entwürfe haben keins.
 * „Gelesen" bleibt bewusst ein Filter über die geladenen Mails.
 */
export function nutztServerFilterUngelesen(folder: FolderType, readFilter: ReadFilter): boolean {
    return readFilter === 'unread' && folder !== 'sent' && folder !== 'drafts';
}

/**
 * Baut die URL einer Ordnerliste. Bei `nurUngelesen` liefert das Backend nur
 * ungelesene Mails; offset/limit gelten dann NACH dem Filter.
 */
export function buildEmailListUrl(folder: FolderType, offset: number, limit: number, nurUngelesen: boolean): string {
    // Entwürfe haben eine eigene Liste; fällt hier nur zur Sicherheit auf den Posteingang zurück.
    const pfad = folder === 'drafts' ? 'inbox' : folder;
    const params = new URLSearchParams({ offset: String(offset), limit: String(limit) });
    if (nurUngelesen) params.set('nurUngelesen', 'true');
    return `/api/emails/${pfad}?${params.toString()}`;
}

/**
 * Schlüssel für den Ordner-Zwischenspeicher. Gefilterte und ungefilterte Liste
 * liegen getrennt, damit nie die eine als die andere angezeigt wird.
 */
export function emailListCacheKey(folder: FolderType, nurUngelesen: boolean): string {
    return nurUngelesen ? `${folder}|unread` : folder;
}



export const getSenderName = (email: EmailItem) => {
    // Explizit zugeordneter Lieferant gewinnt – das ist ein bewusst gesetzter FK.
    if (email.lieferantId && email.lieferantName) return email.lieferantName;
    if (email.kundeName) return email.kundeName;
    if (email.lieferantName) return email.lieferantName;
    if (email.projektName) return email.projektName;
    if (email.anfrageName) return email.anfrageName;
    if (email.fromAddress) return extractDisplayName(email.fromAddress);
    return email.sender || 'Unbekannt';
};

export const getRecipientName = (email: EmailItem) => extractDisplayName(email.recipient);

/**
 * Anzeigename einer Mail in der Liste. `isOwnAddress` erkennt die eigenen Absender
 * (Adressen aus /api/emails/absender-postfaecher); ohne sie gilt keine Adresse als eigene.
 */
export const getDisplayName = (email: EmailItem, isOwnAddress: (address: string) => boolean = () => false) => {
    if (email.kundeName) return email.kundeName;
    if (email.lieferantName) return email.lieferantName;
    if (email.projektName) return email.projektName;
    if (email.anfrageName) return email.anfrageName;

    if (email.direction === 'OUT') {
        const parsed = parseRecipientList(email.recipient);
        if (parsed.length > 1) {
            return `${parsed[0].displayName} (+${parsed.length - 1})`;
        }
        return getRecipientName(email);
    }

    // Bei eingehenden E-Mails: Wenn fromAddress eine eigene Firmenadresse ist (z.B. versehentliche Selbst-Antwort),
    // lieber den Kunden-Empfänger anzeigen
    if (isOwnAddress(extractEmailAddress(email.fromAddress).toLowerCase()) && email.recipient) {
        const recip = getRecipientName(email);
        if (recip && recip !== 'Unbekannt') return recip;
    }

    return getSenderName(email);
};


/** Eine Zeile der Mail-Liste: alle geladenen Nachrichten eines Verlaufs. */
export interface EmailThreadRow {
    /** Jüngste geladene Nachricht des Verlaufs – sie steht stellvertretend in der Liste. */
    email: EmailItem;
    /** Alle geladenen Nachrichten des Verlaufs, älteste zuerst. */
    members: EmailItem[];
    /** Zeitpunkt der jüngsten Aktivität im gesamten Verlauf (ms), auch außerhalb des Ordners. */
    latestActivity: number;
    anyUnread: boolean;
}

const toMs = (iso?: string) => (iso ? new Date(iso).getTime() || 0 : 0);

/**
 * Fasst die geladenen Mails eines Ordners zu Verläufen zusammen – eine Zeile pro Verlauf.
 *
 * Maßgeblich ist die Wurzel aus dem Backend (`threadRootId`). Nur ältere Antworten ohne
 * dieses Feld werden über die geladene `parentEmailId`-Kette zugeordnet. So bleibt ein
 * Verlauf auch dann eine Zeile, wenn ein Zwischenglied in einem anderen Ordner liegt
 * (z. B. die eigene Antwort unter "Gesendet") oder erst auf einer späteren Seite kommt.
 */
export function groupEmailThreads(emails: EmailItem[]): EmailThreadRow[] {
    const byId = new Map(emails.map(email => [email.id, email]));
    const threadKey = (start: EmailItem): number => {
        if (start.threadRootId) return start.threadRootId;
        let current = start;
        const seen = new Set<number>();
        while (current.parentEmailId) {
            seen.add(current.id);
            const parent = byId.get(current.parentEmailId);
            if (!parent) break;
            if (parent.threadRootId) return parent.threadRootId;
            // Kaputte Ring-Verknüpfung: alle Glieder landen in derselben Zeile.
            if (seen.has(parent.id)) return Math.min(...seen);
            current = parent;
        }
        return current.id;
    };

    const rows = new Map<number, EmailThreadRow>();
    for (const email of emails) {
        const key = threadKey(email);
        const activity = Math.max(toMs(email.sentAt), toMs(email.threadLastActivityAt));
        const row = rows.get(key);
        if (!row) {
            rows.set(key, { email, members: [email], latestActivity: activity, anyUnread: !email.isRead });
            continue;
        }
        row.members.push(email);
        row.latestActivity = Math.max(row.latestActivity, activity);
        row.anyUnread = row.anyUnread || !email.isRead;
        const newer = toMs(email.sentAt) - toMs(row.email.sentAt);
        if (newer > 0 || (newer === 0 && email.id > row.email.id)) row.email = email;
    }
    for (const row of rows.values()) {
        row.members.sort((a, b) => toMs(a.sentAt) - toMs(b.sentAt) || a.id - b.id);
    }
    return Array.from(rows.values());
}

export type ReplyMode = 'reply' | 'replyAll';

export interface ReplyAddressing {
    to: string;
    cc: string[];
}

type ReplySource = Pick<EmailItem, 'direction' | 'fromAddress' | 'replyToAddress' | 'recipient' | 'cc'>
    & Partial<Pick<EmailItem, 'kundeName' | 'lieferantName'>>;

/**
 * Empfänger einer Antwort.
 *
 * - Antworten: an Reply-To bzw. den Absender. Auf eine eigene gesendete Mail an deren
 *   externe Empfänger – nie an uns selbst.
 * - Allen antworten: zusätzlich alle übrigen An-Empfänger (An) und CC-Empfänger (CC).
 *   Wer uns die Mail nur in CC geschickt hat, bekommt so trotzdem alle Beteiligten.
 *
 * Eigene Adressen und doppelte Adressen fliegen immer raus.
 */
export function buildReplyAddressing(email: ReplySource, mode: ReplyMode,
    isOwnAddress: (address: string) => boolean): ReplyAddressing {
    const seen = new Set<string>();
    const take = (raw: string, nameOverride?: string): string | null => {
        const address = extractEmailAddress(raw).toLowerCase();
        if (!address || !address.includes('@') || seen.has(address) || isOwnAddress(address)) return null;
        seen.add(address);
        return formatRecipient(raw, nameOverride) || raw;
    };
    const entries = (list?: string) => parseRecipientList(list).map(entry => entry.raw);
    const ownMail = email.direction === 'OUT' || isOwnAddress(extractEmailAddress(email.fromAddress));

    const to: string[] = [];
    if (ownMail) {
        entries(email.recipient).forEach(raw => { const r = take(raw); if (r) to.push(r); });
    } else if (email.replyToAddress?.trim()) {
        entries(email.replyToAddress).forEach(raw => { const r = take(raw); if (r) to.push(r); });
    } else if (email.fromAddress) {
        const parsed = parseRecipientList(email.fromAddress)[0];
        const ownName = parsed && parsed.displayName !== parsed.email ? parsed.displayName : undefined;
        // Kunden-/Lieferantenname nur für den Absender selbst – nie für andere Beteiligte.
        const r = take(email.fromAddress, ownName ?? email.kundeName ?? email.lieferantName);
        if (r) to.push(r);
    }

    const cc: string[] = [];
    if (mode === 'replyAll') {
        if (!ownMail) entries(email.recipient).forEach(raw => { const r = take(raw); if (r) to.push(r); });
        entries(email.cc).forEach(raw => { const r = take(raw); if (r) cc.push(r); });
    }
    return { to: to.join(', '), cc };
}

/** `true`, wenn "Allen antworten" mehr Empfänger erreicht als "Antworten". */
export function hasFurtherRecipients(email: ReplySource, isOwnAddress: (address: string) => boolean): boolean {
    const single = buildReplyAddressing(email, 'reply', isOwnAddress);
    const all = buildReplyAddressing(email, 'replyAll', isOwnAddress);
    return all.cc.length > 0 || all.to !== single.to;
}

const REPLY_PREFIX = /^\s*(?:re|aw|antw|antwort)\s*:/i;
const FORWARD_PREFIX = /^\s*(?:fwd?|wg)\s*:/i;

/** Betreff einer Antwort – vorhandene Präfixe (AW:, RE:, Re:) werden nicht verdoppelt. */
export function buildReplySubject(subject?: string): string {
    const clean = (subject || '').trim();
    return REPLY_PREFIX.test(clean) ? clean : `Re: ${clean}`;
}

/** Betreff einer Weiterleitung – vorhandene Präfixe (WG:, Fwd:, FW:) werden nicht verdoppelt. */
export function buildForwardSubject(subject?: string): string {
    const clean = (subject || '').trim();
    return FORWARD_PREFIX.test(clean) ? clean : `Fwd: ${clean}`;
}

/**
 * Fehlermeldung nach dem Löschen: eine einzelne Mail mit Server-Begründung (wenn vorhanden),
 * mehrere als „n von m“, damit klar ist, dass ein Teil durchging.
 */
export function loeschFehlerMeldung(fehlgeschlagen: number, gesamt: number, serverMeldung?: string | null): string {
    const grund = serverMeldung?.trim() || '';
    if (gesamt <= 1) return grund || 'Die E-Mail konnte nicht gelöscht werden.';
    const zahl = `${fehlgeschlagen} von ${gesamt} E-Mails konnten nicht gelöscht werden.`;
    return grund ? `${zahl} ${grund}` : zahl;
}

/**
 * Anhänge der Original-Mail, die beim Weiterleiten mitgehen können. Eingebettete Bilder
 * (z. B. das Logo in der Signatur) stehen im Text der Mail und werden nicht angeboten.
 */
export function weiterleitbareAnhaenge(anhaenge: EmailAttachment[] | null | undefined): EmailAttachment[] {
    return (anhaenge ?? []).filter(anhang => anhang.inline !== true);
}
