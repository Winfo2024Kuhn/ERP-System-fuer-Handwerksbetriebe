import { useMemo, useState } from 'react';
import { ChevronDown, Folder, FolderOpen, X } from 'lucide-react';
import { cn } from '../lib/utils';

export interface KategorieFlach {
    id: number;
    beschreibung: string;
    parentId: number | null;
}

function buildKategorieTree(flach: KategorieFlach[]): Map<number | null, KategorieFlach[]> {
    const map = new Map<number | null, KategorieFlach[]>();
    flach.forEach(k => {
        const list = map.get(k.parentId) ?? [];
        list.push(k);
        map.set(k.parentId, list);
    });
    return map;
}

export interface KategoriePickerProps {
    kategorien: KategorieFlach[];
    value: number | null;
    onChange: (id: number | null, beschreibung: string | null) => void;
    compact?: boolean;
    placeholder?: string;
    allowClear?: boolean;
}

export const KategoriePicker: React.FC<KategoriePickerProps> = ({
    kategorien,
    value,
    onChange,
    compact,
    placeholder = 'Kategorie...',
    allowClear = false,
}) => {
    const [offen, setOffen] = useState(false);
    const [expandedIds, setExpandedIds] = useState<Set<number>>(new Set());
    const tree = useMemo(() => buildKategorieTree(kategorien), [kategorien]);
    const gewaehlt = useMemo(() => kategorien.find(k => k.id === value) ?? null, [kategorien, value]);

    const toggleExpand = (id: number) => {
        setExpandedIds(prev => {
            const next = new Set(prev);
            if (next.has(id)) next.delete(id);
            else next.add(id);
            return next;
        });
    };

    const renderTree = (parentId: number | null, depth: number) => {
        const kinder = tree.get(parentId) ?? [];
        return (
            <ul className="space-y-0.5">
                {kinder.map(k => {
                    const hatKinder = (tree.get(k.id) ?? []).length > 0;
                    const istOffen = expandedIds.has(k.id);
                    const istGewaehlt = value === k.id;
                    return (
                        <li key={k.id}>
                            <div
                                className={cn(
                                    'flex items-center gap-2 px-2 py-1 rounded cursor-pointer text-sm select-none',
                                    istGewaehlt ? 'bg-rose-100 text-rose-800 font-medium' : 'hover:bg-slate-100 text-slate-700'
                                )}
                                style={{ paddingLeft: `${8 + depth * 14}px` }}
                                onClick={() => {
                                    if (hatKinder) toggleExpand(k.id);
                                    onChange(k.id, k.beschreibung);
                                    if (!hatKinder) setOffen(false);
                                }}
                            >
                                {hatKinder ? (
                                    istOffen ? <FolderOpen className="w-4 h-4 text-rose-500 shrink-0" /> : <Folder className="w-4 h-4 text-slate-400 shrink-0" />
                                ) : (
                                    <Folder className="w-4 h-4 text-slate-300 shrink-0" />
                                )}
                                <span className="truncate">{k.beschreibung}</span>
                            </div>
                            {hatKinder && istOffen && renderTree(k.id, depth + 1)}
                        </li>
                    );
                })}
            </ul>
        );
    };

    return (
        <div className="relative">
            <button
                type="button"
                onClick={() => setOffen(v => !v)}
                className={cn(
                    'w-full flex items-center justify-between border rounded-md text-left',
                    compact ? 'px-2 py-1.5 text-sm' : 'px-3 py-2 text-sm',
                    gewaehlt ? 'border-rose-300 bg-rose-50 text-rose-800' : 'border-slate-300 bg-white text-slate-400'
                )}
            >
                <span className="truncate">{gewaehlt?.beschreibung ?? placeholder}</span>
                <div className="flex items-center gap-1 shrink-0">
                    {allowClear && gewaehlt && (
                        <span
                            role="button"
                            tabIndex={0}
                            onClick={e => {
                                e.stopPropagation();
                                onChange(null, null);
                            }}
                            onKeyDown={e => {
                                if (e.key === 'Enter' || e.key === ' ') {
                                    e.stopPropagation();
                                    onChange(null, null);
                                }
                            }}
                            className="p-0.5 hover:bg-rose-200 rounded text-rose-600"
                            aria-label="Auswahl entfernen"
                        >
                            <X className="w-3.5 h-3.5" />
                        </span>
                    )}
                    <ChevronDown className={cn('w-4 h-4 text-slate-400 transition-transform', offen && 'rotate-180')} />
                </div>
            </button>
            {offen && (
                <div className="absolute top-full left-0 right-0 mt-1 z-10 border border-slate-200 rounded-lg bg-white shadow-xl max-h-64 overflow-auto p-2">
                    {renderTree(null, 0)}
                </div>
            )}
        </div>
    );
};
