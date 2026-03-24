import React, { useEffect, useState } from 'react';
import { ChevronDown, ChevronRight, Folder, FolderOpen, X } from 'lucide-react';
import { Card } from './ui/card';
import { cn } from '../lib/utils';

interface Kategorie {
    id: number;
    name: string;
    bezeichnung?: string;
    parentId?: number | null;
}

interface ProjektKategorieTreeModalProps {
    projektId: number;
    onSelect: (kategorieId: number | null, kategorieName: string) => void;
    onClose: () => void;
}

interface TreeNodeProps {
    kategorie: Kategorie;
    allKategorien: Kategorie[];
    level: number;
    onSelect: (id: number, name: string) => void;
}

const TreeNode: React.FC<TreeNodeProps> = ({ kategorie, allKategorien, level, onSelect }) => {
    const [expanded, setExpanded] = useState(false);

    // Finde Unterkategorien
    const children = allKategorien.filter(k => k.parentId === kategorie.id);
    const hasChildren = children.length > 0;

    const handleToggle = (e: React.MouseEvent) => {
        e.stopPropagation();
        if (hasChildren) {
            setExpanded(!expanded);
        }
    };

    const handleSelect = () => {
        onSelect(kategorie.id, kategorie.name || kategorie.bezeichnung || 'Unbekannt');
    };

    return (
        <div>
            <div
                className={cn(
                    "flex items-center gap-2 px-3 py-2 rounded text-sm transition-colors hover:bg-slate-100",
                    level > 0 && "ml-4"
                )}
                style={{ paddingLeft: `${level * 16 + 8}px` }}
            >
                <div
                    onClick={handleToggle}
                    className={cn(
                        "flex items-center gap-1 p-1 -ml-1 rounded",
                        hasChildren ? "cursor-pointer hover:bg-slate-200 text-slate-500 hover:text-slate-700" : "text-transparent"
                    )}
                >
                    {hasChildren ? (
                        expanded ? <ChevronDown className="w-4 h-4" /> : <ChevronRight className="w-4 h-4" />
                    ) : (
                        <span className="w-4" />
                    )}
                    {expanded ? <FolderOpen className="w-4 h-4 text-rose-500" /> : <Folder className="w-4 h-4 text-rose-500" />}
                </div>

                <span
                    onClick={handleSelect}
                    className="truncate cursor-pointer hover:text-rose-700 hover:underline py-0.5 flex-1"
                >
                    {kategorie.name || kategorie.bezeichnung}
                </span>
            </div>
            {expanded && hasChildren && (
                <div className="border-l border-slate-200 ml-4">
                    {children.map(child => (
                        <TreeNode
                            key={child.id}
                            kategorie={child}
                            allKategorien={allKategorien}
                            level={level + 1}
                            onSelect={onSelect}
                        />
                    ))}
                </div>
            )}
        </div>
    );
};

export const ProjektKategorieTreeModal: React.FC<ProjektKategorieTreeModalProps> = ({ projektId, onSelect, onClose }) => {
    const [kategorien, setKategorien] = useState<Kategorie[]>([]);
    const [loading, setLoading] = useState(() => !!projektId);

    useEffect(() => {
        if (!projektId) {
            return;
        }

        const startLoading = window.setTimeout(() => setLoading(true), 0);
        fetch(`/api/zeiterfassung/kategorien/${projektId}`)
            .then(res => res.json())
            .then(data => {
                // API gibt {id, name, bezeichnung} zurück - wir bauen eine flache Liste
                const kats = Array.isArray(data) ? data : [];
                setKategorien(kats);
            })
            .catch(err => console.error('Fehler beim Laden der Kategorien:', err))
            .finally(() => setLoading(false));

        return () => window.clearTimeout(startLoading);
    }, [projektId]);

    // Finde Root-Kategorien (ohne Parent oder Parent nicht in der Liste)
    const rootKategorien = kategorien.filter(k => 
        !k.parentId || !kategorien.some(other => other.id === k.parentId)
    );

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
            <Card className="w-full max-w-md flex flex-col max-h-[70vh] bg-white shadow-2xl">
                <div className="p-4 border-b flex items-center justify-between">
                    <h3 className="font-semibold text-lg">Produktkategorie auswählen</h3>
                    <button onClick={onClose} className="text-slate-400 hover:text-slate-600">
                        <X className="w-5 h-5" />
                    </button>
                </div>
                <div className="flex-1 overflow-y-auto p-2">
                    {/* Keine Kategorie Option */}
                    <div
                        className="px-3 py-2 hover:bg-rose-50 cursor-pointer rounded text-sm font-medium text-slate-700 flex items-center gap-2"
                        onClick={() => onSelect(null, '')}
                    >
                        <span className="w-4" />
                        <span className="text-slate-400">—</span>
                        <span>Keine Kategorie</span>
                    </div>

                    {loading ? (
                        <div className="text-center py-4 text-slate-500">Lade Kategorien...</div>
                    ) : kategorien.length === 0 ? (
                        <div className="text-center py-8 text-slate-400">
                            <Folder className="w-12 h-12 mx-auto mb-2 opacity-30" />
                            <p>Keine Kategorien für dieses Projekt verfügbar</p>
                        </div>
                    ) : (
                        rootKategorien.map(root => (
                            <TreeNode
                                key={root.id}
                                kategorie={root}
                                allKategorien={kategorien}
                                level={0}
                                onSelect={onSelect}
                            />
                        ))
                    )}
                </div>
            </Card>
        </div>
    );
};

export default ProjektKategorieTreeModal;
