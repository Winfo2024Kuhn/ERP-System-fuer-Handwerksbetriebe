/**
 * Zahlen- und Dateinamen-Formatierung für das Report Centre (reine Funktionen).
 */

const EINE_NACHKOMMASTELLE = new Intl.NumberFormat('de-DE', { minimumFractionDigits: 1, maximumFractionDigits: 1 });

/** „152,0“, „1.234,5“ und Minus als echtes Minuszeichen („−3,5“). */
export function formatZahl(wert: number): string {
    // -0,04 würde sonst als „−0,0“ erscheinen.
    const gerundet = Math.round(wert * 10) / 10;
    return EINE_NACHKOMMASTELLE.format(gerundet === 0 ? 0 : gerundet).replace('-', '−');
}

/** Erlaubte Zeichen im Dateinamen; alles andere wird zu `_`. */
const UNSICHERE_ZEICHEN = /[^\p{L}\p{N}_.\- ]/gu;

/**
 * Dateiname aus dem `Content-Disposition`-Kopf (auch `filename*=UTF-8''…`).
 * Pfadteile und Sonderzeichen werden entfernt; ohne brauchbaren Namen gilt `ersatz`.
 */
export function dateinameAusDisposition(kopf: string | null, ersatz: string): string {
    if (!kopf) return ersatz;
    const kodiert = /filename\*=UTF-8''([^;]+)/i.exec(kopf)?.[1];
    let roh: string | undefined;
    if (kodiert) {
        try {
            roh = decodeURIComponent(kodiert);
        } catch {
            roh = undefined;
        }
    }
    roh ??= /filename="?([^";]+)"?/i.exec(kopf)?.[1];
    const name = roh?.split(/[\\/]/).pop()?.replace(UNSICHERE_ZEICHEN, '_').trim();
    return name && name !== '.' && name !== '..' ? name : ersatz;
}
