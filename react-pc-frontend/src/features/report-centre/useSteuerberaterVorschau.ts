import { useEffect, useState } from 'react';
import { useToast } from '../../components/ui/toast';
import { fehlerText } from '../../lib/apiFehler';
import { ladeSteuerberaterVorschau, type SteuerberaterVorschau } from './api';

type Stand =
    | { art: 'laedt' }
    | { art: 'fertig'; vorschau: SteuerberaterVorschau }
    | { art: 'fehler'; meldung: string };

/**
 * Lädt die Vorschau-Tabelle für den Steuerberater. Ändert sich die Auswahl,
 * wird die alte Anfrage abgebrochen, damit keine späte Antwort sie überschreibt.
 */
export function useSteuerberaterVorschau(jahr: number, monat: number, mitarbeiterIds: number[]) {
    const toast = useToast();
    const schluessel = `${jahr}|${monat}|${mitarbeiterIds.join(',')}`;
    const [stand, setStand] = useState<Stand>({ art: 'laedt' });
    const [versuch, setVersuch] = useState(0);
    const [geladenFuer, setGeladenFuer] = useState(`${schluessel}#${versuch}`);
    if (geladenFuer !== `${schluessel}#${versuch}`) {
        setGeladenFuer(`${schluessel}#${versuch}`);
        setStand({ art: 'laedt' });
    }

    useEffect(() => {
        const abbruch = new AbortController();
        const ids = schluessel.split('|')[2];
        ladeSteuerberaterVorschau(
            { jahr, monat, mitarbeiterIds: ids ? ids.split(',').map(Number) : [] },
            abbruch.signal,
        )
            .then(vorschau => setStand({ art: 'fertig', vorschau }))
            .catch(err => {
                if (abbruch.signal.aborted) return;
                const meldung = fehlerText(err, 'Die Vorschau konnte nicht geladen werden.');
                setStand({ art: 'fehler', meldung });
                toast.error(meldung);
            });
        return () => abbruch.abort();
    }, [jahr, monat, schluessel, versuch, toast]);

    return { stand, nochmal: () => setVersuch(v => v + 1) };
}
