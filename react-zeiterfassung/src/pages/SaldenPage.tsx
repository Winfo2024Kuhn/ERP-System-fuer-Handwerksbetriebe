import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { ArrowLeft, Sun, Briefcase, Clock, TrendingUp, TrendingDown, Minus, RefreshCw, Stethoscope, GraduationCap, CalendarDays, ChevronLeft, ChevronRight } from 'lucide-react'

interface Mitarbeiter {
    id: number
    name?: string
}

interface SaldoData {
    urlaub: {
        jahresanspruch: number
        genommen: number
        geplant: number
        verbleibend: number
        krankheitsTage: number
        fortbildungsTage: number
    }
    monat: {
        name: string
        monatNummer: number
        sollStunden: number
        istStunden: number
        differenz: number
    }
    gesamt: {
        istStunden: number
        sollStunden: number
        saldo: number
        startDatum?: string
    }
    mitarbeiterName: string
    jahr: number
}

interface SaldenPageProps {
    mitarbeiter: Mitarbeiter | null
    syncStatus?: 'syncing' | 'done' | 'error'
    onSync?: () => void
}

interface Feiertag {
    datum: string
    bezeichnung: string
}

export default function SaldenPage({ syncStatus, onSync }: SaldenPageProps) {
    const navigate = useNavigate()
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState<string | null>(null)
    const [saldo, setSaldo] = useState<SaldoData | null>(null)
    const [feiertage, setFeiertage] = useState<Feiertag[]>([])

    // Month navigation state - default to current month/year
    const [selectedMonth, setSelectedMonth] = useState<number>(new Date().getMonth() + 1)
    const [selectedYear, setSelectedYear] = useState<number>(new Date().getFullYear())

    useEffect(() => {
        loadSaldo(selectedMonth, selectedYear)
    }, [selectedMonth, selectedYear])

    const loadSaldo = async (month?: number, year?: number) => {
        setLoading(true)
        setError(null)
        const token = localStorage.getItem('zeiterfassung_token')
        if (!token) {
            setError('Nicht angemeldet')
            setLoading(false)
            return
        }

        try {
            // ========== API-Aufruf für Saldodaten ==========
            //
            // GESAMTSALDO-BERECHNUNG (gesamtBisHeute=true):
            // Die Mobile App zeigt IMMER das aktuelle Gesamtstundenkonto bis HEUTE an,
            // unabhängig davon welcher Monat/Jahr für die Monats-Ansicht ausgewählt wurde.
            // 
            // Beispiel: Wenn der Benutzer Dezember 2024 auswählt:
            // - Monat: zeigt Soll/Ist für Dezember 2024
            // - Gesamtsaldo: zeigt ALLE +/- Stunden von Eintrittsdatum bis HEUTE
            //
            // Das PC-Frontend (ZeiterfassungKalender) hat ein anderes Verhalten:
            // Dort wird das Gesamtsaldo bis zum Ende des ausgewählten Jahres berechnet.
            //
            let url = `/api/zeiterfassung/saldo/${token}`
            const params = new URLSearchParams()
            if (year) params.append('jahr', year.toString())
            if (month) params.append('monat', month.toString())
            // WICHTIG: gesamtBisHeute=true = Gesamtsaldo immer bis heute berechnen
            params.append('gesamtBisHeute', 'true')
            if (params.toString()) url += `?${params.toString()}`

            const res = await fetch(url)
            if (res.ok) {
                const data = await res.json()
                setSaldo(data)
            } else {
                setError('Fehler beim Laden der Daten')
            }
        } catch {
            setError('Server nicht erreichbar')
        } finally {
            setLoading(false)
        }
    }

    // Navigate to previous or next month
    const changeMonth = (delta: number) => {
        let newMonth = selectedMonth + delta
        let newYear = selectedYear

        if (newMonth < 1) {
            newMonth = 12
            newYear -= 1
        } else if (newMonth > 12) {
            newMonth = 1
            newYear += 1
        }

        setSelectedMonth(newMonth)
        setSelectedYear(newYear)
    }

    const loadFeiertage = async (jahr: number) => {
        try {
            const res = await fetch(`/api/zeiterfassung/feiertage?jahr=${jahr}`)
            if (res.ok) {
                const data = await res.json()
                // Sort by date
                const sorted = data.sort((a: Feiertag, b: Feiertag) =>
                    new Date(a.datum).getTime() - new Date(b.datum).getTime()
                )
                setFeiertage(sorted)
            }
        } catch (err) {
            console.error('Fehler beim Laden der Feiertage:', err)
        }
    }

    // Load Feiertage when saldo changes (to get the year)
    useEffect(() => {
        if (saldo?.jahr) {
            loadFeiertage(saldo.jahr)
        }
    }, [saldo?.jahr])

    const formatHours = (hours: number) => {
        const h = Math.floor(Math.abs(hours))
        const m = Math.round((Math.abs(hours) - h) * 60)
        const sign = hours < 0 ? '-' : hours > 0 ? '+' : ''
        return `${sign}${h}:${m.toString().padStart(2, '0')}`
    }

    const getSaldoColor = (saldo: number) => {
        if (saldo > 0) return 'text-green-600'
        if (saldo < 0) return 'text-red-600'
        return 'text-slate-600'
    }

    const getSaldoBg = (saldo: number) => {
        if (saldo > 0) return 'bg-green-100'
        if (saldo < 0) return 'bg-red-100'
        return 'bg-slate-100'
    }

    const getSaldoIcon = (saldo: number) => {
        if (saldo > 0) return <TrendingUp className="w-5 h-5" />
        if (saldo < 0) return <TrendingDown className="w-5 h-5" />
        return <Minus className="w-5 h-5" />
    }

    const getWochentag = (dateStr: string) => {
        const tage = ['So', 'Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa']
        const d = new Date(dateStr)
        return tage[d.getDay()]
    }

    const formatDateShort = (dateStr: string) => {
        return new Date(dateStr).toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit' })
    }

    const isPastDate = (dateStr: string) => {
        const d = new Date(dateStr)
        const today = new Date()
        today.setHours(0, 0, 0, 0)
        return d < today
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
                    <div>
                        <h1 className="text-lg font-bold text-slate-900">Saldenauswertung</h1>
                        <p className="text-xs text-slate-500">{saldo?.jahr || new Date().getFullYear()}</p>
                    </div>
                    <button
                        onClick={() => {
                            loadSaldo();
                            if (onSync) onSync();
                        }}
                        className="ml-auto p-2 text-slate-500 hover:text-rose-600 active:scale-95 transition-transform"
                    >
                        <RefreshCw className={`w-5 h-5 ${loading || syncStatus === 'syncing' ? 'animate-sync-spin text-rose-600' : 'text-slate-500'}`} />
                    </button>
                </div>
            </header>

            {/* Content */}
            <main className="flex-1 p-4 space-y-4 overflow-y-auto">
                {loading && (
                    <div className="flex items-center justify-center py-12">
                        <RefreshCw className="w-8 h-8 text-rose-500 animate-spin" />
                    </div>
                )}

                {error && (
                    <div className="bg-red-50 text-red-600 p-4 rounded-xl text-center">
                        {error}
                    </div>
                )}

                {saldo && !loading && (
                    <>
                        {/* Urlaub Section */}
                        <section className="bg-white rounded-2xl shadow-sm border border-slate-200 overflow-hidden">
                            <div className="p-4 border-b border-slate-100 flex items-center gap-3">
                                <div className="w-10 h-10 bg-amber-100 rounded-xl flex items-center justify-center">
                                    <Sun className="w-5 h-5 text-amber-600" />
                                </div>
                                <div>
                                    <h2 className="font-bold text-slate-900">Urlaubstage</h2>
                                    <p className="text-xs text-slate-500">Jahresanspruch {saldo.jahr}</p>
                                </div>
                            </div>
                            <div className="p-4 grid grid-cols-4 gap-2">
                                <div className="text-center">
                                    <p className="text-xl font-bold text-slate-900">{saldo.urlaub.jahresanspruch}</p>
                                    <p className="text-[10px] text-slate-500">Anspruch</p>
                                </div>
                                <div className="text-center">
                                    <p className="text-xl font-bold text-amber-600">{saldo.urlaub.genommen}</p>
                                    <p className="text-[10px] text-slate-500">Genommen</p>
                                </div>
                                <div className="text-center">
                                    <p className="text-xl font-bold text-blue-600">{saldo.urlaub.geplant || 0}</p>
                                    <p className="text-[10px] text-slate-500">Geplant</p>
                                </div>
                                <div className="text-center">
                                    <p className="text-xl font-bold text-green-600">{saldo.urlaub.verbleibend}</p>
                                    <p className="text-[10px] text-slate-500">Frei</p>
                                </div>
                            </div>
                            {/* Krankheit & Fortbildung */}
                            <div className="px-4 pb-4 grid grid-cols-2 gap-3">
                                <div className="flex items-center gap-2 p-3 bg-slate-50 rounded-xl">
                                    <Stethoscope className="w-4 h-4 text-slate-500" />
                                    <div>
                                        <p className="text-lg font-semibold text-slate-900">{saldo.urlaub.krankheitsTage}</p>
                                        <p className="text-xs text-slate-500">Krankheitstage</p>
                                    </div>
                                </div>
                                <div className="flex items-center gap-2 p-3 bg-slate-50 rounded-xl">
                                    <GraduationCap className="w-4 h-4 text-slate-500" />
                                    <div>
                                        <p className="text-lg font-semibold text-slate-900">{saldo.urlaub.fortbildungsTage}</p>
                                        <p className="text-xs text-slate-500">Fortbildung</p>
                                    </div>
                                </div>
                            </div>
                        </section>

                        {/* Monats-Section */}
                        <section className="bg-white rounded-2xl shadow-sm border border-slate-200 overflow-hidden">
                            <div className="p-4 border-b border-slate-100 flex items-center justify-between">
                                <div className="flex items-center gap-3">
                                    <div className="w-10 h-10 bg-blue-100 rounded-xl flex items-center justify-center">
                                        <Clock className="w-5 h-5 text-blue-600" />
                                    </div>
                                    <div>
                                        <h2 className="font-bold text-slate-900">{saldo.monat.name}</h2>
                                        <p className="text-xs text-slate-500">
                                            {selectedYear !== new Date().getFullYear()
                                                ? `${selectedYear}`
                                                : (selectedMonth === new Date().getMonth() + 1
                                                    ? 'Aktueller Monat'
                                                    : `${selectedYear}`)}
                                        </p>
                                    </div>
                                </div>
                                {/* Month Navigation Buttons */}
                                <div className="flex items-center gap-1">
                                    <button
                                        onClick={() => changeMonth(-1)}
                                        className="p-2 text-slate-500 hover:text-blue-600 hover:bg-blue-50 rounded-lg transition-colors"
                                        aria-label="Vorheriger Monat"
                                    >
                                        <ChevronLeft className="w-5 h-5" />
                                    </button>
                                    <button
                                        onClick={() => changeMonth(1)}
                                        className="p-2 text-slate-500 hover:text-blue-600 hover:bg-blue-50 rounded-lg transition-colors"
                                        aria-label="Nächster Monat"
                                    >
                                        <ChevronRight className="w-5 h-5" />
                                    </button>
                                </div>
                            </div>
                            <div className="p-4">
                                <div className="flex items-center justify-between mb-3">
                                    <span className="text-sm text-slate-600">Soll-Stunden</span>
                                    <span className="font-semibold text-slate-900">{saldo.monat.sollStunden.toFixed(1)}h</span>
                                </div>
                                <div className="flex items-center justify-between mb-3">
                                    <span className="text-sm text-slate-600">Ist-Stunden</span>
                                    <span className="font-semibold text-slate-900">{saldo.monat.istStunden.toFixed(1)}h</span>
                                </div>
                                <div className={`flex items-center justify-between p-3 rounded-xl ${getSaldoBg(saldo.monat.differenz)}`}>
                                    <span className={`text-sm font-medium ${getSaldoColor(saldo.monat.differenz)}`}>
                                        {saldo.monat.differenz >= 0 ? 'Überstunden' : 'Fehlstunden'}
                                    </span>
                                    <div className={`flex items-center gap-2 ${getSaldoColor(saldo.monat.differenz)}`}>
                                        {getSaldoIcon(saldo.monat.differenz)}
                                        <span className="font-bold text-lg">{formatHours(saldo.monat.differenz)}h</span>
                                    </div>
                                </div>
                            </div>
                        </section>

                        {/* Gesamtsaldo Section */}
                        <section className="bg-white rounded-2xl shadow-sm border border-slate-200 overflow-hidden">
                            <div className="p-4 border-b border-slate-100 flex items-center gap-3">
                                <div className="w-10 h-10 bg-rose-100 rounded-xl flex items-center justify-center">
                                    <Briefcase className="w-5 h-5 text-rose-600" />
                                </div>
                                <div>
                                    <h2 className="font-bold text-slate-900">Gesamtsaldo</h2>
                                    <p className="text-xs text-slate-500">
                                        {saldo.gesamt.startDatum
                                            ? `Seit ${formatDateShort(saldo.gesamt.startDatum)}`
                                            : `Jahr ${saldo.jahr} bis heute`}
                                    </p>
                                </div>
                            </div>
                            <div className="p-4">

                                <div className={`flex items-center justify-center gap-3 p-4 rounded-xl ${getSaldoBg(saldo.gesamt.saldo)}`}>
                                    <div className={getSaldoColor(saldo.gesamt.saldo)}>
                                        {getSaldoIcon(saldo.gesamt.saldo)}
                                    </div>
                                    <div className="text-center">
                                        <p className={`text-3xl font-bold ${getSaldoColor(saldo.gesamt.saldo)}`}>
                                            {formatHours(saldo.gesamt.saldo)}h
                                        </p>
                                        <p className="text-sm text-slate-500">
                                            {saldo.gesamt.saldo >= 0 ? 'Überstunden' : 'Fehlstunden'}
                                        </p>
                                    </div>
                                </div>
                            </div>
                        </section>

                        {/* Feiertage Section */}
                        <section className="bg-white rounded-2xl shadow-sm border border-slate-200 overflow-hidden">
                            <div className="p-4 border-b border-slate-100 flex items-center gap-3">
                                <div className="w-10 h-10 bg-purple-100 rounded-xl flex items-center justify-center">
                                    <CalendarDays className="w-5 h-5 text-purple-600" />
                                </div>
                                <div>
                                    <h2 className="font-bold text-slate-900">Feiertage {saldo.jahr}</h2>
                                    <p className="text-xs text-slate-500">{feiertage.length} Feiertage in Bayern</p>
                                </div>
                            </div>
                            <div className="p-4 space-y-2">
                                {feiertage.length === 0 ? (
                                    <div className="text-center py-4 text-slate-400 text-sm">
                                        Keine Feiertage geladen
                                    </div>
                                ) : (
                                    feiertage.map((f, idx) => {
                                        const vergangen = isPastDate(f.datum)
                                        const wochentag = getWochentag(f.datum)
                                        const isWeekend = wochentag === 'Sa' || wochentag === 'So'
                                        return (
                                            <div
                                                key={idx}
                                                className={`flex items-center justify-between p-2.5 rounded-xl transition-colors ${vergangen
                                                    ? 'bg-slate-50 opacity-60'
                                                    : 'bg-purple-50 border border-purple-100'
                                                    }`}
                                            >
                                                <div className="flex items-center gap-3">
                                                    <div className={`w-10 h-10 rounded-lg flex flex-col items-center justify-center text-xs font-bold ${isWeekend
                                                        ? 'bg-slate-200 text-slate-600'
                                                        : vergangen
                                                            ? 'bg-slate-300 text-slate-700'
                                                            : 'bg-purple-600 text-white'
                                                        }`}>
                                                        <span className="text-[10px] uppercase">{wochentag}</span>
                                                        <span className="text-sm">{new Date(f.datum).getDate()}</span>
                                                    </div>
                                                    <div>
                                                        <p className={`font-medium text-sm ${vergangen ? 'text-slate-500' : 'text-slate-900'}`}>
                                                            {f.bezeichnung}
                                                        </p>
                                                        <p className="text-xs text-slate-400">
                                                            {formatDateShort(f.datum)}
                                                            {isWeekend && <span className="ml-1 text-amber-600">(Wochenende)</span>}
                                                        </p>
                                                    </div>
                                                </div>
                                                {vergangen && (
                                                    <span className="text-[10px] px-1.5 py-0.5 rounded bg-slate-200 text-slate-500 uppercase tracking-wide">
                                                        vergangen
                                                    </span>
                                                )}
                                            </div>
                                        )
                                    })
                                )}
                            </div>
                        </section>
                    </>
                )}
            </main>
        </div>
    )
}
