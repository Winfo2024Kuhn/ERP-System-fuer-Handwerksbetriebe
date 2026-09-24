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

/**
 * HTML-Entity-Escaping für Strings, die als Plain-Text in einen
 * HTML-Body eingesetzt werden sollen. Verhindert, dass eingebettete
 * `<script>` / `<img onerror>` etc. als HTML interpretiert werden.
 */
export function escapeHtml(input: string): string {
    return input
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}
