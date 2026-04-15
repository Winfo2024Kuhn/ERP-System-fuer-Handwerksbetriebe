import { X, GitBranch, Image, FileText, Printer, Save, AlignVerticalSpaceAround } from 'lucide-react';
import { Button } from '../ui/button';

interface OrganigrammToolbarProps {
    name?: string | null;
    onAutoLayout: (direction: 'TB' | 'LR') => void;
    onExportPng: () => void;
    onExportPdf: () => void;
    onPrint: () => void;
    onSave: () => void;
    onClose: () => void;
}

export default function OrganigrammToolbar({
    name,
    onAutoLayout,
    onExportPng,
    onExportPdf,
    onPrint,
    onSave,
    onClose,
}: OrganigrammToolbarProps) {
    return (
        <div className="flex items-center justify-between gap-4 py-3 px-4 bg-white border border-slate-200 rounded-2xl shadow-sm">
            {/* Left — Title */}
            <div className="flex items-center gap-3 min-w-0">
                <div className="flex items-center gap-2">
                    <GitBranch className="w-4 h-4 text-rose-600" />
                    <span className="text-sm font-semibold text-slate-800 truncate max-w-[200px]">
                        {name || 'Neues Organigramm'}
                    </span>
                </div>
            </div>

            {/* Center — Actions */}
            <div className="flex items-center gap-2 flex-shrink-0">
                {/* Layout */}
                <Button
                    variant="outline"
                    size="sm"
                    onClick={() => onAutoLayout('TB')}
                    title="Von oben nach unten anordnen"
                    className="border-slate-200 text-slate-600 hover:bg-slate-50"
                >
                    <GitBranch className="w-3.5 h-3.5 mr-1" />
                    Anordnen
                </Button>
                <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => onAutoLayout('LR')}
                    title="Von links nach rechts anordnen"
                    className="text-slate-500"
                >
                    <AlignVerticalSpaceAround className="w-3.5 h-3.5 mr-1 rotate-90" />
                    Seitlich
                </Button>

                <div className="h-6 w-px bg-slate-200" />

                {/* Export */}
                <Button
                    variant="ghost"
                    size="sm"
                    onClick={onExportPng}
                    title="Als Bild herunterladen"
                    className="text-slate-500"
                >
                    <Image className="w-3.5 h-3.5 mr-1" />
                    Als Bild
                </Button>
                <Button
                    variant="ghost"
                    size="sm"
                    onClick={onExportPdf}
                    title="Als PDF herunterladen"
                    className="text-slate-500"
                >
                    <FileText className="w-3.5 h-3.5 mr-1" />
                    Als PDF
                </Button>
                <Button
                    variant="ghost"
                    size="sm"
                    onClick={onPrint}
                    title="Organigramm drucken"
                    className="text-slate-500"
                >
                    <Printer className="w-3.5 h-3.5 mr-1" />
                    Drucken
                </Button>

                <div className="h-6 w-px bg-slate-200" />

                {/* Save */}
                <Button
                    size="sm"
                    onClick={onSave}
                >
                    <Save className="w-3.5 h-3.5 mr-1" />
                    Speichern
                </Button>
            </div>

            {/* Right — Close */}
            <button
                onClick={onClose}
                className="flex items-center justify-center w-8 h-8 rounded-lg text-slate-400 hover:text-red-600 hover:bg-red-50 transition-all cursor-pointer"
                title="Editor schließen"
            >
                <X className="w-5 h-5" />
            </button>
        </div>
    );
}
