import { Trash2, Minus } from 'lucide-react';
import { Button } from '../ui/button';

interface SeparatorBlockProps {
    blockId: string;
    isLocked: boolean;
    onRemove: (id: string) => void;
}

export function SeparatorBlock({ blockId, isLocked, onRemove }: SeparatorBlockProps) {
    return (
        <div className="group relative py-2">
            {/* The visual separator line */}
            <div className="flex items-center gap-3">
                <div className="flex-1 border-t-2 border-slate-300 border-dashed" />
                <div className="flex items-center gap-1.5 px-2 py-0.5 bg-slate-50 rounded-full border border-slate-200">
                    <Minus className="w-3 h-3 text-slate-400" />
                    <span className="text-[10px] font-medium text-slate-400 uppercase tracking-wider">Trennlinie</span>
                </div>
                <div className="flex-1 border-t-2 border-slate-300 border-dashed" />
            </div>

            {/* Delete button on hover */}
            {!isLocked && (
                <div className="absolute right-0 top-1/2 -translate-y-1/2 opacity-0 group-hover:opacity-100 transition-opacity">
                    <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => onRemove(blockId)}
                        className="h-6 w-6 p-0 text-slate-300 hover:text-rose-600 hover:bg-rose-50 rounded-md"
                    >
                        <Trash2 className="w-3 h-3" />
                    </Button>
                </div>
            )}
        </div>
    );
}
