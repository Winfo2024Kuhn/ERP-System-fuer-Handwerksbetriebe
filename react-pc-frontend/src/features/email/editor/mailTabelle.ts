/**
 * Tabellen im Mail-Textfeld: neue Tabelle als HTML, Zeilen und Spalten
 * einfügen und löschen, Nachbarzelle für Tab. Reine DOM-Funktionen ohne React.
 *
 * Rahmen und Abstände stehen als Inline-Stil an Tabelle und Zellen: Outlook und
 * Gmail werfen `<style>`-Blöcke und Klassen weg, Inline-Stile zeigen sie.
 * Neue Zellen bekommen ein `<br>`, sonst sind sie leer zu schmal zum Hineinklicken.
 */

export const TABELLE_MAX = 8;

const TABELLE_STIL = 'border-collapse:collapse;width:100%;margin:8px 0';
const ZELLE_STIL = 'border:1px solid #cbd5e1;padding:4px 8px;vertical-align:top';

/** Markiert die gerade eingefügte Tabelle, damit der Cursor in ihre erste Zelle kommt. */
export const NEUE_TABELLE_MARKE = 'data-mail-tabelle-neu';

const begrenze = (wert: number) => Math.min(TABELLE_MAX, Math.max(1, Math.round(wert) || 1));

/**
 * HTML einer leeren Tabelle mit Absatz dahinter (zum Weiterschreiben).
 * Gebaut über DOM-Knoten, nie über zusammengesetzte Zeichenketten.
 */
export function tabellenHtml(zeilen: number, spalten: number): string {
    const dokument = document.implementation.createHTMLDocument('');
    const tabelle = dokument.createElement('table');
    tabelle.setAttribute('style', TABELLE_STIL);
    tabelle.setAttribute(NEUE_TABELLE_MARKE, '');
    const rumpf = dokument.createElement('tbody');
    for (let z = 0; z < begrenze(zeilen); z += 1) {
        const zeile = dokument.createElement('tr');
        for (let s = 0; s < begrenze(spalten); s += 1) {
            const zelle = dokument.createElement('td');
            zelle.setAttribute('style', ZELLE_STIL);
            zelle.append(dokument.createElement('br'));
            zeile.append(zelle);
        }
        rumpf.append(zeile);
    }
    tabelle.append(rumpf);
    const absatz = dokument.createElement('p');
    absatz.append(dokument.createElement('br'));
    return tabelle.outerHTML + absatz.outerHTML;
}

function alsElement(knoten: Node | null | undefined): Element | null {
    if (!knoten) return null;
    return knoten.nodeType === Node.ELEMENT_NODE ? (knoten as Element) : knoten.parentElement;
}

/** Tabellenzelle am Anfang der Auswahl – nur innerhalb des Felds. */
export function zelleBeiAuswahl(editor: HTMLElement, range: Range | null | undefined): HTMLTableCellElement | null {
    if (!range) return null;
    const zelle = alsElement(range.startContainer)?.closest('td, th');
    return zelle && editor.contains(zelle) ? (zelle as HTMLTableCellElement) : null;
}

function tabelleVon(zelle: HTMLTableCellElement): HTMLTableElement {
    return zelle.closest('table') as HTMLTableElement;
}

function leereZelleWie(vorlage: HTMLTableCellElement): HTMLTableCellElement {
    const zelle = vorlage.ownerDocument.createElement(vorlage.tagName.toLowerCase() as 'td' | 'th');
    const stil = vorlage.getAttribute('style');
    if (stil) zelle.setAttribute('style', stil);
    zelle.append(vorlage.ownerDocument.createElement('br'));
    return zelle;
}

/** Neue Zeile über bzw. unter der Zelle; liefert die Zelle derselben Spalte in der neuen Zeile. */
export function fuegeZeileEin(zelle: HTMLTableCellElement, wo: 'oben' | 'unten'): HTMLTableCellElement {
    const zeile = zelle.parentElement as HTMLTableRowElement;
    const neu = zeile.ownerDocument.createElement('tr');
    Array.from(zeile.cells).forEach(vorlage => neu.append(leereZelleWie(vorlage)));
    if (wo === 'oben') zeile.before(neu);
    else zeile.after(neu);
    return neu.cells[Math.min(zelle.cellIndex, neu.cells.length - 1)];
}

/** Neue Spalte links bzw. rechts der Zelle in jeder Zeile; liefert die neue Zelle in der Zeile der Zelle. */
export function fuegeSpalteEin(zelle: HTMLTableCellElement, wo: 'links' | 'rechts'): HTMLTableCellElement {
    const index = zelle.cellIndex;
    let ergebnis: HTMLTableCellElement | null = null;
    Array.from(tabelleVon(zelle).rows).forEach(zeile => {
        const vorlage = zeile.cells[Math.min(index, zeile.cells.length - 1)];
        if (!vorlage) return;
        const neu = leereZelleWie(vorlage);
        if (wo === 'links') vorlage.before(neu);
        else vorlage.after(neu);
        if (zeile === zelle.parentElement) ergebnis = neu;
    });
    return ergebnis ?? zelle;
}

/** Löscht die Tabelle. Liefert den Knoten dahinter (bzw. davor) für den Cursor. */
export function loescheTabelle(zelle: HTMLTableCellElement): Element | null {
    const tabelle = tabelleVon(zelle);
    const nachbar = tabelle.nextElementSibling ?? tabelle.previousElementSibling;
    tabelle.remove();
    return nachbar;
}

/** Löscht die Zeile der Zelle. Liefert die Zelle für den Cursor, `null`, wenn die Tabelle mit weg ist. */
export function loescheZeile(zelle: HTMLTableCellElement): HTMLTableCellElement | null {
    const tabelle = tabelleVon(zelle);
    if (tabelle.rows.length <= 1) {
        loescheTabelle(zelle);
        return null;
    }
    const zeile = zelle.parentElement as HTMLTableRowElement;
    const index = zeile.rowIndex;
    zeile.remove();
    const ziel = tabelle.rows[Math.min(index, tabelle.rows.length - 1)];
    return ziel.cells[Math.min(zelle.cellIndex, ziel.cells.length - 1)] ?? null;
}

/** Löscht die Spalte der Zelle in jeder Zeile. `null`, wenn die Tabelle mit weg ist. */
export function loescheSpalte(zelle: HTMLTableCellElement): HTMLTableCellElement | null {
    const tabelle = tabelleVon(zelle);
    const index = zelle.cellIndex;
    const zeile = zelle.parentElement as HTMLTableRowElement;
    if (Array.from(tabelle.rows).every(r => r.cells.length <= 1)) {
        loescheTabelle(zelle);
        return null;
    }
    Array.from(tabelle.rows).forEach(r => {
        if (r.cells.length > 1) r.cells[Math.min(index, r.cells.length - 1)]?.remove();
    });
    return zeile.cells[Math.min(index, zeile.cells.length - 1)] ?? null;
}

/** Nächste (1) bzw. vorige (-1) Zelle derselben Tabelle, zeilenweise; `null` am Rand. */
export function nachbarZelle(zelle: HTMLTableCellElement, richtung: 1 | -1): HTMLTableCellElement | null {
    const alle = Array.from(tabelleVon(zelle).rows).flatMap(zeile => Array.from(zeile.cells));
    return alle[alle.indexOf(zelle) + richtung] ?? null;
}

/** Änderungen aus dem Menü „Tabelle“. */
export type TabellenAktion =
    | 'zeileOben' | 'zeileUnten' | 'spalteLinks' | 'spalteRechts'
    | 'zeileLoeschen' | 'spalteLoeschen' | 'tabelleLoeschen';

/** Führt eine Änderung an der Tabelle der Zelle aus. Liefert, wohin der Cursor soll. */
export function aendereTabelle(zelle: HTMLTableCellElement, aktion: TabellenAktion): Element | null {
    switch (aktion) {
        case 'zeileOben': return fuegeZeileEin(zelle, 'oben');
        case 'zeileUnten': return fuegeZeileEin(zelle, 'unten');
        case 'spalteLinks': return fuegeSpalteEin(zelle, 'links');
        case 'spalteRechts': return fuegeSpalteEin(zelle, 'rechts');
        case 'zeileLoeschen': return loescheZeile(zelle);
        case 'spalteLoeschen': return loescheSpalte(zelle);
        case 'tabelleLoeschen': return loescheTabelle(zelle);
    }
}

/**
 * Setzt die Auswahl in ein Element: In einer Zelle mit Text wird der Inhalt
 * markiert (Tippen ersetzt ihn, wie in Word), sonst steht der Cursor am Anfang.
 */
export function waehleInhalt(element: Element): void {
    const range = element.ownerDocument.createRange();
    if (element.matches('td, th') && (element.textContent ?? '').trim()) range.selectNodeContents(element);
    else {
        range.setStart(element, 0);
        range.collapse(true);
    }
    const auswahl = window.getSelection();
    auswahl?.removeAllRanges();
    auswahl?.addRange(range);
}

/** Markiert in der geänderten Tabelle die Zelle, in die danach der Cursor kommt. */
export const ZIEL_MARKE = 'data-mail-zelle-ziel';

/**
 * Baut die Tabelle der Zelle als geänderte Kopie: `aendern` bekommt die passende
 * Zelle der Kopie und liefert die Zielzelle. Ergebnis ist das HTML der neuen
 * Tabelle (Ziel markiert) oder `null`, wenn die Tabelle ganz wegfällt. Das
 * Original bleibt unberührt, damit der Aufrufer es per `insertHTML` ersetzen kann.
 */
export function tabelleMitAenderung(
    zelle: HTMLTableCellElement,
    aendern: (kopie: HTMLTableCellElement) => Element | null,
): string | null {
    const zeile = zelle.parentElement as HTMLTableRowElement;
    const kopie = tabelleVon(zelle).cloneNode(true) as HTMLTableElement;
    // Eine Hülle, damit „Tabelle löschen“ die Kopie wirklich entfernen kann.
    const huelle = zelle.ownerDocument.createElement('div');
    huelle.append(kopie);
    const ziel = aendern(kopie.rows[zeile.rowIndex].cells[zelle.cellIndex]);
    if (!ziel || kopie.parentElement !== huelle) return null;
    ziel.setAttribute(ZIEL_MARKE, '');
    return kopie.outerHTML;
}
