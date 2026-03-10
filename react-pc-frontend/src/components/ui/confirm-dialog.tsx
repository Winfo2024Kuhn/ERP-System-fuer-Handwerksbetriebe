import React, { createContext, useContext, useState, useCallback, useRef } from 'react';
import { AlertTriangle, Trash2, HelpCircle } from 'lucide-react';

// --- Types ---
type ConfirmVariant = 'danger' | 'warning' | 'info';

interface ConfirmOptions {
    title?: string;
    message: string;
    confirmLabel?: string;
    cancelLabel?: string;
    variant?: ConfirmVariant;
}

interface ConfirmDialogState extends ConfirmOptions {
    open: boolean;
}

interface ConfirmContextValue {
    confirm: (options: ConfirmOptions) => Promise<boolean>;
}

// --- Context ---
const ConfirmContext = createContext<ConfirmContextValue | null>(null);

// --- Hook ---
export function useConfirm() {
    const ctx = useContext(ConfirmContext);
    if (!ctx) throw new Error('useConfirm must be used within a ConfirmProvider');
    return ctx.confirm;
}

// --- Styling maps ---
const iconMap: Record<ConfirmVariant, React.ReactNode> = {
    danger: (
        <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-red-100">
            <Trash2 className="h-6 w-6 text-red-600" />
        </div>
    ),
    warning: (
        <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-amber-100">
            <AlertTriangle className="h-6 w-6 text-amber-600" />
        </div>
    ),
    info: (
        <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-sky-100">
            <HelpCircle className="h-6 w-6 text-sky-600" />
        </div>
    ),
};

const confirmBtnMap: Record<ConfirmVariant, string> = {
    danger: 'bg-red-600 hover:bg-red-700 focus:ring-red-500 text-white',
    warning: 'bg-amber-500 hover:bg-amber-600 focus:ring-amber-400 text-white',
    info: 'bg-rose-600 hover:bg-rose-700 focus:ring-rose-500 text-white',
};

// --- Provider ---
export function ConfirmProvider({ children }: { children: React.ReactNode }) {
    const [dialog, setDialog] = useState<ConfirmDialogState>({
        open: false,
        message: '',
    });

    const resolveRef = useRef<((value: boolean) => void) | null>(null);

    const confirm = useCallback((options: ConfirmOptions): Promise<boolean> => {
        return new Promise(resolve => {
            resolveRef.current = resolve;
            setDialog({ open: true, ...options });
        });
    }, []);

    const handleConfirm = () => {
        setDialog(prev => ({ ...prev, open: false }));
        resolveRef.current?.(true);
        resolveRef.current = null;
    };

    const handleCancel = () => {
        setDialog(prev => ({ ...prev, open: false }));
        resolveRef.current?.(false);
        resolveRef.current = null;
    };

    const variant = dialog.variant || 'danger';

    return (
        <ConfirmContext.Provider value={{ confirm }}>
            {children}

            {dialog.open && (
                <>
                    {/* Backdrop */}
                    <div
                        className="fixed inset-0 z-[10000] bg-black/40 backdrop-blur-sm transition-opacity"
                        onClick={handleCancel}
                    />
                    {/* Dialog */}
                    <div className="fixed inset-0 z-[10001] flex items-center justify-center p-4">
                        <div className="bg-white rounded-2xl shadow-2xl border border-slate-200 w-full max-w-md p-6 animate-in fade-in zoom-in-95 duration-200">
                            <div className="flex flex-col items-center text-center gap-4">
                                {iconMap[variant]}

                                <div>
                                    {dialog.title && (
                                        <h3 className="text-lg font-semibold text-slate-900 mb-1">
                                            {dialog.title}
                                        </h3>
                                    )}
                                    <p className="text-sm text-slate-600 whitespace-pre-line">
                                        {dialog.message}
                                    </p>
                                </div>

                                <div className="flex gap-3 w-full mt-2">
                                    <button
                                        onClick={handleCancel}
                                        className="flex-1 px-4 py-2.5 text-sm font-medium rounded-xl border border-slate-200 text-slate-700 bg-white hover:bg-slate-50 transition-colors focus:outline-none focus:ring-2 focus:ring-slate-300"
                                    >
                                        {dialog.cancelLabel || 'Abbrechen'}
                                    </button>
                                    <button
                                        onClick={handleConfirm}
                                        autoFocus
                                        className={`flex-1 px-4 py-2.5 text-sm font-medium rounded-xl transition-colors focus:outline-none focus:ring-2 ${confirmBtnMap[variant]}`}
                                    >
                                        {dialog.confirmLabel || 'Bestätigen'}
                                    </button>
                                </div>
                            </div>
                        </div>
                    </div>
                </>
            )}
        </ConfirmContext.Provider>
    );
}
