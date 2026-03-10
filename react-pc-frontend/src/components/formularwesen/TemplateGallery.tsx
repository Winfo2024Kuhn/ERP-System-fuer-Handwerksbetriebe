import { useState } from 'react';
import { Plus, Search, FileText, LayoutTemplate, Copy, Trash2, Pencil, Filter, Calendar } from 'lucide-react';
import { Button } from '../ui/button';
import { Card } from '../ui/card';
import type { FormTemplateListItem } from '../../types';
import { DOCUMENT_TYPES } from './constants';
import { useConfirm } from '../ui/confirm-dialog';

interface TemplateGalleryProps {
    templates: FormTemplateListItem[];
    onCreateNew: () => void;
    onEdit: (name: string) => void;
    onDuplicate: (name: string) => void;
    onDelete: (name: string) => void;
    onRefresh: () => void;
}

/** Canva-style template gallery — shows saved templates as visual cards */
export default function TemplateGallery({ templates, onCreateNew, onEdit, onDuplicate, onDelete }: TemplateGalleryProps) {
    const [search, setSearch] = useState('');
    const [filterType, setFilterType] = useState<string | null>(null);

    const filteredTemplates = templates.filter(t => {
        const matchesSearch = !search || t.name.toLowerCase().includes(search.toLowerCase());
        const matchesFilter = !filterType || (t.assignedDokumenttypen || []).includes(filterType);
        return matchesSearch && matchesFilter;
    });

    return (
        <div className="space-y-6">
            {/* Search & Filter Bar */}
            <div className="flex flex-col sm:flex-row gap-3 items-stretch sm:items-center">
                <div className="relative flex-1 max-w-md">
                    <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
                    <input
                        type="text"
                        value={search}
                        onChange={e => setSearch(e.target.value)}
                        placeholder="Vorlagen durchsuchen..."
                        className="w-full pl-10 pr-4 py-2.5 bg-white border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-rose-500/20 focus:border-rose-400 transition-all"
                    />
                </div>
                <div className="flex gap-2 flex-wrap">
                    <button
                        onClick={() => setFilterType(null)}
                        className={`px-3 py-2 text-xs font-medium rounded-lg transition-all ${!filterType
                            ? 'bg-rose-600 text-white shadow-sm'
                            : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
                            }`}
                    >
                        <Filter className="w-3 h-3 inline mr-1" />Alle
                    </button>
                    {DOCUMENT_TYPES.map(dt => (
                        <button
                            key={dt}
                            onClick={() => setFilterType(filterType === dt ? null : dt)}
                            className={`px-3 py-2 text-xs font-medium rounded-lg transition-all ${filterType === dt
                                ? 'bg-rose-600 text-white shadow-sm'
                                : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
                                }`}
                        >
                            {dt}
                        </button>
                    ))}
                </div>
            </div>

            {/* Template Grid */}
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-5">
                {/* Create New Card — always first */}
                <button
                    onClick={onCreateNew}
                    className="group relative flex flex-col items-center justify-center min-h-[280px] border-2 border-dashed border-slate-300 rounded-2xl hover:border-rose-400 hover:bg-rose-50/50 transition-all duration-200 cursor-pointer"
                >
                    <div className="w-16 h-16 rounded-2xl bg-rose-100 flex items-center justify-center mb-4 group-hover:bg-rose-200 group-hover:scale-110 transition-all duration-200">
                        <Plus className="w-8 h-8 text-rose-600" />
                    </div>
                    <span className="text-base font-semibold text-slate-700 group-hover:text-rose-700 transition-colors">
                        Neue Vorlage
                    </span>
                    <span className="text-xs text-slate-400 mt-1">
                        Leeres Design erstellen
                    </span>
                </button>

                {/* Template Cards */}
                {filteredTemplates.map(template => (
                    <TemplateCard
                        key={template.name}
                        template={template}
                        onEdit={() => onEdit(template.name)}
                        onDuplicate={() => onDuplicate(template.name)}
                        onDelete={() => onDelete(template.name)}
                    />
                ))}
            </div>

            {/* Empty State */}
            {filteredTemplates.length === 0 && templates.length > 0 && (
                <div className="flex flex-col items-center justify-center py-16 text-center">
                    <div className="w-16 h-16 rounded-2xl bg-slate-100 flex items-center justify-center mb-4">
                        <Search className="w-8 h-8 text-slate-400" />
                    </div>
                    <p className="text-lg font-medium text-slate-700">Keine Vorlagen gefunden</p>
                    <p className="text-sm text-slate-500 mt-1">Passe deine Suche oder Filter an</p>
                </div>
            )}

            {templates.length === 0 && (
                <div className="flex flex-col items-center justify-center py-16 text-center">
                    <div className="w-20 h-20 rounded-2xl bg-rose-50 flex items-center justify-center mb-4">
                        <LayoutTemplate className="w-10 h-10 text-rose-400" />
                    </div>
                    <p className="text-lg font-medium text-slate-700">Noch keine Vorlagen erstellt</p>
                    <p className="text-sm text-slate-500 mt-1 max-w-sm">
                        Erstelle deine erste Dokumentvorlage und verwende sie für Angebote, Rechnungen und mehr.
                    </p>
                    <Button size="sm" className="mt-4" onClick={onCreateNew}>
                        <Plus className="w-4 h-4 mr-1" />Erste Vorlage erstellen
                    </Button>
                </div>
            )}
        </div>
    );
}

// ─── Template Card ───────────────────────────────────────────────────

interface TemplateCardProps {
    template: FormTemplateListItem;
    onEdit: () => void;
    onDuplicate: () => void;
    onDelete: () => void;
}

function TemplateCard({ template, onEdit, onDuplicate, onDelete }: TemplateCardProps) {
    const confirmDialog = useConfirm();
    const [showActions, setShowActions] = useState(false);

    const documentTypes = template.assignedDokumenttypen || [];
    const modifiedDate = template.modified
        ? new Date(template.modified).toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric' })
        : null;

    return (
        <Card
            className="group relative overflow-hidden rounded-2xl hover:shadow-lg hover:border-rose-300 transition-all duration-200 cursor-pointer"
            onClick={onEdit}
            onMouseEnter={() => setShowActions(true)}
            onMouseLeave={() => setShowActions(false)}
        >
            {/* Preview Area */}
            <div className="relative h-44 bg-gradient-to-br from-slate-50 to-slate-100 flex items-center justify-center overflow-hidden">
                {/* Mini document preview */}
                <div className="w-20 h-28 bg-white rounded shadow-md border border-slate-200 flex flex-col items-center justify-center p-2 group-hover:scale-105 transition-transform duration-200">
                    <div className="w-10 h-1.5 bg-slate-300 rounded-full mb-1.5" />
                    <div className="w-full h-1 bg-slate-200 rounded-full mb-1" />
                    <div className="w-full h-1 bg-slate-200 rounded-full mb-1" />
                    <div className="w-3/4 h-1 bg-slate-200 rounded-full mb-2" />
                    <div className="w-full h-6 bg-slate-100 rounded border border-slate-200" />
                </div>

                {/* Hover Actions Overlay */}
                <div className={`absolute inset-0 bg-black/40 flex items-center justify-center gap-2 transition-opacity duration-200 ${showActions ? 'opacity-100' : 'opacity-0'}`}>
                    <Button
                        size="sm"
                        className="shadow-lg"
                        onClick={e => { e.stopPropagation(); onEdit(); }}
                    >
                        <Pencil className="w-3.5 h-3.5 mr-1" />Bearbeiten
                    </Button>
                    <Button
                        variant="outline"
                        size="sm"
                        className="bg-white/90 shadow-lg"
                        onClick={e => { e.stopPropagation(); onDuplicate(); }}
                    >
                        <Copy className="w-3.5 h-3.5" />
                    </Button>
                    <Button
                        variant="outline"
                        size="sm"
                        className="bg-white/90 shadow-lg text-red-600 hover:bg-red-50"
                        onClick={async (e) => {
                            e.stopPropagation();
                            if (await confirmDialog({ title: "Vorlage löschen", message: `Vorlage "${template.name}" wirklich löschen?`, variant: "danger", confirmLabel: "Löschen" })) onDelete();
                        }}
                    >
                        <Trash2 className="w-3.5 h-3.5" />
                    </Button>
                </div>
            </div>

            {/* Info Area */}
            <div className="p-4">
                <div className="flex items-start justify-between gap-2">
                    <div className="min-w-0 flex-1">
                        <h3 className="font-semibold text-slate-900 truncate text-sm">
                            {template.name}
                        </h3>
                        {modifiedDate && (
                            <p className="flex items-center gap-1 text-xs text-slate-400 mt-0.5">
                                <Calendar className="w-3 h-3" />{modifiedDate}
                            </p>
                        )}
                    </div>
                    <FileText className="w-4 h-4 text-slate-400 flex-shrink-0 mt-0.5" />
                </div>

                {/* Document type badges */}
                {documentTypes.length > 0 && (
                    <div className="flex flex-wrap gap-1 mt-2.5">
                        {documentTypes.map(dt => (
                            <span
                                key={dt}
                                className="px-2 py-0.5 text-[10px] font-medium bg-rose-50 text-rose-600 rounded-full"
                            >
                                {dt}
                            </span>
                        ))}
                    </div>
                )}
            </div>
        </Card>
    );
}
