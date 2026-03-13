import { useState, useEffect, useCallback, useRef } from 'react';
import {
    Send,
    Loader2,
    Mail,
    User,
    FileText,
    Paperclip,
    Trash2,
} from 'lucide-react';
import { Button } from './ui/button';
import { AiButton } from './ui/ai-button';
import { Input } from './ui/input';
import { Label } from './ui/label';
import { Dialog, DialogContent, DialogTitle, DialogHeader } from './ui/dialog';
import type { ProjektEmail } from '../types';

// Interface für hochgeladene Dateien
interface UploadedFile {
    file: File;
    previewUrl?: string;
}

interface EmailReplyModalProps {
    isOpen: boolean;
    onClose: () => void;
    // Context can be specific or inferred from email
    context?: {
        type: 'projekt' | 'anfrage' | 'lieferant';
        id: number;
    };
    email: ProjektEmail; 
    projektName?: string; 
    kundenEmail?: string; 
}

// Signatur-Wrapper für konsistentes Styling
const wrapSignatureHtml = (rawHtml: string): string => {
    const trimmed = (rawHtml || '').trim();
    if (!trimmed) return '';
    if (/email-signature/i.test(trimmed)) {
        return trimmed;
    }
    return `<div class="email-signature" style="margin-top: 20px; padding-top: 10px; border-top: 1px solid #ddd;">${trimmed}</div>`;
};

// HTML für E-Mail-Versand vorbereiten
const prepareHtmlForSending = (rawHtml: string): string => {
    const wrapper = document.createElement('div');
    wrapper.innerHTML = rawHtml || '';
    wrapper.querySelectorAll('script, style').forEach(n => n.remove());
    wrapper.querySelectorAll('[contenteditable]').forEach(n => n.removeAttribute('contenteditable'));
    return wrapper.innerHTML.trim();
};

// Betreff für Antwort formatieren
const makeReplySubject = (subject?: string): string => {
    if (!subject) return 'AW: ';
    return /^(\s*)(aw|re):\s*/i.test(subject) ? subject : `AW: ${subject}`;
};

// CID-URLs durch API-URLs ersetzen (für Inline-Bilder in Signatur etc.)
const replaceCidUrls = (html: string, emailId: number, attachments?: Array<{ id?: number; contentId?: string }>): string => {
    if (!html || !attachments) return html;
    let result = html;
    attachments.forEach(att => {
        if (att.contentId && att.id) {
            const cleanCid = att.contentId.replace(/[<>]/g, '');
            const url = `/api/emails/${emailId}/attachments/${att.id}`;
            // Replace src="cid:xyz" patterns
            result = result.replace(new RegExp(`src=["']cid:${cleanCid}["']`, 'gi'), `src="${url}"`);
            result = result.replace(new RegExp(`src=["']cid:<${cleanCid}>["']`, 'gi'), `src="${url}"`);
            if (att.contentId !== cleanCid) {
                result = result.replace(new RegExp(`src=["']cid:${att.contentId}["']`, 'gi'), `src="${url}"`);
            }
        }
    });
    return result;
};

// Aktuelles Frontend-Profil aus localStorage holen
const FRONTEND_USER_STORAGE_KEY = 'frontendUserSelection';

interface FrontendUserSelection {
    id: number;
    displayName: string;
}

const getCurrentFrontendUser = (): FrontendUserSelection | null => {
    try {
        const raw = localStorage.getItem(FRONTEND_USER_STORAGE_KEY);
        if (!raw) return null;
        const parsed = JSON.parse(raw);
        if (parsed && typeof parsed.id === 'number') {
            return parsed as FrontendUserSelection;
        }
    } catch (err) {
        console.warn('Frontend-Profil konnte nicht gelesen werden:', err);
    }
    return null;
};

export function EmailReplyModal({
    isOpen,
    onClose,
    context,
    email,
    projektName,
    kundenEmail
}: EmailReplyModalProps) {
    const [recipient, setRecipient] = useState('');
    const [subject, setSubject] = useState('');
    const [body, setBody] = useState('');
    const [signature, setSignature] = useState('');
    const [isSending, setIsSending] = useState(false);
    const [beautifying, setBeautifying] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [uploadedFiles, setUploadedFiles] = useState<UploadedFile[]>([]);
    const fileInputRef = useRef<HTMLInputElement>(null);

    // Datei-Upload Handler
    const handleFileUpload = (files: FileList | null) => {
        if (!files) return;
        const newFiles: UploadedFile[] = Array.from(files).map(file => ({
            file,
            previewUrl: file.type.startsWith('image/') ? URL.createObjectURL(file) : undefined,
        }));
        setUploadedFiles(prev => [...prev, ...newFiles]);
    };

    // Datei entfernen
    const removeUploadedFile = (index: number) => {
        setUploadedFiles(prev => {
            const updated = prev.filter((_, i) => i !== index);
            if (prev[index]?.previewUrl) {
                URL.revokeObjectURL(prev[index].previewUrl!);
            }
            return updated;
        });
    };

    // Signatur laden
    const loadSignature = useCallback(async (): Promise<string> => {
        try {
            const currentUser = getCurrentFrontendUser();
            const params = new URLSearchParams();
            if (currentUser?.id) {
                params.set('frontendUserId', String(currentUser.id));
            }
            const signatureUrl = params.toString()
                ? `/api/email/signatures/default?${params.toString()}`
                : '/api/email/signatures/default';

            const res = await fetch(signatureUrl);
            if (res.ok && res.status !== 204) {
                const data = await res.json();
                if (data.html) {
                    const wrappedSig = wrapSignatureHtml(data.html);
                    setSignature(wrappedSig);
                    return wrappedSig;
                }
            }
        } catch (err) {
            console.error('Signatur konnte nicht geladen werden:', err);
        }
        return '';
    }, []);

    // Initialisierung wenn Modal öffnet
    useEffect(() => {
        if (isOpen && email) {
            // Empfänger setzen
            const toAddr = email.direction === 'IN'
                ? (email.from || email.fromAddress || kundenEmail || '')
                : (kundenEmail || '');
            setRecipient(toAddr);

            setSubject(makeReplySubject(email.subject));

            const originalBody = email.bodyHtml || email.bodyPreview || '';
            const processedBody = replaceCidUrls(originalBody, email.id, email.attachments);
            const sanitizedBody = prepareHtmlForSending(processedBody);
            const quotedBody = sanitizedBody
                ? `<br><br><blockquote style="border-left: 2px solid #ddd; margin: 0; padding-left: 0.8em; color: #666;">${sanitizedBody}</blockquote>`
                : '';

            loadSignature().then(sig => {
                setBody(`<p><br></p>${sig}${quotedBody}`);
            });

            setError(null);
            setUploadedFiles([]);
        }
    }, [isOpen, email, kundenEmail, loadSignature]);

    // KI-Verschönerung
    const handleBeautify = async () => {
        const container = document.createElement('div');
        container.innerHTML = body;
        const sigEl = container.querySelector('.email-signature');
        const quoteEl = container.querySelector('blockquote');
        let quoteHtml = '';
        if (quoteEl) {
            quoteHtml = quoteEl.outerHTML;
            quoteEl.remove();
        }
        if (sigEl) sigEl.remove();

        const plainText = container.textContent?.trim() || '';
        if (!plainText) {
            setError('Kein Text zum Umformulieren gefunden.');
            return;
        }

        setBeautifying(true);
        setError(null);

        try {
            const res = await fetch('/api/email/beautify', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    body: plainText,
                    context: projektName || null
                })
            });

            if (!res.ok) throw new Error(`HTTP ${res.status}`);

            const data = await res.json();
            const suggestion = typeof data?.body === 'string' ? data.body.trim() : '';

            if (!suggestion) {
                setError('Keine alternative Formulierung erhalten.');
                return;
            }

            const isHtml = /<[^>]+>/.test(suggestion);
            const htmlSuggestion = isHtml
                ? suggestion
                : suggestion.split(/\n{2,}/).filter(Boolean).map((p: string) => `<p>${p}</p>`).join('');

            setBody(`${htmlSuggestion}${signature}${quoteHtml}`);
        } catch (err) {
            console.error('KI-Verschönerung fehlgeschlagen:', err);
            setError('Formulierung fehlgeschlagen. Bitte erneut versuchen.');
        } finally {
            setBeautifying(false);
        }
    };

    // E-Mail senden
    const handleSend = async () => {
        if (!recipient.trim()) {
            setError('Bitte Empfänger angeben.');
            return;
        }
        if (!subject.trim()) {
            setError('Bitte Betreff angeben.');
            return;
        }

        setIsSending(true);
        setError(null);

        try {
            const fullBody = body;
            const currentUser = getCurrentFrontendUser();
            const formData = new FormData();
            
            const dtoPayload = {
                sender: 'bauschlosserei-kuhn@t-online.de', // TODO: Settings
                recipients: [recipient.trim()],
                subject: subject.trim(),
                body: prepareHtmlForSending(fullBody),
                direction: 'OUT',
                parentId: email.id,
                benutzer: currentUser?.displayName || '',
                frontendUserId: currentUser?.id || null,
                // Explicit Assignment
                projektId: context?.type === 'projekt' ? context.id : undefined,
                anfrageId: context?.type === 'anfrage' ? context.id : undefined,
                lieferantId: context?.type === 'lieferant' ? context.id : undefined
            };

            formData.append('dto', new Blob([JSON.stringify(dtoPayload)], { type: 'application/json' }));
            
            uploadedFiles.forEach((uf) => {
                formData.append('attachments', uf.file);
            });

            const res = await fetch(`/api/emails/${email.id}/reply`, {
                method: 'POST',
                body: formData
            });

            if (!res.ok) {
                throw new Error('Antwort senden fehlgeschlagen');
            }

            onClose();
            // TODO: Proper refresh instead of reload
            // window.location.reload(); 
        } catch (err) {
            console.error('E-Mail senden fehlgeschlagen:', err);
            setError('E-Mail konnte nicht gesendet werden. Bitte erneut versuchen.');
        } finally {
            setIsSending(false);
        }
    };

    if (!isOpen) return null;

    return (
        <Dialog open={isOpen} onOpenChange={(open) => !open && onClose()}>
            <DialogContent className="max-w-5xl w-[90vw] h-[90vh] flex flex-col p-0 gap-0">
                <DialogHeader className="px-6 py-4 border-b border-slate-200 bg-rose-50 flex flex-row items-center justify-between m-0 shrink-0">
                    <div className="flex items-center gap-3">
                        <div className="w-10 h-10 rounded-full bg-rose-100 flex items-center justify-center">
                            <Mail className="w-5 h-5 text-rose-600" />
                        </div>
                        <div className="flex flex-col items-start gap-0.5">
                            <DialogTitle className="text-lg font-semibold text-slate-900 leading-none">E-Mail Antwort</DialogTitle>
                            <p className="text-sm text-slate-500 font-normal">
                                Antwort auf: {email.subject || '(kein Betreff)'}
                            </p>
                        </div>
                    </div>
                    
                    <div className="flex items-center gap-2">
                        <Button
                            variant="ghost"
                            onClick={onClose}
                            disabled={isSending}
                        >
                            Abbrechen
                        </Button>
                        <Button
                            onClick={handleSend}
                            disabled={isSending || !recipient.trim() || !subject.trim()}
                            className="bg-rose-600 text-white hover:bg-rose-700"
                        >
                            {isSending ? (
                                <>
                                    <Loader2 className="w-4 h-4 mr-2 animate-spin" />
                                    Wird gesendet...
                                </>
                            ) : (
                                <>
                                    <Send className="w-4 h-4 mr-2" />
                                    Senden
                                </>
                            )}
                        </Button>
                    </div>
                </DialogHeader>

                <div className="flex-1 overflow-auto p-6 space-y-4">
                    {error && (
                        <div className="p-3 bg-red-50 border border-red-200 rounded-lg text-red-700 text-sm">
                            {error}
                        </div>
                    )}

                    <div className="space-y-2">
                        <Label className="flex items-center gap-2 text-slate-700">
                            <User className="w-4 h-4" />
                            An
                        </Label>
                        <Input
                            type="email"
                            value={recipient}
                            onChange={(e) => setRecipient(e.target.value)}
                            className="border-slate-200 focus:border-rose-300 focus:ring-rose-200"
                        />
                    </div>

                    <div className="space-y-2">
                        <Label className="flex items-center gap-2 text-slate-700">
                            <FileText className="w-4 h-4" />
                            Betreff
                        </Label>
                        <Input
                            type="text"
                            value={subject}
                            onChange={(e) => setSubject(e.target.value)}
                            className="border-slate-200 focus:border-rose-300 focus:ring-rose-200"
                        />
                    </div>

                    <div className="space-y-2">
                        <Label className="flex items-center gap-2 text-slate-700">
                            <Paperclip className="w-4 h-4" />
                            Dateien
                        </Label>
                        <div
                            className="border-2 border-dashed border-rose-300 rounded-lg p-4 text-center cursor-pointer hover:border-rose-400 hover:bg-rose-50/50 transition-colors"
                            onClick={() => fileInputRef.current?.click()}
                            onDragOver={(e) => {
                                e.preventDefault();
                                e.currentTarget.classList.add('border-rose-400', 'bg-rose-50');
                            }}
                            onDragLeave={(e) => {
                                e.preventDefault();
                                e.currentTarget.classList.remove('border-rose-400', 'bg-rose-50');
                            }}
                            onDrop={(e) => {
                                e.preventDefault();
                                e.currentTarget.classList.remove('border-rose-400', 'bg-rose-50');
                                handleFileUpload(e.dataTransfer.files);
                            }}
                        >
                            <Paperclip className="w-8 h-8 mx-auto text-slate-400 mb-2" />
                            <p className="text-sm text-slate-600">
                                Dateien hier ablegen (Drag & Drop)
                            </p>
                        </div>
                        <input
                            ref={fileInputRef}
                            type="file"
                            multiple
                            className="hidden"
                            onChange={(e) => handleFileUpload(e.target.files)}
                        />

                        {uploadedFiles.length > 0 && (
                            <div className="flex flex-wrap gap-2 mt-2">
                                {uploadedFiles.map((uf, index) => (
                                    <div
                                        key={index}
                                        className="flex items-center gap-2 px-3 py-2 bg-slate-100 rounded-lg text-sm"
                                    >
                                        <Paperclip className="w-4 h-4 text-slate-500" />
                                        <span className="truncate max-w-[150px]" title={uf.file.name}>
                                            {uf.file.name}
                                        </span>
                                        <button
                                            type="button"
                                            onClick={() => removeUploadedFile(index)}
                                            className="text-slate-400 hover:text-red-500"
                                        >
                                            <Trash2 className="w-4 h-4" />
                                        </button>
                                    </div>
                                ))}
                            </div>
                        )}
                    </div>

                    <div className="space-y-2 flex-1 flex flex-col min-h-[300px]">
                        <Label className="flex items-center gap-2 text-slate-700">
                            <Mail className="w-4 h-4" />
                            Nachricht
                        </Label>
                        <div className="flex-1 border border-slate-200 rounded-lg overflow-hidden flex flex-col">
                        <div className="bg-slate-50 p-2 border-b border-slate-200 flex gap-2">
                                <AiButton
                                    onClick={handleBeautify}
                                    isLoading={beautifying || isSending}
                                    label="KI-Optimierung"
                                />
                            </div>
                            <div className="flex-1 p-4 overflow-y-auto text-sm bg-white">
                                <p className="text-xs text-slate-400 mb-2">Tipp: Bilder per Drag & Drop oder Einfügen (Ctrl+V) hinzufügen. Klicken Sie auf ein Bild, um es zu bearbeiten.</p>
                                <div 
                                    className="min-h-[200px] outline-none focus:ring-1 focus:ring-rose-200"
                                    contentEditable
                                    suppressContentEditableWarning
                                    dangerouslySetInnerHTML={{ __html: body }}
                                    onBlur={(e) => setBody(e.currentTarget.innerHTML)}
                                />
                            </div>
                        </div>
                    </div>
                </div>


            </DialogContent>
        </Dialog>
    );
}
