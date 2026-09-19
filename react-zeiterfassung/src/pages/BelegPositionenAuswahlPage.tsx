import { useCallback, useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { ArrowLeft, Loader2, AlertCircle, Save, ListChecks, CheckCircle2 } from 'lucide-react'

/**
 * Mobile-Page fuer die Checkbox-Auswahl der Beleg-Positionen.
 *
 * Workflow:
 *  1) BelegScannerPage hat den Beleg mit aufteilungsModus=TEILWEISE hochgeladen.
 *  2) Hier pollen wir auf den KI-Analyse-Status — sobald DONE, zeigen wir die
 *     extrahierten Positionen als Checkbox-Liste mit Live-Summen (netto, MwSt,
 *     brutto). Der Nutzer hakt an, was zur Firma gehoert.
 *  3) Auf "Speichern" senden wir die Auswahl ans Backend; das Backend rechnet
 *     die Firma-Summen am Beleg neu durch und wir navigieren zurueck.
 */
interface Position {
    id: number
    sortierung: number
    beschreibung: string
    menge: number | null
    einheit: string | null
    einzelpreis: number | null
    betragNetto: number | null
    betragBrutto: number | null
    mwstSatz: number | null
    istFuerFirma: boolean
}

interface BelegResponse {
    id: number
    kiAnalyseStatus: 'PENDING' | 'LAEUFT' | 'DONE' | 'FAILED'
    aufteilungsModus: 'VOLLSTAENDIG' | 'TEILWEISE'
    belegNummer: string | null
    belegDatum: string | null
    betragBrutto: number | null
    betragFirmaNetto: number | null
    betragFirmaBrutto: number | null
    betragFirmaMwst: number | null
    positionen: Position[]
    kiFehlerText: string | null
}

const POLL_INTERVAL_MS = 1500
const EUR = new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR' })

function fmt(value: number | null | undefined): string {
    if (value == null) return '–'
    return EUR.format(value)
}

export default function BelegPositionenAuswahlPage() {
    const { id } = useParams<{ id: string }>()
    const navigate = useNavigate()
    const belegId = id ? Number(id) : null
    const token = typeof window !== 'undefined' ? localStorage.getItem('zeiterfassung_token') : null

    const [beleg, setBeleg] = useState<BelegResponse | null>(null)
    const [selected, setSelected] = useState<Set<number>>(new Set())
    const [saving, setSaving] = useState(false)
    const [error, setError] = useState<string | null>(null)
    const [saved, setSaved] = useState(false)

    const ladeBeleg = useCallback(async () => {
        if (belegId == null || !token) return null
        try {
            // Mobile-Spiegel verwenden — die PWA kommt nicht durch die
            // session-basierte apiFilterChain, nur durch /mobile/** mit Token.
            const res = await fetch(`/api/buchhaltung/mobile/belege/${belegId}?token=${token}`)
            if (!res.ok) {
                setError(`Beleg konnte nicht geladen werden (HTTP ${res.status})`)
                return null
            }
            const data: BelegResponse = await res.json()
            setBeleg(data)
            return data
        } catch (e) {
            setError(e instanceof Error ? e.message : 'Netzwerkfehler')
            return null
        }
    }, [belegId, token])

    // Initial-Load + Polling, solange die KI noch laeuft.
    useEffect(() => {
        let cancelled = false
        let timer: number | undefined
        const tick = async () => {
            const data = await ladeBeleg()
            if (cancelled) return
            const istFertig = data && (data.kiAnalyseStatus === 'DONE' || data.kiAnalyseStatus === 'FAILED')
            if (data && data.positionen?.length > 0) {
                // Auswahl aus Backend uebernehmen (z.B. zweite Bearbeitung).
                setSelected(new Set(data.positionen.filter(p => p.istFuerFirma).map(p => p.id)))
            }
            if (!istFertig) {
                timer = window.setTimeout(tick, POLL_INTERVAL_MS)
            }
        }
        void tick()
        return () => { cancelled = true; if (timer) window.clearTimeout(timer) }
    }, [ladeBeleg])

    const togglePosition = (posId: number) => {
        setSelected(prev => {
            const next = new Set(prev)
            if (next.has(posId)) next.delete(posId); else next.add(posId)
            return next
        })
    }

    const handleSelectAll = () => {
        if (!beleg) return
        setSelected(new Set(beleg.positionen.map(p => p.id)))
    }
    const handleSelectNone = () => setSelected(new Set())

    const handleSpeichern = async () => {
        if (belegId == null || !token) return
        setSaving(true)
        setError(null)
        try {
            const res = await fetch(`/api/buchhaltung/mobile/belege/${belegId}/positionen?token=${token}`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ firmaPositionIds: Array.from(selected) }),
            })
            if (!res.ok) {
                const txt = await res.text().catch(() => '')
                throw new Error(`HTTP ${res.status}: ${txt}`)
            }
            const updated: BelegResponse = await res.json()
            setBeleg(updated)
            setSaved(true)
            window.setTimeout(() => navigate('/belege'), 800)
        } catch (e) {
            setError(e instanceof Error ? e.message : 'Speichern fehlgeschlagen')
        } finally {
            setSaving(false)
        }
    }

    // Live-Summen aus der lokalen Checkbox-Auswahl, damit die Anzeige sofort reagiert.
    // Persistente Summen kommen erst nach dem Save zurueck.
    const liveSummen = (() => {
        if (!beleg) return { netto: 0, brutto: 0, mwst: 0, perSatz: new Map<number, number>() }
        let netto = 0, brutto = 0
        const perSatz = new Map<number, number>()
        for (const p of beleg.positionen) {
            if (!selected.has(p.id)) continue
            const satz = p.mwstSatz ?? 0
            let pBrutto = p.betragBrutto
            let pNetto = p.betragNetto
            if (pBrutto == null && pNetto != null && satz) pBrutto = pNetto * (1 + satz / 100)
            if (pNetto == null && pBrutto != null && satz) pNetto = pBrutto / (1 + satz / 100)
            if (pBrutto == null) pBrutto = pNetto ?? 0
            if (pNetto == null) pNetto = pBrutto
            netto += pNetto
            brutto += pBrutto
            const mwstPos = pBrutto - pNetto
            perSatz.set(satz, (perSatz.get(satz) ?? 0) + mwstPos)
        }
        return { netto, brutto, mwst: brutto - netto, perSatz }
    })()

    if (!belegId) {
        return (
            <div className="h-full flex items-center justify-center text-slate-500">
                Ungültige Beleg-ID
            </div>
        )
    }

    const istFertig = beleg?.kiAnalyseStatus === 'DONE'
    const istFehler = beleg?.kiAnalyseStatus === 'FAILED'
    const keinePositionen = istFertig && (beleg?.positionen?.length ?? 0) === 0

    return (
        <div className="h-full flex flex-col bg-slate-50">
            <header className="bg-white border-b border-slate-200 px-4 py-4 safe-area-top flex items-center gap-3">
                <button onClick={() => navigate('/belege')} className="p-2 hover:bg-slate-100 rounded-lg active:scale-95">
                    <ArrowLeft className="w-5 h-5 text-slate-600" />
                </button>
                <div className="flex-1 min-w-0">
                    <p className="text-xs font-semibold uppercase tracking-wide text-rose-600">Beleg-Aufteilung</p>
                    <h1 className="font-bold text-slate-900 truncate">
                        {beleg?.belegNummer ?? `Beleg #${belegId}`}
                    </h1>
                </div>
                <ListChecks className="w-6 h-6 text-rose-600" />
            </header>

            {!istFertig && !istFehler && (
                <div className="flex-1 flex flex-col items-center justify-center p-8 text-center text-slate-500 gap-3">
                    <Loader2 className="w-10 h-10 animate-spin text-rose-600" />
                    <p className="font-medium text-slate-700">KI extrahiert Positionen…</p>
                    <p className="text-xs max-w-xs">
                        Das dauert nur ein paar Sekunden. Du kannst diese Seite offen lassen.
                    </p>
                </div>
            )}

            {istFehler && (
                <div className="flex-1 flex flex-col items-center justify-center p-8 text-center text-slate-500 gap-3">
                    <AlertCircle className="w-10 h-10 text-red-500" />
                    <p className="font-medium text-slate-700">KI-Analyse fehlgeschlagen</p>
                    <p className="text-xs max-w-xs text-red-600">{beleg?.kiFehlerText ?? 'Unbekannter Fehler'}</p>
                    <button
                        onClick={() => navigate('/belege')}
                        className="mt-3 px-4 py-2 bg-rose-600 text-white rounded-lg font-semibold"
                    >
                        Zurück
                    </button>
                </div>
            )}

            {istFertig && keinePositionen && (
                <div className="flex-1 flex flex-col items-center justify-center p-8 text-center text-slate-500 gap-3">
                    <AlertCircle className="w-10 h-10 text-slate-400" />
                    <p className="font-medium text-slate-700">Keine Einzel-Positionen erkannt</p>
                    <p className="text-xs max-w-xs">
                        Der Bon hatte vermutlich nur eine Gesamtsumme. Bitte am PC manuell aufteilen
                        oder den Beleg als „Ganz für Firma“ buchen.
                    </p>
                    <button
                        onClick={() => navigate('/belege')}
                        className="mt-3 px-4 py-2 bg-rose-600 text-white rounded-lg font-semibold"
                    >
                        Zurück
                    </button>
                </div>
            )}

            {istFertig && !keinePositionen && beleg && (
                <>
                    {/* Bulk-Aktionen */}
                    <div className="px-4 py-2 bg-white border-b border-slate-100 flex items-center justify-between gap-2 text-xs">
                        <span className="text-slate-500">{selected.size} von {beleg.positionen.length} ausgewählt</span>
                        <div className="flex gap-2">
                            <button onClick={handleSelectAll} className="px-3 py-1.5 border border-rose-300 text-rose-700 hover:bg-rose-50 rounded-lg font-semibold">
                                Alle
                            </button>
                            <button onClick={handleSelectNone} className="px-3 py-1.5 border border-slate-300 text-slate-700 hover:bg-slate-50 rounded-lg font-semibold">
                                Keine
                            </button>
                        </div>
                    </div>

                    {/* Positions-Liste */}
                    <div className="flex-1 overflow-y-auto overflow-x-hidden p-3 space-y-2 pb-44">
                        {beleg.positionen.map(p => {
                            const checked = selected.has(p.id)
                            return (
                                <label
                                    key={p.id}
                                    className={`flex items-start gap-3 p-3 rounded-xl border-2 cursor-pointer active:scale-[0.99] transition-colors ${
                                        checked ? 'bg-rose-50 border-rose-400' : 'bg-white border-slate-200'
                                    }`}
                                >
                                    <input
                                        type="checkbox"
                                        checked={checked}
                                        onChange={() => togglePosition(p.id)}
                                        className="mt-1 w-5 h-5 accent-rose-600 flex-shrink-0"
                                    />
                                    <div className="flex-1 min-w-0">
                                        <p className="font-medium text-slate-900 text-sm">{p.beschreibung}</p>
                                        <div className="flex flex-wrap gap-x-3 gap-y-0.5 mt-1 text-xs text-slate-500">
                                            {p.menge != null && (
                                                <span>{p.menge}{p.einheit ? ' ' + p.einheit : ''}</span>
                                            )}
                                            {p.einzelpreis != null && (
                                                <span>à {fmt(p.einzelpreis)}</span>
                                            )}
                                            {p.mwstSatz != null && (
                                                <span>{p.mwstSatz}% MwSt</span>
                                            )}
                                        </div>
                                    </div>
                                    <div className="text-right flex-shrink-0">
                                        <p className="font-bold text-slate-900 tabular-nums">{fmt(p.betragBrutto)}</p>
                                    </div>
                                </label>
                            )
                        })}
                    </div>

                    {/* Sticky Footer mit Live-Summen + Save */}
                    <div className="fixed bottom-0 left-0 right-0 bg-white border-t border-slate-200 shadow-lg">
                        <div className="px-4 py-3 grid grid-cols-3 gap-2 text-center">
                            <SummenBox label="Netto" value={fmt(liveSummen.netto)} />
                            <SummenBox label="MwSt" value={fmt(liveSummen.mwst)} />
                            <SummenBox label="Brutto" value={fmt(liveSummen.brutto)} accent />
                        </div>
                        {liveSummen.perSatz.size > 1 && (
                            <div className="px-4 pb-2 text-xs text-slate-500 flex flex-wrap gap-x-3">
                                {Array.from(liveSummen.perSatz.entries()).map(([satz, betrag]) => (
                                    <span key={satz}>{satz}%: {fmt(betrag)}</span>
                                ))}
                            </div>
                        )}
                        {error && (
                            <div className="px-4 py-2 text-xs text-red-600 flex items-center gap-2">
                                <AlertCircle className="w-4 h-4" /> {error}
                            </div>
                        )}
                        <button
                            disabled={saving}
                            onClick={handleSpeichern}
                            className="w-full bg-rose-600 hover:bg-rose-700 disabled:opacity-50 text-white font-bold py-4 flex items-center justify-center gap-2"
                        >
                            {saved ? <CheckCircle2 className="w-5 h-5" /> : saving ? <Loader2 className="w-5 h-5 animate-spin" /> : <Save className="w-5 h-5" />}
                            {saved ? 'Gespeichert' : saving ? 'Speichern…' : 'Auswahl übernehmen'}
                        </button>
                    </div>
                </>
            )}
        </div>
    )
}

function SummenBox({ label, value, accent }: { label: string; value: string; accent?: boolean }) {
    return (
        <div className={`rounded-lg p-2 ${accent ? 'bg-rose-50 border border-rose-200' : 'bg-slate-50 border border-slate-200'}`}>
            <p className="text-[10px] uppercase tracking-wide text-slate-500">{label}</p>
            <p className={`font-bold tabular-nums text-sm ${accent ? 'text-rose-700' : 'text-slate-900'}`}>{value}</p>
        </div>
    )
}
