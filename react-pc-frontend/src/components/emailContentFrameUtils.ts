/**
 * Erkennt Mails, die in Wirklichkeit Plain-Text sind (z. B. t-online,
 * Hetzner-Tickets): enthalten echte `\n`-Umbrüche und überhaupt keine
 * HTML-Tags. Für diese Mails muss `\n` als sichtbarer Zeilenumbruch
 * gerendert werden, sonst kollabiert alles zu einer Bandwurm-Zeile.
 *
 * Strikt: schon ein einziges `<tag` deaktiviert die Klassifizierung,
 * damit pretty-printed HTML (Tabellen-Mails mit `\n` zwischen `<td>`-Tags)
 * nicht versehentlich als Plain-Text behandelt wird.
 */
export function isLikelyPlainText(html: string | null | undefined): boolean {
    if (!html) return false;
    if (!/\r?\n/.test(html)) return false;
    return !/<[a-zA-Z]/.test(html);
}

/** HTML-Escaping für Plain-Text im HTML-Body – gemeinsame Funktion aus `lib/htmlSanitizer.ts`. */
export { escapeHtml } from '../lib/htmlSanitizer';
