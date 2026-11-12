import { apiAnfrage, apiJson } from '../../lib/apiFehler';
import { SEITEN_GROESSE } from './firmendokumenteLogik';
import type { DokumentAngaben, DokumentSeite, FirmenDokument, FirmenOrdner, OrdnerRechte, OrdnerRechteAenderung } from './typen';

/**
 * Netzwerkzugriffe der Seite „Firmendokumente“ (Vertrag: `docs/firmendokumente-api.md`).
 * Jede Funktion wirft bei einem Fehler einen `Error` mit der Meldung des
 * Servers (`message`) bzw. einem Ersatztext – die Oberfläche zeigt ihn direkt.
 */

const BASIS = '/api/firmendokumente';
const JSON_KOPF = { 'Content-Type': 'application/json' };

const id = (wert: number) => encodeURIComponent(String(wert));

// ---------------------------------------------------------------------------
// Ordner
// ---------------------------------------------------------------------------

export async function ladeOrdner(): Promise<FirmenOrdner[]> {
    const daten = await apiJson<FirmenOrdner[]>(`${BASIS}/ordner`, undefined, 'Die Ordner konnten nicht geladen werden.');
    return Array.isArray(daten) ? daten : [];
}

export function legeOrdnerAn(name: string, parentId: number | null): Promise<FirmenOrdner> {
    return apiJson(`${BASIS}/ordner`, {
        method: 'POST', headers: JSON_KOPF, body: JSON.stringify({ name, parentId }),
    }, 'Der Ordner konnte nicht angelegt werden.');
}

export function benenneOrdnerUm(ordnerId: number, name: string): Promise<FirmenOrdner> {
    return apiJson(`${BASIS}/ordner/${id(ordnerId)}`, {
        method: 'PUT', headers: JSON_KOPF, body: JSON.stringify({ name }),
    }, 'Der Ordner konnte nicht umbenannt werden.');
}

export function verschiebeOrdner(ordnerId: number, parentId: number | null): Promise<FirmenOrdner> {
    return apiJson(`${BASIS}/ordner/${id(ordnerId)}/verschieben`, {
        method: 'PUT', headers: JSON_KOPF, body: JSON.stringify({ parentId }),
    }, 'Der Ordner konnte nicht verschoben werden.');
}

export async function loescheOrdner(ordnerId: number): Promise<void> {
    await apiAnfrage(`${BASIS}/ordner/${id(ordnerId)}`, { method: 'DELETE' }, 'Der Ordner konnte nicht gelöscht werden.');
}

/** Wer den obersten Ordner sehen und ändern darf – nur für Admins. */
export function ladeOrdnerRechte(ordnerId: number): Promise<OrdnerRechte> {
    return apiJson(`${BASIS}/ordner/${id(ordnerId)}/rechte`, undefined, 'Die Rechte des Ordners konnten nicht geladen werden.');
}

export function speichereOrdnerRechte(ordnerId: number, rechte: OrdnerRechteAenderung): Promise<OrdnerRechte> {
    return apiJson(`${BASIS}/ordner/${id(ordnerId)}/rechte`, {
        method: 'PUT', headers: JSON_KOPF, body: JSON.stringify(rechte),
    }, 'Die Rechte des Ordners konnten nicht gespeichert werden.');
}

// ---------------------------------------------------------------------------
// Dokumente
// ---------------------------------------------------------------------------

function seitenParameter(seite: number): string {
    return `page=${encodeURIComponent(String(seite))}&size=${SEITEN_GROESSE}`;
}

export function ladeDokumente(ordnerId: number, seite: number, signal?: AbortSignal): Promise<DokumentSeite> {
    return apiJson(`${BASIS}?ordnerId=${id(ordnerId)}&${seitenParameter(seite)}`, { signal }, 'Die Dokumente konnten nicht geladen werden.');
}

export function sucheDokumente(suchbegriff: string, seite: number, signal?: AbortSignal): Promise<DokumentSeite> {
    return apiJson(`${BASIS}/suche?q=${encodeURIComponent(suchbegriff)}&${seitenParameter(seite)}`, { signal }, 'Die Suche hat nicht geklappt.');
}

export function ladeFaellige(seite: number, signal?: AbortSignal): Promise<DokumentSeite> {
    return apiJson(`${BASIS}/faellig?${seitenParameter(seite)}`, { signal }, 'Die fälligen Dokumente konnten nicht geladen werden.');
}

export function ladeHoch(datei: File, angaben: DokumentAngaben): Promise<FirmenDokument> {
    const form = new FormData();
    form.append('datei', datei);
    form.append('ordnerId', String(angaben.ordnerId));
    form.append('name', angaben.name);
    if (angaben.faelligAm) form.append('faelligAm', angaben.faelligAm);
    return apiJson(BASIS, { method: 'POST', body: form }, 'Die Datei konnte nicht hochgeladen werden.');
}

/** Name, Ordner und Fällig am in einem – alle drei Felder gehen immer mit. */
export function speichereDokument(dokumentId: number, angaben: DokumentAngaben): Promise<FirmenDokument> {
    return apiJson(`${BASIS}/${id(dokumentId)}`, {
        method: 'PUT', headers: JSON_KOPF,
        body: JSON.stringify({ name: angaben.name, ordnerId: angaben.ordnerId, faelligAm: angaben.faelligAm }),
    }, 'Das Dokument konnte nicht gespeichert werden.');
}

export async function loescheDokument(dokumentId: number): Promise<void> {
    await apiAnfrage(`${BASIS}/${id(dokumentId)}`, { method: 'DELETE' }, 'Das Dokument konnte nicht gelöscht werden.');
}

/** Adresse der Datei – zum Ansehen bzw. mit `download` als Anhang. Aus der ID gebaut, nicht aus fremden Daten. */
export function dateiUrl(dokumentId: number, download = false): string {
    return `${BASIS}/${id(dokumentId)}/datei${download ? '?download=true' : ''}`;
}

/** Kleines Vorschaubild (JPEG, längste Kante höchstens 512 px) – nur für PDFs und Bilder. 404, wenn es keins gibt. */
export function vorschaubildUrl(dokumentId: number): string {
    return `${BASIS}/${id(dokumentId)}/vorschaubild`;
}
