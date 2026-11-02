import { AlertTriangle, Clock } from 'lucide-react';
import { formatDatum, heuteIso } from '../../lib/datum';
import { cn } from '../../lib/utils';
import { faelligkeit, faelligkeitText } from './firmendokumenteLogik';

const FARBE = {
    ueberfaellig: 'border-red-200 bg-red-50 text-red-700',
    bald: 'border-amber-200 bg-amber-50 text-amber-800',
    spaeter: 'border-slate-200 bg-white text-slate-700',
} as const;

/**
 * „Fällig am“ mit Farbe: rot = überfällig, gelb = in den nächsten 30 Tagen,
 * sonst neutral. Ohne Datum ein Strich. Der Zustand steht zusätzlich als
 * Text daneben (nicht nur über die Farbe).
 */
export function FaelligAmAnzeige({ faelligAm, heute = heuteIso() }: { faelligAm: string | null; heute?: string }) {
    const stand = faelligkeit(faelligAm, heute);
    if (!stand) return <span className="text-slate-400">–</span>;
    const Symbol = stand === 'ueberfaellig' ? AlertTriangle : Clock;
    return (
        <span
            className={cn('inline-flex items-center gap-1 whitespace-nowrap rounded-md border px-2 py-0.5 text-sm tabular-nums', FARBE[stand])}
            title={faelligkeitText(stand)}
        >
            {stand !== 'spaeter' && <Symbol className="h-3.5 w-3.5" aria-hidden="true" />}
            {formatDatum(faelligAm)}
            {stand !== 'spaeter' && <span className="sr-only">, {faelligkeitText(stand)}</span>}
        </span>
    );
}
