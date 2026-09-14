import { CheckCircle2, Sparkles } from 'lucide-react';
import { Button } from '../ui/button';
import type { BelegVorschlag } from '../../types';

interface VorschlagsChipProps {
    vorschlag: BelegVorschlag | null | undefined;
    hinweis?: string | null;
    aktuelleId: number | null;
    onUebernehmen: (id: number) => void;
    was: 'Konto' | 'Baustelle';
}

const QUELLE: Record<BelegVorschlag['quelle'], string> = {
    KI: 'Die KI schlägt vor',
    HISTORIE: 'Beim letzten Mal bei diesem Lieferanten',
    LIEFERANT_STANDARD: 'Beim Lieferanten hinterlegt',
};

/** Zeigt einen nachvollziehbaren Vorschlag und übernimmt ihn nur auf ausdrücklichen Klick. */
export function VorschlagsChip({ vorschlag, hinweis, aktuelleId, onUebernehmen, was }: VorschlagsChipProps) {
    if (!vorschlag && !hinweis) return null;
    if (!vorschlag) return <div className="rounded-lg border border-slate-200 bg-slate-50 p-3 text-sm text-slate-700">
        <span className="font-medium">Kein Vorschlag:</span> {hinweis}
    </div>;
    const uebernommen = aktuelleId === vorschlag.id;
    return <div className="rounded-lg border border-rose-200 bg-rose-50/60 p-3">
        <div className="flex min-w-0 items-start justify-between gap-3">
            <div className="min-w-0">
                <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-rose-700">
                    <Sparkles className="h-4 w-4 shrink-0" aria-hidden /> {QUELLE[vorschlag.quelle]}
                </div>
                <p className="mt-1 break-words text-sm font-semibold text-slate-900">
                    {vorschlag.nummer ? `${vorschlag.nummer} ` : ''}{vorschlag.bezeichnung}
                </p>
                {vorschlag.begruendung && <p className="mt-1 text-xs leading-relaxed text-slate-600">{vorschlag.begruendung}</p>}
            </div>
            {uebernommen ? <span className="inline-flex shrink-0 items-center gap-1 text-xs font-medium text-emerald-700">
                <CheckCircle2 className="h-4 w-4" aria-hidden /> übernommen
            </span> : <Button type="button" size="sm" variant="outline" className="shrink-0 border-rose-300 text-rose-700 hover:bg-rose-50"
                onClick={() => onUebernehmen(vorschlag.id)}>{was} übernehmen</Button>}
        </div>
    </div>;
}
