import type { SignaturDto, SignaturSpeichernBody } from './signaturTypen';

/**
 * Aufrufe rund um Signaturen (`/api/email/signatures`). Jede Funktion wirft
 * bei einem Fehler eine `Error` mit deutscher Meldung für den Toast – bevorzugt
 * die `message` des Servers.
 */

const BASIS = '/api/email/signatures';

async function fehlerText(res: Response, standard: string): Promise<string> {
    try {
        const body = await res.json() as { message?: unknown };
        if (typeof body?.message === 'string' && body.message.trim()) return body.message;
    } catch {
        // Antwort ohne JSON – Standardtext
    }
    if (res.status === 403) return 'Dafür fehlt Ihnen die Berechtigung.';
    if (res.status === 404) return 'Diese Signatur gibt es nicht mehr oder Sie dürfen sie nicht sehen.';
    return standard;
}

async function anfrage<T>(url: string, init: RequestInit | undefined, standardFehler: string): Promise<T | null> {
    let res: Response;
    try {
        res = await fetch(url, init);
    } catch {
        throw new Error(`${standardFehler} Bitte Verbindung prüfen.`);
    }
    if (!res.ok) throw new Error(await fehlerText(res, standardFehler));
    if (res.status === 204) return null;
    try {
        return await res.json() as T;
    } catch {
        return null;
    }
}

const JSON_KOPF = { 'Content-Type': 'application/json' };

/** Rohdaten defensiv auf den Vertrag bringen – alte Antworten kennen manche Felder nicht. */
export function normalisiereSignatur(roh: unknown): SignaturDto | null {
    if (!roh || typeof roh !== 'object') return null;
    const s = roh as Record<string, unknown>;
    if (typeof s.id !== 'number') return null;
    return {
        id: s.id,
        name: typeof s.name === 'string' ? s.name : '',
        html: typeof s.html === 'string' ? s.html : '',
        vorlageDaten: typeof s.vorlageDaten === 'string' ? s.vorlageDaten : null,
        defaultSignature: s.defaultSignature === true,
        isSystemDefault: s.isSystemDefault === true,
        besitzerId: typeof s.besitzerId === 'number' ? s.besitzerId : null,
        besitzerName: typeof s.besitzerName === 'string' ? s.besitzerName : null,
        eigene: s.eigene === true,
        firmenVorlage: s.firmenVorlage === true,
        firmenVorlageId: typeof s.firmenVorlageId === 'number' ? s.firmenVorlageId : null,
        nurLesen: s.nurLesen === true,
        createdAt: typeof s.createdAt === 'string' ? s.createdAt : '',
        updatedAt: typeof s.updatedAt === 'string' ? s.updatedAt : '',
        images: Array.isArray(s.images) ? s.images as SignaturDto['images'] : [],
    };
}

const alsListe = (daten: unknown): SignaturDto[] =>
    (Array.isArray(daten) ? daten.map(normalisiereSignatur).filter((s): s is SignaturDto => s !== null) : []);

export async function ladeSignaturen(): Promise<SignaturDto[]> {
    return alsListe(await anfrage<unknown>(BASIS, undefined, 'Signaturen konnten nicht geladen werden.'));
}

/** Alle Firmen-Vorlagen mit Formulardaten, nach Name sortiert. Für alle angemeldeten Benutzer. */
export async function ladeFirmenVorlagen(): Promise<SignaturDto[]> {
    return alsListe(await anfrage<unknown>(`${BASIS}/firmen-vorlagen`, undefined, 'Firmen-Vorlagen konnten nicht geladen werden.'));
}

/** Nur Admins: Signatur als Firmen-Vorlage markieren. */
export async function setzeFirmenVorlage(id: number): Promise<void> {
    await anfrage(`${BASIS}/${id}/firmen-vorlage`, { method: 'PUT' }, 'Firmen-Vorlage konnte nicht festgelegt werden.');
}

/** Nur Admins: Markierung als Firmen-Vorlage wieder entfernen. */
export async function entferneFirmenVorlage(id: number): Promise<void> {
    await anfrage(`${BASIS}/${id}/firmen-vorlage`, { method: 'DELETE' }, 'Firmen-Vorlage konnte nicht entfernt werden.');
}

/** Eine Signatur komplett – mit den Formulardaten, die die Liste aus Platzgründen weglässt. */
export async function ladeSignatur(id: number): Promise<SignaturDto> {
    const daten = await anfrage<unknown>(`${BASIS}/${id}`, undefined, 'Signatur konnte nicht geladen werden.');
    const signatur = normalisiereSignatur(daten);
    if (!signatur) throw new Error('Signatur konnte nicht geladen werden.');
    return signatur;
}

export async function speichereSignatur(body: SignaturSpeichernBody): Promise<SignaturDto> {
    const daten = await anfrage<unknown>(BASIS, { method: 'POST', headers: JSON_KOPF, body: JSON.stringify(body) },
        'Signatur konnte nicht gespeichert werden.');
    const signatur = normalisiereSignatur(daten);
    if (!signatur) throw new Error('Signatur konnte nicht gespeichert werden.');
    return signatur;
}

export async function loescheSignatur(id: number): Promise<void> {
    await anfrage(`${BASIS}/${id}`, { method: 'DELETE' }, 'Signatur konnte nicht gelöscht werden.');
}

export async function setzeStandardSignatur(id: number): Promise<void> {
    await anfrage(`${BASIS}/${id}/standard`, { method: 'PUT' }, 'Standard-Signatur konnte nicht gesetzt werden.');
}

export async function entferneStandardSignatur(): Promise<void> {
    await anfrage(`${BASIS}/standard`, { method: 'DELETE' }, 'Standard-Signatur konnte nicht entfernt werden.');
}

export async function setzeSystemSignatur(id: number): Promise<void> {
    await anfrage(`${BASIS}/${id}/system-default`, { method: 'PUT' }, 'System-Signatur konnte nicht gesetzt werden.');
}

/** Vorschau mit aufgelösten Bildern alter Signaturen (cid → Bild-Link). */
export async function ladeSignaturVorschau(id: number): Promise<string> {
    const daten = await anfrage<{ html?: unknown }>(`${BASIS}/${id}/preview`, undefined, 'Vorschau konnte nicht geladen werden.');
    return typeof daten?.html === 'string' ? daten.html : '';
}

export interface FirmenDaten {
    firmenname: string | null;
    strasse: string | null;
    plz: string | null;
    ort: string | null;
    telefon: string | null;
    fax: string | null;
    email: string | null;
    website: string | null;
    firmenfarbe: string | null;
}

/** Firmendaten zum Vorausfüllen. Fehler sind hier nicht schlimm – dann bleibt das Formular leer. */
export async function ladeFirmenDaten(): Promise<FirmenDaten | null> {
    try {
        const res = await fetch('/api/firma');
        if (!res.ok || res.status === 204) return null;
        const roh = await res.json() as Record<string, unknown>;
        const text = (wert: unknown) => (typeof wert === 'string' && wert.trim() ? wert.trim() : null);
        return {
            firmenname: text(roh.firmenname),
            strasse: text(roh.strasse),
            plz: text(roh.plz),
            ort: text(roh.ort),
            telefon: text(roh.telefon),
            fax: text(roh.fax),
            email: text(roh.email),
            website: text(roh.website),
            firmenfarbe: text(roh.firmenfarbe),
        };
    } catch {
        return null;
    }
}

export interface BenutzerKurz {
    id: number;
    displayName: string;
    active: boolean;
}

/** Benutzer für die Besitzer-Auswahl (nur Admins). */
export async function ladeBenutzer(): Promise<BenutzerKurz[]> {
    const daten = await anfrage<unknown>('/api/frontend-users', undefined, 'Benutzerliste konnte nicht geladen werden.');
    if (!Array.isArray(daten)) return [];
    return daten
        .filter((b): b is Record<string, unknown> => !!b && typeof b === 'object' && typeof (b as { id?: unknown }).id === 'number')
        .map((b) => ({
            id: b.id as number,
            displayName: typeof b.displayName === 'string' && b.displayName.trim() ? b.displayName : `Benutzer ${b.id as number}`,
            active: b.active !== false,
        }));
}
