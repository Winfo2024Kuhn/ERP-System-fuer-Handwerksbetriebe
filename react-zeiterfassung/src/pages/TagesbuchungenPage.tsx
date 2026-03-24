import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { ArrowLeft, Calendar, Clock, Briefcase, Hammer, FolderOpen, MessageSquare, ChevronLeft, ChevronRight, Loader2, Coffee, RefreshCw, WifiOff } from 'lucide-react'
import { OfflineService } from '../services/OfflineService'

interface Buchung {
    id: number | string
    startMinuten?: number
    endeMinuten?: number
    dauerMinuten?: number
    projektId?: number
    projektNummer?: string
    projektName?: string
    kundenName?: string
    arbeitsgangId?: number
    taetigkeit?: string
    kategorieId?: number
    kategorieName?: string
    kommentar?: string
    typ?: 'PAUSE' | 'URLAUB' | 'KRANKHEIT' | 'FORTBILDUNG' | 'ARBEIT' | null
    isOffline?: boolean // Flag für offline Buchungen
}

interface Projekt {
    id: number
    name: string
    projektNummer?: string
    kundenName?: string
}

interface Arbeitsgang {
    id: number
    beschreibung: string
}

interface PendingEntry {
    id: string
    type: 'start' | 'stop' | 'pause'
    data: Record<string, unknown>
    timestamp: string
    originalTime?: string
}

const formatMinutenToTime = (minuten?: number): string => {
    if (minuten === undefined || minuten === null) return '--:--'
    const hours = Math.floor(minuten / 60)
    const mins = minuten % 60
    return `${hours.toString().padStart(2, '0')}:${mins.toString().padStart(2, '0')}`
}

const formatDauer = (minuten?: number): string => {
    if (!minuten) return '0 min'
    if (minuten < 60) return `${minuten} min`
    const hours = Math.floor(minuten / 60)
    const mins = minuten % 60
    if (mins === 0) return `${hours}h`
    return `${hours}h ${mins}min`
}

const WOCHENTAGE = ['So', 'Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa']
const MONATE = ['Januar', 'Februar', 'März', 'April', 'Mai', 'Juni', 'Juli', 'August', 'September', 'Oktober', 'November', 'Dezember']

const toDateKey = (date: Date): string => {
    const year = date.getFullYear()
    const month = `${date.getMonth() + 1}`.padStart(2, '0')
    const day = `${date.getDate()}`.padStart(2, '0')
    return `${year}-${month}-${day}`
}

const getEntryTime = (entry: PendingEntry): Date => new Date(entry.originalTime || entry.timestamp)

const isSameDisplayedBuchung = (left: Buchung, right: Buchung): boolean => {
    return (left.typ || null) === (right.typ || null)
        && (left.projektId || null) === (right.projektId || null)
        && (left.arbeitsgangId || null) === (right.arbeitsgangId || null)
        && (left.startMinuten || null) === (right.startMinuten || null)
        && (left.endeMinuten || null) === (right.endeMinuten || null)
        && (left.dauerMinuten || null) === (right.dauerMinuten || null)
}

interface TagesbuchungenPageProps {
    syncStatus?: 'syncing' | 'done' | 'error'
    onSync?: () => void
}

export default function TagesbuchungenPage({ syncStatus, onSync }: TagesbuchungenPageProps) {
    const navigate = useNavigate()
    const [selectedDate, setSelectedDate] = useState<Date>(new Date())
    const [showCalendar, setShowCalendar] = useState(false)
    const [calendarMonth, setCalendarMonth] = useState<Date>(new Date())
    const [buchungen, setBuchungen] = useState<Buchung[]>([])
    const [loading, setLoading] = useState(true)
    const [isOfflineMode, setIsOfflineMode] = useState(false)

    const token = localStorage.getItem('zeiterfassung_token') || ''

    const loadBuchungen = async () => {
        setLoading(true)
        const datum = toDateKey(selectedDate)
        const today = toDateKey(new Date())
        
        let serverBuchungen: Buchung[] = []
        let offlineMode = false

        const serverResult = await OfflineService.getTagesbuchungen(token, datum)
        serverBuchungen = serverResult.buchungen as unknown as Buchung[]
        offlineMode = serverResult.fromCache

        const pending = await OfflineService.getPendingEntries() as PendingEntry[]
        const offlineBuchungen = await buildOfflineBuchungen(pending, datum)

        const allBuchungen = [...serverBuchungen]
        for (const offlineBuchung of offlineBuchungen) {
            if (!allBuchungen.some(serverBuchung => isSameDisplayedBuchung(serverBuchung, offlineBuchung))) {
                allBuchungen.push(offlineBuchung)
            }
        }

        // Also add current active session from localStorage if not already shown
        if (datum === today) {
            const storedSession = localStorage.getItem('zeiterfassung_active_session')
            if (storedSession) {
                try {
                    const session = JSON.parse(storedSession)
                    if (session.startTime) {
                        const startTime = new Date(session.startTime)
                        const now = new Date()
                        const startMinuten = startTime.getHours() * 60 + startTime.getMinutes()
                        const dauerMinuten = Math.floor((now.getTime() - startTime.getTime()) / 60000)
                        const runningId = `running-${session.projektId}-${startMinuten}`
                        
                        // Only add if not already in list
                        if (!allBuchungen.some(b => b.id === runningId || 
                            (b.startMinuten === startMinuten && b.projektId === session.projektId))) {
                            allBuchungen.push({
                                id: runningId,
                                startMinuten,
                                dauerMinuten,
                                projektId: session.projektId,
                                projektName: session.projektName,
                                kundenName: session.kundenName,
                                arbeitsgangId: session.arbeitsgangId,
                                taetigkeit: session.arbeitsgangName,
                                typ: session.typ,
                            })
                        }
                    }
                } catch { /* ignore */ }
            }
        }

        allBuchungen.sort((a, b) => (a.startMinuten || 0) - (b.startMinuten || 0))
        setBuchungen(allBuchungen)
        
        setIsOfflineMode(offlineMode)
        setLoading(false)
    }

    // Build buchungen from pending offline entries
    const buildOfflineBuchungen = async (pending: PendingEntry[], datum: string): Promise<Buchung[]> => {
        const buchungen: Buchung[] = []
        const [projekte, tokenArbeitsgaenge, globaleArbeitsgaenge] = await Promise.all([
            OfflineService.getProjekte() as Promise<Projekt[]>,
            OfflineService.getArbeitsgaenge(token) as Promise<Arbeitsgang[]>,
            OfflineService.getArbeitsgaenge() as Promise<Arbeitsgang[]>,
        ])
        const arbeitsgaenge = tokenArbeitsgaenge.length > 0 ? tokenArbeitsgaenge : globaleArbeitsgaenge
        const tageseintraege = pending
            .filter((entry) => toDateKey(getEntryTime(entry)) === datum)
            .sort((left, right) => getEntryTime(left).getTime() - getEntryTime(right).getTime())
        
        let currentEntry: PendingEntry | null = null

        const addArbeitsbuchung = (startEntry: PendingEntry, endTime?: Date) => {
            const projektId = startEntry.data.projektId as number | undefined
            const arbeitsgangId = startEntry.data.arbeitsgangId as number | undefined
            const projekt = projekte.find(p => p.id === projektId)
            const arbeitsgang = arbeitsgaenge.find(a => a.id === arbeitsgangId)
            const startTime = getEntryTime(startEntry)
            const startMinuten = startTime.getHours() * 60 + startTime.getMinutes()
            const endeMinuten = endTime ? (endTime.getHours() * 60 + endTime.getMinutes()) : undefined
            const dauerMinuten = endTime ? Math.max(0, Math.floor((endTime.getTime() - startTime.getTime()) / 60000)) : undefined

            buchungen.push({
                id: `offline-${startEntry.id}`,
                startMinuten,
                endeMinuten,
                dauerMinuten,
                projektId,
                projektNummer: projekt?.projektNummer,
                projektName: projekt?.name || 'Offline Projekt',
                kundenName: projekt?.kundenName,
                arbeitsgangId,
                taetigkeit: arbeitsgang?.beschreibung || 'Offline Tätigkeit',
                isOffline: true,
                kommentar: endTime ? undefined : 'Läuft...',
            })
        }

        const addPausebuchung = (pauseEntry: PendingEntry, endTime?: Date) => {
            const startTime = getEntryTime(pauseEntry)
            const startMinuten = startTime.getHours() * 60 + startTime.getMinutes()
            const endeMinuten = endTime ? (endTime.getHours() * 60 + endTime.getMinutes()) : undefined
            const dauerMinuten = endTime ? Math.max(0, Math.floor((endTime.getTime() - startTime.getTime()) / 60000)) : undefined

            buchungen.push({
                id: `offline-pause-${pauseEntry.id}`,
                startMinuten,
                endeMinuten,
                dauerMinuten,
                typ: 'PAUSE',
                isOffline: true,
            })
        }
        
        for (const entry of tageseintraege) {
            if (entry.type === 'start') {
                currentEntry = entry
            } else if (entry.type === 'pause') {
                const pauseStart = getEntryTime(entry)
                if (currentEntry?.type === 'start') {
                    addArbeitsbuchung(currentEntry, pauseStart)
                }
                currentEntry = entry
            } else if (entry.type === 'stop' && currentEntry) {
                const stopTime = getEntryTime(entry)
                if (currentEntry.type === 'start') {
                    addArbeitsbuchung(currentEntry, stopTime)
                } else if (currentEntry.type === 'pause') {
                    addPausebuchung(currentEntry, stopTime)
                }
                currentEntry = null
            }
        }
        
        if (currentEntry) {
            const now = datum === toDateKey(new Date()) ? new Date() : undefined
            if (currentEntry.type === 'start') {
                addArbeitsbuchung(currentEntry, now)
            } else if (currentEntry.type === 'pause') {
                addPausebuchung(currentEntry, now)
            }
        }
        
        return buchungen
    }

    const isToday = (date: Date) => {
        const today = new Date()
        return date.getDate() === today.getDate() &&
            date.getMonth() === today.getMonth() &&
            date.getFullYear() === today.getFullYear()
    }

    const formatDateDisplay = (date: Date): string => {
        if (isToday(date)) return 'Heute'
        const yesterday = new Date()
        yesterday.setDate(yesterday.getDate() - 1)
        if (date.getDate() === yesterday.getDate() &&
            date.getMonth() === yesterday.getMonth() &&
            date.getFullYear() === yesterday.getFullYear()) {
            return 'Gestern'
        }
        return `${WOCHENTAGE[date.getDay()]}, ${date.getDate()}. ${MONATE[date.getMonth()]}`
    }

    const gesamtMinuten = buchungen
        .filter(b => b.typ !== 'PAUSE')
        .reduce((sum, b) => sum + (b.dauerMinuten || 0), 0)

    // Kalender-Logik
    const getDaysInMonth = (date: Date) => {
        const year = date.getFullYear()
        const month = date.getMonth()
        const firstDay = new Date(year, month, 1)
        const lastDay = new Date(year, month + 1, 0)
        const daysInMonth = lastDay.getDate()
        const startingDayOfWeek = firstDay.getDay()

        const days: (Date | null)[] = []
        // Leere Tage am Anfang (Montag = 1, also Sonntag = 0 -> 6 leere Tage)
        const emptyDays = startingDayOfWeek === 0 ? 6 : startingDayOfWeek - 1
        for (let i = 0; i < emptyDays; i++) {
            days.push(null)
        }
        for (let i = 1; i <= daysInMonth; i++) {
            days.push(new Date(year, month, i))
        }
        return days
    }

    const selectDay = (day: Date | null) => {
        if (day) {
            setSelectedDate(day)
            setShowCalendar(false)
        }
    }

    useEffect(() => {
        const timeoutId = window.setTimeout(() => {
            void loadBuchungen()
        }, 0)

        return () => window.clearTimeout(timeoutId)
    // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [selectedDate])

    // Reload when sync completes
    useEffect(() => {
        if (syncStatus === 'done') {
            const timeoutId = window.setTimeout(() => {
                void loadBuchungen()
            }, 0)

            return () => window.clearTimeout(timeoutId)
        }
    // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [syncStatus])

    const goToPreviousDay = () => {
        const newDate = new Date(selectedDate)
        newDate.setDate(newDate.getDate() - 1)
        setSelectedDate(newDate)
    }

    const goToNextDay = () => {
        const newDate = new Date(selectedDate)
        newDate.setDate(newDate.getDate() + 1)
        if (newDate <= new Date()) {
            setSelectedDate(newDate)
        }
    }

    return (
        <div className="h-full flex flex-col bg-slate-50">
            {/* Header */}
            <header className="bg-white border-b border-slate-200 px-4 py-4 safe-area-top">
                <div className="flex items-center gap-3">
                    <button
                        onClick={() => navigate('/')}
                        aria-label="Zur Startseite"
                        title="Zur Startseite"
                        className="p-2 hover:bg-slate-100 rounded-lg transition-all active:scale-95"
                    >
                        <ArrowLeft className="w-5 h-5 text-slate-600" />
                    </button>
                    <div className="flex items-center gap-3 flex-1">
                        <div className="w-10 h-10 bg-rose-50 rounded-lg flex items-center justify-center">
                            <Clock className="w-5 h-5 text-rose-600" />
                        </div>
                        <div>
                            <h1 className="font-bold text-slate-900">Tagesbuchungen</h1>
                            <p className="text-sm text-slate-500">{formatDauer(gesamtMinuten)} gearbeitet</p>
                        </div>
                    </div>
                    {/* Offline-Indicator */}
                    {isOfflineMode && (
                        <div className="flex items-center gap-1 px-2 py-1 bg-amber-100 rounded-lg">
                            <WifiOff className="w-4 h-4 text-amber-600" />
                            <span className="text-xs text-amber-700">Offline</span>
                        </div>
                    )}
                    <button
                        onClick={() => onSync && onSync()}
                        aria-label="Tagesbuchungen synchronisieren"
                        title="Tagesbuchungen synchronisieren"
                        className="p-2 hover:bg-slate-100 rounded-lg transition-all active:scale-95"
                    >
                        <RefreshCw className={`w-5 h-5 ${loading || syncStatus === 'syncing' ? 'animate-sync-spin text-rose-600' : 'text-slate-500'}`} />
                    </button>
                </div>
            </header>

            {/* Datum-Auswahl */}
            <div className="bg-white border-b border-slate-100 px-4 py-3">
                <div className="flex items-center justify-between">
                    <button
                        onClick={goToPreviousDay}
                        aria-label="Vorheriger Tag"
                        title="Vorheriger Tag"
                        className="p-2 hover:bg-slate-100 rounded-lg transition-all active:scale-95"
                    >
                        <ChevronLeft className="w-5 h-5 text-slate-600" />
                    </button>

                    <button
                        onClick={() => {
                            setCalendarMonth(selectedDate)
                            setShowCalendar(true)
                        }}
                        className="flex items-center gap-2 px-4 py-2 bg-slate-100 hover:bg-slate-200 rounded-xl transition-colors"
                    >
                        <Calendar className="w-4 h-4 text-rose-600" />
                        <span className="font-medium text-slate-900">{formatDateDisplay(selectedDate)}</span>
                    </button>

                    <button
                        onClick={goToNextDay}
                        disabled={isToday(selectedDate)}
                        aria-label="Nächster Tag"
                        title="Nächster Tag"
                        className="p-2 hover:bg-slate-100 rounded-lg transition-all active:scale-95 disabled:opacity-30"
                    >
                        <ChevronRight className="w-5 h-5 text-slate-600" />
                    </button>
                </div>
            </div>

            {/* Buchungen-Liste */}
            <div className="flex-1 overflow-auto p-4">
                {loading ? (
                    <div className="flex justify-center py-12">
                        <Loader2 className="w-8 h-8 animate-spin text-rose-600" />
                    </div>
                ) : buchungen.length === 0 ? (
                    <div className="text-center py-12 text-slate-500">
                        <Clock className="w-12 h-12 mx-auto mb-3 opacity-30" />
                        <p>Keine Buchungen an diesem Tag</p>
                    </div>
                ) : (
                    <div className="space-y-3">
                        {buchungen.map((buchung) => (
                            <div
                                key={buchung.id}
                                className={`bg-white border rounded-xl p-4 ${buchung.typ === 'PAUSE' ? 'border-amber-200 bg-amber-50/50' : 'border-slate-200'}`}
                            >
                                {/* Zeitzeile */}
                                <div className="flex items-center justify-between mb-3">
                                    <div className="flex items-center gap-2">
                                        {buchung.typ === 'PAUSE' ? (
                                            <Coffee className="w-4 h-4 text-amber-600" />
                                        ) : (
                                            <Clock className="w-4 h-4 text-rose-600" />
                                        )}
                                        <span className="font-mono text-lg font-semibold text-slate-900">
                                            {formatMinutenToTime(buchung.startMinuten)} - {formatMinutenToTime(buchung.endeMinuten)}
                                        </span>
                                    </div>
                                    <span className={`text-sm font-medium px-2 py-1 rounded-lg ${buchung.typ === 'PAUSE' ? 'text-amber-700 bg-amber-100' : 'text-rose-600 bg-rose-50'}`}>
                                        {formatDauer(buchung.dauerMinuten)}
                                    </span>
                                </div>

                                {buchung.typ === 'PAUSE' ? (
                                    <div className="flex items-center gap-2 text-amber-700 font-medium text-sm">
                                        <span>Pause</span>
                                    </div>
                                ) : (
                                    /* Details für normale Buchungen */
                                    <div className="space-y-2 text-sm">
                                        {/* Projekt */}
                                        {buchung.projektName && (
                                            <div className="mb-1">
                                                <div className="flex items-center gap-2 text-slate-700">
                                                    <Briefcase className="w-4 h-4 text-slate-400" />
                                                    <span className="font-medium">{buchung.projektNummer || 'Projekt'}</span>
                                                    <span className="text-slate-500 truncate">- {buchung.projektName}</span>
                                                </div>
                                                {buchung.kundenName && (
                                                    <div className="ml-6 text-xs text-slate-500">
                                                        {buchung.kundenName}
                                                    </div>
                                                )}
                                            </div>
                                        )}

                                        {/* Tätigkeit */}
                                        {buchung.taetigkeit && (
                                            <div className="flex items-center gap-2 text-slate-700">
                                                <Hammer className="w-4 h-4 text-slate-400" />
                                                <span>{buchung.taetigkeit}</span>
                                            </div>
                                        )}

                                        {/* Kategorie */}
                                        {buchung.kategorieName && (
                                            <div className="flex items-center gap-2 text-slate-700">
                                                <FolderOpen className="w-4 h-4 text-slate-400" />
                                                <span className="text-xs">{buchung.kategorieName}</span>
                                            </div>
                                        )}

                                        {/* Kommentar */}
                                        {buchung.kommentar && (
                                            <div className="flex items-start gap-2 text-slate-600 bg-slate-50 rounded-lg p-2 mt-2">
                                                <MessageSquare className="w-4 h-4 text-slate-400 mt-0.5" />
                                                <span className="text-xs">{buchung.kommentar}</span>
                                            </div>
                                        )}
                                    </div>
                                )}
                            </div>
                        ))}
                    </div>
                )}
            </div>

            {/* Kalender Modal */}
            {showCalendar && (
                <div
                    className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4"
                    onClick={() => setShowCalendar(false)}
                >
                    <div
                        className="bg-white rounded-2xl p-4 max-w-sm w-full"
                        onClick={(e) => e.stopPropagation()}
                    >
                        {/* Monat/Jahr Navigation */}
                        <div className="flex items-center justify-between mb-4">
                            <button
                                onClick={() => {
                                    const newMonth = new Date(calendarMonth)
                                    newMonth.setMonth(newMonth.getMonth() - 1)
                                    setCalendarMonth(newMonth)
                                }}
                                aria-label="Vorheriger Monat"
                                title="Vorheriger Monat"
                                className="p-2 hover:bg-slate-100 rounded-lg"
                            >
                                <ChevronLeft className="w-5 h-5" />
                            </button>
                            <span className="font-semibold text-slate-900">
                                {MONATE[calendarMonth.getMonth()]} {calendarMonth.getFullYear()}
                            </span>
                            <button
                                onClick={() => {
                                    const newMonth = new Date(calendarMonth)
                                    newMonth.setMonth(newMonth.getMonth() + 1)
                                    setCalendarMonth(newMonth)
                                }}
                                aria-label="Nächster Monat"
                                title="Nächster Monat"
                                className="p-2 hover:bg-slate-100 rounded-lg"
                            >
                                <ChevronRight className="w-5 h-5" />
                            </button>
                        </div>

                        {/* Wochentage Header */}
                        <div className="grid grid-cols-7 gap-1 mb-2">
                            {['Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa', 'So'].map((tag) => (
                                <div key={tag} className="text-center text-xs font-medium text-slate-500 py-2">
                                    {tag}
                                </div>
                            ))}
                        </div>

                        {/* Tage */}
                        <div className="grid grid-cols-7 gap-1">
                            {getDaysInMonth(calendarMonth).map((day, idx) => {
                                const isSelected = day && day.toDateString() === selectedDate.toDateString()
                                const isTodayDay = day && isToday(day)
                                const isFuture = day && day > new Date()

                                return (
                                    <button
                                        key={idx}
                                        onClick={() => !isFuture && selectDay(day)}
                                        disabled={!day || Boolean(isFuture)}
                                        className={`
                                            aspect-square flex items-center justify-center rounded-full text-sm font-medium transition-colors
                                            ${!day ? 'invisible' : ''}
                                            ${isFuture ? 'text-slate-300 cursor-not-allowed' : ''}
                                            ${isSelected ? 'bg-rose-600 text-white' : ''}
                                            ${isTodayDay && !isSelected ? 'bg-rose-100 text-rose-700' : ''}
                                            ${!isSelected && !isTodayDay && !isFuture ? 'hover:bg-slate-100 text-slate-900' : ''}
                                        `}
                                    >
                                        {day?.getDate()}
                                    </button>
                                )
                            })}
                        </div>

                        {/* Heute Button */}
                        <button
                            onClick={() => {
                                setSelectedDate(new Date())
                                setShowCalendar(false)
                            }}
                            className="w-full mt-4 py-2 text-rose-600 font-medium hover:bg-rose-50 rounded-lg transition-colors"
                        >
                            Heute
                        </button>
                    </div>
                </div>
            )}
        </div>
    )
}
