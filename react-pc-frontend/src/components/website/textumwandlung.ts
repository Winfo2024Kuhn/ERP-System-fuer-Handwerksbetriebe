import createDOMPurify from 'dompurify';

/**
 * Genau die Tags, die die Website in sanitizePostContent zulaesst
 * (molecular-mercury/src/lib/richtext.ts). Alles andere wird verworfen,
 * der enthaltene Text bleibt erhalten.
 */
export const ERLAUBTE_TAGS = ['p', 'br', 'b', 'strong', 'i', 'em', 'ul', 'ol', 'li', 'span'];

/** Nur span darf ein style tragen, und dort nur Farbe und Schriftgroesse. */
const ERLAUBTE_ATTRIBUTE = ['style'];

/**
 * Dieselben drei Ausdruecke, mit denen die Website ein span-style prueft
 * (molecular-mercury/src/lib/richtext.ts:11-13, wortgleich uebernommen).
 * DOMPurify kennt ALLOWED_ATTR nur global, nicht pro Tag - deshalb reicht
 * die Konfiguration allein nicht, um "style nur an span, und dort nur
 * Farbe/Schriftgroesse" durchzusetzen. Der afterSanitizeAttributes-Hook
 * weiter unten prueft jede Deklaration gegen genau diese Ausdruecke.
 */
const HEX_COLOR = /^#[0-9a-fA-F]{3}([0-9a-fA-F]{3})?$/;
const RGB_COLOR = /^rgb\(\s*\d{1,3}\s*,\s*\d{1,3}\s*,\s*\d{1,3}\s*\)$/;
const FONT_SIZE = /^\d{1,3}(\.\d+)?(px|rem|em|%)$/;

function istErlaubteStyleDeklaration(eigenschaft: string, wert: string): boolean {
    if (eigenschaft === 'color') return HEX_COLOR.test(wert) || RGB_COLOR.test(wert);
    if (eigenschaft === 'font-size') return FONT_SIZE.test(wert);
    return false;
}

/**
 * Filtert das style-Attribut eines einzelnen Elements. An allem ausser span
 * fliegt style komplett raus. An span bleibt nur, was gegen die drei
 * Ausdruecke oben besteht - alles andere (Farbworte, Einheiten-lose Groessen,
 * fremde Eigenschaften wie position/width) wird verworfen.
 */
function bereinigeStyleAttribut(element: Element): void {
    if (element.tagName.toLowerCase() !== 'span') {
        element.removeAttribute('style');
        return;
    }

    const roh = element.getAttribute('style');
    if (!roh) return;

    const behalten = roh
        .split(';')
        .map(deklaration => deklaration.trim())
        .filter(Boolean)
        .filter(deklaration => {
            const trennstelle = deklaration.indexOf(':');
            if (trennstelle === -1) return false;
            const eigenschaft = deklaration.slice(0, trennstelle).trim().toLowerCase();
            const wert = deklaration.slice(trennstelle + 1).trim();
            return istErlaubteStyleDeklaration(eigenschaft, wert);
        });

    if (behalten.length === 0) {
        element.removeAttribute('style');
    } else {
        element.setAttribute('style', `${behalten.join('; ')};`);
    }
}

/**
 * Eigene DOMPurify-Instanz nur fuer Beitrags-HTML, bewusst nicht der
 * geteilte Standard-Import. DOMPurify.addHook wirkt auf die Instanz, an der
 * er haengt - `components/EmailSettings.tsx` sanitisiert E-Mail-Signaturen
 * ueber genau denselben Standard-Import (`import DOMPurify from 'dompurify'`
 * liefert ueberall dasselbe Singleton-Objekt). Ein Hook auf diesem Singleton
 * wuerde dort also die Signaturen mitbeschneiden. `createDOMPurify(window)`
 * (der Default-Export ist zugleich die Factory) erzeugt dagegen eine frische,
 * isolierte Instanz mit eigenem Hook-Zustand.
 */
const beitragsDomPurify = createDOMPurify(window);
beitragsDomPurify.addHook('afterSanitizeAttributes', node => {
    if (node.hasAttribute('style')) {
        bereinigeStyleAttribut(node);
    }
});

function maskiere(text: string): string {
    return text
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;');
}

/**
 * Wandelt den Klartext der KI in das HTML, das die Website annimmt.
 * Absaetze trennen sich an Leerzeilen, einzelne Umbrueche werden zu br.
 * Ein Block, dessen Zeilen alle mit "- " beginnen, wird zu einer Liste.
 *
 * Der Text wird maskiert, bevor Tags gesetzt werden. Damit kann aus dem
 * Modell kein HTML durchschlagen, auch wenn es sich nicht an die Vorgabe haelt.
 */
export function klartextZuHtml(text: string): string {
    return text
        .split(/\n{2,}/)
        .map(block => block.trim())
        .filter(Boolean)
        .map(block => {
            const zeilen = block.split('\n').map(z => z.trim()).filter(Boolean);
            const istListe = zeilen.length > 0 && zeilen.every(z => /^[-*]\s+/.test(z));
            if (istListe) {
                const punkte = zeilen
                    .map(z => `<li>${maskiere(z.replace(/^[-*]\s+/, ''))}</li>`)
                    .join('');
                return `<ul>${punkte}</ul>`;
            }
            return `<p>${maskiere(block).replace(/\n/g, '<br>')}</p>`;
        })
        .join('');
}

const BLOCK_MUSTER = /<p[^>]*>([\s\S]*?)<\/p>|<(?:ul|ol)[^>]*>([\s\S]*?)<\/(?:ul|ol)>/gi;
const LISTENPUNKT_MUSTER = /<li[^>]*>([\s\S]*?)<\/li>/gi;

/**
 * Raeumt den Inhalt eines einzelnen Absatz- oder Listenpunkt-Blocks auf:
 * br wird zum Zeilenumbruch, alle uebrigen Tags (b, strong, i, em, span,
 * ein verschachteltes p in einem li, ...) fallen weg, ihr Text bleibt.
 *
 * Die Tags muessen VOR den Entitaeten verschwinden: solange &lt; und &gt;
 * noch als Entitaet dastehen, sind es keine echten spitzen Klammern und das
 * Tag-Muster fasst sie nicht an. Erst danach werden sie zurueckgewandelt -
 * sonst wuerde ein vom Modell maskiertes "<script>" hier zu einem echten Tag
 * und im naechsten Schritt gleich wieder verschwinden.
 *
 * &amp; wird zuletzt entpackt: ein durch &amp; entstandenes "&" darf nicht
 * zusammen mit direkt folgendem Text erneut wie eine Entitaet aussehen
 * (aus dem maskierten "&amp;lt;" wuerde sonst faelschlich "<" statt "&lt;").
 */
function wandleInlineInhaltZurueck(html: string): string {
    return html
        .replace(/<br\s*\/?>/gi, '\n')
        .replace(/<[^>]+>/g, '')
        .replace(/&nbsp;/g, ' ')
        .replace(/&lt;/g, '<')
        .replace(/&gt;/g, '>')
        .replace(/&amp;/g, '&');
}

/**
 * Wandelt Beitrags-HTML zurueck in Klartext - das Gegenstueck zu
 * klartextZuHtml. Jeder <p>-Block wird ein eigener Absatz (Leerzeile
 * dazwischen), br darin ein einzelner Zeilenumbruch. <ul> und <ol> werden zu
 * Zeilen mit fuehrendem "- ", eine pro <li>; die Listen-Tags selbst
 * verschwinden als "uebriger Tag" wie alle anderen.
 *
 * Wird gebraucht, weil an die KI Klartext geht, nicht HTML (siehe
 * SchrittText.tsx): das Backend-DTO dokumentiert das Feld als Klartext, und
 * die Systemanweisung sagt der KI ausdruecklich, keine HTML-Tags zu
 * verwenden. Schickte man stattdessen das rohe <p>...</p> aus dem Editor,
 * koennte sich das Modell am Eingabeformat statt an der Anweisung
 * orientieren und HTML zurueckliefern - klartextZuHtml wuerde das dann
 * maskieren, und im Beitrag stuende fuer den Leser sichtbar "&lt;p&gt;".
 */
export function htmlZuKlartext(html: string): string {
    const bloecke = [...html.matchAll(BLOCK_MUSTER)].map(treffer => {
        const [, pInhalt, listeInhalt] = treffer;
        if (listeInhalt !== undefined) {
            return [...listeInhalt.matchAll(LISTENPUNKT_MUSTER)]
                .map(li => `- ${wandleInlineInhaltZurueck(li[1]).trim()}`)
                .join('\n');
        }
        return wandleInlineInhaltZurueck(pInhalt).trim();
    });

    return bloecke.filter(Boolean).join('\n\n');
}

const STANDARD_LAENGE = 160;

/**
 * Baut die Kurzbeschreibung aus dem Text, so wie deriveExcerpt auf der Website.
 * Tags raus, Leerraum zusammenfalten, an der Wortgrenze kuerzen.
 */
export function leiteKurzbeschreibungAb(html: string, maxLaenge = STANDARD_LAENGE): string {
    const text = html
        .replace(/<[^>]*>/g, ' ')
        .replace(/&nbsp;/g, ' ')
        .replace(/&amp;/g, '&')
        .replace(/&lt;/g, '<')
        .replace(/&gt;/g, '>')
        .replace(/\s+/g, ' ')
        .trim();

    if (text.length <= maxLaenge) return text;

    const schnitt = text.slice(0, maxLaenge);
    const letztesLeerzeichen = schnitt.lastIndexOf(' ');
    const gekuerzt = letztesLeerzeichen > 0 ? schnitt.slice(0, letztesLeerzeichen) : schnitt;
    return `${gekuerzt}…`;
}

/**
 * Bereinigt Beitrags-HTML vor der Anzeige. Zweite Absicherung neben der
 * Bereinigung auf der Website: was hier durchkaeme, wuerde dort ohnehin
 * verworfen, aber die Vorschau soll zeigen, was tatsaechlich erscheint -
 * inklusive der verschaerften style-Regel (siehe Hook oben).
 */
export function bereinigeBeitragsHtml(html: string): string {
    return beitragsDomPurify.sanitize(html, {
        ALLOWED_TAGS: ERLAUBTE_TAGS,
        ALLOWED_ATTR: ERLAUBTE_ATTRIBUTE,
    });
}
