import { Check, Loader2 } from 'lucide-react'
import type { ScanSeite } from './useScanSeiten'
import { PDF_LAEUFT } from './texte'

interface KameraLeisteProps {
    seiten: ScanSeite[]
    onAufnehmen: () => void
    onSeitenOeffnen: () => void
    onFertig: () => void
    erstelltPdf: boolean
}

/**
 * Untere Leiste der Kamera wie in der Apple-Scanner-App: links die zuletzt
 * gescannte Seite (öffnet die Seitenübersicht), Mitte der Auslöser von Hand,
 * rechts „Fertig“.
 */
export default function KameraLeiste({ seiten, onAufnehmen, onSeitenOeffnen, onFertig, erstelltPdf }: KameraLeisteProps) {
    const letzte = seiten.at(-1)
    const anzahl = seiten.length
    const fertigGesperrt = anzahl === 0 || erstelltPdf

    return (
        <div className="grid grid-cols-[1fr_auto_1fr] items-center gap-4 bg-black px-5 py-5 text-white">
            <div className="flex justify-start">
                {letzte ? (
                    <button
                        type="button"
                        onClick={onSeitenOeffnen}
                        aria-label={`Seiten ansehen und bearbeiten (${anzahl} ${anzahl === 1 ? 'Seite' : 'Seiten'})`}
                        className="relative h-16 w-12 rounded-md border-2 border-white/80 bg-slate-800 focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-500"
                    >
                        {letzte.vorschauUrl ? (
                            <img
                                key={letzte.id}
                                src={letzte.vorschauUrl}
                                alt=""
                                className="h-full w-full rounded-[4px] object-cover"
                            />
                        ) : (
                            <span className="flex h-full w-full items-center justify-center rounded-[4px] motion-safe:animate-pulse">
                                <Loader2 aria-hidden="true" className="h-4 w-4 text-slate-300 motion-safe:animate-spin" />
                            </span>
                        )}
                        <span
                            aria-hidden="true"
                            className="absolute -right-2 -top-2 flex h-5 min-w-5 items-center justify-center rounded-full bg-white px-1 text-xs font-bold text-slate-900 shadow"
                        >
                            {anzahl}
                        </span>
                    </button>
                ) : (
                    <span className="h-16 w-12" aria-hidden="true" />
                )}
            </div>

            <button
                type="button"
                onClick={onAufnehmen}
                disabled={erstelltPdf}
                aria-label="Foto von Hand aufnehmen"
                title={erstelltPdf ? PDF_LAEUFT : undefined}
                className="flex h-20 w-20 items-center justify-center rounded-full border-4 border-white bg-white/20 active:bg-white/50 focus:outline-none focus-visible:ring-4 focus-visible:ring-rose-500 disabled:opacity-40 motion-safe:transition-colors"
            >
                <span className="h-16 w-16 rounded-full bg-white" />
            </button>

            <div className="flex justify-end">
                <button
                    type="button"
                    onClick={onFertig}
                    disabled={fertigGesperrt}
                    title={erstelltPdf ? PDF_LAEUFT : anzahl === 0 ? 'Erst eine Seite scannen' : undefined}
                    className="flex min-h-11 items-center gap-1.5 rounded-full border border-rose-600 bg-rose-600 px-4 text-sm font-semibold text-white hover:bg-rose-700 focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-300 disabled:border-slate-700 disabled:bg-slate-800 disabled:text-slate-400"
                >
                    {erstelltPdf
                        ? <Loader2 aria-hidden="true" className="h-4 w-4 motion-safe:animate-spin" />
                        : <Check aria-hidden="true" className="h-4 w-4" />}
                    Fertig ({anzahl})
                </button>
            </div>
        </div>
    )
}
