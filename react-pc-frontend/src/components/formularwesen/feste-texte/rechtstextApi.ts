import createDOMPurify from 'dompurify';
import { leseFehlermeldung } from '../../../lib/apiFehler';

/**
 * Typen, Aufrufe und Hilfen für „Feste Texte“ (Widerrufsbelehrung & Co.).
 * Alle Aufrufe gehen an /api/formulare/rechtstexte. Den CSRF-Kopf setzt der
 * globale Interceptor in main.tsx, hier bewusst nicht.
 */

const BASIS = '/api/formulare/rechtstexte';

/** Höchstlänge, die das Backend annimmt (Zeichen HTML). */
export const MAX_ZEICHEN = 50_000;

/** Kurzfassung eines festen Textes, so wie die Liste sie liefert. */
export interface RechtstextUebersicht {
    art: string;
    bezeichnung: string;
    /** 0 = noch nie gespeichert, es gilt der mitgelieferte Standardtext. */
    fassung: number;
    /** ISO-Datum, z. B. „2026-10-13“. */
    stand: string;
    standardtext: boolean;
    /** ISO-Datum-Uhrzeit oder null, solange nie gespeichert. */
    geaendertAm: string | null;
}

export interface RechtstextPlatzhalter {
    token: string;
    beschreibung: string;
    beispiel: string;
}

export interface RechtstextDetail extends RechtstextUebersicht {
    html: string;
    platzhalter: RechtstextPlatzhalter[];
}

export interface RechtstextFassungKurz {
    fassung: number;
    stand: string;
    geaendertAm: string | null;
}

export interface RechtstextFassung extends RechtstextFassungKurz {
    html: string;
}

/** Fehler mit verständlicher Meldung aus dem Backend (`{ message }`). */
export class RechtstextFehler extends Error {
    public readonly status: number;

    constructor(message: string, status: number) {
        super(message);
        this.status = status;
        this.name = 'RechtstextFehler';
    }
}

async function anfrage(url: string, ersatz: string, optionen?: RequestInit): Promise<Response> {
    let res: Response;
    try {
        res = await fetch(url, optionen);
    } catch {
        throw new RechtstextFehler('Keine Verbindung zum Server. Bitte später erneut versuchen.', 0);
    }
    if (!res.ok) {
        throw new RechtstextFehler(await leseFehlermeldung(res, ersatz), res.status);
    }
    return res;
}

async function holeJson<T>(url: string, ersatz: string, optionen?: RequestInit): Promise<T> {
    const res = await anfrage(url, ersatz, optionen);
    return res.json() as Promise<T>;
}

function artPfad(art: string): string {
    return `${BASIS}/${encodeURIComponent(art)}`;
}

function jsonKoerper(body: unknown): RequestInit {
    return {
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
    };
}

export function ladeRechtstexte(): Promise<RechtstextUebersicht[]> {
    return holeJson(BASIS, 'Die festen Texte konnten nicht geladen werden.');
}

export function ladeRechtstext(art: string): Promise<RechtstextDetail> {
    return holeJson(artPfad(art), 'Der Text konnte nicht geladen werden.');
}

export function speichereRechtstext(art: string, html: string): Promise<RechtstextDetail> {
    return holeJson(artPfad(art), 'Der Text konnte nicht gespeichert werden.', {
        method: 'PUT',
        ...jsonKoerper({ html }),
    });
}

export function setzeAufStandard(art: string): Promise<RechtstextDetail> {
    return holeJson(`${artPfad(art)}/standard`, 'Der Standardtext konnte nicht gesetzt werden.', {
        method: 'POST',
    });
}

export function ladeFassungen(art: string): Promise<RechtstextFassungKurz[]> {
    return holeJson(`${artPfad(art)}/fassungen`, 'Die früheren Fassungen konnten nicht geladen werden.');
}

export function ladeFassung(art: string, fassung: number): Promise<RechtstextFassung> {
    return holeJson(
        `${artPfad(art)}/fassungen/${encodeURIComponent(String(fassung))}`,
        'Diese Fassung konnte nicht geladen werden.',
    );
}

/** PDF des übergebenen Textes; `null` = gespeicherter Stand. */
export async function ladeVorschau(art: string, html: string | null): Promise<Blob> {
    const res = await anfrage(`${artPfad(art)}/vorschau`, 'Die Vorschau konnte nicht erstellt werden.', {
        method: 'POST',
        ...jsonKoerper({ html }),
    });
    return res.blob();
}

// ─── Anzeige ────────────────────────────────────────────────────────────────

/**
 * Schriftbild für Editor und schreibgeschützte Ansicht – dieselben Abstände,
 * damit eine frühere Fassung genauso aussieht wie im Editor.
 */
export const RECHTSTEXT_TYPO = [
    'text-sm leading-relaxed text-slate-800',
    '[&_p]:my-2 [&_h3]:mt-4 [&_h3]:mb-2 [&_h3]:text-base [&_h3]:font-semibold [&_h3]:text-slate-900',
    '[&_ul]:my-2 [&_ul]:list-disc [&_ul]:pl-6 [&_ol]:my-2 [&_ol]:list-decimal [&_ol]:pl-6',
    '[&_strong]:font-semibold [&_b]:font-semibold',
].join(' ');

/** „2026-10-13“ → „13.10.2026“. Ohne Date-Objekt, damit keine Zeitzone verrutscht. */
export function formatiereDatum(iso: string | null | undefined): string {
    const treffer = /^(\d{4})-(\d{2})-(\d{2})/.exec(iso ?? '');
    if (!treffer) return '';
    return `${treffer[3]}.${treffer[2]}.${treffer[1]}`;
}

/** „2026-10-13T14:05:00“ → „13.10.2026, 14:05 Uhr“. */
export function formatiereZeitpunkt(iso: string | null | undefined): string {
    const datum = formatiereDatum(iso);
    if (!datum) return '';
    const uhrzeit = /T(\d{2}):(\d{2})/.exec(iso ?? '');
    return uhrzeit ? `${datum}, ${uhrzeit[1]}:${uhrzeit[2]} Uhr` : datum;
}

/**
 * Untertitel einer Karte bzw. des Dialogkopfs, z. B. „Stand 13.10.2026 · Fassung 3“.
 * Fassung 0 heißt: noch nie gespeichert, es gilt der mitgelieferte Standardtext.
 */
export function beschreibeStand(text: Pick<RechtstextUebersicht, 'fassung' | 'stand'>): string {
    const stand = `Stand ${formatiereDatum(text.stand)}`;
    return text.fassung <= 0
        ? `${stand} · Standardtext nach gesetzlichem Muster`
        : `${stand} · Fassung ${text.fassung}`;
}

// ─── Bereinigung ────────────────────────────────────────────────────────────

/**
 * Der Editor hängt hinter einer Überschrift oder Liste am Ende einen leeren
 * Absatz an, damit man dort weiterschreiben kann. Der soll nicht mitgespeichert werden.
 */
export function ohneLeereAbsaetzeAmEnde(html: string): string {
    let ergebnis = html;
    while (ergebnis.endsWith('<p></p>')) ergebnis = ergebnis.slice(0, -'<p></p>'.length);
    return ergebnis;
}

/** Genau die Tags, die das Backend für feste Texte durchlässt. */
export const ERLAUBTE_RECHTSTEXT_TAGS = ['p', 'br', 'strong', 'b', 'em', 'i', 'h3', 'ul', 'ol', 'li'];

/**
 * Eigene DOMPurify-Instanz (Vorbild `bereinigeBeitragsHtml`), damit
 * Einstellungen hier nie auf das geteilte `sanitizeHtml()` durchschlagen.
 */
const rechtstextDomPurify = createDOMPurify(window);

/**
 * Bereinigt einen festen Text vor der Anzeige: nur die erlaubten Tags, keine
 * Attribute. Strenger als `sanitizeHtml()` – Skripte, Bilder, Links und
 * Ereignis-Handler fallen weg, der Text darin bleibt.
 */
export function bereinigeRechtstextHtml(html: string | null | undefined): string {
    if (!html) return '';
    return rechtstextDomPurify.sanitize(html, {
        ALLOWED_TAGS: ERLAUBTE_RECHTSTEXT_TAGS,
        ALLOWED_ATTR: [],
    });
}
