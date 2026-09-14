import { useState } from 'react';
import { useDroppable } from '@dnd-kit/core';
import { Inbox, Send, FileEdit, Star, Trash2, ShieldAlert, Newspaper, Briefcase, FileText,
    Package, Calculator, AlertCircle, Settings, PanelLeftClose, PanelLeftOpen, PenSquare, ChevronDown,
    type LucideIcon } from 'lucide-react';
import { Button } from '../../components/ui/button';
import { cn } from '../../lib/utils';
import type { FolderType } from './emailCenterModel';

type Counts = Record<Exclude<FolderType, 'tax-advisors'> | 'taxAdvisors', number>;
type Folder = { id: FolderType; label: string; icon: LucideIcon; droppable?: boolean };
const mailboxFolders: Folder[] = [
    { id: 'inbox', label: 'Posteingang', icon: Inbox, droppable: true },
    { id: 'sent', label: 'Gesendet', icon: Send },
    { id: 'drafts', label: 'Entwürfe', icon: FileEdit },
    { id: 'starred', label: 'Markiert', icon: Star },
    { id: 'newsletter', label: 'Newsletter', icon: Newspaper, droppable: true },
    { id: 'spam', label: 'Spam', icon: ShieldAlert, droppable: true },
    { id: 'trash', label: 'Papierkorb', icon: Trash2, droppable: true },
];
const assignedFolders: Folder[] = [
    { id: 'unassigned', label: 'Nicht zugeordnet', icon: AlertCircle },
    { id: 'projects', label: 'Projekte', icon: Briefcase },
    { id: 'offers', label: 'Anfragen', icon: FileText },
    { id: 'suppliers', label: 'Lieferanten', icon: Package },
    { id: 'tax-advisors', label: 'Steuerberater', icon: Calculator },
];

function FolderButton({ folder, count, active, collapsed, dragActive, onSelect }: {
    folder: Folder; count: number; active: boolean; collapsed: boolean; dragActive: boolean;
    onSelect: (folder: FolderType) => void;
}) {
    const { isOver, setNodeRef } = useDroppable({
        id: `folder-${folder.id}`, disabled: !folder.droppable, data: { folderId: folder.id },
    });
    return <button ref={setNodeRef} type="button" onClick={() => onSelect(folder.id)}
        aria-label={folder.label} aria-current={active ? 'page' : undefined}
        title={folder.label}
        className={cn('relative flex w-full min-w-0 items-center gap-2.5 rounded-lg px-3 py-2 text-sm font-medium cursor-pointer transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-500',
            active ? 'bg-rose-100/70 text-rose-700' : 'text-slate-600 hover:bg-slate-200/60',
            collapsed && 'justify-center px-0', isOver && 'ring-2 ring-rose-400 bg-rose-100',
            dragActive && !folder.droppable && 'opacity-40')}>
        <folder.icon className="h-4 w-4 shrink-0" />
        {!collapsed && <span className="min-w-0 flex-1 text-left break-words">{folder.label}</span>}
        {count > 0 && <span aria-hidden="true" className={cn('text-[11px] tabular-nums text-slate-500',
            collapsed && 'absolute right-0 top-0 rounded-full bg-slate-200 px-1 text-[9px]',
            active && 'text-rose-700')}>
            {collapsed && count > 99 ? '99+' : count}
        </span>}
    </button>;
}

export function EmailFolderSidebar({ width, collapsed, resizing, activeFolder, counts, dragActive,
    showSettings, onToggle, onCompose, onSelect, onSettings }: {
    width: number; collapsed: boolean; resizing: boolean; activeFolder: FolderType; counts: Counts;
    dragActive: boolean; showSettings: boolean; onToggle: () => void; onCompose: () => void;
    onSelect: (folder: FolderType) => void; onSettings: () => void;
}) {
    const [expanded, setExpanded] = useState(true);
    const toggleLabel = collapsed ? 'Ordnerleiste ausklappen' : 'Ordnerleiste einklappen';
    const renderFolder = (folder: Folder) => <FolderButton key={folder.id} folder={folder}
        count={counts[folder.id === 'tax-advisors' ? 'taxAdvisors' : folder.id]}
        active={activeFolder === folder.id && !showSettings} collapsed={collapsed}
        dragActive={dragActive} onSelect={onSelect} />;
    return <nav aria-label="E-Mail-Ordner" style={{ width }}
        className={cn('flex min-h-0 shrink-0 flex-col border-r border-slate-200 bg-slate-50', !resizing && 'motion-safe:transition-[width]')}>
        <div className="shrink-0 border-b border-slate-200 p-2.5">
            <div className={cn('mb-2 flex items-center justify-between gap-1', collapsed && 'justify-center')}>
                {!collapsed && <h2 className="text-sm font-semibold text-slate-900">E-Mail Center</h2>}
                <button type="button" title={toggleLabel} aria-label={toggleLabel} aria-expanded={!collapsed}
                    onClick={onToggle} className="rounded-md p-1.5 text-slate-500 hover:bg-slate-200 cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-500">
                    {collapsed ? <PanelLeftOpen className="h-4 w-4" /> : <PanelLeftClose className="h-4 w-4" />}
                </button>
            </div>
            <Button size="sm" onClick={onCompose} title="Neue E-Mail schreiben" aria-label="Neue E-Mail"
                className={cn('w-full rounded-lg', collapsed && 'px-0')}>
                <PenSquare className="h-4 w-4 shrink-0" />{!collapsed && 'Neue E-Mail'}
            </Button>
        </div>
        <div className="min-h-0 flex-1 overflow-y-auto overflow-x-hidden p-2 space-y-0.5">
            {mailboxFolders.map(renderFolder)}
            <div className="my-2 border-t border-slate-200" />
            {!collapsed && <button type="button" onClick={() => setExpanded(value => !value)} aria-expanded={expanded}
                className="flex w-full items-center justify-between px-3 py-2 text-[10px] font-semibold uppercase tracking-wider text-slate-500 hover:text-slate-800 cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-500">
                Zugeordnet<ChevronDown className={cn('h-3.5 w-3.5', !expanded && '-rotate-90')} />
            </button>}
            {(expanded || collapsed) && assignedFolders.map(renderFolder)}
        </div>
        <button type="button" onClick={onSettings} title="Einstellungen" aria-label="Einstellungen"
            className={cn('flex shrink-0 items-center gap-2.5 border-t border-slate-200 px-5 py-3 text-sm font-medium text-slate-600 hover:bg-slate-100 cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-500',
                collapsed && 'justify-center px-0', showSettings && 'bg-rose-50 text-rose-700')}>
            <Settings className="h-4 w-4 shrink-0" />{!collapsed && 'Einstellungen'}
        </button>
    </nav>;
}
