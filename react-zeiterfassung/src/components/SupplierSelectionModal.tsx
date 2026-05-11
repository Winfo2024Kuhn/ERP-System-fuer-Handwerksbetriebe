import { useCallback, useEffect, useRef, useState } from 'react'
import { X, Search, Building2, ChevronRight, Loader2 } from 'lucide-react'

interface Lieferant {
    id: number
    firmenname: string
}

// Mobile-Endpoint `/api/zeiterfassung/lieferanten` liefert eine flache Liste
// mit `firmenname` (bereits via Map gemappt aus `lieferantenname`).
interface LieferantApiItem {
    id: number
    firmenname: string
}

interface SupplierSelectionModalProps {
    isOpen: boolean
    onClose: () => void
    onSelect: (lieferant: Lieferant | null) => void
}

const DEBOUNCE_MS = 250
const INITIAL_LIMIT = 50

export function SupplierSelectionModal({ isOpen, onClose, onSelect }: SupplierSelectionModalProps) {
    const [searchTerm, setSearchTerm] = useState('')
    const [results, setResults] = useState<Lieferant[]>([])
    const [loading, setLoading] = useState(false)
    const abortRef = useRef<AbortController | null>(null)

    // Beim echten Unmount laufende Requests abbrechen.
    useEffect(() => () => abortRef.current?.abort(), [])

    const loadLieferanten = useCallback((query: string) => {
        abortRef.current?.abort()
        const ctrl = new AbortController()
        abortRef.current = ctrl
        setLoading(true)
        // Mobile-spezifischer Endpoint, gleicher wie `OfflineService.searchLieferanten`.
        const q = query.trim()
        const url = q
            ? `/api/zeiterfassung/lieferanten?search=${encodeURIComponent(q)}`
            : `/api/zeiterfassung/lieferanten?limit=${INITIAL_LIMIT}`
        fetch(url, { signal: ctrl.signal })
            .then(res => res.ok ? res.json() as Promise<LieferantApiItem[]> : [])
            .then(data => {
                if (ctrl.signal.aborted) return
                const list = (Array.isArray(data) ? data : [])
                    .filter(l => l && l.firmenname)
                    .map(l => ({ id: l.id, firmenname: l.firmenname }))
                list.sort((a, b) => a.firmenname.localeCompare(b.firmenname, 'de'))
                setResults(list)
            })
            .catch(err => {
                if (err?.name !== 'AbortError') setResults([])
            })
            .finally(() => {
                if (!ctrl.signal.aborted) setLoading(false)
            })
    }, [])

    useEffect(() => {
        if (!isOpen) return
        // Leere Query (Initial-Load) sofort, jede Eingabe debounced.
        const delay = searchTerm ? DEBOUNCE_MS : 0
        const handle = setTimeout(() => loadLieferanten(searchTerm), delay)
        return () => clearTimeout(handle)
    }, [searchTerm, isOpen, loadLieferanten])

    const resetAndClose = () => {
        abortRef.current?.abort()
        setSearchTerm('')
        setResults([])
        setLoading(false)
        onClose()
    }
    const pick = (l: Lieferant | null) => {
        abortRef.current?.abort()
        setSearchTerm('')
        setResults([])
        setLoading(false)
        onSelect(l)
    }

    if (!isOpen) return null

    const showInitialLoading = loading && results.length === 0
    const showEmpty = !loading && results.length === 0

    return (
        <div className="fixed inset-0 bg-slate-50 z-[60] flex flex-col safe-area-top safe-area-bottom animate-in slide-in-from-bottom duration-200">
            {/* Header */}
            <div className="bg-white border-b border-slate-200 px-4 py-4 flex items-center gap-3 shadow-sm z-10">
                <button
                    onClick={resetAndClose}
                    className="p-2 hover:bg-slate-100 rounded-full transition-colors"
                >
                    <X className="w-6 h-6 text-slate-600" />
                </button>
                <div className="flex-1">
                    <h2 className="text-lg font-bold text-slate-900">Lieferant auswählen</h2>
                    <p className="text-sm text-slate-500">Für Dokument-Zuordnung</p>
                </div>
            </div>

            {/* Content */}
            <div className="flex-1 flex flex-col overflow-hidden">
                {/* Search */}
                <div className="p-4 bg-white border-b border-slate-100">
                    <div className="relative">
                        <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-slate-400" />
                        <input
                            type="text"
                            value={searchTerm}
                            onChange={(e) => setSearchTerm(e.target.value)}
                            placeholder="Lieferant suchen…"
                            autoFocus
                            className="w-full pl-10 pr-10 py-3 bg-slate-50 border border-slate-200 rounded-xl text-slate-900 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-rose-500 focus:border-rose-500 transition-all"
                        />
                        {loading && (
                            <Loader2 className="absolute right-3 top-1/2 -translate-y-1/2 w-5 h-5 text-rose-500 animate-spin" />
                        )}
                    </div>
                </div>

                {/* List */}
                <div className="flex-1 overflow-y-auto p-4 space-y-2">
                    {/* "No Supplier" Option */}
                    <button
                        onClick={() => pick(null)}
                        className="w-full bg-slate-100 border border-slate-200 rounded-xl p-4 flex items-center justify-between hover:bg-slate-200 transition-all text-left mb-4"
                    >
                        <span className="font-medium text-slate-600">Ohne Lieferant fortfahren</span>
                        <ChevronRight className="w-5 h-5 text-slate-400" />
                    </button>

                    {showInitialLoading && (
                        <div className="text-center py-12 text-slate-400">
                            <Loader2 className="w-8 h-8 mx-auto mb-2 animate-spin opacity-50" />
                            <p>Lieferanten werden geladen…</p>
                        </div>
                    )}

                    {showEmpty && (
                        <div className="text-center py-12 text-slate-400">
                            {searchTerm.trim() ? 'Keine Lieferanten gefunden' : 'Keine Lieferanten verfügbar'}
                        </div>
                    )}

                    {results.map(lieferant => (
                        <button
                            key={lieferant.id}
                            onClick={() => pick(lieferant)}
                            className="w-full bg-white border border-slate-200 rounded-xl p-4 flex items-center justify-between hover:border-rose-200 hover:shadow-sm active:bg-rose-50 transition-all text-left group"
                        >
                            <div className="flex items-center gap-3">
                                <div className="w-10 h-10 bg-rose-50 rounded-lg flex items-center justify-center group-hover:bg-rose-100 transition-colors">
                                    <Building2 className="w-5 h-5 text-rose-600" />
                                </div>
                                <span className="font-medium text-slate-900">{lieferant.firmenname}</span>
                            </div>
                            <ChevronRight className="w-5 h-5 text-slate-400 group-hover:text-rose-400" />
                        </button>
                    ))}
                </div>
            </div>
        </div>
    )
}
