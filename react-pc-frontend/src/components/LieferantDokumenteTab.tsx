import { useState, useMemo, useCallback, useEffect, useRef } from "react";
import { useNavigate } from "react-router-dom";
import { Search, FileText, Link2, ChevronRight, AlertCircle, X, Sparkles, Upload, User, ArrowUpDown, ArrowUp, ArrowDown, Briefcase, Building2, ExternalLink, Edit2, Trash2, Euro, CalendarDays, FileBadge, FilePlus2, type LucideIcon } from "lucide-react";
import { Button } from "./ui/button";
import { Input } from "./ui/input";
import { Card } from "./ui/card";
import { Select } from "./ui/select-custom";
import { cn } from "../lib/utils";
import type { LieferantDokument, LieferantDokumentTyp, LieferantDokumentenKette } from "../types";
import LieferantDokumentModal from "./LieferantDokumentModal";
import { LieferantDokumentImportModal } from "./LieferantDokumentImportModal";
import { ZuordnungModal } from "./ZuordnungModal";
import { prependUniqueById } from "../lib/optimisticUploads";
import { useAuth } from "../auth/AuthContext";
import { PositionsTrefferZeile } from "./PositionsTrefferZeile";
import { useToast } from "./ui/toast";
import { positionsSucheAktiv, trefferNachDokument, vereinigeTreffer, type PositionsTreffer } from "../lib/positionsTreffer";
import { DokumentSuchenDialog } from "../features/bestellungen/DokumentSuchenDialog";
import { istVorschlagsTyp, lieferantDokumentAlsBeleg } from "../features/bestellungen/kettenVorschlag";

// Typ-Konfiguration mit Farben
// icon: nur wo die Farbe allein nicht reicht (Werkstoffzeugnis ist neutral wie Sonstiges, aber mit Zeugnis-Symbol)
const DOK_TYP_CONFIG: Record<string, { label: string; color: string; bgColor: string; borderColor: string; icon?: LucideIcon }> = {
    ANGEBOT: { label: 'Angebot', color: 'text-blue-700', bgColor: 'bg-blue-50', borderColor: 'border-blue-200' },
    AUFTRAGSBESTAETIGUNG: { label: 'AB', color: 'text-purple-700', bgColor: 'bg-purple-50', borderColor: 'border-purple-200' },
    LIEFERSCHEIN: { label: 'Lieferschein', color: 'text-amber-700', bgColor: 'bg-amber-50', borderColor: 'border-amber-200' },
    WERKSTOFFZEUGNIS: { label: 'Werkstoffzeugnis', color: 'text-slate-700', bgColor: 'bg-slate-100', borderColor: 'border-slate-300', icon: FileBadge },
    RECHNUNG: { label: 'Rechnung', color: 'text-rose-700', bgColor: 'bg-rose-50', borderColor: 'border-rose-200' },
    GUTSCHRIFT: { label: 'Gutschrift', color: 'text-green-700', bgColor: 'bg-green-50', borderColor: 'border-green-200' },
    SONSTIG: { label: 'Sonstiges', color: 'text-slate-700', bgColor: 'bg-slate-50', borderColor: 'border-slate-200' },
};

// Fallback für unbekannte Typen
const DEFAULT_CONFIG = { label: 'Dokument', color: 'text-slate-700', bgColor: 'bg-slate-50', borderColor: 'border-slate-200' };

const getConfig = (typ: string) => DOK_TYP_CONFIG[typ] || DEFAULT_CONFIG;

const TYP_REIHENFOLGE: LieferantDokumentTyp[] = ['ANGEBOT', 'AUFTRAGSBESTAETIGUNG', 'LIEFERSCHEIN', 'WERKSTOFFZEUGNIS', 'RECHNUNG', 'GUTSCHRIFT', 'SONSTIG'];

/** So lange wartet die Positionssuche nach dem letzten Tastendruck. */
const POSITIONSSUCHE_VERZOEGERUNG_MS = 300;

const KEINE_POSITIONSTREFFER: ReadonlyMap<number, PositionsTreffer> = new Map();

/** Einmal je geöffnetem Reiter, wenn die Positionssuche nicht geht (Fehler, Netz weg, 401). */
export const POSITIONSSUCHE_FEHLER_TEXT = "Suche in Positionen gerade nicht möglich – Treffer nur nach Nummer und Betrag.";

interface LieferantDokumenteTabProps {
    lieferantId: number | string;
    lieferantName?: string;
    dokumente?: LieferantDokument[];
}

export default function LieferantDokumenteTab({ lieferantId, lieferantName, dokumente: initialDokumente }: LieferantDokumenteTabProps) {
    const navigate = useNavigate();
    const { isAdmin } = useAuth();
    const [dokumente, setDokumente] = useState<LieferantDokument[]>(initialDokumente || []);
    const [loading, setLoading] = useState(!initialDokumente);
    const [searchQuery, setSearchQuery] = useState("");
    const [filterTyp, setFilterTyp] = useState<string>("");
    // Treffer der Positionssuche im Backend – gehören immer zu genau einem Suchbegriff
    const [positionsErgebnis, setPositionsErgebnis] = useState<{ suche: string; treffer: ReadonlyMap<number, PositionsTreffer> } | null>(null);
    const suche = searchQuery.trim();
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

    // Zuordnung bearbeiten State
    const [zuordnungDokument, setZuordnungDokument] = useState<LieferantDokument | null>(null);

    // Dokument nachträglich einer Kette zuordnen: Kette oder einzelnes Dokument, zu dem gesucht wird
    const [kettenSuche, setKettenSuche] = useState<{ dokumente: LieferantDokument[]; einzeldokument: boolean } | null>(null);
    const suchKette = useMemo(() => kettenSuche && {
        lieferantName: lieferantName ?? null,
        dokumente: kettenSuche.dokumente.map(dok => lieferantDokumentAlsBeleg(dok, lieferantId)),
    }, [kettenSuche, lieferantName, lieferantId]);

    // Handler für Dokument-Klick
    const handleDokumentSelect = (dok: LieferantDokument) => {
        setSelectedDokument(dok);
        setShowDokumentModal(true);
    };

    const handleLoescheDokument = async (dok: LieferantDokument) => {
        const label = dok.geschaeftsdaten?.dokumentNummer || dok.originalDateiname || `ID ${dok.id}`;
        if (!window.confirm(`Dokument „${label}" wirklich löschen? Diese Aktion kann nicht rückgängig gemacht werden.`)) return;
        const res = await fetch(`/api/lieferant-dokumente/${dok.id}`, { method: 'DELETE' });
        if (res.ok) {
            setDokumente(prev => prev.filter(d => d.id !== dok.id));
        } else if (res.status === 422) {
            const body = await res.json().catch(() => ({}));
            alert(body.message || 'Dieses Dokument kann aus GoBD-Gründen nicht gelöscht werden. Bitte Typ zuerst auf "Sonstiges" ändern.');
        } else {
            alert('Löschen fehlgeschlagen. Bitte Seite neu laden und erneut versuchen.');
        }
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

    const toast = useToast();
    // Fehler der Positionssuche nur einmal melden – sonst käme bei jedem Tastendruck ein neuer Toast
    const positionsFehlerGemeldet = useRef(false);

    // Positionssuche (Material, Charge, Abmessung …): ab 2 Zeichen, 300 ms nach dem
    // letzten Tastendruck. Eine noch laufende Anfrage wird abgebrochen (das ist kein
    // Fehler). Schlägt sie fehl, bleiben die Treffer aus dem Browser stehen, und ein
    // dezenter Hinweis sagt einmal, dass gerade nur nach Nummer und Betrag gesucht wird.
    useEffect(() => {
        if (!positionsSucheAktiv(suche)) return;
        const controller = new AbortController();
        const timer = window.setTimeout(() => {
            const url = `/api/lieferanten/${encodeURIComponent(String(lieferantId))}/dokumente/positionssuche?q=${encodeURIComponent(suche)}`;
            fetch(url, { signal: controller.signal })
                .then(res => (res.ok ? res.json() : Promise.reject(new Error(`Positionssuche: ${res.status}`))))
                .then(daten => {
                    if (!controller.signal.aborted) setPositionsErgebnis({ suche, treffer: trefferNachDokument(daten) });
                })
                .catch(() => {
                    if (controller.signal.aborted || positionsFehlerGemeldet.current) return;
                    positionsFehlerGemeldet.current = true;
                    toast.warning(POSITIONSSUCHE_FEHLER_TEXT);
                });
        }, POSITIONSSUCHE_VERZOEGERUNG_MS);
        return () => {
            window.clearTimeout(timer);
            controller.abort();
        };
        // toast ist bei jedem Render ein neues Objekt – die Suche hängt nur an Begriff und Lieferant
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [suche, lieferantId]);

    // Nur Treffer zum aktuellen Suchbegriff zählen – alte Antworten bleiben außen vor
    const positionsTreffer = positionsSucheAktiv(suche) && positionsErgebnis?.suche === suche
        ? positionsErgebnis.treffer
        : KEINE_POSITIONSTREFFER;

    // Intelligente Suche über alle relevanten Felder – vereinigt mit den Positionstreffern
    const filteredDokumente = useMemo(() => {
        const passendeTypen = filterTyp ? dokumente.filter(dok => dok.typ === filterTyp) : dokumente;
        return vereinigeTreffer(passendeTypen, dok => {
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
        }, positionsTreffer);
    }, [dokumente, filterTyp, searchQuery, positionsTreffer]);

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
                                placeholder="Nummer, Material, Charge, Kommission …"
                                aria-label="Dokumente durchsuchen"
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
                                { value: "WERKSTOFFZEUGNIS", label: "Werkstoffzeugnisse" },
                                { value: "RECHNUNG", label: "Rechnungen" },
                                { value: "GUTSCHRIFT", label: "Gutschriften" },
                            ]}
                            className="w-full sm:w-52"
                            aria-label="Dokumenttyp"
                        />
                    </div>

                    {/* Search Hints */}
                    {!searchQuery && (
                        <div className="flex flex-wrap gap-2 text-xs text-slate-500">
                            {[
                                { icon: FileText, text: "Dokumentnummer" },
                                { icon: Link2, text: "Referenznummer" },
                                { icon: FileBadge, text: "Material, Charge (z.B. S235JR)" },
                                { icon: Euro, text: "Betrag (z.B. 952,00)" },
                                { icon: CalendarDays, text: "Datum (z.B. 25.9.2025)" },
                            ].map(({ icon: Icon, text }) => (
                                <span key={text} className="inline-flex items-center gap-1 bg-white/60 px-2 py-0.5 rounded border border-slate-200">
                                    <Icon className="w-3 h-3 text-slate-400" aria-hidden="true" />
                                    {text}
                                </span>
                            ))}
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
                            onNavigate={navigate}
                            onBearbeiten={(dok) => setZuordnungDokument(dok)}
                            onDokumentHinzufuegen={() => setKettenSuche({ dokumente: kette.dokumente, einzeldokument: false })}
                            positionsTreffer={positionsTreffer}
                            suchbegriff={suche}
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
                                { field: 'nummer' as SortField, label: 'Nr.' },
                                { field: 'datum' as SortField, label: 'Datum' },
                                { field: 'betrag' as SortField, label: 'Betrag' },
                                { field: 'typ' as SortField, label: 'Typ' },
                            ].map(({ field, label }) => {
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
                                onNavigate={navigate}
                                onBearbeiten={() => setZuordnungDokument(dok)}
                                onZuKetteZuordnen={istVorschlagsTyp(dok.typ) ? () => setKettenSuche({ dokumente: [dok], einzeldokument: true }) : undefined}
                                onDelete={isAdmin ? () => handleLoescheDokument(dok) : undefined}
                                treffer={positionsTreffer.get(dok.id)}
                                suchbegriff={suche}
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

            {/* Dokument einer Kette zuordnen */}
            {suchKette && (
                <DokumentSuchenDialog
                    kette={suchKette}
                    einzeldokument={kettenSuche?.einzeldokument ?? false}
                    onClose={() => setKettenSuche(null)}
                    onVerknuepft={() => {
                        setKettenSuche(null);
                        void loadDokumente();
                    }}
                />
            )}

            {/* Zuordnung bearbeiten Modal */}
            {zuordnungDokument && zuordnungDokument.geschaeftsdaten && (
                <ZuordnungModal
                    geschaeftsdokumentId={zuordnungDokument.id}
                    dokumentNummer={zuordnungDokument.geschaeftsdaten.dokumentNummer}
                    lieferantName={lieferantName ?? null}
                    pdfUrl={zuordnungDokument.url ?? null}
                    onClose={() => setZuordnungDokument(null)}
                    onSuccess={() => {
                        setZuordnungDokument(null);
                        void loadDokumente();
                    }}
                />
            )}
        </div>
    );
}

// Dokumentenkette Komponente
interface DokumentenKetteProps {
    kette: LieferantDokumentenKette;
    formatDate: (d?: string) => string;
    formatCurrency: (v?: number) => string;
    onSelect: (dok: LieferantDokument) => void;
    onNavigate: (path: string) => void;
    onBearbeiten: (dok: LieferantDokument) => void;
    /** Öffnet die Suche nach Dokumenten, die noch zur Kette gehören. */
    onDokumentHinzufuegen: () => void;
    /** Treffer der Positionssuche je Dokument-ID */
    positionsTreffer: ReadonlyMap<number, PositionsTreffer>;
    suchbegriff: string;
}

function DokumentenKette({ kette, formatDate, formatCurrency, onSelect, onNavigate, onBearbeiten, onDokumentHinzufuegen, positionsTreffer, suchbegriff }: DokumentenKetteProps) {
    const trefferInKette = kette.dokumente
        .map(dok => ({ dok, treffer: positionsTreffer.get(dok.id) }))
        .filter((eintrag): eintrag is { dok: LieferantDokument; treffer: PositionsTreffer } => eintrag.treffer !== undefined);

    // Collect all unique project allocations across chain documents
    const allAnteile = useMemo(() => {
        const seen = new Set<string>();
        const result: LieferantDokument['projektAnteile'] = [];
        for (const dok of kette.dokumente) {
            for (const anteil of dok.projektAnteile) {
                const key = `${anteil.projektId ?? ''}-${anteil.kostenstelleId ?? ''}`;
                if (!seen.has(key)) {
                    seen.add(key);
                    result.push(anteil);
                }
            }
        }
        return result;
    }, [kette.dokumente]);

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
                                <span className={cn("inline-flex items-center gap-1 text-xs font-semibold uppercase", config.color)}>
                                    {config.icon && <config.icon className="w-3.5 h-3.5" aria-hidden="true" />}
                                    {config.label}
                                </span>
                                <span
                                    className="text-sm font-medium text-slate-900 mt-1 truncate max-w-[90px]"
                                    title={dok.geschaeftsdaten?.dokumentNummer}
                                    data-kuerzung-erlaubt=""
                                >
                                    {dok.geschaeftsdaten?.dokumentNummer || "-"}
                                </span>
                                {dok.geschaeftsdaten?.referenzNummer && (
                                    <span className="text-xs text-slate-500 mt-0.5 truncate max-w-[90px]" title={dok.geschaeftsdaten.referenzNummer} data-kuerzung-erlaubt="">
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

            {/* Treffer der Positionssuche: welches Dokument der Kette welche Position enthält */}
            {trefferInKette.length > 0 && (
                <div className="mt-2 space-y-1">
                    {trefferInKette.map(({ dok, treffer }) => (
                        <div key={dok.id} className="flex items-start gap-2 min-w-0">
                            <span className={cn("shrink-0 text-[11px] font-semibold uppercase leading-5", getConfig(dok.typ).color)}>
                                {getConfig(dok.typ).label}
                                {dok.geschaeftsdaten?.dokumentNummer ? ` ${dok.geschaeftsdaten.dokumentNummer}` : ""}
                            </span>
                            <PositionsTrefferZeile
                                trefferText={treffer.trefferText}
                                weitereTreffer={treffer.weitereTreffer}
                                suchbegriff={suchbegriff}
                                className="leading-5"
                            />
                        </div>
                    ))}
                </div>
            )}

            {/* Zuordnungen: Projekte und Kostenstellen */}
            {allAnteile.length > 0 && (
                <div className="mt-3 pt-3 border-t border-slate-200/60 space-y-1.5">
                    <div className="flex items-center justify-between mb-1">
                        <p className="text-xs font-medium text-slate-500 uppercase tracking-wide">Zuordnungen</p>
                        {(() => {
                            const editableDok = kette.dokumente.find(d => d.geschaeftsdaten && d.projektAnteile.length > 0);
                            return editableDok ? (
                                <Button
                                    variant="ghost"
                                    size="sm"
                                    className="text-rose-700 hover:bg-rose-100 h-6 px-2 text-xs"
                                    onClick={() => onBearbeiten(editableDok)}
                                >
                                    <Edit2 className="w-3 h-3 mr-1" />
                                    Bearbeiten
                                </Button>
                            ) : null;
                        })()}
                    </div>
                    {allAnteile.map((anteil, idx) => (
                        <div key={idx} className="flex items-center gap-2 text-xs">
                            {anteil.projektId ? (
                                <button
                                    onClick={() => onNavigate(`/projekte?projektId=${anteil.projektId}`)}
                                    className="inline-flex items-center gap-1 text-rose-600 hover:text-rose-700 hover:underline"
                                >
                                    <Briefcase className="w-3 h-3" />
                                    <span className="font-medium">{anteil.projektName}</span>
                                    {anteil.auftragsnummer && <span className="text-slate-400">({anteil.auftragsnummer})</span>}
                                    <ExternalLink className="w-2.5 h-2.5" />
                                </button>
                            ) : anteil.kostenstelleId ? (
                                <span className="inline-flex items-center gap-1 text-slate-600">
                                    <Building2 className="w-3 h-3" />
                                    <span className="font-medium">{anteil.kostenstelleName}</span>
                                </span>
                            ) : null}
                            <span className="text-slate-400">
                                {anteil.prozent != null && `${anteil.prozent}%`}
                                {anteil.berechneterBetrag != null && ` · ${formatCurrency(anteil.berechneterBetrag)}`}
                            </span>
                            {anteil.beschreibung && (
                                <span className="text-slate-500 italic truncate max-w-[200px]" title={anteil.beschreibung}>
                                    „{anteil.beschreibung}"
                                </span>
                            )}
                            {anteil.zugeordnetVonName && (
                                <span className="text-slate-400">(von {anteil.zugeordnetVonName})</span>
                            )}
                        </div>
                    ))}
                </div>
            )}

            <div className="mt-3 pt-3 border-t border-slate-200/60 flex justify-end">
                <Button
                    size="sm"
                    variant="outline"
                    onClick={onDokumentHinzufuegen}
                    aria-label={kette.hauptDokumentNummer ? `Dokument zur Kette ${kette.hauptDokumentNummer} hinzufügen` : 'Dokument zur Kette hinzufügen'}
                    title="Fehlendes Dokument suchen, z. B. ein Werkstoffzeugnis oder die Rechnung"
                >
                    <FilePlus2 className="w-4 h-4" aria-hidden="true" />
                    Dokument hinzufügen
                </Button>
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
    onNavigate: (path: string) => void;
    onBearbeiten: () => void;
    /** „Zu Kette zuordnen“ – fehlt bei Sonstigem, das in keine Kette gehört. */
    onZuKetteZuordnen?: () => void;
    onDelete?: () => void;
    /** Getroffene Position, wenn die Positionssuche dieses Dokument gefunden hat */
    treffer?: PositionsTreffer;
    suchbegriff: string;
}

function DokumentCard({ dokument, formatDate, formatCurrency, onSelect, onNavigate, onBearbeiten, onZuKetteZuordnen, onDelete, treffer, suchbegriff }: DokumentCardProps) {
    const config = getConfig(dokument.typ);
    const hasProject = dokument.projektAnteile.length > 0;
    const confidence = dokument.geschaeftsdaten?.aiConfidence;

    return (
        <Card className={cn("p-4 relative group hover:shadow-md transition-shadow", config.bgColor, "border", config.borderColor)}>
            {/* Typ Badge */}
            <div className="flex items-center justify-between mb-2">
                <span className={cn("inline-flex items-center gap-1 text-xs font-semibold uppercase px-2 py-0.5 rounded-full", config.color, "bg-white/80")}>
                    {config.icon && <config.icon className="w-3.5 h-3.5" aria-hidden="true" />}
                    {config.label}
                </span>
                <div className="flex items-center gap-1">
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
                    {onDelete && (
                        <button
                            onClick={(e) => { e.stopPropagation(); onDelete(); }}
                            title="Dokument löschen (nur Admin)"
                            className="p-1 text-slate-300 hover:text-rose-600 hover:bg-rose-50 rounded opacity-0 group-hover:opacity-100 transition-opacity"
                        >
                            <Trash2 className="w-3.5 h-3.5" />
                        </button>
                    )}
                </div>
            </div>

            {/* Dokumentnummer */}
            <button onClick={onSelect} className="text-left w-full">
                <h4 className="font-semibold text-slate-900 truncate">
                    {dokument.geschaeftsdaten?.dokumentNummer || "Keine Nummer"}
                </h4>
                <p className="text-xs text-slate-500 truncate">{dokument.originalDateiname}</p>
            </button>
            {treffer && (
                <PositionsTrefferZeile
                    trefferText={treffer.trefferText}
                    weitereTreffer={treffer.weitereTreffer}
                    suchbegriff={suchbegriff}
                    className="mt-2"
                />
            )}

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

            {/* Zuordnungsdetails: Wer hat zugeordnet + Aufteilung */}
            {hasProject && (
                <div className="mt-3 pt-2 border-t border-slate-200/60 space-y-1.5">
                    {dokument.geschaeftsdaten && (
                        <div className="flex justify-end">
                            <Button
                                variant="ghost"
                                size="sm"
                                className="text-rose-700 hover:bg-rose-100 h-6 px-2 text-xs"
                                onClick={(e) => { e.stopPropagation(); onBearbeiten(); }}
                            >
                                <Edit2 className="w-3 h-3 mr-1" />
                                Bearbeiten
                            </Button>
                        </div>
                    )}
                    {dokument.projektAnteile.map((anteil, idx) => (
                        <div key={idx} className="flex items-center justify-between text-xs">
                            <div className="flex items-center gap-1 text-slate-600 min-w-0">
                                {anteil.projektId ? (
                                    <button
                                        onClick={(e) => { e.stopPropagation(); onNavigate(`/projekte?projektId=${anteil.projektId}`); }}
                                        className="flex items-center gap-1 truncate text-rose-600 hover:text-rose-700 hover:underline"
                                    >
                                        <Briefcase className="w-3 h-3 shrink-0" />
                                        <span className="truncate font-medium">{anteil.projektName}</span>
                                        {anteil.auftragsnummer && <span className="text-slate-400">({anteil.auftragsnummer})</span>}
                                        <ExternalLink className="w-2.5 h-2.5 shrink-0" />
                                    </button>
                                ) : anteil.kostenstelleId ? (
                                    <span className="flex items-center gap-1 truncate">
                                        <Building2 className="w-3 h-3 text-slate-500 shrink-0" />
                                        <span className="truncate font-medium">{anteil.kostenstelleName}</span>
                                    </span>
                                ) : null}
                            </div>
                            <div className="flex items-center gap-2 shrink-0 text-slate-500">
                                {anteil.prozent != null && <span>{anteil.prozent}%</span>}
                                {anteil.berechneterBetrag != null && <span className="font-medium">{formatCurrency(anteil.berechneterBetrag)}</span>}
                            </div>
                        </div>
                    ))}
                    {/* Beschreibung (erste mit Wert) */}
                    {dokument.projektAnteile.some(a => a.beschreibung) && (
                        <p className="text-xs text-slate-500 italic truncate" title={dokument.projektAnteile.find(a => a.beschreibung)?.beschreibung}>
                            „{dokument.projektAnteile.find(a => a.beschreibung)?.beschreibung}"
                        </p>
                    )}
                    {/* Zugeordnet von (erster Anteil mit User-Info) */}
                    {dokument.projektAnteile.some(a => a.zugeordnetVonName) && (
                        <div className="flex items-center gap-1 text-xs text-slate-400 mt-1">
                            <User className="w-3 h-3" />
                            <span>
                                Zugeordnet von{' '}
                                <span className="font-medium text-slate-600">
                                    {dokument.projektAnteile.find(a => a.zugeordnetVonName)?.zugeordnetVonName}
                                </span>
                            </span>
                            {dokument.projektAnteile.find(a => a.zugeordnetAm)?.zugeordnetAm && (
                                <span className="text-slate-400">
                                    am {new Date(dokument.projektAnteile.find(a => a.zugeordnetAm)!.zugeordnetAm!).toLocaleDateString('de-DE')}
                                </span>
                            )}
                        </div>
                    )}
                </div>
            )}

            {onZuKetteZuordnen && (
                <div className="mt-3 pt-2 border-t border-slate-200/60 flex justify-end">
                    <Button
                        size="sm"
                        variant="outline"
                        className="bg-white"
                        onClick={(e) => { e.stopPropagation(); onZuKetteZuordnen(); }}
                        aria-label={`${config.label} ${dokument.geschaeftsdaten?.dokumentNummer || dokument.originalDateiname} zu Kette zuordnen`}
                        title="Passende Bestellung suchen und dieses Dokument dort anhängen"
                    >
                        <Link2 className="w-4 h-4" aria-hidden="true" />
                        Zu Kette zuordnen
                    </Button>
                </div>
            )}
        </Card>
    );
}
