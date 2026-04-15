import { Building2, User, Shield, Trash2, Users } from 'lucide-react';
import { Button } from '../ui/button';
import type { Node } from '@xyflow/react';

interface OrganigrammPropertiesSidebarProps {
    selectedNode: Node | null;
    onDeleteNode: (id: string) => void;
}

export default function OrganigrammPropertiesSidebar({
    selectedNode,
    onDeleteNode,
}: OrganigrammPropertiesSidebarProps) {
    if (!selectedNode) {
        return (
            <div className="h-full flex flex-col bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden">
                <div className="px-4 py-3 border-b border-slate-100 bg-slate-50/50">
                    <h3 className="text-sm font-bold text-slate-800 tracking-tight">Eigenschaften</h3>
                    <p className="text-[11px] text-slate-400 mt-0.5">Wähle ein Element zum Bearbeiten</p>
                </div>
                <div className="flex-1 flex items-center justify-center p-6">
                    <p className="text-sm text-slate-400 text-center">
                        Klicke auf ein Element im Organigramm, um Details zu sehen.
                    </p>
                </div>
            </div>
        );
    }

    const data = selectedNode.data as Record<string, string | number | boolean | null | undefined>;
    const nodeType = selectedNode.type;

    const icon = (nodeType === 'abteilungGroup' || nodeType === 'abteilung')
        ? <Building2 className="w-5 h-5 text-rose-600" />
        : nodeType === 'mitarbeiter' ? <User className="w-5 h-5 text-slate-600" />
        : <Shield className="w-5 h-5 text-amber-600" />;

    const typeLabel = (nodeType === 'abteilungGroup' || nodeType === 'abteilung')
        ? 'Abteilung'
        : nodeType === 'mitarbeiter' ? 'Mitarbeiter'
        : 'EN 1090 Rolle';

    const typeBg = (nodeType === 'abteilungGroup' || nodeType === 'abteilung')
        ? 'bg-rose-50 text-rose-700 border-rose-200'
        : nodeType === 'mitarbeiter' ? 'bg-slate-100 text-slate-700 border-slate-200'
        : 'bg-amber-50 text-amber-700 border-amber-200';

    const rollenArr = typeof data.en1090RolleNames === 'string'
        ? data.en1090RolleNames.split(',').map((r: string) => r.trim()).filter(Boolean)
        : [];

    const isDocked = !!data.isDocked;

    return (
        <div className="h-full flex flex-col bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden">
            <div className="px-4 py-3 border-b border-slate-100 bg-slate-50/50">
                <h3 className="text-sm font-bold text-slate-800 tracking-tight">Eigenschaften</h3>
                <p className="text-[11px] text-slate-400 mt-0.5">Details zum ausgewählten Element</p>
            </div>

            <div className="flex-1 overflow-y-auto p-4 space-y-4">
                {/* Type Badge */}
                <div className="flex items-center gap-2">
                    {icon}
                    <span className={`px-2 py-1 text-xs font-medium border rounded-lg ${typeBg}`}>
                        {typeLabel}
                    </span>
                    {isDocked && (
                        <span className="px-2 py-1 text-xs font-medium bg-blue-50 text-blue-600 border border-blue-200 rounded-lg">
                            In Gruppe
                        </span>
                    )}
                </div>

                {/* Name */}
                <div>
                    <p className="text-[10px] uppercase tracking-wider text-slate-400 font-semibold mb-1">Name</p>
                    <p className="text-sm font-semibold text-slate-900">{String(data.label ?? '')}</p>
                </div>

                {/* Group-specific: member count */}
                {(nodeType === 'abteilungGroup') && data.childCount != null && (
                    <div>
                        <p className="text-[10px] uppercase tracking-wider text-slate-400 font-semibold mb-1">Mitglieder</p>
                        <div className="flex items-center gap-1.5">
                            <Users className="w-4 h-4 text-rose-400" />
                            <span className="text-sm font-medium text-slate-700">
                                {Number(data.childCount)} {Number(data.childCount) === 1 ? 'Mitglied' : 'Mitglieder'}
                            </span>
                        </div>
                    </div>
                )}

                {/* Mitarbeiter-specific */}
                {nodeType === 'mitarbeiter' && (
                    <>
                        {data.qualifikation && (
                            <div>
                                <p className="text-[10px] uppercase tracking-wider text-slate-400 font-semibold mb-1">Qualifikation</p>
                                <span className="px-2 py-1 text-xs font-medium bg-slate-100 text-slate-600 rounded">
                                    {String(data.qualifikation)}
                                </span>
                            </div>
                        )}
                        {rollenArr.length > 0 && (
                            <div>
                                <p className="text-[10px] uppercase tracking-wider text-slate-400 font-semibold mb-1">EN 1090 Rollen</p>
                                <div className="flex flex-wrap gap-1">
                                    {rollenArr.map((r) => (
                                        <span key={r} className="px-1.5 py-0.5 text-[10px] font-medium bg-amber-50 text-amber-700 border border-amber-200 rounded">
                                            {r}
                                        </span>
                                    ))}
                                </div>
                            </div>
                        )}
                        <div>
                            <p className="text-[10px] uppercase tracking-wider text-slate-400 font-semibold mb-1">Status</p>
                            {data.aktiv !== false ? (
                                <span className="px-2 py-1 text-xs font-medium bg-green-50 text-green-700 border border-green-200 rounded">
                                    Aktiv
                                </span>
                            ) : (
                                <span className="px-2 py-1 text-xs font-medium bg-red-50 text-red-600 border border-red-200 rounded">
                                    Inaktiv
                                </span>
                            )}
                        </div>
                    </>
                )}

                {/* EN 1090 Rolle-specific */}
                {nodeType === 'en1090rolle' && data.beschreibung && (
                    <div>
                        <p className="text-[10px] uppercase tracking-wider text-slate-400 font-semibold mb-1">Beschreibung</p>
                        <p className="text-xs text-slate-600 leading-relaxed">{String(data.beschreibung)}</p>
                    </div>
                )}
            </div>

            {/* Delete Button */}
            <div className="px-4 py-3 border-t border-slate-100">
                <Button
                    variant="outline"
                    size="sm"
                    className="w-full border-red-200 text-red-600 hover:bg-red-50 hover:text-red-700"
                    onClick={() => onDeleteNode(selectedNode.id)}
                >
                    <Trash2 className="w-3.5 h-3.5 mr-1.5" />
                    {(nodeType === 'abteilungGroup') ? 'Gruppe auflösen' : 'Vom Organigramm entfernen'}
                </Button>
            </div>
        </div>
    );
}
