import { useState, useEffect, useLayoutEffect, useRef, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { cn } from '../../lib/utils';
import {
    Bell, Mail, Plane, FileText, AlertTriangle, Truck, CalendarClock, X, Package, CheckCircle2,
    Inbox, Wallet, Users, Building2, Briefcase, Globe, CheckCheck, Clock, CalendarCheck,
    Phone, PhoneMissed, Voicemail
} from 'lucide-react';

import { useToast } from '../ui/toast';
import { refreshEmails } from '../../lib/notificationRefresh';

// ── Types & Pure-Logic Helpers ───────────────────────────────────────────
// Die Pure-Functions (ohneEintrag/ohneSpalte/...) liegen in
// notification-helpers.ts, damit Vitest sie ohne Komponenten-Mount testen
// kann und Vites Fast-Refresh-Regel hier nicht bricht.

import type { CategoryDto, RecentItemDto, NotificationSummary } from './notification-helpers';
import { enthaeltEmailKategorie, entferneAlteMerker, ohneEintrag, ohneSpalte } from './notification-helpers';

/** Mehr ids nimmt POST /api/notifications/erledigt nicht an. */
const MAX_IDS_PRO_ANFRAGE = 200;

function postJson(url: string, body: unknown): Promise<Response> {
    // Den CSRF-Header setzt der globale Interceptor in main.tsx.
    return fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
    });
}

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
    ZEIT_AUTO_BEENDET: 'text-amber-600',
    MONATSABSCHLUSS: 'text-rose-600',
    SPRACHNACHRICHT: 'text-amber-600',
    VERPASSTER_ANRUF: 'text-red-600',
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
    ZEIT_AUTO_BEENDET: Clock,
    MONATSABSCHLUSS: CalendarCheck,
    SPRACHNACHRICHT: Voicemail,
    VERPASSTER_ANRUF: PhoneMissed,
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
        // Direkt hinter der Webseite: wer angerufen hat, wartet meist auf
        // einen Rückruf. Nur für Benutzer mit dem Telefon-Recht befüllt.
        id: 'telefon',
        label: 'Telefon',
        icon: Phone,
        accentText: 'text-sky-700',
        accentBg: 'bg-sky-50',
        types: ['SPRACHNACHRICHTEN', 'VERPASSTE_ANRUFE'],
        recentTypes: ['SPRACHNACHRICHT', 'VERPASSTER_ANRUF'],
    },
    {
        // Direkt hinter den Webseiten-Anfragen: Hier steht geschätzte statt
        // gestempelter Arbeitszeit in den Zeitkonten. Das muss auffallen,
        // bevor der Monat abgerechnet wird.
        id: 'zeiterfassung',
        label: 'Zeiten prüfen',
        icon: Clock,
        accentText: 'text-amber-700',
        accentBg: 'bg-amber-100',
        types: ['ZEITEN_AUTO_BEENDET', 'MONATSABSCHLUSS'],
        recentTypes: ['ZEIT_AUTO_BEENDET', 'MONATSABSCHLUSS'],
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

// ── Component ────────────────────────────────────────────────────────────

export function NotificationBell() {
    const [data, setData] = useState<NotificationSummary | null>(null);
    const toast = useToast();
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

            // Erledigtes hat das Backend schon herausgefiltert – hier wird
            // nichts mehr lokal weggelassen.
            // Animate if count increased (but not on the very first load)
            if (summary.totalCount > prevCountRef.current && prevCountRef.current > 0) {
                setShake(true);
                setTimeout(() => setShake(false), 600);
            }
            prevCountRef.current = summary.totalCount;
            setData(summary);
        } catch (err) {
            if ((err as Error).name === 'AbortError') return;
            /* silently ignore other errors */
        }
    }, []); // stable reference – no external deps needed

    useEffect(() => {
        // Altlast: früher merkte sich der Browser selbst, was weggeklickt war.
        entferneAlteMerker();
    }, []);

    useEffect(() => {
        // fetchNotifications setzt den State erst nach dem await (Abruf vom
        // Server) – der Lint hält den Erstabruf beim Mount fälschlich für ein
        // synchrones setState im Effect.
        // eslint-disable-next-line react-hooks/set-state-in-effect
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

    /**
     * Meldet dem Backend, was erledigt ist, und lädt danach in jedem Fall neu:
     * die Server-Antwort ist die Wahrheit – bei einem Fehler taucht der Eintrag
     * so wieder auf. Ein laufender Abruf wird vorher abgebrochen, damit er die
     * optimistische Anzeige nicht mit einem alten Stand überschreibt.
     */
    const meldeErledigt = async (url: string, body: unknown, fehlertext: string): Promise<boolean> => {
        abortRef.current?.abort();
        let ok = false;
        try {
            ok = (await postJson(url, body)).ok;
        } catch {
            ok = false;
        }
        if (!ok) toast.error(fehlertext);
        await fetchNotifications();
        return ok;
    };

    /**
     * "Spalte als gelesen markieren": leert die Spalte sofort und lässt das Backend
     * ALLE aktuellen Einträge ihrer Kategorien als erledigt merken (nicht nur die
     * sichtbaren). Bei E-Mail-Ordnern liest das Backend die Mails wirklich – das
     * offene E-Mail-Center bekommt deshalb Bescheid, damit seine Zahlen stimmen.
     */
    const handleMarkColumnRead = async (
        e: React.MouseEvent,
        group: NotificationGroup,
        groupItems: RecentItemDto[],
        groupCats: CategoryDto[],
    ) => {
        e.stopPropagation();
        const kategorien = groupCats.map(c => c.type);
        setData(prev => (prev ? ohneSpalte(prev, kategorien, group.recentTypes) : prev));
        const fehlertext = 'Die Meldungen konnten nicht als gelesen markiert werden.';
        const ok = kategorien.length > 0
            ? await meldeErledigt('/api/notifications/erledigt/kategorien', { kategorien }, fehlertext)
            : await meldeErledigt(
                '/api/notifications/erledigt',
                { ids: groupItems.slice(0, MAX_IDS_PRO_ANFRAGE).map(i => i.id) },
                fehlertext,
            );
        if (ok && (enthaeltEmailKategorie(kategorien) || groupItems.some(i => i.type === 'EMAIL'))) {
            refreshEmails();
        }
    };

    /**
     * Klick auf einen Eintrag: sofort ausblenden (Zähler der Kategorie −1), dem
     * Backend als erledigt melden und hinspringen. Bei E-Mails setzt das Backend
     * die Mail dabei selbst auf gelesen.
     */
    const handleItemClick = (item: RecentItemDto) => {
        setData(prev => (prev ? ohneEintrag(prev, item) : prev));
        setOpen(false);
        void meldeErledigt(
            '/api/notifications/erledigt',
            { ids: [item.id] },
            'Die Meldung konnte nicht als erledigt markiert werden.',
        ).then(ok => {
            if (ok && item.type === 'EMAIL') refreshEmails();
        });
        navigate(item.link);
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
                                                rechts ein kleiner Button, um alle Einträge der Spalte als
                                                erledigt zu markieren (das Backend merkt es sich pro Benutzer). */}
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
                                                            onClick={(e) => { void handleMarkColumnRead(e, group, groupItems, groupCats); }}
                                                            title="Alle als gelesen markieren"
                                                            aria-label={`${group.label}: alle als gelesen markieren`}
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
                                                    groupItems.map((item) => {
                                                        const Icon = RECENT_TYPE_ICONS[item.type] || Bell;
                                                        const iconColor = RECENT_TYPE_COLORS[item.type] || 'text-slate-400';
                                                        return (
                                                            <button
                                                                key={item.id}
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
                                                    // Fallback: Backend liefert Counter, aber keine Items (z.B. weil der
                                                    // Detail-Build im Server fehlgeschlagen ist). Statt einer toten Textzeile
                                                    // rendern wir die Kategorien als klickbare Zeilen, damit der Nutzer
                                                    // immer in die passende Übersicht gelangt.
                                                    groupCats.map((cat) => (
                                                        <button
                                                            key={`${group.id}-cat-${cat.type}`}
                                                            onClick={() => { setOpen(false); navigate(cat.link); }}
                                                            className={cn(
                                                                "w-full flex items-center gap-2.5 px-3 py-2.5 transition-colors text-left border-b border-slate-50 last:border-b-0",
                                                                isLeadGruppe
                                                                    ? "bg-rose-50/40 hover:bg-rose-50"
                                                                    : "hover:bg-rose-50/40"
                                                            )}
                                                            title="Übersicht öffnen"
                                                        >
                                                            <span className={cn(
                                                                "shrink-0 inline-flex items-center justify-center min-w-[22px] h-[22px] px-1.5 rounded-full text-[11px] font-bold",
                                                                group.accentBg, group.accentText
                                                            )}>
                                                                {cat.count}
                                                            </span>
                                                            <span className="text-[13px] font-medium text-slate-700 truncate flex-1">
                                                                {cat.label}
                                                            </span>
                                                        </button>
                                                    ))
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
