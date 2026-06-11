import { FileText, Trash2, Plus } from 'lucide-react';
import { Button } from '../ui/button';
import { TiptapEditor } from '../TiptapEditor';
import { cn } from '../../lib/utils';
import type { DocBlock, EditorInstance } from './types';

interface TextBlockProps {
    block: DocBlock;
    isLocked: boolean;
    isActive: boolean;
    editorRefs: React.MutableRefObject<Record<string, EditorInstance | null>>;
    onEditorReady: (editorKey: string, editor: EditorInstance | null) => void;
    onUpdate: (id: string, updates: Partial<DocBlock>) => void;
    onRemove: (id: string) => void;
    onFocus: (blockId: string) => void;
    onEditorFocus: (editor: EditorInstance | null) => void;
    /** Bereitet block.content fuer den Editor auf: {{ZAHLUNGSZIEL}} → Chip, restliche Platzhalter → Klartext. */
    prepareContent: (text: string) => string;
    /** Serialisiert Editor-HTML zurueck fuer block.content: Chip → {{ZAHLUNGSZIEL}}. */
    serializeContent: (html: string) => string;
    /** Klick auf den Zahlungsziel-Chip: oeffnet das Bearbeitungs-Popover (Anker = Chip-Position). */
    onZahlungszielChipClick?: (anchor: DOMRect) => void;
    /** Optional: oeffnet den AddTypeDialog mit dieser Karte als Anker (Insert direkt darunter). */
    onAddBelow?: (anchorId: string) => void;
}

export function TextBlock({
    block,
    isLocked,
    isActive,
    editorRefs,
    onEditorReady,
    onUpdate,
    onRemove,
    onFocus,
    onEditorFocus,
    prepareContent,
    serializeContent,
    onZahlungszielChipClick,
    onAddBelow,
}: TextBlockProps) {
    return (
        <div className="group/card">
        <div
            className={cn(
                "bg-white rounded-xl border-l-[3px] border transition-all duration-200",
                isActive
                    ? "border-l-rose-500 border-rose-200 ring-2 ring-rose-500/30 shadow-md shadow-rose-50"
                    : "border-l-rose-300 border-slate-200 hover:border-slate-300 hover:shadow-sm"
            )}
            onClick={(e) => {
                onFocus(block.id);
                const chip = (e.target as HTMLElement).closest?.('[data-zahlungsziel-chip]');
                if (chip && !isLocked && onZahlungszielChipClick) {
                    onZahlungszielChipClick(chip.getBoundingClientRect());
                }
            }}
        >
            <div className="p-4">
                {/* Header */}
                <div className="flex items-center justify-between mb-2">
                    <div className="flex items-center gap-2">
                        <div className="p-1 bg-rose-50 rounded">
                            <FileText className="w-3.5 h-3.5 text-rose-400" />
                        </div>
                        <span className="text-[11px] font-semibold text-slate-400 uppercase tracking-wider">
                            Textbaustein
                        </span>
                    </div>
                    <Button
                        variant="ghost"
                        size="sm"
                        onClick={(e) => { e.stopPropagation(); onRemove(block.id); }}
                        disabled={isLocked}
                        className="h-7 w-7 p-0 text-slate-300 hover:text-rose-600 hover:bg-rose-50 rounded-md"
                    >
                        <Trash2 className="w-3.5 h-3.5" />
                    </Button>
                </div>

                {/* Editor */}
                <div className="ml-0.5">
                    <TiptapEditor
                        value={prepareContent(block.content || '')}
                        onChange={(val) => onUpdate(block.id, { content: serializeContent(val) })}
                        readOnly={isLocked}
                        hideToolbar={true}
                        compactMode={true}
                        onFocus={() => {
                            onFocus(block.id);
                            onEditorFocus(editorRefs.current[block.id]);
                        }}
                        onEditorReady={(editor) => onEditorReady(block.id, editor)}
                    />
                </div>
            </div>
        </div>
        {/* "+"-Button: fuegt direkt unter diesem Textbaustein ein neues Element ein. */}
        {!isLocked && onAddBelow && (
            <AddBelowButton onClick={() => onAddBelow(block.id)} />
        )}
        </div>
    );
}

/**
 * Schmale "+"-Pille unter einer Karte, oeffnet den AddTypeDialog.
 * Sichtbar bei Hover ueber die Karte (Wrapper mit group/card-Klasse) oder
 * wenn ein Kind den Fokus haelt.
 */
export function AddBelowButton({ onClick }: { onClick: () => void }) {
    return (
        <div className="flex justify-center -mt-1 mb-1 opacity-0 group-hover/card:opacity-100 focus-within:opacity-100 transition-opacity">
            <button
                type="button"
                onClick={(e) => { e.stopPropagation(); onClick(); }}
                title="Direkt darunter Leistung, Stundensatz oder Textbaustein einfügen"
                className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full border border-dashed border-rose-300 bg-white text-rose-600 text-[11px] font-medium hover:bg-rose-50 hover:border-rose-500 hover:shadow-sm transition-all"
            >
                <Plus className="w-3 h-3" />
                Hier einfügen
            </button>
        </div>
    );
}
