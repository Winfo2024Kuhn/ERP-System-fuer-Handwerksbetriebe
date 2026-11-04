/** Verlauf einer E-Mail, 1:1 mit dem Backend-DTO (`/api/emails/{id}/thread`). */

export interface ThreadAttachment {
    id: number;
    originalFilename: string;
    mimeType?: string;
    sizeBytes?: number;
    contentId?: string;
    inline?: boolean;
}

export interface EmailThreadEntry {
    id: number;
    subject?: string;
    fromAddress?: string;
    recipient?: string;
    cc?: string;
    /** Reply-To-Kopfzeile: Antworten gehen dorthin statt an fromAddress. */
    replyToAddress?: string;
    sentAt?: string;
    direction: 'IN' | 'OUT';
    forwarded?: boolean;
    isDraft?: boolean;
    draftId?: number;
    snippet?: string;
    htmlBody?: string;
    /** Nur bei direction 'OUT': 'OFFEN' = kein Fehler bekannt, 'UNZUSTELLBAR' = kam nicht an. */
    zustellStatus?: 'OFFEN' | 'UNZUSTELLBAR';
    /** Grund der Ablehnung, z.B. "unknown user / Teilnehmer existiert nicht". */
    zustellFehler?: string;
    attachments: ThreadAttachment[];
}

export interface EmailThread {
    rootEmailId: number;
    focusedEmailId: number;
    emails: EmailThreadEntry[];
}
