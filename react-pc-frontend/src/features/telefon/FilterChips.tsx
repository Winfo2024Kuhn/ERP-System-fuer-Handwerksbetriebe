import { cn } from '../../lib/utils';

export interface FilterChip<T extends string> {
    wert: T;
    text: string;
}

/**
 * Filter als Chips (nur einer aktiv). Technisch eine Gruppe von
 * Umschaltknöpfen mit `aria-pressed`, damit Screenreader den Zustand
 * vorlesen.
 */
export function FilterChips<T extends string>({ chips, aktiv, onWechsel, beschriftung }: {
    chips: FilterChip<T>[];
    aktiv: T;
    onWechsel: (wert: T) => void;
    beschriftung: string;
}) {
    return (
        <div role="group" aria-label={beschriftung} className="flex flex-wrap items-center gap-2">
            {chips.map((chip) => {
                const gewaehlt = chip.wert === aktiv;
                return (
                    <button
                        key={chip.wert}
                        type="button"
                        aria-pressed={gewaehlt}
                        onClick={() => onWechsel(chip.wert)}
                        className={cn(
                            'rounded-full border px-3.5 py-1.5 text-sm font-medium transition-colors focus:outline-none focus:ring-2 focus:ring-rose-500',
                            gewaehlt
                                ? 'border-rose-300 bg-rose-50 text-rose-700 shadow-sm'
                                : 'border-slate-200 bg-white text-slate-600 hover:border-rose-200 hover:text-rose-700',
                        )}
                    >
                        {chip.text}
                    </button>
                );
            })}
        </div>
    );
}
