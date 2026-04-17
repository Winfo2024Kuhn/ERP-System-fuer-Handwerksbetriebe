import { useState, useEffect, useRef, useCallback } from 'react';
import { Search, X, Package, ChevronRight, Loader2, Filter, Check, Ruler } from 'lucide-react';
import { Input } from './ui/input';
import { Select } from './ui/select-custom';

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
    fixmassMm?: number; // Neu: Für die Übernahme ins Modal
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
    const [filterLieferant, setFilterLieferant] = useState(lieferantName || '');
    const [filterProduktlinie, setFilterProduktlinie] = useState('');
    const [filterWerkstoff, setFilterWerkstoff] = useState('');
    const [showFilters, setShowFilters] = useState(false);

    const [werkstoffOptions, setWerkstoffOptions] = useState<{value: string, label: string}[]>([{ value: '', label: 'Alle Werkstoffe' }]);

    const [artikel, setArtikel] = useState<ArtikelSuchErgebnis[]>([]);
    const [totalCount, setTotalCount] = useState(0);
    const [loading, setLoading] = useState(false);
    
    // selected state includes potentially modified items (e.g., with fixmassMm)
    const [selected, setSelected] = useState<Map<number, ArtikelSuchErgebnis>>(new Map());
    
    const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);
    const abortRef = useRef<AbortController | null>(null);

    // Initial load: Werkstoffe
    useEffect(() => {
        if (isOpen) {
            fetch('/api/artikel/werkstoffe')
                .then(res => res.json())
                .then(data => {
                    if (Array.isArray(data)) {
                        const ops = data.map(w => ({ value: w, label: w }));
                        setWerkstoffOptions([{ value: '', label: 'Alle Werkstoffe' }, ...ops]);
                    }
                })
                .catch(console.error);
        }
    }, [isOpen]);

    const loadArtikel = useCallback(async (query: string, fLieferant: string, fProduktlinie: string, fWerkstoff: string) => {
        if (abortRef.current) abortRef.current.abort();
        const controller = new AbortController();
        abortRef.current = controller;

        setLoading(true);
        try {
            const params = new URLSearchParams({ size: '50', sort: 'produktname', dir: 'asc' });
            if (query.trim()) params.set('q', query.trim());
            if (fLieferant.trim()) params.set('lieferant', fLieferant.trim());
            if (fProduktlinie.trim()) params.set('produktlinie', fProduktlinie.trim());
            if (fWerkstoff.trim()) params.set('werkstoff', fWerkstoff.trim());

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
    }, []);

    useEffect(() => {
        if (isOpen) {
            setSearchTerm('');
            setFilterLieferant(lieferantName || '');
            setFilterProduktlinie('');
            setFilterWerkstoff('');
            setSelected(new Map());
            loadArtikel('', lieferantName || '', '', '');
        }
        return () => {
            if (abortRef.current) abortRef.current.abort();
        };
    }, [isOpen, lieferantName, loadArtikel]);

    useEffect(() => {
        if (!isOpen) return;
        if (debounceRef.current) clearTimeout(debounceRef.current);
        debounceRef.current = setTimeout(() => {
            loadArtikel(searchTerm, filterLieferant, filterProduktlinie, filterWerkstoff);
        }, 300);
        return () => {
            if (debounceRef.current) clearTimeout(debounceRef.current);
        };
    }, [searchTerm, filterLieferant, filterProduktlinie, filterWerkstoff, isOpen, loadArtikel]);

    const handleRowClick = (a: ArtikelSuchErgebnis) => {
        if (multiSelect) {
            setSelected(prev => {
                const next = new Map(prev);
                if (next.has(a.id)) {
                    next.delete(a.id);
                } else {
                    next.set(a.id, { ...a });
                }
                return next;
            });
        } else {
            onSelect(a);
            onClose();
        }
    };

    const updateSelectedFixmass = (id: number, fixmassStr: string, active: boolean) => {
        setSelected(prev => {
            const next = new Map(prev);
            const item = next.get(id);
            if (item) {
                if (!active) {
                    item.fixmassMm = undefined;
                } else {
                    const parsed = parseInt(fixmassStr, 10);
                    item.fixmassMm = isNaN(parsed) ? undefined : parsed;
                }
                next.set(id, { ...item });
            }
            return next;
        });
    };

    const handleBestaetigen = () => {
        if (onSelectMany) onSelectMany(Array.from(selected.values()));
        onClose();
    };

    if (!isOpen) return null;

    const selectedArray = Array.from(selected.values());

    return (
        <div className="fixed inset-0 bg-black/50 z-[60] flex items-center justify-center p-4">
            <div className="bg-white rounded-xl shadow-2xl w-full max-w-4xl max-h-[90vh] flex flex-col overflow-hidden animate-in zoom-in-95 duration-200">
                
                {/* Header */}
                <div className="p-4 border-b border-slate-200 shrink-0">
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

                    <div className="flex gap-2 items-center">
                        <div className="relative flex-1">
                            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-slate-400" />
                            <input
                                type="text"
                                value={searchTerm}
                                onChange={e => setSearchTerm(e.target.value)}
                                placeholder={placeholder}
                                className="w-full pl-10 pr-10 py-2 bg-slate-50 border border-slate-200 rounded-lg text-slate-900 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-rose-500 focus:border-rose-500 transition-shadow"
                                autoFocus
                            />
                            {loading && (
                                <Loader2 className="absolute right-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400 animate-spin" />
                            )}
                        </div>
                        <button 
                            className={`px-3 py-2 border rounded-lg flex items-center gap-2 text-sm transition-colors ${showFilters ? 'bg-rose-50 border-rose-200 text-rose-700' : 'bg-white border-slate-200 text-slate-600 hover:bg-slate-50'}`}
                            onClick={() => setShowFilters(!showFilters)}
                        >
                            <Filter className="w-4 h-4" />
                            Filter
                        </button>
                    </div>

                    {showFilters && (
                        <div className="mt-3 p-3 bg-slate-50 rounded-lg border border-slate-200 grid grid-cols-1 md:grid-cols-3 gap-3 animate-in slide-in-from-top-2">
                            <div>
                                <label className="block text-xs font-medium text-slate-500 mb-1">Lieferant</label>
                                <Input 
                                    value={filterLieferant} 
                                    onChange={e => setFilterLieferant(e.target.value)} 
                                    placeholder="Lieferant..."
                                    disabled={!!lieferantName}
                                    className={lieferantName ? "bg-slate-100" : ""}
                                />
                            </div>
                            <div>
                                <label className="block text-xs font-medium text-slate-500 mb-1">Produktlinie</label>
                                <Input 
                                    value={filterProduktlinie} 
                                    onChange={e => setFilterProduktlinie(e.target.value)} 
                                    placeholder="Linie..."
                                />
                            </div>
                            <div>
                                <label className="block text-xs font-medium text-slate-500 mb-1">Werkstoff</label>
                                <Select 
                                    value={filterWerkstoff} 
                                    onChange={v => setFilterWerkstoff(v)} 
                                    options={werkstoffOptions}
                                />
                            </div>
                        </div>
                    )}
                </div>

                {/* Main Content Area (Split if items selected) */}
                <div className={`flex-1 flex flex-col min-h-0 ${multiSelect && selected.size > 0 ? 'md:flex-row divide-y md:divide-y-0 md:divide-x divide-slate-200' : ''}`}>
                    
                    {/* Search Results */}
                    <div className={`flex-1 overflow-y-auto ${multiSelect && selected.size > 0 ? 'md:w-1/2' : ''}`}>
                        {loading && artikel.length === 0 ? (
                            <div className="text-center py-12 text-slate-400">
                                <Loader2 className="w-8 h-8 mx-auto mb-2 animate-spin opacity-50" />
                                <p>Artikel werden geladen...</p>
                            </div>
                        ) : artikel.length === 0 ? (
                            <div className="text-center py-12 text-slate-400">
                                <Package className="w-10 h-10 mx-auto mb-2 opacity-30" />
                                <p>{searchTerm || filterLieferant || filterProduktlinie || filterWerkstoff ? 'Keine Artikel gefunden' : 'Keine Artikel verfügbar'}</p>
                            </div>
                        ) : (
                            <div className="divide-y divide-slate-100">
                                {artikel.map(a => {
                                    const istSelektiert = selected.has(a.id);
                                    return (
                                        <button
                                            key={`${a.id}-${a.lieferantId ?? 'x'}`}
                                            onClick={() => handleRowClick(a)}
                                            className={`w-full flex items-center gap-3 p-3 text-left transition-colors group
                                                ${istSelektiert
                                                    ? 'bg-rose-50/50'
                                                    : 'hover:bg-slate-50'
                                                }`}
                                        >
                                            {multiSelect && (
                                                <div className="flex-shrink-0 flex items-center justify-center p-1">
                                                    <div className={`w-5 h-5 rounded border flex items-center justify-center transition-colors ${istSelektiert ? 'bg-rose-600 border-rose-600' : 'border-slate-300 bg-white'}`}>
                                                        {istSelektiert && <Check className="w-3.5 h-3.5 text-white" />}
                                                    </div>
                                                </div>
                                            )}
                                            <div className="flex-1 min-w-0 py-1">
                                                <div className="flex items-center gap-2">
                                                    <p className={`font-medium text-sm truncate ${istSelektiert ? 'text-rose-700' : 'text-slate-900'}`}>
                                                        {a.produktname || 'Unbenannter Artikel'}
                                                    </p>
                                                </div>
                                                <div className="flex items-center gap-2 text-xs text-slate-500 mt-1 flex-wrap">
                                                    {a.werkstoffName && (
                                                        <span className="bg-slate-100 px-1.5 py-0.5 rounded flex-shrink-0 text-slate-600">
                                                            {a.werkstoffName}
                                                        </span>
                                                    )}
                                                    {a.externeArtikelnummer && (
                                                        <span className="font-mono text-slate-500">
                                                            #{a.externeArtikelnummer}
                                                        </span>
                                                    )}
                                                    {a.lieferantenname && !lieferantName && (
                                                        <span className="text-slate-400 truncate max-w-[120px]">
                                                            {a.lieferantenname}
                                                        </span>
                                                    )}
                                                </div>
                                            </div>
                                            {!multiSelect && (
                                                <ChevronRight className="w-4 h-4 flex-shrink-0 text-slate-300 group-hover:text-slate-400" />
                                            )}
                                        </button>
                                    );
                                })}
                            </div>
                        )}
                    </div>

                    {/* Selected Items Panel */}
                    {multiSelect && selected.size > 0 && (
                        <div className="flex-1 bg-slate-50 overflow-y-auto flex flex-col md:w-1/2 border-t md:border-t-0">
                            <div className="p-3 bg-slate-100 border-b border-slate-200 sticky top-0 z-10">
                                <h3 className="text-sm font-semibold text-slate-700 flex items-center gap-2">
                                    <Check className="w-4 h-4 text-rose-600" /> Ausgewählte Artikel ({selected.size})
                                </h3>
                            </div>
                            <div className="p-2 space-y-2">
                                {selectedArray.map(sel => {
                                    const hasFixmass = sel.fixmassMm !== undefined;
                                    return (
                                        <div key={`sel-${sel.id}`} className="bg-white border border-slate-200 rounded-lg p-3 shadow-sm animate-in slide-in-from-right-4 duration-200">
                                            <div className="flex justify-between items-start mb-2">
                                                <div className="min-w-0 pr-2">
                                                    <p className="text-sm font-medium text-slate-900 truncate" title={sel.produktname}>
                                                        {sel.produktname}
                                                    </p>
                                                    <p className="text-xs text-slate-500 truncate">
                                                        {sel.werkstoffName} {sel.externeArtikelnummer ? `· #${sel.externeArtikelnummer}` : ''}
                                                    </p>
                                                </div>
                                                <button
                                                    onClick={() => handleRowClick(sel)}
                                                    className="p-1 text-slate-400 hover:text-red-500 hover:bg-slate-100 rounded"
                                                    title="Entfernen"
                                                >
                                                    <X className="w-4 h-4" />
                                                </button>
                                            </div>
                                            <div className="flex items-center gap-2 mt-2 pt-2 border-t border-slate-100">
                                                <label className="flex items-center gap-2 text-xs font-medium text-slate-600 cursor-pointer">
                                                    <input 
                                                        type="checkbox" 
                                                        className="w-3.5 h-3.5 text-rose-600 rounded border-slate-300 focus:ring-rose-500"
                                                        checked={hasFixmass}
                                                        onChange={(e) => updateSelectedFixmass(sel.id, sel.fixmassMm?.toString() || '', e.target.checked)}
                                                    />
                                                    <Ruler className="w-3.5 h-3.5 text-slate-400" />
                                                    Fixmaß
                                                </label>
                                                {hasFixmass && (
                                                    <div className="flex items-center gap-1.5 ml-2 animate-in fade-in slide-in-from-left-2">
                                                        <Input 
                                                            type="number"
                                                            value={sel.fixmassMm || ''}
                                                            onChange={(e) => updateSelectedFixmass(sel.id, e.target.value, true)}
                                                            className="h-7 text-xs w-20 px-2"
                                                            placeholder="z.B. 6000"
                                                            min="1"
                                                            autoFocus
                                                        />
                                                        <span className="text-xs text-slate-500">mm</span>
                                                    </div>
                                                )}
                                            </div>
                                        </div>
                                    );
                                })}
                            </div>
                        </div>
                    )}
                </div>

                {/* Footer */}
                <div className="p-3 border-t border-slate-200 bg-white flex items-center justify-between shrink-0">
                    <span className="text-xs text-slate-500">
                        {artikel.length}{totalCount > artikel.length ? ` von ${totalCount}` : ''} Artikeln gefunden
                    </span>
                    <div className="flex items-center gap-3">
                        <button
                            onClick={onClose}
                            className="px-4 py-2 text-sm font-medium text-slate-600 hover:bg-slate-100 rounded-lg transition-colors"
                        >
                            Abbrechen
                        </button>
                        {multiSelect && (
                            <button
                                onClick={handleBestaetigen}
                                disabled={selected.size === 0}
                                className="px-5 py-2 text-sm font-medium bg-rose-600 text-white rounded-lg hover:bg-rose-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors shadow-sm"
                            >
                                {selected.size} Artikel übernehmen
                            </button>
                        )}
                    </div>
                </div>
            </div>
        </div>
    );
}
