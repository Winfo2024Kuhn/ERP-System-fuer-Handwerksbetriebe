import { useEffect, useState } from 'react';
import { useToast } from '../../components/ui/toast';
import type { LieferantNotiz, LieferantStatistik } from '../../types';

/**
 * Holt nach dem Öffnen der Detailseite Kennzahlen und Notizen parallel nach (die
 * E-Mails lädt der Reiter selbst seitenweise). Die Seite selbst steht dann schon mit
 * den Stammdaten da; `undefined` heißt „lädt noch“, damit die Reiter einen Ladezustand
 * statt einer leeren Liste zeigen.
 */
export function useLieferantNachladen(lieferantId: number | string, ladeVersion: number) {
    const toast = useToast();
    const [statistik, setStatistik] = useState<LieferantStatistik | null | undefined>(undefined);
    const [notizen, setNotizen] = useState<LieferantNotiz[] | undefined>(undefined);

    // Anderer Lieferant oder neu gespeichert: zurück auf „lädt noch“, bevor der Effekt nachlädt.
    const ladeSchluessel = `${lieferantId}-${ladeVersion}`;
    const [geladenFuer, setGeladenFuer] = useState(ladeSchluessel);
    if (geladenFuer !== ladeSchluessel) {
        setGeladenFuer(ladeSchluessel);
        setStatistik(undefined);
        setNotizen(undefined);
    }

    useEffect(() => {
        let abgebrochen = false;
        const lade = async <T,>(pfad: string, fehlertext: string, setzen: (wert: T) => void, ersatz: T) => {
            try {
                const res = await fetch(`/api/lieferanten/${lieferantId}/${pfad}`);
                if (!res.ok) throw new Error(`HTTP ${res.status}`);
                const daten = await res.json() as T;
                if (!abgebrochen) setzen(daten);
            } catch (err) {
                console.error(fehlertext, err);
                if (!abgebrochen) {
                    setzen(ersatz);
                    toast.error(fehlertext);
                }
            }
        };
        void lade<LieferantStatistik | null>('statistik', 'Kennzahlen des Lieferanten konnten nicht geladen werden.', setStatistik, null);
        void lade<LieferantNotiz[]>('notizen', 'Notizen konnten nicht geladen werden.', setNotizen, []);
        return () => { abgebrochen = true; };
        // toast ist stabil; neu laden bei anderem Lieferanten oder nach dem Speichern.
    }, [lieferantId, ladeVersion, toast]);

    return { statistik, notizen, setNotizen };
}
