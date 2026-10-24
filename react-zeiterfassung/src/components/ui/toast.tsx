/* eslint-disable react-refresh/only-export-components */
import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { AlertTriangle, CheckCircle, Info, X, XCircle } from 'lucide-react'
import { createPortal } from 'react-dom'

type Kind = 'success' | 'error' | 'warning' | 'info'
type ToastApi = Record<Kind, (message: string, duration?: number) => void>
const Context = createContext<ToastApi | null>(null)
const icons = { success: CheckCircle, error: XCircle, warning: AlertTriangle, info: Info }
const styles = { success: 'bg-emerald-50 border-emerald-200 text-emerald-900', error: 'bg-rose-50 border-rose-200 text-rose-900', warning: 'bg-amber-50 border-amber-200 text-amber-900', info: 'bg-white border-slate-200 text-slate-900' }

/**
 * Wie lange eine Meldung von selbst stehen bleibt (ms). Erfolg und Info
 * verschwinden nach 4 s, Warnung und Fehler bleiben, bis man sie per X
 * schließt – sonst liest sie auf der Baustelle niemand zu Ende.
 */
const STANDARD_DAUER: Record<Kind, number> = { success: 4000, info: 4000, warning: 0, error: 0 }
/** Höchstens so viele Meldungen schweben gleichzeitig; ältere kommen nach, sobald eine geschlossen wird. */
const MAX_SICHTBAR = 3
/** Dauer der Ausblende-Bewegung, muss zu `.mobile-toast` in index.css passen. */
const AUSBLENDEN_MS = 180

interface Meldung { id: number; kind: Kind; message: string; geht: boolean }

export function useToast() {
    const value = useContext(Context)
    if (!value) throw new Error('useToast benötigt ToastProvider')
    return value
}

function wenigerBewegung() {
    return typeof window !== 'undefined' && !!window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
}

/**
 * Meldungen der Handy-App als schwebende Ebene oben über dem Inhalt.
 *
 * Die Ebene ist `fixed` und nimmt keinen Platz ein: nichts auf der Seite und
 * in Dialogen verschiebt sich, wenn eine Meldung kommt oder geht. Der
 * Container selbst lässt Tipps durch (`pointer-events-none`), nur die Karten
 * und ihr X fangen sie ab. Oben hält die Ebene den Safe-Area-Abstand ein,
 * unten gibt es bewusst keinen.
 */
export function ToastProvider({ children }: { children: ReactNode }) {
    const [messages, setMessagesState] = useState<Meldung[]>([])
    // Spiegel des aktuellen Stands, damit `add` doppelte Meldungen erkennt, ohne im State-Updater Timer zu setzen.
    const aktuell = useRef<Meldung[]>([])
    const setMessages = useCallback((aendern: (alt: Meldung[]) => Meldung[]) => {
        aktuell.current = aendern(aktuell.current)
        setMessagesState(aktuell.current)
    }, [])
    const nextId = useRef(0)
    const timers = useRef(new Map<number, ReturnType<typeof setTimeout>>())
    const entfernen = useCallback((id: number) => {
        clearTimeout(timers.current.get(id))
        timers.current.delete(id)
        setMessages(current => current.filter(message => message.id !== id))
    }, [setMessages])
    const dismiss = useCallback((id: number) => {
        clearTimeout(timers.current.get(id))
        if (wenigerBewegung()) { entfernen(id); return }
        setMessages(current => current.map(message => message.id === id ? { ...message, geht: true } : message))
        timers.current.set(id, setTimeout(() => entfernen(id), AUSBLENDEN_MS))
    }, [entfernen, setMessages])
    useEffect(() => {
        const pending = timers.current
        return () => { pending.forEach(clearTimeout); pending.clear() }
    }, [])
    const zeigen = useCallback((kind: Kind, message: string, duration = STANDARD_DAUER[kind]) => {
        // Gleiche Meldung steht schon da (z. B. zweimal derselbe Fehler): nicht stapeln,
        // nur ihre Laufzeit neu starten.
        const vorhanden = aktuell.current.find(m => !m.geht && m.kind === kind && m.message === message)
        const id = vorhanden?.id ?? ++nextId.current
        if (vorhanden) clearTimeout(timers.current.get(id))
        else setMessages(current => [...current, { id, kind, message, geht: false }])
        if (duration > 0) timers.current.set(id, setTimeout(() => dismiss(id), duration))
    }, [dismiss, setMessages])
    const api = useMemo<ToastApi>(() => ({
        success: (message, duration) => zeigen('success', message, duration),
        error: (message, duration) => zeigen('error', message, duration),
        warning: (message, duration) => zeigen('warning', message, duration),
        info: (message, duration) => zeigen('info', message, duration),
    }), [zeigen])
    const sichtbar = messages.slice(-MAX_SICHTBAR)
    const verborgen = messages.length - sichtbar.length
    // Per Portal an <body>, nicht in #root: #root ist fest am Fenster verankert und
    // bildet damit eine eigene Stapelebene. Darin laege die Meldungsflaeche immer
    // unter Sheets und Dialogen, die selbst per Portal an <body> haengen.
    return <Context.Provider value={api}>
        {createPortal(<div data-mobile-toasts role="region" aria-label="Meldungen" hidden={messages.length === 0}
            style={{ top: 'calc(var(--sa-base-top, 2.5rem) + var(--sat, 0px))' }}
            className={messages.length ? 'pointer-events-none fixed inset-x-3 z-[10030] mx-auto flex max-w-md flex-col gap-2' : 'hidden'}>
            {verborgen > 0 && <p className="pointer-events-none self-center rounded-full bg-slate-900/80 px-3 py-1 text-xs font-medium text-white shadow">
                {verborgen === 1 ? '1 weitere Meldung' : `${verborgen} weitere Meldungen`}
            </p>}
            {sichtbar.map(({ id, kind, message, geht }) => {
                const Icon = icons[kind]
                return <div key={id} role={kind === 'error' || kind === 'warning' ? 'alert' : 'status'} data-geht={geht || undefined}
                    className={`mobile-toast pointer-events-auto flex items-center gap-2 rounded-2xl border py-1 pl-3 pr-1 shadow-xl shadow-slate-900/10 ${styles[kind]}`}>
                    <Icon aria-hidden="true" className="h-5 w-5 shrink-0" />
                    <p className="min-w-0 flex-1 break-words py-2 text-sm font-medium leading-snug">{message}</p>
                    <button type="button" aria-label="Meldung schließen" onClick={() => dismiss(id)} className="flex min-h-11 min-w-11 shrink-0 items-center justify-center rounded-xl hover:bg-black/5 focus:outline-none focus:ring-2 focus:ring-rose-500"><X aria-hidden="true" className="h-5 w-5" /></button>
                </div>
            })}
        </div>, document.body)}
        <div className="h-full min-h-0 overflow-auto">{children}</div>
    </Context.Provider>
}
