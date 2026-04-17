import { useState, useEffect, useRef, useCallback, useMemo } from 'react';
import {
    Search,
    X,
    Package,
    Loader2,
    Check,
    ChevronLeft,
    ChevronRight,
} from 'lucide-react';
import { Input } from './ui/input';
import { Label } from './ui/label';
import { Select } from './ui/select-custom';
import { Button } from './ui/button';
import { KategoriePicker, type KategorieFlach } from './KategoriePicker';
import { cn } from '../lib/utils';

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
    preisDatum?: string | null;
    kgProMeter?: number | null;
    verpackungseinheit?: string | null;
    verrechnungseinheit?: string | { name: string; anzeigename?: string } | null;
    fixmassMm?: number;
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
}

const PAGE_SIZE = 20;

const formatCurrency = (val?: number | null) =>
    val != null ? new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR' }).format(val) : '-';

const formatKg = (val?: number | null) =>
    val != null ? new Intl.NumberFormat('de-DE', { minimumFractionDigits: 3, maximumFractionDigits: 3 }).format(val) : '';

const getVerrechnungseinheitLabel = (v: ArtikelSuchErgebnis['verrechnungseinheit']): string => {
    if (!v) return '';
    if (typeof v === 'string') return v;
    return v.anzeigename || v.name || '';
};

export function ArtikelSearchModal({
    isOpen,
    onClose,
    onSelect,
    lieferantName,
    multiSelect = false,
    onSelectMany,
}: ArtikelSearchModalProps) {
    // Filter
    const [searchTerm, setSearchTerm] = useState('');
    const [filterLieferant, setFilterLieferant] = useState(lieferantName || '');
    const [filterProduktlinie, setFilterProduktlinie] = useState('');
    const [filterWerkstoff, setFilterWerkstoff] = useState('');
    const [filterKategorieId, setFilterKategorieId] = useState<number | null>(null);

    // Sort & Pagination
    const [sortColumn, setSortColumn] = useState('produktname');
    const [sortDirection, setSortDirection] = useState<'asc' | 'desc'>('asc');
    const [page, setPage] = useState(0);

    // Stammdaten
    const [werkstoffOptions, setWerkstoffOptions] = useState<{ value: string; label: string }[]>([
        { value: '', label: 'Alle Werkstoffe' },
    ]);
    const [kategorien, setKategorien] = useState<KategorieFlach[]>([]);

    // Ergebnisse
    const [artikel, setArtikel] = useState<ArtikelSuchErgebnis[]>([]);
    const [totalCount, setTotalCount] = useState(0);
    const [loading, setLoading] = useState(false);

    // Auswahl
    const [selected, setSelected] = useState<Map<number, ArtikelSuchErgebnis>>(new Map());

    const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);
    const abortRef = useRef<AbortController | null>(null);

    // Stammdaten laden beim Öffnen
    useEffect(() => {
        if (!isOpen) return;
        fetch('/api/artikel/werkstoffe')
            .then(res => res.json())
            .then(data => {
                if (Array.isArray(data)) {
                    setWerkstoffOptions([
                        { value: '', label: 'Alle Werkstoffe' },
                        ...data.map((w: string) => ({ value: w, label: w })),
                    ]);
                }
            })
            .catch(console.error);

        fetch('/api/kategorien')
            .then(res => res.json())
            .then(data => setKategorien(Array.isArray(data) ? data : []))
            .catch(console.error);
    }, [isOpen]);

    // Reset beim Öffnen
    useEffect(() => {
        if (!isOpen) return;
        setSearchTerm('');
        setFilterLieferant(lieferantName || '');
        setFilterProduktlinie('');
        setFilterWerkstoff('');
        setFilterKategorieId(null);
        setSortColumn('produktname');
        setSortDirection('asc');
        setPage(0);
        setSelected(new Map());
        return () => {
            if (abortRef.current) abortRef.current.abort();
        };
    }, [isOpen, lieferantName]);

    // Artikel laden (mit Debounce)
    const loadArtikel = useCallback(async () => {
        if (abortRef.current) abortRef.current.abort();
        const controller = new AbortController();
        abortRef.current = controller;

        setLoading(true);
        try {
            const params = new URLSearchParams({
                page: String(page),
                size: String(PAGE_SIZE),
                sort: sortColumn,
                dir: sortDirection,
            });
            if (searchTerm.trim()) params.set('q', searchTerm.trim());
            if (filterLieferant.trim()) params.set('lieferant', filterLieferant.trim());
            if (filterProduktlinie.trim()) params.set('produktlinie', filterProduktlinie.trim());
            if (filterWerkstoff.trim()) params.set('werkstoff', filterWerkstoff.trim());
            if (filterKategorieId) params.set('kategorieId', String(filterKategorieId));

            const res = await fetch(`/api/artikel?${params}`, { signal: controller.signal });
            if (!res.ok) throw new Error('Fehler beim Laden');
            const data = await res.json();
            const list: ArtikelSuchErgebnis[] = Array.isArray(data?.artikel) ? data.artikel : [];
            setArtikel(list);
            setTotalCount(typeof data?.gesamt === 'number' ? data.gesamt : list.length);
        } catch (e) {
            if (!(e instanceof DOMException && e.name === 'AbortError')) {
                console.error('Artikelsuche fehlgeschlagen:', e);
                setArtikel([]);
                setTotalCount(0);
            }
        } finally {
            if (!controller.signal.aborted) setLoading(false);
        }
    }, [page, sortColumn, sortDirection, searchTerm, filterLieferant, filterProduktlinie, filterWerkstoff, filterKategorieId]);

    useEffect(() => {
        if (!isOpen) return;
        if (debounceRef.current) clearTimeout(debounceRef.current);
        debounceRef.current = setTimeout(() => {
            loadArtikel();
        }, 250);
        return () => {
            if (debounceRef.current) clearTimeout(debounceRef.current);
        };
    }, [isOpen, loadArtikel]);

    // Seite zurücksetzen bei Filter-/Sortieränderung
    useEffect(() => {
        setPage(0);
    }, [searchTerm, filterLieferant, filterProduktlinie, filterWerkstoff, filterKategorieId, sortColumn, sortDirection]);

    // Handlers
    const handleSort = (col: string) => {
        if (sortColumn === col) {
            setSortDirection(prev => (prev === 'asc' ? 'desc' : 'asc'));
        } else {
            setSortColumn(col);
            setSortDirection('asc');
        }
    };

    const handleRowClick = (a: ArtikelSuchErgebnis) => {
        if (multiSelect) {
            setSelected(prev => {
                const next = new Map(prev);
                if (next.has(a.id)) next.delete(a.id);
                else next.set(a.id, { ...a });
                return next;
            });
        } else {
            onSelect(a);
            onClose();
        }
    };

    const handleResetFilter = () => {
        setSearchTerm('');
        if (!lieferantName) setFilterLieferant('');
        setFilterProduktlinie('');
        setFilterWerkstoff('');
        setFilterKategorieId(null);
    };

    const handleBestaetigen = () => {
        if (onSelectMany) onSelectMany(Array.from(selected.values()));
        onClose();
    };

    const totalPages = Math.max(1, Math.ceil(totalCount / PAGE_SIZE));
    const selectedArray = useMemo(() => Array.from(selected.values()), [selected]);
    const hasActiveFilter =
        searchTerm || (filterLieferant && !lieferantName) || filterProduktlinie || filterWerkstoff || filterKategorieId;

    if (!isOpen) return null;

    return (
        <div
            className="fixed inset-4 z-[60] bg-white rounded-2xl shadow-2xl flex flex-col overflow-hidden border border-slate-200"
            role="dialog"
            aria-modal="true"
            aria-labelledby="artikelsuche-title"
        >
            {/* Header */}
            <div className="flex items-center justify-between px-6 py-4 border-b border-slate-200 bg-gradient-to-r from-rose-50 to-white shrink-0">
                <div className="flex items-center gap-3">
                    <div className="p-2 bg-rose-100 text-rose-600 rounded-lg">
                        <Package className="w-5 h-5" />
                    </div>
                    <div>
                        <h2 id="artikelsuche-title" className="text-xl font-bold text-slate-900">
                            {multiSelect ? 'Artikel auswählen (Mehrfachauswahl)' : 'Artikel auswählen'}
                        </h2>
                        <p className="text-sm text-slate-500">
                            {lieferantName
                                ? <>Vorgefiltert auf Lieferant <span className="font-medium text-rose-700">{lieferantName}</span></>
                                : 'Mehrere Filter kombinierbar — Auswahl per Klick'}
                        </p>
                    </div>
                </div>
                <div className="flex items-center gap-2">
                    <Button variant="ghost" onClick={onClose}>Abbrechen</Button>
                    {multiSelect && (
                        <Button
                            onClick={handleBestaetigen}
                            disabled={selected.size === 0}
                            className="bg-rose-600 text-white hover:bg-rose-700"
                        >
                            <Check className="w-4 h-4 mr-2" />
                            {selected.size === 0
                                ? 'Keine Auswahl'
                                : `${selected.size} Artikel übernehmen`}
                        </Button>
                    )}
                    <Button variant="ghost" size="sm" onClick={onClose}>
                        <X className="w-5 h-5" />
                    </Button>
                </div>
            </div>

            {/* Filterleiste */}
            <div className="px-6 py-4 bg-slate-50 border-b border-slate-200 shrink-0">
                <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-6 gap-3">
                    <div className="xl:col-span-2">
                        <Label className="text-xs font-semibold text-slate-500 uppercase tracking-wide">Freitext</Label>
                        <div className="relative mt-1">
                            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400 pointer-events-none" />
                            <input
                                type="text"
                                value={searchTerm}
                                onChange={e => setSearchTerm(e.target.value)}
                                placeholder="Name, Nummer, Werkstoff..."
                                className="h-10 w-full pl-9 pr-9 rounded-md border border-slate-200 bg-white text-sm placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-rose-500 focus:border-rose-500"
                                autoFocus
                            />
                            {loading && (
                                <Loader2 className="absolute right-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400 animate-spin" />
                            )}
                        </div>
                    </div>
                    <div>
                        <Label className="text-xs font-semibold text-slate-500 uppercase tracking-wide">Lieferant</Label>
                        <Input
                            value={filterLieferant}
                            onChange={e => setFilterLieferant(e.target.value)}
                            placeholder="Alle Lieferanten"
                            disabled={!!lieferantName}
                            className={cn('mt-1', lieferantName && 'bg-slate-100 cursor-not-allowed')}
                        />
                    </div>
                    <div>
                        <Label className="text-xs font-semibold text-slate-500 uppercase tracking-wide">Kategorie</Label>
                        <div className="mt-1">
                            <KategoriePicker
                                kategorien={kategorien}
                                value={filterKategorieId}
                                onChange={id => setFilterKategorieId(id)}
                                placeholder="Alle Kategorien"
                                allowClear
                            />
                        </div>
                    </div>
                    <div>
                        <Label className="text-xs font-semibold text-slate-500 uppercase tracking-wide">Werkstoff</Label>
                        <div className="mt-1">
                            <Select
                                value={filterWerkstoff}
                                onChange={v => setFilterWerkstoff(v)}
                                options={werkstoffOptions}
                            />
                        </div>
                    </div>
                    <div>
                        <Label className="text-xs font-semibold text-slate-500 uppercase tracking-wide">Produktlinie</Label>
                        <Input
                            value={filterProduktlinie}
                            onChange={e => setFilterProduktlinie(e.target.value)}
                            placeholder="Produktlinie"
                            className="mt-1"
                        />
                    </div>
                </div>
                {hasActiveFilter && (
                    <div className="mt-3 flex justify-end">
                        <button
                            type="button"
                            onClick={handleResetFilter}
                            className="text-xs text-slate-500 hover:text-rose-600 underline-offset-2 hover:underline"
                        >
                            Filter zurücksetzen
                        </button>
                    </div>
                )}
            </div>

            {/* Hauptbereich: Tabelle + ggf. Auswahl-Sidebar */}
            <div className="flex-1 flex min-h-0 overflow-hidden">
                {/* Tabelle */}
                <div className="flex-1 overflow-auto bg-white">
                    {loading && artikel.length === 0 ? (
                        <div className="flex items-center justify-center h-full py-16 text-slate-400">
                            <Loader2 className="w-8 h-8 animate-spin opacity-60" />
                        </div>
                    ) : artikel.length === 0 ? (
                        <div className="flex flex-col items-center justify-center h-full py-16 text-slate-400">
                            <Package className="w-12 h-12 mb-2 opacity-30" />
                            <p className="text-sm">
                                {hasActiveFilter ? 'Keine Artikel gefunden' : 'Keine Artikel verfügbar'}
                            </p>
                        </div>
                    ) : (
                        <table className="w-full text-sm text-left">
                            <thead className="bg-slate-50 text-slate-500 font-medium border-b border-slate-200 sticky top-0 z-10">
                                <tr>
                                    {multiSelect && (
                                        <th className="px-3 py-3 w-10 text-center">
                                            <span className="sr-only">Auswahl</span>
                                        </th>
                                    )}
                                    <SortableHeader label="Nr." column="externeArtikelnummer" currentSort={sortColumn} direction={sortDirection} onSort={handleSort} />
                                    <SortableHeader label="Produktlinie" column="produktlinie" currentSort={sortColumn} direction={sortDirection} onSort={handleSort} />
                                    <SortableHeader label="Name" column="produktname" currentSort={sortColumn} direction={sortDirection} onSort={handleSort} />
                                    <SortableHeader label="Text" column="produkttext" currentSort={sortColumn} direction={sortDirection} onSort={handleSort} />
                                    <SortableHeader label="VPE" column="verpackungseinheit" currentSort={sortColumn} direction={sortDirection} onSort={handleSort} className="text-center" />
                                    <SortableHeader label="Werkstoff" column="werkstoffName" currentSort={sortColumn} direction={sortDirection} onSort={handleSort} />
                                    {!lieferantName && (
                                        <SortableHeader label="Lieferant" column="lieferantenname" currentSort={sortColumn} direction={sortDirection} onSort={handleSort} />
                                    )}
                                    <SortableHeader label="kg/m" column="kgProMeter" currentSort={sortColumn} direction={sortDirection} onSort={handleSort} className="text-right" />
                                    <SortableHeader label="Preis" column="preis" currentSort={sortColumn} direction={sortDirection} onSort={handleSort} className="text-right" />
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-slate-100">
                                {artikel.map(a => {
                                    const istSelektiert = selected.has(a.id);
                                    return (
                                        <tr
                                            key={`${a.id}-${a.lieferantId ?? 'x'}`}
                                            onClick={() => handleRowClick(a)}
                                            className={cn(
                                                'cursor-pointer transition-colors',
                                                istSelektiert ? 'bg-rose-50 hover:bg-rose-100' : 'hover:bg-slate-50'
                                            )}
                                        >
                                            {multiSelect && (
                                                <td className="px-3 py-3 text-center">
                                                    <div
                                                        className={cn(
                                                            'w-5 h-5 mx-auto rounded border flex items-center justify-center transition-colors',
                                                            istSelektiert ? 'bg-rose-600 border-rose-600' : 'border-slate-300 bg-white'
                                                        )}
                                                    >
                                                        {istSelektiert && <Check className="w-3.5 h-3.5 text-white" />}
                                                    </div>
                                                </td>
                                            )}
                                            <td className="px-4 py-3 font-mono text-xs text-slate-600">{a.externeArtikelnummer || '-'}</td>
                                            <td className="px-4 py-3 text-slate-600">{a.produktlinie || '-'}</td>
                                            <td className={cn('px-4 py-3 font-medium', istSelektiert ? 'text-rose-700' : 'text-slate-900')}>
                                                {a.produktname || 'Unbenannt'}
                                            </td>
                                            <td className="px-4 py-3 text-slate-500 max-w-[220px] truncate" title={a.produkttext ?? undefined}>
                                                {a.produkttext || '-'}
                                            </td>
                                            <td className="px-4 py-3 text-center text-slate-600">{a.verpackungseinheit || '-'}</td>
                                            <td className="px-4 py-3 text-slate-600">{a.werkstoffName || '-'}</td>
                                            {!lieferantName && (
                                                <td className="px-4 py-3 text-slate-600">{a.lieferantenname || '-'}</td>
                                            )}
                                            <td className="px-4 py-3 text-right text-slate-600">{formatKg(a.kgProMeter)}</td>
                                            <td className="px-4 py-3 text-right font-medium text-slate-900 whitespace-nowrap">
                                                {formatCurrency(a.preis)}
                                                {a.verrechnungseinheit && (
                                                    <span className="text-xs font-normal text-slate-400 ml-1">
                                                        / {getVerrechnungseinheitLabel(a.verrechnungseinheit)}
                                                    </span>
                                                )}
                                            </td>
                                        </tr>
                                    );
                                })}
                            </tbody>
                        </table>
                    )}
                </div>

                {/* Auswahl-Sidebar (nur multiSelect & etwas ausgewählt) */}
                {multiSelect && selected.size > 0 && (
                    <aside className="w-80 border-l border-slate-200 bg-slate-50 flex flex-col shrink-0">
                        <div className="px-4 py-3 border-b border-slate-200 bg-white shrink-0">
                            <h3 className="text-sm font-semibold text-slate-900 flex items-center gap-2">
                                <Check className="w-4 h-4 text-rose-600" />
                                Ausgewählt ({selected.size})
                            </h3>
                            <p className="text-xs text-slate-500 mt-0.5">
                                Mengen & Zeugnisse werden anschließend je Position festgelegt.
                            </p>
                        </div>
                        <div className="flex-1 overflow-y-auto p-2 space-y-2">
                            {selectedArray.map(sel => (
                                <div
                                    key={`sel-${sel.id}`}
                                    className="bg-white border border-slate-200 rounded-lg p-2.5 shadow-sm flex items-start gap-2"
                                >
                                    <div className="min-w-0 flex-1">
                                        <p className="text-sm font-medium text-slate-900 truncate" title={sel.produktname}>
                                            {sel.produktname}
                                        </p>
                                        <div className="flex items-center gap-1.5 mt-0.5 text-xs text-slate-500 flex-wrap">
                                            {sel.werkstoffName && (
                                                <span className="bg-slate-100 px-1.5 py-0.5 rounded">{sel.werkstoffName}</span>
                                            )}
                                            {sel.externeArtikelnummer && (
                                                <span className="font-mono">#{sel.externeArtikelnummer}</span>
                                            )}
                                        </div>
                                    </div>
                                    <button
                                        type="button"
                                        onClick={() => handleRowClick(sel)}
                                        className="p-1 text-slate-400 hover:text-red-500 hover:bg-red-50 rounded"
                                        title="Aus Auswahl entfernen"
                                        aria-label={`${sel.produktname} aus Auswahl entfernen`}
                                    >
                                        <X className="w-4 h-4" />
                                    </button>
                                </div>
                            ))}
                        </div>
                    </aside>
                )}
            </div>

            {/* Footer: Paginierung & Status */}
            <div className="px-6 py-3 border-t border-slate-200 bg-white flex items-center justify-between shrink-0">
                <span className="text-xs text-slate-500">
                    {totalCount === 0
                        ? 'Keine Treffer'
                        : `Zeige ${page * PAGE_SIZE + 1}–${Math.min((page + 1) * PAGE_SIZE, totalCount)} von ${totalCount}`}
                </span>
                <div className="flex items-center gap-2">
                    <Button
                        variant="outline"
                        size="sm"
                        disabled={page === 0 || loading}
                        onClick={() => setPage(p => Math.max(0, p - 1))}
                    >
                        <ChevronLeft className="w-4 h-4 mr-1" />
                        Zurück
                    </Button>
                    <span className="text-xs text-slate-600 px-2">
                        Seite {page + 1} / {totalPages}
                    </span>
                    <Button
                        variant="outline"
                        size="sm"
                        disabled={page >= totalPages - 1 || loading}
                        onClick={() => setPage(p => p + 1)}
                    >
                        Weiter
                        <ChevronRight className="w-4 h-4 ml-1" />
                    </Button>
                </div>
            </div>
        </div>
    );
}

interface SortableHeaderProps {
    label: string;
    column: string;
    currentSort: string;
    direction: 'asc' | 'desc';
    onSort: (col: string) => void;
    className?: string;
}

function SortableHeader({ label, column, currentSort, direction, onSort, className }: SortableHeaderProps) {
    const aktiv = currentSort === column;
    return (
        <th
            className={cn(
                'px-4 py-3 cursor-pointer hover:bg-slate-100 transition-colors select-none whitespace-nowrap',
                className
            )}
            onClick={() => onSort(column)}
        >
            <div
                className={cn(
                    'flex items-center gap-1',
                    className?.includes('text-right') && 'justify-end',
                    className?.includes('text-center') && 'justify-center'
                )}
            >
                {label}
                <span className={cn('w-3 text-xs', aktiv ? 'text-rose-600' : 'text-slate-300')}>
                    {aktiv ? (direction === 'asc' ? '▲' : '▼') : '↕'}
                </span>
            </div>
        </th>
    );
}
