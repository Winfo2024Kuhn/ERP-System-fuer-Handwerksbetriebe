import { useEffect, useRef, useState } from 'react';
import { ladeKontaktUeberblick } from './api';
import type { KontaktKurz, KontaktUeberblick } from './types';

export type UeberblickZustand =
    | { status: 'laedt' }
    | { status: 'fertig'; daten: KontaktUeberblick }
    | { status: 'fehler'; meldung: string };

/**
 * Lädt den Überblick (Adresse, Projekte, Anfragen) zum Anrufer.
 *
 * <p>Geladen wird einmal je Kontakt – Statuswechsel des Anrufs (klingelt →
 * im Gespräch) liefern zwar ein neues Kontakt-Objekt, aber denselben
 * Kontakt, und lösen deshalb keine neue Abfrage aus.</p>
 *
 * @param onFehler wird bei einem Ladefehler einmal mit der Meldung gerufen (für den Toast)
 */
export function useKontaktUeberblick(kontakt: KontaktKurz | null, onFehler?: (meldung: string) => void): UeberblickZustand | null {
    const typ = kontakt?.typ;
    const id = kontakt?.id;
    const [ergebnis, setErgebnis] = useState<{ schluessel: string; zustand: UeberblickZustand } | null>(null);
    const schluessel = typ && id ? `${typ}-${id}` : null;
    const onFehlerRef = useRef(onFehler);
    useEffect(() => {
        onFehlerRef.current = onFehler;
    }, [onFehler]);

    useEffect(() => {
        if (!typ || !id) return;
        const abbruch = new AbortController();
        const fuer = `${typ}-${id}`;
        ladeKontaktUeberblick(typ, id, abbruch.signal)
            .then((daten) => {
                if (!abbruch.signal.aborted) setErgebnis({ schluessel: fuer, zustand: { status: 'fertig', daten } });
            })
            .catch((fehler: unknown) => {
                if (abbruch.signal.aborted) return;
                const meldung = fehler instanceof Error ? fehler.message : 'Projekte und Anfragen konnten nicht geladen werden.';
                setErgebnis({ schluessel: fuer, zustand: { status: 'fehler', meldung } });
                onFehlerRef.current?.(meldung);
            });
        return () => abbruch.abort();
    }, [typ, id]);

    if (!schluessel) return null;
    return ergebnis?.schluessel === schluessel ? ergebnis.zustand : { status: 'laedt' };
}
