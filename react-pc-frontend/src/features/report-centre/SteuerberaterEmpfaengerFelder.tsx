import { Loader2 } from 'lucide-react';
import { Input } from '../../components/ui/input';
import { Label } from '../../components/ui/label';
import { Select } from '../../components/ui/select-custom';
import type { SteuerberaterEmpfaenger } from './useSteuerberaterEmpfaenger';

/** Steuerberater (nur bei mehreren), Ansprechpartner und Empfänger der Stundenübermittlung. */
export function SteuerberaterEmpfaengerFelder({ empfaenger: e }: { empfaenger: SteuerberaterEmpfaenger }) {
    if (e.laedt) {
        return (
            <div className="flex items-center gap-2 text-sm text-slate-500">
                <Loader2 aria-hidden="true" className="h-4 w-4 motion-safe:animate-spin" /> Steuerberater werden geladen …
            </div>
        );
    }
    return (
        <>
            {e.liste.length === 0 ? (
                <div className="rounded-lg border border-rose-200 bg-rose-50 p-3 text-sm text-rose-700">
                    Es ist noch kein Steuerberater hinterlegt. Bitte unter <strong>Firma → Steuerberater</strong>
                    {' '}einen Kontakt mit mindestens einem Ansprechpartner anlegen.
                </div>
            ) : (
                <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
                    {e.liste.length > 1 && (
                        <div className="space-y-2">
                            <Label>Steuerberater</Label>
                            <Select aria-label="Steuerberater" options={e.optionen.steuerberater}
                                value={e.steuerberaterId !== null ? String(e.steuerberaterId) : ''}
                                onChange={v => e.waehleSteuerberater(v ? Number(v) : null)} placeholder="Steuerberater wählen" />
                        </div>
                    )}
                    <div className="space-y-2">
                        <Label>Ansprechpartner</Label>
                        {e.optionen.ansprechpartner.length > 0 ? (
                            <Select aria-label="Ansprechpartner" options={e.optionen.ansprechpartner}
                                value={e.ansprechpartnerId !== null ? String(e.ansprechpartnerId) : ''}
                                onChange={v => e.waehleAnsprechpartner(v ? Number(v) : null)} placeholder="Ansprechpartner wählen" />
                        ) : (
                            <p className="text-xs text-slate-600">
                                Kein Ansprechpartner hinterlegt – Anrede wird „Sehr geehrte Damen und Herren“ verwendet.
                            </p>
                        )}
                    </div>
                </div>
            )}
            <div className="space-y-2">
                <Label htmlFor="stundenuebermittlung-empfaenger">Empfänger</Label>
                {e.optionen.empfaenger.length > 1 ? (
                    <Select id="stundenuebermittlung-empfaenger" aria-label="Empfänger" options={e.optionen.empfaenger} value={e.empfaenger}
                        onChange={v => e.waehleEmpfaenger(v)} placeholder="E-Mail-Adresse wählen" />
                ) : (
                    <Input id="stundenuebermittlung-empfaenger" value={e.empfaenger} onChange={ev => e.waehleEmpfaenger(ev.target.value)}
                        placeholder="E-Mail-Adresse..." />
                )}
            </div>
        </>
    );
}
