import { useState, useMemo } from 'react'
import { X, Search, Building2, ChevronRight } from 'lucide-react'

interface Lieferant {
    id: number
    firmenname: string
}

interface SupplierSelectionModalProps {
    isOpen: boolean
    onClose: () => void
    onSelect: (lieferant: Lieferant | null) => void
    lieferanten: Lieferant[]
}

export function SupplierSelectionModal({ isOpen, onClose, onSelect, lieferanten }: SupplierSelectionModalProps) {
    const [searchTerm, setSearchTerm] = useState('')

    const filteredLieferanten = useMemo(() => {
        if (!searchTerm) return lieferanten
        const lower = searchTerm.toLowerCase()
        return lieferanten.filter(l => 
            l.firmenname.toLowerCase().includes(lower)
        )
    }, [lieferanten, searchTerm])

    if (!isOpen) return null

    return (
        <div className="fixed inset-0 bg-slate-50 z-[60] flex flex-col safe-area-top safe-area-bottom animate-in slide-in-from-bottom duration-200">
            {/* Header */}
            <div className="bg-white border-b border-slate-200 px-4 py-4 flex items-center gap-3 shadow-sm z-10">
                <button
                    onClick={onClose}
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
                            placeholder="Lieferant suchen..."
                            autoFocus
                            className="w-full pl-10 pr-4 py-3 bg-slate-50 border border-slate-200 rounded-xl text-slate-900 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-rose-500 focus:border-rose-500 transition-all"
                        />
                    </div>
                </div>

                {/* List */}
                <div className="flex-1 overflow-y-auto p-4 space-y-2">
                    {/* "No Supplier" Option */}
                    <button
                        onClick={() => onSelect(null)}
                        className="w-full bg-slate-100 border border-slate-200 rounded-xl p-4 flex items-center justify-between hover:bg-slate-200 transition-all text-left mb-4"
                    >
                        <span className="font-medium text-slate-600">Ohne Lieferant fortfahren</span>
                        <ChevronRight className="w-5 h-5 text-slate-400" />
                    </button>

                    {filteredLieferanten.length === 0 ? (
                        <div className="text-center py-12 text-slate-400">
                            Keine Lieferanten gefunden
                        </div>
                    ) : (
                        filteredLieferanten.map(lieferant => (
                            <button
                                key={lieferant.id}
                                onClick={() => onSelect(lieferant)}
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
                        ))
                    )}
                </div>
            </div>
        </div>
    )
}
