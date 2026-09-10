import { DecimalInput } from '../components/ui/decimal-input'
import { validateDecimalInput, formatDecimalInput } from '../lib/numberInput'
import { useToast } from '../components/ui/toast'
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ArrowLeft, Calculator, Loader2, AlertCircle } from 'lucide-react'

/**
 * Mobile-MwSt-Rechner. Der Nutzer tippt zwei der drei Felder
 * (Netto / Brutto / Satz%) ein, das Backend rechnet den dritten Wert plus
 * den MwSt-Betrag aus.
 */
interface Ergebnis {
    netto: number
    brutto: number
    satzProzent: number
    mwstBetrag: number
}

const EUR = new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR' })

export default function MwstRechnerPage() {
    const toast = useToast()
    const navigate = useNavigate()
    const token = typeof window !== 'undefined' ? localStorage.getItem('zeiterfassung_token') : null
    const [netto, setNetto] = useState('')
    const [brutto, setBrutto] = useState('')
    const [satz, setSatz] = useState('19')
    const [ergebnis, setErgebnis] = useState<Ergebnis | null>(null)
    const [error, setError] = useState<string | null>(null)
    const [loading, setLoading] = useState(false)

    const handleBerechnen = async () => {
        setError(null)
        setErgebnis(null)
        const values = [validateDecimalInput(netto, { label: 'Netto', min: -1_000_000_000, max: 1_000_000_000 }), validateDecimalInput(brutto, { label: 'Brutto', min: -1_000_000_000, max: 1_000_000_000 }), validateDecimalInput(satz, { label: 'MwSt-Satz', min: 0, max: 100 })]
        for (const value of values) {
            if (!value.valid) { setError(value.message); toast.error(value.message); return }
        }
        const [nettoNum, bruttoNum, satzNum] = values.map(value => value.valid ? value.value : null)
        const gesetzt = [nettoNum, bruttoNum, satzNum].filter(v => v != null).length
        if (gesetzt < 2) {
            setError('Bitte zwei Werte ausfüllen (Netto + Satz, Brutto + Satz oder Netto + Brutto).')
            toast.error('Bitte zwei Werte ausfüllen (Netto + Satz, Brutto + Satz oder Netto + Brutto).')
            return
        }
        setLoading(true)
        try {
            const url = token
                ? `/api/buchhaltung/mobile/mwst-rechner?token=${encodeURIComponent(token)}`
                : '/api/buchhaltung/mobile/mwst-rechner'
            const res = await fetch(url, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ netto: nettoNum, brutto: bruttoNum, satzProzent: satzNum }),
            })
            if (!res.ok) {
                const txt = await res.text().catch(() => '')
                throw new Error(`HTTP ${res.status}: ${txt}`)
            }
            const data: Ergebnis = await res.json()
            setErgebnis(data)
            setNetto(data.netto.toFixed(2).replace('.', ','))
            setBrutto(data.brutto.toFixed(2).replace('.', ','))
            setSatz(formatDecimalInput(data.satzProzent))
        } catch {
            const message = 'Berechnung fehlgeschlagen. Bitte Eingaben prüfen oder später erneut versuchen.'
            setError(message)
            toast.error(message)
        } finally {
            setLoading(false)
        }
    }

    const handleClear = () => {
        setNetto('')
        setBrutto('')
        setSatz('19')
        setErgebnis(null)
        setError(null)
    }

    return (
        <div className="h-full flex flex-col bg-slate-50">
            <header className="bg-white border-b border-slate-200 px-4 py-4 safe-area-top flex items-center gap-3">
                <button onClick={() => navigate(-1)} className="p-2 hover:bg-slate-100 rounded-lg active:scale-95">
                    <ArrowLeft className="w-5 h-5 text-slate-600" />
                </button>
                <div className="flex-1">
                    <p className="text-xs font-semibold uppercase tracking-wide text-rose-600">Rechner</p>
                    <h1 className="font-bold text-slate-900">Mehrwertsteuer</h1>
                </div>
                <Calculator className="w-6 h-6 text-rose-600" />
            </header>

            <div className="flex-1 overflow-auto p-4 space-y-4">
                <p className="text-sm text-slate-600 bg-white p-3 rounded-xl border border-slate-200">
                    Trage <strong>zwei</strong> der drei Werte ein — den dritten rechne ich automatisch aus.
                </p>

                <EingabeFeld label="Netto (ohne MwSt)" suffix="€" value={netto} onChange={setNetto} />
                <EingabeFeld label="Brutto (mit MwSt)" suffix="€" value={brutto} onChange={setBrutto} />
                <EingabeFeld label="MwSt-Satz" suffix="%" value={satz} onChange={setSatz} />

                <div className="flex gap-2 pt-2 flex-wrap">
                    {['7', '19'].map(s => (
                        <button
                            key={s}
                            onClick={() => setSatz(s)}
                            className={`px-4 py-2 rounded-full text-sm font-semibold border-2 ${
                                satz === s
                                    ? 'bg-rose-50 border-rose-500 text-rose-700'
                                    : 'bg-white border-slate-200 text-slate-600'
                            }`}
                        >
                            {s}%
                        </button>
                    ))}
                </div>

                <div className="grid grid-cols-2 gap-2 pt-2">
                    <button
                        onClick={handleClear}
                        className="px-4 py-3 bg-white border-2 border-slate-200 text-slate-700 font-semibold rounded-xl hover:bg-slate-50"
                    >
                        Leeren
                    </button>
                    <button
                        onClick={handleBerechnen}
                        disabled={loading}
                        className="px-4 py-3 bg-rose-600 hover:bg-rose-700 text-white font-bold rounded-xl flex items-center justify-center gap-2 disabled:opacity-50"
                    >
                        {loading ? <Loader2 className="w-5 h-5 animate-spin" /> : <Calculator className="w-5 h-5" />}
                        Rechnen
                    </button>
                </div>

                {error && (
                    <div className="flex items-start gap-2 p-3 bg-red-50 border border-red-200 text-red-700 rounded-xl text-sm">
                        <AlertCircle className="w-5 h-5 flex-shrink-0 mt-0.5" />
                        <span>{error}</span>
                    </div>
                )}

                {ergebnis && (
                    <div className="bg-white border border-slate-200 rounded-2xl divide-y divide-slate-100 overflow-hidden">
                        <ResultRow label="Netto" value={EUR.format(ergebnis.netto)} />
                        <ResultRow label={`MwSt (${formatDecimalInput(ergebnis.satzProzent)}%)`} value={EUR.format(ergebnis.mwstBetrag)} />
                        <ResultRow label="Brutto" value={EUR.format(ergebnis.brutto)} accent />
                    </div>
                )}
            </div>
        </div>
    )
}

function EingabeFeld({ label, suffix, value, onChange }: {
    label: string
    suffix: string
    value: string
    onChange: (v: string) => void
}) {
    return (
        <label className="block">
            <span className="text-xs font-semibold uppercase tracking-wide text-slate-500">{label}</span>
            <div className="relative mt-1">
                <DecimalInput
                    aria-label={label}
                    value={value}
                    onChange={onChange}
                    placeholder="0,00"
                    className="pr-12 text-lg tabular-nums"
                />
                <span className="pointer-events-none absolute right-4 top-3 text-slate-500 font-semibold">{suffix}</span>
            </div>
        </label>
    )
}

function ResultRow({ label, value, accent }: { label: string; value: string; accent?: boolean }) {
    return (
        <div className={`flex items-center justify-between px-4 py-3 ${accent ? 'bg-rose-50' : ''}`}>
            <span className={`text-sm ${accent ? 'font-bold text-rose-800' : 'text-slate-600'}`}>{label}</span>
            <span className={`tabular-nums ${accent ? 'font-bold text-rose-700 text-lg' : 'font-semibold text-slate-900'}`}>{value}</span>
        </div>
    )
}
