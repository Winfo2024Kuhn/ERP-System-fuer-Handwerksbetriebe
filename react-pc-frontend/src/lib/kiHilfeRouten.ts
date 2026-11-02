import { BESTELLBEDARF_PFAD, istFreigegeben } from './freigaben';

/**
 * Seiten, auf die die KI-Hilfe in ihren Antworten verlinken darf. Nicht
 * freigegebene Seiten fehlen, ihr Link erscheint dann nur als fetter Text.
 */
export const KI_HILFE_ROUTEN: ReadonlySet<string> = new Set([
    '/projekte', '/anfragen', '/kunden', '/lieferanten', '/artikel',
    '/bestellungen', BESTELLBEDARF_PFAD, '/textbausteine', '/leistungen',
    '/arbeitsgaenge', '/mitarbeiter', '/arbeitszeitarten',
    '/kalender', '/emails', '/formulare', '/offeneposten',
    '/rechnungsuebersicht', '/miete', '/analyse', '/zeitbuchungen',
    '/auswertung', '/steuerberater', '/zeitkonten', '/feiertage',
    '/urlaubsantraege', '/firma', '/benutzer', '/firmendokumente',
].filter(istFreigegeben));

/** Ziel eines Links aus einer KI-Antwort (`projekte` oder `/projekte`) – `null`, wenn die Seite nicht verlinkt werden darf. */
export function kiHilfeLinkZiel(roh: string): string | null {
    const pfad = roh.startsWith('/') ? roh : `/${roh}`;
    return KI_HILFE_ROUTEN.has(pfad) ? pfad : null;
}
