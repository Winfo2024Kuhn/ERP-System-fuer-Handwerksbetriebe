import React, { useState, useMemo } from 'react';
import { Mail, Search, ChevronRight, Paperclip, Plus } from 'lucide-react';
import { Button } from './ui/button';
import { cn } from '../lib/utils';
import { EmailDetailModal } from './EmailDetailModal';
import { EmailComposeModal } from './EmailComposeModal';
import type { ProjektEmail } from '../types';

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
}

export const EmailsTab: React.FC<EmailsTabProps> = ({
    emails,
    projektId,
    anfrageId,
    angebotId,
    kundeId,
    lieferantId,
    entityName,
    kundenEmail,
    projekt,
    anfrage,
    angebot,
    onEmailSent,
    showComposeButton = true,
}) => {
    // State
    const [emailFilter, setEmailFilter] = useState<'all' | 'in' | 'out'>('all');
    const [emailSearch, setEmailSearch] = useState('');
    const [selectedEmail, setSelectedEmail] = useState<ProjektEmail | null>(null);
    const [showComposeModal, setShowComposeModal] = useState(false);

    // Determine the entity ID for the detail modal
    const entityId = projektId || anfrageId || angebotId || kundeId || lieferantId || 0;

    // Filter emails
    const filteredEmails = useMemo(() => {
        if (!emails) return [];
        return emails.filter(email => {
            // Direction filter
            if (emailFilter === 'in' && email.direction !== 'IN') return false;
            if (emailFilter === 'out' && email.direction !== 'OUT') return false;

            // Search filter
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
    }, [emails, emailFilter, emailSearch]);

    // Handle email click - convert to ProjektEmail format for modal
    const handleEmailClick = (email: GenericEmail) => {
        const projektEmail: ProjektEmail = {
            id: email.id,
            subject: email.subject,
            from: email.from || email.fromAddress || email.sender,
            fromAddress: email.fromAddress || email.from || email.sender,
            to: email.to || email.recipients?.join(', '),
            bodyHtml: email.bodyHtml || email.body,
            bodyPreview: email.bodyPreview,
            direction: email.direction || 'IN', // Default to IN if undefined
            sentAt: email.sentAt,
            sentDate: email.sentDate,
            receivedDate: email.receivedDate,
            attachments: email.attachments?.map(att => ({
                id: att.id || 0,
                originalFilename: att.originalFilename || att.filename || '',
                storedFilename: att.storedFilename || '',
                contentId: att.contentId,
                inline: att.inline,
                url: att.url,
            })),
        };
        setSelectedEmail(projektEmail);
    };

    // Handle compose modal close with optional refresh
    const handleComposeClose = () => {
        setShowComposeModal(false);
        if (onEmailSent) {
            onEmailSent();
        }
    };

    // Handle detail modal close
    const handleDetailClose = () => {
        setSelectedEmail(null);
    };

    // Handle email sent from detail modal
    const handleEmailSentFromDetail = () => {
        setSelectedEmail(null);
        if (onEmailSent) {
            onEmailSent();
        }
    };

    // Format date helper
    const formatDate = (email: GenericEmail) => {
        const dateStr = email.sentAt || email.receivedDate || email.sentDate;
        if (!dateStr) return '-';
        try {
            return new Date(dateStr).toLocaleDateString('de-DE', {
                day: '2-digit',
                month: '2-digit',
                year: 'numeric',
                hour: '2-digit',
                minute: '2-digit'
            });
        } catch {
            return dateStr;
        }
    };

    // Extract text from HTML for preview
    const getTextPreview = (email: GenericEmail) => {
        const html = email.bodyHtml || email.bodyPreview || email.body || '';
        const div = document.createElement('div');
        div.innerHTML = html;
        return div.textContent || div.innerText || '';
    };

    return (
        <div className="space-y-4">
            {/* Header with filters and compose button */}
            <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4 mb-4">
                <h3 className="text-lg font-medium text-slate-900">E-Mails</h3>
                <div className="flex flex-wrap items-center gap-3">
                    {/* Search */}
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

                    {/* Direction filter */}
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

                    {/* Compose button */}
                    {showComposeButton && (projektId || anfrageId || angebotId) && (
                        <Button
                            onClick={() => setShowComposeModal(true)}
                            className="bg-rose-600 text-white hover:bg-rose-700"
                        >
                            <Plus className="w-4 h-4 mr-2" />
                            Neue E-Mail
                        </Button>
                    )}
                </div>
            </div>

            {/* Email list */}
            {filteredEmails.length > 0 ? (
                <div className="space-y-3">
                    {filteredEmails.map((email) => (
                        <div
                            key={email.id}
                            className="bg-white rounded-xl border border-slate-200 hover:border-rose-300 hover:shadow-md transition-all cursor-pointer group"
                            onClick={() => handleEmailClick(email)}
                        >
                            <div className="p-4">
                                <div className="flex items-start justify-between gap-4">
                                    <div className="flex items-start gap-3 flex-1 min-w-0">
                                        <div className="flex-1 min-w-0">
                                            <div className="flex items-center gap-2 mb-1">
                                                <span className={cn(
                                                    "text-xs font-medium px-2 py-0.5 rounded-full",
                                                    email.direction === 'IN'
                                                        ? "bg-blue-50 text-blue-700"
                                                        : "bg-green-50 text-green-700"
                                                )}>
                                                    {email.direction === 'IN' ? 'Eingang' : 'Ausgang'}
                                                </span>
                                                <span className="text-xs text-slate-500">
                                                    {formatDate(email)}
                                                </span>
                                            </div>
                                            <h4 className="font-medium text-slate-900 truncate">
                                                {email.subject || '(kein Betreff)'}
                                            </h4>
                                            <p className="text-sm text-slate-500 truncate">
                                                {email.direction === 'IN' ? 'Von: ' : 'An: '}
                                                {email.from || email.fromAddress || email.sender || 'Unbekannt'}
                                            </p>
                                            {(email.bodyHtml || email.bodyPreview || email.body) && (
                                                <p className="text-sm text-slate-400 mt-1 line-clamp-2">
                                                    {getTextPreview(email)}
                                                </p>
                                            )}
                                        </div>
                                    </div>
                                    <div className="flex flex-col items-end gap-2">
                                        <ChevronRight className="w-5 h-5 text-slate-300 group-hover:text-rose-400 transition-colors" />
                                        {email.attachments && email.attachments.filter(a => !a.inline).length > 0 && (
                                            <Paperclip className="w-4 h-4 text-slate-400" />
                                        )}
                                    </div>
                                </div>
                            </div>
                        </div>
                    ))}
                </div>
            ) : (
                <div className="flex flex-col items-center justify-center py-12 text-slate-500">
                    <Mail className="w-12 h-12 text-slate-300 mb-3" />
                    <p>{emailSearch || emailFilter !== 'all' ? 'Keine E-Mails gefunden.' : 'Keine E-Mails vorhanden.'}</p>
                </div>
            )}

            {/* Email Detail Modal */}
            {selectedEmail && (
                <EmailDetailModal
                    isOpen={!!selectedEmail}
                    onClose={handleDetailClose}
                    email={selectedEmail}
                    projektId={entityId}
                    projektName={entityName}
                    kundenEmail={kundenEmail}
                    onEmailSent={handleEmailSentFromDetail}
                />
            )}

            {/* Compose Modal - only for Projekt or Anfrage */}
            {showComposeModal && (
                <EmailComposeModal
                    isOpen={showComposeModal}
                    onClose={handleComposeClose}
                    projektId={projektId}
                    projekt={projekt as unknown as import('../types').ProjektDetail}
                    anfrageId={anfrageId ?? angebotId}
                    anfrage={anfrage ?? angebot}
                />
            )}
        </div>
    );
};

export default EmailsTab;
