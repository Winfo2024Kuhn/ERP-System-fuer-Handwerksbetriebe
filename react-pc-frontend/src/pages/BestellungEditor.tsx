import { useCallback, useEffect, useMemo, useState, useRef } from "react";
import { PdfCanvasViewer } from "../components/ui/PdfCanvasViewer";
import {
    ChevronDown,
    ChevronRight,
    Download,
    ExternalLink,
    Eye,
    File,
    Folder,
    FolderOpen,
    Loader2,
    Mail,
    Package,
    Paperclip,
    Plus,
    RefreshCw,
    Send,
    ShieldCheck,
    Trash2,
    Upload,
    X,
} from "lucide-react";
import { Button } from "../components/ui/button";
import { AiButton } from "../components/ui/ai-button";
import { Card } from "../components/ui/card";
import { PageLayout } from '../components/layout/PageLayout';
import { Select } from '../components/ui/select-custom';
import { cn } from "../lib/utils";
import { Input } from "../components/ui/input";
import { useToast } from '../components/ui/toast';
import { useConfirm } from '../components/ui/confirm-dialog';

// Statische Icon-Pfade
const BASE_URL = '/react-textbausteine/';
const ICON_PDF = `${BASE_URL}pdf_icon.jpg`;
const ICON_EXCEL = `${BASE_URL}excel_image.jpg`;
const ICON_TENADO = `${BASE_URL}tenado_logo.jpg`;
const ICON_HICAD = `${BASE_URL}hicad_logo.png`;

// Dateinamens-Hilfsfunktionen
const getFileExtension = (filename: string): string => {
    return filename.split('.').pop()?.toLowerCase() || '';
};

const isImageFile = (filename: string): boolean => {
    const ext = getFileExtension(filename);
    return ['jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp'].includes(ext);
};

const isPdfFile = (filename: string): boolean => {
    return getFileExtension(filename) === 'pdf';
};

const getFileIconUrl = (filename: string): string | null => {
    const ext = getFileExtension(filename);
    if (ext === 'pdf') return ICON_PDF;
    if (['xlsx', 'xls', 'xlsm', 'xlsb'].includes(ext)) return ICON_EXCEL;
    if (ext === 'tcd') return ICON_TENADO;
    if (ext === 'sza') return ICON_HICAD;
    return null;
};

// Interface für hochgeladene Dateien
interface UploadedFile {
    file: File;
    previewUrl?: string;
}

// Frontend-Profil aus localStorage
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

// Signatur-Wrapper
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

// ==================== TYPES ====================
interface Bestellung {
    id: number;
    artikelId: number;
    externeArtikelnummer?: string;
    produktname?: string;
    produkttext?: string;
    werkstoffName?: string;
    kategorieName?: string;
    rootKategorieId?: number;
    rootKategorieName?: string;
    stueckzahl: number;
    menge?: number;
    einheit?: string;
    projektId?: number;
    projektName?: string;
    projektNummer?: string;
    kundenName?: string;
    lieferantName?: string;
    lieferantId?: number;
    bestellt: boolean;
    bestelltAm?: string;
    kommentar?: string;
    kilogramm?: number;
    gesamtKilogramm?: number;
    schnittForm?: string;
    anschnittWinkelLinks?: string;
    anschnittWinkelRechts?: string;
}

interface LieferantGruppe {
    lieferantId: number | null;
    lieferantName: string;
    items: Bestellung[];
}

// ==================== ATTACHMENT PREVIEW MODAL ====================
interface AttachmentPreviewModalProps {
    file: UploadedFile | null;
    onClose: () => void;
}

const AttachmentPreviewModal: React.FC<AttachmentPreviewModalProps> = ({ file, onClose }) => {
    if (!file) return null;

    const filename = file.file.name;
    const isImage = isImageFile(filename);
    const isPdf = isPdfFile(filename);
    const iconUrl = getFileIconUrl(filename);

    return (
        <div className="fixed inset-0 z-[60] flex items-center justify-center bg-black/70 backdrop-blur-sm">
            <div className="bg-white rounded-2xl shadow-2xl w-full max-w-4xl mx-4 max-h-[90vh] overflow-hidden flex flex-col" onClick={e => e.stopPropagation()}>
                <div className="flex items-center justify-between px-6 py-4 border-b border-slate-200">
                    <h3 className="font-semibold text-slate-900 truncate">{filename}</h3>
                    <Button variant="ghost" size="sm" onClick={onClose}>
                        <X className="w-5 h-5" />
                    </Button>
                </div>
                <div className="flex-1 overflow-auto p-6 flex items-center justify-center bg-slate-100">
                    {isImage && file.previewUrl && (
                        <img src={file.previewUrl} alt={filename} className="max-w-full max-h-[70vh] object-contain rounded-lg shadow" />
                    )}
                    {isPdf && file.previewUrl && (
                        <PdfCanvasViewer url={file.previewUrl} className="w-full h-[70vh] rounded-lg overflow-y-auto overflow-x-hidden" />
                    )}
                    {!isImage && !isPdf && (
                        <div className="text-center">
                            {iconUrl ? (
                                <img src={iconUrl} alt="Icon" className="w-24 h-24 mx-auto mb-4 object-contain" />
                            ) : (
                                <File className="w-24 h-24 mx-auto mb-4 text-slate-400" />
                            )}
                            <p className="text-slate-600">Vorschau nicht verfügbar</p>
                            <p className="text-sm text-slate-400 mt-2">{filename}</p>
                        </div>
                    )}
                </div>
            </div>
        </div>
    );
};

// ==================== EMAIL MODAL ====================
interface BestellungEmailModalProps {
    isOpen: boolean;
    onClose: () => void;
    lieferantId: number;
    lieferantName: string;
    onSuccess: () => void;
}

const BestellungEmailModal: React.FC<BestellungEmailModalProps> = ({
    isOpen,
    onClose,
    lieferantId,
    lieferantName,
    onSuccess,
}) => {
    const toast = useToast();
    const confirmDialog = useConfirm();
    const editorRef = useRef<HTMLDivElement>(null);
    const fileInputRef = useRef<HTMLInputElement>(null);

    const [lieferantenEmails, setLieferantenEmails] = useState<string[]>([]);
    const [recipient, setRecipient] = useState('');
    const [customRecipient, setCustomRecipient] = useState('');
    const [showCustomRecipient, setShowCustomRecipient] = useState(false);
    const [cc, setCc] = useState('');
    const [ccManual, setCcManual] = useState('');
    const [fromAddress, setFromAddress] = useState('');
    const [fromAddresses, setFromAddresses] = useState<string[]>([]);
    const [subject, setSubject] = useState('');
    const [body, setBody] = useState('');
    const [signature, setSignature] = useState('');
    const [sending, setSending] = useState(false);
    const [beautifying, setBeautifying] = useState(false);
    const [uploadedFiles, setUploadedFiles] = useState<UploadedFile[]>([]);
    const [previewFile, setPreviewFile] = useState<UploadedFile | null>(null);

    const pdfPreviewUrl = `/api/bestellungen/lieferant/${lieferantId}/pdf`;

    // Signatur laden
    const loadSignature = useCallback(async () => {
        const user = getCurrentFrontendUser();
        if (!user) return '';
        try {
            const res = await fetch(`/api/email/signatures/default?frontendUserId=${user.id}`);
            if (!res.ok) return '';
            const data = await res.json();
            const rawHtml = data?.html || '';
            return wrapSignatureHtml(rawHtml);
        } catch {
            return '';
        }
    }, []);

    // Modal initialisieren
    useEffect(() => {
        if (!isOpen) return;

        // Reset
        setSubject(`Bestellanfrage: ${lieferantName}`);
        setSending(false);
        setBeautifying(false);
        setUploadedFiles([]);
        setPreviewFile(null);
        setShowCustomRecipient(false);
        setCustomRecipient('');
        setCc('');
        setCcManual('');
        setLieferantenEmails([]);
        setRecipient('');

        // Lieferanten-spezifische E-Mails laden
        fetch(`/api/lieferanten/${lieferantId}`)
            .then(async res => {
                if (!res.ok) {
                    throw new Error('Lieferant konnte nicht geladen werden');
                }
                return res.json();
            })
            .then(data => {
                const emails = Array.isArray(data?.kundenEmails)
                    ? data.kundenEmails.filter((value: unknown) => typeof value === 'string' && value.trim().length > 0)
                    : [];
                setLieferantenEmails(emails);
                if (emails.length > 0) {
                    setRecipient(emails[0]);
                    setShowCustomRecipient(false);
                } else {
                    setShowCustomRecipient(true);
                }
            })
            .catch((error) => {
                console.error(error);
                setShowCustomRecipient(true);
            });

        // Absender-Adressen laden
        fetch('/api/email/from-addresses')
            .then(res => res.json())
            .then(data => {
                const addresses = Array.isArray(data) ? data : [];
                setFromAddresses(addresses);
                if (addresses.length > 0) setFromAddress(addresses[0]);
            })
            .catch(console.error);

        // Signatur laden und Body initialisieren
        loadSignature().then(sig => {
            setSignature(sig);
            const initialBody = `<p>Sehr geehrte Damen und Herren,</p><p><br></p><p>bitte erstellen Sie uns ein Angebot für die angehängten Positionen.</p><p><br></p>${sig}`;
            setBody(initialBody);
        });
    }, [isOpen, lieferantId, lieferantName, loadSignature]);

    // AI Verschönerung
    const handleBeautify = async () => {
        if (!editorRef.current) return;
        const currentContent = editorRef.current.innerHTML;

        // Extrahiere nur den Text vor der Signatur
        const sigIndex = currentContent.indexOf('email-signature');
        const textToBeautify = sigIndex > -1
            ? currentContent.substring(0, currentContent.lastIndexOf('<div', sigIndex))
            : currentContent;

        setBeautifying(true);
        try {
            const res = await fetch('/api/email/beautify', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ text: textToBeautify }),
            });
            if (!res.ok) throw new Error('Beautify failed');
            const data = await res.json();
            const beautified = data.suggestion || data.beautifiedText || '';
            if (beautified) {
                const newBody = `${beautified}${signature}`;
                setBody(newBody);
            }
        } catch (err) {
            console.error('Beautify error:', err);
            toast.error('Formulierung fehlgeschlagen. Bitte erneut versuchen.');
        } finally {
            setBeautifying(false);
        }
    };

    // Datei-Upload Handler
    const handleFileUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
        const files = e.target.files;
        if (!files) return;

        const newFiles: UploadedFile[] = [];
        for (let i = 0; i < files.length; i++) {
            const file = files[i];
            const previewUrl = (isImageFile(file.name) || isPdfFile(file.name))
                ? URL.createObjectURL(file)
                : undefined;
            newFiles.push({ file, previewUrl });
        }
        setUploadedFiles(prev => [...prev, ...newFiles]);
        e.target.value = '';
    };

    // Datei entfernen
    const removeFile = (index: number) => {
        setUploadedFiles(prev => {
            const updated = [...prev];
            if (updated[index].previewUrl) {
                URL.revokeObjectURL(updated[index].previewUrl!);
            }
            updated.splice(index, 1);
            return updated;
        });
    };

    // Senden
    const handleSend = async () => {
        const finalRecipient = (showCustomRecipient ? customRecipient : recipient).trim();
        if (!finalRecipient) {
            toast.warning('Bitte Empfänger auswählen oder eingeben');
            return;
        }
        if (!subject.trim()) {
            toast.warning('Bitte Betreff eingeben');
            return;
        }

        setSending(true);

        try {
            const user = getCurrentFrontendUser();
            const formData = new FormData();

            const dtoPayload = {
                sender: fromAddress || 'bauschlosserei-kuhn@t-online.de',
                recipients: [finalRecipient],
                cc: (cc === 'manual' ? ccManual : cc)
                    .split(',')
                    .map(value => value.trim())
                    .filter(Boolean),
                subject: subject.trim(),
                body: prepareHtmlForSending(editorRef.current?.innerHTML || body),
                direction: 'OUT',
                benutzer: user?.displayName || '',
                frontendUserId: user?.id || null,
                lieferantId,
            };

            formData.append('dto', new Blob([JSON.stringify(dtoPayload)], { type: 'application/json' }));

            const bestellungPdfRes = await fetch(pdfPreviewUrl);
            if (!bestellungPdfRes.ok) {
                throw new Error('Bestell-PDF konnte nicht geladen werden');
            }
            const bestellungPdfBlob = await bestellungPdfRes.blob();
            const safeLieferantName = (lieferantName || 'lieferant').replace(/[^a-zA-Z0-9äöüÄÖÜß]+/g, '_');
            formData.append('attachments', bestellungPdfBlob, `Bestellung_${safeLieferantName}.pdf`);

            // Zusätzliche Anhänge
            uploadedFiles.forEach(uf => {
                formData.append('attachments', uf.file);
            });

            const res = await fetch('/api/emails/send', {
                method: 'POST',
                body: formData,
            });

            if (!res.ok) throw new Error('Senden fehlgeschlagen');

            const isNewEmail = !lieferantenEmails.some(
                email => email.toLowerCase() === finalRecipient.toLowerCase()
            );

            if (isNewEmail) {
                const shouldSave = await confirmDialog({
                    title: 'E-Mail-Adresse speichern?',
                    message: `Soll die Adresse ${finalRecipient} beim Lieferanten ${lieferantName} gespeichert werden?`,
                    confirmLabel: 'Speichern',
                    cancelLabel: 'Nicht speichern',
                    variant: 'info',
                });

                if (shouldSave) {
                    const saveRes = await fetch(`/api/lieferanten/${lieferantId}/emails`, {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ email: finalRecipient }),
                    });

                    if (saveRes.ok) {
                        toast.success('E-Mail versendet und Adresse gespeichert');
                    } else {
                        toast.warning('E-Mail versendet, Adresse konnte nicht gespeichert werden');
                    }
                } else {
                    toast.success('E-Mail versendet');
                }
            } else {
                toast.success('E-Mail versendet');
            }

            onSuccess();
            onClose();
        } catch (err) {
            console.error('Send error:', err);
            toast.error('Fehler beim Senden');
        } finally {
            setSending(false);
        }
    };

    // Cleanup
    useEffect(() => {
        return () => {
            uploadedFiles.forEach(uf => {
                if (uf.previewUrl) URL.revokeObjectURL(uf.previewUrl);
            });
        };
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    if (!isOpen) return null;

    return (
        <>
            <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm">
                <div className="bg-white rounded-2xl shadow-2xl w-full max-w-5xl mx-4 h-[90vh] overflow-hidden flex flex-col" onClick={e => e.stopPropagation()}>
                    <div className="flex items-center justify-between px-6 py-4 border-b border-slate-200 bg-rose-50 shrink-0">
                        <div className="flex items-center gap-3">
                            <Mail className="w-5 h-5 text-rose-600" />
                            <h2 className="text-lg font-semibold text-slate-900">
                                Bestellung an {lieferantName} senden
                            </h2>
                        </div>
                        <div className="flex items-center gap-2">
                             <Button variant="ghost" onClick={onClose} disabled={sending}>
                                 Abbrechen
                             </Button>
                             <Button
                                 onClick={handleSend}
                                 disabled={sending || (!recipient && !customRecipient)}
                                 className="bg-rose-600 text-white hover:bg-rose-700"
                             >
                                 {sending ? (
                                     <Loader2 className="w-4 h-4 mr-2 animate-spin" />
                                 ) : (
                                     <Send className="w-4 h-4 mr-2" />
                                 )}
                                 {sending ? 'Senden...' : 'E-Mail senden'}
                             </Button>
                            <Button variant="ghost" size="sm" onClick={onClose}>
                                <X className="w-5 h-5" />
                            </Button>
                        </div>
                    </div>

                    <div className="flex-1 overflow-auto p-6 space-y-4">
                        {/* Bestellungs-PDF automatisch angehängt */}
                        <div className="p-3 bg-rose-50 border border-rose-200 rounded-lg">
                            <p className="text-sm text-rose-700 font-medium mb-2">
                                <Paperclip className="w-4 h-4 inline-block mr-1" />
                                Automatisch angehängt: Bestellungs-PDF
                            </p>
                            <a
                                href={pdfPreviewUrl}
                                target="_blank"
                                rel="noopener noreferrer"
                                className="inline-flex items-center gap-2 text-rose-600 hover:underline text-sm"
                            >
                                <Eye className="w-4 h-4" />
                                PDF-Vorschau öffnen
                                <ExternalLink className="w-3 h-3" />
                            </a>
                        </div>

                        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                            {/* Empfänger */}
                            <div>
                                <label className="block text-sm font-medium text-slate-700 mb-1">Empfänger *</label>
                                {!showCustomRecipient ? (
                                    <div className="space-y-2">
                                        <Select
                                            value={recipient}
                                            onChange={(value) => {
                                                if (value === '__custom__') {
                                                    setShowCustomRecipient(true);
                                                } else {
                                                    setRecipient(value);
                                                }
                                            }}
                                            options={[
                                                ...lieferantenEmails.map(email => ({ value: email, label: email })),
                                                { value: '__custom__', label: 'Andere E-Mail eingeben...' }
                                            ]}
                                            placeholder="Empfänger wählen"
                                        />
                                    </div>
                                ) : (
                                    <div className="flex gap-2">
                                        <Input
                                            value={customRecipient}
                                            onChange={e => setCustomRecipient(e.target.value)}
                                            placeholder="E-Mail-Adresse eingeben"
                                            className="flex-1"
                                        />
                                        <Button variant="outline" size="sm" onClick={() => setShowCustomRecipient(false)}>
                                            Zurück
                                        </Button>
                                    </div>
                                )}
                            </div>

                            {/* CC */}
                            <div>
                                <label className="block text-sm font-medium text-slate-700 mb-1">CC</label>
                                <Select
                                    value={cc}
                                    onChange={(value) => setCc(value)}
                                    options={[
                                        { value: '', label: 'Keine' },
                                        ...lieferantenEmails.map(email => ({ value: email, label: email })),
                                        { value: 'manual', label: 'Manuell eingeben' }
                                    ]}
                                    placeholder="CC wählen"
                                />
                                {cc === 'manual' && (
                                    <Input
                                        value={ccManual}
                                        onChange={e => setCcManual(e.target.value)}
                                        placeholder="CC E-Mail-Adresse"
                                        className="mt-2"
                                    />
                                )}
                            </div>

                            {/* Von */}
                            <div>
                                <label className="block text-sm font-medium text-slate-700 mb-1">Von</label>
                                <Select
                                    value={fromAddress}
                                    onChange={(value) => setFromAddress(value)}
                                    options={fromAddresses.map(addr => ({ value: addr, label: addr }))}
                                    placeholder="Absender wählen"
                                />
                            </div>

                            {/* Betreff */}
                            <div>
                                <label className="block text-sm font-medium text-slate-700 mb-1">Betreff</label>
                                <Input
                                    value={subject}
                                    onChange={e => setSubject(e.target.value)}
                                    placeholder="Betreff"
                                />
                            </div>
                        </div>

                        {/* E-Mail Body Editor */}
                        <div>
                            <div className="flex items-center justify-between mb-1">
                                <label className="block text-sm font-medium text-slate-700">Nachricht</label>
                                <AiButton 
                                    onClick={handleBeautify}
                                    isLoading={beautifying}
                                    label="KI Formulierung"
                                />
                            </div>
                            <div
                                ref={editorRef}
                                contentEditable
                                suppressContentEditableWarning
                                dangerouslySetInnerHTML={{ __html: body }}
                                onBlur={() => setBody(editorRef.current?.innerHTML || '')}
                                className="min-h-[250px] p-4 border border-slate-200 rounded-lg bg-white focus:outline-none focus:ring-2 focus:ring-rose-500 prose prose-sm max-w-none"
                            />
                        </div>

                        {/* Zusätzliche Anhänge */}
                        <div>
                            <div className="flex items-center justify-between mb-2">
                                <label className="block text-sm font-medium text-slate-700">
                                    <Paperclip className="w-4 h-4 inline-block mr-1" />
                                    Zusätzliche Anhänge
                                </label>
                                <Button
                                    variant="outline"
                                    size="sm"
                                    onClick={() => fileInputRef.current?.click()}
                                >
                                    <Upload className="w-4 h-4 mr-1" />
                                    Dateien hinzufügen
                                </Button>
                                <input
                                    ref={fileInputRef}
                                    type="file"
                                    multiple
                                    onChange={handleFileUpload}
                                    className="hidden"
                                    title="Dateien als zusätzlichen Anhang auswählen"
                                    aria-label="Dateien als zusätzlichen Anhang auswählen"
                                />
                            </div>

                            {uploadedFiles.length > 0 && (
                                <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-3 mt-2">
                                    {uploadedFiles.map((uf, idx) => {
                                        const iconUrl = getFileIconUrl(uf.file.name);
                                        const isImg = isImageFile(uf.file.name);
                                        return (
                                            <div key={idx} className="relative group border border-slate-200 rounded-lg p-2 bg-slate-50">
                                                <div
                                                    className="w-full h-16 flex items-center justify-center cursor-pointer"
                                                    onClick={() => setPreviewFile(uf)}
                                                >
                                                    {isImg && uf.previewUrl ? (
                                                        <img src={uf.previewUrl} alt="" className="max-h-full max-w-full object-contain rounded" />
                                                    ) : iconUrl ? (
                                                        <img src={iconUrl} alt="" className="h-12 object-contain" />
                                                    ) : (
                                                        <File className="w-10 h-10 text-slate-400" />
                                                    )}
                                                </div>
                                                <p className="text-xs text-slate-600 truncate mt-1 text-center">{uf.file.name}</p>
                                                <div className="absolute top-1 right-1 flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                                                    <button
                                                        onClick={() => setPreviewFile(uf)}
                                                        className="p-1 bg-white rounded shadow hover:bg-slate-100"
                                                        title="Vorschau"
                                                    >
                                                        <Eye className="w-3 h-3 text-slate-600" />
                                                    </button>
                                                    <button
                                                        onClick={() => removeFile(idx)}
                                                        className="p-1 bg-white rounded shadow hover:bg-red-100"
                                                        title="Entfernen"
                                                    >
                                                        <Trash2 className="w-3 h-3 text-red-600" />
                                                    </button>
                                                </div>
                                            </div>
                                        );
                                    })}
                                </div>
                            )}
                        </div>
                    </div>


                </div>
            </div>

            {/* Attachment Preview Modal */}
            <AttachmentPreviewModal file={previewFile} onClose={() => setPreviewFile(null)} />
        </>
    );
};

// ==================== MANUELLE BESTELLPOSITION MODAL ====================

interface KategorieFlach {
    id: number;
    beschreibung: string;
    parentId: number | null;
    zeugnisExc1: string | null;
    zeugnisExc2: string | null;
    zeugnisExc3: string | null;
    zeugnisExc4: string | null;
}

interface ProjektSimple {
    id: number;
    bauvorhaben: string;
    auftragsnummer: string;
    abgeschlossen: boolean;
    excKlasse: string | null;
}

interface LieferantSimple {
    id: number;
    lieferantenname: string;
}

const ZEUGNIS_OPTIONEN = [
    { value: '', label: '— Kein Zeugnis —' },
    { value: 'WZ_2_1', label: 'Werkszeugnis 2.1 (EN 10204)' },
    { value: 'WZ_2_2', label: 'Werkszeugnis 2.2 (EN 10204)' },
    { value: 'APZ_3_1', label: 'Abnahmeprüfzeugnis 3.1 (EN 10204)' },
    { value: 'APZ_3_2', label: 'Abnahmeprüfzeugnis 3.2 (EN 10204)' },
    { value: 'CE_KONFORMITAET', label: 'CE-Kennzeichnung / Konformitätserklärung' },
];

const EXC_LABEL: Record<string, string> = {
    EXC_1: 'EXC 1', EXC_2: 'EXC 2', EXC_3: 'EXC 3', EXC_4: 'EXC 4',
};

// Baut einen Kategorie-Baum aus flacher Liste
function buildKategorieTree(flach: KategorieFlach[]): Map<number | null, KategorieFlach[]> {
    const map = new Map<number | null, KategorieFlach[]>();
    flach.forEach(k => {
        const list = map.get(k.parentId) ?? [];
        list.push(k);
        map.set(k.parentId, list);
    });
    return map;
}

interface KategorieTreeProps {
    tree: Map<number | null, KategorieFlach[]>;
    parentId: number | null;
    selected: KategorieFlach | null;
    onSelect: (k: KategorieFlach) => void;
    depth?: number;
}

const KategorieTree: React.FC<KategorieTreeProps> = ({ tree, parentId, selected, onSelect, depth = 0 }) => {
    const kinder = tree.get(parentId) ?? [];
    const [offen, setOffen] = useState<Set<number>>(new Set());

    const toggle = (id: number) => {
        setOffen(prev => {
            const next = new Set(prev);
            next.has(id) ? next.delete(id) : next.add(id);
            return next;
        });
    };

    return (
        <ul className="space-y-0.5">
            {kinder.map(k => {
                const hatKinder = (tree.get(k.id) ?? []).length > 0;
                const istOffen = offen.has(k.id);
                const istGewaehlt = selected?.id === k.id;
                return (
                    <li key={k.id}>
                        <div
                            className={cn(
                                "flex items-center gap-2 px-2 py-1.5 rounded-lg cursor-pointer text-sm select-none",
                                depth === 0 && "pl-2",
                                depth === 1 && "pl-6",
                                depth === 2 && "pl-10",
                                depth >= 3 && "pl-14",
                                istGewaehlt
                                    ? "bg-rose-100 text-rose-800 font-medium"
                                    : "hover:bg-slate-100 text-slate-700"
                            )}
                            onClick={() => {
                                if (hatKinder) toggle(k.id);
                                onSelect(k);
                            }}
                        >
                            {hatKinder ? (
                                istOffen
                                    ? <FolderOpen className="w-4 h-4 text-rose-500 shrink-0" />
                                    : <Folder className="w-4 h-4 text-slate-400 shrink-0" />
                            ) : (
                                <Folder className="w-4 h-4 text-slate-300 shrink-0" />
                            )}
                            <span className="truncate">{k.beschreibung}</span>
                        </div>
                        {hatKinder && istOffen && (
                            <KategorieTree
                                tree={tree}
                                parentId={k.id}
                                selected={selected}
                                onSelect={onSelect}
                                depth={depth + 1}
                            />
                        )}
                    </li>
                );
            })}
        </ul>
    );
};

interface ManuelleBestellpositionModalProps {
    isOpen: boolean;
    onClose: () => void;
    onSuccess: () => void;
}

const ManuelleBestellpositionModal: React.FC<ManuelleBestellpositionModalProps> = ({ isOpen, onClose, onSuccess }) => {
    const toast = useToast();

    const [kategorien, setKategorien] = useState<KategorieFlach[]>([]);
    const [projekte, setProjekte] = useState<ProjektSimple[]>([]);
    const [lieferanten, setLieferanten] = useState<LieferantSimple[]>([]);

    const [kategorie, setKategorie] = useState<KategorieFlach | null>(null);
    const [kategorieOffen, setKategorieOffen] = useState(false);
    const [projektId, setProjektId] = useState<string>('');
    const [lieferantId, setLieferantId] = useState<string>('');
    const [produktname, setProduktname] = useState('');
    const [produkttext, setProdukttext] = useState('');
    const [menge, setMenge] = useState('');
    const [einheit, setEinheit] = useState('Stück');
    const [zeugnis, setZeugnis] = useState('');
    const [zeugnisVomSystem, setZeugnisVomSystem] = useState('');
    const [kommentar, setKommentar] = useState('');
    const [saving, setSaving] = useState(false);

    const kategorieTree = useMemo(() => buildKategorieTree(kategorien), [kategorien]);

    const gewaehltesProjekt = useMemo(
        () => projekte.find(p => p.id === Number(projektId)) ?? null,
        [projekte, projektId]
    );

    // Stammdaten laden
    useEffect(() => {
        if (!isOpen) return;
        // Reset
        setKategorie(null); setProjektId(''); setLieferantId('');
        setProduktname(''); setProdukttext(''); setMenge(''); setEinheit('Stück');
        setZeugnis(''); setZeugnisVomSystem(''); setKommentar('');

        fetch('/api/kategorien').then(r => r.json()).then(setKategorien).catch(console.error);
        fetch('/api/projekte/simple?nurOffene=true&size=500').then(r => r.json()).then((data) => {
            const list = Array.isArray(data) ? data : [];
            setProjekte(list.filter((p: ProjektSimple) => !p.abgeschlossen));
        }).catch(console.error);
        fetch('/api/lieferanten').then(r => r.json()).then((data) => {
            setLieferanten(Array.isArray(data) ? data : []);
        }).catch(console.error);
    }, [isOpen]);

    // Zeugnis-Vorschlag laden wenn Kategorie oder Projekt sich ändert
    useEffect(() => {
        if (!kategorie || !projektId) { setZeugnisVomSystem(''); return; }
        const proj = projekte.find(p => p.id === Number(projektId));
        if (!proj?.excKlasse) { setZeugnisVomSystem(''); return; }

        fetch(`/api/bestellungen/zeugnis-default?kategorieId=${kategorie.id}&excKlasse=${proj.excKlasse}`)
            .then(r => r.json())
            .then(data => {
                const vorschlag = data?.zeugnisTyp ?? '';
                setZeugnisVomSystem(vorschlag);
                setZeugnis(prev => prev === '' || prev === zeugnisVomSystem ? vorschlag : prev);
            })
            .catch(console.error);
    // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [kategorie?.id, projektId]);

    const handleSave = async () => {
        if (!produktname.trim()) { toast.warning('Bitte Produktname eingeben'); return; }
        if (!lieferantId) { toast.warning('Bitte Lieferanten wählen'); return; }
        if (!menge || isNaN(Number(menge))) { toast.warning('Bitte gültige Menge eingeben'); return; }

        setSaving(true);
        try {
            const res = await fetch('/api/bestellungen/manuell', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    projektId: projektId ? Number(projektId) : null,
                    lieferantId: Number(lieferantId),
                    kategorieId: kategorie?.id ?? null,
                    produktname: produktname.trim(),
                    produkttext: produkttext.trim() || null,
                    menge: Number(menge),
                    einheit: einheit.trim() || 'Stück',
                    zeugnisAnforderung: zeugnis || null,
                    kommentar: kommentar.trim() || null,
                }),
            });
            if (!res.ok) throw new Error('Fehler beim Speichern');
            toast.success('Position hinzugefügt');
            onSuccess();
            onClose();
        } catch {
            toast.error('Fehler beim Speichern der Position');
        } finally {
            setSaving(false);
        }
    };

    if (!isOpen) return null;

    const excBadge = gewaehltesProjekt?.excKlasse
        ? <span className="ml-2 inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-rose-100 text-rose-700 text-xs font-medium">
            <ShieldCheck className="w-3 h-3" />{EXC_LABEL[gewaehltesProjekt.excKlasse] ?? gewaehltesProjekt.excKlasse}
          </span>
        : null;

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm">
            <div className="bg-white rounded-2xl shadow-2xl w-full max-w-3xl mx-4 max-h-[92vh] overflow-hidden flex flex-col"
                onClick={e => e.stopPropagation()}>

                {/* Header */}
                <div className="flex items-center justify-between px-6 py-4 border-b border-slate-200 bg-slate-50 shrink-0">
                    <div className="flex items-center gap-3">
                        <Plus className="w-5 h-5 text-rose-600" />
                        <h2 className="text-lg font-semibold text-slate-900">Bestellposition hinzufügen</h2>
                    </div>
                    <div className="flex items-center gap-2">
                        <Button variant="ghost" onClick={onClose} disabled={saving}>Abbrechen</Button>
                        <Button onClick={handleSave} disabled={saving} className="bg-rose-600 text-white hover:bg-rose-700">
                            {saving ? <Loader2 className="w-4 h-4 mr-2 animate-spin" /> : <Plus className="w-4 h-4 mr-2" />}
                            {saving ? 'Speichern...' : 'Position speichern'}
                        </Button>
                        <Button variant="ghost" size="sm" onClick={onClose}><X className="w-5 h-5" /></Button>
                    </div>
                </div>

                <div className="flex-1 overflow-auto p-6 space-y-5">

                    {/* Zeile 1: Kategorie + Produkt */}
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                        {/* Kategorie-Picker */}
                        <div>
                            <label className="block text-sm font-medium text-slate-700 mb-1">Warengruppe / Kategorie</label>
                            <button
                                type="button"
                                className={cn(
                                    "w-full flex items-center justify-between px-3 py-2 border rounded-lg text-sm text-left",
                                    kategorie
                                        ? "border-rose-300 bg-rose-50 text-rose-800"
                                        : "border-slate-300 bg-white text-slate-400"
                                )}
                                onClick={() => setKategorieOffen(v => !v)}
                            >
                                <span className="truncate">{kategorie?.beschreibung ?? 'Kategorie wählen...'}</span>
                                <ChevronDown className={cn("w-4 h-4 text-slate-400 shrink-0 transition-transform", kategorieOffen && "rotate-180")} />
                            </button>
                            {kategorieOffen && (
                                <div className="mt-1 border border-slate-200 rounded-lg bg-white shadow-lg max-h-56 overflow-auto p-2">
                                    <KategorieTree
                                        tree={kategorieTree}
                                        parentId={null}
                                        selected={kategorie}
                                        onSelect={k => {
                                            setKategorie(k);
                                            // Blatt-Auswahl schließt Picker
                                            const hatKinder = (kategorieTree.get(k.id) ?? []).length > 0;
                                            if (!hatKinder) setKategorieOffen(false);
                                        }}
                                    />
                                </div>
                            )}
                        </div>

                        {/* Produktname */}
                        <div>
                            <label className="block text-sm font-medium text-slate-700 mb-1">Produktname *</label>
                            <Input
                                value={produktname}
                                onChange={e => setProduktname(e.target.value)}
                                placeholder="z. B. Flachstahl 40×5 mm, S235"
                            />
                        </div>
                    </div>

                    {/* Zeile 2: Produkttext */}
                    <div>
                        <label className="block text-sm font-medium text-slate-700 mb-1">Produktbeschreibung</label>
                        <Input
                            value={produkttext}
                            onChange={e => setProdukttext(e.target.value)}
                            placeholder="Optionale Beschreibung oder Norm-Angabe"
                        />
                    </div>

                    {/* Zeile 3: Menge + Einheit + Projekt + Lieferant */}
                    <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                        <div>
                            <label className="block text-sm font-medium text-slate-700 mb-1">Menge *</label>
                            <Input
                                type="number"
                                value={menge}
                                onChange={e => setMenge(e.target.value)}
                                placeholder="0"
                                min="0"
                            />
                        </div>
                        <div>
                            <label className="block text-sm font-medium text-slate-700 mb-1">Einheit</label>
                            <Select
                                value={einheit}
                                onChange={setEinheit}
                                options={[
                                    { value: 'Stück', label: 'Stück' },
                                    { value: 'm', label: 'm (Meter)' },
                                    { value: 'kg', label: 'kg' },
                                    { value: 'l', label: 'l (Liter)' },
                                    { value: 'Paar', label: 'Paar' },
                                    { value: 'Paket', label: 'Paket' },
                                ]}
                            />
                        </div>
                        <div>
                            <label className="block text-sm font-medium text-slate-700 mb-1">Projekt</label>
                            <Select
                                value={projektId}
                                onChange={setProjektId}
                                options={[
                                    { value: '', label: '— Kein Projekt —' },
                                    ...projekte.map(p => ({
                                        value: String(p.id),
                                        label: `${p.auftragsnummer} – ${p.bauvorhaben}${p.excKlasse ? ` [${EXC_LABEL[p.excKlasse] ?? p.excKlasse}]` : ''}`,
                                    })),
                                ]}
                                placeholder="Projekt wählen"
                            />
                            {excBadge && <div className="mt-1">{excBadge}</div>}
                        </div>
                        <div>
                            <label className="block text-sm font-medium text-slate-700 mb-1">Lieferant *</label>
                            <Select
                                value={lieferantId}
                                onChange={setLieferantId}
                                options={lieferanten.map(l => ({ value: String(l.id), label: l.lieferantenname }))}
                                placeholder="Lieferant wählen"
                            />
                        </div>
                    </div>

                    {/* Zeile 4: Zeugnis + Kommentar */}
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                        <div>
                            <label className="block text-sm font-medium text-slate-700 mb-1">
                                Zeugnisanforderung (EN 1090)
                                {zeugnisVomSystem && zeugnis === zeugnisVomSystem && (
                                    <span className="ml-2 text-xs text-rose-600 font-normal">
                                        <ShieldCheck className="w-3 h-3 inline-block mr-0.5" />Norm-Vorschlag
                                    </span>
                                )}
                            </label>
                            <Select
                                value={zeugnis}
                                onChange={setZeugnis}
                                options={ZEUGNIS_OPTIONEN}
                            />
                            {!kategorie && !projektId && (
                                <p className="text-xs text-slate-400 mt-1">
                                    Kategorie + Projekt wählen für automatischen Norm-Vorschlag
                                </p>
                            )}
                        </div>
                        <div>
                            <label className="block text-sm font-medium text-slate-700 mb-1">Kommentar</label>
                            <Input
                                value={kommentar}
                                onChange={e => setKommentar(e.target.value)}
                                placeholder="z. B. Lieferung bis KW 22 benötigt"
                            />
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
};

// ==================== PROJEKT GRUPPE COMPONENT ====================
interface LieferantGruppeCardProps {
    gruppe: LieferantGruppe;
    onToggleBestellt: (id: number, bestellt: boolean) => Promise<void>;
    onEmailClick: (lieferantId: number, lieferantName: string) => void;
}

const LieferantGruppeCard: React.FC<LieferantGruppeCardProps> = ({
    gruppe,
    onToggleBestellt,
    onEmailClick,
}) => {
    const [expanded, setExpanded] = useState(true);

    const totalKg = useMemo(() => {
        return gruppe.items.reduce((sum, b) => sum + (b.kilogramm || 0), 0);
    }, [gruppe.items]);

    const formatKg = (kg: number) => {
        return kg.toLocaleString('de-DE', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    };

    const projektAnzahl = useMemo(() => {
        const ids = new Set(gruppe.items.map(item => item.projektId).filter((id): id is number => !!id));
        return ids.size;
    }, [gruppe.items]);

    const handlePdfExport = () => {
        if (gruppe.lieferantId) {
            window.open(`/api/bestellungen/lieferant/${gruppe.lieferantId}/pdf`, '_blank');
        }
    };

    return (
        <Card className="overflow-hidden">
            {/* Header */}
            <div
                className="flex items-center justify-between p-4 bg-slate-50 border-b border-slate-200 cursor-pointer"
                onClick={() => setExpanded(!expanded)}
            >
                <div className="flex items-center gap-3">
                    {expanded ? (
                        <ChevronDown className="w-5 h-5 text-slate-500" />
                    ) : (
                        <ChevronRight className="w-5 h-5 text-slate-500" />
                    )}
                    <div>
                        <h3 className="font-semibold text-slate-900">
                            {gruppe.lieferantName || 'Ohne Lieferant'}
                        </h3>
                        <div className="flex items-center gap-4 text-sm text-slate-500">
                            {projektAnzahl > 0 && (
                                <span>{projektAnzahl} Projekte</span>
                            )}
                            <span>{gruppe.items.length} Positionen</span>
                            {totalKg > 0 && (
                                <span>{formatKg(totalKg)} kg</span>
                            )}
                        </div>
                    </div>
                </div>

                <div className="flex items-center gap-2" onClick={e => e.stopPropagation()}>
                    <Button
                        variant="outline"
                        size="sm"
                        onClick={handlePdfExport}
                        disabled={!gruppe.lieferantId}
                    >
                        <Download className="w-4 h-4 mr-1" />
                        PDF
                    </Button>
                    <Button
                        size="sm"
                        onClick={() => gruppe.lieferantId && onEmailClick(gruppe.lieferantId, gruppe.lieferantName)}
                        disabled={!gruppe.lieferantId}
                        className="bg-rose-600 text-white hover:bg-rose-700"
                    >
                        <Mail className="w-4 h-4 mr-1" />
                        E-Mail
                    </Button>
                </div>
            </div>

            {/* Table */}
            {expanded && (
                <div className="overflow-x-auto">
                    <table className="w-full text-sm">
                        <thead className="bg-slate-100">
                            <tr>
                                <th className="px-4 py-3 text-left font-medium text-slate-600">Bestellt</th>
                                <th className="px-4 py-3 text-left font-medium text-slate-600">Projektnummer</th>
                                <th className="px-4 py-3 text-left font-medium text-slate-600">Projekt</th>
                                <th className="px-4 py-3 text-left font-medium text-slate-600">Kunde</th>
                                <th className="px-4 py-3 text-left font-medium text-slate-600">Artikelnummer</th>
                                <th className="px-4 py-3 text-left font-medium text-slate-600">Produkt</th>
                                <th className="px-4 py-3 text-left font-medium text-slate-600">Produkttext</th>
                                <th className="px-4 py-3 text-left font-medium text-slate-600">Kommentar</th>
                                <th className="px-4 py-3 text-left font-medium text-slate-600">Werkstoff</th>
                                <th className="px-4 py-3 text-left font-medium text-slate-600">Kategorie</th>
                                <th className="px-4 py-3 text-left font-medium text-slate-600">Menge</th>
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-slate-100">
                            {gruppe.items.map((b) => (
                                <tr key={b.id} className={cn(
                                    "hover:bg-slate-50 transition-colors",
                                    b.bestellt && "bg-green-50"
                                )}>
                                    <td className="px-4 py-3">
                                        <input
                                            type="checkbox"
                                            checked={b.bestellt}
                                            onChange={e => onToggleBestellt(b.id, e.target.checked)}
                                            className="w-4 h-4 text-rose-600 border-slate-300 rounded focus:ring-rose-500"
                                            title={`Position ${b.produktname || ''} als bestellt markieren`}
                                            aria-label={`Position ${b.produktname || ''} als bestellt markieren`}
                                        />
                                    </td>
                                    <td className="px-4 py-3 text-slate-600">
                                        {b.projektNummer || '-'}
                                    </td>
                                    <td className="px-4 py-3 text-slate-900 font-medium">
                                        {b.projektName || '-'}
                                    </td>
                                    <td className="px-4 py-3 text-slate-600">
                                        {b.kundenName || '-'}
                                    </td>
                                    <td className="px-4 py-3 text-slate-900 font-mono text-xs">
                                        {b.externeArtikelnummer || '-'}
                                    </td>
                                    <td className="px-4 py-3 text-slate-900 font-medium">
                                        {b.produktname || '-'}
                                    </td>
                                    <td className="px-4 py-3 text-slate-600 max-w-xs truncate">
                                        {b.produkttext || '-'}
                                    </td>
                                    <td className="px-4 py-3 text-slate-600 max-w-xs truncate">
                                        {b.kommentar || '-'}
                                    </td>
                                    <td className="px-4 py-3 text-slate-600">
                                        {b.werkstoffName || '-'}
                                    </td>
                                    <td className="px-4 py-3 text-slate-600">
                                        {b.kategorieName || '-'}
                                    </td>
                                    <td className="px-4 py-3 text-slate-900">
                                        {b.menge ? `${b.menge} ${b.einheit || ''}` : '-'}
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
            )}
        </Card>
    );
};

// ==================== MAIN COMPONENT ====================
export default function BestellungEditor() {
    const [bestellungen, setBestellungen] = useState<Bestellung[]>([]);
    const [loading, setLoading] = useState(true);
    const [emailModal, setEmailModal] = useState<{ lieferantId: number; lieferantName: string } | null>(null);
    const [bestellpositionModalOffen, setBestellpositionModalOffen] = useState(false);

    const loadBestellungen = useCallback(async () => {
        setLoading(true);
        try {
            const res = await fetch('/api/bestellungen/offen');
            const data = res.ok ? await res.json() : [];
            setBestellungen(Array.isArray(data) ? data : []);
        } catch (err) {
            console.error('Error loading Bestellungen:', err);
            setBestellungen([]);
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        loadBestellungen();
    }, [loadBestellungen]);

    // Group by supplier
    const lieferantGruppen = useMemo(() => {
        const gruppiert: Record<string, LieferantGruppe> = {};
        bestellungen.forEach(b => {
            const key = b.lieferantId != null
                ? `lieferant-${b.lieferantId}`
                : `ohne-${(b.lieferantName || 'lieferant').toLowerCase()}`;
            if (!gruppiert[key]) {
                gruppiert[key] = {
                    lieferantId: b.lieferantId || null,
                    lieferantName: b.lieferantName || 'Ohne Lieferant',
                    items: [],
                };
            }
            gruppiert[key].items.push(b);
        });

        return Object.values(gruppiert).sort((a, b) =>
            a.lieferantName.localeCompare(b.lieferantName, 'de-DE')
        );
    }, [bestellungen]);

    const handleToggleBestellt = async (id: number, bestellt: boolean) => {
        try {
            await fetch(`/api/bestellungen/${id}?bestellt=${bestellt}`, {
                method: 'PATCH',
            });
            // Reload to get updated data
            loadBestellungen();
        } catch (err) {
            console.error('Error updating Bestellung:', err);
        }
    };

    const handleEmailClick = (lieferantId: number, lieferantName: string) => {
        setEmailModal({ lieferantId, lieferantName });
    };

    return (

        <PageLayout
            ribbonCategory="Einkauf"
            title="Bestellungen"
            subtitle="Offene Bestellungen nach Lieferanten verwalten und versenden."
            actions={
                <div className="flex items-center gap-2">
                    <Button onClick={() => setBestellpositionModalOffen(true)} className="bg-rose-600 text-white hover:bg-rose-700" size="sm">
                        <Plus className="w-4 h-4 mr-2" />
                        Position hinzufügen
                    </Button>
                    <Button variant="outline" size="sm" onClick={loadBestellungen} disabled={loading}>
                        <RefreshCw className={cn("w-4 h-4 mr-2", loading && "animate-spin")} />
                        Aktualisieren
                    </Button>
                </div>
            }
        >

            {/* Content */}
            {loading ? (
                <div className="flex items-center justify-center py-12">
                    <RefreshCw className="w-8 h-8 text-rose-600 animate-spin" />
                </div>
            ) : lieferantGruppen.length === 0 ? (
                <Card className="p-12 text-center">
                    <Package className="w-16 h-16 text-slate-300 mx-auto mb-4" />
                    <p className="text-slate-500 text-lg">Keine offenen Bestellungen vorhanden.</p>
                </Card>
            ) : (
                <div className="space-y-6">
                    {lieferantGruppen.map((gruppe) => (
                        <LieferantGruppeCard
                            key={gruppe.lieferantId || gruppe.lieferantName || 'ohne-lieferant'}
                            gruppe={gruppe}
                            onToggleBestellt={handleToggleBestellt}
                            onEmailClick={handleEmailClick}
                        />
                    ))}
                </div>
            )}

            {/* Manuelle Bestellposition */}
            <ManuelleBestellpositionModal
                isOpen={bestellpositionModalOffen}
                onClose={() => setBestellpositionModalOffen(false)}
                onSuccess={loadBestellungen}
            />

            {/* Email Modal */}
            {emailModal && (
                <BestellungEmailModal
                    isOpen={true}
                    onClose={() => setEmailModal(null)}
                    lieferantId={emailModal.lieferantId}
                    lieferantName={emailModal.lieferantName}
                    onSuccess={loadBestellungen}
                />
            )}
        </PageLayout>
    );
}
