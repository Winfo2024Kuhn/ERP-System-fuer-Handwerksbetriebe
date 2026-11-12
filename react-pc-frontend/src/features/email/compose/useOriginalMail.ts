import { useEffect, useMemo, useState } from 'react';
import { useToast } from '../../../components/ui/toast';
import { weiterleitbareAnhaenge, type EmailAttachment } from '../emailCenterModel';
import type { PostfachKurz } from '../postfach';

interface OriginalMailOptionen {
    /** Antwort oder Weiterleitung: ID der Original-Mail. */
    originalEmailId?: number;
    weitergeleitetVonEmailId?: number;
    /** Postfach der Original-Mail, falls der Aufrufer es schon kennt (`undefined` = selbst holen). */
    bekanntesAntwortPostfach?: PostfachKurz | null;
    /** Anhänge der Original-Mail, falls der Aufrufer sie schon kennt. */
    bekannteAnhaenge?: EmailAttachment[];
}

/**
 * Was eine Antwort bzw. Weiterleitung von der Original-Mail braucht: den festen
 * Absender (Postfach, in dem die Mail ankam) und bei Weiterleitungen die
 * Anhänge, von denen der Nutzer einzelne abwählen kann. Fehlt etwas, holt der
 * Hook die Original-Mail selbst (z. B. beim Wiederöffnen eines Entwurfs).
 */
export function useOriginalMail({ originalEmailId, weitergeleitetVonEmailId, bekanntesAntwortPostfach, bekannteAnhaenge }: OriginalMailOptionen) {
    const toast = useToast();
    const anhaengeBekannt = bekannteAnhaenge !== undefined;
    /** `undefined` = wird noch ermittelt, Text = Rückfall, wenn die Original-Mail nicht geladen werden konnte. */
    const [festesPostfach, setFestesPostfach] = useState<PostfachKurz | string | null | undefined>(bekanntesAntwortPostfach);
    const [anhaenge, setAnhaenge] = useState<EmailAttachment[]>(() => weiterleitbareAnhaenge(bekannteAnhaenge));
    const [entfernteIds, setEntfernteIds] = useState<Set<number>>(() => new Set());
    const [laedt, setLaedt] = useState(false);
    const mitgesendet = useMemo(() => anhaenge.filter(anhang => !entfernteIds.has(anhang.id)), [anhaenge, entfernteIds]);

    // Ladeanzeigen beim Rendern setzen, sobald etwas zu holen ist.
    const postfachFehlt = bekanntesAntwortPostfach === undefined;
    const anhaengeFehlen = !!weitergeleitetVonEmailId && !anhaengeBekannt;
    const ladeSchluessel = originalEmailId && (postfachFehlt || anhaengeFehlen)
        ? `${originalEmailId}|${postfachFehlt}|${anhaengeFehlen}`
        : null;
    const [ladeSchluesselVorher, setLadeSchluesselVorher] = useState<string | null>(null);
    if (ladeSchluessel !== ladeSchluesselVorher) {
        setLadeSchluesselVorher(ladeSchluessel);
        if (ladeSchluessel) {
            if (postfachFehlt) setFestesPostfach(undefined);
            if (anhaengeFehlen) setLaedt(true);
        }
    }

    useEffect(() => {
        const postfachHolen = bekanntesAntwortPostfach === undefined;
        const anhaengeHolen = !!weitergeleitetVonEmailId && !anhaengeBekannt;
        if (!originalEmailId || (!postfachHolen && !anhaengeHolen)) return;
        let aktiv = true;
        fetch(`/api/emails/${originalEmailId}`)
            .then(res => (res.ok ? res.json() : Promise.reject(new Error(`HTTP ${res.status}`))))
            .then((original: { antwortPostfach?: PostfachKurz | null; attachments?: EmailAttachment[] | null }) => {
                if (!aktiv) return;
                if (postfachHolen) setFestesPostfach(original?.antwortPostfach ?? null);
                if (anhaengeHolen) {
                    setAnhaenge(weiterleitbareAnhaenge(original?.attachments));
                    setEntfernteIds(new Set());
                }
            })
            .catch(() => {
                if (!aktiv) return;
                if (postfachHolen) setFestesPostfach('Postfach, in dem die Mail ankam');
                if (anhaengeHolen) toast.error('Die Anhänge der Original-Mail konnten nicht geladen werden.');
            })
            .finally(() => {
                if (aktiv && anhaengeHolen) setLaedt(false);
            });
        return () => { aktiv = false; };
    }, [originalEmailId, bekanntesAntwortPostfach, weitergeleitetVonEmailId, anhaengeBekannt, toast]);

    // Weitergeleitete Anhänge stecken genauso in der Nachricht und zählen zur Grenze.
    const bytes = useMemo(() => mitgesendet.reduce((summe, anhang) => summe + (anhang.fileSize ?? 0), 0), [mitgesendet]);

    return {
        festesPostfach, anhaenge, mitgesendet, entfernteIds, laedt, bytes,
        entfernen: (id: number) => setEntfernteIds(vorher => new Set(vorher).add(id)),
        alleWiederAnhaengen: () => setEntfernteIds(new Set()),
    };
}

export type OriginalMail = ReturnType<typeof useOriginalMail>;
