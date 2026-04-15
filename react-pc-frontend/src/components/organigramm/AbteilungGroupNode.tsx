import { memo } from 'react';
import { Handle, Position, type NodeProps } from '@xyflow/react';
import { Building2, X, Users } from 'lucide-react';

export interface AbteilungGroupNodeData {
    label: string;
    abteilungId?: number;
    childCount?: number;
    onDelete?: (id: string) => void;
    isDockTarget?: boolean;
    [key: string]: unknown;
}

const AbteilungGroupNodeComponent = ({ id, data }: NodeProps) => {
    const d = data as AbteilungGroupNodeData;
    const isEmpty = !d.childCount || d.childCount === 0;

    return (
        <div className="relative group" style={{ width: '100%', height: '100%' }}>
            {/* Target Handle — top of container */}
            <Handle
                type="target"
                position={Position.Top}
                className="!bg-rose-400 !w-3 !h-3 !border-2 !border-white !-top-1.5 !z-10"
            />

            {/* Container background */}
            <div
                className={`rounded-2xl border-2 transition-all duration-200 ${
                    d.isDockTarget
                        ? 'border-blue-400 bg-blue-50/40 shadow-lg shadow-blue-100 ring-2 ring-blue-300 ring-offset-1'
                        : 'border-dashed border-rose-200 bg-rose-50/30'
                }`}
                style={{ width: '100%', height: '100%', minHeight: 92 }}
            >
                {/* Header */}
                <div className="flex items-center gap-2.5 px-4 py-2.5 border-b border-rose-200/60">
                    <div className="w-8 h-8 rounded-lg bg-rose-100 flex items-center justify-center flex-shrink-0">
                        <Building2 className="w-4 h-4 text-rose-600" />
                    </div>
                    <div className="min-w-0 flex-1">
                        <p className="text-[10px] uppercase tracking-wider text-rose-500 font-semibold">Abteilung</p>
                        <p className="text-sm font-bold text-slate-900 truncate">{d.label}</p>
                    </div>
                    {d.childCount != null && d.childCount > 0 && (
                        <span className="flex items-center gap-1 text-[10px] text-rose-400 font-medium">
                            <Users className="w-3 h-3" />
                            {d.childCount}
                        </span>
                    )}
                </div>

                {/* Empty state placeholder */}
                {isEmpty && (
                    <div className="flex items-center justify-center py-3 px-4">
                        <p className="text-[11px] text-rose-300 italic">Mitarbeiter & Rollen hierher ziehen</p>
                    </div>
                )}
            </div>

            {/* Delete button */}
            {d.onDelete && (
                <button
                    onClick={(e) => { e.stopPropagation(); d.onDelete!(id); }}
                    className="absolute -top-2 -right-2 w-5 h-5 bg-white border border-slate-200 rounded-full flex items-center justify-center text-slate-400 hover:text-red-600 hover:border-red-300 opacity-0 group-hover:opacity-100 transition-all shadow-sm cursor-pointer z-20"
                >
                    <X className="w-3 h-3" />
                </button>
            )}

            {/* Source Handle — bottom of container */}
            <Handle
                type="source"
                position={Position.Bottom}
                className="!bg-rose-400 !w-3 !h-3 !border-2 !border-white !-bottom-1.5 !z-10"
            />
        </div>
    );
};

export const AbteilungGroupNode = memo(AbteilungGroupNodeComponent);
