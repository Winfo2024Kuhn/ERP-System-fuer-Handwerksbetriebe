export interface DecimalRules {
    label: string;
    required?: boolean;
    min?: number;
    max?: number;
    integer?: boolean;
}
export type InputValidation<T> = { valid: true; value: T | null } | { valid: false; message: string };

/** Keine Gruppierung: ein Eingabeentwurf bleibt ohne mehrdeutige Tausenderpunkte. */
export function formatDecimalInput(value: number): string {
    if (!Number.isFinite(value)) throw new RangeError('Die Zahl muss endlich sein.');
    return value.toLocaleString('de-DE', { useGrouping: false, maximumSignificantDigits: 21 });
}

/** Auch in Aktionen ohne HTML-Formular VOR jeder Mutation/Berechnung aufrufen. */
export function validateDecimalInput(draft: string, rules: DecimalRules): InputValidation<number> {
    const text = draft.trim();
    if (!text) return rules.required
        ? { valid: false, message: `Bitte ${rules.label} eingeben.` }
        : { valid: true, value: null };
    if (!/^[+-]?\d+(?:,\d+)?$/.test(text)) {
        return { valid: false, message: `Bitte ${rules.label} als vollständige Zahl mit Dezimalkomma eingeben (z. B. 12,5).` };
    }
    const value = Number(text.replace(',', '.'));
    if (!Number.isFinite(value)) return { valid: false, message: `${rules.label}: Die Zahl ist zu groß.` };
    if (rules.integer && !Number.isSafeInteger(value)) {
        return { valid: false, message: `Bitte ${rules.label} als gültige ganze Zahl eingeben.` };
    }
    if (rules.min !== undefined && value < rules.min) {
        return { valid: false, message: `${rules.label} muss mindestens ${formatDecimalInput(rules.min)} sein.` };
    }
    if (rules.max !== undefined && value > rules.max) {
        return { valid: false, message: `${rules.label} darf höchstens ${formatDecimalInput(rules.max)} sein.` };
    }
    return { valid: true, value };
}
