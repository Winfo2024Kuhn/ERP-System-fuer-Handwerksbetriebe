import { useEffect, useState } from 'react';
import { Loader2, WalletCards } from 'lucide-react';
import { Button } from '../ui/button';
import { Dialog, DialogHeader, DialogTitle } from '../ui/dialog';
import { Select } from '../ui/select-custom';
import { DecimalInput } from '../ui/decimal-input';
import { formatDecimalInput } from '../../lib/numberInput';
import { validateNumberDrafts } from '../../lib/numberDrafts';
import type { KasseEinstellung, Sachkonto } from '../../types';
import { modalInputCls } from './belegFormat';
import { FieldRow, ModalFooter } from './NeueBuchungDialog';

export function KasseEinstellungenDialog({ sachkonten, onClose, onSaved, onError, onPayOnce }: {
    sachkonten: Sachkonto[];
    onClose: () => void;
    onSaved: () => void;
    onError: (msg: string) => void;
    onPayOnce?: () => void;
}) {
    const [einstellung, setEinstellung] = useState<KasseEinstellung | null>(null);
    const [saving, setSaving] = useState(false);
    const [drafts, setDrafts] = useState({ minimum: '0', betrag: '', tag: '' });

    useEffect(() => {
        fetch('/api/buchhaltung/kasse/einstellung')
            .then(r => r.ok ? r.json() : null)
            .then((e: KasseEinstellung | null) => {
                const value = e ?? defaultEinstellung(); setEinstellung(value);
                setDrafts({ minimum: formatDecimalInput(value.mindestbestand ?? 0), betrag: value.ehegattengehaltBetrag == null ? '' : formatDecimalInput(value.ehegattengehaltBetrag), tag: value.ehegattengehaltTag == null ? '' : String(value.ehegattengehaltTag) });
            })
            .catch(() => setEinstellung(defaultEinstellung()));
    }, []);

    if (!einstellung) {
        return (
            <Dialog open onOpenChange={open => { if (!open) onClose(); }} aria-label="Kassen-Einstellungen" className="w-full max-w-xl">
                <DialogHeader><DialogTitle>Kassen-Einstellungen</DialogTitle></DialogHeader>
                <div className="py-8 flex justify-center"><Loader2 className="w-6 h-6 animate-spin text-rose-500" /></div>
            </Dialog>
        );
    }

    const update = <K extends keyof KasseEinstellung>(k: K, v: KasseEinstellung[K]) =>
        setEinstellung(e => e ? { ...e, [k]: v } : e);

    const setKontonummer = (feld: 'kassenkontoNummer' | 'bankkontoNummer', wert: string) => {
        update(feld, wert);
    };

    const privatSachkonten = sachkonten.filter(s => s.kontoTyp === 'PRIVAT').sort((a, b) => a.sortierung - b.sortierung);
    const kontoUngueltig = (wert: string | null | undefined) => Boolean(wert && (!/^\d+$/.test(wert) || wert.length > 8));
    const datevUngueltig = (wert: string | null | undefined, max: number) => Boolean(wert && (!/^\d+$/.test(wert) || wert.length > max));
    const kassenkontoUngueltig = kontoUngueltig(einstellung.kassenkontoNummer);
    const bankkontoUngueltig = kontoUngueltig(einstellung.bankkontoNummer);
    const beraternummerUngueltig = datevUngueltig(einstellung.datevBeraternummer, 7);
    const mandantennummerUngueltig = datevUngueltig(einstellung.datevMandantennummer, 5);

    const submit = async () => {
        const activeDrafts = einstellung.ehegattengehaltAktiv ? drafts : { ...drafts, betrag: '', tag: '' };
        const result = validateNumberDrafts(activeDrafts, {
            minimum: { label: 'Mindestbestand', required: true, min: 0, maxDecimalPlaces: 2 },
            betrag: { label: 'Monatlicher Betrag', required: einstellung.ehegattengehaltAktiv, min: 0, maxDecimalPlaces: 2 },
            tag: { label: 'Tag des Monats', required: einstellung.ehegattengehaltAktiv, integer: true, min: 1, max: 28 },
        });
        if (!result.valid) { onError(result.message); return; }
        if (einstellung.ehegattengehaltAktiv && result.values.betrag! <= 0) { onError('Bitte einen positiven monatlichen Betrag eingeben.'); return; }
        if (kassenkontoUngueltig || bankkontoUngueltig || beraternummerUngueltig || mandantennummerUngueltig) return;
        setSaving(true);
        try {
            const res = await fetch('/api/buchhaltung/kasse/einstellung', {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    mindestbestand: result.values.minimum,
                    ehegattengehaltAktiv: einstellung.ehegattengehaltAktiv,
                    ehegattengehaltBetrag: einstellung.ehegattengehaltAktiv ? result.values.betrag : einstellung.ehegattengehaltBetrag ?? null,
                    ehegattengehaltTag: einstellung.ehegattengehaltAktiv ? result.values.tag : einstellung.ehegattengehaltTag ?? null,
                    ehegattengehaltEmpfaengerName: einstellung.ehegattengehaltEmpfaengerName ?? null,
                    privateinlageSachkontoId: einstellung.privateinlageSachkontoId ?? null,
                    datevBeraternummer: einstellung.datevBeraternummer ?? null,
                    datevMandantennummer: einstellung.datevMandantennummer ?? null,
                    wirtschaftsjahrBeginnMonat: einstellung.wirtschaftsjahrBeginnMonat ?? 1,
                    kassenkontoNummer: einstellung.kassenkontoNummer ?? '1000',
                    bankkontoNummer: einstellung.bankkontoNummer ?? '1200',
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
            <Dialog open onOpenChange={open => { if (!open) onClose(); }} aria-label="Kassen-Einstellungen" className="w-full max-w-xl">
                <DialogHeader><DialogTitle>Kassen-Einstellungen</DialogTitle></DialogHeader>
                <div className="min-h-0 flex-1 overflow-y-auto p-1 -mx-1">
            <h3 className="font-semibold text-slate-900 mb-2 text-sm">Mindestbestand der Kasse</h3>
            <FieldRow label="Mindestbestand (€)">
                <DecimalInput aria-label="Mindestbestand (€)" value={drafts.minimum} onChange={value => setDrafts(d => ({ ...d, minimum: value }))} className={modalInputCls} />
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

            <h3 className="font-semibold text-slate-900 mb-2 text-sm pt-2 border-t border-slate-200">Für den Steuerberater</h3>
            <p className="text-xs text-slate-500 mb-3">Diese Angaben stehen in der DATEV-Datei, die dein Steuerberater bekommt. Wenn du sie nicht kennst, frag ihn — der Export geht auch ohne.</p>
            <FieldRow label="Beraternummer">
                <input aria-label="Beraternummer" type="text" maxLength={7} placeholder="z.B. 1234567" value={einstellung.datevBeraternummer ?? ''} onChange={e => update('datevBeraternummer', e.target.value)} className={modalInputCls} />
                {beraternummerUngueltig && <p className="mt-1 text-xs text-amber-900">Bitte nur Ziffern, höchstens 7 Stellen.</p>}
            </FieldRow>
            <FieldRow label="Mandantennummer">
                <input aria-label="Mandantennummer" type="text" maxLength={5} placeholder="z.B. 54321" value={einstellung.datevMandantennummer ?? ''} onChange={e => update('datevMandantennummer', e.target.value)} className={modalInputCls} />
                {mandantennummerUngueltig && <p className="mt-1 text-xs text-amber-900">Bitte nur Ziffern, höchstens 5 Stellen.</p>}
            </FieldRow>
            <FieldRow label="Wirtschaftsjahr beginnt im">
                <Select aria-label="Wirtschaftsjahr beginnt im" value={String(einstellung.wirtschaftsjahrBeginnMonat ?? 1)} onChange={v => update('wirtschaftsjahrBeginnMonat', Number(v))} options={MONATE.map((label, index) => ({ value: String(index + 1), label }))} />
                <p className="mt-1 text-xs text-slate-500">Bei den meisten Betrieben ist das der Januar.</p>
            </FieldRow>
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                <FieldRow label="Kassenkonto">
                    <input aria-label="Kassenkonto" type="text" inputMode="numeric" maxLength={8} value={einstellung.kassenkontoNummer ?? '1000'} onChange={e => setKontonummer('kassenkontoNummer', e.target.value)} className={modalInputCls} />
                    {kassenkontoUngueltig && <p className="mt-1 text-xs text-amber-900">Bitte nur Ziffern, höchstens 8 Stellen.</p>}
                </FieldRow>
                <FieldRow label="Bankkonto">
                    <input aria-label="Bankkonto" type="text" inputMode="numeric" maxLength={8} value={einstellung.bankkontoNummer ?? '1200'} onChange={e => setKontonummer('bankkontoNummer', e.target.value)} className={modalInputCls} />
                    {bankkontoUngueltig && <p className="mt-1 text-xs text-amber-900">Bitte nur Ziffern, höchstens 8 Stellen.</p>}
                </FieldRow>
            </div>
            <p className="text-xs text-slate-500 mb-4">Standard im SKR03. Nur ändern, wenn dein Steuerberater andere Nummern nutzt.</p>

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
                            className={modalInputCls} placeholder="z.B. Diana Mustermann" />
                    </FieldRow>
                    <FieldRow label="Monatlicher Betrag (€)">
                        <DecimalInput aria-label="Monatlicher Betrag (€)" value={drafts.betrag} onChange={value => setDrafts(d => ({ ...d, betrag: value }))} className={modalInputCls} />
                    </FieldRow>
                    <FieldRow label="Tag des Monats (1–28)">
                        <DecimalInput aria-label="Tag des Monats (1–28)" value={drafts.tag} onChange={value => setDrafts(d => ({ ...d, tag: value }))} className={modalInputCls} />
                    </FieldRow>
                </>
            )}
            {onPayOnce && <Button type="button" variant="outline" size="sm" onClick={onPayOnce} className="mb-1 border-rose-300 text-rose-700 hover:bg-rose-50"><WalletCards className="mr-2 h-4 w-4" />Jetzt einmalig auszahlen</Button>}
                </div>

                <ModalFooter onClose={onClose} onSubmit={submit} saving={saving} label="Speichern" />
            </Dialog>
    );
}

function defaultEinstellung(): KasseEinstellung {
    return { mindestbestand: 0, ehegattengehaltAktiv: false, wirtschaftsjahrBeginnMonat: 1, kassenkontoNummer: '1000', bankkontoNummer: '1200' };
}

const MONATE = ['Januar', 'Februar', 'März', 'April', 'Mai', 'Juni', 'Juli', 'August', 'September', 'Oktober', 'November', 'Dezember'];
