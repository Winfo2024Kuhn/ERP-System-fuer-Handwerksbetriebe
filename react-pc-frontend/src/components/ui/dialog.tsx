import * as React from "react"
import { createPortal } from "react-dom"
import { X } from "lucide-react"

import { cn } from "../../lib/utils"

const DialogDepth = React.createContext(0);
const focusSelector = 'button, a[href], input, select, textarea, [tabindex], [contenteditable="true"]';

function visible(element: HTMLElement): boolean {
    for (let node: HTMLElement | null = element; node; node = node.parentElement) {
        const style = getComputedStyle(node);
        if (node.hidden || node.hasAttribute('inert') || node.getAttribute('aria-hidden') === 'true'
            || style.display === 'none' || style.visibility === 'hidden') return false;
    }
    return true;
}
function topDialog(exclude?: HTMLElement): HTMLElement | undefined {
    let result: HTMLElement | undefined;
    let highest = -Infinity;
    for (const panel of document.querySelectorAll<HTMLElement>('[role="dialog"][aria-modal="true"]')) {
        if (panel === exclude || !visible(panel)) continue;
        let zIndex = 0;
        for (let node: HTMLElement | null = panel; node; node = node.parentElement) {
            zIndex = Math.max(zIndex, Number.parseInt(getComputedStyle(node).zIndex) || 0);
        }
        if (zIndex >= highest) { highest = zIndex; result = panel; }
    }
    return result;
}
function notificationRoots(): HTMLElement[] {
    return Array.from(document.querySelectorAll<HTMLElement>('[data-pc-toasts]')).filter(visible);
}
/** Owned picker portals are linked to their trigger by aria-controls. */
function focusRoots(panel: HTMLElement): HTMLElement[] {
    const roots = [panel];
    for (const trigger of panel.querySelectorAll<HTMLElement>('[aria-controls]')) {
        for (const id of (trigger.getAttribute('aria-controls') ?? '').split(/\s+/)) {
            const popup = document.getElementById(id);
            if (popup && !roots.includes(popup)) roots.push(popup);
        }
    }
    return [...roots, ...notificationRoots()];
}
function focusable(panel: HTMLElement): HTMLElement[] {
    const candidates = [panel, ...notificationRoots()].flatMap(root => [
        ...(root !== panel ? [root] : []), ...root.querySelectorAll<HTMLElement>(focusSelector),
    ]);
    return Array.from(new Set(candidates))
        .filter(element => element.tabIndex >= 0 && !element.matches(':disabled') && visible(element));
}

const Dialog = React.forwardRef<
    HTMLDivElement,
    React.HTMLAttributes<HTMLDivElement> & { open?: boolean; onOpenChange?: (open: boolean) => void }
>(({ className, open, onOpenChange, children, ...props }, ref) => {
    const depth = React.useContext(DialogDepth);
    const panelRef = React.useRef<HTMLDivElement | null>(null);
    const openerRef = React.useRef<HTMLElement | null>(null);
    const onChangeRef = React.useRef(onOpenChange);
    React.useLayoutEffect(() => { onChangeRef.current = onOpenChange; }, [onOpenChange]);
    // Capture during commit, before a child's autoFocus layout work runs.
    React.useInsertionEffect(() => {
        if (open) openerRef.current = document.activeElement as HTMLElement | null;
    }, [open]);

    React.useLayoutEffect(() => {
        const panel = panelRef.current;
        if (!open || !panel) return;
        const opener = openerRef.current;
        let lastFocus: HTMLElement | null = null;
        let lastDialogFocus: HTMLElement | null = null;
        const belongs = (element: Node | null) => !!element && focusRoots(panel).some(root => root.contains(element));
        const focusInside = () => {
            const target = lastFocus?.isConnected && belongs(lastFocus) && visible(lastFocus)
                ? lastFocus : lastDialogFocus?.isConnected && visible(lastDialogFocus) ? lastDialogFocus : focusable(panel)[0] ?? panel;
            target.focus();
        };
        const onFocus = (event: FocusEvent) => {
            if (topDialog() !== panel) return;
            if (belongs(event.target as Node)) {
                lastFocus = event.target as HTMLElement;
                if (!notificationRoots().some(root => root.contains(lastFocus))) lastDialogFocus = lastFocus;
            } else focusInside();
        };
        const onKey = (event: KeyboardEvent) => {
            if (event.defaultPrevented || topDialog() !== panel) return;
            if (event.key === 'Escape') {
                event.preventDefault(); event.stopPropagation();
                if (notificationRoots().some(root => root.contains(event.target as Node))) {
                    // Escape in a notification returns to the unfinished form;
                    // it must never close that form as a side effect.
                    const target = lastDialogFocus?.isConnected && visible(lastDialogFocus) ? lastDialogFocus : focusable(panel)[0] ?? panel;
                    target.focus();
                } else onChangeRef.current?.(false);
            } else if (event.key === 'Tab') {
                // DatePicker manages its own portal navigation and returns to its trigger.
                if (focusRoots(panel).slice(1).some(root => !root.hasAttribute('data-pc-toasts') && root.contains(event.target as Node))) return;
                const elements = focusable(panel);
                const first = elements[0] ?? panel;
                const last = elements.at(-1) ?? panel;
                const lastPanel = elements.filter(element => panel.contains(element)).at(-1);
                const firstNotice = elements.find(element => !panel.contains(element));
                // Notification DOM precedes the portal. Bridge that boundary
                // explicitly; ordinary navigation inside each region stays native.
                if (firstNotice && ((!event.shiftKey && document.activeElement === lastPanel)
                    || (event.shiftKey && document.activeElement === firstNotice))) {
                    event.preventDefault(); (event.shiftKey ? lastPanel ?? panel : firstNotice).focus(); return;
                }
                if (!belongs(document.activeElement) || document.activeElement === panel
                    || (event.shiftKey && document.activeElement === first)
                    || (!event.shiftKey && document.activeElement === last)) {
                    event.preventDefault(); (event.shiftKey ? last : first).focus();
                }
            }
        };
        document.addEventListener('focusin', onFocus);
        document.addEventListener('keydown', onKey);
        if (topDialog() === panel) {
            if (belongs(document.activeElement)) { lastFocus = document.activeElement as HTMLElement; lastDialogFocus = lastFocus; }
            else focusInside();
        }
        return () => {
            document.removeEventListener('focusin', onFocus);
            document.removeEventListener('keydown', onKey);
            // Wait until React has removed portals; never steal focus from a newer modal.
            queueMicrotask(() => {
                const top = topDialog(panel);
                if (opener?.isConnected && (!top || focusRoots(top).some(root => root.contains(opener)))) opener.focus();
            });
        };
    }, [open]);
    if (!open) return null;

    return createPortal(
        <>
            {/* Backdrop */}
            <div
                className="fixed inset-0 z-50 bg-black/50 backdrop-blur-sm"
                style={{ zIndex: 50 + depth }}
                // Kein Backdrop-Klick: Schließen über Dialogaktionen oder Escape.
            />
            {/* Dialog Container */}
            <div className="fixed inset-0 z-50 flex items-center justify-center p-4" style={{ zIndex: 50 + depth }}>
                <div
                    ref={element => {
                        panelRef.current = element;
                        if (typeof ref === 'function') ref(element);
                        else if (ref) ref.current = element;
                    }}
                    tabIndex={-1}
                    role="dialog"
                    aria-modal="true"
                    className={cn(
                        "relative z-50 bg-white p-6 shadow-2xl rounded-2xl border border-slate-200",
                        "transition-all duration-700 ease-[cubic-bezier(0.32,0.72,0,1)]",
                        "max-h-[calc(100vh-2rem)] flex flex-col",
                        className
                    )}
                    {...props}
                >
                    <DialogDepth.Provider value={depth + 1}>{children}</DialogDepth.Provider>
                    <button
                        type="button"
                        className="absolute right-4 top-4 p-1.5 rounded-full bg-slate-100 opacity-70 transition-all hover:opacity-100 hover:bg-rose-100 hover:text-rose-600 focus:outline-none focus:ring-2 focus:ring-rose-500"
                        onClick={() => onOpenChange?.(false)}
                    >
                        <X className="h-4 w-4" />
                        <span className="sr-only">Schließen</span>
                    </button>
                </div>
            </div>
        </>,
        document.body
    )
})
Dialog.displayName = "Dialog"

const DialogContent = React.forwardRef<
    HTMLDivElement,
    React.HTMLAttributes<HTMLDivElement>
>(({ className, ...props }, ref) => (
    <div ref={ref} className={cn("flex flex-col gap-4 w-full flex-1 min-h-0", className)} {...props} />
))
DialogContent.displayName = "DialogContent"

const DialogHeader = ({
    className,
    ...props
}: React.HTMLAttributes<HTMLDivElement>) => (
    <div
        className={cn(
            "flex flex-col space-y-1.5 text-center sm:text-left flex-shrink-0",
            className
        )}
        {...props}
    />
)
DialogHeader.displayName = "DialogHeader"

const DialogFooter = ({
    className,
    ...props
}: React.HTMLAttributes<HTMLDivElement>) => (
    <div
        className={cn(
            "flex flex-col-reverse sm:flex-row sm:justify-end sm:space-x-2 flex-shrink-0",
            className
        )}
        {...props}
    />
)
DialogFooter.displayName = "DialogFooter"

const DialogTitle = React.forwardRef<
    HTMLParagraphElement,
    React.HTMLAttributes<HTMLHeadingElement>
>(({ className, ...props }, ref) => (
    <h3
        ref={ref}
        className={cn(
            "text-lg font-semibold leading-none tracking-tight",
            className
        )}
        {...props}
    />
))
DialogTitle.displayName = "DialogTitle"

const DialogDescription = React.forwardRef<
    HTMLParagraphElement,
    React.HTMLAttributes<HTMLParagraphElement>
>(({ className, ...props }, ref) => (
    <p
        ref={ref}
        className={cn("text-sm text-slate-500", className)}
        {...props}
    />
))
DialogDescription.displayName = "DialogDescription"

export {
    Dialog,
    DialogContent,
    DialogHeader,
    DialogFooter,
    DialogTitle,
    DialogDescription,
}
