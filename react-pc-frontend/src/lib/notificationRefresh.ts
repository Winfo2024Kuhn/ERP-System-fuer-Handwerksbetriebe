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

/** Name des Events, auf das das E-Mail-Center mit einem stillen Neuladen reagiert. */
export const EMAILS_REFRESH_EVENT = 'emails:refresh';

/**
 * Triggert ein sofortiges Neuladen des E-Mail-Centers (Ordnerzahlen und
 * aktuelle Liste). Die Glocke ruft das auf, nachdem sie ganze E-Mail-Ordner
 * als gelesen markiert hat – sonst stünden im offenen E-Mail-Center noch
 * bis zum nächsten 30s-Polling die alten Zahlen.
 */
export function refreshEmails(): void {
    try {
        window.dispatchEvent(new Event(EMAILS_REFRESH_EVENT));
    } catch {
        /* SSR-/Test-Kontext ohne window – einfach ignorieren */
    }
}
