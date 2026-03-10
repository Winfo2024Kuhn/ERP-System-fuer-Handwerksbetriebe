import { Trash2, Calculator } from 'lucide-react';
import { Button } from '../ui/button';
import type { DocBlock } from './types';

interface SubtotalBlockProps {
    block: DocBlock;
    blocks: DocBlock[];
    isLocked: boolean;
    onRemove: (id: string) => void;
}

/**
 * Legacy SubtotalBlock - no longer used in the UI.
 * Subtotals are now auto-generated inside SectionHeaderBlock containers.
 * Kept for backward compatibility with old saved documents.
 */
export function SubtotalBlock({ block, isLocked, onRemove }: SubtotalBlockProps) {
    return (
        <div className="bg-gradient-to-r from-rose-50 to-white rounded-xl border border-rose-200 overflow-hidden">
            <div className="flex items-center justify-between p-3">
                <div className="flex items-center gap-3 flex-1 min-w-0">
                    <div className="w-9 h-9 bg-rose-100 rounded-lg flex items-center justify-center flex-shrink-0">
                        <Calculator className="w-4 h-4 text-rose-600" />
                    </div>
                    <div className="flex-1 min-w-0">
                        <div className="text-[10px] font-semibold text-rose-400 uppercase tracking-wider">
                            Zwischensumme (veraltet)
                        </div>
                        <div className="text-xs text-slate-500 mt-0.5">
                            Bitte Bauabschnitte verwenden
                        </div>
                    </div>
                </div>
                <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => onRemove(block.id)}
                    disabled={isLocked}
                    className="h-7 w-7 p-0 text-slate-300 hover:text-rose-600 hover:bg-rose-50 rounded-md flex-shrink-0"
                >
                    <Trash2 className="w-3.5 h-3.5" />
                </Button>
            </div>
        </div>
    );
}
