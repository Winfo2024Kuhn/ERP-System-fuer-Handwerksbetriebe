import { useEffect, useState } from 'react';
import { AlertTriangle, Loader2, X } from 'lucide-react';
import { Button } from '../ui/button';
import { DatePicker } from '../ui/datepicker';
import type { KasseEinstellung } from '../../types';
import { formatEuro, modalInputCls } from './belegFormat';

// Task 9 (reine Verschiebung, kein Verhalten geaendert): die vier
// Buchungs-Modale aus KasseShortcuts.tsx (BankAbhebungModal 216-291,
// EinfacheKasseModal 295-408, LohnZahlungModal 414-513) samt ModalShell,
// FieldRow, ModalFooter. KasseShortcuts.tsx importiert sie und ruft sie wie
// bisher auf.

export interface SaldoInfo {
    saldo: number;
    mindestbestand: number;
}

const todayIso = () => new Date().toISOString().slice(0, 10);

// ===================== Bank-Abhebung =====================

export function BankAbhebungModal({ onClose, onSuccess, onError, saldo }: {
    onClose: () => void;
    onSuccess: (msg: string) => void;
    onError: (msg: string) => void;
    saldo: SaldoInfo | null;
}) {
    const [betrag, setBetrag] = useState<string>('');
    const [datum, setDatum] = useState<string>(todayIso());
    const [belegNr, setBelegNr] = useState<string>('');
    const [beschreibung, setBeschreibung] = useState<string>('');
    const [saving, setSaving] = useState(false);

    const projSaldo = saldo && betrag !== '' && !Number.isNaN(Number(betrag))
        ? saldo.saldo + Number(betrag) : null;

    const submit = async () => {
        const b = Number(betrag);
        if (!betrag || !Number.isFinite(b) || b <= 0) {
            onError('Bitte einen positiven Betrag eingeben.');
            return;
        }
        setSaving(true);
        try {
            const res = await fetch('/api/buchhaltung/kasse/bank-abhebung', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ betrag: b, datum, belegNr: belegNr || null, beschreibung: beschreibung || null }),
            });
            if (res.ok) {
                onSuccess(`Bank → Kasse: ${formatEuro(b)} € gebucht`);
            } else {
                const body = await res.json().catch(() => null);
                onError(body?.message ?? 'Buchung fehlgeschlagen');
            }
        } catch (e) {
            console.error(e);
            onError('Netzwerkfehler');
        } finally {
            setSaving(false);
        }
    };

    return (
        <ModalShell title="Bank → Kasse (Abhebung)" onClose={onClose}>
            <p className="text-sm text-slate-600 mb-3">
                Bargeld, das du gerade bei der Bank geholt hast — wird als Kassen-Eingang gebucht.
            </p>
            <FieldRow label="Betrag (€)">
                <input type="number" step="0.01" value={betrag}
                    onChange={e => setBetrag(e.target.value)}
                    className={modalInputCls} autoFocus />
            </FieldRow>
            <FieldRow label="Datum">
                <DatePicker value={datum} onChange={setDatum} />
            </FieldRow>
            <FieldRow label="Beleg-Nr. (optional)">
                <input type="text" value={belegNr}
                    onChange={e => setBelegNr(e.target.value)}
                    placeholder="z.B. EC-Auszug-Nr."
                    className={modalInputCls} />
            </FieldRow>
            <FieldRow label="Beschreibung (optional)">
                <input type="text" value={beschreibung}
                    onChange={e => setBeschreibung(e.target.value)}
                    placeholder="z.B. Bargeld geholt"
                    className={modalInputCls} />
            </FieldRow>
            {projSaldo != null && (
                <div className="text-xs text-slate-500 mt-2">
                    Kassenstand nachher: <strong className="text-slate-700">{formatEuro(projSaldo)} €</strong>
                </div>
            )}
            <ModalFooter onClose={onClose} onSubmit={submit} saving={saving} label="Buchen" />
        </ModalShell>
    );
}

// ===================== Privateinlage / Privatentnahme =====================

export function EinfacheKasseModal({ titel, endpoint, defaultBeschreibung, onClose, onSuccess, onError }: {
    titel: string;
    endpoint: string;
    defaultBeschreibung: string;
    onClose: () => void;
    onSuccess: (msg: string) => void;
    onError: (msg: string) => void;
}) {
    const [betrag, setBetrag] = useState<string>('');
    const [datum, setDatum] = useState<string>(todayIso());
    const [beschreibung, setBeschreibung] = useState<string>(defaultBeschreibung);
    const [saving, setSaving] = useState(false);
    const [konflikt, setKonflikt] = useState<{ projizierterSaldo: number; mindestbestand: number; message: string } | null>(null);

    const submit = async (): Promise<void> => {
        const b = Number(betrag);
        if (!betrag || !Number.isFinite(b) || b <= 0) {
            onError('Bitte einen positiven Betrag eingeben.');
            return;
        }
        setSaving(true);
        setKonflikt(null);
        try {
            const res = await fetch(endpoint, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ betrag: b, datum, beschreibung: beschreibung || null }),
            });
            if (res.ok) {
                onSuccess(`${titel}: ${formatEuro(b)} € gebucht`);
                return;
            }
            if (res.status === 409) {
                const body = await res.json();
                setKonflikt({
                    projizierterSaldo: Number(body.projizierterSaldo),
                    mindestbestand: Number(body.mindestbestand),
                    message: body.message ?? 'Kasse würde unter Mindestbestand fallen',
                });
                return;
            }
            const body = await res.json().catch(() => null);
            onError(body?.message ?? 'Buchung fehlgeschlagen');
        } catch (e) {
            console.error(e);
            onError('Netzwerkfehler');
        } finally {
            setSaving(false);
        }
    };

    // 1-Klick-Vorschlag bei 409: erst Privateinlage in passender Hoehe buchen,
    // dann den Original-Versuch wiederholen.
    const loeseUnterdeckung = async () => {
        if (!konflikt) return;
        const benoetigt = Math.max(0, konflikt.mindestbestand - konflikt.projizierterSaldo);
        if (benoetigt <= 0) {
            setKonflikt(null);
            return;
        }
        setSaving(true);
        try {
            const res1 = await fetch('/api/buchhaltung/kasse/privateinlage', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ betrag: benoetigt, datum, beschreibung: 'Vorab-Einlage fuer Privatentnahme' }),
            });
            if (!res1.ok) {
                onError('Vorab-Einlage fehlgeschlagen');
                return;
            }
            setKonflikt(null);
            await submit();
        } finally {
            setSaving(false);
        }
    };

    return (
        <ModalShell title={titel} onClose={onClose}>
            <FieldRow label="Betrag (€)">
                <input type="number" step="0.01" value={betrag}
                    onChange={e => setBetrag(e.target.value)}
                    className={modalInputCls} autoFocus />
            </FieldRow>
            <FieldRow label="Datum">
                <DatePicker value={datum} onChange={setDatum} />
            </FieldRow>
            <FieldRow label="Beschreibung (optional)">
                <input type="text" value={beschreibung}
                    onChange={e => setBeschreibung(e.target.value)}
                    className={modalInputCls} />
            </FieldRow>

            {konflikt && (
                <div className="mt-3 bg-amber-50 border border-amber-200 rounded-lg p-3 text-sm text-amber-900">
                    <div className="flex items-start gap-2">
                        <AlertTriangle className="w-4 h-4 mt-0.5 flex-shrink-0" />
                        <div className="flex-1">
                            <p className="font-medium">Kasse würde auf {formatEuro(konflikt.projizierterSaldo)} € rutschen.</p>
                            <p className="text-xs mt-1">Mindestbestand: {formatEuro(konflikt.mindestbestand)} €</p>
                            <Button size="sm" className="mt-2 bg-rose-600 text-white border border-rose-600 hover:bg-rose-700"
                                onClick={loeseUnterdeckung} disabled={saving}>
                                Privateinlage in Höhe {formatEuro(Math.max(0, konflikt.mindestbestand - konflikt.projizierterSaldo))} € vorab buchen?
                            </Button>
                        </div>
                    </div>
                </div>
            )}

            <ModalFooter onClose={onClose} onSubmit={submit} saving={saving} label="Buchen" />
        </ModalShell>
    );
}

// ===================== Ehegattengehalt-Lohnzahlung =====================

// Buchungskonto ist hart "4120 Loehne & Gehaelter" (Backend), keine Kosten-
// stelle. Der Handwerker soll nichts auswaehlen muessen — reine Buchhaltung.
export function LohnZahlungModal({ onClose, onSuccess, onError }: {
    onClose: () => void;
    onSuccess: (msg: string) => void;
    onError: (msg: string) => void;
}) {
    const [einstellung, setEinstellung] = useState<KasseEinstellung | null>(null);
    const [betrag, setBetrag] = useState<string>('');
    const [datum, setDatum] = useState<string>(todayIso());
    const [empfaenger, setEmpfaenger] = useState<string>('');
    const [saldo, setSaldo] = useState<SaldoInfo | null>(null);
    const [saving, setSaving] = useState(false);

    useEffect(() => {
        fetch('/api/buchhaltung/kasse/einstellung')
            .then(r => r.ok ? r.json() : null)
            .then((e: KasseEinstellung | null) => {
                if (!e) return;
                setEinstellung(e);
                if (e.ehegattengehaltBetrag) setBetrag(String(e.ehegattengehaltBetrag));
                if (e.ehegattengehaltEmpfaengerName) setEmpfaenger(e.ehegattengehaltEmpfaengerName);
            })
            .catch(err => console.error(err));
        fetch('/api/buchhaltung/kasse/saldo')
            .then(r => r.ok ? r.json() : null)
            .then((s: SaldoInfo | null) => s && setSaldo(s))
            .catch(err => console.error(err));
    }, []);

    const b = Number(betrag);
    const valid = Number.isFinite(b) && b > 0;
    const projSaldo = saldo && valid ? saldo.saldo - b : null;
    const benoetigteEinlage = projSaldo != null && saldo
        ? Math.max(0, saldo.mindestbestand - projSaldo) : 0;

    const submit = async () => {
        if (!valid) { onError('Bitte einen positiven Betrag eingeben.'); return; }
        setSaving(true);
        try {
            const res = await fetch('/api/buchhaltung/kasse/lohn-zahlung', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    betrag: b, datum, empfaengerName: empfaenger || null,
                }),
            });
            if (res.ok) {
                onSuccess(`Ehegattengehalt ${formatEuro(b)} € gebucht`);
            } else {
                const body = await res.json().catch(() => null);
                onError(body?.message ?? 'Buchung fehlgeschlagen');
            }
        } catch (e) {
            console.error(e);
            onError('Netzwerkfehler');
        } finally {
            setSaving(false);
        }
    };

    return (
        <ModalShell title="Ehegattengehalt zahlen" onClose={onClose}>
            {einstellung && einstellung.ehegattengehaltAktiv && (
                <div className="mb-3 text-xs text-slate-500 bg-slate-50 border border-slate-200 rounded p-2">
                    Default-Werte aus den Einstellungen geladen. Anpassbar für diese Buchung.
                </div>
            )}
            <div className="mb-3 text-xs text-slate-500 bg-rose-50 border border-rose-200 rounded p-2">
                Wird automatisch auf <strong>Löhne &amp; Gehälter</strong> gebucht — reine Buchhaltung, keine Kostenstelle.
            </div>
            <FieldRow label="Empfänger (Name)">
                <input type="text" value={empfaenger}
                    onChange={e => setEmpfaenger(e.target.value)}
                    className={modalInputCls} placeholder="z.B. Diana Mustermann" />
            </FieldRow>
            <FieldRow label="Monat (zur Notiz)">
                <DatePicker value={datum} onChange={setDatum} />
            </FieldRow>
            <FieldRow label="Betrag (€)">
                <input type="number" step="0.01" value={betrag}
                    onChange={e => setBetrag(e.target.value)}
                    className={modalInputCls} autoFocus />
            </FieldRow>

            {projSaldo != null && saldo && (
                <div className="mt-3 text-xs text-slate-600 bg-slate-50 border border-slate-200 rounded p-2 space-y-1">
                    <div>Kassenstand jetzt: <strong>{formatEuro(saldo.saldo)} €</strong></div>
                    <div>Kassenstand nach Zahlung: <strong className={projSaldo < saldo.mindestbestand ? 'text-red-700' : 'text-slate-700'}>{formatEuro(projSaldo)} €</strong></div>
                    {benoetigteEinlage > 0 && (
                        <div className="text-amber-700 inline-flex items-center gap-1">
                            <AlertTriangle className="w-3 h-3" />
                            Auto-Privateinlage in Höhe {formatEuro(benoetigteEinlage)} € wird vorab gebucht.
                        </div>
                    )}
                </div>
            )}

            <ModalFooter onClose={onClose} onSubmit={submit} saving={saving} label="Lohn buchen" />
        </ModalShell>
    );
}

// ===================== Modal Shell + helpers =====================

function ModalShell({ title, onClose, wide, children }: {
    title: string;
    onClose: () => void;
    wide?: boolean;
    children: React.ReactNode;
}) {
    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
            <div className={`bg-white rounded-xl shadow-2xl w-full ${wide ? 'max-w-xl' : 'max-w-md'} max-h-[90vh] flex flex-col`}>
                <div className="px-4 py-3 border-b border-slate-200 flex items-center justify-between">
                    <h2 className="font-semibold text-slate-900">{title}</h2>
                    <button onClick={onClose} className="p-1.5 hover:bg-slate-100 rounded-full">
                        <X className="w-4 h-4 text-slate-500" />
                    </button>
                </div>
                <div className="p-4 overflow-auto">
                    {children}
                </div>
            </div>
        </div>
    );
}

function FieldRow({ label, children }: { label: string; children: React.ReactNode }) {
    return (
        <div className="mb-3">
            <label className="block text-xs font-semibold uppercase tracking-wide text-slate-500 mb-1">{label}</label>
            {children}
        </div>
    );
}

function ModalFooter({ onClose, onSubmit, saving, label }: {
    onClose: () => void;
    onSubmit: () => Promise<void> | void;
    saving: boolean;
    label: string;
}) {
    return (
        <div className="flex justify-end gap-2 mt-4 pt-3 border-t border-slate-200">
            <Button variant="outline" size="sm" onClick={onClose} disabled={saving}>Abbrechen</Button>
            <Button size="sm" onClick={onSubmit} disabled={saving}
                className="bg-rose-600 text-white border border-rose-600 hover:bg-rose-700">
                {saving ? <Loader2 className="w-4 h-4 mr-2 animate-spin" /> : null}
                {label}
            </Button>
        </div>
    );
}

export { ModalShell, FieldRow, ModalFooter };
