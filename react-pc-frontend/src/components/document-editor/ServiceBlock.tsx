import { Trash2 } from 'lucide-react';
import { Button } from '../ui/button';
import { TiptapEditor } from '../TiptapEditor';
import { cn } from '../../lib/utils';
import { formatCurrency, serviceLineTotal } from './helpers';
import type { DocBlock, EditorInstance } from './types';

interface ServiceBlockProps {
    block: DocBlock;
    positionNumber: string;
    isLocked: boolean;
    isActive: boolean;
    editorRefs: React.MutableRefObject<Record<string, EditorInstance | null>>;
    onUpdate: (id: string, updates: Partial<DocBlock>) => void;
    onRemove: (id: string) => void;
    onToggleOptional: (id: string, current: boolean | undefined) => void;
    onFocus: (blockId: string) => void;
    onEditorFocus: (editor: EditorInstance | null) => void;
}

export function ServiceBlock({
    block,
    positionNumber,
    isLocked,
    isActive,
    editorRefs,
    onUpdate,
    onRemove,
    onToggleOptional,
    onFocus,
    onEditorFocus,
}: ServiceBlockProps) {
    const total = serviceLineTotal(block);
    const hasDiscount = (block.discount ?? 0) > 0;

    return (
        <div
            className={cn(
                "bg-white rounded-xl border transition-all duration-200",
                isActive
                    ? "ring-2 ring-rose-500/50 border-rose-200 shadow-md shadow-rose-50"
                    : "border-slate-200 hover:border-slate-300 hover:shadow-sm",
                block.optional && "bg-slate-50/50"
            )}
            onClick={() => onFocus(block.id)}
        >
            {/* Header row: pos badge + title + actions */}
            <div className="flex items-start gap-3 p-4 pb-0">
                {/* Position badge */}
                <div className="flex-shrink-0 mt-0.5">
                    <div className={cn(
                        "w-10 h-10 rounded-lg flex items-center justify-center text-sm font-bold",
                        block.optional
                            ? "bg-amber-50 text-amber-500 border border-amber-200"
                            : "bg-rose-50 text-rose-600 border border-rose-100"
                    )}>
                        {positionNumber}
                    </div>
                </div>

                {/* Title */}
                <div className="flex-1 min-w-0 pt-1">
                    <input
                        type="text"
                        placeholder="Titel / Kurztext"
                        value={block.title || ''}
                        onChange={(e) => onUpdate(block.id, { title: e.target.value })}
                        disabled={isLocked}
                        className={cn(
                            "w-full font-semibold text-slate-900 bg-transparent border-none p-0 text-sm focus:ring-0 focus:outline-none placeholder:text-slate-300 disabled:text-slate-400",
                            block.optional && "italic text-slate-500"
                        )}
                    />
                </div>

                {/* Actions */}
                <div className="flex items-center gap-1 flex-shrink-0">
                    <Button
                        variant="ghost"
                        size="sm"
                        onClick={(e) => { e.stopPropagation(); onToggleOptional(block.id, block.optional); }}
                        disabled={isLocked}
                        className={cn(
                            "h-7 px-2 text-[11px] gap-1 rounded-md",
                            block.optional
                                ? "text-amber-600 bg-amber-50 hover:bg-amber-100"
                                : "text-slate-400 hover:text-slate-600 hover:bg-slate-100"
                        )}
                        title="Als Alternativ-Position markieren (nicht in Summe)"
                    >
                        {block.optional ? 'Alternativ' : 'Opt'}
                    </Button>
                    <Button
                        variant="ghost"
                        size="sm"
                        onClick={(e) => { e.stopPropagation(); onRemove(block.id); }}
                        disabled={isLocked}
                        className="h-7 w-7 p-0 text-slate-300 hover:text-rose-600 hover:bg-rose-50 rounded-md"
                    >
                        <Trash2 className="w-3.5 h-3.5" />
                    </Button>
                </div>
            </div>

            {/* Description - always visible */}
            <div className="px-4 pt-2 pb-3">
                <div className="pl-[52px]">
                    <TiptapEditor
                        value={block.description || ''}
                        onChange={(val) => onUpdate(block.id, { description: val })}
                        readOnly={isLocked}
                        hideToolbar={true}
                        compactMode={true}
                        onFocus={() => {
                            onFocus(block.id);
                            onEditorFocus(editorRefs.current[`${block.id}-desc`]);
                        }}
                        onEditorReady={(editor) => {
                            editorRefs.current[`${block.id}-desc`] = editor;
                        }}
                    />
                </div>
            </div>

            {/* Calculation row */}
            <div className="mx-4 mb-4 bg-slate-50 rounded-lg border border-slate-100 p-3">
                <div className="flex items-center gap-3">
                    {/* Menge + Einheit */}
                    <div className="flex items-center gap-1.5">
                        <div>
                            <label className="block text-[9px] font-semibold text-slate-400 uppercase tracking-wider mb-0.5">Menge</label>
                            <input
                                type="number"
                                min="0"
                                value={block.quantity || ''}
                                onFocus={(e) => { if (e.target.value === '0') e.target.value = ''; }}
                                onChange={(e) => {
                                    const val = e.target.value;
                                    if (val === '' || val === '-') { onUpdate(block.id, { quantity: 0 }); return; }
                                    const num = parseFloat(val);
                                    if (!isNaN(num) && num >= 0) onUpdate(block.id, { quantity: num });
                                }}
                                disabled={isLocked}
                                className="w-16 text-center text-sm font-semibold bg-white border border-slate-200 rounded-md px-2 py-1.5 focus:ring-2 focus:ring-rose-500/20 focus:border-rose-300 disabled:bg-slate-100 disabled:text-slate-400 transition-all"
                            />
                        </div>
                        <div>
                            <label className="block text-[9px] font-semibold text-slate-400 uppercase tracking-wider mb-0.5">Einheit</label>
                            <input
                                type="text"
                                value={block.unit || 'Stk'}
                                onChange={(e) => onUpdate(block.id, { unit: e.target.value })}
                                disabled={isLocked}
                                className="w-14 text-center text-xs text-slate-600 bg-white border border-slate-200 rounded-md px-1.5 py-1.5 focus:ring-2 focus:ring-rose-500/20 focus:border-rose-300 disabled:bg-slate-100 transition-all"
                            />
                        </div>
                    </div>

                    <span className="text-slate-300 text-sm font-light pt-3.5">×</span>

                    {/* Einzelpreis */}
                    <div>
                        <label className="block text-[9px] font-semibold text-slate-400 uppercase tracking-wider mb-0.5">EP</label>
                        <div className="relative">
                            <input
                                type="number"
                                step="0.01"
                                min="0"
                                value={block.price || ''}
                                onFocus={(e) => { if (e.target.value === '0') e.target.value = ''; }}
                                onChange={(e) => {
                                    const val = e.target.value;
                                    if (val === '' || val === '-') { onUpdate(block.id, { price: 0 }); return; }
                                    const num = parseFloat(val);
                                    if (!isNaN(num) && num >= 0) onUpdate(block.id, { price: num });
                                }}
                                disabled={isLocked}
                                className="w-24 text-right text-sm font-semibold bg-white border border-slate-200 rounded-md pl-2 pr-6 py-1.5 focus:ring-2 focus:ring-rose-500/20 focus:border-rose-300 disabled:bg-slate-100 disabled:text-slate-400 transition-all"
                            />
                            <span className="absolute right-2 top-1/2 -translate-y-1/2 text-slate-400 text-xs">€</span>
                        </div>
                    </div>

                    <span className="text-slate-300 text-sm font-light pt-3.5">=</span>

                    {/* Gesamtpreis */}
                    <div className="flex-1 text-right">
                        <label className="block text-[9px] font-semibold text-slate-400 uppercase tracking-wider mb-0.5">Gesamt</label>
                        <div className={cn(
                            "font-bold text-base py-0.5",
                            block.optional
                                ? "line-through text-slate-400 decoration-2"
                                : hasDiscount ? "text-rose-600" : "text-slate-900"
                        )}>
                            {formatCurrency(block.optional ? 0 : total)} €
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
}
