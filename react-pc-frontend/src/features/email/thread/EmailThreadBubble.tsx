import { useEffect, useMemo, useRef, useState } from 'react';
import { AlertTriangle, Paperclip, ChevronDown, ChevronUp, Reply, ReplyAll } from 'lucide-react';
import { klartextGrund } from '../../../lib/zustellGrund';
import { extractDisplayName, extractEmailAddress } from '../../../lib/emailAddress';
import { cn } from '../../../lib/utils';
import { scrolleMittigInDenBlick } from '../../../lib/scrollInDenBlick';
import { getThreadPreview } from '../threadQuotes';
import type { ReplyMode } from '../emailCenterModel';
import { EmailContentFrame } from '../../../components/EmailContentFrame';
import { EmailRecipientDropdown } from '../../../components/EmailRecipientDropdown';
import { EmailAttachmentChip } from './EmailAttachmentChip';
import { formatDateTime, formatTime } from './threadFormat';
import type { EmailThreadEntry } from './threadTypes';

// EmailThreadBubble – kollabiert: Chat-Stil | expandiert: Email-Stil

export interface BubbleProps {
    entry: EmailThreadEntry;
    isFocused: boolean;
    showAvatar: boolean;
    showSenderName: boolean;
    isLast: boolean;
    onPreview?: (url: string, type: 'image' | 'pdf', name: string) => void;
    onReply?: (entry: EmailThreadEntry, mode: ReplyMode) => void;
    /** Ob "Allen antworten" weitere Empfänger erreicht als "Antworten". */
    canReplyAll?: (entry: EmailThreadEntry) => boolean;
}

export function EmailThreadBubble({ entry, isFocused, showAvatar, showSenderName, onPreview, onReply, canReplyAll }: BubbleProps) {
    const [expanded, setExpanded] = useState(isFocused);
    const bubbleRef = useRef<HTMLDivElement>(null);
    const isOut = entry.direction === 'OUT';
    const preview = useMemo(() => getThreadPreview(entry.htmlBody || entry.snippet || '', { keepForwardedContent: entry.forwarded }),
        [entry.htmlBody, entry.snippet, entry.forwarded]);

    // Fokussierte Nachricht mittig in den Verlauf holen – ohne das Seitenlayout mitzuschieben
    // (scrollIntoView schob sonst das ganze E-Mail-Center unter die Menüleiste).
    useEffect(() => {
        if (isFocused && bubbleRef.current) scrolleMittigInDenBlick(bubbleRef.current);
    }, [isFocused]);

    const visibleAttachments = entry.attachments.filter(a => !a.inline);
    const fromName = extractDisplayName(entry.fromAddress);
    const fromEmail = extractEmailAddress(entry.fromAddress);
    const avatarName = fromName; // immer der Absender
    const initial = (avatarName.charAt(0) || '?').toUpperCase();
    // Eigene Mails neutral in Slate, eingehende in Rose: Grün hätte hier keine Bedeutung.
    const avatarBg = isOut ? 'bg-slate-600' : 'bg-rose-500';

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
                    onKeyDown={e => {
                        if (e.target === e.currentTarget && (e.key === 'Enter' || e.key === ' ')) {
                            e.preventDefault();
                            setExpanded(false);
                        }
                    }}
                    aria-expanded={true}
                    aria-label={`Nachricht von ${fromName} einklappen`}
                    className="flex items-start gap-2 px-3 py-3 cursor-pointer
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
                        <div className="flex flex-wrap items-center justify-between gap-x-2 gap-y-0.5 mb-0.5">
                            <p className="font-semibold text-slate-900 min-w-0 break-words text-sm">
                                {fromName}
                            </p>
                            <time dateTime={entry.sentAt} title={formatDateTime(entry.sentAt)} className="text-xs text-slate-500 whitespace-nowrap tabular-nums">
                                {formatTime(entry.sentAt)}
                            </time>
                        </div>
                        <div className="text-xs text-slate-500 space-y-0.5 [overflow-wrap:anywhere]">
                            {isOut ? (
                                <p><span className="text-slate-500">Von:</span> {fromName} &lt;{fromEmail}&gt;</p>
                            ) : (
                                <p><span className="text-slate-500">Von:</span> {fromName}{fromEmail && fromEmail !== fromName && ` <${fromEmail}>`}</p>
                            )}
                            <EmailRecipientDropdown recipients={entry.recipient} cc={entry.cc} />
                        </div>
                    </div>

                    <ChevronUp aria-hidden="true" className="w-4 h-4 text-slate-400 shrink-0 mt-1" />
                </div>

                {/* Rueckläufer-Warnung: Eine unzustellbare Antwort haengt mitten im
                    Thread und taucht in der Uebersichtsliste (nur Wurzeln) nicht auf —
                    hier ist die einzige Stelle, an der der Nutzer sie sieht. */}
                {entry.zustellStatus === 'UNZUSTELLBAR' && (
                    <div className="mx-5 mb-3 flex items-start gap-2 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2">
                        <AlertTriangle aria-hidden="true" className="w-4 h-4 text-rose-600 shrink-0 mt-0.5" />
                        <div className="min-w-0">
                            <p className="text-sm font-semibold text-rose-700">Nicht angekommen</p>
                            <p className="text-xs text-rose-600 break-words" title={entry.zustellFehler}>
                                {klartextGrund(entry.zustellFehler)}
                            </p>
                        </div>
                    </div>
                )}

                {/* Trennlinie */}
                <div className="h-px bg-slate-100 mx-5" />

                {/* Vollständiger HTML-Body – zitierte Inhalte eingeklappt */}
                <div className="px-1">
                    <EmailContentFrame
                        html={entry.htmlBody || entry.snippet || '<p style="color:#94a3b8;font-style:italic">Kein Inhalt</p>'}
                        className="bg-white"
                        hideQuotes
                        keepForwardedContent={entry.forwarded}
                    />
                </div>

                {/* Anhänge */}
                {visibleAttachments.length > 0 && (
                    <div className="px-5 pb-4 pt-2 border-t border-slate-100">
                        <p className="text-xs font-medium text-slate-500 mb-2 flex items-center gap-1">
                            <Paperclip aria-hidden="true" className="w-3 h-3" />
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
                    <div className="px-5 pb-3 flex justify-end gap-4">
                        <button
                            type="button"
                            onClick={() => onReply(entry, 'reply')}
                            className="inline-flex items-center gap-1.5 text-xs font-medium
                                       text-slate-500 hover:text-rose-600 transition-colors"
                        >
                            <Reply aria-hidden="true" className="w-3.5 h-3.5" />
                            Antworten
                        </button>
                        {canReplyAll?.(entry) && (
                            <button
                                type="button"
                                onClick={() => onReply(entry, 'replyAll')}
                                className="inline-flex items-center gap-1.5 text-xs font-medium
                                           text-slate-500 hover:text-rose-600 transition-colors"
                            >
                                <ReplyAll aria-hidden="true" className="w-3.5 h-3.5" />
                                Allen antworten
                            </button>
                        )}
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
            <div className={cn('flex min-w-0 flex-col max-w-[88%] xl:max-w-[80%]', isOut ? 'items-end' : 'items-start')}>
                {showSenderName && (
                    <p className={cn('text-xs font-semibold mb-1 px-1', isOut ? 'text-slate-700' : 'text-rose-700')}>
                        {fromName}
                    </p>
                )}
                <div
                    ref={bubbleRef}
                    role="button"
                    tabIndex={0}
                    onClick={() => setExpanded(true)}
                    onKeyDown={e => {
                        if (e.key === 'Enter' || e.key === ' ') {
                            e.preventDefault();
                            setExpanded(true);
                        }
                    }}
                    aria-expanded={false}
                    aria-label={`Nachricht von ${fromName} öffnen`}
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
                        <p className="text-[10px] font-medium text-slate-500 uppercase tracking-wide mb-1">
                            Weitergeleitet
                        </p>
                    )}
                    <p className="text-sm text-slate-700 line-clamp-3 leading-relaxed [overflow-wrap:anywhere]">
                        {preview || <span className="italic text-slate-500">Kein neuer Text</span>}
                    </p>

                    <div className="flex items-center justify-between gap-2 mt-1.5">
                        <div className="flex items-center gap-1.5">
                            {visibleAttachments.length > 0 && (
                                <span className="inline-flex items-center gap-0.5 text-[11px] text-slate-500">
                                    <Paperclip aria-hidden="true" className="w-2.5 h-2.5" />
                                    {visibleAttachments.length}
                                </span>
                            )}
                        </div>
                        <div className="flex items-center gap-1">
                            <p className="text-[10px] text-slate-500 tabular-nums">{formatTime(entry.sentAt)}</p>
                            <ChevronDown aria-hidden="true" className="w-3 h-3 text-slate-300" />
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
}
