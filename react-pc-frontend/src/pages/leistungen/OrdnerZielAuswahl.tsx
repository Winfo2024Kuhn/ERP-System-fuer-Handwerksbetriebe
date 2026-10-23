import { useId, useMemo, useState } from 'react';
import { Folder, Search } from 'lucide-react';
import { Input } from '../../components/ui/input';
import { cn } from '../../lib/utils';
import { passtZurSuche } from './ordnerLogik';
import type { ZielEintrag } from './typen';

export interface OrdnerZielAuswahlProps {
    ziele: ZielEintrag[];
    /** `undefined` = noch nichts gewählt, `null` = oberste Ebene. */
    wert: number | null | undefined;
    onWahl: (id: number | null) => void;
    beschriftung: string;
}

const schluessel = (id: number | null) => (id === null ? 'oben' : String(id));

/**
 * Auswahl eines Ziel-Ordners als eingerückte Liste. Ziele, die nicht gehen,
 * bleiben sichtbar, sind aber gesperrt und nennen den Grund – so muss
 * niemand raten, warum ein Ordner fehlt. Bedienbar mit Pfeiltasten wie eine
 * normale Optionsgruppe; ab zehn Ordnern hilft ein Suchfeld.
 */
export function OrdnerZielAuswahl({ ziele, wert, onWahl, beschriftung }: OrdnerZielAuswahlProps) {
    const name = useId();
    const [suche, setSuche] = useState('');
    const sichtbar = useMemo(
        () => (suche.trim() ? ziele.filter((z) => passtZurSuche(z.name, suche)) : ziele),
        [ziele, suche],
    );

    return (
        <fieldset className="space-y-2">
            <legend className="text-sm font-medium text-slate-700">{beschriftung}</legend>
            {ziele.length >= 10 && (
                <div className="relative">
                    <Search className="pointer-events-none absolute left-2.5 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" aria-hidden="true" />
                    <Input
                        type="text"
                        value={suche}
                        onChange={(e) => setSuche(e.target.value)}
                        placeholder="Ordner suchen …"
                        aria-label="Ziel-Ordner suchen"
                        className="rounded-lg py-1.5 pl-8 pr-3"
                    />
                </div>
            )}
            <div className="max-h-64 overflow-y-auto rounded-lg border border-slate-200 p-1">
                {sichtbar.length === 0 && <p className="px-3 py-4 text-sm text-slate-500">Kein Ordner passt zu „{suche}“.</p>}
                {sichtbar.map((ziel) => {
                    const gewaehlt = wert !== undefined && schluessel(wert) === schluessel(ziel.id);
                    return (
                        <label
                            key={schluessel(ziel.id)}
                            className={cn(
                                'flex items-start gap-2 rounded-md py-1.5 pr-2 text-sm',
                                ziel.gesperrt ? 'cursor-not-allowed text-slate-400' : 'cursor-pointer text-slate-800 hover:bg-rose-50',
                                gewaehlt && 'bg-rose-50 ring-1 ring-inset ring-rose-300',
                            )}
                            style={{ paddingLeft: 8 + (suche.trim() ? 0 : ziel.ebene * 16) }}
                        >
                            <input
                                type="radio"
                                name={name}
                                value={schluessel(ziel.id)}
                                checked={gewaehlt}
                                disabled={ziel.gesperrt !== null}
                                onChange={() => onWahl(ziel.id)}
                                className="mt-0.5 h-4 w-4 shrink-0 accent-rose-600"
                            />
                            <Folder className={cn('mt-0.5 h-4 w-4 shrink-0', ziel.gesperrt ? 'text-slate-300' : 'text-rose-500')} aria-hidden="true" />
                            <span className="min-w-0">
                                <span className="block">{ziel.name}</span>
                                {ziel.gesperrt && <span className="block text-xs text-slate-500">{ziel.gesperrt}</span>}
                            </span>
                        </label>
                    );
                })}
            </div>
        </fieldset>
    );
}
