import { sanitizeHtml } from '../../../lib/htmlSanitizer';

/**
 * DOM-Bausteine des Mail-Textfelds (natives contentEditable).
 *
 * Formatierung läuft über `document.execCommand`: Das behält den Rückgängig-
 * Stapel des Browsers und lässt fremdes HTML (Zitate, Signaturen) unangetastet.
 * Wo der Browser einen Befehl nicht ausführt, bauen die Funktionen das Ergebnis
 * selbst als DOM-Knoten (Text über `textContent`, Adresse über `setAttribute`) –
 * nie über ungeprüftes HTML.
 */

const MARKE = 'data-mail-link-neu';

/** Schriftgrößen der Leiste. „Normal“ nimmt jede eigene Größe wieder heraus. */
export type MailSchriftgroesse = 'klein' | 'normal' | 'gross';
const GROESSE_PX: Record<MailSchriftgroesse, string> = { klein: '13px', normal: '', gross: '18px' };

/** Führt einen Formatierungsbefehl aus. `false`, wenn der Browser ihn nicht kennt oder ablehnt. */
export function fuehreAus(befehl: string, wert?: string): boolean {
    const dokument = document as Document & { execCommand?: Document['execCommand'] };
    if (typeof dokument.execCommand !== 'function') return false;
    try {
        return dokument.execCommand(befehl, false, wert);
    } catch {
        return false;
    }
}

/** Ist ein Format an der Auswahl aktiv (Fett, Liste …)? Ohne Browser-Unterstützung `false`. */
export function befehlAktiv(befehl: string): boolean {
    const dokument = document as Document & { queryCommandState?: Document['queryCommandState'] };
    if (typeof dokument.queryCommandState !== 'function') return false;
    try {
        return dokument.queryCommandState(befehl);
    } catch {
        return false;
    }
}

/** Aktueller Wert eines Befehls, z. B. der Absatztyp bei `formatBlock`. */
export function befehlWert(befehl: string): string {
    const dokument = document as Document & { queryCommandValue?: Document['queryCommandValue'] };
    if (typeof dokument.queryCommandValue !== 'function') return '';
    try {
        return dokument.queryCommandValue(befehl) ?? '';
    } catch {
        return '';
    }
}

function waehleAus(range: Range): void {
    const auswahl = window.getSelection();
    auswahl?.removeAllRanges();
    auswahl?.addRange(range);
}

/** Setzt den Cursor direkt hinter einen Knoten. */
export function cursorHinter(knoten: Node): void {
    const range = document.createRange();
    range.setStartAfter(knoten);
    range.collapse(true);
    waehleAus(range);
}

function alsElement(knoten: Node | null | undefined): Element | null {
    if (!knoten) return null;
    return knoten.nodeType === Node.ELEMENT_NODE ? (knoten as Element) : knoten.parentElement;
}

/** Link, in dem die Auswahl beginnt – nur innerhalb des Felds. */
export function linkBeiAuswahl(editor: HTMLElement, range: Range | null | undefined): HTMLAnchorElement | null {
    if (!range) return null;
    const kandidaten = [range.startContainer, range.startContainer.childNodes[range.startOffset]];
    for (const kandidat of kandidaten) {
        const link = alsElement(kandidat)?.closest('a');
        if (link && link !== editor && editor.contains(link)) return link as HTMLAnchorElement;
    }
    return null;
}

function letzterLinkMit(editor: HTMLElement, href: string): HTMLAnchorElement | null {
    const treffer = Array.from(editor.querySelectorAll('a')).filter(a => a.getAttribute('href') === href);
    return treffer.at(-1) ?? null;
}

/**
 * Setzt an der Auswahl einen Link mit eigenem Text ein (markierter Text wird ersetzt)
 * und stellt den Cursor dahinter. Liefert den eingesetzten Link.
 */
export function fuegeLinkEin(editor: HTMLElement, range: Range, text: string, href: string): HTMLAnchorElement {
    const link = editor.ownerDocument.createElement('a');
    link.setAttribute('href', href);
    link.setAttribute(MARKE, '');
    link.textContent = text;
    waehleAus(range);
    let ergebnis: HTMLAnchorElement | null = null;
    if (fuehreAus('insertHTML', sanitizeHtml(link.outerHTML))) {
        ergebnis = editor.querySelector<HTMLAnchorElement>(`a[${MARKE}]`) ?? letzterLinkMit(editor, href);
    }
    if (!ergebnis) {
        range.deleteContents();
        range.insertNode(link);
        ergebnis = link;
    }
    ergebnis.removeAttribute(MARKE);
    cursorHinter(ergebnis);
    return ergebnis;
}

/** Macht den markierten Inhalt samt Formatierung zum Link (beim Einfügen einer Adresse). */
export function machAuswahlZumLink(editor: HTMLElement, range: Range, href: string): HTMLAnchorElement | null {
    waehleAus(range);
    if (fuehreAus('createLink', href)) {
        const aktuell = window.getSelection()?.rangeCount ? window.getSelection()!.getRangeAt(0) : range;
        const link = linkBeiAuswahl(editor, aktuell) ?? letzterLinkMit(editor, href);
        if (link) cursorHinter(link);
        return link;
    }
    const link = editor.ownerDocument.createElement('a');
    link.setAttribute('href', href);
    link.appendChild(range.extractContents());
    range.insertNode(link);
    cursorHinter(link);
    return link;
}

/** Ändert Adresse und – nur wenn anders – den Text eines Links. */
export function aendereLink(link: HTMLAnchorElement, text: string, href: string): void {
    if (link.textContent !== text) link.textContent = text;
    link.setAttribute('href', href);
    cursorHinter(link);
}

function entpacke(element: Element): Node[] {
    const kinder = Array.from(element.childNodes);
    element.replaceWith(...kinder);
    return kinder;
}

/** Entfernt einen Link, der Text samt Formatierung bleibt stehen. */
export function entferneLink(link: HTMLAnchorElement): void {
    const kinder = entpacke(link);
    const letztes = kinder.at(-1);
    if (letztes) cursorHinter(letztes);
}

function entferneGroessen(wurzel: Element): void {
    wurzel.querySelectorAll<HTMLElement>('[style]').forEach(element => {
        element.style.removeProperty('font-size');
        if (element.getAttribute('style')?.trim()) return;
        element.removeAttribute('style');
        if (element.tagName === 'SPAN' && element.attributes.length === 0) entpacke(element);
    });
}

/**
 * Setzt die Schriftgröße der Auswahl als `<span style="font-size:…">`, das jedes
 * Mailprogramm versteht (statt `<font size>`). Die Auswahl bleibt markiert.
 */
export function setzeSchriftgroesse(editor: HTMLElement, groesse: MailSchriftgroesse): boolean {
    fuehreAus('styleWithCSS', 'false');
    if (!fuehreAus('fontSize', '7')) return false;
    const neu: Node[] = [];
    editor.querySelectorAll('font[size="7"]').forEach(font => {
        entferneGroessen(font);
        const px = GROESSE_PX[groesse];
        if (!px) {
            neu.push(...entpacke(font));
            return;
        }
        const span = editor.ownerDocument.createElement('span');
        span.style.fontSize = px;
        span.append(...Array.from(font.childNodes));
        font.replaceWith(span);
        neu.push(span);
    });
    const erstes = neu[0];
    const letztes = neu.at(-1);
    if (erstes && letztes) {
        const range = document.createRange();
        range.setStartBefore(erstes);
        range.setEndAfter(letztes);
        waehleAus(range);
    }
    return true;
}
