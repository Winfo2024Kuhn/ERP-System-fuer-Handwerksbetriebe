/**
 * Wandelt eine vom Nutzer eingegebene Telefonnummer (mit -, /, (), Leerzeichen)
 * in eine für tel:-Links und Wählen tauglich Form um.
 *
 * Behält ein führendes "+" für internationale Nummern; entfernt sonst alle
 * Nicht-Ziffern. Mobile-App und Desktop können das Ergebnis direkt in
 * <a href={`tel:${toDialable(value)}`}> verwenden.
 */
export function toDialablePhone(input: string | null | undefined): string {
    if (!input) return '';
    const trimmed = input.trim();
    const hasPlus = trimmed.startsWith('+');
    const digits = trimmed.replace(/\D/g, '');
    return hasPlus ? `+${digits}` : digits;
}

/**
 * Akzeptiert in einem <input> erlaubte Zeichen für Telefonnummern:
 * Ziffern, +, -, /, (, ), Leerzeichen.
 * Wird zur sanften Filterung beim onChange verwendet.
 */
export function sanitizePhoneInput(input: string): string {
    return input.replace(/[^\d+\-/() ]/g, '');
}

/**
 * Unter dieser Ziffernzahl steht nach einer 0 keine vollständige Rufnummer im Feld,
 * sondern nur die Ortsvorwahl, die `PhoneInput` beim Eingeben der Adresse vorschlägt.
 * Gleiche Grenze wie `Telefonnummer.MINDEST_ZIFFERN` im Backend.
 */
const MINDEST_ZIFFERN = 7;

/**
 * Wert zum Speichern: getrimmt; eine reine Vorwahl („0931 “, „+49 931“) wird leer.
 * Kurze Ortsnummern ohne Vorwahl („12345“) bleiben stehen – wie im Backend
 * (`Telefonnummer.ohneReineVorwahl`).
 */
export function ohneReineVorwahl(input: string | null | undefined): string {
    const eingabe = input?.trim() ?? '';
    // +49 / 0049 wie die führende 0 behandeln, sonst zählt die Ländervorwahl als Rufnummer mit.
    const ziffern = eingabe.replace(/^(?:\+|00)49/, '0').replace(/\D/g, '');
    const reineVorwahl = ziffern.startsWith('0') && ziffern.length < MINDEST_ZIFFERN;
    return reineVorwahl ? '' : eingabe;
}
