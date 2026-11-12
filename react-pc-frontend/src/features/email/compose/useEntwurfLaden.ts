import { useEffect, useRef, useState } from 'react';
import { loadEmailDraft } from '../emailDraftPersistence';
import type { EmailDraftSnapshot } from '../emailDraftPersistence';

/**
 * Lädt einen gespeicherten Entwurf samt Anhängen und gibt ihn an `anwenden`
 * (Felder des Formulars befüllen). Scheitert das Laden, bleibt der Fehler
 * stehen, bis der Nutzer „Erneut laden“ drückt.
 */
export function useEntwurfLaden(
    draftId: number | undefined,
    anwenden: (entwurf: EmailDraftSnapshot) => void,
    meldeFehler: (meldung: string) => void,
) {
    const [laedt, setLaedt] = useState(!!draftId);
    const [fehler, setFehler] = useState<string | null>(null);
    const [versuch, setVersuch] = useState(0);
    const anwendenRef = useRef(anwenden);
    useEffect(() => { anwendenRef.current = anwenden; });

    // Anderer Entwurf: Ladeanzeige beim Rendern einschalten (erneuter Versuch: im Knopf).
    const [idVorher, setIdVorher] = useState(draftId);
    if (draftId !== idVorher) {
        setIdVorher(draftId);
        if (draftId) {
            setLaedt(true);
            setFehler(null);
        }
    }

    useEffect(() => {
        if (!draftId) return;
        let abgebrochen = false;
        void loadEmailDraft(draftId).then((entwurf) => {
            if (abgebrochen) return;
            anwendenRef.current(entwurf);
            setLaedt(false);
        }).catch((error: unknown) => {
            if (abgebrochen) return;
            const meldung = error instanceof Error ? error.message : 'Entwurf konnte nicht geladen werden.';
            setFehler(meldung);
            setLaedt(false);
            meldeFehler(meldung);
        });
        return () => { abgebrochen = true; };
    }, [draftId, versuch, meldeFehler]);

    const erneutLaden = () => {
        setLaedt(true);
        setFehler(null);
        setVersuch(wert => wert + 1);
    };

    return { laedt, fehler, erneutLaden };
}
