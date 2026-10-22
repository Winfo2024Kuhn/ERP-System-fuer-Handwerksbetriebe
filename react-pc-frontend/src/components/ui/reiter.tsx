import { useRef, type HTMLAttributes, type KeyboardEvent, type ReactNode } from 'react';
import { Check } from 'lucide-react';
import { cn } from '../../lib/utils';

/**
 * Kleines Zeichen am Reiter:
 * - `haken`: grünes Häkchen, z. B. „hier ist schon etwas ausgefüllt“.
 * - `punkt`: bernsteinfarbener Punkt, z. B. „hier ist noch etwas nicht gespeichert“.
 * Der Text steht für Screenreader und als Tooltip daneben – die Farbe allein trägt die Aussage nicht.
 */
export interface ReiterKennzeichen {
    art: 'haken' | 'punkt';
    text: string;
}

export interface ReiterEintrag<T extends string = string> {
    id: T;
    label: string;
    icon?: ReactNode;
    /** Leiser Zusatz hinter der Beschriftung, z. B. „optional“. */
    zusatz?: string;
    /** Zahl in einem kleinen Kästchen, z. B. die Anzahl der Lieferanten. */
    anzahl?: number;
    kennzeichen?: ReiterKennzeichen;
}

/** IDs von Reiter-Knopf und zugehörigem Bereich – beide verweisen per ARIA aufeinander. */
function reiterIds(idPrefix: string, id: string) {
    return { reiter: `${idPrefix}-reiter-${id}`, panel: `${idPrefix}-panel-${id}` };
}

interface ReiterProps<T extends string> {
    reiter: ReiterEintrag<T>[];
    aktiv: T;
    onWechsel: (id: T) => void;
    /** Worum es in der Leiste geht, z. B. „Bereiche des Artikels“. */
    ariaLabel: string;
    /** Eindeutiger Vorsatz für die IDs, falls mehrere Leisten auf einer Seite stehen. */
    idPrefix: string;
    className?: string;
}

/**
 * Reiter-Leiste wie in den Einstellungen: unterstrichener aktiver Reiter,
 * Pfeiltasten links/rechts sowie Pos1/Ende wandern durch die Reiter, und nur
 * der aktive Reiter liegt in der Tab-Reihenfolge (die übrigen erreicht man
 * per Pfeiltaste). Den Inhalt zeigt {@link ReiterPanel}.
 */
export function Reiter<T extends string>({ reiter, aktiv, onWechsel, ariaLabel, idPrefix, className }: ReiterProps<T>) {
    const knoepfe = useRef<(HTMLButtonElement | null)[]>([]);

    const beiTaste = (event: KeyboardEvent<HTMLButtonElement>, index: number) => {
        let ziel: number;
        if (event.key === 'ArrowRight') ziel = (index + 1) % reiter.length;
        else if (event.key === 'ArrowLeft') ziel = (index - 1 + reiter.length) % reiter.length;
        else if (event.key === 'Home') ziel = 0;
        else if (event.key === 'End') ziel = reiter.length - 1;
        else return;
        event.preventDefault();
        onWechsel(reiter[ziel].id);
        knoepfe.current[ziel]?.focus();
    };

    return (
        <div role="tablist" aria-label={ariaLabel}
            className={cn('flex flex-wrap items-center gap-1 border-b border-slate-200', className)}>
            {reiter.map((r, index) => {
                const istAktiv = r.id === aktiv;
                const ids = reiterIds(idPrefix, r.id);
                return (
                    <button
                        key={r.id}
                        ref={(el) => { knoepfe.current[index] = el; }}
                        type="button"
                        role="tab"
                        id={ids.reiter}
                        aria-selected={istAktiv}
                        aria-controls={ids.panel}
                        tabIndex={istAktiv ? 0 : -1}
                        onClick={() => onWechsel(r.id)}
                        onKeyDown={(e) => beiTaste(e, index)}
                        className={cn(
                            'flex items-center gap-2 px-4 py-3 text-sm font-medium border-b-2 transition-colors -mb-px',
                            'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-rose-500 rounded-t-md',
                            istAktiv
                                ? 'border-rose-600 text-rose-700'
                                : 'border-transparent text-slate-500 hover:text-slate-700 hover:border-slate-200',
                        )}
                    >
                        {r.icon}
                        {r.label}
                        {r.zusatz && <span className="text-xs font-normal text-slate-400">{r.zusatz}</span>}
                        {r.anzahl !== undefined && (
                            <span className={cn(
                                'min-w-5 rounded-full px-1.5 text-xs tabular-nums',
                                istAktiv ? 'bg-rose-100 text-rose-700' : 'bg-slate-100 text-slate-600',
                            )}>
                                {r.anzahl}
                            </span>
                        )}
                        {r.kennzeichen?.art === 'haken' && (
                            <span title={r.kennzeichen.text}
                                className="inline-flex items-center justify-center w-4 h-4 rounded-full bg-emerald-100 text-emerald-700">
                                <Check className="w-3 h-3" aria-hidden="true" />
                                <span className="sr-only">{r.kennzeichen.text}</span>
                            </span>
                        )}
                        {r.kennzeichen?.art === 'punkt' && (
                            <span title={r.kennzeichen.text} className="inline-flex items-center">
                                <span className="w-2 h-2 rounded-full bg-amber-500" aria-hidden="true" />
                                <span className="sr-only">{r.kennzeichen.text}</span>
                            </span>
                        )}
                    </button>
                );
            })}
        </div>
    );
}

interface ReiterPanelProps extends HTMLAttributes<HTMLDivElement> {
    idPrefix: string;
    id: string;
}

/**
 * Inhalt eines Reiters. `hidden` lässt einen Bereich gebaut, aber unsichtbar –
 * etwa damit eine angefangene Eingabe einen Reiterwechsel übersteht.
 */
export function ReiterPanel({ idPrefix, id, children, ...props }: ReiterPanelProps) {
    const ids = reiterIds(idPrefix, id);
    return (
        <div role="tabpanel" id={ids.panel} aria-labelledby={ids.reiter} {...props}>
            {children}
        </div>
    );
}
