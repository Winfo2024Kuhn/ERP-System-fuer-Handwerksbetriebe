import type { InfoText } from './berichtTypen';

/**
 * Welche Stunden der Steuerberater bekommt. Ein Admin legt das unter
 * Einstellungen → Zeiterfassung fest. Die Vorschau fragt ohne Stundenart
 * (der Server nimmt die Einstellung und nennt sie in der Antwort); jedes PDF
 * schickt genau diese Stundenart mit, damit PDF und Vorschau übereinstimmen.
 */
export type Stundenart = 'SOLL' | 'IST';

/** Pfad der Einstellung (Reiter Zeiterfassung). */
export const STUNDENART_EINSTELLUNG_PFAD = '/einstellungen#zeiterfassung';

/** „Sollstunden“ / „Iststunden“. */
export function stundenartName(art: Stundenart): string {
    return art === 'IST' ? 'Iststunden' : 'Sollstunden';
}

/** Spaltenkopf der Arbeitsstunden, z. B. „Arbeitsstunden (Soll)“. */
export function arbeitsstundenTitel(art: Stundenart): string {
    return art === 'IST' ? 'Arbeitsstunden (Ist)' : 'Arbeitsstunden (Soll)';
}

const INFO_SOLL: InfoText = {
    titel: 'Arbeitsstunden (Soll)',
    hintergrund: 'Das Programm nimmt die Sollstunden des Monats und zieht Urlaub, Feiertage, Krankheit und Fortbildung ab.',
    bewirkt: 'So steht beim Steuerberater die bezahlte Arbeitszeit nach Vertrag – Überstunden landen nicht im Lohn, sondern im Stundenkonto.',
};

const INFO_IST: InfoText = {
    titel: 'Arbeitsstunden (Ist)',
    hintergrund: 'Das Programm zählt die wirklich gebuchte Arbeitszeit des Monats, ohne Pausen.',
    bewirkt: 'So steht beim Steuerberater, was tatsächlich gearbeitet wurde – auch Überstunden. Urlaub, Feiertage und Krankheit stehen in eigenen Spalten.',
};

/** Erklärtext für die Spalte „Arbeitsstunden“ passend zur Stundenart. */
export function arbeitsstundenInfo(art: Stundenart): InfoText {
    return art === 'IST' ? INFO_IST : INFO_SOLL;
}
