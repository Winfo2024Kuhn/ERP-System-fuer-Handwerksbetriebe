import { Trash2, AlignLeft, AlignCenter, AlignRight, Bold, Type } from 'lucide-react';
import { Button } from '../ui/button';
import type { FormBlock } from '../../types';
import { BLOCK_LABELS, BLOCK_ICONS, DOCUMENT_TYPES, isTextualType } from './constants';

interface PropertiesSidebarProps {
    selectedItem: FormBlock | null;
    selectedDokumenttypen: string[];
    onUpdateBlock: (id: string, updates: Partial<FormBlock>) => void;
    onDeleteBlock: (id: string) => void;
    onUpdateDokumenttypen: (types: string[]) => void;
}

/** Right sidebar — block inspector + document type assignment */
export default function PropertiesSidebar({
    selectedItem,
    selectedDokumenttypen,
    onUpdateBlock,
    onDeleteBlock,
    onUpdateDokumenttypen
}: PropertiesSidebarProps) {
    return (
        <div className="h-full flex flex-col bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden">
            {/* Header */}
            <div className="px-4 py-3 border-b border-slate-100 bg-slate-50/50">
                <h3 className="text-sm font-bold text-slate-800 tracking-tight">Eigenschaften</h3>
            </div>

            {/* Scrollable content */}
            <div className="flex-1 overflow-y-auto p-4 space-y-4">
                {selectedItem ? (
                    <>
                        {/* Block Type Header */}
                        <div className="flex items-center gap-3 p-3 bg-slate-50 rounded-xl">
                            <div className="w-10 h-10 flex items-center justify-center text-base bg-white border border-slate-200 rounded-lg shadow-sm">
                                {BLOCK_ICONS[selectedItem.type]}
                            </div>
                            <div>
                                <p className="text-sm font-semibold text-slate-800">{BLOCK_LABELS[selectedItem.type]}</p>
                                <p className="text-[11px] text-slate-400">Seite {selectedItem.page}</p>
                            </div>
                        </div>

                        {/* Position & Size */}
                        <div>
                            <p className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider mb-2">Position & Größe</p>
                            <div className="grid grid-cols-2 gap-2">
                                <PropertyInput label="X" value={selectedItem.x} onChange={v => onUpdateBlock(selectedItem.id, { x: v })} />
                                <PropertyInput label="Y" value={selectedItem.y} onChange={v => onUpdateBlock(selectedItem.id, { y: v })} />
                                <PropertyInput label="B" value={selectedItem.width} onChange={v => onUpdateBlock(selectedItem.id, { width: v })} />
                                <PropertyInput label="H" value={selectedItem.height} onChange={v => onUpdateBlock(selectedItem.id, { height: v })} />
                            </div>
                        </div>

                        {/* Text Properties */}
                        {isTextualType(selectedItem.type) && (
                            <>
                                <div className="border-t border-slate-100 pt-4">
                                    <p className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider mb-2">Inhalt</p>
                                    <textarea
                                        value={selectedItem.content || ''}
                                        onChange={e => onUpdateBlock(selectedItem.id, { content: e.target.value })}
                                        className="w-full p-2.5 text-sm bg-slate-50 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-rose-500/20 focus:border-rose-400 transition-all resize-none"
                                        rows={3}
                                        placeholder="Text eingeben..."
                                    />
                                </div>

                                <div className="border-t border-slate-100 pt-4">
                                    <p className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider mb-2">Textformat</p>
                                    <div className="space-y-2.5">
                                        {/* Font Size */}
                                        <div className="flex items-center gap-2">
                                            <Type className="w-3.5 h-3.5 text-slate-400 flex-shrink-0" />
                                            <input
                                                type="number"
                                                value={selectedItem.styles?.fontSize || 14}
                                                onChange={e => onUpdateBlock(selectedItem.id, {
                                                    styles: { ...selectedItem.styles, fontSize: +e.target.value }
                                                })}
                                                className="flex-1 p-1.5 text-sm bg-slate-50 border border-slate-200 rounded-lg text-center focus:outline-none focus:ring-2 focus:ring-rose-500/20 focus:border-rose-400"
                                            />
                                            <span className="text-[10px] text-slate-400">px</span>
                                        </div>

                                        {/* Font Weight Toggle */}
                                        <div className="flex items-center gap-2">
                                            <button
                                                onClick={() => onUpdateBlock(selectedItem.id, {
                                                    styles: {
                                                        ...selectedItem.styles,
                                                        fontWeight: selectedItem.styles?.fontWeight === '700' ? '400' : '700'
                                                    }
                                                })}
                                                className={`p-2 rounded-lg border transition-all ${selectedItem.styles?.fontWeight === '700'
                                                    ? 'bg-rose-50 border-rose-300 text-rose-700'
                                                    : 'bg-slate-50 border-slate-200 text-slate-500 hover:border-slate-300'
                                                    }`}
                                                title="Fett"
                                            >
                                                <Bold className="w-3.5 h-3.5" />
                                            </button>

                                            {/* Text Align */}
                                            <div className="flex border border-slate-200 rounded-lg overflow-hidden">
                                                {(['left', 'center', 'right'] as const).map(align => {
                                                    const Icon = align === 'left' ? AlignLeft : align === 'center' ? AlignCenter : AlignRight;
                                                    return (
                                                        <button
                                                            key={align}
                                                            onClick={() => onUpdateBlock(selectedItem.id, {
                                                                styles: { ...selectedItem.styles, textAlign: align }
                                                            })}
                                                            className={`p-2 transition-all ${(selectedItem.styles?.textAlign || 'left') === align
                                                                ? 'bg-rose-50 text-rose-700'
                                                                : 'bg-slate-50 text-slate-500 hover:bg-slate-100'
                                                                }`}
                                                            title={align === 'left' ? 'Links' : align === 'center' ? 'Zentriert' : 'Rechts'}
                                                        >
                                                            <Icon className="w-3.5 h-3.5" />
                                                        </button>
                                                    );
                                                })}
                                            </div>

                                            {/* Color Picker */}
                                            <div className="relative ml-auto">
                                                <input
                                                    type="color"
                                                    value={selectedItem.styles?.color || '#111827'}
                                                    onChange={e => onUpdateBlock(selectedItem.id, {
                                                        styles: { ...selectedItem.styles, color: e.target.value }
                                                    })}
                                                    className="w-8 h-8 rounded-lg border border-slate-200 cursor-pointer"
                                                    title="Textfarbe"
                                                />
                                            </div>
                                        </div>
                                    </div>
                                </div>
                            </>
                        )}

                        {/* Table Column Widths */}
                        {selectedItem.type === 'table' && (
                            <div className="border-t border-slate-100 pt-4">
                                <p className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider mb-2">Spaltenbreiten (px)</p>
                                <div className="grid grid-cols-3 gap-2">
                                    {(['pos', 'menge', 'me', 'bezeichnung', 'ep', 'gp'] as const).map(col => (
                                        <PropertyInput
                                            key={col}
                                            label={col.charAt(0).toUpperCase() + col.slice(1)}
                                            value={selectedItem.tableColumns?.[col] || 0}
                                            onChange={v => onUpdateBlock(selectedItem.id, {
                                                tableColumns: { ...selectedItem.tableColumns!, [col]: v }
                                            })}
                                        />
                                    ))}
                                </div>
                            </div>
                        )}

                        {/* Delete */}
                        <div className="border-t border-slate-100 pt-4">
                            <Button
                                variant="outline"
                                size="sm"
                                className="w-full text-red-600 border-red-200 hover:bg-red-50 hover:border-red-300"
                                onClick={() => onDeleteBlock(selectedItem.id)}
                            >
                                <Trash2 className="w-3.5 h-3.5 mr-1" />Block löschen
                            </Button>
                        </div>
                    </>
                ) : (
                    <div className="flex flex-col items-center justify-center py-12 text-center">
                        <div className="w-12 h-12 rounded-xl bg-slate-100 flex items-center justify-center mb-3">
                            <Type className="w-6 h-6 text-slate-300" />
                        </div>
                        <p className="text-sm font-medium text-slate-500">Kein Block ausgewählt</p>
                        <p className="text-[11px] text-slate-400 mt-1">Klicke auf einen Block im Canvas</p>
                    </div>
                )}

                {/* Document Type Assignment — always visible */}
                <div className="border-t border-slate-100 pt-4">
                    <p className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider mb-2.5">Dokumenttyp-Zuordnung</p>
                    <div className="space-y-1.5">
                        {DOCUMENT_TYPES.map(dt => {
                            const checked = selectedDokumenttypen.includes(dt);
                            return (
                                <label
                                    key={dt}
                                    className={`flex items-center gap-2.5 px-3 py-2 text-sm rounded-lg cursor-pointer transition-all duration-100 ${checked
                                        ? 'bg-rose-50 text-rose-700 font-medium'
                                        : 'hover:bg-slate-50 text-slate-600'
                                        }`}
                                >
                                    <input
                                        type="checkbox"
                                        checked={checked}
                                        onChange={e => {
                                            if (e.target.checked) onUpdateDokumenttypen([...selectedDokumenttypen, dt]);
                                            else onUpdateDokumenttypen(selectedDokumenttypen.filter(d => d !== dt));
                                        }}
                                        className="rounded border-slate-300 text-rose-600 focus:ring-rose-500"
                                    />
                                    {dt}
                                </label>
                            );
                        })}
                    </div>
                </div>
            </div>
        </div>
    );
}

// ─── Property Input ──────────────────────────────────────────────────

function PropertyInput({ label, value, onChange }: { label: string; value: number; onChange: (v: number) => void }) {
    return (
        <label className="flex flex-col">
            <span className="text-[10px] font-medium text-slate-400 mb-0.5">{label}</span>
            <input
                type="number"
                value={Math.round(value)}
                onChange={e => onChange(+e.target.value)}
                className="w-full p-1.5 text-xs text-center bg-slate-50 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-rose-500/20 focus:border-rose-400 transition-all"
            />
        </label>
    );
}
