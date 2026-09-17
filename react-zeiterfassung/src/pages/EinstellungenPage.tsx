import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ArrowLeft, Bell, Camera, ChevronDown, Loader2, Mic } from 'lucide-react'
import { useToast } from '../components/ui/toast'
import {
    leseBenachrichtigungsStatus,
    leseKameraStatus,
    leseMikrofonStatus,
    frageKameraAn,
    frageMikrofonAn,
    type BerechtigungsStatus,
} from '../services/permissionStatusService'
import { frageBenachrichtigungenAn } from '../services/notificationBootstrap'

/**
 * Was das Geraet darf — Benachrichtigungen, Mikrofon, Kamera an einer Stelle.
 *
 * <p>Eine einmal abgelehnte Berechtigung kann KEINE Webseite zuruecksetzen,
 * das ist Browser-Sicherheit. Deshalb zeigt die Seite bei "Blockiert" keinen
 * weiteren Knopf, der ins Leere liefe, sondern den Weg durch die
 * Systemeinstellungen.
 */

type Bereich = 'benachrichtigungen' | 'mikrofon' | 'kamera'

const ABZEICHEN: Record<BerechtigungsStatus, { text: string; klassen: string }> = {
    'erlaubt': { text: 'Erlaubt', klassen: 'bg-emerald-50 text-emerald-700 border-emerald-200' },
    'nicht-gefragt': { text: 'Noch nicht gefragt', klassen: 'bg-slate-50 text-slate-600 border-slate-200' },
    'blockiert': { text: 'Blockiert', klassen: 'bg-rose-50 text-rose-700 border-rose-200' },
    'nicht-verfuegbar': { text: 'Gerät kann das nicht', klassen: 'bg-slate-50 text-slate-500 border-slate-200' },
}

const FEHLERTEXT: Record<Bereich, string> = {
    benachrichtigungen: 'Benachrichtigungen wurden nicht erlaubt.',
    mikrofon: 'Das Mikrofon wurde nicht erlaubt.',
    kamera: 'Die Kamera wurde nicht erlaubt.',
}

export default function EinstellungenPage() {
    const navigate = useNavigate()
    const toast = useToast()
    const [status, setStatus] = useState<Record<Bereich, BerechtigungsStatus> | null>(null)
    const [laeuft, setLaeuft] = useState<Bereich | null>(null)
    const [hilfeOffen, setHilfeOffen] = useState<Bereich | null>(null)

    const ladeStatus = useCallback(async () => {
        const [mikrofon, kamera] = await Promise.all([leseMikrofonStatus(), leseKameraStatus()])
        setStatus({ benachrichtigungen: leseBenachrichtigungsStatus(), mikrofon, kamera })
    }, [])

    useEffect(() => { void ladeStatus() }, [ladeStatus])

    const frageAn = useCallback(async (bereich: Bereich) => {
        setLaeuft(bereich)
        try {
            if (bereich === 'benachrichtigungen') {
                const token = localStorage.getItem('zeiterfassung_token')
                if (!token) {
                    toast.error('Nicht angemeldet. Bitte den QR-Code neu scannen.')
                    return
                }
                const erlaubt = await frageBenachrichtigungenAn(token)
                if (!erlaubt) {
                    toast.error(FEHLERTEXT.benachrichtigungen)
                    return
                }
            } else if (bereich === 'mikrofon') {
                await frageMikrofonAn()
            } else {
                await frageKameraAn()
            }
            toast.success('Erlaubnis erteilt.')
        } catch {
            toast.error(FEHLERTEXT[bereich])
        } finally {
            setLaeuft(null)
            // Immer neu lesen: der Nutzer kann im Systemdialog auch abgelehnt haben.
            await ladeStatus()
        }
    }, [toast, ladeStatus])

    const zeilen: { bereich: Bereich; titel: string; erklaerung: string; Icon: typeof Bell }[] = [
        { bereich: 'benachrichtigungen', titel: 'Benachrichtigungen', erklaerung: 'Damit du Termine und Freigaben aufs Handy bekommst', Icon: Bell },
        { bereich: 'mikrofon', titel: 'Mikrofon', erklaerung: 'Damit du Einträge diktieren kannst statt sie zu tippen', Icon: Mic },
        { bereich: 'kamera', titel: 'Kamera', erklaerung: 'Für Fotos im Bautagebuch und zum Belege scannen', Icon: Camera },
    ]

    return (
        <div className="h-full bg-slate-50 flex flex-col overflow-hidden">
            <header className="bg-white border-b border-slate-200 px-4 py-4 safe-area-top">
                <div className="flex items-center gap-3">
                    <button
                        onClick={() => navigate('/')}
                        aria-label="Zurück"
                        className="p-2 -ml-2 hover:bg-slate-100 rounded-full"
                    >
                        <ArrowLeft className="w-5 h-5 text-slate-600" />
                    </button>
                    <h1 className="text-xl font-bold text-slate-900">Einstellungen</h1>
                </div>
            </header>

            <main className="flex-1 p-4 space-y-4 overflow-y-auto safe-area-bottom">
                <p className="text-sm text-slate-600">
                    Hier siehst du, was die App auf diesem Gerät darf.
                </p>

                {status === null ? (
                    <div className="flex items-center justify-center gap-2 py-10 text-slate-500">
                        <Loader2 className="w-5 h-5 animate-spin" />
                        <span className="text-sm">Wird geprüft…</span>
                    </div>
                ) : zeilen.map(({ bereich, titel, erklaerung, Icon }) => {
                    const aktuell = status[bereich]
                    const abzeichen = ABZEICHEN[aktuell]
                    return (
                        <div key={bereich} className="bg-white rounded-2xl p-4 border border-slate-200 shadow-sm">
                            <div className="flex items-start gap-3">
                                <div className="w-10 h-10 bg-rose-50 rounded-xl flex items-center justify-center flex-shrink-0">
                                    <Icon aria-hidden="true" className="w-5 h-5 text-rose-600" />
                                </div>
                                <div className="flex-1 min-w-0">
                                    <div className="font-medium text-slate-900">{titel}</div>
                                    <div className="text-sm text-slate-500 mt-0.5">{erklaerung}</div>
                                </div>
                                <span className={`text-xs px-2 py-1 rounded-lg border whitespace-nowrap ${abzeichen.klassen}`}>
                                    {abzeichen.text}
                                </span>
                            </div>

                            {aktuell === 'nicht-gefragt' && (
                                <button
                                    onClick={() => void frageAn(bereich)}
                                    disabled={laeuft !== null}
                                    className="w-full mt-3 min-h-12 bg-rose-600 hover:bg-rose-700 disabled:bg-slate-300 text-white font-medium rounded-xl flex items-center justify-center gap-2 transition-colors"
                                >
                                    {laeuft === bereich ? <Loader2 className="w-5 h-5 animate-spin" /> : 'Erlauben'}
                                </button>
                            )}

                            {aktuell === 'blockiert' && (
                                <div className="mt-3">
                                    <button
                                        onClick={() => setHilfeOffen(hilfeOffen === bereich ? null : bereich)}
                                        aria-expanded={hilfeOffen === bereich}
                                        className="flex items-center gap-1 text-sm text-rose-700 hover:underline"
                                    >
                                        Wie schalte ich das wieder ein?
                                        <ChevronDown aria-hidden="true" className={`w-4 h-4 transition-transform ${hilfeOffen === bereich ? 'rotate-180' : ''}`} />
                                    </button>
                                    {hilfeOffen === bereich && (
                                        <div className="mt-2 text-sm text-slate-600 space-y-1">
                                            <p>Auf dem iPhone: Einstellungen → Safari → {titel} erlauben und die App neu starten.</p>
                                            <p>Auf Android: im Browser oben auf das Schloss-Symbol tippen und die Erlaubnis dort umstellen.</p>
                                        </div>
                                    )}
                                </div>
                            )}

                            {bereich === 'mikrofon' && (
                                <p className="text-xs text-slate-500 mt-3">
                                    Diktierte Aufnahmen werden nur zum Aufschreiben verschickt und danach sofort gelöscht.
                                    Gespeichert wird weder die Aufnahme noch der Text.
                                </p>
                            )}
                        </div>
                    )
                })}
            </main>
        </div>
    )
}
