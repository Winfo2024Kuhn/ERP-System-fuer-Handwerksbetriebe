import { useCallback, useEffect, useMemo, useState } from "react";
import { RefreshCw, Package, ChevronLeft, ChevronRight, Save, X, Search, Folder, FolderPlus, Plus, Sparkles, TrendingUp } from "lucide-react";
import { Button } from "../components/ui/button";
import { Input } from "../components/ui/input";
import { Label } from "../components/ui/label";
import { Select } from "../components/ui/select-custom";
import { cn } from "../lib/utils";
import type { Artikel } from "../types";
import { SupplierSelectModal } from "../components/SupplierSelectModal";
import { CategoryTreeModal } from "../components/CategoryTreeModal";
import { CreateArticleModal } from "../components/CreateArticleModal";
import { ArtikelVorschlaegeModal } from "../components/ArtikelVorschlaegeModal";
import { ArtikelDetailModal } from "../components/ArtikelDetailModal";
import { PageLayout } from "../components/layout/PageLayout";
import { useToast } from '../components/ui/toast';

const PAGE_SIZE = 12;

// Helpers
const formatCurrency = (val?: number) =>
  val === undefined || val === null
    ? '—'
    : new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR', minimumFractionDigits: 2, maximumFractionDigits: 4 }).format(val);
const formatDateTime = (dateStr?: string) =>
  dateStr
    ? new Date(dateStr).toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric' })
    : '—';
const formatKg = (val?: number) => val ? new Intl.NumberFormat('de-DE', { minimumFractionDigits: 3, maximumFractionDigits: 3 }).format(val) : '';

const einheitKuerzel = (einheit?: string | { name: string; anzeigename?: string }): string => {
  const name = typeof einheit === 'string' ? einheit : einheit?.name;
  switch (name) {
    case 'KILOGRAMM': return 'kg';
    case 'LAUFENDE_METER': return 'm';
    case 'QUADRATMETER': return 'm²';
    case 'STUECK': return 'Stk';
    default:
      if (!einheit) return '';
      return typeof einheit === 'string' ? einheit : (einheit.anzeigename || einheit.name);
  }
};

// ==================== MAIN COMPONENT ====================

export default function ArtikelEditor() {
    const toast = useToast();
    useEffect(() => { console.log('ArtikelEditor mounted'); }, []);
    const [artikelList, setArtikelList] = useState<Artikel[]>([]);
    const [loading, setLoading] = useState(false);
    const [total, setTotal] = useState(0);
    const [page, setPage] = useState(0);

    // Detail/Edit state
    const [detailArtikel, setDetailArtikel] = useState<Artikel | null>(null);
    const [editingArticle, setEditingArticle] = useState<Artikel | null>(null);
    const [editSupplierData, setEditSupplierData] = useState<{ id: number, name: string } | null>(null);

    // Modals state
    const [showSupplierModal, setShowSupplierModal] = useState(false);
    const [showCategoryModal, setShowCategoryModal] = useState(false);
    const [showCategoryManagerModal, setShowCategoryManagerModal] = useState(false);
    const [showCreateModal, setShowCreateModal] = useState(false);
    const [showVorschlaegeModal, setShowVorschlaegeModal] = useState(false);
    const [pendingCount, setPendingCount] = useState(0);
    const [supplierSelectionMode, setSupplierSelectionMode] = useState<'filter' | 'edit'>('filter');

    const ladePendingCount = useCallback(() => {
        fetch('/api/artikel-vorschlaege/count')
            .then(res => res.ok ? res.json() : { pending: 0 })
            .then(data => setPendingCount(data?.pending ?? 0))
            .catch(() => setPendingCount(0));
    }, []);

    useEffect(() => {
        ladePendingCount();
        const intervalId = window.setInterval(ladePendingCount, 60_000);
        return () => window.clearInterval(intervalId);
    }, [ladePendingCount]);

    // Sort state
    const [sortColumn, setSortColumn] = useState('produktname');
    const [sortDirection, setSortDirection] = useState<'asc' | 'desc'>('asc');

    const [filters, setFilters] = useState({
        q: "",
        lieferant: "",
        produktlinie: "",
        werkstoff: "",
        kategorieId: 0,
        kategorieName: ""
    });

    const [werkstoffOptions, setWerkstoffOptions] = useState<string[]>([]);

    // Fetch initial data (Werkstoffe)
    useEffect(() => {
        fetch('/api/artikel/werkstoffe')
            .then(res => res.json())
            .then(data => setWerkstoffOptions(Array.isArray(data) ? data : []))
            .catch(err => console.error("Fehler beim Laden der Werkstoffe", err));
    }, []);

    // Fetch List
    const loadArtikel = useCallback(async () => {
        setLoading(true);
        try {
            const params = new URLSearchParams();
            params.set("page", String(page));
            params.set("size", String(PAGE_SIZE));
            params.set("sort", sortColumn);
            params.set("dir", sortDirection);

            if (filters.q) params.set("q", filters.q);
            if (filters.lieferant) params.set("lieferant", filters.lieferant);
            if (filters.produktlinie) params.set("produktlinie", filters.produktlinie);
            if (filters.werkstoff) params.set("werkstoff", filters.werkstoff);
            if (filters.kategorieId) params.set("kategorieId", String(filters.kategorieId));

            const res = await fetch(`/api/artikel?${params.toString()}`);
            if (!res.ok) throw new Error("Fehler beim Laden");
            const data = await res.json();

            setArtikelList(data && Array.isArray(data.artikel) ? data.artikel : []);
            setTotal(data && typeof data.gesamt === "number" ? data.gesamt : 0);
        } catch (err) {
            console.error(err);
            setArtikelList([]);
            setTotal(0);
        } finally {
            setLoading(false);
        }
    }, [page, filters, sortColumn, sortDirection]);

    useEffect(() => {
        loadArtikel();
    }, [loadArtikel]);

    // Synchronisiere das Detail-Modal mit der neu geladenen Liste (z.B. nach Preis-Save)
    useEffect(() => {
        if (!detailArtikel) return;
        const frisch = artikelList.find(a => a.id === detailArtikel.id);
        if (frisch && frisch !== detailArtikel) {
            setDetailArtikel(frisch);
        }
    }, [artikelList, detailArtikel]);

    const handleFilterChange = (key: string, value: string | number) => {
        setFilters((prev) => ({ ...prev, [key]: value }));
    };

    const handleFilterSubmit = (e: React.FormEvent) => {
        e.preventDefault();
        setPage(0);
        loadArtikel();
    };

    const handleResetFilters = () => {
        setFilters({ q: "", lieferant: "", produktlinie: "", werkstoff: "", kategorieId: 0, kategorieName: "" });
        setPage(0);
    };

    const handleSort = (column: string) => {
        if (sortColumn === column) {
            setSortDirection(prev => prev === 'asc' ? 'desc' : 'asc');
        } else {
            setSortColumn(column);
            setSortDirection('asc');
        }
    };

    const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE));

    const statusText = useMemo(() => {
        if (loading) return 'Artikel werden geladen...';
        if (total === 0) return 'Keine Artikel gefunden.';
        const start = page * PAGE_SIZE + 1;
        const end = Math.min(start + artikelList.length - 1, total);
        return `Zeige ${start}-${end} von ${total} Artikeln`;
    }, [loading, total, page, artikelList.length]);

    const openDetail = (artikel: Artikel) => {
        setDetailArtikel(artikel);
    };

    const openEditFromDetail = (artikel: Artikel, lieferant: { id: number; name: string } | null) => {
        setEditingArticle(artikel);
        setEditSupplierData(lieferant);
    };

    const handleSavePrice = async (price: number, exNummer: string) => {
        if (!editingArticle) return;
        if (!editSupplierData) {
            toast.warning("Bitte wählen Sie einen Lieferanten aus.");
            return;
        }

        try {
            const hatBereitsPreis = (editingArticle.lieferantenpreise ?? [])
                .some(lp => lp.lieferantId === editSupplierData.id);

            const res = hatBereitsPreis
                ? await fetch(`/api/lieferanten/${editSupplierData.id}/artikelpreise/${editingArticle.id}`, {
                    method: 'PUT',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ preis: price, externeArtikelnummer: exNummer })
                })
                : await fetch(`/api/lieferanten/${editSupplierData.id}/artikelpreise`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        artikelId: editingArticle.id,
                        preis: price,
                        externeArtikelnummer: exNummer
                    })
                });

            if (!res.ok) throw new Error('Speichern fehlgeschlagen');

            setEditingArticle(null);
            setEditSupplierData(null);
            loadArtikel();
        } catch (err) {
            toast.error('Fehler beim Speichern des Preises');
            console.error(err);
        }
    };

    const handleSupplierSelect = (s: { id: number, name: string }) => {
        if (supplierSelectionMode === 'filter') {
            handleFilterChange('lieferant', s.name);
        } else {
            setEditSupplierData(s);
        }
        setShowSupplierModal(false);
    };

    return (
        <PageLayout
            ribbonCategory="Katalog"
            title="Artikelübersicht"
            subtitle="Verwaltung der Artikel und Preise."
            actions={
                <>
                    <Button
                        size="sm"
                        variant="outline"
                        className={cn(
                            "relative border-rose-300 text-rose-700 hover:bg-rose-50",
                            pendingCount > 0 && "border-rose-500 text-rose-800 bg-rose-50 shadow-sm"
                        )}
                        onClick={() => setShowVorschlaegeModal(true)}
                        title="Vorgeschlagene neue Materialien aus Lieferantenrechnungen pr\u00fcfen"
                    >
                        <Sparkles className="w-4 h-4 mr-2" />
                        KI-Vorschläge
                        {pendingCount > 0 && (
                            <span className="ml-2 inline-flex items-center justify-center min-w-[20px] h-5 px-1.5 rounded-full bg-rose-600 text-white text-xs font-semibold">
                                {pendingCount > 99 ? '99+' : pendingCount}
                            </span>
                        )}
                    </Button>
                    <Button size="sm" className="bg-rose-600 text-white hover:bg-rose-700" onClick={() => setShowCreateModal(true)}>
                        <Plus className="w-4 h-4 mr-2" />
                        Neu
                    </Button>
                    <Button
                        variant="outline"
                        size="sm"
                        className="border-rose-300 text-rose-700 hover:bg-rose-50"
                        onClick={() => setShowCategoryManagerModal(true)}
                    >
                        <FolderPlus className="w-4 h-4 mr-2" />
                        Kategorien anlegen
                    </Button>
                    <Button variant="outline" size="sm" onClick={loadArtikel}>
                        <RefreshCw className={cn("w-4 h-4 mr-2", loading && "animate-spin")} />
                        Aktualisieren
                    </Button>
                </>
            }
        >
            {/* Filter */}
            <div className="bg-white p-6 rounded-2xl shadow-lg border border-slate-100">
                <form onSubmit={handleFilterSubmit} className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-5 gap-4">
                    <div>
                        <Label htmlFor="filter-q" className="text-sm font-medium text-gray-700">Freitext</Label>
                        <Input id="filter-q" type="text" className="mt-1" placeholder="Name, Nummer..." value={filters.q} onChange={e => handleFilterChange('q', e.target.value)} />
                    </div>
                    <div>
                        <Label className="text-sm font-medium text-gray-700">Lieferant</Label>
                        <div className="relative mt-1">
                            <div
                                className="flex h-10 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm ring-offset-white file:border-0 file:bg-transparent file:text-sm file:font-medium placeholder:text-slate-500 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-500 focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50 cursor-pointer flex items-center justify-between"
                                onClick={() => { setSupplierSelectionMode('filter'); setShowSupplierModal(true); }}
                            >
                                <span className={!filters.lieferant ? "text-slate-500" : ""}>{filters.lieferant || "Alle Lieferanten"}</span>
                                <Search className="w-4 h-4 text-slate-400" />
                            </div>
                            {filters.lieferant && (
                                <button
                                    type="button"
                                    className="absolute right-8 top-2.5 text-slate-400 hover:text-rose-600"
                                    onClick={(e) => { e.stopPropagation(); handleFilterChange('lieferant', ''); }}
                                >
                                    <X className="w-4 h-4" />
                                </button>
                            )}
                        </div>
                    </div>
                    <div>
                        <Label className="text-sm font-medium text-gray-700">Kategorie</Label>
                        <div className="relative mt-1">
                            <div
                                className="flex h-10 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm ring-offset-white file:border-0 file:bg-transparent file:text-sm file:font-medium placeholder:text-slate-500 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-500 focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50 cursor-pointer flex items-center justify-between"
                                onClick={() => setShowCategoryModal(true)}
                            >
                                <span className={!filters.kategorieName ? "text-slate-500" : "truncate"}>{filters.kategorieName || "Alle Kategorien"}</span>
                                <Folder className="w-4 h-4 text-slate-400 ml-2 shrink-0" />
                            </div>
                            {filters.kategorieId !== 0 && (
                                <button
                                    type="button"
                                    className="absolute right-8 top-2.5 text-slate-400 hover:text-rose-600"
                                    onClick={(e) => { e.stopPropagation(); handleFilterChange('kategorieId', 0); handleFilterChange('kategorieName', ''); }}
                                >
                                    <X className="w-4 h-4" />
                                </button>
                            )}
                        </div>
                    </div>
                    <div>
                        <Label htmlFor="filter-werkstoff" className="text-sm font-medium text-gray-700">Werkstoff</Label>
                        <div className="mt-1">
                            <Select
                                options={[{ value: "", label: "Alle Werkstoffe" }, ...werkstoffOptions.map(opt => ({ value: opt, label: opt }))]}
                                value={filters.werkstoff}
                                onChange={val => handleFilterChange('werkstoff', val)}
                                placeholder="Alle Werkstoffe"
                            />
                        </div>
                    </div>
                    <div>
                        <Label htmlFor="filter-produktlinie" className="text-sm font-medium text-gray-700">Produktlinie</Label>
                        <Input id="filter-produktlinie" type="text" className="mt-1" placeholder="Produktlinie" value={filters.produktlinie} onChange={e => handleFilterChange('produktlinie', e.target.value)} />
                    </div>
                    <div className="flex items-end gap-3">
                        <button type="submit" className="btn flex-1 bg-rose-600 text-white px-4 py-2 rounded-lg hover:bg-rose-700">Filtern</button>
                        <button type="button" className="btn-secondary flex-1 px-4 py-2 border rounded-lg hover:bg-slate-50" onClick={handleResetFilters}>Reset</button>
                    </div>
                </form>
                <p className="text-xs text-gray-500 mt-3">Für Performance werden immer nur {PAGE_SIZE} Einträge auf einmal geladen.</p>
            </div>

            {/* Grid Content */}
            {loading ? (
                <div className="text-center py-8 text-slate-500">Artikel werden geladen...</div>
            ) : artikelList.length === 0 ? (
                <div className="bg-white p-8 rounded-2xl text-center text-slate-500 border-dashed border-2">
                    <Package className="w-10 h-10 mx-auto mb-2 text-rose-200" />
                    Keine Artikel gefunden.
                </div>
            ) : (
                <div className="bg-white rounded-2xl shadow-lg overflow-hidden border border-slate-100">
                    <div className="overflow-x-auto">
                        <table className="w-full text-sm text-left">
                            <thead className="bg-slate-50 text-slate-500 font-medium border-b border-slate-200">
                                <tr>
                                    <SortableHeader label="Produktlinie" column="produktlinie" currentSort={sortColumn} direction={sortDirection} onSort={handleSort} />
                                    <SortableHeader label="Name" column="produktname" currentSort={sortColumn} direction={sortDirection} onSort={handleSort} />
                                    <SortableHeader label="Text" column="produkttext" currentSort={sortColumn} direction={sortDirection} onSort={handleSort} />
                                    <SortableHeader label="VPE" column="verpackungseinheit" currentSort={sortColumn} direction={sortDirection} onSort={handleSort} className="text-center" />
                                    <SortableHeader label="Werkstoff" column="werkstoffName" currentSort={sortColumn} direction={sortDirection} onSort={handleSort} />
                                    <th className="px-4 py-3 text-center">Lief.</th>
                                    <th className="px-4 py-3 text-right">kg/m</th>
                                    <SortableHeader label="Ø Preis" column="preis" currentSort={sortColumn} direction={sortDirection} onSort={handleSort} className="text-right" />
                                    <SortableHeader label="Aktualisiert" column="preisDatum" currentSort={sortColumn} direction={sortDirection} onSort={handleSort} className="text-right" />
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-slate-100">
                                {artikelList.map((artikel) => {
                                    const einheit = einheitKuerzel(artikel.verrechnungseinheit);
                                    const anzahl = artikel.anzahlLieferanten ?? artikel.lieferantenpreise?.length ?? 0;
                                    return (
                                        <tr
                                            key={artikel.id}
                                            className="hover:bg-rose-50/50 cursor-pointer transition-colors"
                                            onClick={() => openDetail(artikel)}
                                            role="button"
                                            tabIndex={0}
                                            onKeyDown={(e) => {
                                                if (e.key === 'Enter' || e.key === ' ') {
                                                    e.preventDefault();
                                                    openDetail(artikel);
                                                }
                                            }}
                                            title="Details, Lieferantenpreise und Historie anzeigen"
                                        >
                                            <td className="px-4 py-3 text-slate-600">{artikel.produktlinie || '—'}</td>
                                            <td className="px-4 py-3 font-medium text-slate-900">{artikel.produktname}</td>
                                            <td className="px-4 py-3 text-slate-500 truncate max-w-[220px]" title={artikel.produkttext}>{artikel.produkttext || '—'}</td>
                                            <td className="px-4 py-3 text-center text-slate-600">{artikel.verpackungseinheit || '—'}</td>
                                            <td className="px-4 py-3 text-slate-600">{artikel.werkstoffName || '—'}</td>
                                            <td className="px-4 py-3 text-center">
                                                <span className={cn(
                                                    "inline-flex items-center justify-center min-w-[28px] h-6 px-2 rounded-full text-xs font-semibold",
                                                    anzahl === 0 ? "bg-slate-100 text-slate-500" : "bg-rose-100 text-rose-800"
                                                )}>
                                                    {anzahl}
                                                </span>
                                            </td>
                                            <td className="px-4 py-3 text-right text-slate-600">{formatKg(artikel.kgProMeter)}</td>
                                            <td className="px-4 py-3 text-right">
                                                {artikel.durchschnittspreisNetto !== undefined && artikel.durchschnittspreisNetto !== null ? (
                                                    <span className="inline-flex items-baseline gap-1">
                                                        <TrendingUp className="w-3 h-3 text-rose-500 self-center" />
                                                        <span className="font-semibold text-slate-900">{formatCurrency(artikel.durchschnittspreisNetto)}</span>
                                                        {einheit && <span className="text-xs font-normal text-slate-400">/ {einheit}</span>}
                                                    </span>
                                                ) : (
                                                    <span className="text-slate-400 italic text-xs">noch kein Ø</span>
                                                )}
                                            </td>
                                            <td className="px-4 py-3 text-right text-slate-500 text-xs">{formatDateTime(artikel.durchschnittspreisAktualisiertAm)}</td>
                                        </tr>
                                    );
                                })}
                            </tbody>
                        </table>
                    </div>
                </div>
            )}

            {/* Pagination */}
            <div className="flex flex-col md:flex-row md:items-center justify-between gap-3">
                <p className="text-sm text-gray-600">{statusText}</p>
                <div className="flex gap-2 justify-end">
                    <Button variant="outline" size="sm" disabled={page === 0} onClick={() => setPage(p => Math.max(0, p - 1))}>
                        <ChevronLeft className="w-4 h-4" /> zurück
                    </Button>
                    <Button variant="outline" size="sm" disabled={page >= totalPages - 1} onClick={() => setPage(p => p + 1)}>
                        Weiter <ChevronRight className="w-4 h-4" />
                    </Button>
                </div>
            </div>

            {/* Modals */}
            {showCreateModal && (
                <CreateArticleModal
                    onClose={() => setShowCreateModal(false)}
                    onSave={() => { loadArtikel(); setShowCreateModal(false); }}
                />
            )}
            {detailArtikel && (
                <ArtikelDetailModal
                    artikel={detailArtikel}
                    highlightLieferantName={filters.lieferant || undefined}
                    onClose={() => setDetailArtikel(null)}
                    onEditPrice={openEditFromDetail}
                />
            )}
            {editingArticle && (
                <PriceEditModal
                    artikel={editingArticle}
                    currentSupplier={editSupplierData}
                    onClose={() => { setEditingArticle(null); setEditSupplierData(null); }}
                    onSave={handleSavePrice}
                    onSelectSupplier={() => { setSupplierSelectionMode('edit'); setShowSupplierModal(true); }}
                />
            )}
            {showSupplierModal && (
                <SupplierSelectModal
                    onSelect={handleSupplierSelect}
                    onClose={() => setShowSupplierModal(false)}
                />
            )}
            {showCategoryModal && (
                <CategoryTreeModal
                    onSelect={(id, name) => { handleFilterChange('kategorieId', id); handleFilterChange('kategorieName', name); setShowCategoryModal(false); }}
                    onClose={() => setShowCategoryModal(false)}
                />
            )}
            {showCategoryManagerModal && (
                <CategoryTreeModal
                    mode="manage"
                    onClose={() => setShowCategoryManagerModal(false)}
                />
            )}
            {showVorschlaegeModal && (
                <ArtikelVorschlaegeModal
                    onClose={() => { setShowVorschlaegeModal(false); ladePendingCount(); loadArtikel(); }}
                    onApproved={() => { ladePendingCount(); loadArtikel(); }}
                />
            )}
        </PageLayout>
    );
}

function SortableHeader({ label, column, currentSort, direction, onSort, className }: { label: string, column: string, currentSort: string, direction: string, onSort: (col: string) => void, className?: string }) {
    return (
        <th className={cn("px-4 py-3 cursor-pointer hover:bg-slate-100 transition-colors select-none group", className)} onClick={() => onSort(column)}>
            <div className={cn("flex items-center gap-1", className?.includes("text-right") && "justify-end", className?.includes("text-center") && "justify-center")}>
                {label}
                <span className="text-slate-400 w-4">
                    {currentSort === column && (direction === 'asc' ? '▲' : '▼')}
                </span>
            </div>
        </th>
    );
}

interface PriceEditModalProps {
    artikel: Artikel;
    currentSupplier: { id: number, name: string } | null;
    onClose: () => void;
    onSave: (price: number, num: string) => void;
    onSelectSupplier: () => void;
}

function PriceEditModal({ artikel, currentSupplier, onClose, onSave, onSelectSupplier }: PriceEditModalProps) {
    const [price, setPrice] = useState(artikel.preis || 0);
    const [nummer, setNummer] = useState(artikel.externeArtikelnummer || '');

    return (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
            <div className="bg-white rounded-xl shadow-2xl w-full max-w-sm mx-4" onClick={e => e.stopPropagation()}>
                <div className="p-4 border-b border-slate-100 flex justify-between items-center">
                    <h3 className="font-semibold text-slate-900">Preis / Lieferant bearbeiten</h3>
                    <button onClick={onClose} className="text-slate-400 hover:text-slate-600"><X className="w-5 h-5" /></button>
                </div>
                <div className="p-4 space-y-4">
                    <div>
                        <p className="text-sm font-medium text-slate-900">{artikel.produktname}</p>
                        {currentSupplier ? (
                            <p className="text-xs text-slate-500 flex items-center gap-2">
                                Lieferant: <span className="font-medium text-slate-700">{currentSupplier.name}</span>
                            </p>
                        ) : (
                            <Button variant="outline" size="sm" className="mt-2 w-full" onClick={onSelectSupplier}>
                                <Search className="w-3 h-3 mr-2" /> Lieferant verlinken
                            </Button>
                        )}
                    </div>
                    <div>
                        <Label htmlFor="edit-price">Preis (€)</Label>
                        <Input
                            id="edit-price"
                            type="number"
                            step="0.01"
                            value={price}
                            onChange={e => setPrice(parseFloat(e.target.value))}
                        />
                    </div>
                    <div>
                        <Label htmlFor="edit-nr">Externe Nummer <span className="text-rose-500">*</span></Label>
                        <Input
                            id="edit-nr"
                            value={nummer}
                            onChange={e => setNummer(e.target.value)}
                            placeholder={currentSupplier ? "Erforderlich" : ""}
                            className={currentSupplier && !nummer ? "border-rose-300 focus-visible:ring-rose-500" : ""}
                        />
                        {currentSupplier && !nummer && (
                            <p className="text-xs text-rose-500 mt-1">Externe Nummer ist erforderlich.</p>
                        )}
                    </div>
                </div>
                <div className="p-4 border-t border-slate-100 bg-slate-50 flex justify-end gap-2 rounded-b-xl">
                    <Button variant="ghost" size="sm" onClick={onClose}>Abbrechen</Button>
                    <Button
                        size="sm"
                        className="bg-rose-600 hover:bg-rose-700 text-white"
                        onClick={() => onSave(price, nummer)}
                        disabled={!currentSupplier || !nummer}
                    >
                        <Save className="w-4 h-4 mr-2" /> Speichern
                    </Button>
                </div>
            </div>
        </div>
    );
}