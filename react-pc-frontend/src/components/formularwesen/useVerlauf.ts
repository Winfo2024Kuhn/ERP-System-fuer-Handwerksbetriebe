import { useCallback, useState } from 'react';

const MAX_SCHRITTE = 50;

export interface Verlauf<T> {
    stand: T;
    /**
     * Ändert den Stand und merkt sich den alten für „Rückgängig“.
     * @param gruppe Folgen mehrere Änderungen mit derselben Gruppe direkt
     *   aufeinander (ein Zug mit der Maus, Tippen im selben Feld, mehrere
     *   Pfeiltasten), wird daraus ein einziger Rückgängig-Schritt.
     */
    aendern: (aktion: T | ((vorher: T) => T), gruppe?: string) => void;
    /** Setzt einen neuen Stand ohne Verlauf (z. B. beim Öffnen einer Vorlage). */
    zuruecksetzen: (stand: T) => void;
    rueckgaengig: () => void;
    wiederholen: () => void;
    kannRueckgaengig: boolean;
    kannWiederholen: boolean;
}

interface Intern<T> {
    vergangenheit: T[];
    stand: T;
    zukunft: T[];
    letzteGruppe: string | null;
}

/** Rückgängig/Wiederholen für einen unveränderlichen Stand (hier: die Bausteine der Vorlage). */
export function useVerlauf<T>(anfang: T): Verlauf<T> {
    const [intern, setIntern] = useState<Intern<T>>({ vergangenheit: [], stand: anfang, zukunft: [], letzteGruppe: null });
    const aendern = useCallback((aktion: T | ((vorher: T) => T), gruppe?: string) => {
        setIntern(v => {
            // Im Updater entschieden, damit auch mehrere Änderungen im selben
            // Render-Durchlauf (mousemove) in der richtigen Reihenfolge zählen.
            const zusammenfassen = gruppe !== undefined && gruppe === v.letzteGruppe;
            const neu = typeof aktion === 'function' ? (aktion as (vorher: T) => T)(v.stand) : aktion;
            if (neu === v.stand) return v;
            return {
                vergangenheit: zusammenfassen ? v.vergangenheit : [...v.vergangenheit, v.stand].slice(-MAX_SCHRITTE),
                stand: neu,
                zukunft: [],
                letzteGruppe: gruppe ?? null,
            };
        });
    }, []);

    const zuruecksetzen = useCallback((stand: T) => {
        setIntern({ vergangenheit: [], stand, zukunft: [], letzteGruppe: null });
    }, []);

    const rueckgaengig = useCallback(() => {
        setIntern(v => {
            if (v.vergangenheit.length === 0) return v;
            return {
                vergangenheit: v.vergangenheit.slice(0, -1),
                stand: v.vergangenheit[v.vergangenheit.length - 1],
                zukunft: [v.stand, ...v.zukunft],
                letzteGruppe: null,
            };
        });
    }, []);

    const wiederholen = useCallback(() => {
        setIntern(v => {
            if (v.zukunft.length === 0) return v;
            return {
                vergangenheit: [...v.vergangenheit, v.stand],
                stand: v.zukunft[0],
                zukunft: v.zukunft.slice(1),
                letzteGruppe: null,
            };
        });
    }, []);

    return {
        stand: intern.stand,
        aendern,
        zuruecksetzen,
        rueckgaengig,
        wiederholen,
        kannRueckgaengig: intern.vergangenheit.length > 0,
        kannWiederholen: intern.zukunft.length > 0,
    };
}
