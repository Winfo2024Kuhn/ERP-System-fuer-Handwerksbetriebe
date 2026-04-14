import { ArrowLeft, GitBranch, Image, FileText, Printer, Save, AlignVerticalSpaceAround } from 'lucide-react';
import { Button } from '../ui/button';

interface OrganigrammToolbarProps {
    onAutoLayout: (direction: 'TB' | 'LR') => void;
    onExportPng: () => void;
    onExportPdf: () => void;
    onPrint: () => void;
    onSave: () => void;
    onBack: () => void;
}

export default function OrganigrammToolbar({
    onAutoLayout,
    onExportPng,
    onExportPdf,
    onPrint,
    onSave,
    onBack,
}: OrganigrammToolbarProps) {
    return (
        <div className="flex items-center justify-between gap-4 py-3 px-4 bg-white border border-slate-200 rounded-2xl shadow-sm">
            {/* Left — Back + Title */}
            <div className="flex items-center gap-3 min-w-0">
                <button
                    onClick={onBack}
                    className="flex items-center gap-1.5 px-2.5 py-1.5 text-sm font-medium text-slate-600 hover:text-rose-700 hover:bg-rose-50 rounded-lg transition-all cursor-pointer"
                >
                    <ArrowLeft className="w-4 h-4" />
                    <span className="hidden sm:inline">Zurück</span>
                </button>

                <div className="h-6 w-px bg-slate-200" />

                <div className="flex items-center gap-2">
                    <GitBranch className="w-4 h-4 text-rose-600" />
                    <span className="text-sm font-semibold text-slate-800">Organigramm</span>
                </div>
            </div>

            {/* Right — Actions */}
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
        </div>
    );
}
