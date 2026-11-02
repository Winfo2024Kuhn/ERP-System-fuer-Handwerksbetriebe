import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react';
import { fehlerText } from '../lib/apiFehler';

/** Eine Seite einer Liste vom Server. */
export interface ListenSeite<T> {
    eintraege: T[];
    gesamt: number;
}

/** Lädt Seite `seite` (ab 0). `signal` bricht veraltete Abfragen ab. */
export type SeitenLader<T> = (seite: number, signal: AbortSignal) => Promise<ListenSeite<T>>;

interface Stand<T> {
    schluessel: string | null;
    eintraege: T[];
    gesamt: number;
    seite: number;
    laedt: boolean;
    laedtMehr: boolean;
    fehler: string | null;
}

const leer = <T,>(schluessel: string | null): Stand<T> => ({
    schluessel, eintraege: [], gesamt: 0, seite: 0, laedt: schluessel !== null, laedtMehr: false, fehler: null,
});

/**
 * Liste, die seitenweise vom Server kommt („Mehr laden“). Wechselt `schluessel`
 * (z. B. anderer Ordner, andere Suche), beginnt sie leer bei Seite 1; späte
 * Antworten alter Abfragen werden verworfen. `schluessel = null` lädt nichts.
 *
 * Fehler gehen an `meldeFehler` (Toast); beim ersten Laden steht der Text
 * zusätzlich in `fehler`, damit die Liste einen Fehler-Zustand zeigen kann.
 */
export function useNachladeListe<T extends { id: number }>(schluessel: string | null, lader: SeitenLader<T>, meldeFehler: (text: string) => void) {
    const [stand, setStand] = useState<Stand<T>>(() => leer(schluessel));
    const [version, setVersion] = useState(0);
    const laderRef = useRef(lader);
    const meldeRef = useRef(meldeFehler);
    const mehrAbbruchRef = useRef<AbortController | null>(null);
    useLayoutEffect(() => {
        laderRef.current = lader;
        meldeRef.current = meldeFehler;
    });

    if (stand.schluessel !== schluessel) setStand(leer(schluessel));

    useEffect(() => {
        if (schluessel === null) return;
        const abbruch = new AbortController();
        mehrAbbruchRef.current?.abort();
        laderRef.current(0, abbruch.signal)
            .then((seite) => {
                if (abbruch.signal.aborted) return;
                setStand({ schluessel, eintraege: seite.eintraege, gesamt: seite.gesamt, seite: 0, laedt: false, laedtMehr: false, fehler: null });
            })
            .catch((err: unknown) => {
                if (abbruch.signal.aborted) return;
                const text = fehlerText(err, 'Die Liste konnte nicht geladen werden.');
                setStand((s) => ({ ...s, laedt: false, fehler: text }));
                meldeRef.current(text);
            });
        return () => abbruch.abort();
    }, [schluessel, version]);

    const mehrLaden = useCallback(() => {
        if (stand.laedtMehr || stand.eintraege.length >= stand.gesamt) return;
        const abbruch = new AbortController();
        mehrAbbruchRef.current = abbruch;
        const naechste = stand.seite + 1;
        setStand((s) => ({ ...s, laedtMehr: true }));
        laderRef.current(naechste, abbruch.signal)
            .then((seite) => {
                if (abbruch.signal.aborted) return;
                setStand((s) => {
                    const bekannt = new Set(s.eintraege.map((e) => e.id));
                    const neu = seite.eintraege.filter((e) => !bekannt.has(e.id));
                    return { ...s, eintraege: [...s.eintraege, ...neu], gesamt: seite.gesamt, seite: naechste, laedtMehr: false };
                });
            })
            .catch((err: unknown) => {
                if (abbruch.signal.aborted) return;
                setStand((s) => ({ ...s, laedtMehr: false }));
                meldeRef.current(fehlerText(err, 'Weitere Einträge konnten nicht geladen werden.'));
            });
    }, [stand.laedtMehr, stand.eintraege.length, stand.gesamt, stand.seite]);

    /** Erste Seite neu laden; die bisherige Liste bleibt bis zur Antwort stehen. */
    const neuLaden = useCallback(() => {
        setStand((s) => ({ ...s, laedt: true, fehler: null }));
        setVersion((v) => v + 1);
    }, []);

    return {
        eintraege: stand.eintraege,
        gesamt: stand.gesamt,
        laedt: stand.laedt,
        laedtMehr: stand.laedtMehr,
        fehler: stand.fehler,
        mehrLaden,
        neuLaden,
    };
}
