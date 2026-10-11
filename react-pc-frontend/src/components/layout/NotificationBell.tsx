import { useState, useEffect, useRef, useCallback } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { Bell } from 'lucide-react';
import { cn } from '../../lib/utils';
import { BENACHRICHTIGUNGEN_PFAD, entferneAlteMerker, summaryUrl, type NotificationSummary } from './notification-helpers';

/**
 * Die Glocke oben rechts: zeigt nur noch, wie viele Meldungen offen sind, und
 * führt per Klick auf die Seite „Benachrichtigungen“. Dort stehen alle
 * Meldungen nach Themen sortiert – das frühere breite Aufklapp-Fenster mit
 * vielen Spalten gibt es nicht mehr.
 *
 * Die Zahl kommt alle 60 Sekunden frisch vom Server, sofort beim Zurückkehren
 * in den Browser-Tab und immer dann, wenn eine andere Stelle das Ereignis
 * `notifications:refresh` auslöst (z. B. die Seite nach „Erledigt“ oder das
 * E-Mail-Center nach „gelesen“). Steigt die Zahl, wackelt die Glocke kurz.
 */
export function NotificationBell() {
    const [total, setTotal] = useState(0);
    const [shake, setShake] = useState(false);
    // Ref statt State, damit der Abruf-Callback stabil bleibt.
    const prevCountRef = useRef(0);
    const abortRef = useRef<AbortController | null>(null);
    const navigate = useNavigate();
    const { pathname } = useLocation();
    const aktiv = pathname === BENACHRICHTIGUNGEN_PFAD;

    const fetchNotifications = useCallback(async () => {
        // Laufenden Abruf abbrechen, sonst überholt ein alter Stand den neuen.
        abortRef.current?.abort();
        abortRef.current = new AbortController();
        const signal = abortRef.current.signal;

        try {
            const res = await fetch(summaryUrl(false), { signal });
            if (!res.ok || signal.aborted) return;
            const summary: NotificationSummary = await res.json();
            if (signal.aborted) return;
            const neu = Number.isFinite(summary?.totalCount) ? summary.totalCount : 0;

            // Wackeln, wenn die Zahl steigt – nicht beim allerersten Laden.
            if (neu > prevCountRef.current && prevCountRef.current > 0) {
                setShake(true);
                setTimeout(() => setShake(false), 600);
            }
            prevCountRef.current = neu;
            setTotal(neu);
        } catch (err) {
            if ((err as Error).name === 'AbortError') return;
            /* Die Glocke ist nur ein Hinweis – Netzfehler still übergehen, beim nächsten Abruf klappt es. */
        }
    }, []);

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
        const handleVisibility = () => {
            if (document.visibilityState === 'visible') fetchNotifications();
        };
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

    const beschriftung = total > 0 ? `Benachrichtigungen (${total} neu)` : 'Benachrichtigungen';

    return (
        <>
            <button
                type="button"
                onClick={() => navigate(BENACHRICHTIGUNGEN_PFAD)}
                className={cn(
                    'relative flex items-center justify-center w-10 h-10 rounded-xl transition-all',
                    'hover:bg-rose-50 active:scale-95 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-500',
                    aktiv && 'bg-rose-50 ring-1 ring-rose-200',
                    shake && 'animate-bell-shake',
                )}
                title={beschriftung}
                aria-label={beschriftung}
                aria-current={aktiv ? 'page' : undefined}
            >
                <Bell aria-hidden="true" className={cn('w-5 h-5 transition-colors', total > 0 || aktiv ? 'text-rose-600' : 'text-slate-400')} />

                {total > 0 && (
                    <span
                        aria-hidden="true"
                        className="absolute -top-0.5 -right-0.5 flex items-center justify-center min-w-[18px] h-[18px] px-1 text-[10px] font-bold text-white bg-rose-600 rounded-full ring-2 ring-white animate-in zoom-in-50 duration-200"
                    >
                        {total > 99 ? '99+' : total}
                    </span>
                )}
            </button>

            {/* Wackeln nur, wenn das Betriebssystem Bewegung nicht abgeschaltet hat. */}
            <style>{`
                @keyframes bell-shake {
                    0%, 100% { transform: rotate(0deg); }
                    15% { transform: rotate(12deg); }
                    30% { transform: rotate(-10deg); }
                    45% { transform: rotate(8deg); }
                    60% { transform: rotate(-6deg); }
                    75% { transform: rotate(3deg); }
                }
                @media (prefers-reduced-motion: no-preference) {
                    .animate-bell-shake {
                        animation: bell-shake 0.6s ease-in-out;
                    }
                }
            `}</style>
        </>
    );
}
