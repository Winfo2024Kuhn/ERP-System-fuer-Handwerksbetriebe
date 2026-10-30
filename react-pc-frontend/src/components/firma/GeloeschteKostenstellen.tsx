import { useId, useState } from 'react';
import { ChevronDown, ChevronRight, RefreshCw, RotateCcw } from 'lucide-react';
import { Button } from '../ui/button';
import { Card } from '../ui/card';
import { InfoAbschnitt, InfoButton } from '../ui/info-button';
import { cn } from '../../lib/utils';
import { getTypColor, getTypLabel, type Kostenstelle } from '../../features/kostenstellen/kostenstellenTypen';

interface GeloeschteKostenstellenProps {
    kostenstellen: Kostenstelle[];
    /** Id der Kostenstelle, die gerade zurückgeholt wird – deren Knopf ist gesperrt und dreht. */
    wiederherstellendeId: number | null;
    onWiederherstellen: (kostenstelle: Kostenstelle) => void;
}

/**
 * Aufklappbarer Bereich unter den Kostenstellen-Kacheln: zeigt ausgeblendete (gelöschte)
 * Kostenstellen und holt sie per Knopf zurück. Bei leerer Liste erscheint nichts.
 */
export function GeloeschteKostenstellen({ kostenstellen, wiederherstellendeId, onWiederherstellen }: GeloeschteKostenstellenProps) {
    const [offen, setOffen] = useState(false);
    const inhaltId = useId();
    if (kostenstellen.length === 0) return null;

    const Pfeil = offen ? ChevronDown : ChevronRight;
    return (
        <Card className="p-4 border-0 shadow-sm rounded-xl" data-testid="geloeschte-kostenstellen">
            <div className="flex items-center gap-1">
                <button
                    type="button"
                    onClick={() => setOffen(o => !o)}
                    aria-expanded={offen}
                    aria-controls={inhaltId}
                    className="flex items-center gap-2 rounded-lg px-1 py-1 text-left hover:bg-slate-50 focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-500"
                >
                    <Pfeil className="w-4 h-4 text-slate-500" aria-hidden="true" />
                    <span className="font-semibold text-slate-900">Gelöschte Kostenstellen</span>
                    <span className="rounded-full bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-600">
                        {kostenstellen.length}
                    </span>
                </button>
                <InfoButton titel="Gelöschte Kostenstellen">
                    <InfoAbschnitt titel="Was passiert im Hintergrund?">
                        <p>
                            Der Papierkorb löscht eine Kostenstelle nicht endgültig, er blendet sie nur aus. Sie verschwindet
                            aus allen Auswahllisten und aus der Übersicht.
                        </p>
                    </InfoAbschnitt>
                    <InfoAbschnitt titel="Was bewirkt das?">
                        <p>
                            Bisherige Buchungen bleiben ihr zugeordnet. Mit ‚Wiederherstellen‘ ist sie sofort wieder überall
                            da – samt alten Buchungen. Legst du eine neue Kostenstelle mit dem Namen einer gelöschten an, wird
                            automatisch die alte zurückgeholt.
                        </p>
                    </InfoAbschnitt>
                </InfoButton>
            </div>

            {offen && (
                <ul id={inhaltId} className="mt-3 divide-y divide-slate-100 border-t border-slate-100">
                    {kostenstellen.map(ks => {
                        const laeuft = wiederherstellendeId === ks.id;
                        return (
                            <li key={ks.id} className="flex flex-wrap items-center justify-between gap-3 py-3">
                                <div className="min-w-0">
                                    <div className="flex flex-wrap items-center gap-2">
                                        <span className="font-medium text-slate-900">{ks.bezeichnung}</span>
                                        <span className={cn('inline-block px-2 py-0.5 text-xs rounded border', getTypColor(ks.typ))}>
                                            {getTypLabel(ks.typ)}
                                        </span>
                                    </div>
                                    {ks.beschreibung && <p className="text-sm text-slate-500 mt-0.5">{ks.beschreibung}</p>}
                                </div>
                                <Button
                                    variant="outline"
                                    size="sm"
                                    disabled={wiederherstellendeId !== null}
                                    onClick={() => onWiederherstellen(ks)}
                                    aria-label={`${ks.bezeichnung} wiederherstellen`}
                                    className="border-rose-300 text-rose-700 hover:bg-rose-50"
                                >
                                    {laeuft
                                        ? <RefreshCw className="w-4 h-4 mr-1 motion-safe:animate-spin" />
                                        : <RotateCcw className="w-4 h-4 mr-1" />}
                                    Wiederherstellen
                                </Button>
                            </li>
                        );
                    })}
                </ul>
            )}
        </Card>
    );
}
