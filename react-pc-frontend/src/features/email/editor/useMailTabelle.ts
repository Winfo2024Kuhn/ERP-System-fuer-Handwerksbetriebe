import { useCallback, useMemo, type KeyboardEvent, type RefObject } from 'react';
import { fuegeHtmlEin, fuehreAus } from './mailEditorDom';
import {
    NEUE_TABELLE_MARKE, ZIEL_MARKE, aendereTabelle, fuegeZeileEin, nachbarZelle, tabelleMitAenderung, tabellenHtml,
    waehleInhalt, zelleBeiAuswahl, type TabellenAktion,
} from './mailTabelle';
import type { MailAuswahl } from './useMailAuswahl';

/** Nimmt die Marke vom Element und setzt den Cursor hinein (bzw. in sein erstes `selektor`-Kind). */
function cursorAnMarke(editor: HTMLElement, marke: string, selektor = ''): void {
    const element = editor.querySelector(`[${marke}]`);
    element?.removeAttribute(marke);
    const ziel = selektor ? element?.querySelector(selektor) : element;
    if (ziel) waehleInhalt(ziel);
}

/**
 * Ersetzt die Tabelle der Zelle über den Browser (`insertHTML` bzw. `delete`), damit
 * Strg+Z die Änderung zurücknimmt. Danach steht der Cursor in der Zielzelle.
 */
function ersetzeTabelle(
    editor: HTMLElement,
    zelle: HTMLTableCellElement,
    aendern: (kopie: HTMLTableCellElement) => Element | null,
): void {
    const tabelle = zelle.closest('table');
    if (!tabelle) return;
    const html = tabelleMitAenderung(zelle, aendern);
    const range = document.createRange();
    range.selectNode(tabelle);
    if (html !== null) {
        fuegeHtmlEin(editor, range, html);
        cursorAnMarke(editor, ZIEL_MARKE);
        return;
    }
    const nachbar = tabelle.nextElementSibling ?? tabelle.previousElementSibling;
    window.getSelection()?.removeAllRanges();
    window.getSelection()?.addRange(range);
    if (fuehreAus('delete')) return;
    tabelle.remove();
    if (nachbar) waehleInhalt(nachbar);
}

/**
 * Tabellen im Mail-Textfeld: einfügen, Zeilen/Spalten ändern und Tab zwischen
 * Zellen. Alles läuft über `insertHTML`/`delete`, damit Strg+Z greift.
 * `nachBefehl` merkt die Auswahl, frischt die Leiste auf und meldet die Änderung.
 */
export function useMailTabelle(editorRef: RefObject<HTMLDivElement | null>, auswahl: MailAuswahl, nachBefehl: () => void) {
    const einfuegen = useCallback((zeilen: number, spalten: number) => {
        const stelle = auswahl.wiederherstellen();
        const editor = editorRef.current;
        if (!editor || !stelle) return;
        fuegeHtmlEin(editor, stelle, tabellenHtml(zeilen, spalten));
        cursorAnMarke(editor, NEUE_TABELLE_MARKE, 'td, th');
        nachBefehl();
    }, [auswahl, editorRef, nachBefehl]);

    const aendere = useCallback((aktion: TabellenAktion) => {
        const editor = editorRef.current;
        const zelle = editor ? zelleBeiAuswahl(editor, auswahl.wiederherstellen()) : null;
        if (!editor || !zelle) return;
        ersetzeTabelle(editor, zelle, kopie => aendereTabelle(kopie, aktion));
        nachBefehl();
    }, [auswahl, editorRef, nachBefehl]);

    /** Tab/Shift+Tab in einer Tabelle. `true`, wenn die Taste verbraucht ist. */
    const beiTaste = useCallback((event: KeyboardEvent<HTMLDivElement>): boolean => {
        if (event.key !== 'Tab' || event.altKey || event.ctrlKey || event.metaKey) return false;
        const editor = editorRef.current;
        const live = window.getSelection();
        const zelle = editor && live?.rangeCount ? zelleBeiAuswahl(editor, live.getRangeAt(0)) : null;
        if (!editor || !zelle) return false;
        const naechste = nachbarZelle(zelle, event.shiftKey ? -1 : 1);
        // Shift+Tab in der ersten Zelle: Fokus darf das Feld verlassen.
        if (!naechste && event.shiftKey) return false;
        event.preventDefault();
        if (naechste) {
            waehleInhalt(naechste);
            return true;
        }
        // Letzte Zelle: neue Zeile anhängen, Cursor in ihre erste Zelle.
        ersetzeTabelle(editor, zelle, kopie => (fuegeZeileEin(kopie, 'unten').parentElement as HTMLTableRowElement).cells[0]);
        nachBefehl();
        return true;
    }, [editorRef, nachBefehl]);

    return useMemo(() => ({ einfuegen, aendere, beiTaste }), [einfuegen, aendere, beiTaste]);
}
