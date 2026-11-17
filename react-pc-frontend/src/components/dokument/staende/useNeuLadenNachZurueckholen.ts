import { useEffect, useRef, useState } from 'react';
import { onInhaltZurueckgeholt } from '../../../lib/dokumentChannel';
import { useToast } from '../../ui/toast';

/**
 * Für einen offenen Editor: Wird bei diesem Dokument ein Stand wiederhergestellt (in
 * diesem oder einem anderen Tab), ändert sich der Schlüssel. Als `key` am Editor
 * baut das ihn neu auf und lädt den wiederhergestellten Inhalt. Sonst würde sein
 * automatisches Speichern ihn mit dem alten Inhalt überschreiben.
 *
 * @param hatUngespeichert Fragt den Editor, ob dabei Eingaben verloren gehen; dann
 *   warnt der Toast statt nur zu melden.
 */
export function useNeuLadenNachZurueckholen(dokumentId: number | undefined, hatUngespeichert?: () => boolean): number {
    const toast = useToast();
    const [schluessel, setSchluessel] = useState(0);
    const hatUngespeichertRef = useRef(hatUngespeichert);
    useEffect(() => {
        hatUngespeichertRef.current = hatUngespeichert;
    });

    useEffect(() => {
        if (dokumentId == null) return undefined;
        return onInhaltZurueckgeholt((ereignis) => {
            if (ereignis.dokumentId !== dokumentId) return;
            const verworfen = hatUngespeichertRef.current?.() ?? false;
            setSchluessel((s) => s + 1);
            if (verworfen) {
                toast.warning(`Stand ${ereignis.standNummer} wurde wiederhergestellt. Ihre letzten, noch nicht gespeicherten Eingaben in diesem Fenster wurden verworfen.`);
            } else {
                toast.success(`Stand ${ereignis.standNummer} wurde wiederhergestellt. Das Dokument ist neu geladen.`);
            }
        });
    }, [dokumentId, toast]);

    return schluessel;
}
