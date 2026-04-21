import { useState, useEffect, useRef, useCallback, useMemo } from 'react';
import {
    Search, X, Flame, Loader2, Check, Filter, CheckSquare, Square,
    ShieldCheck, AlertTriangle,
} from 'lucide-react';

export interface WpsSuchErgebnis {
    id: number;
    wpsNummer: string;
    bezeichnung?: string | null;
    norm?: string | null;
    schweissProzes?: string | null;
    grundwerkstoff?: string | null;
    zusatzwerkstoff?: string | null;
    nahtart?: string | null;
    gueltigBis?: string | null;
    quelle?: 'EDITOR' | 'UPLOAD' | string | null;
}

interface WpsSearchModalProps {
    isOpen: boolean;
    onClose: () => void;
    /** Wird beim Klick auf "Übernehmen" mit allen markierten WPS aufgerufen. */
    onConfirm: (wps: WpsSuchErgebnis[]) => void;
    /** IDs die beim Öffnen vorausgewählt sind. */
    initialSelectedIds?: number[];
}

type GueltigkeitsFilter = 'ALLE' | 'NUR_GUELTIG' | 'NUR_ABGELAUFEN';

/**
 * Multi-Picker-Modal für Schweißanweisungen (WPS) mit Freitextsuche
 * und mehreren Filtern (Prozess, Grundwerkstoff, Quelle, Gültigkeit).
 *
 * Lädt alle WPS einmalig über {@code /api/wps} und filtert lokal –
 * bei typischerweise wenigen hundert WPS pro Betrieb ist das
 * spürbar schneller als Server-Roundtrips pro Tastendruck.
 */
export function WpsSearchModal({
    isOpen,
    onClose,
    onConfirm,
    initialSelectedIds = [],
}: WpsSearchModalProps) {
    const [alle, setAlle] = useState<WpsSuchErgebnis[]>([]);
    const [loading, setLoading] = useState(false);
    const [fehler, setFehler] = useState<string | null>(null);
    const [suche, setSuche] = useState('');
    const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set());

    // Filter
    const [prozessFilter, setProzessFilter] = useState<Set<string>>(new Set());
    const [werkstoffFilter, setWerkstoffFilter] = useState<Set<string>>(new Set());
    const [quelleFilter, setQuelleFilter] = useState<Set<string>>(new Set());
    const [gueltigkeit, setGueltigkeit] = useState<GueltigkeitsFilter>('ALLE');

    const abortRef = useRef<AbortController | null>(null);

    const loadWps = useCallback(async () => {
        if (abortRef.current) abortRef.current.abort();
        const controller = new AbortController();
        abortRef.current = controller;
        setLoading(true);
        setFehler(null);
        try {
            const res = await fetch('/api/wps', { signal: controller.signal });
            if (!res.ok) throw new Error(`HTTP ${res.status}`);
            const data = await res.json();
            const list: WpsSuchErgebnis[] = Array.isArray(data) ? data : [];
            setAlle(list);
        } catch (e) {
            if (!(e instanceof DOMException && e.name === 'AbortError')) {
                console.error('WPS laden fehlgeschlagen:', e);
                setFehler('Schweißanweisungen konnten nicht geladen werden.');
            }
        } finally {
            if (!controller.signal.aborted) setLoading(false);
        }
    }, []);

    useEffect(() => {
        if (isOpen) {
            setSuche('');
            setProzessFilter(new Set());
            setWerkstoffFilter(new Set());
            setQuelleFilter(new Set());
            setGueltigkeit('ALLE');
            setSelectedIds(new Set(initialSelectedIds));
            loadWps();
        }
        return () => {
            if (abortRef.current) abortRef.current.abort();
        };
        // initialSelectedIds bewusst aus dem Dep-Array, sonst reset bei jeder Parent-Re-Render-Runde
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [isOpen, loadWps]);

    // Distinct-Werte für Filter-Chips aus geladener Gesamtliste.
    const prozessOptionen = useMemo(
        () => Array.from(new Set(alle.map(w => (w.schweissProzes || '').trim()).filter(Boolean))).sort(),
        [alle],
    );
    const werkstoffOptionen = useMemo(
        () => Array.from(new Set(alle.map(w => (w.grundwerkstoff || '').trim()).filter(Boolean))).sort(),
        [alle],
    );

    const heute = useMemo(() => new Date().toISOString().slice(0, 10), []);

    const istAbgelaufen = (w: WpsSuchErgebnis) =>
        w.gueltigBis != null && w.gueltigBis < heute;

    const gefiltert = useMemo(() => {
        const q = suche.trim().toLowerCase();
        return alle.filter(w => {
            if (prozessFilter.size > 0 && !prozessFilter.has((w.schweissProzes || '').trim())) return false;
            if (werkstoffFilter.size > 0 && !werkstoffFilter.has((w.grundwerkstoff || '').trim())) return false;
            if (quelleFilter.size > 0 && !quelleFilter.has(String(w.quelle || 'EDITOR'))) return false;
            if (gueltigkeit === 'NUR_GUELTIG' && istAbgelaufen(w)) return false;
            if (gueltigkeit === 'NUR_ABGELAUFEN' && !istAbgelaufen(w)) return false;
            if (q) {
                const hay = [
                    w.wpsNummer, w.bezeichnung, w.norm, w.schweissProzes,
                    w.grundwerkstoff, w.zusatzwerkstoff, w.nahtart,
                ].filter(Boolean).join(' ').toLowerCase();
                if (!hay.includes(q)) return false;
            }
            return true;
        });
    }, [alle, suche, prozessFilter, werkstoffFilter, quelleFilter, gueltigkeit, heute]);

    const toggleSelect = (id: number) => {
        setSelectedIds(prev => {
            const next = new Set(prev);
            if (next.has(id)) next.delete(id); else next.add(id);
            return next;
        });
    };

    const toggleFilter = (setter: (updater: (prev: Set<string>) => Set<string>) => void, value: string) => {
        setter(prev => {
            const next = new Set(prev);
            if (next.has(value)) next.delete(value); else next.add(value);
            return next;
        });
    };

    const alleSichtbarAuswaehlen = () => {
        setSelectedIds(prev => {
            const next = new Set(prev);
            gefiltert.forEach(w => next.add(w.id));
            return next;
        });
    };

    const sichtbareAbwaehlen = () => {
        setSelectedIds(prev => {
            const next = new Set(prev);
            gefiltert.forEach(w => next.delete(w.id));
            return next;
        });
    };

    const handleConfirm = () => {
        const ausgewaehlt = alle.filter(w => selectedIds.has(w.id));
        onConfirm(ausgewaehlt);
        onClose();
    };

    const resetFilter = () => {
        setProzessFilter(new Set());
        setWerkstoffFilter(new Set());
        setQuelleFilter(new Set());
        setGueltigkeit('ALLE');
        setSuche('');
    };

    const aktiveFilterCount =
        prozessFilter.size + werkstoffFilter.size + quelleFilter.size
        + (gueltigkeit !== 'ALLE' ? 1 : 0) + (suche.trim() ? 1 : 0);

    if (!isOpen) return null;

    return (
        <div className="fixed inset-0 bg-black/50 z-[60] flex items-center justify-center p-4">
            <div className="bg-white rounded-xl shadow-2xl w-full max-w-4xl max-h-[85vh] flex flex-col overflow-hidden animate-in zoom-in-95 duration-200">
                {/* Header */}
                <div className="p-4 border-b border-slate-200">
                    <div className="flex items-center justify-between mb-3">
                        <div className="flex items-center gap-2">
                            <Flame className="w-5 h-5 text-rose-600" />
                            <h2 className="text-lg font-bold text-slate-900">Schweißanweisungen auswählen</h2>
                            {selectedIds.size > 0 && (
                                <span className="ml-2 text-xs bg-rose-100 text-rose-700 px-2 py-0.5 rounded-full font-medium">
                                    {selectedIds.size} ausgewählt
                                </span>
                            )}
                        </div>
                        <button
                            onClick={onClose}
                            className="p-1.5 hover:bg-slate-100 rounded-full transition-colors"
                            aria-label="Schließen"
                        >
                            <X className="w-5 h-5 text-slate-500" />
                        </button>
                    </div>

                    {/* Suchfeld */}
                    <div className="relative">
                        <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-slate-400" />
                        <input
                            type="text"
                            value={suche}
                            onChange={e => setSuche(e.target.value)}
                            placeholder="Suche nach Nummer, Bezeichnung, Prozess, Werkstoff, Naht..."
                            className="w-full pl-10 pr-10 py-2.5 bg-slate-50 border border-slate-200 rounded-lg text-slate-900 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-rose-500 focus:border-rose-500"
                            autoFocus
                        />
                        {loading && (
                            <Loader2 className="absolute right-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400 animate-spin" />
                        )}
                    </div>
                </div>

                {/* Filter-Bar */}
                <div className="px-4 py-3 bg-slate-50 border-b border-slate-200 space-y-2">
                    <div className="flex items-center gap-2 flex-wrap">
                        <Filter className="w-4 h-4 text-slate-500" />
                        <span className="text-xs font-medium text-slate-600 uppercase tracking-wide">Prozess</span>
                        {prozessOptionen.length === 0 && (
                            <span className="text-xs text-slate-400">(keine Daten)</span>
                        )}
                        {prozessOptionen.map(p => {
                            const aktiv = prozessFilter.has(p);
                            return (
                                <button
                                    key={p}
                                    onClick={() => toggleFilter(setProzessFilter, p)}
                                    className={`px-2.5 py-1 rounded-full text-xs font-medium border transition-colors ${aktiv
                                        ? 'bg-rose-600 text-white border-rose-600'
                                        : 'bg-white text-slate-600 border-slate-200 hover:border-rose-300'}`}
                                >
                                    {p}
                                </button>
                            );
                        })}
                    </div>
                    <div className="flex items-center gap-2 flex-wrap">
                        <span className="w-4" />
                        <span className="text-xs font-medium text-slate-600 uppercase tracking-wide">Werkstoff</span>
                        {werkstoffOptionen.length === 0 && (
                            <span className="text-xs text-slate-400">(keine Daten)</span>
                        )}
                        {werkstoffOptionen.map(w => {
                            const aktiv = werkstoffFilter.has(w);
                            return (
                                <button
                                    key={w}
                                    onClick={() => toggleFilter(setWerkstoffFilter, w)}
                                    className={`px-2.5 py-1 rounded-full text-xs font-medium border transition-colors ${aktiv
                                        ? 'bg-rose-600 text-white border-rose-600'
                                        : 'bg-white text-slate-600 border-slate-200 hover:border-rose-300'}`}
                                >
                                    {w}
                                </button>
                            );
                        })}
                    </div>
                    <div className="flex items-center gap-2 flex-wrap">
                        <span className="w-4" />
                        <span className="text-xs font-medium text-slate-600 uppercase tracking-wide">Quelle</span>
                        {(['EDITOR', 'UPLOAD'] as const).map(q => {
                            const aktiv = quelleFilter.has(q);
                            const label = q === 'EDITOR' ? 'Editor' : 'PDF-Import';
                            return (
                                <button
                                    key={q}
                                    onClick={() => toggleFilter(setQuelleFilter, q)}
                                    className={`px-2.5 py-1 rounded-full text-xs font-medium border transition-colors ${aktiv
                                        ? 'bg-rose-600 text-white border-rose-600'
                                        : 'bg-white text-slate-600 border-slate-200 hover:border-rose-300'}`}
                                >
                                    {label}
                                </button>
                            );
                        })}
                        <span className="w-2" />
                        <span className="text-xs font-medium text-slate-600 uppercase tracking-wide">Gültigkeit</span>
                        {(['ALLE', 'NUR_GUELTIG', 'NUR_ABGELAUFEN'] as const).map(g => {
                            const aktiv = gueltigkeit === g;
                            const label = g === 'ALLE' ? 'Alle' : g === 'NUR_GUELTIG' ? 'Nur gültige' : 'Nur abgelaufene';
                            return (
                                <button
                                    key={g}
                                    onClick={() => setGueltigkeit(g)}
                                    className={`px-2.5 py-1 rounded-full text-xs font-medium border transition-colors ${aktiv
                                        ? 'bg-rose-600 text-white border-rose-600'
                                        : 'bg-white text-slate-600 border-slate-200 hover:border-rose-300'}`}
                                >
                                    {label}
                                </button>
                            );
                        })}
                        {aktiveFilterCount > 0 && (
                            <button
                                onClick={resetFilter}
                                className="ml-auto text-xs text-slate-500 hover:text-rose-600 underline"
                            >
                                Filter zurücksetzen
                            </button>
                        )}
                    </div>
                </div>

                {/* Bulk-Aktionen */}
                <div className="px-4 py-2 bg-white border-b border-slate-200 flex items-center gap-3 text-sm">
                    <button
                        onClick={alleSichtbarAuswaehlen}
                        className="inline-flex items-center gap-1.5 text-slate-600 hover:text-rose-600 font-medium"
                        disabled={gefiltert.length === 0}
                    >
                        <CheckSquare className="w-4 h-4" />
                        Alle {gefiltert.length} markieren
                    </button>
                    <span className="text-slate-300">·</span>
                    <button
                        onClick={sichtbareAbwaehlen}
                        className="inline-flex items-center gap-1.5 text-slate-600 hover:text-rose-600 font-medium"
                        disabled={gefiltert.length === 0}
                    >
                        <Square className="w-4 h-4" />
                        Sichtbare abwählen
                    </button>
                    <span className="ml-auto text-slate-500 text-xs">
                        {gefiltert.length} von {alle.length} Schweißanweisungen
                    </span>
                </div>

                {/* Liste */}
                <div className="flex-1 overflow-y-auto">
                    {fehler ? (
                        <div className="text-center py-12 text-rose-600">
                            <AlertTriangle className="w-8 h-8 mx-auto mb-2" />
                            <p className="font-medium">{fehler}</p>
                            <button
                                onClick={loadWps}
                                className="mt-3 text-sm underline hover:text-rose-700"
                            >
                                Neu laden
                            </button>
                        </div>
                    ) : loading && alle.length === 0 ? (
                        <div className="text-center py-12 text-slate-400">
                            <Loader2 className="w-8 h-8 mx-auto mb-2 animate-spin opacity-50" />
                            <p>Schweißanweisungen werden geladen...</p>
                        </div>
                    ) : gefiltert.length === 0 ? (
                        <div className="text-center py-12 text-slate-400">
                            <Flame className="w-10 h-10 mx-auto mb-2 opacity-30" />
                            <p>Keine Schweißanweisung passt zu den Filtern.</p>
                        </div>
                    ) : (
                        <div className="divide-y divide-slate-100">
                            {gefiltert.map(w => {
                                const selected = selectedIds.has(w.id);
                                const abgelaufen = istAbgelaufen(w);
                                return (
                                    <button
                                        key={w.id}
                                        onClick={() => toggleSelect(w.id)}
                                        className={`w-full flex items-start gap-3 p-3.5 text-left transition-colors
                                            ${selected
                                                ? 'bg-rose-50 border-l-4 border-rose-500'
                                                : 'hover:bg-slate-50 border-l-4 border-transparent'}`}
                                    >
                                        <div className={`w-5 h-5 mt-0.5 rounded border-2 flex-shrink-0 flex items-center justify-center
                                            ${selected ? 'bg-rose-600 border-rose-600' : 'border-slate-300'}`}>
                                            {selected && <Check className="w-3.5 h-3.5 text-white" />}
                                        </div>
                                        <div className="flex-1 min-w-0">
                                            <div className="flex items-center gap-2 flex-wrap">
                                                <span className={`font-semibold ${selected ? 'text-rose-700' : 'text-slate-900'}`}>
                                                    {w.wpsNummer}
                                                </span>
                                                {w.bezeichnung && (
                                                    <span className="text-slate-600 truncate">— {w.bezeichnung}</span>
                                                )}
                                                {w.quelle === 'UPLOAD' && (
                                                    <span className="text-[10px] bg-amber-100 text-amber-700 px-1.5 py-0.5 rounded font-medium">
                                                        PDF-Import
                                                    </span>
                                                )}
                                                {abgelaufen && (
                                                    <span className="text-[10px] bg-red-100 text-red-700 px-1.5 py-0.5 rounded font-medium">
                                                        abgelaufen
                                                    </span>
                                                )}
                                            </div>
                                            <div className="flex items-center gap-3 text-xs text-slate-500 mt-1 flex-wrap">
                                                {w.schweissProzes && (
                                                    <span><span className="text-slate-400">Prozess:</span> {w.schweissProzes}</span>
                                                )}
                                                {w.grundwerkstoff && (
                                                    <span><span className="text-slate-400">Werkstoff:</span> {w.grundwerkstoff}</span>
                                                )}
                                                {w.nahtart && (
                                                    <span><span className="text-slate-400">Naht:</span> {w.nahtart}</span>
                                                )}
                                                {w.gueltigBis && (
                                                    <span className={abgelaufen ? 'text-red-600' : ''}>
                                                        <span className="text-slate-400">gültig bis:</span> {w.gueltigBis}
                                                    </span>
                                                )}
                                            </div>
                                        </div>
                                    </button>
                                );
                            })}
                        </div>
                    )}
                </div>

                {/* Footer */}
                <div className="px-4 py-3 border-t border-slate-200 bg-slate-50 flex items-center gap-3">
                    <span className="text-sm text-slate-500">
                        {selectedIds.size === 0
                            ? 'Keine Auswahl'
                            : `${selectedIds.size} Schweißanweisung${selectedIds.size === 1 ? '' : 'en'} markiert`}
                    </span>
                    <div className="ml-auto flex items-center gap-2">
                        <button
                            onClick={onClose}
                            className="px-4 py-2 text-sm font-medium text-slate-600 hover:bg-slate-100 rounded-lg transition-colors"
                        >
                            Abbrechen
                        </button>
                        <button
                            onClick={handleConfirm}
                            disabled={selectedIds.size === 0}
                            className="inline-flex items-center gap-1.5 px-4 py-2 text-sm font-medium bg-rose-600 text-white rounded-lg hover:bg-rose-700 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
                        >
                            <ShieldCheck className="w-4 h-4" />
                            Übernehmen
                        </button>
                    </div>
                </div>
            </div>
        </div>
    );
}
