import { extractEmailAddress, infoAdresseZuDomain } from '../../lib/emailAddress';

/**
 * Die Reiter der Lieferanten-Detailansicht stehen in der Adresszeile
 * (`/lieferanten?lieferantId=7&tab=dokumente`).
 *
 * <p>Dadurch landet ein Klick auf "Neuer Lieferschein" in der Glocke direkt
 * bei den Dokumenten statt im E-Mail-Verlauf, ein Link lässt sich teilen,
 * und der Zurück-Knopf des Browsers führt wieder aus der Detailansicht
 * heraus zur Liste.</p>
 */
export const LIEFERANT_TABS = ['emails', 'ansprechpartner', 'anrufe', 'dokumente', 'notizen', 'reklamationen'] as const;
export type LieferantTab = typeof LIEFERANT_TABS[number];

export function istGueltigerTab(value: string | null): value is LieferantTab {
    return value !== null && LIEFERANT_TABS.some((tab) => tab === value);
}

/**
 * Ergänzt die Adressliste um eine neue Adresse – und hängt die allgemeine
 * `info@`-Adresse derselben Domain mit an, falls sie noch fehlt.
 *
 * Trägt jemand also `bestellung@meier.de` ein, steht danach auch `info@meier.de`
 * zur Auswahl. Grund: An die Zentrale gehen Reklamationen und allgemeine Post,
 * und im Alltag denkt beim Anlegen eines Lieferanten niemand daran, sie separat
 * nachzutragen. Sichtbar als Chip in der Liste – wer sie nicht will, entfernt sie.
 *
 * Die eingetragene Adresse bleibt bewusst vorn: Die erste Adresse der Liste wird
 * an anderer Stelle als Standard-Empfänger vorbelegt, und das soll die Adresse
 * sein, die der Benutzer selbst gewählt hat – nicht die automatisch ergänzte.
 */
export function mitInfoAdresse(vorhandene: string[] | undefined, neueAdresse: string): string[] {
    const liste = [...(vorhandene || [])];
    const bereitsEnthalten = (adresse: string) =>
        liste.some(e => extractEmailAddress(e).toLowerCase() === adresse.toLowerCase());

    const neu = neueAdresse.trim();
    if (!neu) return liste;

    if (!bereitsEnthalten(extractEmailAddress(neu))) {
        liste.push(neu);
    }
    const infoAdresse = infoAdresseZuDomain(neu);
    if (infoAdresse && !bereitsEnthalten(infoAdresse)) {
        liste.push(infoAdresse);
    }
    return liste;
}
