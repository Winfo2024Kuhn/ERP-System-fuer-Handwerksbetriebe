import { useEffect, useState } from 'react';
import { useToast } from '../../components/ui/toast';
import { fehlerText } from '../../lib/apiFehler';
import { ladeBerichtMitarbeiter, type BerichtMitarbeiter } from './api';

/** Lädt die auswählbaren Mitarbeiter einmal, solange `aktiv` gilt (z. B. Assistent offen). */
export function useBerichtMitarbeiter(aktiv: boolean) {
    const toast = useToast();
    const [mitarbeiter, setMitarbeiter] = useState<BerichtMitarbeiter[] | null>(null);
    const [fehler, setFehler] = useState<string | null>(null);

    useEffect(() => {
        if (!aktiv || mitarbeiter !== null) return;
        const abbruch = new AbortController();
        ladeBerichtMitarbeiter(abbruch.signal)
            .then(liste => { setMitarbeiter(liste); setFehler(null); })
            .catch(err => {
                if (abbruch.signal.aborted) return;
                const meldung = fehlerText(err, 'Die Mitarbeiter konnten nicht geladen werden.');
                setFehler(meldung);
                setMitarbeiter([]);
                toast.error(meldung);
            });
        return () => abbruch.abort();
    }, [aktiv, mitarbeiter, toast]);

    return { mitarbeiter: mitarbeiter ?? [], laedt: mitarbeiter === null, fehler };
}
