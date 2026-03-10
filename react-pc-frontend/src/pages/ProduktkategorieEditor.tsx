import { useCallback, useEffect, useMemo, useState } from 'react';
import {
    BarChart3,
    ChevronDown,
    ChevronRight,
    Folder,
    FolderOpen,
    FolderPlus,
    Image,
    Pencil,
    Plus,
    RefreshCw,
    Save,
    Trash2,
    X
} from 'lucide-react';
import { Button } from '../components/ui/button';
import { Card } from '../components/ui/card';
import { Input } from '../components/ui/input';
import { Label } from '../components/ui/label';
import { Select } from '../components/ui/select-custom';
import { TiptapEditor } from '../components/TiptapEditor';
import { KategorieAnalyseModal } from '../components/KategorieAnalyseModal';
import { ImageViewer } from '../components/ui/image-viewer';
import { cn } from '../lib/utils';
import {
    type Produktkategorie,
    type VerrechnungseinheitName,
    VERRECHNUNGSEINHEITEN
} from '../types';
import { PageLayout } from '../components/layout/PageLayout';
import { useToast } from '../components/ui/toast';
import { useConfirm } from '../components/ui/confirm-dialog';

// ==================== Kategorie Tree Component ====================
interface KategorieTreeProps {
    kategorien: Produktkategorie[];
    selectedId: number | null;
    expanded: Set<number>;
    onToggle: (id: number) => void;
    onSelect: (kategorie: Produktkategorie) => void;
    loadChildren: (parentId: number) => Promise<Produktkategorie[]>;
    childrenCache: Map<number, Produktkategorie[]>;
    onChildrenLoaded: (parentId: number, children: Produktkategorie[]) => void;
}

const KategorieTree: React.FC<KategorieTreeProps> = ({
    kategorien,
    selectedId,
    expanded,
    onToggle,
    onSelect,
    loadChildren,
    childrenCache,
    onChildrenLoaded,
}) => {
    const renderNode = (kategorie: Produktkategorie, level = 0) => {
        const isExpanded = expanded.has(kategorie.id);
        const isSelected = selectedId === kategorie.id;
        const hasChildren = !kategorie.isLeaf;
        const children = childrenCache.get(kategorie.id) || [];

        const handleExpandClick = async (e: React.MouseEvent) => {
            e.stopPropagation();
            if (!isExpanded && !childrenCache.has(kategorie.id)) {
                const loadedChildren = await loadChildren(kategorie.id);
                onChildrenLoaded(kategorie.id, loadedChildren);
            }
            onToggle(kategorie.id);
        };

        return (
            <div key={kategorie.id}>
                <div
                    className={cn(
                        'group flex items-center justify-between gap-2 rounded-lg border px-3 py-2 cursor-pointer transition',
                        'border-slate-200 bg-white hover:border-rose-200 hover:shadow-sm',
                        isSelected ? 'border-rose-500 bg-rose-50 shadow-sm' : ''
                    )}
                    style={{ marginLeft: level * 16 }}
                    onClick={() => onSelect(kategorie)}
                >
                    <div className="flex items-center gap-2 min-w-0">
                        {hasChildren ? (
                            <button
                                type="button"
                                className="p-1 rounded text-slate-500 hover:text-rose-600 hover:bg-rose-50"
                                onClick={handleExpandClick}
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
                        <span className="text-sm font-semibold text-slate-900 truncate">
                            {kategorie.bezeichnung} {kategorie.projektAnzahl !== undefined && `(${kategorie.projektAnzahl})`}
                        </span>
                    </div>
                    {isSelected && (
                        <ChevronRight className="w-4 h-4 text-rose-600 flex-shrink-0" />
                    )}
                </div>
                {isExpanded && children.length > 0 && (
                    <div className="mt-2 space-y-2">
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

// ==================== Main Component ====================
export default function ProduktkategorieEditor() {
    const toast = useToast();
    const confirmDialog = useConfirm();
    const [hauptkategorien, setHauptkategorien] = useState<Produktkategorie[]>([]);
    const [childrenCache, setChildrenCache] = useState<Map<number, Produktkategorie[]>>(new Map());
    const [selectedKategorie, setSelectedKategorie] = useState<Produktkategorie | null>(null);
    const [expanded, setExpanded] = useState<Set<number>>(new Set());
    const [loading, setLoading] = useState(false);
    const [viewerUrl, setViewerUrl] = useState<string | null>(null);

    // Form state for new category
    const [formMode, setFormMode] = useState<'create' | 'edit' | 'none'>('none');
    const [formBezeichnung, setFormBezeichnung] = useState('');
    const [formVerrechnungseinheit, setFormVerrechnungseinheit] = useState<VerrechnungseinheitName>('STUECK');
    const [formBeschreibung, setFormBeschreibung] = useState('');
    const [formBild, setFormBild] = useState<File | null>(null);
    const [saving, setSaving] = useState(false);
    const [analyseOpen, setAnalyseOpen] = useState(false);

    const selectedPath = useMemo(() => {
        return selectedKategorie?.pfad || selectedKategorie?.bezeichnung || 'Keine Auswahl';
    }, [selectedKategorie]);

    // Load main categories
    const loadHauptkategorien = useCallback(async () => {
        setLoading(true);
        try {
            const res = await fetch('/api/produktkategorien/haupt');
            if (res.ok) {
                const data = await res.json();
                setHauptkategorien(Array.isArray(data) ? data : []);
            }
        } catch (err) {
            console.error('Fehler beim Laden:', err);
        } finally {
            setLoading(false);
        }
    }, []);

    // Load children for a category
    const loadChildren = useCallback(async (parentId: number): Promise<Produktkategorie[]> => {
        try {
            const res = await fetch(`/api/produktkategorien/${parentId}/unterkategorien`);
            if (res.ok) {
                const data = await res.json();
                return Array.isArray(data) ? data : [];
            }
        } catch (err) {
            console.error('Fehler beim Laden der Unterkategorien:', err);
        }
        return [];
    }, []);

    const handleChildrenLoaded = useCallback((parentId: number, children: Produktkategorie[]) => {
        setChildrenCache((prev) => {
            const next = new Map(prev);
            next.set(parentId, children);
            return next;
        });
    }, []);

    useEffect(() => {
        loadHauptkategorien();
    }, [loadHauptkategorien]);

    // Toggle expand/collapse
    const handleToggle = (id: number) => {
        const next = new Set(expanded);
        if (next.has(id)) {
            next.delete(id);
        } else {
            next.add(id);
        }
        setExpanded(next);
    };

    // Select a category
    const handleSelect = (kategorie: Produktkategorie) => {
        setSelectedKategorie(kategorie);
        setFormMode('none');
    };

    // Start creating subcategory
    const handleStartCreate = () => {
        setFormMode('create');
        setFormBezeichnung('');
        setFormVerrechnungseinheit('STUECK');
        setFormBeschreibung('');
        setFormBild(null);
    };

    // Start editing current category
    const handleStartEdit = () => {
        if (!selectedKategorie) return;
        setFormMode('edit');
        setFormBezeichnung(selectedKategorie.bezeichnung);
        setFormVerrechnungseinheit(
            (selectedKategorie.verrechnungseinheit?.name as VerrechnungseinheitName) || 'STUECK'
        );
        setFormBeschreibung(selectedKategorie.beschreibung || '');
        setFormBild(null);
    };

    // Save new category
    const handleSaveNew = async () => {
        if (!formBezeichnung.trim()) return;
        setSaving(true);
        try {
            const formData = new FormData();
            formData.append('bezeichnung', formBezeichnung.trim());
            formData.append('verrechnungseinheit', formVerrechnungseinheit);
            if (selectedKategorie) {
                formData.append('parentId', selectedKategorie.id.toString());
            }
            if (formBeschreibung.trim()) {
                formData.append('beschreibung', formBeschreibung.trim());
            }
            if (formBild) {
                formData.append('bild', formBild);
            }

            const res = await fetch('/api/produktkategorien', {
                method: 'POST',
                body: formData,
            });

            if (res.ok) {
                setFormMode('none');
                // Reload tree
                await loadHauptkategorien();
                // Refresh children if we added to a parent
                if (selectedKategorie) {
                    const children = await loadChildren(selectedKategorie.id);
                    handleChildrenLoaded(selectedKategorie.id, children);
                    // Mark parent as expanded
                    setExpanded((prev) => new Set(prev).add(selectedKategorie.id));
                }
            } else {
                toast.error('Fehler beim Erstellen der Kategorie.');
            }
        } catch (err) {
            console.error(err);
            toast.error('Fehler beim Erstellen der Kategorie.');
        } finally {
            setSaving(false);
        }
    };

    // Update category details (name, unit, description, image)
    const handleUpdateCategory = async () => {
        if (!selectedKategorie || !formBezeichnung.trim()) return;
        setSaving(true);
        try {
            const formData = new FormData();
            formData.append('bezeichnung', formBezeichnung.trim());
            formData.append('verrechnungseinheit', formVerrechnungseinheit);
            formData.append('beschreibung', formBeschreibung.trim());
            if (formBild) {
                formData.append('bild', formBild);
            }

            const res = await fetch(`/api/produktkategorien/${selectedKategorie.id}`, {
                method: 'PUT',
                body: formData,
            });

            if (res.ok) {
                const updatedData = await res.json();
                setSelectedKategorie(updatedData);
                setFormMode('none');
                // Reload tree to reflect changes (especially name)
                await loadHauptkategorien();
                // If it has children, refresh them too if needed (or just reload tree is enough)
            } else {
                toast.error('Fehler beim Aktualisieren der Kategorie.');
            }
        } catch (err) {
            console.error(err);
            toast.error('Fehler beim Aktualisieren der Kategorie.');
        } finally {
            setSaving(false);
        }
    };

    // Delete category
    const handleDelete = async () => {
        if (!selectedKategorie) return;
        if (!await confirmDialog({ title: "Kategorie löschen", message: `Möchten Sie die Kategorie "${selectedKategorie.bezeichnung}" wirklich löschen?`, variant: "danger", confirmLabel: "Löschen" })) {
            return;
        }
        try {
            const res = await fetch(`/api/produktkategorien/${selectedKategorie.id}`, {
                method: 'DELETE',
            });

            if (res.ok) {
                setSelectedKategorie(null);
                setFormMode('none');
                await loadHauptkategorien();
            } else if (res.status === 409) {
                toast.warning('Kategorie kann nicht gelöscht werden, da sie noch Unterkategorien oder Artikel enthält.');
            } else {
                toast.error('Fehler beim Löschen der Kategorie.');
            }
        } catch (err) {
            console.error(err);
        }
    };

    const handleCancel = () => {
        setFormMode('none');
    };

    return (
        <PageLayout
            ribbonCategory="Artikelverwaltung"
            title="Produktkategorien"
            subtitle="Verwaltung der hierarchischen Kategoriestruktur."
            actions={
                <>
                    {selectedKategorie && (
                        <Button
                            variant="outline"
                            size="sm"
                            className="border-rose-300 text-rose-700 hover:bg-rose-50"
                            onClick={() => setAnalyseOpen(true)}
                        >
                            <BarChart3 className="w-4 h-4 mr-2" />
                            Analyse
                        </Button>
                    )}
                    <Button variant="outline" size="sm" onClick={loadHauptkategorien} disabled={loading}>
                        <RefreshCw className={cn("w-4 h-4 mr-2", loading && "animate-spin")} />
                        Aktualisieren
                    </Button>
                </>
            }
        >
            {/* 3-Column Grid */}
            <div className="grid grid-cols-1 xl:grid-cols-[1fr_1fr_1.5fr] gap-6">
                {/* Column 1: Category Tree */}
                <Card className="p-6 border-rose-100 shadow-lg">
                    <div className="flex items-center justify-between mb-4">
                        <div>
                            <p className="text-xs uppercase tracking-wide text-slate-500">Kategorien</p>
                            <h4 className="text-lg font-semibold text-slate-900">Struktur</h4>
                        </div>
                        <Button
                            className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700"
                            size="sm"
                            onClick={() => {
                                setSelectedKategorie(null);
                                handleStartCreate();
                            }}
                        >
                            <Plus className="w-4 h-4" /> Hauptkategorie
                        </Button>
                    </div>

                    {loading ? (
                        <div className="text-slate-500 text-sm py-6">Wird geladen...</div>
                    ) : (
                        <KategorieTree
                            kategorien={hauptkategorien}
                            selectedId={selectedKategorie?.id || null}
                            expanded={expanded}
                            onToggle={handleToggle}
                            onSelect={handleSelect}
                            loadChildren={loadChildren}
                            childrenCache={childrenCache}
                            onChildrenLoaded={handleChildrenLoaded}
                        />
                    )}
                </Card>

                {/* Column 2: Selected Category Details */}
                <Card className="p-6 border-rose-100 shadow-lg">
                    <div className="flex items-start gap-4 mb-4">
                        {selectedKategorie?.bildUrl ? (
                            <img
                                src={selectedKategorie.bildUrl}
                                alt=""
                                className="w-20 h-20 rounded-xl object-cover shadow-md cursor-pointer hover:opacity-90 transition-opacity"
                                onClick={() => setViewerUrl(selectedKategorie.bildUrl || null)}
                            />
                        ) : (
                            <div className="w-20 h-20 rounded-xl bg-gradient-to-br from-rose-100 to-rose-200 flex items-center justify-center">
                                <Image className="w-10 h-10 text-rose-400" />
                            </div>
                        )}
                        <div className="flex-1 min-w-0">
                            <p className="text-xs uppercase tracking-wide text-slate-500">Ausgewählt</p>
                            <h4 className="text-2xl font-bold text-slate-900 truncate">
                                {selectedKategorie?.bezeichnung || 'Keine Auswahl'}
                            </h4>
                        </div>
                    </div>

                    {selectedKategorie ? (
                        <div className="space-y-4">
                            {/* Category Info */}
                            <div className="space-y-3">
                                <div className="flex items-center gap-2 text-sm">
                                    <span className="text-slate-500">Pfad:</span>
                                    <span className="text-slate-900 font-medium">{selectedPath}</span>
                                </div>
                                <div className="flex items-center gap-2 text-sm">
                                    <span className="text-slate-500">Verrechnungseinheit:</span>
                                    <span className="inline-flex items-center px-2 py-0.5 rounded-full bg-rose-50 text-rose-700 text-xs font-medium">
                                        {selectedKategorie.verrechnungseinheit?.anzeigename || 'Nicht festgelegt'}
                                    </span>
                                </div>
                                <div className="flex items-center gap-2 text-sm">
                                    <span className="text-slate-500">Typ:</span>
                                    <span className={cn(
                                        "inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium",
                                        selectedKategorie.isLeaf
                                            ? "bg-green-50 text-green-700"
                                            : "bg-slate-100 text-slate-700"
                                    )}>
                                        {selectedKategorie.isLeaf ? 'Endkategorie' : 'Hat Unterkategorien'}
                                    </span>
                                </div>
                            </div>

                            {/* Beschreibung Preview */}
                            {selectedKategorie.beschreibung && (
                                <div className="border border-slate-100 rounded-lg p-3 bg-slate-50">
                                    <p className="text-xs text-slate-500 mb-2">Beschreibung:</p>
                                    <div
                                        className="prose prose-sm max-w-none text-slate-700"
                                        dangerouslySetInnerHTML={{ __html: selectedKategorie.beschreibung }}
                                    />
                                </div>
                            )}


                            {/* Action Buttons */}
                            <div className="flex flex-wrap gap-2 pt-4 border-t border-slate-100">
                                <Button
                                    className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700"
                                    size="sm"
                                    onClick={handleStartCreate}
                                >
                                    <FolderPlus className="w-4 h-4" /> Unterkategorie
                                </Button>
                                <Button
                                    variant="ghost"
                                    size="sm"
                                    className="text-rose-700 hover:bg-rose-100"
                                    onClick={handleStartEdit}
                                >
                                    <Pencil className="w-4 h-4" /> Bearbeiten
                                </Button>
                                <Button
                                    variant="ghost"
                                    size="sm"
                                    className="text-rose-700 hover:bg-rose-100"
                                    onClick={handleDelete}
                                >
                                    <Trash2 className="w-4 h-4" /> Löschen
                                </Button>
                            </div>
                        </div>
                    ) : (
                        <Card className="p-10 text-center text-slate-500 border-dashed">
                            Wählen Sie eine Kategorie aus der Struktur
                        </Card>
                    )}
                </Card>

                {/* Column 3: Create/Edit Form */}
                <div className="min-h-full">
                    {formMode !== 'none' ? (
                        <Card className="border-rose-100 shadow-lg">
                            <div className="p-4 space-y-4">
                                <div className="flex items-center justify-between gap-3">
                                    <div>
                                        <p className="text-xs uppercase tracking-wide text-slate-500">
                                            {formMode === 'create' ? 'Neuanlage' : 'Bearbeitung'}
                                        </p>
                                        <h3 className="text-xl font-semibold text-slate-900">
                                            {formMode === 'create'
                                                ? (selectedKategorie ? 'Neue Unterkategorie' : 'Neue Hauptkategorie')
                                                : 'Kategorie bearbeiten'
                                            }
                                        </h3>
                                    </div>
                                    {selectedKategorie && formMode === 'create' && (
                                        <span className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-rose-50 text-rose-700 border border-rose-100 text-sm">
                                            Unter: {selectedKategorie.bezeichnung}
                                        </span>
                                    )}
                                </div>

                                <>
                                    <div className="space-y-2">
                                        <Label htmlFor="bezeichnung">Bezeichnung *</Label>
                                        <Input
                                            id="bezeichnung"
                                            value={formBezeichnung}
                                            onChange={(e) => setFormBezeichnung(e.target.value)}
                                            placeholder="z.B. Metallfassaden"
                                        />
                                    </div>

                                    <div className="space-y-2">
                                        <Label htmlFor="verrechnungseinheit">Verrechnungseinheit</Label>
                                        <Select
                                            value={formVerrechnungseinheit}
                                            onChange={(value) => setFormVerrechnungseinheit(value as VerrechnungseinheitName)}
                                            options={VERRECHNUNGSEINHEITEN.map((v) => ({
                                                value: v.name,
                                                label: v.anzeigename
                                            }))}
                                            placeholder="Einheit wählen"
                                        />
                                    </div>

                                    <div className="space-y-2">
                                        <Label htmlFor="bild">Kategoriesbild (optional)</Label>
                                        <div className="flex items-center gap-2">
                                            <Input
                                                id="bild"
                                                type="file"
                                                accept="image/*"
                                                onChange={(e) => setFormBild(e.target.files?.[0] || null)}
                                                className="text-sm"
                                            />
                                            {formBild && (
                                                <span className="text-xs text-slate-500 flex items-center gap-1">
                                                    <Image className="w-3 h-3" />
                                                    {formBild.name}
                                                </span>
                                            )}
                                        </div>
                                    </div>
                                </>

                                <div className="space-y-2">
                                    <Label>Beschreibung (Rich-Text)</Label>
                                    <TiptapEditor
                                        value={formBeschreibung}
                                        onChange={setFormBeschreibung}
                                    />
                                </div>

                                <div className="flex gap-3 pt-2">
                                    <Button
                                        className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700"
                                        size="sm"
                                        onClick={formMode === 'create' ? handleSaveNew : handleUpdateCategory}
                                        disabled={saving || (formMode === 'create' && !formBezeichnung.trim())}
                                    >
                                        <Save className="w-4 h-4" />
                                        {saving ? 'Speichert...' : 'Speichern'}
                                    </Button>
                                    <Button variant="outline" size="sm" onClick={handleCancel}>
                                        <X className="w-4 h-4" /> Abbrechen
                                    </Button>
                                </div>
                            </div>
                        </Card>
                    ) : (
                        <Card className="p-10 h-full border-dashed border-slate-200 text-center text-slate-500 shadow-inner">
                            <FolderPlus className="w-12 h-12 mx-auto mb-4 text-rose-200" />
                            <p>Wählen Sie eine Kategorie und klicken Sie auf</p>
                            <p className="font-medium text-slate-700 mt-1">"Unterkategorie" oder "Bearbeiten"</p>
                        </Card>
                    )}
                </div>
            </div>

            {/* Analyse Modal */}
            {analyseOpen && selectedKategorie && (
                <KategorieAnalyseModal
                    kategorie={selectedKategorie}
                    onClose={() => setAnalyseOpen(false)}
                />
            )}

            <ImageViewer
                src={viewerUrl}
                alt="Kategorie-Bild"
                onClose={() => setViewerUrl(null)}
            />
        </PageLayout>
    );
}
