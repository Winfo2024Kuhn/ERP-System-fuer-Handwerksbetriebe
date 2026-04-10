import React, { useEffect, useRef, useState } from 'react';
import { Paperclip, File, ChevronDown, ChevronUp, Reply } from 'lucide-react';
import { cn } from '../lib/utils';
import { EmailContentFrame } from './EmailContentFrame';

// ─────────────────────────────────────────────────────────────────
// TYPEN (1:1 mit Backend-DTO)
// ─────────────────────────────────────────────────────────────────

export interface ThreadAttachment {
    id: number;
    originalFilename: string;
    mimeType?: string;
    sizeBytes?: number;
    contentId?: string;
    inline?: boolean;
}

export interface EmailThreadEntry {
    id: number;
    subject?: string;
    fromAddress?: string;
    recipient?: string;
    sentAt?: string;
    direction: 'IN' | 'OUT';
    forwarded?: boolean;
    snippet?: string;
    htmlBody?: string;
    attachments: ThreadAttachment[];
}

export interface EmailThread {
    rootEmailId: number;
    focusedEmailId: number;
    emails: EmailThreadEntry[];
}

// ─────────────────────────────────────────────────────────────────
// HILFSFUNKTIONEN
// ─────────────────────────────────────────────────────────────────

function extractDisplayName(address?: string): string {
    if (!address) return 'Unbekannt';
    const match = address.match(/^"?(.*?)"?\s*<.*>$/);
    if (match && match[1]) return match[1].trim();
    return address;
}

function extractEmail(address?: string): string {
    if (!address) return '';
    const match = address.match(/<(.+)>/);
    if (match) return match[1];
    return address;
}


function formatSize(bytes?: number): string {
    if (!bytes) return '';
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function formatTime(sentAt?: string): string {
    if (!sentAt) return '';
    try {
        return new Date(sentAt).toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit' });
    } catch { return ''; }
}

function formatDateTime(sentAt?: string): string {
    if (!sentAt) return '';
    try {
        const d = new Date(sentAt);
        return d.toLocaleDateString('de-DE', {
            weekday: 'long', day: 'numeric', month: 'long', year: 'numeric',
        }) + ', ' + d.toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit' }) + ' Uhr';
    } catch { return ''; }
}

function formatDateLabel(sentAt?: string): string {
    if (!sentAt) return '';
    try {
        return new Date(sentAt).toLocaleDateString('de-DE', {
            weekday: 'long', day: 'numeric', month: 'long', year: 'numeric',
        });
    } catch { return ''; }
}

function getDayString(sentAt?: string): string {
    return sentAt ? sentAt.split('T')[0] : '';
}

// ─────────────────────────────────────────────────────────────────
// EmailAttachmentChip
// ─────────────────────────────────────────────────────────────────

interface EmailAttachmentChipProps {
    attachment: ThreadAttachment;
    emailId: number;
    onPreview?: (url: string, type: 'image' | 'pdf', name: string) => void;
}

function EmailAttachmentChip({ attachment, emailId, onPreview }: EmailAttachmentChipProps) {
    const filename = attachment.originalFilename || 'Datei';
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
                       transition-colors duration-150 text-xs text-slate-700 cursor-pointer group"
        >
            <File className="w-3.5 h-3.5 text-rose-500 shrink-0 group-hover:text-rose-600" />
            <span className="truncate font-medium">{filename}</span>
            {attachment.sizeBytes && (
                <span className="text-slate-400 shrink-0 ml-auto">{formatSize(attachment.sizeBytes)}</span>
            )}
        </button>
    );
}

// ─────────────────────────────────────────────────────────────────
// SkeletonBubble
// ─────────────────────────────────────────────────────────────────

function SkeletonBubble({ alignRight }: { alignRight: boolean }) {
    return (
        <div className={cn('flex items-start gap-3 mb-2', alignRight && 'flex-row-reverse')}>
            <div className="w-8 h-8 rounded-full bg-slate-200 animate-pulse shrink-0 mt-1" />
            <div className="flex flex-col gap-1.5" style={{ maxWidth: '70%' }}>
                <div className="h-3 w-24 bg-slate-200 animate-pulse rounded" />
                <div className="h-20 bg-slate-200 animate-pulse rounded-xl" />
            </div>
        </div>
    );
}

// ─────────────────────────────────────────────────────────────────
// DaySeparator
// ─────────────────────────────────────────────────────────────────

function DaySeparator({ label }: { label: string }) {
    return (
        <div className="flex items-center gap-3 my-5">
            <div className="flex-1 h-px bg-slate-200" />
            <span className="px-3 py-1 rounded-full bg-white border border-slate-200
                             text-xs font-medium text-slate-500 shadow-sm whitespace-nowrap">
                {label}
            </span>
            <div className="flex-1 h-px bg-slate-200" />
        </div>
    );
}

// ─────────────────────────────────────────────────────────────────
// EmailThreadBubble – kollabiert: Chat-Stil | expandiert: Email-Stil
// ─────────────────────────────────────────────────────────────────

interface BubbleProps {
    entry: EmailThreadEntry;
    isFocused: boolean;
    showAvatar: boolean;
    showSenderName: boolean;
    isLast: boolean;
    onPreview?: (url: string, type: 'image' | 'pdf', name: string) => void;
    onReply?: (entry: EmailThreadEntry) => void;
}

function EmailThreadBubble({ entry, isFocused, showAvatar, showSenderName, onPreview, onReply }: BubbleProps) {
    const [expanded, setExpanded] = useState(isFocused);
    const bubbleRef = useRef<HTMLDivElement>(null);
    const isOut = entry.direction === 'OUT';

    useEffect(() => {
        if (isFocused && bubbleRef.current) {
            const prefersReduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
            bubbleRef.current.scrollIntoView({ block: 'center', behavior: prefersReduced ? 'auto' : 'smooth' });
        }
    }, [isFocused]);

    const visibleAttachments = entry.attachments.filter(a => !a.inline);
    const fromName = extractDisplayName(entry.fromAddress);
    const fromEmail = extractEmail(entry.fromAddress);
    const toName = extractDisplayName(entry.recipient);
    const toEmail = extractEmail(entry.recipient);
    const avatarName = isOut ? toName : fromName;
    const initial = (avatarName.charAt(0) || '?').toUpperCase();
    const avatarBg = isOut ? 'bg-emerald-500' : 'bg-rose-500';

    // ── EXPANDIERT: volle E-Mail-Ansicht ──────────────────────────
    if (expanded) {
        return (
            <div
                ref={bubbleRef}
                className={cn(
                    'bg-white rounded-xl border shadow-sm overflow-hidden mb-2',
                    isFocused ? 'border-rose-300 ring-1 ring-rose-200' : 'border-slate-200',
                )}
            >
                {/* E-Mail-Header */}
                <div
                    role="button"
                    tabIndex={0}
                    onClick={() => setExpanded(false)}
                    onKeyDown={e => e.key === 'Enter' && setExpanded(false)}
                    className="flex items-start gap-3 px-5 py-4 cursor-pointer
                               hover:bg-slate-50 transition-colors duration-150
                               focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-400"
                >
                    {/* Avatar */}
                    <div className={cn(
                        'w-9 h-9 rounded-full text-white flex items-center justify-center',
                        'text-sm font-semibold shadow-sm shrink-0 mt-0.5',
                        avatarBg
                    )}>
                        {initial}
                    </div>

                    {/* Absender + Meta */}
                    <div className="flex-1 min-w-0">
                        <div className="flex items-center justify-between gap-2 mb-0.5">
                            <p className="font-semibold text-slate-900 truncate text-sm">
                                {isOut ? toName : fromName}
                            </p>
                            <span className="text-xs text-slate-400 whitespace-nowrap shrink-0">
                                {formatDateTime(entry.sentAt)}
                            </span>
                        </div>
                        <div className="text-xs text-slate-500 space-y-0.5">
                            {isOut ? (
                                <p><span className="text-slate-400">Von:</span> {fromName} &lt;{fromEmail}&gt;</p>
                            ) : (
                                <p><span className="text-slate-400">Von:</span> {fromName}{fromEmail && fromEmail !== fromName && ` <${fromEmail}>`}</p>
                            )}
                            <p><span className="text-slate-400">An:</span> {toName}{toEmail && toEmail !== toName && ` <${toEmail}>`}</p>
                        </div>
                    </div>

                    <ChevronUp className="w-4 h-4 text-slate-400 shrink-0 mt-1" />
                </div>

                {/* Trennlinie */}
                <div className="h-px bg-slate-100 mx-5" />

                {/* Vollständiger HTML-Body – zitierte Inhalte eingeklappt */}
                <div className="px-1">
                    <EmailContentFrame
                        html={entry.htmlBody || entry.snippet || '<p style="color:#94a3b8;font-style:italic">Kein Inhalt</p>'}
                        className="bg-white"
                        hideQuotes
                    />
                </div>

                {/* Anhänge */}
                {visibleAttachments.length > 0 && (
                    <div className="px-5 pb-4 pt-2 border-t border-slate-100">
                        <p className="text-xs font-medium text-slate-500 mb-2 flex items-center gap-1">
                            <Paperclip className="w-3 h-3" />
                            {visibleAttachments.length} {visibleAttachments.length === 1 ? 'Anhang' : 'Anhänge'}
                        </p>
                        <div className="flex flex-wrap gap-2">
                            {visibleAttachments.map(att => (
                                <EmailAttachmentChip
                                    key={att.id}
                                    attachment={att}
                                    emailId={entry.id}
                                    onPreview={onPreview}
                                />
                            ))}
                        </div>
                    </div>
                )}

                {/* Antworten-Button */}
                {onReply && (
                    <div className="px-5 pb-3 flex justify-end">
                        <button
                            onClick={() => onReply(entry)}
                            className="inline-flex items-center gap-1.5 text-xs font-medium
                                       text-slate-500 hover:text-rose-600 transition-colors"
                        >
                            <Reply className="w-3.5 h-3.5" />
                            Antworten
                        </button>
                    </div>
                )}
            </div>
        );
    }

    // ── KOLLABIERT: kompakte Chat-Bubble ──────────────────────────
    return (
        <div className={cn('flex items-end gap-2 mb-1', isOut && 'flex-row-reverse')}>
            {/* Avatar */}
            {showAvatar ? (
                <div className={cn(
                    'w-7 h-7 rounded-full text-white flex items-center justify-center',
                    'text-xs font-semibold shadow-sm shrink-0',
                    avatarBg
                )}>
                    {initial}
                </div>
            ) : (
                <div className="w-7 shrink-0" />
            )}

            {/* Bubble */}
            <div className={cn('flex flex-col max-w-[72%]', isOut ? 'items-end' : 'items-start')}>
                {showSenderName && (
                    <p className={cn('text-xs font-semibold mb-1 px-1', isOut ? 'text-emerald-700' : 'text-rose-700')}>
                        {isOut ? toName : fromName}
                    </p>
                )}
                <div
                    ref={bubbleRef}
                    role="button"
                    tabIndex={0}
                    onClick={() => setExpanded(true)}
                    onKeyDown={e => e.key === 'Enter' && setExpanded(true)}
                    className={cn(
                        'text-left rounded-2xl px-4 py-2.5 shadow-sm cursor-pointer w-full',
                        'transition-colors duration-200',
                        'focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-400',
                        isOut
                            ? 'bg-white border border-slate-200 rounded-br-none hover:bg-slate-50'
                            : 'bg-rose-50 border border-rose-200 rounded-bl-none hover:bg-rose-100/70',
                        isFocused && 'ring-2 ring-rose-300'
                    )}
                >
                    {entry.forwarded && (
                        <p className="text-[10px] font-medium text-slate-400 uppercase tracking-wide mb-1">
                            Weitergeleitet
                        </p>
                    )}
                    <p className="text-sm text-slate-700 line-clamp-2 leading-relaxed">
                        {entry.snippet || <span className="italic text-slate-400">Kein Inhalt</span>}
                    </p>

                    <div className="flex items-center justify-between gap-2 mt-1.5">
                        <div className="flex items-center gap-1.5">
                            {visibleAttachments.length > 0 && (
                                <span className="inline-flex items-center gap-0.5 text-[11px] text-slate-400">
                                    <Paperclip className="w-2.5 h-2.5" />
                                    {visibleAttachments.length}
                                </span>
                            )}
                        </div>
                        <div className="flex items-center gap-1">
                            <p className="text-[10px] text-slate-400 tabular-nums">{formatTime(entry.sentAt)}</p>
                            <ChevronDown className="w-3 h-3 text-slate-300" />
                        </div>
                    </div>
                </div>
            </div>
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
    onReply?: (entry: EmailThreadEntry) => void;
}

export function EmailThreadView({ thread, loading, onPreview, onReply }: EmailThreadViewProps) {
    // Thread-übergreifende Anhang-Dedup über originalFilename:sizeBytes
    const seenAttachments = new Set<string>();
    const emails = thread.emails.map(entry => ({
        ...entry,
        attachments: entry.attachments.filter(a => {
            const key = `${a.originalFilename}:${a.sizeBytes ?? ''}`;
            if (seenAttachments.has(key)) return false;
            seenAttachments.add(key);
            return true;
        }),
    }));

    if (loading) {
        return (
            <div className="flex-1 overflow-auto bg-slate-50 px-5 py-5 space-y-3">
                <SkeletonBubble alignRight={false} />
                <SkeletonBubble alignRight={true} />
                <SkeletonBubble alignRight={false} />
            </div>
        );
    }

    return (
        <div className="flex-1 overflow-auto bg-slate-50 px-5 py-5">
            {emails.map((entry, idx) => {
                const prev = idx > 0 ? emails[idx - 1] : null;
                const showDaySeparator = !prev || getDayString(entry.sentAt) !== getDayString(prev.sentAt);
                const showAvatar = !prev || prev.fromAddress !== entry.fromAddress || prev.direction !== entry.direction;
                const isLast = idx === emails.length - 1;

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
                        />
                    </React.Fragment>
                );
            })}
        </div>
    );
}
