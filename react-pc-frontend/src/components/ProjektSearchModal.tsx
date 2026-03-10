import { useState, useEffect, useRef, useCallback } from 'react';
import { Search, X, Briefcase, ChevronRight, Loader2 } from 'lucide-react';

interface Projekt {
    id: number;
    bauvorhaben: string;
    auftragsnummer?: string;
    kunde?: string;
    abgeschlossen?: boolean;
}

interface ProjektSearchModalProps {
    isOpen: boolean;
    onClose: () => void;
    onSelect: (projekt: Projekt) => void;
    /** @deprecated Projekte werden jetzt serverseitig geladen */
    projekte?: Projekt[];
    currentProjektId?: number;
    /** Nur offene (nicht abgeschlossene) Projekte anzeigen */
    nurOffene?: boolean;
}

/**
 * Modal für Projektsuche mit server-seitiger Suche.
 * Sucht über Bauvorhaben, Auftragsnummer, Kundenname und Ansprechpartner.
 */
export function ProjektSearchModal({
    isOpen,
    onClose,
    onSelect,
    currentProjektId,
    nurOffene = false
}: ProjektSearchModalProps) {
    const [searchTerm, setSearchTerm] = useState('');
    const [projekte, setProjekte] = useState<Projekt[]>([]);
    const [totalCount, setTotalCount] = useState(0);
    const [loading, setLoading] = useState(false);
    const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);
    const abortRef = useRef<AbortController | null>(null);

    // Projekte vom Server laden
    const loadProjekte = useCallback(async (query: string) => {
        // Vorherigen Request abbrechen
        if (abortRef.current) abortRef.current.abort();
        const controller = new AbortController();
        abortRef.current = controller;

        setLoading(true);
        try {
            const params = new URLSearchParams({ size: '500' });
            if (query.trim()) params.set('q', query.trim());
            if (nurOffene) params.set('nurOffene', 'true');
            const res = await fetch(`/api/projekte/simple?${params}`, { signal: controller.signal });
            if (!res.ok) throw new Error('Fehler beim Laden');
            const data: Projekt[] = await res.json();
            setProjekte(data);
            // Beim ersten Laden (ohne Query) die Gesamtanzahl merken
            if (!query.trim()) setTotalCount(data.length);
        } catch (e: any) {
            if (e.name !== 'AbortError') {
                console.error('Projektsuche fehlgeschlagen:', e);
            }
        } finally {
            if (!controller.signal.aborted) setLoading(false);
        }
    }, [nurOffene]);

    // Initiales Laden wenn Modal geöffnet wird
    useEffect(() => {
        if (isOpen) {
            setSearchTerm('');
            loadProjekte('');
        }
        return () => {
            if (abortRef.current) abortRef.current.abort();
        };
    }, [isOpen, loadProjekte]);

    // Debounced Suche bei Eingabe
    useEffect(() => {
        if (!isOpen) return;
        if (debounceRef.current) clearTimeout(debounceRef.current);
        debounceRef.current = setTimeout(() => {
            loadProjekte(searchTerm);
        }, 300);
        return () => {
            if (debounceRef.current) clearTimeout(debounceRef.current);
        };
    }, [searchTerm, isOpen, loadProjekte]);

    const handleSelect = (projekt: Projekt) => {
        onSelect(projekt);
        setSearchTerm('');
        onClose();
    };

    if (!isOpen) return null;

    return (
        <div className="fixed inset-0 bg-black/50 z-[60] flex items-center justify-center p-4">
            <div className="bg-white rounded-xl shadow-2xl w-full max-w-2xl max-h-[80vh] flex flex-col overflow-hidden animate-in zoom-in-95 duration-200">
                {/* Header mit Suche */}
                <div className="p-4 border-b border-slate-200">
                    <div className="flex items-center justify-between mb-3">
                        <div className="flex items-center gap-2">
                            <Briefcase className="w-5 h-5 text-rose-600" />
                            <h2 className="text-lg font-bold text-slate-900">Projekt auswählen</h2>
                        </div>
                        <button
                            onClick={onClose}
                            className="p-1.5 hover:bg-slate-100 rounded-full transition-colors"
                        >
                            <X className="w-5 h-5 text-slate-500" />
                        </button>
                    </div>

                    {/* Suchfeld */}
                    <div className="relative">
                        <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-slate-400" />
                        <input
                            type="text"
                            value={searchTerm}
                            onChange={(e) => setSearchTerm(e.target.value)}
                            placeholder="Freitext suchen (Bauvorhaben, Kunde, Ansprechpartner, Auftragsnr.)..."
                            className="w-full pl-10 pr-10 py-2.5 bg-slate-50 border border-slate-200 rounded-lg text-slate-900 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-rose-500 focus:border-rose-500"
                            autoFocus
                        />
                        {loading && (
                            <Loader2 className="absolute right-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400 animate-spin" />
                        )}
                    </div>
                </div>

                {/* Liste */}
                <div className="flex-1 overflow-y-auto">
                    {loading && projekte.length === 0 ? (
                        <div className="text-center py-12 text-slate-400">
                            <Loader2 className="w-8 h-8 mx-auto mb-2 animate-spin opacity-50" />
                            <p>Projekte werden geladen...</p>
                        </div>
                    ) : projekte.length === 0 ? (
                        <div className="text-center py-12 text-slate-400">
                            <Briefcase className="w-10 h-10 mx-auto mb-2 opacity-30" />
                            <p>{searchTerm ? 'Keine Projekte gefunden' : 'Keine Projekte verfügbar'}</p>
                        </div>
                    ) : (
                        <div className="divide-y divide-slate-100">
                            {projekte.map(projekt => {
                                const isSelected = projekt.id === currentProjektId;
                                return (
                                    <button
                                        key={projekt.id}
                                        onClick={() => handleSelect(projekt)}
                                        className={`w-full flex items-center gap-4 p-4 text-left transition-colors group
                                            ${isSelected
                                                ? 'bg-rose-50 border-l-4 border-rose-500'
                                                : 'hover:bg-slate-50 border-l-4 border-transparent'
                                            }`}
                                    >
                                        <div className={`w-10 h-10 rounded-lg flex items-center justify-center flex-shrink-0
                                            ${isSelected ? 'bg-rose-100' : 'bg-slate-100 group-hover:bg-slate-200'}`}>
                                            <Briefcase className={`w-5 h-5 ${isSelected ? 'text-rose-600' : 'text-slate-500'}`} />
                                        </div>
                                        <div className="flex-1 min-w-0">
                                            <div className="flex items-center gap-2">
                                                <p className={`font-medium truncate ${isSelected ? 'text-rose-700' : 'text-slate-900'}`}>
                                                    {projekt.bauvorhaben || 'Unbenanntes Projekt'}
                                                </p>
                                                {projekt.abgeschlossen && (
                                                    <span className="text-xs bg-slate-200 text-slate-600 px-1.5 py-0.5 rounded flex-shrink-0">
                                                        Beendet
                                                    </span>
                                                )}
                                            </div>
                                            <div className="flex items-center gap-2 text-sm text-slate-500">
                                                {projekt.auftragsnummer && (
                                                    <span className="font-mono bg-slate-100 px-1.5 py-0.5 rounded text-xs">
                                                        {projekt.auftragsnummer}
                                                    </span>
                                                )}
                                                {projekt.kunde && (
                                                    <span className="truncate">{projekt.kunde}</span>
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

                {/* Footer mit Anzahl */}
                <div className="px-4 py-3 border-t border-slate-200 bg-slate-50 text-sm text-slate-500">
                    {projekte.length}{totalCount > 0 && searchTerm.trim() ? ` von ${totalCount}` : ''} Projekten
                </div>
            </div>
        </div>
    );
}
