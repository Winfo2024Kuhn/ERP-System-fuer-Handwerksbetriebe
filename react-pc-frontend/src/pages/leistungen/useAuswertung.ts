import { useCallback, useEffect, useState } from 'react';
import { ladeAuswertung } from './api';
import type { AuswertungZeile } from './typen';

/**
 * Lädt `GET /api/produktkategorien/auswertung` für ein Jahr und einen
 * Eltern-Ordner (`null` = Hauptordner). Mit `aktiv = false` wird nichts geladen.
 * Ältere Antworten werden verworfen, wenn inzwischen ein anderes Jahr gewählt ist.
 */
export function useAuswertung(jahr: string, parentId: number | null, aktiv = true) {
    const [zeilen, setZeilen] = useState<AuswertungZeile[] | null>(null);
    const [laedt, setLaedt] = useState(aktiv);
    const [fehler, setFehler] = useState<string | null>(null);
    const [versuch, setVersuch] = useState(0);

    useEffect(() => {
        if (!aktiv) return;
        let gueltig = true;
        // eslint-disable-next-line react-hooks/set-state-in-effect
        setLaedt(true);
        setFehler(null);
        ladeAuswertung(jahr, parentId)
            .then((daten) => { if (gueltig) setZeilen(daten); })
            .catch((err: unknown) => {
                if (!gueltig) return;
                setZeilen(null);
                setFehler(err instanceof Error ? err.message : 'Die Auswertung konnte nicht geladen werden.');
            })
            .finally(() => { if (gueltig) setLaedt(false); });
        return () => { gueltig = false; };
    }, [jahr, parentId, aktiv, versuch]);

    const erneut = useCallback(() => setVersuch((v) => v + 1), []);
    return { zeilen, laedt, fehler, erneut };
}
