import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Play, FolderOpen, Users, Clock, Loader2, ChevronRight, ArrowRightLeft, LogOut, Plane, AlertTriangle, Calendar, Hammer, Zap } from 'lucide-react'
import { buildBookingRequestPayload, createOperationId, OfflineService } from '../services/OfflineService'
import NetworkStatusBadge from '../components/NetworkStatusBadge'
import { shouldIncludeCurrentSessionMinutes, shouldIncludeOfflineCompletedMinutes } from './DashboardPage.logic'

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
    onSync?: () => void
}

export default function DashboardPage({ mitarbeiter, syncStatus, onSync }: DashboardPageProps) {
    const navigate = useNavigate()
    const [activeSession, setActiveSession] = useState<Session | null>(null)
    const [elapsedTime, setElapsedTime] = useState('00:00:00')
    const [showArbeitsgangSwitch, setShowArbeitsgangSwitch] = useState(false)
    const [arbeitsgaenge, setArbeitsgaenge] = useState<Arbeitsgang[]>([])
    const [switching, setSwitching] = useState(false)
    const [heuteStunden, setHeuteStunden] = useState(0)
    const [heuteMinuten, setHeuteMinuten] = useState(0)

    // Checkbox/Switch logic
    const [viewMode, setViewMode] = useState<'activities' | 'projects'>('activities')
    const [projekte, setProjekte] = useState<ProjektSummary[]>([])
    const [selectedProjektId, setSelectedProjektId] = useState<number | null>(null)

    // Kategorie switch modal
    const [showKategorieSwitch, setShowKategorieSwitch] = useState(false)
    const [kategorien, setKategorien] = useState<Array<{ id: number; name: string }>>([])

    // Urlaubsverfall-Warnung
    interface UrlaubsVerfallWarnung {
        resturlaubTage: number
        verfallsDatum: string
        verfallsJahr: number
        tageVerbleibend: number
        dringend: boolean
    }
    const [urlaubsWarnung, setUrlaubsWarnung] = useState<UrlaubsVerfallWarnung | null>(null)



    // Load today's hours - OFFLINE FIRST: show local data immediately
    const loadHeuteGearbeitet = async () => {
        const token = localStorage.getItem('zeiterfassung_token')
        if (!token) return

        // 1. Get server/cached total for today.
        // Fresh server data already includes a running booking, cached data does not.
        const heuteData = await OfflineService.getHeuteGearbeitet(token)
        const serverMinuten = (heuteData.stunden || 0) * 60 + (heuteData.minuten || 0)

        const pendingCount = await OfflineService.getPendingCount()

        // 2. Read local session and cooldown state.
        let currentSessionMinuten = 0
        const storedSession = localStorage.getItem('zeiterfassung_active_session')
        const startSyncedAt = localStorage.getItem('zeiterfassung_start_synced_at')
        const hasRecentStartSync = Boolean(
            startSyncedAt && storedSession && Date.now() - parseInt(startSyncedAt, 10) < 15000,
        )

        if (storedSession) {
            try {
                const session = JSON.parse(storedSession)
                const includeCurrentSession = shouldIncludeCurrentSessionMinutes(
                    heuteData.fromCache,
                    pendingCount,
                    hasRecentStartSync,
                )

                // Only count work sessions, not pauses, and only when the local
                // state is ahead of or replacing the server state.
                if (includeCurrentSession && session.startTime && !isAbwesenheitOderPause(session.typ)) {
                    const start = new Date(session.startTime)
                    const now = new Date()
                    currentSessionMinuten = Math.floor((now.getTime() - start.getTime()) / 60000)
                }
            } catch { /* ignore */ }
        }

        // 3. Add locally completed offline bookings only when server data is stale
        // or when unsynced entries still exist. Otherwise they would be double-counted
        // immediately after reconnect/sync.
        const offlineMinuten = shouldIncludeOfflineCompletedMinutes(heuteData.fromCache, pendingCount)
            ? await OfflineService.getOfflineHeuteMinuten()
            : 0

        // Total = server/cached total + local-only completed work + local running session
        const totalMinuten = serverMinuten + offlineMinuten + currentSessionMinuten
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
                    // Safe to trust the server here.
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

    // Load vacation expiration warning
    const loadUrlaubsVerfallWarnung = async () => {
        const token = localStorage.getItem('zeiterfassung_token')
        if (!token) return
        try {
            const res = await fetch(`/api/zeiterfassung/urlaubsverfall/${token}`)
            if (res.ok) {
                const data = await res.json()
                // Only set warning if we have data (resturlaubTage > 0)
                if (data && data.resturlaubTage && data.resturlaubTage > 0) {
                    setUrlaubsWarnung(data)
                } else {
                    setUrlaubsWarnung(null)
                }
            }
        } catch {
            console.log('Urlaubsverfall-Warnung konnte nicht geladen werden')
        }
    }

    useEffect(() => {
        // Load active session from server (primary) or localStorage (fallback)
        loadActiveSession()
        // Load today's hours
        loadHeuteGearbeitet()
        // Load vacation expiration warning
        loadUrlaubsVerfallWarnung()

        // NOTE: Online/Heartbeat handlers wurden nach App.tsx verschoben
        // um globalen Sync auf allen Seiten zu ermöglichen
    }, [])

    // Reload stats when sync completes (e.g. after offline batch upload)
    useEffect(() => {
        if (syncStatus === 'done') {
            // Reload both session and hours from server after successful sync
            loadActiveSession()
            loadHeuteGearbeitet()
        }
    }, [syncStatus])

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

    // Stop current session (Pause/Buchung beenden)
    const handleStopSession = async () => {
        if (!activeSession) return

        const token = localStorage.getItem('zeiterfassung_token')
        const stopTime = new Date().toISOString()
        const stopOperationId = createOperationId()

        // Calculate elapsed minutes for offline tracking (only for work, not pause)
        const start = new Date(activeSession.startTime)
        const now = new Date()
        const elapsedMinutes = Math.floor((now.getTime() - start.getTime()) / 60000)
        const isWorkSession = !isAbwesenheitOderPause(activeSession.typ)

        try {
            const controller = new AbortController()
            const timeoutId = setTimeout(() => controller.abort(), 3000)

            const res = await fetch('/api/zeiterfassung/stop', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(buildBookingRequestPayload({ token }, stopTime, stopOperationId)),
                signal: controller.signal
            })
            clearTimeout(timeoutId)

            if (res.ok) {
                const data = await res.json()
                console.log('Buchung gestoppt:', data.stunden, 'Stunden')
                // Online stop successful - clear any offline minutes as server has the real data now
                await OfflineService.clearOfflineHeuteMinuten()
            } else {
                throw new Error('Server error');
            }
        } catch {
            console.log('Offline (oder Timeout) - speichere Stop-Event lokal')
            await OfflineService.addPendingEntryWithOperationId('stop', { token }, stopTime, stopOperationId)
            // Track offline worked minutes (only for actual work, not pauses)
            if (isWorkSession && elapsedMinutes > 0) {
                await OfflineService.addOfflineWorkedMinutes(elapsedMinutes)
            }
        }

        localStorage.removeItem('zeiterfassung_active_session')
        setActiveSession(null)
        setElapsedTime('00:00:00')
        // Reload today's hours
        loadHeuteGearbeitet()
    }

    // Start pause (stop current work, start pause booking)
    const handlePause = async () => {
        const token = localStorage.getItem('zeiterfassung_token')
        if (!token) return
        const pauseTime = new Date().toISOString()
        const pauseOperationId = createOperationId()

        try {
            const controller = new AbortController()
            const timeoutId = setTimeout(() => controller.abort(), 3000)

            const res = await fetch('/api/zeiterfassung/pause', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(buildBookingRequestPayload({ token }, pauseTime, pauseOperationId)),
                signal: controller.signal
            })
            clearTimeout(timeoutId)

            if (res.ok) {
                const data = await res.json()
                console.log('Pause gestartet:', data)
                // Update session to show pause
                const pauseSession: Session = {
                    projektId: null,
                    projektName: null,
                    kundenName: null,
                    auftragsnummer: null,
                    arbeitsgangId: null,
                    arbeitsgangName: null,
                    startTime: data.startZeit,
                    typ: 'PAUSE',
                }
                localStorage.setItem('zeiterfassung_active_session', JSON.stringify(pauseSession))
                setActiveSession(pauseSession)
            } else {
                const errorData = await res.json()
                console.error('Pause Fehler:', errorData.error)
            }
        } catch {
            console.log('Offline - speichere Pause-Event lokal')
            await OfflineService.addPendingEntryWithOperationId('pause', { token }, pauseTime, pauseOperationId)
            // Optimistic UI update for offline pause
            const pauseSession: Session = {
                projektId: null,
                projektName: null,
                kundenName: null,
                auftragsnummer: null,
                arbeitsgangId: null,
                arbeitsgangName: null,
                startTime: new Date().toISOString(),
                typ: 'PAUSE',
            }
            localStorage.setItem('zeiterfassung_active_session', JSON.stringify(pauseSession))
            setActiveSession(pauseSession)
        }
    }

    // Resume from pause - stop the active PAUSE booking, then navigate to start work
    const handleResumeFromPause = async () => {
        const token = localStorage.getItem('zeiterfassung_token')
        const stopTime = new Date().toISOString()
        const stopOperationId = createOperationId()

        try {
            const controller = new AbortController()
            const timeoutId = setTimeout(() => controller.abort(), 3000)

            const res = await fetch('/api/zeiterfassung/stop', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(buildBookingRequestPayload({ token }, stopTime, stopOperationId)),
                signal: controller.signal
            })
            clearTimeout(timeoutId)

            if (!res.ok) {
                console.warn('Stop-Aufruf nicht erfolgreich, speichere für später')
                await OfflineService.addPendingEntryWithOperationId('stop', { token }, stopTime, stopOperationId)
            }
        } catch {
            console.log('Offline - speichere Stop-Event für Pause lokal')
            await OfflineService.addPendingEntryWithOperationId('stop', { token }, stopTime, stopOperationId)
        }

        // Clear local pause session and navigate to time tracking
        localStorage.removeItem('zeiterfassung_active_session')
        setActiveSession(null)
        navigate('/zeiterfassung')
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

    // Switch to new Kategorie (stop old, start new with same activity)
    const handleSwitchKategorie = async (newKategorie: { id: number; name: string }) => {
        if (!activeSession) return
        setSwitching(true)

        const token = localStorage.getItem('zeiterfassung_token')
        const oldSession = { ...activeSession } // Backup for rollback

        // Calculate elapsed minutes for offline tracking
        const start = new Date(activeSession.startTime)
        const now = new Date()
        const elapsedMinutes = Math.floor((now.getTime() - start.getTime()) / 60000)
        const isWorkSession = !isAbwesenheitOderPause(activeSession.typ)
        const stopOperationTime = new Date().toISOString()
        const stopOperationId = createOperationId()
        const startOperationTime = new Date().toISOString()
        const startOperationId = createOperationId()

        // stopRes vor try deklarieren, damit im catch geprüft werden kann
        // ob der Stop online bereits erfolgreich war (Race-Condition-Fix)
        let stopRes: Response | null = null

        try {
            // Online: Stop old booking, start new with different kategorie
            const stopController = new AbortController()
            const stopTimeout = setTimeout(() => stopController.abort(), 3000)

            stopRes = await fetch('/api/zeiterfassung/stop', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(buildBookingRequestPayload({ token }, stopOperationTime, stopOperationId)),
                signal: stopController.signal
            })
            clearTimeout(stopTimeout)

            // Start new booking with same activity but different category
            const startController = new AbortController()
            const startTimeout = setTimeout(() => startController.abort(), 3000)

            const res = await fetch('/api/zeiterfassung/start', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(buildBookingRequestPayload({
                    token,
                    projektId: activeSession.projektId,
                    arbeitsgangId: activeSession.arbeitsgangId,
                    produktkategorieId: newKategorie.id
                }, startOperationTime, startOperationId)),
                signal: startController.signal
            })
            clearTimeout(startTimeout)

            if (res.ok) {
                const data = await res.json()
                const newSession = {
                    ...activeSession,
                    id: data.id,
                    produktkategorieId: newKategorie.id,
                    produktkategorieName: newKategorie.name,
                    startTime: new Date().toISOString(),
                }
                localStorage.setItem('zeiterfassung_active_session', JSON.stringify(newSession))
                setActiveSession(newSession)
            } else {
                // Start failed after stop succeeded - try to restore old booking
                console.warn('⚠️ Kategorie-Start fehlgeschlagen nach Stop - versuche Wiederherstellung')
                if (stopRes.ok) {
                    try {
                        await fetch('/api/zeiterfassung/start', {
                            method: 'POST',
                            headers: { 'Content-Type': 'application/json' },
                            body: JSON.stringify(buildBookingRequestPayload({
                                token,
                                projektId: oldSession.projektId,
                                arbeitsgangId: oldSession.arbeitsgangId,
                                produktkategorieId: oldSession.produktkategorieId ?? null
                            }, new Date().toISOString(), createOperationId()))
                        })
                    } catch { /* best effort */ }
                }
                localStorage.setItem('zeiterfassung_active_session', JSON.stringify(oldSession))
                setActiveSession(oldSession as Session)
            }
        } catch {
            console.log('Offline - Kategorie-Switch wird lokal gespeichert')
            // Track offline worked minutes
            if (isWorkSession && elapsedMinutes > 0) {
                await OfflineService.addOfflineWorkedMinutes(elapsedMinutes)
            }

            // Nur Stop queuen wenn der online Stop NICHT bereits erfolgreich war.
            // Wenn stopRes.ok, hat der Server den Stop schon verarbeitet - ein erneuter
            // offline Stop würde die NEUE Buchung stoppen (Race-Condition).
            if (!stopRes || !stopRes.ok) {
                await OfflineService.addPendingEntryWithOperationId('stop', { token }, stopOperationTime, stopOperationId)
            }
            await OfflineService.addPendingEntryWithOperationId('start', {
                token,
                projektId: activeSession.projektId,
                arbeitsgangId: activeSession.arbeitsgangId,
                produktkategorieId: newKategorie.id
            }, startOperationTime, startOperationId)

            // Optimistic UI update
            const newSession = {
                ...activeSession,
                id: 'offline-' + Date.now(),
                produktkategorieId: newKategorie.id,
                produktkategorieName: newKategorie.name,
                startTime: new Date().toISOString(),
            }
            localStorage.setItem('zeiterfassung_active_session', JSON.stringify(newSession))
            setActiveSession(newSession)
        }

        setSwitching(false)
        setShowKategorieSwitch(false)
        loadHeuteGearbeitet()
    }

    // Switch to new Arbeitsgang (stop old, start new) - only activity changes!
    const handleSwitchArbeitsgang = async (newArbeitsgang: Arbeitsgang) => {
        if (!activeSession) return
        setSwitching(true)

        const token = localStorage.getItem('zeiterfassung_token')
        const oldSession = { ...activeSession } // Backup for rollback

        // Calculate elapsed minutes for offline tracking (only for work, not pause)
        const start = new Date(activeSession.startTime)
        const now = new Date()
        const elapsedMinutes = Math.floor((now.getTime() - start.getTime()) / 60000)
        const isWorkSession = !isAbwesenheitOderPause(activeSession.typ)
        const stopOperationTime = new Date().toISOString()
        const stopOperationId = createOperationId()
        const startOperationTime = new Date().toISOString()
        const startOperationId = createOperationId()

        // stopRes vor try deklarieren, damit im catch geprüft werden kann
        // ob der Stop online bereits erfolgreich war (Race-Condition-Fix)
        let stopRes: Response | null = null

        try {
            // Online Switch Attempt
            // 1. Stop
            const stopController = new AbortController()
            const stopTimeout = setTimeout(() => stopController.abort(), 3000)

            stopRes = await fetch('/api/zeiterfassung/stop', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(buildBookingRequestPayload({ token }, stopOperationTime, stopOperationId)),
                signal: stopController.signal
            })
            clearTimeout(stopTimeout)

            // 2. Start with same project and category, only new activity
            const startController = new AbortController()
            const startTimeout = setTimeout(() => startController.abort(), 3000)

            const res = await fetch('/api/zeiterfassung/start', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(buildBookingRequestPayload({
                    token,
                    projektId: activeSession.projektId,
                    arbeitsgangId: newArbeitsgang.id,
                    produktkategorieId: activeSession.produktkategorieId ?? null
                }, startOperationTime, startOperationId)),
                signal: startController.signal
            })
            clearTimeout(startTimeout)

            if (res.ok) {
                const data = await res.json()
                const newSession = {
                    id: data.id,
                    projektId: activeSession.projektId,
                    projektName: activeSession.projektName,
                    kundenName: activeSession.kundenName,
                    auftragsnummer: activeSession.auftragsnummer,
                    arbeitsgangId: newArbeitsgang.id,
                    arbeitsgangName: newArbeitsgang.beschreibung,
                    produktkategorieId: activeSession.produktkategorieId ?? null,
                    produktkategorieName: activeSession.produktkategorieName ?? null,
                    startTime: new Date().toISOString(),
                    typ: null,
                }
                localStorage.setItem('zeiterfassung_active_session', JSON.stringify(newSession))
                setActiveSession(newSession)
            } else {
                // Start failed after stop succeeded - try to restart old booking
                console.warn('⚠️ Start fehlgeschlagen nach Stop - versuche alte Buchung wiederherzustellen')
                if (stopRes.ok) {
                    try {
                        const restoreRes = await fetch('/api/zeiterfassung/start', {
                            method: 'POST',
                            headers: { 'Content-Type': 'application/json' },
                            body: JSON.stringify(buildBookingRequestPayload({
                                token,
                                projektId: oldSession.projektId,
                                arbeitsgangId: oldSession.arbeitsgangId,
                                produktkategorieId: oldSession.produktkategorieId ?? null
                            }, new Date().toISOString(), createOperationId()))
                        })
                        if (restoreRes.ok) {
                            console.log('✅ Alte Buchung wiederhergestellt')
                        } else {
                            console.error('❌ Alte Buchung konnte nicht wiederhergestellt werden')
                        }
                    } catch {
                        console.error('❌ Restore fehlgeschlagen')
                    }
                }
                // Keep old session visible regardless
                localStorage.setItem('zeiterfassung_active_session', JSON.stringify(oldSession))
                setActiveSession(oldSession as Session)
            }
        } catch {
            console.log('Offline (oder Timeout) - Switch wird lokal gespeichert')
            // Track offline worked minutes from the OLD session
            if (isWorkSession && elapsedMinutes > 0) {
                await OfflineService.addOfflineWorkedMinutes(elapsedMinutes)
            }

            // Nur Stop queuen wenn der online Stop NICHT bereits erfolgreich war.
            // Wenn stopRes.ok, hat der Server den Stop schon verarbeitet - ein erneuter
            // offline Stop würde die NEUE Buchung stoppen (Race-Condition).
            if (!stopRes || !stopRes.ok) {
                await OfflineService.addPendingEntryWithOperationId('stop', { token }, stopOperationTime, stopOperationId)
            }
            await OfflineService.addPendingEntryWithOperationId('start', {
                token,
                projektId: activeSession.projektId,
                arbeitsgangId: newArbeitsgang.id,
                produktkategorieId: activeSession.produktkategorieId ?? null
            }, startOperationTime, startOperationId)

            // Update UI with "Fake" Session (IMMEDIATE optimistic update)
            const newSession = {
                id: 'offline-' + Date.now(),
                projektId: activeSession.projektId,
                projektName: activeSession.projektName,
                kundenName: activeSession.kundenName,
                auftragsnummer: activeSession.auftragsnummer,
                arbeitsgangId: newArbeitsgang.id,
                arbeitsgangName: newArbeitsgang.beschreibung,
                produktkategorieId: activeSession.produktkategorieId ?? null,
                produktkategorieName: activeSession.produktkategorieName ?? null,
                startTime: new Date().toISOString(),
                typ: null,
            }
            localStorage.setItem('zeiterfassung_active_session', JSON.stringify(newSession))
            setActiveSession(newSession)
        }

        setSwitching(false)
        setShowArbeitsgangSwitch(false)
        // Refresh heute gearbeitet to include the newly added offline minutes
        loadHeuteGearbeitet()
    }

    return (
        <div className="h-full bg-slate-50 flex flex-col overflow-hidden">
            {/* Header */}
            <header className="bg-white border-b border-slate-200 px-4 py-4 safe-area-top">
                <div className="flex items-center justify-between">
                    <div>
                        <p className="text-sm text-slate-500">{formatDate(new Date())}</p>
                        <h1 className="text-xl font-bold text-slate-900">{getDailyGreeting()}, {displayName}!</h1>
                    </div>
                    <div className="flex items-center gap-2">
                        <NetworkStatusBadge syncStatus={syncStatus} onSync={onSync} />
                    </div>
                </div>
            </header>

            {/* Main Content */}
            <main className="flex-1 p-4 space-y-4 overflow-y-auto safe-area-bottom pb-16">

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
                        <div className="grid grid-cols-3 gap-2 mb-3">
                            {isAbwesenheitOderPause(activeSession.typ) ? (
                                <>
                                    <button
                                        onClick={handleResumeFromPause}
                                        className="bg-green-100 hover:bg-green-200 text-green-700 font-medium py-3 rounded-xl flex items-center justify-center gap-2 transition-colors col-span-2"
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
                                        className="bg-blue-100 hover:bg-blue-200 text-blue-700 font-medium py-3 rounded-xl flex items-center justify-center gap-2 transition-colors text-xs"
                                    >
                                        <ArrowRightLeft className="w-4 h-4" />
                                        Auftrag
                                    </button>
                                    <button
                                        onClick={handleOpenKategorieSwitch}
                                        className="bg-purple-100 hover:bg-purple-200 text-purple-700 font-medium py-3 rounded-xl flex items-center justify-center gap-2 transition-colors text-xs"
                                    >
                                        <ArrowRightLeft className="w-4 h-4" />
                                        Kategorie
                                    </button>
                                </>
                            )}
                        </div>

                        {/* Pause Button (above Gehen, same size) */}
                        {!isAbwesenheitOderPause(activeSession.typ) && (
                            <button
                                onClick={handlePause}
                                className="w-full bg-amber-100 hover:bg-amber-200 text-amber-700 font-semibold py-4 rounded-xl flex items-center justify-center gap-2 transition-colors mb-2"
                            >
                                <Clock className="w-5 h-5" />
                                Pause
                            </button>
                        )}

                        {/* Gehen Button */}
                        <button
                            onClick={handleStopSession}
                            className="w-full bg-rose-600 hover:bg-rose-700 text-white font-semibold py-4 rounded-xl flex items-center justify-center gap-2 transition-colors"
                        >
                            <LogOut className="w-5 h-5" />
                            Gehen (Feierabend)
                        </button>
                    </div>
                ) : (
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

                    <button
                        onClick={() => navigate('/betriebsmittel')}
                        className="w-full bg-white border border-slate-200 rounded-xl p-4 hover:border-rose-200 hover:shadow-md transition-all text-left flex items-center gap-4"
                    >
                        <div className="w-10 h-10 bg-amber-50 rounded-lg flex items-center justify-center">
                            <Zap className="w-5 h-5 text-amber-600" />
                        </div>
                        <div>
                            <p className="font-semibold text-slate-900">E-Check</p>
                            <p className="text-sm text-slate-500">Betriebsmittelprüfung (DGUV V3)</p>
                        </div>
                    </button>

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

                    <button
                        onClick={() => navigate('/urlaub')}
                        className="w-full bg-white border border-slate-200 rounded-xl p-4 hover:border-rose-200 hover:shadow-md transition-all text-left flex items-center gap-4"
                    >
                        <div className="w-10 h-10 bg-rose-50 rounded-lg flex items-center justify-center">
                            <Plane className="w-5 h-5 text-rose-600" />
                        </div>
                        <div>
                            <p className="font-semibold text-slate-900">Abwesenheit beantragen</p>
                            <p className="text-sm text-slate-500">Urlaub, Krankheit, Fortbildung</p>
                        </div>
                    </button>

                    <button
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
                    </button>
                </div>

                {/* Heute Stats */}
                <button
                    onClick={() => navigate('/tagesbuchungen')}
                    className="w-full bg-white border border-slate-200 rounded-xl p-4 text-left hover:border-rose-200 hover:shadow-sm transition-all"
                >
                    <div className="flex items-center gap-2 mb-3">
                        <Clock className="w-4 h-4 text-slate-400" />
                        <span className="text-sm font-medium text-slate-600">Heute gearbeitet</span>
                        <ChevronRight className="w-4 h-4 text-slate-400 ml-auto" />
                    </div>
                    <p className="text-2xl font-bold text-slate-900">{heuteStunden}h {heuteMinuten.toString().padStart(2, '0')}min</p>
                </button>

                {/* Urlaubsverfall-Warnung - ganz unten auf dem Dashboard */}
                {urlaubsWarnung && (
                    <div className={`rounded-2xl p-4 ${urlaubsWarnung.dringend ? 'bg-red-50 border border-red-200' : 'bg-amber-50 border border-amber-200'}`}>
                        <div className="flex items-start gap-3">
                            <div className={`flex-shrink-0 w-10 h-10 rounded-xl flex items-center justify-center ${urlaubsWarnung.dringend ? 'bg-red-100' : 'bg-amber-100'}`}>
                                <AlertTriangle className={`w-5 h-5 ${urlaubsWarnung.dringend ? 'text-red-600' : 'text-amber-600'}`} />
                            </div>
                            <div className="flex-1 min-w-0">
                                <p className={`font-bold ${urlaubsWarnung.dringend ? 'text-red-800' : 'text-amber-800'}`}>
                                    {urlaubsWarnung.dringend ? '⚠️ Dringend: ' : ''}Urlaubsverfall beachten!
                                </p>
                                <p className={`text-sm mt-0.5 ${urlaubsWarnung.dringend ? 'text-red-700' : 'text-amber-700'}`}>
                                    Du hast noch <strong>{urlaubsWarnung.resturlaubTage}</strong> Resturlaub-Tage vom Vorjahr.
                                    Diese verfallen am <strong>01.02.{urlaubsWarnung.verfallsJahr}</strong>{urlaubsWarnung.tageVerbleibend > 0 ? ` (in ${urlaubsWarnung.tageVerbleibend} Tagen)` : ' (heute!)'}!
                                </p>
                                <button
                                    onClick={() => navigate('/salden')}
                                    className={`mt-2 text-sm font-medium ${urlaubsWarnung.dringend ? 'text-red-600' : 'text-amber-600'} hover:underline`}
                                >
                                    Zur Urlaubsübersicht →
                                </button>
                            </div>
                        </div>
                    </div>
                )}
            </main>

            {/* Arbeitsgang Switch Modal */}
            {showArbeitsgangSwitch && (
                <div className="fixed inset-0 bg-black/50 flex items-end z-50">
                    <div className="bg-white w-full rounded-t-3xl max-h-[85vh] flex flex-col safe-area-bottom">
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

                        <div className="flex-1 overflow-auto p-4 space-y-2">
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
                    <div className="bg-white w-full rounded-t-3xl max-h-[85vh] flex flex-col safe-area-bottom">
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

                        <div className="flex-1 overflow-auto p-4 space-y-2">
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
        </div>
    )
}
