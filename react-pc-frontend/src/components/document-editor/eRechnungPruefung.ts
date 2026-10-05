/**
 * Hilfen für den E-Rechnung-Export (ZUGFeRD): Der Server liefert bei fehlenden
 * Firmendaten eine lesbare Meldung (HTTP 422, Feld "message"). Die soll der
 * Nutzer sehen, statt eines allgemeinen "fehlgeschlagen".
 */

/** Liest "message" aus einer Fehlerantwort des Servers; sonst der übergebene Ersatztext. */
export async function fehlermeldungAusAntwort(response: Response, ersatz: string): Promise<string> {
    try {
        const body: unknown = await response.json();
        if (body && typeof body === 'object' && 'message' in body) {
            const message = (body as { message?: unknown }).message;
            if (typeof message === 'string' && message.trim() !== '') {
                return message;
            }
        }
    } catch {
        // Kein JSON im Fehlerkörper: Ersatztext genügt.
    }
    return ersatz;
}

/**
 * Prüft VOR dem Buchen, ob die Firmendaten für eine E-Rechnung reichen. Sonst wäre die
 * Rechnung gebucht und gesperrt, aber das PDF käme nie zustande.
 * Wirft einen Error mit der lesbaren Meldung des Servers.
 */
export async function pruefeFirmendatenFuerERechnung(): Promise<void> {
    const response = await fetch('/api/dokument-generator/zugferd-pruefung');
    if (!response.ok) {
        throw new Error(await fehlermeldungAusAntwort(
            response,
            'Die Firmendaten reichen für eine E-Rechnung noch nicht aus.',
        ));
    }
}
