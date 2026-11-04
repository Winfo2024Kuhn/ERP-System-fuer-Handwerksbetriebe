// Generic email type that works across different entities
export interface GenericEmail {
    id: number;
    /** Nur bei direction 'OUT': 'OFFEN' = kein Fehler bekannt, 'UNZUSTELLBAR' = kam nicht an. */
    zustellStatus?: 'OFFEN' | 'UNZUSTELLBAR';
    /** Grund der Ablehnung, z.B. "unknown user / Teilnehmer existiert nicht". */
    zustellFehler?: string;
    subject?: string;
    from?: string;
    fromAddress?: string;
    to?: string;
    /** Felder aus /api/emails/{id} (UnifiedEmailDto) */
    recipient?: string;
    cc?: string;
    replyToAddress?: string;
    bodyHtml?: string;
    bodyPreview?: string;
    body?: string;
    direction?: 'IN' | 'OUT';
    sentAt?: string;
    sentDate?: string;
    receivedDate?: string;
    attachments?: Array<{
        id?: number;
        originalFilename?: string;
        storedFilename?: string;
        filename?: string;  // From Kommunikation API
        url?: string;       // From Kommunikation API
        contentId?: string;
        inline?: boolean;
    }>;
    sender?: string;
    recipients?: string[];
    // Thread support
    parentEmailId?: number;
    parentId?: number;
    replyCount?: number;
    replies?: GenericEmail[];
}

/** Filter der Mailliste im Reiter: alle, nur Eingang, nur Ausgang. */
export type EmailsTabFilter = 'all' | 'in' | 'out';
