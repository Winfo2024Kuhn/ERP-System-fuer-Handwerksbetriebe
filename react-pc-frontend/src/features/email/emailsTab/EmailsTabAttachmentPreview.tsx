import { X } from 'lucide-react';
import { toSafeResourceUrl } from '../../../lib/htmlSanitizer';

export interface EmailsTabAttachment {
    url: string;
    type: 'image' | 'pdf';
    name: string;
}

interface EmailsTabAttachmentPreviewProps {
    attachment: EmailsTabAttachment | null;
    onClose: () => void;
}

/** Vorschau eines Bild- oder PDF-Anhangs aus dem Verlauf; Klick daneben schließt. */
export function EmailsTabAttachmentPreview({ attachment, onClose }: EmailsTabAttachmentPreviewProps) {
    if (!attachment) return null;
    const safeUrl = toSafeResourceUrl(attachment.url);
    return (
        <div className="fixed inset-0 z-[60] flex items-center justify-center bg-black/60" onClick={onClose}>
            <div className="bg-white rounded-xl shadow-2xl max-w-4xl max-h-[90vh] overflow-auto p-2" onClick={e => e.stopPropagation()}>
                <div className="flex justify-between items-center px-3 py-2 border-b border-slate-100">
                    <p className="text-sm font-medium text-slate-700">{attachment.name}</p>
                    <button onClick={onClose} className="p-1 hover:bg-slate-100 rounded">
                        <X className="w-4 h-4" />
                    </button>
                </div>
                {attachment.type === 'image' ? (
                    <img src={safeUrl || undefined} alt={attachment.name} className="max-w-full max-h-[80vh]" />
                ) : (
                    <iframe src={safeUrl || undefined} className="w-[800px] h-[80vh]" title={attachment.name} />
                )}
            </div>
        </div>
    );
}
