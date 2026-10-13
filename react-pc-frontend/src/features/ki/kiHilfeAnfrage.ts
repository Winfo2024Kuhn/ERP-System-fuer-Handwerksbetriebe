import { leseFehlermeldung } from '../../lib/apiFehler';
import { leseSseEreignisse } from '../../lib/sseLeser';

/** Web-Quelle, die die KI für eine Fachfrage per Google-Suche gefunden hat. */
export interface KiQuelle {
    title: string;
    url: string;
}

/** Fertige Antwort der KI-Hilfe. */
export interface KiAntwort {
    /** Antworttext als einfaches Markdown. */
    reply: string;
    sources: KiQuelle[];
    /** Wo die KI nachgeschaut hat, in der Reihenfolge der Schritte (reiner Text). */
    schritte: string[];
}

/** Was der Chat an die KI-Hilfe schickt (Aufbau wie `KiHilfeController`). */
export interface KiAnfrage {
    messages: { role: 'user' | 'assistant'; text: string }[];
    context: {
        route: string;
        pageTitle: string;
        visibleContent?: string;
        errorMessages?: string;
        latitude?: number;
        longitude?: number;
    };
}

export const KI_VERBINDUNGSFEHLER = 'Verbindung zur KI fehlgeschlagen. Ist der Server erreichbar?';
const KI_ANFRAGE_ABGELEHNT = 'Die KI-Hilfe konnte die Frage nicht annehmen. Bitte versuche es erneut.';
const KI_NICHT_VERFUEGBAR = 'KI-Hilfe ist gerade nicht verfügbar. Bitte versuche es später erneut.';

/** Fehler mit einer Meldung, die so im Chat stehen darf. */
export class KiHilfeFehler extends Error {
    constructor(meldung: string) {
        super(meldung);
        this.name = 'KiHilfeFehler';
    }
}

function leseJson(text: string): unknown {
    try {
        return JSON.parse(text);
    } catch {
        return null;
    }
}

/** Liest ein Textfeld aus einem JSON-Objekt; leere Texte zählen als fehlend. */
function textFeld(inhalt: unknown, feld: string): string | undefined {
    if (!inhalt || typeof inhalt !== 'object') return undefined;
    const wert = (inhalt as Record<string, unknown>)[feld];
    return typeof wert === 'string' && wert.trim() ? wert : undefined;
}

/** Nur echte Web-Adressen – alles andere (z. B. `javascript:`) fällt weg. */
function quellenAus(roh: unknown): KiQuelle[] {
    if (!Array.isArray(roh)) return [];
    return roh.flatMap((eintrag) => {
        const url = textFeld(eintrag, 'url');
        if (!url || !/^https?:\/\//i.test(url)) return [];
        return [{ title: textFeld(eintrag, 'title') ?? '', url }];
    });
}

function texteAus(roh: unknown): string[] {
    if (!Array.isArray(roh)) return [];
    return roh.filter((eintrag): eintrag is string => typeof eintrag === 'string' && eintrag.trim() !== '');
}

function zuAntwort(inhalt: unknown): KiAntwort {
    const reply = textFeld(inhalt, 'reply');
    // Ohne Antworttext ist die Antwort unterwegs kaputtgegangen.
    if (reply === undefined) throw new KiHilfeFehler(KI_VERBINDUNGSFEHLER);
    const { sources, schritte } = inhalt as Record<string, unknown>;
    return { reply, sources: quellenAus(sources), schritte: texteAus(schritte) };
}

/**
 * Stellt der KI-Hilfe eine Frage und liefert die fertige Antwort.
 *
 * <p>Das Backend antwortet als Ereignisstrom: Während die KI im Programm
 * nachschlägt, kommen Ereignisse `schritt` (jeder wird sofort an
 * `beiSchritt` gemeldet), am Ende genau ein `antwort` oder ein `fehler`.
 * Fehler, bevor die KI loslegt (z. B. leere Nachricht), kommen als JSON mit
 * `message` bzw. `error`.</p>
 *
 * <p>Meldungen für den Chat kommen als {@link KiHilfeFehler}. Ein Abbruch über
 * `signal` und ein nicht erreichbarer Server werfen den Fehler von `fetch`
 * unverändert weiter – der Aufrufer unterscheidet über `signal.aborted`.</p>
 */
export async function stelleKiFrage(
    anfrage: KiAnfrage,
    optionen: { signal?: AbortSignal; beiSchritt?: (text: string) => void } = {},
): Promise<KiAntwort> {
    const res = await fetch('/api/ki-hilfe/chat', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(anfrage),
        signal: optionen.signal,
    });
    if (!res.ok || res.headers.get('Content-Type')?.includes('json')) {
        throw new KiHilfeFehler(await leseFehlermeldung(res, KI_ANFRAGE_ABGELEHNT));
    }
    for await (const { event, data } of leseSseEreignisse(res)) {
        const inhalt = leseJson(data);
        if (event === 'schritt') {
            const text = textFeld(inhalt, 'text');
            if (text) optionen.beiSchritt?.(text);
        } else if (event === 'antwort') {
            return zuAntwort(inhalt);
        } else if (event === 'fehler') {
            throw new KiHilfeFehler(textFeld(inhalt, 'error') ?? KI_NICHT_VERFUEGBAR);
        }
    }
    // Strom zu Ende ohne Antwort und ohne Fehler: Verbindung abgerissen.
    throw new KiHilfeFehler(KI_VERBINDUNGSFEHLER);
}
