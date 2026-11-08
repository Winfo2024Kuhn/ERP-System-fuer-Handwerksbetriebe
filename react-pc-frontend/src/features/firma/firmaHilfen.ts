// Kleine reine Hilfen der Seite „Firma“.

/** Jahre für die Jahr-Auswahl: aktuelles Jahr plus Lohn- und BWA-Jahre, neueste zuerst. */
export function jahreZusammenfuehren(aktuellesJahr: number, lohnJahre: unknown, bwaJahre: unknown): number[] {
    const jahre = new Set<number>([aktuellesJahr]);
    if (Array.isArray(lohnJahre)) lohnJahre.forEach((y: number) => jahre.add(y));
    if (Array.isArray(bwaJahre)) bwaJahre.forEach((y: number) => jahre.add(y));
    return Array.from(jahre).sort((a, b) => b - a);
}

/** `filter` ist 'ALL' oder die id eines Steuerberaters. */
export const lohnabrechnungenUrl = (filter: string, jahr: number): string => filter !== 'ALL'
    ? `/api/lohnabrechnungen/steuerberater/${filter}/jahr/${jahr}`
    : `/api/lohnabrechnungen/jahr/${jahr}`;

/** Betrag mit zwei Nachkommastellen, ohne Währungszeichen (z. B. „12.345,60“). */
export const formatiereBetrag = (betrag: number): string =>
    betrag.toLocaleString('de-DE', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

/**
 * Meldung aus dem Text einer Fehlerantwort: `message` aus JSON, sonst `ersatz`;
 * ist der Text kein auswertbares JSON, entscheidet `beiKeinemJson`.
 */
export function meldungAusFehlertext(text: string, ersatz: string, beiKeinemJson: (text: string) => string): string {
    try {
        const json = JSON.parse(text);
        return json.message || ersatz;
    } catch {
        return beiKeinemJson(text);
    }
}
