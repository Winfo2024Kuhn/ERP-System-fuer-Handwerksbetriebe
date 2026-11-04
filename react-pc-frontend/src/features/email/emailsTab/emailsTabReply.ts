import { escapeHtml, extractDisplayName, extractEmailAddress } from '../../../lib/emailAddress';
import { buildReplyAddressing } from '../emailCenterModel';
import type { GenericEmail } from './emailsTabTypes';

function formatQuoteDate(sentAt?: string): string {
    if (!sentAt) return '';
    return new Date(sentAt).toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric' })
        + ', ' + new Date(sentAt).toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit' }) + ' Uhr';
}

/**
 * Zitat der Mail, auf die geantwortet wird („Am … schrieb …:“ plus Text).
 * script, style und link werden über einen inerten DOMParser entfernt; angezeigt wird
 * das Zitat erst im Mail-Editor.
 */
export function buildReplyQuote(email: GenericEmail | null): string | undefined {
    if (!email) return undefined;
    const senderName = extractDisplayName(email.fromAddress || email.from || email.sender);
    const date = formatQuoteDate(email.sentAt);
    let cleanBody = email.bodyHtml || email.body || '';
    try {
        const parser = new DOMParser();
        const doc = parser.parseFromString(cleanBody, 'text/html');
        doc.querySelectorAll('script, style, link').forEach(el => el.remove());
        cleanBody = doc.body.innerHTML;
    } catch { /* ignore */ }
    return `<div class="email-quote" style="border-left:3px solid #e2e8f0;padding-left:1rem;color:#64748b;margin-top:0.5rem">
            <p style="font-size:0.8125rem;color:#94a3b8;margin-bottom:0.5rem">Am ${escapeHtml(date)} schrieb ${escapeHtml(senderName)}:</p>
            ${cleanBody}
        </div>`;
}

/** Empfänger der Antwort; auf eine eigene gesendete Mail geht sie an deren Empfänger. */
export function buildReplyRecipient(email: GenericEmail | null): string {
    if (!email) return '';
    const fromAddress = email.fromAddress || email.from || email.sender || '';
    // Auf eine eigene gesendete Mail antworten heißt: an deren Empfänger, nie an uns selbst.
    const ownSender = email.direction === 'OUT' ? extractEmailAddress(fromAddress).toLowerCase() : '';
    return buildReplyAddressing({
        direction: email.direction ?? 'IN',
        fromAddress,
        replyToAddress: email.replyToAddress,
        recipient: email.recipient || email.to || email.recipients?.join(', '),
        cc: email.cc,
    }, 'reply', address => !!ownSender && address === ownSender).to;
}

/** Betreff der Antwort: „AW: “ davor, außer er beginnt schon mit AW: oder RE:. */
export function buildReplySubject(email: GenericEmail | null): string {
    if (!email?.subject) return 'AW: ';
    return /^(\s*)(aw|re):\s*/i.test(email.subject) ? email.subject : `AW: ${email.subject}`;
}
