import { FileEdit, Trash2 } from 'lucide-react';
import { EmailRecipientDropdown } from '../../../components/EmailRecipientDropdown';
import { formatTime } from './threadFormat';
import type { EmailThreadEntry } from './threadTypes';

// DraftThreadBubble – Entwurf inline im Verlauf (Gmail-Stil)

interface DraftBubbleProps {
    entry: EmailThreadEntry;
    onOpenDraft?: (entry: EmailThreadEntry) => void;
    onDeleteDraft?: (draftId: number) => void;
}

export function DraftThreadBubble({ entry, onOpenDraft, onDeleteDraft }: DraftBubbleProps) {
    const hasContent = entry.snippet && entry.snippet !== '[Entwurf]';

    return (
        <div className="flex items-end gap-2 flex-row-reverse mb-2">
            {/* Avatar */}
            <div className="w-7 h-7 rounded-full bg-amber-500 text-white flex items-center justify-center
                            text-xs font-semibold shadow-sm shrink-0">
                <FileEdit aria-hidden="true" className="w-3.5 h-3.5" />
            </div>

            {/* Draft Card */}
            <div className="flex flex-col items-end max-w-[72%] gap-1">
                <div className="flex items-center gap-1 text-[10px] text-amber-700 tabular-nums">
                    <span>{formatTime(entry.sentAt)}</span>
                    {onDeleteDraft && entry.draftId && (
                        <button
                            type="button"
                            onClick={() => onDeleteDraft(entry.draftId!)}
                            className="inline-flex items-center justify-center w-7 h-7 rounded-lg text-amber-700 hover:bg-amber-100 hover:text-rose-700 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-500"
                            title="Entwurf löschen"
                            aria-label="Entwurf löschen"
                        >
                            <Trash2 aria-hidden="true" className="w-4 h-4" />
                        </button>
                    )}
                </div>
                <button
                    type="button"
                    onClick={() => onOpenDraft?.(entry)}
                    className="w-full text-left rounded-2xl rounded-br-none px-4 py-3 cursor-pointer
                               border-2 border-dashed border-amber-300 bg-amber-50/80
                               hover:bg-amber-100/80 hover:border-amber-400
                               transition-colors duration-200 shadow-sm
                               focus:outline-none focus-visible:ring-2 focus-visible:ring-amber-400"
                >
                    {/* Draft Badge */}
                    <div className="flex items-center justify-between gap-2 mb-1.5">
                        <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full
                                         bg-amber-200/80 text-amber-800 text-[10px] font-bold uppercase tracking-wide">
                            <FileEdit aria-hidden="true" className="w-2.5 h-2.5" />
                            Entwurf
                        </span>
                        <span className="text-[10px] font-medium text-amber-700/80">Nicht gesendet</span>
                    </div>

                    {/* Recipient */}
                    {entry.recipient && (
                        <div className="mb-1">
                            <EmailRecipientDropdown recipients={entry.recipient} label="An:" className="text-amber-700/80 text-xs" />
                        </div>
                    )}

                    {/* Subject */}
                    {entry.subject && (
                        <p className="text-xs font-medium text-slate-700 mb-0.5 truncate">
                            {entry.subject}
                        </p>
                    )}

                    {/* Preview */}
                    <p className="text-sm text-slate-600 line-clamp-2 leading-relaxed">
                        {hasContent
                            ? entry.snippet
                            : <span className="italic text-amber-700">Klicken zum Bearbeiten…</span>
                        }
                    </p>
                </button>
            </div>
        </div>
    );
}
