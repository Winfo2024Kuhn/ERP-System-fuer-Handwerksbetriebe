import { useCallback, useEffect, useRef, useState } from 'react';
import type { EmailThread } from '../../../components/EmailThreadView';

/** Ergebnis einer Abfrage: Verlauf, `null` = Server lehnt ab, `undefined` = keine Verbindung. */
function holeVerlauf(emailId: number): Promise<EmailThread | null | undefined> {
    return fetch(`/api/emails/${emailId}/thread`)
        .then(r => (r.ok ? r.json() as Promise<EmailThread> : null))
        .catch(() => undefined);
}

/** Geladener Stand für genau eine Mail; gehört er zu einer anderen, gilt er nicht. */
interface Stand {
    id: number;
    thread: EmailThread | null;
    loading: boolean;
}

/**
 * Lädt den Verlauf der im E-Mail-Reiter geöffneten Mail.
 *
 * Jede Abfrage bekommt eine laufende Nummer. Übernommen wird nur die Antwort der
 * neuesten Abfrage; wechselt die Auswahl oder schließt der Verlauf, gilt keine alte
 * Antwort mehr (auch nicht die eines Neuladens).
 */
export function useEmailsTabThread(selectedEmailId: number | null) {
    const [stand, setStand] = useState<Stand | null>(null);
    const anfrageNr = useRef(0);

    /** Holt den Verlauf; `behalten` = bei Netzausfall den bisherigen stehen lassen. */
    const laden = useCallback((emailId: number, behalten: boolean) => {
        const nr = ++anfrageNr.current;
        return holeVerlauf(emailId).then(daten => {
            if (nr !== anfrageNr.current) return;
            setStand(vorher => ({
                id: emailId,
                thread: daten === undefined && behalten && vorher?.id === emailId ? vorher.thread : daten ?? null,
                loading: false,
            }));
        });
    }, []);

    /** Macht alle laufenden Abfragen ungültig. */
    const verwerfeOffene = useCallback(() => { anfrageNr.current++; }, []);

    useEffect(() => {
        if (!selectedEmailId) return;
        void laden(selectedEmailId, false);
        return () => {
            verwerfeOffene();
            setStand(null);
        };
    }, [selectedEmailId, laden, verwerfeOffene]);

    /** Nach Antwort oder gelöschtem Entwurf: Verlauf neu holen; bei Netzausfall bleibt der alte stehen. */
    const reloadThread = useCallback(() => {
        if (!selectedEmailId) return;
        setStand(vorher => (vorher?.id === selectedEmailId ? { ...vorher, loading: true } : vorher));
        void laden(selectedEmailId, true);
    }, [selectedEmailId, laden]);

    const aktuell = stand?.id === selectedEmailId ? stand : null;
    return {
        thread: aktuell?.thread ?? null,
        threadLoading: !!selectedEmailId && (!aktuell || aktuell.loading),
        reloadThread,
    };
}
