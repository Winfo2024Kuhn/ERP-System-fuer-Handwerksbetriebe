import { useCallback, useEffect, useId, useRef, useState, type FocusEvent, type KeyboardEvent, type RefObject } from 'react';
import { fuehreAus, setzeSchriftgroesse, type MailSchriftgroesse } from './mailEditorDom';
import { formatStand, gleicherStand, KEIN_FORMAT, type MailBefehl, type MailFormatStand } from './mailFormate';
import { fokussiereNaechstesNach } from './mailLeisteTasten';
import { useMailAuswahl } from './useMailAuswahl';
import { useMailLinks } from './useMailLinks';

export interface MailEditorOptionen {
    /** Ref des Aufrufers auf das editierbare Element (Befüllen, `innerHTML` lesen). */
    editorRef: RefObject<HTMLDivElement | null>;
}

/**
 * Steuerung eines Mail-Textfelds: Auswahl merken, Formatierung, Links. Ein
 * Objekt für Leiste (`MailFormatLeiste`) und Feld (`MailTextFeld`), damit die
 * Leiste auch außerhalb des Felds stehen kann (z. B. in der Kopfzeile).
 *
 * <p>Jede Änderung löst am Feld ein `input`-Ereignis aus – Aufrufer hören wie
 * beim Tippen auf `onInput` (Entwurf speichern, Body übernehmen).</p>
 */
export function useMailEditor({ editorRef }: MailEditorOptionen) {
    const linkLeisteId = useId();
    const [aktiv, setAktiv] = useState<MailFormatStand>(KEIN_FORMAT);
    const zeigeLink = useRef<() => void>(() => undefined);

    const meldeAenderung = useCallback(() => {
        editorRef.current?.dispatchEvent(new Event('input', { bubbles: true }));
    }, [editorRef]);

    const aktualisiere = useCallback(() => {
        const editor = editorRef.current;
        const auswahl = window.getSelection();
        const range = editor && auswahl?.rangeCount && editor.contains(auswahl.getRangeAt(0).commonAncestorContainer)
            ? auswahl.getRangeAt(0) : null;
        const neu = formatStand(editor, range);
        setAktiv(alt => (gleicherStand(alt, neu) ? alt : neu));
    }, [editorRef]);

    const beiAuswahl = useCallback(() => {
        aktualisiere();
        zeigeLink.current();
    }, [aktualisiere]);

    const auswahl = useMailAuswahl(editorRef, beiAuswahl);
    const links = useMailLinks(editorRef, auswahl, meldeAenderung);
    const beiAuswahlImFeld = useCallback(() => {
        auswahl.merken();
        beiAuswahl();
    }, [auswahl, beiAuswahl]);
    useEffect(() => { zeigeLink.current = links.zeigeLinkAnAuswahl; }, [links.zeigeLinkAnAuswahl]);

    // Enter erzeugt <p> statt <div>, damit Absätze in jedem Mailprogramm gleich aussehen.
    useEffect(() => { fuehreAus('defaultParagraphSeparator', 'p'); }, []);

    const nachBefehl = useCallback(() => {
        auswahl.merken();
        beiAuswahl();
        meldeAenderung();
    }, [auswahl, beiAuswahl, meldeAenderung]);

    const formatiere = useCallback((befehl: MailBefehl) => {
        auswahl.wiederherstellen();
        // Fett & Co. als <b>, <i>, <u> – das verstehen alle Mailprogramme.
        fuehreAus('styleWithCSS', 'false');
        fuehreAus(befehl);
        nachBefehl();
    }, [auswahl, nachBefehl]);

    const mitStil = useCallback((befehl: 'foreColor' | 'hiliteColor', wert: string) => {
        auswahl.wiederherstellen();
        // Farben als <span style="…"> statt <font color>.
        fuehreAus('styleWithCSS', 'true');
        fuehreAus(befehl, wert);
        fuehreAus('styleWithCSS', 'false');
        nachBefehl();
    }, [auswahl, nachBefehl]);

    const schriftgroesse = useCallback((groesse: MailSchriftgroesse) => {
        auswahl.wiederherstellen();
        const editor = editorRef.current;
        if (groesse === 'normal' && aktiv.ueberschrift) fuehreAus('formatBlock', '<p>');
        if (editor) setzeSchriftgroesse(editor, groesse);
        nachBefehl();
    }, [auswahl, editorRef, aktiv.ueberschrift, nachBefehl]);

    const ueberschrift = useCallback(() => {
        auswahl.wiederherstellen();
        fuehreAus('formatBlock', aktiv.ueberschrift ? '<p>' : '<h3>');
        nachBefehl();
    }, [auswahl, aktiv.ueberschrift, nachBefehl]);

    const zurueckInsFeld = useCallback(() => {
        links.schliesseLeiste(true);
        auswahl.wiederherstellen();
    }, [links, auswahl]);

    /** Tab aus der Link-Leiste: weiter zum nächsten Bedienelement hinter dem Feld. */
    const verlasseFeld = useCallback(() => {
        links.schliesseLeiste(true);
        fokussiereNaechstesNach(editorRef.current);
    }, [links, editorRef]);

    const beiTaste = useCallback((event: KeyboardEvent<HTMLDivElement>) => {
        if ((event.metaKey || event.ctrlKey) && !event.altKey && event.key.toLowerCase() === 'k') {
            event.preventDefault();
            links.oeffneDialog();
            return;
        }
        if (!links.leistenLink) return;
        if (event.key === 'Escape') {
            // Nur die Link-Leiste schließen – nicht den Dialog, in dem das Feld steht.
            event.preventDefault();
            event.stopPropagation();
            links.schliesseLeiste(true);
        } else if (event.key === 'Tab' && !event.shiftKey) {
            event.preventDefault();
            document.getElementById(linkLeisteId)?.querySelector<HTMLButtonElement>('button')?.focus();
        }
    }, [links, linkLeisteId]);

    const beimVerlassen = useCallback((event: FocusEvent<HTMLDivElement>) => {
        if (!document.getElementById(linkLeisteId)?.contains(event.relatedTarget as Node | null)) links.schliesseLeiste();
    }, [links, linkLeisteId]);

    return {
        editorRef, linkLeisteId, aktiv, links,
        formatiere, schriftgroesse, ueberschrift, zurueckInsFeld, verlasseFeld,
        farbe: (wert: string) => mitStil('foreColor', wert),
        marker: (wert: string) => mitStil('hiliteColor', wert),
        feldEreignisse: {
            onKeyDown: beiTaste,
            // Escape schließt die Link-Leiste – ihr Loslassen darf sie nicht wieder öffnen.
            onKeyUp: (event: KeyboardEvent<HTMLDivElement>) => { if (event.key !== 'Escape') beiAuswahlImFeld(); },
            // Ein Klick zeigt die Link-Leiste wieder, auch nach Escape.
            onMouseUp: () => {
                auswahl.merken();
                aktualisiere();
                links.zeigeLinkAnAuswahl(true);
            },
            onBlur: beimVerlassen,
            onPaste: links.beimEinfuegen,
        },
    };
}

export type MailEditor = ReturnType<typeof useMailEditor>;
