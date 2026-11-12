/**
 * Erkennt nackte Adressen in Text (`https://…`, `http://…`, `www.…`, E-Mail-Adressen)
 * und zerlegt ihn in Text- und Link-Stücke. Genutzt beim Anzeigen von Mails
 * (`EmailContentFrame`) und vor dem Versand (`htmlFuerVersand`).
 *
 * <p>Nur `http:`, `https:` und `mailto:` werden zu Links. Die Muster haben
 * begrenzte, nicht verschachtelte Wiederholungen – auch sehr lange Zeichenketten
 * ohne Leerzeichen laufen linear durch (kein ReDoS).</p>
 */

export type TextStueck =
    | { art: 'text'; text: string }
    | { art: 'link'; text: string; href: string };

/**
 * Kandidat: Web-Adresse oder E-Mail-Adresse, nicht mitten in einem Wort oder Pfad.
 * Web-Adressen über 2048 Zeichen verwirft `zerlegeInLinks` (nie abgeschnitten
 * verlinken), E-Mail-Adressen über 253 Zeichen passen gar nicht. Satzzeichen am Ende schneidet `kuerzeEnde` ab.
 */
const KANDIDAT = /(?<![\w@./-])(?:(?:https?:\/\/|www\.)[^\s<>"]{1,2049}|[\w.%+-]{1,64}@[\w-]{1,63}\.[\w.-]{1,253}(?![\w.-]))/gi;
const MAX_ADRESSE = 2048;
const TRENNER = /[\s<>"]/g;
const SATZZEICHEN = new Set(['.', ',', ';', ':', '!', '?', '»', '«', '"', "'", '“', '”', '„', '‘', '’', '…']);
const OEFFNER: Record<string, string> = { ')': '(', ']': '[', '}': '{' };

/**
 * Satzzeichen und unausgeglichene schließende Klammern am Ende gehören nicht zum Link.
 * Linear: Klammern werden einmal gezählt und beim Kürzen heruntergezählt.
 */
function kuerzeEnde(kandidat: string): string {
    const offen: Record<string, number> = { '(': 0, '[': 0, '{': 0 };
    const zu: Record<string, number> = { ')': 0, ']': 0, '}': 0 };
    for (const zeichen of kandidat) {
        if (zeichen in offen) offen[zeichen] += 1;
        else if (zeichen in zu) zu[zeichen] += 1;
    }
    let ende = kandidat.length;
    while (ende > 0) {
        const zeichen = kandidat[ende - 1];
        const oeffner = OEFFNER[zeichen];
        if (SATZZEICHEN.has(zeichen)) {
            ende -= 1;
        } else if (oeffner && zu[zeichen] > offen[oeffner]) {
            zu[zeichen] -= 1;
            ende -= 1;
        } else {
            break;
        }
    }
    return kandidat.slice(0, ende);
}

function alsHref(text: string): string | null {
    const href = /^www\./i.test(text) ? `https://${text}`
        : /^https?:\/\//i.test(text) ? text
            : `mailto:${text}`;
    try {
        const url = new URL(href);
        if (url.protocol === 'mailto:') return url.pathname.includes('@') ? href : null;
        if (url.protocol !== 'http:' && url.protocol !== 'https:') return null;
        return url.hostname.includes('.') || url.hostname === 'localhost' ? href : null;
    } catch {
        return null;
    }
}

function fuegeTextAn(stuecke: TextStueck[], text: string): void {
    if (!text) return;
    const letztes = stuecke.at(-1);
    if (letztes?.art === 'text') letztes.text += text;
    else stuecke.push({ art: 'text', text });
}

/** Zerlegt Text in Text- und Link-Stücke. Ohne Adresse: ein Text-Stück (leer: keins). */
export function zerlegeInLinks(text: string): TextStueck[] {
    const stuecke: TextStueck[] = [];
    let position = 0;
    // Zu lange Adresse: sie und alles bis zum nächsten Leerraum bleibt Text. Ohne
    // Rücksprung im Muster bleibt die Laufzeit linear.
    let sperreBis = 0;
    for (const treffer of text.matchAll(KANDIDAT)) {
        const start = treffer.index ?? 0;
        if (start < sperreBis) continue;
        if (treffer[0].length > MAX_ADRESSE) {
            TRENNER.lastIndex = start + treffer[0].length;
            sperreBis = TRENNER.exec(text)?.index ?? text.length;
            continue;
        }
        const linkText = kuerzeEnde(treffer[0]);
        const href = linkText ? alsHref(linkText) : null;
        if (!href) continue;
        fuegeTextAn(stuecke, text.slice(position, start));
        stuecke.push({ art: 'link', text: linkText, href });
        position = start + linkText.length;
    }
    fuegeTextAn(stuecke, text.slice(position));
    return stuecke;
}
