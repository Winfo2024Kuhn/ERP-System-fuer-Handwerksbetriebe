import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { ChevronDown, ChevronRight, Folder, FolderOpen, FolderPlus, Save, Scissors, X } from 'lucide-react';
import { Card } from './ui/card';
import { Button } from './ui/button';
import { Input } from './ui/input';
import { Label } from './ui/label';
import { cn } from '../lib/utils';
import { type ProduktkategorieDto } from '../types';
import { useToast } from './ui/toast';
import { KategorieSchnittbilderModal } from './KategorieSchnittbilderModal';

interface CategoryTreeModalProps {
    onSelect?: (kategorieId: number, kategorieName: string) => void;
    onClose: () => void;
    mode?: 'select' | 'manage';
    onCategoryCreated?: () => void;
}

interface KategorieTreeProps {
    kategorien: ProduktkategorieDto[];
    selectedId: number | null;
    expanded: Set<number>;
    loadingNodes: Set<number>;
    childrenCache: Map<number, ProduktkategorieDto[]>;
    onToggle: (kategorie: ProduktkategorieDto) => void;
    onSelect: (kategorie: ProduktkategorieDto) => void;
}

const KategorieTree: React.FC<KategorieTreeProps> = ({
    kategorien,
    selectedId,
    expanded,
    loadingNodes,
    childrenCache,
    onToggle,
    onSelect,
}) => {
    const renderNode = (kategorie: ProduktkategorieDto, level = 0) => {
        const id = Number(kategorie.id);
        const isLeaf = kategorie.leaf === true;
        const isExpanded = expanded.has(id);
        const isSelected = selectedId === id;
        const isLoading = loadingNodes.has(id);
        const children = childrenCache.get(id) || [];

        return (
            <div key={id}>
                <div
                    className={cn(
                        'group flex items-center gap-2 rounded-lg border px-3 py-2 cursor-pointer transition',
                        'border-slate-200 bg-white hover:border-rose-200 hover:shadow-sm',
                        isSelected ? 'border-rose-500 bg-rose-50 shadow-sm' : '',
                        level > 0 ? 'ml-4' : ''
                    )}
                    onClick={() => onSelect(kategorie)}
                >
                    {!isLeaf ? (
                        <button
                            type="button"
                            onClick={(e) => {
                                e.stopPropagation();
                                onToggle(kategorie);
                            }}
                            className="p-1 rounded text-slate-500 hover:text-rose-600 hover:bg-rose-50"
                        >
                            {isExpanded ? (
                                <ChevronDown className="w-4 h-4" />
                            ) : (
                                <ChevronRight className="w-4 h-4" />
                            )}
                        </button>
                    ) : (
                        <span className="w-6" />
                    )}
                    {isExpanded ? (
                        <FolderOpen className="w-4 h-4 text-rose-600" />
                    ) : (
                        <Folder className="w-4 h-4 text-rose-600" />
                    )}

                    <span className="text-sm font-semibold text-slate-900 truncate flex-1">
                        {kategorie.bezeichnung}
                    </span>

                    {isLoading && <span className="text-xs text-slate-400">Lade...</span>}
                </div>

                {isExpanded && children.length > 0 && (
                    <div className="mt-2 space-y-2 ml-4 border-l border-slate-100 pl-2">
                        {children.map((child) => renderNode(child, level + 1))}
                    </div>
                )}
            </div>
        );
    };

    if (!kategorien.length) {
        return (
            <Card className="p-8 text-center text-slate-500 border-dashed">
                <Folder className="w-10 h-10 mx-auto mb-2 text-rose-200" />
                Keine Kategorien vorhanden
            </Card>
        );
    }

    return (
        <div className="space-y-2">
            {kategorien.map((kategorie) => renderNode(kategorie))}
        </div>
    );
};

export const CategoryTreeModal: React.FC<CategoryTreeModalProps> = ({
    onSelect,
    onClose,
    mode = 'select',
    onCategoryCreated,
}) => {
    const toast = useToast();
    const isManageMode = mode === 'manage';
    const [schnittbilderFuer, setSchnittbilderFuer] = useState<ProduktkategorieDto | null>(null);

    const [roots, setRoots] = useState<ProduktkategorieDto[]>([]);
    const [loading, setLoading] = useState(true);
    const [selectedNode, setSelectedNode] = useState<ProduktkategorieDto | null>(null);
    const [expanded, setExpanded] = useState<Set<number>>(new Set());
    const [loadingNodes, setLoadingNodes] = useState<Set<number>>(new Set());
    const [childrenCache, setChildrenCache] = useState<Map<number, ProduktkategorieDto[]>>(new Map());
    const [formBezeichnung, setFormBezeichnung] = useState('');
    const [saving, setSaving] = useState(false);

    const selectedParentLabel = useMemo(() => {
        if (!selectedNode) return 'Hauptkategorie';
        return `Unter „${selectedNode.bezeichnung}“`;
    }, [selectedNode]);

    const loadRoots = useCallback(async () => {
        setLoading(true);
        try {
            const res = await fetch('/api/artikel/kategorien/haupt');
            if (!res.ok) {
                throw new Error('Kategorien konnten nicht geladen werden.');
            }
            const data = await res.json();
            setRoots(Array.isArray(data) ? data : []);
        } catch (err) {
            console.error(err);
            toast.error('Kategorien konnten nicht geladen werden.');
            setRoots([]);
        } finally {
            setLoading(false);
        }
    }, [toast]);

    const loadChildren = useCallback(async (parentId: number) => {
        try {
            const res = await fetch(`/api/artikel/kategorien/${parentId}/unterkategorien`);
            if (!res.ok) {
                throw new Error('Unterkategorien konnten nicht geladen werden.');
            }
            const data = await res.json();
            return Array.isArray(data) ? data : [];
        } catch (err) {
            console.error(err);
            toast.error('Unterkategorien konnten nicht geladen werden.');
            return [];
        }
    }, [toast]);

    const updateNodeLeafState = useCallback((nodeId: number, leaf: boolean) => {
        setRoots((prev) => prev.map((node) => (Number(node.id) === nodeId ? { ...node, leaf } : node)));

        setChildrenCache((prev) => {
            const next = new Map<number, ProduktkategorieDto[]>();
            for (const [parentId, nodes] of prev.entries()) {
                next.set(
                    parentId,
                    nodes.map((node) => (Number(node.id) === nodeId ? { ...node, leaf } : node))
                );
            }
            return next;
        });

        setSelectedNode((prev) => {
            if (!prev || Number(prev.id) !== nodeId) return prev;
            return { ...prev, leaf };
        });
    }, []);

    useEffect(() => {
        void loadRoots();
    }, [loadRoots]);

    const handleToggle = useCallback(async (kategorie: ProduktkategorieDto) => {
        const id = Number(kategorie.id);
        const isLeaf = kategorie.leaf === true;
        if (isLeaf) return;

        const isExpanded = expanded.has(id);
        if (isExpanded) {
            setExpanded((prev) => {
                const next = new Set(prev);
                next.delete(id);
                return next;
            });
            return;
        }

        if (!childrenCache.has(id)) {
            setLoadingNodes((prev) => new Set(prev).add(id));
            const children = await loadChildren(id);
            setChildrenCache((prev) => {
                const next = new Map(prev);
                next.set(id, children);
                return next;
            });
            setLoadingNodes((prev) => {
                const next = new Set(prev);
                next.delete(id);
                return next;
            });
        }

        setExpanded((prev) => new Set(prev).add(id));
    }, [childrenCache, expanded, loadChildren]);

    const handleSelectNode = useCallback((kategorie: ProduktkategorieDto) => {
        setSelectedNode(kategorie);
        if (!isManageMode && onSelect) {
            onSelect(Number(kategorie.id), kategorie.bezeichnung);
        }
    }, [isManageMode, onSelect]);

    const handleCreateCategory = useCallback(async () => {
        const bezeichnung = formBezeichnung.trim();
        if (!bezeichnung) {
            toast.warning('Bitte eine Bezeichnung eingeben.');
            return;
        }

        setSaving(true);
        try {
            const parentId = selectedNode ? Number(selectedNode.id) : null;
            const res = await fetch('/api/artikel/kategorien', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({
                    bezeichnung,
                    parentId,
                }),
            });

            if (!res.ok) {
                throw new Error('Kategorie konnte nicht angelegt werden.');
            }

            await loadRoots();

            if (parentId) {
                updateNodeLeafState(parentId, false);
                const children = await loadChildren(parentId);
                setChildrenCache((prev) => {
                    const next = new Map(prev);
                    next.set(parentId, children);
                    return next;
                });
                setExpanded((prev) => new Set(prev).add(parentId));
            }

            setFormBezeichnung('');
            onCategoryCreated?.();
            toast.success('Kategorie erfolgreich angelegt.');
        } catch (err) {
            console.error(err);
            toast.error('Kategorie konnte nicht angelegt werden.');
        } finally {
            setSaving(false);
        }
    }, [formBezeichnung, loadChildren, loadRoots, onCategoryCreated, selectedNode, toast, updateNodeLeafState]);

    const modalTitle = isManageMode ? 'Kategorien verwalten' : 'Kategorie auswählen';

    return (
        <div className="fixed inset-0 z-[60] flex items-center justify-center bg-black/50 p-4">
            <Card
                className={cn(
                    'w-full flex flex-col max-h-[85vh] bg-white shadow-2xl border border-rose-100',
                    isManageMode ? 'max-w-5xl' : 'max-w-lg'
                )}
            >
                <div className="p-4 border-b flex items-center justify-between">
                    <div>
                        <h3 className="font-semibold text-lg text-slate-900">{modalTitle}</h3>
                        {isManageMode && (
                            <p className="text-sm text-slate-500">Kategorien können hier rekursiv als Ordnerstruktur angelegt werden.</p>
                        )}
                    </div>
                    <button
                        onClick={onClose}
                        title="Schließen"
                        aria-label="Schließen"
                        className="text-slate-400 hover:text-slate-600"
                    >
                        <X className="w-5 h-5" />
                    </button>
                </div>

                {isManageMode ? (
                    <div className="grid grid-cols-1 xl:grid-cols-[1.2fr_0.8fr] gap-4 p-4 overflow-y-auto">
                        <Card className="p-4 border-rose-100 shadow-sm min-h-[420px]">
                            <div className="flex items-center justify-between mb-4">
                                <div>
                                    <p className="text-xs uppercase tracking-wide text-slate-500">Kategorien</p>
                                    <h4 className="text-lg font-semibold text-slate-900">Ordnerstruktur</h4>
                                </div>
                                <Button
                                    variant="outline"
                                    size="sm"
                                    className="border-rose-300 text-rose-700 hover:bg-rose-50"
                                    onClick={() => setSelectedNode(null)}
                                >
                                    <FolderPlus className="w-4 h-4 mr-2" />
                                    Hauptkategorie
                                </Button>
                            </div>

                            {loading ? (
                                <div className="text-slate-500 text-sm py-6">Lade Kategorien...</div>
                            ) : (
                                <KategorieTree
                                    kategorien={roots}
                                    selectedId={selectedNode ? Number(selectedNode.id) : null}
                                    expanded={expanded}
                                    loadingNodes={loadingNodes}
                                    childrenCache={childrenCache}
                                    onToggle={handleToggle}
                                    onSelect={handleSelectNode}
                                />
                            )}
                        </Card>

                        <Card className="p-4 border-rose-100 shadow-sm h-fit">
                            <div className="space-y-4">
                                <div>
                                    <p className="text-xs uppercase tracking-wide text-slate-500">Neuanlage</p>
                                    <h4 className="text-lg font-semibold text-slate-900">Kategorie hinzufügen</h4>
                                    <span className="mt-2 inline-flex items-center px-2 py-0.5 rounded-full bg-rose-50 text-rose-700 text-xs font-medium">
                                        {selectedParentLabel}
                                    </span>
                                </div>

                                <div className="space-y-2">
                                    <Label htmlFor="kategorie-modal-bezeichnung">Bezeichnung *</Label>
                                    <Input
                                        id="kategorie-modal-bezeichnung"
                                        value={formBezeichnung}
                                        onChange={(e) => setFormBezeichnung(e.target.value)}
                                        placeholder="z.B. Metallfassaden"
                                    />
                                </div>

                                <div className="flex gap-2 pt-2">
                                    <Button
                                        size="sm"
                                        className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700"
                                        onClick={handleCreateCategory}
                                        disabled={saving || !formBezeichnung.trim()}
                                    >
                                        <Save className="w-4 h-4 mr-2" />
                                        {saving
                                            ? 'Speichert...'
                                            : selectedNode
                                                ? 'Unterkategorie anlegen'
                                                : 'Hauptkategorie anlegen'}
                                    </Button>
                                    <Button variant="outline" size="sm" onClick={onClose}>
                                        <X className="w-4 h-4 mr-2" /> Schließen
                                    </Button>
                                </div>

                                {/* Schnittbilder — nur für Leaf-Kategorien (= ohne Unterkategorien),
                                    weil Artikel nur an Leaf-Kategorien hängen und Achsen/Schnittbilder
                                    somit immer kontextspezifisch bleiben. */}
                                {selectedNode && (
                                    <div className="pt-4 border-t border-slate-100">
                                        <p className="text-xs uppercase tracking-wide text-slate-500 mb-2">
                                            Schnittbilder
                                        </p>
                                        {selectedNode.leaf === true ? (
                                            <>
                                                <p className="text-sm text-slate-600 mb-2">
                                                    Pflege Achsen und Schnittbilder für
                                                    <span className="font-medium text-slate-900"> {selectedNode.bezeichnung}</span>.
                                                </p>
                                                <Button
                                                    size="sm"
                                                    variant="outline"
                                                    className="border-rose-300 text-rose-700 hover:bg-rose-50"
                                                    onClick={() => setSchnittbilderFuer(selectedNode)}
                                                >
                                                    <Scissors className="w-4 h-4 mr-2" />
                                                    Schnittbilder verwalten
                                                </Button>
                                            </>
                                        ) : (
                                            <p className="text-xs text-slate-500">
                                                Schnittbilder können nur an einer „Leaf"-Kategorie
                                                (ohne Unterkategorien) hinterlegt werden, weil Artikel
                                                nur dort zugewiesen sind. Wähle eine Unterkategorie,
                                                um Schnittbilder zu pflegen.
                                            </p>
                                        )}
                                    </div>
                                )}
                            </div>
                        </Card>
                    </div>
                ) : (
                    <div className="flex-1 overflow-y-auto p-2">
                        <div
                            className="px-3 py-2 hover:bg-rose-50 cursor-pointer rounded text-sm font-medium text-slate-700"
                            onClick={() => onSelect?.(0, '')}
                        >
                            Alle Kategorien
                        </div>
                        {loading ? (
                            <div className="text-center py-4 text-slate-500">Lade Kategorien...</div>
                        ) : (
                            <KategorieTree
                                kategorien={roots}
                                selectedId={selectedNode ? Number(selectedNode.id) : null}
                                expanded={expanded}
                                loadingNodes={loadingNodes}
                                childrenCache={childrenCache}
                                onToggle={handleToggle}
                                onSelect={handleSelectNode}
                            />
                        )}
                    </div>
                )}
            </Card>

            {schnittbilderFuer && (
                <KategorieSchnittbilderModal
                    kategorie={{ id: Number(schnittbilderFuer.id), bezeichnung: schnittbilderFuer.bezeichnung }}
                    onClose={() => setSchnittbilderFuer(null)}
                />
            )}
        </div>
    );
};
