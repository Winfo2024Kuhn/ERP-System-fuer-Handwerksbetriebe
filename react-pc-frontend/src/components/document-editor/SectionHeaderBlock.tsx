import { useState } from 'react';
import { Trash2, FolderOpen, ChevronDown, ChevronUp, ArrowUpFromLine, FileText, Plus } from 'lucide-react';
import { useDroppable } from '@dnd-kit/core';
import { Button } from '../ui/button';
import { cn } from '../../lib/utils';
import { ServiceBlock } from './ServiceBlock';
import { calculateSectionSubtotal, formatCurrency } from './helpers';
import type { DocBlock, EditorInstance } from './types';

interface SectionHeaderBlockProps {
    block: DocBlock;
    isLocked: boolean;
    isActive: boolean;
    activeEditorId: string | null;
    editorRefs: React.MutableRefObject<Record<string, EditorInstance | null>>;
    onUpdate: (id: string, updates: Partial<DocBlock>) => void;
    onUpdateChild: (sectionId: string, childId: string, updates: Partial<DocBlock>) => void;
    onRemove: (id: string) => void;
    onRemoveChild: (sectionId: string, childId: string) => void;
    onEjectChild: (sectionId: string, childId: string) => void;
    onToggleChildOptional: (sectionId: string, childId: string, current: boolean | undefined) => void;
    onFocus: (blockId: string) => void;
    onEditorFocus: (editor: EditorInstance | null) => void;
    getPositionString: (block: DocBlock) => string;
    sectionPosition: string;
    /** Oeffnet den AddTypeDialog mit dem angegebenen Anker (Leistung-Karte unterhalb). */
    onAddBelow?: (anchorId: string) => void;
    /** Oeffnet den AddTypeDialog mit "in diesen Bauabschnitt einfuegen" als Ziel. */
    onAddIntoSection?: (sectionId: string) => void;
}

export function SectionHeaderBlock({
    block,
    isLocked,
    isActive,
    activeEditorId,
    editorRefs,
    onUpdate,
    onUpdateChild,
    onRemove,
    onRemoveChild,
    onEjectChild,
    onToggleChildOptional,
    onFocus,
    onEditorFocus,
    getPositionString,
    sectionPosition,
    onAddBelow,
    onAddIntoSection,
}: SectionHeaderBlockProps) {
    const [editing, setEditing] = useState(false);
    const [localLabel, setLocalLabel] = useState(block.sectionLabel || '');
    // Beim Oeffnen ist jeder Bauabschnitt zu — bei 20 Positionen ist die
    // Uebersicht sonst weg. Reine Ansichtssache, wird nicht persistiert.
    const [collapsed, setCollapsed] = useState(true);

    const { setNodeRef, isOver } = useDroppable({
        id: `section-drop-${block.id}`,
    });

    const children = block.children || [];
    const subtotal = calculateSectionSubtotal(block);

    const handleBlur = () => {
        setEditing(false);
        if (localLabel !== block.sectionLabel) {
            onUpdate(block.id, { sectionLabel: localLabel });
        }
    };

    const handleKeyDown = (e: React.KeyboardEvent) => {
        if (e.key === 'Enter') {
            (e.target as HTMLInputElement).blur();
        }
    };

    return (
        <div
            className={cn(
                "rounded-xl border-2 overflow-hidden transition-all duration-200",
                isOver
                    ? "border-rose-400 ring-2 ring-rose-400/30"
                    : isActive
                        ? "border-rose-300 ring-1 ring-rose-500/20 shadow-md"
                        : "border-slate-200 hover:border-slate-300 hover:shadow-sm"
            )}
            onClick={() => onFocus(block.id)}
        >
            {/* Section Header Bar */}
            <div className="bg-slate-800 px-4 py-2.5 flex items-center justify-between">
                <div className="flex items-center gap-3 flex-1 min-w-0">
                    <div className="w-8 h-8 bg-rose-600 rounded-lg flex items-center justify-center flex-shrink-0">
                        <FolderOpen className="w-4 h-4 text-white" />
                    </div>
                    <div className="flex-1 min-w-0">
                        <div className="text-[10px] font-semibold text-slate-400 uppercase tracking-wider mb-0.5">
                            Bauabschnitt {sectionPosition}
                        </div>
                        {editing && !isLocked ? (
                            <input
                                type="text"
                                value={localLabel}
                                onChange={(e) => setLocalLabel(e.target.value)}
                                onBlur={handleBlur}
                                onKeyDown={handleKeyDown}
                                autoFocus
                                placeholder="z.B. Rohbauarbeiten, Stahlkonstruktion..."
                                className="w-full bg-transparent text-white text-sm font-bold border-b border-rose-400 focus:outline-none placeholder:text-slate-500"
                            />
                        ) : (
                            <div
                                className="text-white text-sm font-bold truncate cursor-text hover:text-rose-200 transition-colors"
                                onClick={(e) => {
                                    e.stopPropagation();
                                    if (!isLocked) {
                                        setLocalLabel(block.sectionLabel || '');
                                        setEditing(true);
                                    }
                                }}
                            >
                                {block.sectionLabel || 'Klicken zum Benennen...'}
                            </div>
                        )}
                    </div>
                </div>
                <div className="flex items-center gap-1 flex-shrink-0">
                    {children.length > 0 && (
                        <span className="text-[10px] font-medium text-slate-400 mr-1">
                            {children.length} Pos.
                        </span>
                    )}
                    {!isLocked && onAddIntoSection && (
                        <Button
                            variant="ghost"
                            size="sm"
                            onClick={(e) => { e.stopPropagation(); onAddIntoSection(block.id); }}
                            className="h-7 w-7 p-0 text-slate-300 hover:text-rose-300 hover:bg-slate-700 rounded-md"
                            title="In diesen Bauabschnitt einfügen (Leistung, Stundensatz oder Textbaustein)"
                        >
                            <Plus className="w-3.5 h-3.5" />
                        </Button>
                    )}
                    <Button
                        variant="ghost"
                        size="sm"
                        onClick={(e) => { e.stopPropagation(); setCollapsed(!collapsed); }}
                        aria-label={collapsed ? 'Aufklappen' : 'Zuklappen'}
                        aria-expanded={!collapsed}
                        className="h-7 w-7 p-0 text-slate-400 hover:text-white hover:bg-slate-700 rounded-md"
                    >
                        {collapsed ? <ChevronDown className="w-3.5 h-3.5" /> : <ChevronUp className="w-3.5 h-3.5" />}
                    </Button>
                    <Button
                        variant="ghost"
                        size="sm"
                        onClick={(e) => { e.stopPropagation(); onRemove(block.id); }}
                        disabled={isLocked}
                        className="h-7 w-7 p-0 text-slate-400 hover:text-rose-400 hover:bg-slate-700 rounded-md"
                    >
                        <Trash2 className="w-3.5 h-3.5" />
                    </Button>
                </div>
            </div>

            {/* Collapsed Summary */}
            {collapsed && children.length > 0 && (
                <div className="px-4 py-2 bg-slate-50 border-t border-slate-100 flex items-center justify-between">
                    <span className="text-xs text-slate-500">
                        {children.length} Leistung{children.length !== 1 ? 'en' : ''}
                    </span>
                    <span className="text-xs font-bold text-slate-700">
                        {formatCurrency(subtotal)} €
                    </span>
                </div>
            )}

            {/* Children + Drop Zone (when expanded) */}
            {!collapsed && (
                <div ref={setNodeRef} className="bg-slate-50/50 min-h-[1px]">
                    {/* Children */}
                    {children.length > 0 && (
                        <div className="px-3 pt-3 space-y-2">
                            {children.map(child => (
                                <div key={child.id} className="relative group/child group/card">
                                    {child.type === 'TEXT' ? (
                                        /* Inline TEXT (Remark) block within section */
                                        <>
                                        <div className="bg-white rounded-lg border border-slate-200 p-3">
                                            <div className="flex items-center gap-2 mb-1">
                                                <FileText className="w-3 h-3 text-slate-400" />
                                                <span className="text-[10px] font-semibold text-slate-400 uppercase tracking-wider">Hinweis</span>
                                            </div>
                                            <div
                                                className="text-xs text-slate-600 leading-relaxed prose prose-xs max-w-none"
                                                dangerouslySetInnerHTML={{ __html: child.content || '' }}
                                            />
                                        </div>
                                        {!isLocked && onAddBelow && (
                                            <div className="flex justify-center -mt-1 mb-1 opacity-0 group-hover/card:opacity-100 transition-opacity">
                                                <button
                                                    type="button"
                                                    onClick={(e) => { e.stopPropagation(); onAddBelow(child.id); }}
                                                    title="Direkt darunter einfügen"
                                                    className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full border border-dashed border-rose-300 bg-white text-rose-600 text-[11px] font-medium hover:bg-rose-50 hover:border-rose-500 hover:shadow-sm transition-all"
                                                >
                                                    <Plus className="w-3 h-3" />
                                                    Hier einfügen
                                                </button>
                                            </div>
                                        )}
                                        </>
                                    ) : (
                                        <ServiceBlock
                                            block={child}
                                            positionNumber={getPositionString(child)}
                                            isLocked={isLocked}
                                            isActive={activeEditorId === child.id}
                                            editorRefs={editorRefs}
                                            onEditorReady={(key, editor) => { editorRefs.current[key] = editor; }}
                                            onUpdate={(id, updates) => onUpdateChild(block.id, id, updates)}
                                            onRemove={(id) => onRemoveChild(block.id, id)}
                                            onToggleOptional={(id, current) => onToggleChildOptional(block.id, id, current)}
                                            onFocus={onFocus}
                                            onEditorFocus={onEditorFocus}
                                            onAddBelow={onAddBelow}
                                        />
                                    )}
                                    {/* Eject button */}
                                    {!isLocked && (
                                        <button
                                            onClick={(e) => {
                                                e.stopPropagation();
                                                onEjectChild(block.id, child.id);
                                            }}
                                            className="absolute -right-2 top-2 opacity-0 group-hover/child:opacity-100 transition-opacity z-10 w-6 h-6 rounded-full bg-white border border-slate-200 shadow-sm flex items-center justify-center hover:bg-rose-50 hover:border-rose-300"
                                            title="Aus Bauabschnitt entfernen"
                                        >
                                            <ArrowUpFromLine className="w-3 h-3 text-slate-400 hover:text-rose-500" />
                                        </button>
                                    )}
                                </div>
                            ))}
                        </div>
                    )}

                    {/* Drop Zone + "+"-Button */}
                    {!isLocked && (
                        <div className={cn(
                            "mx-3 my-3 py-3 border-2 border-dashed rounded-lg flex items-center justify-center gap-3 transition-all duration-200",
                            isOver
                                ? "border-rose-400 bg-rose-50 text-rose-600"
                                : children.length === 0
                                    ? "border-slate-300 text-slate-400"
                                    : "border-slate-200 text-slate-400 hover:border-slate-300"
                        )}>
                            {isOver ? (
                                <p className="text-xs font-medium">↓ Hier ablegen</p>
                            ) : (
                                <>
                                    <p className="text-xs">
                                        {children.length === 0 ? 'Bauabschnitt befüllen:' : 'Weitere Position hinzufügen:'}
                                    </p>
                                    {onAddIntoSection && (
                                        <button
                                            type="button"
                                            onClick={(e) => { e.stopPropagation(); onAddIntoSection(block.id); }}
                                            className="inline-flex items-center gap-1 px-3 py-1 rounded-full bg-rose-600 text-white text-xs font-semibold hover:bg-rose-700 shadow-sm transition-colors"
                                            title="Leistung, Stundensatz oder Textbaustein einfügen"
                                        >
                                            <Plus className="w-3.5 h-3.5" />
                                            Einfügen
                                        </button>
                                    )}
                                    <span className="text-[10px] text-slate-400">oder per Drag &amp; Drop</span>
                                </>
                            )}
                        </div>
                    )}

                    {/* Auto-Subtotal */}
                    {children.length > 0 && (
                        <div className="px-3 pb-3">
                            <div className="flex items-center justify-between px-4 py-2.5 bg-rose-50 border border-rose-200 rounded-lg">
                                <span className="text-xs font-semibold text-rose-700 uppercase tracking-wide">
                                    Zwischensumme {block.sectionLabel || ''}
                                </span>
                                <span className="text-sm font-bold text-slate-900">
                                    {formatCurrency(subtotal)} €
                                </span>
                            </div>
                        </div>
                    )}
                </div>
            )}
        </div>
    );
}
