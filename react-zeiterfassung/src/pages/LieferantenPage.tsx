import { useState, useEffect } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { Truck, MapPin, Phone, Loader2, Navigation, ChevronRight, Smartphone, FileText, ArrowLeft, Search, X, RefreshCw, AlertTriangle } from 'lucide-react'
import { OfflineService } from '../services/OfflineService'


interface Lieferant {
    id: number
    lieferantenname: string
    ort?: string
    strasse?: string
    plz?: string
    telefon?: string
    mobiltelefon?: string
    vertreter?: string
    eigeneKundennummer?: string
    lieferantenTyp?: string
    kundenEmails?: string[]
}

interface LieferantRaw extends Lieferant {
    firmenname?: string
}

interface LieferantenPageProps {
    mitarbeiter: { id: number; name: string } | null
    syncStatus?: 'syncing' | 'done' | 'error'
    onSync?: () => void
}



export default function LieferantenPage({ syncStatus, onSync }: LieferantenPageProps) {
    const navigate = useNavigate()
    const [searchParams, setSearchParams] = useSearchParams()
    const [lieferanten, setLieferanten] = useState<Lieferant[]>([])
    const [loading, setLoading] = useState(true)
    const [searchTerm, setSearchTerm] = useState('')
    const [selectedLieferant, setSelectedLieferant] = useState<Lieferant | null>(null)
    const [showPhoneModal, setShowPhoneModal] = useState(false)

    // URL-based Selection Sync
    useEffect(() => {
        const idParam = searchParams.get('id')
        if (idParam && lieferanten.length > 0) {
            const lieferantId = parseInt(idParam, 10)
            const found = lieferanten.find(l => l.id === lieferantId)
            if (found && found.id !== selectedLieferant?.id) {
                const timeoutId = window.setTimeout(() => {
                    setSelectedLieferant(found)
                }, 0)

                return () => window.clearTimeout(timeoutId)
            }
        } else if (!idParam && selectedLieferant) {
            const timeoutId = window.setTimeout(() => {
                setSelectedLieferant(null)
            }, 0)

            return () => window.clearTimeout(timeoutId)
        }
    // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [searchParams, lieferanten])

    async function loadLieferanten() {
        setLoading(true)
        try {
            const data = await OfflineService.getLieferanten()
            // Map firmenname to lieferantenname for consistency
            const mapped = (data as LieferantRaw[]).map(l => ({
                ...l,
                lieferantenname: l.lieferantenname || l.firmenname
            })) as Lieferant[]
            setLieferanten(mapped)
        } catch (err) {
            console.error('Fehler beim Laden:', err)
        }
        setLoading(false)
    }

    useEffect(() => {
        const timeoutId = window.setTimeout(() => {
            void loadLieferanten()
        }, 0)

        return () => window.clearTimeout(timeoutId)
    }, [])

    // Server-side search logic
    useEffect(() => {
        if (!searchTerm) {
            const timeoutId = window.setTimeout(() => {
                void loadLieferanten()
            }, 0)

            return () => window.clearTimeout(timeoutId)
        }

        const delayDebounceFn = setTimeout(async () => {
            if (searchTerm.length >= 2) {
                setLoading(true);
                const results = await OfflineService.searchLieferanten(searchTerm);
                const mapped = (results as LieferantRaw[]).map(l => ({
                    ...l,
                    lieferantenname: l.lieferantenname || l.firmenname
                })) as Lieferant[]
                setLieferanten(mapped);
                setLoading(false);
            }
        }, 500);

        return () => clearTimeout(delayDebounceFn);
    }, [searchTerm]);

    const openMaps = () => {
        if (!selectedLieferant) return
        const address = `${selectedLieferant.strasse || ''} ${selectedLieferant.plz || ''} ${selectedLieferant.ort || ''}`
        const url = `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(address)}`
        window.open(url, '_blank')
    }

    const openNavigation = () => {
        if (!selectedLieferant) return
        const address = `${selectedLieferant.strasse || ''} ${selectedLieferant.plz || ''} ${selectedLieferant.ort || ''}`
        const url = `https://www.google.com/maps/dir/?api=1&destination=${encodeURIComponent(address)}`
        window.open(url, '_blank')
    }

    const callPhone = (telefon: string) => {
        window.location.href = `tel:${telefon}`
    }





    return (
        <div className="h-full flex flex-col bg-slate-50">
            {/* Header */}
            <header className="bg-white border-b border-slate-200 px-4 py-4 safe-area-top">
                <div className="flex items-center gap-3">
                    <button
                        onClick={() => selectedLieferant ? setSearchParams({}) : navigate('/')}
                        className="p-2 hover:bg-slate-100 rounded-lg transition-all active:scale-95"
                    >
                        <ArrowLeft className="w-5 h-5 text-slate-600" />
                    </button>
                    <div className="flex items-center gap-3">
                        <div className="w-10 h-10 bg-rose-50 rounded-lg flex items-center justify-center">
                            <Truck className="w-5 h-5 text-rose-600" />
                        </div>
                        <div>
                            <h1 className="font-bold text-slate-900">
                                {selectedLieferant ? selectedLieferant.lieferantenname : 'Lieferanten'}
                            </h1>
                            {selectedLieferant?.eigeneKundennummer && (
                                <p className="text-sm text-slate-500">Kd.-Nr. {selectedLieferant.eigeneKundennummer}</p>
                            )}
                        </div>
                    </div>
                    {!selectedLieferant && (
                        <button
                            onClick={() => onSync && onSync()}
                            className="ml-auto p-2 hover:bg-slate-100 rounded-lg transition-all active:scale-95"
                        >
                            <RefreshCw className={`w-5 h-5 ${loading || syncStatus === 'syncing' ? 'animate-sync-spin text-rose-600' : 'text-slate-500'}`} />
                        </button>
                    )}
                </div>
            </header>

            {/* Search (nur in Liste) */}
            {!selectedLieferant && (
                <div className="p-4 bg-white border-b border-slate-100">
                    <div className="relative">
                        <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-slate-400" />
                        <input
                            type="text"
                            value={searchTerm}
                            onChange={(e) => setSearchTerm(e.target.value)}
                            placeholder="Lieferant suchen..."
                            className="w-full pl-10 pr-4 py-3 bg-slate-50 border border-slate-200 rounded-xl text-slate-900 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-rose-500"
                        />
                    </div>
                </div>
            )}

            {/* Content */}
            <div className="flex-1 overflow-auto">
                {loading ? (
                    <div className="flex justify-center py-12">
                        <Loader2 className="w-8 h-8 animate-spin text-rose-600" />
                    </div>
                ) : selectedLieferant ? (
                    /* Lieferant Detail View */
                    <div className="p-4 space-y-4">

                        {/* Reklamationen Card */}
                        <div className="bg-white rounded-2xl p-4 border border-slate-200">
                            <div className="flex items-center gap-2 mb-4">
                                <AlertTriangle className="w-4 h-4 text-rose-600" />
                                <span className="font-medium text-slate-700">Aktionen</span>
                            </div>

                            <div className="grid grid-cols-2 gap-3">
                                <button
                                    onClick={() => navigate(`/lieferanten/${selectedLieferant.id}/lieferscheine`)}
                                    className="bg-slate-900 hover:bg-slate-800 text-white font-semibold py-4 rounded-xl flex flex-col items-center justify-center gap-2 transition-colors"
                                >
                                    <FileText className="w-6 h-6" />
                                    <span className="text-sm">Lieferscheine</span>
                                </button>

                                <button
                                    onClick={() => navigate(`/lieferanten/${selectedLieferant.id}/reklamationen`)}
                                    className="bg-rose-600 hover:bg-rose-700 text-white font-semibold py-4 rounded-xl flex flex-col items-center justify-center gap-2 transition-colors"
                                >
                                    <AlertTriangle className="w-6 h-6" />
                                    <span className="text-sm">Reklamation</span>
                                </button>
                            </div>
                        </div>

                        {/* B. Address Card (Middle) - Always Visible */}
                        <div className="bg-white rounded-2xl p-4 border border-slate-200">
                            <div className="flex items-center gap-2 mb-4">
                                <MapPin className="w-4 h-4 text-rose-600" />
                                <span className="font-medium text-slate-700">Adresse</span>
                            </div>

                            <div className="space-y-2 mb-4">
                                {(selectedLieferant.strasse || selectedLieferant.plz || selectedLieferant.ort) ? (
                                    <>
                                        <p className="text-slate-900 font-medium">{selectedLieferant.strasse || '-'}</p>
                                        <p className="text-slate-600">{selectedLieferant.plz || ''} {selectedLieferant.ort || ''}</p>
                                    </>
                                ) : (
                                    <p className="text-slate-400 text-sm">Keine Adresse hinterlegt</p>
                                )}
                            </div>

                            <div className="grid grid-cols-2 gap-3">
                                <button
                                    onClick={openMaps}
                                    className="bg-slate-100 hover:bg-slate-200 text-slate-700 font-medium py-3 rounded-xl flex items-center justify-center gap-2 transition-colors"
                                >
                                    <MapPin className="w-4 h-4" />
                                    Karte
                                </button>
                                <button
                                    onClick={openNavigation}
                                    className="bg-rose-600 hover:bg-rose-700 text-white font-medium py-3 rounded-xl flex items-center justify-center gap-2 transition-colors"
                                >
                                    <Navigation className="w-4 h-4" />
                                    Navigation
                                </button>
                            </div>
                        </div>

                        {/* C. Contact Card (Bottom) - Always Visible */}
                        <div className="bg-white rounded-2xl p-4 border border-slate-200">
                            <div className="flex items-center gap-2 mb-4">
                                <Truck className="w-4 h-4 text-rose-600" />
                                <span className="font-medium text-slate-700">Kontakt</span>
                            </div>
                            <p className="text-lg font-semibold text-slate-900 mb-3">
                                {selectedLieferant.lieferantenname}
                            </p>

                            {(selectedLieferant.telefon || selectedLieferant.mobiltelefon) ? (
                                selectedLieferant.telefon && selectedLieferant.mobiltelefon ? (
                                    <button
                                        onClick={() => setShowPhoneModal(true)}
                                        className="w-full flex items-center gap-3 p-3 bg-slate-50 hover:bg-green-50 border border-slate-200 hover:border-green-200 rounded-xl transition-all"
                                    >
                                        <div className="w-10 h-10 bg-green-100 rounded-lg flex items-center justify-center">
                                            <Phone className="w-5 h-5 text-green-600" />
                                        </div>
                                        <div className="flex-1 text-left">
                                            <p className="text-xs text-slate-500">Telefon</p>
                                            <p className="text-slate-900 font-medium">2 Nummern verfügbar</p>
                                        </div>
                                        <span className="text-green-600 text-sm font-medium">Anrufen</span>
                                    </button>
                                ) : (
                                    <button
                                        onClick={() => callPhone(selectedLieferant.telefon || selectedLieferant.mobiltelefon || '')}
                                        className="w-full flex items-center gap-3 p-3 bg-slate-50 hover:bg-green-50 border border-slate-200 hover:border-green-200 rounded-xl transition-all"
                                    >
                                        <div className="w-10 h-10 bg-green-100 rounded-lg flex items-center justify-center">
                                            {selectedLieferant.mobiltelefon ? (
                                                <Smartphone className="w-5 h-5 text-green-600" />
                                            ) : (
                                                <Phone className="w-5 h-5 text-green-600" />
                                            )}
                                        </div>
                                        <div className="flex-1 text-left">
                                            <p className="text-xs text-slate-500">{selectedLieferant.telefon ? 'Telefon' : 'Mobil'}</p>
                                            <p className="text-slate-900 font-medium">{selectedLieferant.telefon || selectedLieferant.mobiltelefon}</p>
                                        </div>
                                        <span className="text-green-600 text-sm font-medium">Anrufen</span>
                                    </button>
                                )
                            ) : (
                                <button
                                    disabled
                                    className="w-full flex items-center gap-3 p-3 bg-slate-50 border border-slate-200 rounded-xl opacity-60 cursor-not-allowed"
                                >
                                    <div className="w-10 h-10 bg-slate-100 rounded-lg flex items-center justify-center">
                                        <Phone className="w-5 h-5 text-slate-400" />
                                    </div>
                                    <div className="flex-1 text-left">
                                        <p className="text-xs text-slate-500">Telefon</p>
                                        <p className="text-slate-400 font-medium">Keine Nummer</p>
                                    </div>
                                </button>
                            )}


                        </div>

                    </div>
                ) : (
                    /* Lieferanten List */
                    <div className="p-4 space-y-2">
                        {lieferanten.map(lieferant => (
                            <button
                                key={lieferant.id}
                                onClick={() => setSearchParams({ id: lieferant.id.toString() })}
                                className="w-full bg-white border border-slate-200 rounded-xl p-4 text-left transition-all hover:border-rose-200 hover:shadow-sm"
                            >
                                <div className="flex items-center gap-3">
                                    <div className="w-12 h-12 bg-rose-50 rounded-xl flex items-center justify-center flex-shrink-0">
                                        <Truck className="w-5 h-5 text-rose-600" />
                                    </div>
                                    <div className="flex-1 min-w-0">
                                        <p className="text-slate-900 font-medium truncate">{lieferant.lieferantenname}</p>
                                        {lieferant.ort && (
                                            <p className="text-slate-500 text-sm flex items-center gap-1">
                                                <MapPin className="w-3 h-3" /> {lieferant.ort}
                                            </p>
                                        )}
                                        {lieferant.telefon && (
                                            <p className="text-slate-400 text-xs flex items-center gap-1 mt-1">
                                                <Phone className="w-3 h-3" /> {lieferant.telefon}
                                            </p>
                                        )}
                                    </div>
                                    <ChevronRight className="w-4 h-4 text-slate-400" />
                                </div>
                            </button>
                        ))}

                        {lieferanten.length === 0 && (
                            <div className="text-center py-12 text-slate-500">
                                <Truck className="w-12 h-12 mx-auto mb-3 opacity-30" />
                                <p>Keine Lieferanten gefunden</p>
                            </div>
                        )}
                    </div>
                )}

            </div>

            {/* Phone Selection Modal */}
            {showPhoneModal && selectedLieferant && (
                <div
                    className="fixed inset-0 bg-black/50 z-50 flex items-end justify-center"
                    onClick={() => setShowPhoneModal(false)}
                >
                    <div
                        className="bg-white rounded-t-2xl w-full max-w-lg p-6 safe-area-bottom animate-slide-up"
                        onClick={(e) => e.stopPropagation()}
                    >
                        <div className="flex items-center justify-between mb-6">
                            <h3 className="text-lg font-bold text-slate-900">Nummer wählen</h3>
                            {/* Close button similar to ProjectsPage */}
                            <button
                                onClick={() => setShowPhoneModal(false)}
                                className="p-2 hover:bg-slate-100 rounded-full"
                            >
                                <X className="w-5 h-5 text-slate-500" />
                            </button>
                        </div>
                        <div className="space-y-3">
                            {selectedLieferant.telefon && (
                                <button
                                    onClick={() => {
                                        callPhone(selectedLieferant.telefon!)
                                        setShowPhoneModal(false)
                                    }}
                                    className="w-full bg-slate-50 hover:bg-slate-100 border border-slate-200 rounded-xl p-4 flex items-center gap-4 transition-colors"
                                >
                                    <div className="w-12 h-12 bg-blue-100 rounded-full flex items-center justify-center">
                                        <Phone className="w-6 h-6 text-blue-600" />
                                    </div>
                                    <div className="flex-1 text-left">
                                        <p className="font-medium text-slate-900">Festnetz</p>
                                        <p className="text-slate-500">{selectedLieferant.telefon}</p>
                                    </div>
                                </button>
                            )}
                            {selectedLieferant.mobiltelefon && (
                                <button
                                    onClick={() => {
                                        callPhone(selectedLieferant.mobiltelefon!)
                                        setShowPhoneModal(false)
                                    }}
                                    className="w-full bg-slate-50 hover:bg-slate-100 border border-slate-200 rounded-xl p-4 flex items-center gap-4 transition-colors"
                                >
                                    <div className="w-12 h-12 bg-green-100 rounded-full flex items-center justify-center">
                                        <Smartphone className="w-6 h-6 text-green-600" />
                                    </div>
                                    <div className="flex-1 text-left">
                                        <p className="font-medium text-slate-900">Mobil</p>
                                        <p className="text-slate-500">{selectedLieferant.mobiltelefon}</p>
                                    </div>
                                </button>
                            )}
                        </div>
                        <button
                            onClick={() => setShowPhoneModal(false)}
                            className="w-full mt-4 py-3 text-slate-500 font-medium"
                        >
                            Abbrechen
                        </button>
                    </div>
                </div>
            )}


        </div>
    )
}
