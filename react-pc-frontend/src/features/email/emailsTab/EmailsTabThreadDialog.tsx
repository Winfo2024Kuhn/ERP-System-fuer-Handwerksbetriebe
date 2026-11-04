import { useCallback, useId, useRef, useState } from 'react';
import { Loader2, Reply } from 'lucide-react';
import { Button } from '../../../components/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogTitle } from '../../../components/ui/dialog';
import { ImageViewer } from '../../../components/ui/image-viewer';
import DocumentPreviewModal from '../../../components/DocumentPreviewModal';
import { EmailComposeForm } from '../../../components/EmailComposeForm';
import { EmailThreadView } from '../../../components/EmailThreadView';
import type { EmailThread, EmailThreadEntry } from '../../../components/EmailThreadView';
import { toSafeResourceUrl } from '../../../lib/htmlSanitizer';
import { verlaufAnzahl } from './emailsTabFormat';
import { useEmailsTabEntwurfLoeschen } from './useEmailsTabEntwurfLoeschen';
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
    onReloadThread: () => void;
    onOpenDraft: (entry: EmailThreadEntry) => void;
}

interface Anhang { url: string; type: 'image' | 'pdf'; name: string }

/**
 * Verlauf im E-Mail-Reiter als gemeinsamer Dialog (Fokusfalle, Escape, Titel = Betreff)
 * mit Antwort-Formular und Anhang-Vorschau.
 *
 * Die Vorschau hängt im Dialog, damit Fokusfalle und Escape sie einschließen: Escape
 * schließt dann nur die Vorschau. Beim Schließen während einer Antwort wird der Entwurf
 * erst gespeichert; scheitert das, bleibt der Verlauf offen.
 */
export function EmailsTabThreadDialog(props: EmailsTabThreadDialogProps) {
    const { subject, thread, showReplyButton, reply, onClose, onReloadThread } = props;
    const titelId = useId();
    const [anhang, setAnhang] = useState<Anhang | null>(null);
    const speichernVorDemSchliessen = useRef<(() => Promise<boolean>) | null>(null);
    const merkeSpeichern = useCallback((speichern: (() => Promise<boolean>) | null) => {
        speichernVorDemSchliessen.current = speichern;
    }, []);
    const { sichtbarerVerlauf, entwurfLoeschen } = useEmailsTabEntwurfLoeschen(thread, onReloadThread);

    const schliessenWollen = () => {
        if (anhang) { setAnhang(null); return; }
        const speichern = reply.isReplying ? speichernVorDemSchliessen.current : null;
        if (!speichern) { onClose(); return; }
        void speichern().then(gespeichert => { if (gespeichert) onClose(); });
    };

    return (
        <Dialog
            open
            onOpenChange={offen => { if (!offen) schliessenWollen(); }}
            aria-labelledby={titelId}
            aria-describedby={sichtbarerVerlauf ? `${titelId}-anzahl` : undefined}
            className="p-0 overflow-hidden w-[75vw] max-w-5xl h-[90vh]"
        >
            <DialogContent className="gap-0">
                {/* Rechts Platz für den Schließen-Knopf des Dialogs */}
                <div className="flex items-center justify-between gap-3 pl-5 pr-14 py-3 border-b border-slate-200 bg-slate-50 shrink-0">
                    <div className="min-w-0">
                        <DialogTitle id={titelId} className="text-sm leading-normal text-slate-900 truncate">{subject}</DialogTitle>
                        {sichtbarerVerlauf && (
                            <DialogDescription id={`${titelId}-anzahl`} className="text-xs">
                                {verlaufAnzahl(sichtbarerVerlauf.emails)}
                            </DialogDescription>
                        )}
                    </div>
                    {showReplyButton && !reply.isReplying && sichtbarerVerlauf && sichtbarerVerlauf.emails.length > 0 && (
                        <Button
                            variant="outline"
                            size="sm"
                            disabled={reply.replyLaedt}
                            onClick={() => { void reply.startReply(sichtbarerVerlauf.emails[sichtbarerVerlauf.emails.length - 1]); }}
                            className="gap-1.5 shrink-0 text-slate-700 border-slate-300 hover:bg-rose-50 hover:text-rose-700 hover:border-rose-300"
                        >
                            {reply.replyLaedt
                                ? <Loader2 aria-hidden="true" className="w-4 h-4 motion-safe:animate-spin" />
                                : <Reply aria-hidden="true" className="w-4 h-4" />}
                            Antworten
                        </Button>
                    )}
                </div>

                <EmailsTabThreadContent
                    {...props}
                    thread={sichtbarerVerlauf}
                    onBeforeLeave={merkeSpeichern}
                    onPreview={(url, type, name) => setAnhang({ url, type, name })}
                    onDeleteDraft={entwurfLoeschen}
                />
                <EmailsTabAnhangVorschau anhang={anhang} onClose={() => setAnhang(null)} />
            </DialogContent>
        </Dialog>
    );
}

interface ContentProps extends EmailsTabThreadDialogProps {
    onBeforeLeave: (speichern: (() => Promise<boolean>) | null) => void;
    onPreview: (url: string, type: 'image' | 'pdf', name: string) => void;
    onDeleteDraft: (draftId: number) => void;
}

function EmailsTabThreadContent({
    thread, threadLoading, showReplyButton, reply, projektId, anfrageId,
    onReplySuccess, onBeforeLeave, onPreview, onOpenDraft, onDeleteDraft,
}: ContentProps) {
    if (reply.isReplying && reply.replyToEmail) {
        return (
            <div className="flex-1 min-h-0 overflow-hidden flex flex-col">
                <EmailComposeForm
                    onClose={reply.cancelReply}
                    onBeforeLeave={onBeforeLeave}
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
    if (threadLoading && !thread) return <EmailsTabThreadSkeleton />;
    if (thread) {
        return (
            <EmailThreadView
                thread={thread}
                onPreview={onPreview}
                onReply={showReplyButton ? (entry) => { void reply.startReply(entry); } : undefined}
                onOpenDraft={onOpenDraft}
                onDeleteDraft={onDeleteDraft}
            />
        );
    }
    return (
        <div className="flex-1 flex items-center justify-center p-6">
            <p className="text-sm text-slate-500">Der Verlauf konnte nicht geladen werden.</p>
        </div>
    );
}

/** Bilder im gemeinsamen ImageViewer, PDFs in der gemeinsamen Dokumentvorschau. */
function EmailsTabAnhangVorschau({ anhang, onClose }: { anhang: Anhang | null; onClose: () => void }) {
    const sichereUrl = anhang ? toSafeResourceUrl(anhang.url) : '';
    if (!anhang || !sichereUrl) return null;
    if (anhang.type === 'image') return <ImageViewer src={sichereUrl} alt={anhang.name} onClose={onClose} />;
    return <DocumentPreviewModal doc={{ url: sichereUrl, title: anhang.name }} isPdf onClose={onClose} />;
}

function EmailsTabThreadSkeleton() {
    return (
        <div aria-hidden="true" className="flex-1 overflow-auto bg-slate-50 px-6 py-6 space-y-2">
            {[false, true, false].map((right, i) => (
                <div key={i} className={`flex items-end gap-2 mb-2 ${right ? 'flex-row-reverse' : ''}`}>
                    <div className="w-8 h-8 rounded-full bg-slate-200 motion-safe:animate-pulse shrink-0" />
                    <div className={`motion-safe:animate-pulse bg-slate-200 rounded-2xl h-16 ${right ? 'rounded-br-sm w-56' : 'rounded-bl-sm w-64'}`} />
                </div>
            ))}
        </div>
    );
}
