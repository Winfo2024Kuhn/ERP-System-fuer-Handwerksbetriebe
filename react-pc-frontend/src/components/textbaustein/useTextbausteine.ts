import { useCallback, useEffect, useState } from 'react';
import { useToast } from '../ui/toast';
import { fehlerMeldung, ladeTextbausteine, loescheTextbaustein, speichereTextbaustein } from './textbausteinApi';
import type { TextbausteinDto, TextbausteinSpeichernDaten } from './typen';

export type LadeStatus = 'laedt' | 'fertig' | 'fehler';

/**
 * Lädt die Textbausteine einmal und hält die Liste nach Speichern und Löschen
 * aktuell, ohne erneut zu laden (der Server liefert den gespeicherten Stand zurück).
 */
export function useTextbausteine() {
    const toast = useToast();
    const [liste, setListe] = useState<TextbausteinDto[]>([]);
    const [status, setStatus] = useState<LadeStatus>('laedt');
    const [ladeVersuch, setLadeVersuch] = useState(0);

    useEffect(() => {
        let aktiv = true;
        ladeTextbausteine()
            .then((daten) => {
                if (!aktiv) return;
                setListe(daten);
                setStatus('fertig');
            })
            .catch((fehler: unknown) => {
                if (!aktiv) return;
                setStatus('fehler');
                toast.error(fehlerMeldung(fehler, 'Die Textbausteine konnten nicht geladen werden.'));
            });
        return () => { aktiv = false; };
    }, [ladeVersuch, toast]);

    const erneutLaden = useCallback(() => {
        setStatus('laedt');
        setLadeVersuch((versuch) => versuch + 1);
    }, []);

    /** Speichert und übernimmt den Stand des Servers. Fehler gehen als `TextbausteinFehler` an den Aufrufer. */
    const speichern = useCallback((id: number | null, daten: TextbausteinSpeichernDaten) =>
        speichereTextbaustein(id, daten).then((gespeichert) => {
            setListe((alt) => (alt.some((b) => b.id === gespeichert.id)
                ? alt.map((b) => (b.id === gespeichert.id ? gespeichert : b))
                : [...alt, gespeichert]));
            return gespeichert;
        }), []);

    /** Löscht und meldet Fehler selbst per Toast; liefert, ob es geklappt hat. */
    const loeschen = useCallback((id: number) =>
        loescheTextbaustein(id)
            .then(() => {
                setListe((alt) => alt.filter((b) => b.id !== id));
                return true;
            })
            .catch((fehler: unknown) => {
                toast.error(fehlerMeldung(fehler, 'Der Textbaustein konnte nicht gelöscht werden.'));
                return false;
            }), [toast]);

    return { liste, status, erneutLaden, speichern, loeschen };
}
