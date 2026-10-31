import { useCallback } from 'react';
import { useSearchParams } from 'react-router-dom';
import { ALLE_DOKUMENTARTEN } from './textbausteinFilter';

/**
 * Suche (`?q=`) und Dokumentart (`?dokumentart=`) stehen in der URL: Links
 * lassen sich teilen, und „Zurück“ landet wieder beim selben Filter.
 * Tippen ersetzt den Verlaufseintrag, statt pro Buchstabe einen anzulegen.
 */
export function useTextbausteinFilterUrl() {
    const [params, setParams] = useSearchParams();
    const suche = params.get('q') ?? '';
    const dokumentart = params.get('dokumentart') ?? ALLE_DOKUMENTARTEN;

    const setze = useCallback((schluessel: 'q' | 'dokumentart', wert: string, ersetzen: boolean) => {
        setParams((alt) => {
            const neu = new URLSearchParams(alt);
            if (wert) neu.set(schluessel, wert);
            else neu.delete(schluessel);
            return neu;
        }, { replace: ersetzen });
    }, [setParams]);

    const setSuche = useCallback((wert: string) => setze('q', wert, true), [setze]);
    const setDokumentart = useCallback((wert: string) => setze('dokumentart', wert, false), [setze]);
    const zuruecksetzen = useCallback(() => setParams(new URLSearchParams()), [setParams]);

    return { suche, dokumentart, setSuche, setDokumentart, zuruecksetzen };
}
