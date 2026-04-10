import React, { useState, useMemo, useCallback, useEffect } from 'react';
import { Mail, Search, Paperclip, Plus, Reply, MessagesSquare, X, ArrowLeft } from 'lucide-react';
import { Button } from './ui/button';
import { cn } from '../lib/utils';
import { EmailComposeModal } from './EmailComposeModal';
import { EmailThreadView } from './EmailThreadView';
import { EmailComposeForm } from './EmailComposeForm';
import type { EmailThread, EmailThreadEntry } from './EmailThreadView';
import type { ProjektDetail } from '../types';

// Generic email type that works across different entities
export interface GenericEmail {
    id: number;
    subject?: string;
    from?: string;
    fromAddress?: string;
    to?: string;
    bodyHtml?: string;
    bodyPreview?: string;
    body?: string;
    direction?: 'IN' | 'OUT';
    sentAt?: string;
    sentDate?: string;
    receivedDate?: string;
    attachments?: Array<{
        id?: number;
        originalFilename?: string;
        storedFilename?: string;
        filename?: string;  // From Kommunikation API
        url?: string;       // From Kommunikation API
        contentId?: string;
        inline?: boolean;
    }>;
    sender?: string;
    recipients?: string[];
    // Thread support
    parentEmailId?: number;
    parentId?: number;
    replyCount?: number;
    replies?: GenericEmail[];
}

export interface EmailsTabProps {
    // Required
    emails: GenericEmail[];

    // Entity context - exactly one should be provided
    projektId?: number;
    anfrageId?: number;
    angebotId?: number;
    kundeId?: number;
    lieferantId?: number;

    // Optional context for compose/reply
    entityName?: string; // e.g. "Bauvorhaben XYZ" for email context
    kundenEmail?: string; // First customer email for replies

    // Optional: Projekt detail object for compose modal
    projekt?: {
        id: number;
        bauvorhaben?: string;
        kunde?: string;
        kundenEmails?: string[];
        kundeDto?: { kundenEmails?: string[] };
        bezahlt?: boolean;
    };

    // Optional: Anfrage detail object for compose modal  
    anfrage?: {
        bauvorhaben: string;
        kundenName?: string;
        kundenEmails?: string[];
        kundenAnrede?: string;
        kundenAnsprechpartner?: string;
    };

    // Optional: Angebot detail object for compose modal (mapped to anfrage payload)
    angebot?: {
        bauvorhaben: string;
        kundenName?: string;
        kundenEmails?: string[];
        kundenAnrede?: string;
        kundenAnsprechpartner?: string;
    };

    // Callbacks
    onEmailSent?: () => void;

    // Optional: Show/hide compose button (defaults to true)
    showComposeButton?: boolean;

    // Optional: Show/hide reply button in thread view (defaults to true)
    showReplyButton?: boolean;
}

// ─── Helper: count replies recursively ────────────────────────
function countReplies(email: GenericEmail): number {
    if (!email.replies || email.replies.length === 0) return 0;
    let count = email.replies.length;
    for (const r of email.replies) {
        count += countReplies(r);
    }
    return count;
}

// ─── Helper: extract display name ────────────────────────────
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

export const EmailsTab: React.FC<EmailsTabProps> = ({
    emails,
    projektId,
    anfrageId,
    angebotId,
    kundeId: _kundeId,
    lieferantId: _lieferantId,
    entityName: _entityName,
    kundenEmail: _kundenEmail,
    projekt,
    anfrage,
    angebot,
    onEmailSent,
    showComposeButton = true,
    showReplyButton = true,
}) => {
    // State
    const [emailFilter, setEmailFilter] = useState<'all' | 'in' | 'out'>('all');
    const [emailSearch, setEmailSearch] = useState('');
    const [showComposeModal, setShowComposeModal] = useState(false);

    // Thread view state
    const [selectedEmailId, setSelectedEmailId] = useState<number | null>(null);
    const [thread, setThread] = useState<EmailThread | null>(null);
    const [threadLoading, setThreadLoading] = useState(false);

    // Reply state (inline compose within thread dialog)
    const [isReplying, setIsReplying] = useState(false);
    const [replyToEmail, setReplyToEmail] = useState<GenericEmail | null>(null);
    const [replyToEmailId, setReplyToEmailId] = useState<number | undefined>(undefined);

    // Attachment preview
    const [previewAttachment, setPreviewAttachment] = useState<{ url: string; type: 'image' | 'pdf'; name: string } | null>(null);

    // Determine the entity context
    const contextProjektId = projektId;
    const contextAnfrageId = anfrageId ?? angebotId;

    // ─── Thread-grouped emails: show only roots ───────────────
    const threadRoots = useMemo(() => {
        if (!emails) return [];
        const parentIdOf = (e: GenericEmail) => e.parentEmailId ?? e.parentId;
        const emailMap = new Map<number, GenericEmail>();
        for (const e of emails) emailMap.set(e.id, e);

        // Build reply counts by walking to root
        const replyCounts = new Map<number, number>();
        for (const e of emails) {
            const pid = parentIdOf(e);
            if (pid != null) {
                let rootId = pid;
                const visited = new Set<number>();
                while (emailMap.has(rootId)) {
                    const parent = emailMap.get(rootId)!;
                    const ppid = parentIdOf(parent);
                    if (ppid == null || !emailMap.has(ppid) || visited.has(ppid)) break;
                    visited.add(rootId);
                    rootId = ppid;
                }
                replyCounts.set(rootId, (replyCounts.get(rootId) || 0) + 1);
            }
        }

        // Roots = emails without parentId OR whose parent is not in this list
        const roots = emails.filter(e => {
            const pid = parentIdOf(e);
            return pid == null || !emailMap.has(pid);
        });

        return roots.map(root => ({
            ...root,
            // Prefer server-provided replyCount (counts full thread chain regardless of entity assignment).
            // Fall back to client-side flat-list traversal for older endpoints that don't send replyCount.
            _replyCount: (root.replyCount != null && root.replyCount > 0)
                ? root.replyCount
                : (replyCounts.get(root.id) || 0) + countReplies(root),
        }));
    }, [emails]);

    // Filter thread roots
    const filteredEmails = useMemo(() => {
        return threadRoots.filter(email => {
            if (emailFilter === 'in' && email.direction !== 'IN') return false;
            if (emailFilter === 'out' && email.direction !== 'OUT') return false;
            if (emailSearch.trim()) {
                const search = emailSearch.toLowerCase();
                const subject = (email.subject || '').toLowerCase();
                const from = (email.from || email.fromAddress || email.sender || '').toLowerCase();
                const body = (email.bodyHtml || email.bodyPreview || email.body || '').toLowerCase();
                if (!subject.includes(search) && !from.includes(search) && !body.includes(search)) {
                    return false;
                }
            }
            return true;
        });
    }, [threadRoots, emailFilter, emailSearch]);

    // ─── Load thread when email is selected ───────────────────
    useEffect(() => {
        if (!selectedEmailId) { setThread(null); return; }
        setThreadLoading(true);
        fetch(`/api/emails/${selectedEmailId}/thread`)
            .then(r => { if (!r.ok) throw new Error('Thread not found'); return r.json(); })
            .then((data: EmailThread) => setThread(data))
            .catch(() => setThread(null))
            .finally(() => setThreadLoading(false));
    }, [selectedEmailId]);

    const handleEmailClick = (email: GenericEmail) => {
        setSelectedEmailId(email.id);
        setIsReplying(false);
        setReplyToEmail(null);
    };

    // ─── Handle reply from thread view ────────────────────────
    const handleThreadReply = useCallback(async (entry: EmailThreadEntry) => {
        let fullEmail: GenericEmail | null = null;
        try {
            const res = await fetch(`/api/emails/${entry.id}`);
            if (res.ok) fullEmail = await res.json();
        } catch { /* fallback */ }
        if (!fullEmail) {
            fullEmail = {
                id: entry.id, subject: entry.subject, fromAddress: entry.fromAddress,
                to: entry.recipient, bodyHtml: entry.htmlBody, body: entry.snippet,
                direction: entry.direction, sentAt: entry.sentAt,
            };
        }
        setReplyToEmail(fullEmail);
        setReplyToEmailId(entry.id);
        setIsReplying(true);
    }, []);

    // ─── Handle reply success ─────────────────────────────────
    const handleReplySuccess = useCallback(() => {
        setIsReplying(false);
        setReplyToEmail(null);
        setReplyToEmailId(undefined);
        // Reload thread
        if (selectedEmailId) {
            setThreadLoading(true);
            fetch(`/api/emails/${selectedEmailId}/thread`)
                .then(r => r.ok ? r.json() : null)
                .then((data: EmailThread | null) => setThread(data))
                .catch(() => {})
                .finally(() => setThreadLoading(false));
        }
        if (onEmailSent) onEmailSent();
    }, [selectedEmailId, onEmailSent]);

    const closeThreadView = () => {
        setSelectedEmailId(null);
        setThread(null);
        setIsReplying(false);
        setReplyToEmail(null);
    };

    const handleComposeClose = () => { setShowComposeModal(false); };
    const handleComposeSent = () => {
        setShowComposeModal(false);
        if (onEmailSent) onEmailSent();
    };

    const formatDate = (email: GenericEmail) => {
        const dateStr = email.sentAt || email.receivedDate || email.sentDate;
        if (!dateStr) return '-';
        try {
            return new Date(dateStr).toLocaleDateString('de-DE', {
                day: '2-digit', month: '2-digit', year: 'numeric',
                hour: '2-digit', minute: '2-digit'
            });
        } catch { return dateStr; }
    };

    const getTextPreview = (email: GenericEmail) => {
        const html = email.bodyHtml || email.bodyPreview || email.body || '';
        const div = document.createElement('div');
        div.innerHTML = html;

        // Remove style/script tags first (prevent raw CSS in preview)
        div.querySelectorAll('style, script, link').forEach(el => el.remove());
        // Remove HTML comments (<!-- ... -->)
        const walker = document.createTreeWalker(div, NodeFilter.SHOW_COMMENT, null);
        const comments: Comment[] = [];
        let c: Comment | null;
        while ((c = walker.nextNode() as Comment | null)) comments.push(c);
        comments.forEach(n => n.remove());

        // Remove quoted content (Outlook, Gmail, standard blockquote, etc.)
        // Outlook OWA
        const rplyEl = div.querySelector('#divRplyFwdMsg');
        if (rplyEl) {
            let cur: Element | null = rplyEl;
            while (cur) { const next: Element | null = cur.nextElementSibling; cur.remove(); cur = next; }
            const hr = div.querySelector('hr');
            if (hr) hr.remove();
        }
        // Outlook Desktop: border-top div with "Von:/From:/Gesendet:/Sent:"
        div.querySelectorAll<HTMLElement>('div[style*="border-top"]').forEach(bd => {
            if (/Von:|From:|Gesendet:|Sent:/i.test(bd.textContent || '')) {
                let startEl: Element = bd;
                if (bd.parentElement?.tagName === 'DIV') {
                    const pLen = (bd.parentElement.textContent || '').trim().length;
                    const bLen = (bd.textContent || '').trim().length;
                    if (pLen > 0 && Math.abs(pLen - bLen) < 30) startEl = bd.parentElement;
                }
                let cur: Element | null = startEl;
                while (cur) { const next: Element | null = cur.nextElementSibling; cur.remove(); cur = next; }
            }
        });
        // Gmail quotes, Yahoo, standard blockquotes
        div.querySelectorAll('blockquote, .gmail_quote, .yahoo_quoted, .moz-cite-prefix, [class*="ygmail_extra"]')
            .forEach(el => el.remove());
        // email-quote class (from our own compose)
        div.querySelectorAll('.email-quote').forEach(el => el.remove());

        // Extract text, collapse whitespace, remove email signatures/footers
        let text = (div.textContent || div.innerText || '').replace(/\s+/g, ' ').trim();

        // Strip common text-based quote markers: "Von: ... Gesendet: ... An: ... Betreff: ..." pattern
        text = text.replace(/\s*Von:.*?Gesendet:.*?An:.*?Betreff:.*$/is, '').trim();
        // Strip "Am DD.MM.YYYY um HH:MM schrieb ...:" (t-online, Thunderbird)
        text = text.replace(/\s*(Am\s+\d{1,2}\.\d{1,2}\.\d{2,4}\s+um\s+\d{1,2}:\d{2}\s+schrieb\b.*?:).*/is, '').trim();
        // Strip "Am DD.MM.YYYY ... schrieb ...:" (generic German)
        text = text.replace(/\s*(Am\s+\d{1,2}\.\d{1,2}\.\d{4}.*?schrieb.*?:).*/is, '').trim();
        // Strip "On ... wrote:" (English)
        text = text.replace(/\s*(On\s+\w+.*?wrote.*?:).*/is, '').trim();
        // Strip "-------- Ursprüngliche Nachricht --------" / "---- Original Message ----"
        text = text.replace(/\s*-{3,}\s*(Urspr[üu]ngliche Nachricht|Original Message|Weitergeleitete Nachricht|Forwarded Message)\s*-{3,}.*/is, '').trim();

        return text;
    };

    // ─── Build reply quote ────────────────────────────────────
    const replyQuote = useMemo(() => {
        if (!replyToEmail) return undefined;
        const senderName = extractDisplayName(replyToEmail.fromAddress || replyToEmail.from || replyToEmail.sender);
        const date = replyToEmail.sentAt
            ? new Date(replyToEmail.sentAt).toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric' })
              + ', ' + new Date(replyToEmail.sentAt).toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit' }) + ' Uhr'
            : '';
        let cleanBody = replyToEmail.bodyHtml || replyToEmail.body || '';
        try {
            const parser = new DOMParser();
            const doc = parser.parseFromString(cleanBody, 'text/html');
            doc.querySelectorAll('script, style, link').forEach(el => el.remove());
            cleanBody = doc.body.innerHTML;
        } catch { /* ignore */ }
        return `<div class="email-quote" style="border-left:3px solid #e2e8f0;padding-left:1rem;color:#64748b;margin-top:0.5rem">
            <p style="font-size:0.8125rem;color:#94a3b8;margin-bottom:0.5rem">Am ${date} schrieb ${senderName}:</p>
            ${cleanBody}
        </div>`;
    }, [replyToEmail]);

    const replyInitialRecipient = useMemo(() => {
        if (!replyToEmail) return '';
        const addr = replyToEmail.fromAddress || replyToEmail.from || replyToEmail.sender || '';
        const name = extractDisplayName(addr);
        const em = extractEmail(addr);
        return em ? `"${name}" <${em}>` : name;
    }, [replyToEmail]);

    const replyInitialSubject = useMemo(() => {
        if (!replyToEmail?.subject) return 'AW: ';
        return /^(\s*)(aw|re):\s*/i.test(replyToEmail.subject) ? replyToEmail.subject : `AW: ${replyToEmail.subject}`;
    }, [replyToEmail]);

    return (
        <div className="space-y-4">
            {/* ─── Thread Detail View (replaces EmailDetailModal) ── */}
            {selectedEmailId && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
                    <div className="bg-white rounded-2xl shadow-2xl w-[75vw] max-w-5xl h-[90vh] flex flex-col overflow-hidden">
                        {/* Header */}
                        <div className="flex items-center justify-between px-5 py-3 border-b border-slate-200 bg-slate-50 shrink-0">
                            <div className="flex items-center gap-3">
                                <button onClick={closeThreadView} className="p-1.5 rounded-lg hover:bg-slate-200 transition-colors">
                                    <ArrowLeft className="w-5 h-5 text-slate-600" />
                                </button>
                                <div>
                                    <h3 className="font-semibold text-slate-900 text-sm truncate max-w-md">
                                        {threadRoots.find(e => e.id === selectedEmailId)?.subject || '(kein Betreff)'}
                                    </h3>
                                    {thread && (
                                        <p className="text-xs text-slate-500">
                                            {thread.emails.length} {thread.emails.length === 1 ? 'Nachricht' : 'Nachrichten'} im Thread
                                        </p>
                                    )}
                                </div>
                            </div>
                            <div className="flex items-center gap-2">
                                {showReplyButton && !isReplying && thread && (
                                    <Button
                                        variant="outline"
                                        size="sm"
                                        onClick={() => {
                                            if (thread.emails.length > 0) {
                                                handleThreadReply(thread.emails[thread.emails.length - 1]);
                                            }
                                        }}
                                        className="gap-1.5 text-slate-700 border-slate-300 hover:bg-rose-50 hover:text-rose-700 hover:border-rose-300"
                                    >
                                        <Reply className="w-4 h-4" /> Antworten
                                    </Button>
                                )}
                                <button onClick={closeThreadView} className="p-1.5 rounded-lg hover:bg-slate-200 transition-colors">
                                    <X className="w-5 h-5 text-slate-500" />
                                </button>
                            </div>
                        </div>

                        {/* Content */}
                        {isReplying && replyToEmail ? (
                            <div className="flex-1 overflow-hidden flex flex-col">
                                <EmailComposeForm
                                    onClose={() => { setIsReplying(false); setReplyToEmail(null); setReplyToEmailId(undefined); }}
                                    onSuccess={handleReplySuccess}
                                    initialRecipient={replyInitialRecipient}
                                    initialSubject={replyInitialSubject}
                                    replyQuote={replyQuote}
                                    replyEmailId={replyToEmailId}
                                    projektId={contextProjektId}
                                    anfrageId={contextAnfrageId}
                                    variant="modal"
                                />
                            </div>
                        ) : threadLoading ? (
                            <div className="flex-1 overflow-auto bg-slate-50 px-6 py-6 space-y-2">
                                {[false, true, false].map((right, i) => (
                                    <div key={i} className={`flex items-end gap-2 mb-2 ${right ? 'flex-row-reverse' : ''}`}>
                                        <div className="w-8 h-8 rounded-full bg-slate-200 animate-pulse shrink-0" />
                                        <div className={`animate-pulse bg-slate-200 rounded-2xl h-16 ${right ? 'rounded-br-sm w-56' : 'rounded-bl-sm w-64'}`} />
                                    </div>
                                ))}
                            </div>
                        ) : thread ? (
                            <EmailThreadView
                                thread={thread}
                                onPreview={(url, type, name) => setPreviewAttachment({ url, type, name })}
                                onReply={showReplyButton ? (entry) => handleThreadReply(entry) : undefined}
                            />
                        ) : (
                            <div className="flex-1 flex items-center justify-center p-6">
                                <p className="text-sm text-slate-400 italic">Thread konnte nicht geladen werden.</p>
                            </div>
                        )}
                    </div>
                </div>
            )}

            {/* ─── Attachment Preview ───────────────────────── */}
            {previewAttachment && (
                <div className="fixed inset-0 z-[60] flex items-center justify-center bg-black/60" onClick={() => setPreviewAttachment(null)}>
                    <div className="bg-white rounded-xl shadow-2xl max-w-4xl max-h-[90vh] overflow-auto p-2" onClick={e => e.stopPropagation()}>
                        <div className="flex justify-between items-center px-3 py-2 border-b border-slate-100">
                            <p className="text-sm font-medium text-slate-700">{previewAttachment.name}</p>
                            <button onClick={() => setPreviewAttachment(null)} className="p-1 hover:bg-slate-100 rounded">
                                <X className="w-4 h-4" />
                            </button>
                        </div>
                        {previewAttachment.type === 'image' ? (
                            <img src={previewAttachment.url} alt={previewAttachment.name} className="max-w-full max-h-[80vh]" />
                        ) : (
                            <iframe src={previewAttachment.url} className="w-[800px] h-[80vh]" title={previewAttachment.name} />
                        )}
                    </div>
                </div>
            )}

            {/* ─── Header with filters and compose button ──── */}
            <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4 mb-4">
                <h3 className="text-lg font-medium text-slate-900">E-Mails</h3>
                <div className="flex flex-wrap items-center gap-3">
                    <div className="relative">
                        <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
                        <input
                            type="text"
                            placeholder="E-Mails durchsuchen..."
                            value={emailSearch}
                            onChange={e => setEmailSearch(e.target.value)}
                            className="pl-9 pr-3 py-1.5 text-sm border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-rose-500 w-48"
                        />
                    </div>
                    <div className="flex rounded-lg border border-slate-200 overflow-hidden">
                        <button
                            onClick={() => setEmailFilter('all')}
                            className={cn("px-3 py-1.5 text-sm font-medium transition-colors",
                                emailFilter === 'all' ? "bg-rose-600 text-white" : "bg-white text-slate-600 hover:bg-slate-50"
                            )}
                        >Alle</button>
                        <button
                            onClick={() => setEmailFilter('in')}
                            className={cn("px-3 py-1.5 text-sm font-medium transition-colors border-l border-slate-200",
                                emailFilter === 'in' ? "bg-blue-600 text-white" : "bg-white text-slate-600 hover:bg-slate-50"
                            )}
                        >Eingang</button>
                        <button
                            onClick={() => setEmailFilter('out')}
                            className={cn("px-3 py-1.5 text-sm font-medium transition-colors border-l border-slate-200",
                                emailFilter === 'out' ? "bg-green-600 text-white" : "bg-white text-slate-600 hover:bg-slate-50"
                            )}
                        >Ausgang</button>
                    </div>
                    {showComposeButton && (projektId || anfrageId || angebotId) && (
                        <Button onClick={() => setShowComposeModal(true)} className="bg-rose-600 text-white hover:bg-rose-700">
                            <Plus className="w-4 h-4 mr-2" /> Neue E-Mail
                        </Button>
                    )}
                </div>
            </div>

            {/* ─── Thread list (roots only) ─────────────────── */}
            {filteredEmails.length > 0 ? (
                <div className="space-y-3">
                    {filteredEmails.map((email) => {
                        const isThread = email._replyCount > 0;
                        return (
                            <div
                                key={email.id}
                                className={cn(
                                    "rounded-xl border transition-all cursor-pointer group relative overflow-hidden",
                                    isThread
                                        ? "bg-white border-rose-200 hover:border-rose-400 hover:shadow-md shadow-sm"
                                        : "bg-white border-slate-200 hover:border-rose-300 hover:shadow-md"
                                )}
                                onClick={() => handleEmailClick(email)}
                            >
                                {/* Linker Akzent-Balken für Threads */}
                                {isThread && (
                                    <div className="absolute left-0 top-0 bottom-0 w-1 bg-rose-400" />
                                )}
                                <div className={cn("p-4", isThread && "pl-5")}>
                                    <div className="flex items-start justify-between gap-4">
                                        <div className="flex items-start gap-3 flex-1 min-w-0">
                                            <div className="flex-1 min-w-0">
                                                <div className="flex items-center gap-2 mb-1.5 flex-wrap">
                                                    <span className={cn(
                                                        "text-xs font-medium px-2 py-0.5 rounded-full",
                                                        email.direction === 'IN' ? "bg-blue-50 text-blue-700" : "bg-green-50 text-green-700"
                                                    )}>
                                                        {email.direction === 'IN' ? 'Eingang' : 'Ausgang'}
                                                    </span>
                                                    {isThread && (
                                                        <span
                                                            className="inline-flex items-center gap-1 text-xs font-semibold px-2.5 py-0.5 rounded-full bg-rose-600 text-white shadow-sm"
                                                            title={`${email._replyCount + 1} Nachrichten in diesem Thread`}
                                                        >
                                                            <MessagesSquare className="w-3 h-3" />
                                                            {email._replyCount + 1} Nachrichten
                                                        </span>
                                                    )}
                                                    <span className="text-xs text-slate-400">{formatDate(email)}</span>
                                                </div>
                                                <h4 className="font-medium text-slate-900 truncate">{email.subject || '(kein Betreff)'}</h4>
                                                <p className="text-sm text-slate-500 truncate">
                                                    {email.direction === 'IN' ? 'Von: ' : 'An: '}
                                                    {email.from || email.fromAddress || email.sender || 'Unbekannt'}
                                                </p>
                                                {(email.bodyHtml || email.bodyPreview || email.body) && (
                                                    <p className="text-sm text-slate-400 mt-1 line-clamp-2">{getTextPreview(email)}</p>
                                                )}
                                            </div>
                                        </div>
                                        <div className="flex flex-col items-end gap-2 shrink-0">
                                            {isThread
                                                ? <MessagesSquare className="w-5 h-5 text-rose-300 group-hover:text-rose-500 transition-colors" />
                                                : <Mail className="w-5 h-5 text-slate-300 group-hover:text-rose-400 transition-colors" />
                                            }
                                            {email.attachments && email.attachments.filter(a => !a.inline).length > 0 && (
                                                <Paperclip className="w-4 h-4 text-slate-400" />
                                            )}
                                        </div>
                                    </div>
                                </div>
                            </div>
                        );
                    })}
                </div>
            ) : (
                <div className="flex flex-col items-center justify-center py-12 text-slate-500">
                    <Mail className="w-12 h-12 text-slate-300 mb-3" />
                    <p>{emailSearch || emailFilter !== 'all' ? 'Keine E-Mails gefunden.' : 'Keine E-Mails vorhanden.'}</p>
                </div>
            )}

            {/* Compose Modal */}
            {showComposeModal && (
                <EmailComposeModal
                    isOpen={showComposeModal}
                    onClose={handleComposeClose}
                    projektId={projektId}
                    projekt={projekt as unknown as ProjektDetail}
                    anfrageId={anfrageId ?? angebotId}
                    anfrage={anfrage ?? angebot}
                    onSuccess={handleComposeSent}
                />
            )}
        </div>
    );
};

export default EmailsTab;
