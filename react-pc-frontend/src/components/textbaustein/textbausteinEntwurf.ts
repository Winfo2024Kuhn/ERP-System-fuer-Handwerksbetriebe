import { chipHtmlZuKuerzel, verwendeteKuerzel } from './platzhalter';
import type { TextbausteinDto, TextbausteinEntwurf, TextbausteinSpeichernDaten } from './typen';

export const NAME_MAX_LAENGE = 150;

/** Kurzer, sinnvoller Anfang für einen neuen Textbaustein. */
export const NEUER_TEXT = '<p>{{Anrede}} {{Ansprechpartner}},</p><p>vielen Dank für Ihre Anfrage …</p>';

/** Neuer Textbaustein; steht der Filter auf einer Dokumentart, ist sie schon eingeschaltet. */
export function neuerEntwurf(vorauswahl?: string): TextbausteinEntwurf {
    return {
        id: null,
        name: '',
        html: NEUER_TEXT,
        dokumenttypen: vorauswahl ? [vorauswahl] : [],
        typ: 'FREITEXT',
        beschreibung: null,
        sortOrder: null,
    };
}

export function entwurfAus(baustein: TextbausteinDto): TextbausteinEntwurf {
    return {
        id: baustein.id,
        name: baustein.name,
        html: baustein.html ?? '',
        dokumenttypen: [...baustein.dokumenttypen],
        typ: baustein.typ,
        beschreibung: baustein.beschreibung,
        sortOrder: baustein.sortOrder,
    };
}

/** Kopie als neuer Textbaustein: gleicher Text, gleiche Zuordnung, Name mit „Kopie von“. */
export function kopieVon(baustein: TextbausteinDto): TextbausteinEntwurf {
    const name = `Kopie von ${baustein.name}`.slice(0, NAME_MAX_LAENGE);
    return { ...entwurfAus(baustein), id: null, name, sortOrder: null };
}

/**
 * Was gespeichert wird: Chips werden wieder zu ihren Kürzeln, `placeholders`
 * nennt nur die Kürzel, die im Text stehen. `typ`, `beschreibung` und
 * `sortOrder` gehen so zurück, wie sie geladen wurden.
 */
export function speichernDaten(
    entwurf: TextbausteinEntwurf,
    eingaben: { name: string; dokumenttypen: string[]; chipHtml: string },
): TextbausteinSpeichernDaten {
    const html = chipHtmlZuKuerzel(eingaben.chipHtml);
    return {
        name: eingaben.name.trim(),
        typ: entwurf.typ,
        beschreibung: entwurf.beschreibung,
        sortOrder: entwurf.sortOrder,
        html,
        placeholders: verwendeteKuerzel(html),
        dokumenttypen: eingaben.dokumenttypen,
    };
}

/**
 * Ob beim Schließen nachgefragt werden muss. `vergleichsHtml` ist der Stand des
 * Editors direkt nach dem Laden (mit Kürzeln), damit Tippen + Rückgängig nicht zählt.
 * Die Reihenfolge der Dokumentarten spielt keine Rolle.
 */
export function hatAenderungen(
    entwurf: TextbausteinEntwurf,
    eingaben: { name: string; dokumenttypen: string[]; chipHtml: string },
    vergleichsHtml: string,
): boolean {
    const vorher = new Set(entwurf.dokumenttypen);
    const jetzt = new Set(eingaben.dokumenttypen);
    return eingaben.name !== entwurf.name
        || vorher.size !== jetzt.size
        || [...jetzt].some((art) => !vorher.has(art))
        || chipHtmlZuKuerzel(eingaben.chipHtml) !== vergleichsHtml;
}
