import React, { useEffect, useState } from 'react';
import { ChevronDown, ChevronRight, Folder, FolderOpen, X, Check } from 'lucide-react';
import { Card } from './ui/card';
import { Button } from './ui/button';
import { cn } from '../lib/utils';
import type { ProduktkategorieDto } from '../types';

interface SelectedCategory {
    id: number;
    bezeichnung: string;
    verrechnungseinheit?: string;
}

interface CategoryMultiSelectModalProps {
    onConfirm: (categories: SelectedCategory[]) => void;
    onClose: () => void;
    initialSelected?: SelectedCategory[];
}

interface TreeNodeProps {
    kategorie: ProduktkategorieDto;
    level: number;
    selectedIds: Set<number>;
    onToggle: (id: number, name: string, verrechnungseinheit?: string) => void;
}

const TreeNode: React.FC<TreeNodeProps> = ({ kategorie, level, selectedIds, onToggle }) => {
    const [expanded, setExpanded] = useState(false);
    const [children, setChildren] = useState<ProduktkategorieDto[]>([]);
    const [loading, setLoading] = useState(false);
    const isSelected = selectedIds.has(Number(kategorie.id));
    const isLeaf = kategorie.leaf === true;

    const handleExpand = async (e: React.MouseEvent) => {
        e.stopPropagation();
        if (expanded) {
            setExpanded(false);
            return;
        }

        setExpanded(true);
        if (children.length === 0 && !kategorie.leaf) {
            setLoading(true);
            try {
                const res = await fetch(`/api/produktkategorien/${kategorie.id}/unterkategorien`);
                if (res.ok) {
                    const data = await res.json();
                    setChildren(data);
                }
            } catch (err) {
                console.error(err);
            } finally {
                setLoading(false);
            }
        }
    };

    // Klick auf Zeile: Bei Leaf auswählen, bei Parent aufklappen
    const handleRowClick = () => {
        if (isLeaf) {
            const einheit = kategorie.verrechnungseinheit?.anzeigename || kategorie.verrechnungseinheit?.name || '';
            onToggle(Number(kategorie.id), kategorie.bezeichnung, einheit);
        } else {
            // Bei Parent-Kategorien aufklappen statt auswählen
            handleExpand({ stopPropagation: () => {} } as React.MouseEvent);
        }
    };

    return (
        <div>
            <div
                className={cn(
                    "flex items-center gap-2 px-3 py-2 rounded text-sm transition-colors",
                    isSelected && "bg-rose-50",
                    level > 0 && "ml-4"
                )}
                style={{ paddingLeft: `${level * 12 + 8}px` }}
            >
                <div
                    onClick={handleExpand}
                    className="flex items-center gap-1 cursor-pointer p-1 -ml-1 hover:bg-slate-200 rounded text-slate-500 hover:text-slate-700"
                >
                    {!kategorie.leaf ? (
                        expanded ? <ChevronDown className="w-4 h-4" /> : <ChevronRight className="w-4 h-4" />
                    ) : (
                        <span className="w-4" />
                    )}
                    {expanded ? <FolderOpen className="w-4 h-4 text-rose-500" /> : <Folder className="w-4 h-4 text-rose-500" />}
                </div>

                <span
                    onClick={handleRowClick}
                    className={cn(
                        "flex-1 truncate py-0.5 px-2 rounded",
                        isLeaf 
                            ? "cursor-pointer" 
                            : "cursor-default text-slate-500",
                        isLeaf && isSelected 
                            ? "text-rose-700 font-medium" 
                            : isLeaf 
                                ? "hover:text-rose-700 hover:bg-rose-50"
                                : ""
                    )}
                    title={!isLeaf ? "Bitte eine Unterkategorie auswählen" : undefined}
                >
                    {kategorie.bezeichnung}
                    {!isLeaf && <span className="text-xs text-slate-400 ml-1">(Oberkategorie)</span>}
                </span>

                {isSelected && (
                    <Check className="w-4 h-4 text-rose-600 flex-shrink-0" />
                )}
            </div>
            {expanded && (
                <div className="border-l border-slate-100 ml-4">
                    {loading && <div className="px-4 py-1 text-xs text-slate-400">Lade...</div>}
                    {children.map(child => (
                        <TreeNode 
                            key={child.id} 
                            kategorie={child} 
                            level={level + 1} 
                            selectedIds={selectedIds}
                            onToggle={onToggle}
                        />
                    ))}
                </div>
            )}
        </div>
    );
};

export const CategoryMultiSelectModal: React.FC<CategoryMultiSelectModalProps> = ({ 
    onConfirm, 
    onClose, 
    initialSelected = [] 
}) => {
    const [roots, setRoots] = useState<ProduktkategorieDto[]>([]);
    const [loading, setLoading] = useState(true);
    const [selected, setSelected] = useState<SelectedCategory[]>(initialSelected);

    const selectedIds = new Set(selected.map(s => s.id));

    useEffect(() => {
        fetch('/api/produktkategorien/haupt')
            .then(res => res.json())
            .then(data => setRoots(Array.isArray(data) ? data : []))
            .catch(err => console.error(err))
            .finally(() => setLoading(false));
    }, []);

    const handleToggle = (id: number, name: string, verrechnungseinheit?: string) => {
        setSelected(prev => {
            const exists = prev.find(s => s.id === id);
            if (exists) {
                return prev.filter(s => s.id !== id);
            }
            return [...prev, { id, bezeichnung: name, verrechnungseinheit }];
        });
    };

    const handleConfirm = () => {
        onConfirm(selected);
        onClose();
    };

    return (
        <div className="fixed inset-0 z-[60] flex items-center justify-center bg-black/50 p-4">
            <Card className="w-full max-w-lg flex flex-col max-h-[80vh] bg-white shadow-2xl">
                <div className="p-4 border-b flex items-center justify-between">
                    <div>
                        <h3 className="font-semibold text-lg">Produktkategorien auswählen</h3>
                        <p className="text-sm text-slate-500">Mehrfachauswahl möglich</p>
                    </div>
                    <button onClick={onClose} className="text-slate-400 hover:text-slate-600">
                        <X className="w-5 h-5" />
                    </button>
                </div>

                {/* Selected categories chips */}
                {selected.length > 0 && (
                    <div className="px-4 py-2 border-b bg-slate-50 flex flex-wrap gap-2">
                        {selected.map(cat => (
                            <span 
                                key={cat.id}
                                className="inline-flex items-center gap-1 px-2 py-1 bg-rose-100 text-rose-700 rounded-full text-xs font-medium"
                            >
                                {cat.bezeichnung}
                                <button 
                                    onClick={() => handleToggle(cat.id, cat.bezeichnung)}
                                    className="hover:bg-rose-200 rounded-full p-0.5"
                                >
                                    <X className="w-3 h-3" />
                                </button>
                            </span>
                        ))}
                    </div>
                )}

                <div className="flex-1 overflow-y-auto p-2">
                    {loading ? (
                        <div className="text-center py-4 text-slate-500">Lade Kategorien...</div>
                    ) : (
                        roots.map(root => (
                            <TreeNode 
                                key={root.id} 
                                kategorie={root} 
                                level={0} 
                                selectedIds={selectedIds}
                                onToggle={handleToggle}
                            />
                        ))
                    )}
                </div>

                <div className="p-4 border-t flex justify-end gap-3 bg-slate-50">
                    <Button variant="outline" onClick={onClose}>
                        Abbrechen
                    </Button>
                    <Button 
                        onClick={handleConfirm}
                        className="bg-rose-600 hover:bg-rose-700 text-white"
                    >
                        <Check className="w-4 h-4 mr-2" />
                        {selected.length} Kategorien übernehmen
                    </Button>
                </div>
            </Card>
        </div>
    );
};
