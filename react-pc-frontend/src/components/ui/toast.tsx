/* eslint-disable react-refresh/only-export-components */
import { createContext, useContext, useState, useCallback, useRef, useEffect, useLayoutEffect, useMemo } from 'react';
import type { ReactNode } from 'react';
import { CheckCircle, XCircle, AlertTriangle, Info, X } from 'lucide-react';

type ToastType = 'success' | 'error' | 'warning' | 'info';
type ToastApi = Record<ToastType, (message: string, duration?: number) => void>;
interface Toast { id: number; type: ToastType; message: string }
const ToastContext = createContext<ToastApi | null>(null);

export function useToast() {
    const toast = useContext(ToastContext);
    if (!toast) throw new Error('useToast must be used within a ToastProvider');
    return toast;
}

const icons = { success: CheckCircle, error: XCircle, warning: AlertTriangle, info: Info };
const colors = {
    success: 'border-emerald-200 bg-emerald-50 text-emerald-800',
    error: 'border-rose-200 bg-rose-50 text-rose-800',
    warning: 'border-amber-200 bg-amber-50 text-amber-800',
    info: 'border-slate-200 bg-slate-50 text-slate-800',
};

/** Floating notifications do not change application or dialog geometry. */
export function ToastProvider({ children }: { children: ReactNode }) {
    const [messages, setMessages] = useState<Toast[]>([]);
    const noticeRef = useRef<HTMLDivElement>(null);
    const nextId = useRef(0);
    const timers = useRef(new Map<number, ReturnType<typeof setTimeout>>());

    useLayoutEffect(() => {
        const panel = noticeRef.current;
        if (panel) panel.scrollTop = panel.scrollHeight;
    }, [messages.length]);

    const dismiss = useCallback((id: number) => {
        clearTimeout(timers.current.get(id));
        timers.current.delete(id);
        setMessages(current => current.filter(message => message.id !== id));
    }, []);

    useEffect(() => {
        const pending = timers.current;
        return () => { pending.forEach(clearTimeout); pending.clear(); };
    }, []);

    // Stabile Referenz: Meldungen dürfen Effekte der Verbraucher nicht erneut
    // auslösen und dabei deren ungespeicherte Formularentwürfe überschreiben.
    const api = useMemo<ToastApi>(() => {
        const add = (type: ToastType, fallback: number) => (message: string, duration = fallback) => {
            const id = ++nextId.current;
            setMessages(current => [...current, { id, type, message }]);
            timers.current.set(id, setTimeout(() => dismiss(id), Math.max(0, duration)));
        };
        return { success: add('success', 4000), error: add('error', 5000), warning: add('warning', 5000), info: add('info', 4000) };
    }, [dismiss]);

    return <ToastContext.Provider value={api}>
        <div ref={noticeRef} data-testid="toast-container" data-pc-toasts role="region" aria-label="Meldungen" tabIndex={messages.length ? 0 : -1}
            hidden={!messages.length}
            className={messages.length ? 'pointer-events-none fixed right-4 top-4 z-[10010] max-h-[min(25dvh,12rem)] w-[min(24rem,calc(100vw-2rem))] overflow-y-auto overscroll-contain rounded-xl focus:outline-none focus:ring-2 focus:ring-inset focus:ring-rose-500' : 'hidden'}>
            <div className="flex flex-col gap-2">
                {messages.map(({ id, type, message }) => {
                    const Icon = icons[type];
                    return <div key={id} role={type === 'error' || type === 'warning' ? 'alert' : 'status'} className={`pointer-events-auto flex items-start gap-3 rounded-xl border px-4 py-3 shadow-lg ${colors[type]}`}>
                        <Icon aria-hidden="true" className="mt-0.5 h-5 w-5 shrink-0" />
                        <p className="min-w-0 flex-1 break-words text-sm font-medium">{message}</p>
                        <button type="button" aria-label="Meldung schließen" onClick={() => dismiss(id)} className="shrink-0 rounded-md p-1 hover:bg-black/5 focus:outline-none focus:ring-2 focus:ring-rose-500">
                            <X aria-hidden="true" className="h-4 w-4" />
                        </button>
                    </div>;
                })}
            </div>
        </div>
        {children}
    </ToastContext.Provider>;
}
