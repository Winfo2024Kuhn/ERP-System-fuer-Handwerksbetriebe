import { useCallback, useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { RefreshCw, Package, Edit2, ChevronLeft, ChevronRight, Save, X, Search, Folder, FolderPlus, Plus } from "lucide-react";
import { Button } from "../components/ui/button";
import { Input } from "../components/ui/input";
import { Label } from "../components/ui/label";
import { Select } from "../components/ui/select-custom";
import { cn } from "../lib/utils";
import type { Artikel } from "../types";
import { SupplierSelectModal } from "../components/SupplierSelectModal";
import { CategoryTreeModal } from "../components/CategoryTreeModal";
import { CreateArticleModal } from "../components/CreateArticleModal";
import { PageLayout } from "../components/layout/PageLayout";
import { useToast } from '../components/ui/toast';

const PAGE_SIZE = 12;

/** Auswahlmoeglichkeit eines technischen Merkmals samt Klartext und Erklaerung. */
interface Merkmalsoption {
    wert: string;
    name: string;
    erklaerung?: string;
}

// Helpers
const formatCurrency = (val?: number) => new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR' }).format(val || 0);
const formatKg = (val?: number) => val ? new Intl.NumberFormat('de-DE', { minimumFractionDigits: 3, maximumFractionDigits: 3 }).format(val) : '';

/**
 * Klartext-Bezeichnung fuer die Liste. Der Produktname enthaelt oft nur das Mass
 * ("0,75 mm") - erst die Form davor sagt dem Anwender, was er vor sich hat:
 * "Blech 0,75 mm". Steht die Form schon im Namen, wird sie nicht doppelt gesetzt.
 */
const artikelBezeichnung = (artikel: Artikel) => {
    const form = artikel.profilform?.anzeigename;
    const mass = artikel.abmessung || artikel.produktname;
    if (!form) return artikel.produktname;
    if (mass && mass.toLowerCase().includes(form.toLowerCase())) return mass;
    return mass ? `${form} ${mass}` : form;
};

/** Verrechnungseinheit kommt je nach Endpunkt als Text oder als Objekt. */
const einheitText = (einheit?: string | { name: string; anzeigename?: string }) => {
    if (!einheit) return '';
    return typeof einheit === 'object' ? (einheit.anzeigename || einheit.name) : einheit;
};

// ==================== MAIN COMPONENT ====================

export default function ArtikelEditor() {
    const toast = useToast();
    const navigate = useNavigate();
    const [artikelList, setArtikelList] = useState<Artikel[]>([]);
    const [loading, setLoading] = useState(false);
    const [total, setTotal] = useState(0);
    const [page, setPage] = useState(0);

    // Edit state
    const [editingArticle, setEditingArticle] = useState<Artikel | null>(null);
    const [editSupplierData, setEditSupplierData] = useState<{ id: number, name: string } | null>(null);

    // Modals state
    const [showSupplierModal, setShowSupplierModal] = useState(false);
    const [showCategoryModal, setShowCategoryModal] = useState(false);
    const [showCategoryManagerModal, setShowCategoryManagerModal] = useState(false);
    const [showCreateModal, setShowCreateModal] = useState(false);
    const [supplierSelectionMode, setSupplierSelectionMode] = useState<'filter' | 'edit'>('filter');

    // Sort state
    const [sortColumn, setSortColumn] = useState('produktname');
    const [sortDirection, setSortDirection] = useState<'asc' | 'desc'>('asc');

    const [filters, setFilters] = useState({
        q: "",
        lieferant: "",
        produktlinie: "",
        werkstoff: "",
        kategorieId: 0,
        kategorieName: "",
        // Technische Merkmale: fuer Anwender greifbarer als die Norm.
        herstellverfahren: "",
        fertigungszustand: "",
        verzinkbar: "",
        pulverbeschichtbar: ""
    });

    const [werkstoffOptions, setWerkstoffOptions] = useState<string[]>([]);
    const [filterOptionen, setFilterOptionen] = useState<{
        herstellverfahren: Merkmalsoption[];
        fertigungszustand: Merkmalsoption[];
    }>({ herstellverfahren: [], fertigungszustand: [] });

    // Fetch initial data (Werkstoffe)
    useEffect(() => {
        fetch('/api/artikel/werkstoffe')
            .then(res => res.json())
            .then(data => setWerkstoffOptions(Array.isArray(data) ? data : []))
            .catch(err => console.error("Fehler beim Laden der Werkstoffe", err));
    }, []);

    // Auswahlmoeglichkeiten fuer Herstellverfahren und Zustand samt Klartext.
    useEffect(() => {
        fetch('/api/artikel/filteroptionen')
            .then(res => res.json())
            .then(data => setFilterOptionen({
                herstellverfahren: Array.isArray(data?.herstellverfahren) ? data.herstellverfahren : [],
                fertigungszustand: Array.isArray(data?.fertigungszustand) ? data.fertigungszustand : [],
            }))
            .catch(err => console.error("Fehler beim Laden der Filteroptionen", err));
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
            if (filters.herstellverfahren) params.set("herstellverfahren", filters.herstellverfahren);
            if (filters.fertigungszustand) params.set("fertigungszustand", filters.fertigungszustand);
            if (filters.verzinkbar) params.set("verzinkbar", filters.verzinkbar);
            if (filters.pulverbeschichtbar) params.set("pulverbeschichtbar", filters.pulverbeschichtbar);

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

    const handleFilterChange = (key: string, value: string | number) => {
        setFilters((prev) => ({ ...prev, [key]: value }));
    };

    const handleFilterSubmit = (e: React.FormEvent) => {
        e.preventDefault();
        setPage(0);
        loadArtikel();
    };

    const handleResetFilters = () => {
        setFilters({
            q: "", lieferant: "", produktlinie: "", werkstoff: "", kategorieId: 0, kategorieName: "",
            herstellverfahren: "", fertigungszustand: "", verzinkbar: "", pulverbeschichtbar: ""
        });
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

    const openEdit = (artikel: Artikel) => {
        setEditingArticle(artikel);
        setEditSupplierData(artikel.lieferantId ? { id: artikel.lieferantId, name: artikel.lieferantenname || '' } : null);
    };

    const handleSavePrice = async (price: number, exNummer: string) => {
        if (!editingArticle) return;

        // Determine if update or create
        // If we have a supplierId in editSupplierData, use it.
        if (!editSupplierData) {
            toast.warning("Bitte wählen Sie einen Lieferanten aus.");
            return;
        }

        try {
            const isNewLink = !editingArticle.lieferantId; // Or strictly checking if we are linking a new one
            // However, API logic:
            // Update: PUT /api/lieferanten/{supplierId}/artikelpreise/{articleId}
            // Create: POST /api/lieferanten/{supplierId}/artikelpreise body {artikelId, ...}

            let res;
            if (isNewLink) {
                // Create new price link
                res = await fetch(`/api/lieferanten/${editSupplierData.id}/artikelpreise`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        artikelId: editingArticle.id,
                        preis: price,
                        externeArtikelnummer: exNummer
                    })
                });
            } else {
                // Update existing - assume supplier didn't change for now or we use the original ID?
                // If the user changed the supplier (which we might allow), we would need POST if the new supplier doesn't have a price yet.
                // For simplicity and per requirement "link if not happened", we assume if it happened, we update that one.
                // But we use editSupplierData.id which is the current one.

                // If editingArticle.lieferantId != editSupplierData.id, we are effectively creating a new price for a new supplier.
                const supplierChanged = editingArticle.lieferantId !== editSupplierData.id;

                if (supplierChanged) {
                    res = await fetch(`/api/lieferanten/${editSupplierData.id}/artikelpreise`, {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({
                            artikelId: editingArticle.id,
                            preis: price,
                            externeArtikelnummer: exNummer
                        })
                    });
                } else {
                    res = await fetch(`/api/lieferanten/${editSupplierData.id}/artikelpreise/${editingArticle.id}`, {
                        method: 'PUT',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ preis: price, externeArtikelnummer: exNummer })
                    });
                }
            }

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

                    {/* Technische Merkmale: "geschweißt, kaltgefertigt" sagt mehr als "EN 10219-2". */}
                    <div>
                        <Label htmlFor="filter-verfahren" className="text-sm font-medium text-gray-700">Herstellung</Label>
                        <div className="mt-1">
                            <Select
                                options={[{ value: "", label: "Egal" },
                                    ...filterOptionen.herstellverfahren.map(o => ({ value: o.wert, label: o.name }))]}
                                value={filters.herstellverfahren}
                                onChange={val => handleFilterChange('herstellverfahren', val)}
                                placeholder="Egal"
                            />
                        </div>
                    </div>
                    <div>
                        <Label htmlFor="filter-zustand" className="text-sm font-medium text-gray-700">Zustand</Label>
                        <div className="mt-1">
                            <Select
                                options={[{ value: "", label: "Egal" },
                                    ...filterOptionen.fertigungszustand.map(o => ({ value: o.wert, label: o.name }))]}
                                value={filters.fertigungszustand}
                                onChange={val => handleFilterChange('fertigungszustand', val)}
                                placeholder="Egal"
                            />
                        </div>
                    </div>
                    <div>
                        <Label className="text-sm font-medium text-gray-700">Oberfläche</Label>
                        <div className="mt-1 flex flex-wrap gap-2">
                            <button
                                type="button"
                                aria-pressed={filters.verzinkbar === 'true'}
                                onClick={() => handleFilterChange('verzinkbar', filters.verzinkbar === 'true' ? '' : 'true')}
                                className={cn(
                                    "px-3 py-2 min-h-[40px] rounded-lg border text-sm transition-colors",
                                    filters.verzinkbar === 'true'
                                        ? "bg-rose-600 text-white border-rose-600"
                                        : "border-slate-300 text-slate-700 hover:bg-slate-50"
                                )}
                            >
                                verzinkbar
                            </button>
                            <button
                                type="button"
                                aria-pressed={filters.pulverbeschichtbar === 'true'}
                                onClick={() => handleFilterChange('pulverbeschichtbar', filters.pulverbeschichtbar === 'true' ? '' : 'true')}
                                className={cn(
                                    "px-3 py-2 min-h-[40px] rounded-lg border text-sm transition-colors",
                                    filters.pulverbeschichtbar === 'true'
                                        ? "bg-rose-600 text-white border-rose-600"
                                        : "border-slate-300 text-slate-700 hover:bg-slate-50"
                                )}
                            >
                                pulverbeschichtbar
                            </button>
                        </div>
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
                                    <SortableHeader label="Artikel-Nr." column="produktname" currentSort={sortColumn} direction={sortDirection} onSort={handleSort} />
                                    <SortableHeader label="Bezeichnung" column="produktname" currentSort={sortColumn} direction={sortDirection} onSort={handleSort} />
                                    <SortableHeader label="Werkstoff" column="werkstoffName" currentSort={sortColumn} direction={sortDirection} onSort={handleSort} />
                                    <th className="px-4 py-3">Ausführung</th>
                                    <SortableHeader label="kg/m" column="kgProMeter" currentSort={sortColumn} direction={sortDirection} onSort={handleSort} className="text-right" />
                                    <SortableHeader label="Bester Preis" column="preis" currentSort={sortColumn} direction={sortDirection} onSort={handleSort} className="text-right" />
                                    <th className="px-4 py-3">Günstigster Anbieter</th>
                                    <th className="px-4 py-3 text-center w-12"></th>
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-slate-100">
                                {artikelList.map((artikel) => (
                                    <tr
                                        key={artikel.id}
                                        className="hover:bg-slate-50 transition-colors cursor-pointer"
                                        onClick={() => navigate(`/artikel/${artikel.id}`)}
                                        tabIndex={0}
                                        role="link"
                                        aria-label={`Details zu ${artikelBezeichnung(artikel)} öffnen`}
                                        onKeyDown={(e) => {
                                            if (e.key === 'Enter' || e.key === ' ') {
                                                e.preventDefault();
                                                navigate(`/artikel/${artikel.id}`);
                                            }
                                        }}
                                    >
                                        <td className="px-4 py-3 font-mono text-xs text-slate-600">{artikel.artikelnummer || '-'}</td>
                                        <td className="px-4 py-3">
                                            <div
                                                className="font-medium text-slate-900"
                                                title={artikel.profilform?.erklaerung}
                                            >
                                                {artikelBezeichnung(artikel)}
                                            </div>
                                            {artikel.massnorm && (
                                                <div className="text-xs text-slate-400">{artikel.massnorm}</div>
                                            )}
                                        </td>
                                        <td className="px-4 py-3 text-slate-600">{artikel.werkstoffName || '-'}</td>
                                        <td className="px-4 py-3">
                                            {/* Klartext statt Normnummer - der Anwender soll sehen, was er bekommt. */}
                                            <div className="flex flex-wrap gap-1">
                                                {artikel.herstellverfahren && (
                                                    <span title={artikel.herstellverfahren.erklaerung}
                                                        className="inline-block rounded bg-slate-100 text-slate-700 text-xs px-1.5 py-0.5">
                                                        {artikel.herstellverfahren.anzeigename}
                                                    </span>
                                                )}
                                                {artikel.fertigungszustand && (
                                                    <span title={artikel.fertigungszustand.erklaerung}
                                                        className="inline-block rounded bg-slate-100 text-slate-700 text-xs px-1.5 py-0.5">
                                                        {artikel.fertigungszustand.anzeigename}
                                                    </span>
                                                )}
                                                {!artikel.herstellverfahren && !artikel.fertigungszustand && (
                                                    <span className="text-slate-400">-</span>
                                                )}
                                            </div>
                                        </td>
                                        <td className="px-4 py-3 text-right text-slate-600 tabular-nums">{formatKg(artikel.kgProMeter)}</td>
                                        <td className="px-4 py-3 text-right font-medium text-slate-900 tabular-nums">
                                            {artikel.guenstigsterPreis !== undefined && artikel.guenstigsterPreis !== null ? (
                                                <>
                                                    {formatCurrency(artikel.guenstigsterPreis)}
                                                    <span className="text-xs font-normal text-slate-400"> / {einheitText(artikel.verrechnungseinheit)}</span>
                                                </>
                                            ) : (
                                                <span className="text-slate-400 font-normal">kein Preis</span>
                                            )}
                                        </td>
                                        <td className="px-4 py-3 text-slate-600">
                                            {artikel.guenstigsterLieferantName ? (
                                                <>
                                                    <div>{artikel.guenstigsterLieferantName}</div>
                                                    {(artikel.anzahlLieferanten ?? 0) > 1 && (
                                                        <div className="text-xs text-slate-400">
                                                            +{(artikel.anzahlLieferanten ?? 1) - 1} weitere{(artikel.anzahlLieferanten ?? 1) - 1 === 1 ? 'r' : ''}
                                                        </div>
                                                    )}
                                                </>
                                            ) : '-'}
                                        </td>
                                        <td className="px-4 py-3 text-center">
                                            <button
                                                onClick={(e) => { e.stopPropagation(); openEdit(artikel); }}
                                                className="p-2 text-slate-400 hover:text-rose-600 hover:bg-rose-50 rounded transition-colors"
                                                title="Preis bearbeiten"
                                                aria-label={`Preis von ${artikelBezeichnung(artikel)} bearbeiten`}
                                            >
                                                <Edit2 className="w-4 h-4" />
                                            </button>
                                        </td>
                                    </tr>
                                ))}
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
                    kategorieId={supplierSelectionMode === 'edit' ? editingArticle?.kategorieId : undefined}
                    kategorieName={supplierSelectionMode === 'edit' ? editingArticle?.kategoriePfad : undefined}
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