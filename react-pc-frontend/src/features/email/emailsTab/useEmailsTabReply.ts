import { useCallback, useMemo, useRef, useState } from 'react';
import type { EmailThreadEntry } from '../../../components/EmailThreadView';
import { buildReplyQuote, buildReplyRecipient, buildReplySubject } from './emailsTabReply';
import type { GenericEmail } from './emailsTabTypes';

/** Ersatz aus den Daten des Verlaufs, wenn die vollständige Mail nicht kommt. */
function ausVerlauf(entry: EmailThreadEntry): GenericEmail {
    return {
        id: entry.id, subject: entry.subject, fromAddress: entry.fromAddress,
        to: entry.recipient, recipient: entry.recipient, cc: entry.cc, replyToAddress: entry.replyToAddress,
        bodyHtml: entry.htmlBody, body: entry.snippet,
        direction: entry.direction, sentAt: entry.sentAt,
    };
}

function holeMail(id: number): Promise<GenericEmail | null> {
    return fetch(`/api/emails/${id}`)
        .then(res => (res.ok ? res.json() as Promise<GenericEmail> : null))
        .catch(() => null);
}

/**
 * Antwort aus einem geöffneten Verlauf im E-Mail-Reiter: welche Mail, Zitat, Empfänger, Betreff.
 * Bei mehreren Klicks gewinnt der letzte; nach Abbruch wird eine späte Antwort verworfen.
 */
export function useEmailsTabReply() {
    const [isReplying, setIsReplying] = useState(false);
    const [replyLaedt, setReplyLaedt] = useState(false);
    const [replyToEmail, setReplyToEmail] = useState<GenericEmail | null>(null);
    const [replyToEmailId, setReplyToEmailId] = useState<number | undefined>(undefined);
    const wahlNr = useRef(0);

    /** Holt die vollständige Mail; klappt das nicht, reichen die Daten aus dem Verlauf. */
    const startReply = useCallback((entry: EmailThreadEntry) => {
        const nr = ++wahlNr.current;
        setReplyLaedt(true);
        return holeMail(entry.id).then(fullEmail => {
            if (nr !== wahlNr.current) return;
            setReplyToEmail(fullEmail ?? ausVerlauf(entry));
            setReplyToEmailId(entry.id);
            setIsReplying(true);
            setReplyLaedt(false);
        });
    }, []);

    const cancelReply = useCallback(() => {
        wahlNr.current++;
        setReplyLaedt(false);
        setIsReplying(false);
        setReplyToEmail(null);
        setReplyToEmailId(undefined);
    }, []);

    const replyQuote = useMemo(() => buildReplyQuote(replyToEmail), [replyToEmail]);
    const replyInitialRecipient = useMemo(() => buildReplyRecipient(replyToEmail), [replyToEmail]);
    const replyInitialSubject = useMemo(() => buildReplySubject(replyToEmail), [replyToEmail]);

    return {
        isReplying, replyLaedt, replyToEmail, replyToEmailId,
        startReply, cancelReply,
        replyQuote, replyInitialRecipient, replyInitialSubject,
    };
}

export type EmailsTabReply = ReturnType<typeof useEmailsTabReply>;
