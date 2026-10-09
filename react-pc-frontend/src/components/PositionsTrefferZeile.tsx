import { ListTree } from 'lucide-react';
import { cn } from '../lib/utils';
import { hervorhebungsStuecke, weitereTrefferText } from '../lib/positionsTreffer';

interface PositionsTrefferZeileProps {
    /** z. B. „Flachstahl 50x5 · S235JR · Charge 123456 · 12 Stück“ */
    trefferText: string;
    weitereTreffer?: number | null;
    /** Was der Nutzer gesucht hat – wird im Text hervorgehoben. */
    suchbegriff: string;
    className?: string;
}

/**
 * Kleine Zeile unter einem Dokument: welche Position die Suche getroffen hat.
 * Der Suchbegriff wird per Text-Zerlegung markiert (kein HTML aus dem Server).
 */
export function PositionsTrefferZeile({ trefferText, weitereTreffer, suchbegriff, className }: PositionsTrefferZeileProps) {
    const stuecke = hervorhebungsStuecke(trefferText, suchbegriff);
    const weitere = weitereTrefferText(weitereTreffer);
    return (
        <p
            className={cn('flex items-start gap-1.5 text-xs text-slate-500 min-w-0', className)}
            data-testid="positions-treffer"
        >
            <ListTree className="w-3.5 h-3.5 mt-px shrink-0 text-slate-400" aria-hidden="true" />
            <span className="min-w-0 break-words">
                <span className="sr-only">Gefunden in Position: </span>
                {stuecke.map((stueck, index) => stueck.treffer ? (
                    <mark key={index} className="rounded-sm bg-rose-100 px-0.5 font-medium text-rose-800">{stueck.text}</mark>
                ) : (
                    <span key={index}>{stueck.text}</span>
                ))}
                {weitere && <span className="text-slate-400"> {weitere}</span>}
            </span>
        </p>
    );
}
