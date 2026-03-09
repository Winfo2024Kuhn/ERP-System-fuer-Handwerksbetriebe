import { Check, ChevronDown, ChevronRight } from 'lucide-react';
import { cn } from '../lib/utils';
import type { DocBlock } from './document-editor/types';

const formatCurrency = (val: number | null | undefined) =>
    val !== null && val !== undefined
        ? new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR' }).format(val)
        : '-';

function stripHtml(html: string): string {
    const div = document.createElement('div');
    div.innerHTML = html;
    return div.textContent || div.innerText || '';
}

interface TeilrechnungPositionRowProps {
    block: DocBlock;
    selected: boolean;
    expanded: boolean;
    onToggleSelect: () => void;
    onToggleExpand: () => void;
    disabled?: boolean;
}

export function TeilrechnungPositionRow({ block, selected, expanded, onToggleSelect, onToggleExpand, disabled }: TeilrechnungPositionRowProps) {
    const lineTotal = (block.quantity || 0) * (block.price || 0);
    const hasLangtext = !!(block.content && stripHtml(block.content).trim());

    return (
        <div className={cn(
            "rounded-lg border transition-all",
            disabled ? "border-slate-200 bg-slate-50 opacity-50" : selected ? "border-rose-200 bg-rose-50/50" : "border-slate-100 bg-white"
        )}>
            <div className="flex items-center gap-2 px-3 py-2.5">
                {/* Checkbox */}
                <button
                    onClick={disabled ? undefined : onToggleSelect}
                    disabled={disabled}
                    className={cn(
                        "w-4 h-4 rounded border flex items-center justify-center flex-shrink-0",
                        disabled ? "bg-slate-300 border-slate-300 cursor-not-allowed" : selected ? "bg-rose-600 border-rose-600" : "border-slate-300"
                    )}
                >
                    {(selected || disabled) && <Check className="w-3 h-3 text-white" />}
                </button>

                {/* Expand Toggle */}
                {hasLangtext ? (
                    <button
                        onClick={onToggleExpand}
                        className={cn(
                            "w-5 h-5 flex items-center justify-center flex-shrink-0",
                            disabled ? "text-slate-300" : "text-slate-400 hover:text-rose-600"
                        )}
                    >
                        {expanded ? <ChevronDown className="w-4 h-4" /> : <ChevronRight className="w-4 h-4" />}
                    </button>
                ) : (
                    <div className="w-5" />
                )}

                {/* Titel & Details */}
                <div className={cn("flex-1 min-w-0", disabled ? "cursor-default" : "cursor-pointer")} onClick={disabled ? undefined : (hasLangtext ? onToggleExpand : onToggleSelect)}>
                    <p className={cn("text-sm font-medium truncate", disabled ? "text-slate-400" : "text-slate-800")}>
                        {block.pos && <span className={cn("mr-1.5", disabled ? "text-slate-300" : "text-slate-400")}>{block.pos}</span>}
                        {block.title || stripHtml(block.content || '') || 'Position'}
                    </p>
                    <p className={cn("text-xs mt-0.5", disabled ? "text-slate-300" : "text-slate-400")}>
                        {block.quantity} {block.unit} × {formatCurrency(block.price || 0)}
                        {disabled && <span className="ml-2 text-slate-400 italic">– bereits abgerechnet</span>}
                    </p>
                </div>

                {/* Gesamtpreis */}
                <span className={cn(
                    "text-sm font-semibold whitespace-nowrap",
                    disabled ? "text-slate-300" : selected ? "text-rose-700" : "text-slate-500"
                )}>
                    {formatCurrency(lineTotal)}
                </span>
            </div>

            {/* Aufklappbarer Langtext */}
            {expanded && hasLangtext && (
                <div className="px-3 pb-3 pt-0 ml-11">
                    <div
                        className="text-xs text-slate-600 bg-slate-50 rounded-lg p-3 border border-slate-100 prose prose-xs max-w-none"
                        dangerouslySetInnerHTML={{ __html: block.content! }}
                    />
                </div>
            )}
        </div>
    );
}

/** Alle SERVICE-Blocks (root + nested) aus einer Block-Liste extrahieren */
export function getAllServiceBlocks(blocks: DocBlock[]): DocBlock[] {
    const result: DocBlock[] = [];
    for (const b of blocks) {
        if (b.type === 'SERVICE') result.push(b);
        if (b.type === 'SECTION_HEADER' && b.children) {
            for (const c of b.children) {
                if (c.type === 'SERVICE') result.push(c);
            }
        }
    }
    return result;
}

/** Blocks filtern: nur ausgewählte SERVICE-Blocks behalten, Sections mit gefilterten Kindern */
export function filterBlocksBySelectedIds(blocks: DocBlock[], selectedIds: Set<string>): DocBlock[] {
    const result: DocBlock[] = [];
    for (const b of blocks) {
        if (b.type === 'SECTION_HEADER') {
            const filteredChildren = (b.children || []).filter(c => selectedIds.has(c.id));
            if (filteredChildren.length > 0) {
                result.push({ ...b, children: filteredChildren });
            }
        } else if (b.type === 'SERVICE') {
            if (selectedIds.has(b.id)) result.push(b);
        } else {
            result.push(b);
        }
    }
    return result;
}

/**
 * Alle Blocks beibehalten, aber bei nicht-ausgewählten SERVICE-Blocks
 * die Beträge auf 0 setzen ("noch nicht abgerechnet").
 */
export function zeroOutUnselectedBlocks(blocks: DocBlock[], selectedIds: Set<string>): DocBlock[] {
    const result: DocBlock[] = [];
    for (const b of blocks) {
        if (b.type === 'SECTION_HEADER') {
            const mappedChildren = (b.children || []).map(c => {
                if (c.type === 'SERVICE' && !selectedIds.has(c.id)) {
                    return { ...c, quantity: 0, price: 0 };
                }
                return c;
            });
            result.push({ ...b, children: mappedChildren });
        } else if (b.type === 'SERVICE') {
            if (!selectedIds.has(b.id)) {
                result.push({ ...b, quantity: 0, price: 0 });
            } else {
                result.push(b);
            }
        } else {
            result.push(b);
        }
    }
    return result;
}
