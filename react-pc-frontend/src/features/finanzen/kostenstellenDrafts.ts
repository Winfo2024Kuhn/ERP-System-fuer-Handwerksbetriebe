import { formatDecimalInput } from '../../lib/numberInput';
import { validateNumberDrafts } from '../../lib/numberDrafts';

export interface KostenstellenSplit {
    id?: number | null;
    kostenstelleId: number | null;
    kostenstelleBezeichnung?: string | null;
    kostenstelleIstFixkosten?: boolean | null;
    prozent: number | null;
    absoluterBetrag: number | null;
    berechneterBetrag?: number | null;
    beschreibung?: string | null;
    streckungJahre: number;
    streckungStartJahr: number | null;
    // Client-seitiger Stabil-Key fuer React-Reconciliation. Wird nur beim
    // Hinzufuegen vergeben; persistierte Splits nutzen ihre echte id.
    _clientKey?: string;
    _drafts?: Partial<Record<'prozent' | 'absoluterBetrag' | 'streckungJahre' | 'streckungStartJahr', string>>;
}

export type SplitZahl = 'prozent' | 'absoluterBetrag' | 'streckungJahre' | 'streckungStartJahr';
export const draft = (s: KostenstellenSplit, key: SplitZahl) => s._drafts?.[key] ?? (s[key] == null ? '' : formatDecimalInput(s[key]));

export function validateKostenstellenSplits(splits: KostenstellenSplit[]): { valid: true; splits: KostenstellenSplit[] } | { valid: false; message: string } {
    const values: KostenstellenSplit[] = [];
    for (const [index, s] of splits.entries()) {
        const label = `Split ${index + 1}`;
        const result = validateNumberDrafts({ prozent: draft(s, 'prozent'), absoluterBetrag: draft(s, 'absoluterBetrag'), streckungJahre: draft(s, 'streckungJahre'), streckungStartJahr: draft(s, 'streckungStartJahr') }, {
            prozent: { label: `${label} Anteil`, min: 0, max: 100, integer: true }, absoluterBetrag: { label: `${label} Betrag`, maxDecimalPlaces: 2 },
            streckungJahre: { label: `${label} Streckung`, required: true, integer: true, min: 1, max: 20 },
            streckungStartJahr: { label: `${label} Startjahr`, integer: true, min: 2000, max: 2100 },
        });
        if (!result.valid) return result;
        if (!s.kostenstelleId) return { valid: false, message: `${label}: Bitte eine Kostenstelle wählen.` };
        const v = result.values;
        if ((v.prozent == null) === (v.absoluterBetrag == null)) return { valid: false, message: `${label}: Genau einen Anteil oder absoluten Betrag eingeben.` };
        if (v.streckungJahre! > 1 && v.streckungStartJahr == null) return { valid: false, message: `${label}: Bitte ein Startjahr eingeben.` };
        values.push({ ...s, ...v, streckungJahre: v.streckungJahre!, _drafts: undefined });
    }
    if (values.reduce((sum, s) => sum + (s.prozent ?? 0), 0) > 100) return { valid: false, message: 'Die Summe der Anteile darf nicht über 100 % liegen.' };
    return { valid: true, splits: values };
}

