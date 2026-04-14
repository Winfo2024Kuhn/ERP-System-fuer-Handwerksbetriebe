import { useState } from 'react';
import { Building2, User, Shield, Search, ChevronDown, ChevronRight } from 'lucide-react';
import { Input } from '../ui/input';
import type { Abteilung, Mitarbeiter, En1090Rolle } from '../../types';

interface OrganigrammSidebarProps {
    abteilungen: Abteilung[];
    mitarbeiter: Mitarbeiter[];
    en1090Rollen: En1090Rolle[];
    showEn1090: boolean;
}

interface DragItem {
    entityType: 'abteilung' | 'mitarbeiter' | 'en1090rolle';
    entityId: number;
    label: string;
    qualifikation?: string | null;
    en1090RolleNames?: string | null;
    aktiv?: boolean;
    beschreibung?: string | null;
}

function SectionHeader({ label, count, isOpen, onToggle, color }: {
    label: string;
    count: number;
    isOpen: boolean;
    onToggle: () => void;
    color: string;
}) {
    return (
        <button
            onClick={onToggle}
            className="flex items-center gap-2 w-full text-left px-2 py-1.5 rounded-lg hover:bg-slate-50 transition-colors cursor-pointer"
        >
            {isOpen
                ? <ChevronDown className="w-3.5 h-3.5 text-slate-400" />
                : <ChevronRight className="w-3.5 h-3.5 text-slate-400" />
            }
            <span className={`text-xs font-semibold uppercase tracking-wider ${color}`}>
                {label}
            </span>
            <span className="text-[10px] text-slate-400 ml-auto">{count}</span>
        </button>
    );
}

function DraggableItem({ item, icon, accentClass }: {
    item: DragItem;
    icon: React.ReactNode;
    accentClass: string;
}) {
    const handleDragStart = (e: React.DragEvent) => {
        e.dataTransfer.setData('application/orgnode', JSON.stringify(item));
        e.dataTransfer.effectAllowed = 'move';
    };

    return (
        <div
            draggable
            onDragStart={handleDragStart}
            className={`flex items-center gap-2.5 px-3 py-2 rounded-xl border border-slate-200 bg-slate-50 hover:border-rose-300 hover:bg-rose-50 transition-all cursor-grab active:cursor-grabbing group ${accentClass}`}
        >
            <span className="w-7 h-7 flex-shrink-0 flex items-center justify-center bg-white border border-slate-200 rounded-lg group-hover:border-rose-300 transition-colors">
                {icon}
            </span>
            <span className="text-xs font-medium text-slate-700 group-hover:text-rose-700 truncate">
                {item.label}
            </span>
        </div>
    );
}

export default function OrganigrammSidebar({ abteilungen, mitarbeiter, en1090Rollen, showEn1090 }: OrganigrammSidebarProps) {
    const [search, setSearch] = useState('');
    const [showAbt, setShowAbt] = useState(true);
    const [showMit, setShowMit] = useState(true);
    const [showRol, setShowRol] = useState(true);
    const [showInactive, setShowInactive] = useState(false);

    const term = search.toLowerCase();

    const filteredAbt = abteilungen.filter(a => a.name.toLowerCase().includes(term));
    const filteredMit = mitarbeiter
        .filter(m => showInactive || m.aktiv !== false)
        .filter(m => `${m.vorname} ${m.nachname}`.toLowerCase().includes(term));
    const filteredRol = en1090Rollen
        .filter(r => r.aktiv)
        .filter(r => r.kurztext.toLowerCase().includes(term));

    return (
        <div className="h-full flex flex-col bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden">
            {/* Header */}
            <div className="px-4 py-3 border-b border-slate-100 bg-slate-50/50">
                <h3 className="text-sm font-bold text-slate-800 tracking-tight">Bausteine</h3>
                <p className="text-[11px] text-slate-400 mt-0.5">Ziehe Elemente auf die Zeichenfläche</p>
            </div>

            {/* Search */}
            <div className="px-3 pt-3 pb-1">
                <div className="relative">
                    <Search className="absolute left-2.5 top-2 w-3.5 h-3.5 text-slate-400" />
                    <Input
                        value={search}
                        onChange={e => setSearch(e.target.value)}
                        placeholder="Suchen..."
                        className="pl-8 h-8 text-xs"
                    />
                </div>
            </div>

            <div className="flex-1 overflow-y-auto p-3 space-y-3">

                {/* Abteilungen */}
                <div>
                    <SectionHeader
                        label="Abteilungen"
                        count={filteredAbt.length}
                        isOpen={showAbt}
                        onToggle={() => setShowAbt(!showAbt)}
                        color="text-rose-500"
                    />
                    {showAbt && (
                        <div className="mt-1.5 space-y-1">
                            {filteredAbt.map(a => (
                                <DraggableItem
                                    key={a.id}
                                    item={{ entityType: 'abteilung', entityId: a.id, label: a.name }}
                                    icon={<Building2 className="w-3.5 h-3.5 text-rose-500" />}
                                    accentClass=""
                                />
                            ))}
                            {filteredAbt.length === 0 && (
                                <p className="text-[11px] text-slate-400 px-2 py-1">Keine Abteilungen gefunden</p>
                            )}
                        </div>
                    )}
                </div>

                <div className="border-t border-slate-100" />

                {/* Mitarbeiter */}
                <div>
                    <SectionHeader
                        label="Mitarbeiter"
                        count={filteredMit.length}
                        isOpen={showMit}
                        onToggle={() => setShowMit(!showMit)}
                        color="text-slate-500"
                    />
                    {showMit && (
                        <div className="mt-1.5 space-y-1">
                            <label className="flex items-center gap-1.5 text-[10px] text-slate-400 px-2 cursor-pointer">
                                <input
                                    type="checkbox"
                                    checked={showInactive}
                                    onChange={e => setShowInactive(e.target.checked)}
                                    className="rounded border-slate-300 text-rose-600 focus:ring-rose-500 w-3 h-3"
                                />
                                Auch inaktive anzeigen
                            </label>
                            {filteredMit.map(m => (
                                <DraggableItem
                                    key={m.id}
                                    item={{
                                        entityType: 'mitarbeiter',
                                        entityId: m.id,
                                        label: `${m.vorname} ${m.nachname}`,
                                        qualifikation: m.qualifikation,
                                        en1090RolleNames: m.en1090RolleNames,
                                        aktiv: m.aktiv,
                                    }}
                                    icon={<User className="w-3.5 h-3.5 text-slate-500" />}
                                    accentClass=""
                                />
                            ))}
                            {filteredMit.length === 0 && (
                                <p className="text-[11px] text-slate-400 px-2 py-1">Keine Mitarbeiter gefunden</p>
                            )}
                        </div>
                    )}
                </div>

                {/* EN 1090 Rollen */}
                {showEn1090 && (
                    <>
                        <div className="border-t border-slate-100" />
                        <div>
                            <SectionHeader
                                label="EN 1090 Rollen"
                                count={filteredRol.length}
                                isOpen={showRol}
                                onToggle={() => setShowRol(!showRol)}
                                color="text-amber-500"
                            />
                            {showRol && (
                                <div className="mt-1.5 space-y-1">
                                    {filteredRol.map(r => (
                                        <DraggableItem
                                            key={r.id}
                                            item={{
                                                entityType: 'en1090rolle',
                                                entityId: r.id,
                                                label: r.kurztext,
                                                beschreibung: r.beschreibung,
                                            }}
                                            icon={<Shield className="w-3.5 h-3.5 text-amber-500" />}
                                            accentClass=""
                                        />
                                    ))}
                                    {filteredRol.length === 0 && (
                                        <p className="text-[11px] text-slate-400 px-2 py-1">Keine Rollen gefunden</p>
                                    )}
                                </div>
                            )}
                        </div>
                    </>
                )}
            </div>
        </div>
    );
}
