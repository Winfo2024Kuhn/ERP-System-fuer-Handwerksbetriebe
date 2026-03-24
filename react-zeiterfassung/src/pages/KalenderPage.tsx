import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import {
    ArrowLeft, Calendar, ChevronLeft, ChevronRight, RefreshCw, Clock,
    Users, LayoutGrid, List, CalendarClock, X, ExternalLink,
    Briefcase, User, Truck, FileText
} from 'lucide-react'

interface KalenderPageProps {
    mitarbeiter: { id: number; name: string } | null
    token: string | null
    syncStatus?: 'syncing' | 'done' | 'error'
    onSync?: () => void
}

interface Teilnehmer {
    id: number
    name: string
}

interface KalenderEintrag {
    id: number
    titel: string
    beschreibung: string | null
    datum: string
    startZeit: string | null
    endeZeit: string | null
    ganztaegig: boolean
    farbe: string | null
    projektId: number | null
    projektName: string | null
    kundeId: number | null
    kundeName: string | null
    lieferantId: number | null
    lieferantName: string | null
    anfrageId: number | null
    anfrageBetreff: string | null
    erstellerId: number | null
    erstellerName: string | null
    teilnehmer: Teilnehmer[]
}

// Team-Abwesenheiten (Urlaub, Krankheit, Fortbildung)
interface TeamAbwesenheit {
    id: number
    datum: string
    typ: 'URLAUB' | 'KRANKHEIT' | 'FORTBILDUNG' | 'ZEITAUSGLEICH'
    stunden: number
    mitarbeiterId: number
    mitarbeiterName: string
}

// Farben für Abwesenheitstypen (minimalistisch ohne Emojis)
const ABWESENHEIT_FARBEN: Record<string, { farbe: string; label: string }> = {
    URLAUB: { farbe: '#0ea5e9', label: 'Urlaub' },
    KRANKHEIT: { farbe: '#64748b', label: 'Krankheit' },
    FORTBILDUNG: { farbe: '#10b981', label: 'Fortbildung' },
    ZEITAUSGLEICH: { farbe: '#f59e0b', label: 'Zeitausgleich' },
    FEIERTAG: { farbe: '#f43f5e', label: 'Feiertag' },
    TERMIN: { farbe: '#7c3aed', label: 'Auftrag/Termin' },
}

const WOCHENTAGE = ['Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa', 'So']
const WOCHENTAGE_LANG = ['', 'Montag', 'Dienstag', 'Mittwoch', 'Donnerstag', 'Freitag', 'Samstag', 'Sonntag']
const MONATE = ['Januar', 'Februar', 'März', 'April', 'Mai', 'Juni',
    'Juli', 'August', 'September', 'Oktober', 'November', 'Dezember']

// Farboptionen für Events
// WICHTIG: Hier müssen ALLE Farben definiert sein, die irgendwo verwendet werden (auch Abwesenheiten)
const FARB_OPTIONEN = [
    // Standard User-Farben
    { value: '#dc2626', bg: 'bg-red-100', border: 'border-red-500', text: 'text-red-700' },
    { value: '#ea580c', bg: 'bg-orange-100', border: 'border-orange-500', text: 'text-orange-700' },
    { value: '#ca8a04', bg: 'bg-yellow-100', border: 'border-yellow-500', text: 'text-yellow-700' },
    { value: '#16a34a', bg: 'bg-green-100', border: 'border-green-500', text: 'text-green-700' },
    { value: '#0891b2', bg: 'bg-cyan-100', border: 'border-cyan-500', text: 'text-cyan-700' },
    { value: '#2563eb', bg: 'bg-blue-100', border: 'border-blue-500', text: 'text-blue-700' },
    { value: '#7c3aed', bg: 'bg-violet-100', border: 'border-violet-500', text: 'text-violet-700' },
    { value: '#db2777', bg: 'bg-pink-100', border: 'border-pink-500', text: 'text-pink-700' },

    // Abwesenheit Farben (Explizit hinzufügen damit getFarbStyle sie findet!)
    { value: '#0ea5e9', bg: 'bg-sky-100', border: 'border-sky-500', text: 'text-sky-700' },        // URLAUB
    { value: '#64748b', bg: 'bg-slate-100', border: 'border-slate-500', text: 'text-slate-700' },   // KRANKHEIT
    { value: '#10b981', bg: 'bg-emerald-100', border: 'border-emerald-500', text: 'text-emerald-700' }, // FORTBILDUNG
    { value: '#f59e0b', bg: 'bg-amber-100', border: 'border-amber-500', text: 'text-amber-700' },   // ZEITAUSGLEICH
    { value: '#f43f5e', bg: 'bg-rose-100', border: 'border-rose-500', text: 'text-rose-700' },      // FEIERTAG
]

// Ermittelt die Farb-Styles für einen Eintrag
// Bei normalen Terminen (id > 0) wird Rot (#dc2626) auf Violett umgewandelt,
// da Rot nur für Feiertage reserviert ist (alte Termine hatten falschen Default)
function getFarbStyle(farbe: string | null, eintragId?: number) {
    // Bei normalen Terminen (positive ID): Rot auf Violett korrigieren (alter falscher Default)
    const korrigierteFarbe = (eintragId && eintragId > 0 && farbe === '#dc2626') ? '#7c3aed' : farbe
    
    const found = FARB_OPTIONEN.find(f => f.value === korrigierteFarbe)
    // Fallback auf Violett für normale Termine, statt Rot (dieses bleibt für Feiertage)
    return found || FARB_OPTIONEN.find(f => f.value === '#7c3aed') || FARB_OPTIONEN[6]
}

type Ansicht = 'monat' | 'woche' | 'tag'

interface Feiertag {
    datum: string
    bezeichnung: string
}

export default function KalenderPage({ token, syncStatus, onSync }: KalenderPageProps) {
    const navigate = useNavigate()
    const [eintraege, setEintraege] = useState<KalenderEintrag[]>([])
    const [teamAbwesenheiten, setTeamAbwesenheiten] = useState<TeamAbwesenheit[]>([])
    const [feiertage, setFeiertage] = useState<Feiertag[]>([])
    const [loading, setLoading] = useState(true)
    const [ansicht, setAnsicht] = useState<Ansicht>('monat')
    const [selectedDate, setSelectedDate] = useState(new Date())
    const [showEventModal, setShowEventModal] = useState(false)
    const [selectedEintrag, setSelectedEintrag] = useState<KalenderEintrag | null>(null)

    // Jahr und Monat für das aktuelle Datum
    const jahr = selectedDate.getFullYear()
    const monat = selectedDate.getMonth() + 1

    useEffect(() => {
        if (token) loadEintraege()
    }, [token, jahr, monat])

    const loadEintraege = async () => {
        if (!token) return
        setLoading(true)
        try {
            // Kalendereinträge laden
            const res = await fetch(`/api/kalender/mobile?token=${token}&jahr=${jahr}&monat=${monat}`)
            if (res.ok) {
                const data = await res.json()
                setEintraege(data)
            }

            // Team-Abwesenheiten laden
            const letzterTag = new Date(jahr, monat, 0).getDate()
            const von = `${jahr}-${String(monat).padStart(2, '0')}-01`
            const bis = `${jahr}-${String(monat).padStart(2, '0')}-${String(letzterTag).padStart(2, '0')}`
            const abwRes = await fetch(`/api/abwesenheit/team?von=${von}&bis=${bis}`)
            if (abwRes.ok) {
                const abwData = await abwRes.json()
                setTeamAbwesenheiten(Array.isArray(abwData) ? abwData : [])
            }

            // Feiertage laden (immer komplettes Jahr)
            const feiertagRes = await fetch(`/api/zeiterfassung/feiertage?jahr=${jahr}`)
            if (feiertagRes.ok) {
                const fData = await feiertagRes.json()
                setFeiertage(Array.isArray(fData) ? fData : [])
            }
        } catch (err) {
            console.error('Fehler beim Laden der Termine:', err)
        } finally {
            setLoading(false)
        }
    }

    // Navigation
    const navigateMonth = (delta: number) => {
        const newDate = new Date(selectedDate)
        newDate.setMonth(newDate.getMonth() + delta)
        setSelectedDate(newDate)
    }

    const navigateWeek = (delta: number) => {
        const newDate = new Date(selectedDate)
        newDate.setDate(newDate.getDate() + (delta * 7))
        setSelectedDate(newDate)
    }

    const navigateDay = (delta: number) => {
        const newDate = new Date(selectedDate)
        newDate.setDate(newDate.getDate() + delta)
        setSelectedDate(newDate)
    }

    const goToToday = () => {
        setSelectedDate(new Date())
    }

    // Helper: Mappt Feiertag zu KalenderEintrag
    const mapFeiertagToEintrag = (f: Feiertag, index: number): KalenderEintrag => {
        const info = ABWESENHEIT_FARBEN.FEIERTAG
        return {
            id: -(index + 100000), // Pseudo-ID um Konflikte zu vermeiden
            titel: f.bezeichnung,
            beschreibung: 'Gesetzlicher Feiertag',
            datum: f.datum,
            startZeit: null,
            endeZeit: null,
            ganztaegig: true,
            farbe: info.farbe,
            projektId: null,
            projektName: null,
            kundeId: null,
            kundeName: null,
            lieferantId: null,
            lieferantName: null,
            anfrageId: null,
            anfrageBetreff: null,
            erstellerId: null,
            erstellerName: 'System',
            teilnehmer: []
        }
    }

    // Kalendertage generieren
    const generateKalenderTage = () => {
        const firstDay = new Date(jahr, monat - 1, 1)
        const lastDay = new Date(jahr, monat, 0)
        let ersterWochentag = firstDay.getDay()
        if (ersterWochentag === 0) ersterWochentag = 7 // Sonntag = 7

        const days = []
        for (let d = 1; d <= lastDay.getDate(); d++) {
            const datum = new Date(jahr, monat - 1, d)
            const datumStr = formatLocalDate(datum)

            // Normale Einträge + Team-Abwesenheiten
            const normalEintraege = eintraege.filter(e => e.datum === datumStr)
            const abwesenheitsEintraege: KalenderEintrag[] = teamAbwesenheiten
                .filter(a => a.datum === datumStr)
                .map(a => {
                    const info = ABWESENHEIT_FARBEN[a.typ] || ABWESENHEIT_FARBEN.URLAUB
                    return {
                        id: -a.id,
                        titel: a.mitarbeiterName,
                        beschreibung: info.label,
                        datum: a.datum,
                        startZeit: null,
                        endeZeit: null,
                        ganztaegig: true,
                        farbe: info.farbe,
                        projektId: null,
                        projektName: null,
                        kundeId: null,
                        kundeName: null,
                        lieferantId: null,
                        lieferantName: null,
                        anfrageId: null,
                        anfrageBetreff: null,
                        erstellerId: a.mitarbeiterId,
                        erstellerName: a.mitarbeiterName,
                        teilnehmer: []
                    }
                })

            const feiertagEintraege: KalenderEintrag[] = feiertage
                .filter(f => f.datum === datumStr)
                .map((f, i) => mapFeiertagToEintrag(f, i + (d * 100)))

            days.push({
                datum: datumStr,
                day: d,
                wochentag: datum.getDay() === 0 ? 7 : datum.getDay(),
                eintraege: [...feiertagEintraege, ...abwesenheitsEintraege, ...normalEintraege]
            })
        }
        return { days, ersterWochentag }
    }

    // Helper: Formatiere Datum als lokales YYYY-MM-DD
    const formatLocalDate = (date: Date): string => {
        const year = date.getFullYear()
        const month = String(date.getMonth() + 1).padStart(2, '0')
        const day = String(date.getDate()).padStart(2, '0')
        return `${year}-${month}-${day}`
    }

    // Wochentage generieren
    const generateWochenTage = () => {
        const start = new Date(selectedDate)
        const day = start.getDay()
        const diff = start.getDate() - day + (day === 0 ? -6 : 1)
        start.setDate(diff)

        const days = []
        for (let i = 0; i < 7; i++) {
            const datum = new Date(start)
            datum.setDate(start.getDate() + i)
            const datumStr = formatLocalDate(datum)

            // Feiertage
            const feiertagEintraege: KalenderEintrag[] = feiertage
                .filter(f => f.datum === datumStr)
                .map((f, idx) => mapFeiertagToEintrag(f, idx + 1000))

            // Team-Abwesenheiten (Bug fix: diese fehlten in der Wochenansicht)
            const abwesenheitsEintraege: KalenderEintrag[] = teamAbwesenheiten
                .filter(a => a.datum === datumStr)
                .map(a => {
                    const info = ABWESENHEIT_FARBEN[a.typ] || ABWESENHEIT_FARBEN.URLAUB
                    return {
                        id: -a.id,
                        titel: a.mitarbeiterName,
                        beschreibung: info.label,
                        datum: a.datum,
                        startZeit: null,
                        endeZeit: null,
                        ganztaegig: true,
                        farbe: info.farbe,
                        projektId: null,
                        projektName: null,
                        kundeId: null,
                        kundeName: null,
                        lieferantId: null,
                        lieferantName: null,
                        anfrageId: null,
                        anfrageBetreff: null,
                        erstellerId: a.mitarbeiterId,
                        erstellerName: a.mitarbeiterName,
                        teilnehmer: []
                    }
                })

            // Normale Einträge
            const normalEintraege = eintraege.filter(e => e.datum === datumStr)

            days.push({
                datum: datumStr,
                day: datum.getDate(),
                wochentag: datum.getDay() === 0 ? 7 : datum.getDay(),
                eintraege: [...feiertagEintraege, ...abwesenheitsEintraege, ...normalEintraege]
            })
        }
        return days
    }

    const { days: kalenderTage, ersterWochentag } = generateKalenderTage()
    const wochenTage = generateWochenTage()
    const tagDatumStr = formatLocalDate(selectedDate)

    // Tageseinträge inkl. Team-Abwesenheiten
    const tagesAbwesenheiten: KalenderEintrag[] = teamAbwesenheiten
        .filter(a => a.datum === tagDatumStr)
        .map(a => {
            const info = ABWESENHEIT_FARBEN[a.typ] || ABWESENHEIT_FARBEN.URLAUB
            return {
                id: -a.id,
                titel: a.mitarbeiterName,
                beschreibung: info.label,
                datum: a.datum,
                startZeit: null,
                endeZeit: null,
                ganztaegig: true,
                farbe: info.farbe,
                projektId: null,
                projektName: null,
                kundeId: null,
                kundeName: null,
                lieferantId: null,
                lieferantName: null,
                anfrageId: null,
                anfrageBetreff: null,
                erstellerId: a.mitarbeiterId,
                erstellerName: a.mitarbeiterName,
                teilnehmer: []
            }
        })

    const tagesFeiertage: KalenderEintrag[] = feiertage
        .filter(f => f.datum === tagDatumStr)
        .map((f, i) => mapFeiertagToEintrag(f, i + 5000))

    const tagesEintraege = [...tagesFeiertage, ...tagesAbwesenheiten, ...eintraege.filter(e => e.datum === tagDatumStr)]
    const todayStr = formatLocalDate(new Date())

    const handleEventClick = (eintrag: KalenderEintrag) => {
        setSelectedEintrag(eintrag)
        setShowEventModal(true)
    }

    return (
        <div className="h-full bg-slate-50 flex flex-col">
            {/* Header */}
            <header className="bg-white border-b border-slate-200 px-4 py-4 safe-area-top sticky top-0 z-10">
                <div className="flex items-center gap-3">
                    <button
                        onClick={() => navigate('/')}
                        className="p-2 -ml-2 text-slate-500 hover:text-slate-900"
                    >
                        <ArrowLeft className="w-5 h-5" />
                    </button>
                    <h1 className="text-lg font-bold text-slate-900">Kalender</h1>
                    <button
                        onClick={() => onSync && onSync()}
                        className="ml-auto p-2 hover:bg-slate-100 rounded-lg transition-all active:scale-95"
                    >
                        <RefreshCw className={`w-5 h-5 ${loading || syncStatus === 'syncing' ? 'animate-sync-spin text-rose-600' : 'text-slate-500'}`} />
                    </button>
                </div>
            </header>

            {/* Ansicht Toggle */}
            <div className="px-4 py-3 bg-white border-b border-slate-200">
                <div className="flex bg-slate-100 rounded-lg p-1">
                    <button
                        onClick={() => setAnsicht('monat')}
                        className={`flex-1 px-3 py-2 text-sm font-medium rounded-md transition-all flex items-center justify-center gap-1 ${ansicht === 'monat' ? 'bg-white text-rose-700 shadow-sm' : 'text-slate-600'
                            }`}
                    >
                        <LayoutGrid className="w-4 h-4" /> Monat
                    </button>
                    <button
                        onClick={() => setAnsicht('woche')}
                        className={`flex-1 px-3 py-2 text-sm font-medium rounded-md transition-all flex items-center justify-center gap-1 ${ansicht === 'woche' ? 'bg-white text-rose-700 shadow-sm' : 'text-slate-600'
                            }`}
                    >
                        <List className="w-4 h-4" /> Woche
                    </button>
                    <button
                        onClick={() => setAnsicht('tag')}
                        className={`flex-1 px-3 py-2 text-sm font-medium rounded-md transition-all flex items-center justify-center gap-1 ${ansicht === 'tag' ? 'bg-white text-rose-700 shadow-sm' : 'text-slate-600'
                            }`}
                    >
                        <CalendarClock className="w-4 h-4" /> Tag
                    </button>
                </div>
            </div>

            {/* Navigation */}
            <div className="px-4 py-3 bg-white flex items-center justify-between">
                <button
                    onClick={() => ansicht === 'tag' ? navigateDay(-1) : ansicht === 'woche' ? navigateWeek(-1) : navigateMonth(-1)}
                    className="p-2 hover:bg-slate-100 rounded-lg"
                >
                    <ChevronLeft className="w-5 h-5 text-slate-600" />
                </button>
                <div className="text-center">
                    {ansicht === 'tag' ? (
                        <p className="font-bold text-slate-900">
                            {selectedDate.toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric' })}
                        </p>
                    ) : (
                        <p className="font-bold text-slate-900">
                            {MONATE[monat - 1]} {jahr}
                        </p>
                    )}
                </div>
                <button
                    onClick={() => ansicht === 'tag' ? navigateDay(1) : ansicht === 'woche' ? navigateWeek(1) : navigateMonth(1)}
                    className="p-2 hover:bg-slate-100 rounded-lg"
                >
                    <ChevronRight className="w-5 h-5 text-slate-600" />
                </button>
            </div>

            {/* Heute Button */}
            <div className="px-4 py-2">
                <button
                    onClick={goToToday}
                    className="w-full py-2 bg-slate-100 rounded-lg text-sm font-medium text-slate-700 hover:bg-slate-200 transition-colors"
                >
                    Heute
                </button>
            </div>

            {/* Farblegende */}
            <div className="px-4 pb-3">
                <div className="bg-white rounded-xl border border-slate-200 p-3">
                    <p className="text-xs font-semibold text-slate-500 uppercase mb-2">Legende</p>
                    <div className="flex flex-wrap gap-3">
                        {Object.entries(ABWESENHEIT_FARBEN).map(([typ, { farbe, label }]) => (
                            <div key={typ} className="flex items-center gap-1.5">
                                <div
                                    className="w-3 h-3 rounded-full"
                                    style={{ backgroundColor: farbe }}
                                />
                                <span className="text-xs text-slate-600">
                                    {label}
                                </span>
                            </div>
                        ))}
                    </div>
                </div>
            </div>

            {/* Content */}
            <main className="flex-1 px-4 pb-4 overflow-auto">
                {loading ? (
                    <div className="flex items-center justify-center py-12">
                        <div className="w-8 h-8 border-2 border-rose-200 border-t-rose-600 rounded-full animate-spin"></div>
                    </div>
                ) : ansicht === 'monat' ? (
                    /* ========== MONATSANSICHT ========== */
                    <div className="bg-white rounded-xl border border-slate-200 overflow-hidden">
                        {/* Wochentags Header */}
                        <div className="grid grid-cols-7 bg-slate-50 border-b border-slate-200">
                            {WOCHENTAGE.map(tag => (
                                <div key={tag} className="p-2 text-center text-xs font-bold text-slate-500">
                                    {tag}
                                </div>
                            ))}
                        </div>
                        {/* Tage */}
                        <div className="grid grid-cols-7">
                            {/* Gap */}
                            {Array.from({ length: ersterWochentag - 1 }).map((_, i) => (
                                <div key={`empty-${i}`} className="min-h-[60px] bg-slate-50 border-r border-b border-slate-100" />
                            ))}
                            {kalenderTage.map(tag => {
                                const isToday = tag.datum === todayStr
                                const isWeekend = tag.wochentag >= 6
                                return (
                                    <div
                                        key={tag.datum}
                                        onClick={() => { setSelectedDate(new Date(tag.datum)); setAnsicht('tag'); }}
                                        className={`min-h-[60px] p-1 border-r border-b border-slate-100 cursor-pointer transition-colors ${isWeekend ? 'bg-slate-50' : 'bg-white'
                                            } hover:bg-rose-50`}
                                    >
                                        <span className={`text-xs font-bold w-6 h-6 flex items-center justify-center rounded-full ${isToday ? 'bg-rose-600 text-white' : 'text-slate-700'
                                            }`}>
                                            {tag.day}
                                        </span>
                                        {tag.eintraege.length > 0 && (
                                            <div className="mt-1 flex flex-wrap gap-0.5">
                                                {tag.eintraege.slice(0, 3).map(e => {
                                                    // Bei normalen Terminen (id > 0): Rot auf Violett korrigieren
                                                    const farbe = (e.id > 0 && e.farbe === '#dc2626') ? '#7c3aed' : (e.farbe || '#7c3aed')
                                                    return (
                                                        <div
                                                            key={e.id}
                                                            className={`w-1.5 h-1.5 rounded-full`}
                                                            style={{ backgroundColor: farbe }}
                                                        />
                                                    )
                                                })}
                                            </div>
                                        )}
                                    </div>
                                )
                            })}
                        </div>
                    </div>
                ) : ansicht === 'woche' ? (
                    /* ========== WOCHENANSICHT ========== */
                    <div className="space-y-2">
                        {wochenTage.map(tag => {
                            const isToday = tag.datum === todayStr
                            return (
                                <div
                                    key={tag.datum}
                                    className={`bg-white rounded-xl border p-3 ${isToday ? 'border-rose-300 bg-rose-50/50' : 'border-slate-200'}`}
                                    onClick={() => { setSelectedDate(new Date(tag.datum)); setAnsicht('tag'); }}
                                >
                                    <div className="flex items-center gap-3 mb-2">
                                        <div className={`w-10 h-10 rounded-full flex items-center justify-center font-bold ${isToday ? 'bg-rose-600 text-white' : 'bg-slate-100 text-slate-700'
                                            }`}>
                                            {tag.day}
                                        </div>
                                        <div>
                                            <p className="font-semibold text-slate-900">{WOCHENTAGE_LANG[tag.wochentag]}</p>
                                            <p className="text-xs text-slate-500">{new Date(tag.datum).toLocaleDateString('de-DE', { month: 'short' })}</p>
                                        </div>
                                        {tag.eintraege.length > 0 && (
                                            <span className="ml-auto bg-rose-100 text-rose-700 text-xs font-bold px-2 py-0.5 rounded-full">
                                                {tag.eintraege.length}
                                            </span>
                                        )}
                                    </div>
                                    {tag.eintraege.length > 0 && (
                                        <div className="space-y-1.5 ml-13">
                                            {tag.eintraege.slice(0, 3).map(eintrag => {
                                                const farbStyle = getFarbStyle(eintrag.farbe, eintrag.id)
                                                return (
                                                    <div
                                                        key={eintrag.id}
                                                        onClick={(e) => { e.stopPropagation(); handleEventClick(eintrag); }}
                                                        className={`p-2 rounded-lg border-l-4 ${farbStyle.bg} ${farbStyle.border}`}
                                                    >
                                                        <p className={`text-sm font-medium ${farbStyle.text}`}>{eintrag.titel}</p>
                                                        {!eintrag.ganztaegig && eintrag.startZeit && (
                                                            <p className="text-xs text-slate-500 flex items-center gap-1">
                                                                <Clock className="w-3 h-3" />
                                                                {eintrag.startZeit.substring(0, 5)}
                                                                {eintrag.endeZeit && ` – ${eintrag.endeZeit.substring(0, 5)}`}
                                                            </p>
                                                        )}
                                                    </div>
                                                )
                                            })}
                                            {tag.eintraege.length > 3 && (
                                                <p className="text-xs text-slate-400 text-center">+{tag.eintraege.length - 3} weitere</p>
                                            )}
                                        </div>
                                    )}
                                </div>
                            )
                        })}
                    </div>
                ) : (
                    /* ========== TAGESANSICHT ========== */
                    <div className="bg-white rounded-xl border border-slate-200 overflow-hidden">
                        {tagesEintraege.length === 0 ? (
                            <div className="text-center py-12">
                                <Calendar className="w-12 h-12 mx-auto text-slate-300 mb-3" />
                                <p className="text-slate-500">Keine Termine für diesen Tag.</p>
                            </div>
                        ) : (
                            <div className="divide-y divide-slate-100">
                                {tagesEintraege.map(eintrag => {
                                    const farbStyle = getFarbStyle(eintrag.farbe, eintrag.id)
                                    return (
                                        <div
                                            key={eintrag.id}
                                            onClick={() => handleEventClick(eintrag)}
                                            className="p-4 hover:bg-slate-50 transition-colors cursor-pointer"
                                        >
                                            <div className="flex items-start gap-3">
                                                <div className={`w-1 h-full min-h-[40px] rounded-full ${farbStyle.border.replace('border-', 'bg-')}`} />
                                                <div className="flex-1">
                                                    <p className={`font-semibold ${farbStyle.text}`}>{eintrag.titel}</p>
                                                    {!eintrag.ganztaegig && eintrag.startZeit ? (
                                                        <p className="text-sm text-slate-500 flex items-center gap-1 mt-0.5">
                                                            <Clock className="w-3.5 h-3.5" />
                                                            {eintrag.startZeit.substring(0, 5)}
                                                            {eintrag.endeZeit && ` – ${eintrag.endeZeit.substring(0, 5)}`}
                                                        </p>
                                                    ) : (
                                                        <p className="text-sm text-slate-500 mt-0.5">Ganztägig</p>
                                                    )}
                                                    {eintrag.beschreibung && (
                                                        <p className="text-sm text-slate-600 mt-2">{eintrag.beschreibung}</p>
                                                    )}
                                                    {(eintrag.teilnehmer && eintrag.teilnehmer.length > 0) && (
                                                        <div className="flex items-center gap-1 mt-2 text-xs text-slate-500">
                                                            <Users className="w-3.5 h-3.5" />
                                                            {eintrag.teilnehmer.map(t => t.name).join(', ')}
                                                        </div>
                                                    )}
                                                </div>
                                            </div>
                                        </div>
                                    )
                                })}
                            </div>
                        )}
                    </div>
                )}
            </main>

            {/* Event Detail Modal */}
            {showEventModal && selectedEintrag && (
                <div className="fixed inset-0 bg-black/50 z-50 flex items-end" onClick={() => setShowEventModal(false)}>
                    <div
                        className="bg-white w-full rounded-t-2xl max-h-[80vh] overflow-auto safe-area-bottom"
                        onClick={(e) => e.stopPropagation()}
                    >
                        {/* Modal Header */}
                        <div className="sticky top-0 bg-white border-b border-slate-200 p-4 flex items-center justify-between">
                            <h2 className="font-bold text-lg text-slate-900">Termindetails</h2>
                            <button
                                onClick={() => setShowEventModal(false)}
                                className="p-2 hover:bg-slate-100 rounded-lg"
                            >
                                <X className="w-5 h-5 text-slate-500" />
                            </button>
                        </div>

                        {/* Modal Content */}
                        <div className="p-4 space-y-4">
                            {/* Titel */}
                            <div>
                                <p className="text-xs font-semibold text-slate-500 uppercase mb-1">Titel</p>
                                <p className="text-lg font-bold text-slate-900">{selectedEintrag.titel}</p>
                            </div>

                            {/* Datum & Zeit */}
                            <div className="grid grid-cols-2 gap-4">
                                <div>
                                    <p className="text-xs font-semibold text-slate-500 uppercase mb-1">Datum</p>
                                    <p className="text-slate-900">
                                        {new Date(selectedEintrag.datum).toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric' })}
                                    </p>
                                </div>
                                {!selectedEintrag.ganztaegig && selectedEintrag.startZeit && (
                                    <div>
                                        <p className="text-xs font-semibold text-slate-500 uppercase mb-1">Uhrzeit</p>
                                        <p className="text-slate-900">
                                            {selectedEintrag.startZeit.substring(0, 5)}
                                            {selectedEintrag.endeZeit && ` – ${selectedEintrag.endeZeit.substring(0, 5)}`}
                                        </p>
                                    </div>
                                )}
                                {selectedEintrag.ganztaegig && (
                                    <div>
                                        <p className="text-xs font-semibold text-slate-500 uppercase mb-1">Uhrzeit</p>
                                        <p className="text-slate-900">Ganztägig</p>
                                    </div>
                                )}
                            </div>

                            {/* Beschreibung */}
                            {selectedEintrag.beschreibung && (
                                <div>
                                    <p className="text-xs font-semibold text-slate-500 uppercase mb-1">Beschreibung</p>
                                    <p className="text-slate-700">{selectedEintrag.beschreibung}</p>
                                </div>
                            )}

                            {/* Ersteller */}
                            {selectedEintrag.erstellerName && (
                                <div>
                                    <p className="text-xs font-semibold text-slate-500 uppercase mb-1">Erstellt von</p>
                                    <p className="text-slate-700">{selectedEintrag.erstellerName}</p>
                                </div>
                            )}

                            {/* Teilnehmer */}
                            {selectedEintrag.teilnehmer && selectedEintrag.teilnehmer.length > 0 && (
                                <div>
                                    <p className="text-xs font-semibold text-slate-500 uppercase mb-1">Teilnehmer</p>
                                    <div className="flex flex-wrap gap-2">
                                        {selectedEintrag.teilnehmer.map(t => (
                                            <span key={t.id} className="px-2.5 py-1 bg-violet-100 text-violet-700 rounded-full text-sm">
                                                {t.name}
                                            </span>
                                        ))}
                                    </div>
                                </div>
                            )}

                            {/* Verknüpfungen */}
                            {(selectedEintrag.projektId || selectedEintrag.kundeId || selectedEintrag.lieferantId || selectedEintrag.anfrageId) && (
                                <div>
                                    <p className="text-xs font-semibold text-slate-500 uppercase mb-2">Verknüpfungen</p>
                                    <div className="space-y-2">
                                        {selectedEintrag.projektId && selectedEintrag.projektName && (
                                            <button
                                                onClick={() => { setShowEventModal(false); navigate(`/projekte?id=${selectedEintrag.projektId}`); }}
                                                className="w-full flex items-center gap-3 p-3 bg-rose-50 hover:bg-rose-100 border border-rose-200 rounded-xl transition-colors text-left"
                                            >
                                                <div className="w-9 h-9 bg-rose-100 rounded-lg flex items-center justify-center">
                                                    <Briefcase className="w-4 h-4 text-rose-600" />
                                                </div>
                                                <div className="flex-1 min-w-0">
                                                    <p className="text-xs text-slate-500">Projekt</p>
                                                    <p className="text-sm font-medium text-slate-900 truncate">{selectedEintrag.projektName}</p>
                                                </div>
                                                <ExternalLink className="w-4 h-4 text-rose-500" />
                                            </button>
                                        )}
                                        {selectedEintrag.kundeId && selectedEintrag.kundeName && (
                                            <button
                                                onClick={() => { setShowEventModal(false); navigate(`/kunden?id=${selectedEintrag.kundeId}`); }}
                                                className="w-full flex items-center gap-3 p-3 bg-blue-50 hover:bg-blue-100 border border-blue-200 rounded-xl transition-colors text-left"
                                            >
                                                <div className="w-9 h-9 bg-blue-100 rounded-lg flex items-center justify-center">
                                                    <User className="w-4 h-4 text-blue-600" />
                                                </div>
                                                <div className="flex-1 min-w-0">
                                                    <p className="text-xs text-slate-500">Kunde</p>
                                                    <p className="text-sm font-medium text-slate-900 truncate">{selectedEintrag.kundeName}</p>
                                                </div>
                                                <ExternalLink className="w-4 h-4 text-blue-500" />
                                            </button>
                                        )}
                                        {selectedEintrag.lieferantId && selectedEintrag.lieferantName && (
                                            <button
                                                onClick={() => { setShowEventModal(false); navigate(`/lieferanten?id=${selectedEintrag.lieferantId}`); }}
                                                className="w-full flex items-center gap-3 p-3 bg-amber-50 hover:bg-amber-100 border border-amber-200 rounded-xl transition-colors text-left"
                                            >
                                                <div className="w-9 h-9 bg-amber-100 rounded-lg flex items-center justify-center">
                                                    <Truck className="w-4 h-4 text-amber-600" />
                                                </div>
                                                <div className="flex-1 min-w-0">
                                                    <p className="text-xs text-slate-500">Lieferant</p>
                                                    <p className="text-sm font-medium text-slate-900 truncate">{selectedEintrag.lieferantName}</p>
                                                </div>
                                                <ExternalLink className="w-4 h-4 text-amber-500" />
                                            </button>
                                        )}
                                        {selectedEintrag.anfrageId && selectedEintrag.anfrageBetreff && (
                                            <button
                                                onClick={() => { setShowEventModal(false); navigate(`/anfragen?id=${selectedEintrag.anfrageId}`); }}
                                                className="w-full flex items-center gap-3 p-3 bg-green-50 hover:bg-green-100 border border-green-200 rounded-xl transition-colors text-left"
                                            >
                                                <div className="w-9 h-9 bg-green-100 rounded-lg flex items-center justify-center">
                                                    <FileText className="w-4 h-4 text-green-600" />
                                                </div>
                                                <div className="flex-1 min-w-0">
                                                    <p className="text-xs text-slate-500">Anfrage</p>
                                                    <p className="text-sm font-medium text-slate-900 truncate">{selectedEintrag.anfrageBetreff}</p>
                                                </div>
                                                <ExternalLink className="w-4 h-4 text-green-500" />
                                            </button>
                                        )}
                                    </div>
                                </div>
                            )}
                        </div>
                    </div>
                </div>
            )}
        </div>
    )
}
