import React from 'react';
import type { ReplyMode } from '../features/email/emailCenterModel';
import { DraftThreadBubble } from '../features/email/thread/DraftThreadBubble';
import { EmailThreadBubble } from '../features/email/thread/EmailThreadBubble';
import { formatDateLabel, getDayString, isWiederholtesSignaturLogo } from '../features/email/thread/threadFormat';
import type { EmailThread, EmailThreadEntry } from '../features/email/thread/threadTypes';
import { cn } from '../lib/utils';

export type { EmailThread, EmailThreadEntry, ThreadAttachment } from '../features/email/thread/threadTypes';

// ─────────────────────────────────────────────────────────────────
// SkeletonBubble
// ─────────────────────────────────────────────────────────────────

function SkeletonBubble({ alignRight }: { alignRight: boolean }) {
    return (
        <div className={cn('flex items-start gap-3 mb-2', alignRight && 'flex-row-reverse')}>
            <div className="w-8 h-8 rounded-full bg-slate-200 motion-safe:animate-pulse shrink-0 mt-1" />
            <div className="flex flex-col gap-1.5" style={{ maxWidth: '70%' }}>
                <div className="h-3 w-24 bg-slate-200 motion-safe:animate-pulse rounded" />
                <div className="h-20 bg-slate-200 motion-safe:animate-pulse rounded-xl" />
            </div>
        </div>
    );
}

// ─────────────────────────────────────────────────────────────────
// DaySeparator
// ─────────────────────────────────────────────────────────────────

function DaySeparator({ label }: { label: string }) {
    return (
        <div className="flex items-center gap-2 my-3">
            <div className="flex-1 h-px bg-slate-200" />
            <span className="px-3 py-1 rounded-full bg-white border border-slate-200
                             text-xs font-medium text-slate-500 shadow-sm text-center">
                {label}
            </span>
            <div className="flex-1 h-px bg-slate-200" />
        </div>
    );
}

// ─────────────────────────────────────────────────────────────────
// EmailThreadView – Haupt-Komponente
// ─────────────────────────────────────────────────────────────────

interface EmailThreadViewProps {
    thread: EmailThread;
    loading?: boolean;
    onPreview?: (url: string, type: 'image' | 'pdf', name: string) => void;
    onReply?: (entry: EmailThreadEntry, mode: ReplyMode) => void;
    canReplyAll?: (entry: EmailThreadEntry) => boolean;
    onOpenDraft?: (entry: EmailThreadEntry) => void;
    onDeleteDraft?: (draftId: number) => void;
}

/**
 * E-Mail-Verlauf im E-Mail-Center und im E-Mail-Reiter: setzt Tagestrenner, Nachrichten
 * (`EmailThreadBubble`) und Entwürfe (`DraftThreadBubble`) zusammen und blendet
 * wiederholte Signatur-Logos aus. Die Blasen liegen unter `features/email/thread/`.
 */
export function EmailThreadView({ thread, loading, onPreview, onReply, canReplyAll, onOpenDraft, onDeleteDraft }: EmailThreadViewProps) {
    // Thread-übergreifende Anhang-Dedup über originalFilename:sizeBytes.
    // Greift NUR bei kleinen Bildern (Signatur-Logos, die in jeder Nachricht
    // erneut mitkommen). Dokumente und große Bilder bleiben in jeder Nachricht
    // sichtbar – sonst verschwindet eine bewusst erneut gesendete Datei aus dem
    // Verlauf und der Nutzer hält den Versand für fehlgeschlagen.
    const seenAttachments = new Set<string>();
    const emails = thread.emails.map(entry => ({
        ...entry,
        attachments: entry.attachments.filter(a => {
            // Inline-Bilder stecken im Text und werden ohnehin nicht als Chip
            // gezeigt – sie dürfen einem späteren echten Anhang nicht den Platz
            // wegnehmen.
            if (a.inline) return true;
            if (!isWiederholtesSignaturLogo(a)) return true;
            const key = `${a.originalFilename}:${a.sizeBytes ?? ''}`;
            if (seenAttachments.has(key)) return false;
            seenAttachments.add(key);
            return true;
        }),
    }));

    // Entwürfe separat herausfiltern – sie werden am Ende des Threads angezeigt
    const regularEmails = emails.filter(e => !e.isDraft);
    const draftEntries = emails.filter(e => e.isDraft);

    if (loading) {
        return (
            <div className="flex-1 min-h-0 overflow-auto bg-slate-50 px-3 py-3 space-y-3">
                <SkeletonBubble alignRight={false} />
                <SkeletonBubble alignRight={true} />
                <SkeletonBubble alignRight={false} />
            </div>
        );
    }

    return (
        <div data-testid="email-verlauf" className="flex-1 min-h-0 overflow-auto bg-slate-50 px-3 py-3">
            {regularEmails.map((entry, idx) => {
                const prev = idx > 0 ? regularEmails[idx - 1] : null;
                const showDaySeparator = !prev || getDayString(entry.sentAt) !== getDayString(prev.sentAt);
                const showAvatar = !prev || prev.fromAddress !== entry.fromAddress || prev.direction !== entry.direction;
                const isLast = idx === regularEmails.length - 1 && draftEntries.length === 0;

                return (
                    <React.Fragment key={entry.id}>
                        {showDaySeparator && <DaySeparator label={formatDateLabel(entry.sentAt)} />}
                        <EmailThreadBubble
                            entry={entry}
                            isFocused={entry.id === thread.focusedEmailId}
                            showAvatar={showAvatar}
                            showSenderName={showAvatar}
                            isLast={isLast}
                            onPreview={onPreview}
                            onReply={onReply}
                            canReplyAll={canReplyAll}
                        />
                    </React.Fragment>
                );
            })}

            {/* Entwürfe am Ende des Threads, visuell abgesetzt */}
            {draftEntries.length > 0 && (
                <>
                    <div className="flex items-center gap-3 my-4">
                        <div className="flex-1 border-t border-dashed border-amber-300" />
                        <span className="text-[10px] font-semibold text-amber-700 uppercase tracking-widest">
                            Entwürfe
                        </span>
                        <div className="flex-1 border-t border-dashed border-amber-300" />
                    </div>
                    {draftEntries.map(draft => (
                        <DraftThreadBubble
                            key={`draft-${draft.draftId}`}
                            entry={draft}
                            onOpenDraft={onOpenDraft}
                            onDeleteDraft={onDeleteDraft}
                        />
                    ))}
                </>
            )}
        </div>
    );
}
