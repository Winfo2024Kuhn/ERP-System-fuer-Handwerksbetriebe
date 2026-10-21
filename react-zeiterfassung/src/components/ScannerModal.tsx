import { useCallback, useEffect, useRef, useState } from 'react'
import { X } from 'lucide-react'
import Camera, { type CameraHandle } from './Camera'
import { useToast } from './ui/toast'
import { useConfirm } from './ui/confirm-dialog'
import { cn } from '../lib/utils'
import type { Point } from '../services/scannerOverlay'
import { kameraFehlerText } from '../services/kameraFehler'
import { erstelleScanPdf } from '../services/scanPdf'
import type { ScanFilter } from '../services/scanFilter'
import { useScanSeiten } from './scanner/useScanSeiten'
import { useLiveErkennung } from './scanner/useLiveErkennung'
import KameraLeiste from './scanner/KameraLeiste'
import LiveStatusPille from './scanner/LiveStatusPille'
import SeitenUebersicht from './scanner/SeitenUebersicht'
import ZuschneidenAnsicht from './scanner/ZuschneidenAnsicht'
import { PDF_LAEUFT } from './scanner/texte'

interface ScannerModalProps {
    onClose: () => void
    /** `vorschau` ist die erste Seite als JPEG – auf dem Handy viel leichter anzuzeigen als das PDF. */
    onSave: (file: File, vorschau: Blob) => Promise<void>
}

type Schritt = 'KAMERA' | 'ZUSCHNEIDEN' | 'SEITEN'

/** Was gerade zugeschnitten wird: ein neues Foto (Auslöser von Hand) oder eine vorhandene Seite. */
type Zuschnitt =
    | { art: 'neu'; fotoUrl: string }
    | { art: 'bearbeiten'; seitenId: string; fotoUrl: string; ecken: Point[] | null }

const KOPF_TITEL: Record<Schritt, string> = {
    KAMERA: '',
    ZUSCHNEIDEN: 'Ecken anpassen',
    SEITEN: 'Seiten prüfen',
}

/**
 * Dokumentenscanner wie in der Apple-Scanner-App: Die Kamera bleibt offen,
 * erkennt ein Blatt und nimmt es automatisch auf, sobald es ruhig liegt.
 * Jede Seite wird im Hintergrund zugeschnitten und verbessert. „Fertig“
 * macht aus allen Seiten ein PDF.
 */
export default function ScannerModal({ onClose, onSave }: ScannerModalProps) {
    const toast = useToast()
    const confirm = useConfirm()
    const kameraRef = useRef<CameraHandle>(null)
    const overlayRef = useRef<HTMLCanvasElement>(null)
    const [schritt, setSchritt] = useState<Schritt>('KAMERA')
    const [zuschnitt, setZuschnitt] = useState<Zuschnitt | null>(null)
    const [seitenIndex, setSeitenIndex] = useState(0)
    const [erstelltPdf, setErstelltPdfState] = useState(false)
    // Zusätzlich als Ref: Kamera-Rückrufe und Klicks im selben Takt sehen den Wert sofort.
    const erstelltPdfRef = useRef(false)
    const setErstelltPdf = useCallback((wert: boolean) => {
        erstelltPdfRef.current = wert
        setErstelltPdfState(wert)
    }, [])
    const aufnahmeLaeuftRef = useRef(false)
    // Das Kamerabild wird asynchron kodiert; schließt der Scanner in der Zeit, darf keine Seite mehr entstehen.
    const eingehaengtRef = useRef(true)
    useEffect(() => {
        eingehaengtRef.current = true
        return () => { eingehaengtRef.current = false }
    }, [])
    // Fehler-Text, wenn die Kamera nicht startet (Zugriff verweigert, kein
    // Gerät, belegt). Liegt über dem Kamerabild statt eines schwarzen Bildes.
    const [kameraFehler, setKameraFehler] = useState<string | null>(null)
    const [blitz, setBlitz] = useState(false)
    const { seiten, fuegeSeiteHinzu, passeEckenAn, setzeFilter, loescheSeite, warteAufAlle } = useScanSeiten()

    // Ein noch nicht übernommenes Foto beim Schließen freigeben.
    const zuschnittRef = useRef(zuschnitt)
    useEffect(() => { zuschnittRef.current = zuschnitt }, [zuschnitt])
    useEffect(() => () => {
        const offen = zuschnittRef.current
        if (offen?.art === 'neu') URL.revokeObjectURL(offen.fotoUrl)
    }, [])

    useEffect(() => {
        if (!blitz) return
        const timer = setTimeout(() => setBlitz(false), 120)
        return () => clearTimeout(timer)
    }, [blitz])

    /** Hält das aktuelle Kamerabild fest; Object-URL des JPEG oder `null`. */
    const nimmFotoAuf = useCallback(async (): Promise<string | null> => {
        const blob = await kameraRef.current?.getScreenshotBlob().catch(() => null)
        return blob && eingehaengtRef.current ? URL.createObjectURL(blob) : null
    }, [])

    const autoAufnahme = useCallback(async (ecken: Point[], quelle: { breite: number; hoehe: number }) => {
        if (erstelltPdfRef.current || aufnahmeLaeuftRef.current) return false
        aufnahmeLaeuftRef.current = true
        try {
            const fotoUrl = await nimmFotoAuf()
            if (!fotoUrl) return false
            setBlitz(true)
            navigator.vibrate?.(30)
            fuegeSeiteHinzu({ fotoUrl, ecken, eckenQuelle: quelle, verfeinern: true })
            return true
        } finally {
            aufnahmeLaeuftRef.current = false
        }
    }, [fuegeSeiteHinzu, nimmFotoAuf])

    const live = useLiveErkennung({
        aktiv: schritt === 'KAMERA' && !kameraFehler && !erstelltPdf,
        kameraRef,
        overlayRef,
        onAusloesen: autoAufnahme,
    })

    const aufnahmeVonHand = async () => {
        if (erstelltPdfRef.current || aufnahmeLaeuftRef.current) return
        aufnahmeLaeuftRef.current = true
        try {
            const fotoUrl = await nimmFotoAuf()
            if (!eingehaengtRef.current) return
            if (!fotoUrl) {
                toast.error('Kamera ist noch nicht bereit. Bitte einen Moment warten und noch einmal auslösen.')
                return
            }
            live.sperren()
            setBlitz(true)
            setZuschnitt({ art: 'neu', fotoUrl })
            setSchritt('ZUSCHNEIDEN')
        } finally {
            aufnahmeLaeuftRef.current = false
        }
    }

    const zuschnittZurueck = () => {
        if (zuschnitt?.art === 'neu') {
            URL.revokeObjectURL(zuschnitt.fotoUrl)
            setSchritt('KAMERA')
        } else {
            setSchritt('SEITEN')
        }
        setZuschnitt(null)
    }

    const zuschnittUebernehmen = (ecken: Point[]) => {
        if (!zuschnitt || erstelltPdfRef.current) return
        if (zuschnitt.art === 'neu') {
            fuegeSeiteHinzu({ fotoUrl: zuschnitt.fotoUrl, ecken, verfeinern: false })
            setSchritt('KAMERA')
        } else {
            passeEckenAn(zuschnitt.seitenId, ecken)
            setSchritt('SEITEN')
        }
        setZuschnitt(null)
    }

    const oeffneSeiten = () => {
        setSeitenIndex(seiten.length - 1)
        setSchritt('SEITEN')
    }

    const eckenAnpassen = (id: string) => {
        if (erstelltPdfRef.current) return
        const seite = seiten.find(s => s.id === id)
        if (!seite) return
        setZuschnitt({ art: 'bearbeiten', seitenId: id, fotoUrl: seite.fotoUrl, ecken: seite.ecken })
        setSchritt('ZUSCHNEIDEN')
    }

    const filterWaehlen = (id: string, filter: ScanFilter) => {
        if (erstelltPdfRef.current) return
        setzeFilter(id, filter)
    }

    const seiteLoeschen = async (id: string) => {
        if (erstelltPdfRef.current) return
        const nummer = seiten.findIndex(s => s.id === id) + 1
        const ok = await confirm({
            title: 'Seite löschen?',
            message: `Seite ${nummer} wird aus diesem Scan entfernt. Das lässt sich nicht rückgängig machen.`,
            confirmLabel: 'Löschen',
            variant: 'danger',
        })
        if (!ok) return
        loescheSeite(id)
        const rest = seiten.length - 1
        if (rest === 0) setSchritt('KAMERA')
        else setSeitenIndex(i => Math.min(i, rest - 1))
    }

    const schliessen = async () => {
        if (erstelltPdfRef.current) return
        if (seiten.length > 0) {
            const ok = await confirm({
                title: 'Scan verwerfen?',
                message: `${seiten.length === 1 ? 'Die gescannte Seite geht' : `Die ${seiten.length} gescannten Seiten gehen`} verloren.`,
                confirmLabel: 'Verwerfen',
                cancelLabel: 'Weiter scannen',
                variant: 'danger',
            })
            if (!ok) return
        }
        onClose()
    }

    const fertig = async () => {
        if (erstelltPdfRef.current || seiten.length === 0) return
        setErstelltPdf(true)
        try {
            // Eine gerade laufende Aufnahme gehört noch mit ins PDF: warten, bis sie eingereiht ist.
            while (aufnahmeLaeuftRef.current) await new Promise(resolve => setTimeout(resolve, 50))
            const alle = await warteAufAlle()
            const fehlerIndex = alle.findIndex(s => !s.bild || s.status === 'fehler')
            if (fehlerIndex >= 0) {
                toast.error(`Seite ${fehlerIndex + 1} konnte nicht verbessert werden. Bitte Ecken anpassen oder die Seite löschen.`)
                setSeitenIndex(fehlerIndex)
                setSchritt('SEITEN')
                return
            }
            const datei = await erstelleScanPdf(alle.map(s => ({ bild: s.bild as Blob, breite: s.breite ?? 0, hoehe: s.hoehe ?? 0 })))
            await onSave(datei, alle[0].bild as Blob)
            onClose()
        } catch (err) {
            console.error('[Scanner] PDF konnte nicht erstellt werden', err)
            toast.error('Scan konnte nicht gespeichert werden. Bitte noch einmal auf „Fertig“ tippen.')
        } finally {
            setErstelltPdf(false)
        }
    }

    const titel = schritt === 'KAMERA' ? `Seite ${seiten.length + 1} scannen` : KOPF_TITEL[schritt]

    return (
        <div
            role="dialog"
            aria-modal="true"
            aria-label="Dokument scannen"
            className="fixed inset-0 z-50 flex select-none flex-col bg-black safe-area-top touch-none"
        >
            <div className="z-40 flex items-center justify-between bg-black/80 px-2 py-2 text-white">
                <button
                    type="button"
                    onClick={schliessen}
                    disabled={erstelltPdf}
                    aria-label="Scanner schließen"
                    title={erstelltPdf ? PDF_LAEUFT : undefined}
                    className="flex h-11 w-11 items-center justify-center rounded-full hover:bg-white/10 focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-500 disabled:opacity-40"
                >
                    <X aria-hidden="true" className="h-6 w-6" />
                </button>
                <div className="flex flex-col items-center">
                    <span className="font-bold">{titel}</span>
                    {schritt === 'KAMERA' && (
                        <span className="mt-0.5 text-xs text-slate-300">Reihenfolge: 1. Scan = 1. Seite</span>
                    )}
                </div>
                <span className="h-11 w-11" aria-hidden="true" />
            </div>

            <div className="relative flex flex-1 flex-col overflow-hidden">
                {/* Die Kamera bleibt in allen Schritten offen, damit es nach dem
                    Zuschneiden oder der Seitenübersicht ohne Wartezeit weitergeht.
                    Solange ein anderer Schritt darüber liegt, ist sie nur
                    unsichtbar (nicht ausgehängt) und für Screenreader ausgeblendet. */}
                <div
                    className={cn('relative flex flex-1 flex-col overflow-hidden', schritt !== 'KAMERA' && 'invisible')}
                    aria-hidden={schritt !== 'KAMERA'}
                >
                    <div className="relative flex-1 overflow-hidden">
                        <Camera
                            ref={kameraRef}
                            screenshotQuality={1}
                            videoConstraints={{
                                facingMode: 'environment',
                                width: { min: 1920, ideal: 3840, max: 4096 },
                                height: { min: 1080, ideal: 2160, max: 2160 },
                            }}
                            className="absolute inset-0 h-full w-full object-cover"
                            onError={err => {
                                console.error('[Scanner] Kamera startet nicht', err)
                                const meldung = kameraFehlerText(err)
                                setKameraFehler(meldung)
                                toast.error(meldung)
                            }}
                        />

                        {/* Live-Rahmen: gleiche object-cover-Geometrie wie das Video,
                            daher deckt sich das Viereck mit dem sichtbaren Blatt. */}
                        <canvas
                            ref={overlayRef}
                            className="pointer-events-none absolute inset-0 z-10 h-full w-full object-cover"
                            aria-hidden="true"
                        />

                        {/* Auslöse-Blitz: kurz weiß, dann ausblenden */}
                        <div
                            aria-hidden="true"
                            data-testid="ausloese-blitz"
                            className={cn(
                                'pointer-events-none absolute inset-0 z-20 bg-white',
                                blitz ? 'opacity-70' : 'opacity-0 motion-safe:transition-opacity motion-safe:duration-300',
                            )}
                        />

                        {kameraFehler ? (
                            <div className="absolute inset-0 z-30 flex items-center justify-center bg-black/80 p-6">
                                <div className="max-w-sm rounded-2xl border border-slate-200 bg-white p-5 text-center">
                                    <p className="mb-2 font-semibold text-slate-900">Kamera nicht verfügbar</p>
                                    <p className="text-sm text-slate-600">{kameraFehler}</p>
                                </div>
                            </div>
                        ) : (
                            <LiveStatusPille erkennung={live.erkennung} phase={live.phase} anzahlSeiten={seiten.length} />
                        )}
                    </div>

                    <KameraLeiste
                        seiten={seiten}
                        onAufnehmen={aufnahmeVonHand}
                        onSeitenOeffnen={oeffneSeiten}
                        onFertig={fertig}
                        erstelltPdf={erstelltPdf}
                    />
                </div>

                {schritt === 'ZUSCHNEIDEN' && zuschnitt && (
                    <ZuschneidenAnsicht
                        key={zuschnitt.art === 'neu' ? zuschnitt.fotoUrl : zuschnitt.seitenId}
                        fotoUrl={zuschnitt.fotoUrl}
                        startEcken={zuschnitt.art === 'bearbeiten' ? zuschnitt.ecken : null}
                        onUebernehmen={zuschnittUebernehmen}
                        onZurueck={zuschnittZurueck}
                    />
                )}

                {schritt === 'SEITEN' && seiten.length > 0 && (
                    <SeitenUebersicht
                        seiten={seiten}
                        index={Math.min(seitenIndex, seiten.length - 1)}
                        onIndex={setSeitenIndex}
                        onFilter={filterWaehlen}
                        onEckenAnpassen={eckenAnpassen}
                        onLoeschen={seiteLoeschen}
                        onWeiterScannen={() => setSchritt('KAMERA')}
                        onFertig={fertig}
                        erstelltPdf={erstelltPdf}
                    />
                )}
            </div>
        </div>
    )
}
