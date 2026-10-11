import { toSafeResourceUrl } from '../../../lib/htmlSanitizer';
import { STANDARD_AKZENTFARBE } from './signaturTypen';

/**
 * Sicherheits-Helfer für das Signatur-HTML. Alles, was der Benutzer eintippt,
 * läuft hier durch, bevor es in eine Vorlage wandert: Text wird HTML-escaped,
 * Links und Bilder nur mit erlaubten Schemata übernommen.
 */

/** Größe, ab der ein eingebettetes Bild abgelehnt wird (wie im Backend). */
export const MAX_BILD_BYTES = 1024 * 1024;

const ERLAUBTE_BILD_TYPEN = ['png', 'jpeg', 'gif', 'webp'] as const;

const ESCAPE: Record<string, string> = {
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#39;',
};

/** Escaped Text für HTML-Inhalt und Attribute (immer in doppelten Anführungszeichen). */
export function escapeHtml(text: string | null | undefined): string {
    if (!text) return '';
    return text.replace(/[&<>"']/g, (zeichen) => ESCAPE[zeichen]);
}

const HAT_SCHEMA = /^[a-z][a-z\d+.-]*:/i;

/**
 * Link für `href`: nur http/https. Eingaben ohne Schema wie
 * „www.mustermann.de“ bekommen `https://` vorangestellt. Alles andere
 * (javascript:, data:, relative Pfade) ergibt einen leeren String.
 */
export function sichereLinkUrl(eingabe: string | null | undefined): string {
    const wert = (eingabe ?? '').trim();
    if (!wert || wert.startsWith('/') || wert.startsWith('\\')) return '';
    const mitSchema = HAT_SCHEMA.test(wert) ? wert : `https://${wert}`;
    const geprueft = toSafeResourceUrl(mitSchema, ['http:', 'https:']);
    return geprueft.startsWith('http://') || geprueft.startsWith('https://') ? geprueft : '';
}

/** Nur `data:image/(png|jpeg|gif|webp);base64,…` bis 1 MB. */
export function istErlaubtesBildDataUri(wert: string | null | undefined): boolean {
    if (!wert || !wert.startsWith('data:image/')) return false;
    const komma = wert.indexOf(',');
    if (komma < 0) return false;
    const kopf = wert.slice(0, komma).toLowerCase();
    const typOk = ERLAUBTE_BILD_TYPEN.some((typ) => kopf === `data:image/${typ};base64`);
    if (!typOk) return false;
    const daten = wert.slice(komma + 1);
    if (!daten || !/^[A-Za-z\d+/]+={0,2}$/.test(daten)) return false;
    return dataUriBytes(wert) <= MAX_BILD_BYTES;
}

/** Ungefähre Dateigröße eines Base64-Data-URIs in Bytes. */
export function dataUriBytes(wert: string): number {
    const komma = wert.indexOf(',');
    if (komma < 0) return 0;
    const daten = wert.slice(komma + 1);
    const polster = daten.endsWith('==') ? 2 : daten.endsWith('=') ? 1 : 0;
    return Math.max(0, Math.floor((daten.length * 3) / 4) - polster);
}

/** Bildquelle: http/https-Link oder erlaubter Data-URI – sonst leer. */
export function sichereBildUrl(eingabe: string | null | undefined): string {
    const wert = (eingabe ?? '').trim();
    if (!wert) return '';
    if (wert.startsWith('data:')) return istErlaubtesBildDataUri(wert) ? wert : '';
    if (!HAT_SCHEMA.test(wert)) return '';
    return sichereLinkUrl(wert);
}

/**
 * Dropbox-Freigabelinks zeigen eine Webseite statt des Bildes. Mit `raw=1`
 * liefert Dropbox das Bild selbst – das hängen wir automatisch an.
 */
export function bildLinkAufbereiten(eingabe: string): string {
    const wert = eingabe.trim();
    if (!wert.startsWith('https://')) return wert;
    try {
        const url = new URL(wert);
        const istDropbox = url.hostname === 'dropbox.com' || url.hostname.endsWith('.dropbox.com');
        if (!istDropbox || url.searchParams.get('raw') === '1') return wert;
        url.searchParams.delete('dl');
        url.searchParams.set('raw', '1');
        return url.href;
    } catch {
        return wert;
    }
}

/** `tel:`-Link aus einer Telefonnummer; nur Ziffern und ein führendes +. */
export function telHref(nummer: string | null | undefined): string {
    const wert = (nummer ?? '').trim();
    const ziffern = wert.replace(/\D/g, '');
    if (!ziffern) return '';
    return `tel:${wert.startsWith('+') ? '+' : ''}${ziffern}`;
}

const UNERLAUBT_IN_EMAIL = /[\s<>"'(),;:\\]/;

/** `mailto:`-Link, wenn die Adresse plausibel aussieht – sonst leer. */
export function mailtoHref(email: string | null | undefined): string {
    const wert = (email ?? '').trim();
    const at = wert.indexOf('@');
    if (at <= 0 || at !== wert.lastIndexOf('@') || UNERLAUBT_IN_EMAIL.test(wert)) return '';
    const domain = wert.slice(at + 1);
    if (!domain.includes('.') || domain.startsWith('.') || domain.endsWith('.')) return '';
    return `mailto:${wert}`;
}

/** WhatsApp-Link aus Nummer oder Link. Deutsche Nummern mit führender 0 bekommen +49. */
export function whatsappUrl(eingabe: string | null | undefined): string {
    const wert = (eingabe ?? '').trim();
    if (!wert) return '';
    if (HAT_SCHEMA.test(wert) || wert.includes('wa.me') || wert.includes('whatsapp.com')) return sichereLinkUrl(wert);
    let ziffern = wert.replace(/\D/g, '');
    if (!ziffern) return '';
    if (wert.startsWith('00')) ziffern = ziffern.slice(2);
    else if (!wert.startsWith('+') && ziffern.startsWith('0')) ziffern = `49${ziffern.slice(1)}`;
    return `https://wa.me/${ziffern}`;
}

const SOZIAL_BASIS = {
    facebook: 'https://www.facebook.com/',
    instagram: 'https://www.instagram.com/',
    linkedin: 'https://www.linkedin.com/in/',
} as const;

/**
 * Link zu einem Profil. Erlaubt ist ein ganzer Link („https://…“, „www.…“)
 * oder nur der Profilname („@mustermann“).
 */
export function sozialUrl(netzwerk: keyof typeof SOZIAL_BASIS, eingabe: string | null | undefined): string {
    const wert = (eingabe ?? '').trim();
    if (!wert) return '';
    if (HAT_SCHEMA.test(wert) || wert.includes('.') || wert.includes('/')) return sichereLinkUrl(wert);
    const profil = wert.startsWith('@') ? wert.slice(1) : wert;
    if (!profil) return '';
    return `${SOZIAL_BASIS[netzwerk]}${encodeURIComponent(profil)}`;
}

/** Website ohne Schema und ohne Schrägstrich am Ende – für die Anzeige. */
export function websiteAnzeige(eingabe: string): string {
    return eingabe.trim().replace(/^https?:\/\//i, '').replace(/\/$/, '');
}

const HEX_LANG = /^#[\da-f]{6}$/i;
const HEX_KURZ = /^#[\da-f]{3}$/i;

/** Hex-Farbe (#rgb oder #rrggbb) normalisiert – sonst die Standardfarbe. */
export function sichereFarbe(wert: string | null | undefined, standard = STANDARD_AKZENTFARBE): string {
    const farbe = (wert ?? '').trim();
    if (HEX_LANG.test(farbe)) return farbe.toLowerCase();
    if (HEX_KURZ.test(farbe)) {
        const [, r, g, b] = farbe.toLowerCase();
        return `#${r}${r}${g}${g}${b}${b}`;
    }
    return standard;
}

export function istHexFarbe(wert: string | null | undefined): boolean {
    const farbe = (wert ?? '').trim();
    return HEX_LANG.test(farbe) || HEX_KURZ.test(farbe);
}

/** Lesbare Schriftfarbe auf der Akzentfarbe (weiß auf dunkel, dunkel auf hell). */
export function kontrastFarbe(hintergrund: string): string {
    const hex = sichereFarbe(hintergrund);
    const kanal = (start: number) => {
        const wert = parseInt(hex.slice(start, start + 2), 16) / 255;
        return wert <= 0.03928 ? wert / 12.92 : ((wert + 0.055) / 1.055) ** 2.4;
    };
    const helligkeit = 0.2126 * kanal(1) + 0.7152 * kanal(3) + 0.0722 * kanal(5);
    return helligkeit > 0.45 ? '#1f2937' : '#ffffff';
}
