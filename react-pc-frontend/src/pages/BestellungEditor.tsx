import { useCallback, useEffect, useMemo, useState, useRef } from "react";
import { PdfCanvasViewer } from "../components/ui/PdfCanvasViewer";
import {
    ChevronDown,
    ChevronRight,
    Download,
    ExternalLink,
    Eye,
    File,
    Loader2,
    Mail,
    Package,
    Paperclip,
    RefreshCw,
    Send,
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

interface ProjektGruppe {
    projektId: number | null;
    projektName: string;
    projektNummer?: string;
    kundenName?: string;
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
    projektId: number;
    projektName: string;
    onSuccess: () => void;
}

const BestellungEmailModal: React.FC<BestellungEmailModalProps> = ({
    isOpen,
    onClose,
    projektId,
    projektName,
    onSuccess,
}) => {
    const toast = useToast();
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
        setSubject(`Anfrage: (${projektName})`);
        setSending(false);
        setBeautifying(false);
        setUploadedFiles([]);
        setPreviewFile(null);
        setShowCustomRecipient(false);
        setCustomRecipient('');
        setCc('');
        setCcManual('');

        // Lieferanten-E-Mails laden
        fetch('/api/lieferanten/emails')
            .then(res => res.json())
            .then(data => {
                const emails = Array.isArray(data) ? data : [];
                setLieferantenEmails(emails);
                if (emails.length > 0) setRecipient(emails[0]);
            })
            .catch(console.error);

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
            const initialBody = `<p>Sehr geehrte Damen und Herren,</p><p><br></p><p>bitte erstellen Sie uns ein Anfrage für die angehängten Positionen.</p><p><br></p>${sig}`;
            setBody(initialBody);
        });
    }, [isOpen, projektName, loadSignature]);

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
        const finalRecipient = showCustomRecipient ? customRecipient : recipient;
        if (!finalRecipient) {
            toast.warning('Bitte Empfänger auswählen oder eingeben');
            return;
        }

        setSending(true);

        try {
            const user = getCurrentFrontendUser();
            const formData = new FormData();
            formData.append('projektId', projektId.toString());
            formData.append('recipient', finalRecipient);
            formData.append('cc', cc === 'manual' ? ccManual : cc);
            formData.append('fromAddress', fromAddress);
            formData.append('subject', subject);
            formData.append('htmlBody', prepareHtmlForSending(editorRef.current?.innerHTML || body));
            if (user) {
                formData.append('frontendUserId', user.id.toString());
                formData.append('benutzer', user.displayName);
            }

            // Zusätzliche Anhänge
            uploadedFiles.forEach(uf => {
                formData.append('attachments', uf.file);
            });

            const res = await fetch('/api/bestellungen/email/send', {
                method: 'POST',
                body: formData,
            });

            if (!res.ok) throw new Error('Senden fehlgeschlagen');

            toast.success('E-Mail versendet');
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
                                Anfrage für {projektName} senden
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
                                href={`/api/bestellungen/projekt/${projektId}/pdf`}
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

// ==================== PROJEKT GRUPPE COMPONENT ====================
interface ProjektGruppeCardProps {
    gruppe: ProjektGruppe;
    onToggleBestellt: (id: number, bestellt: boolean) => Promise<void>;
    onEmailClick: (projektId: number, projektName: string) => void;
}

const ProjektGruppeCard: React.FC<ProjektGruppeCardProps> = ({
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

    const handlePdfExport = () => {
        if (gruppe.projektId) {
            window.open(`/api/bestellungen/projekt/${gruppe.projektId}/pdf`, '_blank');
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
                            {gruppe.projektName || 'Ohne Projekt'}
                        </h3>
                        <div className="flex items-center gap-4 text-sm text-slate-500">
                            {gruppe.projektNummer && (
                                <span>#{gruppe.projektNummer}</span>
                            )}
                            {gruppe.kundenName && (
                                <span>{gruppe.kundenName}</span>
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
                        disabled={!gruppe.projektId}
                    >
                        <Download className="w-4 h-4 mr-1" />
                        PDF
                    </Button>
                    <Button
                        size="sm"
                        onClick={() => gruppe.projektId && onEmailClick(gruppe.projektId, gruppe.projektName)}
                        disabled={!gruppe.projektId}
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
                                <th className="px-4 py-3 text-left font-medium text-slate-600">Artikelnummer</th>
                                <th className="px-4 py-3 text-left font-medium text-slate-600">Produkt</th>
                                <th className="px-4 py-3 text-left font-medium text-slate-600">Produkttext</th>
                                <th className="px-4 py-3 text-left font-medium text-slate-600">Kommentar</th>
                                <th className="px-4 py-3 text-left font-medium text-slate-600">Werkstoff</th>
                                <th className="px-4 py-3 text-left font-medium text-slate-600">Kategorie</th>
                                <th className="px-4 py-3 text-left font-medium text-slate-600">Menge</th>
                                <th className="px-4 py-3 text-left font-medium text-slate-600">Lieferant</th>
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
                                        />
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
                                    <td className="px-4 py-3 text-slate-600">
                                        {b.lieferantName || '-'}
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
    const [emailModal, setEmailModal] = useState<{ projektId: number; projektName: string } | null>(null);

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

    // Group by project
    const projektGruppen = useMemo(() => {
        const gruppiert: Record<string, ProjektGruppe> = {};
        bestellungen.forEach(b => {
            const key = b.projektId?.toString() || '0';
            if (!gruppiert[key]) {
                gruppiert[key] = {
                    projektId: b.projektId || null,
                    projektName: b.projektName || 'Ohne Projekt',
                    projektNummer: b.projektNummer,
                    kundenName: b.kundenName,
                    items: [],
                };
            }
            gruppiert[key].items.push(b);
        });
        return Object.values(gruppiert);
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

    const handleEmailClick = (projektId: number, projektName: string) => {
        setEmailModal({ projektId, projektName });
    };

    return (

        <PageLayout
            ribbonCategory="Einkauf"
            title="Bestellungen"
            subtitle="Offene Bestellungen verwalten und an Lieferanten senden."
            actions={
                <Button variant="outline" size="sm" onClick={loadBestellungen} disabled={loading}>
                    <RefreshCw className={cn("w-4 h-4 mr-2", loading && "animate-spin")} />
                    Aktualisieren
                </Button>
            }
        >

            {/* Content */}
            {loading ? (
                <div className="flex items-center justify-center py-12">
                    <RefreshCw className="w-8 h-8 text-rose-600 animate-spin" />
                </div>
            ) : projektGruppen.length === 0 ? (
                <Card className="p-12 text-center">
                    <Package className="w-16 h-16 text-slate-300 mx-auto mb-4" />
                    <p className="text-slate-500 text-lg">Keine offenen Bestellungen vorhanden.</p>
                </Card>
            ) : (
                <div className="space-y-6">
                    {projektGruppen.map((gruppe) => (
                        <ProjektGruppeCard
                            key={gruppe.projektId || 'no-project'}
                            gruppe={gruppe}
                            onToggleBestellt={handleToggleBestellt}
                            onEmailClick={handleEmailClick}
                        />
                    ))}
                </div>
            )}

            {/* Email Modal */}
            {emailModal && (
                <BestellungEmailModal
                    isOpen={true}
                    onClose={() => setEmailModal(null)}
                    projektId={emailModal.projektId}
                    projektName={emailModal.projektName}
                    onSuccess={loadBestellungen}
                />
            )}
        </PageLayout>
    );
}
