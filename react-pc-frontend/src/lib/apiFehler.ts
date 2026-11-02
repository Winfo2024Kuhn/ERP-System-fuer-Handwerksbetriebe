/**
 * Liest die Meldung aus einer fehlgeschlagenen API-Antwort.
 *
 * Das Backend liefert Fehler entweder als `{ error }` (ältere Endpunkte) oder als
 * `{ message }` (zentraler RestExceptionHandler, z. B. 403 „Ihr Benutzer ist keinem
 * Mitarbeiter zugeordnet …“). Ohne lesbare Meldung kommt der Ersatztext zurück.
 *
 * Unerwartete Serverfehler beantwortet Spring mit seiner Standardform
 * `{ timestamp, status, error, path }`. Dort ist `error` nur der englische
 * HTTP-Statustext („Internal Server Error“) – dann gilt der Ersatztext.
 */
export async function leseFehlermeldung(res: Response, ersatz: string): Promise<string> {
    try {
        const daten: unknown = await res.json();
        if (daten && typeof daten === 'object' && !('timestamp' in daten && 'path' in daten)) {
            const { error, message } = daten as { error?: unknown; message?: unknown };
            if (typeof error === 'string' && error.trim()) return error;
            if (typeof message === 'string' && message.trim()) return message;
        }
    } catch {
        // Kein JSON im Fehler-Body – Ersatztext verwenden.
    }
    return ersatz;
}

/** Meldung, wenn der Server gar nicht erreichbar ist. */
export const KEINE_VERBINDUNG = 'Keine Verbindung zum Server. Bitte prüfen Sie die Verbindung und versuchen Sie es erneut.';

/**
 * `fetch` mit verständlichen Fehlern: Jeder Fehlschlag wirft einen `Error` mit
 * einem Satz, den man dem Nutzer zeigen kann – die Meldung des Servers, sonst
 * `ersatz` (bzw. {@link KEINE_VERBINDUNG}, wenn der Server nicht antwortet).
 * CSRF ergänzt der globale Abfangjäger in `main.tsx`.
 */
export async function apiAnfrage(url: string, init: RequestInit | undefined, ersatz: string): Promise<Response> {
    let res: Response;
    try {
        res = await fetch(url, init);
    } catch {
        throw new Error(KEINE_VERBINDUNG);
    }
    if (!res.ok) throw new Error(await leseFehlermeldung(res, ersatz));
    return res;
}

/** Wie {@link apiAnfrage}, liefert aber den JSON-Inhalt der Antwort. */
export async function apiJson<T>(url: string, init: RequestInit | undefined, ersatz: string): Promise<T> {
    const res = await apiAnfrage(url, init, ersatz);
    return res.json() as Promise<T>;
}

/** Text eines gefangenen Fehlers für den Nutzer – bei Nicht-`Error` der Ersatztext. */
export function fehlerText(err: unknown, ersatz: string): string {
    return err instanceof Error ? err.message : ersatz;
}
