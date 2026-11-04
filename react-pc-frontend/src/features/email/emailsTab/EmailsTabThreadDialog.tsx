import { ArrowLeft, Reply, X } from 'lucide-react';
import { Button } from '../../../components/ui/button';
import { EmailComposeForm } from '../../../components/EmailComposeForm';
import { EmailThreadView } from '../../../components/EmailThreadView';
import type { EmailThread, EmailThreadEntry } from '../../../components/EmailThreadView';
import type { EmailsTabReply } from './useEmailsTabReply';

interface EmailsTabThreadDialogProps {
    subject: string;
    thread: EmailThread | null;
    threadLoading: boolean;
    showReplyButton: boolean;
    reply: EmailsTabReply;
    projektId?: number;
    anfrageId?: number;
    onClose: () => void;
    onReplySuccess: () => void;
    onPreview: (url: string, type: 'image' | 'pdf', name: string) => void;
    onOpenDraft: (entry: EmailThreadEntry) => void;
    onDeleteDraft: (draftId: number) => void;
}

/** Vollbild-Ansicht eines Verlaufs im E-Mail-Reiter mit Antwort-Formular. */
export function EmailsTabThreadDialog(props: EmailsTabThreadDialogProps) {
    const { subject, thread, showReplyButton, reply, onClose } = props;
    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
            <div className="bg-white rounded-2xl shadow-2xl w-[75vw] max-w-5xl h-[90vh] flex flex-col overflow-hidden">
                {/* Header */}
                <div className="flex items-center justify-between px-5 py-3 border-b border-slate-200 bg-slate-50 shrink-0">
                    <div className="flex items-center gap-3">
                        <button onClick={onClose} className="p-1.5 rounded-lg hover:bg-slate-200 transition-colors">
                            <ArrowLeft className="w-5 h-5 text-slate-600" />
                        </button>
                        <div>
                            <h3 className="font-semibold text-slate-900 text-sm truncate max-w-md">
                                {subject}
                            </h3>
                            {thread && (
                                <p className="text-xs text-slate-500">
                                    {thread.emails.length} {thread.emails.length === 1 ? 'Nachricht' : 'Nachrichten'} im Thread
                                </p>
                            )}
                        </div>
                    </div>
                    <div className="flex items-center gap-2">
                        {showReplyButton && !reply.isReplying && thread && (
                            <Button
                                variant="outline"
                                size="sm"
                                onClick={() => {
                                    if (thread.emails.length > 0) {
                                        reply.startReply(thread.emails[thread.emails.length - 1]);
                                    }
                                }}
                                className="gap-1.5 text-slate-700 border-slate-300 hover:bg-rose-50 hover:text-rose-700 hover:border-rose-300"
                            >
                                <Reply className="w-4 h-4" /> Antworten
                            </Button>
                        )}
                        <button onClick={onClose} className="p-1.5 rounded-lg hover:bg-slate-200 transition-colors">
                            <X className="w-5 h-5 text-slate-500" />
                        </button>
                    </div>
                </div>

                {/* Content */}
                <EmailsTabThreadContent {...props} />
            </div>
        </div>
    );
}

function EmailsTabThreadContent({
    thread, threadLoading, showReplyButton, reply, projektId, anfrageId,
    onReplySuccess, onPreview, onOpenDraft, onDeleteDraft,
}: EmailsTabThreadDialogProps) {
    if (reply.isReplying && reply.replyToEmail) {
        return (
            <div className="flex-1 overflow-hidden flex flex-col">
                <EmailComposeForm
                    onClose={reply.cancelReply}
                    onSuccess={onReplySuccess}
                    initialRecipient={reply.replyInitialRecipient}
                    initialSubject={reply.replyInitialSubject}
                    replyQuote={reply.replyQuote}
                    replyEmailId={reply.replyToEmailId}
                    projektId={projektId}
                    anfrageId={anfrageId}
                    variant="modal"
                />
            </div>
        );
    }
    if (threadLoading) return <EmailsTabThreadSkeleton />;
    if (thread) {
        return (
            <EmailThreadView
                thread={thread}
                onPreview={onPreview}
                onReply={showReplyButton ? (entry) => reply.startReply(entry) : undefined}
                onOpenDraft={onOpenDraft}
                onDeleteDraft={onDeleteDraft}
            />
        );
    }
    return (
        <div className="flex-1 flex items-center justify-center p-6">
            <p className="text-sm text-slate-400 italic">Thread konnte nicht geladen werden.</p>
        </div>
    );
}

function EmailsTabThreadSkeleton() {
    return (
        <div className="flex-1 overflow-auto bg-slate-50 px-6 py-6 space-y-2">
            {[false, true, false].map((right, i) => (
                <div key={i} className={`flex items-end gap-2 mb-2 ${right ? 'flex-row-reverse' : ''}`}>
                    <div className="w-8 h-8 rounded-full bg-slate-200 animate-pulse shrink-0" />
                    <div className={`animate-pulse bg-slate-200 rounded-2xl h-16 ${right ? 'rounded-br-sm w-56' : 'rounded-bl-sm w-64'}`} />
                </div>
            ))}
        </div>
    );
}
