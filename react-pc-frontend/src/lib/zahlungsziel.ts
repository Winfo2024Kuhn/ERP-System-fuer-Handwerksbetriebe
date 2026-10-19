/**
 * Zahlungsziel in Tagen: Grenzen für alle Eingaben (Kunden-Formulare und
 * Dokument-Editor), Textprüfung für die Kunden-Formulare. Der Dokument-Editor
 * prüft selbst, weil er zusätzlich über 8 Tagen nachfragt. Das Backend lehnt
 * dieselben Grenzen ab (`KundeCreateRequestDto`, @Min/@Max).
 */

/** Standard, wenn weder Dokument noch Kunde eines gepflegt haben. */
export const DEFAULT_ZAHLUNGSZIEL_TAGE = 8;

/** Kleinstes erlaubtes Zahlungsziel. */
export const MIN_ZAHLUNGSZIEL_TAGE = 1;

/** Größtes erlaubtes Zahlungsziel (ein Jahr). */
export const MAX_ZAHLUNGSZIEL_TAGE = 365;

export const ZAHLUNGSZIEL_MELDUNG =
    `Das Zahlungsziel muss zwischen ${MIN_ZAHLUNGSZIEL_TAGE} und ${MAX_ZAHLUNGSZIEL_TAGE} Tagen liegen.`;

export type ZahlungszielPruefung =
    | { gueltig: true; tage: number }
    | { gueltig: false; meldung: string };

/**
 * Prüft einen Eingabe-Entwurf (Text, wie im Feld getippt). Nur ganze Tage,
 * keine Vorzeichen, kein Komma; Leerraum außen herum ist erlaubt.
 */
export function pruefeZahlungszielTage(entwurf: string): ZahlungszielPruefung {
    const text = entwurf.trim();
    if (!/^\d{1,3}$/.test(text)) return { gueltig: false, meldung: ZAHLUNGSZIEL_MELDUNG };
    const tage = Number(text);
    if (tage < MIN_ZAHLUNGSZIEL_TAGE || tage > MAX_ZAHLUNGSZIEL_TAGE) {
        return { gueltig: false, meldung: ZAHLUNGSZIEL_MELDUNG };
    }
    return { gueltig: true, tage };
}
