/** Vollständige Mail aus `/api/emails/{id}`, Grundlage einer Antwort aus dem Verlauf. */
export interface GenericEmail {
    id: number;
    subject?: string;
    from?: string;
    fromAddress?: string;
    sender?: string;
    to?: string;
    recipient?: string;
    recipients?: string[];
    cc?: string;
    replyToAddress?: string;
    bodyHtml?: string;
    body?: string;
    direction?: 'IN' | 'OUT';
    sentAt?: string;
}

/** Wo der E-Mail-Reiter hängt; bestimmt die Adresse `/api/emails/reiter/{bezug}/{id}`. */
export type EmailsTabBezug = 'projekt' | 'anfrage' | 'kunde' | 'lieferant';

/** Filter der Mailliste im Reiter: alle, nur Eingang, nur Ausgang. */
export type EmailsTabFilter = 'all' | 'in' | 'out';

/**
 * Ein Verlauf in der Mailliste des Reiters. Der Server fasst die Antwortkette zusammen
 * und schickt die neueste passende Mail; `vorschau` rechnet der Reiter einmal je Seite aus.
 */
export interface EmailsTabEintrag {
    id: number;
    subject: string | null;
    fromAddress: string | null;
    recipient: string | null;
    direction: 'IN' | 'OUT';
    sentAt: string | null;
    /** Erste Zeilen der neuesten Nachricht ohne zitierten Verlauf. */
    vorschau: string;
    /** Nachrichten im Verlauf, mindestens 1. */
    nachrichtenAnzahl: number;
    mitAnhang: boolean;
    /** Nur bei Ausgang: 'OFFEN' = kein Fehler bekannt, 'UNZUSTELLBAR' = kam nicht an. */
    zustellStatus: 'OFFEN' | 'UNZUSTELLBAR' | null;
    /** Grund der Ablehnung, z. B. „unknown user“. */
    zustellFehler: string | null;
}
