import { useEffect, useRef, useState, type KeyboardEvent } from 'react';
import type { BaumZeile } from '../../lib/ordnerBaum';
import type { FirmenOrdner } from './typen';

/** Schlüssel einer Baumzeile: Ordner-ID oder die Zeile „Übersicht“. */
export type ZeilenSchluessel = number | 'uebersicht';
export const UEBERSICHT: ZeilenSchluessel = 'uebersicht';

interface Optionen {
    zeilen: BaumZeile<FirmenOrdner>[];
    gewaehlt: number | null;
    onWaehlen: (id: number | null) => void;
    onAufklappen: (id: number, offen: boolean) => void;
}

/**
 * Tastatur-Bedienung des Ordnerbaums nach dem WAI-ARIA-Muster „Tree View“:
 * Pfeil hoch/runter wandert, rechts klappt auf bzw. geht ins erste Kind,
 * links klappt zu bzw. springt zum Eltern-Ordner, Pos1/Ende springen,
 * Enter/Leertaste öffnet. Nur eine Zeile ist per Tab erreichbar (roving tabindex).
 */
export function useBaumTastatur({ zeilen, gewaehlt, onWaehlen, onAufklappen }: Optionen) {
    const refs = useRef(new Map<ZeilenSchluessel, HTMLDivElement>());
    const [aktiv, setAktiv] = useState<ZeilenSchluessel>(gewaehlt ?? UEBERSICHT);
    const [fokusWunsch, setFokusWunsch] = useState<ZeilenSchluessel | null>(null);
    const [gewaehltVorher, setGewaehltVorher] = useState(gewaehlt);
    const schluessel: ZeilenSchluessel[] = [UEBERSICHT, ...zeilen.map((z) => z.ordner.id)];

    // Neuer gewählter Ordner (Klick, Suche „Im Ordner zeigen“) wird zur aktiven Zeile.
    if (gewaehltVorher !== gewaehlt) {
        setGewaehltVorher(gewaehlt);
        setAktiv(gewaehlt ?? UEBERSICHT);
    }

    useEffect(() => {
        if (fokusWunsch === null) return;
        refs.current.get(fokusWunsch)?.focus();
        // Erlaubt im Effekt: Der Wunsch ist erst nach dem DOM-Zugriff erledigt.
        // eslint-disable-next-line react-hooks/set-state-in-effect
        setFokusWunsch(null);
    }, [fokusWunsch]);

    /** Ist die aktive Zeile weggeklappt, übernimmt „Übersicht“ den Tab-Stopp. */
    const aktivSichtbar = schluessel.includes(aktiv) ? aktiv : UEBERSICHT;

    const fokussiere = (ziel: ZeilenSchluessel) => {
        setAktiv(ziel);
        setFokusWunsch(ziel);
    };

    const beiTaste = (event: KeyboardEvent<HTMLElement>, zeile: BaumZeile<FirmenOrdner> | null) => {
        if (event.target !== event.currentTarget) return;
        const index = schluessel.indexOf(zeile ? zeile.ordner.id : UEBERSICHT);
        const nimm = (i: number) => { event.preventDefault(); fokussiere(schluessel[Math.max(0, Math.min(schluessel.length - 1, i))]); };
        switch (event.key) {
            case 'ArrowDown': return nimm(index + 1);
            case 'ArrowUp': return nimm(index - 1);
            case 'Home': return nimm(0);
            case 'End': return nimm(schluessel.length - 1);
            case 'ArrowRight':
                event.preventDefault();
                if (zeile?.hatKinder && !zeile.aufgeklappt) onAufklappen(zeile.ordner.id, true);
                else if (zeile?.aufgeklappt) fokussiere(schluessel[index + 1]);
                return;
            case 'ArrowLeft':
                event.preventDefault();
                if (zeile?.aufgeklappt) onAufklappen(zeile.ordner.id, false);
                else if (zeile) fokussiere(zeile.ordner.parentId !== null && schluessel.includes(zeile.ordner.parentId) ? zeile.ordner.parentId : UEBERSICHT);
                return;
            case 'Enter':
            case ' ':
                event.preventDefault();
                onWaehlen(zeile ? zeile.ordner.id : null);
                return;
        }
    };

    /** Callback-Ref je Zeile – die Map füllt sich beim Einhängen, nicht beim Rendern. */
    const refSetzen = (key: ZeilenSchluessel) => (element: HTMLDivElement | null) => {
        if (element) refs.current.set(key, element);
        else refs.current.delete(key);
    };

    return { aktiv: aktivSichtbar, setAktiv, beiTaste, refSetzen };
}
