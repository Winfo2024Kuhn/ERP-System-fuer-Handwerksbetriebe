import { useSortable } from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';
import { GripVertical } from 'lucide-react';
import { cn } from '../../lib/utils';
import type { DocBlock } from './types';

interface SortableBlockProps {
    block: DocBlock;
    children: React.ReactNode;
    isLocked: boolean;
    isDragOverlay?: boolean;
}

export function SortableBlock({ block, children, isLocked, isDragOverlay }: SortableBlockProps) {
    const {
        attributes,
        listeners,
        setNodeRef,
        setActivatorNodeRef,
        transform,
        transition,
        isDragging,
        isSorting,
    } = useSortable({ id: block.id, disabled: isLocked });

    const style = {
        transform: CSS.Transform.toString(transform ? { ...transform, scaleX: 1, scaleY: 1 } : null),
        transition: isSorting ? transition : undefined,
        zIndex: isDragging ? 50 : 1,
        position: 'relative' as const,
    };

    // When this item is being dragged, show a subtle placeholder
    if (isDragging && !isDragOverlay) {
        return (
            <div
                ref={setNodeRef}
                style={style}
                className="rounded-xl border-2 border-dashed border-rose-300 bg-rose-50/50 min-h-[60px] transition-all duration-200"
            />
        );
    }

    // Overlay mode: elevated card look
    if (isDragOverlay) {
        return (
            <div className="rounded-xl shadow-2xl ring-2 ring-rose-400/40 bg-white/95 backdrop-blur-sm opacity-95 rotate-[0.5deg] scale-[1.02] pointer-events-none">
                {children}
            </div>
        );
    }

    return (
        <div
            ref={setNodeRef}
            style={style}
            className={cn("group relative transition-all duration-150")}
        >
            {/* Drag Handle */}
            {!isLocked && (
                <div
                    ref={setActivatorNodeRef}
                    {...attributes}
                    {...listeners}
                    className={cn(
                        "absolute left-[-32px] top-1/2 -translate-y-1/2 flex items-center justify-center",
                        "w-6 h-10 cursor-grab rounded-md transition-all duration-200",
                        "text-slate-300 opacity-40 group-hover:opacity-100",
                        "hover:text-rose-500 hover:bg-rose-50 active:cursor-grabbing active:text-rose-600 active:bg-rose-100 active:scale-110"
                    )}
                    title="Verschieben"
                >
                    <GripVertical className="w-4 h-4" />
                </div>
            )}
            {children}
        </div>
    );
}
