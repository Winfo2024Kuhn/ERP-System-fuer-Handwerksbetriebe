import { validateDecimalInput } from './numberInput';

/** Ganze Feldgruppe prüfen, bevor ein fachlicher Payload oder eine Berechnung entsteht. */
export function validateNumberDrafts<K extends string>(
    drafts: Record<K, string>,
    rules: Record<K, Parameters<typeof validateDecimalInput>[1] & { maxDecimalPlaces?: number }>,
): { valid: true; values: Record<K, number | null> } | { valid: false; message: string; field: K } {
    const values = {} as Record<K, number | null>;
    for (const key of Object.keys(rules) as K[]) {
        const result = validateDecimalInput(drafts[key], rules[key]);
        if (!result.valid) return { ...result, field: key };
        const precision = rules[key].maxDecimalPlaces;
        const fraction = (drafts[key].trim().split(',')[1] ?? '').replace(/0+$/, '');
        if (precision !== undefined && fraction.length > precision) {
            return { valid: false, field: key, message: `${rules[key].label}: Bitte höchstens ${precision} Nachkommastellen eingeben.` };
        }
        values[key] = result.value;
    }
    return { valid: true, values };
}

