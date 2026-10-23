import { leseFehlermeldung } from '../../lib/apiFehler';
import { normalisiereLeistung, normalisiereOrdner } from './ordnerLogik';
import type {
    AuswertungZeile, Leistung, LeistungDto, LeistungVerwendung, Loeschpruefung, LoeschModus, Ordner, OrdnerDto,
} from './typen';

/**
 * Netzwerkzugriffe der Seite „Leistungen & Kategorien“.
 *
 * Jede Funktion wirft bei einem Fehler einen `Error` mit einem Satz, den man
 * dem Nutzer zeigen kann – bei 400/409 die `message` aus dem Backend, sonst
 * einen Ersatztext. CSRF ergänzt der globale Abfangjäger in `main.tsx`.
 */

const KEINE_VERBINDUNG = 'Keine Verbindung zum Server. Bitte prüfen Sie die Verbindung und versuchen Sie es erneut.';

async function anfrage(url: string, init: RequestInit | undefined, ersatz: string): Promise<Response> {
    let res: Response;
    try {
        res = await fetch(url, init);
    } catch {
        throw new Error(KEINE_VERBINDUNG);
    }
    if (!res.ok) throw new Error(await leseFehlermeldung(res, ersatz));
    return res;
}

async function json<T>(url: string, init: RequestInit | undefined, ersatz: string): Promise<T> {
    const res = await anfrage(url, init, ersatz);
    return res.json() as Promise<T>;
}

const JSON_KOPF = { 'Content-Type': 'application/json' };

// ---------------------------------------------------------------------------
// Ordner
// ---------------------------------------------------------------------------

export async function ladeOrdner(): Promise<Ordner[]> {
    const daten = await json<OrdnerDto[]>('/api/produktkategorien?light=true', undefined, 'Die Ordner konnten nicht geladen werden.');
    return (Array.isArray(daten) ? daten : []).map(normalisiereOrdner);
}

export async function legeOrdnerAn(angaben: { name: string; parentId: number | null; leistungenMitnehmen: boolean }): Promise<Ordner> {
    const form = new FormData();
    form.append('bezeichnung', angaben.name);
    if (angaben.parentId !== null) form.append('parentId', String(angaben.parentId));
    form.append('leistungenMitnehmen', String(angaben.leistungenMitnehmen));
    const dto = await json<OrdnerDto>('/api/produktkategorien', { method: 'POST', body: form }, 'Der Ordner konnte nicht angelegt werden.');
    return normalisiereOrdner(dto);
}

export interface OrdnerAenderung {
    name: string;
    einheit: string | null;
    beschreibung: string;
    bild?: File | null;
    bildEntfernen?: boolean;
}

/** PUT mit allen aktuellen Feldern – sonst gingen nicht mitgeschickte Werte verloren. */
export async function speichereOrdner(id: number, aenderung: OrdnerAenderung): Promise<Ordner> {
    const form = new FormData();
    form.append('bezeichnung', aenderung.name);
    if (aenderung.einheit) form.append('verrechnungseinheit', aenderung.einheit);
    form.append('beschreibung', aenderung.beschreibung);
    if (aenderung.bild) form.append('bild', aenderung.bild);
    if (aenderung.bildEntfernen) form.append('bildEntfernen', 'true');
    const dto = await json<OrdnerDto>(`/api/produktkategorien/${id}`, { method: 'PUT', body: form }, 'Der Ordner konnte nicht gespeichert werden.');
    return normalisiereOrdner(dto);
}

/** Alle bisherigen Felder eines Ordners als Grundlage für eine Änderung. */
export function aenderungAus(ordner: Ordner): OrdnerAenderung {
    return { name: ordner.name, einheit: ordner.einheit, beschreibung: ordner.beschreibung };
}

export async function verschiebeOrdner(id: number, parentId: number | null): Promise<void> {
    await anfrage(`/api/produktkategorien/${id}/verschieben`, {
        method: 'PATCH', headers: JSON_KOPF, body: JSON.stringify({ parentId }),
    }, 'Der Ordner konnte nicht verschoben werden.');
}

export async function ladeLoeschpruefung(id: number): Promise<Loeschpruefung> {
    return json<Loeschpruefung>(`/api/produktkategorien/${id}/loeschpruefung`, undefined, 'Es konnte nicht geprüft werden, ob der Ordner noch verwendet wird.');
}

export async function loescheOrdnerMitModus(id: number, modus: LoeschModus, zielKategorieId: number | null): Promise<void> {
    const body = modus === 'UMZIEHEN' ? { modus, zielKategorieId } : { modus };
    await anfrage(`/api/produktkategorien/${id}/loeschen`, {
        method: 'POST', headers: JSON_KOPF, body: JSON.stringify(body),
    }, 'Der Ordner konnte nicht gelöscht werden.');
}

export const ordnerLoeschUrl = (id: number) => `/api/produktkategorien/${id}`;

export async function ladeAuswertung(jahr: string, parentId: number | null): Promise<AuswertungZeile[]> {
    const params = new URLSearchParams();
    if (jahr) params.set('jahr', jahr);
    if (parentId !== null) params.set('parentId', String(parentId));
    const qs = params.toString();
    const daten = await json<AuswertungZeile[]>(`/api/produktkategorien/auswertung${qs ? `?${qs}` : ''}`, undefined, 'Die Auswertung konnte nicht geladen werden.');
    return Array.isArray(daten) ? daten : [];
}

// ---------------------------------------------------------------------------
// Leistungen
// ---------------------------------------------------------------------------

export async function ladeLeistungen(): Promise<Leistung[]> {
    const daten = await json<LeistungDto[]>('/api/leistungen', undefined, 'Die Leistungen konnten nicht geladen werden.');
    return (Array.isArray(daten) ? daten : []).map(normalisiereLeistung);
}

/** Neue Leistung (ohne `id`) oder bestehende speichern. Verschieben = Speichern mit neuem Ordner. */
export async function speichereLeistung(leistung: Omit<Leistung, 'id'> & { id?: number }): Promise<Leistung> {
    const neu = leistung.id === undefined;
    const dto = await json<LeistungDto>(neu ? '/api/leistungen' : `/api/leistungen/${leistung.id}`, {
        method: neu ? 'POST' : 'PUT',
        headers: JSON_KOPF,
        body: JSON.stringify({
            name: leistung.name,
            description: leistung.beschreibung,
            price: leistung.preis,
            unit: leistung.einheit,
            folderId: leistung.ordnerId,
        }),
    }, 'Die Leistung konnte nicht gespeichert werden.');
    return normalisiereLeistung(dto);
}

export async function ladeVerwendung(id: number): Promise<LeistungVerwendung> {
    return json<LeistungVerwendung>(`/api/leistungen/${id}/verwendung`, undefined, 'Es konnte nicht geprüft werden, wo die Leistung verwendet wird.');
}

export const leistungLoeschUrl = (id: number) => `/api/leistungen/${id}`;

export async function loescheSofort(url: string, ersatz: string): Promise<void> {
    await anfrage(url, { method: 'DELETE' }, ersatz);
}
