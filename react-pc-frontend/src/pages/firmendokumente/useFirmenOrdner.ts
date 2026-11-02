import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import { baueBaum } from '../../lib/ordnerBaum';
import { ladeOrdner } from './api';
import { ordnerMitUeberfaelligem } from './firmendokumenteLogik';
import type { FirmenOrdner } from './typen';

/**
 * Lädt alle Ordner der Firmendokumente und baut daraus den Baum.
 * `neuLaden()` holt die Liste samt Zählern erneut, z. B. nach dem Hochladen.
 * Beim Neuladen bleibt der bisherige Baum stehen.
 */
export function useFirmenOrdner(meldeFehler: (text: string) => void) {
    const [ordner, setOrdner] = useState<FirmenOrdner[] | null>(null);
    const [fehler, setFehler] = useState<string | null>(null);
    const [version, setVersion] = useState(0);
    const meldeRef = useRef(meldeFehler);
    useLayoutEffect(() => { meldeRef.current = meldeFehler; });

    useEffect(() => {
        let gueltig = true;
        ladeOrdner()
            .then((daten) => {
                if (!gueltig) return;
                setOrdner(daten);
                setFehler(null);
            })
            .catch((err: unknown) => {
                if (!gueltig) return;
                const text = err instanceof Error ? err.message : 'Die Ordner konnten nicht geladen werden.';
                setFehler(text);
                meldeRef.current(text);
            });
        return () => { gueltig = false; };
    }, [version]);

    const neuLaden = useCallback(() => setVersion((v) => v + 1), []);
    const baum = useMemo(() => baueBaum(ordner ?? []), [ordner]);
    const ueberfaellig = useMemo(() => ordnerMitUeberfaelligem(baum), [baum]);

    return { ordner, baum, ueberfaellig, fehler, neuLaden };
}
