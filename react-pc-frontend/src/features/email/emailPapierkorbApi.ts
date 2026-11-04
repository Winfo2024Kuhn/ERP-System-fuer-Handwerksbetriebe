import { leseFehlermeldung } from '../../lib/apiFehler';
import { inBloecke } from './emailLoeschen';

export const ZURUECKHOLEN_URL = '/api/emails/papierkorb/zurueckholen';
export const ZURUECKHOLEN_FEHLER = 'E-Mails konnten nicht zurückgeholt werden.';

export const PAPIERKORB_URL = '/api/emails/papierkorb';

/** Zahl aus der Antwort (`verschoben`/`zurueckgeholt`); fehlt sie, gilt der ganze Block. */
async function leseAnzahl(res: Response, feld: string, ersatz: number): Promise<number> {
    const daten: unknown = await res.json().catch(() => null);
    const wert = daten && typeof daten === 'object' ? (daten as Record<string, unknown>)[feld] : undefined;
    return typeof wert === 'number' && Number.isFinite(wert) ? wert : ersatz;
}

/**
 * Schickt die IDs blockweise. Liefert die angenommenen IDs, wie viele Mails der Server laut
 * Antwort wirklich bewegt hat, und den ersten Fehlertext.
 */
export async function sendeBloecke(url: string, ids: number[], ersatz: string, zahlFeld: string) {
    const erledigt: number[] = [];
    let anzahl = 0;
    let meldung: string | null = null;
    for (const block of inBloecke(ids)) {
        try {
            const res = await fetch(url, {
                method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ ids: block }),
            });
            if (res.ok) {
                erledigt.push(...block);
                anzahl += await leseAnzahl(res, zahlFeld, block.length);
            } else meldung ??= await leseFehlermeldung(res, ersatz);
        } catch {
            meldung ??= ersatz;
        }
    }
    return { erledigt, anzahl, meldung };
}
