import { Truck, User } from 'lucide-react';
import { cn } from '../../lib/utils';
import type { KontaktTyp } from './types';

/**
 * Kleines Schild „Kunde" bzw. „Lieferant" neben einem Namen. Farbe und
 * Symbol unterscheiden beide, damit das nicht allein an der Farbe hängt.
 */
export function KontaktKennzeichen({ typ, gross = false }: { typ: KontaktTyp; gross?: boolean }) {
    const istKunde = typ === 'KUNDE';
    const Symbol = istKunde ? User : Truck;
    return (
        <span
            className={cn(
                'inline-flex items-center gap-1 rounded-full border font-medium whitespace-nowrap',
                gross ? 'px-3 py-1 text-sm' : 'px-2 py-0.5 text-[11px]',
                istKunde ? 'border-rose-200 bg-rose-50 text-rose-700' : 'border-slate-200 bg-slate-100 text-slate-700',
            )}
        >
            <Symbol aria-hidden="true" className={gross ? 'h-4 w-4' : 'h-3 w-3'} />
            {istKunde ? 'Kunde' : 'Lieferant'}
        </span>
    );
}
