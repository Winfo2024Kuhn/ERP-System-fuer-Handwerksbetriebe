/**
 * Freemail-Anbieter (gmail.com, gmx.de, t-online.de, …).
 *
 * Bei einer eigenen Firmen-Domain gehört jede Adresse dahinter zum selben Partner,
 * bei gmx.de oder t-online.de sagt die Domain nichts aus. Deshalb ergänzt die
 * Oberfläche dort keine `info@`-Adresse, und das Backend ordnet E-Mails solcher
 * Domains nur über die exakt hinterlegte Adresse zu.
 *
 * Gleiche Liste wie im Backend (`util/FreemailDomains.java`). Der Backend-Test
 * `FreemailDomainsTest` liest diese Datei und schlägt fehl, sobald beide Listen
 * auseinanderlaufen – Änderungen also immer an beiden Stellen.
 */
export const FREEMAIL_DOMAINS: ReadonlySet<string> = new Set([
    'gmail.com', 'googlemail.com',
    'gmx.de', 'gmx.net', 'gmx.at', 'gmx.ch',
    'web.de', 't-online.de', 'freenet.de', 'arcor.de', '1und1.de',
    'outlook.com', 'outlook.de', 'hotmail.com', 'hotmail.de',
    'live.com', 'live.de', 'msn.com',
    'yahoo.com', 'yahoo.de', 'yahoo.co.uk', 'ymail.com', 'aol.com', 'aol.de',
    'icloud.com', 'me.com', 'mac.com',
    'mail.de', 'posteo.de', 'mailbox.org', 'protonmail.com', 'proton.me',
    'gmx.com', 'gmx.li', 'googlemail.de', 'hotmail.fr',
    'email.de', 'online.de', 'kabelmail.de', 'vodafone.de',
    'netcologne.de', 'ewetel.net', 'unitybox.de', 'unitymedia.de', 'firemail.de',
    't-online.com', 'telekom.de', 'arcor.net',
]);

/** Prüft, ob eine Domain zu einem Freemail-Anbieter gehört (Groß-/Kleinschreibung egal). */
export function istFreemailDomain(domain?: string | null): boolean {
    return !!domain && FREEMAIL_DOMAINS.has(domain.trim().toLowerCase());
}
