import { zerlegeInLinks } from './linkErkennung';

/** Text in diesen Elementen bleibt, wie er ist (schon Link, Code, Bedienelement). */
const UEBERSPRINGEN = 'a, script, style, textarea, button, select, option, svg, math, title, noscript, iframe, xmp, noembed, noframes, [data-quote-btn]';
const SICHERER_REL = 'noopener noreferrer';

interface VerlinkenOptionen {
    /** Links öffnen in einem neuen Tab, ohne Zugriff auf das ERP (`target`, `rel`). */
    neuerTab?: boolean;
}

function baueLink(dokument: Document, text: string, href: string, neuerTab: boolean): HTMLAnchorElement {
    const link = dokument.createElement('a');
    link.setAttribute('href', href);
    if (neuerTab) {
        link.setAttribute('target', '_blank');
        link.setAttribute('rel', SICHERER_REL);
    }
    link.textContent = text;
    return link;
}

/**
 * Macht nackte Adressen in allen Textknoten unter `wurzel` zu Links. Gebaut
 * wird nur über `createElement`, `textContent` und `setAttribute` – nie über
 * HTML-Strings. Vorhandene Links bleiben unverändert; mit `neuerTab` bekommen
 * sie zusätzlich `rel="noopener noreferrer"`.
 */
export function verlinkeAdressen(wurzel: Element, { neuerTab = false }: VerlinkenOptionen = {}): void {
    const dokument = wurzel.ownerDocument;
    const walker = dokument.createTreeWalker(wurzel, NodeFilter.SHOW_TEXT);
    const textknoten: Text[] = [];
    for (let knoten = walker.nextNode(); knoten; knoten = walker.nextNode()) {
        if (!knoten.parentElement?.closest(UEBERSPRINGEN)) textknoten.push(knoten as Text);
    }
    for (const knoten of textknoten) {
        const stuecke = zerlegeInLinks(knoten.data);
        if (!stuecke.some(stueck => stueck.art === 'link')) continue;
        const neu = dokument.createDocumentFragment();
        for (const stueck of stuecke) {
            neu.append(stueck.art === 'link'
                ? baueLink(dokument, stueck.text, stueck.href, neuerTab)
                : dokument.createTextNode(stueck.text));
        }
        knoten.replaceWith(neu);
    }
    if (neuerTab) wurzel.querySelectorAll('a[href]').forEach(link => link.setAttribute('rel', SICHERER_REL));
}
