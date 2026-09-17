import type { ComponentProps } from 'react'
import { Drawer as DrawerPrimitive } from 'vaul'
import { cn } from '../../lib/utils'
import { mobileOverlayStyle } from './toast'

/**
 * Bottom-Sheet auf Basis von vaul – der shadcn-Baustein „drawer“, über den
 * shadcn-MCP bezogen und auf unser Design-System umgestellt: weiße Fläche,
 * rounded-t-3xl, Griff oben, Scrim mit Weichzeichner. Lässt sich mit dem
 * Finger nach unten zuziehen und respektiert die reservierte Meldungsfläche
 * der Toasts (mobileOverlayStyle).
 */
function Drawer(props: ComponentProps<typeof DrawerPrimitive.Root>) {
    return <DrawerPrimitive.Root data-slot="drawer" {...props} />
}

function DrawerTrigger(props: ComponentProps<typeof DrawerPrimitive.Trigger>) {
    return <DrawerPrimitive.Trigger data-slot="drawer-trigger" {...props} />
}

function DrawerPortal(props: ComponentProps<typeof DrawerPrimitive.Portal>) {
    return <DrawerPrimitive.Portal data-slot="drawer-portal" {...props} />
}

function DrawerClose(props: ComponentProps<typeof DrawerPrimitive.Close>) {
    return <DrawerPrimitive.Close data-slot="drawer-close" {...props} />
}

function DrawerOverlay({ className, ...props }: ComponentProps<typeof DrawerPrimitive.Overlay>) {
    return (
        <DrawerPrimitive.Overlay
            data-slot="drawer-overlay"
            style={mobileOverlayStyle}
            className={cn('fixed inset-0 z-[10000] bg-black/40 backdrop-blur-sm', className)}
            {...props}
        />
    )
}

function DrawerContent({ className, children, style, ...props }: ComponentProps<typeof DrawerPrimitive.Content>) {
    return (
        <DrawerPortal>
            <DrawerOverlay />
            <DrawerPrimitive.Content
                data-slot="drawer-content"
                // Die Meldungsfläche der Toasts (oben, bis 30dvh) darf das Sheet nie überdecken:
                // die Höhe wird um die zentral gemessene Toast-Höhe gekürzt. Eigene Styles des
                // Aufrufers kommen dazu, ersetzen diese Absicherung aber nicht.
                style={{ maxHeight: 'calc(88dvh - var(--mobile-toast-height, 0px))', ...style }}
                className={cn(
                    'fixed inset-x-0 bottom-0 z-[10001] flex h-auto flex-col rounded-t-3xl bg-white shadow-2xl outline-none',
                    className,
                )}
                {...props}
            >
                <div aria-hidden="true" className="mx-auto mt-3 h-1.5 w-10 shrink-0 rounded-full bg-slate-300" />
                {children}
            </DrawerPrimitive.Content>
        </DrawerPortal>
    )
}

function DrawerHeader({ className, ...props }: ComponentProps<'div'>) {
    return <div data-slot="drawer-header" className={cn('flex flex-col gap-1 px-5 pt-4 pb-3', className)} {...props} />
}

function DrawerFooter({ className, ...props }: ComponentProps<'div'>) {
    return <div data-slot="drawer-footer" className={cn('mt-auto flex flex-col gap-2 px-5 py-4', className)} {...props} />
}

function DrawerTitle({ className, ...props }: ComponentProps<typeof DrawerPrimitive.Title>) {
    return <DrawerPrimitive.Title data-slot="drawer-title" className={cn('text-lg font-bold text-slate-900', className)} {...props} />
}

function DrawerDescription({ className, ...props }: ComponentProps<typeof DrawerPrimitive.Description>) {
    return <DrawerPrimitive.Description data-slot="drawer-description" className={cn('text-sm text-slate-500', className)} {...props} />
}

export {
    Drawer,
    DrawerPortal,
    DrawerOverlay,
    DrawerTrigger,
    DrawerClose,
    DrawerContent,
    DrawerHeader,
    DrawerFooter,
    DrawerTitle,
    DrawerDescription,
}
