import type { GenericEmail } from './emailsTabTypes';

/** Datum und Uhrzeit einer Mail für die Liste im E-Mail-Reiter, z. B. „09.10.2026, 10:05“. */
export function formatEmailDate(email: Pick<GenericEmail, 'sentAt' | 'receivedDate' | 'sentDate'>): string {
    const dateStr = email.sentAt || email.receivedDate || email.sentDate;
    if (!dateStr) return '-';
    try {
        return new Date(dateStr).toLocaleDateString('de-DE', {
            day: '2-digit', month: '2-digit', year: 'numeric',
            hour: '2-digit', minute: '2-digit'
        });
    } catch { return dateStr; }
}
