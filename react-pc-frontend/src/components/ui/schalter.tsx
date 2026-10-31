import { cn } from '../../lib/utils';

export interface SchalterProps {
    an: boolean;
    onChange: (an: boolean) => void;
    /** Sichtbare Beschriftung rechts vom Schalter – zugleich sein Name. */
    beschriftung: string;
    disabled?: boolean;
    /** Tooltip, z. B. warum der Schalter gerade gesperrt ist. */
    title?: string;
    /** Id eines Erklärtextes, den Screenreader zum Schalter vorlesen. */
    beschreibungId?: string;
    className?: string;
}

/**
 * Kleiner Ein/Aus-Schalter (`role="switch"`), rose wenn an.
 *
 * <p>Ein echter Knopf statt einer versteckten Checkbox: Leertaste und Enter
 * schalten um, der Fokus-Ring ist sichtbar, und Screenreader sagen „Schalter,
 * an/aus“. Speichert eine Stelle sofort beim Umschalten, sperrt sie ihn
 * währenddessen per `disabled` gegen Doppel-Klicks.</p>
 */
export function Schalter({ an, onChange, beschriftung, disabled, title, beschreibungId, className }: SchalterProps) {
    return (
        <button
            type="button"
            role="switch"
            aria-checked={an}
            aria-describedby={beschreibungId}
            disabled={disabled}
            title={title}
            onClick={() => onChange(!an)}
            className={cn(
                'group inline-flex items-center gap-2 rounded-md px-1 py-1 text-sm font-medium text-slate-700 focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-500 disabled:cursor-not-allowed disabled:opacity-60',
                className,
            )}
        >
            <span className={cn('relative inline-flex h-5 w-9 shrink-0 items-center rounded-full transition-colors', an ? 'bg-rose-600' : 'bg-slate-300 group-hover:bg-slate-400')}>
                <span className={cn('inline-block h-4 w-4 rounded-full bg-white shadow-sm transition-transform', an ? 'translate-x-[18px]' : 'translate-x-0.5')} />
            </span>
            {beschriftung}
        </button>
    );
}
