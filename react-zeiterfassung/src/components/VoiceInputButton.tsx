import { useCallback, useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { Loader2, Mic, Square } from 'lucide-react'
import { useToast, mobileOverlayStyle } from './ui/toast'
import {
    starteAufnahme,
    mikrofonWirdUnterstuetzt,
    MikrofonNichtErlaubtError,
    type AufnahmeSitzung,
} from '../services/audioRecorderService'
import { transkribiere } from '../services/spracheingabeService'

/**
 * Mikrofon-Knopf fuer die mehrzeiligen Freitextfelder der App.
 *
 * <p>Der Handwerker spricht, die Aufnahme geht an den eigenen Endpunkt und
 * kommt als sauber aufgeschriebener Text zurueck. Der Text wird an den
 * vorhandenen Feldinhalt ANGEHAENGT, niemals ersetzt — getippten Text zu
 * ueberschreiben ist der schlimmste denkbare Fehlerfall dieser Funktion.
 *
 * <p>Deshalb bekommt die Komponente `wert` und gibt ueber `onErgebnis` den
 * KOMPLETTEN neuen Feldinhalt zurueck, statt nur den rohen Text zu liefern.
 * So liegt die Anhaenge-Regel an einer einzigen Stelle und nicht vierfach
 * kopiert in jedem Wirtsformular.
 *
 * <p><b>Datenschutz.</b> Die Aufnahme lebt nur im Arbeitsspeicher, wird nach
 * dem Aufruf verworfen und taucht weder in Logs noch in der Konsole auf.
 * Beim ersten Antippen sieht der Nutzer einen Hinweis darauf.
 */

/** Merker im localStorage, damit der Datenschutz-Hinweis nur einmal je Geraet kommt. */
const HINWEIS_MERKER = 'zeiterfassung_spracheingabe_hinweis'

/** Nach zwei Minuten wird von selbst gestoppt und das Gesagte verarbeitet. */
const MAX_AUFNAHME_MS = 120_000

/** Kuerzer heisst Fehlgriff statt Diktat — verwerfen, aber nicht als Fehler behandeln. */
const MIN_AUFNAHME_MS = 1_000

/** Wie lange der kurze Hinweis unter dem Knopf stehen bleibt. */
const HINWEIS_DAUER_MS = 4_000

type Zustand = 'bereit' | 'nimmt-auf' | 'verarbeitet'

export interface VoiceInputButtonProps {
    /** Aktueller Inhalt des Feldes. Wird nur gelesen, nie ersetzt. */
    wert: string
    /** Bekommt den KOMPLETTEN neuen Feldinhalt (alt + Leerzeile + Diktat). */
    onErgebnis: (neuerWert: string) => void
    /** Fuer die aria-labels, z.B. "Eintrag", "Bemerkung". Default: "Text". */
    feldName?: string
    /** Hart sperren, z.B. waehrend die Wirtsseite speichert. */
    disabled?: boolean
    /** Zusaetzliche Klassen fuer die Positionierung im Wirtsformular. */
    className?: string
}

function formatiereDauer(ms: number): string {
    const gesamt = Math.floor(ms / 1000)
    const minuten = Math.floor(gesamt / 60)
    const sekunden = gesamt % 60
    return `${minuten}:${String(sekunden).padStart(2, '0')}`
}

export default function VoiceInputButton({
    wert,
    onErgebnis,
    feldName = 'Text',
    disabled = false,
    className = '',
}: VoiceInputButtonProps) {
    const toast = useToast()
    const [zustand, setZustand] = useState<Zustand>('bereit')
    const [hinweis, setHinweis] = useState<string | null>(null)
    const [zeigtDatenschutzHinweis, setZeigtDatenschutzHinweis] = useState(false)
    const [dauerMs, setDauerMs] = useState(0)
    const [istOnline, setIstOnline] = useState(() => navigator.onLine)

    const sitzungRef = useRef<AufnahmeSitzung | null>(null)
    const stoppUhrRef = useRef<number | null>(null)
    const tickerRef = useRef<number | null>(null)
    const hinweisUhrRef = useRef<number | null>(null)
    const abbruchRef = useRef<AbortController | null>(null)

    // Online-/Offline-Erkennung wie in NetworkStatusBadge.
    useEffect(() => {
        const rauf = () => setIstOnline(true)
        const runter = () => setIstOnline(false)
        window.addEventListener('online', rauf)
        window.addEventListener('offline', runter)
        return () => {
            window.removeEventListener('online', rauf)
            window.removeEventListener('offline', runter)
        }
    }, [])

    const raeumeTimerAuf = useCallback(() => {
        if (stoppUhrRef.current !== null) {
            window.clearTimeout(stoppUhrRef.current)
            stoppUhrRef.current = null
        }
        if (tickerRef.current !== null) {
            window.clearInterval(tickerRef.current)
            tickerRef.current = null
        }
    }, [])

    // Beim Verlassen der Seite darf keine Aufnahme weiterlaufen — sonst bliebe
    // das Mikrofon offen und auf iOS der orange Aufnahmepunkt stehen.
    useEffect(() => {
        return () => {
            sitzungRef.current?.brichAb()
            sitzungRef.current = null
            abbruchRef.current?.abort()
            if (stoppUhrRef.current !== null) window.clearTimeout(stoppUhrRef.current)
            if (tickerRef.current !== null) window.clearInterval(tickerRef.current)
            if (hinweisUhrRef.current !== null) window.clearTimeout(hinweisUhrRef.current)
        }
    }, [])

    const zeigeKurzenHinweis = useCallback((text: string) => {
        setHinweis(text)
        if (hinweisUhrRef.current !== null) window.clearTimeout(hinweisUhrRef.current)
        hinweisUhrRef.current = window.setTimeout(() => setHinweis(null), HINWEIS_DAUER_MS)
    }, [])

    const verarbeite = useCallback(
        async (aufnahme: Blob) => {
            const token = localStorage.getItem('zeiterfassung_token')
            if (!token) {
                toast.error('Nicht angemeldet. Bitte den QR-Code neu scannen.')
                setZustand('bereit')
                return
            }

            setZustand('verarbeitet')
            const abbruch = new AbortController()
            abbruchRef.current = abbruch
            try {
                const text = await transkribiere(aufnahme, token, abbruch.signal)
                // Anhaengen statt ersetzen. Der vorhandene Text bleibt unangetastet.
                const alt = wert.trimEnd()
                onErgebnis(alt ? `${alt}\n\n${text}` : text)
                zeigeKurzenHinweis('Fertig.')
            } catch (fehler) {
                // Das Feld bleibt unveraendert — kein Teil-Update, kein Leeren.
                toast.error(fehler instanceof Error ? fehler.message : 'Das Diktat konnte nicht aufgeschrieben werden.')
            } finally {
                abbruchRef.current = null
                setZustand('bereit')
            }
        },
        [wert, onErgebnis, toast, zeigeKurzenHinweis],
    )

    const stoppeUndSende = useCallback(async () => {
        const sitzung = sitzungRef.current
        if (!sitzung) return
        sitzungRef.current = null
        raeumeTimerAuf()

        let aufnahme: Blob
        try {
            aufnahme = await sitzung.stoppe()
        } catch {
            toast.error('Die Aufnahme konnte nicht beendet werden.')
            setZustand('bereit')
            return
        }

        // Fehlgriff statt Diktat: verwerfen, aber nicht als Fehler behandeln.
        if (Date.now() - sitzung.gestartetAm < MIN_AUFNAHME_MS) {
            setZustand('bereit')
            zeigeKurzenHinweis('Zu kurz — Knopf drücken und sprechen')
            return
        }

        await verarbeite(aufnahme)
    }, [raeumeTimerAuf, toast, verarbeite, zeigeKurzenHinweis])

    const beginneAufnahme = useCallback(async () => {
        setHinweis(null)
        let sitzung: AufnahmeSitzung
        try {
            sitzung = await starteAufnahme()
        } catch (fehler) {
            if (fehler instanceof MikrofonNichtErlaubtError) {
                toast.error('Kein Zugriff auf das Mikrofon. Unter "Einstellungen" in der App kannst du nachsehen, wie du es erlaubst.')
            } else {
                toast.error('Die Aufnahme konnte nicht gestartet werden.')
            }
            return
        }

        sitzungRef.current = sitzung
        setZustand('nimmt-auf')
        setDauerMs(0)

        tickerRef.current = window.setInterval(() => {
            setDauerMs(Date.now() - sitzung.gestartetAm)
        }, 250)

        // Kein Abbruch: das bereits Gesagte wird ganz normal verarbeitet.
        stoppUhrRef.current = window.setTimeout(() => {
            toast.info('Zwei Minuten sind voll. Das Gesagte wird jetzt aufgeschrieben.')
            void stoppeUndSende()
        }, MAX_AUFNAHME_MS)
    }, [toast, stoppeUndSende])

    const beiKlick = useCallback(() => {
        if (zustand === 'nimmt-auf') {
            void stoppeUndSende()
            return
        }
        if (zustand !== 'bereit') return

        // Beim ersten Mal erst erklaeren, wohin die Aufnahme geht — dann erst aufnehmen.
        let schonGesehen = false
        try {
            schonGesehen = localStorage.getItem(HINWEIS_MERKER) === '1'
        } catch {
            // Privater Modus oder gesperrter Speicher: dann eben jedes Mal zeigen.
            schonGesehen = false
        }
        if (!schonGesehen) {
            setZeigtDatenschutzHinweis(true)
            return
        }
        void beginneAufnahme()
    }, [zustand, stoppeUndSende, beginneAufnahme])

    const bestaetigeHinweis = useCallback(() => {
        try {
            localStorage.setItem(HINWEIS_MERKER, '1')
        } catch {
            // Nicht schlimm — dann kommt der Hinweis beim naechsten Mal erneut.
        }
        setZeigtDatenschutzHinweis(false)
        void beginneAufnahme()
    }, [beginneAufnahme])

    const kannNichtDiktieren = !mikrofonWirdUnterstuetzt()
    const gesperrt = disabled || kannNichtDiktieren || !istOnline || zustand === 'verarbeitet'

    // Kein stummes Grau: wenn der Knopf nicht geht, steht auch da, warum.
    const grund = kannNichtDiktieren
        ? 'Dieses Gerät kann nicht diktieren.'
        : !istOnline
            ? 'Spracheingabe braucht Internet.'
            : null

    const statusText =
        zustand === 'nimmt-auf' ? formatiereDauer(dauerMs)
            : zustand === 'verarbeitet' ? 'Wird aufgeschrieben…'
                : grund ?? hinweis

    return (
        <div className={`flex items-center gap-2 ${className}`}>
            <button
                type="button"
                onClick={beiKlick}
                disabled={gesperrt}
                aria-label={zustand === 'nimmt-auf' ? 'Aufnahme beenden' : `${feldName} diktieren`}
                className={`flex min-h-11 min-w-11 items-center justify-center rounded-full border transition-colors focus:outline-none focus:ring-2 focus:ring-rose-500 disabled:cursor-not-allowed disabled:border-slate-200 disabled:bg-slate-100 disabled:text-slate-400 ${
                    zustand === 'nimmt-auf'
                        ? 'border-rose-600 bg-rose-600 text-white hover:bg-rose-700'
                        : 'border-rose-300 text-rose-700 hover:bg-rose-50'
                }`}
            >
                {zustand === 'verarbeitet'
                    ? <Loader2 aria-hidden="true" className="h-5 w-5 animate-spin" />
                    : zustand === 'nimmt-auf'
                        ? <Square aria-hidden="true" className="h-5 w-5" />
                        : <Mic aria-hidden="true" className="h-5 w-5" />}
            </button>

            {zustand === 'nimmt-auf' && (
                <span aria-hidden="true" className="h-2 w-2 rounded-full bg-rose-600 motion-safe:animate-pulse" />
            )}

            <p role="status" className="text-xs text-slate-500">{statusText}</p>

            {zeigtDatenschutzHinweis && createPortal(
                <div
                    style={mobileOverlayStyle}
                    className="fixed inset-0 z-[10010] flex items-center justify-center bg-black/40 p-4 backdrop-blur-sm"
                    onClick={event => { if (event.target === event.currentTarget) setZeigtDatenschutzHinweis(false) }}
                >
                    <div
                        role="dialog"
                        aria-modal="true"
                        aria-labelledby="spracheingabe-hinweis-titel"
                        aria-describedby="spracheingabe-hinweis-text"
                        className="max-h-full w-full max-w-md overflow-auto rounded-2xl border border-slate-200 bg-white p-5 shadow-2xl"
                    >
                        <Mic aria-hidden="true" className="mb-3 h-7 w-7 text-rose-600" />
                        <h2 id="spracheingabe-hinweis-titel" className="text-lg font-semibold text-slate-900">Sprechen statt tippen</h2>
                        <p id="spracheingabe-hinweis-text" className="mt-2 text-sm text-slate-600">
                            Deine Aufnahme wird nur zum Aufschreiben verschickt und danach sofort gelöscht. Gespeichert wird nichts.
                        </p>
                        <div className="mt-5 flex gap-3">
                            <button
                                type="button"
                                onClick={() => setZeigtDatenschutzHinweis(false)}
                                className="min-h-12 flex-1 rounded-lg border border-slate-300 px-3 text-slate-700 focus:outline-none focus:ring-2 focus:ring-rose-500"
                            >
                                Abbrechen
                            </button>
                            <button
                                type="button"
                                onClick={bestaetigeHinweis}
                                className="min-h-12 flex-1 rounded-lg bg-rose-600 px-3 text-white hover:bg-rose-700 focus:outline-none focus:ring-2 focus:ring-rose-500"
                            >
                                Verstanden
                            </button>
                        </div>
                    </div>
                </div>,
                document.body,
            )}
        </div>
    )
}
