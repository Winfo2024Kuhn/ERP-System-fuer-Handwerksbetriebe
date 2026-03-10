import { FileText, Trash2 } from 'lucide-react';
import { Button } from '../ui/button';
import { TiptapEditor } from '../TiptapEditor';
import { cn } from '../../lib/utils';
import type { DocBlock, EditorInstance } from './types';

interface TextBlockProps {
    block: DocBlock;
    isLocked: boolean;
    isActive: boolean;
    editorRefs: React.MutableRefObject<Record<string, EditorInstance | null>>;
    onUpdate: (id: string, updates: Partial<DocBlock>) => void;
    onRemove: (id: string) => void;
    onFocus: (blockId: string) => void;
    onEditorFocus: (editor: EditorInstance | null) => void;
    replacePlaceholders: (text: string) => string;
}

export function TextBlock({
    block,
    isLocked,
    isActive,
    editorRefs,
    onUpdate,
    onRemove,
    onFocus,
    onEditorFocus,
    replacePlaceholders,
}: TextBlockProps) {
    return (
        <div
            className={cn(
                "bg-white rounded-xl border-l-[3px] border transition-all duration-200",
                isActive
                    ? "border-l-rose-500 border-rose-200 ring-2 ring-rose-500/30 shadow-md shadow-rose-50"
                    : "border-l-rose-300 border-slate-200 hover:border-slate-300 hover:shadow-sm"
            )}
            onClick={() => onFocus(block.id)}
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
                        value={replacePlaceholders(block.content || '')}
                        onChange={(val) => onUpdate(block.id, { content: val })}
                        readOnly={isLocked}
                        hideToolbar={true}
                        compactMode={true}
                        onFocus={() => {
                            onFocus(block.id);
                            onEditorFocus(editorRefs.current[block.id]);
                        }}
                        onEditorReady={(editor) => {
                            editorRefs.current[block.id] = editor;
                        }}
                    />
                </div>
            </div>
        </div>
    );
}
