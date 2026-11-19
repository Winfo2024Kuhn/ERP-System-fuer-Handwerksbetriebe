import { useCallback, useLayoutEffect, useRef, useState } from 'react';
import { CLOSURE_BLOCK_ID } from '../blockOps';
import type { AenderungsAuftrag } from '../useDokumentVerlauf';
import {
    aenderungsBezeichnung,
    normalisiereAbschlussZeilen,
    setzeZeile,
    type AbschlussZeileAnpassung,
    type AbschlussZeilen,
    type AbschlussZeileSchluessel,
} from './abschlussZeilen';

/**
 * Zustand der eigenen Abschluss-Zeilen im Dokumenteditor, wie der
 * Fortschrittsbalken-Schalter: State fürs Rendern, Ref für Verlauf und
 * Speichern (synchron, ohne auf den nächsten Render zu warten).
 */
export function useAbschlussZeilen() {
    const [abschlussZeilen, setAbschlussZeilen] = useState<AbschlussZeilen>({});
    const abschlussZeilenRef = useRef<AbschlussZeilen>(abschlussZeilen);
    useLayoutEffect(() => { abschlussZeilenRef.current = abschlussZeilen; }, [abschlussZeilen]);

    const setzeAbschlussZeilen = useCallback((neu: unknown) => {
        const bereinigt = normalisiereAbschlussZeilen(neu);
        abschlussZeilenRef.current = bereinigt;
        setAbschlussZeilen(bereinigt);
    }, []);

    return { abschlussZeilen, abschlussZeilenRef, setzeAbschlussZeilen };
}

/** Schritt für `verlauf.aendern`, damit Strg+Z/Strg+Y auch hier greifen. */
export function abschlussZeileAuftrag(
    aktuell: AbschlussZeilen,
    schluessel: AbschlussZeileSchluessel,
    neu: AbschlussZeileAnpassung | undefined,
): AenderungsAuftrag {
    return {
        bezeichnung: aenderungsBezeichnung(aktuell[schluessel], neu),
        berechne: (stand) => ({ abschlussZeilen: setzeZeile(stand.abschlussZeilen, schluessel, neu) }),
        buendelSchluessel: null,
        ziel: { blockId: CLOSURE_BLOCK_ID },
    };
}
