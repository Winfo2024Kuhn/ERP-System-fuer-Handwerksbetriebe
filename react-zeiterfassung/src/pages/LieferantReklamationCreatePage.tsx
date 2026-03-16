import { useState, useRef, useEffect } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { ArrowLeft, Save, Plus, X, Search, FileText, Camera, AlertTriangle, Loader2 } from 'lucide-react'
import { NotificationService } from '../services/NotificationService'

interface LieferscheinResult {
    id: number
    dokumentNummer?: string
    originalDateiname: string
    datum: string
}

export default function LieferantReklamationCreatePage() {
    const { lieferantId } = useParams()
    const navigate = useNavigate()
    const [loading, setLoading] = useState(false)
    const [searchQuery, setSearchQuery] = useState('')
    const [lieferscheinResults, setLieferscheinResults] = useState<LieferscheinResult[]>([])
    const [searching, setSearching] = useState(false)

    // Form State
    const [beschreibung, setBeschreibung] = useState('')
    const [selectedLieferschein, setSelectedLieferschein] = useState<LieferscheinResult | null>(null)
    const [images, setImages] = useState<File[]>([])

    const fileInputRef = useRef<HTMLInputElement>(null)

    // Lieferschein Search
    useEffect(() => {
        if (!searchQuery || searchQuery.length < 2) {
            setLieferscheinResults([])
            return
        }

        const delayDebounceFn = setTimeout(async () => {
            setSearching(true)
            try {
                const token = localStorage.getItem('zeiterfassung_token')
                const res = await fetch(`/api/reklamationen/lieferscheine/search?lieferantId=${lieferantId}&query=${encodeURIComponent(searchQuery)}&token=${token}`)
                if (res.ok) {
                    const data = await res.json()
                    setLieferscheinResults(data)
                }
            } catch (err) {
                console.error("Search error", err)
            }
            setSearching(false)
        }, 500)

        return () => clearTimeout(delayDebounceFn)
    }, [searchQuery, lieferantId])

    const handleAddImage = (e: React.ChangeEvent<HTMLInputElement>) => {
        if (e.target.files && e.target.files.length > 0) {
            const newFiles = Array.from(e.target.files)
            setImages([...images, ...newFiles])
        }
    }

    const removeImage = (index: number) => {
        setImages(images.filter((_, i) => i !== index))
    }

    const handleSubmit = async () => {
        if (!beschreibung && images.length === 0) {
            alert("Bitte geben Sie eine Beschreibung ein oder fügen Sie Bilder hinzu.")
            return
        }

        setLoading(true)
        const token = localStorage.getItem('zeiterfassung_token')

        try {
            // 1. Create Reclamation
            const res = await fetch(`/api/reklamationen/lieferant/${lieferantId}?token=${token}`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    beschreibung,
                    lieferscheinId: selectedLieferschein?.id,
                    status: 'OFFEN'
                })
            })

            if (!res.ok) throw new Error('Fehler beim Erstellen der Reklamation')
            const reklamation = await res.json()

            // 2. Upload Images
            for (const image of images) {
                const formData = new FormData()
                formData.append('datei', image)

                await fetch(`/api/reklamationen/${reklamation.id}/bilder?token=${token}`, {
                    method: 'POST',
                    body: formData
                })
            }

            if (token) NotificationService.onReklamationCreated(token)
            alert('Reklamation erfolgreich erstellt')
            navigate(`/lieferanten/${lieferantId}/reklamationen`)

        } catch (err) {
            console.error(err)
            alert('Fehler beim Speichern der Reklamation')
        }
        setLoading(false)
    }

    return (
        <div className="h-full flex flex-col bg-slate-50 safe-area-top safe-area-bottom">
            {/* Header */}
            <div className="bg-white border-b border-slate-200 px-4 py-4 flex items-center justify-between">
                <div className="flex items-center gap-3">
                    <button
                        onClick={() => navigate(-1)}
                        className="p-2 hover:bg-slate-100 rounded-full transition-colors"
                    >
                        <ArrowLeft className="w-6 h-6 text-slate-600" />
                    </button>
                    <h1 className="font-bold text-lg text-slate-900">Reklamation erfassen</h1>
                </div>
                <button
                    onClick={handleSubmit}
                    disabled={loading}
                    className="bg-rose-600 text-white px-4 py-2 rounded-lg font-medium flex items-center gap-2 hover:bg-rose-700 disabled:opacity-50"
                >
                    {loading ? <Loader2 className="w-5 h-5 animate-spin" /> : <Save className="w-5 h-5" />}
                    Speichern
                </button>
            </div>

            <div className="flex-1 overflow-y-auto p-4 space-y-6">

                {/* 1. Lieferschein Auswahl */}
                <div className="bg-white rounded-2xl p-4 border border-slate-200 shadow-sm">
                    <h3 className="font-semibold text-slate-900 mb-3 flex items-center gap-2">
                        <FileText className="w-5 h-5 text-rose-600" />
                        Lieferschein zuweisen
                    </h3>

                    {selectedLieferschein ? (
                        <div className="bg-rose-50 border border-rose-100 rounded-xl p-3 flex items-center justify-between">
                            <div>
                                <p className="font-medium text-slate-900">
                                    {selectedLieferschein.dokumentNummer || selectedLieferschein.originalDateiname}
                                </p>
                                <p className="text-xs text-slate-500">
                                    {selectedLieferschein.datum}
                                </p>
                            </div>
                            <button
                                onClick={() => setSelectedLieferschein(null)}
                                className="p-2 hover:bg-rose-100 rounded-full text-rose-600"
                            >
                                <X className="w-5 h-5" />
                            </button>
                        </div>
                    ) : (
                        <div className="relative">
                            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-slate-400" />
                            <input
                                type="text"
                                placeholder="Lieferschein suchen (Nr. oder Dateiname)..."
                                value={searchQuery}
                                onChange={(e) => setSearchQuery(e.target.value)}
                                className="w-full pl-10 pr-4 py-3 bg-slate-50 border border-slate-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-rose-500"
                            />
                            {searching && (
                                <div className="absolute right-3 top-1/2 -translate-y-1/2">
                                    <Loader2 className="w-4 h-4 animate-spin text-slate-400" />
                                </div>
                            )}

                            {/* Suchergebnisse Dropdown */}
                            {lieferscheinResults.length > 0 && (
                                <div className="absolute top-full left-0 right-0 mt-2 bg-white border border-slate-200 rounded-xl shadow-lg z-10 max-h-60 overflow-y-auto">
                                    {lieferscheinResults.map(ls => (
                                        <button
                                            key={ls.id}
                                            onClick={() => {
                                                setSelectedLieferschein(ls)
                                                setSearchQuery('')
                                                setLieferscheinResults([])
                                            }}
                                            className="w-full text-left p-3 hover:bg-slate-50 border-b border-slate-100 last:border-0"
                                        >
                                            <p className="font-medium text-slate-900">{ls.dokumentNummer || ls.originalDateiname}</p>
                                            <div className="flex justify-between mt-1 text-xs text-slate-500">
                                                <span>{ls.datum}</span>
                                                {ls.dokumentNummer && <span className="truncate max-w-[150px]">{ls.originalDateiname}</span>}
                                            </div>
                                        </button>
                                    ))}
                                </div>
                            )}
                        </div>
                    )}
                </div>

                {/* 2. Beschreibung */}
                <div className="bg-white rounded-2xl p-4 border border-slate-200 shadow-sm">
                    <h3 className="font-semibold text-slate-900 mb-3 flex items-center gap-2">
                        <AlertTriangle className="w-5 h-5 text-amber-500" />
                        Problembeschreibung
                    </h3>
                    <textarea
                        value={beschreibung}
                        onChange={(e) => setBeschreibung(e.target.value)}
                        placeholder="Was ist beschädigt oder fehlt?"
                        className="w-full h-32 p-3 bg-slate-50 border border-slate-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-rose-500 resize-none"
                    />
                </div>

                {/* 3. Bilder */}
                <div className="bg-white rounded-2xl p-4 border border-slate-200 shadow-sm">
                    <div className="flex items-center justify-between mb-4">
                        <h3 className="font-semibold text-slate-900 flex items-center gap-2">
                            <Camera className="w-5 h-5 text-blue-500" />
                            Bilder ({images.length})
                        </h3>
                        <button
                            onClick={() => fileInputRef.current?.click()}
                            className="bg-slate-100 hover:bg-slate-200 text-slate-700 px-3 py-1.5 rounded-lg text-sm font-medium flex items-center gap-2 transition-colors"
                        >
                            <Plus className="w-4 h-4" />
                            Hinzufügen
                        </button>
                        <input
                            type="file"
                            ref={fileInputRef}
                            onChange={handleAddImage}
                            multiple
                            accept="image/*"
                            capture="environment"
                            className="hidden"
                        />
                    </div>

                    {images.length === 0 ? (
                        <div
                            onClick={() => fileInputRef.current?.click()}
                            className="border-2 border-dashed border-slate-200 rounded-xl p-8 flex flex-col items-center justify-center text-slate-400 cursor-pointer hover:bg-slate-50 transition-colors"
                        >
                            <Camera className="w-10 h-10 mb-2 opacity-50" />
                            <p className="text-sm">Tippen für Fotoaufnahme</p>
                        </div>
                    ) : (
                        <div className="grid grid-cols-3 gap-3">
                            {images.map((img, idx) => (
                                <div key={idx} className="relative aspect-square rounded-xl overflow-hidden bg-slate-100 border border-slate-200">
                                    <img
                                        src={URL.createObjectURL(img)}
                                        alt={`Preview ${idx}`}
                                        className="w-full h-full object-cover"
                                    />
                                    <button
                                        onClick={() => removeImage(idx)}
                                        className="absolute top-1 right-1 bg-black/50 p-1 rounded-full text-white backdrop-blur-sm"
                                    >
                                        <X className="w-4 h-4" />
                                    </button>
                                </div>
                            ))}
                        </div>
                    )}
                </div>
            </div>
        </div>
    )
}
