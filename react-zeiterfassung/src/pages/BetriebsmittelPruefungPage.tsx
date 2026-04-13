import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { ArrowLeft, CheckCircle2, XCircle, AlertTriangle, Send, Loader2 } from 'lucide-react'

interface Betriebsmittel {
    id: number
    bezeichnung: string
    seriennummer?: string
    barcode?: string
    hersteller?: string
    modell?: string
    standort?: string
    naechstesPruefDatum?: string
    pruefIntervallMonate: number
    ausserBetrieb: boolean
}

interface PruefFormData {
    pruefDatum: string
    bestanden: boolean
    schutzklasse: string
    messwertSchutzleiter: string
    messwertIsolationswiderstand: string
    messwertAbleitstrom: string
    bemerkung: string
}

export default function BetriebsmittelPruefungPage() {
    const { id } = useParams<{ id: string }>()
    const navigate = useNavigate()
    const [geraet, setGeraet] = useState<Betriebsmittel | null>(null)
    const [loading, setLoading] = useState(true)
    const [submitting, setSubmitting] = useState(false)
    const [success, setSuccess] = useState(false)
    const [error, setError] = useState<string | null>(null)

    const [form, setForm] = useState<PruefFormData>({
        pruefDatum: new Date().toISOString().slice(0, 10),
        bestanden: true,
        schutzklasse: 'SK I',
        messwertSchutzleiter: '',
        messwertIsolationswiderstand: '',
        messwertAbleitstrom: '',
        bemerkung: '',
    })

    useEffect(() => {
        const token = localStorage.getItem('zeiterfassung_token')
        if (!token || !id) return
        fetch(`/api/betriebsmittel/${id}?token=${encodeURIComponent(token)}`)
            .then(r => r.ok ? r.json() : null)
            .then(setGeraet)
            .catch(() => setGeraet(null))
            .finally(() => setLoading(false))
    }, [id])

    const updateField = <K extends keyof PruefFormData>(field: K, value: PruefFormData[K]) => {
        setForm(prev => ({ ...prev, [field]: value }))
    }

    const handleSubmit = async () => {
        if (submitting) return
        setSubmitting(true)
        setError(null)

        const token = localStorage.getItem('zeiterfassung_token')
        const mitarbeiterStr = localStorage.getItem('zeiterfassung_mitarbeiter')
        let prueferId: number | undefined
        if (mitarbeiterStr) {
            try {
                prueferId = JSON.parse(mitarbeiterStr).id
            } catch { /* ignore */ }
        }

        const body = {
            pruefDatum: form.pruefDatum,
            bestanden: form.bestanden,
            schutzklasse: form.schutzklasse || null,
            messwertSchutzleiter: form.messwertSchutzleiter ? parseFloat(form.messwertSchutzleiter) : null,
            messwertIsolationswiderstand: form.messwertIsolationswiderstand ? parseFloat(form.messwertIsolationswiderstand) : null,
            messwertAbleitstrom: form.messwertAbleitstrom ? parseFloat(form.messwertAbleitstrom) : null,
            bemerkung: form.bemerkung || null,
        }

        const url = prueferId
            ? `/api/betriebsmittel/${id}/pruefungen?prueferId=${prueferId}&token=${encodeURIComponent(token || '')}`
            : `/api/betriebsmittel/${id}/pruefungen?token=${encodeURIComponent(token || '')}`

        try {
            const res = await fetch(url, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(body),
            })

            if (res.ok) {
                setSuccess(true)
                setTimeout(() => navigate('/betriebsmittel', { replace: true }), 1500)
            } else {
                const text = await res.text()
                setError(text || 'Fehler beim Speichern')
            }
        } catch {
            setError('Keine Verbindung zum Server')
        } finally {
            setSubmitting(false)
        }
    }

    if (loading) {
        return (
            <div className="min-h-screen bg-slate-50 flex items-center justify-center">
                <Loader2 className="w-8 h-8 text-rose-600 animate-spin" />
            </div>
        )
    }

    if (!geraet) {
        return (
            <div className="min-h-screen bg-slate-50 flex flex-col items-center justify-center gap-3 px-6">
                <XCircle className="w-12 h-12 text-red-400" />
                <p className="text-slate-700 font-medium">Gerät nicht gefunden</p>
                <button onClick={() => navigate('/betriebsmittel')} className="text-rose-600 text-sm font-medium">
                    Zurück zur Liste
                </button>
            </div>
        )
    }

    if (success) {
        return (
            <div className="min-h-screen bg-slate-50 flex flex-col items-center justify-center gap-3 px-6">
                <div className="w-16 h-16 rounded-full bg-green-100 flex items-center justify-center">
                    <CheckCircle2 className="w-8 h-8 text-green-600" />
                </div>
                <p className="text-lg font-bold text-slate-900">Prüfung gespeichert</p>
                <p className="text-sm text-slate-500">{geraet.bezeichnung}</p>
            </div>
        )
    }

    const faellig = !geraet.naechstesPruefDatum || geraet.naechstesPruefDatum <= new Date().toISOString().slice(0, 10)

    return (
        <div className="min-h-screen bg-slate-50 flex flex-col">
            {/* Header */}
            <div className="sticky top-0 z-10 bg-white border-b border-slate-200 px-4 py-3 safe-area-top">
                <div className="flex items-center gap-3">
                    <button onClick={() => navigate('/betriebsmittel')} className="p-2 -ml-2 rounded-lg hover:bg-slate-100">
                        <ArrowLeft className="w-5 h-5 text-slate-700" />
                    </button>
                    <div className="flex-1 min-w-0">
                        <h1 className="text-lg font-bold text-slate-900 truncate">{geraet.bezeichnung}</h1>
                        <p className="text-xs text-slate-500 truncate">
                            {[geraet.hersteller, geraet.modell].filter(Boolean).join(' · ')}
                            {geraet.seriennummer ? ` · SN: ${geraet.seriennummer}` : ''}
                        </p>
                    </div>
                    {faellig && (
                        <span className="px-2 py-1 rounded-full bg-red-100 text-red-700 text-xs font-semibold flex-shrink-0">
                            Fällig
                        </span>
                    )}
                </div>
            </div>

            {/* Form */}
            <main className="flex-1 px-4 py-4 space-y-4 pb-32">
                {/* Prüfdatum */}
                <div>
                    <label className="block text-sm font-medium text-slate-700 mb-1">Prüfdatum</label>
                    <input
                        type="date"
                        value={form.pruefDatum}
                        onChange={e => updateField('pruefDatum', e.target.value)}
                        className="w-full px-3 py-2.5 rounded-xl border border-slate-200 bg-white text-sm focus:outline-none focus:ring-2 focus:ring-rose-500"
                    />
                </div>

                {/* Schutzklasse */}
                <div>
                    <label className="block text-sm font-medium text-slate-700 mb-1">Schutzklasse</label>
                    <div className="flex gap-2">
                        {['SK I', 'SK II', 'SK III'].map(sk => (
                            <button
                                key={sk}
                                onClick={() => updateField('schutzklasse', sk)}
                                className={`flex-1 py-2.5 rounded-xl text-sm font-medium transition-colors ${
                                    form.schutzklasse === sk
                                        ? 'bg-rose-600 text-white'
                                        : 'bg-white border border-slate-200 text-slate-700 hover:border-rose-200'
                                }`}
                            >
                                {sk}
                            </button>
                        ))}
                    </div>
                </div>

                {/* Messwerte */}
                <div className="bg-white rounded-xl border border-slate-200 p-4 space-y-3">
                    <h3 className="text-sm font-semibold text-slate-800">Messwerte</h3>

                    <div>
                        <label className="block text-xs text-slate-500 mb-1">Schutzleiterwiderstand (Ω)</label>
                        <input
                            type="number"
                            step="0.0001"
                            inputMode="decimal"
                            value={form.messwertSchutzleiter}
                            onChange={e => updateField('messwertSchutzleiter', e.target.value)}
                            placeholder="z.B. 0.3"
                            className="w-full px-3 py-2.5 rounded-xl border border-slate-200 bg-slate-50 text-sm focus:outline-none focus:ring-2 focus:ring-rose-500"
                        />
                    </div>

                    <div>
                        <label className="block text-xs text-slate-500 mb-1">Isolationswiderstand (MΩ)</label>
                        <input
                            type="number"
                            step="0.0001"
                            inputMode="decimal"
                            value={form.messwertIsolationswiderstand}
                            onChange={e => updateField('messwertIsolationswiderstand', e.target.value)}
                            placeholder="z.B. 1.0"
                            className="w-full px-3 py-2.5 rounded-xl border border-slate-200 bg-slate-50 text-sm focus:outline-none focus:ring-2 focus:ring-rose-500"
                        />
                    </div>

                    <div>
                        <label className="block text-xs text-slate-500 mb-1">Ableitstrom (mA)</label>
                        <input
                            type="number"
                            step="0.0001"
                            inputMode="decimal"
                            value={form.messwertAbleitstrom}
                            onChange={e => updateField('messwertAbleitstrom', e.target.value)}
                            placeholder="z.B. 0.5"
                            className="w-full px-3 py-2.5 rounded-xl border border-slate-200 bg-slate-50 text-sm focus:outline-none focus:ring-2 focus:ring-rose-500"
                        />
                    </div>
                </div>

                {/* Bemerkung */}
                <div>
                    <label className="block text-sm font-medium text-slate-700 mb-1">Bemerkung</label>
                    <textarea
                        value={form.bemerkung}
                        onChange={e => updateField('bemerkung', e.target.value)}
                        rows={3}
                        placeholder="Optionale Anmerkungen zur Prüfung..."
                        className="w-full px-3 py-2.5 rounded-xl border border-slate-200 bg-white text-sm focus:outline-none focus:ring-2 focus:ring-rose-500 resize-none"
                    />
                </div>

                {/* Bestanden Toggle */}
                <div className="bg-white rounded-xl border border-slate-200 p-4">
                    <div className="flex items-center justify-between">
                        <span className="text-sm font-medium text-slate-700">Prüfung bestanden?</span>
                        <div className="flex gap-2">
                            <button
                                onClick={() => updateField('bestanden', true)}
                                className={`flex items-center gap-1.5 px-4 py-2 rounded-xl text-sm font-medium transition-colors ${
                                    form.bestanden
                                        ? 'bg-green-600 text-white'
                                        : 'bg-slate-100 text-slate-500 hover:bg-slate-200'
                                }`}
                            >
                                <CheckCircle2 className="w-4 h-4" /> Ja
                            </button>
                            <button
                                onClick={() => updateField('bestanden', false)}
                                className={`flex items-center gap-1.5 px-4 py-2 rounded-xl text-sm font-medium transition-colors ${
                                    !form.bestanden
                                        ? 'bg-red-600 text-white'
                                        : 'bg-slate-100 text-slate-500 hover:bg-slate-200'
                                }`}
                            >
                                <XCircle className="w-4 h-4" /> Nein
                            </button>
                        </div>
                    </div>
                    {!form.bestanden && (
                        <div className="mt-3 p-3 bg-red-50 rounded-lg flex items-start gap-2">
                            <AlertTriangle className="w-4 h-4 text-red-500 mt-0.5 flex-shrink-0" />
                            <p className="text-xs text-red-700">Gerät wird als nicht bestanden markiert und muss vor der Wiederinbetriebnahme repariert werden.</p>
                        </div>
                    )}
                </div>

                {/* Fehler */}
                {error && (
                    <div className="p-3 bg-red-50 border border-red-200 rounded-xl">
                        <p className="text-sm text-red-700">{error}</p>
                    </div>
                )}
            </main>

            {/* Submit Button (fixed bottom) */}
            <div className="fixed bottom-0 left-0 right-0 bg-white border-t border-slate-200 p-4 safe-area-bottom">
                <button
                    onClick={handleSubmit}
                    disabled={submitting}
                    className="w-full py-3.5 rounded-xl bg-rose-600 text-white font-semibold hover:bg-rose-700 disabled:opacity-50 transition-colors flex items-center justify-center gap-2"
                >
                    {submitting ? (
                        <Loader2 className="w-5 h-5 animate-spin" />
                    ) : (
                        <Send className="w-5 h-5" />
                    )}
                    Prüfung speichern
                </button>
            </div>
        </div>
    )
}
