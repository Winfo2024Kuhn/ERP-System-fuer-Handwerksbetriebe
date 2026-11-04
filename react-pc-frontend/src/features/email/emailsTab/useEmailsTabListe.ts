import { useCallback, useState } from 'react';
import { useToast } from '../../../components/ui/toast';
import { useEntprellterWert } from '../../../hooks/useEntprellterWert';
import { useNachladeListe, type SeitenLader } from '../../../hooks/useNachladeListe';
import { apiJson } from '../../../lib/apiFehler';
import {
    SUCHE_VERZOEGERUNG_MS, listenSchluessel, normalisiereReiterSeite, reiterUrl, wirksameSuche,
} from './emailsTabListe';
import type { EmailsTabBezug, EmailsTabEintrag, EmailsTabFilter } from './emailsTabTypes';

/**
 * Mailliste des E-Mail-Reiters, seitenweise vom Server (`useNachladeListe`).
 * Suche (entprellt) und Filter gehen an den Server; jede Änderung beginnt bei Seite 1,
 * späte Antworten alter Abfragen werden verworfen. Fehler kommen als Meldung.
 */
export function useEmailsTabListe(bezug: EmailsTabBezug, bezugId: number) {
    const toast = useToast();
    const [suchtext, setSuchtext] = useState('');
    const [filter, setFilter] = useState<EmailsTabFilter>('all');
    const entprellt = useEntprellterWert(suchtext, SUCHE_VERZOEGERUNG_MS);
    // Leeren wirkt sofort; nur beim Tippen wird die Pause abgewartet.
    const suche = suchtext.trim() === '' ? '' : entprellt;

    const abfrage = { bezug, bezugId, suche, filter };
    // Ein neuer Lader je Darstellung ist in Ordnung: Die Liste lädt nur neu, wenn sich der Schlüssel ändert.
    const lader: SeitenLader<EmailsTabEintrag> = (seite, signal) =>
        apiJson<unknown>(reiterUrl(abfrage, seite), { signal }, 'Die E-Mails konnten nicht geladen werden.')
            .then(normalisiereReiterSeite);
    const meldeFehler = useCallback((text: string) => toast.error(text), [toast]);
    const liste = useNachladeListe(listenSchluessel(abfrage), lader, meldeFehler);
    const filterZuruecksetzen = useCallback(() => {
        setSuchtext('');
        setFilter('all');
    }, []);

    return {
        ...liste,
        suchtext,
        setSuchtext,
        filter,
        setFilter,
        /** Suche leeren und „Alle“ wählen; lädt sofort ab Seite 1. */
        filterZuruecksetzen,
        /** Sucht oder filtert die Liste gerade? Dann heißt „leer“: nichts gefunden. */
        gefiltert: wirksameSuche(suche) !== '' || filter !== 'all',
    };
}
