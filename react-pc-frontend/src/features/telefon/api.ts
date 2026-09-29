import type {
    AbholErgebnis,
    KontaktRufnummer,
    AktenTyp,
    KontaktTyp,
    KontaktUeberblick,
    Seite,
    Sprachnachricht,
    SteuerberaterAuswahl,
    TelefonAnruf,
    TelefonEinstellungen,
    TelefonStatus,
    TelefonVerbindungstest,
    ZuordnenZiel,
} from './types';

/**
 * Alle Aufrufe der Telefon-Schnittstelle an einer Stelle.
 *
 * <p>Fehler kommen als {@link Error} mit der Meldung des Backends
 * (`{message}`) heraus, damit die Oberfläche sie direkt im Toast zeigen kann.
 * Fehlt eine Meldung, greift der übergebene Ersatztext in Handwerker-Sprache.</p>
 */

const BASIS = '/api/telefon';

async function meldungAus(res: Response, ersatz: string): Promise<string> {
    if (res.status === 403) return 'Dafür fehlt Ihnen das Recht „Anrufe & Anrufbeantworter“.';
    try {
        const text = await res.text();
        const daten = JSON.parse(text);
        if (typeof daten?.message === 'string' && daten.message.trim()) return daten.message;
    } catch {
        // Kein JSON im Körper – dann bleibt es beim Ersatztext.
    }
    return ersatz;
}

async function holeJson<T>(url: string, ersatz: string, init?: RequestInit): Promise<T> {
    const res = await fetch(url, init);
    if (!res.ok) throw new Error(await meldungAus(res, ersatz));
    return res.json() as Promise<T>;
}

function jsonInit(method: string, body?: unknown): RequestInit {
    return {
        method,
        headers: { 'Content-Type': 'application/json' },
        body: body === undefined ? undefined : JSON.stringify(body),
    };
}

/**
 * Pfad zur Kunden- bzw. Lieferantenakte. Steuerberater haben keine eigene
 * Akte (nur Firma › Steuerberater für Administratoren) – dann `null` und
 * es gibt keinen Link.
 */
export function aktenPfad(typ: KontaktTyp, id: number): string | null {
    if (typ === 'KUNDE') return `/kunden?kundeId=${encodeURIComponent(String(id))}`;
    if (typ === 'LIEFERANT') return `/lieferanten?lieferantId=${encodeURIComponent(String(id))}`;
    return null;
}

const ID_PARAMETER: Record<KontaktTyp, string> = {
    KUNDE: 'kundeId',
    LIEFERANT: 'lieferantId',
    STEUERBERATER: 'steuerberaterId',
};

/** Pfad in ein Projekt. */
export function projektPfad(id: number): string {
    return `/projekte?projektId=${encodeURIComponent(String(id))}`;
}

/** Pfad in eine Anfrage. */
export function anfragePfad(id: number): string {
    return `/anfragen?anfrageId=${encodeURIComponent(String(id))}`;
}

/** Adresse der Aufnahme – als `<audio src>` und Download-Link nutzbar. */
export function audioPfad(id: number): string {
    return `${BASIS}/sprachnachrichten/${encodeURIComponent(String(id))}/audio`;
}

export async function ladeBerechtigung(signal?: AbortSignal): Promise<boolean> {
    const res = await fetch(`${BASIS}/berechtigung`, { signal });
    if (!res.ok) return false;
    const daten = await res.json();
    return daten?.darfTelefonSehen === true;
}

export function ladeStatus(): Promise<TelefonStatus> {
    return holeJson<TelefonStatus>(`${BASIS}/status`, 'Der Telefon-Stand konnte nicht geladen werden.');
}

export interface AnrufFilter {
    art?: string;
    nurUnbekannt?: boolean;
    /** Nur verpasste Anrufe, um die sich noch keiner gekümmert hat (wie in der Glocke). */
    nurOffen?: boolean;
    suche?: string;
    /** ISO-Tag (`2026-09-29`): nur Anrufe dieses Tages. */
    tag?: string;
    /** Nur Anrufe von Kunden, Lieferanten oder Steuerberatern. */
    kontaktart?: KontaktTyp;
    kundeId?: number;
    lieferantId?: number;
    seite?: number;
    groesse?: number;
}

interface SpringPage<T> {
    content?: T[];
    totalElements?: number;
    totalPages?: number;
    page?: { totalElements?: number; totalPages?: number };
}

export async function ladeAnrufe(filter: AnrufFilter, signal?: AbortSignal): Promise<Seite<TelefonAnruf>> {
    const params = new URLSearchParams();
    if (filter.art) params.set('art', filter.art);
    if (filter.nurUnbekannt) params.set('nurUnbekannt', 'true');
    if (filter.nurOffen) params.set('nurOffen', 'true');
    if (filter.suche?.trim()) params.set('suche', filter.suche.trim());
    if (filter.tag) params.set('tag', filter.tag);
    if (filter.kontaktart) params.set('kontaktart', filter.kontaktart);
    if (filter.kundeId) params.set('kundeId', String(filter.kundeId));
    if (filter.lieferantId) params.set('lieferantId', String(filter.lieferantId));
    params.set('seite', String(filter.seite ?? 0));
    params.set('groesse', String(filter.groesse ?? 50));
    const daten = await holeJson<SpringPage<TelefonAnruf>>(
        `${BASIS}/anrufe?${params}`, 'Die Anrufe konnten nicht geladen werden.', { signal });
    // Spring liefert die Seitenangaben je nach Einstellung flach oder unter "page".
    const inhalt = daten.content ?? [];
    return {
        inhalt,
        gesamt: daten.totalElements ?? daten.page?.totalElements ?? inhalt.length,
        seiten: daten.totalPages ?? daten.page?.totalPages ?? 1,
    };
}

export interface NachrichtenFilter {
    nurNeue?: boolean;
    anrufbeantworter?: number | null;
    /** ISO-Tag (`2026-09-29`): nur Nachrichten dieses Tages. */
    tag?: string;
    kundeId?: number;
    lieferantId?: number;
}

export function ladeSprachnachrichten(filter: NachrichtenFilter, signal?: AbortSignal): Promise<Sprachnachricht[]> {
    const params = new URLSearchParams();
    if (filter.nurNeue) params.set('nurNeue', 'true');
    if (filter.anrufbeantworter !== undefined && filter.anrufbeantworter !== null) {
        params.set('anrufbeantworter', String(filter.anrufbeantworter));
    }
    if (filter.tag) params.set('tag', filter.tag);
    if (filter.kundeId) params.set('kundeId', String(filter.kundeId));
    if (filter.lieferantId) params.set('lieferantId', String(filter.lieferantId));
    const query = params.toString();
    return holeJson<Sprachnachricht[]>(
        `${BASIS}/sprachnachrichten${query ? `?${query}` : ''}`,
        'Die Nachrichten auf dem Anrufbeantworter konnten nicht geladen werden.',
        { signal });
}

export async function ladeAnzahlNeueNachrichten(signal?: AbortSignal): Promise<number> {
    const res = await fetch(`${BASIS}/sprachnachrichten/anzahl-neu`, { signal });
    if (!res.ok) return 0;
    const daten = await res.json();
    return typeof daten?.anzahl === 'number' ? daten.anzahl : 0;
}

export function setzeAbgehoert(id: number, abgehoert: boolean): Promise<Sprachnachricht> {
    return holeJson<Sprachnachricht>(
        `${BASIS}/sprachnachrichten/${id}`,
        abgehoert ? 'Die Nachricht konnte nicht als abgehört markiert werden.' : 'Die Nachricht konnte nicht wieder als neu markiert werden.',
        jsonInit('PATCH', { abgehoert }));
}

/** Genau eins von kundeId/lieferantId/steuerberaterId ist gesetzt. */
export interface ZuordnenDaten {
    kundeId: number | null;
    lieferantId: number | null;
    steuerberaterId: number | null;
    nummerMerken: boolean;
    /** Nur bei Steuerberatern: Nummer bei diesem Ansprechpartner als Telefon eintragen. */
    ansprechpartnerId: number | null;
}

/**
 * Zuordnen-Daten für einen gewählten Kontakt. Mit `ansprechpartnerId` landet die
 * Nummer beim Ansprechpartner der Kanzlei – dafür muss `nummerMerken` gesetzt sein.
 */
export function zuordnenDaten(typ: KontaktTyp, id: number, nummerMerken: boolean, ansprechpartnerId: number | null = null): ZuordnenDaten {
    return {
        kundeId: typ === 'KUNDE' ? id : null,
        lieferantId: typ === 'LIEFERANT' ? id : null,
        steuerberaterId: typ === 'STEUERBERATER' ? id : null,
        nummerMerken,
        ansprechpartnerId: typ === 'STEUERBERATER' ? ansprechpartnerId : null,
    };
}

/** Kanzleien mit ihren Ansprechpartnern zur Auswahl beim Zuordnen (es sind nur wenige). */
export function ladeSteuerberaterAuswahl(signal?: AbortSignal): Promise<SteuerberaterAuswahl[]> {
    return holeJson<SteuerberaterAuswahl[]>(`${BASIS}/steuerberater`, 'Die Steuerberater konnten nicht geladen werden.', { signal });
}

function zuordnungsPfad(ziel: ZuordnenZiel): string {
    return ziel.art === 'anruf'
        ? `${BASIS}/anrufe/${ziel.id}/zuordnung`
        : `${BASIS}/sprachnachrichten/${ziel.id}/zuordnung`;
}

export function ordneZu<T extends TelefonAnruf | Sprachnachricht>(ziel: ZuordnenZiel, daten: ZuordnenDaten): Promise<T> {
    return holeJson<T>(zuordnungsPfad(ziel), 'Der Kontakt konnte nicht zugeordnet werden.', jsonInit('POST', daten));
}

export function hebeZuordnungAuf<T extends TelefonAnruf | Sprachnachricht>(ziel: ZuordnenZiel): Promise<T> {
    return holeJson<T>(zuordnungsPfad(ziel), 'Die Zuordnung konnte nicht aufgehoben werden.', jsonInit('DELETE'));
}

export function holeJetztAb(): Promise<AbholErgebnis> {
    return holeJson<AbholErgebnis>(`${BASIS}/abholen`, 'Die Anrufe konnten nicht von der FRITZ!Box abgeholt werden.', jsonInit('POST'));
}

export async function ladeKontaktRufnummern(typ: AktenTyp, id: number, signal?: AbortSignal): Promise<KontaktRufnummer[]> {
    const param = typ === 'KUNDE' ? 'kundeId' : 'lieferantId';
    const res = await fetch(`${BASIS}/kontakt-rufnummern?${param}=${encodeURIComponent(String(id))}`, { signal });
    if (!res.ok) return [];
    const daten = await res.json();
    return Array.isArray(daten) ? daten : [];
}

/** Adresse, Ansprechpartner, Projekte und Anfragen des Anrufers für das Anruf-Fenster. */
export function ladeKontaktUeberblick(typ: KontaktTyp, id: number, signal?: AbortSignal): Promise<KontaktUeberblick> {
    const param = ID_PARAMETER[typ];
    return holeJson<KontaktUeberblick>(
        `${BASIS}/kontakt-ueberblick?${param}=${encodeURIComponent(String(id))}`,
        'Projekte und Anfragen konnten nicht geladen werden.', { signal });
}

export async function loescheKontaktRufnummer(id: number): Promise<void> {
    const res = await fetch(`${BASIS}/kontakt-rufnummern/${id}`, { method: 'DELETE' });
    if (!res.ok) throw new Error(await meldungAus(res, 'Die Rufnummer konnte nicht entfernt werden.'));
}

// ── Nur für Administratoren ────────────────────────────────────────────

export function ladeEinstellungen(): Promise<TelefonEinstellungen> {
    return holeJson<TelefonEinstellungen>(`${BASIS}/einstellungen`, 'Die Telefon-Einstellungen konnten nicht geladen werden.');
}

export interface EinstellungenSpeichern {
    aktiv: boolean;
    host: string;
    benutzer: string;
    /** null = unverändert lassen. */
    passwort: string | null;
    geschaeftsnummern: string[];
    anrufbeantworter: { index: number; name: string }[];
    aufbewahrungAnrufeMonate: number;
    aufbewahrungSprachnachrichtenMonate: number;
}

export function speichereEinstellungen(daten: EinstellungenSpeichern): Promise<TelefonEinstellungen> {
    return holeJson<TelefonEinstellungen>(`${BASIS}/einstellungen`, 'Die Telefon-Einstellungen konnten nicht gespeichert werden.', jsonInit('PUT', daten));
}

export function testeVerbindung(daten: { host?: string; benutzer?: string; passwort?: string }): Promise<TelefonVerbindungstest> {
    return holeJson<TelefonVerbindungstest>(`${BASIS}/einstellungen/test`, 'Die Verbindung zur FRITZ!Box konnte nicht geprüft werden.', jsonInit('POST', daten));
}

export function holeAeltereNach(tage: number): Promise<AbholErgebnis> {
    return holeJson<AbholErgebnis>(
        `${BASIS}/admin/nachholen?tage=${encodeURIComponent(String(tage))}`,
        'Ältere Anrufe konnten nicht nachgeholt werden.', jsonInit('POST'));
}
