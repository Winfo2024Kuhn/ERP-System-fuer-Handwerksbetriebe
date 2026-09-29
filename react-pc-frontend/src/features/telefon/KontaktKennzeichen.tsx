import { Calculator, Truck, User } from 'lucide-react';
import type { ComponentType } from 'react';
import { cn } from '../../lib/utils';
import { KONTAKTART_TEXT } from './format';
import type { KontaktTyp } from './types';

/**
 * Kleines Schild „Kunde", „Lieferant" oder „Steuerberater" neben einem Namen.
 * Farbe und Symbol unterscheiden die Arten, damit das nicht allein an der
 * Farbe hängt.
 */

const ART: Record<KontaktTyp, { symbol: ComponentType<{ className?: string }>; farbe: string }> = {
    KUNDE: { symbol: User, farbe: 'border-rose-200 bg-rose-50 text-rose-700' },
    LIEFERANT: { symbol: Truck, farbe: 'border-slate-200 bg-slate-100 text-slate-700' },
    STEUERBERATER: { symbol: Calculator, farbe: 'border-slate-300 bg-white text-slate-700' },
};

export function KontaktKennzeichen({ typ, gross = false }: { typ: KontaktTyp; gross?: boolean }) {
    const { symbol: Symbol, farbe } = ART[typ];
    return (
        <span
            className={cn(
                'inline-flex items-center gap-1 rounded-full border font-medium whitespace-nowrap',
                gross ? 'px-3 py-1 text-sm' : 'px-2 py-0.5 text-[11px]',
                farbe,
            )}
        >
            <Symbol aria-hidden="true" className={gross ? 'h-4 w-4' : 'h-3 w-3'} />
            {KONTAKTART_TEXT[typ]}
        </span>
    );
}
