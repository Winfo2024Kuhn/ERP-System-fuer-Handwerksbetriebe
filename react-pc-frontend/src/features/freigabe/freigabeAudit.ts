/** Laden des Annahme-Nachweises einer digitalen Freigabe (ohne React). */

import type { FreigabeRechtsnachweisDaten } from './rechtsnachweisLogik';

/**
 * Beweisdaten einer digitalen Annahme, wie sie `GET /api/ausgangs-dokumente/{id}/freigabe-audit`
 * liefert (`FreigabeAuditDto`). Für eine Auftragsbestätigung kommt die Freigabe des angenommenen
 * Angebots – `dokumentArt`/`dokumentNummer` nennen dann das Angebot.
 */
export type FreigabeAuditDaten = {
    status: 'PENDING' | 'ACCEPTED' | 'EXPIRED' | 'REVOKED';
    dokumentArt: string | null;
    dokumentNummer: string | null;
    erstelltAm: string | null;
    ablaufDatum: string | null;
    akzeptiertAm: string | null;
    akzeptiertEmail: string | null;
    akzeptiertIp: string | null;
    akzeptiertUserAgent: string | null;
    // Vor-/Nachname der konkret klickenden Person (bei Firmenkunden die
    // vertretungsberechtigte Person). Für Altbestand (Akzeptanzen vor V317) null.
    unterzeichnerVorname: string | null;
    unterzeichnerNachname: string | null;
    unterzeichnerName: string | null;
    hashOriginal: string | null;
    hashAcceptance: string | null;
} & Omit<FreigabeRechtsnachweisDaten, 'akzeptiertAm'>;

/**
 * Ergebnis des Ladens: `keine` heißt, das Dokument wurde nicht online angenommen (404),
 * `fehler` steht für Netz- oder Serverfehler.
 */
export type FreigabeAuditErgebnis =
    | { art: 'gefunden'; daten: FreigabeAuditDaten }
    | { art: 'keine' }
    | { art: 'fehler' };

/** Lädt den Annahme-Nachweis eines Ausgangsdokuments; wirft nie. */
export function ladeFreigabeAudit(dokumentId: number): Promise<FreigabeAuditErgebnis> {
    return fetch(`/api/ausgangs-dokumente/${encodeURIComponent(String(dokumentId))}/freigabe-audit`)
        .then((res): Promise<FreigabeAuditErgebnis> | FreigabeAuditErgebnis => {
            if (res.status === 404) return { art: 'keine' };
            if (!res.ok) return { art: 'fehler' };
            return res.json().then((daten: FreigabeAuditDaten) => ({ art: 'gefunden' as const, daten }));
        })
        .catch((): FreigabeAuditErgebnis => ({ art: 'fehler' }));
}
