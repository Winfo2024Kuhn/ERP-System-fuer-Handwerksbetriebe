import { useState } from 'react';
import { Type, Table2, Image, ChevronDown, ChevronRight, Clipboard, Check } from 'lucide-react';
import type { FormBlock, FormBlockType } from '../../types';
import { PLACEHOLDER_MAP, uid, SIZE_DEFAULTS, STYLE_DEFAULTS, DEFAULT_TABLE_COLUMNS } from './constants';

interface BlocksSidebarProps {
    items: FormBlock[];
    selectedId: string | null;
    activePage: number;
    onSelectBlock: (id: string, page: number) => void;
    onAddBlock: (block: FormBlock) => void;
    onInsertPlaceholder: (placeholder: string) => void;
}

const ADD_BLOCKS: { type: FormBlockType; label: string; description: string; icon: React.ReactNode }[] = [
    {
        type: 'text',
        label: 'Freitext',
        description: 'Freier Textblock mit Platzhalter-Unterstützung',
        icon: <Type className="w-5 h-5" />
    },
    {
        type: 'table',
        label: 'Leistungstabelle',
        description: 'Tabelle mit Positionen, Mengen und Preisen',
        icon: <Table2 className="w-5 h-5" />
    },
    {
        type: 'logo',
        label: 'Logo / Bild',
        description: 'Firmenlogo oder Bild einfügen',
        icon: <Image className="w-5 h-5" />
    }
];

/** Left sidebar — simplified: Freitext, Leistungen, Logo + Platzhalter */
export default function BlocksSidebar({ items, selectedId, activePage, onAddBlock, onInsertPlaceholder }: BlocksSidebarProps) {
    const [showPlaceholders, setShowPlaceholders] = useState(true);
    const [copiedPlaceholder, setCopiedPlaceholder] = useState<string | null>(null);

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
    const canInsert = selectedItem && selectedItem.type !== 'table' && selectedItem.type !== 'logo';

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

            <div className="flex-1 overflow-y-auto p-3 space-y-3">

                {/* Block-Buttons */}
                <div className="space-y-1.5">
                    {ADD_BLOCKS.map(({ type, label, description, icon }) => (
                        <button
                            key={type}
                            onClick={() => handleAddBlock(type)}
                            className="w-full flex items-center gap-3 px-3 py-2.5 text-left bg-slate-50 border border-slate-200 rounded-xl hover:border-rose-400 hover:bg-rose-50 hover:text-rose-700 transition-all duration-150 group cursor-pointer"
                        >
                            <span className="w-8 h-8 flex-shrink-0 flex items-center justify-center bg-white border border-slate-200 rounded-lg text-slate-500 group-hover:border-rose-300 group-hover:text-rose-600 transition-colors">
                                {icon}
                            </span>
                            <span className="min-w-0">
                                <span className="block text-xs font-semibold text-slate-700 group-hover:text-rose-700">{label}</span>
                                <span className="block text-[10px] text-slate-400 leading-tight truncate">{description}</span>
                            </span>
                        </button>
                    ))}
                </div>

                {/* Divider */}
                <div className="border-t border-slate-100" />

                {/* Platzhalter */}
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
                                    ? <span className="text-rose-500 font-medium">Textblock aktiv — Klicke zum Einfügen</span>
                                    : 'Wähle einen Textblock aus oder kopiere in Zwischenablage'
                                }
                            </p>
                            <div className="flex flex-wrap gap-1">
                                {Object.keys(PLACEHOLDER_MAP).map(ph => {
                                    const isCopied = copiedPlaceholder === ph;
                                    return (
                                        <button
                                            key={ph}
                                            onClick={() => handlePlaceholderClick(ph)}
                                            title={canInsert ? `Einfügen: ${ph}` : `Kopieren: ${ph}`}
                                            className={`px-2 py-1 text-[10px] font-mono rounded-md transition-all duration-150 cursor-pointer ${
                                                isCopied
                                                    ? 'bg-green-100 text-green-700 border border-green-300'
                                                    : canInsert
                                                        ? 'bg-rose-50 text-rose-700 border border-rose-200 hover:bg-rose-100'
                                                        : 'bg-slate-100 text-slate-600 border border-transparent hover:bg-slate-200'
                                            }`}
                                        >
                                            {isCopied
                                                ? <span className="flex items-center gap-1"><Check className="w-3 h-3 inline" /> OK</span>
                                                : ph.replace(/[{}]/g, '')
                                            }
                                        </button>
                                    );
                                })}
                            </div>
                        </div>
                    )}
                </div>
            </div>
        </div>
    );
}
