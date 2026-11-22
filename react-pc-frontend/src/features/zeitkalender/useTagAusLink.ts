import { useCallback, useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import type { KalenderTag } from './kalenderTypen';
import type { TagMarkierung } from './DayEditorModal';
import { leseTagLink, ohneTagLink, tagLinkSchluessel } from './tagLink';

interface Optionen {
    mitarbeiterId: number | null;
    /** Tage des geladenen Monats. */
    tage: KalenderTag[] | null;
    /** Kalender und Monatsabschluss für Mitarbeiter, Jahr und Monat aus der Adresse sind geladen. */
    bereit: boolean;
    /** Öffnet die Tagesansicht wie ein Doppelklick (abgeschlossene Monate fragen erst nach). */
    oeffneTag: (tag: KalenderTag) => void;
}

/**
 * Link aus einer Meldung („Zeiten prüfen“): öffnet die Tagesansicht des verlinkten Tages
 * genau einmal, sobald dessen Monat geladen ist, und liefert die Markierung dazu.
 * Ein neuer Link (andere Adresse über die Glocke) öffnet erneut.
 */
export function useTagAusLink({ mitarbeiterId, tage, bereit, oeffneTag }: Optionen) {
    const [searchParams, setSearchParams] = useSearchParams();
    const link = useMemo(() => leseTagLink(searchParams), [searchParams]);
    const schluessel = link ? tagLinkSchluessel(mitarbeiterId, link) : null;
    const [erledigt, setErledigt] = useState<string | null>(null);
    const [ohneTreffer, setOhneTreffer] = useState<string | null>(null);

    // Während des Renderns angleichen (wie beim Wechsel von Mitarbeiter/Monat auf der Seite),
    // damit der Tag ohne zusätzlichen Effekt-Durchlauf aufgeht.
    if (schluessel === null && erledigt !== null) {
        setErledigt(null);
    } else if (link && schluessel !== erledigt && bereit && tage) {
        setErledigt(schluessel);
        // Liegt der Tag nicht im geladenen Monat, öffnet sich still nichts.
        const treffer = tage.find(t => t.datum === link.tag);
        if (treffer) oeffneTag(treffer);
        else setOhneTreffer(schluessel);
    }

    const markierungFuer = useCallback((datum: string): TagMarkierung | undefined => (
        link && link.tag === datum ? { ids: link.markiert, anlass: link.anlass } : undefined
    ), [link]);

    /** Nimmt Tag, Markierung und Anlass aus der Adresse, damit Neuladen nichts wieder öffnet. */
    const linkBeenden = useCallback(() => {
        setSearchParams(prev => ohneTagLink(prev), { replace: true });
    }, [setSearchParams]);

    // Tag nicht gefunden: Link-Teile entfernen, damit sie bei einem späteren Monatswechsel
    // nicht unerwartet greifen. Adresse ändern geht nur außerhalb des Renderns.
    useEffect(() => {
        if (ohneTreffer !== null && ohneTreffer === schluessel) linkBeenden();
    }, [ohneTreffer, schluessel, linkBeenden]);

    return { markierungFuer, linkBeenden };
}
