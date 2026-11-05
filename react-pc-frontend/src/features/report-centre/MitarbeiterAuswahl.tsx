import { useId } from 'react';
import { Loader2 } from 'lucide-react';
import { Button } from '../../components/ui/button';
import type { BerichtMitarbeiter } from './api';

interface MitarbeiterAuswahlProps {
    mitarbeiter: BerichtMitarbeiter[];
    gewaehlt: number[];
    onChange: (ids: number[]) => void;
    max: number;
    /** „Alle“ ist auch über `max` erlaubt. */
    alleErlaubt?: boolean;
    laedt: boolean;
    fehler: string | null;
}

/**
 * Mehrfachauswahl von Mitarbeitern als Liste mit Häkchen, dazu „Alle“ und
 * „Keine“. Laden, Fehler und leere Liste sehen unterschiedlich aus.
 */
export function MitarbeiterAuswahl({ mitarbeiter, gewaehlt, onChange, max, alleErlaubt, laedt, fehler }: MitarbeiterAuswahlProps) {
    const ueberschriftId = useId();
    const gewaehltSet = new Set(gewaehlt);
    const umschalten = (id: number) => onChange(
        gewaehltSet.has(id) ? gewaehlt.filter(g => g !== id) : mitarbeiter.filter(m => gewaehltSet.has(m.id) || m.id === id).map(m => m.id),
    );

    return (
        <div role="group" className="space-y-2" aria-labelledby={ueberschriftId}>
            <div className="flex flex-wrap items-center justify-between gap-2">
                <p id={ueberschriftId} className="text-sm font-medium text-slate-700">
                    Mitarbeiter <span className="font-normal text-slate-500">({gewaehlt.length} von {mitarbeiter.length} gewählt{max < mitarbeiter.length ? `, höchstens ${max}${alleErlaubt ? ' oder alle' : ''}` : ''})</span>
                </p>
                <div className="flex gap-2">
                    <Button type="button" size="sm" variant="outline" disabled={laedt || mitarbeiter.length === 0}
                        onClick={() => onChange(mitarbeiter.map(m => m.id))}>Alle</Button>
                    <Button type="button" size="sm" variant="outline" disabled={laedt || gewaehlt.length === 0}
                        onClick={() => onChange([])}>Keine</Button>
                </div>
            </div>
            {laedt ? (
                <p className="flex items-center gap-2 rounded-lg border border-slate-200 bg-slate-50 p-3 text-sm text-slate-500">
                    <Loader2 aria-hidden="true" className="h-4 w-4 motion-safe:animate-spin" /> Mitarbeiter werden geladen …
                </p>
            ) : fehler ? (
                <p role="alert" className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-700">{fehler}</p>
            ) : mitarbeiter.length === 0 ? (
                <p className="rounded-lg border border-dashed border-slate-300 p-3 text-sm text-slate-500">
                    Es gibt noch keine Mitarbeiter mit Zeitkonto. Richte zuerst unter „Zeitkonten“ eines ein.
                </p>
            ) : (
                // -m-1 p-1: Platz für den Fokus-Ring der Häkchen im Scrollbereich.
                <div className="-m-1 max-h-56 overflow-y-auto p-1">
                    <ul className="grid grid-cols-1 gap-1 sm:grid-cols-2">
                        {mitarbeiter.map(m => (
                            <li key={m.id}>
                                <label className="flex cursor-pointer items-center gap-2 rounded-md px-2 py-1.5 text-sm text-slate-700 hover:bg-slate-50">
                                    <input
                                        type="checkbox"
                                        checked={gewaehltSet.has(m.id)}
                                        onChange={() => umschalten(m.id)}
                                        className="h-4 w-4 rounded border-slate-300 accent-rose-600 focus:ring-2 focus:ring-rose-500"
                                    />
                                    {m.name}
                                </label>
                            </li>
                        ))}
                    </ul>
                </div>
            )}
        </div>
    );
}
