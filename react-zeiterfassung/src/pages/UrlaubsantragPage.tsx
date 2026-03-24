import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { ArrowLeft, FileText, Send, Loader2, CheckCircle2, Plane, Stethoscope, Calendar, AlertTriangle, X, Clock, RefreshCw } from 'lucide-react'

interface UrlaubsantragPageProps {
    mitarbeiter: { id: number; name: string } | null
    syncStatus?: 'syncing' | 'done' | 'error'
    onSync?: () => void
}

interface Urlaubsantrag {
    id: number
    mitarbeiter: { id: number; vorname: string; nachname: string }
    vonDatum: string
    bisDatum: string
    bemerkung: string
    status: 'OFFEN' | 'GENEHMIGT' | 'ABGELEHNT' | 'STORNIERT'
    typ: 'URLAUB' | 'KRANKHEIT' | 'FORTBILDUNG' | 'ZEITAUSGLEICH' | 'ARBEIT' | 'PAUSE'
}

interface Feiertag {
    datum: string
    bezeichnung: string
}

const STATUS_FILTER_OPTIONS = [
    { id: 'ALLE', label: 'Alle' },
    { id: 'OFFEN', label: 'Offen' },
    { id: 'GENEHMIGT', label: 'Genehmigt' },
    { id: 'ABGELEHNT', label: 'Abgelehnt' },
] as const

export default function UrlaubsantragPage({ mitarbeiter, syncStatus, onSync }: UrlaubsantragPageProps) {
    const navigate = useNavigate()

    // Tab State
    const [activeTab, setActiveTab] = useState<'BEANTRAGEN' | 'UEBERSICHT'>('BEANTRAGEN')

    // Form State
    const [typ, setTyp] = useState<'URLAUB' | 'KRANKHEIT' | 'FORTBILDUNG' | 'ZEITAUSGLEICH'>('URLAUB')
    const [von, setVon] = useState('')
    const [bis, setBis] = useState('')
    const [bemerkung, setBemerkung] = useState('')
    const [loading, setLoading] = useState(false)
    const [success, setSuccess] = useState(false)
    const [error, setError] = useState<string | null>(null)

    // Resturlaub State
    const [resturlaub, setResturlaub] = useState<number | null>(null)
    const [loadingResturlaub, setLoadingResturlaub] = useState(false)

    // Overview State
    const [antraege, setAntraege] = useState<Urlaubsantrag[]>([])
    const [loadingAntraege, setLoadingAntraege] = useState(false)
    const [selectedYear, setSelectedYear] = useState(new Date().getFullYear())
    const [statusFilter, setStatusFilter] = useState<'ALLE' | 'OFFEN' | 'GENEHMIGT' | 'ABGELEHNT'>('ALLE')

    // Feiertag Modal State
    const [showFeiertagModal, setShowFeiertagModal] = useState(false)
    const [gefundeneFeiertage, setGefundeneFeiertage] = useState<Feiertag[]>([])

    // Fetch Antraege when tab is overview or year changes
    useEffect(() => {
        if (activeTab === 'UEBERSICHT' && mitarbeiter) {
            fetchAntraege()
        }
    }, [activeTab, selectedYear, mitarbeiter])

    // Fetch Resturlaub when typ=URLAUB and mitarbeiter available
    useEffect(() => {
        if (mitarbeiter && typ === 'URLAUB') {
            fetchResturlaub()
        } else {
            setResturlaub(null)
        }
    }, [mitarbeiter, typ])

    const fetchResturlaub = async () => {
        if (!mitarbeiter) return
        setLoadingResturlaub(true)
        try {
            const res = await fetch(`/api/urlaub/resturlaub?mitarbeiterId=${mitarbeiter.id}`)
            if (res.ok) {
                const data = await res.json()
                setResturlaub(data.verbleibend)
            }
        } catch (err) {
            console.error('Fehler beim Laden des Resturlaubs:', err)
        } finally {
            setLoadingResturlaub(false)
        }
    }

    /**
     * Zählt Arbeitstage (Mo–Fr) zwischen von und bis (inkl.).
     * Feiertage werden serverseitig abgezogen – hier nur grobe Schätzung.
     */
    const zaehleArbeitstage = (vonStr: string, bisStr: string): number => {
        if (!vonStr || !bisStr) return 0
        const start = new Date(vonStr)
        const end = new Date(bisStr)
        let count = 0
        const d = new Date(start)
        while (d <= end) {
            const day = d.getDay()
            if (day !== 0 && day !== 6) count++
            d.setDate(d.getDate() + 1)
        }
        return count
    }

    const beantragteTage = zaehleArbeitstage(von, bis)
    const ueberschritten = typ === 'URLAUB' && resturlaub !== null && beantragteTage > resturlaub

    const fetchAntraege = async () => {
        if (!mitarbeiter) return
        setLoadingAntraege(true)
        try {
            // Fetch by year (filtering by status happens client-side for better UX with small lists)
            const res = await fetch(`/api/urlaub/antraege?mitarbeiterId=${mitarbeiter.id}&jahr=${selectedYear}`)
            if (res.ok) {
                const data = await res.json()
                // Sort by date desc
                data.sort((a: Urlaubsantrag, b: Urlaubsantrag) => new Date(b.vonDatum).getTime() - new Date(a.vonDatum).getTime())
                setAntraege(data)
            }
        } catch (error) {
            console.error("Failed to fetch antraege", error)
        } finally {
            setLoadingAntraege(false)
        }
    }

    // Prüfe auf Feiertage im gewählten Zeitraum
    const checkFeiertage = async (): Promise<Feiertag[]> => {
        if (!von || !bis) return []
        try {
            const res = await fetch(`/api/zeitverwaltung/feiertage/zwischen?von=${von}&bis=${bis}`)
            if (res.ok) {
                const data = await res.json()
                return Array.isArray(data) ? data : []
            }
        } catch (err) {
            console.error('Fehler beim Laden der Feiertage:', err)
        }
        return []
    }

    // Antrag ohne Feiertage absenden
    const submitAntrag = async () => {
        if (!mitarbeiter) return

        setLoading(true)
        setError(null)

        try {
            const res = await fetch('/api/urlaub/antraege', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    mitarbeiterId: mitarbeiter.id,
                    typ,
                    von,
                    bis,
                    bemerkung
                })
            })

            if (res.ok) {
                setSuccess(true)
                setShowFeiertagModal(false)
                fetchResturlaub() // Resturlaub aktualisieren
                // Switch to overview after success after a delay
                setTimeout(() => {
                    setSuccess(false)
                    setActiveTab('UEBERSICHT')
                    setVon('')
                    setBis('')
                    setBemerkung('')
                }, 1500)
            } else {
                // Parse error from backend (overlap validation)
                try {
                    const errorData = await res.json()
                    setError(errorData.error || 'Fehler beim Senden des Antrags.')
                } catch {
                    setError('Fehler beim Senden des Antrags.')
                }
            }
        } catch (err) {
            console.error(err)
            setError('Verbindungsfehler. Bitte später versuchen.')
        } finally {
            setLoading(false)
        }
    }

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault()
        if (!mitarbeiter || !von || !bis) return

        setLoading(true)
        setError(null)

        // Zuerst Feiertage prüfen
        const feiertage = await checkFeiertage()

        if (feiertage.length > 0) {
            // Feiertage gefunden - Modal anzeigen
            setGefundeneFeiertage(feiertage)
            setShowFeiertagModal(true)
            setLoading(false)
        } else {
            // Keine Feiertage - direkt absenden
            setLoading(false)
            await submitAntrag()
        }
    }

    const formatDate = (dateStr: string) => {
        return new Date(dateStr).toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric' })
    }

    const getStatusColor = (status: string) => {
        switch (status) {
            case 'GENEHMIGT': return 'bg-emerald-100 text-emerald-700 border-emerald-200'
            case 'ABGELEHNT': return 'bg-rose-100 text-rose-700 border-rose-200'
            case 'STORNIERT': return 'bg-slate-100 text-slate-700 border-slate-200'
            default: return 'bg-amber-100 text-amber-700 border-amber-200'
        }
    }

    const getTypeIcon = (type: string) => {
        switch (type) {
            case 'KRANKHEIT': return <Stethoscope className="w-4 h-4" />
            case 'FORTBILDUNG': return <FileText className="w-4 h-4" />
            case 'ZEITAUSGLEICH': return <Clock className="w-4 h-4" />
            default: return <Plane className="w-4 h-4" />
        }
    }

    const filteredAntraege = antraege.filter(a => statusFilter === 'ALLE' || a.status === statusFilter)

    if (success) {
        return (
            <div className="min-h-screen bg-white flex flex-col items-center justify-center p-6 text-center">
                <div className="w-16 h-16 bg-green-100 rounded-full flex items-center justify-center mb-4">
                    <CheckCircle2 className="w-8 h-8 text-green-600" />
                </div>
                <h2 className="text-2xl font-bold text-slate-900 mb-2">Antrag gesendet!</h2>
                <p className="text-slate-500">Dein Urlaubsantrag wurde erfolgreich übermittelt.</p>
            </div>
        )
    }

    return (
        <div className="h-full bg-slate-50 flex flex-col">
            {/* Header */}
            <header className="bg-white border-b border-slate-200 safe-area-top sticky top-0 z-20">
                <div className="px-4 py-4 flex items-center gap-3">
                    <button
                        onClick={() => navigate('/')}
                        className="p-2 -ml-2 text-slate-500 hover:text-slate-900"
                    >
                        <ArrowLeft className="w-5 h-5" />
                    </button>
                    <h1 className="text-lg font-bold text-slate-900">Abwesenheit</h1>
                    <button
                        onClick={() => onSync && onSync()}
                        className="ml-auto p-2 hover:bg-slate-100 rounded-lg transition-all active:scale-95"
                    >
                        <RefreshCw className={`w-5 h-5 ${loading || syncStatus === 'syncing' ? 'animate-sync-spin text-rose-600' : 'text-slate-500'}`} />
                    </button>
                </div>

                {/* Tabs */}
                <div className="flex px-4 gap-4">
                    <button
                        onClick={() => setActiveTab('BEANTRAGEN')}
                        className={`flex-1 pb-3 text-sm font-medium border-b-2 transition-colors ${activeTab === 'BEANTRAGEN' ? 'border-rose-600 text-rose-600' : 'border-transparent text-slate-500'}`}
                    >
                        Beantragen
                    </button>
                    <button
                        onClick={() => setActiveTab('UEBERSICHT')}
                        className={`flex-1 pb-3 text-sm font-medium border-b-2 transition-colors ${activeTab === 'UEBERSICHT' ? 'border-rose-600 text-rose-600' : 'border-transparent text-slate-500'}`}
                    >
                        Übersicht
                    </button>
                </div>
            </header>

            {/* Content */}
            <main className="flex-1 p-4 overflow-x-hidden overflow-y-auto">
                {activeTab === 'BEANTRAGEN' ? (
                    <div className="bg-white rounded-2xl shadow-sm border border-slate-200 overflow-hidden">
                        <form onSubmit={handleSubmit} className="p-5 space-y-6 overflow-hidden">
                            <div className="space-y-4">
                                <h2 className="text-base font-semibold text-slate-900">Neuen Antrag stellen</h2>
                                <div className="grid grid-cols-2 gap-2 mb-6">
                                    <button
                                        type="button"
                                        onClick={() => setTyp('URLAUB')}
                                        className={`p-3 rounded-xl border flex flex-col items-center gap-2 transition-all ${typ === 'URLAUB'
                                            ? 'bg-rose-50 border-rose-600 text-rose-700 ring-1 ring-rose-600'
                                            : 'bg-white border-slate-200 text-slate-500 hover:border-rose-200'
                                            }`}
                                    >
                                        <Plane className="w-5 h-5" />
                                        <span className="font-semibold text-xs">Urlaub</span>
                                    </button>
                                    <button
                                        type="button"
                                        onClick={() => setTyp('KRANKHEIT')}
                                        className={`p-3 rounded-xl border flex flex-col items-center gap-2 transition-all ${typ === 'KRANKHEIT'
                                            ? 'bg-rose-50 border-rose-600 text-rose-700 ring-1 ring-rose-600'
                                            : 'bg-white border-slate-200 text-slate-500 hover:border-rose-200'
                                            }`}
                                    >
                                        <Stethoscope className="w-5 h-5" />
                                        <span className="font-semibold text-xs">Krankheit</span>
                                    </button>
                                    <button
                                        type="button"
                                        onClick={() => setTyp('FORTBILDUNG')}
                                        className={`p-3 rounded-xl border flex flex-col items-center gap-2 transition-all ${typ === 'FORTBILDUNG'
                                            ? 'bg-rose-50 border-rose-600 text-rose-700 ring-1 ring-rose-600'
                                            : 'bg-white border-slate-200 text-slate-500 hover:border-rose-200'
                                            }`}
                                    >
                                        <FileText className="w-5 h-5" />
                                        <span className="font-semibold text-xs">Fortbildung</span>
                                    </button>
                                    <button
                                        type="button"
                                        onClick={() => setTyp('ZEITAUSGLEICH')}
                                        className={`p-3 rounded-xl border flex flex-col items-center gap-2 transition-all ${typ === 'ZEITAUSGLEICH'
                                            ? 'bg-rose-50 border-rose-600 text-rose-700 ring-1 ring-rose-600'
                                            : 'bg-white border-slate-200 text-slate-500 hover:border-rose-200'
                                            }`}
                                    >
                                        <Clock className="w-5 h-5" />
                                        <span className="font-semibold text-xs">Zeitausgleich</span>
                                    </button>
                                </div>

                                <div>
                                    <label className="block text-sm font-medium text-slate-700 mb-1">
                                        Vom (Erster Tag)
                                    </label>
                                    <input
                                        type="date"
                                        required
                                        value={von}
                                        onChange={(e) => setVon(e.target.value)}
                                        className="w-full px-4 py-3 rounded-xl border border-slate-200 focus:border-rose-500 focus:ring-1 focus:ring-rose-500 outline-none transition-all appearance-none"
                                        style={{ maxWidth: '100%' }}
                                    />
                                </div>

                                <div>
                                    <label className="block text-sm font-medium text-slate-700 mb-1">
                                        Bis (Letzter Tag)
                                    </label>
                                    <input
                                        type="date"
                                        required
                                        value={bis}
                                        min={von}
                                        onChange={(e) => setBis(e.target.value)}
                                        className="w-full px-4 py-3 rounded-xl border border-slate-200 focus:border-rose-500 focus:ring-1 focus:ring-rose-500 outline-none transition-all appearance-none"
                                        style={{ maxWidth: '100%' }}
                                    />
                                </div>

                                <div>
                                    <label className="block text-sm font-medium text-slate-700 mb-1">
                                        Bemerkung (Optional)
                                    </label>
                                    <div className="relative">
                                        <textarea
                                            value={bemerkung}
                                            onChange={(e) => setBemerkung(e.target.value)}
                                            rows={3}
                                            placeholder="z.B. Sommerurlaub..."
                                            className="w-full px-4 py-3 rounded-xl border border-slate-200 focus:border-rose-500 focus:ring-1 focus:ring-rose-500 outline-none transition-all resize-none"
                                        />
                                        <FileText className="absolute right-3 top-3 w-5 h-5 text-slate-300 pointer-events-none" />
                                    </div>
                                </div>
                            </div>

                            {/* Resturlaub Info / Warning */}
                            {typ === 'URLAUB' && (
                                <div className={`flex items-start gap-3 p-3 rounded-xl text-sm ${
                                    loadingResturlaub
                                        ? 'bg-slate-50 text-slate-500'
                                        : ueberschritten
                                            ? 'bg-red-50 text-red-700 border border-red-200'
                                            : 'bg-emerald-50 text-emerald-700 border border-emerald-200'
                                }`}>
                                    {loadingResturlaub ? (
                                        <Loader2 className="w-4 h-4 animate-spin mt-0.5 shrink-0" />
                                    ) : ueberschritten ? (
                                        <AlertTriangle className="w-4 h-4 mt-0.5 shrink-0" />
                                    ) : (
                                        <CheckCircle2 className="w-4 h-4 mt-0.5 shrink-0" />
                                    )}
                                    <div>
                                        {loadingResturlaub ? (
                                            <span>Resturlaub wird geladen…</span>
                                        ) : resturlaub !== null ? (
                                            <>
                                                <span className="font-semibold">
                                                    {resturlaub} Urlaubstage übrig
                                                </span>
                                                {beantragteTage > 0 && (
                                                    <span className="block mt-0.5">
                                                        Beantragt: {beantragteTage} Arbeitstag{beantragteTage !== 1 ? 'e' : ''}
                                                        {ueberschritten && (
                                                            <span className="font-semibold"> — Nicht genügend Urlaubstage!</span>
                                                        )}
                                                    </span>
                                                )}
                                            </>
                                        ) : null}
                                    </div>
                                </div>
                            )}

                            {error && (
                                <div className="bg-red-50 text-red-600 text-sm p-3 rounded-xl">
                                    {error}
                                </div>
                            )}

                            <button
                                type="submit"
                                disabled={loading || ueberschritten}
                                className="w-full bg-rose-600 hover:bg-rose-700 disabled:bg-rose-300 text-white font-bold py-4 rounded-xl flex items-center justify-center gap-2 transition-all shadow-sm active:scale-[0.98]"
                            >
                                {loading ? <Loader2 className="w-5 h-5 animate-spin" /> : <Send className="w-5 h-5" />}
                                Antrag senden
                            </button>
                        </form>
                    </div>
                ) : (
                    <div className="space-y-4">
                        {/* Filters */}
                        <div className="bg-white p-4 rounded-2xl shadow-sm border border-slate-200 space-y-4">
                            <div className="flex items-center justify-between">
                                <label className="text-sm font-medium text-slate-700">Jahr</label>
                                <select
                                    value={selectedYear}
                                    onChange={(e) => setSelectedYear(parseInt(e.target.value))}
                                    className="bg-slate-50 border border-slate-200 rounded-lg px-3 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-rose-500"
                                >
                                    {[0, 1, 2].map(offset => {
                                        const y = new Date().getFullYear() - 1 + offset
                                        return <option key={y} value={y}>{y}</option>
                                    })}
                                </select>
                            </div>

                            <div className="flex gap-2 overflow-x-auto pb-1 no-scrollbar">
                                {STATUS_FILTER_OPTIONS.map((filter) => (
                                    <button
                                        key={filter.id}
                                        onClick={() => setStatusFilter(filter.id)}
                                        className={`px-3 py-1.5 rounded-full text-xs font-medium whitespace-nowrap border transition-all ${statusFilter === filter.id
                                            ? 'bg-rose-600 text-white border-rose-600'
                                            : 'bg-white text-slate-600 border-slate-200 hover:bg-slate-50'
                                            }`}
                                    >
                                        {filter.label}
                                    </button>
                                ))}
                            </div>
                        </div>

                        {/* List */}
                        {loadingAntraege ? (
                            <div className="flex justify-center py-8">
                                <Loader2 className="w-6 h-6 animate-spin text-slate-400" />
                            </div>
                        ) : filteredAntraege.length === 0 ? (
                            <div className="text-center py-12 text-slate-400 bg-white rounded-2xl border border-slate-200 border-dashed">
                                <Calendar className="w-10 h-10 mx-auto mb-3 opacity-20" />
                                <p>Keine Anträge gefunden.</p>
                            </div>
                        ) : (
                            <div className="space-y-3">
                                {filteredAntraege.map(antrag => (
                                    <div key={antrag.id} className="bg-white p-4 rounded-xl border border-slate-200 shadow-sm">
                                        <div className="flex justify-between items-start mb-2">
                                            <div className="flex items-center gap-2">
                                                <span className={`p-1.5 rounded-lg ${antrag.typ === 'KRANKHEIT' ? 'bg-rose-50 text-rose-600' : 'bg-blue-50 text-blue-600'
                                                    }`}>
                                                    {getTypeIcon(antrag.typ)}
                                                </span>
                                                <span className="font-semibold text-slate-900 capitalize">
                                                    {antrag.typ.toLowerCase()}
                                                </span>
                                            </div>
                                            <span className={`px-2 py-0.5 rounded-full text-[10px] font-bold uppercase tracking-wide border ${getStatusColor(antrag.status)}`}>
                                                {antrag.status}
                                            </span>
                                        </div>

                                        <div className="flex items-center gap-2 mb-2">
                                            <Calendar className="w-4 h-4 text-slate-400" />
                                            <span className="text-sm text-slate-700">
                                                {formatDate(antrag.vonDatum)} - {formatDate(antrag.bisDatum)}
                                            </span>
                                        </div>

                                        {antrag.bemerkung && (
                                            <p className="text-xs text-slate-500 bg-slate-50 p-2 rounded-lg mt-2 italic">
                                                "{antrag.bemerkung}"
                                            </p>
                                        )}
                                    </div>
                                ))}
                            </div>
                        )}
                    </div>
                )}
            </main>

            {/* Feiertage Modal */}
            {showFeiertagModal && (
                <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4">
                    <div className="bg-white w-full max-w-md rounded-2xl shadow-xl animate-slide-up">
                        {/* Modal Header */}
                        <div className="flex items-center gap-3 p-5 border-b border-slate-100">
                            <div className="w-10 h-10 rounded-full bg-amber-100 flex items-center justify-center">
                                <AlertTriangle className="w-5 h-5 text-amber-600" />
                            </div>
                            <div>
                                <h3 className="font-bold text-slate-900">Feiertage im Zeitraum</h3>
                                <p className="text-sm text-slate-500">Im gewählten Zeitraum liegen Feiertage</p>
                            </div>
                            <button
                                onClick={() => setShowFeiertagModal(false)}
                                className="ml-auto p-1.5 -mr-1.5 rounded-full hover:bg-slate-100"
                            >
                                <X className="w-5 h-5 text-slate-400" />
                            </button>
                        </div>

                        {/* Modal Content */}
                        <div className="p-5 space-y-4">
                            <p className="text-sm text-slate-600">
                                Im gewählten Zeitraum liegen folgende Feiertage. Diese werden vom Backend automatisch aus der Berechnung der Urlaubstage ausgeschlossen:
                            </p>

                            <div className="bg-slate-50 rounded-xl p-3 space-y-2 max-h-48 overflow-y-auto">
                                {gefundeneFeiertage.map((f, idx) => (
                                    <div key={idx} className="flex items-center justify-between text-sm">
                                        <span className="font-medium text-slate-900">{f.bezeichnung}</span>
                                        <span className="text-slate-500">{formatDate(f.datum)}</span>
                                    </div>
                                ))}
                            </div>

                            <p className="text-sm text-slate-600">
                                Möchten Sie den Antrag trotzdem absenden?
                            </p>
                        </div>

                        {/* Modal Footer */}
                        <div className="flex gap-3 p-5 border-t border-slate-100">
                            <button
                                onClick={() => setShowFeiertagModal(false)}
                                className="flex-1 py-3 rounded-xl font-semibold text-slate-600 bg-slate-100 hover:bg-slate-200 transition-colors"
                            >
                                Abbrechen
                            </button>
                            <button
                                onClick={submitAntrag}
                                disabled={loading}
                                className="flex-1 py-3 rounded-xl font-semibold text-white bg-rose-600 hover:bg-rose-700 disabled:bg-rose-300 transition-colors flex items-center justify-center gap-2"
                            >
                                {loading ? <Loader2 className="w-4 h-4 animate-spin" /> : <Send className="w-4 h-4" />}
                                Trotzdem senden
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    )
}
