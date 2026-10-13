import { useRef, type ReactNode, type TouchEvent } from 'react'
import { Camera, Check, ChevronLeft, ChevronRight, Crop, ImageOff, Loader2, Trash2 } from 'lucide-react'
import { InfoAbschnitt, InfoButton } from '../ui/info-button'
import { SCAN_FILTER, type ScanFilter } from '../../services/scanFilter'
import { cn } from '../../lib/utils'
import type { ScanSeite } from './useScanSeiten'
import { PDF_LAEUFT } from './texte'

interface SeitenUebersichtProps {
    seiten: ScanSeite[]
    index: number
    onIndex: (index: number) => void
    onFilter: (id: string, filter: ScanFilter) => void
    onEckenAnpassen: (id: string) => void
    onLoeschen: (id: string) => void
    onWeiterScannen: () => void
    onFertig: () => void
    /** PDF wird gerade erstellt */
    erstelltPdf: boolean
}

/** Ab dieser Strecke (px) gilt eine Fingerbewegung als Wischen zur nächsten Seite. */
const WISCH_PX = 50

/**
 * Seitenübersicht nach dem Scannen: eine Seite groß, Wischen oder Pfeile
 * blättern. Darunter der Filter für genau diese Seite und die Aktionen.
 */
export default function SeitenUebersicht({
    seiten, index, onIndex, onFilter, onEckenAnpassen, onLoeschen, onWeiterScannen, onFertig, erstelltPdf,
}: SeitenUebersichtProps) {
    const seite = seiten[index]
    const wischStartRef = useRef<number | null>(null)
    if (!seite) return null

    const hatVorige = index > 0
    const hatNaechste = index < seiten.length - 1

    const beiWischStart = (e: TouchEvent) => { wischStartRef.current = e.touches[0]?.clientX ?? null }
    const beiWischEnde = (e: TouchEvent) => {
        const start = wischStartRef.current
        wischStartRef.current = null
        const ende = e.changedTouches[0]?.clientX
        if (start === null || ende === undefined) return
        const strecke = ende - start
        if (strecke <= -WISCH_PX && hatNaechste) onIndex(index + 1)
        else if (strecke >= WISCH_PX && hatVorige) onIndex(index - 1)
    }

    return (
        <div className="absolute inset-0 z-30 flex flex-col bg-black text-white">
            <div
                className="relative flex min-h-0 flex-1 items-center justify-center px-12 py-4"
                onTouchStart={beiWischStart}
                onTouchEnd={beiWischEnde}
            >
                <SeitenBild seite={seite} nummer={index + 1} />

                <PfeilKnopf
                    seite="links"
                    label="Vorige Seite"
                    sichtbar={hatVorige}
                    onClick={() => onIndex(index - 1)}
                >
                    <ChevronLeft aria-hidden="true" className="h-6 w-6" />
                </PfeilKnopf>
                <PfeilKnopf
                    seite="rechts"
                    label="Nächste Seite"
                    sichtbar={hatNaechste}
                    onClick={() => onIndex(index + 1)}
                >
                    <ChevronRight aria-hidden="true" className="h-6 w-6" />
                </PfeilKnopf>
            </div>

            <p className="pb-3 text-center text-sm text-slate-300" aria-live="polite">
                Seite {index + 1} von {seiten.length}
            </p>

            <div className="flex items-center justify-center gap-1 px-4 pb-4">
                <div
                    role="group"
                    aria-label="Bild verbessern"
                    className="flex rounded-full bg-slate-800 p-1"
                >
                    {SCAN_FILTER.map(({ wert, label }) => {
                        const aktiv = seite.filter === wert
                        return (
                            <button
                                key={wert}
                                type="button"
                                aria-pressed={aktiv}
                                disabled={erstelltPdf}
                                title={erstelltPdf ? PDF_LAEUFT : undefined}
                                onClick={() => onFilter(seite.id, wert)}
                                className={cn(
                                    'min-h-9 rounded-full px-3 text-sm font-medium focus:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-rose-500 motion-safe:transition-colors',
                                    aktiv ? 'bg-white text-slate-900 shadow-sm' : 'text-slate-300 hover:text-white',
                                    'disabled:cursor-not-allowed disabled:opacity-50',
                                )}
                            >
                                {label}
                            </button>
                        )
                    })}
                </div>
                <InfoButton titel="Bild verbessern" className="text-slate-300 hover:bg-white/10 hover:text-white">
                    <InfoAbschnitt titel="Was passiert im Hintergrund?">
                        <p>Das Handy macht das Papier gleichmäßig weiß und entfernt Schatten und Farbstich vom Licht. Alles passiert auf dem Handy, das Foto wird dafür nirgends hochgeladen.</p>
                    </InfoAbschnitt>
                    <InfoAbschnitt titel="Was bewirkt das?">
                        <p>Ins PDF kommt die Seite so, wie du sie hier siehst. ‚Original‘ ist nur zugeschnitten, ohne Verbesserung. Die Wahl gilt nur für diese Seite; die anderen Seiten bleiben, wie sie sind.</p>
                    </InfoAbschnitt>
                </InfoButton>
            </div>

            <div className="grid grid-cols-3 gap-2 px-4 pb-3">
                <AktionsKnopf onClick={() => onEckenAnpassen(seite.id)} label="Ecken anpassen" gesperrt={erstelltPdf}>
                    <Crop aria-hidden="true" className="h-5 w-5" />
                </AktionsKnopf>
                <AktionsKnopf onClick={() => onLoeschen(seite.id)} label="Löschen" gesperrt={erstelltPdf}>
                    <Trash2 aria-hidden="true" className="h-5 w-5" />
                </AktionsKnopf>
                <AktionsKnopf onClick={onWeiterScannen} label="Weiter scannen" gesperrt={erstelltPdf}>
                    <Camera aria-hidden="true" className="h-5 w-5" />
                </AktionsKnopf>
            </div>

            <div className="px-4 pb-5">
                <button
                    type="button"
                    onClick={onFertig}
                    disabled={erstelltPdf}
                    title={erstelltPdf ? PDF_LAEUFT : undefined}
                    className="flex min-h-12 w-full items-center justify-center gap-2 rounded-full border border-rose-600 bg-rose-600 font-semibold text-white hover:bg-rose-700 focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-300 disabled:opacity-60"
                >
                    {erstelltPdf
                        ? <Loader2 aria-hidden="true" className="h-5 w-5 motion-safe:animate-spin" />
                        : <Check aria-hidden="true" className="h-5 w-5" />}
                    {erstelltPdf ? PDF_LAEUFT : `Fertig (${seiten.length})`}
                </button>
            </div>
        </div>
    )
}

function SeitenBild({ seite, nummer }: { seite: ScanSeite; nummer: number }) {
    if (seite.status === 'fehler' && !seite.vorschauUrl) {
        return (
            <div className="flex aspect-[1/1.414] max-h-full w-full max-w-xs flex-col items-center justify-center gap-2 rounded-lg bg-slate-800 p-6 text-center text-sm text-slate-300">
                <ImageOff aria-hidden="true" className="h-8 w-8 text-slate-400" />
                Seite konnte nicht verbessert werden. Ecken anpassen oder die Seite löschen.
            </div>
        )
    }
    if (!seite.vorschauUrl) {
        return (
            <div
                className="flex aspect-[1/1.414] max-h-full w-full max-w-xs items-center justify-center rounded-lg bg-slate-800 motion-safe:animate-pulse"
                role="status"
                aria-label={`Seite ${nummer} wird verbessert`}
            >
                <Loader2 aria-hidden="true" className="h-8 w-8 text-slate-400 motion-safe:animate-spin" />
            </div>
        )
    }
    return (
        <div className="relative flex h-full max-h-full items-center justify-center">
            <img
                src={seite.vorschauUrl}
                alt={`Seite ${nummer}`}
                className={cn('max-h-full max-w-full rounded-sm object-contain shadow-xl', seite.status === 'arbeitet' && 'opacity-60')}
            />
            {seite.status === 'arbeitet' && (
                <div className="absolute inset-0 flex items-center justify-center" role="status" aria-label={`Seite ${nummer} wird verbessert`}>
                    <div className="rounded-full bg-black/70 p-3">
                        <Loader2 aria-hidden="true" className="h-6 w-6 text-white motion-safe:animate-spin" />
                    </div>
                </div>
            )}
        </div>
    )
}

function PfeilKnopf({ seite, label, sichtbar, onClick, children }: {
    seite: 'links' | 'rechts'
    label: string
    sichtbar: boolean
    onClick: () => void
    children: ReactNode
}) {
    if (!sichtbar) return null
    return (
        <button
            type="button"
            aria-label={label}
            onClick={onClick}
            className={cn(
                'absolute top-1/2 flex h-11 w-11 -translate-y-1/2 items-center justify-center rounded-full bg-white/10 text-white hover:bg-white/20 focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-500',
                seite === 'links' ? 'left-1' : 'right-1',
            )}
        >
            {children}
        </button>
    )
}

function AktionsKnopf({ label, onClick, gesperrt, children }: { label: string; onClick: () => void; gesperrt: boolean; children: ReactNode }) {
    return (
        <button
            type="button"
            onClick={onClick}
            disabled={gesperrt}
            title={gesperrt ? PDF_LAEUFT : undefined}
            className="flex min-h-14 flex-col items-center justify-center gap-1 rounded-xl bg-slate-800 text-xs font-medium text-slate-100 hover:bg-slate-700 focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-500 disabled:cursor-not-allowed disabled:opacity-50"
        >
            {children}
            {label}
        </button>
    )
}
