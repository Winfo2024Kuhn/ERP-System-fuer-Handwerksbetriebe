import { useCallback, useEffect, useState } from 'react';
import { PdfCanvasViewer } from './ui/PdfCanvasViewer';
import { useDropzone } from 'react-dropzone';
import {
    ChevronDown,
    ChevronLeft,
    ChevronRight,
    File,
    Trash2,
    Upload,
    Download,
    ExternalLink,
    FolderOpen,
    X,
} from 'lucide-react';
import { Button } from './ui/button';
import { ThumbnailImage } from './ui/ThumbnailImage';
import { cn } from '../lib/utils';
import type { ProjektDokument, DokumentGruppe } from '../types';
import { DOKUMENT_GRUPPEN } from '../types';
import { useToast } from './ui/toast';
import { useConfirm } from './ui/confirm-dialog';
import { mergeUploadedDokumente } from '../lib/optimisticUploads';

// Statische Icon-Pfade (relativ zur base URL /react-textbausteine/)
const BASE_URL = import.meta.env.BASE_URL || '/react-textbausteine/';
const ICON_PDF = `${BASE_URL}pdf_icon.jpg`;
const ICON_TENADO = `${BASE_URL}tenado_logo.jpg`;
const ICON_EXCEL = `${BASE_URL}excel_image.jpg`;
const ICON_HICAD = `${BASE_URL}hicad_logo.png`;

interface DocumentManagerProps {
    projektId?: number;
    anfrageId?: number;
    className?: string;
}

interface GroupedDocuments {
    [key: string]: ProjektDokument[];
}

// Prüft Dateityp und gibt entsprechendes Icon zurück
const getFileIconUrl = (doc: ProjektDokument): string | null => {
    const filename = doc.originalDateiname?.toLowerCase() || '';

    if (filename.endsWith('.pdf')) {
        return ICON_PDF;
    }
    if (filename.endsWith('.tcd')) {
        return ICON_TENADO;
    }
    if (filename.endsWith('.xls') || filename.endsWith('.xlsx') || filename.endsWith('.xlsm')) {
        return ICON_EXCEL;
    }
    if (filename.endsWith('.sza')) {
        return ICON_HICAD;
    }
    return null;
};

const isImageFile = (doc: ProjektDokument): boolean => {
    const filename = doc.originalDateiname?.toLowerCase() || '';
    const type = doc.dateityp?.toLowerCase() || '';
    return type.includes('image') || !!filename.match(/\.(jpg|jpeg|png|gif|webp|bmp)$/);
};

const isPdfFile = (doc: ProjektDokument): boolean => {
    const filename = doc.originalDateiname?.toLowerCase() || '';
    return filename.endsWith('.pdf');
};

// Prüft ob die Datei über Netzwerkpfad geöffnet werden soll
const shouldOpenViaNetwork = (doc: ProjektDokument): boolean => {
    const filename = doc.originalDateiname?.toLowerCase() || '';
    return (
        filename.endsWith('.xls') ||
        filename.endsWith('.xlsx') ||
        filename.endsWith('.xlsm') ||
        filename.endsWith('.sza') ||
        filename.endsWith('.tcd')
    );
};

// Öffnet Datei über openfile:// Protokoll (Netzwerkpfad)
const openViaNetworkPath = (doc: ProjektDokument): boolean => {
    if (doc.netzwerkPfad) {
        // Netzwerkpfad muss URL-encoded als Query-Parameter übergeben werden
        // Format: openfile://?path=\\SERVER\Share\path\to\file.ext
        const encodedPath = encodeURIComponent(doc.netzwerkPfad);
        const openfileUrl = `openfile://?path=${encodedPath}`;
        window.location.href = openfileUrl;
        return true;
    }
    return false;
};

export default function DocumentManager({ projektId, anfrageId, className }: DocumentManagerProps) {
    const toast = useToast();
    const confirmDialog = useConfirm();
    const [documents, setDocuments] = useState<ProjektDokument[]>([]);
    const [loading, setLoading] = useState(() => Boolean(projektId || anfrageId));
    const [expandedGroups, setExpandedGroups] = useState<Set<string>>(new Set());
    const [uploadingGroups, setUploadingGroups] = useState<Set<string>>(new Set());
    const [deletingIds, setDeletingIds] = useState<Set<number>>(new Set());
    const [previewDoc, setPreviewDoc] = useState<ProjektDokument | null>(null);

    // Determine API base path based on context
    const isAnfrageContext = !!anfrageId;
    const entityId = projektId || anfrageId;
    const apiBasePath = isAnfrageContext
        ? `/api/anfragen/${entityId}/dokumente`
        : `/api/projekte/${entityId}/dokumente`;

    // Anderes Projekt / andere Anfrage: Ladezustand beim Rendern anpassen.
    const ladeSchluessel = entityId ? apiBasePath : null;
    const [ladeSchluesselVorher, setLadeSchluesselVorher] = useState(ladeSchluessel);
    if (ladeSchluessel !== ladeSchluesselVorher) {
        setLadeSchluesselVorher(ladeSchluessel);
        if (ladeSchluessel) {
            setLoading(true);
        } else {
            setDocuments([]);
            setLoading(false);
        }
    }

    // Dokumente laden (den Ladezustand zu Beginn setzt der Aufrufer)
    const loadDocuments = useCallback((options?: { preserveLoading?: boolean }) => {
        if (!entityId) return Promise.resolve();
        return fetch(apiBasePath)
            .then(res => {
                if (!res.ok) throw new Error('Fehler beim Laden der Dokumente');
                return res.json();
            })
            .then((data: ProjektDokument[]) => {
                setDocuments(data);
            })
            .catch(err => {
                console.error('Fehler beim Laden der Dokumente:', err);
            })
            .finally(() => {
                if (!options?.preserveLoading) {
                    setLoading(false);
                }
            });
    }, [entityId, apiBasePath]);

    useEffect(() => {
        void loadDocuments();
    }, [loadDocuments]);

    // Dokumente nach Gruppe gruppieren
    const groupedDocuments: GroupedDocuments = documents.reduce((acc, doc) => {
        const group = doc.dokumentGruppe || 'DIVERSE_DOKUMENTE';
        if (!acc[group]) acc[group] = [];
        acc[group].push(doc);
        return acc;
    }, {} as GroupedDocuments);

    // Toggle Gruppe
    const toggleGroup = (group: string) => {
        setExpandedGroups(prev => {
            const newSet = new Set(prev);
            if (newSet.has(group)) {
                newSet.delete(group);
            } else {
                newSet.add(group);
            }
            return newSet;
        });
    };

    // Upload Handler
    const handleUpload = async (files: File[], gruppe: DokumentGruppe) => {
        if (files.length === 0) return;

        setUploadingGroups(prev => new Set(prev).add(gruppe));

        try {
            const formData = new FormData();
            files.forEach(file => formData.append('datei', file));

            // Den Hochlader ermittelt der Server aus der Anmeldung.
            const res = await fetch(`${apiBasePath}?gruppe=${gruppe}`, {
                method: 'POST',
                body: formData,
            });

            if (!res.ok) throw new Error('Upload fehlgeschlagen');

            const uploadedDokumente = await res.json() as ProjektDokument[];
            setDocuments(prev => mergeUploadedDokumente(prev, uploadedDokumente));
            void loadDocuments({ preserveLoading: true });
        } catch (err) {
            console.error('Fehler beim Upload:', err);
            toast.error('Fehler beim Hochladen der Dateien');
        } finally {
            setUploadingGroups(prev => {
                const newSet = new Set(prev);
                newSet.delete(gruppe);
                return newSet;
            });
        }
    };

    // Löschen Handler
    const handleDelete = async (doc: ProjektDokument) => {
        if (!await confirmDialog({ title: "Datei löschen", message: `Möchten Sie "${doc.originalDateiname}" wirklich löschen?`, variant: "danger", confirmLabel: "Löschen" })) return;

        setDeletingIds(prev => new Set(prev).add(doc.id));

        try {
            const res = await fetch(`${apiBasePath.replace('/dokumente', '')}/dokumente/${doc.id}`, {
                method: 'DELETE',
            });

            if (!res.ok) throw new Error('Löschen fehlgeschlagen');

            setDocuments(prev => prev.filter(d => d.id !== doc.id));
        } catch (err) {
            console.error('Fehler beim Löschen:', err);
            toast.error('Fehler beim Löschen der Datei');
        } finally {
            setDeletingIds(prev => {
                const newSet = new Set(prev);
                newSet.delete(doc.id);
                return newSet;
            });
        }
    };

    // Öffnen im Preview-Modal oder über Netzwerkpfad
    const handleOpen = (doc: ProjektDokument) => {
        // Excel, SZA, TCD über Netzwerkpfad öffnen
        if (shouldOpenViaNetwork(doc)) {
            if (!openViaNetworkPath(doc)) {
                // Fallback: Download wenn kein Netzwerkpfad
                handleDownload(doc);
            }
            return;
        }
        // Andere Dateien im Preview-Modal
        setPreviewDoc(doc);
    };

    // Öffnen in neuem Tab (externer Link) oder über Netzwerkpfad
    const handleOpenExternal = (doc: ProjektDokument) => {
        // Excel, SZA, TCD über Netzwerkpfad öffnen
        if (shouldOpenViaNetwork(doc)) {
            if (!openViaNetworkPath(doc)) {
                // Fallback: In neuem Tab öffnen
                window.open(doc.url, '_blank');
            }
            return;
        }
        window.open(doc.url, '_blank');
    };

    // Download - funktioniert für alle Dateitypen
    const handleDownload = (doc: ProjektDokument) => {
        // Fetch als Blob für korrekten Download
        // download=true sorgt dafür, dass der Server immer die Binärdatei liefert
        const downloadUrl = doc.url + (doc.url.includes('?') ? '&' : '?') + 'download=true';
        fetch(downloadUrl)
            .then(res => res.blob())
            .then(blob => {
                const url = window.URL.createObjectURL(blob);
                const link = document.createElement('a');
                link.href = url;
                link.download = doc.originalDateiname;
                document.body.appendChild(link);
                link.click();
                document.body.removeChild(link);
                window.URL.revokeObjectURL(url);
            })
            .catch(err => {
                console.error('Download fehlgeschlagen:', err);
                // Fallback: Direkter Link
                const link = document.createElement('a');
                link.href = doc.url;
                link.download = doc.originalDateiname;
                document.body.appendChild(link);
                link.click();
                document.body.removeChild(link);
            });
    };

    if (loading) {
        return (
            <div className={cn("flex items-center justify-center py-12", className)}>
                <div className="text-slate-500">Dokumente werden geladen...</div>
            </div>
        );
    }

    return (
        <div className={cn("space-y-4", className)}>
            {DOKUMENT_GRUPPEN.map(({ value: gruppe, label }) => (
                <DocumentGroup
                    key={gruppe}
                    gruppe={gruppe}
                    label={label}
                    documents={groupedDocuments[gruppe] || []}
                    isExpanded={expandedGroups.has(gruppe)}
                    isUploading={uploadingGroups.has(gruppe)}
                    deletingIds={deletingIds}
                    onToggle={() => toggleGroup(gruppe)}
                    onUpload={(files) => handleUpload(files, gruppe)}
                    onDelete={handleDelete}
                    onOpen={handleOpen}
                    onDownload={handleDownload}
                />
            ))}

            {/* Preview Modal */}
            {previewDoc && (
                <DocumentPreviewModal
                    doc={previewDoc}
                    onClose={() => setPreviewDoc(null)}
                    onOpenExternal={() => handleOpenExternal(previewDoc)}
                    onDownload={() => handleDownload(previewDoc)}
                    allImageDocs={documents.filter(d => isImageFile(d))}
                    onNavigate={(doc) => setPreviewDoc(doc)}
                />
            )}
        </div>
    );
}

interface DocumentGroupProps {
    gruppe: DokumentGruppe;
    label: string;
    documents: ProjektDokument[];
    isExpanded: boolean;
    isUploading: boolean;
    deletingIds: Set<number>;
    onToggle: () => void;
    onUpload: (files: File[]) => void;
    onDelete: (doc: ProjektDokument) => void;
    onOpen: (doc: ProjektDokument) => void;
    onDownload: (doc: ProjektDokument) => void;
}

function DocumentGroup({
    gruppe,
    label,
    documents,
    isExpanded,
    isUploading,
    deletingIds,
    onToggle,
    onUpload,
    onDelete,
    onOpen,
    onDownload,
}: DocumentGroupProps) {
    const { getRootProps, getInputProps, isDragActive } = useDropzone({
        onDrop: onUpload,
        noClick: true,
        noKeyboard: true,
    });

    return (
        <div className="bg-white rounded-xl border border-slate-200 overflow-hidden">
            {/* Header */}
            <button
                onClick={onToggle}
                className="w-full flex items-center justify-between px-4 py-3 bg-slate-50 hover:bg-slate-100 transition-colors"
            >
                <div className="flex items-center gap-3">
                    {isExpanded ? (
                        <ChevronDown className="w-5 h-5 text-slate-500" />
                    ) : (
                        <ChevronRight className="w-5 h-5 text-slate-500" />
                    )}
                    <FolderOpen className="w-5 h-5 text-rose-500" />
                    <span className="font-medium text-slate-900">{label}</span>
                    <span className="text-sm text-slate-500">({documents.length})</span>
                </div>
            </button>

            {/* Content */}
            {isExpanded && (
                <div
                    {...getRootProps()}
                    className={cn(
                        "p-4 transition-colors min-h-[120px]",
                        isDragActive && "bg-rose-50 ring-2 ring-rose-300 ring-inset"
                    )}
                >
                    <input {...getInputProps()} />

                    {/* Upload Bereich */}
                    {isUploading ? (
                        <div className="flex items-center justify-center py-8">
                            <div className="text-rose-600 font-medium animate-pulse">
                                Wird hochgeladen...
                            </div>
                        </div>
                    ) : isDragActive ? (
                        <div className="flex flex-col items-center justify-center py-8 text-rose-600">
                            <Upload className="w-12 h-12 mb-2" />
                            <p className="font-medium">Dateien hier ablegen</p>
                        </div>
                    ) : documents.length === 0 ? (
                        <DropzoneEmpty gruppe={gruppe} onUpload={onUpload} />
                    ) : (
                        <div className="space-y-4">
                            {/* Dokumente Grid */}
                            <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-4">
                                {documents.map(doc => (
                                    <DocumentCard
                                        key={doc.id}
                                        doc={doc}
                                        isDeleting={deletingIds.has(doc.id)}
                                        onDelete={() => onDelete(doc)}
                                        onOpen={() => onOpen(doc)}
                                        onDownload={() => onDownload(doc)}
                                    />
                                ))}
                            </div>

                            {/* Weitere Dateien hinzufügen */}
                            <DropzoneEmpty gruppe={gruppe} onUpload={onUpload} compact />
                        </div>
                    )}
                </div>
            )}
        </div>
    );
}

interface DropzoneEmptyProps {
    gruppe: DokumentGruppe;
    onUpload: (files: File[]) => void;
    compact?: boolean;
}

function DropzoneEmpty({ onUpload, compact }: DropzoneEmptyProps) {
    const { getRootProps, getInputProps, open, isDragActive } = useDropzone({
        onDrop: onUpload,
        noClick: false,
    });

    if (compact) {
        return (
            <div
                {...getRootProps()}
                className={cn(
                    "flex items-center justify-center gap-2 py-3 px-4 border-2 border-dashed rounded-lg cursor-pointer transition-colors",
                    isDragActive
                        ? "border-rose-400 bg-rose-50 text-rose-600"
                        : "border-slate-200 hover:border-rose-300 hover:bg-rose-50 text-slate-500 hover:text-rose-600"
                )}
            >
                <input {...getInputProps()} />
                <Upload className="w-4 h-4" />
                <span className="text-sm">Weitere Dateien hinzufügen</span>
            </div>
        );
    }

    return (
        <div
            {...getRootProps()}
            className={cn(
                "flex flex-col items-center justify-center py-8 border-2 border-dashed rounded-lg cursor-pointer transition-colors",
                isDragActive
                    ? "border-rose-400 bg-rose-50"
                    : "border-slate-200 hover:border-rose-300 hover:bg-rose-50"
            )}
        >
            <input {...getInputProps()} />
            <Upload className={cn(
                "w-10 h-10 mb-3",
                isDragActive ? "text-rose-500" : "text-slate-400"
            )} />
            <p className={cn(
                "font-medium mb-1",
                isDragActive ? "text-rose-600" : "text-slate-600"
            )}>
                Dateien hier ablegen
            </p>
            <p className="text-sm text-slate-500">
                oder{' '}
                <button
                    type="button"
                    onClick={(e) => { e.stopPropagation(); open(); }}
                    className="text-rose-600 hover:underline font-medium"
                >
                    durchsuchen
                </button>
            </p>
        </div>
    );
}

interface DocumentCardProps {
    doc: ProjektDokument;
    isDeleting: boolean;
    onDelete: () => void;
    onOpen: () => void;
    onDownload: () => void;
}

function DocumentCard({ doc, isDeleting, onDelete, onOpen, onDownload }: DocumentCardProps) {
    const [showActions, setShowActions] = useState(false);
    const isImage = isImageFile(doc);
    const iconUrl = getFileIconUrl(doc);

    return (
        <div className="flex flex-col">
            {/* Quadratische Karte */}
            <div
                className={cn(
                    "relative aspect-square bg-white rounded-lg border border-slate-200 overflow-hidden group cursor-pointer transition-all hover:shadow-md hover:border-rose-300",
                    isDeleting && "opacity-50 pointer-events-none"
                )}
                onMouseEnter={() => setShowActions(true)}
                onMouseLeave={() => setShowActions(false)}
                onClick={onOpen}
            >
                {/* Thumbnail / Icon */}
                <div className="absolute inset-0 flex items-center justify-center p-3">
                    {isImage ? (
                        <ThumbnailImage
                            src={doc.thumbnailUrl || doc.url}
                            fallbackSrc={doc.url}
                            alt={doc.originalDateiname}
                            className="object-cover rounded"
                        />
                    ) : iconUrl ? (
                        <img
                            src={iconUrl}
                            alt={doc.originalDateiname}
                            className="w-16 h-16 object-contain"
                            loading="lazy"
                        />
                    ) : (
                        <File className="w-12 h-12 text-slate-400" />
                    )}
                </div>

                {/* Actions Overlay */}
                {showActions && (
                    <div
                        className="absolute inset-0 bg-slate-900/60 flex items-center justify-center gap-2"
                        onClick={(e) => e.stopPropagation()}
                    >
                        <Button
                            size="sm"
                            variant="ghost"
                            className="h-8 w-8 p-0 bg-white/90 hover:bg-white text-slate-700"
                            onClick={onOpen}
                            title="Öffnen"
                        >
                            <ExternalLink className="w-4 h-4" />
                        </Button>
                        <Button
                            size="sm"
                            variant="ghost"
                            className="h-8 w-8 p-0 bg-white/90 hover:bg-white text-slate-700"
                            onClick={onDownload}
                            title="Herunterladen"
                        >
                            <Download className="w-4 h-4" />
                        </Button>
                        <Button
                            size="sm"
                            variant="ghost"
                            className="h-8 w-8 p-0 bg-white/90 hover:bg-red-50 text-red-600"
                            onClick={onDelete}
                            title="Löschen"
                            disabled={isDeleting}
                        >
                            <Trash2 className="w-4 h-4" />
                        </Button>
                    </div>
                )}

                {/* Loading Overlay */}
                {isDeleting && (
                    <div className="absolute inset-0 bg-white/80 flex items-center justify-center">
                        <div className="text-sm text-slate-500">Wird gelöscht...</div>
                    </div>
                )}
            </div>

            {/* Dateiname darunter – in der schmalen Kachel gewollt gekürzt, voller Name per Tooltip und in der Vorschau */}
            <p
                className="mt-2 text-xs text-slate-700 text-center truncate px-1"
                title={doc.originalDateiname}
                data-kuerzung-erlaubt=""
            >
                {doc.originalDateiname}
            </p>
            {/* Upload-Datum und Uploader-Info */}
            <p className="text-[10px] text-slate-400 text-center truncate px-1">
                {doc.uploadDatum && new Date(doc.uploadDatum).toLocaleDateString('de-DE')}
                {(doc.uploadedByVorname || doc.uploadedByNachname) && (
                    <span> • {doc.uploadedByVorname} {doc.uploadedByNachname}</span>
                )}
            </p>
        </div>
    );
}

// Preview Modal Komponente
interface DocumentPreviewModalProps {
    doc: ProjektDokument;
    onClose: () => void;
    onOpenExternal: () => void;
    onDownload: () => void;
    allImageDocs?: ProjektDokument[];
    onNavigate?: (doc: ProjektDokument) => void;
}

function DocumentPreviewModal({ doc, onClose, onOpenExternal, onDownload, allImageDocs, onNavigate }: DocumentPreviewModalProps) {
    const isImage = isImageFile(doc);
    const isPdf = isPdfFile(doc);
    const iconUrl = getFileIconUrl(doc);
    const canOpenViaNetwork = shouldOpenViaNetwork(doc);

    // Image navigation
    const currentIndex = isImage && allImageDocs ? allImageDocs.findIndex(d => d.id === doc.id) : -1;
    const hasPrev = currentIndex > 0;
    const hasNext = allImageDocs ? currentIndex < allImageDocs.length - 1 : false;
    const hasMultipleImages = allImageDocs ? allImageDocs.length > 1 : false;

    const goPrev = () => {
        if (hasPrev && allImageDocs && onNavigate) {
            onNavigate(allImageDocs[currentIndex - 1]);
        }
    };

    const goNext = () => {
        if (hasNext && allImageDocs && onNavigate) {
            onNavigate(allImageDocs[currentIndex + 1]);
        }
    };

    // ESC-Taste und Pfeiltasten
    useEffect(() => {
        const handleKeyDown = (e: KeyboardEvent) => {
            if (e.key === 'Escape') onClose();
            if (e.key === 'ArrowLeft' && hasPrev) goPrev();
            if (e.key === 'ArrowRight' && hasNext) goNext();
        };
        window.addEventListener('keydown', handleKeyDown);
        return () => window.removeEventListener('keydown', handleKeyDown);
    // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [onClose, hasPrev, hasNext]);

    // Öffnen über Netzwerkpfad
    const handleOpenNetwork = () => {
        if (!openViaNetworkPath(doc)) {
            onOpenExternal();
        }
    };

    return (
        <div
            className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-sm"
            onClick={onClose}
        >
            {/* Modal Content */}
            <div
                className="relative bg-white rounded-2xl shadow-2xl w-[calc(100vw-2cm)] h-[calc(100vh-2cm)] overflow-hidden flex flex-col"
                onClick={(e) => e.stopPropagation()}
            >
                {/* Header */}
                <div className="flex items-center justify-between px-6 py-4 border-b border-slate-200 bg-slate-50">
                    <h3 className="font-semibold text-slate-900 truncate pr-4" title={doc.originalDateiname}>
                        {doc.originalDateiname}
                    </h3>
                    <div className="flex items-center gap-2">
                        <Button
                            size="sm"
                            variant="ghost"
                            onClick={onDownload}
                            title="Herunterladen"
                            className="text-slate-600 hover:text-slate-900"
                        >
                            <Download className="w-4 h-4 mr-1" />
                            Herunterladen
                        </Button>
                        {canOpenViaNetwork && doc.netzwerkPfad ? (
                            <Button
                                size="sm"
                                variant="ghost"
                                onClick={handleOpenNetwork}
                                title="Im Programm öffnen"
                                className="text-slate-600 hover:text-slate-900"
                            >
                                <ExternalLink className="w-4 h-4 mr-1" />
                                Öffnen
                            </Button>
                        ) : (
                            <Button
                                size="sm"
                                variant="ghost"
                                onClick={onOpenExternal}
                                title="In neuem Tab öffnen"
                                className="text-slate-600 hover:text-slate-900"
                            >
                                <ExternalLink className="w-4 h-4 mr-1" />
                                Neuer Tab
                            </Button>
                        )}
                        <Button
                            size="sm"
                            variant="ghost"
                            onClick={onClose}
                            className="text-slate-500 hover:text-slate-700"
                        >
                            <X className="w-5 h-5" />
                        </Button>
                    </div>
                </div>

                {/* Content */}
                <div className="flex-1 overflow-auto p-6 flex items-center justify-center bg-slate-100 min-h-[400px] relative">
                    {/* Image navigation arrows */}
                    {isImage && hasMultipleImages && hasPrev && (
                        <button
                            onClick={(e) => { e.stopPropagation(); goPrev(); }}
                            className="absolute left-4 top-1/2 -translate-y-1/2 z-10 w-10 h-10 flex items-center justify-center bg-black/40 hover:bg-black/60 text-white rounded-full transition-all backdrop-blur-sm"
                            title="Vorheriges Bild (←)"
                        >
                            <ChevronLeft className="w-6 h-6" />
                        </button>
                    )}
                    {isImage && hasMultipleImages && hasNext && (
                        <button
                            onClick={(e) => { e.stopPropagation(); goNext(); }}
                            className="absolute right-4 top-1/2 -translate-y-1/2 z-10 w-10 h-10 flex items-center justify-center bg-black/40 hover:bg-black/60 text-white rounded-full transition-all backdrop-blur-sm"
                            title="Nächstes Bild (→)"
                        >
                            <ChevronRight className="w-6 h-6" />
                        </button>
                    )}
                    {/* Image counter */}
                    {isImage && hasMultipleImages && allImageDocs && (
                        <div className="absolute bottom-8 left-1/2 -translate-x-1/2 z-10 text-sm font-medium bg-black/50 text-white px-3 py-1 rounded-full backdrop-blur-sm">
                            {currentIndex + 1} / {allImageDocs.length}
                        </div>
                    )}
                    {isImage ? (
                        <img
                            src={doc.url}
                            alt={doc.originalDateiname}
                            className="max-w-full max-h-[70vh] object-contain rounded-lg shadow-lg"
                        />
                    ) : isPdf ? (
                        <PdfCanvasViewer
                            url={doc.url}
                            className="w-full h-full rounded-lg overflow-y-auto"
                        />
                    ) : (
                        <div className="flex flex-col items-center justify-center py-12">
                            {iconUrl ? (
                                <img
                                    src={iconUrl}
                                    alt={doc.originalDateiname}
                                    className="w-32 h-32 object-contain mb-6"
                                />
                            ) : (
                                <File className="w-24 h-24 text-slate-300 mb-6" />
                            )}
                            <p className="text-lg font-medium text-slate-700 mb-2">
                                {doc.originalDateiname}
                            </p>
                            <p className="text-sm text-slate-500 mb-6">
                                Diese Datei kann nicht direkt angezeigt werden.
                            </p>
                            <div className="flex gap-3">
                                <Button
                                    onClick={onDownload}
                                    className="bg-rose-600 text-white hover:bg-rose-700"
                                >
                                    <Download className="w-4 h-4 mr-2" />
                                    Herunterladen
                                </Button>
                                {canOpenViaNetwork && doc.netzwerkPfad ? (
                                    <Button
                                        variant="outline"
                                        onClick={handleOpenNetwork}
                                    >
                                        <ExternalLink className="w-4 h-4 mr-2" />
                                        Im Programm öffnen
                                    </Button>
                                ) : (
                                    <Button
                                        variant="outline"
                                        onClick={onOpenExternal}
                                    >
                                        <ExternalLink className="w-4 h-4 mr-2" />
                                        In neuem Tab öffnen
                                    </Button>
                                )}
                            </div>
                        </div>
                    )}
                </div>
            </div>
        </div>
    );
}

export { DocumentManager };
