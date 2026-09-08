import { cn } from '../../lib/utils';
import { PHASEN_BADGE, PHASEN_LABEL, formatDatum, type Phase } from './phasen';

interface PhasenZeitleisteProps {
    phasen: Phase[];
    heute?: string;
}

function istLaufend(phase: Phase, heute: string): boolean {
    return phase.vonDatum <= heute && (phase.bisDatum === null || heute <= phase.bisDatum);
}

function zeitraumText(phase: Phase): string {
    return phase.bisDatum === null
        ? `ab ${formatDatum(phase.vonDatum)}`
        : `${formatDatum(phase.vonDatum)} – ${formatDatum(phase.bisDatum)}`;
}

/**
 * Zeitliche Abfolge der Phasen einer Langzeitkrankmeldung (Lohnfortzahlung,
 * Krankengeld, Wiedereingliederung) als senkrechte Liste. Reine
 * Präsentationskomponente — bekommt ihre Daten per Props.
 */
export function PhasenZeitleiste({ phasen, heute }: PhasenZeitleisteProps) {
    const heuteIso = heute ?? new Date().toISOString().slice(0, 10);

    if (phasen.length === 0) {
        return (
            <p className="text-sm text-slate-500">
                Für diese Krankmeldung sind noch keine Phasen hinterlegt.
            </p>
        );
    }

    return (
        <ul className="flex flex-col gap-3">
            {phasen.map((phase) => {
                const laeuft = istLaufend(phase, heuteIso);
                return (
                    <li
                        key={phase.id}
                        data-testid={`phase-${phase.id}`}
                        className={cn(
                            'flex items-start gap-3 rounded-lg p-2',
                            laeuft && 'ring-2 ring-rose-400',
                        )}
                    >
                        <span className="mt-1.5 h-2 w-2 shrink-0 rounded-full bg-rose-600" aria-hidden="true" />
                        <div className="min-w-0 flex-1">
                            <div className="flex min-w-0 flex-wrap items-center gap-2">
                                <span
                                    className={cn(
                                        'inline-flex items-center rounded px-2 py-0.5 text-xs font-medium',
                                        PHASEN_BADGE[phase.typ],
                                    )}
                                >
                                    {PHASEN_LABEL[phase.typ]}
                                </span>
                                {laeuft && (
                                    <span className="text-xs font-medium text-rose-600">Läuft gerade</span>
                                )}
                            </div>
                            <p className="mt-1 min-w-0 text-sm text-slate-600">{zeitraumText(phase)}</p>
                            {phase.typ === 'WIEDEREINGLIEDERUNG' && phase.stundenProTag != null && (
                                <p className="min-w-0 text-sm text-slate-500">{`${phase.stundenProTag} Std. pro Tag`}</p>
                            )}
                        </div>
                    </li>
                );
            })}
        </ul>
    );
}
