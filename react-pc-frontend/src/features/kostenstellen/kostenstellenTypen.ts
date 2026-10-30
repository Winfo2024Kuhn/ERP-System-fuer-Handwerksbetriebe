/** Art einer Kostenstelle, wie sie der Server unter `/api/firma/kostenstellen` liefert. */
export type KostenstellenTyp = 'LAGER' | 'GEMEINKOSTEN' | 'PROJEKT' | 'SONSTIG';

/** Eine Kostenstelle, wie `/api/firma/kostenstellen` (und `/geloescht`) sie liefert. */
export interface Kostenstelle {
    id: number;
    bezeichnung: string;
    typ: KostenstellenTyp;
    beschreibung: string | null;
    istFixkosten: boolean;
    istInvestition: boolean;
    aktiv: boolean;
    sortierung: number | null;
}

export const KOSTENSTELLEN_TYP_OPTIONS: { value: KostenstellenTyp; label: string }[] = [
    { value: 'LAGER', label: 'Lager (Investitionen)' },
    { value: 'GEMEINKOSTEN', label: 'Gemeinkosten (Fixkosten)' },
    { value: 'PROJEKT', label: 'Projekt' },
    { value: 'SONSTIG', label: 'Sonstige' },
];

export const getTypLabel = (typ: string) =>
    KOSTENSTELLEN_TYP_OPTIONS.find(o => o.value === typ)?.label || typ;

export const getTypColor = (typ: string) => {
    switch (typ) {
        case 'GEMEINKOSTEN': return 'bg-rose-100 text-rose-700 border-rose-200';
        case 'LAGER': return 'bg-rose-50 text-rose-600 border-rose-100';
        case 'PROJEKT': return 'bg-slate-100 text-slate-700 border-slate-300';
        default: return 'bg-slate-100 text-slate-600 border-slate-200';
    }
};
