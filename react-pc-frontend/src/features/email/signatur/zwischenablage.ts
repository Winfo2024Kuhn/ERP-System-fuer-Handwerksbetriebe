import { stripHtmlTags, unescapeHtmlEntities } from '../../../lib/htmlSanitizer';

/**
 * Macht aus Signatur-HTML lesbaren Text für die Zwischenablage
 * (Zeilenumbrüche an Tabellenzeilen, Absätzen und <br>).
 */
export function signaturAlsText(html: string): string {
    const mitUmbruechen = html.replace(/<br\s*\/?>|<\/(?:tr|p|div|h[1-6])>/gi, '\n');
    return unescapeHtmlEntities(stripHtmlTags(mitUmbruechen))
        .replace(/\u00a0/g, ' ')
        .split('\n')
        .map((zeile) => zeile.replace(/[ \t]+/g, ' ').trim())
        .filter(Boolean)
        .join('\n');
}

export type KopierErgebnis = 'formatiert' | 'nurText';

/**
 * Kopiert die Signatur so, dass Outlook & Co. sie formatiert einfügen
 * (text/html plus text/plain). Ohne ClipboardItem-Unterstützung bleibt nur der
 * reine Text. Wirft, wenn der Browser das Kopieren ganz verweigert.
 */
export async function kopiereSignatur(html: string): Promise<KopierErgebnis> {
    const text = signaturAlsText(html);
    const clipboard = typeof navigator !== 'undefined' ? navigator.clipboard : undefined;
    if (clipboard && typeof clipboard.write === 'function' && typeof ClipboardItem !== 'undefined') {
        try {
            await clipboard.write([
                new ClipboardItem({
                    'text/html': new Blob([html], { type: 'text/html' }),
                    'text/plain': new Blob([text], { type: 'text/plain' }),
                }),
            ]);
            return 'formatiert';
        } catch {
            // weiter mit dem reinen Text
        }
    }
    if (clipboard && typeof clipboard.writeText === 'function') {
        await clipboard.writeText(text);
        return 'nurText';
    }
    throw new Error('Ihr Browser erlaubt das Kopieren nicht. Bitte die Signatur in der Vorschau markieren und kopieren.');
}
