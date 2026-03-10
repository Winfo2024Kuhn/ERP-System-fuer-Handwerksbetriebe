import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { ArrowLeft, Calendar, Plane, Stethoscope, GraduationCap, Clock, CheckCircle2, XCircle, HelpCircle, Filter, RefreshCw } from 'lucide-react'

interface AbwesenheitenPageProps {
    mitarbeiter: { id: number; name: string } | null
    syncStatus?: 'syncing' | 'done' | 'error'
    onSync?: () => void
}

interface Antrag {
    id: number
    vonDatum: string
    bisDatum: string
    typ: 'URLAUB' | 'KRANKHEIT' | 'FORTBILDUNG' | 'ZEITAUSGLEICH'
    status: 'OFFEN' | 'GENEHMIGT' | 'ABGELEHNT' | 'STORNIERT'
    bemerkung?: string
}

const statusConfig = {
    OFFEN: { label: 'Offen', icon: Clock, color: 'text-amber-600 bg-amber-50 border-amber-200' },
    GENEHMIGT: { label: 'Genehmigt', icon: CheckCircle2, color: 'text-green-600 bg-green-50 border-green-200' },
    ABGELEHNT: { label: 'Abgelehnt', icon: XCircle, color: 'text-red-600 bg-red-50 border-red-200' },
    STORNIERT: { label: 'Storniert', icon: HelpCircle, color: 'text-slate-500 bg-slate-50 border-slate-200' },
}

const typConfig = {
    URLAUB: { label: 'Urlaub', icon: Plane, color: 'text-blue-600' },
    KRANKHEIT: { label: 'Krankheit', icon: Stethoscope, color: 'text-red-600' },
    FORTBILDUNG: { label: 'Fortbildung', icon: GraduationCap, color: 'text-purple-600' },
    ZEITAUSGLEICH: { label: 'Zeitausgleich', icon: Clock, color: 'text-emerald-600' },
}

export default function AbwesenheitenPage({ mitarbeiter, syncStatus, onSync }: AbwesenheitenPageProps) {
    const navigate = useNavigate()
    const [antraege, setAntraege] = useState<Antrag[]>([])
    const [loading, setLoading] = useState(true)
    const [statusFilter, setStatusFilter] = useState<string>('ALLE')
    const [jahrFilter, setJahrFilter] = useState<number>(new Date().getFullYear())

    // Verfügbare Jahre (aktuelles Jahr bis 5 Jahre zurück)
    const currentYear = new Date().getFullYear()
    const years = Array.from({ length: 6 }, (_, i) => currentYear - i)

    useEffect(() => {
        if (!mitarbeiter) return

        const loadAntraege = async () => {
            setLoading(true)
            try {
                // Je nach Filter unterschiedliche Anfragen
                let url = `/api/urlaub/antraege?mitarbeiterId=${mitarbeiter.id}`
                if (jahrFilter) {
                    url += `&jahr=${jahrFilter}`
                }
                if (statusFilter && statusFilter !== 'ALLE') {
                    url = `/api/urlaub/antraege?mitarbeiterId=${mitarbeiter.id}&status=${statusFilter}`
                }

                const res = await fetch(url)
                if (res.ok) {
                    const data = await res.json()
                    setAntraege(data)
                }
            } catch (err) {
                console.error('Fehler beim Laden der Anträge:', err)
            } finally {
                setLoading(false)
            }
        }

        loadAntraege()
    }, [mitarbeiter, statusFilter, jahrFilter])

    const formatDatum = (dateStr: string) => {
        const date = new Date(dateStr)
        return date.toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric' })
    }

    const berechneUrlaubstage = (von: string, bis: string) => {
        const start = new Date(von)
        const end = new Date(bis)
        let count = 0
        const current = new Date(start)
        while (current <= end) {
            const day = current.getDay()
            if (day !== 0 && day !== 6) count++ // Wochenenden ausschließen
            current.setDate(current.getDate() + 1)
        }
        return count
    }

    // Statistiken
    const genehmigte = antraege.filter(a => a.status === 'GENEHMIGT')
    const offene = antraege.filter(a => a.status === 'OFFEN')
    const urlaubsTage = genehmigte.filter(a => a.typ === 'URLAUB').reduce((sum, a) => sum + berechneUrlaubstage(a.vonDatum, a.bisDatum), 0)

    return (
        <div className="h-full bg-slate-50 flex flex-col">
            {/* Header */}
            <header className="bg-white border-b border-slate-200 px-4 py-4 safe-area-top sticky top-0 z-10">
                <div className="flex items-center gap-3">
                    <button
                        onClick={() => navigate('/')}
                        className="p-2 -ml-2 text-slate-500 hover:text-slate-900"
                    >
                        <ArrowLeft className="w-5 h-5" />
                    </button>
                    <h1 className="text-lg font-bold text-slate-900">Meine Abwesenheiten</h1>
                    <button
                        onClick={() => onSync && onSync()}
                        className="ml-auto p-2 hover:bg-slate-100 rounded-lg transition-all active:scale-95"
                    >
                        <RefreshCw className={`w-5 h-5 ${loading || syncStatus === 'syncing' ? 'animate-sync-spin text-rose-600' : 'text-slate-500'}`} />
                    </button>
                </div>
            </header>

            {/* Stats */}
            <div className="grid grid-cols-3 gap-3 p-4">
                <div className="bg-white rounded-xl p-3 border border-slate-200 text-center">
                    <p className="text-2xl font-bold text-rose-600">{urlaubsTage}</p>
                    <p className="text-xs text-slate-500">Urlaubstage {jahrFilter}</p>
                </div>
                <div className="bg-white rounded-xl p-3 border border-slate-200 text-center">
                    <p className="text-2xl font-bold text-green-600">{genehmigte.length}</p>
                    <p className="text-xs text-slate-500">Genehmigt</p>
                </div>
                <div className="bg-white rounded-xl p-3 border border-slate-200 text-center">
                    <p className="text-2xl font-bold text-amber-600">{offene.length}</p>
                    <p className="text-xs text-slate-500">Offen</p>
                </div>
            </div>

            {/* Filters */}
            <div className="px-4 pb-4 flex gap-2">
                {/* Status Filter */}
                <div className="flex-1 relative">
                    <Filter className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
                    <select
                        value={statusFilter}
                        onChange={(e) => setStatusFilter(e.target.value)}
                        className="w-full pl-9 pr-4 py-2.5 bg-white border border-slate-200 rounded-xl text-sm font-medium text-slate-700 appearance-none focus:outline-none focus:ring-2 focus:ring-rose-500"
                    >
                        <option value="ALLE">Alle Status</option>
                        <option value="OFFEN">Offen</option>
                        <option value="GENEHMIGT">Genehmigt</option>
                        <option value="ABGELEHNT">Abgelehnt</option>
                    </select>
                </div>

                {/* Jahr Filter */}
                <div className="relative">
                    <Calendar className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
                    <select
                        value={jahrFilter}
                        onChange={(e) => setJahrFilter(Number(e.target.value))}
                        className="pl-9 pr-4 py-2.5 bg-white border border-slate-200 rounded-xl text-sm font-medium text-slate-700 appearance-none focus:outline-none focus:ring-2 focus:ring-rose-500"
                    >
                        {years.map(year => (
                            <option key={year} value={year}>{year}</option>
                        ))}
                    </select>
                </div>
            </div>

            {/* List */}
            <main className="flex-1 px-4 pb-4 overflow-auto">
                {loading ? (
                    <div className="flex items-center justify-center py-12">
                        <div className="w-8 h-8 border-2 border-rose-200 border-t-rose-600 rounded-full animate-spin"></div>
                    </div>
                ) : antraege.length === 0 ? (
                    <div className="text-center py-12">
                        <Calendar className="w-12 h-12 mx-auto text-slate-300 mb-3" />
                        <p className="text-slate-500">Keine Anträge für {jahrFilter} gefunden.</p>
                    </div>
                ) : (
                    <div className="space-y-3">
                        {antraege.map(antrag => {
                            const tage = berechneUrlaubstage(antrag.vonDatum, antrag.bisDatum)
                            const TypIcon = typConfig[antrag.typ]?.icon || Plane
                            const StatusIcon = statusConfig[antrag.status]?.icon || Clock
                            const statusStyle = statusConfig[antrag.status]?.color || ''

                            return (
                                <div key={antrag.id} className="bg-white rounded-xl border border-slate-200 p-4">
                                    <div className="flex items-start justify-between">
                                        <div className="flex items-center gap-3">
                                            <div className={`w-10 h-10 rounded-full flex items-center justify-center ${typConfig[antrag.typ]?.color || 'text-slate-500'} bg-slate-100`}>
                                                <TypIcon className="w-5 h-5" />
                                            </div>
                                            <div>
                                                <p className="font-semibold text-slate-900">
                                                    {typConfig[antrag.typ]?.label || antrag.typ}
                                                </p>
                                                <p className="text-sm text-slate-500">
                                                    {formatDatum(antrag.vonDatum)} – {formatDatum(antrag.bisDatum)}
                                                </p>
                                            </div>
                                        </div>
                                        <div className={`px-2.5 py-1 rounded-full text-xs font-semibold flex items-center gap-1 border ${statusStyle}`}>
                                            <StatusIcon className="w-3.5 h-3.5" />
                                            {statusConfig[antrag.status]?.label || antrag.status}
                                        </div>
                                    </div>
                                    <div className="mt-3 flex items-center justify-between text-sm">
                                        <span className="text-slate-500">
                                            {tage} {tage === 1 ? 'Tag' : 'Tage'}
                                        </span>
                                        {antrag.bemerkung && (
                                            <span className="text-slate-400 text-xs truncate max-w-[150px]">
                                                {antrag.bemerkung}
                                            </span>
                                        )}
                                    </div>
                                </div>
                            )
                        })}
                    </div>
                )}
            </main>

            {/* FAB for new request */}
            <button
                onClick={() => navigate('/abwesenheit')}
                className="fixed bottom-6 right-6 bg-rose-600 hover:bg-rose-700 text-white w-14 h-14 rounded-full shadow-lg flex items-center justify-center text-2xl font-bold transition-transform active:scale-95"
            >
                +
            </button>
        </div>
    )
}
