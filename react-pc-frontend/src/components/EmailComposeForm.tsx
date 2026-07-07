/* eslint-disable react-refresh/only-export-components */
import React, { useState, useEffect, useCallback, useRef, useMemo } from 'react';
import {
    Loader2, Paperclip, X, Plus, Mail, Upload, Eye, Save, Send, FolderOpen
} from 'lucide-react';
import { Button } from './ui/button';
import { PdfCanvasViewer } from './ui/PdfCanvasViewer';
import { AiButton } from './ui/ai-button';
import { Input } from './ui/input';
import { Label } from './ui/label';
import { Select } from './ui/select-custom';
import type { ProjektDetail, ProjektDokument } from '../types';
import { EmailRecipientInput } from './EmailRecipientInput';
import { EmailEntityDocumentPicker } from './EmailEntityDocumentPicker';

// Interface für hochgeladene externe Dateien
interface UploadedFile {
    file: File;
    previewUrl?: string;
    sourceDokumentId?: number;
}

const MAX_ATTACHMENT_BYTES = 20 * 1024 * 1024;

const formatFileSize = (bytes: number): string => {
    if (bytes < 1024 * 1024) return `${Math.max(1, Math.round(bytes / 1024))} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
};

const isImageAttachment = (file: File): boolean =>
    file.type.startsWith('image/') || /\.(jpe?g|png|gif|webp|bmp)$/i.test(file.name);

const isPdfAttachment = (file: File): boolean =>
    file.type === 'application/pdf' || /\.pdf$/i.test(file.name);

export interface EmailComposeFormProps {
    onClose: () => void;
    // For projects
    projektId?: number;
    projekt?: ProjektDetail;
    // For offers (Anfragen)
    anfrageId?: number;
    anfrage?: {
        bauvorhaben: string;
        kundenName?: string;
        kundenEmails?: string[];
        kundenAnrede?: string;
        kundenAnsprechpartner?: string;
    };
    /** Customer ID for saving new email addresses */
    kundeId?: number;
    initialRecipient?: string;
    initialSubject?: string;
    initialBody?: string;
    /** Zitat-Block für Antworten – wird NACH der Signatur eingefügt (richtige Reihenfolge: Text → Signatur → Zitat) */
    replyQuote?: string;
    /** ID der Email, auf die geantwortet wird – nutzt /{replyEmailId}/reply statt /send */
    replyEmailId?: number;
    /** Pre-attached files (e.g. generated PDF from DocumentEditor) */
    initialAttachments?: File[];
    /** Vom Benutzer im Pop-up gewählte Gültigkeit des digitalen Annahme-Links (nur Angebote). */
    gueltigkeitTage?: number;
    onSuccess?: () => void;
    variant?: 'default' | 'modal';
    /** Existing draft ID to resume editing */
    draftId?: number;
}

interface SignatureResponse {
    id?: number;
    html?: string;
}

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

const deriveOrderRecipientName = (subject: string): string => {
    const match = subject.match(/^Bestellanfrage:\s*(.+)$/i);
    return match?.[1]?.trim() || '';
};

// Anrede-Enum in korrekten Anredetext umwandeln (ohne Name - wird vom Backend separat hinzugefügt)
export const anredeEnumToText = (anrede: string | undefined): string => {
    if (!anrede) return 'Sehr geehrte Damen und Herren';
    const upper = anrede.toUpperCase();
    switch (upper) {
        case 'HERR': return 'Sehr geehrter Herr';
        case 'FRAU': return 'Sehr geehrte Frau';
        case 'FAMILIE': return 'Sehr geehrte Familie';
        case 'FIRMA': return 'Sehr geehrte Damen und Herren';
        case 'DAMEN_HERREN': return 'Sehr geehrte Damen und Herren';
        default: return 'Sehr geehrte Damen und Herren';
    }
};

export function EmailComposeForm({
    onClose,
    projektId,
    projekt,
    anfrageId,
    anfrage,
    kundeId,
    initialRecipient = '',
    initialSubject = '',
    initialBody = '',
    replyQuote,
    replyEmailId,
    initialAttachments,
    onSuccess,
    draftId: initialDraftId,
}: EmailComposeFormProps) {
    // Determine context: projekt or anfrage
    const isAnfrageContext = !!anfrageId;
    const entityId = projektId || anfrageId;

    // State für vom Backend geladene Daten (Emails + Projektdaten für Template)
    const [fetchedEmails, setFetchedEmails] = useState<string[]>([]);
    const [fetchedProjekt, setFetchedProjekt] = useState<ProjektDetail | null>(null);
    const [fromAddresses, setFromAddresses] = useState<string[]>([]);
    const [fromAddress, setFromAddress] = useState('');

    // Effektives Projekt: Prop oder vom Backend geladenes Projekt
    const effectiveProjekt = projekt || fetchedProjekt;
    const entityName = effectiveProjekt?.bauvorhaben || anfrage?.bauvorhaben || '';

    // Beim Öffnen: Verknüpfte E-Mails + Projektdaten direkt vom Backend laden
    useEffect(() => {
        const loadLinkedData = async () => {
            const emails: string[] = [];
            try {
                if (projektId) {
                    const res = await fetch(`/api/projekte/${projektId}`);
                    if (res.ok) {
                        const data = await res.json();
                        if (data.kundenEmails) data.kundenEmails.forEach((e: string) => { if (e && !emails.includes(e)) emails.push(e); });
                        if (data.kundeDto?.kundenEmails) data.kundeDto.kundenEmails.forEach((e: string) => { if (e && !emails.includes(e)) emails.push(e); });
                        setFetchedProjekt(data);
                    }
                } else if (anfrageId) {
                    const res = await fetch(`/api/anfragen/${anfrageId}`);
                    if (res.ok) {
                        const data = await res.json();
                        if (data.kundenEmails) data.kundenEmails.forEach((e: string) => { if (e && !emails.includes(e)) emails.push(e); });
                    }
                }
            } catch {
                // Fallback auf Props
            }
            if (emails.length > 0) setFetchedEmails(emails);
        };
        loadLinkedData();
    }, [projektId, anfrageId]);

    // Merge: Props + Backend (dedupliziert)
    const entityKundenEmails = useMemo(() => {
        const emails: string[] = [];
        // Projekt-spezifische E-Mails (aus effectiveProjekt)
        if (effectiveProjekt?.kundenEmails) {
            effectiveProjekt.kundenEmails.forEach(e => {
                if (e && !emails.includes(e)) emails.push(e);
            });
        }
        // Kunden-E-Mails aus kundeDto
        if (effectiveProjekt?.kundeDto?.kundenEmails) {
            effectiveProjekt.kundeDto.kundenEmails.forEach(e => {
                if (e && !emails.includes(e)) emails.push(e);
            });
        }
        // Anfrage-E-Mails als Fallback
        if (anfrage?.kundenEmails) {
            anfrage.kundenEmails.forEach(e => {
                if (e && !emails.includes(e)) emails.push(e);
            });
        }
        // Backend-geladene E-Mails ergänzen
        fetchedEmails.forEach(e => {
            if (e && !emails.includes(e)) emails.push(e);
        });
        return emails;
    }, [effectiveProjekt?.kundenEmails, effectiveProjekt?.kundeDto?.kundenEmails, anfrage?.kundenEmails, fetchedEmails]);

    const [recipient, setRecipient] = useState<string>(initialRecipient || '');
    const [subject, setSubject] = useState<string>(initialSubject || '');
    const [body, setBody] = useState<string>(initialBody || '');
    const [signature, setSignature] = useState('');
    const [sending, setSending] = useState(false);
    const [beautifying, setBeautifying] = useState(false);
    const [error, setError] = useState<string | null>(null);

    // Draft auto-save
    const [draftId, setDraftId] = useState<number | null>(initialDraftId ?? null);
    const draftSaveTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
    const [draftSaving, setDraftSaving] = useState(false);
    // Erst speichern, wenn der User selbst etwas ändert. Sonst legt das Öffnen aus
    // dem DocumentEditor (mit prefilled initialBody/Subject/Recipient) sofort einen
    // Entwurf an, der beim Abbrechen im E-Mail-Center verbleibt.
    const userInteracted = useRef<boolean>(!!initialDraftId);
    const markDirty = useCallback(() => {
        userInteracted.current = true;
    }, []);

    // CC State
    const [ccRecipients, setCcRecipients] = useState<string[]>([]);
    const [showCc, setShowCc] = useState(false);

    // Dokumente
    const [pdfPreviewUrl, setPdfPreviewUrl] = useState<string | null>(null);
    const [imagePreview, setImagePreview] = useState<{ url: string; name: string } | null>(null);
    const [entityDokumente, setEntityDokumente] = useState<ProjektDokument[]>([]);
    const [loadingEntityDokumente, setLoadingEntityDokumente] = useState(false);
    const [showEntityDokumente, setShowEntityDokumente] = useState(false);
    const [loadingEntityDokumentIds, setLoadingEntityDokumentIds] = useState<Set<number>>(new Set());

    // Externe hochgeladene Dateien
    const [uploadedFiles, setUploadedFiles] = useState<UploadedFile[]>(() => {
        if (initialAttachments && initialAttachments.length > 0) {
            return initialAttachments.map(file => ({ file }));
        }
        return [];
    });
    const fileInputRef = useRef<HTMLInputElement>(null);
    const editorRef = useRef<HTMLDivElement>(null);
    const attachmentBytes = useMemo(
        () => uploadedFiles.reduce((sum, uploadedFile) => sum + uploadedFile.file.size, 0),
        [uploadedFiles]
    );
    const attachmentLimitExceeded = attachmentBytes > MAX_ATTACHMENT_BYTES;
    const selectedEntityDokumentIds = useMemo(
        () => new Set(uploadedFiles.flatMap(file => file.sourceDokumentId ? [file.sourceDokumentId] : [])),
        [uploadedFiles]
    );

    // Save-Email Dialog State
    const [showSaveEmailDialog, setShowSaveEmailDialog] = useState(false);
    const [newEmailToSave, setNewEmailToSave] = useState<string>('');
    const [savingEmail, setSavingEmail] = useState(false);

    // Verfügbare E-Mail-Adressen sammeln (memoized um Re-Render-Loops zu vermeiden)
    const availableEmails = useMemo(() => {
        const emails: string[] = [];
        entityKundenEmails.forEach(e => {
            if (e && !emails.includes(e)) emails.push(e);
        });
        return emails;
    }, [entityKundenEmails]);

    const isBestellungContext = (initialSubject || subject).trim().toLowerCase().startsWith('bestellanfrage:');
    const orderRecipientName = deriveOrderRecipientName(initialSubject || subject);
    const dialogTitle = isBestellungContext && orderRecipientName
        ? `Bestellung an ${orderRecipientName} senden`
        : 'Neue E-Mail';
    const dialogSubtitle = entityName
        ? `${isAnfrageContext ? 'Anfrage' : 'Projekt'}: ${entityName}`
        : isBestellungContext && orderRecipientName
            ? 'Bestellanfrage mit E-Mail-Versand vorbereiten'
            : 'E-Mail verfassen und Anhänge verwalten';
    const hasInitialAttachments = !!(initialAttachments && initialAttachments.length > 0);
    const initialAttachmentLabel = hasInitialAttachments
        ? initialAttachments!.length === 1
            ? initialAttachments![0].name
            : `${initialAttachments!.length} Dateien automatisch angehängt`
        : '';
    const firstPdfAttachment = uploadedFiles.find(file => file.file.type === 'application/pdf');
    const fromAddressOptions = useMemo(
        () => fromAddresses.map(address => ({ value: address, label: address })),
        [fromAddresses]
    );
    // Handle recipient change
    const handleRecipientChange = (val: string) => {
        markDirty();
        setRecipient(val);
    };

    // ═══════════════════════════════════════════════════════════════
    // DRAFT AUTO-SAVE (2s Debounce)
    // ═══════════════════════════════════════════════════════════════
    const saveDraft = useCallback(async () => {
        // Kein Speichern, solange der User keine eigene Eingabe gemacht hat
        // (verhindert Phantom-Entwürfe beim Öffnen aus dem DocumentEditor).
        if (!userInteracted.current) return;
        const currentBody = editorRef.current?.innerHTML || body;
        // Nur speichern wenn mindestens Empfänger, Betreff oder Body vorhanden
        if (!recipient.trim() && !subject.trim() && !currentBody.trim()) return;

        const draftData = {
            recipient: recipient.trim(),
            cc: ccRecipients.filter(c => c.trim()).join(', '),
            subject: subject.trim(),
            body: currentBody,
            fromAddress: fromAddress || null,
            replyEmailId: replyEmailId || null,
            projektId: !isAnfrageContext && entityId ? entityId : null,
            anfrageId: isAnfrageContext && entityId ? entityId : null,
        };

        try {
            setDraftSaving(true);
            if (draftId) {
                await fetch(`/api/emails/drafts/${draftId}`, {
                    method: 'PUT',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(draftData),
                });
            } else {
                const res = await fetch('/api/emails/drafts', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(draftData),
                });
                if (res.ok) {
                    const saved = await res.json();
                    setDraftId(saved.id);
                }
            }
        } catch (err) {
            console.warn('Draft auto-save fehlgeschlagen:', err);
        } finally {
            setDraftSaving(false);
        }
    }, [recipient, subject, body, ccRecipients, fromAddress, replyEmailId, entityId, isAnfrageContext, draftId]);

    // Debounced auto-save: bei jeder relevanten Änderung wird nach 2s gespeichert
    useEffect(() => {
        if (draftSaveTimer.current) clearTimeout(draftSaveTimer.current);
        draftSaveTimer.current = setTimeout(() => {
            saveDraft();
        }, 2000);
        return () => {
            if (draftSaveTimer.current) clearTimeout(draftSaveTimer.current);
        };
    }, [saveDraft]);

    // Draft löschen (nach Send oder wenn User Entwurf verwirft)
    const deleteDraft = useCallback(async () => {
        if (!draftId) return;
        try {
            await fetch(`/api/emails/drafts/${draftId}`, { method: 'DELETE' });
            setDraftId(null);
        } catch (err) {
            console.warn('Draft löschen fehlgeschlagen:', err);
        }
    }, [draftId]);
    const loadSignature = useCallback(async () => {
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
                const data: SignatureResponse = await res.json();
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

    const loadFromAddresses = useCallback(async () => {
        try {
            const currentUser = getCurrentFrontendUser();
            const params = new URLSearchParams();
            if (currentUser?.id) {
                params.set('frontendUserId', String(currentUser.id));
            }
            const url = params.toString()
                ? `/api/email/from-addresses?${params.toString()}`
                : '/api/email/from-addresses';

            const res = await fetch(url);
            if (!res.ok) return;
            const data = await res.json();
            const addresses = Array.isArray(data)
                ? data.filter((value): value is string => typeof value === 'string' && value.trim().length > 0)
                : [];
            setFromAddresses(addresses);
            if (addresses.length > 0) {
                // Adressen sind bereits sortiert: User-Adresse steht an erster Stelle.
                setFromAddress(prev => prev || addresses[0]);
            }
        } catch (err) {
            console.error('Absender-Adressen konnten nicht geladen werden:', err);
        }
    }, []);

    const loadEntityDokumente = useCallback(async () => {
        if (!entityId) {
            setEntityDokumente([]);
            return;
        }
        setLoadingEntityDokumente(true);
        try {
            const apiUrl = isAnfrageContext
                ? `/api/anfragen/${entityId}/dokumente`
                : `/api/projekte/${entityId}/dokumente`;
            const res = await fetch(apiUrl);
            if (!res.ok) throw new Error('Dateien konnten nicht geladen werden');
            setEntityDokumente(await res.json());
        } catch (err) {
            console.error('Projekt-/Anfrage-Dateien konnten nicht geladen werden:', err);
            setError('Projekt-/Anfrage-Dateien konnten nicht geladen werden.');
        } finally {
            setLoadingEntityDokumente(false);
        }
    }, [entityId, isAnfrageContext]);

    // Initialisierung - nur einmal beim Mount
    useEffect(() => {
        // contentEditable: Enter soll konsistent <p> statt <div> erzeugen,
        // damit Absätze sauber gestylt werden können (siehe Editor-CSS).
        try {
            document.execCommand('defaultParagraphSeparator', false, 'p');
        } catch {
            // execCommand ist deprecated – Fallback ist der Browser-Default.
        }

        if (replyQuote) {
            // Antwort-Modus: Schreibbereich → Signatur → Zitat
            loadSignature().then(sig => {
                const content = `<p><br></p>${sig}<br>${replyQuote}`;
                setBody(content);
                if (editorRef.current) {
                    editorRef.current.innerHTML = content;
                    // Cursor an den Anfang setzen
                    const range = document.createRange();
                    const sel = window.getSelection();
                    range.setStart(editorRef.current, 0);
                    range.collapse(true);
                    sel?.removeAllRanges();
                    sel?.addRange(range);
                }
            });
        } else if (!initialBody) {
            // Neue E-Mail: nur Signatur
            loadSignature().then(sig => {
                const initialContent = `<p><br></p>${sig}`;
                setBody(initialContent);
                if (editorRef.current) {
                    editorRef.current.innerHTML = initialContent;
                }
            });
        } else {
            // initialBody vorhanden (z.B. aus Draft oder DocumentEditor)
            // Signatur nur anhängen wenn im Body noch keine enthalten ist (Draft enthält sie bereits)
            const alreadyHasSignature = /email-signature/i.test(initialBody);
            if (alreadyHasSignature) {
                setBody(initialBody);
                if (editorRef.current) {
                    editorRef.current.innerHTML = initialBody;
                }
            } else {
                loadSignature().then(sig => {
                    const content = `${initialBody}${sig}`;
                    setBody(content);
                    if (editorRef.current) {
                        editorRef.current.innerHTML = content;
                    }
                });
            }
        }

        loadEntityDokumente();
        loadFromAddresses();
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    // Initiale Empfänger setzen - wenn keine initiale E-Mail vorgegeben und E-Mails verfügbar
    useEffect(() => {
        if (!initialRecipient && !recipient && availableEmails.length > 0) {
            setRecipient(availableEmails[0]);
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [availableEmails]);

    // Externe Dateien hochladen
    const handleFileUpload = (files: FileList | null) => {
        if (!files) return;

        setUploadedFiles(previous => {
            const accepted: UploadedFile[] = [];
            let nextSize = previous.reduce((sum, uploadedFile) => sum + uploadedFile.file.size, 0);
            for (const file of Array.from(files)) {
                if (nextSize + file.size > MAX_ATTACHMENT_BYTES) {
                    setError(`Anhänge dürfen zusammen höchstens ${formatFileSize(MAX_ATTACHMENT_BYTES)} groß sein.`);
                    continue;
                }
                accepted.push({ file });
                nextSize += file.size;
            }
            if (accepted.length > 0) {
                markDirty();
                setError(null);
            }
            return [...previous, ...accepted];
        });
    };

    const handleEntityDokumentToggle = async (document: ProjektDokument) => {
        if (selectedEntityDokumentIds.has(document.id)) {
            setUploadedFiles(previous => previous.filter(file => file.sourceDokumentId !== document.id));
            markDirty();
            return;
        }

        setLoadingEntityDokumentIds(previous => new Set(previous).add(document.id));
        setError(null);
        try {
            const downloadUrl = document.url + (document.url.includes('?') ? '&' : '?') + 'download=true';
            const response = await fetch(downloadUrl);
            if (!response.ok) throw new Error(`HTTP ${response.status}`);
            const blob = await response.blob();
            const file = new File(
                [blob],
                document.originalDateiname,
                { type: blob.type || document.dateityp || 'application/octet-stream' }
            );

            setUploadedFiles(previous => {
                const currentSize = previous.reduce((sum, uploadedFile) => sum + uploadedFile.file.size, 0);
                if (currentSize + file.size > MAX_ATTACHMENT_BYTES) {
                    setError(`"${document.originalDateiname}" passt nicht mehr in das Anhangslimit von ${formatFileSize(MAX_ATTACHMENT_BYTES)}.`);
                    return previous;
                }
                markDirty();
                return [...previous, { file, sourceDokumentId: document.id }];
            });
        } catch (err) {
            console.error('Datei konnte nicht als E-Mail-Anhang geladen werden:', err);
            setError(`"${document.originalDateiname}" konnte nicht angehängt werden.`);
        } finally {
            setLoadingEntityDokumentIds(previous => {
                const next = new Set(previous);
                next.delete(document.id);
                return next;
            });
        }
    };

    const handleDrop = (e: React.DragEvent) => {
        e.preventDefault();
        e.stopPropagation();
        handleFileUpload(e.dataTransfer.files);
    };

    const handleDragOver = (e: React.DragEvent) => {
        e.preventDefault();
        e.stopPropagation();
    };

    // KI-Verschönerung
    const handleBeautify = async () => {
        const currentHtml = editorRef.current?.innerHTML || body;
        const container = document.createElement('div');
        container.innerHTML = currentHtml;

        // 1. Signatur entfernen (und speichern)
        const sigEl = container.querySelector('.email-signature');
        let sigHtml = '';
        if (sigEl) {
            sigHtml = sigEl.outerHTML;
            sigEl.remove();
        }

        // 2. Zitat / Antwort-Kontext entfernen (und speichern)
        // Wir suchen nach .email-quote ODRE dem typischen Style
        let quoteEl = container.querySelector('.email-quote');
        if (!quoteEl) {
            // Fallback: Suche nach dem Style, falls Klasse fehlt
            const divs = container.querySelectorAll('div[style*="border-left"]');
            if (divs.length > 0) {
                quoteEl = divs[0];
            }
        }

        let quoteHtml = '';
        let contextText = '';

        if (quoteEl) {
            quoteHtml = quoteEl.outerHTML;
            contextText = quoteEl.textContent || '';
            quoteEl.remove();
        }

        // 3. Verbleibender Text ist der User-Input
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
                    context: contextText || (entityName ? `Projekt/Anfrage: ${entityName}` : null)
                })
            });

            if (!res.ok) {
                throw new Error(`HTTP ${res.status}`);
            }

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

            // 4. Alles wieder zusammensetzen: Optimierter Text + Signatur + Zitat
            const newContent = `${htmlSuggestion}${sigHtml || signature}${quoteHtml ? '<br>' + quoteHtml : ''}`;
            markDirty();
            setBody(newContent);
            if (editorRef.current) {
                editorRef.current.innerHTML = newContent;
            }
        } catch (err) {
            console.error('KI-Verschönerung fehlgeschlagen:', err);
            setError('Formulierung fehlgeschlagen. Bitte erneut versuchen.');
        } finally {
            setBeautifying(false);
        }
    };

    // E-Mail senden
    const handleSend = async () => {
        const finalRecipient = recipient.trim();

        if (!finalRecipient) {
            setError('Bitte Empfänger angeben.');
            return;
        }
        if (!subject.trim()) {
            setError('Bitte Betreff angeben.');
            return;
        }
        if (attachmentLimitExceeded) {
            setError(`Anhänge dürfen zusammen höchstens ${formatFileSize(MAX_ATTACHMENT_BYTES)} groß sein.`);
            return;
        }

        setSending(true);
        setError(null);

        try {
            const currentUser = getCurrentFrontendUser();

            // FormData für multipart request (mit Anhängen)
            const formData = new FormData();

            const dtoPayload = {
                // Leerer sender = Backend loest aus frontendUserId auf (zugewiesene Adresse).
                sender: fromAddress || null,
                recipients: [finalRecipient],
                cc: ccRecipients.filter(c => c.trim().length > 0),
                subject: subject.trim(),
                body: prepareHtmlForSending(editorRef.current?.innerHTML || body),
                direction: 'OUT',
                benutzer: currentUser?.displayName || '',
                frontendUserId: currentUser?.id || null,
                // Include entity assignment in the DTO
                projektId: !isAnfrageContext && entityId ? entityId : null,
                anfrageId: isAnfrageContext && entityId ? entityId : null,
            };

            formData.append('dto', new Blob([JSON.stringify(dtoPayload)], { type: 'application/json' }));

            uploadedFiles.forEach((uf) => {
                formData.append('attachments', uf.file);
            });

            // Bei Antworten den Reply-Endpoint nutzen (setzt parentEmail + In-Reply-To Header)
            const apiUrl = replyEmailId
                ? `/api/emails/${replyEmailId}/reply`
                : '/api/emails/send';

            const res = await fetch(apiUrl, {
                method: 'POST',
                body: formData,
            });

            if (!res.ok) {
                throw new Error('E-Mail senden fehlgeschlagen');
            }

            // Draft nach erfolgreichem Senden löschen
            await deleteDraft();

            // Check if the recipient email is new (not in known emails)
            const isNewEmail = finalRecipient && !availableEmails.some(
                e => e.toLowerCase() === finalRecipient.toLowerCase()
            );

            if (isNewEmail && (kundeId || projektId || anfrageId)) {
                setNewEmailToSave(finalRecipient);
                setShowSaveEmailDialog(true);
                // Don't close yet — wait for save dialog decision
            } else {
                if (onSuccess) onSuccess();
                onClose();
            }
        } catch (err) {
            console.error('E-Mail senden fehlgeschlagen:', err);
            setError('E-Mail konnte nicht gesendet werden. Bitte erneut versuchen.');
        } finally {
            setSending(false);
        }
    };

    // Save new email to a specific entity
    const handleSaveEmail = async (target: 'kunde' | 'projekt' | 'anfrage') => {
        if (!newEmailToSave) return;
        setSavingEmail(true);
        try {
            let url = '';
            if (target === 'kunde' && kundeId) {
                url = `/api/kunden/${kundeId}/emails`;
            } else if (target === 'projekt' && projektId) {
                url = `/api/projekte/${projektId}/emails`;
            } else if (target === 'anfrage' && anfrageId) {
                url = `/api/anfragen/${anfrageId}/emails`;
            }
            if (url) {
                await fetch(url, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ email: newEmailToSave }),
                });
            }
        } catch (err) {
            console.error('E-Mail-Adresse konnte nicht gespeichert werden:', err);
        } finally {
            setSavingEmail(false);
            setShowSaveEmailDialog(false);
            if (onSuccess) onSuccess();
            onClose();
        }
    };

    const handleSkipSaveEmail = () => {
        setShowSaveEmailDialog(false);
        if (onSuccess) onSuccess();
        onClose();
    };

    return (
        <div className="flex flex-col h-full bg-slate-50">
            {/* Header */}
            <div className="flex items-center justify-between gap-3 px-6 py-4 border-b border-slate-200 bg-rose-50 flex-shrink-0">
                <div className="flex min-w-0 items-center gap-3 flex-1">
                    <div className="w-11 h-11 rounded-full bg-white border border-rose-200 flex items-center justify-center shadow-sm flex-shrink-0">
                        <Mail className="w-5 h-5 text-rose-600" />
                    </div>
                    <div className="min-w-0 overflow-hidden">
                        <h2 className="text-lg font-semibold text-slate-900 truncate">{dialogTitle}</h2>
                        <p className="text-sm text-slate-500 truncate hidden sm:block">
                            {dialogSubtitle}
                        </p>
                    </div>
                </div>
                <div className="flex items-center gap-2 flex-shrink-0">
                    <Button
                        variant="ghost"
                        size="sm"
                        onClick={onClose}
                        aria-label="Fenster schließen"
                        className="text-slate-500 hover:text-slate-700"
                    >
                        <X className="w-5 h-5" />
                    </Button>
                </div>
            </div>

            {/* Scrollable Content */}
            <div className="flex-1 overflow-y-auto p-6 space-y-4">
                {error && (
                    <div className="p-3 bg-red-50 border border-red-200 rounded-lg text-red-700 text-sm">
                        {error}
                    </div>
                )}

                <div className="mx-auto w-full max-w-5xl space-y-5">
                    {hasInitialAttachments && (
                        <div className="rounded-2xl border border-rose-200 bg-rose-50 p-4 shadow-sm">
                            <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                                <div>
                                    <p className="text-sm font-semibold text-rose-700 flex items-center gap-2">
                                        <Paperclip className="w-4 h-4" />
                                        Automatisch angehängt
                                    </p>
                                    <p className="mt-1 text-sm text-rose-700/90 break-words">{initialAttachmentLabel}</p>
                                </div>
                                {firstPdfAttachment && (
                                    <Button
                                        type="button"
                                        variant="outline"
                                        size="sm"
                                        onClick={() => {
                                            const url = URL.createObjectURL(firstPdfAttachment.file);
                                            setPdfPreviewUrl(url);
                                        }}
                                        className="border-rose-300 text-rose-700 hover:bg-white"
                                    >
                                        <Eye className="w-4 h-4 mr-2" />
                                        PDF-Vorschau öffnen
                                    </Button>
                                )}
                            </div>
                        </div>
                    )}

                    <div className="rounded-2xl border border-slate-200 bg-white shadow-sm overflow-hidden">
                        <div className="border-b border-slate-100 bg-slate-50/80 px-5 py-3">
                            <p className="text-sm font-semibold text-slate-900">E-Mail-Details</p>
                            <p className="text-xs text-slate-500 mt-1">Empfänger, Absender und Betreff in einer kompakten Übersicht.</p>
                        </div>

                        <div className="p-5 flex flex-col gap-4">
                            <div className="space-y-2">
                                <div className="flex items-center justify-between gap-3">
                                    <Label>Empfänger *</Label>
                                    {!showCc && (
                                        <Button
                                            type="button"
                                            variant="ghost"
                                            size="sm"
                                            className="min-h-11 text-xs text-rose-600 hover:text-rose-700 hover:bg-rose-50 px-3"
                                            onClick={() => { setShowCc(true); setCcRecipients(['']); }}
                                        >
                                            + CC hinzufügen
                                        </Button>
                                    )}
                                </div>
                                <EmailRecipientInput
                                    value={recipient}
                                    onChange={handleRecipientChange}
                                    suggestions={availableEmails}
                                    placeholder="Name, Firma oder E-Mail eingeben"
                                    readOnly={!!replyEmailId}
                                />
                            </div>

                            <div className="space-y-2">
                                <Label>Von</Label>
                                <Select
                                    options={fromAddressOptions}
                                    value={fromAddress}
                                    onChange={(val) => { markDirty(); setFromAddress(val); }}
                                    placeholder="Absender wählen"
                                    disabled={fromAddressOptions.length === 0}
                                />
                            </div>

                            <div className="space-y-2">
                                <Label htmlFor="subject">Betreff</Label>
                                <Input
                                    id="subject"
                                    value={subject}
                                    onChange={(e) => { markDirty(); setSubject(e.target.value); }}
                                    placeholder="Betreff eingeben"
                                    className="font-medium"
                                />
                            </div>

                            {/* CC Section */}
                            {showCc && (
                                <div className="space-y-2 animate-in fade-in slide-in-from-top-2 duration-200">
                                    <div className="flex items-center justify-between gap-3">
                                        <Label>CC</Label>
                                        <Button
                                            type="button"
                                            variant="ghost"
                                            size="sm"
                                            onClick={() => {
                                                setShowCc(false);
                                                setCcRecipients([]);
                                            }}
                                            className="text-xs text-slate-500 hover:text-slate-700"
                                        >
                                            Keine
                                        </Button>
                                    </div>
                                    <div className="space-y-2">
                                        {ccRecipients.map((cc, idx) => (
                                            <EmailRecipientInput
                                                key={idx}
                                                value={cc}
                                                onChange={(val) => {
                                                    markDirty();
                                                    const newCcs = [...ccRecipients];
                                                    newCcs[idx] = val;
                                                    setCcRecipients(newCcs);
                                                }}
                                                onRemove={() => {
                                                    markDirty();
                                                    const newCcs = [...ccRecipients];
                                                    newCcs.splice(idx, 1);
                                                    setCcRecipients(newCcs);
                                                    if (newCcs.length === 0) setShowCc(false);
                                                }}
                                                suggestions={availableEmails}
                                                placeholder="CC Empfänger hinzufügen"
                                            />
                                        ))}
                                        <Button
                                            type="button"
                                            variant="outline"
                                            size="sm"
                                            onClick={() => setCcRecipients([...ccRecipients, ''])}
                                            className="text-xs min-h-11"
                                        >
                                            <Plus className="w-3 h-3 mr-1" />
                                            CC hinzufügen
                                        </Button>
                                    </div>
                                </div>
                            )}
                        </div>
                    </div>

                    {/* PDF Preview Modal */}
                    {pdfPreviewUrl && (
                        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
                            <div className="bg-white rounded-xl shadow-2xl w-[90%] max-w-4xl h-[85vh] flex flex-col">
                                <div className="flex justify-between items-center px-4 py-3 border-b border-slate-200">
                                    <h3 className="font-semibold text-slate-900">Dokument-Vorschau</h3>
                                    <Button
                                        variant="ghost"
                                        size="sm"
                                        onClick={() => setPdfPreviewUrl(null)}
                                        aria-label="Vorschau schließen"
                                    >
                                        <X className="w-5 h-5" />
                                    </Button>
                                </div>
                                <div className="flex-1 p-4 bg-slate-50 overflow-hidden">
                                    <PdfCanvasViewer
                                        url={pdfPreviewUrl}
                                        className="w-full h-full rounded overflow-y-auto overflow-x-hidden"
                                    />
                                </div>
                            </div>
                        </div>
                    )}

                    {imagePreview && (
                        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-6">
                            <div className="flex h-[85vh] w-full max-w-5xl flex-col overflow-hidden rounded-xl bg-white shadow-2xl">
                                <div className="flex items-center justify-between border-b border-slate-200 px-4 py-3">
                                    <h3 className="truncate pr-4 font-semibold text-slate-900">{imagePreview.name}</h3>
                                    <Button
                                        type="button"
                                        variant="ghost"
                                        size="sm"
                                        onClick={() => {
                                            URL.revokeObjectURL(imagePreview.url);
                                            setImagePreview(null);
                                        }}
                                        aria-label="Bildvorschau schließen"
                                    >
                                        <X className="h-5 w-5" />
                                    </Button>
                                </div>
                                <div className="flex flex-1 items-center justify-center overflow-hidden bg-slate-100 p-4">
                                    <img
                                        src={imagePreview.url}
                                        alt={imagePreview.name}
                                        className="max-h-full max-w-full rounded object-contain shadow"
                                    />
                                </div>
                            </div>
                        </div>
                    )}

                    <div className="rounded-2xl border border-slate-200 bg-white shadow-sm overflow-hidden">
                        <div className="border-b border-slate-100 bg-slate-50/80 px-5 py-3">
                            <p className="text-sm font-semibold text-slate-900 flex items-center gap-2">
                                <Upload className="w-4 h-4 text-rose-600" />
                                Anhänge
                            </p>
                            <p className="text-xs text-slate-500 mt-1">Zusätzliche Dateien per Klick oder Drag & Drop anhängen.</p>
                        </div>
                        <div className="p-5 space-y-3">
                            {entityId && (
                                <Button
                                    type="button"
                                    variant="outline"
                                    onClick={() => setShowEntityDokumente(true)}
                                    className="min-h-11 border-rose-200 text-rose-700 hover:bg-rose-50"
                                >
                                    <FolderOpen className="mr-2 h-4 w-4" />
                                    Dateien aus {isAnfrageContext ? 'Anfrage' : 'Projekt'} hinzufügen
                                </Button>
                            )}

                            {showEntityDokumente && (
                                <div className="fixed inset-0 z-[60] flex items-center justify-center bg-black/60 p-4">
                                    <div className="flex h-[82vh] w-full max-w-5xl flex-col overflow-hidden rounded-2xl bg-white shadow-2xl">
                                        <div className="flex items-center justify-between border-b border-slate-200 px-5 py-4">
                                            <div>
                                                <h3 className="font-semibold text-slate-900">
                                                    {isAnfrageContext ? 'Anfrage-Dateien' : 'Projekt-Dateien'}
                                                </h3>
                                                <p className="mt-1 text-xs text-slate-500">
                                                    Dateien anklicken, um sie als E-Mail-Anhang auszuwählen.
                                                </p>
                                            </div>
                                            <Button
                                                type="button"
                                                variant="ghost"
                                                size="sm"
                                                onClick={() => setShowEntityDokumente(false)}
                                                aria-label="Dateiauswahl schließen"
                                            >
                                                <X className="h-5 w-5" />
                                            </Button>
                                        </div>
                                        <div className="flex-1 overflow-y-auto p-4">
                                            <EmailEntityDocumentPicker
                                                documents={entityDokumente}
                                                loading={loadingEntityDokumente}
                                                selectedIds={selectedEntityDokumentIds}
                                                loadingIds={loadingEntityDokumentIds}
                                                onToggle={handleEntityDokumentToggle}
                                            />
                                        </div>
                                        <div className="flex items-center justify-between border-t border-slate-200 bg-slate-50 px-5 py-3">
                                            <span className="text-xs text-slate-500">
                                                {selectedEntityDokumentIds.size} Datei(en) ausgewählt
                                            </span>
                                            <Button type="button" onClick={() => setShowEntityDokumente(false)}>
                                                Auswahl übernehmen
                                            </Button>
                                        </div>
                                    </div>
                                </div>
                            )}
                            <div
                                onDrop={handleDrop}
                                onDragOver={handleDragOver}
                                className="border-2 border-dashed border-slate-300 rounded-xl p-5 text-center hover:border-rose-400 hover:bg-rose-50/50 transition-colors cursor-pointer"
                                onClick={() => fileInputRef.current?.click()}
                            >
                                <Upload className="w-8 h-8 mx-auto text-slate-400 mb-2" />
                                <p className="text-sm font-medium text-slate-700">Dateien hier ablegen oder klicken</p>
                                <p className="text-xs text-slate-500 mt-1">PDFs, Bilder und weitere Dokumente werden direkt als Anhang hinzugefügt.</p>
                            </div>
                            <input
                                ref={fileInputRef}
                                type="file"
                                multiple
                                className="hidden"
                                onChange={(e) => handleFileUpload(e.target.files)}
                            />

                            <div className="space-y-1">
                                <div className="flex items-center justify-between text-xs">
                                    <span className={attachmentLimitExceeded ? 'font-medium text-red-600' : 'text-slate-500'}>
                                        {formatFileSize(attachmentBytes)} von {formatFileSize(MAX_ATTACHMENT_BYTES)}
                                    </span>
                                    <span className="text-slate-400">T-Online-kompatibles Sicherheitslimit</span>
                                </div>
                                <div className="h-1.5 overflow-hidden rounded-full bg-slate-100">
                                    <div
                                        className={`h-full rounded-full ${attachmentLimitExceeded ? 'bg-red-500' : 'bg-rose-500'}`}
                                        style={{ width: `${Math.min(100, (attachmentBytes / MAX_ATTACHMENT_BYTES) * 100)}%` }}
                                    />
                                </div>
                            </div>

                            {uploadedFiles.length > 0 && (
                                <div className="space-y-2 mt-2">
                                    {uploadedFiles.map((uf, idx) => (
                                        <div key={idx} className="p-3 bg-slate-50 rounded-xl border border-slate-200 flex items-center justify-between gap-3">
                                            <div className="min-w-0">
                                                <span className="text-sm font-medium text-slate-700 block truncate">{uf.file.name}</span>
                                                <span className="text-xs text-slate-500">{formatFileSize(uf.file.size)}</span>
                                            </div>
                                            <div className="flex items-center gap-1">
                                                {(isPdfAttachment(uf.file) || isImageAttachment(uf.file)) && (
                                                    <Button
                                                        type="button"
                                                        variant="ghost"
                                                        size="sm"
                                                        onClick={() => {
                                                            const url = URL.createObjectURL(uf.file);
                                                            if (isImageAttachment(uf.file)) {
                                                                setImagePreview({ url, name: uf.file.name });
                                                            } else {
                                                                setPdfPreviewUrl(url);
                                                            }
                                                        }}
                                                        title="Vorschau"
                                                        aria-label={`Vorschau für ${uf.file.name}`}
                                                    >
                                                        <Eye className="w-4 h-4" />
                                                    </Button>
                                                )}
                                                <Button
                                                    type="button"
                                                    variant="ghost"
                                                    size="sm"
                                                    onClick={() => {
                                                        const newFiles = [...uploadedFiles];
                                                        newFiles.splice(idx, 1);
                                                        setUploadedFiles(newFiles);
                                                        markDirty();
                                                    }}
                                                    aria-label={`${uf.file.name} entfernen`}
                                                >
                                                    <X className="w-4 h-4" />
                                                </Button>
                                            </div>
                                        </div>
                                    ))}
                                </div>
                            )}
                        </div>
                    </div>

                    {/* Text Editor */}
                    <div className="rounded-2xl border border-slate-200 bg-white shadow-sm overflow-hidden flex flex-col min-h-[320px]">
                        <div className="border-b border-slate-200 bg-slate-50 px-5 py-3 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                            <div>
                                <Label className="text-slate-900">Nachricht</Label>
                                <p className="text-xs text-slate-500 mt-1">Formulieren, überarbeiten und anschließend direkt versenden.</p>
                            </div>
                            <AiButton
                                onClick={handleBeautify}
                                isLoading={beautifying}
                                label="KI-Optimierung"
                            />
                        </div>
                        <div className="flex-1 p-4 bg-white">
                            <div
                                ref={editorRef}
                                className="email-compose-editor h-full min-h-[240px] rounded-xl border border-slate-200 p-4 outline-none overflow-auto focus-within:border-rose-300 [&_p]:min-h-[1.25em] [&_p]:my-0 [&>p+p]:mt-2 [&>div+p]:mt-2 [&>p+div]:mt-2"
                                contentEditable
                                suppressContentEditableWarning
                                onInput={() => { markDirty(); setBody(editorRef.current?.innerHTML || ''); }}
                            />
                        </div>
                    </div>

                </div>
            </div>

            {/* Footer – immer sichtbar, damit "E-Mail senden" nie verdeckt wird */}
            <div className="px-6 py-4 border-t border-slate-200 bg-slate-50 flex items-center justify-between flex-shrink-0 relative z-10">
                <span className="text-xs text-slate-400">
                    {draftSaving ? 'Entwurf wird gespeichert…' : draftId ? 'Entwurf gespeichert' : ''}
                </span>
                <div className="flex gap-3">
                    <Button variant="outline" onClick={onClose} disabled={sending}>
                        Schließen
                    </Button>
                    <Button
                        onClick={handleSend}
                        disabled={sending || !recipient.trim() || !subject.trim() || attachmentLimitExceeded}
                        className="bg-rose-600 hover:bg-rose-700 text-white"
                    >
                        {sending ? <Loader2 className="w-4 h-4 animate-spin mr-2" /> : <Send className="w-4 h-4 mr-2" />}
                        {sending ? 'Wird gesendet...' : 'E-Mail senden'}
                    </Button>
                </div>
            </div>

            {/* Save Email Dialog */}
            {showSaveEmailDialog && (
                <div className="fixed inset-0 z-[70] flex items-center justify-center bg-black/50 backdrop-blur-sm">
                    <div className="bg-white rounded-xl shadow-2xl max-w-md w-full mx-4 p-6" onClick={e => e.stopPropagation()}>
                        <div className="flex items-center gap-3 mb-4">
                            <div className="w-10 h-10 rounded-full bg-rose-100 flex items-center justify-center">
                                <Save className="w-5 h-5 text-rose-600" />
                            </div>
                            <div>
                                <h3 className="text-lg font-semibold text-slate-900">E-Mail-Adresse speichern?</h3>
                                <p className="text-sm text-slate-500">
                                    <span className="font-medium text-slate-700">{newEmailToSave}</span> ist noch nicht gespeichert.
                                </p>
                            </div>
                        </div>
                        <p className="text-sm text-slate-600 mb-4">
                            Möchten Sie diese E-Mail-Adresse für zukünftige Verwendung speichern?
                        </p>
                        <div className="flex flex-col gap-2">
                            {kundeId && (
                                <Button
                                    onClick={() => handleSaveEmail('kunde')}
                                    disabled={savingEmail}
                                    className="w-full bg-rose-600 hover:bg-rose-700 text-white justify-start"
                                    size="sm"
                                >
                                    {savingEmail ? <Loader2 className="w-4 h-4 animate-spin mr-2" /> : <Mail className="w-4 h-4 mr-2" />}
                                    Beim Kunden speichern
                                </Button>
                            )}
                            {projektId && (
                                <Button
                                    onClick={() => handleSaveEmail('projekt')}
                                    disabled={savingEmail}
                                    className="w-full bg-rose-600 hover:bg-rose-700 text-white justify-start"
                                    size="sm"
                                >
                                    {savingEmail ? <Loader2 className="w-4 h-4 animate-spin mr-2" /> : <Mail className="w-4 h-4 mr-2" />}
                                    Als Projekt-E-Mail speichern
                                </Button>
                            )}
                            {anfrageId && (
                                <Button
                                    onClick={() => handleSaveEmail('anfrage')}
                                    disabled={savingEmail}
                                    className="w-full bg-rose-600 hover:bg-rose-700 text-white justify-start"
                                    size="sm"
                                >
                                    {savingEmail ? <Loader2 className="w-4 h-4 animate-spin mr-2" /> : <Mail className="w-4 h-4 mr-2" />}
                                    Als Anfrage-E-Mail speichern
                                </Button>
                            )}
                            <Button
                                variant="outline"
                                onClick={handleSkipSaveEmail}
                                disabled={savingEmail}
                                className="w-full border-slate-300 text-slate-700 justify-start"
                                size="sm"
                            >
                                <X className="w-4 h-4 mr-2" />
                                Nicht speichern
                            </Button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}
