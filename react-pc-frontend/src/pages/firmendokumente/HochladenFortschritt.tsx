import { Loader2 } from 'lucide-react';
import { Button } from '../../components/ui/button';
import type { StapelStand } from './useStapelHochladen';

/** Leiste „Lade hoch … 12 von 40“ mit Balken und „Abbrechen“, solange mehrere Dateien ohne Dialog hochgehen. */
export function HochladenFortschritt({ stand, onAbbrechen }: { stand: StapelStand; onAbbrechen: () => void }) {
    const anteil = stand.gesamt > 0 ? Math.round((stand.fertig / stand.gesamt) * 100) : 0;
    return (
        <div className="space-y-1.5 rounded-lg border border-slate-200 bg-slate-50 px-3 py-2">
            <div className="flex items-center justify-between gap-3">
                <p className="flex items-center gap-2 text-sm text-slate-700" aria-live="polite">
                    <Loader2 className="h-4 w-4 shrink-0 text-rose-600 motion-safe:animate-spin" aria-hidden="true" />
                    <span className="tabular-nums">Lade hoch … {stand.fertig} von {stand.gesamt}</span>
                </p>
                <Button variant="ghost" size="sm" onClick={onAbbrechen} disabled={stand.abgebrochen}
                    aria-label={stand.abgebrochen ? undefined : 'Hochladen abbrechen'}
                    title={stand.abgebrochen ? undefined : 'Dateien, die noch nicht dran waren, nicht mehr hochladen'}
                    className="h-7 px-2 text-slate-600 hover:text-rose-700">
                    {stand.abgebrochen ? 'Wird abgebrochen …' : 'Abbrechen'}
                </Button>
            </div>
            <div
                role="progressbar"
                aria-label="Dateien werden hochgeladen"
                aria-valuemin={0}
                aria-valuemax={stand.gesamt}
                aria-valuenow={stand.fertig}
                className="h-1.5 overflow-hidden rounded-full bg-slate-200"
            >
                <div className="h-full bg-rose-600" style={{ width: `${anteil}%` }} />
            </div>
        </div>
    );
}
