import { useCallback, useMemo, useState } from 'react';
import type { EmailThreadEntry } from '../../../components/EmailThreadView';
import { buildReplyQuote, buildReplyRecipient, buildReplySubject } from './emailsTabReply';
import type { GenericEmail } from './emailsTabTypes';

/** Antwort aus einem geöffneten Verlauf im E-Mail-Reiter: welche Mail, Zitat, Empfänger, Betreff. */
export function useEmailsTabReply() {
    const [isReplying, setIsReplying] = useState(false);
    const [replyToEmail, setReplyToEmail] = useState<GenericEmail | null>(null);
    const [replyToEmailId, setReplyToEmailId] = useState<number | undefined>(undefined);

    /** Holt die vollständige Mail; klappt das nicht, reichen die Daten aus dem Verlauf. */
    const startReply = useCallback(async (entry: EmailThreadEntry) => {
        let fullEmail: GenericEmail | null = null;
        try {
            const res = await fetch(`/api/emails/${entry.id}`);
            if (res.ok) fullEmail = await res.json();
        } catch { /* fallback */ }
        if (!fullEmail) {
            fullEmail = {
                id: entry.id, subject: entry.subject, fromAddress: entry.fromAddress,
                to: entry.recipient, recipient: entry.recipient, cc: entry.cc, replyToAddress: entry.replyToAddress,
                bodyHtml: entry.htmlBody, body: entry.snippet,
                direction: entry.direction, sentAt: entry.sentAt,
            };
        }
        setReplyToEmail(fullEmail);
        setReplyToEmailId(entry.id);
        setIsReplying(true);
    }, []);

    const cancelReply = useCallback(() => {
        setIsReplying(false);
        setReplyToEmail(null);
        setReplyToEmailId(undefined);
    }, []);

    const replyQuote = useMemo(() => buildReplyQuote(replyToEmail), [replyToEmail]);
    const replyInitialRecipient = useMemo(() => buildReplyRecipient(replyToEmail), [replyToEmail]);
    const replyInitialSubject = useMemo(() => buildReplySubject(replyToEmail), [replyToEmail]);

    return {
        isReplying, replyToEmail, replyToEmailId,
        startReply, cancelReply,
        replyQuote, replyInitialRecipient, replyInitialSubject,
    };
}

export type EmailsTabReply = ReturnType<typeof useEmailsTabReply>;
