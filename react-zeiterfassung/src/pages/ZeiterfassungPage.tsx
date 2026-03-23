import { useState, useEffect } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { ArrowLeft, Search, Play, Loader2, ChevronRight, Briefcase, Layers, Wrench, RefreshCw } from 'lucide-react'
import { buildBookingRequestPayload, createOperationId, OfflineService } from '../services/OfflineService'

interface Projekt {
    id: number
    name: string
    projektNummer: string
    kundenName: string
    bezahlt?: boolean
    abgeschlossen?: boolean
    projektArt?: 'PAUSCHAL' | 'REGIE' | 'INTERN' | 'GARANTIE'
    projektArtDisplayName?: string
}

interface Produktkategorie {
    id: number
    name: string
}

interface Arbeitsgang {
    id: number
    beschreibung: string
}

interface ZeiterfassungPageProps {
    mitarbeiter: { id: number; name: string } | null
}

type Step = 'projekt' | 'kategorie' | 'arbeitsgang'

export default function ZeiterfassungPage({ mitarbeiter: _mitarbeiter }: ZeiterfassungPageProps) {
    const navigate = useNavigate()
    const [searchParams] = useSearchParams()
    const isSwitching = searchParams.get('switching') === 'true'
    const [step, setStep] = useState<Step>('projekt')

    // Data
    const [projekte, setProjekte] = useState<Projekt[]>([])
    const [kategorien, setKategorien] = useState<Produktkategorie[]>([])
    const [arbeitsgaenge, setArbeitsgaenge] = useState<Arbeitsgang[]>([])

    // Selections
    const [selectedProjekt, setSelectedProjekt] = useState<Projekt | null>(null)
    const [selectedKategorie, setSelectedKategorie] = useState<Produktkategorie | null>(null)
    const [selectedArbeitsgang, setSelectedArbeitsgang] = useState<Arbeitsgang | null>(null)

    // UI States
    const [searchTerm, setSearchTerm] = useState('')
    const [arbeitsgangSearchTerm, setArbeitsgangSearchTerm] = useState('')
    const [loading, setLoading] = useState(false)
    const [starting, setStarting] = useState(false)
    const [isSyncing, setIsSyncing] = useState(false)

    const handleSync = async () => {
        setIsSyncing(true)
        const minSpinTime = new Promise(resolve => setTimeout(resolve, 1000)) // Mindestens 1 volle Drehung
        try {
            await Promise.all([OfflineService.syncAll(), minSpinTime])
            // Reload current step data
            if (step === 'projekt') loadProjekte()
            else if (step === 'kategorie') loadKategorien()
            else if (step === 'arbeitsgang') loadArbeitsgaenge()
        } catch (e) {
            console.error("Sync failed", e)
        } finally {
            setIsSyncing(false)
        }
    }

    // Load projects
    useEffect(() => {
        loadProjekte()
    }, [])

    // Load categories when project is selected
    useEffect(() => {
        if (selectedProjekt) {
            loadKategorien()
        }
    }, [selectedProjekt])

    // Load Arbeitsgänge when category is selected
    useEffect(() => {
        if (selectedKategorie) {
            loadArbeitsgaenge()
        }
    }, [selectedKategorie])

    const loadProjekte = async () => {
        setLoading(true)
        const data = await OfflineService.getProjekte() as Projekt[]
        // Nur nicht-abgeschlossene Projekte für Zeiterfassung
        const offeneProjekte = data.filter(p => !p.abgeschlossen)
        setProjekte(offeneProjekte)
        setLoading(false)
    }

    const loadKategorien = async () => {
        if (!selectedProjekt) return
        setLoading(true)
        const data = await OfflineService.getKategorien(selectedProjekt.id)
        setKategorien(data as Produktkategorie[])
        setLoading(false)
    }

    const loadArbeitsgaenge = async () => {
        setLoading(true)
        const token = localStorage.getItem('zeiterfassung_token') || undefined
        const data = await OfflineService.getArbeitsgaenge(token)
        setArbeitsgaenge(data as Arbeitsgang[])
        setLoading(false)
    }

    const handleStartTracking = async () => {
        if (!selectedProjekt || !selectedArbeitsgang) return

        setStarting(true)
        const token = localStorage.getItem('zeiterfassung_token')

        // If switching from an existing booking, stop the old one first
        if (isSwitching) {
            const oldSessionStr = localStorage.getItem('zeiterfassung_active_session')
            let oldElapsedMinutes = 0
            let wasWorkSession = true
            const stopTime = new Date().toISOString()
            const stopOperationId = createOperationId()

            if (oldSessionStr) {
                try {
                    const oldSession = JSON.parse(oldSessionStr)
                    const oldStart = new Date(oldSession.startTime)
                    const now = new Date()
                    oldElapsedMinutes = Math.floor((now.getTime() - oldStart.getTime()) / 60000)
                    const isAbwesenheit = ['URLAUB', 'KRANKHEIT', 'FORTBILDUNG', 'PAUSE'].includes(oldSession.typ)
                    wasWorkSession = !isAbwesenheit
                } catch { /* ignore */ }
            }

            try {
                const stopController = new AbortController()
                const stopTimeout = setTimeout(() => stopController.abort(), 3000)

                const stopRes = await fetch('/api/zeiterfassung/stop', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(buildBookingRequestPayload({ token }, stopTime, stopOperationId)),
                    signal: stopController.signal
                })
                clearTimeout(stopTimeout)

                if (stopRes.ok) {
                    console.log('Alte Buchung für Projektwechsel gestoppt')
                    await OfflineService.clearOfflineHeuteMinuten()
                } else {
                    throw new Error('Server error on stop')
                }
            } catch {
                console.log('Offline/Timeout - speichere Stop-Event lokal')
                await OfflineService.addPendingEntryWithOperationId('stop', { token }, stopTime, stopOperationId)
                if (wasWorkSession && oldElapsedMinutes > 0) {
                    await OfflineService.addOfflineWorkedMinutes(oldElapsedMinutes)
                }
            }

            // Clear old session
            localStorage.removeItem('zeiterfassung_active_session')
        }

        const startTime = new Date().toISOString()
        const startOperationId = createOperationId()

        try {
            // Online Start Attempt with Timeout
            const controller = new AbortController()
            const timeoutId = setTimeout(() => controller.abort(), 5000)

            const res = await fetch('/api/zeiterfassung/start', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(buildBookingRequestPayload({
                    token,
                    projektId: selectedProjekt.id,
                    arbeitsgangId: selectedArbeitsgang.id,
                    produktkategorieId: selectedKategorie?.id || null
                }, startTime, startOperationId)),
                signal: controller.signal
            })
            clearTimeout(timeoutId)

            if (res.ok) {
                const data = await res.json()

                // Wenn wir switchen und der Start online erfolgreich war, bedeutet das:
                // Der Server hat keine aktive Buchung → der Stop wurde verarbeitet.
                // Verwaiste Offline-Stop-Entries entfernen, damit sie nicht später
                // die NEUE Buchung stoppen (Race-Condition-Fix).
                if (isSwitching) {
                    await OfflineService.removePendingEntriesByType('stop')
                }

                const session = {
                    id: data.id,
                    projektId: selectedProjekt.id,
                    projektName: selectedProjekt.name,
                    arbeitsgangId: selectedArbeitsgang.id,
                    arbeitsgangName: selectedArbeitsgang.beschreibung,
                    produktkategorieId: selectedKategorie?.id || null,
                    produktkategorieName: selectedKategorie?.name || null,
                    startTime: new Date().toISOString(),
                }
                localStorage.setItem('zeiterfassung_active_session', JSON.stringify(session))

            } else {
                // Server responded with error - DO NOT queue offline, it might be "already running"
                const errorData = await res.json().catch(() => ({}))
                console.error('Server error:', errorData.error || res.status)
                alert(errorData.error || 'Fehler beim Starten der Buchung')
                setStarting(false)
                return // Don't navigate, don't queue
            }
        } catch (err) {
            // Only queue offline if we're actually offline or it was a network error
            if (!navigator.onLine || (err instanceof Error && err.name === 'AbortError')) {
                console.log('Offline oder Timeout - wird später synchronisiert', err)

                // Queue sync job - mit originalem Zeitstempel!
                await OfflineService.addPendingEntryWithOperationId('start', {
                    token,
                    projektId: selectedProjekt.id,
                    arbeitsgangId: selectedArbeitsgang.id,
                    produktkategorieId: selectedKategorie?.id || null
                }, startTime, startOperationId)

                // Set local active session (fake)
                const session = {
                    id: 'offline-' + Date.now(),
                    projektId: selectedProjekt.id,
                    projektName: selectedProjekt.name,
                    kundenName: selectedProjekt.kundenName || null,
                    auftragsnummer: selectedProjekt.projektNummer || null,
                    arbeitsgangId: selectedArbeitsgang.id,
                    arbeitsgangName: selectedArbeitsgang.beschreibung,
                    produktkategorieId: selectedKategorie?.id || null,
                    produktkategorieName: selectedKategorie?.name || null,
                    startTime: new Date().toISOString(),
                }
                localStorage.setItem('zeiterfassung_active_session', JSON.stringify(session))

            } else {
                // Some other error - don't queue
                console.error('Unerwarteter Fehler:', err)
                alert('Fehler beim Starten der Buchung')
                setStarting(false)
                return
            }
        }

        navigate('/')
    }

    const filteredProjekte = projekte.filter(p =>
        p.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
        p.projektNummer?.toLowerCase().includes(searchTerm.toLowerCase()) ||
        p.kundenName?.toLowerCase().includes(searchTerm.toLowerCase())
    )

    const filteredArbeitsgaenge = arbeitsgaenge.filter(ag =>
        ag.beschreibung.toLowerCase().includes(arbeitsgangSearchTerm.toLowerCase())
    )

    const handleBack = () => {
        if (step === 'arbeitsgang') {
            setStep('kategorie')
            setSelectedArbeitsgang(null)
        } else if (step === 'kategorie') {
            setStep('projekt')
            setSelectedKategorie(null)
        } else {
            navigate('/')
        }
    }

    const getStepTitle = () => {
        switch (step) {
            case 'projekt': return 'Projekt auswählen'
            case 'kategorie': return 'Kategorie auswählen'
            case 'arbeitsgang': return 'Arbeitsgang auswählen'
        }
    }

    const getStepIcon = () => {
        switch (step) {
            case 'projekt': return <Briefcase className="w-5 h-5 text-rose-600" />
            case 'kategorie': return <Layers className="w-5 h-5 text-rose-600" />
            case 'arbeitsgang': return <Wrench className="w-5 h-5 text-rose-600" />
        }
    }

    return (
        <div className="h-full flex flex-col bg-slate-50">
            {/* Header */}
            <header className="bg-white border-b border-slate-200 px-4 py-4 safe-area-top">
                <div className="flex items-center gap-3">
                    <button
                        onClick={handleBack}
                        aria-label="Zurück"
                        title="Zurück"
                        className="p-2 hover:bg-slate-100 rounded-lg transition-all active:scale-95"
                    >
                        <ArrowLeft className="w-5 h-5 text-slate-600" />
                    </button>
                    <div className="flex items-center gap-3">
                        {getStepIcon()}
                        <div>
                            <h1 className="font-bold text-slate-900">{getStepTitle()}</h1>
                            {selectedProjekt && step !== 'projekt' && (
                                <p className="text-slate-500 text-sm">{selectedProjekt.name}</p>
                            )}
                        </div>
                    </div>
                    <button
                        onClick={handleSync}
                        aria-label="Jetzt synchronisieren"
                        title="Jetzt synchronisieren"
                        className="ml-auto p-2 hover:bg-slate-100 rounded-lg transition-all active:scale-95"
                    >
                        <RefreshCw className={`w-5 h-5 ${loading || isSyncing ? 'animate-sync-spin text-rose-600' : 'text-slate-500'}`} />
                    </button>
                </div>
            </header>

            {/* Progress Indicator */}
            <div className="flex gap-2 p-4 bg-white border-b border-slate-100">
                <div className={`flex-1 h-1.5 rounded-full ${step === 'projekt' || step === 'kategorie' || step === 'arbeitsgang' ? 'bg-rose-600' : 'bg-slate-200'}`} />
                <div className={`flex-1 h-1.5 rounded-full ${step === 'kategorie' || step === 'arbeitsgang' ? 'bg-rose-600' : 'bg-slate-200'}`} />
                <div className={`flex-1 h-1.5 rounded-full ${step === 'arbeitsgang' ? 'bg-rose-600' : 'bg-slate-200'}`} />
            </div>

            {/* Switching-Hinweis: Alte Buchung läuft noch */}
            {isSwitching && (
                <div className="mx-4 mt-3 bg-amber-50 border border-amber-200 rounded-xl p-3 flex items-center gap-2">
                    <div className="w-2 h-2 bg-amber-500 rounded-full animate-pulse flex-shrink-0" />
                    <p className="text-sm text-amber-800">
                        Laufende Buchung bleibt aktiv bis zur neuen Auswahl. Bei Abbruch läuft sie weiter.
                    </p>
                </div>
            )}

            {/* Content */}
            <div className="flex-1 overflow-auto">
                {/* Step 1: Project Selection */}
                {step === 'projekt' && (
                    <div className="p-4 space-y-3">
                        {/* Search */}
                        <div className="relative">
                            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-slate-400" />
                            <input
                                type="text"
                                value={searchTerm}
                                onChange={(e) => setSearchTerm(e.target.value)}
                                placeholder="Projekt suchen..."
                                className="w-full pl-10 pr-4 py-3 bg-white border border-slate-200 rounded-xl text-slate-900 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-rose-500 focus:border-rose-500"
                            />
                        </div>

                        {loading ? (
                            <div className="flex items-center justify-center py-12">
                                <Loader2 className="w-6 h-6 animate-spin text-rose-600" />
                            </div>
                        ) : (
                            <div className="space-y-2">
                                {filteredProjekte.map(projekt => (
                                    <button
                                        key={projekt.id}
                                        onClick={() => {
                                            setSelectedProjekt(projekt)
                                            setStep('kategorie')
                                        }}
                                        className="w-full bg-white border border-slate-200 rounded-xl p-4 flex items-center justify-between hover:border-rose-200 hover:shadow-sm transition-all text-left"
                                    >
                                        <div>
                                            <div className="flex items-center gap-2">
                                                <p className="font-medium text-slate-900">{projekt.name}</p>
                                                {projekt.projektArt && (
                                                    <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium ${
                                                        projekt.projektArt === 'PAUSCHAL' ? 'bg-blue-100 text-blue-700' :
                                                        projekt.projektArt === 'REGIE' ? 'bg-emerald-100 text-emerald-700' :
                                                        projekt.projektArt === 'INTERN' ? 'bg-slate-100 text-slate-600' :
                                                        projekt.projektArt === 'GARANTIE' ? 'bg-amber-100 text-amber-700' :
                                                        'bg-slate-100 text-slate-600'
                                                    }`}>
                                                        {projekt.projektArtDisplayName || projekt.projektArt}
                                                    </span>
                                                )}
                                            </div>
                                            <p className="text-sm text-slate-500">{projekt.kundenName}</p>
                                            {projekt.projektNummer && (
                                                <p className="text-xs text-slate-400 mt-1">#{projekt.projektNummer}</p>
                                            )}
                                        </div>
                                        <ChevronRight className="w-5 h-5 text-slate-400" />
                                    </button>
                                ))}
                            </div>
                        )}
                    </div>
                )}

                {/* Step 2: Category Selection */}
                {step === 'kategorie' && (
                    <div className="p-4 space-y-3">
                        {loading ? (
                            <div className="flex items-center justify-center py-12">
                                <Loader2 className="w-6 h-6 animate-spin text-rose-600" />
                            </div>
                        ) : (
                            <>
                                {/* Skip Button */}
                                <button
                                    onClick={() => {
                                        setSelectedKategorie(null)
                                        setStep('arbeitsgang')
                                        loadArbeitsgaenge()
                                    }}
                                    className="w-full bg-slate-100 border border-slate-200 rounded-xl p-4 text-center text-slate-600 hover:bg-slate-200 transition-colors"
                                >
                                    Ohne Kategorie fortfahren →
                                </button>

                                <div className="space-y-2">
                                    {kategorien.map(kategorie => (
                                        <button
                                            key={kategorie.id}
                                            onClick={() => {
                                                setSelectedKategorie(kategorie)
                                                setStep('arbeitsgang')
                                            }}
                                            className="w-full bg-white border border-slate-200 rounded-xl p-4 flex items-center justify-between hover:border-rose-200 hover:shadow-sm transition-all text-left"
                                        >
                                            <div className="flex items-center gap-3">
                                                <div className="w-10 h-10 bg-rose-50 rounded-lg flex items-center justify-center">
                                                    <Layers className="w-5 h-5 text-rose-600" />
                                                </div>
                                                <span className="font-medium text-slate-900">{kategorie.name}</span>
                                            </div>
                                            <ChevronRight className="w-5 h-5 text-slate-400" />
                                        </button>
                                    ))}
                                </div>
                            </>
                        )}
                    </div>
                )}

                {/* Step 3: Arbeitsgang Selection */}
                {step === 'arbeitsgang' && (
                    <div className="flex flex-col h-full">
                        {/* Search for activities */}
                        <div className="p-4 bg-white border-b border-slate-100">
                            <div className="relative">
                                <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-slate-400" />
                                <input
                                    type="text"
                                    value={arbeitsgangSearchTerm}
                                    onChange={(e) => setArbeitsgangSearchTerm(e.target.value)}
                                    placeholder="Tätigkeit suchen..."
                                    className="w-full pl-10 pr-4 py-3 bg-white border border-slate-200 rounded-xl text-slate-900 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-rose-500 focus:border-rose-500"
                                />
                            </div>
                        </div>

                        {/* Scrollable activity list */}
                        <div className="flex-1 overflow-auto p-4 pb-24">
                            {loading ? (
                                <div className="flex items-center justify-center py-12">
                                    <Loader2 className="w-6 h-6 animate-spin text-rose-600" />
                                </div>
                            ) : (
                                <div className="space-y-2">
                                    {filteredArbeitsgaenge.map(ag => (
                                        <button
                                            key={ag.id}
                                            onClick={() => setSelectedArbeitsgang(ag)}
                                            className={`w-full bg-white border rounded-xl p-4 flex items-center justify-between transition-all text-left ${selectedArbeitsgang?.id === ag.id
                                                ? 'border-rose-500 bg-rose-50 shadow-sm'
                                                : 'border-slate-200 hover:border-rose-200 hover:shadow-sm'
                                                }`}
                                        >
                                            <div className="flex items-center gap-3">
                                                <div className={`w-10 h-10 rounded-lg flex items-center justify-center ${selectedArbeitsgang?.id === ag.id ? 'bg-rose-600' : 'bg-slate-100'
                                                    }`}>
                                                    <Wrench className={`w-5 h-5 ${selectedArbeitsgang?.id === ag.id ? 'text-white' : 'text-slate-500'
                                                        }`} />
                                                </div>
                                                <span className="font-medium text-slate-900">{ag.beschreibung}</span>
                                            </div>
                                            {selectedArbeitsgang?.id === ag.id && (
                                                <div className="w-5 h-5 bg-rose-600 rounded-full flex items-center justify-center">
                                                    <span className="text-white text-xs">✓</span>
                                                </div>
                                            )}
                                        </button>
                                    ))}
                                </div>
                            )}
                        </div>
                    </div>
                )}
            </div>

            {/* Start Button (only in step 3) - Fixed at bottom */}
            {step === 'arbeitsgang' && selectedArbeitsgang && (
                <div className="fixed bottom-0 left-0 right-0 p-4 bg-white border-t border-slate-200 safe-area-bottom shadow-lg">
                    <button
                        onClick={handleStartTracking}
                        disabled={starting}
                        className="w-full bg-rose-600 hover:bg-rose-700 text-white font-semibold py-4 rounded-xl flex items-center justify-center gap-2 transition-colors disabled:opacity-50"
                    >
                        {starting ? (
                            <Loader2 className="w-5 h-5 animate-spin" />
                        ) : (
                            <>
                                <Play className="w-5 h-5" />
                                {isSwitching ? 'Auftrag wechseln' : 'Zeiterfassung starten'}
                            </>
                        )}
                    </button>
                </div>
            )}
        </div>
    )
}
