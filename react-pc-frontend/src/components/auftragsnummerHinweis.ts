/**
 * Auftragsnummern folgen der Syntax YYYY/MM/NNNCC:
 * NNN = Kundennummer innerhalb des Jahres, CC = laufender Auftrag dieses Kunden im Jahr.
 *
 * Diese Datei liegt bewusst neben dem ProjektErstellenModal statt darin: eine
 * Komponenten-Datei darf nur Komponenten exportieren (react-refresh/only-export-components).
 */

/** Antwort von GET /api/projekte/naechste-auftragsnummer */
export interface NaechsteAuftragsnummerResponse {
    auftragsnummer: string;
    prefix: string;
    zaehler: number;
    /** true, wenn die Nummer der Kunden-Syntax YYYY/MM/NNNCC folgt */
    kundenLogik: boolean;
    /** true, wenn der Kunde in diesem Jahr noch keinen Auftrag hatte */
    neuerKundeImJahr: boolean;
    /** Kundennummer im Jahr (die mittleren drei Ziffern) */
    kundenSlot: number;
    /** Der wievielte Auftrag dieses Kunden im Jahr (1-basiert) */
    auftragImJahr: number;
}

/**
 * Erklärt in Handwerker-Sprache, wie die vorgeschlagene Auftragsnummer zustande kommt.
 * Ohne Kundenbezug (oder wenn das System auf die fortlaufende Nummer zurückfällt)
 * gibt es nichts zu erklären → null.
 */
export function baueAuftragsnummerHinweis(
    data: NaechsteAuftragsnummerResponse,
    kundeName?: string,
): string | null {
    if (!data.kundenLogik) return null;

    const jahr = data.prefix.split('/')[0];
    const name = kundeName?.trim() || 'Der Kunde';
    const kundennummer = String(data.kundenSlot).padStart(3, '0');

    if (data.neuerKundeImJahr) {
        return `${name} hat ${jahr} noch keinen Auftrag — bekommt die Kundennummer ${kundennummer} und damit den ersten Auftrag.`;
    }

    const bisher = data.auftragImJahr - 1;
    const bisherText = bisher === 1 ? 'einen Auftrag' : `${bisher} Aufträge`;
    return `${name} hatte ${jahr} schon ${bisherText} (Kundennummer ${kundennummer}) — das hier ist Auftrag Nummer ${data.auftragImJahr}.`;
}
