import { useToast } from '../components/ui/toast'
import { useState, useEffect } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { ArrowLeft, Search, Play, Loader2, ChevronRight, Briefcase, Layers, Wrench, RefreshCw } from 'lucide-react'
import { createOperationId, OfflineService, sendeBuchung, startZeitAusErgebnis } from '../services/OfflineService'
import { hatEingerichtetesZeitkonto, zeitkontoHinweis, type ZeitkontoStatus } from '../types/zeitkonto'

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

export default function ZeiterfassungPage(props: ZeiterfassungPageProps) {
    void props
    const toast = useToast()
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
    const [zeitkontoStatus, setZeitkontoStatus] = useState<ZeitkontoStatus | null>(null)
    const [statusLoading, setStatusLoading] = useState(true)
    const [statusError, setStatusError] = useState<string | null>(null)

    const holeZeitkontoStatus = (token: string) => fetch(`/api/zeiterfassung/buchungszeitfenster/${encodeURIComponent(token)}`)
        .then(res => {
            if (!res.ok) throw new Error()
            return res.json() as Promise<ZeitkontoStatus>
        })
        .then(data => {
            if (typeof data.fuehrtZeitkonto !== 'boolean' || typeof data.eingerichtet !== 'boolean') throw new Error()
            setZeitkontoStatus(data)
        })
        .catch(() => {
            setZeitkontoStatus(null)
            setStatusError('Die Arbeitszeit-Einrichtung konnte nicht geprüft werden.')
        })
        .finally(() => setStatusLoading(false))

    // Erneut-versuchen-Knopf; beim Öffnen startet statusLoading schon mit true.
    const loadZeitkontoStatus = () => {
        const token = localStorage.getItem('zeiterfassung_token')
        if (!token) return
        setStatusLoading(true)
        setStatusError(null)
        void holeZeitkontoStatus(token)
    }

    // Die Ladefunktionen setzen nur das Ergebnis; die Ladeanzeige schaltet an, wer das Laden
    // auslöst (Auswahl-Tipp, Abgleich, fertige Einrichtungsprüfung).
    const loadProjekte = () => OfflineService.getProjekte().then(data => {
        // Nur nicht-abgeschlossene Projekte für Zeiterfassung
        setProjekte((data as Projekt[]).filter(p => !p.abgeschlossen))
        setLoading(false)
    })

    const loadKategorien = (projekt: Projekt | null = selectedProjekt) => {
        if (!projekt) return Promise.resolve()
        return OfflineService.getKategorien(projekt.id).then(data => {
            setKategorien(data as Produktkategorie[])
            setLoading(false)
        })
    }

    const loadArbeitsgaenge = () => {
        const token = localStorage.getItem('zeiterfassung_token') || undefined
        return OfflineService.getArbeitsgaenge(token).then(data => {
            setArbeitsgaenge(data as Arbeitsgang[])
            setLoading(false)
        })
    }

    const handleSync = async () => {
        setIsSyncing(true)
        const minSpinTime = new Promise(resolve => setTimeout(resolve, 1000)) // Mindestens 1 volle Drehung
        try {
            await Promise.all([OfflineService.syncAll(), minSpinTime])
            // Reload current step data
            if (step === 'projekt' || step === 'kategorie' || step === 'arbeitsgang') setLoading(true)
            if (step === 'projekt') loadProjekte()
            else if (step === 'kategorie') loadKategorien()
            else if (step === 'arbeitsgang') loadArbeitsgaenge()
        } catch (e) {
            console.error("Sync failed", e)
        } finally {
            setIsSyncing(false)
        }
    }

    useEffect(() => {
        const token = localStorage.getItem('zeiterfassung_token')
        if (token) void holeZeitkontoStatus(token)
    }, [])

    // Load projects – sobald die Einrichtung geprüft ist; die Ladeanzeige geht schon beim Rendern an.
    const zeitkontoEingerichtet = hatEingerichtetesZeitkonto(zeitkontoStatus)
    const [projekteFuerEinrichtung, setProjekteFuerEinrichtung] = useState(false)
    if (projekteFuerEinrichtung !== zeitkontoEingerichtet) {
        setProjekteFuerEinrichtung(zeitkontoEingerichtet)
        if (zeitkontoEingerichtet) setLoading(true)
    }

    useEffect(() => {
        if (hatEingerichtetesZeitkonto(zeitkontoStatus)) void loadProjekte()
    }, [zeitkontoStatus])


    const handleStartTracking = async () => {
        if (!selectedProjekt || !selectedArbeitsgang) return

        setStarting(true)
        const token = localStorage.getItem('zeiterfassung_token')

        try {
            // Beim Wechsel zuerst die alte Buchung beenden. Über sendeBuchung geht das
            // nie an älteren Offline-Einträgen vorbei; wird der Stop eingereiht, reiht
            // sich der Start unten automatisch dahinter ein. Lehnt der Server den Stop
            // ab ("keine aktive Buchung"), läuft dort nichts mehr – dann einfach starten.
            if (isSwitching) {
                const oldSessionStr = localStorage.getItem('zeiterfassung_active_session')
                let oldElapsedMinutes = 0
                let wasWorkSession = true

                if (oldSessionStr) {
                    try {
                        const oldSession = JSON.parse(oldSessionStr)
                        oldElapsedMinutes = Math.floor((Date.now() - new Date(oldSession.startTime).getTime()) / 60000)
                        wasWorkSession = !['URLAUB', 'KRANKHEIT', 'FORTBILDUNG', 'PAUSE'].includes(oldSession.typ)
                    } catch { /* ignore */ }
                }

                await sendeBuchung('stop', { token }, {
                    originalTime: new Date().toISOString(),
                    operationId: createOperationId(),
                    // Arbeitszeit der beendeten Buchung, damit "heute gearbeitet"
                    // stimmt, solange der Stop noch wartet.
                    durationMinutes: wasWorkSession && oldElapsedMinutes > 0 ? oldElapsedMinutes : undefined,
                    ablehnungInReparaturListe: false,
                })

                // Clear old session
                localStorage.removeItem('zeiterfassung_active_session')
            }

            const startTime = new Date().toISOString()
            const start = await sendeBuchung('start', {
                token,
                projektId: selectedProjekt.id,
                arbeitsgangId: selectedArbeitsgang.id,
                produktkategorieId: selectedKategorie?.id || null
            }, {
                originalTime: startTime,
                operationId: createOperationId(),
                ablehnungInReparaturListe: false,
            })

            if (start.art === 'abgelehnt') {
                // Echte Ablehnung (z. B. "Es läuft bereits eine Buchung") – nicht
                // einreihen, nicht weiter: Der Handwerker soll es sofort sehen.
                console.error('Server error:', start.status, start.meldung)
                toast.error(start.meldung)
                setStarting(false)
                return
            }

            // 'ok' oder 'eingereiht' (Funkloch, Zeitlimit, Server ausgelastet):
            // Die Buchung läuft auf dem Handy, die Warteschlange überträgt sie später.
            const session = {
                id: start.art === 'ok' ? start.daten.id ?? null : 'offline-' + Date.now(),
                projektId: selectedProjekt.id,
                projektName: selectedProjekt.name,
                kundenName: selectedProjekt.kundenName || null,
                auftragsnummer: selectedProjekt.projektNummer || null,
                arbeitsgangId: selectedArbeitsgang.id,
                arbeitsgangName: selectedArbeitsgang.beschreibung,
                produktkategorieId: selectedKategorie?.id || null,
                produktkategorieName: selectedKategorie?.name || null,
                startTime: startZeitAusErgebnis(start, startTime),
            }
            localStorage.setItem('zeiterfassung_active_session', JSON.stringify(session))
        } catch (err) {
            console.error('Buchung konnte nicht gespeichert werden:', err)
            toast.error('Die Buchung konnte auf dem Handy nicht gespeichert werden. Bitte noch einmal versuchen.')
            setStarting(false)
            return
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
                {/* min-w-0 + break-words: Ein langes Bauvorhaben bricht im Kopf um, statt ihn
                    breiter als den Bildschirm zu machen (sonst liess sich die Seite seitlich schieben). */}
                <div className="flex items-center gap-3">
                    <button
                        onClick={handleBack}
                        aria-label="Zurück"
                        title="Zurück"
                        className="flex-shrink-0 p-2 hover:bg-slate-100 rounded-lg transition-all active:scale-95"
                    >
                        <ArrowLeft className="w-5 h-5 text-slate-600" />
                    </button>
                    <div className="flex min-w-0 items-center gap-3">
                        <span className="flex-shrink-0">{getStepIcon()}</span>
                        <div className="min-w-0">
                            <h1 className="font-bold text-slate-900">{getStepTitle()}</h1>
                            {selectedProjekt && step !== 'projekt' && (
                                <p className="text-slate-500 text-sm break-words">{selectedProjekt.name}</p>
                            )}
                        </div>
                    </div>
                    <button
                        onClick={handleSync}
                        aria-label="Jetzt synchronisieren"
                        title="Jetzt synchronisieren"
                        className="ml-auto flex-shrink-0 p-2 hover:bg-slate-100 rounded-lg transition-all active:scale-95"
                    >
                        <RefreshCw className={`w-5 h-5 ${loading || isSyncing ? 'animate-sync-spin text-rose-600' : 'text-slate-500'}`} />
                    </button>
                </div>
            </header>

            {!statusLoading && !hatEingerichtetesZeitkonto(zeitkontoStatus) ? (
                <main className="flex flex-1 items-center p-4">
                    <div className="w-full rounded-2xl border border-amber-200 bg-amber-50 p-5 shadow-sm" role="alert">
                        <h2 className="font-bold text-slate-900">Keine neue Zeitbuchung möglich</h2>
                        <p className="mt-2 text-sm leading-6 text-slate-700">{zeitkontoHinweis(zeitkontoStatus)}</p>
                        {statusError && <p className="mt-2 text-sm text-amber-800">{statusError}</p>}
                        <button onClick={loadZeitkontoStatus} className="mt-4 rounded-lg border border-rose-300 px-3 py-2 text-sm font-semibold text-rose-700 hover:bg-rose-50">
                            Erneut prüfen
                        </button>
                    </div>
                </main>
            ) : statusLoading ? (
                <main className="flex flex-1 items-center justify-center" aria-label="Arbeitszeit-Einrichtung wird geprüft">
                    <Loader2 className="h-7 w-7 animate-spin text-rose-600" aria-hidden="true" />
                </main>
            ) : <>

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
            {/* break-words: lange Woerter (Bauvorhaben, Arbeitsgang) brechen um, statt seitlich ueberzustehen */}
            <div className="flex-1 overflow-y-auto overflow-x-hidden break-words">
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
                                            // Kategorien des gewählten Projekts laden
                                            setLoading(true)
                                            setSelectedProjekt(projekt)
                                            setStep('kategorie')
                                            void loadKategorien(projekt)
                                        }}
                                        className="w-full bg-white border border-slate-200 rounded-xl p-4 flex items-center justify-between hover:border-rose-200 hover:shadow-sm transition-all text-left"
                                    >
                                        <div className="min-w-0 flex-1">
                                            <div className="flex items-center gap-2">
                                                <p className="min-w-0 font-medium text-slate-900">{projekt.name}</p>
                                                {projekt.projektArt && (
                                                    <span className={`inline-flex flex-shrink-0 items-center px-2 py-0.5 rounded-full text-xs font-medium ${
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
                                        <ChevronRight className="w-5 h-5 flex-shrink-0 text-slate-400" />
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
                                                // Arbeitsgänge laden, sobald eine Kategorie gewählt ist
                                                setLoading(true)
                                                setSelectedKategorie(kategorie)
                                                setStep('arbeitsgang')
                                                void loadArbeitsgaenge()
                                            }}
                                            className="w-full bg-white border border-slate-200 rounded-xl p-4 flex items-center justify-between hover:border-rose-200 hover:shadow-sm transition-all text-left"
                                        >
                                            <div className="flex min-w-0 items-center gap-3">
                                                <div className="w-10 h-10 flex-shrink-0 bg-rose-50 rounded-lg flex items-center justify-center">
                                                    <Layers className="w-5 h-5 text-rose-600" />
                                                </div>
                                                <span className="min-w-0 font-medium text-slate-900">{kategorie.name}</span>
                                            </div>
                                            <ChevronRight className="w-5 h-5 flex-shrink-0 text-slate-400" />
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
                        <div className="flex-1 overflow-y-auto overflow-x-hidden p-4 pb-24">
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
                                            <div className="flex min-w-0 items-center gap-3">
                                                <div className={`w-10 h-10 flex-shrink-0 rounded-lg flex items-center justify-center ${selectedArbeitsgang?.id === ag.id ? 'bg-rose-600' : 'bg-slate-100'
                                                    }`}>
                                                    <Wrench className={`w-5 h-5 ${selectedArbeitsgang?.id === ag.id ? 'text-white' : 'text-slate-500'
                                                        }`} />
                                                </div>
                                                <span className="min-w-0 font-medium text-slate-900">{ag.beschreibung}</span>
                                            </div>
                                            {selectedArbeitsgang?.id === ag.id && (
                                                <div className="w-5 h-5 flex-shrink-0 bg-rose-600 rounded-full flex items-center justify-center">
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
            {step === 'arbeitsgang' && selectedArbeitsgang && hatEingerichtetesZeitkonto(zeitkontoStatus) && (
                <div className="fixed bottom-0 left-0 right-0 p-4 bg-white border-t border-slate-200 shadow-lg">
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
            )}</>}
        </div>
    )
}
