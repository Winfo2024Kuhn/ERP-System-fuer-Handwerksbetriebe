import { useId } from 'react';
import { X } from 'lucide-react';
import { DatePicker } from '../../components/ui/datepicker';
import { heuteIso } from '../../lib/datum';
import { cn } from '../../lib/utils';
import { tagAnzeige } from './format';

/**
 * Tagesfilter für Anrufe und Anrufbeantworter: der gemeinsame Kalender
 * ({@link DatePicker}) plus ein Knopf zum Zurücksetzen. Tage in der Zukunft
 * sind gesperrt – dort kann noch niemand angerufen haben.
 */

interface TagFilterProps {
    /** ISO-Tag oder leer = alle Tage. */
    tag: string;
    onWechsel: (tag: string) => void;
    disabled?: boolean;
    /** Erklärt, warum der Filter gerade gesperrt ist. */
    gesperrtGrund?: string;
    className?: string;
}

export function TagFilter({ tag, onWechsel, disabled = false, gesperrtGrund, className }: TagFilterProps) {
    const grundId = useId();
    const zeigeGrund = disabled && Boolean(gesperrtGrund);
    // Der sichtbare Text („Alle Tage" bzw. das Datum) steckt mit im Namen (WCAG 2.5.3).
    const beschriftung = `Tag filtern: ${tag && !disabled ? tagAnzeige(tag) : 'Alle Tage'}`;
    return (
        <div className={cn('flex items-center gap-1', className)} title={disabled ? gesperrtGrund : undefined}>
            <DatePicker
                value={disabled ? '' : tag}
                onChange={onWechsel}
                placeholder="Alle Tage"
                aria-label={beschriftung}
                aria-describedby={zeigeGrund ? grundId : undefined}
                max={heuteIso()}
                disabled={disabled}
                className="w-full sm:w-40"
            />
            {zeigeGrund && <span id={grundId} className="sr-only">{gesperrtGrund}</span>}
            {tag && !disabled && (
                <button
                    type="button"
                    onClick={() => onWechsel('')}
                    aria-label="Tagesfilter entfernen"
                    title="Wieder alle Tage zeigen"
                    className="shrink-0 rounded-md p-2 text-slate-400 transition-colors hover:bg-rose-50 hover:text-rose-700 focus:outline-none focus:ring-2 focus:ring-rose-500"
                >
                    <X aria-hidden="true" className="h-4 w-4" />
                </button>
            )}
        </div>
    );
}
