import { useState, useEffect, useRef } from 'react'
import { QrCode, Smartphone, AlertCircle, Camera, X, Keyboard } from 'lucide-react'
import { Html5Qrcode } from 'html5-qrcode'

interface SetupPageProps {
    error?: string | null
    onTokenScanned?: (token: string) => void
}

export default function SetupPage({ error, onTokenScanned }: SetupPageProps) {
    const [scanning, setScanning] = useState(false)
    const [scanError, setScanError] = useState<string | null>(null)
    const [processing, setProcessing] = useState(false)
    const [showManualInput, setShowManualInput] = useState(false)
    const [manualToken, setManualToken] = useState('')
    const scannerRef = useRef<Html5Qrcode | null>(null)

    const startScanner = async () => {
        setScanError(null)

        // Prüfe ob Kamera verfügbar ist
        if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
            setScanError('Kamera wird von diesem Browser nicht unterstützt. Bitte Token manuell eingeben.')
            setShowManualInput(true)
            return
        }

        setScanning(true)

        try {
            const scanner = new Html5Qrcode('qr-reader')
            scannerRef.current = scanner

            await scanner.start(
                { facingMode: 'environment' },
                { fps: 10, qrbox: { width: 250, height: 250 } },
                (decodedText: string) => {
                    console.log('QR-Code gescannt:', decodedText)
                    handleQrResult(decodedText)
                },
                () => { }
            )
        } catch (err: unknown) {
            console.error('Scanner error:', err)

            // Bessere Fehlermeldungen
            const errorMessage = err instanceof Error ? err.message : String(err)

            if (errorMessage.includes('NotAllowedError') || errorMessage.includes('Permission')) {
                setScanError('Kamerazugriff wurde verweigert. Bitte in den Browser-Einstellungen erlauben, oder Token manuell eingeben.')
            } else if (errorMessage.includes('NotFoundError') || errorMessage.includes('device not found')) {
                setScanError('Keine Kamera gefunden. Bitte Token manuell eingeben.')
            } else if (errorMessage.includes('NotSupportedError')) {
                setScanError('Kamera wird nicht unterstützt. Bitte HTTPS verwenden oder Token manuell eingeben.')
            } else {
                setScanError('Kamera konnte nicht gestartet werden. Bitte Token manuell eingeben.')
            }

            setShowManualInput(true)
            setScanning(false)
        }
    }

    const stopScanner = async () => {
        if (scannerRef.current) {
            try {
                await scannerRef.current.stop()
                scannerRef.current.clear()
            } catch { }
            scannerRef.current = null
        }
        setScanning(false)
    }

    const handleQrResult = async (result: string) => {
        setProcessing(true)
        await stopScanner()

        try {
            let token = result
            if (result.includes('token=')) {
                const url = new URL(result)
                token = url.searchParams.get('token') || result
            }

            if (token && onTokenScanned) {
                onTokenScanned(token)
            } else {
                setScanError('Ungültiger QR-Code. Bitte erneut versuchen.')
                setProcessing(false)
            }
        } catch {
            setScanError('QR-Code konnte nicht gelesen werden.')
            setProcessing(false)
        }
    }

    const handleManualSubmit = () => {
        if (manualToken.trim() && onTokenScanned) {
            setProcessing(true)
            onTokenScanned(manualToken.trim())
        }
    }

    useEffect(() => {
        return () => {
            if (scannerRef.current) {
                scannerRef.current.stop().catch(() => { })
            }
        }
    }, [])

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
            <div className="min-h-screen bg-slate-900 flex flex-col">
                <div className="bg-white px-4 py-4 flex items-center justify-between">
                    <h1 className="text-slate-900 font-bold text-lg">QR-Code scannen</h1>
                    <button
                        onClick={stopScanner}
                        className="p-2 bg-slate-100 rounded-full hover:bg-slate-200 transition-colors"
                    >
                        <X className="w-5 h-5 text-slate-600" />
                    </button>
                </div>

                <div className="flex-1 flex items-center justify-center p-4">
                    <div id="qr-reader" className="w-full max-w-sm rounded-xl overflow-hidden"></div>
                </div>

                <div className="bg-white p-4 text-center">
                    <p className="text-slate-600 text-sm">
                        Halte die Kamera auf den QR-Code in der Mitarbeiterverwaltung
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
                Scanne den QR-Code oder gib das Token manuell ein.
            </p>

            {/* Scan Button */}
            <button
                onClick={startScanner}
                className="bg-rose-600 hover:bg-rose-700 text-white font-semibold py-4 px-8 rounded-xl flex items-center gap-3 transition-colors shadow-sm mb-4"
            >
                <Camera className="w-6 h-6" />
                QR-Code scannen
            </button>

            {/* Manual Input Toggle */}
            <button
                onClick={() => setShowManualInput(!showManualInput)}
                className="text-slate-500 hover:text-rose-600 text-sm flex items-center gap-2 mb-6 transition-colors"
            >
                <Keyboard className="w-4 h-4" />
                Token manuell eingeben
            </button>

            {/* Manual Token Input */}
            {showManualInput && (
                <div className="w-full max-w-sm mb-8">
                    <input
                        type="text"
                        value={manualToken}
                        onChange={(e) => setManualToken(e.target.value)}
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
                            <p className="text-slate-500 text-xs">Gehe zu deinem Profil</p>
                        </div>
                    </div>

                    <div className="flex items-start gap-4">
                        <div className="w-7 h-7 bg-rose-600 rounded-full flex items-center justify-center flex-shrink-0 text-white font-bold text-sm">
                            2
                        </div>
                        <div className="text-left">
                            <p className="text-slate-900 font-medium text-sm">Klicke auf "QR-Code"</p>
                            <p className="text-slate-500 text-xs">Der QR-Code wird angezeigt</p>
                        </div>
                    </div>

                    <div className="flex items-start gap-4">
                        <div className="w-7 h-7 bg-rose-600 rounded-full flex items-center justify-center flex-shrink-0 text-white font-bold text-sm">
                            3
                        </div>
                        <div className="text-left">
                            <p className="text-slate-900 font-medium text-sm">Scanne den QR-Code</p>
                            <p className="text-slate-500 text-xs">Mit dem Button oben</p>
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
