import { AlertTriangle, Search, FileText, Wrench, Clock, X, Plus, Printer, Star, Folder, FolderOpen, ChevronDown, ChevronRight, Loader2 } from 'lucide-react';
import { Button } from '../ui/button';
import { useState, useMemo, useEffect, useCallback, useRef } from 'react';
import type { TextbausteinApiDto, LeistungApiDto, ArbeitszeitartApiDto } from './types';
import { AUSGANGS_GESCHAEFTSDOKUMENT_TYPEN } from '../../types';
import type { AusgangsGeschaeftsDokumentTyp, ProduktkategorieDto } from '../../types';
import { cn } from '../../lib/utils';

/** Export Warning Modal */
export function ExportWarningModal({
    onCancel,
    onConfirm,
}: {
    onCancel: () => void;
    onConfirm: () => void;
}) {
    return (
        <div className="fixed inset-0 z-[60] bg-black/40 backdrop-blur-sm flex items-center justify-center">
            <div className="bg-white rounded-2xl shadow-2xl p-6 max-w-md mx-4 border border-slate-100">
                <div className="flex items-start gap-4">
                    <div className="p-2.5 bg-amber-50 rounded-xl flex-shrink-0">
                        <AlertTriangle className="w-5 h-5 text-amber-500" />
                    </div>
                    <div>
                        <h3 className="text-base font-bold text-slate-900">Dokument exportieren?</h3>
                        <p className="text-sm text-slate-600 mt-2 leading-relaxed">
                            Nach dem Export wird das Dokument <strong>gebucht und gesperrt</strong>.
                            Es kann dann nicht mehr bearbeitet werden.
                        </p>
                        <p className="text-xs text-slate-400 mt-2">
                            Bei Fehlern ist nur eine Stornierung möglich.
                        </p>
                    </div>
                </div>
                <div className="flex gap-3 mt-6">
                    <Button
                        variant="outline"
                        onClick={onCancel}
                        className="flex-1 border-slate-200 text-slate-600"
                    >
                        Abbrechen
                    </Button>
                    <Button
                        onClick={onConfirm}
                        className="flex-1 bg-rose-600 hover:bg-rose-700 text-white shadow-sm"
                    >
                        Exportieren & Buchen
                    </Button>
                </div>
            </div>
        </div>
    );
}

/** Print Options Modal */
export function PrintOptionsModal({
    onCancel,
    onConfirm,
    allowFinalization = true,
}: {
    onCancel: () => void;
    onConfirm: (options: { withBackground: boolean; isFinal: boolean }) => void;
    allowFinalization?: boolean;
}) {
    const [withBackground, setWithBackground] = useState(false);

    return (
        <div className="fixed inset-0 z-[60] bg-black/40 backdrop-blur-sm flex items-center justify-center">
            <div className="bg-white rounded-2xl shadow-2xl p-6 max-w-md mx-4 border border-slate-100">
                <div className="flex items-start gap-4">
                    <div className="p-2.5 bg-rose-50 rounded-xl flex-shrink-0">
                        <Printer className="w-5 h-5 text-rose-600" />
                    </div>
                    <div className="flex-1">
                        <h3 className="text-base font-bold text-slate-900">Druckoptionen</h3>
                        <p className="text-sm text-slate-500 mt-1">Wählen Sie die gewünschten Einstellungen.</p>
                    </div>
                </div>

                <div className="mt-5 space-y-3">
                    {/* Background option */}
                    <label className="flex items-center gap-3 p-3 rounded-xl border border-slate-200 hover:border-rose-200 hover:bg-rose-50/50 transition-colors cursor-pointer">
                        <input
                            type="checkbox"
                            checked={withBackground}
                            onChange={(e) => setWithBackground(e.target.checked)}
                            className="w-4 h-4 rounded border-slate-300 text-rose-600 focus:ring-rose-500"
                        />
                        <div>
                            <p className="text-sm font-medium text-slate-700">Mit Hintergrund drucken</p>
                            <p className="text-xs text-slate-400">Briefkopf und Hintergrundbild einbeziehen</p>
                        </div>
                    </label>

                    {allowFinalization && (
                        <div className="flex items-start gap-2 p-2.5 bg-amber-50 rounded-lg border border-amber-100">
                            <AlertTriangle className="w-4 h-4 text-amber-500 flex-shrink-0 mt-0.5" />
                            <p className="text-xs text-amber-700 leading-relaxed">
                                Das Dokument wird beim Drucken automatisch <strong>gebucht und gesperrt</strong>. Änderungen sind danach nicht mehr möglich.
                            </p>
                        </div>
                    )}
                </div>

                <div className="flex gap-3 mt-6">
                    <Button
                        variant="outline"
                        onClick={onCancel}
                        className="flex-1 border-slate-200 text-slate-600"
                    >
                        Abbrechen
                    </Button>
                    <Button
                        onClick={() => onConfirm({ withBackground, isFinal: allowFinalization })}
                        className="flex-1 bg-rose-600 hover:bg-rose-700 text-white shadow-sm gap-1.5"
                    >
                        <Printer className="w-4 h-4" />
                        {allowFinalization ? 'Drucken & Buchen' : 'Drucken'}
                    </Button>
                </div>
            </div>
        </div>
    );
}

/** Unsaved Changes Warning Modal */
export function UnsavedChangesModal({
    onCancel,
    onDiscard,
    onSaveAndClose,
}: {
    onCancel: () => void;
    onDiscard: () => void;
    onSaveAndClose: () => void;
}) {
    return (
        <div className="fixed inset-0 z-[70] bg-black/40 backdrop-blur-sm flex items-center justify-center">
            <div className="bg-white rounded-2xl p-6 max-w-md shadow-2xl border border-slate-100">
                <div className="flex items-center gap-3 mb-4">
                    <div className="p-2.5 bg-amber-50 rounded-xl flex-shrink-0">
                        <AlertTriangle className="w-5 h-5 text-amber-500" />
                    </div>
                    <div>
                        <h3 className="text-base font-bold text-slate-900">Ungespeicherte Änderungen</h3>
                        <p className="text-xs text-slate-400 mt-0.5">
                            Das Dokument enthält noch nicht gespeicherte Änderungen.
                        </p>
                    </div>
                </div>
                <p className="text-sm text-slate-600 mb-6 leading-relaxed">
                    Möchten Sie die Änderungen speichern, bevor Sie den Editor verlassen?
                </p>
                <div className="flex gap-2.5">
                    <Button
                        variant="outline"
                        onClick={onCancel}
                        className="flex-1 border-slate-200 text-slate-600"
                    >
                        Abbrechen
                    </Button>
                    <Button
                        variant="outline"
                        onClick={onDiscard}
                        className="flex-1 text-rose-600 border-rose-200 hover:bg-rose-50"
                    >
                        Nicht speichern
                    </Button>
                    <Button
                        onClick={onSaveAndClose}
                        className="flex-1 bg-rose-600 hover:bg-rose-700 text-white shadow-sm"
                    >
                        Speichern & Schließen
                    </Button>
                </div>
            </div>
        </div>
    );
}
/** Reusable Picker Modal Shell */
function PickerModal({
    title,
    icon,
    children,
    onClose,
}: {
    title: string;
    icon: React.ReactNode;
    children: React.ReactNode;
    onClose: () => void;
}) {
    return (
        <div className="fixed inset-0 z-[60] bg-black/40 backdrop-blur-sm flex items-center justify-center">
            <div
                className="bg-white rounded-2xl shadow-2xl w-full max-w-lg mx-4 border border-slate-100 flex flex-col max-h-[80vh] animate-in fade-in zoom-in-95 duration-200"
                onClick={e => e.stopPropagation()}
            >
                {/* Header */}
                <div className="flex items-center justify-between px-5 py-4 border-b border-slate-100 flex-shrink-0">
                    <div className="flex items-center gap-2.5">
                        <div className="p-2 bg-rose-50 rounded-xl">
                            {icon}
                        </div>
                        <h3 className="text-base font-bold text-slate-900">{title}</h3>
                    </div>
                    <button
                        onClick={onClose}
                        className="p-1.5 hover:bg-slate-100 rounded-lg transition-colors"
                    >
                        <X className="w-4 h-4 text-slate-400" />
                    </button>
                </div>
                {/* Content */}
                {children}
            </div>
        </div>
    );
}

/** Textbaustein Picker Modal */
export function TextbausteinPickerModal({
    textbausteine,
    onSelect,
    onClose,
    dokumentTyp,
}: {
    textbausteine: TextbausteinApiDto[];
    onSelect: (tb: TextbausteinApiDto) => void;
    onClose: () => void;
    dokumentTyp?: AusgangsGeschaeftsDokumentTyp;
}) {
    const [search, setSearch] = useState('');

    // Get the label for the current document type (e.g. "Anfrage", "Rechnung")
    const dokumentTypLabel = dokumentTyp
        ? AUSGANGS_GESCHAEFTSDOKUMENT_TYPEN.find(t => t.value === dokumentTyp)?.label || dokumentTyp
        : null;

    // Filter by search, then split into matching/rest
    const { matching, rest } = useMemo(() => {
        let items = textbausteine;
        if (search) {
            const q = search.toLowerCase();
            items = items.filter(tb =>
                tb.name.toLowerCase().includes(q) || (tb.beschreibung || '').toLowerCase().includes(q)
            );
        }

        if (!dokumentTypLabel) {
            return { matching: [] as TextbausteinApiDto[], rest: items };
        }

        const matchGroup: TextbausteinApiDto[] = [];
        const restGroup: TextbausteinApiDto[] = [];

        items.forEach(tb => {
            const hasMatch = tb.dokumenttypen?.some((dt: string) =>
                dt.toLowerCase() === dokumentTyp!.toLowerCase() ||
                dt.toLowerCase() === dokumentTypLabel.toLowerCase()
            );
            if (hasMatch) {
                matchGroup.push(tb);
            } else {
                restGroup.push(tb);
            }
        });

        return { matching: matchGroup, rest: restGroup };
    }, [textbausteine, search, dokumentTyp, dokumentTypLabel]);

    const totalCount = matching.length + rest.length;

    const renderItem = (tb: TextbausteinApiDto, isRecommended: boolean) => (
        <button
            key={tb.id}
            onClick={() => onSelect(tb)}
            className={`w-full group p-3 text-left rounded-xl transition-all duration-150 ${
                isRecommended
                    ? 'bg-rose-50/60 hover:bg-rose-50 border border-rose-200 hover:border-rose-300'
                    : 'bg-white hover:bg-rose-50 border border-slate-150 hover:border-rose-200'
            }`}
        >
            <div className="flex items-start gap-3">
                <div className={`mt-0.5 p-1.5 rounded-lg transition-colors flex-shrink-0 ${
                    isRecommended
                        ? 'bg-rose-100 group-hover:bg-rose-200'
                        : 'bg-slate-100 group-hover:bg-rose-100'
                }`}>
                    <FileText className={`w-3.5 h-3.5 ${
                        isRecommended
                            ? 'text-rose-500'
                            : 'text-slate-400 group-hover:text-rose-500'
                    }`} />
                </div>
                <div className="min-w-0 flex-1">
                    <span className={`text-sm font-medium block truncate ${
                        isRecommended
                            ? 'text-rose-700 group-hover:text-rose-800'
                            : 'text-slate-700 group-hover:text-rose-700'
                    }`}>
                        {tb.name}
                    </span>
                    {tb.beschreibung && (
                        <span className="text-xs text-slate-400 block truncate mt-0.5">
                            {(() => { let t = tb.beschreibung; let p; do { p = t; t = t.replace(/<[^>]*>/g, ''); } while (t !== p); return t.slice(0, 80); })()}
                        </span>
                    )}
                </div>
                <Plus className="w-4 h-4 text-slate-300 group-hover:text-rose-400 flex-shrink-0 mt-0.5 opacity-0 group-hover:opacity-100 transition-opacity" />
            </div>
        </button>
    );

    return (
        <PickerModal title="Textbaustein einfügen" icon={<FileText className="w-4 h-4 text-rose-600" />} onClose={onClose}>
            {/* Search */}
            <div className="px-5 pt-3 pb-2 flex-shrink-0">
                <div className="relative">
                    <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
                    <input
                        type="text"
                        placeholder="Textbaustein suchen…"
                        value={search}
                        onChange={e => setSearch(e.target.value)}
                        autoFocus
                        className="w-full pl-10 pr-4 py-2.5 text-sm bg-slate-50 border border-slate-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-rose-500/20 focus:border-rose-300 placeholder:text-slate-400 transition-all"
                    />
                </div>
            </div>
            {/* List */}
            <div className="flex-1 overflow-y-auto px-5 pb-4 min-h-0">
                {totalCount === 0 ? (
                    <div className="py-10 text-center">
                        <Search className="w-10 h-10 text-slate-200 mx-auto mb-2" />
                        <p className="text-sm text-slate-400">{search ? 'Keine Ergebnisse' : 'Keine Textbausteine vorhanden'}</p>
                    </div>
                ) : (
                    <>
                        {/* Recommended for this document type */}
                        {matching.length > 0 && (
                            <div className="mb-3">
                                <div className="flex items-center gap-2 mb-2 px-1">
                                    <Star className="w-3.5 h-3.5 text-rose-500" />
                                    <span className="text-xs font-semibold text-rose-600 uppercase tracking-wide">
                                        Empfohlen für {dokumentTypLabel}
                                    </span>
                                </div>
                                <div className="space-y-1.5">
                                    {matching.map(tb => renderItem(tb, true))}
                                </div>
                            </div>
                        )}
                        {/* Separator between groups */}
                        {matching.length > 0 && rest.length > 0 && (
                            <div className="flex items-center gap-3 my-3 px-1">
                                <div className="flex-1 h-px bg-slate-200" />
                                <span className="text-xs text-slate-400 font-medium">Weitere Textbausteine</span>
                                <div className="flex-1 h-px bg-slate-200" />
                            </div>
                        )}
                        {/* Rest of textbausteine */}
                        {rest.length > 0 && (
                            <div className="space-y-1.5">
                                {rest.map(tb => renderItem(tb, false))}
                            </div>
                        )}
                    </>
                )}
            </div>
        </PickerModal>
    );
}

// ─── Kategorie-Baum für Leistungspicker ────────────────────────────────────

interface KategorieNode {
    id: number;
    bezeichnung: string;
    isLeaf: boolean;
    parentId: number | null;
}

interface KategorieTreeNodeProps {
    node: KategorieNode;
    selectedId: number | null;
    onSelect: (id: number) => void;
    onChildrenLoaded: (children: KategorieNode[]) => void;
}

function KategorieTreeNode({ node, selectedId, onSelect, onChildrenLoaded }: KategorieTreeNodeProps) {
    const [expanded, setExpanded] = useState(false);
    const [children, setChildren] = useState<KategorieNode[]>([]);
    const [loading, setLoading] = useState(false);
    const loaded = useRef(false);

    const isSelected = selectedId === node.id;

    const handleToggle = async (e: React.MouseEvent) => {
        e.stopPropagation();
        if (node.isLeaf) return;
        if (expanded) { setExpanded(false); return; }
        setExpanded(true);
        if (!loaded.current) {
            setLoading(true);
            try {
                const res = await fetch(`/api/produktkategorien/${node.id}/unterkategorien?light=true`);
                if (res.ok) {
                    const data: ProduktkategorieDto[] = await res.json();
                    const childNodes: KategorieNode[] = (Array.isArray(data) ? data : []).map(cat => ({
                        id: Number(cat.id),
                        bezeichnung: cat.bezeichnung || cat.pfad || 'Kategorie',
                        isLeaf: cat.leaf ?? true,
                        parentId: node.id,
                    }));
                    setChildren(childNodes);
                    onChildrenLoaded(childNodes);
                    loaded.current = true;
                }
            } catch { /* ignore */ } finally {
                setLoading(false);
            }
        }
    };

    return (
        <div>
            <div
                className={cn(
                    'flex items-center gap-2 rounded-lg border px-2.5 py-1.5 cursor-pointer transition-all duration-150',
                    'border-slate-200 bg-white hover:border-rose-200 hover:bg-rose-50/60',
                    isSelected && 'border-rose-500 bg-rose-50 shadow-sm'
                )}
                onClick={() => onSelect(node.id)}
            >
                {!node.isLeaf ? (
                    <button
                        type="button"
                        className="p-0.5 rounded text-slate-400 hover:text-rose-600 flex-shrink-0"
                        onClick={handleToggle}
                    >
                        {loading ? (
                            <Loader2 className="w-3.5 h-3.5 animate-spin" />
                        ) : expanded ? (
                            <ChevronDown className="w-3.5 h-3.5" />
                        ) : (
                            <ChevronRight className="w-3.5 h-3.5" />
                        )}
                    </button>
                ) : (
                    <span className="w-5 flex-shrink-0" />
                )}
                {expanded
                    ? <FolderOpen className="w-3.5 h-3.5 text-rose-500 flex-shrink-0" />
                    : <Folder className="w-3.5 h-3.5 text-rose-500 flex-shrink-0" />
                }
                <span className={cn(
                    'text-xs font-medium truncate',
                    isSelected ? 'text-rose-700' : 'text-slate-700'
                )}>
                    {node.bezeichnung}
                </span>
            </div>
            {expanded && children.length > 0 && (
                <div className="mt-1 space-y-1 pl-3">
                    {children.map(child => (
                        <KategorieTreeNode
                            key={child.id}
                            node={child}
                            selectedId={selectedId}
                            onSelect={onSelect}
                            onChildrenLoaded={onChildrenLoaded}
                        />
                    ))}
                </div>
            )}
        </div>
    );
}

// ─── Leistung Picker Modal ──────────────────────────────────────────────────

/** Leistung Picker Modal */
export function LeistungPickerModal({
    leistungen,
    onSelect,
    onClose,
}: {
    leistungen: LeistungApiDto[];
    onSelect: (l: LeistungApiDto) => void;
    onClose: () => void;
}) {
    const [search, setSearch] = useState('');
    const [rootKategorien, setRootKategorien] = useState<KategorieNode[]>([]);
    const [alleKategorien, setAlleKategorien] = useState<KategorieNode[]>([]);
    const [selectedKategorieId, setSelectedKategorieId] = useState<number | null>(null);
    const [ladeKategorien, setLadeKategorien] = useState(true);

    // Escape zum Schließen
    useEffect(() => {
        const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose(); };
        window.addEventListener('keydown', onKey);
        return () => window.removeEventListener('keydown', onKey);
    }, [onClose]);

    // Root-Kategorien laden
    useEffect(() => {
        fetch('/api/produktkategorien/haupt?light=true')
            .then(r => r.ok ? r.json() : [])
            .then((data: ProduktkategorieDto[]) => {
                const roots: KategorieNode[] = (Array.isArray(data) ? data : []).map(cat => ({
                    id: Number(cat.id),
                    bezeichnung: cat.bezeichnung || cat.pfad || 'Kategorie',
                    isLeaf: cat.leaf ?? true,
                    parentId: null,
                }));
                setRootKategorien(roots);
                setAlleKategorien(roots);
            })
            .catch(() => {})
            .finally(() => setLadeKategorien(false));
    }, []);

    const handleChildrenLoaded = useCallback((children: KategorieNode[]) => {
        setAlleKategorien(prev => {
            const existingIds = new Set(prev.map(k => k.id));
            const newOnes = children.filter(c => !existingIds.has(c.id));
            return newOnes.length ? [...prev, ...newOnes] : prev;
        });
    }, []);

    // Gefilterte Leistungen: Suche hat Vorrang, dann Kategoriefilter
    const filtered = useMemo(() => {
        if (search) {
            const q = search.toLowerCase();
            return leistungen.filter(l =>
                l.name.toLowerCase().includes(q) || (l.description || '').toLowerCase().includes(q)
            );
        }
        if (selectedKategorieId !== null) {
            return leistungen.filter(l => l.folderId === selectedKategorieId);
        }
        return leistungen;
    }, [leistungen, search, selectedKategorieId]);

    const selectedKategorieName = useMemo(
        () => alleKategorien.find(k => k.id === selectedKategorieId)?.bezeichnung ?? null,
        [alleKategorien, selectedKategorieId]
    );

    return (
        <div className="fixed inset-0 z-[60] bg-black/40 backdrop-blur-sm flex items-center justify-center">
            <div
                className="bg-white rounded-2xl shadow-2xl w-full max-w-5xl mx-4 border border-slate-100 flex flex-col max-h-[90vh] animate-in fade-in zoom-in-95 duration-200"
                onClick={e => e.stopPropagation()}
            >
                {/* Header */}
                <div className="flex items-center justify-between px-5 py-4 border-b border-slate-100 flex-shrink-0">
                    <div className="flex items-center gap-2.5">
                        <div className="p-2 bg-rose-50 rounded-xl">
                            <Wrench className="w-4 h-4 text-rose-600" />
                        </div>
                        <div>
                            <h3 className="text-base font-bold text-slate-900">Leistung einfügen</h3>
                            {selectedKategorieName && !search && (
                                <p className="text-xs text-slate-400 mt-0.5">{selectedKategorieName}</p>
                            )}
                        </div>
                    </div>
                    <button type="button" aria-label="Schließen" onClick={onClose} className="p-1.5 hover:bg-slate-100 rounded-lg transition-colors">
                        <X className="w-4 h-4 text-slate-400" />
                    </button>
                </div>

                {/* Search */}
                <div className="px-5 pt-3 pb-2 flex-shrink-0">
                    <div className="relative">
                        <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
                        <input
                            type="text"
                            placeholder="Leistung suchen… (durchsucht alle Kategorien)"
                            value={search}
                            onChange={e => setSearch(e.target.value)}
                            autoFocus
                            className="w-full pl-10 pr-4 py-2.5 text-sm bg-slate-50 border border-slate-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-rose-500/20 focus:border-rose-300 placeholder:text-slate-400 transition-all"
                        />
                    </div>
                </div>

                {/* Two-panel body */}
                <div className="flex flex-1 min-h-0 border-t border-slate-100">
                    {/* Left: Kategorie-Baum */}
                    <div className="w-72 flex-shrink-0 border-r border-slate-100 overflow-y-auto p-3 space-y-1">
                        <p className="text-[10px] uppercase tracking-wide text-slate-400 font-semibold px-1 mb-2">Ordner</p>

                        {/* "Alle" Option */}
                        <div
                            className={cn(
                                'flex items-center gap-2 rounded-lg border px-2.5 py-1.5 cursor-pointer transition-all duration-150',
                                'border-slate-200 bg-white hover:border-rose-200 hover:bg-rose-50/60',
                                selectedKategorieId === null && !search && 'border-rose-500 bg-rose-50 shadow-sm'
                            )}
                            onClick={() => { setSelectedKategorieId(null); setSearch(''); }}
                        >
                            <Folder className="w-3.5 h-3.5 text-slate-400 flex-shrink-0" />
                            <span className={cn(
                                'text-xs font-medium',
                                selectedKategorieId === null && !search ? 'text-rose-700' : 'text-slate-500'
                            )}>
                                Alle Leistungen
                            </span>
                            <span className="ml-auto text-[10px] text-slate-400">{leistungen.length}</span>
                        </div>

                        {ladeKategorien ? (
                            <div className="flex items-center gap-2 px-2 py-3 text-xs text-slate-400">
                                <Loader2 className="w-3.5 h-3.5 animate-spin" /> Laden…
                            </div>
                        ) : rootKategorien.length === 0 ? (
                            <p className="text-xs text-slate-400 px-2 py-3">Keine Kategorien</p>
                        ) : rootKategorien.map(root => (
                            <KategorieTreeNode
                                key={root.id}
                                node={root}
                                selectedId={search ? null : selectedKategorieId}
                                onSelect={id => { setSelectedKategorieId(id); setSearch(''); }}
                                onChildrenLoaded={handleChildrenLoaded}
                            />
                        ))}
                    </div>

                    {/* Right: Leistungsliste */}
                    <div className="flex-1 overflow-y-auto p-3 space-y-1.5 min-h-0">
                        {search && (
                            <p className="text-[10px] uppercase tracking-wide text-slate-400 font-semibold px-1 mb-2">
                                Suchergebnisse für „{search}"
                            </p>
                        )}
                        {filtered.length === 0 ? (
                            <div className="py-12 text-center">
                                <Search className="w-10 h-10 text-slate-200 mx-auto mb-2" />
                                <p className="text-sm text-slate-400">
                                    {search ? 'Keine Ergebnisse' : selectedKategorieId ? 'Keine Leistungen in dieser Kategorie' : 'Keine Leistungen vorhanden'}
                                </p>
                            </div>
                        ) : filtered.map(l => (
                            <button
                                key={l.id}
                                type="button"
                                onClick={() => onSelect(l)}
                                className="w-full group p-3 text-left bg-white hover:bg-rose-50 border border-slate-200 hover:border-rose-200 rounded-xl transition-all duration-150"
                            >
                                <div className="flex items-center justify-between gap-3">
                                    <div className="min-w-0 flex-1">
                                        <span className="text-sm font-medium text-slate-700 group-hover:text-rose-700 block truncate">
                                            {l.name}
                                        </span>
                                        {l.description && (
                                            <span className="text-xs text-slate-400 block truncate mt-0.5">
                                                {(() => { let t = l.description; let p; do { p = t; t = t.replace(/<[^>]*>/g, ''); } while (t !== p); return t.slice(0, 80); })()}
                                            </span>
                                        )}
                                        {search && l.kategoriePfad && (
                                            <span className="text-[10px] text-rose-400 block mt-0.5">{l.kategoriePfad}</span>
                                        )}
                                    </div>
                                    <span className="text-xs font-semibold text-slate-500 bg-slate-100 group-hover:bg-rose-100 group-hover:text-rose-600 px-2 py-1 rounded-lg flex-shrink-0 transition-colors whitespace-nowrap">
                                        {l.price?.toFixed(2)} €
                                    </span>
                                </div>
                            </button>
                        ))}
                    </div>
                </div>
            </div>
        </div>
    );
}

/** Stundensatz Picker Modal */
export function StundensatzPickerModal({
    arbeitszeitarten,
    onSelect,
    onClose,
}: {
    arbeitszeitarten: ArbeitszeitartApiDto[];
    onSelect: (az: ArbeitszeitartApiDto) => void;
    onClose: () => void;
}) {
    const [search, setSearch] = useState('');

    const filtered = useMemo(() => {
        if (!search) return arbeitszeitarten;
        const q = search.toLowerCase();
        return arbeitszeitarten.filter(az =>
            az.bezeichnung.toLowerCase().includes(q) || (az.beschreibung || '').toLowerCase().includes(q)
        );
    }, [arbeitszeitarten, search]);

    return (
        <PickerModal title="Stundensatz einfügen" icon={<Clock className="w-4 h-4 text-rose-600" />} onClose={onClose}>
            {/* Search */}
            <div className="px-5 pt-3 pb-2 flex-shrink-0">
                <div className="relative">
                    <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
                    <input
                        type="text"
                        placeholder="Stundensatz suchen…"
                        value={search}
                        onChange={e => setSearch(e.target.value)}
                        autoFocus
                        className="w-full pl-10 pr-4 py-2.5 text-sm bg-slate-50 border border-slate-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-rose-500/20 focus:border-rose-300 placeholder:text-slate-400 transition-all"
                    />
                </div>
            </div>
            {/* List */}
            <div className="flex-1 overflow-y-auto px-5 pb-4 space-y-1.5 min-h-0">
                {filtered.length === 0 ? (
                    <div className="py-10 text-center">
                        <Search className="w-10 h-10 text-slate-200 mx-auto mb-2" />
                        <p className="text-sm text-slate-400">{search ? 'Keine Ergebnisse' : 'Keine Stundensätze vorhanden'}</p>
                    </div>
                ) : filtered.map(az => (
                    <button
                        key={az.id}
                        onClick={() => onSelect(az)}
                        className="w-full group p-3 text-left bg-white hover:bg-rose-50 border border-slate-150 hover:border-rose-200 rounded-xl transition-all duration-150"
                    >
                        <div className="flex items-center justify-between gap-3">
                            <div className="min-w-0 flex-1">
                                <span className="text-sm font-medium text-slate-700 group-hover:text-rose-700 block truncate">
                                    {az.bezeichnung}
                                </span>
                                {az.beschreibung && (
                                    <span className="text-xs text-slate-400 block truncate mt-0.5">
                                        {(() => { let t = az.beschreibung; let p; do { p = t; t = t.replace(/<[^>]*>/g, ''); } while (t !== p); return t.slice(0, 80); })()}
                                    </span>
                                )}
                            </div>
                            <span className="text-xs font-semibold text-slate-500 bg-slate-100 group-hover:bg-rose-100 group-hover:text-rose-600 px-2 py-1 rounded-lg flex-shrink-0 transition-colors">
                                {az.stundensatz?.toFixed(2)} €/h
                            </span>
                        </div>
                    </button>
                ))}
            </div>
        </PickerModal>
    );
}