import { useState, useEffect, useRef, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { cn } from '../../lib/utils';
import {
    Bell, Mail, Plane, FileText, AlertTriangle, Truck, CalendarClock, X, Package
} from 'lucide-react';

// ── Types ────────────────────────────────────────────────────────────────

interface CategoryDto {
    type: string;
    label: string;
    count: number;
    icon: string;
    link: string;
}

interface RecentItemDto {
    type: string;
    title: string;
    subtitle: string;
    timestamp: string;
    link: string;
}

interface NotificationSummary {
    totalCount: number;
    categories: CategoryDto[];
    recentItems: RecentItemDto[];
}

// ── Icon map ─────────────────────────────────────────────────────────────

const ICON_MAP: Record<string, React.ComponentType<{ className?: string }>> = {
    Mail, Plane, FileText, AlertTriangle, Truck, CalendarClock, Package,
};

const TYPE_COLORS: Record<string, { bg: string; text: string; border: string }> = {
    EMAILS: { bg: 'bg-blue-50', text: 'text-blue-600', border: 'border-blue-200' },
    URLAUBSANTRAEGE: { bg: 'bg-emerald-50', text: 'text-emerald-600', border: 'border-emerald-200' },
    BAUTAGEBUCH: { bg: 'bg-amber-50', text: 'text-amber-600', border: 'border-amber-200' },
    EINGANG_FAELLIG: { bg: 'bg-orange-50', text: 'text-orange-600', border: 'border-orange-200' },
    AUSGANG_UEBERFAELLIG: { bg: 'bg-red-50', text: 'text-red-600', border: 'border-red-200' },
    RECHNUNGEN: { bg: 'bg-violet-50', text: 'text-violet-600', border: 'border-violet-200' },
    TERMINE: { bg: 'bg-rose-50', text: 'text-rose-600', border: 'border-rose-200' },
    LIEFERSCHEINE: { bg: 'bg-cyan-50', text: 'text-cyan-600', border: 'border-cyan-200' },
    REKLAMATIONEN: { bg: 'bg-pink-50', text: 'text-pink-600', border: 'border-pink-200' },
};

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
};

// ── Dismissal helpers ────────────────────────────────────────────────────

const DISMISSAL_KEY = 'notification_dismissals';

interface Dismissals {
    categories: Record<string, number>; // type → timestamp
    items: Record<string, number>;      // "type-title" → timestamp
}

function loadDismissals(): Dismissals {
    try {
        const raw = localStorage.getItem(DISMISSAL_KEY);
        if (raw) return JSON.parse(raw);
    } catch { /* ignore */ }
    return { categories: {}, items: {} };
}

function saveDismissals(d: Dismissals) {
    localStorage.setItem(DISMISSAL_KEY, JSON.stringify(d));
}

function dismissCategory(type: string) {
    const d = loadDismissals();
    d.categories[type] = Date.now();
    saveDismissals(d);
}

function dismissItem(type: string, title: string) {
    const d = loadDismissals();
    d.items[`${type}::${title}`] = Date.now();
    saveDismissals(d);
}

function filterDismissed(data: NotificationSummary): NotificationSummary {
    const d = loadDismissals();
    const now = Date.now();
    const MAX_AGE = 5 * 60 * 1000; // 5 minutes — after that, re-show if still present

    // Clean up expired dismissals
    let changed = false;
    for (const key of Object.keys(d.categories)) {
        if (now - d.categories[key] > MAX_AGE) { delete d.categories[key]; changed = true; }
    }
    for (const key of Object.keys(d.items)) {
        if (now - d.items[key] > MAX_AGE) { delete d.items[key]; changed = true; }
    }
    if (changed) saveDismissals(d);

    const categories = data.categories.filter(c => !d.categories[c.type]);
    const recentItems = data.recentItems.filter(item => !d.items[`${item.type}::${item.title}`]);
    const totalCount = categories.reduce((sum, c) => sum + c.count, 0);

    return { totalCount, categories, recentItems };
}

// ── Component ────────────────────────────────────────────────────────────

export function NotificationBell() {
    const [rawData, setRawData] = useState<NotificationSummary | null>(null);
    const [data, setData] = useState<NotificationSummary | null>(null);
    const [open, setOpen] = useState(false);
    const [shake, setShake] = useState(false);
    const dropdownRef = useRef<HTMLDivElement>(null);
    // Use a ref for prevCount to avoid stale-closure issues in the callback
    const prevCountRef = useRef(0);
    const abortRef = useRef<AbortController | null>(null);
    const navigate = useNavigate();

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
        document.addEventListener('visibilitychange', handleVisibility);
        return () => {
            abortRef.current?.abort();
            clearInterval(interval);
            document.removeEventListener('visibilitychange', handleVisibility);
        };
    }, [fetchNotifications]);

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
        // Mark email as read when clicking on an email notification
        if (type === 'EMAIL') {
            const match = link.match(/emailId=(\d+)/);
            if (match) {
                fetch(`/api/emails/${match[1]}/mark-read`, { method: 'POST' })
                    .then(() => fetchNotifications())
                    .catch(() => { /* ignore */ });
            }
        }
        navigate(link);
    };

    const handleCategoryClick = (cat: CategoryDto) => {
        dismissCategory(cat.type);
        // Immediately update local display
        if (rawData) setData(filterDismissed(rawData));
        handleNavigate(cat.link, cat.type);
    };

    const handleItemClick = (item: RecentItemDto) => {
        dismissItem(item.type, item.title);
        // Immediately update local display
        if (rawData) setData(filterDismissed(rawData));
        handleNavigate(item.link, item.type);
    };

    const total = data?.totalCount ?? prevCountRef.current ?? 0;

    return (
        <div className="relative" ref={dropdownRef}>
            {/* Bell button */}
            <button
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

            {/* Dropdown */}
            {open && data && (
                <div className="absolute right-0 top-full mt-2 w-[420px] bg-white rounded-2xl shadow-2xl border border-slate-100 z-50 animate-in fade-in slide-in-from-top-2 duration-200 overflow-hidden">
                    {/* Header */}
                    <div className="flex items-center justify-between px-5 py-3 bg-gradient-to-r from-rose-50 to-white border-b border-slate-100">
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

                    {/* Category tiles */}
                    {data.categories.length > 0 ? (
                        <div className="grid grid-cols-2 gap-2 p-3">
                            {data.categories.map((cat) => {
                                const Icon = ICON_MAP[cat.icon] || Bell;
                                const colors = TYPE_COLORS[cat.type] || TYPE_COLORS.EMAILS;
                                return (
                                    <button
                                        key={cat.type}
                                        onClick={() => handleCategoryClick(cat)}
                                        className={cn(
                                            "flex items-center gap-3 p-3 rounded-xl border transition-all",
                                            "hover:shadow-sm hover:scale-[1.02] active:scale-[0.98]",
                                            colors.bg, colors.border
                                        )}
                                    >
                                        <div className={cn("p-2 rounded-lg", colors.bg)}>
                                            <Icon className={cn("w-4 h-4", colors.text)} />
                                        </div>
                                        <div className="text-left min-w-0">
                                            <p className={cn("text-lg font-bold leading-none", colors.text)}>
                                                {cat.count}
                                            </p>
                                            <p className="text-[11px] text-slate-500 truncate mt-0.5">
                                                {cat.label}
                                            </p>
                                        </div>
                                    </button>
                                );
                            })}
                        </div>
                    ) : (
                        <div className="p-8 text-center text-slate-400">
                            <Bell className="w-8 h-8 mx-auto mb-2 text-slate-200" />
                            <p className="text-sm">Keine neuen Benachrichtigungen</p>
                        </div>
                    )}

                    {/* Recent items */}
                    {data.recentItems.length > 0 && (
                        <>
                            <div className="px-4 py-2 border-t border-slate-100">
                                <p className="text-[10px] font-semibold text-slate-400 uppercase tracking-wider">
                                    Aktuell
                                </p>
                            </div>
                            <div className="max-h-[280px] overflow-y-auto">
                                {data.recentItems.map((item, i) => {
                                    const Icon = RECENT_TYPE_ICONS[item.type] || Bell;
                                    const iconColor = RECENT_TYPE_COLORS[item.type] || 'text-slate-400';
                                    return (
                                        <button
                                            key={`${item.type}-${i}`}
                                            onClick={() => handleItemClick(item)}
                                            className="w-full flex items-start gap-3 px-4 py-2.5 hover:bg-slate-50 transition-colors text-left group"
                                        >
                                            <div className={cn("mt-0.5 p-1.5 rounded-lg bg-slate-50 group-hover:bg-white transition-colors", iconColor)}>
                                                <Icon className="w-3.5 h-3.5" />
                                            </div>
                                            <div className="flex-1 min-w-0">
                                                <p className="text-sm font-medium text-slate-800 truncate">
                                                    {item.title}
                                                </p>
                                                <p className="text-xs text-slate-500 truncate">
                                                    {item.subtitle}
                                                </p>
                                            </div>
                                        </button>
                                    );
                                })}
                            </div>
                        </>
                    )}
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
