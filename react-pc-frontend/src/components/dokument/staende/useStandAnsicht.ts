import { useEffect, useState } from 'react';
import { fehlerText } from '../../../lib/apiFehler';
import { useToast } from '../../ui/toast';
import { ladeStandDetail } from './staendeApi';
import type { StandDetail } from './staendeTypen';

interface Zustand {
    schluessel: string;
    stand: StandDetail | null;
    fehler: string | null;
}

/**
 * Lädt für die Ansicht eines Stands den Stand selbst. Ob wiederhergestellt werden
 * darf, liefert der Stand mit (`dokumentBearbeitbar`). Ein Fehler (z. B. 404, den
 * Stand gibt es nicht) geht als Toast raus und steht in `fehler`.
 */
export function useStandAnsicht(dokumentId: number, standId: number) {
    const toast = useToast();
    const schluessel = `${dokumentId}/${standId}`;
    const [zustand, setZustand] = useState<Zustand>({ schluessel, stand: null, fehler: null });
    if (zustand.schluessel !== schluessel) setZustand({ schluessel, stand: null, fehler: null });

    useEffect(() => {
        const abbruch = new AbortController();
        ladeStandDetail(dokumentId, standId, abbruch.signal)
            .then((stand) => {
                if (!abbruch.signal.aborted) setZustand({ schluessel, stand, fehler: null });
            })
            .catch((err: unknown) => {
                if (abbruch.signal.aborted) return;
                const text = fehlerText(err, 'Dieser Stand konnte nicht geladen werden.');
                setZustand({ schluessel, stand: null, fehler: text });
                toast.error(text);
            });
        return () => abbruch.abort();
    }, [dokumentId, standId, schluessel, toast]);

    return { stand: zustand.stand, fehler: zustand.fehler, laedt: zustand.stand === null && zustand.fehler === null };
}
