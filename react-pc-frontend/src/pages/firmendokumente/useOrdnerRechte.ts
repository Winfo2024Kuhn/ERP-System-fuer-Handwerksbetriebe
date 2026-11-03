import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { useToast } from '../../components/ui/toast';
import { fehlerText } from '../../lib/apiFehler';
import { ladeOrdnerRechte, speichereOrdnerRechte } from './api';
import type { OrdnerRechte, RechteStufe } from './typen';

/**
 * Zustand des Dialogs „Wer darf das sehen?“: lädt die Rechte eines obersten
 * Ordners, hält die Änderungen und speichert sie. Fehler erscheinen als Toast
 * und als `fehler` im Dialog.
 */
export function useOrdnerRechte(ordnerId: number, onGespeichert: (rechte: OrdnerRechte) => void) {
    const toast = useToast();
    const [rechte, setRechte] = useState<OrdnerRechte | null>(null);
    const [fehler, setFehler] = useState<string | null>(null);
    const [sendet, setSendet] = useState(false);
    const meldeRef = useRef(toast.error);
    useLayoutEffect(() => { meldeRef.current = toast.error; });

    useEffect(() => {
        let gueltig = true;
        ladeOrdnerRechte(ordnerId)
            .then((daten) => { if (gueltig) setRechte(daten); })
            .catch((err: unknown) => {
                if (!gueltig) return;
                const text = fehlerText(err, 'Die Rechte des Ordners konnten nicht geladen werden.');
                setFehler(text);
                meldeRef.current(text);
            });
        return () => { gueltig = false; };
    }, [ordnerId]);

    const setEingeschraenkt = (eingeschraenkt: boolean) => setRechte((alt) => (alt ? { ...alt, eingeschraenkt } : alt));
    const setStufe = (abteilungId: number, stufe: RechteStufe) => setRechte((alt) => (alt
        ? { ...alt, abteilungen: alt.abteilungen.map((a) => (a.abteilungId === abteilungId ? { ...a, stufe } : a)) }
        : alt));

    const speichern = () => {
        if (!rechte || sendet) return;
        setSendet(true);
        setFehler(null);
        // Auch bei „Alle Mitarbeiter“ gehen die Abteilungen mit – sie bleiben für später gespeichert.
        speichereOrdnerRechte(ordnerId, {
            eingeschraenkt: rechte.eingeschraenkt,
            abteilungen: rechte.abteilungen.map(({ abteilungId, stufe }) => ({ abteilungId, stufe })),
        })
            .then(onGespeichert)
            .catch((err: unknown) => {
                const text = fehlerText(err, 'Die Rechte des Ordners konnten nicht gespeichert werden.');
                setFehler(text);
                meldeRef.current(text);
                setSendet(false);
            });
    };

    return { rechte, fehler, sendet, setEingeschraenkt, setStufe, speichern };
}
