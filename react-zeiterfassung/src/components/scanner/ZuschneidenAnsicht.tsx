import { useId, useRef, useState, type CSSProperties, type MouseEvent, type SyntheticEvent, type TouchEvent } from 'react'
import { AlertTriangle, Check, Loader2, Wand2 } from 'lucide-react'
import { detectDocumentCorners } from '../../services/DocumentEdgeDetector'
import { viereckFehler, type Point, type ViereckFehler } from '../../services/scannerOverlay'
import { cn } from '../../lib/utils'

interface ZuschneidenAnsichtProps {
    /** Object-URL des Fotos */
    fotoUrl: string
    /** Gespeicherte Ecken der Seite; ohne sie sucht die Erkennung das Blatt im Foto. */
    startEcken: Point[] | null
    onUebernehmen: (ecken: Point[]) => void
    onZurueck: () => void
}

type ErkennungsStatus = 'vorgegeben' | 'sucht' | 'erkannt' | 'vonHand'

interface Lupe {
    bildschirmX: number
    bildschirmY: number
    quelleX: number
    quelleY: number
}

const LUPE_ZOOM = 1.15
const LUPE_GROESSE = 96
const LUPE_RADIUS = LUPE_GROESSE / 2

const FEHLER_TEXT: Record<ViereckFehler, string> = {
    'ueberkreuzt': 'Ecken überkreuzen sich – bitte so ziehen, dass ein Viereck entsteht',
    'zu-klein': 'Ausschnitt ist zu klein – bitte die Ecken weiter auseinanderziehen',
}

/**
 * Zuschneiden einer Seite: vier Ecken, die sich mit dem Finger an die
 * Blattkanten ziehen lassen. Beim Ziehen zeigt eine Lupe die Stelle unter
 * dem Finger vergrößert.
 */
export default function ZuschneidenAnsicht({ fotoUrl, startEcken, onUebernehmen, onZurueck }: ZuschneidenAnsichtProps) {
    const bildRef = useRef<HTMLImageElement>(null)
    const [ecken, setEcken] = useState<Point[]>([])
    const [status, setStatus] = useState<ErkennungsStatus>(startEcken ? 'vorgegeben' : 'sucht')
    const [zieht, setZieht] = useState<number | null>(null)
    const ziehtRef = useRef<number | null>(null)
    const [lupe, setLupe] = useState<Lupe | null>(null)
    /** Pixelmaße des Fotos (naturalWidth/Height), sobald es geladen ist. */
    const [groesse, setGroesse] = useState<{ b: number; h: number } | null>(null)

    const beiBildGeladen = (e: SyntheticEvent<HTMLImageElement>) => {
        const bild = e.currentTarget
        const b = bild.naturalWidth
        const h = bild.naturalHeight
        setGroesse({ b, h })
        if (startEcken?.length === 4) {
            setEcken(startEcken)
            setStatus('vorgegeben')
            return
        }
        // Erst 10 % Rand, damit sofort etwas zu sehen ist; die Erkennung
        // überschreibt das, sobald sie das Blatt gefunden hat.
        setEcken([
            { x: b * 0.1, y: h * 0.1 },
            { x: b * 0.9, y: h * 0.1 },
            { x: b * 0.9, y: h * 0.9 },
            { x: b * 0.1, y: h * 0.9 },
        ])
        setStatus('sucht')
        detectDocumentCorners(bild)
            .catch(() => null)
            .then(erkannt => {
                // Hat der Nutzer schon eine Ecke angefasst, nicht drüberschreiben.
                if (erkannt && ziehtRef.current === null) {
                    setEcken(erkannt)
                    setStatus('erkannt')
                } else {
                    setStatus('vonHand')
                }
            })
    }

    const starteZiehen = (index: number) => {
        ziehtRef.current = index
        setZieht(index)
    }

    const beiZiehen = (e: TouchEvent | MouseEvent) => {
        const index = ziehtRef.current
        const bild = bildRef.current
        if (index === null || !bild) return
        const punkt = 'touches' in e ? e.touches[0] : e
        if (!punkt) return
        const rahmen = bild.getBoundingClientRect()
        const quelleX = Math.max(0, Math.min(bild.naturalWidth, (punkt.clientX - rahmen.left) * (bild.naturalWidth / rahmen.width)))
        const quelleY = Math.max(0, Math.min(bild.naturalHeight, (punkt.clientY - rahmen.top) * (bild.naturalHeight / rahmen.height)))
        setLupe({ bildschirmX: punkt.clientX, bildschirmY: punkt.clientY - 80, quelleX, quelleY })
        setEcken(vorher => vorher.map((p, i) => (i === index ? { x: quelleX, y: quelleY } : p)))
    }

    const beendeZiehen = () => {
        ziehtRef.current = null
        setZieht(null)
        setLupe(null)
    }

    const hinweisId = useId()
    const fehler = groesse && ecken.length === 4 ? viereckFehler(ecken, groesse.b, groesse.h) : null
    const gesperrt = ecken.length !== 4 || fehler !== null

    const eckStil = (p: Point): CSSProperties => (groesse
        ? { left: `${(p.x / groesse.b) * 100}%`, top: `${(p.y / groesse.h) * 100}%` }
        : {})

    return (
        <div className="absolute inset-0 z-30 flex flex-col bg-black">
            <div
                className="relative flex flex-1 items-center justify-center overflow-hidden p-4"
                onMouseMove={beiZiehen}
                onMouseUp={beendeZiehen}
                onMouseLeave={beendeZiehen}
                onTouchMove={beiZiehen}
                onTouchEnd={beendeZiehen}
                onTouchCancel={beendeZiehen}
            >
                <div className="pointer-events-none absolute left-1/2 top-2 z-30 -translate-x-1/2 whitespace-nowrap" role="status" aria-live="polite">
                    {status === 'sucht' && (
                        <div className="flex items-center gap-2 rounded-full bg-black/70 px-3 py-1.5 text-xs text-white shadow-lg">
                            <Loader2 aria-hidden="true" className="h-3 w-3 motion-safe:animate-spin" />
                            Blattkanten werden gesucht…
                        </div>
                    )}
                    {status === 'erkannt' && (
                        <div className="flex items-center gap-2 rounded-full bg-slate-800/90 px-3 py-1.5 text-xs text-white shadow-lg">
                            <Wand2 aria-hidden="true" className="h-3 w-3" />
                            Ecken erkannt – bei Bedarf verschieben
                        </div>
                    )}
                    {(status === 'vonHand' || status === 'vorgegeben') && (
                        <div className="rounded-full bg-slate-800/90 px-3 py-1.5 text-xs text-white shadow-lg">
                            Ecken an die Blattkanten ziehen
                        </div>
                    )}
                </div>

                <div className="relative inline-block">
                    <img
                        ref={bildRef}
                        src={fotoUrl}
                        alt="Aufgenommenes Foto zum Zuschneiden"
                        onLoad={beiBildGeladen}
                        className="pointer-events-none max-h-[70dvh] max-w-full object-contain"
                    />
                    {groesse && (
                        <svg
                            className="pointer-events-none absolute inset-0 h-full w-full"
                            viewBox={`0 0 ${groesse.b} ${groesse.h}`}
                            preserveAspectRatio="none"
                            aria-hidden="true"
                        >
                            <polygon
                                points={ecken.map(p => `${p.x},${p.y}`).join(' ')}
                                fill="rgba(225, 29, 72, 0.2)"
                                stroke="#e11d48"
                                strokeWidth="2"
                                vectorEffect="non-scaling-stroke"
                            />
                        </svg>
                    )}
                    {ecken.map((p, i) => (
                        <div
                            key={i}
                            style={eckStil(p)}
                            data-testid={`ecke-${i}`}
                            className={cn(
                                'absolute z-20 -ml-6 -mt-6 flex h-12 w-12 cursor-move touch-none items-center justify-center motion-safe:transition-transform',
                                zieht === i && 'scale-110',
                            )}
                            onMouseDown={e => { e.preventDefault(); starteZiehen(i) }}
                            onTouchStart={() => starteZiehen(i)}
                        >
                            <div className={cn(
                                'flex h-5 w-5 items-center justify-center rounded-full border-2 border-white bg-rose-600 shadow-lg',
                                zieht === i && 'bg-rose-500 ring-4 ring-rose-300/50',
                            )}>
                                <div className="h-2 w-2 rounded-full bg-white" />
                            </div>
                        </div>
                    ))}
                </div>

                {lupe && (
                    <div
                        data-testid="lupe"
                        aria-hidden="true"
                        className="pointer-events-none fixed z-50 overflow-hidden rounded-full border-4 border-white bg-black shadow-2xl"
                        style={{
                            width: LUPE_GROESSE,
                            height: LUPE_GROESSE,
                            left: lupe.bildschirmX - LUPE_RADIUS,
                            top: lupe.bildschirmY - LUPE_RADIUS,
                        }}
                    >
                        <div
                            className="h-full w-full bg-no-repeat"
                            style={{
                                backgroundImage: `url(${fotoUrl})`,
                                backgroundPosition: `${LUPE_RADIUS - lupe.quelleX * LUPE_ZOOM}px ${LUPE_RADIUS - lupe.quelleY * LUPE_ZOOM}px`,
                                backgroundSize: `${(groesse?.b ?? 0) * LUPE_ZOOM}px ${(groesse?.h ?? 0) * LUPE_ZOOM}px`,
                            }}
                        />
                        <div className="absolute inset-0 flex items-center justify-center opacity-70">
                            <div className="h-3 w-0.5 bg-rose-500" />
                            <div className="absolute h-0.5 w-3 bg-rose-500" />
                        </div>
                    </div>
                )}
            </div>

            {/* Immer vorhanden, damit Screenreader Änderungen ruhig ansagen – beim Ziehen wechselt der Hinweis oft. */}
            <div aria-live="polite">
                {fehler && (
                    <p id={hinweisId} className="flex items-start gap-2 bg-black px-6 pt-3 text-sm text-amber-300">
                        <AlertTriangle aria-hidden="true" className="mt-0.5 h-4 w-4 shrink-0" />
                        {FEHLER_TEXT[fehler]}
                    </p>
                )}
            </div>
            <div className="flex items-center justify-between gap-4 bg-black px-6 py-5 text-white">
                <button
                    type="button"
                    onClick={onZurueck}
                    className="min-h-11 rounded-lg px-3 text-sm text-slate-300 hover:text-white focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-500"
                >
                    Zurück
                </button>
                <button
                    type="button"
                    onClick={() => onUebernehmen(ecken)}
                    disabled={gesperrt}
                    title={fehler ? FEHLER_TEXT[fehler] : undefined}
                    aria-describedby={fehler ? hinweisId : undefined}
                    className="flex min-h-11 items-center gap-2 rounded-full border border-rose-600 bg-rose-600 px-6 font-semibold text-white hover:bg-rose-700 focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-300 disabled:opacity-50"
                >
                    <Check aria-hidden="true" className="h-5 w-5" />
                    Übernehmen
                </button>
            </div>
        </div>
    )
}
