import { useState, useEffect, useRef } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { ArrowLeft, MessageCircle, Plus, Loader2, Send, X, User, Edit2, Trash2, Camera, Image, Lock } from 'lucide-react'
import { ImageViewer } from '../components/ui/image-viewer'

interface NotizBild {
    id: number
    originalDateiname: string
    url: string
    erstelltAm: string
}

interface Notiz {
    id: number
    notiz: string
    erstelltAm: string
    mitarbeiterId: number
    mitarbeiterVorname: string
    mitarbeiterNachname: string
    mobileSichtbar: boolean
    nurFuerErsteller: boolean
    canEdit?: boolean
    bilder?: NotizBild[]
}

export default function ProjektNotizenPage() {
    const { projektId } = useParams<{ projektId: string }>()
    const navigate = useNavigate()
    const [notizen, setNotizen] = useState<Notiz[]>([])
    const [loading, setLoading] = useState(true)
    const [showModal, setShowModal] = useState(false)
    const [neueNotiz, setNeueNotiz] = useState('')
    const [mobileSichtbar, setMobileSichtbar] = useState(true)
    const [nurFuerErsteller, setNurFuerErsteller] = useState(false)
    const [editingNotiz, setEditingNotiz] = useState<Notiz | null>(null)
    const [saving, setSaving] = useState(false)
    const [uploadingBildNotizId, setUploadingBildNotizId] = useState<number | null>(null)
    const [viewerNotizBilder, setViewerNotizBilder] = useState<NotizBild[]>([])
    const [viewerIndex, setViewerIndex] = useState<number | null>(null)
    const cameraInputRef = useRef<HTMLInputElement>(null)
    const galleryInputRef = useRef<HTMLInputElement>(null)
    const [selectedNotizForImage, setSelectedNotizForImage] = useState<number | null>(null)

    useEffect(() => {
        if (projektId) {
            loadNotizen()
        }
    }, [projektId])

    const loadNotizen = async () => {
        setLoading(true)
        try {
            const token = localStorage.getItem('zeiterfassung_token')
            const res = await fetch(`/api/projekte/${projektId}/notizen?token=${token}`)
            if (res.ok) {
                const data = await res.json()
                setNotizen(data)
            }
        } catch (err) {
            console.error('Fehler beim Laden der Notizen:', err)
        }
        setLoading(false)
    }

    const handleSave = async () => {
        if (!neueNotiz.trim()) return
        setSaving(true)
        try {
            const token = localStorage.getItem('zeiterfassung_token')
            const url = editingNotiz
                ? `/api/projekte/${projektId}/notizen/${editingNotiz.id}?token=${token}`
                : `/api/projekte/${projektId}/notizen?token=${token}`

            const method = editingNotiz ? 'PATCH' : 'POST'

            const res = await fetch(url, {
                method: method,
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    notiz: neueNotiz.trim(),
                    mobileSichtbar: mobileSichtbar,
                    nurFuerErsteller: nurFuerErsteller
                })
            })
            if (res.ok) {
                setNeueNotiz('')
                setNurFuerErsteller(false)
                setEditingNotiz(null)
                setShowModal(false)
                loadNotizen()
            } else {
                alert('Fehler beim Speichern der Notiz')
            }
        } catch (err) {
            console.error('Fehler beim Speichern:', err)
            alert('Fehler beim Speichern')
        }
        setSaving(false)
    }

    const handleDelete = async (notizId: number) => {
        if (!window.confirm('Notiz wirklich löschen?')) return
        try {
            const token = localStorage.getItem('zeiterfassung_token')
            const res = await fetch(`/api/projekte/${projektId}/notizen/${notizId}?token=${token}`, {
                method: 'DELETE'
            })
            if (res.ok) {
                loadNotizen()
            } else {
                alert('Fehler beim Löschen')
            }
        } catch (err) {
            console.error(err)
            alert('Fehler beim Löschen')
        }
    }

    const handleImageUpload = async (notizId: number, file: File) => {
        setUploadingBildNotizId(notizId)
        try {
            const token = localStorage.getItem('zeiterfassung_token')
            const formData = new FormData()
            formData.append('datei', file)

            const res = await fetch(`/api/projekte/${projektId}/notizen/${notizId}/bilder?token=${token}`, {
                method: 'POST',
                body: formData
            })
            if (res.ok) {
                loadNotizen()
            } else {
                alert('Fehler beim Hochladen des Bildes')
            }
        } catch (err) {
            console.error('Fehler beim Hochladen:', err)
            alert('Fehler beim Hochladen')
        }
        setUploadingBildNotizId(null)
        setSelectedNotizForImage(null)
    }

    const handleDeleteImage = async (notizId: number, bildId: number) => {
        if (!window.confirm('Bild wirklich löschen?')) return
        try {
            const token = localStorage.getItem('zeiterfassung_token')
            const res = await fetch(`/api/projekte/${projektId}/notizen/${notizId}/bilder/${bildId}?token=${token}`, {
                method: 'DELETE'
            })
            if (res.ok) {
                loadNotizen()
            } else {
                alert('Fehler beim Löschen des Bildes')
            }
        } catch (err) {
            console.error(err)
            alert('Fehler beim Löschen')
        }
    }

    const handleFileSelect = async (e: React.ChangeEvent<HTMLInputElement>) => {
        const files = e.target.files
        if (files && files.length > 0 && selectedNotizForImage) {
            // Convert FileList to array for easier iteration
            const fileArray = Array.from(files)

            // Upload files sequentially to avoid overwhelming the server or UI state
            for (const file of fileArray) {
                await handleImageUpload(selectedNotizForImage, file)
            }
        }
        e.target.value = '' // Reset input
        setSelectedNotizForImage(null) // Reset after all uploads are initiated
    }

    const openCamera = (notizId: number) => {
        setSelectedNotizForImage(notizId)
        cameraInputRef.current?.click()
    }

    const openGallery = (notizId: number) => {
        setSelectedNotizForImage(notizId)
        galleryInputRef.current?.click()
    }

    const openCreateModal = () => {
        setNeueNotiz('')
        setNurFuerErsteller(false)
        setEditingNotiz(null)
        setMobileSichtbar(true)
        setShowModal(true)
    }

    const openEditModal = (n: Notiz) => {
        setNeueNotiz(n.notiz)
        setNurFuerErsteller(!!n.nurFuerErsteller)
        setMobileSichtbar(!!n.mobileSichtbar)
        setEditingNotiz(n)
        setShowModal(true)
    }

    const formatDateTime = (dateStr: string) => {
        if (!dateStr) return ''
        const date = new Date(dateStr);
        if (isNaN(date.getTime())) return dateStr; // Fallback if already formatted
        return date.toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric' }) + ' ' +
            date.toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit' });
    }

    return (
        <div className="h-full flex flex-col bg-slate-50">
            {/* Hidden file inputs */}
            <input
                ref={cameraInputRef}
                type="file"
                accept="image/*"
                capture="environment"
                onChange={handleFileSelect}
                className="hidden"
            />
            <input
                ref={galleryInputRef}
                type="file"
                accept="image/*"
                multiple
                onChange={handleFileSelect}
                className="hidden"
            />

            {/* Header */}
            <header className="bg-white border-b border-slate-200 px-4 py-4 safe-area-top">
                <div className="flex items-center gap-3">
                    <button
                        onClick={() => navigate(-1)}
                        className="p-2 hover:bg-slate-100 rounded-lg transition-all active:scale-95"
                    >
                        <ArrowLeft className="w-5 h-5 text-slate-600" />
                    </button>
                    <div className="flex items-center gap-3">
                        <div className="w-10 h-10 bg-rose-50 rounded-lg flex items-center justify-center">
                            <MessageCircle className="w-5 h-5 text-rose-600" />
                        </div>
                        <div>
                            <h1 className="font-bold text-slate-900">Bau Tagebuch</h1>
                            <p className="text-sm text-slate-500">{notizen.length} Einträge</p>
                        </div>
                    </div>
                    <button
                        onClick={openCreateModal}
                        className="ml-auto p-2 bg-rose-600 hover:bg-rose-700 text-white rounded-lg transition-colors active:scale-95"
                    >
                        <Plus className="w-5 h-5" />
                    </button>
                </div>
            </header>

            {/* Content */}
            <div className="flex-1 overflow-auto p-4">
                {loading ? (
                    <div className="flex justify-center py-12">
                        <Loader2 className="w-8 h-8 animate-spin text-rose-600" />
                    </div>
                ) : notizen.length === 0 ? (
                    <div className="text-center py-12 text-slate-500">
                        <MessageCircle className="w-12 h-12 mx-auto mb-3 opacity-30" />
                        <p>Kein Bau Tagebuch</p>
                        <p className="text-sm mt-1">Tippe auf + um einen Eintrag hinzuzufügen</p>
                    </div>
                ) : (
                    <div className="space-y-3">
                        {notizen.map(notiz => (
                            <div key={notiz.id} className="bg-white rounded-2xl p-4 border border-slate-200">
                                {/* Speech bubble style */}
                                <div className="flex items-start gap-3">
                                    <div className="w-8 h-8 bg-rose-100 rounded-full flex items-center justify-center flex-shrink-0">
                                        <User className="w-4 h-4 text-rose-600" />
                                    </div>
                                    <div className="flex-1 min-w-0">
                                        <div className="flex items-center gap-2 mb-1">
                                            <span className="font-medium text-slate-900 text-sm">
                                                {notiz.mitarbeiterVorname} {notiz.mitarbeiterNachname}
                                            </span>
                                            <span className="text-xs text-slate-400">
                                                {formatDateTime(notiz.erstelltAm)}
                                            </span>
                                            {notiz.nurFuerErsteller && (
                                                <Lock className="w-3 h-3 text-amber-500 ml-1" />
                                            )}
                                        </div>
                                        <p className="text-slate-700 whitespace-pre-wrap break-words">
                                            {notiz.notiz}
                                        </p>

                                        {/* Bilder */}
                                        {notiz.bilder && notiz.bilder.length > 0 && (
                                            <div className="mt-3 grid grid-cols-3 gap-2">
                                                {notiz.bilder.map((bild, idx) => (
                                                    <div key={bild.id} className="relative">
                                                        <button
                                                            onClick={() => {
                                                                setViewerNotizBilder(notiz.bilder!)
                                                                setViewerIndex(idx)
                                                            }}
                                                            className="aspect-square rounded-lg overflow-hidden bg-slate-100 hover:ring-2 hover:ring-rose-500 transition-all w-full"
                                                        >
                                                            <img
                                                                src={bild.url + '/thumbnail'}
                                                                alt={bild.originalDateiname}
                                                                className="w-full h-full object-cover pointer-events-none"
                                                                loading="lazy"
                                                                onError={(e) => {
                                                                    // Fallback auf Original wenn Thumbnail fehlschlägt
                                                                    const img = e.target as HTMLImageElement
                                                                    if (img.src.includes('/thumbnail')) {
                                                                        img.src = bild.url
                                                                    }
                                                                }}
                                                            />
                                                        </button>
                                                        {notiz.canEdit && (
                                                            <button
                                                                onClick={() => handleDeleteImage(notiz.id, bild.id)}
                                                                className="absolute top-1 right-1 p-1 bg-red-500 hover:bg-red-600 text-white rounded-full shadow"
                                                            >
                                                                <X className="w-3 h-3" />
                                                            </button>
                                                        )}
                                                    </div>
                                                ))}
                                            </div>
                                        )}

                                        {/* Bild hinzufügen Buttons - nur wenn canEdit */}
                                        {notiz.canEdit && (
                                            <div className="mt-3 flex gap-2">
                                                {uploadingBildNotizId === notiz.id ? (
                                                    <div className="flex items-center gap-2 text-rose-600 text-sm">
                                                        <Loader2 className="w-4 h-4 animate-spin" />
                                                        Wird hochgeladen...
                                                    </div>
                                                ) : (
                                                    <>
                                                        <button
                                                            onClick={() => openCamera(notiz.id)}
                                                            className="flex items-center gap-1 text-xs text-slate-500 hover:text-rose-600 px-2 py-1 rounded-lg hover:bg-rose-50 transition-colors"
                                                        >
                                                            <Camera className="w-3.5 h-3.5" />
                                                            Kamera
                                                        </button>
                                                        <button
                                                            onClick={() => openGallery(notiz.id)}
                                                            className="flex items-center gap-1 text-xs text-slate-500 hover:text-rose-600 px-2 py-1 rounded-lg hover:bg-rose-50 transition-colors"
                                                        >
                                                            <Image className="w-3.5 h-3.5" />
                                                            Galerie
                                                        </button>
                                                    </>
                                                )}
                                            </div>
                                        )}
                                    </div>
                                    {notiz.canEdit && (
                                        <div className="flex flex-col gap-1 ml-2">
                                            <button
                                                onClick={() => openEditModal(notiz)}
                                                className="p-1.5 text-slate-400 hover:bg-slate-100 rounded-full active:bg-slate-200"
                                            >
                                                <Edit2 className="w-4 h-4" />
                                            </button>
                                            <button
                                                onClick={() => handleDelete(notiz.id)}
                                                className="p-1.5 text-slate-400 hover:bg-red-50 hover:text-red-600 rounded-full active:bg-red-100"
                                            >
                                                <Trash2 className="w-4 h-4" />
                                            </button>
                                        </div>
                                    )}
                                </div>
                            </div>
                        ))}
                    </div>
                )}
            </div>

            {/* New Note Modal */}
            {showModal && (
                <div
                    className="fixed inset-0 bg-black/50 z-50 flex items-end justify-center"
                    onClick={() => setShowModal(false)}
                >
                    <div
                        className="bg-white rounded-t-2xl w-full max-w-lg p-6 safe-area-bottom animate-slide-up"
                        onClick={(e) => e.stopPropagation()}
                    >
                        <div className="flex items-center justify-between mb-4">
                            <h3 className="text-lg font-bold text-slate-900">{editingNotiz ? 'Eintrag bearbeiten' : 'Neuer Eintrag'}</h3>
                            <button
                                onClick={() => setShowModal(false)}
                                className="p-2 hover:bg-slate-100 rounded-full"
                            >
                                <X className="w-5 h-5 text-slate-500" />
                            </button>
                        </div>
                        <textarea
                            value={neueNotiz}
                            onChange={(e) => setNeueNotiz(e.target.value)}
                            placeholder="Eintrag eingeben..."
                            rows={4}
                            className="w-full p-3 border border-slate-200 rounded-xl text-slate-900 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-rose-500 resize-none"
                            autoFocus
                        />
                        <div className="space-y-3 mt-4">
                            <label className="flex items-center gap-3 p-3 rounded-xl border border-slate-200 bg-slate-50">
                                <input
                                    type="checkbox"
                                    checked={nurFuerErsteller}
                                    onChange={(e) => setNurFuerErsteller(e.target.checked)}
                                    className="w-5 h-5 rounded border-slate-300 text-rose-600 focus:ring-rose-500"
                                />
                                <div className="flex-1">
                                    <div className="text-sm font-medium text-slate-900">Nur für mich sichtbar</div>
                                    <div className="text-xs text-slate-500">Andere Mitarbeiter sehen diese Notiz nicht</div>
                                </div>
                                <Lock className="w-4 h-4 text-slate-400" />
                            </label>
                            {/* Checkbox "Auf Mobilgeräten sichtbar" ausgeblendet, da von Mobile immer sichtbar */}
                        </div>
                        <button
                            onClick={handleSave}
                            disabled={!neueNotiz.trim() || saving}
                            className="w-full mt-4 py-3 bg-rose-600 hover:bg-rose-700 disabled:bg-slate-300 text-white font-medium rounded-xl flex items-center justify-center gap-2 transition-colors"
                        >
                            {saving ? (
                                <Loader2 className="w-5 h-5 animate-spin" />
                            ) : (
                                <>
                                    <Send className="w-5 h-5" />
                                    Eintrag speichern
                                </>
                            )}
                        </button>
                    </div>
                </div>
            )}

            {/* Image Viewer Modal */}
            <ImageViewer
                src={viewerIndex !== null && viewerNotizBilder[viewerIndex] ? viewerNotizBilder[viewerIndex].url : null}
                alt="Notiz-Bild"
                onClose={() => { setViewerIndex(null); setViewerNotizBilder([]); }}
                images={viewerNotizBilder.map(b => ({ url: b.url, name: b.originalDateiname }))}
                startIndex={viewerIndex ?? 0}
            />
        </div>
    )
}
