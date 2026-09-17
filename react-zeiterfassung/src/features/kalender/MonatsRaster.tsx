import { useMemo } from 'react'
import type { KalenderTermin } from './typen'
import { WOCHENTAGE_KURZ, anzahlText, formatDatumLang, monatsRaster, punktFarben } from './kalenderDaten'
import { useWischen } from './useWischen'

interface MonatsRasterProps {
    jahr: number
    /** 0-basiert wie `Date.getMonth()`. */
    monat0: number
    /** `null`, solange der Monat noch lädt – dann erscheint das Skeleton. */
    termineProTag: Map<string, KalenderTermin[]> | null
    heute: string
    ausgewaehlt: string
    onTag: (datum: string) => void
    /** Wischen nach links → +1 (nächster Monat), nach rechts → −1. */
    onWischen?: (richtung: 1 | -1) => void
}

/**
 * Monatsraster im Stil der Apple-Kalender-App: Zahl im Kreis, darunter bis zu
 * drei farbige Punkte. Heute ist rose gefüllt, der gewählte Tag dunkel. Das
 * Raster füllt die Höhe, die ihm der Elternknoten gibt – die Zeilen wachsen mit.
 */
export function MonatsRaster({ jahr, monat0, termineProTag, heute, ausgewaehlt, onTag, onWischen }: MonatsRasterProps) {
    // Wochenweise gruppiert, damit das ARIA-Grid echte Zeilen hat (grid → row → gridcell).
    const wochen = useMemo(() => {
        const tage = monatsRaster(jahr, monat0)
        return Array.from({ length: tage.length / 7 }, (_, i) => tage.slice(i * 7, i * 7 + 7))
    }, [jahr, monat0])
    const wischen = useWischen(onWischen)

    // Kein min-h-0 auf der Karte: sie darf nie unter ihre Inhaltshöhe schrumpfen, sonst
    // läuft die letzte Zeile über die Legende. Fehlt Platz, scrollt stattdessen die Seite.
    return (
        <div className="flex flex-1 flex-col rounded-2xl border border-slate-200 bg-white shadow-sm" {...wischen}>
            <div className="grid shrink-0 grid-cols-7 px-2 pt-3 pb-1" aria-hidden="true">
                {WOCHENTAGE_KURZ.map((tag, i) => (
                    <div key={tag} className={`text-center text-xs font-semibold uppercase tracking-wide ${i >= 5 ? 'text-slate-300' : 'text-slate-400'}`}>
                        {tag}
                    </div>
                ))}
            </div>
            <div className="grid flex-1 auto-rows-fr grid-cols-7 px-2 pb-2" role="grid" aria-label="Monatsraster">
                {wochen.map(woche => (
                <div key={woche[0].datum} role="row" className="contents">
                {woche.map(tag => {
                    const termine = termineProTag?.get(tag.datum) ?? []
                    const istHeute = tag.datum === heute
                    const istAusgewaehlt = tag.datum === ausgewaehlt
                    const zahlKlasse = istHeute
                        ? 'bg-rose-600 font-semibold text-white'
                        : istAusgewaehlt
                            ? 'bg-slate-900 font-semibold text-white'
                            : !tag.imMonat
                                ? 'text-slate-300'
                                : tag.wochenende
                                    ? 'text-slate-400'
                                    : 'text-slate-800'
                    return (
                        <div key={tag.datum} role="gridcell" aria-selected={istAusgewaehlt} className="min-h-14">
                        <button
                            type="button"
                            onClick={() => onTag(tag.datum)}
                            aria-label={`${formatDatumLang(tag.datum)}, ${anzahlText(termine.length, 'Eintrag', 'Einträge')}`}
                            aria-current={istHeute ? 'date' : undefined}
                            className="flex h-full w-full flex-col items-center justify-center gap-1 rounded-xl transition-colors hover:bg-slate-50 focus:outline-none focus:ring-2 focus:ring-rose-500"
                        >
                            <span className={`flex h-9 w-9 items-center justify-center rounded-full text-base tabular-nums ${zahlKlasse}`}>
                                {tag.tag}
                            </span>
                            <span className="flex h-2 items-center gap-1" aria-hidden="true">
                                {termineProTag === null && tag.imMonat ? (
                                    <span className="h-2 w-5 rounded-full bg-slate-100 motion-safe:animate-pulse" />
                                ) : (
                                    punktFarben(termine).map(farbe => (
                                        <span key={farbe} className="h-2 w-2 rounded-full" style={{ backgroundColor: farbe }} />
                                    ))
                                )}
                            </span>
                        </button>
                        </div>
                    )
                })}
                </div>
                ))}
            </div>
        </div>
    )
}
