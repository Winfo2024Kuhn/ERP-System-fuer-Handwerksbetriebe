import { useCallback, useLayoutEffect, useRef } from 'react';
import { useConfirm } from '../../components/ui/confirm-dialog';
import { useToast } from '../../components/ui/toast';
import { leseFehlermeldung } from '../../lib/apiFehler';
import { PAPIERKORB_URL, ZURUECKHOLEN_FEHLER, ZURUECKHOLEN_URL, sendeBloecke } from './emailPapierkorbApi';
import { RUECKGAENGIG_MS } from '../../lib/rueckgaengig';
import { loeschFehlerMeldung } from './emailCenterModel';
import {
    endgueltigFrage, endgueltigText, nichtMehrImPapierkorbText, papierkorbText, schonImPapierkorbText, zurueckText,
} from './emailLoeschen';

export interface EmailLoeschenOptionen {
    /** Mails sofort aus Liste und Auswahl nehmen (vor der Antwort des Servers). */
    ausblenden: (ids: number[]) => void;
    /** Nach Fehler oder „Rückgängig“: diese Mails wieder zulassen, Liste und Zähler neu holen. */
    neuLaden: (ids: number[]) => void;
    /** Nach erfolgreichem Löschen: Zähler und Zwischenspeicher auffrischen. */
    geaendert: () => void;
}

/**
 * Löschen im E-Mail-Center.
 *
 * Außerhalb des Papierkorbs ohne Rückfrage: Die Mails wandern sofort in den Papierkorb
 * (`POST /api/emails/papierkorb`), die Meldung bietet „Rückgängig“ an und holt dieselben
 * Mails per `POST /api/emails/papierkorb/zurueckholen` in ihren Ursprungsordner zurück.
 * Im Papierkorb ist Löschen endgültig (auch auf dem Mailserver) und fragt deshalb nach;
 * „Wiederherstellen“ nutzt dort denselben Zurückholen-Endpunkt wie „Rückgängig“.
 */
export function useEmailLoeschen(optionen: EmailLoeschenOptionen) {
    const toast = useToast();
    const confirm = useConfirm();
    // „Rückgängig“ kann Sekunden später kommen: immer die aktuellen Rückrufe nutzen.
    const optionenRef = useRef(optionen);
    useLayoutEffect(() => { optionenRef.current = optionen; });

    const zurueckholen = useCallback(async (ids: number[]) => {
        const { erledigt, anzahl, meldung } = await sendeBloecke(ZURUECKHOLEN_URL, ids, ZURUECKHOLEN_FEHLER, 'zurueckgeholt');
        if (erledigt.length > 0) {
            toast.info(anzahl > 0 ? zurueckText(anzahl) : nichtMehrImPapierkorbText(erledigt.length));
            if (anzahl > 0) optionenRef.current.neuLaden(erledigt);
        }
        if (meldung) toast.error(meldung);
    }, [toast]);

    const inPapierkorb = useCallback(async (ids: number[]) => {
        optionenRef.current.ausblenden(ids);
        const ersatz = ids.length === 1 ? 'E-Mail konnte nicht in den Papierkorb gelegt werden.'
            : 'E-Mails konnten nicht in den Papierkorb gelegt werden.';
        const { erledigt, anzahl, meldung } = await sendeBloecke(PAPIERKORB_URL, ids, ersatz, 'verschoben');
        const geschafft = new Set(erledigt);
        const offen = ids.filter(id => !geschafft.has(id));
        if (offen.length > 0) optionenRef.current.neuLaden(offen);
        if (erledigt.length > 0 && anzahl === 0) {
            // Nichts bewegt: kein „Rückgängig“, das ins Leere zeigt; die Mails tauchen wieder auf.
            optionenRef.current.neuLaden(erledigt);
            toast.info(schonImPapierkorbText(erledigt.length));
        } else if (erledigt.length > 0) {
            optionenRef.current.geaendert();
            toast.success(papierkorbText(anzahl), {
                aktion: { label: 'Rückgängig', onClick: () => { void zurueckholen(erledigt); } },
                dauerMs: RUECKGAENGIG_MS,
            });
        }
        if (meldung) toast.error(meldung);
    }, [toast, zurueckholen]);

    const endgueltig = useCallback(async (ids: number[], betreff?: string | null) => {
        if (!await confirm(endgueltigFrage(ids.length, betreff))) return;
        optionenRef.current.ausblenden(ids);
        const fehlgeschlagen: number[] = [];
        let serverMeldung: string | null = null;
        for (const id of ids) {
            try {
                const res = await fetch(`/api/emails/${id}/permanent`, { method: 'DELETE' });
                if (!res.ok) {
                    fehlgeschlagen.push(id);
                    serverMeldung ??= await leseFehlermeldung(res, '');
                }
            } catch {
                fehlgeschlagen.push(id);
            }
        }
        optionenRef.current.geaendert();
        if (fehlgeschlagen.length > 0) {
            optionenRef.current.neuLaden(fehlgeschlagen);
            toast.error(loeschFehlerMeldung(fehlgeschlagen.length, ids.length, serverMeldung));
        } else {
            toast.success(endgueltigText(ids.length));
        }
    }, [confirm, toast]);

    /**
     * „Wiederherstellen“ im Papierkorb: holt die Mails in ihren Ursprungsordner zurück (Spam bleibt Spam).
     * Sie verschwinden sofort aus der Liste; was der Server nicht zurückholt, taucht wieder auf.
     */
    const wiederherstellen = useCallback(async (ids: number[]) => {
        if (ids.length === 0) return;
        optionenRef.current.ausblenden(ids);
        const { erledigt, anzahl, meldung } = await sendeBloecke(ZURUECKHOLEN_URL, ids, ZURUECKHOLEN_FEHLER, 'zurueckgeholt');
        const geschafft = new Set(erledigt);
        const offen = ids.filter(id => !geschafft.has(id));
        if (offen.length > 0) optionenRef.current.neuLaden(offen);
        if (erledigt.length > 0 && anzahl === 0) {
            optionenRef.current.neuLaden(erledigt);
            toast.info(nichtMehrImPapierkorbText(erledigt.length));
        } else if (erledigt.length > 0) {
            optionenRef.current.geaendert();
            toast.info(zurueckText(anzahl));
        }
        if (meldung) toast.error(meldung);
    }, [toast]);

    /**
     * In den Papierkorb mit Rückgängig – oder, für Mails, die schon im Papierkorb liegen, `endgueltig`
     * mit Rückfrage. `betreff` nennt die Rückfrage, wenn es um eine Zeile geht.
     */
    const loeschen = useCallback(async (ids: number[], { endgueltig: istEndgueltig, betreff }: { endgueltig: boolean; betreff?: string | null }) => {
        if (ids.length === 0) return;
        if (istEndgueltig) await endgueltig(ids, betreff);
        else await inPapierkorb(ids);
    }, [endgueltig, inPapierkorb]);

    return { loeschen, wiederherstellen };
}
