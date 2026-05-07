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
