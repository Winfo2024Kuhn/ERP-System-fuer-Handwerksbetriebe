import React, { useMemo, useState } from 'react';
import {
    Dialog,
    DialogContent,
    DialogHeader,
    DialogTitle,
} from "../components/ui/dialog";
import { Button } from "../components/ui/button";
import { User, Clock, Paperclip, Reply } from "lucide-react";
import { EmailAttachmentCard } from "./EmailAttachmentCard";
import { EmailReplyModal } from "./EmailReplyModal";
import { EmailContentFrame } from "./EmailContentFrame";
import type { ProjektEmail, ProjektEmailAttachment } from '../types';

interface EmailDetailModalProps {
    isOpen: boolean;
    onClose: () => void;
    email: ProjektEmail | null;
    projektId: number;
    projektName?: string;
    kundenEmail?: string;
    onEmailSent?: () => void;
}

export const EmailDetailModal: React.FC<EmailDetailModalProps> = ({
    isOpen,
    onClose,
    email,
    projektId,
    projektName,
    kundenEmail,
    onEmailSent
}) => {
    const [showReplyModal, setShowReplyModal] = useState(false);
    
    // Helper to process HTML and replace CID images
    const processedHtml = useMemo(() => {
        if (!email) return '';

        let html = email.bodyHtml || email.bodyPreview || '<p class="text-slate-400 italic">Kein Inhalt</p>';
        
        if (email.attachments) {
            email.attachments.forEach((att: ProjektEmailAttachment) => {
                if (att.contentId) {
                    // Clean contentId (in case it still has brackets, though backend usually strips them)
                    const cleanCid = att.contentId.replace(/[<>]/g, '');
                    
                    // Construct URL
                    const url = `/api/emails/${email.id}/attachments/${att.id}`;
                    
                    // Regex to match src="cid:..." with or without brackets, and with single or double quotes
                    // Matches: src="cid:CLEAN_CID" OR src="cid:<CLEAN_CID>"
                    // We use a broader regex to capture the `cid:` part and any potential brackets
                    
                    // 1. Replace cases where CID is "cid:cleanCid"
                    let regex = new RegExp(`src=["']cid:${cleanCid}["']`, 'gi');
                    html = html.replace(regex, `src="${url}"`);

                    // 2. Replace cases where CID is "cid:<cleanCid>" (common in some clients)
                    regex = new RegExp(`src=["']cid:<${cleanCid}>["']`, 'gi');
                    html = html.replace(regex, `src="${url}"`);
                    
                     // 3. Fallback: try raw contentId if it differs
                    if (att.contentId !== cleanCid) {
                         html = html.replace(new RegExp(`src=["']cid:${att.contentId}["']`, 'gi'), `src="${url}"`);
                    }
                }
            });
        }
        return html;
    }, [email]);

    // Filter out attachments that are likely inline (have contentId)
    const visibleAttachments = useMemo(() => {
        if (!email || !email.attachments) return [];
        return email.attachments.filter(att => !att.inline);
    }, [email]);

    // Helper to format date
    const formatDate = (dateStr?: string) => {
        if (!dateStr) return '-';
        try {
            return new Date(dateStr).toLocaleString('de-DE', {
                day: '2-digit', month: '2-digit', year: 'numeric',
                hour: '2-digit', minute: '2-digit'
            });
        } catch {
            return dateStr;
        }
    };

    // Handle reply button click
    const handleReplyClick = () => {
        setShowReplyModal(true);
    };

    // Handle reply modal close
    const handleReplyClose = () => {
        setShowReplyModal(false);
        if (onEmailSent) {
            onEmailSent();
        }
    };

    if (!email) return null;

    return (
        <>
            <Dialog open={isOpen && !showReplyModal} onOpenChange={(open) => !open && onClose()}>
            <DialogContent className="w-[70vw] max-w-[70vw] h-[90vh] flex flex-col p-0 gap-0">
                    <DialogHeader className="p-6 pb-2 border-b border-slate-100 flex-shrink-0">
                        <div className="flex items-start justify-between">
                            <div>
                                <div className="flex items-center gap-2 mb-2">
                                    <span className={`px-2 py-0.5 rounded text-xs font-medium uppercase tracking-wide border ${
                                        email.direction === 'IN' 
                                            ? 'bg-blue-50 text-blue-700 border-blue-100' 
                                            : 'bg-emerald-50 text-emerald-700 border-emerald-100'
                                    }`}>
                                        {email.direction === 'IN' ? 'Eingang' : 'Ausgang'}
                                    </span>
                                    <span className="text-slate-500 text-sm flex items-center gap-1">
                                        <Clock className="w-3.5 h-3.5" />
                                        {formatDate(email.sentAt || email.sentDate || email.receivedDate)}
                                    </span>
                                </div>
                                <DialogTitle className="text-xl font-bold text-slate-900 mb-1">
                                    {email.subject || '(Kein Betreff)'}
                                </DialogTitle>
                                <p className="text-sm text-slate-600 flex items-center gap-2">
                                    <User className="w-4 h-4" />
                                    <span>{email.from || email.fromAddress || 'Unbekannt'}</span>
                                    {email.to && <span className="text-slate-400">an {email.to}</span>}
                                </p>
                            </div>
                        </div>
                    </DialogHeader>

                    <div className="flex-1 overflow-y-auto p-6 bg-slate-50">
                        <div className="bg-white rounded-lg border border-slate-200 shadow-sm p-8 min-h-[300px]">
                            <EmailContentFrame 
                                html={processedHtml} 
                                className="min-h-[300px]"
                            />
                        </div>

                        {visibleAttachments.length > 0 && (
                            <div className="mt-6">
                                <h3 className="text-sm font-semibold text-slate-900 mb-3 flex items-center gap-2">
                                    <Paperclip className="w-4 h-4" /> Anhänge ({visibleAttachments.length})
                                </h3>
                                <div className="grid grid-cols-2 sm:grid-cols-4 md:grid-cols-6 gap-3">
                                    {visibleAttachments.map((att, idx) => (
                                        <EmailAttachmentCard
                                            key={att.storedFilename || idx}
                                            attachment={att}
                                            emailId={email.id}
                                        />
                                    ))}
                                </div>
                            </div>
                        )}
                    </div>

                    <div className="p-4 border-t border-slate-100 bg-white flex justify-between gap-2 flex-shrink-0">
                        <Button 
                            onClick={handleReplyClick}
                            className="bg-rose-600 text-white hover:bg-rose-700"
                        >
                            <Reply className="w-4 h-4 mr-2" />
                            Antworten
                        </Button>
                        <Button variant="outline" onClick={onClose}>
                            Schließen
                        </Button>
                    </div>
                </DialogContent>
            </Dialog>

            {/* Reply Modal */}
            {showReplyModal && email && (
                <EmailReplyModal
                    isOpen={showReplyModal}
                    onClose={handleReplyClose}
                    context={{ type: 'projekt', id: projektId }}
                    email={email}
                    projektName={projektName}
                    kundenEmail={kundenEmail}
                />
            )}
        </>
    );
};
