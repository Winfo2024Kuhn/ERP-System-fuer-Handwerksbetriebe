import React, { useState, useMemo, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { Mail } from 'lucide-react';
import { getThreadPreview } from '../features/email/threadQuotes';
import { EmailComposeModal } from './EmailComposeModal';
import type { EmailThreadEntry } from './EmailThreadView';
import type { ProjektDetail } from '../types';
import { buildThreadRoots, filterThreadRoots } from '../features/email/emailsTab/emailsTabThreadRoots';
import { useEmailsTabThread } from '../features/email/emailsTab/useEmailsTabThread';
import { useEmailsTabReply } from '../features/email/emailsTab/useEmailsTabReply';
import { EmailsTabToolbar } from '../features/email/emailsTab/EmailsTabToolbar';
import { EmailsTabListItem } from '../features/email/emailsTab/EmailsTabListItem';
import { EmailsTabThreadDialog } from '../features/email/emailsTab/EmailsTabThreadDialog';
import type { EmailsTabFilter, GenericEmail } from '../features/email/emailsTab/emailsTabTypes';

export type { GenericEmail };

export interface EmailsTabProps {
    // Required
    emails: GenericEmail[];

    // Kontext für „Neue E-Mail“ und Antworten (Projekt oder Anfrage)
    projektId?: number;
    anfrageId?: number;

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

    // Callbacks
    onEmailSent?: () => void;

    // Optional: Show/hide compose button (defaults to true)
    showComposeButton?: boolean;

    // Optional: Show/hide reply button in thread view (defaults to true)
    showReplyButton?: boolean;
}

/**
 * E-Mail-Reiter in Kunde, Projekt, Anfrage und Lieferant: Mailliste nach Verläufen,
 * Suche und Filter, Verlauf im Dialog mit Antwort, Anhang-Vorschau und Entwurf löschen
 * (mit „Rückgängig“), neue E-Mail.
 * Bausteine und Logik liegen unter `features/email/emailsTab/`.
 */
export const EmailsTab: React.FC<EmailsTabProps> = ({
    emails,
    projektId,
    anfrageId,
    projekt,
    anfrage,
    onEmailSent,
    showComposeButton = true,
    showReplyButton = true,
}) => {
    const navigate = useNavigate();

    const [emailFilter, setEmailFilter] = useState<EmailsTabFilter>('all');
    const [emailSearch, setEmailSearch] = useState('');
    const [showComposeModal, setShowComposeModal] = useState(false);
    const [selectedEmailId, setSelectedEmailId] = useState<number | null>(null);

    const { thread, threadLoading, reloadThread } = useEmailsTabThread(selectedEmailId);
    const reply = useEmailsTabReply();
    const { cancelReply } = reply;

    // Nur Wurzeln der Verläufe anzeigen
    const threadRoots = useMemo(() => buildThreadRoots(emails), [emails]);
    const filteredEmails = useMemo(
        () => filterThreadRoots(threadRoots, emailFilter, emailSearch),
        [threadRoots, emailFilter, emailSearch]);

    /**
     * Vorschauzeile ohne zitierten Verlauf, dieselbe Erkennung wie im E-Mail-Center.
     * Einmal je Mailliste berechnet (nicht je Rendern und nicht je Tastendruck in der Suche).
     */
    const textPreviews = useMemo(() => new Map(threadRoots.map(email =>
        [email.id, getThreadPreview(email.bodyHtml || email.bodyPreview || email.body || '')])), [threadRoots]);

    const handleEmailClick = (email: GenericEmail) => {
        setSelectedEmailId(email.id);
        cancelReply();
    };

    const closeThreadView = () => {
        setSelectedEmailId(null);
        cancelReply();
    };

    const handleReplySuccess = useCallback(() => {
        cancelReply();
        reloadThread();
        if (onEmailSent) onEmailSent();
    }, [cancelReply, reloadThread, onEmailSent]);

    const handleOpenDraft = (entry: EmailThreadEntry) => {
        if (entry.draftId) {
            navigate(`/emails/drafts?draft=${encodeURIComponent(entry.draftId)}`);
        }
    };

    const handleComposeSent = () => {
        setShowComposeModal(false);
        if (onEmailSent) onEmailSent();
    };

    return (
        <div className="space-y-4">
            {selectedEmailId && (
                <EmailsTabThreadDialog
                    subject={threadRoots.find(e => e.id === selectedEmailId)?.subject || '(kein Betreff)'}
                    thread={thread}
                    threadLoading={threadLoading}
                    showReplyButton={showReplyButton}
                    reply={reply}
                    projektId={projektId}
                    anfrageId={anfrageId}
                    onClose={closeThreadView}
                    onReplySuccess={handleReplySuccess}
                    onReloadThread={reloadThread}
                    onOpenDraft={handleOpenDraft}
                />
            )}

            <EmailsTabToolbar
                search={emailSearch}
                onSearchChange={setEmailSearch}
                filter={emailFilter}
                onFilterChange={setEmailFilter}
                showCompose={showComposeButton && !!(projektId || anfrageId)}
                onCompose={() => setShowComposeModal(true)}
            />

            {filteredEmails.length > 0 ? (
                <div className="space-y-3">
                    {filteredEmails.map(email => (
                        <EmailsTabListItem
                            key={email.id}
                            email={email}
                            preview={textPreviews.get(email.id)}
                            onOpen={handleEmailClick}
                        />
                    ))}
                </div>
            ) : (
                <div className="flex flex-col items-center justify-center py-12 text-slate-500">
                    <Mail className="w-12 h-12 text-slate-300 mb-3" />
                    <p>{emailSearch || emailFilter !== 'all' ? 'Keine E-Mails gefunden.' : 'Keine E-Mails vorhanden.'}</p>
                </div>
            )}

            {showComposeModal && (
                <EmailComposeModal
                    isOpen={showComposeModal}
                    onClose={() => setShowComposeModal(false)}
                    projektId={projektId}
                    projekt={projekt as unknown as ProjektDetail}
                    anfrageId={anfrageId}
                    anfrage={anfrage}
                    onSuccess={handleComposeSent}
                />
            )}
        </div>
    );
};

export default EmailsTab;
