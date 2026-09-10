import { mobileOverlayStyle } from './toast'
/* eslint-disable react-refresh/only-export-components */
import { createContext, useCallback, useContext, useEffect, useId, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { createPortal } from 'react-dom'
import { AlertTriangle } from 'lucide-react'

export interface ConfirmOptions {
    title?: string
    message: string
    confirmLabel?: string
    cancelLabel?: string
    variant?: 'danger' | 'warning' | 'info' | 'fehlschlag'
}
const Context = createContext<((options: ConfirmOptions) => Promise<boolean>) | null>(null)
export function useConfirm() {
    const confirm = useContext(Context)
    if (!confirm) throw new Error('useConfirm benötigt ConfirmProvider')
    return confirm
}
export function ConfirmProvider({ children }: { children: ReactNode }) {
    const [options, setOptions] = useState<ConfirmOptions | null>(null)
    const pending = useRef<((value: boolean) => void) | null>(null)
    const previousFocus = useRef<HTMLElement | null>(null)
    const cancelRef = useRef<HTMLButtonElement>(null)
    const panelRef = useRef<HTMLDivElement>(null)
    const id = useId()
    const finish = useCallback((value: boolean) => {
        const resolve = pending.current
        pending.current = null
        setOptions(null)
        previousFocus.current?.focus()
        resolve?.(value)
    }, [])
    const confirm = useCallback((next: ConfirmOptions) => new Promise<boolean>(resolve => {
        pending.current?.(false)
        if (!pending.current) previousFocus.current = document.activeElement as HTMLElement
        pending.current = resolve
        setOptions(next)
    }), [])
    useEffect(() => () => { pending.current?.(false); pending.current = null; previousFocus.current?.focus() }, [])
    useEffect(() => {
        if (!options) return
        cancelRef.current?.focus()
        const keydown = (event: KeyboardEvent) => {
            if (event.key === 'Escape') { event.preventDefault(); finish(false) }
            if (event.key === 'Tab') {
                const nodes = Array.from(panelRef.current?.querySelectorAll<HTMLElement>('button:not(:disabled)') ?? [])
                const first = nodes[0], last = nodes.at(-1)
                if (event.shiftKey && (document.activeElement === first || !panelRef.current?.contains(document.activeElement))) { event.preventDefault(); last?.focus() }
                else if (!event.shiftKey && (document.activeElement === last || !panelRef.current?.contains(document.activeElement))) { event.preventDefault(); first?.focus() }
            }
        }
        document.addEventListener('keydown', keydown)
        return () => document.removeEventListener('keydown', keydown)
    }, [options, finish])
    return <Context.Provider value={confirm}>{children}{options && createPortal(
        <div style={mobileOverlayStyle} className="fixed inset-0 z-[10010] flex items-center justify-center bg-black/40 p-4 backdrop-blur-sm" onClick={event => { if (event.target === event.currentTarget) finish(false) }}>
            <div ref={panelRef} role="dialog" aria-modal="true" aria-labelledby={id} aria-describedby={`${id}-message`} className="max-h-full w-full max-w-md overflow-auto rounded-2xl border border-slate-200 bg-white p-5 shadow-2xl">
                <AlertTriangle aria-hidden="true" className="mb-3 h-7 w-7 text-rose-600" />
                <h2 id={id} className="text-lg font-semibold text-slate-900">{options.title ?? 'Bitte bestätigen'}</h2>
                <p id={`${id}-message`} className="mt-2 whitespace-pre-line break-words text-sm text-slate-600">{options.message}</p>
                <div className="mt-5 flex gap-3">
                    <button ref={cancelRef} type="button" onClick={() => finish(false)} className="min-h-12 flex-1 rounded-lg border border-slate-300 px-3 text-slate-700 focus:outline-none focus:ring-2 focus:ring-rose-500">{options.cancelLabel ?? 'Abbrechen'}</button>
                    <button type="button" onClick={() => finish(true)} className="min-h-12 flex-1 rounded-lg bg-rose-600 px-3 text-white hover:bg-rose-700 focus:outline-none focus:ring-2 focus:ring-rose-500">{options.confirmLabel ?? 'Bestätigen'}</button>
                </div>
            </div>
        </div>, document.body)}</Context.Provider>
}
