import { useState, useEffect, useRef, useCallback } from 'react';
import { Search, X, Package, ChevronRight, Loader2, Tag } from 'lucide-react';

export interface ArtikelSuchErgebnis {
    id: number;
    produktname: string;
    produktlinie?: string | null;
    produkttext?: string | null;
    externeArtikelnummer?: string | null;
    werkstoffName?: string | null;
    kategoriePfad?: string | null;
    kategorieId?: number | null;
    rootKategorieId?: number | null;
    lieferantenname?: string | null;
    lieferantId?: number | null;
    preis?: number | null;
    kgProMeter?: number | null;
}

interface ArtikelSearchModalProps {
    isOpen: boolean;
    onClose: () => void;
    onSelect: (artikel: ArtikelSuchErgebnis) => void;
    /** Vor-Filter: Nur Artikel eines bestimmten Lieferanten anzeigen */
    lieferantName?: string;
    /** Wenn true, Mehrfachauswahl möglich — nutzt onSelectMany statt onSelect */
    multiSelect?: boolean;
    onSelectMany?: (artikel: ArtikelSuchErgebnis[]) => void;
    /** Platzhaltertext für das Suchfeld */
    placeholder?: string;
}

/**
 * Modal zur Artikel-Suche über /api/artikel.
 * Sucht in Produktname, Produktlinie, Produkttext, Werkstoff und externer Artikelnummer.
 */
export function ArtikelSearchModal({
    isOpen,
    onClose,
    onSelect,
    lieferantName,
    multiSelect = false,
    onSelectMany,
    placeholder = 'Suche nach Produktname, Werkstoff, Artikelnummer...'
}: ArtikelSearchModalProps) {
    const [searchTerm, setSearchTerm] = useState('');
    const [artikel, setArtikel] = useState<ArtikelSuchErgebnis[]>([]);
    const [totalCount, setTotalCount] = useState(0);
    const [loading, setLoading] = useState(false);
    const [selected, setSelected] = useState<Map<number, ArtikelSuchErgebnis>>(new Map());
    const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);
    const abortRef = useRef<AbortController | null>(null);

    const loadArtikel = useCallback(async (query: string) => {
        if (abortRef.current) abortRef.current.abort();
        const controller = new AbortController();
        abortRef.current = controller;

        setLoading(true);
        try {
            const params = new URLSearchParams({ size: '50', sort: 'produktname', dir: 'asc' });
            if (query.trim()) params.set('q', query.trim());
            if (lieferantName) params.set('lieferant', lieferantName);
            const res = await fetch(`/api/artikel?${params}`, { signal: controller.signal });
            if (!res.ok) throw new Error('Fehler beim Laden');
            const data = await res.json();
            const list: ArtikelSuchErgebnis[] = Array.isArray(data?.artikel) ? data.artikel : [];
            setArtikel(list);
            setTotalCount(typeof data?.gesamt === 'number' ? data.gesamt : list.length);
        } catch (e) {
            if (!(e instanceof DOMException && e.name === 'AbortError')) {
                console.error('Artikelsuche fehlgeschlagen:', e);
            }
        } finally {
            if (!controller.signal.aborted) setLoading(false);
        }
    }, [lieferantName]);

    useEffect(() => {
        if (isOpen) {
            setSearchTerm('');
            setSelected(new Map());
            loadArtikel('');
        }
        return () => {
            if (abortRef.current) abortRef.current.abort();
        };
    }, [isOpen, loadArtikel]);

    useEffect(() => {
        if (!isOpen) return;
        if (debounceRef.current) clearTimeout(debounceRef.current);
        debounceRef.current = setTimeout(() => loadArtikel(searchTerm), 250);
        return () => {
            if (debounceRef.current) clearTimeout(debounceRef.current);
        };
    }, [searchTerm, isOpen, loadArtikel]);

    const handleRowClick = (a: ArtikelSuchErgebnis) => {
        if (multiSelect) {
            setSelected(prev => {
                const next = new Map(prev);
                if (next.has(a.id)) next.delete(a.id);
                else next.set(a.id, a);
                return next;
            });
        } else {
            onSelect(a);
            onClose();
        }
    };

    const handleBestaetigen = () => {
        if (onSelectMany) onSelectMany(Array.from(selected.values()));
        onClose();
    };

    if (!isOpen) return null;

    return (
        <div className="fixed inset-0 bg-black/50 z-[60] flex items-center justify-center p-4">
            <div className="bg-white rounded-xl shadow-2xl w-full max-w-3xl max-h-[85vh] flex flex-col overflow-hidden animate-in zoom-in-95 duration-200">
                <div className="p-4 border-b border-slate-200">
                    <div className="flex items-center justify-between mb-3">
                        <div className="flex items-center gap-2">
                            <Package className="w-5 h-5 text-rose-600" />
                            <h2 className="text-lg font-bold text-slate-900">
                                {multiSelect ? 'Artikel auswählen (Mehrfachauswahl)' : 'Artikel auswählen'}
                            </h2>
                            {lieferantName && (
                                <span className="ml-2 text-xs font-medium bg-rose-100 text-rose-700 px-2 py-0.5 rounded-full">
                                    {lieferantName}
                                </span>
                            )}
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
                            placeholder={placeholder}
                            className="w-full pl-10 pr-10 py-2.5 bg-slate-50 border border-slate-200 rounded-lg text-slate-900 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-rose-500 focus:border-rose-500"
                            autoFocus
                        />
                        {loading && (
                            <Loader2 className="absolute right-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400 animate-spin" />
                        )}
                    </div>
                </div>

                <div className="flex-1 overflow-y-auto">
                    {loading && artikel.length === 0 ? (
                        <div className="text-center py-12 text-slate-400">
                            <Loader2 className="w-8 h-8 mx-auto mb-2 animate-spin opacity-50" />
                            <p>Artikel werden geladen...</p>
                        </div>
                    ) : artikel.length === 0 ? (
                        <div className="text-center py-12 text-slate-400">
                            <Package className="w-10 h-10 mx-auto mb-2 opacity-30" />
                            <p>{searchTerm ? 'Keine Artikel gefunden' : 'Keine Artikel verfügbar'}</p>
                        </div>
                    ) : (
                        <div className="divide-y divide-slate-100">
                            {artikel.map(a => {
                                const istSelektiert = selected.has(a.id);
                                return (
                                    <button
                                        key={`${a.id}-${a.lieferantId ?? 'x'}`}
                                        onClick={() => handleRowClick(a)}
                                        className={`w-full flex items-center gap-4 p-4 text-left transition-colors group
                                            ${istSelektiert
                                                ? 'bg-rose-50 border-l-4 border-rose-500'
                                                : 'hover:bg-slate-50 border-l-4 border-transparent'
                                            }`}
                                    >
                                        {multiSelect && (
                                            <input
                                                type="checkbox"
                                                checked={istSelektiert}
                                                onChange={() => handleRowClick(a)}
                                                onClick={e => e.stopPropagation()}
                                                className="w-4 h-4 text-rose-600 border-slate-300 rounded focus:ring-rose-500"
                                                aria-label={`${a.produktname} auswählen`}
                                            />
                                        )}
                                        <div className={`w-10 h-10 rounded-lg flex items-center justify-center flex-shrink-0
                                            ${istSelektiert ? 'bg-rose-100' : 'bg-slate-100 group-hover:bg-slate-200'}`}>
                                            <Package className={`w-5 h-5 ${istSelektiert ? 'text-rose-600' : 'text-slate-500'}`} />
                                        </div>
                                        <div className="flex-1 min-w-0">
                                            <div className="flex items-center gap-2">
                                                <p className={`font-medium truncate ${istSelektiert ? 'text-rose-700' : 'text-slate-900'}`}>
                                                    {a.produktname || 'Unbenannter Artikel'}
                                                </p>
                                                {a.werkstoffName && (
                                                    <span className="text-xs bg-slate-100 text-slate-700 px-1.5 py-0.5 rounded flex-shrink-0">
                                                        {a.werkstoffName}
                                                    </span>
                                                )}
                                            </div>
                                            <div className="flex items-center gap-2 text-sm text-slate-500 mt-0.5 flex-wrap">
                                                {a.externeArtikelnummer && (
                                                    <span className="font-mono bg-slate-100 px-1.5 py-0.5 rounded text-xs">
                                                        {a.externeArtikelnummer}
                                                    </span>
                                                )}
                                                {a.kategoriePfad && (
                                                    <span className="inline-flex items-center gap-1 truncate">
                                                        <Tag className="w-3 h-3" />
                                                        {a.kategoriePfad}
                                                    </span>
                                                )}
                                                {a.lieferantenname && !lieferantName && (
                                                    <span className="text-slate-400">· {a.lieferantenname}</span>
                                                )}
                                            </div>
                                            {a.produkttext && (
                                                <p className="text-xs text-slate-400 mt-0.5 truncate">{a.produkttext}</p>
                                            )}
                                        </div>
                                        {!multiSelect && (
                                            <ChevronRight className={`w-5 h-5 flex-shrink-0 ${istSelektiert ? 'text-rose-400' : 'text-slate-300 group-hover:text-slate-400'}`} />
                                        )}
                                    </button>
                                );
                            })}
                        </div>
                    )}
                </div>

                <div className="px-4 py-3 border-t border-slate-200 bg-slate-50 flex items-center justify-between text-sm text-slate-500">
                    <span>
                        {artikel.length}{totalCount > artikel.length ? ` von ${totalCount}` : ''} Artikeln
                        {multiSelect && selected.size > 0 && (
                            <span className="ml-3 font-medium text-rose-700">
                                {selected.size} ausgewählt
                            </span>
                        )}
                    </span>
                    {multiSelect && (
                        <button
                            onClick={handleBestaetigen}
                            disabled={selected.size === 0}
                            className="px-4 py-1.5 bg-rose-600 text-white rounded-md hover:bg-rose-700 disabled:opacity-50 disabled:cursor-not-allowed"
                        >
                            {selected.size} übernehmen
                        </button>
                    )}
                </div>
            </div>
        </div>
    );
}
