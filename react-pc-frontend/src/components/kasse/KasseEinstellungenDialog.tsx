import { useEffect, useState } from 'react';
import { Loader2 } from 'lucide-react';
import { Select } from '../ui/select-custom';
import type { KasseEinstellung, Sachkonto } from '../../types';
import { modalInputCls } from './belegFormat';
import { FieldRow, ModalFooter, ModalShell } from './NeueBuchungDialog';

// Task 9 (reine Verschiebung, kein Verhalten geaendert): heutiges
// KasseSettingsModal (KasseShortcuts.tsx:517-641) plus defaultEinstellung.
// KasseShortcuts.tsx importiert und ruft es wie bisher auf.

export function KasseEinstellungenDialog({ sachkonten, onClose, onSaved, onError }: {
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
                    className={modalInputCls} />
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
                            className={modalInputCls} placeholder="z.B. Diana Mustermann" />
                    </FieldRow>
                    <FieldRow label="Monatlicher Betrag (€)">
                        <input type="number" step="0.01" value={einstellung.ehegattengehaltBetrag ?? ''}
                            onChange={e => update('ehegattengehaltBetrag', e.target.value === '' ? null : Number(e.target.value))}
                            className={modalInputCls} />
                    </FieldRow>
                    <FieldRow label="Tag des Monats (1–28)">
                        <input type="number" min={1} max={28} value={einstellung.ehegattengehaltTag ?? ''}
                            onChange={e => update('ehegattengehaltTag', e.target.value === '' ? null : Number(e.target.value))}
                            className={modalInputCls} />
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
