/**
 * Triggert ein sofortiges Neuladen der Notification-Glocke.
 * Andere Komponenten (z.B. EmailCenter beim mark-read) rufen das auf,
 * damit der Glocken-Zähler sich nicht erst nach dem 60s-Polling aktualisiert.
 */
export function refreshNotifications(): void {
    try {
        window.dispatchEvent(new Event('notifications:refresh'));
    } catch {
        /* SSR-/Test-Kontext ohne window – einfach ignorieren */
    }
}
