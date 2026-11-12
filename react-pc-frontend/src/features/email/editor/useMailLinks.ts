import { useCallback, useEffect, useRef, useState, type ClipboardEvent, type RefObject } from 'react';
import { aendereLink, entferneLink, fuegeLinkEin, linkBeiAuswahl, machAuswahlZumLink } from './mailEditorDom';
import { webAdresseAusEinfuegen } from './mailLink';
import type { MailAuswahl } from './useMailAuswahl';

export interface MailLinkDialogStand {
    modus: 'neu' | 'aendern';
    text: string;
    href: string;
}

interface DialogZiel {
    link: HTMLAnchorElement | null;
    range: Range | null;
    /** Text der Markierung beim Öffnen – bleibt er gleich, behält der Link die Formatierung. */
    markiert: string;
}

/**
 * Links im Mail-Textfeld: Dialog „Link einfügen/ändern“, die kleine Leiste am
 * angeklickten Link und das Einfügen einer Adresse (über markiertem Text oder als Link mit
 * der Adresse als Text).
 * Jede Änderung meldet `meldeAenderung`, damit der Aufrufer (Entwurf, Body) sie mitbekommt.
 */
export function useMailLinks(
    editorRef: RefObject<HTMLDivElement | null>,
    auswahl: MailAuswahl,
    meldeAenderung: () => void,
) {
    const [dialog, setDialog] = useState<MailLinkDialogStand | null>(null);
    const [leistenLink, setLeistenLink] = useState<HTMLAnchorElement | null>(null);
    const ziel = useRef<DialogZiel>({ link: null, range: null, markiert: '' });
    const fokusNachDialog = useRef(false);
    /** Wohin der Cursor nach dem Dialog kommt: hinter den neuen Link bzw. die alte Markierung. */
    const rueckkehr = useRef<Range | null>(null);

    /** Mit Escape oder Tab geschlossen: bleibt zu, bis der Cursor den Link verlässt oder ihn anklickt. */
    const unterdrueckt = useRef<HTMLAnchorElement | null>(null);

    const zeigeLinkAnAuswahl = useCallback((nachKlick = false) => {
        if (nachKlick) unterdrueckt.current = null;
        const editor = editorRef.current;
        const link = editor ? linkBeiAuswahl(editor, auswahl.aktuelle()) : null;
        if (link && link === unterdrueckt.current) {
            setLeistenLink(null);
            return;
        }
        unterdrueckt.current = null;
        setLeistenLink(link);
    }, [editorRef, auswahl]);

    const oeffneDialogFuer = useCallback((link: HTMLAnchorElement | null, range: Range | null) => {
        const markiert = link ? '' : (range?.toString() ?? '').trim();
        ziel.current = { link, range: range?.cloneRange() ?? null, markiert };
        setLeistenLink(null);
        setDialog(link
            ? { modus: 'aendern', text: link.textContent ?? '', href: link.getAttribute('href') ?? '' }
            : { modus: 'neu', text: markiert, href: '' });
    }, []);

    const oeffneDialog = useCallback(() => {
        const editor = editorRef.current;
        if (!editor) return;
        const range = auswahl.aktuelle();
        oeffneDialogFuer(linkBeiAuswahl(editor, range), range);
    }, [editorRef, auswahl, oeffneDialogFuer]);

    const beendeDialog = useCallback((cursor: Range | null) => {
        rueckkehr.current = cursor;
        fokusNachDialog.current = true;
        setDialog(null);
    }, []);

    /** Abbrechen, X oder Escape: zurück zur Markierung von vorher. */
    const schliesseDialog = useCallback(() => beendeDialog(ziel.current.range), [beendeDialog]);

    const uebernehme = useCallback((text: string, href: string) => {
        const editor = editorRef.current;
        if (!editor) return;
        const { link, range, markiert } = ziel.current;
        if (link?.isConnected && editor.contains(link)) {
            aendereLink(link, text, href);
        } else {
            const stelle = range && editor.contains(range.startContainer) ? range : auswahl.aktuelle();
            if (!stelle) return;
            if (!stelle.collapsed && markiert && text === markiert) machAuswahlZumLink(editor, stelle, href);
            else fuegeLinkEin(editor, stelle, text, href);
        }
        auswahl.merken();
        meldeAenderung();
        const jetzt = window.getSelection();
        beendeDialog(jetzt?.rangeCount ? jetzt.getRangeAt(0).cloneRange() : null);
    }, [editorRef, auswahl, meldeAenderung, beendeDialog]);

    // Nach dem Dialog zurück in den Text. Ausdrücklich setzen: Beim Fokus springt der
    // Browser sonst an den Anfang des Felds, weil die Auswahl im Dialog lag.
    useEffect(() => {
        if (dialog || !fokusNachDialog.current) return;
        fokusNachDialog.current = false;
        if (rueckkehr.current) auswahl.setzen(rueckkehr.current);
        else auswahl.wiederherstellen();
    }, [dialog, auswahl]);

    const entferneLeistenLink = useCallback(() => {
        if (!leistenLink) return;
        entferneLink(leistenLink);
        setLeistenLink(null);
        auswahl.merken();
        editorRef.current?.focus({ preventScroll: true });
        meldeAenderung();
    }, [leistenLink, auswahl, editorRef, meldeAenderung]);

    const aendereLeistenLink = useCallback(() => {
        if (!leistenLink) return;
        // Abbrechen bringt den Cursor hinter den Link zurück.
        const hinterDemLink = document.createRange();
        hinterDemLink.setStartAfter(leistenLink);
        hinterDemLink.collapse(true);
        oeffneDialogFuer(leistenLink, hinterDemLink);
    }, [leistenLink, oeffneDialogFuer]);

    const schliesseLeiste = useCallback((bisVerlassen = false) => {
        if (bisVerlassen) unterdrueckt.current = leistenLink;
        setLeistenLink(null);
    }, [leistenLink]);

    const beimEinfuegen = useCallback((event: ClipboardEvent<HTMLDivElement>) => {
        const editor = editorRef.current;
        const href = webAdresseAusEinfuegen(event.clipboardData?.getData('text/plain') ?? '');
        const auswahlJetzt = window.getSelection();
        const range = auswahlJetzt?.rangeCount ? auswahlJetzt.getRangeAt(0) : null;
        if (!editor || !href || !range || !editor.contains(range.commonAncestorContainer)) return;
        event.preventDefault();
        // Markierter Text wird zum Link; ohne Markierung steht die Adresse selbst als Link da.
        if (range.collapsed) fuegeLinkEin(editor, range, href, href);
        else machAuswahlZumLink(editor, range, href);
        auswahl.merken();
        meldeAenderung();
    }, [editorRef, auswahl, meldeAenderung]);

    return {
        dialog, oeffneDialog, schliesseDialog, uebernehme,
        leistenLink, zeigeLinkAnAuswahl, aendereLeistenLink, entferneLeistenLink, schliesseLeiste,
        beimEinfuegen,
    };
}

export type MailLinks = ReturnType<typeof useMailLinks>;
