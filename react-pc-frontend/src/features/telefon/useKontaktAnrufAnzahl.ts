import { useEffect, useState } from 'react';
import { ladeAnrufe } from './api';
import type { KontaktTyp } from './types';

/**
 * Anzahl der Anrufe eines Kunden oder Lieferanten – für den Zähler am
 * Reiter „Anrufe" in der Akte. Fragt nur eine Ein-Eintrag-Seite ab und
 * liest die Gesamtzahl. Ohne Telefon-Recht (`aktiv = false`) passiert nichts.
 */
export function useKontaktAnrufAnzahl(typ: KontaktTyp, kontaktId: number | null | undefined, aktiv: boolean): number {
    const [anzahl, setAnzahl] = useState(0);
    useEffect(() => {
        if (!aktiv || !kontaktId) return;
        const abbruch = new AbortController();
        ladeAnrufe(typ === 'KUNDE' ? { kundeId: kontaktId, groesse: 1 } : { lieferantId: kontaktId, groesse: 1 }, abbruch.signal)
            .then((seite) => {
                if (!abbruch.signal.aborted) setAnzahl(seite.gesamt);
            })
            .catch(() => undefined);
        return () => abbruch.abort();
    }, [typ, kontaktId, aktiv]);
    return aktiv ? anzahl : 0;
}
