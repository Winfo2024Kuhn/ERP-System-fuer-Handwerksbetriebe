import { useState } from 'react';
import { Plus, Search, LayoutTemplate, Copy, Trash2, Pencil, Filter, Calendar } from 'lucide-react';
import { Button } from '../ui/button';
import { Input } from '../ui/input';
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
            {/* Suche in eigener Zeile: die vielen Dokumenttyp-Filter würden sie sonst zusammendrücken */}
            <div className="flex flex-col gap-3">
                <div className="relative w-full max-w-md">
                    <Search aria-hidden="true" className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
                    <Input
                        type="text"
                        value={search}
                        onChange={e => setSearch(e.target.value)}
                        placeholder="Vorlagen durchsuchen..."
                        aria-label="Vorlagen durchsuchen"
                        className="h-10 rounded-lg bg-white pl-10"
                    />
                </div>
                <div className="flex gap-2 flex-wrap">
                    <button
                        type="button"
                        aria-pressed={!filterType}
                        onClick={() => setFilterType(null)}
                        className={`px-3 py-2 text-xs font-medium rounded-lg transition-all ${!filterType
                            ? 'bg-rose-600 text-white shadow-sm'
                            : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
                            }`}
                    >
                        <Filter aria-hidden="true" className="w-3 h-3 inline mr-1" />Alle
                    </button>
                    {DOCUMENT_TYPES.map(dt => (
                        <button
                            key={dt}
                            type="button"
                            aria-pressed={filterType === dt}
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
                    type="button"
                    onClick={onCreateNew}
                    className="group relative flex flex-col items-center justify-center min-h-[280px] border-2 border-dashed border-slate-300 rounded-xl hover:border-rose-400 hover:bg-rose-50/50 transition-colors duration-200 cursor-pointer focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-500"
                >
                    <div className="w-14 h-14 rounded-xl bg-rose-100 flex items-center justify-center mb-4 group-hover:bg-rose-200 transition-colors duration-200">
                        <Plus aria-hidden="true" className="w-7 h-7 text-rose-600" />
                    </div>
                    <span className="text-base font-semibold text-slate-700 group-hover:text-rose-700 transition-colors">
                        Neue Vorlage
                    </span>
                    <span className="text-xs text-slate-400 mt-1">
                        Mit dem Standard-Layout beginnen
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
                    <p className="text-sm text-slate-500 mt-1">Passen Sie Suche oder Filter an</p>
                </div>
            )}

            {templates.length === 0 && (
                <div className="flex flex-col items-center justify-center py-16 text-center">
                    <div className="w-20 h-20 rounded-2xl bg-rose-50 flex items-center justify-center mb-4">
                        <LayoutTemplate className="w-10 h-10 text-rose-400" />
                    </div>
                    <p className="text-lg font-medium text-slate-700">Noch keine Vorlagen erstellt</p>
                    <p className="text-sm text-slate-500 mt-1 max-w-sm">
                        Legen Sie Ihre erste Dokumentvorlage an und verwenden Sie sie für Anfragen, Rechnungen und mehr.
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

/** Schematischer Briefbogen: Logo, Fenster mit Anschrift, Infoblock, Positionen. */
function MiniBrief() {
    return (
        <div aria-hidden="true" className="relative h-32 w-[5.65rem] rounded-sm bg-white p-2 shadow-md ring-1 ring-slate-200 transition-transform duration-200 motion-safe:group-hover:-translate-y-0.5">
            <div className="absolute right-2 top-2 h-2.5 w-7 rounded-sm bg-slate-200" />
            <div className="absolute left-2 top-[1.35rem] h-5 w-9 rounded-[2px] border border-dashed border-slate-300 p-0.5">
                <div className="mt-1.5 h-0.5 w-6 rounded-full bg-slate-300" />
                <div className="mt-0.5 h-0.5 w-5 rounded-full bg-slate-300" />
            </div>
            <div className="absolute right-2 top-[1.6rem] space-y-0.5">
                <div className="h-0.5 w-6 rounded-full bg-slate-200" />
                <div className="h-0.5 w-5 rounded-full bg-slate-200" />
            </div>
            <div className="absolute inset-x-2 top-[3.4rem] space-y-1">
                <div className="h-1 w-2/3 rounded-full bg-slate-300" />
                <div className="h-px w-full bg-slate-200" />
                <div className="h-0.5 w-full rounded-full bg-slate-100" />
                <div className="h-0.5 w-full rounded-full bg-slate-100" />
                <div className="h-0.5 w-full rounded-full bg-slate-100" />
                <div className="ml-auto h-0.5 w-1/3 rounded-full bg-slate-300" />
            </div>
        </div>
    );
}

function TemplateCard({ template, onEdit, onDuplicate, onDelete }: TemplateCardProps) {
    const confirmDialog = useConfirm();

    const documentTypes = template.assignedDokumenttypen || [];
    const modifiedDate = template.modified
        ? new Date(template.modified).toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric' })
        : null;
    const aktion = 'inline-flex h-8 w-8 items-center justify-center rounded-md text-slate-500 transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-500';

    return (
        <Card className="group flex flex-col overflow-hidden rounded-xl transition-shadow hover:border-rose-200 hover:shadow-lg">
            <button
                type="button"
                onClick={onEdit}
                aria-label={`Vorlage „${template.name}“ bearbeiten`}
                className="flex h-44 items-center justify-center bg-slate-100 focus:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-rose-500"
            >
                <MiniBrief />
            </button>

            <div className="flex-1 px-4 pb-3 pt-3">
                <h3 className="truncate text-sm font-semibold text-slate-900" data-kuerzung-erlaubt title={template.name}>{template.name}</h3>
                <p className="mt-0.5 flex items-center gap-1 text-xs text-slate-500">
                    <Calendar aria-hidden="true" className="h-3 w-3" />
                    {modifiedDate ? `Geändert am ${modifiedDate}` : 'Noch nicht geändert'}
                </p>
                {documentTypes.length > 0 ? (
                    <div className="mt-2.5 flex flex-wrap gap-1">
                        {documentTypes.map(dt => (
                            <span key={dt} className="rounded-full bg-rose-50 px-2 py-0.5 text-[11px] font-medium text-rose-700">{dt}</span>
                        ))}
                    </div>
                ) : (
                    <p className="mt-2.5 text-[11px] text-slate-400">Noch keinem Dokumenttyp zugeordnet</p>
                )}
            </div>

            <div className="flex items-center gap-1 border-t border-slate-100 px-2 py-2">
                <Button size="sm" variant="ghost" onClick={onEdit} className="h-8">
                    <Pencil aria-hidden="true" className="mr-1.5 h-3.5 w-3.5" />Bearbeiten
                </Button>
                <button type="button" onClick={onDuplicate} aria-label={`Vorlage „${template.name}“ duplizieren`} title="Duplizieren" className={`${aktion} ml-auto hover:bg-slate-100 hover:text-slate-800`}>
                    <Copy aria-hidden="true" className="h-4 w-4" />
                </button>
                <button
                    type="button"
                    aria-label={`Vorlage „${template.name}“ löschen`}
                    title="Löschen"
                    className={`${aktion} hover:bg-red-50 hover:text-red-600`}
                    onClick={async () => {
                        if (await confirmDialog({ title: 'Vorlage löschen', message: `Vorlage „${template.name}“ wirklich löschen? Das lässt sich nicht rückgängig machen.`, variant: 'danger', confirmLabel: 'Löschen' })) onDelete();
                    }}
                >
                    <Trash2 aria-hidden="true" className="h-4 w-4" />
                </button>
            </div>
        </Card>
    );
}
