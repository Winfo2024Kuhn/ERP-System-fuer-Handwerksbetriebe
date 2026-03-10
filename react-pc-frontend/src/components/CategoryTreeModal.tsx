import React, { useEffect, useState } from 'react';
import { ChevronDown, ChevronRight, Folder, FolderOpen, X } from 'lucide-react';
import { Card } from './ui/card';
import { cn } from '../lib/utils';
import type { ProduktkategorieDto } from '../types';

interface CategoryTreeModalProps {
    onSelect: (kategorieId: number, kategorieName: string) => void;
    onClose: () => void;
}

interface TreeNodeProps {
    kategorie: ProduktkategorieDto;
    level: number;
    onSelect: (id: number, name: string) => void;
}

const TreeNode: React.FC<TreeNodeProps> = ({ kategorie, level, onSelect }) => {
    const [expanded, setExpanded] = useState(false);
    const [children, setChildren] = useState<ProduktkategorieDto[]>([]);
    const [loading, setLoading] = useState(false);

    const handleToggle = async (e: React.MouseEvent) => {
        e.stopPropagation();
        if (expanded) {
            setExpanded(false);
            return;
        }

        setExpanded(true);
        if (children.length === 0 && !kategorie.leaf) {
            setLoading(true);
            try {
                const res = await fetch(`/artikel/kategorien/${kategorie.id}/unterkategorien`);
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

    return (
        <div>
            <div
                className={cn(
                    "flex items-center gap-2 px-3 py-2 rounded text-sm transition-colors",
                    level > 0 && "ml-4"
                )}
                style={{ paddingLeft: `${level * 12 + 8}px` }}
            >
                <div
                    onClick={handleToggle}
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
                    onClick={() => onSelect(Number(kategorie.id), kategorie.bezeichnung)}
                    className="truncate cursor-pointer hover:text-rose-700 hover:underline py-0.5"
                >
                    {kategorie.bezeichnung}
                </span>
            </div>
            {expanded && (
                <div className="border-l border-slate-100 ml-4">
                    {loading && <div className="px-4 py-1 text-xs text-slate-400">Lade...</div>}
                    {children.map(child => (
                        <TreeNode key={child.id} kategorie={child} level={level + 1} onSelect={onSelect} />
                    ))}
                </div>
            )}
        </div>
    );
};

export const CategoryTreeModal: React.FC<CategoryTreeModalProps> = ({ onSelect, onClose }) => {
    const [roots, setRoots] = useState<ProduktkategorieDto[]>([]);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        fetch('/artikel/kategorien/haupt')
            .then(res => res.json())
            .then(data => setRoots(Array.isArray(data) ? data : []))
            .catch(err => console.error(err))
            .finally(() => setLoading(false));
    }, []);

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
            <Card className="w-full max-w-lg flex flex-col max-h-[80vh] bg-white shadow-2xl">
                <div className="p-4 border-b flex items-center justify-between">
                    <h3 className="font-semibold text-lg">Kategorie auswählen</h3>
                    <button onClick={onClose} className="text-slate-400 hover:text-slate-600">
                        <X className="w-5 h-5" />
                    </button>
                </div>
                <div className="flex-1 overflow-y-auto p-2">
                    <div className="px-3 py-2 hover:bg-rose-50 cursor-pointer rounded text-sm font-medium text-slate-700" onClick={() => onSelect(0, '')}>
                        Alle Kategorien
                    </div>
                    {loading ? (
                        <div className="text-center py-4 text-slate-500">Lade Kategorien...</div>
                    ) : (
                        roots.map(root => (
                            <TreeNode key={root.id} kategorie={root} level={0} onSelect={onSelect} />
                        ))
                    )}
                </div>
            </Card>
        </div>
    );
};
