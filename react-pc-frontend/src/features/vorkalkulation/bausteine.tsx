/**
 * Kleine, nur hier verwendete Bausteine der Vor-Kalkulation.
 *
 * Sie liegen bewusst im Feature-Ordner und nicht unter `components/ui/`:
 * Es sind Zusammensetzungen aus den vorhandenen Atomen (`Input`, `Label`),
 * keine neuen Atome. Sollte einer davon spaeter woanders gebraucht werden,
 * wandert er nach Ruecksprache nach `components/ui/`.
 */
import type { ReactNode } from 'react';
import { cn } from '../../lib/utils';
import { Input } from '../../components/ui/input';

/**
 * Segment-Umschalter fuer Entscheidungen mit genau zwei bis drei Moeglichkeiten.
 *
 * Gleiche Bauart wie der Lager/Bestellen-Schalter im ProjektEditor: Ein
 * aufklappendes Menue waere ein Klick mehr fuer dieselbe Entscheidung, und in
 * einer Liste mit zwanzig Zeilen faellt das ins Gewicht.
 */
export function Umschalter<T extends string>({
    wert,
    optionen,
    onChange,
    ariaLabel,
    disabled,
    klein,
}: {
    wert: T;
    optionen: { wert: T; text: string }[];
    onChange: (wert: T) => void;
    ariaLabel: string;
    disabled?: boolean;
    klein?: boolean;
}) {
    return (
        <div
            role="group"
            aria-label={ariaLabel}
            className={cn(
                'inline-flex rounded-lg border border-slate-300 overflow-hidden',
                disabled && 'opacity-50',
            )}
        >
            {optionen.map((option) => (
                <button
                    key={option.wert}
                    type="button"
                    disabled={disabled}
                    aria-pressed={wert === option.wert}
                    onClick={() => onChange(option.wert)}
                    className={cn(
                        'transition-colors whitespace-nowrap',
                        klein ? 'px-2.5 py-1 text-[11px]' : 'px-3 py-1.5 min-h-[32px] text-xs',
                        disabled && 'cursor-not-allowed',
                        // Auch gesperrt muss sichtbar bleiben, WAS gewaehlt ist —
                        // sonst zeigt eine schreibgeschuetzte Kalkulation die
                        // Einheit und die Warenart nur noch dem Screenreader.
                        wert === option.wert
                            ? (disabled ? 'bg-rose-200 text-rose-900' : 'bg-rose-600 text-white')
                            : (disabled ? 'text-slate-400' : 'text-slate-700 hover:bg-slate-50'),
                    )}
                >
                    {option.text}
                </button>
            ))}
        </div>
    );
}

/**
 * Ein-/Ausschalter mit Beschriftung, z. B. "verzinken".
 *
 * Eingeschaltet ist er rose gefuellt, ausgeschaltet ein schlichter Rahmen —
 * damit auf einen Blick erkennbar ist, welche Zeilen zur Verzinkerei gehen.
 */
export function Schalter({
    an,
    onChange,
    children,
    disabled,
    titel,
}: {
    an: boolean;
    onChange: (an: boolean) => void;
    children: ReactNode;
    disabled?: boolean;
    titel?: string;
}) {
    return (
        <button
            type="button"
            role="switch"
            aria-checked={an}
            disabled={disabled}
            title={titel}
            onClick={() => onChange(!an)}
            className={cn(
                'inline-flex items-center gap-1.5 rounded-lg border px-2.5 py-1 text-[11px] font-medium transition-colors',
                'focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-500/40',
                disabled && 'opacity-50 cursor-not-allowed',
                an
                    ? 'border-rose-600 bg-rose-600 text-white'
                    : 'border-slate-300 bg-white text-slate-600 hover:bg-slate-50',
            )}
        >
            <span
                aria-hidden="true"
                className={cn(
                    'h-1.5 w-1.5 rounded-full',
                    an ? 'bg-white' : 'bg-slate-300',
                )}
            />
            {children}
        </button>
    );
}

/**
 * Kompaktes Zahlenfeld mit Beschriftung darueber und Einheit dahinter.
 *
 * Warum nicht `components/ui/decimal-input.tsx`: `DecimalInput` blendet bei
 * ungueltiger Eingabe einen Fehlertext UNTER dem Feld ein. In einer Liste mit
 * zwanzig Zeilen springt dadurch das ganze Layout, waehrend der Bediener noch
 * tippt — und eine halb getippte "12," ist hier der Normalfall, kein Fehler.
 * Deshalb meldet die Vor-Kalkulation gesammelt beim Uebernehmen
 * (`pruefeEntwuerfe` + Toast) statt bei jedem Tastendruck an der Zeile.
 *
 * Die Null-beim-Fokus-Regel ist dieselbe wie in `DecimalInput` (bewusst
 * gleiches Verhalten, siehe FRONTEND_UI.md); die Pruefung selbst laeuft ueber
 * die gemeinsamen Helfer in `lib/numberDrafts.ts`, nicht ueber eine Kopie.
 */
export function Zahlenfeld({
    label,
    wert,
    onChange,
    einheit,
    breite = 'w-24',
    ariaLabel,
    platzhalter,
    disabled,
    hinweis,
}: {
    label?: string;
    wert: string;
    onChange: (entwurf: string) => void;
    einheit?: string;
    breite?: string;
    ariaLabel: string;
    platzhalter?: string;
    disabled?: boolean;
    hinweis?: string;
}) {
    return (
        <div>
            {label && (
                <label className="mb-0.5 block text-[9px] font-semibold uppercase tracking-wider text-slate-400">
                    {label}
                </label>
            )}
            <div className="relative">
                <Input
                    type="text"
                    inputMode="decimal"
                    value={wert}
                    aria-label={ariaLabel}
                    placeholder={platzhalter}
                    disabled={disabled}
                    title={hinweis}
                    onFocus={(event) => {
                        // Eine angezeigte 0 verschwindet beim Hineinklicken, andere
                        // Werte bleiben stehen (Vorgabe aus FRONTEND_UI.md).
                        if (!disabled && /^[+-]?0+(?:,0+)?$/.test(event.target.value.trim())) onChange('');
                    }}
                    onChange={(event) => onChange(event.target.value)}
                    className={cn(
                        breite,
                        'py-1.5 text-right text-sm tabular-nums disabled:bg-slate-100 disabled:text-slate-400',
                        einheit && (einheit.length <= 1 ? 'pr-7' : einheit.length <= 2 ? 'pr-9' : 'pr-12'),
                    )}
                />
                {einheit && (
                    <span className="pointer-events-none absolute right-2 top-1/2 -translate-y-1/2 text-xs text-slate-400">
                        {einheit}
                    </span>
                )}
            </div>
        </div>
    );
}

/** Kennzahl-Kachel fuer die Leiste unter einem Reiter. */
export function Kennzahl({
    titel,
    wert,
    zusatz,
    betont,
}: {
    titel: string;
    wert: string;
    zusatz?: string;
    betont?: boolean;
}) {
    return (
        <div
            className={cn(
                'rounded-lg border px-3 py-2',
                betont ? 'border-rose-200 bg-rose-50' : 'border-slate-200 bg-white',
            )}
        >
            <p className="text-[10px] font-semibold uppercase tracking-wider text-slate-500">{titel}</p>
            <p
                className={cn(
                    'tabular-nums font-bold',
                    betont ? 'text-base text-rose-700' : 'text-sm text-slate-900',
                )}
            >
                {wert}
            </p>
            {zusatz && <p className="text-[11px] text-slate-500">{zusatz}</p>}
        </div>
    );
}
