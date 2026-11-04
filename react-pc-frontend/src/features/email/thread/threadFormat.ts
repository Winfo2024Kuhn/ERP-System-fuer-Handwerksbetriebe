import type { ThreadAttachment } from './threadTypes';

/** Reine Hilfsfunktionen für den E-Mail-Verlauf: Größen, Uhrzeiten, Tage, Dateinamen. */

/**
 * Bis zu dieser Größe gilt ein Bild als Signatur-Logo und nicht als echter Anhang.
 * Bekannte Grenze: Ein bewusst erneut gesendetes kleines Bild (< 100 KB) bleibt in
 * Folgenachrichten unsichtbar. Trennschärfer wäre die contentId des Anhangs.
 */
const SIGNATUR_LOGO_MAX_BYTES = 100 * 1024;

/**
 * Signatur-Logos hängen an jeder Nachricht eines Verlaufs erneut dran.
 * Nur solche Bilder werden thread-weit einmalig angezeigt.
 */
export function isWiederholtesSignaturLogo(attachment: ThreadAttachment): boolean {
    if (!attachment.mimeType?.toLowerCase().startsWith('image/')) return false;
    const size = attachment.sizeBytes ?? 0;
    return size > 0 && size <= SIGNATUR_LOGO_MAX_BYTES;
}

export function formatSize(bytes?: number): string {
    if (!bytes) return '';
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

export function formatTime(sentAt?: string): string {
    if (!sentAt) return '';
    try {
        return new Date(sentAt).toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit' });
    } catch { return ''; }
}

export function formatDateTime(sentAt?: string): string {
    if (!sentAt) return '';
    try {
        const d = new Date(sentAt);
        return d.toLocaleDateString('de-DE', {
            weekday: 'long', day: 'numeric', month: 'long', year: 'numeric',
        }) + ', ' + d.toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit' }) + ' Uhr';
    } catch { return ''; }
}

export function formatDateLabel(sentAt?: string): string {
    if (!sentAt) return '';
    try {
        return new Date(sentAt).toLocaleDateString('de-DE', {
            weekday: 'long', day: 'numeric', month: 'long', year: 'numeric',
        });
    } catch { return ''; }
}

export function getDayString(sentAt?: string): string {
    return sentAt ? sentAt.split('T')[0] : '';
}

/**
 * Dekodiert RFC 2047 Encoded-Words in Dateinamen / Subjects.
 * Beispiel: =?iso-8859-1?Q?Balkenlage_mit_Stahltr=E4ger_HEB240.pdf?=
 *        → "Balkenlage mit Stahlträger HEB240.pdf"
 */
export function decodeMimeWord(str: string): string {
    if (!str || !str.includes('=?')) return str;
    return str
        .replace(/=\?([^?]+)\?([BQbq])\?([^?]*)\?=/g, (_match, charset: string, encoding: string, text: string) => {
            try {
                let bytes: Uint8Array;
                if (encoding.toUpperCase() === 'B') {
                    const binary = atob(text);
                    bytes = new Uint8Array(binary.length);
                    for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
                } else {
                    // Q-Encoding: _ → Leerzeichen, =XX → Byte im angegebenen Charset
                    const result: number[] = [];
                    for (let i = 0; i < text.length; i++) {
                        if (text[i] === '_') {
                            result.push(0x20);
                        } else if (text[i] === '=' && i + 2 < text.length) {
                            result.push(parseInt(text.slice(i + 1, i + 3), 16));
                            i += 2;
                        } else {
                            result.push(text.charCodeAt(i));
                        }
                    }
                    bytes = new Uint8Array(result);
                }
                return new TextDecoder(charset).decode(bytes);
            } catch {
                return _match;
            }
        })
        .replace(/\s+/g, ' ')
        .trim();
}
