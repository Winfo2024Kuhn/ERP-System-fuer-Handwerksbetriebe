import { useState, useEffect, useRef, useCallback } from 'react';
import { Search, X, User, ChevronRight, Loader2 } from 'lucide-react';

export interface KundeSearchItem {
    id: number;
    name: string;
    kundennummer?: string;
    ort?: string;
    ansprechspartner?: string;
}

interface KundeSearchModalProps {
    isOpen: boolean;
    onClose: () => void;
    onSelect: (kunde: KundeSearchItem) => void;
    currentKundeId?: number;
}

/**
 * Modal für Kundensuche – server-seitig.
 * Sucht über Name, Kundennummer, Ort, Ansprechpartner.
 */
export function KundeSearchModal({ isOpen, onClose, onSelect, currentKundeId }: KundeSearchModalProps) {
    const [searchTerm, setSearchTerm] = useState('');
    const [kunden, setKunden] = useState<KundeSearchItem[]>([]);
    const [totalCount, setTotalCount] = useState(0);
    const [loading, setLoading] = useState(false);
    const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);
    const abortRef = useRef<AbortController | null>(null);

    const loadKunden = useCallback(async (query: string) => {
        if (abortRef.current) abortRef.current.abort();
        const controller = new AbortController();
        abortRef.current = controller;

        setLoading(true);
        try {
            const params = new URLSearchParams({ size: '50', page: '0' });
            if (query.trim()) params.set('q', query.trim());
            const res = await fetch(`/api/kunden?${params}`, { signal: controller.signal });
            if (!res.ok) throw new Error('Fehler beim Laden');
            const data = await res.json();
            const items: KundeSearchItem[] = (data.kunden ?? []).map((k: { id: number; name: string; kundennummer?: string; ort?: string; ansprechspartner?: string }) => ({
                id: k.id,
                name: k.name,
                kundennummer: k.kundennummer,
                ort: k.ort,
                ansprechspartner: k.ansprechspartner,
            }));
            setKunden(items);
            if (!query.trim()) setTotalCount(data.gesamt ?? items.length);
        } catch (e) {
            if (!(e instanceof DOMException && e.name === 'AbortError')) {
                console.error('Kundensuche fehlgeschlagen:', e);
            }
        } finally {
            if (!controller.signal.aborted) setLoading(false);
        }
    }, []);

    useEffect(() => {
        if (isOpen) {
            setSearchTerm('');
            loadKunden('');
        }
        return () => {
            if (abortRef.current) abortRef.current.abort();
        };
    }, [isOpen, loadKunden]);

    useEffect(() => {
        if (!isOpen) return;
        if (debounceRef.current) clearTimeout(debounceRef.current);
        debounceRef.current = setTimeout(() => {
            loadKunden(searchTerm);
        }, 300);
        return () => {
            if (debounceRef.current) clearTimeout(debounceRef.current);
        };
    }, [searchTerm, isOpen, loadKunden]);

    const handleSelect = (kunde: KundeSearchItem) => {
        onSelect(kunde);
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
                            <User className="w-5 h-5 text-rose-600" />
                            <h2 className="text-lg font-bold text-slate-900">Kunde auswählen</h2>
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
                            onChange={(e) => setSearchTerm(e.target.value)}
                            placeholder="Freitext suchen (Name, Kundennr., Ort, Ansprechpartner)..."
                            className="w-full pl-10 pr-10 py-2.5 bg-slate-50 border border-slate-200 rounded-lg text-slate-900 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-rose-500 focus:border-rose-500"
                            autoFocus
                        />
                        {loading && (
                            <Loader2 className="absolute right-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400 animate-spin" />
                        )}
                    </div>
                </div>

                <div className="flex-1 overflow-y-auto">
                    {loading && kunden.length === 0 ? (
                        <div className="text-center py-12 text-slate-400">
                            <Loader2 className="w-8 h-8 mx-auto mb-2 animate-spin opacity-50" />
                            <p>Kunden werden geladen...</p>
                        </div>
                    ) : kunden.length === 0 ? (
                        <div className="text-center py-12 text-slate-400">
                            <User className="w-10 h-10 mx-auto mb-2 opacity-30" />
                            <p>{searchTerm ? 'Keine Kunden gefunden' : 'Keine Kunden verfügbar'}</p>
                        </div>
                    ) : (
                        <div className="divide-y divide-slate-100">
                            {kunden.map(kunde => {
                                const isSelected = kunde.id === currentKundeId;
                                return (
                                    <button
                                        key={kunde.id}
                                        onClick={() => handleSelect(kunde)}
                                        className={`w-full flex items-center gap-4 p-4 text-left transition-colors group
                                            ${isSelected
                                                ? 'bg-rose-50 border-l-4 border-rose-500'
                                                : 'hover:bg-slate-50 border-l-4 border-transparent'
                                            }`}
                                    >
                                        <div className={`w-10 h-10 rounded-lg flex items-center justify-center flex-shrink-0
                                            ${isSelected ? 'bg-rose-100' : 'bg-slate-100 group-hover:bg-slate-200'}`}>
                                            <User className={`w-5 h-5 ${isSelected ? 'text-rose-600' : 'text-slate-500'}`} />
                                        </div>
                                        <div className="flex-1 min-w-0">
                                            <p className={`font-medium truncate ${isSelected ? 'text-rose-700' : 'text-slate-900'}`}>
                                                {kunde.name || 'Unbenannter Kunde'}
                                            </p>
                                            <div className="flex items-center gap-2 text-sm text-slate-500">
                                                {kunde.kundennummer && (
                                                    <span className="font-mono bg-slate-100 px-1.5 py-0.5 rounded text-xs">
                                                        {kunde.kundennummer}
                                                    </span>
                                                )}
                                                {kunde.ort && (
                                                    <span className="truncate">{kunde.ort}</span>
                                                )}
                                                {kunde.ansprechspartner && (
                                                    <span className="truncate text-slate-400">· {kunde.ansprechspartner}</span>
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
                    {kunden.length}{totalCount > 0 && searchTerm.trim() ? ` von ${totalCount}` : ''} Kunden
                </div>
            </div>
        </div>
    );
}
