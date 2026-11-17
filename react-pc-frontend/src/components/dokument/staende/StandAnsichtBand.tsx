import { forwardRef } from 'react';
import { Link } from 'react-router-dom';
import { AlertTriangle, ArrowRight, History } from 'lucide-react';
import { aktuellerStandUrl, standAnsichtText } from './standFormat';
import { WiederherstellenKnopf } from './WiederherstellenKnopf';
import type { StandEintrag } from './staendeTypen';

interface Props {
    dokumentId: number;
    stand: StandEintrag | null;
    /** Fehler beim Laden des Stands (z. B. gibt es ihn nicht mehr). */
    fehler: string | null;
    sperrgrund: string | null;
    laeuft: boolean;
    onWiederherstellen: () => void;
}

/**
 * Band über dem Editor, wenn ein festgehaltener Stand angesehen wird: sagt, welcher
 * Stand das ist, und bietet Wiederherstellen und den Weg zum aktuellen Inhalt.
 */
export const StandAnsichtBand = forwardRef<HTMLDivElement, Props>(function StandAnsichtBand(
    { dokumentId, stand, fehler, sperrgrund, laeuft, onWiederherstellen }, ref,
) {
    return (
        <div ref={ref} className="shrink-0 border-b border-amber-200 bg-amber-50 px-4 py-2.5 flex flex-wrap items-center justify-between gap-3">
            <div className="flex min-w-0 flex-1 items-center gap-2 text-sm">
                {fehler ? (
                    <p role="alert" className="flex items-center gap-2 font-medium text-red-700">
                        <AlertTriangle className="w-4 h-4 shrink-0 text-red-600" aria-hidden="true" />
                        {fehler}
                    </p>
                ) : stand ? (
                    <p className="flex items-center gap-2 text-amber-900">
                        <History className="w-4 h-4 shrink-0" aria-hidden="true" />
                        <span>{standAnsichtText(stand)}</span>
                    </p>
                ) : (
                    <p className="text-slate-600" role="status">Stand wird geladen …</p>
                )}
            </div>
            <div className="flex flex-wrap items-center gap-2">
                {stand && (
                    <WiederherstellenKnopf
                        standNummer={stand.nummer}
                        sperrgrund={sperrgrund}
                        laeuft={laeuft}
                        onKlick={onWiederherstellen}
                        text="Diesen Stand wiederherstellen"
                        art="primaer"
                        mitInfo
                    />
                )}
                <Link
                    to={aktuellerStandUrl(dokumentId)}
                    className="inline-flex items-center gap-2 rounded border border-rose-200 bg-white px-3 py-1.5 text-sm text-rose-700 transition-colors hover:bg-rose-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-500 focus-visible:ring-offset-1"
                >
                    Zum aktuellen Stand
                    <ArrowRight className="w-4 h-4" aria-hidden="true" />
                </Link>
            </div>
        </div>
    );
});
