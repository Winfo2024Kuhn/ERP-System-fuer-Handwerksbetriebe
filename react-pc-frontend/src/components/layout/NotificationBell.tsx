import { useState, useEffect, useLayoutEffect, useRef, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { cn } from '../../lib/utils';
import {
    Bell, Mail, Plane, FileText, AlertTriangle, Truck, CalendarClock, X, Package, CheckCircle2,
    Inbox, Wallet, Users, Building2, Briefcase, Globe, CheckCheck
} from 'lucide-react';

// ── Types & Pure-Logic Helpers ───────────────────────────────────────────
// Die Pure-Functions (filterDismissed/dismissItem/dismissCategory) liegen in
// notification-helpers.ts, damit Vitest sie ohne Komponenten-Mount testen
// kann und Vites Fast-Refresh-Regel hier nicht bricht.

import type { CategoryDto, RecentItemDto, NotificationSummary } from './notification-helpers';
import { dismissItem, dismissCategory, filterDismissed } from './notification-helpers';

// ── Icon map ─────────────────────────────────────────────────────────────

const RECENT_TYPE_COLORS: Record<string, string> = {
    EMAIL: 'text-blue-500',
    URLAUBSANTRAG: 'text-emerald-500',
    BAUTAGEBUCH: 'text-amber-500',
    EINGANG_FAELLIG: 'text-orange-500',
    AUSGANG_UEBERFAELLIG: 'text-red-500',
    RECHNUNG: 'text-violet-500',
    TERMIN: 'text-rose-500',
    LIEFERSCHEIN: 'text-cyan-500',
    REKLAMATION: 'text-pink-500',
    FREIGABE_ANGENOMMEN: 'text-emerald-500',
    ANFRAGE_WEBSEITE: 'text-rose-600',
};

const RECENT_TYPE_ICONS: Record<string, React.ComponentType<{ className?: string }>> = {
    EMAIL: Mail,
    URLAUBSANTRAG: Plane,
    BAUTAGEBUCH: FileText,
    EINGANG_FAELLIG: AlertTriangle,
    AUSGANG_UEBERFAELLIG: AlertTriangle,
    RECHNUNG: Truck,
    TERMIN: CalendarClock,
    LIEFERSCHEIN: Package,
    REKLAMATION: AlertTriangle,
    FREIGABE_ANGENOMMEN: CheckCircle2,
    ANFRAGE_WEBSEITE: Globe,
};

// ── Gruppen-Definition ─────────────────────────────────────────────────
// Strukturiert die Kategorien thematisch — sonst wirkt die Liste zerfasert.

interface NotificationGroup {
    id: string;
    label: string;
    icon: React.ComponentType<{ className?: string }>;
    accentText: string;
    accentBg: string;
    /** Category-Types (CategoryDto.type) die zu dieser Gruppe gehören. */
    types: string[];
    /** RecentItem-Types (RecentItemDto.type) die zu dieser Gruppe gehören. */
    recentTypes: string[];
}

// Reihenfolge bestimmt das Rendering — Webseiten-Anfragen sollen ganz oben sein.
const GROUPS: NotificationGroup[] = [
    {
        id: 'webseite',
        label: 'Neu von der Webseite',
        icon: Globe,
        accentText: 'text-rose-700',
        accentBg: 'bg-rose-100',
        types: ['ANFRAGEN_WEBSEITE'],
        recentTypes: ['ANFRAGE_WEBSEITE'],
    },
    {
        id: 'posteingaenge',
        label: 'Posteingänge',
        icon: Inbox,
        accentText: 'text-blue-600',
        accentBg: 'bg-blue-50',
        types: ['EMAILS', 'EMAILS_PROJECTS', 'EMAILS_OFFERS', 'EMAILS_SUPPLIERS', 'EMAILS_SPAM', 'EMAILS_NEWSLETTER'],
        recentTypes: ['EMAIL'],
    },
    {
        id: 'geschaeft',
        label: 'Geschäft',
        icon: Briefcase,
        accentText: 'text-emerald-600',
        accentBg: 'bg-emerald-50',
        types: ['FREIGABEN_ANGENOMMEN', 'BAUTAGEBUCH'],
        recentTypes: ['FREIGABE_ANGENOMMEN', 'BAUTAGEBUCH'],
    },
    {
        id: 'finanzen',
        label: 'Finanzen',
        icon: Wallet,
        accentText: 'text-orange-600',
        accentBg: 'bg-orange-50',
        types: ['EINGANG_FAELLIG', 'AUSGANG_UEBERFAELLIG', 'RECHNUNGEN'],
        recentTypes: ['EINGANG_FAELLIG', 'AUSGANG_UEBERFAELLIG', 'RECHNUNG'],
    },
    {
        id: 'termine',
        label: 'Termine',
        icon: CalendarClock,
        accentText: 'text-rose-600',
        accentBg: 'bg-rose-50',
        types: ['TERMINE'],
        recentTypes: ['TERMIN'],
    },
    {
        id: 'personal',
        label: 'Personal',
        icon: Users,
        accentText: 'text-emerald-600',
        accentBg: 'bg-emerald-50',
        types: ['URLAUBSANTRAEGE'],
        recentTypes: ['URLAUBSANTRAG'],
    },
    {
        id: 'lieferanten',
        label: 'Lieferanten',
        icon: Building2,
        accentText: 'text-cyan-600',
        accentBg: 'bg-cyan-50',
        types: ['LIEFERSCHEINE', 'REKLAMATIONEN'],
        recentTypes: ['LIEFERSCHEIN', 'REKLAMATION'],
    },
];

// Pure Helpers (dismissItem, dismissCategory, filterDismissed) sind nach
// notification-helpers.ts ausgelagert – siehe Import oben.

// ── Component ────────────────────────────────────────────────────────────

export function NotificationBell() {
    const [rawData, setRawData] = useState<NotificationSummary | null>(null);
    const [data, setData] = useState<NotificationSummary | null>(null);
    const [open, setOpen] = useState(false);
    const [shake, setShake] = useState(false);
    const dropdownRef = useRef<HTMLDivElement>(null);
    const bellButtonRef = useRef<HTMLButtonElement>(null);
    const panelRef = useRef<HTMLDivElement>(null);
    // Use a ref for prevCount to avoid stale-closure issues in the callback
    const prevCountRef = useRef(0);
    const abortRef = useRef<AbortController | null>(null);
    const navigate = useNavigate();
    const [panelPos, setPanelPos] = useState<{ top: number; right: number } | null>(null);

    const fetchNotifications = useCallback(async () => {
        // Cancel any in-flight request before starting a new one
        abortRef.current?.abort();
        abortRef.current = new AbortController();
        const signal = abortRef.current.signal;

        try {
            let mitarbeiterId: number | null = null;
            try {
                const stored = localStorage.getItem('frontendUserSelection');
                if (stored) {
                    const parsed = JSON.parse(stored);
                    if (parsed.mitarbeiterId) mitarbeiterId = parsed.mitarbeiterId;
                }
            } catch { /* ignore */ }

            const params = new URLSearchParams();
            if (mitarbeiterId) params.set('mitarbeiterId', String(mitarbeiterId));
            const url = `/api/notifications/summary${params.toString() ? '?' + params.toString() : ''}`;

            const res = await fetch(url, { signal });
            if (!res.ok || signal.aborted) return;
            const summary: NotificationSummary = await res.json();
            if (signal.aborted) return;

            setRawData(summary);
            const filtered = filterDismissed(summary);
            // Animate if count increased (but not on the very first load)
            if (filtered.totalCount > prevCountRef.current && prevCountRef.current > 0) {
                setShake(true);
                setTimeout(() => setShake(false), 600);
            }
            prevCountRef.current = filtered.totalCount;
            setData(filtered);
        } catch (err) {
            if ((err as Error).name === 'AbortError') return;
            /* silently ignore other errors */
        }
    }, []); // stable reference – no external deps needed

    useEffect(() => {
        fetchNotifications();
        const interval = setInterval(fetchNotifications, 60_000);
        // Refresh immediately when the browser tab becomes visible again
        const handleVisibility = () => {
            if (document.visibilityState === 'visible') fetchNotifications();
        };
        // Andere Komponenten (z.B. EmailCenter) feuern dieses Event nach
        // mark-read/mark-spam/mark-not-spam, damit die Glocke sofort den
        // verringerten Stand zeigt – ohne auf das 60s-Polling zu warten.
        const handleRefresh = () => fetchNotifications();
        document.addEventListener('visibilitychange', handleVisibility);
        window.addEventListener('notifications:refresh', handleRefresh);
        return () => {
            abortRef.current?.abort();
            clearInterval(interval);
            document.removeEventListener('visibilitychange', handleVisibility);
            window.removeEventListener('notifications:refresh', handleRefresh);
        };
    }, [fetchNotifications]);

    // Position berechnen: Modal wird unter der Glocke geöffnet, an deren rechte Kante
    // ausgerichtet. Wenn die natürliche Breite nach links über den Bildschirm hinausragen
    // würde, wird das Modal mittig zentriert. Es wird nie breiter als der Viewport.
    const recomputePanelPosition = useCallback(() => {
        if (!bellButtonRef.current || !panelRef.current) return;
        const bell = bellButtonRef.current.getBoundingClientRect();
        const panel = panelRef.current.getBoundingClientRect();
        const vw = window.innerWidth;
        const margin = 16;
        const top = bell.bottom + 8;
        // Default: rechter Modal-Rand am rechten Glocken-Rand
        let right = vw - bell.right;
        const leftEdgeIfDefault = vw - right - panel.width;
        if (leftEdgeIfDefault < margin) {
            // Würde links überlaufen → mittig auf den verfügbaren Viewport setzen
            const centeredRight = (vw - panel.width) / 2;
            right = Math.max(margin, centeredRight);
        }
        setPanelPos(prev =>
            prev && prev.top === top && prev.right === right ? prev : { top, right }
        );
    }, []);

    useLayoutEffect(() => {
        if (!open) return;
        recomputePanelPosition();
    }, [open, data, recomputePanelPosition]);

    useEffect(() => {
        if (!open) return;
        const handler = () => recomputePanelPosition();
        window.addEventListener('resize', handler);
        // Wenn der Inhalt der Spalten sich ändert (z.B. Items kommen rein), ggf. neu zentrieren
        const ro = new ResizeObserver(() => recomputePanelPosition());
        if (panelRef.current) ro.observe(panelRef.current);
        return () => {
            window.removeEventListener('resize', handler);
            ro.disconnect();
        };
    }, [open, recomputePanelPosition]);

    // Close on outside click
    useEffect(() => {
        if (!open) return;
        const handler = (e: MouseEvent) => {
            if (dropdownRef.current && !dropdownRef.current.contains(e.target as Node)) {
                setOpen(false);
            }
        };
        document.addEventListener('mousedown', handler);
        return () => document.removeEventListener('mousedown', handler);
    }, [open]);

    const handleNavigate = (link: string, type?: string) => {
        setOpen(false);
        // Mark email as read when clicking on any email notification
        if (type === 'EMAIL' || type?.startsWith('EMAILS_')) {
            const match = link.match(/\/emails\/\w+\/(\d+)/);
            if (match) {
                fetch(`/api/emails/${match[1]}/mark-read`, { method: 'POST' })
                    .then(() => fetchNotifications())
                    .catch(() => { /* ignore */ });
            }
        }
        navigate(link);
    };

    /**
     * "Spalte als gelesen markieren": blendet alle aktuell sichtbaren Items dieser
     * Themengruppe für die laufende Sitzung aus, dismissed zusätzlich die zugehörigen
     * Kategorien (damit der Glocken-Zähler sofort sinkt) und ruft – falls es sich um
     * E-Mails handelt – serverseitig mark-read auf.
     */
    const handleMarkColumnRead = (e: React.MouseEvent, groupItems: RecentItemDto[], groupCats: CategoryDto[]) => {
        e.stopPropagation();
        groupItems.forEach(item => {
            dismissItem(item.type, item.title);
            if (item.type === 'EMAIL') {
                const match = item.link.match(/\/emails\/\w+\/(\d+)/);
                if (match) {
                    fetch(`/api/emails/${match[1]}/mark-read`, { method: 'POST' })
                        .catch(() => { /* fehler ignorieren – nächstes Polling korrigiert */ });
                }
            }
        });
        // Kategorien selbst dismissen, damit der Spalten-Counter und der Glocken-
        // Total-Zähler sofort runtergehen. Sobald wirklich neue Einträge eintrudeln,
        // wird die Kategorie automatisch wieder sichtbar (filterDismissed-Logik).
        groupCats.forEach(cat => dismissCategory(cat.type, cat.count));
        if (rawData) setData(filterDismissed(rawData));
        window.setTimeout(() => fetchNotifications(), 600);
    };

    const handleItemClick = (item: RecentItemDto) => {
        dismissItem(item.type, item.title);
        // Immediately update local display
        if (rawData) setData(filterDismissed(rawData));
        handleNavigate(item.link, item.type);
    };

    const total = data?.totalCount ?? 0;

    return (
        <div className="relative" ref={dropdownRef}>
            {/* Bell button */}
            <button
                ref={bellButtonRef}
                onClick={() => setOpen(!open)}
                className={cn(
                    "relative flex items-center justify-center w-10 h-10 rounded-xl transition-all",
                    "hover:bg-rose-50 active:scale-95",
                    open ? "bg-rose-50 ring-1 ring-rose-200" : "",
                    shake ? "animate-bell-shake" : ""
                )}
                title="Benachrichtigungen"
            >
                <Bell className={cn("w-5 h-5 transition-colors", total > 0 ? "text-rose-600" : "text-slate-400")} />

                {/* Badge */}
                {total > 0 && (
                    <span className="absolute -top-0.5 -right-0.5 flex items-center justify-center min-w-[18px] h-[18px] px-1 text-[10px] font-bold text-white bg-rose-600 rounded-full ring-2 ring-white animate-in zoom-in-50 duration-200">
                        {total > 99 ? '99+' : total}
                    </span>
                )}
            </button>

            {/* Dropdown — Breite und Position dynamisch; nie breiter als Bildschirm,
                und wenn nicht mehr neben der Glocke Platz ist, animiert mittig.  */}
            {open && data && (
                <div
                    ref={panelRef}
                    className="fixed w-fit max-w-[calc(100vw-2rem)] bg-white rounded-2xl shadow-2xl border border-slate-100 z-50 animate-in fade-in duration-200 overflow-hidden flex flex-col"
                    style={{
                        top: panelPos ? `${panelPos.top}px` : '4rem',
                        right: panelPos ? `${panelPos.right}px` : '1rem',
                        height: 'min(640px, 80vh)',
                        transition: 'right 240ms ease-out, top 240ms ease-out',
                    }}
                >
                    {/* Header */}
                    <div className="flex items-center justify-between px-5 py-3 bg-gradient-to-r from-rose-50 to-white border-b border-slate-100 shrink-0">
                        <div className="flex items-center gap-2">
                            <Bell className="w-4 h-4 text-rose-600" />
                            <span className="text-sm font-semibold text-slate-800">Benachrichtigungen</span>
                            {total > 0 && (
                                <span className="px-2 py-0.5 text-xs font-bold text-rose-600 bg-rose-100 rounded-full">
                                    {total}
                                </span>
                            )}
                        </div>
                        <button
                            type="button"
                            onClick={() => setOpen(false)}
                            title="Schließen"
                            className="p-1 rounded-lg hover:bg-slate-100 text-slate-400 hover:text-slate-600 transition-colors"
                        >
                            <X className="w-4 h-4" />
                        </button>
                    </div>

                    {/* Body — Themengruppen als Spalten nebeneinander; nur Items klickbar. */}
                    <div className="overflow-x-auto overflow-y-hidden flex-1">
                        {data.categories.length === 0 && data.recentItems.length === 0 ? (
                            <div className="p-12 text-center text-slate-400 min-w-[420px]">
                                <Bell className="w-10 h-10 mx-auto mb-3 text-slate-200" />
                                <p className="text-sm font-medium">Keine neuen Benachrichtigungen</p>
                                <p className="text-xs text-slate-400 mt-1">Alles erledigt – gut gemacht!</p>
                            </div>
                        ) : (
                            <div className="flex items-stretch divide-x divide-slate-100 h-full">
                                {GROUPS.map((group) => {
                                    const groupCats = data.categories.filter(c => group.types.includes(c.type));
                                    const groupItems = data.recentItems.filter(i => group.recentTypes.includes(i.type));
                                    // Spalte nur anzeigen, wenn die Gruppe Daten hat – sonst keine leeren Spalten.
                                    if (groupCats.length === 0 && groupItems.length === 0) return null;
                                    const groupTotal = groupCats.reduce((sum, c) => sum + c.count, 0);
                                    const GroupIcon = group.icon;
                                    const isLeadGruppe = group.id === 'webseite';
                                    return (
                                        <div
                                            key={group.id}
                                            className="flex flex-col w-[260px] shrink-0 h-full min-h-0"
                                        >
                                            {/* Spalten-Header — nicht klickbar, nur Orientierung;
                                                rechts ein kleiner Button, um alle Items der Spalte als gelesen
                                                zu markieren (E-Mails: serverseitig, andere: lokal ausblenden). */}
                                            <div className={cn(
                                                "flex flex-col gap-1 px-4 py-3 border-b border-slate-100 sticky top-0 z-10",
                                                isLeadGruppe ? "bg-rose-50" : "bg-slate-50/80"
                                            )}>
                                                <div className="flex items-center gap-2">
                                                    <div className={cn("flex items-center justify-center w-7 h-7 rounded-lg", group.accentBg)}>
                                                        <GroupIcon className={cn("w-4 h-4", group.accentText)} />
                                                    </div>
                                                    <span className={cn(
                                                        "text-[11px] font-bold uppercase tracking-wider leading-tight flex-1 min-w-0 truncate",
                                                        isLeadGruppe ? group.accentText : "text-slate-600"
                                                    )}>
                                                        {group.label}
                                                    </span>
                                                    {(groupItems.length > 0 || groupCats.length > 0) && (
                                                        <button
                                                            type="button"
                                                            onClick={(e) => handleMarkColumnRead(e, groupItems, groupCats)}
                                                            title="Alle als gelesen markieren"
                                                            className="shrink-0 p-1 rounded-md text-slate-400 hover:text-rose-600 hover:bg-white transition-colors"
                                                        >
                                                            <CheckCheck className="w-3.5 h-3.5" />
                                                        </button>
                                                    )}
                                                </div>
                                                {groupTotal > 0 && (
                                                    <span className={cn(
                                                        "self-start text-[11px] font-bold rounded-full px-2 py-0.5",
                                                        group.accentBg, group.accentText
                                                    )}>
                                                        {groupTotal} {groupTotal === 1 ? 'Eintrag' : 'Einträge'}
                                                    </span>
                                                )}
                                            </div>
                                            {/* Items — eigene Scrollbox pro Spalte, sonst werden lange Spalten zur Bremse */}
                                            <div className="overflow-y-auto flex-1">
                                                {groupItems.length > 0 ? (
                                                    groupItems.map((item, i) => {
                                                        const Icon = RECENT_TYPE_ICONS[item.type] || Bell;
                                                        const iconColor = RECENT_TYPE_COLORS[item.type] || 'text-slate-400';
                                                        return (
                                                            <button
                                                                key={`${group.id}-${item.type}-${i}`}
                                                                onClick={() => handleItemClick(item)}
                                                                className={cn(
                                                                    "w-full flex items-start gap-2.5 px-3 py-2.5 transition-colors text-left group border-b border-slate-50 last:border-b-0",
                                                                    isLeadGruppe
                                                                        ? "bg-rose-50/40 hover:bg-rose-50"
                                                                        : "hover:bg-rose-50/40"
                                                                )}
                                                                title="Direkt zu diesem Eintrag öffnen"
                                                            >
                                                                <div className={cn(
                                                                    "mt-0.5 p-1.5 rounded-lg shrink-0 transition-colors",
                                                                    isLeadGruppe ? "bg-white" : "bg-slate-50 group-hover:bg-white",
                                                                    iconColor
                                                                )}>
                                                                    <Icon className="w-3.5 h-3.5" />
                                                                </div>
                                                                <div className="flex-1 min-w-0">
                                                                    <p className="text-[13px] font-semibold text-slate-900 truncate">
                                                                        {item.title}
                                                                    </p>
                                                                    <p className="text-[11px] text-slate-500 truncate mt-0.5">
                                                                        {item.subtitle}
                                                                    </p>
                                                                </div>
                                                            </button>
                                                        );
                                                    })
                                                ) : (
                                                    <div className="px-3 py-3 text-[11px] text-slate-500">
                                                        {groupTotal} {groupTotal === 1 ? 'Eintrag' : 'Einträge'} – in der Übersicht öffnen.
                                                    </div>
                                                )}
                                            </div>
                                        </div>
                                    );
                                })}
                            </div>
                        )}
                    </div>
                </div>
            )}

            {/* Inline animation keyframes */}
            <style>{`
                @keyframes bell-shake {
                    0%, 100% { transform: rotate(0deg); }
                    15% { transform: rotate(12deg); }
                    30% { transform: rotate(-10deg); }
                    45% { transform: rotate(8deg); }
                    60% { transform: rotate(-6deg); }
                    75% { transform: rotate(3deg); }
                }
                .animate-bell-shake {
                    animation: bell-shake 0.6s ease-in-out;
                }
            `}</style>
        </div>
    );
}
