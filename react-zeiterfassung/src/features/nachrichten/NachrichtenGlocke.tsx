import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { AlertTriangle, Ban, Bell, BellOff, Check, CheckCheck, CheckCircle2, Loader2, XCircle } from 'lucide-react'
import { Drawer, DrawerContent, DrawerDescription, DrawerFooter, DrawerHeader, DrawerTitle } from '../../components/ui/drawer'
import { useToast } from '../../components/ui/toast'
import { cn } from '../../lib/utils'
import { useNachrichten, type Nachricht } from './useNachrichten'

const FEHLER_TEXT = 'Das konnte gerade nicht gespeichert werden. Bitte später noch einmal versuchen.'

interface NachrichtenGlockeProps {
    mitarbeiterId: number | null | undefined
    /** Ändert sich der Wert (z. B. Sync fertig), lädt die Glocke neu. */
    neuLadenAusloeser?: unknown
}

function NachrichtIcon({ nachricht }: { nachricht: Nachricht }) {
    if (nachricht.art === 'GENEHMIGT') {
        return <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-emerald-100"><CheckCircle2 aria-hidden="true" className="h-5 w-5 text-emerald-600" /></span>
    }
    if (nachricht.art === 'STORNIERT') {
        return <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-slate-100"><Ban aria-hidden="true" className="h-5 w-5 text-slate-600" /></span>
    }
    if (nachricht.art === 'ABGELEHNT') {
        return <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-rose-100"><XCircle aria-hidden="true" className="h-5 w-5 text-rose-600" /></span>
    }
    return <span className={cn('flex h-10 w-10 shrink-0 items-center justify-center rounded-xl', nachricht.dringend ? 'bg-rose-100' : 'bg-amber-100')}>
        <AlertTriangle aria-hidden="true" className={cn('h-5 w-5', nachricht.dringend ? 'text-rose-600' : 'text-amber-600')} />
    </span>
}

const TITEL_FARBE: Record<string, string> = {
    GENEHMIGT: 'text-emerald-700',
    ABGELEHNT: 'text-rose-700',
    STORNIERT: 'text-slate-600',
}

/**
 * Glocke in der Kopfzeile: zeigt die Zahl neuer Nachrichten und öffnet ein
 * Bottom-Sheet mit der Liste. „Gelesen" entfernt eine Nachricht; schlägt das
 * fehl, bleibt sie stehen und ein Fehler-Toast erklärt es.
 */
export function NachrichtenGlocke({ mitarbeiterId, neuLadenAusloeser }: NachrichtenGlockeProps) {
    const navigate = useNavigate()
    const toast = useToast()
    const { nachrichten, neuLaden, alleGelesen } = useNachrichten(mitarbeiterId, neuLadenAusloeser)
    const [offen, setOffen] = useState(false)
    const [laeuft, setLaeuft] = useState<string | null>(null)
    const anzahl = nachrichten.length

    const gelesen = async (nachricht: Nachricht) => {
        setLaeuft(nachricht.key)
        try {
            await nachricht.onGelesen()
        } catch {
            toast.error(FEHLER_TEXT)
        } finally {
            setLaeuft(null)
        }
    }

    const alleAlsGelesen = async () => {
        setLaeuft('alle')
        try {
            const { fehlgeschlagen } = await alleGelesen()
            if (fehlgeschlagen > 0) toast.error(FEHLER_TEXT)
        } catch {
            toast.error(FEHLER_TEXT)
        } finally {
            setLaeuft(null)
        }
    }

    const oeffnen = () => {
        setOffen(true)
        void neuLaden()
    }

    return (
        <>
            <button
                type="button"
                onClick={oeffnen}
                aria-haspopup="dialog"
                aria-expanded={offen}
                className="relative flex min-h-11 min-w-11 items-center justify-center rounded-lg transition-all hover:bg-slate-100 active:scale-95 focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-500"
            >
                <Bell aria-hidden="true" className="h-5 w-5 text-slate-600" />
                <span className="sr-only">Nachrichten</span>
                {anzahl > 0 && (
                    <>
                        <span
                            aria-hidden="true"
                            data-testid="nachrichten-zaehler"
                            className="absolute right-0.5 top-0.5 flex h-5 min-w-5 items-center justify-center rounded-full bg-rose-600 px-1 text-[11px] font-bold leading-none text-white ring-2 ring-white"
                        >
                            {anzahl > 9 ? '9+' : anzahl}
                        </span>
                        <span className="sr-only">, {anzahl} neu</span>
                    </>
                )}
            </button>

            <Drawer open={offen} onOpenChange={setOffen}>
                <DrawerContent>
                    <DrawerHeader>
                        <DrawerTitle>Nachrichten</DrawerTitle>
                        <DrawerDescription>
                            {anzahl === 0 ? 'Alles gelesen.' : anzahl === 1 ? '1 neue Nachricht' : `${anzahl} neue Nachrichten`}
                        </DrawerDescription>
                    </DrawerHeader>

                    <div className="min-h-0 flex-1 overflow-y-auto px-5 pb-2">
                        {anzahl === 0 ? (
                            <div className="flex flex-col items-center gap-2 py-10 text-center text-slate-500">
                                <BellOff aria-hidden="true" className="h-8 w-8 text-slate-300" />
                                <p>Keine neuen Nachrichten.</p>
                            </div>
                        ) : (
                            <ul aria-label="Nachrichten" className="space-y-2">
                                {nachrichten.map(nachricht => (
                                    <li key={nachricht.key} data-testid="nachricht" className="flex items-center gap-1 rounded-2xl border border-slate-200 bg-white p-2 shadow-sm">
                                        <button
                                            type="button"
                                            onClick={() => { setOffen(false); navigate(nachricht.ziel) }}
                                            className="flex min-h-11 min-w-0 flex-1 items-start gap-3 rounded-xl p-1 text-left hover:bg-slate-50 focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-500"
                                        >
                                            <NachrichtIcon nachricht={nachricht} />
                                            <span className="min-w-0 flex-1">
                                                <span className={cn('block text-xs font-semibold uppercase tracking-wide', TITEL_FARBE[nachricht.art] ?? (nachricht.dringend ? 'text-rose-700' : 'text-amber-700'))}>
                                                    {nachricht.titel}
                                                </span>
                                                <span className="mt-0.5 block text-sm font-medium leading-snug text-slate-900">{nachricht.text}</span>
                                                {nachricht.datum && <span className="mt-0.5 block text-xs text-slate-500">Entschieden am {nachricht.datum}</span>}
                                            </span>
                                        </button>
                                        <button
                                            type="button"
                                            aria-label={`Gelesen: ${nachricht.titel}`}
                                            onClick={() => gelesen(nachricht)}
                                            disabled={laeuft !== null}
                                            className="flex min-h-11 shrink-0 flex-col items-center justify-center gap-0.5 rounded-xl px-2 text-[11px] font-semibold text-slate-600 hover:bg-slate-100 disabled:opacity-60 focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-500"
                                        >
                                            {laeuft === nachricht.key
                                                ? <Loader2 aria-hidden="true" className="h-5 w-5 animate-spin" />
                                                : <Check aria-hidden="true" className="h-5 w-5" />}
                                            Gelesen
                                        </button>
                                    </li>
                                ))}
                            </ul>
                        )}
                    </div>

                    {anzahl > 1 && (
                        <DrawerFooter>
                            <button
                                type="button"
                                onClick={alleAlsGelesen}
                                disabled={laeuft !== null}
                                className="flex min-h-12 w-full items-center justify-center gap-2 rounded-xl border border-slate-300 bg-white px-4 text-sm font-semibold text-slate-700 hover:bg-slate-50 active:scale-[0.98] disabled:opacity-60"
                            >
                                {laeuft === 'alle' ? <Loader2 aria-hidden="true" className="h-4 w-4 animate-spin" /> : <CheckCheck aria-hidden="true" className="h-4 w-4" />}
                                Alle als gelesen markieren
                            </button>
                        </DrawerFooter>
                    )}
                </DrawerContent>
            </Drawer>
        </>
    )
}
