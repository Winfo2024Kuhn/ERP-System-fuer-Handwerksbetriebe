import { useCallback, useEffect, useRef, useState, type KeyboardEvent, type RefObject } from 'react';
import { fuehreAus, setzeSchriftgroesse, type MailSchriftgroesse } from './mailEditorDom';
import { formatStand, gleicherStand, KEIN_FORMAT, type MailBefehl, type MailFormatStand } from './mailFormate';
import { useMailAuswahl } from './useMailAuswahl';
import { useMailEinfuegen, type MailDateienAnnehmen } from './useMailEinfuegen';
import { useMailLinks } from './useMailLinks';
import { useMailTabelle } from './useMailTabelle';
import { useMailTasten } from './useMailTasten';

export interface MailEditorOptionen {
    /** Ref des Aufrufers auf das editierbare Element (Befüllen, `innerHTML` lesen). */
    editorRef: RefObject<HTMLDivElement | null>;
    /** Eingefügte oder hineingezogene Dateien und Bilder in die Anhangsliste; ohne: Hinweis statt Anhang. */
    onDateien?: MailDateienAnnehmen;
}

/**
 * Steuerung eines Mail-Textfelds: Auswahl merken, Formatierung, Links,
 * Tabellen, Einfügen (Bilder und Dateien als Anhang). Ein
 * Objekt für Leiste (`MailFormatLeiste`) und Feld (`MailTextFeld`), damit die
 * Leiste auch außerhalb des Felds stehen kann (z. B. in der Kopfzeile).
 *
 * <p>Jede Änderung löst am Feld ein `input`-Ereignis aus – Aufrufer hören wie
 * beim Tippen auf `onInput` (Entwurf speichern, Body übernehmen).</p>
 */
export function useMailEditor({ editorRef, onDateien }: MailEditorOptionen) {
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
    const tabelle = useMailTabelle(editorRef, auswahl, nachBefehl);
    const einfuegen = useMailEinfuegen({ editorRef, auswahl, meldeAenderung, onDateien, adresseEinfuegen: links.beimEinfuegen });

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

    const tasten = useMailTasten({ editorRef, auswahl, links, tabellenTaste: tabelle.beiTaste });
    const { linkLeisteId, zurueckInsFeld, verlasseFeld } = tasten;

    return {
        editorRef, linkLeisteId, aktiv, links, tabelle,
        formatiere, schriftgroesse, ueberschrift, zurueckInsFeld, verlasseFeld,
        farbe: (wert: string) => mitStil('foreColor', wert),
        marker: (wert: string) => mitStil('hiliteColor', wert),
        feldEreignisse: {
            onKeyDown: tasten.beiTaste,
            // Escape schließt die Link-Leiste – ihr Loslassen darf sie nicht wieder öffnen.
            onKeyUp: (event: KeyboardEvent<HTMLDivElement>) => { if (event.key !== 'Escape') beiAuswahlImFeld(); },
            // Ein Klick zeigt die Link-Leiste wieder, auch nach Escape.
            onMouseUp: () => {
                auswahl.merken();
                aktualisiere();
                links.zeigeLinkAnAuswahl(true);
            },
            onBlur: tasten.beimVerlassen,
            ...einfuegen,
        },
    };
}

export type MailEditor = ReturnType<typeof useMailEditor>;
