import { memo } from 'react';
import { Handle, Position, type NodeProps } from '@xyflow/react';
import { Building2, User, Shield, X } from 'lucide-react';

// ─── Node Data Interfaces ───────────────────────────────────────────────────

export interface AbteilungNodeData {
    label: string;
    onDelete?: (id: string) => void;
    [key: string]: unknown;
}

export interface MitarbeiterNodeData {
    label: string;
    qualifikation?: string | null;
    en1090RolleNames?: string | null;
    aktiv?: boolean;
    onDelete?: (id: string) => void;
    [key: string]: unknown;
}

export interface En1090RolleNodeData {
    label: string;
    beschreibung?: string | null;
    onDelete?: (id: string) => void;
    [key: string]: unknown;
}

// ─── Abteilung Node ─────────────────────────────────────────────────────────

const AbteilungNodeComponent = ({ id, data }: NodeProps) => {
    const nodeData = data as AbteilungNodeData;
    return (
        <div className="relative group min-w-[180px] max-w-[260px]">
            <Handle type="target" position={Position.Top} className="!bg-rose-400 !w-2.5 !h-2.5 !border-2 !border-white" />
            <div className="bg-white border border-slate-200 border-l-4 border-l-rose-500 rounded-lg shadow-sm px-4 py-3 hover:shadow-md transition-shadow">
                <div className="flex items-center gap-2.5">
                    <div className="w-8 h-8 rounded-lg bg-rose-50 flex items-center justify-center flex-shrink-0">
                        <Building2 className="w-4 h-4 text-rose-600" />
                    </div>
                    <div className="min-w-0">
                        <p className="text-[10px] uppercase tracking-wider text-rose-500 font-semibold">Abteilung</p>
                        <p className="text-sm font-bold text-slate-900 truncate">{nodeData.label}</p>
                    </div>
                </div>
            </div>
            {nodeData.onDelete && (
                <button
                    onClick={(e) => { e.stopPropagation(); nodeData.onDelete!(id); }}
                    className="absolute -top-2 -right-2 w-5 h-5 bg-white border border-slate-200 rounded-full flex items-center justify-center text-slate-400 hover:text-red-600 hover:border-red-300 opacity-0 group-hover:opacity-100 transition-all shadow-sm cursor-pointer"
                >
                    <X className="w-3 h-3" />
                </button>
            )}
            <Handle type="source" position={Position.Bottom} className="!bg-rose-400 !w-2.5 !h-2.5 !border-2 !border-white" />
        </div>
    );
};

// ─── Mitarbeiter Node ───────────────────────────────────────────────────────

const MitarbeiterNodeComponent = ({ id, data }: NodeProps) => {
    const nodeData = data as MitarbeiterNodeData;
    const rollenArr = nodeData.en1090RolleNames?.split(',').map(r => r.trim()).filter(Boolean) || [];

    return (
        <div className="relative group min-w-[180px] max-w-[280px]">
            <Handle type="target" position={Position.Top} className="!bg-slate-400 !w-2.5 !h-2.5 !border-2 !border-white" />
            <div className={`bg-white border border-slate-200 border-l-4 rounded-lg shadow-sm px-4 py-3 hover:shadow-md transition-shadow ${nodeData.aktiv !== false ? 'border-l-slate-400' : 'border-l-slate-300 opacity-60'}`}>
                <div className="flex items-center gap-2.5">
                    <div className="w-8 h-8 rounded-lg bg-slate-100 flex items-center justify-center flex-shrink-0">
                        <User className="w-4 h-4 text-slate-600" />
                    </div>
                    <div className="min-w-0">
                        <p className="text-[10px] uppercase tracking-wider text-slate-400 font-semibold">Mitarbeiter</p>
                        <p className="text-sm font-bold text-slate-900 truncate">{nodeData.label}</p>
                    </div>
                </div>

                {/* Badges */}
                <div className="flex flex-wrap gap-1 mt-2">
                    {nodeData.qualifikation && (
                        <span className="px-1.5 py-0.5 text-[10px] font-medium bg-slate-100 text-slate-600 rounded">
                            {nodeData.qualifikation}
                        </span>
                    )}
                    {rollenArr.map(r => (
                        <span key={r} className="px-1.5 py-0.5 text-[10px] font-medium bg-amber-50 text-amber-700 border border-amber-200 rounded">
                            {r}
                        </span>
                    ))}
                    {nodeData.aktiv === false && (
                        <span className="px-1.5 py-0.5 text-[10px] font-medium bg-red-50 text-red-600 rounded">
                            Inaktiv
                        </span>
                    )}
                </div>
            </div>
            {nodeData.onDelete && (
                <button
                    onClick={(e) => { e.stopPropagation(); nodeData.onDelete!(id); }}
                    className="absolute -top-2 -right-2 w-5 h-5 bg-white border border-slate-200 rounded-full flex items-center justify-center text-slate-400 hover:text-red-600 hover:border-red-300 opacity-0 group-hover:opacity-100 transition-all shadow-sm cursor-pointer"
                >
                    <X className="w-3 h-3" />
                </button>
            )}
            <Handle type="source" position={Position.Bottom} className="!bg-slate-400 !w-2.5 !h-2.5 !border-2 !border-white" />
        </div>
    );
};

// ─── EN 1090 Rolle Node ─────────────────────────────────────────────────────

const En1090RolleNodeComponent = ({ id, data }: NodeProps) => {
    const nodeData = data as En1090RolleNodeData;
    return (
        <div className="relative group min-w-[160px] max-w-[240px]">
            <Handle type="target" position={Position.Top} className="!bg-amber-400 !w-2.5 !h-2.5 !border-2 !border-white" />
            <div className="bg-white border border-slate-200 border-l-4 border-l-amber-500 rounded-lg shadow-sm px-4 py-3 hover:shadow-md transition-shadow">
                <div className="flex items-center gap-2.5">
                    <div className="w-8 h-8 rounded-lg bg-amber-50 flex items-center justify-center flex-shrink-0">
                        <Shield className="w-4 h-4 text-amber-600" />
                    </div>
                    <div className="min-w-0">
                        <p className="text-[10px] uppercase tracking-wider text-amber-500 font-semibold">EN 1090 Rolle</p>
                        <p className="text-sm font-bold text-slate-900 truncate">{nodeData.label}</p>
                    </div>
                </div>
                {nodeData.beschreibung && (
                    <p className="text-[11px] text-slate-500 mt-1.5 line-clamp-2">{nodeData.beschreibung}</p>
                )}
            </div>
            {nodeData.onDelete && (
                <button
                    onClick={(e) => { e.stopPropagation(); nodeData.onDelete!(id); }}
                    className="absolute -top-2 -right-2 w-5 h-5 bg-white border border-slate-200 rounded-full flex items-center justify-center text-slate-400 hover:text-red-600 hover:border-red-300 opacity-0 group-hover:opacity-100 transition-all shadow-sm cursor-pointer"
                >
                    <X className="w-3 h-3" />
                </button>
            )}
            <Handle type="source" position={Position.Bottom} className="!bg-amber-400 !w-2.5 !h-2.5 !border-2 !border-white" />
        </div>
    );
};

// ─── Exports ────────────────────────────────────────────────────────────────

export const AbteilungNode = memo(AbteilungNodeComponent);
export const MitarbeiterNode = memo(MitarbeiterNodeComponent);
export const En1090RolleNode = memo(En1090RolleNodeComponent);
