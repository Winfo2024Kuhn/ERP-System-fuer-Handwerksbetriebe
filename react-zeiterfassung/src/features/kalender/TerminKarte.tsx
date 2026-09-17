import { ChevronRight, Users } from 'lucide-react'
import type { KalenderTermin } from './typen'
import { anzahlText } from './kalenderDaten'
import { ART_ICONS, VERKNUEPFUNG_ICONS } from './icons'

interface TerminKarteProps {
    termin: KalenderTermin
    onClick: (termin: KalenderTermin) => void
}

/**
 * Eine Zeile in Terminlisten: Zeitspalte links, farbiger Balken in der Farbe
 * des Eintrags, Titel und Einordnung, Pfeil rechts als Hinweis auf die Details.
 */
export function TerminKarte({ termin, onClick }: TerminKarteProps) {
    // Untertitel-Icon: bei Terminen das der ersten Verknüpfung, sonst das der Art.
    const verknuepfung = termin.verknuepfungen[0]
    const Icon = verknuepfung ? VERKNUEPFUNG_ICONS[verknuepfung.art] : ART_ICONS[termin.art]
    return (
        <button
            type="button"
            onClick={() => onClick(termin)}
            className="flex w-full items-stretch gap-3 rounded-xl border border-slate-200 bg-white p-3 text-left shadow-sm transition-[border-color,box-shadow] hover:border-slate-300 hover:shadow-md focus:outline-none focus:ring-2 focus:ring-rose-500"
        >
            <div className="flex w-14 shrink-0 flex-col justify-center text-right tabular-nums">
                {termin.ganztaegig ? (
                    <span className="text-[11px] font-medium leading-4 text-slate-500">Ganztägig</span>
                ) : (
                    <>
                        <span className="text-sm font-semibold leading-5 text-slate-900">{termin.startZeit}</span>
                        {termin.endeZeit && <span className="text-xs leading-4 text-slate-500">{termin.endeZeit}</span>}
                    </>
                )}
            </div>
            <span aria-hidden="true" className="w-1 shrink-0 self-stretch rounded-full" style={{ backgroundColor: termin.farbe }} />
            <div className="min-w-0 flex-1 py-0.5">
                {/* Kürzung gewollt: die Unterseite zeigt den vollen Titel; `title` liefert ihn schon hier. */}
                <p className="line-clamp-2 text-sm font-semibold leading-5 text-slate-900" title={termin.titel} data-kuerzung-erlaubt>
                    {termin.titel}
                </p>
                <p className="mt-0.5 flex items-center gap-1.5 text-xs text-slate-500">
                    <Icon aria-hidden="true" className="h-3.5 w-3.5 shrink-0" />
                    <span className="line-clamp-1" title={termin.untertitel} data-kuerzung-erlaubt>{termin.untertitel}</span>
                </p>
                {termin.teilnehmer.length > 0 && (
                    <p className="mt-0.5 flex items-center gap-1.5 text-xs text-slate-500">
                        <Users aria-hidden="true" className="h-3.5 w-3.5 shrink-0" />
                        <span>{anzahlText(termin.teilnehmer.length, 'Teilnehmer', 'Teilnehmer')}</span>
                    </p>
                )}
            </div>
            <ChevronRight aria-hidden="true" className="h-4 w-4 shrink-0 self-center text-slate-300" />
        </button>
    )
}
