import { useCallback, useEffect, useState } from 'react';
import { useToast } from '../ui/toast';
import { fehlerMeldung, ladeDokumenttypGruppen, ladePlatzhalter } from './textbausteinApi';
import type { DokumenttypGruppe, Platzhalter } from './typen';
import type { LadeStatus } from './useTextbausteine';

export interface TextbausteinKataloge {
    platzhalter: Platzhalter[];
    gruppen: DokumenttypGruppe[];
    status: LadeStatus;
    erneutLaden: () => void;
}

/**
 * Platzhalter (mit Beispielwerten) und Dokumentart-Gruppen – beide kommen vom
 * Backend, damit die Seite nichts doppelt pflegt. Schlägt eines fehl, meldet ein
 * Toast den Grund und `status` wird `fehler`: Ohne Katalog wären alle Chips
 * „unbekannt“, das soll nicht still so aussehen.
 */
export function useTextbausteinKataloge(): TextbausteinKataloge {
    const toast = useToast();
    const [platzhalter, setPlatzhalter] = useState<Platzhalter[]>([]);
    const [gruppen, setGruppen] = useState<DokumenttypGruppe[]>([]);
    const [status, setStatus] = useState<LadeStatus>('laedt');
    const [ladeVersuch, setLadeVersuch] = useState(0);

    useEffect(() => {
        let aktiv = true;
        Promise.all([ladePlatzhalter(), ladeDokumenttypGruppen()])
            .then(([neuePlatzhalter, neueGruppen]) => {
                if (!aktiv) return;
                setPlatzhalter(neuePlatzhalter);
                setGruppen(neueGruppen);
                setStatus('fertig');
            })
            .catch((fehler: unknown) => {
                if (!aktiv) return;
                setStatus('fehler');
                toast.error(fehlerMeldung(fehler, 'Platzhalter und Dokumentarten konnten nicht geladen werden.'));
            });
        return () => { aktiv = false; };
    }, [ladeVersuch, toast]);

    const erneutLaden = useCallback(() => {
        setStatus('laedt');
        setLadeVersuch((versuch) => versuch + 1);
    }, []);

    return { platzhalter, gruppen, status, erneutLaden };
}
