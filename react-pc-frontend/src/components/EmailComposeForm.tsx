/* eslint-disable react-refresh/only-export-components */
import React, { useState, useEffect, useCallback, useRef, useMemo } from 'react';
import {
    Loader2, Paperclip, X, Plus, Mail, Upload, Eye, Save, Send, FileText
} from 'lucide-react';
import { Button } from './ui/button';
import { PdfCanvasViewer } from './ui/PdfCanvasViewer';
import { AiButton } from './ui/ai-button';
import { Input } from './ui/input';
import { Label } from './ui/label';
import { Select } from './ui/select-custom';
import type { ProjektDetail, ProjektDokument } from '../types';
import { EmailRecipientInput } from './EmailRecipientInput';

// Interface für hochgeladene externe Dateien
interface UploadedFile {
    file: File;
    previewUrl?: string;
}

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
    /** Pre-attached files (e.g. generated PDF from DocumentEditor) */
    initialAttachments?: File[];
    onSuccess?: () => void;
    variant?: 'default' | 'modal';
}

interface SignatureResponse {
    id?: number;
    html?: string;
}

interface EmailTemplateResponse {
    subject: string;
    body: string;
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

// Dokumenttyp aus geschaeftsdokumentart erkennen (matches backend Dokumenttyp enum)
const detectDokumentTyp = (art: string | undefined): string | null => {
    if (!art) return null;
    const lower = art.toLowerCase();
    if (lower.includes('stornorechnung') || lower.includes('storno')) return 'STORNORECHNUNG';
    if (lower.includes('schlussrechnung')) return 'SCHLUSSRECHNUNG';
    if (lower.includes('teilrechnung')) return 'TEILRECHNUNG';
    if (lower.includes('abschlagsrechnung')) return 'ABSCHLAGSRECHNUNG';
    if (lower.includes('zahlungserinnerung')) return 'ZAHLUNGSERINNERUNG';
    if (lower.includes('2. mahnung')) return 'ZWEITE_MAHNUNG';
    if (lower.includes('1. mahnung')) return 'ERSTE_MAHNUNG';
    if (lower.includes('mahnung')) return 'ERSTE_MAHNUNG';
    if (lower.includes('angebot')) return 'ANGEBOT';
    if (lower.includes('auftragsbestätigung') || lower.includes('auftragsbestaetigung')) return 'AUFTRAGSBESTAETIGUNG';
    return null;
};

// Betrag formatieren
const formatBetrag = (betrag: number | undefined): string => {
    if (betrag === undefined || betrag === null) return '';
    return new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR' }).format(betrag);
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
    initialAttachments,
    onSuccess,
    variant = 'default'
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

    // CC State
    const [ccRecipients, setCcRecipients] = useState<string[]>([]);
    const [showCc, setShowCc] = useState(false);

    // Dokumente
    const [emailDokumente, setEmailDokumente] = useState<ProjektDokument[]>([]);
    const [selectedDokument, setSelectedDokument] = useState<ProjektDokument | null>(null);
    const [loadingDokumente, setLoadingDokumente] = useState(false);
    const [pdfPreviewUrl, setPdfPreviewUrl] = useState<string | null>(null);

    // Externe hochgeladene Dateien
    const [uploadedFiles, setUploadedFiles] = useState<UploadedFile[]>(() => {
        if (initialAttachments && initialAttachments.length > 0) {
            return initialAttachments.map(file => ({ file }));
        }
        return [];
    });
    const fileInputRef = useRef<HTMLInputElement>(null);
    const editorRef = useRef<HTMLDivElement>(null);

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
    const dokumentOptions = useMemo(
        () => emailDokumente.map(dok => ({
            value: String(dok.id),
            label: `${dok.geschaeftsdokumentart ? `[${dok.geschaeftsdokumentart}] ` : ''}${dok.originalDateiname}${dok.rechnungsnummer ? ` (${dok.rechnungsnummer})` : ''}`,
        })),
        [emailDokumente]
    );

    // Handle recipient change
    const handleRecipientChange = (val: string) => {
        setRecipient(val);
    };

    // Signatur laden
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
            const res = await fetch('/api/email/from-addresses');
            if (!res.ok) return;
            const data = await res.json();
            const addresses = Array.isArray(data)
                ? data.filter((value): value is string => typeof value === 'string' && value.trim().length > 0)
                : [];
            setFromAddresses(addresses);
            if (addresses.length > 0) {
                setFromAddress(prev => prev || addresses[0]);
            }
        } catch (err) {
            console.error('Absender-Adressen konnten nicht geladen werden:', err);
        }
    }, []);

    // E-Mail-Dokumente laden (für Projekte UND Anfragen)
    const loadEmailDokumente = useCallback(async () => {
        if (!entityId) {
            setEmailDokumente([]);
            return;
        }
        setLoadingDokumente(true);
        try {
            // Unterschiedlicher Endpoint für Anfragen vs. Projekte
            const apiUrl = isAnfrageContext
                ? `/api/anfragen/${entityId}/email-dokumente`
                : `/api/projekte/${entityId}/email-dokumente`;
            const res = await fetch(apiUrl);
            if (res.ok) {
                const data: ProjektDokument[] = await res.json();
                setEmailDokumente(data);
            }
        } catch (err) {
            console.error('Dokumente konnten nicht geladen werden:', err);
        } finally {
            setLoadingDokumente(false);
        }
    }, [entityId, isAnfrageContext]);

    // Template laden basierend auf Dokument (für Projekt UND Anfrage)
    const loadTemplate = useCallback(async (dokument: ProjektDokument) => {
        const dokumentTyp = detectDokumentTyp(dokument.geschaeftsdokumentart);
        if (!dokumentTyp) return;

        try {
            const currentUser = getCurrentFrontendUser();

            let requestBody;
            if (isAnfrageContext && anfrage) {
                // Anfrage-Kontext: Daten aus Anfrage + Dokument
                // Anrede aus Kundendaten verwenden, nicht hardcoded
                const kundeAnrede = anredeEnumToText(anfrage.kundenAnrede);
                requestBody = {
                    dokumentTyp,
                    anrede: kundeAnrede,
                    kundenName: anfrage.kundenAnsprechpartner || anfrage.kundenName || '',
                    bauvorhaben: anfrage.bauvorhaben || '',
                    projektnummer: '',
                    dokumentnummer: dokument.rechnungsnummer || '',
                    betrag: formatBetrag(dokument.rechnungsbetrag),
                    benutzer: currentUser?.displayName || '',
                };
            } else if (effectiveProjekt) {
                // Projekt-Kontext (Prop oder vom Backend geladenes Projekt)
                const kundenName = effectiveProjekt.kundeDto?.ansprechspartner || effectiveProjekt.kundeDto?.name || effectiveProjekt.kunde || '';
                const kundeAnrede = anredeEnumToText(effectiveProjekt.kundeDto?.anrede);
                requestBody = {
                    dokumentTyp,
                    anrede: kundeAnrede,
                    kundenName,
                    bauvorhaben: effectiveProjekt.bauvorhaben || '',
                    projektnummer: effectiveProjekt.auftragsnummer || '',
                    dokumentnummer: dokument.rechnungsnummer || '',
                    rechnungsdatum: dokument.rechnungsdatum || '',
                    faelligkeitsdatum: dokument.faelligkeitsdatum || '',
                    betrag: formatBetrag(dokument.rechnungsbetrag),
                    benutzer: currentUser?.displayName || '',
                };
            } else {
                return; // Kein Kontext verfügbar
            }

            const res = await fetch('/api/email/template', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(requestBody),
            });

            if (res.ok) {
                const template: EmailTemplateResponse = await res.json();
                setSubject(template.subject);
                // Body setzen UND Editor aktualisieren
                const bodyContent = `${template.body}${signature}`;
                setBody(bodyContent);
                if (editorRef.current) {
                    editorRef.current.innerHTML = bodyContent;
                }
            }
        } catch (err) {
            console.error('Template konnte nicht geladen werden:', err);
        }
    }, [effectiveProjekt, anfrage, signature, isAnfrageContext]);

    // Initialisierung - nur einmal beim Mount
    useEffect(() => {
        if (!initialBody) {
            // Signatur laden wenn body leer
            loadSignature().then(sig => {
                // Für BEIDE Kontexte: Nur Signatur initial setzen, Template wird bei Dokument-Auswahl geladen
                const initialContent = `<p><br></p>${sig}`;
                setBody(initialContent);
                // Setze Editor-Inhalt via ref (nicht via dangerouslySetInnerHTML bei Re-Render)
                if (editorRef.current) {
                    editorRef.current.innerHTML = initialContent;
                }
            });
        } else {
            // initialBody vorhanden (z.B. aus DocumentEditor) → mit Signatur kombinieren
            loadSignature().then(sig => {
                const content = `${initialBody}${sig}`;
                setBody(content);
                if (editorRef.current) {
                    editorRef.current.innerHTML = content;
                }
            });
        }

        loadEmailDokumente();
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

    // Dokument-Auswahl Handler
    const handleDokumentSelect = (dokId: string) => {
        if (!dokId) {
            setSelectedDokument(null);
            return;
        }
        const dok = emailDokumente.find(d => String(d.id) === dokId);
        if (dok) {
            setSelectedDokument(dok);
            loadTemplate(dok);
        }
    };

    // Externe Dateien hochladen
    const handleFileUpload = (files: FileList | null) => {
        if (!files) return;

        const newFiles: UploadedFile[] = Array.from(files).map(file => {
            const isImage = file.type.startsWith('image/');
            return {
                file,
                previewUrl: isImage ? URL.createObjectURL(file) : undefined,
            };
        });

        setUploadedFiles(prev => [...prev, ...newFiles]);
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

            // 4. Alles wieder zusammensetzen: Optimierter Text + Zitat + Signatur
            const newContent = `${htmlSuggestion}${quoteHtml ? '<br>' + quoteHtml : ''}${sigHtml || signature}`;
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

        setSending(true);
        setError(null);

        try {
            const currentUser = getCurrentFrontendUser();

            // FormData für multipart request (mit Anhängen)
            const formData = new FormData();

            const dtoPayload = {
                sender: fromAddress || 'bauschlosserei-kuhn@t-online.de',
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

            if (selectedDokument) {
                formData.append('dokumentId', String(selectedDokument.id));
            }

            uploadedFiles.forEach((uf) => {
                formData.append('attachments', uf.file);
            });

            // Always use the unified send endpoint - assignment handled via DTO payload
            const apiUrl = '/api/emails/send';

            const res = await fetch(apiUrl, {
                method: 'POST',
                body: formData,
            });

            if (!res.ok) {
                throw new Error('E-Mail senden fehlgeschlagen');
            }

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
            <div className="flex items-center justify-between gap-4 px-6 py-4 border-b border-slate-200 bg-rose-50 flex-shrink-0">
                <div className="flex min-w-0 items-center gap-3">
                    <div className="w-11 h-11 rounded-full bg-white border border-rose-200 flex items-center justify-center shadow-sm">
                        <Mail className="w-5 h-5 text-rose-600" />
                    </div>
                    <div className="min-w-0">
                        <h2 className="text-lg font-semibold text-slate-900 truncate">{dialogTitle}</h2>
                        <p className="text-sm text-slate-500 truncate">
                            {dialogSubtitle}
                        </p>
                    </div>
                </div>
                <div className="flex items-center gap-2 flex-shrink-0">
                    {variant === 'modal' && (
                        <>
                            <Button variant="ghost" onClick={onClose} disabled={sending} className="hidden sm:inline-flex">
                                Abbrechen
                            </Button>
                            <Button
                                onClick={handleSend}
                                disabled={sending || !recipient.trim() || !subject.trim()}
                                className="bg-rose-600 hover:bg-rose-700 text-white"
                            >
                                {sending ? <Loader2 className="w-4 h-4 animate-spin mr-2" /> : <Send className="w-4 h-4 mr-2" />}
                                {sending ? 'Wird gesendet...' : 'E-Mail senden'}
                            </Button>
                        </>
                    )}
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

                        <div className="p-5 grid grid-cols-1 md:grid-cols-2 gap-4">
                            <div className="space-y-2 md:col-span-2">
                                <div className="flex items-center justify-between gap-3">
                                    <Label>Empfänger *</Label>
                                    {!showCc && (
                                        <Button
                                            type="button"
                                            variant="ghost"
                                            size="sm"
                                            className="min-h-11 text-xs text-rose-600 hover:text-rose-700 hover:bg-rose-50 px-3"
                                            onClick={() => setShowCc(true)}
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
                                />
                            </div>

                            <div className="space-y-2">
                                <Label htmlFor="subject">Betreff</Label>
                                <Input
                                    id="subject"
                                    value={subject}
                                    onChange={(e) => setSubject(e.target.value)}
                                    placeholder="Betreff eingeben"
                                    className="font-medium"
                                />
                            </div>

                            <div className="space-y-2">
                                <Label>Von</Label>
                                <Select
                                    options={fromAddressOptions}
                                    value={fromAddress}
                                    onChange={setFromAddress}
                                    placeholder="Absender wählen"
                                    disabled={fromAddressOptions.length === 0}
                                />
                            </div>

                            {/* CC Section */}
                            {showCc && (
                                <div className="space-y-2 md:col-span-2 animate-in fade-in slide-in-from-top-2 duration-200">
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
                                                    const newCcs = [...ccRecipients];
                                                    newCcs[idx] = val;
                                                    setCcRecipients(newCcs);
                                                }}
                                                onRemove={() => {
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

                            {/* Dokument Auswahl - für Projekt UND Anfrage Kontext (ausgeblendet wenn initialAttachments vorhanden) */}
                            {entityId && !hasInitialAttachments && (
                                <div className="space-y-2 md:col-span-2">
                                    <Label className="flex items-center gap-2 text-slate-700">
                                        <FileText className="w-4 h-4" />
                                        Dokument aus {isAnfrageContext ? 'Anfrage' : 'Projekt'} anhängen
                                    </Label>
                                    <div className="flex flex-col gap-2 sm:flex-row">
                                        <div className="flex-1">
                                            <Select
                                                options={[{ value: '', label: 'Kein Dokument' }, ...dokumentOptions]}
                                                value={selectedDokument ? String(selectedDokument.id) : ''}
                                                onChange={handleDokumentSelect}
                                                placeholder={loadingDokumente ? 'Dokumente werden geladen...' : 'Dokument wählen'}
                                                disabled={loadingDokumente}
                                            />
                                        </div>
                                        {selectedDokument && (
                                            <Button
                                                type="button"
                                                variant="outline"
                                                size="sm"
                                                onClick={() => {
                                                    const filename = selectedDokument.gespeicherterDateiname || selectedDokument.originalDateiname;
                                                    const previewUrl = `/api/dokumente/${encodeURIComponent(filename)}`;
                                                    setPdfPreviewUrl(previewUrl);
                                                }}
                                                title="Dokument ansehen"
                                                className="min-h-11 px-4"
                                            >
                                                <Eye className="w-4 h-4 mr-2" />
                                                Vorschau
                                            </Button>
                                        )}
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

                    <div className="rounded-2xl border border-slate-200 bg-white shadow-sm overflow-hidden">
                        <div className="border-b border-slate-100 bg-slate-50/80 px-5 py-3">
                            <p className="text-sm font-semibold text-slate-900 flex items-center gap-2">
                                <Upload className="w-4 h-4 text-rose-600" />
                                Anhänge
                            </p>
                            <p className="text-xs text-slate-500 mt-1">Zusätzliche Dateien per Klick oder Drag & Drop anhängen.</p>
                        </div>
                        <div className="p-5 space-y-3">
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

                            {uploadedFiles.length > 0 && (
                                <div className="space-y-2 mt-2">
                                    {uploadedFiles.map((uf, idx) => (
                                        <div key={idx} className="p-3 bg-slate-50 rounded-xl border border-slate-200 flex items-center justify-between gap-3">
                                            <div className="min-w-0">
                                                <span className="text-sm font-medium text-slate-700 block truncate">{uf.file.name}</span>
                                                <span className="text-xs text-slate-500">{Math.max(1, Math.round(uf.file.size / 1024))} KB</span>
                                            </div>
                                            <div className="flex items-center gap-1">
                                                {uf.file.type === 'application/pdf' && (
                                                    <Button
                                                        type="button"
                                                        variant="ghost"
                                                        size="sm"
                                                        onClick={() => {
                                                            const url = URL.createObjectURL(uf.file);
                                                            setPdfPreviewUrl(url);
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
                                className="h-full min-h-[240px] rounded-xl border border-slate-200 p-4 outline-none overflow-auto focus-within:border-rose-300"
                                contentEditable
                                suppressContentEditableWarning
                            />
                        </div>
                    </div>

                </div>
            </div>

            {/* Footer - Only show if variant is default */}
            {variant !== 'modal' && (
                <div className="px-6 py-4 border-t border-slate-200 bg-slate-50 flex justify-end gap-3 flex-shrink-0">
                    <Button variant="outline" onClick={onClose} disabled={sending}>
                        Abbrechen
                    </Button>
                    <Button
                        onClick={handleSend}
                        disabled={sending || !recipient.trim() || !subject.trim()}
                        className="bg-rose-600 hover:bg-rose-700 text-white"
                    >
                        {sending ? <Loader2 className="w-4 h-4 animate-spin mr-2" /> : <Send className="w-4 h-4 mr-2" />}
                        {sending ? 'Wird gesendet...' : 'E-Mail senden'}
                    </Button>
                </div>
            )}

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
