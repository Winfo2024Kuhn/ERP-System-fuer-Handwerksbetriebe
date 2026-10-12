/**
 * Schalter für Bereiche, die fachlich noch nicht fertig sind.
 *
 * Ein Bereich mit `false` bleibt im Code, ist für die Nutzer aber nicht
 * erreichbar: kein Menüpunkt, kein Link aus der KI-Hilfe, und die Adresse
 * leitet um. Freigeben heißt: Schalter auf `true` setzen,
 * `docs/BESTELLWESEN.md` anpassen und die Seite im KI-Hilfe-Prompt
 * (`KiHilfeService.BASE_SYSTEM_PROMPT`) wieder eintragen.
 */

/** Seite „Bedarf“ (Einkauf): Material, das für Projekte noch bestellt werden muss. */
export const BESTELLBEDARF_AKTIV = false;

/**
 * Knopf „E-Mail“ je Lieferant auf der Seite „Bedarf“ (Bestellanfrage per E-Mail).
 * Der Dialog dahinter (`BestellungEmailModal`) ist vorbereitet und getestet.
 */
export const BESTELLANFRAGE_PER_EMAIL_AKTIV = false;

export const BESTELLBEDARF_PFAD = '/bestellungen/bedarf';

/** Ist die Seite unter diesem Pfad für die Nutzer freigegeben? */
export function istFreigegeben(pfad: string): boolean {
    return pfad !== BESTELLBEDARF_PFAD || BESTELLBEDARF_AKTIV;
}
