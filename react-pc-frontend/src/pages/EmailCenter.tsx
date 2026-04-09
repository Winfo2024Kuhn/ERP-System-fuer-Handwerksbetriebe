import React, { useState, useEffect, useCallback, useMemo, useRef } from 'react';
import { useSearchParams } from 'react-router-dom';
import { PdfCanvasViewer } from '../components/ui/PdfCanvasViewer';
import {
    Mail,
    RefreshCw,
    Trash2,
    Inbox,
    Send,
    AlertCircle,
    Paperclip,
    Clock,
    Download,
    FolderPlus,
    Search,
    Briefcase,
    FileText,
    ChevronDown,
    ChevronRight,
    File,
    Eye,
    PenSquare,
    HelpCircle,
    ShieldAlert,
    ShieldCheck,
    ShieldX,
    Settings,
    Star,
    Package,
    Reply,
    Newspaper,
    Globe,
    X,
    CheckSquare,
    MailCheck
} from 'lucide-react';
import { Button } from '../components/ui/button';
import { Input } from '../components/ui/input';
import { cn } from '../lib/utils';
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '../components/ui/dialog';
import { EmailComposeForm } from '../components/EmailComposeForm';
import { EmailContentFrame } from '../components/EmailContentFrame';
import EmailSettings from '../components/EmailSettings';
import { ImageViewer } from '../components/ui/image-viewer';
import { useToast } from '../components/ui/toast';
import { useConfirm } from '../components/ui/confirm-dialog';

// Statische Icon-Pfade für spezielle Dateitypen
const BASE_URL = '/react-textbausteine/';
const ICON_TENADO = `${BASE_URL}tenado_logo.jpg`;
const ICON_EXCEL = `${BASE_URL}excel_image.jpg`;
const ICON_HICAD = `${BASE_URL}hicad_logo.png`;

const getAttachmentIcon = (filename: string): string | null => {
    const lower = filename?.toLowerCase() || '';
    if (lower.endsWith('.pdf')) return 'pdf';
    if (lower.endsWith('.tcd')) return ICON_TENADO;
    if (lower.endsWith('.xls') || lower.endsWith('.xlsx') || lower.endsWith('.xlsm')) return ICON_EXCEL;
    if (lower.endsWith('.sza')) return ICON_HICAD;
    return null;
};

const isImageAttachment = (filename: string): boolean => {
    const lower = filename?.toLowerCase() || '';
    return /\.(jpg|jpeg|png|gif|webp|bmp)$/.test(lower);
};

interface EmailAttachment {
    id?: number;
    filename?: string;
    originalFilename?: string;
    storedFilename?: string;
    url?: string;
    type?: string;
    contentId?: string;
    inline?: boolean;
}

interface EmailItem {
    id: number;
    type: string;
    containerId?: number;
    direction: 'IN' | 'OUT';
    subject?: string;
    sender?: string;
    fromAddress?: string;
    body?: string;
    htmlBody?: string;
    sentAt?: string;
    attachments?: EmailAttachment[];
    replies?: EmailItem[];
    zuordnungTyp?: string;
    projektName?: string;
    anfrageName?: string;
    lieferantName?: string;
    isRead?: boolean;
    recipient?: string;
    spamScore?: number;
    // Assignment IDs
    projektId?: number;
    anfrageId?: number;
    lieferantId?: number;
}

// Folder Types
type FolderType = 'inbox' | 'sent' | 'trash' | 'spam' | 'newsletter' | 'projects' | 'offers' | 'suppliers' | 'unassigned' | 'inquiries';




const getSenderName = (email: EmailItem) => {
    if (email.lieferantName) return email.lieferantName;
    if (email.projektName) return email.projektName;
    if (email.anfrageName) return email.anfrageName;
    if (email.fromAddress) {
        const match = email.fromAddress.match(/^"?(.*?)"? <.*>$/);
        if (match && match[1]) return match[1];
        return email.fromAddress;
    }
    return email.sender || 'Unbekannt';
};

const getRecipientName = (email: EmailItem) => {
    if (email.recipient) {
        const match = email.recipient.match(/^"?(.*?)"? <.*>$/);
        if (match && match[1]) return match[1];
        return email.recipient;
    }
    return 'Unbekannt';
};

const getDisplayName = (email: EmailItem) => {
    if (email.direction === 'OUT') {
        return getRecipientName(email);
    }
    return getSenderName(email);
};

// Email Attachment Card Component
function EmailAttachmentCard({ attachment, email, onPreview }: {
    attachment: EmailAttachment,
    email: EmailItem,
    onPreview: (url: string, type: 'image' | 'pdf', name: string) => void
}) {
    const filename = attachment.originalFilename || attachment.filename || attachment.storedFilename || 'Datei';
    const iconSrc = getAttachmentIcon(filename);
    const isImage = isImageAttachment(filename);
    const downloadUrl = `/api/emails/${email.id}/attachments/${attachment.id}`;

    const handleDownload = (e: React.MouseEvent) => {
        e.stopPropagation();
        const a = document.createElement('a');
        a.href = downloadUrl;
        a.download = filename;
        a.click();
    };

    const handlePreview = () => {
        if (isImage) {
            onPreview(downloadUrl, 'image', filename);
        } else if (iconSrc === 'pdf') {
            onPreview(downloadUrl, 'pdf', filename);
        } else {
            const a = document.createElement('a');
            a.href = downloadUrl;
            a.download = filename;
            a.click();
        }
    };

    return (
        <div
            onClick={handlePreview}
            className="flex items-center gap-3 p-2.5 bg-slate-50 rounded-lg border border-slate-200 hover:bg-slate-100 hover:border-slate-300 transition-all duration-200 group cursor-pointer"
        >
            {isImage ? (
                <img
                    src={downloadUrl}
                    alt={filename}
                    className="w-10 h-10 object-cover rounded"
                />
            ) : iconSrc === 'pdf' ? (
                <div className="w-10 h-10 rounded bg-red-50 flex items-center justify-center">
                    <File className="w-5 h-5 text-red-500" />
                </div>
            ) : iconSrc ? (
                <img src={iconSrc} alt={filename} className="w-10 h-10 object-contain" />
            ) : (
                <div className="w-10 h-10 rounded bg-slate-100 flex items-center justify-center">
                    <File className="w-5 h-5 text-slate-400" />
                </div>
            )}
            <div className="flex-1 min-w-0">
                <p className="text-sm font-medium text-slate-700 truncate">{filename}</p>
            </div>
            <div className="flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                {(isImage || iconSrc === 'pdf') && (
                    <Button
                        variant="ghost"
                        size="sm"
                        onClick={(e) => { e.stopPropagation(); handlePreview(); }}
                        className="h-8 w-8 p-0 text-slate-500 hover:text-rose-600"
                        title="Vorschau"
                    >
                        <Eye className="w-4 h-4" />
                    </Button>
                )}
                <Button
                    variant="ghost"
                    size="sm"
                    onClick={handleDownload}
                    className="h-8 w-8 p-0 text-slate-500 hover:text-rose-600"
                    title="Download"
                >
                    <Download className="w-4 h-4" />
                </Button>
            </div>
        </div>
    );
}

// Assignment Modal Component
interface AssignModalProps {
    isOpen: boolean;
    onClose: () => void;
    onAssign: (type: 'projekt' | 'anfrage', targetId: number) => Promise<void>;
    emailSubject: string;
    emailId?: number;
}

interface EntityOption {
    id: number;
    name: string;
    type: string;
    projektNummer?: string;
    anfrageNummer?: string;
}

function AssignModal({ isOpen, onClose, onAssign, emailSubject, emailId }: AssignModalProps) {
    const toast = useToast();
    const [searchType, setSearchType] = useState<'projekt' | 'anfrage'>('projekt');
    const [searchQuery, setSearchQuery] = useState('');
    const [results, setResults] = useState<{ id: number; bauvorhaben?: string; name?: string; kunde?: string }[]>([]);
    const [suggestions, setSuggestions] = useState<{ projekte: EntityOption[], anfragen: EntityOption[] }>({ projekte: [], anfragen: [] });
    const [loading, setLoading] = useState(false);
    const [loadingSuggestions, setLoadingSuggestions] = useState(false);
    const [assigning, setAssigning] = useState(false);

    // Load suggestions when modal opens
    useEffect(() => {
        if (isOpen && emailId) {
            setLoadingSuggestions(true);
            fetch(`/api/emails/${emailId}/possible-assignments`)
                .then(res => res.json())
                .then(data => {
                    setSuggestions({
                        projekte: data.projekte || [],
                        anfragen: data.anfragen || []
                    });
                })
                .catch(err => console.error('Failed to load suggestions:', err))
                .finally(() => setLoadingSuggestions(false));
        }
    }, [isOpen, emailId]);

    useEffect(() => {
        if (!isOpen) {
            setSearchQuery('');
            setResults([]);
        }
    }, [isOpen]);

    const fetchResults = useCallback(async () => {
        if (!searchQuery.trim()) {
            setResults([]);
            setLoading(false);
            return;
        }
        setLoading(true);
        try {
            const endpoint = searchType === 'projekt'
                ? `/api/projekte/search?q=${encodeURIComponent(searchQuery)}`
                : `/api/anfragen/search?q=${encodeURIComponent(searchQuery)}`;
            const res = await fetch(endpoint);
            if (res.ok) {
                setResults(await res.json());
            }
        } catch (err) {
            console.error('Search failed:', err);
        } finally {
            setLoading(false);
        }
    }, [searchQuery, searchType]);

    useEffect(() => {
        const timeout = setTimeout(fetchResults, 300);
        return () => clearTimeout(timeout);
    }, [fetchResults]);

    const handleSelect = async (type: 'projekt' | 'anfrage', id: number) => {
        setAssigning(true);
        try {
            await onAssign(type, id);
            onClose();
        } catch {
            toast.error('Zuordnung fehlgeschlagen');
        } finally {
            setAssigning(false);
        }
    };

    const currentSuggestions = searchType === 'projekt' ? suggestions.projekte : suggestions.anfragen;
    const hasSuggestions = currentSuggestions.length > 0;

    return (
        <Dialog open={isOpen} onOpenChange={(open) => !open && onClose()}>
            <DialogContent className="max-w-lg">
                <DialogHeader>
                    <DialogTitle>E-Mail zuordnen</DialogTitle>
                </DialogHeader>
                <div className="space-y-4">
                    <p className="text-sm text-slate-600 truncate">"{emailSubject}"</p>

                    <div className="flex gap-2">
                        <Button
                            variant={searchType === 'projekt' ? 'default' : 'outline'}
                            size="sm"
                            onClick={() => setSearchType('projekt')}
                            className={searchType === 'projekt' ? 'bg-rose-600 hover:bg-rose-700' : ''}
                        >
                            <Briefcase className="w-4 h-4 mr-1" />
                            Projekt
                            {suggestions.projekte.length > 0 && (
                                <span className="ml-1 bg-white/20 px-1.5 rounded text-xs">{suggestions.projekte.length}</span>
                            )}
                        </Button>
                        <Button
                            variant={searchType === 'anfrage' ? 'default' : 'outline'}
                            size="sm"
                            onClick={() => setSearchType('anfrage')}
                            className={searchType === 'anfrage' ? 'bg-rose-600 hover:bg-rose-700' : ''}
                        >
                            <FileText className="w-4 h-4 mr-1" />
                            Anfrage
                            {suggestions.anfragen.length > 0 && (
                                <span className="ml-1 bg-white/20 px-1.5 rounded text-xs">{suggestions.anfragen.length}</span>
                            )}
                        </Button>
                    </div>

                    {/* Suggestions Section */}
                    {loadingSuggestions ? (
                        <div className="text-center text-slate-500 py-2">
                            <RefreshCw className="w-4 h-4 animate-spin mx-auto" />
                            <p className="text-xs mt-1">Lade Vorschläge...</p>
                        </div>
                    ) : hasSuggestions && (
                        <div className="space-y-1">
                            <p className="text-xs font-medium text-rose-600">Vorschläge (passende E-Mail-Adresse):</p>
                            {currentSuggestions.map((item) => (
                                <button
                                    key={`suggestion-${item.id}`}
                                    onClick={() => handleSelect(searchType, item.id)}
                                    disabled={assigning}
                                    className="w-full text-left p-3 rounded-lg bg-rose-50 hover:bg-rose-100 transition-colors border border-rose-200"
                                >
                                    <p className="font-medium text-slate-900">
                                        {item.projektNummer && <span className="text-rose-600 mr-2">{item.projektNummer}</span>}
                                        {item.anfrageNummer && <span className="text-rose-600 mr-2">{item.anfrageNummer}</span>}
                                        {item.name}
                                    </p>
                                    <p className="text-xs text-rose-600 flex items-center gap-1">
                                        <Star className="w-3 h-3 fill-rose-500 text-rose-500" />
                                        Empfohlen
                                    </p>
                                </button>
                            ))}
                        </div>
                    )}

                    {/* Manual Search Section */}
                    <div className="pt-2 border-t border-slate-200">
                        <p className="text-xs text-slate-500 mb-2">Oder manuell suchen:</p>
                        <Input
                            placeholder={`${searchType === 'projekt' ? 'Projekt' : 'Anfrage'} suchen...`}
                            value={searchQuery}
                            onChange={(e) => setSearchQuery(e.target.value)}
                            className="border-slate-200"
                        />
                    </div>

                    <div className="max-h-40 overflow-auto space-y-1">
                        {loading ? (
                            <p className="text-center text-slate-500 py-4">Suche...</p>
                        ) : results.length === 0 && searchQuery ? (
                            <p className="text-center text-slate-500 py-4">Keine Ergebnisse</p>
                        ) : (
                            results.map((item: { id: number; bauvorhaben?: string; name?: string; kunde?: string }) => (
                                <button
                                    key={item.id}
                                    onClick={() => handleSelect(searchType, item.id)}
                                    disabled={assigning}
                                    className="w-full text-left p-3 rounded-lg hover:bg-rose-50 transition-colors border border-slate-200"
                                >
                                    <p className="font-medium text-slate-900">{item.bauvorhaben || item.name}</p>
                                    {item.kunde && <p className="text-sm text-slate-500">{item.kunde}</p>}
                                </button>
                            ))
                        )}
                    </div>
                </div>
            </DialogContent>
        </Dialog>
    );
}

// Main EmailCenter Component
export default function EmailCenter() {
    const toast = useToast();
    const confirmDialog = useConfirm();
    const [searchParams, setSearchParams] = useSearchParams();
    // State
    const [activeFolder, setActiveFolder] = useState<FolderType>('inbox');
    // activeFilter removed
    const [emails, setEmails] = useState<EmailItem[]>([]);
    const [searchQuery, setSearchQuery] = useState('');
    const [isGlobalSearch, setIsGlobalSearch] = useState(false);
    const [globalSearchResults, setGlobalSearchResults] = useState<EmailItem[]>([]);
    const [globalSearchLoading, setGlobalSearchLoading] = useState(false);
    const [loading, setLoading] = useState(false);
    const [selectedEmail, setSelectedEmail] = useState<EmailItem | null>(null);
    const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set());
    const lastSelectedIdRef = useRef<number | null>(null);

    // Composition State
    const [isComposing, setIsComposing] = useState(false);
    const [replyToEmail, setReplyToEmail] = useState<EmailItem | null>(null);

    const [showAssignModal, setShowAssignModal] = useState(false);
    const [folderCounts, setFolderCounts] = useState({
        inbox: 0, sent: 0, trash: 0, spam: 0, newsletter: 0,
        projects: 0, offers: 0, suppliers: 0, unassigned: 0, inquiries: 0
    });
    const [expandedFilters, setExpandedFilters] = useState(true);
    const [previewAttachment, setPreviewAttachment] = useState<{ url: string, type: 'image' | 'pdf', name: string } | null>(null);
    const [showSettings, setShowSettings] = useState(false);

    // Initial load
    useEffect(() => {
        loadEmails();
        loadStats();
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [activeFolder]);

    // Action Hanlders
    const handleComposeNew = () => {
        setSelectedEmail(null);
        setReplyToEmail(null);
        setIsComposing(true);
    };

    const handleReply = (email: EmailItem) => {
        setReplyToEmail(email);
        setIsComposing(true);
    };

    const handleComposeClose = () => {
        setIsComposing(false);
        setReplyToEmail(null);
    };

    const handleComposeSuccess = () => {
        setIsComposing(false);
        setReplyToEmail(null);
        // Reload in background without resetting scroll/selection
        refreshEmailsSilently();
        loadStats();
    };

    // Load emails based on folder and filter
    const loadEmails = useCallback(async () => {
        setLoading(true);
        try {
            const endpointMap: Record<string, string> = {
                'inbox': '/api/emails/inbox',
                'sent': '/api/emails/sent',
                'trash': '/api/emails/trash',
                'spam': '/api/emails/spam',
                'newsletter': '/api/emails/newsletter',
                'projects': '/api/emails/projects',
                'offers': '/api/emails/offers',
                'suppliers': '/api/emails/suppliers',
                'unassigned': '/api/emails/unassigned',
                'inquiries': '/api/emails/inquiries'
            };
            const endpoint = endpointMap[activeFolder] || '/api/emails/inbox';

            const res = await fetch(endpoint);
            if (res.ok) {
                let data = await res.json();
                if (!Array.isArray(data)) data = [];

                // Fix: Zugeordnete Emails dürfen niemals in Spam oder Newsletter landen
                if (activeFolder === 'spam' || activeFolder === 'newsletter') {
                    data = data.filter((email: EmailItem) => {
                        const hasAssignment =
                            (email.zuordnungTyp && email.zuordnungTyp !== 'KEINE') ||
                            email.projektId ||
                            email.anfrageId ||
                            email.lieferantId;
                        return !hasAssignment;
                    });
                }

                setEmails(data);
            } else {
                setEmails([]);
            }
        } catch (err) {
            console.error('Failed to load emails', err);
            setEmails([]);
        } finally {
            setLoading(false);
        }
    }, [activeFolder]);

    // Load stats for folder counts
    const loadStats = useCallback(async () => {
        try {
            const res = await fetch('/api/emails/stats');
            if (res.ok) {
                const stats = await res.json();
                setFolderCounts({
                    inbox: stats.inboxCount || 0,
                    sent: stats.sentCount || 0,
                    trash: stats.trashCount || 0,
                    spam: stats.spamCount || 0,
                    newsletter: stats.newsletterCount || 0,
                    unassigned: stats.unassignedCount || 0,
                    inquiries: stats.inquiriesCount || 0,
                    projects: stats.projectCount || 0,
                    offers: stats.offerCount || 0,
                    suppliers: stats.supplierCount || 0
                });
            }
        } catch (err) {
            console.error('Failed to load stats', err);
        }
    }, []);

    // Silent refresh: merges new emails without resetting scroll position or selection
    const refreshEmailsSilently = useCallback(async () => {
        try {
            const endpointMap: Record<string, string> = {
                'inbox': '/api/emails/inbox',
                'sent': '/api/emails/sent',
                'trash': '/api/emails/trash',
                'spam': '/api/emails/spam',
                'newsletter': '/api/emails/newsletter',
                'projects': '/api/emails/projects',
                'offers': '/api/emails/offers',
                'suppliers': '/api/emails/suppliers',
                'unassigned': '/api/emails/unassigned',
                'inquiries': '/api/emails/inquiries'
            };
            const endpoint = endpointMap[activeFolder] || '/api/emails/inbox';
            const res = await fetch(endpoint);
            if (res.ok) {
                let data = await res.json();
                if (!Array.isArray(data)) data = [];

                if (activeFolder === 'spam' || activeFolder === 'newsletter') {
                    data = data.filter((email: EmailItem) => {
                        const hasAssignment =
                            (email.zuordnungTyp && email.zuordnungTyp !== 'KEINE') ||
                            email.projektId ||
                            email.anfrageId ||
                            email.lieferantId;
                        return !hasAssignment;
                    });
                }

                setEmails(data);
            }
        } catch (err) {
            console.error('Silent refresh failed', err);
        }
    }, [activeFolder]);

    useEffect(() => {
        loadEmails();
        loadStats();
    }, [loadEmails, loadStats]);

    // Auto-poll for new emails every 30 seconds
    useEffect(() => {
        const interval = setInterval(() => {
            refreshEmailsSilently();
            loadStats();
        }, 30000);
        return () => clearInterval(interval);
    }, [refreshEmailsSilently, loadStats]);

    // Deep-link: auto-select email from URL param ?emailId=123
    useEffect(() => {
        const emailIdParam = searchParams.get('emailId');
        if (!emailIdParam || emails.length === 0) return;
        const emailId = Number(emailIdParam);
        if (isNaN(emailId)) return;
        const found = emails.find(e => e.id === emailId);
        if (found) {
            setSelectedEmail(found);
            setSelectedIds(new Set([found.id]));
            // Mark as read
            if (!found.isRead) {
                fetch(`/api/emails/${found.id}/mark-read`, { method: 'POST' })
                    .then(() => {
                        setEmails(prev => prev.map(e => e.id === found.id ? { ...e, isRead: true } : e));
                        loadStats();
                    })
                    .catch(err => console.error('Failed to mark as read:', err));
            }
            // Clear URL param after selecting
            setSearchParams({}, { replace: true });
        } else {
            // Email not in current folder - try fetching it directly
            fetch(`/api/emails/${emailId}`)
                .then(res => { if (res.ok) return res.json(); throw new Error('not found'); })
                .then((email: EmailItem) => {
                    setSelectedEmail(email);
                    setSelectedIds(new Set([email.id]));
                    if (!email.isRead) {
                        fetch(`/api/emails/${email.id}/mark-read`, { method: 'POST' })
                            .then(() => loadStats())
                            .catch(err => console.error('Failed to mark as read:', err));
                    }
                    setSearchParams({}, { replace: true });
                })
                .catch(() => { /* email not found, ignore */ });
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [emails, searchParams]);

    useEffect(() => {
        if (!isComposing) {
            setSelectedEmail(null);
            setSelectedIds(new Set());
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [activeFolder]);

    // Handlers
    const handleEmailClick = async (e: React.MouseEvent, email: EmailItem) => {
        if (isComposing) {
            if (await confirmDialog({ title: 'Entwurf verwerfen', message: 'Möchten Sie den Entwurf verwerfen?', variant: 'warning', confirmLabel: 'Verwerfen' })) {
                setIsComposing(false);
                setReplyToEmail(null);
            } else {
                return;
            }
        }

        if (e.ctrlKey || e.metaKey || e.shiftKey) {
            e.preventDefault();
            window.getSelection()?.removeAllRanges();
        }

        const id = email.id;
        let newSelected = new Set(selectedIds);

        if (e.ctrlKey || e.metaKey) {
            if (newSelected.has(id)) {
                newSelected.delete(id);
            } else {
                newSelected.add(id);
                lastSelectedIdRef.current = id;
            }
        } else if (e.shiftKey && lastSelectedIdRef.current) {
            const startId = lastSelectedIdRef.current;
            const currentList = filteredEmails;
            const startIndex = currentList.findIndex(x => x.id === startId);
            const endIndex = currentList.findIndex(x => x.id === id);

            if (startIndex !== -1 && endIndex !== -1) {
                const min = Math.min(startIndex, endIndex);
                const max = Math.max(startIndex, endIndex);
                newSelected = new Set();
                for (let i = min; i <= max; i++) {
                    newSelected.add(currentList[i].id);
                }
            } else {
                newSelected.add(id);
            }
        } else {
            newSelected = new Set([id]);
            lastSelectedIdRef.current = id;
        }

        setSelectedIds(newSelected);

        if (newSelected.has(id)) {
            setSelectedEmail(email);
            // Mark as read if not already read
            if (!email.isRead) {
                fetch(`/api/emails/${id}/mark-read`, { method: 'POST' })
                    .then(() => {
                        // Update local state
                        setEmails(prev => prev.map(e => e.id === id ? { ...e, isRead: true } : e));
                        // Refresh stats to update counters
                        loadStats();
                    })
                    .catch(err => console.error('Failed to mark as read:', err));
            }
        } else {
            if (selectedEmail?.id === id) setSelectedEmail(null);
        }
    };

    const handleDelete = async (e: React.MouseEvent | null, email?: EmailItem) => {
        if (e) {
            e.preventDefault();
            e.stopPropagation();
        }

        const idsToDelete = new Set<number>();
        if (email) {
            idsToDelete.add(email.id);
        } else {
            selectedIds.forEach(id => idsToDelete.add(id));
        }

        if (idsToDelete.size === 0) return;

        // Im Papierkorb: Bestätigung für endgültiges Löschen
        const isPermanentDelete = activeFolder === 'trash';
        if (isPermanentDelete) {
            const count = idsToDelete.size;
            const message = count === 1
                ? 'Möchten Sie diese E-Mail endgültig löschen? Sie wird auch vom Mailserver entfernt.'
                : `Möchten Sie ${count} E-Mails endgültig löschen? Sie werden auch vom Mailserver entfernt.`;
            if (!await confirmDialog({ title: 'Bestätigung', message: message, variant: 'danger', confirmLabel: 'Bestätigen' })) return;
        }

        const currentIds = Array.from(idsToDelete);
        setEmails(prev => prev.filter(item => !idsToDelete.has(item.id)));

        setSelectedIds(prev => {
            const next = new Set(prev);
            currentIds.forEach(id => next.delete(id));
            return next;
        });

        if (selectedEmail && idsToDelete.has(selectedEmail.id)) {
            setSelectedEmail(null);
        }

        for (const id of currentIds) {
            try {
                // Im Papierkorb: Permanent löschen (DB + Mailserver)
                // Ansonsten: Soft delete (in Papierkorb verschieben)
                const endpoint = isPermanentDelete
                    ? `/api/emails/${id}/permanent`
                    : `/api/emails/${id}`;
                await fetch(endpoint, { method: 'DELETE' });
            } catch {
                console.error('Delete failed for', id);
            }
        }

        // Stats aktualisieren nach Löschung
        loadStats();
    };

    const handleAssign = async (type: 'projekt' | 'anfrage', targetId: number) => {
        const idsToAssign = selectedIds.size > 1 ? Array.from(selectedIds) : (selectedEmail ? [selectedEmail.id] : []);
        if (idsToAssign.length === 0) return;

        for (const emailId of idsToAssign) {
            const url = type === 'projekt'
                ? `/api/emails/${emailId}/assign/${targetId}`
                : `/api/emails/${emailId}/assign/anfrage/${targetId}`;
            const res = await fetch(url, { method: 'POST' });
            if (!res.ok) throw new Error('Failed to assign');
        }

        const assignedSet = new Set(idsToAssign);
        setEmails(prev => prev.filter(e => !assignedSet.has(e.id)));
        setSelectedIds(new Set());
        setSelectedEmail(null);
        loadStats();
    };

    const handleBlockSender = async (emailIds?: number[]) => {
        const ids = emailIds || (selectedEmail ? [selectedEmail.id] : []);
        if (ids.length === 0) return;

        const count = ids.length;
        const message = count === 1
            ? `Möchten Sie den Absender "${selectedEmail?.fromAddress}" wirklich sperren?\nAlle E-Mails dieses Absenders werden in Spam verschoben.`
            : `Möchten Sie die Absender von ${count} E-Mails sperren?\nAlle E-Mails dieser Absender werden in Spam verschoben.`;
        if (!await confirmDialog({ title: "Absender sperren", message, variant: "danger", confirmLabel: "Sperren" })) return;

        // Optimistic: remove from list immediately
        const idsSet = new Set(ids);
        setEmails(prev => prev.filter(e => !idsSet.has(e.id)));
        setSelectedIds(new Set());
        setSelectedEmail(null);

        try {
            let successCount = 0;
            for (const id of ids) {
                const res = await fetch(`/api/emails/${id}/block-sender`, { method: 'POST' });
                if (res.ok) successCount++;
            }
            toast.info(successCount === 1 ? "Absender gesperrt" : `${successCount} Absender gesperrt`);
            loadStats();
        } catch (err) {
            console.error(err);
            toast.error("Fehler beim Sperren");
            // Rollback: reload on failure
            refreshEmailsSilently();
        }
    };

    const handleMarkSpam = async (emailIds?: number[]) => {
        const ids = emailIds || (selectedEmail ? [selectedEmail.id] : []);
        if (ids.length === 0) return;

        // Optimistic: remove from list immediately
        const idsSet = new Set(ids);
        setEmails(prev => prev.filter(e => !idsSet.has(e.id)));
        setSelectedIds(new Set());
        setSelectedEmail(null);

        try {
            let successCount = 0;
            for (const id of ids) {
                const res = await fetch(`/api/emails/${id}/mark-spam`, { method: 'POST' });
                if (res.ok) successCount++;
            }
            toast.info(successCount === 1 ? "Als Spam markiert – Modell lernt dazu" : `${successCount} E-Mails als Spam markiert`);
            loadStats();
        } catch (err) {
            console.error(err);
            toast.error("Fehler beim Markieren als Spam");
            refreshEmailsSilently();
        }
    };

    const handleMarkNotSpam = async (emailIds?: number[]) => {
        const ids = emailIds || (selectedEmail ? [selectedEmail.id] : []);
        if (ids.length === 0) return;

        // Optimistic: remove from list immediately
        const idsSet = new Set(ids);
        setEmails(prev => prev.filter(e => !idsSet.has(e.id)));
        setSelectedIds(new Set());
        setSelectedEmail(null);

        try {
            let successCount = 0;
            for (const id of ids) {
                const res = await fetch(`/api/emails/${id}/mark-not-spam`, { method: 'POST' });
                if (res.ok) successCount++;
            }
            toast.info(successCount === 1 ? "Kein Spam – zurück im Posteingang" : `${successCount} E-Mails als Nicht-Spam markiert`);
            loadStats();
        } catch (err) {
            console.error(err);
            toast.error("Fehler beim Markieren als Nicht-Spam");
            refreshEmailsSilently();
        }
    };

    const handleMarkAllRead = async () => {
        const unreadCount = emails.filter(e => !e.isRead).length;
        if (unreadCount === 0) { toast.info("Alle E-Mails bereits gelesen"); return; }

        // Optimistic: alle lokal auf gelesen setzen
        setEmails(prev => prev.map(e => ({ ...e, isRead: true })));

        try {
            const res = await fetch(`/api/emails/mark-all-read?folder=${encodeURIComponent(activeFolder)}`, { method: 'POST' });
            if (res.ok) {
                const data = await res.json();
                toast.success(`${data.updated} E-Mail${data.updated !== 1 ? 's' : ''} als gelesen markiert`);
                loadStats();
            } else {
                throw new Error('Fehler');
            }
        } catch {
            toast.error("Fehler beim Markieren als gelesen");
            refreshEmailsSilently();
        }
    };

    const handleScanAssignments = async () => {
        if (!await confirmDialog({ title: "E-Mails erneut prüfen", message: "Alle unzugeordneten E-Mails im Posteingang erneut prüfen?\nDies kann einen Moment dauern.", variant: "info", confirmLabel: "Prüfen" })) return;

        setLoading(true);
        try {
            const res = await fetch('/api/emails/scan-assignments', { method: 'POST' });
            if (res.ok) {
                const data = await res.json();
                toast.info(data.message);
                refreshEmailsSilently();
                loadStats();
            } else {
                toast.error("Fehler beim Scan");
            }
        } catch (err) {
            console.error(err);
            toast.error("Fehler beim Scan");
        } finally {
            setLoading(false);
        }
    };

    const formatDate = (dateStr?: string) => {
        if (!dateStr) return '-';
        try {
            const date = new Date(dateStr);
            const today = new Date();
            const isToday = date.toDateString() === today.toDateString();

            if (isToday) {
                return date.toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit' });
            }
            return date.toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit' });
        } catch {
            return dateStr;
        }
    };

    // Process HTML for preview
    const processedHtml = useMemo(() => {
        if (!selectedEmail) return '';
        let html = selectedEmail.htmlBody || selectedEmail.body || '<p class="text-slate-400 italic">Kein Inhalt</p>';

        // CID-Bilder durch echte URLs ersetzen
        if (selectedEmail.attachments) {
            selectedEmail.attachments.forEach(att => {
                const filename = att.originalFilename || att.filename || att.storedFilename;
                const url = `/api/emails/${selectedEmail.id}/attachments/${att.id}`;

                // 1. ContentId matching (mit und ohne < >)
                if (att.contentId) {
                    const cleanCid = att.contentId.replace(/[<>]/g, '');
                    html = html.replace(new RegExp(`src=["']cid:${escapeRegex(cleanCid)}["']`, 'gi'), `src="${url}"`);
                    html = html.replace(new RegExp(`src=["']cid:${escapeRegex(att.contentId)}["']`, 'gi'), `src="${url}"`);
                }

                // 2. Fallback: Filename im CID (z.B. cid:image003.jpg@01DC2C56.D1072BF0)
                if (filename) {
                    const baseName = filename.replace(/\.[^.]+$/, ''); // ohne Extension
                    html = html.replace(new RegExp(`src=["']cid:${escapeRegex(baseName)}[^"']*["']`, 'gi'), `src="${url}"`);
                }
            });
        }

        // 3. Verbleibende CID-Referenzen durch Platzhalter ersetzen (verhindert broken images)
        html = html.replace(/src=["']cid:[^"']+["']/gi, 'src="data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7" style="display:none"');

        return html;
    }, [selectedEmail]);

    // Helper: Regex-Zeichen escapen
    function escapeRegex(str: string): string {
        return str.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    }

    const visibleAttachments = useMemo(() => {
        if (!selectedEmail) return [];
        return (selectedEmail.attachments || []).filter(att => !att.inline);
    }, [selectedEmail]);

    // Global search with debounce
    useEffect(() => {
        if (!isGlobalSearch || !searchQuery.trim() || searchQuery.trim().length < 2) {
            setGlobalSearchResults([]);
            setGlobalSearchLoading(false);
            return;
        }
        setGlobalSearchLoading(true);
        const timeout = setTimeout(async () => {
            try {
                const res = await fetch(`/api/emails/search?q=${encodeURIComponent(searchQuery.trim())}`);
                if (res.ok) {
                    const data = await res.json();
                    setGlobalSearchResults(Array.isArray(data) ? data : []);
                } else {
                    setGlobalSearchResults([]);
                }
            } catch (err) {
                console.error('Global search failed:', err);
                setGlobalSearchResults([]);
            } finally {
                setGlobalSearchLoading(false);
            }
        }, 350);
        return () => clearTimeout(timeout);
    }, [isGlobalSearch, searchQuery]);

    // Filter emails by search
    const filteredEmails = useMemo(() => {
        if (isGlobalSearch) return globalSearchResults;
        if (!searchQuery.trim()) return emails;
        const q = searchQuery.toLowerCase();
        return emails.filter(e =>
            e.subject?.toLowerCase().includes(q) ||
            e.fromAddress?.toLowerCase().includes(q) ||
            getSenderName(e).toLowerCase().includes(q)
        );
    }, [emails, searchQuery, isGlobalSearch, globalSearchResults]);

    // Right Pane Content Logic
    const renderRightPane = () => {
        // Settings View
        if (showSettings) {
            return (
                <div className="flex-1 overflow-auto p-6 bg-white">
                    <div className="flex items-center justify-between mb-6">
                        <div>
                            <h2 className="text-xl font-bold text-slate-900">E-Mail-Einstellungen</h2>
                            <p className="text-sm text-slate-500">Signaturen und Abwesenheitsnotizen verwalten</p>
                        </div>
                        <Button
                            variant="outline"
                            size="sm"
                            onClick={() => setShowSettings(false)}
                            className="text-slate-600"
                        >
                            Schließen
                        </Button>
                    </div>
                    <EmailSettings />
                </div>
            );
        }

        if (isComposing) {
            // Determine initial props for reply
            let initialRecipient = '';
            let initialSubject = '';
            let initialBody = '';

            if (replyToEmail) {
                const senderName = getSenderName(replyToEmail);
                const senderEmailPattern = /<(.+)>/;
                const match = replyToEmail.fromAddress?.match(senderEmailPattern);
                const senderEmail = match ? match[1] : (replyToEmail.fromAddress || '');

                initialRecipient = senderEmail ? `"${senderName}" <${senderEmail}>` : senderName;
                initialSubject = replyToEmail.subject?.startsWith('Re:') ? replyToEmail.subject : `Re: ${replyToEmail.subject || ''}`;

                // Quote body
                const date = new Date(replyToEmail.sentAt || '').toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric' }) + ' ' + new Date(replyToEmail.sentAt || '').toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit' });
                // Sanitize content to avoid breaking the editor/page
                let cleanBody = replyToEmail.htmlBody || replyToEmail.body || '';
                try {
                    const parser = new DOMParser();
                    const doc = parser.parseFromString(cleanBody, 'text/html');
                    doc.querySelectorAll('script, style, link').forEach(el => el.remove());
                    cleanBody = doc.body.innerHTML;
                } catch (e) {
                    console.error('Failed to sanitize email body', e);
                }

                initialBody = `<p><br></p><div class="email-quote" style="border-left: 1px solid #ccc; padding-left: 1rem; color: #666;">
                    <p>Am ${date} schrieb ${senderName}:</p>
                    ${cleanBody}
                </div>`;
            }

            return (
                <EmailComposeForm
                    onClose={handleComposeClose}
                    onSuccess={handleComposeSuccess}
                    initialRecipient={initialRecipient}
                    initialSubject={initialSubject}
                    initialBody={initialBody}
                    projektId={replyToEmail?.projektId}
                    anfrageId={replyToEmail?.anfrageId}
                />
            );
        }

        // Multi-select bulk actions
        if (selectedIds.size > 1) {
            const bulkIds = Array.from(selectedIds);
            return (
                <div className="flex-1 flex flex-col items-center justify-center gap-6 p-8">
                    <div className="w-20 h-20 rounded-2xl bg-rose-50 flex items-center justify-center">
                        <CheckSquare className="w-10 h-10 text-rose-400" />
                    </div>
                    <div className="text-center">
                        <p className="text-2xl font-bold text-slate-900">{selectedIds.size} E-Mails ausgewählt</p>
                        <p className="text-sm text-slate-500 mt-1">Wählen Sie eine Aktion für die ausgewählten E-Mails</p>
                    </div>

                    <div className="flex flex-col gap-2 w-full max-w-xs">
                        <Button
                            variant="outline"
                            onClick={() => setShowAssignModal(true)}
                            className="w-full gap-2 justify-start h-11 border-slate-200 hover:bg-rose-50 hover:text-rose-700 hover:border-rose-300"
                        >
                            <FolderPlus className="w-4 h-4" />
                            Zuordnen ({selectedIds.size})
                        </Button>

                        {activeFolder === 'spam' ? (
                            <Button
                                variant="outline"
                                onClick={() => handleMarkNotSpam(bulkIds)}
                                className="w-full gap-2 justify-start h-11 border-slate-200 hover:bg-emerald-50 hover:text-emerald-700 hover:border-emerald-300"
                            >
                                <ShieldCheck className="w-4 h-4" />
                                Kein Spam ({selectedIds.size})
                            </Button>
                        ) : (
                            <Button
                                variant="outline"
                                onClick={() => handleMarkSpam(bulkIds)}
                                className="w-full gap-2 justify-start h-11 border-slate-200 hover:bg-red-50 hover:text-red-700 hover:border-red-300"
                            >
                                <ShieldX className="w-4 h-4" />
                                Als Spam markieren ({selectedIds.size})
                            </Button>
                        )}

                        <Button
                            variant="outline"
                            onClick={() => handleBlockSender(bulkIds)}
                            className="w-full gap-2 justify-start h-11 border-slate-200 hover:bg-red-50 hover:text-red-700 hover:border-red-300"
                        >
                            <ShieldAlert className="w-4 h-4" />
                            Absender sperren ({selectedIds.size})
                        </Button>

                        <div className="h-px bg-slate-200 my-1" />

                        <Button
                            variant="outline"
                            onClick={(e) => handleDelete(e)}
                            className="w-full gap-2 justify-start h-11 border-red-200 text-red-600 hover:bg-red-50 hover:text-red-700 hover:border-red-300"
                        >
                            <Trash2 className="w-4 h-4" />
                            Löschen ({selectedIds.size})
                        </Button>
                    </div>

                    <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => { setSelectedIds(new Set()); setSelectedEmail(null); }}
                        className="text-slate-500 hover:text-slate-700 mt-2"
                    >
                        <X className="w-4 h-4 mr-1" />
                        Auswahl aufheben
                    </Button>
                </div>
            );
        }

        if (selectedEmail) {
            return (
                <>
                    {/* Header */}
                    <div className="p-6 border-b border-slate-200">
                        <div className="flex items-start justify-between gap-4">
                            <div className="flex-1 min-w-0">
                                <h2 className="text-xl font-bold text-slate-900 mb-2">
                                    {selectedEmail.subject || '(Kein Betreff)'}
                                </h2>
                                <div className="flex items-center gap-3 text-sm text-slate-600">
                                    <div className={cn(
                                        "w-9 h-9 rounded-full flex items-center justify-center text-white font-medium text-sm shadow-sm",
                                        selectedEmail.direction === 'IN' ? "bg-rose-500" : "bg-emerald-500"
                                    )}>
                                        {getDisplayName(selectedEmail).charAt(0).toUpperCase()}
                                    </div>
                                    <div>
                                        <p className="font-medium text-slate-900 flex items-center gap-2">
                                            {getSenderName(selectedEmail)}
                                            <span className="text-slate-500 font-normal text-xs text-muted-foreground hidden sm:inline-block">
                                                &lt;{selectedEmail.fromAddress}&gt;
                                            </span>
                                        </p>
                                        <p className="text-xs text-slate-500 mt-0.5">
                                            An: <span className="text-slate-700">{getRecipientName(selectedEmail)}</span>
                                        </p>
                                    </div>
                                </div>
                                <p className="text-xs text-slate-400 mt-2 flex items-center gap-1">
                                    <Clock className="w-3 h-3" />
                                    {selectedEmail.sentAt ? (
                                        <>
                                            {new Date(selectedEmail.sentAt).toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric' })} {new Date(selectedEmail.sentAt).toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit' })}
                                        </>
                                    ) : ''}
                                </p>
                            </div>

                            {/* Actions */}
                            <div className="flex items-center gap-1.5">
                                {activeFolder === 'spam' ? (
                                    <Button
                                        variant="ghost"
                                        size="sm"
                                        onClick={() => handleMarkNotSpam()}
                                        className="text-slate-500 hover:text-emerald-600 hover:bg-emerald-50"
                                        title="Kein Spam"
                                    >
                                        <ShieldCheck className="w-4 h-4" />
                                    </Button>
                                ) : (
                                    <Button
                                        variant="ghost"
                                        size="sm"
                                        onClick={() => handleMarkSpam()}
                                        className="text-slate-500 hover:text-red-600 hover:bg-red-50"
                                        title="Als Spam markieren"
                                    >
                                        <ShieldX className="w-4 h-4" />
                                    </Button>
                                )}
                                <Button
                                    variant="ghost"
                                    size="sm"
                                    onClick={() => handleBlockSender()}
                                    className="text-slate-500 hover:text-red-600 hover:bg-red-50"
                                    title="Absender sperren"
                                >
                                    <ShieldAlert className="w-4 h-4" />
                                </Button>
                                <Button
                                    variant="outline"
                                    size="sm"
                                    onClick={() => handleReply(selectedEmail)}
                                    className="gap-1.5 text-slate-700 border-slate-300 hover:bg-rose-50 hover:text-rose-700 hover:border-rose-300"
                                >
                                    <Reply className="w-4 h-4" /> Antworten
                                </Button>
                                <Button
                                    variant="outline"
                                    size="sm"
                                    onClick={() => setShowAssignModal(true)}
                                    className="gap-1.5 border-slate-300 text-slate-700 hover:bg-rose-50 hover:text-rose-700 hover:border-rose-300"
                                >
                                    <FolderPlus className="w-4 h-4" />
                                    Zuordnen
                                </Button>
                                <Button
                                    variant="ghost"
                                    size="sm"
                                    onClick={(e) => handleDelete(e, selectedEmail)}
                                    className="text-slate-500 hover:text-red-600 hover:bg-red-50"
                                >
                                    <Trash2 className="w-4 h-4" />
                                </Button>
                            </div>
                        </div>
                    </div>

                    {/* Body */}
                    <div className="flex-1 overflow-auto p-6">
                        <EmailContentFrame
                            html={processedHtml}
                            className="bg-white"
                        />

                        {/* Attachments */}
                        {visibleAttachments.length > 0 && (
                            <div className="mt-6 pt-6 border-t border-slate-200">
                                <h3 className="text-sm font-semibold text-slate-900 mb-3 flex items-center gap-2">
                                    <Paperclip className="w-4 h-4" />
                                    Anhänge ({visibleAttachments.length})
                                </h3>
                                <div className="grid grid-cols-2 gap-2">
                                    {visibleAttachments.map((att, idx) => (
                                        <EmailAttachmentCard
                                            key={att.id || idx}
                                            attachment={att}
                                            email={selectedEmail}
                                            onPreview={(url, type, name) => setPreviewAttachment({ url, type, name })}
                                        />
                                    ))}
                                </div>
                            </div>
                        )}
                    </div>
                </>
            );
        }

        return (
            <div className="flex-1 flex flex-col items-center justify-center text-slate-400 gap-3">
                <div className="w-20 h-20 rounded-2xl bg-slate-100 flex items-center justify-center">
                    <Mail className="w-10 h-10 text-slate-300" />
                </div>
                <div className="text-center">
                    <p className="text-base font-medium text-slate-500">Keine E-Mail ausgewählt</p>
                    <p className="text-sm text-slate-400 mt-1">Wählen Sie eine E-Mail aus der Liste</p>
                </div>
            </div>
        );
    };

    return (
        <div className="flex bg-slate-100 overflow-hidden -m-8 h-[calc(100%+4rem)] w-[calc(100%+4rem)]">
            {/* Left Sidebar - Folders */}
            <div className="w-64 bg-slate-50/80 border-r border-slate-200/80 flex flex-col flex-shrink-0">
                {/* Header */}
                <div className="p-4 border-b border-slate-200/80 bg-white space-y-3">
                    <div className="flex items-center gap-2.5">
                        <div className="w-9 h-9 rounded-xl bg-gradient-to-br from-rose-500 to-rose-600 flex items-center justify-center shadow-sm shadow-rose-200">
                            <Mail className="w-4.5 h-4.5 text-white" />
                        </div>
                        <div>
                            <h2 className="font-bold text-slate-900 text-sm leading-tight">E-Mail Center</h2>
                            <p className="text-[10px] text-slate-400 leading-tight">Postfach verwalten</p>
                        </div>
                    </div>
                    <Button
                        onClick={handleComposeNew}
                        className="w-full bg-rose-600 hover:bg-rose-700 text-white shadow-sm shadow-rose-200/50 gap-2 h-10 font-semibold"
                    >
                        <PenSquare className="w-4 h-4" />
                        Neue E-Mail
                    </Button>
                    <Button
                        variant="outline"
                        onClick={handleScanAssignments}
                        className="w-full gap-2 text-slate-600 border-slate-200 hover:bg-white hover:border-rose-200 hover:text-rose-600 transition-colors"
                        title="Erneute Prüfung der Zuordnung aller Mails im Posteingang"
                    >
                        <RefreshCw className="w-4 h-4" />
                        Auto-Zuordnung
                    </Button>
                </div>

                {/* Folders */}
                <div className="flex-1 overflow-y-auto p-2.5 space-y-0.5">
                    {/* Main Folders */}
                    <button
                        onClick={() => setActiveFolder('inbox')}
                        className={cn(
                            "w-full flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-all duration-200 cursor-pointer",
                            activeFolder === 'inbox'
                                ? "bg-rose-50 text-rose-700 shadow-sm ring-1 ring-rose-200"
                                : "text-slate-700 hover:bg-slate-50"
                        )}
                    >
                        <Inbox className="w-4 h-4" />
                        <span className="flex-1 text-left">Posteingang</span>
                        {folderCounts.inbox > 0 && (
                            <span className={cn(
                                "text-xs font-semibold px-2 py-0.5 rounded-full min-w-[1.25rem] text-center",
                                activeFolder === 'inbox'
                                    ? "bg-rose-200 text-rose-800"
                                    : "bg-rose-100 text-rose-700"
                            )}>
                                {folderCounts.inbox}
                            </span>
                        )}
                    </button>

                    <button
                        onClick={() => setActiveFolder('sent')}
                        className={cn(
                            "w-full flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-all duration-200 cursor-pointer",
                            activeFolder === 'sent'
                                ? "bg-rose-50 text-rose-700 shadow-sm ring-1 ring-rose-200"
                                : "text-slate-700 hover:bg-slate-50"
                        )}
                    >
                        <Send className="w-4 h-4" />
                        <span className="flex-1 text-left">Gesendet</span>
                        {folderCounts.sent > 0 && (
                            <span className="text-xs text-slate-500 tabular-nums">
                                {folderCounts.sent}
                            </span>
                        )}
                    </button>

                    <div className="h-px bg-slate-100 my-2" />

                    {/* Filter Folders */}
                    <div className="px-3 py-1.5 flex items-center justify-between">
                        <span className="text-xs font-semibold text-slate-400 uppercase tracking-wider">Zugeordnet</span>
                        <button onClick={() => setExpandedFilters(!expandedFilters)} className="cursor-pointer p-0.5 rounded hover:bg-slate-100 transition-colors">
                            {expandedFilters ? <ChevronDown className="w-3.5 h-3.5 text-slate-400" /> : <ChevronRight className="w-3.5 h-3.5 text-slate-400" />}
                        </button>
                    </div>

                    {expandedFilters && (
                        <>
                            <button
                                onClick={() => setActiveFolder('unassigned')}
                                className={cn(
                                    "w-full flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-all duration-200 cursor-pointer",
                                    activeFolder === 'unassigned'
                                        ? "bg-rose-50 text-rose-700 shadow-sm ring-1 ring-rose-200"
                                        : "text-slate-700 hover:bg-slate-50"
                                )}
                            >
                                <AlertCircle className="w-4 h-4" />
                                <span className="flex-1 text-left">Nicht zugeordnet</span>
                                {folderCounts.unassigned > 0 && (
                                    <span className="bg-amber-100 text-amber-700 text-xs font-semibold px-2 py-0.5 rounded-full">
                                        {folderCounts.unassigned}
                                    </span>
                                )}
                            </button>

                            <button
                                onClick={() => setActiveFolder('inquiries')}
                                className={cn(
                                    "w-full flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-all duration-200 cursor-pointer",
                                    activeFolder === 'inquiries'
                                        ? "bg-rose-50 text-rose-700 shadow-sm ring-1 ring-rose-200"
                                        : "text-slate-700 hover:bg-slate-50"
                                )}
                            >
                                <HelpCircle className="w-4 h-4" />
                                <span className="flex-1 text-left">Anfragen</span>
                                {folderCounts.inquiries > 0 && (
                                    <span className="bg-rose-100 text-rose-700 text-xs font-semibold px-2 py-0.5 rounded-full">
                                        {folderCounts.inquiries}
                                    </span>
                                )}
                            </button>

                            <div className="h-px bg-slate-200 my-2" />

                            <button
                                onClick={() => setActiveFolder('projects')}
                                className={cn(
                                    "w-full flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-all duration-200 cursor-pointer",
                                    activeFolder === 'projects'
                                        ? "bg-rose-50 text-rose-700 shadow-sm ring-1 ring-rose-200"
                                        : "text-slate-700 hover:bg-slate-50"
                                )}
                            >
                                <Briefcase className="w-4 h-4" />
                                <span className="flex-1 text-left">Projekte</span>
                                {folderCounts.projects > 0 && (
                                    <span className="text-xs text-slate-500 tabular-nums">
                                        {folderCounts.projects}
                                    </span>
                                )}
                            </button>

                            <button
                                onClick={() => setActiveFolder('offers')}
                                className={cn(
                                    "w-full flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-all duration-200 cursor-pointer",
                                    activeFolder === 'offers'
                                        ? "bg-rose-50 text-rose-700 shadow-sm ring-1 ring-rose-200"
                                        : "text-slate-700 hover:bg-slate-50"
                                )}
                            >
                                <FileText className="w-4 h-4" />
                                <span className="flex-1 text-left">Angebote</span>
                                {folderCounts.offers > 0 && (
                                    <span className="text-xs text-slate-500 tabular-nums">
                                        {folderCounts.offers}
                                    </span>
                                )}
                            </button>

                            <button
                                onClick={() => setActiveFolder('suppliers')}
                                className={cn(
                                    "w-full flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-all duration-200 cursor-pointer",
                                    activeFolder === 'suppliers'
                                        ? "bg-rose-50 text-rose-700 shadow-sm ring-1 ring-rose-200"
                                        : "text-slate-700 hover:bg-slate-50"
                                )}
                            >
                                <Package className="w-4 h-4" />
                                <span className="flex-1 text-left">Lieferanten</span>
                                {folderCounts.suppliers > 0 && (
                                    <span className="text-xs text-slate-500 tabular-nums">
                                        {folderCounts.suppliers}
                                    </span>
                                )}
                            </button>
                        </>
                    )}

                    <div className="h-px bg-slate-100 my-2" />

                    <button
                        onClick={() => setActiveFolder('trash')}
                        className={cn(
                            "w-full flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-all duration-200 cursor-pointer",
                            activeFolder === 'trash'
                                ? "bg-rose-50 text-rose-700 shadow-sm ring-1 ring-rose-200"
                                : "text-slate-700 hover:bg-slate-50"
                        )}
                    >
                        <Trash2 className="w-4 h-4" />
                        <span className="flex-1 text-left">Papierkorb</span>
                        {folderCounts.trash > 0 && (
                            <span className="text-xs text-slate-500 tabular-nums">
                                {folderCounts.trash}
                            </span>
                        )}
                    </button>

                    <button
                        onClick={() => setActiveFolder('spam')}
                        className={cn(
                            "w-full flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-all duration-200 cursor-pointer",
                            activeFolder === 'spam'
                                ? "bg-rose-50 text-rose-700 shadow-sm ring-1 ring-rose-200"
                                : "text-slate-700 hover:bg-slate-50"
                        )}
                    >
                        <ShieldAlert className="w-4 h-4" />
                        <span className="flex-1 text-left">Spam</span>
                        {folderCounts.spam > 0 && (
                            <span className="text-xs text-slate-500 tabular-nums">
                                {folderCounts.spam}
                            </span>
                        )}
                    </button>

                    <button
                        onClick={() => setActiveFolder('newsletter')}
                        className={cn(
                            "w-full flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-all duration-200 cursor-pointer",
                            activeFolder === 'newsletter'
                                ? "bg-rose-50 text-rose-700 shadow-sm ring-1 ring-rose-200"
                                : "text-slate-700 hover:bg-slate-50"
                        )}
                    >
                        <Newspaper className="w-4 h-4" />
                        <span className="flex-1 text-left">Newsletter</span>
                        {folderCounts.newsletter > 0 && (
                            <span className="text-xs text-slate-500 tabular-nums">
                                {folderCounts.newsletter}
                            </span>
                        )}
                    </button>

                    <div className="h-px bg-slate-100 my-2" />

                    {/* Settings Button */}
                    <button
                        onClick={() => { setShowSettings(true); setSelectedEmail(null); }}
                        className={cn(
                            "w-full flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-all duration-200 cursor-pointer",
                            showSettings
                                ? "bg-rose-50 text-rose-700 shadow-sm ring-1 ring-rose-200"
                                : "text-slate-700 hover:bg-slate-50"
                        )}
                    >
                        <Settings className="w-4 h-4" />
                        <span className="flex-1 text-left">Einstellungen</span>
                    </button>
                </div>
            </div>

            {/* Middle List - Email List */}
            <div className="w-96 bg-white border-r border-slate-200 flex flex-col flex-shrink-0">
                {/* Ordner-Header mit "Alle gelesen"-Button */}
                {!isGlobalSearch && activeFolder !== 'sent' && emails.some(e => !e.isRead) && (
                    <div className="flex items-center justify-between px-3 py-2 bg-slate-50 border-b border-slate-200">
                        <span className="text-xs text-slate-500 font-medium">
                            {emails.filter(e => !e.isRead).length} ungelesen
                        </span>
                        <button
                            onClick={handleMarkAllRead}
                            className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-xs font-medium text-rose-600 hover:bg-rose-50 hover:text-rose-700 transition-colors cursor-pointer"
                            title="Alle als gelesen markieren"
                        >
                            <MailCheck className="w-3.5 h-3.5" />
                            Alle gelesen
                        </button>
                    </div>
                )}
                {/* Search */}
                <div className="p-3 border-b border-slate-200 space-y-2">
                    <div className="relative">
                        {isGlobalSearch ? (
                            <Globe className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-rose-500" />
                        ) : (
                            <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
                        )}
                        <Input
                            placeholder={isGlobalSearch ? "Globale Suche (alle Ordner)..." : "In diesem Ordner suchen..."}
                            className={cn(
                                "pl-9 pr-9 border-slate-200 focus:border-rose-300 focus:ring-rose-200 transition-colors",
                                isGlobalSearch ? "bg-rose-50/50 border-rose-200" : "bg-slate-50 focus:bg-white"
                            )}
                            value={searchQuery}
                            onChange={(e) => setSearchQuery(e.target.value)}
                        />
                        {searchQuery && (
                            <button
                                onClick={() => { setSearchQuery(''); setGlobalSearchResults([]); }}
                                className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600 cursor-pointer"
                            >
                                <X className="w-4 h-4" />
                            </button>
                        )}
                    </div>
                    <div className="flex items-center gap-2">
                        <button
                            onClick={() => { setIsGlobalSearch(false); setGlobalSearchResults([]); }}
                            className={cn(
                                "flex-1 flex items-center justify-center gap-1.5 px-2 py-1.5 rounded-md text-xs font-medium transition-all cursor-pointer",
                                !isGlobalSearch
                                    ? "bg-slate-100 text-slate-800 shadow-sm"
                                    : "text-slate-500 hover:bg-slate-50"
                            )}
                        >
                            <Search className="w-3 h-3" />
                            Ordner
                        </button>
                        <button
                            onClick={() => setIsGlobalSearch(true)}
                            className={cn(
                                "flex-1 flex items-center justify-center gap-1.5 px-2 py-1.5 rounded-md text-xs font-medium transition-all cursor-pointer",
                                isGlobalSearch
                                    ? "bg-rose-100 text-rose-700 shadow-sm"
                                    : "text-slate-500 hover:bg-slate-50"
                            )}
                        >
                            <Globe className="w-3 h-3" />
                            Alle Ordner
                        </button>
                    </div>
                </div>

                {/* Global search info banner */}
                {isGlobalSearch && (
                    <div className="px-3 py-2 bg-rose-50 border-b border-rose-100 text-xs text-rose-600 flex items-center gap-2">
                        <Globe className="w-3.5 h-3.5 flex-shrink-0" />
                        {searchQuery.trim().length < 2
                            ? "Mindestens 2 Zeichen eingeben..."
                            : globalSearchLoading
                                ? "Suche läuft..."
                                : `${filteredEmails.length} Ergebnis${filteredEmails.length !== 1 ? 'se' : ''} gefunden`}
                    </div>
                )}

                {/* List */}
                <div className="flex-1 overflow-y-auto">
                    {(loading || (isGlobalSearch && globalSearchLoading)) ? (
                        <div className="flex flex-col items-center justify-center h-48 text-slate-400 gap-3">
                            <RefreshCw className="w-6 h-6 animate-spin text-rose-400" />
                            <p className="text-sm font-medium">Lade E-Mails...</p>
                        </div>
                    ) : filteredEmails.length === 0 ? (
                        <div className="flex flex-col items-center justify-center h-48 text-slate-400 gap-2">
                            <div className="w-14 h-14 rounded-full bg-slate-100 flex items-center justify-center">
                                {isGlobalSearch ? <Globe className="w-7 h-7 text-slate-300" /> : <Mail className="w-7 h-7 text-slate-300" />}
                            </div>
                            <p className="text-sm font-medium text-slate-500">
                                {isGlobalSearch
                                    ? (searchQuery.trim().length < 2 ? 'Suchbegriff eingeben' : 'Keine Treffer')
                                    : 'Keine E-Mails'}
                            </p>
                            <p className="text-xs text-slate-400">
                                {isGlobalSearch
                                    ? 'Suche in Betreff, Absender und Inhalt'
                                    : 'In diesem Ordner ist nichts vorhanden'}
                            </p>
                        </div>
                    ) : (
                        <div className="divide-y divide-slate-100">
                            {filteredEmails.map(email => (
                                <div
                                    key={email.id}
                                    onClick={(e) => handleEmailClick(e, email)}
                                    className={cn(
                                        "px-4 py-3 cursor-pointer transition-all duration-150 border-l-[3px]",
                                        selectedIds.has(email.id)
                                            ? "bg-rose-50/80 border-l-rose-500"
                                            : email.isRead
                                                ? "bg-white border-l-transparent hover:bg-slate-50"
                                                : "bg-white border-l-rose-400 hover:bg-rose-50/30"
                                    )}
                                >
                                    <div className="flex items-start justify-between gap-2 mb-1">
                                        <p className={cn(
                                            "text-sm truncate",
                                            !email.isRead ? "font-bold text-slate-900" : "font-medium text-slate-700"
                                        )}>
                                            {getDisplayName(email)}
                                        </p>
                                        <span className="text-xs text-slate-400 whitespace-nowrap">
                                            {formatDate(email.sentAt)}
                                        </span>
                                    </div>
                                    <p className={cn(
                                        "text-sm mb-1 truncate",
                                        !email.isRead ? "font-semibold text-slate-800" : "text-slate-600"
                                    )}>
                                        {email.subject || '(Kein Betreff)'}
                                    </p>
                                    <p className="text-xs text-slate-400 line-clamp-2">
                                        {(email.body || '...').replace(/<[^>]*>/g, ' ').replace(/\s+/g, ' ').trim().substring(0, 150)}
                                    </p>

                                    {/* Badges */}
                                    <div className="flex gap-2 mt-2">
                                        {email.attachments && email.attachments.length > 0 && (
                                            <div className="flex items-center gap-1 text-xs text-slate-500 bg-slate-100 px-1.5 py-0.5 rounded">
                                                <Paperclip className="w-3 h-3" />
                                                {email.attachments.length}
                                            </div>
                                        )}
                                        {email.spamScore != null && email.spamScore > 0 && (
                                            <div className={cn(
                                                "flex items-center gap-1 text-xs px-1.5 py-0.5 rounded",
                                                email.spamScore >= 90 ? "text-red-700 bg-red-100 border border-red-200 font-semibold" :
                                                email.spamScore >= 50 ? "text-orange-700 bg-orange-50 border border-orange-200" :
                                                "text-slate-500 bg-slate-50 border border-slate-200"
                                            )}>
                                                <ShieldAlert className="w-3 h-3" />
                                                {email.spamScore}% Spam
                                            </div>
                                        )}
                                        {email.zuordnungTyp && email.zuordnungTyp !== 'KEINE' && (
                                            <div className="flex items-center gap-1 text-xs text-rose-600 bg-rose-50 px-1.5 py-0.5 rounded border border-rose-100">
                                                <FolderPlus className="w-3 h-3" />
                                                {email.zuordnungTyp}
                                            </div>
                                        )}
                                    </div>
                                </div>
                            ))}
                        </div>
                    )}
                </div>

                {/* Footer Status */}
                <div className="px-4 py-2.5 border-t border-slate-200 text-xs text-slate-500 flex items-center justify-between">
                    <span>
                        {isGlobalSearch && <Globe className="w-3 h-3 inline mr-1 text-rose-500" />}
                        {filteredEmails.length} {filteredEmails.length === 1 ? 'Nachricht' : 'Nachrichten'}
                    </span>
                    {selectedIds.size > 0 && (
                        <span className="text-rose-600 font-medium">{selectedIds.size} ausgewählt</span>
                    )}
                </div>
            </div>

            {/* Right Pane - Preview/Compose */}
            <div className="flex-1 bg-slate-50 flex flex-col min-w-0">
                {renderRightPane()}
            </div>

            {/* Assignment Modal */}
            <AssignModal
                isOpen={showAssignModal}
                onClose={() => setShowAssignModal(false)}
                onAssign={handleAssign}
                emailSubject={selectedEmail?.subject || ''}
                emailId={selectedEmail?.id}
            />

            {/* Standard ImageViewer for Images */}
            {previewAttachment?.type === 'image' && (
                <ImageViewer
                    src={previewAttachment.url}
                    alt={previewAttachment.name}
                    onClose={() => setPreviewAttachment(null)}
                    images={visibleAttachments
                        .filter(att => isImageAttachment(att.originalFilename || att.filename || att.storedFilename || ''))
                        .map(att => ({
                            url: `/api/emails/${selectedEmail?.id}/attachments/${att.id}`,
                            name: att.originalFilename || att.filename || att.storedFilename || 'Bild'
                        }))}
                    startIndex={visibleAttachments
                        .filter(att => isImageAttachment(att.originalFilename || att.filename || att.storedFilename || ''))
                        .findIndex(att => `/api/emails/${selectedEmail?.id}/attachments/${att.id}` === previewAttachment.url)}
                />
            )}

            {/* Existing Dialog for PDFs and others */}
            <Dialog
                open={!!previewAttachment && previewAttachment.type !== 'image'}
                onOpenChange={(open) => !open && setPreviewAttachment(null)}
            >
                <DialogContent className="max-w-[95vw] w-[95vw] h-[95vh] flex flex-col">
                    <DialogHeader className="shrink-0">
                        <DialogTitle className="flex items-center justify-between">
                            <span className="truncate">{previewAttachment?.name}</span>
                            <div className="flex gap-2">
                                <Button variant="outline" size="sm" onClick={() => {
                                    if (previewAttachment?.url) {
                                        const a = document.createElement('a');
                                        a.href = previewAttachment.url;
                                        a.download = previewAttachment.name;
                                        a.click();
                                    }
                                }}>
                                    <Download className="w-4 h-4 mr-1" /> Download
                                </Button>
                            </div>
                        </DialogTitle>
                    </DialogHeader>
                    <div className="flex-1 bg-slate-100 rounded-lg overflow-hidden flex items-center justify-center p-2 border border-slate-200 min-h-0">
                        {previewAttachment?.type === 'pdf' ? (
                            <PdfCanvasViewer
                                url={previewAttachment.url}
                                className="w-full h-full min-h-[80vh] overflow-y-auto overflow-x-hidden"
                            />
                        ) : (
                            <div className="text-center text-slate-500">
                                <File className="w-16 h-16 mx-auto mb-4" />
                                <p>Keine Vorschau verfügbar</p>
                            </div>
                        )}
                    </div>
                </DialogContent>
            </Dialog>
        </div>
    );
}
