import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ArrowLeft, Flashlight, FlashlightOff, Keyboard } from 'lucide-react'
import { Html5Qrcode } from 'html5-qrcode'

export default function BetriebsmittelScanPage() {
    const navigate = useNavigate()
    const [error, setError] = useState<string | null>(null)
    const [manualMode, setManualMode] = useState(false)
    const [manualCode, setManualCode] = useState('')
    const [searching, setSearching] = useState(false)
    const [torchOn, setTorchOn] = useState(false)
    const scannerRef = useRef<Html5Qrcode | null>(null)
    const processingRef = useRef(false)

    useEffect(() => {
        if (manualMode) return

        const scanner = new Html5Qrcode('qr-reader')
        scannerRef.current = scanner

        scanner.start(
            { facingMode: 'environment' },
            { fps: 10, qrbox: { width: 250, height: 250 } },
            (decodedText) => {
                if (processingRef.current) return
                processingRef.current = true
                lookupBarcode(decodedText)
            },
            () => { /* ignore scan failures */ }
        ).catch(() => {
            setError('Kamera konnte nicht gestartet werden. Bitte Berechtigung prüfen.')
            setManualMode(true)
        })

        return () => {
            if (scanner.isScanning) {
                scanner.stop().catch(() => {})
            }
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [manualMode])

    const toggleTorch = async () => {
        const scanner = scannerRef.current
        if (!scanner) return
        try {
            const track = (scanner as unknown as { getRunningTrackSettings?: () => MediaTrackSettings })
            if (track.getRunningTrackSettings) {
                // html5-qrcode doesn't expose torch control directly,
                // but underlying MediaStreamTrack supports it on Android Chrome
                const videoElement = document.querySelector('#qr-reader video') as HTMLVideoElement | null
                if (videoElement?.srcObject) {
                    const stream = videoElement.srcObject as MediaStream
                    const videoTrack = stream.getVideoTracks()[0]
                    if (videoTrack) {
                        await videoTrack.applyConstraints({ advanced: [{ torch: !torchOn } as MediaTrackConstraintSet] } as MediaTrackConstraints)
                        setTorchOn(!torchOn)
                    }
                }
            }
        } catch {
            // Torch not supported on this device
        }
    }

    const lookupBarcode = async (barcode: string) => {
        setSearching(true)
        setError(null)
        const token = localStorage.getItem('zeiterfassung_token')
        try {
            const res = await fetch(`/api/betriebsmittel/barcode/${encodeURIComponent(barcode)}?token=${encodeURIComponent(token || '')}`)
            if (res.ok) {
                const geraet = await res.json()
                navigate(`/betriebsmittel/${geraet.id}/pruefung`, { replace: true })
            } else if (res.status === 404) {
                setError(`Kein Gerät mit Barcode "${barcode}" gefunden.`)
                processingRef.current = false
            } else {
                setError('Serverfehler. Bitte erneut versuchen.')
                processingRef.current = false
            }
        } catch {
            setError('Keine Verbindung zum Server.')
            processingRef.current = false
        } finally {
            setSearching(false)
        }
    }

    const handleManualSubmit = () => {
        if (!manualCode.trim()) return
        lookupBarcode(manualCode.trim())
    }

    return (
        <div className="min-h-screen bg-black flex flex-col">
            {/* Header */}
            <div className="sticky top-0 z-10 bg-black/80 backdrop-blur px-4 py-3 safe-area-top flex items-center gap-3">
                <button onClick={() => navigate('/betriebsmittel')} className="p-2 -ml-2 rounded-lg hover:bg-white/10">
                    <ArrowLeft className="w-5 h-5 text-white" />
                </button>
                <h1 className="text-white font-bold flex-1">Barcode scannen</h1>
                {!manualMode && (
                    <>
                        <button onClick={toggleTorch} className="p-2 rounded-lg hover:bg-white/10">
                            {torchOn
                                ? <FlashlightOff className="w-5 h-5 text-white" />
                                : <Flashlight className="w-5 h-5 text-white" />
                            }
                        </button>
                        <button onClick={() => setManualMode(true)} className="p-2 rounded-lg hover:bg-white/10">
                            <Keyboard className="w-5 h-5 text-white" />
                        </button>
                    </>
                )}
            </div>

            {/* Scanner or Manual Input */}
            <div className="flex-1 flex flex-col items-center justify-center px-4">
                {!manualMode ? (
                    <>
                        <div id="qr-reader" className="w-full max-w-sm rounded-2xl overflow-hidden" />
                        <p className="text-white/60 text-sm mt-4 text-center">
                            Barcode oder QR-Code des Geräts scannen
                        </p>
                    </>
                ) : (
                    <div className="w-full max-w-sm space-y-4">
                        <p className="text-white text-sm text-center">Barcode / Seriennummer manuell eingeben</p>
                        <input
                            type="text"
                            value={manualCode}
                            onChange={e => setManualCode(e.target.value)}
                            onKeyDown={e => e.key === 'Enter' && handleManualSubmit()}
                            placeholder="Barcode oder Seriennummer..."
                            autoFocus
                            className="w-full px-4 py-3 rounded-xl bg-white/10 border border-white/20 text-white placeholder-white/40 text-center text-lg focus:outline-none focus:ring-2 focus:ring-rose-500"
                        />
                        <button
                            onClick={handleManualSubmit}
                            disabled={!manualCode.trim() || searching}
                            className="w-full py-3 rounded-xl bg-rose-600 text-white font-semibold hover:bg-rose-700 disabled:opacity-50 transition-colors"
                        >
                            {searching ? 'Suche...' : 'Gerät suchen'}
                        </button>
                        <button
                            onClick={() => { setManualMode(false); setError(null); processingRef.current = false }}
                            className="w-full py-2 text-white/60 text-sm hover:text-white transition-colors"
                        >
                            Zurück zum Scanner
                        </button>
                    </div>
                )}

                {/* Error */}
                {error && (
                    <div className="mt-4 bg-red-500/20 border border-red-500/30 rounded-xl px-4 py-3 max-w-sm w-full">
                        <p className="text-red-200 text-sm text-center">{error}</p>
                    </div>
                )}

                {/* Loading */}
                {searching && (
                    <div className="mt-4 flex items-center gap-2 text-white/60">
                        <div className="w-4 h-4 border-2 border-white/40 border-t-white rounded-full animate-spin" />
                        <span className="text-sm">Gerät wird gesucht...</span>
                    </div>
                )}
            </div>
        </div>
    )
}
