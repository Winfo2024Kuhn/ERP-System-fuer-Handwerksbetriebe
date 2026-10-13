import { Maximize, ZoomIn, ZoomOut } from 'lucide-react';
import { cn } from '../../lib/utils';
import { MAX_ZOOM, MIN_ZOOM, type SeitenZoom } from './useSeitenZoom';

const knopf = 'inline-flex h-8 w-8 items-center justify-center rounded-md text-slate-600 transition-colors hover:bg-slate-100 hover:text-slate-900 focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-500 disabled:cursor-not-allowed disabled:opacity-40';

/** −  70 %  +  [Einpassen] – gleich in Editor und Vorschau. */
export function ZoomRegler({ zoom, eingepasst, vergroessern, verkleinern, einpassen }: SeitenZoom) {
    return (
        <div className="flex items-center gap-0.5 rounded-lg border border-slate-200 bg-white p-0.5">
            <button type="button" className={knopf} onClick={verkleinern} disabled={zoom <= MIN_ZOOM} aria-label="Verkleinern" title="Verkleinern">
                <ZoomOut aria-hidden="true" className="h-4 w-4" />
            </button>
            <span className="min-w-[3.25rem] text-center text-xs font-medium tabular-nums text-slate-600" aria-live="polite">
                {Math.round(zoom * 100)} %
            </span>
            <button type="button" className={knopf} onClick={vergroessern} disabled={zoom >= MAX_ZOOM} aria-label="Vergrößern" title="Vergrößern">
                <ZoomIn aria-hidden="true" className="h-4 w-4" />
            </button>
            <button
                type="button"
                onClick={einpassen}
                aria-pressed={eingepasst}
                title="Ganze Seite zeigen"
                className={cn(knopf, 'w-auto gap-1.5 px-2 text-xs font-medium', eingepasst && 'bg-rose-50 text-rose-700 hover:bg-rose-50 hover:text-rose-700')}
            >
                <Maximize aria-hidden="true" className="h-3.5 w-3.5" />Einpassen
            </button>
        </div>
    );
}
