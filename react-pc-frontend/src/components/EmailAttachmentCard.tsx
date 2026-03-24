import React, { useState, useMemo } from 'react';
import { File, FileText, ExternalLink, Download } from 'lucide-react';
import { PdfCanvasViewer } from './ui/PdfCanvasViewer';

// Statische Icon-Pfade (Dupliziert aus EmailCenter.tsx)
const BASE_URL = '/react-textbausteine/';
const ICON_TENADO = `${BASE_URL}tenado_logo.jpg`;
const ICON_EXCEL = `${BASE_URL}excel_image.jpg`;
const ICON_HICAD = `${BASE_URL}hicad_logo.png`;

// Interfaces kompatibel mit ProjektEmailAttachment und EmailCenter Attachment
export interface AttachmentProps {
    id?: number;
    filename?: string;
    originalFilename?: string;
    storedFilename?: string;
    url?: string;
    contentId?: string;
    inline?: boolean;
}

interface EmailAttachmentCardProps {
    attachment: AttachmentProps;
    emailId?: number; // Optional context
    email?: Record<string, unknown>; // Optional context object
    baseUrl?: string; // Optional override for API base path
}

const getAttachmentIcon = (filename: string): string | null => {
    const lower = filename?.toLowerCase() || '';
    if (lower.endsWith('.pdf')) return 'pdf';
    if (lower.endsWith('.tcd')) return ICON_TENADO;
    if (lower.endsWith('.xls') || lower.endsWith('.xlsx') || lower.endsWith('.xlsm')) return ICON_EXCEL;
    if (lower.endsWith('.sza')) return ICON_HICAD;
    return null;
};

const isImageAttachment = (filename: string): boolean => {
    const lower = filename?.toLowerCase() || '';
    return /\.(jpg|jpeg|png|gif|webp|bmp)$/.test(lower);
};

export const EmailAttachmentCard: React.FC<EmailAttachmentCardProps> = ({ attachment, emailId, email }) => {
    const [showPreview, setShowPreview] = useState(false);
    const filename = attachment.originalFilename || attachment.filename || 'Anhang';
    const isImage = isImageAttachment(filename);
    const isPdf = filename.toLowerCase().endsWith('.pdf');
    const iconUrl = getAttachmentIcon(filename);

    // URL Construction
    const attachmentUrl = useMemo(() => {
        if (attachment.url) return attachment.url;
        // Generic logic
        if (attachment.id && emailId) {
            return `/api/emails/${emailId}/attachments/${attachment.id}`;
        }
        if (attachment.id && email?.id) {
             return `/api/emails/${email.id}/attachments/${attachment.id}`;
        }
        return '#';
    }, [attachment, emailId, email]);

    const handleOpen = () => {
        if (isImage || isPdf) {
            setShowPreview(true);
        } else {
            handleDownload();
        }
    };

    const handleDownload = () => {
        fetch(attachmentUrl)
            .then(res => res.blob())
            .then(blob => {
                const url = window.URL.createObjectURL(blob);
                const link = document.createElement('a');
                link.href = url;
                link.download = filename;
                document.body.appendChild(link);
                link.click();
                document.body.removeChild(link);
                window.URL.revokeObjectURL(url);
            })
            .catch(err => {
                console.error('Download fehlgeschlagen:', err);
                window.open(attachmentUrl, '_blank');
            });
    };

    return (
        <>
            <div className="flex flex-col cursor-pointer group" onClick={handleOpen}>
                <div className="relative w-16 h-16 bg-white rounded-lg border border-slate-200 overflow-hidden transition-all hover:shadow-md hover:border-rose-300 mx-auto mb-2">
                    <div className="absolute inset-0 flex items-center justify-center bg-slate-50">
                        {isImage ? (
                            <img
                                src={attachmentUrl}
                                alt={filename}
                                className="w-full h-full object-cover"
                                loading="lazy"
                                onError={(e) => { (e.target as HTMLImageElement).style.display = 'none'; }}
                            />
                        ) : iconUrl === 'pdf' ? (
                            <FileText className="w-10 h-10 text-red-500" />
                        ) : iconUrl ? (
                            <img
                                src={iconUrl}
                                alt={filename}
                                className="w-10 h-10 object-contain"
                                loading="lazy"
                            />
                        ) : (
                            <File className="w-8 h-8 text-slate-400" />
                        )}
                    </div>
                    {/* Hover Overlay */}
                    <div className="absolute inset-0 bg-slate-900/60 opacity-0 group-hover:opacity-100 flex items-center justify-center gap-1 transition-opacity">
                        <button
                            onClick={(e) => { e.stopPropagation(); handleOpen(); }}
                            className="h-6 w-6 p-0 bg-white/90 hover:bg-white rounded flex items-center justify-center"
                            title="Öffnen"
                        >
                            <ExternalLink className="w-3 h-3 text-slate-700" />
                        </button>
                        <button
                            onClick={(e) => { e.stopPropagation(); handleDownload(); }}
                            className="h-6 w-6 p-0 bg-white/90 hover:bg-white rounded flex items-center justify-center"
                            title="Herunterladen"
                        >
                            <Download className="w-3 h-3 text-slate-700" />
                        </button>
                    </div>
                </div>
                <p className="text-xs text-slate-600 text-center truncate w-full px-1 font-medium" title={filename}>
                    {filename}
                </p>
            </div>

            {/* Preview Modal */}
            {showPreview && (
                <div
                    className="fixed inset-0 z-[100] flex items-center justify-center bg-black/70 backdrop-blur-sm"
                    onClick={() => setShowPreview(false)}
                >
                    <div
                        className="relative bg-white rounded-2xl shadow-2xl max-w-5xl w-full mx-4 max-h-[90vh] overflow-hidden flex flex-col"
                        onClick={(e) => e.stopPropagation()}
                    >
                        <div className="p-4 border-b border-slate-200 flex items-center justify-between">
                            <h3 className="font-semibold text-slate-900">{filename}</h3>
                            <div className="flex gap-2">
                                <button
                                    onClick={handleDownload}
                                    className="p-2 hover:bg-slate-100 rounded-full transition-colors"
                                    title="Herunterladen"
                                >
                                    <Download className="w-5 h-5 text-slate-600" />
                                </button>
                                <button
                                    onClick={() => setShowPreview(false)}
                                    className="p-2 hover:bg-slate-100 rounded-full transition-colors"
                                >
                                    <ExternalLink className="w-5 h-5 text-slate-600 rotate-180" /> {/* Close icon substitute or just X */}
                                </button>
                            </div>
                        </div>
                        <div className="flex-1 bg-slate-100 overflow-auto p-4 flex items-center justify-center min-h-[400px]">
                            {isImage ? (
                                <img src={attachmentUrl} alt={filename} className="max-w-full max-h-full object-contain shadow-lg" />
                            ) : (
                                <PdfCanvasViewer url={attachmentUrl} className="w-full h-full min-h-[600px] overflow-y-auto overflow-x-hidden" />
                            )}
                        </div>
                    </div>
                </div>
            )}
        </>
    );
};
