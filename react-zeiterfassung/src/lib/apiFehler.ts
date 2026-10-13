/**
 * Liest die Meldung aus einer fehlgeschlagenen API-Antwort.
 *
 * Neuere Endpunkte liefern `{ status, message }` (zentraler RestExceptionHandler),
 * ältere `{ error }`. `message` hat Vorrang. Ohne lesbare Meldung kommt der
 * Ersatztext zurück – auch bei Springs Standardform `{ timestamp, status, error, path }`,
 * denn dort ist `error` nur der englische HTTP-Statustext („Internal Server Error“).
 */
export async function leseFehlermeldung(res: Response, ersatz: string): Promise<string> {
    try {
        const daten: unknown = await res.json()
        if (daten && typeof daten === 'object' && !('timestamp' in daten && 'path' in daten)) {
            const { message, error } = daten as { message?: unknown; error?: unknown }
            if (typeof message === 'string' && message.trim()) return message
            if (typeof error === 'string' && error.trim()) return error
        }
    } catch {
        // Kein JSON im Fehler-Body – Ersatztext verwenden.
    }
    return ersatz
}
