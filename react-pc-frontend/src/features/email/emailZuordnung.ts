import { anfragePfad, lieferantPfad, projektPfad } from '../../lib/navigationPfade';
import type { EmailItem } from './emailCenterModel';

export type EmailZuordnungArt = 'Projekt' | 'Anfrage' | 'Lieferant' | 'Steuerberater';

/** Wohin eine E-Mail gehört – fertig aufbereitet für die Anzeige. */
export interface EmailZuordnung {
    art: EmailZuordnungArt;
    /** Kurzer Anzeigetext, z. B. „Projekt 2026-041 · Garagentor“. */
    label: string;
    /** Ziel in der App; `null`, wenn es keine eigene Seite dafür gibt. */
    pfad: string | null;
    /** Langer Text für Tooltip und Screenreader. */
    titel: string;
}

type ZuordnungsFelder = Pick<EmailItem,
    'zuordnungTyp' | 'projektId' | 'projektName' | 'projektAuftragsnummer'
    | 'anfrageId' | 'anfrageName' | 'lieferantId' | 'lieferantName'>;

/** Felder kommen vom Backend auch als `null` – deshalb großzügig typisiert. */
type Nullbar<T> = { [K in keyof T]?: T[K] | null };

function text(wert: string | null | undefined): string | null {
    const getrimmt = wert?.trim();
    return getrimmt ? getrimmt : null;
}

function gueltigeId(id: number | null | undefined): number | null {
    return typeof id === 'number' && Number.isFinite(id) && id > 0 ? id : null;
}

/**
 * „Projekt 2026-041 · Garagentor“, ohne Name „Projekt 2026-041“,
 * ohne Name und Nummer „Projekt #12“.
 */
function beschrifte(art: EmailZuordnungArt, nummer: string | null, name: string | null, id: number | null): string {
    const kopf = nummer ? `${art} ${nummer}` : art;
    if (name) return `${kopf} · ${name}`;
    if (nummer || id === null) return kopf;
    return `${art} #${id}`;
}

function baue(art: EmailZuordnungArt, nummer: string | null, name: string | null, id: number | null,
    pfad: (id: number) => string): EmailZuordnung {
    const label = beschrifte(art, nummer, name, id);
    const ziel = id !== null ? pfad(id) : null;
    return { art, label, pfad: ziel, titel: ziel ? `${label} öffnen` : label };
}

function projekt(e: Nullbar<ZuordnungsFelder>): EmailZuordnung {
    return baue('Projekt', text(e.projektAuftragsnummer), text(e.projektName), gueltigeId(e.projektId), projektPfad);
}

function anfrage(e: Nullbar<ZuordnungsFelder>): EmailZuordnung {
    return baue('Anfrage', null, text(e.anfrageName), gueltigeId(e.anfrageId), anfragePfad);
}

function lieferant(e: Nullbar<ZuordnungsFelder>): EmailZuordnung {
    return baue('Lieferant', null, text(e.lieferantName), gueltigeId(e.lieferantId), lieferantPfad);
}

/**
 * Leitet aus einer E-Mail ab, zu welchem Projekt, welcher Anfrage oder welchem
 * Lieferanten sie gehört.
 *
 * Der Zuordnungstyp vom Backend hat Vorrang: Eine Projekt-Mail kann zusätzlich
 * einen Lieferanten-Verweis tragen, gehört aber zum Projekt. Fehlt der Typ,
 * entscheiden die gesetzten IDs in der Reihenfolge Projekt → Anfrage → Lieferant.
 * `KEINE` (oder gar nichts) ergibt `null`.
 */
export function ermittleEmailZuordnung(email: Nullbar<ZuordnungsFelder>): EmailZuordnung | null {
    switch (email.zuordnungTyp?.trim().toUpperCase()) {
        case 'KEINE': return null;
        case 'PROJEKT': return projekt(email);
        case 'ANFRAGE': return anfrage(email);
        case 'LIEFERANT': return lieferant(email);
        case 'STEUERBERATER':
            return { art: 'Steuerberater', label: 'Steuerberater', pfad: null, titel: 'Steuerberater' };
        default:
            if (gueltigeId(email.projektId) !== null) return projekt(email);
            if (gueltigeId(email.anfrageId) !== null) return anfrage(email);
            if (gueltigeId(email.lieferantId) !== null) return lieferant(email);
            return null;
    }
}
