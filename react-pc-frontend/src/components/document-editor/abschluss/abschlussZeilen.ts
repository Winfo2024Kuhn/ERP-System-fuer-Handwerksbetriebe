/**
 * Eigene Texte und ausgeblendete Zeilen im Abrechnungsstand einer Rechnung.
 *
 * Der Handwerker kann die Zeile "Diese Rechnung" (bzw. Abschlags-/Teil-/
 * Schlussrechnung) und die Differenzzeile ("Zusätzliche Leistungen" /
 * "Nicht angefallen") umbenennen oder von der Rechnung nehmen. Beträge bleiben
 * immer automatisch. Gespeichert wird im positionenJson unter `abschlussZeilen`,
 * ans PDF geht es als `dieseRechnungZeile` / `differenzZeile`.
 *
 * Fehlt ein Wert, gilt der automatische Text. Gespeichert wird nur, was vom
 * Automatischen abweicht: Bestandsrechnungen ohne Schlüssel bleiben unverändert.
 */

/** Gleiche Grenzen wie im Backend (`AbschlussZeileAnpassung.BEZEICHNUNG_MAX_LAENGE` / `UNTERZEILE_MAX_LAENGE`). */
export const BEZEICHNUNG_MAX = 80;
export const UNTERZEILE_MAX = 200;

export type AbschlussZeileSchluessel = 'dieseRechnung' | 'differenz';

const SCHLUESSEL: AbschlussZeileSchluessel[] = ['dieseRechnung', 'differenz'];

export interface AbschlussZeileAnpassung {
    bezeichnung?: string;
    unterzeile?: string;
    /** true = Zeile steht nicht auf der Rechnung. */
    ausgeblendet?: boolean;
}

export type AbschlussZeilen = Partial<Record<AbschlussZeileSchluessel, AbschlussZeileAnpassung>>;

/** Form im PDF-Request (Feld `abrechnungsverlauf.dieseRechnungZeile` bzw. `differenzZeile`). */
export interface PdfAbschlussZeile {
    bezeichnung: string | null;
    unterzeile: string | null;
    ausblenden: boolean;
}

/** Steuer- und Formatzeichen sowie Zeilen-/Absatztrenner (wie `DruckTextBegrenzung` im Backend). */
const STEUERZEICHEN = /[\p{Cc}\p{Cf}\u2028\u2029]/gu;

/**
 * Wie `DruckTextBegrenzung.begrenze` im Backend: Steuerzeichen zu Leerzeichen,
 * trimmen, nach Zeichen (Codepoints) auf `max` kürzen, inklusive „…“.
 * Leer oder kein Text = undefined (automatisch).
 */
export function bereinigeText(wert: unknown, max: number): string | undefined {
    if (typeof wert !== 'string') return undefined;
    const text = wert.replace(STEUERZEICHEN, ' ').trim();
    if (text === '') return undefined;
    const zeichen = Array.from(text);
    return zeichen.length <= max ? text : `${zeichen.slice(0, max - 1).join('')}…`;
}

/** Eine Zeile bereinigen. Nichts Abweichendes = undefined. */
export function normalisiereZeile(wert: unknown): AbschlussZeileAnpassung | undefined {
    if (!wert || typeof wert !== 'object' || Array.isArray(wert)) return undefined;
    const roh = wert as Record<string, unknown>;
    const ergebnis: AbschlussZeileAnpassung = {};
    const bezeichnung = bereinigeText(roh.bezeichnung, BEZEICHNUNG_MAX);
    const unterzeile = bereinigeText(roh.unterzeile, UNTERZEILE_MAX);
    if (bezeichnung) ergebnis.bezeichnung = bezeichnung;
    if (unterzeile) ergebnis.unterzeile = unterzeile;
    if (roh.ausgeblendet === true) ergebnis.ausgeblendet = true;
    return Object.keys(ergebnis).length > 0 ? ergebnis : undefined;
}

/** Beide Zeilen bereinigen, z. B. beim Laden aus dem positionenJson. Fehlt alles: `{}`. */
export function normalisiereAbschlussZeilen(wert: unknown): AbschlussZeilen {
    if (!wert || typeof wert !== 'object' || Array.isArray(wert)) return {};
    const roh = wert as Record<string, unknown>;
    const ergebnis: AbschlussZeilen = {};
    for (const schluessel of SCHLUESSEL) {
        const zeile = normalisiereZeile(roh[schluessel]);
        if (zeile) ergebnis[schluessel] = zeile;
    }
    return ergebnis;
}

/** Wert für `positionenJson.abschlussZeilen` oder undefined, wenn alles automatisch ist. */
export function abschlussZeilenSpeicherwert(zeilen: AbschlussZeilen): AbschlussZeilen | undefined {
    const bereinigt = normalisiereAbschlussZeilen(zeilen);
    return Object.keys(bereinigt).length > 0 ? bereinigt : undefined;
}

/** PDF-Form einer Zeile; null, wenn der Nutzer nichts geändert hat. */
export function pdfZeile(zeile: AbschlussZeileAnpassung | undefined): PdfAbschlussZeile | null {
    const bereinigt = normalisiereZeile(zeile);
    if (!bereinigt) return null;
    return {
        bezeichnung: bereinigt.bezeichnung ?? null,
        unterzeile: bereinigt.unterzeile ?? null,
        ausblenden: bereinigt.ausgeblendet === true,
    };
}

/**
 * Felder `dieseRechnungZeile` / `differenzZeile` für den PDF-Request. Nur, wenn der
 * Editor den Abrechnungsstand auch zeigt: Schliesst eine einfache Rechnung den
 * Auftrag nicht mehr ab (weitere Rechnung am Angebot), bliebe sonst eine
 * unsichtbare Anpassung im PDF.
 */
export function pdfAbschlussZeilen(
    zeilen: AbschlussZeilen,
    sichtbar: boolean,
): { dieseRechnungZeile: PdfAbschlussZeile | null; differenzZeile: PdfAbschlussZeile | null } {
    if (!sichtbar) return { dieseRechnungZeile: null, differenzZeile: null };
    return { dieseRechnungZeile: pdfZeile(zeilen.dieseRechnung), differenzZeile: pdfZeile(zeilen.differenz) };
}

/** Neue Gesamtform mit ersetzter Zeile; ganz automatische Zeilen fallen weg. */
export function setzeZeile(
    zeilen: AbschlussZeilen,
    schluessel: AbschlussZeileSchluessel,
    neu: AbschlussZeileAnpassung | undefined,
): AbschlussZeilen {
    return normalisiereAbschlussZeilen({ ...zeilen, [schluessel]: neu });
}

/** Hat die Zeile eigene Texte (Bezeichnung oder Unterzeile)? */
export function istTextAngepasst(zeile: AbschlussZeileAnpassung | undefined): boolean {
    return Boolean(zeile?.bezeichnung || zeile?.unterzeile);
}

/** Eintrag in der Rückgängig-Liste für den Wechsel von `vorher` zu `nachher`. */
export function aenderungsBezeichnung(
    vorher: AbschlussZeileAnpassung | undefined,
    nachher: AbschlussZeileAnpassung | undefined,
): string {
    const warAus = vorher?.ausgeblendet === true;
    const istAus = nachher?.ausgeblendet === true;
    if (!warAus && istAus) return 'Zeile ausgeblendet';
    if (warAus && !istAus) return 'Zeile eingeblendet';
    return 'Zeilentext geändert';
}
