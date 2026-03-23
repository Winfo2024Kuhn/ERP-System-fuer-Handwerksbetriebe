import { useState } from 'react'
import { QrCode, Smartphone, AlertCircle, Keyboard } from 'lucide-react'

interface SetupPageProps {
    error?: string | null
    onTokenScanned?: (token: string) => void
}

export default function SetupPage({ error, onTokenScanned }: SetupPageProps) {
    const [processing, setProcessing] = useState(false)
    const [showManualInput, setShowManualInput] = useState(false)
    const [manualToken, setManualToken] = useState('')

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

    return (
        <div className="min-h-screen bg-white flex flex-col items-center justify-center p-6 text-center">
            {/* Logo */}
            <div className="w-24 h-24 bg-rose-600 rounded-2xl flex items-center justify-center mb-6 shadow-lg">
                <QrCode className="w-12 h-12 text-white" />
            </div>

            <h1 className="text-2xl font-bold text-slate-900 mb-2">Zeiterfassung</h1>

            {/* Error Message */}
            {error && (
                <div className="bg-rose-50 border border-rose-200 rounded-xl p-4 mb-6 max-w-sm w-full flex items-start gap-3">
                    <AlertCircle className="w-5 h-5 text-rose-600 flex-shrink-0 mt-0.5" />
                    <p className="text-rose-700 text-sm text-left">{error}</p>
                </div>
            )}

            <p className="text-slate-500 mb-8 max-w-xs">
                Scanne den QR-Code mit deiner Kamera-App oder gib das Token manuell ein.
            </p>

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
                            <p className="text-slate-900 font-medium text-sm">Scanne mit der Kamera-App</p>
                            <p className="text-slate-500 text-xs">Öffne die Kamera und halte sie auf den QR-Code</p>
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
