import { useCallback, useEffect, useRef, useState } from 'react';
import { useToast } from '../../../ui/toast';

export type LadeStatus = 'wartet' | 'laedt' | 'fertig' | 'fehler';

export interface NachladeListe<T> {
    status: LadeStatus;
    eintraege: T[];
    neuLaden: () => void;
}

/**
 * Lädt eine Liste erst, wenn sie gebraucht wird (`aktiv`), und danach nicht
 * erneut – auch nicht, wenn `aktiv` zwischendurch wieder `false` war. Fehler
 * meldet sie per Toast; der Aufrufer zeigt dazu den Zustand `fehler` mit
 * „Erneut laden“ (`neuLaden`).
 */
export function useNachladeListe<T>(url: string, aktiv: boolean, parse: (daten: unknown) => T[], fehlerText: string): NachladeListe<T> {
    const toast = useToast();
    const [status, setStatus] = useState<LadeStatus>('wartet');
    const [eintraege, setEintraege] = useState<T[]>([]);
    const [versuch, setVersuch] = useState(0);
    /** Welcher Ladeversuch schon gelaufen ist. */
    const geladenerVersuch = useRef(-1);

    useEffect(() => {
        if (!aktiv || geladenerVersuch.current === versuch) return;
        geladenerVersuch.current = versuch;
        let gueltig = true;
        let erledigt = false;
        fetch(url)
            .then(async res => {
                if (!res.ok) throw new Error(`HTTP ${res.status}`);
                const daten = parse(await res.json());
                if (!gueltig) return;
                erledigt = true;
                setEintraege(daten);
                setStatus('fertig');
            })
            .catch(() => {
                if (!gueltig) return;
                erledigt = true;
                setStatus('fehler');
                toast.error(fehlerText);
            });
        return () => {
            gueltig = false;
            // Abgebrochen, bevor die Antwort da war: beim nächsten Mal erneut laden.
            if (!erledigt) geladenerVersuch.current = -1;
        };
    }, [aktiv, versuch, url, parse, fehlerText, toast]);

    const neuLaden = useCallback(() => {
        setStatus('laedt');
        setVersuch(v => v + 1);
    }, []);
    return { status, eintraege, neuLaden };
}
