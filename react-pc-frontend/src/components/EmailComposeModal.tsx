import { useEffect } from 'react';
import { EmailComposeForm } from './EmailComposeForm';
import type { ProjektDetail, ProjektDokument } from '../types';
import {
    X,
    File,
    Download,
    ExternalLink,
} from 'lucide-react';
import { Button } from './ui/button';
import { PdfCanvasViewer } from './ui/PdfCanvasViewer';

// Statische Icon-Pfade
const BASE_URL = '/react-textbausteine/';
const ICON_PDF = `${BASE_URL}pdf_icon.jpg`;
const ICON_EXCEL = `${BASE_URL}excel_image.jpg`;
const ICON_TENADO = `${BASE_URL}tenado_logo.jpg`;
const ICON_HICAD = `${BASE_URL}hicad_logo.png`;

// Dateinamens-Hilfsfunktionen
const getFileExtension = (filename: string): string => {
    return filename.split('.').pop()?.toLowerCase() || '';
};

const isImageFile = (filename: string): boolean => {
    const ext = getFileExtension(filename);
    return ['jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp'].includes(ext);
};

const isPdfFile = (filename: string): boolean => {
    return getFileExtension(filename) === 'pdf';
};

const getFileIconUrl = (filename: string): string | null => {
    const ext = getFileExtension(filename);
    if (ext === 'pdf') return ICON_PDF;
    if (['xlsx', 'xls', 'xlsm', 'xlsb'].includes(ext)) return ICON_EXCEL;
    if (ext === 'tcd') return ICON_TENADO;
    if (ext === 'sza') return ICON_HICAD;
    return null;
};

// Interface für hochgeladene externe Dateien
interface UploadedFile {
    file: File;
    previewUrl?: string;
}

interface EmailComposeModalProps {
    isOpen: boolean;
    onClose: () => void;
    // For projects
    projektId?: number;
    projekt?: ProjektDetail;
    // For offers (Anfragen)
    anfrageId?: number;
    anfrage?: {
        bauvorhaben: string;
        kundenName?: string;
        kundenEmails?: string[];
        kundenAnrede?: string;
        kundenAnsprechpartner?: string;
    };
    /** Customer ID for saving new email addresses */
    kundeId?: number;
    /** Pre-attached files (e.g. generated PDF from DocumentEditor) */
    initialAttachments?: File[];
    initialRecipient?: string;
    initialSubject?: string;
    initialBody?: string;
    /** Optional callback after successful email send */
    onSuccess?: () => void;
}

export default function EmailComposeModal({
    isOpen,
    onClose,
    projektId,
    projekt,
    anfrageId,
    anfrage,
    kundeId,
    initialAttachments,
    initialRecipient,
    initialSubject,
    initialBody,
    onSuccess,
}: EmailComposeModalProps) {
    if (!isOpen) return null;

    return (
        <div
            className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm"
        >
            <div
                className="bg-white rounded-2xl shadow-2xl w-full max-w-6xl mx-4 h-[95vh] overflow-hidden flex flex-col"
                onClick={(e) => e.stopPropagation()}
            >
                <EmailComposeForm
                    onClose={onClose}
                    projektId={projektId}
                    projekt={projekt}
                    anfrageId={anfrageId}
                    anfrage={anfrage}
                    kundeId={kundeId}
                    initialAttachments={initialAttachments}
                    initialRecipient={initialRecipient}
                    initialSubject={initialSubject}
                    initialBody={initialBody}
                    onSuccess={onSuccess || (() => window.location.reload())}
                    variant="modal"
                />
            </div>
        </div>
    );
}

// Attachment Preview Modal Komponente
interface AttachmentPreviewModalProps {
    previewItem: {
        type: 'dokument' | 'file';
        dokument?: ProjektDokument;
        uploadedFile?: UploadedFile;
    };
    onClose: () => void;
}

// Exporting it to avoid unused variable error and allow usage elsewhere
export function AttachmentPreviewModal({ previewItem, onClose }: AttachmentPreviewModalProps) {
    const isDokument = previewItem.type === 'dokument' && previewItem.dokument;
    const isUploadedFile = previewItem.type === 'file' && previewItem.uploadedFile;

    // Dateiname und URL bestimmen
    const filename = isDokument
        ? previewItem.dokument!.originalDateiname
        : previewItem.uploadedFile!.file.name;

    const fileUrl = isDokument
        ? previewItem.dokument!.url
        : previewItem.uploadedFile!.previewUrl || URL.createObjectURL(previewItem.uploadedFile!.file);

    const isImage = isImageFile(filename);
    const isPdf = isPdfFile(filename);
    const iconUrl = getFileIconUrl(filename);

    // ESC-Taste zum Schließen
    useEffect(() => {
        const handleEsc = (e: KeyboardEvent) => {
            if (e.key === 'Escape') onClose();
        };
        window.addEventListener('keydown', handleEsc);
        return () => window.removeEventListener('keydown', handleEsc);
    }, [onClose]);

    // Download-Handler
    const handleDownload = async () => {
        try {
            if (isUploadedFile) {
                // Lokale Datei herunterladen
                const url = URL.createObjectURL(previewItem.uploadedFile!.file);
                const a = document.createElement('a');
                a.href = url;
                a.download = filename;
                document.body.appendChild(a);
                a.click();
                document.body.removeChild(a);
                URL.revokeObjectURL(url);
            } else if (isDokument) {
                // Dokument vom Server herunterladen
                const response = await fetch(previewItem.dokument!.url);
                const blob = await response.blob();
                const url = URL.createObjectURL(blob);
                const a = document.createElement('a');
                a.href = url;
                a.download = filename;
                document.body.appendChild(a);
                a.click();
                document.body.removeChild(a);
                URL.revokeObjectURL(url);
            }
        } catch (err) {
            console.error('Download fehlgeschlagen:', err);
        }
    };

    // Extern öffnen (neuer Tab)
    const handleOpenExternal = () => {
        if (isDokument) {
            window.open(previewItem.dokument!.url, '_blank');
        } else if (isUploadedFile) {
            const url = URL.createObjectURL(previewItem.uploadedFile!.file);
            window.open(url, '_blank');
        }
    };

    return (
        <div
            className="fixed inset-0 z-[60] flex items-center justify-center bg-black/70 backdrop-blur-sm"
            onClick={onClose}
        >
            <div
                className="relative bg-white rounded-2xl shadow-2xl max-w-4xl w-full mx-4 max-h-[90vh] overflow-hidden flex flex-col"
                onClick={(e) => e.stopPropagation()}
            >
                {/* Header */}
                <div className="flex items-center justify-between px-6 py-4 border-b border-slate-200 bg-slate-50">
                    <h3 className="font-semibold text-slate-900 truncate pr-4" title={filename}>
                        {filename}
                    </h3>
                    <div className="flex items-center gap-2">
                        <Button
                            size="sm"
                            variant="ghost"
                            onClick={handleDownload}
                            title="Herunterladen"
                            className="text-slate-600 hover:text-slate-900"
                        >
                            <Download className="w-4 h-4 mr-1" />
                            Herunterladen
                        </Button>
                        <Button
                            size="sm"
                            variant="ghost"
                            onClick={handleOpenExternal}
                            title="In neuem Tab öffnen"
                            className="text-slate-600 hover:text-slate-900"
                        >
                            <ExternalLink className="w-4 h-4 mr-1" />
                            Öffnen
                        </Button>
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
                <div className="flex-1 overflow-auto p-6 flex items-center justify-center bg-slate-100 min-h-[400px]">
                    {isImage ? (
                        <img
                            src={fileUrl}
                            alt={filename}
                            className="max-w-full max-h-[70vh] object-contain rounded-lg shadow-lg"
                        />
                    ) : isPdf ? (
                        <PdfCanvasViewer
                            url={fileUrl}
                            className="w-full h-[70vh] rounded-lg overflow-y-auto overflow-x-hidden"
                        />
                    ) : (
                        <div className="flex flex-col items-center justify-center py-12">
                            {iconUrl ? (
                                <img src={iconUrl} alt={filename} className="w-24 h-24 object-contain mb-4" />
                            ) : (
                                <File className="w-20 h-20 text-slate-300 mb-4" />
                            )}
                            <p className="text-lg font-medium text-slate-700 mb-2">{filename}</p>
                            <p className="text-sm text-slate-500 mb-4">
                                Vorschau nicht verfügbar
                            </p>
                            <div className="flex gap-3">
                                <Button
                                    variant="outline"
                                    onClick={handleDownload}
                                    className="border-rose-300 text-rose-700 hover:bg-rose-50"
                                >
                                    <Download className="w-4 h-4 mr-2" />
                                    Herunterladen
                                </Button>
                                <Button
                                    variant="outline"
                                    onClick={handleOpenExternal}
                                    className="border-slate-300 text-slate-700 hover:bg-slate-50"
                                >
                                    <ExternalLink className="w-4 h-4 mr-2" />
                                    Extern öffnen
                                </Button>
                            </div>
                        </div>
                    )}
                </div>
            </div>
        </div>
    );
}

export { EmailComposeModal };
