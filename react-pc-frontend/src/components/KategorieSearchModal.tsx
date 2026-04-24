import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
    ChevronRight,
    ChevronDown,
    Folder,
    FolderOpen,
    Loader2,
    Search,
    X,
} from 'lucide-react';
import { cn } from '../lib/utils';

export interface KategorieSuchErgebnis {
    id: number;
    beschreibung: string;
    parentId: number | null;
}

interface KategorieSearchModalProps {
    isOpen: boolean;
    onClose: () => void;
    onSelect: (kategorie: KategorieSuchErgebnis, pfad: string) => void;
    currentKategorieId?: number | null;
}

type KategorieDto = { id: number; beschreibung: string; parentId: number | null };

/**
 * Modal zur Kategorie-Auswahl analog zu {@link LieferantSearchModal} und {@link ProjektSearchModal}.
 * - Tree-Ansicht mit Auf-/Zuklappen
 * - Freitext-Suche: bei aktiver Suche wird eine flache Trefferliste mit vollem Pfad gezeigt
 */
export function KategorieSearchModal({
    isOpen,
    onClose,
    onSelect,
    currentKategorieId,
}: KategorieSearchModalProps) {
    const [kategorien, setKategorien] = useState<KategorieDto[]>([]);
    const [loading, setLoading] = useState(false);
    const [searchTerm, setSearchTerm] = useState('');
    const [expandedIds, setExpandedIds] = useState<Set<number>>(new Set());
    const abortRef = useRef<AbortController | null>(null);

    const loadKategorien = useCallback(async () => {
        if (abortRef.current) abortRef.current.abort();
        const controller = new AbortController();
        abortRef.current = controller;
        setLoading(true);
        try {
            const res = await fetch('/api/kategorien', { signal: controller.signal });
            if (!res.ok) throw new Error(`HTTP ${res.status}`);
            const data = await res.json();
            setKategorien(Array.isArray(data) ? data : []);
        } catch (e) {
            if (!(e instanceof DOMException && e.name === 'AbortError')) {
                console.error('Kategoriesuche fehlgeschlagen:', e);
            }
        } finally {
            if (!controller.signal.aborted) setLoading(false);
        }
    }, []);

    useEffect(() => {
        if (isOpen) {
            setSearchTerm('');
            setExpandedIds(new Set());
            loadKategorien();
        }
        return () => {
            if (abortRef.current) abortRef.current.abort();
        };
    }, [isOpen, loadKategorien]);

    // id → Kategorie (für Pfad-Auflösung)
    const byId = useMemo(() => {
        const m = new Map<number, KategorieDto>();
        kategorien.forEach(k => m.set(k.id, k));
        return m;
    }, [kategorien]);

    // parentId → Kinder (für Tree-Rendering)
    const tree = useMemo(() => {
        const m = new Map<number | null, KategorieDto[]>();
        kategorien.forEach(k => {
            const list = m.get(k.parentId) ?? [];
            list.push(k);
            m.set(k.parentId, list);
        });
        m.forEach(list => list.sort((a, b) => a.beschreibung.localeCompare(b.beschreibung, 'de')));
        return m;
    }, [kategorien]);

    const buildPfad = useCallback((id: number): string => {
        const pfad: string[] = [];
        let aktuell: KategorieDto | undefined = byId.get(id);
        let guard = 0;
        while (aktuell && guard++ < 20) {
            pfad.unshift(aktuell.beschreibung);
            aktuell = aktuell.parentId != null ? byId.get(aktuell.parentId) : undefined;
        }
        return pfad.join(' › ');
    }, [byId]);

    const toggleExpand = (id: number) => {
        setExpandedIds(prev => {
            const next = new Set(prev);
            if (next.has(id)) next.delete(id);
            else next.add(id);
            return next;
        });
    };

    const handleSelect = (k: KategorieDto) => {
        onSelect({ id: k.id, beschreibung: k.beschreibung, parentId: k.parentId }, buildPfad(k.id));
        onClose();
    };

    const sucheAktiv = searchTerm.trim().length > 0;
    const trefferFlach = useMemo(() => {
        if (!sucheAktiv) return [];
        const q = searchTerm.trim().toLowerCase();
        return kategorien
            .filter(k => k.beschreibung.toLowerCase().includes(q))
            .sort((a, b) => a.beschreibung.localeCompare(b.beschreibung, 'de'));
    }, [kategorien, searchTerm, sucheAktiv]);

    const renderTree = (parentId: number | null, depth: number): React.ReactNode => {
        const kinder = tree.get(parentId) ?? [];
        if (kinder.length === 0) return null;
        return (
            <ul className="space-y-0.5">
                {kinder.map(k => {
                    const hatKinder = (tree.get(k.id) ?? []).length > 0;
                    const offen = expandedIds.has(k.id);
                    const gewaehlt = currentKategorieId === k.id;
                    return (
                        <li key={k.id}>
                            <div
                                className={cn(
                                    'group flex items-center gap-1 rounded-md text-sm cursor-pointer transition-colors',
                                    gewaehlt
                                        ? 'bg-rose-50 text-rose-800 font-medium'
                                        : 'hover:bg-slate-50 text-slate-700',
                                )}
                                style={{ paddingLeft: `${4 + depth * 16}px` }}
                            >
                                {hatKinder ? (
                                    <button
                                        type="button"
                                        onClick={() => toggleExpand(k.id)}
                                        className="p-1 rounded hover:bg-slate-200 shrink-0"
                                        aria-label={offen ? 'Einklappen' : 'Ausklappen'}
                                    >
                                        {offen ? (
                                            <ChevronDown className="w-3.5 h-3.5 text-slate-500" />
                                        ) : (
                                            <ChevronRight className="w-3.5 h-3.5 text-slate-400" />
                                        )}
                                    </button>
                                ) : (
                                    <span className="w-5 h-5 shrink-0" />
                                )}
                                <button
                                    type="button"
                                    onClick={() => handleSelect(k)}
                                    className="flex items-center gap-2 py-1.5 flex-1 min-w-0 text-left"
                                >
                                    {hatKinder ? (
                                        offen ? (
                                            <FolderOpen className="w-4 h-4 text-rose-500 shrink-0" />
                                        ) : (
                                            <Folder className="w-4 h-4 text-slate-400 shrink-0" />
                                        )
                                    ) : (
                                        <Folder className="w-4 h-4 text-slate-300 shrink-0" />
                                    )}
                                    <span className="truncate">{k.beschreibung}</span>
                                </button>
                            </div>
                            {hatKinder && offen && renderTree(k.id, depth + 1)}
                        </li>
                    );
                })}
            </ul>
        );
    };

    if (!isOpen) return null;

    return (
        <div className="fixed inset-0 bg-black/50 z-[70] flex items-center justify-center p-4">
            <div className="bg-white rounded-xl shadow-2xl w-full max-w-2xl max-h-[80vh] flex flex-col overflow-hidden animate-in zoom-in-95 duration-200">
                {/* Header */}
                <div className="p-4 border-b border-slate-200">
                    <div className="flex items-center justify-between mb-3">
                        <div className="flex items-center gap-2">
                            <Folder className="w-5 h-5 text-rose-600" />
                            <h2 className="text-lg font-bold text-slate-900">Kategorie auswählen</h2>
                        </div>
                        <button
                            onClick={onClose}
                            className="p-1.5 hover:bg-slate-100 rounded-full transition-colors"
                            aria-label="Schließen"
                        >
                            <X className="w-5 h-5 text-slate-500" />
                        </button>
                    </div>

                    {/* Suche */}
                    <div className="relative">
                        <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-slate-400" />
                        <input
                            type="text"
                            value={searchTerm}
                            onChange={e => setSearchTerm(e.target.value)}
                            placeholder="Kategorie suchen..."
                            className="w-full pl-10 pr-10 py-2.5 bg-slate-50 border border-slate-200 rounded-lg text-slate-900 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-rose-500 focus:border-rose-500"
                            autoFocus
                        />
                        {loading && (
                            <Loader2 className="absolute right-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400 animate-spin" />
                        )}
                    </div>
                </div>

                {/* Inhalt */}
                <div className="flex-1 overflow-y-auto p-3">
                    {loading && kategorien.length === 0 ? (
                        <div className="text-center py-12 text-slate-400">
                            <Loader2 className="w-8 h-8 mx-auto mb-2 animate-spin opacity-50" />
                            <p>Kategorien werden geladen...</p>
                        </div>
                    ) : kategorien.length === 0 ? (
                        <div className="text-center py-12 text-slate-400">
                            <Folder className="w-10 h-10 mx-auto mb-2 opacity-30" />
                            <p>Keine Kategorien verfügbar</p>
                        </div>
                    ) : sucheAktiv ? (
                        trefferFlach.length === 0 ? (
                            <div className="text-center py-12 text-slate-400">
                                <Folder className="w-10 h-10 mx-auto mb-2 opacity-30" />
                                <p>Keine Treffer</p>
                            </div>
                        ) : (
                            <ul className="divide-y divide-slate-100">
                                {trefferFlach.map(k => {
                                    const gewaehlt = currentKategorieId === k.id;
                                    return (
                                        <li key={k.id}>
                                            <button
                                                type="button"
                                                onClick={() => handleSelect(k)}
                                                className={cn(
                                                    'w-full text-left flex items-center gap-3 px-3 py-2.5 rounded-md transition-colors',
                                                    gewaehlt
                                                        ? 'bg-rose-50 text-rose-800'
                                                        : 'hover:bg-slate-50 text-slate-700',
                                                )}
                                            >
                                                <Folder className="w-4 h-4 text-slate-400 shrink-0" />
                                                <div className="min-w-0">
                                                    <p className={cn('font-medium truncate', gewaehlt && 'text-rose-700')}>
                                                        {k.beschreibung}
                                                    </p>
                                                    <p className="text-xs text-slate-500 truncate">
                                                        {buildPfad(k.id)}
                                                    </p>
                                                </div>
                                            </button>
                                        </li>
                                    );
                                })}
                            </ul>
                        )
                    ) : (
                        renderTree(null, 0)
                    )}
                </div>

                {/* Footer */}
                <div className="px-4 py-3 border-t border-slate-200 bg-slate-50 text-sm text-slate-500">
                    {sucheAktiv
                        ? `${trefferFlach.length} Treffer`
                        : `${kategorien.length} Kategorien`}
                </div>
            </div>
        </div>
    );
}
