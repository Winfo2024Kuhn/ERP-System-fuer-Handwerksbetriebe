/**
 * Link aus einer Meldung („Zeiten prüfen“) in die Tagesansicht des Kalenders:
 * `/zeitbuchungen?mitarbeiterId=…&jahr=…&monat=…&tag=yyyy-MM-dd&anlass=…&markiert=1,2`.
 * Den Link baut das Backend; hier wird er nur defensiv gelesen.
 */

export type TagLinkAnlass = 'ueberschneidung' | 'ohne-feierabend';

export interface TagLink {
    /** Tag, dessen Tagesansicht aufgehen soll (yyyy-MM-dd). */
    tag: string;
    /** IDs der Buchungen, die in der Tagesansicht hervorgehoben werden. */
    markiert: number[];
    /** Wählt den Hinweistext; null = nur markieren. */
    anlass: TagLinkAnlass | null;
}

export const MAX_MARKIERTE_BUCHUNGEN = 10;

const LINK_PARAMETER = ['tag', 'markiert', 'anlass'] as const;
const ANLAESSE: readonly TagLinkAnlass[] = ['ueberschneidung', 'ohne-feierabend'];

function istGueltigerTag(wert: string): boolean {
    const treffer = /^(\d{4})-(\d{2})-(\d{2})$/.exec(wert);
    if (!treffer) return false;
    const [jahr, monat, tag] = treffer.slice(1).map(Number);
    const datum = new Date(Date.UTC(jahr, monat - 1, tag));
    return datum.getUTCFullYear() === jahr && datum.getUTCMonth() === monat - 1 && datum.getUTCDate() === tag;
}

function leseMarkierte(wert: string | null): number[] {
    if (!wert) return [];
    const ids: number[] = [];
    for (const teil of wert.split(',')) {
        const text = teil.trim();
        if (!/^\d{1,16}$/.test(text)) continue;
        const id = Number(text);
        if (!Number.isSafeInteger(id) || id <= 0 || ids.includes(id)) continue;
        ids.push(id);
        if (ids.length === MAX_MARKIERTE_BUCHUNGEN) break;
    }
    return ids;
}

/** Liest Tag, Markierung und Anlass; ohne gültigen Tag gibt es keinen Link (Altbestand). */
export function leseTagLink(params: URLSearchParams): TagLink | null {
    const tag = params.get('tag');
    if (!tag || !istGueltigerTag(tag)) return null;
    const anlass = params.get('anlass');
    return {
        tag,
        markiert: leseMarkierte(params.get('markiert')),
        anlass: ANLAESSE.find(a => a === anlass) ?? null,
    };
}

/** Dieselben Parameter ohne die Link-Teile; Mitarbeiter, Jahr und Monat bleiben. */
export function ohneTagLink(params: URLSearchParams): URLSearchParams {
    const naechste = new URLSearchParams(params);
    LINK_PARAMETER.forEach(name => naechste.delete(name));
    return naechste;
}

/** Erkennungszeichen eines Links, damit er genau einmal geöffnet wird. */
export function tagLinkSchluessel(mitarbeiterId: number | null, link: TagLink): string {
    return [mitarbeiterId ?? '', link.tag, link.markiert.join(','), link.anlass ?? ''].join('|');
}
