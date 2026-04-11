import React, { useState, useEffect, useCallback, useMemo, useRef } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
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
    PenSquare,
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
    MailCheck,
    MessagesSquare,
    DatabaseZap,
    RotateCcw,
    FolderInput
} from 'lucide-react';
import {
    DndContext,
    DragOverlay,
    PointerSensor,
    useSensor,
    useSensors,
    useDraggable,
    useDroppable,
    type DragStartEvent,
    type DragEndEvent
} from '@dnd-kit/core';
import { Button } from '../components/ui/button';
import { Input } from '../components/ui/input';
import { cn } from '../lib/utils';
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '../components/ui/dialog';
import { EmailComposeForm } from '../components/EmailComposeForm';
import EmailSettings from '../components/EmailSettings';
import { ImageViewer } from '../components/ui/image-viewer';
import { useToast } from '../components/ui/toast';
import { useConfirm } from '../components/ui/confirm-dialog';
import { EmailThreadView } from '../components/EmailThreadView';
import type { EmailThread } from '../components/EmailThreadView';


/** Anhang-Daten aus der Backend-API (UnifiedEmailDto.AttachmentDto). */
interface EmailAttachment {
    id: number;
    originalFilename?: string;
    filename?: string;
    storedFilename?: string;
    mimeType?: string;
    fileSize?: number;
    contentId?: string;
    inline?: boolean;
}

/** Prüft ob ein Dateiname auf ein gängiges Bildformat hindeutet. */
function isImageAttachment(filename: string): boolean {
    return /\.(jpg|jpeg|png|gif|webp|bmp|svg)$/i.test(filename);
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
    // Thread-Informationen
    parentEmailId?: number;   // null/undefined = Thread-Wurzel
    replyCount?: number;      // Anzahl direkter Antworten
}

// Folder Types
type FolderType = 'inbox' | 'sent' | 'trash' | 'spam' | 'newsletter' | 'projects' | 'offers' | 'suppliers' | 'unassigned';




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
                ? `/api/projekte/suche?q=${encodeURIComponent(searchQuery)}`
                : `/api/anfragen?q=${encodeURIComponent(searchQuery)}`;
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
                            results.map((item: { id: number; bauvorhaben?: string; name?: string; kunde?: string; kundenName?: string; anfragesnummer?: string }) => (
                                <button
                                    key={item.id}
                                    onClick={() => handleSelect(searchType, item.id)}
                                    disabled={assigning}
                                    className="w-full text-left p-3 rounded-lg hover:bg-rose-50 transition-colors border border-slate-200"
                                >
                                    <p className="font-medium text-slate-900">{item.bauvorhaben || item.name || item.kundenName}</p>
                                    {(item.kunde || item.kundenName) && <p className="text-sm text-slate-500">{item.kunde || item.kundenName}</p>}
                                    {item.anfragesnummer && <p className="text-xs text-slate-400">{item.anfragesnummer}</p>}
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
const VALID_FOLDERS: FolderType[] = ['inbox', 'sent', 'trash', 'spam', 'newsletter', 'projects', 'offers', 'suppliers', 'unassigned'];

// ─────────────────────────────────────────────────────────────
// DnD helper components (Drag & Drop für Ordner-Verschieben)
// ─────────────────────────────────────────────────────────────

interface DroppableFolderButtonProps {
    folderId: FolderType;
    icon: React.ComponentType<{ className?: string }>;
    label: string;
    count?: number;
    isActive: boolean;
    onClick: () => void;
    /** wenn true, ist dies ein gültiges Drop-Ziel (inbox/trash/spam/newsletter) */
    droppable: boolean;
    /** wenn true, läuft gerade ein Drag – Non-Drop-Ordner werden dann visuell ausgegraut */
    dragActive: boolean;
    countVariant?: 'rose' | 'amber' | 'slate';
}

function DroppableFolderButton({
    folderId,
    icon: Icon,
    label,
    count,
    isActive,
    onClick,
    droppable,
    dragActive,
    countVariant = 'slate'
}: DroppableFolderButtonProps) {
    const { isOver, setNodeRef } = useDroppable({
        id: `folder-${folderId}`,
        disabled: !droppable,
        data: { folderId }
    });

    const readOnlyDuringDrag = dragActive && !droppable;

    return (
        <button
            ref={setNodeRef}
            onClick={onClick}
            title={readOnlyDuringDrag ? 'Automatisch zugeordnet – Drag & Drop nicht möglich' : undefined}
            className={cn(
                "w-full flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-all duration-200 cursor-pointer",
                isActive
                    ? "bg-rose-50 text-rose-700 shadow-sm ring-1 ring-rose-200"
                    : "text-slate-700 hover:bg-slate-50",
                isOver && droppable && "bg-rose-100 text-rose-800 ring-2 ring-rose-400 ring-offset-2 ring-offset-slate-50 shadow-lg",
                readOnlyDuringDrag && "opacity-40 cursor-not-allowed"
            )}
        >
            <Icon className="w-4 h-4" />
            <span className="flex-1 text-left">{label}</span>
            {count != null && count > 0 && (
                <span className={cn(
                    "text-xs font-semibold px-2 py-0.5 rounded-full min-w-[1.25rem] text-center tabular-nums",
                    countVariant === 'rose' && (isActive ? "bg-rose-200 text-rose-800" : "bg-rose-100 text-rose-700"),
                    countVariant === 'amber' && "bg-amber-100 text-amber-700",
                    countVariant === 'slate' && "bg-slate-100 text-slate-600"
                )}>
                    {count}
                </span>
            )}
        </button>
    );
}

interface DraggableEmailWrapperProps {
    emailId: number;
    children: React.ReactNode;
}

function DraggableEmailWrapper({ emailId, children }: DraggableEmailWrapperProps) {
    const { attributes, listeners, setNodeRef, isDragging } = useDraggable({
        id: `email-${emailId}`,
        data: { emailId }
    });
    return (
        <div
            ref={setNodeRef}
            {...attributes}
            {...listeners}
            className={cn(
                "outline-none",
                isDragging && "opacity-40"
            )}
        >
            {children}
        </div>
    );
}

export default function EmailCenter() {
    const toast = useToast();
    const confirmDialog = useConfirm();
    const { folder: folderParam, emailId: emailIdParam } = useParams<{ folder?: string; emailId?: string }>();
    const navigate = useNavigate();

    // Derive activeFolder from URL param
    const activeFolder: FolderType = VALID_FOLDERS.includes(folderParam as FolderType)
        ? (folderParam as FolderType)
        : 'inbox';

    // Navigate helper: updates URL (which drives activeFolder)
    const setActiveFolder = useCallback((folder: FolderType) => {
        navigate(`/emails/${folder}`, { replace: false });
    }, [navigate]);

    // State
    const [emails, setEmails] = useState<EmailItem[]>([]);
    const [searchQuery, setSearchQuery] = useState('');
    const [isGlobalSearch, setIsGlobalSearch] = useState(false);
    const [globalSearchResults, setGlobalSearchResults] = useState<EmailItem[]>([]);
    const [globalSearchLoading, setGlobalSearchLoading] = useState(false);
    const [loading, setLoading] = useState(false);
    const [selectedEmail, setSelectedEmail] = useState<EmailItem | null>(null);
    const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set());
    const lastSelectedIdRef = useRef<number | null>(null);

    // Folder cache: stale-while-revalidate – show cached data instantly, refresh in background
    const folderCacheRef = useRef<Map<FolderType, { emails: EmailItem[]; timestamp: number }>>(new Map());
    // Ref to guard against stale async responses when switching folders quickly
    const activeFolderRef = useRef<FolderType>(activeFolder);
    // IDs die gerade optimistisch entfernt werden (Spam, Löschen, Blockieren) – verhindert
    // dass der Deep-Link-Effect die Email via API-Fetch zurücksetzt bevor die URL gecleart ist
    const pendingRemovalsRef = useRef<Set<number>>(new Set());
    activeFolderRef.current = activeFolder;

    // Sync URL when selectedEmail changes (deselection clears emailId from URL)
    const prevSelectedRef = useRef<number | null>(null);
    useEffect(() => {
        const currentId = selectedEmail?.id ?? null;
        if (prevSelectedRef.current !== currentId) {
            prevSelectedRef.current = currentId;
            if (currentId === null && emailIdParam) {
                navigate(`/emails/${activeFolder}`, { replace: true });
            }
        }
    }, [selectedEmail, activeFolder, emailIdParam, navigate]);

    // Composition State
    const [isComposing, setIsComposing] = useState(false);
    const [replyToEmail, setReplyToEmail] = useState<EmailItem | null>(null);
    /** ID der Email, auf die geantwortet wird (für Thread-Verknüpfung im Backend) */
    const [replyToEmailId, setReplyToEmailId] = useState<number | undefined>(undefined);

    const [showAssignModal, setShowAssignModal] = useState(false);
    const [folderCounts, setFolderCounts] = useState({
        inbox: 0, sent: 0, trash: 0, spam: 0, newsletter: 0,
        projects: 0, offers: 0, suppliers: 0, unassigned: 0
    });
    const [expandedFilters, setExpandedFilters] = useState(true);
    const [previewAttachment, setPreviewAttachment] = useState<{ url: string, type: 'image' | 'pdf', name: string } | null>(null);
    const [showSettings, setShowSettings] = useState(false);

    // Thread-State für Konversationsverlauf
    const [thread, setThread] = useState<EmailThread | null>(null);
    const [threadLoading, setThreadLoading] = useState(false);

    // Thread laden wenn eine E-Mail selektiert wird
    useEffect(() => {
        if (!selectedEmail) {
            setThread(null);
            return;
        }
        setThreadLoading(true);
        fetch(`/api/emails/${selectedEmail.id}/thread`)
            .then(r => {
                if (!r.ok) throw new Error('Thread not found');
                return r.json();
            })
            .then((data: EmailThread) => setThread(data))
            .catch(() => setThread(null))
            .finally(() => setThreadLoading(false));
    }, [selectedEmail?.id]);

    // Action Handlers
    const handleComposeNew = () => {
        setSelectedEmail(null);
        setReplyToEmail(null);
        setIsComposing(true);
    };

    const handleReply = async (email: EmailItem, replyId?: number) => {
        // List DTO has truncated body/no htmlBody – fetch full email for quote
        let fullEmail = email;
        if (!email.htmlBody) {
            try {
                const res = await fetch(`/api/emails/${email.id}`);
                if (res.ok) fullEmail = await res.json();
            } catch { /* use truncated version as fallback */ }
        }
        setReplyToEmail(fullEmail);
        setReplyToEmailId(replyId ?? email.id);
        setIsComposing(true);
    };

    const handleComposeClose = () => {
        setIsComposing(false);
        setReplyToEmail(null);
        setReplyToEmailId(undefined);
    };

    const handleComposeSuccess = () => {
        setIsComposing(false);
        setReplyToEmail(null);
        setReplyToEmailId(undefined);
        // Reload in background without resetting scroll/selection
        refreshEmailsSilently();
        loadStats();
    };

    // Load emails based on folder and filter – stale-while-revalidate
    const loadEmails = useCallback(async () => {
        const folderAtCallTime = activeFolder;
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
        };
        const endpoint = endpointMap[activeFolder] || '/api/emails/inbox';

        // Show cached data instantly if available
        const cached = folderCacheRef.current.get(activeFolder);
        if (cached) {
            setEmails(cached.emails);
            // Only show loading spinner if cache is older than 2 minutes
            const isStale = Date.now() - cached.timestamp > 120_000;
            if (!isStale) {
                // Fresh enough – still revalidate silently
                setLoading(false);
            } else {
                setLoading(true);
            }
        } else {
            setLoading(true);
        }

        try {
            const res = await fetch(endpoint);
            if (activeFolderRef.current !== folderAtCallTime) return; // folder changed, discard stale response
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
                folderCacheRef.current.set(activeFolder, { emails: data, timestamp: Date.now() });
            } else {
                if (!cached) setEmails([]);
            }
        } catch (err) {
            console.error('Failed to load emails', err);
            if (!cached) setEmails([]);
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
            };
            const endpoint = endpointMap[activeFolder] || '/api/emails/inbox';
            const res = await fetch(endpoint);
            if (activeFolderRef.current !== activeFolder) return; // folder changed, discard stale response
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
                folderCacheRef.current.set(activeFolder, { emails: data, timestamp: Date.now() });
            }
        } catch (err) {
            console.error('Silent refresh failed', err);
        }
    }, [activeFolder]);

    // Load emails + stats when folder changes (single effect, no duplicates)
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

    // Deep-link: auto-select email from URL param /emails/:folder/:emailId
    useEffect(() => {
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
        } else {
            // Nicht fetchen wenn die Email gerade optimistisch entfernt wird (Spam, Löschen, Blockieren)
            // – verhindert Race-Condition: URL-Param ist noch gesetzt während die Email bereits aus
            // der Liste entfernt wurde, Backend hat die Email aber noch nicht verschoben.
            if (pendingRemovalsRef.current.has(emailId)) return;
            // Email not in current folder - try fetching it directly (cross-folder deep-link)
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
                })
                .catch(() => { /* email not found, ignore */ });
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [emails, emailIdParam]);

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
            // Update URL to reflect selected email (single-select only)
            if (newSelected.size === 1) {
                navigate(`/emails/${activeFolder}/${id}`, { replace: true });
            }
            // Fetch full email detail (list DTO has truncated body/no htmlBody)
            fetch(`/api/emails/${id}`)
                .then(r => { if (r.ok) return r.json(); throw new Error(); })
                .then((full: EmailItem) => setSelectedEmail(prev => prev?.id === id ? full : prev))
                .catch(() => { /* keep list version as fallback */ });
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
        currentIds.forEach(id => pendingRemovalsRef.current.add(id));
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
        folderCacheRef.current.clear();
        loadStats();
    };

    const handleAssign = async (type: 'projekt' | 'anfrage', targetId: number) => {
        const idsToAssign = selectedIds.size > 1 ? Array.from(selectedIds) : (selectedEmail ? [selectedEmail.id] : []);
        if (idsToAssign.length === 0) return;

        for (const emailId of idsToAssign) {
            const url = type === 'projekt'
                ? `/api/emails/${emailId}/assign/projekt/${targetId}`
                : `/api/emails/${emailId}/assign/anfrage/${targetId}`;
            const res = await fetch(url, { method: 'POST' });
            if (!res.ok) throw new Error('Failed to assign');
        }

        const assignedSet = new Set(idsToAssign);
        setEmails(prev => prev.filter(e => !assignedSet.has(e.id)));
        setSelectedIds(new Set());
        setSelectedEmail(null);
        folderCacheRef.current.clear();
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
        ids.forEach(id => pendingRemovalsRef.current.add(id));
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
        ids.forEach(id => pendingRemovalsRef.current.add(id));
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
            folderCacheRef.current.clear();
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
        ids.forEach(id => pendingRemovalsRef.current.add(id));
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
            folderCacheRef.current.clear();
            loadStats();
        } catch (err) {
            console.error(err);
            toast.error("Fehler beim Markieren als Nicht-Spam");
            refreshEmailsSilently();
        }
    };

    const MOVE_TARGETS = [
        { id: 'inbox' as const, label: 'Posteingang', icon: Inbox },
        { id: 'trash' as const, label: 'Papierkorb', icon: Trash2 },
        { id: 'spam' as const, label: 'Spam', icon: ShieldAlert },
        { id: 'newsletter' as const, label: 'Newsletter', icon: Newspaper }
    ];
    type MoveTarget = typeof MOVE_TARGETS[number]['id'];

    const moveTargetLabel = (t: MoveTarget) =>
        MOVE_TARGETS.find(m => m.id === t)?.label ?? t;

    const handleMoveToFolder = async (target: MoveTarget, emailIds?: number[]) => {
        const ids = emailIds && emailIds.length > 0
            ? emailIds
            : (selectedIds.size > 0
                ? Array.from(selectedIds)
                : (selectedEmail ? [selectedEmail.id] : []));
        if (ids.length === 0) return;
        if (target === activeFolder) return;

        const idsSet = new Set(ids);
        ids.forEach(id => pendingRemovalsRef.current.add(id));
        setEmails(prev => prev.filter(e => !idsSet.has(e.id)));
        setSelectedIds(prev => {
            const next = new Set(prev);
            ids.forEach(id => next.delete(id));
            return next;
        });
        if (selectedEmail && idsSet.has(selectedEmail.id)) {
            setSelectedEmail(null);
        }

        try {
            const res = await fetch('/api/emails/bulk/move-to-folder', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ ids, targetFolder: target })
            });
            if (!res.ok) throw new Error('move failed');
            const label = moveTargetLabel(target);
            toast.info(ids.length === 1
                ? `E-Mail nach ${label} verschoben`
                : `${ids.length} E-Mails nach ${label} verschoben`);
            folderCacheRef.current.clear();
            loadStats();
        } catch (err) {
            console.error(err);
            toast.error('Fehler beim Verschieben');
            refreshEmailsSilently();
        } finally {
            ids.forEach(id => pendingRemovalsRef.current.delete(id));
        }
    };

    // ─────────────────────────────────────────────────────────
    // Drag & Drop – Setup
    // ─────────────────────────────────────────────────────────
    const sensors = useSensors(
        useSensor(PointerSensor, { activationConstraint: { distance: 8 } })
    );
    const [dragActiveEmail, setDragActiveEmail] = useState<EmailItem | null>(null);
    const [dragCount, setDragCount] = useState(0);
    const [moveMenuAt, setMoveMenuAt] = useState<'detail' | 'bulk' | null>(null);

    const handleDragStart = (e: DragStartEvent) => {
        const data = e.active.data.current as { emailId?: number } | undefined;
        const id = data?.emailId;
        if (id == null) return;
        const email = emails.find(x => x.id === id) || null;
        setDragActiveEmail(email);
        const count = selectedIds.has(id) && selectedIds.size > 1 ? selectedIds.size : 1;
        setDragCount(count);
    };

    const handleDragEnd = (e: DragEndEvent) => {
        const startedId = (e.active.data.current as { emailId?: number } | undefined)?.emailId ?? null;
        setDragActiveEmail(null);
        setDragCount(0);

        const over = e.over;
        if (!over) return;
        const targetData = over.data.current as { folderId?: FolderType } | undefined;
        const targetFolder = targetData?.folderId;
        if (!targetFolder) return;
        if (!['inbox', 'trash', 'spam', 'newsletter'].includes(targetFolder)) return;
        if (startedId == null) return;

        const ids = (selectedIds.has(startedId) && selectedIds.size > 1)
            ? Array.from(selectedIds)
            : [startedId];
        handleMoveToFolder(targetFolder as MoveTarget, ids);
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
                folderCacheRef.current.delete(activeFolder);
                loadStats();
            } else {
                throw new Error('Fehler');
            }
        } catch {
            toast.error("Fehler beim Markieren als gelesen");
            refreshEmailsSilently();
        }
    };

    const handleBackfillThreads = async () => {
        if (!await confirmDialog({
            title: "Thread-Verknüpfungen rückwirkend aufbauen",
            message: "Alle gespeicherten E-Mails werden anhand von Message-ID / In-Reply-To / References rückwirkend zu Threads verknüpft.\nDies kann bei vielen E-Mails einen Moment dauern.",
            variant: "info",
            confirmLabel: "Starten"
        })) return;

        setLoading(true);
        try {
            const res = await fetch('/api/emails/backfill-parents', { method: 'POST' });
            if (res.ok) {
                const data = await res.json();
                toast.success(data.message || `${data.updatedCount} E-Mails verknüpft`);
                loadEmails();
                loadStats();
            } else {
                toast.error('Fehler beim Backfill');
            }
        } catch (err) {
            console.error(err);
            toast.error('Fehler beim Backfill');
        } finally {
            setLoading(false);
        }
    };

    const handleBackfillAttachmentFilenames = async () => {
        if (!await confirmDialog({
            title: "Anhang-Dateinamen reparieren",
            message: "Alle MIME-kodierten Dateinamen (z.B. =?iso-8859-1?Q?...?=) werden in der Datenbank in lesbare Namen umgewandelt.\nDies ist einmalig nötig und dauert einen Moment.",
            variant: "info",
            confirmLabel: "Reparieren"
        })) return;

        setLoading(true);
        try {
            const res = await fetch('/api/emails/admin/backfill-attachment-filenames', { method: 'POST' });
            if (res.ok) {
                const data = await res.json();
                toast.success(`${data.updated} Anhang-Dateinamen repariert`);
                loadEmails();
            } else {
                toast.error('Fehler bei der Reparatur');
            }
        } catch (err) {
            console.error(err);
            toast.error('Fehler bei der Reparatur');
        } finally {
            setLoading(false);
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

    // Helper: Regex-Zeichen escapen
    function escapeRegex(str: string): string {
        return str.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    }

    // Ermittelt IDs der Attachments, die als Inline-Bild in der HTML referenziert werden
    // (via Content-ID oder Dateiname im cid:-Verweis). Wird sowohl für die
    // HTML-Vorschau (CID-Ersetzung) als auch für die Anhangs-Liste benötigt.
    const inlineAttachmentIds = useMemo(() => {
        const ids = new Set<number>();
        if (!selectedEmail?.attachments) return ids;
        const rawHtml = selectedEmail.htmlBody || '';
        selectedEmail.attachments.forEach(att => {
            if (att.id == null) return;
            // 1. Explizit als inline markiert (Content-Disposition: inline)
            if (att.inline) {
                ids.add(att.id);
                return;
            }
            // 2. Content-ID wird im HTML als cid:... referenziert
            if (att.contentId) {
                const cleanCid = att.contentId.replace(/[<>]/g, '');
                const cidPattern = new RegExp(`cid:${escapeRegex(cleanCid)}`, 'i');
                if (cidPattern.test(rawHtml)) {
                    ids.add(att.id);
                    return;
                }
            }
            // 3. Fallback: Dateiname als cid:-Referenz im HTML (z.B. cid:image003.jpg@...)
            const filename = att.originalFilename || att.filename || att.storedFilename;
            if (filename) {
                const baseName = filename.replace(/\.[^.]+$/, '');
                const filenamePattern = new RegExp(`cid:${escapeRegex(baseName)}`, 'i');
                if (filenamePattern.test(rawHtml)) {
                    ids.add(att.id);
                }
            }
        });
        return ids;
    }, [selectedEmail]);


    const visibleAttachments = useMemo(() => {
        if (!selectedEmail) return [];
        return (selectedEmail.attachments || []).filter(att => {
            if (att.id != null && inlineAttachmentIds.has(att.id)) return false;
            return true;
        });
    }, [selectedEmail, inlineAttachmentIds]);

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
    // Bei Ordner-Ansicht nur Thread-Wurzeln anzeigen (parentEmailId == null),
    // damit Antworten nicht doppelt in der Liste erscheinen.
    // Bei globaler Suche werden alle Treffer gezeigt (Nutzer sucht gezielt nach
    // einer bestimmten Nachricht).
    const filteredEmails = useMemo(() => {
        let base: EmailItem[];
        if (isGlobalSearch) {
            base = globalSearchResults;
        } else if (!searchQuery.trim()) {
            base = emails.filter(e => !e.parentEmailId);
        } else {
            const q = searchQuery.toLowerCase();
            base = emails.filter(e =>
                !e.parentEmailId && (
                    e.subject?.toLowerCase().includes(q) ||
                    e.fromAddress?.toLowerCase().includes(q) ||
                    getSenderName(e).toLowerCase().includes(q)
                )
            );
        }
        return base;
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
            let initialRecipient = '';
            let initialSubject = '';
            let replyQuote: string | undefined;

            if (replyToEmail) {
                const senderName = getSenderName(replyToEmail);
                const match = replyToEmail.fromAddress?.match(/<(.+)>/);
                const senderEmail = match ? match[1] : (replyToEmail.fromAddress || '');

                initialRecipient = senderEmail ? `"${senderName}" <${senderEmail}>` : senderName;
                initialSubject = replyToEmail.subject?.startsWith('Re:') ? replyToEmail.subject : `Re: ${replyToEmail.subject || ''}`;

                // Zitat aufbauen – Quote separat, Signatur wird in EmailComposeForm dazwischen gesetzt
                const date = new Date(replyToEmail.sentAt || '').toLocaleDateString('de-DE', {
                    day: '2-digit', month: '2-digit', year: 'numeric',
                }) + ', ' + new Date(replyToEmail.sentAt || '').toLocaleTimeString('de-DE', {
                    hour: '2-digit', minute: '2-digit',
                }) + ' Uhr';

                let cleanBody = replyToEmail.htmlBody || replyToEmail.body || '';
                try {
                    const parser = new DOMParser();
                    const doc = parser.parseFromString(cleanBody, 'text/html');
                    doc.querySelectorAll('script, style, link').forEach(el => el.remove());
                    cleanBody = doc.body.innerHTML;
                } catch { /* ignore */ }

                replyQuote = `<div class="email-quote" style="border-left:3px solid #e2e8f0;padding-left:1rem;color:#64748b;margin-top:0.5rem">
                    <p style="font-size:0.8125rem;color:#94a3b8;margin-bottom:0.5rem">Am ${date} schrieb ${senderName}:</p>
                    ${cleanBody}
                </div>`;
            }

            return (
                <EmailComposeForm
                    onClose={handleComposeClose}
                    onSuccess={handleComposeSuccess}
                    initialRecipient={initialRecipient}
                    initialSubject={initialSubject}
                    replyQuote={replyQuote}
                    replyEmailId={replyToEmailId}
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

                        {activeFolder === 'trash' && (
                            <Button
                                variant="outline"
                                onClick={() => handleMoveToFolder('inbox', bulkIds)}
                                className="w-full gap-2 justify-start h-11 border-emerald-200 text-emerald-700 hover:bg-emerald-50 hover:text-emerald-800 hover:border-emerald-300"
                            >
                                <RotateCcw className="w-4 h-4" />
                                Wiederherstellen ({selectedIds.size})
                            </Button>
                        )}

                        {/* Verschieben nach – alle 4 Ordner außer dem aktuellen */}
                        <div className="rounded-lg border border-slate-200 bg-slate-50/60 p-2 space-y-1">
                            <div className="flex items-center gap-1.5 px-1.5 pb-1 text-[10px] font-bold text-slate-400 uppercase tracking-wider">
                                <FolderInput className="w-3 h-3" />
                                Verschieben nach
                            </div>
                            <div className="grid grid-cols-2 gap-1">
                                {MOVE_TARGETS.filter(t => t.id !== activeFolder).map(t => (
                                    <button
                                        key={t.id}
                                        onClick={() => handleMoveToFolder(t.id, bulkIds)}
                                        className="flex items-center gap-1.5 px-2 py-2 rounded-md text-xs font-medium text-slate-700 hover:bg-rose-50 hover:text-rose-700 border border-transparent hover:border-rose-200 transition-all cursor-pointer"
                                    >
                                        <t.icon className="w-3.5 h-3.5" />
                                        <span className="truncate">{t.label}</span>
                                    </button>
                                ))}
                            </div>
                        </div>

                        {activeFolder === 'spam' ? (
                            <Button
                                variant="outline"
                                onClick={() => handleMarkNotSpam(bulkIds)}
                                className="w-full gap-2 justify-start h-11 border-slate-200 hover:bg-emerald-50 hover:text-emerald-700 hover:border-emerald-300"
                            >
                                <ShieldCheck className="w-4 h-4" />
                                Kein Spam ({selectedIds.size})
                            </Button>
                        ) : activeFolder !== 'trash' && (
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
                            {activeFolder === 'trash' ? `Endgültig löschen (${selectedIds.size})` : `In Papierkorb (${selectedIds.size})`}
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
                                <h2 className="text-xl font-bold text-slate-900 mb-2 break-words">
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
                            <div className="flex items-center gap-1.5 shrink-0">
                                {activeFolder === 'trash' && (
                                    <Button
                                        variant="outline"
                                        size="sm"
                                        onClick={() => handleMoveToFolder('inbox', [selectedEmail.id])}
                                        className="gap-1.5 border-emerald-300 text-emerald-700 hover:bg-emerald-50 hover:text-emerald-800 hover:border-emerald-400"
                                        title="E-Mail aus Papierkorb wiederherstellen"
                                    >
                                        <RotateCcw className="w-4 h-4" />
                                        Wiederherstellen
                                    </Button>
                                )}
                                {/* Verschieben nach – Dropdown */}
                                <div className="relative">
                                    <Button
                                        variant="ghost"
                                        size="sm"
                                        onClick={() => setMoveMenuAt(moveMenuAt === 'detail' ? null : 'detail')}
                                        className="text-slate-500 hover:text-rose-600 hover:bg-rose-50 gap-1"
                                        title="Verschieben nach..."
                                    >
                                        <FolderInput className="w-4 h-4" />
                                        <ChevronDown className="w-3 h-3" />
                                    </Button>
                                    {moveMenuAt === 'detail' && (
                                        <>
                                            <div
                                                className="fixed inset-0 z-40"
                                                onClick={() => setMoveMenuAt(null)}
                                            />
                                            <div className="absolute right-0 top-full mt-1 bg-white rounded-xl shadow-2xl border border-slate-200 p-1.5 z-50 min-w-[200px]">
                                                <div className="px-2 py-1 text-[10px] font-bold text-slate-400 uppercase tracking-wider flex items-center gap-1.5">
                                                    <FolderInput className="w-3 h-3" />
                                                    Verschieben nach
                                                </div>
                                                {MOVE_TARGETS.filter(t => t.id !== activeFolder).map(t => (
                                                    <button
                                                        key={t.id}
                                                        onClick={() => {
                                                            setMoveMenuAt(null);
                                                            handleMoveToFolder(t.id, [selectedEmail.id]);
                                                        }}
                                                        className="w-full flex items-center gap-2.5 px-2.5 py-2 rounded-lg text-sm font-medium text-slate-700 hover:bg-rose-50 hover:text-rose-700 transition-colors cursor-pointer"
                                                    >
                                                        <t.icon className="w-4 h-4 text-rose-500" />
                                                        {t.label}
                                                    </button>
                                                ))}
                                            </div>
                                        </>
                                    )}
                                </div>
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
                                ) : activeFolder !== 'trash' && (
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
                                    title={activeFolder === 'trash' ? 'Endgültig löschen' : 'In Papierkorb'}
                                >
                                    <Trash2 className="w-4 h-4" />
                                </Button>
                            </div>
                        </div>
                    </div>

                    {/* Thread-Verlauf */}
                    {threadLoading ? (
                        <div className="flex-1 overflow-auto bg-slate-50 px-6 py-6 space-y-2">
                            {[false, true, false].map((right, i) => (
                                <div key={i} className={`flex items-end gap-2 mb-2 ${right ? 'flex-row-reverse' : ''}`}>
                                    <div className="w-8 h-8 rounded-full bg-slate-200 animate-pulse shrink-0" />
                                    <div className={`animate-pulse bg-slate-200 rounded-2xl h-16 ${right ? 'rounded-br-sm w-56' : 'rounded-bl-sm w-64'}`} />
                                </div>
                            ))}
                        </div>
                    ) : thread ? (
                        <EmailThreadView
                            thread={thread}
                            onPreview={(url, type, name) => setPreviewAttachment({ url, type, name })}
                            onReply={(entry) => {
                                // Thread-Eintrag → replyToEmail (mit Kontext aus selectedEmail)
                                const replyItem: EmailItem = {
                                    id: entry.id,
                                    type: selectedEmail.type,
                                    subject: entry.subject ?? selectedEmail.subject,
                                    fromAddress: entry.fromAddress ?? selectedEmail.fromAddress,
                                    recipient: entry.recipient ?? selectedEmail.recipient,
                                    sentAt: entry.sentAt ?? selectedEmail.sentAt,
                                    body: entry.snippet,
                                    htmlBody: entry.htmlBody ?? undefined,
                                    direction: (entry.direction as 'IN' | 'OUT') ?? selectedEmail.direction,
                                    projektId: selectedEmail.projektId,
                                    anfrageId: selectedEmail.anfrageId,
                                    lieferantId: selectedEmail.lieferantId,
                                };
                                handleReply(replyItem, entry.id);
                            }}
                        />
                    ) : (
                        <div className="flex-1 overflow-auto p-6">
                            <p className="text-sm text-slate-400 italic">Thread konnte nicht geladen werden.</p>
                        </div>
                    )}
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
        <DndContext sensors={sensors} onDragStart={handleDragStart} onDragEnd={handleDragEnd}>
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
                    {/* ═══ ORDNER (Drag & Drop Drop-Ziele) ═══ */}
                    <div className="px-3 py-1.5 flex items-center gap-1.5">
                        <span className="text-[10px] font-bold text-slate-400 uppercase tracking-widest">Ordner</span>
                        <span className="text-[9px] text-slate-300 font-medium">· Drag &amp; Drop</span>
                    </div>

                    <DroppableFolderButton
                        folderId="inbox"
                        icon={Inbox}
                        label="Posteingang"
                        count={folderCounts.inbox}
                        isActive={activeFolder === 'inbox'}
                        onClick={() => setActiveFolder('inbox')}
                        droppable={true}
                        dragActive={dragActiveEmail !== null}
                        countVariant="rose"
                    />
                    <DroppableFolderButton
                        folderId="trash"
                        icon={Trash2}
                        label="Papierkorb"
                        count={folderCounts.trash}
                        isActive={activeFolder === 'trash'}
                        onClick={() => setActiveFolder('trash')}
                        droppable={true}
                        dragActive={dragActiveEmail !== null}
                    />
                    <DroppableFolderButton
                        folderId="spam"
                        icon={ShieldAlert}
                        label="Spam"
                        count={folderCounts.spam}
                        isActive={activeFolder === 'spam'}
                        onClick={() => setActiveFolder('spam')}
                        droppable={true}
                        dragActive={dragActiveEmail !== null}
                    />
                    <DroppableFolderButton
                        folderId="newsletter"
                        icon={Newspaper}
                        label="Newsletter"
                        count={folderCounts.newsletter}
                        isActive={activeFolder === 'newsletter'}
                        onClick={() => setActiveFolder('newsletter')}
                        droppable={true}
                        dragActive={dragActiveEmail !== null}
                    />

                    <div className="h-px bg-slate-200 my-2.5" />

                    <DroppableFolderButton
                        folderId="sent"
                        icon={Send}
                        label="Gesendet"
                        isActive={activeFolder === 'sent'}
                        onClick={() => setActiveFolder('sent')}
                        droppable={false}
                        dragActive={dragActiveEmail !== null}
                    />

                    <div className="h-px bg-slate-200 my-2.5" />

                    {/* ═══ ZUGEORDNET (read-only, auto-assigned) ═══ */}
                    <div className="px-3 py-1.5 flex items-center justify-between" title="Werden automatisch zugeordnet – kein Drag & Drop möglich">
                        <div className="flex items-center gap-1.5">
                            <span className="text-[10px] font-bold text-slate-400 uppercase tracking-widest">Zugeordnet</span>
                            <span className="text-[9px] text-slate-300 font-medium">· automatisch</span>
                        </div>
                        <button onClick={() => setExpandedFilters(!expandedFilters)} className="cursor-pointer p-0.5 rounded hover:bg-slate-100 transition-colors">
                            {expandedFilters ? <ChevronDown className="w-3.5 h-3.5 text-slate-400" /> : <ChevronRight className="w-3.5 h-3.5 text-slate-400" />}
                        </button>
                    </div>

                    {expandedFilters && (
                        <>
                            <DroppableFolderButton
                                folderId="unassigned"
                                icon={AlertCircle}
                                label="Nicht zugeordnet"
                                count={folderCounts.unassigned}
                                isActive={activeFolder === 'unassigned'}
                                onClick={() => setActiveFolder('unassigned')}
                                droppable={false}
                                dragActive={dragActiveEmail !== null}
                                countVariant="amber"
                            />
                            <DroppableFolderButton
                                folderId="projects"
                                icon={Briefcase}
                                label="Projekte"
                                count={folderCounts.projects}
                                isActive={activeFolder === 'projects'}
                                onClick={() => setActiveFolder('projects')}
                                droppable={false}
                                dragActive={dragActiveEmail !== null}
                            />
                            <DroppableFolderButton
                                folderId="offers"
                                icon={FileText}
                                label="Angebote"
                                count={folderCounts.offers}
                                isActive={activeFolder === 'offers'}
                                onClick={() => setActiveFolder('offers')}
                                droppable={false}
                                dragActive={dragActiveEmail !== null}
                            />
                            <DroppableFolderButton
                                folderId="suppliers"
                                icon={Package}
                                label="Lieferanten"
                                count={folderCounts.suppliers}
                                isActive={activeFolder === 'suppliers'}
                                onClick={() => setActiveFolder('suppliers')}
                                droppable={false}
                                dragActive={dragActiveEmail !== null}
                            />
                        </>
                    )}

                    <div className="h-px bg-slate-200 my-2.5" />

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
                        <div className="divide-y divide-slate-100 animate-pulse">
                            {Array.from({ length: 8 }).map((_, i) => (
                                <div key={i} className="px-4 py-3 border-l-[3px] border-l-transparent">
                                    <div className="flex items-start justify-between gap-2 mb-1">
                                        <div className="h-4 bg-slate-200 rounded w-32" />
                                        <div className="h-3 bg-slate-100 rounded w-16" />
                                    </div>
                                    <div className="h-4 bg-slate-200 rounded w-3/4 mb-1" />
                                    <div className="h-3 bg-slate-100 rounded w-full" />
                                    <div className="h-3 bg-slate-100 rounded w-2/3 mt-1" />
                                </div>
                            ))}
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
                                <DraggableEmailWrapper key={email.id} emailId={email.id}>
                                <div
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
                                    <div className="flex gap-2 mt-2 flex-wrap">
                                        {/* Thread-Badge: zeigt Anzahl Nachrichten im Thread */}
                                        {email.replyCount != null && email.replyCount > 0 && (
                                            <div
                                                className="flex items-center gap-1 text-xs font-semibold text-white bg-rose-600 px-2 py-0.5 rounded-full shadow-sm"
                                                title={`${email.replyCount + 1} Nachrichten in diesem Thread`}
                                            >
                                                <MessagesSquare className="w-3 h-3" />
                                                {email.replyCount + 1} Nachrichten
                                            </div>
                                        )}
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
                                </DraggableEmailWrapper>
                            ))}
                        </div>
                    )}
                </div>

                {/* Footer Status */}
                <div className="px-4 py-2.5 border-t border-slate-200 text-xs text-slate-500 flex items-center justify-between gap-2">
                    <span>
                        {isGlobalSearch && <Globe className="w-3 h-3 inline mr-1 text-rose-500" />}
                        {filteredEmails.length} {filteredEmails.length === 1 ? 'Nachricht' : 'Nachrichten'}
                    </span>
                    <div className="flex items-center gap-2">
                        {selectedIds.size > 0 && (
                            <span className="text-rose-600 font-medium">{selectedIds.size} ausgewählt</span>
                        )}
                        <button
                            onClick={handleBackfillThreads}
                            title="Thread-Verknüpfungen rückwirkend aufbauen"
                            className="flex items-center gap-1 px-2 py-1 rounded text-slate-400 hover:text-rose-600 hover:bg-rose-50 transition-colors cursor-pointer"
                        >
                            <DatabaseZap className="w-3.5 h-3.5" />
                            Threads
                        </button>
                        <button
                            onClick={handleBackfillAttachmentFilenames}
                            title="MIME-kodierte Anhang-Dateinamen reparieren (=?iso-8859-1?Q?...?=)"
                            className="flex items-center gap-1 px-2 py-1 rounded text-slate-400 hover:text-rose-600 hover:bg-rose-50 transition-colors cursor-pointer"
                        >
                            <Paperclip className="w-3.5 h-3.5" />
                            Dateinamen
                        </button>
                    </div>
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
                    <DialogHeader className="shrink-0 pr-10">
                        <DialogTitle className="flex items-center gap-3">
                            <span className="truncate flex-1">{previewAttachment?.name}</span>
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
                        </DialogTitle>
                    </DialogHeader>
                    <div className="flex-1 min-h-0 rounded-lg overflow-y-auto overflow-x-hidden border border-slate-200 bg-slate-100">
                        {previewAttachment?.type === 'pdf' ? (
                            <PdfCanvasViewer
                                url={previewAttachment.url}
                                className="w-full"
                            />
                        ) : (
                            <div className="flex items-center justify-center h-full text-center text-slate-500">
                                <File className="w-16 h-16 mx-auto mb-4" />
                                <p>Keine Vorschau verfügbar</p>
                            </div>
                        )}
                    </div>
                </DialogContent>
            </Dialog>
        </div>
        <DragOverlay dropAnimation={null}>
            {dragActiveEmail ? (
                <div className="bg-white rounded-xl shadow-2xl border-2 border-rose-300 px-4 py-3 flex items-center gap-3 rotate-[-2deg] min-w-[240px] max-w-[320px] pointer-events-none">
                    <div className="w-9 h-9 rounded-lg bg-gradient-to-br from-rose-500 to-rose-600 flex items-center justify-center shadow-sm shadow-rose-200 flex-shrink-0">
                        <Mail className="w-4 h-4 text-white" />
                    </div>
                    <div className="flex-1 min-w-0">
                        {dragCount > 1 ? (
                            <>
                                <p className="text-sm font-bold text-rose-700">{dragCount} E-Mails</p>
                                <p className="text-xs text-slate-500 truncate">werden verschoben...</p>
                            </>
                        ) : (
                            <>
                                <p className="text-sm font-semibold text-slate-900 truncate">
                                    {dragActiveEmail.subject || '(Kein Betreff)'}
                                </p>
                                <p className="text-xs text-slate-500 truncate">
                                    {dragActiveEmail.fromAddress}
                                </p>
                            </>
                        )}
                    </div>
                </div>
            ) : null}
        </DragOverlay>
        </DndContext>
    );
}


