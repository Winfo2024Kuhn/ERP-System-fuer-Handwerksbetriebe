import { toSafeResourceUrl } from '../../lib/htmlSanitizer';

const BILD_DATA_URI = /^data:image\/(?:png|jpeg|jpg|gif|webp);base64,[A-Za-z\d+/]+={0,2}$/i;

/**
 * Bildquelle für Briefpapier und Logo im Vorlagen-Editor.
 *
 * Hochgeladenes Briefpapier steckt als Data-URI in der Vorlage. Erlaubt sind
 * nur Rasterbilder (PNG, JPEG, GIF, WebP) als Base64 – kein SVG und kein
 * `data:text/html`. Alles andere läuft über {@link toSafeResourceUrl}
 * (relative Pfade, http/https, blob).
 */
export function sichereBildQuelle(quelle: string | null | undefined): string {
    const wert = (quelle ?? '').trim();
    if (!wert) return '';
    if (wert.slice(0, 5).toLowerCase() === 'data:') return BILD_DATA_URI.test(wert) ? wert : '';
    return toSafeResourceUrl(wert);
}
