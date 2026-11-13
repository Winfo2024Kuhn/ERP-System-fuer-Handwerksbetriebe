import { useEffect, useRef, type RefObject } from 'react';
import { setzeEditorHtml } from '../email/mailEditorHtml';
import { briefanrede } from '../../lib/briefanrede';
import { escapeHtml } from '../../lib/emailAddress';
import { ersetzeAnredeZeile } from '../email/editor/anredeZeile';
import type { SteuerberaterAnsprechpartner } from './steuerberaterKontakt';
import type { Stundenart } from './stundenart';
import { stundenMailText } from './stundenMail';

interface StundenMailOptionen {
    offen: boolean;
    laedt: boolean;
    ansprechpartner: SteuerberaterAnsprechpartner | null;
    monat: number;
    jahr: number;
    stundenart: Stundenart;
    signatur: string;
}

/**
 * Befüllt das Mail-Textfeld der Stundenübermittlung. Der ganze Text wird nur
 * beim Öffnen und bei anderem Monat, Jahr oder anderer Stundenart gesetzt.
 * Wechselt danach der Ansprechpartner (auch über den Empfänger), wird nur die
 * Anredezeile getauscht, damit eigene Änderungen am Text bleiben.
 */
export function useStundenMailText(editorRef: RefObject<HTMLElement | null>, o: StundenMailOptionen) {
    const textFuer = useRef('');
    const gesetzteAnrede = useRef('');
    const { offen, laedt, ansprechpartner, monat, jahr, stundenart, signatur } = o;

    useEffect(() => {
        if (!offen) { textFuer.current = ''; return; }
        if (laedt) return;
        // Kein Ansprechpartner: briefanrede liefert „Sehr geehrte Damen und Herren“.
        const anrede = `${briefanrede(ansprechpartner?.anrede, ansprechpartner?.nachname)},`;
        const schluessel = `${monat}|${jahr}|${stundenart}`;
        if (textFuer.current !== schluessel) {
            setzeEditorHtml(editorRef.current, stundenMailText(escapeHtml(anrede), monat, jahr, stundenart, signatur));
            textFuer.current = schluessel;
        } else {
            ersetzeAnredeZeile(editorRef.current, gesetzteAnrede.current, anrede);
        }
        gesetzteAnrede.current = anrede;
    }, [editorRef, offen, laedt, ansprechpartner, monat, jahr, stundenart, signatur]);
}
