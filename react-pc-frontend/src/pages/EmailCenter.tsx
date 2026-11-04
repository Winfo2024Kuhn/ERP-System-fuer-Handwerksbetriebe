import { EmailNavigationGuard } from '../features/email/EmailNavigationGuard';
import { EmailDetailHeader } from '../features/email/EmailDetailHeader';
import { EmailEntwurfZeile } from '../features/email/EmailEntwurfZeile';
import { EmailListenZeile } from '../features/email/EmailListenZeile';
import { EmailListeLaedt, EmailListeLeer, EmailListenEnde, EmailSuchFehler, type LeerArt } from '../features/email/EmailListenZustand';
import { EmailSuchHinweis, EmailSuchleiste } from '../features/email/EmailSuchleiste';
import { naechsteNachLoeschen } from '../features/email/emailLoeschen';
import { VERSCHIEBE_ZIELE, ablageOrdner, type VerschiebeZiel } from '../features/email/emailOrdner';
import { useEmailLoeschen } from '../features/email/useEmailLoeschen';
import { useEmailSuche } from '../features/email/useEmailSuche';
import { useLoeschTaste } from '../features/email/useLoeschTaste';
import { useAbsenderPostfaecher } from '../features/email/useAbsenderPostfaecher';
import { ermittleEmailZuordnung } from '../features/email/emailZuordnung';
import { EmailFolderSidebar } from '../features/email/EmailFolderSidebar';
import { UngelesenKopfzeile } from '../features/email/UngelesenKopfzeile';
import { AssignModal, type ZuordnungsZiel } from '../features/email/EmailAssignmentDialog';
import { OrdnerHinweis } from '../features/email/OrdnerHinweis';
import { useOptionalAuth } from '../auth/AuthContext';
import { useEmailPaneWidth } from '../features/email/useEmailPaneWidth';
import {
    buildEmailListUrl, buildForwardSubject, buildReplyAddressing, buildReplySubject, emailListCacheKey, filtereEntwuerfe,
    getSenderName, getDisplayName, groupEmailThreads, hasFurtherRecipients, isImageAttachment, nutztServerFilterUngelesen,
    type DraftItem, type EmailItem, type EmailThreadRow, type FolderType, type ReadFilter, type ReplyMode,
} from '../features/email/emailCenterModel';
import React, { useState, useEffect, useContext, useCallback, useLayoutEffect, useMemo, useRef } from 'react';
import { useParams, useNavigate, useSearchParams, UNSAFE_DataRouterContext } from 'react-router-dom';
import { PdfCanvasViewer } from '../components/ui/PdfCanvasViewer';
import {
    Mail, Trash2, Inbox, Download, FolderPlus, File, ShieldAlert, ShieldCheck, ShieldX, X, CheckSquare, RotateCcw,
    FolderInput, CheckCircle2,
} from 'lucide-react';
import {
    DndContext,
    DragOverlay,
    PointerSensor,
    useSensor,
    useSensors,
    type DragStartEvent,
    type DragEndEvent
} from '@dnd-kit/core';
import { Button } from '../components/ui/button';
import { cn } from '../lib/utils';
import { extractDisplayName, extractEmailAddress, escapeHtml, parseRecipientList } from '../lib/emailAddress';
import { EMAILS_REFRESH_EVENT, refreshNotifications } from '../lib/notificationRefresh';
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '../components/ui/dialog';
import { EmailComposeForm } from '../components/EmailComposeForm';
import { ImageViewer } from '../components/ui/image-viewer';
import { useToast } from '../components/ui/toast';
import { useConfirm } from '../components/ui/confirm-dialog';
import { parseErrorMessage } from '../components/settings/settingsApi';
import { EmailThreadView } from '../components/EmailThreadView';
import type { EmailThread } from '../components/EmailThreadView';


// Main EmailCenter Component
const VALID_FOLDERS: FolderType[] = ['inbox', 'sent', 'drafts', 'trash', 'spam', 'newsletter', 'starred', 'projects', 'offers', 'suppliers', 'tax-advisors', 'unassigned'];

/** Gemerkte Mail-Liste je Ordner/Filter (stale-while-revalidate). */
type EmailListCacheEintrag = { emails: EmailItem[]; hasMore: boolean; timestamp: number };

export default function EmailCenter() {
    const hasDataRouter = useContext(UNSAFE_DataRouterContext) !== null;
    const toast = useToast();
    // Eigener Name, um fremde Entwürfe (nur Admins sehen sie) mit „von …“ zu kennzeichnen.
    const eigenerName = useOptionalAuth()?.user?.displayName ?? null;
    const confirmDialog = useConfirm();
    const { folder: folderParam, emailId: emailIdParam } = useParams<{ folder?: string; emailId?: string }>();
    const [searchParams, setSearchParams] = useSearchParams();
    const navigate = useNavigate();

    const beforeLeaveComposer = useRef<(() => Promise<boolean>) | null>(null);
    const leavingComposer = useRef(false);
    const registerBeforeLeave = useCallback((save: (() => Promise<boolean>) | null) => {
        beforeLeaveComposer.current = save;
    }, []);
    const persistComposer = useCallback(async () => {
        if (!beforeLeaveComposer.current) return true;
        if (leavingComposer.current) return false;
        leavingComposer.current = true;
        try { return await beforeLeaveComposer.current(); }
        finally { leavingComposer.current = false; }
    }, []);
    const [composerVersion, setComposerVersion] = useState(0);

    // Derive activeFolder from URL param
    const activeFolder: FolderType = VALID_FOLDERS.includes(folderParam as FolderType)
        ? (folderParam as FolderType)
        : 'inbox';

    // Navigate helper: updates URL (which drives activeFolder)
    const setActiveFolder = useCallback(async (folder: FolderType) => {
        if (!await persistComposer()) return;
        navigate(`/emails/${folder}`, { replace: false });
    }, [navigate, persistComposer]);

    // State
    const [emails, setEmails] = useState<EmailItem[]>([]);

    // Eigene Postfach-Adressen laden (verhindert Antworten an mich selbst)
    const absenderPostfaecher = useAbsenderPostfaecher();
    const ownAddresses = useMemo(
        () => absenderPostfaecher.postfaecher.map(postfach => postfach.emailAdresse),
        [absenderPostfaecher.postfaecher],
    );
    useEffect(() => {
        if (absenderPostfaecher.fehler) toast.error('Eigene Absenderadressen konnten nicht geladen werden.');
    }, [absenderPostfaecher.fehler, toast]);

    const isOwnEmail = useCallback((addr?: string) => {
        if (!addr) return false;
        const clean = extractEmailAddress(addr).toLowerCase();
        if (!clean) return false;
        return ownAddresses.some(own => own.toLowerCase() === clean);
    }, [ownAddresses]);

    const { ref: mailLayoutRef, width: availableWidth } = useEmailPaneWidth();

    // Resizable Spalten & Einklappen (im localStorage gemerkt)
    const [sidebarWidth, setSidebarWidth] = useState<number>(() => {
        const saved = localStorage.getItem('email_center_sidebar_width');
        if (saved) {
            const val = parseInt(saved, 10);
            if (!isNaN(val) && val >= 180 && val <= 400) return val;
        }
        return 240;
    });

    const [listWidth, setListWidth] = useState<number>(() => {
        const saved = localStorage.getItem('email_center_list_width');
        if (saved) {
            const val = parseInt(saved, 10);
            if (!isNaN(val) && val >= 280 && val <= 700) return val;
        }
        return 380;
    });

    const [isSidebarCollapsed, setIsSidebarCollapsed] = useState<boolean>(() => {
        return localStorage.getItem('email_center_sidebar_collapsed') === 'true';
    });

    // Alte Einstellungen dürfen den Lesebereich beim Fensterwechsel nicht verdrängen.
    const effectiveSidebarWidth = isSidebarCollapsed ? 56 : Math.min(sidebarWidth, Math.max(180, availableWidth * 0.2));
    const effectiveListWidth = Math.min(listWidth, Math.max(280, availableWidth - effectiveSidebarWidth - 468));

    const [isDraggingSidebar, setIsDraggingSidebar] = useState(false);
    const [isDraggingList, setIsDraggingList] = useState(false);

    const toggleSidebar = useCallback(() => {
        setIsSidebarCollapsed(prev => {
            const next = !prev;
            localStorage.setItem('email_center_sidebar_collapsed', String(next));
            return next;
        });
    }, []);

    const handleSidebarResizeStart = useCallback((e: React.PointerEvent) => {
        e.preventDefault();
        setIsDraggingSidebar(true);
        const startX = e.clientX;
        const startWidth = effectiveSidebarWidth;
        document.body.style.userSelect = 'none';
        document.body.style.cursor = 'col-resize';

        const onPointerMove = (moveEvent: PointerEvent) => {
            const delta = moveEvent.clientX - startX;
            const newWidth = Math.min(Math.max(startWidth + delta, 180), 400);
            setSidebarWidth(newWidth);
            localStorage.setItem('email_center_sidebar_width', String(newWidth));
        };

        const onPointerUp = () => {
            setIsDraggingSidebar(false);
            document.body.style.userSelect = '';
            document.body.style.cursor = '';
            window.removeEventListener('pointermove', onPointerMove);
            window.removeEventListener('pointerup', onPointerUp);
        };

        window.addEventListener('pointermove', onPointerMove);
        window.addEventListener('pointerup', onPointerUp);
    }, [effectiveSidebarWidth]);

    const handleListResizeStart = useCallback((e: React.PointerEvent) => {
        e.preventDefault();
        setIsDraggingList(true);
        const startX = e.clientX;
        const startWidth = effectiveListWidth;
        document.body.style.userSelect = 'none';
        document.body.style.cursor = 'col-resize';

        const onPointerMove = (moveEvent: PointerEvent) => {
            const delta = moveEvent.clientX - startX;
            const newWidth = Math.min(Math.max(startWidth + delta, 280), 700);
            setListWidth(newWidth);
            localStorage.setItem('email_center_list_width', String(newWidth));
        };

        const onPointerUp = () => {
            setIsDraggingList(false);
            document.body.style.userSelect = '';
            document.body.style.cursor = '';
            window.removeEventListener('pointermove', onPointerMove);
            window.removeEventListener('pointerup', onPointerUp);
        };

        window.addEventListener('pointermove', onPointerMove);
        window.addEventListener('pointerup', onPointerUp);
    }, [effectiveListWidth]);
    // Serverseitige Suche in beiden Modi; unter 2 Zeichen zeigt die Liste den Ordner.
    const suche = useEmailSuche(activeFolder);
    const { aktiv: sucheAktiv, treffer: suchTreffer, aendereTreffer, mehrLaden: sucheMehrLaden,
        neuLaden: sucheNeuLaden, nachladeFehler: sucheNachladeFehler, hatMehr: sucheHatMehr } = suche;
    const [loading, setLoading] = useState(false);
    // Pagination – seitenweise Nachladen pro Ordner (Infinite Scroll)
    const PAGE_SIZE = 50;
    const [hasMore, setHasMore] = useState(false);
    const [loadingMore, setLoadingMore] = useState(false);
    const [selectedEmail, setSelectedEmail] = useState<EmailItem | null>(null);
    const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set());
    const lastSelectedIdRef = useRef<number | null>(null);
    const selectionVersionRef = useRef(0);
    /**
     * Auswahlstand, zu dem ein Klick eine Mail in die URL geschrieben hat. Der URL-Effekt läuft
     * erst nach dem nächsten Rendern – unter Last kann bis dahin schon ein Strg-Klick die Auswahl
     * erweitert haben. Mit dem hier gemerkten Stand erkennt der Effekt das und überschreibt die
     * Mehrfachauswahl nicht mit der verspäteten Detailantwort.
     */
    const klickAuswahlRef = useRef<{ emailId: number; version: number } | null>(null);

    // Folder cache: stale-while-revalidate – show cached data instantly, refresh in background.
    // Schlüssel über emailListCacheKey: die „Ungelesen"-Liste liegt getrennt von der vollen Liste.
    const folderCacheRef = useRef<Map<string, EmailListCacheEintrag>>(new Map());
    // IDs die gerade optimistisch entfernt werden (Spam, Löschen, Blockieren) – verhindert
    // dass der Deep-Link-Effect die Email via API-Fetch zurücksetzt bevor die URL gecleart ist
    const pendingRemovalsRef = useRef<Set<number>>(new Set());

    // Sync URL when selectedEmail changes (deselection clears emailId from URL)
    const prevSelectedRef = useRef<number | null>(null);
    useEffect(() => {
        const currentId = selectedEmail?.id ?? null;
        if (prevSelectedRef.current !== currentId) {
            const previousId = prevSelectedRef.current;
            prevSelectedRef.current = currentId;
            if (currentId === null && emailIdParam && Number(emailIdParam) === previousId) {
                navigate(`/emails/${activeFolder}`, { replace: true });
            }
        }
    }, [selectedEmail, activeFolder, emailIdParam, navigate]);

    // Composition State
    const [isComposing, setIsComposing] = useState(false);
    const [replyToEmail, setReplyToEmail] = useState<EmailItem | null>(null);
    /** ID der Email, auf die geantwortet wird (für Thread-Verknüpfung im Backend) */
    const [replyToEmailId, setReplyToEmailId] = useState<number | undefined>(undefined);
    const [replyMode, setReplyMode] = useState<ReplyMode>('reply');

    const [showAssignModal, setShowAssignModal] = useState(false);
    const [folderCounts, setFolderCounts] = useState({
        inbox: 0, sent: 0, drafts: 0, trash: 0, spam: 0, newsletter: 0,
        starred: 0, projects: 0, offers: 0, suppliers: 0, taxAdvisors: 0, unassigned: 0
    });
    // Gesamt-Counts pro Ordner (fuer Footer "X von Y")
    const [folderTotals, setFolderTotals] = useState<Record<string, number>>({});
    const [previewAttachment, setPreviewAttachment] = useState<{ url: string, type: 'image' | 'pdf', name: string } | null>(null);
    // Sentinel fuer Infinite Scroll am Ende der Email-Liste
    const loadMoreSentinelRef = useRef<HTMLDivElement | null>(null);
    const [sortOrder, setSortOrder] = useState<'desc' | 'asc'>('desc');
    const [readFilter, setReadFilter] = useState<ReadFilter>('all');
    // „Ungelesen" filtert der Server – sonst fehlten ungelesene Mails, die nicht unter
    // den ersten geladenen 50 stehen. Der Listen-Schlüssel (Ordner + Filter) schützt
    // vor verspäteten Antworten nach einem Ordner- oder Filterwechsel.
    const nurUngelesen = nutztServerFilterUngelesen(activeFolder, readFilter);
    const listKey = emailListCacheKey(activeFolder, nurUngelesen);
    // Neuester Listen-Schlüssel für späte Antworten; schon beim Commit (Layout-Effekt)
    // nachgezogen, damit keine Antwort dazwischen den alten Schlüssel sieht.
    const listKeyRef = useRef(listKey);
    useLayoutEffect(() => { listKeyRef.current = listKey; }, [listKey]);
    const [forwardEmail, setForwardEmail] = useState<EmailItem | null>(null);

    // Drafts
    const [drafts, setDrafts] = useState<DraftItem[]>([]);
    const [activeDraftId, setActiveDraftId] = useState<number | undefined>(undefined);
    const [activeDraft, setActiveDraft] = useState<DraftItem | null>(null);

    // Thread-State für Konversationsverlauf
    const [thread, setThread] = useState<EmailThread | null>(null);
    const [threadLoading, setThreadLoading] = useState(false);

    // Thread laden wenn eine E-Mail selektiert wird
    const selectedEmailId = selectedEmail?.id;
    useEffect(() => {
        if (!selectedEmailId) return;
        let active = true;
        (async () => {
            setThreadLoading(true);
            try {
                const r = await fetch(`/api/emails/${selectedEmailId}/thread`);
                if (!r.ok) throw new Error('Thread not found');
                const data: EmailThread = await r.json();
                if (active) setThread(data);
            } catch {
                if (active) setThread(null);
            } finally {
                if (active) setThreadLoading(false);
            }
        })();
        return () => { active = false; setThread(null); };
    }, [selectedEmailId]);

    // Action Handlers
    const handleComposeNew = async () => {
        if (!await persistComposer()) return;
        setComposerVersion(value => value + 1);
        setSelectedEmail(null);
        setReplyToEmail(null);
        setReplyToEmailId(undefined);
        setForwardEmail(null);
        setActiveDraftId(undefined);
        setActiveDraft(null);
        setIsComposing(true);
    };

    const handleReply = async (email: EmailItem, replyId?: number, mode: ReplyMode = 'reply') => {
        if (!await persistComposer()) return;
        setComposerVersion(value => value + 1);
        // List DTO has truncated body/no htmlBody – fetch full email for quote
        let fullEmail = email;
        if (!email.htmlBody) {
            try {
                const res = await fetch(`/api/emails/${email.id}`);
                if (res.ok) fullEmail = await res.json();
            } catch { /* use truncated version as fallback */ }
        }

        if (!buildReplyAddressing(fullEmail, mode, isOwnEmail).to) {
            toast.info('Kein externer Empfänger gefunden – bitte Empfänger manuell eingeben.');
        }

        setReplyToEmail(fullEmail);
        setReplyToEmailId(replyId ?? email.id);
        setReplyMode(mode);
        setForwardEmail(null);
        setActiveDraftId(undefined);
        setActiveDraft(null);
        setIsComposing(true);
    };

    const handleForward = async (email: EmailItem) => {
        if (!await persistComposer()) return;
        setComposerVersion(value => value + 1);
        let fullEmail = email;
        if (!email.htmlBody) {
            try {
                const res = await fetch(`/api/emails/${email.id}`);
                if (res.ok) fullEmail = await res.json();
            } catch { /* use truncated version as fallback */ }
        }
        setReplyToEmail(null);
        setReplyToEmailId(undefined);
        setForwardEmail(fullEmail);
        setActiveDraftId(undefined);
        setActiveDraft(null);
        setIsComposing(true);
    };

    const handleToggleStar = async (emailId: number) => {
        const umschalten = (liste: EmailItem[]) => liste.map(em => em.id === emailId ? { ...em, isStarred: !em.isStarred } : em);
        // Optimistic update
        setEmails(umschalten);
        aendereTreffer(umschalten);
        if (selectedEmail?.id === emailId) {
            setSelectedEmail(prev => prev ? { ...prev, isStarred: !prev.isStarred } : prev);
        }
        try {
            const res = await fetch(`/api/emails/${emailId}/toggle-star`, { method: 'POST' });
            if (!res.ok) throw new Error();
            loadStats();
        } catch {
            // Revert on error
            setEmails(umschalten);
            aendereTreffer(umschalten);
            if (selectedEmail?.id === emailId) {
                setSelectedEmail(prev => prev ? { ...prev, isStarred: !prev.isStarred } : prev);
            }
            toast.error('Stern konnte nicht gesetzt werden');
        }
    };

    // Drafts laden
    // Ergebnisse erst in den Promise-Callbacks übernehmen (auch beim Aufruf aus Effekten).
    const loadDrafts = useCallback(() =>
        fetch('/api/emails/drafts')
            .then(async (res) => {
                if (res.ok) {
                    setDrafts(await res.json());
                }
            })
            .catch((err: unknown) => {
                console.error('Failed to load drafts', err);
            }),
    []);

    // Draft öffnen = Compose-Form mit Draft-Daten
    const handleOpenDraft = async (draft: DraftItem) => {
        if (!await persistComposer()) return;
        setComposerVersion(value => value + 1);
        setSelectedEmail(null);
        setReplyToEmail(null);
        setReplyToEmailId(draft.replyEmailId || undefined);
        setForwardEmail(null);
        setActiveDraftId(draft.id);
        setActiveDraft(draft);
        setIsComposing(true);
    };

    // Draft löschen
    const handleDeleteDraft = async (draftId: number, e?: React.MouseEvent) => {
        if (e) { e.stopPropagation(); e.preventDefault(); }
        try {
            const res = await fetch(`/api/emails/drafts/${draftId}`, { method: 'DELETE' });
            if (!res.ok) {
                toast.error(await parseErrorMessage(res, 'Der Entwurf konnte nicht gelöscht werden.'));
                return;
            }
            setDrafts(prev => prev.filter(d => d.id !== draftId));
            loadStats();
        } catch {
            toast.error('Der Entwurf konnte nicht gelöscht werden.');
        }
    };

    // Load emails based on folder and filter – stale-while-revalidate.
    // Schritt 1 (synchron beim Ordner-/Filterwechsel): gemerkte Liste sofort zeigen.
    // Bewusst synchron im Effekt (wie vor dem Umbau): Die Liste kommt aus dem Zwischenspeicher
    // (Ref, also ein externer Speicher). Die Regel schweigt hier, weil die Funktion ein Ref liest –
    // das gilt auch für die Ladeanzeige ohne Treffer, die nicht aus dem Ref stammt.
    // Liefert den Zwischenspeicher-Eintrag zurück, den Schritt 2 für die Fehlerbehandlung braucht.
    const zeigeGemerkteListe = useCallback((keyAtCallTime: string) => {
        const cached = folderCacheRef.current.get(keyAtCallTime);
        if (cached) {
            setEmails(cached.emails);
            setHasMore(cached.hasMore);
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
            setHasMore(false);
        }
        return cached;
    }, []);

    // Schritt 2: frisch vom Server holen – Ergebnisse erst in den Promise-Callbacks übernehmen.
    const ladeEmails = useCallback((keyAtCallTime: string, cached: EmailListCacheEintrag | undefined) => {
        const endpoint = buildEmailListUrl(activeFolder, 0, PAGE_SIZE, nurUngelesen);
        return fetch(endpoint)
            .then(async (res) => {
                if (listKeyRef.current !== keyAtCallTime) return; // Ordner/Filter gewechselt, alte Antwort verwerfen
                if (res.ok) {
                    let data = await res.json();
                    if (!Array.isArray(data)) data = [];

                    const more = data.length >= PAGE_SIZE;
                    setEmails(data);
                    setHasMore(more);
                    folderCacheRef.current.set(keyAtCallTime, { emails: data, hasMore: more, timestamp: Date.now() });
                } else {
                    if (!cached) { setEmails([]); setHasMore(false); }
                }
            })
            .catch((err: unknown) => {
                console.error('Failed to load emails', err);
                if (!cached) { setEmails([]); setHasMore(false); }
            })
            .finally(() => {
                setLoading(false);
            });
    }, [activeFolder, nurUngelesen]);

    // Entwürfe-Ordner hat keine Mail-Liste: beim Wechsel dorthin sofort leeren.
    const [vorherigerListKey, setVorherigerListKey] = useState<string | null>(null);
    if (vorherigerListKey !== listKey) {
        setVorherigerListKey(listKey);
        if (activeFolder === 'drafts') {
            setEmails([]);
            setHasMore(false);
            setLoading(false);
        }
    }

    // Naechste Seite asynchron nachladen (Infinite Scroll)
    const loadMoreEmails = useCallback(async () => {
        if (activeFolder === 'drafts') return;
        if (loadingMore || loading || !hasMore) return;

        const keyAtCallTime = listKey;
        const offset = emails.length;
        const endpoint = buildEmailListUrl(activeFolder, offset, PAGE_SIZE, nurUngelesen);

        setLoadingMore(true);
        try {
            const res = await fetch(endpoint);
            if (listKeyRef.current !== keyAtCallTime) return;
            if (!res.ok) { setHasMore(false); return; }

            let data = await res.json();
            if (!Array.isArray(data)) data = [];

            // Duplikate vermeiden (Race-Condition mit silent-refresh)
            setEmails(prev => {
                const existing = new Set(prev.map(e => e.id));
                const fresh = data.filter((e: EmailItem) => !existing.has(e.id));
                const merged = [...prev, ...fresh];
                const more = data.length >= PAGE_SIZE;
                folderCacheRef.current.set(keyAtCallTime, { emails: merged, hasMore: more, timestamp: Date.now() });
                setHasMore(more);
                return merged;
            });
        } catch (err) {
            console.error('Failed to load more emails', err);
        } finally {
            setLoadingMore(false);
        }
    }, [activeFolder, emails.length, hasMore, listKey, loading, loadingMore, nurUngelesen]);

    // Load stats for folder counts
    const loadStats = useCallback(() =>
        Promise.all([
            fetch('/api/emails/stats'),
            fetch('/api/emails/drafts/count')
        ])
            .then(async ([statsRes, draftRes]) => {
                let draftCount = 0;
                if (draftRes.ok) {
                    const draftData = await draftRes.json();
                    draftCount = draftData.count || 0;
                }
                if (statsRes.ok) {
                    const stats = await statsRes.json();
                    setFolderCounts({
                        inbox: stats.inboxCount || 0,
                        sent: stats.sentCount || 0,
                        drafts: draftCount,
                        trash: stats.trashCount || 0,
                        spam: stats.spamCount || 0,
                        newsletter: stats.newsletterCount || 0,
                        starred: stats.starredCount || 0,
                        unassigned: stats.unassignedCount || 0,
                        projects: stats.projectCount || 0,
                        offers: stats.offerCount || 0,
                        suppliers: stats.supplierCount || 0,
                        taxAdvisors: stats.taxAdvisorCount || 0
                    });
                    setFolderTotals({
                        inbox: stats.inboxTotal || 0,
                        sent: stats.sentTotal || 0,
                        drafts: draftCount,
                        trash: stats.trashTotal || 0,
                        spam: stats.spamTotal || 0,
                        newsletter: stats.newsletterTotal || 0,
                        starred: stats.starredTotal || 0,
                        unassigned: stats.unassignedTotal || 0,
                        projects: stats.projectTotal || 0,
                        offers: stats.offerTotal || 0,
                        suppliers: stats.supplierTotal || 0,
                        'tax-advisors': stats.taxAdvisorTotal || 0
                    });
                }
            })
            .catch((err: unknown) => {
                console.error('Failed to load stats', err);
            }),
    []);

    // Silent refresh: refreshed das aktuell sichtbare Slice (limit = bisherige Anzahl, mind. PAGE_SIZE)
    const refreshEmailsSilently = useCallback(async () => {
        try {
            if (activeFolder === 'drafts') {
                await loadDrafts();
                return;
            }
            const keyAtCallTime = listKey;
            const refreshLimit = Math.max(emails.length, PAGE_SIZE);
            const endpoint = buildEmailListUrl(activeFolder, 0, refreshLimit, nurUngelesen);
            const res = await fetch(endpoint);
            if (listKeyRef.current !== keyAtCallTime) return; // Ordner/Filter gewechselt, alte Antwort verwerfen
            if (res.ok) {
                let data = await res.json();
                if (!Array.isArray(data)) data = [];

                const more = data.length >= refreshLimit;
                setEmails(data);
                setHasMore(more);
                folderCacheRef.current.set(keyAtCallTime, { emails: data, hasMore: more, timestamp: Date.now() });
            }
        } catch (err) {
            console.error('Silent refresh failed', err);
        }
    }, [activeFolder, emails.length, listKey, loadDrafts, nurUngelesen]);

    // Steht hinter refreshEmailsSilently/loadDrafts/loadStats, die es aufruft.
    const handleComposeClose = () => {
        setIsComposing(false);
        setReplyToEmail(null);
        setReplyToEmailId(undefined);
        setForwardEmail(null);
        setActiveDraftId(undefined);
        setActiveDraft(null);
        // Refresh drafts and stats so draft count updates
        loadDrafts();
        loadStats();
    };

    const handleComposeSuccess = () => {
        setIsComposing(false);
        setReplyToEmail(null);
        setReplyToEmailId(undefined);
        setForwardEmail(null);
        setActiveDraftId(undefined);
        setActiveDraft(null);
        // Reload in background without resetting scroll/selection
        refreshEmailsSilently();
        loadDrafts();
        loadStats();
    };

    // Load emails + stats when folder changes (single effect, no duplicates)
    // Drafts werden immer geladen, damit das Entwurf-Badge in allen Ordnern sichtbar ist
    useEffect(() => {
        if (activeFolder !== 'drafts') {
            // Stale-while-revalidate: gemerkte Liste (Ref-Zwischenspeicher) sofort zeigen, dann frisch laden.
            const cached = zeigeGemerkteListe(listKey);
            void ladeEmails(listKey, cached);
        }
        void loadStats();
        void loadDrafts();
    }, [zeigeGemerkteListe, ladeEmails, listKey, loadStats, loadDrafts, activeFolder]);

    // Auto-poll for new emails every 30 seconds
    useEffect(() => {
        const interval = setInterval(() => {
            refreshEmailsSilently();
            loadStats();
            loadDrafts();
        }, 30000);
        return () => clearInterval(interval);
    }, [refreshEmailsSilently, loadStats, loadDrafts]);

    // Die Glocke hat ganze E-Mail-Ordner als gelesen markiert: Zwischenspeicher
    // verwerfen, Ordnerzahlen und aktuelle Liste still neu laden.
    useEffect(() => {
        const handleEmailsRefresh = () => {
            folderCacheRef.current.clear();
            loadStats();
            refreshEmailsSilently();
        };
        window.addEventListener(EMAILS_REFRESH_EVENT, handleEmailsRefresh);
        return () => window.removeEventListener(EMAILS_REFRESH_EVENT, handleEmailsRefresh);
    }, [refreshEmailsSilently, loadStats]);

    // Deep-link: auto-open draft from query param ?draft=<id> (e.g. from ProjektEditor)
    useEffect(() => {
        const draftParam = searchParams.get('draft');
        if (!draftParam) return;
        const draftIdToOpen = Number(draftParam);
        if (isNaN(draftIdToOpen)) return;

        // Clear the query param so it doesn't re-trigger
        setSearchParams({}, { replace: true });

        // Load draft and open compose
        (async () => {
            try {
                const res = await fetch('/api/emails/drafts');
                if (!res.ok) return;
                const allDrafts: DraftItem[] = await res.json();
                setDrafts(allDrafts);
                const found = allDrafts.find(d => d.id === draftIdToOpen);
                if (found) {
                    setSelectedEmail(null);
                    setReplyToEmail(null);
                    setReplyToEmailId(found.replyEmailId || undefined);
                    setForwardEmail(null);
                    setActiveDraftId(found.id);
                    setActiveDraft(found);
                    setIsComposing(true);
                }
            } catch { /* ignore */ }
        })();
    // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    // The route identifies the view and the selected message independently.
    // Loading a message must never replace a virtual folder or a search context.
    useEffect(() => {
        if (!emailIdParam) return;
        const emailId = Number(emailIdParam);
        if (!Number.isSafeInteger(emailId) || emailId <= 0 || pendingRemovalsRef.current.has(emailId)) return;
        const controller = new AbortController();
        // Per Klick geöffnet: der Stand des Klicks. Direktlink oder Zurück: der aktuelle Stand.
        const ausKlick = klickAuswahlRef.current?.emailId === emailId ? klickAuswahlRef.current : null;
        klickAuswahlRef.current = null;
        const selectionVersion = ausKlick ? ausKlick.version : selectionVersionRef.current;
        fetch(`/api/emails/${emailId}`, { signal: controller.signal })
            .then(res => { if (res.ok) return res.json(); throw new Error('not found'); })
            .then((email: EmailItem) => {
                if (controller.signal.aborted || pendingRemovalsRef.current.has(emailId)
                    || selectionVersionRef.current !== selectionVersion) return;
                setSelectedEmail(email);
                setSelectedIds(new Set([email.id]));
                if (!email.isRead) {
                    fetch(`/api/emails/${email.id}/mark-read`, { method: 'POST' })
                        .then(res => {
                            if (!res.ok) return;
                            setEmails(prev => prev.map(item => item.id === email.id ? { ...item, isRead: true } : item));
                            aendereTreffer(prev => prev.map(item => item.id === email.id ? { ...item, isRead: true } : item));
                            loadStats();
                            refreshNotifications();
                        })
                        .catch(err => console.error('Failed to mark as read:', err));
                }
            })
            .catch(() => { /* keep an already selected list entry if detail loading fails */ });
        return () => controller.abort();
    }, [emailIdParam, activeFolder, loadStats, aendereTreffer]);

    // Ordnerwechsel ohne offenen Editor und ohne gewählte Mail in der Adresse: Auswahl leeren.
    const [auswahlOrdner, setAuswahlOrdner] = useState(activeFolder);
    if (auswahlOrdner !== activeFolder) {
        setAuswahlOrdner(activeFolder);
        if (!isComposing && !emailIdParam) {
            setSelectedEmail(null);
            setSelectedIds(new Set());
        }
    }
    // Der Anker für die Umschalt-Auswahl ist ein Ref – den setzt der Effekt zurück.
    useEffect(() => {
        if (!isComposing && !emailIdParam) lastSelectedIdRef.current = null;
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [activeFolder]);

    // Steht vor den Handlern, die diese Werte lesen.
    // Eine Zeile pro Verlauf (wie Gmail): Die jüngste geladene Nachricht steht stellvertretend
    // in der Liste; Sortierung und "Ungelesen" richten sich nach dem ganzen Verlauf, damit neue
    // Antworten den Verlauf nach oben holen. Suchtreffer stehen einzeln da (der Nutzer sucht gezielt
    // nach einer bestimmten Nachricht).
    const threadRows = useMemo(() => groupEmailThreads(emails), [emails]);
    const threadRowByEmailId = useMemo(() => {
        const byId = new Map<number, EmailThreadRow>();
        for (const row of threadRows) for (const member of row.members) byId.set(member.id, row);
        return byId;
    }, [threadRows]);

    /** Alle geladenen Nachrichten der Verläufe zu den gewählten Listenzeilen. */
    const expandToThreadMembers = (ids: number[]): number[] => {
        if (sucheAktiv) return ids;
        const expanded = new Set<number>();
        for (const id of ids) {
            const members = threadRowByEmailId.get(id)?.members;
            if (members) members.forEach(member => expanded.add(member.id));
            else expanded.add(id);
        }
        return Array.from(expanded);
    };

    // Gegenstelle pro Verlauf: der externe Kunde/Partner – auch wenn die jüngste Nachricht
    // eine eigene Folgenachricht oder Selbst-Antwort ist.
    const threadCounterparts = useMemo(() => {
        const counterparts = new Map<number, string>();
        for (const row of threadRows) {
            for (const e of row.members) {
                let name: string | undefined;
                if (e.kundeName) {
                    name = e.kundeName;
                } else if (e.lieferantName) {
                    name = e.lieferantName;
                } else if (e.direction === 'IN' && e.fromAddress && !isOwnEmail(e.fromAddress)) {
                    name = extractDisplayName(e.fromAddress);
                } else if (e.direction === 'OUT' && e.recipient && !isOwnEmail(e.recipient)) {
                    const external = parseRecipientList(e.recipient).filter(p => !isOwnEmail(p.email));
                    name = external.length > 0 ? external[0].displayName : extractDisplayName(e.recipient);
                }
                if (name) {
                    counterparts.set(row.email.id, name);
                    break;
                }
            }
        }
        return counterparts;
    }, [threadRows, isOwnEmail]);

    // Suchtreffer kommen fertig sortiert vom Server; Sortierung und Lese-Filter gelten nur für die Ordnerliste.
    const filteredEmails = useMemo(() => {
        if (sucheAktiv) return suchTreffer;
        let rows = threadRows;
        // Gesendet hat kein sinnvolles Lese-Konzept.
        // Ein Verlauf gilt als ungelesen, sobald eine seiner Nachrichten ungelesen ist.
        if (readFilter !== 'all' && activeFolder !== 'sent') {
            rows = rows.filter(row => readFilter === 'unread' ? row.anyUnread : !row.anyUnread);
        }
        return [...rows]
            .sort((a, b) => sortOrder === 'desc' ? b.latestActivity - a.latestActivity : a.latestActivity - b.latestActivity)
            .map(row => row.email);
    }, [threadRows, sucheAktiv, suchTreffer, sortOrder, readFilter, activeFolder]);

    /** Wo eine Mail liegt: bei der Suche in allen Ordnern allein ihr eigener Ordner (unbekannt = `null`). */
    const ordnerDerMail = (email: EmailItem) => ablageOrdner(email, sucheAktiv && suche.modus === 'alle', activeFolder);
    const liegtImPapierkorb = (email: EmailItem) => ordnerDerMail(email) === 'trash';

    // Handlers
    const handleEmailClick = async (e: React.MouseEvent, email: EmailItem) => {
        if (!await persistComposer()) return;
        selectionVersionRef.current += 1;
        if (isComposing) {
            // Entwurf wird automatisch gespeichert – kein Bestätigungsdialog nötig
            setIsComposing(false);
            setReplyToEmail(null);
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
                if (newSelected.size === 0) lastSelectedIdRef.current = null;
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
            zeigeEmail(email, newSelected.size === 1);
        } else {
            if (selectedEmail?.id === id) setSelectedEmail(null);
        }
    };

    /** Zeigt eine Mail rechts an, lädt sie vollständig und markiert ihren Verlauf als gelesen. */
    const zeigeEmail = (email: EmailItem, inAdresse: boolean) => {
        const id = email.id;
        setSelectedEmail(email);
        // Update URL to reflect selected email (single-select only)
        if (inAdresse) {
            // Nur wenn sich die URL ändert – sonst läuft der Effekt nicht und der Stand bliebe liegen.
            if (emailIdParam !== String(id)) klickAuswahlRef.current = { emailId: id, version: selectionVersionRef.current };
            navigate(`/emails/${activeFolder}/${id}`, { replace: true });
        }
        // Fetch full email detail (list DTO has truncated body/no htmlBody)
        fetch(`/api/emails/${id}`)
            .then(r => { if (r.ok) return r.json(); throw new Error(); })
            .then((full: EmailItem) => setSelectedEmail(prev => prev?.id === id ? full : prev))
            .catch(() => { /* keep list version as fallback */ });
        // Der ganze Verlauf ist jetzt sichtbar – alle ungelesenen Nachrichten darin als gelesen markieren.
        const unreadIds = sucheAktiv
            ? (email.isRead ? [] : [id])
            : (threadRowByEmailId.get(id)?.members ?? [email]).filter(member => !member.isRead).map(member => member.id);
        if (unreadIds.length > 0) {
            // allSettled: Was auf dem Server gelesen ist, wird auch lokal gelesen – auch wenn
            // einzelne Aufrufe scheitern.
            void Promise.allSettled(unreadIds.map(unreadId => fetch(`/api/emails/${unreadId}/mark-read`, { method: 'POST' })
                .then(res => { if (!res.ok) throw new Error(`HTTP ${res.status}`); return unreadId; })))
                .then(results => {
                    const marked = new Set(results.flatMap(result => result.status === 'fulfilled' ? [result.value] : []));
                    if (marked.size > 0) {
                        setEmails(prev => prev.map(e => marked.has(e.id) ? { ...e, isRead: true } : e));
                        aendereTreffer(prev => prev.map(e => marked.has(e.id) ? { ...e, isRead: true } : e));
                        // Refresh stats + Glocke (entfernt diese E-Mails aus dem Notification-Center)
                        loadStats();
                        refreshNotifications();
                    }
                    if (marked.size < results.length) {
                        console.error('Failed to mark as read:', results.filter(result => result.status === 'rejected'));
                        toast.error('E-Mail konnte nicht als gelesen markiert werden.');
                    }
                });
        }
    };

    /** Nimmt Mails aus Ordnerliste, Suchtreffern und Auswahl; die Detail-Abfrage für sie bleibt gesperrt. */
    const entferneAusListen = (ids: number[]) => {
        const weg = new Set(ids);
        ids.forEach(id => pendingRemovalsRef.current.add(id));
        setEmails(prev => prev.filter(e => !weg.has(e.id)));
        aendereTreffer(prev => prev.filter(e => !weg.has(e.id)));
        setSelectedIds(prev => new Set([...prev].filter(id => !weg.has(id))));
        return weg;
    };

    /** Beim Löschen wie Outlook/Gmail: Die nächste Mail der Liste öffnet sich, sonst bleibt rechts der leere Hinweis. */
    const ausListeNehmen = (ids: number[]) => {
        const betroffen = (selectedEmail !== null && ids.includes(selectedEmail.id)) || [...selectedIds].some(id => ids.includes(id));
        const naechste = betroffen && !isComposing ? naechsteNachLoeschen(filteredEmails, new Set(ids)) : null;
        entferneAusListen(ids);
        if (!betroffen) return;
        if (naechste) {
            selectionVersionRef.current += 1;
            lastSelectedIdRef.current = naechste.id;
            setSelectedIds(new Set([naechste.id]));
            zeigeEmail(naechste, true);
        } else {
            setSelectedEmail(null);
        }
    };

    const { loeschen, wiederherstellen } = useEmailLoeschen({
        ausblenden: ausListeNehmen,
        neuLaden: (ids) => {
            ids.forEach(id => pendingRemovalsRef.current.delete(id));
            folderCacheRef.current.clear();
            void refreshEmailsSilently();
            void loadStats();
            if (sucheAktiv) sucheNeuLaden();
        },
        geaendert: () => {
            folderCacheRef.current.clear();
            void loadStats();
            refreshNotifications();
        },
    });

    /**
     * Löscht Listenzeilen samt ihrem Verlauf im Ordner. Außerhalb des Papierkorbs ohne Rückfrage mit
     * „Rückgängig“, im Papierkorb endgültig mit Rückfrage. Bei gemischter Auswahl (Suche in allen
     * Ordnern) wandert nur, was noch nicht im Papierkorb liegt.
     */
    const loescheZeilen = (zeilen: EmailItem[]) => {
        if (zeilen.length === 0) return Promise.resolve();
        const endgueltig = zeilen.every(liegtImPapierkorb);
        const auswahl = endgueltig ? zeilen : zeilen.filter(zeile => !liegtImPapierkorb(zeile));
        const ids = expandToThreadMembers(auswahl.map(zeile => zeile.id));
        return loeschen(ids, { endgueltig, betreff: auswahl.length === 1 ? auswahl[0].subject : null });
    };

    /** „Wiederherstellen“ im Papierkorb: Zeilen samt Verlauf zurück in ihren Ursprungsordner. */
    const stelleZeilenWiederHer = (zeilen: EmailItem[]) =>
        wiederherstellen(expandToThreadMembers(zeilen.map(zeile => zeile.id)));

    /** Markierte Zeilen bzw. die geöffnete Mail. */
    const ausgewaehlteZeilen = (): EmailItem[] => {
        const markiert = filteredEmails.filter(email => selectedIds.has(email.id));
        if (markiert.length > 0) return markiert;
        return selectedEmail ? [selectedEmail] : [];
    };

    const handleAssign = async (type: ZuordnungsZiel, targetId: number) => {
        // Eine Listenzeile steht für den ganzen Verlauf – er landet beim Projekt/bei der Anfrage/beim Lieferanten.
        // Bewusst anders zugeordnete Mitglieder (z. B. Lieferanten-Rechnung im Kunden-Verlauf) bleiben, wo sie sind.
        const chosenIds = selectedIds.size > 1 ? Array.from(selectedIds) : (selectedEmail ? [selectedEmail.id] : []);
        const chosen = new Set(chosenIds);
        const idsToAssign = expandToThreadMembers(chosenIds).filter(id => {
            if (chosen.has(id)) return true;
            const typ = threadRowByEmailId.get(id)?.members.find(member => member.id === id)?.zuordnungTyp;
            return !typ || typ === 'KEINE';
        });
        if (idsToAssign.length === 0) return;

        for (const emailId of idsToAssign) {
            const url = `/api/emails/${emailId}/assign/${type}/${targetId}`;
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

    /** Hebt die Zuordnung der geöffneten Mail auf – sie gilt danach wieder als nicht zugeordnet. */
    const handleUnassign = async () => {
        const email = selectedEmail;
        if (!email) return;
        const res = await fetch(`/api/emails/${email.id}/unassign`, { method: 'POST' });
        if (!res.ok) throw new Error('Zuordnung aufheben fehlgeschlagen');
        const aktualisiert = await res.json().catch(() => null) as Partial<EmailItem> | null;
        const ohneZuordnung: Partial<EmailItem> = {
            zuordnungTyp: 'KEINE',
            projektId: undefined, projektName: undefined, projektAuftragsnummer: undefined,
            anfrageId: undefined, anfrageName: undefined,
            lieferantId: undefined, lieferantName: undefined,
            ...(aktualisiert ?? {}),
        };
        setSelectedEmail(prev => (prev && prev.id === email.id ? { ...prev, ...ohneZuordnung } : prev));
        setEmails(prev => prev.map(e => (e.id === email.id ? { ...e, ...ohneZuordnung } : e)));
        folderCacheRef.current.clear();
        loadStats();
        refreshEmailsSilently();
    };

    const handleBlockSender = async (emailIds?: number[]) => {
        const ids = emailIds || (selectedEmail ? [selectedEmail.id] : []);
        if (ids.length === 0) return;

        const count = ids.length;
        const message = count === 1
            ? `Möchten Sie den Absender "${selectedEmail?.fromAddress}" wirklich sperren?\nAlle bisherigen E-Mails dieses Absenders werden gelöscht und neue Nachrichten zukünftig komplett blockiert.`
            : `Möchten Sie die Absender von ${count} E-Mails sperren?\nAlle bisherigen E-Mails dieser Absender werden gelöscht und neue Nachrichten zukünftig komplett blockiert.`;
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
            refreshNotifications();
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
            refreshNotifications();
        } catch (err) {
            console.error(err);
            toast.error("Fehler beim Markieren als Nicht-Spam");
            refreshEmailsSilently();
        }
    };

    const handleMarkNotNewsletter = async (emailIds?: number[]) => {
        const ids = emailIds || (selectedEmail ? [selectedEmail.id] : []);
        if (ids.length === 0) return;

        const idsSet = new Set(ids);
        ids.forEach(id => pendingRemovalsRef.current.add(id));
        setEmails(prev => prev.filter(e => !idsSet.has(e.id)));
        setSelectedIds(new Set());
        setSelectedEmail(null);

        try {
            let successCount = 0;
            for (const id of ids) {
                const res = await fetch(`/api/emails/${id}/mark-not-newsletter`, { method: 'POST' });
                if (res.ok) successCount++;
            }
            toast.info(successCount === 1 ? "Kein Newsletter – zurück im Posteingang" : `${successCount} E-Mails in Posteingang verschoben`);
            folderCacheRef.current.clear();
            loadStats();
            refreshNotifications();
        } catch (err) {
            console.error(err);
            toast.error("Fehler beim Verschieben in Posteingang");
            refreshEmailsSilently();
        }
    };

    const handleConfirmNewsletter = async (emailIds?: number[]) => {
        const ids = emailIds || (selectedEmail ? [selectedEmail.id] : []);
        if (ids.length === 0) return;

        const idsSet = new Set(ids);
        ids.forEach(id => pendingRemovalsRef.current.add(id));
        setEmails(prev => prev.filter(e => !idsSet.has(e.id)));
        setSelectedIds(new Set());
        setSelectedEmail(null);

        try {
            let successCount = 0;
            for (const id of ids) {
                const res = await fetch(`/api/emails/${id}/confirm-newsletter`, { method: 'POST' });
                if (res.ok) successCount++;
            }
            toast.success(successCount === 1 ? "Newsletter bestätigt – Modell lernt dazu" : `${successCount} Newsletter bestätigt`);
            folderCacheRef.current.clear();
            loadStats();
            refreshNotifications();
        } catch (err) {
            console.error(err);
            toast.error("Fehler beim Bestätigen des Newsletters");
            refreshEmailsSilently();
        }
    };

    const moveTargetLabel = (t: VerschiebeZiel) =>
        VERSCHIEBE_ZIELE.find(m => m.id === t)?.label ?? t;

    /** Listenzeilen zu IDs; die geöffnete Mail zählt auch, wenn sie (Direktlink) nicht in der Liste steht. */
    const zeilenZu = (ids: number[]): EmailItem[] => ids.flatMap(id => {
        const zeile = filteredEmails.find(email => email.id === id) ?? (selectedEmail?.id === id ? selectedEmail : undefined);
        return zeile ? [zeile] : [];
    });

    /**
     * Verschieben per Menü, Mehrfachauswahl oder Ziehen. Mails, die schon im Ziel liegen, bleiben, wo
     * sie sind. Der Papierkorb als Ziel nimmt denselben Weg wie Löschen – mit „Rückgängig“.
     */
    const handleMoveToFolder = async (target: VerschiebeZiel, emailIds?: number[]) => {
        const zeilenIds = emailIds && emailIds.length > 0
            ? emailIds
            : (selectedIds.size > 0 ? Array.from(selectedIds) : (selectedEmail ? [selectedEmail.id] : []));
        const zeilen = zeilenZu(zeilenIds).filter(zeile => ordnerDerMail(zeile) !== target);
        if (zeilen.length === 0) return;
        if (target === 'trash') return loescheZeilen(zeilen);
        const ids = expandToThreadMembers(zeilen.map(zeile => zeile.id));

        const idsSet = entferneAusListen(ids);
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
            refreshNotifications();
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

    const handleDragStart = (e: DragStartEvent) => {
        const data = e.active.data.current as { emailId?: number } | undefined;
        const id = data?.emailId;
        if (id == null) return;
        const email = filteredEmails.find(x => x.id === id) ?? emails.find(x => x.id === id) ?? null;
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
        const ziel = VERSCHIEBE_ZIELE.find(z => z.id === targetFolder);
        if (!ziel) return;
        if (startedId == null) return;

        const ids = (selectedIds.has(startedId) && selectedIds.size > 1)
            ? Array.from(selectedIds)
            : [startedId];
        handleMoveToFolder(ziel.id, ids);
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
                folderCacheRef.current.delete(emailListCacheKey(activeFolder, false));
                folderCacheRef.current.delete(emailListCacheKey(activeFolder, true));
                loadStats();
            } else {
                throw new Error('Fehler');
            }
        } catch {
            toast.error("Fehler beim Markieren als gelesen");
            refreshEmailsSilently();
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

    // IntersectionObserver: lädt nach, sobald das Listenende in Sicht kommt.
    // Nach einem gescheiterten Nachladen der Suche nur noch per Knopf, sonst liefe es in einer Schleife.
    useEffect(() => {
        const node = loadMoreSentinelRef.current;
        if (!node) return;
        const observer = new IntersectionObserver(
            (entries) => {
                if (!entries[0]?.isIntersecting) return;
                if (!sucheAktiv) loadMoreEmails();
                else if (!sucheNachladeFehler) sucheMehrLaden();
            },
            { rootMargin: '200px 0px' } // Vorlaufzeit, damit's "ganz am Ende" gefuehlt asynchron startet
        );
        observer.observe(node);
        return () => observer.disconnect();
    }, [sucheAktiv, sucheNachladeFehler, sucheMehrLaden, loadMoreEmails, hasMore, sucheHatMehr, emails.length, suchTreffer.length]);

    // Entf/⌫ löschen die geöffnete bzw. markierten Mails – nicht beim Schreiben, in Entwürfen oder Dialogen.
    useLoeschTaste(!isComposing && !previewAttachment && !(activeFolder === 'drafts' && !sucheAktiv),
        () => { void loescheZeilen(ausgewaehlteZeilen()); });

    /** Fett/markiert, solange irgendeine Nachricht des Verlaufs ungelesen ist. */
    const isRowUnread = (email: EmailItem) => sucheAktiv
        ? !email.isRead
        : (threadRowByEmailId.get(email.id)?.anyUnread ?? !email.isRead);

    const filteredDrafts = useMemo(() => {
        return [...filtereEntwuerfe(drafts, suche.begriff)].sort((a, b) => {
            const dateA = a.updatedAt ? new Date(a.updatedAt).getTime() : 0;
            const dateB = b.updatedAt ? new Date(b.updatedAt).getTime() : 0;
            return sortOrder === 'desc' ? dateB - dateA : dateA - dateB;
        });
    }, [drafts, suche.begriff, sortOrder]);

    // Set von E-Mail-IDs, zu denen ein Entwurf existiert (replyEmailId → draftId)
    const draftsByEmailId = useMemo(() => {
        const map = new Map<number, DraftItem>();
        for (const d of drafts) {
            if (d.replyEmailId) map.set(d.replyEmailId, d);
        }
        return map;
    }, [drafts]);

    const isDraftFolderView = activeFolder === 'drafts' && !sucheAktiv;
    const visibleItemCount = isDraftFolderView ? filteredDrafts.length : filteredEmails.length;
    const leerArt: LeerArt = sucheAktiv ? 'suche'
        : isDraftFolderView ? 'entwuerfe'
            : readFilter === 'unread' && activeFolder !== 'sent' ? 'ungelesen' : 'ordner';

    // Right Pane Content Logic
    const renderRightPane = () => {
        if (isComposing) {
            let initialRecipient = activeDraft?.recipient || '';
            let initialCc: string[] | undefined;
            let initialSubject = activeDraft?.subject || '';
            let replyQuote: string | undefined;
            const initialBody: string | undefined = activeDraft?.body;

            if (forwardEmail) {
                // Forward mode – empty recipient, Fwd: prefix, original body as quote
                initialSubject = buildForwardSubject(forwardEmail.subject);

                const senderName = getSenderName(forwardEmail);
                const date = new Date(forwardEmail.sentAt || '').toLocaleDateString('de-DE', {
                    day: '2-digit', month: '2-digit', year: 'numeric',
                }) + ', ' + new Date(forwardEmail.sentAt || '').toLocaleTimeString('de-DE', {
                    hour: '2-digit', minute: '2-digit',
                }) + ' Uhr';

                let cleanBody = forwardEmail.htmlBody || forwardEmail.body || '';
                try {
                    const parser = new DOMParser();
                    const doc = parser.parseFromString(cleanBody, 'text/html');
                    doc.querySelectorAll('script, style, link').forEach(el => el.remove());
                    cleanBody = doc.body.innerHTML;
                } catch { /* ignore */ }

                // replyQuote verwenden (nicht initialBody), damit EmailComposeForm
                // die korrekte Reihenfolge: [leer] → [Signatur] → [Zitat] erzeugt
                replyQuote = `<div class="email-quote" style="border-top:1px solid #e2e8f0;padding-top:1rem;margin-top:1rem;color:#64748b">
                    <p style="font-size:0.8125rem;color:#94a3b8;margin-bottom:0.5rem">---------- Weitergeleitete Nachricht ----------<br/>
                    Von: ${escapeHtml(senderName)} &lt;${escapeHtml(forwardEmail.fromAddress || '')}&gt;<br/>
                    Datum: ${escapeHtml(date)}<br/>
                    Betreff: ${escapeHtml(forwardEmail.subject || '')}<br/>
                    An: ${escapeHtml(forwardEmail.recipient || '')}</p>
                    ${cleanBody}
                </div>`;
            } else if (replyToEmail) {
                const isReplyToOut = replyToEmail.direction === 'OUT' || isOwnEmail(replyToEmail.fromAddress);
                // Empfänger nie die eigene Firmenadresse; bei "Allen antworten" auch An/CC der Mail.
                const addressing = buildReplyAddressing(replyToEmail, replyMode, isOwnEmail);
                initialRecipient = addressing.to;
                initialCc = addressing.cc;
                initialSubject = buildReplySubject(replyToEmail.subject);

                // Zitat aufbauen
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

                const quoteHeader = isReplyToOut
                    ? (initialRecipient
                        ? `Am ${date} schrieben Sie an ${initialRecipient}:`
                        : `Am ${date} schrieben Sie:`)
                    : `Am ${date} schrieb ${getSenderName(replyToEmail)}:`;

                replyQuote = `<div class="email-quote" style="border-left:3px solid #e2e8f0;padding-left:1rem;color:#64748b;margin-top:0.5rem">
                    <p style="font-size:0.8125rem;color:#94a3b8;margin-bottom:0.5rem">${escapeHtml(quoteHeader)}</p>
                    ${cleanBody}
                </div>`;
            }

            return (
                <EmailComposeForm
                    // Neu mounten, wenn ein anderer Entwurf/eine andere Antwort geöffnet wird –
                    // sonst bleiben Empfänger, Betreff und Zuordnung des vorherigen stehen.
                    key={`${composerVersion}-${activeDraftId ?? replyToEmailId ?? forwardEmail?.id ?? 'neu'}-${replyMode}`}
                    onBeforeLeave={registerBeforeLeave}
                    onClose={handleComposeClose}
                    onSuccess={handleComposeSuccess}
                    initialRecipient={initialRecipient}
                    initialCc={initialCc}
                    initialSubject={initialSubject}
                    initialBody={initialBody}
                    replyQuote={replyQuote}
                    draftId={activeDraftId}
                    replyEmailId={replyToEmailId}
                    // Weiterleitungs-Entwurf: fester Absender wie bei der Weiterleitung selbst.
                    weitergeleitetVonEmailId={forwardEmail?.id ?? activeDraft?.weitergeleitetVonEmailId ?? undefined}
                    // Anhänge der Original-Mail zum Mitsenden – nur aus der vollständig geladenen Mail.
                    // Sonst (Entwurf, Listen-Eintrag) lädt das Formular sie selbst.
                    weiterleitungsAnhaenge={forwardEmail?.htmlBody ? forwardEmail.attachments ?? [] : undefined}
                    // Antwort-Postfach nur übergeben, wenn es zur beantworteten Mail gehört;
                    // sonst holt das Formular es selbst (z. B. Antwort auf eine ältere Mail im Verlauf).
                    antwortPostfach={forwardEmail
                        ? forwardEmail.antwortPostfach
                        : replyToEmail && replyToEmail.id === replyToEmailId ? replyToEmail.antwortPostfach : undefined}
                    projektId={activeDraft?.projektId ?? (replyToEmail ?? forwardEmail)?.projektId}
                    anfrageId={activeDraft?.anfrageId ?? (replyToEmail ?? forwardEmail)?.anfrageId}
                    // Im E-Mail-Center ist der Vorgang nicht durch die Seite vorgegeben –
                    // hier darf (und soll) der User Projekt/Anfrage selbst verknüpfen.
                    zuordnungWaehlbar
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
                        {activeFolder !== 'sent' && (
                            <Button
                                variant="outline"
                                onClick={() => setShowAssignModal(true)}
                                className="w-full gap-2 justify-start h-11 border-slate-200 hover:bg-rose-50 hover:text-rose-700 hover:border-rose-300"
                            >
                                <FolderPlus className="w-4 h-4" />
                                Zuordnen ({selectedIds.size})
                            </Button>
                        )}

                        {activeFolder === 'trash' && (
                            <Button
                                variant="outline"
                                onClick={() => { void stelleZeilenWiederHer(ausgewaehlteZeilen()); }}
                                className="w-full gap-2 justify-start h-11 border-emerald-200 text-emerald-700 hover:bg-emerald-50 hover:text-emerald-800 hover:border-emerald-300"
                            >
                                <RotateCcw className="w-4 h-4" />
                                Wiederherstellen ({selectedIds.size})
                            </Button>
                        )}

                        {/* Verschieben nach – im Gesendet-Ordner ausgeblendet */}
                        {activeFolder !== 'sent' && (
                            <div className="rounded-lg border border-slate-200 bg-slate-50/60 p-2 space-y-1">
                                <div className="flex items-center gap-1.5 px-1.5 pb-1 text-[10px] font-bold text-slate-400 uppercase tracking-wider">
                                    <FolderInput className="w-3 h-3" />
                                    Verschieben nach
                                </div>
                                <div className="grid grid-cols-2 gap-1">
                                    {VERSCHIEBE_ZIELE.filter(t => t.id !== activeFolder).map(t => (
                                        <button
                                            key={t.id}
                                            type="button"
                                            onClick={() => handleMoveToFolder(t.id, bulkIds)}
                                            className="flex items-center gap-1.5 px-2 py-2 rounded-md text-xs font-medium text-slate-700 hover:bg-rose-50 hover:text-rose-700 border border-transparent hover:border-rose-200 transition-colors cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-500"
                                        >
                                            <t.icon className="w-3.5 h-3.5" />
                                            <span className="truncate">{t.label}</span>
                                        </button>
                                    ))}
                                </div>
                            </div>
                        )}

                        {activeFolder === 'spam' ? (
                            <Button
                                variant="outline"
                                onClick={() => handleMarkNotSpam(bulkIds)}
                                className="w-full gap-2 justify-start h-11 border-slate-200 hover:bg-emerald-50 hover:text-emerald-700 hover:border-emerald-300"
                            >
                                <ShieldCheck className="w-4 h-4" />
                                Kein Spam ({selectedIds.size})
                            </Button>
                        ) : activeFolder === 'newsletter' ? (
                            <>
                                <Button
                                    variant="outline"
                                    onClick={() => handleMarkSpam(bulkIds)}
                                    className="w-full gap-2 justify-start h-11 border-slate-200 hover:bg-red-50 hover:text-red-700 hover:border-red-300"
                                >
                                    <ShieldX className="w-4 h-4" />
                                    Ist Spam ({selectedIds.size})
                                </Button>
                                <Button
                                    variant="outline"
                                    onClick={() => handleMarkNotNewsletter(bulkIds)}
                                    className="w-full gap-2 justify-start h-11 border-slate-200 hover:bg-blue-50 hover:text-blue-700 hover:border-blue-300"
                                >
                                    <Inbox className="w-4 h-4" />
                                    Kein Newsletter ({selectedIds.size})
                                </Button>
                                <Button
                                    variant="outline"
                                    onClick={() => handleConfirmNewsletter(bulkIds)}
                                    className="w-full gap-2 justify-start h-11 border-slate-200 hover:bg-emerald-50 hover:text-emerald-700 hover:border-emerald-300"
                                >
                                    <CheckCircle2 className="w-4 h-4" />
                                    Newsletter bestätigen ({selectedIds.size})
                                </Button>
                            </>
                        ) : activeFolder !== 'trash' && activeFolder !== 'sent' && (
                            <Button
                                variant="outline"
                                onClick={() => handleMarkSpam(bulkIds)}
                                className="w-full gap-2 justify-start h-11 border-slate-200 hover:bg-red-50 hover:text-red-700 hover:border-red-300"
                            >
                                <ShieldX className="w-4 h-4" />
                                Als Spam markieren ({selectedIds.size})
                            </Button>
                        )}

                        {activeFolder !== 'sent' && (
                            <Button
                                variant="outline"
                                onClick={() => handleBlockSender(bulkIds)}
                                className="w-full gap-2 justify-start h-11 border-slate-200 hover:bg-red-50 hover:text-red-700 hover:border-red-300"
                            >
                                <ShieldAlert className="w-4 h-4" />
                                Absender sperren ({selectedIds.size})
                            </Button>
                        )}

                        <div className="h-px bg-slate-200 my-1" />

                        <Button
                            variant="outline"
                            onClick={() => { void loescheZeilen(ausgewaehlteZeilen()); }}
                            title="Taste Entf"
                            className="w-full gap-2 justify-start h-11 border-red-200 text-red-600 hover:bg-red-50 hover:text-red-700 hover:border-red-300"
                        >
                            <Trash2 className="w-4 h-4" />
                            {ausgewaehlteZeilen().every(liegtImPapierkorb) ? `Endgültig löschen (${selectedIds.size})` : `In Papierkorb (${selectedIds.size})`}
                        </Button>
                    </div>

                    <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => { setSelectedIds(new Set()); setSelectedEmail(null); lastSelectedIdRef.current = null; }}
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
                    <EmailDetailHeader
                        email={selectedEmail}
                        folder={ordnerDerMail(selectedEmail)}
                        onReply={() => handleReply(selectedEmail)}
                        onReplyAll={hasFurtherRecipients(selectedEmail, isOwnEmail)
                            ? () => handleReply(selectedEmail, undefined, 'replyAll') : undefined}
                        onForward={() => handleForward(selectedEmail)}
                        onAssign={() => setShowAssignModal(true)}
                        onStar={() => handleToggleStar(selectedEmail.id)}
                        onDelete={() => loescheZeilen([selectedEmail])}
                        onRestore={() => stelleZeilenWiederHer([selectedEmail])}
                        onMove={(target) => handleMoveToFolder(target, [selectedEmail.id])}
                        onSpam={handleMarkSpam}
                        onNotSpam={handleMarkNotSpam}
                        onBlock={handleBlockSender}
                        onNotNewsletter={handleMarkNotNewsletter}
                        onConfirmNewsletter={handleConfirmNewsletter}
                    />

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
                            canReplyAll={(entry) => hasFurtherRecipients(entry, isOwnEmail)}
                            onReply={(entry, mode) => {
                                // Thread-Eintrag → replyToEmail (mit Kontext aus selectedEmail)
                                const sameSender = extractEmailAddress(entry.fromAddress).toLowerCase()
                                    === extractEmailAddress(selectedEmail.fromAddress).toLowerCase();
                                const replyItem: EmailItem = {
                                    id: entry.id,
                                    type: selectedEmail.type,
                                    subject: entry.subject ?? selectedEmail.subject,
                                    fromAddress: entry.fromAddress ?? selectedEmail.fromAddress,
                                    recipient: entry.recipient ?? selectedEmail.recipient,
                                    cc: entry.cc,
                                    replyToAddress: entry.replyToAddress,
                                    sentAt: entry.sentAt ?? selectedEmail.sentAt,
                                    body: entry.snippet,
                                    htmlBody: entry.htmlBody ?? undefined,
                                    direction: (entry.direction as 'IN' | 'OUT') ?? selectedEmail.direction,
                                    projektId: selectedEmail.projektId,
                                    anfrageId: selectedEmail.anfrageId,
                                    lieferantId: selectedEmail.lieferantId,
                                    kundeId: selectedEmail.kundeId,
                                    // Der Kundenname gehört nur zum Absender der gewählten Mail – nicht
                                    // zu einem Lieferanten, der im selben Verlauf geschrieben hat.
                                    kundeName: sameSender ? selectedEmail.kundeName : undefined,
                                };
                                handleReply(replyItem, entry.id, mode);
                            }}
                            onOpenDraft={(entry) => {
                                if (entry.draftId) {
                                    const existingDraft = drafts.find(draft => draft.id === entry.draftId);
                                    const draft: DraftItem = existingDraft ?? {
                                        id: entry.draftId,
                                        recipient: entry.recipient ?? undefined,
                                        subject: entry.subject ?? undefined,
                                        body: entry.htmlBody ?? undefined,
                                        fromAddress: entry.fromAddress ?? undefined,
                                        replyEmailId: selectedEmail?.id,
                                    };
                                    handleOpenDraft(draft);
                                }
                            }}
                            onDeleteDraft={async (draftId) => {
                                await handleDeleteDraft(draftId);
                                // Thread neu laden damit der Entwurf verschwindet
                                if (selectedEmail?.id) {
                                    try {
                                        const res = await fetch(`/api/emails/${selectedEmail.id}/thread`);
                                        if (res.ok) setThread(await res.json());
                                    } catch { /* ignorieren */ }
                                }
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

    const ungeleseneAnzahl = emails.filter(e => !e.isRead).length;

    return (
        <>
        {hasDataRouter && <EmailNavigationGuard active={isComposing} persist={persistComposer} />}
        <DndContext sensors={sensors} onDragStart={handleDragStart} onDragEnd={handleDragEnd}>
        <div ref={mailLayoutRef} className="flex h-full min-h-0 w-full min-w-0 bg-slate-100 overflow-hidden">
            <EmailFolderSidebar
                width={effectiveSidebarWidth}
                collapsed={isSidebarCollapsed}
                resizing={isDraggingSidebar}
                activeFolder={activeFolder}
                counts={folderCounts}
                dragActive={dragActiveEmail !== null}
                onToggle={toggleSidebar}
                onCompose={handleComposeNew}
                onSelect={setActiveFolder}
            />

            {/* Splitter 1: Sidebar Resizer */}
            {!isSidebarCollapsed && (
                <div
                    onPointerDown={handleSidebarResizeStart}
                    className={cn(
                        "w-1 hover:w-1.5 bg-slate-200/80 hover:bg-rose-400 active:bg-rose-600 cursor-col-resize transition-all select-none relative shrink-0 z-20 group",
                        isDraggingSidebar && "bg-rose-500 w-1.5"
                    )}
                    title="Ordnerspalte verschieben"
                >
                    <div className="absolute inset-y-0 -left-1.5 -right-1.5 cursor-col-resize" />
                </div>
            )}

            {/* Middle List - Email List */}
            <div
                style={{ width: effectiveListWidth }}
                className="bg-white border-r border-slate-200 flex flex-col shrink-0 min-w-[280px]"
            >
                {/* Besonderheiten des Ordners: Aufräumfrist, eigene Entwürfe, „Nicht zugeordnet“ */}
                {!(sucheAktiv && suche.modus === 'alle') && <OrdnerHinweis ordner={activeFolder} />}
                {/* Ordner-Header mit "Alle gelesen"-Button */}
                {!sucheAktiv && activeFolder !== 'sent' && ungeleseneAnzahl > 0 && (
                    <UngelesenKopfzeile
                        anzahl={ungeleseneAnzahl}
                        onAlleGelesen={handleMarkAllRead}
                    />
                )}
                <EmailSuchleiste begriff={suche.begriff} onBegriff={suche.setBegriff} modus={suche.modus} onModus={suche.setModus}
                    sucheAktiv={sucheAktiv} sortierung={sortOrder} onSortierung={setSortOrder}
                    leseFilter={readFilter} onLeseFilter={setReadFilter} zeigeLeseFilter={activeFolder !== 'sent' && !isDraftFolderView} />
                <EmailSuchHinweis modus={suche.modus} begriff={suche.begriff} aktiv={sucheAktiv} laedt={suche.laedt}
                    fehler={suche.fehler} anzahl={suchTreffer.length} hatMehr={sucheHatMehr}
                    serverSuche={!(activeFolder === 'drafts' && suche.modus === 'ordner')} />

                {/* List */}
                <div className="flex-1 overflow-y-auto">
                    {(sucheAktiv ? suche.laedt : loading) ? (
                        <EmailListeLaedt />
                    ) : sucheAktiv && suche.fehler ? (
                        <EmailSuchFehler onErneut={sucheNeuLaden} />
                    ) : visibleItemCount === 0 ? (
                        <EmailListeLeer art={leerArt} begriff={suche.begriff} modus={suche.modus} onAlleOrdner={() => suche.setModus('alle')} />
                    ) : (
                        <div className="divide-y divide-slate-100">
                            {isDraftFolderView ? filteredDrafts.map(draft => (
                                <EmailEntwurfZeile key={draft.id} entwurf={draft} eigenerName={eigenerName}
                                    onOeffnen={handleOpenDraft} onLoeschen={handleDeleteDraft} />
                            )) : filteredEmails.map(email => (
                                <EmailListenZeile key={email.id} email={email}
                                    name={threadCounterparts.get(email.id) || getDisplayName(email, isOwnEmail)}
                                    ausgewaehlt={selectedIds.has(email.id)} ungelesen={isRowUnread(email)}
                                    zeigeOrdner={sucheAktiv && suche.modus === 'alle'} imPapierkorb={liegtImPapierkorb(email)}
                                    ziehbar={activeFolder !== 'sent'} entwurf={draftsByEmailId.get(email.id)}
                                    onOeffnen={handleEmailClick} onStern={(zeile) => handleToggleStar(zeile.id)}
                                    onLoeschen={(zeile) => { void loescheZeilen([zeile]); }} onEntwurf={handleOpenDraft} />
                            ))}
                            {/* Listenende: lädt beim Hinscrollen nach oder per Knopf */}
                            {!isDraftFolderView && (
                                <EmailListenEnde ref={loadMoreSentinelRef}
                                    laedtMehr={sucheAktiv ? suche.laedtMehr : loadingMore}
                                    hatMehr={sucheAktiv ? sucheHatMehr : hasMore}
                                    onMehr={sucheAktiv ? sucheMehrLaden : () => { void loadMoreEmails(); }} />
                            )}
                        </div>
                    )}
                </div>

                {/* Footer Status */}
                <div className="px-4 py-2.5 border-t border-slate-200 text-xs text-slate-500 flex items-center justify-between gap-2">
                    <span>
                        {(() => {
                            // Bei „Ungelesen" und bei Suchtreffern wäre die Gesamtzahl des Ordners irreführend.
                            const total = sucheAktiv || nurUngelesen
                                ? null
                                : (folderTotals[activeFolder] ?? null);
                            const noun = sucheAktiv ? 'Treffer'
                                : visibleItemCount === 1
                                    ? (isDraftFolderView ? 'Entwurf' : 'Nachricht')
                                    : (isDraftFolderView ? 'Entwürfe' : 'Nachrichten');
                            if (total != null && total > visibleItemCount) {
                                return `${visibleItemCount} von ${total} ${noun}`;
                            }
                            return `${visibleItemCount} ${noun}`;
                        })()}
                    </span>
                    <div className="flex items-center gap-2">
                        {!isDraftFolderView && selectedIds.size > 0 && (
                            <span className="text-rose-600 font-medium">{selectedIds.size} ausgewählt</span>
                        )}
                    </div>
                </div>
            </div>

            {/* Splitter 2: List Resizer */}
            <div
                onPointerDown={handleListResizeStart}
                className={cn(
                    "w-1 hover:w-1.5 bg-slate-200/80 hover:bg-rose-400 active:bg-rose-600 cursor-col-resize transition-all select-none relative shrink-0 z-20 group",
                    isDraggingList && "bg-rose-500 w-1.5"
                )}
                title="Listenbreite verschieben"
            >
                <div className="absolute inset-y-0 -left-1.5 -right-1.5 cursor-col-resize" />
            </div>

            {/* Right Pane - Preview/Compose */}
            <div role="region" aria-label="E-Mail lesen und schreiben" className="flex-1 bg-slate-50 flex flex-col min-h-0 min-w-0 overflow-hidden">
                {renderRightPane()}
            </div>

            {/* Assignment Modal */}
            <AssignModal
                isOpen={showAssignModal}
                onClose={() => setShowAssignModal(false)}
                onAssign={handleAssign}
                emailSubject={selectedEmail?.subject || ''}
                emailId={selectedEmail?.id}
                // Aufheben nur für die eine geöffnete Mail – nicht bei Mehrfachauswahl.
                aktuelleZuordnung={selectedIds.size > 1 || !selectedEmail ? null : ermittleEmailZuordnung(selectedEmail)?.label ?? null}
                onUnassign={handleUnassign}
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
        </>
    );
}
