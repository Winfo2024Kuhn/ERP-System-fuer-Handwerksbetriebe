/**
 * Zentrale Hilfsfunktionen für sicheres HTML-Stripping, Entity-Decoding und URL-Validierung.
 *
 * Verhindert gängige Sicherheitslücken wie:
 * - Unvollständige Multicharacter-Sanitization (CWE-20/80/116, z. B. <<script>script>)
 * - Double-Unescaping (&amp;lt; -> < statt &lt;)
 * - DOM-XSS über ungeprüfte URLs (javascript:, data:text/html)
 */

/**
 * Entfernt alle HTML-Tags vollständig per Fixpunktschleife.
 * Durch den innere-Klammern-Muster /<[^<>]*>/g werden geschachtelte Tags
 * von innen nach außen schrittweise und restlos aufgelöst.
 */
export function stripHtmlTags(input: string, replacement = ''): string {
    if (!input) return '';
    let prev = '';
    let result = input;
    let iterations = 0;
    do {
        prev = result;
        result = result.replace(/<[^<>]*>/g, replacement);
        iterations++;
    } while (result !== prev && iterations < 20);
    return result;
}

/**
 * Wandelt bekannte HTML-Entitäten in einem einzigen Durchlauf um.
 * Ein Single-Pass-Verfahren schließt doppeltes Entpacken (Double-Unescaping)
 * mathematisch aus, da ersetzte Zeichen nicht erneut gescannt werden.
 */
export function unescapeHtmlEntities(input: string): string {
    if (!input) return '';
    return input.replace(/&(?:amp|lt|gt|quot|#39|nbsp);/g, (match) => {
        switch (match) {
            case '&amp;': return '&';
            case '&lt;': return '<';
            case '&gt;': return '>';
            case '&quot;': return '"';
            case '&#39;': return "'";
            case '&nbsp;': return ' ';
            default: return match;
        }
    });
}

/**
 * Validiert und bereinigt eine Ressourcen-URL für <iframe> oder <img>.
 * Erlaubt standardmäßig nur blob:, http:, https: sowie relative Pfade beginnend mit '/'.
 * Filtert gefährliche Schemata wie javascript: oder data: sowie protocol-relative URLs (//) aus.
 */
export function toSafeResourceUrl(
    rawUrl?: string | null,
    allowedProtocols: string[] = ['blob:', 'http:', 'https:']
): string {
    if (!rawUrl) return '';
    const trimmed = rawUrl.trim();
    if (!trimmed) return '';

    // Protocol-relative URLs wie //evil.com blockieren
    if (trimmed.startsWith('//')) {
        return '';
    }

    // Relativer Pfad innerhalb der Origin
    if (trimmed.startsWith('/')) {
        return trimmed;
    }

    // Blob-URLs: Dürfen nur mit 'blob:' beginnen und müssen ein gültiges URL-Format haben
    if (trimmed.startsWith('blob:')) {
        try {
            const parsed = new URL(trimmed);
            if (parsed.protocol === 'blob:' && allowedProtocols.includes('blob:')) {
                return parsed.href;
            }
        } catch {
            return '';
        }
        return '';
    }

    // HTTP / HTTPS mit Whitelist
    if (trimmed.startsWith('http://') || trimmed.startsWith('https://')) {
        try {
            const parsed = new URL(trimmed);
            if (allowedProtocols.includes(parsed.protocol)) {
                return parsed.href;
            }
        } catch {
            return '';
        }
        return '';
    }

    return '';
}
