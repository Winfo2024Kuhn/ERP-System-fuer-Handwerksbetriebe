import { useState, useMemo, useCallback, useEffect } from "react";
import { Search, FileText, Link2, FolderPlus, ChevronRight, AlertCircle, X, Sparkles, Upload, User, ArrowUpDown, ArrowUp, ArrowDown } from "lucide-react";
import { Button } from "./ui/button";
import { Input } from "./ui/input";
import { Card } from "./ui/card";
import { Select } from "./ui/select-custom";
import { cn } from "../lib/utils";
import type { LieferantDokument, LieferantDokumentTyp, LieferantDokumentenKette } from "../types";
import LieferantDokumentModal from "./LieferantDokumentModal";
import { LieferantDokumentImportModal } from "./LieferantDokumentImportModal";
import { prependUniqueById } from "../lib/optimisticUploads";

// Typ-Konfiguration mit Farben
const DOK_TYP_CONFIG: Record<string, { label: string; color: string; bgColor: string; borderColor: string }> = {
    ANGEBOT: { label: 'Angebot', color: 'text-blue-700', bgColor: 'bg-blue-50', borderColor: 'border-blue-200' },
    AUFTRAGSBESTAETIGUNG: { label: 'AB', color: 'text-purple-700', bgColor: 'bg-purple-50', borderColor: 'border-purple-200' },
    LIEFERSCHEIN: { label: 'Lieferschein', color: 'text-amber-700', bgColor: 'bg-amber-50', borderColor: 'border-amber-200' },
    RECHNUNG: { label: 'Rechnung', color: 'text-rose-700', bgColor: 'bg-rose-50', borderColor: 'border-rose-200' },
    GUTSCHRIFT: { label: 'Gutschrift', color: 'text-green-700', bgColor: 'bg-green-50', borderColor: 'border-green-200' },
    SONSTIG: { label: 'Sonstiges', color: 'text-slate-700', bgColor: 'bg-slate-50', borderColor: 'border-slate-200' },
};

// Fallback für unbekannte Typen
const DEFAULT_CONFIG = { label: 'Dokument', color: 'text-slate-700', bgColor: 'bg-slate-50', borderColor: 'border-slate-200' };

const getConfig = (typ: string) => DOK_TYP_CONFIG[typ] || DEFAULT_CONFIG;

const TYP_REIHENFOLGE: LieferantDokumentTyp[] = ['ANGEBOT', 'AUFTRAGSBESTAETIGUNG', 'LIEFERSCHEIN', 'RECHNUNG', 'GUTSCHRIFT', 'SONSTIG'];

interface LieferantDokumenteTabProps {
    lieferantId: number | string;
    dokumente?: LieferantDokument[];
}

export default function LieferantDokumenteTab({ lieferantId, dokumente: initialDokumente }: LieferantDokumenteTabProps) {
    const [dokumente, setDokumente] = useState<LieferantDokument[]>(initialDokumente || []);
    const [loading, setLoading] = useState(!initialDokumente);
    const [searchQuery, setSearchQuery] = useState("");
    const [filterTyp, setFilterTyp] = useState<string>("");
    // Sortier-State
    type SortField = 'datum' | 'betrag' | 'typ' | 'nummer';
    type SortDirection = 'asc' | 'desc';
    const [sortField, setSortField] = useState<SortField>('datum');
    const [sortDirection, setSortDirection] = useState<SortDirection>('desc');

    const handleSort = (field: SortField) => {
        if (sortField === field) {
            setSortDirection(prev => prev === 'asc' ? 'desc' : 'asc');
        } else {
            setSortField(field);
            setSortDirection('asc');
        }
    };

    // UI State für Modals
    const [selectedDokument, setSelectedDokument] = useState<LieferantDokument | null>(null);
    const [showDokumentModal, setShowDokumentModal] = useState(false);
    const [showImportModal, setShowImportModal] = useState(false);
    const [, setShowProjektModal] = useState(false);
    const [, setShowVerknuepfungModal] = useState(false);

    // Handler für Dokument-Klick
    const handleDokumentSelect = (dok: LieferantDokument) => {
        setSelectedDokument(dok);
        setShowDokumentModal(true);
    };

    // Dokumente laden falls nicht übergeben
    useEffect(() => {
        if (!initialDokumente) {
            loadDokumente();
        }
    // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [lieferantId, initialDokumente]);

    const loadDokumente = useCallback(async () => {
        setLoading(true);
        try {
            const res = await fetch(`/api/lieferanten/${lieferantId}/dokumente`);
            if (res.ok) {
                const data = await res.json();
                setDokumente(data);
            }
        } catch (err) {
            console.error("Fehler beim Laden der Dokumente", err);
        } finally {
            setLoading(false);
        }
    }, [lieferantId]);

    // Intelligente Suche über alle relevanten Felder
    const filteredDokumente = useMemo(() => {
        return dokumente.filter(dok => {
            // Typ-Filter
            if (filterTyp && dok.typ !== filterTyp) return false;

            // Intelligente Suche über viele Felder
            if (searchQuery) {
                const q = searchQuery.toLowerCase().trim();
                const gd = dok.geschaeftsdaten;

                // Alle durchsuchbaren Felder sammeln
                const searchableFields = [
                    gd?.dokumentNummer,
                    gd?.referenzNummer,
                    gd?.bestellnummer,
                    dok.originalDateiname,
                    // Beträge als String (mit und ohne Formatierung)
                    gd?.betragNetto?.toString(),
                    gd?.betragBrutto?.toString(),
                    gd?.betragNetto?.toFixed(2).replace('.', ','),
                    gd?.betragBrutto?.toFixed(2).replace('.', ','),
                    // Datum in verschiedenen Formaten
                    gd?.dokumentDatum,
                    gd?.dokumentDatum ? new Date(gd.dokumentDatum).toLocaleDateString('de-DE') : null,
                    // Dokumenttyp-Label
                    DOK_TYP_CONFIG[dok.typ]?.label,
                ].filter(Boolean).map(s => s!.toLowerCase());

                // Prüfe ob irgendeines der Felder den Suchbegriff enthält
                const matches = searchableFields.some(field => field.includes(q));
                if (!matches) return false;
            }

            return true;
        });
    }, [dokumente, filterTyp, searchQuery]);

    // Berechne Anzahl aktiver Filter
    const activeFilterCount = useMemo(() => {
        let count = 0;
        if (searchQuery) count++;
        if (filterTyp) count++;
        return count;
    }, [searchQuery, filterTyp]);

    // Gruppiere Dokumente in Ketten (verknüpfte) und Einzeldokumente
    const { ketten, einzelDokumente } = useMemo(() => {
        const verknuepft = new Set<number>();
        const chains: LieferantDokument[][] = [];
        const processed = new Set<number>();

        // Erstelle Lookup für alle Dokumente
        const dokLookup = new Map<number, LieferantDokument>();
        filteredDokumente.forEach(dok => dokLookup.set(dok.id, dok));

        // Helfer für transitive Gruppierung
        const collectChain = (dok: LieferantDokument, currentChain: Set<number>) => {
            if (currentChain.has(dok.id)) return;
            currentChain.add(dok.id);

            // Verknüpfungen folgen
            dok.verknuepfteDokumente.forEach(ref => {
                const linked = dokLookup.get(ref.id);
                if (linked) {
                    collectChain(linked, currentChain);
                }
            });

            // Rückwärts-Verknüpfungen finden (wer verweist auf mich?)
            filteredDokumente.forEach(other => {
                if (other.verknuepfteDokumente.some(ref => ref.id === dok.id)) {
                    collectChain(other, currentChain);
                }
            });
        };

        filteredDokumente.forEach(dok => {
            if (processed.has(dok.id)) return;

            if (dok.verknuepfteDokumente.length > 0 ||
                filteredDokumente.some(other => other.verknuepfteDokumente.some(ref => ref.id === dok.id))) {

                const chainSet = new Set<number>();
                collectChain(dok, chainSet);

                if (chainSet.size > 1) {
                    const chainDocs: LieferantDokument[] = [];
                    chainSet.forEach(id => {
                        const d = dokLookup.get(id);
                        if (d) chainDocs.push(d);
                        processed.add(id);
                        verknuepft.add(id);
                    });
                    chains.push(chainDocs);
                }
            }
        });

        // Sortiere Dokumente in Ketten nach Typ-Reihenfolge und Datum
        const sortedKetten: LieferantDokumentenKette[] = chains.map((docs, idx) => {
            docs.sort((a, b) => {
                const diff = TYP_REIHENFOLGE.indexOf(a.typ) - TYP_REIHENFOLGE.indexOf(b.typ);
                if (diff !== 0) return diff;
                // Bei gleichem Typ nach Datum sortieren
                const dateA = a.geschaeftsdaten?.dokumentDatum || "";
                const dateB = b.geschaeftsdaten?.dokumentDatum || "";
                return dateA.localeCompare(dateB);
            });
            return {
                id: `kette-${idx}`,
                dokumente: docs,
                hauptDokumentNummer: docs.find(d => d.typ === 'RECHNUNG')?.geschaeftsdaten?.dokumentNummer || docs[0]?.geschaeftsdaten?.dokumentNummer,
                gesamtBetrag: docs.find(d => d.typ === 'RECHNUNG')?.geschaeftsdaten?.betragBrutto
            };
        });

        // Einzeldokumente (nicht verknüpft) – sortiert
        const einzelDokumente = filteredDokumente.filter(dok => !verknuepft.has(dok.id));

        // Sortierung anwenden
        einzelDokumente.sort((a, b) => {
            let cmp = 0;
            switch (sortField) {
                case 'datum': {
                    const dA = a.geschaeftsdaten?.dokumentDatum || a.uploadDatum || '';
                    const dB = b.geschaeftsdaten?.dokumentDatum || b.uploadDatum || '';
                    cmp = dA.localeCompare(dB);
                    break;
                }
                case 'betrag': {
                    const bA = a.geschaeftsdaten?.betragBrutto ?? 0;
                    const bB = b.geschaeftsdaten?.betragBrutto ?? 0;
                    cmp = bA - bB;
                    break;
                }
                case 'typ': {
                    cmp = TYP_REIHENFOLGE.indexOf(a.typ) - TYP_REIHENFOLGE.indexOf(b.typ);
                    break;
                }
                case 'nummer': {
                    const nA = a.geschaeftsdaten?.dokumentNummer || '';
                    const nB = b.geschaeftsdaten?.dokumentNummer || '';
                    cmp = nA.localeCompare(nB, 'de', { numeric: true });
                    break;
                }
            }
            return sortDirection === 'asc' ? cmp : -cmp;
        });

        return { ketten: sortedKetten, einzelDokumente };
    }, [filteredDokumente, sortField, sortDirection]);


    const formatDate = (dateStr?: string) => {
        if (!dateStr) return "-";
        try {
            return new Date(dateStr).toLocaleDateString('de-DE');
        } catch {
            return dateStr;
        }
    };

    const formatCurrency = (val?: number) => {
        if (val === undefined || val === null) return "-";
        return new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR' }).format(val);
    };

    if (loading) {
        return (
            <div className="flex items-center justify-center py-12 text-slate-500">
                <div className="animate-spin w-5 h-5 border-2 border-rose-500 border-t-transparent rounded-full mr-3" />
                Dokumente werden geladen...
            </div>
        );
    }

    return (
        <div className="space-y-6">
            {/* Smart Search & Filter Bar */}
            <Card className="p-4 bg-gradient-to-r from-slate-50 to-rose-50/30 border-slate-200">
                <div className="flex flex-col gap-3">
                    {/* Search Header */}
                    <div className="flex items-center gap-2">
                        <Sparkles className="w-4 h-4 text-rose-500" />
                        <span className="text-sm font-medium text-slate-700">Intelligente Suche</span>
                        {activeFilterCount > 0 && (
                            <span className="text-xs bg-rose-100 text-rose-700 px-2 py-0.5 rounded-full">
                                {activeFilterCount} Filter aktiv
                            </span>
                        )}
                        {activeFilterCount > 0 && (
                            <Button
                                variant="ghost"
                                size="sm"
                                onClick={() => { setSearchQuery(""); setFilterTyp(""); }}
                                className="ml-auto text-xs text-slate-500 hover:text-rose-600 h-6 px-2"
                            >
                                <X className="w-3 h-3 mr-1" />
                                Zurücksetzen
                            </Button>
                        )}
                    </div>

                    {/* Search Input Row */}
                    <div className="flex flex-col sm:flex-row gap-3">
                        <div className="relative flex-1">
                            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
                            <Input
                                placeholder="Suche nach Nummer, Referenz, Betrag, Datum..."
                                value={searchQuery}
                                onChange={e => setSearchQuery(e.target.value)}
                                className="pl-10 pr-10 bg-white border-slate-200 focus:border-rose-300 focus:ring-rose-200"
                            />
                            {searchQuery && (
                                <button
                                    onClick={() => setSearchQuery("")}
                                    className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600"
                                    title="Suche zurücksetzen"
                                >
                                    <X className="w-4 h-4" />
                                </button>
                            )}
                        </div>
                        <Select
                            value={filterTyp}
                            onChange={setFilterTyp}
                            options={[
                                { value: "", label: "Alle Typen" },
                                { value: "ANGEBOT", label: "Angebote" },
                                { value: "AUFTRAGSBESTAETIGUNG", label: "Auftragsbestätigungen" },
                                { value: "LIEFERSCHEIN", label: "Lieferscheine" },
                                { value: "RECHNUNG", label: "Rechnungen" },
                                { value: "GUTSCHRIFT", label: "Gutschriften" },
                            ]}
                            className="w-full sm:w-52"
                        />
                    </div>

                    {/* Search Hints */}
                    {!searchQuery && (
                        <div className="flex flex-wrap gap-2 text-xs text-slate-500">
                            <span className="bg-white/60 px-2 py-0.5 rounded border border-slate-200">
                                📄 Dokumentnummer
                            </span>
                            <span className="bg-white/60 px-2 py-0.5 rounded border border-slate-200">
                                🔗 Referenznummer
                            </span>
                            <span className="bg-white/60 px-2 py-0.5 rounded border border-slate-200">
                                💰 Betrag (z.B. 952,00)
                            </span>
                            <span className="bg-white/60 px-2 py-0.5 rounded border border-slate-200">
                                📅 Datum (z.B. 25.9.2025)
                            </span>
                        </div>
                    )}

                    {/* Search Results Info */}
                    {searchQuery && (
                        <div className="text-xs text-slate-600">
                            <span className="font-medium text-rose-600">{filteredDokumente.length}</span> von {dokumente.length} Dokumenten gefunden
                        </div>
                    )}
                </div>
            </Card>

            {/* Action Bar */}
            <div className="flex justify-between items-center">
                <div className="grid grid-cols-2 md:grid-cols-4 gap-3 flex-1">
                    <div className="bg-slate-50 p-3 rounded-xl border border-slate-100">
                        <p className="text-xs text-slate-500 uppercase tracking-wide">Gesamt</p>
                        <p className="text-lg font-semibold text-slate-900">{dokumente.length}</p>
                    </div>
                    <div className="bg-blue-50 p-3 rounded-xl border border-blue-100">
                        <p className="text-xs text-blue-600 uppercase tracking-wide">Anfragen</p>
                        <p className="text-lg font-semibold text-blue-900">{dokumente.filter(d => d.typ === 'ANGEBOT').length}</p>
                    </div>
                    <div className="bg-purple-50 p-3 rounded-xl border border-purple-100">
                        <p className="text-xs text-purple-600 uppercase tracking-wide">ABs</p>
                        <p className="text-lg font-semibold text-purple-900">{dokumente.filter(d => d.typ === 'AUFTRAGSBESTAETIGUNG').length}</p>
                    </div>
                    <div className="bg-rose-50 p-3 rounded-xl border border-rose-100">
                        <p className="text-xs text-rose-600 uppercase tracking-wide">Rechnungen</p>
                        <p className="text-lg font-semibold text-rose-900">{dokumente.filter(d => d.typ === 'RECHNUNG').length}</p>
                    </div>
                </div>
                <Button
                    size="sm"
                    onClick={() => setShowImportModal(true)}
                    className="bg-rose-600 text-white hover:bg-rose-700 ml-4"
                >
                    <Upload className="w-4 h-4 mr-2" />
                    Dokument importieren
                </Button>
            </div>

            {/* Dokumentenketten */}
            {ketten.length > 0 && (
                <div className="space-y-4">
                    <h3 className="text-sm font-semibold text-slate-700 uppercase tracking-wide flex items-center gap-2">
                        <Link2 className="w-4 h-4" />
                        Dokumentenketten ({ketten.length})
                    </h3>
                    {ketten.map(kette => (
                        <DokumentenKette
                            key={kette.id}
                            kette={kette}
                            formatDate={formatDate}
                            formatCurrency={formatCurrency}
                            onSelect={handleDokumentSelect}
                        />
                    ))}
                </div>
            )}

            {/* Einzeldokumente */}
            {einzelDokumente.length > 0 && (
                <div className="space-y-4">
                    <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2">
                        <h3 className="text-sm font-semibold text-slate-700 uppercase tracking-wide flex items-center gap-2">
                            <FileText className="w-4 h-4" />
                            Einzeldokumente ({einzelDokumente.length})
                        </h3>
                        {/* Sortierleiste */}
                        <div className="flex items-center gap-1 flex-wrap">
                            <span className="text-xs text-slate-400 mr-1">Sortieren:</span>
                            {[
                                { field: 'nummer' as SortField, label: 'Nr.', icon: '📄' },
                                { field: 'datum' as SortField, label: 'Datum', icon: '📅' },
                                { field: 'betrag' as SortField, label: 'Betrag', icon: '💰' },
                                { field: 'typ' as SortField, label: 'Typ', icon: '🏷' },
                            ].map(({ field, label, icon }) => {
                                const isActive = sortField === field;
                                const SortIcon = isActive
                                    ? (sortDirection === 'asc' ? ArrowUp : ArrowDown)
                                    : ArrowUpDown;
                                return (
                                    <button
                                        key={field}
                                        onClick={() => handleSort(field)}
                                        className={cn(
                                            "flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-medium transition-colors",
                                            isActive
                                                ? "bg-rose-100 text-rose-700 border border-rose-200"
                                                : "bg-slate-100 text-slate-500 hover:bg-slate-200 border border-transparent"
                                        )}
                                    >
                                        <span>{icon}</span>
                                        {label}
                                        <SortIcon className="w-3 h-3" />
                                    </button>
                                );
                            })}
                        </div>
                    </div>
                    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3">
                        {einzelDokumente.map(dok => (
                            <DokumentCard
                                key={dok.id}
                                dokument={dok}
                                formatDate={formatDate}
                                formatCurrency={formatCurrency}
                                onSelect={() => handleDokumentSelect(dok)}
                                onProjektZuordnen={() => {
                                    setSelectedDokument(dok);
                                    setShowProjektModal(true);
                                }}
                                onVerknuepfen={() => {
                                    setSelectedDokument(dok);
                                    setShowVerknuepfungModal(true);
                                }}
                            />
                        ))}
                    </div>

                </div>
            )}

            {/* Empty State */}
            {dokumente.length === 0 && (
                <div className="text-center py-12 text-slate-500">
                    <FileText className="w-12 h-12 mx-auto mb-3 text-slate-300" />
                    <p>Keine Dokumente vorhanden</p>
                    <p className="text-sm mt-1">Dokumente werden automatisch aus E-Mail-Anhängen erstellt</p>
                </div>
            )}

            {/* No Results */}
            {dokumente.length > 0 && filteredDokumente.length === 0 && (
                <div className="text-center py-8 text-slate-500">
                    <Search className="w-8 h-8 mx-auto mb-2 text-slate-300" />
                    <p>Keine Dokumente gefunden</p>
                    <Button variant="ghost" size="sm" onClick={() => { setSearchQuery(""); setFilterTyp(""); }} className="mt-2">
                        Filter zurücksetzen
                    </Button>
                </div>
            )}

            {/* Dokument Detail Modal */}
            <LieferantDokumentModal
                isOpen={showDokumentModal}
                onClose={() => setShowDokumentModal(false)}
                dokument={selectedDokument}
                lieferantId={lieferantId}
                onSave={(updated) => {
                    setDokumente(prev => prev.map(d => d.id === updated.id ? updated : d));
                }}
            />

            {/* Dokument Import Modal */}
            <LieferantDokumentImportModal
                isOpen={showImportModal}
                onClose={() => setShowImportModal(false)}
                lieferantId={lieferantId}
                onSuccess={(createdDokumente) => {
                    setDokumente(prev => prependUniqueById(prev, createdDokumente));
                    void loadDokumente();
                }}
            />
        </div>
    );
}

// Dokumentenkette Komponente
interface DokumentenKetteProps {
    kette: LieferantDokumentenKette;
    formatDate: (d?: string) => string;
    formatCurrency: (v?: number) => string;
    onSelect: (dok: LieferantDokument) => void;
}

function DokumentenKette({ kette, formatDate, formatCurrency, onSelect }: DokumentenKetteProps) {
    return (
        <Card className="p-4">
            <div className="flex items-center gap-2 mb-3">
                <Link2 className="w-4 h-4 text-rose-500" />
                <span className="text-sm font-medium text-slate-700">
                    Kette: {kette.hauptDokumentNummer || `#${kette.id}`}
                </span>
                {kette.gesamtBetrag && (
                    <span className="ml-auto text-sm font-semibold text-slate-900">
                        {formatCurrency(kette.gesamtBetrag)}
                    </span>
                )}
            </div>
            <div className="flex items-center gap-2 overflow-x-auto pb-2">
                {kette.dokumente.map((dok, idx) => {
                    const config = getConfig(dok.typ);
                    return (
                        <div key={dok.id} className="flex items-center gap-2">
                            {idx > 0 && (
                                <ChevronRight className="w-4 h-4 text-slate-300 shrink-0" />
                            )}
                            <button
                                onClick={() => onSelect(dok)}
                                title={`Dokument ${dok.geschaeftsdaten?.dokumentNummer || dok.originalDateiname} öffnen`}
                                className={cn(
                                    "flex flex-col items-center p-3 rounded-lg border-2 min-w-[100px] transition-all hover:shadow-md",
                                    config.bgColor, config.borderColor
                                )}
                            >
                                <span className={cn("text-xs font-semibold uppercase", config.color)}>
                                    {config.label}
                                </span>
                                <span className="text-sm font-medium text-slate-900 mt-1 truncate max-w-[90px]">
                                    {dok.geschaeftsdaten?.dokumentNummer || "-"}
                                </span>
                                {dok.geschaeftsdaten?.referenzNummer && (
                                    <span className="text-xs text-slate-500 mt-0.5 truncate max-w-[90px]" title={dok.geschaeftsdaten.referenzNummer}>
                                        Ref: {dok.geschaeftsdaten.referenzNummer}
                                    </span>
                                )}
                                <span className="text-xs text-slate-500 mt-0.5">
                                    {formatDate(dok.geschaeftsdaten?.dokumentDatum)}
                                </span>
                                {dok.geschaeftsdaten?.betragBrutto && (
                                    <span className="text-xs font-medium text-slate-700 mt-1">
                                        {formatCurrency(dok.geschaeftsdaten.betragBrutto)}
                                    </span>
                                )}
                                {dok.uploadedByName && (
                                    <div className="flex items-center gap-1 mt-1 text-[10px] text-slate-400">
                                        <User className="w-3 h-3" />
                                        <span className="truncate max-w-[80px]">{dok.uploadedByName}</span>
                                    </div>
                                )}
                            </button>
                        </div>
                    );
                })}
            </div>
        </Card>
    );
}

// Einzeldokument Card
interface DokumentCardProps {
    dokument: LieferantDokument;
    formatDate: (d?: string) => string;
    formatCurrency: (v?: number) => string;
    onSelect: () => void;
    onProjektZuordnen: () => void;
    onVerknuepfen: () => void;
}

function DokumentCard({ dokument, formatDate, formatCurrency, onSelect, onProjektZuordnen, onVerknuepfen }: DokumentCardProps) {
    const config = getConfig(dokument.typ);
    const hasProject = dokument.projektAnteile.length > 0;
    const confidence = dokument.geschaeftsdaten?.aiConfidence;

    return (
        <Card className={cn("p-4 relative group hover:shadow-md transition-shadow", config.bgColor, "border", config.borderColor)}>
            {/* Typ Badge */}
            <div className="flex items-center justify-between mb-2">
                <span className={cn("text-xs font-semibold uppercase px-2 py-0.5 rounded-full", config.color, "bg-white/80")}>
                    {config.label}
                </span>
                {confidence !== undefined && (
                    <span className={cn(
                        "text-xs px-1.5 py-0.5 rounded",
                        confidence >= 0.9 ? "bg-green-100 text-green-700" :
                            confidence >= 0.7 ? "bg-yellow-100 text-yellow-700" :
                                "bg-red-100 text-red-700"
                    )}>
                        {Math.round(confidence * 100)}%
                    </span>
                )}
            </div>

            {/* Dokumentnummer */}
            <button onClick={onSelect} className="text-left w-full">
                <h4 className="font-semibold text-slate-900 truncate">
                    {dokument.geschaeftsdaten?.dokumentNummer || "Keine Nummer"}
                </h4>
                <p className="text-xs text-slate-500 truncate">{dokument.originalDateiname}</p>
            </button>

            {/* Details */}
            <div className="mt-3 space-y-1 text-sm">
                <div className="flex justify-between">
                    <span className="text-slate-500">Datum:</span>
                    <span className="font-medium">{formatDate(dokument.geschaeftsdaten?.dokumentDatum)}</span>
                </div>
                {dokument.geschaeftsdaten?.referenzNummer && (
                    <div className="flex justify-between">
                        <span className="text-slate-500">Referenz:</span>
                        <span className="font-medium">{dokument.geschaeftsdaten.referenzNummer}</span>
                    </div>
                )}
                {dokument.geschaeftsdaten?.betragBrutto && (
                    <div className="flex justify-between">
                        <span className="text-slate-500">Betrag:</span>
                        <span className="font-semibold">{formatCurrency(dokument.geschaeftsdaten.betragBrutto)}</span>
                    </div>
                )}
                {dokument.uploadedByName && (
                    <div className="flex justify-between items-center text-slate-500">
                        <span className="text-xs flex items-center gap-1">
                            <User className="w-3 h-3" /> Erfasst von:
                        </span>
                        <span className="font-medium text-slate-700">{dokument.uploadedByName}</span>
                    </div>
                )}
            </div>

            {/* Warnungen */}
            {dokument.geschaeftsdaten?.manuellePruefungErforderlich && (
                <div className="mt-3 flex items-center gap-1.5 text-xs text-red-600 bg-red-50 px-2 py-1 rounded border border-red-200">
                    <AlertCircle className="w-3 h-3" />
                    Manuelle Prüfung erforderlich
                </div>
            )}
            {!hasProject && (
                <div className="mt-3 flex items-center gap-1.5 text-xs text-amber-600 bg-amber-50 px-2 py-1 rounded">
                    <AlertCircle className="w-3 h-3" />
                    Kein Projekt zugeordnet
                </div>
            )}

            {/* Actions */}
            <div className="mt-3 pt-3 border-t border-slate-200/50 flex gap-2">
                <Button variant="ghost" size="sm" onClick={onProjektZuordnen} className="flex-1 text-xs">
                    <FolderPlus className="w-3 h-3 mr-1" />
                    Projekt
                </Button>
                <Button variant="ghost" size="sm" onClick={onVerknuepfen} className="flex-1 text-xs">
                    <Link2 className="w-3 h-3 mr-1" />
                    Verknüpfen
                </Button>
            </div>
        </Card>
    );
}
