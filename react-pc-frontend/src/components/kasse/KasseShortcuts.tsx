import { useCallback, useEffect, useState } from 'react';
import {
    Banknote, ArrowDownToLine, ArrowUpFromLine, UserSquare2,
    Settings, X, Loader2, Coins, AlertTriangle,
} from 'lucide-react';
import { Card } from '../ui/card';
import { Button } from '../ui/button';
import { Select } from '../ui/select-custom';
import { DatePicker } from '../ui/datepicker';

// Saldo-Bar + 4 Shortcut-Buttons + Settings (Issue #59).
//
// Die Komponente kapselt alle vier Buchungs-Modale (Bank-Abhebung,
// Ehegattengehalt, Privateinlage, Privatentnahme) plus das Settings-Modal.
// Sie ruft `onChanged()` nach erfolgreicher Buchung auf, damit der Parent
// die Beleg-Liste und den Kassenbuch-View neu lädt.

type SachkontoTyp = 'AUFWAND' | 'ERTRAG' | 'PRIVAT' | 'NEUTRAL';

interface Sachkonto {
    id: number;
    nummer?: string | null;
    bezeichnung: string;
    kontoTyp: SachkontoTyp;
    aktiv: boolean;
    sortierung: number;
}

interface SaldoInfo {
    saldo: number;
    mindestbestand: number;
}

interface KasseEinstellung {
    id?: number | null;
    mindestbestand: number;
    ehegattengehaltAktiv: boolean;
    ehegattengehaltBetrag?: number | null;
    ehegattengehaltTag?: number | null;
    ehegattengehaltEmpfaengerName?: string | null;
    privateinlageSachkontoId?: number | null;
}

interface KasseShortcutsProps {
    sachkonten: Sachkonto[];
    onChanged: () => void;
}

const formatEuro = (v: number | null | undefined): string =>
    v == null || !Number.isFinite(v)
        ? '–'
        : new Intl.NumberFormat('de-DE', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(v);

const todayIso = () => new Date().toISOString().slice(0, 10);

export function KasseShortcuts({ sachkonten, onChanged }: KasseShortcutsProps) {
    const [saldo, setSaldo] = useState<SaldoInfo | null>(null);
    const [openModal, setOpenModal] = useState<null | 'bank' | 'lohn' | 'einlage' | 'entnahme' | 'settings'>(null);
    const [toast, setToast] = useState<{ kind: 'ok' | 'err'; text: string } | null>(null);

    const loadSaldo = useCallback(async () => {
        try {
            const res = await fetch('/api/buchhaltung/kasse/saldo');
            if (res.ok) setSaldo(await res.json());
        } catch (e) {
            console.error('Saldo laden fehlgeschlagen', e);
        }
    }, []);

    // Initial-Load via async-Wrapper, damit der set-state-in-effect-Lint
    // nicht anschlägt — Standard-Pattern für „fetch on mount".
    useEffect(() => {
        let cancelled = false;
        (async () => {
            try {
                const res = await fetch('/api/buchhaltung/kasse/saldo');
                if (res.ok && !cancelled) setSaldo(await res.json());
            } catch (e) {
                console.error('Saldo laden fehlgeschlagen', e);
            }
        })();
        return () => { cancelled = true; };
    }, []);

    // Toast nach 4s automatisch ausblenden — kein alert(), kein blocking dialog.
    useEffect(() => {
        if (!toast) return;
        const t = setTimeout(() => setToast(null), 4000);
        return () => clearTimeout(t);
    }, [toast]);

    const refreshAlles = useCallback(() => {
        loadSaldo();
        onChanged();
    }, [loadSaldo, onChanged]);

    const showToast = (kind: 'ok' | 'err', text: string) => setToast({ kind, text });

    const saldoUnterMindestbestand = saldo != null && saldo.saldo < saldo.mindestbestand;

    return (
        <Card className="p-4 bg-gradient-to-r from-rose-50 to-white border-rose-200">
            <div className="flex flex-wrap items-center gap-4">
                <div className="flex items-center gap-3 mr-4">
                    <div className="bg-rose-100 text-rose-700 rounded-lg p-2">
                        <Coins className="w-5 h-5" />
                    </div>
                    <div>
                        <div className="text-xs uppercase tracking-wide text-rose-700 font-semibold">Aktueller Kassenstand</div>
                        <div className="flex items-baseline gap-2">
                            <span className={`text-2xl font-bold ${saldoUnterMindestbestand ? 'text-red-700' : 'text-slate-900'}`}>
                                {saldo ? `${formatEuro(saldo.saldo)} €` : '–'}
                            </span>
                            {saldo && saldo.mindestbestand > 0 && (
                                <span className="text-xs text-slate-500">
                                    Mindestbestand: {formatEuro(saldo.mindestbestand)} €
                                </span>
                            )}
                            {saldoUnterMindestbestand && (
                                <span className="inline-flex items-center gap-1 text-xs text-red-700 bg-red-50 border border-red-200 rounded px-2 py-0.5">
                                    <AlertTriangle className="w-3 h-3" /> unter Mindestbestand
                                </span>
                            )}
                        </div>
                    </div>
                </div>

                <div className="flex flex-wrap items-center gap-2 ml-auto">
                    <Button variant="outline" size="sm" onClick={() => setOpenModal('bank')}
                        className="border-rose-300 text-rose-700 hover:bg-rose-50"
                        title="Bargeld von der Bank in die Kasse legen">
                        <Banknote className="w-4 h-4 mr-2" /> Bank → Kasse
                    </Button>
                    <Button variant="outline" size="sm" onClick={() => setOpenModal('lohn')}
                        className="border-rose-300 text-rose-700 hover:bg-rose-50"
                        title="Ehegattengehalt aus der Kasse auszahlen">
                        <UserSquare2 className="w-4 h-4 mr-2" /> Ehegattengehalt
                    </Button>
                    <Button variant="outline" size="sm" onClick={() => setOpenModal('einlage')}
                        className="border-rose-300 text-rose-700 hover:bg-rose-50"
                        title="Privates Geld in die Firma einlegen">
                        <ArrowDownToLine className="w-4 h-4 mr-2" /> Privateinlage
                    </Button>
                    <Button variant="outline" size="sm" onClick={() => setOpenModal('entnahme')}
                        className="border-rose-300 text-rose-700 hover:bg-rose-50"
                        title="Bargeld aus der Firma ins Private nehmen">
                        <ArrowUpFromLine className="w-4 h-4 mr-2" /> Privatentnahme
                    </Button>
                    <Button variant="ghost" size="sm" onClick={() => setOpenModal('settings')}
                        className="text-rose-700 hover:bg-rose-100"
                        title="Mindestbestand & Automatik einstellen">
                        <Settings className="w-4 h-4" />
                    </Button>
                </div>
            </div>

            {openModal === 'bank' && (
                <BankAbhebungModal
                    onClose={() => setOpenModal(null)}
                    onSuccess={(msg) => { setOpenModal(null); refreshAlles(); showToast('ok', msg); }}
                    onError={(m) => showToast('err', m)}
                    saldo={saldo}
                />
            )}
            {openModal === 'lohn' && (
                <LohnZahlungModal
                    onClose={() => setOpenModal(null)}
                    onSuccess={(msg) => { setOpenModal(null); refreshAlles(); showToast('ok', msg); }}
                    onError={(m) => showToast('err', m)}
                />
            )}
            {openModal === 'einlage' && (
                <EinfacheKasseModal
                    titel="Privateinlage buchen"
                    endpoint="/api/buchhaltung/kasse/privateinlage"
                    defaultBeschreibung="Privateinlage"
                    onClose={() => setOpenModal(null)}
                    onSuccess={(msg) => { setOpenModal(null); refreshAlles(); showToast('ok', msg); }}
                    onError={(m) => showToast('err', m)}
                />
            )}
            {openModal === 'entnahme' && (
                <EinfacheKasseModal
                    titel="Privatentnahme buchen"
                    endpoint="/api/buchhaltung/kasse/privatentnahme"
                    defaultBeschreibung="Privatentnahme"
                    onClose={() => setOpenModal(null)}
                    onSuccess={(msg) => { setOpenModal(null); refreshAlles(); showToast('ok', msg); }}
                    onError={(m) => showToast('err', m)}
                />
            )}
            {openModal === 'settings' && (
                <KasseSettingsModal
                    sachkonten={sachkonten}
                    onClose={() => setOpenModal(null)}
                    onSaved={() => { setOpenModal(null); refreshAlles(); showToast('ok', 'Einstellungen gespeichert'); }}
                    onError={(m) => showToast('err', m)}
                />
            )}

            {toast && (
                <div className={`mt-3 text-sm px-3 py-2 rounded-lg border ${
                    toast.kind === 'ok'
                        ? 'bg-rose-50 border-rose-200 text-rose-800'
                        : 'bg-red-50 border-red-200 text-red-800'
                }`}>
                    {toast.text}
                </div>
            )}
        </Card>
    );
}

// ===================== Bank-Abhebung =====================

function BankAbhebungModal({ onClose, onSuccess, onError, saldo }: {
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
                    className={inputCls} autoFocus />
            </FieldRow>
            <FieldRow label="Datum">
                <DatePicker value={datum} onChange={setDatum} />
            </FieldRow>
            <FieldRow label="Beleg-Nr. (optional)">
                <input type="text" value={belegNr}
                    onChange={e => setBelegNr(e.target.value)}
                    placeholder="z.B. EC-Auszug-Nr."
                    className={inputCls} />
            </FieldRow>
            <FieldRow label="Beschreibung (optional)">
                <input type="text" value={beschreibung}
                    onChange={e => setBeschreibung(e.target.value)}
                    placeholder="z.B. Bargeld geholt"
                    className={inputCls} />
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

function EinfacheKasseModal({ titel, endpoint, defaultBeschreibung, onClose, onSuccess, onError }: {
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
                    className={inputCls} autoFocus />
            </FieldRow>
            <FieldRow label="Datum">
                <DatePicker value={datum} onChange={setDatum} />
            </FieldRow>
            <FieldRow label="Beschreibung (optional)">
                <input type="text" value={beschreibung}
                    onChange={e => setBeschreibung(e.target.value)}
                    className={inputCls} />
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
function LohnZahlungModal({ onClose, onSuccess, onError }: {
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
                    className={inputCls} placeholder="z.B. Diana Mustermann" />
            </FieldRow>
            <FieldRow label="Monat (zur Notiz)">
                <DatePicker value={datum} onChange={setDatum} />
            </FieldRow>
            <FieldRow label="Betrag (€)">
                <input type="number" step="0.01" value={betrag}
                    onChange={e => setBetrag(e.target.value)}
                    className={inputCls} autoFocus />
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

// ===================== Einstellungen-Modal =====================

function KasseSettingsModal({ sachkonten, onClose, onSaved, onError }: {
    sachkonten: Sachkonto[];
    onClose: () => void;
    onSaved: () => void;
    onError: (msg: string) => void;
}) {
    const [einstellung, setEinstellung] = useState<KasseEinstellung | null>(null);
    const [saving, setSaving] = useState(false);

    useEffect(() => {
        fetch('/api/buchhaltung/kasse/einstellung')
            .then(r => r.ok ? r.json() : null)
            .then((e: KasseEinstellung | null) => setEinstellung(e ?? defaultEinstellung()))
            .catch(() => setEinstellung(defaultEinstellung()));
    }, []);

    if (!einstellung) {
        return (
            <ModalShell title="Kassen-Einstellungen" onClose={onClose}>
                <div className="py-8 flex justify-center"><Loader2 className="w-6 h-6 animate-spin text-rose-500" /></div>
            </ModalShell>
        );
    }

    const update = <K extends keyof KasseEinstellung>(k: K, v: KasseEinstellung[K]) =>
        setEinstellung(e => e ? { ...e, [k]: v } : e);

    const privatSachkonten = sachkonten.filter(s => s.kontoTyp === 'PRIVAT').sort((a, b) => a.sortierung - b.sortierung);

    const submit = async () => {
        setSaving(true);
        try {
            const res = await fetch('/api/buchhaltung/kasse/einstellung', {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    mindestbestand: einstellung.mindestbestand ?? 0,
                    ehegattengehaltAktiv: einstellung.ehegattengehaltAktiv,
                    ehegattengehaltBetrag: einstellung.ehegattengehaltBetrag ?? null,
                    ehegattengehaltTag: einstellung.ehegattengehaltTag ?? null,
                    ehegattengehaltEmpfaengerName: einstellung.ehegattengehaltEmpfaengerName ?? null,
                    privateinlageSachkontoId: einstellung.privateinlageSachkontoId ?? null,
                }),
            });
            if (res.ok) {
                onSaved();
            } else {
                const body = await res.json().catch(() => null);
                onError(body?.message ?? 'Speichern fehlgeschlagen');
            }
        } catch (e) {
            console.error(e);
            onError('Netzwerkfehler');
        } finally {
            setSaving(false);
        }
    };

    return (
        <ModalShell title="Kassen-Einstellungen" onClose={onClose} wide>
            <h3 className="font-semibold text-slate-900 mb-2 text-sm">Mindestbestand der Kasse</h3>
            <FieldRow label="Mindestbestand (€)">
                <input type="number" step="0.01" value={einstellung.mindestbestand ?? 0}
                    onChange={e => update('mindestbestand', Number(e.target.value))}
                    className={inputCls} />
            </FieldRow>
            <p className="text-xs text-slate-500 mb-4">
                Buchungen, die den Kassenstand unter diesen Wert fallen lassen würden, werden geblockt.
                0 = keine Sperre.
            </p>

            <h3 className="font-semibold text-slate-900 mb-2 text-sm pt-2 border-t border-slate-200">Vorab-Sachkonto für Auto-Einlagen</h3>
            <FieldRow label="Sachkonto Privateinlage">
                <Select
                    value={einstellung.privateinlageSachkontoId != null ? String(einstellung.privateinlageSachkontoId) : ''}
                    onChange={(v: string) => update('privateinlageSachkontoId', v ? Number(v) : null)}
                    placeholder="– kein Konto –"
                    options={[
                        { value: '', label: '– kein Konto –' },
                        ...privatSachkonten.map(s => ({
                            value: String(s.id),
                            label: `${s.nummer ? s.nummer + ' ' : ''}${s.bezeichnung}`,
                        })),
                    ]}
                />
            </FieldRow>

            <h3 className="font-semibold text-slate-900 mb-2 text-sm pt-2 border-t border-slate-200">Ehegattengehalt-Automatik</h3>
            <p className="text-xs text-slate-500 mb-3">
                Wird am Stichtag automatisch auf <strong>Löhne &amp; Gehälter</strong> gebucht — reine Buchhaltung, keine Kostenstelle.
                Falls die Kasse danach unter den Mindestbestand fiele, wird vorher automatisch eine Privateinlage gebucht.
            </p>
            <label className="flex items-center gap-2 mb-3">
                <input type="checkbox" checked={einstellung.ehegattengehaltAktiv}
                    onChange={e => update('ehegattengehaltAktiv', e.target.checked)} />
                <span className="text-sm">Jeden Monat automatisch buchen</span>
            </label>
            {einstellung.ehegattengehaltAktiv && (
                <>
                    <FieldRow label="Empfänger (Name)">
                        <input type="text" value={einstellung.ehegattengehaltEmpfaengerName ?? ''}
                            onChange={e => update('ehegattengehaltEmpfaengerName', e.target.value)}
                            className={inputCls} placeholder="z.B. Diana Mustermann" />
                    </FieldRow>
                    <FieldRow label="Monatlicher Betrag (€)">
                        <input type="number" step="0.01" value={einstellung.ehegattengehaltBetrag ?? ''}
                            onChange={e => update('ehegattengehaltBetrag', e.target.value === '' ? null : Number(e.target.value))}
                            className={inputCls} />
                    </FieldRow>
                    <FieldRow label="Tag des Monats (1–28)">
                        <input type="number" min={1} max={28} value={einstellung.ehegattengehaltTag ?? ''}
                            onChange={e => update('ehegattengehaltTag', e.target.value === '' ? null : Number(e.target.value))}
                            className={inputCls} />
                    </FieldRow>
                </>
            )}

            <ModalFooter onClose={onClose} onSubmit={submit} saving={saving} label="Speichern" />
        </ModalShell>
    );
}

function defaultEinstellung(): KasseEinstellung {
    return { mindestbestand: 0, ehegattengehaltAktiv: false };
}

// ===================== Modal Shell + helpers =====================

const inputCls = 'w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-rose-500';

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
