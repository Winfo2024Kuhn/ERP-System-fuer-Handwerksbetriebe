import { ArrowLeft, Save, Eye, RotateCcw, Check, Pencil, Undo2, FileText } from 'lucide-react';
import { useState } from 'react';
import { Button } from '../ui/button';

interface EditorToolbarProps {
    templateName: string | null;
    hasUnsavedChanges?: boolean;
    onBack: () => void;
    onSave: () => void;
    onPreview: () => void;
    onReset: () => void;
    onRename: (name: string) => void;
    onUndo: () => void;
    canUndo: boolean;
    onOpenTextbausteinDefaults?: () => void;
    textbausteinDefaultsDisabled?: boolean;
}

/** Top toolbar for the template editor — shows template name, save, preview, back */
export default function EditorToolbar({
    templateName,
    onBack,
    onSave,
    onPreview,
    onReset,
    onRename,
    onUndo,
    canUndo,
    onOpenTextbausteinDefaults,
    textbausteinDefaultsDisabled,
}: EditorToolbarProps) {
    const [isEditing, setIsEditing] = useState(false);
    const [editName, setEditName] = useState('');

    const startEditing = () => {
        setEditName(templateName || 'Unbenannte Vorlage');
        setIsEditing(true);
    };

    const confirmRename = () => {
        if (editName.trim()) {
            onRename(editName.trim());
        }
        setIsEditing(false);
    };

    return (
        <div className="flex items-center justify-between gap-4 py-3 px-4 bg-white border border-slate-200 rounded-2xl shadow-sm">
            {/* Left — Back + Name */}
            <div className="flex items-center gap-3 min-w-0">
                <button
                    onClick={onBack}
                    className="flex items-center gap-1.5 px-2.5 py-1.5 text-sm font-medium text-slate-600 hover:text-rose-700 hover:bg-rose-50 rounded-lg transition-all"
                >
                    <ArrowLeft className="w-4 h-4" />
                    <span className="hidden sm:inline">Zurück</span>
                </button>

                <div className="h-6 w-px bg-slate-200" />

                {isEditing ? (
                    <div className="flex items-center gap-2">
                        <input
                            type="text"
                            value={editName}
                            onChange={e => setEditName(e.target.value)}
                            onKeyDown={e => { if (e.key === 'Enter') confirmRename(); if (e.key === 'Escape') setIsEditing(false); }}
                            className="px-2.5 py-1 text-sm font-semibold bg-slate-50 border border-rose-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-rose-500/20 w-48"
                            autoFocus
                        />
                        <button onClick={confirmRename} className="p-1.5 rounded-lg bg-rose-50 text-rose-600 hover:bg-rose-100 transition-colors">
                            <Check className="w-3.5 h-3.5" />
                        </button>
                    </div>
                ) : (
                    <button
                        onClick={startEditing}
                        className="flex items-center gap-1.5 min-w-0 group"
                    >
                        <span className="text-sm font-semibold text-slate-800 truncate">
                            {templateName || 'Unbenannte Vorlage'}
                        </span>
                        <Pencil className="w-3 h-3 text-slate-400 opacity-0 group-hover:opacity-100 transition-opacity flex-shrink-0" />
                    </button>
                )}
            </div>

            {/* Right — Actions */}
            <div className="flex items-center gap-2 flex-shrink-0">
                <Button
                    variant="ghost"
                    size="sm"
                    onClick={onUndo}
                    disabled={!canUndo}
                    className={canUndo ? 'text-slate-500' : 'text-slate-300'}
                    title="Rückgängig (Strg+Z)"
                >
                    <Undo2 className="w-3.5 h-3.5" />
                </Button>
                <Button
                    variant="ghost"
                    size="sm"
                    onClick={onReset}
                    className="text-slate-500"
                    title="Zurücksetzen"
                >
                    <RotateCcw className="w-3.5 h-3.5" />
                </Button>
                {onOpenTextbausteinDefaults && (
                    <Button
                        variant="outline"
                        size="sm"
                        onClick={onOpenTextbausteinDefaults}
                        disabled={textbausteinDefaultsDisabled}
                        title={textbausteinDefaultsDisabled
                            ? 'Vorlage zuerst speichern, um Standard-Texte festzulegen'
                            : 'Standard-Texte je Dokumenttyp festlegen'}
                    >
                        <FileText className="w-3.5 h-3.5 mr-1" />Standard-Texte
                    </Button>
                )}
                <Button
                    variant="outline"
                    size="sm"
                    onClick={onPreview}
                >
                    <Eye className="w-3.5 h-3.5 mr-1" />Vorschau
                </Button>
                <Button
                    size="sm"
                    onClick={onSave}
                >
                    <Save className="w-3.5 h-3.5 mr-1" />Speichern
                </Button>
            </div>
        </div>
    );
}
