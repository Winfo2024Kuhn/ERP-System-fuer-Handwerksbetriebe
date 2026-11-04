import { File } from 'lucide-react';
import { decodeMimeWord, formatSize } from './threadFormat';
import type { ThreadAttachment } from './threadTypes';

interface EmailAttachmentChipProps {
    attachment: ThreadAttachment;
    emailId: number;
    onPreview?: (url: string, type: 'image' | 'pdf', name: string) => void;
}

/** Anhang einer Nachricht: Bild und PDF öffnen die Vorschau, alles andere wird heruntergeladen. */
export function EmailAttachmentChip({ attachment, emailId, onPreview }: EmailAttachmentChipProps) {
    const filename = decodeMimeWord(attachment.originalFilename || 'Datei');
    const downloadUrl = `/api/emails/${emailId}/attachments/${attachment.id}`;
    const isImage = /\.(jpg|jpeg|png|gif|webp|bmp)$/i.test(filename);
    const isPdf = /\.pdf$/i.test(filename);

    const handleClick = (e: React.MouseEvent) => {
        e.stopPropagation();
        if (onPreview && (isImage || isPdf)) {
            onPreview(downloadUrl, isImage ? 'image' : 'pdf', filename);
        } else {
            const a = document.createElement('a');
            a.href = downloadUrl;
            a.download = filename;
            a.click();
        }
    };

    return (
        <button
            type="button"
            onClick={handleClick}
            className="inline-flex items-center gap-1.5 max-w-[240px] px-3 py-2 rounded-lg
                       bg-slate-50 border border-slate-200 hover:border-rose-300 hover:bg-rose-50
                       transition-colors duration-150 text-xs text-slate-700 cursor-pointer group
                       focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-500"
        >
            <File aria-hidden="true" className="w-3.5 h-3.5 text-rose-500 shrink-0 group-hover:text-rose-600" />
            {/* Lange Namen werden gekürzt; der volle Name steht im Tooltip. */}
            <span className="truncate font-medium" title={filename} data-kuerzung-erlaubt>{filename}</span>
            {attachment.sizeBytes && (
                <span className="text-slate-500 shrink-0 ml-auto tabular-nums">{formatSize(attachment.sizeBytes)}</span>
            )}
        </button>
    );
}
