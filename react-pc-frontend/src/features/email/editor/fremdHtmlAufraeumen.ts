/**
 * Räumt HTML aus Word, Excel, Outlook und Webseiten auf, bevor es ins
 * Mail-Textfeld kommt. Was das Aussehen trägt (Tabellen, Rahmen, fett, Farben,
 * Schriftgrößen, Listen, Links), bleibt; Office-Beiwerk fliegt raus.
 *
 * Arbeitet auf einem per `parseHtmlInert()` geparsten Knoten (dort lädt und
 * läuft nichts). Bereinigt wird danach trotzdem mit `sanitizeHtml()`.
 */

/**
 * Elemente, die samt Inhalt wegfallen: Kopfteil, Office-Zeichenobjekte, eingebettete
 * Medien (laden etwas nach) und Formularfelder (in einer Mail ohne Sinn).
 */
const WEG_SAMT_INHALT = 'style, meta, link, title, xml, script, template, picture, source, svg, video, audio, canvas, '
    + 'object, embed, iframe, input, button, select, textarea';

/**
 * Attribute ohne Bedeutung in einer Mail (Klassen gibt es dort nicht, IDs könnten sich
 * doppeln) und solche, die Bilder nachladen (`background`, `srcset`, `poster`).
 */
const WEG_ATTRIBUTE = ['class', 'id', 'lang', 'background', 'srcset', 'poster'];

/** Werte, die etwas nachladen; `\` fängt CSS-Escapes wie `u\72l(…)` ab. */
const NACHLADEN = ['url(', 'image(', 'image-set(', 'cross-fade(', 'expression(', '\\'];

/** `p.MsoNormal`, `.xl65` – ein Tag (optional) und genau eine Klasse. */
const KLASSEN_SELEKTOR = /^([a-z][a-z0-9]*)?\.([a-z0-9_-]+)$/i;

interface KlassenRegel {
    tag: string | null;
    klasse: string;
    deklarationen: string[];
}

/** Zerlegt `a:b; c:d` in einzelne Deklarationen. */
function deklarationenAus(text: string): string[] {
    return text.split(';').map(teil => teil.trim()).filter(teil => teil.includes(':'));
}

/** Behält eine Deklaration? Office-Eigenheiten und alles, was etwas nachlädt, fällt weg. */
function bereinigeDeklaration(deklaration: string): string | null {
    const doppelpunkt = deklaration.indexOf(':');
    const name = deklaration.slice(0, doppelpunkt).trim().toLowerCase();
    const wert = deklaration.slice(doppelpunkt + 1).trim();
    const klein = deklaration.toLowerCase();
    if (!name || !wert || name.startsWith('mso-') || NACHLADEN.some(muster => klein.includes(muster))) return null;
    if (name === 'position' || name === 'behavior') return null;
    // „windowtext“ ist eine Windows-Systemfarbe, die nicht jedes Mailprogramm kennt.
    return `${name}:${wert.replace(/\bwindowtext\b/gi, '#000000')}`;
}

/** Entfernt CSS-Kommentare (Schrägstrich-Stern bis Stern-Schrägstrich), linear ohne regulären Ausdruck. */
function ohneKommentare(css: string): string {
    let ergebnis = '';
    let start = 0;
    for (;;) {
        const auf = css.indexOf('/*', start);
        if (auf < 0) return ergebnis + css.slice(start);
        ergebnis += css.slice(start, auf);
        const zu = css.indexOf('*/', auf + 2);
        if (zu < 0) return ergebnis;
        start = zu + 2;
    }
}

/** Klassen-Regeln aus `<style>`-Blöcken (Word-Absatzvorlagen, Excel-Zellrahmen), in Reihenfolge. */
function klassenRegeln(wurzel: HTMLElement): KlassenRegel[] {
    const regeln: KlassenRegel[] = [];
    wurzel.querySelectorAll('style').forEach(stil => {
        const text = ohneKommentare(stil.textContent ?? '').split('<!--').join('').split('-->').join('');
        let start = 0;
        for (;;) {
            const auf = text.indexOf('{', start);
            const zu = auf < 0 ? -1 : text.indexOf('}', auf);
            if (auf < 0 || zu < 0) break;
            const deklarationen = deklarationenAus(text.slice(auf + 1, zu));
            text.slice(start, auf).split(',').forEach(selektor => {
                const treffer = KLASSEN_SELEKTOR.exec(selektor.trim());
                if (treffer) regeln.push({ tag: treffer[1]?.toUpperCase() ?? null, klasse: treffer[2], deklarationen });
            });
            start = zu + 1;
        }
    });
    return regeln;
}

/** Schreibt die Klassen-Regeln als Inline-Stil an die Elemente; eigene Inline-Stile gewinnen. */
function klassenInlineSetzen(wurzel: HTMLElement): void {
    const regeln = klassenRegeln(wurzel);
    if (regeln.length === 0) return;
    const mitKlasse = Array.from(wurzel.querySelectorAll('[class]'));
    const zusatz = new Map<Element, string[]>();
    for (const regel of regeln) {
        mitKlasse.forEach(element => {
            if (!element.classList.contains(regel.klasse) || (regel.tag && element.tagName !== regel.tag)) return;
            zusatz.set(element, [...(zusatz.get(element) ?? []), ...regel.deklarationen]);
        });
    }
    zusatz.forEach((deklarationen, element) => {
        const eigene = element.getAttribute('style') ?? '';
        element.setAttribute('style', [...deklarationen, ...deklarationenAus(eigene)].join(';'));
    });
}

function entferneKommentare(wurzel: HTMLElement): void {
    const walker = wurzel.ownerDocument.createTreeWalker(wurzel, NodeFilter.SHOW_COMMENT);
    const kommentare: Node[] = [];
    for (let knoten = walker.nextNode(); knoten; knoten = walker.nextNode()) kommentare.push(knoten);
    kommentare.forEach(kommentar => kommentar.parentNode?.removeChild(kommentar));
}

function entpacke(element: Element): void {
    element.replaceWith(...Array.from(element.childNodes));
}

/** `<o:p>` und VML-Zeichnungen (`<v:…>`) weg, andere Office-Tags (`<w:…>`) entpacken. */
function entferneOfficeTags(wurzel: HTMLElement): void {
    Array.from(wurzel.querySelectorAll('*'))
        .filter(element => element.tagName.includes(':'))
        .forEach(element => {
            const praefix = element.tagName.split(':')[0].toLowerCase();
            if (praefix === 'o' || praefix === 'v') element.remove();
            else entpacke(element);
        });
}

function bereinigeAttribute(wurzel: HTMLElement): void {
    wurzel.querySelectorAll('*').forEach(element => {
        WEG_ATTRIBUTE.forEach(name => element.removeAttribute(name));
        const stil = element.getAttribute('style');
        if (stil === null) return;
        const sauber = deklarationenAus(stil).map(bereinigeDeklaration).filter(Boolean).join(';');
        if (sauber) element.setAttribute('style', sauber);
        else element.removeAttribute('style');
    });
    // Nackte <span> ohne Stil tragen nichts mehr.
    Array.from(wurzel.querySelectorAll('span')).filter(span => span.attributes.length === 0).forEach(entpacke);
}

/**
 * Räumt fremdes HTML im geparsten Knoten auf. Bilder behandelt der Aufrufer
 * vorher selbst (Anhänge), damit keines verloren geht.
 */
export function raeumeFremdHtmlAuf(wurzel: HTMLElement): void {
    entferneKommentare(wurzel);
    klassenInlineSetzen(wurzel);
    wurzel.querySelectorAll(WEG_SAMT_INHALT).forEach(element => element.remove());
    // Formulare entpacken: Ihr Inhalt ist oft der eigentliche Text einer Webseite.
    wurzel.querySelectorAll('form').forEach(entpacke);
    entferneOfficeTags(wurzel);
    bereinigeAttribute(wurzel);
}
