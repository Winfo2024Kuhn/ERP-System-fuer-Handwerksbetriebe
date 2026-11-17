import { useCallback, useState } from 'react';
import { useNachladeListe } from '../../../hooks/useNachladeListe';
import { useToast } from '../../ui/toast';
import { ladeStaendeSeite } from './staendeApi';
import type { StandEintrag } from './staendeTypen';

/**
 * Stände eines Dokuments, seitenweise („Mehr laden“). Die erste Seite bringt
 * zusätzlich mit, was sich seit dem letzten Export geändert hat und ob das
 * Dokument noch geändert werden darf.
 */
export function useDokumentStaende(dokumentId: number | undefined) {
    const toast = useToast();
    // undefined = noch nicht geladen, null = es gibt noch keinen Stand
    const [offeneAenderungen, setOffeneAenderungen] = useState<string[] | null | undefined>(undefined);
    const [dokumentBearbeitbar, setDokumentBearbeitbar] = useState<boolean | undefined>(undefined);

    const lader = useCallback(async (seite: number, signal: AbortSignal) => {
        const antwort = await ladeStaendeSeite(dokumentId!, seite, signal);
        if (seite === 0 && !signal.aborted) {
            setOffeneAenderungen(antwort.offeneAenderungen);
            setDokumentBearbeitbar(antwort.dokumentBearbeitbar);
        }
        return { eintraege: antwort.staende, gesamt: antwort.gesamt };
    }, [dokumentId]);

    const meldeFehler = useCallback((text: string) => toast.error(text), [toast]);
    const liste = useNachladeListe<StandEintrag>(dokumentId != null ? String(dokumentId) : null, lader, meldeFehler);

    return { ...liste, offeneAenderungen, dokumentBearbeitbar };
}
