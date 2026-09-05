/* eslint-disable react-refresh/only-export-components */
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

/**
 * True, solange irgendwo ein offener Dialog (role="dialog") im DOM steht.
 *
 * Grund: der Toast-Container liegt fest unten rechts -- genau dort, wo so
 * gut wie jedes Modal im Projekt seine Fussleiste mit "Abbrechen"/"Speichern"
 * hat (siehe LieferantDokumentModal.tsx). Ein Fehler-Toast beim Oeffnen legt
 * sich dann fuenf Sekunden lang ueber genau die Knoepfe, die als naechstes
 * gebraucht werden -- auf 14 Zoll (1440x900) trifft ein Klick in die Mitte
 * beider Knoepfe den Toast statt den Knopf. Ausweichen statt den Fehler in
 * jedem einzelnen Modal einzeln zu umschiffen: bei offenem Dialog wandert der
 * GESAMTE Container nach unten LINKS (siehe Kommentar am Container unten fuer
 * die Begruendung, warum unten links und nicht oben rechts/oben links).
 */
function useIrgendeinDialogOffen(): boolean {
    const [offen, setOffen] = useState(
        () => typeof document !== 'undefined' && document.querySelector('[role="dialog"]') !== null
    );

    useEffect(() => {
        if (typeof document === 'undefined' || typeof MutationObserver === 'undefined') return;

        const aktualisieren = () => {
            setOffen(document.querySelector('[role="dialog"]') !== null);
        };
        aktualisieren();

        // Beobachtet das ganze Dokument, nicht nur einen bekannten Modal-Slot --
        // Modale werden hier ganz unterschiedlich eingehaengt (Portal, direkt im
        // Baum, ...). attributeFilter auf 'role' begrenzt, damit nicht jede
        // beliebige Attribut-Aenderung irgendwo im DOM einen Re-Check ausloest.
        const observer = new MutationObserver(aktualisieren);
        observer.observe(document.body, {
            childList: true,
            subtree: true,
            attributes: true,
            attributeFilter: ['role'],
        });

        return () => observer.disconnect();
    }, []);

    return offen;
}

// --- Provider ---
export function ToastProvider({ children }: { children: React.ReactNode }) {
    const [toasts, setToasts] = useState<Toast[]>([]);
    const idCounter = useRef(0);
    const dialogOffen = useIrgendeinDialogOffen();

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
            {/*
                Toast Container -- unten LINKS bei offenem Dialog, sonst unten
                rechts (siehe useIrgendeinDialogOffen).

                Design-Review-Nachbesserung (Task 8a): "oben rechts" (die
                6b-Loesung) reichte nicht -- dort sitzen bei praktisch jedem
                Modal der Schliessen-X-Knopf UND oft ein weiterer Kopf-Knopf
                (z.B. "Vorschau aktiv" in LieferantDokumentModal). Gemessen
                blieben zwischen einem einzeiligen Toast und dem X nur 4px
                Luft (Toast endet y=70, X beginnt y=74) -- ein zweizeiliger
                Toast (46 -> 86px) ueberdeckt X und "Vorschau aktiv" auf
                beiden Bildschirmgroessen. Die naechste Wahl war oben LINKS --
                die eine Ecke, die in keinem Modal im Projekt eine Aktion
                traegt.

                Design-Review-Nachbesserung 2 (Task 8c): "oben links" schnitt
                seinerseits auf 14 Zoll die Modal-Ueberschrift an. Gemessen
                endet ein zweizeiliger Toast [24,24,480,66] bei y=90, der
                Titel "Dokument bearbeiten" beginnt schon bei y=78 (12px
                Ueberlappung -- elementFromPoint auf Titel UND Eyebrow trifft
                dort den Toast statt den Text). Nach oben ausweichen geht auf
                14 Zoll nicht: das Modal beginnt bei y=57, ein 66px hoher
                Toast reicht selbst bei top-2 bis y=74. UNTEN LINKS traegt
                dagegen weder LieferantDokumentModal (Fussleiste rechts) noch
                der Confirm-Dialog (Knoepfe mittig/rechts) irgendeine Aktion --
                und die Ecke bleibt frei, unabhaengig davon, wie viele Zeilen
                der Toast-Text braucht.
            */}
            <div
                data-testid="toast-container"
                className={`fixed z-[9999] flex flex-col gap-2 pointer-events-auto ${
                    dialogOffen ? 'bottom-6 left-6 items-start' : 'bottom-6 right-6 items-end'
                }`}
            >
                {toasts.map(t => (
                    <ToastItem key={t.id} toast={t} onDismiss={dismiss} />
                ))}
            </div>
        </ToastContext.Provider>
    );
}
