import { useCallback, useEffect, useRef, useState } from 'react';
import { Calendar, ChevronRight, FileText, Loader2, Search, X } from 'lucide-react';
import type { Anfrage } from '../types';

interface AnfrageSearchModalProps {
    isOpen: boolean;
    onClose: () => void;
    onSelect: (anfrage: Anfrage) => void;
    kundenId?: number;
}

/**
 * Suche für offene Anfragen eines bestimmten Kunden.
 * Aufbau und Suchverhalten entsprechen dem ProjektSearchModal.
 */
export function AnfrageSearchModal({
    isOpen,
    onClose,
    onSelect,
    kundenId,
}: AnfrageSearchModalProps) {
    const [searchTerm, setSearchTerm] = useState('');
    const [anfragen, setAnfragen] = useState<Anfrage[]>([]);
    const [totalCount, setTotalCount] = useState(0);
    const [loading, setLoading] = useState(false);
    const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);
    const abortRef = useRef<AbortController | null>(null);

    const loadAnfragen = useCallback(async (query: string) => {
        if (abortRef.current) abortRef.current.abort();
        const controller = new AbortController();
        abortRef.current = controller;

        setLoading(true);
        try {
            const params = new URLSearchParams({ nurOhneProjekt: 'true' });
            if (query.trim()) params.set('q', query.trim());

            const res = await fetch(`/api/anfragen?${params}`, { signal: controller.signal });
            if (!res.ok) throw new Error('Fehler beim Laden');

            const data = await res.json();
            const alleAnfragen: Anfrage[] = Array.isArray(data) ? data : data.anfragen || [];
            const passendeAnfragen = kundenId == null
                ? []
                : alleAnfragen.filter(anfrage => anfrage.kundenId === kundenId);
            setAnfragen(passendeAnfragen);
            if (!query.trim()) setTotalCount(passendeAnfragen.length);
        } catch (error) {
            if (!(error instanceof DOMException && error.name === 'AbortError')) {
                console.error('Anfragesuche fehlgeschlagen:', error);
                setAnfragen([]);
            }
        } finally {
            if (!controller.signal.aborted) setLoading(false);
        }
    }, [kundenId]);

    useEffect(() => {
        if (isOpen) {
            setSearchTerm('');
            loadAnfragen('');
        }
        return () => {
            if (abortRef.current) abortRef.current.abort();
        };
    }, [isOpen, loadAnfragen]);

    useEffect(() => {
        if (!isOpen) return;
        if (debounceRef.current) clearTimeout(debounceRef.current);
        debounceRef.current = setTimeout(() => loadAnfragen(searchTerm), 300);
        return () => {
            if (debounceRef.current) clearTimeout(debounceRef.current);
        };
    }, [searchTerm, isOpen, loadAnfragen]);

    const handleSelect = (anfrage: Anfrage) => {
        onSelect(anfrage);
        setSearchTerm('');
        onClose();
    };

    if (!isOpen) return null;

    return (
        <div className="fixed inset-0 bg-black/50 z-[60] flex items-center justify-center p-4">
            <div className="bg-white rounded-xl shadow-2xl w-full max-w-2xl max-h-[80vh] flex flex-col overflow-hidden animate-in zoom-in-95 duration-200">
                <div className="p-4 border-b border-slate-200">
                    <div className="flex items-center justify-between mb-3">
                        <div className="flex items-center gap-2">
                            <FileText className="w-5 h-5 text-rose-600" />
                            <h2 className="text-lg font-bold text-slate-900">Anfrage auswählen</h2>
                        </div>
                        <button
                            onClick={onClose}
                            className="p-1.5 hover:bg-slate-100 rounded-full transition-colors"
                            aria-label="Anfragesuche schließen"
                        >
                            <X className="w-5 h-5 text-slate-500" />
                        </button>
                    </div>

                    <div className="relative">
                        <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-slate-400" />
                        <input
                            type="text"
                            value={searchTerm}
                            onChange={(event) => setSearchTerm(event.target.value)}
                            placeholder="Freitext suchen (Bauvorhaben, Kunde, Ansprechpartner, Anfragenr.)..."
                            className="w-full pl-10 pr-10 py-2.5 bg-slate-50 border border-slate-200 rounded-lg text-slate-900 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-rose-500 focus:border-rose-500"
                            autoFocus
                        />
                        {loading && (
                            <Loader2 className="absolute right-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400 animate-spin" />
                        )}
                    </div>
                </div>

                <div className="flex-1 overflow-y-auto">
                    {loading && anfragen.length === 0 ? (
                        <div className="text-center py-12 text-slate-400">
                            <Loader2 className="w-8 h-8 mx-auto mb-2 animate-spin opacity-50" />
                            <p>Anfragen werden geladen...</p>
                        </div>
                    ) : anfragen.length === 0 ? (
                        <div className="text-center py-12 text-slate-400">
                            <FileText className="w-10 h-10 mx-auto mb-2 opacity-30" />
                            <p>
                                {searchTerm
                                    ? 'Keine passende Anfrage desselben Kunden gefunden'
                                    : 'Keine offene Anfrage desselben Kunden verfügbar'}
                            </p>
                        </div>
                    ) : (
                        <div className="divide-y divide-slate-100">
                            {anfragen.map(anfrage => (
                                <button
                                    key={anfrage.id}
                                    onClick={() => handleSelect(anfrage)}
                                    className="w-full flex items-center gap-4 p-4 text-left transition-colors group hover:bg-slate-50 border-l-4 border-transparent hover:border-rose-300"
                                >
                                    <div className="w-10 h-10 rounded-lg flex items-center justify-center flex-shrink-0 bg-slate-100 group-hover:bg-rose-100">
                                        <FileText className="w-5 h-5 text-slate-500 group-hover:text-rose-600" />
                                    </div>
                                    <div className="flex-1 min-w-0">
                                        <p className="font-medium text-slate-900 truncate">
                                            {anfrage.bauvorhaben || 'Unbenannte Anfrage'}
                                        </p>
                                        <div className="flex items-center gap-2 text-sm text-slate-500">
                                            {anfrage.anfragesnummer && (
                                                <span className="font-mono bg-slate-100 px-1.5 py-0.5 rounded text-xs">
                                                    {anfrage.anfragesnummer}
                                                </span>
                                            )}
                                            {anfrage.anlegedatum && (
                                                <span className="inline-flex items-center gap-1">
                                                    <Calendar className="w-3.5 h-3.5" />
                                                    {new Date(anfrage.anlegedatum).toLocaleDateString('de-DE')}
                                                </span>
                                            )}
                                            {anfrage.kundenName && (
                                                <span className="truncate">{anfrage.kundenName}</span>
                                            )}
                                        </div>
                                    </div>
                                    <ChevronRight className="w-5 h-5 flex-shrink-0 text-slate-300 group-hover:text-rose-400" />
                                </button>
                            ))}
                        </div>
                    )}
                </div>

                <div className="px-4 py-3 border-t border-slate-200 bg-slate-50 text-sm text-slate-500">
                    {anfragen.length}
                    {totalCount > 0 && searchTerm.trim() ? ` von ${totalCount}` : ''} Anfragen
                </div>
            </div>
        </div>
    );
}
