import { useEffect, useState } from 'react'
import { Loader2, Save, ShieldCheck, Plug, AlertCircle } from 'lucide-react'
import { Button } from './ui/button'
import { Input } from './ui/input'
import { Label } from './ui/label'
import { Select } from './ui/select-custom'
import { useToast } from './ui/toast'

type IdsProtokoll = 'IDS_CONNECT_2_5' | 'OCI_4_0'

interface IdsKonfigDto {
    aktiviert: boolean
    protokoll: IdsProtokoll
    punchoutUrl?: string | null
    kundennummer?: string | null
    loginName?: string | null
    /** Beim GET: leer oder "********" wenn hinterlegt. Beim PUT: Klartext oder "********" für unverändert. */
    passwort?: string | null
    notizen?: string | null
}

interface Props {
    lieferantId: number
    lieferantName: string
}

const PROTOKOLL_OPTIONS = [
    { value: 'IDS_CONNECT_2_5', label: 'IDS-Connect 2.5 (ZVSHK)' },
    { value: 'OCI_4_0', label: 'OCI 4.0 (SAP)' },
]

const PASSWORT_PLATZHALTER = '********'

export function LieferantIdsKonfigTab({ lieferantId, lieferantName }: Props) {
    const toast = useToast()
    const [loading, setLoading] = useState(true)
    const [saving, setSaving] = useState(false)
    const [forbidden, setForbidden] = useState(false)
    const [konfig, setKonfig] = useState<IdsKonfigDto>({
        aktiviert: false,
        protokoll: 'IDS_CONNECT_2_5',
        punchoutUrl: '',
        kundennummer: '',
        loginName: '',
        passwort: '',
        notizen: '',
    })

    useEffect(() => {
        let aborted = false
        const load = async () => {
            setLoading(true)
            setForbidden(false)
            try {
                const res = await fetch(`/api/admin/lieferanten/${lieferantId}/ids-konfig`)
                if (res.status === 403) {
                    if (!aborted) setForbidden(true)
                    return
                }
                if (!res.ok) {
                    throw new Error(`HTTP ${res.status}`)
                }
                const data: IdsKonfigDto = await res.json()
                if (!aborted) {
                    setKonfig({
                        aktiviert: !!data.aktiviert,
                        protokoll: data.protokoll || 'IDS_CONNECT_2_5',
                        punchoutUrl: data.punchoutUrl ?? '',
                        kundennummer: data.kundennummer ?? '',
                        loginName: data.loginName ?? '',
                        passwort: data.passwort ?? '',
                        notizen: data.notizen ?? '',
                    })
                }
            } catch (err) {
                console.error('IDS-Konfig laden fehlgeschlagen', err)
                toast.error('IDS-Konfig konnte nicht geladen werden.')
            } finally {
                if (!aborted) setLoading(false)
            }
        }
        load()
        return () => { aborted = true }
    }, [lieferantId, toast])

    const handleSave = async () => {
        setSaving(true)
        try {
            const res = await fetch(`/api/admin/lieferanten/${lieferantId}/ids-konfig`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(konfig),
            })
            if (res.status === 403) {
                toast.error('Nur Admins dürfen die Schnittstelle ändern.')
                return
            }
            if (!res.ok) {
                throw new Error(`HTTP ${res.status}`)
            }
            const data: IdsKonfigDto = await res.json()
            setKonfig({
                aktiviert: !!data.aktiviert,
                protokoll: data.protokoll || 'IDS_CONNECT_2_5',
                punchoutUrl: data.punchoutUrl ?? '',
                kundennummer: data.kundennummer ?? '',
                loginName: data.loginName ?? '',
                passwort: data.passwort ?? '',
                notizen: data.notizen ?? '',
            })
            toast.success('Schnittstelle gespeichert.')
        } catch (err) {
            console.error('IDS-Konfig speichern fehlgeschlagen', err)
            toast.error('Speichern fehlgeschlagen.')
        } finally {
            setSaving(false)
        }
    }

    if (loading) {
        return (
            <div className="flex items-center justify-center py-12 text-slate-500">
                <Loader2 className="w-6 h-6 animate-spin mr-2" />
                Schnittstelle wird geladen…
            </div>
        )
    }

    if (forbidden) {
        return (
            <div className="flex items-start gap-3 p-4 rounded-lg bg-rose-50 border border-rose-200 text-rose-900">
                <AlertCircle className="w-5 h-5 flex-shrink-0 mt-0.5" />
                <div>
                    <p className="font-semibold">Nur für Admins</p>
                    <p className="text-sm mt-1">
                        Schnittstellen-Konfigurationen enthalten Login-Daten und sind daher
                        nur mit Admin-Rechten einsehbar.
                    </p>
                </div>
            </div>
        )
    }

    return (
        <div className="max-w-2xl space-y-6">
            <div className="flex items-start gap-3 p-4 rounded-lg bg-slate-50 border border-slate-200">
                <Plug className="w-5 h-5 text-rose-500 flex-shrink-0 mt-0.5" />
                <div className="text-sm text-slate-700">
                    Mit IDS-Connect kannst Du im Online-Shop von <b>{lieferantName}</b> Material
                    aussuchen, der Warenkorb wird automatisch als Bestellung in dieses ERP
                    zurückgespielt. Die Zugangsdaten bekommst Du beim Lieferanten – bei Würth
                    z. B. von Deinem Außendienstler.
                </div>
            </div>

            <label className="flex items-center gap-3 p-3 rounded-lg border border-slate-200 cursor-pointer">
                <input
                    type="checkbox"
                    checked={konfig.aktiviert}
                    onChange={e => setKonfig(k => ({ ...k, aktiviert: e.target.checked }))}
                    className="w-5 h-5 rounded text-rose-600 focus:ring-rose-500"
                />
                <div className="flex-1">
                    <div className="text-sm font-medium text-slate-900">Schnittstelle aktiviert</div>
                    <div className="text-xs text-slate-500">
                        Nur aktivierte Schnittstellen erscheinen im Bestell-Dialog.
                    </div>
                </div>
                {konfig.aktiviert && <ShieldCheck className="w-4 h-4 text-rose-600" />}
            </label>

            <div className="space-y-4">
                <div>
                    <Label htmlFor="ids-protokoll">Protokoll</Label>
                    <Select
                        value={konfig.protokoll}
                        onChange={(value) => setKonfig(k => ({ ...k, protokoll: value as IdsProtokoll }))}
                        options={PROTOKOLL_OPTIONS}
                        placeholder="Protokoll wählen"
                    />
                </div>

                <div>
                    <Label htmlFor="ids-url">Punchout-URL</Label>
                    <Input
                        id="ids-url"
                        type="url"
                        value={konfig.punchoutUrl ?? ''}
                        onChange={e => setKonfig(k => ({ ...k, punchoutUrl: e.target.value }))}
                        placeholder="https://eshop.lieferant.de/.../IDSInBound"
                    />
                </div>

                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    <div>
                        <Label htmlFor="ids-kundennummer">Kundennummer</Label>
                        <Input
                            id="ids-kundennummer"
                            value={konfig.kundennummer ?? ''}
                            onChange={e => setKonfig(k => ({ ...k, kundennummer: e.target.value }))}
                            placeholder="887051"
                        />
                    </div>
                    <div>
                        <Label htmlFor="ids-login">Login-Name</Label>
                        <Input
                            id="ids-login"
                            value={konfig.loginName ?? ''}
                            onChange={e => setKonfig(k => ({ ...k, loginName: e.target.value }))}
                            placeholder="14137019"
                        />
                    </div>
                </div>

                <div>
                    <Label htmlFor="ids-passwort">Passwort</Label>
                    <Input
                        id="ids-passwort"
                        type="password"
                        value={konfig.passwort ?? ''}
                        onChange={e => setKonfig(k => ({ ...k, passwort: e.target.value }))}
                        placeholder="••••••••"
                        autoComplete="new-password"
                    />
                    <p className="text-xs text-slate-500 mt-1">
                        Wird verschlüsselt gespeichert. Leer lassen, um das hinterlegte Passwort
                        nicht zu ändern. „{PASSWORT_PLATZHALTER}" steht für „Passwort ist
                        hinterlegt".
                    </p>
                </div>

                <div>
                    <Label htmlFor="ids-notizen">Notizen (optional)</Label>
                    <textarea
                        id="ids-notizen"
                        value={konfig.notizen ?? ''}
                        onChange={e => setKonfig(k => ({ ...k, notizen: e.target.value }))}
                        rows={3}
                        className="w-full px-3 py-2 border border-slate-200 rounded-lg text-sm text-slate-900 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-rose-500"
                        placeholder="Ansprechpartner, Konditionen, …"
                    />
                </div>
            </div>

            <div className="flex justify-end pt-2 border-t border-slate-200">
                <Button onClick={handleSave} disabled={saving} className="bg-rose-600 text-white hover:bg-rose-700">
                    {saving ? (
                        <>
                            <Loader2 className="w-4 h-4 animate-spin mr-2" />
                            Speichern…
                        </>
                    ) : (
                        <>
                            <Save className="w-4 h-4 mr-2" />
                            Schnittstelle speichern
                        </>
                    )}
                </Button>
            </div>
        </div>
    )
}
