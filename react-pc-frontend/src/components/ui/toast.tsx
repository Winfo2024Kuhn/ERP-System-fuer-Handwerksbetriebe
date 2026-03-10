import React, { createContext, useContext, useState, useCallback, useRef, useEffect } from 'react';
import { CheckCircle, XCircle, AlertTriangle, Info, X } from 'lucide-react';

// --- Types ---
type ToastType = 'success' | 'error' | 'warning' | 'info';

interface Toast {
    id: number;
    type: ToastType;
    message: string;
    duration: number;
}

interface ToastContextValue {
    toast: {
        success: (message: string, duration?: number) => void;
        error: (message: string, duration?: number) => void;
        warning: (message: string, duration?: number) => void;
        info: (message: string, duration?: number) => void;
    };
}

// --- Context ---
const ToastContext = createContext<ToastContextValue | null>(null);

// --- Hook ---
export function useToast() {
    const ctx = useContext(ToastContext);
    if (!ctx) throw new Error('useToast must be used within a ToastProvider');
    return ctx.toast;
}

// --- Icons ---
const iconMap: Record<ToastType, React.ReactNode> = {
    success: <CheckCircle className="w-5 h-5 text-emerald-500 shrink-0" />,
    error: <XCircle className="w-5 h-5 text-red-500 shrink-0" />,
    warning: <AlertTriangle className="w-5 h-5 text-amber-500 shrink-0" />,
    info: <Info className="w-5 h-5 text-sky-500 shrink-0" />,
};

const bgMap: Record<ToastType, string> = {
    success: 'border-emerald-200 bg-emerald-50',
    error: 'border-red-200 bg-red-50',
    warning: 'border-amber-200 bg-amber-50',
    info: 'border-sky-200 bg-sky-50',
};

const textMap: Record<ToastType, string> = {
    success: 'text-emerald-800',
    error: 'text-red-800',
    warning: 'text-amber-800',
    info: 'text-sky-800',
};

// --- Single Toast Item ---
function ToastItem({ toast, onDismiss }: { toast: Toast; onDismiss: (id: number) => void }) {
    const [visible, setVisible] = useState(false);
    const [exiting, setExiting] = useState(false);

    useEffect(() => {
        // Trigger enter animation
        requestAnimationFrame(() => setVisible(true));
        const timer = setTimeout(() => {
            setExiting(true);
            setTimeout(() => onDismiss(toast.id), 300);
        }, toast.duration);
        return () => clearTimeout(timer);
    }, [toast.duration, toast.id, onDismiss]);

    const handleDismiss = () => {
        setExiting(true);
        setTimeout(() => onDismiss(toast.id), 300);
    };

    return (
        <div
            className={`
                flex items-start gap-3 px-4 py-3 rounded-xl border shadow-lg backdrop-blur-sm
                transition-all duration-300 ease-out min-w-[320px] max-w-[480px]
                ${bgMap[toast.type]}
                ${visible && !exiting ? 'opacity-100 translate-y-0' : 'opacity-0 translate-y-2'}
            `}
        >
            {iconMap[toast.type]}
            <p className={`text-sm font-medium flex-1 ${textMap[toast.type]}`}>
                {toast.message}
            </p>
            <button
                onClick={handleDismiss}
                className="p-0.5 rounded-full hover:bg-black/5 transition-colors shrink-0"
            >
                <X className="w-3.5 h-3.5 text-slate-400" />
            </button>
        </div>
    );
}

// --- Provider ---
export function ToastProvider({ children }: { children: React.ReactNode }) {
    const [toasts, setToasts] = useState<Toast[]>([]);
    const idCounter = useRef(0);

    const dismiss = useCallback((id: number) => {
        setToasts(prev => prev.filter(t => t.id !== id));
    }, []);

    const addToast = useCallback((type: ToastType, message: string, duration = 4000) => {
        const id = ++idCounter.current;
        setToasts(prev => [...prev, { id, type, message, duration }]);
    }, []);

    const toast = {
        success: useCallback((msg: string, dur?: number) => addToast('success', msg, dur), [addToast]),
        error: useCallback((msg: string, dur?: number) => addToast('error', msg, dur ?? 5000), [addToast]),
        warning: useCallback((msg: string, dur?: number) => addToast('warning', msg, dur ?? 5000), [addToast]),
        info: useCallback((msg: string, dur?: number) => addToast('info', msg, dur), [addToast]),
    };

    return (
        <ToastContext.Provider value={{ toast }}>
            {children}
            {/* Toast Container */}
            <div className="fixed bottom-6 right-6 z-[9999] flex flex-col gap-2 items-end pointer-events-auto">
                {toasts.map(t => (
                    <ToastItem key={t.id} toast={t} onDismiss={dismiss} />
                ))}
            </div>
        </ToastContext.Provider>
    );
}
