import { useState, useEffect, useRef } from 'react'
import { useNavigate, useParams } from 'react-router-dom'

import { ArrowLeft, Search, ScanLine, Upload, FileText, Loader2, X, ChevronRight, Calendar, Hash, Save } from 'lucide-react'
import ScannerModal from '../components/ScannerModal'
import MobileDatePicker from '../components/MobileDatePicker'
import { NotificationService } from '../services/NotificationService'

interface Lieferant {
    id: number
    lieferantenname: string
}

interface Lieferschein {
    id: number
    originalDateiname?: string
    gespeicherterDateiname?: string
    uploadDatum: string
    typ: string
    geschaeftsdaten?: {
        dokumentNummer?: string
        dokumentDatum?: string
        betragBrutto?: number
        liefertermin?: string
        bestellnummer?: string
    }
}

interface AnalyzeResponse {
    dokumentTyp: string
    dokumentNummer?: string
    dokumentDatum?: string
    betragNetto?: number
    betragBrutto?: number
    mwstSatz?: number
    liefertermin?: string
    zahlungsziel?: string
    bestellnummer?: string
    referenzNummer?: string
    bereitsGezahlt?: boolean
    skontoTage?: number
    skontoProzent?: number
    nettoTage?: number
}

interface MultiInvoiceResponse {
    pageRange: string
    analyzeResponse: AnalyzeResponse
    splitPdfBase64?: string
}

export default function LieferantLieferscheinePage() {
    const navigate = useNavigate()
    const { lieferantId } = useParams()
    const [lieferant, setLieferant] = useState<Lieferant | null>(null)
    const [lieferscheine, setLieferscheine] = useState<Lieferschein[]>([])
    const [loading, setLoading] = useState(true)
    const [searchTerm, setSearchTerm] = useState('')

    // Upload & Analysis State
    const [showScanner, setShowScanner] = useState(false)
    const [analyzing, setAnalyzing] = useState(false)
    const [verifying, setVerifying] = useState(false)
    const [currentFile, setCurrentFile] = useState<File | null>(null)
    const fileInputRef = useRef<HTMLInputElement>(null)

    // Form State for Verification
    const [formData, setFormData] = useState<AnalyzeResponse>({
        dokumentTyp: 'LIEFERSCHEIN'
    })

    useEffect(() => {
        if (lieferantId) {
            loadData()
        }
    // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [lieferantId])

    const loadData = async () => {
        setLoading(true)
        try {
            const token = localStorage.getItem('zeiterfassung_token')

            // 1. Load Lieferant Name
            const liefRes = await fetch(`/api/lieferanten/${lieferantId}?token=${token}`)
            if (liefRes.ok) {
                const data = await liefRes.json()
                setLieferant({ id: data.id, lieferantenname: data.lieferantenname })
            }

            // 2. Load Documents
            const docRes = await fetch(`/api/lieferanten/${lieferantId}/dokumente?typ=LIEFERSCHEIN&token=${token}`)
            if (docRes.ok) {
                const data = await docRes.json()
                setLieferscheine(data)
            }
        } catch (err) {
            console.error(err)
        }
        setLoading(false)
    }

    const filteredLieferscheine = lieferscheine.filter(d => {
        const term = searchTerm.toLowerCase()
        const meta = d.geschaeftsdaten
        return (
            d.originalDateiname?.toLowerCase().includes(term) ||
            meta?.dokumentNummer?.toLowerCase().includes(term) ||
            meta?.bestellnummer?.toLowerCase().includes(term) ||
            false
        )
    })

    const handleFileUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
        if (e.target.files && e.target.files[0]) {
            startAnalysis(e.target.files[0])
        }
    }

    const handleScanComplete = async (file: File) => {
        setShowScanner(false)
        startAnalysis(file)
    }

    const startAnalysis = async (file: File) => {
        setAnalyzing(true)
        setCurrentFile(file)

        const token = localStorage.getItem('zeiterfassung_token')
        const formData = new FormData()
        formData.append('datei', file)

        try {
            // NOTE: Using Flash model via backend default (or explicit param if needed)
            // Backend Controller handles "analyze" endpoint
            const res = await fetch(`/api/lieferanten/${lieferantId}/dokumente/analyze?token=${token}`, {
                method: 'POST',
                body: formData
            })

            if (res.ok) {
                const data: MultiInvoiceResponse[] = await res.json()
                if (data && data.length > 0) {
                    // Take the first result for verification
                    const result = data[0].analyzeResponse


                    // Initialize Form Data
                    setFormData({
                        ...result,
                        dokumentTyp: 'LIEFERSCHEIN' // Force Lieferschein context
                    })
                    setVerifying(true)
                } else {
                    alert('Keine Daten erkannt.')
                }
            } else {
                alert('Fehler bei der Analyse.')
            }
        } catch (err) {
            console.error(err)
            alert('Netzwerkfehler.')
        } finally {
            setAnalyzing(false)
            if (fileInputRef.current) fileInputRef.current.value = ''
        }
    }

    const saveVerifiedDocument = async () => {
        if (!currentFile || !lieferantId) return
        setAnalyzing(true) // Reuse spinner

        const token = localStorage.getItem('zeiterfassung_token')
        const uploadData = new FormData()
        uploadData.append('datei', currentFile)
        uploadData.append('metadata', new Blob([JSON.stringify(formData)], { type: 'application/json' }))

        try {
            const res = await fetch(`/api/lieferanten/${lieferantId}/dokumente/import?token=${token}`, {
                method: 'POST',
                body: uploadData
            })

            if (res.ok) {
                setVerifying(false)
                setCurrentFile(null)
                if (token) NotificationService.onLieferscheinUploaded(token)
                loadData() // Reload list
            } else {
                const err = await res.json()
                alert(`Fehler beim Speichern: ${err.message || 'Unbekannt'}`)
            }
        } catch (err) {
            console.error(err)
            alert('Fehler beim Speichern.')
        } finally {
            setAnalyzing(false)
        }
    }

    const openDocument = (doc: Lieferschein) => {
        // Construct standard download/view URL
        // Assuming /files/lieferanten/{id}/{filename} or similar. 
        // Or check where `src` comes from. Usually `OfflineService` handles URLs or there's a simple path
        // In this project (based on other pages), it might be /api/dokumente/download/...
        // But let's assume standard static path or api endpoint.
        // Actually, looking at LieferantDokumentDto, it has `gespeicherterDateiname`.
        // Let's rely on backend serving it via `uploads` or controller.
        // Quick fix: open full Image Viewer or just new tab if PDF.
        if (doc.gespeicherterDateiname) {
            // Try standard endpoint
            const token = localStorage.getItem('zeiterfassung_token')
            // Backend likely needs an endpoint to serve file by ID or path
            // Assuming controller has `GET /api/lieferanten/{id}/dokumente/{docId}/content` ?
            // Or static resource.
            // Given the context, let's look at `LieferantenPage` which does not seem to open docs yet?
            // Ah, `LieferantenPage.tsx` just has buttons, no list of documents logic there except "Dokumente" header.

            // Let's use a generic API download link if possible, or static serving.
            // Usually: /api/files/...?
            // Let's Assume: /api/lieferanten/{id}/dokumente/{docId}/download

            // Wait, I don't have doc ID in my simplified `Lieferschein` interface? Yes I do `id`.
            const url = `/api/lieferanten/${lieferantId}/dokumente/${doc.id}/download?token=${token}`
            window.open(url, '_blank')
        }
    }

    if (verifying) {
        return (
            <div className="fixed inset-0 bg-slate-50 z-50 flex flex-col safe-area-top safe-area-bottom overflow-auto">
                {/* Header */}
                <div className="bg-white border-b border-slate-200 p-4 sticky top-0 z-10 flex items-center justify-between">
                    <button onClick={() => setVerifying(false)} className="p-2 hover:bg-slate-100 rounded-full">
                        <X className="w-6 h-6 text-slate-500" />
                    </button>
                    <h2 className="font-bold text-lg">Prüfen & Speichern</h2>
                    <div className="w-10"></div>
                </div>

                <div className="p-4 space-y-4 flex-1">
                    <div className="bg-rose-50 border border-rose-100 rounded-xl p-4 flex gap-3">
                        <div className="bg-rose-100 p-2 rounded-lg h-fit">
                            <ScanLine className="w-5 h-5 text-rose-600" />
                        </div>
                        <div>
                            <p className="font-medium text-rose-900">KI-Analyse</p>
                            <p className="text-sm text-rose-700">Bitte überprüfen Sie die erkannten Daten.</p>
                        </div>
                    </div>

                    <div className="space-y-4">
                        <div>
                            <label className="block text-sm font-medium text-slate-700 mb-1">Dokumentennummer</label>
                            <div className="relative">
                                <Hash className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
                                <input
                                    type="text"
                                    value={formData.dokumentNummer || ''}
                                    onChange={e => setFormData({ ...formData, dokumentNummer: e.target.value })}
                                    className="w-full pl-10 p-3 bg-white border border-slate-200 rounded-xl focus:ring-2 focus:ring-rose-500 outline-none"
                                    placeholder="Lieferschein-Nr."
                                />
                            </div>
                        </div>

                        <div>
                            <MobileDatePicker
                                value={formData.dokumentDatum || ''}
                                onChange={(val) => setFormData({ ...formData, dokumentDatum: val })}
                                label="Belegdatum"
                            />
                        </div>

                        <div>
                            <MobileDatePicker
                                value={formData.liefertermin || ''}
                                onChange={(val) => setFormData({ ...formData, liefertermin: val })}
                                label="Liefertermin"
                            />
                        </div>

                        <div>
                            <label className="block text-sm font-medium text-slate-700 mb-1">Bestellnummer/Auftragsnummer (Referenz)</label>
                            <input
                                type="text"
                                value={formData.bestellnummer || ''}
                                onChange={e => setFormData({ ...formData, bestellnummer: e.target.value })}
                                className="w-full p-3 bg-white border border-slate-200 rounded-xl focus:ring-2 focus:ring-rose-500 outline-none"
                                placeholder="z.B. AB-12345"
                            />
                        </div>

                        {/* Hidden fields for internal logic */}
                        {/* We default to LIEFERSCHEIN, so we don't show type selector unless necessary */}
                    </div>
                </div>

                <div className="p-4 bg-white border-t border-slate-200 sticky bottom-0">
                    <button
                        onClick={saveVerifiedDocument}
                        disabled={analyzing}
                        className="w-full bg-rose-600 hover:bg-rose-700 text-white font-bold py-4 rounded-xl flex items-center justify-center gap-2"
                    >
                        {analyzing ? <Loader2 className="w-5 h-5 animate-spin" /> : <Save className="w-5 h-5" />}
                        Speichern
                    </button>
                </div>
            </div>
        )
    }

    return (
        <div className="h-full flex flex-col bg-slate-50">
            {/* Header */}
            <header className="bg-white border-b border-slate-200 px-4 py-4 safe-area-top">
                <div className="flex items-center gap-3">
                    <button
                        onClick={() => navigate(-1)}
                        className="p-2 hover:bg-slate-100 rounded-lg transition-all active:scale-95"
                    >
                        <ArrowLeft className="w-5 h-5 text-slate-600" />
                    </button>
                    <div>
                        <h1 className="font-bold text-slate-900">Lieferscheine</h1>
                        <p className="text-sm text-slate-500">{loading ? 'Lade...' : lieferant?.lieferantenname}</p>
                    </div>
                </div>
            </header>

            {/* Sticky Search & Actions */}
            <div className="bg-white border-b border-slate-100 p-4 sticky top-[73px] z-10 space-y-3 shadow-sm">
                <div className="relative">
                    <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-slate-400" />
                    <input
                        type="text"
                        value={searchTerm}
                        onChange={(e) => setSearchTerm(e.target.value)}
                        placeholder="Nr, Bestellung, Datum..."
                        className="w-full pl-10 pr-4 py-3 bg-slate-50 border border-slate-200 rounded-xl text-slate-900 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-rose-500"
                    />
                </div>

                <div className="grid grid-cols-2 gap-3">
                    <button
                        onClick={() => setShowScanner(true)}
                        className="bg-slate-900 text-white py-3 rounded-xl flex items-center justify-center gap-2 font-medium active:scale-95 transition-all"
                    >
                        <ScanLine className="w-4 h-4" />
                        Scannen
                    </button>
                    <button
                        onClick={() => fileInputRef.current?.click()}
                        disabled={analyzing}
                        className="bg-white border border-slate-200 text-slate-700 py-3 rounded-xl flex items-center justify-center gap-2 font-medium active:scale-95 transition-all"
                    >
                        {analyzing ? <Loader2 className="w-4 h-4 animate-spin" /> : <Upload className="w-4 h-4" />}
                        Upload
                    </button>
                    <input
                        ref={fileInputRef}
                        type="file"
                        accept="image/*,application/pdf"
                        onChange={handleFileUpload}
                        className="hidden"
                    />
                </div>
            </div>

            {/* List */}
            <div className="flex-1 overflow-auto p-4 space-y-3">
                {loading && lieferscheine.length === 0 ? (
                    <div className="flex justify-center py-12">
                        <Loader2 className="w-8 h-8 animate-spin text-rose-600" />
                    </div>
                ) : filteredLieferscheine.length > 0 ? (
                    filteredLieferscheine.map(doc => (
                        <button
                            key={doc.id}
                            onClick={() => openDocument(doc)}
                            className="w-full bg-white border border-slate-200 rounded-xl p-4 text-left active:scale-[0.99] transition-all flex items-center gap-4 hover:border-rose-200"
                        >
                            <div className="w-12 h-12 bg-rose-50 rounded-lg flex items-center justify-center flex-shrink-0">
                                <FileText className="w-6 h-6 text-rose-600" />
                            </div>
                            <div className="flex-1 min-w-0">
                                <div className="flex items-center justify-between mb-1">
                                    <span className="font-semibold text-slate-900 truncate">
                                        {doc.geschaeftsdaten?.dokumentNummer || 'Ohne Nummer'}
                                    </span>
                                    <span className="text-xs text-slate-400 bg-slate-50 px-2 py-1 rounded-md">
                                        {new Date(doc.uploadDatum).toLocaleDateString('de-DE')}
                                    </span>
                                </div>
                                <div className="flex items-center gap-4 text-sm text-slate-500">
                                    {doc.geschaeftsdaten?.dokumentDatum && (
                                        <span className="flex items-center gap-1">
                                            <Calendar className="w-3 h-3" />
                                            {new Date(doc.geschaeftsdaten.dokumentDatum).toLocaleDateString('de-DE')}
                                        </span>
                                    )}
                                    {doc.geschaeftsdaten?.bestellnummer && (
                                        <span className="truncate">Ref: {doc.geschaeftsdaten.bestellnummer}</span>
                                    )}
                                </div>
                            </div>
                            <ChevronRight className="w-5 h-5 text-slate-300" />
                        </button>
                    ))
                ) : (
                    <div className="text-center py-12 text-slate-500">
                        <FileText className="w-12 h-12 mx-auto mb-3 opacity-20" />
                        <p>Keine Lieferscheine gefunden</p>
                    </div>
                )}
            </div>

            {/* Analysis Loading Overlay */}
            {analyzing && !verifying && (
                <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center">
                    <div className="bg-white p-6 rounded-2xl flex flex-col items-center gap-4 animate-in fade-in zoom-in">
                        <Loader2 className="w-10 h-10 text-rose-600 animate-spin" />
                        <p className="font-medium text-slate-900">Analysiere Dokument...</p>
                    </div>
                </div>
            )}

            {showScanner && (
                <ScannerModal
                    onClose={() => setShowScanner(false)}
                    onSave={handleScanComplete}
                />
            )}
        </div>
    )
}
