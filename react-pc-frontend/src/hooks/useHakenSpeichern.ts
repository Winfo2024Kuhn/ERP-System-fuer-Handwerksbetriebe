import { useCallback, useRef, useState } from 'react';
import { useToast } from '../components/ui/toast';
import { leseFehlermeldung } from '../lib/apiFehler';

export interface HakenAnfrage {
    /** Zeile, deren Haken umgestellt wird – sie bleibt bis zur Antwort gesperrt. */
    id: number;
    /** Schickt die Änderung ans Backend. */
    senden: () => Promise<Response>;
    /** Deutscher Satz für den Fall, dass der Server keine eigene Meldung mitschickt. */
    fehlertext: string;
    /** Eigene Meldungen für einzelne Statuscodes, z. B. 403 mit Hinweis auf das fehlende Recht. */
    meldungJeStatus?: Partial<Record<number, string>>;
}

/**
 * Speichert einen Haken in einer Liste („Bezahlt“, „Genehmigt“ …):
 * - sperrt die Zeile, solange die Anfrage läuft: ein zweiter Aufruf für dieselbe Zeile
 *   wird ignoriert, und `inArbeit` sagt dem Aufrufer, welche Haken er deaktivieren soll,
 * - zeigt jeden Fehler als Toast – mit der Meldung vom Server oder dem Ersatztext,
 * - lädt die Liste nach jeder Server-Antwort neu, damit der Haken den gespeicherten
 *   Stand zeigt (z. B. nach einer Änderung von jemand anderem). Nach einem
 *   Verbindungsfehler nicht – dann hat sich auf dem Server nichts geändert.
 *
 * `neuLaden` meldet eigene Fehler selbst (Toast) und wirft nicht.
 */
export function useHakenSpeichern(neuLaden: () => Promise<void>) {
    const toast = useToast();
    const [inArbeit, setInArbeit] = useState<ReadonlySet<number>>(() => new Set());
    // Sofort sichtbar, anders als der State – schützt auch vor zwei Klicks im selben Render.
    const laufend = useRef(new Set<number>());

    const speichern = useCallback(async ({ id, senden, fehlertext, meldungJeStatus }: HakenAnfrage) => {
        if (laufend.current.has(id)) return;
        laufend.current.add(id);
        setInArbeit(ids => new Set(ids).add(id));
        try {
            let res: Response;
            try {
                res = await senden();
            } catch {
                toast.error(`${fehlertext} Bitte die Verbindung prüfen und es noch einmal versuchen.`);
                return;
            }
            if (!res.ok) {
                toast.error(meldungJeStatus?.[res.status] ?? await leseFehlermeldung(res, fehlertext));
            }
            await neuLaden();
        } finally {
            laufend.current.delete(id);
            setInArbeit(ids => {
                const rest = new Set(ids);
                rest.delete(id);
                return rest;
            });
        }
    }, [neuLaden, toast]);

    return { inArbeit, speichern };
}
