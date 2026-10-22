import { useCallback, useEffect, useMemo, useRef } from 'react';

export interface EinmalTimer {
    /** Startet den Timer neu; ein noch laufender Timer dieses Hooks wird vorher abgebrochen. */
    starte: (aktion: () => void, ms: number) => void;
    /** Bricht den laufenden Timer ab (ohne Wirkung, wenn keiner laeuft). */
    stoppe: () => void;
}

/**
 * Ein einzelner `setTimeout`, der beim Unmount automatisch abgeraeumt wird.
 *
 * Fuer kurze Nachlaeufer wie „Gespeichert“-Haken oder Hinweise, die nach ein
 * paar Sekunden wieder verschwinden. Ohne Aufraeumen feuert so ein Timer auch
 * nach dem Schliessen der Komponente noch und setzt State ins Leere – in Tests
 * sogar erst, wenn jsdom schon abgebaut ist (`window is not defined`).
 *
 * Das zurueckgegebene Objekt ist stabil und muss nicht in Hook-Abhaengigkeiten.
 */
export function useEinmalTimer(): EinmalTimer {
    const timerIdRef = useRef<ReturnType<typeof setTimeout> | null>(null);
    // starte() steht oft hinter einem await (Speichern, Laden). Endet das erst
    // nach dem Unmount, darf kein neuer Timer mehr entstehen, den niemand abraeumt.
    const gemountetRef = useRef(true);

    const stoppe = useCallback(() => {
        if (timerIdRef.current != null) {
            clearTimeout(timerIdRef.current);
            timerIdRef.current = null;
        }
    }, []);

    const starte = useCallback((aktion: () => void, ms: number) => {
        stoppe();
        if (!gemountetRef.current) return;
        timerIdRef.current = setTimeout(() => {
            timerIdRef.current = null;
            aktion();
        }, ms);
    }, [stoppe]);

    useEffect(() => {
        // Erneut setzen, weil StrictMode Effekte einmal ab- und wieder aufbaut.
        gemountetRef.current = true;
        return () => {
            gemountetRef.current = false;
            stoppe();
        };
    }, [stoppe]);

    return useMemo(() => ({ starte, stoppe }), [starte, stoppe]);
}
