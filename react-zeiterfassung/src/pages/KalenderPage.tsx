import { useCallback, useEffect } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { AlertTriangle, ArrowLeft, ChevronLeft, ChevronRight, RefreshCw } from 'lucide-react'
import { useToast } from '../components/ui/toast'
import { EINTRAG_ARTEN, EINTRAG_ART_REIHENFOLGE, type KalenderTermin } from '../features/kalender/typen'
import { addiereMonate, formatMonatsTitel, heuteIso, istGueltigesIsoDatum, parseLocalDate, sichtbarerBereich } from '../features/kalender/kalenderDaten'
import { LADEFEHLER_TEXT, useKalenderDaten } from '../features/kalender/useKalenderDaten'
import { MonatsRaster } from '../features/kalender/MonatsRaster'
import { TagesSheet } from '../features/kalender/TagesSheet'
import { terminPfad } from '../features/kalender/pfade'
import '../features/kalender/kalender.css'

interface KalenderPageProps {
    mitarbeiter: { id: number; name: string } | null
    token: string | null
    syncStatus?: 'syncing' | 'done' | 'error'
    onSync?: () => void
}

function liesDatum(wert: string | null, heute: string): string {
    return istGueltigesIsoDatum(wert) ? wert : heute
}

/**
 * Kalender der Zeiterfassungs-App: ein großes Monatsraster mit Legende, sonst
 * nichts. Ein Tipp auf einen Tag öffnet das Tages-Sheet, ein Tipp auf einen
 * Eintrag darin die Termin-Unterseite. Datum und offenes Sheet stehen in der
 * URL (`?datum=&sheet=tag`), damit der Zurück-Pfeil der Unterseite exakt
 * hierher zurückführt.
 */
export default function KalenderPage({ token, syncStatus, onSync }: KalenderPageProps) {
    const navigate = useNavigate()
    const toast = useToast()
    const [searchParams, setSearchParams] = useSearchParams()

    const heute = heuteIso()
    const datum = liesDatum(searchParams.get('datum'), heute)
    const sheetDatum = searchParams.get('sheet') === 'tag' ? datum : null
    const terminParam = searchParams.get('termin')

    // Kein useMemo nötig: der Hook liest aus dem Bereich nur die beiden Datumsstrings.
    const { termineProTag, laedt, fehler, neuLaden } = useKalenderDaten(token, sichtbarerBereich(datum))

    const aktualisiereParams = useCallback((aenderungen: Record<string, string | null>) => {
        setSearchParams(alt => {
            const neu = new URLSearchParams(alt)
            for (const [schluessel, wert] of Object.entries(aenderungen)) {
                if (wert === null) neu.delete(schluessel)
                else neu.set(schluessel, wert)
            }
            return neu
        }, { replace: true })
    }, [setSearchParams])

    useEffect(() => {
        if (fehler) toast.error(fehler)
    }, [fehler, toast])

    // Deep-Link aus der Push-Benachrichtigung (?termin=ID): direkt zur Unterseite.
    useEffect(() => {
        if (!terminParam || !termineProTag) return
        const id = Number(terminParam)
        const termin = Array.from(termineProTag.values()).flat().find(t => t.terminId === id)
        if (termin) {
            navigate(terminPfad(termin), { replace: true, state: { termin, zurueck: false } })
        } else {
            aktualisiereParams({ termin: null })
            toast.info('Der Termin liegt nicht im angezeigten Monat.')
        }
    }, [terminParam, termineProTag, navigate, aktualisiereParams, toast])

    const blaettere = (richtung: 1 | -1) => aktualisiereParams({ datum: addiereMonate(datum, richtung) })
    const zuHeute = () => aktualisiereParams({ datum: heute })
    const oeffneTagesSheet = (tag: string) => aktualisiereParams({ datum: tag, sheet: 'tag' })
    const schliesseSheet = () => aktualisiereParams({ sheet: null })
    const oeffneTermin = (termin: KalenderTermin) => navigate(terminPfad(termin), { state: { termin, zurueck: true } })
    const aktualisieren = () => {
        neuLaden()
        onSync?.()
    }

    const monatsDatum = parseLocalDate(datum)

    return (
        <div className="flex h-full flex-col bg-slate-50">
            <header className="safe-area-top [--sa-base-top:0.75rem] sticky top-0 z-10 border-b border-slate-200 bg-white px-4 py-3">
                <div className="flex items-center gap-3">
                    <button
                        type="button"
                        onClick={() => navigate('/')}
                        aria-label="Zurück zur Übersicht"
                        className="-ml-2 flex h-11 w-11 items-center justify-center rounded-full text-slate-500 hover:bg-slate-100 hover:text-slate-900 focus:outline-none focus:ring-2 focus:ring-rose-500"
                    >
                        <ArrowLeft aria-hidden="true" className="h-5 w-5" />
                    </button>
                    <div className="min-w-0 flex-1">
                        <h1 className="text-lg font-bold text-slate-900">Kalender</h1>
                        <p className="text-xs text-slate-500">Termine, Urlaub und Feiertage im Team</p>
                    </div>
                    <button
                        type="button"
                        onClick={aktualisieren}
                        aria-label="Aktualisieren"
                        className="-mr-2 flex h-11 w-11 items-center justify-center rounded-full hover:bg-slate-100 focus:outline-none focus:ring-2 focus:ring-rose-500"
                    >
                        <RefreshCw aria-hidden="true" className={`h-5 w-5 ${laedt || syncStatus === 'syncing' ? 'animate-sync-spin text-rose-600' : 'text-slate-500'}`} />
                    </button>
                </div>
            </header>

            <main className="flex min-h-0 flex-1 flex-col overflow-y-auto px-4 pb-6">
                <div className="flex shrink-0 items-center gap-1 py-3">
                    <h2 className="min-w-0 flex-1 text-lg font-bold text-slate-900" aria-live="polite">{formatMonatsTitel(datum)}</h2>
                    <button
                        type="button"
                        onClick={() => blaettere(-1)}
                        aria-label="Vorheriger Monat"
                        className="flex h-10 w-10 items-center justify-center rounded-full text-slate-600 hover:bg-slate-100 focus:outline-none focus:ring-2 focus:ring-rose-500"
                    >
                        <ChevronLeft aria-hidden="true" className="h-5 w-5" />
                    </button>
                    <button
                        type="button"
                        onClick={() => blaettere(1)}
                        aria-label="Nächster Monat"
                        className="flex h-10 w-10 items-center justify-center rounded-full text-slate-600 hover:bg-slate-100 focus:outline-none focus:ring-2 focus:ring-rose-500"
                    >
                        <ChevronRight aria-hidden="true" className="h-5 w-5" />
                    </button>
                    <button
                        type="button"
                        onClick={zuHeute}
                        className="ml-1 h-9 rounded-full border border-rose-200 bg-rose-50 px-3 text-xs font-semibold text-rose-700 hover:bg-rose-100 focus:outline-none focus:ring-2 focus:ring-rose-500"
                    >
                        Heute
                    </button>
                </div>

                {/* Fehlerzustand bleibt sichtbar, auch wenn der Toast längst weg ist – ein leeres Raster sieht sonst aus wie „keine Termine“. */}
                {fehler && (
                    <div className="mb-3 flex shrink-0 items-center gap-3 rounded-xl border border-rose-200 bg-rose-50 px-4 py-3">
                        <AlertTriangle aria-hidden="true" className="h-5 w-5 shrink-0 text-rose-600" />
                        <p className="min-w-0 flex-1 text-sm text-rose-900">{LADEFEHLER_TEXT}</p>
                        <button
                            type="button"
                            onClick={aktualisieren}
                            className="shrink-0 rounded-lg border border-rose-300 bg-white px-3 py-1.5 text-xs font-semibold text-rose-700 hover:bg-rose-100 focus:outline-none focus:ring-2 focus:ring-rose-500"
                        >
                            Erneut versuchen
                        </button>
                    </div>
                )}

                {/* Der Monat nutzt die ganze verbleibende Höhe: das Raster wächst, die Legende bleibt darunter. */}
                <div key={datum.substring(0, 7)} className="kalender-einblenden flex flex-1 flex-col gap-4">
                    <MonatsRaster
                        jahr={monatsDatum.getFullYear()}
                        monat0={monatsDatum.getMonth()}
                        termineProTag={termineProTag}
                        heute={heute}
                        ausgewaehlt={datum}
                        onTag={oeffneTagesSheet}
                        onWischen={blaettere}
                    />
                    <Legende />
                </div>
            </main>

            <TagesSheet
                datum={sheetDatum}
                termine={sheetDatum && termineProTag ? (termineProTag.get(sheetDatum) ?? []) : null}
                heute={heute}
                onSchliessen={schliesseSheet}
                onTermin={oeffneTermin}
            />
        </div>
    )
}

/** Farblegende – erklärt die Punkte im Raster. */
function Legende() {
    return (
        <ul aria-label="Legende" className="flex shrink-0 flex-wrap gap-x-4 gap-y-2 px-1">
            {EINTRAG_ART_REIHENFOLGE.map(art => (
                <li key={art} className="flex items-center gap-1.5 text-xs font-medium text-slate-500">
                    <span aria-hidden="true" className="h-2.5 w-2.5 rounded-full" style={{ backgroundColor: EINTRAG_ARTEN[art].farbe }} />
                    {EINTRAG_ARTEN[art].label}
                </li>
            ))}
        </ul>
    )
}
