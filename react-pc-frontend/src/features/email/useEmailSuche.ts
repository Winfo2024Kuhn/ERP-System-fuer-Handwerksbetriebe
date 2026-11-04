import { useCallback, useEffect, useReducer, useRef, useState } from 'react';
import { useToast } from '../../components/ui/toast';
import { leseFehlermeldung } from '../../lib/apiFehler';
import type { EmailItem, FolderType } from './emailCenterModel';
import {
    LEERER_SUCHSTAND, SUCHE_ENTPRELLEN_MS, SUCHE_MIN_ZEICHEN, buildEmailSucheUrl, leseSuchSeite, suchOrdner, suchReducer,
    type SuchModus,
} from './emailSuche';

const FEHLER_SUCHE = 'E-Mails konnten nicht durchsucht werden.';
const FEHLER_NACHLADEN = 'Weitere Treffer konnten nicht geladen werden.';

function holeSeite(url: string, signal: AbortSignal, ersatz: string) {
    return fetch(url, { signal }).then(async (res) => {
        if (!res.ok) throw new Error(await leseFehlermeldung(res, ersatz));
        return leseSuchSeite(await res.json());
    });
}

const istAbbruch = (fehler: unknown) => fehler instanceof DOMException && fehler.name === 'AbortError';
/** TypeError = keine Verbindung; sonst steht der Satz des Servers im Fehler. */
const meldungAus = (fehler: unknown, ersatz: string) =>
    fehler instanceof Error && !(fehler instanceof TypeError) && fehler.message ? fehler.message : ersatz;

/**
 * Serverseitige E-Mail-Suche mit Seiten – „In diesem Ordner“ und „Alle Ordner“.
 *
 * Ab 2 Zeichen fragt sie entprellt (400 ms) `GET /api/emails/suche`. Ändern sich Begriff,
 * Modus oder Ordner, leert sie die Treffer, bricht laufende Anfragen ab und beginnt bei Seite 0.
 * Darunter (und im Entwürfe-Ordner im Ordner-Modus) ist sie aus: Die Seite zeigt ihre normale Liste.
 */
export function useEmailSuche(aktiverOrdner: FolderType) {
    const toast = useToast();
    const [begriff, setBegriff] = useState('');
    const [modus, setModus] = useState<SuchModus>('ordner');
    const [durchlauf, setDurchlauf] = useState(0);
    const ordner = suchOrdner(modus, aktiverOrdner);
    const q = begriff.trim();
    const aktiv = ordner !== null && q.length >= SUCHE_MIN_ZEICHEN;
    const schluessel = aktiv ? `${ordner}|${durchlauf}|${q}` : '';

    const [stand, dispatch] = useReducer(suchReducer, LEERER_SUCHSTAND);
    // Neue Suche: Liste sofort leeren und „lädt“ zeigen, noch bevor die Entprellzeit um ist.
    if (stand.schluessel !== schluessel) dispatch({ typ: 'neu', schluessel, aktiv });

    const abbruchRef = useRef<AbortController | null>(null);
    useEffect(() => {
        if (!aktiv || ordner === null) return;
        const controller = new AbortController();
        abbruchRef.current = controller;
        const timer = setTimeout(() => {
            holeSeite(buildEmailSucheUrl(q, ordner, 0), controller.signal, FEHLER_SUCHE)
                .then(({ eintraege, hatMehr }) => dispatch({ typ: 'seite', schluessel, seite: 0, eintraege, hatMehr }))
                .catch((fehler: unknown) => {
                    if (istAbbruch(fehler) || controller.signal.aborted) return;
                    toast.error(meldungAus(fehler, FEHLER_SUCHE));
                    dispatch({ typ: 'fehler', schluessel });
                });
        }, SUCHE_ENTPRELLEN_MS);
        return () => { clearTimeout(timer); controller.abort(); };
    }, [aktiv, ordner, q, schluessel, toast]);

    const laedtMehrRef = useRef(false);
    const mehrLaden = useCallback(() => {
        const controller = abbruchRef.current;
        if (!aktiv || ordner === null || !controller || stand.status !== 'fertig' || !stand.hatMehr || laedtMehrRef.current) return;
        laedtMehrRef.current = true;
        dispatch({ typ: 'mehrStart', schluessel });
        const naechste = stand.seite + 1;
        holeSeite(buildEmailSucheUrl(q, ordner, naechste), controller.signal, FEHLER_NACHLADEN)
            .then(({ eintraege, hatMehr }) => dispatch({ typ: 'seite', schluessel, seite: naechste, eintraege, hatMehr }))
            .catch((fehler: unknown) => {
                if (istAbbruch(fehler) || controller.signal.aborted) return;
                toast.error(meldungAus(fehler, FEHLER_NACHLADEN));
                dispatch({ typ: 'mehrFehler', schluessel });
            })
            .finally(() => { laedtMehrRef.current = false; });
    }, [aktiv, ordner, q, schluessel, stand.hatMehr, stand.seite, stand.status, toast]);

    const neuLaden = useCallback(() => setDurchlauf(wert => wert + 1), []);
    const aendereTreffer = useCallback((aendern: (eintraege: EmailItem[]) => EmailItem[]) => dispatch({ typ: 'aendern', aendern }), []);

    return {
        begriff, setBegriff, modus, setModus, aktiv,
        treffer: stand.eintraege,
        laedt: aktiv && stand.status === 'laedt',
        fehler: aktiv && stand.status === 'fehler',
        hatMehr: stand.hatMehr,
        laedtMehr: stand.laedtMehr,
        nachladeFehler: stand.nachladeFehler,
        mehrLaden, neuLaden, aendereTreffer,
    };
}

export type EmailSuche = ReturnType<typeof useEmailSuche>;
