/**
 * Hinweise, die der Server nach dem Stornieren einer Rechnung mitschickt
 * (POST /api/ausgangs-dokumente/{id}/storno, Feld `hinweise`).
 *
 * Hintergrund: Wird eine Abschlagsrechnung storniert, betrifft das die
 * Schlussrechnung. Eine gebuchte Schlussrechnung storniert der Server mit,
 * ein Entwurf bleibt stehen und zieht den stornierten Abschlag noch ab.
 * Beides soll der Nutzer sofort sehen, nicht nur im Server-Log.
 */
export type StornoHinweisArt = 'MITSTORNIERT' | 'ENTWURF_PRUEFEN';

export interface StornoHinweis {
    art: StornoHinweisArt;
    text: string;
}

type HinweisToast = Record<'warning' | 'info', (message: string, duration?: number) => void>;

/** Ein Entwurf muss vor dem Buchen angefasst werden – länger stehen lassen als ein reiner Hinweis. */
export const ENTWURF_PRUEFEN_DAUER_MS = 12000;
export const MITSTORNIERT_DAUER_MS = 8000;

/** Liest die Hinweise aus der Storno-Antwort; fehlt das Feld oder ist es kaputt, gibt es keine. */
export function stornoHinweiseAus(antwort: unknown): StornoHinweis[] {
    if (!antwort || typeof antwort !== 'object') return [];
    const hinweise = (antwort as { hinweise?: unknown }).hinweise;
    if (!Array.isArray(hinweise)) return [];
    return hinweise.filter(
        (h): h is StornoHinweis =>
            !!h &&
            typeof h === 'object' &&
            ((h as StornoHinweis).art === 'MITSTORNIERT' || (h as StornoHinweis).art === 'ENTWURF_PRUEFEN') &&
            typeof (h as StornoHinweis).text === 'string' &&
            (h as StornoHinweis).text.trim() !== '',
    );
}

/**
 * Zeigt jeden Hinweis als eigenen Toast: Entwurf prüfen als Warnung, Mitstorniert als Info.
 * Warnungen kommen zuletzt: Der Meldungsstapel ist niedrig und rollt zur neuesten Meldung,
 * so bleibt das, was der Nutzer noch tun muss, sichtbar.
 */
export function zeigeStornoHinweise(toast: HinweisToast, hinweise: StornoHinweis[]): void {
    const sortiert = [
        ...hinweise.filter(h => h.art !== 'ENTWURF_PRUEFEN'),
        ...hinweise.filter(h => h.art === 'ENTWURF_PRUEFEN'),
    ];
    for (const hinweis of sortiert) {
        if (hinweis.art === 'ENTWURF_PRUEFEN') {
            toast.warning(hinweis.text, ENTWURF_PRUEFEN_DAUER_MS);
        } else {
            toast.info(hinweis.text, MITSTORNIERT_DAUER_MS);
        }
    }
}
