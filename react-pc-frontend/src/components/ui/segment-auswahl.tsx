import { useRef, type KeyboardEvent } from 'react';
import { cn } from '../../lib/utils';

export interface SegmentOption {
    wert: string;
    text: string;
    /** Kleiner Zusatz unter dem Text, z. B. „kein Schlüssel“. */
    zusatz?: string;
    /** Gesperrt mit Grund (Tooltip und Screenreader-Beschreibung). */
    gesperrt?: string | null;
}

interface SegmentAuswahlProps {
    optionen: SegmentOption[];
    wert: string;
    onChange: (wert: string) => void;
    /** Name der Gruppe für Screenreader. */
    beschriftung: string;
    className?: string;
}

/**
 * Auswahl aus wenigen Werten (bis etwa vier) als nebeneinanderliegende
 * Segmente – statt eines Klapp-Menüs, damit alle Möglichkeiten auf einen
 * Blick zu sehen sind.
 *
 * <p>Radio-Gruppe nach WAI-ARIA: Tab erreicht nur das gewählte Segment, die
 * Pfeiltasten wandern und wählen (gesperrte werden übersprungen). Gesperrte
 * Segmente bleiben sichtbar und sagen per Tooltip, warum.</p>
 */
export function SegmentAuswahl({ optionen, wert, onChange, beschriftung, className }: SegmentAuswahlProps) {
    const refs = useRef<(HTMLButtonElement | null)[]>([]);
    const gewaehlt = optionen.findIndex((o) => o.wert === wert);
    const fokus = gewaehlt >= 0 ? gewaehlt : optionen.findIndex((o) => !o.gesperrt);

    const taste = (index: number) => (event: KeyboardEvent<HTMLButtonElement>) => {
        const schritt = event.key === 'ArrowRight' || event.key === 'ArrowDown' ? 1
            : event.key === 'ArrowLeft' || event.key === 'ArrowUp' ? -1 : 0;
        if (!schritt) return;
        event.preventDefault();
        for (let i = 1; i <= optionen.length; i += 1) {
            const ziel = (index + schritt * i + optionen.length * i) % optionen.length;
            if (!optionen[ziel].gesperrt) {
                refs.current[ziel]?.focus();
                onChange(optionen[ziel].wert);
                return;
            }
        }
    };

    return (
        <div role="radiogroup" aria-label={beschriftung} className={cn('inline-flex max-w-full flex-wrap gap-1 rounded-lg border border-slate-200 bg-white p-1', className)}>
            {optionen.map((o, index) => {
                const aktiv = o.wert === wert;
                return (
                    <button
                        key={o.wert}
                        ref={(el) => { refs.current[index] = el; }}
                        type="button"
                        role="radio"
                        aria-checked={aktiv}
                        aria-disabled={o.gesperrt ? true : undefined}
                        title={o.gesperrt ?? undefined}
                        tabIndex={index === fokus ? 0 : -1}
                        onClick={() => { if (!o.gesperrt) onChange(o.wert); }}
                        onKeyDown={taste(index)}
                        className={cn(
                            'flex min-w-0 flex-col items-start rounded-md px-3 py-1.5 text-left text-sm transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-500',
                            aktiv ? 'bg-rose-50 font-semibold text-rose-700 ring-1 ring-rose-200' : 'text-slate-700 hover:bg-slate-50',
                            o.gesperrt && 'cursor-not-allowed text-slate-400 hover:bg-transparent',
                        )}
                    >
                        <span>{o.text}</span>
                        {o.zusatz && <span className="text-xs font-normal text-slate-500">{o.zusatz}</span>}
                    </button>
                );
            })}
        </div>
    );
}
