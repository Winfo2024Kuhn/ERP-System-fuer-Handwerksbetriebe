import { cn } from '../../lib/utils';
import { ordnerSchildFuer } from './emailOrdner';

/**
 * Kleines Schild an einem Suchtreffer aus „Alle Ordner“: in welchem Ordner die Mail liegt.
 * Papierkorb und Spam sind dezent warnend (Farbe zusätzlich zu Icon und Text).
 */
export function EmailOrdnerChip({ folder }: { folder?: string | null }) {
    const schild = ordnerSchildFuer(folder);
    if (!schild) return null;
    const Icon = schild.icon;
    return (
        <span
            data-testid="email-ordner-chip"
            title={`Liegt in: ${schild.label}`}
            className={cn('inline-flex shrink-0 items-center gap-1 rounded border px-1.5 py-px text-[11px] font-medium leading-4',
                schild.warnend ? 'border-amber-200 bg-amber-50 text-amber-800' : 'border-slate-200 bg-slate-50 text-slate-600')}
        >
            <Icon aria-hidden="true" className="h-3 w-3 shrink-0" />
            <span className="sr-only">Liegt in:</span> {schild.label}
        </span>
    );
}
