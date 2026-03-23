import { useState, useEffect, useRef } from 'react'
import { QrCode, Smartphone, AlertCircle, Keyboard, Camera, X } from 'lucide-react'
import { Html5Qrcode } from 'html5-qrcode'

interface SetupPageProps {
    error?: string | null
    onTokenScanned?: (token: string) => void
}

export default function SetupPage({ error, onTokenScanned }: SetupPageProps) {
    const [processing, setProcessing] = useState(false)
    const [showManualInput, setShowManualInput] = useState(false)
    const [manualToken, setManualToken] = useState('')
    const [scanning, setScanning] = useState(false)
    const [scanError, setScanError] = useState<string | null>(null)
    const scannerRef = useRef<Html5Qrcode | null>(null)

    useEffect(() => {
        return () => {
            stopScanner()
        }
    }, [])

    const stopScanner = async () => {
        if (scannerRef.current) {
            try {
                const state = scannerRef.current.getState()
                // State 2 = SCANNING, state 3 = PAUSED
                if (state === 2 || state === 3) {
                    await scannerRef.current.stop()
                }
                scannerRef.current.clear()
            } catch { /* ignore cleanup errors */ }
            scannerRef.current = null
        }
    }

    const startScanner = async () => {
        setScanError(null)
        setScanning(true)
        setShowManualInput(false)

        // Wait for DOM element to be rendered
        await new Promise(resolve => setTimeout(resolve, 150))

        try {
            const scanner = new Html5Qrcode('qr-reader')
            scannerRef.current = scanner

            await scanner.start(
                { facingMode: 'environment' },
                { fps: 10, qrbox: { width: 250, height: 250 } },
                (decodedText) => {
                    // Extract token from URL or use raw value
                    let token = decodedText.trim()
                    try {
                        const url = new URL(decodedText)
                        const urlToken = url.searchParams.get('token')
                        if (urlToken) token = urlToken
                    } catch { /* not a URL, use as-is */ }

                    stopScanner().then(() => {
                        setScanning(false)
                        setProcessing(true)
                        if (onTokenScanned) onTokenScanned(token)
                    })
                },
                () => { /* ignore per-frame decode errors */ }
            )
        } catch (err) {
            console.error('Camera error:', err)
            setScanning(false)
            setScanError('Kamera konnte nicht geöffnet werden. Bitte Kamera-Berechtigung erteilen oder Token manuell eingeben.')
        }
    }

    const handleStopScanner = async () => {
        await stopScanner()
        setScanning(false)
    }

    const handleManualSubmit = () => {
        if (manualToken.trim() && onTokenScanned) {
            setProcessing(true)
            onTokenScanned(manualToken.trim())
        }
    }

    if (processing) {
        return (
            <div className="min-h-screen bg-white flex flex-col items-center justify-center p-6">
                <div className="w-12 h-12 border-4 border-rose-600 border-t-transparent rounded-full animate-spin mb-4"></div>
                <p className="text-slate-700 text-lg">Anmeldung wird verarbeitet...</p>
            </div>
        )
    }

    if (scanning) {
        return (
            <div className="min-h-screen bg-black flex flex-col items-center justify-center">
                <div className="w-full max-w-sm">
                    <div className="flex items-center justify-between px-4 py-3">
                        <p className="text-white font-medium">QR-Code scannen</p>
                        <button
                            type="button"
                            onClick={handleStopScanner}
                            title="Scanner schließen"
                            aria-label="Scanner schließen"
                            className="w-9 h-9 bg-white/20 hover:bg-white/30 rounded-full flex items-center justify-center transition-colors"
                        >
                            <X className="w-5 h-5 text-white" />
                        </button>
                    </div>
                    <div id="qr-reader" className="w-full" />
                    <p className="text-white/60 text-sm text-center mt-4 px-4">
                        Halte die Kamera auf den QR-Code deines Profils
                    </p>
                </div>
            </div>
        )
    }

    return (
        <div className="min-h-screen bg-white flex flex-col items-center justify-center p-6 text-center">
            {/* Logo */}
            <div className="w-24 h-24 bg-rose-600 rounded-2xl flex items-center justify-center mb-6 shadow-lg">
                <QrCode className="w-12 h-12 text-white" />
            </div>

            <h1 className="text-2xl font-bold text-slate-900 mb-2">Zeiterfassung</h1>

            {/* Error Message */}
            {(error || scanError) && (
                <div className="bg-rose-50 border border-rose-200 rounded-xl p-4 mb-6 max-w-sm w-full flex items-start gap-3">
                    <AlertCircle className="w-5 h-5 text-rose-600 flex-shrink-0 mt-0.5" />
                    <p className="text-rose-700 text-sm text-left">{error || scanError}</p>
                </div>
            )}

            <p className="text-slate-500 mb-8 max-w-xs">
                Scanne deinen persönlichen QR-Code oder gib das Token manuell ein.
            </p>

            {/* Primary: Scan with camera */}
            <button
                type="button"
                onClick={startScanner}
                className="w-full max-w-sm bg-rose-600 hover:bg-rose-700 active:bg-rose-800 text-white font-semibold py-4 rounded-2xl flex items-center justify-center gap-3 mb-4 shadow-md transition-colors"
            >
                <Camera className="w-5 h-5" />
                Kamera öffnen &amp; QR-Code scannen
            </button>

            {/* Secondary: Manual Token Input */}
            <button
                onClick={() => setShowManualInput(!showManualInput)}
                className="text-slate-500 hover:text-rose-600 text-sm flex items-center gap-2 mb-4 transition-colors"
            >
                <Keyboard className="w-4 h-4" />
                Token manuell eingeben
            </button>

            {showManualInput && (
                <div className="w-full max-w-sm mb-8">
                    <input
                        type="text"
                        value={manualToken}
                        onChange={(e) => setManualToken(e.target.value)}
                        onKeyDown={(e) => e.key === 'Enter' && handleManualSubmit()}
                        placeholder="Token eingeben..."
                        className="w-full px-4 py-3 border border-slate-200 rounded-xl text-slate-900 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-rose-500 focus:border-rose-500 mb-3"
                    />
                    <button
                        onClick={handleManualSubmit}
                        disabled={!manualToken.trim()}
                        className="w-full bg-slate-900 hover:bg-slate-800 text-white font-semibold py-3 rounded-xl transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                    >
                        Anmelden
                    </button>
                </div>
            )}

            {/* Instructions Card */}
            {!showManualInput && (
                <div className="bg-slate-50 border border-slate-200 rounded-2xl p-6 max-w-sm w-full space-y-4">
                    <p className="text-sm font-medium text-slate-700 text-left mb-4">So funktioniert's:</p>

                    <div className="flex items-start gap-4">
                        <div className="w-7 h-7 bg-rose-600 rounded-full flex items-center justify-center flex-shrink-0 text-white font-bold text-sm">
                            1
                        </div>
                        <div className="text-left">
                            <p className="text-slate-900 font-medium text-sm">Öffne die Mitarbeiterverwaltung</p>
                            <p className="text-slate-500 text-xs">Gehe zu deinem Profil am PC</p>
                        </div>
                    </div>

                    <div className="flex items-start gap-4">
                        <div className="w-7 h-7 bg-rose-600 rounded-full flex items-center justify-center flex-shrink-0 text-white font-bold text-sm">
                            2
                        </div>
                        <div className="text-left">
                            <p className="text-slate-900 font-medium text-sm">Klicke auf "QR-Code anzeigen"</p>
                            <p className="text-slate-500 text-xs">Der QR-Code wird am Bildschirm angezeigt</p>
                        </div>
                    </div>

                    <div className="flex items-start gap-4">
                        <div className="w-7 h-7 bg-rose-600 rounded-full flex items-center justify-center flex-shrink-0 text-white font-bold text-sm">
                            3
                        </div>
                        <div className="text-left">
                            <p className="text-slate-900 font-medium text-sm">Tippe auf "Kamera öffnen"</p>
                            <p className="text-slate-500 text-xs">Halte dein Handy auf den QR-Code</p>
                        </div>
                    </div>
                </div>
            )}

            {/* Hint */}
            <div className="mt-8 flex items-center gap-2 text-slate-400 text-sm">
                <Smartphone className="w-4 h-4" />
                <span>Danach bist du dauerhaft angemeldet</span>
            </div>
        </div>
    )
}
