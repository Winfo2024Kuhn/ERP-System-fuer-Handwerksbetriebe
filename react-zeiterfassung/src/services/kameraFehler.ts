/**
 * Übersetzt Fehler beim Kamerastart (getUserMedia) in verständliche deutsche
 * Hinweise. Die Browser liefern nur englische Fachmeldungen; maßgeblich ist
 * der Fehlername (`DOMException.name`).
 */
export function kameraFehlerText(fehler: unknown): string {
    const name = fehler instanceof Error || fehler instanceof DOMException ? fehler.name : ''
    const text = fehler instanceof Error || fehler instanceof DOMException ? fehler.message : String(fehler)

    if (name === 'NotAllowedError' || name === 'SecurityError' || /denied|not allowed|notallowed/i.test(text)) {
        return 'Kein Zugriff auf die Kamera. Bitte in den iOS-Einstellungen unter Safari → Kamera erlauben und die App neu starten.'
    }
    if (name === 'NotFoundError' || name === 'OverconstrainedError') {
        return 'Keine passende Kamera gefunden. Bitte den Scanner auf einem Handy mit Kamera öffnen.'
    }
    if (name === 'NotReadableError' || name === 'AbortError') {
        return 'Die Kamera wird gerade von einer anderen App benutzt. Bitte die andere App schließen und den Scanner neu öffnen.'
    }
    return 'Kamera konnte nicht gestartet werden. Bitte App neu öffnen.'
}
