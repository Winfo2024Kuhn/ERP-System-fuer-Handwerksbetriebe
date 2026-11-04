/**
 * Einzige Quelle für Anredetexte im PC-Frontend (Dokumenteditor, Steuerberater-
 * Mails). Das Backend setzt für E-Mail-Vorlagen und PDFs dieselbe Regel um.
 *
 * Anrede-Werte kommen als Enum vom Backend: HERR, FRAU, FAMILIE, FIRMA,
 * DAMEN_HERREN (Groß/klein egal). Unbekanntes oder Leeres ergibt immer
 * „Sehr geehrte Damen und Herren“, nie den Rohwert.
 */

const DAMEN_UND_HERREN = 'Sehr geehrte Damen und Herren';

const ANREDE_MIT_NAME: Readonly<Record<string, string>> = {
    HERR: 'Sehr geehrter Herr',
    FRAU: 'Sehr geehrte Frau',
    FAMILIE: 'Sehr geehrte Familie',
};

/**
 * Schon im Namen stehendes „Herr“, „Frau“ oder „Familie“ (Groß/klein egal),
 * gefolgt von Leerraum oder als ganzer Name. „Herrmann“ bleibt stehen.
 */
const ANREDE_IM_NAMEN = /^(?:herr|frau|familie)(?:\s+|$)/i;

function normalisiere(art: string | null | undefined): string {
    return (art ?? '').trim().toUpperCase();
}

/** Anredetext ohne Namen, z. B. „Sehr geehrter Herr“ (Platzhalter {{Anrede}} allein). */
export function anredeText(art: string | null | undefined): string {
    return ANREDE_MIT_NAME[normalisiere(art)] ?? DAMEN_UND_HERREN;
}

/**
 * Briefanrede aus Anrede und Name, ohne Komma: „Sehr geehrter Herr Mustermann“.
 * Nur Herr, Frau und Familie bekommen den Namen, und nur, wenn er nicht leer ist.
 * In allen anderen Fällen „Sehr geehrte Damen und Herren“.
 */
export function briefanrede(art: string | null | undefined, name: string | null | undefined): string {
    const text = ANREDE_MIT_NAME[normalisiere(art)];
    const nachname = (name ?? '').trim().replace(ANREDE_IM_NAMEN, '');
    if (!text || !nachname) return DAMEN_UND_HERREN;
    return `${text} ${nachname}`;
}

/**
 * Auswahlliste „Anrede“ für Kunden- und Ansprechpartner-Formulare. Die
 * Beschriftung ist der Anredetext, der später im Brief steht. Eine leere
 * Erstoption („Bitte wählen“, „(keine)“) setzt das Formular selbst davor.
 */
export const ANREDE_AUSWAHL: ReadonlyArray<{ value: string; label: string }> =
    ['HERR', 'FRAU', 'DAMEN_HERREN', 'FAMILIE'].map(value => ({ value, label: anredeText(value) }));
