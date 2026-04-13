import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ArrowLeft, Search, AlertTriangle, CheckCircle2, ChevronRight, QrCode } from 'lucide-react'

interface Betriebsmittel {
    id: number
    bezeichnung: string
    seriennummer?: string
    barcode?: string
    hersteller?: string
    modell?: string
    standort?: string
    naechstesPruefDatum?: string
    pruefIntervallMonate: number
    ausserBetrieb: boolean
}

type Filter = 'alle' | 'faellig' | 'ok'

export default function BetriebsmittelListePage() {
    const navigate = useNavigate()
    const [geraete, setGeraete] = useState<Betriebsmittel[]>([])
    const [loading, setLoading] = useState(true)
    const [search, setSearch] = useState('')
    const [filter, setFilter] = useState<Filter>('alle')

    useEffect(() => {
        const token = localStorage.getItem('zeiterfassung_token')
        if (!token) return
        fetch(`/api/betriebsmittel?token=${encodeURIComponent(token)}`)
            .then(r => r.ok ? r.json() : [])
            .then(setGeraete)
            .catch(() => setGeraete([]))
            .finally(() => setLoading(false))
    }, [])

    const heute = new Date().toISOString().slice(0, 10)

    const isFaellig = (g: Betriebsmittel) =>
        !g.ausserBetrieb && (!g.naechstesPruefDatum || g.naechstesPruefDatum <= heute)

    const filtered = geraete
        .filter(g => !g.ausserBetrieb)
        .filter(g => {
            if (filter === 'faellig') return isFaellig(g)
            if (filter === 'ok') return !isFaellig(g)
            return true
        })
        .filter(g => {
            if (!search) return true
            const q = search.toLowerCase()
            return g.bezeichnung.toLowerCase().includes(q)
                || g.seriennummer?.toLowerCase().includes(q)
                || g.hersteller?.toLowerCase().includes(q)
                || g.standort?.toLowerCase().includes(q)
        })
        .sort((a, b) => {
            // Fällige zuerst
            const aF = isFaellig(a) ? 0 : 1
            const bF = isFaellig(b) ? 0 : 1
            if (aF !== bF) return aF - bF
            return a.bezeichnung.localeCompare(b.bezeichnung)
        })

    const faelligCount = geraete.filter(g => !g.ausserBetrieb && isFaellig(g)).length

    return (
        <div className="min-h-screen bg-slate-50 flex flex-col">
            {/* Header */}
            <div className="sticky top-0 z-10 bg-white border-b border-slate-200 px-4 py-3 safe-area-top">
                <div className="flex items-center gap-3">
                    <button onClick={() => navigate('/')} className="p-2 -ml-2 rounded-lg hover:bg-slate-100">
                        <ArrowLeft className="w-5 h-5 text-slate-700" />
                    </button>
                    <div className="flex-1">
                        <h1 className="text-lg font-bold text-slate-900">E-Check</h1>
                        <p className="text-xs text-slate-500">Betriebsmittelprüfung</p>
                    </div>
                    <button
                        onClick={() => navigate('/betriebsmittel/scan')}
                        className="p-2.5 rounded-xl bg-rose-600 text-white hover:bg-rose-700 transition-colors"
                    >
                        <QrCode className="w-5 h-5" />
                    </button>
                </div>

                {/* Search */}
                <div className="relative mt-3">
                    <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
                    <input
                        type="text"
                        value={search}
                        onChange={e => setSearch(e.target.value)}
                        placeholder="Gerät suchen..."
                        className="w-full pl-10 pr-4 py-2.5 rounded-xl border border-slate-200 bg-slate-50 text-sm focus:outline-none focus:ring-2 focus:ring-rose-500 focus:border-transparent"
                    />
                </div>

                {/* Filter Pills */}
                <div className="flex gap-2 mt-3">
                    {([
                        { key: 'alle' as Filter, label: 'Alle' },
                        { key: 'faellig' as Filter, label: `Fällig (${faelligCount})` },
                        { key: 'ok' as Filter, label: 'OK' },
                    ]).map(f => (
                        <button
                            key={f.key}
                            onClick={() => setFilter(f.key)}
                            className={`px-3 py-1.5 rounded-full text-xs font-medium transition-colors ${
                                filter === f.key
                                    ? 'bg-rose-600 text-white'
                                    : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
                            }`}
                        >
                            {f.label}
                        </button>
                    ))}
                </div>
            </div>

            {/* Liste */}
            <main className="flex-1 px-4 py-3 space-y-2 pb-8">
                {loading ? (
                    <div className="space-y-3">
                        {[1,2,3,4].map(i => (
                            <div key={i} className="bg-white rounded-xl p-4 animate-pulse">
                                <div className="h-4 bg-slate-200 rounded w-2/3 mb-2" />
                                <div className="h-3 bg-slate-100 rounded w-1/2" />
                            </div>
                        ))}
                    </div>
                ) : filtered.length === 0 ? (
                    <div className="text-center py-12 text-slate-500">
                        <p className="text-sm">{search ? 'Keine Treffer' : 'Keine Betriebsmittel vorhanden'}</p>
                    </div>
                ) : (
                    filtered.map(g => {
                        const faellig = isFaellig(g)
                        return (
                            <button
                                key={g.id}
                                onClick={() => navigate(`/betriebsmittel/${g.id}/pruefung`)}
                                className="w-full bg-white rounded-xl p-4 border border-slate-200 hover:border-rose-200 hover:shadow-sm transition-all text-left flex items-center gap-3"
                            >
                                <div className={`w-10 h-10 rounded-lg flex items-center justify-center flex-shrink-0 ${
                                    faellig ? 'bg-red-50' : 'bg-green-50'
                                }`}>
                                    {faellig
                                        ? <AlertTriangle className="w-5 h-5 text-red-500" />
                                        : <CheckCircle2 className="w-5 h-5 text-green-500" />
                                    }
                                </div>
                                <div className="flex-1 min-w-0">
                                    <p className="font-semibold text-slate-900 truncate">{g.bezeichnung}</p>
                                    <p className="text-xs text-slate-500 truncate">
                                        {[g.hersteller, g.modell].filter(Boolean).join(' · ') || g.seriennummer || 'Kein Hersteller'}
                                    </p>
                                    {g.standort && (
                                        <p className="text-xs text-slate-400 truncate">{g.standort}</p>
                                    )}
                                </div>
                                <div className="text-right flex-shrink-0">
                                    {g.naechstesPruefDatum ? (
                                        <p className={`text-xs font-medium ${faellig ? 'text-red-600' : 'text-green-600'}`}>
                                            {faellig ? 'Fällig!' : new Date(g.naechstesPruefDatum).toLocaleDateString('de-DE')}
                                        </p>
                                    ) : (
                                        <p className="text-xs text-red-500 font-medium">Nie geprüft</p>
                                    )}
                                </div>
                                <ChevronRight className="w-4 h-4 text-slate-300 flex-shrink-0" />
                            </button>
                        )
                    })
                )}
            </main>
        </div>
    )
}
