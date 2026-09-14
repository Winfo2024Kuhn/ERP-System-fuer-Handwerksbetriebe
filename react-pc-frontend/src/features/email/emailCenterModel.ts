import { extractDisplayName, extractEmailAddress, parseRecipientList } from '../../lib/emailAddress';

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
    anfrageName?: string;
    lieferantName?: string;
    kundeName?: string;
    isRead?: boolean;
    recipient?: string;
    cc?: string;
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
    replyCount?: number;      // Anzahl direkter Antworten
    /** Backend-berechnete jüngste Aktivität im gesamten Thread (für Sortierung). */
    threadLastActivityAt?: string;
    isStarred?: boolean;
}

// Folder Types
export type FolderType = 'inbox' | 'sent' | 'drafts' | 'trash' | 'spam' | 'newsletter' | 'starred' | 'projects' | 'offers' | 'suppliers' | 'tax-advisors' | 'unassigned';




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

export const getDisplayName = (email: EmailItem) => {
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
    const fromClean = extractEmailAddress(email.fromAddress).toLowerCase();
    const isFromSelf = fromClean.includes('bauschlosserei') || fromClean.includes('t-online.de') || fromClean.includes('kuhn');
    if (isFromSelf && email.recipient) {
        const recip = getRecipientName(email);
        if (recip && recip !== 'Unbekannt') return recip;
    }

    return getSenderName(email);
};
