import { CalendarDays } from 'lucide-react'
import type { KalenderTermin } from './typen'
import { TerminKarte } from './TerminKarte'

interface TagesListeProps {
    termine: KalenderTermin[]
    onTermin: (termin: KalenderTermin) => void
}

/** Sortierte Terminliste eines Tages mit gestaltetem Leerzustand. */
export function TagesListe({ termine, onTermin }: TagesListeProps) {
    if (termine.length === 0) return <LeerZustand text="Keine Termine an diesem Tag." />
    return (
        <ul className="space-y-2">
            {termine.map(termin => (
                <li key={termin.key}>
                    <TerminKarte termin={termin} onClick={onTermin} />
                </li>
            ))}
        </ul>
    )
}

export function LeerZustand({ text }: { text: string }) {
    return (
        <div className="flex flex-col items-center gap-2 rounded-xl border border-dashed border-slate-200 bg-white/70 px-4 py-6 text-center">
            <span className="flex h-10 w-10 items-center justify-center rounded-full bg-slate-100">
                <CalendarDays aria-hidden="true" className="h-5 w-5 text-slate-400" />
            </span>
            <p className="text-sm text-slate-500">{text}</p>
        </div>
    )
}

/** Platzhalter, solange Termine laden – nie ein leerer Kasten. */
export function TagesListeSkeleton({ zeilen = 3 }: { zeilen?: number }) {
    return (
        <div role="status" aria-label="Termine werden geladen" className="space-y-2">
            {Array.from({ length: zeilen }).map((_, i) => (
                <div key={i} className="flex items-stretch gap-3 rounded-xl border border-slate-200 bg-white p-3 shadow-sm">
                    <div className="w-14 shrink-0 space-y-1.5 py-1">
                        <div className="ml-auto h-3.5 w-10 rounded bg-slate-200 motion-safe:animate-pulse" />
                        <div className="ml-auto h-3 w-8 rounded bg-slate-100 motion-safe:animate-pulse" />
                    </div>
                    <div className="w-1 shrink-0 rounded-full bg-slate-200 motion-safe:animate-pulse" />
                    <div className="flex-1 space-y-2 py-1">
                        <div className="h-3.5 w-2/3 rounded bg-slate-200 motion-safe:animate-pulse" />
                        <div className="h-3 w-1/3 rounded bg-slate-100 motion-safe:animate-pulse" />
                    </div>
                </div>
            ))}
        </div>
    )
}
