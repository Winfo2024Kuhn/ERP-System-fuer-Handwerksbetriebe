import { KEINE_VERBINDUNG, leseFehlermeldung } from '../../lib/apiFehler';
import type { ZeitkontoStatus } from '../../types/zeitkonto';
import { dateinameAusDisposition } from './berichtFormat';
import type { Stundenart } from './stundenart';

/** Meldung bei 403: Ohne das Recht „Zeiterfassung sehen“ gibt es keine Berichte. */
export const RECHT_FEHLT = 'Dafür fehlt dir das Recht, die Zeiterfassung zu sehen.';
export const NICHT_ANGEMELDET = 'Du bist nicht mehr angemeldet. Bitte melde dich neu an.';

const BASIS = '/api/zeitverwaltung/berichte';

/** Ein Mitarbeiter, der in Berichten auswählbar ist. */
export interface BerichtMitarbeiter {
    id: number;
    name: string;
}

/** Eine Zeile je Mitarbeiter; bewusst ohne Sollstunden des Monats (keine Gesamtstunden an den Steuerberater). */
export interface SteuerberaterZeile {
    mitarbeiterId: number;
    name: string;
    tagessoll: number;
    arbeitsstunden: number;
    urlaubStunden: number;
    feiertagStunden: number;
    krankheitStunden: number;
    fortbildungStunden: number;
    stundenkontoStand: number;
    resturlaubTage: number;
    festgeschrieben: boolean;
}

/** Antwort von `GET /api/zeitverwaltung/berichte/steuerberater`. */
export interface SteuerberaterVorschau {
    jahr: number;
    monat: number;
    /** Welche Stunden übermittelt werden (Admin-Einstellung, vom Server gewählt). */
    stundenart: Stundenart;
    zeilen: SteuerberaterZeile[];
    alleFestgeschrieben: boolean;
}

/** Ein geladenes PDF samt Dateiname aus der Server-Antwort. */
export interface GeladenesPdf {
    blob: Blob;
    dateiname: string;
}

/**
 * `fetch` für Berichte: Jeder Fehlschlag wirft einen `Error` mit einem Satz für
 * den Nutzer (401/403 eigene Hinweise, sonst die Meldung des Servers).
 */
async function berichtAnfrage(url: string, ersatz: string, signal?: AbortSignal): Promise<Response> {
    let res: Response;
    try {
        res = await fetch(url, { signal });
    } catch (err) {
        if (err instanceof DOMException && err.name === 'AbortError') throw err;
        throw new Error(KEINE_VERBINDUNG, { cause: err });
    }
    if (res.status === 401) throw new Error(NICHT_ANGEMELDET);
    if (res.status === 403) throw new Error(RECHT_FEHLT);
    if (!res.ok) throw new Error(await leseFehlermeldung(res, ersatz));
    return res;
}

function idListe(ids: number[]): string {
    return ids.join(',');
}

/** Mitarbeiter mit eingerichtetem Zeitkonto, ohne Geschäftsführer (wie bisher beim Steuerberater). */
export async function ladeBerichtMitarbeiter(signal?: AbortSignal): Promise<BerichtMitarbeiter[]> {
    const res = await berichtAnfrage('/api/zeitverwaltung/zeitkonten', 'Die Mitarbeiter konnten nicht geladen werden.', signal);
    const daten = await res.json() as ZeitkontoStatus[];
    if (!Array.isArray(daten)) return [];
    return daten
        // Menschen ohne aktuelles Zeitkonto fehlen nur in der Auswahl; ihre alten Daten bleiben im Backend.
        .filter(konto => konto.fuehrtZeitkonto && konto.aktuell !== null && !konto.istGeschaeftsfuehrer)
        .map(konto => ({ id: konto.mitarbeiterId, name: konto.mitarbeiterName }));
}

export interface SteuerberaterParameter {
    jahr: number;
    monat: number;
    mitarbeiterIds: number[];
}

export interface SteuerberaterPdfParameter extends SteuerberaterParameter {
    mitResturlaub: boolean;
    mitStundenkonto: boolean;
    /** Aus der Vorschau übernommen, damit PDF und Vorschau garantiert dieselben Stunden zeigen. */
    stundenart: Stundenart;
}

function steuerberaterSuche({ jahr, monat, mitarbeiterIds }: SteuerberaterParameter): URLSearchParams {
    const suche = new URLSearchParams({ jahr: String(jahr), monat: String(monat) });
    if (mitarbeiterIds.length > 0) suche.set('mitarbeiterIds', idListe(mitarbeiterIds));
    return suche;
}

/** Vorschau ohne `stundenart`: Der Server nimmt die Admin-Einstellung und nennt sie in der Antwort. */
export async function ladeSteuerberaterVorschau(p: SteuerberaterParameter, signal?: AbortSignal): Promise<SteuerberaterVorschau> {
    const res = await berichtAnfrage(`${BASIS}/steuerberater?${steuerberaterSuche(p)}`, 'Die Vorschau konnte nicht geladen werden.', signal);
    return res.json() as Promise<SteuerberaterVorschau>;
}

export function steuerberaterPdfUrl(p: SteuerberaterPdfParameter): string {
    const suche = steuerberaterSuche(p);
    suche.set('mitResturlaub', String(p.mitResturlaub));
    suche.set('mitStundenkonto', String(p.mitStundenkonto));
    suche.set('stundenart', p.stundenart);
    return `${BASIS}/steuerberater/pdf?${suche}`;
}

export interface MitarbeiterPdfParameter {
    mitarbeiterIds: number[];
    von: string;
    bis: string;
    mitTagesliste: boolean;
    mitUnterschrift: boolean;
}

export function mitarbeiterPdfUrl(p: MitarbeiterPdfParameter): string {
    const suche = new URLSearchParams({
        mitarbeiterIds: idListe(p.mitarbeiterIds),
        von: p.von,
        bis: p.bis,
        mitTagesliste: String(p.mitTagesliste),
        mitUnterschrift: String(p.mitUnterschrift),
    });
    return `${BASIS}/mitarbeiter/pdf?${suche}`;
}

/** Lädt ein Bericht-PDF; den Dateinamen nennt der Server, sonst gilt `ersatzName`. */
export async function ladePdf(url: string, ersatzName: string, signal?: AbortSignal): Promise<GeladenesPdf> {
    const res = await berichtAnfrage(url, 'Das PDF konnte nicht erstellt werden.', signal);
    const dateiname = dateinameAusDisposition(res.headers.get('Content-Disposition'), ersatzName);
    return { blob: await res.blob(), dateiname };
}
