import { useState, useEffect, useRef, useCallback } from 'react';
import { Search, X, Truck, ChevronRight, Loader2, MapPin } from 'lucide-react';

export interface LieferantSuchErgebnis {
    id: number;
    lieferantenname: string;
    lieferantenTyp?: string | null;
    ort?: string | null;
    plz?: string | null;
    strasse?: string | null;
    vertreter?: string | null;
    istAktiv?: boolean | null;
    kundenEmails?: string[] | null;
}

interface LieferantSearchModalProps {
    isOpen: boolean;
    onClose: () => void;
    onSelect: (lieferant: LieferantSuchErgebnis) => void;
    currentLieferantId?: number;
    /** Nur aktive Lieferanten anzeigen (Default: true) */
    nurAktive?: boolean;
}

/**
 * Modal zur Lieferantensuche via /api/lieferanten?q=...
 * Sucht über Name, Typ, Vertreter, Ort und Straße.
 */
export function LieferantSearchModal({
    isOpen,
    onClose,
    onSelect,
    currentLieferantId,
    nurAktive = true,
}: LieferantSearchModalProps) {
    const [searchTerm, setSearchTerm] = useState('');
    const [lieferanten, setLieferanten] = useState<LieferantSuchErgebnis[]>([]);
    const [totalCount, setTotalCount] = useState(0);
    const [loading, setLoading] = useState(false);
    const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);
    const abortRef = useRef<AbortController | null>(null);

    const loadLieferanten = useCallback(async (query: string) => {
        if (abortRef.current) abortRef.current.abort();
        const controller = new AbortController();
        abortRef.current = controller;

        setLoading(true);
        try {
            const params = new URLSearchParams({ size: '100' });
            if (query.trim()) params.set('q', query.trim());
            const res = await fetch(`/api/lieferanten?${params}`, { signal: controller.signal });
            if (!res.ok) throw new Error('Fehler beim Laden');
            const data = await res.json();
            const list: LieferantSuchErgebnis[] = Array.isArray(data?.lieferanten) ? data.lieferanten : [];
            const gefiltert = nurAktive ? list.filter(l => l.istAktiv !== false) : list;
            setLieferanten(gefiltert);
            setTotalCount(typeof data?.gesamt === 'number' ? data.gesamt : gefiltert.length);
        } catch (e) {
            if (!(e instanceof DOMException && e.name === 'AbortError')) {
                console.error('Lieferantensuche fehlgeschlagen:', e);
            }
        } finally {
            if (!controller.signal.aborted) setLoading(false);
        }
    }, [nurAktive]);

    useEffect(() => {
        if (isOpen) {
            setSearchTerm('');
            loadLieferanten('');
        }
        return () => {
            if (abortRef.current) abortRef.current.abort();
        };
    }, [isOpen, loadLieferanten]);

    useEffect(() => {
        if (!isOpen) return;
        if (debounceRef.current) clearTimeout(debounceRef.current);
        debounceRef.current = setTimeout(() => loadLieferanten(searchTerm), 250);
        return () => {
            if (debounceRef.current) clearTimeout(debounceRef.current);
        };
    }, [searchTerm, isOpen, loadLieferanten]);

    const handleSelect = (l: LieferantSuchErgebnis) => {
        onSelect(l);
        setSearchTerm('');
        onClose();
    };

    if (!isOpen) return null;

    return (
        <div className="fixed inset-0 bg-black/50 z-[60] flex items-center justify-center p-4">
            <div className="bg-white rounded-xl shadow-2xl w-full max-w-2xl max-h-[80vh] flex flex-col overflow-hidden animate-in zoom-in-95 duration-200">
                <div className="p-4 border-b border-slate-200">
                    <div className="flex items-center justify-between mb-3">
                        <div className="flex items-center gap-2">
                            <Truck className="w-5 h-5 text-rose-600" />
                            <h2 className="text-lg font-bold text-slate-900">Lieferant auswählen</h2>
                        </div>
                        <button
                            onClick={onClose}
                            className="p-1.5 hover:bg-slate-100 rounded-full transition-colors"
                        >
                            <X className="w-5 h-5 text-slate-500" />
                        </button>
                    </div>

                    <div className="relative">
                        <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-slate-400" />
                        <input
                            type="text"
                            value={searchTerm}
                            onChange={e => setSearchTerm(e.target.value)}
                            placeholder="Suche nach Name, Ort, Typ, Vertreter..."
                            className="w-full pl-10 pr-10 py-2.5 bg-slate-50 border border-slate-200 rounded-lg text-slate-900 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-rose-500 focus:border-rose-500"
                            autoFocus
                        />
                        {loading && (
                            <Loader2 className="absolute right-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400 animate-spin" />
                        )}
                    </div>
                </div>

                <div className="flex-1 overflow-y-auto">
                    {loading && lieferanten.length === 0 ? (
                        <div className="text-center py-12 text-slate-400">
                            <Loader2 className="w-8 h-8 mx-auto mb-2 animate-spin opacity-50" />
                            <p>Lieferanten werden geladen...</p>
                        </div>
                    ) : lieferanten.length === 0 ? (
                        <div className="text-center py-12 text-slate-400">
                            <Truck className="w-10 h-10 mx-auto mb-2 opacity-30" />
                            <p>{searchTerm ? 'Keine Lieferanten gefunden' : 'Keine Lieferanten verfügbar'}</p>
                        </div>
                    ) : (
                        <div className="divide-y divide-slate-100">
                            {lieferanten.map(l => {
                                const isSelected = l.id === currentLieferantId;
                                return (
                                    <button
                                        key={l.id}
                                        onClick={() => handleSelect(l)}
                                        className={`w-full flex items-center gap-4 p-4 text-left transition-colors group
                                            ${isSelected
                                                ? 'bg-rose-50 border-l-4 border-rose-500'
                                                : 'hover:bg-slate-50 border-l-4 border-transparent'
                                            }`}
                                    >
                                        <div className={`w-10 h-10 rounded-lg flex items-center justify-center flex-shrink-0
                                            ${isSelected ? 'bg-rose-100' : 'bg-slate-100 group-hover:bg-slate-200'}`}>
                                            <Truck className={`w-5 h-5 ${isSelected ? 'text-rose-600' : 'text-slate-500'}`} />
                                        </div>
                                        <div className="flex-1 min-w-0">
                                            <div className="flex items-center gap-2">
                                                <p className={`font-medium truncate ${isSelected ? 'text-rose-700' : 'text-slate-900'}`}>
                                                    {l.lieferantenname}
                                                </p>
                                                {l.lieferantenTyp && (
                                                    <span className="text-xs bg-slate-100 text-slate-600 px-1.5 py-0.5 rounded flex-shrink-0">
                                                        {l.lieferantenTyp}
                                                    </span>
                                                )}
                                                {l.istAktiv === false && (
                                                    <span className="text-xs bg-slate-200 text-slate-500 px-1.5 py-0.5 rounded flex-shrink-0">
                                                        Inaktiv
                                                    </span>
                                                )}
                                            </div>
                                            <div className="flex items-center gap-3 text-sm text-slate-500 mt-0.5">
                                                {(l.plz || l.ort) && (
                                                    <span className="inline-flex items-center gap-1 truncate">
                                                        <MapPin className="w-3 h-3" />
                                                        {[l.plz, l.ort].filter(Boolean).join(' ')}
                                                    </span>
                                                )}
                                                {l.vertreter && (
                                                    <span className="truncate">· {l.vertreter}</span>
                                                )}
                                            </div>
                                        </div>
                                        <ChevronRight className={`w-5 h-5 flex-shrink-0 ${isSelected ? 'text-rose-400' : 'text-slate-300 group-hover:text-slate-400'}`} />
                                    </button>
                                );
                            })}
                        </div>
                    )}
                </div>

                <div className="px-4 py-3 border-t border-slate-200 bg-slate-50 text-sm text-slate-500">
                    {lieferanten.length}{totalCount > lieferanten.length ? ` von ${totalCount}` : ''} Lieferanten
                </div>
            </div>
        </div>
    );
}
