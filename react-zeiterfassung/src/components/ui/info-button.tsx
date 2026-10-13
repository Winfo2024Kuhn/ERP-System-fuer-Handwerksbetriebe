import { useState, type ReactNode } from 'react'
import { Info } from 'lucide-react'
import { cn } from '../../lib/utils'
import { Drawer, DrawerContent, DrawerDescription, DrawerHeader, DrawerTitle } from './drawer'

export interface InfoButtonProps {
    /** Worum es geht – Überschrift im Sheet und Teil des Knopf-Namens („Mehr zu: …"). */
    titel: string
    children: ReactNode
    className?: string
}

/**
 * Kleines (i) neben einem Feld oder Knopf, das auf Antippen erklärt, was im
 * Hintergrund passiert und was eine Aktion bewirkt – das Handy-Gegenstück zum
 * InfoButton der PC-App.
 *
 * Am Handy öffnet sich statt eines Popovers ein Bottom-Sheet (unser `Drawer`):
 * groß genug für den Daumen, Escape und Wischen nach unten schließen es, der
 * Fokus kehrt zum (i) zurück. Die Erklärung steht in kurzen Sätzen in
 * Handwerker-Sprache.
 */
export function InfoButton({ titel, children, className }: InfoButtonProps) {
    const [offen, setOffen] = useState(false)
    return (
        <>
            <button
                type="button"
                aria-label={`Mehr zu: ${titel}`}
                aria-haspopup="dialog"
                aria-expanded={offen}
                onClick={() => setOffen(true)}
                className={cn(
                    'inline-flex h-8 w-8 shrink-0 items-center justify-center rounded-full align-middle text-slate-400 transition-colors',
                    'hover:bg-rose-50 hover:text-rose-600 active:bg-rose-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-500',
                    offen && 'bg-rose-50 text-rose-600',
                    className,
                )}
            >
                <Info aria-hidden="true" className="h-4 w-4" />
            </button>
            <Drawer open={offen} onOpenChange={setOffen}>
                <DrawerContent aria-describedby={undefined}>
                    <DrawerHeader>
                        <DrawerTitle className="flex items-center gap-2 text-base">
                            <Info aria-hidden="true" className="h-4 w-4 shrink-0 text-rose-600" />
                            {titel}
                        </DrawerTitle>
                        <DrawerDescription className="sr-only">Erklärung zu {titel}</DrawerDescription>
                    </DrawerHeader>
                    <div className="space-y-3 px-5 pb-6 text-sm text-slate-600">{children}</div>
                </DrawerContent>
            </Drawer>
        </>
    )
}

/** Ein Absatz im Info-Sheet, z. B. „Was passiert im Hintergrund?" oder „Was bewirkt das?". */
export function InfoAbschnitt({ titel, children }: { titel: string; children: ReactNode }) {
    return (
        <div>
            <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">{titel}</p>
            <div className="mt-1 space-y-1">{children}</div>
        </div>
    )
}
