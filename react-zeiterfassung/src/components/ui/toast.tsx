/* eslint-disable react-refresh/only-export-components */
import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, useLayoutEffect } from 'react'
import type { ReactNode, CSSProperties } from 'react'
import { AlertTriangle, CheckCircle, Info, X, XCircle } from 'lucide-react'

/** Eigene Vollbild-Overlays halten die reservierte Meldungsfläche frei. */
export const mobileOverlayStyle: CSSProperties = { top: 'var(--mobile-toast-height, 0px)' }

type Kind = 'success' | 'error' | 'warning' | 'info'
type ToastApi = Record<Kind, (message: string, duration?: number) => void>
const Context = createContext<ToastApi | null>(null)
const icons = { success: CheckCircle, error: XCircle, warning: AlertTriangle, info: Info }
const styles = { success: 'bg-emerald-50 border-emerald-200 text-emerald-900', error: 'bg-rose-50 border-rose-200 text-rose-900', warning: 'bg-amber-50 border-amber-200 text-amber-900', info: 'bg-slate-50 border-slate-200 text-slate-900' }

export function useToast() {
    const value = useContext(Context)
    if (!value) throw new Error('useToast benötigt ToastProvider')
    return value
}

export function ToastProvider({ children }: { children: ReactNode }) {
    const [messages, setMessages] = useState<{ id: number; kind: Kind; message: string }[]>([])
    const noticeRef = useRef<HTMLDivElement>(null)
    useLayoutEffect(() => {
        const style = document.documentElement.style
        const previous = style.getPropertyValue('--mobile-toast-height')
        return () => {
            if (previous) style.setProperty('--mobile-toast-height', previous)
            else style.removeProperty('--mobile-toast-height')
        }
    }, [])
    useLayoutEffect(() => {
        const measure = () => document.documentElement.style.setProperty('--mobile-toast-height', `${messages.length ? noticeRef.current?.getBoundingClientRect().height ?? 0 : 0}px`)
        measure()
        const observer = typeof ResizeObserver === 'undefined' ? null : new ResizeObserver(measure)
        if (noticeRef.current) observer?.observe(noticeRef.current)
        window.addEventListener('resize', measure)
        return () => { observer?.disconnect(); window.removeEventListener('resize', measure) }
    }, [messages.length])
    const nextId = useRef(0)
    const timers = useRef(new Map<number, ReturnType<typeof setTimeout>>())
    const dismiss = useCallback((id: number) => {
        clearTimeout(timers.current.get(id))
        timers.current.delete(id)
        setMessages(current => current.filter(message => message.id !== id))
    }, [])
    useEffect(() => {
        const pending = timers.current
        return () => { pending.forEach(clearTimeout); pending.clear() }
    }, [])
    const api = useMemo<ToastApi>(() => {
        const add = (kind: Kind) => (message: string, duration = 8000) => {
            const id = ++nextId.current
            setMessages(current => [...current, { id, kind, message }])
            if (duration > 0) timers.current.set(id, setTimeout(() => dismiss(id), duration))
        }
        return { success: add('success'), error: add('error'), warning: add('warning'), info: add('info') }
    }, [dismiss])
    return <Context.Provider value={api}><div className="flex h-full min-h-0 flex-col">
        <div ref={noticeRef} data-mobile-toasts hidden={messages.length === 0} className={messages.length ? "relative z-[10030] flex max-h-[30dvh] shrink-0 flex-col gap-2 overflow-y-auto bg-slate-50 p-3" : "hidden"}>
            {messages.map(({ id, kind, message }) => {
                const Icon = icons[kind]
                return <div key={id} role={kind === 'error' || kind === 'warning' ? 'alert' : 'status'} className={`pointer-events-auto flex items-start gap-3 rounded-xl border p-3 shadow-lg ${styles[kind]}`}>
                    <Icon aria-hidden="true" className="mt-3 h-5 w-5 shrink-0" />
                    <p className="min-w-0 flex-1 break-words py-2 text-sm">{message}</p>
                    <button type="button" aria-label="Meldung schließen" onClick={() => dismiss(id)} className="flex min-h-11 min-w-11 items-center justify-center rounded-lg focus:outline-none focus:ring-2 focus:ring-rose-500"><X aria-hidden="true" className="h-5 w-5" /></button>
                </div>
            })}
        </div><div className="min-h-0 flex-1 overflow-auto">{children}</div></div></Context.Provider>
}
