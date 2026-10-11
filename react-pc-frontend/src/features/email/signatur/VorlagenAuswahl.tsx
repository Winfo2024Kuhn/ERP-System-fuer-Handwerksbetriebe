import { useRef } from 'react';
import { Check } from 'lucide-react';
import { cn } from '../../../lib/utils';
import { sichereFarbe } from './signaturSicherheit';
import { VORLAGEN, type VorlagenId } from './signaturTypen';

/**
 * Kachel-Auswahl der Vorlagen. Jede Kachel zeigt eine kleine Skizze des
 * Aufbaus in der gewählten Akzentfarbe. Bedienung wie eine Radiogruppe
 * (Pfeiltasten wechseln die Vorlage).
 */

const linie = (breite: string, extra = '') => <span className={cn('block h-1.5 rounded-full bg-slate-300', breite, extra)} />;

function Skizze({ id, farbe }: { id: VorlagenId; farbe: string }) {
    const akzent = { backgroundColor: farbe };
    switch (id) {
        case 'klassisch-foto':
            return (
                <span className="flex h-full gap-2">
                    <span className="flex w-7 flex-col gap-1">
                        <span className="block h-7 w-7 rounded-md bg-slate-300" />
                        <span className="block h-2 w-7 rounded-sm bg-slate-200" />
                    </span>
                    <span className="flex flex-1 flex-col gap-1">
                        <span className="block h-2 w-3/4 rounded-full" style={akzent} />
                        {linie('w-1/2')}
                        <span className="my-0.5 block h-px w-full bg-slate-200" />
                        {[0, 1, 2].map((i) => (
                            <span key={i} className="flex items-center gap-1">
                                <span className="block h-1.5 w-1.5 rounded-[2px]" style={akzent} />
                                {linie('w-2/3')}
                            </span>
                        ))}
                    </span>
                </span>
            );
        case 'modern-balken':
            return (
                <span className="flex h-full items-center gap-2">
                    <span className="block h-7 w-7 shrink-0 rounded-full bg-slate-300" />
                    <span className="block h-full w-1 shrink-0 rounded-full" style={akzent} />
                    <span className="flex flex-1 flex-col gap-1">
                        {linie('w-3/4', 'bg-slate-400')}
                        <span className="block h-1.5 w-1/2 rounded-full" style={akzent} />
                        {linie('w-2/3')}
                        {linie('w-1/2')}
                    </span>
                </span>
            );
        case 'kompakt':
            return (
                <span className="flex h-full flex-col justify-center gap-1.5">
                    <span className="flex gap-1">
                        <span className="block h-2 w-1/3 rounded-full" style={akzent} />
                        {linie('w-1/2')}
                    </span>
                    {linie('w-full')}
                </span>
            );
        case 'banner':
            return (
                <span className="flex h-full flex-col gap-1">
                    <span className="block h-2 w-1/2 rounded-full" style={akzent} />
                    {linie('w-2/3')}
                    {linie('w-1/2')}
                    <span className="mt-auto block h-3.5 w-full rounded-sm" style={{ backgroundColor: farbe, opacity: 0.85 }} />
                </span>
            );
        case 'schlicht':
        default:
            return (
                <span className="flex h-full flex-col justify-center gap-1">
                    <span className="block h-2 w-1/2 rounded-full" style={akzent} />
                    {linie('w-2/3')}
                    {linie('w-3/5')}
                    {linie('w-1/2')}
                </span>
            );
    }
}

interface VorlagenAuswahlProps {
    wert: VorlagenId;
    akzentfarbe: string;
    onChange: (id: VorlagenId) => void;
}

export function VorlagenAuswahl({ wert, akzentfarbe, onChange }: VorlagenAuswahlProps) {
    const farbe = sichereFarbe(akzentfarbe);
    const refs = useRef<(HTMLButtonElement | null)[]>([]);

    const tastatur = (event: React.KeyboardEvent, index: number) => {
        const schritt = event.key === 'ArrowRight' || event.key === 'ArrowDown' ? 1
            : event.key === 'ArrowLeft' || event.key === 'ArrowUp' ? -1 : 0;
        if (!schritt) return;
        event.preventDefault();
        const ziel = (index + schritt + VORLAGEN.length) % VORLAGEN.length;
        onChange(VORLAGEN[ziel].id);
        refs.current[ziel]?.focus();
    };

    return (
        <div role="radiogroup" aria-label="Vorlage" className="grid grid-cols-2 gap-3 sm:grid-cols-3">
            {VORLAGEN.map((vorlage, index) => {
                const aktiv = vorlage.id === wert;
                return (
                    <button
                        key={vorlage.id}
                        ref={(el) => { refs.current[index] = el; }}
                        type="button"
                        role="radio"
                        aria-checked={aktiv}
                        tabIndex={aktiv ? 0 : -1}
                        onClick={() => onChange(vorlage.id)}
                        onKeyDown={(e) => tastatur(e, index)}
                        className={cn(
                            'group relative flex flex-col gap-2 rounded-lg border p-3 text-left transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-500 focus-visible:ring-offset-1',
                            aktiv ? 'border-rose-400 bg-rose-50/60 ring-1 ring-rose-300' : 'border-slate-200 bg-white hover:border-rose-200 hover:bg-rose-50/30',
                        )}
                    >
                        <span className="block h-14 rounded-md border border-slate-100 bg-white p-2 shadow-sm" aria-hidden="true">
                            <Skizze id={vorlage.id} farbe={farbe} />
                        </span>
                        <span className="block text-sm font-semibold leading-tight text-slate-900">{vorlage.titel}</span>
                        <span className="block text-xs leading-snug text-slate-500">{vorlage.beschreibung}</span>
                        {aktiv && (
                            <span className="absolute right-2 top-2 flex h-5 w-5 items-center justify-center rounded-full bg-rose-600 text-white" aria-hidden="true">
                                <Check className="h-3 w-3" />
                            </span>
                        )}
                    </button>
                );
            })}
        </div>
    );
}
