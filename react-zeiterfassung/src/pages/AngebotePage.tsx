import { useState, useEffect, useRef } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { ArrowLeft, Search, FileText, MapPin, User, Loader2, Image, ChevronRight, Navigation, Phone, X, Camera, FolderOpen, Plus, Smartphone, RefreshCw, MessageCircle } from 'lucide-react'
import { ImageViewer } from '../components/ui/image-viewer'

interface Angebot {
    id: number
    bauvorhaben: string
    kundenName: string
    kundenOrt?: string
    kundenStrasse?: string
    kundenPlz?: string
    kundenTelefon?: string
    kundenMobil?: string
    ansprechpartner?: string
    kundennummer?: string
    projektStrasse?: string
    projektPlz?: string
    projektOrt?: string
    bildUrl?: string
    anlegedatum?: string
    abgeschlossen?: boolean
}

interface Bild {
    id: number
    name: string
    url: string
    thumbnailUrl?: string
    uploadDatum?: string
    uploadedByVorname?: string
    uploadedByNachname?: string
}

interface AngebotsPageProps {
    mitarbeiter?: { id: number; name: string } | null
    syncStatus?: 'syncing' | 'done' | 'error'
    onSync?: () => void
}

export default function AngebotePage({ mitarbeiter, syncStatus, onSync }: AngebotsPageProps) {
    const navigate = useNavigate()
    const [searchParams, setSearchParams] = useSearchParams()
    const cameraInputRef = useRef<HTMLInputElement>(null)
    const galleryInputRef = useRef<HTMLInputElement>(null)
    const [angebote, setAngebote] = useState<Angebot[]>([])
    const [loading, setLoading] = useState(true)
    const [detailLoading, setDetailLoading] = useState(false)
    const [searchTerm, setSearchTerm] = useState('')
    const [selectedAngebot, setSelectedAngebot] = useState<Angebot | null>(null)
    const [bilder, setBilder] = useState<Bild[]>([])
    const [viewerIndex, setViewerIndex] = useState<number | null>(null)
    const [uploading, setUploading] = useState(false)
    const [showPhoneModal, setShowPhoneModal] = useState(false)
    const [showUploadModal, setShowUploadModal] = useState(false)

    async function loadAngebote(searchQuery?: string) {
        setLoading(true)
        try {
            let url = '/api/angebote?size=15'
            if (searchQuery) {
                // Use the 'q' parameter for comprehensive free-text search 
                // (includes bauvorhaben, kundenname, ansprechpartner, etc.)
                url = `/api/angebote?q=${encodeURIComponent(searchQuery)}&size=20`
            }
            const res = await fetch(url)
            if (res.ok) {
                const data = await res.json()
                // API returns direct array or paged content
                const list = Array.isArray(data) ? data : (data.content || data.angebote || [])
                const mapped = list
                    .filter((a: { abgeschlossen?: boolean }) => !a.abgeschlossen) // Nur nicht-beendete Angebote
                    .map((a: {
                        id: number;
                        bauvorhaben: string;
                        betrag?: number;
                        kundenName?: string;
                        kundenOrt?: string;
                        kundenStrasse?: string;
                        kundenPlz?: string;
                        kundenTelefon?: string;
                        kundenMobiltelefon?: string;
                        projektStrasse?: string;
                        projektPlz?: string;
                        projektOrt?: string;
                        bildUrl?: string;
                        anlegedatum?: string;
                        abgeschlossen?: boolean;
                    }) => ({
                        id: a.id,
                        bauvorhaben: a.bauvorhaben,
                        betrag: a.betrag,
                        kundenName: a.kundenName || 'Kein Kunde',
                        kundenOrt: a.kundenOrt,
                        kundenStrasse: a.kundenStrasse,
                        kundenPlz: a.kundenPlz,
                        kundenTelefon: a.kundenTelefon,
                        kundenMobil: a.kundenMobiltelefon,
                        projektStrasse: a.projektStrasse,
                        projektPlz: a.projektPlz,
                        projektOrt: a.projektOrt,
                        bildUrl: a.bildUrl,
                        anlegedatum: a.anlegedatum,
                        abgeschlossen: a.abgeschlossen,
                    }))
                setAngebote(mapped)
            }
        } catch (err) {
            console.error('Fehler beim Laden:', err)
        }
        setLoading(false)
    }

    useEffect(() => {
        const timeoutId = window.setTimeout(() => {
            void loadAngebote()
        }, 0)

        return () => window.clearTimeout(timeoutId)
    }, [])

    async function loadAngebotDetail(angebot: Angebot) {
        setSelectedAngebot(angebot)
        setDetailLoading(true)
        setBilder([])

        try {
            // Load angebot bilder from endpoint
            const bilderRes = await fetch(`/api/angebote/${angebot.id}/dokumente?gruppe=BILDER`)
            if (bilderRes.ok) {
                const bilderData = await bilderRes.json()
                const mapped = bilderData?.map((d: { id: number; originalDateiname: string; url: string; thumbnailUrl?: string }) => ({
                    id: d.id,
                    name: d.originalDateiname,
                    url: d.url,
                    thumbnailUrl: d.thumbnailUrl || (d.url ? d.url + '/thumbnail' : undefined)
                })) || []
                setBilder(mapped)
            }
        } catch (err) {
            console.error('Fehler beim Laden:', err)
        }
        setDetailLoading(false)
    }

    // URL-based Selection Sync
    useEffect(() => {
        const idParam = searchParams.get('id')
        if (idParam && angebote.length > 0) {
            const angebotId = parseInt(idParam, 10)
            const found = angebote.find(a => a.id === angebotId)
            if (found && found.id !== selectedAngebot?.id) {
                const timeoutId = window.setTimeout(() => {
                    void loadAngebotDetail(found)
                }, 0)

                return () => window.clearTimeout(timeoutId)
            }
        } else if (!idParam && selectedAngebot) {
            const timeoutId = window.setTimeout(() => {
                setSelectedAngebot(null)
                setBilder([])
            }, 0)

            return () => window.clearTimeout(timeoutId)
        }
    // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [searchParams, angebote])

    // Server-side search logic
    useEffect(() => {
        if (!searchTerm) {
            const timeoutId = window.setTimeout(() => {
                void loadAngebote()
            }, 0)

            return () => window.clearTimeout(timeoutId)
        }

        const delayDebounceFn = setTimeout(async () => {
            if (searchTerm.length >= 2) {
                await loadAngebote(searchTerm);
            }
        }, 500);

        return () => clearTimeout(delayDebounceFn);
    }, [searchTerm]);

    const getAddress = () => {
        // Prefer projekt address, fallback to kunden address
        const strasse = selectedAngebot?.projektStrasse || selectedAngebot?.kundenStrasse
        const plz = selectedAngebot?.projektPlz || selectedAngebot?.kundenPlz
        const ort = selectedAngebot?.projektOrt || selectedAngebot?.kundenOrt
        return `${strasse || ''} ${plz || ''} ${ort || ''}`
    }

    const openExternalMap = (address: string, navigation: boolean) => {
        const encoded = encodeURIComponent(address)
        const ua = navigator.userAgent || ''
        const isIOS = /iPad|iPhone|iPod/.test(ua) && !(window as unknown as { MSStream?: unknown }).MSStream
        const isAndroid = /Android/.test(ua)
        if (isIOS) {
            const gmaps = navigation ? `comgooglemaps://?daddr=${encoded}` : `comgooglemaps://?q=${encoded}`
            const apple = navigation ? `maps://?daddr=${encoded}` : `maps://?q=${encoded}`
            let didHide = false
            const onVis = () => { if (document.hidden) didHide = true }
            document.addEventListener('visibilitychange', onVis)
            window.location.href = gmaps
            window.setTimeout(() => {
                document.removeEventListener('visibilitychange', onVis)
                if (!didHide) window.location.href = apple
            }, 1200)
            return
        }
        if (isAndroid) {
            window.location.href = `geo:0,0?q=${encoded}`
            return
        }
        window.open(
            navigation
                ? `https://www.google.com/maps/dir/?api=1&destination=${encoded}`
                : `https://www.google.com/maps/search/?api=1&query=${encoded}`,
            '_blank'
        )
    }

    const openMaps = () => {
        const address = getAddress().trim()
        if (!address) return
        openExternalMap(address, false)
    }

    const openNavigation = () => {
        const address = getAddress().trim()
        if (!address) return
        openExternalMap(address, true)
    }

    const openViewer = (idx: number) => {
        setViewerIndex(idx)
    }

    const closeViewer = () => {
        setViewerIndex(null)
    }

    const handleCall = (number: string) => {
        window.location.href = `tel:${number}`
        setShowPhoneModal(false)
    }

    const openPhoneModal = () => {
        if (selectedAngebot?.kundenTelefon && !selectedAngebot?.kundenMobil) {
            handleCall(selectedAngebot.kundenTelefon)
        } else if (selectedAngebot?.kundenMobil && !selectedAngebot?.kundenTelefon) {
            handleCall(selectedAngebot.kundenMobil)
        } else if (selectedAngebot?.kundenTelefon || selectedAngebot?.kundenMobil) {
            setShowPhoneModal(true)
        }
    }

    const handleUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
        const files = e.target.files
        if (!files || files.length === 0 || !selectedAngebot) return

        setUploading(true)
        setShowUploadModal(false)
        const formData = new FormData()
        for (let i = 0; i < files.length; i++) {
            formData.append('datei', files[i])
        }

        // Add mitarbeiter header for upload tracking
        const headers: Record<string, string> = {}
        if (mitarbeiter?.id) {
            headers['X-Mitarbeiter-Id'] = String(mitarbeiter.id)
        }

        try {
            const res = await fetch(`/api/angebote/${selectedAngebot.id}/dokumente?gruppe=BILDER`, {
                method: 'POST',
                headers,
                body: formData
            })
            if (res.ok) {
                await loadAngebotDetail(selectedAngebot)
            }
        } catch (err) {
            console.error('Upload fehlgeschlagen:', err)
        }
        setUploading(false)
        // Reset inputs
        if (cameraInputRef.current) cameraInputRef.current.value = ''
        if (galleryInputRef.current) galleryInputRef.current.value = ''
    }

    const hasPhoneNumber = selectedAngebot?.kundenTelefon || selectedAngebot?.kundenMobil
    const hasAddress = selectedAngebot?.projektStrasse || selectedAngebot?.kundenStrasse


    return (
        <div className="h-full flex flex-col bg-slate-50">
            {/* Header */}
            <header className="bg-white border-b border-slate-200 px-4 py-4 safe-area-top">
                <div className="flex items-center gap-3">
                    <button
                        onClick={() => selectedAngebot ? setSearchParams({}) : navigate('/')}
                        className="p-2 hover:bg-slate-100 rounded-lg transition-all active:scale-95"
                    >
                        <ArrowLeft className="w-5 h-5 text-slate-600" />
                    </button>
                    <div className="flex items-center gap-3">
                        <div className="w-10 h-10 bg-rose-50 rounded-lg flex items-center justify-center">
                            <FileText className="w-5 h-5 text-rose-600" />
                        </div>
                        <div>
                            <h1 className="font-bold text-slate-900">
                                {selectedAngebot ? selectedAngebot.bauvorhaben : 'Angebote'}
                            </h1>
                            {selectedAngebot && (
                                <p className="text-sm text-slate-500">{selectedAngebot.kundenName}</p>
                            )}
                        </div>
                    </div>
                    {!selectedAngebot && (
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
            {!selectedAngebot && (
                <div className="p-4 bg-white border-b border-slate-100">
                    <div className="relative">
                        <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-slate-400" />
                        <input
                            type="text"
                            value={searchTerm}
                            onChange={(e) => setSearchTerm(e.target.value)}
                            placeholder="Angebot suchen..."
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
                ) : selectedAngebot ? (
                    /* Angebot Detail View */
                    <div className="p-4 space-y-4">
                        {detailLoading ? (
                            <div className="flex justify-center py-12">
                                <Loader2 className="w-8 h-8 animate-spin text-rose-600" />
                            </div>
                        ) : (
                            <>
                                {/* Bilder Galerie */}
                                <div className="bg-white rounded-2xl p-4 border border-slate-200">
                                    <div className="flex items-center justify-between mb-3">
                                        <div className="flex items-center gap-2">
                                            <Image className="w-4 h-4 text-rose-600" />
                                            <span className="font-medium text-slate-700">Angebotsbilder</span>
                                            <span className="text-xs text-slate-400">({bilder.length})</span>
                                        </div>
                                        {/* Upload Button */}
                                        <input
                                            ref={cameraInputRef}
                                            type="file"
                                            accept="image/*"
                                            capture="environment"
                                            onChange={handleUpload}
                                            className="hidden"
                                        />
                                        <input
                                            ref={galleryInputRef}
                                            type="file"
                                            accept="image/*"
                                            multiple
                                            onChange={handleUpload}
                                            className="hidden"
                                        />
                                        <button
                                            onClick={() => setShowUploadModal(true)}
                                            disabled={uploading}
                                            className="flex items-center gap-1 px-3 py-1.5 bg-rose-600 hover:bg-rose-700 text-white text-sm rounded-lg transition-colors disabled:opacity-50"
                                        >
                                            {uploading ? (
                                                <Loader2 className="w-4 h-4 animate-spin" />
                                            ) : (
                                                <>
                                                    <Camera className="w-4 h-4" />
                                                    <Plus className="w-3 h-3" />
                                                </>
                                            )}
                                        </button>
                                    </div>
                                    {bilder.length > 0 ? (
                                        <div className="grid grid-cols-3 gap-2">
                                            {bilder.map((bild, idx) => (
                                                <div key={bild.id || idx} className="flex flex-col">
                                                    <button
                                                        onClick={() => openViewer(idx)}
                                                        className="aspect-square rounded-lg overflow-hidden bg-slate-100 hover:ring-2 hover:ring-rose-500 transition-all touch-manipulation"
                                                    >
                                                        <img
                                                            src={bild.thumbnailUrl || bild.url}
                                                            alt={bild.name || `Bild ${idx + 1}`}
                                                            className="w-full h-full object-cover pointer-events-none"
                                                            loading="lazy"
                                                            onError={(e) => {
                                                                (e.target as HTMLImageElement).src = 'data:image/svg+xml,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100"><rect fill="%23f1f5f9" width="100" height="100"/><text x="50" y="55" text-anchor="middle" fill="%2394a3b8" font-size="12">Bild</text></svg>'
                                                            }}
                                                        />
                                                    </button>
                                                    <p className="text-[10px] text-slate-500 mt-1 text-center truncate">
                                                        {bild.uploadDatum ? new Date(bild.uploadDatum).toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric' }) : ''}
                                                        {(bild.uploadedByVorname || bild.uploadedByNachname) && (
                                                            <span className="text-slate-400"> • {bild.uploadedByVorname?.charAt(0)}. {bild.uploadedByNachname}</span>
                                                        )}
                                                    </p>
                                                </div>
                                            ))}
                                        </div>
                                    ) : (
                                        <div className="text-center py-8 text-slate-400">
                                            <Image className="w-10 h-10 mx-auto mb-2 opacity-40" />
                                            <p className="text-sm">Noch keine Bilder</p>
                                            <p className="text-xs mt-1">Tippe oben auf + um Bilder hinzuzufügen</p>
                                        </div>
                                    )}
                                </div>

                                {/* Bau Tagebuch Button */}
                                <button
                                    onClick={() => navigate(`/angebote/${selectedAngebot?.id}/notizen`)}
                                    className="w-full mb-4 bg-white border border-slate-200 rounded-2xl p-4 flex items-center justify-between transition-all hover:border-rose-200 hover:shadow-sm"
                                >
                                    <div className="flex items-center gap-3">
                                        <div className="w-10 h-10 bg-amber-50 rounded-xl flex items-center justify-center">
                                            <MessageCircle className="w-5 h-5 text-amber-600" />
                                        </div>
                                        <div className="text-left">
                                            <p className="text-slate-900 font-medium">Bau Tagebuch</p>
                                            <p className="text-slate-500 text-sm">Einträge und Infos</p>
                                        </div>
                                    </div>
                                    <ChevronRight className="w-5 h-5 text-slate-400" />
                                </button>

                                {/* Adresse Card */}
                                {hasAddress && (
                                    <div className="bg-white rounded-2xl p-4 border border-slate-200">
                                        <div className="flex items-center gap-2 mb-4">
                                            <MapPin className="w-4 h-4 text-rose-600" />
                                            <span className="font-medium text-slate-700">Projektadresse</span>
                                        </div>

                                        <div className="space-y-2 mb-4">
                                            <p className="text-slate-900 font-medium">{selectedAngebot.projektStrasse || selectedAngebot.kundenStrasse}</p>
                                            <p className="text-slate-600">{selectedAngebot.projektPlz || selectedAngebot.kundenPlz} {selectedAngebot.projektOrt || selectedAngebot.kundenOrt}</p>
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

                                {/* Kunde Card */}
                                <div className="bg-white rounded-2xl p-4 border border-slate-200">
                                    <div className="flex items-center gap-2 mb-4">
                                        <User className="w-4 h-4 text-rose-600" />
                                        <span className="font-medium text-slate-700">Kunde</span>
                                    </div>

                                    <p className="text-lg font-semibold text-slate-900 mb-3">
                                        {selectedAngebot.kundenName}
                                    </p>

                                    {/* Kundennummer und Ansprechpartner */}
                                    {(selectedAngebot.kundennummer || selectedAngebot.ansprechpartner) && (
                                        <div className="space-y-3 mb-4">
                                            {selectedAngebot.kundennummer && (
                                                <div>
                                                    <p className="text-xs text-slate-500">Kundennummer</p>
                                                    <p className="text-slate-900 font-medium">{selectedAngebot.kundennummer}</p>
                                                </div>
                                            )}
                                            {selectedAngebot.ansprechpartner && (
                                                <div>
                                                    <p className="text-xs text-slate-500">Ansprechpartner</p>
                                                    <p className="text-slate-900 font-medium">{selectedAngebot.ansprechpartner}</p>
                                                </div>
                                            )}
                                        </div>
                                    )}
                                    {/* Anrufen Button - Flat Row Style */}
                                    {hasPhoneNumber && (
                                        <button
                                            onClick={openPhoneModal}
                                            className="w-full flex items-center gap-3 p-3 bg-slate-50 hover:bg-green-50 border border-slate-200 hover:border-green-200 rounded-xl transition-all"
                                        >
                                            <div className="w-10 h-10 bg-green-100 rounded-lg flex items-center justify-center">
                                                <Phone className="w-5 h-5 text-green-600" />
                                            </div>
                                            <div className="flex-1 text-left">
                                                <p className="text-xs text-slate-500">Telefon</p>
                                                <p className="text-slate-900 font-medium">
                                                    {selectedAngebot.kundenTelefon && selectedAngebot.kundenMobil
                                                        ? '2 Nummern verfügbar'
                                                        : (selectedAngebot.kundenTelefon || selectedAngebot.kundenMobil)}
                                                </p>
                                            </div>
                                            <span className="text-green-600 text-sm font-medium">Anrufen</span>
                                        </button>
                                    )}

                                    {!hasPhoneNumber && (
                                        <p className="text-center text-slate-400 text-sm py-2">
                                            Keine Telefonnummer hinterlegt
                                        </p>
                                    )}
                                </div>
                            </>
                        )}
                    </div>
                ) : (
                    /* Angebot List */
                    <div className="p-4 space-y-2">
                        {angebote.map(angebot => (
                            <button
                                key={angebot.id}
                                onClick={() => setSearchParams({ id: angebot.id.toString() })}
                                className="w-full bg-white border border-slate-200 rounded-xl p-4 text-left transition-all hover:border-rose-200 hover:shadow-sm"
                            >
                                <div className="flex items-center gap-3">
                                    <div className="w-12 h-12 bg-rose-50 rounded-xl flex items-center justify-center flex-shrink-0">
                                        <FileText className="w-5 h-5 text-rose-600" />
                                    </div>
                                    <div className="flex-1 min-w-0">
                                        <p className="text-slate-900 font-medium truncate">{angebot.bauvorhaben}</p>
                                        <p className="text-slate-500 text-sm truncate">{angebot.kundenName}</p>
                                        {(angebot.projektOrt || angebot.kundenOrt) && (
                                            <p className="text-slate-400 text-xs flex items-center gap-1 mt-1">
                                                <MapPin className="w-3 h-3" />
                                                {angebot.projektOrt || angebot.kundenOrt}
                                            </p>
                                        )}
                                    </div>
                                    <div className="flex flex-col items-end gap-1">
                                        <ChevronRight className="w-4 h-4 text-slate-400" />
                                    </div>
                                </div>
                            </button>
                        ))}

                        {angebote.length === 0 && (
                            <div className="text-center py-12 text-slate-500">
                                <FileText className="w-12 h-12 mx-auto mb-3 opacity-30" />
                                <p>Keine Angebote gefunden</p>
                            </div>
                        )}
                    </div>
                )
                }
            </div >

            {/* Upload Choice Modal */}
            {
                showUploadModal && (
                    <div className="fixed inset-0 bg-black/50 z-50 flex items-end justify-center">
                        <div className="bg-white w-full max-w-md rounded-t-2xl p-6 pb-8 safe-area-bottom animate-slide-up">
                            <div className="flex items-center justify-between mb-6">
                                <h3 className="text-lg font-bold text-slate-900">Bild hinzufügen</h3>
                                <button
                                    onClick={() => setShowUploadModal(false)}
                                    className="p-2 hover:bg-slate-100 rounded-full"
                                >
                                    <X className="w-5 h-5 text-slate-500" />
                                </button>
                            </div>

                            <div className="space-y-3">
                                <button
                                    onClick={() => cameraInputRef.current?.click()}
                                    className="w-full bg-slate-50 hover:bg-slate-100 border border-slate-200 rounded-xl p-4 flex items-center gap-4 transition-colors"
                                >
                                    <div className="w-12 h-12 bg-rose-100 rounded-full flex items-center justify-center">
                                        <Camera className="w-6 h-6 text-rose-600" />
                                    </div>
                                    <div className="flex-1 text-left">
                                        <p className="font-medium text-slate-900">Foto aufnehmen</p>
                                        <p className="text-sm text-slate-500">Kamera öffnen</p>
                                    </div>
                                </button>

                                <button
                                    onClick={() => galleryInputRef.current?.click()}
                                    className="w-full bg-slate-50 hover:bg-slate-100 border border-slate-200 rounded-xl p-4 flex items-center gap-4 transition-colors"
                                >
                                    <div className="w-12 h-12 bg-blue-100 rounded-full flex items-center justify-center">
                                        <FolderOpen className="w-6 h-6 text-blue-600" />
                                    </div>
                                    <div className="flex-1 text-left">
                                        <p className="font-medium text-slate-900">Aus Galerie wählen</p>
                                        <p className="text-sm text-slate-500">Vorhandene Bilder auswählen</p>
                                    </div>
                                </button>
                            </div>

                            <button
                                onClick={() => setShowUploadModal(false)}
                                className="w-full mt-4 py-3 text-slate-500 font-medium"
                            >
                                Abbrechen
                            </button>
                        </div>
                    </div>
                )
            }

            {/* Phone Selection Modal */}
            {
                showPhoneModal && selectedAngebot && (
                    <div className="fixed inset-0 bg-black/50 z-50 flex items-end justify-center">
                        <div className="bg-white w-full max-w-md rounded-t-2xl p-6 pb-8 safe-area-bottom animate-slide-up">
                            <div className="flex items-center justify-between mb-6">
                                <h3 className="text-lg font-bold text-slate-900">Nummer wählen</h3>
                                <button
                                    onClick={() => setShowPhoneModal(false)}
                                    className="p-2 hover:bg-slate-100 rounded-full"
                                >
                                    <X className="w-5 h-5 text-slate-500" />
                                </button>
                            </div>

                            <div className="space-y-3">
                                {selectedAngebot.kundenTelefon && (
                                    <button
                                        onClick={() => handleCall(selectedAngebot.kundenTelefon!)}
                                        className="w-full bg-slate-50 hover:bg-slate-100 border border-slate-200 rounded-xl p-4 flex items-center gap-4 transition-colors"
                                    >
                                        <div className="w-12 h-12 bg-blue-100 rounded-full flex items-center justify-center">
                                            <Phone className="w-6 h-6 text-blue-600" />
                                        </div>
                                        <div className="flex-1 text-left">
                                            <p className="font-medium text-slate-900">Festnetz</p>
                                            <p className="text-slate-500">{selectedAngebot.kundenTelefon}</p>
                                        </div>
                                    </button>
                                )}

                                {selectedAngebot.kundenMobil && (
                                    <button
                                        onClick={() => handleCall(selectedAngebot.kundenMobil!)}
                                        className="w-full bg-slate-50 hover:bg-slate-100 border border-slate-200 rounded-xl p-4 flex items-center gap-4 transition-colors"
                                    >
                                        <div className="w-12 h-12 bg-green-100 rounded-full flex items-center justify-center">
                                            <Smartphone className="w-6 h-6 text-green-600" />
                                        </div>
                                        <div className="flex-1 text-left">
                                            <p className="font-medium text-slate-900">Mobil</p>
                                            <p className="text-slate-500">{selectedAngebot.kundenMobil}</p>
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
                )
            }

            {/* Fullscreen Image Viewer */}
            {viewerIndex !== null && (
                <ImageViewer
                    src={bilder[viewerIndex]?.url ?? null}
                    alt="Angebotsbild Vollansicht"
                    onClose={closeViewer}
                    images={bilder.map(b => ({ url: b.url, name: b.name }))}
                    startIndex={viewerIndex}
                />
            )}
        </div >
    )
}
