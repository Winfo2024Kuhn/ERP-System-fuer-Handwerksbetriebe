import * as React from "react"
import { createPortal } from "react-dom"
import { X } from "lucide-react"

import { cn } from "../../lib/utils"

const Dialog = React.forwardRef<
    HTMLDivElement,
    React.HTMLAttributes<HTMLDivElement> & { open?: boolean; onOpenChange?: (open: boolean) => void }
>(({ className, open, onOpenChange, children, ...props }, ref) => {
    if (!open) return null;

    return createPortal(
        <>
            {/* Backdrop */}
            <div
                className="fixed inset-0 z-50 bg-black/50 backdrop-blur-sm"
                // Kein onClick - Dialog schließt NUR über X-Button
            />
            {/* Dialog Container */}
            <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
                <div
                    ref={ref}
                    className={cn(
                        "relative z-50 bg-white p-6 shadow-2xl rounded-2xl border border-slate-200",
                        "transition-all duration-700 ease-[cubic-bezier(0.32,0.72,0,1)]",
                        "max-h-[calc(100vh-2rem)] flex flex-col",
                        className
                    )}
                    {...props}
                >
                    {children}
                    <button
                        className="absolute right-4 top-4 p-1.5 rounded-full bg-slate-100 opacity-70 transition-all hover:opacity-100 hover:bg-rose-100 hover:text-rose-600 focus:outline-none focus:ring-2 focus:ring-rose-500"
                        onClick={() => onOpenChange?.(false)}
                    >
                        <X className="h-4 w-4" />
                        <span className="sr-only">Close</span>
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
