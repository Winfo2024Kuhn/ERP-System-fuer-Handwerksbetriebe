import { Check, Loader2, ScanLine } from 'lucide-react'
import type { AusloeserPhase } from '../../services/scanAusloeser'
import type { ErkennungsStand } from './useLiveErkennung'
import { cn } from '../../lib/utils'

interface LiveStatusPilleProps {
    erkennung: ErkennungsStand
    phase: AusloeserPhase
    /** Anzahl bereits gescannter Seiten */
    anzahlSeiten: number
}

/**
 * Kurzer Hinweis oben im Kamerabild, was der Scanner gerade tut.
 * aria-live="polite": Die Aufnahme selbst ist so kurz, dass „assertive“
 * nur andere Ansagen abschneiden würde.
 */
export default function LiveStatusPille({ erkennung, phase, anzahlSeiten }: LiveStatusPilleProps) {
    let inhalt: { text: string; symbol: 'laedt' | 'suchen' | 'fertig'; hervorheben?: boolean }
    if (erkennung === 'laedt') inhalt = { text: 'Automatik lädt…', symbol: 'laedt' }
    else if (erkennung === 'fehlt') inhalt = { text: 'Blatt ins Bild halten und auslösen', symbol: 'suchen' }
    else if (phase === 'gesperrt') inhalt = { text: `Seite ${anzahlSeiten} erfasst – nächstes Blatt hinlegen`, symbol: 'fertig' }
    else if (phase === 'halten' || phase === 'ausloesen') inhalt = { text: 'Ruhig halten…', symbol: 'suchen', hervorheben: true }
    else inhalt = { text: 'Blatt ins Bild halten', symbol: 'suchen' }

    return (
        <div
            className="pointer-events-none absolute left-1/2 top-3 z-20 -translate-x-1/2"
            role="status"
            aria-live="polite"
            aria-atomic="true"
        >
            <div className={cn(
                'flex items-center gap-2 whitespace-nowrap rounded-full px-3 py-1.5 text-xs text-white shadow-lg',
                inhalt.hervorheben ? 'bg-slate-800/95 font-semibold' : 'bg-black/70',
            )}>
                {inhalt.symbol === 'laedt' && <Loader2 aria-hidden="true" className="h-3 w-3 motion-safe:animate-spin" />}
                {inhalt.symbol === 'suchen' && <ScanLine aria-hidden="true" className="h-3 w-3" />}
                {inhalt.symbol === 'fertig' && <Check aria-hidden="true" className="h-3 w-3" />}
                {inhalt.text}
            </div>
        </div>
    )
}
