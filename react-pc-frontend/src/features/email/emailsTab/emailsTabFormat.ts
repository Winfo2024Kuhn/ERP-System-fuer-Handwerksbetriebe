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

/** Untertitel des Verlaufs, z. B. „2 Nachrichten im Verlauf, 1 Entwurf“. Entwürfe zählen nicht als Nachricht. */
export function verlaufAnzahl(eintraege: ReadonlyArray<{ isDraft?: boolean }>): string {
    const entwuerfe = eintraege.filter(e => e.isDraft).length;
    const nachrichten = eintraege.length - entwuerfe;
    const text = `${nachrichten} ${nachrichten === 1 ? 'Nachricht' : 'Nachrichten'} im Verlauf`;
    return entwuerfe ? `${text}, ${entwuerfe} ${entwuerfe === 1 ? 'Entwurf' : 'Entwürfe'}` : text;
}

const BEZEICHNUNG_MAX = 40;

/**
 * Wie ein Entwurf in Meldungen heißt: „Entwurf „Betreff““, sonst mit dem Textanfang,
 * sonst nur „Entwurf“. Lange Texte werden mit „…“ gekürzt.
 */
export function entwurfBezeichnung(entwurf: { subject?: string; snippet?: string } | undefined): string {
    const betreff = entwurf?.subject?.trim();
    const anfang = entwurf?.snippet?.trim();
    const text = betreff || (anfang && anfang !== '[Entwurf]' ? anfang : '');
    if (!text) return 'Entwurf';
    const kurz = text.length > BEZEICHNUNG_MAX ? `${text.slice(0, BEZEICHNUNG_MAX).trimEnd()}…` : text;
    return `Entwurf „${kurz}“`;
}
