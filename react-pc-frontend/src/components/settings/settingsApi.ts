/**
 * Gemeinsame Typen und Hilfsfunktionen für die Einstellungs-Bereiche.
 *
 * <p>Bewusst ohne JSX in einer eigenen Datei: Eine Datei, die Komponenten
 * <em>und</em> normale Funktionen exportiert, bricht das Hot-Reload im
 * Entwicklungsmodus (react-refresh). Die Bausteine mit Markup stehen daher
 * in {@code settingsUi.tsx}.</p>
 */

/** Antwort der `/test`-Endpunkte: hat es geklappt, und was soll dastehen. */
export interface TestResult {
    success: boolean;
    message: string;
}

/**
 * Holt die Fehlermeldung des Backends aus der Antwort. Fällt auf den
 * übergebenen Text zurück, wenn der Body leer oder kein JSON ist — der
 * Anwender soll nie ein rohes "[object Object]" sehen.
 */
export async function parseErrorMessage(res: Response, fallback: string): Promise<string> {
    try {
        const text = await res.text();
        const data = JSON.parse(text);
        if (typeof data?.message === 'string' && data.message.trim()) {
            return data.message;
        }
        if (text.trim()) return text;
    } catch {
        // Kein JSON im Body — dann bleibt es beim Fallback.
    }
    return fallback;
}
