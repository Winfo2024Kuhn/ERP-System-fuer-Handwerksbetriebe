import { validateNumberDrafts } from '../../lib/numberDrafts';

/** Geldbetrag in den Kassenaktionen: Centgenau prüfen, niemals still runden. */
export function validateMoneyDraft(draft: string): { valid: true; value: number } | { valid: false; message: string } {
    const result = validateNumberDrafts({ betrag: draft }, { betrag: { label: 'Betrag', required: true, min: 0, maxDecimalPlaces: 2 } });
    return result.valid ? { valid: true, value: result.values.betrag! } : result;
}
