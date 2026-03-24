import { useState, useEffect } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { ArrowLeft, Search, Users, MapPin, Phone, Mail, Loader2, Navigation, ChevronRight, Building2, Smartphone, RefreshCw } from 'lucide-react'
import { OfflineService } from '../services/OfflineService'

interface Kunde {
    id: number
    name: string
    kundennummer?: string
    ansprechspartner?: string
    strasse?: string
    plz?: string
    ort?: string
    telefon?: string
    mobiltelefon?: string
    email?: string
}

interface KundenPageProps {
    syncStatus?: 'syncing' | 'done' | 'error'
    onSync?: () => void
}

export default function KundenPage({ syncStatus, onSync }: KundenPageProps) {
    const navigate = useNavigate()
    const [searchParams, setSearchParams] = useSearchParams()
    const [kunden, setKunden] = useState<Kunde[]>([])
    const [loading, setLoading] = useState(true)
    const [searchTerm, setSearchTerm] = useState('')
    const [selectedKunde, setSelectedKunde] = useState<Kunde | null>(null)
    const [showPhoneModal, setShowPhoneModal] = useState(false)

    // URL-based Selection Sync
    useEffect(() => {
        const idParam = searchParams.get('id')
        if (idParam && kunden.length > 0) {
            const kundeId = parseInt(idParam, 10)
            const found = kunden.find(k => k.id === kundeId)
            if (found && found.id !== selectedKunde?.id) {
                const timeoutId = window.setTimeout(() => {
                    setSelectedKunde(found)
                }, 0)

                return () => window.clearTimeout(timeoutId)
            }
        } else if (!idParam && selectedKunde) {
            const timeoutId = window.setTimeout(() => {
                setSelectedKunde(null)
            }, 0)

            return () => window.clearTimeout(timeoutId)
        }
    }, [searchParams, kunden])

    async function loadKunden() {
        setLoading(true)
        try {
            const data = await OfflineService.getKunden() as Kunde[]
            setKunden(data)
        } catch (err) {
            console.error('Fehler beim Laden:', err)
        }
        setLoading(false)
    }

    useEffect(() => {
        const timeoutId = window.setTimeout(() => {
            void loadKunden()
        }, 0)

        return () => window.clearTimeout(timeoutId)
    }, [])

    // Server-side search logic
    useEffect(() => {
        if (!searchTerm) {
            const timeoutId = window.setTimeout(() => {
                void loadKunden()
            }, 0)

            return () => window.clearTimeout(timeoutId)
        }

        const delayDebounceFn = setTimeout(async () => {
            if (searchTerm.length >= 2) {
                setLoading(true);
                const results = await OfflineService.searchKunden(searchTerm);
                setKunden(results);
                setLoading(false);
            }
        }, 500);

        return () => clearTimeout(delayDebounceFn);
    }, [searchTerm]);

    const openMaps = () => {
        if (!selectedKunde) return
        const address = `${selectedKunde.strasse || ''} ${selectedKunde.plz || ''} ${selectedKunde.ort || ''}`
        const url = `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(address)}`
        window.open(url, '_blank')
    }

    const openNavigation = () => {
        if (!selectedKunde) return
        const address = `${selectedKunde.strasse || ''} ${selectedKunde.plz || ''} ${selectedKunde.ort || ''}`
        const url = `https://www.google.com/maps/dir/?api=1&destination=${encodeURIComponent(address)}`
        window.open(url, '_blank')
    }

    const callPhone = (telefon: string) => {
        window.location.href = `tel:${telefon}`
    }

    const sendEmail = (email: string) => {
        window.location.href = `mailto:${email}`
    }

    return (
        <div className="h-full flex flex-col bg-slate-50">
            {/* Header */}
            <header className="bg-white border-b border-slate-200 px-4 py-4 safe-area-top">
                <div className="flex items-center gap-3">
                    <button
                        onClick={() => selectedKunde ? setSearchParams({}) : navigate('/')}
                        className="p-2 hover:bg-slate-100 rounded-lg transition-all active:scale-95"
                    >
                        <ArrowLeft className="w-5 h-5 text-slate-600" />
                    </button>
                    <div className="flex items-center gap-3">
                        <div className="w-10 h-10 bg-rose-50 rounded-lg flex items-center justify-center">
                            <Users className="w-5 h-5 text-rose-600" />
                        </div>
                        <div>
                            <h1 className="font-bold text-slate-900">
                                {selectedKunde ? selectedKunde.name : 'Kunden'}
                            </h1>
                            {selectedKunde?.kundennummer && (
                                <p className="text-sm text-slate-500">Nr. {selectedKunde.kundennummer}</p>
                            )}
                        </div>
                    </div>
                    {!selectedKunde && (
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
            {!selectedKunde && (
                <div className="p-4 bg-white border-b border-slate-100">
                    <div className="relative">
                        <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-slate-400" />
                        <input
                            type="text"
                            value={searchTerm}
                            onChange={(e) => setSearchTerm(e.target.value)}
                            placeholder="Kunde suchen..."
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
                ) : selectedKunde ? (
                    /* Customer Detail View */
                    <div className="p-4 space-y-4">
                        {/* Kundeninformationen Card */}
                        {(selectedKunde.kundennummer || selectedKunde.ansprechspartner) && (
                            <div className="bg-white rounded-2xl p-4 border border-slate-200">
                                <div className="flex items-center gap-2 mb-4">
                                    <Users className="w-4 h-4 text-rose-600" />
                                    <span className="font-medium text-slate-700">Kundeninformationen</span>
                                </div>
                                <div className="space-y-3">
                                    {selectedKunde.kundennummer && (
                                        <div>
                                            <p className="text-xs text-slate-500">Kundennummer</p>
                                            <p className="text-slate-900 font-medium">{selectedKunde.kundennummer}</p>
                                        </div>
                                    )}
                                    {selectedKunde.ansprechspartner && (
                                        <div>
                                            <p className="text-xs text-slate-500">Ansprechpartner</p>
                                            <p className="text-slate-900 font-medium">{selectedKunde.ansprechspartner}</p>
                                        </div>
                                    )}
                                </div>
                            </div>
                        )}

                        {/* Kontakt Card */}
                        <div className="bg-white rounded-2xl p-4 border border-slate-200">
                            <div className="flex items-center gap-2 mb-4">
                                <Building2 className="w-4 h-4 text-rose-600" />
                                <span className="font-medium text-slate-700">Kontaktdaten</span>
                            </div>

                            <div className="space-y-4">
                                {/* Telefon - Flat Row Style */}
                                {(selectedKunde.telefon || selectedKunde.mobiltelefon) && (
                                    <>
                                        {selectedKunde.telefon && selectedKunde.mobiltelefon ? (
                                            /* Both phone numbers - show button that opens modal */
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
                                            /* Single phone number - direct call */
                                            <button
                                                onClick={() => callPhone(selectedKunde.telefon || selectedKunde.mobiltelefon || '')}
                                                className="w-full flex items-center gap-3 p-3 bg-slate-50 hover:bg-green-50 border border-slate-200 hover:border-green-200 rounded-xl transition-all"
                                            >
                                                <div className="w-10 h-10 bg-green-100 rounded-lg flex items-center justify-center">
                                                    {selectedKunde.mobiltelefon ? (
                                                        <Smartphone className="w-5 h-5 text-green-600" />
                                                    ) : (
                                                        <Phone className="w-5 h-5 text-green-600" />
                                                    )}
                                                </div>
                                                <div className="flex-1 text-left">
                                                    <p className="text-xs text-slate-500">{selectedKunde.telefon ? 'Telefon' : 'Mobil'}</p>
                                                    <p className="text-slate-900 font-medium">{selectedKunde.telefon || selectedKunde.mobiltelefon}</p>
                                                </div>
                                                <span className="text-green-600 text-sm font-medium">Anrufen</span>
                                            </button>
                                        )}
                                    </>
                                )}

                                {/* E-Mail */}
                                {selectedKunde.email && (
                                    <button
                                        onClick={() => sendEmail(selectedKunde.email!)}
                                        className="w-full flex items-center gap-3 p-3 bg-slate-50 hover:bg-blue-50 border border-slate-200 hover:border-blue-200 rounded-xl transition-all"
                                    >
                                        <div className="w-10 h-10 bg-blue-100 rounded-lg flex items-center justify-center">
                                            <Mail className="w-5 h-5 text-blue-600" />
                                        </div>
                                        <div className="flex-1 text-left min-w-0">
                                            <p className="text-xs text-slate-500">E-Mail</p>
                                            <p className="text-slate-900 font-medium truncate">{selectedKunde.email}</p>
                                        </div>
                                        <span className="text-blue-600 text-sm font-medium">Schreiben</span>
                                    </button>
                                )}
                            </div>
                        </div>

                        {/* Adresse Card */}
                        {(selectedKunde.strasse || selectedKunde.ort) && (
                            <div className="bg-white rounded-2xl p-4 border border-slate-200">
                                <div className="flex items-center gap-2 mb-4">
                                    <MapPin className="w-4 h-4 text-rose-600" />
                                    <span className="font-medium text-slate-700">Adresse</span>
                                </div>

                                <div className="space-y-2 mb-4">
                                    <p className="text-slate-900 font-medium">{selectedKunde.strasse}</p>
                                    <p className="text-slate-600">{selectedKunde.plz} {selectedKunde.ort}</p>
                                </div>

                                {/* Maps Buttons */}
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
                        )}
                    </div>
                ) : (
                    /* Customer List */
                    <div className="p-4 space-y-2">
                        {kunden.map(kunde => (
                            <button
                                key={kunde.id}
                                onClick={() => setSearchParams({ id: kunde.id.toString() })}
                                className="w-full bg-white border border-slate-200 rounded-xl p-4 text-left transition-all hover:border-rose-200 hover:shadow-sm"
                            >
                                <div className="flex items-center gap-3">
                                    <div className="w-12 h-12 bg-rose-50 rounded-xl flex items-center justify-center flex-shrink-0">
                                        <Users className="w-5 h-5 text-rose-600" />
                                    </div>
                                    <div className="flex-1 min-w-0">
                                        <p className="text-slate-900 font-medium truncate">{kunde.name}</p>
                                        {kunde.ort && (
                                            <p className="text-slate-500 text-sm flex items-center gap-1">
                                                <MapPin className="w-3 h-3" /> {kunde.ort}
                                            </p>
                                        )}
                                        {kunde.telefon && (
                                            <p className="text-slate-400 text-xs flex items-center gap-1 mt-1">
                                                <Phone className="w-3 h-3" /> {kunde.telefon}
                                            </p>
                                        )}
                                    </div>
                                    <ChevronRight className="w-4 h-4 text-slate-400" />
                                </div>
                            </button>
                        ))}

                        {kunden.length === 0 && (
                            <div className="text-center py-12 text-slate-500">
                                <Users className="w-12 h-12 mx-auto mb-3 opacity-30" />
                                <p>Keine Kunden gefunden</p>
                            </div>
                        )}
                    </div>
                )}
            </div>

            {/* Phone Selection Modal */}
            {showPhoneModal && selectedKunde && (
                <div
                    className="fixed inset-0 bg-black/50 z-50 flex items-end justify-center"
                    onClick={() => setShowPhoneModal(false)}
                >
                    <div
                        className="bg-white rounded-t-2xl w-full max-w-lg p-6 safe-area-bottom"
                        onClick={(e) => e.stopPropagation()}
                    >
                        <h3 className="text-lg font-bold text-slate-900 mb-4 text-center">Nummer wählen</h3>
                        <div className="space-y-3">
                            {selectedKunde.telefon && (
                                <button
                                    onClick={() => {
                                        callPhone(selectedKunde.telefon!)
                                        setShowPhoneModal(false)
                                    }}
                                    className="w-full flex items-center gap-3 p-4 bg-slate-50 hover:bg-green-50 border border-slate-200 hover:border-green-200 rounded-xl transition-all"
                                >
                                    <div className="w-12 h-12 bg-green-100 rounded-lg flex items-center justify-center">
                                        <Phone className="w-6 h-6 text-green-600" />
                                    </div>
                                    <div className="flex-1 text-left">
                                        <p className="text-sm text-slate-500">Festnetz</p>
                                        <p className="text-slate-900 font-medium">{selectedKunde.telefon}</p>
                                    </div>
                                </button>
                            )}
                            {selectedKunde.mobiltelefon && (
                                <button
                                    onClick={() => {
                                        callPhone(selectedKunde.mobiltelefon!)
                                        setShowPhoneModal(false)
                                    }}
                                    className="w-full flex items-center gap-3 p-4 bg-slate-50 hover:bg-green-50 border border-slate-200 hover:border-green-200 rounded-xl transition-all"
                                >
                                    <div className="w-12 h-12 bg-green-100 rounded-lg flex items-center justify-center">
                                        <Smartphone className="w-6 h-6 text-green-600" />
                                    </div>
                                    <div className="flex-1 text-left">
                                        <p className="text-sm text-slate-500">Mobil</p>
                                        <p className="text-slate-900 font-medium">{selectedKunde.mobiltelefon}</p>
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
