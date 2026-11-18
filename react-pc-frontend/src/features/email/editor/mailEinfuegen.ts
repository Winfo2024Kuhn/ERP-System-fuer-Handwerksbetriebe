import { parseHtmlInert, sanitizeHtml } from '../../../lib/htmlSanitizer';
import { raeumeFremdHtmlAuf } from './fremdHtmlAufraeumen';

/**
 * Eingefügtes und hineingezogenes HTML für das Mail-Textfeld vorbereiten.
 *
 * Bilder kommen nie in den Text: Eingebettete Bilder (`data:`) werden Dateien
 * für die Anhangsliste, alle anderen fallen weg und werden gezählt (Bilder aus
 * dem Internet getrennt von nicht lesbaren wie `blob:`, `file:`, `cid:`).
 * Tabellen und Formatierung bleiben. Geparst wird nur über `parseHtmlInert()`,
 * das Ergebnis läuft durch `sanitizeHtml()`.
 */

export interface NichtUebernommen {
    /** Bilder, die eine Webseite aus dem Internet lädt. */
    internet: number;
    /** Bilder, die sich nicht lesen lassen (`blob:`, `file:`, `cid:`, kaputt). */
    unlesbar: number;
}

export interface EinfuegeErgebnis {
    html: string;
    dateien: File[];
    nichtUebernommen: NichtUebernommen;
}

export interface EinfuegeOptionen {
    /** Liefert die nächste freie Nummer für „Bild-1.png“, „Bild-2.png“ … */
    vergibNummer: () => number;
    /**
     * Wie viele Bilder schon als Datei in der Zwischenablage liegen (ein Bild aus
     * dem Browser kopiert: HTML mit Bild-Adresse und die Bilddatei). So viele
     * Bilder im HTML gelten als erledigt: kein Hinweis, kein doppelter Anhang.
     */
    gedeckteBilder?: number;
}

export const KEINE_DATEIEN_MELDUNG = 'In dieses Feld lassen sich keine Bilder oder Dateien einfügen.';

const ENDUNGEN: Record<string, string> = { jpeg: 'jpg', 'svg+xml': 'svg', tiff: 'tif', 'x-icon': 'ico' };
const BILD_TYP = /^image\/[a-z0-9.+-]+$/;

function endungFuer(typ: string): string {
    const art = typ.split('/')[1] ?? '';
    return ENDUNGEN[art] ?? (art.replace(/[^a-z0-9]/g, '') || 'bin');
}

/** Macht aus einem `data:image/…`-Bild eine Datei `<name>.<endung>`; sonst `null`. */
export function dataUrlAlsDatei(url: string, name: string): File | null {
    if (!/^data:/i.test(url)) return null;
    const komma = url.indexOf(',');
    if (komma < 0) return null;
    const kopf = url.slice(5, komma).split(';').map(teil => teil.trim().toLowerCase());
    const typ = kopf[0];
    if (!BILD_TYP.test(typ)) return null;
    const inhalt = url.slice(komma + 1);
    let bytes: Uint8Array<ArrayBuffer>;
    try {
        bytes = kopf.includes('base64')
            ? Uint8Array.from(atob(inhalt.replace(/\s/g, '')), zeichen => zeichen.charCodeAt(0))
            : new TextEncoder().encode(decodeURIComponent(inhalt));
    } catch {
        return null;
    }
    if (bytes.length === 0) return null;
    return new File([bytes], `${name}.${endungFuer(typ)}`, { type: typ });
}

/** Zwischenablage-Bilder heißen oft nur „image.png“; die bekommen „Bild-n.png“. Echte Dateinamen bleiben. */
export function benenneDateien(dateien: File[], vergibNummer: () => number): File[] {
    return dateien.map(datei => {
        if (datei.name && !/^image\.[a-z0-9]+$/i.test(datei.name)) return datei;
        const endung = datei.type ? endungFuer(datei.type) : (datei.name.split('.').pop() || 'bin');
        return new File([datei], `Bild-${vergibNummer()}.${endung}`, { type: datei.type, lastModified: datei.lastModified });
    });
}

const istWebAdresse = (src: string) => /^https?:/i.test(src);

/**
 * Nimmt alle Bilder aus dem Knoten (auch Bild-Knöpfe und Hintergrundbilder).
 * Eingebettete werden Dateien, der Rest wird
 * gezählt. Bilder, die schon als Datei mitkamen, verrechnen erst Web-Bilder,
 * dann nicht lesbare, zuletzt eingebettete (sonst hinge dasselbe Bild doppelt an).
 */
function nimmBilderHeraus(wurzel: HTMLElement, { vergibNummer, gedeckteBilder = 0 }: EinfuegeOptionen) {
    const bilder = Array.from(wurzel.querySelectorAll('img, input[type="image" i]'), bild => {
        bild.remove();
        return bild.getAttribute('src') ?? '';
    });
    // Hintergrundbilder (`<td background=…>`) zählen mit; das Attribut entfernt das Aufräumen.
    const hintergruende = Array.from(wurzel.querySelectorAll('[background]'), element => element.getAttribute('background') ?? '');
    const quellen = [...bilder, ...hintergruende].map(src => src.trim()).filter(Boolean);
    const eingebettet = quellen.filter(src => !istWebAdresse(src))
        .map(src => dataUrlAlsDatei(src, 'Bild'));
    const dateien = eingebettet.filter((datei): datei is File => datei !== null);
    let offen = gedeckteBilder;
    const verrechne = (anzahl: number) => {
        const gedeckt = Math.min(offen, anzahl);
        offen -= gedeckt;
        return anzahl - gedeckt;
    };
    const internet = verrechne(quellen.filter(istWebAdresse).length);
    const unlesbar = verrechne(eingebettet.length - dateien.length);
    const behalten = dateien.slice(0, verrechne(dateien.length))
        .map(datei => new File([datei], `Bild-${vergibNummer()}.${endungFuer(datei.type)}`, { type: datei.type }));
    return { daten: behalten, nichtUebernommen: { internet, unlesbar } };
}

/** Bereitet eingefügtes HTML vor: `{ html, dateien, nichtUebernommen }`. */
export function bereiteEinfuegenVor(rohHtml: string, optionen: EinfuegeOptionen): EinfuegeErgebnis {
    const wurzel = parseHtmlInert(rohHtml);
    const { daten, nichtUebernommen } = nimmBilderHeraus(wurzel, optionen);
    raeumeFremdHtmlAuf(wurzel);
    return { html: sanitizeHtml(wurzel.innerHTML).trim(), dateien: daten, nichtUebernommen };
}

/** Steht nach dem Aufräumen etwas Sichtbares da (Text oder Tabelle)? */
export function hatSichtbarenInhalt(html: string): boolean {
    const wurzel = parseHtmlInert(html);
    return !!wurzel.querySelector('table, hr') || (wurzel.textContent ?? '').trim().length > 0;
}

/** Toast nach dem Anhängen. */
export function anhangMeldung(dateien: File[]): string {
    if (dateien.length !== 1) return `${dateien.length} Dateien als Anhang hinzugefügt.`;
    return dateien[0].type.startsWith('image/') ? 'Bild als Anhang hinzugefügt.' : 'Datei als Anhang hinzugefügt.';
}

/** Hinweise zu Bildern, die nicht in die Mail kommen. */
export function nichtUebernommenMeldungen({ internet, unlesbar }: NichtUebernommen): string[] {
    const meldungen: string[] = [];
    if (internet === 1) meldungen.push('1 Bild aus dem Internet wurde nicht übernommen. Bitte als Datei anhängen.');
    if (internet > 1) meldungen.push(`${internet} Bilder aus dem Internet wurden nicht übernommen. Bitte als Datei anhängen.`);
    if (unlesbar === 1) meldungen.push('1 Bild konnte nicht übernommen werden. Bitte als Datei anhängen.');
    if (unlesbar > 1) meldungen.push(`${unlesbar} Bilder konnten nicht übernommen werden. Bitte als Datei anhängen.`);
    return meldungen;
}
