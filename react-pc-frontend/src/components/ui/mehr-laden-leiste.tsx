import { Loader2 } from 'lucide-react';
import { Button } from './button';
import { cn } from '../../lib/utils';

interface MehrLadenLeisteProps {
    /** So viele Einträge stehen schon in der Liste. */
    angezeigt: number;
    /** So viele gibt es laut Server insgesamt (zu Suche und Filter passend). */
    gesamt: number;
    laedtMehr: boolean;
    onMehrLaden: () => void;
    /** Gibt es mehr? Standard: `angezeigt < gesamt`. */
    hatMehr?: boolean;
    /** Zähler links, Standard „25 von 140 angezeigt“. */
    zaehlerText?: string;
    /** Beschriftung des Knopfs, Standard „Mehr laden“. */
    knopfText?: string;
    className?: string;
}

/**
 * Fußzeile seitenweise geladener Listen: links der Zähler („25 von 140 angezeigt“),
 * rechts „Mehr laden“, solange es mehr gibt. Beim Nachladen ist der Knopf gesperrt und
 * dreht einen Spinner; die Liste darüber bleibt stehen.
 */
export function MehrLadenLeiste({
    angezeigt, gesamt, laedtMehr, onMehrLaden, hatMehr = angezeigt < gesamt,
    zaehlerText = `${angezeigt} von ${gesamt} angezeigt`, knopfText = 'Mehr laden', className,
}: MehrLadenLeisteProps) {
    return (
        <div className={cn('flex flex-wrap items-center justify-between gap-3 text-sm text-slate-500', className)}>
            <span className="tabular-nums" aria-live="polite">{zaehlerText}</span>
            {hatMehr && (
                <Button variant="outline" size="sm" onClick={onMehrLaden} disabled={laedtMehr} aria-busy={laedtMehr}>
                    {laedtMehr && <Loader2 className="h-4 w-4 motion-safe:animate-spin" aria-hidden="true" />}
                    {knopfText}
                </Button>
            )}
        </div>
    );
}
