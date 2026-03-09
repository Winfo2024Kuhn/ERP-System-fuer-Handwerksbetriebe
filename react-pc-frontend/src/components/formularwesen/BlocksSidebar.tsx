import { useState } from 'react';
import { ChevronDown, ChevronRight, Layers, GripVertical, Clipboard } from 'lucide-react';
import type { FormBlock, FormBlockType } from '../../types';
import { BLOCK_CATEGORIES, BLOCK_LABELS, BLOCK_ICONS, PLACEHOLDER_MAP, uid, SIZE_DEFAULTS, STYLE_DEFAULTS, DEFAULT_TABLE_COLUMNS } from './constants';

interface BlocksSidebarProps {
    items: FormBlock[];
    selectedId: string | null;
    activePage: number;
    onSelectBlock: (id: string, page: number) => void;
    onAddBlock: (block: FormBlock) => void;
    onInsertPlaceholder: (placeholder: string) => void;
}

/** Left sidebar — block palette + placeholder chips + layers */
export default function BlocksSidebar({ items, selectedId, activePage, onSelectBlock, onAddBlock, onInsertPlaceholder }: BlocksSidebarProps) {
    const [expandedCategories, setExpandedCategories] = useState<Record<string, boolean>>(() => {
        const initial: Record<string, boolean> = {};
        BLOCK_CATEGORIES.forEach(c => { initial[c.label] = true; });
        return initial;
    });
    const [showPlaceholders, setShowPlaceholders] = useState(true);
    const [showLayers, setShowLayers] = useState(true);
    const [copiedPlaceholder, setCopiedPlaceholder] = useState<string | null>(null);

    const toggleCategory = (label: string) => {
        setExpandedCategories(prev => ({ ...prev, [label]: !prev[label] }));
    };

    const handleAddBlock = (type: FormBlockType) => {
        const defaults = SIZE_DEFAULTS[type] || { width: 200, height: 80 };
        const newBlock: FormBlock = {
            id: uid(),
            type,
            page: activePage,
            x: 32,
            y: 32,
            z: Math.max(0, ...items.map(i => i.z)) + 1,
            width: defaults.width,
            height: defaults.height,
            content: type === 'logo' ? '/image001.png' : undefined,
            styles: STYLE_DEFAULTS[type],
            tableColumns: type === 'table' ? DEFAULT_TABLE_COLUMNS : undefined
        };
        onAddBlock(newBlock);
    };

    const selectedItem = items.find(i => i.id === selectedId) || null;
    const canInsert = selectedItem && selectedItem.type !== 'table';

    const handlePlaceholderClick = (ph: string) => {
        if (canInsert) {
            onInsertPlaceholder(ph);
            setCopiedPlaceholder(ph);
            setTimeout(() => setCopiedPlaceholder(null), 1500);
        } else {
            navigator.clipboard.writeText(ph).then(() => {
                setCopiedPlaceholder(ph);
                setTimeout(() => setCopiedPlaceholder(null), 1500);
            });
        }
    };

    return (
        <div className="h-full flex flex-col bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden">
            {/* Header */}
            <div className="px-4 py-3 border-b border-slate-100 bg-slate-50/50">
                <h3 className="text-sm font-bold text-slate-800 tracking-tight">Bausteine</h3>
                <p className="text-[11px] text-slate-400 mt-0.5">Klicke um einen Baustein hinzuzufügen</p>
            </div>

            {/* Scrollable content */}
            <div className="flex-1 overflow-y-auto p-3 space-y-3">
                {/* Block Categories */}
                {BLOCK_CATEGORIES.map(category => (
                    <div key={category.label}>
                        <button
                            onClick={() => toggleCategory(category.label)}
                            className="flex items-center gap-2 w-full text-left px-2 py-1.5 rounded-lg hover:bg-slate-50 transition-colors"
                        >
                            {expandedCategories[category.label]
                                ? <ChevronDown className="w-3.5 h-3.5 text-slate-400" />
                                : <ChevronRight className="w-3.5 h-3.5 text-slate-400" />
                            }
                            <span className="text-xs font-semibold text-slate-500 uppercase tracking-wider">{category.label}</span>
                        </button>
                        {expandedCategories[category.label] && (
                            <div className="grid grid-cols-2 gap-1.5 mt-1 pl-1">
                                {category.types.map(type => (
                                    <button
                                        key={type}
                                        onClick={() => handleAddBlock(type)}
                                        className="flex items-center gap-2 px-2.5 py-2 text-xs font-medium text-slate-700 bg-slate-50 border border-slate-200 rounded-lg hover:border-rose-400 hover:bg-rose-50 hover:text-rose-700 transition-all duration-150 group"
                                    >
                                        <span className="w-6 h-6 flex-shrink-0 flex items-center justify-center text-[11px] bg-white border border-slate-200 rounded-md group-hover:border-rose-300 group-hover:bg-rose-50 transition-colors">
                                            {BLOCK_ICONS[type]}
                                        </span>
                                        <span className="truncate">{BLOCK_LABELS[type]}</span>
                                    </button>
                                ))}
                            </div>
                        )}
                    </div>
                ))}

                {/* Divider */}
                <div className="border-t border-slate-100" />

                {/* Placeholders */}
                <div>
                    <button
                        onClick={() => setShowPlaceholders(!showPlaceholders)}
                        className="flex items-center gap-2 w-full text-left px-2 py-1.5 rounded-lg hover:bg-slate-50 transition-colors"
                    >
                        {showPlaceholders
                            ? <ChevronDown className="w-3.5 h-3.5 text-slate-400" />
                            : <ChevronRight className="w-3.5 h-3.5 text-slate-400" />
                        }
                        <Clipboard className="w-3.5 h-3.5 text-slate-400" />
                        <span className="text-xs font-semibold text-slate-500 uppercase tracking-wider">Platzhalter</span>
                    </button>
                    {showPlaceholders && (
                        <div className="mt-1.5 pl-1">
                            <p className="text-[10px] text-slate-400 mb-2 px-1">
                                {canInsert
                                    ? '✓ Textblock ausgewählt — Klicke zum Einfügen'
                                    : 'Wähle zuerst einen Textblock aus'
                                }
                            </p>
                            <div className="flex flex-wrap gap-1">
                                {Object.keys(PLACEHOLDER_MAP).map(ph => (
                                    <button
                                        key={ph}
                                        onClick={() => handlePlaceholderClick(ph)}
                                        disabled={!canInsert}
                                        className={`px-2 py-1 text-[10px] font-mono rounded-md transition-all duration-150 ${copiedPlaceholder === ph
                                            ? 'bg-green-100 text-green-700 border border-green-300'
                                            : canInsert
                                                ? 'bg-slate-100 text-slate-600 border border-transparent hover:bg-rose-50 hover:text-rose-700 hover:border-rose-200 cursor-pointer'
                                                : 'bg-slate-50 text-slate-300 border border-transparent cursor-not-allowed'
                                            }`}
                                        title={canInsert ? `Klicke zum Einfügen: ${ph}` : 'Wähle zuerst einen Textblock'}
                                    >
                                        {copiedPlaceholder === ph ? '✓ Eingefügt' : ph.replace(/[{}]/g, '')}
                                    </button>
                                ))}
                            </div>
                        </div>
                    )}
                </div>

                {/* Divider */}
                <div className="border-t border-slate-100" />

                {/* Layers Panel */}
                <div>
                    <button
                        onClick={() => setShowLayers(!showLayers)}
                        className="flex items-center gap-2 w-full text-left px-2 py-1.5 rounded-lg hover:bg-slate-50 transition-colors"
                    >
                        {showLayers
                            ? <ChevronDown className="w-3.5 h-3.5 text-slate-400" />
                            : <ChevronRight className="w-3.5 h-3.5 text-slate-400" />
                        }
                        <Layers className="w-3.5 h-3.5 text-slate-400" />
                        <span className="text-xs font-semibold text-slate-500 uppercase tracking-wider">Ebenen</span>
                        <span className="ml-auto text-[10px] text-slate-400 bg-slate-100 rounded-full px-1.5 py-0.5">{items.length}</span>
                    </button>
                    {showLayers && (
                        <div className="mt-1.5 space-y-0.5 max-h-48 overflow-y-auto">
                            {items.sort((a, b) => b.z - a.z).map(item => (
                                <button
                                    key={item.id}
                                    onClick={() => onSelectBlock(item.id, item.page)}
                                    className={`w-full flex items-center gap-2 px-2 py-1.5 text-xs rounded-lg transition-all duration-100 ${selectedId === item.id
                                        ? 'bg-rose-50 border border-rose-300 text-rose-700 font-medium'
                                        : 'hover:bg-slate-50 text-slate-600 border border-transparent'
                                        }`}
                                >
                                    <GripVertical className="w-3 h-3 text-slate-300 flex-shrink-0" />
                                    <span className="w-5 h-5 flex-shrink-0 flex items-center justify-center text-[10px] bg-slate-100 rounded text-slate-500">
                                        {BLOCK_ICONS[item.type]}
                                    </span>
                                    <span className="flex-1 truncate text-left">{BLOCK_LABELS[item.type]}</span>
                                    <span className="text-[10px] text-slate-400 flex-shrink-0">S{item.page}</span>
                                </button>
                            ))}
                        </div>
                    )}
                </div>
            </div>
        </div>
    );
}
