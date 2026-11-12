import { useCallback, useEffect, useMemo, useRef, type RefObject } from 'react';

export interface MailAuswahl {
    /** Merkt die aktuelle Auswahl, wenn sie im Feld liegt. */
    merken: () => void;
    /** Auswahl im Feld: die lebende, sonst die gemerkte, sonst der Anfang des ersten Absatzes. */
    aktuelle: () => Range | null;
    /** Fokus ins Feld und die gemerkte Auswahl zurückholen (nach Klick in Leiste, Menü, Dialog). */
    wiederherstellen: () => Range | null;
    /** Fokus ins Feld und genau diese Auswahl setzen (z. B. die Markierung von vor dem Dialog). */
    setzen: (range: Range) => void;
}

function lebendeAuswahlIn(editor: HTMLElement): Range | null {
    const auswahl = window.getSelection();
    if (!auswahl?.rangeCount) return null;
    const range = auswahl.getRangeAt(0);
    return editor.contains(range.commonAncestorContainer) ? range : null;
}

function anfangDesFelds(editor: HTMLElement): Range {
    const range = document.createRange();
    range.setStart(editor.firstElementChild ?? editor, 0);
    range.collapse(true);
    return range;
}

/**
 * Merkt sich die Auswahl im Mail-Textfeld. Ein Klick in Leiste, Menü oder Dialog
 * nimmt dem Feld den Fokus; danach soll der Befehl trotzdem genau dort wirken,
 * wo der Nutzer markiert hatte. `onAuswahl` meldet jede Änderung im Feld
 * (aktive Formate, Link-Leiste).
 */
export function useMailAuswahl(editorRef: RefObject<HTMLDivElement | null>, onAuswahl: () => void): MailAuswahl {
    const gemerkt = useRef<Range | null>(null);
    const onAuswahlRef = useRef(onAuswahl);
    useEffect(() => { onAuswahlRef.current = onAuswahl; }, [onAuswahl]);

    const merken = useCallback(() => {
        const editor = editorRef.current;
        const range = editor ? lebendeAuswahlIn(editor) : null;
        if (range) gemerkt.current = range.cloneRange();
    }, [editorRef]);

    useEffect(() => {
        const beiAenderung = () => {
            const editor = editorRef.current;
            if (!editor || !lebendeAuswahlIn(editor)) return;
            merken();
            onAuswahlRef.current();
        };
        document.addEventListener('selectionchange', beiAenderung);
        return () => document.removeEventListener('selectionchange', beiAenderung);
    }, [editorRef, merken]);

    const aktuelle = useCallback(() => {
        const editor = editorRef.current;
        if (!editor) return null;
        const lebend = lebendeAuswahlIn(editor);
        if (lebend) return lebend;
        const alt = gemerkt.current;
        return alt && editor.contains(alt.startContainer) ? alt : anfangDesFelds(editor);
    }, [editorRef]);

    const wiederherstellen = useCallback(() => {
        const editor = editorRef.current;
        if (!editor) return null;
        const lebend = lebendeAuswahlIn(editor);
        if (document.activeElement !== editor) editor.focus({ preventScroll: true });
        if (lebend) return lebend;
        const range = aktuelle();
        if (range) {
            const auswahl = window.getSelection();
            auswahl?.removeAllRanges();
            auswahl?.addRange(range);
        }
        return range;
    }, [editorRef, aktuelle]);

    const setzen = useCallback((range: Range) => {
        const editor = editorRef.current;
        if (!editor || !editor.contains(range.startContainer)) return;
        if (document.activeElement !== editor) editor.focus({ preventScroll: true });
        const auswahl = window.getSelection();
        auswahl?.removeAllRanges();
        auswahl?.addRange(range);
        gemerkt.current = range.cloneRange();
    }, [editorRef]);

    return useMemo(() => ({ merken, aktuelle, wiederherstellen, setzen }), [merken, aktuelle, wiederherstellen, setzen]);
}
