/**
 * Liest die Meldung aus einer fehlgeschlagenen API-Antwort.
 *
 * Das Backend liefert Fehler entweder als `{ error }` (ältere Endpunkte) oder als
 * `{ message }` (zentraler RestExceptionHandler, z. B. 403 „Ihr Benutzer ist keinem
 * Mitarbeiter zugeordnet …“). Ohne lesbare Meldung kommt der Ersatztext zurück.
 */
export async function leseFehlermeldung(res: Response, ersatz: string): Promise<string> {
    try {
        const daten: unknown = await res.json();
        if (daten && typeof daten === 'object') {
            const { error, message } = daten as { error?: unknown; message?: unknown };
            if (typeof error === 'string' && error.trim()) return error;
            if (typeof message === 'string' && message.trim()) return message;
        }
    } catch {
        // Kein JSON im Fehler-Body – Ersatztext verwenden.
    }
    return ersatz;
}
