import { useState, useRef, useEffect, useCallback, useMemo } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import {
    Gem, Send, Plus, Trash2, MessageSquare, Pencil, Check, X,
    ExternalLink, Sparkles, Database, Search, FileText, Mail,
    BarChart3, ArrowRight, ChevronLeft, Clock, Bot,
    Zap, Globe, AlertCircle, Copy, CheckCheck, Square,
} from 'lucide-react';
import { cn } from '../lib/utils';

/* ═══════════════════════════════════════════════════════════
   Types
   ═══════════════════════════════════════════════════════════ */

interface MessageDto {
    id: number | null;
    role: 'user' | 'assistant';
    content: string;
    createdAt: string | null;
}

interface ChatSummary {
    id: number;
    title: string;
    createdAt: string;
    updatedAt: string;
}

interface ChatDetail {
    id: number;
    title: string;
    createdAt: string;
    updatedAt: string;
    messages: MessageDto[];
}

/* ═══════════════════════════════════════════════════════════
   User helper (same pattern as KiHilfeChat)
   ═══════════════════════════════════════════════════════════ */

const FRONTEND_USER_STORAGE_KEY = 'frontendUserSelection';

function getCurrentUser(): { id: number; displayName: string } | null {
    try {
        const raw = localStorage.getItem(FRONTEND_USER_STORAGE_KEY);
        if (!raw) return null;
        const parsed = JSON.parse(raw);
        if (parsed && typeof parsed.id === 'number') {
            return { id: parsed.id, displayName: parsed.displayName || parsed.username || 'Benutzer' };
        }
    } catch { /* ignore */ }
    return null;
}

/* ═══════════════════════════════════════════════════════════
   Valid routes for navigation links
   ═══════════════════════════════════════════════════════════ */

const VALID_ROUTES = new Set([
    '/projekte', '/anfragen', '/kunden', '/lieferanten', '/artikel',
    '/bestellungen', '/bestellungen/bedarf', '/textbausteine', '/leistungen',
    '/arbeitsgaenge', '/produktkategorien', '/mitarbeiter', '/arbeitszeitarten',
    '/kalender', '/emails', '/formulare', '/offeneposten',
    '/rechnungsuebersicht', '/miete', '/analyse', '/zeitbuchungen',
    '/auswertung', '/steuerberater', '/zeitkonten', '/feiertage',
    '/urlaubsantraege', '/firma', '/benutzer', '/ki-assistent',
]);

/* ═══════════════════════════════════════════════════════════
   Main Page Component
   ═══════════════════════════════════════════════════════════ */

export default function KiAssistentPage() {
    const navigate = useNavigate();
    const location = useLocation();
    const [user] = useState(() => getCurrentUser());
    const [chats, setChats] = useState<ChatSummary[]>([]);
    const [activeChatId, setActiveChatId] = useState<number | null>(null);
    const [messages, setMessages] = useState<MessageDto[]>([]);
    const [input, setInput] = useState('');
    const [loading, setLoading] = useState(false);
    const [loadingChats, setLoadingChats] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [editingChatId, setEditingChatId] = useState<number | null>(null);
    const [editTitle, setEditTitle] = useState('');
    const [sidebarOpen, setSidebarOpen] = useState(true);

    const messagesEndRef = useRef<HTMLDivElement>(null);
    const inputRef = useRef<HTMLTextAreaElement>(null);

    const userId = user?.id ?? null;

    /* ─── Load chat list ─── */
    const loadChats = useCallback(async () => {
        if (!userId) return;
        try {
            const res = await fetch(`/api/ki-chat?userId=${userId}`);
            if (res.ok) {
                const data: ChatSummary[] = await res.json();
                setChats(data);
            }
        } catch { /* ignore */ } finally {
            setLoadingChats(false);
        }
    }, [userId]);

    useEffect(() => { loadChats(); }, [loadChats]);

    /* ─── Load chat messages ─── */
    const loadChat = useCallback(async (chatId: number) => {
        if (!userId) return;
        setActiveChatId(chatId);
        setError(null);
        try {
            const res = await fetch(`/api/ki-chat/${chatId}?userId=${userId}`);
            if (res.ok) {
                const data: ChatDetail = await res.json();
                setMessages(data.messages);
            }
        } catch {
            setError('Chat konnte nicht geladen werden');
        }
    }, [userId]);

    /* ─── Create new chat ─── */
    const createChat = useCallback(async () => {
        if (!userId) return;
        try {
            const res = await fetch('/api/ki-chat', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ userId }),
            });
            if (res.ok) {
                const data: ChatDetail = await res.json();
                setActiveChatId(data.id);
                setMessages([]);
                await loadChats();
            }
        } catch {
            setError('Chat konnte nicht erstellt werden');
        }
    }, [userId, loadChats]);

    /* ─── Delete chat ─── */
    const deleteChat = useCallback(async (chatId: number) => {
        if (!userId) return;
        try {
            await fetch(`/api/ki-chat/${chatId}?userId=${userId}`, { method: 'DELETE' });
            if (activeChatId === chatId) {
                setActiveChatId(null);
                setMessages([]);
            }
            await loadChats();
        } catch { /* ignore */ }
    }, [userId, activeChatId, loadChats]);

    /* ─── Rename chat ─── */
    const renameChat = useCallback(async (chatId: number, title: string) => {
        if (!userId || !title.trim()) return;
        try {
            await fetch(`/api/ki-chat/${chatId}?userId=${userId}`, {
                method: 'PATCH',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ title: title.trim() }),
            });
            setEditingChatId(null);
            await loadChats();
        } catch { /* ignore */ }
    }, [userId, loadChats]);

    /* ─── Polling for async KI processing ─── */
    const pollingRef = useRef<ReturnType<typeof setInterval> | null>(null);

    const stopPolling = useCallback(() => {
        if (pollingRef.current) {
            clearInterval(pollingRef.current);
            pollingRef.current = null;
        }
    }, []);

    const startPolling = useCallback((chatId: number) => {
        stopPolling();
        pollingRef.current = setInterval(async () => {
            if (!userId) return;
            try {
                const res = await fetch(`/api/ki-chat/${chatId}/status?userId=${userId}`);
                if (!res.ok) return;
                const data = await res.json();

                if (data.status === 'done') {
                    stopPolling();
                    // Reload messages from DB
                    const chatRes = await fetch(`/api/ki-chat/${chatId}?userId=${userId}`);
                    if (chatRes.ok) {
                        const chatData: ChatDetail = await chatRes.json();
                        setMessages(chatData.messages);
                    }
                    setLoading(false);
                    await loadChats();
                } else if (data.status === 'error') {
                    stopPolling();
                    setError(data.error || 'KI-Fehler');
                    setLoading(false);
                } else if (data.status === 'cancelled') {
                    stopPolling();
                    setLoading(false);
                } else if (data.status === 'idle') {
                    // No active processing – maybe finished before polling started
                    stopPolling();
                    const chatRes = await fetch(`/api/ki-chat/${chatId}?userId=${userId}`);
                    if (chatRes.ok) {
                        const chatData: ChatDetail = await chatRes.json();
                        setMessages(chatData.messages);
                    }
                    setLoading(false);
                    await loadChats();
                }
            } catch { /* network error, keep polling */ }
        }, 1500);
    }, [userId, stopPolling, loadChats]);

    // Clean up polling on unmount
    useEffect(() => () => stopPolling(), [stopPolling]);

    // Resume polling on mount/chat switch if processing is active
    useEffect(() => {
        if (!activeChatId || !userId) return;
        let cancelled = false;
        (async () => {
            try {
                const res = await fetch(`/api/ki-chat/${activeChatId}/status?userId=${userId}`);
                if (!res.ok || cancelled) return;
                const data = await res.json();
                if (data.status === 'processing') {
                    setLoading(true);
                    startPolling(activeChatId);
                }
            } catch { /* ignore */ }
        })();
        return () => { cancelled = true; };
    }, [activeChatId, userId, startPolling]);

    /* ─── Cancel processing ─── */
    const cancelProcessing = useCallback(async () => {
        if (!activeChatId || !userId) return;
        try {
            await fetch(`/api/ki-chat/${activeChatId}/cancel?userId=${userId}`, { method: 'POST' });
        } catch { /* ignore */ }
        stopPolling();
        setLoading(false);
    }, [activeChatId, userId, stopPolling]);

    /* ─── Send message (async – fire & poll) ─── */
    const sendMessage = useCallback(async () => {
        const trimmed = input.trim();
        if (!trimmed || loading || !userId) return;

        let chatId = activeChatId;

        // Create a chat on the fly if none is selected
        if (!chatId) {
            try {
                const res = await fetch('/api/ki-chat', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ userId }),
                });
                if (!res.ok) return;
                const data: ChatDetail = await res.json();
                chatId = data.id;
                setActiveChatId(chatId);
            } catch { return; }
        }

        const optimisticMsg: MessageDto = { id: null, role: 'user', content: trimmed, createdAt: null };
        setMessages(prev => [...prev, optimisticMsg]);
        setInput('');
        if (inputRef.current) inputRef.current.style.height = 'auto';
        setError(null);
        setLoading(true);

        try {
            // Gather minimal page context
            const context = {
                route: location.pathname,
                pageTitle: 'KI-Assistent',
                visibleContent: undefined,
                errorMessages: undefined,
                latitude: undefined,
                longitude: undefined,
            };

            const res = await fetch(`/api/ki-chat/${chatId}/messages`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ userId, message: trimmed, context }),
            });

            const data = await res.json();
            if (!res.ok) {
                setError(data.error || 'KI-Fehler');
                setLoading(false);
                return;
            }

            // Backend returns 202 Accepted → start polling for result
            startPolling(chatId);
        } catch {
            setError('Verbindung fehlgeschlagen');
            setLoading(false);
        }
    }, [input, loading, userId, activeChatId, location.pathname, startPolling]);

    /* ─── Scroll + focus ─── */
    useEffect(() => {
        messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    }, [messages, loading]);

    useEffect(() => {
        if (!loading) inputRef.current?.focus();
    }, [activeChatId, loading]);

    /* ─── Keyboard ─── */
    const handleKeyDown = (e: React.KeyboardEvent) => {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            sendMessage();
        }
    };

    /* ─── Format relative time ─── */
    const formatRelativeTime = (dateStr: string) => {
        const date = new Date(dateStr);
        const now = new Date();
        const diffMs = now.getTime() - date.getTime();
        const diffMin = Math.floor(diffMs / 60000);
        const diffH = Math.floor(diffMin / 60);
        const diffD = Math.floor(diffH / 24);
        if (diffMin < 1) return 'Jetzt';
        if (diffMin < 60) return `${diffMin}m`;
        if (diffH < 24) return `${diffH}h`;
        if (diffD < 7) return `${diffD}d`;
        return date.toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit' });
    };

    const activeChat = useMemo(() => chats.find(c => c.id === activeChatId), [chats, activeChatId]);

    /* ─── No user ─── */
    if (!user) {
        return (
            <div className="flex items-center justify-center h-full">
                <div className="text-center">
                    <div className="w-14 h-14 rounded-2xl bg-slate-100 flex items-center justify-center mx-auto mb-4">
                        <AlertCircle className="w-7 h-7 text-slate-400" />
                    </div>
                    <p className="text-slate-500 text-sm">Bitte melde dich an, um den KI-Assistenten zu nutzen.</p>
                </div>
            </div>
        );
    }

    /* ═══════════════════════════════════════════════════════════
       Render – EmailCenter-style full-bleed layout
       ═══════════════════════════════════════════════════════════ */

    return (
        <div className="flex bg-slate-100 overflow-hidden -m-8 h-[calc(100%+4rem)] w-[calc(100%+4rem)]">
            {/* ─── Sidebar ─── */}
            <aside className={cn(
                'flex flex-col bg-slate-50/80 border-r border-slate-200/80 transition-all duration-300 ease-in-out flex-shrink-0',
                sidebarOpen ? 'w-72' : 'w-0 overflow-hidden'
            )}>
                {/* Sidebar Header */}
                <div className="p-4 border-b border-slate-200/80 bg-white space-y-3">
                    <div className="flex items-center gap-2.5">
                        <div className="w-9 h-9 rounded-xl bg-gradient-to-br from-rose-500 to-rose-600 flex items-center justify-center shadow-sm shadow-rose-200">
                            <Gem className="w-4 h-4 text-white" />
                        </div>
                        <div>
                            <h2 className="font-bold text-slate-900 text-sm leading-tight">KI-Assistent</h2>
                            <p className="text-[10px] text-slate-400 leading-tight">Chat-Verlauf</p>
                        </div>
                    </div>
                    <button
                        onClick={createChat}
                        className="w-full flex items-center justify-center gap-2 h-9 rounded-lg bg-rose-600 hover:bg-rose-700 text-white text-sm font-semibold shadow-sm shadow-rose-200/50 transition-colors cursor-pointer"
                    >
                        <Plus className="w-4 h-4" />
                        Neuer Chat
                    </button>
                </div>

                {/* Chat List */}
                <div className="flex-1 overflow-y-auto p-2">
                    {loadingChats ? (
                        <div className="space-y-1.5 animate-pulse p-1">
                            {Array.from({ length: 5 }).map((_, i) => (
                                <div key={i} className="flex items-center gap-2.5 px-3 py-2.5 rounded-lg">
                                    <div className="w-4 h-4 rounded bg-slate-200 flex-shrink-0" />
                                    <div className="flex-1 space-y-1.5">
                                        <div className="h-3.5 bg-slate-200 rounded w-3/4" />
                                        <div className="h-2.5 bg-slate-100 rounded w-1/2" />
                                    </div>
                                </div>
                            ))}
                        </div>
                    ) : chats.length === 0 ? (
                        <div className="flex flex-col items-center justify-center py-12 px-4">
                            <div className="w-12 h-12 rounded-full bg-slate-100 flex items-center justify-center mb-3">
                                <MessageSquare className="w-6 h-6 text-slate-300" />
                            </div>
                            <p className="text-sm font-medium text-slate-400 mb-1">Noch keine Chats</p>
                            <p className="text-xs text-slate-300 mb-4 text-center">Starte eine Unterhaltung mit dem KI-Assistenten</p>
                            <button
                                onClick={createChat}
                                className="text-xs font-semibold text-rose-600 hover:text-rose-700 cursor-pointer transition-colors"
                            >
                                Ersten Chat starten
                            </button>
                        </div>
                    ) : (
                        <div className="space-y-0.5">
                            {chats.map(chat => (
                                <div
                                    key={chat.id}
                                    className={cn(
                                        'group flex items-center gap-2.5 px-3 py-2.5 rounded-lg cursor-pointer transition-all duration-150',
                                        activeChatId === chat.id
                                            ? 'bg-white text-slate-900 shadow-sm border border-slate-200/80'
                                            : 'hover:bg-white/60 text-slate-600'
                                    )}
                                    onClick={() => { if (editingChatId !== chat.id) loadChat(chat.id); }}
                                >
                                    <MessageSquare className={cn(
                                        'w-4 h-4 flex-shrink-0 transition-colors',
                                        activeChatId === chat.id ? 'text-rose-500' : 'text-slate-300'
                                    )} />

                                    {editingChatId === chat.id ? (
                                        <div className="flex-1 flex items-center gap-1">
                                            <input
                                                value={editTitle}
                                                onChange={e => setEditTitle(e.target.value)}
                                                onKeyDown={e => {
                                                    if (e.key === 'Enter') renameChat(chat.id, editTitle);
                                                    if (e.key === 'Escape') setEditingChatId(null);
                                                }}
                                                className="flex-1 text-sm border border-rose-300 rounded-md px-2 py-1 focus:outline-none focus:ring-2 focus:ring-rose-200 bg-white"
                                                autoFocus
                                                onClick={e => e.stopPropagation()}
                                            />
                                            <button
                                                onClick={e => { e.stopPropagation(); renameChat(chat.id, editTitle); }}
                                                className="p-1 text-emerald-600 hover:bg-emerald-50 rounded cursor-pointer transition-colors"
                                            >
                                                <Check className="w-3.5 h-3.5" />
                                            </button>
                                            <button
                                                onClick={e => { e.stopPropagation(); setEditingChatId(null); }}
                                                className="p-1 text-slate-400 hover:bg-slate-100 rounded cursor-pointer transition-colors"
                                            >
                                                <X className="w-3.5 h-3.5" />
                                            </button>
                                        </div>
                                    ) : (
                                        <>
                                            <div className="flex-1 min-w-0">
                                                <p className="text-sm truncate leading-tight">{chat.title}</p>
                                                <p className="text-[10px] text-slate-400 mt-0.5 flex items-center gap-1">
                                                    <Clock className="w-2.5 h-2.5" />
                                                    {formatRelativeTime(chat.updatedAt)}
                                                </p>
                                            </div>
                                            <div className={cn(
                                                'flex items-center gap-0.5 transition-opacity',
                                                activeChatId === chat.id ? 'opacity-100' : 'opacity-0 group-hover:opacity-100'
                                            )}>
                                                <button
                                                    onClick={e => {
                                                        e.stopPropagation();
                                                        setEditingChatId(chat.id);
                                                        setEditTitle(chat.title);
                                                    }}
                                                    className="p-1 text-slate-400 hover:text-slate-600 hover:bg-slate-100 rounded cursor-pointer transition-colors"
                                                    title="Umbenennen"
                                                >
                                                    <Pencil className="w-3 h-3" />
                                                </button>
                                                <button
                                                    onClick={e => { e.stopPropagation(); deleteChat(chat.id); }}
                                                    className="p-1 text-slate-400 hover:text-red-600 hover:bg-red-50 rounded cursor-pointer transition-colors"
                                                    title="Löschen"
                                                >
                                                    <Trash2 className="w-3 h-3" />
                                                </button>
                                            </div>
                                        </>
                                    )}
                                </div>
                            ))}
                        </div>
                    )}
                </div>

                {/* Sidebar Footer */}
                <div className="px-4 py-3 border-t border-slate-200/80 bg-white">
                    <div className="flex items-center gap-2 text-[10px] text-slate-400">
                        <div className="flex items-center gap-1">
                            <Bot className="w-3 h-3" />
                            <span>Gemini · Agentic</span>
                        </div>
                        <span className="text-slate-200">·</span>
                        <span>{chats.length} {chats.length === 1 ? 'Chat' : 'Chats'}</span>
                    </div>
                </div>
            </aside>

            {/* ─── Main Chat Area ─── */}
            <div className="flex-1 flex flex-col min-w-0 bg-white">
                {/* Top bar */}
                <div className="flex items-center gap-3 px-4 py-2.5 bg-white border-b border-slate-200/80 flex-shrink-0">
                    <button
                        onClick={() => setSidebarOpen(v => !v)}
                        className="p-1.5 rounded-lg hover:bg-slate-100 transition-colors text-slate-400 hover:text-slate-600 cursor-pointer"
                        title={sidebarOpen ? 'Sidebar schließen' : 'Sidebar öffnen'}
                    >
                        <ChevronLeft className={cn('w-4 h-4 transition-transform duration-200', !sidebarOpen && 'rotate-180')} />
                    </button>

                    <div className="h-5 w-px bg-slate-200" />

                    <div className="flex items-center gap-2.5 flex-1 min-w-0">
                        <div className="min-w-0">
                            <h1 className="text-sm font-semibold text-slate-800 truncate">
                                {activeChat?.title || 'KI-Assistent'}
                            </h1>
                            <div className="flex items-center gap-1.5 mt-0.5">
                                {[
                                    { icon: Zap, label: '5 Tools' },
                                    { icon: Database, label: 'DB' },
                                    { icon: Globe, label: 'Google Search' },
                                ].map(cap => (
                                    <span key={cap.label} className="inline-flex items-center gap-0.5 text-[10px] text-slate-400">
                                        <cap.icon className="w-2.5 h-2.5" />
                                        {cap.label}
                                    </span>
                                ))}
                            </div>
                        </div>
                    </div>

                    {activeChatId && (
                        <button
                            onClick={createChat}
                            className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold text-rose-600 hover:bg-rose-50 rounded-lg transition-colors border border-rose-200 cursor-pointer"
                        >
                            <Plus className="w-3.5 h-3.5" />
                            <span className="hidden sm:inline">Neuer Chat</span>
                        </button>
                    )}
                </div>

                {/* Messages Area – only this scrolls */}
                <div className="flex-1 overflow-y-auto">
                    {messages.length === 0 && !loading ? (
                        /* ─── Empty State ─── */
                        <div className="flex flex-col items-center justify-center h-full px-4">
                            <div className="relative mb-8">
                                <div className="w-20 h-20 rounded-2xl bg-gradient-to-br from-rose-500 to-rose-600 flex items-center justify-center shadow-xl shadow-rose-200/50">
                                    <Sparkles className="w-10 h-10 text-white" />
                                </div>
                                <div className="absolute -bottom-1 -right-1 w-6 h-6 rounded-full bg-emerald-500 border-2 border-white flex items-center justify-center">
                                    <Zap className="w-3 h-3 text-white" />
                                </div>
                            </div>
                            <h2 className="text-2xl font-bold text-slate-800 mb-1">
                                Hallo{user.displayName ? `, ${user.displayName}` : ''}!
                            </h2>
                            <p className="text-slate-400 mb-10 text-center max-w-md text-sm">
                                Ich bin dein KI-Assistent mit Datenbankzugriff, E-Mail-Suche und Google Search.
                                Wie kann ich dir helfen?
                            </p>

                            {/* Suggestion Cards */}
                            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 max-w-2xl w-full">
                                {[
                                    { icon: BarChart3, title: 'Umsatzanalyse', text: 'Wie hoch ist der Umsatz dieses Quartal?', color: 'rose' as const },
                                    { icon: Search, title: 'Navigation', text: 'Wo finde ich die Zeiterfassung?', color: 'sky' as const },
                                    { icon: Database, title: 'Datenabfrage', text: 'Welche Projekte sind aktuell in Bearbeitung?', color: 'emerald' as const },
                                    { icon: Mail, title: 'E-Mails finden', text: 'Suche E-Mails vom Lieferanten Müller', color: 'amber' as const },
                                ].map((card) => (
                                    <button
                                        key={card.title}
                                        onClick={() => { setInput(card.text); inputRef.current?.focus(); }}
                                        className="group flex items-start gap-3 p-4 rounded-xl bg-white border border-slate-200 hover:border-rose-200 hover:shadow-md hover:shadow-rose-100/50 transition-all duration-200 text-left cursor-pointer"
                                    >
                                        <div className={cn(
                                            'w-9 h-9 rounded-lg flex items-center justify-center flex-shrink-0 transition-transform duration-200 group-hover:scale-105',
                                            card.color === 'rose' && 'bg-rose-50 text-rose-600',
                                            card.color === 'sky' && 'bg-sky-50 text-sky-600',
                                            card.color === 'emerald' && 'bg-emerald-50 text-emerald-600',
                                            card.color === 'amber' && 'bg-amber-50 text-amber-600',
                                        )}>
                                            <card.icon className="w-4 h-4" />
                                        </div>
                                        <div className="min-w-0 flex-1">
                                            <p className="text-sm font-semibold text-slate-700 group-hover:text-rose-700 transition-colors">{card.title}</p>
                                            <p className="text-xs text-slate-400 mt-0.5 leading-relaxed">{card.text}</p>
                                        </div>
                                        <ArrowRight className="w-4 h-4 text-slate-200 group-hover:text-rose-400 group-hover:translate-x-0.5 mt-1 flex-shrink-0 transition-all duration-200" />
                                    </button>
                                ))}
                            </div>

                            {/* Capabilities pills */}
                            <div className="flex flex-wrap justify-center gap-2 mt-10">
                                {[
                                    { icon: Search, label: 'Code-Suche' },
                                    { icon: FileText, label: 'Dateien lesen' },
                                    { icon: Database, label: 'Datenbank' },
                                    { icon: Mail, label: 'E-Mail-Suche' },
                                    { icon: Globe, label: 'Google Search' },
                                ].map(cap => (
                                    <span key={cap.label} className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full bg-slate-50 text-slate-400 text-[11px] font-medium border border-slate-100">
                                        <cap.icon className="w-3 h-3" />
                                        {cap.label}
                                    </span>
                                ))}
                            </div>
                        </div>
                    ) : (
                        /* ─── Messages ─── */
                        <div className="max-w-3xl mx-auto px-4 py-6 space-y-1">
                            {messages.map((msg, i) => (
                                <MessageBubble
                                    key={msg.id ?? `msg-${i}`}
                                    msg={msg}
                                    userInitial={(user.displayName || 'U')[0].toUpperCase()}
                                    onNavigate={navigate}
                                />
                            ))}

                            {loading && (
                                <div className="flex gap-3 py-4">
                                    <div className="w-7 h-7 rounded-lg bg-gradient-to-br from-rose-500 to-rose-600 flex items-center justify-center flex-shrink-0">
                                        <Gem className="w-3.5 h-3.5 text-white" />
                                    </div>
                                    <div className="bg-slate-50 border border-slate-200 rounded-2xl rounded-bl-md px-4 py-3.5">
                                        <div className="flex items-center gap-3">
                                            <div className="flex gap-1">
                                                <span className="w-2 h-2 bg-rose-400 rounded-full animate-bounce" style={{ animationDelay: '0ms' }} />
                                                <span className="w-2 h-2 bg-rose-300 rounded-full animate-bounce" style={{ animationDelay: '150ms' }} />
                                                <span className="w-2 h-2 bg-rose-200 rounded-full animate-bounce" style={{ animationDelay: '300ms' }} />
                                            </div>
                                            <span className="text-xs text-slate-400">KI denkt nach…</span>
                                            <button
                                                onClick={cancelProcessing}
                                                className="text-xs text-slate-400 hover:text-red-500 transition-colors cursor-pointer ml-2 flex items-center gap-1"
                                            >
                                                <Square className="w-3 h-3" />
                                                Abbrechen
                                            </button>
                                        </div>
                                    </div>
                                </div>
                            )}

                            {error && (
                                <div className="flex items-start gap-2 bg-red-50 border border-red-200 rounded-xl px-4 py-3 text-sm text-red-700 max-w-3xl mx-auto">
                                    <AlertCircle className="w-4 h-4 mt-0.5 flex-shrink-0" />
                                    <span>{error}</span>
                                </div>
                            )}

                            <div ref={messagesEndRef} />
                        </div>
                    )}
                </div>

                {/* ─── Input Bar ─── */}
                <div className="border-t border-slate-200/80 bg-white px-4 py-3 flex-shrink-0">
                    <div className="max-w-3xl mx-auto">
                        <div className={cn(
                            'flex items-end gap-3 rounded-2xl border px-4 py-3 transition-all duration-200',
                            input.trim()
                                ? 'bg-white border-rose-300 ring-2 ring-rose-100 shadow-sm'
                                : 'bg-slate-50 border-slate-200 focus-within:bg-white focus-within:border-rose-300 focus-within:ring-2 focus-within:ring-rose-100 focus-within:shadow-sm'
                        )}>
                            <textarea
                                ref={inputRef}
                                value={input}
                                onChange={e => {
                                    setInput(e.target.value);
                                    // Auto-resize textarea
                                    e.target.style.height = 'auto';
                                    e.target.style.height = Math.min(e.target.scrollHeight, 120) + 'px';
                                }}
                                onKeyDown={handleKeyDown}
                                placeholder="Nachricht an KI-Assistent…"
                                rows={1}
                                autoComplete="off"
                                autoCorrect="off"
                                data-1p-ignore
                                data-lpignore="true"
                                data-protonpass-ignore
                                data-form-type="other"
                                name="ki-chat-input"
                                role="textbox"
                                style={{ height: 'auto' }}
                                className="flex-1 bg-transparent resize-none text-sm text-slate-800 placeholder:text-slate-400 focus:outline-none max-h-[120px] overflow-y-auto leading-relaxed"
                                disabled={loading}
                            />
                            {loading ? (
                                <button
                                    onClick={cancelProcessing}
                                    className="p-2 rounded-xl transition-all duration-200 flex-shrink-0 cursor-pointer bg-slate-700 text-white hover:bg-slate-800 shadow-sm"
                                    title="Abbrechen"
                                >
                                    <Square className="w-4 h-4" />
                                </button>
                            ) : (
                                <button
                                    onClick={sendMessage}
                                    disabled={!input.trim()}
                                    className={cn(
                                        'p-2 rounded-xl transition-all duration-200 flex-shrink-0 cursor-pointer',
                                        input.trim()
                                            ? 'bg-rose-600 text-white hover:bg-rose-700 shadow-sm shadow-rose-200/50'
                                            : 'bg-slate-100 text-slate-300 cursor-not-allowed'
                                    )}
                                >
                                    <Send className="w-4 h-4" />
                                </button>
                            )}
                        </div>
                        <p className="text-[10px] text-slate-300 mt-2 text-center">
                            KI-Antworten können fehlerhaft sein · Agentic AI mit Datenbankzugriff & Google Search
                        </p>
                    </div>
                </div>
            </div>
        </div>
    );
}

/* ═══════════════════════════════════════════════════════════
   Message Bubble Component
   ═══════════════════════════════════════════════════════════ */

function MessageBubble({ msg, userInitial, onNavigate }: {
    msg: MessageDto;
    userInitial: string;
    onNavigate: (path: string) => void;
}) {
    const [copied, setCopied] = useState(false);

    const handleCopy = () => {
        navigator.clipboard.writeText(msg.content);
        setCopied(true);
        setTimeout(() => setCopied(false), 2000);
    };

    if (msg.role === 'user') {
        return (
            <div className="flex gap-3 py-3 justify-end">
                <div className="rounded-2xl rounded-br-md px-4 py-3 text-sm leading-relaxed max-w-[75%] bg-rose-600 text-white shadow-sm shadow-rose-200/30">
                    <span className="whitespace-pre-wrap">{msg.content}</span>
                </div>
                <div className="w-7 h-7 rounded-lg bg-slate-100 flex items-center justify-center flex-shrink-0 mt-0.5">
                    <span className="text-slate-600 text-xs font-bold">{userInitial}</span>
                </div>
            </div>
        );
    }

    return (
        <div className="group flex gap-3 py-3">
            <div className="w-7 h-7 rounded-lg bg-gradient-to-br from-rose-500 to-rose-600 flex items-center justify-center flex-shrink-0 mt-0.5 shadow-sm shadow-rose-200/30">
                <Gem className="w-3.5 h-3.5 text-white" />
            </div>
            <div className="min-w-0 max-w-[80%]">
                <div className="rounded-2xl rounded-bl-md px-4 py-3 text-sm leading-relaxed bg-slate-50 border border-slate-200/80 text-slate-700">
                    <MarkdownText text={msg.content} onNavigate={onNavigate} />
                </div>
                {/* Copy button */}
                <div className="flex items-center gap-2 mt-1 ml-1 opacity-0 group-hover:opacity-100 transition-opacity duration-150">
                    <button
                        onClick={handleCopy}
                        className="flex items-center gap-1 text-[10px] text-slate-400 hover:text-slate-600 transition-colors cursor-pointer"
                        title="Kopieren"
                    >
                        {copied ? <CheckCheck className="w-3 h-3 text-emerald-500" /> : <Copy className="w-3 h-3" />}
                        {copied ? 'Kopiert' : 'Kopieren'}
                    </button>
                </div>
            </div>
        </div>
    );
}

/* ═══════════════════════════════════════════════════════════
   Markdown rendering (shared logic from KiHilfeChat)
   ═══════════════════════════════════════════════════════════ */

function MarkdownText({ text, onNavigate }: { text: string; onNavigate: (path: string) => void }) {
    const lines = text.split('\n');
    const elements: React.ReactNode[] = [];
    const fmt = (t: string) => formatInline(t, onNavigate);

    let i = 0;
    while (i < lines.length) {
        const line = lines[i];

        // Table detection
        if (line.includes('|') && i + 1 < lines.length && /^[\s|:-]+$/.test(lines[i + 1].replace(/[^|:\s-]/g, ''))) {
            const tableLines: string[] = [];
            let j = i;
            while (j < lines.length && lines[j].includes('|')) { tableLines.push(lines[j]); j++; }
            if (tableLines.length >= 2) {
                const parseRow = (row: string) => row.split('|').map(c => c.trim()).filter(c => c.length > 0);
                const headers = parseRow(tableLines[0]);
                const dataRows = tableLines.slice(2).map(parseRow);
                elements.push(
                    <div key={i} className="my-3 overflow-x-auto rounded-lg border border-slate-200">
                        <table className="w-full text-xs">
                            <thead><tr className="bg-slate-50">
                                {headers.map((h, hi) => <th key={hi} className="px-3 py-2 text-left font-semibold text-slate-700 border-b border-slate-200">{fmt(h)}</th>)}
                            </tr></thead>
                            <tbody>
                                {dataRows.map((row, ri) => (
                                    <tr key={ri} className={ri % 2 === 0 ? 'bg-white' : 'bg-slate-50/50'}>
                                        {row.map((cell, ci) => <td key={ci} className="px-3 py-2 text-slate-600 border-b border-slate-100">{fmt(cell)}</td>)}
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                );
                i = j; continue;
            }
        }

        // Code block
        if (line.startsWith('```')) {
            const codeLines: string[] = [];
            i++;
            while (i < lines.length && !lines[i].startsWith('```')) { codeLines.push(lines[i]); i++; }
            i++; // skip closing ```
            elements.push(
                <pre key={`code-${i}`} className="my-2 p-3 rounded-lg bg-slate-900 text-slate-100 text-xs overflow-x-auto font-mono">
                    <code>{codeLines.join('\n')}</code>
                </pre>
            );
            continue;
        }

        if (line.startsWith('### ')) {
            elements.push(<h4 key={i} className="font-semibold text-sm mt-3 mb-1">{fmt(line.slice(4))}</h4>);
        } else if (line.startsWith('## ')) {
            elements.push(<h3 key={i} className="font-bold text-sm mt-3 mb-1">{fmt(line.slice(3))}</h3>);
        } else if (line.startsWith('# ')) {
            elements.push(<h2 key={i} className="font-bold text-base mt-3 mb-1">{fmt(line.slice(2))}</h2>);
        } else if (line.startsWith('- ') || line.startsWith('* ')) {
            elements.push(
                <div key={i} className="flex gap-2 ml-2">
                    <span className="text-rose-400 mt-0.5">•</span>
                    <span>{fmt(line.slice(2))}</span>
                </div>
            );
        } else if (/^\d+\.\s/.test(line)) {
            const match = line.match(/^(\d+)\.\s(.*)$/);
            if (match) {
                elements.push(
                    <div key={i} className="flex gap-2 ml-2">
                        <span className="text-rose-500 font-medium min-w-[1rem]">{match[1]}.</span>
                        <span>{fmt(match[2])}</span>
                    </div>
                );
            }
        } else if (line.trim() === '') {
            elements.push(<div key={i} className="h-2" />);
        } else {
            elements.push(<p key={i}>{fmt(line)}</p>);
        }
        i++;
    }

    return <div className="space-y-0.5">{elements}</div>;
}

function formatInline(text: string, onNavigate: (path: string) => void): React.ReactNode {
    const parts: React.ReactNode[] = [];
    const regex = /(\[([^\]]+)\]\((\/?[a-z][a-z0-9-/]*)\)|\*\*(.+?)\*\*|`(.+?)`|\*(.+?)\*)/g;
    let lastIndex = 0;
    let match;

    while ((match = regex.exec(text)) !== null) {
        if (match.index > lastIndex) parts.push(text.slice(lastIndex, match.index));

        if (match[2] && match[3]) {
            const linkText = match[2];
            const href = match[3].startsWith('/') ? match[3] : '/' + match[3];
            if (VALID_ROUTES.has(href)) {
                parts.push(
                    <button key={match.index} onClick={() => onNavigate(href)}
                        className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full bg-rose-100 text-rose-700 text-xs font-semibold hover:bg-rose-200 hover:text-rose-800 transition-colors border border-rose-200 shadow-sm align-middle cursor-pointer"
                        title={`Zu ${linkText} navigieren`}>
                        <ExternalLink className="w-3 h-3" />{linkText}
                    </button>
                );
            } else {
                parts.push(<strong key={match.index} className="font-semibold">{linkText}</strong>);
            }
        } else if (match[4]) {
            parts.push(<strong key={match.index} className="font-semibold">{match[4]}</strong>);
        } else if (match[5]) {
            parts.push(<code key={match.index} className="bg-slate-200 text-rose-700 px-1 py-0.5 rounded text-xs font-mono">{match[5]}</code>);
        } else if (match[6]) {
            parts.push(<em key={match.index}>{match[6]}</em>);
        }

        lastIndex = match.index + match[0].length;
    }

    if (lastIndex < text.length) parts.push(text.slice(lastIndex));
    return parts.length === 0 ? text : <>{parts}</>;
}
