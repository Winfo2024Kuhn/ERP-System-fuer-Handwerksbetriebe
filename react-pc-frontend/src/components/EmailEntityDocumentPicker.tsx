import { ChevronDown, ChevronRight, File, FolderOpen, Loader2 } from 'lucide-react';
import { useMemo, useState } from 'react';
import type { DokumentGruppe, ProjektDokument } from '../types';
import { DOKUMENT_GRUPPEN } from '../types';

interface EmailEntityDocumentPickerProps {
    documents: ProjektDokument[];
    loading: boolean;
    selectedIds: Set<number>;
    loadingIds: Set<number>;
    onToggle: (document: ProjektDokument) => void;
}

export function EmailEntityDocumentPicker({
    documents,
    loading,
    selectedIds,
    loadingIds,
    onToggle,
}: EmailEntityDocumentPickerProps) {
    const [expandedGroups, setExpandedGroups] = useState<Set<DokumentGruppe>>(new Set());
    const groupedDocuments = useMemo(() => {
        const groups = new Map<DokumentGruppe, ProjektDokument[]>();
        DOKUMENT_GRUPPEN.forEach(({ value }) => groups.set(value, []));
        documents.forEach(document => {
            const group = document.dokumentGruppe || 'DIVERSE_DOKUMENTE';
            groups.get(group)?.push(document);
        });
        return groups;
    }, [documents]);

    const toggleGroup = (group: DokumentGruppe) => {
        setExpandedGroups(previous => {
            const next = new Set(previous);
            if (next.has(group)) next.delete(group);
            else next.add(group);
            return next;
        });
    };

    const isImage = (document: ProjektDokument) =>
        document.dateityp?.startsWith('image/') ||
        /\.(jpe?g|png|gif|webp|bmp)$/i.test(document.originalDateiname);

    if (loading) {
        return (
            <div className="flex items-center justify-center gap-2 rounded-xl border border-slate-200 bg-slate-50 px-4 py-8 text-sm text-slate-500">
                <Loader2 className="h-4 w-4 animate-spin" />
                Dateien werden geladen…
            </div>
        );
    }

    return (
        <div className="space-y-2">
            {DOKUMENT_GRUPPEN.map(({ value, label }) => {
                const groupDocuments = groupedDocuments.get(value) || [];
                const expanded = expandedGroups.has(value);
                return (
                    <div key={value} className="overflow-hidden rounded-xl border border-slate-200 bg-white">
                        <button
                            type="button"
                            onClick={() => toggleGroup(value)}
                            className="flex min-h-12 w-full items-center gap-3 bg-slate-50 px-4 py-3 text-left hover:bg-slate-100"
                        >
                            {expanded
                                ? <ChevronDown className="h-4 w-4 text-slate-500" />
                                : <ChevronRight className="h-4 w-4 text-slate-500" />}
                            <FolderOpen className="h-5 w-5 text-rose-500" />
                            <span className="flex-1 text-sm font-medium text-slate-800">{label}</span>
                            <span className="text-xs text-slate-500">({groupDocuments.length})</span>
                        </button>

                        {expanded && (
                            <div className="border-t border-slate-100 p-3">
                                {groupDocuments.length === 0 ? (
                                    <p className="px-2 py-3 text-xs text-slate-500">Keine Dateien vorhanden.</p>
                                ) : (
                                    <div className="grid grid-cols-2 gap-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5">
                                        {groupDocuments.map(document => {
                                            const selected = selectedIds.has(document.id);
                                            const itemLoading = loadingIds.has(document.id);
                                            return (
                                                <label
                                                    key={document.id}
                                                    className={`relative cursor-pointer overflow-hidden rounded-lg border bg-white transition-all hover:border-rose-300 hover:shadow-sm ${
                                                        selected ? 'border-rose-500 ring-2 ring-rose-100' : 'border-slate-200'
                                                    }`}
                                                >
                                                    <input
                                                        type="checkbox"
                                                        checked={selected}
                                                        disabled={itemLoading}
                                                        onChange={() => onToggle(document)}
                                                        className="absolute left-2 top-2 z-10 h-4 w-4 rounded border-slate-300 text-rose-600 focus:ring-rose-500"
                                                    />
                                                    <div className="flex aspect-[4/3] items-center justify-center bg-slate-100">
                                                        {itemLoading ? (
                                                            <Loader2 className="h-6 w-6 animate-spin text-rose-600" />
                                                        ) : isImage(document) ? (
                                                            <img
                                                                src={document.url}
                                                                alt=""
                                                                className="h-full w-full object-cover"
                                                                loading="lazy"
                                                            />
                                                        ) : (
                                                            <File className="h-10 w-10 text-slate-400" />
                                                        )}
                                                    </div>
                                                    <span
                                                        className="block truncate px-2 py-2 text-xs font-medium text-slate-700"
                                                        title={document.originalDateiname}
                                                    >
                                                        {document.originalDateiname}
                                                    </span>
                                                </label>
                                            );
                                        })}
                                    </div>
                                )}
                            </div>
                        )}
                    </div>
                );
            })}
        </div>
    );
}
