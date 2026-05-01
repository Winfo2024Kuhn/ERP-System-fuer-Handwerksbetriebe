import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { Plus, RefreshCw, X, Mail, Phone, MapPin, Building2, User, ArrowLeft, Edit2, ChevronLeft, ChevronRight, FileText, StickyNote, AlertTriangle, Package } from "lucide-react";
import { Button } from "../components/ui/button";
import { Card } from "../components/ui/card";
import { Input } from "../components/ui/input";
import { Label } from "../components/ui/label";
import { cn } from "../lib/utils";
import type { Lieferant, LieferantDetail } from "../types";
import { EmailsTab } from "../components/EmailsTab";
import GoogleMapsEmbed from "../components/GoogleMapsEmbed";
import LieferantDokumenteTab from "../components/LieferantDokumenteTab";
import { LieferantNotizenTab } from "../components/LieferantNotizenTab";

import { LieferantReklamationenTab } from "../components/LieferantReklamationenTab";
import { DetailLayout } from "../components/DetailLayout";
import { PageLayout } from "../components/layout/PageLayout";
import { Select } from "../components/ui/select-custom";
import { useToast } from '../components/ui/toast';
import { KostenstelleSelectModal } from "../components/KostenstelleSelectModal";
import { AddressAutocomplete } from "../components/AddressAutocomplete";
import { PhoneInput } from "../components/PhoneInput";

const LIEFERANT_TYPES = [
    { value: "STAHL", label: "Stahl" },
    { value: "ALUMINIUM", label: "Aluminium" },
    { value: "LACKIERER", label: "Lackierer" },
    { value: "SONSTIGER", label: "Sonstiger" },
];

const PAGE_SIZE = 12;

// ==================== DETAIL VIEW ====================

interface LieferantDetailViewProps {
    lieferant: LieferantDetail;
    onBack: () => void;
    onEdit: () => void;
}

const LieferantDetailView: React.FC<LieferantDetailViewProps> = ({ lieferant, onBack, onEdit }) => {
    const initials = lieferant.lieferantenname?.slice(0, 2).toUpperCase() || "??";

    // Formatter helpers
    const formatCurrency = (val?: number) => new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR' }).format(val || 0);

    const header = (
        <Card className="p-6">
            <div className="flex flex-col xl:flex-row gap-8 justify-between">
                <div className="flex items-start gap-4">
                    <Button variant="ghost" size="sm" onClick={onBack} className="-ml-2 h-auto py-1 self-start">
                        <ArrowLeft className="w-5 h-5" />
                    </Button>
                    <div className="w-16 h-16 rounded-full bg-rose-100 text-rose-600 flex items-center justify-center text-xl font-bold shrink-0">
                        {initials}
                    </div>
                    <div>
                        <div className="flex items-center gap-3">
                            <h1 className="text-2xl font-bold text-slate-900">{lieferant.lieferantenname}</h1>
                            <span className="px-2.5 py-0.5 rounded-full bg-slate-100 text-slate-600 text-xs font-medium border border-slate-200">
                                {lieferant.lieferantenTyp || "Lieferant"}
                            </span>
                        </div>
                        <div className="mt-1 text-slate-500 space-y-0.5">
                            {lieferant.vertreter && <p className="flex items-center gap-2"><User className="w-4 h-4" /> {lieferant.vertreter}</p>}
                            <p className="flex items-center gap-2"><MapPin className="w-4 h-4" /> {lieferant.strasse}, {lieferant.plz} {lieferant.ort}</p>
                        </div>
                    </div>
                </div>

                {/* Bento Stats Grid */}
                <div className="grid grid-cols-2 md:grid-cols-4 gap-4 flex-1 max-w-4xl">
                    <div className="bg-slate-50 p-3 rounded-xl border border-slate-100">
                        <p className="text-xs text-slate-500 uppercase tracking-wide">Gesamtkosten</p>
                        <p className="text-lg font-semibold text-slate-900">{formatCurrency(lieferant.statistik?.gesamtKosten)}</p>
                    </div>
                    <div className="bg-purple-50 p-3 rounded-xl border border-purple-100">
                        <p className="text-xs text-purple-600 uppercase tracking-wide">Bestellungen</p>
                        <p className="text-lg font-semibold text-purple-900">{lieferant.statistik?.bestellungAnzahl || 0}</p>
                    </div>
                    <div className="bg-blue-50 p-3 rounded-xl border border-blue-100">
                        <p className="text-xs text-blue-600 uppercase tracking-wide">Artikel</p>
                        <p className="text-lg font-semibold text-blue-900">{lieferant.statistik?.artikelAnzahl || 0}</p>
                    </div>
                    <div className="bg-emerald-50 p-3 rounded-xl border border-emerald-100">
                        <p className="text-xs text-emerald-600 uppercase tracking-wide">Lieferzeit Ø</p>
                        <p className="text-lg font-semibold text-emerald-900">{lieferant.statistik?.lieferzeit || lieferant.lieferzeit || 0} Tage</p>
                    </div>
                </div>

                <div className="flex items-start">
                    <Button variant="outline" onClick={onEdit}>
                        <Edit2 className="w-4 h-4 mr-2" /> Bearbeiten
                    </Button>
                </div>
            </div>
        </Card>
    );

    // Tab State
    const [activeTab, setActiveTab] = useState<'emails' | 'dokumente' | 'notizen' | 'reklamationen'>('emails');

    const mainContent = (
        <>
            {/* Tab Navigation */}
            <div className="flex items-center gap-1 mb-6 border-b border-slate-200">
                <button
                    onClick={() => setActiveTab('emails')}
                    className={cn(
                        "flex items-center gap-2 px-4 py-2.5 text-sm font-medium transition-colors -mb-px",
                        activeTab === 'emails'
                            ? "text-rose-600 border-b-2 border-rose-500"
                            : "text-slate-500 hover:text-slate-700"
                    )}
                >
                    <Mail className="w-4 h-4" />
                    E-Mail-Verlauf
                    <span className="text-xs bg-slate-100 text-slate-600 px-1.5 py-0.5 rounded-full">
                        {lieferant.kommunikation?.length || 0}
                    </span>
                </button>
                <button
                    onClick={() => setActiveTab('dokumente')}
                    className={cn(
                        "flex items-center gap-2 px-4 py-2.5 text-sm font-medium transition-colors -mb-px",
                        activeTab === 'dokumente'
                            ? "text-rose-600 border-b-2 border-rose-500"
                            : "text-slate-500 hover:text-slate-700"
                    )}
                >
                    <FileText className="w-4 h-4" />
                    Dokumente
                    <span className="text-xs bg-slate-100 text-slate-600 px-1.5 py-0.5 rounded-full">
                        {lieferant.dokumente?.length || 0}
                    </span>
                </button>
                <button
                    onClick={() => setActiveTab('notizen')}
                    className={cn(
                        "flex items-center gap-2 px-4 py-2.5 text-sm font-medium transition-colors -mb-px",
                        activeTab === 'notizen'
                            ? "text-rose-600 border-b-2 border-rose-500"
                            : "text-slate-500 hover:text-slate-700"
                    )}
                >
                    <StickyNote className="w-4 h-4" />
                    Notizen
                    <span className="text-xs bg-slate-100 text-slate-600 px-1.5 py-0.5 rounded-full">
                        {lieferant.notizen?.length || 0}
                    </span>
                </button>

                <button
                    onClick={() => setActiveTab('reklamationen')}
                    className={cn(
                        "flex items-center gap-2 px-4 py-2.5 text-sm font-medium transition-colors -mb-px",
                        activeTab === 'reklamationen'
                            ? "text-rose-600 border-b-2 border-rose-500"
                            : "text-slate-500 hover:text-slate-700"
                    )}
                >
                    <AlertTriangle className="w-4 h-4" />
                    Reklamationen
                </button>
            </div>

            {/* Tab Content */}
            <div className="flex-1 min-h-0 relative">
                {activeTab === 'emails' && (
                    <div className="absolute inset-0 overflow-y-auto pr-2">
                        <EmailsTab
                            emails={lieferant.emails || []}
                            lieferantId={lieferant.id as number}
                            entityName={lieferant.lieferantenname}
                            kundenEmail={lieferant.kundenEmails?.[0]}
                            showComposeButton={false}
                            showReplyButton={false}
                        />
                    </div>
                )}
                {activeTab === 'dokumente' && (
                    <div className="absolute inset-0 overflow-y-auto pr-2">
                        <LieferantDokumenteTab
                            lieferantId={lieferant.id}
                            lieferantName={lieferant.lieferantenname}
                            dokumente={lieferant.dokumente}
                        />
                    </div>
                )}
                {activeTab === 'notizen' && (
                    <div className="absolute inset-0 overflow-y-auto pr-2">
                        <LieferantNotizenTab
                            lieferantId={lieferant.id as number}
                            notizen={lieferant.notizen || []}
                        />
                    </div>
                )}

                {activeTab === 'reklamationen' && (
                    <div className="absolute inset-0 overflow-y-auto pr-2">
                        <LieferantReklamationenTab
                            lieferantId={lieferant.id as number}
                        />
                    </div>
                )}
            </div>
        </>
    );


    const sideContent = (
        <>
            <h2 className="text-lg font-semibold text-slate-900 mb-4 flex items-center gap-2">
                <Phone className="w-5 h-5 text-rose-500" />
                Kontaktdaten
            </h2>
            <div className="space-y-4">
                <div className="p-3 bg-slate-50 rounded-lg flex items-center gap-3">
                    <div className="p-2 bg-white rounded-md shadow-sm text-slate-400">
                        <Phone className="w-4 h-4" />
                    </div>
                    <div>
                        <p className="text-xs text-slate-500">Telefon</p>
                        <p className="font-medium text-slate-900">{lieferant.telefon || '-'}</p>
                    </div>
                </div>
                <div className="p-3 bg-slate-50 rounded-lg flex items-center gap-3">
                    <div className="p-2 bg-white rounded-md shadow-sm text-slate-400">
                        <Building2 className="w-4 h-4" />
                    </div>
                    <div>
                        <p className="text-xs text-slate-500">Mobil / Fax</p>
                        <p className="font-medium text-slate-900">{lieferant.mobiltelefon || '-'}</p>
                    </div>
                </div>
                <div className="p-3 bg-slate-50 rounded-lg flex items-center gap-3">
                    <div className="p-2 bg-white rounded-md shadow-sm text-slate-400">
                        <Mail className="w-4 h-4" />
                    </div>
                    <div className="min-w-0 flex-1">
                        <p className="text-xs text-slate-500">E-Mail</p>
                        <div className="flex flex-col">
                            {lieferant.kundenEmails && lieferant.kundenEmails.length > 0 ? (
                                lieferant.kundenEmails.map(email => (
                                    <a key={email} href={`mailto:${email}`} className="font-medium text-rose-600 hover:underline truncate block">{email}</a>
                                ))
                            ) : (
                                <span className="text-slate-400">-</span>
                            )}
                        </div>
                    </div>
                </div>
                <div className="p-3 bg-slate-50 rounded-lg flex items-center gap-3">
                    <div className="p-2 bg-white rounded-md shadow-sm text-slate-400">
                        <User className="w-4 h-4" />
                    </div>
                    <div>
                        <p className="text-xs text-slate-500">Vertreter</p>
                        <p className="font-medium text-slate-900">{lieferant.vertreter || '-'}</p>
                    </div>
                </div>
                <div className="p-3 bg-slate-50 rounded-lg flex items-center gap-3">
                    <div className="p-2 bg-white rounded-md shadow-sm text-slate-400">
                        <Package className="w-4 h-4" />
                    </div>
                    <div>
                        <p className="text-xs text-slate-500">Standard-Kostenstelle</p>
                        <p className="font-medium text-slate-900">
                            {lieferant.standardKostenstelleName || <span className="text-slate-400">Keine zugewiesen</span>}
                        </p>
                    </div>
                </div>
            </div>

            <div className="mt-8 pt-6 border-t border-slate-100 flex-1 flex flex-col min-h-0">
                <h3 className="text-sm font-medium text-slate-900 mb-3 flex items-center gap-2 shrink-0">
                    <MapPin className="w-4 h-4 text-slate-400" /> Standort
                </h3>
                <div className="flex-1 min-h-[200px] rounded-lg overflow-hidden border border-slate-200">
                    <GoogleMapsEmbed
                        strasse={lieferant.strasse}
                        plz={lieferant.plz}
                        ort={lieferant.ort}
                        className="w-full h-full"
                    />
                </div>
            </div>
        </>
    );

    return (
        <DetailLayout
            header={header}
            mainContent={mainContent}
            sideContent={sideContent}
        />
    );
};

// ==================== MAIN COMPONENT ====================

export default function LieferantenEditor() {
    const toast = useToast();
    const [searchParams, setSearchParams] = useSearchParams();
    const [viewMode, setViewMode] = useState<'list' | 'detail'>('list');
    const [lieferanten, setLieferanten] = useState<Lieferant[]>([]);
    const [selectedLieferant, setSelectedLieferant] = useState<LieferantDetail | null>(null);
    const [loading, setLoading] = useState(false);
    const [total, setTotal] = useState(0);
    const [page, setPage] = useState(0);

    // Filters
    const [filters, setFilters] = useState({
        q: "",
        name: "",
        typ: "",
        ort: "",
        email: "",
    });

    const [editingLieferant, setEditingLieferant] = useState<Lieferant | null>(null);
    const [isModalOpen, setIsModalOpen] = useState(false);

    // Deep-link: restore detail view from URL param ?lieferantId=123
    const lastProcessedLieferantId = useRef<string | null>(null);
    useEffect(() => {
        const lieferantIdParam = searchParams.get('lieferantId');
        if (!lieferantIdParam) return;
        if (lastProcessedLieferantId.current === lieferantIdParam) return;
        const lieferantId = Number(lieferantIdParam);
        if (isNaN(lieferantId) || !lieferantId) return;
        lastProcessedLieferantId.current = lieferantIdParam;
        (async () => {
            try {
                setLoading(true);
                const res = await fetch(`/api/lieferanten/${lieferantId}`);
                if (!res.ok) throw new Error('Fehler beim Laden der Details');
                const data: LieferantDetail = await res.json();
                setSelectedLieferant(data);
                setViewMode('detail');
            } catch (err) {
                console.error('Deep-link load error', err);
            } finally {
                setLoading(false);
            }
        })();
    }, [searchParams]);

    // Fetch List
    const loadLieferanten = useCallback(async () => {
        setLoading(true);
        try {
            const params = new URLSearchParams();
            params.set("page", String(page));
            params.set("size", String(PAGE_SIZE));
            if (filters.q) params.set("q", filters.q);
            if (filters.name) params.set("name", filters.name);
            if (filters.typ) params.set("typ", filters.typ);
            if (filters.ort) params.set("ort", filters.ort);
            if (filters.email) params.set("email", filters.email);

            const res = await fetch(`/api/lieferanten?${params.toString()}`);
            if (!res.ok) throw new Error("Fehler beim Laden");
            const data = await res.json();

            setLieferanten(Array.isArray(data.lieferanten) ? data.lieferanten : []);
            setTotal(typeof data.gesamt === "number" ? data.gesamt : 0);
        } catch (err) {
            console.error(err);
            setLieferanten([]);
            setTotal(0);
        } finally {
            setLoading(false);
        }
    }, [page, filters]);

    useEffect(() => {
        if (viewMode === 'list') {
            loadLieferanten();
        }
    }, [loadLieferanten, viewMode]);

    // Handlers
    const handleFilterChange = (key: string, value: string) => {
        setFilters((prev) => ({ ...prev, [key]: value }));
    };

    const handleFilterSubmit = (e: React.FormEvent) => {
        e.preventDefault();
        setPage(0);
        loadLieferanten();
    };

    const handleResetFilters = () => {
        setFilters({ q: "", name: "", typ: "", ort: "", email: "" });
        setPage(0);
    };

    const handleCreate = () => {
        setEditingLieferant({
            id: "",
            lieferantenTyp: "",
            lieferantenname: "",
            eigeneKundennummer: "",
            kundenEmails: []
        });
        setIsModalOpen(true);
    };

    const handleEdit = (lieferant: Lieferant) => {
        setEditingLieferant({ ...lieferant });
        setIsModalOpen(true);
    };

    const handleDetail = async (lieferant: Lieferant) => {
        try {
            setLoading(true);
            const res = await fetch(`/api/lieferanten/${lieferant.id}`);
            if (!res.ok) throw new Error("Fehler beim Laden der Details");
            const data: LieferantDetail = await res.json();

            setSelectedLieferant(data);
            setViewMode('detail');
            setSearchParams({ lieferantId: String(data.id) }, { replace: true });
        } catch (err) {
            console.error("Detail load error", err);
            setSelectedLieferant(lieferant as LieferantDetail);
            setViewMode('detail');
            setSearchParams({ lieferantId: String(lieferant.id) }, { replace: true });
        } finally {
            setLoading(false);
        }
    };

    const handleSave = async (data: Lieferant) => {
        try {
            const isEdit = !!data.id;
            const url = isEdit ? `/api/lieferanten/${data.id}` : "/api/lieferanten";
            const method = isEdit ? "PUT" : "POST";

            const res = await fetch(url, {
                method,
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(data),
            });

            if (!res.ok) {
                const errData = await res.json();
                toast.error(errData.message || "Speichern fehlgeschlagen");
                return;
            }

            setIsModalOpen(false);
            setEditingLieferant(null);

            if (viewMode === 'detail' && selectedLieferant?.id === data.id) {
                handleDetail(data);
            } else {
                loadLieferanten();
            }
        } catch (err) {
            console.error(err);
            toast.error("Ein Fehler ist aufgetreten.");
        }
    };

    const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE));

    const statusText = useMemo(() => {
        if (loading) return 'Lieferanten werden geladen...';
        if (total === 0) return 'Keine Lieferanten gefunden.';
        const start = page * PAGE_SIZE + 1;
        const end = Math.min(start + lieferanten.length - 1, total);
        return `Zeige ${start}-${end} von ${total} Lieferanten`;
    }, [loading, total, page, lieferanten.length]);

    if (viewMode === 'detail' && selectedLieferant) {
        return (
            <PageLayout
                ribbonCategory="Stammdaten"
                title="LIEFERANTENDETAILS"
                subtitle={selectedLieferant.lieferantenname}
                actions={
                    <Button variant="outline" size="sm" onClick={() => {
                        setSelectedLieferant(null);
                        setViewMode('list');
                        setSearchParams({}, { replace: true });
                    }}>
                        <ArrowLeft className="w-4 h-4 mr-2" /> Zurück
                    </Button>
                }
            >
                <LieferantDetailView
                    lieferant={selectedLieferant}
                    onBack={() => {
                        setSelectedLieferant(null);
                        setViewMode('list');
                        setSearchParams({}, { replace: true });
                    }}
                    onEdit={() => handleEdit(selectedLieferant)}
                />
                {isModalOpen && editingLieferant && (
                    <LieferantModal
                        lieferant={editingLieferant}
                        onClose={() => setIsModalOpen(false)}
                        onSave={handleSave}
                    />
                )}
            </PageLayout>
        );
    }

    return (
        <PageLayout
            ribbonCategory="Stammdaten"
            title="LIEFERANTENÜBERSICHT"
            subtitle="Übersicht und Verwaltung Ihrer Lieferanten."
            actions={
                <>
                    <Button size="sm" onClick={handleCreate} className="bg-rose-600 hover:bg-rose-700 text-white">
                        <Plus className="w-4 h-4 mr-2" />
                        Neuer Lieferant
                    </Button>
                    <Button variant="outline" size="sm" onClick={() => loadLieferanten()}>
                        <RefreshCw className={cn("w-4 h-4 mr-2", loading && "animate-spin")} />
                        Aktualisieren
                    </Button>
                </>
            }
        >
            {/* Filter - volle Breite */}
            <div className="bg-white p-6 rounded-2xl shadow-lg border border-slate-100">
                <form onSubmit={handleFilterSubmit} className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-5 gap-4">
                    <div>
                        <label className="block text-sm font-medium text-gray-700">Freitext</label>
                        <input type="text" className="filter-input w-full mt-1 px-3 py-2 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-rose-500" placeholder="Name, Ort..." value={filters.q} onChange={e => handleFilterChange('q', e.target.value)} />
                    </div>
                    <div>
                        <label className="block text-sm font-medium text-gray-700">Name</label>
                        <input type="text" className="filter-input w-full mt-1 px-3 py-2 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-rose-500" placeholder="Lieferantenname" value={filters.name} onChange={e => handleFilterChange('name', e.target.value)} />
                    </div>
                    <div>
                        <label className="block text-sm font-medium text-gray-700">Typ</label>
                        <Select
                            value={filters.typ}
                            onChange={(value) => handleFilterChange("typ", value)}
                            options={[
                                { value: "", label: "Alle Typen" },
                                ...LIEFERANT_TYPES
                            ]}
                            placeholder="Typ filtern"
                            className="mt-1"
                        />
                    </div>
                    <div>
                        <label className="block text-sm font-medium text-gray-700">Ort</label>
                        <input type="text" className="filter-input w-full mt-1 px-3 py-2 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-rose-500" placeholder="Ort" value={filters.ort} onChange={e => handleFilterChange('ort', e.target.value)} />
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
                <div className="text-center py-8 text-slate-500">Lieferanten werden geladen...</div>
            ) : lieferanten.length === 0 ? (
                <div className="bg-white p-8 rounded-2xl text-center text-slate-500 border-dashed border-2">
                    <Building2 className="w-10 h-10 mx-auto mb-2 text-rose-200" />
                    Keine Lieferanten gefunden.
                </div>
            ) : (
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
                    {lieferanten.map((lieferant) => (
                        <LieferantCard
                            key={lieferant.id}
                            lieferant={lieferant}
                            onClick={() => handleDetail(lieferant)}
                            onEdit={(e) => {
                                e.stopPropagation();
                                handleEdit(lieferant);
                            }}
                        />
                    ))}
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

            {/* Create/Edit Modal */}
            {isModalOpen && editingLieferant && (
                <LieferantModal
                    lieferant={editingLieferant}
                    onClose={() => setIsModalOpen(false)}
                    onSave={handleSave}
                />
            )}
        </PageLayout>
    );
}

function LieferantCard({ lieferant, onClick, onEdit }: { lieferant: Lieferant; onClick: () => void; onEdit: (e: React.MouseEvent) => void }) {
    const adresse = [lieferant.strasse, [lieferant.plz, lieferant.ort].filter(Boolean).join(" ")].filter(Boolean).join(", ");
    const kontakt = [lieferant.telefon, lieferant.mobiltelefon].filter(Boolean).join(" / ");

    return (
        <Card
            className="group relative cursor-pointer hover:shadow-md transition-all border-slate-200 bg-white overflow-hidden"
            onClick={onClick}
        >
            <div className="p-4 space-y-3">
                <div>
                    <span className="text-xs font-semibold tracking-wider text-rose-600 uppercase bg-rose-50 px-2 py-0.5 rounded-full">
                        {lieferant.lieferantenTyp || "Ohne Typ"}
                    </span>
                    <h3 className="font-semibold text-slate-900 mt-2 truncate text-base" title={lieferant.lieferantenname}>
                        {lieferant.lieferantenname || "Unbenannt"}
                    </h3>
                    <p className="text-sm text-slate-500 truncate">{lieferant.ort || "Kein Ort"}</p>
                </div>

                <div className="space-y-1 pt-2 border-t border-slate-50">
                    <div className="flex items-start gap-2 text-sm text-slate-600">
                        <MapPin className="w-4 h-4 mt-0.5 text-slate-400 shrink-0" />
                        <span className="line-clamp-2">{adresse || "-"}</span>
                    </div>
                    {(lieferant.telefon || lieferant.mobiltelefon) && (
                        <div className="flex items-center gap-2 text-sm text-slate-600">
                            <Phone className="w-4 h-4 text-slate-400 shrink-0" />
                            <span className="truncate">{kontakt}</span>
                        </div>
                    )}
                    {lieferant.vertreter && (
                        <div className="flex items-center gap-2 text-sm text-slate-600">
                            <User className="w-4 h-4 text-slate-400 shrink-0" />
                            <span className="truncate">{lieferant.vertreter}</span>
                        </div>
                    )}
                </div>
            </div>

            <button
                onClick={onEdit}
                className="absolute top-3 right-3 p-1.5 rounded-full bg-white/90 text-slate-400 opacity-0 group-hover:opacity-100 hover:text-rose-600 hover:bg-rose-50 transition-all shadow-sm border border-transparent hover:border-rose-100"
                title="Bearbeiten"
            >
                <Edit2 className="w-4 h-4" />
            </button>
        </Card>
    );
}

function LieferantModal({ lieferant, onClose, onSave }: { lieferant: Lieferant; onClose: () => void; onSave: (l: Lieferant) => void }) {
    const [formData, setFormData] = useState<Lieferant>({ ...lieferant });
    const [newEmail, setNewEmail] = useState("");
    const [showKostenstelleModal, setShowKostenstelleModal] = useState(false);

    const handleChange = (field: keyof Lieferant, value: Lieferant[keyof Lieferant]) => {
        setFormData(prev => ({ ...prev, [field]: value }));
    };

    const addEmail = () => {
        if (!newEmail.trim()) return;
        const current = formData.kundenEmails || [];
        if (current.includes(newEmail.trim())) {
            setNewEmail("");
            return;
        }
        setFormData(prev => ({ ...prev, kundenEmails: [...current, newEmail.trim()] }));
        setNewEmail("");
    };

    const removeEmail = (email: string) => {
        setFormData(prev => ({
            ...prev,
            kundenEmails: (prev.kundenEmails || []).filter(e => e !== email)
        }));
    };

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4 animate-in fade-in duration-200">
            <div className="bg-white rounded-xl shadow-2xl w-full max-w-2xl max-h-[90vh] overflow-y-auto flex flex-col" onClick={e => e.stopPropagation()}>
                <div className="flex items-center justify-between p-6 border-b border-slate-100">
                    <h3 className="text-xl font-semibold text-slate-900">
                        {lieferant.id ? "Lieferant bearbeiten" : "Neuer Lieferant"}
                    </h3>
                    <button onClick={onClose} className="text-slate-400 hover:text-slate-600">
                        <X className="w-5 h-5" />
                    </button>
                </div>

                <div className="p-6 space-y-4 overflow-y-auto flex-1">
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                        <div className="space-y-1.5">
                            <Label>Typ *</Label>
                            <Select
                                value={formData.lieferantenTyp || ""}
                                onChange={(value) => handleChange("lieferantenTyp", value)}
                                options={[
                                    { value: "", label: "Bitte wählen" },
                                    ...LIEFERANT_TYPES
                                ]}
                                placeholder="Typ wählen"
                            />
                        </div>
                        <div className="space-y-1.5">
                            <Label>Name *</Label>
                            <Input
                                value={formData.lieferantenname || ""}
                                onChange={(e) => handleChange("lieferantenname", e.target.value)}
                                required
                            />
                        </div>
                        <div className="md:col-span-2 space-y-1.5">
                            <Label>Eigene Kundennummer (beim Lieferanten)</Label>
                            <Input
                                value={formData.eigeneKundennummer || ""}
                                onChange={(e) => handleChange("eigeneKundennummer", e.target.value)}
                                placeholder="z.B. K-123456"
                            />
                        </div>
                        <div className="md:col-span-2 space-y-1.5">
                            <Label>Standard-Kostenstelle</Label>
                            <p className="text-xs text-slate-400 -mt-1">
                                Wird bei neuen Bestellungen / Eingangsrechnungen automatisch vorgeschlagen.
                            </p>
                            <div className="flex items-center gap-2">
                                <div className="flex-1 px-3 py-2 bg-slate-50 border border-slate-200 rounded-lg text-sm text-slate-700 flex items-center gap-2">
                                    <Package className="w-4 h-4 text-slate-400 shrink-0" />
                                    {formData.standardKostenstelleName ? (
                                        <span>{formData.standardKostenstelleName}</span>
                                    ) : (
                                        <span className="text-slate-400">Keine zugewiesen</span>
                                    )}
                                </div>
                                <Button type="button" variant="outline" onClick={() => setShowKostenstelleModal(true)}>
                                    {formData.standardKostenstelleName ? "Ändern" : "Auswählen"}
                                </Button>
                                {formData.standardKostenstelleId != null && (
                                    <Button
                                        type="button"
                                        variant="ghost"
                                        onClick={() => setFormData(prev => ({ ...prev, standardKostenstelleId: null, standardKostenstelleName: undefined }))}
                                        title="Kostenstelle entfernen"
                                    >
                                        <X className="w-4 h-4" />
                                    </Button>
                                )}
                            </div>
                        </div>
                    </div>

                    <AddressAutocomplete
                        value={{
                            strasse: formData.strasse || "",
                            plz: formData.plz || "",
                            ort: formData.ort || ""
                        }}
                        onChange={(next) => setFormData(prev => ({
                            ...prev,
                            strasse: next.strasse,
                            plz: next.plz,
                            ort: next.ort
                        }))}
                    />

                    <div className="space-y-1.5">
                        <Label>Lieferzeit Ø (Tage)</Label>
                        <div className="px-3 py-2 bg-slate-50 border border-slate-200 rounded-lg text-slate-600">
                            {formData.lieferzeit || "—"}
                        </div>
                        <p className="text-xs text-slate-400">Wird automatisch aus Auftragsbestätigungen berechnet</p>
                    </div>

                    <div className="grid grid-cols-1 md:grid-cols-3 gap-4 border-t border-slate-100 pt-4">
                        <div className="space-y-1.5">
                            <Label>Telefon</Label>
                            <PhoneInput
                                value={formData.telefon || ""}
                                onChange={(v) => handleChange("telefon", v)}
                                variant="festnetz"
                                autoPrefillAreaCode
                                plz={formData.plz}
                                ort={formData.ort}
                            />
                        </div>
                        <div className="space-y-1.5">
                            <Label>Mobil</Label>
                            <PhoneInput
                                value={formData.mobiltelefon || ""}
                                onChange={(v) => handleChange("mobiltelefon", v)}
                                variant="mobil"
                            />
                        </div>
                        <div className="space-y-1.5">
                            <Label>Vertreter</Label>
                            <Input
                                value={formData.vertreter || ""}
                                onChange={(e) => handleChange("vertreter", e.target.value)}
                            />
                        </div>
                    </div>

                    <div className="space-y-2 border-t border-slate-100 pt-4">
                        <Label>E-Mail-Adressen</Label>
                        <div className="flex gap-2">
                            <Input
                                value={newEmail}
                                onChange={e => setNewEmail(e.target.value)}
                                placeholder="neue@adresse.de"
                                onKeyDown={e => { if (e.key === 'Enter') { e.preventDefault(); addEmail(); } }}
                            />
                            <Button type="button" onClick={addEmail} variant="outline">Hinzufügen</Button>
                        </div>
                        <div className="flex flex-wrap gap-2">
                            {(formData.kundenEmails || []).map(email => (
                                <span key={email} className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full bg-slate-100 text-slate-700 text-sm border border-slate-200">
                                    <Mail className="w-3 h-3 text-slate-400" />
                                    {email}
                                    <button type="button" onClick={() => removeEmail(email)} className="ml-1 text-slate-400 hover:text-red-600">
                                        <X className="w-3 h-3" />
                                    </button>
                                </span>
                            ))}
                        </div>
                    </div>
                </div>

                <div className="p-6 border-t border-slate-100 bg-slate-50 flex justify-end gap-3 rounded-b-xl">
                    <Button variant="ghost" onClick={onClose}>Abbrechen</Button>
                    <Button onClick={() => {
                        // Wenn noch Text im E-Mail-Feld steht, diesen automatisch hinzufügen
                        const finalData = { ...formData };
                        if (newEmail.trim()) {
                            const current = finalData.kundenEmails || [];
                            // Nur hinzufügen wenn noch nicht vorhanden
                            if (!current.includes(newEmail.trim())) {
                                finalData.kundenEmails = [...current, newEmail.trim()];
                            }
                        }
                        onSave(finalData);
                    }} className="bg-rose-600 hover:bg-rose-700 text-white">Speichern</Button>
                </div>
            </div>
            {showKostenstelleModal && (
                <KostenstelleSelectModal
                    onClose={() => setShowKostenstelleModal(false)}
                    onSelect={(k) => {
                        setFormData(prev => ({
                            ...prev,
                            standardKostenstelleId: k.id,
                            standardKostenstelleName: k.bezeichnung,
                        }));
                        setShowKostenstelleModal(false);
                    }}
                />
            )}
        </div>
    );
}

