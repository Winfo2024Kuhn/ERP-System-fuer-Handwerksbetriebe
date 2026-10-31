import { useEffect, useState } from 'react';
import { ladeIdsVerfuegbar, type IdsLieferant } from './api';

/**
 * Welche Lieferanten-Shops kann der angemeldete Nutzer gerade öffnen?
 *
 * <p>Einmal beim Laden gefragt. Ist die Anbindung aus, hat kein Lieferant
 * einen aktiven Zugang oder schlägt die Abfrage fehl, kommt eine leere Liste
 * zurück – der Knopf bleibt dann still ausgeblendet. Eine Fehlermeldung wäre
 * hier Lärm: Wer die Anbindung nicht nutzt, soll davon nichts merken.</p>
 */
export function useIdsVerfuegbar(): IdsLieferant[] {
    const [lieferanten, setLieferanten] = useState<IdsLieferant[]>([]);

    useEffect(() => {
        const controller = new AbortController();
        ladeIdsVerfuegbar(controller.signal)
            .then((daten) => {
                if (!controller.signal.aborted) setLieferanten(daten.lieferanten);
            })
            .catch(() => {
                // Bewusst still – siehe oben.
            });
        return () => controller.abort();
    }, []);

    return lieferanten;
}
