import { useCallback, useId, type FocusEvent, type KeyboardEvent, type RefObject } from 'react';
import { fokussiereNaechstesNach } from './mailLeisteTasten';
import type { MailAuswahl } from './useMailAuswahl';
import type { MailLinks } from './useMailLinks';

interface Optionen {
    editorRef: RefObject<HTMLDivElement | null>;
    auswahl: MailAuswahl;
    links: MailLinks;
    /** Tab in Tabellen; `true`, wenn die Taste verbraucht ist. */
    tabellenTaste: (event: KeyboardEvent<HTMLDivElement>) => boolean;
}

/**
 * Tasten und Fokus des Mail-Textfelds: Strg/⌘+K öffnet den Link-Dialog, Tab in
 * Tabellen springt zwischen Zellen, sonst führen Tab und Escape in die bzw. aus
 * der Link-Leiste am angeklickten Link.
 */
export function useMailTasten({ editorRef, auswahl, links, tabellenTaste }: Optionen) {
    const linkLeisteId = useId();

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
        // In einer Tabelle springt Tab von Zelle zu Zelle, auch an einem Link.
        if (tabellenTaste(event) || !links.leistenLink) return;
        if (event.key === 'Escape') {
            // Nur die Link-Leiste schließen – nicht den Dialog, in dem das Feld steht.
            event.preventDefault();
            event.stopPropagation();
            links.schliesseLeiste(true);
        } else if (event.key === 'Tab' && !event.shiftKey) {
            event.preventDefault();
            document.getElementById(linkLeisteId)?.querySelector<HTMLButtonElement>('button')?.focus();
        }
    }, [links, linkLeisteId, tabellenTaste]);

    const beimVerlassen = useCallback((event: FocusEvent<HTMLDivElement>) => {
        if (!document.getElementById(linkLeisteId)?.contains(event.relatedTarget as Node | null)) links.schliesseLeiste();
    }, [links, linkLeisteId]);

    return { linkLeisteId, zurueckInsFeld, verlasseFeld, beiTaste, beimVerlassen };
}
