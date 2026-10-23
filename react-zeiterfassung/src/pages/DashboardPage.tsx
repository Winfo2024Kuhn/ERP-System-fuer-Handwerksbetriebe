import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Play, FolderOpen, Users, Clock, Loader2, ChevronRight, ArrowRightLeft, LogOut, Settings, Plane, AlertTriangle, Calendar, Hammer, Receipt, Wrench, Stethoscope } from 'lucide-react'
import { createOperationId, OfflineService, sendeBuchung, startZeitAusErgebnis, type FailedEntry } from '../services/OfflineService'
import NetworkStatusBadge from '../components/NetworkStatusBadge'
import FailedEntriesModal from '../components/FailedEntriesModal'
import { hatEingerichtetesZeitkonto, zeitkontoHinweis, type ZeitkontoStatus } from '../types/zeitkonto'
import { NachrichtenGlocke } from '../features/nachrichten/NachrichtenGlocke'
import { useToast } from '../components/ui/toast'

interface Session {
    projektId: number | null
    projektName: string | null
    kundenName: string | null
    auftragsnummer: string | null
    arbeitsgangId: number | null
    arbeitsgangName: string | null
    produktkategorieId?: number | null
    produktkategorieName?: string | null
    startTime: string
    typ: 'URLAUB' | 'KRANKHEIT' | 'FORTBILDUNG' | 'PAUSE' | 'ARBEIT' | null
}

// Helper to check if session is a special type (not normal work)
const isAbwesenheitOderPause = (typ: Session['typ']) =>
    typ === 'URLAUB' || typ === 'KRANKHEIT' || typ === 'FORTBILDUNG' || typ === 'PAUSE';

// Eine gerade erst lokal gestartete/gewechselte Buchung gilt für dieses
// Zeitfenster als "frisch". Innerhalb dieses Fensters darf ein verzögertes
// GET /aktiv (Server-Transaction noch nicht sichtbar) die lokale Session NICHT
// löschen. Gleiche Größenordnung wie die bestehenden Cooldown-Fenster (15s).
const SESSION_FRESHNESS_MS = 15000;

// Rotating daily greetings for a personal touch
const getDailyGreeting = (): string => {
    const greetings = [
        'Moin', 'Hey', 'Servus', 'Hallo', 'Hi', 'Grüß dich'
    ];
    // Use day of year as seed for consistent daily greeting
    const now = new Date();
    const startOfYear = new Date(now.getFullYear(), 0, 0);
    const dayOfYear = Math.floor((now.getTime() - startOfYear.getTime()) / (1000 * 60 * 60 * 24));
    return greetings[dayOfYear % greetings.length];
};

interface Arbeitsgang {
    id: number
    beschreibung: string
}

interface ProjektSummary {
    id: number
    name: string
    beschreibung?: string | null
    projektNummer?: string | null
    kundenName?: string | null
}

// Auf welchem Projekt läuft die Buchung nach einem Tätigkeitswechsel weiter?
// Im Fenster "Tätigkeit wählen" kann der Handwerker über "Ändern" ein anderes
// Projekt wählen – dann zählt dieses. Die Kategorie wandert dabei nicht mit:
// Kategorien hängen am Projekt, die alte gibt es im neuen womöglich gar nicht.
const projektFuerTaetigkeitswechsel = (
    session: Session,
    projekte: ProjektSummary[],
    gewaehlteProjektId: number | null,
) => {
    const neuesProjekt = gewaehlteProjektId !== null && gewaehlteProjektId !== session.projektId
        ? projekte.find(p => p.id === gewaehlteProjektId)
        : undefined
    if (!neuesProjekt) {
        return {
            projektId: session.projektId,
            projektName: session.projektName,
            kundenName: session.kundenName,
            auftragsnummer: session.auftragsnummer,
            produktkategorieId: session.produktkategorieId ?? null,
            produktkategorieName: session.produktkategorieName ?? null,
        }
    }
    return {
        projektId: neuesProjekt.id,
        projektName: neuesProjekt.name,
        kundenName: neuesProjekt.kundenName ?? null,
        auftragsnummer: neuesProjekt.projektNummer ?? null,
        produktkategorieId: null,
        produktkategorieName: null,
    }
}

interface Mitarbeiter {
    id: number
    name?: string
    vorname?: string
    nachname?: string
}

interface DashboardPageProps {
    mitarbeiter: Mitarbeiter | null
    onLogout: () => void
    syncStatus?: 'syncing' | 'done' | 'error'
    /** Wird nach jedem erfolgreichen Abgleich hochgezählt (App.tsx) – Auslöser zum Neuladen. */
    abgleichNummer?: number
    onSync?: () => void
}

export default function DashboardPage({ mitarbeiter, syncStatus, abgleichNummer = 0, onSync }: DashboardPageProps) {
    const navigate = useNavigate()
    const [activeSession, setActiveSession] = useState<Session | null>(null)
    const [elapsedTime, setElapsedTime] = useState('00:00:00')
    const [showArbeitsgangSwitch, setShowArbeitsgangSwitch] = useState(false)
    const [arbeitsgaenge, setArbeitsgaenge] = useState<Arbeitsgang[]>([])
    const [switching, setSwitching] = useState(false)
    const actionLockRef = useRef(false)
    const [actionInProgress, setActionInProgress] = useState(false)
    const [heuteStunden, setHeuteStunden] = useState(0)
    const [heuteMinuten, setHeuteMinuten] = useState(0)
    const [zeitkontoStatus, setZeitkontoStatus] = useState<ZeitkontoStatus | null>(null)
    const [zeitkontoWirdGeprueft, setZeitkontoWirdGeprueft] = useState(true)
    const [zeitkontoFehler, setZeitkontoFehler] = useState<string | null>(null)

    // Checkbox/Switch logic
    const [viewMode, setViewMode] = useState<'activities' | 'projects'>('activities')
    const [projekte, setProjekte] = useState<ProjektSummary[]>([])
    const [selectedProjektId, setSelectedProjektId] = useState<number | null>(null)

    // Kategorie switch modal
    const [showKategorieSwitch, setShowKategorieSwitch] = useState(false)
    const [kategorien, setKategorien] = useState<Array<{ id: number; name: string }>>([])

    // Hinweis nach einer Aktion, die NICHT wie erwartet durchlief. Bisher landete
    // ein nicht übertragener Feierabend nur in der Konsole: Die Karte verschwand,
    // der Handwerker war überzeugt gestempelt zu haben – und am Server lief die
    // Buchung weiter, bis der Auto-Stop sie auf die Endezeit setzte.
    // Bleibt als schwebender Warn-Toast stehen, bis man ihn per X schließt.
    const toast = useToast()
    const zeigeAktionsHinweis = (hinweis: string) => toast.warning(hinweis)

    // Langzeitkrankmeldung: informiert den Mitarbeiter, wenn gerade eine Phase
    // (Lohnfortzahlung/Krankengeld/Wiedereingliederung) läuft - damit klar ist,
    // wogegen er stempelt, wenn sein Tagessoll reduziert ist. NUR LESEND: die
    // Meldung wird ausschließlich am PC gepflegt, die App zeigt sie nur an.
    interface LangzeitFall {
        phase: 'LOHNFORTZAHLUNG' | 'KRANKENGELD' | 'WIEDEREINGLIEDERUNG'
        phaseLabel: string
        heuteGeplanteStunden: number | null
        seit: string
        bisDatum: string | null
    }
    const [langzeitFall, setLangzeitFall] = useState<LangzeitFall | null>(null)

    // Reparatur-Liste (Failed Entries): Buchungen, die der Server beim
    // Sync abgelehnt hat. "Mobile gewinnt" - der Handwerker sieht den
    // Fehler statt dass die Buchung still gelöscht wird.
    const [failedEntries, setFailedEntries] = useState<FailedEntry[]>([])
    const [showFailedModal, setShowFailedModal] = useState(false)

    const hatZeitkonto = hatEingerichtetesZeitkonto(zeitkontoStatus)

    const loadZeitkontoStatus = async () => {
        const token = localStorage.getItem('zeiterfassung_token')
        if (!token) return

        setZeitkontoWirdGeprueft(true)
        setZeitkontoFehler(null)
        try {
            const res = await fetch(`/api/zeiterfassung/buchungszeitfenster/${encodeURIComponent(token)}`)
            if (!res.ok) throw new Error('Status konnte nicht geladen werden')
            const data = await res.json() as ZeitkontoStatus
            if (typeof data.fuehrtZeitkonto !== 'boolean' || typeof data.eingerichtet !== 'boolean') {
                throw new Error('Unvollständige Antwort')
            }
            setZeitkontoStatus(data)
        } catch {
            setZeitkontoStatus(null)
            setZeitkontoFehler('Die Arbeitszeit-Einrichtung konnte nicht geprüft werden. Bitte später erneut versuchen.')
        } finally {
            setZeitkontoWirdGeprueft(false)
        }
    }

    const loadFailedEntries = async () => {
        const entries = await OfflineService.getFailedEntries()
        setFailedEntries(entries)
    }

    const handleDismissFailedEntry = async (id: string) => {
        await OfflineService.removeFailedEntry(id)
        await loadFailedEntries()
        await loadHeuteGearbeitet()
    }

    // Belegerfassung: Kachel nur sichtbar, wenn Mitarbeiter zur Abteilung Buchhaltung gehört
    // (Permission läuft über bestehendes System AbteilungDokumentBerechtigung 'BELEG').
    const [darfBelegeScannen, setDarfBelegeScannen] = useState(false)
    useEffect(() => {
        const token = localStorage.getItem('zeiterfassung_token')
        if (!token) return
        fetch(`/api/buchhaltung/mobile/me/permissions?token=${token}`)
            .then(res => res.ok ? res.json() : null)
            .then(data => { if (data?.darfScannen) setDarfBelegeScannen(true) })
            .catch(() => { /* offline -> Kachel bleibt versteckt, kein Drama */ })
    }, [])

    // Load today's hours - OFFLINE FIRST: show local data immediately
    //
    // "Heute gearbeitet" muss IMMER stimmen. Quellen-Komposition:
    //   serverMinuten        = abgeschlossene Buchungen, die der Server kennt
    //   unsyncedStopMinutes  = Stop/Pause-Events, die der Server noch NICHT
    //                          kennt (pending) ODER abgelehnt hat (failed).
    //                          Jeder Eintrag trägt seine eigene Dauer, daher
    //                          gibt es weder Doppelzählung beim Sync noch
    //                          Datenverlust durch eine globale clear()-Aktion.
    //   currentSessionMinuten = laufende Session (live aus localStorage)
    const loadHeuteGearbeitet = async () => {
        const token = localStorage.getItem('zeiterfassung_token')
        if (!token) return

        const heuteData = await OfflineService.getHeuteGearbeitet(token)
        const serverMinuten = (heuteData.stunden || 0) * 60 + (heuteData.minuten || 0)

        // Laufende Session ermitteln. Quelle: lokale Session zuerst, dann
        // Server-Antwort als Fallback (z.B. nach App-Neustart bevor lokale
        // Session geladen wurde).
        let currentSessionMinuten = 0
        const storedSession = localStorage.getItem('zeiterfassung_active_session')
        let runningStart: Date | null = null
        let runningIsAbwesenheit = false

        if (storedSession) {
            try {
                const session = JSON.parse(storedSession)
                if (session.startTime) {
                    runningStart = new Date(session.startTime)
                    runningIsAbwesenheit = isAbwesenheitOderPause(session.typ)
                }
            } catch { /* ignore */ }
        } else if (heuteData.aktiveBuchungStartZeit) {
            runningStart = new Date(heuteData.aktiveBuchungStartZeit)
            runningIsAbwesenheit = false
        }

        if (runningStart && !runningIsAbwesenheit) {
            const now = new Date()
            currentSessionMinuten = Math.max(0, Math.floor((now.getTime() - runningStart.getTime()) / 60000))
        }

        // Unsynced Stop/Pause-Events (pending + Reparatur-Liste) tragen
        // ihre eigene Dauer bei. Damit ist die Anzeige immer konsistent,
        // egal ob der Server schon synchronisiert hat oder nicht.
        const unsyncedMinuten = await OfflineService.getUnsyncedStopMinutes()

        const totalMinuten = serverMinuten + unsyncedMinuten + currentSessionMinuten
        setHeuteStunden(Math.floor(totalMinuten / 60))
        setHeuteMinuten(totalMinuten % 60)
    }

    // Load active session - OFFLINE FIRST PRIORITY
    const loadActiveSession = async () => {
        const token = localStorage.getItem('zeiterfassung_token')
        if (!token) return

        // FIRST: Always load from localStorage for instant display
        const stored = localStorage.getItem('zeiterfassung_active_session')
        let localSession: Session | null = null
        if (stored) {
            try {
                localSession = JSON.parse(stored)
                setActiveSession(localSession)
                console.log('📱 Session aus localStorage geladen:', localSession)
            } catch {
                // Invalid JSON, ignore
            }
        }

        // STOP-COOLDOWN: Wenn der User gerade gestoppt hat (Feierabend / Pause beendet),
        // darf der Server-State die leere lokale Session NICHT wiederherstellen.
        // Sonst sieht der User eine alte (Pause-)Session, die er nicht mehr loswird.
        const stoppedAt = localStorage.getItem('zeiterfassung_stopped_at')
        if (stoppedAt) {
            const stoppedAtMs = parseInt(stoppedAt, 10)
            const stoppedElapsed = Date.now() - stoppedAtMs
            if (stoppedElapsed < SESSION_FRESHNESS_MS) {
                // Wurde die lokale Session NACH dem Stop gestartet, ist sie eine
                // NEUE Buchung (z.B. Pause -> "Arbeit fortsetzen" -> Projekt
                // anstechen). Der Stop-Cooldown darf sie NICHT löschen, sonst
                // verschwindet die frisch angestochene Buchung bis der Cooldown
                // nach 15s abläuft. Sie ist lokal autoritativ -> behalten.
                const sessionStartedAfterStop = !!localSession?.startTime
                    && new Date(localSession.startTime).getTime() >= stoppedAtMs
                if (sessionStartedAfterStop) {
                    console.log('🆕 Neue Buchung nach Stop - behalte lokale Session, Cooldown beendet')
                    localStorage.removeItem('zeiterfassung_stopped_at')
                    return
                }
                console.log(`🛑 Stop wurde vor ${(stoppedElapsed / 1000).toFixed(1)}s ausgelöst - ignoriere Server-Session (Cooldown)`)
                if (localSession) {
                    // Defensive: localStorage sollte leer sein, aber falls nicht, aufräumen
                    localStorage.removeItem('zeiterfassung_active_session')
                    setActiveSession(null)
                }
                return
            }
            localStorage.removeItem('zeiterfassung_stopped_at')
        }

        // Check if we have pending offline entries - if yes, DON'T sync with server!
        const pendingCount = await OfflineService.getPendingCount()
        if (pendingCount > 0) {
            console.log(`⚠️ ${pendingCount} pending entries - überspringe Server-Sync, behalte lokale Session`)
            return // SKIP server sync entirely!
        }

        // Check if a 'start' entry was recently synced — if yes, give the server
        // a few seconds before we trust its GET /aktiv response.
        // Without this cooldown, the GET can arrive before the server's transaction
        // is fully visible, returning { aktiv: false } and killing the local session.
        const startSyncedAt = localStorage.getItem('zeiterfassung_start_synced_at')
        if (startSyncedAt && localSession) {
            const elapsed = Date.now() - parseInt(startSyncedAt, 10)
            if (elapsed < 15000) { // 15 second cooldown
                console.log(`⏳ Start wurde vor ${(elapsed / 1000).toFixed(1)}s gesynct - behalte lokale Session (Cooldown)`)
                return // Trust local session during cooldown
            }
            // Cooldown expired - clean up the flag
            localStorage.removeItem('zeiterfassung_start_synced_at')
        }

        // Only sync with server if NO pending entries (we're fully synced)
        try {
            const controller = new AbortController()
            const timeoutId = setTimeout(() => controller.abort(), 2000)

            const res = await fetch(`/api/zeiterfassung/aktiv/${token}`, {
                signal: controller.signal
            })
            clearTimeout(timeoutId)

            if (res.ok) {
                const data = await res.json()
                if (data && data.id) {
                    // Server has an active session - update localStorage and state
                    const session: Session = {
                        projektId: data.projektId,
                        projektName: data.projektName,
                        kundenName: data.kundenName || null,
                        auftragsnummer: data.auftragsnummer || null,
                        arbeitsgangId: data.arbeitsgangId,
                        arbeitsgangName: data.arbeitsgangName,
                        produktkategorieId: data.produktkategorieId || null,
                        produktkategorieName: data.produktkategorieName || null,
                        startTime: data.startZeit,
                        typ: data.typ || null,
                    }
                    localStorage.setItem('zeiterfassung_active_session', JSON.stringify(session))
                    setActiveSession(session)
                    console.log('✅ Session vom Server synchronisiert:', session)
                } else {
                    // Server says no active session AND we have no pending entries
                    // (we already returned early above if pendingCount > 0)

                    // FRISCH-GUARD: Eine gerade erst lokal gestartete/gewechselte
                    // Buchung darf NICHT durch eine verzögerte Server-Antwort gelöscht
                    // werden. Der GET /aktiv (2s-Timeout) kann den frisch committeten
                    // Start/Wechsel noch nicht sehen (Transaction-Visibility) und liefert
                    // dann fälschlich aktiv:false. Symptom: "Aktive Buchung"-Karte
                    // verschwindet direkt nach dem Anstechen/Wechseln und kommt erst nach
                    // App-Neustart zurück. Ein echtes Beenden ist hier nicht betroffen:
                    // handleStopSession setzt zuerst 'zeiterfassung_stopped_at', das oben
                    // (Stop-Cooldown) bereits früher returnt.
                    if (localSession?.startTime) {
                        const sessionAge = Date.now() - new Date(localSession.startTime).getTime()
                        if (sessionAge < SESSION_FRESHNESS_MS) {
                            console.log(`🆕 Lokale Session ist erst ${(sessionAge / 1000).toFixed(1)}s alt - ignoriere Server aktiv:false (Frisch-Guard)`)
                            return
                        }
                    }

                    if (localSession) {
                        console.log('ℹ️ Server hat keine aktive Session mehr - räume lokale Session auf')
                    }
                    localStorage.removeItem('zeiterfassung_active_session')
                    setActiveSession(null)
                    console.log('ℹ️ Keine aktive Session')
                }
            }
        } catch {
            console.log('📴 Server nicht erreichbar - behalte lokale Session')
        }
    }

    // Load running Langzeitkrankmeldung-Phase (falls vorhanden). Der Endpunkt
    // liefert genau fünf Felder, kein Name, keine Notiz (DSGVO) - und ein
    // leeres Objekt {}, wenn nichts anliegt (das ist der Normalfall für fast
    // alle Mitarbeiter, fast immer). Ein Fehler hier darf das Dashboard nie
    // kaputtmachen: im Zweifel bleibt die Karte einfach weg.
    const loadLangzeitkrankmeldung = async () => {
        const token = localStorage.getItem('zeiterfassung_token')
        if (!token) return
        try {
            const res = await fetch(`/api/zeiterfassung/langzeitkrankmeldung/${token}`)
            if (res.ok) {
                const data = await res.json()
                if (data && data.phase) {
                    setLangzeitFall(data)
                } else {
                    setLangzeitFall(null)
                }
            }
        } catch {
            console.log('Langzeitkrankmeldung konnte nicht geladen werden')
        }
    }

    useEffect(() => {
        // Load active session from server (primary) or localStorage (fallback)
        // Bewusste Ausnahme: Die fünf Ladefunktionen gleichen mit Server und Offline-Speicher ab und setzen
        // ihren Zustand meist erst nach await (das erkennt die Regel nicht). Synchron übernehmen nur
        // loadActiveSession (im Handy gespeicherte Buchung, Stop-Cooldown) und loadZeitkontoStatus
        // (Ladeanzeige) etwas. Sie auf Promise-Ketten umzubauen ist ein eigener Schritt für die Offline-Logik.
        // eslint-disable-next-line react-hooks/set-state-in-effect -- siehe Kommentar darüber
        loadActiveSession()
        loadZeitkontoStatus()
        // Load today's hours
        loadHeuteGearbeitet()
        // Langzeitkrankmeldung laden. Nachrichten (Entscheidungen, Urlaubsverfall)
        // lädt die Glocke selbst, parallel dazu.
        loadLangzeitkrankmeldung()
        // Load repair list (failed sync entries)
        loadFailedEntries()

        // NOTE: Online/Heartbeat handlers wurden nach App.tsx verschoben
        // um globalen Sync auf allen Seiten zu ermöglichen
    }, [])

    // Nach jedem erfolgreichen Abgleich neu laden (z. B. nachdem wartende Buchungen
    // übertragen wurden). Der Zähler statt syncStatus === 'done': Ein zweiter Abgleich
    // direkt nach dem ersten wäre 'done' → 'done' und würde nichts auslösen.
    const geladenBeiAbgleich = useRef(abgleichNummer)
    useEffect(() => {
        if (abgleichNummer === geladenBeiAbgleich.current) return
        geladenBeiAbgleich.current = abgleichNummer
        loadActiveSession()
        loadHeuteGearbeitet()
        loadFailedEntries()
        loadZeitkontoStatus()
    }, [abgleichNummer])

    useEffect(() => {
        if (!activeSession) return

        const interval = setInterval(() => {
            const start = new Date(activeSession.startTime)
            const now = new Date()
            const diff = Math.floor((now.getTime() - start.getTime()) / 1000)

            const hours = Math.floor(diff / 3600)
            const minutes = Math.floor((diff % 3600) / 60)
            const seconds = diff % 60

            setElapsedTime(
                `${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`
            )
        }, 1000)

        return () => clearInterval(interval)
    }, [activeSession])

    const displayName = mitarbeiter?.vorname
        ? `${mitarbeiter.vorname}`
        : mitarbeiter?.name || 'Mitarbeiter'

    const formatDate = (date: Date) => {
        const days = ['Sonntag', 'Montag', 'Dienstag', 'Mittwoch', 'Donnerstag', 'Freitag', 'Samstag']
        return `${days[date.getDay()]}, ${date.toLocaleDateString('de-DE')}`
    }

    const formatTime = (date: Date) => {
        return date.toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit' })
    }

    // ISO-Datum (yyyy-MM-dd) -> deutsches Format, ohne Zeitzonen-Umweg über Date().
    const formatDatumDe = (isoDatum: string) => {
        const [jahr, monat, tag] = isoDatum.split('-')
        return `${tag}.${monat}.${jahr}`
    }

    // 4.00 -> "4", 2.50 -> "2,5" - deutsches Zahlenformat statt Nachkommastellen-Ballast.
    const formatStundenDe = (stunden: number) => stunden.toLocaleString('de-DE', { maximumFractionDigits: 2 })

    const beginAction = () => {
        if (actionLockRef.current) return false
        actionLockRef.current = true
        setActionInProgress(true)
        return true
    }

    const endAction = () => {
        actionLockRef.current = false
        setActionInProgress(false)
    }

    const uhrzeit = (iso: string) => new Date(iso).toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit' })

    // Stop current session (Pause/Buchung beenden)
    const handleStopSession = async () => {
        // Beide Guards haben früher stumm abgebrochen. Bei einer Aktion, die für
        // den Handwerker "Feierabend" bedeutet, darf nie einfach nichts passieren.
        if (!activeSession) {
            zeigeAktionsHinweis('Es läuft gerade keine Buchung, die beendet werden könnte.')
            return
        }
        if (!beginAction()) {
            zeigeAktionsHinweis('Einen Moment – die letzte Aktion läuft noch.')
            return
        }
        const token = localStorage.getItem('zeiterfassung_token')
        const stopTime = new Date().toISOString()

        // Dauer der beendeten Arbeit: zählt in "heute gearbeitet", solange der
        // Server den Feierabend noch nicht kennt (Warteschlange/Reparatur-Liste).
        const elapsedMinutes = Math.floor((Date.now() - new Date(activeSession.startTime).getTime()) / 60000)
        const isWorkSession = !isAbwesenheitOderPause(activeSession.typ)

        try {
            // sendeBuchung reiht hinter älteren Offline-Einträgen ein, bricht die
            // Anfrage bei langsamem Netz nicht ab und behandelt 429/5xx als "später".
            const ergebnis = await sendeBuchung('stop', { token }, {
                originalTime: stopTime,
                operationId: createOperationId(),
                durationMinutes: isWorkSession && elapsedMinutes > 0 ? elapsedMinutes : undefined,
                ablehnungInReparaturListe: true,
            })

            if (ergebnis.art === 'eingereiht') {
                // Ehrlich bleiben: gespeichert, aber noch nicht im Büro angekommen.
                zeigeAktionsHinweis(
                    `Feierabend um ${uhrzeit(stopTime)} gespeichert. `
                    + 'Er wird automatisch ans Büro übertragen, sobald die Verbindung steht.'
                )
            } else if (ergebnis.art === 'abgelehnt') {
                toast.error(`Feierabend nicht übernommen: ${ergebnis.meldung}`)
                loadFailedEntries()
            }

            // Stop-Cooldown setzen, damit ein nachfolgendes loadActiveSession()
            // die alte Session NICHT vom Server wiederherstellt (z.B. wegen
            // verzögerter Server-Transaction oder Idempotency-Replay).
            localStorage.setItem('zeiterfassung_stopped_at', Date.now().toString())
            localStorage.removeItem('zeiterfassung_active_session')
            setActiveSession(null)
            setElapsedTime('00:00:00')
            // Reload today's hours
            loadHeuteGearbeitet()
        } catch {
            toast.error('Der Feierabend konnte auf dem Handy nicht gespeichert werden. Bitte noch einmal tippen.')
        } finally {
            endAction()
        }
    }

    // Start pause (stop current work, start pause booking)
    const handlePause = async () => {
        if (!beginAction()) return

        const token = localStorage.getItem('zeiterfassung_token')
        if (!token) {
            endAction()
            return
        }
        const pauseTime = new Date().toISOString()

        // Falls eine Arbeitsbuchung läuft: Dauer der gerade beendeten Arbeit
        // ermitteln, damit "heute gearbeitet" auch offline korrekt bleibt.
        let workDurationMinutes: number | undefined
        if (activeSession && !isAbwesenheitOderPause(activeSession.typ)) {
            const elapsed = Math.floor((Date.now() - new Date(activeSession.startTime).getTime()) / 60000)
            if (elapsed > 0) workDurationMinutes = elapsed
        }

        try {
            const ergebnis = await sendeBuchung('pause', { token }, {
                originalTime: pauseTime,
                operationId: createOperationId(),
                durationMinutes: workDurationMinutes,
                ablehnungInReparaturListe: true,
            })

            // "Mobile gewinnt": Die Pause wird immer sofort angezeigt – auch wenn sie
            // noch wartet oder abgelehnt wurde (dann steht sie in der Reparatur-Liste).
            const pauseSession: Session = {
                projektId: null,
                projektName: null,
                kundenName: null,
                auftragsnummer: null,
                arbeitsgangId: null,
                arbeitsgangName: null,
                startTime: startZeitAusErgebnis(ergebnis, pauseTime),
                typ: 'PAUSE',
            }
            if (ergebnis.art !== 'ok') {
                // Cooldown setzen, damit ein nachfolgendes loadActiveSession() die
                // optimistische Pause nicht durch eine vom Server weiterhin aktive
                // Arbeitsbuchung ueberschreibt - z.B. wenn das Pause-Event in der
                // Reparatur-Liste steht (pendingCount=0) und der Server die alte
                // Buchung noch liefert.
                localStorage.setItem('zeiterfassung_start_synced_at', Date.now().toString())
            }
            if (ergebnis.art === 'abgelehnt') {
                toast.error(`Pause nicht übernommen: ${ergebnis.meldung}`)
                loadFailedEntries()
            }
            localStorage.setItem('zeiterfassung_active_session', JSON.stringify(pauseSession))
            setActiveSession(pauseSession)
            loadHeuteGearbeitet()
        } catch {
            toast.error('Die Pause konnte auf dem Handy nicht gespeichert werden. Bitte noch einmal tippen.')
        } finally {
            endAction()
        }
    }

    // Resume from pause - stop the active PAUSE booking, then navigate to start work
    const handleResumeFromPause = async () => {
        if (!beginAction()) return

        const token = localStorage.getItem('zeiterfassung_token')

        try {
            const ergebnis = await sendeBuchung('stop', { token }, {
                originalTime: new Date().toISOString(),
                operationId: createOperationId(),
                ablehnungInReparaturListe: true,
            })
            if (ergebnis.art === 'abgelehnt') {
                toast.error(`Pausenende nicht übernommen: ${ergebnis.meldung}`)
                loadFailedEntries()
            }

            // Stop-Cooldown setzen (analog zu handleStopSession), damit der Server
            // die beendete Pause-Session nicht versehentlich zurückbringt.
            localStorage.setItem('zeiterfassung_stopped_at', Date.now().toString())
            // Clear local pause session and navigate to time tracking
            localStorage.removeItem('zeiterfassung_active_session')
            setActiveSession(null)
            navigate('/zeiterfassung')
        } catch {
            toast.error('Das Pausenende konnte auf dem Handy nicht gespeichert werden. Bitte noch einmal tippen.')
        } finally {
            endAction()
        }
    }

    // Switch to a different project - DON'T stop yet, navigate to selection first
    // The old booking will only be stopped when the user actually starts a new one
    const handleSwitchProjekt = () => {
        if (!activeSession) return
        // Navigate with switching flag - old session stays active until new one is confirmed
        navigate('/zeiterfassung?switching=true')
    }

    // Open Arbeitsgang switch modal
    const handleOpenArbeitsgangSwitch = async () => {
        const token = localStorage.getItem('zeiterfassung_token') || undefined
        const agData = await OfflineService.getArbeitsgaenge(token) as Arbeitsgang[]
        const projData = await OfflineService.getProjekte() as ProjektSummary[]

        setArbeitsgaenge(agData)
        setProjekte(projData)
        if (activeSession) {
            setSelectedProjektId(activeSession.projektId)
        }

        setViewMode('activities')
        setShowArbeitsgangSwitch(true)
    }

    // Open Kategorie switch modal
    const handleOpenKategorieSwitch = async () => {
        if (!activeSession?.projektId) return

        // Load categories for current project
        const katData = await OfflineService.getKategorien(activeSession.projektId) as Array<{ id: number; name: string }>
        setKategorien(katData)
        setShowKategorieSwitch(true)
    }

    /**
     * Wechsel = laufende Buchung beenden und neue starten. Beide gehen über
     * sendeBuchung, also nie an älteren Offline-Einträgen vorbei: Wird der Stop
     * eingereiht, landet der Start automatisch dahinter.
     * Eine Ablehnung des Stops ("keine aktive Buchung") heißt: am Server läuft
     * nichts – dann einfach starten. Lehnt der Server den Start ab, bleibt die
     * alte Buchung sichtbar und wird – falls der Stop schon durch war – neu gestartet.
     */
    const wechsleBuchung = async (
        neueStartDaten: Record<string, unknown>,
        neueSession: (startZeit: string, id: unknown) => Session,
    ) => {
        if (!activeSession) return
        const token = localStorage.getItem('zeiterfassung_token')
        const alteSession = { ...activeSession } // Backup for rollback
        // eslint-disable-next-line react-hooks/purity -- wechsleBuchung läuft nur nach einem Tipp (Event-Handler), nie beim Rendern
        const elapsedMinutes = Math.floor((Date.now() - new Date(activeSession.startTime).getTime()) / 60000)
        const isWorkSession = !isAbwesenheitOderPause(activeSession.typ)
        const stopZeit = new Date().toISOString()

        const stop = await sendeBuchung('stop', { token }, {
            originalTime: stopZeit,
            operationId: createOperationId(),
            durationMinutes: isWorkSession && elapsedMinutes > 0 ? elapsedMinutes : undefined,
            ablehnungInReparaturListe: false,
        })

        const startZeit = new Date().toISOString()
        const start = await sendeBuchung('start', { token, ...neueStartDaten }, {
            originalTime: startZeit,
            operationId: createOperationId(),
            ablehnungInReparaturListe: false,
        })

        if (start.art === 'abgelehnt') {
            console.warn('⚠️ Start beim Wechsel abgelehnt - alte Buchung bleibt')
            if (stop.art === 'ok') {
                const wiederhergestellt = await sendeBuchung('start', {
                    token,
                    projektId: alteSession.projektId,
                    arbeitsgangId: alteSession.arbeitsgangId,
                    produktkategorieId: alteSession.produktkategorieId ?? null,
                }, { ablehnungInReparaturListe: false })
                if (wiederhergestellt.art === 'abgelehnt') {
                    console.error('❌ Alte Buchung konnte nicht wiederhergestellt werden')
                }
            }
            toast.error(`Wechsel nicht möglich: ${start.meldung}`)
            localStorage.setItem('zeiterfassung_active_session', JSON.stringify(alteSession))
            setActiveSession(alteSession)
            return
        }

        const session = neueSession(
            startZeitAusErgebnis(start, startZeit),
            // eslint-disable-next-line react-hooks/purity -- wie oben: nur im Event-Handler
            start.art === 'ok' ? start.daten.id ?? null : 'offline-' + Date.now(),
        )
        localStorage.setItem('zeiterfassung_active_session', JSON.stringify(session))
        setActiveSession(session)
    }

    // Switch to new Kategorie (stop old, start new with same activity)
    const handleSwitchKategorie = async (newKategorie: { id: number; name: string }) => {
        if (!activeSession) return
        setSwitching(true)
        const aktuell = activeSession
        try {
            await wechsleBuchung({
                projektId: aktuell.projektId,
                arbeitsgangId: aktuell.arbeitsgangId,
                produktkategorieId: newKategorie.id,
            }, (startZeit, id) => ({
                ...aktuell,
                id,
                produktkategorieId: newKategorie.id,
                produktkategorieName: newKategorie.name,
                startTime: startZeit,
            } as Session))
        } catch {
            toast.error('Der Wechsel konnte auf dem Handy nicht gespeichert werden. Bitte noch einmal versuchen.')
        } finally {
            setSwitching(false)
            setShowKategorieSwitch(false)
            loadHeuteGearbeitet()
        }
    }

    // Switch to new Arbeitsgang (stop old, start new) - optional auf einem über "Ändern" gewählten Projekt
    const handleSwitchArbeitsgang = async (newArbeitsgang: Arbeitsgang) => {
        if (!activeSession) return
        setSwitching(true)
        const ziel = projektFuerTaetigkeitswechsel(activeSession, projekte, selectedProjektId)
        try {
            await wechsleBuchung({
                projektId: ziel.projektId,
                arbeitsgangId: newArbeitsgang.id,
                produktkategorieId: ziel.produktkategorieId,
            }, (startZeit, id) => ({
                id,
                ...ziel,
                arbeitsgangId: newArbeitsgang.id,
                arbeitsgangName: newArbeitsgang.beschreibung,
                startTime: startZeit,
                typ: null,
            } as Session))
        } catch {
            toast.error('Der Wechsel konnte auf dem Handy nicht gespeichert werden. Bitte noch einmal versuchen.')
        } finally {
            setSwitching(false)
            setShowArbeitsgangSwitch(false)
            // Refresh heute gearbeitet to include the newly added offline minutes
            loadHeuteGearbeitet()
        }
    }

    return (
        <div className="h-full bg-slate-50 flex flex-col">
            {/* Header */}
            <header className="bg-white border-b border-slate-200 px-4 py-4 safe-area-top">
                <div className="flex items-center justify-between">
                    {/* Fette Begrüßung zuerst, Datum darunter: iOS 26 legt unter der Statusleiste
                        einen Weichzeichner über die obersten Zeilen – kleiner grauer Text wirkt dort
                        unscharf, die fette Überschrift nicht. */}
                    <div>
                        <h1 className="text-xl font-bold text-slate-900">{getDailyGreeting()}, {displayName}!</h1>
                        <p className="text-sm text-slate-500">{formatDate(new Date())}</p>
                    </div>
                    <div className="flex items-center gap-1">
                        <NachrichtenGlocke mitarbeiterId={mitarbeiter?.id} neuLadenAusloeser={abgleichNummer} />
                        <button
                            onClick={() => navigate('/einstellungen')}
                            aria-label="Einstellungen"
                            className="flex min-h-11 min-w-11 items-center justify-center hover:bg-slate-100 rounded-lg transition-all active:scale-95 mr-1"
                        >
                            <Settings className="w-5 h-5 text-slate-600" />
                        </button>
                        <NetworkStatusBadge syncStatus={syncStatus} onSync={onSync} />
                    </div>
                </div>
            </header>

            {/* Main Content */}
            <main className="flex-1 p-4 space-y-4 overflow-y-auto pb-16">

                {/* Reparatur-Banner: zeigt Buchungen, die der Server abgelehnt hat */}
                {failedEntries.length > 0 && (
                    <button
                        onClick={() => setShowFailedModal(true)}
                        className="w-full bg-amber-50 border border-amber-300 text-amber-900 rounded-2xl p-4 shadow-sm flex items-center gap-3 hover:bg-amber-100 transition-colors text-left"
                    >
                        <div className="w-10 h-10 bg-amber-200 rounded-xl flex items-center justify-center flex-shrink-0">
                            <Wrench className="w-5 h-5 text-amber-700" />
                        </div>
                        <div className="flex-1">
                            <p className="font-semibold">
                                {failedEntries.length === 1
                                    ? '1 Buchung konnte nicht gespeichert werden'
                                    : `${failedEntries.length} Buchungen konnten nicht gespeichert werden`}
                            </p>
                            <p className="text-sm text-amber-800">
                                Tippe hier, um sie zu prüfen
                            </p>
                        </div>
                        <ChevronRight className="w-5 h-5 text-amber-700 flex-shrink-0" />
                    </button>
                )}

                {/* Aktive Buchung oder Start Button */}
                {activeSession ? (
                    <div className="bg-white border border-rose-200 rounded-2xl p-5 shadow-sm">
                        <div className="flex items-center justify-between mb-4">
                            <div className="flex items-center gap-2">
                                <div className="w-3 h-3 bg-rose-500 rounded-full animate-pulse"></div>
                                <span className="text-sm font-medium text-rose-600">Aktive Buchung</span>
                            </div>
                            <span className="text-2xl font-mono font-bold text-slate-900">{elapsedTime}</span>
                        </div>

                        <div className="mb-4">
                            <p className="text-lg font-semibold text-slate-900">
                                {isAbwesenheitOderPause(activeSession.typ) ? (
                                    activeSession.typ === 'URLAUB' ? '✈ Urlaub' :
                                        activeSession.typ === 'KRANKHEIT' ? '🩺 Krankheit' :
                                            activeSession.typ === 'FORTBILDUNG' ? '🎓 Fortbildung' :
                                                '☕ Pause'
                                ) : (
                                    <span className="flex items-center gap-2">
                                        <Hammer className="w-5 h-5 text-rose-600" />
                                        {activeSession.projektName || 'Zeitbuchung'}
                                    </span>
                                )}
                            </p>
                            {activeSession.kundenName && !isAbwesenheitOderPause(activeSession.typ) && (
                                <p className="text-sm text-slate-600">{activeSession.kundenName} • {activeSession.auftragsnummer}</p>
                            )}
                            {/* Produktkategorie anzeigen wenn vorhanden - über Tätigkeit */}
                            {activeSession?.produktkategorieName && !isAbwesenheitOderPause(activeSession.typ) && (
                                <p className="text-sm text-rose-600 font-medium">
                                    📦 {activeSession.produktkategorieName}
                                </p>
                            )}
                            <p className="text-slate-500">
                                {activeSession.arbeitsgangName || (isAbwesenheitOderPause(activeSession.typ) ? 'Abwesenheit' : 'Keine Tätigkeit')}
                            </p>
                            <p className="text-sm text-slate-400 mt-1">
                                Gestartet um {formatTime(new Date(activeSession.startTime))}
                            </p>
                        </div>

                        {/* Action Buttons */}
                        {hatZeitkonto && (
                        <div className="grid grid-cols-3 gap-2 mb-3">
                            {isAbwesenheitOderPause(activeSession.typ) ? (
                                <>
                                    <button
                                        onClick={handleResumeFromPause}
                                        disabled={actionInProgress}
                                        className="bg-rose-600 hover:bg-rose-700 text-white font-medium py-3 rounded-xl flex items-center justify-center gap-2 transition-colors col-span-2 disabled:opacity-60 disabled:pointer-events-none"
                                    >
                                        <Play className="w-4 h-4" />
                                        Arbeit fortsetzen
                                    </button>
                                </>
                            ) : (
                                <>
                                    <button
                                        onClick={handleOpenArbeitsgangSwitch}
                                        className="bg-slate-100 hover:bg-slate-200 text-slate-700 font-medium py-3 rounded-xl flex items-center justify-center gap-2 transition-colors text-xs"
                                    >
                                        <ArrowRightLeft className="w-4 h-4" />
                                        Tätigkeit
                                    </button>
                                    <button
                                        onClick={handleSwitchProjekt}
                                        className="bg-slate-100 hover:bg-slate-200 text-slate-700 font-medium py-3 rounded-xl flex items-center justify-center gap-2 transition-colors text-xs"
                                    >
                                        <ArrowRightLeft className="w-4 h-4" />
                                        Auftrag
                                    </button>
                                    <button
                                        onClick={handleOpenKategorieSwitch}
                                        className="bg-slate-100 hover:bg-slate-200 text-slate-700 font-medium py-3 rounded-xl flex items-center justify-center gap-2 transition-colors text-xs"
                                    >
                                        <ArrowRightLeft className="w-4 h-4" />
                                        Kategorie
                                    </button>
                                </>
                            )}
                        </div>
                        )}

                        {/* Pause Button (above Gehen, same size) */}
                        {hatZeitkonto && !isAbwesenheitOderPause(activeSession.typ) && (
                            <button
                                onClick={handlePause}
                                disabled={actionInProgress}
                                className="w-full bg-rose-50 hover:bg-rose-100 text-rose-700 border border-rose-200 font-semibold py-4 rounded-xl flex items-center justify-center gap-2 transition-colors mb-2 disabled:opacity-60 disabled:pointer-events-none"
                            >
                                <Clock className="w-5 h-5" />
                                Pause
                            </button>
                        )}

                        {/* Gehen Button */}
                        <button
                            onClick={handleStopSession}
                            disabled={actionInProgress}
                            className="w-full bg-rose-600 hover:bg-rose-700 text-white font-semibold py-4 rounded-xl flex items-center justify-center gap-2 transition-colors disabled:opacity-60 disabled:pointer-events-none"
                        >
                            <LogOut className="w-5 h-5" />
                            Gehen (Feierabend)
                        </button>
                    </div>
                ) : hatZeitkonto ? (
                    <button
                        onClick={() => navigate('/zeiterfassung')}
                        className="w-full bg-rose-600 hover:bg-rose-700 text-white rounded-2xl p-6 shadow-sm transition-colors"
                    >
                        <div className="flex items-center justify-between">
                            <div className="flex items-center gap-4">
                                <div className="w-14 h-14 bg-white/20 rounded-xl flex items-center justify-center">
                                    <Play className="w-7 h-7 text-white" />
                                </div>
                                <div className="text-left">
                                    <p className="text-lg font-bold text-white">Zeit erfassen</p>
                                    <p className="text-rose-200 text-sm">Neue Buchung starten</p>
                                </div>
                            </div>
                            <ChevronRight className="w-6 h-6 text-white/70" />
                        </div>
                    </button>
                ) : (
                    <div className="rounded-2xl border border-amber-200 bg-amber-50 p-4 shadow-sm" role="status">
                        <div className="flex items-start gap-3">
                            <AlertTriangle className="mt-0.5 h-5 w-5 flex-shrink-0 text-amber-700" aria-hidden="true" />
                            <div>
                                <p className="font-semibold text-slate-900">Zeiterfassung derzeit nicht verfügbar</p>
                                <p className="mt-1 text-sm text-slate-700">
                                    {zeitkontoWirdGeprueft ? 'Die Arbeitszeit-Einrichtung wird geprüft.' : zeitkontoHinweis(zeitkontoStatus)}
                                </p>
                                {zeitkontoFehler && <p className="mt-1 text-sm text-amber-800">{zeitkontoFehler}</p>}
                            </div>
                        </div>
                    </div>
                )}

                {/* Quick Actions Grid - 2x2 */}
                <div className="grid grid-cols-2 gap-2">
                    <button
                        onClick={() => navigate('/projekte')}
                        className="bg-white border border-slate-200 rounded-xl p-4 hover:border-rose-200 hover:shadow-md transition-all text-center"
                    >
                        <div className="w-10 h-10 bg-rose-50 rounded-lg flex items-center justify-center mx-auto mb-2">
                            <FolderOpen className="w-5 h-5 text-rose-600" />
                        </div>
                        <p className="text-sm font-semibold text-slate-900">Projekte</p>
                    </button>

                    <button
                        onClick={() => navigate('/anfragen')}
                        className="bg-white border border-slate-200 rounded-xl p-4 hover:border-rose-200 hover:shadow-md transition-all text-center"
                    >
                        <div className="w-10 h-10 bg-amber-50 rounded-lg flex items-center justify-center mx-auto mb-2">
                            <FolderOpen className="w-5 h-5 text-amber-600" />
                        </div>
                        <p className="text-sm font-semibold text-slate-900">Anfragen</p>
                    </button>

                    <button
                        onClick={() => navigate('/kunden')}
                        className="bg-white border border-slate-200 rounded-xl p-4 hover:border-rose-200 hover:shadow-md transition-all text-center"
                    >
                        <div className="w-10 h-10 bg-blue-50 rounded-lg flex items-center justify-center mx-auto mb-2">
                            <Users className="w-5 h-5 text-blue-600" />
                        </div>
                        <p className="text-sm font-semibold text-slate-900">Kunden</p>
                    </button>

                    <button
                        onClick={() => navigate('/lieferanten')}
                        className="bg-white border border-slate-200 rounded-xl p-4 hover:border-rose-200 hover:shadow-md transition-all text-center"
                    >
                        <div className="w-10 h-10 bg-emerald-50 rounded-lg flex items-center justify-center mx-auto mb-2">
                            <Users className="w-5 h-5 text-emerald-600" />
                        </div>
                        <p className="text-sm font-semibold text-slate-900">Lieferanten</p>
                    </button>
                </div>

                {/* Full-width actions */}
                <div className="space-y-3">

                    {darfBelegeScannen && (
                        <button
                            onClick={() => navigate('/belege')}
                            className="w-full bg-white border border-slate-200 rounded-xl p-4 hover:border-rose-200 hover:shadow-md transition-all text-left flex items-center gap-4 active:scale-[0.99]"
                        >
                            <div className="w-10 h-10 bg-rose-50 rounded-lg flex items-center justify-center">
                                <Receipt className="w-5 h-5 text-rose-600" />
                            </div>
                            <div className="flex-1">
                                <p className="font-semibold text-slate-900">Belege scannen</p>
                                <p className="text-sm text-slate-500">Schnellscan – Buchhaltung validiert am PC</p>
                            </div>
                            <ChevronRight className="w-5 h-5 text-slate-300" />
                        </button>
                    )}

                    <button
                        onClick={() => navigate('/kalender')}
                        className="w-full bg-white border border-slate-200 rounded-xl p-4 hover:border-rose-200 hover:shadow-md transition-all text-left flex items-center gap-4"
                    >
                        <div className="w-10 h-10 bg-violet-50 rounded-lg flex items-center justify-center">
                            <Calendar className="w-5 h-5 text-violet-600" />
                        </div>
                        <div>
                            <p className="font-semibold text-slate-900">Kalender</p>
                            <p className="text-sm text-slate-500">Termine & Einladungen</p>
                        </div>
                    </button>

                    {hatZeitkonto && <button
                        onClick={() => navigate('/urlaub')}
                        className="w-full bg-white border border-slate-200 rounded-xl p-4 hover:border-rose-200 hover:shadow-md transition-all text-left flex items-center gap-4"
                    >
                        <div className="w-10 h-10 bg-rose-50 rounded-lg flex items-center justify-center">
                            <Plane className="w-5 h-5 text-rose-600" />
                        </div>
                        <div>
                            <p className="font-semibold text-slate-900">Abwesenheit beantragen</p>
                            <p className="text-sm text-slate-500">Urlaub, Zeitausgleich, Krankheit</p>
                        </div>
                    </button>}

                    {hatZeitkonto && <button
                        onClick={() => navigate('/salden')}
                        className="w-full bg-white border border-slate-200 rounded-xl p-4 hover:border-rose-200 hover:shadow-md transition-all text-left flex items-center gap-4"
                    >
                        <div className="w-10 h-10 bg-emerald-50 rounded-lg flex items-center justify-center">
                            <Clock className="w-5 h-5 text-emerald-600" />
                        </div>
                        <div>
                            <p className="font-semibold text-slate-900">Saldenauswertung</p>
                            <p className="text-sm text-slate-500">Urlaub & Stunden übersicht</p>
                        </div>
                    </button>}
                </div>

                {/* Heute Stats */}
                {hatZeitkonto && <button
                    onClick={() => navigate('/tagesbuchungen')}
                    className="w-full bg-white border border-slate-200 rounded-xl p-4 text-left hover:border-rose-200 hover:shadow-sm transition-all"
                >
                    <div className="flex items-center gap-2 mb-3">
                        <Clock className="w-4 h-4 text-slate-400" />
                        <span className="text-sm font-medium text-slate-600">Heute gearbeitet</span>
                        <ChevronRight className="w-4 h-4 text-slate-400 ml-auto" />
                    </div>
                    <p className="text-2xl font-bold text-slate-900">{heuteStunden}h {heuteMinuten.toString().padStart(2, '0')}min</p>
                </button>}

                {/* Langzeitkrankmeldung-Karte: informiert, solange eine Phase läuft.
                    NUR LESEND - keine Bearbeitung hier, kein Button, kein Formular,
                    kein Schreib-Request. Eine Langzeitkrankmeldung wird ausschließlich
                    am PC angelegt/geändert (Vorgabe des Projektinhabers, 08.09.2026).
                    Neutrale Information, keine Warnung -> indigo (--info-Rolle im
                    Design-System, kein teal - das kommt dort nirgends vor). Aufbau
                    folgt den Nachbarkarten (Kalender, Abwesenheit beantragen,
                    Saldenauswertung): weiße Karte mit getönter Icon-Kachel. */}
                {langzeitFall && (
                    <div className="w-full bg-white border border-slate-200 rounded-xl p-4 shadow-sm flex items-center gap-4">
                        <div className="w-10 h-10 bg-indigo-50 rounded-lg flex items-center justify-center flex-shrink-0">
                            <Stethoscope className="w-5 h-5 text-indigo-600" />
                        </div>
                        <div className="flex-1 min-w-0">
                            <p className="font-semibold text-slate-900">
                                {langzeitFall.phase === 'WIEDEREINGLIEDERUNG' && langzeitFall.heuteGeplanteStunden !== null
                                    ? `${langzeitFall.phaseLabel} — heute ${formatStundenDe(langzeitFall.heuteGeplanteStunden)} Stunden geplant`
                                    : `${langzeitFall.phaseLabel} — seit ${formatDatumDe(langzeitFall.seit)}`}
                            </p>
                        </div>
                    </div>
                )}

            </main>

            {/* Arbeitsgang Switch Modal */}
            {showArbeitsgangSwitch && (
                <div className="fixed inset-0 bg-black/50 flex items-end z-50">
                    <div className="bg-white w-full rounded-t-3xl max-h-[85vh] flex flex-col">
                        <div className="p-4 border-b border-slate-200 flex items-center justify-between">
                            <h2 className="text-lg font-bold text-slate-900">
                                {viewMode === 'projects' ? 'Projekt wählen' : 'Tätigkeit wählen'}
                            </h2>
                            <button
                                onClick={() => {
                                    if (viewMode === 'projects') setViewMode('activities')
                                    else setShowArbeitsgangSwitch(false)
                                }}
                                className="text-slate-500 hover:text-slate-700 text-sm"
                            >
                                {viewMode === 'projects' ? 'Zurück' : 'Abbrechen'}
                            </button>
                        </div>

                        <div className="flex-1 overflow-y-auto overflow-x-hidden p-4 space-y-2">
                            {switching ? (
                                <div className="flex items-center justify-center py-8">
                                    <Loader2 className="w-6 h-6 animate-spin text-rose-600" />
                                </div>
                            ) : viewMode === 'projects' ? (
                                <div className="space-y-2">
                                    {projekte.map(p => (
                                        <button
                                            key={p.id}
                                            onClick={() => {
                                                setSelectedProjektId(p.id)
                                                setViewMode('activities')
                                            }}
                                            className={`w-full border rounded-xl p-4 text-left transition-all ${selectedProjektId === p.id
                                                ? 'bg-rose-50 border-rose-300 ring-1 ring-rose-300'
                                                : 'bg-slate-50 border-slate-200 hover:border-rose-300'
                                                }`}
                                        >
                                            <p className="font-bold text-slate-900">{p.name}</p>
                                            <p className="text-sm text-slate-500">{p.beschreibung}</p>
                                        </button>
                                    ))}
                                </div>
                            ) : (
                                <>
                                    {/* Current Project Info & Switch Button */}
                                    <div className="bg-rose-50 rounded-xl p-3 mb-4 flex items-center justify-between">
                                        <div>
                                            <p className="text-xs text-rose-600 uppercase font-semibold">Aktuelles Projekt</p>
                                            <p className="font-bold text-slate-900">
                                                {projekte.find(p => p.id === selectedProjektId)?.name || activeSession?.projektName}
                                            </p>
                                        </div>
                                        <button
                                            onClick={() => setViewMode('projects')}
                                            className="text-sm text-rose-700 font-medium hover:underline"
                                        >
                                            Ändern
                                        </button>
                                    </div>

                                    <p className="text-sm text-slate-500 font-medium mb-2 pl-1">Verfügbare Tätigkeiten:</p>

                                    {arbeitsgaenge.map(ag => (
                                        <button
                                            key={ag.id}
                                            onClick={() => handleSwitchArbeitsgang(ag)}
                                            className="w-full bg-slate-50 border border-slate-200 rounded-xl p-4 text-left hover:border-rose-300 hover:bg-rose-50 transition-all mb-2"
                                        >
                                            <p className="font-medium text-slate-900">{ag.beschreibung}</p>
                                        </button>
                                    ))}
                                </>
                            )}
                        </div>
                    </div>
                </div>
            )}

            {/* Kategorie Switch Modal */}
            {showKategorieSwitch && (
                <div className="fixed inset-0 bg-black/50 flex items-end z-50">
                    <div className="bg-white w-full rounded-t-3xl max-h-[85vh] flex flex-col">
                        <div className="p-4 border-b border-slate-200 flex items-center justify-between">
                            <h2 className="text-lg font-bold text-slate-900">
                                Kategorie wählen
                            </h2>
                            <button
                                onClick={() => setShowKategorieSwitch(false)}
                                className="text-slate-500 hover:text-slate-700 text-sm"
                            >
                                Abbrechen
                            </button>
                        </div>

                        <div className="flex-1 overflow-y-auto overflow-x-hidden p-4 space-y-2">
                            {switching ? (
                                <div className="flex items-center justify-center py-8">
                                    <Loader2 className="w-6 h-6 animate-spin text-rose-600" />
                                </div>
                            ) : (
                                <>
                                    {/* Current Category Info */}
                                    {activeSession?.produktkategorieName && (
                                        <div className="bg-purple-50 rounded-xl p-3 mb-4">
                                            <p className="text-xs text-purple-600 uppercase font-semibold">Aktuelle Kategorie</p>
                                            <p className="font-bold text-slate-900">{activeSession.produktkategorieName}</p>
                                        </div>
                                    )}

                                    <p className="text-sm text-slate-500 font-medium mb-2 pl-1">Verfügbare Kategorien:</p>

                                    {kategorien.length === 0 ? (
                                        <p className="text-slate-400 text-center py-4">Keine Kategorien für dieses Projekt</p>
                                    ) : (
                                        kategorien.map(kat => (
                                            <button
                                                key={kat.id}
                                                onClick={() => handleSwitchKategorie(kat)}
                                                className={`w-full border rounded-xl p-4 text-left transition-all mb-2 ${activeSession?.produktkategorieId === kat.id
                                                        ? 'bg-purple-50 border-purple-300 ring-1 ring-purple-300'
                                                        : 'bg-slate-50 border-slate-200 hover:border-purple-300 hover:bg-purple-50'
                                                    }`}
                                            >
                                                <p className="font-medium text-slate-900">{kat.name}</p>
                                            </button>
                                        ))
                                    )}
                                </>
                            )}
                        </div>
                    </div>
                </div>
            )}

            {showFailedModal && (
                <FailedEntriesModal
                    entries={failedEntries}
                    onClose={() => setShowFailedModal(false)}
                    onDismiss={handleDismissFailedEntry}
                />
            )}
        </div>
    )
}
