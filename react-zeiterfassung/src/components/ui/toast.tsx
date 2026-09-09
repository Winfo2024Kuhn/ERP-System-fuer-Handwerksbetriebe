/* eslint-disable react-refresh/only-export-components */
import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { createPortal } from 'react-dom'
import { AlertTriangle, CheckCircle, Info, X, XCircle } from 'lucide-react'

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
    return <Context.Provider value={api}>{children}{createPortal(
        <div data-mobile-toasts className="fixed left-3 right-3 z-[10030] flex max-h-[35dvh] flex-col gap-2 overflow-y-auto pointer-events-none" style={{ top: 'max(12px, env(safe-area-inset-top))' }}>
            {messages.map(({ id, kind, message }) => {
                const Icon = icons[kind]
                return <div key={id} role={kind === 'error' || kind === 'warning' ? 'alert' : 'status'} className={`pointer-events-auto flex items-start gap-3 rounded-xl border p-3 shadow-lg ${styles[kind]}`}>
                    <Icon aria-hidden="true" className="mt-3 h-5 w-5 shrink-0" />
                    <p className="min-w-0 flex-1 break-words py-2 text-sm">{message}</p>
                    <button type="button" aria-label="Meldung schließen" onClick={() => dismiss(id)} className="flex min-h-11 min-w-11 items-center justify-center rounded-lg focus:outline-none focus:ring-2 focus:ring-rose-500"><X aria-hidden="true" className="h-5 w-5" /></button>
                </div>
            })}
        </div>, document.body)}</Context.Provider>
}
