import { useCallback, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useEntprellterWert } from '../../hooks/useEntprellterWert';
import { useNachladeListe, type SeitenLader } from '../../hooks/useNachladeListe';
import type { Baum } from '../../lib/ordnerBaum';
import { ladeDokumente, ladeFaellige, sucheDokumente } from './api';
import { istSuche } from './firmendokumenteLogik';
import type { Ansicht } from './RechterBereich';
import type { FirmenDokument, FirmenOrdner } from './typen';

/** Wartezeit nach dem letzten Tastendruck, bevor gesucht wird. */
export const SUCHE_VERZOEGERUNG_MS = 400;

/**
 * Was rechts zu sehen ist und die passende Liste: Suche (ab 2 Zeichen) vor
 * gewähltem Ordner (`?ordner=…` in der Adresse) vor Übersicht. Solange die
 * Ordner noch laden, wird nichts abgefragt.
 */
export function useAnsicht(baum: Baum<FirmenOrdner>, ordnerGeladen: boolean, meldeFehler: (text: string) => void) {
    const [params, setParams] = useSearchParams();
    const [suchtext, setSuchtext] = useState('');
    const suchbegriff = useEntprellterWert(suchtext.trim(), SUCHE_VERZOEGERUNG_MS);

    const roh = Number(params.get('ordner'));
    const gewaehlt = Number.isInteger(roh) && baum.byId.has(roh) ? roh : null;
    const ansicht: Ansicht = istSuche(suchbegriff) && istSuche(suchtext)
        ? { art: 'suche', suchbegriff }
        : gewaehlt !== null ? { art: 'ordner', ordnerId: gewaehlt } : { art: 'uebersicht' };

    const schluessel = !ordnerGeladen ? null
        : ansicht.art === 'suche' ? `suche:${ansicht.suchbegriff}`
            : ansicht.art === 'ordner' ? `ordner:${ansicht.ordnerId}` : 'faellig';
    // Ein neuer Lader je Darstellung ist in Ordnung: Die Liste lädt nur neu, wenn sich der Schlüssel ändert.
    const lader: SeitenLader<FirmenDokument> = ansicht.art === 'suche'
        ? (seite, signal) => sucheDokumente(ansicht.suchbegriff, seite, signal)
        : ansicht.art === 'ordner'
            ? (seite, signal) => ladeDokumente(ansicht.ordnerId, seite, signal)
            : (seite, signal) => ladeFaellige(seite, signal);
    const liste = useNachladeListe(schluessel, lader, meldeFehler);

    /** Ordner wählen (`null` = Übersicht); eine laufende Suche wird dabei geleert. */
    const waehle = useCallback((id: number | null) => {
        setSuchtext('');
        setParams(id === null ? {} : { ordner: String(id) });
    }, [setParams]);

    return { ansicht, gewaehlt, suchtext, setSuchtext, liste, waehle };
}
